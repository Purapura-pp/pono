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

package org.openpnp.gui.form;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.jdesktop.beansbinding.Converter;
import org.openpnp.gui.form.Form.Field;
import org.openpnp.gui.form.Form.Kind;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Tokens;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.gui.support.DisplayNames;
import org.openpnp.gui.support.DoubleConverter;
import org.openpnp.gui.support.IntegerConverter;
import org.openpnp.gui.support.LengthConverter;
import org.openpnp.gui.support.MutableLocationProxy;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;

import com.formdev.flatlaf.FlatClientProperties;

/**
 * The form a {@link Form} describes: its sections one under another, a row per field, the
 * bindings of the old wizards underneath - edits are buffered and written by Apply, taken back by
 * Reset - and the checks and conditions re-evaluated as the user types.
 */
@SuppressWarnings({ "serial", "rawtypes", "unchecked" })
public class FormWizard extends AbstractConfigurationWizard {
    final Form.Builder spec;
    /** Each field's editing control, to read its value on screen and to bind it. */
    private final Map<Field, JComponent> controls = new LinkedHashMap<>();
    private final Map<String, Field> byProperty = new LinkedHashMap<>();
    /** The label and the row of each field, shown and hidden together. */
    private final Map<Field, JComponent[]> rows = new LinkedHashMap<>();
    private final Map<Field, JLabel> errors = new LinkedHashMap<>();
    /** Beyond this many characters the words beside a switch wrap. */
    private static final int LONG_TOGGLE_WORDS = 18;

    private final Map<Field, MutableLocationProxy> locations = new LinkedHashMap<>();
    private final Map<Field, JPanel> pipelines = new LinkedHashMap<>();

