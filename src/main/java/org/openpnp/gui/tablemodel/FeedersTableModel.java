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

import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.openpnp.ConfigurationListener;
import org.openpnp.Translations;
import org.openpnp.gui.support.DisplayNames;
import org.openpnp.gui.support.FeederDescriptions;
import org.openpnp.gui.support.TableUtils;
import org.openpnp.machine.reference.ReferenceFeeder;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.Configuration;
import org.openpnp.model.Part;
import org.openpnp.spi.Feeder;
import org.openpnp.util.BeanUtils;

/**
 * The feeders as the mockup's table has them: switched on, name, kind, part, slot, tape, what is
 * left, a status and when a part was last picked. The priority, the feed options and the fault
 * record are there too, hidden until the column settings show them.
 */
public class FeedersTableModel extends AbstractObjectTableModel implements TableUtils.DefaultHidden {
    public static final int ENABLED = 0;
    public static final int NAME = 1;
    public static final int TYPE = 2;
    public static final int PART = 3;
    public static final int SLOT = 4;
    public static final int TAPE = 5;
    public static final int LEFT = 6;
    public static final int STATUS = 7;
    public static final int LAST_PICK = 8;
    public static final int PRIORITY = 9;
    public static final int FEED_OPTIONS = 10;
    public static final int FAULTS = 11;

    /** What the status column says: the troubles first, so that sorting by it puts them on top. */
    public enum Status {
        Fault, Empty, NoPart, Low, Disabled, Ready
    }

    public static Status statusOf(Feeder feeder) {
        if (!feeder.isEnabled()) {
            return Status.Disabled;
        }
        if (feeder.getPart() == null) {
            return Status.NoPart;
        }
        if (feeder instanceof ReferenceFeeder && ((ReferenceFeeder) feeder).summariseJobFaults().startsWith("X")) { //$NON-NLS-1$
            return Status.Fault;
        }
        if (feeder.isEmpty()) {
            return Status.Empty;
        }
        if (feeder.isLow()) {
            return Status.Low;
        }
        return Status.Ready;
    }

    final private Configuration configuration;

    private final String[] columnNames = new String[] {
            Translations.getString("FeedersTableModel.ColumnName.Enabled"), //$NON-NLS-1$
            Translations.getString("FeedersTableModel.ColumnName.Name"), //$NON-NLS-1$
            Translations.getString("FeedersTableModel.ColumnName.Type"), //$NON-NLS-1$
            Translations.getString("FeedersTableModel.ColumnName.Part"), //$NON-NLS-1$
            Translations.getString("FeedersTableModel.ColumnName.Slot"), //$NON-NLS-1$
            Translations.getString("FeedersTableModel.ColumnName.Tape"), //$NON-NLS-1$
            Translations.getString("FeedersTableModel.ColumnName.Left"), //$NON-NLS-1$
            Translations.getString("FeedersTableModel.ColumnName.Status"), //$NON-NLS-1$
            Translations.getString("FeedersTableModel.ColumnName.LastPick"), //$NON-NLS-1$
            Translations.getString("FeedersTableModel.ColumnName.Priority"), //$NON-NLS-1$
            Translations.getString("FeedersTableModel.ColumnName.FeedOptions"), //$NON-NLS-1$
            Translations.getString("FeedersTableModel.ColumnName.Faults") //$NON-NLS-1$
    };

    private List<Feeder> feeders;

    /** A feeder's row is drawn again when anything about it changes, from whichever thread. */
    private final PropertyChangeListener rowChanged = event -> {
        Object source = event.getSource();
        SwingUtilities.invokeLater(() -> {
            if (source instanceof Feeder) {
                refresh((Feeder) source);
            }
        });
    };

    public FeedersTableModel(Configuration configuration) {
        this.configuration = configuration;
        Configuration.get().addListener(new ConfigurationListener.Adapter() {
            public void configurationComplete(Configuration configuration) throws Exception {
                BeanUtils.addPropertyChangeListener(configuration.getMachine(), "feeders", event -> { //$NON-NLS-1$
                    refresh();
                });
                refresh();
            }
        });
    }

