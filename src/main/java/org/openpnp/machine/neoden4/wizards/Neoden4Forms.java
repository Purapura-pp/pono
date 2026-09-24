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

package org.openpnp.machine.neoden4.wizards;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.openpnp.Translations;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Ui;
import org.openpnp.machine.neoden4.NeoDen4Driver;
import org.openpnp.machine.neoden4.NeoDen4FeederActuator;
import org.openpnp.machine.neoden4.Neoden4Camera;
import org.openpnp.machine.neoden4.Neoden4Feeder;
import org.openpnp.machine.neoden4.Neoden4Signaler;
import org.openpnp.machine.neoden4.Neoden4SwitcherCamera;
import org.openpnp.machine.reference.feeder.wizards.DragFeederForm;
import org.openpnp.machine.reference.feeder.wizards.FeederForm;
import org.openpnp.machine.reference.wizards.ActuatorForm;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.Length;
import org.openpnp.model.Location;
import org.openpnp.model.Part;
import org.openpnp.model.Rectangle;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Driver;
import org.openpnp.spi.Machine;
import org.openpnp.spi.base.AbstractJobProcessor;
import org.openpnp.util.UiUtils;
import org.pmw.tinylog.Logger;

/**
 * The NeoDen4's own parts: the driver's coordinates, both cameras, the feeders' actuator, the
 * feeder and the buzzer.
 */
public final class Neoden4Forms {
    private Neoden4Forms() {
    }

    /** The driver's second tab, after its connection. */
    public static FormWizard driver(NeoDen4Driver driver) {
        String unit = driver.getUnits().getShortName();
        return Form.of(driver).named("Neoden4Forms.Machine") //$NON-NLS-1$
                .section("Neoden4Forms.Coordinates", "crosshair") //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("homeCoordinateX", "Neoden4Forms.HomeX").unit(unit).width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("homeCoordinateY", "Neoden4Forms.HomeY").unit(unit).width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("Neoden4Forms.Home.Hint") //$NON-NLS-1$
                .decimal("scaleFactorX", "Neoden4Forms.ScaleX").format("%.6f").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .decimal("scaleFactorY", "Neoden4Forms.ScaleY").format("%.6f").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("Neoden4Forms.Scale.Hint") //$NON-NLS-1$
                .build();
    }

    /** The camera's device tab: the size of the picture it reads. */
    public static FormWizard camera(Neoden4Camera camera) {
        return Form.of(camera).named("CameraForm.Device") //$NON-NLS-1$
                .section("Neoden4CameraConfigurationWizard.panelImage.Border.title", "camera") //$NON-NLS-1$ //$NON-NLS-2$
                .integer("width", "Neoden4CameraConfigurationWizard.lblImageWidth.text").unit("px").width(100) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .integer("height", "Neoden4CameraConfigurationWizard.lblImageHeight.text").unit("px").width(100) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .integer("timeout", "Neoden4CameraConfigurationWizard.lblTimeout.text").unit("ms").width(100) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("Neoden4Forms.Timeout.Hint") //$NON-NLS-1$
                .onApply(f -> {
                    if (camera.isDirty()) {
                        UiUtils.messageBoxOnException(camera::reinitialize);
                    }
                })
                .build();
    }

    /** One of the cameras behind the NeoDen4 camera's switch, each with its own exposure and gain. */
    public static FormWizard switcher(Neoden4SwitcherCamera camera) {
        List<Camera> sources = new ArrayList<>();
        for (Camera other : camera.getMachine().getCameras()) {
            if (other != camera) {
                sources.add(other);
            }
        }
        return Form.of(camera).named("CameraForm.Device") //$NON-NLS-1$
                .section("CameraForm.Switching", "camera") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("camera", "Neoden4SwitcherCameraConfigurationWizard.SourceCamera.text", sources, null) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("switcher", "Neoden4SwitcherCameraConfigurationWizard.SwitcherNumber.text").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("Neoden4Forms.Switcher.Hint") //$NON-NLS-1$
                .integer("exposure", "Neoden4SwitcherCameraConfigurationWizard.lblExposure.text").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("gain", "Neoden4SwitcherCameraConfigurationWizard.lblGain.text").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("Neoden4Forms.ExposureGain.Hint") //$NON-NLS-1$
                .build();
    }

