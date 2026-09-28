package org.openpnp.gui.tablemodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.feeder.ReferenceTubeFeeder;
import org.openpnp.model.AbstractVisionSettings;
import org.openpnp.model.Configuration;

/**
 * A cell's editor starts from what the cell shows - a built-in name in the display language, a
 * dash for no slot - and committed unchanged, that is not written as the value.
 */
public class CommittedAsShownTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
    }

    @AfterEach
    public void tearDown() throws Exception {
        ((ReferenceMachine) Configuration.get().getMachine()).close();
    }

    @Test
    public void aBuiltInVisionNameCommittedAsShownKeepsTheNameItIsStoredUnder() {
        VisionSettingsTableModel model = new VisionSettingsTableModel(Configuration.get());
        int row = -1;
        for (int i = 0; i < model.getRowCount(); i++) {
            if (AbstractVisionSettings.DEFAULT_BOTTOM_ID.equals(model.getRowObjectAt(i).getId())) {
                row = i;
            }
        }
        assertTrue(row >= 0, "the machine's default bottom vision is listed");
        String stored = model.getRowObjectAt(row).getName();
        Object shown = model.getValueAt(row, VisionSettingsTableModel.NAME);
        assertNotEquals(stored, shown, "the name is shown translated");

        model.setValueAt(shown, row, VisionSettingsTableModel.NAME);
        assertEquals(stored, model.getRowObjectAt(row).getName());

        model.setValueAt("Mine", row, VisionSettingsTableModel.NAME);
        assertEquals("Mine", model.getRowObjectAt(row).getName(), "a name typed is written");
    }

    @Test
    public void theDashCommittedForNoSlotLeavesTheFeederWithoutOne() throws Exception {
        ReferenceTubeFeeder feeder = new ReferenceTubeFeeder();
        Configuration.get().getMachine().addFeeder(feeder);
        FeedersTableModel model = new FeedersTableModel(Configuration.get());
        model.refresh();
        int row = model.indexOf(feeder);

        model.setValueAt(model.getValueAt(row, FeedersTableModel.SLOT), row, FeedersTableModel.SLOT);
        assertNull(feeder.getSlotName());

        model.setValueAt("A3", row, FeedersTableModel.SLOT);
        assertEquals("A3", feeder.getSlotName());
    }
}
