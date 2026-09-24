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

package org.openpnp.machine.photon.sheets.gui;

import java.util.function.Predicate;

import javax.swing.SwingUtilities;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.machine.photon.PhotonFeeder;
import org.openpnp.machine.photon.PhotonFeederSlots;
import org.openpnp.machine.photon.PhotonProperties;
import org.openpnp.machine.reference.feeder.wizards.FeederForm;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Part;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Machine;
import org.openpnp.util.MovableUtils;
import org.openpnp.util.UiUtils;

/**
 * A Photon feeder: what the bus says of it, its part, and where it picks - its slot's location,
 * which the slot keeps for whichever feeder is put in it, and the feeder's own offset from there.
 * And what all of them share: the search for them on the bus and the programming of the slots.
 */
public final class PhotonForms {
    private PhotonForms() {
    }

    private static final int MAX_ADDRESS = 254;

    /** The feeder's form, flat: the slot's location is written to the slot on Apply. */
    public static final class Bean extends AbstractModelObject {
        private final PhotonFeeder feeder;

        Bean(PhotonFeeder feeder) {
            this.feeder = feeder;
        }

        public String getHardwareId() { return feeder.getHardwareId(); }

        public String getSlotAddress() {
            Integer address = feeder.getSlotAddress();
            return address == null ? Translations.getString("PhotonForms.NoSlot") : address.toString(); //$NON-NLS-1$
        }

        public Part getPart() { return feeder.getPart(); }

        public void setPart(Part part) { feeder.setPart(part); }

        public int getFeedRetryCount() { return feeder.getFeedRetryCount(); }

        public void setFeedRetryCount(int count) { feeder.setFeedRetryCount(count); }

        public int getPickRetryCount() { return feeder.getPickRetryCount(); }

        public void setPickRetryCount(int count) { feeder.setPickRetryCount(count); }

        public int getPartPitch() { return feeder.getPartPitch(); }

        public void setPartPitch(int pitch) { feeder.setPartPitch(pitch); }

        public Location getSlotLocation() {
            PhotonFeederSlots.Slot slot = feeder.getSlot();
            return slot == null || slot.getLocation() == null ? new Location(LengthUnit.Millimeters) : slot.getLocation();
        }

        /** A slot without a location keeps none until one is given it: the origin is no pick location. */
        public void setSlotLocation(Location location) {
            PhotonFeederSlots.Slot slot = feeder.getSlot();
            if (slot == null) {
                return;
            }
            if (slot.getLocation() == null && !location.isInitialized()) {
                return;
            }
            slot.setLocation(location);
        }

        public Location getOffset() {
            return feeder.getOffset() == null ? new Location(LengthUnit.Millimeters) : feeder.getOffset();
        }

        public void setOffset(Location offset) { feeder.setOffset(offset); }

        public boolean isMoveWhileFeeding() { return feeder.getMoveWhileFeeding(); }

        public void setMoveWhileFeeding(boolean move) { feeder.setMoveWhileFeeding(move); }
    }

    public static FormWizard feeder(PhotonFeeder feeder) {
        FormWizard[] form = new FormWizard[1];
        Predicate<FormWizard> inSlot = f -> feeder.getSlot() != null;
        Form.Builder builder = Form.of(new Bean(feeder)).named(feeder.getName())
                .section("FeederConfigurationWizard.InfoPanel.Border.title", "info") //$NON-NLS-1$ //$NON-NLS-2$
                .readOnly("hardwareId", "PhotonForms.HardwareId") //$NON-NLS-1$ //$NON-NLS-2$
                .readOnly("slotAddress", "PhotonForms.SlotAddress") //$NON-NLS-1$ //$NON-NLS-2$
                .hint("PhotonForms.SlotAddress.Hint") //$NON-NLS-1$
                .action("FeederConfigurationWizard.FindSlotAddressAction.Name", "search", //$NON-NLS-1$ //$NON-NLS-2$
                        () -> find(form[0], feeder));
        form[0] = FeederForm.common(builder, feeder, false)
                .integer("partPitch", "FeederConfigurationWizard.PartPanel.partPitchLabel.text").unit("mm").width(100) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .action("FeederConfigurationWizard.FeedAction.Name", "play", () -> feed(form[0], feeder, false)).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .button("PhotonForms.FeedOneMm", "step", f -> feed(f, feeder, true)).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .visibleIf(inSlot)
                .section("FeederConfigurationWizard.LocationPanel.Border.title", "crosshair") //$NON-NLS-1$ //$NON-NLS-2$
                .location("slotLocation", "PhotonForms.SlotLocation", true).locationButtons() //$NON-NLS-1$ //$NON-NLS-2$
                .hint("PhotonForms.SlotLocation.Hint") //$NON-NLS-1$
                .visibleIf(inSlot)
                .location("offset", "FeederConfigurationWizard.LocationPanel.offsetLabel.text", true) //$NON-NLS-1$ //$NON-NLS-2$
                .iconButton("capture", "PhotonForms.Offset.Capture", PhotonForms::captureOffset) //$NON-NLS-1$ //$NON-NLS-2$
                .iconButton("crosshair", "PhotonForms.Offset.Move", PhotonForms::moveToPick).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .hint("PhotonForms.Offset.Hint") //$NON-NLS-1$
                .visibleIf(inSlot)
                .toggle("moveWhileFeeding", "PhotonForms.MoveWhileFeeding", //$NON-NLS-1$ //$NON-NLS-2$
                        "FeederConfigurationWizard.LocationPanel.moveWhileFeedingLabel.toolTipText") //$NON-NLS-1$
                .build();
        return form[0];
    }

