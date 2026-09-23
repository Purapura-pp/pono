package org.openpnp.machine.reference.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.solutions.MachineDiagnostics.TestGroup;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults;
import org.openpnp.model.CalibrationStep;
import org.openpnp.model.Configuration;
import org.openpnp.model.Solutions;
import org.openpnp.spi.Camera;
import org.openpnp.util.UiUtils;

/** A calibration session, against machinery that only records what it was asked to do. */
public class CalibrationRunnerTest {
    @TempDir
    Path tempDir;

    private ReferenceMachine machine;
    private MachineDiagnosticsResults results;
    private Fake fake;

    /** An issue that does its step when accepted, or fails to. */
    private final class Step extends Solutions.Issue {
        private boolean fails;
        private int accepted;

        Step(CalibrationStep kind, Object subject, String wording) {
            super(machine, wording, "Do it.", Solutions.Severity.Warning, null);
            withCalibrationStep(kind, subject);
        }

        Step failing() {
            fails = true;
            return this;
        }
    }

    /** Machinery that plans from the issues it holds and records the rest. */
    private final class Fake implements CalibrationRunner.Machinery {
        final List<Solutions.Issue> issues = new ArrayList<>();
        final List<Set<TestGroup>> measured = new ArrayList<>();
        final List<String> invalidated = new ArrayList<>();
        int saves;
        File backup;

        @Override
        public CalibrationPlan plan() {
            return CalibrationPlan.of(machine, issues);
        }

        @Override
        public void measure(Set<TestGroup> groups) {
            measured.add(EnumSet.copyOf(groups));
            for (TestGroup group : groups) {
                results.setRun(group, System.currentTimeMillis(), "report");
            }
        }

        @Override
        public void accept(CalibrationPlan.Step step, Solutions.Issue issue) throws Exception {
            Step fakeStep = (Step) issue;
            fakeStep.accepted++;
            if (fakeStep.fails) {
                throw new Exception("it did not work");
            }
            issue.setState(Solutions.State.Solved);
        }

        @Override
        public void compensate() {
        }

        @Override
        public void invalidate(Set<TestGroup> groups, String by) {
            for (TestGroup group : groups) {
                results.invalidate(group, by);
                invalidated.add(group + " by " + by);
            }
        }

        @Override
        public void save() {
            saves++;
        }

        @Override
        public File backup(String stamp) throws Exception {
            backup = tempDir.resolve("machine.xml.before-calibration-" + stamp).toFile();
            Files.writeString(backup.toPath(), "<machine/>");
            return backup;
        }

        @Override
        public File reportDirectory(String stamp) {
            File directory = tempDir.resolve("diagnostics").resolve(stamp + "-calibration").toFile();
            directory.mkdirs();
            return directory;
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
        machine = (ReferenceMachine) Configuration.get().getMachine();
        results = new MachineDiagnosticsResults();
        machine.getMachineDiagnostics().setLastResults(results);
        fake = new Fake();
    }

    private Step add(CalibrationStep kind, Object subject, String wording) {
        Step step = new Step(kind, subject, wording);
        fake.issues.add(step);
        return step;
    }

    private static String key(CalibrationStep kind) {
        return kind.name() + ":machine";
    }

    private Camera camera() throws Exception {
        return machine.getDefaultHead().getDefaultCamera();
    }

    @Test
    public void theStepsRunInTheCataloguesOrderMeasuredFirstAndEachSaved() throws Exception {
        Step subPixel = add(CalibrationStep.SubPixel, machine, "Sub-pixel.");
        Step limits = add(CalibrationStep.ControllerLimits, machine, "Controller limits.");
        results.setRun(TestGroup.Kinematics, System.currentTimeMillis(), "report");
        List<String> started = new ArrayList<>();
        CalibrationRunner runner = new CalibrationRunner(fake, new CalibrationRunner.Listener() {
            @Override
            public void stepStarted(CalibrationPlan.Step step) {
                started.add(step.getKind().name());
            }
        });

        CalibrationRunner.Session session = runner.run(
                List.of(key(CalibrationStep.SubPixel), key(CalibrationStep.ControllerLimits)), Set.of());

        assertEquals(List.of("ControllerLimits", "SubPixel"), started);
        assertEquals(List.of(EnumSet.of(TestGroup.Firmware)), fake.measured,
                "the controller limits are decided by what the controller said, measured first");
        assertEquals(1, limits.accepted);
        assertEquals(1, subPixel.accepted);
        assertEquals(2, fake.saves, "saved after each step that changed something");
        assertTrue(results.getRun(TestGroup.Kinematics).isInvalidated(),
                "limits that changed make the motion measured under the old ones out of date");
        assertNull(session.getFailure());
        assertEquals(CalibrationRunner.Outcome.Done, session.getResult(key(CalibrationStep.ControllerLimits)).getOutcome());
        assertNotNull(fake.backup);
        String report = Files.readString(new File(session.getReportDirectory(), "report.txt").toPath(),
                StandardCharsets.UTF_8);
        assertTrue(report.contains(fake.backup.toString()), report);
        assertTrue(report.indexOf(CalibrationStep.ControllerLimits.getName()) < report.indexOf(CalibrationStep.SubPixel.getName()),
                report);
    }

