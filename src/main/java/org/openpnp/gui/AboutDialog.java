/*
 * Copyright (C) 2011 Jason von Nieda <jason@vonnieda.org>
 * 
 * This file is part of OpenPnP.
 * 
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * For more information about OpenPnP visit http://openpnp.org
 */

package org.openpnp.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.URI;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.border.EmptyBorder;

import org.apache.commons.io.FileUtils;
import org.openpnp.Main;
import org.openpnp.Translations;
import org.openpnp.gui.components.MarkupTextPane;
import org.pmw.tinylog.Logger;

@SuppressWarnings("serial")
public class AboutDialog extends JDialog {

    private final JPanel contentPanel = new JPanel();
    private MarkupTextPane releaseNotes;
    private MarkupTextPane credits;

    public AboutDialog(Frame frame) {
        super(frame, true);
        createUi();

        try {
            String s = FileUtils.readFileToString(new File("CHANGES.md"));
            releaseNotes.setText(s);
            releaseNotes.setUri(new URI(Main.getSourceUri()+"CHANGES.md"));
        }
        catch (Exception e) {
            Logger.warn(e, "Failed to load CHANGES.md, release notes will be empty.");
        }
        try {
            String s = FileUtils.readFileToString(new File("SPONSORS.md"));
            credits.setText(s);
            credits.setUri(new URI(Main.getSourceUri()+"SPONSORS.md"));
        }
        catch (Exception e) {
            Logger.warn(e, "Failed to load SPONSORS.md, credits will be empty.");
        }
    }

    private void createUi() {
        setTitle(String.format(Translations.getString("AboutDialog.Title"), Main.NAME, Main.getVersionString())); //$NON-NLS-1$
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setBounds(100, 100, 347, 360);
        getContentPane().setLayout(new BorderLayout());
        JPanel buttonPane = new JPanel();
        buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
        getContentPane().add(buttonPane, BorderLayout.SOUTH);
        JButton okButton = new JButton(Translations.getString("AboutDialog.Ok")); //$NON-NLS-1$
        okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                setVisible(false);
            }
        });
        okButton.setActionCommand("OK");
        buttonPane.add(okButton);
        getRootPane().setDefaultButton(okButton);

        contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        getContentPane().add(contentPanel, BorderLayout.CENTER);
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        JLabel lblOpenpnp = new JLabel(Main.NAME);
        lblOpenpnp.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblOpenpnp.setFont(new Font("Lucida Grande", Font.BOLD, 32));
        contentPanel.add(lblOpenpnp);
        JLabel lblCopyright = new JLabel(Translations.getString("AboutDialog.Copyright")); //$NON-NLS-1$
        lblCopyright.setFont(new Font("Lucida Grande", Font.PLAIN, 10));
        lblCopyright.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(lblCopyright);
        // GPL v3 §5(a): a modified work must carry a prominent notice that it was modified, with a date.
        // Two short labels: the dialog is 347 px wide and a JLabel truncates rather than wraps.
        JLabel lblModified = new JLabel(Translations.getString("AboutDialog.Modified")); //$NON-NLS-1$
        lblModified.setFont(new Font("Lucida Grande", Font.PLAIN, 10));
        lblModified.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(lblModified);
        JLabel lblModifiedBy = new JLabel(Translations.getString("AboutDialog.ModifiedBy")); //$NON-NLS-1$
        lblModifiedBy.setFont(new Font("Lucida Grande", Font.PLAIN, 10));
        lblModifiedBy.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(lblModifiedBy);
        JLabel lblVersion = new JLabel(String.format(Translations.getString("AboutDialog.Version"), Main.getVersion())); //$NON-NLS-1$
        lblVersion.setFont(new Font("Lucida Grande", Font.PLAIN, 10));
        lblVersion.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(lblVersion);

        JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        contentPanel.add(tabbedPane);

        releaseNotes = new MarkupTextPane();
        tabbedPane.addTab(Translations.getString("AboutDialog.ReleaseNotes"), null, releaseNotes, null); //$NON-NLS-1$

        credits = new MarkupTextPane();
        tabbedPane.addTab(Translations.getString("AboutDialog.Credits"), null, credits, null); //$NON-NLS-1$
    }
}