    /** The buzzer sounds on every error and at every job's end; it has nothing to set. */
    public static FormWizard signaler(Neoden4Signaler signaler) {
        return Form.of(signaler).named("SignalerForms.Title") //$NON-NLS-1$
                .section("Neoden4Forms.Buzzer", "bell") //$NON-NLS-1$ //$NON-NLS-2$
                .action("Neoden4SignalerConfigurationWizard.Action.TestErrorSound", "play", //$NON-NLS-1$ //$NON-NLS-2$
                        () -> signaler.signalJobProcessorState(AbstractJobProcessor.State.ERROR))
                .action("Neoden4SignalerConfigurationWizard.Action.TestFinishedSound", "play", //$NON-NLS-1$ //$NON-NLS-2$
                        () -> signaler.signalJobProcessorState(AbstractJobProcessor.State.FINISHED))
                .hint("Neoden4Forms.Buzzer.Hint") //$NON-NLS-1$
                .build();
    }

    /** The actuator that feeds and peels a NeoDen4 feeder, by the feeder's and the peeler's ID. */
    public static FormWizard feederActuator(NeoDen4FeederActuator actuator) {
        List<Driver> drivers = new ArrayList<>();
        drivers.add(null);
        drivers.addAll(actuator.getMachine().getDrivers());
        FormWizard[] form = new FormWizard[1];
        Form.Builder builder = Form.of(actuator).named("ActuatorForm.Title") //$NON-NLS-1$
                .section("ActuatorForm.Basics", "zap") //$NON-NLS-1$ //$NON-NLS-2$
                .text("name", "ReferenceActuatorConfigurationWizard.PropertiesPanel.NameLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("driver", "ReferenceActuatorConfigurationWizard.PropertiesPanel.DriverLabel.text", drivers, null) //$NON-NLS-1$ //$NON-NLS-2$
                .section("Neoden4Forms.Feeder", "feeder") //$NON-NLS-1$ //$NON-NLS-2$
                .integer("feederId", "Neoden4Forms.FeederId").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("peelerId", "Neoden4Forms.PeelerId").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("feedStrength", "Neoden4Forms.FeedStrength").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("peelStrength", "Neoden4Forms.PeelStrength").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("peelLength", "Neoden4Forms.PeelLength").unit("%").width(100) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("Neoden4Forms.PeelLength.Hint") //$NON-NLS-1$
                .custom("Neoden4Forms.ChangeId", new IdChange(actuator, form)) //$NON-NLS-1$
                .hint("Neoden4Forms.ChangeId.Hint"); //$NON-NLS-1$
        form[0] = ActuatorForm.common(builder, actuator).build();
        return form[0];
    }

    /**
     * A new ID written into the feeder's own memory, the feeder being the one whose ID the form
     * shows; the form then takes the new ID, and keeps it.
     */
    private static final class IdChange extends JPanel {
        IdChange(NeoDen4FeederActuator actuator, FormWizard[] form) {
            JComboBox<Integer> id = new JComboBox<>();
            for (int i = 0; i <= 99; i++) {
                id.addItem(i);
            }
            JButton write = Ui.button(Translations.getString("Neoden4Forms.ChangeId.Write"), //$NON-NLS-1$
                    Ui.iconSm("download"), Ui.Size.Sm, Ui.Variant.Default); //$NON-NLS-1$
            write.addActionListener(e -> change(actuator, form[0], (Integer) id.getSelectedItem()));
            setOpaque(false);
            setLayout(new java.awt.BorderLayout());
            add(Forms.row(id, write), java.awt.BorderLayout.WEST);
        }

        private void change(NeoDen4FeederActuator actuator, FormWizard form, int newId) {
            int oldId;
            try {
                oldId = Integer.parseInt(String.valueOf(form.value("feederId")).trim()); //$NON-NLS-1$
            }
            catch (NumberFormatException e) {
                return;
            }
            NeoDen4Driver driver = driver(actuator.getMachine());
            if (driver == null || oldId == newId) {
                return;
            }
            int answer = Dialogs.ask(this, Dialogs.Tone.Warn, "feeder", //$NON-NLS-1$
                    Translations.getString("Neoden4Forms.ChangeId.Title"), //$NON-NLS-1$
                    String.format(Translations.getString("Neoden4Forms.ChangeId.What"), oldId, newId), //$NON-NLS-1$
                    Translations.getString("Neoden4Forms.ChangeId.More"), //$NON-NLS-1$
                    Dialogs.Choice.primary(Translations.getString("Neoden4Forms.ChangeId.Write"))); //$NON-NLS-1$
            if (answer != 0) {
                return;
            }
            UiUtils.submitUiMachineTask(() -> {
                driver.changeFeederId(oldId, newId);
                SwingUtilities.invokeLater(() -> {
                    form.setValue("feederId", Integer.toString(newId)); //$NON-NLS-1$
                    form.apply();
                });
            });
        }
    }

    private static NeoDen4Driver driver(Machine machine) {
        for (Driver driver : machine.getDrivers()) {
            if (driver instanceof NeoDen4Driver) {
                return (NeoDen4Driver) driver;
            }
        }
        return null;
    }

    /** The feeder's form, whose own settings are flat: its vision's are the feeder's here. */
    public static final class Bean extends AbstractModelObject implements DragFeederForm.Picture {
        private final Neoden4Feeder feeder;

        Bean(Neoden4Feeder feeder) {
            this.feeder = feeder;
        }

        public Part getPart() { return feeder.getPart(); }

        public void setPart(Part part) { feeder.setPart(part); }

        public int getFeedRetryCount() { return feeder.getFeedRetryCount(); }

        public void setFeedRetryCount(int count) { feeder.setFeedRetryCount(count); }

        public int getPickRetryCount() { return feeder.getPickRetryCount(); }

        public void setPickRetryCount(int count) { feeder.setPickRetryCount(count); }

        public Location getLocation() { return feeder.getLocation(); }

        public void setLocation(Location location) { feeder.setLocation(location); }

        public String getActuatorName() { return feeder.getActuatorName(); }

        public void setActuatorName(String name) { feeder.setActuatorName(name); }

        public Length getPartPitchInTape() { return feeder.getPartPitchInTape(); }

        public void setPartPitchInTape(Length pitch) { feeder.setPartPitchInTape(pitch); }

        public int getPartRotationInTape() { return feeder.getPartRotationInTape(); }

        public void setPartRotationInTape(int rotation) { feeder.setPartRotationInTape(rotation); }

        public int getFeedCount() { return feeder.getFeedCount(); }

        public void setFeedCount(int count) { feeder.setFeedCount(count); }

        public boolean isVisionEnabled() { return feeder.getVision().isEnabled(); }

        public void setVisionEnabled(boolean enabled) { feeder.getVision().setEnabled(enabled); }

        private Rectangle area() {
            Rectangle area = feeder.getVision().getAreaOfInterest();
            return area == null ? new Rectangle(0, 0, 0, 0) : area;
        }

        public int getAoiX() { return area().getX(); }

        public void setAoiX(int x) {
            Rectangle a = area();
            feeder.getVision().setAreaOfInterest(new Rectangle(x, a.getY(), a.getWidth(), a.getHeight()));
        }

        public int getAoiY() { return area().getY(); }

        public void setAoiY(int y) {
            Rectangle a = area();
            feeder.getVision().setAreaOfInterest(new Rectangle(a.getX(), y, a.getWidth(), a.getHeight()));
        }

        public int getAoiWidth() { return area().getWidth(); }

        public void setAoiWidth(int width) {
            Rectangle a = area();
            feeder.getVision().setAreaOfInterest(new Rectangle(a.getX(), a.getY(), width, a.getHeight()));
        }

        public int getAoiHeight() { return area().getHeight(); }

        public void setAoiHeight(int height) {
            Rectangle a = area();
            feeder.getVision().setAreaOfInterest(new Rectangle(a.getX(), a.getY(), a.getWidth(), height));
        }

        @Override
        public BufferedImage getTemplateImage() { return feeder.getVision().getTemplateImage(); }

        @Override
        public void setTemplateImage(BufferedImage image) { feeder.getVision().setTemplateImage(image); }

        @Override
        public Rectangle getAreaOfInterest() { return feeder.getVision().getAreaOfInterest(); }

        /** The feeder counts its area from the middle of the camera's image. */
        @Override
        public Point origin(Camera camera) {
            return new Point(camera.getWidth() / 2, camera.getHeight() / 2);
        }
    }

    public static FormWizard feeder(Neoden4Feeder feeder) {
        Bean bean = new Bean(feeder);
        DragFeederForm.Template template = new DragFeederForm.Template(bean);
        List<String> actuators = new ArrayList<>();
        actuators.add(null);
        for (Actuator actuator : feeder.getMachine().getActuators()) {
            actuators.add(actuator.getName());
        }
        String current = feeder.getActuatorName();
        if (current != null && !current.isEmpty() && !actuators.contains(current)) {
            actuators.add(current);
        }
        FormWizard[] form = new FormWizard[1];
        form[0] = FeederForm.common(Form.of(bean).named(feeder.getName()), feeder, true)
                .section("DragFeederForm.Feed", "move") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("actuatorName", "FeederForm.Auto.Feed", actuators, null) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Test", "play", f -> feed(f, feeder)).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .length("partPitchInTape", "DragFeederForm.PartPitch").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("Neoden4Forms.Pitch.Hint") //$NON-NLS-1$
                .integer("partRotationInTape", "PushPullForm.Rotation").unit("\u00b0").width(100) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("Neoden4Forms.Rotation.Hint") //$NON-NLS-1$
                .integer("feedCount", "Neoden4FeederConfigurationWizard.lblFeedCount.text").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .button("FeederForm.Tray.Reset", "undo", f -> { //$NON-NLS-1$ //$NON-NLS-2$
                    f.setValue("feedCount", "0"); //$NON-NLS-1$ //$NON-NLS-2$
                    f.apply();
                })
                .section("DragFeederForm.Vision", "eye") //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("visionEnabled", "DragFeederForm.UseVision", "DragFeederForm.UseVision.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .custom("DragFeederForm.Template", template) //$NON-NLS-1$
                .integer("aoiX", "DragFeederForm.AoiX").unit("px").width(100) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .integer("aoiY", "DragFeederForm.AoiY").unit("px").width(100) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .integer("aoiWidth", "DragFeederForm.AoiWidth").unit("px").width(100) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .integer("aoiHeight", "DragFeederForm.AoiHeight").unit("px").width(100) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("Neoden4Forms.Area.Hint") //$NON-NLS-1$
                .button("DragFeederForm.AoiSelect", "crosshair", template::selectArea) //$NON-NLS-1$ //$NON-NLS-2$
                .action("DragFeederForm.ResetOffsets", "undo", () -> UiUtils.messageBoxOnException(feeder::resetVisionOffsets)) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("DragFeederForm.ResetOffsets.Hint") //$NON-NLS-1$
                .onReload(f -> template.load())
                .onApply(f -> template.store())
                .build();
        template.attach(form[0]);
        return form[0];
    }

    /** One pitch fed by the feeder's actuator, after Apply; the camera looks again a second later. */
    private static void feed(FormWizard form, Neoden4Feeder feeder) {
        form.apply();
        UiUtils.submitUiMachineTask(() -> {
            String name = feeder.getActuatorName();
            if (name == null || name.isEmpty()) {
                throw new Exception(String.format(Translations.getString("FeederForm.Auto.NoActuator"), feeder.getName())); //$NON-NLS-1$
            }
            Actuator actuator = feeder.getMachine().getActuatorByName(name);
            if (actuator == null) {
                throw new Exception(String.format(Translations.getString("FeederForm.Auto.UnknownActuator"), name)); //$NON-NLS-1$
            }
            actuator.actuate(feeder.getPartPitchInTape().getValue());
            Camera camera = feeder.getMachine().getDefaultHead().getDefaultCamera();
            if (camera != null) {
                Timer timer = new Timer(1000, e -> {
                    try {
                        camera.capture();
                    }
                    catch (Exception e1) {
                        Logger.warn(e1, "Failed to refresh camera {} after feeding.", camera.getName()); //$NON-NLS-1$
                    }
                });
                timer.setRepeats(false);
                timer.start();
            }
        });
    }
}
