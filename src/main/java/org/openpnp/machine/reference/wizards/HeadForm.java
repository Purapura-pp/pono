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

package org.openpnp.machine.reference.wizards;

import java.util.ArrayList;
import java.util.List;

import org.openpnp.Translations;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.machine.reference.ReferenceHead;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.CalibrationStep;
import org.openpnp.model.Length;
import org.openpnp.model.Location;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.base.AbstractHead.VacuumPumpControl;
import org.openpnp.spi.base.AbstractHead.VisualHomingMethod;
import org.openpnp.util.UiUtils;

/**
 * A head: where it parks, how it homes on a fiducial, the calibration rig it measures its
 * nozzles and cameras against, and the actuators it probes and makes its vacuum with.
 */
public final class HeadForm {
    private HeadForm() {
    }

    public static class Bean extends AbstractModelObject {
        private final ReferenceHead head;

        Bean(ReferenceHead head) {
            this.head = head;
        }

        public Location getParkLocation() {
            return head.getParkLocation();
        }

        public void setParkLocation(Location location) {
            head.setParkLocation(location);
        }

        public VisualHomingMethod getVisualHomingMethod() {
            return head.getVisualHomingMethod();
        }

        public void setVisualHomingMethod(VisualHomingMethod method) {
            head.setVisualHomingMethod(method);
        }

        public Location getHomingFiducialLocation() {
            return head.getHomingFiducialLocation();
        }

        public void setHomingFiducialLocation(Location location) {
            head.setHomingFiducialLocation(location);
        }

        public Location getCalibrationPrimaryFiducialLocation() {
            return head.getCalibrationPrimaryFiducialLocation();
        }

        public void setCalibrationPrimaryFiducialLocation(Location location) {
            head.setCalibrationPrimaryFiducialLocation(location);
        }

        public Length getCalibrationPrimaryFiducialDiameter() {
            return head.getCalibrationPrimaryFiducialDiameter();
        }

        public void setCalibrationPrimaryFiducialDiameter(Length diameter) {
            head.setCalibrationPrimaryFiducialDiameter(diameter);
        }

        public Location getCalibrationSecondaryFiducialLocation() {
            return head.getCalibrationSecondaryFiducialLocation();
        }

        public void setCalibrationSecondaryFiducialLocation(Location location) {
            head.setCalibrationSecondaryFiducialLocation(location);
        }

        public Length getCalibrationSecondaryFiducialDiameter() {
            return head.getCalibrationSecondaryFiducialDiameter();
        }

        public void setCalibrationSecondaryFiducialDiameter(Length diameter) {
            head.setCalibrationSecondaryFiducialDiameter(diameter);
        }

        public Length getCalibrationTestObjectDiameter() {
            return head.getCalibrationTestObjectDiameter();
        }

        public void setCalibrationTestObjectDiameter(Length diameter) {
            head.setCalibrationTestObjectDiameter(diameter);
        }

        public Actuator getProbeActuator() {
            return head.getzProbeActuator();
        }

        public void setProbeActuator(Actuator actuator) {
            head.setzProbeActuator(actuator);
        }

        public Actuator getPumpActuator() {
            return head.getPumpActuator();
        }

        public void setPumpActuator(Actuator actuator) {
            head.setPumpActuator(actuator);
        }

        public VacuumPumpControl getVacuumPumpControl() {
            return head.getVacuumPumpControl();
        }

        public void setVacuumPumpControl(VacuumPumpControl control) {
            head.setVacuumPumpControl(control);
        }

        public int getPumpOnWaitMilliseconds() {
            return head.getPumpOnWaitMilliseconds();
        }

        public void setPumpOnWaitMilliseconds(int milliseconds) {
            head.setPumpOnWaitMilliseconds(milliseconds);
        }
    }

