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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.Function;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import org.openpnp.model.Abstract2DLocatable.Side;

/**
 * Cell renderers for the shapes the stylesheet puts in table cells: the check square, the toggle,
 * the status capsule, the side badge, and numbers in a monospaced face.
 */
public final class DockRenderers {
    private DockRenderers() {
    }

    /** Fill the cell with the row's background: selected or plain. */
    private static void background(JComponent c, JTable table, boolean selected) {
        c.setOpaque(true);
        c.setBackground(selected ? table.getSelectionBackground() : table.getBackground());
    }

    /** A Boolean as the stylesheet's 16 pixel {@code .check}: accent fill and a white tick when on. */
    public static TableCellRenderer check() {
        return new TableCellRenderer() {
            private final Mark mark = new Mark(false);

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                mark.on = Boolean.TRUE.equals(value);
                background(mark, table, isSelected);
                return mark;
            }
        };
    }

    /** A Boolean as the stylesheet's 30 by 17 {@code .toggle}. */
    public static TableCellRenderer toggle() {
        return new TableCellRenderer() {
            private final Mark mark = new Mark(true);

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                mark.on = Boolean.TRUE.equals(value);
                background(mark, table, isSelected);
                return mark;
            }
        };
    }

    /**
     * A value as a status capsule.
     * 
     * @param tone What colour the value earns.
     * @param text What the capsule says; null hides it.
     */
    public static TableCellRenderer status(Function<Object, Chip.Tone> tone,
            Function<Object, String> text) {
        return new TableCellRenderer() {
            private final JPanel cell = new JPanel(new BorderLayout());
            private final Chip chip = new Chip("", Chip.Tone.Neutral, Chip.Shape.Status); //$NON-NLS-1$
            {
                cell.setBorder(new EmptyBorder(0, 10, 0, 10));
                JPanel holder = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
                holder.setOpaque(false);
                holder.add(chip);
                cell.add(holder, BorderLayout.WEST);
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                String label = value == null ? null : text.apply(value);
                chip.setVisible(label != null);
                if (label != null) {
                    chip.setText(label);
                    chip.setTone(tone.apply(value));
                }
                background(cell, table, isSelected);
                return cell;
            }
        };
    }

    /** A number, right aligned, in the monospaced face and the secondary colour. */
    public static TableCellRenderer mono(Function<Object, String> format) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setText(value == null ? "" : format.apply(value)); //$NON-NLS-1$
                setHorizontalAlignment(SwingConstants.RIGHT);
                setFont(Ui.mono(12.5f, Font.PLAIN));
                setForeground(Ui.text2());
                setBorder(new EmptyBorder(0, 10, 0, 10));
                return this;
            }
        };
    }

    /** Text in the muted colour, for a column that is only sometimes worth reading. */
    /** The stylesheet's bold ID: the column a row is known by. */
    public static TableCellRenderer bold() {
        return new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(javax.swing.JTable table,
                    Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setFont(Ui.font(Tokens.FS_TABLE, Font.BOLD));
                return this;
            }
        };
    }

    public static TableCellRenderer muted() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setForeground(Ui.muted());
                setBorder(new EmptyBorder(0, 10, 0, 10));
                return this;
            }
        };
    }

    /**
     * A value that is changed in place from a list, as the mockups draw it: the value with a small
     * chevron after it, so that it can be seen that a click opens the choices. Without the chevron
     * the table's combo box editors were found by those who knew to double-click, and the rest set
     * these values from the right-click menu.
     * 
     * @param applies Whether the row has the choice, given the table and the view row: the error
     *                handling of a fiducial, which is never picked, is only a dash.
     */
    public static TableCellRenderer dropdown(TableCellRenderer inner,
            java.util.function.BiPredicate<JTable, Integer> applies) {
        return new TableCellRenderer() {
            private final JLabel chevron = new JLabel(Ui.icon("chevdown", 12, Ui.muted())); //$NON-NLS-1$
            private final JPanel cell = new JPanel(null) {
                // What the column is sized by: the value and the chevron side by side.
                @Override
                public Dimension getPreferredSize() {
                    Component value = getComponentCount() > 0 ? getComponent(0) : null;
                    Dimension v = value == null ? new Dimension() : value.getPreferredSize();
                    Dimension c = chevron.isVisible() ? chevron.getPreferredSize() : new Dimension();
                    return new Dimension(v.width + c.width, Math.max(v.height, c.height));
                }

                @Override
                public void doLayout() {
                    // The chevron right after the value, as the mockups' caret; the value gives
                    // way, with its ellipsis, where the column is too narrow for both.
                    int h = getHeight();
                    int cw = chevron.isVisible() ? chevron.getPreferredSize().width : 0;
                    Component value = getComponentCount() > 0 ? getComponent(0) : null;
                    if (value == null) {
                        return;
                    }
                    int w = Math.min(value.getPreferredSize().width, Math.max(0, getWidth() - cw));
                    value.setBounds(0, 0, chevron.isVisible() ? w : getWidth(), h);
                    chevron.setBounds(w, 0, cw, h);
                }
            };
            {
                chevron.setBorder(new EmptyBorder(0, 2, 0, 8));
                chevron.setHorizontalAlignment(SwingConstants.LEFT);
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component shown = inner.getTableCellRendererComponent(table, value, isSelected, hasFocus,
                        row, column);
                cell.removeAll();
                cell.add(shown);
                chevron.setVisible(table.isCellEditable(row, column)
                        && (applies == null || applies.test(table, row)));
                cell.add(chevron);
                background(cell, table, isSelected);
                return cell;
            }
        };
    }

    /** A value changed in place from a list, in every row that can be edited. */
    public static TableCellRenderer dropdown(TableCellRenderer inner) {
        return dropdown(inner, null);
    }

    /** A board side as the stylesheet's badge: a T in accent or a B in amber, then the word. */
    public static TableCellRenderer side() {
        return side(true);
    }

    /**
     * A board side as the badge, with the word after it or alone: the letter and its colour say
     * the side, and a table of ten columns has no room for "Bottom".
     */
    public static TableCellRenderer side(boolean withWord) {
        return new TableCellRenderer() {
            // Centred in the row: a flow layout put the badge and the word at the top of it.
            private final JPanel cell = new JPanel(new java.awt.GridBagLayout());
            private final SideBadge badge = new SideBadge();
            private final JLabel word = new JLabel();
            {
                cell.setBorder(new EmptyBorder(0, 10, 0, 10));
                JPanel holder = new JPanel();
                holder.setOpaque(false);
                holder.setLayout(new javax.swing.BoxLayout(holder, javax.swing.BoxLayout.X_AXIS));
                badge.setMaximumSize(badge.getPreferredSize());
                holder.add(badge);
                if (withWord) {
                    holder.add(javax.swing.Box.createHorizontalStrut(6));
                    holder.add(word);
                }
                java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
                gc.anchor = java.awt.GridBagConstraints.WEST;
                gc.weightx = 1;
                cell.add(holder, gc);
                word.setFont(Ui.font(12.5f));
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                badge.top = value == Side.Top;
                badge.setVisible(value != null);
                word.setText(value == null ? "" : String.valueOf(value)); //$NON-NLS-1$
                word.setForeground(table.getForeground());
                background(cell, table, isSelected);
                return cell;
            }
        };
    }

    /** The check or the toggle. */
    private static final class Mark extends JComponent {
        private final boolean toggle;
        boolean on;

        Mark(boolean toggle) {
            this.toggle = toggle;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (isOpaque()) {
                    g2.setColor(getBackground());
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
                if (toggle) {
                    int w = 30, h = 17;
                    int x = 10, y = (getHeight() - h) / 2;
                    g2.setColor(on ? Ui.accent() : Ui.surface3());
                    g2.fillRoundRect(x, y, w, h, h, h);
                    g2.setColor(on ? Ui.accent() : Ui.borderStrong());
                    g2.drawRoundRect(x, y, w - 1, h - 1, h, h);
                    g2.setColor(on ? Color.WHITE : Ui.muted());
                    g2.fillOval(x + (on ? 15 : 2), y + 2, 13, 13);
                }
                else {
                    int s = 16;
                    int x = 10, y = (getHeight() - s) / 2;
                    if (on) {
                        g2.setColor(Ui.accent());
                        g2.fillRoundRect(x, y, s, s, 8, 8);
                        g2.setColor(Color.WHITE);
                        g2.setStroke(new java.awt.BasicStroke(2f));
                        g2.drawLine(x + 4, y + 8, x + 7, y + 11);
                        g2.drawLine(x + 7, y + 11, x + 12, y + 5);
                    }
                    else {
                        g2.setColor(Ui.borderStrong());
                        g2.setStroke(new java.awt.BasicStroke(1.5f));
                        g2.drawRoundRect(x, y, s - 1, s - 1, 8, 8);
                    }
                }
            }
            finally {
                g2.dispose();
            }
        }
    }

    /** The 14 pixel T or B square. */
    private static final class SideBadge extends JComponent {
        boolean top = true;

        SideBadge() {
            setPreferredSize(new Dimension(14, 14));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(top ? Ui.accentSoft() : Ui.warnSoft());
                g2.fillRoundRect(0, 0, 14, 14, 6, 6);
                g2.setColor(top ? Ui.accent() : Ui.warn());
                g2.setFont(Ui.font(9f, Font.BOLD));
                String letter = top ? "T" : "B"; //$NON-NLS-1$ //$NON-NLS-2$
                int w = g2.getFontMetrics().stringWidth(letter);
                g2.drawString(letter, (14 - w) / 2f, 11f);
            }
            finally {
                g2.dispose();
            }
        }
    }
}
