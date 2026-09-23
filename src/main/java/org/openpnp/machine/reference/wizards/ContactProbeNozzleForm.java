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

import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.machine.reference.ContactProbeNozzle;
import org.openpnp.machine.reference.ContactProbeNozzle.ContactProbeMethod;
import org.openpnp.machine.reference.ContactProbeNozzle.ContactProbeTrigger;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.Length;
import org.openpnp.spi.Actuator;
import org.openpnp.util.UiUtils;

/**
 * How a contact probe nozzle feels for a surface: with a contact sensing actuator or by its
 * vacuum, how far and how fast, what it probes, and its Z calibration against the nozzle tip's
 * touch location. Only the fields of the method chosen are shown.
 */
public final class ContactProbeNozzleForm {
    private ContactProbeNozzleForm() {
    }

    public static class Bean extends AbstractModelObject {
        private final ContactProbeNozzle nozzle;

        Bean(ContactProbeNozzle nozzle) {
            this.nozzle = nozzle;
        }

        public ContactProbeMethod getContactProbeMethod() {
            return nozzle.getContactProbeMethod();
        }

        public void setContactProbeMethod(ContactProbeMethod method) {
            nozzle.setContactProbeMethod(method);
        }

        public Actuator getContactProbeActuator() {
            return nozzle.getContactProbeActuator();
        }

        public void setContactProbeActuator(Actuator actuator) {
            nozzle.setContactProbeActuator(actuator);
        }

        public Length getContactProbeStartOffsetZ() {
            return nozzle.getContactProbeStartOffsetZ();
        }

        public void setContactProbeStartOffsetZ(Length offset) {
            nozzle.setContactProbeStartOffsetZ(offset);
        }

        public Length getContactProbeDepthZ() {
            return nozzle.getContactProbeDepthZ();
        }

        public void setContactProbeDepthZ(Length depth) {
            nozzle.setContactProbeDepthZ(depth);
        }

        public double getContactProbeSpeed() {
            return nozzle.getContactProbeSpeed();
        }

        public void setContactProbeSpeed(double speed) {
            nozzle.setContactProbeSpeed(speed);
        }

        public Length getContactProbeAdjustZ() {
            return nozzle.getContactProbeAdjustZ();
        }

        public void setContactProbeAdjustZ(Length adjust) {
            nozzle.setContactProbeAdjustZ(adjust);
        }

        public Length getSniffleIncrementZ() {
            return nozzle.getSniffleIncrementZ();
        }

        public void setSniffleIncrementZ(Length increment) {
            nozzle.setSniffleIncrementZ(increment);
        }

        public long getSniffleDwellTime() {
            return nozzle.getSniffleDwellTime();
        }

        public void setSniffleDwellTime(long milliseconds) {
            nozzle.setSniffleDwellTime((int) milliseconds);
        }

        public ContactProbeTrigger getFeederHeightProbing() {
            return nozzle.getFeederHeightProbing();
        }

        public void setFeederHeightProbing(ContactProbeTrigger trigger) {
            nozzle.setFeederHeightProbing(trigger);
        }

        public ContactProbeTrigger getPartHeightProbing() {
            return nozzle.getPartHeightProbing();
        }

        public void setPartHeightProbing(ContactProbeTrigger trigger) {
            nozzle.setPartHeightProbing(trigger);
        }

        public boolean isDiscardProbing() {
            return nozzle.isDiscardProbing();
        }

        public void setDiscardProbing(boolean probing) {
            nozzle.setDiscardProbing(probing);
        }

        /** What the calibration below measured; not typed. */
        public Length getCalibrationOffsetZ() {
            return nozzle.getCalibrationOffsetZ();
        }
    }

