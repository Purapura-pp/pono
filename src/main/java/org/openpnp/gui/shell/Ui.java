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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.Map;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;

/**
 * The mockups' design system as Swing factories. Every number and colour here is from
 * {@code ui-mockups/src/mock.css}; the icons are its sprite, one SVG file per icon.
 * <p>
 * Colours are read from the theme by their {@code Pono.*} key, with the stylesheet's literal value
 * as the fallback for a user on a look and feel that is not one of the Pono themes. Button styling
 * goes through FlatLaf's per-component style strings, which re-resolve their {@code $key}
 * references when the theme changes; the few shapes FlatLaf has no slot for (chips, keycaps,
 * toggles) are painted here.
 */
public final class Ui {
    private Ui() {
    }

    // ---- tokens -------------------------------------------------------------------------------

    /** Base type size of the mockups. */
    public static final float BASE = 13f;

    public static Color color(String key, int fallbackRgb) {
        Color color = UIManager.getColor(key);
        return color != null ? color : new Color(fallbackRgb);
    }

    public static Color color(String key, int fallbackRgb, int fallbackAlpha) {
        Color color = UIManager.getColor(key);
        return color != null ? color : new Color((fallbackRgb & 0xffffff) | (fallbackAlpha << 24), true);
    }

    public static Color accent() { return color("Pono.accent", 0x4f8cff); } //$NON-NLS-1$
    public static Color accentStrong() { return color("Pono.accentStrong", 0x2f6fe0); } //$NON-NLS-1$
    public static Color accentSoft() { return color("Pono.accentSoft", 0x4f8cff, 0x29); } //$NON-NLS-1$
    public static Color onAccent() { return color("Pono.onAccent", 0xffffff); } //$NON-NLS-1$
    public static Color ok() { return color("Pono.statusOk", 0x34c77b); } //$NON-NLS-1$
    public static Color okSoft() { return color("Pono.okSoft", 0x34c77b, 0x29); } //$NON-NLS-1$
    public static Color warn() { return color("Pono.statusWarn", 0xf5b840); } //$NON-NLS-1$
    public static Color warnSoft() { return color("Pono.warnSoft", 0xf5b840, 0x29); } //$NON-NLS-1$
    public static Color err() { return color("Pono.statusErr", 0xff5d5d); } //$NON-NLS-1$
    public static Color errSoft() { return color("Pono.errSoft", 0xff5d5d, 0x29); } //$NON-NLS-1$
    public static Color run() { return color("Pono.statusRun", 0x38bdf8); } //$NON-NLS-1$
    public static Color bg() { return color("Pono.bg", 0xeceff3); } //$NON-NLS-1$
    public static Color surface() { return color("Pono.surface", 0xffffff); } //$NON-NLS-1$
    public static Color surface2() { return color("Pono.surface2", 0xf5f7fa); } //$NON-NLS-1$
    public static Color surface3() { return color("Pono.surface3", 0xe9edf2); } //$NON-NLS-1$
    public static Color border() { return color("Pono.border", 0xdde2e9); } //$NON-NLS-1$
    public static Color borderStrong() { return color("Pono.borderStrong", 0xc6cfda); } //$NON-NLS-1$
    public static Color hover() { return color("Pono.hover", 0x000000, 0x0a); } //$NON-NLS-1$
    public static Color text() { return color("Label.foreground", 0x171c26); } //$NON-NLS-1$
    public static Color text2() { return color("Pono.textSecondary", 0x48546a); } //$NON-NLS-1$
    public static Color muted() { return color("Pono.textMuted", 0x7f8a9c); } //$NON-NLS-1$
    public static Color overlay() { return color("Pono.overlayBackground", 0xffffff, 0xd1); } //$NON-NLS-1$

