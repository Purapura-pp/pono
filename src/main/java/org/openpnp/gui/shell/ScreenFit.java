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

import java.awt.AWTEvent;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;

import javax.swing.JDialog;
import javax.swing.JScrollPane;

/**
 * Windows and dialogs that fit on the screen they are on.
 * <p>
 * The window came back where it was last closed, which on a laptop that had been on a second
 * monitor was off every screen, and a dialog was as tall as its content, which on a 768 pixel
 * screen put its buttons under the task bar with no way to reach them.
 */
public final class ScreenFit {
    private ScreenFit() {
    }

    private static boolean installed;

    /**
     * Bounds moved and shrunk into the usable area - the screen less its task bar - of the screen
     * they mostly lie on, or of the first screen when they lie on none.
     */
    public static Rectangle clamp(Rectangle bounds, Dimension minimum) {
        if (GraphicsEnvironment.isHeadless()) {
            return bounds;
        }
        Rectangle usable = usableArea(bounds);
        if (usable == null) {
            return bounds;
        }
        int w = Math.max(Math.min(bounds.width, usable.width), Math.min(minimum.width, usable.width));
        int h = Math.max(Math.min(bounds.height, usable.height), Math.min(minimum.height, usable.height));
        int x = Math.max(usable.x, Math.min(bounds.x, usable.x + usable.width - w));
        int y = Math.max(usable.y, Math.min(bounds.y, usable.y + usable.height - h));
        return new Rectangle(x, y, w, h);
    }

    /** The usable area of the screen a rectangle mostly lies on. */
    static Rectangle usableArea(Rectangle bounds) {
        GraphicsConfiguration best = null;
        long bestArea = -1;
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            GraphicsConfiguration gc = device.getDefaultConfiguration();
            Rectangle overlap = gc.getBounds().intersection(bounds);
            long area = overlap.isEmpty() ? 0 : (long) overlap.width * overlap.height;
            if (area > bestArea) {
                best = gc;
                bestArea = area;
            }
        }
        if (best == null) {
            return null;
        }
        Rectangle screen = best.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(best);
        return new Rectangle(screen.x + insets.left, screen.y + insets.top,
                screen.width - insets.left - insets.right, screen.height - insets.top - insets.bottom);
    }

    /** From now on, every dialog taller than its screen is cut to it and scrolls. */
    public static synchronized void installForDialogs() {
        if (installed || GraphicsEnvironment.isHeadless()) {
            return;
        }
        installed = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event.getID() == WindowEvent.WINDOW_OPENED && event.getSource() instanceof JDialog) {
                fit((JDialog) event.getSource());
            }
        }, AWTEvent.WINDOW_EVENT_MASK);
    }

    /** A dialog cut to the usable height of its screen, its content scrolling inside it. */
    static void fit(JDialog dialog) {
        Rectangle usable = usableArea(dialog.getBounds());
        if (usable == null) {
            return;
        }
        Rectangle bounds = dialog.getBounds();
        if (bounds.height <= usable.height && bounds.width <= usable.width) {
            if (!usable.contains(bounds)) {
                dialog.setBounds(clamp(bounds, new Dimension(0, 0)));
            }
            return;
        }
        Container content = dialog.getContentPane();
        if (!(content instanceof JScrollPane)) {
            JScrollPane scroll = new JScrollPane(content);
            scroll.setBorder(null);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            dialog.setContentPane(scroll);
        }
        dialog.setBounds(clamp(new Rectangle(bounds.x, bounds.y, Math.min(bounds.width + 16, usable.width),
                Math.min(bounds.height, usable.height)), new Dimension(0, 0)));
        dialog.validate();
    }
}
