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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lays one component out to fill the container and pins the rest to its corners and top edge.
 * <p>
 * This is what puts the machine controls, the readout and the wizard instructions over the camera
 * image rather than in a column beside it. The camera view is the one thing on screen that gets
 * more useful the larger it is, and everything anchored here is small, so the space they used to
 * occupy goes to the image.
 * <p>
 * Each anchored component keeps its preferred size, clamped to the container so that a card can
 * never grow itself out of reach. The order components were added decides nothing; the anchor
 * does.
 */
public class OverlayAnchorLayout implements LayoutManager2 {
    public enum Anchor {
        /** The layer underneath, given the whole container. */
        Fill,
        NorthWest,
        North,
        NorthEast,
        SouthWest,
        SouthEast
    }

    /** Distance from the container's edges, so a card does not touch the image border. */
    private static final int MARGIN = 10;

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
            // The east side is placed first, because the west side is the one that gives way.
            for (Anchor anchor : new Anchor[] { Anchor.Fill, Anchor.NorthEast, Anchor.SouthEast,
                    Anchor.North, Anchor.NorthWest, Anchor.SouthWest }) {
                Component component = componentAt(anchor);
                if (component == null || !component.isVisible()) {
                    continue;
                }
                if (anchor == Anchor.Fill) {
                    component.setBounds(left, top, width, height);
                    continue;
                }
                Dimension size = component.getPreferredSize();
                int w = Math.min(size.width, Math.max(0, width - 2 * MARGIN));
                int h = Math.min(size.height, Math.max(0, height - 2 * MARGIN));
                int x;
                int y;
                switch (anchor) {
                    case North:
                        x = left + (width - w) / 2;
                        y = top + MARGIN;
                        break;
                    case NorthEast:
                        x = left + width - w - MARGIN;
                        y = top + MARGIN;
                        break;
                    case SouthWest:
                        x = left + MARGIN;
                        y = top + height - h - MARGIN;
                        break;
                    case SouthEast:
                        x = left + width - w - MARGIN;
                        y = top + height - h - MARGIN;
                        break;
                    case NorthWest:
                    default:
                        x = left + MARGIN;
                        y = top + MARGIN;
                        break;
                }
                component.setBounds(x, y, w, h);
                stackIfCrowded(component, anchor);
            }
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
        Anchor eastern = anchor == Anchor.SouthWest ? Anchor.SouthEast
                : anchor == Anchor.NorthWest ? Anchor.NorthEast : null;
        if (eastern == null) {
            return;
        }
        Component neighbour = componentAt(eastern);
        if (neighbour == null || !neighbour.isVisible()) {
            return;
        }
        int roomBeside = neighbour.getX() - MARGIN - component.getX();
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
            y = neighbour.getY() - component.getHeight() - MARGIN;
            int floor = MARGIN;
            for (Anchor top : new Anchor[] { Anchor.NorthWest, Anchor.North }) {
                Component above = componentAt(top);
                if (above != null && above.isVisible()) {
                    floor = Math.max(floor, above.getY() + above.getHeight() + MARGIN);
                }
            }
            y = Math.max(y, floor);
        }
        else {
            y = neighbour.getY() + neighbour.getHeight() + MARGIN;
        }
        component.setBounds(component.getX(), y, component.getWidth(), component.getHeight());
    }

    private Component componentAt(Anchor anchor) {
        for (Map.Entry<Component, Anchor> entry : anchors.entrySet()) {
            if (entry.getValue() == anchor) {
                return entry.getKey();
            }
        }
        return null;
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
                    size = entry.getKey().getPreferredSize();
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
