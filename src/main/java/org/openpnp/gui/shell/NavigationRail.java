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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
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
 * The pages live in a card layout this owns, so that the selected item and the visible page cannot
 * disagree. Callers address a page by its component, the way they did with the tabbed pane, rather
 * than by a title: a title is translated, which is why {@code showTab("Feeders")} had quietly
 * stopped working in every language but English.
 */
@SuppressWarnings("serial")
public class NavigationRail extends JPanel {
    /** From the mockup: wide enough for two Chinese characters under a 24px icon. */
    private static final Dimension ITEM_SIZE = new Dimension(56, 54);
    /** The accent stripe that marks the selected item. */
    private static final int INDICATOR_WIDTH = 3;
    private static final int BADGE_DIAMETER = 15;

    private final JPanel pages = new JPanel(new CardLayout());
    private final ButtonGroup group = new ButtonGroup();
    private final Map<Component, RailButton> buttons = new LinkedHashMap<>();
    private final List<ChangeListener> listeners = new ArrayList<>();
    private final Box top = Box.createVerticalBox();
    private final Box bottom = Box.createVerticalBox();
    private Component selected;

    public NavigationRail() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(color("Pono.rail.background", getBackground())); //$NON-NLS-1$
        setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        add(top);
        add(Box.createVerticalGlue());
        add(bottom);
    }

    /** The card panel holding the pages. Belongs next to the rail in the window's layout. */
    public JPanel getPages() {
        return pages;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        StringBuilder debug = new StringBuilder("RAIL size=" + getSize() + " pref=" + getPreferredSize());
        for (Component child : getComponents()) {
            debug.append("\n  ").append(child.getClass().getSimpleName()).append(" bounds=")
                    .append(child.getBounds()).append(" pref=").append(child.getPreferredSize());
            if (child instanceof java.awt.Container) {
                for (Component grand : ((java.awt.Container) child).getComponents()) {
                    debug.append("\n     ").append(grand.getClass().getSimpleName())
                            .append(" bounds=").append(grand.getBounds());
                }
            }
        }
        org.pmw.tinylog.Logger.info(debug.toString());
    }

    /**
     * @param label The short label under the icon, two words at most.
     * @param toolTip The full name of the page, which no longer fits under an icon.
     */
    public void addPage(String label, String toolTip, Icon icon, Component page) {
        RailButton button = new RailButton(label, toolTip, icon, page);
        button.card = String.valueOf(buttons.size());
        buttons.put(page, button);
        group.add(button);
        top.add(button);
        pages.add(page, button.card);
        if (selected == null) {
            setSelectedComponent(page);
        }
    }

    /** A gap that groups the items above it, as the mockup groups job, library and machine. */
    public void addGap() {
        top.add(Box.createVerticalStrut(10));
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
        bottom.add(button);
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
     * business; the rail only reports that there is something to attend to there.
     */
    public void setBadge(Component page, int count) {
        RailButton button = buttons.get(page);
        if (button != null && button.badge != count) {
            button.badge = count;
            button.repaint();
        }
    }

    private static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }

    /**
     * One item: an icon, a short label, an accent stripe when it is the page being shown, and a
     * badge when its page has a count to report.
     */
    private final class RailButton extends JToggleButton {
        private final Component page;
        private String card;
        private int badge;

        RailButton(String label, String toolTip, Icon icon, Component page) {
            super(label, icon);
            this.page = page;
            setToolTipText(toolTip);
            setVerticalTextPosition(SwingConstants.BOTTOM);
            setHorizontalTextPosition(SwingConstants.CENTER);
            setFont(getFont().deriveFont(getFont().getSize2D() - 1f).deriveFont(Font.PLAIN));
            setIconTextGap(2);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setMinimumSize(ITEM_SIZE);
            setPreferredSize(ITEM_SIZE);
            setMaximumSize(ITEM_SIZE);
            setAlignmentX(CENTER_ALIGNMENT);
            addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (RailButton.this.page != null) {
                        setSelectedComponent(RailButton.this.page);
                    }
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            boolean showing = page != null && page == selected;
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (showing) {
                    g2.setColor(color("Pono.rail.selectedBackground", getBackground())); //$NON-NLS-1$
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    g2.setColor(color("Pono.rail.selectedIndicator", //$NON-NLS-1$
                            UIManager.getColor("Component.focusColor"))); //$NON-NLS-1$
                    g2.fillRect(0, 0, INDICATOR_WIDTH, getHeight());
                }
                else if (getModel().isRollover()) {
                    g2.setColor(color("Pono.rail.hoverBackground", getBackground())); //$NON-NLS-1$
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
            }
            finally {
                g2.dispose();
            }
            setForeground(showing
                    ? color("Pono.rail.selectedIndicator", UIManager.getColor("Component.focusColor")) //$NON-NLS-1$ //$NON-NLS-2$
                    : color("Pono.textSecondary", UIManager.getColor("Label.foreground"))); //$NON-NLS-1$ //$NON-NLS-2$
            super.paintComponent(g);
            if (badge > 0) {
                paintBadge(g);
            }
        }

        /** On the icon's upper right, where a count does not push the label around. */
        private void paintBadge(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int x = getWidth() / 2 + 6;
                int y = 4;
                g2.setColor(color("Pono.statusErr", Color.RED)); //$NON-NLS-1$
                g2.fillOval(x, y, BADGE_DIAMETER, BADGE_DIAMETER);
                String text = badge > 99 ? "99+" : String.valueOf(badge); //$NON-NLS-1$
                g2.setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() - 2f));
                g2.setColor(Color.WHITE);
                int textWidth = g2.getFontMetrics().stringWidth(text);
                g2.drawString(text, x + (BADGE_DIAMETER - textWidth) / 2,
                        y + BADGE_DIAMETER - g2.getFontMetrics().getDescent() - 2);
            }
            finally {
                g2.dispose();
            }
        }
    }
}
