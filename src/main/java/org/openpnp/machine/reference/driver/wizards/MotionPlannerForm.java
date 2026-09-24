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

package org.openpnp.machine.reference.driver.wizards;

import java.util.Locale;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.components.SimpleGraphView;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.form.WeakForward;
import org.openpnp.gui.shell.Tokens;
import org.openpnp.gui.shell.Ui;
import org.openpnp.machine.reference.driver.ReferenceAdvancedMotionPlanner;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.Location;
import org.openpnp.spi.HeadMountable;
import org.openpnp.util.SimpleGraph;
import org.openpnp.util.UiUtils;

/**
 * The advanced motion planner's two tabs: what it may do to go faster, with the test motion
 * it is tried on; and the test with the motion it planned drawn.
 */
public final class MotionPlannerForm {
    private MotionPlannerForm() {
    }

    public static class Bean extends AbstractModelObject {
        private final ReferenceAdvancedMotionPlanner planner;

        Bean(ReferenceAdvancedMotionPlanner planner) {
            this.planner = planner;
            // The planned and actual times and the graph arrive after each test.
            WeakForward.listen(planner, this, (bean, e) -> {
                bean.firePropertyChange(e.getPropertyName(), null, e.getNewValue());
                bean.firePropertyChange("moveTimes", null, bean.getMoveTimes()); //$NON-NLS-1$
            });
        }

        public boolean isAllowContinuousMotion() {
            return planner.isAllowContinuousMotion();
        }

        public void setAllowContinuousMotion(boolean allow) {
            planner.setAllowContinuousMotion(allow);
        }

        public boolean isAllowUncoordinated() {
            return planner.isAllowUncoordinated();
        }

        public void setAllowUncoordinated(boolean allow) {
            planner.setAllowUncoordinated(allow);
        }

        public boolean isFuseBacklashFinalApproach() {
            return planner.isFuseBacklashFinalApproach();
        }

        public void setFuseBacklashFinalApproach(boolean fuse) {
            planner.setFuseBacklashFinalApproach(fuse);
        }

        public boolean isInterpolationRetiming() {
            return planner.isInterpolationRetiming();
        }

        public void setInterpolationRetiming(boolean retiming) {
            planner.setInterpolationRetiming(retiming);
        }

        public double getMinimumSpeed() {
            return planner.getMinimumSpeed();
        }

        public void setMinimumSpeed(double speed) {
            planner.setMinimumSpeed(speed);
        }

        public boolean isStartLocationEnabled() {
            return planner.isStartLocationEnabled();
        }

        public void setStartLocationEnabled(boolean enabled) {
            planner.setStartLocationEnabled(enabled);
        }

        public Location getStartLocation() {
            return planner.getStartLocation();
        }

        public void setStartLocation(Location location) {
            planner.setStartLocation(location);
        }

        public double getToMid1Speed() {
            return planner.getToMid1Speed();
        }

        public void setToMid1Speed(double speed) {
            planner.setToMid1Speed(speed);
        }

        public boolean isToMid1SafeZ() {
            return planner.isToMid1SafeZ();
        }

        public void setToMid1SafeZ(boolean safeZ) {
            planner.setToMid1SafeZ(safeZ);
        }

        public boolean isMid1LocationEnabled() {
            return planner.isMid1LocationEnabled();
        }

        public void setMid1LocationEnabled(boolean enabled) {
            planner.setMid1LocationEnabled(enabled);
        }

        public Location getMidLocation1() {
            return planner.getMidLocation1();
        }

        public void setMidLocation1(Location location) {
            planner.setMidLocation1(location);
        }

        public double getToMid2Speed() {
            return planner.getToMid2Speed();
        }

        public void setToMid2Speed(double speed) {
            planner.setToMid2Speed(speed);
        }

        public boolean isToMid2SafeZ() {
            return planner.isToMid2SafeZ();
        }

        public void setToMid2SafeZ(boolean safeZ) {
            planner.setToMid2SafeZ(safeZ);
        }

        public boolean isMid2LocationEnabled() {
            return planner.isMid2LocationEnabled();
        }

        public void setMid2LocationEnabled(boolean enabled) {
            planner.setMid2LocationEnabled(enabled);
        }

        public Location getMidLocation2() {
            return planner.getMidLocation2();
        }

        public void setMidLocation2(Location location) {
            planner.setMidLocation2(location);
        }

