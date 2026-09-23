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

package org.openpnp.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.io.File;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.function.ToIntFunction;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

import org.openpnp.Translations;
import org.openpnp.gui.shell.Tokens;
import org.openpnp.gui.shell.Ui;
import org.openpnp.model.Configuration;
import org.openpnp.model.Job;
import org.openpnp.model.Location;
import org.openpnp.model.PlacementsHolder;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;

/**
 * The board or panel definitions down the left of their page, as the mockups draw them: the name
 * in bold, with an amber dot while it has changes that are not saved, its file and size under it,
 * and at the right how many placements or boards it holds. It was a table of the name, the width
 * and the length, edited in place, over the placements in a split; the name and the size are
 * edited in the properties column now.
 */
final class DefinitionList {
    private DefinitionList() {
    }

    /**
     * A table of definitions as the list: one column of two lines to a row, no header, no
     * editing in place.
     * 
     * @param count What the number at the right of a row counts: placements, or boards.
     */
    static JScrollPane dress(JTable table, ToIntFunction<PlacementsHolder<?>> count) {
        TableColumnModel columns = table.getColumnModel();
        while (columns.getColumnCount() > 1) {
            columns.removeColumn(columns.getColumn(columns.getColumnCount() - 1));
        }
        table.setTableHeader(null);
        table.setRowHeight(48);
        table.setShowGrid(false);
        table.setIntercellSpacing(new java.awt.Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.putClientProperty(FlatClientProperties.STYLE,
                "selectionBackground: $Pono.accentSoft; selectionForeground: $Label.foreground; " //$NON-NLS-1$
                        + "selectionInactiveBackground: $Pono.accentSoft; selectionInactiveForeground: $Label.foreground; " //$NON-NLS-1$
                        + "cellFocusColor: null; background: $Pono.surface"); //$NON-NLS-1$
        columns.getColumn(0).setCellRenderer(new Renderer(count));
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Ui.surface());
        return scroll;
    }

    /** Where a definition's file is, short: its folder and its name, "jobs\demo-board.board.xml". */
    static String where(File file) {
        if (file == null) {
            return Translations.getString("DefinitionList.NoFile"); //$NON-NLS-1$
        }
        File parent = file.getParentFile();
        return parent == null ? file.getName() : parent.getName() + File.separator + file.getName();
    }

    /** Its size, "160 × 120 mm"; "size not set" while it is zero. */
    static String size(PlacementsHolder<?> holder) {
        Location dimensions = holder.getDimensions();
        if (dimensions == null || (dimensions.getX() == 0 && dimensions.getY() == 0)) {
            return Translations.getString("DefinitionList.NoSize"); //$NON-NLS-1$
        }
        return number(dimensions.getX()) + " \u00d7 " + number(dimensions.getY()) + " " //$NON-NLS-1$ //$NON-NLS-2$
                + dimensions.getUnits().getShortName();
    }

    static String number(double value) {
        return new BigDecimal(String.format(Locale.ROOT, "%.3f", value)).stripTrailingZeros().toPlainString(); //$NON-NLS-1$
    }

    /**
     * What uses the selected definition, for the line under the list: the job and how many
     * times, a loaded panel, or nothing, which is what Clean up takes off. Short, for a list 220
     * pixels wide; {@link #usageTip} says it with the names.
     */
    static String usage(Configuration configuration, PlacementsHolder<?> holder) {
        if (holder == null) {
            return Translations.getString("DefinitionList.Usage.NoneSelected"); //$NON-NLS-1$
        }
        Job job = configuration.getJob();
        int instances = job == null ? 0 : job.instanceCount(holder);
        if (instances > 0) {
            return String.format(Translations.getString("DefinitionList.Usage.InJob"), instances); //$NON-NLS-1$
        }
        if (configuration.isInUse(holder)) {
            return Translations.getString("DefinitionList.Usage.InPanel"); //$NON-NLS-1$
        }
        return Translations.getString("DefinitionList.Usage.Unused"); //$NON-NLS-1$
    }

    /** The usage with the job's file and the definition's name: "demo-board.job.xml uses demo-board × 2". */
    static String usageTip(Configuration configuration, PlacementsHolder<?> holder) {
        Job job = configuration.getJob();
        int instances = holder == null || job == null ? 0 : job.instanceCount(holder);
        if (instances <= 0) {
            return null;
        }
        String jobName = job.getFile() == null ? Translations.getString("DefinitionList.Usage.ThisJob") //$NON-NLS-1$
                : job.getFile().getName();
        return String.format(Translations.getString("DefinitionList.Usage.InJobTip"), jobName, //$NON-NLS-1$
                holder.getName(), instances);
    }

    /** The amber dot after the name of a definition with unsaved changes. */
    private static final Icon UNSAVED = new Icon() {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Ui.warn());
                int d = UIScale.scale(7);
                g2.fillOval(x, y + (getIconHeight() - d) / 2, d, d);
            }
            finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return UIScale.scale(7);
        }

        @Override
        public int getIconHeight() {
            return UIScale.scale(9);
        }
    };

    private static final class Renderer extends JPanel implements TableCellRenderer {
        private final ToIntFunction<PlacementsHolder<?>> count;
        private final JLabel name = new JLabel();
        private final JLabel detail = Ui.muted(""); //$NON-NLS-1$
        private final JLabel number = Ui.mono("", 12f); //$NON-NLS-1$

        Renderer(ToIntFunction<PlacementsHolder<?>> count) {
            super(new BorderLayout(8, 0));
            this.count = count;
            setBorder(new EmptyBorder(6, 12, 6, 12));
            name.setFont(Ui.font(Tokens.FS_TABLE, java.awt.Font.BOLD));
            name.setHorizontalTextPosition(SwingConstants.LEADING);
            name.setIconTextGap(6);
            detail.setFont(Ui.font(11.5f));
            JPanel lines = new JPanel(new GridLayout(2, 1, 0, 1));
            lines.setOpaque(false);
            lines.add(name);
            lines.add(detail);
            add(lines, BorderLayout.CENTER);
            number.setForeground(Ui.text2());
            add(number, BorderLayout.EAST);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            Object object = row < table.getRowCount()
                    ? ((org.openpnp.gui.tablemodel.AbstractObjectTableModel) table.getModel())
                            .getRowObjectAt(table.convertRowIndexToModel(row))
                    : null;
            if (object instanceof PlacementsHolder) {
                PlacementsHolder<?> holder = (PlacementsHolder<?>) object;
                name.setText(holder.getName());
                name.setIcon(holder.isDirty() ? UNSAVED : null);
                name.setForeground(table.getForeground());
                detail.setText(where(holder.getFile()) + " \u00b7 " + DefinitionList.size(holder)); //$NON-NLS-1$
                number.setText(String.valueOf(count.applyAsInt(holder)));
            }
            setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return this;
        }
    }
}
