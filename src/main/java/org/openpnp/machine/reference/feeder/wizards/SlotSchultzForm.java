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

import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Ui;
import org.openpnp.machine.reference.feeder.SlotSchultzFeeder;
import org.openpnp.machine.reference.feeder.SlotSchultzFeeder.Bank;
import org.openpnp.machine.reference.feeder.SlotSchultzFeeder.Feeder;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Part;
import org.openpnp.spi.Machine;
import org.openpnp.util.UiUtils;

/**
 * A slot for Schultz feeders: which bank's feeder is in it, found by the ID the feeder reports,
 * the actuators it is driven and asked through, and the pick location a fiducial corrects.
 */
public final class SlotSchultzForm {
    private SlotSchultzForm() {
    }

    static SlotBanks.Model<Bank, Feeder> model(SlotSchultzFeeder slot) {
        return new SlotBanks.Model<Bank, Feeder>() {
            public List<Bank> banks() { return SlotSchultzFeeder.getBanks(slot.getMachine()); }

            public Bank newBank() {
                Bank bank = new Bank();
                banks().add(bank);
                return bank;
            }

            public List<Feeder> feeders(Bank bank) { return bank.getFeeders(); }

            public Feeder newFeeder(Bank bank) {
                Feeder feeder = new Feeder();
                bank.getFeeders().add(feeder);
                return feeder;
            }

            public String name(Bank bank) { return bank.getName(); }
            public void rename(Bank bank, String name) { bank.setName(name); }
            public String feederName(Feeder feeder) { return feeder.getName(); }
            public void renameFeeder(Feeder feeder, String name) { feeder.setName(name); }
            public Part part(Feeder feeder) { return feeder.getPart(); }
            public void setPart(Feeder feeder, Part part) { feeder.setPart(part); }
            public Location offsets(Feeder feeder) { return feeder.getOffsets(); }
            public void setOffsets(Feeder feeder, Location offsets) { feeder.setOffsets(offsets); }
        };
    }