    public static FormWizard build(ReferenceHead head) {
        List<Actuator> actuators = new ArrayList<>();
        actuators.add(null);
        actuators.addAll(head.getActuators());
        FormWizard[] form = new FormWizard[1];
        form[0] = Form.of(new Bean(head)).named("HeadForm.Title") //$NON-NLS-1$
                .section("HeadForm.Park", "pin") //$NON-NLS-1$ //$NON-NLS-2$
                .location("parkLocation", "ReferenceHeadConfigurationWizard.LocationsPanel.ParkLocationLabel.text", false) //$NON-NLS-1$ //$NON-NLS-2$
                .locationButtons()
                .section("HeadForm.Homing", "home").measuredBy(CalibrationStep.VisualHoming, head) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("visualHomingMethod", "ReferenceHeadConfigurationWizard.LocationsPanel.HomingMethodLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        VisualHomingMethod.class)
                .location("homingFiducialLocation", "ReferenceHeadConfigurationWizard.LocationsPanel.HomingFiducialLabel.text", false) //$NON-NLS-1$ //$NON-NLS-2$
                .locationButtons()
                .visibleWhen("visualHomingMethod", v -> v != VisualHomingMethod.None) //$NON-NLS-1$
                .note("HeadForm.HomingFiducial.Note") //$NON-NLS-1$
                .action("ReferenceHeadConfigurationWizard.LocationsPanel.VisualTestButton.text", "target", //$NON-NLS-1$ //$NON-NLS-2$
                        () -> visualHome(form[0], head, false))
                .movesMachine()
                .visibleWhen("visualHomingMethod", v -> v == VisualHomingMethod.ResetToFiducialLocation) //$NON-NLS-1$
                .action("ReferenceHeadConfigurationWizard.LocationsPanel.VisualHomeButton.text", "home", //$NON-NLS-1$ //$NON-NLS-2$
                        () -> visualHome(form[0], head, true))
                .movesMachine()
                .visibleWhen("visualHomingMethod", v -> v == VisualHomingMethod.ResetToFiducialLocation) //$NON-NLS-1$
                .section("ReferenceHeadConfigurationWizard.CalibrationRigPanel.Border.title", "target").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .measuredBy(CalibrationStep.PrimaryFiducial, head)
                .custom("", org.openpnp.gui.shell.Forms.paragraph(Translations.getString("HeadForm.Rig.Note"))) //$NON-NLS-1$ //$NON-NLS-2$
                .location("calibrationPrimaryFiducialLocation", //$NON-NLS-1$
                        "ReferenceHeadConfigurationWizard.CalibrationRigPanel.PrimaryFiducialLabel.text", false) //$NON-NLS-1$
                .withZ().locationButtons()
                .note("HeadForm.RigPrimary.Note") //$NON-NLS-1$
                .length("calibrationPrimaryFiducialDiameter", "HeadForm.RigPrimaryDiameter").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .location("calibrationSecondaryFiducialLocation", //$NON-NLS-1$
                        "ReferenceHeadConfigurationWizard.CalibrationRigPanel.SecondaryFiducialLabel.text", false) //$NON-NLS-1$
                .withZ().locationButtons()
                .note("HeadForm.RigSecondary.Note") //$NON-NLS-1$
                .length("calibrationSecondaryFiducialDiameter", "HeadForm.RigSecondaryDiameter").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .length("calibrationTestObjectDiameter", "HeadForm.RigTestObject").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .note("HeadForm.RigTestObject.Note") //$NON-NLS-1$
                .section("ReferenceHeadConfigurationWizard.ZProbePanel.Border.title", "nozzle") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("probeActuator", "ReferenceHeadConfigurationWizard.ZProbePanel.ZProbeActuatorLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        actuators, null)
                .section("ReferenceHeadConfigurationWizard.PumpPanel.Border.title", "circle") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("pumpActuator", "ReferenceHeadConfigurationWizard.PumpPanel.VacuumPumpActuatorLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        actuators, null)
                .choice("vacuumPumpControl", "ReferenceHeadConfigurationWizard.PumpPanel.VacuumPumpControlLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        VacuumPumpControl.class)
                .visibleWhen("pumpActuator", v -> v != null) //$NON-NLS-1$
                .note("HeadForm.PumpControl.Note") //$NON-NLS-1$
                .integer("pumpOnWaitMilliseconds", "HeadForm.PumpWait").unit("ms").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen("pumpActuator", v -> v != null) //$NON-NLS-1$
                .note("ReferenceHeadConfigurationWizard.PumpPanel.VacuumPumpStartTimeLabel.toolTipText") //$NON-NLS-1$
                .build();
        return form[0];
    }

    /** Homes on the fiducial, or only looks where it is to test it, with what is on screen applied. */
    private static void visualHome(FormWizard form, ReferenceHead head, boolean apply) {
        form.apply();
        UiUtils.submitUiMachineTask(() -> head.visualHome(head.getMachine(), apply));
    }
}
