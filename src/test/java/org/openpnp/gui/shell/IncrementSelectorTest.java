/*
 * Copyright (C) 2026 Pono contributors
 * 
 * This file is part of Pono, a modified version of OpenPnP.
 * 
 * Pono is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * Pono is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Pono. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package org.openpnp.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JToggleButton;

import org.junit.jupiter.api.Test;

/**
 * The five jog distances, which decide how far the machine moves when a jog button is pressed.
 * <p>
 * This replaced a five-tick slider, and the level it reports is fed straight into the distance
 * arithmetic and into the stored preference. An off-by-one here is a nozzle moving ten millimetres
 * where a tenth was asked for, so the clamping and the change notification are worth pinning down
 * away from a machine.
 */
public class IncrementSelectorTest {
    private List<AbstractButton> buttonsOf(IncrementSelector selector) {
        List<AbstractButton> buttons = new ArrayList<>();
        for (Component child : selector.getComponents()) {
            buttons.add((JToggleButton) child);
        }
        return buttons;
    }

    @Test
    public void itStartsAtTheFinestLevelWithThatButtonSelected() {
        IncrementSelector selector = new IncrementSelector();

        assertEquals(1, selector.getLevel());
        assertTrue(buttonsOf(selector).get(0).isSelected());
        assertEquals(IncrementSelector.LEVELS, buttonsOf(selector).size());
    }

    @Test
    public void selectingALevelMovesTheSelectionWithIt() {
        IncrementSelector selector = new IncrementSelector();

        selector.setLevel(4);

        assertEquals(4, selector.getLevel());
        assertTrue(buttonsOf(selector).get(3).isSelected());
        assertFalse(buttonsOf(selector).get(0).isSelected());
    }

    @Test
    public void pressingAButtonIsTheSameAsSelectingItsLevel() {
        IncrementSelector selector = new IncrementSelector();
        List<Integer> seen = new ArrayList<>();
        selector.addChangeListener(e -> seen.add(selector.getLevel()));

        buttonsOf(selector).get(2).doClick();

        assertEquals(3, selector.getLevel());
        assertEquals(List.of(3), seen);
    }

    @Test
    public void aLevelPastEitherEndIsClampedRatherThanRefused() {
        IncrementSelector selector = new IncrementSelector();

        // This is what the raise and lower increment actions do when they are already at an end.
        selector.setLevel(0);
        assertEquals(1, selector.getLevel());
        selector.setLevel(-7);
        assertEquals(1, selector.getLevel());
        selector.setLevel(IncrementSelector.LEVELS + 1);
        assertEquals(IncrementSelector.LEVELS, selector.getLevel());
    }

    @Test
    public void aLevelThatIsAlreadyInForceIsNotReportedAsAChange() {
        IncrementSelector selector = new IncrementSelector();
        selector.setLevel(3);
        List<Integer> seen = new ArrayList<>();
        selector.addChangeListener(e -> seen.add(selector.getLevel()));

        selector.setLevel(3);
        // Clamped to the level it is already on, which is the same thing from the caller's side.
        selector.setLevel(3);

        assertTrue(seen.isEmpty(), "the distance was written back for no reason: " + seen);
    }

    @Test
    public void everyChangeIsReportedOnce() {
        IncrementSelector selector = new IncrementSelector();
        List<Integer> seen = new ArrayList<>();
        selector.addChangeListener(e -> seen.add(selector.getLevel()));

        selector.setLevel(2);
        selector.setLevel(5);
        buttonsOf(selector).get(0).doClick();

        assertEquals(List.of(2, 5, 1), seen);
    }

    @Test
    public void theLabelsAreTheDistancesTheUnitInForceWrites() {
        IncrementSelector selector = new IncrementSelector();

        selector.setLabels(new String[] {"0.001", "0.01", "0.1", "1.0", "10.0"});

        assertEquals("0.001", buttonsOf(selector).get(0).getText());
        // Written as the stylesheet writes a distance: 1 and 10, not 1.0 and 10.0.
        assertEquals("1", buttonsOf(selector).get(3).getText());
        assertEquals("10", buttonsOf(selector).get(4).getText());
    }

    @Test
    public void aWrongNumberOfLabelsIsRefusedRatherThanLeavingButtonsBlank() {
        IncrementSelector selector = new IncrementSelector();

        assertThrows(IllegalArgumentException.class,
                () -> selector.setLabels(new String[] {"1", "2", "3"}));
    }

    @Test
    public void focusBelongsToTheButtonForTheLevelInForce() {
        IncrementSelector selector = new IncrementSelector();

        selector.setLevel(5);

        // The machine controls put focus here so that a stray space bar cannot reach a jog button.
        assertSame(buttonsOf(selector).get(4), selector.getFocusTarget());
    }

    @Test
    public void disablingTheSelectorDisablesEveryLevel() {
        IncrementSelector selector = new IncrementSelector();

        selector.setEnabled(false);

        for (AbstractButton button : buttonsOf(selector)) {
            assertFalse(button.isEnabled());
        }
    }
}
