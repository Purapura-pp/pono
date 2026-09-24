package org.openpnp.gui.machinesettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.machine.reference.ReferenceHead;
import org.openpnp.machine.reference.ReferenceHead.NozzleSolution;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.ReferenceNozzleTip;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis;
import org.openpnp.machine.reference.axis.ReferenceMappedAxis;
import org.openpnp.machine.reference.driver.AbstractReferenceDriver.CommunicationsType;
import org.openpnp.machine.reference.driver.GcodeDriver;
import org.openpnp.machine.reference.driver.SerialPortCommunications;
import org.openpnp.machine.reference.solutions.HeadSolutions;
import org.openpnp.machine.reference.solutions.HeadSolutions.NozzlePlan;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Axis;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.NozzleTip;
import org.openpnp.spi.base.AbstractHead.VacuumPumpControl;

/**
 * The machine settings page's model: the nozzles' structure read off their axes rather than the
 * head's say-so, a change of structure said before it is made and made as said, and the settings
 * a machine's own definition lacks on this computer.
 */
public class MachineSettingsTest {
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

    private static Length mm(double value) {
        return new Length(value, LengthUnit.Millimeters);
    }

    // ---- the nozzles' structure ---------------------------------------------------------------

    @Test
    public void theDefaultHeadIsOneNozzleOnItsOwnZ() {
        NozzleStructure structure = NozzleStructure.of(head);
        assertEquals(NozzleSolution.Standalone, structure.solution);
        assertEquals(1, structure.nozzles);
        assertEquals(1, structure.multiplier);
    }

    @Test
    public void aPairOnOneMappedAxisIsDualNegatedWhateverTheHeadSays() throws Exception {
        new HeadSolutions(head).applyNozzleSolution(NozzleSolution.DualNegated, 1);
        // What the LumenPnP configuration says of its pair.
        head.setNozzleSolution(NozzleSolution.Standalone);
        NozzleStructure structure = NozzleStructure.of(head);
        assertEquals(NozzleSolution.DualNegated, structure.solution);
        assertEquals(2, structure.nozzles);
        assertEquals(1, structure.multiplier);
    }

    @Test
    public void planningTheStructureAHeadAlreadyHasChangesNothing() throws Exception {
        HeadSolutions solutions = new HeadSolutions(head);
        solutions.applyNozzleSolution(NozzleSolution.DualNegated, 1);
        assertTrue(solutions.planNozzleSolution(NozzleSolution.DualNegated, 1).isEmpty());
    }

    @Test
    public void aMappedAxisWithAnotherMappingIsSaidToBeSetUpAfresh() throws Exception {
        HeadSolutions solutions = new HeadSolutions(head);
        solutions.applyNozzleSolution(NozzleSolution.DualNegated, 1);
        for (Axis axis : machine.getAxes()) {
            if (axis instanceof ReferenceMappedAxis) {
                // A LumenPnP's: z2 = 63 - z1.
                ReferenceMappedAxis mapped = (ReferenceMappedAxis) axis;
                mapped.setMapOutput0(mm(63));
                mapped.setMapInput1(mm(63));
                mapped.setMapOutput1(mm(0));
            }
        }
        NozzlePlan plan = solutions.planNozzleSolution(NozzleSolution.DualNegated, 1);
        assertEquals(1, plan.remapped.size());
        assertTrue(plan.created.isEmpty() && plan.removed.isEmpty() && plan.renamed.isEmpty());
    }

    @Test
    public void thePlanSaysWhatApplyingItDoes() throws Exception {
        HeadSolutions solutions = new HeadSolutions(head);
        solutions.applyNozzleSolution(NozzleSolution.DualNegated, 1);
        Set<String> before = names();

        NozzlePlan plan = solutions.planNozzleSolution(NozzleSolution.Standalone, 2);
        solutions.applyNozzleSolution(NozzleSolution.Standalone, 2);
        Set<String> after = names();

        for (NozzlePlan.Item item : plan.created) {
            assertTrue(after.contains(item.kind + " " + item.to), "made " + item.to);
        }
        for (NozzlePlan.Item item : plan.renamed) {
            assertTrue(before.contains(item.kind + " " + item.from), "had " + item.from);
            assertTrue(after.contains(item.kind + " " + item.to), "renamed " + item.to);
        }
        for (NozzlePlan.Item item : plan.removed) {
            assertTrue(before.contains(item.kind + " " + item.from), "had " + item.from);
        }
        // The pair's mapped axis goes, and each nozzle has a Z motor of its own.
        assertTrue(plan.removed.stream().anyMatch(i -> i.kind == NozzlePlan.Kind.Axis));
        for (Axis axis : machine.getAxes()) {
            assertFalse(axis instanceof ReferenceMappedAxis, axis.getName());
        }
        List<String> nozzles = new ArrayList<>();
        for (Nozzle nozzle : head.getNozzles()) {
            nozzles.add(nozzle.getName());
            assertTrue(nozzle.getAxisZ() instanceof ReferenceControllerAxis, nozzle.getName());
        }
        assertEquals(List.of("N1", "N2"), nozzles);
        assertEquals(NozzleSolution.Standalone, NozzleStructure.of(head).solution);
    }

