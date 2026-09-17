package org.openpnp.machine.reference.solutions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.machine.reference.camera.ReferenceCamera;
import org.openpnp.machine.reference.ReferenceHead;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis;
import org.openpnp.machine.reference.axis.ReferenceLinearTransformAxis;
import org.openpnp.model.Configuration;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Axis;
import org.openpnp.spi.base.AbstractAxis;

/**
 * The compensation is the inverse of what the datum board measured, the primary fiducial keeps
 * its coordinates, and applying it to a machine can be undone to the last property.
 */
public class MachineCompensationTest {
    private static final Location ANCHOR = new Location(LengthUnit.Millimeters, 217.287, 196.534, 5.7, 0);

    @Test
    public void theTransformUndoesTheMachinesScaleAndLeavesTheAnchorWhereItIs() {
        MachineCompensation c = new MachineCompensation(1.01327, 1.01027, 0, ANCHOR);

        Location anchorTrue = c.toTrue(ANCHOR);
        assertEquals(ANCHOR.getX(), anchorTrue.getX(), 1e-9);
        assertEquals(ANCHOR.getY(), anchorTrue.getY(), 1e-9);
        assertEquals(ANCHOR.getZ(), anchorTrue.getZ(), 1e-9);

        // 70.93 controller millimetres to the right of the anchor is 70.00 true millimetres.
        Location fid7Raw = ANCHOR.add(new Location(LengthUnit.Millimeters, 70.0 * 1.01327, 0, 0, 0));
        assertEquals(70.0, c.toTrue(fid7Raw).getX() - anchorTrue.getX(), 1e-6);
        // And back.
        Location back = c.toRaw(c.toTrue(fid7Raw));
        assertEquals(fid7Raw.getX(), back.getX(), 1e-9);
        assertEquals(fid7Raw.getY(), back.getY(), 1e-9);
    }

    @Test
    public void squarenessLeansTheYAxisBack() {
        double shear = 0.2;
        MachineCompensation c = new MachineCompensation(1.0, 1.0, shear, ANCHOR);
        // A point 100 mm up the controller's Y axis, which leans 0.2 degrees towards +X, is
        // 100 mm up and 0.349 mm to the left in true millimetres.
        Location up = ANCHOR.add(new Location(LengthUnit.Millimeters, 0, 100, 0, 0));
        Location trueUp = c.toTrue(up);
        assertEquals(-100 * Math.tan(Math.toRadians(shear)), trueUp.getX() - ANCHOR.getX(), 1e-6);
        assertEquals(100 / Math.cos(Math.toRadians(shear)), trueUp.getY() - ANCHOR.getY(), 1e-6);
        assertEquals(up.getX(), c.toRaw(trueUp).getX(), 1e-9);
    }

    @Test
    public void aVectorIsScaledButNotShifted() {
        MachineCompensation c = new MachineCompensation(1.02, 1.01, 0, ANCHOR);
        Location offsets = new Location(LengthUnit.Millimeters, 10.2, -20.2, 3, 45);

        Location trueOffsets = c.toTrueVector(offsets);
        assertEquals(10.0, trueOffsets.getX(), 1e-9);
        assertEquals(-20.0, trueOffsets.getY(), 1e-9);
        assertEquals(3, trueOffsets.getZ(), 1e-9);
        assertEquals(45, trueOffsets.getRotation(), 1e-9);
        assertEquals(offsets.getX(), c.toRawVector(trueOffsets).getX(), 1e-9);

        Location upp = new Location(LengthUnit.Millimeters, 0.010055, 0.010041, 0, 0);
        assertEquals(0.010055 / 1.02, c.unitsPerPixelToTrue(upp).getX(), 1e-12);
        assertEquals(0.010041 / 1.01, c.unitsPerPixelToTrue(upp).getY(), 1e-12);
    }

