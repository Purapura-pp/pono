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
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import org.openpnp.Main;
import org.openpnp.Translations;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.RoundedPanel;
import org.openpnp.gui.shell.Tokens;
import org.openpnp.gui.shell.Ui;

import com.formdev.flatlaf.FlatClientProperties;

/**
 * The first thing a new installation shows: what this is, and the three steps that get a machine
 * ready - choose the model and check the machine's settings, switch it on and home it, collect
 * and calibrate - each with the button that takes the step. The mockups' 20.
 * <p>
 * It used to be the upstream OpenPnP 2.0 release notes, read from a file in whatever the working
 * directory happened to be, under a title the translation lookup turned into "!欢迎使用 Pono ...!"
 * and an OK button and a copyright line in English.
 */
@SuppressWarnings("serial")
public class Welcome2_0Dialog extends JDialog {
    /** Whether the user asked not to see it again. */
    private final JCheckBox dontShow = Forms.check(Translations.getString("WelcomeDialog.DontShowAgain")); //$NON-NLS-1$

    private final org.openpnp.model.Configuration configuration;

    public Welcome2_0Dialog(Frame frame, org.openpnp.model.Configuration configuration) {
        super(frame, true);
        this.configuration = configuration;
        setTitle(String.format(Translations.getString("WelcomeDialog.Title"), versionShort())); //$NON-NLS-1$
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        MainFrame main = frame instanceof MainFrame ? (MainFrame) frame : MainFrame.get();

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Ui.surface());

