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
import java.awt.datatransfer.StringSelection;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import org.openpnp.Translations;
import org.openpnp.gui.components.AutoSelectTextTable;
import org.openpnp.gui.shell.DockPanel;
import org.openpnp.gui.support.StatusPillRenderer;
import org.openpnp.gui.support.StatusPillRenderer.Tone;
import org.openpnp.model.JobRun;

/**
 * The job page's run log: what this run did, in order - the fiducials found, each placement
 * placed on which nozzle, the picks retried, the feeders that ran out, the errors. The tab used to
 * be a second copy of the global log, whose level setting changed the level of the whole
 * program's log.
 */
@SuppressWarnings("serial")
public class JobRunLogPanel extends JPanel {
    private final EventsModel model = new EventsModel();
    private final JTable table;
    private JobRun run;

    public JobRunLogPanel() {
        super(new BorderLayout());
        setOpaque(false);
        DockPanel.Toolbar toolbar = new DockPanel.Toolbar();
        toolbar.button(new javax.swing.AbstractAction() {
            {
                putValue(NAME, Translations.getString("JobRunLogPanel.Copy")); //$NON-NLS-1$
                putValue(SHORT_DESCRIPTION, Translations.getString("JobRunLogPanel.Copy.toolTipText")); //$NON-NLS-1$
            }

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(model.asText()), null);
            }
        }, "copy", "JobRunLogPanel.Copy"); //$NON-NLS-1$ //$NON-NLS-2$
        add(toolbar, BorderLayout.NORTH);
        table = new AutoSelectTextTable(model);
        table.setDefaultRenderer(JobRun.EventKind.class,
                new StatusPillRenderer(value -> toneOf((JobRun.EventKind) value),
                        value -> Translations.getString("JobRunLogPanel.Kind." + value))); //$NON-NLS-1$
        AutoSelectTextTable.setEmptyText(table, Translations.getString("JobRunLogPanel.Empty")); //$NON-NLS-1$
        add(DockPanel.table(table), BorderLayout.CENTER);
        table.getColumnModel().getColumn(0).setMaxWidth(90);
        table.getColumnModel().getColumn(1).setMaxWidth(120);
    }

    /** Follows the run of the job now shown. */
    public void setRun(JobRun run) {
        this.run = run;
        refresh();
    }

    /** The events as they stand; called when the run says something changed. */
    public void refresh() {
        model.setEvents(run == null ? new ArrayList<>() : new ArrayList<>(run.getEvents()));
        if (table.getRowCount() > 0) {
            table.scrollRectToVisible(table.getCellRect(table.getRowCount() - 1, 0, true));
        }
    }

    public int getEventCount() {
        return model.getRowCount();
    }

    private static Tone toneOf(JobRun.EventKind kind) {
        switch (kind) {
            case Placed:
            case Finished:
                return Tone.Ok;
            case Retry:
            case FeederEmpty:
                return Tone.Warning;
            case Error:
                return Tone.Error;
            case Started:
            case Fiducials:
                return Tone.Info;
            default:
                return Tone.Muted;
        }
    }

    /** What an event says, in words. */
    public static String describe(JobRun.Event event) {
        String id = event.getPlacementId() == null ? "" : event.getPlacementId(); //$NON-NLS-1$
        String detail = event.getDetail() == null ? "" : event.getDetail(); //$NON-NLS-1$
        if (event.getKind() == JobRun.EventKind.Error && id.isEmpty()) {
            // Not about one placement: the fiducials of a board, the job as a whole.
            return detail;
        }
        return String.format(Translations.getString("JobRunLogPanel.Text." + event.getKind()), id, detail); //$NON-NLS-1$
    }

    private static final class EventsModel extends AbstractTableModel {
        private List<JobRun.Event> events = new ArrayList<>();
        private final SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss"); //$NON-NLS-1$

        void setEvents(List<JobRun.Event> events) {
            this.events = events;
            fireTableDataChanged();
        }

        String asText() {
            StringBuilder text = new StringBuilder();
            for (JobRun.Event event : events) {
                text.append(time.format(new Date(event.getMillis()))).append('\t')
                        .append(Translations.getString("JobRunLogPanel.Kind." + event.getKind())).append('\t') //$NON-NLS-1$
                        .append(describe(event)).append('\n');
            }
            return text.toString();
        }

        @Override
        public int getRowCount() {
            return events.size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public String getColumnName(int column) {
            return Translations.getString("JobRunLogPanel.Column." + column); //$NON-NLS-1$
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 1 ? JobRun.EventKind.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            JobRun.Event event = events.get(row);
            switch (column) {
                case 0:
                    return time.format(new Date(event.getMillis()));
                case 1:
                    return event.getKind();
                default:
                    return describe(event);
            }
        }
    }
}
