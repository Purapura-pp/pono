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
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;
import javax.swing.table.JTableHeader;

import org.openpnp.Translations;

import com.formdev.flatlaf.FlatClientProperties;

/**
 * The card that holds a page's tables: the stylesheet's {@code .dock}.
 * <p>
 * A row of tabs along the top, each with an icon, a name and a count, with the page's own tools at
 * the right end of the row; below it, whatever the selected tab holds - which is a toolbar and a
 * table, built with the helpers here so that every page's toolbar reads the same way. The
 * toolbars used to be rows of icons with no words, which is why the wiki has a page explaining
 * what each one does.
 */
@SuppressWarnings("serial")
public class DockPanel extends RoundedPanel {
    private final JPanel tabRow = new JPanel();
    private final JPanel tabs = new JPanel();
    private final JPanel tools = new JPanel();
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final ButtonGroup group = new ButtonGroup();
    private final List<Tab> all = new ArrayList<>();
    private final List<ChangeListener> listeners = new ArrayList<>();
    private Tab selected;

    public DockPanel() {
        super(14, Ui::surface, Ui::border);
        setLayout(new BorderLayout());
        tabRow.setOpaque(false);
        tabRow.setLayout(new BorderLayout());
        tabRow.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.border()),
                new EmptyBorder(6, 10, 0, 10)));
        tabs.setOpaque(false);
        tabs.setLayout(new BoxLayout(tabs, BoxLayout.X_AXIS));
        tools.setOpaque(false);
        tools.setLayout(new BoxLayout(tools, BoxLayout.X_AXIS));
        tools.setBorder(new EmptyBorder(0, 0, 4, 0));
        tabRow.add(tabs, BorderLayout.WEST);
        tabRow.add(tools, BorderLayout.EAST);
        add(tabRow, BorderLayout.NORTH);
        body.setOpaque(false);
        add(body, BorderLayout.CENTER);
    }

    /** One tab: its button in the row and its content below. */
    public final class Tab {
        private final JToggleButton button;
        private final JLabel count = new JLabel();
        private final JComponent content;
        private final String key;

        Tab(String key, Icon icon, String label, JComponent content) {
            this.key = key;
            this.content = content;
            button = new JToggleButton(label, icon) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (isSelected()) {
                        // The stylesheet's 2 pixel accent underline, 8 pixels in from either end.
                        Graphics2D g2 = (Graphics2D) g.create();
                        try {
                            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            g2.setColor(Ui.accent());
                            g2.fillRoundRect(8, getHeight() - 2, getWidth() - 16, 2, 2, 2);
                        }
                        finally {
                            g2.dispose();
                        }
                    }
                }
            };
            button.setFocusable(false);
            button.setIconTextGap(7);
            button.setHorizontalTextPosition(SwingConstants.RIGHT);
            button.putClientProperty(FlatClientProperties.STYLE,
                    "arc: 6; focusWidth: 0; borderWidth: 0; minimumHeight: 34; margin: 0,12,0,12; " //$NON-NLS-1$
                            + "background: null; borderColor: null; foreground: $Pono.textSecondary; " //$NON-NLS-1$
                            + "hoverBackground: $Pono.hover; pressedBackground: null; " //$NON-NLS-1$
                            + "selectedBackground: null; selectedForeground: $Label.foreground"); //$NON-NLS-1$
            button.setFont(Ui.font(Ui.BASE).deriveFont(java.util.Map.of(
                    java.awt.font.TextAttribute.WEIGHT, java.awt.font.TextAttribute.WEIGHT_MEDIUM)));
            count.setFont(Ui.font(11f));
            count.setVisible(false);
            button.addActionListener(e -> select(this));
        }

        /** The number in the little capsule after the name; null for none. */
        public void setCount(Integer value) {
            count.setVisible(value != null);
            count.setText(value == null ? "" : String.valueOf(value)); //$NON-NLS-1$
            paintCount();
            tabs.revalidate();
            tabs.repaint();
        }

        private void paintCount() {
            boolean on = selected == this;
            count.setForeground(on ? Ui.accent() : Ui.muted());
            count.putClientProperty("fill", on ? Ui.accentSoft() : Ui.surface3()); //$NON-NLS-1$
            count.repaint();
        }

        public JComponent getContent() {
            return content;
        }
    }

    public Tab addTab(Icon icon, String label, JComponent content) {
        String key = String.valueOf(all.size());
        Tab tab = new Tab(key, icon, label, content);
        all.add(tab);
        group.add(tab.button);
        JPanel cell = new JPanel();
        cell.setOpaque(false);
        cell.setLayout(new BoxLayout(cell, BoxLayout.X_AXIS));
        cell.add(tab.button);
        CountCapsule capsule = new CountCapsule(tab.count);
        cell.add(capsule);
        cell.add(Box.createHorizontalStrut(2));
        tabs.add(cell);
        body.add(content, key);
        if (selected == null) {
            select(tab);
        }
        return tab;
    }

    /** The controls at the right end of the tab row: a chip, column settings, maximise. */
    public void setTools(JComponent... components) {
        tools.removeAll();
        for (int i = 0; i < components.length; i++) {
            if (i > 0) {
                tools.add(Box.createHorizontalStrut(6));
            }
            components[i].setAlignmentY(CENTER_ALIGNMENT);
            tools.add(components[i]);
        }
        tools.revalidate();
    }

    public void select(Tab tab) {
        selected = tab;
        tab.button.setSelected(true);
        cards.show(body, tab.key);
        for (Tab t : all) {
            t.paintCount();
        }
        for (ChangeListener listener : new ArrayList<>(listeners)) {
            listener.stateChanged(new javax.swing.event.ChangeEvent(this));
        }
    }

    public Tab getSelectedTab() {
        return selected;
    }

    public void addChangeListener(ChangeListener listener) {
        listeners.add(listener);
    }

    /** The count in its capsule: 11 pixel text, 1 by 6 padding, 8 pixel arc. */
    private static final class CountCapsule extends JPanel {
        private final JLabel label;

        CountCapsule(JLabel label) {
            this.label = label;
            setOpaque(false);
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(1, 6, 1, 6));
            add(label, BorderLayout.CENTER);
        }

        @Override
        public boolean isVisible() {
            return label.isVisible();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Object fill = label.getClientProperty("fill"); //$NON-NLS-1$
            if (fill instanceof java.awt.Color) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor((java.awt.Color) fill);
                    g2.fillRoundRect(0, (getHeight() - 16) / 2, getWidth(), 16, 8, 8);
                }
                finally {
                    g2.dispose();
                }
            }
            super.paintComponent(g);
        }
    }

    // ---- the toolbar under the tabs ------------------------------------------------------------

    /**
     * The stylesheet's {@code .toolbar}: 28 pixel buttons with words on them, 6 pixels apart, a
     * hairline under the row, and the filter box at the right.
     */
    public static final class Toolbar extends JPanel {
        public Toolbar() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.border()),
                    new EmptyBorder(8, 10, 8, 10)));
        }

        private void gap() {
            if (getComponentCount() > 0) {
                super.add(Box.createHorizontalStrut(6));
            }
        }

        @Override
        public Component add(Component component) {
            gap();
            if (component instanceof JComponent) {
                ((JComponent) component).setAlignmentY(CENTER_ALIGNMENT);
            }
            return super.add(component);
        }

        /** A button with the action's own text, and the sprite icon named. */
        public JButton button(Action action, String icon, Ui.Variant variant) {
            JButton button = Ui.button(action, Ui.Size.Sm, variant);
            if (icon != null) {
                button.setIcon(Ui.iconSm(icon));
            }
            // The menu convention of an ellipsis on anything that opens a dialog is noise on a
            // toolbar, and the stylesheet has none.
            String text = button.getText();
            if (text != null) {
                button.setText(text.replaceAll("(\\.\\.\\.|\u2026)\\s*$", "")); //$NON-NLS-1$ //$NON-NLS-2$
                action.addPropertyChangeListener(e -> {
                    if (Action.NAME.equals(e.getPropertyName()) && button.getText() != null) {
                        button.setText(button.getText().replaceAll("(\\.\\.\\.|\u2026)\\s*$", "")); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                });
            }
            button.setPreferredSize(null);
            add(button);
            return button;
        }

        public JButton button(Action action, String icon) {
            return button(action, icon, Ui.Variant.Default);
        }

        /**
         * A button whose label is the translation of {@code labelKey} rather than the action's
         * name. The actions were named for menus, in whole sentences - "move the top camera to
         * the placement's location" - and the stylesheet's toolbars say "Camera" and let the
         * icon and the tooltip do the rest. The sentence becomes the tooltip.
         */
        public JButton button(Action action, String icon, String labelKey, Ui.Variant variant) {
            JButton button = button(action, icon, variant);
            String sentence = String.valueOf(action.getValue(Action.NAME));
            button.setText(text(labelKey));
            button.setToolTipText(action.getValue(Action.SHORT_DESCRIPTION) != null
                    ? String.valueOf(action.getValue(Action.SHORT_DESCRIPTION)) : sentence);
            button.setPreferredSize(null);
            return button;
        }

        public JButton button(Action action, String icon, String labelKey) {
            return button(action, icon, labelKey, Ui.Variant.Default);
        }

        /** A ghost button with only the sprite icon, for delete and its like. */
        public JButton iconButton(Action action, String icon) {
            JButton button = Ui.iconButton(action, Ui.Size.Sm, Ui.Variant.Ghost);
            button.setIcon(Ui.iconSm(icon));
            add(button);
            return button;
        }

        /** The 1 by 18 rule between groups. */
        public void separator() {
            gap();
            super.add(Box.createHorizontalStrut(4));
            super.add(Ui.divider(18));
            super.add(Box.createHorizontalStrut(4));
        }

        /** Everything after this is pushed to the right end. */
        public void glue() {
            gap();
            super.add(Box.createHorizontalGlue());
        }

        /**
         * The stylesheet's {@code .filter}: 220 wide, a search icon, placeholder text, and "/" as
         * the key that focuses it.
         */
        public JTextField filter(String placeholder, JComponent focusScope) {
            JTextField field = new JTextField();
            field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
            field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Ui.iconSm("search")); //$NON-NLS-1$
            field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, Ui.kbd("/")); //$NON-NLS-1$
            field.putClientProperty(FlatClientProperties.STYLE,
                    "arc: 6; background: $Pono.surface2; borderColor: $Pono.border; " //$NON-NLS-1$
                            + "focusedBorderColor: $Pono.accent; focusWidth: 0; placeholderForeground: $Pono.textMuted"); //$NON-NLS-1$
            Dimension size = new Dimension(220, 28);
            field.setPreferredSize(size);
            field.setMinimumSize(new Dimension(120, 28));
            field.setMaximumSize(size);
            if (focusScope != null) {
                focusScope.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                        .put(KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, 0), "focusFilter"); //$NON-NLS-1$
                focusScope.getActionMap().put("focusFilter", new AbstractAction() { //$NON-NLS-1$
                    @Override
                    public void actionPerformed(java.awt.event.ActionEvent e) {
                        field.requestFocusInWindow();
                    }
                });
            }
            add(field);
            return field;
        }
    }

    // ---- tables --------------------------------------------------------------------------------

    /**
     * Dress a table as the stylesheet's {@code table.grid}: 32 pixel rows, horizontal rules only,
     * a muted 11.5 pixel header on surface-2, the accent-soft selection with a 3 pixel accent bar
     * at the left of a selected row.
     */
    public static JScrollPane table(JTable table) {
        table.setRowHeight(32);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.putClientProperty(FlatClientProperties.STYLE,
                "selectionBackground: $Pono.accentSoft; selectionForeground: $Label.foreground; " //$NON-NLS-1$
                        + "selectionInactiveBackground: $Pono.accentSoft; selectionInactiveForeground: $Label.foreground; " //$NON-NLS-1$
                        + "gridColor: $Pono.border; cellMargins: 0,10,0,10; " //$NON-NLS-1$
                        + "cellFocusColor: null; background: $Pono.surface"); //$NON-NLS-1$
        table.setFont(Ui.font(12.5f));
        JTableHeader header = table.getTableHeader();
        header.setFont(Ui.font(11.5f).deriveFont(java.util.Map.of(
                java.awt.font.TextAttribute.WEIGHT, java.awt.font.TextAttribute.WEIGHT_SEMIBOLD)));
        header.putClientProperty(FlatClientProperties.STYLE,
                "background: $Pono.surface2; foreground: $Pono.textMuted; height: 30; " //$NON-NLS-1$
                        + "separatorColor: $Pono.surface2; bottomSeparatorColor: $Pono.border; cellMargins: 0,10,0,10"); //$NON-NLS-1$
        table.putClientProperty("Pono.dockTable", Boolean.TRUE); //$NON-NLS-1$
        JScrollPane scroll = new JScrollPane(table) {
            @Override
            public void paint(Graphics g) {
                super.paint(g);
                // The 3 pixel accent bar down the left of every selected row.
                int[] rows = table.getSelectedRows();
                if (rows.length == 0 || !table.isShowing()) {
                    return;
                }
                java.awt.Rectangle view = getViewport().getViewRect();
                java.awt.Point origin = getViewport().getLocation();
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setColor(Ui.accent());
                    for (int row : rows) {
                        java.awt.Rectangle cell = table.getCellRect(row, 0, true);
                        int y = cell.y - view.y + origin.y;
                        if (y + cell.height >= origin.y && y <= origin.y + view.height) {
                            g2.fillRect(origin.x, y, 3, cell.height);
                        }
                    }
                }
                finally {
                    g2.dispose();
                }
            }
        };
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Ui.surface());
        return scroll;
    }

    /** The "no side" label the stylesheet uses in empty muted cells. */
    public static String dash() {
        return "\u2014"; //$NON-NLS-1$
    }

    /** {@code Translations} shortcut for the labels this class needs. */
    static String text(String key) {
        return Translations.getString(key);
    }
}
