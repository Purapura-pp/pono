/*
 * Copyright (C) 2026 Pono
 * 
 * This file is part of OpenPnP.
 * 
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * For more information about OpenPnP visit http://openpnp.org
 */

package org.openpnp.machine.reference.calibration;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.swing.SwingUtilities;

import org.openpnp.Translations;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.camera.ReferenceCamera;
import org.openpnp.machine.reference.solutions.MachineDiagnostics;
import org.openpnp.machine.reference.solutions.MachineDiagnostics.TestGroup;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults;
import org.openpnp.model.CalibrationStep;
import org.openpnp.model.Configuration;
import org.openpnp.model.Job;
import org.openpnp.model.Length;
import org.openpnp.model.Solutions;
import org.pmw.tinylog.Logger;

/**
 * Carries out a calibration session: the steps asked for, in the catalogue's order, each one on
 * the plan as it stands when its turn comes.
 * <p>
 * A session starts by saving the configuration and copying machine.xml aside. A step whose
 * measurements are missing or out of date has them measured first. A step that needs someone at
 * the machine waits for them to say it is done. Each step that succeeds marks the measurements it
 * changed as out of date and saves the configuration straight away; the first that fails stops
 * the session, its issue having put back what it changed. Stop takes effect once the step under
 * way is done. The report goes beside the diagnostics reports.
 * <p>
 * What reaches the machine goes through {@link Machinery}; {@link OnMachine} is the machine's.
 */
public class CalibrationRunner {
    public enum Outcome {
        Done,
        Skipped,
        Failed,
        Stopped;

        public String getName() {
            return Translations.getString("CalibrationRunner.Outcome." + name()); //$NON-NLS-1$
        }
    }

    /** What someone at the machine answered to a step that needed them. */
    public enum Decision {
        Continue,
        Skip,
        Stop
    }

    /** How a camera's settling is set: to the measured fixed wait, or adaptively. */
    public enum SettleMethod {
        /** The slower of the settling and the frame latency measured, with a margin, as a fixed wait. */
        Measured,
        /** The adaptive settling of Issues and Solutions, which waits for the image to stop moving. */
        Adaptive
    }

    /** What a session tells whoever shows it. Called on the runner's thread. */
    public interface Listener {
        default void stepStarted(CalibrationPlan.Step step) {
        }

        default void stepFinished(CalibrationPlan.Step step, Outcome outcome, String message) {
        }

        /** The step needs someone at the machine: its description says what to do. */
        default void waitingForPerson(CalibrationPlan.Step step) {
        }

        default void log(String line) {
        }

        default void finished(Session session) {
        }
    }

    /** What happened to one step. */
    public static final class Result {
        private final String key;
        private final String title;
        private final Outcome outcome;
        private final String message;
        private final List<String> changes;
        private final long millis;

        Result(String key, String title, Outcome outcome, String message, List<String> changes, long millis) {
            this.key = key;
            this.title = title;
            this.outcome = outcome;
            this.message = message;
            this.changes = changes;
            this.millis = millis;
        }

        public String getKey() {
            return key;
        }

        public String getTitle() {
            return title;
        }

        public Outcome getOutcome() {
            return outcome;
        }

        public String getMessage() {
            return message;
        }

        /** What it changed, as "setting: old \u2192 new". */
        public List<String> getChanges() {
            return Collections.unmodifiableList(changes);
        }

        public long getMillis() {
            return millis;
        }
    }

    /** One session: its backup, its report and what happened to each step. */
    public static final class Session {
        private final Date started = new Date();
        private File backup;
        private File reportDirectory;
        private final List<Result> results = new ArrayList<>();
        private boolean stopped;
        private String failure;

        public Date getStarted() {
            return started;
        }

        public File getBackup() {
            return backup;
        }

        public File getReportDirectory() {
            return reportDirectory;
        }

        public List<Result> getResults() {
            return Collections.unmodifiableList(results);
        }

        public boolean isStopped() {
            return stopped;
        }

        /** Why the session ended early, or null if it went through. */
        public String getFailure() {
            return failure;
        }

        public Result getResult(String key) {
            for (Result result : results) {
                if (result.getKey().equals(key)) {
                    return result;
                }
            }
            return null;
        }
    }