        // ---- header: the logo, the name, one line on what this is ----
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(22, 24, 8, 24));
        header.add(new Logo(), BorderLayout.WEST);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(getTitle());
        title.setFont(Ui.weighted(18f, Tokens.FW_TITLE));
        titles.add(title);
        titles.add(Box.createVerticalStrut(2));
        titles.add(Ui.t2(Translations.getString("WelcomeDialog.Subtitle"))); //$NON-NLS-1$
        header.add(titles, BorderLayout.CENTER);
        JPanel placeholder = new JPanel();
        placeholder.setOpaque(false);
        placeholder.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_PLACEHOLDER, "win"); //$NON-NLS-1$
        header.add(placeholder, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // ---- the three steps, the first one to do marked ----
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(8, 24, 12, 24));
        RoundedPanel steps = new RoundedPanel(Tokens.R_MD, Ui::surface, Ui::border);
        steps.setLayout(new BoxLayout(steps, BoxLayout.Y_AXIS));
        JButton machine = Ui.button(Translations.getString("WelcomeDialog.StepMachine.Action"), null, //$NON-NLS-1$
                Ui.Size.Sm, Ui.Variant.Primary);
        machine.addActionListener(e -> {
            dispose();
            if (main != null) {
                main.showMachineSettings(org.openpnp.gui.machinesettings.MachineSettingsPanel.PRESETS);
            }
        });
        JButton enable = Ui.button(Translations.getString("WelcomeDialog.Step1.Action"), null, //$NON-NLS-1$
                Ui.Size.Sm, Ui.Variant.Default);
        Ui.movesMachine(enable);
        enable.addActionListener(e -> {
            dispose();
            enableAndHome();
        });
        JButton calibrate = Ui.button(Translations.getString("WelcomeDialog.Step3.Action"), null, //$NON-NLS-1$
                Ui.Size.Sm, Ui.Variant.Default);
        calibrate.addActionListener(e -> {
            dispose();
            if (main != null) {
                main.showCalibrationStep(null, null);
            }
        });
        // A configuration made elsewhere names that computer's port and cameras: the machine is
        // set up for this one before it is switched on.
        steps.add(step(1, "WelcomeDialog.StepMachine", true, machine)); //$NON-NLS-1$
        steps.add(step(2, "WelcomeDialog.Step1", false, enable)); //$NON-NLS-1$
        steps.add(step(3, "WelcomeDialog.Step3", false, calibrate)); //$NON-NLS-1$
        steps.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(steps);
        body.add(Box.createVerticalStrut(14));

        // ---- links ----
        JPanel links = new JPanel();
        links.setOpaque(false);
        links.setLayout(new BoxLayout(links, BoxLayout.X_AXIS));
        links.setAlignmentX(Component.LEFT_ALIGNMENT);
        links.add(link("book", "WelcomeDialog.Manual", () -> { //$NON-NLS-1$ //$NON-NLS-2$
            if (main != null) {
                main.userManualLinkAction.actionPerformed(null);
            }
        }));
        links.add(Box.createHorizontalStrut(16));
        links.add(link("folder", "WelcomeDialog.ConfigFolder", () -> org.openpnp.util.UiUtils.openFolder(this, //$NON-NLS-1$ //$NON-NLS-2$
                configuration.getConfigurationDirectory())));
        links.add(Box.createHorizontalGlue());
        body.add(links);
        root.add(body, BorderLayout.CENTER);

        // ---- foot: don't show again, later, start ----
        JPanel foot = new JPanel();
        foot.setOpaque(true);
        foot.setBackground(Ui.surface2());
        foot.setLayout(new BoxLayout(foot, BoxLayout.X_AXIS));
        foot.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.border()),
                new EmptyBorder(12, 20, 12, 20)));
        dontShow.setOpaque(false);
        dontShow.setFont(Ui.font(Tokens.FS_SMALL));
        foot.add(dontShow);
        foot.add(Box.createHorizontalGlue());
        JButton later = Ui.button(Translations.getString("WelcomeDialog.Later"), null, Ui.Size.Sm, Ui.Variant.Ghost); //$NON-NLS-1$
        later.setFocusable(true);
        later.addActionListener(e -> dispose());
        foot.add(later);
        foot.add(Box.createHorizontalStrut(8));
        JButton start = Ui.button(Translations.getString("WelcomeDialog.Start"), null, Ui.Size.Sm, Ui.Variant.Primary); //$NON-NLS-1$
        start.setFocusable(true);
        start.addActionListener(e -> {
            dispose();
            if (main != null) {
                main.showMachineSettings(org.openpnp.gui.machinesettings.MachineSettingsPanel.PRESETS);
            }
        });
        foot.add(start);
        root.add(foot, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(start);
        if (com.formdev.flatlaf.util.SystemInfo.isWindows_10_orLater
                && com.formdev.flatlaf.ui.FlatNativeWindowBorder.isSupported()) {
            getRootPane().putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT, true);
        }
        root.setPreferredSize(new Dimension(760, root.getPreferredSize().height));
        pack();
    }

    /** Whether the user ticked "don't show this at start again". */
    public boolean isDontShowAgain() {
        return dontShow.isSelected();
    }

    private static String versionShort() {
        String version = Main.getVersionString();
        int dash = version.indexOf('-');
        return dash > 0 ? version.substring(0, dash) : version;
    }

    /** Switches the machine on and homes it, as the machine controls do. */
    private void enableAndHome() {
        org.openpnp.util.UiUtils.submitUiMachineTask(() -> {
            org.openpnp.spi.Machine machine = configuration.getMachine();
            if (!machine.isEnabled()) {
                machine.setEnabled(true);
            }
            machine.home();
            return null;
        }, result -> {
        }, org.openpnp.util.UiUtils::showError, true);
    }

    /** One step: its number, what it is, where it stands, and the button that takes it. */
    private static JComponent step(int n, String key, boolean next, JButton action) {
        JPanel row = new JPanel(new BorderLayout(12, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                if (next) {
                    g.setColor(Ui.accentSoft());
                    g.fillRect(0, 0, getWidth(), getHeight());
                }
                super.paintComponent(g);
            }
        };
        row.setOpaque(false);
        row.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createMatteBorder(n == 1 ? 0 : 1, 0, 0, 0, Ui.border()),
                new EmptyBorder(14, 16, 14, 16)));
        JLabel number = new JLabel(String.valueOf(n), JLabel.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Ui.surface3());
                    g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                }
                finally {
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        number.setFont(Ui.font(Tokens.FS_BODY, Font.BOLD));
        number.setPreferredSize(new Dimension(34, 34));
        JPanel numberHolder = new JPanel(new java.awt.GridBagLayout());
        numberHolder.setOpaque(false);
        numberHolder.add(number);
        row.add(numberHolder, BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(Translations.getString(key + ".Title")); //$NON-NLS-1$
        title.setFont(Ui.weighted(Tokens.FS_BODY, Tokens.FW_SECTION));
        text.add(title);
        text.add(Box.createVerticalStrut(2));
        JLabel detail = new JLabel("<html><div style='width:360px'>" //$NON-NLS-1$
                + Translations.getString(key + ".Text") + "</div></html>"); //$NON-NLS-1$ //$NON-NLS-2$
        detail.setFont(Ui.font(Tokens.FS_SMALL));
        detail.setForeground(Ui.text2());
        text.add(detail);
        row.add(text, BorderLayout.CENTER);
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        Chip badge = new Chip(Translations.getString(next ? "WelcomeDialog.Next" : "WelcomeDialog.Later.Badge"), //$NON-NLS-1$ //$NON-NLS-2$
                next ? Chip.Tone.Run : Chip.Tone.Pending, Chip.Shape.Status);
        badge.setAlignmentY(Component.CENTER_ALIGNMENT);
        right.add(badge);
        right.add(Box.createHorizontalStrut(10));
        action.setAlignmentY(Component.CENTER_ALIGNMENT);
        right.add(action);
        JPanel rightHolder = new JPanel(new java.awt.GridBagLayout());
        rightHolder.setOpaque(false);
        rightHolder.add(right);
        row.add(rightHolder, BorderLayout.EAST);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private static JComponent link(String icon, String key, Runnable action) {
        JLabel link = new JLabel(Translations.getString(key), Ui.iconSm(icon), JLabel.LEFT);
        link.setFont(Ui.font(Tokens.FS_SMALL));
        link.setForeground(Ui.accent());
        link.setIconTextGap(6);
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }
        });
        return link;
    }

    /** The 40 pixel gradient tile with the P in it, as the top bar's but larger. */
    private static final class Logo extends JComponent {
        Logo() {
            setPreferredSize(new Dimension(40, 40));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, Ui.accent(), 40, 40, Ui.color("Pono.logoGradientEnd", 0x7c5cff))); //$NON-NLS-1$
                g2.fillRoundRect(0, 0, 40, 40, 24, 24);
                g2.setColor(Color.WHITE);
                g2.setFont(Ui.font(18f, Font.BOLD));
                int w = g2.getFontMetrics().stringWidth("P"); //$NON-NLS-1$
                g2.drawString("P", (40 - w) / 2f, 27f); //$NON-NLS-1$
            }
            finally {
                g2.dispose();
            }
        }
    }
}
