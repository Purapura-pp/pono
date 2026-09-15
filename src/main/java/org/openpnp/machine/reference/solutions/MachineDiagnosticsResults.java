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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.openpnp.machine.reference.solutions.MachineDiagnostics.TestGroup;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;

/**
 * What the last diagnostics run concluded, kept in machine.xml so that Issues and Solutions can
 * report on measurements taken minutes or months ago.
 * <p>
 * Conclusions only. The samples, the graphs and the fitted curves stay in the report directory,
 * which is where anyone reading the numbers goes; these are the handful of values a check needs
 * in order to say that a setting disagrees with the machine. They are keyed by the id of the axis
 * or camera they were measured on, because that is what an issue has to be about, and by id
 * rather than by name so that renaming an axis does not orphan its measurement.
 * <p>
 * A test group replaces its own conclusions as it finishes, and nothing else. A group that was
 * not selected, or that failed, leaves what it concluded last time alone. Replacing a group
 * wholesale rather than merging axis by axis is deliberate: it keeps {@link #getRun} the true age
 * of every conclusion the group holds, so no stale value can pass itself off as freshly measured.
 */
public class MachineDiagnosticsResults {
    /** What the controller reports as its own limits, from its settings report. */
    public static class ControllerLimits {
        @Attribute
        private String axisId;
        /** M92, steps per unit. */
        @Attribute(required = false)
        private Double stepsPerUnit;
        /** M203, maximum feed rate per second. */
        @Attribute(required = false)
        private Double maxFeedRate;
        /** M201, maximum acceleration per second squared. */
        @Attribute(required = false)
        private Double maxAcceleration;

        ControllerLimits() {
        }

        public ControllerLimits(String axisId, Double stepsPerUnit, Double maxFeedRate,
                Double maxAcceleration) {
            this.axisId = axisId;
            this.stepsPerUnit = stepsPerUnit;
            this.maxFeedRate = maxFeedRate;
            this.maxAcceleration = maxAcceleration;
        }

        public String getAxisId() {
            return axisId;
        }

        public Double getStepsPerUnit() {
            return stepsPerUnit;
        }

        public Double getMaxFeedRate() {
            return maxFeedRate;
        }

        public Double getMaxAcceleration() {
            return maxAcceleration;
        }
    }

    /** Acceleration, cruise velocity and fixed cost per move, fitted to measured move times. */
    public static class Motion {
        @Attribute
        private String axisId;
        @Attribute
        private double acceleration;
        @Attribute
        private double velocity;
        @Attribute
        private double overheadSeconds;
        /** "mm" or "deg": a rotation axis is timed in degrees. */
        @Attribute
        private String unit;

        Motion() {
        }

        public Motion(String axisId, double acceleration, double velocity, double overheadSeconds,
                String unit) {
            this.axisId = axisId;
            this.acceleration = acceleration;
            this.velocity = velocity;
            this.overheadSeconds = overheadSeconds;
            this.unit = unit;
        }

        public String getAxisId() {
            return axisId;
        }

        public double getAcceleration() {
            return acceleration;
        }

        public double getVelocity() {
            return velocity;
        }

        public double getOverheadSeconds() {
            return overheadSeconds;
        }

        public String getUnit() {
            return unit;
        }
    }

    /** How the machine lands on a point, per axis, with compensation switched off. */
    public static class Positioning {
        @Attribute
        private String axisId;
        @Attribute
        private double backlashMinMm;
        @Attribute
        private double backlashMaxMm;
        /**
         * The largest jump of a staircase that stalled and then caught up, which is the distance
         * the axis really moves in one go. Absent when the axis followed every commanded step.
         */
        @Attribute(required = false)
        private Double effectiveResolutionMm;

        Positioning() {
        }

        public Positioning(String axisId, double backlashMinMm, double backlashMaxMm,
                Double effectiveResolutionMm) {
            this.axisId = axisId;
            this.backlashMinMm = backlashMinMm;
            this.backlashMaxMm = backlashMaxMm;
            this.effectiveResolutionMm = effectiveResolutionMm;
        }

        public String getAxisId() {
            return axisId;
        }

        public double getBacklashMinMm() {
            return backlashMinMm;
        }

        public double getBacklashMaxMm() {
            return backlashMaxMm;
        }

        public Double getEffectiveResolutionMm() {
            return effectiveResolutionMm;
        }
    }

    /** The Units per Pixel scale error across the field of view, and the distortion left under it. */
    public static class FieldOfView {
        @Attribute
        private String cameraId;
        /** "X" or "Y", the image direction the scan was fitted along. */
        @Attribute
        private String axis;
        /** As a fraction: 0.01 is one percent too large a Units per Pixel. */
        @Attribute
        private double scaleError;
        @Attribute
        private double residualMm;

        FieldOfView() {
        }

        public FieldOfView(String cameraId, String axis, double scaleError, double residualMm) {
            this.cameraId = cameraId;
            this.axis = axis;
            this.scaleError = scaleError;
            this.residualMm = residualMm;
        }

        public String getCameraId() {
            return cameraId;
        }

