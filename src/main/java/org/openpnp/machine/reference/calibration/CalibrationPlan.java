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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openpnp.Translations;
import org.openpnp.machine.reference.ReferenceHead;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.ReferenceNozzle;
import org.openpnp.machine.reference.ReferenceNozzleTip;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis;
import org.openpnp.machine.reference.camera.ReferenceCamera;
import org.openpnp.machine.reference.solutions.MachineDiagnostics;
import org.openpnp.machine.reference.solutions.MachineDiagnostics.TestGroup;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults;
import org.openpnp.machine.reference.solutions.VisionSolutions;
import org.openpnp.model.CalibrationStep;
import org.openpnp.model.Solutions;
import org.openpnp.spi.Axis;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Head;
import org.openpnp.spi.Machine;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.NozzleTip;
import org.openpnp.spi.base.AbstractHead;
import org.pmw.tinylog.Logger;

/**
 * The calibration of one machine as it stands: the catalogue of {@link CalibrationStep}s expanded
 * over the machine's axes, cameras, nozzles and nozzle tips, each with what it says about itself.
 * <p>
 * A step's status comes from three places. The issues tagged with it say what needs doing and
 * how: an issue that can be accepted is the step's action, one that cannot is evidence that it
 * needs doing again. The configuration says whether it was done, for steps whose issues only
 * appear once their prerequisites are - soft limits are not raised on a machine that is not
 * homed. And the measurements a step is judged by say whether they are there to judge by, and
 * current.
 * <p>
 * Built from issues found by the calibration page's own search, {@link #scan}; nothing is
 * changed by building it.
 */
public class CalibrationPlan {
    /** Where a step stands. */
    public enum Status {
        /** Nothing to do. */
        Done,
        /** It has to be done: an issue says so, or the configuration does. */
        Needed,
        /** It can be done, and is worth doing, but the machine works without it. */
        Suggested,
        /** It has to be done, after a step before it that is not done. */
        Waiting,
        /** What decides it has not been measured, or was measured before a step changed it. */
        NeedsMeasurement,
        /** Dismissed on the issues page, which counts as a decision not to. */
        Dismissed;

        public String getName() {
            return Translations.getString("CalibrationPlan.Status." + name()); //$NON-NLS-1$
        }

        /** Whether a step after it may go ahead. */
        public boolean isSettled() {
            return this == Done || this == Dismissed;
        }
    }

    /** What a step is judged by: an issue, or a measurement and when it was made. */
    public static final class Basis {
        private final Solutions.Issue issue;
        private final TestGroup group;
        private final Date when;

        Basis(Solutions.Issue issue, TestGroup group, Date when) {
            this.issue = issue;
            this.group = group;
            this.when = when;
        }

        /** The issue, or null for a basis that is a measurement alone. */
        public Solutions.Issue getIssue() {
            return issue;
        }

        /** The measurement group, or null for an issue read off the configuration. */
        public TestGroup getGroup() {
            return group;
        }

        /** When the group measured it, or null if it never has. */
        public Date getWhen() {
            return when;
        }
    }

    /** One step of the catalogue about one element of the machine. */
    public static final class Step {
        private final CalibrationStep kind;
        private final Object subject;
        private final List<Solutions.Issue> issues = new ArrayList<>();
        private final List<Step> prerequisites = new ArrayList<>();
        private final Set<TestGroup> missing = EnumSet.noneOf(TestGroup.class);
        private Status status = Status.Done;
        private Boolean configured;

        Step(CalibrationStep kind, Object subject) {
            this.kind = kind;
            this.subject = subject;
        }

        public CalibrationStep getKind() {
            return kind;
        }

        /** The axis, camera, nozzle, nozzle tip or head it is about, or the machine. */
        public Object getSubject() {
            return subject;
        }

        public String getSubjectName() {
            return nameOf(subject);
        }

        /** The step and what it is about, as a line of a report says it. */
        public String getTitle() {
            return subject instanceof Machine ? kind.getName() : kind.getName() + " \u00b7 " + getSubjectName(); //$NON-NLS-1$
        }