    /**
     * What the runner does to the machine, apart from deciding what to do. Each method blocks
     * until it is done, and throws when it failed.
     */
    public interface Machinery {
        /** The plan as it stands now: a search and a build. */
        CalibrationPlan plan() throws Exception;

        /** Runs the measurement groups. */
        void measure(Set<TestGroup> groups) throws Exception;

        /** Accepts the issue and waits for what it started to finish; throws if it did not succeed. */
        void accept(CalibrationPlan.Step step, Solutions.Issue issue) throws Exception;

        /** Compensates the machine's frame from the datum board, readings first. */
        void compensate() throws Exception;

        /** Marks the groups' measurements out of date, because the step changed what they measured. */
        void invalidate(Set<TestGroup> groups, String by);

        void save() throws Exception;

        /** Saves, and copies machine.xml aside, before the session changes anything. */
        File backup(String stamp) throws Exception;

        File reportDirectory(String stamp) throws Exception;
    }

    private final Machinery machinery;
    private final Listener listener;
    private SettleMethod settleMethod = SettleMethod.Measured;
    private volatile boolean stopRequested;
    private volatile CompletableFuture<Decision> person;

    public CalibrationRunner(Machinery machinery, Listener listener) {
        this.machinery = machinery;
        this.listener = listener == null ? new Listener() {
        } : listener;
    }

    public SettleMethod getSettleMethod() {
        return settleMethod;
    }

    public void setSettleMethod(SettleMethod settleMethod) {
        this.settleMethod = settleMethod;
    }