        public String getAxis() {
            return axis;
        }

        public double getScaleError() {
            return scaleError;
        }

        public double getResidualMm() {
            return residualMm;
        }
    }

    /** How long the image took to stop moving, from the move that took longest to settle. */
    public static class Settling {
        @Attribute
        private String cameraId;
        @Attribute
        private double settleSeconds;
        @Attribute
        private double distanceMm;

        Settling() {
        }

        public Settling(String cameraId, double settleSeconds, double distanceMm) {
            this.cameraId = cameraId;
            this.settleSeconds = settleSeconds;
            this.distanceMm = distanceMm;
        }

        public String getCameraId() {
            return cameraId;
        }

        public double getSettleSeconds() {
            return settleSeconds;
        }

        public double getDistanceMm() {
            return distanceMm;
        }
    }

    /** How closely homing reproduces the origin, over several cycles. */
    public static class Homing {
        @Attribute
        private String headId;
        /** The worse of the X and Y spreads, which is what a job is exposed to. */
        @Attribute
        private double spreadMm;
        @Attribute
        private int cycles;

        Homing() {
        }

        public Homing(String headId, double spreadMm, int cycles) {
            this.headId = headId;
            this.spreadMm = spreadMm;
            this.cycles = cycles;
        }

        public String getHeadId() {
            return headId;
        }

        public double getSpreadMm() {
            return spreadMm;
        }

        public int getCycles() {
            return cycles;
        }
    }

    /** Rotation backlash, measured on the bottom camera. */
    public static class Rotation {
        @Attribute
        private String axisId;
        @Attribute
        private double backlashDegrees;

        Rotation() {
        }

        public Rotation(String axisId, double backlashDegrees) {
            this.axisId = axisId;
            this.backlashDegrees = backlashDegrees;
        }

        public String getAxisId() {
            return axisId;
        }

        public double getBacklashDegrees() {
            return backlashDegrees;
        }
    }

    /**
     * How much the detection of a standing fiducial scatters from frame to frame, which is the
     * floor under every position this camera measures: a machine cannot be shown to repeat better
     * than the camera can see.
     */
    public static class VisionNoise {
        @Attribute
        private String cameraId;
        @Attribute
        private double sdPixels;
        @Attribute
        private double sdMm;
        @Attribute
        private double rangePixels;
        @Attribute
        private int frames;
        @Attribute(required = false)
        private double framesPerSecond;

        VisionNoise() {
        }

        public VisionNoise(String cameraId, double sdPixels, double sdMm, double rangePixels,
                int frames, double framesPerSecond) {
            this.cameraId = cameraId;
            this.sdPixels = sdPixels;
            this.sdMm = sdMm;
            this.rangePixels = rangePixels;
            this.frames = frames;
            this.framesPerSecond = framesPerSecond;
        }

        public String getCameraId() {
            return cameraId;
        }

        public double getSdPixels() {
            return sdPixels;
        }

        public double getSdMm() {
            return sdMm;
        }

        public double getRangePixels() {
            return rangePixels;
        }

        public int getFrames() {
            return frames;
        }

        public double getFramesPerSecond() {
            return framesPerSecond;
        }
    }

    /**
     * How far an axis drifted from the fiducial after a stretch of fast travel: lost steps, or
     * slipping, per speed factor tried.
     */
    public static class LostSteps {
        @Attribute
        private String axisId;
        @Attribute
        private double speedFactor;
        @Attribute
        private double travelMm;
        @Attribute
        private double driftMm;
        @Attribute(required = false)
        private double feedRateAtTest;

        LostSteps() {
        }

        public LostSteps(String axisId, double speedFactor, double travelMm, double driftMm,
                double feedRateAtTest) {
            this.axisId = axisId;
            this.speedFactor = speedFactor;
            this.travelMm = travelMm;
            this.driftMm = driftMm;
            this.feedRateAtTest = feedRateAtTest;
        }

        public String getAxisId() {
            return axisId;
        }

        public double getSpeedFactor() {
            return speedFactor;
        }

        public double getTravelMm() {
            return travelMm;
        }

        public double getDriftMm() {
            return driftMm;
        }

        /** The feed rate the axis was planned with when this was measured. */
        public double getFeedRateAtTest() {
            return feedRateAtTest;
        }

        /** Drift per 1000 mm of travel, which is the figure a job's worth of moves multiplies. */
        public double getDriftPerMetre() {
            return travelMm > 0 ? driftMm * 1000 / travelMm : 0;
        }
    }

    /**
     * How long after the controller reports a move complete the camera is still delivering
     * frames of the motion: the delay of the camera's pipeline, which every settle wait must
     * exceed before the image it captures is even of the present.
     */
    public static class CameraLatency {
        @Attribute
        private String cameraId;
        @Attribute
        private double latencySeconds;
        @Attribute
        private double framesPerSecond;
        @Attribute
        private double bufferedFrames;

        CameraLatency() {
        }

        public CameraLatency(String cameraId, double latencySeconds, double framesPerSecond,
                double bufferedFrames) {
            this.cameraId = cameraId;
            this.latencySeconds = latencySeconds;
            this.framesPerSecond = framesPerSecond;
            this.bufferedFrames = bufferedFrames;
        }

