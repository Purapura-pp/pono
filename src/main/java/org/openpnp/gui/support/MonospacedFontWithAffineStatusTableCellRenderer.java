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

import java.awt.Color;
import java.awt.Component;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import org.openpnp.Translations;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.tablemodel.PlacementsHolderLocationsTableModel;
import org.openpnp.model.PlacementsHolderLocation.PlacementsTransformStatus;
import org.pmw.tinylog.Logger;

/**
 * A board's coordinates in the mono face, tinted by where its position came from: the accent
 * where its own fiducials set it, green where the panel it is on set it. The tints were a light
 * blue and a light green fill with the text left dark, which in the dark theme made the one set
 * of unreadable cells on the page, and nothing said what the colours meant.
 */
@SuppressWarnings("serial")
public class MonospacedFontWithAffineStatusTableCellRenderer extends DefaultTableCellRenderer {
    /** How much of the colour is left in the tint. */
    private static final int TINT_ALPHA = 46;

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
            boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        setFont(Ui.mono(table.getFont().getSize2D(), java.awt.Font.PLAIN));
        setToolTipText(null);
        if (isSelected) {
            return this;
        }
        setBackground(table.getBackground());
        try {
            PlacementsTransformStatus transformStatus = ((PlacementsHolderLocationsTableModel) table.getModel())
                    .getPlacementsHolderLocation(table.convertRowIndexToModel(row)).getPlacementsTransformStatus();
            if (transformStatus == PlacementsTransformStatus.LocallySet) {
                setBackground(tint(Ui.accent(), table.getBackground()));
                setToolTipText(Translations.getString("JobPanel.Board.Transform.Local")); //$NON-NLS-1$
            }
            else if (transformStatus == PlacementsTransformStatus.GloballySet) {
                setBackground(tint(Ui.ok(), table.getBackground()));
                setToolTipText(Translations.getString("JobPanel.Board.Transform.Global")); //$NON-NLS-1$
            }
        }
        catch (Exception ex) {
            // Rendering path, keep this quiet: the row just gets the plain background.
            Logger.trace(ex, "Failed to determine the placements transform status of row {}.", row); //$NON-NLS-1$
        }
        return this;
    }

    /** The colour laid faintly over the background, as an opaque colour a cell can paint. */
    private static Color tint(Color colour, Color background) {
        float a = TINT_ALPHA / 255f;
        return new Color(
                Math.round(colour.getRed() * a + background.getRed() * (1 - a)),
                Math.round(colour.getGreen() * a + background.getGreen() * (1 - a)),
                Math.round(colour.getBlue() * a + background.getBlue() * (1 - a)));
    }
}