        /** Identifies the step across plans: the kind and the subject's id. */
        public String getKey() {
            return kind.name() + ":" + idOf(subject); //$NON-NLS-1$
        }

        public Status getStatus() {
            return status;
        }

        /** Every issue tagged with it, whatever its state. */
        public List<Solutions.Issue> getIssues() {
            return Collections.unmodifiableList(issues);
        }

        /**
         * What carries the step out: its open issues that can be accepted, in the order they were
         * found, which is the order the producer lays out a calibration in.
         */
        public List<Solutions.Issue> getActions() {
            List<Solutions.Issue> actions = new ArrayList<>();
            for (Solutions.Issue issue : issues) {
                if (issue.getState() == Solutions.State.Open && issue.canBeAccepted()) {
                    actions.add(issue);
                }
            }
            return actions;
        }

        /** The steps before it that have to be done first. */
        public List<Step> getPrerequisites() {
            return Collections.unmodifiableList(prerequisites);
        }

        /** The prerequisites that are not done, which a Waiting step waits for. */
        public List<Step> getUnsettledPrerequisites() {
            List<Step> unsettled = new ArrayList<>();
            for (Step prerequisite : prerequisites) {
                if (!prerequisite.getStatus().isSettled()) {
                    unsettled.add(prerequisite);
                }
            }
            return unsettled;
        }

        /** The measurement groups it is judged by that are missing or out of date. */
        public Set<TestGroup> getMissingMeasurements() {
            return Collections.unmodifiableSet(missing);
        }

        /** What each open issue would change, from the value as it stands to the proposal. */
        public List<SettingChange> getChanges() {
            List<SettingChange> changes = new ArrayList<>();
            for (Solutions.Issue issue : issues) {
                SettingChange change = SettingChange.of(issue);
                if (issue.getState() == Solutions.State.Open && change != null) {
                    changes.add(change);
                }
            }
            return changes;
        }

        /** Why the step has the status it has. */
        public List<Basis> getBasis(MachineDiagnosticsResults results) {
            List<Basis> basis = new ArrayList<>();
            for (Solutions.Issue issue : issues) {
                if (issue.getState() != Solutions.State.Open) {
                    continue;
                }
                TestGroup group = issue instanceof MachineDiagnostics.Finding
                        ? ((MachineDiagnostics.Finding) issue).getMeasuredBy()
                        : null;
                basis.add(new Basis(issue, group, when(results, group)));
            }
            for (TestGroup group : missing) {
                basis.add(new Basis(null, group, when(results, group)));
            }
            return basis;
        }

        public boolean isNeedsPerson() {
            return kind.isNeedsPerson();
        }

        public boolean isMovesMachine() {
            return kind.isMovesMachine();
        }

