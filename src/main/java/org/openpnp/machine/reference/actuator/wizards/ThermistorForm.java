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

package org.openpnp.machine.reference.actuator.wizards;

import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.machine.reference.actuator.ThermistorToLinearSensorActuator;

/**
 * How a thermistor actuator turns its reading into a temperature: the Steinhart-Hart
 * coefficients and the divider, the converter's range, then a linear transform.
 */
public final class ThermistorForm {
    private ThermistorForm() {
    }

    public static FormWizard build(ThermistorToLinearSensorActuator actuator) {
        return Form.of(actuator).named("ThermistorForm.Title") //$NON-NLS-1$
                .section("ThermistorToLinearSensorActuatorTransforms.Thermistor.Border.title", "activity") //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("a", "A").format("%g").width(160) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .decimal("b", "B").format("%g").width(160) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .decimal("c", "C").format("%g").width(160) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("ThermistorForm.Coefficients.Hint") //$NON-NLS-1$
                .decimal("r1", "R1").format("%f").unit("\u03a9").width(160) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                .hint("ThermistorForm.R1.Hint") //$NON-NLS-1$
                .decimal("r2", "R2").format("%f").unit("\u03a9").width(160) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                .section("ThermistorToLinearSensorActuatorTransforms.ADC.Border.title", "sliders") //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("adcMax", "ThermistorToLinearSensorActuatorTransforms.MaximumValue.text").format("%f").width(160) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .decimal("vRef", "ThermistorToLinearSensorActuatorTransforms.VoltageReference.text").format("%f").unit("V").width(160) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                .section("ThermistorToLinearSensorActuatorTransforms.LinearTransform.Border.title", "ruler") //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("scale", "ThermistorToLinearSensorActuatorTransforms.Scale.text").format("%g").width(160) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .decimal("offset", "ThermistorToLinearSensorActuatorTransforms.Offset.text").format("%g").width(160) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("ThermistorForm.Linear.Hint") //$NON-NLS-1$
                .build();
    }
}
