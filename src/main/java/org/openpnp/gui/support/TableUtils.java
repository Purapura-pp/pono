/*
 * Copyright (C) 2022 Jason von Nieda <jason@vonnieda.org>, Tony Luken <tonyluken62+openpnp@gmail.com>
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
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import org.openpnp.Translations;
import org.openpnp.gui.shell.ErrorMessages;
import org.openpnp.gui.shell.Tokens;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.tablemodel.ColumnAlignable;
import org.pmw.tinylog.Logger;

/**
 * The tables' common behaviour: alignment, the header, widths by content, hidden columns.
 * <p>
 * Renderers are looked up when a cell is painted, not when the table is set up. The alignment
 * wrapper used to take the renderer the table had at that moment and keep it, so the renderers a
 * page installed afterwards for a column class - the check boxes, the side badges - never showed.
 * <p>
 * Widths come from the content: the header and a sample of the cells. A width the user dragged is
 * remembered; nothing else is. The widths of the first layout used to be stored as if chosen, and
 * every later layout scaled them, which is how ten columns came to share the width equally and
 * show an ellipsis each.
 */
public class TableUtils {
    public static final int MIN_COLUMN_WIDTH = 10;

    /** What a column holds: it decides the width it may take and which goes first when narrow. */
    public enum Kind {
        /** A check box or toggle: as wide as its header. */
        Check,
        /** An identifier, bold, not wider than its content. */
        Id,
        /** A name or description: takes the room that is left. */
        Name,
        /** A number, right aligned and monospaced, not wider than its content. */
        Number,
        /** A status capsule. */
        Status,
        /** Shown when there is room; hidden first when there is not. */
        Secondary
    }

    /** Implemented by table models that know their columns better than the defaults do. */
    public interface ColumnKinds {
        /** One per model column. */
        Kind[] getColumnKinds();
    }

    private static final String KINDS = "Pono.table.kinds"; //$NON-NLS-1$
    private static final String PREFS = "Pono.table.prefs"; //$NON-NLS-1$
    private static final String PREF_KEY = "Pono.table.prefKey"; //$NON-NLS-1$
    private static final String REMOVED = "Pono.table.removedColumns"; //$NON-NLS-1$
    private static final String TRUNCATION_TIP = "Pono.table.truncationTip"; //$NON-NLS-1$
    /** Cells beyond this are not measured for the natural width: enough to see the shape. */
    private static final int SAMPLE_ROWS = 120;
    private static final int MAX_NATURAL = 420;
    /**
     * Room above the measured text. Text is measured at 100 %; painted at 125 or 150 % its width
     * comes out a little larger, and a column with nothing to spare shows R11 as "R...".
     */
    private static final int SLACK = 14;

    /**
     * Sets the horizontal alignment of each of the columns of the specified table, with the
     * header aligned to match: numbers right, everything else left.
     * @param tableModel - the table model for the table. Must implement ColumnAlignable.
     * @param table - the table whose columns' horizontal alignment is to be set
     */
    public static void setColumnAlignment(ColumnAlignable tableModel, JTable table) {
        int[] alignments = tableModel.getColumnAlignments();
        for (int iCol = 0; iCol < table.getColumnCount(); iCol++) {
            TableColumn column = table.getColumnModel().getColumn(iCol);
            int alignment = headerAlignment(alignments[column.getModelIndex()]);
            column.setHeaderRenderer(new HeaderRenderer(alignment));
            column.setCellRenderer(new AlignedRenderer(alignments[column.getModelIndex()]));
        }
    }

    private static int headerAlignment(int alignment) {
        return alignment == SwingConstants.RIGHT || alignment == SwingConstants.TRAILING
                ? SwingConstants.RIGHT : SwingConstants.LEFT;
    }

    /** The header's own renderer, aligned: numbers right, everything else left. */
    private static final class HeaderRenderer implements TableCellRenderer {
        private final int alignment;

