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

package org.openpnp.gui.components.reticle;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

import org.openpnp.model.LengthUnit;
import org.simpleframework.xml.Root;

/**
 * The crosshair of the mockups' camera scene: the two axes across the whole image, a 42 pixel
 * ring and a 6 pixel one at the centre, and a tick every 60 pixels along the axes with every
 * third one longer, in green at 1.2 pixels. What the camera tools' crosshair switch draws; the
 * right-click menu keeps the plain crosshair.
 */
@Root
public class SceneReticle extends CrosshairReticle {
    private static final int RING = 42;
    private static final int DOT = 6;
    private static final int TICK_STEP = 60;

    public SceneReticle() {
        setColor(new Color(0x3dff8a));
    }

    @Override
    public void draw(Graphics2D g2d, LengthUnit cameraUnitsPerPixelUnits,
            double cameraUnitsPerPixelX, double cameraUnitsPerPixelY, double viewPortCenterX,
            double viewPortCenterY, int viewPortWidth, int viewPortHeight, double rotation) {
        Graphics2D g = (Graphics2D) g2d.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(1.2f));
            g.setColor(color);
            g.translate(viewPortCenterX, viewPortCenterY);
            // AffineTransform rotates positive clockwise, so the value is inverted.
            g.rotate(Math.toRadians(-rotation));
            double reach = Math.hypot(viewPortWidth, viewPortHeight) / 2.0;
            g.setComposite(AlphaComposite.SrcOver.derive(0.9f * 0.55f));
            g.draw(new Line2D.Double(-reach, 0, reach, 0));
            g.draw(new Line2D.Double(0, -reach, 0, reach));
            g.setComposite(AlphaComposite.SrcOver.derive(0.9f));
            g.draw(new Ellipse2D.Double(-RING, -RING, 2 * RING, 2 * RING));
            g.draw(new Ellipse2D.Double(-DOT, -DOT, 2 * DOT, 2 * DOT));
            for (int i = 1; i * TICK_STEP < reach; i++) {
                double d = i * TICK_STEP;
                double t = i % 3 == 0 ? 12 : 6;
                g.draw(new Line2D.Double(d, -t, d, t));
                g.draw(new Line2D.Double(-d, -t, -d, t));
                g.draw(new Line2D.Double(-t, d, t, d));
                g.draw(new Line2D.Double(-t, -d, t, -d));
            }
        }
        finally {
            g.dispose();
        }
    }
}
