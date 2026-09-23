/*
 * Copyright (C) 2026 Pono
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
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.openpnp.Translations;
import org.openpnp.gui.components.AutoSelectTextTable;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.DockPanel;
import org.openpnp.gui.shell.RoundedPanel;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.DisplayNames;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.ReferenceNozzleTip;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis;
import org.openpnp.machine.reference.camera.ReferenceCamera;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.spi.Axis;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Driver;
import org.openpnp.spi.Head;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.PropertySheetHolder;

/**
 * The issues page's Machine overview tab, as mockup 15 has it: the axes, cameras, drivers and
 * nozzles as four cards of four columns, read only, a row taking the user to the machine page
 * where it is set. It was the top of the diagnostics page, in seven and eight columns a table.
 */
@SuppressWarnings("serial")
public class MachineOverviewPanel extends JPanel {
    /** A cell that is a capsule: its words and its tone. */
    static final class Pill {
        final String text;
        final Chip.Tone tone;

        Pill(String text, Chip.Tone tone) {
            this.text = text;
            this.tone = tone;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    private final ReferenceMachine machine;
    private final List<Card> cards = new ArrayList<>();
    private final Card axes;
    private final Card cameras;
    private final Card drivers;
    private final Card nozzles;

    public MachineOverviewPanel(ReferenceMachine machine) {
        this.machine = machine;
        setLayout(new BorderLayout());
        setOpaque(false);
        DockPanel.Toolbar toolbar = new DockPanel.Toolbar();
        toolbar.add(Ui.t2(Translations.getString("MachineOverviewPanel.Note"))); //$NON-NLS-1$
        toolbar.glue();
        JButton copy = Ui.button(Translations.getString("MachineOverviewPanel.Copy"), Ui.iconSm("copy"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Ghost);
        copy.setFocusable(false);
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(asText()), null));
        toolbar.add(copy);
        add(toolbar, BorderLayout.NORTH);

        axes = new Card("tree", "Axes", "Name", "Type", "Backlash", "SoftLimits"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
        cameras = new Card("camera", "Cameras", "Name", "Looking", "UnitsPerPixel", "Calibration"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
        drivers = new Card("upload", "Drivers", "Name", "Type", "Communications", "State"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
        nozzles = new Card("nozzle", "Nozzles", "Nozzle", "NozzleTip", "Offsets", "RunOut"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
        JPanel grid = new JPanel(new GridLayout(2, 2, 10, 10));
        grid.setOpaque(false);
        grid.setBorder(new EmptyBorder(10, 10, 10, 10));
        grid.add(axes.panel);
        grid.add(cameras.panel);
        grid.add(drivers.panel);
        grid.add(nozzles.panel);
        add(grid, BorderLayout.CENTER);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        refresh();
    }

    /** Reads the machine again: it is small enough to walk, and there is nothing here to edit. */
    public void refresh() {
        MachineDiagnosticsResults results = machine.getMachineDiagnostics().getLastResults();
        axes.clear();
        for (Axis axis : machine.getAxes()) {
            Object backlash = "\u2014"; //$NON-NLS-1$
            Object limits = "\u2014"; //$NON-NLS-1$
            if (axis instanceof ReferenceControllerAxis) {
                ReferenceControllerAxis a = (ReferenceControllerAxis) axis;
                backlash = backlash(a, results);
                limits = a.isSoftLimitLowEnabled() || a.isSoftLimitHighEnabled()
                        ? mm(a.isSoftLimitLowEnabled() ? a.getSoftLimitLow() : null) + " \u2026 " //$NON-NLS-1$
                                + mm(a.isSoftLimitHighEnabled() ? a.getSoftLimitHigh() : null)
                        : Translations.getString("MachineOverviewPanel.NotSet"); //$NON-NLS-1$
            }
            axes.add(axis, axis.getName(), DisplayNames.typeName(axis.getClass()), backlash, limits);
        }
        cameras.clear();
        for (Camera camera : machine.getAllCameras()) {
            cameras.add(camera instanceof PropertySheetHolder ? camera : null, camera.getName(),
                    DisplayNames.of(camera.getLooking()),
                    camera.getUnitsPerPixel().isInitialized() ? String.format(Locale.ROOT, "%.4f mm", //$NON-NLS-1$
                            camera.getUnitsPerPixel().convertToUnits(LengthUnit.Millimeters).getX()) : "\u2014", //$NON-NLS-1$
                    calibrated(camera instanceof ReferenceCamera
                            && (((ReferenceCamera) camera).getAdvancedCalibration().isEnabled()
                                    || ((ReferenceCamera) camera).getCalibration().isEnabled())));
        }
        drivers.clear();
        for (Driver driver : machine.getDrivers()) {
            Object communications = "\u2014"; //$NON-NLS-1$
            if (driver instanceof org.openpnp.machine.reference.driver.AbstractReferenceDriver) {
                communications = DisplayNames.of(((org.openpnp.machine.reference.driver.AbstractReferenceDriver) driver)
                        .getCommunicationsType());
            }
            drivers.add(driver, driver.getName(), DisplayNames.typeName(driver.getClass()), communications,
                    machine.isEnabled()
                            ? new Pill(Translations.getString("MachineOverviewPanel.Connected"), Chip.Tone.Ok) //$NON-NLS-1$
                            : new Pill(Translations.getString("MachineOverviewPanel.Disconnected"), Chip.Tone.Neutral)); //$NON-NLS-1$
        }
        nozzles.clear();
        for (Head head : machine.getHeads()) {
            for (Nozzle nozzle : head.getNozzles()) {
                Object runout = "\u2014"; //$NON-NLS-1$
                if (nozzle.getNozzleTip() instanceof ReferenceNozzleTip) {
                    runout = calibrated(((ReferenceNozzleTip) nozzle.getNozzleTip()).getCalibration().isEnabled());
                }
                org.openpnp.model.Location offsets = nozzle.getHeadOffsets().convertToUnits(LengthUnit.Millimeters);
                nozzles.add(nozzle, nozzle.getName(),
                        nozzle.getNozzleTip() == null ? "\u2014" : nozzle.getNozzleTip().getName(), //$NON-NLS-1$
                        String.format(Locale.ROOT, "%.3f, %.3f", offsets.getX(), offsets.getY()), runout); //$NON-NLS-1$
            }
        }
        axes.note(String.format(Translations.getString("MachineOverviewPanel.Axes.Count"), axes.model.rows.size())); //$NON-NLS-1$
        cameras.note(String.format(Translations.getString("MachineOverviewPanel.Count"), cameras.model.rows.size())); //$NON-NLS-1$
        drivers.note(String.format(Translations.getString("MachineOverviewPanel.Count"), drivers.model.rows.size())); //$NON-NLS-1$
        nozzles.note(String.format(Translations.getString("MachineOverviewPanel.Count"), nozzles.model.rows.size())); //$NON-NLS-1$
    }

    /** "单侧 0.150" in green, "无 · 实测 0.20" in amber when what was measured is not covered. */
    private static Object backlash(ReferenceControllerAxis axis, MachineDiagnosticsResults results) {
        Double measured = null;
        if (results != null) {
            for (MachineDiagnosticsResults.Positioning positioning : results.getPositioning()) {
                if (positioning.getAxisId().equals(axis.getId())) {
                    measured = positioning.getBacklashMaxMm();
                }
            }
        }
        double offset = axis.getBacklashOffset().convertToUnits(LengthUnit.Millimeters).getValue();
        boolean none = axis.getBacklashCompensationMethod() == ReferenceControllerAxis.BacklashCompensationMethod.None;
        String text = none ? Translations.getString("MachineOverviewPanel.None") //$NON-NLS-1$
                : DisplayNames.of(axis.getBacklashCompensationMethod()) + String.format(Locale.ROOT, " %.3f", offset); //$NON-NLS-1$
        if (measured == null) {
            return none ? text : new Pill(text, Chip.Tone.Ok);
        }
        boolean covered = !none && measured <= offset + 0.005;
        return new Pill(covered ? text : text + String.format(Locale.ROOT, " \u00b7 " //$NON-NLS-1$
                + Translations.getString("MachineOverviewPanel.Measured"), measured), //$NON-NLS-1$
                covered ? Chip.Tone.Ok : Chip.Tone.Warn);
    }

    private static Pill calibrated(boolean yes) {
        return yes ? new Pill(Translations.getString("MachineOverviewPanel.Calibrated"), Chip.Tone.Ok) //$NON-NLS-1$
                : new Pill(Translations.getString("MachineOverviewPanel.NotCalibrated"), Chip.Tone.Warn); //$NON-NLS-1$
    }

    private static String mm(Length length) {
        return length == null ? "\u2014" //$NON-NLS-1$
                : String.format(Locale.ROOT, "%.1f", length.convertToUnits(LengthUnit.Millimeters).getValue()); //$NON-NLS-1$
    }

    /** The four cards as text, a tab between columns, for pasting into a message or a report. */
    String asText() {
        StringBuilder text = new StringBuilder();
        for (Card card : cards) {
            text.append(card.title.getText()).append('\n');
            for (int c = 0; c < card.model.columns.length; c++) {
                text.append(c == 0 ? "" : "\t").append(card.model.columns[c]); //$NON-NLS-1$ //$NON-NLS-2$
            }
            text.append('\n');
            for (Object[] row : card.model.rows) {
                for (int c = 0; c < row.length; c++) {
                    text.append(c == 0 ? "" : "\t").append(row[c]); //$NON-NLS-1$ //$NON-NLS-2$
                }
                text.append('\n');
            }
            text.append('\n');
        }
        return text.toString();
    }

    /** One card: an icon and a title over a table, and a note at the right of the title. */
    private final class Card {
        final RoundedPanel panel = RoundedPanel.card();
        final JLabel title;
        final JLabel note = Ui.muted(""); //$NON-NLS-1$
        final Model model;
        final AutoSelectTextTable table;

        Card(String icon, String key, String... columnKeys) {
            String[] columns = new String[columnKeys.length];
            for (int i = 0; i < columnKeys.length; i++) {
                columns[i] = Translations.getString("MachineOverviewPanel.Column." + columnKeys[i]); //$NON-NLS-1$
            }
            model = new Model(columns);
            table = new AutoSelectTextTable(model);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setDefaultRenderer(Object.class, new CellRenderer());
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    open();
                }
            });
            panel.setLayout(new BorderLayout());
            title = new JLabel(Translations.getString("MachineOverviewPanel." + key), Ui.iconSm(icon), JLabel.LEFT); //$NON-NLS-1$
            title.setFont(Ui.weighted(Ui.BASE, 600));
            note.setFont(Ui.font(11.5f));
            JPanel head = new JPanel(new BorderLayout());
            head.setOpaque(false);
            head.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.border()),
                    new EmptyBorder(8, 12, 8, 12)));
            head.add(title, BorderLayout.WEST);
            head.add(note, BorderLayout.EAST);
            panel.add(head, BorderLayout.NORTH);
            JScrollPane scroll = DockPanel.table(table);
            scroll.setBorder(null);
            scroll.setPreferredSize(new Dimension(200, 120));
            panel.add(scroll, BorderLayout.CENTER);
            cards.add(this);
        }

