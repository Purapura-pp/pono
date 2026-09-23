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

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.support.LengthConverter;
import org.openpnp.machine.reference.SimulationModeMachine;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.util.UiUtils;

/**
 * The simulated machine: how far it simulates, and the imperfections it puts into what the
 * simulated cameras see - runout, a frame out of square, noise, lag, vibration and a homing
 * error - to try the calibrations on. The wizard had them in one grid with a warning about runout
 * in English, and the sheet was titled "Simulation Mode" whatever the language.
 */
public final class SimulationMachineForm {
    private SimulationMachineForm() {
    }

    public static class Bean extends AbstractModelObject {
        private final SimulationModeMachine machine;
        private Length machineTableZ = new Length(0, LengthUnit.Millimeters);

        Bean(SimulationModeMachine machine) {
            this.machine = machine;
        }

        public SimulationModeMachine.SimulationMode getSimulationMode() {
            return machine.getSimulationMode();
        }

        public void setSimulationMode(SimulationModeMachine.SimulationMode mode) {
            machine.setSimulationMode(mode);
        }

        public boolean isReplacingDrivers() {
            return machine.isReplacingDrivers();
        }

        public void setReplacingDrivers(boolean replacing) {
            machine.setReplacingDrivers(replacing);
        }

        public Length getSimulatedRunout() {
            return machine.getSimulatedRunout();
        }

        public void setSimulatedRunout(Length runout) {
            machine.setSimulatedRunout(runout);
        }

        public double getSimulatedRunoutPhase() {
            return machine.getSimulatedRunoutPhase();
        }

        public void setSimulatedRunoutPhase(double phase) {
            machine.setSimulatedRunoutPhase(phase);
        }

        public double getSimulatedNonSquarenessFactor() {
            return machine.getSimulatedNonSquarenessFactor();
        }

        public void setSimulatedNonSquarenessFactor(double factor) {
            machine.setSimulatedNonSquarenessFactor(factor);
        }

        public boolean isPickAndPlaceChecking() {
            return machine.isPickAndPlaceChecking();
        }

        public void setPickAndPlaceChecking(boolean checking) {
            machine.setPickAndPlaceChecking(checking);
        }

        public double getSimulatedCameraLag() {
            return machine.getSimulatedCameraLag();
        }

        public void setSimulatedCameraLag(double lag) {
            machine.setSimulatedCameraLag(lag);
        }

        public int getSimulatedCameraNoise() {
            return machine.getSimulatedCameraNoise();
        }

        public void setSimulatedCameraNoise(int noise) {
            machine.setSimulatedCameraNoise(noise);
        }

        public double getSimulatedVibrationAmplitude() {
            return machine.getSimulatedVibrationAmplitude();
        }

        public void setSimulatedVibrationAmplitude(double amplitude) {
            machine.setSimulatedVibrationAmplitude(amplitude);
        }

        public double getSimulatedVibrationDuration() {
            return machine.getSimulatedVibrationDuration();
        }

        public void setSimulatedVibrationDuration(double duration) {
            machine.setSimulatedVibrationDuration(duration);
        }

        public Location getHomingError() {
            return machine.getHomingError();
        }

        public void setHomingError(Location error) {
            machine.setHomingError(error);
        }

        /** Not kept: the height the feeders' pick locations are all put at by the button beside it. */
        public Length getMachineTableZ() {
            return machineTableZ;
        }

        public void setMachineTableZ(Length z) {
            machineTableZ = z;
        }
    }

