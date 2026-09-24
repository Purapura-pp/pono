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

import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.machine.reference.feeder.ReferenceSlotAutoFeeder;
import org.openpnp.machine.reference.feeder.ReferenceSlotAutoFeeder.Bank;
import org.openpnp.machine.reference.feeder.ReferenceSlotAutoFeeder.Feeder;
import org.openpnp.model.Location;
import org.openpnp.model.Part;

/**
 * A slot for auto feeders: the actuators, the pick location, and which bank's feeder sits in the
 * slot now. The banks and their feeders are edited as they are, below.
 */
public final class SlotAutoForm {
    private SlotAutoForm() {
    }

    static SlotBanks.Model<Bank, Feeder> model(ReferenceSlotAutoFeeder slot) {
        return new SlotBanks.Model<Bank, Feeder>() {
            public List<Bank> banks() { return ReferenceSlotAutoFeeder.getBanks(slot.getMachine()); }

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

    public static FormWizard build(ReferenceSlotAutoFeeder slot) {
        List<String> actuators = SchultzForm.actuatorNames(slot.getMachine(), slot.getActuatorName(),
                slot.getPostPickActuatorName());
        SlotBanks<Bank, Feeder> banks = new SlotBanks<>(model(slot), FeederForm.parts(slot), slot.getBank());
        Object[] bank = {slot.getBank()};
        FormWizard[] form = new FormWizard[1];
        form[0] = Form.of(slot).named(slot.getName())
                .section("SlotAutoForm.Slot", "feeder") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("bank", "SlotAutoForm.Bank", new ArrayList<Object>(ReferenceSlotAutoFeeder.getBanks(slot.getMachine())), null) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("feeder", "SlotAutoForm.Feeder", banks.choices(slot.getBank()), null) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("SlotAutoForm.Slot.Hint") //$NON-NLS-1$
                .integer("feedRetryCount", "AbstractReferenceFeederConfigurationWizard.GeneralPanel.FeedRetryCountLabel.text").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("pickRetryCount", "AbstractReferenceFeederConfigurationWizard.GeneralPanel.PickRetryCountLabel.text").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .section("AbstractReferenceFeederConfigurationWizard.PickLocationPanel.Border.title", "crosshair") //$NON-NLS-1$ //$NON-NLS-2$
                .location("location", "FeederForm.PickLocation", true).locationButtons() //$NON-NLS-1$ //$NON-NLS-2$
                .hint("SlotAutoForm.Pick.Hint") //$NON-NLS-1$
                .section("ReferenceAutoFeederConfigurationWizard.ActuatorsPanel.Border.title", "zap") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("actuatorName", "FeederForm.Auto.Feed", actuators, null) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("actuatorValue", "FeederForm.Auto.FeedValue").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Test", "play", //$NON-NLS-1$ //$NON-NLS-2$
                        f -> SchultzForm.actuate(f, slot.getMachine(), slot::getActuatorName, slot::getActuatorValue, null, null))
                .choice("postPickActuatorName", "FeederForm.Auto.PostPick", actuators, null) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("postPickActuatorValue", "FeederForm.Auto.PostPickValue").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Test", "play", //$NON-NLS-1$ //$NON-NLS-2$
                        f -> SchultzForm.actuate(f, slot.getMachine(), slot::getPostPickActuatorName,
                                slot::getPostPickActuatorValue, null, null))
                .hint("FeederForm.Auto.Value.Hint") //$NON-NLS-1$
                .toggle("moveBeforeFeed", "FeederForm.Auto.MoveBeforeFeed", "FeederForm.Auto.MoveBeforeFeed.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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
}