    @Test
    public void theVacuumActuatorsKeepTheNamesTheNozzlesKnowThemBy() throws Exception {
        new HeadSolutions(head).applyNozzleSolution(NozzleSolution.DualNegated, 1);
        Set<String> actuators = new HashSet<>();
        for (Actuator actuator : head.getActuators()) {
            actuators.add(actuator.getName());
        }
        assertTrue(actuators.containsAll(List.of("VAC1", "VACS1", "VAC2", "VACS2")), actuators.toString());
    }

    /** Every nozzle, axis and actuator by kind and name, as the plan's items name them. */
    private Set<String> names() {
        Set<String> names = new HashSet<>();
        for (Nozzle nozzle : head.getNozzles()) {
            names.add(NozzlePlan.Kind.Nozzle + " " + nozzle.getName());
        }
        for (Axis axis : machine.getAxes()) {
            names.add(NozzlePlan.Kind.Axis + " " + axis.getName());
        }
        for (Actuator actuator : head.getActuators()) {
            names.add(NozzlePlan.Kind.Actuator + " " + actuator.getName());
        }
        return names;
    }

    // ---- what the definition lacks ------------------------------------------------------------

    @Test
    public void aHeadWithoutAPumpIsAskedForOneUnlessItControlsNone() {
        head.setPumpActuator(null);
        head.setVacuumPumpControl(VacuumPumpControl.PartOn);
        assertNotNull(SetupChecks.of(machine).about(SetupChecks.NO_PUMP, head));
        head.setVacuumPumpControl(VacuumPumpControl.None);
        assertNull(SetupChecks.of(machine).about(SetupChecks.NO_PUMP, head));
    }

    @Test
    public void nozzleTipsWithTheSameProblemAreOneThingToFix() throws Exception {
        ReferenceNozzleTip first = (ReferenceNozzleTip) machine.getNozzleTips().get(0);
        ReferenceNozzleTip second = new ReferenceNozzleTip();
        second.setName("N08");
        machine.addNozzleTip(second);
        for (NozzleTip tip : machine.getNozzleTips()) {
            ReferenceNozzleTip reference = (ReferenceNozzleTip) tip;
            reference.getCalibration().setEnabled(true);
            reference.setMaxPickTolerance(mm(1));
            reference.setMinPartDiameter(mm(0));
            reference.setMaxPartDiameter(mm(20));
        }
        List<SetupChecks.Check> checks = kind(SetupChecks.of(machine), SetupChecks.MIN_DIAMETER);
        assertEquals(1, checks.size());
        assertEquals(2, checks.get(0).subjects.size());
        assertEquals(MachineSettingsPanel.NOZZLES, checks.get(0).topic);

        first.setMinPartDiameter(mm(3));
        checks = kind(SetupChecks.of(machine), SetupChecks.MIN_DIAMETER);
        assertEquals(List.of(second), checks.get(0).subjects);
    }

    @Test
    public void aTipWithItsCalibrationOffIsNotCheckedForIt() {
        ReferenceNozzleTip tip = (ReferenceNozzleTip) machine.getNozzleTips().get(0);
        tip.getCalibration().setEnabled(false);
        tip.setMinPartDiameter(mm(0));
        assertTrue(kind(SetupChecks.of(machine), SetupChecks.MIN_DIAMETER).isEmpty());
    }

    @Test
    public void theSmallestPartMustBeLargerThanTwiceThePickTolerance() {
        assertFalse(SetupChecks.minimumValid(mm(2), mm(1)));
        assertTrue(SetupChecks.minimumValid(mm(2.1), mm(1)));
        assertFalse(SetupChecks.minimumValid(null, mm(1)));
    }

    @Test
    public void aSerialPortThisComputerDoesNotHaveIsReported() throws Exception {
        String[] ports;
        try {
            ports = SerialPortCommunications.getPortNames();
        }
        catch (Throwable e) {
            ports = null;
        }
        Assumptions.assumeTrue(ports != null, "no serial library here");
        GcodeDriver driver = new GcodeDriver();
        driver.setName("LumenPnP");
        driver.setCommunicationsType(CommunicationsType.serial);
        driver.setPortName("ttyACM0-not-on-this-computer");
        machine.addDriver(driver);
        SetupChecks.Check missing = SetupChecks.of(machine).about(SetupChecks.PORT_MISSING, driver);
        assertNotNull(missing);
        assertEquals(MachineSettingsPanel.CONNECTION, missing.topic);
        assertTrue(missing.text().contains("ttyACM0-not-on-this-computer"), missing.text());

        driver.setPortName(null);
        assertNotNull(SetupChecks.of(machine).about(SetupChecks.NO_PORT, driver));
        driver.setCommunicationsType(CommunicationsType.tcp);
        assertNull(SetupChecks.of(machine).about(SetupChecks.NO_PORT, driver));
    }

    private static List<SetupChecks.Check> kind(SetupChecks checks, String kind) {
        List<SetupChecks.Check> of = new ArrayList<>();
        for (SetupChecks.Check check : checks.all()) {
            if (check.kind.equals(kind)) {
                of.add(check);
            }
        }
        return of;
    }
}
