package org.openpnp.machine.reference.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.machine.reference.ReferenceHead;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis;
import org.openpnp.machine.reference.solutions.MachineDiagnostics.TestGroup;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults;
import org.openpnp.model.CalibrationStep;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Solutions;
import org.openpnp.spi.Axis;
import org.openpnp.spi.Camera;

/** The catalogue expanded over a machine, and what each step says about itself. */
public class CalibrationPlanTest {
    @TempDir
    Path tempDir;

    private ReferenceMachine machine;
    private ReferenceHead head;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
        machine = (ReferenceMachine) Configuration.get().getMachine();
        head = (ReferenceHead) machine.getDefaultHead();
    }

    private CalibrationPlan plan() throws Exception {
        List<Solutions.Issue> issues = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> issues.addAll(CalibrationPlan.scan(machine, machine.getSolutions())));
        return CalibrationPlan.of(machine, issues);
    }

    private ReferenceControllerAxis axis(Axis.Type type) {
        for (Axis axis : machine.getAxes()) {
            if (axis.getType() == type && axis instanceof ReferenceControllerAxis) {
                return (ReferenceControllerAxis) axis;
            }
        }
        throw new AssertionError("no " + type + " controller axis");
    }

    private MachineDiagnosticsResults results() {
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        machine.getMachineDiagnostics().setLastResults(results);
        return results;
    }

    @Test
    public void theCatalogueHasTwentyFiveStepsAndThePlanKeepsItsOrder() throws Exception {
        assertEquals(25, CalibrationStep.values().length);
        CalibrationPlan plan = plan();
        int previous = -1;
        for (CalibrationPlan.Step step : plan.getSteps()) {
            assertTrue(step.getKind().ordinal() >= previous, "out of order at " + step);
            previous = step.getKind().ordinal();
        }
        assertEquals(CalibrationStep.Home, plan.getSteps().get(0).getKind());
        assertEquals(CalibrationStep.Remeasure, plan.getSteps().get(plan.getSteps().size() - 1).getKind());
        for (CalibrationStep kind : CalibrationStep.values()) {
            assertFalse(kind.getName().startsWith("CalibrationStep."), "no name for " + kind);
            assertFalse(kind.getDescription().startsWith("CalibrationStep."), "no description for " + kind);
        }
    }

    @Test
    public void stepsAreExpandedOverTheElementsTheyApplyTo() throws Exception {
        CalibrationPlan plan = plan();
        assertNotNull(plan.getStep(CalibrationStep.XyBacklash, axis(Axis.Type.X)));
        assertNotNull(plan.getStep(CalibrationStep.XyBacklash, axis(Axis.Type.Y)));
        assertNotNull(plan.getStep(CalibrationStep.ZBacklash, axis(Axis.Type.Z)));
        assertNotNull(plan.getStep(CalibrationStep.AdvancedDownCamera, head.getDefaultCamera()));
        assertNotNull(plan.getStep(CalibrationStep.NozzleTouchPrimary, head.getDefaultNozzle()));
        Camera up = null;
        for (Camera camera : machine.getCameras()) {
            if (camera.getLooking() == Camera.Looking.Up) {
                up = camera;
            }
        }
        assertNotNull(plan.getStep(CalibrationStep.BottomCamera, up));
        assertEquals(1, plan.getSteps(CalibrationStep.Home).size(), "one step for the whole machine");
        assertEquals(1, plan.getSteps(CalibrationStep.ControllerLimits).size());
        CalibrationPlan.Step x = plan.getStep(CalibrationStep.XyBacklash, axis(Axis.Type.X));
        assertEquals(x, plan.getStep(x.getKey()), "a step is found again by its key");
    }

    @Test
    public void anUnhomedMachineIsAskedToHomeAndTheRestWaits() throws Exception {
        CalibrationPlan plan = plan();
        CalibrationPlan.Step home = plan.getSteps(CalibrationStep.Home).get(0);
        assertEquals(CalibrationPlan.Status.Needed, home.getStatus());
        assertFalse(home.getActions().isEmpty(), "homing is what the home issue does");

        CalibrationPlan.Step softLimits = plan.getStep(CalibrationStep.SoftLimits, axis(Axis.Type.X));
        assertEquals(CalibrationPlan.Status.Waiting, softLimits.getStatus());
        assertEquals(List.of(home), softLimits.getUnsettledPrerequisites());
        assertEquals(CalibrationPlan.Status.Waiting,
                plan.getStep(CalibrationStep.PrimaryFiducial, head).getStatus());
    }

    @Test
    public void aStepDecidedByAMeasurementAsksForIt() throws Exception {
        CalibrationPlan.Step limits = plan().getSteps(CalibrationStep.ControllerLimits).get(0);
        assertEquals(CalibrationPlan.Status.NeedsMeasurement, limits.getStatus());
        assertEquals(java.util.Set.of(TestGroup.Firmware), limits.getMissingMeasurements());

        MachineDiagnosticsResults results = results();
        results.setRun(TestGroup.Firmware, System.currentTimeMillis(), "report");
        limits = plan().getSteps(CalibrationStep.ControllerLimits).get(0);
        assertEquals(CalibrationPlan.Status.Done, limits.getStatus(), "measured, and nothing wrong with it");

        results.invalidate(TestGroup.Firmware, "XyBacklash");
        CalibrationPlan plan = plan();
        assertEquals(CalibrationPlan.Status.NeedsMeasurement,
                plan.getSteps(CalibrationStep.ControllerLimits).get(0).getStatus());
        assertEquals(CalibrationPlan.Status.Needed, plan.getSteps(CalibrationStep.Remeasure).get(0).getStatus(),
                "what a step made out of date is measured again");
        assertTrue(plan.invalidated().contains(TestGroup.Firmware));
    }

    @Test
    public void aFindingIsTheStepsChangeAndItsBasis() throws Exception {
        ReferenceControllerAxis x = axis(Axis.Type.X);
        x.setFeedratePerSecond(new Length(500, LengthUnit.Millimeters));
        MachineDiagnosticsResults results = results();
        results.setControllerLimits(
                List.of(new MachineDiagnosticsResults.ControllerLimits(x.getId(), null, 300.0, null)));
        results.setRun(TestGroup.Firmware, System.currentTimeMillis(), "report");

        CalibrationPlan.Step limits = plan().getSteps(CalibrationStep.ControllerLimits).get(0);

        assertEquals(CalibrationPlan.Status.Needed, limits.getStatus());
        assertEquals(1, limits.getChanges().size(), "the feed rate, lowered to the controller's");
        SettingChange change = limits.getChanges().get(0);
        assertEquals(500, ((Length) change.getCurrentValue()).convertToUnits(LengthUnit.Millimeters).getValue(), 1e-9);
        assertEquals(300, ((Length) change.getProposedValue()).convertToUnits(LengthUnit.Millimeters).getValue(), 1e-9);
        CalibrationPlan.Basis basis = limits.getBasis(results).get(0);
        assertEquals(TestGroup.Firmware, basis.getGroup());
        assertNotNull(basis.getWhen());
        assertEquals(List.of("Feed rate per second: 500 mm \u2192 300 mm"),
                CalibrationRunner.describe(limits.getChanges()));
    }

    /**
     * A step that writes a measured value applies it by accepting its issue and takes it back by
     * reopening it, which is how the runner and the issues page both do it.
     */
    @Test
    public void aMeasuredChangeIsAppliedAndTakenBack() throws Exception {
        ReferenceControllerAxis x = axis(Axis.Type.X);
        x.setFeedratePerSecond(new Length(500, LengthUnit.Millimeters));
        MachineDiagnosticsResults results = results();
        results.setControllerLimits(
                List.of(new MachineDiagnosticsResults.ControllerLimits(x.getId(), null, 300.0, null)));
        results.setRun(TestGroup.Firmware, System.currentTimeMillis(), "report");
        Solutions.Issue change = plan().getSteps(CalibrationStep.ControllerLimits).get(0).getActions().get(0);

        change.setState(Solutions.State.Solved);
        assertEquals(300, x.getFeedratePerSecond().convertToUnits(LengthUnit.Millimeters).getValue(), 1e-9);
        assertEquals(CalibrationPlan.Status.Done, plan().getSteps(CalibrationStep.ControllerLimits).get(0).getStatus(),
                "the setting and the measurement agree");

        change.setState(Solutions.State.Open);
        assertEquals(500, x.getFeedratePerSecond().convertToUnits(LengthUnit.Millimeters).getValue(), 1e-9);
    }

    @Test
    public void theBacklashCalibrationIsTheStepOfItsAxisOnceTheFiducialIsSet() throws Exception {
        head.setCalibrationPrimaryFiducialLocation(new Location(LengthUnit.Millimeters, 50, 50, -10, 0));
        head.setCalibrationPrimaryFiducialDiameter(new Length(1, LengthUnit.Millimeters));
        ReferenceControllerAxis x = axis(Axis.Type.X);

        CalibrationPlan.Step backlash = plan().getStep(CalibrationStep.XyBacklash, x);

        assertEquals(1, backlash.getActions().size());
        assertEquals("Calibrate backlash compensation for axis " + x.getName() + ".",
                backlash.getActions().get(0).getUntranslatedIssue());
        assertEquals(x, backlash.getActions().get(0).getCalibrationSubject(),
                "the issue is about the camera that measures it, the step about the axis");
    }
}
