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
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import org.openpnp.Main;
import org.openpnp.Translations;
import org.openpnp.gui.JobPanel;
import org.openpnp.gui.MachineControlsPanel;
import org.openpnp.gui.components.ThemeDialog;
import org.openpnp.gui.components.ThemeInfo;
import org.openpnp.gui.components.ThemeSettingsPanel;
import org.openpnp.gui.theme.PonoThemes;
import org.openpnp.model.Configuration;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;

/**
 * The one bar across the top: brand, menus, the job, the machine's state, the job controls,
 * progress, the command search, notifications and the theme.
 * <p>
 * This is the stylesheet's {@code .topbar}: 52 pixels, everything on a single row with 14 pixel
 * gaps and 1 pixel rules between groups. The menu bar is an ordinary component here rather than
 * the window's own, which is what lets the brand sit to its left and the job to its right.
 */
@SuppressWarnings("serial")
public class TopBarPanel extends JPanel {
    public static final int HEIGHT = 52;

    private final Configuration configuration;
    private final JobPanel jobPanel;

    private final JLabel jobNameLabel = new JLabel();
    private final JComponent unsavedDot = new Dot();
    private final JLabel progressText = Ui.mono("0 / 0", 12f); //$NON-NLS-1$
    private final ProgressBar progressBar = new ProgressBar();
    private final JLabel progressPercent = Ui.muted("0%"); //$NON-NLS-1$
    private JButton startButton;
    private JButton pauseButton;
    private JButton themeButton;
    private final JMenuBar menuBar;

    /** Shown while the configuration has changes that are not on disk yet; a click saves them. */
    private final Chip configurationDirty = new Chip(
            Translations.getString("TopBar.ConfigurationDirty"), Chip.Tone.Warn, Chip.Shape.Chip); //$NON-NLS-1$

