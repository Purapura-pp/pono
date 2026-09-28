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

package org.openpnp.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.openpnp.Translations;

/**
 * The calibration of a machine as a fixed catalogue of steps, in the order they are carried out.
 * <p>
 * Each step is one thing to calibrate, expanded over the elements it applies to: XY backlash for
 * each X and Y axis, the advanced calibration for each camera. An issue that one of these steps
 * resolves says which, through {@link Solutions.Issue#getCalibrationStep()}, and the calibration
 * page gathers them under the step.
 * <p>
 * The order is the order of the catalogue, and a step's prerequisites come before it.
 */
public enum CalibrationStep {
    // Preparation
    Home(Group.Prepare, false, true),
    SafeZ(Group.Prepare, true, false),
    SoftLimits(Group.Prepare, true, false),
    ManualNozzleTipChange(Group.Prepare, true, false),
    ControllerLimits(Group.Prepare, false, false),
    // Vision
    SubPixel(Group.Vision, false, false),
    Exposure(Group.Vision, true, false),
    PrimaryFiducial(Group.Vision, true, true),
    NozzleTouchPrimary(Group.Vision, true, false),
    SecondaryFiducial(Group.Vision, true, true),
    OtherNozzleOffsets(Group.Vision, true, false),
    OtherCameraOffsets(Group.Vision, false, true),
    BottomCamera(Group.Vision, true, true),
    CameraSettle(Group.Vision, false, true),
    // Motion
    VisualHoming(Group.Motion, false, true),
    XyBacklash(Group.Motion, false, true),
    FeedAcceleration(Group.Motion, false, true),
    ZBacklash(Group.Motion, false, true),
    RotationBacklash(Group.Motion, true, true),
    // The machine's frame
    FrameCompensation(Group.Frame, false, true),
    // Fine calibration
    AdvancedDownCamera(Group.Fine, false, true),
    AdvancedUpCamera(Group.Fine, true, true),
    NozzleTipCalibration(Group.Fine, false, true),
    PreciseNozzleOffsets(Group.Fine, true, true),
    // Measuring again what the steps changed
    Remeasure(Group.Recheck, false, true);

    /** The stages of the catalogue, as the calibration page groups its rows. */
    public enum Group {
        Prepare,
        Vision,
        Motion,
        Frame,
        Fine,
        Recheck;

        public String getName() {
            return Translations.getString("CalibrationStep.Group." + name()); //$NON-NLS-1$
        }
    }

    /** How a step is carried out, which says when it needs someone: the calibration page's "way" column. */
    public enum Way {
        /** It measures in phases on its own. */
        Auto,
        /** Someone gets the machine ready, and then it measures on its own. */
        Prepare,
        /** It stops part of the way through for someone. */
        Person,
        /** Someone moves the machine to a place, and the step records it. */
        Manual,
        /** It measures on its own, and again after each homing or nozzle tip change. */
        Recurring;

        public String getName() {
            return Translations.getString("CalibrationStep.Way." + name()); //$NON-NLS-1$
        }
    }

    /** One phase of a step, as the calibration page lists it while the step runs. */
    public static final class Phase {
        private final String key;
        private final boolean person;
        private final String group;

        private Phase(String key, boolean person, String group) {
            this.key = key;
            this.person = person;
            this.group = group;
        }

        public String getKey() {
            return key;
        }

        /** Whether someone does it. */
        public boolean isPerson() {
            return person;
        }

        /** The measurement group it runs, by name, or null. */
        public String getGroup() {
            return group;
        }

        public String getName() {
            if (group != null) {
                return String.format(Translations.getString("CalibrationStep.Phase.Group"), //$NON-NLS-1$
                        Translations.getString("MachineDiagnosticsWizard.Test." + group)); //$NON-NLS-1$
            }
            return Translations.getString("CalibrationStep.Phase." + key); //$NON-NLS-1$
        }
    }

    private static final Map<CalibrationStep, Way> WAYS = new EnumMap<>(CalibrationStep.class);
    private static final Map<CalibrationStep, List<Phase>> PHASES = new EnumMap<>(CalibrationStep.class);

    /** "P" a phase someone does, "G:Kinematics" one that runs a measurement group, anything else the step's own. */
    private static void define(CalibrationStep step, Way way, String... phases) {
        WAYS.put(step, way);
        List<Phase> list = new ArrayList<>();
        for (String phase : phases) {
            if (phase.startsWith("G:")) { //$NON-NLS-1$
                list.add(new Phase("Group." + phase.substring(2), false, phase.substring(2))); //$NON-NLS-1$
            }
            else if (phase.startsWith("P:")) { //$NON-NLS-1$
                list.add(new Phase(phase.substring(2), true, null));
            }
            else {
                list.add(new Phase(phase, false, null));
            }
        }
        PHASES.put(step, Collections.unmodifiableList(list));
    }

