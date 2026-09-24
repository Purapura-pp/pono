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

import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.openpnp.Translations;
import org.openpnp.capture.CaptureDevice;
import org.openpnp.capture.CaptureFormat;
import org.openpnp.gui.shell.Forms;
import org.openpnp.util.MovableUtils;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.MainFrame;
import org.openpnp.machine.reference.camera.GstreamerCamera;
import org.openpnp.machine.reference.camera.ImageCamera;
import org.openpnp.machine.reference.camera.MjpgCaptureCamera;
import org.openpnp.machine.reference.camera.OnvifIPCamera;
import org.openpnp.machine.reference.camera.OpenCvCamera;
import org.openpnp.machine.reference.camera.OpenCvCamera.OpenCvCaptureProperty;
import org.openpnp.machine.reference.camera.OpenCvCamera.OpenCvCapturePropertyValue;
import org.openpnp.machine.reference.camera.OpenPnpCaptureCamera;
import org.openpnp.machine.reference.camera.OpenPnpCaptureCamera.CapturePropertyHolder;
import org.openpnp.machine.reference.camera.ReferenceCamera;
import org.openpnp.machine.reference.camera.SimulatedUpCamera;
import org.openpnp.machine.reference.camera.SwitcherCamera;
import org.openpnp.machine.reference.camera.Webcams;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Camera;
import org.openpnp.util.UiUtils;

/**
 * The cameras' device tabs: where each kind of camera gets its picture. Apply opens the device
 * again when what it opens has changed.
 */
public final class CameraDeviceForms {
    private CameraDeviceForms() {
    }

    private static final String TITLE = "CameraForm.Device"; //$NON-NLS-1$

    private static void reopen(ReferenceCamera camera, boolean needed) {
        if (needed) {
            UiUtils.messageBoxOnException(camera::reinitialize);
        }
    }

    public static FormWizard gstreamer(GstreamerCamera camera) {
        return Form.of(camera).named(TITLE)
                .section("GstreamerCameraConfigurationWizard.panelPipe.Border.title", "camera") //$NON-NLS-1$ //$NON-NLS-2$
                .textArea("gstPipeline", "GstreamerCameraConfigurationWizard.lblPipeline.text", 3) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("CameraForm.Gstreamer.Hint") //$NON-NLS-1$
                .build();
    }

    public static FormWizard mjpg(MjpgCaptureCamera camera) {
        return Form.of(camera).named(TITLE)
                .section(TITLE, "camera") //$NON-NLS-1$
                .text("mjpgURL", "MjpgCaptureCameraWizard.lblIP.text") //$NON-NLS-1$ //$NON-NLS-2$
                .integer("timeout", "CameraForm.Timeout").unit("ms").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .onApply(form -> reopen(camera, camera.isDirty()))
                .build();
    }

    public static FormWizard webcam(Webcams camera) {
        List<String> devices = new ArrayList<>();
        try {
            devices.addAll(camera.getDeviceIds());
        }
        catch (Exception e) {
            // No capture library, no devices: the one the camera has is still shown.
        }
        if (camera.getDeviceId() != null && !devices.contains(camera.getDeviceId())) {
            devices.add(0, camera.getDeviceId());
        }
        return Form.of(camera).named(TITLE)
                .section(TITLE, "camera") //$NON-NLS-1$
                .choice("deviceId", "WebcamConfigurationWizard.lblDeviceId.text", devices, null) //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("forceGray", "CameraForm.ForceGray", "CameraForm.ForceGray.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .onApply(form -> reopen(camera, true))
                .build();
    }

    /** The resolution as the list shows it, no preference being none. */
    public static class OnvifBean extends AbstractModelObject {
        private final OnvifIPCamera camera;

        OnvifBean(OnvifIPCamera camera) {
            this.camera = camera;
        }

        public String getHostIP() {
            return camera.getHostIP();
        }

