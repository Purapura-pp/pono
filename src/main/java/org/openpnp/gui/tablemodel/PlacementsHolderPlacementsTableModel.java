/*
 * Copyright (C) 2023 Jason von Nieda <jason@vonnieda.org>, Tony Luken <tonyluken62+openpnp@gmail.com>
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

import java.awt.Container;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import javax.swing.SwingUtilities;
import javax.swing.event.TableModelEvent;

import org.openpnp.Translations;
import org.openpnp.events.DefinitionStructureChangedEvent;
import org.openpnp.events.PlacementChangedEvent;
import org.openpnp.gui.JobPlacementsPanel;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.support.LengthCellValue;
import org.openpnp.gui.support.PartCellValue;
import org.openpnp.gui.support.RotationCellValue;
import org.openpnp.gui.support.TableUtils;
import org.openpnp.model.Abstract2DLocatable.Side;
import org.openpnp.model.Configuration;
import org.openpnp.model.Job;
import org.openpnp.model.JobRun;
import org.openpnp.model.Length;
import org.openpnp.model.Location;
import org.openpnp.model.Panel;
import org.openpnp.model.PanelLocation;
import org.openpnp.model.Part;
import org.openpnp.model.Placement;
import org.openpnp.model.Placement.ErrorHandling;
import org.openpnp.model.Placement.Type;
import org.openpnp.spi.Feeder;
import org.openpnp.model.PlacementsHolder;
import org.openpnp.model.PlacementsHolderLocation;
import org.openpnp.util.Utils2D;
import com.google.common.eventbus.Subscribe;

@SuppressWarnings("serial")
public class PlacementsHolderPlacementsTableModel extends AbstractObjectTableModel 
        implements ColumnAlignable, ColumnWidthSaveable, TableUtils.ColumnKinds, TableUtils.DefaultHidden {
    /**
     * Placed and rank: the status says whether a placement is placed, and the rank is for the few
     * jobs that order their placements by hand. The mockups' table has neither.
     */
    @Override
    public int[] getDefaultHiddenColumns() {
        return new int[] { 8, 11 };
    }

    private PlacementsHolder<?> placementsHolder = null;

    private String[] columnNames =
            new String[] {Translations.getString("PlacementsHolderPlacementsTableModel.ColumnName.Enabled"), //$NON-NLS-1$
                    Translations.getString("PlacementsHolderPlacementsTableModel.ColumnName.Id"), //$NON-NLS-1$
                    Translations.getString("PlacementsHolderPlacementsTableModel.ColumnName.Part"), //$NON-NLS-1$
                    Translations.getString("PlacementsHolderPlacementsTableModel.ColumnName.Side"), //$NON-NLS-1$
                    "X", //$NON-NLS-1$ 
                    "Y", //$NON-NLS-1$
                    Translations.getString("PlacementsHolderPlacementsTableModel.ColumnName.Rot"), //$NON-NLS-1$
                    Translations.getString("PlacementsHolderPlacementsTableModel.ColumnName.Type"), //$NON-NLS-1$
                    Translations.getString("PlacementsHolderPlacementsTableModel.ColumnName.Placed"), //$NON-NLS-1$
                    Translations.getString("PlacementsHolderPlacementsTableModel.ColumnName.Status"), //$NON-NLS-1$
                    Translations.getString("PlacementsHolderPlacementsTableModel.ColumnName.ErrorHandling"), //$NON-NLS-1$
                    Translations.getString("PlacementsHolderPlacementsTableModel.ColumnName.Rank"), //$NON-NLS-1$
                    Translations.getString("PlacementsHolderPlacementsTableModel.ColumnName.Comments")}; //$NON-NLS-1$

    private String[] propertyNames = new String[] {
            "enabled", //$NON-NLS-1$
            "id", //$NON-NLS-1$
            "part", //$NON-NLS-1$
            "side", //$NON-NLS-1$
            "location", //$NON-NLS-1$
            "location", //$NON-NLS-1$
            "location", //$NON-NLS-1$
            "type", //$NON-NLS-1$
            "placed", //$NON-NLS-1$
            "status", //$NON-NLS-1$
            "errorHandling", //$NON-NLS-1$
            "rank", //$NON-NLS-1$
            "comments" //$NON-NLS-1$
    };
    
    @SuppressWarnings("rawtypes")
    private Class[] columnTypes = new Class[] {Boolean.class, PartCellValue.class, Part.class, 
            Side.class, LengthCellValue.class, LengthCellValue.class, RotationCellValue.class, 
            Type.class, Boolean.class, PlacementStatus.class, ErrorHandling.class, Integer.class, String.class};
    
    // Numbers right, everything else left, as the stylesheet's table.grid: centred columns made
    // the coordinates hard to compare down the column.
    private int[] columnAlignments = new int[] {CENTER, LEFT, LEFT, LEFT, RIGHT, RIGHT, 
            RIGHT, LEFT, CENTER, LEFT, LEFT, RIGHT, LEFT};

    private TableUtils.Kind[] columnKinds = new TableUtils.Kind[] {TableUtils.Kind.Check,
            TableUtils.Kind.Id, TableUtils.Kind.Name, TableUtils.Kind.Status, TableUtils.Kind.Number,
            TableUtils.Kind.Number, TableUtils.Kind.Number, TableUtils.Kind.Status,
            TableUtils.Kind.Secondary, TableUtils.Kind.Status, TableUtils.Kind.Status,
            TableUtils.Kind.Secondary, TableUtils.Kind.Name};

    @Override
    public TableUtils.Kind[] getColumnKinds() {
        return columnKinds;
    }

    private int[] columnWidthTypes = new int[] {FIXED, FIXED, PROPORTIONAL, FIXED, FIXED, 
            FIXED, FIXED, FIXED, FIXED, FIXED, FIXED, FIXED, PROPORTIONAL};
    
    public enum Status {
        Ready,
        MissingPart,
        MissingFeeder,
        ZeroPartHeight,
        Disabled
    }

    private boolean localReferenceFrame = true;

    private PanelLocation parent = null;

    private List<Placement> placements = null;

    private PlacementsHolderLocation<?> placementsHolderLocation;
    private JobPlacementsPanel jobPlacementsPanel;
    private boolean editDefinition;
    private boolean isPanel;
    private final Configuration configuration;

    private Container container;

    /** Cleared by fireTableChanged; see partsWithEnabledFeeder(). */
    private Set<Part> partsWithEnabledFeeder;
    
    public PlacementsHolderPlacementsTableModel(Configuration configuration, Container container) {
        super();
        this.container = container;
        this.configuration = configuration;
        configuration.getBus().register(this);
    }
    
    @Subscribe
    public void definitionStructureChangedEventHandler(DefinitionStructureChangedEvent event) {
        if (event.source != this && event.source != container && 
                event.changedName.contentEquals("placements")) {
            SwingUtilities.invokeLater(() -> {
                fireTableDataChanged();
            });
        }
    }

    @Subscribe
    public void placementChangedEventHandler(PlacementChangedEvent evt) {
        if (evt.source != this && evt.placementsHolder == placementsHolder) {
            Placement placement = evt.placement;
            int index = indexOf(placement);
            if (index < 0) {
                for (index = 0; index < getRowCount(); index++) {
                    if (getRowObjectAt(index).getDefinition() == placement) {
                        break;
                    }
                }
            }
            if (index < getRowCount()) {
                final int idx = index;
                SwingUtilities.invokeLater(() -> {
                    fireTableCellUpdated(idx, TableModelEvent.ALL_COLUMNS);
                });
            }
        }
    }
    
    public PlacementsHolder<?> getPlacementsHolder() {
        return placementsHolder;
    }

    public void setPlacementsHolder(PlacementsHolder<?> placementsHolder) {
        this.placementsHolder = placementsHolder;
        isPanel = placementsHolder instanceof Panel;
        fireTableDataChanged();
    }
    
    public void setPlacements(List<Placement> placements) {
        this.placements = placements;
        placementsHolder = null;
        fireTableDataChanged();
    }

    public void setJobPlacementsPanel(JobPlacementsPanel jobPlacementsPanel) {
        this.jobPlacementsPanel = jobPlacementsPanel;
    }

    public void setPlacementsHolderLocation(PlacementsHolderLocation<?> placementsHolderLocation,
            boolean editDefinition) {
        this.placementsHolderLocation = placementsHolderLocation;
        this.editDefinition = editDefinition;
        if (placementsHolderLocation == null) {
            placementsHolder = null;
        }
        else {
            placementsHolder = placementsHolderLocation.getPlacementsHolder();
            isPanel = placementsHolder instanceof Panel;
        }
        fireTableDataChanged();
    }

    @Override
    public Placement getRowObjectAt(int index) {
        if (placementsHolder != null) {
            int limit = placementsHolder.getPlacements().size();
            if (isPanel && index >= limit) {
                int idx = index - limit;
                if (idx < ((Panel) placementsHolder).getPseudoPlacements().size()) {
                    return ((Panel) placementsHolder).getPseudoPlacement(index - limit);
                }
                else {
                    return null;
                }
            }
            return placementsHolder.getPlacement(index);
        }
        else {
            return placements.get(index);
        }
    }

    @Override
    public int indexOf(Object object) {
        if (placementsHolder != null) {
            int limit = placementsHolder.getPlacements().size();
            int index = placementsHolder.getPlacements().indexOf(object);
            if (isPanel && index < 0) {
                return ((Panel) placementsHolder).getPseudoPlacements().indexOf((Placement) object) + limit;
            }
            return index;
        }
        else {
            return placements.indexOf(object);
        }
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    public int getColumnCount() {
        return columnNames.length;
    }

    public int getRowCount() {
        if (placementsHolder != null) {
            int count = 0;
            if (isPanel) {
                count = ((Panel) placementsHolder).getPseudoPlacements().size();
            }
            return count + placementsHolder.getPlacements().size();
        }
        return (placements == null) ? 0 : placements.size();
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex != 1 && columnIndex != 9; //Can't edit the Id or Status
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnTypes[columnIndex];
    }
    
    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        try {
            Placement placement = getRowObjectAt(rowIndex);
            Placement definition = (Placement) placement.getDefinition();
            if (definition == null) {
                definition = placement;
            }
            if (columnIndex == 0) {
                if (editDefinition) {
                    definition.setEnabled((Boolean) aValue);
                }
                else {
                    placement.setEnabled((Boolean) aValue);
                }
                fireTableCellUpdated(rowIndex, columnIndex);
                if (jobPlacementsPanel != null) {
                    jobPlacementsPanel.updateActivePlacements();
                }
            }
            else if (columnIndex == 2) {
                definition.setPart((Part) aValue);
                fireTableCellUpdated(rowIndex, columnIndex);
            }
            else if (columnIndex == 3) {
                definition.setSide((Side) aValue);
                fireTableCellUpdated(rowIndex, columnIndex);
                if (jobPlacementsPanel != null) {
                    jobPlacementsPanel.updateActivePlacements();
                }
            }
            else if (columnIndex == 4) {
                LengthCellValue value = (LengthCellValue) aValue;
                value.setDisplayNativeUnits(true);
                Length length = value.getLength();
                Location oldValue = placement.getLocation();
                Location location = Length.setLocationField(configuration, oldValue, length, Length.Field.X,
                        true);
                definition.setLocation(location);
                fireTableCellUpdated(rowIndex, columnIndex);
            }
            else if (columnIndex == 5) {
                LengthCellValue value = (LengthCellValue) aValue;
                value.setDisplayNativeUnits(true);
                Length length = value.getLength();
                Location oldValue = placement.getLocation();
                Location location = Length.setLocationField(configuration, oldValue, length, Length.Field.Y,
                        true);
                definition.setLocation(location);
                fireTableCellUpdated(rowIndex, columnIndex);
            }
            else if (columnIndex == 6) {
                RotationCellValue value = (RotationCellValue) aValue;
                double rotation = value.getRotation();
                Location oldValue = placement.getLocation();
                Location location = oldValue.derive(null, null, null, rotation);
                definition.setLocation(location);
                fireTableCellUpdated(rowIndex, columnIndex);
            }
            else if (columnIndex == 7) {
                definition.setType((Type) aValue);
                fireTableCellUpdated(rowIndex, columnIndex);
                if (jobPlacementsPanel != null) {
                    jobPlacementsPanel.updateActivePlacements();
                }
            }
            else if (columnIndex == 8) {
                jobPlacementsPanel.getJobPanel().getJob()
                    .storePlacedStatus(placementsHolderLocation, placement.getId(), (Boolean) aValue);
                fireTableCellUpdated(rowIndex, columnIndex);
                jobPlacementsPanel.updateActivePlacements();
            }
            else if (columnIndex == 10) {
                if (editDefinition) {
                    definition.setErrorHandling((ErrorHandling) aValue);
                }
                else {
                    placement.setErrorHandling((ErrorHandling) aValue);
                }
                fireTableCellUpdated(rowIndex, columnIndex);
             }
            else if (columnIndex == 11) {
                definition.setRank((Integer) aValue);
                fireTableCellUpdated(rowIndex, columnIndex);
            }
            else if (columnIndex == 12) {
                definition.setComments((String) aValue);
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }
        catch (Exception e) {
            org.openpnp.gui.support.TableUtils.rejected(this, columnIndex, aValue, e);
        }
    }

    public void fireTableCellUpdated(Placement placement, String columnName) {
        fireTableCellUpdated(indexOf(placement), findColumn(columnName));
    }
    
    public void fireTableCellUpdated(Placement placement, int columnIndex) {
        fireTableCellUpdated(indexOf(placement), columnIndex);
    }
    
    @Override
    public void fireTableCellUpdated(int row, int column) {
        super.fireTableCellUpdated(row, column);
        String propName = "ALL";
        Object newValue = null;
        if (column >= 0 && column < propertyNames.length) {
            propName = propertyNames[column];
            newValue = getValueAt(row, column);
        }
        configuration.getBus().post(new PlacementChangedEvent(placementsHolder, 
                getRowObjectAt(row), propName, null, newValue, this));
    }
    
    public Object getValueAt(int row, int col) {
        Placement placement = getRowObjectAt(row);
        if (placement == null) {
            return null;
        }
        Location loc;
        Side side;
        if (localReferenceFrame || parent == null) {
            loc = placement.getLocation();
            side = placement.getSide();
        }
        else {
            loc = Utils2D.calculateBoardPlacementLocation(parent, placement);
            side = placement.getSide().flip(parent.getGlobalSide() == Side.Bottom);
        }
        switch (col) {
			case 0:
				return placement.isEnabled();
            case 1:
                return new PartCellValue(placement.getId());
            case 2:
                return placement.getPart();
            case 3:
                return side;
            case 4:
                return new LengthCellValue(loc.getLengthX(), true, true);
            case 5:
                return new LengthCellValue(loc.getLengthY(), true, true);
            case 6:
                return new RotationCellValue(loc.getRotation(), true, true);
            case 7:
                return placement.getType();
            case 8:
                // TODO: It would be better for this to be pushed in by a listener than pulled
                // during rendering, but it is only a map lookup now, so it is no longer a cost
                // worth restructuring the panel for.
                return job() != null && job().retrievePlacedStatus(placementsHolderLocation, placement.getId());
            case 9: {
                Job job = job();
                return new PlacementStatus(placement.getType(), getPlacementStatus(placement),
                        job == null ? null : job.getRun().get(JobRun.key(placementsHolderLocation, placement.getId())),
                        job != null && job.retrievePlacedStatus(placementsHolderLocation, placement.getId()));
            }
            case 10:
                return placement.getErrorHandling();
            case 11:
                return placement.getRank();
            case 12:
                return placement.getComments();
            default:
                return null;
        }
    }

    /** The job the page shows, or null before there is a window. */
    private static Job job() {
        MainFrame frame = MainFrame.get();
        return frame == null || frame.getJobTab() == null ? null : frame.getJobTab().getJob();
    }

    /**
     * What the status column shows: the current run's state of the placement where the run has
     * reached it, whether it is placed, and otherwise whether it is ready to be. The mockups' pills
     * - placed, placing on N1, waiting for a refill of F-08, skipped, pick failed three times -
     * where the column only ever said whether the part and its feeder were there. Its text is
     * what the table shows, so that the filter finds what is read in the column.
     */
    public static final class PlacementStatus implements Comparable<PlacementStatus> {
        private final Type type;
        private final Status readiness;
        private final JobRun.PlacementRun run;
        private final boolean placed;

        public PlacementStatus(Type type, Status readiness, JobRun.PlacementRun run, boolean placed) {
            this.type = type;
            this.readiness = readiness;
            this.run = run;
            this.placed = placed;
        }

        public Status getReadiness() {
            return readiness;
        }

        /** The run's record of the placement, or null if the run has not reached it. */
        public JobRun.PlacementRun getRun() {
            return run;
        }

        public boolean isPlaced() {
            return placed;
        }

        /** The run's state where there is one, placed when placed before the run, null otherwise. */
        public JobRun.State getState() {
            if (run != null && run.getState() != null) {
                return run.getState();
            }
            return placed ? JobRun.State.Placed : null;
        }

        /** A fiducial is located, not placed: it has no status of its own. */
        public boolean isFiducial() {
            return type == Type.Fiducial;
        }

        public String getText() {
            if (isFiducial()) {
                return "\u2014"; //$NON-NLS-1$
            }
            JobRun.State state = getState();
            if (state != null) {
                switch (state) {
                    case Placed:
                        return Translations.getString("JobPlacementsPanel.Status.Placed"); //$NON-NLS-1$
                    case Placing:
                        return String.format(Translations.getString("JobPlacementsPanel.Status.Placing"), //$NON-NLS-1$
                                run.getNozzle());
                    case WaitingForFeeder:
                        return String.format(Translations.getString("JobPlacementsPanel.Status.WaitingForFeeder"), //$NON-NLS-1$
                                run.getFeeder());
                    case Skipped:
                        return Translations.getString("JobPlacementsPanel.Status.Skipped"); //$NON-NLS-1$
                    case Error:
                        if (run.getPickFailures() > 0) {
                            return String.format(Translations.getString("JobPlacementsPanel.Status.PickFailed"), //$NON-NLS-1$
                                    run.getPickFailures());
                        }
                        return String.format(Translations.getString("JobPlacementsPanel.Status.Error"), //$NON-NLS-1$
                                shorten(org.openpnp.gui.shell.ErrorMessages.explain(null, run.getError()).what));
                    default:
                        break;
                }
            }
            switch (readiness) {
                case Ready:
                    return Translations.getString("JobPlacementsPanel.Status.Pending"); //$NON-NLS-1$
                case MissingFeeder:
                    return Translations.getString("JobPlacementsPanel.StatusRenderer.StatusMissingFeeder"); //$NON-NLS-1$
                case ZeroPartHeight:
                    return Translations.getString("JobPlacementsPanel.StatusRenderer.StatusPartHeight"); //$NON-NLS-1$
                case MissingPart:
                    return Translations.getString("JobPlacementsPanel.StatusRenderer.StatusMissingPart"); //$NON-NLS-1$
                case Disabled:
                    return Translations.getString("JobPlacementsPanel.StatusRenderer.StatusDisabled"); //$NON-NLS-1$
                default:
                    return readiness.toString();
            }
        }

        /** The whole error, for the cell's tooltip, where the pill has room for the start of it. */
        public String getDetail() {
            return run != null && run.getState() == JobRun.State.Error ? run.getError() : null;
        }

        private static String shorten(String text) {
            if (text == null) {
                return ""; //$NON-NLS-1$
            }
            String line = text.trim().split("\n")[0]; //$NON-NLS-1$
            return line.length() > 24 ? line.substring(0, 23) + "\u2026" : line; //$NON-NLS-1$
        }

        /** Sorted by what needs attention: errors first, then waits, work under way, and what is done last. */
        private int order() {
            if (isFiducial()) {
                return 9;
            }
            JobRun.State state = getState();
            if (state != null) {
                switch (state) {
                    case Error:
                        return 0;
                    case WaitingForFeeder:
                        return 1;
                    case Placing:
                        return 2;
                    case Skipped:
                        return 7;
                    case Placed:
                        return 8;
                    default:
                        break;
                }
            }
            return readiness == Status.Ready ? 6 : readiness == Status.Disabled ? 7 : 3;
        }

        @Override
        public int compareTo(PlacementStatus other) {
            return Integer.compare(order(), other.order());
        }

        @Override
        public String toString() {
            return getText();
        }
    }

    // TODO: Ideally this would all come from the JobPlanner, but this is a
    // good start for now.
    private Status getPlacementStatus(Placement placement) {
        if (placement.getPart() == null) {
            return Status.MissingPart;
        }
        if (!placement.isEnabled()) {
            return Status.Disabled;

        }
        if (placement.getType() == Placement.Type.Placement && placement.isEnabled()) {
            if (!partsWithEnabledFeeder().contains(placement.getPart())) {
                return Status.MissingFeeder;
            }

            if (placement.getPart().isPartHeightUnknown()) {
                return Status.ZeroPartHeight;
            }
        }
        return Status.Ready;
    }

    /**
     * The parts that an enabled feeder can supply. This used to be a scan of every feeder, run for
     * each cell of the status column: once per visible row on a repaint, and once per row in the
     * whole table when it is sorted by that column, so the work was the placement count times the
     * feeder count.
     * <p>
     * Built on first use after the table data changes. A feeder enabled or reassigned while this
     * table is on screen is therefore not reflected until the table reloads - which was already
     * the case, since nothing here listens for feeder changes to repaint in the first place.
     * <p>
     * Membership is by identity, matching the reference comparison this replaces.
     */
    private Set<Part> partsWithEnabledFeeder() {
        if (partsWithEnabledFeeder == null) {
            Set<Part> parts = Collections.newSetFromMap(new IdentityHashMap<Part, Boolean>());
            for (Feeder feeder : configuration.getMachine().getFeeders()) {
                if (feeder.isEnabled() && feeder.getPart() != null) {
                    parts.add(feeder.getPart());
                }
            }
            partsWithEnabledFeeder = parts;
        }
        return partsWithEnabledFeeder;
    }

    @Override
    public void fireTableChanged(TableModelEvent e) {
        // Every fireTableXxx of the superclass funnels through here, so this is the one place the
        // feeder lookup has to be dropped.
        partsWithEnabledFeeder = null;
        super.fireTableChanged(e);
    }

    public void setLocalReferenceFrame(boolean b) {
        localReferenceFrame = b;
        fireTableDataChanged();
    }

    @Override
    public int[] getColumnAlignments() {
        return columnAlignments;
    }

    @Override
    public int[] getColumnWidthTypes() {
        return columnWidthTypes;
    }
}
