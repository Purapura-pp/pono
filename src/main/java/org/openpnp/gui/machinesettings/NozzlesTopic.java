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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;

import org.openpnp.Translations;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.gui.shell.DockRenderers;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.RoundedPanel;
import org.openpnp.gui.shell.Tokens;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.TableUtils;
import org.openpnp.machine.reference.ReferenceHead;
import org.openpnp.machine.reference.ReferenceHead.NozzleSolution;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.ReferenceNozzle;
import org.openpnp.machine.reference.ReferenceNozzleTip;
import org.openpnp.machine.reference.solutions.HeadSolutions;
import org.openpnp.machine.reference.solutions.HeadSolutions.NozzlePlan;
import org.openpnp.machine.reference.wizards.HeadForm;
import org.openpnp.machine.reference.wizards.NozzleForm;
import org.openpnp.machine.reference.wizards.NozzleTipForm;
import org.openpnp.model.Length;
import org.openpnp.model.Location;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.NozzleTip;
import org.openpnp.spi.base.AbstractHead.VacuumPumpControl;
import org.openpnp.util.UiUtils;

/**
 * The nozzles and their tips, the mockups' 26: how many nozzles and how they move - read off the
 * axes, and changed only after the page has said what that makes and deletes - their offsets with
 * the way to measure them by hand, the pump, and the tips' sizes.
 */
final class NozzlesTopic extends Topic {
    private static final List<NozzleSolution> SOLUTIONS = Arrays.asList(NozzleSolution.Standalone,
            NozzleSolution.DualNegated, NozzleSolution.DualCam);

    private final MachineSettingsPanel page;
    private final ReferenceMachine machine;
    private ReferenceHead head;
    private NozzleStructure structure;
    private Forms.Segmented count;
    private final List<Card> cards = new ArrayList<>();
    private NozzleSolution chosen;
    private JButton change;

    private final List<ReferenceNozzle> nozzles = new ArrayList<>();
    private final NozzleModel nozzleModel = new NozzleModel();
    private final JPanel nozzleHolder = new JPanel(new BorderLayout());
    private JTable nozzleTable;
    private FormWizard nozzleForm;
    private ReferenceNozzle nozzle;

    private final List<ReferenceNozzleTip> tips = new ArrayList<>();
    private final TipModel tipModel = new TipModel();
    private final JPanel tipHolder = new JPanel(new BorderLayout());
    private final JPanel tipCheck = new JPanel(new BorderLayout());
    private JTable tipTable;
    private FormWizard tipForm;
    private ReferenceNozzleTip tip;
    private boolean reverting;

    NozzlesTopic(MachineSettingsPanel page, ReferenceMachine machine) {
        super(MachineSettingsPanel.NOZZLES, "nozzle"); //$NON-NLS-1$
        this.page = page;
        this.machine = machine;
    }

    @Override
    protected JComponent build() {
        try {
            head = machine.getDefaultHead() instanceof ReferenceHead ? (ReferenceHead) machine.getDefaultHead() : null;
        }
        catch (Exception e) {
            head = null;
        }
        if (head == null) {
            return Forms.emptyState("nozzle", title(), Translations.getString("MachineSettings.Nozzles.NoHead")); //$NON-NLS-1$ //$NON-NLS-2$
        }
        List<JComponent> sections = new ArrayList<>();
        sections.add(structure());
        sections.add(nozzleList());
        sections.add(nozzleHolder);
        sections.add(forms.add(pump()));
        sections.add(tipList());
        sections.add(tipHolder);
        forms.onChange(() -> {
            if (!nozzles.isEmpty()) {
                nozzleModel.fireTableRowsUpdated(0, nozzles.size() - 1);
            }
            if (!tips.isEmpty()) {
                tipModel.fireTableRowsUpdated(0, tips.size() - 1);
            }
        });
        JButton calibration = Ui.button(Translations.getString("MachineSettings.Guide.ToCalibration"), //$NON-NLS-1$
                Ui.iconSm("target"), Ui.Size.Sm, Ui.Variant.Ghost); //$NON-NLS-1$
        calibration.addActionListener(e -> page.getFrame().showCalibrationStep(null, null));
        JComponent view = MachineSettingsPanel.page(
                Guide.of(Translations.getString("MachineSettings.Guide.Nozzles"), calibration), //$NON-NLS-1$
                sections.toArray(new JComponent[0]));
        if (!nozzles.isEmpty()) {
            nozzleTable.setRowSelectionInterval(0, 0);
        }
        if (!tips.isEmpty()) {
            tipTable.setRowSelectionInterval(0, 0);
        }
        return view;
    }