        public double getToEndSpeed() {
            return planner.getToEndSpeed();
        }

        public void setToEndSpeed(double speed) {
            planner.setToEndSpeed(speed);
        }

        public boolean isToEndSafeZ() {
            return planner.isToEndSafeZ();
        }

        public void setToEndSafeZ(boolean safeZ) {
            planner.setToEndSafeZ(safeZ);
        }

        public boolean isEndLocationEnabled() {
            return planner.isEndLocationEnabled();
        }

        public void setEndLocationEnabled(boolean enabled) {
            planner.setEndLocationEnabled(enabled);
        }

        public Location getEndLocation() {
            return planner.getEndLocation();
        }

        public void setEndLocation(Location location) {
            planner.setEndLocation(location);
        }

        public boolean isDiagnosticsEnabled() {
            return planner.isDiagnosticsEnabled();
        }

        public void setDiagnosticsEnabled(boolean enabled) {
            planner.setDiagnosticsEnabled(enabled);
        }

        /** "0.842 s planned, 0.851 s taken", or that the interpolation failed; a dash before the first test. */
        public String getMoveTimes() {
            if (planner.isInterpolationFailed()) {
                return Translations.getString("ReferenceAdvancedMotionPlannerDiagnosticsWizard.InterpolationFailedLabel.text"); //$NON-NLS-1$
            }
            Double planned = planner.getMoveTimePlanned();
            Double actual = planner.getMoveTimeActual();
            if (planned == null && actual == null) {
                return null;
            }
            return String.format(Locale.US, Translations.getString("MotionPlannerForm.Times"), //$NON-NLS-1$
                    seconds(planned), seconds(actual));
        }