    @Test
    public void aStepThatFailsStopsTheSession() throws Exception {
        add(CalibrationStep.SubPixel, machine, "Sub-pixel.").failing();
        Step remeasure = add(CalibrationStep.Remeasure, machine, "Measure again.");

        CalibrationRunner.Session session = new CalibrationRunner(fake, null).run(
                List.of(key(CalibrationStep.SubPixel), key(CalibrationStep.Remeasure)), Set.of());

        assertEquals(CalibrationRunner.Outcome.Failed, session.getResult(key(CalibrationStep.SubPixel)).getOutcome());
        assertNull(session.getResult(key(CalibrationStep.Remeasure)), "nothing after a failure");
        assertEquals(0, remeasure.accepted);
        assertTrue(session.getFailure().contains("it did not work"), session.getFailure());
        assertEquals(0, fake.saves);
    }

    @Test
    public void stopTakesEffectOnceTheStepUnderWayIsDone() throws Exception {
        Step subPixel = add(CalibrationStep.SubPixel, machine, "Sub-pixel.");
        results.setRun(TestGroup.Firmware, System.currentTimeMillis(), "report");
        Step limits = add(CalibrationStep.ControllerLimits, machine, "Controller limits.");
        CalibrationRunner[] runner = new CalibrationRunner[1];
        runner[0] = new CalibrationRunner(fake, new CalibrationRunner.Listener() {
            @Override
            public void stepStarted(CalibrationPlan.Step step) {
                runner[0].requestStop();
            }
        });

        CalibrationRunner.Session session = runner[0].run(
                List.of(key(CalibrationStep.ControllerLimits), key(CalibrationStep.SubPixel)), Set.of());

        assertEquals(1, limits.accepted, "the step under way is finished");
        assertEquals(0, subPixel.accepted);
        assertTrue(session.isStopped());
    }

    @Test
    public void aStepThatNeedsSomeoneWaitsForThem() throws Exception {
        Camera camera = camera();
        Step exposure = add(CalibrationStep.Exposure, camera, "Exposure.");
        String key = CalibrationStep.Exposure.name() + ":" + camera.getId();
        CompletableFuture<CalibrationPlan.Step> waiting = new CompletableFuture<>();
        CalibrationRunner runner = new CalibrationRunner(fake, new CalibrationRunner.Listener() {
            @Override
            public void waitingForPerson(CalibrationPlan.Step step) {
                waiting.complete(step);
            }
        });

        CompletableFuture<CalibrationRunner.Session> session = runner.start(List.of(key), Set.of());

        assertEquals(CalibrationStep.Exposure, waiting.get(10, TimeUnit.SECONDS).getKind());
        assertTrue(runner.isWaitingForPerson());
        assertEquals(0, exposure.accepted, "nothing is done before they say so");
        runner.continueAfterPerson();
        assertEquals(CalibrationRunner.Outcome.Done, session.get(10, TimeUnit.SECONDS).getResult(key).getOutcome());
        assertEquals(1, exposure.accepted);
    }

    @Test
    public void aStepThatNeedsSomeoneCanBeSkippedAheadOrWhenItComes() throws Exception {
        Camera camera = camera();
        Step exposure = add(CalibrationStep.Exposure, camera, "Exposure.");
        String key = CalibrationStep.Exposure.name() + ":" + camera.getId();

        CalibrationRunner.Session ahead = new CalibrationRunner(fake, null).run(List.of(key), Set.of(key));
        assertEquals(CalibrationRunner.Outcome.Skipped, ahead.getResult(key).getOutcome());

        CalibrationRunner[] runner = new CalibrationRunner[1];
        runner[0] = new CalibrationRunner(fake, new CalibrationRunner.Listener() {
            @Override
            public void waitingForPerson(CalibrationPlan.Step step) {
                runner[0].skipStep();
            }
        });
        CalibrationRunner.Session when = runner[0].start(List.of(key), Set.of()).get(10, TimeUnit.SECONDS);
        assertEquals(CalibrationRunner.Outcome.Skipped, when.getResult(key).getOutcome());
        assertEquals(0, exposure.accepted);
    }

