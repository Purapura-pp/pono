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

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import org.openpnp.ConfigurationListener;
import org.openpnp.Main;
import org.openpnp.Translations;
import org.openpnp.model.Configuration;

/**
 * The strip along the bottom: what the machine is doing, how far the job has got, the units the
 * numbers are in, the version, and the axis readouts.
 * <p>
 * The three pieces it replaces each sat in a lowered bevel border, which drew three boxes around
 * text that is only ever read, and the units and version were nowhere in the window at all - so a
 * coordinate could not be interpreted without opening the settings, and a bug report could not say
 * which build it came from.
 */
@SuppressWarnings("serial")
public class StatusBarPanel extends JPanel {
    private final JLabel statusLabel = new JLabel(" "); //$NON-NLS-1$
    private final JLabel placementsLabel = new JLabel(
            Translations.getString("MainFrame.StatusPanel.PlacementsLabel.initial.text")); //$NON-NLS-1$
    private final JLabel unitsLabel = new JLabel();
    private final JLabel versionLabel = new JLabel(Main.getVersionString());
    private final DroPanel droPanel;

    public StatusBarPanel(Configuration configuration) {
        droPanel = new DroPanel(configuration);
        setLayout(new GridBagLayout());
        setBorder(new EmptyBorder(new Insets(2, 2, 0, 2)));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = 0;
        gc.anchor = GridBagConstraints.WEST;
        gc.insets = new Insets(0, 0, 0, 14);

        gc.gridx = 0;
        gc.weightx = 1;
        add(statusLabel, gc);

        gc.weightx = 0;
        gc.anchor = GridBagConstraints.EAST;
        gc.gridx = 1;
        add(placementsLabel, gc);
        gc.gridx = 2;
        add(unitsLabel, gc);
        gc.gridx = 3;
        add(versionLabel, gc);
        gc.gridx = 4;
        gc.insets = new Insets(0, 0, 0, 0);
        add(droPanel, gc);

        // The units are a user preference that the settings can change while running.
        configuration.addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationComplete(Configuration configuration) throws Exception {
                unitsLabel.setText(configuration.getSystemUnits().getShortName());
            }
        });

        applyColors();
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
    }

    public void setPlacements(String text) {
        placementsLabel.setText(text);
    }

    public DroPanel getDroPanel() {
        return droPanel;
    }

    /** Everything here is supporting detail, so none of it competes with the window's content. */
    private void applyColors() {
        Color secondary = UIManager.getColor("Pono.textSecondary"); //$NON-NLS-1$
        Color muted = UIManager.getColor("Pono.textMuted"); //$NON-NLS-1$
        if (secondary == null) {
            secondary = UIManager.getColor("Label.disabledForeground"); //$NON-NLS-1$
        }
        if (muted == null) {
            muted = secondary;
        }
        if (secondary != null) {
            statusLabel.setForeground(secondary);
            placementsLabel.setForeground(secondary);
        }
        if (muted != null) {
            unitsLabel.setForeground(muted);
            versionLabel.setForeground(muted);
        }
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // Null while the superclass constructor is still running.
        if (statusLabel != null) {
            applyColors();
        }
    }
}
