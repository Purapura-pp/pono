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

package org.openpnp.machine.reference.feeder.wizards;

import java.util.ArrayList;
import java.util.List;

import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.machine.reference.feeder.ReferencePushPullFeeder;
import org.openpnp.model.AxesLocation;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Axis;
import org.openpnp.spi.Head;
import org.openpnp.spi.Machine;
import org.openpnp.util.UiUtils;
import org.pmw.tinylog.Logger;

/**
 * A push-pull feeder's motion: the actuator and the five points it goes through, each with its
 * pause, whether the push, the multiple feed and the pull pass by it, and the speeds between.
 */
public final class PushPullMotionForm {
    private PushPullMotionForm() {
    }

    /** One of the five points, and what passes by it. */
    private static final class Point {
        final String location;
        final String title;
        final String delay;
        final String push;
        final String pushSpeed;
        final String multi;
        final String pull;
        final String pullSpeed;

        Point(String location, String title, String delay, String push, String pushSpeed, String multi, String pull,
                String pullSpeed) {
            this.location = location;
            this.title = title;
            this.delay = delay;
            this.push = push;
            this.pushSpeed = pushSpeed;
            this.multi = multi;
            this.pull = pull;
            this.pullSpeed = pullSpeed;
        }
    }

    private static final Point[] POINTS = {
            new Point("feedStartLocation", "PushPullMotionForm.Start", "delay0", null, null, "includedMulti0", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                    "includedPull0", "feedSpeedPull0"), //$NON-NLS-1$ //$NON-NLS-2$
            new Point("feedMid1Location", "PushPullMotionForm.Mid1", "delay1", "includedPush1", "feedSpeedPush1", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    "includedMulti1", "includedPull1", "feedSpeedPull1"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new Point("feedMid2Location", "PushPullMotionForm.Mid2", "delay2", "includedPush2", "feedSpeedPush2", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    "includedMulti2", "includedPull2", "feedSpeedPull2"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new Point("feedMid3Location", "PushPullMotionForm.Mid3", "delay3", "includedPush3", "feedSpeedPush3", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    "includedMulti3", "includedPull3", "feedSpeedPull3"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            new Point("feedEndLocation", "PushPullMotionForm.End", "delay4", "includedPushEnd", "feedSpeedPushEnd", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    "includedMultiEnd", null, null), //$NON-NLS-1$
    };

    public static FormWizard build(ReferencePushPullFeeder feeder) {
        List<Actuator> actuators = new ArrayList<>();
        actuators.add(null);
        Head head = null;
        try {
            head = feeder.getMachine().getDefaultHead();
            actuators.addAll(head.getActuators());
        }
        catch (Exception e) {
            Logger.error(e, "Cannot determine the default head of the machine."); //$NON-NLS-1$
        }
        Actuator pusher = feeder.getActuator();
        Form.Builder form = Form.of(feeder).named("PushPullMotionForm.Title") //$NON-NLS-1$
                .section("PushPullMotionForm.Actuators", "zap") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("actuator", "PushPullMotionForm.Actuator", actuators, null) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("actuator2", "PushPullMotionForm.PeelOff", actuators, null) //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("calibrateMotionX", "PushPullMotionForm.CalibrateX", "PushPullMotionForm.Calibrate.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .toggle("calibrateMotionY", "PushPullMotionForm.CalibrateY", "PushPullMotionForm.Calibrate.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .toggle("additiveRotation", "PushPullMotionForm.Additive", "PushPullMotionForm.Additive.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .action("PushPullMotionForm.ResetRotation", "rcw", () -> resetRotation(feeder)).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("additiveRotation", Boolean.TRUE::equals) //$NON-NLS-1$
                .hint("PushPullMotionForm.ResetRotation.Hint"); //$NON-NLS-1$
        for (Point point : POINTS) {
            form.section(point.title, "move") //$NON-NLS-1$
                    .location(point.location, "PushPullMotionForm.Location", true); //$NON-NLS-1$
            if (pusher != null) {
                form.locationButtons(pusher);
            }
            else {
                form.locationButtons();
            }
            form.integer(point.delay, "PushPullMotionForm.Pause").unit("ms").width(100); //$NON-NLS-1$ //$NON-NLS-2$
            if (point.push != null) {
                form.toggle(point.push, "PushPullMotionForm.Push", "PushPullMotionForm.Push.Note") //$NON-NLS-1$ //$NON-NLS-2$
                        .percent(point.pushSpeed, "PushPullMotionForm.PushSpeed").width(100); //$NON-NLS-1$
            }
            form.toggle(point.multi, "PushPullMotionForm.Multi", "PushPullMotionForm.Multi.Note"); //$NON-NLS-1$ //$NON-NLS-2$
            if (point.pull != null) {
                form.toggle(point.pull, "PushPullMotionForm.Pull", "PushPullMotionForm.Pull.Note") //$NON-NLS-1$ //$NON-NLS-2$
                        .percent(point.pullSpeed, "PushPullMotionForm.PullSpeed").width(100); //$NON-NLS-1$
            }
        }
        return form.build();
    }

    /**
     * The rotation axis set to zero where the actuator stands, so that the location buttons
     * capture and position additively from there.
     */
    private static void resetRotation(ReferencePushPullFeeder feeder) {
        Actuator actuator = feeder.getActuator();
        Machine machine = feeder.getMachine();
        if (actuator == null || actuator.getLocation().getRotation() == 0 || !machine.isEnabled()) {
            return;
        }
        UiUtils.submitUiMachineTask(() -> {
            AxesLocation rotation = actuator.toRaw(actuator.toHeadLocation(actuator.getLocation().multiply(1, 1, 1, 0)))
                    .byType(Axis.Type.Rotation);
            machine.getMotionPlanner().setGlobalOffsets(rotation);
        });
    }
}
