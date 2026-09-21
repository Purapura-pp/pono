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
        // Under the 45 mm camera hole there is nothing to find; a reading of Y there keeps its Y
        // and goes to the hole two columns over, 30 mm from the camera's node.
        Location beside = StagingPlate.nearestHole(FID1, 221, 151, camera, true);
        assertNotNull(beside);
        assertEquals(30.0, Math.abs(beside.getX() - FID1.getX()), 1e-9);
        assertEquals(FID1.getY() - 45, beside.getY(), 1e-9);
        // The camera's own node and its diagonal neighbours have no hole, with or without the
        // camera's position given: the model knows where the camera is.
        assertNull(StagingPlate.hole(FID1, 0, -3, camera));
        assertNull(StagingPlate.hole(FID1, 0, -3, null));
        assertNull(StagingPlate.hole(FID1, 1, -2, null));
        assertNull(StagingPlate.hole(FID1, -1, -4, null));
        assertNotNull(StagingPlate.hole(FID1, 2, -3, null), "30 mm from the camera is clear");
        assertNotNull(StagingPlate.hole(FID1, 0, -1, null), "the fiducial's neighbour is clear");
        assertNull(StagingPlate.nearestHole(FID1, FID1.getX(), FID1.getY() + 400, null, true),
                "400 mm up is off the plate");
        assertNotNull(StagingPlate.nearestHole(FID1, 20, FID1.getY(), null, false),
                "the plate is wider than the X travel");
        // The plate's own bolt holes take the place of four lattice holes in each end column.
        assertNull(StagingPlate.hole(FID1, 19, 0, null));
        assertNull(StagingPlate.hole(FID1, -19, -6, null));
        assertNull(StagingPlate.hole(FID1, 19, 2, null));
        assertNull(StagingPlate.hole(FID1, -19, 8, null));
        assertNotNull(StagingPlate.hole(FID1, 19, 4, null));
        assertNotNull(StagingPlate.hole(FID1, 17, 0, null));
    }

    /** The plate's 280 holes, from the fiducial: 293 odd nodes less 5 under the camera, less 8 bolts. */
    @Test
    public void thePlateHasTwoHundredAndEightyHolesFromTheFiducial() {
        java.util.List<Location> holes = StagingPlate.allHoles(FID1, null);

        assertEquals(280, holes.size());
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Location h : holes) {
            minX = Math.min(minX, h.getX());
            maxX = Math.max(maxX, h.getX());
            minY = Math.min(minY, h.getY());
            maxY = Math.max(maxY, h.getY());
            long i = Math.round((h.getX() - FID1.getX()) / 15);
            long j = Math.round((h.getY() - FID1.getY()) / 15);
            assertEquals(1, Math.abs((i + j) % 2), "every hole is on an odd node: " + h);
        }
        // 570 by 210 mm: 19 columns either side of the fiducial, 6 rows below and 8 above.
        assertEquals(FID1.getX() - 285, minX, 1e-9);
        assertEquals(FID1.getX() + 285, maxX, 1e-9);
        assertEquals(FID1.getY() - 90, minY, 1e-9);
        assertEquals(FID1.getY() + 120, maxY, 1e-9);
        double[] yRange = StagingPlate.fieldYRange(FID1, null);
        assertEquals(106.534, yRange[0], 1e-9);
        assertEquals(316.534, yRange[1], 1e-9);
    }

    /** A plate lying the other way round has its camera above the fiducial and its field below. */
    @Test
    public void aPlateTheOtherWayRoundIsReadOffTheCamerasSide() {
        Location cameraAbove = new Location(LengthUnit.Millimeters, FID1.getX(), FID1.getY() + 45, 0, 0);

        assertEquals(-1, StagingPlate.ySign(FID1, cameraAbove));
        assertEquals(1, StagingPlate.ySign(FID1, null));
        java.util.List<Location> holes = StagingPlate.allHoles(FID1, cameraAbove);
        assertEquals(280, holes.size());
        double[] yRange = StagingPlate.fieldYRange(FID1, cameraAbove);
        assertEquals(FID1.getY() - 120, yRange[0], 1e-9);
        assertEquals(FID1.getY() + 90, yRange[1], 1e-9);
        assertNull(StagingPlate.hole(FID1, 1, 2, cameraAbove), "beside the camera, now above");
        assertNotNull(StagingPlate.hole(FID1, 1, -2, cameraAbove));
        assertNull(StagingPlate.hole(FID1, 19, -2, cameraAbove), "the bolt rows mirror too");
        assertNotNull(StagingPlate.hole(FID1, 19, 2, cameraAbove));
    }
}
