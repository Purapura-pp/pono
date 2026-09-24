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

package org.openpnp.machine.reference.camera.wizards;

import javax.swing.SwingUtilities;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.machine.reference.ReferenceNozzle;
import org.openpnp.machine.reference.ReferenceNozzleTip;
import org.openpnp.machine.reference.camera.AutoFocusProvider;
import org.openpnp.machine.reference.camera.ReferenceCamera;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Nozzle;
import org.openpnp.util.MovableUtils;
import org.openpnp.util.UiUtils;
import org.pmw.tinylog.Logger;

/**
 * A camera's auto focus: how finely and how fast it seeks, a test on the selected nozzle, and
 * the camera's Z put where the test found the focus.
 */
public final class AutoFocusForm {
    private AutoFocusForm() {
    }

    /** The provider's settings, and the distance the last test found, the form's own. */
    public static class Bean extends AbstractModelObject {
        private final AutoFocusProvider provider;
        private Length lastFocusDistance;

        Bean(AutoFocusProvider provider) {
            this.provider = provider;
        }

        public Length getFocalResolution() {
            return provider.getFocalResolution();
        }

        public void setFocalResolution(Length resolution) {
            provider.setFocalResolution(resolution);
        }

        public int getAveragedFrames() {
            return provider.getAveragedFrames();
        }

        public void setAveragedFrames(int frames) {
            provider.setAveragedFrames(frames);
        }

        public double getFocusSpeed() {
            return provider.getFocusSpeed();
        }

        public void setFocusSpeed(double speed) {
            provider.setFocusSpeed(speed);
        }

        public boolean isShowDiagnostics() {
            return provider.isShowDiagnostics();
        }

        public void setShowDiagnostics(boolean show) {
            provider.setShowDiagnostics(show);
        }

        public Length getLastFocusDistance() {
            return lastFocusDistance;
        }

        void setLastFocusDistance(Length distance) {
            this.lastFocusDistance = distance;
            firePropertyChange("lastFocusDistance", null, distance); //$NON-NLS-1$
        }
    }