    @Test
    public void aScaleThatIsNotAMachineIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new MachineCompensation(1.2, 1.0, 0, ANCHOR));
    }

    @Test
    public void propertyNamesSayWhatTheyAre() {
        assertEquals(MachineCompensation.Kind.Absolute, MachineCompensation.kindOf("referenceHoleLocation"));
        assertEquals(MachineCompensation.Kind.Absolute, MachineCompensation.kindOf("changerStartLocation"));
        assertEquals(MachineCompensation.Kind.Absolute, MachineCompensation.kindOf("hole1Location"));
        assertEquals(MachineCompensation.Kind.Absolute, MachineCompensation.kindOf("fiducial1Location"));
        assertEquals(MachineCompensation.Kind.Absolute, MachineCompensation.kindOf("calibrationPrimaryFiducialLocation"));
        assertEquals(MachineCompensation.Kind.Vector, MachineCompensation.kindOf("headOffsets"));
        assertEquals(MachineCompensation.Kind.Vector, MachineCompensation.kindOf("offsets"));
        assertEquals(MachineCompensation.Kind.Vector, MachineCompensation.kindOf("visionOffset"));
        assertEquals(MachineCompensation.Kind.Skip, MachineCompensation.kindOf("unitsPerPixel"));
        assertEquals(MachineCompensation.Kind.Skip, MachineCompensation.kindOf("homingFiducialLocation"));
        assertEquals(MachineCompensation.Kind.Skip, MachineCompensation.kindOf("templateImageTopLeft"));
    }

    // ---- against a machine ----

    @TempDir
    Path tempDir;

    private ReferenceMachine machine;
    private ReferenceHead head;
    private ReferenceCamera camera;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
        machine = (ReferenceMachine) Configuration.get().getMachine();
        head = (ReferenceHead) machine.getDefaultHead();
        camera = (ReferenceCamera) head.getDefaultCamera();
    }

    private ReferenceControllerAxis controllerAxis(AbstractAxis axis) {
        return (ReferenceControllerAxis) axis;
    }

    @Test
    public void applyingMovesTheHeadMountablesOntoTrueAxesAndCarriesTheCoordinates() throws Exception {
        ReferenceControllerAxis rawX = controllerAxis(camera.getAxisX());
        ReferenceControllerAxis rawY = controllerAxis(camera.getAxisY());
        assertNotNull(rawX);
        assertNotNull(rawY);
        head.setCalibrationPrimaryFiducialLocation(ANCHOR);
        Location farFiducial = ANCHOR.add(new Location(LengthUnit.Millimeters, 101.327, 0, 0, 0));
        head.setCalibrationSecondaryFiducialLocation(farFiducial);
        Location upp = camera.getUnitsPerPixel();
        Location offsets = new Location(LengthUnit.Millimeters, 10.1327, 0, 0, 0);
        camera.setHeadOffsets(offsets);
        int axesBefore = machine.getAxes().size();

        MachineCompensation c = new MachineCompensation(1.01327, 1.0, 0, ANCHOR);
        MachineCompensation.Applied applied = c.apply(machine, rawX, rawY, null);

        assertEquals(axesBefore + 2, machine.getAxes().size());
        assertTrue(camera.getAxisX() instanceof ReferenceLinearTransformAxis);
        ReferenceLinearTransformAxis trueX = (ReferenceLinearTransformAxis) camera.getAxisX();
        assertSame(rawX, trueX.getInputAxisX());
        assertEquals(Axis.Type.X, trueX.getType());
        assertTrue(trueX.isCompensation());
        assertEquals(1 / 1.01327, trueX.getFactorX(), 1e-12);
        // The anchor stays; the far fiducial is 100 true millimetres away now.
        assertEquals(ANCHOR.getX(), head.getCalibrationPrimaryFiducialLocation().getX(), 1e-9);
        assertEquals(100.0, head.getCalibrationSecondaryFiducialLocation().getX() - ANCHOR.getX(), 1e-6);
        // Offsets are scaled, not shifted; Units per Pixel is in true millimetres.
        assertEquals(10.0, camera.getHeadOffsets().getX(), 1e-6);
        assertEquals(upp.getX() / 1.01327, camera.getUnitsPerPixel().getX(), 1e-12);
        assertFalse(applied.changes.isEmpty());

        // And back to the last property.
        applied.undo(machine);
        assertEquals(axesBefore, machine.getAxes().size());
        assertSame(rawX, camera.getAxisX());
        assertEquals(farFiducial.getX(), head.getCalibrationSecondaryFiducialLocation().getX(), 1e-9);
        assertEquals(offsets.getX(), camera.getHeadOffsets().getX(), 1e-9);
        assertEquals(upp.getX(), camera.getUnitsPerPixel().getX(), 1e-12);
    }

    @Test
    public void applyingTwiceUpdatesTheAxesRatherThanStackingThem() throws Exception {
        ReferenceControllerAxis rawX = controllerAxis(camera.getAxisX());
        ReferenceControllerAxis rawY = controllerAxis(camera.getAxisY());
        head.setCalibrationPrimaryFiducialLocation(ANCHOR);
        int axesBefore = machine.getAxes().size();

        new MachineCompensation(1.01, 1.0, 0, ANCHOR).apply(machine, rawX, rawY, null);
        AbstractAxis first = camera.getAxisX();
        // The second measurement was made through the first compensation, so the second
        // transform composes onto it: 1.01 of residual on top of 1.01 already taken out.
        MachineCompensation.Applied second = new MachineCompensation(1.01, 1.0, 0, ANCHOR)
                .apply(machine, rawX, rawY, null);

        assertEquals(axesBefore + 2, machine.getAxes().size(), "no second pair of axes");
        assertSame(first, camera.getAxisX());
        assertEquals(1 / (1.01 * 1.01), ((ReferenceLinearTransformAxis) first).getFactorX(), 1e-12);
        assertTrue(second.createdAxes.isEmpty());
        assertNotSame(null, second);
        // The anchor still does not move through the composed transform.
        ReferenceLinearTransformAxis x = (ReferenceLinearTransformAxis) first;
        double anchorThrough = x.getFactorX() * ANCHOR.getX() + x.getFactorY() * ANCHOR.getY()
                + x.getOffset().convertToUnits(LengthUnit.Millimeters).getValue();
        assertEquals(ANCHOR.getX(), anchorThrough, 1e-9);

        second.undo(machine);
        assertEquals(1 / 1.01, x.getFactorX(), 1e-12, "the first compensation stands again");
    }
}
