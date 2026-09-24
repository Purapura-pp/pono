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

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.components.CameraView;
import org.openpnp.gui.components.SimpleGraphView;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.form.WeakForward;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.support.LengthConverter;
import org.openpnp.machine.reference.axis.ReferenceVirtualAxis;
import org.openpnp.machine.reference.camera.AbstractSettlingCamera.SettleMethod;
import org.openpnp.machine.reference.camera.ReferenceCamera;
import org.openpnp.machine.reference.camera.ReferenceCamera.FocusSensingMethod;
import org.openpnp.machine.reference.wizards.MountableAxes;
import org.openpnp.model.CalibrationStep;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Camera;
import org.openpnp.spi.HeadMountable;
import org.openpnp.spi.Nozzle;
import org.openpnp.util.MovableUtils;
import org.openpnp.util.SimpleGraph;
import org.openpnp.util.UiUtils;

/**
 * A camera's own tabs: what it is and how it lights, where it is and how large a pixel is, and
 * how it waits for a still picture. The device it reads is in {@link CameraDeviceForms}.
 */
public final class CameraForm {
    private CameraForm() {
    }

    static boolean overridden(ReferenceCamera camera) {
        return camera.getAdvancedCalibration().isOverridingOldTransformsAndDistortionCorrectionSettings();
    }

    /** The step of the calibration page that measures how large the camera's pixel is. */
    static Object[] pixelStep(ReferenceCamera camera) {
        if (camera.getLooking() == Camera.Looking.Up) {
            return new Object[] {CalibrationStep.BottomCamera, camera};
        }
        try {
            if (camera.getHead() != null && camera.getHead().getDefaultCamera() == camera) {
                return new Object[] {CalibrationStep.PrimaryFiducial, camera.getHead()};
            }
        }
        catch (Exception e) {
            // A head without cameras has no default one: this camera is then one of the others.
        }
        return new Object[] {CalibrationStep.OtherCameraOffsets, camera};
    }

    // ==== general ===================================================================================

