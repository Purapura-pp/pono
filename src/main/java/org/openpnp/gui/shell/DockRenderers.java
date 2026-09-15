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

    /** A board side as the stylesheet's badge: a T in accent or a B in amber, then the word. */
    public static TableCellRenderer side() {
        return new TableCellRenderer() {
            private final JPanel cell = new JPanel(new BorderLayout(6, 0));
            private final SideBadge badge = new SideBadge();
            private final JLabel word = new JLabel();
            {
                cell.setBorder(new EmptyBorder(0, 10, 0, 10));
                JPanel holder = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
                holder.setOpaque(false);
                holder.add(badge);
                holder.add(word);
                cell.add(holder, BorderLayout.WEST);
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
                        g2.fillRoundRect(x, y, s, s, 4, 4);
                        g2.setColor(Color.WHITE);
                        g2.setStroke(new java.awt.BasicStroke(2f));
                        g2.drawLine(x + 4, y + 8, x + 7, y + 11);
                        g2.drawLine(x + 7, y + 11, x + 12, y + 5);
                    }
                    else {
                        g2.setColor(Ui.borderStrong());
                        g2.setStroke(new java.awt.BasicStroke(1.5f));
                        g2.drawRoundRect(x, y, s - 1, s - 1, 4, 4);
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
                g2.fillRoundRect(0, 0, 14, 14, 3, 3);
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