    /** A colour with its alpha replaced, for the stylesheet's rgba(colour, .35) borders. */
    public static Color alpha(Color color, double alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                (int) Math.round(alpha * 255));
    }

    public static Font font(float size, int style) {
        Font base = UIManager.getFont("Label.font"); //$NON-NLS-1$
        if (base == null) {
            base = new Font(Font.DIALOG, Font.PLAIN, 13);
        }
        return base.deriveFont(style, size);
    }

    public static Font font(float size) {
        return font(size, Font.PLAIN);
    }

    public static Font mono(float size, int style) {
        Font mono = UIManager.getFont("monospaced.font"); //$NON-NLS-1$
        if (mono == null) {
            mono = new Font(Font.MONOSPACED, Font.PLAIN, 13);
        }
        return mono.deriveFont(style, size);
    }

    // ---- icons --------------------------------------------------------------------------------

    private static final Map<String, FlatSVGIcon> ICONS = new HashMap<>();

    /**
     * One of the sprite's icons at the stylesheet's default 18 pixels, in the current foreground.
     * 
     * @param name The sprite's name, e.g. {@code play}, {@code chevdown}.
     */
    public static Icon icon(String name) {
        return icon(name, 18);
    }

    /** The stylesheet's small size. */
    public static Icon iconSm(String name) {
        return icon(name, 14);
    }

    public static Icon icon(String name, int size) {
        return icon(name, size, null);
    }

    /**
     * @param color The colour to draw in, or null to follow the component's foreground - the
     *              stylesheet's {@code currentColor}.
     */
    public static Icon icon(String name, int size, Color color) {
        String key = name + '/' + size + '/' + (color == null ? "fg" : Integer.toHexString(color.getRGB())); //$NON-NLS-1$
        FlatSVGIcon icon = ICONS.get(key);
        if (icon == null) {
            icon = new FlatSVGIcon("icons/pono/" + name + ".svg", size, size); //$NON-NLS-1$ //$NON-NLS-2$
            if (color != null) {
                icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> color));
            }
            else {
                // The files are drawn in black; the filter swaps it for whatever the component
                // is painting text in, so a white icon on an accent button costs nothing extra.
                icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> currentForeground));
            }
            ICONS.put(key, icon);
        }
        return color == null ? new FollowingIcon(icon) : icon;
    }

    /**
     * FlatSVGIcon's filter has no view of the component, so the foreground is handed in through
     * this field by the wrapper that paints the icon. Set from the event thread only.
     */
    private static Color currentForeground = Color.BLACK;

    /** Paints a foreground-following icon: the wrapper that sets {@link #currentForeground}. */
    private static final class FollowingIcon implements Icon {
        private final Icon inner;

        FollowingIcon(Icon inner) {
            this.inner = inner;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Color was = currentForeground;
            currentForeground = c.isEnabled() ? c.getForeground()
                    : alpha(c.getForeground(), 0.45);
            try {
                inner.paintIcon(c, g, x, y);
            }
            finally {
                currentForeground = was;
            }
        }

        @Override
        public int getIconWidth() {
            return inner.getIconWidth();
        }

        @Override
        public int getIconHeight() {
            return inner.getIconHeight();
        }
    }

    // ---- buttons ------------------------------------------------------------------------------

    public enum Size {
        /** 32 pixels high, 0 12 padding. */
        Md(32, 12, 13f),
        /** 28 pixels high, 0 10 padding, 12 pixel text. */
        Sm(28, 10, 12f),
        /** 24 pixels high, 0 8 padding, 12 pixel text. */
        Xs(24, 8, 12f);

        final int height;
        final int padding;
        final float fontSize;

        Size(int height, int padding, float fontSize) {
            this.height = height;
            this.padding = padding;
            this.fontSize = fontSize;
        }
    }

    public enum Variant {
        /** surface-2 on a strong border. */
        Default,
        /** No fill and no border until hovered; text-2. */
        Ghost,
        /** Accent fill. */
        Primary,
        /** The ok green as a fill, for Start. */
        PrimaryOk,
        /** err-soft fill on an err border, for Stop. */
        Danger
    }

    /** A button in the stylesheet's shape. The action's text and icon are kept if it has them. */
    public static JButton button(Action action, Size size, Variant variant) {
        JButton button = new JButton(action);
        style(button, size, variant, false);
        return button;
    }

    public static JButton button(String text, Icon icon, Size size, Variant variant) {
        JButton button = new JButton(text, icon);
        style(button, size, variant, false);
        return button;
    }

    /** A square button holding only an icon. */
    public static JButton iconButton(Icon icon, Size size, Variant variant, String toolTip) {
        JButton button = new JButton(icon);
        button.setToolTipText(toolTip);
        style(button, size, variant, true);
        return button;
    }

    public static JButton iconButton(Action action, Size size, Variant variant) {
        JButton button = new JButton(action);
        button.setHideActionText(true);
        style(button, size, variant, true);
        return button;
    }

    public static JToggleButton toggle(Icon icon, Size size, String toolTip) {
        JToggleButton button = new JToggleButton(icon);
        button.setToolTipText(toolTip);
        style(button, size, Variant.Default, true);
        return button;
    }

    private static void style(javax.swing.AbstractButton button, Size size, Variant variant,
            boolean square) {
        button.setFocusable(false);
        button.setIconTextGap(7);
        button.setFont(font(size.fontSize, Font.PLAIN).deriveFont(
                java.util.Map.of(java.awt.font.TextAttribute.WEIGHT,
                        java.awt.font.TextAttribute.WEIGHT_MEDIUM)));
        StringBuilder style = new StringBuilder();
        style.append("arc: 6; focusWidth: 0; borderWidth: 1; "); //$NON-NLS-1$
        style.append("minimumHeight: ").append(size.height).append("; "); //$NON-NLS-1$ //$NON-NLS-2$
        style.append("margin: 0,").append(square ? 0 : size.padding).append(",0,") //$NON-NLS-1$ //$NON-NLS-2$
                .append(square ? 0 : size.padding).append("; "); //$NON-NLS-1$
        if (square) {
            style.append("minimumWidth: ").append(size.height).append("; "); //$NON-NLS-1$ //$NON-NLS-2$
        }
        switch (variant) {
            case Ghost:
                style.append("background: null; borderColor: null; foreground: $Pono.textSecondary; " //$NON-NLS-1$
                        + "hoverBackground: $Pono.hover; hoverBorderColor: null; " //$NON-NLS-1$
                        + "pressedBackground: $Pono.surface3; selectedBackground: $Pono.surface3"); //$NON-NLS-1$
                break;
            case Primary:
                style.append("background: $Pono.accent; borderColor: $Pono.accent; foreground: $Pono.onAccent; " //$NON-NLS-1$
                        + "hoverBackground: $Pono.accentStrong; hoverBorderColor: $Pono.accentStrong; " //$NON-NLS-1$
                        + "pressedBackground: $Pono.accentStrong"); //$NON-NLS-1$
                break;
            case PrimaryOk:
                style.append("background: $Pono.statusOk; borderColor: $Pono.statusOk; foreground: #05140b; " //$NON-NLS-1$
                        + "hoverBackground: darken($Pono.statusOk,5%); hoverBorderColor: darken($Pono.statusOk,5%); " //$NON-NLS-1$
                        + "pressedBackground: darken($Pono.statusOk,10%)"); //$NON-NLS-1$
                break;
            case Danger:
                style.append("background: $Pono.errSoft; borderColor: fade($Pono.statusErr,45%); foreground: $Pono.statusErr; " //$NON-NLS-1$
                        + "hoverBackground: fade($Pono.statusErr,25%); hoverBorderColor: fade($Pono.statusErr,60%); " //$NON-NLS-1$
                        + "pressedBackground: fade($Pono.statusErr,35%)"); //$NON-NLS-1$
                break;
            case Default:
            default:
                style.append("background: $Pono.surface2; borderColor: $Pono.borderStrong; foreground: $Label.foreground; " //$NON-NLS-1$
                        + "hoverBackground: $Pono.surface3; hoverBorderColor: $Pono.borderStrong; " //$NON-NLS-1$
                        + "pressedBackground: $Pono.surface3; selectedBackground: $Pono.accentSoft; selectedForeground: $Pono.accent"); //$NON-NLS-1$
                break;
        }
        button.putClientProperty(FlatClientProperties.STYLE, style.toString());
        Dimension preferred = button.getPreferredSize();
        button.setPreferredSize(new Dimension(square ? size.height : preferred.width, size.height));
    }

    /**
     * Dress a toggle as one of the stylesheet's {@code .pills .pill}: 26 high, 7 pixel arc, no
     * border, secondary text, and the accent as a fill when selected.
     */
    public static void pill(javax.swing.AbstractButton button) {
        button.setFocusable(false);
        button.setIconTextGap(6);
        button.setFont(font(12f).deriveFont(
                java.util.Map.of(java.awt.font.TextAttribute.WEIGHT,
                        java.awt.font.TextAttribute.WEIGHT_MEDIUM)));
        button.putClientProperty(FlatClientProperties.STYLE,
                "arc: 7; focusWidth: 0; borderWidth: 0; minimumHeight: 26; margin: 0,11,0,11; " //$NON-NLS-1$
                        + "background: null; borderColor: null; foreground: $Pono.textSecondary; " //$NON-NLS-1$
                        + "hoverBackground: $Pono.hover; hoverBorderColor: null; pressedBackground: $Pono.surface3; " //$NON-NLS-1$
                        + "selectedBackground: $Pono.accent; selectedForeground: $Pono.onAccent; " //$NON-NLS-1$
                        + "toolbar.selectedBackground: $Pono.accent"); //$NON-NLS-1$
    }

    /** An icon-only pill, 7 pixel padding, whose "on" state is the surface-3 fill. */
    public static void iconPill(javax.swing.AbstractButton button) {
        pill(button);
        button.putClientProperty(FlatClientProperties.STYLE,
                "arc: 7; focusWidth: 0; borderWidth: 0; minimumHeight: 26; minimumWidth: 30; margin: 0,7,0,7; " //$NON-NLS-1$
                        + "background: null; borderColor: null; foreground: $Pono.textSecondary; " //$NON-NLS-1$
                        + "hoverBackground: $Pono.hover; hoverBorderColor: null; pressedBackground: $Pono.surface3; " //$NON-NLS-1$
                        + "selectedBackground: $Pono.surface3; selectedForeground: $Label.foreground"); //$NON-NLS-1$
    }

    // ---- small shapes -------------------------------------------------------------------------

    /** The 1 x 24 vertical rule between groups in the top bar. */
    public static JComponent divider(int height) {
        JPanel rule = new JPanel();
        rule.setOpaque(true);
        rule.setBackground(border());
        rule.setPreferredSize(new Dimension(1, height));
        rule.setMinimumSize(new Dimension(1, height));
        rule.setMaximumSize(new Dimension(1, height));
        return rule;
    }

    /** A keycap, e.g. {@code Ctrl K}: mono 10 pixel text in a 4 pixel arc box. */
    public static JLabel kbd(String text) {
        JLabel label = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(surface3());
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 4, 4);
                    g2.setColor(borderStrong());
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 4, 4);
                }
                finally {
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        label.setOpaque(false);
        label.setFont(mono(10f, Font.PLAIN));
        label.setForeground(text2());
        label.setBorder(new javax.swing.border.EmptyBorder(1, 5, 1, 5));
        return label;
    }

    /** A label in the stylesheet's secondary text colour. */
    public static JLabel t2(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(text2());
        return label;
    }

    /** A label in the stylesheet's muted colour. */
    public static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(muted());
        return label;
    }

    /** A monospaced label with tabular figures, for numbers. */
    public static JLabel mono(String text, float size) {
        JLabel label = new JLabel(text);
        label.setFont(mono(size, Font.PLAIN));
        label.setForeground(text2());
        return label;
    }
}
