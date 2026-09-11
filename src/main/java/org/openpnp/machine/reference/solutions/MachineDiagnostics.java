/*
 * Copyright (C) 2026 Pono contributors
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

package org.openpnp.machine.reference.solutions;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.opencv.core.Mat;
import org.opencv.core.RotatedRect;
import org.openpnp.machine.reference.ReferenceHead;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.ReferenceNozzleTip;
import org.openpnp.machine.reference.ReferenceNozzleTipCalibration;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis.BacklashCompensationMethod;
import org.openpnp.machine.reference.camera.AbstractSettlingCamera;
import org.openpnp.machine.reference.camera.ReferenceCamera;
import org.openpnp.machine.reference.driver.GcodeDriver;
import org.openpnp.machine.reference.vision.ReferenceBottomVision;
import org.openpnp.machine.reference.vision.ReferenceFiducialLocator;
import org.openpnp.model.AxesLocation;
import org.openpnp.model.BottomVisionSettings;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Part;
import org.openpnp.model.Solutions;
import org.openpnp.model.Solutions.Milestone;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsMath.GcodeSettingLine;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsMath.LinearFit;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsMath.MotionFit;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsMath.Stats;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsReport.Severity;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults.ControllerLimits;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults.FieldOfView;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults.Motion;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults.Positioning;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults.Settling;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.spi.Axis;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Camera.Looking;
import org.openpnp.spi.Driver;
import org.openpnp.spi.Head;
import org.openpnp.spi.HeadMountable;
import org.openpnp.spi.LinearTransformAxis;
import org.openpnp.spi.MotionPlanner.CompletionType;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.NozzleTip;
import org.openpnp.spi.PartAlignment;
import org.openpnp.spi.base.AbstractCamera;
import org.openpnp.spi.base.AbstractSingleTransformedAxis;
import org.openpnp.util.MovableUtils;
import org.openpnp.util.NanosecondTime;
import org.openpnp.util.OpenCvUtils;
import org.openpnp.util.SimpleGraph;
import org.openpnp.util.Utils2D;
import org.openpnp.util.VisionUtils;
import org.openpnp.vision.pipeline.CvPipeline;
import org.openpnp.vision.pipeline.CvStage.Result.Circle;
import org.openpnp.vision.pipeline.stages.DetectCircularSymmetry;
import org.openpnp.vision.pipeline.stages.DetectCircularSymmetry.ScoreRange;
import org.pmw.tinylog.Logger;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;

/**
 * Measures what the machine actually does, as opposed to what it is configured to do, and writes
 * the result out as a report.
 * <p>
 * Issues and Solutions calibrates: it measures in order to change a setting, and each of its
 * steps is tied to the setting it sets. This asks the complementary question - given the settings
 * as they stand, how does the machine behave - and so it measures things no calibration step
 * needs, such as the acceleration the controller really reaches, the scatter of repeated
 * approaches, how long the image takes to stop moving, and how far homing wanders. None of it
 * changes any setting.
 * <p>
 * Every test group is independent: one that cannot run, because the machine lacks what it needs,
 * or that fails, is recorded as such and the rest still run.
 */
public class MachineDiagnostics extends AbstractModelObject implements Solutions.Subject {
    /** Three points always describe a circle, so a run-out fit from three proves nothing. */
    private static final int MINIMUM_USEFUL_FIT = 3;
    /** Tolerating one lets a single bad frame through without hiding a real eccentricity. */
    private static final int DEFAULT_ALLOW_MISDETECTIONS = 1;
    /** Below this the two fiducials are the same place as far as any calibration can tell. */
    private static final Length HOMING_FIDUCIAL_TOLERANCE = new Length(0.2, LengthUnit.Millimeters);
    /** A controller limit this far below the axis setting is a cap, not a rounding difference. */
    private static final double LIMIT_TOLERANCE = 1.05;
    /** Below this fraction of what it is planned with, a limit describes a different machine. */
    private static final double SHORTFALL_FRACTION = 0.7;
    /** Homing scatter beyond this is worth acting on, as the report also reads it. */
    private static final double HOMING_SCATTER_TOLERANCE_MM = 0.05;
    /** A Units per Pixel error beyond this is visible across a board. */
    private static final double SCALE_ERROR_TOLERANCE = 0.005;
    /** Rotation backlash below this is not worth compensating. */
    private static final double ROTATION_BACKLASH_TOLERANCE = 0.2;
    /** Margin over the measured backlash for a one-sided offset, which has to clear it. */
    private static final double BACKLASH_OFFSET_MARGIN = 1.2;
    /** Margin over the measured settle time for a fixed wait, which has to outlast it. */
    private static final double SETTLE_MARGIN = 1.5;
    /** A fixed wait longer than this many times the measured time is worth shortening. */
    private static final double SETTLE_EXCESS = 2.5;

    private static final String WIKI_MOTION_PLANNER =
            "https://github.com/openpnp/openpnp/wiki/Motion-Planner";
    private static final String WIKI_MACHINE_AXES =
            "https://github.com/openpnp/openpnp/wiki/Machine-Axes";
    private static final String WIKI_CAMERA_SETTLING =
            "https://github.com/openpnp/openpnp/wiki/Camera-Settling";
    private static final String WIKI_CALIBRATION_SOLUTIONS =
            "https://github.com/openpnp/openpnp/wiki/Calibration-Solutions#advanced-camera-calibration";
    private static final String WIKI_VISUAL_HOMING =
            "https://github.com/openpnp/openpnp/wiki/Visual-Homing";

    public enum TestGroup {
        Firmware,
        Kinematics,
        XyPositioning,
        CameraSettle,
        Homing,
        RotationBacklash,
        ConfigSnapshot
    }

    @Attribute(required = false)
    private int repeats = 5;

    @Element(required = false)
    private String timingDistances = "0.5, 1, 2, 5, 10, 20, 50, 100, 200";

    @Element(required = false)
    private String positioningDistances = "1, 10, 50";

    @Element(required = false)
    private String speedFactors = "0.25, 0.5, 1";

    @Attribute(required = false)
    private boolean timingIncludesZAndRotation = true;

    @Element(required = false)
    private String rotationTimingAngles = "5, 15, 45, 90, 180";

    @Attribute(required = false)
    private double stepTestDistanceMm = 0.3;

    @Attribute(required = false)
    private double stepTestStepMm = 0.01;

    @Attribute(required = false)
    private int fieldOfViewGridSteps = 7;

    @Attribute(required = false)
    private double fieldOfViewGridFraction = 0.4;

    @Element(required = false)
    private String settleDistances = "2, 20, 100";

    @Attribute(required = false)
    private double settleSampleSeconds = 2.0;

    @Attribute(required = false)
    private double settleThresholdPixels = 0.5;

    @Attribute(required = false)
    private int homingCycles = 3;

    @Element(required = false)
    private String rotationTestAngles = "0, 30, -30";

    @Attribute(required = false)
    private double rotationApproachAngle = 5.0;

    @Attribute(required = false)
    private String rotationTestPartId;

    @Attribute(required = false)
    private long machineSettleMs = 500;

    @Element(required = false, data = true)
    private String firmwareCommands = "M115\nM503\nM114\nM119";

    /**
     * What the last run of each test group concluded. Persisted, because the measurements take
     * long enough that nobody repeats them to be told something they were told last week, and
     * because the checks in {@link #findIssues} run on every Find Issues rather than only while
     * the report is still on screen.
     */
    @Element(required = false)
    private MachineDiagnosticsResults lastResults;

    private Configuration configuration;
    /**
     * Deliberately not serialized: the machine owns this object, so naming it back would put a
     * cycle in machine.xml. Issues and Solutions hands it over on every findIssues.
     */
    private ReferenceMachine machine;
    private volatile boolean aborting;
    private volatile boolean running;
    private final StringBuilder logText = new StringBuilder();
    private SimpleGraph timingGraph;
    private SimpleGraph positioningGraph;
    private SimpleGraph stepGraph;
    private SimpleGraph settleGraph;
    private File lastReportDirectory;

    private static final Color COLOR_X = new Color(0x00, 0x5B, 0xD9);
    private static final Color COLOR_Y = new Color(0x00, 0x77, 0x00);
    private static final Color COLOR_Z = new Color(0xBB, 0x77, 0x00);
    private static final Color COLOR_ROTATION = new Color(0x99, 0x00, 0x99);
    private static final Color COLOR_MEASURED = new Color(0xFF, 0x00, 0x00);
    private static final Color COLOR_MODEL = new Color(0x00, 0x00, 0x77);

    /** Thrown when the user pressed stop. Distinct from a failure, so it is not reported as one. */
    private static class AbortedException extends Exception {
        private static final long serialVersionUID = 1L;

        AbortedException() {
            super("Stopped.");
        }
    }

