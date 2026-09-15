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
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

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
}