        HeaderRenderer(int alignment) {
            this.alignment = alignment;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            JTableHeader header = table.getTableHeader();
            Component component = header.getDefaultRenderer().getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            if (component instanceof JLabel) {
                ((JLabel) component).setHorizontalAlignment(alignment);
            }
            return component;
        }
    }

    /**
     * The renderer the table has for the column's class, looked up for every cell, aligned; a
     * number in the table's numeric style; the full text as the tooltip when the cell cuts it.
     */
    private static final class AlignedRenderer implements TableCellRenderer {
        private final int alignment;

        AlignedRenderer(int alignment) {
            this.alignment = alignment;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            TableCellRenderer delegate = table.getDefaultRenderer(table.getColumnClass(column));
            Component component = delegate.getTableCellRendererComponent(table, value,
                    isSelected, hasFocus, row, column);
            if (!(component instanceof JLabel)) {
                return component;
            }
            JLabel label = (JLabel) component;
            label.setHorizontalAlignment(alignment);
            if (delegate instanceof DefaultTableCellRenderer && alignment == SwingConstants.RIGHT) {
                // The stylesheet's td.num: monospaced figures in the secondary colour.
                label.setFont(Ui.mono(Tokens.FS_TABLE, java.awt.Font.PLAIN));
                if (!isSelected) {
                    label.setForeground(Ui.text2());
                }
            }
            truncationTip(table, label, column);
            return component;
        }
    }

    /** A cut cell shows its whole text on hover; a tooltip of the renderer's own is left alone. */
    static void truncationTip(JTable table, JLabel label, int column) {
        String text = label.getText();
        boolean ours = Boolean.TRUE.equals(label.getClientProperty(TRUNCATION_TIP));
        String tip = label.getToolTipText();
        if (tip != null && !ours) {
            return;
        }
        int width = table.getColumnModel().getColumn(column).getWidth();
        if (text != null && !text.isEmpty() && label.getPreferredSize().width > width) {
            label.setToolTipText(text);
            label.putClientProperty(TRUNCATION_TIP, Boolean.TRUE);
        }
        else if (ours) {
            label.setToolTipText(null);
            label.putClientProperty(TRUNCATION_TIP, null);
        }
    }

