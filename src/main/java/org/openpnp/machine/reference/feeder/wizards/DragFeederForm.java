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

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.components.CameraView;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.machine.reference.ReferenceFeeder;
import org.openpnp.machine.reference.feeder.ReferenceDragFeeder;
import org.openpnp.machine.reference.feeder.ReferenceLeverFeeder;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.Length;
import org.openpnp.model.Location;
import org.openpnp.model.Part;
import org.openpnp.model.Rectangle;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Nozzle;
import org.openpnp.util.UiUtils;

/**
 * The drag and the lever feeder, alike but for the backoff: where the actuator pushes the tape,
 * how far and how fast, and the template vision finds the pocket with.
 */
public final class DragFeederForm {
    private DragFeederForm() {
    }

    /** The template image a feeder's vision looks for, and the area of the camera's image it looks in. */
    public interface Picture {
        BufferedImage getTemplateImage();

        void setTemplateImage(BufferedImage image);

        Rectangle getAreaOfInterest();

        /** Where the area's coordinates count from in the camera's image: its top left corner here. */
        default java.awt.Point origin(Camera camera) {
            return new java.awt.Point(0, 0);
        }
    }

    /** What the two feeders have alike, each in its own class. */
    interface Target extends Picture {
        ReferenceFeeder feeder();

        Length getPartPitch();

        void setPartPitch(Length pitch);

        double getFeedSpeed();

        void setFeedSpeed(double speed);

        String getActuatorName();

        void setActuatorName(String name);

        String getPeelOffActuatorName();

        void setPeelOffActuatorName(String name);

        Location getFeedStartLocation();

        void setFeedStartLocation(Location location);

        Location getFeedEndLocation();

        void setFeedEndLocation(Location location);

        Length getBackoffDistance();

        void setBackoffDistance(Length distance);

        boolean isVisionEnabled();

        void setVisionEnabled(boolean enabled);

        void setAreaOfInterest(Rectangle area);

        void resetVisionOffsets();
    }

    static Target of(ReferenceDragFeeder f) {
        return new Target() {
            public ReferenceFeeder feeder() { return f; }
            public Length getPartPitch() { return f.getPartPitch(); }
            public void setPartPitch(Length pitch) { f.setPartPitch(pitch); }
            public double getFeedSpeed() { return f.getFeedSpeed(); }
            public void setFeedSpeed(double speed) { f.setFeedSpeed(speed); }
            public String getActuatorName() { return f.getActuatorName(); }
            public void setActuatorName(String name) { f.setActuatorName(name); }
            public String getPeelOffActuatorName() { return f.getPeelOffActuatorName(); }
            public void setPeelOffActuatorName(String name) { f.setPeelOffActuatorName(name); }
            public Location getFeedStartLocation() { return f.getFeedStartLocation(); }
            public void setFeedStartLocation(Location location) { f.setFeedStartLocation(location); }
            public Location getFeedEndLocation() { return f.getFeedEndLocation(); }
            public void setFeedEndLocation(Location location) { f.setFeedEndLocation(location); }
            public Length getBackoffDistance() { return f.getBackoffDistance(); }
            public void setBackoffDistance(Length distance) { f.setBackoffDistance(distance); }
            public boolean isVisionEnabled() { return f.getVision().isEnabled(); }
            public void setVisionEnabled(boolean enabled) { f.getVision().setEnabled(enabled); }
            public BufferedImage getTemplateImage() { return f.getVision().getTemplateImage(); }
            public void setTemplateImage(BufferedImage image) { f.getVision().setTemplateImage(image); }
            public Rectangle getAreaOfInterest() { return f.getVision().getAreaOfInterest(); }
            public void setAreaOfInterest(Rectangle area) { f.getVision().setAreaOfInterest(area); }
            public void resetVisionOffsets() { f.resetVisionOffsets(); }
        };
    }

