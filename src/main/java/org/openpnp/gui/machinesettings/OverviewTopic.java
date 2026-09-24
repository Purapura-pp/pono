/*
 * Copyright (C) 2026 Pono contributors
 * 
 * This file is part of Pono, a modified version of OpenPnP.
 * 
 * Pono is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * Pono is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Pono. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package org.openpnp.gui.machinesettings;

import javax.swing.JComponent;

import org.openpnp.gui.MachineOverviewPanel;
import org.openpnp.machine.reference.ReferenceMachine;

/** The machine at a glance: its axes, cameras, drivers and nozzles, read only, as four cards. */
final class OverviewTopic extends Topic {
    private final ReferenceMachine machine;
    private MachineOverviewPanel overview;

    OverviewTopic(ReferenceMachine machine) {
        super(MachineSettingsPanel.OVERVIEW, "info"); //$NON-NLS-1$
        this.machine = machine;
    }

    @Override
    protected JComponent build() {
        overview = new MachineOverviewPanel(machine);
        return overview;
    }

    @Override
    void shown() {
        if (overview != null) {
            overview.refresh();
        }
    }
}
