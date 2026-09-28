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

package org.openpnp.gui.calibration;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.openpnp.Translations;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.RoundedPanel;
import org.openpnp.gui.shell.Tokens;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.LengthConverter;
import org.openpnp.model.DisplayPreferences;
import org.openpnp.model.Solutions;
import org.openpnp.util.UiUtils;

/**
 * What an issue of Issues and Solutions lets be set or chosen before it is accepted, laid out as
 * the forms are: its settings as labelled rows, its choices as cards to pick one of. What it says
 * about itself at length is a paragraph of its own, {@link #description}, not a setting.
 */
public final class IssueControls {
    private static final int SLIDER_MAX = 10000;

    private IssueControls() {
    }

    /** The issue's longer description as text that wraps, or null when it has none. */
    public static String description(Solutions.Issue issue) {
        String text = Html.plain(issue.getExtendedDescription());
        return text.isEmpty() ? null : text;
    }

    /** Whether it has anything to set or choose. */
    public static boolean hasControls(Solutions.Issue issue) {
        return issue.getProperties().length > 0 || issue.getChoices().length > 0;
    }

    /**
     * Its settings and choices, or null when it has none. Each is written into the issue as it is
     * edited, as the issue expects; the issue is activated, as it is whenever it is shown.
     */
    public static JComponent of(Solutions.Issue issue, DisplayPreferences preferences) {
        if (!hasControls(issue)) {
            return null;
        }
        boolean open = issue.getState() == Solutions.State.Open;
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        if (issue.getProperties().length > 0) {
            Forms.Grid grid = new Forms.Grid();
            for (Solutions.Issue.CustomProperty property : issue.getProperties()) {
                JComponent control = control(property, open, preferences);
                if (control != null) {
                    control.setToolTipText(property.getToolTip());
                    grid.row(label(property), control);
                }
            }
            grid.setAlignmentX(Component.LEFT_ALIGNMENT);
            column.add(grid);
        }
        if (issue.getChoices().length > 0) {
            if (column.getComponentCount() > 0) {
                column.add(Box.createVerticalStrut(12));
            }
            JComponent choices = choices(issue, open);
            choices.setAlignmentX(Component.LEFT_ALIGNMENT);
            column.add(choices);
        }
        UiUtils.messageBoxOnExceptionLater(() -> issue.activate());
        return column;
    }

    /** A property's label as one line of text: some are HTML over two lines. */
    private static String label(Solutions.Issue.CustomProperty property) {
        return Html.plain(property.getLabel()).replace('\n', ' ');
    }

    private static JComponent control(Solutions.Issue.CustomProperty property, boolean open,
            DisplayPreferences preferences) {
        JComponent control;
        if (property instanceof Solutions.Issue.MultiLineTextProperty) {
            control = multiLine((Solutions.Issue.MultiLineTextProperty) property);
        }
        else if (property instanceof Solutions.Issue.StringProperty) {
            control = text((Solutions.Issue.StringProperty) property);
        }
        else if (property instanceof Solutions.Issue.BooleanProperty) {
            control = toggle((Solutions.Issue.BooleanProperty) property);
        }
        else if (property instanceof Solutions.Issue.IntegerProperty) {
            control = integer((Solutions.Issue.IntegerProperty) property);
        }
        else if (property instanceof Solutions.Issue.DoubleProperty) {
            control = slider((Solutions.Issue.DoubleProperty) property);
        }
        else if (property instanceof Solutions.Issue.LengthProperty) {
            control = length((Solutions.Issue.LengthProperty) property, preferences);
        }
        else if (property instanceof Solutions.Issue.ActionProperty) {
            control = action((Solutions.Issue.ActionProperty) property);
        }
        else {
            return null;
        }
        enable(control, open);
        return control;
    }

