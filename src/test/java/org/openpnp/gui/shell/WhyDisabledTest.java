package org.openpnp.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;

import org.junit.jupiter.api.Test;

/** A greyed control says why under what it does, and an enabled one says only what it does. */
public class WhyDisabledTest {
    @Test
    public void theReasonShowsOnlyWhileGreyed() {
        JButton button = Ui.whyDisabled(new Ui.Button("Step", null), () -> "Pause first");
        button.setToolTipText("One placement");
        assertEquals("One placement", button.getToolTipText());
        button.setEnabled(false);
        String tip = button.getToolTipText();
        assertTrue(tip.startsWith("<html>One placement<br>"), tip);
        assertTrue(tip.contains("Pause first"), tip);
    }

    @Test
    public void theActionBehindAButtonCanGiveTheReason() {
        Action action = new AbstractAction("Stop") {
            @Override
            public void actionPerformed(ActionEvent e) {
            }
        };
        action.putValue(Ui.WHY_DISABLED, "No job is running");
        action.setEnabled(false);
        JButton button = new Ui.Button(action);
        assertTrue(button.getToolTipText().contains("No job is running"), button.getToolTipText());
        action.putValue(Ui.WHY_DISABLED, null);
        assertNull(button.getToolTipText(), "no reason and no tooltip of its own");
    }

    @Test
    public void aReasonIsEscapedAndATooltipsOwnHtmlIsNotNested() {
        JButton button = Ui.whyDisabled(new Ui.Button("x", null), () -> "a < b");
        button.setToolTipText("<html><b>Park</b></html>");
        button.setEnabled(false);
        String tip = button.getToolTipText();
        assertEquals(1, tip.split("<html>", -1).length - 1, tip);
        assertTrue(tip.contains("a &lt; b"), tip);
        assertTrue(tip.contains("<b>Park</b>"), tip);
    }

    @Test
    public void withoutAMachineTheReasonIsThatItIsOff() {
        // Before the configuration is loaded there is no machine: it counts as not enabled.
        assertEquals(org.openpnp.Translations.getString("Ui.Disabled.MachineOff"), Ui.machineReason(null));
    }
}
