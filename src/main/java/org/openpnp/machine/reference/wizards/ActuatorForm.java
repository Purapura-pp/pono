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
import java.util.List;

import javax.swing.SwingUtilities;

import org.openpnp.gui.MainFrame;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.machine.reference.HttpActuator;
import org.openpnp.machine.reference.ReferenceActuator;
import org.openpnp.machine.reference.ReferenceActuator.MachineStateActuation;
import org.openpnp.machine.reference.ScriptActuator;
import org.openpnp.spi.Actuator.ActuatorValueType;
import org.openpnp.spi.Driver;
import org.openpnp.spi.base.AbstractActuator.ActuatorCoordinationEnumType;

/**
 * The actuators' forms. What every actuator has - its value, what it does as the machine's state
 * changes, how it waits for the machine and, on a head, where it is - is {@link #common}, the
 * part the actuators' base wizard was; each kind puts its own settings first.
 */
public final class ActuatorForm {
    private ActuatorForm() {
    }

    private static final String VALUE = "valueType"; //$NON-NLS-1$

    /**
     * The sections every actuator has, for a form on the actuator itself. The value type and the
     * interlock switch bring tabs of their own, so Apply shows the actuator again when they
     * change.
     */
    public static Form.Builder common(Form.Builder form, ReferenceActuator actuator) {
        ActuatorValueType[] type = {actuator.getValueType()};
        boolean[] interlock = {actuator.isInterlockActuator()};
        form.section("ActuatorForm.Value", "zap") //$NON-NLS-1$ //$NON-NLS-2$
                .choice(VALUE, "AbstractActuatorConfigurationWizard.GeneralPanel.ValueTypeLabel.text", //$NON-NLS-1$
                        ActuatorValueType.class)
                .hint("ActuatorForm.ValueType.Hint") //$NON-NLS-1$
                .decimal("defaultOnDouble", "ActuatorForm.On").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen(VALUE, ActuatorValueType.Double::equals)
                .decimal("defaultOffDouble", "ActuatorForm.Off").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen(VALUE, ActuatorValueType.Double::equals)
                .text("defaultOnString", "ActuatorForm.On") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen(VALUE, ActuatorValueType.String::equals)
                .text("defaultOffString", "ActuatorForm.Off") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen(VALUE, ActuatorValueType.String::equals)
                .integer("index", "AbstractActuatorConfigurationWizard.GeneralPanel.IndexLabel.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("ActuatorForm.Index.Hint") //$NON-NLS-1$
                .section("ActuatorForm.MachineState", "power") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("enabledActuation", "ActuatorForm.Enabled", MachineStateActuation.class) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("homedActuation", "ActuatorForm.Homed", MachineStateActuation.class) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("disabledActuation", "ActuatorForm.Disabled", MachineStateActuation.class) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("ActuatorForm.MachineState.Hint") //$NON-NLS-1$
                .section("ActuatorForm.Coordination", "clock").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .choice("coordinatedBeforeActuateEnum", "ActuatorForm.BeforeActuate", //$NON-NLS-1$ //$NON-NLS-2$
                        List.of(ActuatorCoordinationEnumType.None, ActuatorCoordinationEnumType.CommandStillstand,
                                ActuatorCoordinationEnumType.WaitForStillstand), null)
                .choice("coordinatedAfterActuateEnum", "ActuatorForm.AfterActuate", //$NON-NLS-1$ //$NON-NLS-2$
                        List.of(ActuatorCoordinationEnumType.None,
                                ActuatorCoordinationEnumType.WaitForUnconditionalCoordination), null)
                .choice("coordinatedBeforeReadEnum", "ActuatorForm.BeforeRead", //$NON-NLS-1$ //$NON-NLS-2$
                        List.of(ActuatorCoordinationEnumType.None, ActuatorCoordinationEnumType.WaitForStillstand), null)
                .hint("ActuatorForm.Coordination.Hint"); //$NON-NLS-1$
        if (actuator.getHead() != null) {
            MountableAxes.section(form, actuator.getMachine());
            form.section("ActuatorForm.Offset", "crosshair") //$NON-NLS-1$ //$NON-NLS-2$
                    .location("headOffsets", "AbstractActuatorConfigurationWizard.CoordinateSystemPanel.OffsetLabel.text", true) //$NON-NLS-1$ //$NON-NLS-2$
                    .length("safeZ", "AbstractActuatorConfigurationWizard.SafeZPanel.SafeZLabel.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                    .toggle("interlockActuator", "ActuatorForm.Interlock", "ActuatorForm.Interlock.Note"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        return form.onApply(f -> {
            if (actuator.getValueType() != type[0] || actuator.isInterlockActuator() != interlock[0]) {
                type[0] = actuator.getValueType();
                interlock[0] = actuator.isInterlockActuator();
                SwingUtilities.invokeLater(() -> MainFrame.get().getMachineSetupTab().selectCurrentTreePath());
            }
        });
    }

    public static FormWizard reference(ReferenceActuator actuator) {
        List<Driver> drivers = new ArrayList<>();
        drivers.add(null);
        drivers.addAll(actuator.getMachine().getDrivers());
        Form.Builder form = Form.of(actuator).named("ActuatorForm.Title") //$NON-NLS-1$
                .section("ActuatorForm.Basics", "zap") //$NON-NLS-1$ //$NON-NLS-2$
                .text("name", "ReferenceActuatorConfigurationWizard.PropertiesPanel.NameLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("driver", "ReferenceActuatorConfigurationWizard.PropertiesPanel.DriverLabel.text", drivers, null); //$NON-NLS-1$ //$NON-NLS-2$
        return common(form, actuator).build();
    }

    public static FormWizard script(ScriptActuator actuator) {
        Form.Builder form = Form.of(actuator).named("ActuatorForm.Title") //$NON-NLS-1$
                .section("ActuatorForm.Basics", "zap") //$NON-NLS-1$ //$NON-NLS-2$
                .text("name", "ScriptActuatorConfigurationWizard.lblName.text") //$NON-NLS-1$ //$NON-NLS-2$
                .text("scriptName", "ScriptActuatorConfigurationWizard.lblScriptName.text") //$NON-NLS-1$ //$NON-NLS-2$
                .hint("ActuatorForm.Script.Hint"); //$NON-NLS-1$
        return common(form, actuator).build();
    }

    public static FormWizard http(HttpActuator actuator) {
        Form.Builder form = Form.of(actuator).named("ActuatorForm.Title") //$NON-NLS-1$
                .section("ActuatorForm.Basics", "zap") //$NON-NLS-1$ //$NON-NLS-2$
                .text("name", "HttpActuatorConfigurationWizard.lblName.text") //$NON-NLS-1$ //$NON-NLS-2$
                .section("ActuatorForm.Http", "globe") //$NON-NLS-1$ //$NON-NLS-2$
                .text("onUrl", "HttpActuatorConfigurationWizard.lblOnUrl.text") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen(VALUE, ActuatorValueType.Boolean::equals)
                .text("offUrl", "HttpActuatorConfigurationWizard.lblOffUrl.text") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen(VALUE, ActuatorValueType.Boolean::equals)
                .text("paramUrl", "HttpActuatorConfigurationWizard.lblParametricUrl.text") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen(VALUE, v -> v != ActuatorValueType.Boolean)
                .hint("ActuatorForm.Http.Param.Hint") //$NON-NLS-1$
                .text("readUrl", "HttpActuatorConfigurationWizard.lblReadUrl.text") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen(VALUE, v -> v != ActuatorValueType.Boolean)
                .text("regex", "HttpActuatorConfigurationWizard.lblRegex.text") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen(VALUE, v -> v != ActuatorValueType.Boolean)
                .hint("ActuatorForm.Http.Regex.Hint"); //$NON-NLS-1$
        return common(form, actuator).build();
    }
}
