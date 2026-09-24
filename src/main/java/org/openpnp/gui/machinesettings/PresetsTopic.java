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

import org.openpnp.Translations;
import org.openpnp.gui.shell.Forms;
import org.openpnp.machine.reference.ReferenceMachine;

/** The model the machine is and the presets it can be set from, the mockups' 24. */
final class PresetsTopic extends Topic {
    private final MachineSettingsPanel page;
    private final ReferenceMachine machine;

    PresetsTopic(MachineSettingsPanel page, ReferenceMachine machine) {
        super(MachineSettingsPanel.PRESETS, "layers"); //$NON-NLS-1$
        this.page = page;
        this.machine = machine;
    }

    @Override
    protected JComponent build() {
        return Forms.emptyState("layers", title(), //$NON-NLS-1$
                Translations.getString("MachineSettings.Presets.Soon")); //$NON-NLS-1$
    }
}
