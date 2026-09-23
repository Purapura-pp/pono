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

package org.openpnp.machine.reference.wizards;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.Collections;
import java.util.List;

import javax.swing.JComponent;

import org.openpnp.gui.shell.Ui;
import org.openpnp.machine.reference.ReferenceNozzleTipCalibration.ModelBasedRunoutCompensation;
import org.openpnp.machine.reference.ReferenceNozzleTipCalibration.RunoutCompensation;
import org.openpnp.machine.reference.ReferenceNozzleTipCalibration.TableBasedRunoutCompensation;
import org.openpnp.model.Location;

/**
 * The runout a nozzle tip calibration measured, as the mockup draws it: where the tip was seen at
 * each angle, the circle fitted through them, and the nozzle's axis as a cross. The dashed circle
 * is the scale, the largest distance drawn.
 */
@SuppressWarnings("serial")
final class RunoutChart extends JComponent {
    private static final int SIZE = 150;

    private RunoutCompensation model;

    RunoutChart() {
        setOpaque(false);
    }

    void setModel(RunoutCompensation model) {
        this.model = model;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(SIZE, SIZE);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    static List<Location> points(RunoutCompensation model) {
        if (model instanceof ModelBasedRunoutCompensation) {
            return ((ModelBasedRunoutCompensation) model).getMeasuredLocations();
        }
        if (model instanceof TableBasedRunoutCompensation) {
            return ((TableBasedRunoutCompensation) model).getMeasuredLocations();
        }
        return Collections.emptyList();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            double half = SIZE / 2.0;
            double scaleRadius = half - 12;
            g2.setColor(Ui.borderStrong());
            g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    new float[] { 3f, 4f }, 0f));
            g2.draw(new Ellipse2D.Double(half - scaleRadius, half - scaleRadius, 2 * scaleRadius, 2 * scaleRadius));
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(Ui.muted());
            g2.draw(new Line2D.Double(half - 5, half, half + 5, half));
            g2.draw(new Line2D.Double(half, half - 5, half, half + 5));
            List<Location> points = points(model);
            if (points.isEmpty()) {
                return;
            }
            // The largest distance from the axis, of a point or of the fitted circle, is the scale.
            double extent = 0;
            for (Location p : points) {
                extent = Math.max(extent, Math.hypot(p.getX(), p.getY()));
            }
            ModelBasedRunoutCompensation fitted = model instanceof ModelBasedRunoutCompensation
                    ? (ModelBasedRunoutCompensation) model : null;
            if (fitted != null) {
                Location center = fitted.getAxisOffset();
                extent = Math.max(extent, Math.hypot(center.getX(), center.getY()) + fitted.getRadius());
            }
            double scale = scaleRadius / Math.max(extent, 1e-3);
            if (fitted != null) {
                Location center = fitted.getAxisOffset();
                double r = fitted.getRadius() * scale;
                double cx = half + center.getX() * scale;
                double cy = half - center.getY() * scale;
                g2.setColor(Ui.accent());
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
                g2.setStroke(new BasicStroke(1f));
            }
            g2.setColor(Ui.ok());
            for (Location p : points) {
                double x = half + p.getX() * scale;
                double y = half - p.getY() * scale;
                g2.fill(new Ellipse2D.Double(x - 3.5, y - 3.5, 7, 7));
            }
        }
        finally {
            g2.dispose();
        }
    }
}
