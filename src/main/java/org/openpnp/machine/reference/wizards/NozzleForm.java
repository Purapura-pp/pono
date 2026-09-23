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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import org.openpnp.Translations;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.machine.reference.ReferenceNozzle;
import org.openpnp.model.CalibrationStep;
import org.openpnp.model.Length;
import org.openpnp.model.Location;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Head;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.NozzleTip;
import org.openpnp.spi.Nozzle.RotationMode;
import org.openpnp.util.MovableUtils;
import org.openpnp.util.UiUtils;

/**
 * A nozzle's sheets: its settings, the nozzle tips it takes, its vacuum, how its tips are
 * changed, and the measurement of its offset by hand.
 */
public final class NozzleForm {
    private NozzleForm() {
    }

    public static class Bean extends MountableAxes.Bean {
        private final ReferenceNozzle nozzle;

        Bean(ReferenceNozzle nozzle) {
            super(nozzle);
            this.nozzle = nozzle;
        }

        public String getName() {
            return nozzle.getName();
        }

        public void setName(String name) {
            nozzle.setName(name);
        }

        public Location getHeadOffsets() {
            return nozzle.getHeadOffsets();
        }

        public void setHeadOffsets(Location offsets) {
            nozzle.setHeadOffsets(offsets);
        }

        public RotationMode getRotationMode() {
            return nozzle.getRotationMode();
        }

        public void setRotationMode(RotationMode mode) {
            nozzle.setRotationMode(mode);
        }

        public boolean isAligningRotationMode() {
            return nozzle.isAligningRotationMode();
        }

        public void setAligningRotationMode(boolean aligning) {
            nozzle.setAligningRotationMode(aligning);
        }

        /** Set on the Z axis, shown here: the calibration page's Safe Z step captures it. */
        public Length getSafeZ() {
            return nozzle.getSafeZ();
        }

        public boolean isEnableDynamicSafeZ() {
            return nozzle.isEnableDynamicSafeZ();
        }

        public void setEnableDynamicSafeZ(boolean dynamic) {
            nozzle.setEnableDynamicSafeZ(dynamic);
        }

        public int getPickDwellMilliseconds() {
            return nozzle.getPickDwellMilliseconds();
        }

        public void setPickDwellMilliseconds(int milliseconds) {
            nozzle.setPickDwellMilliseconds(milliseconds);
        }

        public int getPlaceDwellMilliseconds() {
            return nozzle.getPlaceDwellMilliseconds();
        }

        public void setPlaceDwellMilliseconds(int milliseconds) {
            nozzle.setPlaceDwellMilliseconds(milliseconds);
        }

        /** The nozzle tips it takes: Apply adds the ones switched on and removes the others. */
        public Set<NozzleTip> getCompatibleNozzleTips() {
            return new LinkedHashSet<>(nozzle.getCompatibleNozzleTips());
        }

        public void setCompatibleNozzleTips(Set<NozzleTip> tips) {
            for (NozzleTip tip : new ArrayList<>(nozzle.getCompatibleNozzleTips())) {
                if (!tips.contains(tip)) {
                    nozzle.removeCompatibleNozzleTip(tip);
                }
            }
            for (NozzleTip tip : tips) {
                if (!nozzle.getCompatibleNozzleTips().contains(tip)) {
                    nozzle.addCompatibleNozzleTip(tip);
                }
            }
        }

        public NozzleTip getLoadedNozzleTip() {
            return nozzle.getNozzleTip();
        }

        public Actuator getVacuumActuator() {
            return nozzle.getVacuumActuator();
        }

        public void setVacuumActuator(Actuator actuator) {
            nozzle.setVacuumActuator(actuator);
        }

        public Actuator getBlowOffActuator() {
            return nozzle.getBlowOffActuator();
        }

        public void setBlowOffActuator(Actuator actuator) {
            nozzle.setBlowOffActuator(actuator);
        }

        public boolean isBlowOffClosingValve() {
            return nozzle.isBlowOffClosingValve();
        }

        public void setBlowOffClosingValve(boolean closing) {
            nozzle.setBlowOffClosingValve(closing);
        }

        public Actuator getVacuumSenseActuator() {
            return nozzle.getVacuumSenseActuator();
        }

        public void setVacuumSenseActuator(Actuator actuator) {
            nozzle.setVacuumSenseActuator(actuator);
        }

        public boolean isChangerEnabled() {
            return nozzle.isChangerEnabled();
        }