    public static FormWizard build(ContactProbeNozzle nozzle) {
        List<Actuator> actuators = new ArrayList<>();
        actuators.add(null);
        actuators.addAll(nozzle.getHead().getActuators());
        FormWizard[] form = new FormWizard[1];
        form[0] = Form.of(new Bean(nozzle)).named("ContactProbeNozzleForm.Title") //$NON-NLS-1$
                .section("ContactProbeNozzleWizard.ContactProbing.Border.title", "nozzle") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("contactProbeMethod", "ContactProbeNozzleWizard.lblMethod.text", ContactProbeMethod.class) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("contactProbeActuator", "ContactProbeNozzleWizard.lblContactProbeActuator.text", actuators, null) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("contactProbeMethod", v -> v == ContactProbeMethod.ContactSenseActuator) //$NON-NLS-1$
                .length("contactProbeStartOffsetZ", "ContactProbeNozzleWizard.lblStartOffset.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("contactProbeMethod", ContactProbeNozzleForm::probing) //$NON-NLS-1$
                .hint("ContactProbeNozzleForm.StartOffset.Hint") //$NON-NLS-1$
                .length("contactProbeDepthZ", "ContactProbeNozzleWizard.lblProbeDepth.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("contactProbeMethod", ContactProbeNozzleForm::probing) //$NON-NLS-1$
                .hint("ContactProbeNozzleWizard.lblProbeDepth.toolTipText") //$NON-NLS-1$
                .decimal("contactProbeSpeed", "ContactProbeNozzleWizard.lblProbeSpeed.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("contactProbeMethod", ContactProbeNozzleForm::probing) //$NON-NLS-1$
                .hint("ContactProbeNozzleForm.Speed.Hint") //$NON-NLS-1$
                .length("contactProbeAdjustZ", "ContactProbeNozzleWizard.lblFinalAdjustment.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("contactProbeMethod", ContactProbeNozzleForm::probing) //$NON-NLS-1$
                .hint("ContactProbeNozzleForm.Adjust.Hint") //$NON-NLS-1$
                .length("sniffleIncrementZ", "ContactProbeNozzleWizard.lblSniffleIncrement.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("contactProbeMethod", v -> v == ContactProbeMethod.VacuumSense) //$NON-NLS-1$
                .hint("ContactProbeNozzleWizard.lblSniffleIncrement.toolTipText") //$NON-NLS-1$
                .integer("sniffleDwellTime", "ContactProbeNozzleForm.SniffleDwell").unit("ms").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen("contactProbeMethod", v -> v == ContactProbeMethod.VacuumSense) //$NON-NLS-1$
                .section("ContactProbeNozzleForm.Uses", "list") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("feederHeightProbing", "ContactProbeNozzleWizard.lblFeederHeightProbing.text", //$NON-NLS-1$ //$NON-NLS-2$
                        ContactProbeTrigger.class)
                .visibleWhen("contactProbeMethod", v -> v == ContactProbeMethod.ContactSenseActuator) //$NON-NLS-1$
                .hint("ContactProbeNozzleForm.FeederHeight.Hint") //$NON-NLS-1$
                .choice("partHeightProbing", "ContactProbeNozzleWizard.lblPartHeightProbing.text", //$NON-NLS-1$ //$NON-NLS-2$
                        ContactProbeTrigger.class)
                .visibleWhen("contactProbeMethod", v -> v == ContactProbeMethod.ContactSenseActuator) //$NON-NLS-1$
                .hint("ContactProbeNozzleForm.PartHeight.Hint") //$NON-NLS-1$
                .toggle("discardProbing", "ContactProbeNozzleWizard.lblDiscardProbing.text", //$NON-NLS-1$ //$NON-NLS-2$
                        "ContactProbeNozzleForm.Discard.Note") //$NON-NLS-1$
                .visibleWhen("contactProbeMethod", v -> v == ContactProbeMethod.ContactSenseActuator) //$NON-NLS-1$
                .hint("ContactProbeNozzleForm.Discard.Hint") //$NON-NLS-1$
                .section("ContactProbeNozzleForm.Calibration", "target") //$NON-NLS-1$ //$NON-NLS-2$
                .readOnly("calibrationOffsetZ", "ContactProbeNozzleWizard.lblZCalibration.text") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("contactProbeMethod", ContactProbeNozzleForm::probing) //$NON-NLS-1$
                .hint("ContactProbeNozzleForm.Calibration.Hint") //$NON-NLS-1$
                .action("ContactProbeNozzleForm.CalibrateNow", "target", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                    form[0].apply();
                    UiUtils.submitUiMachineTask(() -> {
                        nozzle.calibrateZ(nozzle.getCalibrationNozzleTip());
                        javax.swing.SwingUtilities.invokeLater(form[0]::reload);
                    });
                })
                .movesMachine()
                .visibleWhen("contactProbeMethod", ContactProbeNozzleForm::probing) //$NON-NLS-1$
                .build();
        return form[0];
    }

    private static boolean probing(Object method) {
        return method != null && method != ContactProbeMethod.None;
    }
}
