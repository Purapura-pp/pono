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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Ui;
import org.openpnp.machine.reference.ReferenceActuator;
import org.openpnp.machine.reference.ReferenceActuatorProfiles;
import org.openpnp.machine.reference.ReferenceActuatorProfiles.Profile;
import org.openpnp.spi.Actuator;
import org.openpnp.util.UiUtils;

/**
 * A profile actuator's profiles: the up to six actuators it sets, and the profiles, each a value
 * for every one of them. The table is the profiles themselves, edited in place.
 */
public final class ProfilesForm {
    private ProfilesForm() {
    }

    private static final String[] ACTUATORS = {"actuator1", "actuator2", "actuator3", "actuator4", "actuator5", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            "actuator6"}; //$NON-NLS-1$
    private static final String[] LABELS = {"lblActuator_1", "lblActuator_2", "lblActuator_3", "lblActuator_4", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            "lblActuator_5", "lblActuator_6"}; //$NON-NLS-1$ //$NON-NLS-2$

    public static FormWizard build(ReferenceActuator actuator, ReferenceActuatorProfiles profiles) {
        List<Actuator> actuators = new ArrayList<>();
        actuators.add(null);
        for (Actuator other : actuator.getHead() != null ? actuator.getHead().getActuators()
                : actuator.getMachine().getActuators()) {
            if (other != actuator) {
                actuators.add(other);
            }
        }
        List<Actuator> before = new ArrayList<>();
        before.addAll(java.util.Arrays.asList(profiles.getActuator1(), profiles.getActuator2(), profiles.getActuator3(),
                profiles.getActuator4(), profiles.getActuator5(), profiles.getActuator6()));
        Table table = new Table(actuator, profiles);
        FormWizard[] form = new FormWizard[1];
        Form.Builder builder = Form.of(profiles).named("ProfilesForm.Title") //$NON-NLS-1$
                .section("ReferenceActuatorProfilesWizard.panelInterlock.Border.title", "zap"); //$NON-NLS-1$ //$NON-NLS-2$
        for (int i = 0; i < ACTUATORS.length; i++) {
            builder.choice(ACTUATORS[i], "ReferenceActuatorProfilesWizard." + LABELS[i] + ".text", actuators, null); //$NON-NLS-1$ //$NON-NLS-2$
        }
        form[0] = builder.hint("ProfilesForm.Actuators.Hint") //$NON-NLS-1$
                .section("ReferenceActuatorProfilesWizard.panelCondition.Border.title", "list") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", table) //$NON-NLS-1$
                .onApply(f -> {
                    List<Actuator> now = java.util.Arrays.asList(profiles.getActuator1(), profiles.getActuator2(),
                            profiles.getActuator3(), profiles.getActuator4(), profiles.getActuator5(),
                            profiles.getActuator6());
                    if (!Objects.equals(now, before)) {
                        // The table's columns are the actuators chosen.
                        before.clear();
                        before.addAll(now);
                        SwingUtilities.invokeLater(() -> MainFrame.get().getMachineSetupTab().selectCurrentTreePath());
                    }
                })
                .build();
        table.form = form[0];
        return form[0];
    }

    /** The profiles' table with its buttons: add, delete, up and down. */
    static final class Table extends JPanel {
        private final ReferenceActuator actuator;
        private final ReferenceActuatorProfiles profiles;
        private final JTable table;
        private final JButton delete;
        private final JButton up;
        private final JButton down;
        FormWizard form;

        Table(ReferenceActuator actuator, ReferenceActuatorProfiles profiles) {
            super(new BorderLayout(0, 6));
            this.actuator = actuator;
            this.profiles = profiles;
            setOpaque(false);
            table = new JTable(profiles) {
                @Override
                public String getToolTipText(java.awt.event.MouseEvent e) {
                    int row = rowAtPoint(e.getPoint());
                    int column = columnAtPoint(e.getPoint());
                    String tip = row < 0 ? null : profiles.getToolTipAt(row, column);
                    return tip != null ? tip : super.getToolTipText(e);
                }
            };
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.getSelectionModel().addListSelectionListener(e -> updateButtons());
            JButton add = Ui.iconButton(Ui.iconSm("plus"), Ui.Size.Sm, Ui.Variant.Default, Translations.getString("ProfilesForm.Add")); //$NON-NLS-1$ //$NON-NLS-2$
            delete = Ui.iconButton(Ui.iconSm("trash"), Ui.Size.Sm, Ui.Variant.Default, Translations.getString("ProfilesForm.Delete")); //$NON-NLS-1$ //$NON-NLS-2$
            up = Ui.iconButton(Ui.iconSm("up"), Ui.Size.Sm, Ui.Variant.Default, Translations.getString("ProfilesForm.Up")); //$NON-NLS-1$ //$NON-NLS-2$
            down = Ui.iconButton(Ui.iconSm("down"), Ui.Size.Sm, Ui.Variant.Default, Translations.getString("ProfilesForm.Down")); //$NON-NLS-1$ //$NON-NLS-2$
            add.addActionListener(e -> run(profiles::addNew));
            delete.addActionListener(e -> run(() -> {
                Profile selected = selected();
                if (selected != null) {
                    profiles.delete(selected);
                    actuator.fireProfilesChanged();
                }
            }));
            up.addActionListener(e -> move(-1));
            down.addActionListener(e -> move(1));
            add(Forms.row(add, delete, up, down), BorderLayout.NORTH);
            JScrollPane scroll = new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(360, 180));
            add(scroll, BorderLayout.CENTER);
            updateButtons();
        }

        /** The actuators chosen above are written first: the profiles' columns follow them. */
        private void run(UiUtils.Thrunnable change) {
            if (form != null) {
                form.apply();
            }
            UiUtils.messageBoxOnException(change);
            updateButtons();
        }

        private Profile selected() {
            int row = table.getSelectedRow();
            return row < 0 ? null : profiles.get(table.convertRowIndexToModel(row));
        }

        private void move(int by) {
            run(() -> {
                ArrayList<Profile> list = profiles.getProfiles();
                Profile profile = selected();
                int at = profile == null ? -1 : list.indexOf(profile);
                int to = at + by;
                if (at < 0 || to < 0 || to >= list.size()) {
                    return;
                }
                list.set(at, list.get(to));
                list.set(to, profile);
                profiles.fireTableRowsUpdated(Math.min(at, to), Math.max(at, to));
                table.setRowSelectionInterval(to, to);
            });
        }

        private void updateButtons() {
            int at = selected() == null ? -1 : profiles.getProfiles().indexOf(selected());
            delete.setEnabled(at >= 0);
            up.setEnabled(at > 0);
            down.setEnabled(at >= 0 && at < profiles.getProfiles().size() - 1);
        }
    }
}
