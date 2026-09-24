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

package org.openpnp.machine.reference.signaler.wizards;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.machine.reference.signaler.ActuatorSignaler;
import org.openpnp.machine.reference.signaler.SoundSignaler;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.base.AbstractJobProcessor;

/** The signalers' forms: an actuator switched with a job state, and the sounds. */
public final class SignalerForms {
    private SignalerForms() {
    }

    public static FormWizard actuator(ActuatorSignaler signaler) {
        List<Actuator> actuators = new ArrayList<>(signaler.getMachine().getActuators());
        List<AbstractJobProcessor.State> states = new ArrayList<>();
        states.add(null);
        states.addAll(Arrays.asList(AbstractJobProcessor.State.values()));
        return Form.of(signaler).named("SignalerForms.Title") //$NON-NLS-1$
                .section("SignalerForms.Actuator", "bell") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("actuator", "ActuatorSignalerConfigurationWizard.ActuatorLabel", actuators, null) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("jobState", "ActuatorSignalerConfigurationWizard.JobStateLabel", states, null) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("SignalerForms.JobState.Hint") //$NON-NLS-1$
                .build();
    }

    public static FormWizard sound(SoundSignaler signaler) {
        return Form.of(signaler).named("SignalerForms.Title") //$NON-NLS-1$
                .section("SignalerForms.Sound", "bell") //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("enableErrorSound", "SignalerForms.Error", "SignalerForms.Error.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .toggle("enableFinishedSound", "SignalerForms.Finished", "SignalerForms.Finished.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .build();
    }
}
