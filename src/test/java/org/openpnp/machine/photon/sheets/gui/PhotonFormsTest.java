package org.openpnp.machine.photon.sheets.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.machine.photon.PhotonFeeder;
import org.openpnp.machine.photon.PhotonProperties;
import org.openpnp.model.Configuration;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Machine;

/**
 * The Photon feeders' forms, P9 W6: the slot's location and the highest address are written on
 * Apply, and neither is written when it is not one.
 */
public class PhotonFormsTest {
    private static final WizardContainer CONTAINER = new WizardContainer() {
        @Override
        public void wizardCompleted(Wizard wizard) {
        }

        @Override
        public void wizardCancelled(Wizard wizard) {
        }
    };

    @TempDir
    Path tempDir;

    private Machine machine;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
        machine = Configuration.get().getMachine();
    }

    private static FormWizard contained(FormWizard form) {
        form.setWizardContainer(CONTAINER);
        return form;
    }

    @Test
    public void theFeederWritesItsSlotOnApplyAndLeavesAnUnsetOneUnset() throws Exception {
        PhotonFeeder feeder = new PhotonFeeder();
        machine.addFeeder(feeder);
        feeder.setHardwareId("00112233445566778899aabb");
        feeder.setSlotAddress(5);
        assertNull(feeder.getSlot().getLocation());

        FormWizard form = contained(PhotonForms.feeder(feeder));
        form.setValue("partPitch", "8");
        form.apply();
        assertEquals(8, feeder.getPartPitch());
        assertNull(feeder.getSlot().getLocation(), "Apply does not put an unset slot at the origin");

        form.setLocation("slotLocation", new Location(LengthUnit.Millimeters, 100, 50, -10, 90));
        form.setLocation("offset", new Location(LengthUnit.Millimeters, 2, 0, 0, 0));
        assertNull(feeder.getSlot().getLocation(), "nothing is written before Apply");
        form.apply();
        assertEquals(100, feeder.getSlot().getLocation().convertToUnits(LengthUnit.Millimeters).getX(), 1e-9);
        assertEquals(2, feeder.getOffset().convertToUnits(LengthUnit.Millimeters).getX(), 1e-9);
    }

    @Test
    public void theHighestAddressIsWrittenOnApplyWhenItIsOne() {
        PhotonProperties properties = new PhotonProperties(machine);
        int before = properties.getMaxFeederAddress();
        FormWizard form = contained(PhotonForms.global(machine));
        form.setValue("maxFeederAddress", "300");
        form.apply();
        assertEquals(before, properties.getMaxFeederAddress(), "there is no address past 254");
        form.setValue("maxFeederAddress", "120");
        form.apply();
        assertEquals(120, properties.getMaxFeederAddress());
    }
}
