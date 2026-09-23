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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JPanel;

/**
 * A panel with rounded corners, a fill and a hairline border: the stylesheet's cards, pills,
 * search box and input fields all start here.
 * <p>
 * The radius is the stylesheet's border-radius. Java2D's round rectangles take the corner's
 * diameter, which is why every card used to come out with half the rounding of the mockups.
 * Children are clipped to the corners, as {@code overflow: hidden} does, and the outline is drawn
 * over them, so an opaque table in a card no longer paints square corners across its border.
 * <p>
 * The fill and border are looked up again on every paint through the suppliers handed in, so a
 * theme change needs no bookkeeping.
 */
@SuppressWarnings("serial")
public class RoundedPanel extends JPanel {
    private final int radius;
    private final java.util.function.Supplier<Color> fill;
    private final java.util.function.Supplier<Color> line;

    public RoundedPanel(int radius, Color fill, Color line) {
        this(radius, () -> fill, () -> line);
    }

    public RoundedPanel(int radius, java.util.function.Supplier<Color> fill,
            java.util.function.Supplier<Color> line) {
        this.radius = radius;
        this.fill = fill;
        this.line = line;
        setOpaque(false);
    }

    /** The stylesheet's {@code .card}: 14 pixel corners, the surface colour, a border. */
    public static RoundedPanel card() {
        return new RoundedPanel(Tokens.R_LG, Ui::surface, Ui::border);
    }

    public int getRadius() {
        return radius;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color background = fill.get();
            if (background != null) {
                g2.setColor(background);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 2 * radius, 2 * radius);
            }
        }
        finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }

    @Override
    protected void paintChildren(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            if (radius > 0) {
                g2.clip(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth(), getHeight(),
                        2 * radius, 2 * radius));
            }
            super.paintChildren(g2);
        }
        finally {
            g2.dispose();
        }
        Color border = line.get();
        if (border != null) {
            Graphics2D g3 = (Graphics2D) g.create();
            try {
                g3.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g3.setColor(border);
                g3.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 2 * radius, 2 * radius);
            }
            finally {
                g3.dispose();
            }
        }
    }
}
