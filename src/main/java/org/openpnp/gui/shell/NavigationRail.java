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

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * The icon rail down the left of the window, and the pages it switches between.
 * <p>
 * It replaces a JTabbedPane of eleven tabs whose titles ran past the width of the window on a
 * laptop, and whose titles were also the only place a count could be shown - Issues and Solutions
 * wrote an HTML dot into its own tab title to do it. An icon above a two-word label takes a
 * fraction of the horizontal space, and a badge belongs to the item rather than to its text.
 * <p>
 * Drawn as the stylesheet's {@code .rail}: 68 pixels wide, 56 by 54 items 4 pixels apart, the
 * selected one on a rounded accent-soft block with its name in the accent at 600 and a 3 by 26
 * bar at the rail's edge, 32 pixel rules between the groups, and settings at the foot. When the
 * window is too short for all of them the items drop their labels, which keeps settings at the
 * foot rather than cut off below it.
 * <p>
 * The pages live in a card layout this owns, so that the selected item and the visible page cannot
 * disagree. Callers address a page by its component, the way they did with the tabbed pane, rather
 * than by a title: a title is translated, which is why {@code showTab("Feeders")} had quietly
 * stopped working in every language but English.
 */
@SuppressWarnings("serial")
public class NavigationRail extends JPanel {
    static final int ITEM_WIDTH = 56;
    static final int ITEM_HEIGHT = 54;
    /** An item without its label, when the window is too short for the full rail. */
    static final int COMPACT_HEIGHT = 40;
    private static final int GAP = 4;
    private static final int PADDING = 10;
    /** A rule between groups: 1 pixel with 6 above and below, on top of the item gap. */
    private static final int RULE = 13;

    /** How loud a badge is: a warning is yellow, anything that needs doing about it red. */
    public enum Badge {
        Warn, Err
    }

    private final JPanel pages = new JPanel(new CardLayout());
    private final ButtonGroup group = new ButtonGroup();
    private final Map<Component, RailButton> buttons = new LinkedHashMap<>();
    private final List<ChangeListener> listeners = new ArrayList<>();
    /** The items and rules from the top, then the ones pinned to the foot. */
    private final List<JComponent> head = new ArrayList<>();
    private final List<JComponent> foot = new ArrayList<>();
    private Component selected;
    private boolean compact;

    public NavigationRail() {
        setLayout(new RailLayout());
        setBackground(Ui.color("Pono.rail.background", 0xffffff)); //$NON-NLS-1$
        setBorder(javax.swing.BorderFactory.createMatteBorder(0, 0, 0, 1, Ui.border()));
        setOpaque(true);
        pages.setOpaque(false);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setBorder(javax.swing.BorderFactory.createMatteBorder(0, 0, 0, 1, Ui.border()));
    }

    /** The pages, in rail order. */
    public List<Component> getPageComponents() {
        return new ArrayList<>(buttons.keySet());
    }

    /** The short label a page is shown under, or null for a component that is not a page. */
    public String getLabel(Component page) {
        RailButton button = buttons.get(page);
        return button == null ? null : button.label;
    }

    /** The card panel holding the pages. Belongs next to the rail in the window's layout. */
    public JPanel getPages() {
        return pages;
    }

    /**
     * @param label The short label under the icon, two words at most.
     * @param toolTip The full name of the page, which no longer fits under an icon.
     */
    public void addPage(String label, String toolTip, Icon icon, Component page) {
        addPage(label, toolTip, icon, page, page);
    }

    /**
     * @param page What callers select the page by.
     * @param view What is shown for it: the page itself, or the page in a card.
     */
    public void addPage(String label, String toolTip, Icon icon, Component page, Component view) {
        RailButton button = new RailButton(label, toolTip, icon, page);
        button.card = String.valueOf(buttons.size());
        buttons.put(page, button);
        group.add(button);
        head.add(button);
        add(button);
        pages.add(view, button.card);
        if (selected == null) {
            setSelectedComponent(page);
        }
    }

    /** The stylesheet's {@code .sep}: a 32 pixel rule between the groups above and below it. */
    public void addGap() {
        JComponent rule = new Rule();
        head.add(rule);
        add(rule);
    }

    /**
     * An item at the foot of the rail that does something instead of showing a page - settings,
     * which is a dialog. It takes no part in the selection, so the page that is showing stays
     * marked as the one the user is on.
     */
    public void addAction(String label, String toolTip, Icon icon, ActionListener action) {
        RailButton button = new RailButton(label, toolTip, icon, null);
        button.addActionListener(e -> {
            button.setSelected(false);
            action.actionPerformed(e);
        });
        foot.add(button);
        add(button);
    }

    public Component getSelectedComponent() {
        return selected;
    }