        public void setHostIP(String hostIP) {
            camera.setHostIP(hostIP);
        }

        public String getUsername() {
            return camera.getUsername();
        }

        public void setUsername(String username) {
            camera.setUsername(username);
        }

        public String getPassword() {
            return camera.getPassword();
        }

        public void setPassword(String password) {
            camera.setPassword(password);
        }

        public String getResolution() {
            String resolution = camera.getPreferredResolution();
            return resolution == null || resolution.isEmpty() ? null : resolution;
        }

        public void setResolution(String resolution) {
            camera.setPreferredResolution(resolution == null ? "" : resolution); //$NON-NLS-1$
        }

        public int getResizeWidth() {
            return camera.getResizeWidth();
        }

        public void setResizeWidth(int width) {
            camera.setResizeWidth(width);
        }

        public int getResizeHeight() {
            return camera.getResizeHeight();
        }

        public void setResizeHeight(int height) {
            camera.setResizeHeight(height);
        }
    }

    public static FormWizard onvif(OnvifIPCamera camera) {
        List<String> resolutions = new ArrayList<>();
        resolutions.add(null);
        if (camera.getSupportedResolutions() != null) {
            camera.getSupportedResolutions()
                    .forEach(r -> resolutions.add(r.getWidth() + "x" + r.getHeight())); //$NON-NLS-1$
        }
        OnvifBean bean = new OnvifBean(camera);
        if (bean.getResolution() != null && !resolutions.contains(bean.getResolution())) {
            resolutions.add(bean.getResolution());
        }
        return Form.of(bean).named(TITLE)
                .section(TITLE, "camera") //$NON-NLS-1$
                .text("hostIP", "OnvifIPCameraConfigurationWizard.lblIP.text") //$NON-NLS-1$ //$NON-NLS-2$
                .text("username", "OnvifIPCameraConfigurationWizard.lblUsername.text") //$NON-NLS-1$ //$NON-NLS-2$
                .text("password", "OnvifIPCameraConfigurationWizard.lblPassword.text") //$NON-NLS-1$ //$NON-NLS-2$
                .section("CameraForm.Picture", "eye") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("resolution", "OnvifIPCameraConfigurationWizard.lblSupportedResolutions.text", resolutions, null) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("CameraForm.Resolution.Hint") //$NON-NLS-1$
                .integer("resizeWidth", "OnvifIPCameraConfigurationWizard.lblResizeWidth.text").unit("px").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .integer("resizeHeight", "OnvifIPCameraConfigurationWizard.lblResizeHeight.text").unit("px").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("CameraForm.Resize.Hint") //$NON-NLS-1$
                .onApply(form -> reopen(camera, camera.isDirty()))
                .build();
    }