    /**
     * Handed the configuration by the machine this hangs off, so that the report directory and
     * the part registry can be reached without going to the singleton for them.
     */
    public void configurationLoaded(Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * Handed the machine when Issues and Solutions asks this for issues, the same way the five
     * solutions classes are. {@link #run} takes the machine as a parameter instead, because it is
     * called from the wizard, which has one.
     */
    public MachineDiagnostics setMachine(ReferenceMachine machine) {
        this.machine = machine;
        return this;
    }

    /**
     * The findings this contributes to Issues and Solutions, of two kinds. Settings that are
     * wrong on their own terms are read straight off the configuration. The rest hold a setting
     * against what {@link #run} last measured, and so appear only on a machine that has been
     * measured. Both are re-evaluated on every Find Issues.
     * <p>
     * The wording of an issue must not carry a number. The fingerprint that remembers a dismissal
     * is a hash of the subject, the issue and the solution, so a number in the text would give the
     * same finding a new identity every time the value changed, and the dismissal would be
     * forgotten. Numbers belong in the extended description and in the properties.
     */
    @Override
    public void findIssues(Solutions solutions) {
        if (machine == null || !solutions.isTargeting(Milestone.Calibration)) {
            return;
        }
        for (NozzleTip nozzleTip : machine.getNozzleTips()) {
            if (nozzleTip instanceof ReferenceNozzleTip) {
                findRunOutFitIssues(solutions, (ReferenceNozzleTip) nozzleTip);
            }
        }
        findSuperSamplingIssues(solutions);
        for (Head head : machine.getHeads()) {
            if (head instanceof ReferenceHead) {
                findHomingFiducialIssues(solutions, (ReferenceHead) head);
            }
        }
        if (lastResults != null) {
            findMeasuredIssues(solutions, lastResults);
        }
    }

    /**
     * A run-out model fitted to three points is a circle through three points: it cannot fail, and
     * it cannot tell a real eccentricity from noise. The calibration accepts as few as
     * {@code max(3, angleSubdivisions + 1 - allowMisdetections)} measurements, so once
     * allowMisdetections is raised far enough that expression collapses onto its own floor and the
     * calibration reports success from three. Found on the LumenPnP configuration, where six
     * nozzle tips allowed five misdetections out of six angles.
     */
    private void findRunOutFitIssues(Solutions solutions, ReferenceNozzleTip nozzleTip) {
        ReferenceNozzleTipCalibration calibration = nozzleTip.getCalibration();
        if (!calibration.isEnabled() || requiredMeasurements(calibration) > MINIMUM_USEFUL_FIT) {
            return;
        }
        solutions.add(new DiagnosticIssue(nozzleTip,
                "Nozzle tip run-out calibration accepts too few measurements to be meaningful.",
                "Reduce the misdetections it tolerates.",
                Solutions.Severity.Warning,
                "https://github.com/openpnp/openpnp/wiki/Nozzle-Tip-Calibration") {
            private final int oldAllowMisdetections = calibration.getAllowMisdetections();
            private int proposed = DEFAULT_ALLOW_MISDETECTIONS;

            @Override
            protected String extendedDescription() {
                return "The calibration measures at " + (calibration.getAngleSubdivisions() + 1)
                        + " angles and tolerates " + oldAllowMisdetections
                        + " misdetections, so it will fit the run-out model to as few as "
                        + requiredMeasurements(calibration) + " points. Three points always yield "
                        + "a circle, whatever the measurements were, so the fit stops being "
                        + "evidence of anything. Accepting sets the tolerance so that a "
                        + "substantially complete set of angles is required.";
            }

            @Override
            public Solutions.Issue.CustomProperty[] getProperties() {
                return new Solutions.Issue.CustomProperty[] {
                        new Solutions.Issue.IntegerProperty("Misdetections tolerated",
                                "How many of the angles may fail to be detected before the "
                                        + "calibration gives up.",
                                0, Math.max(0, calibration.getAngleSubdivisions() - 2)) {
                            @Override
                            public int get() {
                                return proposed;
                            }

                            @Override
                            public void set(int value) {
                                proposed = value;
                            }
                        },
                };
            }

            @Override
            public void setState(Solutions.State state) throws Exception {
                calibration.setAllowMisdetections(
                        state == Solutions.State.Solved ? proposed : oldAllowMisdetections);
                super.setState(state);
            }
        });
    }

    /**
     * @return the smallest number of successful measurements the calibration will fit a run-out
     *         model to, mirroring the check in ReferenceNozzleTipCalibration.
     */
    private static int requiredMeasurements(ReferenceNozzleTipCalibration calibration) {
        return Math.max(MINIMUM_USEFUL_FIT,
                calibration.getAngleSubdivisions() + 1 - calibration.getAllowMisdetections());
    }

    /**
     * Circular symmetry locates a fiducial or a nozzle tip to the nearest pixel when super
     * sampling is off, which puts a floor under every calibration built on it. The target is the
     * value Issues and Solutions already uses for its own symmetry detection, rather than a second
     * number of our own.
     */
    private void findSuperSamplingIssues(Solutions solutions) {
        int target = machine.getVisionSolutions().getSuperSampling();
        if (target <= 1) {
            return;
        }
        for (Map.Entry<Solutions.Subject, CvPipeline> entry : symmetryPipelines().entrySet()) {
            for (DetectCircularSymmetry stage : symmetryStages(entry.getValue())) {
                if (stage.getSuperSampling() > 1) {
                    continue;
                }
                solutions.add(new DiagnosticIssue(entry.getKey(),
                        "Circular symmetry detection is limited to whole pixels.",
                        "Switch super sampling on.",
                        Solutions.Severity.Suggestion,
                        "https://github.com/openpnp/openpnp/wiki/Vision-Solutions") {
                    @Override
                    protected String extendedDescription() {
                        return "The pipeline's DetectCircularSymmetry stage has super sampling "
                                + "set to " + stage.getSuperSampling() + ", so it reports a "
                                + "position to the nearest whole pixel and every calibration "
                                + "built on it inherits that as its floor. Accepting sets it to "
                                + target + ", which is what the vision solutions use for their own "
                                + "detection.";
                    }

                    @Override
                    public void setState(Solutions.State state) throws Exception {
                        stage.setSuperSampling(state == Solutions.State.Solved ? target : 1);
                        super.setState(state);
                    }
                });
            }
        }
    }

    /** The pipelines whose symmetry detection the calibrations depend on. */
    private Map<Solutions.Subject, CvPipeline> symmetryPipelines() {
        Map<Solutions.Subject, CvPipeline> pipelines = new LinkedHashMap<>();
        if (machine.getFiducialLocator() instanceof ReferenceFiducialLocator) {
            ReferenceFiducialLocator locator = (ReferenceFiducialLocator) machine.getFiducialLocator();
            if (locator.getPipeline() != null) {
                // The locator is not a Solutions.Subject and it is machine level anyway, so the
                // machine stands in as the subject of anything found in its pipeline.
                pipelines.put(machine, locator.getPipeline());
            }
        }
        for (NozzleTip nozzleTip : machine.getNozzleTips()) {
            if (nozzleTip instanceof ReferenceNozzleTip) {
                ReferenceNozzleTip referenceNozzleTip = (ReferenceNozzleTip) nozzleTip;
                CvPipeline pipeline = referenceNozzleTip.getCalibration().getPipeline();
                if (referenceNozzleTip.getCalibration().isEnabled() && pipeline != null) {
                    pipelines.put(referenceNozzleTip, pipeline);
                }
            }
        }
        return pipelines;
    }

    private static List<DetectCircularSymmetry> symmetryStages(CvPipeline pipeline) {
        List<DetectCircularSymmetry> stages = new ArrayList<>();
        pipeline.getStages().forEach(stage -> {
            if (stage instanceof DetectCircularSymmetry) {
                stages.add((DetectCircularSymmetry) stage);
            }
        });
        return stages;
    }

    /**
     * Visual homing drives to the homing fiducial and takes the position it finds there as the
     * origin, so if that fiducial is not the one the calibrations were measured against, every
     * calibrated offset is shifted by the distance between them. There is no automatic fix: only
     * the user knows which of the two positions is the correct one.
     */
    private void findHomingFiducialIssues(Solutions solutions, ReferenceHead head) {
        if (head.getVisualHomingMethod() == ReferenceHead.VisualHomingMethod.None) {
            return;
        }
        Location homing = head.getHomingFiducialLocation();
        Location primary = head.getCalibrationPrimaryFiducialLocation();
        if (homing == null || primary == null
                || primary.getLinearLengthTo(homing).compareTo(HOMING_FIDUCIAL_TOLERANCE) <= 0) {
            return;
        }
        solutions.add(new PointerIssue(head,
                "The homing fiducial and the primary calibration fiducial are not the same place.",
                "Re-capture the homing fiducial at the primary calibration fiducial, or confirm "
                        + "that they are deliberately different.",
                Solutions.Severity.Warning, WIKI_VISUAL_HOMING,
                String.format("Visual homing takes the position it finds at the homing fiducial "
                        + "as the machine origin, and the camera calibrations were measured "
                        + "against the primary calibration fiducial. These two are %.4f mm apart, "
                        + "so every calibrated offset carries that difference. This is reported "
                        + "rather than corrected, because which of the two positions is the right "
                        + "one is not something the machine can know.",
                        primary.getLinearLengthTo(homing)
                                .convertToUnits(LengthUnit.Millimeters).getValue())));
    }

    /**
     * The checks that hold a setting against what the machine was measured doing. Each one needs
     * a conclusion from the last run, so none of them appears on a machine that has never been
     * measured, and each stops appearing once the setting and the measurement agree again.
     * <p>
     * A measured value is offered as an adjustable property and written only on Accept, rather
     * than applied here. The number came from one run on one day, and whether that run was
     * representative is not something this can know.
     */
    private void findMeasuredIssues(Solutions solutions, MachineDiagnosticsResults results) {
        for (ControllerLimits limits : results.getControllerLimits()) {
            ReferenceControllerAxis axis = controllerAxis(limits.getAxisId());
            if (axis != null) {
                findControllerLimitIssues(solutions, axis, limits,
                        measuredWhen(results, TestGroup.Firmware));
            }
        }
        for (Motion motion : results.getMotion()) {
            ReferenceControllerAxis axis = controllerAxis(motion.getAxisId());
            if (axis != null) {
                findMotionShortfallIssues(solutions, axis, motion,
                        measuredWhen(results, TestGroup.Kinematics));
            }
        }
        for (Positioning positioning : results.getPositioning()) {
            ReferenceControllerAxis axis = controllerAxis(positioning.getAxisId());
            if (axis != null) {
                findBacklashOffsetIssue(solutions, axis, positioning,
                        measuredWhen(results, TestGroup.XyPositioning));
            }
        }
        for (Settling settling : results.getSettling()) {
            ReferenceCamera camera = camera(settling.getCameraId());
            if (camera != null) {
                findSettleTimeIssues(solutions, camera, settling,
                        measuredWhen(results, TestGroup.CameraSettle));
            }
        }
        findFieldOfViewIssues(solutions, results);
        findHomingScatterIssue(solutions, results);
        findRotationBacklashIssue(solutions, results);
    }

    /**
     * What the controller says about itself, against what the axis is set to.
     * <p>
     * A controller limit below the axis setting caps every move without saying so, which makes
     * the planner's timing - and the speed factors it scales against that timing - describe a
     * machine that does not exist. A resolution finer than the controller's step is the same
     * disagreement in the other direction: coordinates are sent that the machine cannot take up.
     */
    private void findControllerLimitIssues(Solutions solutions, ReferenceControllerAxis axis,
            ControllerLimits limits, String when) {
        // The controller's figures are read as being in the units the axis is planned in, which
        // is the assumption the measurement itself was taken under.
        String unit = axisUnit(axis);
        Double feedRate = limits.getMaxFeedRate();
        if (feedRate != null && axis.getMotionLimit(1) > feedRate * LIMIT_TOLERANCE) {
            Length previous = axis.getFeedratePerSecond();
            solutions.add(new LengthSettingIssue(axis,
                    "The axis is planned with a feed rate the controller will not allow.",
                    "Lower the axis feed rate to the controller's own limit.",
                    Solutions.Severity.Warning, WIKI_MOTION_PLANNER,
                    "Feed rate per second",
                    "The feed rate the axis will be planned with.",
                    String.format("The controller reported a maximum feed rate of %.0f %s/s for "
                            + "this axis on %s, and the axis is set to be planned at %.0f %s/s. The "
                            + "controller caps every move at its own figure without reporting "
                            + "that it did, so the planner's move times, and the speed factors "
                            + "it scales against them, are computed for a machine that is not "
                            + "there.", feedRate, unit, when, axis.getMotionLimit(1), unit),
                    previous, new Length(feedRate, AxesLocation.getUnits()),
                    (value, solved) -> axis.setFeedratePerSecond(value)));
        }
        Double acceleration = limits.getMaxAcceleration();
        if (acceleration != null && axis.getMotionLimit(2) > acceleration * LIMIT_TOLERANCE) {
            Length previous = axis.getAccelerationPerSecond2();
            solutions.add(new LengthSettingIssue(axis,
                    "The axis is planned with an acceleration the controller will not allow.",
                    "Lower the axis acceleration to the controller's own limit.",
                    Solutions.Severity.Warning, WIKI_MOTION_PLANNER,
                    "Acceleration per second squared",
                    "The acceleration the axis will be planned with.",
                    String.format("The controller reported a maximum acceleration of %.0f %s/s² "
                            + "for this axis on %s, and the axis is set to be planned at %.0f "
                            + "%s/s². "
                            + "Every move is capped at the controller's figure, so the planned "
                            + "ramps are shorter than the real ones and the machine is still "
                            + "moving when the plan says it has arrived.", acceleration, unit,
                            when, axis.getMotionLimit(2), unit),
                    previous, new Length(acceleration, AxesLocation.getUnits()),
                    (value, solved) -> axis.setAccelerationPerSecond2(value)));
        }
        Double steps = limits.getStepsPerUnit();
        if (steps != null && steps > 0 && axis.getDriver() != null) {
            LengthUnit driverUnits = axis.getDriver().getUnits();
            double step = 1.0 / steps;
            if (axis.getResolution() < step * 0.5) {
                double previous = axis.getResolution();
                solutions.add(new LengthSettingIssue(axis,
                        "The axis resolution is finer than the smallest step the controller can "
                                + "make.",
                        "Set the resolution to one controller step.",
                        Solutions.Severity.Warning, WIKI_MACHINE_AXES,
                        "Resolution",
                        "The smallest difference in coordinate the axis is asked to make.",
                        String.format("The controller reported %.4f steps per unit for this axis "
                                + "on %s, so its smallest step is %.5f %s, while the axis "
                                + "resolution is set to %.5f %s. Resolution is what decides "
                                + "whether a coordinate counts as a move at all, so a value "
                                + "below one step sends moves the machine cannot make and "
                                + "reports them as done.", steps, when, step,
                                driverUnits.getShortName(), previous,
                                driverUnits.getShortName()),
                        new Length(previous, driverUnits), new Length(step, driverUnits),
                        (value, solved) -> axis.setResolution(
                                value.convertToUnits(driverUnits).getValue())));
            }
        }
    }

    /**
     * An axis that reaches only a fraction of the limit it is planned with. The planner works out
     * every move time from these two numbers, and scales its speed factors against them, so a
     * limit the machine never reaches makes a job slower than the plan says and puts the
     * deceleration somewhere other than where it was planned.
     */
    private void findMotionShortfallIssues(Solutions solutions, ReferenceControllerAxis axis,
            Motion motion, String when) {
        String unit = motion.getUnit();
        double configuredVelocity = axis.getMotionLimit(1);
        if (Double.isFinite(motion.getVelocity()) && configuredVelocity > 0
                && motion.getVelocity() < configuredVelocity * SHORTFALL_FRACTION) {
            solutions.add(new LengthSettingIssue(axis,
                    "The axis never reaches the feed rate it is planned with.",
                    "Set the axis feed rate to the one it was measured reaching.",
                    Solutions.Severity.Warning, WIKI_MOTION_PLANNER,
                    "Feed rate per second",
                    "The feed rate the axis will be planned with.",
                    String.format("Move times measured on %s fit a cruise velocity of %.0f %s/s, "
                            + "against the %.0f %s/s the axis is planned with. Whatever is "
                            + "holding it back - the controller, the driver, the mechanics - the "
                            + "planner is timing moves that take longer than it thinks.", when,
                            motion.getVelocity(), unit, configuredVelocity, unit),
                    axis.getFeedratePerSecond(),
                    new Length(motion.getVelocity(), AxesLocation.getUnits()),
                    (value, solved) -> axis.setFeedratePerSecond(value)));
        }
        double configuredAcceleration = axis.getMotionLimit(2);
        if (Double.isFinite(motion.getAcceleration()) && configuredAcceleration > 0
                && motion.getAcceleration() < configuredAcceleration * SHORTFALL_FRACTION) {
            solutions.add(new LengthSettingIssue(axis,
                    "The axis never reaches the acceleration it is planned with.",
                    "Set the axis acceleration to the one it was measured reaching.",
                    Solutions.Severity.Warning, WIKI_MOTION_PLANNER,
                    "Acceleration per second squared",
                    "The acceleration the axis will be planned with.",
                    String.format("Move times measured on %s fit an acceleration of %.0f %s/s², "
                            + "against the %.0f %s/s² the axis is planned with. The planner puts "
                            + "the start of the deceleration where the configured figure says it "
                            + "should be, so on the real machine the ramp is still running "
                            + "there.", when, motion.getAcceleration(), unit,
                            configuredAcceleration, unit),
                    axis.getAccelerationPerSecond2(),
                    new Length(motion.getAcceleration(), AxesLocation.getUnits()),
                    (value, solved) -> axis.setAccelerationPerSecond2(value)));
        }
    }

    /**
     * One-sided positioning always approaches a target from the same side, having first driven
     * past it by the backlash offset. An offset smaller than the backlash does not get past it,
     * so the approach starts inside the slack and the compensation does nothing.
     */
    private void findBacklashOffsetIssue(Solutions solutions, ReferenceControllerAxis axis,
            Positioning positioning, String when) {
        if (!axis.getBacklashCompensationMethod().isOneSidedPositioningMethod()) {
            return;
        }
        double measured = positioning.getBacklashMaxMm();
        Length offset = axis.getBacklashOffset().convertToUnits(LengthUnit.Millimeters);
        if (measured <= offset.getValue()) {
            return;
        }
        solutions.add(new LengthSettingIssue(axis,
                "One-sided backlash compensation is set to less offset than the axis has "
                        + "backlash.",
                "Raise the offset past the backlash that was measured.",
                Solutions.Severity.Warning, WIKI_MOTION_PLANNER,
                "Backlash offset",
                "How far past the target the axis drives before approaching it.",
                String.format("The axis measured up to %.4f mm of backlash on %s, with compensation "
                        + "switched off, and %s is set to drive %.4f mm past the target before "
                        + "approaching it. That does not clear the slack, so the approach begins "
                        + "inside it and every position still carries the backlash. The offered "
                        + "value keeps a margin over the largest backlash measured.", measured,
                        when, axis.getBacklashCompensationMethod(), offset.getValue()),
                axis.getBacklashOffset(),
                new Length(measured * BACKLASH_OFFSET_MARGIN, LengthUnit.Millimeters)
                        .convertToUnits(axis.getBacklashOffset().getUnits()),
                (value, solved) -> axis.setBacklashOffset(value)));
    }

    /**
     * A camera that waits a fixed time before capturing, against how long the image really takes
     * to stop moving. Too short and every calibration and every alignment is measured on a
     * shaking image; too long and the difference is paid on every single capture in a job.
     */
    private void findSettleTimeIssues(Solutions solutions, ReferenceCamera camera,
            Settling settling, String when) {
        if (camera.getSettleMethod() != AbstractSettlingCamera.SettleMethod.FixedTime) {
            return;
        }
        long configured = camera.getSettleTimeMs();
        double measured = settling.getSettleSeconds();
        if (configured < measured * 1000) {
            solutions.add(new SettleTimeIssue(camera,
                    "The camera waits a fixed time that ends before the image has stopped moving.",
                    "Wait as long as the image was measured taking to settle.",
                    Solutions.Severity.Error,
                    String.format("The image was still moving %.0f ms after a %.0f mm move on %s, "
                            + "and the camera waits %d ms before it captures. Everything that "
                            + "looks through this camera - the calibrations, fiducial location, "
                            + "part alignment - is therefore measuring a moving image, and no "
                            + "amount of calibration afterwards can recover that.",
                            measured * 1000, settling.getDistanceMm(), when, configured),
                    configured, settleMilliseconds(measured * SETTLE_MARGIN)));
        }
        else if (measured > 0 && configured > measured * 1000 * SETTLE_EXCESS) {
            solutions.add(new SettleTimeIssue(camera,
                    "The camera waits considerably longer than the image takes to settle.",
                    "Shorten the wait to what the image was measured needing.",
                    Solutions.Severity.Suggestion,
                    String.format("The image settled %.0f ms after a %.0f mm move on %s, and the "
                            + "camera waits %d ms before it captures. The difference is spent on "
                            + "every capture the machine makes, which over a job of thousands of "
                            + "placements is time spent waiting for something that has already "
                            + "happened.", measured * 1000, settling.getDistanceMm(), when,
                            configured),
                    configured, settleMilliseconds(measured * SETTLE_MARGIN)));
        }
    }

    /**
     * Units per Pixel against the scale the camera was measured having across its field of view.
     * Reported rather than corrected: the scan says the scale is wrong, and the calibration that
     * gets it right is the advanced camera calibration that Issues and Solutions already offers,
     * which measures tilt and lens distortion in the same pass.
     */
    private void findFieldOfViewIssues(Solutions solutions, MachineDiagnosticsResults results) {
        Map<String, FieldOfView> worst = new LinkedHashMap<>();
        for (FieldOfView scan : results.getFieldOfView()) {
            FieldOfView previous = worst.get(scan.getCameraId());
            if (previous == null
                    || Math.abs(scan.getScaleError()) > Math.abs(previous.getScaleError())) {
                worst.put(scan.getCameraId(), scan);
            }
        }
        String when = measuredWhen(results, TestGroup.XyPositioning);
        for (FieldOfView scan : worst.values()) {
            ReferenceCamera camera = camera(scan.getCameraId());
            if (camera == null || Math.abs(scan.getScaleError()) <= SCALE_ERROR_TOLERANCE) {
                continue;
            }
            solutions.add(new PointerIssue(camera,
                    "Units per Pixel does not agree with what the camera sees across its field "
                            + "of view.",
                    "Calibrate the camera with the advanced camera calibration offered here.",
                    Solutions.Severity.Warning, WIKI_CALIBRATION_SOLUTIONS,
                    String.format("A fiducial swept across the field of view on %s moved %+.2f%% "
                            + "further in %s than Units per Pixel accounts for, leaving %.4f mm "
                            + "rms of distortion under the scale error. Units per Pixel scales "
                            + "every vision correction the machine makes, so the error is carried "
                            + "into placement over the whole board. This is not corrected here: "
                            + "the advanced camera calibration in this list measures the scale "
                            + "together with the camera's tilt and the lens distortion, which is "
                            + "what the residual says is also present.", when,
                            scan.getScaleError() * 100, scan.getAxis(), scan.getResidualMm())));
        }
    }

    /**
     * Homing scatter against visual homing. The endstops repeat to whatever precision they
     * repeat to, and that scatter moves the origin of every coordinate in the machine; visual
     * homing pins the origin to a fiducial instead, which is an offer Issues and Solutions
     * already makes and which this points at rather than duplicating.
     */
    private void findHomingScatterIssue(Solutions solutions, MachineDiagnosticsResults results) {
        MachineDiagnosticsResults.Homing homing = results.getHoming();
        if (homing == null || homing.getSpreadMm() <= HOMING_SCATTER_TOLERANCE_MM) {
            return;
        }
        Head head = machine.getHead(homing.getHeadId());
        if (!(head instanceof ReferenceHead)
                || ((ReferenceHead) head).getVisualHomingMethod()
                        != ReferenceHead.VisualHomingMethod.None) {
            return;
        }
        solutions.add(new PointerIssue(head,
                "Homing does not put the machine origin back in the same place.",
                "Set up visual homing, with the Enable Visual Homing solution offered here.",
                Solutions.Severity.Warning, WIKI_VISUAL_HOMING,
                String.format("Homing %d times on %s and measuring the same fiducial after each "
                        + "one put the origin within %.4f mm of itself. Visual homing is off, so "
                        + "that is the repeatability of the endstops, and it shifts every coordinate "
                        + "the machine holds - fiducials, feeders, nozzle offsets - by that much "
                        + "between one power-up and the next. Visual homing takes the origin from "
                        + "a fiducial instead, which is the solution offered in this list.",
                        homing.getCycles(), measuredWhen(results, TestGroup.Homing),
                        homing.getSpreadMm())));
    }

    /**
     * Rotation backlash with no compensation set. The nozzle turns the part by however much of
     * the commanded angle the mechanism takes up, and the error at the corner of a part grows
     * with its size, so it shows up on the large parts that are otherwise easiest to place.
     */
    private void findRotationBacklashIssue(Solutions solutions,
            MachineDiagnosticsResults results) {
        MachineDiagnosticsResults.Rotation rotation = results.getRotation();
        if (rotation == null
                || Math.abs(rotation.getBacklashDegrees()) <= ROTATION_BACKLASH_TOLERANCE) {
            return;
        }
        ReferenceControllerAxis axis = controllerAxis(rotation.getAxisId());
        if (axis == null || axis.getBacklashCompensationMethod()
                != BacklashCompensationMethod.None) {
            return;
        }
        double measured = Math.abs(rotation.getBacklashDegrees());
        String when = measuredWhen(results, TestGroup.RotationBacklash);
        solutions.add(new LengthSettingIssue(axis,
                "The rotation axis has backlash and no compensation set.",
                "Compensate the rotation in the direction of travel, by the offset measured.",
                Solutions.Severity.Warning, WIKI_MOTION_PLANNER,
                "Backlash offset",
                "How far the commanded angle is shifted in the direction the axis is turning.",
                String.format("Approaching the same angle from either side on %s left %.3f degrees "
                        + "between the two, measured on the bottom camera through the part "
                        + "itself, and no compensation is set. At the corner of a 5 mm part that "
                        + "angle is %.3f mm of placement error, which is why it shows up first "
                        + "on the large parts. Accepting sets compensation in the direction of "
                        + "travel, which is the method that costs no extra move.", when, measured,
                        Math.toRadians(measured) * 2.5),
                axis.getBacklashOffset(), new Length(measured, AxesLocation.getUnits()),
                new LengthSetting() {
                    private final BacklashCompensationMethod previousMethod =
                            axis.getBacklashCompensationMethod();

                    @Override
                    public void set(Length value, boolean solved) {
                        axis.setBacklashCompensationMethod(solved
                                ? BacklashCompensationMethod.DirectionalCompensation
                                : previousMethod);
                        axis.setBacklashOffset(value);
                    }
                }));
    }

    /**
     * Marks an issue as one of these checks, so that the diagnostics page can show what it found
     * among everything else the machine reports. The page is the one place that needs to tell
     * them apart; Issues and Solutions deliberately does not care where an issue came from.
     */
    public interface Finding {
    }

    /** An issue one of these checks raised, which is the whole of what the marker means. */
    private abstract static class DiagnosticIssue extends Solutions.Issue implements Finding {
        DiagnosticIssue(Solutions.Subject subject, String issue, String solution,
                Solutions.Severity severity, String uri) {
            super(subject, issue, solution, severity, uri);
        }
    }

    /**
     * A finding with no fix of its own, because the fix is a calibration Issues and Solutions
     * already offers. It says what was measured and names the solution that does the work, so
     * that there is one place performing each calibration rather than two that can drift apart.
     */
    private static class PointerIssue extends Solutions.PlainIssue implements Finding {
        private final String explanation;

        PointerIssue(Solutions.Subject subject, String issue, String solution,
                Solutions.Severity severity, String uri, String explanation) {
            super(subject, issue, solution, severity, uri);
            this.explanation = explanation;
        }

        @Override
        protected String extendedDescription() {
            return explanation;
        }
    }

    /** What an issue does with the length it offers, when accepted and when that is undone. */
    private interface LengthSetting {
        void set(Length value, boolean solved) throws Exception;
    }

    /**
     * An issue whose solution is one measured length going into one setting. The value is
     * adjustable before accepting, accepting writes it, and undoing puts back what was there.
     */
    private static class LengthSettingIssue extends DiagnosticIssue {
        private final String label;
        private final String toolTip;
        private final String explanation;
        private final Length previous;
        private final LengthSetting setting;
        private Length proposed;

        LengthSettingIssue(Solutions.Subject subject, String issue, String solution,
                Solutions.Severity severity, String uri, String label, String toolTip,
                String explanation, Length previous, Length proposed, LengthSetting setting) {
            super(subject, issue, solution, severity, uri);
            this.label = label;
            this.toolTip = toolTip;
            this.explanation = explanation;
            this.previous = previous;
            this.proposed = proposed;
            this.setting = setting;
        }

        @Override
        protected String extendedDescription() {
            return explanation;
        }

        @Override
        public CustomProperty[] getProperties() {
            return new CustomProperty[] { new LengthProperty(label, toolTip) {
                @Override
                public Length get() {
                    return proposed;
                }

                @Override
                public void set(Length value) {
                    proposed = value;
                }
            } };
        }

        @Override
        public void setState(Solutions.State state) throws Exception {
            setting.set(state == Solutions.State.Solved ? proposed : previous,
                    state == Solutions.State.Solved);
            super.setState(state);
        }
    }

    /**
     * The settle time issues, which differ from the length ones only in that a wait is a number
     * of milliseconds and the camera keeps it as one.
     */
    private static class SettleTimeIssue extends DiagnosticIssue {
        private final AbstractSettlingCamera camera;
        private final String explanation;
        private final long previous;
        private int proposed;

        SettleTimeIssue(AbstractSettlingCamera camera, String issue, String solution,
                Solutions.Severity severity, String explanation, long previous, int proposed) {
            super(camera, issue, solution, severity, WIKI_CAMERA_SETTLING);
            this.camera = camera;
            this.explanation = explanation;
            this.previous = previous;
            this.proposed = proposed;
        }

        @Override
        protected String extendedDescription() {
            return explanation;
        }

        @Override
        public CustomProperty[] getProperties() {
            return new CustomProperty[] { new IntegerProperty("Settle time in milliseconds",
                    "How long the camera waits after a move before it captures.", 0, 60000) {
                @Override
                public int get() {
                    return proposed;
                }

                @Override
                public void set(int value) {
                    proposed = value;
                }
            } };
        }

        @Override
        public void setState(Solutions.State state) throws Exception {
            camera.setSettleTimeMs(state == Solutions.State.Solved ? proposed : previous);
            super.setState(state);
        }
    }

    /** A settle time rounded up to the next whole millisecond, which is the camera's unit. */
    private static int settleMilliseconds(double seconds) {
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(seconds * 1000));
    }

    /** The axis by its id, or null if it is gone or is no longer a controller axis. */
    private ReferenceControllerAxis controllerAxis(String id) {
        Axis axis = machine.getAxis(id);
        return axis instanceof ReferenceControllerAxis ? (ReferenceControllerAxis) axis : null;
    }

    /** The camera by its id, wherever it hangs, or null if it is gone. */
    private ReferenceCamera camera(String id) {
        for (Camera camera : machine.getAllCameras()) {
            if (camera.getId().equals(id) && camera instanceof ReferenceCamera) {
                return (ReferenceCamera) camera;
            }
        }
        return null;
    }

    /** Degrees for a rotation axis, the system length unit for the rest. */
    private static String axisUnit(ReferenceControllerAxis axis) {
        return axis.getType() == Axis.Type.Rotation ? "deg" : AxesLocation.getUnits().getShortName();
    }

    /**
     * When the group that measured this last ran. The age of a measurement is what tells the
     * reader whether to trust it against a machine they have worked on since.
     * <p>
     * A date and nothing else, so that the word around it - "on" - belongs to the sentence and
     * can be translated with it, rather than arriving as an English fragment in the middle of a
     * translated paragraph.
     */
    private static String measuredWhen(MachineDiagnosticsResults results, TestGroup group) {
        MachineDiagnosticsResults.Run run = results.getRun(group);
        if (run == null) {
            // Only reachable for conclusions stored before the runs were recorded alongside.
            return "an earlier run";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(run.getWhen());
    }

    private Configuration getConfiguration() throws Exception {
        if (configuration == null) {
            throw new Exception("The machine has not finished loading its configuration.");
        }
        return configuration;
    }

    // Settings.

    public int getRepeats() {
        return repeats;
    }

    public void setRepeats(int repeats) {
        Object oldValue = this.repeats;
        this.repeats = repeats;
        firePropertyChange("repeats", oldValue, repeats);
    }

    public String getTimingDistances() {
        return timingDistances;
    }

    public void setTimingDistances(String timingDistances) {
        Object oldValue = this.timingDistances;
        this.timingDistances = timingDistances;
        firePropertyChange("timingDistances", oldValue, timingDistances);
    }

    public String getPositioningDistances() {
        return positioningDistances;
    }

    public void setPositioningDistances(String positioningDistances) {
        Object oldValue = this.positioningDistances;
        this.positioningDistances = positioningDistances;
        firePropertyChange("positioningDistances", oldValue, positioningDistances);
    }

    public String getSpeedFactors() {
        return speedFactors;
    }

    public void setSpeedFactors(String speedFactors) {
        Object oldValue = this.speedFactors;
        this.speedFactors = speedFactors;
        firePropertyChange("speedFactors", oldValue, speedFactors);
    }

    public boolean isTimingIncludesZAndRotation() {
        return timingIncludesZAndRotation;
    }

    public void setTimingIncludesZAndRotation(boolean timingIncludesZAndRotation) {
        Object oldValue = this.timingIncludesZAndRotation;
        this.timingIncludesZAndRotation = timingIncludesZAndRotation;
        firePropertyChange("timingIncludesZAndRotation", oldValue, timingIncludesZAndRotation);
    }

    public String getRotationTimingAngles() {
        return rotationTimingAngles;
    }

    public void setRotationTimingAngles(String rotationTimingAngles) {
        Object oldValue = this.rotationTimingAngles;
        this.rotationTimingAngles = rotationTimingAngles;
        firePropertyChange("rotationTimingAngles", oldValue, rotationTimingAngles);
    }

    public double getStepTestDistanceMm() {
        return stepTestDistanceMm;
    }

    public void setStepTestDistanceMm(double stepTestDistanceMm) {
        Object oldValue = this.stepTestDistanceMm;
        this.stepTestDistanceMm = stepTestDistanceMm;
        firePropertyChange("stepTestDistanceMm", oldValue, stepTestDistanceMm);
    }

    public double getStepTestStepMm() {
        return stepTestStepMm;
    }

    public void setStepTestStepMm(double stepTestStepMm) {
        Object oldValue = this.stepTestStepMm;
        this.stepTestStepMm = stepTestStepMm;
        firePropertyChange("stepTestStepMm", oldValue, stepTestStepMm);
    }

    public int getFieldOfViewGridSteps() {
        return fieldOfViewGridSteps;
    }

    public void setFieldOfViewGridSteps(int fieldOfViewGridSteps) {
        Object oldValue = this.fieldOfViewGridSteps;
        this.fieldOfViewGridSteps = fieldOfViewGridSteps;
        firePropertyChange("fieldOfViewGridSteps", oldValue, fieldOfViewGridSteps);
    }

    public double getFieldOfViewGridFraction() {
        return fieldOfViewGridFraction;
    }

    public void setFieldOfViewGridFraction(double fieldOfViewGridFraction) {
        Object oldValue = this.fieldOfViewGridFraction;
        this.fieldOfViewGridFraction = fieldOfViewGridFraction;
        firePropertyChange("fieldOfViewGridFraction", oldValue, fieldOfViewGridFraction);
    }

    public String getSettleDistances() {
        return settleDistances;
    }

    public void setSettleDistances(String settleDistances) {
        Object oldValue = this.settleDistances;
        this.settleDistances = settleDistances;
        firePropertyChange("settleDistances", oldValue, settleDistances);
    }

    public double getSettleSampleSeconds() {
        return settleSampleSeconds;
    }

    public void setSettleSampleSeconds(double settleSampleSeconds) {
        Object oldValue = this.settleSampleSeconds;
        this.settleSampleSeconds = settleSampleSeconds;
        firePropertyChange("settleSampleSeconds", oldValue, settleSampleSeconds);
    }

    public double getSettleThresholdPixels() {
        return settleThresholdPixels;
    }

    public void setSettleThresholdPixels(double settleThresholdPixels) {
        Object oldValue = this.settleThresholdPixels;
        this.settleThresholdPixels = settleThresholdPixels;
        firePropertyChange("settleThresholdPixels", oldValue, settleThresholdPixels);
    }

    public int getHomingCycles() {
        return homingCycles;
    }

    public void setHomingCycles(int homingCycles) {
        Object oldValue = this.homingCycles;
        this.homingCycles = homingCycles;
        firePropertyChange("homingCycles", oldValue, homingCycles);
    }

    public String getRotationTestAngles() {
        return rotationTestAngles;
    }

    public void setRotationTestAngles(String rotationTestAngles) {
        Object oldValue = this.rotationTestAngles;
        this.rotationTestAngles = rotationTestAngles;
        firePropertyChange("rotationTestAngles", oldValue, rotationTestAngles);
    }

    public double getRotationApproachAngle() {
        return rotationApproachAngle;
    }

    public void setRotationApproachAngle(double rotationApproachAngle) {
        Object oldValue = this.rotationApproachAngle;
        this.rotationApproachAngle = rotationApproachAngle;
        firePropertyChange("rotationApproachAngle", oldValue, rotationApproachAngle);
    }

    public String getRotationTestPartId() {
        return rotationTestPartId;
    }

    public void setRotationTestPartId(String rotationTestPartId) {
        Object oldValue = this.rotationTestPartId;
        this.rotationTestPartId = rotationTestPartId;
        firePropertyChange("rotationTestPartId", oldValue, rotationTestPartId);
    }

    public long getMachineSettleMs() {
        return machineSettleMs;
    }

    public void setMachineSettleMs(long machineSettleMs) {
        Object oldValue = this.machineSettleMs;
        this.machineSettleMs = machineSettleMs;
        firePropertyChange("machineSettleMs", oldValue, machineSettleMs);
    }

    public String getFirmwareCommands() {
        return firmwareCommands;
    }

    public void setFirmwareCommands(String firmwareCommands) {
        Object oldValue = this.firmwareCommands;
        this.firmwareCommands = firmwareCommands;
        firePropertyChange("firmwareCommands", oldValue, firmwareCommands);
    }

    // Run state.

    public boolean isRunning() {
        return running;
    }

    public void abort() {
        aborting = true;
        log("Stopping after the current step...");
    }

    public String getLog() {
        synchronized (logText) {
            return logText.toString();
        }
    }

    public File getLastReportDirectory() {
        return lastReportDirectory;
    }

    /**
     * @return What each test group last concluded, or null if nothing has ever been measured on
     *         this machine.
     */
    public MachineDiagnosticsResults getLastResults() {
        return lastResults;
    }

    public void setLastResults(MachineDiagnosticsResults lastResults) {
        Object oldValue = this.lastResults;
        this.lastResults = lastResults;
        firePropertyChange("lastResults", oldValue, lastResults);
    }

    /**
     * Store what a test group concluded, under the group's name and the time it finished.
     * <p>
     * Called at the end of a measurement rather than as each number is worked out, so that a
     * group which threw halfway leaves the previous conclusions in place instead of replacing
     * them with a partial set.
     */
    private void recordResults(TestGroup group, MachineDiagnosticsReport report,
            Consumer<MachineDiagnosticsResults> conclusions) {
        MachineDiagnosticsResults results =
                lastResults != null ? lastResults : new MachineDiagnosticsResults();
        conclusions.accept(results);
        results.setRun(group, System.currentTimeMillis(),
                report.getDirectory().getAbsolutePath());
        setLastResults(results);
    }

    public SimpleGraph getTimingGraph() {
        return timingGraph;
    }

    public SimpleGraph getPositioningGraph() {
        return positioningGraph;
    }

    public SimpleGraph getStepGraph() {
        return stepGraph;
    }

    public SimpleGraph getSettleGraph() {
        return settleGraph;
    }

    private void setTimingGraph(SimpleGraph graph) {
        Object oldValue = this.timingGraph;
        this.timingGraph = graph;
        firePropertyChange("timingGraph", oldValue, graph);
    }

    private void setPositioningGraph(SimpleGraph graph) {
        Object oldValue = this.positioningGraph;
        this.positioningGraph = graph;
        firePropertyChange("positioningGraph", oldValue, graph);
    }

    private void setStepGraph(SimpleGraph graph) {
        Object oldValue = this.stepGraph;
        this.stepGraph = graph;
        firePropertyChange("stepGraph", oldValue, graph);
    }

    private void setSettleGraph(SimpleGraph graph) {
        Object oldValue = this.settleGraph;
        this.settleGraph = graph;
        firePropertyChange("settleGraph", oldValue, graph);
    }

    private void log(String text) {
        Logger.info("Machine diagnostics: {}", text);
        synchronized (logText) {
            logText.append(text).append("\n");
        }
        firePropertyChange("log", null, getLog());
    }

    private void log(String format, Object... arguments) {
        log(String.format(format, arguments));
    }

    private void checkAborted() throws AbortedException {
        if (aborting) {
            throw new AbortedException();
        }
    }

    /**
     * Run the selected test groups and write the report.
     * <p>
     * Must be called on the machine task thread, i.e. from inside
     * {@link org.openpnp.util.UiUtils#submitUiMachineTask}, because it moves the machine.
     *
     * @return The directory the report was written to.
     */
    public File run(ReferenceMachine machine, Set<TestGroup> groups) throws Exception {
        if (running) {
            throw new Exception("Diagnostics are already running.");
        }
        if (groups.isEmpty()) {
            throw new Exception("No tests selected.");
        }
        // The snapshot only reads settings, and a machine that will not start is one of the
        // occasions for taking one, so it alone does not need the machine running.
        Set<TestGroup> machineGroups = EnumSet.copyOf(groups);
        machineGroups.remove(TestGroup.ConfigSnapshot);
        if (!machineGroups.isEmpty() && !machine.isEnabled()) {
            throw new Exception("The machine is not enabled.");
        }
        Set<TestGroup> movingGroups = EnumSet.copyOf(machineGroups);
        movingGroups.remove(TestGroup.Firmware);
        if (!movingGroups.isEmpty() && !machine.isHomed()) {
            throw new Exception("The machine is not homed. Home it before measuring, otherwise "
                    + "every position measured here is relative to an unknown origin.");
        }
        running = true;
        aborting = false;
        synchronized (logText) {
            logText.setLength(0);
        }
        firePropertyChange("log", null, getLog());
        firePropertyChange("running", false, true);
        MachineDiagnosticsReport report = new MachineDiagnosticsReport(
                new File(getConfiguration().getConfigurationDirectory(), "diagnostics"));
        lastReportDirectory = report.getDirectory();
        firePropertyChange("lastReportDirectory", null, lastReportDirectory);
        try {
            log("Report directory: %s", report.getDirectory().getAbsolutePath());
            for (TestGroup group : TestGroup.values()) {
                if (!groups.contains(group)) {
                    continue;
                }
                checkAborted();
                try {
                    runGroup(machine, group, report);
                }
                catch (AbortedException e) {
                    throw e;
                }
                catch (Exception e) {
                    Logger.warn(e, "Machine diagnostics: {} failed", group);
                    log("%s FAILED: %s", group, e.getMessage());
                    report.section(group.toString())
                          .line("Failed: " + e.getMessage());
                    report.finding(Severity.Problem, "%s could not be measured: %s", group,
                            e.getMessage());
                }
                report.flush();
            }
        }
        catch (AbortedException e) {
            log("Stopped by the user. The report holds everything measured so far.");
            report.finding(Severity.Warning, "Stopped by the user before all the selected tests ran.");
        }
        finally {
            running = false;
            aborting = false;
            try {
                report.flush();
            }
            catch (Exception e) {
                Logger.error(e, "Machine diagnostics: cannot write the report");
            }
            firePropertyChange("running", true, false);
            log("Done. Report written to %s", report.getReportFile().getAbsolutePath());
        }
        return report.getDirectory();
    }

    private void runGroup(ReferenceMachine machine, TestGroup group,
            MachineDiagnosticsReport report) throws Exception {
        log("--- %s ---", group);
        switch (group) {
            case Firmware:
                testFirmware(machine, report);
                break;
            case Kinematics:
                testKinematics(machine, report);
                break;
            case XyPositioning:
                testXyPositioning(machine, report);
                break;
            case CameraSettle:
                testCameraSettle(machine, report);
                break;
            case Homing:
                testHoming(machine, report);
                break;
            case RotationBacklash:
                testRotationBacklash(machine, report);
                break;
            case ConfigSnapshot:
                writeConfigSnapshot(machine, report);
                break;
            default:
                throw new Exception("Unknown test group " + group + ".");
        }
    }

    // Test group 1: what the controller says about itself.

    private void testFirmware(ReferenceMachine machine, MachineDiagnosticsReport report)
            throws Exception {
        report.section("Firmware and controller settings");
        StringBuilder raw = new StringBuilder();
        List<ControllerLimits> limits = new ArrayList<>();
        boolean anyDriver = false;
        for (Driver driver : machine.getDrivers()) {
            if (!(driver instanceof GcodeDriver)) {
                report.line("%s: not a G-code driver, skipped.", driver.getName());
                continue;
            }
            anyDriver = true;
            GcodeDriver gcodeDriver = (GcodeDriver) driver;
            // The controller must be idle, or the replies interleave with motion confirmations.
            machine.getMotionPlanner().waitForCompletion(null, CompletionType.WaitForStillstand);
            raw.append("=== ").append(driver.getName()).append(" ===\n");
            report.line("%s", driver.getName());
            Map<String, GcodeSettingLine> settings = new LinkedHashMap<>();
            for (String command : firmwareCommands.split("\\r?\\n")) {
                checkAborted();
                command = command.trim();
                if (command.isEmpty() || command.startsWith(";")) {
                    continue;
                }
                log("%s: %s", driver.getName(), command);
                List<String> replies = sendAndCollect(gcodeDriver, command);
                raw.append("> ").append(command).append("\n");
                for (String reply : replies) {
                    raw.append(reply).append("\n");
                }
                raw.append("\n");
                settings.putAll(MachineDiagnosticsMath.parseSettingsReport(replies));
                report.line("  %s -> %d line(s)", command, replies.size());
            }
            compareControllerLimits(machine, gcodeDriver, settings, report, limits);
        }
        if (!anyDriver) {
            throw new Exception("No G-code driver to ask.");
        }
        report.writeText("firmware.txt", raw.toString());
        report.line("Raw controller output: firmware.txt");
        recordResults(TestGroup.Firmware, report, results -> results.setControllerLimits(limits));
    }

    /**
     * Send one command and collect everything the controller said before it confirmed. The
     * confirmation is the last line the controller sends for a command, so once
     * {@link GcodeDriver#sendCommand} has seen it, all the reply lines are already queued.
     */
    private List<String> sendAndCollect(GcodeDriver driver, String command) throws Exception {
        driver.receiveResponses();
        driver.sendCommand(command, driver.getTimeoutMilliseconds());
        List<String> replies = new ArrayList<>();
        for (GcodeDriver.Line line : driver.receiveResponses()) {
            replies.add(line.getLine());
        }
        return replies;
    }

    /**
     * Put the controller's own limits beside the ones OpenPnP plans with. A controller limit
     * below the axis setting silently caps every move, which makes the motion planner's timing,
     * and therefore its speed factors, describe a machine that does not exist.
     */
    private void compareControllerLimits(ReferenceMachine machine, GcodeDriver driver,
            Map<String, GcodeSettingLine> settings, MachineDiagnosticsReport report,
            List<ControllerLimits> limits) {
        GcodeSettingLine maxFeedRate = settings.get("M203");
        GcodeSettingLine maxAcceleration = settings.get("M201");
        GcodeSettingLine stepsPerUnit = settings.get("M92");
        if (maxFeedRate == null && maxAcceleration == null && stepsPerUnit == null) {
            report.line("  The controller did not report its settings in a form this understands. "
                    + "See firmware.txt for what it did say.");
            return;
        }
        report.line("  %-6s %-10s %-12s %-12s %-12s %-12s", "axis", "steps/mm", "feed cfg",
                "feed ctrl", "accel cfg", "accel ctrl");
        for (Axis axis : machine.getAxes()) {
            if (!(axis instanceof ReferenceControllerAxis)) {
                continue;
            }
            ReferenceControllerAxis controllerAxis = (ReferenceControllerAxis) axis;
            if (controllerAxis.getDriver() != driver) {
                continue;
            }
            String letter = controllerAxis.getLetter();
            Double steps = stepsPerUnit != null ? stepsPerUnit.get(letter) : null;
            Double feedController = maxFeedRate != null ? maxFeedRate.get(letter) : null;
            Double accelerationController = maxAcceleration != null ? maxAcceleration.get(letter) : null;
            limits.add(new ControllerLimits(controllerAxis.getId(), steps, feedController,
                    accelerationController));
            double feedConfigured = controllerAxis.getMotionLimit(1);
            double accelerationConfigured = controllerAxis.getMotionLimit(2);
            report.line("  %-6s %-10s %-12.1f %-12s %-12.1f %-12s", controllerAxis.getName(),
                    steps == null ? "?" : String.format("%.4f", steps),
                    feedConfigured, feedController == null ? "?" : String.format("%.1f", feedController),
                    accelerationConfigured,
                    accelerationController == null ? "?" : String.format("%.1f", accelerationController));
            if (feedController != null && feedConfigured > feedController * 1.05) {
                report.finding(Severity.Warning, "Axis %s is configured for %.0f/s but the "
                        + "controller caps it at %.0f/s (M203).", controllerAxis.getName(),
                        feedConfigured, feedController);
            }
            if (accelerationController != null && accelerationConfigured > accelerationController * 1.05) {
                report.finding(Severity.Warning, "Axis %s is configured for %.0f/s2 but the "
                        + "controller caps it at %.0f/s2 (M201).", controllerAxis.getName(),
                        accelerationConfigured, accelerationController);
            }
            if (steps != null && steps > 0) {
                double stepSize = 1.0 / steps;
                if (controllerAxis.getResolution() < stepSize * 0.5) {
                    report.finding(Severity.Warning, "Axis %s has a resolution of %.5f set but the "
                            + "controller steps in %.5f. Coordinates finer than one step are sent "
                            + "as moves the machine cannot make.", controllerAxis.getName(),
                            controllerAxis.getResolution(), stepSize);
                }
            }
        }
    }

    // Test group 2: how fast the machine really moves.

    private void testKinematics(ReferenceMachine machine, MachineDiagnosticsReport report)
            throws Exception {
        report.section("Kinematics, measured from move times");
        report.line("Each move is timed from the command until the machine reports stillstand, so "
                + "the fitted overhead includes the command round trip and the completion handshake.");
        double[] distances = MachineDiagnosticsMath.parseSeries(timingDistances);
        SimpleGraph graph = newTimingGraph();
        List<Object[]> rows = new ArrayList<>();
        List<Motion> fits = new ArrayList<>();
        ReferenceHead head = requireHead(machine);
        HeadMountable camera = requireDownLookingCamera(head);
        Nozzle nozzle = head.getNozzles().isEmpty() ? null : head.getNozzles().get(0);

        List<BacklashSetting> saved = suspendBacklashCompensation(machine, Axis.Type.X, Axis.Type.Y);
        try {
            for (Axis.Type type : new Axis.Type[] { Axis.Type.X, Axis.Type.Y }) {
                ReferenceControllerAxis axis = findControllerAxis(camera, type);
                if (axis == null) {
                    report.line("No controller axis of type %s on the camera, skipped.", type);
                    continue;
                }
                timeAxis(machine, report, graph, rows, fits, camera, axis, distances, "mm");
            }
        }
        finally {
            restoreBacklashCompensation(saved);
        }
        if (timingIncludesZAndRotation && nozzle != null) {
            ReferenceControllerAxis zAxis = findControllerAxis(nozzle, Axis.Type.Z);
            if (zAxis != null && zAxis.isSafeZoneLowEnabled() && zAxis.isSafeZoneHighEnabled()) {
                double zone = Math.abs(zAxis.getSafeZoneHigh().convertToUnits(zAxis.getUnits()).getValue()
                        - zAxis.getSafeZoneLow().convertToUnits(zAxis.getUnits()).getValue());
                List<Double> zDistances = new ArrayList<>();
                for (double distance : distances) {
                    if (distance <= zone * 0.9) {
                        zDistances.add(distance);
                    }
                }
                if (zDistances.size() >= 3) {
                    timeAxis(machine, report, graph, rows, fits, nozzle, zAxis,
                            toArray(zDistances), "mm");
                }
                else {
                    report.line("Z safe zone is %.1fmm, too small for the timing distances, skipped.",
                            zone);
                }
            }
            else {
                report.line("Z has no safe zone set, so there is no travel this can use without "
                        + "risking a collision. Skipped.");
            }
            ReferenceControllerAxis rotationAxis = findControllerAxis(nozzle, Axis.Type.Rotation);
            if (rotationAxis != null) {
                nozzle.moveToSafeZ();
                timeAxis(machine, report, graph, rows, fits, nozzle, rotationAxis,
                        MachineDiagnosticsMath.parseSeries(rotationTimingAngles), "deg");
            }
        }
        report.writeCsv("kinematics.csv",
                new String[] { "axis", "distance", "repeat", "seconds" }, rows);
        setTimingGraph(graph);
        recordResults(TestGroup.Kinematics, report, results -> results.setMotion(fits));
    }

    private void timeAxis(ReferenceMachine machine, MachineDiagnosticsReport report,
            SimpleGraph graph, List<Object[]> rows, List<Motion> fits, HeadMountable movable,
            ReferenceControllerAxis axis, double[] distances, String unit) throws Exception {
        Location base = movable.getLocation();
        double axisPerUnit = axisUnitVector(movable, axis, base);
        if (axisPerUnit == 0) {
            report.line("Axis %s does not move with %s, skipped.", axis.getName(), movable.getName());
            return;
        }
        // Centre the test on the axis travel, so that the long distances are not clipped by
        // whichever soft limit the machine happened to be parked near.
        base = axisTravelCentre(movable, axis, base);
        List<Double> fitDistances = new ArrayList<>();
        List<Double> fitTimes = new ArrayList<>();
        report.blank();
        report.line("Axis %s (%s)", axis.getName(), unit);
        report.line("  %-10s %-10s %-10s %-10s", "distance", "mean s", "sd s", "min s");
        for (double distance : distances) {
            checkAborted();
            Location from = displacedAxisLocation(movable, axis, base, -distance * axisPerUnit / 2);
            Location to = displacedAxisLocation(movable, axis, base, distance * axisPerUnit / 2);
            double achieved = Math.abs(axisCoordinate(movable, axis, to)
                    - axisCoordinate(movable, axis, from));
            if (achieved < distance * 0.9) {
                report.line("  %-10.3f clipped by the soft limits to %.3f, skipped.", distance, achieved);
                continue;
            }
            List<Double> times = new ArrayList<>();
            for (int repeat = 0; repeat < repeats; repeat++) {
                checkAborted();
                moveThere(movable, from, 1.0);
                movable.waitForCompletion(CompletionType.WaitForStillstand);
                Thread.sleep(machineSettleMs);
                double t0 = NanosecondTime.getRuntimeSeconds();
                movable.moveTo(to, 1.0);
                movable.waitForCompletion(CompletionType.WaitForStillstand);
                double seconds = NanosecondTime.getRuntimeSeconds() - t0;
                times.add(seconds);
                rows.add(new Object[] { axis.getName(), distance, repeat, seconds });
            }
            Stats stats = MachineDiagnosticsMath.stats(times);
            report.line("  %-10.3f %-10.4f %-10.4f %-10.4f", distance, stats.mean, stats.stdDev,
                    stats.min);
            // The fastest of the repeats is the one least disturbed by scheduling, so it is the
            // best estimate of what the machine can do.
            fitDistances.add(distance);
            fitTimes.add(stats.min);
            row(graph, unit, axis.getName(), axisColor(axis.getType()), true, false)
                    .recordDataPoint(distance, stats.min);
            log("%s %.3f%s: %.4fs", axis.getName(), distance, unit, stats.min);
        }
        if (fitDistances.size() < 3) {
            report.line("  Too few usable distances to fit acceleration and velocity.");
            return;
        }
        MotionFit fit = MachineDiagnosticsMath.fitMotion(toArray(fitDistances), toArray(fitTimes));
        fits.add(new Motion(axis.getId(), fit.acceleration, fit.velocity, fit.overhead, unit));
        double configuredFeedRate = axis.getMotionLimit(1);
        double configuredAcceleration = axis.getMotionLimit(2);
        report.line("  measured: acceleration %.0f %s/s2, velocity %.0f %s/s, overhead %.0f ms "
                + "(rms %.1f ms)", fit.acceleration, unit, fit.velocity, unit,
                fit.overhead * 1000, fit.rmsResidual * 1000);
        report.line("  configured: acceleration %.0f %s/s2, velocity %.0f %s/s",
                configuredAcceleration, unit, configuredFeedRate, unit);
        for (double distance : toArray(fitDistances)) {
            row(graph, unit, axis.getName() + " fit", COLOR_MODEL, false, true)
                    .recordDataPoint(distance, fit.timeAt(distance));
        }
        reportLimitShortfall(report, axis, "acceleration", configuredAcceleration, fit.acceleration, unit + "/s2");
        reportLimitShortfall(report, axis, "velocity", configuredFeedRate, fit.velocity, unit + "/s");
        if (fit.overhead > 0.05) {
            report.finding(Severity.Info, "Axis %s carries %.0f ms of fixed cost per move. Over a "
                    + "job of thousands of moves this is the dominant term for short moves.",
                    axis.getName(), fit.overhead * 1000);
        }
    }

    private void reportLimitShortfall(MachineDiagnosticsReport report, ReferenceControllerAxis axis,
            String what, double configured, double measured, String unit) {
        if (configured <= 0 || !Double.isFinite(measured)) {
            return;
        }
        double ratio = measured / configured;
        if (ratio < 0.7) {
            report.finding(Severity.Warning, "Axis %s reaches only %.0f %s of the %.0f %s it is "
                    + "configured for, %.0f%% of it. OpenPnP plans motion, and scales speed "
                    + "factors, against a %s it never gets.", axis.getName(), measured, unit,
                    configured, unit, ratio * 100, what);
        }
    }

    // Test group 3: where the machine actually ends up.

    private void testXyPositioning(ReferenceMachine machine, MachineDiagnosticsReport report)
            throws Exception {
        report.section("X/Y positioning, measured against the primary calibration fiducial");
        ReferenceHead head = requireHead(machine);
        ReferenceCamera camera = requireDownLookingCamera(head);
        Location fiducial = requireFiducial(head);
        Length diameter = head.getCalibrationPrimaryFiducialDiameter();

        SimpleGraph graph = new SimpleGraph();
        graph.setRelativePaddingLeft(0.05);
        SimpleGraph.DataScale scale = graph.getScale("mm");
        scale.setSymmetricIfSigned(true);
        scale.setLabelShown(true);

        Map<String, Stats> backlash = new LinkedHashMap<>();
        Map<String, Double> effectiveResolution = new LinkedHashMap<>();
        List<FieldOfView> scans = new ArrayList<>();
        measureRepeatability(machine, report, graph, head, camera, fiducial, diameter);
        measureRawBacklash(machine, report, graph, head, camera, fiducial, diameter, backlash);
        setPositioningGraph(graph);
        measureStepResponse(machine, report, head, camera, fiducial, diameter, effectiveResolution);
        measureFieldOfViewScale(machine, report, head, camera, fiducial, diameter, scans);
        recordResults(TestGroup.XyPositioning, report, results -> {
            List<Positioning> positioning = new ArrayList<>();
            for (Map.Entry<String, Stats> axis : backlash.entrySet()) {
                positioning.add(new Positioning(axis.getKey(), axis.getValue().min,
                        axis.getValue().max, effectiveResolution.get(axis.getKey())));
            }
            results.setPositioning(positioning);
            results.setFieldOfView(scans);
        });
    }

    /**
     * How far apart repeated approaches to one point land, with the machine exactly as
     * configured. This is the scatter a job actually sees, which no calibration step reports
     * because each of them measures with compensation switched off in order to derive it.
     */
    private void measureRepeatability(ReferenceMachine machine, MachineDiagnosticsReport report,
            SimpleGraph graph, ReferenceHead head, ReferenceCamera camera, Location fiducial,
            Length diameter) throws Exception {
        double[] distances = MachineDiagnosticsMath.parseSeries(positioningDistances);
        List<Object[]> rows = new ArrayList<>();
        report.blank();
        report.line("Repeatability with the current settings");
        report.line("  Errors are commanded minus actual, so a positive one means the machine "
                + "stopped short of the fiducial in the positive direction of that axis.");
        report.line("  %-6s %-6s %-10s %-10s %-10s %-10s", "axis", "from", "distance", "mean mm",
                "sd mm", "range mm");
        List<Double> allErrors = new ArrayList<>();
        for (Axis.Type type : new Axis.Type[] { Axis.Type.X, Axis.Type.Y }) {
            ReferenceControllerAxis axis = findControllerAxis(camera, type);
            if (axis == null) {
                continue;
            }
            Location unit = unitLocation(type);
            for (double distance : distances) {
                for (int sign = 1; sign >= -1; sign -= 2) {
                    checkAborted();
                    List<Double> errors = new ArrayList<>();
                    for (int repeat = 0; repeat < repeats; repeat++) {
                        checkAborted();
                        approachFrom(camera, axis, fiducial, sign * distance, 1.0);
                        Location detected = machine.getVisionSolutions().getDetectedLocation(camera,
                                camera, fiducial, diameter,
                                String.format("Repeatability %s %+.0fmm", axis.getName(), sign * distance),
                                false);
                        double error = shortfallMm(detected, fiducial, unit);
                        errors.add(error);
                        allErrors.add(error);
                        rows.add(new Object[] { axis.getName(), sign > 0 ? "+" : "-", distance,
                                repeat, error });
                    }
                    Stats stats = MachineDiagnosticsMath.stats(errors);
                    report.line("  %-6s %-6s %-10.3f %-10.4f %-10.4f %-10.4f", axis.getName(),
                            sign > 0 ? "+" : "-", distance, stats.mean, stats.stdDev, stats.getRange());
                    row(graph, "mm", axis.getName() + (sign > 0 ? " from +" : " from -"),
                            axisColor(axis.getType()), true, true)
                            .recordDataPoint(distance, stats.mean);
                    log("%s from %+.0fmm: mean %.4f sd %.4f", axis.getName(), sign * distance,
                            stats.mean, stats.stdDev);
                }
            }
        }
        report.writeCsv("repeatability.csv",
                new String[] { "axis", "approach", "distance", "repeat", "error_mm" }, rows);
        if (!allErrors.isEmpty()) {
            Stats overall = MachineDiagnosticsMath.stats(allErrors);
            report.line("  Overall spread across every approach: %.4f mm", overall.getRange());
            report.finding(overall.getRange() > 0.05 ? Severity.Warning : Severity.Info,
                    "Repeated approaches to one point land within %.3f mm of each other "
                    + "(sd %.4f mm). This is the positional scatter a job sees.",
                    overall.getRange(), overall.stdDev);
        }
    }

    /**
     * Backlash with compensation switched off, over distance and speed. Run separately from the
     * repeatability above because the two answer different questions: this one says how much
     * mechanism there is to compensate, that one says how well the compensation is working.
     */
    private void measureRawBacklash(ReferenceMachine machine, MachineDiagnosticsReport report,
            SimpleGraph graph, ReferenceHead head, ReferenceCamera camera, Location fiducial,
            Length diameter, Map<String, Stats> measured) throws Exception {
        double[] distances = MachineDiagnosticsMath.parseSeries(positioningDistances);
        double[] speeds = MachineDiagnosticsMath.parseSeries(speedFactors);
        List<Object[]> rows = new ArrayList<>();
        report.blank();
        report.line("Backlash with compensation switched off");
        report.line("  %-6s %-8s %-10s %-12s", "axis", "speed", "distance", "backlash mm");
        List<BacklashSetting> saved = suspendBacklashCompensation(machine, Axis.Type.X, Axis.Type.Y);
        try {
            for (Axis.Type type : new Axis.Type[] { Axis.Type.X, Axis.Type.Y }) {
                ReferenceControllerAxis axis = findControllerAxis(camera, type);
                if (axis == null) {
                    continue;
                }
                Location unit = unitLocation(type);
                for (double speed : speeds) {
                    for (double distance : distances) {
                        checkAborted();
                        approachFrom(camera, axis, fiducial, -distance, speed);
                        Location fromMinus = machine.getVisionSolutions().getDetectedLocation(camera,
                                camera, fiducial, diameter,
                                String.format("Backlash %s %.2fx -", axis.getName(), speed), false);
                        approachFrom(camera, axis, fiducial, distance, speed);
                        Location fromPlus = machine.getVisionSolutions().getDetectedLocation(camera,
                                camera, fiducial, diameter,
                                String.format("Backlash %s %.2fx +", axis.getName(), speed), false);
                        // The difference between where the machine physically stopped coming from
                        // one side and from the other.
                        double backlash = shortfallMm(fromMinus, fiducial, unit)
                                - shortfallMm(fromPlus, fiducial, unit);
                        rows.add(new Object[] { axis.getName(), speed, distance, backlash });
                        report.line("  %-6s %-8.2f %-10.3f %-12.4f", axis.getName(), speed, distance,
                                backlash);
                        row(graph, "mm", axis.getName() + " backlash " + speed + "x",
                                COLOR_MEASURED, true, true).recordDataPoint(distance, backlash);
                        log("%s backlash at %.2fx over %.1fmm: %.4f", axis.getName(), speed,
                                distance, backlash);
                    }
                }
            }
        }
        finally {
            restoreBacklashCompensation(saved);
        }
        report.writeCsv("backlash.csv",
                new String[] { "axis", "speed", "distance", "backlash_mm" }, rows);
        for (Axis.Type type : new Axis.Type[] { Axis.Type.X, Axis.Type.Y }) {
            ReferenceControllerAxis axis = findControllerAxis(camera, type);
            if (axis == null) {
                continue;
            }
            List<Double> axisBacklash = new ArrayList<>();
            for (Object[] row : rows) {
                if (row[0].equals(axis.getName())) {
                    axisBacklash.add((Double) row[3]);
                }
            }
            if (axisBacklash.isEmpty()) {
                continue;
            }
            Stats stats = MachineDiagnosticsMath.stats(axisBacklash);
            measured.put(axis.getId(), stats);
            Length configured = axis.getBacklashOffset().convertToUnits(LengthUnit.Millimeters);
            report.finding(Severity.Info, "Axis %s backlash measures %.4f to %.4f mm across the "
                    + "tested distances and speeds; the configured offset is %.4f mm and the "
                    + "method is %s.", axis.getName(), stats.min, stats.max, configured.getValue(),
                    axis.getBacklashCompensationMethod());
            if (axis.getBacklashCompensationMethod().isOneSidedPositioningMethod()
                    && configured.getValue() > 0 && stats.max > configured.getValue()) {
                report.finding(Severity.Warning, "Axis %s uses %s, which needs an offset at least "
                        + "as large as the backlash, but the offset is %.4f mm and the backlash "
                        + "reaches %.4f mm.", axis.getName(), axis.getBacklashCompensationMethod(),
                        configured.getValue(), stats.max);
            }
        }
    }

    /**
     * Commanded moves of one axis resolution at a time. A machine that sticks and then breaks
     * free shows up here as a staircase: several commands that move nothing followed by one that
     * moves the accumulated distance.
     */
    private void measureStepResponse(ReferenceMachine machine, MachineDiagnosticsReport report,
            ReferenceHead head, ReferenceCamera camera, Location fiducial, Length diameter,
            Map<String, Double> effectiveResolution) throws Exception {
        if (!(stepTestStepMm > 0) || !(stepTestDistanceMm > stepTestStepMm)) {
            report.line("Step test skipped: the step and distance settings do not describe a test.");
            return;
        }
        double[] speeds = MachineDiagnosticsMath.parseSeries(speedFactors);
        double slowest = speeds[0];
        double fastest = speeds[speeds.length - 1];
        SimpleGraph graph = new SimpleGraph();
        graph.setRelativePaddingLeft(0.05);
        SimpleGraph.DataScale scale = graph.getScale("mm");
        scale.setSymmetricIfSigned(true);
        scale.setLabelShown(true);
        List<Object[]> rows = new ArrayList<>();
        report.blank();
        report.line("Step response, %.3f mm steps across %.3f mm", stepTestStepMm, stepTestDistanceMm);
        for (Axis.Type type : new Axis.Type[] { Axis.Type.X, Axis.Type.Y }) {
            ReferenceControllerAxis axis = findControllerAxis(camera, type);
            if (axis == null) {
                continue;
            }
            Location unit = unitLocation(type);
            for (double speed : new double[] { slowest, fastest }) {
                checkAborted();
                // Approach the start of the staircase from below, so that the backlash is taken
                // up before the first step rather than during it.
                approachFrom(camera, axis, fiducial, -Math.max(1.0, stepTestDistanceMm * 4), speed);
                Double previousActual = null;
                int step = 0;
                int stalled = 0;
                double largestJump = 0;
                for (double offset = -stepTestDistanceMm / 2; offset <= stepTestDistanceMm / 2;
                        offset += stepTestStepMm) {
                    checkAborted();
                    Location target = fiducial.add(unit.multiply(offset, offset, 0, 0));
                    camera.moveTo(target, speed);
                    Location detected = machine.getVisionSolutions().getDetectedLocation(camera,
                            camera, fiducial, diameter,
                            String.format("Step %s %.2fx", axis.getName(), speed), false);
                    double shortfall = shortfallMm(detected, fiducial, unit);
                    // Where the camera physically is, relative to the fiducial.
                    double actual = offset - shortfall;
                    double absoluteError = -shortfall;
                    Double relative = null;
                    if (previousActual != null) {
                        relative = actual - previousActual;
                        if (Math.abs(relative) < stepTestStepMm * 0.25) {
                            stalled++;
                        }
                        largestJump = Math.max(largestJump, Math.abs(relative));
                    }
                    rows.add(new Object[] { axis.getName(), speed, step, offset, actual,
                            absoluteError, relative });
                    row(graph, "mm", axis.getName() + " " + speed + "x abs",
                            axisColor(axis.getType()), false, true)
                            .recordDataPoint(step, absoluteError);
                    if (relative != null) {
                        row(graph, "mm", axis.getName() + " " + speed + "x rel", COLOR_MEASURED,
                                true, false).recordDataPoint(step, relative);
                    }
                    previousActual = actual;
                    step++;
                }
                report.line("  %s at %.2fx: %d of %d steps moved less than a quarter of a step; "
                        + "largest single jump %.4f mm", axis.getName(), speed, stalled,
                        Math.max(0, step - 1), largestJump);
                if (step > 1 && stalled * 3 > step) {
                    // The worst speed is the conclusion: an axis that only tracks the commands
                    // when driven slowly still has the coarser resolution in a job.
                    effectiveResolution.merge(axis.getId(), largestJump, Math::max);
                    report.finding(Severity.Warning, "Axis %s at %.2fx does not respond to %.3f mm "
                            + "commands: %d of %d moved almost nothing and the motion then caught "
                            + "up in jumps of up to %.4f mm. Its effective resolution at this speed "
                            + "is around %.3f mm.", axis.getName(), speed, stepTestStepMm, stalled,
                            step - 1, largestJump, largestJump);
                }
                log("%s step test at %.2fx: %d stalled steps, largest jump %.4f mm",
                        axis.getName(), speed, stalled, largestJump);
            }
        }
        report.writeCsv("step-response.csv", new String[] { "axis", "speed", "step",
                "commanded_mm", "actual_mm", "absolute_error_mm", "relative_mm" }, rows);
        setStepGraph(graph);
    }

    /**
     * Sweep the fiducial across the field of view. Its measured machine position should not
     * change; that it does is the Units per Pixel scale error, and what is left after fitting a
     * scale to it is lens distortion.
     */
    private void measureFieldOfViewScale(ReferenceMachine machine, MachineDiagnosticsReport report,
            ReferenceHead head, ReferenceCamera camera, Location fiducial, Length diameter,
            List<FieldOfView> scale) throws Exception {
        if (fieldOfViewGridSteps < 3) {
            report.line("Field of view scan skipped: it needs at least a 3 by 3 grid.");
            return;
        }
        Location upp = camera.getUnitsPerPixelAtZ().convertToUnits(LengthUnit.Millimeters);
        double halfWidth = camera.getWidth() * upp.getX() / 2 * fieldOfViewGridFraction;
        double halfHeight = camera.getHeight() * upp.getY() / 2 * fieldOfViewGridFraction;
        List<Object[]> rows = new ArrayList<>();
        List<Double> offsetsX = new ArrayList<>();
        List<Double> errorsX = new ArrayList<>();
        List<Double> offsetsY = new ArrayList<>();
        List<Double> errorsY = new ArrayList<>();
        report.blank();
        report.line("Field of view scan, %d by %d positions over %.2f by %.2f mm",
                fieldOfViewGridSteps, fieldOfViewGridSteps, halfWidth * 2, halfHeight * 2);
        MovableUtils.moveToLocationAtSafeZ(camera, fiducial);
        for (int iy = 0; iy < fieldOfViewGridSteps; iy++) {
            double dy = -halfHeight + 2 * halfHeight * iy / (fieldOfViewGridSteps - 1.0);
            for (int ix = 0; ix < fieldOfViewGridSteps; ix++) {
                checkAborted();
                double dx = -halfWidth + 2 * halfWidth * ix / (fieldOfViewGridSteps - 1.0);
                Location target = fiducial.add(new Location(LengthUnit.Millimeters, dx, dy, 0, 0));
                // Z never changes here, so the moves stay in plane rather than routing via safe Z.
                camera.moveTo(target);
                Location detected = machine.getVisionSolutions().getDetectedLocation(camera, camera,
                        fiducial, diameter, "Field of view scan", false)
                        .convertToUnits(LengthUnit.Millimeters);
                double errorX = detected.getX() - fiducial.convertToUnits(LengthUnit.Millimeters).getX();
                double errorY = detected.getY() - fiducial.convertToUnits(LengthUnit.Millimeters).getY();
                rows.add(new Object[] { dx, dy, errorX, errorY });
                offsetsX.add(dx);
                errorsX.add(errorX);
                offsetsY.add(dy);
                errorsY.add(errorY);
            }
            log("Field of view row %d of %d", iy + 1, fieldOfViewGridSteps);
        }
        report.writeCsv("field-of-view.csv",
                new String[] { "offset_x_mm", "offset_y_mm", "error_x_mm", "error_y_mm" }, rows);
        reportFieldOfViewAxis(report, camera, "X", toArray(offsetsX), toArray(errorsX), upp.getX(),
                scale);
        reportFieldOfViewAxis(report, camera, "Y", toArray(offsetsY), toArray(errorsY), upp.getY(),
                scale);
    }

    private void reportFieldOfViewAxis(MachineDiagnosticsReport report, ReferenceCamera camera,
            String axis, double[] offsets, double[] errors, double unitsPerPixel,
            List<FieldOfView> scale) {
        LinearFit fit = MachineDiagnosticsMath.linearFit(offsets, errors);
        double residualSum = 0;
        for (int i = 0; i < offsets.length; i++) {
            double residual = errors[i] - fit.valueAt(offsets[i]);
            residualSum += residual * residual;
        }
        double rms = Math.sqrt(residualSum / offsets.length);
        scale.add(new FieldOfView(camera.getId(), axis, fit.slope, rms));
        report.line("  %s: scale error %+.2f%%, residual after removing it %.4f mm rms",
                axis, fit.slope * 100, rms);
        if (Math.abs(fit.slope) < 1) {
            double implied = unitsPerPixel / (1 - fit.slope);
            report.line("  %s: Units per Pixel is set to %.6f mm, the scan implies %.6f mm",
                    axis, unitsPerPixel, implied);
            if (Math.abs(fit.slope) > 0.005) {
                report.finding(Severity.Warning, "Units per Pixel in %s is off by %+.2f%%. Over a "
                        + "100 mm board that is %.2f mm of placement error, and it also scales "
                        + "every vision correction.", axis, fit.slope * 100,
                        Math.abs(fit.slope) * 100);
            }
        }
        if (rms > 0.02) {
            boolean calibrated = camera.getCalibration().isEnabled()
                    || camera.getAdvancedCalibration().isEnabled();
            report.finding(Severity.Warning, "The %s error across the field of view does not fit a "
                    + "straight line to better than %.4f mm rms. That residual is lens distortion, "
                    + "and lens calibration is %s on %s.", axis, rms,
                    calibrated ? "already enabled, so this is what it leaves behind" : "not enabled",
                    camera.getName());
        }
    }

    // Test group 4: how long the image takes to stop moving.

    private void testCameraSettle(ReferenceMachine machine, MachineDiagnosticsReport report)
            throws Exception {
        report.section("Camera settling");
        report.line("The image is sampled as fast as the camera delivers frames after a move, and "
                + "the fiducial is located in each frame. The settle time is when the last "
                + "excursion beyond %.2f px ends.", settleThresholdPixels);
        ReferenceHead head = requireHead(machine);
        ReferenceCamera camera = requireDownLookingCamera(head);
        Location fiducial = requireFiducial(head);
        Length diameter = head.getCalibrationPrimaryFiducialDiameter();
        double[] distances = MachineDiagnosticsMath.parseSeries(settleDistances);
        SimpleGraph graph = new SimpleGraph();
        graph.setRelativePaddingLeft(0.05);
        SimpleGraph.DataScale scale = graph.getScale("px");
        scale.setLabelShown(true);
        List<Object[]> rows = new ArrayList<>();
        int diameterPixels = (int) Math.round(diameter.convertToUnits(LengthUnit.Millimeters).getValue()
                / camera.getUnitsPerPixelAtZ().convertToUnits(LengthUnit.Millimeters).getX());
        Color[] palette = new Color[] { COLOR_X, COLOR_Y, COLOR_Z, COLOR_ROTATION, COLOR_MEASURED };
        int index = 0;
        // The move that took longest to settle is the conclusion, because the wait before a
        // capture is one setting and has to cover every move a job makes.
        Double worstSettled = null;
        double worstDistance = 0;
        for (double distance : distances) {
            checkAborted();
            Double settled = sampleSettling(camera, fiducial, distance, diameterPixels, graph, rows,
                    palette[index++ % palette.length]);
            if (settled != null && (worstSettled == null || settled > worstSettled)) {
                worstSettled = settled;
                worstDistance = distance;
            }
            if (settled == null) {
                report.line("  after a %.0f mm move: never settled within %.0f ms",
                        distance, settleSampleSeconds * 1000);
                report.finding(Severity.Warning, "The image from %s had not stopped moving %.0f ms "
                        + "after a %.0f mm move.", camera.getName(), settleSampleSeconds * 1000,
                        distance);
            }
            else {
                report.line("  after a %.0f mm move: settled at %.0f ms", distance, settled * 1000);
                log("Settle after %.0fmm: %.0f ms", distance, settled * 1000);
                if (camera instanceof AbstractSettlingCamera) {
                    AbstractSettlingCamera settling = (AbstractSettlingCamera) camera;
                    if (settling.getSettleMethod() == AbstractSettlingCamera.SettleMethod.FixedTime) {
                        double configured = settling.getSettleTimeMs() / 1000.0;
                        if (settled > configured) {
                            report.finding(Severity.Problem, "%s waits a fixed %.0f ms but the "
                                    + "image was still moving at %.0f ms after a %.0f mm move. "
                                    + "Vision is running on a shaking image.", camera.getName(),
                                    configured * 1000, settled * 1000, distance);
                        }
                        else if (settled < configured * 0.4) {
                            report.finding(Severity.Info, "%s waits a fixed %.0f ms but settles in "
                                    + "%.0f ms after a %.0f mm move. The difference is spent on "
                                    + "every single capture.", camera.getName(), configured * 1000,
                                    settled * 1000, distance);
                        }
                    }
                }
            }
        }
        report.writeCsv("camera-settle.csv",
                new String[] { "distance_mm", "time_s", "deviation_px" }, rows);
        setSettleGraph(graph);
        List<Settling> settleTimes = new ArrayList<>();
        if (worstSettled != null) {
            settleTimes.add(new Settling(camera.getId(), worstSettled, worstDistance));
        }
        recordResults(TestGroup.CameraSettle, report, results -> results.setSettling(settleTimes));
    }

    private Double sampleSettling(ReferenceCamera camera, Location fiducial, double distance,
            int diameterPixels, SimpleGraph graph, List<Object[]> rows, Color color)
                    throws Exception {
        MovableUtils.moveToLocationAtSafeZ(camera,
                fiducial.add(new Location(LengthUnit.Millimeters, -distance, 0, 0, 0)));
        camera.waitForCompletion(CompletionType.WaitForStillstand);
        Thread.sleep(machineSettleMs);
        List<Double> times = new ArrayList<>();
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        camera.actuateLightBeforeCapture();
        try {
            camera.moveTo(fiducial, 1.0);
            camera.waitForCompletion(CompletionType.WaitForStillstand);
            double t0 = NanosecondTime.getRuntimeSeconds();
            while (NanosecondTime.getRuntimeSeconds() - t0 < settleSampleSeconds) {
                checkAborted();
                BufferedImage image = camera.capture();
                double t = NanosecondTime.getRuntimeSeconds() - t0;
                Circle circle = locateCircle(image, diameterPixels);
                if (circle == null) {
                    continue;
                }
                // A camera can hand out the same frame twice. Counted as a sample it would look
                // like the image had stopped moving when in fact nothing new was looked at.
                if (!xs.isEmpty() && circle.x == xs.get(xs.size() - 1)
                        && circle.y == ys.get(ys.size() - 1)) {
                    continue;
                }
                times.add(t);
                xs.add(circle.x);
                ys.add(circle.y);
            }
        }
        finally {
            camera.actuateLightAfterCapture();
        }
        if (times.size() < 3) {
            throw new Exception("The fiducial could not be located in enough frames to measure "
                    + "settling. Check the fiducial diameter and the lighting.");
        }
        double finalX = xs.get(xs.size() - 1);
        double finalY = ys.get(ys.size() - 1);
        double[] timeArray = new double[times.size()];
        double[] deviations = new double[times.size()];
        for (int i = 0; i < times.size(); i++) {
            timeArray[i] = times.get(i);
            deviations[i] = Math.hypot(xs.get(i) - finalX, ys.get(i) - finalY);
            rows.add(new Object[] { distance, timeArray[i], deviations[i] });
            row(graph, "px", String.format("%.0f mm", distance), color, true, true)
                    .recordDataPoint(timeArray[i], deviations[i]);
        }
        return MachineDiagnosticsMath.settleTime(timeArray, deviations, settleThresholdPixels);
    }

    private Circle locateCircle(BufferedImage image, int diameterPixels) {
        Mat mat = OpenCvUtils.toMat(image);
        try {
            int minDiameter = Math.max(3, (int) (diameterPixels / 1.5));
            int maxDiameter = Math.max(minDiameter + 4, (int) (diameterPixels * 1.5));
            // Sampling starts once the machine already reports stillstand, so the fiducial is
            // within the vibration amplitude of the centre. A tight search keeps each frame cheap
            // enough that the sample rate is the camera's rather than this method's.
            int search = Math.min(Math.min(image.getWidth(), image.getHeight()),
                    Math.max(64, maxDiameter * 4));
            List<Circle> circles = DetectCircularSymmetry.findCircularSymmetry(mat,
                    image.getWidth() / 2, image.getHeight() / 2, minDiameter, maxDiameter,
                    search, search, search, 1, 1.2, 0.0, 4, 4,
                    DetectCircularSymmetry.SymmetryScore.OverallVarianceVsRingVarianceSum,
                    false, false, new ScoreRange());
            return circles.isEmpty() ? null : circles.get(0);
        }
        catch (Exception e) {
            Logger.trace(e, "Machine diagnostics: no circle in this frame");
            return null;
        }
        finally {
            mat.release();
        }
    }

    // Test group 5: how well homing reproduces the origin.

    private void testHoming(ReferenceMachine machine, MachineDiagnosticsReport report)
            throws Exception {
        report.section("Homing repeatability");
        ReferenceHead head = requireHead(machine);
        ReferenceCamera camera = requireDownLookingCamera(head);
        Location fiducial = requireFiducial(head);
        Length diameter = head.getCalibrationPrimaryFiducialDiameter();
        ReferenceControllerAxis xAxis = findControllerAxis(camera, Axis.Type.X);
        if (homingCycles < 2) {
            throw new Exception("Homing repeatability needs at least two cycles.");
        }
        List<Object[]> rows = new ArrayList<>();
        List<Double> xErrors = new ArrayList<>();
        List<Double> yErrors = new ArrayList<>();
        Location reference = fiducial.convertToUnits(LengthUnit.Millimeters);
        report.line("  %-8s %-12s %-12s", "cycle", "x mm", "y mm");
        for (int cycle = 0; cycle < homingCycles; cycle++) {
            checkAborted();
            log("Homing cycle %d of %d", cycle + 1, homingCycles);
            machine.home();
            checkAborted();
            // Always arrive from the same side, so that backlash does not masquerade as homing
            // scatter.
            if (xAxis != null) {
                approachFrom(camera, xAxis, fiducial, -10, 1.0);
            }
            else {
                MovableUtils.moveToLocationAtSafeZ(camera, fiducial);
            }
            Location detected = machine.getVisionSolutions().getDetectedLocation(camera, camera,
                    fiducial, diameter, "Homing repeatability", false)
                    .convertToUnits(LengthUnit.Millimeters);
            double errorX = detected.getX() - reference.getX();
            double errorY = detected.getY() - reference.getY();
            xErrors.add(errorX);
            yErrors.add(errorY);
            rows.add(new Object[] { cycle, errorX, errorY });
            report.line("  %-8d %-12.4f %-12.4f", cycle, errorX, errorY);
        }
        report.writeCsv("homing.csv", new String[] { "cycle", "error_x_mm", "error_y_mm" }, rows);
        Stats statsX = MachineDiagnosticsMath.stats(xErrors);
        Stats statsY = MachineDiagnosticsMath.stats(yErrors);
        report.line("  X spread %.4f mm (sd %.4f), Y spread %.4f mm (sd %.4f)",
                statsX.getRange(), statsX.stdDev, statsY.getRange(), statsY.stdDev);
        double worst = Math.max(statsX.getRange(), statsY.getRange());
        recordResults(TestGroup.Homing, report, results -> results
                .setHoming(new MachineDiagnosticsResults.Homing(head.getId(), worst, homingCycles)));
        Severity severity = worst > 0.05 ? Severity.Warning : Severity.Info;
        report.finding(severity, "Homing reproduces the origin to within %.3f mm over %d cycles.",
                worst, homingCycles);
        if (worst > 0.05 && head.getVisualHomingMethod() == ReferenceHead.VisualHomingMethod.None) {
            report.finding(Severity.Warning, "Visual homing is off, so this scatter is the "
                    + "repeatability of the endstops and it carries into every job. Visual homing "
                    + "against the fiducial would remove it.");
        }
    }

    // Test group 6: rotation backlash.

    private void testRotationBacklash(ReferenceMachine machine, MachineDiagnosticsReport report)
            throws Exception {
        report.section("Rotation backlash, measured on the bottom camera");
        ReferenceHead head = requireHead(machine);
        if (head.getNozzles().isEmpty()) {
            throw new Exception("The head has no nozzle.");
        }
        Nozzle nozzle = head.getDefaultNozzle();
        if (nozzle.getNozzleTip() == null) {
            throw new Exception("Nozzle " + nozzle.getName() + " has no nozzle tip loaded.");
        }
        Part part = getConfiguration().getPart(rotationTestPartId);
        if (part == null) {
            throw new Exception("Set the test part first. Pick a rectangular part by hand with "
                    + nozzle.getName() + ", then name it here so that bottom vision knows what to "
                    + "look for.");
        }
        if (part.getPackage() == null) {
            throw new Exception("Part " + part.getId() + " has no package, so bottom vision has no "
                    + "footprint to align to.");
        }
        ReferenceBottomVision bottomVision = null;
        for (PartAlignment alignment : machine.getPartAlignments()) {
            if (alignment instanceof ReferenceBottomVision) {
                bottomVision = (ReferenceBottomVision) alignment;
                break;
            }
        }
        if (bottomVision == null) {
            throw new Exception("The machine has no bottom vision.");
        }
        BottomVisionSettings settings = bottomVision.getInheritedVisionSettings(part);
        if (settings == null) {
            throw new Exception("Part " + part.getId() + " has no bottom vision settings.");
        }
        Camera camera = VisionUtils.getBottomVisionCamera();
        double[] angles = parseSignedSeries(rotationTestAngles);
        List<Object[]> rows = new ArrayList<>();
        report.line("Part %s, approach %+.1f deg, %d repeats per direction.", part.getId(),
                rotationApproachAngle, repeats);
        report.line("  %-8s %-12s %-12s %-12s", "angle", "from + deg", "from - deg", "backlash deg");
        // Rotation mode offsets shift the commanded angle, which would show up as backlash.
        Double savedOffset = nozzle.getRotationModeOffset();
        try {
            nozzle.setRotationModeOffset(null);
            List<Double> allBacklash = new ArrayList<>();
            for (double angle : angles) {
                checkAborted();
                List<Double> fromPlus = new ArrayList<>();
                List<Double> fromMinus = new ArrayList<>();
                for (int repeat = 0; repeat < repeats; repeat++) {
                    checkAborted();
                    fromPlus.add(measureAngleApproachedFrom(bottomVision, settings, camera, nozzle,
                            part, angle, rotationApproachAngle));
                    fromMinus.add(measureAngleApproachedFrom(bottomVision, settings, camera, nozzle,
                            part, angle, -rotationApproachAngle));
                }
                Stats plus = MachineDiagnosticsMath.stats(fromPlus);
                Stats minus = MachineDiagnosticsMath.stats(fromMinus);
                double backlash = plus.mean - minus.mean;
                allBacklash.add(backlash);
                rows.add(new Object[] { angle, plus.mean, minus.mean, backlash, plus.stdDev,
                        minus.stdDev });
                report.line("  %-8.1f %-12.4f %-12.4f %-12.4f", angle, plus.mean, minus.mean,
                        backlash);
                log("Rotation at %.0f deg: backlash %.4f deg", angle, backlash);
            }
            report.writeCsv("rotation-backlash.csv", new String[] { "angle_deg", "from_plus_deg",
                    "from_minus_deg", "backlash_deg", "sd_plus_deg", "sd_minus_deg" }, rows);
            if (!allBacklash.isEmpty()) {
                Stats stats = MachineDiagnosticsMath.stats(allBacklash);
                report.line("  Mean backlash %.4f deg over the tested angles.", stats.mean);
                ReferenceControllerAxis rotationAxis = findControllerAxis(nozzle, Axis.Type.Rotation);
                String axisName = rotationAxis != null ? rotationAxis.getName() : "rotation";
                if (rotationAxis != null) {
                    recordResults(TestGroup.RotationBacklash, report,
                            results -> results.setRotation(new MachineDiagnosticsResults.Rotation(
                                    rotationAxis.getId(), stats.mean)));
                }
                if (Math.abs(stats.mean) > 0.2) {
                    report.finding(Severity.Warning, "The %s axis has %.3f deg of backlash and %s "
                            + "compensation set. On a 5 mm part that is %.3f mm at the corner.",
                            axisName, Math.abs(stats.mean),
                            rotationAxis != null ? rotationAxis.getBacklashCompensationMethod().toString()
                                    : "no",
                            Math.toRadians(Math.abs(stats.mean)) * 2.5);
                }
                else {
                    report.finding(Severity.Info, "The %s axis backlash measures %.3f deg.",
                            axisName, Math.abs(stats.mean));
                }
            }
        }
        finally {
            nozzle.setRotationModeOffset(savedOffset);
        }
    }

    /**
     * Rotate past the wanted angle and come back to it, then measure the angle bottom vision
     * sees. The difference between approaching from one side and the other is the backlash.
     * <p>
     * Bottom vision's own alignment is not used for this: it iterates until the offset is small,
     * which is exactly the correction that hides the backlash being measured.
     */
    private double measureAngleApproachedFrom(ReferenceBottomVision bottomVision,
            BottomVisionSettings settings, Camera camera, Nozzle nozzle, Part part, double angle,
            double approach) throws Exception {
        Location wanted = bottomVision.getCameraLocationAtPartHeight(part, camera, nozzle, angle);
        MovableUtils.moveToLocationAtSafeZ(nozzle,
                wanted.derive(null, null, null, angle + approach));
        nozzle.waitForCompletion(CompletionType.WaitForStillstand);
        try (CvPipeline pipeline = settings.getPipeline()) {
            // The pipeline shot moves the nozzle to the wanted angle, arriving from the approach
            // side set up above.
            RotatedRect rect = bottomVision.processPipelineAndGetResult(pipeline, camera, part,
                    nozzle, wanted, wanted, settings);
            return Utils2D.angleNorm(VisionUtils.getPixelAngle(camera, rect.angle) - angle);
        }
    }

    // Test group 7: the settings all of the above was measured under.

    private void writeConfigSnapshot(ReferenceMachine machine, MachineDiagnosticsReport report)
            throws Exception {
        StringBuilder snapshot = new StringBuilder();
        snapshot.append("Settings in force when these measurements were taken.\n\n");

        snapshot.append("Axes\n====\n");
        for (Axis axis : machine.getAxes()) {
            snapshot.append("- ").append(axis.getName()).append(" (")
                    .append(axis.getClass().getSimpleName()).append(", ")
                    .append(axis.getType()).append(")\n");
            if (axis instanceof ReferenceControllerAxis) {
                ReferenceControllerAxis controllerAxis = (ReferenceControllerAxis) axis;
                snapshot.append("    letter ").append(controllerAxis.getLetter())
                        .append(", driver ").append(controllerAxis.getDriver() != null
                                ? controllerAxis.getDriver().getName() : "none")
                        .append(", resolution ").append(controllerAxis.getResolution()).append("\n");
                snapshot.append("    feed rate ").append(controllerAxis.getFeedratePerSecond())
                        .append("/s, acceleration ").append(controllerAxis.getAccelerationPerSecond2())
                        .append("/s2, jerk ").append(controllerAxis.getJerkPerSecond3())
                        .append("/s3\n");
                snapshot.append("    backlash ").append(controllerAxis.getBacklashCompensationMethod())
                        .append(", offset ").append(controllerAxis.getBacklashOffset())
                        .append(", sneak-up ").append(controllerAxis.getSneakUpOffset())
                        .append(", speed factor ").append(controllerAxis.getBacklashSpeedFactor())
                        .append(", tolerance ").append(controllerAxis.getAcceptableTolerance())
                        .append("\n");
                snapshot.append("    soft limits ")
                        .append(controllerAxis.isSoftLimitLowEnabled() ? controllerAxis.getSoftLimitLow().toString() : "off")
                        .append(" .. ")
                        .append(controllerAxis.isSoftLimitHighEnabled() ? controllerAxis.getSoftLimitHigh().toString() : "off")
                        .append(", safe zone ")
                        .append(controllerAxis.isSafeZoneLowEnabled() ? controllerAxis.getSafeZoneLow().toString() : "off")
                        .append(" .. ")
                        .append(controllerAxis.isSafeZoneHighEnabled() ? controllerAxis.getSafeZoneHigh().toString() : "off")
                        .append("\n");
                if (axis.getType() == Axis.Type.Rotation) {
                    snapshot.append("    limit rotation ").append(controllerAxis.isLimitRotation())
                            .append(", wrap around ").append(controllerAxis.isWrapAroundRotation())
                            .append("\n");
                }
            }
        }

        snapshot.append("\nDrivers\n=======\n");
        for (Driver driver : machine.getDrivers()) {
            snapshot.append("- ").append(driver.getName()).append(" (")
                    .append(driver.getClass().getSimpleName()).append(")\n");
            snapshot.append("    motion control ").append(driver.getMotionControlType())
                    .append(", units ").append(driver.getUnits()).append("\n");
            if (driver instanceof GcodeDriver) {
                GcodeDriver gcodeDriver = (GcodeDriver) driver;
                snapshot.append("    detected firmware: ")
                        .append(gcodeDriver.getDetectedFirmware()).append("\n");
                for (GcodeDriver.CommandType type : new GcodeDriver.CommandType[] {
                        GcodeDriver.CommandType.MOVE_TO_COMMAND,
                        GcodeDriver.CommandType.MOVE_TO_COMPLETE_COMMAND,
                        GcodeDriver.CommandType.GET_POSITION_COMMAND,
                        GcodeDriver.CommandType.POSITION_REPORT_REGEX }) {
                    String command = gcodeDriver.getCommand(null, type);
                    snapshot.append("    ").append(type).append(": ")
                            .append(command == null ? "(not set)" : command.replace("\n", " | "))
                            .append("\n");
                }
                if (gcodeDriver.getCommand(null, GcodeDriver.CommandType.MOVE_TO_COMPLETE_COMMAND) == null) {
                    report.finding(Severity.Problem, "Driver %s has no move-complete command, so "
                            + "nothing ever waits for the machine to physically stop. Vision and "
                            + "placement happen while it is still moving.", driver.getName());
                }
            }
        }
        snapshot.append("\nMotion planner: ")
                .append(machine.getMotionPlanner().getClass().getSimpleName()).append("\n");

        snapshot.append("\nHeads\n=====\n");
        for (Head head : machine.getHeads()) {
            snapshot.append("- ").append(head.getName()).append("\n");
            if (head instanceof ReferenceHead) {
                ReferenceHead referenceHead = (ReferenceHead) head;
                snapshot.append("    visual homing ").append(referenceHead.getVisualHomingMethod())
                        .append(" at ").append(referenceHead.getHomingFiducialLocation()).append("\n");
                snapshot.append("    primary fiducial ")
                        .append(referenceHead.getCalibrationPrimaryFiducialLocation())
                        .append(" diameter ")
                        .append(orNotSet(referenceHead.getCalibrationPrimaryFiducialDiameter()))
                        .append("\n");
                snapshot.append("    secondary fiducial ")
                        .append(referenceHead.getCalibrationSecondaryFiducialLocation())
                        .append(" diameter ")
                        .append(orNotSet(referenceHead.getCalibrationSecondaryFiducialDiameter()))
                        .append("\n");
                if (referenceHead.getVisualHomingMethod() == ReferenceHead.VisualHomingMethod.None) {
                    report.finding(Severity.Info, "Visual homing is off on head %s, so the origin "
                            + "is only as repeatable as the endstops.", head.getName());
                }
            }
            for (Nozzle nozzle : head.getNozzles()) {
                snapshot.append("    nozzle ").append(nozzle.getName())
                        .append(" offsets ").append(nozzle.getHeadOffsets())
                        .append(" rotation mode ").append(nozzle.getRotationMode()).append("\n");
            }
        }

        snapshot.append("\nCameras\n=======\n");
        for (Camera camera : machine.getAllCameras()) {
            snapshot.append("- ").append(camera.getName()).append(" (")
                    .append(camera.getLooking()).append(")\n");
            snapshot.append("    units per pixel ").append(camera.getUnitsPerPixel()).append("\n");
            if (camera instanceof AbstractCamera) {
                snapshot.append("    units per pixel in 3D ")
                        .append(((AbstractCamera) camera).isEnableUnitsPerPixel3D()).append("\n");
            }
            snapshot.append("    head offsets ").append(camera.getHeadOffsets()).append("\n");
            if (camera instanceof AbstractSettlingCamera) {
                AbstractSettlingCamera settling = (AbstractSettlingCamera) camera;
                snapshot.append("    settle ").append(settling.getSettleMethod())
                        .append(", time ").append(settling.getSettleTimeMs())
                        .append(" ms, timeout ").append(settling.getSettleTimeoutMs())
                        .append(" ms, threshold ").append(settling.getSettleThreshold()).append("\n");
            }
            if (camera instanceof ReferenceCamera) {
                ReferenceCamera referenceCamera = (ReferenceCamera) camera;
                snapshot.append("    lens calibration ")
                        .append(referenceCamera.getCalibration().isEnabled() ? "on" : "off")
                        .append(", advanced calibration ")
                        .append(referenceCamera.getAdvancedCalibration().isEnabled() ? "on" : "off")
                        .append(" (valid ").append(referenceCamera.getAdvancedCalibration().isValid())
                        .append(", rms ").append(referenceCamera.getAdvancedCalibration().getRmsError())
                        .append(")\n");
                if (!referenceCamera.getCalibration().isEnabled()
                        && !referenceCamera.getAdvancedCalibration().isEnabled()) {
                    report.finding(Severity.Info, "Camera %s has no lens calibration enabled, so "
                            + "anything measured away from the image centre carries the lens "
                            + "distortion with it.", camera.getName());
                }
            }
        }

        snapshot.append("\nNozzle tips\n===========\n");
        for (NozzleTip nozzleTip : machine.getNozzleTips()) {
            snapshot.append("- ").append(nozzleTip.getName()).append("\n");
            if (nozzleTip instanceof ReferenceNozzleTip) {
                ReferenceNozzleTipCalibration calibration =
                        ((ReferenceNozzleTip) nozzleTip).getCalibration();
                snapshot.append("    runout calibration ")
                        .append(calibration.isEnabled() ? "on" : "off")
                        .append(", algorithm ").append(calibration.getRunoutCompensationAlgorithm())
                        .append(", trigger ").append(calibration.getRecalibrationTrigger())
                        .append("\n");
                snapshot.append("    subdivisions ").append(calibration.getAngleSubdivisions())
                        .append(", allowed misdetections ").append(calibration.getAllowMisdetections())
                        .append(", Z offset ").append(calibration.getCalibrationZOffset()).append("\n");
                int minimumDetections = Math.max(3,
                        calibration.getAngleSubdivisions() + 1 - calibration.getAllowMisdetections());
                if (calibration.isEnabled() && minimumDetections <= 3
                        && calibration.getAngleSubdivisions() > 3) {
                    report.finding(Severity.Warning, "Nozzle tip %s allows %d misdetections out of "
                            + "%d angles, so a runout model fitted to as few as %d points counts as "
                            + "calibrated.", nozzleTip.getName(), calibration.getAllowMisdetections(),
                            calibration.getAngleSubdivisions(), minimumDetections);
                }
            }
        }

        snapshot.append("\nVision\n======\n");
        for (PartAlignment alignment : machine.getPartAlignments()) {
            if (alignment instanceof ReferenceBottomVision) {
                ReferenceBottomVision bottomVision = (ReferenceBottomVision) alignment;
                snapshot.append("- bottom vision: enabled ").append(bottomVision.isEnabled())
                        .append(", pre-rotate ").append(bottomVision.isPreRotate())
                        .append(", passes ").append(bottomVision.getMaxVisionPasses())
                        .append(", max linear offset ").append(bottomVision.getMaxLinearOffset())
                        .append(", max angular offset ").append(bottomVision.getMaxAngularOffset())
                        .append("\n");
            }
        }
        if (machine.getFiducialLocator() instanceof ReferenceFiducialLocator) {
            ReferenceFiducialLocator locator = (ReferenceFiducialLocator) machine.getFiducialLocator();
            snapshot.append("- fiducial locator: averaging ").append(locator.isEnabledAveraging())
                    .append(", max distance ").append(locator.getMaxDistance()).append("\n");
        }

        report.writeText("config-snapshot.txt", snapshot.toString());
        report.section("Configuration snapshot");
        report.line("Written to config-snapshot.txt.");
        log("Configuration snapshot written.");
        // No conclusions of its own, but the page shows when each group last ran and this one is
        // the group that says which settings everything else was measured under.
        recordResults(TestGroup.ConfigSnapshot, report, results -> {
        });
    }

    // Shared helpers.

    /** A setting that was never given a value says so, rather than reading "null". */
    private String orNotSet(Object value) {
        return value == null ? "(not set)" : value.toString();
    }

    private ReferenceHead requireHead(ReferenceMachine machine) throws Exception {
        Head head = machine.getDefaultHead();
        if (!(head instanceof ReferenceHead)) {
            throw new Exception("The machine has no reference head.");
        }
        return (ReferenceHead) head;
    }

    private ReferenceCamera requireDownLookingCamera(Head head) throws Exception {
        Camera camera = head.getDefaultCamera();
        if (!(camera instanceof ReferenceCamera) || camera.getLooking() != Looking.Down) {
            throw new Exception("The head has no down-looking camera to measure with.");
        }
        return (ReferenceCamera) camera;
    }

    private Location requireFiducial(ReferenceHead head) throws Exception {
        Location fiducial = head.getCalibrationPrimaryFiducialLocation();
        Length diameter = head.getCalibrationPrimaryFiducialDiameter();
        if (fiducial == null || (fiducial.getX() == 0 && fiducial.getY() == 0)) {
            throw new Exception("The primary calibration fiducial location is not set. Set it on "
                    + "the head, or run the Issues and Solutions calibration milestone first.");
        }
        if (diameter == null || diameter.getValue() <= 0) {
            throw new Exception("The primary calibration fiducial diameter is not set.");
        }
        return fiducial;
    }

    private ReferenceControllerAxis findControllerAxis(HeadMountable movable, Axis.Type type) {
        if (movable == null) {
            return null;
        }
        Axis axis = movable.getAxis(type);
        while (axis != null) {
            if (axis instanceof ReferenceControllerAxis) {
                return (ReferenceControllerAxis) axis;
            }
            // Transformed axes sit in front of the controller axis; follow the chain to it.
            if (axis instanceof AbstractSingleTransformedAxis) {
                axis = ((AbstractSingleTransformedAxis) axis).getInputAxis();
            }
            else if (axis instanceof LinearTransformAxis) {
                axis = ((LinearTransformAxis) axis).getPrimaryInputAxis();
            }
            else {
                return null;
            }
        }
        return null;
    }

    /**
     * How far short of its commanded position the machine physically stopped, along one axis, in
     * millimetres.
     * <p>
     * The detected location is the camera's believed position plus the subject's offset in the
     * image, so it comes out as commanded minus physical: a positive value means the machine is
     * at a lower coordinate than it was told to go to.
     *
     * @param detected Where the subject was detected.
     * @param commanded Where the camera was told to go.
     * @param unit A unit vector along the axis of interest.
     */
    private double shortfallMm(Location detected, Location commanded, Location unit) {
        return detected.subtract(commanded).dotProduct(unit)
                .convertToUnits(LengthUnit.Millimeters).getValue();
    }

    private Location unitLocation(Axis.Type type) {
        return new Location(LengthUnit.Millimeters, type == Axis.Type.X ? 1 : 0,
                type == Axis.Type.Y ? 1 : 0, 0, 0);
    }

    /**
     * How much the axis coordinate changes for one unit of head-mountable movement along it. One
     * for a directly driven axis, but a transformed axis can scale or invert.
     */
    private double axisUnitVector(HeadMountable movable, ReferenceControllerAxis axis,
            Location base) throws Exception {
        Location unit = unitLocation(axis.getType());
        if (axis.getType() == Axis.Type.Z) {
            unit = new Location(LengthUnit.Millimeters, 0, 0, 1, 0);
        }
        else if (axis.getType() == Axis.Type.Rotation) {
            unit = new Location(LengthUnit.Millimeters, 0, 0, 0, 1);
        }
        double at0 = axisCoordinate(movable, axis, base);
        double at1 = axisCoordinate(movable, axis, base.add(unit));
        double delta = at1 - at0;
        // Below the axis resolution the difference is numerical noise, not a transformation.
        return Math.abs(delta) < axis.getResolution() ? 0 : delta;
    }

    private double axisCoordinate(HeadMountable movable, ReferenceControllerAxis axis,
            Location location) throws Exception {
        return movable.toRaw(movable.toHeadLocation(location)).getCoordinate(axis);
    }

    /**
     * The given location with the axis moved to the middle of its permitted travel, so that a
     * test has as much room either side of it as the machine allows. Unchanged if the axis has no
     * soft limits, since then nothing is known about where its travel ends.
     */
    private Location axisTravelCentre(HeadMountable movable, ReferenceControllerAxis axis,
            Location location) throws Exception {
        if (!axis.isSoftLimitLowEnabled() || !axis.isSoftLimitHighEnabled()) {
            return location;
        }
        double low = axis.getSoftLimitLow().convertToUnits(axis.getUnits()).getValue();
        double high = axis.getSoftLimitHigh().convertToUnits(axis.getUnits()).getValue();
        AxesLocation axesLocation = movable.toRaw(movable.toHeadLocation(location));
        axesLocation = axesLocation.put(new AxesLocation(axis, (low + high) / 2));
        return movable.toHeadMountableLocation(movable.toTransformed(axesLocation));
    }

    /**
     * The given location, with one axis displaced and clamped to whichever soft limits are
     * actually enabled. Clamping against a disabled limit would pin the move to zero.
     */
    private Location displacedAxisLocation(HeadMountable movable, ReferenceControllerAxis axis,
            Location location, double displacement) throws Exception {
        AxesLocation axesLocation = movable.toRaw(movable.toHeadLocation(location));
        double target = axesLocation.getCoordinate(axis) + displacement;
        if (axis.isSoftLimitLowEnabled()) {
            target = Math.max(axis.getSoftLimitLow().convertToUnits(axis.getUnits()).getValue(), target);
        }
        if (axis.isSoftLimitHighEnabled()) {
            target = Math.min(axis.getSoftLimitHigh().convertToUnits(axis.getUnits()).getValue(), target);
        }
        axesLocation = axesLocation.put(new AxesLocation(axis, target));
        return movable.toHeadMountableLocation(movable.toTransformed(axesLocation));
    }

    /**
     * Move to the target having come from the given displacement along the axis, so that the
     * approach direction is known.
     */
    private void approachFrom(HeadMountable movable, ReferenceControllerAxis axis, Location target,
            double displacementMm, double speed) throws Exception {
        double axisPerUnit = axisUnitVector(movable, axis, target);
        if (axisPerUnit == 0) {
            throw new Exception("Axis " + axis.getName() + " does not move with "
                    + movable.getName() + ".");
        }
        MovableUtils.moveToLocationAtSafeZ(movable,
                displacedAxisLocation(movable, axis, target, displacementMm * axisPerUnit), speed);
        movable.moveTo(target, speed);
    }

    private void moveThere(HeadMountable movable, Location location, double speed) throws Exception {
        MovableUtils.moveToLocationAtSafeZ(movable, location, speed);
    }

    /**
     * A graph row, coloured and styled by what it holds. Rows are keyed by label, so this also
     * returns the existing row on later calls without restyling it.
     */
    private SimpleGraph.DataRow row(SimpleGraph graph, String scale, String label, Color color,
            boolean markers, boolean line) {
        SimpleGraph.DataRow row = graph.getRow(scale, label);
        row.setColor(color);
        row.setMarkerShown(markers);
        row.setLineShown(line);
        return row;
    }

    /** A stable colour per axis, so the same axis keeps its colour across all the graphs. */
    private Color axisColor(Axis.Type type) {
        switch (type) {
            case X:
                return COLOR_X;
            case Y:
                return COLOR_Y;
            case Z:
                return COLOR_Z;
            case Rotation:
                return COLOR_ROTATION;
            default:
                return COLOR_MEASURED;
        }
    }

    private SimpleGraph newTimingGraph() {
        SimpleGraph graph = new SimpleGraph();
        graph.setRelativePaddingLeft(0.05);
        graph.setLogarithmic(true);
        SimpleGraph.DataScale millimetres = graph.getScale("mm");
        millimetres.setLabelShown(true);
        SimpleGraph.DataScale degrees = graph.getScale("deg");
        degrees.setLabelShown(true);
        degrees.setRelativePaddingTop(0.55);
        millimetres.setRelativePaddingBottom(0.5);
        return graph;
    }

    private static class BacklashSetting {
        private final ReferenceControllerAxis axis;
        private final BacklashCompensationMethod method;

        BacklashSetting(ReferenceControllerAxis axis) {
            this.axis = axis;
            this.method = axis.getBacklashCompensationMethod();
        }

        void restore() {
            axis.setBacklashCompensationMethod(method);
        }
    }

    /**
     * Switch backlash compensation off for the duration of a measurement that has to see the
     * machine's own behaviour. The caller must restore it in a finally block: leaving a machine
     * with its compensation off would quietly degrade every job afterwards.
     */
    private List<BacklashSetting> suspendBacklashCompensation(ReferenceMachine machine,
            Axis.Type... types) {
        List<BacklashSetting> saved = new ArrayList<>();
        List<Axis.Type> wanted = Arrays.asList(types);
        for (Axis axis : machine.getAxes()) {
            if (axis instanceof ReferenceControllerAxis && wanted.contains(axis.getType())) {
                ReferenceControllerAxis controllerAxis = (ReferenceControllerAxis) axis;
                if (controllerAxis.getBacklashCompensationMethod() != BacklashCompensationMethod.None) {
                    saved.add(new BacklashSetting(controllerAxis));
                    controllerAxis.setBacklashCompensationMethod(BacklashCompensationMethod.None);
                }
            }
        }
        return saved;
    }

    private void restoreBacklashCompensation(List<BacklashSetting> saved) {
        for (BacklashSetting setting : saved) {
            setting.restore();
        }
    }

    private static double[] toArray(List<Double> values) {
        double[] array = new double[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = values.get(i);
        }
        return array;
    }

    /**
     * Like {@link MachineDiagnosticsMath#parseSeries} but for angles, which may be negative or
     * zero.
     */
    private static double[] parseSignedSeries(String text) throws Exception {
        List<Double> values = new ArrayList<>();
        if (text != null) {
            for (String token : text.split("[,;\\s]+")) {
                if (token.isEmpty()) {
                    continue;
                }
                try {
                    values.add(Double.valueOf(token));
                }
                catch (NumberFormatException e) {
                    throw new Exception("\"" + token + "\" is not a number.");
                }
            }
        }
        if (values.isEmpty()) {
            throw new Exception("The angle series is empty.");
        }
        return toArray(values);
    }
}
