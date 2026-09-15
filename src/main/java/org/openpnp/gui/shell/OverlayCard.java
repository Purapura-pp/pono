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

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;

/**
 * A rounded, semi-transparent panel for the things that float over the camera image.
 * <p>
 * The transparency is the point: a card sitting over the image has to let the operator see what is
 * underneath it, because what is underneath is the part being picked. The colour comes from the
 * theme rather than from a constant here, so both Pono themes place their own value and a user on
 * another look and feel still gets a readable card.
 */
@SuppressWarnings("serial")
public class OverlayCard extends JPanel {
    /** The stylesheet's {@code .glass}: a 10 pixel arc. */
    private static final int ARC = 10;

    public OverlayCard() {
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
    }

    /**
     * A glass strip holding a row of pills: the stylesheet's {@code .glass.pills}, 3 pixel
     * padding and 2 pixel gaps.
     */
    public static OverlayCard strip() {
        OverlayCard strip = new OverlayCard();
        strip.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        strip.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 2, 0));
        return strip;
    }

    /**
     * Stop the plain containers and labels inside a hosted panel from painting their own
     * background over the card's. Buttons, combo boxes, sliders and anything with its own scroll
     * or tab chrome are left alone: those are meant to read as controls, and the mockups draw
     * them that way.
     */
    public static void makeTransparent(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JPanel || child instanceof JLabel) {
                ((JComponent) child).setOpaque(false);
            }
            if (child instanceof Container
                    && !(child instanceof JScrollPane)
                    && !(child instanceof JTabbedPane)) {
                makeTransparent((Container) child);
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Color background = UIManager.getColor("Pono.overlayBackground"); //$NON-NLS-1$
        if (background == null) {
            // Not a Pono theme: fall back to the panel colour, which is opaque but readable.
            background = UIManager.getColor("Panel.background"); //$NON-NLS-1$
        }
        if (background != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(background);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
                Color border = UIManager.getColor("Pono.border"); //$NON-NLS-1$
                if (border != null) {
                    g2.setColor(border);
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
                }
            }
            finally {
                g2.dispose();
            }
        }
        super.paintComponent(g);
    }
}
