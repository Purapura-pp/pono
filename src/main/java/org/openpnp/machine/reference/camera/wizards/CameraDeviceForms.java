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
