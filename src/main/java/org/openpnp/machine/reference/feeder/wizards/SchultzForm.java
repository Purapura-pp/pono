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

package org.openpnp.machine.reference.feeder.wizards;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.openpnp.Translations;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.form.Form;
import org.openpnp.machine.reference.feeder.SchultzFeeder;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Machine;
import org.openpnp.spi.base.AbstractActuator;
import org.openpnp.util.UiUtils;

/**
 * A Schultz feeder: the actuators the feeder is driven and asked through, each with the button
 * that tries it, and what the feeder answered.
 */
public final class SchultzForm {
    private SchultzForm() {
    }

    /** The machine's actuators by name, none first, and a name no actuator has any more kept. */
    static List<String> actuatorNames(Machine machine, String... current) {
        List<String> names = new ArrayList<>();
        names.add(null);
        for (Actuator actuator : machine.getActuators()) {
            names.add(actuator.getName());
        }
        for (String name : current) {
            if (name != null && !name.isEmpty() && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * The named actuator switched to the value, or read with it, after Apply; what it reads goes
     * in the label. Without an actuator named nothing happens, as before.
     */
    static void actuate(FormWizard form, Machine machine, Supplier<String> name, Supplier<Double> value,
            JLabel reading, Runnable then) {
        form.apply();
        UiUtils.submitUiMachineTask(() -> {
            String actuatorName = name.get();
            if (actuatorName == null || actuatorName.isEmpty()) {
                return;
            }
            Actuator actuator = machine.getActuatorByName(actuatorName);
            if (actuator == null) {
                throw new Exception(String.format(Translations.getString("FeederForm.Auto.UnknownActuator"), actuatorName)); //$NON-NLS-1$
            }
            if (reading != null) {
                String answer = actuator.read(value.get());
                SwingUtilities.invokeLater(() -> reading.setText(answer == null ? "\u2014" : answer)); //$NON-NLS-1$
            }
            else {
                AbstractActuator.suggestValueType(actuator, Actuator.ActuatorValueType.Double);
                actuator.actuate(value.get());
            }
            if (then != null) {
                then.run();
            }
        });
    }

    public static FormWizard build(SchultzFeeder feeder) {
        Machine machine = feeder.getMachine();
        List<String> names = actuatorNames(machine, feeder.getActuatorName(), feeder.getPostPickActuatorName(),
                feeder.getFeedCountActuatorName(), feeder.getClearCountActuatorName(), feeder.getPitchActuatorName(),
                feeder.getTogglePitchActuatorName(), feeder.getStatusActuatorName(), feeder.getIdActuatorName());
        JLabel feedCount = Ui.t2("\u2014"); //$NON-NLS-1$
        JLabel pitch = Ui.t2("\u2014"); //$NON-NLS-1$
        JLabel status = Ui.t2("\u2014"); //$NON-NLS-1$
        JLabel id = Ui.t2("\u2014"); //$NON-NLS-1$
        Supplier<Double> value = feeder::getActuatorValue;
        FormWizard[] form = new FormWizard[1];
        Runnable countAgain = () -> actuate(form[0], machine, feeder::getFeedCountActuatorName, value, feedCount, null);
        Runnable pitchAgain = () -> actuate(form[0], machine, feeder::getPitchActuatorName, value, pitch, null);
        form[0] = FeederForm.common(Form.of(feeder).named(feeder.getName()), feeder, true)
                .section("SchultzForm.Feeding", "zap") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("actuatorName", "SchultzForm.PrePick", names, null) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Test", "play", //$NON-NLS-1$ //$NON-NLS-2$
                        f -> actuate(f, machine, feeder::getActuatorName, value, null, null))
                .movesMachine()
                .decimal("actuatorValue", "SchultzForm.Value").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("SchultzForm.Value.Hint") //$NON-NLS-1$
                .choice("postPickActuatorName", "SchultzForm.PostPick", names, null) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Test", "play", //$NON-NLS-1$ //$NON-NLS-2$
                        f -> actuate(f, machine, feeder::getPostPickActuatorName, value, null,
                                () -> SwingUtilities.invokeLater(countAgain)))
                .movesMachine()
                .section("SchultzForm.Count", "list") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("feedCountActuatorName", "SchultzForm.FeedCount", names, null) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Read", "download", f -> countAgain.run()) //$NON-NLS-1$ //$NON-NLS-2$
                .custom("SchultzForm.FeedCount.Value", feedCount) //$NON-NLS-1$
                .choice("clearCountActuatorName", "SchultzForm.ClearCount", names, null) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Clear", "undo", //$NON-NLS-1$ //$NON-NLS-2$
                        f -> actuate(f, machine, feeder::getClearCountActuatorName, value, null,
                                () -> SwingUtilities.invokeLater(() -> feedCount.setText("\u2014")))) //$NON-NLS-1$
                .section("SchultzForm.Pitch", "ruler") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("pitchActuatorName", "SchultzForm.PitchRead", names, null) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Read", "download", f -> pitchAgain.run()) //$NON-NLS-1$ //$NON-NLS-2$
                .custom("SchultzForm.Pitch.Value", pitch) //$NON-NLS-1$
                .choice("togglePitchActuatorName", "SchultzForm.PitchToggle", names, null) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Toggle", "refresh", //$NON-NLS-1$ //$NON-NLS-2$
                        f -> actuate(f, machine, feeder::getTogglePitchActuatorName, value, null,
                                () -> SwingUtilities.invokeLater(pitchAgain)))
                .section("SchultzForm.Identity", "info") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("statusActuatorName", "SchultzForm.Status", names, null) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Read", "download", //$NON-NLS-1$ //$NON-NLS-2$
                        f -> actuate(f, machine, feeder::getStatusActuatorName, value, status, null))
                .custom("SchultzForm.Status.Value", status) //$NON-NLS-1$
                .choice("idActuatorName", "SchultzForm.Id", names, null) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Read", "download", //$NON-NLS-1$ //$NON-NLS-2$
                        f -> actuate(f, machine, feeder::getIdActuatorName, value, id, null))
                .custom("SchultzForm.Id.Value", id) //$NON-NLS-1$
                .build();
        return form[0];
    }
}
