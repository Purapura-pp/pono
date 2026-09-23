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

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager2;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lays what is anchored to fill out over the whole container and pins the rest to its corners
 * and top edge.
 * <p>
 * This is what puts the machine controls, the readout and the wizard instructions over the camera
 * image rather than in a column beside it. The camera view is the one thing on screen that gets
 * more useful the larger it is, and everything anchored here is small, so the space they used to
 * occupy goes to the image.
 * <p>
 * The stylesheet's {@code .ov}: 12 pixels in from the edges, and the cards sharing a corner in a
 * row 6 pixels apart, in the order they were added. The instructions go under the lower of the
 * two rows along the top. Each card keeps its preferred size, clamped to the container so that a
 * card can never grow itself out of reach.
 */
public class OverlayAnchorLayout implements LayoutManager2 {
    public enum Anchor {
        /** The layers underneath, each given the whole container. */
        Fill,
        NorthWest,
        North,
        NorthEast,
        SouthWest,
        /** The middle of the foot, as the strip's drag handle. */
        South,
        SouthEast
    }

    /** Distance from the container's edges, so a card does not touch the image border. */
    static final int MARGIN = Tokens.PAD_OVERLAY;
    /** Between the cards sharing a corner. */
    static final int GAP = 6;

    private final Map<Component, Anchor> anchors = new LinkedHashMap<>();

    @Override
    public void addLayoutComponent(Component component, Object constraint) {
        if (constraint instanceof Anchor) {
            anchors.put(component, (Anchor) constraint);
        }
        // Anything added without an anchor is left where it is rather than refused. A layered
        // pane passes a null constraint through on a plain add(), and a window that will not open
        // is a far worse answer to that than a component in the wrong corner.
    }

    @Override
    public void addLayoutComponent(String name, Component component) {
        throw new UnsupportedOperationException("Use an Anchor rather than a name.");
    }

