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

import java.awt.Color;

import javax.swing.border.LineBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.openpnp.Translations;
import org.openpnp.gui.components.AutoSelectTextTable;
import org.openpnp.model.Solutions;
import org.openpnp.model.Solutions.Issue;
import org.openpnp.model.Solutions.Severity;
import org.openpnp.model.Solutions.State;
import org.openpnp.model.Solutions.Subject;

/**
 * Shows a {@link Solutions} in a table. Solutions used to be the table model itself, which put
 * Swing in the model package; it now reports through property changes and this translates them
 * into the table events Swing expects.
 */
@SuppressWarnings("serial")
public class SolutionsTableModel extends AbstractTableModel {
    private final String[] columnNames = new String[] {
            Translations.getString("Solutions.Model.ColumnName.subject"), //$NON-NLS-1$
            Translations.getString("Solutions.Model.ColumnName.severity"), //$NON-NLS-1$
            Translations.getString("Solutions.Model.ColumnName.issue"), //$NON-NLS-1$
            Translations.getString("Solutions.Model.ColumnName.solution"), //$NON-NLS-1$
            Translations.getString("Solutions.Model.ColumnName.state")}; //$NON-NLS-1$

    private final Class<?>[] columnTypes = new Class<?>[] {
            Subject.class, Severity.class, String.class, String.class, State.class};

    private final Solutions solutions;

    public SolutionsTableModel(Solutions solutions) {
        this.solutions = solutions;
        solutions.addPropertyChangeListener(e -> {
            if ("issues".equals(e.getPropertyName())) {
                fireTableDataChanged();
            }
            else if ("issue".equals(e.getPropertyName())) {
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
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Issue issue = getIssue(rowIndex);
        switch (columnIndex) {
            case 0:
                return issue.getSubject();
            case 1:
                return issue.getSeverity();
            case 2:
                return issue.getIssue();
            case 3:
                return issue.getSolution();
            case 4:
                return issue.getState();
            default:
                return null;
        }
    }

    public String getToolTipAt(int rowIndex, int columnIndex) {
        Issue issue = getIssue(rowIndex);
        switch (columnIndex) {
            case 2:
                return issue.getIssue();
            case 3:
                return issue.getSolution();
        }
        return null;
    }

    static protected class SubjectRenderer extends DefaultTableCellRenderer {
        @Override
        public void setValue(Object value) {
            if (value == null) {
                return;
            }
            Subject subject = (Subject) value;
            setText(subject.getSubjectText());
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
    }
}
