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
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Locale;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import org.openpnp.model.Configuration;
import org.openpnp.model.Location;

/**
 * The four axis readouts, as an axis letter in small type above a monospaced value.
 * <p>
 * This replaces a single label holding {@code "X:1.000 Y:2.000 Z:0.000 C:0.000"} with a bevel
 * border and a hardcoded black on pale green, which was unreadable on a dark theme and told the
 * user that a mark was set by turning the whole strip blue. Marking now tints the numbers with the
 * accent colour instead, so nothing depends on a light background.
 */
@SuppressWarnings("serial")
public class DroPanel extends JPanel {
    private static final String[] AXES = { "X", "Y", "Z", "C" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    /** Used when the theme carries no Pono.dro.fontScale, i.e. on a non-Pono look and feel. */
    private static final float DEFAULT_FONT_SCALE = 1.5f;
    /** Wide enough for a signed four digit coordinate, so the row does not jitter as it counts. */
    private static final int VALUE_COLUMNS = 9;

    private final JLabel[] axisLabels = new JLabel[AXES.length];
    private final JLabel[] valueLabels = new JLabel[AXES.length];
    private final JLabel[] unitLabels = new JLabel[AXES.length];
    private final Configuration configuration;
    private boolean marked;

    public DroPanel(Configuration configuration) {
        this.configuration = configuration;
        setLayout(new GridBagLayout());
        setOpaque(false);

        // The stylesheet's .dro: four columns 18 pixels apart, the letter above the value with the
        // unit tucked after it, the whole thing padded 10 by 14.
        setBorder(new javax.swing.border.EmptyBorder(10, 14, 10, 14));
        GridBagConstraints gc = new GridBagConstraints();
        for (int i = 0; i < AXES.length; i++) {
            axisLabels[i] = new JLabel(AXES[i], SwingConstants.LEFT);
            valueLabels[i] = new JLabel(" ", SwingConstants.RIGHT); //$NON-NLS-1$
            unitLabels[i] = new JLabel(i == AXES.length - 1 ? "\u00b0" : "", SwingConstants.LEFT); //$NON-NLS-1$ //$NON-NLS-2$

            gc.gridx = i * 2;
            gc.gridwidth = 2;
            gc.insets = new Insets(0, i == 0 ? 0 : 18, 0, 0);
            gc.gridy = 0;
            gc.anchor = GridBagConstraints.SOUTHWEST;
            add(axisLabels[i], gc);

            gc.gridy = 1;
            gc.gridwidth = 1;
            gc.anchor = GridBagConstraints.EAST;
            add(valueLabels[i], gc);
            gc.gridx = i * 2 + 1;
            gc.insets = new Insets(0, 3, 0, 0);
            gc.anchor = GridBagConstraints.SOUTHWEST;
            add(unitLabels[i], gc);
        }
        applyStyle();
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
        double[] coordinates = { location.getX(), location.getY(), location.getZ(),
                location.getRotation() };
        String unit = location.getUnits() == null ? "" : location.getUnits().getShortName(); //$NON-NLS-1$
        for (int i = 0; i < AXES.length; i++) {
            valueLabels[i].setText(String.format(Locale.US, format, coordinates[i]));
            if (i < AXES.length - 1) {
                unitLabels[i].setText(unit);
            }
        }
        applyColors();
    }

    private void clear() {
        for (JLabel value : valueLabels) {
            value.setText(""); //$NON-NLS-1$
        }
        applyColors();
    }

    private void applyStyle() {
        Font base = UIManager.getFont("Label.font"); //$NON-NLS-1$
        if (base == null) {
            base = new Font(Font.DIALOG, Font.PLAIN, 12);
        }
        Font mono = UIManager.getFont("monospaced.font"); //$NON-NLS-1$
        if (mono == null) {
            mono = new Font(Font.MONOSPACED, Font.PLAIN, base.getSize());
        }

        Object scale = UIManager.get("Pono.dro.fontScale"); //$NON-NLS-1$
        float factor = scale instanceof Number ? ((Number) scale).floatValue() : DEFAULT_FONT_SCALE;
        Font valueFont = mono.deriveFont(Font.BOLD, base.getSize2D() * factor);
        // The letter is 10 pixels, bold, spaced out; the unit 10 pixels, medium.
        Font axisFont = base.deriveFont(Font.BOLD, 10f).deriveFont(
                java.util.Map.of(java.awt.font.TextAttribute.TRACKING, 0.1f));
        Font unitFont = base.deriveFont(10f);

        for (int i = 0; i < AXES.length; i++) {
            axisLabels[i].setFont(axisFont);
            valueLabels[i].setFont(valueFont);
            unitLabels[i].setFont(unitFont);
            // Reserving the width here rather than per update keeps the numbers from shifting
            // sideways as digits come and go.
            valueLabels[i].setPreferredSize(null);
            valueLabels[i].setText(valueLabels[i].getText());
        }
        applyColors();
    }

    private void applyColors() {
        Color axisColor = color("Pono.textMuted", UIManager.getColor("Label.disabledForeground")); //$NON-NLS-1$ //$NON-NLS-2$
        Color valueColor = marked
                ? color("Component.accentColor", UIManager.getColor("Label.foreground")) //$NON-NLS-1$ //$NON-NLS-2$
                : UIManager.getColor("Label.foreground"); //$NON-NLS-1$
        for (int i = 0; i < AXES.length; i++) {
            if (axisColor != null) {
                axisLabels[i].setForeground(axisColor);
                unitLabels[i].setForeground(axisColor);
            }
            if (valueColor != null) {
                valueLabels[i].setForeground(valueColor);
            }
        }
    }

    private static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color != null ? color : fallback;
    }

    /** Reserves a stable width so the row does not resize while the machine moves. */
    @Override
    public java.awt.Dimension getPreferredSize() {
        java.awt.Dimension size = super.getPreferredSize();
        if (valueLabels[0] != null && valueLabels[0].getFont() != null) {
            int digit = getFontMetrics(valueLabels[0].getFont()).charWidth('0');
            int wanted = AXES.length * (digit * VALUE_COLUMNS + 13);
            size.width = Math.max(size.width, wanted);
        }
        return size;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // Fonts and colours come from the theme, so they are stale after a look and feel change.
        // Null while the superclass constructor is still running.
        if (valueLabels != null && valueLabels[0] != null) {
            applyStyle();
        }
    }
}