        public void setChangerEnabled(boolean enabled) {
            nozzle.setChangerEnabled(enabled);
        }

        public boolean isNozzleTipChangedOnManualFeed() {
            return nozzle.isNozzleTipChangedOnManualFeed();
        }

        public void setNozzleTipChangedOnManualFeed(boolean changed) {
            nozzle.setNozzleTipChangedOnManualFeed(changed);
        }

        public Location getManualNozzleTipChangeLocation() {
            return nozzle.getManualNozzleTipChangeLocation();
        }

        public void setManualNozzleTipChangeLocation(Location location) {
            nozzle.setManualNozzleTipChangeLocation(location);
        }
    }

    /** What the measurement of the offset by hand keeps between its steps; nothing of it is saved. */
    public static class OffsetBean extends Bean {
        private Camera camera;
        private boolean includeZ;
        private Location markLocation;

        OffsetBean(ReferenceNozzle nozzle) {
            super(nozzle);
            List<Camera> cameras = nozzle.getHead().getCameras();
            camera = cameras.isEmpty() ? null : cameras.get(0);
            markLocation = new Location(nozzle.getHeadOffsets().getUnits());
        }

        public Camera getCamera() {
            return camera;
        }

        public void setCamera(Camera camera) {
            this.camera = camera;
        }

        public boolean isIncludeZ() {
            return includeZ;
        }

        public void setIncludeZ(boolean includeZ) {
            this.includeZ = includeZ;
        }

        public Location getMarkLocation() {
            return markLocation;
        }

        public void setMarkLocation(Location location) {
            markLocation = location;
        }
    }

