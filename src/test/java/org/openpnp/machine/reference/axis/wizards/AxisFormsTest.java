package org.openpnp.machine.reference.axis.wizards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.axis.ReferenceCamClockwiseAxis;
import org.openpnp.machine.reference.axis.ReferenceCamCounterClockwiseAxis;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis.BacklashCompensationMethod;
import org.openpnp.machine.reference.axis.ReferenceLinearTransformAxis;
import org.openpnp.machine.reference.axis.ReferenceMappedAxis;
import org.openpnp.machine.reference.axis.ReferenceVirtualAxis;
import org.openpnp.machine.reference.driver.ReferenceAdvancedMotionPlanner;
import org.openpnp.machine.reference.driver.wizards.MotionPlannerForm;
import org.openpnp.model.Configuration;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Axis;

/**
 * The axes' and the motion planner's forms, P9 W2: each builds on properties that are there and
 * writes what is on screen when Apply is pressed.
 */
public class AxisFormsTest {
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

    private ReferenceMachine machine;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
        machine = (ReferenceMachine) Configuration.get().getMachine();
    }

    private static FormWizard contained(FormWizard form) {
        form.setWizardContainer(CONTAINER);
        return form;
    }

    private ReferenceControllerAxis controllerAxis(Axis.Type type) {
        for (Axis axis : machine.getAxes()) {
            if (axis instanceof ReferenceControllerAxis && axis.getType() == type) {
                return (ReferenceControllerAxis) axis;
            }
        }
        throw new AssertionError("no controller axis of type " + type);
    }

    @Test
    public void theControllerAxisFormWritesOnApply() {
        ReferenceControllerAxis axis = controllerAxis(Axis.Type.X);
        FormWizard form = contained(AxisForm.controller(axis));
        assertTrue(Form.properties(form).containsAll(List.of("type", "name", "driver", "letter", "resolution",
                "softLimitLowEnabled", "softLimitLow", "safeZoneHigh", "feedratePerSecond", "jerkPerSecond3")),
                Form.properties(form).toString());
        form.setValue("letter", "U");
        form.setValue("resolution", "0.0005");
        form.set("softLimitLowEnabled", true);
        form.setValue("softLimitLow", "-5");
        form.setValue("feedratePerSecond", "250");
        assertFalse("U".equals(axis.getLetter()), "nothing is written before Apply");
        form.apply();
        assertEquals("U", axis.getLetter());
        assertEquals(0.0005, axis.getResolution(), 1e-12);
        assertTrue(axis.isSoftLimitLowEnabled());
        assertEquals(-5, axis.getSoftLimitLow().convertToUnits(LengthUnit.Millimeters).getValue(), 1e-9);
        assertEquals(250, axis.getFeedratePerSecond().convertToUnits(LengthUnit.Millimeters).getValue(), 1e-9);
    }

    @Test
    public void theConversionsReadWhatIsTyped() {
        assertTrue(AxisForm.stepsPerUnit("0.001", "mm").contains("1000"), AxisForm.stepsPerUnit("0.001", "mm"));
        assertNull(AxisForm.stepsPerUnit("0", "mm"));
        assertTrue(AxisForm.perMinute("10").contains("600"), AxisForm.perMinute("10"));
    }

    @Test
    public void theBacklashFormWritesTheMethod() {
        ReferenceControllerAxis axis = controllerAxis(Axis.Type.Y);
        FormWizard form = contained(AxisForm.backlash(axis));
        form.set("backlashCompensationMethod", BacklashCompensationMethod.DirectionalSneakUp);
        form.setValue("backlashOffset", "0.05");
        form.setValue("sneakUpOffset", "0.2");
        form.apply();
        assertEquals(BacklashCompensationMethod.DirectionalSneakUp, axis.getBacklashCompensationMethod());
        assertEquals(0.05, axis.getBacklashOffset().convertToUnits(LengthUnit.Millimeters).getValue(), 1e-9);
        assertEquals(0.2, axis.getSneakUpOffset().convertToUnits(LengthUnit.Millimeters).getValue(), 1e-9);
    }

    @Test
    public void theTransformedAxesWriteOnApply() {
        ReferenceVirtualAxis virtual = new ReferenceVirtualAxis();
        FormWizard v = contained(AxisForm.virtual(virtual));
        v.setValue("homeCoordinate", "7");
        v.setValue("name", "zCam");
        v.apply();
        assertEquals(7, virtual.getHomeCoordinate().convertToUnits(LengthUnit.Millimeters).getValue(), 1e-9);
        assertEquals("zCam", virtual.getName());

        ReferenceMappedAxis mapped = new ReferenceMappedAxis();
        FormWizard m = contained(AxisForm.mapped(mapped));
        m.setValue("mapOutput1", "-1");
        m.apply();
        assertEquals(-1, mapped.getMapOutput1().convertToUnits(LengthUnit.Millimeters).getValue(), 1e-9);

        ReferenceLinearTransformAxis linear = new ReferenceLinearTransformAxis();
        FormWizard l = contained(AxisForm.linearTransform(linear));
        l.setValue("factorY", "0.001");
        l.set("compensation", true);
        l.apply();
        assertEquals(0.001, linear.getFactorY(), 1e-12);
        assertTrue(linear.isCompensation());

        ReferenceCamCounterClockwiseAxis ccw = new ReferenceCamCounterClockwiseAxis();
        FormWizard c = contained(AxisForm.camCounterClockwise(ccw));
        c.setValue("camRadius", "12");
        c.setValue("camArmsAngle", "15");
        c.apply();
        assertEquals(12, ccw.getCamRadius().convertToUnits(LengthUnit.Millimeters).getValue(), 1e-9);
        assertEquals(15, ccw.getCamArmsAngle(), 1e-9);

        FormWizard cw = contained(AxisForm.camClockwise(new ReferenceCamClockwiseAxis()));
        assertTrue(Form.properties(cw).contains("inputAxis"));
    }

    @Test
    public void theMotionPlannerFormsWriteOnApply() {
        ReferenceAdvancedMotionPlanner planner = new ReferenceAdvancedMotionPlanner();
        FormWizard settings = contained(MotionPlannerForm.settings(planner));
        settings.set("allowContinuousMotion", true);
        settings.setValue("minimumSpeed", "5");
        settings.set("startLocationEnabled", true);
        settings.setLocation("startLocation", new Location(LengthUnit.Millimeters, 10, 20, -3, 45));
        settings.setValue("toMid1Speed", "0.5");
        settings.apply();
        assertTrue(planner.isAllowContinuousMotion());
        assertEquals(0.05, planner.getMinimumSpeed(), 1e-9, "a percentage on screen, a fraction in the model");
        assertTrue(planner.isStartLocationEnabled());
        assertEquals(45, planner.getStartLocation().getRotation(), 1e-9);
        assertEquals(0.5, planner.getToMid1Speed(), 1e-9);

        FormWizard diagnostics = contained(MotionPlannerForm.diagnostics(planner));
        diagnostics.set("diagnosticsEnabled", true);
        diagnostics.apply();
        assertTrue(planner.isDiagnosticsEnabled());
    }
}
