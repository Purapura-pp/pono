package org.openpnp.machine.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;

/**
 * Visual homing resets the frame from where the camera stands and where it saw the fiducial
 * from there, so that a short move onto the fiducial that the axis did or did not make has no
 * say in where the origin ends up.
 */
public class VisualHomingFrameTest {
    private static final Location TAUGHT = new Location(LengthUnit.Millimeters, 221.639, 196.986, 0, 0);

    /** The camera on the fiducial exactly: the frame does not move. */
    @Test
    public void aCameraOnTheFiducialKeepsItsCoordinates() {
        Location camera = new Location(LengthUnit.Millimeters, 221.639, 196.986, 7.8, 0);

        Location homed = ReferenceHead.cameraLocationInHomedFrame(TAUGHT, camera, camera);

        assertEquals(221.639, homed.getX(), 1e-9);
        assertEquals(196.986, homed.getY(), 1e-9);
        assertEquals(7.8, homed.getZ(), 1e-9, "Z is the camera's own");
    }

    /**
     * The seventh session: after mechanical homing the camera stood at the taught coordinates
     * and saw the fiducial 0.03 mm to +X and 0.04 mm to +Y. The frame that puts the fiducial
     * onto the taught location has the camera 0.03 / 0.04 mm to -X / -Y of it - whether or not
     * the camera is then moved.
     */
    @Test
    public void theFiducialSeenBesideTheCameraShiftsTheFrameByThatMuch() {
        Location camera = new Location(LengthUnit.Millimeters, 221.639, 196.986, 7.8, 0);
        Location seen = new Location(LengthUnit.Millimeters, 221.669, 197.026, 7.8, 0);

        Location homed = ReferenceHead.cameraLocationInHomedFrame(TAUGHT, camera, seen);

        assertEquals(221.609, homed.getX(), 1e-9);
        assertEquals(196.946, homed.getY(), 1e-9);
        // And the fiducial, seen 0.03 / 0.04 from the camera, is then at the taught location.
        assertEquals(TAUGHT.getX(), homed.getX() + (seen.getX() - camera.getX()), 1e-9);
        assertEquals(TAUGHT.getY(), homed.getY() + (seen.getY() - camera.getY()), 1e-9);
    }

    /** The camera need not be anywhere near the fiducial: a parallax viewpoint 2 mm off works too. */
    @Test
    public void theCameraMayStandAwayFromTheFiducial() {
        Location camera = new Location(LengthUnit.Millimeters, 223.639, 196.986, 7.8, 0);
        Location seen = new Location(LengthUnit.Millimeters, 221.700, 196.900, 7.8, 0);

        Location homed = ReferenceHead.cameraLocationInHomedFrame(TAUGHT, camera, seen);

        assertEquals(TAUGHT.getX(), homed.getX() + (seen.getX() - camera.getX()), 1e-9);
        assertEquals(TAUGHT.getY(), homed.getY() + (seen.getY() - camera.getY()), 1e-9);
    }

    /** Units are the camera's; the taught location and the detection may come in others. */
    @Test
    public void unitsAreTheCamerasOwn() {
        Location camera = new Location(LengthUnit.Millimeters, 221.639, 196.986, 7.8, 0);
        Location taughtInches = TAUGHT.convertToUnits(LengthUnit.Inches);
        Location seen = new Location(LengthUnit.Millimeters, 221.669, 197.026, 7.8, 0).convertToUnits(LengthUnit.Inches);

        Location homed = ReferenceHead.cameraLocationInHomedFrame(taughtInches, camera, seen);

        assertEquals(LengthUnit.Millimeters, homed.getUnits());
        assertEquals(221.609, homed.getX(), 1e-9);
        assertEquals(196.946, homed.getY(), 1e-9);
    }
}
