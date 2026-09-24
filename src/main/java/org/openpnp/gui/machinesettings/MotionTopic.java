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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

import org.openpnp.Translations;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.DockRenderers;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.TableUtils;
import org.openpnp.machine.reference.ReferenceHead;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis;
import org.openpnp.machine.reference.axis.ReferenceMappedAxis;
import org.openpnp.machine.reference.axis.ReferenceVirtualAxis;
import org.openpnp.machine.reference.axis.wizards.AxisForm;
import org.openpnp.machine.reference.wizards.HeadForm;
import org.openpnp.machine.reference.wizards.MachineForm;
import org.openpnp.model.CalibrationStep;
import org.openpnp.model.Length;
import org.openpnp.spi.Axis;
import org.openpnp.spi.Head;

/**
 * The machine's axes, the mockups' 25: every axis in one table with its limits and speeds, the
 * selected one's settings under it, and where the head parks. What calibration measures - the
 * backlash - is shown with its source rather than edited here.
 */
final class MotionTopic extends Topic {
    private final MachineSettingsPanel page;
    private final ReferenceMachine machine;
    private final List<Axis> axes = new ArrayList<>();
    private final AxesModel model = new AxesModel();
    private final JPanel axisHolder = new JPanel(new BorderLayout());
    private JTable table;
    private FormWizard axisForm;
    private Axis selected;
    private boolean reverting;

    MotionTopic(MachineSettingsPanel page, ReferenceMachine machine) {
        super(MachineSettingsPanel.MOTION, "move"); //$NON-NLS-1$
        this.page = page;
        this.machine = machine;
    }