    public TopBarPanel(Configuration configuration, JobPanel jobPanel,
            MachineControlsPanel machineControls, JMenuBar menuBar, Runnable openIssues,
            Runnable openCommands, Action stopMachine, Runnable saveConfiguration) {
        this.configuration = configuration;
        this.jobPanel = jobPanel;
        this.menuBar = menuBar;
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.border()),
                new EmptyBorder(0, 12, 0, 14)));
        setBackground(Ui.surface());
        setOpaque(true);

        add(brand());
        add(Box.createHorizontalStrut(14));
        add(menus(menuBar));
        add(Box.createHorizontalStrut(14));
        add(Ui.divider(24));
        add(Box.createHorizontalStrut(14));
        add(jobName());
        add(Box.createHorizontalStrut(8));
        configurationDirty.setToolTipText(Translations.getString("TopBar.ConfigurationDirty.toolTipText")); //$NON-NLS-1$
        configurationDirty.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        configurationDirty.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                saveConfiguration.run();
            }
        });
        configurationDirty.setVisible(configuration.isDirty());
        add(configurationDirty);
        add(Box.createHorizontalGlue());
        add(new MachineStateChip(configuration, machineControls.startStopMachineAction));
        add(Box.createHorizontalStrut(14));
        add(Ui.divider(24));
        add(Box.createHorizontalStrut(14));
        add(jobControls());
        add(Box.createHorizontalStrut(8));
        // Always there and always red: stopping the machine must not depend on a job running,
        // which is the only time the job's own Stop is enabled.
        JButton stopMachineButton = Ui.button(stopMachine, Ui.Size.Md, Ui.Variant.SolidDanger);
        stopMachineButton.setIcon(Ui.icon("power")); //$NON-NLS-1$
        // It never gives way: on a narrow window the other controls truncate before this does.
        // Measured again with its icon, which the styling's fixed width was taken without.
        stopMachineButton.setPreferredSize(null);
        Dimension stopSize = new Dimension(stopMachineButton.getPreferredSize().width,
                Ui.Size.Md.height);
        stopMachineButton.setPreferredSize(stopSize);
        stopMachineButton.setMinimumSize(stopSize);
        stopMachineButton.setMaximumSize(stopSize);
        add(stopMachineButton);
        add(Box.createHorizontalStrut(14));
        add(progress());
        add(Box.createHorizontalStrut(14));
        add(Ui.divider(24));
        add(Box.createHorizontalStrut(14));
        add(search(openCommands));
        add(Box.createHorizontalStrut(6));
        JButton bell = Ui.iconButton(Ui.icon("bell"), Ui.Size.Md, Ui.Variant.Ghost, //$NON-NLS-1$
                Translations.getString("TopBar.Notifications.toolTipText")); //$NON-NLS-1$
        bell.addActionListener(e -> openIssues.run());
        add(bell);
        add(Box.createHorizontalStrut(6));
        themeButton = Ui.iconButton(Ui.icon(FlatLaf.isLafDark() ? "moon" : "sun"), Ui.Size.Md, //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Variant.Ghost, Translations.getString("TopBar.Theme.toolTipText")); //$NON-NLS-1$
        themeButton.addActionListener(e -> toggleTheme());
        add(themeButton);

        for (Component c : getComponents()) {
            if (c instanceof JComponent) {
                ((JComponent) c).setAlignmentY(CENTER_ALIGNMENT);
            }
        }
    }

    /** The logo tile, the name and the version, as {@code .brand}. */
    private JComponent brand() {
        JPanel brand = row(9);
        brand.add(new LogoTile());
        JLabel name = new JLabel("Pono"); //$NON-NLS-1$
        name.setFont(Ui.font(15f, Font.BOLD));
        brand.add(name);
        String version = Main.getVersionString();
        int dash = version.indexOf('-');
        JLabel ver = Ui.muted(dash > 0 ? version.substring(0, dash) : version);
        ver.setFont(Ui.font(11f));
        ver.setBorder(new EmptyBorder(0, 2, 0, 0));
        brand.add(ver);
        return brand;
    }

    /** The window's menu bar, dressed as {@code .menus}: flat, secondary text, hover only. */
    private JComponent menus(JMenuBar menuBar) {
        menuBar.setOpaque(false);
        menuBar.setBorder(null);
        menuBar.putClientProperty(FlatClientProperties.STYLE,
                "background: null; itemMargins: 5,7,5,7; " //$NON-NLS-1$
                        + "hoverBackground: $Pono.hover; selectionBackground: $Pono.surface3; " //$NON-NLS-1$
                        + "selectionForeground: $Label.foreground"); //$NON-NLS-1$
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu != null) {
                menu.setForeground(Ui.text2());
            }
        }
        menuBar.setMaximumSize(menuBar.getPreferredSize());
        return menuBar;
    }

    /** The job's file name in a pill, with the warning dot while it has unsaved changes. */
    private JComponent jobName() {
        RoundedPanel pill = new RoundedPanel(6, Ui.surface2(), Ui.border());
        pill.setLayout(new BoxLayout(pill, BoxLayout.X_AXIS));
        pill.setBorder(new EmptyBorder(5, 10, 5, 10));
        pill.setToolTipText(Translations.getString("TopBar.Job.toolTipText")); //$NON-NLS-1$
        JLabel folder = new JLabel(Ui.iconSm("folder")); //$NON-NLS-1$
        folder.setForeground(Ui.text2());
        pill.add(folder);
        pill.add(Box.createHorizontalStrut(6));
        jobNameLabel.setFont(Ui.font(Ui.BASE, Font.BOLD));
        pill.add(jobNameLabel);
        pill.add(Box.createHorizontalStrut(6));
        pill.add(unsavedDot);
        showJobName(jobPanel.getJobDisplayName());
        // JobPanel already recomputes this whenever the job's file or dirty flag changes, because
        // the window title needs it too.
        jobPanel.addPropertyChangeListener(JobPanel.PROPERTY_JOB_DISPLAY_NAME,
                e -> showJobName(String.valueOf(e.getNewValue())));
        // The job's name is what gives way on a narrow window: it truncates with an ellipsis,
        // where a control that lost width would stop working.
        pill.setMaximumSize(new Dimension(360, 30));
        pill.setMinimumSize(new Dimension(120, 30));
        jobNameLabel.setMinimumSize(new Dimension(40, 20));
        return pill;
    }

    /** Whether to show that the configuration has unsaved changes. */
    public void setConfigurationDirty(boolean dirty) {
        configurationDirty.setVisible(dirty);
        revalidate();
        repaint();
    }

    private void showJobName(String displayName) {
        // The display name carries the dirty flag as a leading asterisk, which the dot replaces.
        boolean dirty = displayName.startsWith("*"); //$NON-NLS-1$
        jobNameLabel.setText(dirty ? displayName.substring(1) : displayName);
        unsavedDot.setVisible(dirty);
    }

    /** Start, Pause, Step, Stop as {@code .btn}s: Start green, Stop red, the two others plain. */
    private JComponent jobControls() {
        JPanel row = row(6);
        Action startPause = jobPanel.startPauseResumeJobAction;
        startButton = Ui.button(Translations.getString("TopBar.Job.Start"), Ui.icon("play"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Md, Ui.Variant.PrimaryOk);
        startButton.addActionListener(e -> startPause.actionPerformed(e));
        pauseButton = Ui.button(Translations.getString("TopBar.Job.Pause"), Ui.icon("pause"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Md, Ui.Variant.Default);
        pauseButton.addActionListener(e -> startPause.actionPerformed(e));
        JButton step = Ui.button(Translations.getString("TopBar.Job.Step"), Ui.icon("step"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Md, Ui.Variant.Default);
        step.addActionListener(e -> jobPanel.stepJobAction.actionPerformed(e));
        JButton stop = Ui.button(Translations.getString("TopBar.Job.Stop"), Ui.icon("stop"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Md, Ui.Variant.Danger);
        stop.addActionListener(e -> jobPanel.stopJobAction.actionPerformed(e));
        row.add(startButton);
        row.add(pauseButton);
        row.add(step);
        row.add(stop);
        // One action drives both Start and Pause, saying which it is by its name. The buttons
        // keep their own names and take turns being enabled instead.
        Runnable follow = () -> {
            boolean running = jobPanel.isJobRunning();
            startButton.setEnabled(startPause.isEnabled() && !running);
            pauseButton.setEnabled(startPause.isEnabled() && running);
            step.setEnabled(jobPanel.stepJobAction.isEnabled());
            stop.setEnabled(jobPanel.stopJobAction.isEnabled());
        };
        startPause.addPropertyChangeListener(e -> follow.run());
        jobPanel.stepJobAction.addPropertyChangeListener(e -> follow.run());
        jobPanel.stopJobAction.addPropertyChangeListener(e -> follow.run());
        jobPanel.addPropertyChangeListener(JobPanel.PROPERTY_JOB_RUNNING, e -> follow.run());
        follow.run();
        return row;
    }

    /** {@code 31 / 48 [====  ] 64%}, 180 pixels wide. */
    private JComponent progress() {
        JPanel row = row(8);
        row.add(progressText);
        row.add(progressBar);
        row.add(progressPercent);
        row.setPreferredSize(new Dimension(180, HEIGHT));
        row.setMinimumSize(new Dimension(120, HEIGHT));
        row.setMaximumSize(new Dimension(180, HEIGHT));
        progressPercent.setFont(Ui.font(12f));
        return row;
    }

    /** The command search as {@code .search}: 190 x 32, muted placeholder, a keycap. */
    private JComponent search(Runnable openCommands) {
        RoundedPanel box = new RoundedPanel(6, Ui.surface2(), Ui.border());
        box.setLayout(new BoxLayout(box, BoxLayout.X_AXIS));
        box.setBorder(new EmptyBorder(0, 10, 0, 10));
        JLabel icon = new JLabel(Ui.iconSm("search")); //$NON-NLS-1$
        icon.setForeground(Ui.muted());
        box.add(icon);
        box.add(Box.createHorizontalStrut(8));
        JLabel placeholder = Ui.muted(Translations.getString("TopBar.Search.Placeholder")); //$NON-NLS-1$
        box.add(placeholder);
        box.add(Box.createHorizontalGlue());
        box.add(Ui.kbd("Ctrl K")); //$NON-NLS-1$
        Dimension size = new Dimension(190, 32);
        box.setPreferredSize(size);
        // On a narrow window the box gives up its placeholder before anything else loses a button.
        box.setMinimumSize(new Dimension(60, 32));
        box.setMaximumSize(size);
        placeholder.setMinimumSize(new Dimension(0, 20));
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        box.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openCommands.run();
            }
        });
        return box;
    }

    /** Placements done out of the total, as the progress group shows them. */
    public void setProgress(int done, int total) {
        progressText.setText(done + " / " + total); //$NON-NLS-1$
        int percent = total > 0 ? Math.round(done * 100f / total) : 0;
        progressBar.setFraction(total > 0 ? done / (double) total : 0);
        progressPercent.setText(percent + "%"); //$NON-NLS-1$
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

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.height = HEIGHT;
        return size;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // Runs again after every look and feel change, which is when the colours read here are
        // stale. Null while the superclass constructor is still running.
        if (themeButton != null) {
            themeButton.setIcon(Ui.icon(FlatLaf.isLafDark() ? "moon" : "sun")); //$NON-NLS-1$ //$NON-NLS-2$
            setBackground(Ui.surface());
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.border()),
                    new EmptyBorder(0, 12, 0, 14)));
            if (menuBar != null) {
                menus(menuBar);
            }
        }
    }

    /** A row of components with a fixed gap between them and none outside, as the stylesheet's flex rows. */
    private static JPanel row(int gap) {
        return new Row(gap);
    }

    private static final class Row extends JPanel {
        private final int gap;

        Row(int gap) {
            this.gap = gap;
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        }

        @Override
        public Component add(Component component) {
            if (getComponentCount() > 0) {
                super.add(Box.createHorizontalStrut(gap));
            }
            if (component instanceof JComponent) {
                ((JComponent) component).setAlignmentY(CENTER_ALIGNMENT);
            }
            return super.add(component);
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension size = getPreferredSize();
            return isPreferredSizeSet() ? super.getMaximumSize() : size;
        }
    }

    /** The 26 pixel gradient square with the P in it. */
    private static final class LogoTile extends JComponent {
        LogoTile() {
            setPreferredSize(new Dimension(26, 26));
            setMinimumSize(new Dimension(26, 26));
            setMaximumSize(new Dimension(26, 26));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, Ui.accent(), 26, 26,
                        Ui.color("Pono.logoGradientEnd", 0x7c5cff))); //$NON-NLS-1$
                g2.fillRoundRect(0, 0, 26, 26, 8, 8);
                g2.setColor(Color.WHITE);
                g2.setFont(Ui.font(13f, Font.BOLD));
                String p = "P"; //$NON-NLS-1$
                int w = g2.getFontMetrics().stringWidth(p);
                int h = g2.getFontMetrics().getAscent();
                g2.drawString(p, (26 - w) / 2f, (26 + h) / 2f - 2);
            }
            finally {
                g2.dispose();
            }
        }
    }

    /** The 7 pixel warning dot beside an unsaved job's name. */
    private static final class Dot extends JComponent {
        Dot() {
            Dimension size = new Dimension(7, 7);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Ui.warn());
                g2.fillOval(0, 0, 7, 7);
            }
            finally {
                g2.dispose();
            }
        }
    }

    /** The 6 pixel bar: a rounded track with the accent fill grown to the fraction. */
    private static final class ProgressBar extends JComponent {
        private double fraction;

        ProgressBar() {
            setPreferredSize(new Dimension(80, 6));
            setMinimumSize(new Dimension(40, 6));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        }

        void setFraction(double fraction) {
            this.fraction = Math.max(0, Math.min(1, fraction));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                g2.setColor(Ui.surface3());
                g2.fillRoundRect(0, 0, w, 6, 3, 3);
                int filled = (int) Math.round(w * fraction);
                if (filled > 0) {
                    g2.setColor(fraction >= 1 ? Ui.ok() : Ui.accent());
                    g2.fillRoundRect(0, 0, filled, 6, 3, 3);
                }
            }
            finally {
                g2.dispose();
            }
        }
    }
}
