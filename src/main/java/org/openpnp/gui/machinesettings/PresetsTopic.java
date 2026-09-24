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

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.openpnp.Translations;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.RoundedPanel;
import org.openpnp.gui.shell.Tokens;
import org.openpnp.gui.shell.Ui;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.presets.MachinePreset;
import org.openpnp.machine.reference.presets.MachinePresets;
import org.openpnp.machine.reference.presets.PresetXml;
import org.openpnp.util.UiUtils;
import org.pmw.tinylog.Logger;

/**
 * The model the machine is and the presets it can be set from, the mockups' 24 and 31: the ones
 * Pono comes with and the user's own as cards, a new one from this machine or from another
 * configuration's folder, and applying one - what it changes said first, the configuration backed
 * up, the machine definition replaced with the feeders, parts and jobs kept, and Pono started
 * again on it.
 */
final class PresetsTopic extends Topic {
    private final MachineSettingsPanel page;
    private final ReferenceMachine machine;

    PresetsTopic(MachineSettingsPanel page, ReferenceMachine machine) {
        super(MachineSettingsPanel.PRESETS, "layers"); //$NON-NLS-1$
        this.page = page;
        this.machine = machine;
    }

    private File configurationDirectory() {
        return page.getConfiguration().getConfigurationDirectory();
    }

