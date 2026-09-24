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
import java.util.Collections;
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
import org.openpnp.model.CalibrationStep;
import org.openpnp.model.Configuration;
import org.openpnp.model.Job;
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
    /**
     * Half a pixel of frame-to-frame scatter with nothing moving. Sub-pixel detection on a well
     * lit fiducial holds a tenth of that; more says the lighting or the exposure is marginal.
     */
    private static final double VISION_NOISE_TOLERANCE_PIXELS = 0.5;
    /** Pipeline delay a camera may have before its settle wait has to be told about it. */
    private static final double CAMERA_LATENCY_TOLERANCE_SECONDS = 0.1;
    /** Drift after a run of fast moves that is more than the machine repeats to anyway. */
    private static final double LOST_STEPS_TOLERANCE_MM = 0.02;
    /** A decay time constant beyond which the image is still ringing when a job would capture. */
    private static final double SLOW_DECAY_SECONDS = 0.3;
    /** Z slack worth compensating: the height tolerance of a placement. */
    private static final double Z_BACKLASH_TOLERANCE_MM = 0.05;
    /** Machine scale error against the board worth a warning: 0.05 mm over 100 mm. */
    private static final double DATUM_SCALE_TOLERANCE = 0.0005;
    /** Squareness the board can vouch for; below this the board is as suspect as the machine. */
    private static final double DATUM_SQUARENESS_TOLERANCE_DEGREES = 0.1;
    /** Residual after the frame that is more than the fiducials' own placement tolerance. */
    private static final double DATUM_RESIDUAL_TOLERANCE_MM = 0.03;
    /** Periodic error at the belt pitch worth a warning. */
    private static final double PERIODIC_ERROR_TOLERANCE_MM = 0.01;
    /** X/Y backlash worth calibrating: the repeatability the lost steps test holds an axis to. */
    private static final double XY_BACKLASH_TOLERANCE_MM = 0.02;
    /**
     * The share of the measured backlash a directional compensation or a sneak-up has to set as
     * its offset to count as covering it; one-sided positioning has to clear all of it.
     */
    private static final double DIRECTIONAL_COVERAGE = 0.5;
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
    private static final String WIKI_VISION_SOLUTIONS =
            "https://github.com/openpnp/openpnp/wiki/Vision-Solutions"; //$NON-NLS-1$
    private static final String WIKI_LINEAR_TRANSFORMED_AXES =
            "https://github.com/openpnp/openpnp/wiki/Linear-Transformed-Axes"; //$NON-NLS-1$
    private static final String WIKI_VISUAL_HOMING =
            "https://github.com/openpnp/openpnp/wiki/Visual-Homing";

    public enum TestGroup {
        Firmware,
        /**
         * First of the measuring groups, because every position the others report is read through
         * the camera and this is what the camera's own scatter is.
         */
        VisionNoise,
        /** How late the camera's frames are, which the settle measurements are read through. */
        CameraLatency,
        Kinematics,
        /** Whether fast travel loses steps, which the timing above cannot see. */
        LostSteps,
        XyPositioning,
        CameraSettle,
        Homing,
        RotationBacklash,
        /** Where Z really stops, read off the bottom camera's focus on the nozzle tip. */
        ZFocus,
        /** The machine's frame against a board of known geometry. */
        DatumBoard,
        /** Backlash and sticking along the travel, against whatever round feature is there. */
        HysteresisMap,
        ConfigSnapshot
    }

    /** Step along the ruler; a quarter of the belt pitch, so the pitch can be seen. */
    @Attribute(required = false)
    private double rulerStepMm = 0.25;

    /**
     * Speed factor for every move whose speed is not itself what is being measured: getting to a
     * fiducial, hopping between the datum board's fiducials, stepping along the ruler. A machine
     * that loses steps at full speed is one of the things these tests are for, and the first
     * real run lost the board's frame to exactly that.
     */
    @Attribute(required = false)
    private double measureSpeedFactor = 0.5;

    /** Half the Z range the focus sweep covers around the bottom camera's focus height. */
    @Attribute(required = false)
    private double focusRangeMm = 0.5;

    /** Z step of the focus sweep. */
    @Attribute(required = false)
    private double focusStepMm = 0.05;

    /** Focus sweeps from each side. */
    @Attribute(required = false)
    private int focusRepeats = 5;

    /** Speed factors the stress test drives at; 1.0 is the configured limit and the top. */
    @Element(required = false)
    private String stressSpeedFactors = "0.25, 0.5, 0.75, 1";

    /** Back-and-forth cycles per speed factor of the stress test. */
    @Attribute(required = false)
    private int stressCycles = 20;

    /** Length of one leg of the stress test, centred on the fiducial. */
    @Attribute(required = false)
    private double stressDistanceMm = 100;

    /** Speed factor of the latency test's move: slow enough that nothing vibrates afterwards. */
    @Attribute(required = false)
    private double latencySpeedFactor = 0.05;

    /**
     * Home the machine before each speed factor of the lost steps test. The drift at one speed
     * is read as the difference between the fiducial before and after that speed's cycles, so
     * the loss at an earlier speed does not enter it either way; homing puts the legs back at
     * the same physical place for every speed, and keeps the accumulated loss of a run from
     * carrying the fiducial out of the camera's view.
     */
    @Attribute(required = false)
    private boolean homeBeforeEachStressSpeed = true;

    public boolean isHomeBeforeEachStressSpeed() {
        return homeBeforeEachStressSpeed;
    }

    public void setHomeBeforeEachStressSpeed(boolean homeBeforeEachStressSpeed) {
        boolean old = this.homeBeforeEachStressSpeed;
        this.homeBeforeEachStressSpeed = homeBeforeEachStressSpeed;
        firePropertyChange("homeBeforeEachStressSpeed", old, homeBeforeEachStressSpeed);
    }

    /**
     * Ten rather than five: the spread of five samples says what it is to about a third, which
     * is not enough to tell a 0.02 mm scatter from a 0.03 mm one, and that is the kind of
     * difference the backlash findings turn on.
     */
    @Attribute(required = false)
    private int repeats = 10;

    /**
     * Frames taken of each standing position, the median of which is the measurement. One frame
     * carries the camera's own scatter into every number; the frames are cheap next to the move
     * that preceded them.
     */
    @Attribute(required = false)
    private int framesPerPoint = 5;

    /** Frames taken of the fiducial with the machine standing still, for the noise floor. */
    @Attribute(required = false)
    private int noiseFrames = 30;

    /**
     * How long the camera then goes on watching the fiducial with nothing moving, for the slow
     * drift: the frame, the camera on its mount and the axes under holding current all move with
     * temperature, and what they move in a minute is what two readings a minute apart cannot
     * be compared closer than. Zero to skip.
     */
    @Attribute(required = false)
    private int driftSeconds = 60;

    /** Approach pairs per cell of the backlash matrix; one pair is one frame's luck. */
    @Attribute(required = false)
    private int backlashRepeats = 3;

    /** Runs per settle distance; the wait has to cover the slowest of them. */
    @Attribute(required = false)
    private int settleRepeats = 3;

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

    /** Five, because three homing cycles cannot say anything about a spread. */
    @Attribute(required = false)
    private int homingCycles = 5;

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

            {
                withChange("Misdetections tolerated", () -> oldAllowMisdetections, () -> proposed);
            }

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
        }.withCalibrationStep(CalibrationStep.NozzleTipCalibration));
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
                    private final int oldSuperSampling = stage.getSuperSampling();

                    {
                        withChange("Super-sampling", () -> oldSuperSampling, () -> target);
                    }

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
                }.withCalibrationStep(CalibrationStep.SubPixel));
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
                                .convertToUnits(LengthUnit.Millimeters).getValue()))
                                        .withCalibrationStep(CalibrationStep.VisualHoming));
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
        for (MachineDiagnosticsResults.VisionNoise noise : results.getVisionNoise()) {
            findVisionNoiseIssue(solutions, noise, measuredWhen(results, TestGroup.VisionNoise));
        }
        for (MachineDiagnosticsResults.CameraLatency latency : results.getCameraLatency()) {
            findCameraLatencyIssue(solutions, latency,
                    measuredWhen(results, TestGroup.CameraLatency));
        }
        findLostStepsIssues(solutions, results);
        for (MachineDiagnosticsResults.ZFocus focus : results.getZFocus()) {
            findZBacklashIssue(solutions, focus, measuredWhen(results, TestGroup.ZFocus));
        }
        if (results.getDatum() != null) {
            findDatumIssues(solutions, results, results.getDatum(),
                    measuredWhen(results, TestGroup.DatumBoard));
        }
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
                    (value, solved) -> axis.setFeedratePerSecond(value))
                            .measuredBy(TestGroup.Firmware)
                            .withCalibrationStep(CalibrationStep.ControllerLimits));
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
                    (value, solved) -> axis.setAccelerationPerSecond2(value))
                            .measuredBy(TestGroup.Firmware)
                            .withCalibrationStep(CalibrationStep.ControllerLimits));
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
                                value.convertToUnits(driverUnits).getValue()))
                                        .measuredBy(TestGroup.Firmware)
                                        .withCalibrationStep(CalibrationStep.ControllerLimits));
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
                    (value, solved) -> axis.setFeedratePerSecond(value))
                            .measuredBy(TestGroup.Kinematics)
                            .withCalibrationStep(CalibrationStep.FeedAcceleration));
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
                    (value, solved) -> axis.setAccelerationPerSecond2(value))
                            .measuredBy(TestGroup.Kinematics)
                            .withCalibrationStep(CalibrationStep.FeedAcceleration));
        }
    }

    /**
     * X/Y backlash that the compensation set on the axis does not cover. One-sided positioning
     * drives past the target by the offset before approaching it, so an offset short of the
     * backlash starts the approach inside the slack; a directional compensation of well under
     * the backlash leaves most of it; no compensation leaves all of it.
     * <p>
     * Pointed at, not written: the backlash calibration Issues and Solutions offers is the one
     * way the compensation is set, choosing the method as well as the offset. This finding once
     * raised the offset to 1.2 times the measured backlash on its own, the second of two ways of
     * setting the same thing, and it never looked at the method.
     */
    private void findBacklashOffsetIssue(Solutions solutions, ReferenceControllerAxis axis,
            Positioning positioning, String when) {
        double measured = Math.abs(positioning.getBacklashMaxMm());
        if (measured <= XY_BACKLASH_TOLERANCE_MM) {
            return;
        }
        BacklashCompensationMethod method = axis.getBacklashCompensationMethod();
        Length offset = axis.getBacklashOffset().convertToUnits(LengthUnit.Millimeters);
        boolean covered = method != BacklashCompensationMethod.None
                && Math.abs(offset.getValue()) >= measured
                        * (method.isOneSidedPositioningMethod() ? 1.0 : DIRECTIONAL_COVERAGE);
        if (covered) {
            return;
        }
        solutions.add(new PointerIssue(axis,
                "One-sided backlash compensation is set to less offset than the axis has "
                        + "backlash.",
                "Calibrate the backlash compensation of the axis, as offered here.",
                Solutions.Severity.Warning, WIKI_MOTION_PLANNER,
                String.format("The axis measured up to %.4f mm of backlash on %s, with compensation "
                        + "switched off, and it is compensated by %s with an offset of %.4f mm. "
                        + "That leaves most of the slack in every position the axis approaches. "
                        + "The backlash calibration measures it again and sets the method and the "
                        + "offsets together.", measured, when, method, offset.getValue()))
                .measuredBy(TestGroup.XyPositioning)
                .withCalibrationStep(CalibrationStep.XyBacklash));
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
        // The one fixed wait: the slower of the image settling and the frames arriving late,
        // with the margin. The settle time includes the latency when the settling test saw it,
        // but a run that measured the latency on its own knows it better.
        MachineDiagnosticsResults.CameraLatency latency = lastResults == null ? null
                : lastResults.getCameraLatency(camera.getId());
        double wait = Math.max(measured, latency == null ? 0 : latency.getLatencySeconds());
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
                    configured, settleMilliseconds(wait * SETTLE_MARGIN))
                    .measuredBy(TestGroup.CameraSettle)
                    .withCalibrationStep(CalibrationStep.CameraSettle));
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
                    configured, settleMilliseconds(wait * SETTLE_MARGIN))
                    .measuredBy(TestGroup.CameraSettle)
                    .withCalibrationStep(CalibrationStep.CameraSettle));
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
                            scan.getScaleError() * 100, scan.getAxis(), scan.getResidualMm()))
                                    .measuredBy(TestGroup.XyPositioning)
                                    .withCalibrationStep(CalibrationStep.AdvancedDownCamera));
        }
    }

    /**
     * A camera whose detection scatters by more than half a pixel with nothing moving. There is
     * no setting to write for this - it is lighting, exposure, focus - so it points at the vision
     * solutions and says what every measurement through the camera inherits.
     */
    private void findVisionNoiseIssue(Solutions solutions, MachineDiagnosticsResults.VisionNoise noise,
            String when) {
        if (noise.getSdPixels() <= VISION_NOISE_TOLERANCE_PIXELS) {
            return;
        }
        ReferenceCamera camera = camera(noise.getCameraId());
        if (camera == null) {
            return;
        }
        solutions.add(new PointerIssue(camera,
                "The camera cannot locate a standing fiducial to within half a pixel from frame "
                        + "to frame.",
                "Improve the lighting, exposure and focus of the camera, with the vision solutions "
                        + "offered here.",
                Solutions.Severity.Warning, WIKI_VISION_SOLUTIONS,
                String.format("With the machine standing still on %s, %d frames of %s put the "
                        + "fiducial %.3f px (%.4f mm) apart, sd, and up to %.3f px. Every position "
                        + "the machine is measured at goes through this camera, so nothing it "
                        + "measures - backlash, repeatability, homing scatter - can be read "
                        + "closer than this, and every vision correction in a job carries the "
                        + "same scatter into placement. A well lit fiducial in focus holds a "
                        + "tenth of a pixel with sub-pixel detection enabled.", when,
                        noise.getFrames(), camera.getName(), noise.getSdPixels(), noise.getSdMm(),
                        noise.getRangePixels())).measuredBy(TestGroup.VisionNoise));
    }

    /**
     * A fixed settle wait shorter than the camera's own pipeline delay: the frame captured at the
     * end of the wait was taken before the wait began. The measured settle time already includes
     * the latency, so this only fires when the settle group has not been run; once it has, the
     * settle issue carries the same conclusion with the better number.
     */
    private void findCameraLatencyIssue(Solutions solutions,
            MachineDiagnosticsResults.CameraLatency latency, String when) {
        ReferenceCamera camera = camera(latency.getCameraId());
        if (camera == null || camera.getSettleMethod() != AbstractSettlingCamera.SettleMethod.FixedTime) {
            return;
        }
        long configured = camera.getSettleTimeMs();
        if (configured >= latency.getLatencySeconds() * 1000) {
            return;
        }
        if (lastResults != null) {
            for (Settling settling : lastResults.getSettling()) {
                if (settling.getCameraId().equals(camera.getId())) {
                    return;
                }
            }
        }
        solutions.add(new SettleTimeIssue(camera,
                "The camera waits a fixed time shorter than the delay of its own frames.",
                "Wait at least as long as the camera's frames were measured being late.",
                Solutions.Severity.Error,
                String.format("Frames from %s arrive %.0f ms late, measured on %s from frames that "
                        + "still showed a move after the controller had reported it complete, and "
                        + "the camera waits %d ms before it captures. Whatever is captured after "
                        + "a move is therefore an image from before the move finished, whatever "
                        + "the machine has settled to since. The offered wait covers the latency "
                        + "with a margin; the camera settling test measures the mechanical "
                        + "settling on top of it.", camera.getName(),
                        latency.getLatencySeconds() * 1000, when, configured),
                configured, settleMilliseconds(latency.getLatencySeconds() * SETTLE_MARGIN))
                        .measuredBy(TestGroup.CameraLatency)
                        .withCalibrationStep(CalibrationStep.CameraSettle));
    }

    /**
     * An axis that lost steps at some speed factor. The offered fix is the feed rate scaled to
     * the highest factor that came back clean, which is the one thing that is known to be safe;
     * acceleration is the other suspect and the description says so.
     */
    private void findLostStepsIssues(Solutions solutions, MachineDiagnosticsResults results) {
        Map<String, List<MachineDiagnosticsResults.LostSteps>> byAxis = new LinkedHashMap<>();
        for (MachineDiagnosticsResults.LostSteps loss : results.getLostSteps()) {
            byAxis.computeIfAbsent(loss.getAxisId(), k -> new ArrayList<>()).add(loss);
        }
        String when = measuredWhen(results, TestGroup.LostSteps);
        for (Map.Entry<String, List<MachineDiagnosticsResults.LostSteps>> entry : byAxis.entrySet()) {
            ReferenceControllerAxis axis = controllerAxis(entry.getKey());
            if (axis == null) {
                continue;
            }
            double lowestLosingFactor = Double.NaN;
            double highestCleanFactor = 0;
            double worstDrift = 0;
            double travel = 0;
            double feedRateAtTest = 0;
            for (MachineDiagnosticsResults.LostSteps loss : entry.getValue()) {
                boolean lost = Math.abs(loss.getDriftMm()) > LOST_STEPS_TOLERANCE_MM;
                if (lost) {
                    if (Double.isNaN(lowestLosingFactor) || loss.getSpeedFactor() < lowestLosingFactor) {
                        lowestLosingFactor = loss.getSpeedFactor();
                        worstDrift = loss.getDriftMm();
                        travel = loss.getTravelMm();
                    }
                }
                else if (loss.getSpeedFactor() > highestCleanFactor) {
                    highestCleanFactor = loss.getSpeedFactor();
                }
                feedRateAtTest = loss.getFeedRateAtTest();
            }
            if (Double.isNaN(lowestLosingFactor)) {
                continue;
            }
            double configured = axis.getMotionLimit(1);
            if (feedRateAtTest > 0 && configured < feedRateAtTest * lowestLosingFactor) {
                // Already slowed below the speed that lost steps since the measurement.
                continue;
            }
            double offered = (feedRateAtTest > 0 ? feedRateAtTest : configured)
                    * (highestCleanFactor > 0 ? highestCleanFactor : 0.5);
            solutions.add(new LengthSettingIssue(axis,
                    "The axis loses steps at the speed it is planned with.",
                    "Lower the feed rate to the highest speed that was measured arriving where "
                            + "it was sent.",
                    Solutions.Severity.Error, WIKI_MACHINE_AXES,
                    "Feed rate per second",
                    "The feed rate the axis will be planned with.",
                    String.format("After %.0f mm of travel at %.2f of its feed rate on %s, axis %s "
                            + "came back %.4f mm from where it started, and the controller's "
                            + "position count did not know. At %.2f of the feed rate it came back "
                            + "where it started. The offered feed rate is that fraction of the one "
                            + "it was tested with; if the loss is from acceleration rather than "
                            + "speed, lowering the acceleration instead would let the feed rate "
                            + "stay.", travel, lowestLosingFactor, when, axis.getName(), worstDrift,
                            highestCleanFactor),
                    axis.getFeedratePerSecond(),
                    new Length(offered, AxesLocation.getUnits()),
                    (value, solved) -> axis.setFeedratePerSecond(value))
                            .measuredBy(TestGroup.LostSteps)
                            .withCalibrationStep(CalibrationStep.FeedAcceleration));
        }
    }

    /**
     * The machine's frame against the datum board: a millimetre that is not a millimetre, or
     * axes that are not square. Neither is a setting this writes. A scale error lives in the
     * controller's steps per millimetre, and the corrected figure is given where the firmware
     * group reported the current one; squareness is corrected with a linear transformed axis,
     * whose factor is given. Both are pointed at rather than applied, because rewriting the
     * axis chain or the firmware from here is more than a click should do.
     */
    private void findDatumIssues(Solutions solutions, MachineDiagnosticsResults results,
            MachineDiagnosticsResults.Datum datum, String when) {
        Head head = machine.getHead(datum.getHeadId());
        if (!(head instanceof ReferenceHead)) {
            return;
        }
        ReferenceCamera camera = null;
        for (Camera c : head.getCameras()) {
            if (c instanceof ReferenceCamera && c.getLooking() == Camera.Looking.Down) {
                camera = (ReferenceCamera) c;
                break;
            }
        }
        double[] scales = { datum.getScaleX(), datum.getScaleY() };
        Axis.Type[] types = { Axis.Type.X, Axis.Type.Y };
        for (int i = 0; i < 2; i++) {
            double error = scales[i] - 1;
            if (Math.abs(error) <= DATUM_SCALE_TOLERANCE || camera == null) {
                continue;
            }
            ReferenceControllerAxis axis = findControllerAxis(camera, types[i]);
            if (axis == null) {
                for (Axis candidate : machine.getAxes()) {
                    if (candidate instanceof ReferenceControllerAxis
                            && candidate.getType() == types[i]) {
                        axis = (ReferenceControllerAxis) candidate;
                        break;
                    }
                }
            }
            if (axis == null) {
                continue;
            }
            String steps = "";
            for (ControllerLimits limits : results.getControllerLimits()) {
                if (limits.getAxisId().equals(axis.getId()) && limits.getStepsPerUnit() != null) {
                    // The machine moves too far when it steps too few per millimetre.
                    steps = String.format(" The controller reports %.4f steps per mm for this "
                            + "axis; %.4f would make the millimetre exact.", limits.getStepsPerUnit(),
                            limits.getStepsPerUnit() * scales[i]);
                }
            }
            solutions.add(new PointerIssue(axis,
                    "A commanded millimetre on the axis is not a millimetre on the table.",
                    "Correct the controller's steps per millimetre for the axis by the scale "
                            + "measured, or map the axis through a linear transformed axis with that "
                            + "factor.",
                    Solutions.Severity.Warning, WIKI_MACHINE_AXES,
                    String.format("Against the %s on %s, a millimetre commanded on axis %s came out "
                            + "%+.3f%% long, over %d fiducials placed by one photoplot to about "
                            + "0.02 mm. Over a 100 mm board that is %.3f mm at the far edge. Every "
                            + "position taught on this machine was taught with the same short "
                            + "millimetre and agrees with every other, and a board with two or more "
                            + "fiducials has the scale taken out by its fiducial check; the error "
                            + "reaches placements on boards without fiducials, and the Units per "
                            + "Pixel that was calibrated by moving the machine. Correcting the "
                            + "controller's steps per millimetre changes every taught coordinate - "
                            + "fiducials, feeders, camera and nozzle offsets. The Compensate button on "
                            + "the diagnostics page does exactly that: a copy of machine.xml, two "
                            + "transform axes, every taught coordinate carried across, and a "
                            + "verification run on the board that keeps or undoes it.%s",
                            datum.getBoard(), when, axis.getName(), error * 100, datum.getPoints(),
                            Math.abs(error) * 100, steps))
                                    .measuredBy(TestGroup.DatumBoard)
                                    .withCalibrationStep(CalibrationStep.FrameCompensation, machine));
        }
        if (Math.abs(datum.getShearDegrees()) > DATUM_SQUARENESS_TOLERANCE_DEGREES) {
            double factor = -Math.tan(Math.toRadians(datum.getShearDegrees()));
            solutions.add(new PointerIssue((ReferenceHead) head,
                    "The X and Y axes are not square to each other.",
                    "Compensate the squareness with a linear transformed X axis that takes the "
                            + "measured fraction of Y.",
                    Solutions.Severity.Warning, WIKI_LINEAR_TRANSFORMED_AXES,
                    String.format("Against the %s on %s, the Y axis leans %+.3f degrees towards +X "
                            + "from square, measured over %d fiducials. Over 100 mm of Y that "
                            + "moves X by %.3f mm, and it turns every board by that angle relative "
                            + "to its own fiducials. A linear transformed axis of type X, with the "
                            + "X axis as its input at a factor of 1 and the Y axis at a factor of "
                            + "%+.6f, takes it out. The Compensate button on the diagnostics page "
                            + "adds it, together with the scale, when asked to.", datum.getBoard(),
                            when, datum.getShearDegrees(), datum.getPoints(),
                            Math.abs(Math.tan(Math.toRadians(datum.getShearDegrees()))) * 100,
                            factor))
                                    .measuredBy(TestGroup.DatumBoard)
                                    .withCalibrationStep(CalibrationStep.FrameCompensation, machine));
        }
    }

    /**
     * Slack in Z that the compensation does not cover. Z on most machines is set to directional
     * compensation with an offset of zero, which is no compensation at all; the focus sweep says
     * what the offset should be.
     */
    private void findZBacklashIssue(Solutions solutions, MachineDiagnosticsResults.ZFocus focus,
            String when) {
        double measured = Math.abs(focus.getBacklashMm());
        if (measured <= Z_BACKLASH_TOLERANCE_MM) {
            return;
        }
        ReferenceControllerAxis axis = controllerAxis(focus.getAxisId());
        if (axis == null) {
            return;
        }
        double offset = Math.abs(axis.getBacklashOffset().convertToUnits(LengthUnit.Millimeters)
                .getValue());
        if (axis.getBacklashCompensationMethod() != BacklashCompensationMethod.None
                && offset >= measured * 0.5) {
            return;
        }
        solutions.add(new LengthSettingIssue(axis,
                "The Z axis has slack between moving down and moving up that is not compensated.",
                "Compensate Z in the direction of travel, by the slack measured at the focus.",
                Solutions.Severity.Warning, WIKI_MOTION_PLANNER,
                "Backlash offset",
                "How far the commanded height is shifted in the direction Z is moving.",
                String.format("The nozzle tip came into focus on the bottom camera %.4f mm apart "
                        + "depending on whether Z arrived from above or from below, measured on "
                        + "%s over %d sweeps each way, and axis %s has %s compensation with an "
                        + "offset of %.4f mm. Every placement height is off by that much, one way "
                        + "or the other, according to which way Z last moved. Accepting sets "
                        + "compensation in the direction of travel by the slack measured.",
                        measured, when, focus.getRepeats(), axis.getName(),
                        axis.getBacklashCompensationMethod(), offset),
                axis.getBacklashOffset(), new Length(measured, LengthUnit.Millimeters)
                        .convertToUnits(axis.getBacklashOffset().getUnits()),
                new LengthSetting() {
                    private final BacklashCompensationMethod previousMethod =
                            axis.getBacklashCompensationMethod();

                    @Override
                    public void set(Length value, boolean solved) {
                        if (solved && previousMethod == BacklashCompensationMethod.None) {
                            axis.setBacklashCompensationMethod(
                                    BacklashCompensationMethod.DirectionalCompensation);
                        }
                        else if (!solved) {
                            axis.setBacklashCompensationMethod(previousMethod);
                        }
                        axis.setBacklashOffset(value);
                    }
                }).measuredBy(TestGroup.ZFocus).withCalibrationStep(CalibrationStep.ZBacklash));
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
                        homing.getSpreadMm()))
                                .measuredBy(TestGroup.Homing)
                                .withCalibrationStep(CalibrationStep.VisualHoming));
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
                })
                        .measuredBy(TestGroup.RotationBacklash)
                        .withCalibrationStep(CalibrationStep.RotationBacklash));
    }

    /**
     * Marks an issue as one of these checks, so that the diagnostics page can show what it found
     * among everything else the machine reports. The page is the one place that needs to tell
     * them apart; Issues and Solutions deliberately does not care where an issue came from.
     */
    public interface Finding {
        /**
         * The group whose measurement the finding holds against the configuration, or null for
         * one read off the configuration alone. It is what the calibration page gives as the
         * basis of the step, with when it was measured, and what it measures again when that
         * measurement is missing or out of date.
         */
        default TestGroup getMeasuredBy() {
            return null;
        }
    }

    /** An issue one of these checks raised, which is the whole of what the marker means. */
    private abstract static class DiagnosticIssue extends Solutions.Issue implements Finding {
        private TestGroup measuredBy;

        DiagnosticIssue(Solutions.Subject subject, String issue, String solution,
                Solutions.Severity severity, String uri) {
            super(subject, issue, solution, severity, uri);
        }

        DiagnosticIssue measuredBy(TestGroup group) {
            this.measuredBy = group;
            return this;
        }

        @Override
        public TestGroup getMeasuredBy() {
            return measuredBy;
        }
    }

    /**
     * A finding with no fix of its own, because the fix is a calibration Issues and Solutions
     * already offers. It says what was measured and names the solution that does the work, so
     * that there is one place performing each calibration rather than two that can drift apart.
     */
    private static class PointerIssue extends Solutions.PlainIssue implements Finding {
        private final String explanation;
        private TestGroup measuredBy;

        PointerIssue(Solutions.Subject subject, String issue, String solution,
                Solutions.Severity severity, String uri, String explanation) {
            super(subject, issue, solution, severity, uri);
            this.explanation = explanation;
        }

        PointerIssue measuredBy(TestGroup group) {
            this.measuredBy = group;
            return this;
        }

        @Override
        public TestGroup getMeasuredBy() {
            return measuredBy;
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
    private static class LengthSettingIssue extends DiagnosticIssue
            implements org.openpnp.machine.reference.calibration.SettingChange {
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

        @Override
        public String getSettingName() {
            return label;
        }

        @Override
        public Object getCurrentValue() {
            return previous;
        }

        @Override
        public Object getProposedValue() {
            return proposed;
        }
    }

    /**
     * The settle time issues, which differ from the length ones only in that a wait is a number
     * of milliseconds and the camera keeps it as one.
     */
    private static class SettleTimeIssue extends DiagnosticIssue
            implements org.openpnp.machine.reference.calibration.SettingChange {
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

        @Override
        public String getSettingName() {
            return "Settle time in milliseconds";
        }

        @Override
        public Object getCurrentValue() {
            return previous;
        }

        @Override
        public Object getProposedValue() {
            return proposed;
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

    public double getMeasureSpeedFactor() {
        return measureSpeedFactor;
    }

    public void setMeasureSpeedFactor(double measureSpeedFactor) {
        double old = this.measureSpeedFactor;
        this.measureSpeedFactor = Math.max(0.05, Math.min(1.0, measureSpeedFactor));
        firePropertyChange("measureSpeedFactor", old, this.measureSpeedFactor);
    }

    public double getRulerStepMm() {
        return rulerStepMm;
    }

    public void setRulerStepMm(double rulerStepMm) {
        double old = this.rulerStepMm;
        this.rulerStepMm = rulerStepMm;
        firePropertyChange("rulerStepMm", old, rulerStepMm);
    }

    public double getFocusRangeMm() {
        return focusRangeMm;
    }

    public void setFocusRangeMm(double focusRangeMm) {
        double old = this.focusRangeMm;
        this.focusRangeMm = focusRangeMm;
        firePropertyChange("focusRangeMm", old, focusRangeMm);
    }

    public double getFocusStepMm() {
        return focusStepMm;
    }

    public void setFocusStepMm(double focusStepMm) {
        double old = this.focusStepMm;
        this.focusStepMm = focusStepMm;
        firePropertyChange("focusStepMm", old, focusStepMm);
    }

    public int getFocusRepeats() {
        return focusRepeats;
    }

    public void setFocusRepeats(int focusRepeats) {
        int old = this.focusRepeats;
        this.focusRepeats = Math.max(1, focusRepeats);
        firePropertyChange("focusRepeats", old, this.focusRepeats);
    }

    public String getStressSpeedFactors() {
        return stressSpeedFactors;
    }

    public void setStressSpeedFactors(String stressSpeedFactors) {
        String old = this.stressSpeedFactors;
        this.stressSpeedFactors = stressSpeedFactors;
        firePropertyChange("stressSpeedFactors", old, stressSpeedFactors);
    }

    public int getStressCycles() {
        return stressCycles;
    }

    public void setStressCycles(int stressCycles) {
        int old = this.stressCycles;
        this.stressCycles = Math.max(1, stressCycles);
        firePropertyChange("stressCycles", old, this.stressCycles);
    }

    public double getStressDistanceMm() {
        return stressDistanceMm;
    }

    public void setStressDistanceMm(double stressDistanceMm) {
        double old = this.stressDistanceMm;
        this.stressDistanceMm = stressDistanceMm;
        firePropertyChange("stressDistanceMm", old, stressDistanceMm);
    }

    public double getLatencySpeedFactor() {
        return latencySpeedFactor;
    }

    public void setLatencySpeedFactor(double latencySpeedFactor) {
        double old = this.latencySpeedFactor;
        this.latencySpeedFactor = latencySpeedFactor;
        firePropertyChange("latencySpeedFactor", old, latencySpeedFactor);
    }

    public int getFramesPerPoint() {
        return framesPerPoint;
    }

    public void setFramesPerPoint(int framesPerPoint) {
        int old = this.framesPerPoint;
        this.framesPerPoint = Math.max(1, framesPerPoint);
        firePropertyChange("framesPerPoint", old, this.framesPerPoint);
    }

    public int getNoiseFrames() {
        return noiseFrames;
    }

    public int getDriftSeconds() {
        return driftSeconds;
    }

    public void setDriftSeconds(int driftSeconds) {
        int old = this.driftSeconds;
        this.driftSeconds = Math.max(0, driftSeconds);
        firePropertyChange("driftSeconds", old, this.driftSeconds);
    }

    public void setNoiseFrames(int noiseFrames) {
        int old = this.noiseFrames;
        this.noiseFrames = Math.max(5, noiseFrames);
        firePropertyChange("noiseFrames", old, this.noiseFrames);
    }

    public int getBacklashRepeats() {
        return backlashRepeats;
    }

    public void setBacklashRepeats(int backlashRepeats) {
        int old = this.backlashRepeats;
        this.backlashRepeats = Math.max(1, backlashRepeats);
        firePropertyChange("backlashRepeats", old, this.backlashRepeats);
    }

    public int getSettleRepeats() {
        return settleRepeats;
    }

    public void setSettleRepeats(int settleRepeats) {
        int old = this.settleRepeats;
        this.settleRepeats = Math.max(1, settleRepeats);
        firePropertyChange("settleRepeats", old, this.settleRepeats);
    }

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

    /**
     * Whether the camera's settling or its frame latency was measured. Its fixed wait is then
     * held against the measurement, and the adaptive settling Issues and Solutions suggests for a
     * fixed wait would be a second fix for the same thing, pulling the other way.
     */
    public boolean hasMeasuredSettling(Camera camera) {
        if (lastResults == null) {
            return false;
        }
        for (Settling settling : lastResults.getSettling()) {
            if (settling.getCameraId().equals(camera.getId())) {
                return true;
            }
        }
        return lastResults.getCameraLatency(camera.getId()) != null;
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
        long now = System.currentTimeMillis();
        results.setRun(group, now, report.getDirectory().getAbsolutePath(),
                groupStartedMillis > 0 ? now - groupStartedMillis : 0);
        setLastResults(results);
        // The conclusions live in machine.xml; a measurement is worth nothing if a crash loses it.
        if (configuration != null) {
            configuration.setDirty(true);
        }
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
        recoveries = 0;
        lastFiducialOffsetMm = 0;
        runStartedSeconds = NanosecondTime.getRuntimeSeconds();
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
            if (recoveries > 0) {
                report.finding(Severity.Problem, "The machine had to be homed %d time(s) during "
                        + "this run to find the fiducial again. It loses its position under the "
                        + "moves these tests make; every result here that spans such a point is "
                        + "suspect, and a job would be losing placements the same way.", recoveries);
            }
            // A run that leaves the machine off its origin leaves the crosshair beside the
            // fiducial and the next job placing everything by that much off. Home it.
            if (!aborting && machine.isEnabled() && lastFiducialOffsetMm > 0.1) {
                try {
                    log("The fiducial was last seen %.3f mm from its position: homing so that the "
                            + "machine is left usable", lastFiducialOffsetMm);
                    report.finding(Severity.Problem, "The run ended with the machine %.3f mm off "
                            + "its origin - the position it had lost during the tests. It was "
                            + "homed at the end so that it is left usable; a job run without that "
                            + "homing would have placed everything %.3f mm off.",
                            lastFiducialOffsetMm, lastFiducialOffsetMm);
                    machine.home();
                    lastFiducialOffsetMm = 0;
                }
                catch (Exception e) {
                    Logger.warn(e, "Machine diagnostics: homing at the end of the run");
                }
            }
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

    private long groupStartedMillis;

    // ---- compensation ------------------------------------------------------------------------

    /**
     * How close to a true millimetre a verification reading has to come back for the
     * compensation to stand, while the scatter of the readings is not known well enough to say:
     * 0.1 %. One reading of a belt machine's frame differs from the next by about 0.05 % - the
     * eight quiet readings of the sixth session: sd 0.046 % in X, 0.048 % in Y - and three
     * readings that happen to agree do not make that smaller. The seventh session's basis
     * scattered by 0.019 % over three readings, the line was drawn at 0.05 %, and the
     * verification came back 0.092 % off, twice, from a compensation that had taken the frame
     * from -0.164 % to -0.092 % and was right to within the measurement.
     */
    private static final double COMPENSATION_LINE = 0.001;
    /** Once the scatter is known the line is 2.5 times it, and no tighter than this. */
    private static final double COMPENSATION_LINE_FLOOR = 0.0005;
    /** And no looser than this; a frame that scatters more is not one to compensate. */
    private static final double COMPENSATION_LINE_CAP = 0.003;
    /** How many readings it takes to know the scatter. */
    private static final int COMPENSATION_SCATTER_KNOWN_FROM = 6;
    /**
     * The same for the squareness. One reading's shear is good to about 0.05 degrees: 0.02 mm of
     * positioning noise over the board's 25 mm of Y between its inner and outer fiducials.
     */
    private static final double COMPENSATION_SQUARENESS_LINE_DEGREES = 0.1;
    private static final double COMPENSATION_SQUARENESS_LINE_CAP_DEGREES = 0.3;
    /** A frame residual above this is drift, not geometry, and nothing to compensate from. */
    private static final double COMPENSATION_MAX_RESIDUAL_MM = 0.05;

    /**
     * What a compensation would be made from: the median of the recent board readings under the
     * current compensation, and how much they scatter. One reading of this machine differs from
     * the next by 0.05 % in scale as the machine drifts between the fiducials; the median of
     * several is what the frame is, and their scatter is how closely a verification can be
     * expected to come back to a true millimetre.
     */
    public static final class CompensationBasis {
        public final int readings;
        public final double scaleX, scaleY, shearDegrees;
        public final double sdScaleX, sdScaleY, sdShearDegrees;
        public final double residualMm;
        public final MachineDiagnosticsResults.Datum latest;

        CompensationBasis(List<MachineDiagnosticsResults.Datum> usable) {
            readings = usable.size();
            latest = usable.get(usable.size() - 1);
            List<Double> xs = new ArrayList<>(), ys = new ArrayList<>(), shears = new ArrayList<>();
            double residual = 0;
            for (MachineDiagnosticsResults.Datum d : usable) {
                xs.add(d.getScaleX());
                ys.add(d.getScaleY());
                shears.add(d.getShearDegrees());
                residual = Math.max(residual, d.getRmsResidualMm());
            }
            scaleX = MachineDiagnosticsMath.median(xs);
            scaleY = MachineDiagnosticsMath.median(ys);
            shearDegrees = MachineDiagnosticsMath.median(shears);
            sdScaleX = readings > 1 ? MachineDiagnosticsMath.stats(xs).stdDev : Double.NaN;
            sdScaleY = readings > 1 ? MachineDiagnosticsMath.stats(ys).stdDev : Double.NaN;
            sdShearDegrees = readings > 1 ? MachineDiagnosticsMath.stats(shears).stdDev : Double.NaN;
            residualMm = residual;
        }

        /** As a Datum, for the transform to be built from. */
        MachineDiagnosticsResults.Datum asDatum() {
            MachineDiagnosticsResults.Datum d = new MachineDiagnosticsResults.Datum(latest.getBoard(),
                    latest.getHeadId(), scaleX, scaleY, shearDegrees, latest.getRotationDegrees(),
                    latest.isMirrored(), residualMm, latest.getPoints());
            return d;
        }

        /**
         * How far from a true millimetre a verification may read and still count as converged:
         * 2.5 times the scatter of the readings the basis came from once there are enough of
         * them to know it, and no tighter than 0.05 % then; 0.1 % until there are; never looser
         * than 0.3 %.
         */
        double tolerance(double sd) {
            double floor = readings >= COMPENSATION_SCATTER_KNOWN_FROM ? COMPENSATION_LINE_FLOOR
                    : COMPENSATION_LINE;
            if (Double.isNaN(sd)) {
                return floor;
            }
            return Math.max(floor, Math.min(COMPENSATION_LINE_CAP, 2.5 * sd));
        }

        /** The same line for the squareness, in degrees. */
        double shearTolerance() {
            if (Double.isNaN(sdShearDegrees)) {
                return COMPENSATION_SQUARENESS_LINE_DEGREES;
            }
            return Math.max(COMPENSATION_SQUARENESS_LINE_DEGREES,
                    Math.min(COMPENSATION_SQUARENESS_LINE_CAP_DEGREES, 2.5 * sdShearDegrees));
        }

        /** The squareness is only worth compensating when the readings agree on it. */
        public boolean squarenessIsSettled() {
            return readings >= 2 && !Double.isNaN(sdShearDegrees) && sdShearDegrees < 0.03
                    && Math.abs(shearDegrees) > 0.1;
        }
    }

    /** The basis a compensation would be made from now, or null when nothing usable is recorded. */
    public CompensationBasis getCompensationBasis() {
        if (lastResults == null) {
            return null;
        }
        List<MachineDiagnosticsResults.Datum> usable =
                lastResults.getUsableDatumHistory(COMPENSATION_MAX_RESIDUAL_MM);
        if (usable.isEmpty() && lastResults.getDatum() != null
                && lastResults.getDatum().getRmsResidualMm() <= COMPENSATION_MAX_RESIDUAL_MM) {
            // A machine.xml from before the history existed: the one reading it holds.
            usable = List.of(lastResults.getDatum());
        }
        return usable.isEmpty() ? null : new CompensationBasis(usable);
    }

    /**
     * Whether a compensation stands, from what the board read after it. On each axis the frame
     * has to come back within the line, and closer to a true millimetre than the basis was -
     * unless the basis was within the line already - so that a compensation which changed
     * nothing is not kept along with the coordinates it carried across. The squareness is judged
     * the same way when it was part of the compensation. What is left within the line is not a
     * reason to undo: a verification is one reading, or the mean of two, of a machine whose
     * readings scatter by about the line, and the next compensation, which composes onto this
     * one, takes the remainder out once a few more readings have said what it is.
     */
    public static final class CompensationVerdict {
        public final boolean kept;
        public final boolean xWithin, xBetter, yWithin, yBetter, shearWithin, shearBetter;

        private CompensationVerdict(boolean xWithin, boolean xBetter, boolean yWithin,
                boolean yBetter, boolean shearWithin, boolean shearBetter) {
            this.xWithin = xWithin;
            this.xBetter = xBetter;
            this.yWithin = yWithin;
            this.yBetter = yBetter;
            this.shearWithin = shearWithin;
            this.shearBetter = shearBetter;
            this.kept = xWithin && xBetter && yWithin && yBetter && shearWithin && shearBetter;
        }

        /**
         * @param beforeX    The basis, as scale minus one; likewise Y. Shear in degrees.
         * @param afterX     What the board read after the change, the same way.
         * @param lineX      How far from true the reading may be and still stand; likewise Y
         *                   and the shear.
         * @param squareness Whether the shear was part of the compensation.
         */
        public static CompensationVerdict judge(double beforeX, double beforeY, double beforeShear,
                double afterX, double afterY, double afterShear, double lineX, double lineY,
                double lineShear, boolean squareness) {
            return new CompensationVerdict(
                    Math.abs(afterX) <= lineX,
                    Math.abs(afterX) < Math.abs(beforeX) || Math.abs(beforeX) <= lineX,
                    Math.abs(afterY) <= lineY,
                    Math.abs(afterY) < Math.abs(beforeY) || Math.abs(beforeY) <= lineY,
                    !squareness || Math.abs(afterShear) <= lineShear,
                    !squareness || Math.abs(afterShear) < Math.abs(beforeShear)
                            || Math.abs(beforeShear) <= lineShear);
        }

        /** What did not hold, in English, for the log and the report; empty when kept. */
        public String whatFailed() {
            List<String> failed = new ArrayList<>();
            if (!xWithin) {
                failed.add("X outside the line");
            }
            if (!xBetter) {
                failed.add("X no closer to true than before");
            }
            if (!yWithin) {
                failed.add("Y outside the line");
            }
            if (!yBetter) {
                failed.add("Y no closer to true than before");
            }
            if (!shearWithin) {
                failed.add("squareness outside the line");
            }
            if (!shearBetter) {
                failed.add("squareness no closer to square than before");
            }
            return String.join(", ", failed);
        }
    }

    /** What applying the compensation did, for the page to show. */
    public static final class CompensationOutcome {
        public final boolean kept;
        public final MachineCompensation compensation;
        /** The basis: the median of the readings the compensation was made from. */
        public final MachineDiagnosticsResults.Datum before;
        /** What the board read after the change: one reading, or the mean of two. */
        public final MachineDiagnosticsResults.Datum after;
        public final int basisReadings;
        public final int verificationReadings;
        public final double lineX, lineY, lineShearDegrees;
        public final boolean squareness;
        public final CompensationVerdict verdict;
        public final File backup;
        public final List<String> changes;
        public final List<String> skipped;
        public final String message;
        /** What takes a kept compensation out again; null once taken out, or when not kept. */
        CompensationUndo undo;

        /** Whether {@link MachineDiagnostics#undoCompensation} can take it out again. */
        public boolean canBeUndone() {
            return undo != null;
        }

        CompensationOutcome(boolean kept, MachineCompensation compensation,
                MachineDiagnosticsResults.Datum before, MachineDiagnosticsResults.Datum after,
                int basisReadings, int verificationReadings, double lineX, double lineY,
                double lineShearDegrees, boolean squareness, CompensationVerdict verdict,
                File backup, List<String> changes, List<String> skipped, String message) {
            this.kept = kept;
            this.compensation = compensation;
            this.before = before;
            this.after = after;
            this.basisReadings = basisReadings;
            this.verificationReadings = verificationReadings;
            this.lineX = lineX;
            this.lineY = lineY;
            this.lineShearDegrees = lineShearDegrees;
            this.squareness = squareness;
            this.verdict = verdict;
            this.backup = backup;
            this.changes = changes;
            this.skipped = skipped;
            this.message = message;
        }
    }

    /** The mean of several readings of the board, as one reading; the residual is the worst. */
    static MachineDiagnosticsResults.Datum meanOf(List<MachineDiagnosticsResults.Datum> readings) {
        MachineDiagnosticsResults.Datum latest = readings.get(readings.size() - 1);
        double x = 0, y = 0, shear = 0, residual = 0;
        for (MachineDiagnosticsResults.Datum d : readings) {
            x += d.getScaleX();
            y += d.getScaleY();
            shear += d.getShearDegrees();
            residual = Math.max(residual, d.getRmsResidualMm());
        }
        int n = readings.size();
        return new MachineDiagnosticsResults.Datum(latest.getBoard(), latest.getHeadId(), x / n,
                y / n, shear / n, latest.getRotationDegrees(), latest.isMirrored(), residual,
                latest.getPoints());
    }

    /**
     * Put what the datum board measured into the machine as a compensation, and prove it.
     * <p>
     * machine.xml is copied aside first. The transform axes are added and every taught coordinate
     * carried across, and the camera's axes are checked to travel the new raw distance for a
     * true one; then the datum board group runs again on the compensated machine, once, and a
     * second time if the first reading does not settle it. If the frame comes back within the
     * line the readings' scatter sets - see {@link CompensationBasis#tolerance} - and closer to
     * a true millimetre than it was, the configuration is saved; if not, everything is put back
     * and nothing is saved, and the report says what came back instead. Must run on the machine
     * task thread, as the verification moves the machine.
     *
     * @param job               The open job, whose board positions are carried across; null for none.
     * @param includeSquareness Whether to lean the Y axis back by the shear measured.
     */
    public CompensationOutcome applyCompensation(ReferenceMachine machine, Job job,
            boolean includeSquareness) throws Exception {
        if (lastResults == null || lastResults.getDatum() == null) {
            throw new Exception("Run the datum board group first; there is nothing measured to compensate.");
        }
        if (running) {
            throw new Exception("Diagnostics are already running.");
        }
        CompensationBasis basis = getCompensationBasis();
        if (basis == null) {
            throw new Exception(String.format("No board measurement under the current compensation "
                    + "fits a frame to better than %.3f mm rms: the machine drifted between the "
                    + "fiducials, and a scale fitted through that is not one to compensate from. "
                    + "Run the datum board group again, at a lower positioning speed if need be.",
                    COMPENSATION_MAX_RESIDUAL_MM));
        }
        MachineDiagnosticsResults.Datum before = basis.asDatum();
        log("Compensation basis: %d reading(s), scale X %+.3f%% (sd %.3f%%), Y %+.3f%% (sd %.3f%%), "
                + "shear %+.3f deg (sd %.3f)", basis.readings, (basis.scaleX - 1) * 100,
                basis.sdScaleX * 100, (basis.scaleY - 1) * 100, basis.sdScaleY * 100,
                basis.shearDegrees, basis.sdShearDegrees);
        ReferenceHead head = requireHead(machine);
        ReferenceCamera camera = requireDownLookingCamera(head);
        Location anchor = requireFiducial(head);
        ReferenceControllerAxis rawX = findControllerAxis(camera, Axis.Type.X);
        ReferenceControllerAxis rawY = findControllerAxis(camera, Axis.Type.Y);
        if (rawX == null || rawY == null) {
            throw new Exception("The camera has no controller X and Y axes to compensate.");
        }
        MachineCompensation compensation = MachineCompensation.of(before, anchor, includeSquareness);

        File configurationDirectory = getConfiguration().getConfigurationDirectory();
        File machineXml = new File(configurationDirectory, "machine.xml");
        File backup = new File(configurationDirectory, "machine.xml.before-compensation-"
                + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date()));
        if (machineXml.exists()) {
            java.nio.file.Files.copy(machineXml.toPath(), backup.toPath());
        }
        log("Compensation: %s", compensation);
        log("machine.xml copied to %s", backup.getName());

        // The raw travel the camera's axes make for 100 true millimetres, before and after. The
        // coordinates are carried across on the assumption that the transform took effect on
        // the axes the camera moves in; that is proved in software before the board is asked.
        double[] travelBefore = MachineCompensation.rawTravelPer100(camera, anchor, rawX, rawY);
        MachineCompensation.Applied applied = compensation.apply(machine, rawX, rawY, job);
        log("%d coordinates carried across, %d left alone", applied.changes.size(),
                applied.skipped.size());
        List<String> changes = MachineCompensation.describe(applied);
        double[] travelAfter = MachineCompensation.rawTravelPer100(camera, anchor, rawX, rawY);
        String notInEffect = compensation.checkTravel(travelBefore, travelAfter);
        if (notInEffect != null) {
            applied.undo(machine);
            throw new Exception("The compensation did not take effect on the camera's axes ("
                    + notInEffect + "); it was taken out again and nothing was saved.");
        }
        log("Camera travel per 100 true mm: X %.6f -> %.6f raw, Y %.6f -> %.6f raw",
                travelBefore[0], travelAfter[0], travelBefore[1], travelAfter[1]);

        // Prove it on the board. The verification readings are made under the new compensation,
        // so they are tagged with the next generation; if the compensation goes, so do they.
        int generation = lastResults.getCompensationGeneration();
        lastResults.setCompensationGeneration(generation + 1);
        MachineDiagnosticsResults.Datum latestBefore = lastResults.getDatum();
        double lineX = basis.tolerance(basis.sdScaleX);
        double lineY = basis.tolerance(basis.sdScaleY);
        double lineShear = basis.shearTolerance();
        List<MachineDiagnosticsResults.Datum> verifications = new ArrayList<>();
        MachineDiagnosticsResults.Datum after = null;
        CompensationVerdict verdict = null;
        try {
            // One reading; a second when the first does not settle it. One reading of this
            // machine scatters by about the line; the mean of two by less.
            for (int reading = 1; reading <= 2; reading++) {
                run(machine, EnumSet.of(TestGroup.DatumBoard));
                MachineDiagnosticsResults.Datum read = lastResults == null ? null : lastResults.getDatum();
                if (read == null || read == latestBefore) {
                    break;
                }
                verifications.add(read);
                after = verifications.size() == 1 ? read : meanOf(verifications);
                verdict = CompensationVerdict.judge(before.getScaleX() - 1, before.getScaleY() - 1,
                        before.getShearDegrees(), after.getScaleX() - 1, after.getScaleY() - 1,
                        after.getShearDegrees(), lineX, lineY, lineShear, includeSquareness);
                log("Verification reading %d: X %+.3f%%, Y %+.3f%%, shear %+.3f deg; %s", reading,
                        (read.getScaleX() - 1) * 100, (read.getScaleY() - 1) * 100,
                        read.getShearDegrees(), verdict.kept ? "converged" : verdict.whatFailed());
                if (verdict.kept) {
                    break;
                }
            }
        }
        catch (Exception e) {
            applied.undo(machine);
            lastResults.setCompensationGeneration(generation);
            lastResults.removeDatumHistory(generation + 1);
            lastResults.setDatum(latestBefore);
            setLastResults(lastResults);
            throw new Exception("The verification run failed (" + e.getMessage()
                    + "); the compensation was taken out again and nothing was saved.", e);
        }
        boolean converged = verdict != null && verdict.kept;
        String message;
        if (converged) {
            getConfiguration().save();
            message = String.format("Compensated. Against the board the machine millimetre was "
                    + "%+.3f%% / %+.3f%% (X / Y, the median of %d readings) and reads %+.3f%% / "
                    + "%+.3f%% now, from %d verification reading(s), against a line of %.3f%% / "
                    + "%.3f%%. What is left is within what one reading of this machine scatters "
                    + "by; to take it out as well, read the board two or three more times and "
                    + "compensate again, which composes onto this one. %d coordinates were "
                    + "carried across; machine.xml before the change is %s.",
                    (before.getScaleX() - 1) * 100, (before.getScaleY() - 1) * 100, basis.readings,
                    (after.getScaleX() - 1) * 100, (after.getScaleY() - 1) * 100,
                    verifications.size(), lineX * 100, lineY * 100, applied.changes.size(),
                    backup.getName());
            log(message);
        }
        else {
            applied.undo(machine);
            // The board was read on the compensated machine; with the compensation gone, those
            // readings describe nothing. The ones it was made from stand again.
            lastResults.setCompensationGeneration(generation);
            lastResults.removeDatumHistory(generation + 1);
            lastResults.setDatum(latestBefore);
            setLastResults(lastResults);
            message = after == null
                    ? "The board could not be measured after the change; the compensation was taken out again."
                    : String.format("After the change the board read %+.3f%% / %+.3f%% (X / Y) "
                            + "and %+.3f degrees, from %d verification reading(s), against a basis "
                            + "of %+.3f%% / %+.3f%% and %+.3f degrees from %d reading(s). To stand, "
                            + "the frame had to come back within %.3f%% / %.3f%% (%.2f degrees) of "
                            + "true and closer to it than the basis: %s. The compensation was taken "
                            + "out again and nothing was saved. A frame that reads differently from "
                            + "one run to the next is a machine that moves between the fiducials; "
                            + "the datum board report's anchor drift says how much.",
                            (after.getScaleX() - 1) * 100, (after.getScaleY() - 1) * 100,
                            after.getShearDegrees(), verifications.size(),
                            (before.getScaleX() - 1) * 100, (before.getScaleY() - 1) * 100,
                            before.getShearDegrees(), basis.readings, lineX * 100, lineY * 100,
                            lineShear, verdict == null ? "no reading" : verdict.whatFailed());
            log(message);
        }
        // What was done, beside the last verification report.
        if (lastReportDirectory != null) {
            try {
                List<String> lines = new ArrayList<>();
                lines.add(converged ? "COMPENSATION KEPT" : "COMPENSATION UNDONE");
                lines.add(compensation.toString());
                lines.add(String.format("Basis: %d reading(s); scale X %+.4f%% sd %.4f%%, Y %+.4f%% sd "
                        + "%.4f%%, shear %+.4f deg sd %.4f; line %.4f%% / %.4f%% / %.3f deg", basis.readings,
                        (basis.scaleX - 1) * 100, basis.sdScaleX * 100, (basis.scaleY - 1) * 100,
                        basis.sdScaleY * 100, basis.shearDegrees, basis.sdShearDegrees,
                        lineX * 100, lineY * 100, lineShear));
                lines.add(String.format("Camera travel per 100 true mm: X %.6f -> %.6f raw, Y %.6f -> %.6f raw",
                        travelBefore[0], travelAfter[0], travelBefore[1], travelAfter[1]));
                for (int i = 0; i < verifications.size(); i++) {
                    MachineDiagnosticsResults.Datum v = verifications.get(i);
                    lines.add(String.format("Verification %d: scale X %+.4f%%, Y %+.4f%%, shear %+.4f deg, "
                            + "residual %.4f mm over %d points", i + 1, (v.getScaleX() - 1) * 100,
                            (v.getScaleY() - 1) * 100, v.getShearDegrees(), v.getRmsResidualMm(),
                            v.getPoints()));
                }
                lines.add(message);
                lines.add("");
                lines.add("Coordinates carried across:");
                lines.addAll(changes);
                lines.add("");
                lines.add("Location properties left alone (check them by hand):");
                lines.addAll(applied.skipped);
                lines.add("");
                lines.add("Location properties that were never taught (all zero) and stay so:");
                lines.addAll(applied.unset);
                java.nio.file.Files.write(new File(lastReportDirectory, "compensation.txt").toPath(), lines);
            }
            catch (Exception e) {
                Logger.warn(e, "Machine diagnostics: writing compensation.txt");
            }
        }
        CompensationOutcome outcome = new CompensationOutcome(converged, compensation, before, after,
                basis.readings, verifications.size(), lineX, lineY, lineShear, includeSquareness, verdict,
                backup, changes, applied.skipped, message);
        if (converged) {
            outcome.undo = new CompensationUndo(applied, generation, latestBefore);
        }
        return outcome;
    }

    /** What taking a kept compensation out again puts back. */
    static final class CompensationUndo {
        final MachineCompensation.Applied applied;
        final int generation;
        final MachineDiagnosticsResults.Datum latestBefore;

        CompensationUndo(MachineCompensation.Applied applied, int generation,
                MachineDiagnosticsResults.Datum latestBefore) {
            this.applied = applied;
            this.generation = generation;
            this.latestBefore = latestBefore;
        }
    }

    /**
     * Takes a compensation that was kept out again, as one that failed its verification is: the
     * transform axes and every coordinate carried across go back, and the board readings made
     * under it go with it. The configuration is saved. Must run on the machine task thread.
     */
    public void undoCompensation(ReferenceMachine machine, CompensationOutcome outcome) throws Exception {
        if (outcome == null || outcome.undo == null) {
            throw new Exception("There is no kept compensation to take out.");
        }
        CompensationUndo undo = outcome.undo;
        outcome.undo = null;
        undo.applied.undo(machine);
        if (lastResults != null) {
            lastResults.setCompensationGeneration(undo.generation);
            lastResults.removeDatumHistory(undo.generation + 1);
            lastResults.setDatum(undo.latestBefore);
            setLastResults(lastResults);
        }
        getConfiguration().save();
        log("Compensation taken out again at the user's request.");
    }

    private void runGroup(ReferenceMachine machine, TestGroup group,
            MachineDiagnosticsReport report) throws Exception {
        log("--- %s ---", group);
        groupStartedMillis = System.currentTimeMillis();
        switch (group) {
            case Firmware:
                testFirmware(machine, report);
                break;
            case VisionNoise:
                testVisionNoise(machine, report);
                break;
            case CameraLatency:
                testCameraLatency(machine, report);
                break;
            case Kinematics:
                testKinematics(machine, report);
                break;
            case LostSteps:
                testLostSteps(machine, report);
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
            case ZFocus:
                testZFocus(machine, report);
                break;
            case DatumBoard:
                testDatumBoard(machine, report);
                break;
            case HysteresisMap:
                testHysteresisMap(machine, report);
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
        ReferenceCamera camera = requireDownLookingCamera(head);
        Nozzle nozzle = head.getNozzles().isEmpty() ? null : head.getNozzles().get(0);
        // The timing runs are the fastest, longest moves in these tests, and the first real run
        // lost the fiducial during them. Where it was before and after says what they cost.
        Location fiducial = head.getCalibrationPrimaryFiducialLocation();
        Length diameter = head.getCalibrationPrimaryFiducialDiameter();
        ReferenceControllerAxis approachAxis = findControllerAxis(camera, Axis.Type.X);
        Detection before = null;
        if (fiducial != null && diameter != null && diameter.getValue() > 0) {
            before = acquire(machine, camera, approachAxis, fiducial, diameter, "Kinematics start", report);
        }

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
        if (before != null) {
            Detection after = acquire(machine, camera, approachAxis, fiducial, diameter,
                    "Kinematics end", report);
            Location drift = after.location.convertToUnits(LengthUnit.Millimeters)
                    .subtract(before.location.convertToUnits(LengthUnit.Millimeters));
            double off = Math.hypot(drift.getX(), drift.getY());
            report.line("  After the X and Y timing runs the fiducial had moved %.4f mm "
                    + "(%+.4f, %+.4f) in the camera.", off, drift.getX(), drift.getY());
            if (off > LOST_STEPS_TOLERANCE_MM * 2) {
                report.finding(Severity.Problem, "The timing runs alone moved the fiducial %.3f mm "
                        + "(%+.3f in X, %+.3f in Y): the axes lose steps at the speed they are "
                        + "planned with. The lost steps group says at which speed.", off,
                        drift.getX(), drift.getY());
            }
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
        double reach = axis.getMotionLimit(2) > 0
                ? axis.getMotionLimit(1) * axis.getMotionLimit(1) / axis.getMotionLimit(2) : 0;
        boolean velocityReachable = fitDistances.get(fitDistances.size() - 1) >= reach;
        fits.add(new Motion(axis.getId(), fit.acceleration,
                velocityReachable ? fit.velocity : Double.NaN, fit.overhead, unit));
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
        // A velocity is only reached if some distance is long enough to reach it: d > v^2/a.
        double longest = fitDistances.get(fitDistances.size() - 1);
        double needed = configuredAcceleration > 0
                ? configuredFeedRate * configuredFeedRate / configuredAcceleration : 0;
        if (longest >= needed) {
            reportLimitShortfall(report, axis, "velocity", configuredFeedRate, fit.velocity, unit + "/s");
        }
        else {
            report.line("  the longest distance tested, %.1f %s, is too short to reach the "
                    + "configured velocity (%.1f %s needed), so the fitted velocity is not held "
                    + "against it.", longest, unit, needed, unit);
        }
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

    // Detection through several frames: what every position in this class is read with.

    /** One position read from the camera: the median of several frames, and how they scattered. */
    static final class Detection {
        final Location location;
        /** Scatter of the frames around their median, radial, in pixels and in millimetres. */
        final double sdPixels;
        final double sdMm;
        final int frames;
        /** The detector's score on the first frame, which is what it would have reported alone. */
        final double score;

        Detection(Location location, double sdPixels, double sdMm, int frames, double score) {
            this.location = location;
            this.sdPixels = sdPixels;
            this.sdMm = sdMm;
            this.frames = frames;
            this.score = score;
        }
    }

    /**
     * Locate the fiducial in {@code frames} frames of the standing camera and take the median.
     * <p>
     * The first frame is captured the way any vision operation captures - light on, settle,
     * capture - and the rest are raw captures with the light still on, since the machine has
     * not moved in between. A camera can hand out the same frame twice; a frame whose detection
     * lands on exactly the same sub-pixel position as the previous one is not counted.
     */
    private Detection detect(ReferenceMachine machine, ReferenceCamera camera, Location expected,
            Length diameter, String diagnostics, int frames) throws Exception {
        return detect(machine, camera, expected, diameter, diagnostics, frames, 0.0);
    }

    /**
     * @param extraSearch How much further than usual to look, as a fraction of the image; the
     *                    board's orientation is guessed before it is measured.
     */
    private Detection detect(ReferenceMachine machine, ReferenceCamera camera, Location expected,
            Length diameter, String diagnostics, int frames, double extraSearch) throws Exception {
        VisionSolutions vision = machine.getVisionSolutions();
        Circle feature = vision.getExpectedOffsetsAndDiameter(camera, camera, expected, diameter,
                false);
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        double firstScore = Double.NaN;
        camera.actuateLightBeforeCapture();
        try {
            BufferedImage frame = camera.lightSettleAndCapture();
            long seen = fingerprint(frame);
            int attempts = 0;
            while (xs.size() < Math.max(1, frames) && attempts < Math.max(1, frames) * 3) {
                checkAborted();
                attempts++;
                ScoreRange score = new ScoreRange();
                Circle circle;
                try {
                    circle = vision.getSubjectPixelLocation(camera, camera, feature, extraSearch,
                            xs.isEmpty() ? diagnostics : null, score, false, frame);
                }
                catch (Exception e) {
                    if (xs.isEmpty() && attempts >= 3) {
                        throw e;
                    }
                    frame = freshFrame(camera, seen);
                    seen = fingerprint(frame);
                    continue;
                }
                if (Double.isNaN(firstScore)) {
                    firstScore = score.finalScore;
                }
                xs.add(circle.x);
                ys.add(circle.y);
                if (xs.size() < frames) {
                    frame = freshFrame(camera, seen);
                    seen = fingerprint(frame);
                }
            }
        }
        finally {
            camera.actuateLightAfterCapture();
        }
        if (xs.isEmpty()) {
            throw new Exception("Subject not found.");
        }
        double medianX = MachineDiagnosticsMath.median(xs);
        double medianY = MachineDiagnosticsMath.median(ys);
        double sumSquares = 0;
        for (int i = 0; i < xs.size(); i++) {
            sumSquares += Math.pow(xs.get(i) - medianX, 2) + Math.pow(ys.get(i) - medianY, 2);
        }
        double sdPixels = xs.size() > 1 ? Math.sqrt(sumSquares / (xs.size() - 1)) : 0;
        Location upp = camera.getUnitsPerPixelAtZ().convertToUnits(LengthUnit.Millimeters);
        double sdMm = sdPixels * Math.hypot(upp.getX(), upp.getY()) / Math.sqrt(2);
        Location location = VisionUtils.getPixelLocation(camera, camera, medianX, medianY)
                .convertToUnits(expected.getUnits());
        return new Detection(location, sdPixels, sdMm, xs.size(), firstScore);
    }

    private Detection detect(ReferenceMachine machine, ReferenceCamera camera, Location expected,
            Length diameter, String diagnostics) throws Exception {
        return detect(machine, camera, expected, diameter, diagnostics, framesPerPoint);
    }

    /**
     * A cheap signature of a frame: a grid of its pixels. Frames are deduplicated by this rather
     * than by where the detector put the fiducial, because a sub-pixel detector quantises - at a
     * super sampling of 8, most frames of a standing fiducial land on exactly the same eighth of
     * a pixel, and the first real run kept 5 of 90 frames for that reason.
     */
    private static long fingerprint(BufferedImage image) {
        if (image == null) {
            return 0;
        }
        long hash = 1125899906842597L;
        int stepX = Math.max(1, image.getWidth() / 16);
        int stepY = Math.max(1, image.getHeight() / 16);
        for (int y = stepY / 2; y < image.getHeight(); y += stepY) {
            for (int x = stepX / 2; x < image.getWidth(); x += stepX) {
                hash = 31 * hash + image.getRGB(x, y);
            }
        }
        return hash;
    }

    /** A fresh frame: capture until the image changes, within reason. */
    private BufferedImage freshFrame(ReferenceCamera camera, long previous) throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            BufferedImage image = camera.capture();
            if (image != null && fingerprint(image) != previous) {
                return image;
            }
            Thread.sleep(10);
        }
        throw new Exception("The camera stopped delivering new frames.");
    }

    /** Seconds since the run started, for the time column of every CSV. */
    private double elapsed() {
        return NanosecondTime.getRuntimeSeconds() - runStartedSeconds;
    }

    private double runStartedSeconds;

    /** The columns every measurement row ends with: when, and how sure the camera was. */
    private static final String[] DETECTION_COLUMNS = { "t_s", "frames", "frame_sd_px", "score" };

    private static Object[] detectionColumns(double t, Detection detection) {
        return new Object[] { t, detection.frames, detection.sdPixels, detection.score };
    }

    private static String[] columns(String[] own) {
        String[] all = new String[own.length + DETECTION_COLUMNS.length];
        System.arraycopy(own, 0, all, 0, own.length);
        System.arraycopy(DETECTION_COLUMNS, 0, all, own.length, DETECTION_COLUMNS.length);
        return all;
    }

    private static Object[] row(Object[] own, double t, Detection detection) {
        Object[] tail = detectionColumns(t, detection);
        Object[] all = new Object[own.length + tail.length];
        System.arraycopy(own, 0, all, 0, own.length);
        System.arraycopy(tail, 0, all, own.length, tail.length);
        return all;
    }

    /** Detect, or null where the fiducial is simply not there; aborts still propagate. */
    private Detection tryDetect(ReferenceMachine machine, ReferenceCamera camera, Location expected,
            Length diameter, String diagnostics, double extraSearch) throws Exception {
        try {
            return detect(machine, camera, expected, diameter, diagnostics, framesPerPoint, extraSearch);
        }
        catch (AbortedException e) {
            throw e;
        }
        catch (Exception e) {
            Logger.trace(e, "Machine diagnostics: {} not found", diagnostics);
            return null;
        }
    }

    /** How many times the machine had to be homed to find the fiducial again, this run. */
    private int recoveries;

    /** How far from its configured position the fiducial was last found, in millimetres. */
    private double lastFiducialOffsetMm;

    /** A move to make before looking: the approach whose result is being measured. */
    private interface Approach {
        void move() throws Exception;
    }

    /**
     * Approach and look, and if the fiducial is not there, get it back and do the approach
     * again. The second real run lost the fiducial in the middle of the repeatability cells -
     * ten approaches of 50 mm at full speed are half a metre of travel on a machine that loses
     * a millimetre a metre - and the whole group died on "Subject not found". The loss is the
     * finding; the measurement goes on after it, from the same approach.
     *
     * @return The detection, or null if the fiducial could not be found even after recovery,
     *         in which case the caller records a miss and moves on.
     */
    private Detection measure(ReferenceMachine machine, ReferenceCamera camera,
            ReferenceControllerAxis approachAxis, Location fiducial, Length diameter, String label,
            MachineDiagnosticsReport report, Approach approach) throws Exception {
        approach.move();
        Detection seen = tryDetect(machine, camera, fiducial, diameter, label, 0.0);
        if (seen == null) {
            seen = tryDetect(machine, camera, fiducial, diameter, label, 0.35);
        }
        if (seen != null) {
            return seen;
        }
        log("%s: fiducial lost; getting it back before going on", label);
        report.line("  %s: the fiducial was lost here and had to be found again.", label);
        acquire(machine, camera, approachAxis, fiducial, diameter, label + " (recovery)", report);
        approach.move();
        seen = tryDetect(machine, camera, fiducial, diameter, label, 0.0);
        if (seen == null) {
            seen = tryDetect(machine, camera, fiducial, diameter, label, 0.35);
        }
        return seen;
    }

    /**
     * Go to the fiducial and find it, whatever the machine has done since it was last seen.
     * <p>
     * Approach from the same side as always and look. Not there: look over a wider patch of the
     * image. Still not there: the machine has lost its position, so home it, approach again and
     * look wide. That is the recovery the first real run showed was needed - a machine that
     * loses steps at full speed had put the fiducial out of the camera's view halfway through
     * the tests, and everything after that failed with "Subject not found". The drift found is
     * logged and returned, since it is itself a measurement.
     */
    private Detection acquire(ReferenceMachine machine, ReferenceCamera camera,
            ReferenceControllerAxis approachAxis, Location fiducial, Length diameter, String label,
            MachineDiagnosticsReport report) throws Exception {
        for (int pass = 0; pass < 2; pass++) {
            checkAborted();
            if (approachAxis != null) {
                approachFromCorner(camera, fiducial, measureSpeedFactor);
            }
            else {
                MovableUtils.moveToLocationAtSafeZ(camera, fiducial, measureSpeedFactor);
            }
            Detection seen = tryDetect(machine, camera, fiducial, diameter, label, 0.0);
            if (seen == null) {
                seen = tryDetect(machine, camera, fiducial, diameter, label, 0.35);
            }
            if (seen != null) {
                Location drift = seen.location.convertToUnits(LengthUnit.Millimeters)
                        .subtract(fiducial.convertToUnits(LengthUnit.Millimeters));
                double off = Math.hypot(drift.getX(), drift.getY());
                lastFiducialOffsetMm = off;
                if (off > 0.05) {
                    log("%s: fiducial found %.3f mm from where it is configured (%+.3f, %+.3f)",
                            label, off, drift.getX(), drift.getY());
                    if (report != null) {
                        report.line("  %s: the fiducial was %.3f mm (%+.3f, %+.3f) from its "
                                + "configured position%s.", label, off, drift.getX(), drift.getY(),
                                pass > 0 ? " after homing" : "");
                    }
                }
                return seen;
            }
            if (pass == 0) {
                recoveries++;
                log("%s: fiducial lost; homing the machine to find it again", label);
                if (report != null) {
                    report.line("  %s: the fiducial was not in view. The machine was homed to "
                            + "recover it.", label);
                    report.finding(Severity.Warning, "The fiducial had gone out of the camera's "
                            + "view before %s: the machine had lost its position by more than "
                            + "the search range. It was homed and the measurements continued; "
                            + "positions before and after this point are not in the same frame.",
                            label);
                }
                machine.home();
            }
        }
        throw new Exception("The fiducial was not found even after homing the machine. Check "
                + "that the primary fiducial location is right and that the fiducial is in view "
                + "after homing.");
    }

    /** The noise floor of a camera from the last run, in millimetres, or null. */
    private Double noiseFloorMm(ReferenceCamera camera) {
        if (lastResults == null) {
            return null;
        }
        MachineDiagnosticsResults.VisionNoise noise = lastResults.getVisionNoise(camera.getId());
        return noise == null ? null : noise.getSdMm();
    }

    /** "…, N times the camera's own scatter" for a finding, or nothing if that was never measured. */
    private String againstNoiseFloor(ReferenceCamera camera, double spreadMm) {
        Double floor = noiseFloorMm(camera);
        if (floor == null || floor <= 0) {
            return "";
        }
        return String.format(" That is %.1f times the camera's own scatter of %.4f mm.",
                spreadMm / floor, floor);
    }

    // Test group 2: how much the camera scatters on its own.

    /**
     * The same fiducial located in many frames while nothing moves. What comes out is the scatter
     * of the detection itself, from lighting, sensor noise and sub-pixel interpolation, which is
     * the floor under every position the other groups report: a machine cannot be shown to
     * repeat better than the camera can see, and a spread below this floor is the camera, not
     * the machine.
     */
    private void testVisionNoise(ReferenceMachine machine, MachineDiagnosticsReport report)
            throws Exception {
        report.section("Vision noise floor, the fiducial located in successive frames of a "
                + "standing camera");
        ReferenceHead head = requireHead(machine);
        ReferenceCamera camera = requireDownLookingCamera(head);
        Location fiducial = requireFiducial(head);
        Length diameter = head.getCalibrationPrimaryFiducialDiameter();
        ReferenceControllerAxis xAxis = findControllerAxis(camera, Axis.Type.X);
        acquire(machine, camera, xAxis, fiducial, diameter, "Vision noise floor", report);
        camera.waitForCompletion(CompletionType.WaitForStillstand);
        Thread.sleep(machineSettleMs);

        VisionSolutions vision = machine.getVisionSolutions();
        Circle feature = vision.getExpectedOffsetsAndDiameter(camera, camera, fiducial, diameter,
                false);
        Location upp = camera.getUnitsPerPixelAtZ().convertToUnits(LengthUnit.Millimeters);
        List<Object[]> rows = new ArrayList<>();
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        int wanted = Math.max(5, noiseFrames);
        int missed = 0;
        double t0 = NanosecondTime.getRuntimeSeconds();
        double tLast = t0;
        camera.actuateLightBeforeCapture();
        try {
            BufferedImage frame = camera.lightSettleAndCapture();
            long seen = fingerprint(frame);
            int attempts = 0;
            while (xs.size() < wanted && attempts < wanted * 3) {
                checkAborted();
                attempts++;
                double t = NanosecondTime.getRuntimeSeconds();
                ScoreRange score = new ScoreRange();
                try {
                    Circle circle = vision.getSubjectPixelLocation(camera, camera, feature, 0.0,
                            null, score, false, frame);
                    xs.add(circle.x);
                    ys.add(circle.y);
                    scores.add(score.finalScore);
                    rows.add(new Object[] { xs.size() - 1, t - t0, circle.x, circle.y,
                            score.finalScore });
                    tLast = t;
                }
                catch (Exception e) {
                    missed++;
                }
                frame = freshFrame(camera, seen);
                seen = fingerprint(frame);
            }
        }
        finally {
            camera.actuateLightAfterCapture();
        }
        if (xs.size() < 5) {
            throw new Exception("The fiducial could not be located in enough frames. Check the "
                    + "fiducial diameter and the lighting.");
        }
        double medianX = MachineDiagnosticsMath.median(xs);
        double medianY = MachineDiagnosticsMath.median(ys);
        double sumSquares = 0;
        double range = 0;
        for (int i = 0; i < xs.size(); i++) {
            double dx = xs.get(i) - medianX;
            double dy = ys.get(i) - medianY;
            sumSquares += dx * dx + dy * dy;
            range = Math.max(range, Math.hypot(dx, dy));
        }
        double sdPixels = Math.sqrt(sumSquares / (xs.size() - 1));
        double pixelMm = Math.hypot(upp.getX(), upp.getY()) / Math.sqrt(2);
        double sdMm = sdPixels * pixelMm;
        double fps = xs.size() > 1 && tLast > t0 ? (xs.size() - 1) / (tLast - t0) : 0;
        Stats scoreStats = MachineDiagnosticsMath.stats(scores);
        for (Object[] r : rows) {
            r[2] = (Double) r[2] - medianX;
            r[3] = (Double) r[3] - medianY;
        }
        report.writeCsv("vision-noise.csv",
                new String[] { "frame", "t_s", "dx_px", "dy_px", "score" }, rows);
        report.line("  %d frames in %.1f s (%.1f frames/s), %d frames without a detection",
                xs.size(), tLast - t0, fps, missed);
        report.line("  scatter sd %.3f px = %.4f mm, largest excursion %.3f px", sdPixels, sdMm,
                range);
        report.line("  detection score %.2f to %.2f, mean %.2f", scoreStats.min, scoreStats.max,
                scoreStats.mean);
        log("Vision noise: sd %.3f px (%.4f mm) over %d frames at %.1f fps", sdPixels, sdMm,
                xs.size(), fps);
        Severity severity = sdPixels > VISION_NOISE_TOLERANCE_PIXELS ? Severity.Warning
                : Severity.Info;
        report.finding(severity, "With nothing moving, %s locates the fiducial to within %.3f px "
                + "sd (%.4f mm) from frame to frame. No position measured through this camera "
                + "can be trusted closer than this.", camera.getName(), sdPixels, sdMm);
        if (missed > 0) {
            report.finding(Severity.Warning, "%d of %d frames had no detection at all: the "
                    + "fiducial is at the edge of what the lighting and the pipeline can find.",
                    missed, xs.size() + missed);
        }
        MachineDiagnosticsResults.VisionNoise noise = new MachineDiagnosticsResults.VisionNoise(
                camera.getId(), sdPixels, sdMm, range, xs.size(), fps);
        if (driftSeconds > 0) {
            report.blank();
            report.line("Standing drift");
            measureStandingDrift(camera, vision, feature, upp, sdMm, noise, report, "after the noise floor");
        }
        recordResults(TestGroup.VisionNoise, report, results -> {
            List<MachineDiagnosticsResults.VisionNoise> kept = new ArrayList<>();
            for (MachineDiagnosticsResults.VisionNoise other : results.getVisionNoise()) {
                if (!other.getCameraId().equals(camera.getId())) {
                    kept.add(other);
                }
            }
            kept.add(noise);
            results.setVisionNoise(kept);
        });
    }

    /**
     * The fiducial watched for a minute or so with nothing commanded, about once a second. The
     * noise floor is what changes from one frame to the next; this is what changes from one
     * minute to the next - the frame, the camera on its mount and the axes under their holding
     * current, all moving with temperature - and it is the floor under any two readings taken
     * minutes apart, such as a board reading and the one that verifies a compensation made from
     * it. Read against the datum board group's anchor drift, which is taken between hops, it
     * says whether what moves there moves because the machine moved or moves anyway.
     */
    private void measureStandingDrift(ReferenceCamera camera, VisionSolutions vision, Circle feature,
            Location upp, double frameSdMm, MachineDiagnosticsResults.VisionNoise noise,
            MachineDiagnosticsReport report, String occasion) throws Exception {
        report.line("  the fiducial watched for %d s with nothing moving, %s", driftSeconds, occasion);
        List<Object[]> rows = new ArrayList<>();
        List<Double> ts = new ArrayList<>();
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        double t0 = NanosecondTime.getRuntimeSeconds();
        long seen = 0;
        camera.actuateLightBeforeCapture();
        try {
            while (NanosecondTime.getRuntimeSeconds() - t0 < driftSeconds) {
                checkAborted();
                BufferedImage frame = freshFrame(camera, seen);
                seen = fingerprint(frame);
                double t = NanosecondTime.getRuntimeSeconds() - t0;
                try {
                    Circle circle = vision.getSubjectPixelLocation(camera, camera, feature, 0.0,
                            null, new ScoreRange(), false, frame);
                    ts.add(t);
                    xs.add(circle.x * upp.getX());
                    ys.add(circle.y * upp.getY());
                    rows.add(new Object[] { t, circle.x, circle.y });
                }
                catch (Exception e) {
                    // A frame without a detection; the next one will do.
                }
                Thread.sleep(1000);
            }
        }
        finally {
            camera.actuateLightAfterCapture();
        }
        if (ts.size() < 5) {
            report.line("  too few detections (%d) to say anything about drift", ts.size());
            return;
        }
        double firstX = xs.get(0), firstY = ys.get(0);
        for (Object[] r : rows) {
            r[1] = (Double) r[1] - xs.get(0) / upp.getX();
            r[2] = (Double) r[2] - ys.get(0) / upp.getY();
        }
        report.writeCsv(noise != null ? "vision-drift.csv" : "homing-drift.csv",
                new String[] { "t_s", "dx_px", "dy_px" }, rows);
        double[] t = new double[ts.size()], x = new double[ts.size()], y = new double[ts.size()];
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < ts.size(); i++) {
            t[i] = ts.get(i);
            x[i] = xs.get(i) - firstX;
            y[i] = ys.get(i) - firstY;
            minX = Math.min(minX, x[i]);
            maxX = Math.max(maxX, x[i]);
            minY = Math.min(minY, y[i]);
            maxY = Math.max(maxY, y[i]);
        }
        MachineDiagnosticsMath.LinearFit fitX = MachineDiagnosticsMath.linearFit(t, x);
        MachineDiagnosticsMath.LinearFit fitY = MachineDiagnosticsMath.linearFit(t, y);
        double perMinuteX = fitX.slope * 60;
        double perMinuteY = fitY.slope * 60;
        double span = t[t.length - 1] - t[0];
        double excursion = Math.hypot(maxX - minX, maxY - minY);
        report.line("  %d detections over %.0f s; drift %+.4f mm/min in X, %+.4f mm/min in Y "
                + "(fit r2 %.2f / %.2f); the image wandered %.4f mm in all", ts.size(), span,
                perMinuteX, perMinuteY, fitX.rSquared, fitY.rSquared, excursion);
        if (noise != null) {
            noise.setDrift(span, perMinuteX, perMinuteY, excursion);
        }
        log("Standing drift %s: %+.4f / %+.4f mm/min over %.0f s, excursion %.4f mm", occasion,
                perMinuteX, perMinuteY, span, excursion);
        double perMinute = Math.hypot(perMinuteX, perMinuteY);
        if (excursion <= Math.max(5 * frameSdMm, 0.005)) {
            report.finding(Severity.Info, "With nothing moving for %.0f s %s, the image on %s stayed "
                    + "within %.4f mm: the frame, the camera and the axes hold still between "
                    + "readings.", span, occasion, camera.getName(), excursion);
        }
        else {
            report.finding(Severity.Warning, "With nothing moving for %.0f s %s, the image on %s "
                    + "drifted %.4f mm (%+.4f mm/min in X, %+.4f mm/min in Y), %.0f times the "
                    + "frame-to-frame scatter. Something moves with nothing commanded - the camera "
                    + "on its mount, the frame with temperature, or the axes under their holding "
                    + "current - and two readings a minute apart cannot be compared closer than "
                    + "this. If the datum board group's anchor drifts more than this between hops, "
                    + "the rest is the moving.", span, occasion, camera.getName(), excursion,
                    perMinuteX, perMinuteY, frameSdMm > 0 ? excursion / frameSdMm : 0);
        }
    }

    // Camera latency: how old a frame is when it arrives.

    /**
     * Frames are taken continuously while the camera makes a slow move onto the fiducial. At the
     * speed used nothing vibrates, so the image stops moving the instant the axis does; frames
     * that still show the motion after the controller has reported stillstand are frames that
     * were already old when they arrived. That is the delay of the camera's pipeline - the
     * buffering in the driver and the capture stack - and every settle wait has to exceed it
     * before the frame it captures is even of the present.
     */
    private void testCameraLatency(ReferenceMachine machine, MachineDiagnosticsReport report)
            throws Exception {
        report.section("Camera latency, from frames still showing a move the controller "
                + "reported complete");
        ReferenceHead head = requireHead(machine);
        ReferenceCamera camera = requireDownLookingCamera(head);
        Location fiducial = requireFiducial(head);
        Length diameter = head.getCalibrationPrimaryFiducialDiameter();
        ReferenceControllerAxis xAxis = findControllerAxis(camera, Axis.Type.X);
        if (xAxis == null) {
            throw new Exception("The camera has no X axis to move with.");
        }
        double upp = camera.getUnitsPerPixelAtZ().convertToUnits(LengthUnit.Millimeters).getX();
        int diameterPixels = (int) Math.round(
                diameter.convertToUnits(LengthUnit.Millimeters).getValue() / upp);
        double distance = 3.0;
        double speed = Math.max(0.01, Math.min(1.0, latencySpeedFactor));

        // Start beside the fiducial, from the same side each time.
        acquire(machine, camera, xAxis, fiducial, diameter, "Camera latency", report);
        Location start = fiducial.add(new Location(LengthUnit.Millimeters, -distance, 0, 0, 0));
        camera.moveTo(start, measureSpeedFactor);
        camera.waitForCompletion(CompletionType.WaitForStillstand);
        Thread.sleep(machineSettleMs);

        // Frames are taken on a thread of their own for the whole of the move, timestamped as
        // they arrive; the move is commanded and waited for on this one.
        List<double[]> samples = Collections.synchronizedList(new ArrayList<>());
        java.util.concurrent.atomic.AtomicBoolean capturing = new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.concurrent.atomic.AtomicReference<Exception> captureError = new java.util.concurrent.atomic.AtomicReference<>();
        camera.actuateLightBeforeCapture();
        Thread grabber = new Thread(() -> {
            try {
                long seen = 0;
                while (capturing.get()) {
                    BufferedImage image = camera.capture();
                    if (image == null) {
                        continue;
                    }
                    long print = fingerprint(image);
                    if (print == seen) {
                        continue;
                    }
                    seen = print;
                    double t = NanosecondTime.getRuntimeSeconds();
                    Circle circle = locateCircle(image, diameterPixels);
                    if (circle == null) {
                        continue;
                    }
                    samples.add(new double[] { t, circle.x, circle.y });
                }
            }
            catch (Exception e) {
                captureError.set(e);
            }
        }, "MachineDiagnostics latency frames");
        double tCommand;
        double tComplete;
        try {
            grabber.start();
            Thread.sleep(500);
            tCommand = NanosecondTime.getRuntimeSeconds();
            camera.moveTo(fiducial, speed);
            camera.waitForCompletion(CompletionType.WaitForStillstand);
            tComplete = NanosecondTime.getRuntimeSeconds();
            Thread.sleep(1500);
        }
        finally {
            capturing.set(false);
            grabber.join(3000);
            camera.actuateLightAfterCapture();
        }
        if (captureError.get() != null) {
            throw captureError.get();
        }
        List<double[]> frames = new ArrayList<>(samples);
        if (frames.size() < 10) {
            throw new Exception("Too few frames with the fiducial in them to measure latency. "
                    + "Check the fiducial diameter and the lighting.");
        }
        // Where the fiducial ended up in the image, from the frames of the last half second.
        double tEnd = frames.get(frames.size() - 1)[0];
        List<Double> finalXs = new ArrayList<>();
        for (double[] f : frames) {
            if (f[0] > tEnd - 0.5) {
                finalXs.add(f[1]);
            }
        }
        double finalX = MachineDiagnosticsMath.median(finalXs);
        double threshold = 1.0; // px: the move is 3 mm, hundreds of pixels
        Double tFirstMotion = null;
        Double tLastMotion = null;
        double startX = frames.get(0)[1];
        for (double[] f : frames) {
            if (tFirstMotion == null && Math.abs(f[1] - startX) > threshold) {
                tFirstMotion = f[0];
            }
            if (Math.abs(f[1] - finalX) > threshold) {
                tLastMotion = f[0];
            }
        }
        double fps = (frames.size() - 1) / (tEnd - frames.get(0)[0]);
        double framePeriod = fps > 0 ? 1 / fps : 0;
        List<Object[]> rows = new ArrayList<>();
        for (double[] f : frames) {
            rows.add(new Object[] { f[0] - tCommand, f[1] - finalX, f[2] });
        }
        report.writeCsv("camera-latency.csv",
                new String[] { "t_from_command_s", "dx_from_final_px", "y_px" }, rows);
        if (tLastMotion == null || tFirstMotion == null) {
            throw new Exception("The move was not seen in the frames at all; the fiducial may be "
                    + "outside the search window at the start position.");
        }
        double latency = Math.max(0, tLastMotion - tComplete) + framePeriod / 2;
        double commandToImage = tFirstMotion - tCommand;
        double buffered = latency * fps;
        report.line("  %d frames at %.1f frames/s over the move", frames.size(), fps);
        report.line("  move commanded at 0 ms, controller reported complete at %.0f ms",
                (tComplete - tCommand) * 1000);
        report.line("  first frame showing motion at %.0f ms, last at %.0f ms",
                (tFirstMotion - tCommand) * 1000, (tLastMotion - tCommand) * 1000);
        report.line("  pipeline latency %.0f ms, about %.1f frames", latency * 1000, buffered);
        log("Camera latency: %.0f ms (%.1f frames at %.1f fps)", latency * 1000, buffered, fps);
        Severity severity = latency > CAMERA_LATENCY_TOLERANCE_SECONDS ? Severity.Warning
                : Severity.Info;
        report.finding(severity, "%s delivers frames %.0f ms late: after the controller reported "
                + "the move complete, %.1f more frames still showed it moving. A frame captured "
                + "sooner than that after any move is a frame of the past.", camera.getName(),
                latency * 1000, buffered);
        report.finding(Severity.Info, "From the move command to the first frame showing motion "
                + "took %.0f ms, which is the command round trip and the camera latency together.",
                commandToImage * 1000);
        if (camera instanceof AbstractSettlingCamera) {
            AbstractSettlingCamera settling = (AbstractSettlingCamera) camera;
            if (settling.getSettleMethod() == AbstractSettlingCamera.SettleMethod.FixedTime
                    && settling.getSettleTimeMs() < latency * 1000) {
                report.finding(Severity.Problem, "%s waits a fixed %d ms before capturing, less "
                        + "than its own %.0f ms of latency: every capture after a move is of the "
                        + "image before the move finished.", camera.getName(),
                        settling.getSettleTimeMs(), latency * 1000);
            }
        }
        MachineDiagnosticsResults.CameraLatency measured =
                new MachineDiagnosticsResults.CameraLatency(camera.getId(), latency, fps, buffered);
        recordResults(TestGroup.CameraLatency, report, results -> {
            List<MachineDiagnosticsResults.CameraLatency> kept = new ArrayList<>();
            for (MachineDiagnosticsResults.CameraLatency other : results.getCameraLatency()) {
                if (!other.getCameraId().equals(camera.getId())) {
                    kept.add(other);
                }
            }
            kept.add(measured);
            results.setCameraLatency(kept);
        });
    }

    // Lost steps: whether fast travel arrives where it was sent.

    /**
     * The timing group says how fast the axes move; nothing in it says whether they arrived. An
     * axis driven past what its motor can hold slips or loses steps, silently, and the
     * controller's position count keeps going as if it had not. Here each axis makes a run of
     * long fast moves at each speed factor and is then brought back to the fiducial from the
     * same side as before: whatever it is off by is what the run lost.
     */
    private void testLostSteps(ReferenceMachine machine, MachineDiagnosticsReport report)
            throws Exception {
        report.section("Lost steps, from returning to the fiducial after a run of fast moves");
        ReferenceHead head = requireHead(machine);
        ReferenceCamera camera = requireDownLookingCamera(head);
        Location fiducial = requireFiducial(head);
        Length diameter = head.getCalibrationPrimaryFiducialDiameter();
        double[] speeds = MachineDiagnosticsMath.parseSeries(stressSpeedFactors);
        int cycles = Math.max(1, stressCycles);
        List<Object[]> rows = new ArrayList<>();
        List<MachineDiagnosticsResults.LostSteps> conclusions = new ArrayList<>();
        report.line("  %d cycles of %.0f mm each way per speed factor; the machine is brought back "
                + "to the fiducial from the same side before and after%s.", cycles, stressDistanceMm,
                homeBeforeEachStressSpeed ? ", and homed before each speed" : "");
        report.line("  %-6s %-8s %-12s %-12s %-14s", "axis", "speed", "travel mm", "drift mm",
                "per 1000 mm");
        for (Axis.Type type : new Axis.Type[] { Axis.Type.X, Axis.Type.Y }) {
            ReferenceControllerAxis axis = findControllerAxis(camera, type);
            if (axis == null) {
                continue;
            }
            Location unit = unitLocation(type);
            // The leg is centred on the fiducial and clipped to the axis travel.
            double half = stressDistanceMm / 2;
            Location low = fiducial.add(unit.multiply(-half, -half, 0, 0));
            Location high = fiducial.add(unit.multiply(half, half, 0, 0));
            double achieved = Math.abs(axisCoordinate(camera, axis, high)
                    - axisCoordinate(camera, axis, low));
            if (achieved < stressDistanceMm * 0.5) {
                report.line("  %s: only %.0f mm of travel around the fiducial, skipped.",
                        axis.getName(), achieved);
                continue;
            }
            double travelPerCycle = 2 * achieved;
            Detection before = null;
            if (!homeBeforeEachStressSpeed) {
                before = acquire(machine, camera, axis, fiducial, diameter,
                        String.format("Lost steps %s before", axis.getName()), report);
            }
            for (double speed : speeds) {
                checkAborted();
                if (homeBeforeEachStressSpeed) {
                    log("Homing before the %.2fx cycles on %s", speed, axis.getName());
                    machine.home();
                    before = acquire(machine, camera, axis, fiducial, diameter,
                            String.format("Lost steps %s %.2fx before", axis.getName(), speed), report);
                }
                for (int cycle = 0; cycle < cycles; cycle++) {
                    checkAborted();
                    camera.moveTo(low, speed);
                    camera.moveTo(high, speed);
                }
                camera.waitForCompletion(CompletionType.WaitForStillstand);
                approachFrom(camera, axis, fiducial, -10, measureSpeedFactor);
                Detection after = tryDetect(machine, camera, fiducial, diameter,
                        String.format("Lost steps %s %.2fx", axis.getName(), speed), 0.0);
                if (after == null) {
                    after = tryDetect(machine, camera, fiducial, diameter,
                            String.format("Lost steps %s %.2fx wide", axis.getName(), speed), 0.35);
                }
                if (after == null) {
                    // Lost beyond the search range: that is the finding, and higher speeds would
                    // only lose more. Home so that the groups after this one start referenced.
                    double travel = travelPerCycle * cycles;
                    rows.add(new Object[] { axis.getName(), speed, cycles, travel, Double.NaN,
                            Double.NaN, Double.NaN, Double.NaN, elapsed(), 0, Double.NaN, Double.NaN });
                    report.line("  %-6s %-8.2f %-12.0f %-12s %-14s", axis.getName(), speed, travel,
                            "lost", "beyond search");
                    report.finding(Severity.Problem, "Axis %s lost the fiducial altogether after "
                            + "%.0f mm of travel at %.2f of its speed: more than %.1f mm of steps "
                            + "lost. Higher speeds were not tried. The machine was homed to "
                            + "continue.", axis.getName(), travel, speed,
                            camera.getWidth() * camera.getUnitsPerPixelAtZ()
                                    .convertToUnits(LengthUnit.Millimeters).getX() * 0.3);
                    conclusions.add(new MachineDiagnosticsResults.LostSteps(axis.getId(), speed,
                            travel, Double.NaN, axis.getMotionLimit(1)));
                    recoveries++;
                    machine.home();
                    before = acquire(machine, camera, axis, fiducial, diameter,
                            String.format("Lost steps %s after homing", axis.getName()), report);
                    break;
                }
                Location drift = after.location.convertToUnits(LengthUnit.Millimeters)
                        .subtract(before.location.convertToUnits(LengthUnit.Millimeters));
                double along = type == Axis.Type.X ? drift.getX() : drift.getY();
                double travel = travelPerCycle * cycles;
                double perMetre = along * 1000 / travel;
                rows.add(row(new Object[] { axis.getName(), speed, cycles, travel, drift.getX(),
                        drift.getY(), along, perMetre }, elapsed(), after));
                report.line("  %-6s %-8.2f %-12.0f %-12.4f %-14.4f", axis.getName(), speed, travel,
                        along, perMetre);
                log("%s at %.2fx: drift %.4f mm over %.0f mm", axis.getName(), speed, along, travel);
                conclusions.add(new MachineDiagnosticsResults.LostSteps(axis.getId(), speed,
                        travel, along, axis.getMotionLimit(1)));
                // Each speed is measured against the same starting point, so a loss at one
                // speed does not hide a gain at the next: reset the reference after each.
                before = after;
            }
        }
        report.writeCsv("lost-steps.csv", columns(new String[] { "axis", "speed", "cycles",
                "travel_mm", "drift_x_mm", "drift_y_mm", "drift_along_mm", "drift_per_1000mm" }),
                rows);
        for (MachineDiagnosticsResults.LostSteps loss : conclusions) {
            ReferenceControllerAxis axis = controllerAxis(loss.getAxisId());
            String name = axis != null ? axis.getName() : loss.getAxisId();
            Double floor = noiseFloorMm(camera);
            double significant = Math.max(LOST_STEPS_TOLERANCE_MM,
                    floor != null ? floor * 4 : 0);
            if (Double.isNaN(loss.getDriftMm())) {
                continue;
            }
            if (Math.abs(loss.getDriftMm()) > significant) {
                report.finding(Severity.Problem, "Axis %s came back %.4f mm off after %.0f mm of "
                        + "travel at %.2f of its speed: it is losing steps or slipping at that "
                        + "speed, and the controller's position count does not know.%s", name,
                        loss.getDriftMm(), loss.getTravelMm(), loss.getSpeedFactor(),
                        againstNoiseFloor(camera, Math.abs(loss.getDriftMm())));
            }
        }
        if (!conclusions.isEmpty()) {
            boolean anyLoss = false;
            for (MachineDiagnosticsResults.LostSteps loss : conclusions) {
                anyLoss |= Double.isNaN(loss.getDriftMm())
                        || Math.abs(loss.getDriftMm()) > LOST_STEPS_TOLERANCE_MM;
            }
            if (!anyLoss) {
                report.finding(Severity.Info, "No axis lost steps at any speed factor up to the "
                        + "configured limit over %.0f mm of travel per factor.",
                        conclusions.get(0).getTravelMm());
            }
        }
        recordResults(TestGroup.LostSteps, report, results -> results.setLostSteps(conclusions));
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
        ReferenceControllerAxis approachAxis = findControllerAxis(camera, Axis.Type.X);
        acquire(machine, camera, approachAxis, fiducial, diameter, "X/Y positioning start", report);
        measureRepeatability(machine, report, graph, head, camera, fiducial, diameter);
        acquire(machine, camera, approachAxis, fiducial, diameter, "before the backlash matrix", report);
        measureRawBacklash(machine, report, graph, head, camera, fiducial, diameter, backlash);
        setPositioningGraph(graph);
        acquire(machine, camera, approachAxis, fiducial, diameter, "before the step test", report);
        measureStepResponse(machine, report, head, camera, fiducial, diameter, effectiveResolution);
        acquire(machine, camera, approachAxis, fiducial, diameter, "before the field of view scan", report);
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
        int lost = 0;
        ReferenceControllerAxis approachAxis = findControllerAxis(camera, Axis.Type.X);
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
                    final double displacement = sign * distance;
                    for (int repeat = 0; repeat < repeats; repeat++) {
                        checkAborted();
                        String label = String.format("Repeatability %s %+.0fmm", axis.getName(), displacement);
                        Detection detection = measure(machine, camera, approachAxis, fiducial, diameter,
                                label, report, () -> approachFrom(camera, axis, fiducial, displacement, 1.0));
                        if (detection == null) {
                            lost++;
                            continue;
                        }
                        double error = shortfallMm(detection.location, fiducial, unit);
                        errors.add(error);
                        allErrors.add(error);
                        rows.add(row(new Object[] { axis.getName(), sign > 0 ? "+" : "-", distance,
                                repeat, error }, elapsed(), detection));
                    }
                    if (errors.isEmpty()) {
                        report.line("  %-6s %-6s %-10.3f lost every time", axis.getName(),
                                sign > 0 ? "+" : "-", distance);
                        continue;
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
                columns(new String[] { "axis", "approach", "distance", "repeat", "error_mm" }), rows);
        if (lost > 0) {
            report.finding(Severity.Problem, "%d of the repeatability approaches lost the fiducial "
                    + "altogether and it had to be found again. At full speed the machine loses "
                    + "its position faster than these approaches can measure it.", lost);
        }
        if (!allErrors.isEmpty()) {
            Stats overall = MachineDiagnosticsMath.stats(allErrors);
            report.line("  Overall spread across every approach: %.4f mm", overall.getRange());
            report.finding(overall.getRange() > 0.05 ? Severity.Warning : Severity.Info,
                    "Repeated approaches to one point land within %.3f mm of each other "
                    + "(sd %.4f mm). This is the positional scatter a job sees.%s",
                    overall.getRange(), overall.stdDev,
                    againstNoiseFloor(camera, overall.stdDev));
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
        List<Object[]> rawRows = new ArrayList<>();
        report.blank();
        report.line("Backlash with compensation switched off, %d approach pairs per cell",
                Math.max(1, backlashRepeats));
        report.line("  %-6s %-8s %-10s %-12s %-10s", "axis", "speed", "distance", "backlash mm",
                "sd mm");
        ReferenceControllerAxis approachAxis = findControllerAxis(camera, Axis.Type.X);
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
                        List<Double> pairs = new ArrayList<>();
                        for (int repeat = 0; repeat < Math.max(1, backlashRepeats); repeat++) {
                            checkAborted();
                            Detection fromMinus = measure(machine, camera, approachAxis, fiducial, diameter,
                                    String.format("Backlash %s %.2fx -", axis.getName(), speed), report,
                                    () -> approachFrom(camera, axis, fiducial, -distance, speed));
                            Detection fromPlus = fromMinus == null ? null
                                    : measure(machine, camera, approachAxis, fiducial, diameter,
                                            String.format("Backlash %s %.2fx +", axis.getName(), speed), report,
                                            () -> approachFrom(camera, axis, fiducial, distance, speed));
                            if (fromMinus == null || fromPlus == null) {
                                report.line("  %-6s %-8.2f %-10.3f pair %d lost", axis.getName(), speed,
                                        distance, repeat + 1);
                                continue;
                            }
                            // The difference between where the machine physically stopped coming
                            // from one side and from the other.
                            double backlash = shortfallMm(fromMinus.location, fiducial, unit)
                                    - shortfallMm(fromPlus.location, fiducial, unit);
                            pairs.add(backlash);
                            rawRows.add(row(new Object[] { axis.getName(), speed, distance, repeat,
                                    backlash }, elapsed(), fromPlus));
                        }
                        if (pairs.isEmpty()) {
                            continue;
                        }
                        Stats cell = MachineDiagnosticsMath.stats(pairs);
                        double backlash = cell.mean;
                        rows.add(new Object[] { axis.getName(), speed, distance, backlash,
                                cell.stdDev });
                        report.line("  %-6s %-8.2f %-10.3f %-12.4f %-10.4f", axis.getName(), speed,
                                distance, backlash, cell.stdDev);
                        row(graph, "mm", axis.getName() + " backlash " + speed + "x",
                                COLOR_MEASURED, true, true).recordDataPoint(distance, backlash);
                        log("%s backlash at %.2fx over %.1fmm: %.4f (sd %.4f)", axis.getName(),
                                speed, distance, backlash, cell.stdDev);
                    }
                }
            }
        }
        finally {
            restoreBacklashCompensation(saved);
        }
        report.writeCsv("backlash.csv",
                new String[] { "axis", "speed", "distance", "backlash_mm", "sd_mm" }, rows);
        report.writeCsv("backlash-raw.csv",
                columns(new String[] { "axis", "speed", "distance", "repeat", "backlash_mm" }),
                rawRows);
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
                    + "method is %s.%s", axis.getName(), stats.min, stats.max, configured.getValue(),
                    axis.getBacklashCompensationMethod(),
                    againstNoiseFloor(camera, stats.getRange()));
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
                    Detection detection = tryDetect(machine, camera, fiducial, diameter,
                            String.format("Step %s %.2fx", axis.getName(), speed), 0.35);
                    if (detection == null) {
                        report.line("  %s at %.2fx: the fiducial was lost at step %d; the staircase "
                                + "ends here.", axis.getName(), speed, step);
                        acquire(machine, camera, findControllerAxis(camera, Axis.Type.X), fiducial,
                                diameter, "step test recovery", report);
                        break;
                    }
                    double shortfall = shortfallMm(detection.location, fiducial, unit);
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
                    rows.add(row(new Object[] { axis.getName(), speed, step, offset, actual,
                            absoluteError, relative }, elapsed(), detection));
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
        report.writeCsv("step-response.csv", columns(new String[] { "axis", "speed", "step",
                "commanded_mm", "actual_mm", "absolute_error_mm", "relative_mm" }), rows);
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
                Detection detection = measure(machine, camera, findControllerAxis(camera, Axis.Type.X),
                        fiducial, diameter, "Field of view scan", report,
                        () -> camera.moveTo(target, measureSpeedFactor));
                if (detection == null) {
                    continue;
                }
                Location detected = detection.location.convertToUnits(LengthUnit.Millimeters);
                double errorX = detected.getX() - fiducial.convertToUnits(LengthUnit.Millimeters).getX();
                double errorY = detected.getY() - fiducial.convertToUnits(LengthUnit.Millimeters).getY();
                rows.add(row(new Object[] { dx, dy, errorX, errorY }, elapsed(), detection));
                offsetsX.add(dx);
                errorsX.add(errorX);
                offsetsY.add(dy);
                errorsY.add(errorY);
            }
            log("Field of view row %d of %d", iy + 1, fieldOfViewGridSteps);
        }
        report.writeCsv("field-of-view.csv",
                columns(new String[] { "offset_x_mm", "offset_y_mm", "error_x_mm", "error_y_mm" }),
                rows);
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
        double sumSquaredOffsets = 0;
        double meanOffset = 0;
        for (double offset : offsets) {
            meanOffset += offset / offsets.length;
        }
        for (int i = 0; i < offsets.length; i++) {
            double residual = errors[i] - fit.valueAt(offsets[i]);
            residualSum += residual * residual;
            sumSquaredOffsets += (offsets[i] - meanOffset) * (offsets[i] - meanOffset);
        }
        double rms = Math.sqrt(residualSum / offsets.length);
        // The slope's standard error, and what the machine's own scatter does to it: this scan
        // moves the machine, so a machine whose short moves land tens of microns apart puts
        // that scatter into the scale it reads. The fiducial pairs and the ruler read the
        // camera with the machine standing still.
        double slopeError = offsets.length > 2 && sumSquaredOffsets > 0
                ? Math.sqrt(residualSum / (offsets.length - 2) / sumSquaredOffsets) : 0;
        scale.add(new FieldOfView(camera.getId(), axis, fit.slope, rms));
        report.line("  %s: scale error %+.2f%% (standard error %.2f%%), residual after removing "
                + "it %.4f mm rms", axis, fit.slope * 100, slopeError * 100, rms);
        if (rms > 0.005) {
            report.line("  %s: the residual is the machine's own scatter over these short moves, "
                    + "which this scan cannot tell from the camera; the fiducial pairs and the "
                    + "ruler in the datum board group read the camera's scale with the machine "
                    + "standing still.", axis);
        }
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
        int runsPerDistance = Math.max(1, settleRepeats);
        report.line("  %d runs per distance, arriving along X, along Y and diagonally; the slowest "
                + "run is the one the wait has to cover.", runsPerDistance);
        double[][] directions = { { 1, 0 }, { 0, 1 }, { Math.sqrt(0.5), Math.sqrt(0.5) } };
        String[] directionNames = { "X", "Y", "XY" };
        List<MachineDiagnosticsResults.Vibration> vibrations = new ArrayList<>();
        for (double distance : distances) {
            Double settled = null;
            boolean neverSettled = false;
            Color color = palette[index++ % palette.length];
            for (int d = 0; d < directions.length; d++) {
                acquire(machine, camera, findControllerAxis(camera, Axis.Type.X), fiducial, diameter,
                        String.format("settling, %.0f mm along %s", distance, directionNames[d]), report);
                for (int run = 0; run < runsPerDistance; run++) {
                    checkAborted();
                    SettleRun thisRun = sampleSettling(machine, camera, fiducial, distance, directions[d],
                            directionNames[d], diameterPixels, graph, rows, color, run);
                    if (thisRun.settled == null) {
                        neverSettled = true;
                    }
                    else if (settled == null || thisRun.settled > settled) {
                        settled = thisRun.settled;
                    }
                    report.line("  after a %.0f mm move along %s, run %d: %s; %s", distance,
                            directionNames[d], run + 1,
                            thisRun.settled == null ? "never settled"
                                    : String.format("settled at %.0f ms", thisRun.settled * 1000),
                            describe(thisRun.oscillation, thisRun.fps));
                    vibrations.add(new MachineDiagnosticsResults.Vibration(camera.getId(),
                            directionNames[d], distance, thisRun.oscillation.amplitude,
                            thisRun.oscillation.frequencyHz, thisRun.oscillation.decaySeconds,
                            thisRun.fps / 2));
                }
            }
            if (settled != null && (worstSettled == null || settled > worstSettled)) {
                worstSettled = settled;
                worstDistance = distance;
            }
            if (neverSettled) {
                report.line("  after a %.0f mm move: never settled within %.0f ms",
                        distance, settleSampleSeconds * 1000);
                report.finding(Severity.Warning, "The image from %s had not stopped moving %.0f ms "
                        + "after a %.0f mm move.", camera.getName(), settleSampleSeconds * 1000,
                        distance);
            }
            if (settled != null) {
                report.line("  after a %.0f mm move: slowest of %d runs settled at %.0f ms",
                        distance, runsPerDistance, settled * 1000);
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
                new String[] { "distance_mm", "direction", "run", "time_s", "deviation_px",
                        "along_px" }, rows);
        setSettleGraph(graph);
        reportVibration(report, camera, vibrations);
        List<Settling> settleTimes = new ArrayList<>();
        if (worstSettled != null) {
            settleTimes.add(new Settling(camera.getId(), worstSettled, worstDistance));
        }
        recordResults(TestGroup.CameraSettle, report, results -> {
            results.setSettling(settleTimes);
            results.setVibration(vibrations);
        });
    }

    /** One arrival: when it settled, and what it did on the way. */
    private static final class SettleRun {
        final Double settled;
        final MachineDiagnosticsMath.Oscillation oscillation;
        final double fps;

        SettleRun(Double settled, MachineDiagnosticsMath.Oscillation oscillation, double fps) {
            this.settled = settled;
            this.oscillation = oscillation;
            this.fps = fps;
        }
    }

    private static String describe(MachineDiagnosticsMath.Oscillation oscillation, double fps) {
        StringBuilder text = new StringBuilder();
        text.append(String.format("amplitude %.1f px", oscillation.amplitude));
        if (oscillation.frequencyHz != null) {
            text.append(String.format(", about %.1f Hz", oscillation.frequencyHz));
        }
        if (oscillation.decaySeconds != null) {
            text.append(String.format(", decaying with a %.0f ms time constant",
                    oscillation.decaySeconds * 1000));
        }
        text.append(String.format(" (frames at %.0f/s resolve up to %.0f Hz)", fps, fps / 2));
        return text.toString();
    }

    /**
     * The worst decay per direction, as findings. A frequency is reported as what the frame
     * rate could resolve: a belt resonance at 40 Hz seen at 30 frames per second shows up as
     * 10 Hz, and there is no telling the two apart from these samples.
     */
    private void reportVibration(MachineDiagnosticsReport report, ReferenceCamera camera,
            List<MachineDiagnosticsResults.Vibration> vibrations) {
        Map<String, MachineDiagnosticsResults.Vibration> worst = new LinkedHashMap<>();
        for (MachineDiagnosticsResults.Vibration v : vibrations) {
            MachineDiagnosticsResults.Vibration previous = worst.get(v.getDirection());
            double decay = v.getDecaySeconds() == null ? 0 : v.getDecaySeconds();
            double previousDecay = previous == null || previous.getDecaySeconds() == null ? -1
                    : previous.getDecaySeconds();
            if (previous == null || decay > previousDecay) {
                worst.put(v.getDirection(), v);
            }
        }
        for (MachineDiagnosticsResults.Vibration v : worst.values()) {
            String frequency = v.getFrequencyHz() == null ? "no countable oscillation"
                    : String.format("about %.1f Hz as seen at this frame rate, which cannot tell it "
                            + "from anything above %.0f Hz", v.getFrequencyHz(), v.getResolvableHz());
            if (v.getDecaySeconds() != null && v.getDecaySeconds() > SLOW_DECAY_SECONDS) {
                report.finding(Severity.Warning, "Arriving along %s after a %.0f mm move, the image "
                        + "on %s rings with %.1f px of amplitude and takes %.0f ms to decay to a "
                        + "third of it (%s). That is weak damping in the mechanism, and the settle "
                        + "wait is paying for it on every capture.", v.getDirection(),
                        v.getDistanceMm(), camera.getName(), v.getAmplitudePixels(),
                        v.getDecaySeconds() * 1000, frequency);
            }
            else {
                report.finding(Severity.Info, "Arriving along %s after a %.0f mm move, the image on "
                        + "%s moves by up to %.1f px%s; %s.", v.getDirection(), v.getDistanceMm(),
                        camera.getName(), v.getAmplitudePixels(),
                        v.getDecaySeconds() == null ? ""
                                : String.format(" and decays with a %.0f ms time constant",
                                        v.getDecaySeconds() * 1000),
                        frequency);
            }
        }
    }

    private SettleRun sampleSettling(ReferenceMachine machine, ReferenceCamera camera,
            Location fiducial, double distance, double[] direction, String directionName,
            int diameterPixels, SimpleGraph graph, List<Object[]> rows, Color color, int run)
                    throws Exception {
        MovableUtils.moveToLocationAtSafeZ(camera, fiducial.add(new Location(LengthUnit.Millimeters,
                -distance * direction[0], -distance * direction[1], 0, 0)), measureSpeedFactor);
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
            long seen = 0;
            while (NanosecondTime.getRuntimeSeconds() - t0 < settleSampleSeconds) {
                checkAborted();
                BufferedImage image = camera.capture();
                if (image == null) {
                    continue;
                }
                // A camera can hand out the same frame twice. Counted as a sample it would look
                // like the image had stopped moving when in fact nothing new was looked at.
                long print = fingerprint(image);
                if (print == seen) {
                    continue;
                }
                seen = print;
                double t = NanosecondTime.getRuntimeSeconds() - t0;
                Circle circle = locateCircle(image, diameterPixels);
                if (circle == null) {
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
        // Where the image ends up is the median of the last quarter second, not the last frame.
        List<Double> tailX = new ArrayList<>();
        List<Double> tailY = new ArrayList<>();
        double tEnd = times.get(times.size() - 1);
        for (int i = 0; i < times.size(); i++) {
            if (times.get(i) > tEnd - 0.25) {
                tailX.add(xs.get(i));
                tailY.add(ys.get(i));
            }
        }
        double finalX = MachineDiagnosticsMath.median(tailX);
        double finalY = MachineDiagnosticsMath.median(tailY);
        double[] timeArray = new double[times.size()];
        double[] deviations = new double[times.size()];
        double[] along = new double[times.size()];
        for (int i = 0; i < times.size(); i++) {
            timeArray[i] = times.get(i);
            double dx = xs.get(i) - finalX;
            double dy = ys.get(i) - finalY;
            deviations[i] = Math.hypot(dx, dy);
            // Signed, along the direction of arrival in the image: the image axes are the
            // machine's up to a rotation and mirror, which do not change a frequency.
            along[i] = dx * direction[0] + dy * direction[1];
            rows.add(new Object[] { distance, directionName, run, timeArray[i], deviations[i],
                    along[i] });
            row(graph, "px", String.format("%.0f mm %s #%d", distance, directionName, run + 1),
                    color, true, true).recordDataPoint(timeArray[i], deviations[i]);
        }
        // A sub-pixel detector quantises; a deviation half a quantum over the threshold is the
        // threshold. And the first frames after the controller reports stillstand are frames of
        // the move itself when the camera is late: the second real run read a 2 mm arrival as
        // 160 px of "vibration" from one such frame. The settle time keeps them - the wait has
        // to cover the latency too - but the oscillation is read from the frames after it.
        double quantum = 1.0 / Math.max(1, machine.getVisionSolutions().getSuperSampling());
        double threshold = settleThresholdPixels + quantum / 2;
        Double settled = MachineDiagnosticsMath.settleTime(timeArray, deviations, threshold);
        double latency = 0.15;
        if (lastResults != null && lastResults.getCameraLatency(camera.getId()) != null) {
            latency = lastResults.getCameraLatency(camera.getId()).getLatencySeconds() + 0.02;
        }
        int from = 0;
        while (from < timeArray.length - 3 && timeArray[from] < latency) {
            from++;
        }
        double[] laterTimes = java.util.Arrays.copyOfRange(timeArray, from, timeArray.length);
        double[] laterAlong = java.util.Arrays.copyOfRange(along, from, along.length);
        MachineDiagnosticsMath.Oscillation oscillation = MachineDiagnosticsMath.oscillation(
                laterTimes, laterAlong, threshold);
        double fps = times.size() > 1 ? (times.size() - 1) / (tEnd - times.get(0)) : 0;
        if (from > 0 && deviations[0] > 20) {
            rows.add(new Object[] { distance, directionName, run, -1.0, deviations[0], along[0] });
        }
        return new SettleRun(settled, oscillation, fps);
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
                approachFrom(camera, xAxis, fiducial, -10, measureSpeedFactor);
            }
            else {
                MovableUtils.moveToLocationAtSafeZ(camera, fiducial, measureSpeedFactor);
            }
            Detection detection = tryDetect(machine, camera, fiducial, diameter,
                    "Homing repeatability", 0.0);
            if (detection == null) {
                detection = tryDetect(machine, camera, fiducial, diameter, "Homing repeatability wide", 0.35);
            }
            if (detection == null) {
                report.line("  %-8d %-12s %-12s", cycle, "lost", "lost");
                report.finding(Severity.Problem, "After homing cycle %d the fiducial was not in "
                        + "the camera's view at all: homing put the origin more than the search "
                        + "range from where it was.", cycle + 1);
                rows.add(new Object[] { cycle, Double.NaN, Double.NaN, elapsed(), 0, Double.NaN, Double.NaN });
                continue;
            }
            Location detected = detection.location.convertToUnits(LengthUnit.Millimeters);
            double errorX = detected.getX() - reference.getX();
            double errorY = detected.getY() - reference.getY();
            xErrors.add(errorX);
            yErrors.add(errorY);
            rows.add(row(new Object[] { cycle, errorX, errorY }, elapsed(), detection));
            report.line("  %-8d %-12.4f %-12.4f", cycle, errorX, errorY);
        }
        report.writeCsv("homing.csv",
                columns(new String[] { "cycle", "error_x_mm", "error_y_mm" }), rows);
        if (xErrors.size() < 2) {
            throw new Exception("The fiducial was found after fewer than two homing cycles; "
                    + "nothing can be said about a spread.");
        }
        Stats statsX = MachineDiagnosticsMath.stats(xErrors);
        Stats statsY = MachineDiagnosticsMath.stats(yErrors);
        report.line("  X spread %.4f mm (sd %.4f), Y spread %.4f mm (sd %.4f)",
                statsX.getRange(), statsX.stdDev, statsY.getRange(), statsY.stdDev);
        double worst = Math.max(statsX.getRange(), statsY.getRange());
        recordResults(TestGroup.Homing, report, results -> results
                .setHoming(new MachineDiagnosticsResults.Homing(head.getId(), worst, homingCycles)));
        Severity severity = worst > 0.05 ? Severity.Warning : Severity.Info;
        report.finding(severity, "Homing reproduces the origin to within %.3f mm over %d cycles.%s",
                worst, homingCycles, againstNoiseFloor(camera, worst));
        // Two different things: how far apart the homings land (the scatter above), and where
        // they land against the place the fiducial was taught at. The second is what the user
        // sees as the crosshair standing beside the fiducial after every homing: the endstops'
        // origin today against the origin everything was taught in, which moves from day to day.
        double offset = Math.hypot(statsX.mean, statsY.mean);
        report.line("  After homing the fiducial sits %+.4f, %+.4f mm from where it was taught, on "
                + "average.", statsX.mean, statsY.mean);
        if (offset > HOMING_SCATTER_TOLERANCE_MM
                && head.getVisualHomingMethod() == ReferenceHead.VisualHomingMethod.None) {
            report.finding(Severity.Warning, "Every homing puts the fiducial %.3f mm (%+.3f, %+.3f) "
                    + "from where it was taught. That is the endstops' origin today against the "
                    + "origin the fiducial, the feeders and the offsets were taught in, and it "
                    + "shifts from day to day; every taught position is off by it until the "
                    + "machine is homed against the fiducial itself. Visual homing does that. Set "
                    + "the homing fiducial location to the primary fiducial's own coordinates "
                    + "(%.3f, %.3f), so that homing puts the origin back into the frame everything "
                    + "was taught in rather than into a new one.", offset, statsX.mean, statsY.mean,
                    fiducial.convertToUnits(LengthUnit.Millimeters).getX(),
                    fiducial.convertToUnits(LengthUnit.Millimeters).getY());
        }
        if (worst > 0.05 && head.getVisualHomingMethod() == ReferenceHead.VisualHomingMethod.None) {
            report.finding(Severity.Warning, "Visual homing is off, so this scatter is the "
                    + "repeatability of the endstops and it carries into every job. Visual homing "
                    + "against the fiducial would remove it.");
        }
        if (offset > HOMING_SCATTER_TOLERANCE_MM / 2
                && head.getVisualHomingMethod() != ReferenceHead.VisualHomingMethod.None) {
            // Visual homing sets the frame from its own detection of the fiducial, so this offset
            // is not the endstops: it is where a normal approach to the fiducial lands against
            // the frame the homing set - the machine's positioning, seen from the origin - and
            // any difference between the homing pipeline's centre and this test's detector. The
            // seventh session read +0.03 / +0.04 mm in every one of fifteen cycles, with Y
            // 0.05 mm different in three of them, while visual homing still took a short move
            // onto the fiducial as exact and Y executed such moves as a jump or not at all.
            report.finding(Severity.Info, "After every homing the fiducial sits %.3f mm (%+.3f, %+.3f) "
                    + "from where it was taught, though the homings agree with one another to "
                    + "%.3f mm. Visual homing is on, so this is not the endstops: it is where an "
                    + "approach of 10 mm from -X lands against the frame the homing set from its "
                    + "own detection, and any difference between the homing pipeline's centre and "
                    + "this test's. Every measured position on this machine is made by such an "
                    + "approach, so it is the offset the homed frame has against everything "
                    + "taught.", offset, statsX.mean, statsY.mean, worst);
        }
        if (driftSeconds > 0) {
            // The camera stands on the fiducial after the last cycle: what the origin does in
            // the minute after a homing. The seventh session's board readings found the anchor
            // 0.08 mm to -Y of the taught location a minute after homing and 0.20 mm after
            // two, run after run, against +0.04 mm right after; whether that is the machine
            // relaxing after the homing bump or moving with every hop is what this says.
            VisionSolutions vision = machine.getVisionSolutions();
            Circle feature = vision.getExpectedOffsetsAndDiameter(camera, camera, fiducial,
                    diameter, false);
            Location upp = camera.getUnitsPerPixelAtZ().convertToUnits(LengthUnit.Millimeters);
            report.blank();
            report.line("After the last homing, standing on the fiducial");
            Double floor = noiseFloorMm(camera);
            measureStandingDrift(camera, vision, feature, upp, floor == null ? 0.001 : floor, null,
                    report, "after homing");
        }
    }

    // The datum board: the machine's frame against geometry that is known.

    /** A fiducial of the board found on the machine: where it should be and where it was. */
    private static final class Found {
        final DatumBoard.Dot dot;
        /** Where it was seen, corrected for the anchor's drift; recomputed once the drift after is known. */
        Location location;
        final Location rawLocation;
        final Detection detection;
        /** Index into the anchor readings of the one taken just before the hop to this point. */
        final int anchorRead;

        Found(DatumBoard.Dot dot, Location rawLocation, Detection detection, int anchorRead,
                Location driftBefore) {
            this.dot = dot;
            this.rawLocation = rawLocation;
            this.detection = detection;
            this.anchorRead = anchorRead;
            this.location = driftBefore == null ? rawLocation
                    : rawLocation.subtract(driftBefore.derive(null, null, 0.0, 0.0));
        }

        /**
         * The drift the machine had before the hop and the drift it had after: the point is
         * corrected by their mean. A drift that happened on the way out belongs wholly to the
         * point, one on the way back not at all; not knowing which, the mean halves the error.
         */
        void correct(Location driftBefore, Location driftAfter) {
            Location mean = driftBefore.add(driftAfter).multiply(0.5, 0.5, 0, 0);
            location = rawLocation.subtract(mean.derive(null, null, 0.0, 0.0));
        }
    }

    /**
     * Every test above measures the machine against itself: how well it returns to one point,
     * how far one move overshoots another. None of them can say whether a commanded 70 mm is
     * 70 mm, or whether X and Y are square, because there was nothing on the table whose
     * geometry was known. The datum board is that thing. Its copper fiducials are placed by
     * one photoplot to about 0.02 mm, so the transform that fits them to where the machine
     * finds them is the machine's frame: the scale along each axis, the angle short of square,
     * and, in the residuals, what does not fit a straight frame at all. The ruler on it gives
     * the camera's own scale in a single frame, without the machine moving, which is the one
     * thing the field of view scan could not separate from the machine's scale; and stepping
     * along it in quarter millimetres reads the position error at a resolution fine enough to
     * see the belt pitch.
     */
    private void testDatumBoard(ReferenceMachine machine, MachineDiagnosticsReport report)
            throws Exception {
        DatumBoard board = DatumBoard.lumenPnp();
        report.section("The machine's frame against the " + board.getName());
        ReferenceHead head = requireHead(machine);
        ReferenceCamera camera = requireDownLookingCamera(head);
        Location fiducial = requireFiducial(head).convertToUnits(LengthUnit.Millimeters);
        Length configuredDiameter = head.getCalibrationPrimaryFiducialDiameter();
        DatumBoard.Dot anchor = board.getAnchor();
        if (Math.abs(configuredDiameter.convertToUnits(LengthUnit.Millimeters).getValue()
                - anchor.diameterMm) > 0.3) {
            throw new Exception(String.format("The primary fiducial is %.2f mm across and the "
                    + "board's %s is %.2f mm: the primary fiducial is not %s of this board.",
                    configuredDiameter.convertToUnits(LengthUnit.Millimeters).getValue(),
                    anchor.name, anchor.diameterMm, anchor.name));
        }
        ReferenceControllerAxis xAxis = findControllerAxis(camera, Axis.Type.X);
        ReferenceControllerAxis yAxis = findControllerAxis(camera, Axis.Type.Y);

        // The anchor, approached the way everything else on the board will be. Every other
        // point is measured as a hop from the anchor and back: the anchor is read again before
        // each, and whatever it has moved by in the camera since the first reading - the
        // machine losing its position between hops - is taken off the point. The first real run
        // had the frame wrecked by exactly that, at full speed.
        Detection anchorSeen = acquire(machine, camera, xAxis, fiducial, configuredDiameter,
                anchor.name, report);
        Location anchorMachine = anchorSeen.location.convertToUnits(LengthUnit.Millimeters);
        List<Found> found = new ArrayList<>();
        found.add(new Found(anchor, anchorMachine, anchorSeen, -1, null));
        anchorReference = anchorMachine;
        anchorDriftLimit = 0;
        anchorReads.clear();

        // Which way round the board lies: the nearest fiducials are looked for under each of the
        // eight ways it can, and the first way that finds them all is taken.
        List<DatumBoard.Dot> near = board.getOrientationFiducials();
        int turns = -1;
        boolean mirrored = false;
        search: for (boolean mirror : new boolean[] { false, true }) {
            for (int quarter = 0; quarter < 4; quarter++) {
                checkAborted();
                List<Found> trial = new ArrayList<>();
                for (DatumBoard.Dot dot : near) {
                    double[] o = DatumBoard.orient(dot.x - anchor.x, dot.y - anchor.y, quarter, mirror);
                    Location predicted = anchorMachine.add(new Location(LengthUnit.Millimeters,
                            o[0], o[1], 0, 0));
                    Found seen = lookFor(machine, camera, xAxis, fiducial, configuredDiameter, report,
                            dot, predicted, 0.3);
                    if (seen == null) {
                        break;
                    }
                    trial.add(seen);
                }
                if (trial.size() == near.size()) {
                    turns = quarter;
                    mirrored = mirror;
                    found.addAll(trial);
                    break search;
                }
                log("Board not lying %s%d quarter turns; trying the next way.",
                        mirror ? "mirrored, " : "", quarter);
            }
        }
        if (turns < 0) {
            throw new Exception("The board's fiducials around " + anchor.name + " were not found "
                    + "in any orientation. Is the primary fiducial " + anchor.name + " of this "
                    + "board, and is the board unobstructed?");
        }
        report.line("  The board lies %s%d quarter turn(s) from its drawing.",
                mirrored ? "mirrored and " : "", turns);

        // The exact frame from what was found so far predicts the far fiducials well enough to
        // find them, and then everything found fits the frame proper.
        MachineDiagnosticsMath.Affine rough = fit(found);
        for (DatumBoard.Dot dot : board.getFiducials()) {
            boolean have = false;
            for (Found f : found) {
                have |= f.dot == dot;
            }
            if (have) {
                continue;
            }
            checkAborted();
            double[] p = rough.apply(dot.x, dot.y);
            Found seen = lookFor(machine, camera, xAxis, fiducial, configuredDiameter, report, dot,
                    new Location(LengthUnit.Millimeters, p[0], p[1], fiducial.getZ(), 0), 0.15);
            if (seen == null) {
                report.line("  %s was not found where the frame predicts it; left out.", dot.name);
                continue;
            }
            found.add(seen);
        }
        // One more reading of the anchor, so that the last hop too has a drift read after it;
        // then every point is corrected by the mean of the drift before and after its hop.
        anchorDrift(machine, camera, xAxis, fiducial, configuredDiameter, report, "the frame fit");
        for (Found f : found) {
            if (f.anchorRead < 0) {
                continue;
            }
            Location before = anchorReads.get(f.anchorRead);
            Location after = anchorReads.get(Math.min(f.anchorRead + 1, anchorReads.size() - 1));
            f.correct(before, after);
        }
        MachineDiagnosticsMath.Affine frame = fit(found);
        // A fiducial the machine drifted through on the way to it does not lie on the frame; it
        // lies wherever the drift left it. The fifth real run had one such point, 0.27 mm off
        // with the other six within 0.03, and the scale fitted through it was 0.2 % wrong -
        // which the compensation then applied. One point whose residual stands out from the
        // rest by that much is left out and the frame fitted again, and the report says so.
        Found leftOut = null;
        if (found.size() >= 6) {
            int worst = -1;
            double worstResidual = 0;
            double sumOthers = 0;
            for (int i = 0; i < found.size(); i++) {
                double r = Math.hypot(frame.residualsX[i], frame.residualsY[i]);
                if (r > worstResidual) {
                    worstResidual = r;
                    worst = i;
                }
            }
            for (int i = 0; i < found.size(); i++) {
                if (i != worst) {
                    sumOthers += frame.residualsX[i] * frame.residualsX[i]
                            + frame.residualsY[i] * frame.residualsY[i];
                }
            }
            double rmsOthers = Math.sqrt(sumOthers / (found.size() - 1));
            if (worst >= 0 && worstResidual > DATUM_RESIDUAL_TOLERANCE_MM
                    && worstResidual > 3 * rmsOthers && found.get(worst).dot != anchor) {
                leftOut = found.remove(worst);
                frame = fit(found);
                report.line("  %s is %.4f mm off the frame the other %d fiducials agree on to %.4f "
                        + "mm rms; the machine drifted through that hop. It is left out of the fit.",
                        leftOut.dot.name, worstResidual, found.size(), rmsOthers);
                report.finding(Severity.Warning, "%s was found %.3f mm from where the frame through "
                        + "the other %d fiducials puts it, and left out: the machine lost position "
                        + "during that hop. The frame below is fitted without it.", leftOut.dot.name,
                        worstResidual, found.size());
            }
        }
        List<Object[]> rows = new ArrayList<>();
        report.blank();
        report.line("  %-6s %-10s %-10s %-10s %-10s %-10s %-10s", "point", "board x", "board y",
                "machine x", "machine y", "resid x", "resid y");
        for (int i = 0; i < found.size(); i++) {
            Found f = found.get(i);
            rows.add(row(new Object[] { f.dot.name, f.dot.x, f.dot.y, f.location.getX(),
                    f.location.getY(), frame.residualsX[i], frame.residualsY[i] }, elapsed(),
                    f.detection));
            report.line("  %-6s %-10.3f %-10.3f %-10.4f %-10.4f %-+10.4f %-+10.4f", f.dot.name,
                    f.dot.x, f.dot.y, f.location.getX(), f.location.getY(), frame.residualsX[i],
                    frame.residualsY[i]);
        }
        if (leftOut != null) {
            rows.add(row(new Object[] { leftOut.dot.name + " (left out)", leftOut.dot.x,
                    leftOut.dot.y, leftOut.location.getX(), leftOut.location.getY(), Double.NaN,
                    Double.NaN }, elapsed(), leftOut.detection));
        }
        report.writeCsv("datum-fiducials.csv", columns(new String[] { "point", "board_x_mm",
                "board_y_mm", "machine_x_mm", "machine_y_mm", "residual_x_mm", "residual_y_mm" }),
                rows);
        report.line("  scale X %+.3f%%, scale Y %+.3f%%, Y leans %+.3f deg towards +X, board "
                + "rotated %.3f deg, residual %.4f mm rms over %d points", (frame.scaleX - 1) * 100,
                (frame.scaleY - 1) * 100, frame.shearDegrees, frame.rotationDegrees,
                frame.rmsResidual, found.size());
        report.line("  The anchor drifted by up to %.4f mm between hops; each point was corrected "
                + "by the mean of the drift read before its hop and after it.", anchorDriftLimit);
        if (anchorDriftLimit > 0.1) {
            report.finding(Severity.Warning, "The machine drifted by up to %.3f mm between hops "
                    + "to the board's fiducials, at %.2f of its speed. Each point was corrected by "
                    + "the anchor read just before it, but a drift that happens during the hop "
                    + "itself cannot be taken out, so the frame's residual carries some of it.",
                    anchorDriftLimit, measureSpeedFactor);
        }
        MachineDiagnosticsResults.Datum datum = new MachineDiagnosticsResults.Datum(board.getName(),
                head.getId(), frame.scaleX, frame.scaleY, frame.shearDegrees,
                frame.rotationDegrees, frame.mirrored, frame.rmsResidual, found.size());

        // The longest baseline on its own, as a check on the fit.
        Found left = null, right = null;
        for (Found f : found) {
            if (f.dot.name.equals("FID6")) {
                left = f;
            }
            if (f.dot.name.equals("FID7")) {
                right = f;
            }
        }
        if (left != null && right != null) {
            double nominal = Math.hypot(right.dot.x - left.dot.x, right.dot.y - left.dot.y);
            double measured = Math.hypot(right.location.getX() - left.location.getX(),
                    right.location.getY() - left.location.getY());
            datum.setBaselineScaleX(measured / nominal);
            report.line("  %s to %s: %.4f mm for a nominal %.1f mm, scale %+.3f%%", left.dot.name,
                    right.dot.name, measured, nominal, (measured / nominal - 1) * 100);
        }
        String noise = againstNoiseFloor(camera, frame.rmsResidual);
        report.finding(Math.abs(frame.scaleX - 1) > DATUM_SCALE_TOLERANCE
                || Math.abs(frame.scaleY - 1) > DATUM_SCALE_TOLERANCE ? Severity.Warning : Severity.Info,
                "Against the board, a machine millimetre is %+.3f%% long in X and %+.3f%% in Y. "
                + "Over a 100 mm board that is %.3f mm and %.3f mm of placement error at the far "
                + "edge, which no fiducial check on a smaller board can see.",
                (frame.scaleX - 1) * 100, (frame.scaleY - 1) * 100,
                Math.abs(frame.scaleX - 1) * 100, Math.abs(frame.scaleY - 1) * 100);
        report.finding(Math.abs(frame.shearDegrees) > DATUM_SQUARENESS_TOLERANCE_DEGREES
                ? Severity.Warning : Severity.Info,
                "The Y axis leans %+.3f degrees towards +X from square. Over 100 mm of Y that "
                + "moves X by %.3f mm. The board's own fiducials are square to about 0.05 degrees, "
                + "so smaller angles than that are the board as much as the machine.",
                frame.shearDegrees, Math.abs(Math.tan(Math.toRadians(frame.shearDegrees))) * 100);
        report.finding(frame.rmsResidual > DATUM_RESIDUAL_TOLERANCE_MM ? Severity.Warning : Severity.Info,
                "What does not fit a straight frame: %.4f mm rms across the %d fiducials.%s A "
                + "residual this size is what no scale or squareness correction can remove.",
                frame.rmsResidual, found.size(), noise);

        // The discs: what the mask and the silkscreen are registered to, and how a bright and a
        // dark target of the same size fare with the same pipeline.
        for (DatumBoard.Dot disc : board.getDiscs()) {
            checkAborted();
            double[] p = frame.apply(disc.x, disc.y);
            Location predicted = new Location(LengthUnit.Millimeters, p[0], p[1], fiducial.getZ(), 0);
            try {
                Location drift = anchorDrift(machine, camera, xAxis, fiducial, configuredDiameter,
                        report, disc.name);
                Location target = predicted.add(drift.derive(null, null, 0.0, 0.0));
                MovableUtils.moveToLocationAtSafeZ(camera, target, measureSpeedFactor);
                camera.waitForCompletion(CompletionType.WaitForStillstand);
                Thread.sleep(machineSettleMs);
                // A 5 mm disc is wider than the fiducial pipeline's search window; the circle
                // detector is given the disc's own size and a window to match.
                Location upp = camera.getUnitsPerPixelAtZ().convertToUnits(LengthUnit.Millimeters);
                int diameterPixels = (int) Math.round(disc.diameterMm / upp.getX());
                List<Double> cx = new ArrayList<>();
                List<Double> cy = new ArrayList<>();
                camera.actuateLightBeforeCapture();
                try {
                    BufferedImage image = camera.lightSettleAndCapture();
                    long seen = fingerprint(image);
                    for (int f = 0; f < Math.max(1, framesPerPoint); f++) {
                        Circle circle = locateCircle(image, diameterPixels);
                        if (circle != null) {
                            cx.add(circle.x);
                            cy.add(circle.y);
                        }
                        if (f + 1 < framesPerPoint) {
                            image = freshFrame(camera, seen);
                            seen = fingerprint(image);
                        }
                    }
                }
                finally {
                    camera.actuateLightAfterCapture();
                }
                if (cx.isEmpty()) {
                    throw new Exception("no circle of " + diameterPixels + " px found");
                }
                Location at = VisionUtils.getPixelLocation(camera, camera,
                        MachineDiagnosticsMath.median(cx), MachineDiagnosticsMath.median(cy))
                        .convertToUnits(LengthUnit.Millimeters)
                        .subtract(drift.derive(null, null, 0.0, 0.0));
                double dx = at.getX() - predicted.getX();
                double dy = at.getY() - predicted.getY();
                report.line("  %s (%s): %+.4f, %+.4f mm from where the copper puts it, %d of %d "
                        + "frames", disc.name, disc.layer, dx, dy, cx.size(), framesPerPoint);
                if (Math.hypot(dx, dy) > 0.1) {
                    report.finding(Severity.Info, "The %s is registered %.3f mm from the copper: "
                            + "that is this board's %s-to-copper registration, and the reason the "
                            + "grid is not used for anything precise.", disc.name,
                            Math.hypot(dx, dy), disc.layer.toString().toLowerCase());
                }
            }
            catch (Exception e) {
                report.line("  %s (%s): not found (%s)", disc.name, disc.layer, e.getMessage());
                report.finding(Severity.Warning, "The %s, a %.0f mm %s target, was not found by "
                        + "the fiducial pipeline: the lighting or the pipeline is tuned to one "
                        + "polarity of target.", disc.name, disc.diameterMm,
                        disc.layer == DatumBoard.Layer.Silk ? "bright" : "dark-on-bright");
            }
        }

        // The camera's scale from two fiducials in one frame: the same detector as everything
        // else, a copper baseline, and the machine standing still. A second reading of what the
        // ruler reads, by a different route, for when the two routes disagree.
        try {
            measurePairScale(machine, report, camera, xAxis, fiducial, configuredDiameter, board,
                    frame, found, datum);
        }
        catch (AbortedException e) {
            throw e;
        }
        catch (Exception e) {
            Logger.warn(e, "Machine diagnostics: fiducial pair");
            report.line("  Fiducial pair: %s", e.getMessage());
        }

        // The ruler.
        try {
            measureRuler(machine, report, camera, board, frame, fiducial.getZ(), datum);
        }
        catch (AbortedException e) {
            throw e;
        }
        catch (Exception e) {
            Logger.warn(e, "Machine diagnostics: ruler");
            report.line("  Ruler: %s", e.getMessage());
            report.finding(Severity.Warning, "The ruler could not be read: %s", e.getMessage());
        }
        recordResults(TestGroup.DatumBoard, report, results -> {
            datum.setMillis(System.currentTimeMillis());
            datum.setGeneration(results.getCompensationGeneration());
            results.setDatum(datum);
            results.addDatumToHistory(datum);
        });
    }

    /**
     * Two fiducials in one frame. The camera stands between the anchor and each of the four
     * fiducials around it, both dots in view about 5.6 mm either side of the centre, and the
     * pixel distance between them against the 11.18 mm the copper says gives pixels per
     * millimetre with nothing moving. It is what the ruler measures, read through the circle
     * detector rather than through tick centroids; the field of view scan, which moves the
     * machine, cannot separate the camera's scale from the machine's on a machine whose short
     * moves scatter by tens of microns.
     */
    private void measurePairScale(ReferenceMachine machine, MachineDiagnosticsReport report,
            ReferenceCamera camera, ReferenceControllerAxis xAxis, Location fiducial,
            Length anchorDiameter, DatumBoard board, MachineDiagnosticsMath.Affine frame,
            List<Found> found, MachineDiagnosticsResults.Datum datum) throws Exception {
        Location upp = camera.getUnitsPerPixelAtZ().convertToUnits(LengthUnit.Millimeters);
        double halfWidthMm = camera.getWidth() * upp.getX() / 2;
        double halfHeightMm = camera.getHeight() * upp.getY() / 2;
        DatumBoard.Dot anchor = board.getAnchor();
        int dotPixels = (int) Math.round(anchor.diameterMm / upp.getX());
        List<Double> scales = new ArrayList<>();
        report.blank();
        report.line("Two fiducials in one frame, the machine standing still");
        for (DatumBoard.Dot other : board.getOrientationFiducials()) {
            checkAborted();
            double baseline = Math.hypot(other.x - anchor.x, other.y - anchor.y);
            // Where the two would fall in the image with the camera between them.
            double[] mid = { (anchor.x + other.x) / 2, (anchor.y + other.y) / 2 };
            double[] a = frame.apply(anchor.x, anchor.y);
            double[] b = frame.apply(other.x, other.y);
            double[] m = frame.apply(mid[0], mid[1]);
            if (Math.abs(a[0] - m[0]) > halfWidthMm * 0.8 || Math.abs(a[1] - m[1]) > halfHeightMm * 0.8) {
                report.line("  %s and %s do not both fit in the frame; skipped.", anchor.name, other.name);
                continue;
            }
            Location drift = anchorDrift(machine, camera, xAxis, fiducial, anchorDiameter, report,
                    anchor.name + " and " + other.name);
            Location at = new Location(LengthUnit.Millimeters, m[0], m[1], fiducial.getZ(), 0)
                    .add(drift.derive(null, null, 0.0, 0.0));
            MovableUtils.moveToLocationAtSafeZ(camera, at, measureSpeedFactor);
            camera.waitForCompletion(CompletionType.WaitForStillstand);
            Thread.sleep(machineSettleMs);
            org.openpnp.model.Point pa = VisionUtils.getLocationPixels(camera,
                    new Location(LengthUnit.Millimeters, a[0], a[1], fiducial.getZ(), 0)
                            .add(drift.derive(null, null, 0.0, 0.0)));
            org.openpnp.model.Point pb = VisionUtils.getLocationPixels(camera,
                    new Location(LengthUnit.Millimeters, b[0], b[1], fiducial.getZ(), 0)
                            .add(drift.derive(null, null, 0.0, 0.0)));
            List<Double> distances = new ArrayList<>();
            camera.actuateLightBeforeCapture();
            try {
                BufferedImage image = camera.lightSettleAndCapture();
                long seen = fingerprint(image);
                for (int f = 0; f < Math.max(1, framesPerPoint); f++) {
                    Circle ca = locateCircleAt(image, pa.x, pa.y, dotPixels);
                    Circle cb = locateCircleAt(image, pb.x, pb.y, dotPixels);
                    if (ca != null && cb != null) {
                        distances.add(Math.hypot(cb.x - ca.x, cb.y - ca.y));
                    }
                    if (f + 1 < framesPerPoint) {
                        image = freshFrame(camera, seen);
                        seen = fingerprint(image);
                    }
                }
            }
            finally {
                camera.actuateLightAfterCapture();
            }
            if (distances.isEmpty()) {
                report.line("  %s and %s: one of them was not found in the frame.", anchor.name, other.name);
                continue;
            }
            double pixels = MachineDiagnosticsMath.median(distances);
            double pixelsPerMm = pixels / baseline;
            double nominal = 1 / Math.hypot(upp.getX() * (other.x - anchor.x) / baseline,
                    upp.getY() * (other.y - anchor.y) / baseline);
            double scaleError = nominal / pixelsPerMm - 1;
            scales.add(scaleError);
            report.line("  %s to %s: %.1f px for %.3f mm, %.3f px per mm against %.3f from Units "
                    + "per Pixel; camera scale error %+.3f%%", anchor.name, other.name, pixels,
                    baseline, pixelsPerMm, nominal, scaleError * 100);
        }
        if (scales.isEmpty()) {
            return;
        }
        double median = MachineDiagnosticsMath.median(scales);
        Stats stats = MachineDiagnosticsMath.stats(scales);
        datum.setPairScaleError(median);
        report.line("  Units per Pixel against the fiducial pairs: %+.3f%% (%d pairs, %+.3f%% to %+.3f%%)",
                median * 100, scales.size(), stats.min * 100, stats.max * 100);
        report.finding(Math.abs(median) > SCALE_ERROR_TOLERANCE ? Severity.Warning : Severity.Info,
                "Two copper fiducials in one frame say Units per Pixel is off by %+.3f%%, from %d "
                + "pairs. The ruler is a second reading of the same thing by a different route; "
                + "where the two agree, that is the camera.", median * 100, scales.size());
        // Units per Pixel was calibrated by moving the machine, so it is in the machine's
        // millimetre. On a machine whose millimetre is short, a Units per Pixel that is "too
        // large" by the same fraction is exactly right for it: a fiducial a true millimetre off
        // centre reads as the number of machine millimetres that moves the camera a true
        // millimetre. Only what is left over is the camera's calibration.
        double machineScale = (frame.scaleX + frame.scaleY) / 2;
        double configuredOverTrue = 1 / (1 + median);
        double excess = configuredOverTrue / machineScale - 1;
        report.line("  Of that, %+.3f%% is the machine's own millimetre, which a Units per Pixel "
                + "calibrated by moving the machine is meant to carry; %+.3f%% is the camera's "
                + "calibration proper.", (machineScale - 1) * 100, excess * 100);
        report.finding(Math.abs(excess) > SCALE_ERROR_TOLERANCE ? Severity.Warning : Severity.Info,
                "Units per Pixel is in the machine's millimetre, as it should be, and %+.3f%% beyond "
                + "it. A vision correction of a millimetre therefore moves the machine %.4f mm of "
                + "true travel; the rest of the difference is the machine's scale and is right "
                + "for every move it makes.", excess * 100, 1 + excess);
    }

    /** A circle of about the given diameter near a given pixel position. */
    private Circle locateCircleAt(BufferedImage image, double expectedX, double expectedY,
            int diameterPixels) {
        Mat mat = OpenCvUtils.toMat(image);
        try {
            int minDiameter = Math.max(3, (int) (diameterPixels / 1.5));
            int maxDiameter = Math.max(minDiameter + 4, (int) (diameterPixels * 1.5));
            int search = Math.min(Math.min(image.getWidth(), image.getHeight()),
                    Math.max(64, maxDiameter * 3));
            List<Circle> circles = DetectCircularSymmetry.findCircularSymmetry(mat,
                    (int) Math.round(expectedX), (int) Math.round(expectedY), minDiameter,
                    maxDiameter, search, search, search, 1, 1.2, 0.0, 4, 8,
                    DetectCircularSymmetry.SymmetryScore.OverallVarianceVsRingVarianceSum,
                    false, false, new ScoreRange());
            return circles.isEmpty() ? null : circles.get(0);
        }
        catch (Exception e) {
            Logger.trace(e, "Machine diagnostics: no circle near {}, {}", expectedX, expectedY);
            return null;
        }
        finally {
            mat.release();
        }
    }

    private MachineDiagnosticsMath.Affine fit(List<Found> found) {
        double[][] from = new double[found.size()][];
        double[][] to = new double[found.size()][];
        for (int i = 0; i < found.size(); i++) {
            from[i] = new double[] { found.get(i).dot.x, found.get(i).dot.y };
            to[i] = new double[] { found.get(i).location.getX(), found.get(i).location.getY() };
        }
        return MachineDiagnosticsMath.affineFit(from, to);
    }

    /** Where the anchor was first seen this run, and the largest drift read against it. */
    private Location anchorReference;
    private double anchorDriftLimit;
    /** Every drift read against the anchor this run, in the order read. */
    private final List<Location> anchorReads = new ArrayList<>();

    /**
     * Read the anchor again and return how far it has moved in the camera since the first
     * reading: the machine's drift at this moment, to be taken off whatever is measured next.
     */
    private Location anchorDrift(ReferenceMachine machine, ReferenceCamera camera,
            ReferenceControllerAxis xAxis, Location fiducial, Length diameter,
            MachineDiagnosticsReport report, String before) throws Exception {
        Detection seen = acquire(machine, camera, xAxis, fiducial, diameter,
                "anchor before " + before, report);
        Location drift = seen.location.convertToUnits(LengthUnit.Millimeters).subtract(anchorReference);
        double off = Math.hypot(drift.getX(), drift.getY());
        anchorDriftLimit = Math.max(anchorDriftLimit, off);
        anchorReads.add(drift);
        if (off > 0.05) {
            log("Anchor drifted %.4f mm (%+.4f, %+.4f) before %s; taken off the measurement",
                    off, drift.getX(), drift.getY(), before);
        }
        return drift;
    }

    /**
     * Go to where a fiducial is predicted and look for it; null if it is not there. The anchor
     * is read first and its drift taken off what is found.
     */
    private Found lookFor(ReferenceMachine machine, ReferenceCamera camera,
            ReferenceControllerAxis xAxis, Location fiducial, Length anchorDiameter,
            MachineDiagnosticsReport report, DatumBoard.Dot dot, Location predicted,
            double extraSearch) throws Exception {
        Location drift = anchorDrift(machine, camera, xAxis, fiducial, anchorDiameter, report, dot.name);
        int readIndex = anchorReads.size() - 1;
        Location target = predicted.add(drift.derive(null, null, 0.0, 0.0));
        approachFromCorner(camera, target, measureSpeedFactor);
        try {
            Detection seen = detect(machine, camera, target,
                    new Length(dot.diameterMm, LengthUnit.Millimeters), dot.name, framesPerPoint,
                    extraSearch);
            return new Found(dot, seen.location.convertToUnits(LengthUnit.Millimeters), seen,
                    readIndex, drift);
        }
        catch (AbortedException e) {
            throw e;
        }
        catch (Exception e) {
            Logger.trace(e, "Machine diagnostics: {} not at {}", dot.name, target);
            return null;
        }
    }

    /**
     * The ruler twice over. In one frame, the tick spacing in pixels against the 1.000 mm the
     * copper says it is gives the camera's scale with the machine standing still. Then the
     * camera steps along the ruler in quarter millimetres and, in every frame, reads where the
     * ticks nearest the centre are against where the machine says it is: that is the machine's
     * position error along 30 mm at a resolution fine enough to see the 2 mm pitch of a GT2
     * belt, which one fiducial can never show.
     */
    private void measureRuler(ReferenceMachine machine, MachineDiagnosticsReport report,
            ReferenceCamera camera, DatumBoard board, MachineDiagnosticsMath.Affine frame, double z,
            MachineDiagnosticsResults.Datum datum) throws Exception {
        DatumBoard.Ruler ruler = board.getRuler();
        double[] centre = ruler.centre();
        Location upp = camera.getUnitsPerPixelAtZ().convertToUnits(LengthUnit.Millimeters);
        double pixelsPerMm = 1 / upp.getX();
        report.blank();
        report.line("Ruler, %d ticks %.1f mm apart", ruler.ticks, ruler.pitchMm);

        // One frame at the ruler's centre.
        ReferenceHead head = requireHead(machine);
        Location fiducial = requireFiducial(head).convertToUnits(LengthUnit.Millimeters);
        Length anchorDiameter = head.getCalibrationPrimaryFiducialDiameter();
        ReferenceControllerAxis xAxis = findControllerAxis(camera, Axis.Type.X);
        Location driftBefore = anchorDrift(machine, camera, xAxis, fiducial, anchorDiameter, report,
                "the ruler");
        double[] c = frame.apply(centre[0], centre[1]);
        Location at = new Location(LengthUnit.Millimeters, c[0], c[1], z, 0)
                .add(driftBefore.derive(null, null, 0.0, 0.0));
        MovableUtils.moveToLocationAtSafeZ(camera, at, measureSpeedFactor);
        camera.waitForCompletion(CompletionType.WaitForStillstand);
        Thread.sleep(machineSettleMs);
        RulerFrame first = readRuler(camera, board, frame, driftBefore, z);
        double expected = pixelsPerMm * ruler.pitchMm;
        // Only ticks that sit a pitch from their neighbours are ticks; the silkscreen digits,
        // the mask edge and the anchor's ring stand in the same band and read as spikes. The
        // longest run of consistent spacing is the ruler.
        double[] chain = MachineDiagnosticsMath.consistentChain(first.ticks, expected, 0.15);
        if (chain.length < 6) {
            saveRulerEvidence(report, first, chain);
            throw new Exception("Only " + chain.length + " ticks a pitch apart were read in the "
                    + "frame (" + first.ticks.length + " features in the band).");
        }
        if (chain.length < first.ticks.length - 2) {
            report.line("  %d features in the band, %d of them a pitch apart; the rest are not "
                    + "ticks.", first.ticks.length, chain.length);
            saveRulerEvidence(report, first, chain);
        }
        double[] index = new double[chain.length];
        double running = 0;
        for (int i = 1; i < index.length; i++) {
            running += Math.max(1, Math.round((chain[i] - chain[i - 1]) / expected));
            index[i] = running;
        }
        first = new RulerFrame(chain, first.centrePixel);
        LinearFit spacing = MachineDiagnosticsMath.linearFit(index, chain);
        double measuredPixelsPerMm = spacing.slope / ruler.pitchMm;
        double cameraScaleError = pixelsPerMm / measuredPixelsPerMm - 1;
        double worst = 0;
        for (int i = 0; i < index.length; i++) {
            worst = Math.max(worst, Math.abs(first.ticks[i] - spacing.valueAt(index[i])));
        }
        double distortionMm = worst / measuredPixelsPerMm;
        datum.setCameraScaleErrorX(cameraScaleError);
        datum.setCameraDistortionMm(distortionMm);
        report.line("  %d ticks in one frame: %.3f px per mm against %.3f px per mm from Units "
                + "per Pixel; camera scale error %+.3f%%, worst tick %.4f mm off a straight "
                + "line", first.ticks.length, measuredPixelsPerMm, pixelsPerMm,
                cameraScaleError * 100, distortionMm);
        report.finding(Math.abs(cameraScaleError) > SCALE_ERROR_TOLERANCE ? Severity.Warning : Severity.Info,
                "With the machine standing still, the ruler says Units per Pixel in X is off by "
                + "%+.3f%%. This is the camera alone: the field of view scan measures the camera "
                + "against the machine's moves and cannot tell the two apart, this can.",
                cameraScaleError * 100);

        // Stepping along it, from the anchor's drift as read before the ruler to the drift read
        // after it: a drift during the stepping is spread along it.
        double step = Math.max(0.05, rulerStepMm);
        double half = (ruler.ticks - 1) * ruler.pitchMm / 2 - 1.0;
        List<Object[]> rows = new ArrayList<>();
        List<Double> positions = new ArrayList<>();
        List<Double> errors = new ArrayList<>();
        camera.actuateLightBeforeCapture();
        try {
            for (double x = -half; x <= half + 1e-9; x += step) {
                checkAborted();
                double[] p = frame.apply(centre[0] + x, centre[1]);
                Location target = new Location(LengthUnit.Millimeters, p[0], p[1], z, 0)
                        .add(driftBefore.derive(null, null, 0.0, 0.0));
                camera.moveTo(target, measureSpeedFactor);
                RulerFrame seen = readRuler(camera, board, frame, driftBefore, z);
                seen = new RulerFrame(MachineDiagnosticsMath.consistentChain(seen.ticks,
                        measuredPixelsPerMm * ruler.pitchMm, 0.15), seen.centrePixel);
                // Each tick near the centre says where the machine really is: its board
                // position is a whole pitch, and its offset from the image centre in pixels
                // is how far the camera is from it.
                List<Double> estimates = new ArrayList<>();
                for (double tick : seen.ticks) {
                    double offsetMm = (tick - seen.centrePixel) / measuredPixelsPerMm;
                    if (Math.abs(offsetMm) > 3.0) {
                        continue;
                    }
                    double boardX = x + offsetMm;
                    double nearest = Math.round(boardX / ruler.pitchMm) * ruler.pitchMm;
                    estimates.add(boardX - nearest);
                }
                if (estimates.isEmpty()) {
                    continue;
                }
                double error = MachineDiagnosticsMath.median(estimates);
                positions.add(x);
                errors.add(error);
                rows.add(new Object[] { x, error, estimates.size(), elapsed() });
            }
        }
        finally {
            camera.actuateLightAfterCapture();
        }
        report.writeCsv("ruler-steps.csv",
                new String[] { "board_x_mm", "error_mm", "ticks_used", "t_s" }, rows);
        if (positions.size() < 8) {
            throw new Exception("Too few readings along the ruler.");
        }
        Location driftAfter = anchorDrift(machine, camera, xAxis, fiducial, anchorDiameter, report,
                "the end of the ruler");
        double driftAlong = driftAfter.getX() - driftBefore.getX();
        report.line("  The anchor moved %+.4f mm in X while the ruler was stepped; spread along "
                + "the readings.", driftAlong);
        double[] xs = toArray(positions);
        double[] es = toArray(errors);
        for (int i = 0; i < es.length; i++) {
            es[i] -= driftAlong * i / Math.max(1, es.length - 1);
        }
        LinearFit trend = MachineDiagnosticsMath.linearFit(xs, es);
        double[] detrended = new double[es.length];
        for (int i = 0; i < es.length; i++) {
            detrended[i] = es[i] - trend.valueAt(xs[i]);
        }
        double period = 2.0;
        double[] periodic = MachineDiagnosticsMath.sinusoidFit(xs, detrended, period);
        Stats scatter = MachineDiagnosticsMath.stats(errors);
        datum.setRulerScaleErrorX(trend.slope);
        datum.setPeriodic(periodic[0], period);
        report.line("  %d readings along %.0f mm: machine scale %+.3f%% over the ruler, error "
                + "range %.4f mm, %.4f mm amplitude at a %.1f mm period", positions.size(),
                2 * half, trend.slope * 100, scatter.getRange(), periodic[0], period);
        report.finding(periodic[0] > PERIODIC_ERROR_TOLERANCE_MM ? Severity.Warning : Severity.Info,
                "Stepping along the ruler, the X position error repeats every %.1f mm with %.4f mm "
                + "of amplitude. A 2 mm period is the pitch of a GT2 belt: tooth engagement, or "
                + "a pulley that is not round.", period, periodic[0]);
    }

    /** The ticks of the ruler as read in one frame, in pixels along the ruler's direction. */
    private static final class RulerFrame {
        final double[] ticks;
        final double centrePixel;
        /** The band's intensity profile and the frame it came from, kept for when it fails. */
        final double[] profile;
        final BufferedImage image;
        final int rowFrom, rowTo;

        RulerFrame(double[] ticks, double centrePixel) {
            this(ticks, centrePixel, null, null, 0, 0);
        }

        RulerFrame(double[] ticks, double centrePixel, double[] profile, BufferedImage image,
                int rowFrom, int rowTo) {
            this.ticks = ticks;
            this.centrePixel = centrePixel;
            this.profile = profile;
            this.image = image;
            this.rowFrom = rowFrom;
            this.rowTo = rowTo;
        }
    }

    /**
     * When the ruler cannot be read, the evidence goes beside the report: the frame with the
     * band marked, and the band's profile with the features found in it. Six runs have failed
     * on "only 7 ticks a pitch apart" without anything to look at.
     */
    private void saveRulerEvidence(MachineDiagnosticsReport report, RulerFrame frame, double[] chain)
            throws Exception {
        if (frame.image == null || frame.profile == null) {
            return;
        }
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < frame.profile.length; i++) {
            rows.add(new Object[] { i, frame.profile[i] });
        }
        report.writeCsv("ruler-profile.csv", new String[] { "column_px", "band_mean" }, rows);
        List<Object[]> featureRows = new ArrayList<>();
        for (double t : frame.ticks) {
            boolean inChain = false;
            for (double c : chain) {
                inChain |= c == t;
            }
            featureRows.add(new Object[] { t, inChain ? 1 : 0 });
        }
        report.writeCsv("ruler-features.csv", new String[] { "centre_px", "in_chain" }, featureRows);
        BufferedImage marked = new BufferedImage(frame.image.getWidth(), frame.image.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = marked.createGraphics();
        try {
            g.drawImage(frame.image, 0, 0, null);
            g.setColor(java.awt.Color.RED);
            g.drawRect(0, frame.rowFrom, marked.getWidth() - 1, Math.max(1, frame.rowTo - frame.rowFrom));
            g.setColor(java.awt.Color.YELLOW);
            for (double t : frame.ticks) {
                g.drawLine((int) Math.round(t), Math.max(0, frame.rowFrom - 30), (int) Math.round(t),
                        Math.min(marked.getHeight() - 1, frame.rowTo + 30));
            }
        }
        finally {
            g.dispose();
        }
        javax.imageio.ImageIO.write(marked, "png", new File(report.getDirectory(), "ruler-frame.png"));
        report.line("  The frame with the band and the features marked is ruler-frame.png beside "
                + "this report; the band's profile is ruler-profile.csv.");
    }

    /**
     * Read the ruler's ticks in the current frame. The image is turned so that the ruler runs
     * along its rows, the band the ticks stand in is summed column by column, and the columns
     * that stand out are the ticks.
     */
    private RulerFrame readRuler(ReferenceCamera camera, DatumBoard board,
            MachineDiagnosticsMath.Affine frame, Location drift, double z) throws Exception {
        DatumBoard.Ruler ruler = board.getRuler();
        BufferedImage image = camera.settleAndCapture();
        Location upp = camera.getUnitsPerPixelAtZ().convertToUnits(LengthUnit.Millimeters);
        // Where board points fall in the image, through the frame, the drift the camera was
        // placed with, and the camera's own transform, tells which way the ruler runs and
        // where its tick band is. The fourth real run had the camera placed 0.15 mm of drift
        // away from where the band was computed, and read the board's edge instead of ticks.
        double[] c = ruler.centre();
        org.openpnp.model.Point p0 = pixelOf(camera, frame, drift, c[0], c[1], z);
        org.openpnp.model.Point p1 = pixelOf(camera, frame, drift, c[0] + 1, c[1], z);
        double angle = Math.atan2(p1.y - p0.y, p1.x - p0.x);
        // The tick band: the shortest ticks stand a millimetre from the base, and the base is
        // the edge of the board, so the band keeps clear of both ends.
        double baseY = c[1] - ruler.tickLengthMm / 2;
        org.openpnp.model.Point base = pixelOf(camera, frame, drift, c[0], baseY + 0.15, z);
        org.openpnp.model.Point top = pixelOf(camera, frame, drift, c[0], baseY + 0.85, z);
        Mat mat = OpenCvUtils.toMat(image);
        Mat gray = new Mat();
        Mat turned = new Mat();
        try {
            if (mat.channels() > 1) {
                org.opencv.imgproc.Imgproc.cvtColor(mat, gray, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY);
            }
            else {
                gray = mat.clone();
            }
            org.opencv.core.Point pivot = new org.opencv.core.Point(image.getWidth() / 2.0,
                    image.getHeight() / 2.0);
            Mat rotation = org.opencv.imgproc.Imgproc.getRotationMatrix2D(pivot,
                    Math.toDegrees(angle), 1.0);
            org.opencv.imgproc.Imgproc.warpAffine(gray, turned, rotation, gray.size());
            double[] baseTurned = turn(rotation, base.x, base.y);
            double[] topTurned = turn(rotation, top.x, top.y);
            int rowFrom = (int) Math.max(0, Math.min(baseTurned[1], topTurned[1]));
            int rowTo = (int) Math.min(turned.rows() - 1, Math.max(baseTurned[1], topTurned[1]));
            if (rowTo - rowFrom < 3) {
                throw new Exception("The tick band is not in the frame.");
            }
            double[] profile = new double[turned.cols()];
            for (int col = 0; col < turned.cols(); col++) {
                double sum = 0;
                for (int row = rowFrom; row <= rowTo; row++) {
                    sum += turned.get(row, col)[0];
                }
                profile[col] = sum / (rowTo - rowFrom + 1);
            }
            int minWidth = Math.max(2, (int) (ruler.tickWidthMm / upp.getX() * 0.5));
            double[] ticks = MachineDiagnosticsMath.tickCentres(profile, minWidth);
            // The image centre is where the camera is; after turning about it, it stays put.
            BufferedImage turnedImage = OpenCvUtils.toBufferedImage(turned);
            return new RulerFrame(ticks, pivot.x, profile, turnedImage, rowFrom, rowTo);
        }
        finally {
            mat.release();
            gray.release();
            turned.release();
        }
    }

    private static org.openpnp.model.Point pixelOf(ReferenceCamera camera, MachineDiagnosticsMath.Affine frame,
            Location drift, double boardX, double boardY, double z) {
        double[] p = frame.apply(boardX, boardY);
        return VisionUtils.getLocationPixels(camera, new Location(LengthUnit.Millimeters,
                p[0] + drift.getX(), p[1] + drift.getY(), z, 0));
    }

    private static double[] turn(Mat rotation, double x, double y) {
        double[] r0 = rotation.get(0, 0);
        double[] r1 = rotation.get(0, 1);
        double[] r2 = rotation.get(0, 2);
        double[] s0 = rotation.get(1, 0);
        double[] s1 = rotation.get(1, 1);
        double[] s2 = rotation.get(1, 2);
        return new double[] { r0[0] * x + r1[0] * y + r2[0], s0[0] * x + s1[0] * y + s2[0] };
    }

    // Hysteresis along the travel: what the slack does with position.

    /** Fractions of the travel at which the hysteresis is read. */
    @Element(required = false)
    private String hysteresisPositions = "0.15, 0.32, 0.5, 0.68, 0.85";

    /** Smallest and largest round feature that will serve as a target, in millimetres. */
    @Attribute(required = false)
    private double hysteresisFeatureMinMm = 1.5;
    @Attribute(required = false)
    private double hysteresisFeatureMaxMm = 8.0;

    /**
     * Pitch of the table's hole lattice, millimetres; 0 to look for features without one. The
     * LumenPnP staging plate is a 15 mm checkerboard of 3.2 mm holes anchored on the datum
     * board's fiducial, so with the pitch known the hysteresis map goes straight to a hole
     * rather than hunting for one.
     */
    @Attribute(required = false)
    private double hysteresisLatticePitchMm = StagingPlate.PITCH_MM;

    public double getHysteresisLatticePitchMm() {
        return hysteresisLatticePitchMm;
    }

    public void setHysteresisLatticePitchMm(double hysteresisLatticePitchMm) {
        double old = this.hysteresisLatticePitchMm;
        this.hysteresisLatticePitchMm = hysteresisLatticePitchMm;
        firePropertyChange("hysteresisLatticePitchMm", old, hysteresisLatticePitchMm);
    }

    /**
     * Where to look for a round feature for a hysteresis reading wanted at a position: the
     * nearest lattice hole when the table has a lattice, else the position itself.
     */
    private Location hysteresisSpot(ReferenceMachine machine, Location fiducial, double wantedX,
            double wantedY, boolean stepInX) {
        Location wanted = fiducial.derive(wantedX, wantedY, null, null);
        if (!(hysteresisLatticePitchMm > 0)) {
            return wanted;
        }
        Location cameraHole = bottomCameraHole();
        double[] yRange = StagingPlate.fieldYRange(fiducial, cameraHole);
        if (wantedY < yRange[0] - StagingPlate.PITCH_MM || wantedY > yRange[1] + StagingPlate.PITCH_MM) {
            // Off the plate: nothing to find, and no point spending frames on it.
            return null;
        }
        Location hole = StagingPlate.nearestHole(fiducial, wantedX, wantedY, cameraHole, stepInX);
        return hole != null ? hole : wanted;
    }

    /**
     * The bottom camera's position, which is the plate's camera hole: the lattice has no holes
     * around it, and which side of the fiducial it is on says which way the plate lies. Null
     * when there is no fixed bottom camera.
     */
    private Location bottomCameraHole() {
        try {
            Camera bottom = VisionUtils.getBottomVisionCamera();
            if (bottom != null && bottom.getHead() == null) {
                return bottom.getLocation();
            }
        }
        catch (Exception e) {
            // No bottom camera; no hole to keep clear of.
        }
        return null;
    }

    /**
     * Places on the table with a round feature at them, as "x, y; x, y; ..." in machine
     * millimetres. When given, the hysteresis is read at these rather than at fractions of the
     * travel: the user knows where the holes are, and a table that does not reach the end of
     * the travel has no hole there to find.
     */
    @Element(required = false)
    private String hysteresisTargets = "";

    public String getHysteresisTargets() {
        return hysteresisTargets;
    }

    public void setHysteresisTargets(String hysteresisTargets) {
        String old = this.hysteresisTargets;
        this.hysteresisTargets = hysteresisTargets == null ? "" : hysteresisTargets;
        firePropertyChange("hysteresisTargets", old, this.hysteresisTargets);
    }

    /** The named targets, or empty. Anything that does not read as "x, y" is skipped. */
    static List<double[]> parseTargets(String text) {
        List<double[]> targets = new ArrayList<>();
        if (text == null) {
            return targets;
        }
        for (String pair : text.split(";")) {
            String[] parts = pair.trim().split("[,\\s]+");
            if (parts.length < 2) {
                continue;
            }
            try {
                targets.add(new double[] { Double.parseDouble(parts[0]), Double.parseDouble(parts[1]) });
            }
            catch (NumberFormatException e) {
                // Not a pair; skipped.
            }
        }
        return targets;
    }

    public String getHysteresisPositions() {
        return hysteresisPositions;
    }

    public void setHysteresisPositions(String hysteresisPositions) {
        String old = this.hysteresisPositions;
        this.hysteresisPositions = hysteresisPositions;
        firePropertyChange("hysteresisPositions", old, hysteresisPositions);
    }

    /**
     * The backlash the X/Y group measures is read at one point, the fiducial. Whether that slack
     * is play in the mechanism or the give of a belt cannot be told from one point; it can from
     * several, because a belt's stiffness changes with where the carriage is on it - least in
     * the middle of the travel, most at the ends - and play does not. This group reads the raw
     * backlash, and a short staircase for sticking, at several places along each axis, against
     * whatever round feature the table offers there: a hole in the staging plate, a screw head.
     * Backlash is a difference between two approaches to the same place, so the feature's
     * geometry does not have to be known, only found twice.
     */
    private void testHysteresisMap(ReferenceMachine machine, MachineDiagnosticsReport report)
            throws Exception {
        report.section("Hysteresis along the travel, against whatever round feature the table offers");
        ReferenceHead head = requireHead(machine);
        ReferenceCamera camera = requireDownLookingCamera(head);
        Location fiducial = requireFiducial(head).convertToUnits(LengthUnit.Millimeters);
        Length fiducialDiameter = head.getCalibrationPrimaryFiducialDiameter();
        if (hysteresisLatticePitchMm > 0) {
            Location cameraHole = bottomCameraHole();
            double[] yRange = StagingPlate.fieldYRange(fiducial, cameraHole);
            report.line("  The table's holes are taken to lie on a %.0f mm checkerboard anchored on "
                    + "the primary fiducial, with the nearest holes %.0f mm from it along X and Y; "
                    + "each reading goes to the lattice hole nearest the wanted position. The "
                    + "field runs the whole X travel and from Y %.0f to Y %.0f; positions outside "
                    + "that have no hole to read against.", hysteresisLatticePitchMm,
                    hysteresisLatticePitchMm, yRange[0], yRange[1]);
            List<Object[]> holeRows = new ArrayList<>();
            for (Location hole : StagingPlate.allHoles(fiducial, cameraHole)) {
                Location h = hole.convertToUnits(LengthUnit.Millimeters);
                holeRows.add(new Object[] { h.getX(), h.getY(), h.getX() - fiducial.getX(),
                        h.getY() - fiducial.getY() });
            }
            report.writeCsv("staging-plate-holes.csv", new String[] { "machine_x_mm", "machine_y_mm",
                    "from_fiducial_x_mm", "from_fiducial_y_mm" }, holeRows);
            report.line("  The plate's %d holes, in machine coordinates, are in staging-plate-holes.csv.",
                    holeRows.size());
        }
        double[] fractions = MachineDiagnosticsMath.parseSeries(hysteresisPositions);
        List<Object[]> rows = new ArrayList<>();
        List<MachineDiagnosticsResults.Hysteresis> conclusions = new ArrayList<>();
        ReferenceControllerAxis approachAxis = findControllerAxis(camera, Axis.Type.X);
        acquire(machine, camera, approachAxis, fiducial, fiducialDiameter, "Hysteresis map start", report);
        List<BacklashSetting> saved = suspendBacklashCompensation(machine, Axis.Type.X, Axis.Type.Y);
        try {
            for (Axis.Type type : new Axis.Type[] { Axis.Type.X, Axis.Type.Y }) {
                ReferenceControllerAxis axis = findControllerAxis(camera, type);
                if (axis == null || !axis.isSoftLimitLowEnabled() || !axis.isSoftLimitHighEnabled()) {
                    report.line("  %s: no soft limits to span, skipped.", type);
                    continue;
                }
                double low = axis.getSoftLimitLow().convertToUnits(LengthUnit.Millimeters).getValue();
                double high = axis.getSoftLimitHigh().convertToUnits(LengthUnit.Millimeters).getValue();
                report.blank();
                report.line("Axis %s, travel %.0f to %.0f mm", axis.getName(), low, high);
                report.line("  %-10s %-12s %-10s %-12s %-8s", "position", "backlash mm", "sd mm",
                        "largest step", "stalled");
                List<double[]> targets = parseTargets(hysteresisTargets);
                if (!targets.isEmpty()) {
                    // Where the user says the holes are, in the order along this axis.
                    targets.sort((a, b) -> Double.compare(type == Axis.Type.X ? a[0] : a[1],
                            type == Axis.Type.X ? b[0] : b[1]));
                    for (double[] target : targets) {
                        checkAborted();
                        double position = type == Axis.Type.X ? target[0] : target[1];
                        Location here = fiducial.derive(target[0], target[1], null, null);
                        HysteresisSample sample = sampleHysteresis(machine, report, camera, axis, type,
                                here, (position - low) / (high - low), rows);
                        if (sample != null) {
                            conclusions.add(sample.conclusion);
                        }
                    }
                }
                else {
                    // The readings are spread over the travel - or, when the plate's holes are
                    // what they are read against and the plate does not reach the end of the
                    // travel, over the part of the travel the plate's holes cover: five readings
                    // over 210 mm of Y say more about the belt than two.
                    double spanLow = low;
                    double spanHigh = high;
                    if (hysteresisLatticePitchMm > 0 && type == Axis.Type.Y) {
                        double[] yRange = StagingPlate.fieldYRange(fiducial, bottomCameraHole());
                        spanLow = Math.max(low, yRange[0]);
                        spanHigh = Math.min(high, yRange[1]);
                        report.line("  The plate's holes cover Y %.0f to %.0f of the travel; the "
                                + "readings are spread over that.", spanLow, spanHigh);
                    }
                    for (double fraction : fractions) {
                        checkAborted();
                        // The camera's own axis coordinate is what is placed; the head offsets
                        // between the camera and the axis are a constant and cancel in a difference.
                        double position = spanLow + fraction * (spanHigh - spanLow);
                        // A reading of X keeps its X and may step a row; a reading of Y keeps its Y.
                        Location here = type == Axis.Type.X
                                ? hysteresisSpot(machine, fiducial, position, fiducial.getY(), false)
                                : hysteresisSpot(machine, fiducial, fiducial.getX(), position, true);
                        if (here == null) {
                            report.line("  %-10.1f off the plate's hole field; skipped", position);
                            continue;
                        }
                        HysteresisSample sample = sampleHysteresis(machine, report, camera, axis, type,
                                here, (position - low) / (high - low), rows);
                        if (sample != null) {
                            conclusions.add(sample.conclusion);
                        }
                    }
                }
                describeHysteresis(report, axis, conclusions);
            }

            // Y across the gantry. The two Y belts, one at each end of the X rail, are one axis
            // to the controller; but the Y slack the camera sees at a given X is the two belts'
            // slack interpolated by where the camera is between them. Read along X, it says
            // whether the two belts match, and which is the softer. The user's machine has the
            // X motor riding on the left Y carriage and the left belt the looser of the two.
            ReferenceControllerAxis yAxis = findControllerAxis(camera, Axis.Type.Y);
            ReferenceControllerAxis xAxis = findControllerAxis(camera, Axis.Type.X);
            if (yAxis != null && xAxis != null && xAxis.isSoftLimitLowEnabled()
                    && xAxis.isSoftLimitHighEnabled()) {
                double lowX = xAxis.getSoftLimitLow().convertToUnits(LengthUnit.Millimeters).getValue();
                double highX = xAxis.getSoftLimitHigh().convertToUnits(LengthUnit.Millimeters).getValue();
                report.blank();
                report.line("Axis %s across the gantry: its slack read at several X positions, Y at %.0f",
                        yAxis.getName(), fiducial.getY());
                report.line("  %-10s %-12s %-10s %-12s %-8s", "x position", "backlash mm", "sd mm",
                        "largest step", "stalled");
                List<MachineDiagnosticsResults.Hysteresis> across = new ArrayList<>();
                List<Location> spots = new ArrayList<>();
                List<double[]> named = parseTargets(hysteresisTargets);
                if (!named.isEmpty()) {
                    named.sort((a, b) -> Double.compare(a[0], b[0]));
                    for (double[] target : named) {
                        spots.add(fiducial.derive(target[0], target[1], null, null));
                    }
                }
                else {
                    for (double fraction : new double[] { 0.15, 0.5, 0.85 }) {
                        spots.add(hysteresisSpot(machine, fiducial, lowX + fraction * (highX - lowX),
                                fiducial.getY(), true));
                    }
                }
                for (Location here : spots) {
                    checkAborted();
                    if (here == null) {
                        continue;
                    }
                    double fraction = (here.getX() - lowX) / (highX - lowX);
                    HysteresisSample sample = sampleHysteresis(machine, report, camera, yAxis,
                            Axis.Type.Y, here, fraction, rows);
                    if (sample != null) {
                        // The position that matters here is X, where along the gantry it was read.
                        MachineDiagnosticsResults.Hysteresis h = new MachineDiagnosticsResults.Hysteresis(
                                yAxis.getId(), sample.target.getX(), sample.conclusion.getBacklashMm(),
                                sample.conclusion.getBacklashSdMm(), sample.conclusion.getLargestJumpMm(),
                                sample.conclusion.getStalledSteps());
                        h.setAcrossGantry(true);
                        across.add(h);
                        conclusions.add(h);
                    }
                }
                describeGantry(report, yAxis, across, lowX, highX);
            }
        }
        finally {
            restoreBacklashCompensation(saved);
        }
        report.writeCsv("hysteresis-map.csv", new String[] { "axis", "position_mm", "fraction",
                "backlash_mm", "backlash_sd_mm", "pairs", "largest_step_mm", "stalled_steps",
                "steps", "t_s", "target_x_mm", "target_y_mm" }, rows);
        recordResults(TestGroup.HysteresisMap, report, results -> results.setHysteresis(conclusions));
    }

    /** One place's hysteresis: the feature used, and what was read there. */
    private static final class HysteresisSample {
        final Location target;
        final MachineDiagnosticsResults.Hysteresis conclusion;

        HysteresisSample(Location target, MachineDiagnosticsResults.Hysteresis conclusion) {
            this.target = target;
            this.conclusion = conclusion;
        }
    }

    /**
     * Read the raw backlash of one axis, and a short staircase, against a round feature found
     * near {@code here}. The table's holes are not on the line through the fiducial, so the
     * search spirals out from the spot, frame by frame, up to two frames away in each direction;
     * the fifth real run found something round at one of five places on X, looking only where
     * it was sent. Null when nothing round is within reach or the feature could not be read twice.
     */
    private HysteresisSample sampleHysteresis(ReferenceMachine machine, MachineDiagnosticsReport report,
            ReferenceCamera camera, ReferenceControllerAxis axis, Axis.Type type, Location here,
            double fraction, List<Object[]> rows) throws Exception {
        Location upp = camera.getUnitsPerPixelAtZ().convertToUnits(LengthUnit.Millimeters);
        int minPixels = (int) Math.max(6, hysteresisFeatureMinMm / upp.getX());
        int maxPixels = (int) Math.min(Math.min(camera.getWidth(), camera.getHeight()) * 0.6,
                hysteresisFeatureMaxMm / upp.getX());
        Location unit = unitLocation(type);
        double nominal = type == Axis.Type.X ? here.getX() : here.getY();
        Circle feature = null;
        double frameW = camera.getWidth() * upp.getX() * 0.8;
        double frameH = camera.getHeight() * upp.getY() * 0.8;
        int[][] spiral = { { 0, 0 }, { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { -1, 1 },
                { 1, -1 }, { -1, -1 }, { 2, 0 }, { -2, 0 }, { 0, 2 }, { 0, -2 }, { 2, 1 }, { 2, -1 },
                { -2, 1 }, { -2, -1 }, { 1, 2 }, { -1, 2 }, { 1, -2 }, { -1, -2 } };
        int spots = 0;
        for (int[] step : spiral) {
            checkAborted();
            Location spot = here.add(new Location(LengthUnit.Millimeters, step[0] * frameW,
                    step[1] * frameH, 0, 0));
            MovableUtils.moveToLocationAtSafeZ(camera, spot, measureSpeedFactor);
            camera.waitForCompletion(CompletionType.WaitForStillstand);
            Thread.sleep(machineSettleMs);
            // The first spot's frame is kept as evidence if nothing is found anywhere.
            feature = findAnyCircle(camera, minPixels, maxPixels, report,
                    spots == 0 ? String.format("hysteresis-%s-%.0f", axis.getName(), nominal) : null);
            spots++;
            if (feature != null) {
                break;
            }
        }
        if (feature == null) {
            report.line("  %-10.1f nothing round of %.1f to %.1f mm within two frames of the spot "
                    + "(%d frames looked at); skipped. The first frame is hysteresis-%s-%.0f.png "
                    + "beside this report.", nominal, hysteresisFeatureMinMm, hysteresisFeatureMaxMm,
                    spots, axis.getName(), nominal);
            return null;
        }
        Location target = VisionUtils.getPixelLocation(camera, camera, feature.x, feature.y)
                .convertToUnits(LengthUnit.Millimeters).derive(null, null, here.getZ(), null);
        // Where the feature really is along the axis is the position this row is for.
        double position = type == Axis.Type.X ? target.getX() : target.getY();
        report.line("  %-10.1f using a %.1f mm round feature at (%.2f, %.2f)", position,
                feature.diameter * upp.getX(), target.getX(), target.getY());
        Length diameter = new Length(feature.diameter * upp.getX(), LengthUnit.Millimeters);
        List<Double> pairs = new ArrayList<>();
        for (int repeat = 0; repeat < Math.max(1, backlashRepeats); repeat++) {
            checkAborted();
            approachFrom(camera, axis, target, -5, measureSpeedFactor);
            Detection fromMinus = tryDetect(machine, camera, target, diameter,
                    String.format("Hysteresis %s %.0f -", axis.getName(), position), 0.1);
            approachFrom(camera, axis, target, 5, measureSpeedFactor);
            Detection fromPlus = tryDetect(machine, camera, target, diameter,
                    String.format("Hysteresis %s %.0f +", axis.getName(), position), 0.1);
            if (fromMinus == null || fromPlus == null) {
                continue;
            }
            pairs.add(shortfallMm(fromMinus.location, target, unit)
                    - shortfallMm(fromPlus.location, target, unit));
        }
        if (pairs.isEmpty()) {
            report.line("  %-10.1f the feature was not found twice; skipped", position);
            return null;
        }
        Stats backlash = MachineDiagnosticsMath.stats(pairs);
        // A short staircase from the minus side: does it move when told to move a little.
        approachFrom(camera, axis, target, -2, measureSpeedFactor);
        Double previous = null;
        int stalled = 0;
        double largest = 0;
        int steps = 0;
        for (double offset = -0.05; offset <= 0.05 + 1e-9; offset += stepTestStepMm) {
            checkAborted();
            camera.moveTo(target.add(unit.multiply(offset, offset, 0, 0)), measureSpeedFactor);
            Detection seen = tryDetect(machine, camera, target, diameter, "Hysteresis staircase", 0.1);
            if (seen == null) {
                break;
            }
            double actual = offset - shortfallMm(seen.location, target, unit);
            if (previous != null) {
                double moved = actual - previous;
                if (Math.abs(moved) < stepTestStepMm * 0.25) {
                    stalled++;
                }
                largest = Math.max(largest, Math.abs(moved));
            }
            previous = actual;
            steps++;
        }
        rows.add(new Object[] { axis.getName(), position, fraction, backlash.mean, backlash.stdDev,
                pairs.size(), largest, stalled, steps, elapsed(), target.getX(), target.getY() });
        report.line("  %-10.1f %-12.4f %-10.4f %-12.4f %d of %d", position, backlash.mean,
                backlash.stdDev, largest, stalled, Math.max(0, steps - 1));
        log("%s at %.0f: backlash %.4f, largest step %.4f, %d stalled", axis.getName(), position,
                backlash.mean, largest, stalled);
        return new HysteresisSample(target, new MachineDiagnosticsResults.Hysteresis(axis.getId(),
                position, backlash.mean, backlash.stdDev, largest, stalled));
    }

    /**
     * The Y slack across the gantry, and what it says about the two belts. With the belts at
     * the two ends of the X travel, the slack read at X is the left belt's and the right belt's
     * in proportion to the camera's distance from each; a straight line through the readings,
     * carried to the ends, is each belt's own. Belts that match give a flat line; a slope says
     * which is the softer, and by how much.
     */
    private void describeGantry(MachineDiagnosticsReport report, ReferenceControllerAxis yAxis,
            List<MachineDiagnosticsResults.Hysteresis> across, double lowX, double highX) {
        if (across.size() < 2) {
            report.line("  Too few X positions read to compare the two Y belts.");
            return;
        }
        double[] xs = new double[across.size()];
        double[] bs = new double[across.size()];
        for (int i = 0; i < across.size(); i++) {
            xs[i] = across.get(i).getPositionMm();
            bs[i] = Math.abs(across.get(i).getBacklashMm());
        }
        LinearFit fit = MachineDiagnosticsMath.linearFit(xs, bs);
        double left = Math.max(0, fit.valueAt(lowX));
        double right = Math.max(0, fit.valueAt(highX));
        double sd = 0;
        for (MachineDiagnosticsResults.Hysteresis h : across) {
            sd = Math.max(sd, h.getBacklashSdMm());
        }
        report.line("  %s slack carried to the two ends of the X travel: %.4f mm at X %.0f (left "
                + "belt), %.4f mm at X %.0f (right belt); readings scatter by up to %.4f mm",
                yAxis.getName(), left, lowX, right, highX, sd);
        double difference = Math.abs(left - right);
        if (difference > Math.max(0.02, 3 * sd) && difference > 0.3 * Math.max(left, right)) {
            String softer = left > right ? "left" : "right";
            report.finding(Severity.Warning, "The two Y belts do not match: the %s belt gives %.4f mm "
                    + "against the other's %.4f mm. Under acceleration, and on every reversal, "
                    + "the gantry turns by the difference over its width, %.4f degrees, and every "
                    + "position along X carries a Y error that depends on where it is between the "
                    + "belts. No single Y backlash setting can follow that - the controller drives "
                    + "both belts with the same steps - so the lever is the tension of the %s "
                    + "belt, until the two read alike here.", softer, Math.max(left, right),
                    Math.min(left, right), Math.toDegrees(Math.atan2(difference, highX - lowX)),
                    softer);
        }
        else {
            report.finding(Severity.Info, "The two Y belts read alike: %.4f mm of slack at the left "
                    + "end and %.4f mm at the right, within what the readings scatter by. One Y "
                    + "backlash setting serves the whole gantry.", left, right);
        }
    }

    /**
     * What the backlash does along the axis, and what that says. A belt fixed at both ends, or
     * looped round the axis, is stiffest where one span is short and softest in the middle;
     * slack that follows that shape is the belt giving, and the lever is its tension. Slack
     * that is the same everywhere is play or friction, which tension does not reach.
     */
    private void describeHysteresis(MachineDiagnosticsReport report, ReferenceControllerAxis axis,
            List<MachineDiagnosticsResults.Hysteresis> all) {
        List<MachineDiagnosticsResults.Hysteresis> mine = new ArrayList<>();
        for (MachineDiagnosticsResults.Hysteresis h : all) {
            if (h.getAxisId().equals(axis.getId()) && !h.isAcrossGantry()) {
                mine.add(h);
            }
        }
        if (mine.size() < 3) {
            report.line("  Too few positions read on %s to say how the slack varies.", axis.getName());
            return;
        }
        List<Double> values = new ArrayList<>();
        double middle = 0;
        double ends = 0;
        int endCount = 0;
        for (int i = 0; i < mine.size(); i++) {
            double b = Math.abs(mine.get(i).getBacklashMm());
            values.add(b);
            if (i == 0 || i == mine.size() - 1) {
                ends += b;
                endCount++;
            }
            else if (i == mine.size() / 2) {
                middle = b;
            }
        }
        ends /= Math.max(1, endCount);
        Stats stats = MachineDiagnosticsMath.stats(values);
        double variation = stats.mean > 0 ? stats.getRange() / stats.mean : 0;
        double sticking = 0;
        for (MachineDiagnosticsResults.Hysteresis h : mine) {
            sticking = Math.max(sticking, h.getLargestJumpMm());
        }
        report.line("  %s: backlash %.4f to %.4f mm along the travel, middle %.4f, ends %.4f; "
                + "largest single step %.4f mm", axis.getName(), stats.min, stats.max, middle, ends,
                sticking);
        if (variation > 0.5 && middle > ends * 1.3) {
            report.finding(Severity.Warning, "Axis %s has %.4f mm of slack in the middle of its "
                    + "travel and %.4f mm at the ends. Slack that is largest where the belt spans "
                    + "are longest is the belt giving under the friction it has to overcome: the "
                    + "lever is belt tension, and it is low.", axis.getName(), middle, ends);
        }
        else if (variation < 0.3) {
            report.finding(Severity.Info, "Axis %s has %.4f mm of slack wherever it is measured "
                    + "(%.4f to %.4f). Slack that does not change along the belt is play in the "
                    + "mechanism or friction in the guides, not the belt giving; tension is not "
                    + "the lever for it.", axis.getName(), stats.mean, stats.min, stats.max);
        }
        else {
            report.finding(Severity.Info, "Axis %s has slack from %.4f to %.4f mm along the travel, "
                    + "neither flat nor peaked in the middle; part of it is the belt giving and "
                    + "part is play or friction.", axis.getName(), stats.min, stats.max);
        }
        if (sticking > stepTestStepMm * 3) {
            report.finding(Severity.Warning, "Asked to move %.3f mm at a time, axis %s moved in "
                    + "steps of up to %.4f mm: it sticks and breaks free. That is friction in the "
                    + "guides or a belt so tight that it loads the bearings, not slack.",
                    stepTestStepMm, axis.getName(), sticking);
        }
    }

    /**
     * The best round feature anywhere in the frame, between two diameters.
     * <p>
     * Two passes. The frame is thresholded both ways round and its contours taken; a contour
     * whose area says a diameter in range, whose perimeter says it is round, and which sits
     * clear of the frame's edge is a candidate, and the one nearest the frame's centre is taken.
     * The circular symmetry detector then refines that one in a window its own size. The first
     * version asked the symmetry detector to find the feature itself, and it looked in the
     * middle 2 mm of a 19 mm frame at a diameter range that took seconds a frame: it did not
     * find the table's holes and the user asked how it was looking for them. Fairly.
     *
     * @param evidence A file name beside the report to write the frame to when nothing is found,
     *                 with the contours that were considered marked; null for none.
     */
    private Circle findAnyCircle(ReferenceCamera camera, int minPixels, int maxPixels,
            MachineDiagnosticsReport report, String evidence) throws Exception {
        BufferedImage image = camera.lightSettleAndCapture();
        List<org.opencv.core.MatOfPoint> considered = new ArrayList<>();
        try {
            double[] best = bestRoundFeature(image, minPixels, maxPixels, considered);
            if (best == null) {
                if (evidence != null && report != null) {
                    saveFeatureEvidence(report, evidence, image, considered);
                }
                log("No round feature of %d to %d px in the frame (%d contours of that size, none "
                        + "round and clear of the edge)", minPixels, maxPixels, considered.size());
                return null;
            }
            Circle refined = refineCircleAt(image, best[0], best[1], (int) Math.round(best[2]));
            return refined != null ? refined : new Circle(best[0], best[1], best[2]);
        }
        finally {
            for (org.opencv.core.MatOfPoint contour : considered) {
                contour.release();
            }
        }
    }

    /**
     * The roundest thing nearest the middle of an image, between two diameters: its centre,
     * diameter and circularity, or null. The image is thresholded both ways round and its
     * contours taken; a contour whose area says a diameter in range, whose perimeter says it is
     * round (4 pi A / P squared over 0.7), whose bounding box is near square, and which sits
     * clear of the edge is a candidate.
     *
     * @param considered Every contour of a diameter in range is added, for the caller to draw.
     */
    static double[] bestRoundFeature(BufferedImage image, int minPixels, int maxPixels,
            List<org.opencv.core.MatOfPoint> considered) {
        Mat mat = OpenCvUtils.toMat(image);
        Mat gray = new Mat();
        Mat blurred = new Mat();
        Mat binary = new Mat();
        try {
            if (mat.channels() > 1) {
                org.opencv.imgproc.Imgproc.cvtColor(mat, gray, org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY);
            }
            else {
                gray = mat.clone();
            }
            org.opencv.imgproc.Imgproc.GaussianBlur(gray, blurred, new org.opencv.core.Size(5, 5), 0);
            double centreX = image.getWidth() / 2.0;
            double centreY = image.getHeight() / 2.0;
            double[] best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int polarity = 0; polarity < 2; polarity++) {
                org.opencv.imgproc.Imgproc.threshold(blurred, binary, 0, 255,
                        (polarity == 0 ? org.opencv.imgproc.Imgproc.THRESH_BINARY
                                : org.opencv.imgproc.Imgproc.THRESH_BINARY_INV)
                                | org.opencv.imgproc.Imgproc.THRESH_OTSU);
                List<org.opencv.core.MatOfPoint> contours = new ArrayList<>();
                Mat hierarchy = new Mat();
                org.opencv.imgproc.Imgproc.findContours(binary, contours, hierarchy,
                        org.opencv.imgproc.Imgproc.RETR_LIST,
                        org.opencv.imgproc.Imgproc.CHAIN_APPROX_SIMPLE);
                hierarchy.release();
                for (org.opencv.core.MatOfPoint contour : contours) {
                    double area = org.opencv.imgproc.Imgproc.contourArea(contour);
                    double diameter = 2 * Math.sqrt(area / Math.PI);
                    if (diameter < minPixels || diameter > maxPixels) {
                        contour.release();
                        continue;
                    }
                    considered.add(contour);
                    org.opencv.core.MatOfPoint2f points = new org.opencv.core.MatOfPoint2f(contour.toArray());
                    double perimeter = org.opencv.imgproc.Imgproc.arcLength(points, true);
                    // How much of its own enclosing circle the shape fills: a disc fills it, a
                    // square of the same area fills 64 % of it. Circularity alone lets a square
                    // through, at 0.785.
                    org.opencv.core.Point enclosingCentre = new org.opencv.core.Point();
                    float[] enclosingRadius = new float[1];
                    org.opencv.imgproc.Imgproc.minEnclosingCircle(points, enclosingCentre, enclosingRadius);
                    points.release();
                    double circularity = perimeter > 0 ? 4 * Math.PI * area / (perimeter * perimeter) : 0;
                    double fill = enclosingRadius[0] > 0
                            ? area / (Math.PI * enclosingRadius[0] * enclosingRadius[0]) : 0;
                    org.opencv.core.Rect box = org.opencv.imgproc.Imgproc.boundingRect(contour);
                    double aspect = (double) Math.min(box.width, box.height) / Math.max(box.width, box.height);
                    // Whole and a little clear of the edge is enough: the camera is centred on
                    // the feature before anything is measured against it.
                    double margin = Math.max(5, diameter * 0.1);
                    boolean clear = box.x > margin && box.y > margin
                            && box.x + box.width < image.getWidth() - margin
                            && box.y + box.height < image.getHeight() - margin;
                    if (circularity < 0.8 || fill < 0.85 || aspect < 0.85 || !clear) {
                        continue;
                    }
                    org.opencv.imgproc.Moments moments = org.opencv.imgproc.Imgproc.moments(contour);
                    double cx = moments.m10 / moments.m00;
                    double cy = moments.m01 / moments.m00;
                    double distance = Math.hypot(cx - centreX, cy - centreY);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = new double[] { cx, cy, diameter, circularity };
                    }
                }
            }
            return best;
        }
        finally {
            mat.release();
            gray.release();
            blurred.release();
            binary.release();
        }
    }

    /** The symmetry detector on one candidate, in a window its own size: fast, and sub-pixel. */
    private Circle refineCircleAt(BufferedImage image, double x, double y, int diameterPixels) {
        Mat mat = OpenCvUtils.toMat(image);
        try {
            int minDiameter = Math.max(3, (int) (diameterPixels * 0.8));
            int maxDiameter = Math.max(minDiameter + 4, (int) (diameterPixels * 1.25));
            int search = Math.max(24, diameterPixels / 2);
            List<Circle> circles = DetectCircularSymmetry.findCircularSymmetry(mat,
                    (int) Math.round(x), (int) Math.round(y), minDiameter, maxDiameter, search, search,
                    search, 1, 1.2, 0.0, 2, 4,
                    DetectCircularSymmetry.SymmetryScore.OverallVarianceVsRingVarianceSum,
                    false, false, new ScoreRange());
            return circles.isEmpty() ? null : circles.get(0);
        }
        catch (Exception e) {
            Logger.trace(e, "Machine diagnostics: the candidate at {}, {} did not refine", x, y);
            return null;
        }
        finally {
            mat.release();
        }
    }

    /** The frame in which nothing round was found, with what was considered drawn on it. */
    private void saveFeatureEvidence(MachineDiagnosticsReport report, String name, BufferedImage image,
            List<org.opencv.core.MatOfPoint> considered) {
        try {
            BufferedImage marked = new BufferedImage(image.getWidth(), image.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = marked.createGraphics();
            try {
                g.drawImage(image, 0, 0, null);
                g.setColor(java.awt.Color.YELLOW);
                for (org.opencv.core.MatOfPoint contour : considered) {
                    org.opencv.core.Rect box = org.opencv.imgproc.Imgproc.boundingRect(contour);
                    g.drawRect(box.x, box.y, box.width, box.height);
                }
            }
            finally {
                g.dispose();
            }
            javax.imageio.ImageIO.write(marked, "png", new File(report.getDirectory(), name + ".png"));
        }
        catch (Exception e) {
            Logger.trace(e, "Machine diagnostics: writing {}", name);
        }
    }

    // Z, read off the bottom camera's focus on the nozzle tip.

    /** The auto focus provider's edge score, which is what its focus curve is built from. */
    private static final class FocusScorer extends org.openpnp.machine.reference.camera.AutoFocusProvider {
        double score(BufferedImage image, int diameter) {
            return focusScore(image, diameter, null);
        }
    }

    /**
     * Z is the one axis the down-looking camera cannot see. The bottom camera can: the nozzle
     * tip comes into focus at one height, and where the sweep finds that height, approached
     * from above and from below, is where Z really stops. The spread of the heights found from
     * one side is the repeatability; the difference between the sides is the slack.
     */
    private void testZFocus(ReferenceMachine machine, MachineDiagnosticsReport report)
            throws Exception {
        report.section("Z, from where the nozzle tip comes into focus on the bottom camera");
        ReferenceHead head = requireHead(machine);
        if (head.getNozzles().isEmpty()) {
            throw new Exception("The head has no nozzle.");
        }
        Nozzle nozzle = head.getDefaultNozzle();
        if (nozzle.getNozzleTip() == null) {
            throw new Exception("Nozzle " + nozzle.getName() + " has no nozzle tip loaded.");
        }
        if (nozzle.getPart() != null) {
            throw new Exception("Nozzle " + nozzle.getName() + " is holding a part; the tip itself "
                    + "is what comes into focus.");
        }
        Camera bottom = VisionUtils.getBottomVisionCamera();
        if (!(bottom instanceof ReferenceCamera)) {
            throw new Exception("The machine has no bottom camera.");
        }
        ReferenceCamera camera = (ReferenceCamera) bottom;
        ReferenceControllerAxis zAxis = findControllerAxis(nozzle, Axis.Type.Z);
        if (zAxis == null) {
            throw new Exception("Nozzle " + nozzle.getName() + " has no controller Z axis.");
        }
        Location focus = camera.getLocation(nozzle).convertToUnits(LengthUnit.Millimeters);
        double upp = camera.getUnitsPerPixelAtZ().convertToUnits(LengthUnit.Millimeters).getX();
        int diameter = (int) Math.round(4.0 / upp);
        diameter = Math.min(Math.min(diameter, camera.getHeight() - 50), camera.getWidth() - 50);
        FocusScorer scorer = new FocusScorer();
        double range = Math.abs(focusRangeMm);
        double step = Math.max(0.005, Math.abs(focusStepMm));
        int repeatsEach = Math.max(1, focusRepeats);
        report.line("  Nozzle %s, %s at %.3f mm, sweep %.2f mm either side in %.3f mm steps, %d "
                + "sweeps from each side.", nozzle.getName(), camera.getName(), focus.getZ(), range,
                step, repeatsEach);

        MovableUtils.moveToLocationAtSafeZ(nozzle, focus.derive(null, null, Double.NaN, null));
        List<Object[]> rows = new ArrayList<>();
        List<Double> fromAbove = new ArrayList<>();
        List<Double> fromBelow = new ArrayList<>();
        // Where the peak actually is: the camera's configured height is where it should be,
        // and the first real run found it half a millimetre higher, at the edge of the sweep.
        // A coarse sweep finds it first. It may run further up, away from the glass, as far as
        // it likes; it never goes lower than the configured range allows.
        double centreZ = focus.getZ();
        // The raw slack, with Z compensation off: measured through it, the difference between
        // the sides was what the compensation left, and taking that for the offset compensated
        // too little.
        List<BacklashSetting> savedZ = suspendBacklashCompensation(machine, Axis.Type.Z);
        camera.actuateLightBeforeCapture();
        try {
            double lowest = focus.getZ() - range;
            double highest = focus.getZ() + range * 3;
            for (int pass = 0; pass < 3; pass++) {
                checkAborted();
                nozzle.moveTo(focus.derive(null, null, lowest - 1.0, null));
                List<Double> zs = new ArrayList<>();
                List<Double> scores = new ArrayList<>();
                double coarse = step * 2;
                for (double z = lowest; z <= highest + 1e-9; z += coarse) {
                    checkAborted();
                    nozzle.moveTo(focus.derive(null, null, z, null));
                    BufferedImage image = camera.settleAndCapture();
                    double score = scorer.score(image, diameter);
                    zs.add(z);
                    scores.add(score);
                    rows.add(new Object[] { -1, "coarse", z, score, elapsed() });
                }
                centreZ = MachineDiagnosticsMath.parabolicPeak(toArray(zs), toArray(scores));
                report.line("  coarse sweep %.3f to %.3f mm: sharpest at %.3f mm", lowest, highest,
                        centreZ);
                if (centreZ < highest - coarse) {
                    break;
                }
                // The peak is at the top of the sweep: look further up.
                lowest = highest - range;
                highest = highest + range * 3;
            }
            for (int repeat = 0; repeat < repeatsEach; repeat++) {
                for (int side = 0; side < 2; side++) {
                    checkAborted();
                    boolean above = side == 0;
                    // Start beyond the range on the approach side, so the first step already
                    // moves in the direction of the sweep and takes up the slack that way.
                    double low = Math.max(focus.getZ() - range, centreZ - range);
                    double high = centreZ + range;
                    double start = above ? high + 1.0 : low - 1.0;
                    nozzle.moveTo(focus.derive(null, null, start, null));
                    List<Double> zs = new ArrayList<>();
                    List<Double> scores = new ArrayList<>();
                    int steps = (int) Math.round((high - low) / step);
                    for (int i = 0; i <= steps; i++) {
                        checkAborted();
                        double z = above ? high - i * step : low + i * step;
                        nozzle.moveTo(focus.derive(null, null, z, null));
                        BufferedImage image = camera.settleAndCapture();
                        double score = scorer.score(image, diameter);
                        for (int f = 1; f < Math.max(1, framesPerPoint); f++) {
                            score += scorer.score(camera.capture(), diameter);
                        }
                        score /= Math.max(1, framesPerPoint);
                        zs.add(z);
                        scores.add(score);
                        rows.add(new Object[] { repeat, above ? "above" : "below", z, score,
                                elapsed() });
                    }
                    double peak = MachineDiagnosticsMath.parabolicPeak(toArray(zs), toArray(scores));
                    (above ? fromAbove : fromBelow).add(peak);
                    report.line("  sweep %d from %s: focus at %.4f mm", repeat + 1,
                            above ? "above" : "below", peak);
                    log("Z focus from %s: %.4f mm", above ? "above" : "below", peak);
                }
            }
        }
        finally {
            camera.actuateLightAfterCapture();
            restoreBacklashCompensation(savedZ);
            nozzle.moveToSafeZ();
        }
        report.writeCsv("z-focus.csv",
                new String[] { "sweep", "approach", "z_mm", "focus_score", "t_s" }, rows);
        Stats above = MachineDiagnosticsMath.stats(fromAbove);
        Stats below = MachineDiagnosticsMath.stats(fromBelow);
        List<Double> all = new ArrayList<>(fromAbove);
        all.addAll(fromBelow);
        Stats each = MachineDiagnosticsMath.stats(all);
        double backlash = above.mean - below.mean;
        double repeatability = Math.max(above.getRange(), below.getRange());
        double sd = Math.max(above.stdDev, below.stdDev);
        report.line("  from above: mean %.4f sd %.4f range %.4f; from below: mean %.4f sd %.4f "
                + "range %.4f", above.mean, above.stdDev, above.getRange(), below.mean,
                below.stdDev, below.getRange());
        report.line("  Z repeatability %.4f mm from one side, slack between the sides %.4f mm "
                + "with compensation switched off", repeatability, backlash);
        if (Math.abs(each.mean - focus.getZ()) > Z_BACKLASH_TOLERANCE_MM) {
            report.finding(Severity.Warning, "The nozzle tip is sharpest %.3f mm %s the height the "
                    + "bottom camera is set to. Bottom vision images every part %.3f mm out of "
                    + "focus by that setting; the camera's location Z wants to be %.3f mm.",
                    Math.abs(each.mean - focus.getZ()), each.mean > focus.getZ() ? "above" : "below",
                    Math.abs(each.mean - focus.getZ()), each.mean);
        }
        report.finding(repeatability > Z_BACKLASH_TOLERANCE_MM ? Severity.Warning : Severity.Info,
                "Z on nozzle %s stops within %.4f mm of itself approaching from one side (sd %.4f "
                + "mm), and %.4f mm apart between coming down and coming up; the tip comes into "
                + "focus at %.3f mm against the %.3f mm the camera is set to.", nozzle.getName(),
                repeatability, sd, backlash, each.mean, focus.getZ());
        if (Math.abs(backlash) > Z_BACKLASH_TOLERANCE_MM) {
            Length offset = zAxis.getBacklashOffset().convertToUnits(LengthUnit.Millimeters);
            report.finding(Severity.Warning, "Axis %s has %.4f mm of slack between the two "
                    + "directions, with %s compensation and an offset of %.4f mm. A placement's "
                    + "height is off by that much depending on which way Z last moved.",
                    zAxis.getName(), Math.abs(backlash), zAxis.getBacklashCompensationMethod(),
                    offset.getValue());
        }
        MachineDiagnosticsResults.ZFocus conclusion = new MachineDiagnosticsResults.ZFocus(
                zAxis.getId(), camera.getId(), each.mean, sd, repeatability, backlash, repeatsEach);
        recordResults(TestGroup.ZFocus, report, results -> {
            List<MachineDiagnosticsResults.ZFocus> kept = new ArrayList<>();
            for (MachineDiagnosticsResults.ZFocus other : results.getZFocus()) {
                if (!other.getAxisId().equals(zAxis.getId())) {
                    kept.add(other);
                }
            }
            kept.add(conclusion);
            results.setZFocus(kept);
        });
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
        List<Object[]> rawRows = new ArrayList<>();
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
                    double plusAngle = measureAngleApproachedFrom(bottomVision, settings, camera,
                            nozzle, part, angle, rotationApproachAngle);
                    fromPlus.add(plusAngle);
                    rawRows.add(new Object[] { angle, repeat, "+", plusAngle, elapsed() });
                    double minusAngle = measureAngleApproachedFrom(bottomVision, settings, camera,
                            nozzle, part, angle, -rotationApproachAngle);
                    fromMinus.add(minusAngle);
                    rawRows.add(new Object[] { angle, repeat, "-", minusAngle, elapsed() });
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
            report.writeCsv("rotation-backlash-raw.csv", new String[] { "angle_deg", "repeat",
                    "approach", "measured_deg", "t_s" }, rawRows);
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

    /**
     * Approach a point from below on both axes at once: pre-position 10 mm to -X and -Y of it
     * at safe Z, then move onto it. Every reference read uses this, so that the direction the
     * axes last moved in is the same for every read; the sixth real run showed the anchor
     * "drifting" 0.10 mm in Y each time it was approached from the other side, which was the
     * Y backlash compensation's residual and not the machine at all.
     */
    private void approachFromCorner(HeadMountable movable, Location target, double speed)
            throws Exception {
        Location corner = target.add(new Location(LengthUnit.Millimeters, -10, -10, 0, 0)
                .convertToUnits(target.getUnits()));
        MovableUtils.moveToLocationAtSafeZ(movable, corner, speed);
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