    private static void enable(Component component, boolean enabled) {
        component.setEnabled(enabled);
        if (component instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) component).getComponents()) {
                enable(child, enabled);
            }
        }
    }

    private static void onText(javax.swing.text.JTextComponent field, Runnable changed) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                changed.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changed.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                changed.run();
            }
        });
    }

    private static JComponent text(Solutions.Issue.StringProperty property) {
        if (property.getSuggestions() != null) {
            JComboBox<String> combo = Forms.dropdown(new JComboBox<>(property.getSuggestions()));
            combo.setEditable(true);
            combo.setSelectedItem(property.get());
            combo.addActionListener(e -> UiUtils.messageBoxOnException(() -> property.set((String) combo.getSelectedItem())));
            return combo;
        }
        JTextField field = Forms.input(new JTextField(property.get()), true);
        onText(field, () -> UiUtils.messageBoxOnException(() -> property.set(field.getText())));
        return field;
    }

    private static JComponent multiLine(Solutions.Issue.MultiLineTextProperty property) {
        JTextArea area = new JTextArea(property.get(), 4, 24);
        area.setFont(Ui.mono(12f, java.awt.Font.PLAIN));
        onText(area, () -> UiUtils.messageBoxOnException(() -> property.set(area.getText())));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(240, 84));
        if (property.getSuggestions() == null) {
            return scroll;
        }
        // A template chosen puts its text in the box, to be adjusted there.
        JComboBox<String> templates = Forms.dropdown(new JComboBox<>(property.getSuggestions()));
        templates.setSelectedItem(null);
        templates.setToolTipText(property.getSuggestionToolTip());
        templates.addActionListener(e -> {
            if (templates.getSelectedItem() != null) {
                area.setText((String) templates.getSelectedItem());
            }
        });
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        panel.add(templates, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private static JComponent toggle(Solutions.Issue.BooleanProperty property) {
        Forms.Toggle toggle = new Forms.Toggle();
        toggle.setSelected(property.get());
        toggle.onChange(() -> {
            property.set(toggle.isSelected());
            // An issue may refuse the value; the switch shows what it holds.
            toggle.setSelected(property.get());
        });
        JPanel row = Forms.row(toggle);
        row.add(Box.createHorizontalGlue());
        return row;
    }

    private static JComponent integer(Solutions.Issue.IntegerProperty property) {
        int value = property.get();
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, Math.min(value, property.getMin()),
                Math.max(value, property.getMax()), 1));
        spinner.addChangeListener(e -> {
            int wanted = (Integer) spinner.getValue();
            UiUtils.messageBoxOnException(() -> property.set(wanted));
            if (property.get() != wanted) {
                spinner.setValue(property.get());
            }
        });
        spinner.setPreferredSize(new Dimension(110, spinner.getPreferredSize().height));
        JPanel row = Forms.row(spinner);
        row.add(Box.createHorizontalGlue());
        return row;
    }

    private static JComponent slider(Solutions.Issue.DoubleProperty property) {
        double min = property.getMin();
        double span = property.getMax() - min;
        JSlider slider = new JSlider(0, SLIDER_MAX, (int) Math.round((property.get() - min) * SLIDER_MAX / span));
        JLabel shown = Ui.t2(String.format(Locale.ROOT, "%.3f", property.get())); //$NON-NLS-1$
        shown.setFont(Ui.mono(12f, java.awt.Font.PLAIN));
        slider.addChangeListener(e -> {
            UiUtils.messageBoxOnException(() -> property.set(min + slider.getValue() * span / SLIDER_MAX));
            shown.setText(String.format(Locale.ROOT, "%.3f", property.get())); //$NON-NLS-1$
        });
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(slider, BorderLayout.CENTER);
        row.add(shown, BorderLayout.EAST);
        return row;
    }

    private static JComponent length(Solutions.Issue.LengthProperty property, DisplayPreferences preferences) {
        LengthConverter converter = new LengthConverter(preferences);
        JTextField field = new JTextField(converter.convertForward(property.get()));
        onText(field, () -> {
            org.openpnp.model.Length typed;
            try {
                typed = converter.convertReverse(field.getText());
            }
            catch (RuntimeException e) {
                // Half typed: nothing to write yet.
                return;
            }
            UiUtils.messageBoxOnException(() -> property.set(typed));
        });
        String unit = preferences.getSystemUnits().getShortName();
        JTextField input = Forms.inputWithUnit(Forms.input(field, true), unit);
        input.setPreferredSize(new Dimension(130, input.getPreferredSize().height));
        JPanel row = Forms.row(input);
        row.add(Box.createHorizontalGlue());
        return row;
    }

    private static JComponent action(Solutions.Issue.ActionProperty property) {
        Action action = property.get();
        String name = action.getValue(Action.NAME) instanceof String
                ? Translations.translateText((String) action.getValue(Action.NAME)) : ""; //$NON-NLS-1$
        JButton button = Ui.button(name, action.getValue(Action.SMALL_ICON) instanceof javax.swing.Icon
                ? (javax.swing.Icon) action.getValue(Action.SMALL_ICON) : null, Ui.Size.Sm, Ui.Variant.Default);
        button.addActionListener(action);
        JPanel row = Forms.row(button);
        row.add(Box.createHorizontalGlue());
        return row;
    }

    /** The choices as cards, the one the issue holds marked; the first is chosen when it holds none. */
    private static JComponent choices(Solutions.Issue issue, boolean open) {
        List<ChoiceCard> cards = new ArrayList<>();
        ButtonGroup group = new ButtonGroup();
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        for (Solutions.Issue.Choice choice : issue.getChoices()) {
            if (choice == null) {
                continue;
            }
            if (issue.getChoice() == null) {
                issue.setChoice(choice.getValue());
            }
            ChoiceCard card = new ChoiceCard(issue, choice, group, cards, open);
            cards.add(card);
            if (column.getComponentCount() > 0) {
                column.add(Box.createVerticalStrut(6));
            }
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            column.add(card);
        }
        return column;
    }

    /** One choice: its heading beside the radio button, what it means under it, and its picture. */
    @SuppressWarnings("serial")
    private static final class ChoiceCard extends RoundedPanel {
        private final JRadioButton radio = new JRadioButton();

        ChoiceCard(Solutions.Issue issue, Solutions.Issue.Choice choice, ButtonGroup group, List<ChoiceCard> all,
                boolean open) {
            this(issue, choice, group, all, open, new boolean[1]);
        }

        private ChoiceCard(Solutions.Issue issue, Solutions.Issue.Choice choice, ButtonGroup group,
                List<ChoiceCard> all, boolean open, boolean[] chosen) {
            super(Tokens.R_MD, () -> chosen[0] ? Ui.accentSoft() : Ui.surface2(),
                    () -> chosen[0] ? Ui.accent() : Ui.border());
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(10, 10, 10, 12));
            radio.setOpaque(false);
            radio.setEnabled(open);
            group.add(radio);
            chosen[0] = issue.getChoice() == choice.getValue();
            radio.setSelected(chosen[0]);
            JPanel radioHolder = new JPanel(new BorderLayout());
            radioHolder.setOpaque(false);
            radioHolder.add(radio, BorderLayout.NORTH);
            add(radioHolder, BorderLayout.WEST);

            String description = choice.getDescription();
            String heading = Html.heading(description);
            String plain = Html.plain(description);
            String rest = plain.startsWith(heading) ? plain.substring(heading.length()).trim() : plain;
            JPanel words = new JPanel();
            words.setOpaque(false);
            words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
            JLabel title = new JLabel(heading);
            title.setFont(Ui.weighted(Tokens.FS_BODY, Tokens.FW_SECTION));
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            words.add(title);
            if (!rest.isEmpty()) {
                words.add(Box.createVerticalStrut(3));
                JTextArea text = Forms.paragraph(rest);
                text.setAlignmentX(Component.LEFT_ALIGNMENT);
                words.add(text);
            }
            if (choice.getIcon() != null) {
                words.add(Box.createVerticalStrut(8));
                JLabel picture = new JLabel(choice.getIcon());
                picture.setAlignmentX(Component.LEFT_ALIGNMENT);
                words.add(picture);
            }
            add(words, BorderLayout.CENTER);

            Runnable pick = () -> {
                if (!open) {
                    return;
                }
                issue.setChoice(choice.getValue());
                for (ChoiceCard card : all) {
                    card.radio.setSelected(card == this);
                    card.repaint();
                }
            };
            radio.addActionListener(e -> pick.run());
            if (open) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        pick.run();
                    }
                });
            }
            radio.addItemListener(e -> {
                chosen[0] = radio.isSelected();
                repaint();
            });
        }
    }
}