    public static FormWizard general(ReferenceCamera camera) {
        List<Actuator> lights = new ArrayList<>();
        lights.add(null);
        if (camera.getHead() != null) {
            lights.addAll(camera.getHead().getActuators());
        }
        for (Actuator actuator : camera.getMachine().getActuators()) {
            if (!lights.contains(actuator)) {
                lights.add(actuator);
            }
        }
        FocusSensingMethod[] focus = {camera.getFocusSensingMethod()};
        SimpleGraphView balance = new SimpleGraphView();
        balance.setGraph(camera.getColorBalanceGraph());
        FormWizard[] form = new FormWizard[1];
        form[0] = Form.of(camera).named("CameraForm.General") //$NON-NLS-1$
                .section("CameraForm.Basics", "camera") //$NON-NLS-1$ //$NON-NLS-2$
                .text("name", "CameraConfigurationWizard.PropertiesPanel.NameLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .segmented("looking", "CameraConfigurationWizard.PropertiesPanel.LookingLabel.text", Camera.Looking.class) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("previewFps", "CameraForm.PreviewFps").format("%.1f").unit("fps").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                .toggle("suspendPreviewInTasks", "CameraForm.SuspendPreview", "CameraForm.SuspendPreview.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .toggle("autoVisible", "CameraForm.AutoVisible", "CameraForm.AutoVisible.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .toggle("shownInMultiCameraView", "CameraForm.MultiView", "CameraForm.MultiView.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .choice("focusSensingMethod", "CameraConfigurationWizard.PropertiesPanel.FocusSensingLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        FocusSensingMethod.class)
                .visibleIf(f -> camera.getHead() == null)
                .section("CameraForm.Light", "sun") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("lightActuator", "CameraConfigurationWizard.LightPanel.LightActuatorLabel.text", lights, //$NON-NLS-1$ //$NON-NLS-2$
                        a -> a != null && camera.getHead() != null && a.getHead() == null
                                ? Translations.getString("CameraForm.Light.OnMachine") : null) //$NON-NLS-1$
                .toggle("beforeCaptureLightOn", "CameraForm.Light.Before", "CameraForm.Light.Before.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen("lightActuator", Objects::nonNull) //$NON-NLS-1$
                .toggle("afterCaptureLightOff", "CameraForm.Light.After", "CameraForm.Light.After.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen("lightActuator", Objects::nonNull) //$NON-NLS-1$
                .toggle("userActionLightOn", "CameraForm.Light.User", "CameraForm.Light.User.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen("lightActuator", Objects::nonNull) //$NON-NLS-1$
                .toggle("antiGlareLightOff", "CameraForm.Light.AntiGlare", "CameraForm.Light.AntiGlare.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen("lightActuator", Objects::nonNull) //$NON-NLS-1$
                .section("CameraForm.Transforms", "rcw").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", Forms.paragraph(Translations.getString("CameraForm.Overridden"))) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleIf(f -> overridden(camera))
                .angle("rotation", "ReferenceCameraTransformsConfigurationWizard.TransformsPanel.RotationLabel.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleIf(f -> !overridden(camera))
                .integer("offsetX", "ReferenceCameraTransformsConfigurationWizard.TransformsPanel.OffsetXLabel.text").unit("px").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleIf(f -> !overridden(camera))
                .integer("offsetY", "ReferenceCameraTransformsConfigurationWizard.TransformsPanel.OffsetYLabel.text").unit("px").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleIf(f -> !overridden(camera))
                .toggle("flipX", "CameraForm.FlipX", "CameraForm.FlipX.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleIf(f -> !overridden(camera))
                .toggle("flipY", "CameraForm.FlipY", "CameraForm.FlipY.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleIf(f -> !overridden(camera))
                .integer("cropWidth", "ReferenceCameraTransformsConfigurationWizard.TransformsPanel.CropWidthLabel.text").unit("px").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleIf(f -> !overridden(camera))
                .integer("cropHeight", "ReferenceCameraTransformsConfigurationWizard.TransformsPanel.CropHeightLabel.text").unit("px").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleIf(f -> !overridden(camera))
                .hint("CameraForm.Crop.Hint") //$NON-NLS-1$
                .integer("scaleWidth", "ReferenceCameraTransformsConfigurationWizard.TransformsPanel.ScaleWidthLabel.text").unit("px").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleIf(f -> !overridden(camera))
                .integer("scaleHeight", "ReferenceCameraTransformsConfigurationWizard.TransformsPanel.ScaleHeightLabel.text").unit("px").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleIf(f -> !overridden(camera))
                .hint("CameraForm.Scale.Hint") //$NON-NLS-1$
                .toggle("deinterlace", "CameraForm.Deinterlace", "CameraForm.Deinterlace.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleIf(f -> !overridden(camera))
                .section("ReferenceCameraWhiteBalanceConfigurationWizard.ColorBalancePanel.Border.title", "palette").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .action("ReferenceCameraWhiteBalanceConfigurationWizard.Action.AutoWhiteBalance", "palette", //$NON-NLS-1$ //$NON-NLS-2$
                        () -> balance(form[0], () -> camera.autoAdjustWhiteBalance(false)))
                .button("ReferenceCameraWhiteBalanceConfigurationWizard.Action.Brightest", null, //$NON-NLS-1$
                        f -> balance(f, () -> camera.autoAdjustWhiteBalance(true)))
                .button("ReferenceCameraWhiteBalanceConfigurationWizard.Action.MappedRoughly", null, //$NON-NLS-1$
                        f -> balance(f, () -> camera.autoAdjustWhiteBalanceMapped(8)))
                .button("ReferenceCameraWhiteBalanceConfigurationWizard.Action.MappedFinely", null, //$NON-NLS-1$
                        f -> balance(f, () -> camera.autoAdjustWhiteBalanceMapped(32)))
                .button("ReferenceCameraWhiteBalanceConfigurationWizard.Action.Reset", "undo", //$NON-NLS-1$ //$NON-NLS-2$
                        f -> balance(f, camera::resetWhiteBalance))
                .hint("CameraForm.WhiteBalance.Hint") //$NON-NLS-1$
                .percent("redBalance", "ReferenceCameraWhiteBalanceConfigurationWizard.ColorBalancePanel.RedBalanceLabel.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .percent("greenBalance", "ReferenceCameraWhiteBalanceConfigurationWizard.ColorBalancePanel.GreenBalanceLabel.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .percent("blueBalance", "ReferenceCameraWhiteBalanceConfigurationWizard.ColorBalancePanel.BlueBalanceLabel.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .percent("redGamma", "ReferenceCameraWhiteBalanceConfigurationWizard.ColorBalancePanel.RedGammaLabel.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .percent("greenGamma", "ReferenceCameraWhiteBalanceConfigurationWizard.ColorBalancePanel.GreenGammaLabel.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .percent("blueGamma", "ReferenceCameraWhiteBalanceConfigurationWizard.ColorBalancePanel.BlueGammaLabel.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .custom("CameraForm.WhiteBalance.Curves", balance) //$NON-NLS-1$
                .onReload(f -> balance.setGraph(camera.getColorBalanceGraph()))
                .onApply(f -> {
                    UiUtils.messageBoxOnException(camera::reinitialize);
                    balance.setGraph(camera.getColorBalanceGraph());
                    if (camera.getFocusSensingMethod() != focus[0]) {
                        // The auto focus tab comes and goes with the method.
                        focus[0] = camera.getFocusSensingMethod();
                        SwingUtilities.invokeLater(() -> { if (MainFrame.get() != null) { MainFrame.get().getMachineSetupTab().selectCurrentTreePath(); } });
                    }
                })
                .build();
        return form[0];
    }

    /** An automatic white balance: it reads the camera's picture, then the form shows the result. */
    private static void balance(FormWizard form, UiUtils.Thrunnable adjust) {
        UiUtils.submitUiMachineTask(() -> {
            adjust.thrun();
            SwingUtilities.invokeLater(form::reload);
        });
    }

    // ==== position ==================================================================================

    /** Where the camera is and how large its pixel is, the pixel's Z readable apart. */
    public static class PositionBean extends MountableAxes.Bean {
        private final ReferenceCamera camera;

        PositionBean(ReferenceCamera camera) {
            super(camera);
            this.camera = camera;
        }

        public Location getHeadOffsets() {
            return camera.getHeadOffsets();
        }

        public void setHeadOffsets(Location offsets) {
            camera.setHeadOffsets(offsets);
        }

        public Length getSafeZ() {
            return camera.getSafeZ();
        }

        public void setSafeZ(Length safeZ) {
            camera.setSafeZ(safeZ);
        }

        public Length getRoamingRadius() {
            return camera.getRoamingRadius();
        }

        public void setRoamingRadius(Length radius) {
            camera.setRoamingRadius(radius);
        }

        public Location getUnitsPerPixelPrimary() {
            return orZero(camera.getUnitsPerPixelPrimary());
        }

        public void setUnitsPerPixelPrimary(Location upp) {
            camera.setUnitsPerPixelPrimary(upp);
        }

        public boolean isEnableUnitsPerPixel3D() {
            return camera.isEnableUnitsPerPixel3D();
        }

        public void setEnableUnitsPerPixel3D(boolean enable) {
            camera.setEnableUnitsPerPixel3D(enable);
        }

        public Length getDefaultZ() {
            return camera.getDefaultZ();
        }

        public void setDefaultZ(Length z) {
            camera.setDefaultZ(z);
        }

        /** A pixel size not measured yet is none; zero says the same to the camera. */
        private static Location orZero(Location upp) {
            return upp == null ? new Location(LengthUnit.Millimeters) : upp;
        }

        public Length getPrimaryUppZ() {
            return orZero(camera.getUnitsPerPixelPrimary()).getLengthZ();
        }

        public void setPrimaryUppZ(Length z) {
            camera.setUnitsPerPixelPrimary(orZero(camera.getUnitsPerPixelPrimary()).deriveLengths(null, null, z, null));
        }

        public Length getCameraPrimaryZ() {
            return camera.getCameraPrimaryZ();
        }

        public void setCameraPrimaryZ(Length z) {
            camera.setCameraPrimaryZ(z);
        }

        public Location getUnitsPerPixelSecondary() {
            return orZero(camera.getUnitsPerPixelSecondary());
        }

        public void setUnitsPerPixelSecondary(Location upp) {
            camera.setUnitsPerPixelSecondary(upp);
        }

        public Length getSecondaryUppZ() {
            return orZero(camera.getUnitsPerPixelSecondary()).getLengthZ();
        }

        public void setSecondaryUppZ(Length z) {
            camera.setUnitsPerPixelSecondary(orZero(camera.getUnitsPerPixelSecondary()).deriveLengths(null, null, z, null));
        }

        public Length getCameraSecondaryZ() {
            return camera.getCameraSecondaryZ();
        }

        public void setCameraSecondaryZ(Length z) {
            camera.setCameraSecondaryZ(z);
        }
    }

    public static FormWizard position(ReferenceCamera camera) {
        PositionBean bean = new PositionBean(camera);
        boolean onHead = camera.getHead() != null;
        boolean down = camera.getLooking() == Camera.Looking.Down;
        boolean cameraZ = down && camera.getAxisZ() != null && !(camera.getAxisZ() instanceof ReferenceVirtualAxis);
        Object[] pixelStep = pixelStep(camera);
        UppMeasurement measurement = new UppMeasurement(camera);
        FormWizard[] form = new FormWizard[1];
        Form.Builder builder = Form.of(bean).named("CameraForm.Position"); //$NON-NLS-1$
        if (onHead) {
            MountableAxes.section(builder, camera.getMachine());
            builder.section("CameraForm.Offsets", "crosshair"); //$NON-NLS-1$ //$NON-NLS-2$
            if (pixelStep[0] == CalibrationStep.OtherCameraOffsets) {
                builder.measuredBy(CalibrationStep.OtherCameraOffsets, camera);
            }
            builder.location("headOffsets", "CameraForm.Offsets.Label", true); //$NON-NLS-1$ //$NON-NLS-2$
            if (pixelStep[0] == CalibrationStep.PrimaryFiducial) {
                builder.hint("CameraForm.Offsets.Hint"); //$NON-NLS-1$
            }
            builder.length("safeZ", "ReferenceCameraPositionConfigurationWizard.SafeZPanel.SafeZLabel.text").width(120); //$NON-NLS-1$ //$NON-NLS-2$
        }
        else {
            builder.section("ReferenceCameraPositionConfigurationWizard.LocationPanel.Border.title", "crosshair") //$NON-NLS-1$ //$NON-NLS-2$
                    .measuredBy(CalibrationStep.BottomCamera, camera)
                    .location("headOffsets", "ReferenceCameraPositionConfigurationWizard.LocationPanel.LocationLabel.text", true) //$NON-NLS-1$ //$NON-NLS-2$
                    .locationButtons()
                    .length("roamingRadius", "CameraForm.RoamingRadius").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                    .hint("CameraForm.RoamingRadius.Hint"); //$NON-NLS-1$
        }
        builder.section("CameraForm.Pixel", "ruler") //$NON-NLS-1$ //$NON-NLS-2$
                .measuredBy((CalibrationStep) pixelStep[0], pixelStep[1])
                .custom("", Forms.paragraph(Translations.getString("CameraForm.Overridden"))) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleIf(f -> overridden(camera))
                .location("unitsPerPixelPrimary", "CameraForm.Pixel.Primary", false).format("%.6f") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("CameraForm.Pixel.Hint") //$NON-NLS-1$
                .section("CameraForm.Pixel3D", "layers").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("enableUnitsPerPixel3D", "CameraForm.Pixel3D.Enable", "CameraForm.Pixel3D.Enable.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .length("defaultZ", "CameraForm.Pixel3D.DefaultZ").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleIf(f -> down && Boolean.TRUE.equals(f.value("enableUnitsPerPixel3D"))) //$NON-NLS-1$
                .hint("CameraForm.Pixel3D.DefaultZ.Hint") //$NON-NLS-1$
                .length("primaryUppZ", "CameraForm.Pixel3D.PrimaryZ").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("enableUnitsPerPixel3D", Boolean.TRUE::equals) //$NON-NLS-1$
                .length("cameraPrimaryZ", "CameraForm.Pixel3D.CameraPrimaryZ").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleIf(f -> cameraZ && Boolean.TRUE.equals(f.value("enableUnitsPerPixel3D"))) //$NON-NLS-1$
                .location("unitsPerPixelSecondary", "CameraForm.Pixel3D.Secondary", false).format("%.6f") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen("enableUnitsPerPixel3D", Boolean.TRUE::equals) //$NON-NLS-1$
                .length("secondaryUppZ", "CameraForm.Pixel3D.SecondaryZ").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("enableUnitsPerPixel3D", Boolean.TRUE::equals) //$NON-NLS-1$
                .length("cameraSecondaryZ", "CameraForm.Pixel3D.CameraSecondaryZ").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleIf(f -> cameraZ && Boolean.TRUE.equals(f.value("enableUnitsPerPixel3D"))) //$NON-NLS-1$
                .section("CameraForm.Measure", "ruler").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", Forms.paragraph(Translations.getString( //$NON-NLS-1$
                        down ? "CameraForm.Measure.Down" : "CameraForm.Measure.Up"))) //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", measurement) //$NON-NLS-1$
                .visibleIf(f -> !overridden(camera));
        form[0] = builder.build();
        measurement.form = form[0];
        return form[0];
    }

    /**
     * Measuring the pixel by hand: an object of known size under the camera, a rectangle drawn
     * around it in the camera view, and the size divided by the rectangle. In 3D a second time at
     * another height. The results go in the fields, for Apply to write.
     */
    static final class UppMeasurement extends JPanel {
        private final ReferenceCamera camera;
        private final javax.swing.JTextField width = Forms.input(new javax.swing.JTextField(), true);
        private final javax.swing.JTextField height = Forms.input(new javax.swing.JTextField(), true);
        private final javax.swing.JTextField thickness = Forms.input(new javax.swing.JTextField(), true);
        private final JButton first = new JButton();
        private final JButton second = new JButton();
        private final JButton cancel = new JButton(Translations.getString("CameraConfigurationWizard.Action.CancelMeasure1")); //$NON-NLS-1$
        private int measuring;
        private Location measurementLocation;
        FormWizard form;

        UppMeasurement(ReferenceCamera camera) {
            super(new java.awt.GridLayout(0, 1, 0, 6));
            this.camera = camera;
            setOpaque(false);
            width.setColumns(6);
            height.setColumns(6);
            thickness.setColumns(6);
            add(Forms.row(org.openpnp.gui.shell.Ui.t2(Translations.getString("CameraForm.Measure.Width")), width, //$NON-NLS-1$
                    org.openpnp.gui.shell.Ui.t2(Translations.getString("CameraForm.Measure.Height")), height, //$NON-NLS-1$
                    org.openpnp.gui.shell.Ui.t2(Translations.getString("CameraForm.Measure.Thickness")), thickness)); //$NON-NLS-1$
            org.openpnp.gui.shell.Ui.movesMachine(first);
            org.openpnp.gui.shell.Ui.movesMachine(second);
            first.addActionListener(e -> step(1));
            second.addActionListener(e -> step(2));
            cancel.addActionListener(e -> stop());
            add(Forms.row(first, second, cancel));
            show(0);
        }

        private boolean threeD() {
            return form != null && Boolean.TRUE.equals(form.value("enableUnitsPerPixel3D")); //$NON-NLS-1$
        }

        private void show(int state) {
            measuring = state;
            first.setText(Translations.getString(state == 1 ? "CameraForm.Measure.Confirm" : "CameraForm.Measure.First")); //$NON-NLS-1$ //$NON-NLS-2$
            second.setText(Translations.getString(state == 2 ? "CameraForm.Measure.Confirm" : "CameraForm.Measure.Second")); //$NON-NLS-1$ //$NON-NLS-2$
            first.setEnabled(state != 2);
            second.setEnabled(state != 1);
            second.setVisible(threeD());
            thickness.setVisible(threeD() && camera.getLooking() == Camera.Looking.Up);
            cancel.setEnabled(state != 0);
        }

        @Override
        public void addNotify() {
            super.addNotify();
            show(measuring);
        }

        private void step(int which) {
            if (measuring == which) {
                confirm(which);
                show(0);
                return;
            }
            try {
                begin(which);
                show(which);
            }
            catch (Exception e) {
                UiUtils.showError(e);
                stop();
            }
        }

        private void stop() {
            CameraView view = MainFrame.get().getCameraViews().getCameraView(camera);
            if (view != null) {
                view.setSelectionEnabled(false);
            }
            show(0);
        }

        private LengthUnit units() {
            return camera.getMachine().getConfiguration().getSystemUnits();
        }

        private static double number(javax.swing.JTextField field) throws Exception {
            try {
                return Double.parseDouble(field.getText().trim());
            }
            catch (NumberFormatException e) {
                throw new Exception(Translations.getString("CameraForm.Measure.Size")); //$NON-NLS-1$
            }
        }

        private void begin(int which) throws Exception {
            LengthUnit units = units();
            Nozzle nozzle = MainFrame.get().getMachineControls().getSelectedNozzle();
            number(width);
            number(height);
            if (!threeD()) {
                measurementLocation = null;
            }
            else if (camera.getLooking() == Camera.Looking.Up) {
                Location cameraLocation = camera.getLocation().convertToUnits(units);
                Location nozzleLocation = nozzle.getLocation().convertToUnits(units);
                double thick = number(thickness);
                Location desired = which == 1
                        ? cameraLocation.add(new Location(units, 0, 0, thick, 0))
                        : cameraLocation.derive(null, null, nozzleLocation.getZ(), nozzleLocation.getRotation());
                measurementLocation = desired.subtract(new Location(units, 0, 0, thick, 0)).convertToUnits(units);
                if (which == 2) {
                    Object shown = form.value("primaryUppZ"); //$NON-NLS-1$
                    Length primaryZ = shown == null || String.valueOf(shown).trim().isEmpty() ? null
                            : new LengthConverter().convertReverse(String.valueOf(shown));
                    if (primaryZ != null && Math.abs(measurementLocation.getZ() - primaryZ.convertToUnits(units).getValue())
                            < new Length(1, LengthUnit.Millimeters).convertToUnits(units).getValue()) {
                        throw new Exception(Translations.getString("CameraForm.Measure.SameZ")); //$NON-NLS-1$
                    }
                }
                if (!nozzleLocation.equals(desired)) {
                    if (!confirmMove(String.format(Translations.getString("CameraForm.Measure.MoveNozzle"), //$NON-NLS-1$
                            nozzle.getName(), camera.getName()))) {
                        throw new Exception(Translations.getString("CameraForm.Measure.Aborted")); //$NON-NLS-1$
                    }
                    UiUtils.submitUiMachineTask(() -> {
                        MovableUtils.moveToLocationAtSafeZ(nozzle, desired);
                        MovableUtils.fireTargetedUserAction(nozzle);
                    });
                }
            }
            else {
                measurementLocation = nozzle.getLocation().convertToUnits(units);
                if (!confirmMove(String.format(Translations.getString("CameraForm.Measure.MoveCamera"), //$NON-NLS-1$
                        camera.getName(), nozzle.getName()))) {
                    throw new Exception(Translations.getString("CameraForm.Measure.Aborted")); //$NON-NLS-1$
                }
                Location target = measurementLocation;
                UiUtils.submitUiMachineTask(() -> {
                    MovableUtils.moveToLocationAtSafeZ(camera, target);
                    MovableUtils.fireTargetedUserAction(camera);
                });
            }
            CameraView view = MainFrame.get().getCameraViews().ensureCameraVisible(camera);
            view.setSelectionEnabled(true);
            view.setSelection(0, 0, 100, 100);
        }

        private boolean confirmMove(String what) {
            return Dialogs.ask(MainFrame.get(), Dialogs.Tone.Warn, "move", //$NON-NLS-1$
                    Translations.getString("CameraForm.Measure.Move.Title"), what, //$NON-NLS-1$
                    Translations.getString("CameraForm.Measure.Move.More"), //$NON-NLS-1$
                    Dialogs.Choice.primary(Translations.getString("CameraForm.Measure.Move.Action"))) == 0; //$NON-NLS-1$
        }

        private void confirm(int which) {
            LengthUnit units = units();
            LengthConverter length = new LengthConverter();
            CameraView view = MainFrame.get().getCameraViews().getCameraView(camera);
            view.setSelectionEnabled(false);
            Rectangle selection = view.getSelection();
            double w;
            double h;
            try {
                w = number(width);
                h = number(height);
            }
            catch (Exception e) {
                UiUtils.showError(e);
                return;
            }
            Location physical = camera.getCameraPhysicalLocation();
            Length z = measurementLocation == null ? null : measurementLocation.getLengthZ();
            Location upp = new Location(units, w / Math.abs(selection.width), h / Math.abs(selection.height),
                    z == null ? 0 : z.convertToUnits(units).getValue(), 0);
            if (which == 1) {
                form.setLocation("unitsPerPixelPrimary", upp); //$NON-NLS-1$
                if (z != null) {
                    form.setValue("primaryUppZ", length.convertForward(z)); //$NON-NLS-1$
                    form.setValue("cameraPrimaryZ", length.convertForward(physical.getLengthZ())); //$NON-NLS-1$
                    if (camera.getLooking() == Camera.Looking.Up) {
                        form.setValue("defaultZ", length.convertForward(z)); //$NON-NLS-1$
                    }
                }
            }
            else {
                form.setLocation("unitsPerPixelSecondary", upp); //$NON-NLS-1$
                if (z != null) {
                    form.setValue("secondaryUppZ", length.convertForward(z)); //$NON-NLS-1$
                    form.setValue("cameraSecondaryZ", length.convertForward(physical.getLengthZ())); //$NON-NLS-1$
                }
            }
            form.edit();
        }
    }

    // ==== settling ==================================================================================

    public static FormWizard settling(ReferenceCamera camera) {
        SimpleGraphView graph = new SimpleGraphView();
        graph.setGraph(camera.getSettleGraph());
        WeakForward.listen(camera, graph, (view, e) -> {
            if ("settleGraph".equals(e.getPropertyName())) { //$NON-NLS-1$
                view.setGraph((SimpleGraph) e.getNewValue());
            }
        });
        boolean fixed = camera.getHead() == null;
        boolean[] warned = {false};
        java.util.function.Predicate<FormWizard> timed = f -> f.value("settleMethod") == SettleMethod.FixedTime; //$NON-NLS-1$
        FormWizard[] form = new FormWizard[1];
        form[0] = Form.of(camera).named("CameraForm.Settling") //$NON-NLS-1$
                .section("CameraForm.Settle", "clock") //$NON-NLS-1$ //$NON-NLS-2$
                .measuredBy(CalibrationStep.CameraSettle, camera)
                .choice("settleMethod", "CameraVisionConfigurationWizard.VisionPanel.SettleMethodLabel.text", SettleMethod.class) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("settleTimeMs", "CameraForm.Settle.Time").unit("ms").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleIf(timed)
                .decimal("settleThreshold", "CameraVisionConfigurationWizard.VisionPanel.SettleThresholdLabel.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleIf(timed.negate())
                .integer("settleTimeoutMs", "CameraForm.Settle.Timeout").unit("ms").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleIf(timed.negate())
                .integer("settleDebounce", "CameraVisionConfigurationWizard.VisionPanel.DebounceFramesLabel.text").unit("CameraForm.Frames").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleIf(timed.negate())
                .section("CameraForm.Settle.Picture", "eye").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .integer("settleGaussianBlur", "CameraForm.Settle.Blur").unit("px").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleIf(timed.negate())
                .hint("CameraForm.Settle.Blur.Hint") //$NON-NLS-1$
                .decimal("settleMaskCircle", "CameraVisionConfigurationWizard.VisionPanel.CenterMaskLabel.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleIf(timed.negate())
                .hint("CameraForm.Settle.Mask.Hint") //$NON-NLS-1$
                .decimal("settleContrastEnhance", "CameraVisionConfigurationWizard.VisionPanel.EnhanceContrastLabel.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleIf(timed.negate())
                .hint("CameraVisionConfigurationWizard.VisionPanel.EnhanceContrastLabel.toolTipText") //$NON-NLS-1$
                .toggle("settleFullColor", "CameraForm.Settle.Color", "CameraVisionConfigurationWizard.VisionPanel.ColorSensitiveLabel.toolTipText") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleIf(timed.negate())
                .toggle("settleGradients", "CameraForm.Settle.Edges", "CameraVisionConfigurationWizard.VisionPanel.EdgeSensitiveLabel.toolTipText") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleIf(timed.negate())
                .section("CameraForm.Settle.Diagnostics", "activity").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("settleDiagnostics", "CameraForm.Settle.Record", "CameraVisionConfigurationWizard.VisionPanel.DiagnosticsLabel.toolTipText") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleIf(timed.negate())
                .action("CameraForm.Settle.Test", "camera", () -> settleTest(form[0], camera)) //$NON-NLS-1$ //$NON-NLS-2$
                .iconButton("left", "CameraVisionConfigurationWizard.Action.SettleTestLeftAction.Description", //$NON-NLS-1$ //$NON-NLS-2$
                        f -> jogTest(f, camera, warned, -1, 0, 0)).movesMachine()
                .iconButton("right", "CameraVisionConfigurationWizard.Action.SettleTestRightAction.Description", //$NON-NLS-1$ //$NON-NLS-2$
                        f -> jogTest(f, camera, warned, 1, 0, 0)).movesMachine()
                .iconButton("up", "CameraVisionConfigurationWizard.Action.SettleTestRearAction.Description", //$NON-NLS-1$ //$NON-NLS-2$
                        f -> jogTest(f, camera, warned, 0, 1, 0)).movesMachine()
                .iconButton("down", "CameraVisionConfigurationWizard.Action.SettleTestFrontAction.Description", //$NON-NLS-1$ //$NON-NLS-2$
                        f -> jogTest(f, camera, warned, 0, -1, 0)).movesMachine()
                .visibleIf(f -> Boolean.TRUE.equals(f.value("settleDiagnostics")) && !timed.test(f)) //$NON-NLS-1$
                .action("CameraForm.Settle.SafeZTest", "move", () -> safeZTest(form[0], camera)).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .iconButton("rccw", "CameraVisionConfigurationWizard.Action.SettleTestRotateAction.Description", //$NON-NLS-1$ //$NON-NLS-2$
                        f -> jogTest(f, camera, warned, 0, 0, 1)).movesMachine()
                .visibleIf(f -> fixed && Boolean.TRUE.equals(f.value("settleDiagnostics")) && !timed.test(f)) //$NON-NLS-1$
                .custom("CameraForm.Settle.Graph", graph) //$NON-NLS-1$
                .visibleIf(f -> Boolean.TRUE.equals(f.value("settleDiagnostics")) && !timed.test(f)) //$NON-NLS-1$
                .hint("CameraForm.Settle.Graph.Hint") //$NON-NLS-1$
                .build();
        return form[0];
    }

    /** Settling where the camera is, after Apply: the test is of what the form says. */
    private static void settleTest(FormWizard form, ReferenceCamera camera) {
        form.apply();
        UiUtils.submitUiMachineTask(() -> {
            camera.lightSettleAndCapture();
            MovableUtils.fireTargetedUserAction(camera);
        });
    }

    private static HeadMountable jogTool(ReferenceCamera camera) {
        return camera.getHead() == null ? MainFrame.get().getMachineControls().getSelectedNozzle() : camera;
    }

    /** One jog increment away and back, then settling: the moves' own shake is what it sees. */
    private static void jogTest(FormWizard form, ReferenceCamera camera, boolean[] warned, int x, int y, int c) {
        form.apply();
        HeadMountable tool = jogTool(camera);
        if ((x != 0 || y != 0) && !warned[0]) {
            String what = String.format(Translations.getString("CameraForm.Settle.Jog.What"), tool.getName()); //$NON-NLS-1$
            int chosen = Dialogs.ask(MainFrame.get(), Dialogs.Tone.Warn, "move", //$NON-NLS-1$
                    Translations.getString("CameraForm.Settle.Jog.Title"), what, //$NON-NLS-1$
                    tool instanceof Camera ? null : Translations.getString("CameraForm.Settle.Jog.NoSafeZ"), //$NON-NLS-1$
                    Dialogs.Choice.primary(Translations.getString("CameraForm.Settle.Jog.Action"))); //$NON-NLS-1$
            if (chosen != 0) {
                return;
            }
            warned[0] = true;
        }
        UiUtils.submitUiMachineTask(() -> {
            if (tool instanceof Camera) {
                camera.moveToSafeZ();
            }
            MainFrame.get().getMachineControls().getJogControlsPanel().jogTool(x, y, 0, c, tool);
            MainFrame.get().getMachineControls().getJogControlsPanel().jogTool(-x, -y, 0, -c, tool);
            camera.lightSettleAndCapture();
        });
    }

    /** To Safe Z and back, or for an up-looking camera the nozzle over to it first. */
    private static void safeZTest(FormWizard form, ReferenceCamera camera) {
        form.apply();
        HeadMountable tool = jogTool(camera);
        UiUtils.submitUiMachineTask(() -> {
            if (camera.getHead() == null && tool.getLocation().convertToUnits(LengthUnit.Millimeters)
                    .getXyzDistanceTo(camera.getLocation()) > 5.0) {
                MovableUtils.moveToLocationAtSafeZ(tool, camera.getLocation());
            }
            else {
                Location location = tool.getLocation();
                tool.moveToSafeZ();
                tool.moveTo(location);
            }
            camera.lightSettleAndCapture();
            MovableUtils.fireTargetedUserAction(camera);
        });
    }
}