    @Override
    public void removeLayoutComponent(Component component) {
        anchors.remove(component);
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            int left = insets.left;
            int top = insets.top;
            int width = parent.getWidth() - insets.left - insets.right;
            int height = parent.getHeight() - insets.top - insets.bottom;
            for (Component component : at(Anchor.Fill)) {
                component.setBounds(left, top, width, height);
            }
            // The east side is placed first, because the west side is the one that gives way.
            for (Anchor anchor : new Anchor[] { Anchor.NorthEast, Anchor.SouthEast, Anchor.NorthWest,
                    Anchor.SouthWest }) {
                row(anchor, left, top, width, height);
            }
            for (Anchor anchor : new Anchor[] { Anchor.NorthWest, Anchor.SouthWest }) {
                List<Component> row = at(anchor);
                if (row.size() == 1) {
                    stackIfCrowded(row.get(0), anchor);
                }
            }
            // The instructions go under the camera tools rather than over them: the banner at the
            // top edge covered the camera selector and the reticle switches, and they covered the
            // step's title.
            int below = top + MARGIN;
            for (Anchor anchor : new Anchor[] { Anchor.NorthWest, Anchor.NorthEast }) {
                Rectangle bounds = bounds(anchor);
                if (bounds != null) {
                    below = Math.max(below, bounds.y + bounds.height + MARGIN);
                }
            }
            for (Component component : at(Anchor.North)) {
                Dimension size = component.getPreferredSize();
                int w = Math.min(size.width, Math.max(0, width - 2 * MARGIN));
                int h = Math.min(size.height, Math.max(0, top + height - MARGIN - below));
                component.setBounds(left + (width - w) / 2, below, w, h);
                below += h + GAP;
            }
            for (Component component : at(Anchor.South)) {
                Dimension size = component.getPreferredSize();
                int w = Math.min(size.width, Math.max(0, width - 2 * MARGIN));
                int h = Math.min(size.height, Math.max(0, height - 2 * MARGIN));
                int x = left + (width - w) / 2;
                // Beside the readout in the corner rather than over it, when the middle is taken.
                Rectangle west = bounds(Anchor.SouthWest);
                if (west != null && x < west.x + west.width + MARGIN) {
                    x = Math.min(west.x + west.width + MARGIN, left + width - w - MARGIN);
                }
                component.setBounds(x, top + height - h - MARGIN, w, h);
            }
        }
    }

    /** The visible cards at one corner, side by side, the row flush with that corner. */
    private void row(Anchor anchor, int left, int top, int width, int height) {
        List<Component> row = at(anchor);
        if (row.isEmpty()) {
            return;
        }
        int total = -GAP;
        for (Component component : row) {
            total += Math.min(component.getPreferredSize().width, Math.max(0, width - 2 * MARGIN)) + GAP;
        }
        boolean east = anchor == Anchor.NorthEast || anchor == Anchor.SouthEast;
        boolean south = anchor == Anchor.SouthWest || anchor == Anchor.SouthEast;
        int x = east ? left + width - MARGIN - total : left + MARGIN;
        x = Math.max(left + MARGIN, x);
        for (Component component : row) {
            Dimension size = component.getPreferredSize();
            int w = Math.min(size.width, Math.max(0, width - 2 * MARGIN));
            int h = Math.min(size.height, Math.max(0, height - 2 * MARGIN));
            int y = south ? top + height - h - MARGIN : top + MARGIN;
            component.setBounds(x, y, w, h);
            x += w + GAP;
        }
    }

    /**
     * Make room between a western card and its eastern neighbour when the two would not fit side
     * by side.
     * <p>
     * On a narrow window the readout and the machine controls want more width between them than
     * the image has. Neither can simply be clipped - one is a coordinate and the other is a row of
     * buttons - and the east side is the one that cannot shrink. So the west side gives way: it is
     * narrowed to the room beside its neighbour if its minimum size allows, and lifted above the
     * neighbour if not. Lifting stops short of whatever sits at the top of the image, because a
     * readout on top of the camera selector was the failure this replaces. As the window widens
     * they return to the corners they were asked for.
     */
    private void stackIfCrowded(Component component, Anchor anchor) {
        Anchor eastern = anchor == Anchor.SouthWest ? Anchor.SouthEast : Anchor.NorthEast;
        Rectangle neighbour = bounds(eastern);
        if (neighbour == null) {
            return;
        }
        int roomBeside = neighbour.x - MARGIN - component.getX();
        if (component.getWidth() <= roomBeside) {
            return;
        }
        if (roomBeside >= component.getMinimumSize().width) {
            component.setBounds(component.getX(), component.getY(), roomBeside,
                    component.getHeight());
            return;
        }
        int y;
        if (anchor == Anchor.SouthWest) {
            y = neighbour.y - component.getHeight() - MARGIN;
            int floor = MARGIN;
            for (Anchor top : new Anchor[] { Anchor.NorthWest, Anchor.North }) {
                Rectangle above = bounds(top);
                if (above != null) {
                    floor = Math.max(floor, above.y + above.height + MARGIN);
                }
            }
            y = Math.max(y, floor);
        }
        else {
            y = neighbour.y + neighbour.height + MARGIN;
        }
        component.setBounds(component.getX(), y, component.getWidth(), component.getHeight());
    }

    /** The visible components at an anchor, in the order they were added. */
    private List<Component> at(Anchor anchor) {
        List<Component> found = new ArrayList<>();
        for (Map.Entry<Component, Anchor> entry : anchors.entrySet()) {
            if (entry.getValue() == anchor && entry.getKey().isVisible()) {
                found.add(entry.getKey());
            }
        }
        return found;
    }

    /** What the visible components at an anchor cover together, or null for nothing. */
    private Rectangle bounds(Anchor anchor) {
        Rectangle union = null;
        for (Component component : at(anchor)) {
            union = union == null ? component.getBounds() : union.union(component.getBounds());
        }
        return union;
    }

    /**
     * The filling component's preferred size. The anchored ones are deliberately left out: they
     * float over the image, so asking the container to be large enough for all of them would let
     * the machine controls dictate how much room the camera gets, which is the arrangement this
     * replaces.
     */
    @Override
    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            Insets insets = parent.getInsets();
            Dimension size = new Dimension(0, 0);
            for (Map.Entry<Component, Anchor> entry : anchors.entrySet()) {
                if (entry.getValue() == Anchor.Fill) {
                    Dimension preferred = entry.getKey().getPreferredSize();
                    size = new Dimension(Math.max(size.width, preferred.width),
                            Math.max(size.height, preferred.height));
                }
            }
            return new Dimension(size.width + insets.left + insets.right,
                    size.height + insets.top + insets.bottom);
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        Insets insets = parent.getInsets();
        return new Dimension(insets.left + insets.right, insets.top + insets.bottom);
    }

    @Override
    public Dimension maximumLayoutSize(Container parent) {
        return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public float getLayoutAlignmentX(Container parent) {
        return 0.5f;
    }

    @Override
    public float getLayoutAlignmentY(Container parent) {
        return 0.5f;
    }

    @Override
    public void invalidateLayout(Container parent) {
    }
}