    public static FormWizard switcher(SwitcherCamera camera) {
        List<Camera> sources = new ArrayList<>();
        for (Camera other : camera.getMachine().getCameras()) {
            if (other != camera) {
                sources.add(other);
            }
        }
        List<Actuator> actuators = new ArrayList<>();
        actuators.add(null);
        actuators.addAll(camera.getMachine().getActuators());
        return Form.of(camera).named(TITLE)
                .section("CameraForm.Switching", "camera") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("camera", "SwitcherCameraConfigurationWizard.SourceCamera.text", sources, null) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("switcher", "SwitcherCameraConfigurationWizard.SwitcherNumber.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("actuator", "SwitcherCameraConfigurationWizard.Actuator.text", actuators, null) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("actuatorDoubleValue", "SwitcherCameraConfigurationWizard.ActuatorValue.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("actuatorDelayMillis", "CameraForm.SwitchDelay").unit("ms").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("CameraForm.SwitchDelay.Hint") //$NON-NLS-1$
                .build();
    }

    public static FormWizard openCv(OpenCvCamera camera) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            indices.add(i);
        }
        CaptureProperties properties = new CaptureProperties(camera);
        FormWizard[] form = new FormWizard[1];
        form[0] = Form.of(camera).named(TITLE)
                .section(TITLE, "camera") //$NON-NLS-1$
                .choice("deviceIndex", "OpenCvCameraConfigurationWizard.lblDeviceId.text", indices, null) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("preferredWidth", "OpenCvCameraConfigurationWizard.lblPreferredWidth.text").unit("px").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .integer("preferredHeight", "OpenCvCameraConfigurationWizard.lblPreferredHeight.text").unit("px").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("CameraForm.Native.Hint") //$NON-NLS-1$
                .section("OpenCvCameraConfigurationWizard.PropertiesExperimental.Border.title", "sliders").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", properties) //$NON-NLS-1$
                .onReload(f -> properties.load())
                .onApply(f -> {
                    boolean changed = properties.store();
                    reopen(camera, changed || camera.isDirty());
                })
                .build();
        properties.form = form[0];
        return form[0];
    }

    public static FormWizard image(ImageCamera camera) {
        return Form.of(camera).named(TITLE)
                .section("ImageCameraConfigurationWizard.GeneralPanel.Border.title", "camera") //$NON-NLS-1$ //$NON-NLS-2$
                .text("sourceUri", "ImageCameraConfigurationWizard.GeneralPanel.SourceUrlLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .button("ImageCameraConfigurationWizard.Action.Browse", "folder", form -> { //$NON-NLS-1$ //$NON-NLS-2$
                    String uri = chooseImage();
                    if (uri != null) {
                        form.setValue("sourceUri", uri); //$NON-NLS-1$
                    }
                })
                .integer("viewWidth", "CameraForm.ViewWidth").unit("px").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .integer("viewHeight", "CameraForm.ViewHeight").unit("px").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .decimal("simulatedScale", "ImageCameraConfigurationWizard.GeneralPanel.ViewingScaleLabel.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("simulatedFlipped", "CameraForm.Mirrored", //$NON-NLS-1$ //$NON-NLS-2$
                        "ImageCameraConfigurationWizard.GeneralPanel.MirroredViewLabel.toolTipText") //$NON-NLS-1$
                .section("CameraForm.Mounting", "move") //$NON-NLS-1$ //$NON-NLS-2$
                .angle("simulatedRotation", "ImageCameraConfigurationWizard.GeneralPanel.ZRotationLabel.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("ImageCameraConfigurationWizard.GeneralPanel.ZRotationLabel.toolTipText") //$NON-NLS-1$
                .angle("simulatedYRotation", "ImageCameraConfigurationWizard.GeneralPanel.YRotationLabel.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("ImageCameraConfigurationWizard.GeneralPanel.YRotationLabel.toolTipText") //$NON-NLS-1$
                .decimal("simulatedDistortion", "CameraForm.Distortion").unit("%").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("CameraForm.Distortion.Hint") //$NON-NLS-1$
                .location("imageUnitsPerPixel", "CameraForm.SimulatedUpp", false).format("%.6f") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("CameraForm.SimulatedUpp.Hint") //$NON-NLS-1$
                .location("imageOffset", "ImageCameraConfigurationWizard.GeneralPanel.OffsetLabel.text", false) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("ImageCameraConfigurationWizard.GeneralPanel.OffsetLabel.toolTipText") //$NON-NLS-1$
                .section("ImageCameraConfigurationWizard.GeneralPanel.ExtraPanel.Border.title", "target").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .length("focalLength", "ImageCameraConfigurationWizard.GeneralPanel.ExtraPanel.FocalLengthLabel.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .length("sensorDiagonal", "CameraForm.SensorDiagonal").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .location("primaryFiducial", "ImageCameraConfigurationWizard.GeneralPanel.ExtraPanel.PrimaryFiducialLabel.text", false).withZ() //$NON-NLS-1$ //$NON-NLS-2$
                .location("secondaryFiducial", "ImageCameraConfigurationWizard.GeneralPanel.ExtraPanel.SecondaryFiducialLabel.text", false).withZ() //$NON-NLS-1$ //$NON-NLS-2$
                .onApply(form -> reopen(camera, true))
                .build();
    }

    private static String chooseImage() {
        FileDialog dialog = new FileDialog(MainFrame.get());
        dialog.setFilenameFilter((dir, name) -> {
            String lower = name.toLowerCase();
            for (String extension : new String[] {".png", ".jpg", ".gif", ".tif", ".tiff"}) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                if (lower.endsWith(extension)) {
                    return true;
                }
            }
            return false;
        });
        dialog.setVisible(true);
        if (dialog.getFile() == null) {
            return null;
        }
        return new File(new File(dialog.getDirectory()), dialog.getFile()).toURI().toString();
    }

    public static FormWizard simulatedUp(SimulatedUpCamera camera) {
        return Form.of(camera).named(TITLE)
                .section("SimulatedUpCameraConfigurationWizard.GeneralPanel.Border.title", "camera") //$NON-NLS-1$ //$NON-NLS-2$
                .integer("viewWidth", "CameraForm.ViewWidth").unit("px").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .integer("viewHeight", "CameraForm.ViewHeight").unit("px").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .toggle("simulatedFlipped", "CameraForm.Mirrored", //$NON-NLS-1$ //$NON-NLS-2$
                        "SimulatedUpCameraConfigurationWizard.GeneralPanel.MirroredViewLabel.toolTipText") //$NON-NLS-1$
                .choice("backgroundScenario", "SimulatedUpCameraConfigurationWizard.lblBackgroundColor.text", //$NON-NLS-1$ //$NON-NLS-2$
                        SimulatedUpCamera.BackgroundScenario.class)
                .hint("SimulatedUpCameraConfigurationWizard.lblBackgroundColor.toolTipText") //$NON-NLS-1$
                .toggle("simulateFocalBlur", "CameraForm.FocalBlur", "CameraForm.FocalBlur.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .section("CameraForm.Mounting", "move") //$NON-NLS-1$ //$NON-NLS-2$
                .location("simulatedLocation", "SimulatedUpCameraConfigurationWizard.GeneralPanel.CameraLocationLabel.text", true) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("CameraForm.SimulatedLocation.Hint") //$NON-NLS-1$
                .location("simulatedUnitsPerPixel", "CameraForm.SimulatedUpp", false).format("%.6f") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("CameraForm.SimulatedUpp.Hint") //$NON-NLS-1$
                .length("focalLength", "SimulatedUpCameraConfigurationWizard.GeneralPanel.FocalLengthLabel.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .length("sensorDiagonal", "CameraForm.SensorDiagonal").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .section("CameraForm.PickErrors", "nozzle").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .location("errorOffsets", "SimulatedUpCameraConfigurationWizard.GeneralPanel.PickErrorOffsetsLabel.text", true) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("CameraForm.PickErrors.Hint") //$NON-NLS-1$
                .build();
    }

    private static List<String> formats(CaptureDevice device) {
        List<String> formats = new ArrayList<>();
        if (device != null) {
            // A format's equals() is not stable: its name is what is chosen.
            for (CaptureFormat format : device.getFormats()) {
                formats.add(format.toString());
            }
        }
        return formats;
    }

    public static FormWizard openPnpCapture(OpenPnpCaptureCamera camera) {
        List<CaptureDevice> devices = new ArrayList<>(camera.getCaptureDevices());
        if (camera.getDevice() != null && !devices.contains(camera.getDevice())) {
            devices.add(0, camera.getDevice());
        }
        List<String> formats = formats(camera.getDevice());
        if (camera.getFormatName() != null && !formats.contains(camera.getFormatName())) {
            formats.add(0, camera.getFormatName());
        }
        CaptureControls controls = new CaptureControls(camera);
        javax.swing.JLabel fps = org.openpnp.gui.shell.Ui.t2("\u2014"); //$NON-NLS-1$
        Object[] device = {camera.getDevice()};
        FormWizard[] form = new FormWizard[1];
        form[0] = Form.of(camera).named(TITLE)
                .section(TITLE, "camera") //$NON-NLS-1$
                .choice("device", "OpenPnpCaptureCameraConfigurationWizard.DevicePanel.DeviceLabel.text", devices, null) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("formatName", "OpenPnpCaptureCameraConfigurationWizard.DevicePanel.FormatLabel.text", formats, null) //$NON-NLS-1$ //$NON-NLS-2$
                .custom("OpenPnpCaptureCameraConfigurationWizard.DevicePanel.CaptureFPSLabel.text", fps) //$NON-NLS-1$
                .action("OpenPnpCaptureCameraConfigurationWizard.DevicePanel.TestButton.text", "activity", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                    form[0].apply();
                    fps.setText(Translations.getString("OpenPnpCaptureCameraConfigurationWizard.DevicePanel.TestingLabel.text")); //$NON-NLS-1$
                    javax.swing.SwingUtilities.invokeLater(() -> UiUtils.messageBoxOnException(() -> fps.setText(
                            String.format(java.util.Locale.US, "%.0f fps", camera.estimateCaptureFps())))); //$NON-NLS-1$
                })
                .hint("CameraForm.Capture.Fps.Hint") //$NON-NLS-1$
                .section("CameraForm.Capture.Properties", "sliders").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", controls) //$NON-NLS-1$
                .toggle("freezeProperties", "CameraForm.Capture.Freeze", "CameraForm.Capture.Freeze.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .action("OpenPnpCaptureCameraConfigurationWizard.PropertiesPanel.ReapplyToCameraButton.text", "refresh", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                    camera.reapplyProperties();
                    MovableUtils.fireTargetedUserAction(camera);
                })
                .visibleWhen("freezeProperties", Boolean.TRUE::equals) //$NON-NLS-1$
                .onChange(f -> {
                    Object chosen = f.value("device"); //$NON-NLS-1$
                    if (chosen != device[0]) {
                        device[0] = chosen;
                        f.setItems("formatName", formats((CaptureDevice) chosen)); //$NON-NLS-1$
                    }
                })
                .onReload(f -> controls.load())
                .onApply(f -> {
                    controls.store();
                    reopen(camera, true);
                    controls.load();
                })
                .build();
        controls.form = form[0];
        return form[0];
    }

    /**
     * The capture device's own controls, exposure, gain and the others: each automatic or at a
     * value within the device's range, a control the device lacks greyed. The edits are the
     * form's until Apply.
     */
    static final class CaptureControls extends JPanel {
        private static final String[] NAMES = {"BackLightCompensation", "Brightness", "Contrast", "Exposure", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                "Focus", "Gain", "Gamma", "Hue", "PowerLineFrequency", "Saturation", "Sharpness", "WhiteBalance", "Zoom"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$
        private final OpenPnpCaptureCamera camera;
        private final List<JCheckBox> autos = new ArrayList<>();
        private final List<JTextField> values = new ArrayList<>();
        private final List<javax.swing.JLabel> ranges = new ArrayList<>();
        private boolean showing;
        FormWizard form;

        CaptureControls(OpenPnpCaptureCamera camera) {
            super(new java.awt.GridBagLayout());
            this.camera = camera;
            setOpaque(false);
            java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
            c.insets = new java.awt.Insets(2, 0, 2, 8);
            c.anchor = java.awt.GridBagConstraints.WEST;
            for (int i = 0; i < NAMES.length; i++) {
                c.gridy = i;
                c.gridx = 0;
                add(org.openpnp.gui.shell.Ui.t2(Translations.getString("CameraForm.Capture." + NAMES[i])), c); //$NON-NLS-1$
                JCheckBox auto = new JCheckBox(Translations.getString("OpenPnpCaptureCameraConfigurationWizard.PropertiesPanel.AutoLabel.text")); //$NON-NLS-1$
                auto.setOpaque(false);
                auto.addActionListener(e -> edited());
                c.gridx = 1;
                add(auto, c);
                JTextField value = Forms.input(new JTextField(), true);
                value.setColumns(6);
                value.getDocument().addDocumentListener(new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent e) {
                        edited();
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e) {
                        edited();
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e) {
                        edited();
                    }
                });
                c.gridx = 2;
                add(value, c);
                javax.swing.JLabel range = org.openpnp.gui.shell.Ui.t2(""); //$NON-NLS-1$
                c.gridx = 3;
                add(range, c);
                autos.add(auto);
                values.add(value);
                ranges.add(range);
            }
        }

        private CapturePropertyHolder holder(int i) {
            switch (NAMES[i]) {
                case "BackLightCompensation": //$NON-NLS-1$
                    return camera.getBackLightCompensation();
                case "Brightness": //$NON-NLS-1$
                    return camera.getBrightness();
                case "Contrast": //$NON-NLS-1$
                    return camera.getContrast();
                case "Exposure": //$NON-NLS-1$
                    return camera.getExposure();
                case "Focus": //$NON-NLS-1$
                    return camera.getFocus();
                case "Gain": //$NON-NLS-1$
                    return camera.getGain();
                case "Gamma": //$NON-NLS-1$
                    return camera.getGamma();
                case "Hue": //$NON-NLS-1$
                    return camera.getHue();
                case "PowerLineFrequency": //$NON-NLS-1$
                    return camera.getPowerLineFrequency();
                case "Saturation": //$NON-NLS-1$
                    return camera.getSaturation();
                case "Sharpness": //$NON-NLS-1$
                    return camera.getSharpness();
                case "WhiteBalance": //$NON-NLS-1$
                    return camera.getWhiteBalance();
                default:
                    return camera.getZoom();
            }
        }

        void load() {
            showing = true;
            for (int i = 0; i < NAMES.length; i++) {
                CapturePropertyHolder holder = holder(i);
                boolean supported = holder != null && holder.isSupported();
                autos.get(i).setSelected(supported && holder.isAuto());
                autos.get(i).setEnabled(supported && holder.isAutoSupported());
                values.get(i).setText(supported ? String.valueOf(holder.getValue()) : ""); //$NON-NLS-1$
                values.get(i).setEnabled(supported);
                ranges.get(i).setText(supported ? String.format(Translations.getString("CameraForm.Capture.Range"), //$NON-NLS-1$
                        holder.getMin(), holder.getMax(), holder.getDefault()) : Translations.getString("CameraForm.Capture.Unsupported")); //$NON-NLS-1$
            }
            showing = false;
        }

        void store() {
            for (int i = 0; i < NAMES.length; i++) {
                CapturePropertyHolder holder = holder(i);
                if (holder == null || !holder.isSupported()) {
                    continue;
                }
                if (holder.isAutoSupported() && holder.isAuto() != autos.get(i).isSelected()) {
                    holder.setAuto(autos.get(i).isSelected());
                }
                try {
                    int value = Integer.parseInt(values.get(i).getText().trim());
                    if (value != holder.getValue()) {
                        holder.setValue(value);
                    }
                }
                catch (NumberFormatException e) {
                    // Left as the device has it: the field shows it again after Apply.
                }
            }
        }

        private void edited() {
            if (!showing && form != null) {
                form.edit();
            }
        }
    }

    /**
     * The capture properties the camera sets when it opens: one at a time, each with its value
     * and when to set it. The edits are the form's until Apply.
     */
    static final class CaptureProperties extends JPanel {
        private final OpenCvCamera camera;
        private final List<OpenCvCapturePropertyValue> values = new ArrayList<>();
        private final JComboBox<OpenCvCaptureProperty> property = new JComboBox<>(OpenCvCaptureProperty.values());
        private final JTextField value = new JTextField(8);
        private final JCheckBox beforeOpen = new JCheckBox(
                Translations.getString("OpenCvCameraConfigurationWizard.setBeforeOpenCk.text")); //$NON-NLS-1$
        private final JCheckBox afterOpen = new JCheckBox(
                Translations.getString("OpenCvCameraConfigurationWizard.setAfterOpenCk.text")); //$NON-NLS-1$
        private boolean showing;
        private boolean edited;
        FormWizard form;

        CaptureProperties(OpenCvCamera camera) {
            super(new FlowLayout(FlowLayout.LEFT, 6, 0));
            this.camera = camera;
            setOpaque(false);
            JButton read = new JButton(Icons.refresh);
            read.setToolTipText(Translations.getString("CameraForm.CaptureProperty.Read")); //$NON-NLS-1$
            read.addActionListener(e -> value.setText(
                    String.valueOf(camera.getOpenCvCapturePropertyValue((OpenCvCaptureProperty) property.getSelectedItem()))));
            add(property);
            add(value);
            add(read);
            add(beforeOpen);
            add(afterOpen);
            property.addItemListener(e -> show((OpenCvCaptureProperty) property.getSelectedItem()));
            value.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    changed();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    changed();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    changed();
                }
            });
            beforeOpen.addActionListener(e -> changed());
            afterOpen.addActionListener(e -> changed());
        }

        void load() {
            values.clear();
            for (OpenCvCapturePropertyValue held : camera.getProperties()) {
                values.add(copy(held));
            }
            edited = false;
            show((OpenCvCaptureProperty) property.getSelectedItem());
        }

        /** Writes the edits, telling whether there were any. */
        boolean store() {
            if (!edited) {
                return false;
            }
            camera.getProperties().clear();
            for (OpenCvCapturePropertyValue edit : values) {
                camera.getProperties().add(copy(edit));
            }
            edited = false;
            return true;
        }

        private void show(OpenCvCaptureProperty which) {
            showing = true;
            OpenCvCapturePropertyValue held = find(which);
            value.setText(held == null ? "" : String.valueOf(held.value)); //$NON-NLS-1$
            beforeOpen.setSelected(held != null && held.setBeforeOpen);
            afterOpen.setSelected(held != null && held.setAfterOpen);
            showing = false;
        }

        private void changed() {
            if (showing) {
                return;
            }
            OpenCvCaptureProperty which = (OpenCvCaptureProperty) property.getSelectedItem();
            OpenCvCapturePropertyValue held = find(which);
            Double number = number(value.getText());
            if (number == null) {
                values.remove(held);
            }
            else {
                if (held == null) {
                    held = new OpenCvCapturePropertyValue();
                    held.property = which;
                    values.add(held);
                }
                held.value = number;
                held.setBeforeOpen = beforeOpen.isSelected();
                held.setAfterOpen = afterOpen.isSelected();
            }
            edited = true;
            if (form != null) {
                form.edit();
            }
        }

        private static Double number(String text) {
            try {
                return Double.valueOf(text.trim());
            }
            catch (NumberFormatException e) {
                return null;
            }
        }

        private OpenCvCapturePropertyValue find(OpenCvCaptureProperty which) {
            for (OpenCvCapturePropertyValue held : values) {
                if (held.property == which) {
                    return held;
                }
            }
            return null;
        }

        private static OpenCvCapturePropertyValue copy(OpenCvCapturePropertyValue from) {
            OpenCvCapturePropertyValue to = new OpenCvCapturePropertyValue();
            to.property = from.property;
            to.value = from.value;
            to.setBeforeOpen = from.setBeforeOpen;
            to.setAfterOpen = from.setAfterOpen;
            return to;
        }
    }
}
