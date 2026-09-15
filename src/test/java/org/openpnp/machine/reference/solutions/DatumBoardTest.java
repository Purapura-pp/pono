package org.openpnp.machine.reference.solutions;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The built-in datum board against its KiCad source, and the eight ways it can lie. */
public class DatumBoardTest {
    @Test
    public void theLumenPnpBoardHasItsSevenCopperFiducialsWhereKiCadPutsThem() {
        DatumBoard board = DatumBoard.lumenPnp();

        assertEquals(7, board.getFiducials().size());
        assertEquals("FID1", board.getAnchor().name);
        assertEquals(1.0, board.getAnchor().diameterMm);
        // FID6 at KiCad (5, -5) against FID1 at (40, -25): 35 left, 20 down the sheet.
        DatumBoard.Dot fid6 = board.getFiducials().get(5);
        assertEquals(-35, fid6.x);
        assertEquals(-20, fid6.y);
        for (DatumBoard.Dot dot : board.getFiducials()) {
            assertEquals(DatumBoard.Layer.Copper, dot.layer, dot.name + " is copper");
        }
        // The four around the anchor are what the orientation is read from; the far two are not.
        assertEquals(4, board.getOrientationFiducials().size());
        assertEquals(31, board.getRuler().ticks);
        assertEquals(DatumBoard.Layer.Copper, board.getRuler().layer);
        assertArrayEquals(new double[] { 0, -13.5 }, board.getRuler().centre());
        // The discs are mask and silk, and are marked so.
        assertEquals(DatumBoard.Layer.Mask, board.getDiscs().get(0).layer);
        assertEquals(DatumBoard.Layer.Silk, board.getDiscs().get(1).layer);
    }

    @Test
    public void theEightOrientationsAreDistinctAndCoverTheRotations() {
        double[] p = { 10, 5 };
        assertArrayEquals(new double[] { 10, 5 }, DatumBoard.orient(p[0], p[1], 0, false), 1e-12);
        assertArrayEquals(new double[] { -5, 10 }, DatumBoard.orient(p[0], p[1], 1, false), 1e-12);
        assertArrayEquals(new double[] { -10, -5 }, DatumBoard.orient(p[0], p[1], 2, false), 1e-12);
        assertArrayEquals(new double[] { 5, -10 }, DatumBoard.orient(p[0], p[1], 3, false), 1e-12);
        assertArrayEquals(new double[] { 10, -5 }, DatumBoard.orient(p[0], p[1], 0, true), 1e-12);
        // Four quarter turns come back to the start.
        assertArrayEquals(new double[] { 10, 5 }, DatumBoard.orient(p[0], p[1], 4, false), 1e-12);
        // A quarter turn keeps lengths.
        double[] turned = DatumBoard.orient(3, 4, 1, true);
        assertEquals(5, Math.hypot(turned[0], turned[1]), 1e-12);
        assertTrue(DatumBoard.orient(1, 0, 1, false)[1] > 0, "a quarter turn is anticlockwise");
    }
}
