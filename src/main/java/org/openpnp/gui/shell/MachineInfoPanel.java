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

package org.openpnp.gui.shell;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import org.openpnp.ConfigurationListener;
import org.openpnp.Translations;
import org.openpnp.gui.MachineControlsPanel;
import org.openpnp.model.Configuration;
import org.openpnp.model.Part;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Head;
import org.openpnp.spi.HeadMountable;
import org.openpnp.spi.Machine;
import org.openpnp.spi.Nozzle;
import org.openpnp.util.UiUtils;

/**
 * The machine at a glance, beside a picture that the camera card's height limits, where the card
 * has width to spare: the tool, its tip and the part it holds, and the switches that are otherwise
 * in the manual controls' "..." menu - the machine's switched actuators and board protection.
 * <p>
 * What it shows is what the program already knows; nothing is asked of the machine to fill it.
 */
@SuppressWarnings("serial")
public class MachineInfoPanel extends JPanel {
    /** Less room than this beside the picture, and the panel is left out. */
    public static final int MIN_ROOM = 250;
    public static final int MAX_WIDTH = 360;

    private static final int ROW = 26;

    private final Configuration configuration;
    private final MachineControlsPanel controls;
    private final JLabel tool = value();
    private final JLabel tip = value();
    private final JLabel part = value();
    private final JPanel actuators = column();
    private final JLabel actuatorsTitle = title(Translations.getString("JogControlsPanel.Tab.Actuators")); //$NON-NLS-1$
    private final Map<Actuator, Forms.Toggle> switches = new LinkedHashMap<>();
    private final Forms.Toggle protection = new Forms.Toggle();
    private final Timer refresh = new Timer(1000, e -> refresh());

    public MachineInfoPanel(Configuration configuration, MachineControlsPanel controls) {
        this.configuration = configuration;
        this.controls = controls;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(10, 14, 10, 14));
        add(title(Translations.getString("MachineInfo.Title"))); //$NON-NLS-1$
        add(row(Translations.getString("MachineInfo.Tool"), tool)); //$NON-NLS-1$
        add(row(Translations.getString("MachineInfo.Tip"), tip)); //$NON-NLS-1$
        add(row(Translations.getString("MachineInfo.Part"), part)); //$NON-NLS-1$
        add(rule());
        add(actuatorsTitle);
        add(actuators);
        add(rule());
        protection.onChange(() -> controls.getJogControlsPanel().setBoardProtectionEnabled(protection.isSelected()));
        protection.setToolTipText(Translations.getString("JogControlsPanel.Label.BoardProtection.Description")); //$NON-NLS-1$
        add(switchRow(Translations.getString("JogControlsPanel.Label.BoardProtection"), protection)); //$NON-NLS-1$
        controls.addPropertyChangeListener("selectedTool", e -> refresh()); //$NON-NLS-1$
        configuration.addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationComplete(Configuration configuration) throws Exception {
                SwingUtilities.invokeLater(() -> buildSwitches(configuration.getMachine()));
            }
        });
        refresh();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        refresh.start();
        refresh();
    }

    @Override
    public void removeNotify() {
        refresh.stop();
        super.removeNotify();
    }

    /** The switched actuators: those whose value is on or off. */
    private void buildSwitches(Machine machine) {
        actuators.removeAll();
        switches.clear();
        java.util.List<Actuator> all = new java.util.ArrayList<>(machine.getActuators());
        for (Head head : machine.getHeads()) {
            all.addAll(head.getActuators());
        }
        for (Actuator actuator : all) {
            if (actuator.getValueType() != Actuator.ActuatorValueType.Boolean) {
                continue;
            }
            Forms.Toggle toggle = new Forms.Toggle();
            toggle.onChange(() -> {
                boolean on = toggle.isSelected();
                UiUtils.submitUiMachineTask(() -> {
                    actuator.actuate(on);
                    return null;
                }, result -> refresh(), error -> {
                    refresh();
                    UiUtils.showError(error);
                });
            });
            switches.put(actuator, toggle);
            actuators.add(switchRow(actuator.getName(), toggle));
        }
        actuatorsTitle.setVisible(!switches.isEmpty());
        actuators.setVisible(!switches.isEmpty());
        revalidate();
        refresh();
    }

    private void refresh() {
        HeadMountable selected = controls.getSelectedTool();
        tool.setText(selected == null ? "\u2014" : selected.getName()); //$NON-NLS-1$
        Nozzle nozzle = selected instanceof Nozzle ? (Nozzle) selected : null;
        tip.setText(nozzle == null || nozzle.getNozzleTip() == null ? "\u2014" : nozzle.getNozzleTip().getName()); //$NON-NLS-1$
        Part held = nozzle == null ? null : nozzle.getPart();
        part.setText(held == null ? Translations.getString("MachineInfo.Part.None") : held.getId()); //$NON-NLS-1$
        Machine machine = configuration.getMachine();
        boolean enabled = machine != null && machine.isEnabled();
        for (Map.Entry<Actuator, Forms.Toggle> entry : switches.entrySet()) {
            Boolean on = entry.getKey().isActuated();
            entry.getValue().setSelected(on != null && on);
            entry.getValue().setEnabled(enabled);
            entry.getValue().setToolTipText(enabled ? null : Ui.machineReason(machine));
        }
        protection.setSelected(controls.getJogControlsPanel().isBoardProtectionEnabled());
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = 2 * Tokens.R_MD;
            g2.setColor(Ui.surface2());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.setColor(Ui.border());
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        }
        finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }

    // ---- pieces -------------------------------------------------------------------------------

    private static JLabel value() {
        JLabel label = new JLabel("\u2014"); //$NON-NLS-1$
        label.setFont(Ui.font(12.5f, Font.BOLD));
        return label;
    }

    private static JLabel title(String text) {
        JLabel label = Ui.muted(text);
        label.setFont(Ui.font(11f, Font.BOLD));
        label.setBorder(new EmptyBorder(4, 0, 2, 0));
        left(label);
        return label;
    }

    private static JPanel column() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        left(panel);
        return panel;
    }

    private static JPanel row(String label, JComponent value) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        JLabel name = Ui.t2(label);
        name.setFont(Ui.font(12.5f));
        name.setPreferredSize(new Dimension(56, ROW));
        row.add(name, BorderLayout.WEST);
        row.add(value, BorderLayout.CENTER);
        held(row, ROW);
        return row;
    }

    private static JPanel switchRow(String label, Forms.Toggle toggle) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        JLabel name = new JLabel(label);
        name.setFont(Ui.font(12.5f));
        row.add(name, BorderLayout.CENTER);
        JPanel holder = new JPanel(new java.awt.GridBagLayout());
        holder.setOpaque(false);
        holder.add(toggle);
        row.add(holder, BorderLayout.EAST);
        held(row, ROW + 2);
        return row;
    }

    private static JComponent rule() {
        JComponent rule = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Ui.border());
                g.fillRect(0, getHeight() / 2, getWidth(), 1);
            }
        };
        held(rule, 17);
        return rule;
    }

    private static void held(JComponent c, int height) {
        c.setPreferredSize(new Dimension(10, height));
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        left(c);
    }

    private static void left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
    }
}
