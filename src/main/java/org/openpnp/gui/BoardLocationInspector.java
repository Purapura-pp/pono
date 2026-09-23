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

import java.util.List;

import org.openpnp.Translations;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.support.FeederDescriptions;
import org.openpnp.model.Abstract2DLocatable.Side;
import org.openpnp.model.BoardLocation;
import org.openpnp.model.Location;
import org.openpnp.model.PlacementsHolderLocation;

/**
 * The properties of a board or panel in the job, for the properties column when one is selected
 * on the job page, which showed nothing for them: what it is, where it sits and which side is up,
 * and whether it is placed and its fiducials checked. Its position and side are edited for a board
 * directly in the job, as the boards table allows; one inside a panel follows the panel.
 */
public final class BoardLocationInspector {
    private BoardLocationInspector() {
    }

    /** What the form edits, written as the boards table writes it. */
    public static final class Bean {
        private final PlacementsHolderLocation<?> location;

        Bean(PlacementsHolderLocation<?> location) {
            this.location = location;
        }

        public Location getLocation() {
            return location.getGlobalLocation();
        }

        public void setLocation(Location value) {
            location.setGlobalLocation(value);
        }

        public Side getSide() {
            return location.getGlobalSide();
        }

        /** As the boards table: a board turned over keeps where it is on the machine. */
        public void setSide(Side side) {
            if (side == location.getGlobalSide()) {
                return;
            }
            Location saved = location.getGlobalLocation();
            location.setGlobalSide(side);
            location.setGlobalLocation(saved);
            location.setLocalToParentTransform(null);
        }

        public boolean isEnabled() {
            return location.isLocallyEnabled();
        }

        public void setEnabled(boolean enabled) {
            if (location.getParent() == null || location.getParent().isEnabled()) {
                location.setLocallyEnabled(enabled);
            }
        }

        public boolean isCheckFiducials() {
            return location.isCheckFiducials();
        }

        public void setCheckFiducials(boolean check) {
            location.setCheckFiducials(check);
        }

        public String getName() {
            return location.getPlacementsHolder() == null ? "" : location.getPlacementsHolder().getName(); //$NON-NLS-1$
        }

        public String getFile() {
            return location.getPlacementsHolder() == null || location.getPlacementsHolder().getFile() == null ? "\u2014" //$NON-NLS-1$
                    : location.getPlacementsHolder().getFile().getPath();
        }

        public String getSize() {
            if (location.getPlacementsHolder() == null) {
                return "\u2014"; //$NON-NLS-1$
            }
            Location dimensions = location.getPlacementsHolder().getDimensions();
            return FeederDescriptions.length(dimensions.getLengthX()) + " \u00d7 " //$NON-NLS-1$
                    + FeederDescriptions.length(dimensions.getLengthY());
        }

        /** Where it is, for a board inside a panel, which the panel places. */
        public String getPositionText() {
            Location l = location.getGlobalLocation();
            return String.format(java.util.Locale.ROOT, "X %.3f \u00b7 Y %.3f \u00b7 Z %.3f \u00b7 %.1f\u00b0", //$NON-NLS-1$
                    l.getX(), l.getY(), l.getZ(), l.getRotation());
        }
    }

    /**
     * @param topLevel Whether it is directly in the job, where its position is its own; the
     *                 boards table edits the same.
     */
    public static FormWizard build(PlacementsHolderLocation<?> location, boolean topLevel) {
        Bean bean = new Bean(location);
        Form.Builder form = Form.of(bean).named(location.getUniqueId()).ownedByJob();
        form.section(location instanceof BoardLocation ? "BoardLocationInspector.Board" //$NON-NLS-1$
                : "BoardLocationInspector.Panel", location instanceof BoardLocation ? "board" : "panel"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        form.readOnly("name", "BoardLocationInspector.Name"); //$NON-NLS-1$ //$NON-NLS-2$
        form.readOnly("size", "BoardLocationInspector.Size"); //$NON-NLS-1$ //$NON-NLS-2$
        form.readOnly("file", "BoardLocationInspector.File"); //$NON-NLS-1$ //$NON-NLS-2$
        form.section("BoardLocationInspector.Position", "move"); //$NON-NLS-1$ //$NON-NLS-2$
        if (topLevel) {
            form.location("location", "BoardLocationInspector.Location", true).locationButtons(); //$NON-NLS-1$ //$NON-NLS-2$
            form.segmented("side", "BoardLocationInspector.Side", List.of(Side.Top, Side.Bottom)); //$NON-NLS-1$ //$NON-NLS-2$
        }
        else {
            form.note("BoardLocationInspector.FollowsPanel"); //$NON-NLS-1$
            form.readOnly("positionText", "BoardLocationInspector.Location"); //$NON-NLS-1$ //$NON-NLS-2$
            form.readOnly("side", "BoardLocationInspector.Side"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        form.section("BoardLocationInspector.Options", "gear"); //$NON-NLS-1$ //$NON-NLS-2$
        form.toggle("enabled", "BoardLocationInspector.Enabled", "BoardLocationInspector.EnabledNote"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        form.toggle("checkFiducials", "BoardLocationInspector.CheckFiducials", //$NON-NLS-1$ //$NON-NLS-2$
                "BoardLocationInspector.CheckFiducialsNote"); //$NON-NLS-1$
        return form.build();
    }

    /** The heading's second line: what it is and what it is called. */
    public static String subtitle(PlacementsHolderLocation<?> location) {
        return Translations.getString(location instanceof BoardLocation ? "BoardLocationInspector.Board" //$NON-NLS-1$
                : "BoardLocationInspector.Panel") //$NON-NLS-1$
                + (location.getPlacementsHolder() == null ? "" : " \u00b7 " + location.getPlacementsHolder().getName()); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
