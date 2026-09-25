package org.openpnp.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.model.Configuration;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;

/**
 * The readout keeps the room its longest values have needed: snug around 0.000 at the start,
 * wider once a longer number has been shown, and never narrower again while the machine moves.
 */
public class DroPanelTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
    }

    @Test
    public void theRowGrowsToALongerNumberAndDoesNotShrinkBack() {
        DroPanel dro = new DroPanel(Configuration.get());
        Location home = new Location(LengthUnit.Millimeters, 0, 0, 0, 0);
        dro.setLocation(home, false);
        int atHome = dro.getPreferredSize().width;

        dro.setLocation(new Location(LengthUnit.Millimeters, -120.45, 300.1, 31.5, -90), false);
        int far = dro.getPreferredSize().width;
        assertTrue(far > atHome, "room for the longer numbers");

        dro.setLocation(home, false);
        assertEquals(far, dro.getPreferredSize().width, "no jitter back to the narrow row");
    }

    @Test
    public void aBlankReadoutKeepsTheRoomOfItsValues() {
        DroPanel dro = new DroPanel(Configuration.get());
        dro.setLocation(new Location(LengthUnit.Millimeters, 120.45, 0, 0, 0), false);
        int shown = dro.getPreferredSize().width;
        dro.setLocation(null, false);
        assertEquals(shown, dro.getPreferredSize().width);
    }
}