    @Override
    void shown() {
        describeTipCheck();
    }

    // ---- how many and how they move -----------------------------------------------------------

    private JComponent structure() {
        structure = NozzleStructure.of(head);
        chosen = structure.solution == null ? NozzleSolution.Standalone : structure.solution;
        Forms.Section section = new Forms.Section("nozzle", //$NON-NLS-1$
                Translations.getString("MachineSettings.Nozzles.Structure")) //$NON-NLS-1$
                .withRight(String.format(Translations.getString("MachineSettings.Nozzles.Recognized"), //$NON-NLS-1$
                        structure.nozzles, structure.name()));
        List<Object> counts = new ArrayList<>(Arrays.asList(1, 2, 3, 4));
        if (structure.nozzles > 4) {
            counts.add(structure.nozzles);
        }
        count = new Forms.Segmented(counts, String::valueOf);
        count.setSelectedItem(Math.max(1, structure.nozzles));
        count.onChange(this::describeStructure);
        JPanel options = new JPanel(new GridLayout(1, 3, 10, 0));
        options.setOpaque(false);
        for (NozzleSolution solution : SOLUTIONS) {
            Card card = new Card(solution);
            cards.add(card);
            options.add(card);
        }
        change = Ui.button(Translations.getString("MachineSettings.Nozzles.Change"), Ui.iconSm("edit"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Primary);
        change.setToolTipText(Translations.getString("MachineSettings.Nozzles.Change.Note")); //$NON-NLS-1$
        change.addActionListener(e -> changeStructure());
        JPanel countRow = Forms.row(count);
        countRow.add(Box.createHorizontalGlue());
        JPanel changeRow = Forms.row(change, Ui.muted(Translations.getString("MachineSettings.Nozzles.Change.Note"))); //$NON-NLS-1$
        changeRow.add(Box.createHorizontalGlue());
        Forms.Grid grid = new Forms.Grid(Tokens.FORM_LABEL);
        grid.row(Translations.getString("MachineSettings.Nozzles.Count"), countRow); //$NON-NLS-1$
        grid.row(Translations.getString("MachineSettings.Nozzles.How"), options); //$NON-NLS-1$
        grid.row("", changeRow); //$NON-NLS-1$
        section.content(grid);
        describeStructure();
        return section;
    }

    private int chosenCount() {
        Object selected = count.getSelectedItem();
        return selected instanceof Integer ? (Integer) selected : 1;
    }

    /** A pair of nozzles on one motor needs an even number of them; one nozzle is only standalone. */
    private void describeStructure() {
        int n = chosenCount();
        if (n % 2 != 0 && chosen != NozzleSolution.Standalone) {
            chosen = NozzleSolution.Standalone;
        }
        for (Card card : cards) {
            card.setEnabled(card.solution == NozzleSolution.Standalone || n % 2 == 0);
            card.setOn(card.solution == chosen);
        }
        change.setEnabled(chosen != structure.solution || n != structure.nozzles);
    }

    /** Says what the change would make, rename and delete, backs the configuration up, and makes it. */
    private void changeStructure() {
        if (machine.isEnabled()) {
            Dialogs.info(change, Translations.getString("MachineSettings.Nozzles.Change.Disable.Title"), //$NON-NLS-1$
                    Translations.getString("MachineSettings.Nozzles.Change.Disable")); //$NON-NLS-1$
            return;
        }
        int n = chosenCount();
        int multiplier = chosen == NozzleSolution.Standalone ? n : n / 2;
        HeadSolutions solutions = new HeadSolutions(head);
        NozzlePlan plan = solutions.planNozzleSolution(chosen, multiplier);
        List<String> lines = new ArrayList<>();
        for (NozzlePlan.Item item : plan.created) {
            lines.add(String.format(Translations.getString("MachineSettings.Nozzles.Plan.Create"), kind(item), item.to)); //$NON-NLS-1$
        }
        for (NozzlePlan.Item item : plan.renamed) {
            lines.add(String.format(Translations.getString("MachineSettings.Nozzles.Plan.Rename"), kind(item), item.from, item.to)); //$NON-NLS-1$
        }
        for (NozzlePlan.Item item : plan.remapped) {
            lines.add(String.format(Translations.getString("MachineSettings.Nozzles.Plan.Remap"), item.to, item.from)); //$NON-NLS-1$
        }
        for (NozzlePlan.Item item : plan.removed) {
            lines.add(String.format(Translations.getString("MachineSettings.Nozzles.Plan.Remove"), kind(item), item.from)); //$NON-NLS-1$
        }
        String target = n == 1 ? Translations.getString("MachineSettings.Head.OneNozzle") //$NON-NLS-1$
                : String.format(Translations.getString("MachineSettings.Head.Nozzles"), n, NozzleStructure.name(chosen)); //$NON-NLS-1$
        Dialogs.Content content = new Dialogs.Content().tone(Dialogs.Tone.Warn, "nozzle") //$NON-NLS-1$
                .title(String.format(Translations.getString("MachineSettings.Nozzles.Change.Title"), target)) //$NON-NLS-1$
                .what(Translations.getString("MachineSettings.Nozzles.Change.What")) //$NON-NLS-1$
                .list(String.join("\n", lines)) //$NON-NLS-1$
                .more(Translations.getString("MachineSettings.Nozzles.Change.More")); //$NON-NLS-1$
        int answer = Dialogs.show(change, content, Arrays.asList(Dialogs.Choice.cancel(),
                Dialogs.Choice.primary(Translations.getString("MachineSettings.Nozzles.Change.Do"))), 0, 1); //$NON-NLS-1$
        if (answer != 1) {
            return;
        }
        UiUtils.messageBoxOnException(() -> {
            Backups.backup(page.getConfiguration(), "nozzles"); //$NON-NLS-1$
            solutions.applyNozzleSolution(chosen, multiplier);
            page.rebuild();
        });
    }

    private static String kind(NozzlePlan.Item item) {
        return Translations.getString("MachineSettings.Nozzles.Plan." + item.kind.name()); //$NON-NLS-1$
    }

    /** One of the three ways, the stylesheet's {@code .opt}: its diagram, its name and a line. */
    private final class Card extends RoundedPanel {
        final NozzleSolution solution;
        /** Whether it is the chosen one, which the fill and the outline read on every paint. */
        private final boolean[] on;

        Card(NozzleSolution solution) {
            this(solution, new boolean[1]);
        }

        private Card(NozzleSolution solution, boolean[] on) {
            super(Tokens.R_MD, () -> on[0] ? Ui.accentSoft() : Ui.surface2(),
                    () -> on[0] ? Ui.accent() : Ui.border());
            this.solution = solution;
            this.on = on;
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(8, 10, 8, 10));
            Icon picture = Icons.getIcon(solution == NozzleSolution.Standalone ? "/icons/diagrams/nozzle-single.svg" //$NON-NLS-1$
                    : solution == NozzleSolution.DualNegated ? "/icons/diagrams/nozzle-neg.svg" //$NON-NLS-1$
                            : "/icons/diagrams/nozzle-cam.svg", 44, 44); //$NON-NLS-1$
            add(new JLabel(picture), BorderLayout.WEST);
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            JLabel name = new JLabel(NozzleStructure.name(solution));
            name.setFont(Ui.weighted(Tokens.FS_SMALL + 0.5f, Tokens.FW_SECTION));
            name.setAlignmentX(LEFT_ALIGNMENT);
            text.add(name);
            text.add(Box.createVerticalStrut(2));
            // Two lines where the card is narrow, rather than cut: the words are what tells them apart.
            javax.swing.JTextArea line = Forms.paragraph(Translations.getString("MachineSettings.Nozzles.How." + solution.name())); //$NON-NLS-1$
            line.setFont(Ui.font(Tokens.FS_AUX));
            line.setForeground(Ui.text2());
            line.setAlignmentX(LEFT_ALIGNMENT);
            text.add(line);
            add(text, BorderLayout.CENTER);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(10, 72));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (isEnabled()) {
                        chosen = solution;
                        describeStructure();
                    }
                }
            });
        }

        void setOn(boolean chosen) {
            on[0] = chosen;
            repaint();
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            for (java.awt.Component child : getComponents()) {
                child.setEnabled(enabled);
            }
            setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        }
    }

    // ---- the nozzles --------------------------------------------------------------------------

    private JComponent nozzleList() {
        nozzles.clear();
        for (Nozzle each : head.getNozzles()) {
            if (each instanceof ReferenceNozzle) {
                nozzles.add((ReferenceNozzle) each);
            }
        }
        nozzleTable = new JTable(nozzleModel);
        nozzleTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableUtils.setColumnKinds(nozzleTable, TableUtils.Kind.Id, TableUtils.Kind.Secondary, TableUtils.Kind.Secondary,
                TableUtils.Kind.Number, TableUtils.Kind.Number, TableUtils.Kind.Number, TableUtils.Kind.Secondary,
                TableUtils.Kind.Name);
        JComponent box = Tables.boxed(nozzleTable);
        nozzleTable.getColumnModel().getColumn(0).setCellRenderer(DockRenderers.bold());
        for (int column = 3; column < 6; column++) {
            nozzleTable.getColumnModel().getColumn(column).setCellRenderer(DockRenderers.mono(String::valueOf));
        }
        nozzleTable.getSelectionModel().addListSelectionListener(e -> {
            int row = nozzleTable.getSelectedRow();
            if (!e.getValueIsAdjusting() && !reverting && row >= 0 && row < nozzles.size()) {
                selectNozzle(nozzles.get(row));
            }
        });
        nozzleHolder.setOpaque(false);
        return new Forms.Section("nozzle", Translations.getString("MachineSettings.Nozzles.Nozzles")) //$NON-NLS-1$ //$NON-NLS-2$
                .withRight(Translations.getString("MachineSettings.Motion.Axes.Note")) //$NON-NLS-1$
                .content(box);
    }

    /** The nozzle's offset, and the way to measure it with a mark and the camera, under the table. */
    private void selectNozzle(ReferenceNozzle selected) {
        if (selected == nozzle) {
            return;
        }
        if (!settle(nozzleForm, nozzle == null ? "" : nozzle.getName())) { //$NON-NLS-1$
            revert(nozzleTable, nozzles.indexOf(nozzle));
            return;
        }
        if (nozzleForm != null) {
            forms.remove(nozzleForm);
            nozzleForm.dispose();
        }
        nozzle = selected;
        nozzleForm = forms.add(NozzleForm.offset(selected));
        nozzleHolder.removeAll();
        nozzleHolder.add(nozzleForm, BorderLayout.CENTER);
        nozzleHolder.revalidate();
        nozzleHolder.repaint();
    }

    private final class NozzleModel extends AbstractTableModel {
        private final String[] columns = { "Nozzle", "AxisZ", "AxisC", "OffsetX", "OffsetY", "OffsetZ", "Vacuum", "Tip" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$

        @Override
        public int getRowCount() {
            return nozzles.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return Translations.getString("MachineSettings.Nozzles.Column." + columns[column]); //$NON-NLS-1$
        }

        @Override
        public Object getValueAt(int row, int column) {
            ReferenceNozzle n = nozzles.get(row);
            Location offsets = n.getHeadOffsets().convertToUnits(page.units());
            switch (column) {
                case 0:
                    return n.getName();
                case 1:
                    return n.getAxisZ() == null ? "\u2014" : n.getAxisZ().getName(); //$NON-NLS-1$
                case 2:
                    return n.getAxisRotation() == null ? "\u2014" : n.getAxisRotation().getName(); //$NON-NLS-1$
                case 3:
                    return String.format(Locale.US, "%.3f", offsets.getX()); //$NON-NLS-1$
                case 4:
                    return String.format(Locale.US, "%.3f", offsets.getY()); //$NON-NLS-1$
                case 5:
                    return String.format(Locale.US, "%.3f", offsets.getZ()); //$NON-NLS-1$
                case 6:
                    return n.getVacuumActuator() == null ? "\u2014" : n.getVacuumActuator().getName(); //$NON-NLS-1$
                default:
                    return n.getNozzleTip() == null ? "\u2014" : n.getNozzleTip().getName(); //$NON-NLS-1$
            }
        }
    }

    // ---- the pump -----------------------------------------------------------------------------

    /** The head's pump: none to control where every nozzle switches its own vacuum. */
    private FormWizard pump() {
        List<Actuator> actuators = new ArrayList<>();
        actuators.add(null);
        actuators.addAll(head.getActuators());
        FormWizard[] form = new FormWizard[1];
        form[0] = Form.of(new HeadForm.Bean(head)).named("HeadForm.Title") //$NON-NLS-1$
                .section("ReferenceHeadConfigurationWizard.PumpPanel.Border.title", "circle") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("vacuumPumpControl", "ReferenceHeadConfigurationWizard.PumpPanel.VacuumPumpControlLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        VacuumPumpControl.class)
                .hint("MachineSettings.Nozzles.Pump.Note") //$NON-NLS-1$
                .choice("pumpActuator", "ReferenceHeadConfigurationWizard.PumpPanel.VacuumPumpActuatorLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        actuators, null)
                .visibleWhen("vacuumPumpControl", v -> v != VacuumPumpControl.None) //$NON-NLS-1$
                .validate(v -> v != null || value(form, "vacuumPumpControl") == VacuumPumpControl.None, //$NON-NLS-1$
                        "MachineSettings.Check.NoPump.Fix") //$NON-NLS-1$
                .onApply(f -> page.refreshChecks())
                .build();
        return form[0];
    }

    // ---- the tips -----------------------------------------------------------------------------

    private JComponent tipList() {
        tips.clear();
        for (NozzleTip each : machine.getNozzleTips()) {
            if (each instanceof ReferenceNozzleTip) {
                tips.add((ReferenceNozzleTip) each);
            }
        }
        tipTable = new JTable(tipModel);
        tipTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableUtils.setColumnKinds(tipTable, TableUtils.Kind.Id, TableUtils.Kind.Name, TableUtils.Kind.Number,
                TableUtils.Kind.Number, TableUtils.Kind.Number, TableUtils.Kind.Secondary);
        JComponent box = Tables.boxed(tipTable);
        tipTable.getColumnModel().getColumn(0).setCellRenderer(DockRenderers.bold());
        tipTable.getColumnModel().getColumn(2).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(RIGHT);
                setFont(Ui.mono(12.5f, java.awt.Font.PLAIN));
                boolean bad = page.getChecks().about(SetupChecks.MIN_DIAMETER, tips.get(row)) != null
                        || page.getChecks().about(SetupChecks.MAX_DIAMETER, tips.get(row)) != null;
                setForeground(bad ? Ui.errText() : Ui.text2());
                setBorder(new EmptyBorder(0, 10, 0, 10));
                return this;
            }
        });
        for (int column = 3; column < 5; column++) {
            tipTable.getColumnModel().getColumn(column).setCellRenderer(DockRenderers.mono(String::valueOf));
        }
        tipTable.getSelectionModel().addListSelectionListener(e -> {
            int row = tipTable.getSelectedRow();
            if (!e.getValueIsAdjusting() && !reverting && row >= 0 && row < tips.size()) {
                selectTip(tips.get(row));
            }
        });
        tipHolder.setOpaque(false);
        tipCheck.setOpaque(false);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);
        content.add(tipCheck, BorderLayout.NORTH);
        content.add(box, BorderLayout.CENTER);
        describeTipCheck();
        return new Forms.Section("pkg", Translations.getString("MachineSettings.Nozzles.Tips")) //$NON-NLS-1$ //$NON-NLS-2$
                .withRight(String.format(Translations.getString("MachineSettings.Nozzles.Tips.Note"), tips.size())) //$NON-NLS-1$
                .content(content);
    }

    /** The tips' own problems over their table: the diameters calibration cannot work with. */
    private void describeTipCheck() {
        tipCheck.removeAll();
        for (String kind : new String[] { SetupChecks.PICK_TOLERANCE, SetupChecks.MIN_DIAMETER, SetupChecks.MAX_DIAMETER }) {
            for (SetupChecks.Check check : page.getChecks().of(MachineSettingsPanel.NOZZLES)) {
                if (check.kind.equals(kind)) {
                    JLabel line = new JLabel(check.text() + "\u3000" + check.fix(), Ui.icon("alert", 14, Ui.errText()), JLabel.LEFT); //$NON-NLS-1$ //$NON-NLS-2$
                    line.setForeground(Ui.errText());
                    line.setFont(Ui.font(Tokens.FS_AUX));
                    line.setIconTextGap(6);
                    tipCheck.add(line, BorderLayout.CENTER);
                }
            }
        }
        tipCheck.setVisible(tipCheck.getComponentCount() > 0);
        tipCheck.revalidate();
        if (tipTable != null) {
            tipTable.repaint();
        }
    }

    private void selectTip(ReferenceNozzleTip selected) {
        if (selected == tip) {
            return;
        }
        if (!settle(tipForm, tip == null ? "" : tip.getName())) { //$NON-NLS-1$
            revert(tipTable, tips.indexOf(tip));
            return;
        }
        if (tipForm != null) {
            forms.remove(tipForm);
            tipForm.dispose();
        }
        tip = selected;
        tipForm = forms.add(tipForm(selected));
        tipHolder.removeAll();
        tipHolder.add(tipForm, BorderLayout.CENTER);
        tipHolder.revalidate();
        tipHolder.repaint();
    }

    /**
     * A tip's sizes as the part handling and the calibration need them, checked against each other
     * as they are typed: the smallest part above twice the pick tolerance, the largest above it.
     */
    private FormWizard tipForm(ReferenceNozzleTip selected) {
        FormWizard[] form = new FormWizard[1];
        form[0] = Form.of(new NozzleTipForm.Bean(selected)).named(selected.getName())
                .section(String.format(Translations.getString("MachineSettings.Nozzles.Tip"), selected.getName()), "pkg") //$NON-NLS-1$ //$NON-NLS-2$
                .length("minPartDiameter", "NozzleTipForm.MinPartDiameter").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .validate(v -> selected.getCalibration() == null || !selected.getCalibration().isEnabled()
                        || form[0] == null
                        || SetupChecks.minimumValid(length(v), length(value(form, "maxPickTolerance"))), //$NON-NLS-1$
                        "MachineSettings.Check.MinDiameter.Fix") //$NON-NLS-1$
                .hint("NozzleTipForm.MinPartDiameter.Hint") //$NON-NLS-1$
                .length("maxPartDiameter", "NozzleTipForm.MaxPartDiameter").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .validate(v -> larger(length(v), length(value(form, "minPartDiameter"))), //$NON-NLS-1$
                        "MachineSettings.Check.MaxDiameter.Fix") //$NON-NLS-1$
                .length("maxPickTolerance", "NozzleTipForm.MaxPickTolerance").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("NozzleTipForm.MaxPickTolerance.Hint") //$NON-NLS-1$
                .integer("pickDwellMilliseconds", "NozzleForm.PickDwell").unit("ms").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .integer("placeDwellMilliseconds", "NozzleForm.PlaceDwell").unit("ms").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .onApply(f -> {
                    page.refreshChecks();
                    describeTipCheck();
                })
                .build();
        return form[0];
    }

    /** What a field of the form shows, or nothing while the form is still being built. */
    private static Object value(FormWizard[] form, String property) {
        return form[0] == null ? null : form[0].value(property);
    }

    private Length length(Object text) {
        if (text == null) {
            return null;
        }
        try {
            return Length.parseWithDefaultUnits(String.valueOf(text), page.units());
        }
        catch (Exception e) {
            return null;
        }
    }

    private static boolean larger(Length value, Length than) {
        return value == null || than == null || value.compareTo(than) > 0;
    }

    private final class TipModel extends AbstractTableModel {
        private final String[] columns = { "TipName", "Nozzles", "Diameter", "Tolerance", "Dwell", "Calibration" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$

        @Override
        public int getRowCount() {
            return tips.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return Translations.getString("MachineSettings.Nozzles.Column." + columns[column]); //$NON-NLS-1$
        }

        @Override
        public Object getValueAt(int row, int column) {
            ReferenceNozzleTip t = tips.get(row);
            String units = page.units().getShortName();
            switch (column) {
                case 0:
                    return t.getName();
                case 1: {
                    List<String> names = new ArrayList<>();
                    for (Nozzle n : head.getNozzles()) {
                        if (n.getCompatibleNozzleTips().contains(t)) {
                            names.add(n.getName());
                        }
                    }
                    return names.isEmpty() ? "\u2014" : String.join("\u3001", names); //$NON-NLS-1$ //$NON-NLS-2$
                }
                case 2:
                    return String.format(Locale.US, "%.3f \u2013 %.3f %s", //$NON-NLS-1$
                            t.getMinPartDiameter().convertToUnits(page.units()).getValue(),
                            t.getMaxPartDiameter().convertToUnits(page.units()).getValue(), units);
                case 3:
                    return String.format(Locale.US, "%.3f %s", //$NON-NLS-1$
                            t.getMaxPickTolerance().convertToUnits(page.units()).getValue(), units);
                case 4:
                    return t.getPickDwellMilliseconds() + " / " + t.getPlaceDwellMilliseconds() + " ms"; //$NON-NLS-1$ //$NON-NLS-2$
                default:
                    return Translations.getString(t.getCalibration() != null && t.getCalibration().isEnabled()
                            ? "Form.Change.On" : "Form.Change.Off"); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
    }

    // ---- shared -------------------------------------------------------------------------------

    /** Settles a form's unapplied edits before another row replaces it: false to stay where it is. */
    private boolean settle(FormWizard form, String name) {
        if (form == null || !form.hasEdits()) {
            return true;
        }
        int choice = MachineSettingsPanel.askUnapplied(nozzleHolder, name);
        if (choice == 1) {
            form.apply();
            return true;
        }
        if (choice == 0) {
            form.reset();
            return true;
        }
        return false;
    }

    private void revert(JTable table, int row) {
        if (row < 0) {
            return;
        }
        reverting = true;
        try {
            table.setRowSelectionInterval(row, row);
        }
        finally {
            reverting = false;
        }
    }
}
