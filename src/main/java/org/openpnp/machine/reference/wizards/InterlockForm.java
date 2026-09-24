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

package org.openpnp.machine.reference.wizards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.machine.reference.ActuatorInterlockMonitor;
import org.openpnp.machine.reference.ActuatorInterlockMonitor.ActuatorState;
import org.openpnp.machine.reference.ActuatorInterlockMonitor.InterlockType;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Axis;
import org.openpnp.spi.CoordinateAxis;
import org.openpnp.spi.base.AbstractMachine;

/**
 * An actuator's axis interlock: which axes it watches and what it does or confirms as they move,
 * and the conditions under which it applies.
 */
public final class InterlockForm {
    private InterlockForm() {
    }

    private static final String TYPE = "interlockType"; //$NON-NLS-1$

    private static List<CoordinateAxis> axes(AbstractMachine machine) {
        List<CoordinateAxis> axes = new ArrayList<>();
        for (Axis axis : machine.getAxes()) {
            if (axis instanceof CoordinateAxis) {
                axes.add((CoordinateAxis) axis);
            }
        }
        axes.sort(Comparator.comparing(Axis::getName, Comparator.nullsFirst(Comparator.naturalOrder())));
        axes.add(0, null);
        return axes;
    }

    private static boolean readsNumber(Object type) {
        return type instanceof InterlockType && ((InterlockType) type).isReadingDouble();
    }

    private static boolean readsText(Object type) {
        return type instanceof InterlockType && ((InterlockType) type).isReadingString();
    }

    public static FormWizard build(AbstractMachine machine, Actuator actuator, ActuatorInterlockMonitor monitor) {
        List<CoordinateAxis> axes = axes(machine);
        List<Actuator> actuators = new ArrayList<>();
        actuators.add(null);
        actuators.addAll(actuator.getHead() != null ? actuator.getHead().getActuators() : machine.getActuators());
        return Form.of(monitor).named("InterlockForm.Title") //$NON-NLS-1$
                .section("ActuatorInterlockMonitorConfigurationWizard.panelInterlock.Border.title", "lock") //$NON-NLS-1$ //$NON-NLS-2$
                .choice(TYPE, "ActuatorInterlockMonitorConfigurationWizard.lblFunction.text", InterlockType.class) //$NON-NLS-1$
                .hint("InterlockForm.Type.Hint") //$NON-NLS-1$
                .choice("interlockAxis1", "ActuatorInterlockMonitorConfigurationWizard.lblAxis1.text", axes, null) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("interlockAxis2", "ActuatorInterlockMonitorConfigurationWizard.lblAxis2.text", axes, null) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("interlockAxis3", "ActuatorInterlockMonitorConfigurationWizard.lblAxis.text", axes, null) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("interlockAxis4", "ActuatorInterlockMonitorConfigurationWizard.lblAxis_1.text", axes, null) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("confirmationGoodMin", "InterlockForm.Min").format("%f").width(140) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen(TYPE, InterlockForm::readsNumber)
                .decimal("confirmationGoodMax", "InterlockForm.Max").format("%f").width(140) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen(TYPE, InterlockForm::readsNumber)
                .hint("InterlockForm.Range.Hint") //$NON-NLS-1$
                .text("confirmationPattern", "ActuatorInterlockMonitorConfigurationWizard.lblConfirmationPattern.text") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen(TYPE, InterlockForm::readsText)
                .hint("InterlockForm.Pattern.Hint") //$NON-NLS-1$
                .toggle("confirmationByRegex", "InterlockForm.Regex", "ActuatorInterlockMonitorConfigurationWizard.lblRegex.toolTipText") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen(TYPE, InterlockForm::readsText)
                .section("ActuatorInterlockMonitorConfigurationWizard.panelCondition.Border.title", "filter") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("conditionalActuator", "ActuatorInterlockMonitorConfigurationWizard.lblActuator.text", actuators, null) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("conditionalActuatorState", "InterlockForm.ActuatorState", ActuatorState.class) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("conditionalActuator", v -> v != null) //$NON-NLS-1$
                .hint("ActuatorInterlockMonitorConfigurationWizard.lblActuator.toolTipText") //$NON-NLS-1$
                .percent("conditionalSpeedMin", "InterlockForm.SpeedMin").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .percent("conditionalSpeedMax", "InterlockForm.SpeedMax").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("InterlockForm.Speed.Hint") //$NON-NLS-1$
                .build();
    }
}