    public static FormWizard build(SimulationModeMachine machine) {
        return Form.of(new Bean(machine)).named("SimulationMachineForm.Title") //$NON-NLS-1$
                .section("SimulationMachineForm.Simulation", "sliders") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("simulationMode", "SimulationModeMachineConfigurationWizard.lblSimulationMode.text", //$NON-NLS-1$ //$NON-NLS-2$
                        SimulationModeMachine.SimulationMode.class)
                .toggle("replacingDrivers", "SimulationMachineForm.ReplaceDrivers", //$NON-NLS-1$ //$NON-NLS-2$
                        "SimulationMachineForm.ReplaceDrivers.Note") //$NON-NLS-1$
                .hint("SimulationMachineForm.ReplaceDrivers.Hint") //$NON-NLS-1$
                .toggle("pickAndPlaceChecking", "SimulationMachineForm.PickPlaceChecking", //$NON-NLS-1$ //$NON-NLS-2$
                        "SimulationMachineForm.PickPlaceChecking.Note") //$NON-NLS-1$
                .hint("SimulationMachineForm.PickPlaceChecking.Hint") //$NON-NLS-1$
                .section("SimulationMachineForm.Nozzle", "nozzle") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", Forms.paragraph(Translations.getString("SimulationMachineForm.Nozzle.Note"))) //$NON-NLS-1$ //$NON-NLS-2$
                .length("simulatedRunout", "SimulationModeMachineConfigurationWizard.lblNozzleTipRunout.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .angle("simulatedRunoutPhase", "SimulationModeMachineConfigurationWizard.lblRunoutPhase.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .section("SimulationMachineForm.Frame", "grid") //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("simulatedNonSquarenessFactor", "SimulationModeMachineConfigurationWizard.lblNonsquarenessFactor.text") //$NON-NLS-1$ //$NON-NLS-2$
                .width(120)
                .note("SimulationModeMachineConfigurationWizard.lblNonsquarenessFactor.toolTipText") //$NON-NLS-1$
                .location("homingError", "SimulationModeMachineConfigurationWizard.lblDiscardPoint.text", false) //$NON-NLS-1$ //$NON-NLS-2$
                .note("SimulationMachineForm.HomingError.Note") //$NON-NLS-1$
                .section("SimulationMachineForm.Camera", "camera") //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("simulatedCameraLag", "SimulationMachineForm.CameraLag").unit("s").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .integer("simulatedCameraNoise", "SimulationModeMachineConfigurationWizard.lblCameraNoise.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .note("SimulationMachineForm.CameraNoise.Note") //$NON-NLS-1$
                .decimal("simulatedVibrationAmplitude", "SimulationModeMachineConfigurationWizard.lblVibrationAmplitude.text") //$NON-NLS-1$ //$NON-NLS-2$
                .width(120)
                .note("SimulationModeMachineConfigurationWizard.lblVibrationAmplitude.toolTipText") //$NON-NLS-1$
                .decimal("simulatedVibrationDuration", "SimulationMachineForm.VibrationDuration").unit("s").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .section("SimulationMachineForm.Feeders", "feeder").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .length("machineTableZ", "SimulationModeMachineConfigurationWizard.lblMachineTableZ.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SimulationMachineForm.TableZ.Set", "feeder", form -> setTableZ(machine, form)) //$NON-NLS-1$ //$NON-NLS-2$
                .action("SimulationModeMachineConfigurationWizard.btnResetFeeders.text", "refresh", () -> resetFeeders(machine)) //$NON-NLS-1$ //$NON-NLS-2$
                .build();
    }

    /** Every feeder's pick height to the table's, after asking: it rewrites all of them. */
    private static void setTableZ(SimulationModeMachine machine, FormWizard form) {
        Object text = form.value("machineTableZ"); //$NON-NLS-1$
        UiUtils.messageBoxOnException(() -> {
            Length z = new LengthConverter().convertReverse(String.valueOf(text));
            int chosen = Dialogs.ask(MainFrame.get(), Dialogs.Tone.Warn, "feeder", //$NON-NLS-1$
                    String.format(Translations.getString("SimulationMachineForm.TableZ.Title"), //$NON-NLS-1$
                            z.toString()),
                    Translations.getString("SimulationMachineForm.TableZ.What"), null, //$NON-NLS-1$
                    Dialogs.Choice.primary(Translations.getString("SimulationMachineForm.TableZ.Action"))); //$NON-NLS-1$
            if (chosen == 0) {
                machine.setMachineTableZ(z);
            }
        });
    }

    private static void resetFeeders(SimulationModeMachine machine) {
        int chosen = Dialogs.ask(MainFrame.get(), Dialogs.Tone.Warn, "refresh", //$NON-NLS-1$
                Translations.getString("SimulationMachineForm.ResetFeeders.Title"), //$NON-NLS-1$
                Translations.getString("SimulationMachineForm.ResetFeeders.What"), null, //$NON-NLS-1$
                Dialogs.Choice.primary(Translations.getString("SimulationMachineForm.ResetFeeders.Action"))); //$NON-NLS-1$
        if (chosen == 0) {
            machine.resetAllFeeders();
        }
    }
}
