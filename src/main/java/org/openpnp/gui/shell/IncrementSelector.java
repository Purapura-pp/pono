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

import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.ButtonGroup;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * How far one press of a jog button moves the machine, as five buttons showing the five distances.
 * <p>
 * This was a vertical slider with five ticks, which took a column of the machine controls as tall
 * as the jog buttons themselves and said what it was set to only by which tick the handle was
 * nearest. The distance is the one setting on this card that is read as often as it is changed.
 * <p>
 * The levels are numbered from one, which is the coarsest-to-finest order the jog increment
 * arithmetic, the increment actions and the stored preference all already use.
 */
@SuppressWarnings("serial")
public class IncrementSelector extends JPanel {
    public static final int LEVELS = 5;

    private final JToggleButton[] buttons = new JToggleButton[LEVELS];

    private int level = 1;

    /** Set while a level is being applied, so the buttons' own events are not taken as input. */
    private boolean applying;

    public IncrementSelector() {
        // The stylesheet's .seg: 3 pixel padding, 2 pixel gaps, 24 pixel segments.
        setLayout(new GridLayout(1, LEVELS, 2, 0));
        setOpaque(false);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 3, 3, 3));
        ButtonGroup group = new ButtonGroup();
        for (int index = 0; index < LEVELS; index++) {
            final int buttonLevel = index + 1;
            JToggleButton button = new JToggleButton();
            Ui.seg(button);
            button.addActionListener(e -> setLevel(buttonLevel));
            group.add(button);
            add(button);
            buttons[index] = button;
        }
        buttons[0].setSelected(true);

        // The slider this replaces took the arrow keys, and it is where the machine controls put
        // the focus so that a stray space bar cannot reach a jog button.
        InputMap keys = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "coarser"); //$NON-NLS-1$
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "coarser"); //$NON-NLS-1$
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "finer"); //$NON-NLS-1$
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "finer"); //$NON-NLS-1$
        getActionMap().put("coarser", new AbstractAction() { //$NON-NLS-1$
            @Override
            public void actionPerformed(ActionEvent e) {
                setLevel(level - 1);
                getFocusTarget().requestFocusInWindow();
            }
        });
        getActionMap().put("finer", new AbstractAction() { //$NON-NLS-1$
            @Override
            public void actionPerformed(ActionEvent e) {
                setLevel(level + 1);
                getFocusTarget().requestFocusInWindow();
            }
        });
    }

    /**
     * The button standing for the level in force, which is where focus belongs: the panel around
     * it holds the key bindings but cannot take focus itself.
     */
    public Component getFocusTarget() {
        return buttons[level - 1];
    }

    /**
     * @param labels One distance per level, coarsest last, as the unit in force writes them.
     */
    public void setLabels(String[] labels) {
        if (labels.length != LEVELS) {
            throw new IllegalArgumentException(
                    "An increment selector has " + LEVELS + " levels, got " + labels.length);
        }
        for (int index = 0; index < LEVELS; index++) {
            buttons[index].setText(labels[index]);
        }
    }

    public int getLevel() {
        return level;
    }

    /**
     * Select a level. Out of range values are clamped rather than refused, because this is what
     * the raise and lower actions call when they are already at an end.
     */
    public void setLevel(int level) {
        int wanted = Math.max(1, Math.min(LEVELS, level));
        buttons[wanted - 1].setSelected(true);
        if (wanted == this.level || applying) {
            return;
        }
        this.level = wanted;
        applying = true;
        try {
            ChangeEvent event = new ChangeEvent(this);
            for (ChangeListener listener : listenerList.getListeners(ChangeListener.class)) {
                listener.stateChanged(event);
            }
        }
        finally {
            applying = false;
        }
    }

    public void addChangeListener(ChangeListener listener) {
        listenerList.add(ChangeListener.class, listener);
    }

    public void removeChangeListener(ChangeListener listener) {
        listenerList.remove(ChangeListener.class, listener);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (JToggleButton button : buttons) {
            button.setEnabled(enabled);
        }
    }

    @Override
    protected void paintComponent(java.awt.Graphics g) {
        java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
        try {
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Ui.surface2());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            g2.setColor(Ui.border());
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
        }
        finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
