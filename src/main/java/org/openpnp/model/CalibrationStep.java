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

import org.openpnp.Translations;

/**
 * The calibration of a machine as a fixed catalogue of steps, in the order they are carried out.
 * <p>
 * Each step is one thing to calibrate, expanded over the elements it applies to: XY backlash for
 * each X and Y axis, the advanced calibration for each camera. An issue that one of these steps
 * resolves says which, through {@link Solutions.Issue#getCalibrationStep()}; the calibration page
 * gathers them, and the issues page leaves them to it.
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
}
