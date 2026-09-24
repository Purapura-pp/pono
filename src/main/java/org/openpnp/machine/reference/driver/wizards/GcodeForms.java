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

package org.openpnp.machine.reference.driver.wizards;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.DisplayNames;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.machine.reference.driver.GcodeDriver;
import org.openpnp.machine.reference.driver.GcodeDriver.Command;
import org.openpnp.machine.reference.driver.GcodeDriver.CommandType;
import org.openpnp.model.Configuration;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Head;
import org.openpnp.spi.HeadMountable;
import org.openpnp.spi.Machine;
import org.openpnp.spi.Nozzle;
import org.pmw.tinylog.Logger;
import org.simpleframework.xml.Serializer;

/**
 * A G-code driver's commands, edited one at a time for the machine or one of the things it
 * drives, and its console. The commands' edits are the form's until Apply.
 */
public final class GcodeForms {
    private GcodeForms() {
    }

    // ==== commands ==================================================================================

    /** What a command is for: the machine, or one of the head's things the driver moves. */
    static final class Target {
        final HeadMountable mountable;

        Target(HeadMountable mountable) {
            this.mountable = mountable;
        }

        @Override
        public String toString() {
            if (mountable == null) {
                return Translations.getString("GcodeForms.Default"); //$NON-NLS-1$
            }
            StringBuilder text = new StringBuilder(DisplayNames.typeName(mountable.getClass()));
            if (mountable.getHead() != null) {
                text.append(" \u00b7 ").append(mountable.getHead().getName()); //$NON-NLS-1$
            }
            return text.append(" \u00b7 ").append(mountable.getName()).toString(); //$NON-NLS-1$
        }
    }

    static List<Target> targets(GcodeDriver driver) {
        List<Target> targets = new ArrayList<>();
        targets.add(new Target(null));
        Machine machine = driver.getMachine();
        for (Head head : machine.getHeads()) {
            for (Nozzle nozzle : head.getNozzles()) {
                if (!nozzle.getMappedAxes(machine).drivenBy(driver).isEmpty()) {
                    targets.add(new Target(nozzle));
                }
            }
            for (Camera camera : head.getCameras()) {
                if (!camera.getMappedAxes(machine).drivenBy(driver).isEmpty()) {
                    targets.add(new Target(camera));
                }
            }
            for (Actuator actuator : head.getActuators()) {
                if (actuator.getDriver() == driver || !actuator.getMappedAxes(machine).drivenBy(driver).isEmpty()) {
                    targets.add(new Target(actuator));
                }
            }
        }
        for (Actuator actuator : machine.getActuators()) {
            if (actuator.getDriver() == driver) {
                targets.add(new Target(actuator));
            }
        }
        return targets;
    }

    public static FormWizard gcodes(GcodeDriver driver) {
        Commands commands = new Commands(driver);
        FormWizard[] form = new FormWizard[1];
        form[0] = Form.of(driver).named("GCodeDriver.GCode.title") //$NON-NLS-1$
                .section("GcodeForms.Commands", "file") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", commands) //$NON-NLS-1$
                .hint("GcodeForms.Commands.Hint") //$NON-NLS-1$
                .section("GcodeDriverGcodes.ImportExportPanel.Border.title", "download") //$NON-NLS-1$ //$NON-NLS-2$
                .action("GcodeDriverGcodes.Action.Export", "download", () -> export(driver)) //$NON-NLS-1$ //$NON-NLS-2$
                .action("GcodeDriverGcodes.Action.CopyProfile", "copy", () -> copy(driver)) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("GcodeForms.Export.Hint") //$NON-NLS-1$
                .onReload(f -> commands.load())
                .onApply(f -> commands.store())
                .build();
        commands.form = form[0];
        return form[0];
    }

