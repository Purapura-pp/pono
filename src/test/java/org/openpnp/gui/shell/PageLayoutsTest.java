package org.openpnp.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.prefs.Preferences;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpnp.gui.shell.PageLayouts.Camera;
import org.openpnp.gui.shell.PageLayouts.Inspector;

/**
 * Each page's layout: the camera it starts with, what a drag of the divider makes of it, and the
 * properties column's width and whether it shows.
 */
public class PageLayoutsTest {
    private Preferences prefs;
    private PageLayouts layouts;

    @BeforeEach
    public void setUp() throws Exception {
        prefs = Preferences.userRoot().node("pono-test-page-layouts-" + System.nanoTime());
        layouts = new PageLayouts(prefs);
    }

    @AfterEach
    public void tearDown() throws Exception {
        prefs.removeNode();
    }

    @Test
    public void thePagesThatWorkTheMachineStartWithTheCameraLarge() {
        assertEquals(Camera.Large, layouts.camera("Job"));
        assertEquals(Camera.Large, layouts.camera("Feeders"));
        assertEquals(Camera.Small, layouts.camera("Parts"));
        assertEquals(Camera.Small, layouts.camera("Log"));
    }

    @Test
    public void aLargeCameraLeavesTheDockItsRoomByDefaultAndItsOwnPositionAfterADrag() {
        assertEquals(900 - PageLayouts.DOCK, layouts.dividerFor("Job", Camera.Large, 900));
        assertEquals(Camera.Large, layouts.dragged("Job", 480));
        assertEquals(480, layouts.dividerFor("Job", Camera.Large, 900));
        assertEquals(900 - PageLayouts.DOCK, layouts.dividerFor("Feeders", Camera.Large, 900),
                "one page's divider is not another's");
    }

    @Test
    public void draggingTheDividerUpMakesTheCameraAStripAndThenHidesIt() {
        assertEquals(Camera.Small, layouts.dragged("Job", 180));
        assertEquals(Camera.Small, layouts.camera("Job"));
        assertEquals(PageLayouts.STRIP, layouts.dividerFor("Job", layouts.camera("Job"), 900));
        assertEquals(Camera.Hidden, layouts.dragged("Job", 10));
        assertEquals(0, layouts.dividerFor("Job", layouts.camera("Job"), 900));
    }

    @Test
    public void aLargeCameraNeverLeavesThePageBelowItNothing() {
        layouts.dragged("Job", 880);
        assertTrue(layouts.dividerFor("Job", Camera.Large, 900) <= 900 - 120);
    }

    @Test
    public void thePropertiesColumnIsBetween320AndThirtyPercentOfTheWindow() {
        assertEquals(500, PageLayouts.inspectorWidth(500, 2000));
        assertEquals(480, PageLayouts.inspectorWidth(500, 1600));
        assertEquals(320, PageLayouts.inspectorWidth(500, 1024));
        assertEquals(320, PageLayouts.inspectorWidth(200, 1600));
    }

    @Test
    public void thePropertiesColumnStaysOnThePagesThatUseItUnlessTold() {
        assertTrue(layouts.inspectorShown("Parts", true));
        assertTrue(layouts.inspectorShown("Parts", false), "it does not come and go with the selection");
        assertFalse(layouts.inspectorShown("Log", false), "the log never puts anything in it");
        assertTrue(layouts.inspectorShown("Log", true));
        layouts.setInspector("Parts", Inspector.Hide);
        assertFalse(layouts.inspectorShown("Parts", true));
        layouts.setInspector("Log", Inspector.Show);
        assertTrue(layouts.inspectorShown("Log", false));
    }
}
