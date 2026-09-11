package org.openpnp.machine.reference.solutions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.solutions.MachineDiagnostics.TestGroup;
import org.openpnp.model.Configuration;

/**
 * Covers the part of the diagnostics that can run without a machine attached: the configuration
 * snapshot, and the checks that decide whether the rest may run at all.
 * <p>
 * The snapshot walks every axis, driver, head, camera, nozzle tip and vision setting and asks each
 * one for a dozen properties. That is a lot of calls that compile whatever they return, so without
 * a test the first thing to find a wrong getter or a null would be a user with a machine in front
 * of them.
 */
public class MachineDiagnosticsTest {
    @TempDir
    Path tempDir;

    private ReferenceMachine machine;
    private MachineDiagnostics diagnostics;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
        machine = (ReferenceMachine) Configuration.get().getMachine();
        diagnostics = machine.getMachineDiagnostics();
    }

    @Test
    public void theSnapshotDescribesTheMachineWithoutStartingIt() throws Exception {
        assertFalse(machine.isEnabled(), "this test is about a machine that is not running");

        File directory = diagnostics.run(machine, EnumSet.of(TestGroup.ConfigSnapshot));

        assertNotNull(directory);
        String snapshot = read(new File(directory, "config-snapshot.txt"));
        assertTrue(snapshot.contains("Axes"), "the snapshot lists the axes");
        assertTrue(snapshot.contains("Drivers"), "the snapshot lists the drivers");
        assertTrue(snapshot.contains("Cameras"), "the snapshot lists the cameras");
        assertTrue(snapshot.contains("Motion planner"), "the snapshot names the motion planner");

        String report = read(new File(directory, "report.txt"));
        assertTrue(report.contains("Configuration snapshot"),
                "the report says which measurements were taken");
    }

    /** A second run in the same second must not overwrite the first one's report. */
    @Test
    public void eachRunGetsItsOwnDirectory() throws Exception {
        File first = diagnostics.run(machine, EnumSet.of(TestGroup.ConfigSnapshot));
        File second = diagnostics.run(machine, EnumSet.of(TestGroup.ConfigSnapshot));

        assertFalse(first.equals(second), "two runs wrote to " + first);
        assertTrue(first.isDirectory());
        assertTrue(second.isDirectory());
    }

    @Test
    public void nothingSelectedIsRefused() {
        assertThrows(Exception.class,
                () -> diagnostics.run(machine, EnumSet.noneOf(TestGroup.class)));
    }

    /**
     * Positions measured against an unhomed machine mean nothing, and asking the controller for
     * its settings needs a controller to ask, so neither is allowed to start.
     */
    @Test
    public void measurementsNeedingTheMachineAreRefusedWhenItIsNotRunning() {
        assertThrows(Exception.class,
                () -> diagnostics.run(machine, EnumSet.of(TestGroup.XyPositioning)));
        assertThrows(Exception.class,
                () -> diagnostics.run(machine, EnumSet.of(TestGroup.Firmware)));
        assertThrows(Exception.class,
                () -> diagnostics.run(machine, EnumSet.of(TestGroup.ConfigSnapshot, TestGroup.Homing)));
    }

    /** A refused run must not leave the panel showing that measurements are in progress. */
    @Test
    public void aRefusedRunDoesNotLeaveTheDiagnosticsRunning() {
        assertThrows(Exception.class,
                () -> diagnostics.run(machine, EnumSet.of(TestGroup.Kinematics)));

        assertFalse(diagnostics.isRunning());
    }

    private String read(File file) throws Exception {
        assertTrue(file.isFile(), file + " was not written");
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
