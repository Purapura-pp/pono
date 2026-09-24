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

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.machine.reference.feeder.ReferenceSlotAutoFeeder;
import org.openpnp.machine.reference.feeder.ReferenceSlotAutoFeeder.Bank;
import org.openpnp.machine.reference.feeder.ReferenceSlotAutoFeeder.Feeder;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.Location;
import org.openpnp.model.Part;

/**
 * A slot for auto feeders: the actuators, the pick location, and which bank's feeder sits in the
 * slot now. The banks and their feeders are edited as they are, below.
 */
public final class SlotAutoForm {
    private SlotAutoForm() {
    }

    /** The slot's settings, the bank and the feeder chosen for it written on Apply. */
    public static class Bean extends AbstractModelObject {
        private final ReferenceSlotAutoFeeder slot;

        Bean(ReferenceSlotAutoFeeder slot) {
            this.slot = slot;
        }

        public String getActuatorName() { return slot.getActuatorName(); }
        public void setActuatorName(String name) { slot.setActuatorName(name); }
        public double getActuatorValue() { return slot.getActuatorValue(); }
        public void setActuatorValue(double value) { slot.setActuatorValue(value); }
        public String getPostPickActuatorName() { return slot.getPostPickActuatorName(); }
        public void setPostPickActuatorName(String name) { slot.setPostPickActuatorName(name); }
        public double getPostPickActuatorValue() { return slot.getPostPickActuatorValue(); }
        public void setPostPickActuatorValue(double value) { slot.setPostPickActuatorValue(value); }
        public boolean isMoveBeforeFeed() { return slot.isMoveBeforeFeed(); }
        public void setMoveBeforeFeed(boolean move) { slot.setMoveBeforeFeed(move); }
        public int getFeedRetryCount() { return slot.getFeedRetryCount(); }
        public void setFeedRetryCount(int count) { slot.setFeedRetryCount(count); }
        public int getPickRetryCount() { return slot.getPickRetryCount(); }
        public void setPickRetryCount(int count) { slot.setPickRetryCount(count); }
        public Location getLocation() { return slot.getLocation(); }
        public void setLocation(Location location) { slot.setLocation(location); }

        public Bank getBank() {
            return slot.getBank();
        }

        public void setBank(Bank bank) throws Exception {
            slot.setBank(bank);
        }

        public Feeder getFeeder() {
            return slot.getFeeder();
        }

        public void setFeeder(Feeder feeder) throws Exception {
            slot.setFeeder(feeder);
        }
    }

    static List<Feeder> feeders(Bank bank) {
        List<Feeder> feeders = new ArrayList<>();
        feeders.add(null);
        if (bank != null) {
            feeders.addAll(bank.getFeeders());
        }
        return feeders;
    }