    @Override
    protected JComponent build() {
        JPanel builtIn = grid();
        for (MachinePreset preset : MachinePresets.builtIn()) {
            builtIn.add(new PresetCard(preset));
        }
        JLabel note = Ui.muted("<html>" + Translations.getString("MachineSettings.Presets.BuiltIn.Note") + "</html>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        note.setFont(Ui.font(Tokens.FS_SMALL));
        note.setVerticalAlignment(JLabel.CENTER);
        builtIn.add(note);

        JPanel mine = grid();
        for (MachinePreset preset : MachinePresets.user(configurationDirectory())) {
            mine.add(new PresetCard(preset));
        }
        mine.add(new AddCard());

        Forms.Section builtInSection = new Forms.Section("layers", Translations.getString("MachineSettings.Presets.BuiltIn")) //$NON-NLS-1$ //$NON-NLS-2$
                .content(builtIn);
        JLabel where = Ui.muted(Translations.getString("MachineSettings.Presets.Mine.Where")); //$NON-NLS-1$
        where.setFont(Ui.weighted(Tokens.FS_TAG, Tokens.FW_AUX));
        where.setToolTipText(new File(configurationDirectory(), MachinePresets.DIRECTORY).getPath());
        Forms.Section mineSection = new Forms.Section("user", Translations.getString("MachineSettings.Presets.Mine")) //$NON-NLS-1$ //$NON-NLS-2$
                .withRight(where)
                .content(mine);
        JPanel effects = new JPanel(new GridLayout(1, 3, 10, 0));
        effects.setOpaque(false);
        effects.add(effect("refresh", Ui::accent, "Replaced")); //$NON-NLS-1$ //$NON-NLS-2$
        effects.add(effect("lock", Ui::ok, "Kept")); //$NON-NLS-1$ //$NON-NLS-2$
        effects.add(effect("hand", Ui::warn, "After")); //$NON-NLS-1$ //$NON-NLS-2$
        Forms.Section effectsSection = new Forms.Section("info", Translations.getString("MachineSettings.Presets.Effects")) //$NON-NLS-1$ //$NON-NLS-2$
                .content(effects);
        return MachineSettingsPanel.page(Guide.of(Translations.getString("MachineSettings.Guide.Presets")), //$NON-NLS-1$
                builtInSection, mineSection, effectsSection);
    }

    private static JPanel grid() {
        JPanel grid = new JPanel(new GridLayout(0, 3, 12, 12));
        grid.setOpaque(false);
        return grid;
    }

    private static JComponent effect(String icon, java.util.function.Supplier<Color> tone, String key) {
        RoundedPanel box = new RoundedPanel(Tokens.R_MD, Ui::surface2, Ui::border);
        box.setLayout(new BorderLayout(10, 0));
        box.setBorder(new EmptyBorder(10, 12, 10, 12));
        JLabel mark = new JLabel(Ui.icon(icon, 14, tone.get()));
        mark.setVerticalAlignment(JLabel.TOP);
        box.add(mark, BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel title = new JLabel(Translations.getString("MachineSettings.Presets.Effects." + key)); //$NON-NLS-1$
        title.setFont(Ui.weighted(Tokens.FS_SMALL + 0.5f, Tokens.FW_SECTION));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(title);
        text.add(Box.createVerticalStrut(2));
        javax.swing.JTextArea line = Forms.paragraph(Translations.getString("MachineSettings.Presets.Effects." + key + ".Text")); //$NON-NLS-1$ //$NON-NLS-2$
        line.setFont(Ui.font(Tokens.FS_SMALL));
        line.setForeground(Ui.text2());
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(line);
        box.add(text, BorderLayout.CENTER);
        return box;
    }

    /** The topic built again: a preset made, renamed or deleted. */
    private void refresh() {
        page.rebuild();
    }

    private boolean isCurrent(MachinePreset preset) {
        return preset.getName().equals(machine.getPresetName()) && preset.isBuiltIn() == machine.isPresetBuiltIn();
    }

    // ---- what a preset is, in words ----------------------------------------------------------

    /** The lines of a card: the nozzles and tips, the travel and the controller, the cameras. */
    private static List<String> lines(PresetXml.Summary summary) {
        List<String> lines = new ArrayList<>();
        String nozzles = nozzles(summary);
        if (!summary.tips.isEmpty()) {
            nozzles += " \u00b7 " + String.format(Translations.getString("MachineSettings.Presets.Tips"), summary.tips.size()); //$NON-NLS-1$ //$NON-NLS-2$
        }
        lines.add(nozzles);
        List<String> motion = new ArrayList<>();
        if (summary.travelX != null && summary.travelY != null) {
            motion.add(String.format(Locale.US, Translations.getString("MachineSettings.Head.Travel"), //$NON-NLS-1$
                    summary.travelX, summary.travelY, "mm")); //$NON-NLS-1$
        }
        motion.add(controller(summary, false, null));
        lines.add(String.join(" \u00b7 ", motion)); //$NON-NLS-1$
        lines.add(cameras(summary));
        return lines;
    }

    private static String nozzles(PresetXml.Summary summary) {
        if (summary.nozzles.nozzles == 0) {
            return Translations.getString("MachineSettings.Presets.NoNozzles"); //$NON-NLS-1$
        }
        if (summary.nozzles.nozzles == 1) {
            return Translations.getString("MachineSettings.Head.OneNozzle"); //$NON-NLS-1$
        }
        return String.format(Translations.getString("MachineSettings.Head.Nozzles"), summary.nozzles.nozzles, //$NON-NLS-1$
                NozzleStructure.name(summary.nozzles.solution));
    }

    /**
     * "Marlin bugfix-2.1.x · 115200", "simulated", with the port when asked: the summary's, the
     * one that will be carried over when it has none, or that it is still to be chosen.
     */
    private static String controller(PresetXml.Summary summary, boolean port, String carried) {
        if (summary.driver.equals("NullDriver") || summary.driver.isEmpty()) { //$NON-NLS-1$
            return Translations.getString("MachineSettings.Presets.Simulated"); //$NON-NLS-1$
        }
        List<String> parts = new ArrayList<>();
        parts.add(summary.firmware != null ? summary.firmware
                : summary.driver.startsWith("Gcode") ? Translations.getString("MachineSettings.Presets.Driver.Gcode") //$NON-NLS-1$ //$NON-NLS-2$
                        : summary.driver);
        if (summary.tcp) {
            parts.add("TCP"); //$NON-NLS-1$
        }
        else {
            // The port where it is asked for, a baud rate otherwise: the two do not fit one line.
            if (summary.baud > 0 && !port) {
                parts.add(String.valueOf(summary.baud));
            }
            if (port) {
                String shown = summary.port != null ? summary.port : carried;
                parts.add(shown != null ? shown : Translations.getString("MachineSettings.Presets.PortToChoose")); //$NON-NLS-1$
            }
        }
        return String.join(" \u00b7 ", parts); //$NON-NLS-1$
    }

    private static String cameras(PresetXml.Summary summary) {
        if (summary.cameras.isEmpty()) {
            return Translations.getString("MachineSettings.Presets.NoCameras"); //$NON-NLS-1$
        }
        if (summary.camerasDown == 1 && summary.camerasUp == 1) {
            return Translations.getString("MachineSettings.Head.TopAndBottom"); //$NON-NLS-1$
        }
        return String.format(Translations.getString("MachineSettings.Head.Cameras"), summary.cameras.size()); //$NON-NLS-1$
    }

    // ---- the cards ----------------------------------------------------------------------------

    /** The stylesheet's {@code .pcard}: a picture of the machine, its name, what it is, and what can be done. */
    private final class PresetCard extends RoundedPanel {
        PresetCard(MachinePreset preset) {
            this(preset, isCurrent(preset));
        }

        private PresetCard(MachinePreset preset, boolean current) {
            super(Tokens.R_MD, Ui::surface2, current ? Ui::accent : Ui::border);
            setLayout(new BorderLayout());
            PresetXml.Summary summary = null;
            try {
                summary = MachinePresets.summary(preset);
            }
            catch (Exception e) {
                Logger.warn(e, "The preset {} could not be read.", preset.getName()); //$NON-NLS-1$
            }
            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(true);
            top.setBackground(current ? Ui.accentSoft() : Ui.surface3());
            top.add(new MachinePicture(summary == null ? 1 : summary.nozzles.nozzles, current), BorderLayout.CENTER);
            if (current) {
                Chip tag = new Chip(Translations.getString("MachineSettings.Presets.Current"), Chip.Tone.Accent, //$NON-NLS-1$
                        Chip.Shape.Status).withLed(false);
                JPanel tagHolder = new JPanel(new BorderLayout());
                tagHolder.setOpaque(false);
                tagHolder.setBorder(new EmptyBorder(8, 8, 0, 0));
                tagHolder.add(tag, BorderLayout.WEST);
                top.add(tagHolder, BorderLayout.NORTH);
            }
            add(top, BorderLayout.NORTH);

            JPanel body = new JPanel();
            body.setOpaque(false);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            body.setBorder(new EmptyBorder(10, 12, 10, 12));
            JPanel title = new JPanel();
            title.setOpaque(false);
            title.setLayout(new BoxLayout(title, BoxLayout.X_AXIS));
            JLabel name = new JLabel(preset.getName());
            name.setFont(Ui.weighted(Tokens.FS_BODY, Tokens.FW_TITLE));
            title.add(name);
            if (!preset.getVendor().isEmpty()) {
                title.add(Box.createHorizontalStrut(6));
                JLabel vendor = Ui.t2(preset.getVendor());
                vendor.setFont(Ui.font(Tokens.FS_SMALL));
                title.add(vendor);
            }
            title.add(Box.createHorizontalGlue());
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(title);
            List<String> lines = summary == null ? new ArrayList<>() : lines(summary);
            if (!preset.isBuiltIn()) {
                lines.add(String.format(Translations.getString(preset.getSource().equals("current") //$NON-NLS-1$
                        ? "MachineSettings.Presets.FromCurrent" : "MachineSettings.Presets.FromFolder"), //$NON-NLS-1$ //$NON-NLS-2$
                        preset.getCreated()));
            }
            for (String line : lines) {
                body.add(Box.createVerticalStrut(3));
                JLabel label = Ui.t2(line);
                label.setFont(Ui.font(Tokens.FS_AUX));
                label.setToolTipText(line);
                label.setAlignmentX(Component.LEFT_ALIGNMENT);
                body.add(label);
            }
            if (preset.isBuiltIn() && !preset.getDescription().isEmpty()) {
                body.add(Box.createVerticalStrut(4));
                javax.swing.JTextArea description = Forms.paragraph(preset.getDescription());
                description.setFont(Ui.font(Tokens.FS_AUX));
                description.setForeground(Ui.text2());
                description.setAlignmentX(Component.LEFT_ALIGNMENT);
                body.add(description);
            }
            add(body, BorderLayout.CENTER);

            JPanel actions = new JPanel();
            actions.setOpaque(false);
            actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
            actions.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                    javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.border()),
                    new EmptyBorder(8, 12, 8, 12)));
            if (current) {
                JButton using = Ui.button(Translations.getString("MachineSettings.Presets.InUse"), Ui.iconSm("check"), //$NON-NLS-1$ //$NON-NLS-2$
                        Ui.Size.Sm, Ui.Variant.Default);
                using.setEnabled(false);
                using.setToolTipText(Translations.getString("MachineSettings.Presets.InUse.ToolTip")); //$NON-NLS-1$
                actions.add(using);
            }
            else {
                JButton apply = Ui.button(Translations.getString("MachineSettings.Presets.Apply"), null, //$NON-NLS-1$
                        Ui.Size.Sm, Ui.Variant.Primary);
                apply.setToolTipText(Translations.getString("MachineSettings.Presets.Apply.ToolTip")); //$NON-NLS-1$
                apply.addActionListener(e -> apply(preset));
                actions.add(apply);
            }
            if (!preset.isBuiltIn()) {
                actions.add(Box.createHorizontalStrut(6));
                JButton rename = Ui.button(Translations.getString("MachineSettings.Presets.Rename"), Ui.iconSm("edit"), //$NON-NLS-1$ //$NON-NLS-2$
                        Ui.Size.Sm, Ui.Variant.Ghost);
                rename.setToolTipText(Translations.getString("MachineSettings.Presets.Rename.ToolTip")); //$NON-NLS-1$
                rename.addActionListener(e -> rename(preset));
                actions.add(rename);
                actions.add(Box.createHorizontalGlue());
                JButton more = Ui.iconButton(Ui.iconSm("more"), Ui.Size.Xs, Ui.Variant.Ghost, //$NON-NLS-1$
                        Translations.getString("MachineSettings.Presets.More")); //$NON-NLS-1$
                more.addActionListener(e -> {
                    JPopupMenu menu = new JPopupMenu();
                    JMenuItem folder = new JMenuItem(Translations.getString("MachineSettings.Presets.ShowFolder")); //$NON-NLS-1$
                    folder.addActionListener(a -> UiUtils.openFolder(more, preset.getDirectory()));
                    menu.add(folder);
                    JMenuItem delete = new JMenuItem(Translations.getString("MachineSettings.Presets.Delete")); //$NON-NLS-1$
                    delete.addActionListener(a -> delete(preset));
                    menu.add(delete);
                    menu.show(more, 0, more.getHeight());
                });
                actions.add(more);
            }
            else {
                actions.add(Box.createHorizontalGlue());
            }
            add(actions, BorderLayout.SOUTH);
        }
    }

    /** The dashed card after the user's presets: a new one. */
    private final class AddCard extends JPanel {
        AddCard() {
            super(new BorderLayout());
            setOpaque(false);
            JPanel column = new JPanel();
            column.setOpaque(false);
            column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
            RoundedPanel plus = new RoundedPanel(12, Ui::accentSoft, () -> null);
            plus.setLayout(new BorderLayout());
            plus.add(new JLabel(Ui.icon("plus", 18, Ui.accent()), JLabel.CENTER)); //$NON-NLS-1$
            plus.setPreferredSize(new Dimension(40, 40));
            plus.setMaximumSize(new Dimension(40, 40));
            plus.setAlignmentX(Component.CENTER_ALIGNMENT);
            column.add(Box.createVerticalGlue());
            column.add(plus);
            column.add(Box.createVerticalStrut(8));
            JLabel title = new JLabel(Translations.getString("MachineSettings.Presets.New")); //$NON-NLS-1$
            title.setFont(Ui.weighted(Tokens.FS_BODY, Tokens.FW_SECTION));
            title.setAlignmentX(Component.CENTER_ALIGNMENT);
            column.add(title);
            column.add(Box.createVerticalStrut(2));
            JLabel text = Ui.t2(Translations.getString("MachineSettings.Presets.New.Text")); //$NON-NLS-1$
            text.setFont(Ui.font(Tokens.FS_SMALL));
            text.setAlignmentX(Component.CENTER_ALIGNMENT);
            column.add(text);
            column.add(Box.createVerticalGlue());
            add(column, BorderLayout.CENTER);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(Translations.getString("MachineSettings.Presets.New.Text")); //$NON-NLS-1$
            setPreferredSize(new Dimension(10, 200));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    create(false);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Ui.borderStrong());
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                        new float[] { 5f, 4f }, 0f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 2 * Tokens.R_MD, 2 * Tokens.R_MD);
            }
            finally {
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    /**
     * A machine from above, as the stylesheet's preset cards draw it: the frame, the feeders along
     * the back, the gantry with the head and its nozzles, the board and the bottom camera.
     */
    private static final class MachinePicture extends JComponent {
        private final int nozzles;
        private final boolean current;

        MachinePicture(int nozzles, boolean current) {
            this.nozzles = nozzles;
            this.current = current;
            setPreferredSize(new Dimension(10, 92));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = 150;
                int h = 80;
                g2.translate((getWidth() - w) / 2, (getHeight() - h) / 2);
                Color line = current ? Ui.accent() : Ui.muted();
                g2.setColor(line);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawRoundRect(5, 5, 140, 70, 10, 10);
                g2.drawLine(14, 9, 14, 71);
                g2.drawLine(136, 9, 136, 71);
                for (int i = 0; i < 9; i++) {
                    g2.drawRoundRect(24 + (int) Math.round(i * 11.5), 10, 8, 12, 3, 3);
                }
                g2.drawRoundRect(10, 33, 130, 9, 4, 4);
                int headWidth = nozzles > 1 ? 22 : 14;
                int headX = nozzles > 1 ? 66 : 70;
                g2.setColor(Ui.alpha(line, 0.22));
                g2.fillRoundRect(headX, 29, headWidth, 17, 6, 6);
                g2.setColor(line);
                g2.drawRoundRect(headX, 29, headWidth, 17, 6, 6);
                if (nozzles > 1) {
                    g2.drawOval(70, 48, 5, 5);
                    g2.drawOval(80, 48, 5, 5);
                }
                else {
                    g2.drawOval(75, 48, 5, 5);
                }
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                        new float[] { 3f, 2.5f }, 0f));
                g2.drawRoundRect(26, 55, 62, 14, 4, 4);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(102, 58, 8, 8);
                g2.drawOval(104, 60, 3, 3);
            }
            finally {
                g2.dispose();
            }
        }
    }

    // ---- making, renaming, deleting ------------------------------------------------------------

    /** The new preset dialog, the mockups' 31: from this machine, or imported from a configuration's folder. */
    void create(boolean fromCurrentOnly) {
        JTextField name = Forms.input(new JTextField(machine.getPresetName() == null ? "" //$NON-NLS-1$
                : String.format(Translations.getString("MachineSettings.Presets.New.CopyOf"), machine.getPresetName())), false); //$NON-NLS-1$
        JTextField description = Forms.input(new JTextField(), false);
        List<Object> sources = Arrays.asList("current", "folder"); //$NON-NLS-1$ //$NON-NLS-2$
        Forms.Segmented source = new Forms.Segmented(sources,
                s -> Translations.getString("MachineSettings.Presets.New.Source." + s)); //$NON-NLS-1$
        source.setSelectedItem("current"); //$NON-NLS-1$
        source.setEnabled(!fromCurrentOnly);
        JTextField folder = Forms.input(new JTextField(), true);
        folder.setEditable(false);
        folder.setToolTipText(Translations.getString("MachineSettings.Presets.New.Folder.ToolTip")); //$NON-NLS-1$
        JButton browse = Ui.button(Translations.getString("MachineSettings.Presets.New.Browse"), Ui.iconSm("folder"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Default);
        browse.setToolTipText(Translations.getString("MachineSettings.Presets.New.Folder.ToolTip")); //$NON-NLS-1$
        JLabel found = Ui.t2(""); //$NON-NLS-1$
        found.setFont(Ui.font(Tokens.FS_AUX));
        Forms.Toggle vision = new Forms.Toggle();
        vision.setSelected(true);
        Forms.Toggle records = new Forms.Toggle();
        Forms.Toggle feeders = new Forms.Toggle();

        Forms.Grid grid = new Forms.Grid(72);
        grid.row(Translations.getString("MachineSettings.Presets.New.Name"), name); //$NON-NLS-1$
        grid.row(Translations.getString("MachineSettings.Presets.New.Description"), description); //$NON-NLS-1$
        JPanel sourceRow = Forms.row(source);
        sourceRow.add(Box.createHorizontalGlue());
        grid.row(Translations.getString("MachineSettings.Presets.New.Source"), sourceRow); //$NON-NLS-1$
        JPanel folderRow = new JPanel(new BorderLayout(6, 0));
        folderRow.setOpaque(false);
        folderRow.add(folder, BorderLayout.CENTER);
        folderRow.add(browse, BorderLayout.EAST);
        grid.row(Translations.getString("MachineSettings.Presets.New.Folder"), folderRow); //$NON-NLS-1$
        grid.row("", found); //$NON-NLS-1$

        JPanel includes = new JPanel();
        includes.setOpaque(false);
        includes.setLayout(new BoxLayout(includes, BoxLayout.Y_AXIS));
        JLabel includesTitle = new JLabel(Translations.getString("MachineSettings.Presets.New.Includes")); //$NON-NLS-1$
        includesTitle.setFont(Ui.weighted(Tokens.FS_BODY, Tokens.FW_SECTION));
        includesTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        includes.add(includesTitle);
        for (String always : new String[] { "Axes", "Nozzles", "Cameras", "Connection" }) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            Forms.Toggle on = new Forms.Toggle();
            on.setSelected(true);
            on.setEnabled(false);
            on.setToolTipText(Translations.getString("MachineSettings.Presets.New.Includes.Always")); //$NON-NLS-1$
            includes.add(Box.createVerticalStrut(6));
            includes.add(toggleLine(on, Translations.getString("MachineSettings.Presets.New.Includes." + always))); //$NON-NLS-1$
        }
        for (Object[] option : new Object[][] { { vision, "Vision" }, { records, "Records" }, { feeders, "Feeders" } }) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            includes.add(Box.createVerticalStrut(6));
            includes.add(toggleLine((Forms.Toggle) option[0],
                    Translations.getString("MachineSettings.Presets.New.Includes." + option[1]))); //$NON-NLS-1$
        }
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(grid, BorderLayout.NORTH);
        body.add(includes, BorderLayout.CENTER);

        JButton[] asked = new JButton[1];
        File[] chosen = new File[1];
        Runnable validate = () -> {
            boolean current = "current".equals(source.getSelectedItem()); //$NON-NLS-1$
            folder.setEnabled(!current);
            browse.setEnabled(!current);
            if (current) {
                folder.setText(configurationDirectory().getPath());
                found.setText(Translations.getString("MachineSettings.Presets.New.FromCurrent")); //$NON-NLS-1$
                found.setForeground(Ui.text2());
            }
            else if (chosen[0] == null) {
                folder.setText(""); //$NON-NLS-1$
                found.setText(Translations.getString("MachineSettings.Presets.New.ChooseFolder")); //$NON-NLS-1$
                found.setForeground(Ui.text2());
            }
            else if (!MachinePresets.isConfiguration(chosen[0])) {
                folder.setText(chosen[0].getPath());
                found.setText(Translations.getString("MachineSettings.Presets.New.NotConfiguration")); //$NON-NLS-1$
                found.setForeground(Ui.errText());
            }
            else {
                folder.setText(chosen[0].getPath());
                String what = ""; //$NON-NLS-1$
                try {
                    what = nozzles(MachinePresets.summary(chosen[0]));
                }
                catch (Exception e) {
                    Logger.warn(e, "The configuration in {} could not be read.", chosen[0]); //$NON-NLS-1$
                }
                found.setText(String.format(Translations.getString("MachineSettings.Presets.New.Found"), what)); //$NON-NLS-1$
                found.setForeground(Ui.okText());
            }
            if (asked[0] != null) {
                asked[0].setEnabled(!name.getText().trim().isEmpty()
                        && (current || MachinePresets.isConfiguration(chosen[0])));
            }
        };
        source.onChange(validate);
        name.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                validate.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                validate.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                validate.run();
            }
        });
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(chosen[0] != null ? chosen[0] : configurationDirectory());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle(Translations.getString("MachineSettings.Presets.New.Folder")); //$NON-NLS-1$
            if (chooser.showOpenDialog(browse) == JFileChooser.APPROVE_OPTION) {
                chosen[0] = chooser.getSelectedFile();
                validate.run();
            }
        });
        validate.run();

        Dialogs.Content content = new Dialogs.Content().tone(Dialogs.Tone.Info, "plus") //$NON-NLS-1$
                .title(Translations.getString("MachineSettings.Presets.New.Title")) //$NON-NLS-1$
                .body(body)
                .more(Translations.getString("MachineSettings.Presets.New.Where")) //$NON-NLS-1$
                .width(640)
                .asked(button -> {
                    asked[0] = button;
                    button.setToolTipText(Translations.getString("MachineSettings.Presets.New.Save.ToolTip")); //$NON-NLS-1$
                    validate.run();
                });
        int answer = Dialogs.show(page, content, Arrays.asList(Dialogs.Choice.cancel(),
                Dialogs.Choice.primary(Translations.getString("MachineSettings.Presets.New.Save"))), 0, 1); //$NON-NLS-1$
        if (answer != 1) {
            return;
        }
        boolean current = "current".equals(source.getSelectedItem()); //$NON-NLS-1$
        UiUtils.messageBoxOnException(() -> {
            if (current) {
                page.getConfiguration().save();
            }
            MachinePresets.Options options = new MachinePresets.Options();
            options.vision = vision.isSelected();
            options.records = records.isSelected();
            options.feeders = feeders.isSelected();
            MachinePreset made = MachinePresets.create(configurationDirectory(),
                    current ? configurationDirectory() : chosen[0], current ? "current" : chosen[0].getPath(), //$NON-NLS-1$
                    name.getText(), description.getText(), options);
            page.getFrame().setStatus(String.format(Translations.getString("MachineSettings.Presets.New.Done"), made.getName())); //$NON-NLS-1$
            refresh();
        });
    }

    /** A switch with its words beside it, on as many lines as the dialog's width leaves them. */
    private static JComponent toggleLine(Forms.Toggle toggle, String text) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(toggle, BorderLayout.NORTH);
        row.add(holder, BorderLayout.WEST);
        javax.swing.JTextArea words = Forms.paragraph(text);
        words.setFont(Ui.font(Tokens.FS_SMALL));
        words.setForeground(Ui.text2());
        row.add(words, BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private void rename(MachinePreset preset) {
        JTextField name = Forms.input(new JTextField(preset.getName()), false);
        JButton[] asked = new JButton[1];
        name.getDocument().addDocumentListener(new DocumentListener() {
            private void check() {
                if (asked[0] != null) {
                    asked[0].setEnabled(!name.getText().trim().isEmpty());
                }
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                check();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                check();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                check();
            }
        });
        Dialogs.Content content = new Dialogs.Content().tone(Dialogs.Tone.Info, "edit") //$NON-NLS-1$
                .title(String.format(Translations.getString("MachineSettings.Presets.Rename.Title"), preset.getName())) //$NON-NLS-1$
                .body(name)
                .asked(button -> asked[0] = button);
        int answer = Dialogs.show(page, content, Arrays.asList(Dialogs.Choice.cancel(),
                Dialogs.Choice.primary(Translations.getString("MachineSettings.Presets.Rename"))), 0, 1); //$NON-NLS-1$
        if (answer == 1 && !name.getText().trim().isEmpty()) {
            UiUtils.messageBoxOnException(() -> {
                MachinePresets.rename(preset, name.getText());
                refresh();
            });
        }
    }

    private void delete(MachinePreset preset) {
        if (Dialogs.confirmDanger(page,
                String.format(Translations.getString("MachineSettings.Presets.Delete.Title"), preset.getName()), //$NON-NLS-1$
                Translations.getString("MachineSettings.Presets.Delete.What"), //$NON-NLS-1$
                preset.getDirectory().getPath(), null,
                Translations.getString("MachineSettings.Presets.Delete"))) { //$NON-NLS-1$
            UiUtils.messageBoxOnException(() -> {
                MachinePresets.delete(preset);
                refresh();
            });
        }
    }

    // ---- applying ------------------------------------------------------------------------------

    /** Applies the built-in or user preset of that name, as its card's Apply does. */
    void apply(String name) {
        List<MachinePreset> all = new ArrayList<>(MachinePresets.builtIn());
        all.addAll(MachinePresets.user(configurationDirectory()));
        for (MachinePreset preset : all) {
            if (preset.getName().equals(name)) {
                apply(preset);
                return;
            }
        }
    }

    /**
     * What the preset changes, said first against the machine as it was last saved; then the
     * questions of quitting, the configuration saved and backed up, the preset written over it,
     * and Pono started again.
     */
    private void apply(MachinePreset preset) {
        PresetXml.Summary now;
        PresetXml.Summary then;
        try {
            page.getConfiguration().save();
            now = MachinePresets.summary(configurationDirectory());
            then = MachinePresets.summary(preset);
        }
        catch (Exception e) {
            UiUtils.showError(e);
            return;
        }
        List<String> rows = new ArrayList<>();
        rows.add(row("Nozzles", nozzles(now), nozzles(then))); //$NON-NLS-1$
        rows.add(row("Tips", tips(now), tips(then))); //$NON-NLS-1$
        rows.add(row("Travel", travel(now), travel(then))); //$NON-NLS-1$
        rows.add(row("Cameras", String.join("\u3001", now.cameras), String.join("\u3001", then.cameras))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        rows.add(row("Controller", controller(now, true, null), controller(then, true, now.port))); //$NON-NLS-1$
        rows.add(row("Vision", Translations.getString("MachineSettings.Presets.Apply.Vision.Now"), //$NON-NLS-1$ //$NON-NLS-2$
                Translations.getString(preset.hasVision() ? "MachineSettings.Presets.Apply.Vision.Merged" //$NON-NLS-1$
                        : "MachineSettings.Presets.Apply.Vision.Kept"))); //$NON-NLS-1$
        rows.add(row("Feeders", String.valueOf(now.feeders), //$NON-NLS-1$
                String.format(Translations.getString("MachineSettings.Presets.Apply.Feeders.Kept"), now.feeders))); //$NON-NLS-1$
        Dialogs.Content content = new Dialogs.Content().tone(Dialogs.Tone.Warn, "layers") //$NON-NLS-1$
                .title(String.format(Translations.getString("MachineSettings.Presets.Apply.Title"), preset.getName())) //$NON-NLS-1$
                .what(Translations.getString("MachineSettings.Presets.Apply.What")) //$NON-NLS-1$
                .list(String.join("\n", rows)) //$NON-NLS-1$
                .more(Translations.getString("MachineSettings.Presets.Apply.More")) //$NON-NLS-1$
                .width(680);
        int answer = Dialogs.show(page, content, Arrays.asList(Dialogs.Choice.cancel(),
                Dialogs.Choice.primary(Translations.getString("MachineSettings.Presets.Apply.Do"))), 0, 1); //$NON-NLS-1$
        if (answer != 1 || !page.getFrame().settleBeforeQuit()) {
            return;
        }
        File directory = configurationDirectory();
        List<File> backups = new ArrayList<>();
        try {
            page.getConfiguration().save();
            backups.addAll(Backups.backupMachineFiles(page.getConfiguration(), "preset")); //$NON-NLS-1$
            MachinePresets.apply(preset, directory);
        }
        catch (Exception e) {
            // What was written is taken back, so that the program and the files still agree.
            Backups.restore(directory, backups);
            UiUtils.showError(e);
            return;
        }
        page.getFrame().restart();
    }

    private static String row(String what, String now, String then) {
        return String.format(Translations.getString("MachineSettings.Presets.Apply.Row"), //$NON-NLS-1$
                Translations.getString("MachineSettings.Presets.Apply.Row." + what), now, then); //$NON-NLS-1$
    }

    private static String tips(PresetXml.Summary summary) {
        if (summary.tips.isEmpty()) {
            return "\u2014"; //$NON-NLS-1$
        }
        String names = summary.tips.size() <= 3 ? String.join("\u3001", summary.tips) //$NON-NLS-1$
                : summary.tips.get(0) + " \u2026 " + summary.tips.get(summary.tips.size() - 1); //$NON-NLS-1$
        return String.format(Translations.getString("MachineSettings.Presets.Tips"), summary.tips.size()) + " \u00b7 " + names; //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static String travel(PresetXml.Summary summary) {
        if (summary.travelX == null || summary.travelY == null) {
            return Translations.getString("MachineSettings.Presets.NoLimits"); //$NON-NLS-1$
        }
        return String.format(Locale.US, "%.0f \u00d7 %.0f mm", summary.travelX, summary.travelY); //$NON-NLS-1$
    }
}
