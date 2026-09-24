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

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.openpnp.Translations;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.RoundedPanel;
import org.openpnp.gui.shell.Tokens;
import org.openpnp.gui.shell.Ui;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.driver.AbstractReferenceDriver;
import org.openpnp.machine.reference.driver.GcodeDriver;
import org.openpnp.machine.reference.driver.wizards.DriverForms;
import org.openpnp.machine.reference.wizards.MachineForm;
import org.openpnp.spi.Driver;

/**
 * How the machine is reached, the mockups' 28: each driver's port or address, a test that only
 * asks the controller for its firmware, the G-code the firmware gets, and what happens once the
 * machine is on.
 */
final class ConnectionTopic extends Topic {
    private final MachineSettingsPanel page;
    private final ReferenceMachine machine;
    private final List<Driver> drivers = new ArrayList<>();
    private final List<DriverCard> cards = new ArrayList<>();
    private final JPanel driverHolder = new JPanel(new BorderLayout());
    private final List<FormWizard> driverForms = new ArrayList<>();
    private Driver selected;

    ConnectionTopic(MachineSettingsPanel page, ReferenceMachine machine) {
        super(MachineSettingsPanel.CONNECTION, "power"); //$NON-NLS-1$
        this.page = page;
        this.machine = machine;
    }