    static Target of(ReferenceLeverFeeder f) {
        return new Target() {
            public ReferenceFeeder feeder() { return f; }
            public Length getPartPitch() { return f.getPartPitch(); }
            public void setPartPitch(Length pitch) { f.setPartPitch(pitch); }
            public double getFeedSpeed() { return f.getFeedSpeed(); }
            public void setFeedSpeed(double speed) { f.setFeedSpeed(speed); }
            public String getActuatorName() { return f.getActuatorName(); }
            public void setActuatorName(String name) { f.setActuatorName(name); }
            public String getPeelOffActuatorName() { return f.getPeelOffActuatorName(); }
            public void setPeelOffActuatorName(String name) { f.setPeelOffActuatorName(name); }
            public Location getFeedStartLocation() { return f.getFeedStartLocation(); }
            public void setFeedStartLocation(Location location) { f.setFeedStartLocation(location); }
            public Location getFeedEndLocation() { return f.getFeedEndLocation(); }
            public void setFeedEndLocation(Location location) { f.setFeedEndLocation(location); }
            public Length getBackoffDistance() { return null; }
            public void setBackoffDistance(Length distance) { }
            public boolean isVisionEnabled() { return f.getVision().isEnabled(); }
            public void setVisionEnabled(boolean enabled) { f.getVision().setEnabled(enabled); }
            public BufferedImage getTemplateImage() { return f.getVision().getTemplateImage(); }
            public void setTemplateImage(BufferedImage image) { f.getVision().setTemplateImage(image); }
            public Rectangle getAreaOfInterest() { return f.getVision().getAreaOfInterest(); }
            public void setAreaOfInterest(Rectangle area) { f.getVision().setAreaOfInterest(area); }
            public void resetVisionOffsets() { f.resetVisionOffsets(); }
        };
    }

    /** The feeder's settings and its vision's, flat, the area of interest's four numbers each a field. */
    public static class Bean extends AbstractModelObject {
        private final Target t;

        Bean(Target target) {
            this.t = target;
        }

        public Part getPart() { return t.feeder().getPart(); }
        public void setPart(Part part) { t.feeder().setPart(part); }
        public int getFeedRetryCount() { return t.feeder().getFeedRetryCount(); }
        public void setFeedRetryCount(int count) { t.feeder().setFeedRetryCount(count); }
        public int getPickRetryCount() { return t.feeder().getPickRetryCount(); }
        public void setPickRetryCount(int count) { t.feeder().setPickRetryCount(count); }
        public Location getLocation() { return t.feeder().getLocation(); }
        public void setLocation(Location location) { t.feeder().setLocation(location); }
        public Length getPartPitch() { return t.getPartPitch(); }
        public void setPartPitch(Length pitch) { t.setPartPitch(pitch); }
        public double getFeedSpeed() { return t.getFeedSpeed(); }
        public void setFeedSpeed(double speed) { t.setFeedSpeed(speed); }
        public String getActuatorName() { return t.getActuatorName(); }
        public void setActuatorName(String name) { t.setActuatorName(name); }
        public String getPeelOffActuatorName() { return t.getPeelOffActuatorName(); }
        public void setPeelOffActuatorName(String name) { t.setPeelOffActuatorName(name); }
        public Location getFeedStartLocation() { return t.getFeedStartLocation(); }
        public void setFeedStartLocation(Location location) { t.setFeedStartLocation(location); }
        public Location getFeedEndLocation() { return t.getFeedEndLocation(); }
        public void setFeedEndLocation(Location location) { t.setFeedEndLocation(location); }
        public Length getBackoffDistance() { return t.getBackoffDistance(); }
        public void setBackoffDistance(Length distance) { t.setBackoffDistance(distance); }
        public boolean isVisionEnabled() { return t.isVisionEnabled(); }
        public void setVisionEnabled(boolean enabled) { t.setVisionEnabled(enabled); }

        private Rectangle area() {
            Rectangle area = t.getAreaOfInterest();
            return area == null ? new Rectangle(0, 0, 0, 0) : area;
        }

        public int getAoiX() { return area().getX(); }

        public void setAoiX(int x) {
            Rectangle a = area();
            t.setAreaOfInterest(new Rectangle(x, a.getY(), a.getWidth(), a.getHeight()));
        }

        public int getAoiY() { return area().getY(); }

        public void setAoiY(int y) {
            Rectangle a = area();
            t.setAreaOfInterest(new Rectangle(a.getX(), y, a.getWidth(), a.getHeight()));
        }

