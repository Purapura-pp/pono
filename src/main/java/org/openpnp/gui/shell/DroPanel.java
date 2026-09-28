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

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Locale;

import javax.swing.JPanel;

import org.openpnp.model.Configuration;
import org.openpnp.model.Location;

/**
 * The four axis readouts, each an axis letter, a monospaced value and its unit on one baseline.
 * <p>
 * It sits in the camera card's tool row, and where that row is too narrow it drops the units,
 * then goes a size smaller; in the camera strip and at the top of the machine controls it is two
 * rows of two. It used to float over the bottom left of the image, where it covered the picture.
 * <p>
 * This replaces a single label holding {@code "X:1.000 Y:2.000 Z:0.000 C:0.000"} with a bevel
 * border and a hardcoded black on pale green, which was unreadable on a dark theme and told the
 * user that a mark was set by turning the whole strip blue. Marking now tints the numbers with the
 * accent colour instead, so nothing depends on a light background.
 */
@SuppressWarnings("serial")
public class DroPanel extends JPanel {
    /** How the four values are laid out. */
    public enum Form {
        /** One row, with the units: the tool row with room to spare. */
        Row(15f, true, 16),
        /** One row without the units. */
        RowBare(15f, false, 12),
        /** One row without the units, a size smaller: the narrowest tool row. */
        RowTight(13f, false, 10),
        /** Two rows of two, with the units: the camera strip and the top of the machine controls. */
        Grid(15f, true, 14);

        final float size;
        final boolean units;
        final int gap;

        Form(float size, boolean units, int gap) {
            this.size = size;
            this.units = units;
            this.gap = gap;
        }
    }

    private static final String[] AXES = { "X", "Y", "Z", "C" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    /** Between a letter and its value, and a value and its unit. */
    private static final int LETTER_GAP = 5, UNIT_GAP = 3;
    /** A row of the grid, and the one row's height. */
    private static final int ROW = 24;

    /**
     * The longest value each axis has shown, in characters, which is the room it keeps. The row
     * grows when a longer number first appears and does not shrink back, so it does not jitter as
     * the machine moves.
     */
    private final int[] reserved = new int[AXES.length];
    private final String[] values = new String[AXES.length];
    private String unit = ""; //$NON-NLS-1$
    private final Configuration configuration;
    private boolean marked;
    private Form form = Form.Row;

    public DroPanel(Configuration configuration) {
        this.configuration = configuration;
        setOpaque(false);
        clear();
    }

    /**
     * @param location the tool's position, or the offset from the mark when one is set. Null while
     *        no machine is selected, which shows blanks rather than a stale position.
     * @param marked whether that position is relative to a mark.
     */
    public void setLocation(Location location, boolean marked) {
        this.marked = marked;
        if (location == null) {
            clear();
            return;
        }
        String format = configuration.getLengthDisplayFormat();
        double[] coordinates = { location.getX(), location.getY(), location.getZ(), location.getRotation() };
        unit = location.getUnits() == null ? "" : location.getUnits().getShortName(); //$NON-NLS-1$
        for (int i = 0; i < AXES.length; i++) {
            values[i] = reserve(i, String.format(Locale.US, format, coordinates[i]));
        }
        repaint();
    }

    public Form getForm() {
        return form;
    }

    public void setForm(Form form) {
        if (this.form != form) {
            this.form = form;
            revalidate();
            repaint();
        }
    }

    private void clear() {
        for (int i = 0; i < AXES.length; i++) {
            values[i] = reserve(i, ""); //$NON-NLS-1$
        }
        repaint();
    }

    /** The value in the room its axis keeps, which grows to fit it. */
    private String reserve(int axis, String value) {
        int zero = String.format(Locale.US, configuration.getLengthDisplayFormat(), 0.0).length();
        int room = Math.max(reserved[axis], Math.max(zero, value.length()));
        if (room != reserved[axis]) {
            reserved[axis] = room;
            revalidate();
        }
        return value;
    }

    private Font valueFont(Form form) {
        return Ui.mono(form.size, Font.BOLD);
    }

    private Font letterFont() {
        return Ui.font(Tokens.FS_MICRO, Font.BOLD);
    }

    private Font unitFont() {
        return Ui.weighted(Tokens.FS_MICRO, 500);
    }

    /** One axis's width in a form: its letter, the room its value keeps, and its unit. */
    private int cell(Form form, int axis) {
        int letter = getFontMetrics(letterFont()).stringWidth("M"); //$NON-NLS-1$
        int value = getFontMetrics(valueFont(form)).charWidth('0') * reserved[axis];
        int unitWidth = form.units ? UNIT_GAP + getFontMetrics(unitFont()).stringWidth(axis == 3 ? "\u00b0" : "mm") : 0; //$NON-NLS-1$ //$NON-NLS-2$
        return letter + LETTER_GAP + value + unitWidth;
    }

    /** The width the values take in a form, with their room reserved: what the tool row plans with. */
    public int widthFor(Form form) {
        if (form == Form.Grid) {
            return Math.max(cell(form, 0), cell(form, 2)) + form.gap + Math.max(cell(form, 1), cell(form, 3));
        }
        int width = form.gap * (AXES.length - 1);
        for (int i = 0; i < AXES.length; i++) {
            width += cell(form, i);
        }
        return width;
    }

    public int heightFor(Form form) {
        return form == Form.Grid ? 2 * ROW + 4 : ROW;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(widthFor(form), heightFor(form));
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (form == Form.Grid) {
                int left = Math.max(cell(form, 0), cell(form, 2)) + form.gap;
                int top = (getHeight() - heightFor(form)) / 2;
                paintAxis(g2, 0, 0, top, ROW);
                paintAxis(g2, 1, left, top, ROW);
                paintAxis(g2, 2, 0, top + ROW + 4, ROW);
                paintAxis(g2, 3, left, top + ROW + 4, ROW);
            }
            else {
                int x = Math.max(0, (getWidth() - widthFor(form)) / 2);
                for (int i = 0; i < AXES.length; i++) {
                    paintAxis(g2, i, x, 0, getHeight());
                    x += cell(form, i) + form.gap;
                }
            }
        }
        finally {
            g2.dispose();
        }
    }

    /** One axis at x, its baseline centred in the band from top of the given height. */
    private void paintAxis(Graphics2D g2, int axis, int x, int top, int height) {
        FontMetrics valueMetrics = g2.getFontMetrics(valueFont(form));
        int baseline = top + (height + valueMetrics.getAscent() - valueMetrics.getDescent()) / 2;
        // The secondary text colour rather than the stylesheet's muted: muted on the card came
        // out at a contrast of 2.5.
        g2.setFont(letterFont());
        g2.setColor(Ui.text2());
        g2.drawString(AXES[axis], x, baseline);
        int letter = g2.getFontMetrics().stringWidth("M"); //$NON-NLS-1$
        g2.setFont(valueFont(form));
        g2.setColor(marked ? Ui.accent() : Ui.text());
        int room = valueMetrics.charWidth('0') * reserved[axis];
        int valueX = x + letter + LETTER_GAP;
        String value = values[axis] == null ? "" : values[axis]; //$NON-NLS-1$
        g2.drawString(value, valueX + room - valueMetrics.stringWidth(value), baseline);
        if (form.units) {
            g2.setFont(unitFont());
            g2.setColor(Ui.text2());
            g2.drawString(axis == 3 ? "\u00b0" : unit, valueX + room + UNIT_GAP, baseline); //$NON-NLS-1$
        }
    }
}
