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
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;

import org.openpnp.gui.shell.OverlayAnchorLayout.Anchor;

/**
 * The camera image with the controls that belong to it floating on top.
 * <p>
 * The machine controls, the coordinate readout and a wizard's instructions all used to sit in a
 * column beside or below the camera, where they held a fixed share of the window whether or not
 * anyone was looking at them. They are the same controls, moved onto the image: the camera view is
 * the one thing here that is worth more the larger it is.
 * <p>
 * A layered pane rather than a glass pane, because these are ordinary interactive components - the
 * jog buttons have to take clicks, and the instructions have to take a Next.
 * <p>
 * The stage is the stylesheet's {@code .camera}: a card with 14 pixel corners. The camera view
 * repaints itself for every frame, from the camera's thread, and would paint square corners over
 * a clip set here; the corners are therefore a frame on the top layer, which is repainted with
 * every frame because the overlays make the layers overlap, and which takes no mouse events.
 */
@SuppressWarnings("serial")
public class CameraStage extends JLayeredPane {
    private final OverlayAnchorLayout layout = new OverlayAnchorLayout();

    /**
     * @param cameraView The camera panel, which fills the stage and sits under everything else.
     */
    public CameraStage(Component cameraView) {
        setLayout(layout);
        anchor(cameraView, Anchor.Fill, JLayeredPane.DEFAULT_LAYER);
        anchor(new CornerFrame(), Anchor.Fill, JLayeredPane.DRAG_LAYER);
        // A layered pane does not reliably lay its children out again when it is resized: after
        // the window was made smaller the cards kept the corners they had been given for the
        // larger one, which put the machine controls half off the bottom of the screen.
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                doLayout();
                repaint();
            }
        });
    }

    /**
     * Float a component over the image. Wrapped in a card, so that callers hand over the panel
     * they already have rather than knowing how an overlay is painted.
     */
    public OverlayCard overlay(Component content, Anchor anchor) {
        OverlayCard card = new OverlayCard();
        card.setLayout(new BorderLayout(0, 0));
        card.add(content, BorderLayout.CENTER);
        if (content instanceof Container) {
            OverlayCard.makeTransparent((Container) content);
        }
        anchor(card, anchor, JLayeredPane.PALETTE_LAYER);
        return card;
    }

    /**
     * A layered pane takes a layer where a layout manager takes a constraint, so the anchor is
     * handed to the layout itself rather than through the add.
     */
    /** Float a component that paints its own card, or several cards, over the image. */
    public void anchor(Component component, Anchor anchor) {
        anchor(component, anchor, JLayeredPane.PALETTE_LAYER);
    }

    private void anchor(Component component, Anchor anchor, Integer layer) {
        // The layer has to be set before the add, because a layered pane reads it back from the
        // component; the anchor then travels as the layout constraint, where an Integer would
        // otherwise have been taken for the layer.
        setLayer(component, layer);
        add(component, anchor);
    }

    /**
     * The card's corners in the window's colour and its hairline border, over everything else.
     * It is never the target of a click: the view and the cards under it take them.
     */
    private static final class CornerFrame extends JComponent {
        CornerFrame() {
            setOpaque(false);
            setFocusable(false);
        }

        @Override
        public boolean contains(int x, int y) {
            return false;
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            float arc = 2 * Tokens.R_LG;
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Area corners = new Area(new Rectangle2D.Float(0, 0, w, h));
                corners.subtract(new Area(new RoundRectangle2D.Float(0, 0, w, h, arc, arc)));
                Container parent = getParent() == null ? null : getParent().getParent();
                g2.setColor(parent != null && parent.isOpaque() ? parent.getBackground() : Ui.bg());
                g2.fill(corners);
                g2.setColor(Ui.border());
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc));
            }
            finally {
                g2.dispose();
            }
        }
    }
}
