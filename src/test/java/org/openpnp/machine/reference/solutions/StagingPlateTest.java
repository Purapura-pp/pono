package org.openpnp.machine.reference.solutions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;

/** The staging plate's checkerboard, anchored on the primary fiducial. */
public class StagingPlateTest {
    private static final Location FID1 = new Location(LengthUnit.Millimeters, 217.287, 196.534, 5.7, 0);

    @Test
    public void theNearestHolesToTheFiducialAreFifteenMillimetresAwayAlongEachAxis() {
        // The fiducial sits on a node with no hole; the wanted position at the fiducial itself
        // steps to a neighbour that has one.
        Location alongX = StagingPlate.nearestHole(FID1, FID1.getX(), FID1.getY(), null, true);
        assertNotNull(alongX);
        assertEquals(15.0, Math.abs(alongX.getX() - FID1.getX()), 1e-9);
        assertEquals(FID1.getY(), alongX.getY(), 1e-9);
        Location alongY = StagingPlate.nearestHole(FID1, FID1.getX(), FID1.getY(), null, false);
        assertEquals(FID1.getX(), alongY.getX(), 1e-9);
        assertEquals(15.0, Math.abs(alongY.getY() - FID1.getY()), 1e-9);
        assertEquals(FID1.getZ(), alongX.getZ(), 1e-9);
    }

    @Test
    public void aWantedPositionGoesToTheNearestHoleNodeNotTheNearestNode() {
        // 294.4 along X on the fiducial's row: nearest node is i = 5 (292.3), j = 0, sum odd - a hole.
        Location hole = StagingPlate.nearestHole(FID1, 294.4, FID1.getY(), null, false);
        assertEquals(FID1.getX() + 75, hole.getX(), 1e-9);
        assertEquals(FID1.getY(), hole.getY(), 1e-9);
        // 310 along X for a reading of X: nearest node i = 6 (307.3), sum even, no hole; the X
        // is kept and the row steps to j = 1 or -1.
        Location hole2 = StagingPlate.nearestHole(FID1, 310, FID1.getY(), null, false);
        assertEquals(FID1.getX() + 90, hole2.getX(), 1e-9);
        assertEquals(15.0, Math.abs(hole2.getY() - FID1.getY()), 1e-9);
        // 60 up for a reading of Y: node (0, 4), even; the Y is kept and the column steps.
        Location hole3 = StagingPlate.nearestHole(FID1, FID1.getX(), FID1.getY() + 60, null, true);
        assertEquals(15.0, Math.abs(hole3.getX() - FID1.getX()), 1e-9);
        assertEquals(FID1.getY() + 60, hole3.getY(), 1e-9);
    }

    @Test
    public void theBottomCameraHoleAndTheEndOfThePlateHaveNoHoles() {
        Location camera = new Location(LengthUnit.Millimeters, 221.17, 151.25, 0, 0);
        assertNull(StagingPlate.nearestHole(FID1, 221, 151, camera, true),
                "under the 45 mm camera hole there is nothing to find");
        assertNotNull(StagingPlate.nearestHole(FID1, 221, 151, null, true));
        assertNull(StagingPlate.nearestHole(FID1, FID1.getX(), FID1.getY() + 400, null, true),
                "400 mm up is off the plate");
        assertNotNull(StagingPlate.nearestHole(FID1, 20, FID1.getY(), null, false),
                "the plate is wider than the X travel");
    }
}