    /** The driver as a profile file, its commands and settings, for another machine. */
    private static void export(GcodeDriver driver) {
        FileDialog dialog = new FileDialog(MainFrame.get(),
                Translations.getString("GcodeForms.Export.Title"), FileDialog.SAVE); //$NON-NLS-1$
        dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".xml")); //$NON-NLS-1$
        dialog.setVisible(true);
        String name = dialog.getFile();
        if (name == null) {
            return;
        }
        if (!name.toLowerCase().endsWith(".xml")) { //$NON-NLS-1$
            name = name + ".xml"; //$NON-NLS-1$
        }
        File file = new File(new File(dialog.getDirectory()), name);
        if (file.exists() && Dialogs.ask(MainFrame.get(), Dialogs.Tone.Warn, "file", //$NON-NLS-1$
                Translations.getString("DialogMessages.ReplaceFile.Title"), //$NON-NLS-1$
                String.format(Translations.getString("DialogMessages.ReplaceFile.Message"), file.getName()), //$NON-NLS-1$
                null, Dialogs.Choice.danger(Translations.getString("GcodeForms.Replace"))) != 0) { //$NON-NLS-1$
            return;
        }
        try (Writer out = new FileWriter(file)) {
            Serializer serializer = Configuration.createSerializer();
            serializer.write(driver, out);
        }
        catch (Exception e) {
            MessageBoxes.errorBox(MainFrame.get(), Translations.getString("DialogMessages.ExportFailed"), e); //$NON-NLS-1$
        }
    }

    private static void copy(GcodeDriver driver) {
        try {
            StringWriter out = new StringWriter();
            Configuration.createSerializer().write(driver, out);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(out.toString()), null);
            MessageBoxes.infoBox(Translations.getString("CommonPhrases.copiedGcode"), //$NON-NLS-1$
                    Translations.getString("CommonPhrases.copiedGcodeToClipboard")); //$NON-NLS-1$
        }
        catch (Exception e) {
            MessageBoxes.errorBox(MainFrame.get(), Translations.getString("DialogMessages.CopyFailed"), e); //$NON-NLS-1$
        }
    }

    /** One command at a time: what it is for, which command, and its text. */
    public static final class Commands extends JPanel {
        private final GcodeDriver driver;
        private final JComboBox<Target> target;
        private final JComboBox<CommandType> type = new JComboBox<>();
        private final JTextArea text = new JTextArea(6, 16);
        private final Map<List<Object>, String> changes = new HashMap<>();
        private boolean showing;
        FormWizard form;

        Commands(GcodeDriver driver) {
            super(new BorderLayout(0, 6));
            this.driver = driver;
            setOpaque(false);
            target = new JComboBox<>(targets(driver).toArray(new Target[0]));
            DisplayNames.install(type);
            text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            // One above the other: side by side, a head's actuator and a command's name made the
            // row wider than the properties column.
            JPanel choices = new JPanel(new java.awt.GridLayout(2, 1, 0, 6));
            choices.setOpaque(false);
            target.setPrototypeDisplayValue(new Target(null));
            type.setPrototypeDisplayValue(CommandType.CONNECT_COMMAND);
            choices.add(target);
            choices.add(type);
            add(choices, BorderLayout.NORTH);
            JScrollPane scroll = new JScrollPane(text);
            scroll.setPreferredSize(new Dimension(280, 130));
            add(scroll, BorderLayout.CENTER);
            target.addItemListener(e -> types());
            type.addItemListener(e -> display());
            text.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    edited();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    edited();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    edited();
                }
            });
            types();
        }

        private HeadMountable mountable() {
            Target chosen = (Target) target.getSelectedItem();
            return chosen == null ? null : chosen.mountable;
        }

        private String command(HeadMountable mountable, CommandType kind) {
            String changed = changes.get(key(mountable, kind));
            if (changed != null) {
                return changed;
            }
            Command command = driver.getCommand(mountable, kind, false);
            return command == null ? null : command.getCommand();
        }

        private static List<Object> key(HeadMountable mountable, CommandType kind) {
            return java.util.Arrays.asList(mountable, kind);
        }

        /** The commands the target can have: a deprecated one only while it is still set. */
        private void types() {
            HeadMountable mountable = mountable();
            Object chosen = type.getSelectedItem();
            showing = true;
            type.removeAllItems();
            for (CommandType kind : CommandType.values()) {
                if ((mountable == null || kind.isHeadMountable())
                        && (!kind.isDeprecated() || command(mountable, kind) != null)) {
                    type.addItem(kind);
                }
            }
            if (chosen != null) {
                type.setSelectedItem(chosen);
            }
            showing = false;
            display();
        }

        private void display() {
            CommandType kind = (CommandType) type.getSelectedItem();
            showing = true;
            String command = kind == null ? null : command(mountable(), kind);
            text.setText(command == null ? "" : command); //$NON-NLS-1$
            text.setCaretPosition(0);
            showing = false;
        }

        private void edited() {
            CommandType kind = (CommandType) type.getSelectedItem();
            if (showing || kind == null) {
                return;
            }
            changes.put(key(mountable(), kind), text.getText());
            if (form != null) {
                form.edit();
            }
        }

        void load() {
            changes.clear();
            display();
        }

        void store() {
            for (Map.Entry<List<Object>, String> change : changes.entrySet()) {
                driver.setCommand((HeadMountable) change.getKey().get(0), (CommandType) change.getKey().get(1),
                        change.getValue());
            }
            changes.clear();
        }
    }

    // ==== console ===================================================================================

    public static FormWizard console(GcodeDriver driver) {
        return Form.of(driver).named("GCodeDriver.Console.title") //$NON-NLS-1$
                .section("GcodeForms.Console", "keyboard") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", new Console(driver)) //$NON-NLS-1$
                .hint("GcodeForms.Console.Hint") //$NON-NLS-1$
                .build();
    }

    /** Commands typed and sent as they are, with what the controller answers. */
    static final class Console extends JPanel {
        private static final int HISTORY = 100;
        private final GcodeDriver driver;
        private final JTextArea output = new JTextArea(12, 30);
        private final JTextField line = Forms.input(new JTextField(), true);
        private final JCheckBox upperCase = new JCheckBox(
                Translations.getString("GcodeDriverConsole.GCodeConsolePanel.ForceUpperCaseLabel.text"), true); //$NON-NLS-1$
        private final LinkedList<String> history = new LinkedList<>();
        private int cursor = -1;

        Console(GcodeDriver driver) {
            super(new BorderLayout(0, 6));
            this.driver = driver;
            setOpaque(false);
            output.setEditable(false);
            output.setLineWrap(true);
            output.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            JScrollPane scroll = new JScrollPane(output);
            scroll.setPreferredSize(new Dimension(360, 220));
            add(scroll, BorderLayout.CENTER);
            JButton send = Ui.button(Translations.getString("GcodeDriverConsole.GCodeConsolePanel.SendButton.text"), //$NON-NLS-1$
                    null, Ui.Size.Sm, Ui.Variant.Default);
            send.addActionListener(e -> send());
            upperCase.setOpaque(false);
            JPanel foot = new JPanel(new BorderLayout(6, 0));
            foot.setOpaque(false);
            foot.add(line, BorderLayout.CENTER);
            foot.add(send, BorderLayout.EAST);
            foot.add(upperCase, BorderLayout.SOUTH);
            add(foot, BorderLayout.SOUTH);
            line.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_UP && cursor < history.size() - 1) {
                        line.setText(history.get(++cursor));
                    }
                    else if (e.getKeyCode() == KeyEvent.VK_DOWN && cursor > 0) {
                        line.setText(history.get(--cursor));
                    }
                    else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        send();
                    }
                }
            });
        }

        private void send() {
            if (!driver.getMachine().isEnabled()) {
                MessageBoxes.errorBox(MainFrame.get(), Translations.getString("General.Error"), //$NON-NLS-1$
                        Translations.getString("GcodeDriverConsole.MachineNotEnabled")); //$NON-NLS-1$
                return;
            }
            String command = line.getText();
            if (history.isEmpty() || !Objects.equals(history.getFirst(), command)) {
                history.addFirst(command);
                if (history.size() > HISTORY) {
                    history.removeLast();
                }
            }
            cursor = -1;
            if (upperCase.isSelected()) {
                // Most controllers want their commands in capitals.
                command = command.toUpperCase();
            }
            try {
                for (GcodeDriver.Line response : driver.receiveResponses()) {
                    output.append(response.getLine() + "\n"); //$NON-NLS-1$
                }
                driver.sendCommand(command, 5000);
                output.append("> " + command + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
                for (GcodeDriver.Line response : driver.receiveResponses(
                        driver.getCommand(null, CommandType.COMMAND_CONFIRM_REGEX), driver.getTimeoutMilliseconds(),
                        r -> r)) {
                    output.append(response.getLine() + "\n"); //$NON-NLS-1$
                }
            }
            catch (Exception e) {
                Logger.debug(e, "G-code console error"); //$NON-NLS-1$
                output.append("! " + e.getMessage() + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            output.setCaretPosition(output.getDocument().getLength());
        }
    }
}
