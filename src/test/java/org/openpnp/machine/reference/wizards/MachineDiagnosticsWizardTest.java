package org.openpnp.machine.reference.wizards;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.model.Configuration;

/**
 * Builds the diagnostics page against the default machine and reads every cell it produces.
 * <p>
 * The read-only sections ask each axis, driver, camera, nozzle and nozzle tip for a dozen
 * properties and assemble sentences from them. Without this, the first thing to find a getter
 * that returns null, or a phrase whose placeholders do not match its arguments, would be a user
 * with the page open in front of them.
 */
public class MachineDiagnosticsWizardTest {
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
    public void thePageDescribesTheMachineWithoutBeingShown() {
        MachineDiagnosticsWizard wizard = new MachineDiagnosticsWizard(machine);

        wizard.addNotify();

        List<String> cells = new ArrayList<>();
        for (JTable table : tablesIn(wizard)) {
            for (int row = 0; row < table.getRowCount(); row++) {
                for (int column = 0; column < table.getColumnCount(); column++) {
                    Object value = table.getValueAt(row, column);
                    cells.add(String.valueOf(value));
                }
                // The renderers are what a user actually reads, and a renderer that cannot take
                // what the model hands it throws where nobody is watching.
                for (int column = 0; column < table.getColumnCount(); column++) {
                    table.prepareRenderer(table.getCellRenderer(row, column), row, column);
                }
            }
        }

        assertFalse(cells.isEmpty(), "the page described nothing at all");
        assertFalse(cells.contains("null"), "a cell reads \"null\": " + cells);
        for (String cell : cells) {
            assertFalse(cell.startsWith("!") && cell.endsWith("!"),
                    "a cell shows a missing translation key: " + cell);
        }
        assertTrue(cells.contains("Top"), "the default machine's camera was not described");
        assertTrue(cells.contains("N1"), "the default machine's nozzle was not described");
        assertTrue(cells.contains("Primary calibration fiducial on H1"),
                "a calibration row did not get the element's name put into it");
        assertTrue(cells.contains("never measured"),
                "the calibration status did not say that nothing has been measured yet");
    }

    /** Every table on the page, wherever it sits in the panels the page is built from. */
    private static List<JTable> tablesIn(JPanel panel) {
        List<JTable> tables = new ArrayList<>();
        collectTables(panel, tables);
        return tables;
    }

    private static void collectTables(java.awt.Container container, List<JTable> tables) {
        for (java.awt.Component component : container.getComponents()) {
            if (component instanceof JTable) {
                tables.add((JTable) component);
            }
            else if (component instanceof JScrollPane) {
                collectTables(((JScrollPane) component).getViewport(), tables);
            }
            else if (component instanceof java.awt.Container) {
                collectTables((java.awt.Container) component, tables);
            }
        }
    }
}
