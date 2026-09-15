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
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import org.openpnp.ConfigurationListener;
import org.openpnp.Main;
import org.openpnp.Translations;
import org.openpnp.gui.shell.Chip.Shape;
import org.openpnp.gui.shell.Chip.Tone;
import org.openpnp.model.Configuration;

/**
 * The line along the bottom: what the machine is doing, and how far the job has got.
 * <p>
 * The stylesheet's {@code .statusbar}: 28 pixels, 12 pixel secondary text, a status pill on the
 * left followed by the current action, and on the right the totals, the units and the version.
 * The units and the version are the two facts a bug report most often lacks.
 */
@SuppressWarnings("serial")
public class StatusBarPanel extends JPanel {
    public static final int HEIGHT = 28;

    private final Chip statePill = new Chip(
            Translations.getString("StatusBar.State.Idle"), Tone.Pending, Shape.Status); //$NON-NLS-1$
    private final JLabel statusLabel = Ui.t2(" "); //$NON-NLS-1$
    private final JLabel lastPlacementLabel = Ui.muted(""); //$NON-NLS-1$
    private final JComponent lastPlacementSep = Ui.divider(14);
    private final JLabel totalLabel = Ui.mono("0 / 0", 12f); //$NON-NLS-1$
    private final JLabel boardLabel = Ui.mono("0 / 0", 12f); //$NON-NLS-1$
    private final JLabel remainingLabel = Ui.mono("", 12f); //$NON-NLS-1$
    private final JComponent remainingItem;
    private final JLabel unitsLabel = Ui.muted(""); //$NON-NLS-1$
    private final JLabel versionLabel = Ui.muted("v" + Main.getVersionString()); //$NON-NLS-1$

    public StatusBarPanel(Configuration configuration) {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setOpaque(true);
        setBackground(Ui.surface());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.border()),
                new EmptyBorder(0, 14, 0, 14)));

        statusLabel.setFont(Ui.font(12f));
        lastPlacementLabel.setFont(Ui.font(12f));
        unitsLabel.setFont(Ui.font(12f));
        versionLabel.setFont(Ui.font(12f));

        add(item(6, statePill, statusLabel));
        add(Box.createHorizontalStrut(14));
        add(lastPlacementSep);
        add(Box.createHorizontalStrut(14));
        add(lastPlacementLabel);
        lastPlacementSep.setVisible(false);
        lastPlacementLabel.setVisible(false);
        add(Box.createHorizontalGlue());
        add(item(6, Ui.t2(Translations.getString("StatusBar.TotalProgress")), bold(totalLabel))); //$NON-NLS-1$
        add(Box.createHorizontalStrut(14));
        add(item(6, Ui.t2(Translations.getString("StatusBar.CurrentBoard")), bold(boardLabel))); //$NON-NLS-1$
        add(Box.createHorizontalStrut(14));
        remainingItem = item(6, Ui.t2(Translations.getString("StatusBar.Remaining")), bold(remainingLabel)); //$NON-NLS-1$
        remainingItem.setVisible(false);
        add(remainingItem);
        add(Box.createHorizontalStrut(14));
        add(Ui.divider(14));
        add(Box.createHorizontalStrut(14));
        add(unitsLabel);
        add(Box.createHorizontalStrut(14));
        add(versionLabel);

        for (Component c : getComponents()) {
            if (c instanceof JComponent) {
                ((JComponent) c).setAlignmentY(CENTER_ALIGNMENT);
            }
        }

        // The units are a user preference that the settings can change while running.
        configuration.addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationComplete(Configuration configuration) throws Exception {
                unitsLabel.setText(configuration.getSystemUnits().getShortName());
            }
        });
    }

    private static JLabel bold(JLabel label) {
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private static JComponent item(int gap, JComponent... parts) {
        JPanel item = new JPanel();
        item.setOpaque(false);
        item.setLayout(new BoxLayout(item, BoxLayout.X_AXIS));
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                item.add(Box.createHorizontalStrut(gap));
            }
            parts[i].setAlignmentY(CENTER_ALIGNMENT);
            item.add(parts[i]);
        }
        item.setMaximumSize(item.getPreferredSize());
        return item;
    }

    /** What the machine is doing right now, in words. */
    public void setStatus(String status) {
        statusLabel.setText(status == null || status.isEmpty() ? " " : status); //$NON-NLS-1$
    }

    /**
     * The pill before the status text: running, in a wizard, or idle.
     * 
     * @param text The word in the pill.
     * @param tone Run while a job runs or a wizard is up, Pending when idle, Err on a fault.
     */
    public void setState(String text, Tone tone) {
        statePill.setText(text);
        statePill.setTone(tone);
        revalidate();
    }

    /** Placements done, on the job and on the current board. */
    public void setProgress(int done, int total, int boardDone, int boardTotal) {
        totalLabel.setText(done + " / " + total); //$NON-NLS-1$
        boardLabel.setText(boardDone + " / " + boardTotal); //$NON-NLS-1$
        revalidateItems();
    }

    /** The previous placement and how long it took, or null to show nothing. */
    public void setLastPlacement(String text) {
        boolean shown = text != null && !text.isEmpty();
        lastPlacementLabel.setText(shown ? text : ""); //$NON-NLS-1$
        lastPlacementSep.setVisible(shown);
        lastPlacementLabel.setVisible(shown);
        revalidateItems();
    }

    /** The estimated time left on the job, or null to show nothing. */
    public void setRemaining(String text) {
        boolean shown = text != null && !text.isEmpty();
        remainingLabel.setText(shown ? text : ""); //$NON-NLS-1$
        remainingItem.setVisible(shown);
        revalidateItems();
    }

    private void revalidateItems() {
        for (Component c : getComponents()) {
            if (c instanceof JPanel) {
                ((JPanel) c).setMaximumSize(c.getPreferredSize());
            }
        }
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.height = HEIGHT;
        return size;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // Colours are read from the theme, so they are stale after a look and feel change. Null
        // while the superclass constructor is still running.
        if (statusLabel != null) {
            setBackground(Ui.surface());
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.border()),
                    new EmptyBorder(0, 14, 0, 14)));
            statusLabel.setForeground(Ui.text2());
            for (JLabel muted : new JLabel[] { lastPlacementLabel, unitsLabel, versionLabel }) {
                muted.setForeground(Ui.muted());
            }
        }
    }
}