    /** The nozzle's own settings: name, axes, offset, rotation, Safe Z and dwell. */
    public static FormWizard settings(ReferenceNozzle nozzle) {
        CalibrationStep offsetStep = offsetStep(nozzle);
        Form.Builder form = Form.of(new Bean(nozzle)).named("NozzleForm.Title") //$NON-NLS-1$
                .section("NozzleForm.Basics", "nozzle") //$NON-NLS-1$ //$NON-NLS-2$
                .text("name", "ReferenceNozzleConfigurationWizard.PropertiesPanel.NameLabel.text"); //$NON-NLS-1$ //$NON-NLS-2$
        MountableAxes.section(form, nozzle.getMachine());
        return form
                .section("NozzleForm.Offsets", "target").measuredBy(offsetStep, nozzle) //$NON-NLS-1$ //$NON-NLS-2$
                .location("headOffsets", "ReferenceNozzleConfigurationWizard.OffsetsPanel.OffsetLabel.text", true) //$NON-NLS-1$ //$NON-NLS-2$
                .note("NozzleForm.Offsets.Note") //$NON-NLS-1$
                .section("NozzleForm.Rotation", "rcw") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("rotationMode", "ReferenceNozzleConfigurationWizard.OffsetsPanel.RotationModeLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        RotationMode.class)
                .note("NozzleForm.RotationMode.Note") //$NON-NLS-1$
                .toggle("aligningRotationMode", "NozzleForm.AlignWithPart", "NozzleForm.AlignWithPart.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("NozzleForm.AlignWithPart.Hint") //$NON-NLS-1$
                .section("ReferenceNozzleConfigurationWizard.SafeZPanel.Border.title", "up") //$NON-NLS-1$ //$NON-NLS-2$
                .measuredBy(CalibrationStep.SafeZ, nozzle)
                .readOnly("safeZ", "ReferenceNozzleConfigurationWizard.SafeZPanel.SafeZLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .note("NozzleForm.SafeZ.Note") //$NON-NLS-1$
                .toggle("enableDynamicSafeZ", "ReferenceNozzleConfigurationWizard.SafeZPanel.DynamicSafeZLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        "NozzleForm.DynamicSafeZ.Note") //$NON-NLS-1$
                .hint("NozzleForm.DynamicSafeZ.Hint") //$NON-NLS-1$
                .section("NozzleForm.Dwell", "clock").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .integer("pickDwellMilliseconds", "NozzleForm.PickDwell").unit("ms").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .integer("placeDwellMilliseconds", "NozzleForm.PlaceDwell").unit("ms").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .note("NozzleForm.Dwell.Note") //$NON-NLS-1$
                .build();
    }

    /** The nozzle tips it takes, and the one on it now with the buttons that change it. */
    public static FormWizard nozzleTips(ReferenceNozzle nozzle) {
        FormWizard[] form = new FormWizard[1];
        JButton load = Ui.button(Translations.getString("NozzleForm.Load"), Ui.iconSm("download"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Default);
        Ui.movesMachine(load);
        load.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();
            for (NozzleTip tip : nozzle.getCompatibleNozzleTips()) {
                JMenuItem item = new JMenuItem(tip.getName());
                item.setEnabled(tip != nozzle.getNozzleTip());
                item.addActionListener(a -> change(form[0], nozzle, tip));
                menu.add(item);
            }
            if (menu.getComponentCount() == 0) {
                JMenuItem none = new JMenuItem(Translations.getString("NozzleForm.Load.NoneCompatible")); //$NON-NLS-1$
                none.setEnabled(false);
                menu.add(none);
            }
            menu.show(load, 0, load.getHeight());
        });
        JButton unload = Ui.button(Translations.getString("NozzleForm.Unload"), Ui.iconSm("upload"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Default);
        Ui.movesMachine(unload);
        unload.addActionListener(e -> change(form[0], nozzle, null));
        JPanel buttons = Forms.row(load, unload);
        buttons.add(javax.swing.Box.createHorizontalGlue());
        form[0] = Form.of(new Bean(nozzle)).named("ReferenceNozzle.PropertySheetHolder.NozzleTips.title") //$NON-NLS-1$
                .section("NozzleForm.Compatible", "nozzle") //$NON-NLS-1$ //$NON-NLS-2$
                .checklist("compatibleNozzleTips", "NozzleForm.Compatible.Label", //$NON-NLS-1$ //$NON-NLS-2$
                        new ArrayList<NozzleTip>(nozzle.getMachine().getNozzleTips()), null)
                .note("NozzleForm.Compatible.Note") //$NON-NLS-1$
                .section("NozzleForm.Loaded", "download") //$NON-NLS-1$ //$NON-NLS-2$
                .readOnly("loadedNozzleTip", "NozzleForm.Loaded.Label") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", buttons) //$NON-NLS-1$
                .build();
        return form[0];
    }

    /**
     * Puts the tip on the nozzle, taking it off any other nozzle first, or takes the nozzle's tip
     * off when there is none: the tip changer's moves, or its instructions for changing by hand.
     */
    private static void change(FormWizard form, ReferenceNozzle nozzle, NozzleTip tip) {
        UiUtils.submitUiMachineTask(() -> {
            if (tip == null) {
                nozzle.unloadNozzleTip();
            }
            else {
                for (Head head : nozzle.getHead().getMachine().getHeads()) {
                    for (Nozzle other : head.getNozzles()) {
                        if (other.getNozzleTip() == tip) {
                            other.unloadNozzleTip();
                        }
                    }
                }
                nozzle.loadNozzleTip(tip);
            }
            MovableUtils.fireTargetedUserAction(nozzle);
            SwingUtilities.invokeLater(form::reload);
        });
    }

    /** The actuators that make, blow off and sense its vacuum. */
    public static FormWizard vacuum(ReferenceNozzle nozzle) {
        List<Actuator> actuators = new ArrayList<>();
        actuators.add(null);
        actuators.addAll(nozzle.getHead().getActuators());
        return Form.of(new Bean(nozzle)).named("ReferenceNozzle.PropertySheetHolder.Vacuum.title") //$NON-NLS-1$
                .section("NozzleForm.Vacuum", "circle") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("vacuumActuator", "ReferenceNozzleVacuumWizard.ContentPanel.VacuumActuatorLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        actuators, null)
                .choice("vacuumSenseActuator", "ReferenceNozzleVacuumWizard.ContentPanel.SensingActuatorLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        actuators, null)
                .note("NozzleForm.VacuumSense.Note") //$NON-NLS-1$
                .section("NozzleForm.BlowOff", "circle") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("blowOffActuator", "ReferenceNozzleVacuumWizard.ContentPanel.BlowOffActuatorLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        actuators, null)
                .toggle("blowOffClosingValve", "NozzleForm.ClosesVacuum", "NozzleForm.ClosesVacuum.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen("blowOffActuator", v -> v != null) //$NON-NLS-1$
                .build();
    }

    /** Whether its tips are changed by the tip changer or by hand, and where by hand. */
    public static FormWizard changer(ReferenceNozzle nozzle) {
        return Form.of(new Bean(nozzle)).named("ReferenceNozzle.PropertySheetHolder.ToolChanger.title") //$NON-NLS-1$
                .section("NozzleForm.Changer", "refresh") //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("changerEnabled", "NozzleForm.Automatic", "NozzleForm.Automatic.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("NozzleForm.Automatic.Hint") //$NON-NLS-1$
                .toggle("nozzleTipChangedOnManualFeed", "NozzleForm.OnManualPick", "NozzleForm.OnManualPick.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("NozzleForm.OnManualPick.Hint") //$NON-NLS-1$
                .section("NozzleForm.ByHand", "hand").measuredBy(CalibrationStep.ManualNozzleTipChange, nozzle) //$NON-NLS-1$ //$NON-NLS-2$
                .location("manualNozzleTipChangeLocation", //$NON-NLS-1$
                        "ReferenceNozzleToolChangerWizard.ChangerPanel.ManualChangeLocationLabel.text", false) //$NON-NLS-1$
                .withZ().locationButtons(nozzle)
                .visibleWhen("changerEnabled", v -> Boolean.FALSE.equals(v)) //$NON-NLS-1$
                .note("NozzleForm.ByHand.Note") //$NON-NLS-1$
                .build();
    }

    /**
     * The offset measured by hand: a mark the nozzle leaves, then the camera over it. The
     * calibration page measures it by itself; this is for a machine it cannot.
     */
    public static FormWizard offset(ReferenceNozzle nozzle) {
        Head head = nozzle.getHead();
        CalibrationStep offsetStep = offsetStep(nozzle);
        FormWizard[] form = new FormWizard[1];
        form[0] = Form.of(new OffsetBean(nozzle)).named("ReferenceNozzle.PropertySheetHolder.OffsetWizard.title") //$NON-NLS-1$
                .section("NozzleForm.Measure", "hand").measuredBy(offsetStep, nozzle) //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", Forms.paragraph(Translations.getString("NozzleForm.Measure.Steps"))) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("camera", "NozzleForm.Measure.Camera", new ArrayList<Camera>(head.getCameras()), null) //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("includeZ", "NozzleForm.Measure.IncludeZ", "NozzleForm.Measure.IncludeZ.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .location("markLocation", "NozzleForm.Measure.Mark", false).withZ() //$NON-NLS-1$ //$NON-NLS-2$
                .button("NozzleForm.Measure.Store", "nozzle", f -> //$NON-NLS-1$ //$NON-NLS-2$
                        f.setLocation("markLocation", nozzle.getLocation().subtract(nozzle.getHeadOffsets()))) //$NON-NLS-1$
                .action("NozzleForm.Measure.Calculate", "target", () -> calculate(form[0], nozzle)) //$NON-NLS-1$ //$NON-NLS-2$
                .section("NozzleForm.Measure.Result", "check") //$NON-NLS-1$ //$NON-NLS-2$
                .location("headOffsets", "ReferenceNozzleConfigurationWizard.OffsetsPanel.OffsetLabel.text", false) //$NON-NLS-1$ //$NON-NLS-2$
                .withZ()
                .note("NozzleForm.Measure.Result.Note") //$NON-NLS-1$
                .build();
        return form[0];
    }

    /** The calibration page's step that measures the offset: the head's first nozzle by touching. */
    private static CalibrationStep offsetStep(ReferenceNozzle nozzle) {
        try {
            Head head = nozzle.getHead();
            if (head != null && head.getDefaultNozzle() == nozzle) {
                return CalibrationStep.NozzleTouchPrimary;
            }
        }
        catch (Exception e) {
            // No nozzle on the head is the default one.
        }
        return CalibrationStep.OtherNozzleOffsets;
    }

    /** The camera over the mark: the offset is where it is, less where the mark is. */
    private static void calculate(FormWizard form, ReferenceNozzle nozzle) {
        Object camera = form.value("camera"); //$NON-NLS-1$
        if (!(camera instanceof Camera)) {
            return;
        }
        UiUtils.messageBoxOnException(() -> {
            Location mark = form.location("markLocation"); //$NON-NLS-1$
            Location offsets = ((Camera) camera).getLocation().subtract(mark);
            if (!Boolean.TRUE.equals(form.value("includeZ"))) { //$NON-NLS-1$
                offsets = offsets.derive(nozzle.getHeadOffsets(), false, false, true, false);
            }
            form.setLocation("headOffsets", offsets); //$NON-NLS-1$
            org.pmw.tinylog.Logger.info("Nozzle {} offset measured by hand: {}", nozzle.getName(), offsets); //$NON-NLS-1$
        });
    }
}
