package org.openpnp.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.swing.JScrollPane;
import javax.swing.JTable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.gui.calibration.MeasurementForms;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.calibration.CalibrationPlan;
import org.openpnp.machine.reference.solutions.MachineDiagnostics.TestGroup;
import org.openpnp.model.CalibrationStep;
import org.openpnp.model.Configuration;

/**
 * What the calibration page took over from the diagnostics page: the overview describes the
 * default machine in every cell without being shown, every measurement group has a parameter
 * form whose fields name real properties of the diagnostics, and every group is either one of a
 * step's phases or one of the page's diagnostics.
 */
public class CalibrationPageTabsTest {
    @TempDir
    Path tempDir;

    private ReferenceMachine machine;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
        machine = (ReferenceMachine) Configuration.get().getMachine();
    }

    @Test
    public void theOverviewDescribesTheMachineInEveryCell() {
        MachineOverviewPanel overview = new MachineOverviewPanel(machine);
        overview.refresh();
        List<String> cells = new ArrayList<>();
        List<JTable> tables = new ArrayList<>();
        collect(overview, tables);
        assertTrue(tables.size() == 4, "the axes, cameras, drivers and nozzles, one card each");
        for (JTable table : tables) {
            for (int row = 0; row < table.getRowCount(); row++) {
                for (int column = 0; column < table.getColumnCount(); column++) {
                    cells.add(String.valueOf(table.getValueAt(row, column)));
                    table.prepareRenderer(table.getCellRenderer(row, column), row, column);
                }
            }
        }
        assertFalse(cells.contains("null"), "a cell reads \"null\": " + cells);
        for (String cell : cells) {
            assertFalse(cell.startsWith("!") && cell.endsWith("!"), "a missing translation: " + cell);
        }
        assertTrue(cells.contains("Top"), "the default machine's camera was not described");
        assertTrue(cells.contains("N1"), "the default machine's nozzle was not described");
        assertTrue(overview.asText().contains("N1"), "copy as text leaves the nozzle out");
    }

    @Test
    public void everyGroupHasItsParametersForm() {
        for (TestGroup group : TestGroup.values()) {
            FormWizard form = MeasurementForms.form(machine.getMachineDiagnostics(), group);
            assertNotNull(form, group.name());
            // Built means every field named a readable and writable property of the diagnostics.
            Form.properties(form);
        }
    }

    @Test
    public void everyGroupIsAStepsPhaseOrADiagnostic() {
        assertEquals(EnumSet.of(TestGroup.VisionNoise, TestGroup.CameraLatency, TestGroup.XyPositioning,
                TestGroup.Homing, TestGroup.HysteresisMap, TestGroup.ConfigSnapshot),
                CalibrationPlan.diagnosticGroups(), "the diagnostics are the groups no step measures");
        for (CalibrationStep step : CalibrationStep.values()) {
            assertNotNull(step.getWay(), step.name());
            assertFalse(step.getPhases().isEmpty(), step.name());
            for (CalibrationStep.Phase phase : step.getPhases()) {
                String name = phase.getName();
                assertFalse(name.startsWith("!") && name.endsWith("!"), "a missing translation: " + name);
            }
            for (TestGroup group : CalibrationPlan.decidedBy(step)) {
                assertTrue(step.phaseOfGroup(group.name()) >= 0,
                        step + " is decided by " + group + ", which is not one of its phases");
            }
            boolean someoneFirst = step.getWay() != CalibrationStep.Way.Auto
                    && step.getWay() != CalibrationStep.Way.Recurring;
            assertEquals(someoneFirst, step.firstPersonPhase() == 0,
                    step + ": only a step that is not all automatic starts with someone's part");
            if (step.getWay() == CalibrationStep.Way.Manual || step.getWay() == CalibrationStep.Way.Prepare) {
                assertTrue(step.isNeedsPerson(), step + " is waited for before it starts");
            }
        }
    }

    private static void collect(java.awt.Container container, List<JTable> tables) {
        for (java.awt.Component component : container.getComponents()) {
            if (component instanceof JTable) {
                tables.add((JTable) component);
            }
            else if (component instanceof JScrollPane) {
                collect(((JScrollPane) component).getViewport(), tables);
            }
            else if (component instanceof java.awt.Container) {
                collect((java.awt.Container) component, tables);
            }
        }
    }
}
