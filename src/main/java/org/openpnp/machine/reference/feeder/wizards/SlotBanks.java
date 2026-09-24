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
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Part;

/**
 * The banks of a slot feeder and the feeders in them, edited as they are, as they always were:
 * a bank's name, a feeder's name, part and offsets. New and deleted ones show at once in the
 * form's choices of bank and feeder.
 */
final class SlotBanks<B, F> extends JPanel {
    /** How a kind of slot feeder keeps its banks and feeders. */
    interface Model<B, F> {
        List<B> banks();

        B newBank();

        List<F> feeders(B bank);

        F newFeeder(B bank);

        String name(B bank);

        void rename(B bank, String name);

        String feederName(F feeder);

        void renameFeeder(F feeder, String name);

        Part part(F feeder);

        void setPart(F feeder, Part part);

        Location offsets(F feeder);

        void setOffsets(F feeder, Location offsets);
    }

    private final Model<B, F> model;
    private final JTextField bankName = Forms.input(new JTextField(), false);
    private final JComboBox<Object> feeder = new JComboBox<>();
    private final JTextField feederName = Forms.input(new JTextField(), false);
    private final JComboBox<Part> part = new JComboBox<>();
    private final JTextField[] offsets = new JTextField[4];
    private B bank;
    private boolean showing;
    FormWizard form;