    /** The slot the feeder is in, asked of it again, after Apply; the form then shows it. */
    private static void find(FormWizard form, PhotonFeeder feeder) {
        form.apply();
        UiUtils.submitUiMachineTask(() -> {
            feeder.findSlotAddress();
            SwingUtilities.invokeLater(form::reload);
        });
    }

    private static void feed(FormWizard form, PhotonFeeder feeder, boolean oneMm) {
        form.apply();
        UiUtils.submitUiMachineTask(() -> {
            if (oneMm) {
                feeder.feedOneMm();
            }
            else {
                feeder.feed(null);
            }
        });
    }

    private static Camera camera() throws Exception {
        return MainFrame.get().getMachineControls().getSelectedTool().getHead().getDefaultCamera();
    }

    /**
     * The camera's position as an offset from the slot, in the slot's direction; the offset's Z is
     * left as it is.
     */
    private static void captureOffset(FormWizard form) {
        UiUtils.messageBoxOnException(() -> {
            Location slot = form.location("slotLocation"); //$NON-NLS-1$
            Location offset = camera().getLocation().subtractWithRotation(slot).rotateXy(-slot.getRotation());
            Location z = form.location("offset").convertToUnits(offset.getUnits()); //$NON-NLS-1$
            form.setLocation("offset", offset.derive(null, null, z.getZ(), null)); //$NON-NLS-1$
        });
    }

    /** The camera over the pick location the form shows, as the feeder works it out. */
    private static void moveToPick(FormWizard form) {
        Location pick = form.location("offset").offsetWithRotationFrom(form.location("slotLocation")); //$NON-NLS-1$ //$NON-NLS-2$
        UiUtils.submitUiMachineTask(() -> MovableUtils.moveToLocationAtSafeZ(camera(), pick));
    }

    /** What all the Photon feeders share, on every one of them. */
    public static FormWizard global(Machine machine) {
        PhotonProperties properties = new PhotonProperties(machine);
        FeederSearchProgressBar progress = new FeederSearchProgressBar();
        FormWizard[] form = new FormWizard[1];
        SlotProgramming programming = new SlotProgramming(machine, properties, () -> form[0].reload());
        form[0] = Form.of(properties).named("PhotonForms.Global") //$NON-NLS-1$
                .section("GlobalConfigConfigurationWizard.searchPanel.Border.title", "search") //$NON-NLS-1$ //$NON-NLS-2$
                .integer("maxFeederAddress", "PhotonForms.MaxAddress").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .validate(PhotonForms::address, "PhotonForms.MaxAddress.Invalid") //$NON-NLS-1$
                .button("GlobalConfigConfigurationWizard.searchButton.text", "search", //$NON-NLS-1$ //$NON-NLS-2$
                        f -> search(f, machine, properties, progress))
                .hint("PhotonForms.Search.Hint") //$NON-NLS-1$
                .custom("PhotonForms.Search.Result", progress) //$NON-NLS-1$
                .hint("PhotonForms.Search.Legend") //$NON-NLS-1$
                .section("PhotonForms.Program", "grid") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("PhotonForms.Program.Address", programming) //$NON-NLS-1$
                .hint("PhotonForms.Program.Hint") //$NON-NLS-1$
                .onReload(f -> progress.setNumberOfElements(properties.getMaxFeederAddress()))
                .build();
        return form[0];
    }

    private static boolean address(Object text) {
        try {
            int address = Integer.parseInt(String.valueOf(text).trim());
            return address >= 1 && address <= MAX_ADDRESS;
        }
        catch (NumberFormatException e) {
            return false;
        }
    }

    /** Every address up to the highest, asked in turn after Apply; the bar shows each answer. */
    private static void search(FormWizard form, Machine machine, PhotonProperties properties,
            FeederSearchProgressBar progress) {
        form.apply();
        progress.clearAllState();
        progress.setNumberOfElements(properties.getMaxFeederAddress());
        UiUtils.submitUiMachineTask(() -> {
            PhotonFeeder.findAllFeeders(machine,
                    (address, state) -> SwingUtilities.invokeLater(() -> progress.updateFeederState(address, state)));
            return null;
        }, done -> {
        }, error -> MessageBoxes.errorBox(MainFrame.get(),
                Translations.getString("PhotonForms.Search.Failed"), error)); //$NON-NLS-1$
    }
}
