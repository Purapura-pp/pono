package org.openpnp.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.awt.Rectangle;

import org.junit.jupiter.api.Test;
import org.openpnp.gui.components.CameraArrangement;

/**
 * The camera card at the sizes the window gives it: nothing is laid over the pictures, the tool
 * row narrows step by step, the manual controls choose a size by the height, and the room the
 * pictures leave goes to the page below or to the machine panel.
 * <p>
 * The sizes are those of a maximised window with the properties column shown: 1280 x 720 gives a
 * card of 798 x 242, 1366 x 768 858 x 290, 1600 x 900 1022 x 422, 1920 x 1080 1322 x 602.
 */
public class CameraWorkspaceLayoutTest {
    private static CameraWorkspaceLayout.Input input(int width, int height, int cameras) {
        CameraWorkspaceLayout.Input in = new CameraWorkspaceLayout.Input();
        in.width = width;
        in.height = height;
        in.fit = true;
        in.selector = 214;
        in.droRow = 440;
        in.droBare = 352;
        in.droTight = 300;
        in.tools = 348;
        in.toolsCompact = 214;
        double[] aspects = new double[cameras];
        java.util.Arrays.fill(aspects, 4.0 / 3.0);
        in.pictures = (w, h) -> CameraArrangement.arrange(w, h, aspects).size;
        return in;
    }

    private static void apart(Rectangle a, Rectangle b, String what) {
        if (a != null && b != null) {
            assertFalse(a.intersects(b), what + ": " + a + " and " + b);
        }
    }

    /** Nothing lies over the pictures, and the pictures lie within the card. */
    private static void clear(CameraWorkspaceLayout.Result r, int width, int height) {
        apart(r.video, r.column, "pictures and manual controls");
        apart(r.video, r.info, "pictures and machine panel");
        apart(r.info, r.column, "machine panel and manual controls");
        assertTrue(r.video.y >= CameraWorkspaceLayout.BAR, "pictures under the tool row");
        assertTrue(r.video.x >= 0 && r.video.x + r.video.width <= width, "within the card: " + r.video);
        assertTrue(r.video.y + r.video.height <= height, "within the card: " + r.video);
    }

    @Test
    public void theToolRowNarrowsStepByStepAndFinallyPutsTheReadoutInTheControls() {
        CameraWorkspaceLayout.Result wide = CameraWorkspaceLayout.lay(input(1322, 602, 2));
        assertEquals(DroPanel.Form.Row, wide.droForm);
        assertFalse(wide.toolsCompact);
        CameraWorkspaceLayout.Result medium = CameraWorkspaceLayout.lay(input(1022, 422, 2));
        assertEquals(DroPanel.Form.RowBare, medium.droForm);
        assertFalse(medium.toolsCompact);
        CameraWorkspaceLayout.Result narrow = CameraWorkspaceLayout.lay(input(858, 290, 2));
        assertEquals(DroPanel.Form.RowBare, narrow.droForm);
        assertTrue(narrow.toolsCompact);
        CameraWorkspaceLayout.Result narrower = CameraWorkspaceLayout.lay(input(798, 242, 2));
        assertEquals(DroPanel.Form.RowTight, narrower.droForm);
        assertFalse(narrower.droInColumn);
        CameraWorkspaceLayout.Result narrowest = CameraWorkspaceLayout.lay(input(700, 400, 2));
        assertTrue(narrowest.droInColumn);
        assertNull(narrowest.dro);
        for (CameraWorkspaceLayout.Result r : new CameraWorkspaceLayout.Result[] { wide, medium, narrow, narrower }) {
            assertTrue(r.dro.x >= r.selector.x + r.selector.width, "the readout after the camera choice");
            assertTrue(r.dro.x + r.dro.width <= r.tools.x, "and before the tools");
        }
    }

    @Test
    public void theManualControlsChooseASizeByTheHeightAndFoldWhenTooShort() {
        int bar = CameraWorkspaceLayout.BAR + 1;
        assertEquals(JogCard.Level.Full, CameraWorkspaceLayout.lay(input(1322, bar + JogCard.needed(JogCard.Level.Full), 1)).level);
        assertEquals(JogCard.Level.Compact, CameraWorkspaceLayout.lay(input(1322, bar + JogCard.needed(JogCard.Level.Full) - 1, 1)).level);
        assertEquals(JogCard.Level.Min, CameraWorkspaceLayout.lay(input(1322, bar + JogCard.needed(JogCard.Level.Compact) - 1, 1)).level);
        CameraWorkspaceLayout.Result wide = CameraWorkspaceLayout.lay(input(798, 242, 2));
        assertEquals(JogCard.Level.Wide, wide.level, "the 1280 x 720 window");
        assertEquals(JogCard.WIDE_WIDTH, wide.column.width);
        CameraWorkspaceLayout.Result sliver = CameraWorkspaceLayout.lay(input(1322, bar + JogCard.needed(JogCard.Level.Wide) - 1, 1));
        assertTrue(sliver.autoFolded);
        assertEquals(JogCard.FOLDED_WIDTH, sliver.column.width);
    }