        private static String seconds(Double value) {
            return value == null ? "\u2014" : String.format(Locale.US, "%.3f", value); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    // ==== settings ==================================================================================

    public static FormWizard settings(ReferenceAdvancedMotionPlanner planner) {
        Form.Builder b = Form.of(new Bean(planner)).named("ReferenceAdvancedMotionPlanner.MotionPlanner.title") //$NON-NLS-1$
                .section("MotionPlannerForm.Planning", "sliders") //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("allowContinuousMotion", "MotionPlannerForm.Continuous", "MotionPlannerForm.Continuous.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("MotionPlannerForm.Continuous.Hint") //$NON-NLS-1$
                .toggle("allowUncoordinated", "MotionPlannerForm.Uncoordinated", "MotionPlannerForm.Uncoordinated.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("MotionPlannerForm.Uncoordinated.Hint") //$NON-NLS-1$
                .toggle("fuseBacklashFinalApproach", "MotionPlannerForm.FuseBacklash", "MotionPlannerForm.FuseBacklash.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("MotionPlannerForm.FuseBacklash.Hint") //$NON-NLS-1$
                .toggle("interpolationRetiming", "MotionPlannerForm.Retiming", "MotionPlannerForm.Retiming.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("MotionPlannerForm.Retiming.Hint") //$NON-NLS-1$
                .percent("minimumSpeed", "MotionPlannerForm.MinimumSpeed").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("MotionPlannerForm.MinimumSpeed.Hint") //$NON-NLS-1$
                .section("ReferenceAdvancedMotionPlannerConfigurationWizard.TestMotionPanel.Border.title", "move").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", org.openpnp.gui.shell.Forms.paragraph(Translations.getString("MotionPlannerForm.Test.Note"))); //$NON-NLS-1$ //$NON-NLS-2$
        point(b, "startLocationEnabled", "startLocation", //$NON-NLS-1$ //$NON-NLS-2$
                "ReferenceAdvancedMotionPlannerConfigurationWizard.TestMotionPanel.FirstLocationLabel.text"); //$NON-NLS-1$
        leg(b, "toMid1Speed", "toMid1SafeZ", "MotionPlannerForm.Test.Speed12"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        point(b, "mid1LocationEnabled", "midLocation1", //$NON-NLS-1$ //$NON-NLS-2$
                "ReferenceAdvancedMotionPlannerConfigurationWizard.TestMotionPanel.SecondLocationLabel.text"); //$NON-NLS-1$
        leg(b, "toMid2Speed", "toMid2SafeZ", "MotionPlannerForm.Test.Speed23"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        point(b, "mid2LocationEnabled", "midLocation2", //$NON-NLS-1$ //$NON-NLS-2$
                "ReferenceAdvancedMotionPlannerConfigurationWizard.TestMotionPanel.ThirdLocationLabel.text"); //$NON-NLS-1$
        leg(b, "toEndSpeed", "toEndSafeZ", "MotionPlannerForm.Test.Speed34"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        point(b, "endLocationEnabled", "endLocation", //$NON-NLS-1$ //$NON-NLS-2$
                "ReferenceAdvancedMotionPlannerConfigurationWizard.TestMotionPanel.LastLocationLabel.text"); //$NON-NLS-1$
        return b.build();
    }

    /** A point of the test motion: whether it is on, and where while it is. */
    private static void point(Form.Builder b, String enabled, String location, String label) {
        b.toggle(enabled, label, "MotionPlannerForm.Test.Enabled") //$NON-NLS-1$
                .location(location, "MotionPlannerForm.Test.At", true).locationButtons() //$NON-NLS-1$
                .visibleWhen(enabled, Boolean.TRUE::equals);
    }

    /** How the test goes on to the next point: the speed, and by Safe Z or straight. */
    private static void leg(Form.Builder b, String speed, String safeZ, String label) {
        b.decimal(speed, label).width(120)
                .toggle(safeZ, "MotionPlannerForm.Test.SafeZ", "MotionPlannerForm.Test.SafeZ.Note") //$NON-NLS-1$ //$NON-NLS-2$
                .liveHint(f -> Boolean.TRUE.equals(f.value(safeZ)) ? null
                        : Translations.getString("MotionPlannerForm.Test.SafeZ.Caution")); //$NON-NLS-1$
    }

    // ==== diagnostics ===============================================================================

    public static FormWizard diagnostics(ReferenceAdvancedMotionPlanner planner) {
        Bean bean = new Bean(planner);
        SimpleGraphView graph = new SimpleGraphView();
        graph.setFont(Ui.font(Tokens.FS_AUX));
        graph.setGraph(planner.getMotionGraph());
        graph.setPreferredSize(new java.awt.Dimension(200, 260));
        graph.setMinimumSize(new java.awt.Dimension(60, 260));
        bean.addPropertyChangeListener("motionGraph", e -> graph.setGraph((SimpleGraph) e.getNewValue())); //$NON-NLS-1$
        FormWizard[] form = new FormWizard[1];
        form[0] = Form.of(bean).named("ReferenceAdvancedMotionPlanner.MotionPlannerDiagnostics.title") //$NON-NLS-1$
                .section("MotionPlannerForm.Diagnostics", "activity") //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("diagnosticsEnabled", "MotionPlannerForm.Diagnostics.Enabled", //$NON-NLS-1$ //$NON-NLS-2$
                        "MotionPlannerForm.Diagnostics.Enabled.Note") //$NON-NLS-1$
                .action("MotionPlannerForm.Diagnostics.Test", "play", () -> test(form[0], planner)) //$NON-NLS-1$ //$NON-NLS-2$
                .movesMachine()
                .readOnly("moveTimes", "MotionPlannerForm.Diagnostics.Times") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("MotionPlannerForm.Diagnostics.Graph", graph) //$NON-NLS-1$
                .hint("MotionPlannerForm.Diagnostics.Graph.Hint") //$NON-NLS-1$
                .build();
        return form[0];
    }

    /**
     * Runs the test motion with the selected tool, from the end it is nearer to, so that the test
     * does not first cross the machine to its start.
     */
    private static void test(FormWizard form, ReferenceAdvancedMotionPlanner planner) {
        form.apply();
        UiUtils.messageBoxOnException(() -> {
            HeadMountable tool = MainFrame.get().getMachineControls().getSelectedTool();
            UiUtils.submitUiMachineTask(() -> {
                if (planner.getInitialLocation(false) == null || planner.getInitialLocation(true) == null) {
                    throw new Exception(Translations.getString(
                            "ReferenceAdvancedMotionPlannerDiagnosticsWizard.LocationsUndefined.Message")); //$NON-NLS-1$
                }
                Location at = tool.getLocation();
                boolean reverse = at.getXyzcDistanceTo(planner.getInitialLocation(true))
                        < at.getXyzcDistanceTo(planner.getInitialLocation(false));
                planner.testMotion(tool, reverse);
            });
        });
    }
}
