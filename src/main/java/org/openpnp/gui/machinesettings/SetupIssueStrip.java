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

package org.openpnp.gui.machinesettings;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

import org.openpnp.Translations;
import org.openpnp.gui.calibration.Banner;
import org.openpnp.gui.calibration.IssueControls;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Ui;
import org.openpnp.machine.reference.calibration.CalibrationPlan;
import org.openpnp.model.Solutions;

/**
 * The setup issues of the topic on show, over its settings: what is wrong and what to do about
 * it, with Accept where the issue makes the change itself, and what it lets be set first behind
 * Details. What was dismissed is one line that shows it again.
 */
@SuppressWarnings("serial")
final class SetupIssueStrip extends JPanel {
    private final MachineSettingsPanel page;

    SetupIssueStrip(MachineSettingsPanel page) {
        this.page = page;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(10, 18, 2, 18));
        setVisible(false);
    }

    void show(List<Solutions.Issue> open, List<Solutions.Issue> dismissed) {
        removeAll();
        for (Solutions.Issue issue : open) {
            if (getComponentCount() > 0) {
                add(Box.createVerticalStrut(8));
            }
            add(MachineSettingsPanel.capped(card(issue)));
        }
        if (!dismissed.isEmpty()) {
            if (getComponentCount() > 0) {
                add(Box.createVerticalStrut(6));
            }
            add(MachineSettingsPanel.capped(dismissedLine(dismissed)));
        }
        setVisible(getComponentCount() > 0);
        revalidate();
        repaint();
    }

    private JComponent card(Solutions.Issue issue) {
        Banner banner = new Banner();
        banner.setTone(issue.getSeverity().ordinal() >= Solutions.Severity.Warning.ordinal() ? Banner.Tone.Warn
                : Banner.Tone.Info);
        banner.setTitle(issue.getIssue());
        banner.setText(CalibrationPlan.nameOf(issue.getSubject()) + " \u00b7 " + issue.getSolution()); //$NON-NLS-1$
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setOpaque(false);
        card.add(banner, BorderLayout.NORTH);
        List<JButton> buttons = new ArrayList<>();
        String notes = IssueControls.description(issue);
        if (notes != null || IssueControls.hasControls(issue)) {
            JPanel details = new JPanel();
            details.setOpaque(false);
            details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
            details.setBorder(new EmptyBorder(2, 12, 4, 12));
            details.setVisible(false);
            JButton more = Ui.button(Translations.getString("MachineSettings.Issues.Details"), //$NON-NLS-1$
                    Ui.iconSm("chevright"), Ui.Size.Sm, Ui.Variant.Ghost); //$NON-NLS-1$
            more.addActionListener(e -> {
                // Built when first opened: showing an issue's settings activates the issue.
                if (details.getComponentCount() == 0) {
                    if (notes != null) {
                        JTextArea paragraph = Forms.paragraph(notes);
                        paragraph.setAlignmentX(Component.LEFT_ALIGNMENT);
                        details.add(paragraph);
                    }
                    JComponent controls = IssueControls.of(issue, page.getConfiguration());
                    if (controls != null) {
                        if (notes != null) {
                            details.add(Box.createVerticalStrut(10));
                        }
                        controls.setAlignmentX(Component.LEFT_ALIGNMENT);
                        details.add(controls);
                    }
                }
                details.setVisible(!details.isVisible());
                more.setIcon(Ui.iconSm(details.isVisible() ? "chevdown" : "chevright")); //$NON-NLS-1$ //$NON-NLS-2$
                revalidate();
                repaint();
            });
            buttons.add(more);
            card.add(details, BorderLayout.CENTER);
        }
        String uri = issue.getUri();
        if (uri != null && !uri.isEmpty()) {
            JButton wiki = Ui.button(Translations.getString("MachineSettings.Issues.Wiki"), Ui.iconSm("book"), //$NON-NLS-1$ //$NON-NLS-2$
                    Ui.Size.Sm, Ui.Variant.Ghost);
            wiki.setToolTipText(uri);
            wiki.addActionListener(e -> org.openpnp.util.UiUtils.browseUri(uri));
            buttons.add(wiki);
        }
        JButton dismiss = Ui.button(Translations.getString("MachineSettings.Issues.Dismiss"), null, //$NON-NLS-1$
                Ui.Size.Sm, Ui.Variant.Default);
        dismiss.addActionListener(e -> page.setIssueState(List.of(issue), Solutions.State.Dismissed));
        buttons.add(dismiss);
        if (issue.canBeAccepted()) {
            JButton accept = Ui.button(Translations.getString("MachineSettings.Issues.Accept"), null, //$NON-NLS-1$
                    Ui.Size.Sm, Ui.Variant.Primary);
            accept.setToolTipText(Translations.getString("MachineSettings.Issues.Accept.ToolTip")); //$NON-NLS-1$
            accept.addActionListener(e -> page.acceptIssue(issue));
            buttons.add(accept);
        }
        banner.setActions(buttons.toArray(new JButton[0]));
        return card;
    }

    private JComponent dismissedLine(List<Solutions.Issue> dismissed) {
        JLabel text = Ui.muted(String.format(Translations.getString("MachineSettings.Issues.Dismissed"), //$NON-NLS-1$
                dismissed.size()));
        JButton restore = Ui.button(Translations.getString("MachineSettings.Issues.Restore"), null, //$NON-NLS-1$
                Ui.Size.Xs, Ui.Variant.Ghost);
        restore.addActionListener(e -> page.setIssueState(new ArrayList<>(dismissed), Solutions.State.Open));
        JPanel row = Forms.row(text, restore);
        row.add(Box.createHorizontalGlue());
        return row;
    }
}
