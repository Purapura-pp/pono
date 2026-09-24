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

package org.openpnp.gui.calibration;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

import org.openpnp.Translations;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.support.DisplayNames;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Solutions;
import org.pmw.tinylog.Logger;

/**
 * What an issue asks before it is accepted - a choice, a number, a length - for one issue or for
 * the several of a row at once: each edit goes to all of them, which is what "统一改成" means.
 * Actions and text are left to the issue's own panel, which a row of one issue can show instead.
 */
public final class IssueInputs {
    private IssueInputs() {
    }

    /** Whether the issue asks anything these editors can set. */
    public static boolean editable(Solutions.Issue issue) {
        if (hasChoices(issue)) {
            return true;
        }
        for (Solutions.Issue.CustomProperty property : issue.getProperties()) {
            if (supported(property)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasChoices(Solutions.Issue issue) {
        Solutions.Issue.Choice[] choices = issue.getChoices();
        if (choices == null) {
            return false;
        }
        int n = 0;
        for (Solutions.Issue.Choice choice : choices) {
            if (choice != null) {
                n++;
            }
        }
        return n > 1;
    }

    private static boolean supported(Solutions.Issue.CustomProperty property) {
        return property instanceof Solutions.Issue.IntegerProperty
                || property instanceof Solutions.Issue.DoubleProperty
                || property instanceof Solutions.Issue.LengthProperty
                || property instanceof Solutions.Issue.BooleanProperty;
    }

    /**
     * Editors for the first issue's inputs, each change made to every issue that has the same
     * input. A choice not made yet is made here, the first one, as accepting would make it.
     */
    public static JComponent editors(List<Solutions.Issue> issues, LengthUnit units, Runnable changed) {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        if (issues.isEmpty()) {
            return grid;
        }
        Solutions.Issue first = issues.get(0);
        int row = 0;
        if (hasChoices(first)) {
            List<Solutions.Issue.Choice> choices = new ArrayList<>();
            for (Solutions.Issue.Choice choice : first.getChoices()) {
                if (choice != null) {
                    choices.add(choice);
                }
            }
            if (first.getChoice() == null) {
                setChoice(issues, choices.get(0).getValue());
            }
            JComboBox<Solutions.Issue.Choice> combo = new JComboBox<>(
                    choices.toArray(new Solutions.Issue.Choice[0]));
            combo.setRenderer(new DefaultListCellRenderer() {
                @Override
                public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                        boolean isSelected, boolean cellHasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof Solutions.Issue.Choice) {
                        Solutions.Issue.Choice choice = (Solutions.Issue.Choice) value;
                        String heading = Html.heading(choice.getDescription());
                        setText(heading.isEmpty() ? DisplayNames.of(choice.getValue()) : heading);
                    }
                    return this;
                }
            });
            for (Solutions.Issue.Choice choice : choices) {
                if (Objects.equals(choice.getValue(), first.getChoice())) {
                    combo.setSelectedItem(choice);
                }
            }
            combo.addActionListener(e -> {
                Solutions.Issue.Choice choice = (Solutions.Issue.Choice) combo.getSelectedItem();
                if (choice != null) {
                    setChoice(issues, choice.getValue());
                    changed.run();
                }
            });
            add(grid, row++, Translations.getString("CalibrationPanel.Inputs.Choice"), combo); //$NON-NLS-1$
        }
        Solutions.Issue.CustomProperty[] properties = first.getProperties();
        for (int i = 0; i < properties.length; i++) {
            Solutions.Issue.CustomProperty property = properties[i];
            if (!supported(property)) {
                continue;
            }
            int index = i;
            JComponent editor = editor(property, units, value -> {
                for (Solutions.Issue issue : issues) {
                    Solutions.Issue.CustomProperty[] theirs = issue.getProperties();
                    if (theirs.length > index && theirs[index].getClass().getSuperclass()
                            == property.getClass().getSuperclass()) {
                        set(theirs[index], value);
                    }
                }
                changed.run();
            });
            String label = Translations.translateText(property.getLabel());
            add(grid, row++, label, editor);
            if (property.getToolTip() != null) {
                editor.setToolTipText(Translations.translateText(property.getToolTip()));
            }
        }
        return grid;
    }

    /** An editor of one issue's value, and what puts the issue's value back into it. */
    public static final class Editor {
        public final JComponent component;
        public final Runnable reload;

        Editor(JComponent component, Runnable reload) {
            this.component = component;
            this.reload = reload;
        }
    }

    /**
     * The editor of the issue's first input a number or a length, for a row of its own among
     * others: the value only, without a label. Null for an issue that has none.
     */
    public static Editor editor(Solutions.Issue issue, LengthUnit units, Runnable changed) {
        Solutions.Issue.CustomProperty[] properties = issue.getProperties();
        for (int i = 0; i < properties.length; i++) {
            Solutions.Issue.CustomProperty property = properties[i];
            if (!(property instanceof Solutions.Issue.IntegerProperty
                    || property instanceof Solutions.Issue.DoubleProperty
                    || property instanceof Solutions.Issue.LengthProperty)) {
                continue;
            }
            int index = i;
            JComponent editor = editor(property, units, value -> {
                set(issue.getProperties()[index], value);
                changed.run();
            });
            editor.setPreferredSize(new java.awt.Dimension(84, editor.getPreferredSize().height));
            Runnable reload = () -> {
                Solutions.Issue.CustomProperty now = issue.getProperties()[index];
                if (editor instanceof JSpinner && now instanceof Solutions.Issue.IntegerProperty) {
                    ((JSpinner) editor).setValue(((Solutions.Issue.IntegerProperty) now).get());
                }
                else if (editor instanceof JSpinner && now instanceof Solutions.Issue.DoubleProperty) {
                    ((JSpinner) editor).setValue(((Solutions.Issue.DoubleProperty) now).get());
                }
                else if (editor instanceof JTextField && now instanceof Solutions.Issue.LengthProperty) {
                    Length length = ((Solutions.Issue.LengthProperty) now).get();
                    ((JTextField) editor).setText(length == null ? "" : length.toString()); //$NON-NLS-1$
                }
            };
            return new Editor(editor, reload);
        }
        return null;
    }

    private static void setChoice(List<Solutions.Issue> issues, Object value) {
        for (Solutions.Issue issue : issues) {
            Solutions.Issue.Choice[] theirs = issue.getChoices();
            if (theirs == null) {
                continue;
            }
            for (Solutions.Issue.Choice choice : theirs) {
                if (choice != null && Objects.equals(choice.getValue(), value)) {
                    issue.setChoice(value);
                }
            }
        }
    }

    private static void add(JPanel grid, int row, String label, JComponent editor) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(row == 0 ? 0 : 6, 0, 0, 10);
        grid.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(row == 0 ? 0 : 6, 0, 0, 0);
        grid.add(editor, c);
    }