    @Override
    protected JComponent build() {
        drivers.clear();
        cards.clear();
        drivers.addAll(machine.getDrivers());
        List<JComponent> sections = new ArrayList<>();
        if (drivers.size() > 1) {
            JPanel row = new JPanel(new GridLayout(1, drivers.size(), 10, 0));
            row.setOpaque(false);
            for (Driver driver : drivers) {
                DriverCard card = new DriverCard(driver);
                cards.add(card);
                row.add(card);
            }
            sections.add(new Forms.Section("machine", Translations.getString("MachineSettings.Connection.Drivers")) //$NON-NLS-1$ //$NON-NLS-2$
                    .withRight(String.format(Translations.getString("MachineSettings.Connection.Count"), drivers.size())) //$NON-NLS-1$
                    .content(row));
        }
        driverHolder.setOpaque(false);
        sections.add(driverHolder);
        sections.add(forms.add(Form.of(new MachineForm.Bean(machine)).named("MachineForm.Title") //$NON-NLS-1$
                .section("MachineSettings.Connection.After", "play") //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("homeAfterEnabled", "MachineForm.HomeAfterEnabled", "MachineForm.HomeAfterEnabled.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .build()));
        JComponent view = MachineSettingsPanel.page(
                Guide.of(Translations.getString("MachineSettings.Guide.Connection")), //$NON-NLS-1$
                sections.toArray(new JComponent[0]));
        if (!drivers.isEmpty()) {
            select(firstCommunicating());
        }
        return view;
    }

    /** The driver that talks to a controller, where the other is a simulation. */
    private Driver firstCommunicating() {
        for (Driver driver : drivers) {
            if (driver instanceof AbstractReferenceDriver) {
                return driver;
            }
        }
        return drivers.get(0);
    }

    private void select(Driver driver) {
        if (driver == selected) {
            return;
        }
        for (FormWizard form : driverForms) {
            if (form.hasEdits()) {
                int choice = MachineSettingsPanel.askUnapplied(driverHolder, selected.getName());
                if (choice == 1) {
                    form.apply();
                }
                else if (choice == 0) {
                    form.reset();
                }
                else {
                    return;
                }
            }
        }
        for (FormWizard form : driverForms) {
            forms.remove(form);
            form.dispose();
        }
        driverForms.clear();
        selected = driver;
        for (DriverCard card : cards) {
            card.setOn(card.driver == driver);
        }
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        for (String kind : new String[] { SetupChecks.PORT_MISSING, SetupChecks.NO_PORT }) {
            SetupChecks.Check check = page.getChecks().about(kind, driver);
            if (check != null) {
                column.add(MachineSettingsPanel.capped(hint(check)));
            }
        }
        if (driver instanceof AbstractReferenceDriver) {
            AbstractReferenceDriver reference = (AbstractReferenceDriver) driver;
            FormWizard communications = forms.add(DriverForms.communications(reference));
            driverForms.add(communications);
            column.add(MachineSettingsPanel.capped(communications));
            if (driver instanceof GcodeDriver) {
                column.add(MachineSettingsPanel.capped(test((GcodeDriver) driver, communications)));
                column.add(MachineSettingsPanel.capped(gcode((GcodeDriver) driver)));
                FormWizard timing = forms.add(Form.of(driver).named(driver.getName())
                        .section("DriverForms.Timing", "clock") //$NON-NLS-1$ //$NON-NLS-2$
                        .integer("timeoutMilliseconds", "DriverForms.Timeout").unit("ms").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        .integer("connectWaitTimeMilliseconds", "DriverForms.ConnectWait").unit("ms").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        .build());
                driverForms.add(timing);
                column.add(MachineSettingsPanel.capped(timing));
            }
        }
        else {
            Forms.Section section = new Forms.Section("machine", driver.getName()); //$NON-NLS-1$
            section.content(Forms.paragraph(Translations.getString("MachineSettings.Connection.Simulated"))); //$NON-NLS-1$
            column.add(MachineSettingsPanel.capped(section));
        }
        driverHolder.removeAll();
        driverHolder.add(column, BorderLayout.CENTER);
        driverHolder.revalidate();
        driverHolder.repaint();
    }

    /**
     * The test: the port opened, the firmware asked for with M115 and the port closed again, which
     * moves nothing. What is on screen is applied first, being what the user means to test.
     */
    private JComponent test(GcodeDriver driver, FormWizard communications) {
        Forms.Section section = new Forms.Section("activity", Translations.getString("MachineSettings.Connection.Test")); //$NON-NLS-1$ //$NON-NLS-2$
        JButton test = Ui.button(Translations.getString("MachineSettings.Connection.Test.Do"), Ui.iconSm("activity"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Default);
        Chip result = new Chip("", Chip.Tone.Pending, Chip.Shape.Status); //$NON-NLS-1$
        result.setVisible(false);
        JLabel detail = Ui.t2(""); //$NON-NLS-1$
        detail.setFont(Ui.font(Tokens.FS_SMALL));
        test.addActionListener(e -> {
            if (communications.hasEdits()) {
                communications.apply();
            }
            test.setEnabled(false);
            result.setVisible(true);
            result.setTone(Chip.Tone.Run);
            result.setText(Translations.getString("MachineSettings.Connection.Test.Running")); //$NON-NLS-1$
            detail.setText(""); //$NON-NLS-1$
            Thread thread = new Thread(() -> {
                long start = System.currentTimeMillis();
                String outcome;
                boolean ok;
                try {
                    driver.detectFirmware(false, true);
                    long took = System.currentTimeMillis() - start;
                    String firmware = driver.getFirmwareProperty("FIRMWARE_NAME", null); //$NON-NLS-1$
                    ok = driver.getDetectedFirmware() != null;
                    outcome = ok ? String.format(Translations.getString("MachineSettings.Connection.Test.Ok"), //$NON-NLS-1$
                            firmware == null ? driver.getDetectedFirmware() : firmware, took)
                            : Translations.getString("MachineSettings.Connection.Test.Silent"); //$NON-NLS-1$
                }
                catch (Exception ex) {
                    ok = false;
                    outcome = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                }
                boolean passed = ok;
                String said = outcome;
                SwingUtilities.invokeLater(() -> {
                    test.setEnabled(true);
                    result.setTone(passed ? Chip.Tone.Ok : Chip.Tone.Err);
                    result.setText(Translations.getString(passed ? "MachineSettings.Connection.Test.Passed" //$NON-NLS-1$
                            : "MachineSettings.Connection.Test.Failed")); //$NON-NLS-1$
                    detail.setText(said);
                    detail.setToolTipText(said);
                    page.refreshChecks();
                });
            }, "Pono connection test"); //$NON-NLS-1$
            thread.setDaemon(true);
            thread.start();
        });
        JPanel row = Forms.row(test, result, detail);
        row.add(Box.createHorizontalGlue());
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setOpaque(false);
        content.add(row, BorderLayout.NORTH);
        content.add(Ui.muted(Translations.getString("MachineSettings.Connection.Test.Note")), BorderLayout.CENTER); //$NON-NLS-1$
        return section.content(content);
    }

    /** The firmware as the controller reported it, and the way to the commands it is sent. */
    private JComponent gcode(GcodeDriver driver) {
        Forms.Section section = new Forms.Section("file", Translations.getString("MachineSettings.Connection.Gcode")); //$NON-NLS-1$ //$NON-NLS-2$
        String firmware = driver.getFirmwareProperty("FIRMWARE_NAME", null); //$NON-NLS-1$
        Chip chip = new Chip(firmware == null ? Translations.getString("MachineSettings.Connection.Firmware.Unknown") //$NON-NLS-1$
                : firmware, firmware == null ? Chip.Tone.Pending : Chip.Tone.Ok, Chip.Shape.Status);
        JButton commands = Ui.button(Translations.getString("MachineSettings.Connection.Commands"), Ui.iconSm("tree"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Ghost);
        commands.addActionListener(e -> page.showInTree(driver));
        Forms.Grid grid = new Forms.Grid(Tokens.FORM_LABEL);
        JPanel firmwareRow = Forms.row(chip);
        firmwareRow.add(Box.createHorizontalGlue());
        grid.row(Translations.getString("MachineSettings.Connection.Firmware"), firmwareRow); //$NON-NLS-1$
        JPanel commandsRow = Forms.row(commands, Ui.muted(Translations.getString("MachineSettings.Connection.Commands.Note"))); //$NON-NLS-1$
        commandsRow.add(Box.createHorizontalGlue());
        grid.row(Translations.getString("MachineSettings.Connection.Commands.Label"), commandsRow); //$NON-NLS-1$
        return section.content(grid);
    }

    private static JComponent hint(SetupChecks.Check check) {
        JPanel box = new JPanel(new BorderLayout(8, 0));
        box.setOpaque(false);
        box.setBorder(new EmptyBorder(10, Tokens.PAD_SECTION, 2, Tokens.PAD_SECTION));
        JLabel text = new JLabel(check.text() + "\u3000" + check.fix(), //$NON-NLS-1$
                Ui.icon("alert", 14, Ui.warnText()), JLabel.LEFT); //$NON-NLS-1$
        text.setIconTextGap(6);
        text.setForeground(Ui.warnText());
        text.setFont(Ui.font(Tokens.FS_AUX));
        box.add(text, BorderLayout.CENTER);
        return box;
    }

    /** One driver: its name and kind; the one on show is marked. */
    private final class DriverCard extends RoundedPanel {
        final Driver driver;
        private final boolean[] on;

        DriverCard(Driver driver) {
            this(driver, new boolean[1]);
        }

        private DriverCard(Driver driver, boolean[] on) {
            super(Tokens.R_MD, () -> on[0] ? Ui.accentSoft() : Ui.surface2(), () -> on[0] ? Ui.accent() : Ui.border());
            this.driver = driver;
            this.on = on;
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(10, 12, 10, 12));
            add(new JLabel(Ui.icon("machine", 18, Ui.text2())), BorderLayout.WEST); //$NON-NLS-1$
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            JLabel name = new JLabel(driver.getName());
            name.setFont(Ui.weighted(Tokens.FS_BODY, Tokens.FW_SECTION));
            text.add(name);
            text.add(Box.createVerticalStrut(2));
            JLabel kind = Ui.t2(org.openpnp.gui.shell.InspectorPanel.typeOf(driver));
            kind.setFont(Ui.font(Tokens.FS_AUX));
            text.add(kind);
            add(text, BorderLayout.CENTER);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    select(driver);
                }
            });
        }

        void setOn(boolean chosen) {
            on[0] = chosen;
            repaint();
        }
    }
}
