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

package org.openpnp.gui.support;

import java.awt.Component;
import java.awt.FontMetrics;

import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.TableCellRenderer;

/**
 * Columns whose text runs on to a second line rather than being cut short: a step's basis, a
 * placement's comment. The row grows to what its longest cell needs and back.
 */
public final class WrappingCells {
    private WrappingCells() {
    }

    /**
     * After the table's own renderers are set: the wrapping cells take their fonts and colours.
     * 
     * @param columns By the model's numbering, so that a hidden column does not shift them.
     */
    public static void install(JTable table, int... columns) {
        for (int column : columns) {
            for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
                javax.swing.table.TableColumn c = table.getColumnModel().getColumn(i);
                if (c.getModelIndex() != column) {
                    continue;
                }
                TableCellRenderer base = c.getCellRenderer() != null ? c.getCellRenderer()
                        : table.getDefaultRenderer(table.getModel().getColumnClass(column));
                c.setCellRenderer(new Renderer(base));
            }
        }
        boolean[] queued = { false };
        Runnable fit = () -> {
            if (!queued[0]) {
                queued[0] = true;
                SwingUtilities.invokeLater(() -> {
                    queued[0] = false;
                    fit(table);
                });
            }
        };
        table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            @Override
            public void columnMarginChanged(ChangeEvent e) {
                fit.run();
            }

            @Override
            public void columnAdded(TableColumnModelEvent e) {
                fit.run();
            }

            @Override
            public void columnRemoved(TableColumnModelEvent e) {
                fit.run();
            }

            @Override
            public void columnMoved(TableColumnModelEvent e) {
            }

            @Override
            public void columnSelectionChanged(ListSelectionEvent e) {
            }
        });
        table.getModel().addTableModelListener(e -> fit.run());
        table.addPropertyChangeListener("model", e -> { //$NON-NLS-1$
            table.getModel().addTableModelListener(m -> fit.run());
            fit.run();
        });
        fit.run();
    }

    /** Each row as tall as its tallest wrapping cell, and no less than the table's own height. */
    static void fit(JTable table) {
        int base = table.getRowHeight();
        for (int row = 0; row < table.getRowCount(); row++) {
            int height = base;
            for (int col = 0; col < table.getColumnCount(); col++) {
                TableCellRenderer renderer = table.getCellRenderer(row, col);
                if (!(renderer instanceof Renderer)) {
                    continue;
                }
                Component c = table.prepareRenderer(renderer, row, col);
                c.setSize(table.getColumnModel().getColumn(col).getWidth(), Short.MAX_VALUE);
                height = Math.max(height, c.getPreferredSize().height);
            }
            if (table.getRowHeight(row) != height) {
                table.setRowHeight(row, height);
            }
        }
    }

    /** A text area as a cell: the words wrap at the column's edge, one line sits as a label would. */
    @SuppressWarnings("serial")
    static final class Renderer extends JTextArea implements TableCellRenderer {
        private final TableCellRenderer base;

        Renderer(TableCellRenderer base) {
            this.base = base;
            setLineWrap(true);
            setWrapStyleWord(true);
            setEditable(false);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {
            Component styled = base.getTableCellRendererComponent(table, value, selected, focused, row, column);
            if (!(styled instanceof javax.swing.JLabel)) {
                return styled;
            }
            setText(((javax.swing.JLabel) styled).getText());
            setFont(styled.getFont());
            setForeground(styled.getForeground());
            setBackground(styled.getBackground());
            // One line in the middle of the table's row, as the other cells have theirs.
            FontMetrics fm = getFontMetrics(getFont());
            int vertical = Math.max(2, (table.getRowHeight() - fm.getHeight()) / 2);
            java.awt.Insets insets = ((javax.swing.JLabel) styled).getInsets();
            setBorder(new EmptyBorder(vertical, insets.left, vertical, insets.right));
            return this;
        }
    }
}
