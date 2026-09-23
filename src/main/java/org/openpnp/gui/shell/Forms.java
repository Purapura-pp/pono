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
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;

import javax.swing.JTextArea;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import com.formdev.flatlaf.FlatClientProperties;

/**
 * The pieces of the stylesheet's property forms: a collapsible section with a heading, a two
 * column grid of label and field, and the fields themselves - inputs with a prefix or a unit,
 * read-only values, drop-downs, segments and toggles.
 * <p>
 * Every measurement is mock.css's: an 88 pixel label column, 8 by 10 gaps, 30 pixel inputs with a
 * 6 pixel arc, 16 pixel side padding on a section.
 */
public final class Forms {
    private Forms() {
    }

    /**
     * The stylesheet's {@code .sec}: a heading row that folds its body away when clicked. While
     * folded, the heading's icon is a chevron pointing right, as {@code .sec.collapsed} draws it.
     */
    @SuppressWarnings("serial")
    public static final class Section extends JPanel {
        private final JPanel body = new JPanel(new BorderLayout());
        private final JLabel iconLabel;
        private final String icon;
        private final JLabel right = Ui.muted(""); //$NON-NLS-1$
        private boolean collapsed;

        public Section(String icon, String title) {
            this.icon = icon;
            setOpaque(false);
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.border()),
                    new EmptyBorder(10, Tokens.PAD_SECTION, 12, Tokens.PAD_SECTION)));

            JPanel head = new JPanel();
            head.setOpaque(false);
            head.setLayout(new BoxLayout(head, BoxLayout.X_AXIS));
            head.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            iconLabel = new JLabel(Ui.icon(icon, 14, Ui.muted()));
            head.add(iconLabel);
            head.add(Box.createHorizontalStrut(8));
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(Ui.weighted(Tokens.FS_SECTION, Tokens.FW_SECTION));
            head.add(titleLabel);
            head.add(Box.createHorizontalGlue());
            right.setFont(Ui.weighted(Tokens.FS_TAG, Tokens.FW_AUX));
            head.add(right);
            head.setPreferredSize(new Dimension(0, 26));
            head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
            head.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    setCollapsed(!collapsed);
                }
            });
            add(head, BorderLayout.NORTH);

            body.setOpaque(false);
            body.setBorder(new EmptyBorder(8, 0, 0, 0));
            add(body, BorderLayout.CENTER);
        }

        /** The muted note at the right of the heading: a unit, a count, "live". */
        public Section withRight(String text) {
            right.setText(text);
            return this;
        }

        /** A control at the right of the heading instead of a note, such as a toggle. */
        public Section withRight(JComponent component) {
            java.awt.Container head = (java.awt.Container) getComponent(0);
            head.remove(right);
            component.setAlignmentY(CENTER_ALIGNMENT);
            head.add(component);
            return this;
        }

        public boolean isCollapsed() {
            return collapsed;
        }

        public Section content(JComponent content) {
            body.removeAll();
            body.add(content, BorderLayout.CENTER);
            return this;
        }

        public void setCollapsed(boolean collapsed) {
            this.collapsed = collapsed;
            body.setVisible(!collapsed);
            iconLabel.setIcon(Ui.icon(collapsed ? "chevright" : icon, 14, Ui.muted())); //$NON-NLS-1$
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.border()),
                    new EmptyBorder(10, Tokens.PAD_SECTION, collapsed ? 10 : 12, Tokens.PAD_SECTION)));
            revalidate();
            repaint();
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /**
     * The stylesheet's {@code .form}: the label column, fields taking the rest, 8 by 10 gaps. A
     * label longer than its column wraps onto a second line; it used to be cut at 88 pixels, which
     * is where half the wizards' labels ended in an ellipsis.
     */
    @SuppressWarnings("serial")
    public static final class Grid extends JPanel {
        private int row;
        private final int labelWidth;

        public Grid() {
            this(Tokens.FORM_LABEL);
        }

        public Grid(int labelWidth) {
            this.labelWidth = labelWidth;
            setOpaque(false);
            setLayout(new GridBagLayout());
        }

        /** A label of the column's width, on as many lines as it needs. */
        public static JLabel label(String text, int width) {
            JLabel l = Ui.t2(text);
            l.setFont(Ui.font(Tokens.FS_SMALL));
            if (l.getPreferredSize().width > width) {
                l.setText("<html><div style='width:" + width + "px'>" //$NON-NLS-1$ //$NON-NLS-2$
                        + text.replace("&", "&amp;").replace("<", "&lt;") + "</div></html>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
            }
            Dimension size = new Dimension(width, l.getPreferredSize().height);
            l.setPreferredSize(size);
            l.setMinimumSize(size);
            return l;
        }

        public Grid row(String label, JComponent field) {
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridy = row;
            gc.insets = new Insets(row == 0 ? 0 : 8, 0, 0, 10);
            gc.anchor = GridBagConstraints.WEST;
            gc.gridx = 0;
            JLabel l = label(label, labelWidth);
            add(l, gc);
            gc.gridx = 1;
            gc.weightx = 1;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.insets = new Insets(row == 0 ? 0 : 8, 0, 0, 0);
            add(field, gc);
            row++;
            return this;
        }
    }

    /** A row of fields 6 pixels apart, sharing the width, as {@code .inp-row}. */
    public static JPanel row(JComponent... fields) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                row.add(Box.createHorizontalStrut(6));
            }
            fields[i].setAlignmentY(Component.CENTER_ALIGNMENT);
            row.add(fields[i]);
        }
        return row;
    }

    /** Dress a text field as {@code .inp}: 30 high, 6 pixel arc, surface-2 on a strong border. */
    public static JTextField input(JTextField field, boolean mono) {
        field.putClientProperty(FlatClientProperties.STYLE,
                "arc: 12; background: $Pono.surface2; borderColor: $Pono.borderStrong; " //$NON-NLS-1$
                        + "focusedBorderColor: $Pono.accent; focusWidth: 0; margin: 0,10,0,10"); //$NON-NLS-1$
        field.setFont(mono ? Ui.mono(Ui.BASE, Font.PLAIN) : Ui.font(Ui.BASE));
        field.setPreferredSize(new Dimension(field.getPreferredSize().width, 30));
        field.setMinimumSize(new Dimension(40, 30));
        return field;
    }

    /**
     * {@code .inp} with a small bold prefix, such as the X before a coordinate. FlatLaf puts a
     * leading component against the border and the text straight after it, so the prefix carries
     * the field's 10 pixel padding before it and the 6 pixel gap after.
     */
    public static JTextField input(JTextField field, boolean mono, String prefix) {
        input(field, mono);
        JLabel pre = Ui.muted(prefix);
        pre.setFont(Ui.font(11f, Font.BOLD));
        pre.setBorder(new EmptyBorder(0, 9, 0, 8));
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT, pre);
        return field;
    }

    /** The unit of {@code .inp .u}: 11 pixels, muted, the field's padding after it. */
    public static JLabel unit(String unit) {
        JLabel u = Ui.muted(unit);
        u.setFont(Ui.font(11f));
        u.setBorder(new EmptyBorder(0, 6, 0, 9));
        return u;
    }

    /** {@code .inp} with the unit tucked at the right. */
    public static JTextField inputWithUnit(JTextField field, String unit) {
        input(field, true);
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, unit(unit));
        return field;
    }

    /**
     * The stylesheet's {@code .search}: 32 high, surface-2 on the plain border, the magnifier,
     * the placeholder, and the shortcut's keycap at the right at its own size.
     */
    public static JTextField search(String placeholder, String shortcut) {
        JTextField field = new JTextField();
        field.putClientProperty(FlatClientProperties.STYLE,
                "arc: 12; background: $Pono.surface2; borderColor: $Pono.border; " //$NON-NLS-1$
                        + "focusedBorderColor: $Pono.accent; focusWidth: 0; margin: 0,8,0,6; " //$NON-NLS-1$
                        + "placeholderForeground: $Pono.textMuted"); //$NON-NLS-1$
        field.setFont(Ui.font(Ui.BASE));
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        JLabel icon = new JLabel(Ui.icon("search", 15, Ui.muted())); //$NON-NLS-1$
        icon.setBorder(new EmptyBorder(0, 9, 0, 8));
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT, icon);
        if (shortcut != null) {
            JPanel holder = new JPanel(new GridBagLayout());
            holder.setOpaque(false);
            holder.setBorder(new EmptyBorder(0, 6, 0, 7));
            holder.add(Ui.kbd(shortcut));
            field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, holder);
        }
        field.setPreferredSize(new Dimension(190, 32));
        field.setMinimumSize(new Dimension(80, 32));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        return field;
    }

    /** {@code .inp.ro}: a value that is shown, not edited - a dashed border, secondary text. */
    @SuppressWarnings("serial")
    public static JComponent readOnly(JComponent content) {
        JPanel box = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Ui.surface2());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                    g2.setColor(Ui.borderStrong());
                    g2.setStroke(new java.awt.BasicStroke(1f, java.awt.BasicStroke.CAP_BUTT,
                            java.awt.BasicStroke.JOIN_MITER, 1f, new float[] { 4f, 3f }, 0f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                }
                finally {
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        box.setOpaque(false);
        box.setBorder(new EmptyBorder(0, 10, 0, 10));
        box.setPreferredSize(new Dimension(box.getPreferredSize().width, 30));
        box.setMinimumSize(new Dimension(40, 30));
        box.add(content, BorderLayout.CENTER);
        if (content instanceof JLabel) {
            content.setForeground(Ui.text2());
        }
        return box;
    }

    public static JComponent readOnly(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Ui.font(Ui.BASE));
        return readOnly(label);
    }

    /** A value that is shown, not edited, with a note such as its unit at the right. */
    public static JComponent readOnly(String text, String note) {
        JComponent box = readOnly(text);
        JLabel u = Ui.muted(note);
        u.setFont(Ui.font(11f));
        box.add(u, BorderLayout.EAST);
        return box;
    }

    /** Dress a combo box as {@code .inp} with the caret at the right. */
    public static <T> JComboBox<T> dropdown(JComboBox<T> combo) {
        combo.putClientProperty(FlatClientProperties.STYLE,
                "arc: 12; background: $Pono.surface2; borderColor: $Pono.borderStrong; " //$NON-NLS-1$
                        + "buttonBackground: null; buttonArrowColor: $Pono.textMuted; focusWidth: 0; " //$NON-NLS-1$
                        + "padding: 0,6,0,6"); //$NON-NLS-1$
        combo.setFont(Ui.font(Ui.BASE));
        combo.setPreferredSize(new Dimension(combo.getPreferredSize().width, 30));
        combo.setMinimumSize(new Dimension(40, 30));
        return combo;
    }

    /** The stylesheet's {@code .toggle} beside a word of explanation. */
    @SuppressWarnings("serial")
    public static final class Toggle extends JComponent {
        private boolean on;
        private final java.util.List<Runnable> listeners = new java.util.ArrayList<>();

        public Toggle() {
            setPreferredSize(new Dimension(30, 17));
            setMinimumSize(new Dimension(30, 17));
            setMaximumSize(new Dimension(30, 17));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusable(true);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    flip();
                }
            });
            getInputMap().put(javax.swing.KeyStroke.getKeyStroke("SPACE"), "flip"); //$NON-NLS-1$ //$NON-NLS-2$
            getActionMap().put("flip", new javax.swing.AbstractAction() { //$NON-NLS-1$
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    flip();
                }
            });
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusGained(java.awt.event.FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    repaint();
                }
            });
        }

        private void flip() {
            if (isEnabled()) {
                setSelected(!on);
                for (Runnable listener : new java.util.ArrayList<>(listeners)) {
                    listener.run();
                }
            }
        }

        public boolean isSelected() {
            return on;
        }

        public void setSelected(boolean on) {
            boolean was = this.on;
            this.on = on;
            repaint();
            firePropertyChange("selected", was, on); //$NON-NLS-1$
        }

        /** Called when the user flips it, not when it is set from the model. */
        public void onChange(Runnable listener) {
            listeners.add(listener);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (!isEnabled()) {
                    // The same toggle, at the stylesheet's disabled opacity.
                    g2.setComposite(java.awt.AlphaComposite.SrcOver.derive(Ui.DISABLED_OPACITY));
                }
                g2.setColor(on ? Ui.accent() : Ui.surface3());
                g2.fillRoundRect(0, 0, 30, 17, 17, 17);
                g2.setColor(isFocusOwner() ? Ui.accentStrong() : on ? Ui.accent() : Ui.borderStrong());
                g2.drawRoundRect(0, 0, 29, 16, 17, 17);
                g2.setColor(on ? Color.WHITE : Ui.muted());
                g2.fillOval(on ? 15 : 2, 2, 13, 13);
            }
            finally {
                g2.dispose();
            }
        }
    }

    /** A toggle with its explanation, as {@code .row > .toggle + .t2}. */
    public static JPanel toggleRow(Toggle toggle, String explanation) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        toggle.setAlignmentY(Component.CENTER_ALIGNMENT);
        row.add(toggle);
        row.add(Box.createHorizontalStrut(8));
        JLabel text = Ui.t2(explanation);
        text.setAlignmentY(Component.CENTER_ALIGNMENT);
        row.add(text);
        row.add(Box.createHorizontalGlue());
        return row;
    }

    /**
     * The stylesheet's {@code .seg}: one of a few values as a row of segments. Its selected item
     * is a bound property, as a combo box's is.
     */
    @SuppressWarnings("serial")
    public static final class Segmented extends RoundedPanel {
        private final java.util.List<Object> items = new java.util.ArrayList<>();
        private final java.util.List<javax.swing.JToggleButton> buttons = new java.util.ArrayList<>();
        private final java.util.List<Runnable> listeners = new java.util.ArrayList<>();
        private Object selected;

        public Segmented(java.util.List<?> items, java.util.function.Function<Object, String> names) {
            super(Tokens.R_SM, Ui::surface2, Ui::border);
            setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 2, 3));
            setBorder(new EmptyBorder(0, 1, 0, 3));
            javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
            for (Object item : items) {
                javax.swing.JToggleButton button = new Ui.ToggleButton(names.apply(item), null);
                Ui.seg(button);
                button.setFont(Ui.font(Tokens.FS_SMALL));
                button.setFocusable(true);
                button.addActionListener(e -> {
                    setSelectedItem(item);
                    for (Runnable listener : new java.util.ArrayList<>(listeners)) {
                        listener.run();
                    }
                });
                group.add(button);
                this.items.add(item);
                buttons.add(button);
                add(button);
            }
            setMaximumSize(getPreferredSize());
        }

        public Object getSelectedItem() {
            return selected;
        }

        public void setSelectedItem(Object item) {
            Object was = selected;
            selected = item;
            for (int i = 0; i < items.size(); i++) {
                buttons.get(i).setSelected(java.util.Objects.equals(items.get(i), item));
            }
            firePropertyChange("selectedItem", was, item); //$NON-NLS-1$
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            for (javax.swing.JToggleButton button : buttons) {
                button.setEnabled(enabled);
            }
        }

        /** Called when the user picks a segment, not when it is set from the model. */
        public void onChange(Runnable listener) {
            listeners.add(listener);
        }

        /**
         * The stylesheet's {@code .seg.tight}, for the values beside a field: 30 pixel segments
         * with 6 pixel padding, in the mono figures.
         */
        public Segmented tight() {
            for (javax.swing.JToggleButton button : buttons) {
                Ui.segTight(button);
            }
            setMaximumSize(getPreferredSize());
            return this;
        }
    }

    /** A status capsule standing in a form row. */
    public static JPanel statusRow(Chip chip, String note) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        chip.setAlignmentY(Component.CENTER_ALIGNMENT);
        row.add(chip);
        if (note != null) {
            row.add(Box.createHorizontalStrut(8));
            JLabel text = Ui.t2(note);
            text.setAlignmentY(Component.CENTER_ALIGNMENT);
            row.add(text);
        }
        row.add(Box.createHorizontalGlue());
        return row;
    }

    /** Small mono secondary text, for a measured value in a form. */
    public static JLabel monoNote(String text) {
        JLabel label = Ui.mono(text, 12f);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        return label;
    }

    /**
     * A check box that carries its words, so clicking them ticks it; the stylesheet's
     * {@code .check} is a 16 pixel box with a 4 pixel radius.
     */
    public static javax.swing.JCheckBox check(String text) {
        javax.swing.JCheckBox check = new javax.swing.JCheckBox(text);
        check.setOpaque(false);
        check.setFont(Ui.font(Tokens.FS_SMALL));
        check.setForeground(Ui.text2());
        check.setIconTextGap(8);
        check.putClientProperty(FlatClientProperties.STYLE, "icon.arc: 8; icon.focusWidth: 0"); //$NON-NLS-1$
        return check;
    }

    /**
     * A list renderer for a select whose value has a note: the name bold, the note grey after it,
     * as the part and feeder selects of the mockups. Enum values are shown by their display names.
     */
    public static <T> javax.swing.ListCellRenderer<T> described(
            java.util.function.Function<T, String> name, java.util.function.Function<T, String> note) {
        return new javax.swing.ListCellRenderer<T>() {
            private final javax.swing.DefaultListCellRenderer delegate = new javax.swing.DefaultListCellRenderer();

            @Override
            public Component getListCellRendererComponent(javax.swing.JList<? extends T> list,
                    T value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) delegate.getListCellRendererComponent(list, value, index,
                        isSelected, cellHasFocus);
                if (value == null) {
                    label.setText(" "); //$NON-NLS-1$
                    return label;
                }
                String n = name.apply(value);
                String d = note == null ? null : note.apply(value);
                String grey = String.format("#%06x", Ui.muted().getRGB() & 0xffffff); //$NON-NLS-1$
                label.setText("<html><b>" + escape(n) + "</b>" //$NON-NLS-1$ //$NON-NLS-2$
                        + (d == null || d.isEmpty() ? "" : "&nbsp;&nbsp;<span style='color:" + grey + "'>" + escape(d) + "</span>") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        + "</html>"); //$NON-NLS-1$
                return label;
            }
        };
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    }

    /**
     * The stylesheet's {@code .empty}: what belongs here, and what to do next. A table with no rows
     * shows this rather than a bare header.
     */
    public static JPanel emptyState(String icon, String title, String text, JComponent... actions) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        RoundedPanel tile = new RoundedPanel(16, Ui::accentSoft, () -> null);
        tile.setLayout(new BorderLayout());
        tile.add(new JLabel(Ui.icon(icon, 26, Ui.accent()), SwingConstants.CENTER), BorderLayout.CENTER);
        Dimension tileSize = new Dimension(56, 56);
        tile.setPreferredSize(tileSize);
        tile.setMaximumSize(tileSize);
        tile.setAlignmentX(Component.CENTER_ALIGNMENT);
        column.add(tile);
        column.add(Box.createVerticalStrut(10));
        JLabel heading = new JLabel(title);
        heading.setFont(Ui.weighted(Tokens.FS_TITLE, Tokens.FW_SECTION));
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);
        column.add(heading);
        if (text != null) {
            column.add(Box.createVerticalStrut(6));
            JLabel body = new JLabel("<html><div style='text-align:center;width:420px'>" + escape(text) + "</div></html>"); //$NON-NLS-1$ //$NON-NLS-2$
            body.setFont(Ui.font(Tokens.FS_SMALL));
            body.setForeground(Ui.muted());
            body.setAlignmentX(Component.CENTER_ALIGNMENT);
            column.add(body);
        }
        if (actions.length > 0) {
            column.add(Box.createVerticalStrut(12));
            JPanel row = new JPanel();
            row.setOpaque(false);
            row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
            for (int i = 0; i < actions.length; i++) {
                if (i > 0) {
                    row.add(Box.createHorizontalStrut(8));
                }
                row.add(actions[i]);
            }
            row.setAlignmentX(Component.CENTER_ALIGNMENT);
            column.add(row);
        }
        panel.add(column);
        return panel;
    }

    /**
     * The stylesheet's {@code .pipeline}: the stages of a vision pipeline as small capsules with
     * arrows between them, and its buttons at the right.
     */
    /**
     * A paragraph that wraps at the width it is given, in the secondary colour: what a step or an
     * issue says about itself. An HTML label asked for one line as wide as its text, and cut off
     * everything after the first line when it was given less.
     */
    @SuppressWarnings("serial")
    public static JTextArea paragraph(String text) {
        JTextArea area = new JTextArea(text == null ? "" : text) { //$NON-NLS-1$
            private int laidOutWidth = -1;

            @Override
            public void setBounds(int x, int y, int width, int height) {
                boolean widthChanged = width != laidOutWidth;
                laidOutWidth = width;
                super.setBounds(x, y, width, height);
                // Its height for this width is only known now: the parent asks again.
                if (widthChanged && width > 0 && getPreferredSize().height != height) {
                    javax.swing.SwingUtilities.invokeLater(this::revalidate);
                }
            }

            @Override
            public Dimension getMinimumSize() {
                return new Dimension(40, super.getMinimumSize().height);
            }
        };
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setBorder(null);
        area.setFont(Ui.font(Ui.BASE));
        area.setForeground(Ui.text2());
        return area;
    }

    public static JPanel pipeline(java.util.List<String> stages, JComponent... buttons) {
        // The stages go on to a second line in a narrow column, each with the arrow before it;
        // the buttons stay at the right. In one line the row was wider than the column at 1366.
        JPanel chips = new JPanel(new org.openpnp.gui.support.WrapLayout(java.awt.FlowLayout.LEFT, 0, 0)) {
            /** As narrow as its widest stage: what it takes to wrap rather than be cut. */
            @Override
            public Dimension getMinimumSize() {
                int widest = 0;
                for (java.awt.Component c : getComponents()) {
                    widest = Math.max(widest, c.getPreferredSize().width);
                }
                return new Dimension(widest, super.getMinimumSize().height);
            }
        };
        chips.setOpaque(false);
        for (int i = 0; i < stages.size(); i++) {
            JPanel item = new JPanel();
            item.setOpaque(false);
            item.setLayout(new BoxLayout(item, BoxLayout.X_AXIS));
            if (i > 0) {
                item.add(Box.createHorizontalStrut(6));
                item.add(Ui.muted("\u203a")); //$NON-NLS-1$
                item.add(Box.createHorizontalStrut(6));
            }
            Chip stage = new Chip(stages.get(i), Chip.Tone.Pending, Chip.Shape.Status).withLed(false);
            stage.setFont(Ui.font(Tokens.FS_TAG));
            item.add(stage);
            chips.add(item);
        }
        JPanel tools = new JPanel();
        tools.setOpaque(false);
        tools.setLayout(new BoxLayout(tools, BoxLayout.X_AXIS));
        for (JComponent button : buttons) {
            tools.add(Box.createHorizontalStrut(6));
            tools.add(button);
        }
        // As wide as the row leaves, and in the middle of its height, as the buttons are.
        JPanel middle = new JPanel(new java.awt.GridBagLayout());
        middle.setOpaque(false);
        java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
        gc.weightx = 1;
        gc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gc.anchor = java.awt.GridBagConstraints.WEST;
        middle.add(chips, gc);
        JPanel row = new JPanel(new java.awt.BorderLayout());
        row.setOpaque(false);
        row.add(middle, java.awt.BorderLayout.CENTER);
        row.add(tools, java.awt.BorderLayout.EAST);
        return row;
    }
}
