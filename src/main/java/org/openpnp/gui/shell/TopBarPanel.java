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

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;

import org.openpnp.Translations;
import org.openpnp.gui.JobPanel;
import org.openpnp.gui.MachineControlsPanel;
import org.openpnp.gui.components.ThemeDialog;
import org.openpnp.gui.components.ThemeInfo;
import org.openpnp.gui.components.ThemeSettingsPanel;
import org.openpnp.gui.theme.PonoThemes;
import org.openpnp.model.Configuration;

import com.formdev.flatlaf.FlatLaf;

/**
 * The strip across the top of the window: which job is open, what the machine is doing, and the
 * controls that start and stop it.
 * <p>
 * Those controls used to be three small icons inside the Job tab's toolbar, which meant they were
 * only reachable while that tab was in front, and the machine's state was a single power icon down
 * in the machine controls. Both belong to the window rather than to a tab, so they live here. The
 * Actions themselves are the ones the Job tab and the machine controls already own, so the menu
 * items and the keyboard shortcuts keep working unchanged.
 */
@SuppressWarnings("serial")
public class TopBarPanel extends JPanel {
    private static final int PROGRESS_WIDTH = 180;

    private final JLabel jobLabel = new JLabel();
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final Configuration configuration;
    private JButton themeButton;

    public TopBarPanel(Configuration configuration, JobPanel jobPanel,
            MachineControlsPanel machineControls) {
        this.configuration = configuration;
        // GridBagLayout, and every control added straight to this panel: the job controls are
        // nameless until a job is loaded, and a nested panel measured while they were still blank
        // reported a width that pushed the last of them off the right edge of the window.
        // Unweighted cells here always get their preferred width; only the job name absorbs slack.
        setLayout(new GridBagLayout());
        setBorder(new EmptyBorder(new Insets(2, 2, 6, 2)));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = 0;
        gc.insets = new Insets(0, 0, 0, 6);

        jobLabel.setText(jobPanel.getJobDisplayName());
        jobLabel.setToolTipText(Translations.getString("TopBar.Job.toolTipText")); //$NON-NLS-1$
        // JobPanel already recomputes this whenever the job's file or dirty flag changes, because
        // the window title needs it too.
        jobPanel.addPropertyChangeListener(JobPanel.PROPERTY_JOB_DISPLAY_NAME,
                e -> jobLabel.setText(String.valueOf(e.getNewValue())));
        gc.gridx = 0;
        gc.weightx = 1;
        gc.anchor = GridBagConstraints.WEST;
        add(jobLabel, gc);

        gc.weightx = 0;
        gc.anchor = GridBagConstraints.EAST;
        int gridx = 1;

        gc.gridx = gridx++;
        add(new MachineStateChip(configuration, machineControls.startStopMachineAction), gc);

        for (Action action : new Action[] { jobPanel.startPauseResumeJobAction,
                jobPanel.stepJobAction, jobPanel.stopJobAction }) {
            gc.gridx = gridx++;
            add(new JButton(action), gc);
            action.addPropertyChangeListener(e -> {
                revalidate();
                repaint();
            });
        }

        progressBar.setStringPainted(true);
        progressBar.setToolTipText(Translations.getString("TopBar.Progress.toolTipText")); //$NON-NLS-1$
        progressBar.setPreferredSize(new Dimension(PROGRESS_WIDTH, progressBar.getPreferredSize().height));
        gc.gridx = gridx++;
        add(progressBar, gc);

        themeButton = new JButton();
        themeButton.setToolTipText(Translations.getString("TopBar.Theme.toolTipText")); //$NON-NLS-1$
        themeButton.addActionListener(e -> toggleTheme());
        updateThemeButton();
        gc.gridx = gridx;
        gc.insets = new Insets(0, 0, 0, 0);
        add(themeButton, gc);
    }

    /** Percentage of the running job's placements that are done. */
    public void setProgress(int percent) {
        progressBar.setValue(percent);
    }

    /**
     * Switches between the two Pono themes and stores the choice, so the next start comes up the
     * same way. A user who picked a FlatLaf or system theme lands on a Pono one, which the tooltip
     * says; the appearance dialog is still there for the full list.
     */
    private void toggleTheme() {
        ThemeInfo theme = FlatLaf.isLafDark() ? PonoThemes.light() : PonoThemes.dark();
        new ThemeSettingsPanel().setTheme(theme, configuration.getFontSize(),
                configuration.isAlternateRows());
        configuration.setThemeInfo(theme);
        ThemeDialog.getInstance().setOldTheme(theme);
    }

    private void updateThemeButton() {
        themeButton.setText(Translations.getString(FlatLaf.isLafDark()
                ? "TopBar.Theme.ToLight" //$NON-NLS-1$
                : "TopBar.Theme.ToDark")); //$NON-NLS-1$
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // Runs again after every look and feel change, which is exactly when the button's label
        // has become wrong. Null while the superclass constructor is still running.
        if (themeButton != null) {
            updateThemeButton();
        }
    }
}
