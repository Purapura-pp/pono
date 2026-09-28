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

package org.openpnp.gui.components;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Where the cameras' pictures go in a rectangle: each as large as it can be, whole, under a title
 * bar of its own, with no black bars round it.
 * <p>
 * Two cameras go side by side unless one over the other gives clearly larger pictures; the
 * "side by side" choice used to put them one over the other whatever the room, which left two
 * small pictures in a wide window. Three or more go in a grid.
 */
public final class CameraArrangement {
    /** The bar over each picture with the camera's name and scale. */
    public static final int CAPTION = 22;
    /** Between two pictures. */
    public static final int GAP = 8;
    /**
     * How much larger the pictures one over the other have to be before they are chosen over side
     * by side: side by side is what the choice says, and a card only a little taller than wide
     * would stack them for a few per cent more picture, and take the page's height to do it.
     */
    public static final double STACK_GAIN = 1.3;

    /** The slots, each a title bar and its picture, and the size they take together. */
    public static final class Result {
        public final List<Rectangle> slots;
        public final Dimension size;
        public final boolean sideBySide;

        Result(List<Rectangle> slots, boolean sideBySide) {
            this.slots = Collections.unmodifiableList(slots);
            this.sideBySide = sideBySide;
            int w = 0, h = 0;
            for (Rectangle r : slots) {
                w = Math.max(w, r.x + r.width);
                h = Math.max(h, r.y + r.height);
            }
            this.size = new Dimension(w, h);
        }

        /** The picture of a slot, under its title bar. */
        public Rectangle picture(int i) {
            Rectangle s = slots.get(i);
            return new Rectangle(s.x, s.y + CAPTION, s.width, s.height - CAPTION);
        }
    }

    private CameraArrangement() {
    }

    /**
     * @param aspects Each picture's width over its height, in the order the cameras are shown.
     */
    public static Result arrange(int width, int height, double[] aspects) {
        List<Rectangle> slots = new ArrayList<>();
        int n = aspects.length;
        if (n == 0 || width <= 0 || height <= CAPTION) {
            return new Result(slots, false);
        }
        if (n == 1) {
            double a = aspects[0];
            int h = down(Math.min(width / a, height - CAPTION));
            slots.add(new Rectangle(0, 0, Math.min(width, down(h * a)), h + CAPTION));
            return new Result(slots, false);
        }
        if (n == 2) {
            double a1 = aspects[0], a2 = aspects[1];
            // Side by side, the pictures share a height; one over the other, a width.
            double sideH = Math.max(0, Math.min((width - GAP) / (a1 + a2), height - CAPTION));
            double stackW = Math.max(0, Math.min(width, (height - 2 * CAPTION - GAP) / (1 / a1 + 1 / a2)));
            double sideArea = sideH * sideH * (a1 + a2);
            double stackArea = stackW * stackW * (1 / a1 + 1 / a2);
            if (sideArea * STACK_GAIN >= stackArea) {
                int h = down(sideH);
                int w1 = down(h * a1);
                slots.add(new Rectangle(0, 0, w1, h + CAPTION));
                slots.add(new Rectangle(w1 + GAP, 0, down(h * a2), h + CAPTION));
                return new Result(slots, true);
            }
            int w = down(stackW);
            int h1 = down(w / a1) + CAPTION;
            slots.add(new Rectangle(0, 0, w, h1));
            slots.add(new Rectangle(0, h1 + GAP, w, down(w / a2) + CAPTION));
            return new Result(slots, false);
        }
        int columns = (int) Math.ceil(Math.sqrt(n));
        int rows = (int) Math.ceil(n / (double) columns);
        double a = aspects[0];
        double w = Math.max(0, Math.min((width - (columns - 1) * GAP) / (double) columns,
                ((height - rows * CAPTION - (rows - 1) * GAP) / (double) rows) * a));
        int cellW = down(w), cellH = down(w / a) + CAPTION;
        for (int i = 0; i < n; i++) {
            slots.add(new Rectangle((i % columns) * (cellW + GAP), (i / columns) * (cellH + GAP), cellW, cellH));
        }
        return new Result(slots, true);
    }

    /** Whole pixels, without losing one to a ratio that is not quite exact in floating point. */
    private static int down(double v) {
        return (int) Math.floor(v + 1e-6);
    }
}