    SlotBanks(Model<B, F> model, List<Part> parts, B shown) {
        super(new java.awt.GridLayout(0, 1, 0, 6));
        this.model = model;
        setOpaque(false);
        JButton newBank = button("SlotAutoForm.NewBank", "plus"); //$NON-NLS-1$ //$NON-NLS-2$
        JButton deleteBank = button("SlotAutoForm.DeleteBank", "trash"); //$NON-NLS-1$ //$NON-NLS-2$
        JButton newFeeder = button("SlotAutoForm.NewFeeder", "plus"); //$NON-NLS-1$ //$NON-NLS-2$
        JButton deleteFeeder = button("SlotAutoForm.DeleteFeeder", "trash"); //$NON-NLS-1$ //$NON-NLS-2$
        add(Forms.row(Ui.t2(Translations.getString("SlotAutoForm.BankName")), bankName)); //$NON-NLS-1$
        add(Forms.row(newBank, deleteBank));
        add(Forms.row(Ui.t2(Translations.getString("SlotAutoForm.Feeder")), feeder)); //$NON-NLS-1$
        add(Forms.row(Ui.t2(Translations.getString("SlotAutoForm.FeederName")), feederName)); //$NON-NLS-1$
        part.addItem(null);
        for (Part p : parts) {
            part.addItem(p);
        }
        add(Forms.row(Ui.t2(Translations.getString("AbstractReferenceFeederConfigurationWizard.GeneralPanel.PartLabel.text")), part)); //$NON-NLS-1$
        String[] axes = {"X", "Y", "Z", "C"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        List<JComponent> row = new ArrayList<>();
        row.add(Ui.t2(Translations.getString("SlotAutoForm.Offsets"))); //$NON-NLS-1$
        for (int i = 0; i < 4; i++) {
            offsets[i] = Forms.input(new JTextField(5), true);
            row.add(Ui.t2(axes[i]));
            row.add(offsets[i]);
            offsets[i].addActionListener(e -> writeOffsets());
            onFocusLost(offsets[i], this::writeOffsets);
        }
        add(Forms.row(row.toArray(new JComponent[0])));
        add(Forms.row(newFeeder, deleteFeeder));
        bankName.addActionListener(e -> renameBank());
        onFocusLost(bankName, this::renameBank);
        feederName.addActionListener(e -> renameFeeder());
        onFocusLost(feederName, this::renameFeeder);
        feeder.addActionListener(e -> {
            if (!showing) {
                showFeeder();
            }
        });
        part.addActionListener(e -> {
            F f = selected();
            if (!showing && f != null) {
                model.setPart(f, (Part) part.getSelectedItem());
            }
        });
        newBank.addActionListener(e -> refresh(model.newBank(), null));
        deleteBank.addActionListener(e -> {
            if (model.banks().size() < 2) {
                MessageBoxes.errorBox(MainFrame.get(), Translations.getString("General.Error"), //$NON-NLS-1$
                        Translations.getString("DialogMessages.DeleteBank.LastOne")); //$NON-NLS-1$
                return;
            }
            model.banks().remove(bank);
            refresh(model.banks().get(0), null);
        });
        newFeeder.addActionListener(e -> {
            if (bank != null) {
                refresh(bank, model.newFeeder(bank));
            }
        });
        deleteFeeder.addActionListener(e -> {
            F f = selected();
            if (bank != null && f != null) {
                model.feeders(bank).remove(f);
                refresh(bank, null);
            }
        });
        show(shown);
    }

    private static JButton button(String key, String icon) {
        return Ui.button(Translations.getString(key), Ui.iconSm(icon), Ui.Size.Sm, Ui.Variant.Default);
    }

    private static void onFocusLost(JComponent field, Runnable action) {
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                action.run();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private F selected() {
        return (F) feeder.getSelectedItem();
    }

    /** The feeders the form's choice offers for a bank: none first. */
    List<Object> choices(B shown) {
        List<Object> choices = new ArrayList<>();
        choices.add(null);
        if (shown != null) {
            choices.addAll(model.feeders(shown));
        }
        return choices;
    }

    /** The form's choices follow what was made or deleted here. */
    void refresh(B shown, F chosen) {
        if (form != null) {
            form.setItems("bank", new ArrayList<Object>(model.banks())); //$NON-NLS-1$
            form.set("bank", shown); //$NON-NLS-1$
            form.setItems("feeder", choices(shown)); //$NON-NLS-1$
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

    void show(B shown) {
        showing = true;
        bank = shown;
        bankName.setText(shown == null ? "" : model.name(shown)); //$NON-NLS-1$
        feeder.removeAllItems();
        if (shown != null) {
            for (F f : model.feeders(shown)) {
                feeder.addItem(f);
            }
        }
        showing = false;
        showFeeder();
    }

    void select(F chosen) {
        feeder.setSelectedItem(chosen);
        showFeeder();
    }

    private void showFeeder() {
        showing = true;
        F f = selected();
        feederName.setText(f == null ? "" : model.feederName(f)); //$NON-NLS-1$
        part.setSelectedItem(f == null ? null : model.part(f));
        Location o = f == null || model.offsets(f) == null ? new Location(LengthUnit.Millimeters) : model.offsets(f);
        offsets[0].setText(String.valueOf(o.getX()));
        offsets[1].setText(String.valueOf(o.getY()));
        offsets[2].setText(String.valueOf(o.getZ()));
        offsets[3].setText(String.valueOf(o.getRotation()));
        showing = false;
    }

    private void renameBank() {
        if (bank != null && !bankName.getText().equals(model.name(bank))) {
            model.rename(bank, bankName.getText());
            repaint();
        }
    }

    private void renameFeeder() {
        F f = selected();
        if (f != null && !feederName.getText().equals(model.feederName(f))) {
            model.renameFeeder(f, feederName.getText());
            feeder.repaint();
        }
    }

    private void writeOffsets() {
        F f = selected();
        if (showing || f == null) {
            return;
        }
        try {
            Location held = model.offsets(f) == null ? new Location(LengthUnit.Millimeters) : model.offsets(f);
            model.setOffsets(f, new Location(held.getUnits(), Double.parseDouble(offsets[0].getText().trim()),
                    Double.parseDouble(offsets[1].getText().trim()), Double.parseDouble(offsets[2].getText().trim()),
                    Double.parseDouble(offsets[3].getText().trim())));
        }
        catch (NumberFormatException e) {
            showFeeder();
        }
    }
}