    @Test
    public void theReadoutInTheControlsCountsAgainstTheirHeight() {
        CameraWorkspaceLayout.Input in = input(700, CameraWorkspaceLayout.BAR + 1 + JogCard.needed(JogCard.Level.Full), 1);
        CameraWorkspaceLayout.Result r = CameraWorkspaceLayout.lay(in);
        assertTrue(r.droInColumn);
        assertEquals(JogCard.Level.Compact, r.level, "the readout's rows take the full size's room");
    }

    @Test
    public void picturesLimitedByTheWidthGiveTheRoomAboveAndBelowToThePage() {
        CameraWorkspaceLayout.Result r = CameraWorkspaceLayout.lay(input(1322, 602, 2));
        clear(r, 1322, 602);
        assertTrue(r.fittedHeight < 602, "the pictures side by side leave room: " + r.fittedHeight);
        assertTrue(r.fittedHeight >= CameraWorkspaceLayout.BAR + 1 + JogCard.needed(r.level),
                "but never less than the manual controls' size needs");
        CameraWorkspaceLayout.Input fixed = input(1322, 602, 2);
        fixed.fit = false;
        assertEquals(602, CameraWorkspaceLayout.lay(fixed).fittedHeight, "full screen keeps its height");
    }

    /**
     * Worked out from the card's own height, the fitted height of a card that had been made lower
     * was lower again: resizing the window shrank the camera to a sliver, and it never came back.
     */
    @Test
    public void theFittedHeightComesFromTheWantedHeightAndFitsAgainAtItself() {
        CameraWorkspaceLayout.Result wanted = CameraWorkspaceLayout.lay(input(1322, 602, 2));
        assertTrue(wanted.fittedHeight < 602);
        for (int height : new int[] { 120, 200, 300, wanted.fittedHeight, 602 }) {
            CameraWorkspaceLayout.Input in = input(1322, height, 2);
            in.wanted = 602;
            assertEquals(wanted.fittedHeight, CameraWorkspaceLayout.lay(in).fittedHeight, "laid out " + height + " high");
        }
        CameraWorkspaceLayout.Input fitted = input(1322, wanted.fittedHeight, 2);
        fitted.wanted = 602;
        CameraWorkspaceLayout.Result r = CameraWorkspaceLayout.lay(fitted);
        assertEquals(wanted.video.getSize(), r.video.getSize(), "the same pictures at the fitted height");
        assertEquals(wanted.level, r.level, "and the same manual controls");
    }

    @Test
    public void theWideControlsFoldRatherThanLeaveThePicturesASliver() {
        CameraWorkspaceLayout.Result r = CameraWorkspaceLayout.lay(input(592, 262, 1));
        assertTrue(r.autoFolded);
        assertTrue(r.video.width > 592 - JogCard.WIDE_WIDTH - 1 - 2 * CameraWorkspaceLayout.MARGIN,
                "wider than the wide controls would have left: " + r.video.width);
    }

    /** Narrow and short at once: the readout had gone into manual controls that were folded away. */
    @Test
    public void aReadoutWithNoRoomInTheRowStaysInSightWhenTheControlsFold() {
        CameraWorkspaceLayout.Input in = input(592, 262, 1);
        in.droGridWidth = 178;
        in.droGridHeight = 52;
        CameraWorkspaceLayout.Result r = CameraWorkspaceLayout.lay(in);
        assertTrue(r.autoFolded);
        assertFalse(r.droInColumn);
        assertNotNull(r.dro, "beside the pictures");
        assertEquals(DroPanel.Form.Grid, r.droForm);
        apart(r.dro, r.video, "readout and pictures");
        apart(r.dro, r.column, "readout and the folded controls");
        clear(r, 592, 262);
        CameraWorkspaceLayout.Input folded = input(700, 600, 1);
        folded.droGridWidth = 178;
        folded.droGridHeight = 52;
        folded.folded = true;
        CameraWorkspaceLayout.Result byHand = CameraWorkspaceLayout.lay(folded);
        assertNotNull(byHand.dro, "folded by hand too");
        apart(byHand.dro, byHand.video, "readout and pictures");
    }

