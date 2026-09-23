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

import org.openpnp.events.PlacementsHolderChangedEvent;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.Board;
import org.openpnp.model.Configuration;
import org.openpnp.model.Location;
import org.openpnp.model.PlacementsHolder;

/**
 * A board's or a panel's definition in the properties column: its name, its file and its size,
 * which were edited in place in the cells of the list of definitions.
 */
public final class DefinitionForm {
    private DefinitionForm() {
    }

    public static class Bean extends AbstractModelObject {
        private final Configuration configuration;
        private final PlacementsHolder<?> holder;

        Bean(Configuration configuration, PlacementsHolder<?> holder) {
            this.configuration = configuration;
            this.holder = holder;
        }

        public String getName() {
            return holder.getName();
        }

        public void setName(String name) {
            String old = holder.getName();
            holder.setName(name);
            configuration.getBus().post(new PlacementsHolderChangedEvent(holder, "name", old, name, this)); //$NON-NLS-1$
        }

        public String getFile() {
            return DefinitionList.where(holder.getFile());
        }

        public Location getDimensions() {
            return holder.getDimensions();
        }

        public void setDimensions(Location dimensions) {
            Location old = holder.getDimensions();
            holder.setDimensions(dimensions);
            configuration.getBus()
                    .post(new PlacementsHolderChangedEvent(holder, "dimensions", old, dimensions, this)); //$NON-NLS-1$
        }
    }

    /**
     * The board's or the panel's file holds what is applied here: it is marked as changed, and
     * saved with the Save on its page or with the job. The machine's configuration is not touched.
     */
    public static FormWizard build(Configuration configuration, PlacementsHolder<?> holder) {
        return Form.of(new Bean(configuration, holder)).named(holder.getName()).ownedByJob()
                .section(holder instanceof Board ? "DefinitionForm.Board" : "DefinitionForm.Panel", //$NON-NLS-1$ //$NON-NLS-2$
                        holder instanceof Board ? "board" : "panel") //$NON-NLS-1$ //$NON-NLS-2$
                .text("name", "DefinitionForm.Name") //$NON-NLS-1$ //$NON-NLS-2$
                .readOnly("file", "DefinitionForm.File") //$NON-NLS-1$ //$NON-NLS-2$
                .location("dimensions", "DefinitionForm.Size", false) //$NON-NLS-1$ //$NON-NLS-2$
                .build();
    }
}