    public static FormWizard build(SlotSchultzFeeder slot) {
        Machine machine = slot.getMachine();
        List<String> names = SchultzForm.actuatorNames(machine, slot.getActuatorName(), slot.getPostPickActuatorName(),
                slot.getFeedCountActuatorName(), slot.getClearCountActuatorName(), slot.getPitchActuatorName(),
                slot.getTogglePitchActuatorName(), slot.getStatusActuatorName(), slot.getIdActuatorName());
        SlotBanks<Bank, Feeder> banks = new SlotBanks<>(model(slot), FeederForm.parts(slot), slot.getBank());
        JLabel id = Ui.t2("\u2014"); //$NON-NLS-1$
        JLabel feedCount = Ui.t2("\u2014"); //$NON-NLS-1$
        JLabel pitch = Ui.t2("\u2014"); //$NON-NLS-1$
        JLabel status = Ui.t2("\u2014"); //$NON-NLS-1$
        Supplier<Double> value = slot::getActuatorValue;
        Object[] bank = {slot.getBank()};
        FormWizard[] form = new FormWizard[1];
        Runnable countAgain = () -> SchultzForm.actuate(form[0], machine, slot::getFeedCountActuatorName, value, feedCount, null);
        Runnable pitchAgain = () -> SchultzForm.actuate(form[0], machine, slot::getPitchActuatorName, value, pitch, null);
        form[0] = Form.of(slot).named(slot.getName())
                .section("SlotAutoForm.Slot", "feeder") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("bank", "SlotAutoForm.Bank", new ArrayList<Object>(SlotSchultzFeeder.getBanks(machine)), null) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("feeder", "SlotAutoForm.Feeder", banks.choices(slot.getBank()), null) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("idActuatorName", "SchultzForm.Id", names, null) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Read", "download", //$NON-NLS-1$ //$NON-NLS-2$
                        f -> SchultzForm.actuate(f, machine, slot::getIdActuatorName, value, id, null))
                .custom("SchultzForm.Id.Value", id) //$NON-NLS-1$
                .action("SlotSchultzForm.Load", "download", () -> load(form[0], banks, id.getText())) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("SlotSchultzForm.Load.Hint") //$NON-NLS-1$
                .integer("feedRetryCount", "AbstractReferenceFeederConfigurationWizard.GeneralPanel.FeedRetryCountLabel.text").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("pickRetryCount", "AbstractReferenceFeederConfigurationWizard.GeneralPanel.PickRetryCountLabel.text").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .section("AbstractReferenceFeederConfigurationWizard.PickLocationPanel.Border.title", "crosshair") //$NON-NLS-1$ //$NON-NLS-2$
                .location("location", "FeederForm.PickLocation", true).locationButtons() //$NON-NLS-1$ //$NON-NLS-2$
                .hint("SlotAutoForm.Pick.Hint") //$NON-NLS-1$
                .text("fiducialPart", "SlotSchultzForm.Fiducial") //$NON-NLS-1$ //$NON-NLS-2$
                .button("SlotSchultzForm.Fiducial.Update", "target", f -> updateLocation(f, slot)) //$NON-NLS-1$ //$NON-NLS-2$
                .movesMachine()
                .hint("SlotSchultzForm.Fiducial.Hint") //$NON-NLS-1$
                .section("SchultzForm.Feeding", "zap") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("actuatorName", "SchultzForm.PrePick", names, null) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Test", "play", f -> SchultzForm.actuate(f, machine, slot::getActuatorName, value, null, null)) //$NON-NLS-1$ //$NON-NLS-2$
                .movesMachine()
                .decimal("actuatorValue", "SchultzForm.Value").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("SchultzForm.Value.Hint") //$NON-NLS-1$
                .choice("postPickActuatorName", "SchultzForm.PostPick", names, null) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Test", "play", f -> SchultzForm.actuate(f, machine, slot::getPostPickActuatorName, //$NON-NLS-1$ //$NON-NLS-2$
                        value, null, () -> SwingUtilities.invokeLater(countAgain)))
                .movesMachine()
                .section("SchultzForm.Count", "list") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("feedCountActuatorName", "SchultzForm.FeedCount", names, null) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Read", "download", f -> countAgain.run()) //$NON-NLS-1$ //$NON-NLS-2$
                .custom("SchultzForm.FeedCount.Value", feedCount) //$NON-NLS-1$
                .choice("clearCountActuatorName", "SchultzForm.ClearCount", names, null) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Clear", "undo", f -> SchultzForm.actuate(f, machine, slot::getClearCountActuatorName, //$NON-NLS-1$ //$NON-NLS-2$
                        value, null, () -> SwingUtilities.invokeLater(() -> feedCount.setText("\u2014")))) //$NON-NLS-1$
                .section("SchultzForm.Pitch", "ruler") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("pitchActuatorName", "SchultzForm.PitchRead", names, null) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Read", "download", f -> pitchAgain.run()) //$NON-NLS-1$ //$NON-NLS-2$
                .custom("SchultzForm.Pitch.Value", pitch) //$NON-NLS-1$
                .choice("togglePitchActuatorName", "SchultzForm.PitchToggle", names, null) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Toggle", "refresh", f -> SchultzForm.actuate(f, machine, slot::getTogglePitchActuatorName, //$NON-NLS-1$ //$NON-NLS-2$
                        value, null, () -> SwingUtilities.invokeLater(pitchAgain)))
                .choice("statusActuatorName", "SchultzForm.Status", names, null) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Read", "download", f -> SchultzForm.actuate(f, machine, slot::getStatusActuatorName, //$NON-NLS-1$ //$NON-NLS-2$
                        value, status, null))
                .custom("SchultzForm.Status.Value", status) //$NON-NLS-1$
                .section("SlotAutoForm.Banks", "layers").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", banks) //$NON-NLS-1$
                .onChange(f -> {
                    Object chosen = f.value("bank"); //$NON-NLS-1$
                    if (chosen != bank[0]) {
                        bank[0] = chosen;
                        f.setItems("feeder", banks.choices((Bank) chosen)); //$NON-NLS-1$
                        banks.show((Bank) chosen);
                    }
                })
                .build();
        banks.form = form[0];
        return form[0];
    }

    /**
     * The feeder the ID names, in the bank on screen, chosen for the slot; made with the usual
     * offsets when the bank does not have it yet.
     */
    private static void load(FormWizard form, SlotBanks<Bank, Feeder> banks, String name) {
        Bank bank = (Bank) form.value("bank"); //$NON-NLS-1$
        if (bank == null || name == null || name.isEmpty() || "\u2014".equals(name)) { //$NON-NLS-1$
            return;
        }
        for (Feeder feeder : bank.getFeeders()) {
            if (name.equals(feeder.getName())) {
                form.set("feeder", feeder); //$NON-NLS-1$
                banks.select(feeder);
                return;
            }
        }
        Feeder created = new Feeder(name);
        created.setOffsets(new Location(LengthUnit.Millimeters, -5, -30, 0, 0));
        bank.getFeeders().add(created);
        banks.refresh(bank, created);
    }

    /** The pick location's X and Y from the fiducial, for Apply to write. */
    private static void updateLocation(FormWizard form, SlotSchultzFeeder slot) {
        form.apply();
        UiUtils.submitUiMachineTask(() -> {
            if (slot.getFiducialPart() == null) {
                return;
            }
            Location found = slot.getFiducialLocation(slot.getLocation(), slot.getFiducialPart());
            if (found == null) {
                throw new Exception(org.openpnp.Translations.getString("SlotSchultzForm.Fiducial.NotFound")); //$NON-NLS-1$
            }
            SwingUtilities.invokeLater(() -> {
                Location held = form.location("location"); //$NON-NLS-1$
                Location xy = found.convertToUnits(held.getUnits());
                form.setLocation("location", held.derive(xy.getX(), xy.getY(), null, null)); //$NON-NLS-1$
            });
        });
    }
}
