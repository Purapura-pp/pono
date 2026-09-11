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

package org.openpnp.gui.support;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.Function;

import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Renders a status value as a tinted pill rather than as a whole cell flooded with colour.
 * <p>
 * The renderers this replaces filled the cell with a fixed pastel and wrote on it in
 * {@code Color.black}, so the colour came from neither the theme nor the row's selection state,
 * and a dark theme got a bright block in the middle of the table. Here the colour is the theme's
 * own status colour, the fill is a faint tint of it, and a selected row is left to the table so
 * the selection stays legible.
 */
@SuppressWarnings("serial")
public class StatusPillRenderer extends DefaultTableCellRenderer {
    public enum Tone {
        Ok("Pono.statusOk", new Color(0x34c77b)), //$NON-NLS-1$
        Warning("Pono.statusWarn", new Color(0xf5b840)), //$NON-NLS-1$
        Error("Pono.statusErr", new Color(0xff5d5d)), //$NON-NLS-1$
        Info("Pono.statusRun", new Color(0x38bdf8)), //$NON-NLS-1$
        Muted("Pono.textMuted", Color.GRAY); //$NON-NLS-1$

        private final String colorKey;
        private final Color fallback;

        Tone(String colorKey, Color fallback) {
            this.colorKey = colorKey;
            this.fallback = fallback;
        }

        /** The Pono.* keys exist only in the Pono themes; other look and feels get the literal. */
        Color color() {
            Color color = UIManager.getColor(colorKey);
            return color != null ? color : fallback;
        }
    }

    /** How much of the tone is left in the pill's fill. */
    private static final int FILL_ALPHA = 40;
    private static final int PILL_INSET = 3;

    private final Function<Object, Tone> toneOf;
    private final Function<Object, String> textOf;

    private Tone tone = Tone.Muted;
    private boolean selected;

    public StatusPillRenderer(Function<Object, Tone> toneOf, Function<Object, String> textOf) {
        this.toneOf = toneOf;
        this.textOf = textOf;
        setHorizontalAlignment(SwingConstants.CENTER);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
            boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        selected = isSelected;
        tone = value == null ? Tone.Muted : toneOf.apply(value);
        setText(value == null ? "" : textOf.apply(value)); //$NON-NLS-1$
        // Opaque only when selected, so the table's selection fill is what shows through.
        setOpaque(isSelected);
        setForeground(isSelected ? table.getSelectionForeground() : tone.color());
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!selected && !getText().isEmpty()) {
            Color color = tone.color();
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), FILL_ALPHA));
                int height = getHeight() - PILL_INSET * 2;
                g2.fillRoundRect(PILL_INSET, PILL_INSET, getWidth() - PILL_INSET * 2, height,
                        height, height);
            }
            finally {
                g2.dispose();
            }
        }
        super.paintComponent(g);
    }
}