    private static JComponent editor(Solutions.Issue.CustomProperty property, LengthUnit units,
            java.util.function.Consumer<Object> set) {
        if (property instanceof Solutions.Issue.IntegerProperty) {
            Solutions.Issue.IntegerProperty p = (Solutions.Issue.IntegerProperty) property;
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(clamp(p.get(), p.getMin(), p.getMax()),
                    p.getMin(), p.getMax(), 1));
            spinner.addChangeListener(e -> set.accept(spinner.getValue()));
            return spinner;
        }
        if (property instanceof Solutions.Issue.DoubleProperty) {
            Solutions.Issue.DoubleProperty p = (Solutions.Issue.DoubleProperty) property;
            double value = Math.max(p.getMin(), Math.min(p.getMax(), p.get()));
            double step = Math.max((p.getMax() - p.getMin()) / 1000, 1e-6);
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, p.getMin(), p.getMax(), step));
            spinner.addChangeListener(e -> set.accept(spinner.getValue()));
            return spinner;
        }
        if (property instanceof Solutions.Issue.BooleanProperty) {
            Solutions.Issue.BooleanProperty p = (Solutions.Issue.BooleanProperty) property;
            JCheckBox box = Forms.check(""); //$NON-NLS-1$
            box.setSelected(p.get());
            box.addActionListener(e -> set.accept(box.isSelected()));
            return box;
        }
        Solutions.Issue.LengthProperty p = (Solutions.Issue.LengthProperty) property;
        JTextField field = Forms.input(new JTextField(8), true);
        Length length = p.get();
        field.setText(length == null ? "" : length.toString()); //$NON-NLS-1$
        field.addActionListener(e -> commit(field, p, units, set));
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                commit(field, p, units, set);
            }
        });
        return field;
    }

    private static void commit(JTextField field, Solutions.Issue.LengthProperty property, LengthUnit units,
            java.util.function.Consumer<Object> set) {
        Length length = Length.parse(field.getText().trim(), false);
        if (length == null) {
            Length current = property.get();
            field.setText(current == null ? "" : current.toString()); //$NON-NLS-1$
            return;
        }
        if (length.getUnits() == null) {
            length = new Length(length.getValue(), units);
        }
        set.accept(length);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void set(Solutions.Issue.CustomProperty property, Object value) {
        try {
            if (property instanceof Solutions.Issue.IntegerProperty) {
                ((Solutions.Issue.IntegerProperty) property).set(((Number) value).intValue());
            }
            else if (property instanceof Solutions.Issue.DoubleProperty) {
                ((Solutions.Issue.DoubleProperty) property).set(((Number) value).doubleValue());
            }
            else if (property instanceof Solutions.Issue.BooleanProperty) {
                ((Solutions.Issue.BooleanProperty) property).set((Boolean) value);
            }
            else if (property instanceof Solutions.Issue.LengthProperty) {
                ((Solutions.Issue.LengthProperty) property).set((Length) value);
            }
        }
        catch (Exception e) {
            Logger.warn(e, "Setting {} of an issue failed.", property.getLabel()); //$NON-NLS-1$
        }
    }
}
