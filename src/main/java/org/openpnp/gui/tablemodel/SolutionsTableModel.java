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

package org.openpnp.gui.tablemodel;

import java.awt.Font;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.openpnp.Translations;
import org.openpnp.gui.components.AutoSelectTextTable;
import org.openpnp.gui.support.TableUtils;
import org.openpnp.model.CalibrationStep;
import org.openpnp.model.Solutions;
import org.openpnp.model.Solutions.Issue;
import org.openpnp.model.Solutions.Severity;
import org.openpnp.model.Solutions.State;
import org.openpnp.model.Solutions.Subject;

/**
 * Shows a {@link Solutions} in a table, as mockup 13 has it: severity and state as capsules, the
 * subject in bold, the issue, and where it is dealt with - on this page, or at a step of the
 * calibration page. Solutions used to be the table model itself, which put Swing in the model
 * package; it now reports through property changes and this translates them into the table
 * events Swing expects.
 */
@SuppressWarnings("serial")
public class SolutionsTableModel extends AbstractTableModel implements TableUtils.ColumnKinds {
    public static final int SEVERITY = 0;
    public static final int STATE = 1;
    public static final int SUBJECT = 2;
    public static final int ISSUE = 3;
    public static final int HANDLING = 4;

    /** Where an issue is dealt with: at a calibration step, or on the issues page when null. */
    public static final class Handling {
        final CalibrationStep step;

        Handling(CalibrationStep step) {
            this.step = step;
        }

        public CalibrationStep getStep() {
            return step;
        }

        @Override
        public String toString() {
            return step == null ? Translations.getString("Solutions.Model.Handling.Here") //$NON-NLS-1$
                    : String.format(Translations.getString("Solutions.Model.Handling.Calibration"), step.getName()); //$NON-NLS-1$
        }
    }

    private final String[] columnNames = new String[] {
            Translations.getString("Solutions.Model.ColumnName.severity"), //$NON-NLS-1$
            Translations.getString("Solutions.Model.ColumnName.state"), //$NON-NLS-1$
            Translations.getString("Solutions.Model.ColumnName.subject"), //$NON-NLS-1$
            Translations.getString("Solutions.Model.ColumnName.issue"), //$NON-NLS-1$
            Translations.getString("Solutions.Model.ColumnName.handling")}; //$NON-NLS-1$

    private final Class<?>[] columnTypes = new Class<?>[] {
            Severity.class, State.class, Subject.class, String.class, Handling.class};

    private final Solutions solutions;

    public SolutionsTableModel(Solutions solutions) {
        this.solutions = solutions;
        solutions.addPropertyChangeListener(e -> {
            if ("issues".equals(e.getPropertyName())) { //$NON-NLS-1$
                fireTableDataChanged();
            }
            else if ("issue".equals(e.getPropertyName())) { //$NON-NLS-1$
                int row = solutions.getIssues().indexOf(e.getNewValue());
                if (row >= 0) {
                    fireTableRowsUpdated(row, row);
                }
            }
        });
    }

    public Solutions getSolutions() {
        return solutions;
    }

    public Issue getIssue(int index) {
        return solutions.getIssue(index);
    }

    @Override
    public TableUtils.Kind[] getColumnKinds() {
        return new TableUtils.Kind[] { TableUtils.Kind.Status, TableUtils.Kind.Status, TableUtils.Kind.Name,
                TableUtils.Kind.Name, TableUtils.Kind.Status };
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public int getRowCount() {
        return solutions.getIssues().size();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnTypes[columnIndex];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Issue issue = getIssue(rowIndex);
        switch (columnIndex) {
            case SEVERITY:
                return issue.getSeverity();
            case STATE:
                return issue.getState();
            case SUBJECT:
                return issue.getSubject();
            case ISSUE:
                return issue.getIssue();
            case HANDLING:
                return new Handling(issue.getCalibrationStep());
            default:
                return null;
        }
    }

    public String getToolTipAt(int rowIndex, int columnIndex) {
        Issue issue = getIssue(rowIndex);
        return columnIndex == ISSUE ? issue.getIssue() : null;
    }

    /**
     * "轴 x" rather than "ReferenceControllerAxis x": a subject's own text leads with its class
     * name unless it says otherwise, and the class is named in words.
     */
    public static String subjectText(Subject subject) {
        if (subject == null) {
            return ""; //$NON-NLS-1$
        }
        String text = subject.getSubjectText();
        String simple = subject.getClass().getSimpleName();
        if (text != null && !simple.isEmpty() && text.startsWith(simple)) {
            return org.openpnp.gui.support.DisplayNames.typeName(subject.getClass()) + text.substring(simple.length());
        }
        return text;
    }

    static protected class SubjectRenderer extends DefaultTableCellRenderer {
        @Override
        public java.awt.Component getTableCellRendererComponent(javax.swing.JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value == null ? "" : subjectText((Subject) value), //$NON-NLS-1$
                    isSelected, hasFocus, row, column);
            setFont(table.getFont().deriveFont(Font.BOLD));
            return this;
        }
    }

    /** "校准页 · XY 间隙 ›" as a link in the accent, "本页处理" in the secondary colour. */
    static protected class HandlingRenderer extends DefaultTableCellRenderer {
        @Override
        public java.awt.Component getTableCellRendererComponent(javax.swing.JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            boolean calibration = value instanceof Handling && ((Handling) value).step != null;
            if (!isSelected) {
                setForeground(calibration ? org.openpnp.gui.shell.Ui.accent() : org.openpnp.gui.shell.Ui.text2());
            }
            return this;
        }
    }

    /**
     * Severity as a status capsule in the theme's colours. It used to be black text on a light
     * fill of its own, the one bright cell in every row of the dark theme.
     */
    static protected class SeverityRenderer extends org.openpnp.gui.support.StatusPillRenderer {
        SeverityRenderer() {
            super(value -> {
                switch ((Severity) value) {
                    case Error:
                    case Fundamental:
                        return Tone.Error;
                    case Warning:
                        return Tone.Warning;
                    case Suggestion:
                        return Tone.Info;
                    default:
                        return Tone.Muted;
                }
            }, org.openpnp.gui.support.DisplayNames::of);
        }
    }

    /** State as a status capsule: solved in green, the rest quiet. */
    static protected class StateRenderer extends org.openpnp.gui.support.StatusPillRenderer {
        StateRenderer() {
            super(value -> value == State.Solved ? Tone.Ok : Tone.Muted,
                    org.openpnp.gui.support.DisplayNames::of);
        }
    }

    public static void applyTableUi(AutoSelectTextTable table) {
        table.setDefaultRenderer(Subject.class, new SubjectRenderer());
        table.setDefaultRenderer(Severity.class, new SeverityRenderer());
        table.setDefaultRenderer(State.class, new StateRenderer());
        table.setDefaultRenderer(Handling.class, new HandlingRenderer());
    }
}