        void clear() {
            model.elements.clear();
            model.rows.clear();
            model.fireTableDataChanged();
        }

        void add(PropertySheetHolder element, Object... values) {
            model.elements.add(element);
            model.rows.add(values);
            model.fireTableRowsInserted(model.rows.size() - 1, model.rows.size() - 1);
        }

        void note(String text) {
            note.setText(text);
        }

        /** Opens the element the row is about in the machine page's tree. */
        private void open() {
            int row = table.getSelectedRow();
            PropertySheetHolder element = row < 0 ? null : model.elements.get(table.convertRowIndexToModel(row));
            MainFrame frame = MainFrame.get();
            if (element == null || frame == null) {
                return;
            }
            frame.showTab(frame.getMachineSetupTab());
            frame.getMachineSetupTab().selectPropertySheetHolder(element);
        }
    }

    private static final class Model extends AbstractTableModel {
        final String[] columns;
        final List<PropertySheetHolder> elements = new ArrayList<>();
        final List<Object[]> rows = new ArrayList<>();

        Model(String[] columns) {
            this.columns = columns;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            return rows.get(row)[column];
        }
    }

    /** The name in bold, a capsule for a state, the rest in the secondary colour. */
    private static final class CellRenderer extends DefaultTableCellRenderer {
        private final JPanel pillCell = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 6));
        private final Chip chip = new Chip("", Chip.Tone.Ok, Chip.Shape.Status); //$NON-NLS-1$

        CellRenderer() {
            pillCell.add(chip);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            if (value instanceof Pill) {
                chip.setText(((Pill) value).text);
                chip.setTone(((Pill) value).tone);
                pillCell.setOpaque(true);
                pillCell.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
                return pillCell;
            }
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setFont(column == 0 ? Ui.weighted(Ui.BASE - 0.5f, 600) : table.getFont());
            if (!isSelected) {
                setForeground(column == 0 ? table.getForeground() : Ui.text2());
            }
            return this;
        }
    }
}
