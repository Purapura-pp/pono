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
    /** The stylesheet's .glass radius, 10, as the diameter Java2D takes. */
    private static final int ARC = 2 * Tokens.R_MD;

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

    private boolean accentEdge;

    /** The instructions' card: its left edge is the accent rule. */
    public void setAccentEdge(boolean accentEdge) {
        this.accentEdge = accentEdge;
        repaint();
    }

    /** Room around the glass for the stylesheet's --shadow: mostly below, a little at the sides. */
    public static final int SHADOW_SIDE = 3;
    public static final int SHADOW_TOP = 1;
    public static final int SHADOW_BOTTOM = 5;

    /** The content keeps its padding from the glass, the shadow's room outside that. */
    @Override
    public java.awt.Insets getInsets() {
        java.awt.Insets insets = super.getInsets();
        return new java.awt.Insets(insets.top + SHADOW_TOP, insets.left + SHADOW_SIDE,
                insets.bottom + SHADOW_BOTTOM, insets.right + SHADOW_SIDE);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Color background = UIManager.getColor(Tokens.OVERLAY);
        if (background == null) {
            // Not a Pono theme: fall back to the panel colour, which is opaque but readable.
            background = UIManager.getColor("Panel.background"); //$NON-NLS-1$
        }
        if (background != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                int x = SHADOW_SIDE;
                int y = SHADOW_TOP;
                int w = getWidth() - 2 * SHADOW_SIDE;
                int h = getHeight() - SHADOW_TOP - SHADOW_BOTTOM;
                // A soft shadow as a few widening, fading rounded rectangles below the card.
                Color shadow = Ui.color("Pono.shadow", 0x000000, 0x73); //$NON-NLS-1$
                for (int i = SHADOW_BOTTOM; i >= 1; i--) {
                    int alpha = shadow.getAlpha() * (SHADOW_BOTTOM + 1 - i) / (SHADOW_BOTTOM * 4);
                    g2.setColor(new Color(shadow.getRed(), shadow.getGreen(), shadow.getBlue(), alpha));
                    int spread = Math.min(i, SHADOW_SIDE);
                    g2.fillRoundRect(x - spread, y + i - 1, w + 2 * spread, h + 1, ARC + 2 * spread, ARC + 2 * spread);
                }
                g2.setColor(background);
                g2.fillRoundRect(x, y, w, h, ARC, ARC);
                g2.setColor(Ui.border());
                g2.drawRoundRect(x, y, w - 1, h - 1, ARC, ARC);
                if (accentEdge) {
                    // The stylesheet's border-left: 3px accent, which is the card's own edge.
                    java.awt.Shape clip = g2.getClip();
                    g2.clip(new java.awt.geom.RoundRectangle2D.Float(x, y, w, h, ARC, ARC));
                    g2.setColor(Ui.accent());
                    g2.fillRect(x, y, 3, h);
                    g2.setClip(clip);
                }
            }
            finally {
                g2.dispose();
            }
        }
        super.paintComponent(g);
    }
}