    @Test
    public void aStepThatIsDoneOrWaitsIsNotRun() throws Exception {
        Step subPixel = add(CalibrationStep.SubPixel, machine, "Sub-pixel.");
        subPixel.setState(Solutions.State.Solved);
        String softLimits = CalibrationPlan.of(machine, fake.issues)
                .getSteps(CalibrationStep.SoftLimits).get(0).getKey();

        CalibrationRunner.Session done = new CalibrationRunner(fake, null).run(
                List.of(key(CalibrationStep.SubPixel)), Set.of());
        assertEquals(CalibrationRunner.Outcome.Skipped, done.getResult(key(CalibrationStep.SubPixel)).getOutcome());
        assertEquals(0, subPixel.accepted);

        CalibrationRunner.Session waiting = new CalibrationRunner(fake, null).run(List.of(softLimits), Set.of());
        assertEquals(CalibrationRunner.Outcome.Failed, waiting.getResult(softLimits).getOutcome(),
                "soft limits wait for the machine to be homed");
        assertTrue(waiting.getFailure().contains(CalibrationStep.Home.getName()), waiting.getFailure());
    }

    @Test
    public void theSettlingIsTheMeasuredWaitUnlessTheAdaptiveMethodIsChosen() throws Exception {
        Camera camera = camera();
        Step adaptive = add(CalibrationStep.CameraSettle, camera, "Adaptive.");
        Solutions.Issue measured = new MeasuredWait(camera);
        fake.issues.add(measured);
        CalibrationRunner runner = new CalibrationRunner(fake, null);
        CalibrationPlan.Step step = CalibrationPlan.of(machine, fake.issues).getStep(CalibrationStep.CameraSettle, camera);

        assertEquals(List.of(measured), runner.actionsFor(step));
        runner.setSettleMethod(CalibrationRunner.SettleMethod.Adaptive);
        assertEquals(List.of(adaptive), runner.actionsFor(step));
    }

    /** The measured fixed wait, as the diagnostics offer it. */
    private final class MeasuredWait extends Solutions.Issue implements SettingChange {
        MeasuredWait(Camera camera) {
            super(machine, "Wait.", "Wait longer.", Solutions.Severity.Error, null);
            withCalibrationStep(CalibrationStep.CameraSettle, camera);
        }

        @Override
        public String getSettingName() {
            return "Settle time in milliseconds";
        }

        @Override
        public Object getCurrentValue() {
            return 100L;
        }

        @Override
        public Object getProposedValue() {
            return 450;
        }
    }

    // ----- the machine's machinery ---------------------------------------------------------------

    /** An issue that calibrates as the issues page's do: a machine task, then Solved from its callback. */
    private final class Calibrating extends Solutions.Issue {
        private final boolean fails;

        Calibrating(boolean fails) {
            super(machine, "Calibrate.", "Calibrate it.", Solutions.Severity.Warning, null);
            this.fails = fails;
        }

        @Override
        public void setState(Solutions.State state) throws Exception {
            if (state != Solutions.State.Solved) {
                super.setState(state);
                return;
            }
            UiUtils.submitUiMachineTask(() -> {
                Thread.sleep(300);
                if (fails) {
                    throw new Exception("the fiducial was not found");
                }
                return true;
            }, result -> {
                try {
                    super.setState(state);
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, t -> {
                // Put back, and stay Open: as the issues that calibrate do.
            });
        }
    }

    @Test
    public void anAcceptedIssueIsWaitedForUntilTheMachineIsDoneWithIt() throws Exception {
        machine.setEnabled(true);
        CalibrationRunner.OnMachine onMachine = new CalibrationRunner.OnMachine(machine, Configuration.get(), () -> null);
        Calibrating issue = new Calibrating(false);
        CalibrationPlan.Step step = CalibrationPlan.of(machine, List.of(issue.withCalibrationStep(CalibrationStep.SubPixel)))
                .getSteps(CalibrationStep.SubPixel).get(0);

        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> onMachine.accept(step, issue));

        assertEquals(Solutions.State.Solved, issue.getState());
    }

    /**
     * A task that fails takes the queue with it, the marker the runner waits on included: the
     * runner must not wait for ever, and must say the step did not complete.
     */
    @Test
    public void anIssueWhoseTaskFailsIsReportedAndDoesNotHangTheSession() throws Exception {
        machine.setEnabled(true);
        CalibrationRunner.OnMachine onMachine = new CalibrationRunner.OnMachine(machine, Configuration.get(), () -> null);
        Calibrating issue = new Calibrating(true);
        CalibrationPlan.Step step = CalibrationPlan.of(machine, List.of(issue.withCalibrationStep(CalibrationStep.SubPixel)))
                .getSteps(CalibrationStep.SubPixel).get(0);

        Exception failure = assertTimeoutPreemptively(Duration.ofSeconds(20),
                () -> assertThrows(Exception.class, () -> onMachine.accept(step, issue)));

        assertEquals(Solutions.State.Open, issue.getState());
        assertTrue(failure.getMessage().contains("Calibrate."), failure.getMessage());
        assertFalse(SwingUtilities.isEventDispatchThread());
    }
}