    @Test
    public void theMachinePanelShowsOnlyWhereItFits() {
        CameraWorkspaceLayout.Input in = input(1322, 602, 1);
        in.infoHeight = 200;
        CameraWorkspaceLayout.Result r = CameraWorkspaceLayout.lay(in);
        assertNotNull(r.info);
        assertTrue(r.info.height >= 200);
        in.infoHeight = 700;
        assertNull(CameraWorkspaceLayout.lay(in).info, "taller than the pictures' band");
    }

    @Test
    public void theStripsSidesAreAsWideAsWhatTheyHold() {
        CameraWorkspaceLayout.Input in = input(592, 150, 2);
        in.mode = CameraWorkspaceLayout.Mode.Strip;
        in.stripLeftWidth = 215;
        in.stripRightWidth = 119;
        CameraWorkspaceLayout.Result r = CameraWorkspaceLayout.lay(in);
        assertEquals(215 + 2 * CameraWorkspaceLayout.BAR_PAD, r.stripLeft.width);
        assertEquals(119 + 2 * CameraWorkspaceLayout.BAR_PAD, r.stripRight.width);
        assertTrue(r.video.width > 100, "the pictures keep some width in a narrow window: " + r.video);
        apart(r.video, r.stripLeft, "pictures and the strip's left");
        apart(r.video, r.stripRight, "pictures and the strip's right");
    }

    @Test
    public void picturesLimitedByTheHeightLeaveRoomBesideThemForTheMachinePanel() {
        CameraWorkspaceLayout.Result r = CameraWorkspaceLayout.lay(input(1322, 602, 1));
        clear(r, 1322, 602);
        assertNotNull(r.info, "one picture in a wide card leaves room beside it");
        assertTrue(r.info.width <= MachineInfoPanel.MAX_WIDTH);
        assertEquals(602, r.fittedHeight, "no room above or below to give");
        CameraWorkspaceLayout.Input none = input(1322, 602, 1);
        none.info = false;
        assertNull(CameraWorkspaceLayout.lay(none).info);
        assertNull(CameraWorkspaceLayout.lay(input(858, 290, 2)).info, "too little room");
    }

    @Test
    public void nothingLiesOverThePicturesAtAnyWindowSize() {
        int[][] cards = { { 798, 242 }, { 858, 290 }, { 1022, 422 }, { 1322, 602 }, { 1962, 962 }, { 1192, 242 } };
        for (int[] card : cards) {
            for (int cameras = 1; cameras <= 2; cameras++) {
                clear(CameraWorkspaceLayout.lay(input(card[0], card[1], cameras)), card[0], card[1]);
            }
        }
    }

    @Test
    public void theStripHasItsSidesAndThePicturesBetween() {
        CameraWorkspaceLayout.Input in = input(1022, 150, 2);
        in.mode = CameraWorkspaceLayout.Mode.Strip;
        CameraWorkspaceLayout.Result r = CameraWorkspaceLayout.lay(in);
        assertEquals(DroPanel.Form.Grid, r.droForm);
        apart(r.video, r.stripLeft, "pictures and the strip's left");
        apart(r.video, r.stripRight, "pictures and the strip's right");
        assertEquals(150, r.fittedHeight);
    }

    @Test
    public void aBannerIsARowOfItsOwnAboveThePictures() {
        CameraWorkspaceLayout.Input in = input(1322, 602, 2);
        in.banner = 60;
        CameraWorkspaceLayout.Result r = CameraWorkspaceLayout.lay(in);
        apart(r.banner, r.video, "banner and pictures");
        assertTrue(r.banner.y >= CameraWorkspaceLayout.BAR);
    }

    @Test
    public void theHiddenCameraIsThePicturesAlone() {
        CameraWorkspaceLayout.Input in = input(1322, 40, 1);
        in.mode = CameraWorkspaceLayout.Mode.Hidden;
        in.pictures = (w, h) -> new Dimension(Math.max(0, w), Math.max(0, h));
        CameraWorkspaceLayout.Result r = CameraWorkspaceLayout.lay(in);
        assertNull(r.column);
        assertNull(r.selector);
        assertNotNull(r.video);
    }
}