        public int getAoiWidth() { return area().getWidth(); }

        public void setAoiWidth(int width) {
            Rectangle a = area();
            t.setAreaOfInterest(new Rectangle(a.getX(), a.getY(), width, a.getHeight()));
        }

        public int getAoiHeight() { return area().getHeight(); }

        public void setAoiHeight(int height) {
            Rectangle a = area();
            t.setAreaOfInterest(new Rectangle(a.getX(), a.getY(), a.getWidth(), height));
        }
    }

    public static FormWizard drag(ReferenceDragFeeder feeder) {
        return build(of(feeder), true);
    }

    public static FormWizard lever(ReferenceLeverFeeder feeder) {
        return build(of(feeder), false);
    }

    private static FormWizard build(Target target, boolean backoff) {
        ReferenceFeeder feeder = target.feeder();
        Template template = new Template(target);
        FormWizard[] form = new FormWizard[1];
        Form.Builder builder = FeederForm.common(Form.of(new Bean(target)).named(feeder.getName()), feeder, true)
                .section("DragFeederForm.Feed", "move") //$NON-NLS-1$ //$NON-NLS-2$
                .location("feedStartLocation", "DragFeederForm.Start", false).withZ().locationButtons() //$NON-NLS-1$ //$NON-NLS-2$
                .location("feedEndLocation", "DragFeederForm.End", false).withZ().locationButtons() //$NON-NLS-1$ //$NON-NLS-2$
                .length("partPitch", "DragFeederForm.PartPitch").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .percent("feedSpeed", "DragFeederForm.FeedSpeed").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .text("actuatorName", "DragFeederForm.Actuator") //$NON-NLS-1$ //$NON-NLS-2$
                .text("peelOffActuatorName", "DragFeederForm.PeelOff") //$NON-NLS-1$ //$NON-NLS-2$
                .hint("DragFeederForm.Actuators.Hint"); //$NON-NLS-1$
        if (backoff) {
            builder.length("backoffDistance", "DragFeederForm.Backoff").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                    .hint("DragFeederForm.Backoff.Hint"); //$NON-NLS-1$
        }
        form[0] = builder.action("DragFeederForm.TryFeed", "play", () -> tryFeed(form[0], feeder)).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .section("DragFeederForm.Vision", "eye") //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("visionEnabled", "DragFeederForm.UseVision", "DragFeederForm.UseVision.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .custom("DragFeederForm.Template", template) //$NON-NLS-1$
                .integer("aoiX", "DragFeederForm.AoiX").unit("px").width(100) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .integer("aoiY", "DragFeederForm.AoiY").unit("px").width(100) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .integer("aoiWidth", "DragFeederForm.AoiWidth").unit("px").width(100) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .integer("aoiHeight", "DragFeederForm.AoiHeight").unit("px").width(100) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .button("DragFeederForm.AoiSelect", "crosshair", template::selectArea) //$NON-NLS-1$ //$NON-NLS-2$
                .action("DragFeederForm.ResetOffsets", "undo", () -> UiUtils.messageBoxOnException(target::resetVisionOffsets)) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("DragFeederForm.ResetOffsets.Hint") //$NON-NLS-1$
                .onReload(f -> template.load())
                .onApply(f -> template.store())
                .build();
        template.attach(form[0]);
        return form[0];
    }

    /** Feeding once, as a job does, with the selected nozzle, after Apply. */
    private static void tryFeed(FormWizard form, ReferenceFeeder feeder) {
        form.apply();
        UiUtils.submitUiMachineTask(() -> {
            Nozzle nozzle = MainFrame.get().getMachineControls().getSelectedNozzle();
            feeder.feed(nozzle);
        });
    }

    private static Camera camera() throws Exception {
        return MainFrame.get().getMachineControls().getSelectedTool().getHead().getDefaultCamera();
    }

    private static CameraView view() throws Exception {
        return MainFrame.get().getCameraViews().setSelectedCamera(camera());
    }

