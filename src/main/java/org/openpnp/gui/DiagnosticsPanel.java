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

package org.openpnp.gui;

import java.awt.BorderLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.openpnp.ConfigurationListener;
import org.openpnp.Translations;
import org.openpnp.gui.shell.RoundedPanel;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.wizards.MachineDiagnosticsWizard;
import org.openpnp.model.Configuration;
import org.openpnp.spi.Machine;

/**
 * The machine diagnostics as a page of their own.
 * <p>
 * They were a property sheet of the machine node, which put three tables of measurements in the
 * 500 pixel properties column. The diagnostics are something the operator goes to, not a property
 * of the machine, and the tables were laid out for the width of the window.
 */
@SuppressWarnings("serial")
public class DiagnosticsPanel extends JPanel implements WizardContainer {
    private MachineDiagnosticsWizard wizard;

    public DiagnosticsPanel(Configuration configuration) {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(0, 10, 10, 10));
        RoundedPanel card = new RoundedPanel(14, Ui::surface, Ui::border);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(6, 6, 6, 6));
        add(card, BorderLayout.CENTER);

        // The window is built before the configuration is loaded, so the machine is not there
        // yet: the wizard is made once it is.
        JLabel none = new JLabel(Translations.getString("DiagnosticsPanel.NotAvailable"), //$NON-NLS-1$
                SwingConstants.CENTER);
        none.setForeground(Ui.muted());
        card.add(none, BorderLayout.CENTER);
        configuration.addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationComplete(Configuration configuration) throws Exception {
                Machine machine = configuration.getMachine();
                if (!(machine instanceof ReferenceMachine)) {
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    wizard = new MachineDiagnosticsWizard((ReferenceMachine) machine);
                    wizard.setWizardContainer(DiagnosticsPanel.this);
                    wizard.setOpaque(false);
                    card.removeAll();
                    card.add(wizard, BorderLayout.CENTER);
                    card.revalidate();
                    card.repaint();
                });
            }
        });
    }

    public MachineDiagnosticsWizard getWizard() {
        return wizard;
    }

    @Override
    public void wizardCompleted(Wizard wizard) {
    }

    @Override
    public void wizardCancelled(Wizard wizard) {
    }
}
