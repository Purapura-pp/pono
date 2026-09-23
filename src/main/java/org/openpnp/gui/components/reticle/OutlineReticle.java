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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.util.function.Supplier;

import org.openpnp.model.Footprint;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;

/**
 * The package outline of whatever is selected, drawn at scale on the image as the mockups' scene
 * draws it: dashed in yellow, the body and the pads, with the placement and the package named
 * beside it. The footprint is asked for on every paint, so the outline follows the selection
 * without anyone telling it.
 */
public class OutlineReticle implements Reticle {
    private static final Color COLOUR = new Color(0xffd34d);

    private final Supplier<Footprint> footprint;
    private final Supplier<String> label;

    public OutlineReticle(Supplier<Footprint> footprint, Supplier<String> label) {
        this.footprint = footprint;
        this.label = label;
    }

    @Override
    public void draw(Graphics2D g2d, LengthUnit cameraUnitsPerPixelUnits,
            double cameraUnitsPerPixelX, double cameraUnitsPerPixelY, double viewPortCenterX,
            double viewPortCenterY, int viewPortWidth, int viewPortHeight, double rotation) {
        Footprint current = footprint.get();
        if (current == null || cameraUnitsPerPixelX == 0 || cameraUnitsPerPixelY == 0) {
            return;
        }
        Area shape = new Area();
        for (Shape part : new Shape[] { current.getBodyShape(), current.getPadsShape() }) {
            if (part != null) {
                shape.add(new Area(part));
            }
        }
        if (shape.isEmpty()) {
            return;
        }
        double unitScale = new Length(1, current.getUnits()).convertToUnits(cameraUnitsPerPixelUnits).getValue();
        AffineTransform tx = new AffineTransform();
        tx.translate(viewPortCenterX, viewPortCenterY);
        // Left-hand coordinate system rotates positive clockwise, so the value is inverted.
        tx.rotate(Math.toRadians(-rotation));
        // Left-hand coordinate system, Y inverted.
        tx.scale(unitScale / cameraUnitsPerPixelX, -unitScale / cameraUnitsPerPixelY);
        Graphics2D g = (Graphics2D) g2d.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(COLOUR);
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    new float[] { 6f, 4f }, 0f));
            Shape drawn = tx.createTransformedShape(current.getBodyShape() == null ? shape : current.getBodyShape());
            g.draw(drawn);
            if (current.getPadsShape() != null) {
                g.draw(tx.createTransformedShape(current.getPadsShape()));
            }
            String text = label.get();
            if (text != null && !text.isEmpty()) {
                java.awt.Rectangle box = tx.createTransformedShape(shape).getBounds();
                g.setFont(org.openpnp.gui.shell.Ui.weighted(14f, 600));
                g.drawString(text, box.x + box.width + 8, box.y - 6);
            }
        }
        finally {
            g.dispose();
        }
    }
}