        @Override
        public String toString() {
            return getTitle() + " [" + status + "]"; //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    private final ReferenceMachine machine;
    private final MachineDiagnosticsResults results;
    private final List<Step> steps = new ArrayList<>();

    private CalibrationPlan(ReferenceMachine machine, MachineDiagnosticsResults results) {
        this.machine = machine;
        this.results = results;
    }

    /**
     * The calibration page's own search: a Solutions instance of its own, sharing the machine's
     * record of what was solved and dismissed, into which only the producers of calibration
     * issues report. Must run where Find Issues runs, on the event thread.
     * 
     * @return The issues tagged with a step, as found.
     */
    public static List<Solutions.Issue> scan(ReferenceMachine machine, Solutions shared) {
        Solutions solutions = Solutions.forCalibration(shared);
        solutions.findIssues(machine::findCalibrationIssues);
        solutions.publishIssues();
        List<Solutions.Issue> tagged = new ArrayList<>();
        for (Solutions.Issue issue : solutions.getIssues()) {
            if (issue.getCalibrationStep() != null) {
                tagged.add(issue);
            }
        }
        return tagged;
    }

    /** The plan for the machine from the issues its search found. */
    public static CalibrationPlan of(ReferenceMachine machine, List<Solutions.Issue> issues) {
        MachineDiagnostics diagnostics = machine.getMachineDiagnostics();
        CalibrationPlan plan = new CalibrationPlan(machine,
                diagnostics == null ? null : diagnostics.getLastResults());
        plan.expand();
        for (Solutions.Issue issue : issues) {
            if (issue.getCalibrationStep() != null) {
                plan.stepFor(issue.getCalibrationStep(), issue.getCalibrationSubject()).issues.add(issue);
            }
        }
        plan.sort();
        plan.link();
        plan.judge();
        return plan;
    }

    public List<Step> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    /** The steps of a kind, one for each element it applies to. */
    public List<Step> getSteps(CalibrationStep kind) {
        List<Step> found = new ArrayList<>();
        for (Step step : steps) {
            if (step.kind == kind) {
                found.add(step);
            }
        }
        return found;
    }

    /** The step by its key, or null if the machine no longer has it. */
    public Step getStep(String key) {
        for (Step step : steps) {
            if (step.getKey().equals(key)) {
                return step;
            }
        }
        return null;
    }

    public Step getStep(CalibrationStep kind, Object subject) {
        for (Step step : steps) {
            if (step.kind == kind && step.subject == scoped(kind, subject)) {
                return step;
            }
        }
        return null;
    }

    /** The steps that one-click calibration would run: all that are not done. */
    public List<Step> getOutstanding() {
        List<Step> outstanding = new ArrayList<>();
        for (Step step : steps) {
            if (!step.getStatus().isSettled()) {
                outstanding.add(step);
            }
        }
        return outstanding;
    }

    public MachineDiagnosticsResults getResults() {
        return results;
    }

    // ----- the catalogue --------------------------------------------------------------------------

    /** What a step's element is: the whole machine, a head, a nozzle, a camera, an axis. */
    private enum Scope {
        Machine,
        Head,
        Nozzle,
        Camera,
        Axis,
        NozzleTip
    }

    private static final Map<CalibrationStep, Scope> SCOPES = new EnumMap<>(CalibrationStep.class);
    /** The measurement groups a step is decided by; missing or out of date, they are measured first. */
    private static final Map<CalibrationStep, Set<TestGroup>> NEEDS = new EnumMap<>(CalibrationStep.class);
    /** The groups whose measurements a step's change makes out of date. */
    private static final Map<CalibrationStep, Set<TestGroup>> INVALIDATES = new EnumMap<>(CalibrationStep.class);
    /** The steps before it that a step needs done, and whether it needs them for its own element only. */
    private static final Map<CalibrationStep, List<Object[]>> PREREQUISITES = new EnumMap<>(CalibrationStep.class);

    private static void define(CalibrationStep kind, Scope scope, Set<TestGroup> needs, Set<TestGroup> invalidates,
            Object... prerequisites) {
        SCOPES.put(kind, scope);
        NEEDS.put(kind, needs);
        INVALIDATES.put(kind, invalidates);
        List<Object[]> list = new ArrayList<>();
        for (Object prerequisite : prerequisites) {
            if (prerequisite instanceof Object[]) {
                list.add((Object[]) prerequisite);
            }
            else {
                list.add(new Object[] { prerequisite, false });
            }
        }
        PREREQUISITES.put(kind, list);
    }

    /** A prerequisite about the same element, as the soft limits of the axis whose backlash is calibrated. */
    private static Object[] same(CalibrationStep kind) {
        return new Object[] { kind, true };
    }

    private static Set<TestGroup> none() {
        return EnumSet.noneOf(TestGroup.class);
    }

    private static Set<TestGroup> of(TestGroup first, TestGroup... rest) {
        return EnumSet.of(first, rest);
    }

    static {
        Set<TestGroup> fiducialBased = of(TestGroup.XyPositioning, TestGroup.Homing, TestGroup.DatumBoard,
                TestGroup.HysteresisMap, TestGroup.LostSteps, TestGroup.CameraSettle, TestGroup.VisionNoise,
                TestGroup.CameraLatency);
        define(CalibrationStep.Home, Scope.Machine, none(), none());
        define(CalibrationStep.SafeZ, Scope.Nozzle, none(), none(), CalibrationStep.Home);
        define(CalibrationStep.SoftLimits, Scope.Axis, none(), none(), CalibrationStep.Home);
        define(CalibrationStep.ManualNozzleTipChange, Scope.Nozzle, none(), none(), CalibrationStep.Home);
        define(CalibrationStep.ControllerLimits, Scope.Machine, of(TestGroup.Firmware),
                of(TestGroup.Kinematics, TestGroup.LostSteps));
        define(CalibrationStep.SubPixel, Scope.Machine, none(), of(TestGroup.VisionNoise, TestGroup.XyPositioning));
        define(CalibrationStep.Exposure, Scope.Camera, none(), of(TestGroup.VisionNoise));
        define(CalibrationStep.PrimaryFiducial, Scope.Head, none(), fiducialBased, CalibrationStep.Home,
                CalibrationStep.SafeZ);
        define(CalibrationStep.NozzleTouchPrimary, Scope.Nozzle, none(), of(TestGroup.ZFocus),
                CalibrationStep.PrimaryFiducial);
        define(CalibrationStep.SecondaryFiducial, Scope.Head, none(), none(), CalibrationStep.PrimaryFiducial,
                CalibrationStep.NozzleTouchPrimary);
        define(CalibrationStep.OtherNozzleOffsets, Scope.Nozzle, none(), none(),
                CalibrationStep.NozzleTouchPrimary);
        define(CalibrationStep.OtherCameraOffsets, Scope.Camera, none(), none(), CalibrationStep.PrimaryFiducial);
        define(CalibrationStep.BottomCamera, Scope.Camera, none(), of(TestGroup.ZFocus, TestGroup.RotationBacklash),
                CalibrationStep.NozzleTouchPrimary);
        define(CalibrationStep.CameraSettle, Scope.Camera, of(TestGroup.CameraSettle), none(),
                CalibrationStep.PrimaryFiducial);
        define(CalibrationStep.VisualHoming, Scope.Head, none(), of(TestGroup.Homing, TestGroup.DatumBoard),
                CalibrationStep.PrimaryFiducial);
        define(CalibrationStep.XyBacklash, Scope.Axis, none(),
                of(TestGroup.XyPositioning, TestGroup.LostSteps, TestGroup.DatumBoard, TestGroup.HysteresisMap),
                CalibrationStep.PrimaryFiducial, same(CalibrationStep.SoftLimits));
        define(CalibrationStep.FeedAcceleration, Scope.Axis, of(TestGroup.Kinematics, TestGroup.LostSteps),
                of(TestGroup.LostSteps, TestGroup.CameraSettle), CalibrationStep.PrimaryFiducial,
                CalibrationStep.ControllerLimits);
        define(CalibrationStep.ZBacklash, Scope.Axis, of(TestGroup.ZFocus), of(TestGroup.ZFocus),
                CalibrationStep.BottomCamera);
        define(CalibrationStep.RotationBacklash, Scope.Axis, of(TestGroup.RotationBacklash),
                of(TestGroup.RotationBacklash), CalibrationStep.BottomCamera);
        define(CalibrationStep.FrameCompensation, Scope.Machine, of(TestGroup.DatumBoard),
                of(TestGroup.DatumBoard, TestGroup.XyPositioning, TestGroup.HysteresisMap),
                CalibrationStep.PrimaryFiducial, CalibrationStep.XyBacklash);
        define(CalibrationStep.AdvancedDownCamera, Scope.Camera, none(), of(TestGroup.XyPositioning,
                TestGroup.DatumBoard, TestGroup.VisionNoise), CalibrationStep.SecondaryFiducial);
        define(CalibrationStep.AdvancedUpCamera, Scope.Camera, none(), none(), CalibrationStep.BottomCamera);
        define(CalibrationStep.NozzleTipCalibration, Scope.NozzleTip, none(), none(), CalibrationStep.BottomCamera);
        define(CalibrationStep.PreciseNozzleOffsets, Scope.Nozzle, none(), none(),
                CalibrationStep.NozzleTouchPrimary);
        define(CalibrationStep.Remeasure, Scope.Machine, none(), none());
    }

    /** The groups a step's change makes out of date, which the runner marks when it succeeds. */
    public static Set<TestGroup> invalidatedBy(CalibrationStep kind) {
        return Collections.unmodifiableSet(INVALIDATES.get(kind));
    }

    /** The groups a step is decided by. */
    public static Set<TestGroup> decidedBy(CalibrationStep kind) {
        return Collections.unmodifiableSet(NEEDS.get(kind));
    }

    // ----- building ---------------------------------------------------------------------------------

    /** Every step the machine's elements call for, before any issue is attached. */
    private void expand() {
        stepFor(CalibrationStep.Home, machine);
        stepFor(CalibrationStep.ControllerLimits, machine);
        stepFor(CalibrationStep.SubPixel, machine);
        for (Head head : machine.getHeads()) {
            Nozzle defaultNozzle = defaultNozzle(head);
            Camera defaultCamera = defaultCamera(head);
            if (head instanceof ReferenceHead && defaultCamera != null) {
                stepFor(CalibrationStep.PrimaryFiducial, head);
                stepFor(CalibrationStep.SecondaryFiducial, head);
                stepFor(CalibrationStep.VisualHoming, head);
            }
            for (Nozzle nozzle : head.getNozzles()) {
                stepFor(CalibrationStep.SafeZ, nozzle);
                if (nozzle instanceof ReferenceNozzle) {
                    stepFor(CalibrationStep.ManualNozzleTipChange, nozzle);
                }
                stepFor(nozzle == defaultNozzle ? CalibrationStep.NozzleTouchPrimary
                        : CalibrationStep.OtherNozzleOffsets, nozzle);
                stepFor(CalibrationStep.PreciseNozzleOffsets, nozzle);
            }
            for (Camera camera : head.getCameras()) {
                if (!(camera instanceof ReferenceCamera)) {
                    continue;
                }
                stepFor(CalibrationStep.Exposure, camera);
                if (camera != defaultCamera) {
                    stepFor(CalibrationStep.OtherCameraOffsets, camera);
                }
                stepFor(CalibrationStep.CameraSettle, camera);
                stepFor(CalibrationStep.AdvancedDownCamera, camera);
            }
        }
        for (Camera camera : machine.getCameras()) {
            if (camera instanceof ReferenceCamera && camera.getLooking() == Camera.Looking.Up) {
                stepFor(CalibrationStep.Exposure, camera);
                stepFor(CalibrationStep.BottomCamera, camera);
                stepFor(CalibrationStep.CameraSettle, camera);
                stepFor(CalibrationStep.AdvancedUpCamera, camera);
            }
        }
        for (Axis axis : machine.getAxes()) {
            if (!(axis instanceof ReferenceControllerAxis)) {
                continue;
            }
            switch (axis.getType()) {
                case X:
                case Y:
                    stepFor(CalibrationStep.SoftLimits, axis);
                    stepFor(CalibrationStep.XyBacklash, axis);
                    stepFor(CalibrationStep.FeedAcceleration, axis);
                    break;
                case Z:
                    stepFor(CalibrationStep.ZBacklash, axis);
                    break;
                case Rotation:
                    stepFor(CalibrationStep.RotationBacklash, axis);
                    break;
                default:
                    break;
            }
        }
        stepFor(CalibrationStep.FrameCompensation, machine);
        for (NozzleTip nozzleTip : machine.getNozzleTips()) {
            if (nozzleTip instanceof ReferenceNozzleTip) {
                stepFor(CalibrationStep.NozzleTipCalibration, nozzleTip);
            }
        }
        stepFor(CalibrationStep.Remeasure, machine);
    }

    /** The subject a step of the kind is kept under: the machine for a step about the whole of it. */
    private Object scoped(CalibrationStep kind, Object subject) {
        return SCOPES.get(kind) == Scope.Machine ? machine : subject;
    }

    private Step stepFor(CalibrationStep kind, Object subject) {
        Object scoped = scoped(kind, subject);
        for (Step step : steps) {
            if (step.kind == kind && step.subject == scoped) {
                return step;
            }
        }
        Step step = new Step(kind, scoped);
        steps.add(step);
        return step;
    }

    /** Into the catalogue's order, keeping the elements of a kind in the order they were met. */
    private void sort() {
        List<Step> sorted = new ArrayList<>(steps);
        sorted.sort((a, b) -> Integer.compare(a.kind.ordinal(), b.kind.ordinal()));
        steps.clear();
        steps.addAll(sorted);
    }

    private void link() {
        for (Step step : steps) {
            for (Object[] prerequisite : PREREQUISITES.get(step.kind)) {
                CalibrationStep kind = (CalibrationStep) prerequisite[0];
                boolean sameElement = (Boolean) prerequisite[1];
                for (Step before : getSteps(kind)) {
                    if (!sameElement || before.subject == step.subject) {
                        step.prerequisites.add(before);
                    }
                }
            }
        }
    }

    /** Statuses in catalogue order, so that every prerequisite is judged before what needs it. */
    private void judge() {
        for (Step step : steps) {
            step.configured = configured(step);
            for (TestGroup group : NEEDS.get(step.kind)) {
                if (decides(step, group) && (results == null || !results.isCurrent(group))) {
                    step.missing.add(group);
                }
            }
            Status own = ownStatus(step);
            if ((own == Status.Needed || own == Status.Suggested || own == Status.NeedsMeasurement)
                    && !step.getUnsettledPrerequisites().isEmpty()) {
                own = Status.Waiting;
            }
            step.status = own;
        }
    }

    /**
     * Whether the group decides the step for its element: the settling test measures the head's
     * default camera only, and a camera it never looks through is decided by its issues alone.
     */
    private boolean decides(Step step, TestGroup group) {
        if (step.kind == CalibrationStep.CameraSettle) {
            Camera camera = (Camera) step.subject;
            return camera.getHead() != null && camera == defaultCamera(camera.getHead());
        }
        return true;
    }

    private Status ownStatus(Step step) {
        boolean openAction = false;
        boolean openEvidence = false;
        boolean suggestionOnly = true;
        int dismissed = 0;
        int solved = 0;
        for (Solutions.Issue issue : step.issues) {
            switch (issue.getState()) {
                case Open:
                    if (issue.canBeAccepted()) {
                        openAction = true;
                    }
                    else {
                        openEvidence = true;
                    }
                    if (issue.getSeverity().ordinal() > Solutions.Severity.Suggestion.ordinal()) {
                        suggestionOnly = false;
                    }
                    break;
                case Dismissed:
                    dismissed++;
                    break;
                case Solved:
                    solved++;
                    break;
                default:
                    break;
            }
        }
        if (step.kind == CalibrationStep.Remeasure) {
            return invalidated().isEmpty() ? Status.Done : Status.Needed;
        }
        if (openAction || openEvidence) {
            return openAction && suggestionOnly && !openEvidence ? Status.Suggested : Status.Needed;
        }
        if (dismissed > 0 && solved == 0) {
            return Status.Dismissed;
        }
        if (!step.missing.isEmpty()) {
            return Status.NeedsMeasurement;
        }
        if (step.configured != null && !step.configured) {
            return Status.Needed;
        }
        return Status.Done;
    }

    /** The groups whose measurements a calibration step has made out of date. */
    public Set<TestGroup> invalidated() {
        Set<TestGroup> invalidated = new LinkedHashSet<>();
        if (results != null) {
            for (TestGroup group : TestGroup.values()) {
                MachineDiagnosticsResults.Run run = results.getRun(group);
                if (run != null && run.isInvalidated()) {
                    invalidated.add(group);
                }
            }
        }
        return invalidated;
    }

    /**
     * What the configuration says about a step whose issues do not, or null where the issues are
     * the whole story. The issues of most steps only appear once the steps before them are done:
     * a machine that is not homed raises no soft limit issue, and has soft limits all the same.
     */
    private Boolean configured(Step step) {
        try {
            VisionSolutions vision = machine.getVisionSolutions();
            switch (step.kind) {
                case Home:
                    return machine.isHomed();
                case SafeZ: {
                    // Which side is the safe one depends on which way the axis points, which only
                    // the kinematics can work out; either side set is Safe Z taught.
                    Object rawZ = org.openpnp.machine.reference.solutions.HeadSolutions.getRawAxis(machine,
                            ((org.openpnp.spi.HeadMountable) step.subject).getAxisZ());
                    if (rawZ instanceof ReferenceControllerAxis) {
                        ReferenceControllerAxis z = (ReferenceControllerAxis) rawZ;
                        return z.isSafeZoneLowEnabled() || z.isSafeZoneHighEnabled();
                    }
                    return null;
                }
                case SoftLimits: {
                    ReferenceControllerAxis axis = (ReferenceControllerAxis) step.subject;
                    return axis.isSoftLimitLowEnabled() && axis.isSoftLimitHighEnabled();
                }
                case ManualNozzleTipChange:
                    return ((ReferenceNozzle) step.subject).getManualNozzleTipChangeLocation().isInitialized();
                case PrimaryFiducial:
                    return vision.isSolvedPrimaryXY((ReferenceHead) step.subject);
                case SecondaryFiducial:
                    return vision.isSolvedSecondaryXY((ReferenceHead) step.subject)
                            && vision.isSolvedSecondaryZ((ReferenceHead) step.subject);
                case NozzleTouchPrimary: {
                    Nozzle nozzle = (Nozzle) step.subject;
                    return nozzle.getHead() instanceof ReferenceHead
                            && vision.isSolvedPrimaryZ((ReferenceHead) nozzle.getHead());
                }
                case OtherNozzleOffsets:
                    return ((Nozzle) step.subject).getHeadOffsets().isInitialized();
                case OtherCameraOffsets:
                    return ((ReferenceCamera) step.subject).getUnitsPerPixelPrimary().isInitialized();
                case BottomCamera: {
                    ReferenceCamera camera = (ReferenceCamera) step.subject;
                    return camera.getLocation().isInitialized()
                            && camera.getUnitsPerPixelPrimary().isInitialized();
                }
                case NozzleTipCalibration:
                    return ((ReferenceNozzleTip) step.subject).getCalibration().isEnabled();
                default:
                    return null;
            }
        }
        catch (Exception e) {
            // A part of the machine a check cannot read says nothing either way.
            Logger.trace(e, "Calibration check of {} failed.", step.getTitle()); //$NON-NLS-1$
            return null;
        }
    }

    // ----- the machine -----------------------------------------------------------------------------

    private static Nozzle defaultNozzle(Head head) {
        try {
            return head.getDefaultNozzle();
        }
        catch (Exception e) {
            return null;
        }
    }

    private static Camera defaultCamera(Head head) {
        try {
            return head.getDefaultCamera();
        }
        catch (Exception e) {
            return null;
        }
    }

    private static Date when(MachineDiagnosticsResults results, TestGroup group) {
        if (results == null || group == null) {
            return null;
        }
        MachineDiagnosticsResults.Run run = results.getRun(group);
        return run == null ? null : run.getWhen();
    }

    /** What an element of the machine is called where the calibration page names it. */
    public static String nameOf(Object subject) {
        if (subject instanceof Machine) {
            return Translations.getString("CalibrationPlan.Machine"); //$NON-NLS-1$
        }
        if (subject instanceof org.openpnp.model.Named && ((org.openpnp.model.Named) subject).getName() != null) {
            return ((org.openpnp.model.Named) subject).getName();
        }
        if (subject instanceof AbstractHead) {
            return ((AbstractHead) subject).getName();
        }
        return String.valueOf(subject);
    }

    static String idOf(Object subject) {
        if (subject instanceof Machine) {
            return "machine"; //$NON-NLS-1$
        }
        if (subject instanceof org.openpnp.model.Identifiable) {
            return ((org.openpnp.model.Identifiable) subject).getId();
        }
        return nameOf(subject);
    }
}
