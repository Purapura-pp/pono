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

    /** The stylesheet's status bar status: 18 high, where a table's is 20. */
    private final Chip statePill = new Chip(
            Translations.getString("StatusBar.State.Idle"), Tone.Pending, Shape.Status).withHeight(18); //$NON-NLS-1$
    /** What was last asked for, shown again when the machine comes back. */
    private String stateText = Translations.getString("StatusBar.State.Idle"); //$NON-NLS-1$
    private Tone stateTone = Tone.Pending;
    private boolean machineEnabled;
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
        buildBusyBar();
        add(Box.createHorizontalStrut(8));
        add(busyBar);
        buildWizardLink();
        add(Box.createHorizontalStrut(10));
        add(wizardLink);
        add(Box.createHorizontalStrut(14));
        add(lastPlacementSep);
        add(Box.createHorizontalStrut(14));
        add(lastPlacementLabel);
        lastPlacementSep.setVisible(false);
        lastPlacementLabel.setVisible(false);
        add(Box.createHorizontalGlue());
        JComponent totalItem = item(6, Ui.t2(Translations.getString("StatusBar.TotalProgress")), bold(totalLabel)); //$NON-NLS-1$
        add(totalItem);
        add(Box.createHorizontalStrut(14));
        JComponent boardItem = item(6, Ui.t2(Translations.getString("StatusBar.CurrentBoard")), bold(boardLabel)); //$NON-NLS-1$
        add(boardItem);
        add(Box.createHorizontalStrut(14));
        remainingItem = item(6, Ui.t2(Translations.getString("StatusBar.Remaining")), bold(remainingLabel)); //$NON-NLS-1$
        remainingItem.setVisible(false);
        add(remainingItem);
        add(Box.createHorizontalStrut(14));
        JComponent unitsDivider = Ui.divider(14);
        add(unitsDivider);
        add(Box.createHorizontalStrut(14));
        add(unitsLabel);
        // Production mode's instead: who runs the machine and the time.
        operatorItem = item(4, Ui.muted(Translations.getString("StatusBar.Operator")), bold(operatorLabel)); //$NON-NLS-1$
        operatorItem.setVisible(false);
        add(operatorItem);
        JComponent operatorDivider = Ui.divider(14);
        operatorDivider.setVisible(false);
        add(Box.createHorizontalStrut(14));
        add(operatorDivider);
        add(Box.createHorizontalStrut(14));
        clockLabel.setFont(Ui.font(12f));
        clockLabel.setVisible(false);
        add(clockLabel);
        add(Box.createHorizontalStrut(14));
        add(versionLabel);
        workbenchOnly = new JComponent[] { totalItem, boardItem, unitsDivider, unitsLabel };
        operatorOnly = new JComponent[] { operatorItem, operatorDivider, clockLabel };

        for (Component c : getComponents()) {
            if (c instanceof JComponent) {
                ((JComponent) c).setAlignmentY(CENTER_ALIGNMENT);
            }
        }
        // On a narrow window a long status, "配置已保存 · 10:47:17", was cut at its edge: the
        // version gives way first.
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                fit();
            }
        });

        // The units are a user preference that the settings can change while running.
        configuration.addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationComplete(Configuration configuration) throws Exception {
                unitsLabel.setText(configuration.getSystemUnits().getShortName());
            }
        });
    }

    private final JLabel operatorLabel = Ui.t2(""); //$NON-NLS-1$
    private final JLabel clockLabel = Ui.muted(""); //$NON-NLS-1$
    private final JComponent operatorItem;
    private final JComponent[] workbenchOnly;
    private final JComponent[] operatorOnly;
    private final javax.swing.Timer clock = new javax.swing.Timer(10000, e -> showClock());
    private boolean operatorMode;

    /**
     * Production mode's status bar: what the machine is doing at the left, and at the right who
     * runs it and the time, where the job's totals were - the column beside the camera has them.
     */
    public void setOperatorMode(boolean on, String operator) {
        operatorMode = on;
        for (JComponent c : workbenchOnly) {
            c.setVisible(!on);
        }
        for (JComponent c : operatorOnly) {
            c.setVisible(on);
        }
        remainingItem.setVisible(!on && !remainingLabel.getText().isEmpty());
        lastPlacementSep.setVisible(!on && !lastPlacementLabel.getText().isEmpty());
        lastPlacementLabel.setVisible(!on && !lastPlacementLabel.getText().isEmpty());
        operatorLabel.setText(operator == null || operator.isEmpty()
                ? Translations.getString("StatusBar.Operator.None") : operator); //$NON-NLS-1$
        showClock();
        if (on) {
            clock.start();
        }
        else {
            clock.stop();
        }
        revalidate();
        repaint();
    }

    private void showClock() {
        clockLabel.setText(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date())); //$NON-NLS-1$
    }

    private static JLabel bold(JLabel label) {
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private static JComponent item(int gap, JComponent... parts) {
        // Never wider than what it holds, as that changes: fixed when it was made, the status
        // was a blank then, and "配置已保存 · 10:47:17" was cut at the blank's width.
        @SuppressWarnings("serial")
        JPanel item = new JPanel() {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        item.setOpaque(false);
        item.setLayout(new BoxLayout(item, BoxLayout.X_AXIS));
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                item.add(Box.createHorizontalStrut(gap));
            }
            parts[i].setAlignmentY(CENTER_ALIGNMENT);
            item.add(parts[i]);
        }
        return item;
    }

    private final JLabel wizardLink = new JLabel(Translations.getString("StatusBar.ShowWizard")); //$NON-NLS-1$
    private Runnable showWizard;

    /**
     * While a wizard is under way, a link after its step that brings its instructions back into
     * view; null when there is none.
     */
    public void setWizardLink(Runnable showWizard) {
        this.showWizard = showWizard;
        wizardLink.setVisible(showWizard != null);
        revalidateItems();
    }

    private void buildWizardLink() {
        wizardLink.setFont(Ui.font(12f));
        wizardLink.setForeground(Ui.accent());
        wizardLink.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        wizardLink.setVisible(false);
        wizardLink.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (showWizard != null) {
                    showWizard.run();
                }
            }
        });
    }

    /**
     * Runs while the machine is working on something outside a job - homing, a calibration, a
     * move - which used to show nothing but the busy cursor. A job has its own progress.
     */
    private final javax.swing.JProgressBar busyBar = new javax.swing.JProgressBar();
    private boolean busy;

    private void buildBusyBar() {
        busyBar.setIndeterminate(true);
        busyBar.setToolTipText(Translations.getString("StatusBar.Busy")); //$NON-NLS-1$
        Dimension size = new Dimension(64, 4);
        busyBar.setPreferredSize(size);
        busyBar.setMaximumSize(size);
        busyBar.setVisible(false);
    }

    /** Whether the machine is carrying out a task. */
    public void setBusy(boolean busy) {
        this.busy = busy;
        busyBar.setVisible(busy && stateTone == Tone.Pending);
        revalidateItems();
    }

    /** What the machine is doing right now, in words. */
    public void setStatus(String status) {
        statusLabel.setText(status == null || status.isEmpty() ? " " : status); //$NON-NLS-1$
        fit();
    }

    /** The version shown only while everything else fits beside it. */
    private void fit() {
        if (getWidth() <= 0) {
            return;
        }
        versionLabel.setVisible(true);
        if (getPreferredSize().width > getWidth()) {
            versionLabel.setVisible(false);
        }
        revalidate();
    }

    /**
     * The pill before the status text: running, in a wizard, or idle.
     * 
     * @param text The word in the pill.
     * @param tone Run while a job runs or a wizard is up, Pending when idle, Err on a fault.
     */
    public void setState(String text, Tone tone) {
        stateText = text;
        stateTone = tone;
        showState();
    }

    /**
     * Whether the machine is switched on. Idle with the machine off is not ready for anything:
     * the status says the machine is disconnected until it is on again.
     */
    public void setMachineEnabled(boolean enabled) {
        machineEnabled = enabled;
        showState();
    }

    private void showState() {
        boolean idle = stateTone == Tone.Pending;
        if (idle && !machineEnabled) {
            statePill.setText(Translations.getString("StatusBar.State.Disconnected")); //$NON-NLS-1$
            statePill.setTone(Tone.Neutral);
        }
        else {
            statePill.setText(stateText);
            statePill.setTone(stateTone);
        }
        busyBar.setVisible(busy && idle);
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
        lastPlacementSep.setVisible(shown && !operatorMode);
        lastPlacementLabel.setVisible(shown && !operatorMode);
        revalidateItems();
    }

    /** The estimated time left on the job, or null to show nothing. */
    public void setRemaining(String text) {
        boolean shown = text != null && !text.isEmpty();
        remainingLabel.setText(shown ? text : ""); //$NON-NLS-1$
        remainingItem.setVisible(shown && !operatorMode);
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