    FormWizard(Form.Builder spec) {
        this.spec = spec;
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        for (Form.Section s : spec.sections) {
            Forms.Section section = new Forms.Section(s.icon, s.title);
            if (s.measuredBy != null) {
                section.withRight(calibrationLink(s.measuredBy, s.measured));
            }
            else if (s.note != null) {
                section.withRight(s.note);
            }
            JPanel grid = new JPanel(new GridBagLayout());
            grid.setOpaque(false);
            int row = 0;
            for (Field field : s.fields) {
                row = addRow(grid, row, field);
            }
            section.content(grid);
            section.setCollapsed(s.collapsed);
            section.setAlignmentX(Component.LEFT_ALIGNMENT);
            contentPanel.add(section);
        }
        contentPanel.add(Box.createVerticalGlue());
        // Held at the column's width, where the fields give way down to their minimum and a
        // pipeline's stages wrap, rather than scrolling sideways in a narrow column.
        getScrollPane().setViewportView(new LegacyWizardAdapter.WidthTracking(contentPanel));
        // Opened at its top: a tab shown later had scrolled down to its first field, hiding the
        // heading and the words above it.
        contentPanel.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                    && contentPanel.isShowing() && !shownOnce) {
                shownOnce = true;
                SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(
                        () -> getScrollPane().getViewport().setViewPosition(new java.awt.Point(0, 0))));
            }
        });
        refresh();
    }

    private boolean shownOnce;

    @Override
    public String getWizardName() {
        return spec.name;
    }

    /** "Calibration · the step", at the right of a heading, opening the step on the calibration page. */
    private static JComponent calibrationLink(org.openpnp.model.CalibrationStep step, Object subject) {
        JButton link = Ui.button(String.format(org.openpnp.Translations.getString("Form.MeasuredBy"), //$NON-NLS-1$
                step.getName()), Ui.iconSm("target"), Ui.Size.Xs, Ui.Variant.Ghost); //$NON-NLS-1$
        link.setToolTipText(org.openpnp.Translations.getString("Form.MeasuredBy.ToolTip")); //$NON-NLS-1$
        link.addActionListener(e -> {
            org.openpnp.gui.MainFrame frame = org.openpnp.gui.MainFrame.get();
            if (frame != null) {
                frame.showCalibrationStep(step, subject);
            }
        });
        return link;
    }

    @Override
    protected org.openpnp.model.DisplayPreferences getDisplayPreferences() {
        return spec.preferences != null ? spec.preferences : super.getDisplayPreferences();
    }

    /** The value a field shows now, before it is applied: what conditions and checks look at. */
    public Object value(String property) {
        Field field = byProperty.get(property);
        JComponent control = field == null ? null : controls.get(field);
        if (control instanceof JTextField) {
            return ((JTextField) control).getText();
        }
        if (control instanceof JComboBox) {
            return ((JComboBox) control).getSelectedItem();
        }
        if (control instanceof Forms.Toggle) {
            return ((Forms.Toggle) control).isSelected();
        }
        if (control instanceof Forms.Segmented) {
            return ((Forms.Segmented) control).getSelectedItem();
        }
        if (control instanceof Checklist) {
            return ((Checklist) control).getSelection();
        }
        return null;
    }

    /**
     * A checklist field's switches, one to an item. Its selection, the items switched on, is a
     * bound property as a combo box's selected item is, so Apply writes it and Reset reads it.
     */
    public static final class Checklist extends JPanel {
        private final List<?> items;
        private final List<Forms.Toggle> toggles = new ArrayList<>();

        Checklist(List<?> items, java.util.function.Function<Object, String> note, Runnable edited) {
            this.items = items;
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            for (Object item : items) {
                Forms.Toggle toggle = new Forms.Toggle();
                toggle.onChange(() -> {
                    firePropertyChange("selection", null, getSelection()); //$NON-NLS-1$
                    edited.run();
                });
                toggles.add(toggle);
                JPanel row = new JPanel();
                row.setOpaque(false);
                row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                toggle.setAlignmentY(Component.CENTER_ALIGNMENT);
                row.add(toggle);
                row.add(Box.createHorizontalStrut(8));
                JLabel name = new JLabel(DisplayNames.of(item));
                name.setFont(Ui.font(Tokens.FS_BODY));
                row.add(name);
                String text = note == null ? null : note.apply(item);
                if (text != null && !text.isEmpty()) {
                    row.add(Box.createHorizontalStrut(8));
                    row.add(Ui.muted(text));
                }
                row.add(Box.createHorizontalGlue());
                if (getComponentCount() > 0) {
                    add(Box.createVerticalStrut(6));
                }
                add(row);
            }
        }

        public java.util.Set<Object> getSelection() {
            java.util.Set<Object> selection = new java.util.LinkedHashSet<>();
            for (int i = 0; i < items.size(); i++) {
                if (toggles.get(i).isSelected()) {
                    selection.add(items.get(i));
                }
            }
            return selection;
        }

        public void setSelection(java.util.Set<?> selection) {
            java.util.Set<Object> was = getSelection();
            for (int i = 0; i < items.size(); i++) {
                toggles.get(i).setSelected(selection != null && selection.contains(items.get(i)));
            }
            firePropertyChange("selection", was, getSelection()); //$NON-NLS-1$
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            for (Forms.Toggle toggle : toggles) {
                toggle.setEnabled(enabled);
            }
        }
    }

    // ---- building -----------------------------------------------------------------------------

    private int addRow(JPanel grid, int row, Field field) {
        if (field.property != null) {
            byProperty.put(field.property, field);
        }
        JComponent content = control(field);
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = row;
        gc.gridx = 0;
        // Beside the first line of what is taller than a field - a location's rows, a list of
        // switches, a chart - rather than halfway down it.
        boolean tall = content.getPreferredSize().height > 44;
        int firstLine = field.kind == Kind.Checklist ? 0 : 7;
        gc.anchor = tall ? GridBagConstraints.NORTHWEST : GridBagConstraints.WEST;
        gc.insets = new Insets((row == 0 ? 0 : 8) + (tall ? firstLine : 0), 0, 0, 10);
        JLabel label = Forms.Grid.label(field.label, Tokens.FORM_LABEL);
        boolean fullWidth = field.kind == Kind.Custom && (field.label == null || field.label.isEmpty());
        if (!fullWidth) {
            grid.add(label, gc);
        }
        gc.gridx = fullWidth ? 0 : 1;
        gc.gridwidth = fullWidth ? 2 : 1;
        gc.weightx = 1;
        gc.fill = field.width > 0 ? GridBagConstraints.NONE : GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(row == 0 ? 0 : 8, 0, 0, 0);
        grid.add(content, gc);
        JComponent hint = null;
        if (field.hint != null && !field.hint.isEmpty()) {
            hint = hint(field.hint);
            GridBagConstraints gh = new GridBagConstraints();
            gh.gridy = row + 1;
            gh.gridx = fullWidth ? 0 : 1;
            gh.gridwidth = fullWidth ? 2 : 1;
            gh.weightx = 1;
            gh.fill = GridBagConstraints.HORIZONTAL;
            gh.anchor = GridBagConstraints.WEST;
            gh.insets = new Insets(4, 0, 0, 0);
            grid.add(hint, gh);
        }
        JLabel error = new JLabel();
        error.setForeground(Ui.err());
        error.setFont(Ui.font(Tokens.FS_AUX));
        error.setVisible(false);
        GridBagConstraints ge = new GridBagConstraints();
        ge.gridy = row + 2;
        ge.gridx = 1;
        ge.anchor = GridBagConstraints.WEST;
        ge.insets = new Insets(2, 0, 0, 0);
        grid.add(error, ge);
        errors.put(field, error);
        rows.put(field, new JComponent[] { label, content, error, hint });
        return row + 3;
    }

    /**
     * The grey words under a field, wrapped at the column's width. Asking for little width, so
     * that a long one does not make the grid wider than the column and squeeze every field.
     */
    @SuppressWarnings("serial")
    static javax.swing.JTextArea hint(String text) {
        javax.swing.JTextArea area = new javax.swing.JTextArea(text) {
            private int laidOutWidth = -1;

            @Override
            public void setBounds(int x, int y, int width, int height) {
                boolean widthChanged = width != laidOutWidth;
                laidOutWidth = width;
                super.setBounds(x, y, width, height);
                if (widthChanged && width > 0 && getPreferredSize().height != height) {
                    SwingUtilities.invokeLater(this::revalidate);
                }
            }

            @Override
            public java.awt.Dimension getPreferredSize() {
                java.awt.Dimension size = super.getPreferredSize();
                return new java.awt.Dimension(Math.min(size.width, 120), size.height);
            }

            @Override
            public java.awt.Dimension getMinimumSize() {
                return new java.awt.Dimension(40, super.getMinimumSize().height);
            }
        };
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setBorder(null);
        area.setFont(Ui.font(Tokens.FS_AUX));
        area.setForeground(Ui.text2());
        return area;
    }

    private JComponent control(Field field) {
        switch (field.kind) {
            case Text:
                return text(field, false);
            case TextArea: {
                javax.swing.JTextArea area = new javax.swing.JTextArea(Math.max(2, field.width), 20);
                area.setFont(Ui.mono(Ui.BASE, java.awt.Font.PLAIN));
                area.setLineWrap(false);
                area.getDocument().addDocumentListener(new DocumentListener() {
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
                controls.put(field, area);
                javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(area);
                scroll.setPreferredSize(new java.awt.Dimension(160, area.getPreferredSize().height + 6));
                scroll.setMinimumSize(new java.awt.Dimension(60, area.getPreferredSize().height + 6));
                return scroll;
            }
            case Integer:
            case Decimal:
            case Angle:
            case Length:
                return text(field, true);
            case Location:
                return location(field);
            case Choice:
                return choice(field);
            case Segmented: {
                Forms.Segmented segmented = new Forms.Segmented(field.items, DisplayNames::of);
                segmented.onChange(this::edited);
                controls.put(field, segmented);
                JPanel row = Forms.row(segmented);
                row.add(Box.createHorizontalGlue());
                return row;
            }
            case Pipeline: {
                JPanel holder = new JPanel(new java.awt.BorderLayout());
                holder.setOpaque(false);
                pipelines.put(field, holder);
                showPipeline(field);
                return holder;
            }
            case Toggle: {
                Forms.Toggle toggle = new Forms.Toggle();
                toggle.onChange(this::edited);
                controls.put(field, toggle);
                String words = field.note == null ? "" : field.note; //$NON-NLS-1$
                if (words.length() <= LONG_TOGGLE_WORDS) {
                    return Forms.toggleRow(toggle, words);
                }
                // Longer words wrap beside the switch rather than being cut at the column's edge.
                JPanel row = new JPanel(new java.awt.BorderLayout(8, 0));
                row.setOpaque(false);
                JPanel top = new JPanel();
                top.setOpaque(false);
                top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
                top.add(toggle);
                row.add(top, java.awt.BorderLayout.WEST);
                javax.swing.JTextArea text = hint(words);
                text.setFont(Ui.t2("").getFont()); //$NON-NLS-1$
                row.add(text, java.awt.BorderLayout.CENTER);
                return row;
            }
            case ReadOnly: {
                // Nothing to show is a dash: a binding sets a null value's text to null, past
                // the converter that would have said so.
                @SuppressWarnings("serial")
                JLabel value = new JLabel() {
                    @Override
                    public void setText(String text) {
                        super.setText(text == null || text.isEmpty() ? "\u2014" : text); //$NON-NLS-1$
                    }
                };
                value.setFont(Ui.font(Tokens.FS_BODY));
                controls.put(field, value);
                return Forms.readOnly(value);
            }
            case Checklist: {
                Checklist list = new Checklist(field.items, field.itemNote, this::edited);
                controls.put(field, list);
                return list;
            }
            case Action: {
                JButton button = Ui.button(field.label, field.icon == null ? null : Ui.iconSm(field.icon),
                        Ui.Size.Sm, Ui.Variant.Default);
                if (field.movesMachine) {
                    Ui.movesMachine(button);
                }
                button.addActionListener(e -> field.action.run());
                JPanel row = Forms.row(button);
                row.add(Box.createHorizontalGlue());
                return row;
            }
            case Custom:
            default:
                return field.custom;
        }
    }

    private JComponent text(Field field, boolean mono) {
        JTextField input = Forms.input(new JTextField(), mono);
        String unit = field.unit;
        if (field.kind == Kind.Length && unit == null) {
            unit = getDisplayPreferences().getSystemUnits().getShortName();
        }
        if (unit != null) {
            input.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, Forms.unit(unit));
        }
        if (field.width > 0) {
            input.setPreferredSize(new java.awt.Dimension(field.width, Tokens.H_INPUT));
            input.setMinimumSize(input.getPreferredSize());
        }
        input.getDocument().addDocumentListener(new DocumentListener() {
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
        controls.put(field, input);
        List<JComponent> parts = new ArrayList<>();
        parts.add(input);
        if (field.presets != null) {
            parts.add(presets(field, input));
        }
        if (field.probeLocation != null) {
            parts.add(probeButton(field, input));
        }
        if (field.button != null) {
            JButton button = Ui.button(field.buttonLabel,
                    field.buttonIcon == null ? null : Ui.iconSm(field.buttonIcon), Ui.Size.Sm, Ui.Variant.Default);
            button.addActionListener(e -> field.button.accept(this));
            parts.add(button);
        }
        if (field.note != null) {
            parts.add(Ui.t2(field.note));
        }
        if (parts.size() == 1) {
            return input;
        }
        JPanel row = Forms.row(parts.toArray(new JComponent[0]));
        row.add(Box.createHorizontalGlue());
        return row;
    }

    /** The segments beside a number: a click puts the value in, and the one it holds is marked. */
    private JComponent presets(Field field, JTextField input) {
        Forms.Segmented segments = new Forms.Segmented(field.presets, v -> presetText((Number) v)).tight();
        segments.onChange(() -> input.setText(presetValue(field, (Number) segments.getSelectedItem())));
        Runnable follow = () -> segments.setSelectedItem(matchingPreset(field, input.getText()));
        input.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                follow.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                follow.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                follow.run();
            }
        });
        return segments;
    }

    private static String presetText(Number value) {
        double v = value.doubleValue();
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private String presetValue(Field field, Number value) {
        if (field.kind == Kind.Length) {
            return new LengthConverter(getDisplayPreferences())
                    .convertForward(new Length(value.doubleValue(), LengthUnit.Millimeters));
        }
        return presetText(value);
    }

    private Number matchingPreset(Field field, String text) {
        try {
            double v = field.kind == Kind.Length
                    ? new LengthConverter(getDisplayPreferences()).convertReverse(text)
                            .convertToUnits(LengthUnit.Millimeters).getValue()
                    : Double.parseDouble(text.trim());
            for (Number preset : field.presets) {
                if (Math.abs(preset.doubleValue() - v) < 1e-4) {
                    return preset;
                }
            }
        }
        catch (Exception e) {
            // Not a number yet: nothing is marked.
        }
        return null;
    }

    /**
     * Probes the height at the named location's X and Y with a contact probe nozzle, or takes the
     * selected nozzle's height: into the field, for Apply to write.
     */
    private JComponent probeButton(Field field, JTextField z) {
        JButton button = Ui.button(org.openpnp.Translations.getString("Form.Probe"), //$NON-NLS-1$
                Ui.iconSm("nozzle"), Ui.Size.Sm, Ui.Variant.Default); //$NON-NLS-1$
        button.setToolTipText(org.openpnp.Translations.getString("Form.Probe.ToolTip")); //$NON-NLS-1$
        button.addActionListener(e -> {
            Field location = byProperty.get(field.probeLocation);
            List<JTextField> xy = location == null ? null
                    : (List<JTextField>) controls.get(location).getClientProperty("Pono.form.fields"); //$NON-NLS-1$
            org.openpnp.gui.MainFrame frame = org.openpnp.gui.MainFrame.get();
            Object tool = frame == null || frame.getMachineControls() == null ? null
                    : frame.getMachineControls().getSelectedTool();
            javax.swing.Action action;
            if (tool instanceof org.openpnp.machine.reference.ContactProbeNozzle && xy != null) {
                action = new org.openpnp.gui.components.LocationButtonsPanel(xy.get(0), xy.get(1), z, null)
                        .getContactProbeAction();
            }
            else {
                action = new org.openpnp.gui.components.LocationButtonsPanel(null, null, z, null)
                        .getCaptureToolAction();
            }
            action.actionPerformed(e);
        });
        return Ui.movesMachine(button);
    }

    /**
     * Puts a value on screen as if typed, for Apply to write: what a button beside a field
     * worked out.
     */
    public void setValue(String property, String text) {
        Field field = byProperty.get(property);
        JComponent control = field == null ? null : controls.get(field);
        if (control instanceof JTextField) {
            ((JTextField) control).setText(text);
        }
    }

    /**
     * Puts a value on screen as if chosen, for Apply to write: the text of a field, the item of
     * a choice or of segments, the state of a switch, the set of a checklist.
     */
    public void set(String property, Object value) {
        Field field = byProperty.get(property);
        JComponent control = field == null ? null : controls.get(field);
        if (control instanceof JTextField) {
            ((JTextField) control).setText(value == null ? "" : String.valueOf(value)); //$NON-NLS-1$
        }
        else if (control instanceof JComboBox) {
            ((JComboBox<?>) control).setSelectedItem(value);
        }
        else if (control instanceof Forms.Toggle) {
            ((Forms.Toggle) control).setSelected(Boolean.TRUE.equals(value));
        }
        else if (control instanceof Forms.Segmented) {
            ((Forms.Segmented) control).setSelectedItem(value);
        }
        else if (control instanceof Checklist) {
            ((Checklist) control).setSelection((java.util.Set<?>) value);
        }
        else {
            throw new IllegalArgumentException("no field for " + property); //$NON-NLS-1$
        }
        edited();
    }

    /** Puts a location on screen as if typed into its axes, for Apply to write. */
    @SuppressWarnings("unchecked")
    public void setLocation(String property, org.openpnp.model.Location location) {
        Field field = byProperty.get(property);
        JComponent control = field == null ? null : controls.get(field);
        if (control == null || location == null) {
            return;
        }
        LengthConverter length = new LengthConverter(getDisplayPreferences());
        DoubleConverter decimal = new DoubleConverter(getDisplayPreferences().getLengthDisplayFormat());
        for (JTextField input : (List<JTextField>) control.getClientProperty("Pono.form.fields")) { //$NON-NLS-1$
            switch ((String) input.getClientProperty("Pono.form.axis")) { //$NON-NLS-1$
                case "X": //$NON-NLS-1$
                    input.setText(length.convertForward(location.getLengthX()));
                    break;
                case "Y": //$NON-NLS-1$
                    input.setText(length.convertForward(location.getLengthY()));
                    break;
                case "Z": //$NON-NLS-1$
                    input.setText(length.convertForward(location.getLengthZ()));
                    break;
                default:
                    input.setText(decimal.convertForward(location.getRotation()));
                    break;
            }
        }
    }

    /**
     * The location a location field shows now, before it is applied: its axes as typed, the
     * ones it does not show as the object holds them.
     */
    @SuppressWarnings("unchecked")
    public org.openpnp.model.Location location(String property) {
        Field field = byProperty.get(property);
        JComponent control = field == null ? null : controls.get(field);
        if (control == null) {
            return null;
        }
        Object held = org.jdesktop.beansbinding.BeanProperty.create(property).getValue(spec.bean);
        org.openpnp.model.Location location = held instanceof org.openpnp.model.Location
                ? (org.openpnp.model.Location) held : new org.openpnp.model.Location(LengthUnit.Millimeters);
        LengthConverter length = new LengthConverter(getDisplayPreferences());
        for (JTextField input : (List<JTextField>) control.getClientProperty("Pono.form.fields")) { //$NON-NLS-1$
            String axis = (String) input.getClientProperty("Pono.form.axis"); //$NON-NLS-1$
            if (axis.equals("C")) { //$NON-NLS-1$
                location = location.derive(null, null, null, Double.parseDouble(input.getText().trim()));
            }
            else {
                Length value = length.convertReverse(input.getText()).convertToUnits(location.getUnits());
                location = location.derive(axis.equals("X") ? value.getValue() : null, //$NON-NLS-1$
                        axis.equals("Y") ? value.getValue() : null, //$NON-NLS-1$
                        axis.equals("Z") ? value.getValue() : null, null); //$NON-NLS-1$
            }
        }
        return location;
    }

    /** Shows what the object holds now, after something other than the form changed it. */
    public void reload() {
        loadFromModel();
    }

    private JComponent location(Field field) {
        List<JComponent> fields = new ArrayList<>();
        String[] axes = field.planar ? new String[] { "X", "Y", "C" } //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                : field.withRotation ? new String[] { "X", "Y", "Z", "C" } //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                : field.withZ ? new String[] { "X", "Y", "Z" } : new String[] { "X", "Y" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        for (String axis : axes) {
            JTextField input = Forms.input(new JTextField(), true, axis);
            input.getDocument().addDocumentListener(new DocumentListener() {
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
            input.putClientProperty("Pono.form.axis", axis); //$NON-NLS-1$
            fields.add(input);
        }
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        if (field.capture) {
            org.openpnp.gui.components.LocationButtonsPanel buttons =
                    new org.openpnp.gui.components.LocationButtonsPanel((JTextField) fields.get(0),
                            (JTextField) fields.get(1), null, null);
            panel.add(Forms.row(fields.get(0), fields.get(1),
                    captureButton(buttons.getCaptureCameraAction(), "camera", "Form.Capture.Camera"))); //$NON-NLS-1$ //$NON-NLS-2$
        }
        else {
            panel.add(Forms.row(fields.get(0), fields.get(1)));
        }
        if (field.planar) {
            // The mockups' placement: X and Y, then the angle, 120 wide, with the camera's and
            // the nozzle's capture and the move of the camera to it as three icon buttons.
            JTextField angle = (JTextField) fields.get(2);
            JComponent angleField = Forms.inputWithUnit(angle, "\u00b0"); //$NON-NLS-1$
            java.awt.Dimension width = new java.awt.Dimension(120, angleField.getPreferredSize().height);
            angleField.setPreferredSize(width);
            angleField.setMaximumSize(width);
            org.openpnp.gui.components.LocationButtonsPanel buttons =
                    new org.openpnp.gui.components.LocationButtonsPanel((JTextField) fields.get(0),
                            (JTextField) fields.get(1), null, angle);
            JPanel row = Forms.row(angleField,
                    captureButton(buttons.getCaptureCameraAction(), "camera", "Form.Capture.Camera"), //$NON-NLS-1$ //$NON-NLS-2$
                    captureButton(buttons.getCaptureToolAction(), "nozzle", "Form.Capture.Tool"), //$NON-NLS-1$ //$NON-NLS-2$
                    captureButton(buttons.getPositionCameraAction(), "target", "Form.Capture.MoveCamera")); //$NON-NLS-1$ //$NON-NLS-2$
            row.add(Box.createHorizontalGlue());
            panel.add(Box.createVerticalStrut(6));
            panel.add(row);
        }
        else if (field.withRotation) {
            panel.add(Box.createVerticalStrut(6));
            panel.add(Forms.row(fields.get(2), fields.get(3)));
        }
        else if (field.withZ) {
            // Z alone under X, as wide as it: two equal columns with the second one empty.
            JPanel row = new JPanel(new java.awt.GridLayout(1, 2, 6, 0));
            row.setOpaque(false);
            row.add(fields.get(2));
            row.add(Box.createGlue());
            row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, fields.get(2).getPreferredSize().height));
            panel.add(Box.createVerticalStrut(6));
            panel.add(row);
        }
        if (field.button != null) {
            JButton button = Ui.button(field.buttonLabel,
                    field.buttonIcon == null ? null : Ui.iconSm(field.buttonIcon), Ui.Size.Sm, Ui.Variant.Default);
            button.addActionListener(e -> field.button.accept(this));
            JPanel row = Forms.row(button);
            row.add(Box.createHorizontalGlue());
            panel.add(Box.createVerticalStrut(6));
            panel.add(row);
        }
        if (field.locationButtons) {
            org.openpnp.gui.components.LocationButtonsPanel buttons =
                    new org.openpnp.gui.components.LocationButtonsPanel((JTextField) fields.get(0),
                            (JTextField) fields.get(1),
                            field.withRotation || field.withZ ? (JTextField) fields.get(2) : null,
                            field.withRotation ? (JTextField) fields.get(3) : null);
            if (field.locationTool != null) {
                buttons.setTool(field.locationTool);
            }
            buttons.setOpaque(false);
            buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
            ((java.awt.FlowLayout) buttons.getLayout()).setAlignment(java.awt.FlowLayout.LEFT);
            panel.add(Box.createVerticalStrut(6));
            panel.add(buttons);
        }
        // One edge for all rows: the buttons, aligned left, sat on the centre line of the fields.
        for (Component child : panel.getComponents()) {
            if (child instanceof JComponent) {
                ((JComponent) child).setAlignmentX(Component.LEFT_ALIGNMENT);
            }
        }
        panel.putClientProperty("Pono.form.fields", fields); //$NON-NLS-1$
        controls.put(field, panel);
        return panel;
    }

    /** One of the three icon buttons of a planar location: the action's own, with the mockups' icon. */
    private static JComponent captureButton(javax.swing.Action action, String icon, String toolTip) {
        javax.swing.JButton button = Ui.iconButton(Ui.iconSm(icon), Ui.Size.Sm, Ui.Variant.Default,
                Form.resolve(toolTip));
        button.addActionListener(action);
        action.addPropertyChangeListener(e -> button.setEnabled(action.isEnabled()));
        button.setEnabled(action.isEnabled());
        if (!"Form.Capture.MoveCamera".equals(toolTip)) { //$NON-NLS-1$
            return button;
        }
        return Ui.movesMachine(button);
    }

    /** The pipeline's stages as they are now, with its Edit and Reset buttons. */
    private void showPipeline(Field field) {
        JPanel holder = pipelines.get(field);
        List<String> stages = field.stages == null ? null : field.stages.get();
        JButton edit = Ui.button(org.openpnp.Translations.getString("Form.Pipeline.Edit"), //$NON-NLS-1$
                Ui.iconSm("edit"), Ui.Size.Sm, Ui.Variant.Default); //$NON-NLS-1$
        edit.addActionListener(e -> {
            field.action.run();
            showPipeline(field);
        });
        // Only the icon, its words in the tooltip: the mockup's row has Edit alone, and the row
        // with both named was wider than the properties column.
        JButton reset = Ui.iconButton(Ui.iconSm("undo"), Ui.Size.Sm, Ui.Variant.Ghost, //$NON-NLS-1$
                org.openpnp.Translations.getString("Form.Pipeline.Reset")); //$NON-NLS-1$
        reset.setEnabled(field.reset != null);
        reset.addActionListener(e -> {
            field.reset.run();
            showPipeline(field);
        });
        holder.removeAll();
        holder.add(Forms.pipeline(stages == null ? java.util.Collections.emptyList() : stages, edit, reset));
        holder.revalidate();
        holder.repaint();
    }

    private JComponent choice(Field field) {
        JComboBox combo = Forms.dropdown(new JComboBox(field.items.toArray()));
        // A null among the items is the choice of none, an actuator not used say.
        java.util.function.Function<Object, String> name = field.items.contains(null)
                ? v -> v == null ? org.openpnp.Translations.getString("Form.None") : DisplayNames.of(v) //$NON-NLS-1$
                : DisplayNames::of;
        if (field.itemNote != null) {
            combo.setRenderer(Forms.described(name, v -> v == null ? null : field.itemNote.apply(v)));
        }
        else {
            combo.setRenderer(Forms.described(name, v -> null));
        }
        combo.addActionListener(e -> edited());
        // As wide as the row, not as the longest item: a part list's longest name and note made
        // the properties column scroll sideways.
        java.awt.Dimension size = combo.getPreferredSize();
        combo.setPreferredSize(new java.awt.Dimension(Math.min(size.width, 160), size.height));
        combo.setMinimumSize(new java.awt.Dimension(60, size.height));
        controls.put(field, combo);
        return combo;
    }

    // ---- binding ------------------------------------------------------------------------------

    @Override
    public void createBindings() {
        LengthConverter length = new LengthConverter(getDisplayPreferences());
        IntegerConverter integer = new IntegerConverter();
        DoubleConverter decimal = new DoubleConverter(getDisplayPreferences().getLengthDisplayFormat());
        for (Map.Entry<Field, JComponent> entry : controls.entrySet()) {
            Field field = entry.getKey();
            JComponent control = entry.getValue();
            switch (field.kind) {
                case Text:
                case TextArea:
                    addWrappedBinding(spec.bean, field.property, control, "text"); //$NON-NLS-1$
                    break;
                case Integer: {
                    // A long property, a time in milliseconds say, takes the long converter.
                    Class<?> type = Form.requireProperty(spec.bean.getClass(), field.property, true)
                            .getPropertyType();
                    addWrappedBinding(spec.bean, field.property, control, "text", //$NON-NLS-1$
                            type == long.class || type == Long.class
                                    ? new org.openpnp.gui.support.LongConverter() : integer);
                    break;
                }
                case Decimal:
                case Angle:
                    addWrappedBinding(spec.bean, field.property, control, "text", decimal); //$NON-NLS-1$
                    break;
                case Length:
                    addWrappedBinding(spec.bean, field.property, control, "text", length); //$NON-NLS-1$
                    break;
                case Location: {
                    MutableLocationProxy proxy = new MutableLocationProxy();
                    bind(UpdateStrategy.READ_WRITE, spec.bean, field.property, proxy, "location"); //$NON-NLS-1$
                    locations.put(field, proxy);
                    for (JTextField input : (List<JTextField>) control.getClientProperty("Pono.form.fields")) { //$NON-NLS-1$
                        String axis = (String) input.getClientProperty("Pono.form.axis"); //$NON-NLS-1$
                        if (axis.equals("C")) { //$NON-NLS-1$
                            addWrappedBinding(proxy, "rotation", input, "text", decimal); //$NON-NLS-1$ //$NON-NLS-2$
                        }
                        else {
                            addWrappedBinding(proxy, "length" + axis, input, "text", length); //$NON-NLS-1$ //$NON-NLS-2$
                        }
                    }
                    break;
                }
                case Choice:
                case Segmented:
                    addWrappedBinding(spec.bean, field.property, control, "selectedItem"); //$NON-NLS-1$
                    break;
                case Toggle:
                    addWrappedBinding(spec.bean, field.property, control, "selected"); //$NON-NLS-1$
                    break;
                case Checklist:
                    addWrappedBinding(spec.bean, field.property, control, "selection"); //$NON-NLS-1$
                    break;
                case ReadOnly:
                    bind(UpdateStrategy.READ, spec.bean, field.property, control, "text", TO_TEXT); //$NON-NLS-1$
                    break;
                default:
                    break;
            }
        }
    }

    /** What a read-only field shows: enums by their display names, anything else as it prints. */
    private static final Converter<Object, String> TO_TEXT = new Converter<Object, String>() {
        @Override
        public String convertForward(Object value) {
            if (value instanceof Length) {
                Length length = (Length) value;
                return String.format(java.util.Locale.US, "%.3f %s", length.getValue(), //$NON-NLS-1$
                        length.getUnits().getShortName());
            }
            return value == null ? "\u2014" : DisplayNames.of(value); //$NON-NLS-1$
        }

        @Override
        public Object convertReverse(String value) {
            throw new UnsupportedOperationException();
        }
    };

    @Override
    protected void loadFromModel() {
        // A location reaches its fields through a proxy that hears of the object's changes only
        // when the object tells: one the machine changed behind the form, as an automatic setup
        // does, is read again here.
        for (Map.Entry<Field, MutableLocationProxy> entry : locations.entrySet()) {
            Object value = org.jdesktop.beansbinding.BeanProperty.create(entry.getKey().property)
                    .getValue(spec.bean);
            if (value instanceof org.openpnp.model.Location
                    && !value.equals(entry.getValue().getLocation())) {
                entry.getValue().setLocation((org.openpnp.model.Location) value);
            }
        }
        super.loadFromModel();
        for (Field field : pipelines.keySet()) {
            showPipeline(field);
        }
        refresh();
    }

    @Override
    public void validateInput() throws Exception {
        for (Map.Entry<Field, JLabel> entry : errors.entrySet()) {
            String message = failure(entry.getKey());
            if (message != null) {
                throw new Exception(entry.getKey().label + "\uff1a" + message); //$NON-NLS-1$
            }
        }
    }

    // ---- as the user types --------------------------------------------------------------------

    private void edited() {
        // After the bindings' own listeners have enabled Apply for the edit.
        SwingUtilities.invokeLater(this::refresh);
    }

    /** Shows and hides the conditional fields, and puts each failing check's message in place. */
    private void refresh() {
        boolean valid = true;
        for (Map.Entry<Field, JComponent[]> entry : rows.entrySet()) {
            Field field = entry.getKey();
            boolean visible = field.visibleWhen == null
                    || field.visibleWhen.test(value(field.visibleProperty));
            String message = visible ? failure(field) : null;
            JComponent[] parts = entry.getValue();
            parts[0].setVisible(visible);
            parts[1].setVisible(visible);
            parts[2].setVisible(message != null);
            if (parts[3] != null) {
                parts[3].setVisible(visible);
            }
            ((JLabel) parts[2]).setText(message == null ? "" : message); //$NON-NLS-1$
            if (parts[1] instanceof JTextField) {
                ((JTextField) parts[1]).putClientProperty(FlatClientProperties.OUTLINE,
                        message == null ? null : FlatClientProperties.OUTLINE_ERROR);
            }
            valid &= message == null;
        }
        if (!valid) {
            getApplyAction().setEnabled(false);
        }
        if (spec.onChange != null) {
            spec.onChange.accept(this);
        }
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    /** What the form is about is not the machine's configuration: a placement is the job's. */
    @Override
    protected boolean modifiesConfiguration() {
        return spec.modifiesConfiguration;
    }

    private String failure(Field field) {
        if (field.checks.isEmpty() || field.property == null) {
            return null;
        }
        Object value = value(field.property);
        for (int i = 0; i < field.checks.size(); i++) {
            if (!field.checks.get(i).test(value)) {
                return field.messages.get(i);
            }
        }
        return null;
    }

    /** Marks a status beside the form's heading, for forms that show one. */
    static Chip status(String text, Chip.Tone tone) {
        return new Chip(text, tone, Chip.Shape.Status);
    }
}
