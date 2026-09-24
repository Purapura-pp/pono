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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.function.Supplier;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

import org.openpnp.Translations;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;

/**
 * The design system as the program draws it: every control of {@code 06-design-system} in the
 * order the mockup shows them, from the same factories the pages use. The UI ruler puts this beside
 * the mockup, one half per theme, so the controls are accepted before anything is built of them.
 * Help › Control gallery opens it.
 */
@SuppressWarnings("serial")
public class ControlGallery extends JFrame {
    public static final int WIDTH = 800;
    public static final int HEIGHT = 1000;

    public ControlGallery() {
        super(Translations.getString("ControlGallery.Title")); //$NON-NLS-1$
        setContentPane(content());
        getContentPane().setPreferredSize(new Dimension(WIDTH, HEIGHT));
        pack();
    }

    /** Opens the gallery, or brings it back to the front. */
    public static ControlGallery showGallery(Component parent) {
        ControlGallery gallery = new ControlGallery();
        gallery.setLocationRelativeTo(parent);
        gallery.setVisible(true);
        return gallery;
    }

    private JPanel content() {
        JPanel page = new JPanel();
        page.setBackground(Ui.bg());
        page.setLayout(new BoxLayout(page, BoxLayout.Y_AXIS));
        page.setBorder(new EmptyBorder(26, 28, 26, 28));

        JLabel title = new JLabel(Translations.getString(FlatLaf.isLafDark()
                ? "ControlGallery.Heading.Dark" : "ControlGallery.Heading.Light")); //$NON-NLS-1$ //$NON-NLS-2$
        title.setFont(Ui.font(20f, Font.BOLD));
        page.add(left(title));
        page.add(Box.createVerticalStrut(2));
        JLabel sub = Ui.muted(Translations.getString("ControlGallery.Subtitle")); //$NON-NLS-1$
        sub.setFont(Ui.font(Tokens.FS_SMALL));
        page.add(left(sub));

        page.add(heading("ControlGallery.Colours")); //$NON-NLS-1$
        page.add(left(swatches(new Object[][] {
                { Tokens.BG, "bg" }, { Tokens.SURFACE, "surface" }, { Tokens.SURFACE_2, "surface-2" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                { Tokens.SURFACE_3, "surface-3" }, { Tokens.BORDER_STRONG, "border" } }, false))); //$NON-NLS-1$ //$NON-NLS-2$
        page.add(Box.createVerticalStrut(8));
        page.add(left(swatches(new Object[][] {
                { Tokens.ACCENT, "accent" }, { Tokens.OK, "ok" }, { Tokens.WARN, "warn" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                { Tokens.ERR, "err" }, { Tokens.INFO, "info" } }, true))); //$NON-NLS-1$ //$NON-NLS-2$

        page.add(heading("ControlGallery.Type")); //$NON-NLS-1$
        page.add(left(typeRow("\u6807\u9898 15 / 700", Ui.weighted(Tokens.FS_TITLE, Tokens.FW_TITLE), Ui.text(), "\u98de\u8fbe F-08 \u00b7 \u6599\u5e26\u98de\u8fbe"))); //$NON-NLS-1$ //$NON-NLS-2$
        page.add(left(typeRow("\u6b63\u6587 13 / 400", Ui.font(Tokens.FS_BODY), Ui.text(), "\u628a\u76f8\u673a\u5341\u5b57\u7ebf\u5bf9\u51c6\u7b2c\u4e00\u4e2a\u5b9a\u4f4d\u5b54\u7684\u4e2d\u5fc3"))); //$NON-NLS-1$ //$NON-NLS-2$
        page.add(left(typeRow("\u8f85\u52a9 11.5 / 500", Ui.weighted(Tokens.FS_AUX, Tokens.FW_AUX), Ui.muted(), "\u4e0a\u6b21\u53d6\u6599 3 \u5206\u949f\u524d \u00b7 \u4f59\u91cf 1,240"))); //$NON-NLS-1$ //$NON-NLS-2$
        page.add(left(typeRow("\u6570\u503c mono 20 / 600", Ui.mono(Tokens.FS_DRO, Font.BOLD), Ui.text(), "120.450  -2.000  90.00"))); //$NON-NLS-1$ //$NON-NLS-2$

        page.add(heading("ControlGallery.Buttons")); //$NON-NLS-1$
        JButton disabled = Ui.button(t("ControlGallery.Disabled"), null, Ui.Size.Md, Ui.Variant.Default); //$NON-NLS-1$
        disabled.setEnabled(false);
        disabled.setToolTipText(t("ControlGallery.Disabled.toolTipText")); //$NON-NLS-1$
        page.add(left(row(10,
                Ui.button(t("ControlGallery.Start"), Ui.icon("play"), Ui.Size.Md, Ui.Variant.PrimaryOk), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.button(t("ControlGallery.Apply"), null, Ui.Size.Md, Ui.Variant.Primary), //$NON-NLS-1$
                Ui.button(t("ControlGallery.Reset"), null, Ui.Size.Md, Ui.Variant.Default), //$NON-NLS-1$
                Ui.button(t("ControlGallery.Cancel"), null, Ui.Size.Md, Ui.Variant.Ghost), //$NON-NLS-1$
                Ui.button(t("ControlGallery.Abort"), Ui.icon("stop"), Ui.Size.Md, Ui.Variant.Danger), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.button(t("ControlGallery.EmergencyStop"), null, Ui.Size.Md, Ui.Variant.SolidDanger), //$NON-NLS-1$
                Ui.iconButton(Ui.icon("camera"), Ui.Size.Md, Ui.Variant.Default, t("ControlGallery.Camera")), //$NON-NLS-1$ //$NON-NLS-2$
                disabled,
                group())));

        page.add(heading("ControlGallery.Status")); //$NON-NLS-1$
        page.add(left(row(8,
                tag("\u5df2\u8d34\u88c5", Chip.Tone.Ok), tag("\u6b63\u5728\u8d34\u88c5", Chip.Tone.Run), //$NON-NLS-1$ //$NON-NLS-2$
                tag("\u5f85\u8d34\u88c5", Chip.Tone.Pending), tag("\u4f59\u91cf\u4e0d\u8db3", Chip.Tone.Warn), //$NON-NLS-1$ //$NON-NLS-2$
                tag("\u9519\u8bef", Chip.Tone.Err), tag("\u8df3\u8fc7", Chip.Tone.Skip), //$NON-NLS-1$ //$NON-NLS-2$
                new Chip("\u5df2\u542f\u7528 \u00b7 \u5df2\u5f52\u4f4d", Chip.Tone.Ok, Chip.Shape.Chip).withLed(true), //$NON-NLS-1$
                new Chip("\u672a\u5f52\u4f4d", Chip.Tone.Warn, Chip.Shape.Chip).withLed(true), //$NON-NLS-1$
                new Chip("\u5df2\u65ad\u5f00", Chip.Tone.Neutral, Chip.Shape.Chip).withLed(true)))); //$NON-NLS-1$
        page.add(Box.createVerticalStrut(10));
        Forms.Toggle on = new Forms.Toggle();
        on.setSelected(true);
        javax.swing.JCheckBox checked = Forms.check(""); //$NON-NLS-1$
        checked.setSelected(true);
        page.add(left(row(10, on, new Forms.Toggle(), checked, Forms.check("")))); //$NON-NLS-1$

        page.add(heading("ControlGallery.Inputs")); //$NON-NLS-1$
        JPanel inputs = new JPanel(new GridLayout(1, 2, 20, 0));
        inputs.setOpaque(false);
        Forms.Grid form = new Forms.Grid(88);
        form.row("X / Y", Forms.row(Forms.input(new JTextField("120.450"), true, "X"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                Forms.input(new JTextField("85.210"), true, "Y"))); //$NON-NLS-1$ //$NON-NLS-2$
        JComboBox<String> part = Forms.dropdown(new JComboBox<>(new String[] { "R0603-10K" })); //$NON-NLS-1$
        part.setRenderer(Forms.described(s -> s, s -> null));
        form.row("\u5143\u4ef6", part); //$NON-NLS-1$
        form.row("\u53ea\u8bfb", Forms.readOnly("R0603", "\u9ad8 0.45 mm")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        JPanel formTop = new JPanel(new BorderLayout());
        formTop.setOpaque(false);
        formTop.add(form, BorderLayout.NORTH);
        inputs.add(formTop);
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        Forms.Segmented steps = new Forms.Segmented(
                java.util.List.of("0.01", "0.1", "1", "10", "100"), String::valueOf); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        steps.setSelectedItem("1"); //$NON-NLS-1$
        // A grid cell stretches the stylesheet's segments across it, and not down.
        steps.setMaximumSize(new Dimension(Integer.MAX_VALUE, steps.getPreferredSize().height));
        right.add(left(steps));
        right.add(Box.createVerticalStrut(12));
        JSlider slider = new JSlider(0, 100, 70);
        slider.setOpaque(false);
        JPanel speed = new JPanel(new BorderLayout(12, 0));
        speed.setOpaque(false);
        speed.add(Ui.muted("\u901f\u5ea6"), BorderLayout.WEST); //$NON-NLS-1$
        speed.add(slider, BorderLayout.CENTER);
        speed.add(Ui.mono("70%", Tokens.FS_SMALL), BorderLayout.EAST); //$NON-NLS-1$
        speed.setMaximumSize(new Dimension(Integer.MAX_VALUE, speed.getPreferredSize().height));
        right.add(left(speed));
        right.add(Box.createVerticalStrut(12));
        right.add(left(Forms.search("\u641c\u7d22\u6216\u8f93\u5165\u547d\u4ee4\u2026", "Ctrl K"))); //$NON-NLS-1$ //$NON-NLS-2$
        right.add(Box.createVerticalGlue());
        inputs.add(right);
        page.add(left(inputs));

        page.add(heading("ControlGallery.Navigation")); //$NON-NLS-1$
        page.add(left(row(12, rail(), dock())));
        page.add(Box.createVerticalGlue());
        return page;
    }

    private static String t(String key) {
        return Translations.getString(key);
    }

    private static JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setAlignmentY(Component.TOP_ALIGNMENT);
        return c;
    }

    private static JComponent heading(String key) {
        // The stylesheet sets these headings in capitals: a Latin heading's, as Chinese has none
        // and the "px" in it would only become "PX".
        String text = t(key);
        if (text.codePoints().noneMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)) {
            text = text.toUpperCase(java.util.Locale.ROOT);
        }
        JLabel label = new JLabel(text);
        label.setFont(Ui.font(Tokens.FS_TAG, Font.BOLD));
        label.setForeground(Ui.text2());
        label.setBorder(new EmptyBorder(18, 0, 10, 0));
        return left(label);
    }

    private static JPanel row(int gap, JComponent... items) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, gap, 0));
        row.setOpaque(false);
        for (JComponent item : items) {
            row.add(item);
        }
        ((FlowLayout) row.getLayout()).setAlignOnBaseline(false);
        row.setBorder(new EmptyBorder(0, -gap, 0, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private static JComponent swatches(Object[][] colours, boolean filled) {
        JPanel row = new JPanel(new GridLayout(1, colours.length, 8, 0));
        row.setOpaque(false);
        for (Object[] colour : colours) {
            Supplier<Color> fill = () -> Ui.color((String) colour[0], 0x808080);
            RoundedPanel swatch = new RoundedPanel(Tokens.R_MD, fill, Ui::border);
            swatch.setLayout(new BorderLayout());
            Color c = javax.swing.UIManager.getColor((String) colour[0]);
            JLabel name = new JLabel(colour[1] + " " + (c == null ? "" : String.format("#%06x", c.getRGB() & 0xffffff))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            name.setFont(Ui.mono(Tokens.FS_MICRO, Font.PLAIN));
            // On a filled swatch, white or near black, whichever the fill leaves more readable.
            Color on = c == null ? Color.WHITE : luminance(c) > 0.4 ? new Color(0x0b0e12) : Color.WHITE;
            name.setForeground(filled ? on : Ui.text2());
            name.setBorder(new EmptyBorder(0, 8, 6, 8));
            name.setVerticalAlignment(SwingConstants.BOTTOM);
            swatch.add(name, BorderLayout.CENTER);
            swatch.setPreferredSize(new Dimension(140, 54));
            row.add(swatch);
        }
        row.setPreferredSize(new Dimension(744, 54));
        row.setMaximumSize(new Dimension(744, 54));
        return row;
    }

    /** The colour's relative luminance, 0 for black and 1 for white. */
    private static double luminance(Color c) {
        double[] rgb = { c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0 };
        for (int i = 0; i < 3; i++) {
            rgb[i] = rgb[i] <= 0.03928 ? rgb[i] / 12.92 : Math.pow((rgb[i] + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
    }

    private static JComponent typeRow(String spec, Font font, Color colour, String sample) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        row.setOpaque(false);
        JLabel label = Ui.muted(spec);
        label.setFont(Ui.mono(Tokens.FS_MICRO, Font.PLAIN));
        label.setPreferredSize(new Dimension(130, label.getPreferredSize().height));
        row.add(label);
        JLabel text = new JLabel(sample);
        text.setFont(font);
        text.setForeground(colour);
        row.add(text);
        row.setBorder(new EmptyBorder(0, -12, 4, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private static JComponent tag(String text, Chip.Tone tone) {
        return new Chip(text, tone, Chip.Shape.Status);
    }

    private static JComponent group() {
        return Ui.group(Ui.button("Top", null, Ui.Size.Sm, Ui.Variant.Default), //$NON-NLS-1$
                Ui.button("Bottom", null, Ui.Size.Sm, Ui.Variant.Primary)); //$NON-NLS-1$
    }

    /** Three items of the navigation rail laid in a row, as the mockup samples them. */
    private static JComponent rail() {
        RoundedPanel box = new RoundedPanel(12, Ui::surface, Ui::border) {
            @Override
            protected void paintChildren(Graphics g) {
                super.paintChildren(g);
                // The active item's 3 by 26 accent bar at the rail's left edge.
                Component active = getComponent(0);
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Ui.accent());
                    g2.fillRoundRect(-2, active.getY() + (active.getHeight() - 26) / 2, 5, 26, 4, 4);
                }
                finally {
                    g2.dispose();
                }
            }
        };
        box.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 6));
        box.add(railItem("job", "\u4efb\u52a1", true, 0, false)); //$NON-NLS-1$ //$NON-NLS-2$
        box.add(railItem("feeder", "\u98de\u8fbe", false, 1, true)); //$NON-NLS-1$ //$NON-NLS-2$
        box.add(railItem("alert", "\u95ee\u9898", false, 2, false)); //$NON-NLS-1$ //$NON-NLS-2$
        return box;
    }

    private static JComponent railItem(String icon, String text, boolean active, int badge, boolean warn) {
        JLabel item = new JLabel(text, Ui.icon(icon, 20), SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    if (active) {
                        g2.setColor(Ui.accentSoft());
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 2 * Tokens.R_MD, 2 * Tokens.R_MD);
                    }
                }
                finally {
                    g2.dispose();
                }
                super.paintComponent(g);
                if (badge > 0) {
                    Graphics2D g3 = (Graphics2D) g.create();
                    try {
                        g3.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g3.setColor(warn ? Ui.warn() : Ui.err());
                        g3.fillRoundRect(getWidth() - 24, 6, 16, 16, 16, 16);
                        g3.setColor(warn ? new Color(0x1a1200) : Color.WHITE);
                        g3.setFont(Ui.font(Tokens.FS_MICRO, Font.BOLD));
                        String n = String.valueOf(badge);
                        int w = g3.getFontMetrics().stringWidth(n);
                        g3.drawString(n, getWidth() - 16 - w / 2, 18);
                    }
                    finally {
                        g3.dispose();
                    }
                }
            }
        };
        item.setVerticalTextPosition(SwingConstants.BOTTOM);
        item.setHorizontalTextPosition(SwingConstants.CENTER);
        item.setIconTextGap(4);
        item.setFont(active ? Ui.font(Tokens.FS_TAG, Font.BOLD) : Ui.font(Tokens.FS_TAG));
        item.setForeground(active ? Ui.accent() : Ui.text2());
        item.setPreferredSize(new Dimension(56, 54));
        return item;
    }

    private static JComponent dock() {
        DockPanel dock = new DockPanel();
        DefaultTableModel model = new DefaultTableModel(new Object[][] {
                { "R11", "R0603-10K", "118.200", "\u5df2\u8d34\u88c5" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                { "R12", "R0603-10K", "120.450", "\u6b63\u5728\u8d34\u88c5" } }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                new Object[] { "ID", "\u5143\u4ef6", "X", "\u72b6\u6001" }) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        org.openpnp.gui.support.TableUtils.setColumnKinds(table, org.openpnp.gui.support.TableUtils.Kind.Id,
                org.openpnp.gui.support.TableUtils.Kind.Name, org.openpnp.gui.support.TableUtils.Kind.Number,
                org.openpnp.gui.support.TableUtils.Kind.Status);
        JScrollPane scroll = DockPanel.table(table);
        table.getColumnModel().getColumn(0).setCellRenderer(DockRenderers.bold());
        table.getColumnModel().getColumn(2).setCellRenderer(DockRenderers.mono(v -> String.valueOf(v)));
        table.getColumnModel().getColumn(3).setCellRenderer(DockRenderers.status(
                v -> "\u5df2\u8d34\u88c5".equals(v) ? Chip.Tone.Ok : Chip.Tone.Run, String::valueOf)); //$NON-NLS-1$
        table.setRowSelectionInterval(1, 1);
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(scroll);
        dock.addTab(Ui.iconSm("board"), "\u5355\u677f", new JPanel()).setCount(2); //$NON-NLS-1$ //$NON-NLS-2$
        DockPanel.Tab placements = dock.addTab(Ui.iconSm("parts"), "\u8d34\u7247\u4f4d", body); //$NON-NLS-1$ //$NON-NLS-2$
        placements.setCount(48);
        dock.select(placements);
        dock.setPreferredSize(new Dimension(520, 150));
        return dock;
    }
}