    /**
     * Sizes the columns by their content and keeps the widths the user drags. The keys of the
     * earlier versions, which also stored widths nobody chose, are not read.
     * @param table - the table whose columns' widths are to be saved/restored
     * @param prefs - the Preferences node where the widths are stored/retrieved
     * @param prefKey - the key prefix for the particular table
     */
    public static void installColumnWidthSavers(JTable table, Preferences prefs, String prefKey) {
        String key = prefKey + ".v2."; //$NON-NLS-1$
        table.putClientProperty(PREFS, prefs);
        table.putClientProperty(PREF_KEY, prefKey);
        table.getColumnModel().addColumnModelListener(new javax.swing.event.TableColumnModelListener() {
            @Override
            public void columnMarginChanged(javax.swing.event.ChangeEvent e) {
                TableColumn resizing = table.getTableHeader() == null ? null
                        : table.getTableHeader().getResizingColumn();
                if (resizing != null) {
                    prefs.putInt(key + resizing.getModelIndex(), resizing.getWidth());
                }
            }

            @Override
            public void columnAdded(javax.swing.event.TableColumnModelEvent e) {
            }

            @Override
            public void columnRemoved(javax.swing.event.TableColumnModelEvent e) {
            }

            @Override
            public void columnMoved(javax.swing.event.TableColumnModelEvent e) {
            }

            @Override
            public void columnSelectionChanged(javax.swing.event.ListSelectionEvent e) {
            }
        });
        restoreHidden(table, prefs, key);
        Runnable fit = () -> fitColumns(table, prefs, key);
        table.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                fit.run();
            }
        });
        table.getModel().addTableModelListener(e -> {
            if (e.getFirstRow() == TableModelEvent.HEADER_ROW || e.getType() != TableModelEvent.UPDATE
                    || e.getLastRow() == Integer.MAX_VALUE) {
                SwingUtilities.invokeLater(fit);
            }
        });
        table.addPropertyChangeListener("model", (PropertyChangeEvent e) -> SwingUtilities.invokeLater(fit)); //$NON-NLS-1$
        SwingUtilities.invokeLater(fit);
    }

    /**
     * The keys a table is worked with: Enter edits the selected cell where it can be edited,
     * Delete runs the page's delete, which asks first. Enter used to move to the next row and
     * Delete did nothing.
     * 
     * @param delete The page's delete action, or null for a table nothing is deleted from.
     */
    public static void bindKeys(JTable table, javax.swing.Action delete) {
        javax.swing.InputMap keys = table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        javax.swing.Action nextRow = table.getActionMap().get(keys.get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)));
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "pono.edit"); //$NON-NLS-1$
        table.getActionMap().put("pono.edit", new javax.swing.AbstractAction() { //$NON-NLS-1$
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                int row = table.getSelectedRow();
                int column = table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
                if (row >= 0 && (column < 0 || column >= table.getColumnCount()
                        || !table.isCellEditable(row, column))) {
                    // The cell selected cannot be edited: the first one along the row that can.
                    column = -1;
                    for (int c = 0; c < table.getColumnCount() && column < 0; c++) {
                        if (table.isCellEditable(row, c)) {
                            column = c;
                        }
                    }
                }
                if (row >= 0 && column >= 0) {
                    table.editCellAt(row, column, e);
                    Component editor = table.getEditorComponent();
                    if (editor != null) {
                        editor.requestFocusInWindow();
                    }
                }
                else if (nextRow != null) {
                    nextRow.actionPerformed(e);
                }
            }
        });
        if (delete != null) {
            keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "pono.delete"); //$NON-NLS-1$
            table.getActionMap().put("pono.delete", new javax.swing.AbstractAction() { //$NON-NLS-1$
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    if (delete.isEnabled() && table.getSelectedRowCount() > 0 && !table.isEditing()) {
                        delete.actionPerformed(e);
                    }
                }
            });
        }
    }

    /**
     * For a table model whose setValueAt refused a value. The value used to be dropped with only a
     * line in the log, so the cell went back to what it was and nobody knew why; now the cell
     * says so, under itself, for a few seconds.
     */
    public static void rejected(javax.swing.table.TableModel model, int column, Object value, Exception e) {
        Logger.warn(e, "Failed to apply the edit of column {}, the value was discarded.", //$NON-NLS-1$
                model.getColumnName(column));
        String reason = rejection(value, e);
        Component focus = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        JTable table = focus instanceof JTable ? (JTable) focus
                : (JTable) SwingUtilities.getAncestorOfClass(JTable.class, focus);
        // The table edits the cell until setValueAt returns, so it still knows which cell it was.
        if (table != null && table.getModel() == model && table.isEditing()) {
            int row = table.getEditingRow();
            int viewColumn = table.getEditingColumn();
            SwingUtilities.invokeLater(() -> showRejection(table, row, viewColumn, reason));
        }
        else {
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }

    /** Why a value was refused, in the words the user needs: a number, mostly. */
    static String rejection(Object value, Throwable e) {
        if (e instanceof NumberFormatException) {
            return String.format(Translations.getString("Table.InvalidValue.Number"), value); //$NON-NLS-1$
        }
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = e.getClass().getSimpleName();
        }
        return String.format(Translations.getString("Table.InvalidValue"), //$NON-NLS-1$
                ErrorMessages.explain(null, message).what);
    }

    private static Popup rejectionPopup;
    private static final Timer rejectionTimer = new Timer(4000, e -> hideRejection());

    private static void showRejection(JTable table, int row, int column, String reason) {
        hideRejection();
        if (!table.isShowing() || row >= table.getRowCount() || column >= table.getColumnCount()) {
            return;
        }
        JLabel label = new JLabel(reason, Ui.iconSm("alert"), SwingConstants.LEADING); //$NON-NLS-1$
        label.setOpaque(true);
        label.setBackground(Ui.surface());
        label.setForeground(Ui.text());
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Ui.err()), BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        Rectangle cell = table.getCellRect(row, column, true);
        Point at = new Point(cell.x, cell.y + cell.height + 2);
        SwingUtilities.convertPointToScreen(at, table);
        rejectionPopup = PopupFactory.getSharedInstance().getPopup(table, label, at.x, at.y);
        rejectionPopup.show();
        rejectionTimer.setRepeats(false);
        rejectionTimer.restart();
    }

    private static void hideRejection() {
        if (rejectionPopup != null) {
            rejectionPopup.hide();
            rejectionPopup = null;
        }
    }

    /** As installColumnWidthSavers, unless the table already has its widths looked after. */
    public static void installColumnWidthSaversOnce(JTable table, Preferences prefs, String prefKey) {
        if (table.getClientProperty(PREFS) == null) {
            installColumnWidthSavers(table, prefs, prefKey);
        }
    }

    /** The column settings of a table whose widths this class looks after. */
    public static JPopupMenu columnSettings(JTable table) {
        Object prefs = table.getClientProperty(PREFS);
        Object key = table.getClientProperty(PREF_KEY);
        if (!(prefs instanceof Preferences) || !(key instanceof String)) {
            installColumnWidthSavers(table, Preferences.userNodeForPackage(TableUtils.class),
                    "Table." + table.getModel().getClass().getSimpleName()); //$NON-NLS-1$
            return columnSettings(table);
        }
        return columnSettings(table, (Preferences) prefs, (String) key);
    }

    /**
     * The kinds of the table's model columns: the model's own if it declares them, otherwise read
     * from the column classes and alignments.
     */
    static Kind[] kinds(JTable table) {
        Object declared = table.getClientProperty(KINDS);
        if (declared instanceof Kind[]) {
            return (Kind[]) declared;
        }
        if (table.getModel() instanceof ColumnKinds) {
            return ((ColumnKinds) table.getModel()).getColumnKinds();
        }
        int n = table.getModel().getColumnCount();
        Kind[] kinds = new Kind[n];
        int[] alignments = table.getModel() instanceof ColumnAlignable
                ? ((ColumnAlignable) table.getModel()).getColumnAlignments() : null;
        for (int i = 0; i < n; i++) {
            Class<?> type = table.getModel().getColumnClass(i);
            String name = table.getModel().getColumnName(i);
            if (type == Boolean.class) {
                kinds[i] = Kind.Check;
            }
            else if ("ID".equalsIgnoreCase(name == null ? null : name.trim())) { //$NON-NLS-1$
                kinds[i] = Kind.Id;
            }
            else if (alignments != null && i < alignments.length
                    && (alignments[i] == SwingConstants.RIGHT || alignments[i] == SwingConstants.TRAILING)) {
                kinds[i] = Kind.Number;
            }
            else if (Number.class.isAssignableFrom(type)) {
                kinds[i] = Kind.Number;
            }
            else {
                kinds[i] = Kind.Name;
            }
        }
        return kinds;
    }

    /** Declares the kinds of a table's columns without touching its model. */
    public static void setColumnKinds(JTable table, Kind... kinds) {
        table.putClientProperty(KINDS, kinds);
    }

    /** Each column at its natural width, or at the width the user gave it. */
    static void fitColumns(JTable table, Preferences prefs, String key) {
        if (table.getColumnCount() == 0 || !table.isDisplayable()) {
            return;
        }
        Kind[] kinds = kinds(table);
        TableColumnModel columns = table.getColumnModel();
        for (int i = 0; i < columns.getColumnCount(); i++) {
            TableColumn column = columns.getColumn(i);
            int model = column.getModelIndex();
            Kind kind = model < kinds.length ? kinds[model] : Kind.Name;
            if (column.getHeaderRenderer() == null) {
                // The look and feel centres header text; the stylesheet puts it over the values.
                column.setHeaderRenderer(new HeaderRenderer(
                        kind == Kind.Number ? SwingConstants.RIGHT : SwingConstants.LEFT));
            }
            int header = headerWidth(table, i);
            int natural = Math.min(Math.max(header, contentWidth(table, i)), MAX_NATURAL);
            int saved = prefs.getInt(key + model, -1);
            int min;
            int max;
            switch (kind) {
                case Check:
                    min = natural;
                    max = natural;
                    break;
                case Number:
                case Id:
                case Status:
                    // Not cut below their content while a name can give way instead.
                    min = Math.min(natural, 140);
                    max = natural + 24;
                    break;
                case Secondary:
                    min = Math.min(header, natural);
                    max = Integer.MAX_VALUE;
                    break;
                case Name:
                default:
                    min = Math.min(natural, 96);
                    max = Integer.MAX_VALUE;
                    break;
            }
            if (saved > 0) {
                min = Math.min(min, saved);
                max = Math.max(max, saved);
            }
            column.setMinWidth(Math.max(MIN_COLUMN_WIDTH, min));
            column.setMaxWidth(max);
            column.setPreferredWidth(saved > 0 ? saved : natural);
        }
        hideSecondaryIfNarrow(table, kinds);
    }

    private static int headerWidth(JTable table, int viewColumn) {
        TableColumn column = table.getColumnModel().getColumn(viewColumn);
        JTableHeader header = table.getTableHeader();
        if (header == null) {
            return 40;
        }
        TableCellRenderer renderer = column.getHeaderRenderer() != null ? column.getHeaderRenderer()
                : header.getDefaultRenderer();
        Component component = renderer.getTableCellRendererComponent(table,
                column.getHeaderValue(), false, false, -1, viewColumn);
        return component.getPreferredSize().width + SLACK;
    }

    private static int contentWidth(JTable table, int viewColumn) {
        int rows = table.getRowCount();
        if (rows == 0) {
            return 0;
        }
        int step = Math.max(1, rows / SAMPLE_ROWS);
        int widest = 0;
        for (int row = 0; row < rows; row += step) {
            try {
                Component component = table.prepareRenderer(table.getCellRenderer(row, viewColumn),
                        row, viewColumn);
                widest = Math.max(widest, component.getPreferredSize().width);
            }
            catch (RuntimeException e) {
                // A renderer that cannot draw a row in isolation says nothing about the width.
            }
        }
        return widest + SLACK;
    }

    /**
     * Takes secondary columns out, last first, while the others would be cut; puts them back once
     * they fit again. Only columns the model declares secondary are touched.
     */
    private static void hideSecondaryIfNarrow(JTable table, Kind[] kinds) {
        java.awt.Container parent = table.getParent();
        int available = parent instanceof javax.swing.JViewport ? parent.getWidth() : table.getWidth();
        if (available <= 0) {
            return;
        }
        Map<Integer, TableColumn> removed = removed(table);
        TableColumnModel columns = table.getColumnModel();
        int need = 0;
        for (int i = 0; i < columns.getColumnCount(); i++) {
            need += columns.getColumn(i).getMinWidth();
        }
        for (int i = columns.getColumnCount() - 1; i >= 0 && need > available; i--) {
            TableColumn column = columns.getColumn(i);
            int model = column.getModelIndex();
            if (model < kinds.length && kinds[model] == Kind.Secondary) {
                need -= column.getMinWidth();
                auto(table).add(column);
                removed.put(model, column);
                columns.removeColumn(column);
            }
        }
        for (Map.Entry<Integer, TableColumn> entry : new ArrayList<>(removed.entrySet())) {
            TableColumn column = entry.getValue();
            if (auto(table).contains(column)
                    && need + column.getMinWidth() <= available) {
                need += column.getMinWidth();
                removed.remove(entry.getKey());
                auto(table).remove(column);
                insert(table, column);
            }
        }
    }

    private static final String AUTO = "Pono.table.autoHidden"; //$NON-NLS-1$

    /** The columns taken out for want of room rather than by the user. */
    @SuppressWarnings("unchecked")
    private static Set<TableColumn> auto(JTable table) {
        Object set = table.getClientProperty(AUTO);
        if (!(set instanceof Set)) {
            set = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<TableColumn, Boolean>());
            table.putClientProperty(AUTO, set);
        }
        return (Set<TableColumn>) set;
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, TableColumn> removed(JTable table) {
        Object map = table.getClientProperty(REMOVED);
        if (!(map instanceof Map)) {
            map = new LinkedHashMap<Integer, TableColumn>();
            table.putClientProperty(REMOVED, map);
        }
        return (Map<Integer, TableColumn>) map;
    }

    /** Puts a column back where the model has it among the columns still shown. */
    private static void insert(JTable table, TableColumn column) {
        TableColumnModel columns = table.getColumnModel();
        columns.addColumn(column);
        int to = 0;
        for (int i = 0; i < columns.getColumnCount() - 1; i++) {
            if (columns.getColumn(i).getModelIndex() < column.getModelIndex()) {
                to = i + 1;
            }
        }
        columns.moveColumn(columns.getColumnCount() - 1, to);
    }

    private static void restoreHidden(JTable table, Preferences prefs, String key) {
        Set<Integer> hidden = hiddenColumns(prefs, key);
        TableColumnModel columns = table.getColumnModel();
        for (int i = columns.getColumnCount() - 1; i >= 0; i--) {
            TableColumn column = columns.getColumn(i);
            if (hidden.contains(column.getModelIndex())) {
                removed(table).put(column.getModelIndex(), column);
                columns.removeColumn(column);
            }
        }
    }

    private static Set<Integer> hiddenColumns(Preferences prefs, String key) {
        Set<Integer> hidden = new HashSet<>();
        for (String index : prefs.get(key + "hidden", "").split(",")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            if (!index.isBlank()) {
                hidden.add(Integer.parseInt(index.trim()));
            }
        }
        return hidden;
    }

    /**
     * The column settings: every column with a check box, hidden ones included; a change is
     * remembered for the table.
     */
    public static JPopupMenu columnSettings(JTable table, Preferences prefs, String prefKey) {
        String key = prefKey + ".v2."; //$NON-NLS-1$
        JPopupMenu menu = new JPopupMenu();
        Map<Integer, TableColumn> removed = removed(table);
        List<TableColumn> all = new ArrayList<>();
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            all.add(table.getColumnModel().getColumn(i));
        }
        all.addAll(removed.values());
        all.sort((a, b) -> a.getModelIndex() - b.getModelIndex());
        for (TableColumn column : all) {
            String name = table.getModel().getColumnName(column.getModelIndex());
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(name == null || name.isBlank()
                    ? "#" + (column.getModelIndex() + 1) : name, //$NON-NLS-1$
                    !removed.containsKey(column.getModelIndex()));
            item.addActionListener(e -> {
                if (item.isSelected()) {
                    removed.remove(column.getModelIndex());
                    auto(table).remove(column);
                    insert(table, column);
                }
                else if (table.getColumnCount() > 1) {
                    removed.put(column.getModelIndex(), column);
                    table.getColumnModel().removeColumn(column);
                }
                StringBuilder hidden = new StringBuilder();
                for (Map.Entry<Integer, TableColumn> entry : removed.entrySet()) {
                    if (!auto(table).contains(entry.getValue())) {
                        hidden.append(hidden.length() == 0 ? "" : ",").append(entry.getKey()); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                }
                prefs.put(key + "hidden", hidden.toString()); //$NON-NLS-1$
            });
            menu.add(item);
        }
        return menu;
    }
}