    @Override
    protected JComponent build() {
        axes.clear();
        for (Axis axis : machine.getAxes()) {
            // A virtual axis is a camera's Z or angle that no motor turns.
            if (!(axis instanceof ReferenceVirtualAxis)) {
                axes.add(axis);
            }
        }
        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        TableUtils.setColumnKinds(table, TableUtils.Kind.Id, TableUtils.Kind.Secondary, TableUtils.Kind.Secondary,
                TableUtils.Kind.Name, TableUtils.Kind.Number, TableUtils.Kind.Number, TableUtils.Kind.Number);
        JComponent box = Tables.boxed(table);
        table.getColumnModel().getColumn(0).setCellRenderer(DockRenderers.bold());
        for (int column = 4; column < 7; column++) {
            table.getColumnModel().getColumn(column).setCellRenderer(DockRenderers.mono(String::valueOf));
        }
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (!e.getValueIsAdjusting() && !reverting && row >= 0 && row < axes.size()) {
                select(axes.get(row));
            }
        });
        Forms.Section list = new Forms.Section("move", //$NON-NLS-1$
                Translations.getString("MachineSettings.Motion.Axes")) //$NON-NLS-1$
                .withRight(Translations.getString("MachineSettings.Motion.Axes.Note")); //$NON-NLS-1$
        list.content(box);
        axisHolder.setOpaque(false);

        List<JComponent> sections = new ArrayList<>();
        sections.add(list);
        sections.add(axisHolder);
        Head head = head();
        if (head instanceof ReferenceHead) {
            sections.add(forms.add(Form.of(new HeadForm.Bean((ReferenceHead) head)).named("HeadForm.Title") //$NON-NLS-1$
                    .section("HeadForm.Park", "pin") //$NON-NLS-1$ //$NON-NLS-2$
                    .location("parkLocation", "ReferenceHeadConfigurationWizard.LocationsPanel.ParkLocationLabel.text", false) //$NON-NLS-1$ //$NON-NLS-2$
                    .locationButtons()
                    .build()));
        }
        sections.add(forms.add(Form.of(new MachineForm.Bean(machine)).named("MachineForm.Title") //$NON-NLS-1$
                .section("MachineSettings.Motion.AfterHoming", "home") //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("parkAfterHomed", "MachineForm.ParkAfterHomed", "MachineForm.ParkAfterHomed.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .toggle("safeZPark", "MachineForm.SafeZPark", "MachineForm.SafeZPark.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .build()));
        forms.onChange(() -> {
            if (!axes.isEmpty()) {
                model.fireTableRowsUpdated(0, axes.size() - 1);
            }
        });
        JButton calibration = Ui.button(Translations.getString("MachineSettings.Guide.ToCalibration"), //$NON-NLS-1$
                Ui.iconSm("target"), Ui.Size.Sm, Ui.Variant.Ghost); //$NON-NLS-1$
        calibration.addActionListener(e -> page.getFrame().showCalibrationStep(null, null));
        JComponent view = MachineSettingsPanel.page(
                Guide.of(Translations.getString("MachineSettings.Guide.Motion"), calibration), //$NON-NLS-1$
                sections.toArray(new JComponent[0]));
        if (!axes.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        }
        return view;
    }

    private Head head() {
        try {
            return machine.getDefaultHead();
        }
        catch (Exception e) {
            return null;
        }
    }

    /** The axis a row stands for comes under the table, once its predecessor's edits are settled. */
    private void select(Axis axis) {
        if (axis == selected) {
            return;
        }
        if (axisForm != null && axisForm.hasEdits()) {
            int choice = MachineSettingsPanel.askUnapplied(table, selected.getName());
            if (choice == 1) {
                axisForm.apply();
            }
            else if (choice == 0) {
                axisForm.reset();
            }
            else {
                reverting = true;
                try {
                    int row = axes.indexOf(selected);
                    table.setRowSelectionInterval(row, row);
                }
                finally {
                    reverting = false;
                }
                return;
            }
        }
        if (axisForm != null) {
            forms.remove(axisForm);
            axisForm.dispose();
            axisForm = null;
        }
        selected = axis;
        axisHolder.removeAll();
        if (axis instanceof ReferenceControllerAxis) {
            axisForm = forms.add(controller((ReferenceControllerAxis) axis));
            axisHolder.add(axisForm, BorderLayout.CENTER);
        }
        else if (axis instanceof ReferenceMappedAxis) {
            axisForm = forms.add(AxisForm.mapped((ReferenceMappedAxis) axis));
            axisHolder.add(axisForm, BorderLayout.CENTER);
        }
        else {
            axisHolder.add(elsewhere(axis), BorderLayout.CENTER);
        }
        axisHolder.revalidate();
        axisHolder.repaint();
    }

    /**
     * A controller axis as it matters day to day: how far it may go, how fast, where it homes, and
     * its backlash as calibration measured it. Its driver, letter and resolution are in the tree.
     */
    private FormWizard controller(ReferenceControllerAxis axis) {
        boolean rotation = axis.getType() == Axis.Type.Rotation;
        String unit = rotation ? "\u00b0" : page.units().getShortName(); //$NON-NLS-1$
        Form.Builder form = Form.of(new AxisForm.ControllerBean(axis)).named(axis.getName())
                .section(String.format(Translations.getString("MachineSettings.Motion.Axis"), axis.getName()), "ruler") //$NON-NLS-1$ //$NON-NLS-2$
                .note(describe(axis));
        if (rotation) {
            form.toggle("limitRotation", "ReferenceControllerAxisConfigurationWizard.ControllerSettingsPanel.LimitToRangeLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                    "AxisForm.LimitRotation.Note"); //$NON-NLS-1$
        }
        AxisForm.limit(form, axis, "softLimitLow", "ReferenceControllerAxisConfigurationWizard.KinematicsPanel.SoftLimitLowLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                AxisForm::softLimitsShown);
        AxisForm.limit(form, axis, "softLimitHigh", "ReferenceControllerAxisConfigurationWizard.KinematicsPanel.SoftLimitHighLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                AxisForm::softLimitsShown);
        form.length("homeCoordinate", "ReferenceControllerAxisConfigurationWizard.ControllerSettingsPanel.HomeCoordinateLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .width(150);
        if (axis.getType() == Axis.Type.Z) {
            form.section("AxisForm.SafeZone", "up"); //$NON-NLS-1$ //$NON-NLS-2$
            AxisForm.limit(form, axis, "safeZoneLow", "ReferenceControllerAxisConfigurationWizard.KinematicsPanel.SafeZoneLowLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                    f -> true);
            AxisForm.limit(form, axis, "safeZoneHigh", "ReferenceControllerAxisConfigurationWizard.KinematicsPanel.SafeZoneHighLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                    f -> true);
        }
        form.section("ReferenceControllerAxisConfigurationWizard.KinematicsPanel.Border.title", "sliders") //$NON-NLS-1$ //$NON-NLS-2$
                .length("feedratePerSecond", "AxisForm.Feedrate").unit(unit + "/s").width(150) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .liveHint(f -> rotation ? null : AxisForm.perMinute(f.value("feedratePerSecond"))) //$NON-NLS-1$
                .length("accelerationPerSecond2", "AxisForm.Acceleration").unit(unit + "/s\u00b2").width(150) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .length("jerkPerSecond3", "AxisForm.Jerk").unit(unit + "/s\u00b3").width(150) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("AxisForm.Kinematics.Hint"); //$NON-NLS-1$
        boolean planar = axis.getType() == Axis.Type.X || axis.getType() == Axis.Type.Y;
        CalibrationStep step = planar ? CalibrationStep.XyBacklash
                : axis.getType() == Axis.Type.Z ? CalibrationStep.ZBacklash : CalibrationStep.RotationBacklash;
        form.section("AxisForm.Backlash", "rcw").measuredBy(step, axis) //$NON-NLS-1$ //$NON-NLS-2$
                .readOnly("backlashCompensationMethod", //$NON-NLS-1$
                        "BacklashCompensationConfigurationWizard.BacklashDiagnosticsPanel.CompensationMethodLabel.text") //$NON-NLS-1$
                .readOnly("backlashOffset", "AxisForm.Backlash.Offset"); //$NON-NLS-1$ //$NON-NLS-2$
        return form.build();
    }

    /** "Linear axis · controller axis X · driver GcodeDriver". */
    private static String describe(ReferenceControllerAxis axis) {
        return String.format(Translations.getString("MachineSettings.Motion.Axis.Note"), //$NON-NLS-1$
                kind(axis), axis.getLetter() == null || axis.getLetter().isEmpty() ? "\u2014" : axis.getLetter(), //$NON-NLS-1$
                axis.getDriver() == null ? "\u2014" : axis.getDriver().getName()); //$NON-NLS-1$
    }

    /** An axis of another kind - a cam, a transform - is set where every element is. */
    private JComponent elsewhere(Axis axis) {
        Forms.Section section = new Forms.Section("layers", axis.getName()); //$NON-NLS-1$
        JButton open = Ui.button(Translations.getString("MachineSettings.Motion.InTree"), Ui.iconSm("tree"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Default);
        open.addActionListener(e -> page.showInTree(axis));
        section.content(Forms.row(Ui.t2(Translations.getString("MachineSettings.Motion.Other")), open)); //$NON-NLS-1$
        return section;
    }

    // ---- the table ----------------------------------------------------------------------------

    static String kind(Axis axis) {
        if (axis instanceof ReferenceMappedAxis) {
            return Translations.getString("MachineSettings.Motion.Kind.Mapped"); //$NON-NLS-1$
        }
        if (axis instanceof ReferenceControllerAxis) {
            return Translations.getString(axis.getType() == Axis.Type.Rotation
                    ? "MachineSettings.Motion.Kind.Rotation" : "MachineSettings.Motion.Kind.Linear"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return Translations.getString("MachineSettings.Motion.Kind.Other"); //$NON-NLS-1$
    }

    private final class AxesModel extends AbstractTableModel {
        private final String[] columns = { "Axis", "Kind", "Letter", "Limits", "Feedrate", "Acceleration", "Jerk" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$

        @Override
        public int getRowCount() {
            return axes.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return Translations.getString("MachineSettings.Motion.Column." + columns[column]); //$NON-NLS-1$
        }

        @Override
        public Object getValueAt(int row, int column) {
            Axis axis = axes.get(row);
            ReferenceControllerAxis controller = axis instanceof ReferenceControllerAxis
                    ? (ReferenceControllerAxis) axis : null;
            switch (column) {
                case 0:
                    return axis.getName();
                case 1:
                    return kind(axis);
                case 2:
                    return controller == null || controller.getLetter() == null || controller.getLetter().isEmpty()
                            ? "\u2014" : controller.getLetter(); //$NON-NLS-1$
                case 3:
                    return limits(axis);
                case 4:
                    return controller == null ? "\u2014" : rate(controller, controller.getFeedratePerSecond(), "/s"); //$NON-NLS-1$ //$NON-NLS-2$
                case 5:
                    return controller == null ? "\u2014" : rate(controller, controller.getAccelerationPerSecond2(), "/s\u00b2"); //$NON-NLS-1$ //$NON-NLS-2$
                default:
                    return controller == null ? "\u2014" : rate(controller, controller.getJerkPerSecond3(), "/s\u00b3"); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
    }

    private String unit(Axis axis) {
        return axis.getType() == Axis.Type.Rotation ? "\u00b0" //$NON-NLS-1$
                : page.units().getShortName();
    }

    private String number(Length length, Axis axis) {
        double value = axis.getType() == Axis.Type.Rotation ? length.getValue()
                : length.convertToUnits(page.units()).getValue();
        return String.format(Locale.US, axis.getType() == Axis.Type.Rotation ? "%.0f" : "%.3f", value); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private String rate(ReferenceControllerAxis axis, Length rate, String per) {
        if (rate == null || rate.getValue() == 0) {
            return "0"; //$NON-NLS-1$
        }
        double value = axis.getType() == Axis.Type.Rotation ? rate.getValue()
                : rate.convertToUnits(page.units()).getValue();
        return String.format(Locale.US, "%.0f %s%s", value, unit(axis), per); //$NON-NLS-1$
    }

    /** "0.000 – 433.000 mm", "off", the safe zone for a Z, and what a mapped axis follows. */
    private String limits(Axis axis) {
        if (axis instanceof ReferenceMappedAxis) {
            ReferenceMappedAxis mapped = (ReferenceMappedAxis) axis;
            String input = mapped.getInputAxis() == null ? "\u2014" : mapped.getInputAxis().getName(); //$NON-NLS-1$
            boolean negated = Math.signum(mapped.getMapOutput1().getValue() - mapped.getMapOutput0().getValue())
                    != Math.signum(mapped.getMapInput1().getValue() - mapped.getMapInput0().getValue());
            return String.format(Translations.getString(negated ? "MachineSettings.Motion.Follows.Negated" //$NON-NLS-1$
                    : "MachineSettings.Motion.Follows"), input); //$NON-NLS-1$
        }
        if (!(axis instanceof ReferenceControllerAxis)) {
            return "\u2014"; //$NON-NLS-1$
        }
        ReferenceControllerAxis controller = (ReferenceControllerAxis) axis;
        String off = Translations.getString("MachineSettings.Motion.Off"); //$NON-NLS-1$
        String limits;
        boolean limited = axis.getType() != Axis.Type.Rotation || controller.isLimitRotation();
        if (!limited || (!controller.isSoftLimitLowEnabled() && !controller.isSoftLimitHighEnabled())) {
            limits = off;
        }
        else {
            limits = (controller.isSoftLimitLowEnabled() ? number(controller.getSoftLimitLow(), axis) : "\u2212\u221e") //$NON-NLS-1$
                    + " \u2013 " //$NON-NLS-1$
                    + (controller.isSoftLimitHighEnabled() ? number(controller.getSoftLimitHigh(), axis) : "\u221e") //$NON-NLS-1$
                    + " " + unit(axis); //$NON-NLS-1$
        }
        if (axis.getType() == Axis.Type.Z && controller.isSafeZoneLowEnabled() && controller.isSafeZoneHighEnabled()) {
            limits += String.format(Translations.getString("MachineSettings.Motion.SafeZone"), //$NON-NLS-1$
                    number(controller.getSafeZoneLow(), axis), number(controller.getSafeZoneHigh(), axis), unit(axis));
        }
        return limits;
    }
}