    public static FormWizard build(ReferenceSlotAutoFeeder slot) {
        List<String> actuators = SchultzForm.actuatorNames(slot.getMachine(), slot.getActuatorName(),
                slot.getPostPickActuatorName());
        Banks banks = new Banks(slot);
        Object[] bank = {slot.getBank()};
        FormWizard[] form = new FormWizard[1];
        form[0] = Form.of(new Bean(slot)).named(slot.getName())
                .section("SlotAutoForm.Slot", "feeder") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("bank", "SlotAutoForm.Bank", new ArrayList<Object>(ReferenceSlotAutoFeeder.getBanks(slot.getMachine())), null) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("feeder", "SlotAutoForm.Feeder", feeders(slot.getBank()), null) //$NON-NLS-1$ //$NON-NLS-2$
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
                        f.setItems("feeder", feeders((Bank) chosen)); //$NON-NLS-1$
                        banks.show((Bank) chosen);
                    }
                })
                .build();
        banks.form = form[0];
        return form[0];
    }

    /**
     * The banks and their feeders, edited as they are, as they always were: a bank's name, a
     * feeder's name, part and offsets. New and deleted ones show in the choices above at once.
     */
    static final class Banks extends JPanel {
        private final ReferenceSlotAutoFeeder slot;
        private final JTextField bankName = Forms.input(new JTextField(), false);
        private final JComboBox<Feeder> feeder = new JComboBox<>();
        private final JTextField feederName = Forms.input(new JTextField(), false);
        private final JComboBox<Part> part = new JComboBox<>();
        private final JTextField[] offsets = new JTextField[4];
        private Bank bank;
        private boolean showing;
        FormWizard form;

        Banks(ReferenceSlotAutoFeeder slot) {
            super(new java.awt.GridLayout(0, 1, 0, 6));
            this.slot = slot;
            setOpaque(false);
            JButton newBank = Ui.button(Translations.getString("SlotAutoForm.NewBank"), Ui.iconSm("plus"), Ui.Size.Sm, Ui.Variant.Default); //$NON-NLS-1$ //$NON-NLS-2$
            JButton deleteBank = Ui.button(Translations.getString("SlotAutoForm.DeleteBank"), Ui.iconSm("trash"), Ui.Size.Sm, Ui.Variant.Default); //$NON-NLS-1$ //$NON-NLS-2$
            JButton newFeeder = Ui.button(Translations.getString("SlotAutoForm.NewFeeder"), Ui.iconSm("plus"), Ui.Size.Sm, Ui.Variant.Default); //$NON-NLS-1$ //$NON-NLS-2$
            JButton deleteFeeder = Ui.button(Translations.getString("SlotAutoForm.DeleteFeeder"), Ui.iconSm("trash"), Ui.Size.Sm, Ui.Variant.Default); //$NON-NLS-1$ //$NON-NLS-2$
            add(Forms.row(Ui.t2(Translations.getString("SlotAutoForm.BankName")), bankName)); //$NON-NLS-1$
            add(Forms.row(newBank, deleteBank));
            add(Forms.row(Ui.t2(Translations.getString("SlotAutoForm.Feeder")), feeder)); //$NON-NLS-1$
            add(Forms.row(Ui.t2(Translations.getString("SlotAutoForm.FeederName")), feederName)); //$NON-NLS-1$
            for (Part p : FeederForm.parts(slot)) {
                part.addItem(p);
            }
            add(Forms.row(Ui.t2(Translations.getString("AbstractReferenceFeederConfigurationWizard.GeneralPanel.PartLabel.text")), part)); //$NON-NLS-1$
            String[] axes = {"X", "Y", "Z", "C"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            List<javax.swing.JComponent> row = new ArrayList<>();
            row.add(Ui.t2(Translations.getString("SlotAutoForm.Offsets"))); //$NON-NLS-1$
            for (int i = 0; i < 4; i++) {
                offsets[i] = Forms.input(new JTextField(5), true);
                row.add(Ui.t2(axes[i]));
                row.add(offsets[i]);
                offsets[i].addActionListener(e -> writeOffsets());
                offsets[i].addFocusListener(new java.awt.event.FocusAdapter() {
                    @Override
                    public void focusLost(java.awt.event.FocusEvent e) {
                        writeOffsets();
                    }
                });
            }
            add(Forms.row(row.toArray(new javax.swing.JComponent[0])));
            add(Forms.row(newFeeder, deleteFeeder));
            bankName.addActionListener(e -> renameBank());
            bankName.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    renameBank();
                }
            });
            feederName.addActionListener(e -> renameFeeder());
            feederName.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    renameFeeder();
                }
            });
            feeder.addActionListener(e -> {
                if (!showing) {
                    showFeeder();
                }
            });
            part.addActionListener(e -> {
                Feeder f = (Feeder) feeder.getSelectedItem();
                if (!showing && f != null) {
                    f.setPart((Part) part.getSelectedItem());
                }
            });
            newBank.addActionListener(e -> {
                Bank created = new Bank();
                ReferenceSlotAutoFeeder.getBanks(slot.getMachine()).add(created);
                refreshChoices(created, null);
            });
            deleteBank.addActionListener(e -> {
                if (ReferenceSlotAutoFeeder.getBanks(slot.getMachine()).size() < 2) {
                    MessageBoxes.errorBox(MainFrame.get(), Translations.getString("General.Error"), //$NON-NLS-1$
                            Translations.getString("DialogMessages.DeleteBank.LastOne")); //$NON-NLS-1$
                    return;
                }
                ReferenceSlotAutoFeeder.getBanks(slot.getMachine()).remove(bank);
                refreshChoices(ReferenceSlotAutoFeeder.getBanks(slot.getMachine()).get(0), null);
            });
            newFeeder.addActionListener(e -> {
                if (bank == null) {
                    return;
                }
                Feeder created = new Feeder();
                bank.getFeeders().add(created);
                refreshChoices(bank, created);
            });
            deleteFeeder.addActionListener(e -> {
                Feeder f = (Feeder) feeder.getSelectedItem();
                if (bank != null && f != null) {
                    bank.getFeeders().remove(f);
                    refreshChoices(bank, null);
                }
            });
            show(slot.getBank());
        }

        /** The choices above follow what was made or deleted here. */
        private void refreshChoices(Bank shown, Feeder chosen) {
            if (form != null) {
                form.setItems("bank", new ArrayList<Object>(ReferenceSlotAutoFeeder.getBanks(slot.getMachine()))); //$NON-NLS-1$
                form.set("bank", shown); //$NON-NLS-1$
                form.setItems("feeder", feeders(shown)); //$NON-NLS-1$
                if (chosen != null) {
                    form.set("feeder", chosen); //$NON-NLS-1$
                }
            }
            show(shown);
            if (chosen != null) {
                feeder.setSelectedItem(chosen);
                showFeeder();
            }
        }

        void show(Bank shown) {
            showing = true;
            bank = shown;
            bankName.setText(shown == null ? "" : shown.getName()); //$NON-NLS-1$
            feeder.removeAllItems();
            if (shown != null) {
                for (Feeder f : shown.getFeeders()) {
                    feeder.addItem(f);
                }
            }
            showing = false;
            showFeeder();
        }

        private void showFeeder() {
            showing = true;
            Feeder f = (Feeder) feeder.getSelectedItem();
            feederName.setText(f == null ? "" : f.getName()); //$NON-NLS-1$
            part.setSelectedItem(f == null ? null : f.getPart());
            Location o = f == null || f.getOffsets() == null ? new Location(org.openpnp.model.LengthUnit.Millimeters)
                    : f.getOffsets();
            offsets[0].setText(String.valueOf(o.getX()));
            offsets[1].setText(String.valueOf(o.getY()));
            offsets[2].setText(String.valueOf(o.getZ()));
            offsets[3].setText(String.valueOf(o.getRotation()));
            showing = false;
        }

        private void renameBank() {
            if (bank != null && !bankName.getText().equals(bank.getName())) {
                bank.setName(bankName.getText());
                repaint();
            }
        }

        private void renameFeeder() {
            Feeder f = (Feeder) feeder.getSelectedItem();
            if (f != null && !feederName.getText().equals(f.getName())) {
                f.setName(feederName.getText());
                feeder.repaint();
            }
        }

        private void writeOffsets() {
            Feeder f = (Feeder) feeder.getSelectedItem();
            if (showing || f == null) {
                return;
            }
            try {
                Location held = f.getOffsets() == null ? new Location(org.openpnp.model.LengthUnit.Millimeters) : f.getOffsets();
                f.setOffsets(new Location(held.getUnits(), Double.parseDouble(offsets[0].getText().trim()),
                        Double.parseDouble(offsets[1].getText().trim()), Double.parseDouble(offsets[2].getText().trim()),
                        Double.parseDouble(offsets[3].getText().trim())));
            }
            catch (NumberFormatException e) {
                showFeeder();
            }
        }
    }
}
