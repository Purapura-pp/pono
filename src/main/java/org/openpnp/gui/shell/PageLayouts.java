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

import java.util.Set;
import java.util.prefs.Preferences;

/**
 * How each page lays out the window: how big the camera is and where the divider under it is,
 * and whether the properties column is showing.
 * <p>
 * One divider for the whole window meant a page that needs the camera and a page that does not
 * shared the same split: the parts page, a list of names, had a camera image over half its
 * height; the job page, after the parts page had been given room, had a camera 250 pixels high.
 * Each page keeps what the user made of it, and starts from what it is for: the pages that work
 * with the machine with the camera large, the library and the logs with it as a strip.
 * <p>
 * Full screen and the maximised tables are passing states and are never stored: a window closed
 * in full screen used to come back with the page area at nothing.
 */
public final class PageLayouts {
    /** The camera's size on a page: the stylesheet's large camera, the 150 pixel strip, or none. */
    public enum Camera {
        Large, Small, Hidden
    }

    /**
     * The properties column on a page: shown while there is something to show, always, or never.
     * Folding or unfolding it by hand makes it always or never on that page.
     */
    public enum Inspector {
        Auto, Show, Hide
    }

    /** The strip's height, and the height of the dock under a large camera. */
    public static final int STRIP = 150;
    public static final int DOCK = 320;
    /** A divider dragged above this hides the camera; up to STRIP_MAX it is the strip. */
    static final int HIDE_BELOW = 60;
    static final int STRIP_MAX = 220;
    /** The properties column: never narrower than this, never more than this share of the window. */
    public static final int INSPECTOR_MIN = 320;
    public static final double INSPECTOR_SHARE = 0.30;

    /** The pages that work with the machine: they start with the camera large. */
    private static final Set<String> LARGE = Set.of("Job", "Feeders", "Vision", "MachineSetup"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

    private final Preferences prefs;

    public PageLayouts(Preferences prefs) {
        this.prefs = prefs;
    }

    /**
     * Pages with no use for the camera at all. The machine settings page's topics are forms; its
     * tree keeps the machine page's layout, the camera large.
     */
    private static final Set<String> NO_CAMERA = Set.of("Settings", "MachineSettings"); //$NON-NLS-1$ //$NON-NLS-2$

    /** What a page starts with before the user has changed anything. */
    public static Camera defaultCamera(String page) {
        return LARGE.contains(page) ? Camera.Large : NO_CAMERA.contains(page) ? Camera.Hidden : Camera.Small;
    }

    public Camera camera(String page) {
        try {
            return Camera.valueOf(prefs.get(key(page, "camera"), defaultCamera(page).name())); //$NON-NLS-1$
        }
        catch (IllegalArgumentException e) {
            return defaultCamera(page);
        }
    }

    public void setCamera(String page, Camera camera) {
        prefs.put(key(page, "camera"), camera.name()); //$NON-NLS-1$
    }

    /** Where a large camera's divider was left on a page, or -1 for the default. */
    public int divider(String page) {
        return prefs.getInt(key(page, "divider"), -1); //$NON-NLS-1$
    }

    /**
     * Where the divider goes for a camera mode, in a split of the given height: the page's own
     * position for a large camera, or the dock's 320 pixels under it by default; the strip; none.
     */
    public int dividerFor(String page, Camera camera, int height) {
        switch (camera) {
            case Hidden:
                return 0;
            case Small:
                return Math.min(STRIP, Math.max(0, height / 2));
            case Large:
            default:
                int stored = divider(page);
                int fallback = Math.max(STRIP_MAX + 1, height - DOCK);
                int location = stored > 0 ? stored : fallback;
                // Never so low that the page under it is gone.
                return Math.max(STRIP_MAX + 1, Math.min(location, height - 120));
        }
    }

    /**
     * The user moved the divider on a page: what that makes the camera, remembered with the
     * position for a large one.
     */
    public Camera dragged(String page, int location) {
        Camera camera = location < HIDE_BELOW ? Camera.Hidden
                : location <= STRIP_MAX ? Camera.Small : Camera.Large;
        setCamera(page, camera);
        if (camera == Camera.Large) {
            prefs.putInt(key(page, "divider"), location); //$NON-NLS-1$
        }
        return camera;
    }

    public Inspector inspector(String page) {
        try {
            return Inspector.valueOf(prefs.get(key(page, "inspector"), Inspector.Auto.name())); //$NON-NLS-1$
        }
        catch (IllegalArgumentException e) {
            return Inspector.Auto;
        }
    }

    public void setInspector(String page, Inspector inspector) {
        prefs.put(key(page, "inspector"), inspector.name()); //$NON-NLS-1$
    }

    /** Pages with nothing of their own to show in the column. */
    private static final Set<String> NO_PROPERTIES = Set.of("Log", "Settings", "MachineSettings"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    /**
     * Whether the column shows on a page, given whether there is anything in it. Left to itself
     * it shows on every page that puts things in it, whether or not something is selected at the
     * moment - a column that came and went with the selection would move the page under the
     * pointer - and not on the pages that never do, unless something is there.
     */
    public boolean inspectorShown(String page, boolean hasContent) {
        switch (inspector(page)) {
            case Show:
                return true;
            case Hide:
                return false;
            case Auto:
            default:
                return hasContent || !NO_PROPERTIES.contains(page);
        }
    }

    /** The column's width in a window of the given width: at least 320, at most 30 % of it. */
    public static int inspectorWidth(int wanted, int windowWidth) {
        int max = Math.max(INSPECTOR_MIN, (int) Math.round(windowWidth * INSPECTOR_SHARE));
        return Math.max(INSPECTOR_MIN, Math.min(wanted, max));
    }

    private static String key(String page, String what) {
        return "PageLayouts." + page + "." + what; //$NON-NLS-1$ //$NON-NLS-2$
    }
}
