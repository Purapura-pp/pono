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

import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import org.openpnp.Translations;
import org.openpnp.machine.reference.ReferenceHead;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.wizards.HeadForm;
import org.openpnp.model.CalibrationStep;
import org.openpnp.spi.Head;

/**
 * The head: where it parks, how it homes, the fiducials calibration measures against, and its Z
 * probe. These were only in the properties column of the element tree, behind the head's row and
 * a folded section; the pump stays with the nozzles, whose vacuum it supplies.
 */
final class HeadTopic extends Topic {
    private final MachineSettingsPanel page;
    private final ReferenceMachine machine;

    HeadTopic(MachineSettingsPanel page, ReferenceMachine machine) {
        super(MachineSettingsPanel.HEAD, "pin"); //$NON-NLS-1$
        this.page = page;
        this.machine = machine;
    }

    @Override
    protected JComponent build() {
        List<JComponent> sections = new ArrayList<>();
        Head head = head();
        if (head instanceof ReferenceHead) {
            // The same form as the element tree's properties column shows for the head, without
            // the pump: one place to edit each value.
            sections.add(forms.add(HeadForm.build((ReferenceHead) head, false)));
        }
        return MachineSettingsPanel.page(
                Guide.of(Translations.getString("MachineSettings.Guide.Head"), //$NON-NLS-1$
                        Guide.toCalibration(page, CalibrationStep.VisualHoming, CalibrationStep.PrimaryFiducial,
                                CalibrationStep.SecondaryFiducial)),
                sections.toArray(new JComponent[0]));
    }

    private Head head() {
        try {
            return machine.getDefaultHead();
        }
        catch (Exception e) {
            return null;
        }
    }
}