    public static FormWizard build(Camera camera, AutoFocusProvider provider) {
        Bean bean = new Bean(provider);
        FormWizard[] form = new FormWizard[1];
        form[0] = Form.of(bean).named("ReferenceCamera.FocusProvider.ConfigurationWizard.tab.title") //$NON-NLS-1$
                .section("AutoFocusProviderConfigurationWizard.panelGeneral.Border.title", "crosshair") //$NON-NLS-1$ //$NON-NLS-2$
                .length("focalResolution", "AutoFocusProviderConfigurationWizard.lblFocalResolution.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("AutoFocusProviderConfigurationWizard.lblFocalResolution.toolTipText") //$NON-NLS-1$
                .integer("averagedFrames", "AutoFocusProviderConfigurationWizard.lblAveragedFrames.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("AutoFocusProviderConfigurationWizard.lblAveragedFrames.toolTipText") //$NON-NLS-1$
                .decimal("focusSpeed", "AutoFocusProviderConfigurationWizard.lblFocusSpeed.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("AutoFocusProviderConfigurationWizard.lblFocusSpeed.toolTipText") //$NON-NLS-1$
                .toggle("showDiagnostics", "AutoFocusForm.Diagnostics", //$NON-NLS-1$ //$NON-NLS-2$
                        "AutoFocusProviderConfigurationWizard.lblShowDiagnostics.toolTipText") //$NON-NLS-1$
                .section("AutoFocusForm.Test", "target") //$NON-NLS-1$ //$NON-NLS-2$
                .action("AutoFocusForm.Test.Run", "crosshair", () -> test(form[0], camera, provider, bean)).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .hint("AutoFocusForm.Test.Hint") //$NON-NLS-1$
                .readOnly("lastFocusDistance", "AutoFocusProviderConfigurationWizard.lblLastFocusDistance.text") //$NON-NLS-1$ //$NON-NLS-2$
                .action("AutoFocusForm.AdjustZ", "move", () -> adjustCameraZ(form[0], camera, bean)) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleIf(f -> bean.getLastFocusDistance() != null && camera instanceof ReferenceCamera)
                .hint("AutoFocusForm.AdjustZ.Hint") //$NON-NLS-1$
                .build();
        return form[0];
    }

    private static void test(FormWizard form, Camera camera, AutoFocusProvider provider, Bean bean) {
        form.apply();
        camera.ensureCameraVisible();
        UiUtils.submitUiMachineTask(() -> {
            Nozzle nozzle = MainFrame.get().getMachineControls().getSelectedNozzle();
            if (!(nozzle instanceof ReferenceNozzle)) {
                throw new Exception(String.format(Translations.getString("AutoFocusForm.NotReference"), nozzle.getName())); //$NON-NLS-1$
            }
            ReferenceNozzleTip tip = ((ReferenceNozzle) nozzle).getCalibrationNozzleTip();
            if (tip == null) {
                throw new Exception(Translations.getString("AutoFocusForm.NoTip")); //$NON-NLS-1$
            }
            Location near = camera.getLocation(nozzle).derive(nozzle.getLocation(), false, false, false, true);
            Length maxPartHeight = tip.getMaxPartHeight();
            Location far = near.add(new Location(maxPartHeight.getUnits(), 0, 0, maxPartHeight.getValue(), 0));
            Location focus = provider.autoFocus(camera, nozzle,
                    tip.getMaxPartDiameter().add(tip.getMaxPickTolerance().multiply(2.0)), far, near);
            Length distance = focus.getXyzLengthTo(near);
            SwingUtilities.invokeLater(() -> {
                bean.setLastFocusDistance(distance);
                form.reload();
            });
            MovableUtils.fireTargetedUserAction(camera);
        });
    }

    private static void adjustCameraZ(FormWizard form, Camera camera, Bean bean) {
        form.apply();
        UiUtils.messageBoxOnException(() -> {
            Nozzle nozzle = MainFrame.get().getMachineControls().getSelectedNozzle();
            if (nozzle.getPart() != null) {
                throw new Exception(String.format(Translations.getString("AutoFocusForm.PartOn"), nozzle.getName())); //$NON-NLS-1$
            }
            if (camera.getLocation(nozzle).getLinearLengthTo(nozzle.getLocation())
                    .convertToUnits(LengthUnit.Millimeters).getValue() > 0.1) {
                throw new Exception(String.format(Translations.getString("AutoFocusForm.NotCentered"), nozzle.getName())); //$NON-NLS-1$
            }
            int chosen = Dialogs.ask(MainFrame.get(), Dialogs.Tone.Warn, "move", //$NON-NLS-1$
                    Translations.getString("AutoFocusForm.AdjustZ.Title"), //$NON-NLS-1$
                    Translations.getString("AutoFocusForm.AdjustZ.What"), //$NON-NLS-1$
                    Translations.getString("AutoFocusForm.AdjustZ.More"), //$NON-NLS-1$
                    Dialogs.Choice.primary(Translations.getString("AutoFocusForm.AdjustZ.Action"))); //$NON-NLS-1$
            if (chosen != 0) {
                return;
            }
            ReferenceCamera reference = (ReferenceCamera) camera;
            Length offsetZ = camera.getLocation(nozzle).getLengthZ().subtract(nozzle.getLocation().getLengthZ());
            Location offsets = reference.getHeadOffsets();
            Location adjusted = offsets.subtract(new Location(offsetZ.getUnits(), 0, 0, offsetZ.getValue(), 0));
            Logger.info("Setting camera {} Z to {} (previously {})", camera.getName(), adjusted.getLengthZ(), //$NON-NLS-1$
                    offsets.getLengthZ());
            reference.setHeadOffsets(adjusted);
            bean.setLastFocusDistance(null);
            form.reload();
            MovableUtils.fireTargetedUserAction(camera);
        });
    }
}
