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

import java.awt.Color;
import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;
import java.awt.color.ColorSpace;

import javax.swing.UIManager;

/**
 * A colour that is looked up in the current theme every time it is used. Handed to
 * setForeground, to a border or to a painter, it follows a theme switch by itself; a Color read
 * from UIManager once keeps the old theme's value for as long as the component lives, which is
 * how the job name in the top bar came to stay white after a switch to the dark theme.
 * <p>
 * Deliberately not a UIResource: the look and feel's updateUI must leave it in place rather than
 * put its own default back.
 */
@SuppressWarnings("serial")
public final class ThemeColor extends Color {
    private final String key;
    private final int fallback;
    /** The alpha to paint with, 0 to 1, or negative for the theme colour's own. */
    private final double alpha;

    public ThemeColor(String key, int fallbackArgb) {
        this(key, fallbackArgb, -1);
    }

    private ThemeColor(String key, int fallbackArgb, double alpha) {
        super(fallbackArgb, true);
        this.key = key;
        this.fallback = fallbackArgb;
        this.alpha = alpha;
    }

    public String getKey() {
        return key;
    }

    /** The same theme colour at another opacity, still following the theme. */
    public ThemeColor withAlpha(double alpha) {
        return new ThemeColor(key, fallback, alpha);
    }

    /** The colour as the current theme defines it, as a plain Color. */
    public Color resolve() {
        Color color = UIManager.getColor(key);
        int argb = color == null ? fallback : color.getRGB();
        if (alpha >= 0) {
            argb = (argb & 0xffffff) | ((int) Math.round(alpha * 255) << 24);
        }
        return new Color(argb, true);
    }

    @Override
    public int getRGB() {
        Color color = UIManager.getColor(key);
        int argb = color == null ? fallback : color.getRGB();
        if (alpha >= 0) {
            argb = (argb & 0xffffff) | ((int) Math.round(alpha * 255) << 24);
        }
        return argb;
    }

    @Override
    public int getRed() {
        return (getRGB() >> 16) & 0xff;
    }

    @Override
    public int getGreen() {
        return (getRGB() >> 8) & 0xff;
    }

    @Override
    public int getBlue() {
        return getRGB() & 0xff;
    }

    @Override
    public int getAlpha() {
        return (getRGB() >>> 24) & 0xff;
    }

    @Override
    public int getTransparency() {
        int a = getAlpha();
        return a == 0xff ? OPAQUE : a == 0 ? BITMASK : TRANSLUCENT;
    }

    @Override
    public float[] getRGBComponents(float[] components) {
        return resolve().getRGBComponents(components);
    }

    @Override
    public float[] getRGBColorComponents(float[] components) {
        return resolve().getRGBColorComponents(components);
    }

    @Override
    public float[] getComponents(float[] components) {
        return resolve().getComponents(components);
    }

    @Override
    public float[] getColorComponents(float[] components) {
        return resolve().getColorComponents(components);
    }

    @Override
    public float[] getComponents(ColorSpace space, float[] components) {
        return resolve().getComponents(space, components);
    }

    @Override
    public float[] getColorComponents(ColorSpace space, float[] components) {
        return resolve().getColorComponents(space, components);
    }

    @Override
    public ColorSpace getColorSpace() {
        return resolve().getColorSpace();
    }

    @Override
    public Color brighter() {
        return resolve().brighter();
    }

    @Override
    public Color darker() {
        return resolve().darker();
    }

    @Override
    public synchronized PaintContext createContext(ColorModel model, Rectangle deviceBounds,
            Rectangle2D userBounds, AffineTransform transform, RenderingHints hints) {
        return resolve().createContext(model, deviceBounds, userBounds, transform, hints);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ThemeColor && ((ThemeColor) other).key.equals(key)
                && ((ThemeColor) other).alpha == alpha;
    }

    @Override
    public int hashCode() {
        return key.hashCode() * 31 + Double.hashCode(alpha);
    }

    @Override
    public String toString() {
        return "ThemeColor[" + key + (alpha >= 0 ? " @" + alpha : "") + " = #" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + Integer.toHexString(getRGB()) + "]"; //$NON-NLS-1$
    }
}
