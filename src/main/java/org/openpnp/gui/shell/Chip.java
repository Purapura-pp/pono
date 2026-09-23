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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;

/**
 * A word in a coloured capsule: the stylesheet's {@code .chip} (26 pixels, fully round, an
 * optional LED) and {@code .status} (20 pixels, 6 pixel arc, a dot before the text).
 * <p>
 * These carry state - machine enabled, placement placed, feeder empty - and the colour is the
 * message: green is fine, amber wants a look, red wants a hand. The text says the same thing for
 * anyone who cannot tell the colours apart.
 */
@SuppressWarnings("serial")
public class Chip extends JLabel {
    public enum Tone {
        Ok, Warn, Err, Run, Accent, Neutral, Pending, Skip
    }

    public enum Shape {
        /** 26 pixels, fully round, 12 pixel semibold text, a 1 pixel border at 35%. */
        Chip,
        /** 20 pixels, 6 pixel arc, 11 pixel semibold text, a 6 pixel dot. */
        Status
    }

    private Tone tone;
    private final Shape shape;
    private boolean led;

    public Chip(String text, Tone tone, Shape shape) {
        super(text);
        this.tone = tone;
        this.shape = shape;
        this.led = shape == Shape.Status;
        setOpaque(false);
        applyShape();
    }

    /** Whether the little light before the text is drawn; always on for a status. */
    public Chip withLed(boolean led) {
        this.led = led;
        applyShape();
        return this;
    }

    public void setTone(Tone tone) {
        this.tone = tone;
        setForeground(foregroundFor(tone));
        repaint();
    }

    public Tone getTone() {
        return tone;
    }

    private void applyShape() {
        int dot = led ? (shape == Shape.Chip ? 8 : 6) + 6 : 0;
        if (shape == Shape.Chip) {
            setFont(Ui.font(12f, Font.BOLD));
            setBorder(new EmptyBorder(0, 10 + dot, 0, 10));
        }
        else {
            setFont(Ui.font(11f, Font.BOLD));
            setBorder(new EmptyBorder(0, 8 + dot, 0, 8));
        }
        setForeground(foregroundFor(tone));
    }

    private static Color foregroundFor(Tone tone) {
        switch (tone) {
            case Ok: return Ui.ok();
            case Warn: return Ui.warn();
            case Err: return Ui.err();
            case Run: return Ui.accent();
            case Accent: return Ui.accent();
            case Pending: return Ui.text2();
            case Skip: return Ui.muted();
            case Neutral:
            default: return Ui.text2();
        }
    }

    private Color backgroundFor(Tone tone) {
        switch (tone) {
            case Ok: return Ui.okSoft();
            case Warn: return Ui.warnSoft();
            case Err: return Ui.errSoft();
            case Run: return Ui.accentSoft();
            case Accent: return Ui.accentSoft();
            case Skip: return null;
            case Pending:
            case Neutral:
            default: return Ui.surface3();
        }
    }

    private Color borderFor(Tone tone) {
        if (shape == Shape.Status) {
            return tone == Tone.Skip ? Ui.borderStrong() : null;
        }
        switch (tone) {
            case Ok: return Ui.alpha(Ui.ok(), 0.35);
            case Warn: return Ui.alpha(Ui.warn(), 0.35);
            case Err: return Ui.alpha(Ui.err(), 0.35);
            case Run:
            case Accent: return Ui.alpha(Ui.accent(), 0.35);
            case Neutral:
            default: return Ui.border();
        }
    }

    private int height;

    /** A height other than the shape's own, as the status bar's 18 pixel status. */
    public Chip withHeight(int height) {
        this.height = height;
        revalidate();
        return this;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.height = height > 0 ? height : shape == Shape.Chip ? 26 : 20;
        return size;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            // A chip is a capsule; a status tag has the stylesheet's 6 pixel radius.
            int arc = shape == Shape.Chip ? h : 2 * 6;
            Color background = backgroundFor(tone);
            if (background != null) {
                g2.setColor(background);
                g2.fillRoundRect(0, 0, w, h, arc, arc);
            }
            Color border = borderFor(tone);
            if (border != null) {
                g2.setColor(border);
                if (tone == Tone.Skip) {
                    g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                            1f, new float[] { 3f, 3f }, 0f));
                }
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);
            }
            if (led) {
                int d = shape == Shape.Chip ? 8 : 6;
                int x = shape == Shape.Chip ? 10 : 8;
                int y = (h - d) / 2;
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(getForeground());
                if (shape == Shape.Chip && tone == Tone.Ok) {
                    // The stylesheet's glow.
                    g2.setColor(Ui.alpha(getForeground(), 0.35));
                    g2.fillOval(x - 3, y - 3, d + 6, d + 6);
                    g2.setColor(getForeground());
                }
                g2.fillOval(x, y, d, d);
            }
        }
        finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