    public void setSelectedComponent(Component page) {
        RailButton button = buttons.get(page);
        if (button == null || page == selected) {
            return;
        }
        selected = page;
        button.setSelected(true);
        ((CardLayout) pages.getLayout()).show(pages, button.card);
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener listener : new ArrayList<>(listeners)) {
            listener.stateChanged(event);
        }
        repaint();
    }

    public void addChangeListener(ChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * Show a count on a page's item, or clear it with zero. What the count means is the page's
     * business; the rail only reports that there is something to attend to there, in red when
     * it needs doing and in yellow when it is a warning.
     */
    public void setBadge(Component page, int count, Badge badge) {
        RailButton button = buttons.get(page);
        if (button != null && (button.badge != count || button.tone != badge)) {
            button.badge = count;
            button.tone = badge;
            button.repaint();
        }
    }

    /** A red badge. */
    public void setBadge(Component page, int count) {
        setBadge(page, count, Badge.Err);
    }

    /** The count on a page's item, zero for none. */
    public int getBadge(Component page) {
        RailButton button = buttons.get(page);
        return button == null ? 0 : button.badge;
    }

    /** Whether the items are showing without their labels, the window being too short. */
    public boolean isCompact() {
        return compact;
    }

    private void setCompact(boolean compact) {
        if (this.compact == compact) {
            return;
        }
        this.compact = compact;
        for (Component c : getComponents()) {
            if (c instanceof RailButton) {
                ((RailButton) c).showLabel(!compact);
            }
        }
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        RailButton button = selected == null ? null : buttons.get(selected);
        if (button == null || !button.isVisible()) {
            return;
        }
        // The 3 by 26 bar 6 pixels left of the selected item, which is the rail's own edge.
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Ui.accent());
            int h = Math.min(26, button.getHeight() - 8);
            g2.fillRoundRect(button.getX() - 6 - 2, button.getY() + (button.getHeight() - h) / 2, 5, h, 4, 4);
        }
        finally {
            g2.dispose();
        }
    }

    /** The height the items take in full, with their labels. */
    private int fullHeight() {
        int height = 2 * PADDING;
        for (List<JComponent> list : List.of(head, foot)) {
            for (JComponent c : list) {
                height += (c instanceof Rule ? RULE : ITEM_HEIGHT) + GAP;
            }
        }
        return height;
    }

    /**
     * The items from the top, 4 pixels apart; the foot's items from the bottom. Items lose their
     * labels when the rail is shorter than it needs with them.
     */
    private final class RailLayout implements LayoutManager {
        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return new Dimension(Tokens.W_RAIL, fullHeight());
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(Tokens.W_RAIL, 0);
        }

        @Override
        public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            int width = parent.getWidth() - insets.left - insets.right;
            setCompact(parent.getHeight() < fullHeight());
            int itemHeight = compact ? COMPACT_HEIGHT : ITEM_HEIGHT;
            int x = insets.left + (width - ITEM_WIDTH) / 2;
            int y = insets.top + PADDING;
            for (JComponent c : head) {
                int h = c instanceof Rule ? RULE : itemHeight;
                c.setBounds(c instanceof Rule ? insets.left : x, y, c instanceof Rule ? width : ITEM_WIDTH, h);
                y += h + GAP;
            }
            int bottom = parent.getHeight() - insets.bottom - PADDING;
            for (int i = foot.size() - 1; i >= 0; i--) {
                JComponent c = foot.get(i);
                bottom -= itemHeight;
                c.setBounds(x, bottom, ITEM_WIDTH, itemHeight);
                bottom -= GAP;
            }
        }
    }

    /** The stylesheet's {@code .rail .sep}: 32 by 1 in the border colour, centred. */
    private static final class Rule extends JComponent {
        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(Ui.border());
            g.fillRect((getWidth() - 32) / 2, getHeight() / 2, 32, 1);
        }
    }

    /**
     * One item: an icon, a short label, the accent block and bar when it is the page being shown,
     * and a badge when its page has a count to report.
     */
    private final class RailButton extends JToggleButton {
        private final Component page;
        private final String label;
        private String card;
        private int badge;
        private Badge tone = Badge.Err;

        RailButton(String label, String toolTip, Icon icon, Component page) {
            super(label, icon);
            this.page = page;
            this.label = label;
            setToolTipText(toolTip);
            setVerticalTextPosition(SwingConstants.BOTTOM);
            setHorizontalTextPosition(SwingConstants.CENTER);
            setFont(Ui.font(Tokens.FS_TAG));
            setIconTextGap(4);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setMargin(new Insets(0, 0, 0, 0));
            setBorder(null);
            setRolloverEnabled(true);
            getAccessibleContext().setAccessibleName(label);
            addActionListener(e -> {
                if (RailButton.this.page != null) {
                    setSelectedComponent(RailButton.this.page);
                }
            });
        }

        /** Without the label the full name is still the tooltip and the accessible name. */
        void showLabel(boolean show) {
            setText(show ? label : null);
        }

        private boolean showing() {
            return page != null && page == selected;
        }

        @Override
        protected void paintComponent(Graphics g) {
            boolean showing = showing();
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (showing) {
                    g2.setColor(Ui.accentSoft());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 2 * Tokens.R_MD, 2 * Tokens.R_MD);
                }
                else if (getModel().isRollover() || getModel().isPressed()) {
                    g2.setColor(Ui.hover());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 2 * Tokens.R_MD, 2 * Tokens.R_MD);
                }
            }
            finally {
                g2.dispose();
            }
            setForeground(showing ? Ui.accent() : Ui.text2());
            setFont(showing ? Ui.weighted(Tokens.FS_TAG, Tokens.FW_SECTION) : Ui.font(Tokens.FS_TAG));
            super.paintComponent(g);
            if (badge > 0) {
                paintBadge(g);
            }
        }

        /** The stylesheet's {@code .badge}: a 16 pixel capsule 6 from the top, 8 from the right. */
        private void paintBadge(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                String text = badge > 99 ? "99+" : String.valueOf(badge); //$NON-NLS-1$
                g2.setFont(Ui.font(Tokens.FS_MICRO, java.awt.Font.BOLD));
                int textWidth = g2.getFontMetrics().stringWidth(text);
                int w = Math.max(16, textWidth + 8);
                int x = getWidth() - 8 - w;
                int y = compact ? 2 : 6;
                boolean warn = tone == Badge.Warn;
                g2.setColor(warn ? Ui.warn() : Ui.err());
                g2.fillRoundRect(x, y, w, 16, 16, 16);
                g2.setColor(warn ? new Color(0x1a1200) : Color.WHITE);
                g2.drawString(text, x + (w - textWidth) / 2f,
                        y + (16 + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2f);
            }
            finally {
                g2.dispose();
            }
        }
    }
}