    public void refresh() {
        if (feeders != null) {
            for (Feeder feeder : feeders) {
                if (feeder instanceof AbstractModelObject) {
                    ((AbstractModelObject) feeder).removePropertyChangeListener(rowChanged);
                }
            }
        }
        feeders = new ArrayList<>(configuration.getMachine().getFeeders());
        for (Feeder feeder : feeders) {
            if (feeder instanceof AbstractModelObject) {
                ((AbstractModelObject) feeder).addPropertyChangeListener(rowChanged);
            }
        }
        fireTableDataChanged();
    }

    public void refresh(Feeder f) {
        int row = feeders == null ? -1 : feeders.indexOf(f);
        if (row >= 0) {
            fireTableRowsUpdated(row, row);
        }
    }

    @Override
    public int[] getDefaultHiddenColumns() {
        return new int[] { PRIORITY, FEED_OPTIONS, FAULTS };
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    public int getColumnCount() {
        return columnNames.length;
    }

    public int getRowCount() {
        return (feeders == null) ? 0 : feeders.size();
    }

    @Override
    public Feeder getRowObjectAt(int index) {
        return feeders.get(index);
    }

    @Override
    public int indexOf(Object selectedVisionSettings) {
        return feeders.indexOf(selectedVisionSettings);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        Feeder feeder = feeders.get(rowIndex);
        switch (columnIndex) {
            case ENABLED:
            case NAME:
                return true;
            case SLOT:
                return FeederDescriptions.slotEditable(feeder);
            case PRIORITY:
                return feeder instanceof ReferenceFeeder;
            case FEED_OPTIONS:
                return feeder instanceof ReferenceFeeder && ((ReferenceFeeder) feeder).supportsFeedOptions();
            default:
                return false;
        }
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        try {
            Feeder feeder = feeders.get(rowIndex);
            if (columnIndex == NAME) {
                feeder.setName((String) aValue);
            }
            else if (columnIndex == SLOT) {
                ((org.openpnp.spi.base.AbstractFeeder) feeder).setSlotName((String) aValue);
            }
            else if (columnIndex == PRIORITY) {
                feeder.setPriority((Feeder.Priority) aValue);
            }
            else if (columnIndex == ENABLED) {
                feeder.setEnabled((Boolean) aValue);
            }
            else if (columnIndex == FEED_OPTIONS) {
                ((ReferenceFeeder) feeder).setFeedOptions((ReferenceFeeder.FeedOptions) aValue);
            }
            configuration.setDirty(true);
        }
        catch (Exception e) {
            TableUtils.rejected(this, columnIndex, aValue, e);
        }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case ENABLED:
                return Boolean.class;
            case LEFT:
                return Integer.class;
            case STATUS:
                return Status.class;
            case LAST_PICK:
                return Long.class;
            case PRIORITY:
                return Feeder.Priority.class;
            case FEED_OPTIONS:
                return ReferenceFeeder.FeedOptions.class;
            default:
                return String.class;
        }
    }

    public Object getValueAt(int row, int col) {
        Feeder feeder = feeders.get(row);
        switch (col) {
            case ENABLED:
                return feeder.isEnabled();
            case NAME:
                return feeder.getName();
            case TYPE:
                return DisplayNames.typeName(feeder.getClass());
            case PART: {
                Part part = feeder.getPart();
                return part == null ? null : part.getId();
            }
            case SLOT:
                return FeederDescriptions.slot(feeder);
            case TAPE:
                return FeederDescriptions.tape(feeder);
            case LEFT:
                return feeder.getPartsLeft();
            case STATUS:
                return statusOf(feeder);
            case LAST_PICK:
                return feeder.getLastPickMillis() > 0 ? feeder.getLastPickMillis() : null;
            case PRIORITY:
                return feeder.getPriority();
            case FEED_OPTIONS:
                return feeder instanceof ReferenceFeeder ? ((ReferenceFeeder) feeder).getFeedOptions() : null;
            case FAULTS:
                return feeder instanceof ReferenceFeeder ? ((ReferenceFeeder) feeder).summariseJobFaults() : null;
            default:
                return null;
        }
    }
}
