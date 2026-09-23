package org.openpnp.gui.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.ActionEvent;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import org.junit.jupiter.api.Test;
import org.openpnp.Translations;
import org.openpnp.gui.components.AutoSelectTextTable;

/** Enter edits, Delete deletes, and a refused value says why. */
public class TableKeysTest {
    private static JTable table(boolean firstColumnEditable) {
        DefaultTableModel model = new DefaultTableModel(new Object[][] { { "a", "b" }, { "c", "d" } },
                new Object[] { "x", "y" }) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1 || firstColumnEditable;
            }
        };
        return new AutoSelectTextTable(model);
    }

    private static void press(JTable table, String name) {
        table.getActionMap().get(name).actionPerformed(new ActionEvent(table, ActionEvent.ACTION_PERFORMED, null));
    }

    @Test
    public void enterEditsTheSelectedCellOrTheFirstEditableOneOfTheRow() {
        JTable table = table(false);
        TableUtils.bindKeys(table, null);
        table.changeSelection(1, 0, false, false);
        press(table, "pono.edit");
        assertTrue(table.isEditing());
        assertEquals(1, table.getEditingRow());
        assertEquals(1, table.getEditingColumn(), "the first column cannot be edited, so the second is");
        table.getCellEditor().cancelCellEditing();

        JTable both = table(true);
        TableUtils.bindKeys(both, null);
        both.changeSelection(0, 0, false, false);
        press(both, "pono.edit");
        assertEquals(0, both.getEditingColumn());
    }

    @Test
    public void deleteRunsThePagesDeleteOnlyWithASelectionAndNotWhileEditing() {
        AtomicInteger deleted = new AtomicInteger();
        Action delete = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleted.incrementAndGet();
            }
        };
        JTable table = table(true);
        TableUtils.bindKeys(table, delete);
        press(table, "pono.delete");
        assertEquals(0, deleted.get(), "nothing selected");
        table.changeSelection(0, 0, false, false);
        press(table, "pono.delete");
        assertEquals(1, deleted.get());
        delete.setEnabled(false);
        press(table, "pono.delete");
        assertEquals(1, deleted.get(), "the page's delete is greyed");
        delete.setEnabled(true);
        table.editCellAt(0, 1);
        press(table, "pono.delete");
        assertEquals(1, deleted.get(), "Delete in a cell being edited is text");
        assertFalse(table.getInputMap(JTable.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DELETE, 0)) == null);
    }

    @Test
    public void aRefusedNumberSaysSoAndOtherRefusalsGiveTheirReason() {
        assertEquals(String.format(Translations.getString("Table.InvalidValue.Number"), "abc"),
                TableUtils.rejection("abc", new NumberFormatException("For input string: \"abc\"")));
        String other = TableUtils.rejection("x", new IllegalArgumentException("Too big"));
        assertTrue(other.contains("Too big"), other);
        String bare = TableUtils.rejection("x", new IllegalStateException());
        assertTrue(bare.contains("IllegalStateException"), bare);
    }
}
