/*
 * Copyright (C) 2022 Jason von Nieda <jason@vonnieda.org>, Tony Luken <tonyluken62+openpnp@gmail.com>
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

package org.openpnp.gui.support;

import java.awt.Component;

import javax.swing.JTable;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;

import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.tablemodel.PlacementsHolderLocationsTableModel;
import org.openpnp.model.BoardLocation;
import org.openpnp.model.PlacementsHolderLocation;

/**
 * A board or panel instance's id, indented by how deep in the panels it sits, after the icon of
 * what it is. The icons were the old coloured ones, and the rows filled their own alternate colour
 * where the tables have none; this is drawn in the table's own colours.
 */
@SuppressWarnings("serial")
public class CustomPlacementsHolderRenderer extends DefaultTableCellRenderer {
    /** How far a nesting level indents. */
    private static final int INDENT = 14;

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
            boolean hasFocus, int row, int column) {
        String uniqueId = value == null ? "" : value.toString(); //$NON-NLS-1$
        String id = uniqueId.substring(uniqueId.lastIndexOf(PlacementsHolderLocation.ID_DELIMITTER) + 1);
        super.getTableCellRendererComponent(table, id, isSelected, hasFocus, row, column);
        int depth = 0;
        int at = -1;
        while ((at = uniqueId.indexOf(PlacementsHolderLocation.ID_DELIMITTER, at + 1)) >= 0) {
            depth++;
        }
        setBorder(new EmptyBorder(0, 8 + depth * INDENT, 0, 8));
        boolean board = table.getModel() instanceof PlacementsHolderLocationsTableModel
                && ((PlacementsHolderLocationsTableModel) table.getModel())
                        .getPlacementsHolderLocation(table.convertRowIndexToModel(row)) instanceof BoardLocation;
        setIcon(Ui.icon(board ? "board" : "panel", 14, isSelected ? getForeground() : Ui.text2())); //$NON-NLS-1$ //$NON-NLS-2$
        setIconTextGap(6);
        setFont(table.getFont().deriveFont(java.awt.Font.BOLD));
        return this;
    }
}
