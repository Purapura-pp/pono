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

    /** The stylesheet's {@code .sec}: a heading row that folds its body away when clicked. */
    @SuppressWarnings("serial")
    public static final class Section extends JPanel {
        private final JPanel body = new JPanel(new BorderLayout());
        private final JLabel chevron = new JLabel(Ui.iconSm("chevdown")); //$NON-NLS-1$
        private final JLabel right = Ui.muted(""); //$NON-NLS-1$
        private boolean collapsed;

        public Section(String icon, String title) {
            setOpaque(false);
            setLayout(new BorderLayout());
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.border()),
                    new EmptyBorder(10, 16, 12, 16)));

            JPanel head = new JPanel();
            head.setOpaque(false);
            head.setLayout(new BoxLayout(head, BoxLayout.X_AXIS));
            head.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            JLabel iconLabel = new JLabel(Ui.icon(icon, 14, Ui.muted()));
            head.add(iconLabel);
            head.add(Box.createHorizontalStrut(8));
            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(Ui.font(Ui.BASE).deriveFont(java.util.Map.of(
                    java.awt.font.TextAttribute.WEIGHT, java.awt.font.TextAttribute.WEIGHT_SEMIBOLD)));
            head.add(titleLabel);
            head.add(Box.createHorizontalGlue());
            right.setFont(Ui.font(11f));
            head.add(right);
            head.add(Box.createHorizontalStrut(6));
            chevron.setForeground(Ui.muted());
            head.add(chevron);
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
            head.add(component, head.getComponentCount() - 2);
            return this;
        }

        public Section content(JComponent content) {
            body.removeAll();
            body.add(content, BorderLayout.CENTER);
            return this;
        }

        public void setCollapsed(boolean collapsed) {
            this.collapsed = collapsed;
            body.setVisible(!collapsed);
            chevron.setIcon(Ui.iconSm(collapsed ? "chevright" : "chevdown")); //$NON-NLS-1$ //$NON-NLS-2$
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.border()),
                    new EmptyBorder(10, 16, collapsed ? 10 : 12, 16)));
            revalidate();
            repaint();
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /** The stylesheet's {@code .form}: label column 88 wide, fields taking the rest, 8 by 10 gaps. */
    @SuppressWarnings("serial")
    public static final class Grid extends JPanel {
        private int row;

        public Grid() {
            setOpaque(false);
            setLayout(new GridBagLayout());
        }

        public Grid row(String label, JComponent field) {
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridy = row;
            gc.insets = new Insets(row == 0 ? 0 : 8, 0, 0, 10);
            gc.anchor = GridBagConstraints.WEST;
            gc.gridx = 0;
            JLabel l = Ui.t2(label);
            l.setFont(Ui.font(12f));
            l.setPreferredSize(new Dimension(88, l.getPreferredSize().height));
            l.setMinimumSize(new Dimension(88, l.getPreferredSize().height));
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
                "arc: 6; background: $Pono.surface2; borderColor: $Pono.borderStrong; " //$NON-NLS-1$
                        + "focusedBorderColor: $Pono.accent; focusWidth: 0; margin: 0,10,0,10"); //$NON-NLS-1$
        field.setFont(mono ? Ui.mono(Ui.BASE, Font.PLAIN) : Ui.font(Ui.BASE));
        field.setPreferredSize(new Dimension(field.getPreferredSize().width, 30));
        field.setMinimumSize(new Dimension(40, 30));
        return field;
    }

    /** {@code .inp} with a small bold prefix, such as the X before a coordinate. */
    public static JTextField input(JTextField field, boolean mono, String prefix) {
        input(field, mono);
        JLabel pre = Ui.muted(prefix);
        pre.setFont(Ui.font(11f, Font.BOLD));
        pre.setBorder(new EmptyBorder(0, 0, 0, 2));
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT, pre);
        return field;
    }

    /** {@code .inp} with the unit tucked at the right. */
    public static JTextField inputWithUnit(JTextField field, String unit) {
        input(field, true);
        JLabel u = Ui.muted(unit);
        u.setFont(Ui.font(11f));
        u.setBorder(new EmptyBorder(0, 0, 0, 4));
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, u);
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
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                    g2.setColor(Ui.borderStrong());
                    g2.setStroke(new java.awt.BasicStroke(1f, java.awt.BasicStroke.CAP_BUTT,
                            java.awt.BasicStroke.JOIN_MITER, 1f, new float[] { 4f, 3f }, 0f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
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

    /** Dress a combo box as {@code .inp} with the caret at the right. */
    public static <T> JComboBox<T> dropdown(JComboBox<T> combo) {
        combo.putClientProperty(FlatClientProperties.STYLE,
                "arc: 6; background: $Pono.surface2; borderColor: $Pono.borderStrong; " //$NON-NLS-1$
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
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (isEnabled()) {
                        setSelected(!on);
                        for (Runnable listener : new java.util.ArrayList<>(listeners)) {
                            listener.run();
                        }
                    }
                }
            });
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
                g2.setColor(on ? Ui.accent() : Ui.surface3());
                g2.fillRoundRect(0, 0, 30, 17, 17, 17);
                g2.setColor(on ? Ui.accent() : Ui.borderStrong());
                g2.drawRoundRect(0, 0, 29, 16, 17, 17);
                g2.setColor(on ? Color.WHITE : Ui.muted());
                g2.fillOval(on ? 15 : 2, 2, 13, 13);
                if (!isEnabled()) {
                    g2.setColor(Ui.alpha(Ui.surface(), 0.5));
                    g2.fillRoundRect(0, 0, 30, 17, 17, 17);
                }
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
}
