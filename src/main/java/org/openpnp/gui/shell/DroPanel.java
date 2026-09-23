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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Locale;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import org.openpnp.model.Configuration;
import org.openpnp.model.Location;

/**
 * The four axis readouts, as an axis letter in small type above a monospaced value.
 * <p>
 * The stylesheet's {@code .dro}: four columns 18 pixels apart, the letter at 10 pixels bold and
 * spaced out in the muted colour, the value in monospaced 20/600 with its unit after it on the
 * same baseline, padded 10 by 14. Where the card does not get its full width - a narrow window,
 * the machine controls beside it - it changes to the 16 pixel {@code .dro.compact} rather than
 * being squeezed until its numbers are cut.
 * <p>
 * This replaces a single label holding {@code "X:1.000 Y:2.000 Z:0.000 C:0.000"} with a bevel
 * border and a hardcoded black on pale green, which was unreadable on a dark theme and told the
 * user that a mark was set by turning the whole strip blue. Marking now tints the numbers with the
 * accent colour instead, so nothing depends on a light background.
 */
@SuppressWarnings("serial")
public class DroPanel extends JPanel {
    private static final String[] AXES = { "X", "Y", "Z", "C" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    /** Wide enough for a signed three digit coordinate, so the row does not jitter as it counts. */
    private static final int VALUE_COLUMNS = 8;

    private final JLabel[] axisLabels = new JLabel[AXES.length];
    private final JLabel[] valueLabels = new JLabel[AXES.length];
    private final JLabel[] unitLabels = new JLabel[AXES.length];
    private final Configuration configuration;
    private boolean marked;
    private boolean compact;

    public DroPanel(Configuration configuration) {
        this.configuration = configuration;
        setLayout(new GridBagLayout());
        setOpaque(false);
        setBorder(new javax.swing.border.EmptyBorder(10, 14, 10, 14));
        GridBagConstraints gc = new GridBagConstraints();
        for (int i = 0; i < AXES.length; i++) {
            axisLabels[i] = new JLabel(AXES[i], SwingConstants.LEFT);
            valueLabels[i] = new JLabel(" ", SwingConstants.RIGHT); //$NON-NLS-1$
            unitLabels[i] = new JLabel(i == AXES.length - 1 ? "\u00b0" : "", SwingConstants.LEFT); //$NON-NLS-1$ //$NON-NLS-2$

            gc.gridx = i * 2;
            gc.gridwidth = 2;
            gc.insets = new Insets(0, i == 0 ? 0 : 18, 1, 0);
            gc.gridy = 0;
            gc.anchor = GridBagConstraints.WEST;
            add(axisLabels[i], gc);

            gc.gridy = 1;
            gc.gridwidth = 1;
            gc.insets = new Insets(0, i == 0 ? 0 : 18, 0, 0);
            gc.anchor = GridBagConstraints.BASELINE_TRAILING;
            add(valueLabels[i], gc);
            gc.gridx = i * 2 + 1;
            gc.insets = new Insets(0, 3, 0, 0);
            gc.anchor = GridBagConstraints.BASELINE_LEADING;
            add(unitLabels[i], gc);
        }
        applyStyle();
        clear();
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                boolean wanted = forcedCompact || (getWidth() > 0 && getWidth() < fullWidth());
                if (wanted != compact) {
                    SwingUtilities.invokeLater(() -> setCompact(wanted));
                }
            }
        });
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

    public boolean isCompact() {
        return compact;
    }

    private boolean forcedCompact;

    /** The camera strip's readout is the compact one whatever room it has, as the stylesheet draws it. */
    public void setForcedCompact(boolean forced) {
        forcedCompact = forced;
        setCompact(forced || (getWidth() > 0 && getWidth() < fullWidth()));
        revalidate();
    }

    private void setCompact(boolean compact) {
        if (this.compact != compact) {
            this.compact = compact;
            applyStyle();
            revalidate();
            repaint();
        }
    }

    private void clear() {
        for (JLabel value : valueLabels) {
            value.setText(""); //$NON-NLS-1$
        }
        applyColors();
    }

    private Font valueFont(boolean compact) {
        return Ui.mono(compact ? Tokens.FS_DRO_COMPACT : Tokens.FS_DRO, Font.BOLD);
    }

    private void applyStyle() {
        Font axisFont = Ui.font(Tokens.FS_MICRO, Font.BOLD).deriveFont(
                java.util.Map.of(java.awt.font.TextAttribute.TRACKING, 0.1f));
        Font unitFont = Ui.weighted(Tokens.FS_MICRO, 500);
        for (int i = 0; i < AXES.length; i++) {
            axisLabels[i].setFont(axisFont);
            valueLabels[i].setFont(valueFont(compact));
            unitLabels[i].setFont(unitFont);
        }
        applyColors();
    }

    private void applyColors() {
        for (int i = 0; i < AXES.length; i++) {
            // The secondary text colour rather than the stylesheet's muted: on the light theme's
            // glass over a green board, muted comes out at a contrast of 2.5.
            axisLabels[i].setForeground(Ui.text2());
            unitLabels[i].setForeground(Ui.text2());
            valueLabels[i].setForeground(marked ? Ui.accent() : Ui.text());
        }
    }

    /** The width the four columns take at full size, with the numbers' room reserved. */
    private int fullWidth() {
        return width(valueFont(false));
    }

    private int width(Font valueFont) {
        int digit = getFontMetrics(valueFont).charWidth('0');
        int unit = getFontMetrics(Ui.weighted(Tokens.FS_MICRO, 500)).stringWidth("mm") + 3; //$NON-NLS-1$
        Insets insets = getInsets();
        // The compact readout keeps room for 120.450, the full one for -120.450 as well.
        int columns = valueFont.getSize2D() < Tokens.FS_DRO ? VALUE_COLUMNS - 1 : VALUE_COLUMNS;
        return insets.left + insets.right + AXES.length * (digit * columns + unit) + 3 * 18;
    }

    /**
     * Reserves a stable width so the row does not resize while the machine moves. Always the
     * full width, compact or not: that is what the card asks for, and it goes back to the full
     * size as soon as it gets it.
     */
    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        size.width = Math.max(size.width, forcedCompact ? width(valueFont(true)) : fullWidth());
        return size;
    }

    /** The compact readout's width: what the card may be narrowed to before it has to move. */
    @Override
    public Dimension getMinimumSize() {
        Dimension size = super.getMinimumSize();
        size.width = width(valueFont(true));
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
