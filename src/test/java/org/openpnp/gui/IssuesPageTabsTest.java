package org.openpnp.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.solutions.MachineDiagnostics.TestGroup;
import org.openpnp.model.Configuration;

/**
 * The issues page's two tabs that were the diagnostics page: the overview describes the default
 * machine in every cell without being shown, and every measurement group has a parameter form
 * whose fields name real properties of the diagnostics.
 */
public class IssuesPageTabsTest {
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
        MeasurementsPanel measurements = new MeasurementsPanel(machine, new JPanel(), () -> {
        });
        for (TestGroup group : TestGroup.values()) {
            FormWizard form = measurements.form(group);
            assertNotNull(form, group.name());
            // Built means every field named a readable and writable property of the diagnostics.
            Form.properties(form);
        }
        assertFalse(MeasurementsPanel.calibrationGroups().isEmpty(), "the calibration decides by no measurement");
        assertFalse(MeasurementsPanel.calibrationGroups().contains(TestGroup.ConfigSnapshot),
                "the snapshot decides no calibration step");
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