    /**
     * The template image and the rectangles drawn in the camera view that take a new one and the
     * area of interest, whose fields are {@code aoiX}, {@code aoiY}, {@code aoiWidth} and
     * {@code aoiHeight}. A new image is the form's until Apply.
     */
    public static final class Template extends JPanel {
        private final Picture target;
        private final JLabel image = new JLabel();
        private final JButton select = new JButton();
        private final JButton cancel = new JButton(Translations.getString("DragFeederForm.Cancel")); //$NON-NLS-1$
        private BufferedImage staged;
        private boolean changed;
        private int selecting;
        private FormWizard form;

        public Template(Picture target) {
            super(new BorderLayout(0, 6));
            this.target = target;
            setOpaque(false);
            add(image, BorderLayout.CENTER);
            cancel.setToolTipText(Translations.getString("DragFeederForm.Cancel.Tip")); //$NON-NLS-1$
            add(Forms.row(select, cancel), BorderLayout.SOUTH);
            select.addActionListener(e -> {
                if (selecting == 1) {
                    confirmTemplate();
                }
                else {
                    begin(1);
                }
            });
            cancel.addActionListener(e -> end());
            end();
        }

        /** The form whose Apply a new image waits for. */
        public void attach(FormWizard form) {
            this.form = form;
        }

        public void load() {
            staged = target.getTemplateImage();
            changed = false;
            display(staged);
        }

        public void store() {
            if (changed) {
                target.setTemplateImage(staged);
                changed = false;
            }
        }

        private void display(BufferedImage picture) {
            image.setIcon(picture == null ? null : new ImageIcon(picture));
            image.setText(picture == null ? Translations.getString("DragFeederForm.NoTemplate") : null); //$NON-NLS-1$
        }

        private void begin(int what) {
            UiUtils.messageBoxOnException(() -> {
                Camera camera = camera();
                CameraView view = MainFrame.get().getCameraViews().setSelectedCamera(camera);
                view.setSelectionEnabled(true);
                Rectangle area = target.getAreaOfInterest();
                if (what == 2 && area != null && area.getWidth() > 0 && area.getHeight() > 0) {
                    java.awt.Point origin = target.origin(camera);
                    view.setSelection(area.getX() + origin.x, area.getY() + origin.y, area.getWidth(), area.getHeight());
                }
                else {
                    view.setSelection(0, 0, 100, 100);
                }
                selecting = what;
                select.setText(Translations.getString(what == 1 ? "DragFeederForm.Confirm" : "DragFeederForm.TemplateSelect")); //$NON-NLS-1$ //$NON-NLS-2$
                cancel.setEnabled(true);
            });
        }

        private void end() {
            if (selecting != 0) {
                UiUtils.messageBoxOnException(() -> view().setSelectionEnabled(false));
            }
            selecting = 0;
            select.setText(Translations.getString("DragFeederForm.TemplateSelect")); //$NON-NLS-1$
            cancel.setEnabled(false);
        }

        private void confirmTemplate() {
            UiUtils.messageBoxOnException(() -> {
                BufferedImage picture = view().captureSelectionImage();
                if (picture == null) {
                    MessageBoxes.errorBox(MainFrame.get(),
                            Translations.getString("DialogMessages.NoImageSelected.Title"), //$NON-NLS-1$
                            Translations.getString("DialogMessages.NoImageSelected.Message")); //$NON-NLS-1$
                }
                else {
                    staged = picture;
                    changed = true;
                    display(picture);
                    if (form != null) {
                        form.edit();
                    }
                }
            });
            end();
        }

        /** The area of interest drawn in the camera view: a second click takes it. */
        public void selectArea(FormWizard form) {
            if (selecting == 2) {
                UiUtils.messageBoxOnException(() -> {
                    Camera camera = camera();
                    java.awt.Rectangle rect = MainFrame.get().getCameraViews().setSelectedCamera(camera).getSelection();
                    java.awt.Point origin = target.origin(camera);
                    form.setValue("aoiX", Integer.toString(rect.x - origin.x)); //$NON-NLS-1$
                    form.setValue("aoiY", Integer.toString(rect.y - origin.y)); //$NON-NLS-1$
                    form.setValue("aoiWidth", Integer.toString(rect.width)); //$NON-NLS-1$
                    form.setValue("aoiHeight", Integer.toString(rect.height)); //$NON-NLS-1$
                });
                end();
            }
            else {
                begin(2);
            }
        }
    }
}