    /** Runs the session on a thread of its own. */
    public CompletableFuture<Session> start(List<String> keys, Set<String> skip) {
        CompletableFuture<Session> future = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                future.complete(run(keys, skip));
            }
            catch (Throwable t) {
                future.completeExceptionally(t);
            }
        }, "pono-calibration"); //$NON-NLS-1$
        thread.setDaemon(true);
        thread.start();
        return future;
    }

    /** Stops once the step under way is done; a step waiting for someone stops at once. */
    public void requestStop() {
        stopRequested = true;
        answer(Decision.Stop);
    }

    /** Someone at the machine did what the step waiting for them asked. */
    public void continueAfterPerson() {
        answer(Decision.Continue);
    }

    /** Leaves out the step waiting for someone, this time. */
    public void skipStep() {
        answer(Decision.Skip);
    }

    /** Whether a step is waiting for someone at the machine. */
    public boolean isWaitingForPerson() {
        CompletableFuture<Decision> waiting = person;
        return waiting != null && !waiting.isDone();
    }

    private void answer(Decision decision) {
        CompletableFuture<Decision> waiting = person;
        if (waiting != null) {
            waiting.complete(decision);
        }
    }

    /**
     * The session itself, on the calling thread, which must not be the event thread: the issues
     * are accepted there.
     * 
     * @param keys The steps to carry out, by {@link CalibrationPlan.Step#getKey()}.
     * @param skip Steps that need someone and are to be left out this time.
     */
    public Session run(List<String> keys, Set<String> skip) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("A calibration session cannot run on the event thread."); //$NON-NLS-1$
        }
        stopRequested = false;
        Session session = new Session();
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(session.started); //$NON-NLS-1$
        session.backup = machinery.backup(stamp);
        session.reportDirectory = machinery.reportDirectory(stamp);
        log(String.format(Translations.getString("CalibrationRunner.Backup"), session.backup)); //$NON-NLS-1$
        Set<TestGroup> changed = EnumSet.noneOf(TestGroup.class);
        for (String key : inCatalogueOrder(machinery.plan(), keys)) {
            if (stopRequested) {
                session.stopped = true;
                break;
            }
            long began = System.currentTimeMillis();
            CalibrationPlan.Step step = machinery.plan().getStep(key);
            if (step == null) {
                record(session, null, key, key, Outcome.Skipped,
                        Translations.getString("CalibrationRunner.Gone"), began); //$NON-NLS-1$
                continue;
            }
            listener.stepStarted(step);
            if (skip != null && skip.contains(key)) {
                record(session, step, Outcome.Skipped, Translations.getString("CalibrationRunner.SkippedThisTime"), //$NON-NLS-1$
                        Collections.emptyList(), began);
                continue;
            }
            if (step.getStatus().isSettled()) {
                record(session, step, Outcome.Skipped, Translations.getString("CalibrationRunner.AlreadyDone"), //$NON-NLS-1$
                        Collections.emptyList(), began);
                continue;
            }
            try {
                if (step.getStatus() == CalibrationPlan.Status.Waiting) {
                    throw new Exception(String.format(Translations.getString("CalibrationRunner.Waiting"), //$NON-NLS-1$
                            titles(step.getUnsettledPrerequisites())));
                }
                if (!step.getMissingMeasurements().isEmpty()) {
                    log(String.format(Translations.getString("CalibrationRunner.Measuring"), //$NON-NLS-1$
                            step.getTitle(), step.getMissingMeasurements()));
                    machinery.measure(step.getMissingMeasurements());
                    step = machinery.plan().getStep(key);
                    if (step == null || step.getStatus().isSettled()) {
                        record(session, step, key, step == null ? key : step.getTitle(), Outcome.Done,
                                Translations.getString("CalibrationRunner.MeasuredNothingToChange"), began); //$NON-NLS-1$
                        continue;
                    }
                    if (step.getStatus() == CalibrationPlan.Status.NeedsMeasurement) {
                        throw new Exception(Translations.getString("CalibrationRunner.StillUnmeasured")); //$NON-NLS-1$
                    }
                }
                if (step.isNeedsPerson()) {
                    Decision decision = waitForPerson(step);
                    if (decision == Decision.Skip) {
                        record(session, step, Outcome.Skipped,
                                Translations.getString("CalibrationRunner.SkippedThisTime"), //$NON-NLS-1$
                                Collections.emptyList(), began);
                        continue;
                    }
                    if (decision == Decision.Stop) {
                        session.stopped = true;
                        record(session, step, Outcome.Stopped, "", Collections.emptyList(), began); //$NON-NLS-1$
                        break;
                    }
                    // What they did may have changed the issues: judge the step again.
                    step = machinery.plan().getStep(key);
                    if (step == null || step.getStatus().isSettled()) {
                        record(session, step, key, step == null ? key : step.getTitle(), Outcome.Done, "", began); //$NON-NLS-1$
                        continue;
                    }
                }
                List<String> changes = describe(step.getChanges());
                carryOut(step, changed);
                Set<TestGroup> invalidated = CalibrationPlan.invalidatedBy(step.getKind());
                machinery.invalidate(invalidated, step.getKind().name());
                changed.addAll(invalidated);
                machinery.save();
                record(session, step, Outcome.Done, "", changes, began); //$NON-NLS-1$
            }
            catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                Logger.warn(e, "Calibration step {} failed.", step.getTitle()); //$NON-NLS-1$
                record(session, step, Outcome.Failed, message, Collections.emptyList(), began);
                session.failure = step.getTitle() + ": " + message; //$NON-NLS-1$
                break;
            }
        }
        writeReport(session);
        listener.finished(session);
        return session;
    }

    /** Does what the step does: accepts its issues, or its own procedure for the two that have one. */
    private void carryOut(CalibrationPlan.Step step, Set<TestGroup> changed) throws Exception {
        switch (step.getKind()) {
            case Remeasure: {
                Set<TestGroup> groups = new LinkedHashSet<>(changed);
                CalibrationPlan plan = machinery.plan();
                groups.addAll(plan.invalidated());
                if (!groups.isEmpty()) {
                    log(String.format(Translations.getString("CalibrationRunner.Remeasuring"), groups)); //$NON-NLS-1$
                    machinery.measure(EnumSet.copyOf(groups));
                }
                return;
            }
            case FrameCompensation:
                if (step.getActions().isEmpty()) {
                    machinery.compensate();
                    return;
                }
                break;
            default:
                break;
        }
        List<Solutions.Issue> actions = actionsFor(step);
        if (actions.isEmpty()) {
            throw new Exception(Translations.getString("CalibrationRunner.NoAction")); //$NON-NLS-1$
        }
        for (Solutions.Issue issue : actions) {
            log(String.format(Translations.getString("CalibrationRunner.Accepting"), issue.getIssue())); //$NON-NLS-1$
            machinery.accept(step, issue);
        }
    }

    /**
     * The issues that carry the step out. Its open ones that can be accepted; failing those, when
     * a measurement says a step done before needs doing again, the ones it was done with. A
     * camera's settling takes the method set here, where the step has both.
     */
    List<Solutions.Issue> actionsFor(CalibrationPlan.Step step) {
        List<Solutions.Issue> actions = step.getActions();
        if (step.getKind() == CalibrationStep.CameraSettle && actions.size() > 1) {
            List<Solutions.Issue> chosen = new ArrayList<>();
            for (Solutions.Issue issue : actions) {
                if ((issue instanceof SettingChange) == (settleMethod == SettleMethod.Measured)) {
                    chosen.add(issue);
                }
            }
            if (!chosen.isEmpty()) {
                actions = chosen;
            }
        }
        if (actions.isEmpty()) {
            boolean evidence = false;
            for (Solutions.Issue issue : step.getIssues()) {
                if (issue.getState() == Solutions.State.Open && !issue.canBeAccepted()) {
                    evidence = true;
                }
            }
            if (evidence) {
                for (Solutions.Issue issue : step.getIssues()) {
                    if (issue.getState() == Solutions.State.Solved && issue.canBeAccepted()) {
                        actions.add(issue);
                    }
                }
            }
        }
        return actions;
    }

    private Decision waitForPerson(CalibrationPlan.Step step) throws Exception {
        CompletableFuture<Decision> waiting = new CompletableFuture<>();
        person = waiting;
        if (stopRequested) {
            waiting.complete(Decision.Stop);
        }
        log(String.format(Translations.getString("CalibrationRunner.WaitingForPerson"), step.getTitle())); //$NON-NLS-1$
        listener.waitingForPerson(step);
        try {
            return waiting.get();
        }
        finally {
            person = null;
        }
    }

    private static List<String> inCatalogueOrder(CalibrationPlan plan, List<String> keys) {
        List<String> ordered = new ArrayList<>();
        for (CalibrationPlan.Step step : plan.getSteps()) {
            if (keys.contains(step.getKey())) {
                ordered.add(step.getKey());
            }
        }
        for (String key : keys) {
            if (!ordered.contains(key)) {
                ordered.add(key);
            }
        }
        return ordered;
    }

    private static String titles(List<CalibrationPlan.Step> steps) {
        List<String> titles = new ArrayList<>();
        for (CalibrationPlan.Step step : steps) {
            titles.add(step.getTitle());
        }
        return String.join("\u3001", titles); //$NON-NLS-1$
    }

    /** "setting: old \u2192 new" for each change, the numbers as a person writes them. */
    static List<String> describe(List<SettingChange> changes) {
        List<String> lines = new ArrayList<>();
        for (SettingChange change : changes) {
            lines.add(change.getSettingName() + ": " + format(change.getCurrentValue()) //$NON-NLS-1$
                    + " \u2192 " + format(change.getProposedValue())); //$NON-NLS-1$
        }
        return lines;
    }

    static String format(Object value) {
        if (value instanceof Length) {
            Length length = (Length) value;
            return number(length.getValue()) + " " + length.getUnits().getShortName(); //$NON-NLS-1$
        }
        if (value instanceof Double || value instanceof Float) {
            return number(((Number) value).doubleValue());
        }
        return String.valueOf(value);
    }

    private static String number(double value) {
        String text = String.format(java.util.Locale.ROOT, "%.4f", value); //$NON-NLS-1$
        text = text.replaceAll("0+$", ""); //$NON-NLS-1$ //$NON-NLS-2$
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text; //$NON-NLS-1$
    }

    private void record(Session session, CalibrationPlan.Step step, Outcome outcome, String message,
            List<String> changes, long began) {
        record(session, step, step.getKey(), step.getTitle(), outcome, message, changes, began);
    }

    private void record(Session session, CalibrationPlan.Step step, String key, String title, Outcome outcome,
            String message, long began) {
        record(session, step, key, title, outcome, message, Collections.emptyList(), began);
    }

    private void record(Session session, CalibrationPlan.Step step, String key, String title, Outcome outcome,
            String message, List<String> changes, long began) {
        session.results.add(new Result(key, title, outcome, message, changes, System.currentTimeMillis() - began));
        log(outcome.getName() + " \u00b7 " + title + (message == null || message.isEmpty() ? "" : " \u00b7 " + message)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        if (step != null) {
            listener.stepFinished(step, outcome, message);
        }
    }

    private void log(String line) {
        Logger.info("Calibration: {}", line); //$NON-NLS-1$
        listener.log(line);
    }

    private void writeReport(Session session) {
        if (session.reportDirectory == null) {
            return;
        }
        File file = new File(session.reportDirectory, "report.txt"); //$NON-NLS-1$
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8))) {
            out.println("Pono calibration"); //$NON-NLS-1$
            out.println("Started " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(session.started)); //$NON-NLS-1$ //$NON-NLS-2$
            out.println("Backup " + session.backup); //$NON-NLS-1$
            if (session.failure != null) {
                out.println("Stopped by a failure: " + session.failure); //$NON-NLS-1$
            }
            else if (session.stopped) {
                out.println("Stopped on request."); //$NON-NLS-1$
            }
            out.println();
            for (Result result : session.results) {
                out.println(String.format("%-8s %s  (%.1f s)%s", result.getOutcome(), result.getTitle(), //$NON-NLS-1$
                        result.getMillis() / 1000.0,
                        result.getMessage() == null || result.getMessage().isEmpty() ? "" : "  " + result.getMessage())); //$NON-NLS-1$ //$NON-NLS-2$
                for (String change : result.getChanges()) {
                    out.println("         " + change); //$NON-NLS-1$
                }
            }
        }
        catch (IOException e) {
            Logger.warn(e, "The calibration report could not be written to {}.", file); //$NON-NLS-1$
        }
    }

    // ----- the machine's machinery ---------------------------------------------------------------

    /**
     * The machinery of a real machine. Issues are accepted on the event thread, where the issues
     * page accepts them. An issue that calibrates submits a machine task and returns, and says
     * Solved from that task's callback on the event thread, or stays Open when it failed and put
     * everything back; so an accepted issue is waited for until the machine is done with what it
     * started, then the event thread twice, and then its state says how it went.
     */
    public static class OnMachine implements Machinery {
        /** A machine this long idle with a marker task still not run lost it to a failure before it. */
        private static final long IDLE_MILLIS = 500;
        /** The datum board readings a compensation is made from, when there are no more. */
        private static final int COMPENSATION_READINGS = 3;
        private static final int COMPENSATION_ATTEMPTS = 5;

        private final ReferenceMachine machine;
        private final Configuration configuration;
        private final Supplier<Job> job;

        /**
         * @param job The open job, whose board positions a frame compensation carries across; it
         *            gives null when there is none.
         */
        public OnMachine(ReferenceMachine machine, Configuration configuration, Supplier<Job> job) {
            this.machine = machine;
            this.configuration = configuration;
            this.job = job;
        }

        @Override
        public CalibrationPlan plan() throws Exception {
            return onEventThread(() -> CalibrationPlan.of(machine,
                    CalibrationPlan.scan(machine, machine.getSolutions())));
        }

        @Override
        public void measure(Set<TestGroup> groups) throws Exception {
            MachineDiagnostics diagnostics = machine.getMachineDiagnostics();
            Set<TestGroup> copy = groups.isEmpty() ? EnumSet.noneOf(TestGroup.class) : EnumSet.copyOf(groups);
            result(machine.submit(() -> diagnostics.run(machine, copy)));
        }

        @Override
        public void accept(CalibrationPlan.Step step, Solutions.Issue issue) throws Exception {
            // The issues page chose the first choice for the user when none was made; here the
            // step makes it, rather than leaving an issue that needs one to fail on a null.
            if (issue.getChoice() == null && issue.getChoices() != null) {
                for (Solutions.Issue.Choice choice : issue.getChoices()) {
                    if (choice != null) {
                        issue.setChoice(choice.getValue());
                        break;
                    }
                }
            }
            onEventThread(() -> {
                if (issue.getState() == Solutions.State.Solved) {
                    // Done before, to be done again: reopening puts back nothing that is not
                    // already there, as the issue took the values it restores from now.
                    issue.setStateCall(Solutions.State.Open);
                }
                issue.setStateCall(Solutions.State.Solved);
                return null;
            });
            drain();
            if (step.getKind() == CalibrationStep.AdvancedDownCamera
                    || step.getKind() == CalibrationStep.AdvancedUpCamera) {
                CompletableFuture<Boolean> completion = machine.getCalibrationSolutions()
                        .getAdvancedCalibrationCompletion((ReferenceCamera) step.getSubject());
                if (completion == null || !completion.get()) {
                    throw new Exception(String.format(Translations.getString("CalibrationRunner.NotCompleted"), //$NON-NLS-1$
                            issue.getIssue()));
                }
                drain();
            }
            if (issue.getState() != Solutions.State.Solved) {
                throw new Exception(String.format(Translations.getString("CalibrationRunner.NotCompleted"), //$NON-NLS-1$
                        issue.getIssue()));
            }
        }

        /**
         * Waits until the machine is done with what was submitted before now, and the event
         * thread with what that posted. A marker task queued behind it says when; a task that
         * fails takes the queue with it, marker included, so a machine that stays idle with the
         * marker not run is taken to be done too.
         */
        void drain() throws Exception {
            Future<Object> marker = machine.submit(() -> null, null, true);
            long idleSince = -1;
            while (true) {
                try {
                    marker.get(100, TimeUnit.MILLISECONDS);
                    break;
                }
                catch (TimeoutException e) {
                    if (machine.isIdle()) {
                        long now = System.currentTimeMillis();
                        if (idleSince < 0) {
                            idleSince = now;
                        }
                        else if (now - idleSince > IDLE_MILLIS) {
                            break;
                        }
                    }
                    else {
                        idleSince = -1;
                    }
                }
                catch (ExecutionException e) {
                    break;
                }
            }
            flushEventThread();
            flushEventThread();
        }

        @Override
        public void compensate() throws Exception {
            MachineDiagnostics diagnostics = machine.getMachineDiagnostics();
            for (int attempt = 0; attempt < COMPENSATION_ATTEMPTS; attempt++) {
                MachineDiagnostics.CompensationBasis basis = diagnostics.getCompensationBasis();
                if (basis != null && basis.readings >= COMPENSATION_READINGS) {
                    break;
                }
                measure(EnumSet.of(TestGroup.DatumBoard));
            }
            MachineDiagnostics.CompensationBasis basis = diagnostics.getCompensationBasis();
            if (basis == null || basis.readings < COMPENSATION_READINGS) {
                throw new Exception(Translations.getString("CalibrationRunner.TooFewReadings")); //$NON-NLS-1$
            }
            boolean squareness = basis.squarenessIsSettled();
            MachineDiagnostics.CompensationOutcome outcome = result(
                    machine.submit(() -> diagnostics.applyCompensation(machine, job.get(), squareness)));
            if (!outcome.kept) {
                throw new Exception(outcome.message);
            }
        }

        @Override
        public void invalidate(Set<TestGroup> groups, String by) {
            MachineDiagnosticsResults results = machine.getMachineDiagnostics().getLastResults();
            if (results == null) {
                return;
            }
            for (TestGroup group : groups) {
                results.invalidate(group, by);
            }
            configuration.setDirty(true);
        }

        @Override
        public void save() throws Exception {
            configuration.save();
        }

        @Override
        public File backup(String stamp) throws Exception {
            configuration.save();
            File directory = configuration.getConfigurationDirectory();
            File backup = new File(directory, "machine.xml.before-calibration-" + stamp); //$NON-NLS-1$
            Files.copy(new File(directory, "machine.xml").toPath(), backup.toPath(), //$NON-NLS-1$
                    StandardCopyOption.COPY_ATTRIBUTES);
            return backup;
        }

        @Override
        public File reportDirectory(String stamp) throws Exception {
            File directory = new File(new File(configuration.getConfigurationDirectory(), "diagnostics"), //$NON-NLS-1$
                    stamp + "-calibration"); //$NON-NLS-1$
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Cannot create " + directory); //$NON-NLS-1$
            }
            return directory;
        }
    }

    /** The value of a machine task, or the exception it failed with. */
    static <T> T result(Future<T> future) throws Exception {
        try {
            return future.get();
        }
        catch (ExecutionException e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    /** Runs on the event thread and waits, passing on what it threw. */
    static <T> T onEventThread(java.util.concurrent.Callable<T> callable) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return callable.call();
        }
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Exception> thrown = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                value.set(callable.call());
            }
            catch (Exception e) {
                thrown.set(e);
            }
        });
        if (thrown.get() != null) {
            throw thrown.get();
        }
        return value.get();
    }

    /** Lets the event thread finish what was posted to it before now. */
    static void flushEventThread() throws Exception {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeAndWait(() -> {
            });
        }
    }
}
