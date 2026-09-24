package org.openpnp.machine.reference.feeder.wizards;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.machine.reference.feeder.BlindsFeeder;
import org.openpnp.machine.reference.feeder.ReferenceAutoFeeder;
import org.openpnp.machine.reference.feeder.ReferenceDragFeeder;
import org.openpnp.machine.reference.feeder.ReferenceHeapFeeder;
import org.openpnp.machine.reference.feeder.ReferencePushPullFeeder;
import org.openpnp.machine.reference.feeder.ReferenceRotatedTrayFeeder;
import org.openpnp.machine.reference.feeder.ReferenceTrayFeeder;
import org.openpnp.machine.reference.feeder.ReferenceTubeFeeder;
import org.openpnp.machine.reference.feeder.SchultzFeeder;
import org.openpnp.model.Configuration;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;

/**
 * The feeders' forms, P9 W5: each builds on properties that are there and writes what is on
 * screen when Apply is pressed, not before.
 */
public class FeederFormsTest {
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

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
    }

    private static FormWizard contained(FormWizard form) {
        form.setWizardContainer(CONTAINER);
        return form;
    }

    private static double mm(org.openpnp.model.Length length) {
        return length.convertToUnits(LengthUnit.Millimeters).getValue();
    }

    @Test
    public void theCommonPartAndTheTrayWriteOnApply() {
        ReferenceTubeFeeder tube = new ReferenceTubeFeeder();
        FormWizard tubeForm = contained(FeederForm.tube(tube));
        assertTrue(Form.properties(tubeForm).containsAll(List.of("part", "feedRetryCount", "pickRetryCount", "location")),
                Form.properties(tubeForm).toString());
        tubeForm.setValue("feedRetryCount", "4");
        assertFalse(tube.getFeedRetryCount() == 4, "nothing is written before Apply");
        tubeForm.apply();
        assertEquals(4, tube.getFeedRetryCount());

        ReferenceTrayFeeder tray = new ReferenceTrayFeeder();
        FormWizard trayForm = contained(FeederForm.tray(tray));
        trayForm.setLocation("offsets", new Location(LengthUnit.Millimeters, 4, 6, 0, 0));
        trayForm.setValue("trayCountX", "3");
        trayForm.setValue("trayCountY", "2");
        trayForm.apply();
        assertEquals(3, tray.getTrayCountX());
        assertEquals(6, tray.getOffsets().convertToUnits(LengthUnit.Millimeters).getY(), 1e-9);
    }

    @Test
    public void theActuatedFeedersWriteOnApply() {
        ReferenceAutoFeeder auto = new ReferenceAutoFeeder();
        FormWizard autoForm = contained(FeederForm.auto(auto));
        autoForm.setValue("actuatorValue", "1");
        autoForm.set("moveBeforeFeed", true);
        autoForm.apply();
        assertEquals(1, auto.getActuatorValue(), 1e-9);
        assertTrue(auto.isMoveBeforeFeed());

        SchultzFeeder schultz = new SchultzFeeder();
        FormWizard schultzForm = contained(SchultzForm.build(schultz));
        assertTrue(Form.properties(schultzForm).containsAll(List.of("actuatorName", "postPickActuatorName",
                "feedCountActuatorName", "clearCountActuatorName", "pitchActuatorName", "togglePitchActuatorName",
                "statusActuatorName", "idActuatorName", "actuatorValue")), Form.properties(schultzForm).toString());
        schultzForm.setValue("actuatorValue", "7");
        schultzForm.apply();
        assertEquals(7, schultz.getActuatorValue(), 1e-9);
    }

    @Test
    public void theDragFeederWritesItsAreaOfInterest() {
        ReferenceDragFeeder drag = new ReferenceDragFeeder();
        FormWizard form = contained(DragFeederForm.drag(drag));
        form.setValue("partPitch", "8");
        form.setValue("aoiX", "10");
        form.setValue("aoiWidth", "50");
        form.apply();
        assertEquals(8, mm(drag.getPartPitch()), 1e-9);
        assertEquals(10, drag.getVision().getAreaOfInterest().getX());
        assertEquals(50, drag.getVision().getAreaOfInterest().getWidth());
    }

    @Test
    public void theRotatedTrayWorksOutItsSteps() throws Exception {
        Location a = new Location(LengthUnit.Millimeters, 0, 0, 0, 0);
        Location b = new Location(LengthUnit.Millimeters, 20, 0, 0, 0);
        Location c = new Location(LengthUnit.Millimeters, 20, 10, 0, 0);
        double[] steps = RotatedTrayForm.steps(a, b, c, 5, 3, 0);
        assertArrayEquals(new double[] {5, -5, 0}, steps, 1e-9);

        ReferenceRotatedTrayFeeder feeder = new ReferenceRotatedTrayFeeder();
        FormWizard form = contained(RotatedTrayForm.build(feeder));
        form.setValue("trayCountCols", "5");
        form.setValue("pickZ", "-2");
        form.apply();
        assertEquals(5, feeder.getTrayCountCols());
        assertEquals(-2, feeder.getLocation().convertToUnits(LengthUnit.Millimeters).getZ(), 1e-9);
    }

    @Test
    public void thePushPullAndHeapFeedersWriteOnApply() {
        ReferencePushPullFeeder pushPull = new ReferencePushPullFeeder();
        FormWizard motion = contained(PushPullMotionForm.build(pushPull));
        motion.setValue("delay2", "120");
        motion.setValue("feedSpeedPush1", "50");
        motion.apply();
        assertEquals(120, pushPull.getDelay2());
        assertEquals(0.5, pushPull.getFeedSpeedPush1(), 1e-9);

        FormWizard config = contained(PushPullForm.build(pushPull));
        config.setValue("feedMultiplier", "2");
        config.apply();
        assertEquals(2, pushPull.getFeedMultiplier());

        ReferenceHeapFeeder heap = new ReferenceHeapFeeder();
        FormWizard heapForm = contained(HeapFeederForm.build(heap));
        heapForm.setValue("boxDepth", "12.5");
        heapForm.apply();
        assertEquals(12.5, heap.getBoxDepth(), 1e-9);
    }

    @Test
    public void theBlindsFeederKeepsItsLocationWhole() {
        BlindsFeeder blinds = new BlindsFeeder();
        blinds.setLocation(new Location(LengthUnit.Millimeters, 11, 22, 3, 90));
        FormWizard form = contained(BlindsFeederForm.feeder(blinds));
        form.setValue("partZ", "4");
        form.apply();
        Location location = blinds.getLocation().convertToUnits(LengthUnit.Millimeters);
        assertEquals(4, location.getZ(), 1e-9);
        assertEquals(11, location.getX(), 1e-9, "the part height leaves X and Y as they are");
        assertEquals(90, location.getRotation(), 1e-9);

        FormWizard array = contained(BlindsFeederForm.array(blinds));
        array.setLocation("fiducial2Location", new Location(LengthUnit.Millimeters, 110, 10, 0, 0));
        array.set("normalize", !blinds.isNormalize());
        boolean normalize = !blinds.isNormalize();
        array.apply();
        assertEquals(110, blinds.getFiducial2Location().convertToUnits(LengthUnit.Millimeters).getX(), 1e-9);
        assertEquals(normalize, blinds.isNormalize());
    }
}