    static {
        define(Home, Way.Auto, "Home"); //$NON-NLS-1$
        define(SafeZ, Way.Manual, "P:Move", "Record"); //$NON-NLS-1$ //$NON-NLS-2$
        define(SoftLimits, Way.Manual, "P:Move", "Record"); //$NON-NLS-1$ //$NON-NLS-2$
        define(ManualNozzleTipChange, Way.Manual, "P:Move", "Record"); //$NON-NLS-1$ //$NON-NLS-2$
        define(ControllerLimits, Way.Auto, "G:Firmware", "Compare"); //$NON-NLS-1$ //$NON-NLS-2$
        define(SubPixel, Way.Auto, "Apply"); //$NON-NLS-1$
        define(Exposure, Way.Prepare, "P:Aim", "Capture", "Adjust"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        define(PrimaryFiducial, Way.Prepare, "P:Prepare", "Detect", "Moves", "Compute"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        define(NozzleTouchPrimary, Way.Manual, "P:Move", "Record"); //$NON-NLS-1$ //$NON-NLS-2$
        define(SecondaryFiducial, Way.Prepare, "P:Prepare", "Detect", "Moves", "Compute"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        define(OtherNozzleOffsets, Way.Manual, "P:Move", "Record"); //$NON-NLS-1$ //$NON-NLS-2$
        define(OtherCameraOffsets, Way.Auto, "Detect", "Moves", "Compute"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        define(BottomCamera, Way.Prepare, "P:Prepare", "Detect", "Moves", "Compute"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        define(CameraSettle, Way.Auto, "G:CameraSettle", "Apply"); //$NON-NLS-1$ //$NON-NLS-2$
        define(VisualHoming, Way.Auto, "Detect", "Record"); //$NON-NLS-1$ //$NON-NLS-2$
        define(XyBacklash, Way.Auto, "SpeedControl", "StepTest", "DistanceTest", "SpeedTest", "Decide", "Verify"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
        define(FeedAcceleration, Way.Auto, "G:Kinematics", "G:LostSteps", "Compute"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        define(ZBacklash, Way.Auto, "G:ZFocus", "Compute"); //$NON-NLS-1$ //$NON-NLS-2$
        define(RotationBacklash, Way.Prepare, "P:PickPart", "G:RotationBacklash", "Compute"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        define(FrameCompensation, Way.Auto, "G:DatumBoard", "Compensate", "Verify"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        define(AdvancedDownCamera, Way.Person, "P:Height1", "Walk1", "P:Height2", "Walk2", "Compute"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        define(AdvancedUpCamera, Way.Person, "P:Height1", "Walk1", "P:Height2", "Walk2", "Compute"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        define(NozzleTipCalibration, Way.Recurring, "Angles", "Fit", "Background"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        define(PreciseNozzleOffsets, Way.Prepare, "P:TestObject", "Angles", "Average"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        define(Remeasure, Way.Auto, "Remeasure"); //$NON-NLS-1$
    }

    private final Group group;
    private final boolean needsPerson;
    private final boolean movesMachine;

    CalibrationStep(Group group, boolean needsPerson, boolean movesMachine) {
        this.group = group;
        this.needsPerson = needsPerson;
        this.movesMachine = movesMachine;
    }

    public Group getGroup() {
        return group;
    }

    /**
     * Whether someone has to do something at the machine: jog to a fiducial, touch it with the
     * nozzle, place a test object. One-click calibration stops at these and says what to do.
     */
    public boolean isNeedsPerson() {
        return needsPerson;
    }

    public boolean isMovesMachine() {
        return movesMachine;
    }

    /** The step's name in the display language. */
    public String getName() {
        return Translations.getString("CalibrationStep." + name()); //$NON-NLS-1$
    }

    /** What doing it involves, for the step's description; how to do it for a step that needs a person. */
    public String getDescription() {
        return Translations.getString("CalibrationStep." + name() + ".Description"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public Way getWay() {
        return WAYS.get(this);
    }

    /** "全自动 · 多阶段", or "全自动" for a step done in one go. */
    public String getWayName() {
        return getWay() == Way.Auto && getPhases().size() == 1
                ? Translations.getString("CalibrationStep.Way.AutoOne") //$NON-NLS-1$
                : getWay().getName();
    }

    /** What it does, in the order it does it, the phases someone does marked. */
    public List<Phase> getPhases() {
        return PHASES.get(this);
    }

    /** The index of the phase with this key, or -1. */
    public int phaseOf(String key) {
        List<Phase> phases = getPhases();
        for (int i = 0; i < phases.size(); i++) {
            if (phases.get(i).getKey().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /** The index of the phase that runs the measurement group, or -1. */
    public int phaseOfGroup(String group) {
        List<Phase> phases = getPhases();
        for (int i = 0; i < phases.size(); i++) {
            if (group.equals(phases.get(i).getGroup())) {
                return i;
            }
        }
        return -1;
    }

    /** The index of the first phase someone does, or -1. */
    public int firstPersonPhase() {
        List<Phase> phases = getPhases();
        for (int i = 0; i < phases.size(); i++) {
            if (phases.get(i).isPerson()) {
                return i;
            }
        }
        return -1;
    }
}
