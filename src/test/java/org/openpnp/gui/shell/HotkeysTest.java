package org.openpnp.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.table.DefaultTableModel;

import org.junit.jupiter.api.Test;
import org.openpnp.util.UiUtils;

/** The window-wide hotkeys: none twice, none on a standard command, and not while typing. */
public class HotkeysTest {
    @Test
    public void noKeyIsBoundTwice() {
        Map<KeyStroke, String> seen = new HashMap<>();
        for (Hotkeys.Entry entry : Hotkeys.all()) {
            String before = seen.put(entry.keyStroke, entry.descriptionKey);
            assertTrue(before == null || before.equals(entry.descriptionKey),
                    Hotkeys.describe(entry.keyStroke) + " is bound to " + before + " and " + entry.descriptionKey);
        }
    }

    /**
     * The global dispatcher sees a key before the menu does. Safe Z on Ctrl+Shift+Z took Redo, and
     * a job step on Ctrl+Shift+S took the key a hand reaches for to save as.
     */
    @Test
    public void noHotkeyTakesAStandardCommand() {
        for (Hotkeys.Entry entry : Hotkeys.all()) {
            assertFalse(Hotkeys.RESERVED.contains(entry.keyStroke),
                    Hotkeys.describe(entry.keyStroke) + " is a standard command");
        }
        int ctrlShift = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;
        assertFalse(Hotkeys.SAFE_Z.equals(KeyStroke.getKeyStroke(KeyEvent.VK_Z, ctrlShift)));
        assertFalse(Hotkeys.JOB_STEP.equals(KeyStroke.getKeyStroke(KeyEvent.VK_S, ctrlShift)));
    }

    @Test
    public void theStopKeyIsListedFirstAndDescribedAsAKeycapWould() {
        assertEquals(Hotkeys.STOP_MACHINE, Hotkeys.all().get(0).keyStroke);
        assertEquals("Ctrl+Shift+X", Hotkeys.describe(Hotkeys.STOP_MACHINE));
        assertEquals("Ctrl+\u2191", Hotkeys.describe(
                KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK)));
    }

    /**
     * A table whose cell editing was started by typing keeps the focus itself, so Ctrl+Left used
     * to jog the machine instead of moving the caret a word.
     */
    @Test
    public void anEditingTableIsTextInputAndAnIdleOneIsNot() {
        JTable table = new JTable(new DefaultTableModel(new Object[][] { { "a" } }, new Object[] { "x" }));
        assertFalse(UiUtils.isTextInput(table));
        assertTrue(table.editCellAt(0, 0));
        assertTrue(UiUtils.isTextInput(table));
        table.getCellEditor().cancelCellEditing();
        assertFalse(UiUtils.isTextInput(table));
        assertTrue(UiUtils.isTextInput(new JTextField()));
        assertFalse(UiUtils.isTextInput(null));
    }
}