        public String getCameraId() {
            return cameraId;
        }

        public double getLatencySeconds() {
            return latencySeconds;
        }

        public double getFramesPerSecond() {
            return framesPerSecond;
        }

        public double getBufferedFrames() {
            return bufferedFrames;
        }
    }

    /** When a test group last finished, and the report it wrote. */
    public static class Run {
        @Attribute
        private String group;
        @Attribute
        private long millis;
        @Attribute(required = false)
        private String reportDirectory;

        Run() {
        }

        public Run(String group, long millis, String reportDirectory) {
            this.group = group;
            this.millis = millis;
            this.reportDirectory = reportDirectory;
        }

        public String getGroup() {
            return group;
        }

        public long getMillis() {
            return millis;
        }

        public Date getWhen() {
            return new Date(millis);
        }

        public String getReportDirectory() {
            return reportDirectory;
        }
    }

    @ElementList(required = false)
    private ArrayList<ControllerLimits> controllerLimits = new ArrayList<>();

    @ElementList(required = false)
    private ArrayList<Motion> motion = new ArrayList<>();

    @ElementList(required = false)
    private ArrayList<Positioning> positioning = new ArrayList<>();

    @ElementList(required = false)
    private ArrayList<FieldOfView> fieldOfView = new ArrayList<>();

    @ElementList(required = false)
    private ArrayList<Settling> settling = new ArrayList<>();

    @Element(required = false)
    private Homing homing;

    @Element(required = false)
    private Rotation rotation;

    @ElementList(required = false)
    private ArrayList<VisionNoise> visionNoise = new ArrayList<>();

    @ElementList(required = false)
    private ArrayList<LostSteps> lostSteps = new ArrayList<>();

    @ElementList(required = false)
    private ArrayList<CameraLatency> cameraLatency = new ArrayList<>();

    @ElementList(required = false)
    private ArrayList<Run> runs = new ArrayList<>();

    public List<LostSteps> getLostSteps() {
        return lostSteps;
    }

    public void setLostSteps(List<LostSteps> lostSteps) {
        this.lostSteps = new ArrayList<>(lostSteps);
    }

    public List<CameraLatency> getCameraLatency() {
        return cameraLatency;
    }

    public void setCameraLatency(List<CameraLatency> cameraLatency) {
        this.cameraLatency = new ArrayList<>(cameraLatency);
    }

    /** The pipeline delay measured on a camera, or null if it was never measured. */
    public CameraLatency getCameraLatency(String cameraId) {
        for (CameraLatency latency : cameraLatency) {
            if (latency.getCameraId().equals(cameraId)) {
                return latency;
            }
        }
        return null;
    }

    public List<VisionNoise> getVisionNoise() {
        return visionNoise;
    }

    public void setVisionNoise(List<VisionNoise> visionNoise) {
        this.visionNoise = new ArrayList<>(visionNoise);
    }

    /** The noise floor measured on a camera, or null if it was never measured. */
    public VisionNoise getVisionNoise(String cameraId) {
        for (VisionNoise noise : visionNoise) {
            if (noise.getCameraId().equals(cameraId)) {
                return noise;
            }
        }
        return null;
    }

    public List<ControllerLimits> getControllerLimits() {
        return controllerLimits;
    }

    public void setControllerLimits(List<ControllerLimits> controllerLimits) {
        this.controllerLimits = new ArrayList<>(controllerLimits);
    }

    public List<Motion> getMotion() {
        return motion;
    }

    public void setMotion(List<Motion> motion) {
        this.motion = new ArrayList<>(motion);
    }

    public List<Positioning> getPositioning() {
        return positioning;
    }

    public void setPositioning(List<Positioning> positioning) {
        this.positioning = new ArrayList<>(positioning);
    }

    public List<FieldOfView> getFieldOfView() {
        return fieldOfView;
    }

    public void setFieldOfView(List<FieldOfView> fieldOfView) {
        this.fieldOfView = new ArrayList<>(fieldOfView);
    }

    public List<Settling> getSettling() {
        return settling;
    }

    public void setSettling(List<Settling> settling) {
        this.settling = new ArrayList<>(settling);
    }

    public Homing getHoming() {
        return homing;
    }

    public void setHoming(Homing homing) {
        this.homing = homing;
    }

    public Rotation getRotation() {
        return rotation;
    }

    public void setRotation(Rotation rotation) {
        this.rotation = rotation;
    }

    public List<Run> getRuns() {
        return runs;
    }

    /**
     * @return When the group last finished and what it wrote, or null if it has never run on this
     *         machine.
     */
    public Run getRun(TestGroup group) {
        for (Run run : runs) {
            if (run.getGroup().equals(group.name())) {
                return run;
            }
        }
        return null;
    }

    public void setRun(TestGroup group, long millis, String reportDirectory) {
        runs.removeIf(run -> run.getGroup().equals(group.name()));
        runs.add(new Run(group.name(), millis, reportDirectory));
    }
}
