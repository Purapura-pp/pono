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
 * {@code design/mockups/mock.css}, through the generated {@link Tokens} and the generated block of
 * the two themes; the icons are its sprite, one SVG file per icon.
 * <p>
 * Colours are {@link ThemeColor}s: looked up in the theme by their {@code Pono.*} key every time
 * they are painted, so a component handed one follows a theme switch without being told. Button
 * styling goes through FlatLaf's per-component style strings, which re-resolve their {@code $key}
 * references when the theme changes; the few shapes FlatLaf has no slot for (chips, keycaps,
 * toggles) are painted here.
 */
public final class Ui {
    private Ui() {
    }

    // ---- tokens -------------------------------------------------------------------------------

    /** Base type size of the mockups: every size here is relative to it. */
    public static final float BASE = Tokens.FS_BODY;

    private static final Map<String, ThemeColor> COLORS = new HashMap<>();

    public static Color color(String key, int fallbackRgb) {
        return color(key, fallbackRgb, 0xff);
    }

    public static Color color(String key, int fallbackRgb, int fallbackAlpha) {
        synchronized (COLORS) {
            return COLORS.computeIfAbsent(key,
                    k -> new ThemeColor(k, (fallbackRgb & 0xffffff) | (fallbackAlpha << 24)));
        }
    }

    public static Color accent() { return color(Tokens.ACCENT, 0x4f8cff); }
    public static Color accentStrong() { return color(Tokens.ACCENT_STRONG, 0x2f6fe0); }
    public static Color accentSoft() { return color(Tokens.ACCENT_SOFT, 0x4f8cff, 0x29); }
    public static Color onAccent() { return color(Tokens.ON_ACCENT, 0xffffff); }
    public static Color ok() { return color(Tokens.OK, 0x34c77b); }
    public static Color okSoft() { return color(Tokens.OK_SOFT, 0x34c77b, 0x29); }
    public static Color warn() { return color(Tokens.WARN, 0xf5b840); }
    public static Color warnSoft() { return color(Tokens.WARN_SOFT, 0xf5b840, 0x29); }
    public static Color err() { return color(Tokens.ERR, 0xff5d5d); }
    public static Color errSoft() { return color(Tokens.ERR_SOFT, 0xff5d5d, 0x29); }
    public static Color info() { return color(Tokens.INFO, 0x38bdf8); }
    /** The stylesheet's .status.run: running is shown in the accent, not in info. */
    public static Color run() { return accent(); }
    public static Color bg() { return color(Tokens.BG, 0xeceff3); }
    public static Color surface() { return color(Tokens.SURFACE, 0xffffff); }
    public static Color surface2() { return color(Tokens.SURFACE_2, 0xf5f7fa); }
    public static Color surface3() { return color(Tokens.SURFACE_3, 0xe9edf2); }
    public static Color border() { return color(Tokens.BORDER, 0xdde2e9); }
    public static Color borderStrong() { return color(Tokens.BORDER_STRONG, 0xc6cfda); }
    public static Color hover() { return color(Tokens.HOVER, 0x000000, 0x0a); }
    public static Color text() { return color(Tokens.TEXT, 0x171c26); }
    public static Color text2() { return color(Tokens.TEXT_2, 0x48546a); }
    public static Color muted() { return color(Tokens.MUTED, 0x7f8a9c); }
    public static Color overlay() { return color(Tokens.OVERLAY, 0xffffff, 0xd1); }
    public static Color cameraBg() { return color(Tokens.CAMERA_BG, 0x0b0e12); }

    /**
     * A colour with its alpha replaced, for the stylesheet's rgba(colour, .35) borders. A theme
     * colour stays one.
     */
    public static Color alpha(Color color, double alpha) {
        if (color instanceof ThemeColor) {
            return ((ThemeColor) color).withAlpha(alpha);
        }
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                (int) Math.round(alpha * 255));
    }

    /**
     * A font of the type scale. The size is the stylesheet's at its 13 pixel body and is scaled
     * with the user's font size, so a larger default makes everything larger in proportion.
     *
     * @param style Font.PLAIN or Font.BOLD.
     */
    public static Font font(float size, int style) {
        Font base = UIManager.getFont("Label.font"); //$NON-NLS-1$
        if (base == null) {
            base = new Font(Font.DIALOG, Font.PLAIN, Math.round(BASE));
        }
        return base.deriveFont(style, scaled(base, size));
    }

    public static Font font(float size) {
        return font(size, Font.PLAIN);
    }

    /**
     * A font of the type scale by the stylesheet's weight. Java2D has no weight between regular
     * and bold for the fonts Chinese text falls back to, so 600 and up is bold - which is also
     * what a browser draws for 600 with those fonts - and below it is regular.
     */
    public static Font weighted(float size, int weight) {
        return font(size, weight >= 600 ? Font.BOLD : Font.PLAIN);
    }

    public static Font mono(float size, int style) {
        Font mono = UIManager.getFont("monospaced.font"); //$NON-NLS-1$
        if (mono == null) {
            mono = new Font(Font.MONOSPACED, Font.PLAIN, Math.round(BASE));
        }
        Font base = UIManager.getFont("Label.font"); //$NON-NLS-1$
        return mono.deriveFont(style, base == null ? size : scaled(base, size));
    }

    private static float scaled(Font base, float size) {
        return size * base.getSize2D() / BASE;
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
            // A dimmed button is already painted at the disabled opacity, icon and all.
            currentForeground = c.isEnabled() || c instanceof Dimmed ? c.getForeground()
                    : alpha(c.getForeground(), DISABLED_OPACITY);
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

    /** The stylesheet's .btn.disabled: the same colours, at this opacity. */
    public static final float DISABLED_OPACITY = 0.45f;

    /** A control that paints itself at {@link #DISABLED_OPACITY} when disabled. */
    public interface Dimmed {
    }

    static void paintDimmed(JComponent c, Graphics g, java.util.function.Consumer<Graphics> paint) {
        if (c.isEnabled()) {
            paint.accept(g);
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setComposite(java.awt.AlphaComposite.SrcOver.derive(DISABLED_OPACITY));
            paint.accept(g2);
        }
        finally {
            g2.dispose();
        }
    }

    /**
     * A button that keeps its colours when disabled and is painted at the stylesheet's opacity,
     * so a greyed-out Start still reads as the green Start, only unavailable.
     */
    @SuppressWarnings("serial")
    public static class Button extends JButton implements Dimmed {
        public Button(Action action) {
            super(action);
        }

        public Button(String text, Icon icon) {
            super(text, icon);
        }

        @Override
        public String getToolTipText() {
            return toolTip(this, super.getToolTipText());
        }

        @Override
        public void paint(Graphics g) {
            paintDimmed(this, g, super::paint);
        }

        @Override
        public Dimension getPreferredSize() {
            return sized(this, super.getPreferredSize());
        }
    }

    private static final String HEIGHT = "Pono.button.height"; //$NON-NLS-1$
    private static final String MIN_WIDTH = "Pono.button.minWidth"; //$NON-NLS-1$
    private static final String SQUARE = "Pono.button.square"; //$NON-NLS-1$

    /**
     * A button's size from the stylesheet, applied whenever it is measured. Measured once and
     * fixed, the width was the text's at 100 %, taken before the button was on a screen: at
     * 125 % the text is wider and came out as "Botto...".
     */
    static Dimension sized(javax.swing.AbstractButton button, Dimension size) {
        if (button.isPreferredSizeSet()) {
            return size;
        }
        Object height = button.getClientProperty(HEIGHT);
        if (height instanceof Integer) {
            size.height = com.formdev.flatlaf.util.UIScale.scale((Integer) height);
        }
        Object width = button.getClientProperty(MIN_WIDTH);
        if (width instanceof Integer) {
            size.width = Math.max(size.width, com.formdev.flatlaf.util.UIScale.scale((Integer) width));
        }
        if (Boolean.TRUE.equals(button.getClientProperty(SQUARE))) {
            size.width = size.height;
        }
        return size;
    }

    /**
     * A button that opens a menu: the stylesheet's button with a caret after its text. Space,
     * Enter and the down arrow open the menu as a click does, so it can be used from the keyboard.
     */
    @SuppressWarnings("serial")
    public static class MenuButton extends Button {
        private final java.util.function.Supplier<javax.swing.JPopupMenu> menu;

        public MenuButton(String text, Icon icon, java.util.function.Supplier<javax.swing.JPopupMenu> menu) {
            super(text, icon);
            this.menu = menu;
            addActionListener(e -> open());
            getInputMap(WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0), "openMenu"); //$NON-NLS-1$
            getActionMap().put("openMenu", new javax.swing.AbstractAction() { //$NON-NLS-1$
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    open();
                }
            });
        }

        private void open() {
            javax.swing.JPopupMenu popup = menu.get();
            if (popup != null) {
                popup.show(this, 0, getHeight());
                if (popup.getComponentCount() > 0) {
                    javax.swing.MenuSelectionManager.defaultManager().setSelectedPath(new javax.swing.MenuElement[] {
                            popup, (javax.swing.MenuElement) popup.getComponent(0) });
                }
            }
        }

        @Override
        public java.awt.Insets getInsets() {
            java.awt.Insets insets = super.getInsets();
            return new java.awt.Insets(insets.top, insets.left, insets.bottom, insets.right + 18);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                currentForeground = getForeground();
                Icon caret = icon("chevdown", 14); //$NON-NLS-1$
                caret.paintIcon(this, g2, getWidth() - super.getInsets().right - 16,
                        (getHeight() - caret.getIconHeight()) / 2);
            }
            finally {
                g2.dispose();
            }
        }
    }

    /**
     * Marks a button that moves the machine: the stylesheet's zap after its text, in the warning
     * colour, and a tooltip saying so. Every such button carries it, and no other does.
     */
    public static <B extends javax.swing.AbstractButton> B movesMachine(B button) {
        // Not a focus stop: the space bar pressed for something else must not move the machine.
        // A dialog's button is the exception, where the focus is the answer being chosen.
        if (javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JDialog.class, button) == null) {
            button.setFocusable(false);
        }
        button.setIcon(icon("zap", 13, warn())); //$NON-NLS-1$
        button.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
        String tip = org.openpnp.Translations.getString("Form.MovesMachine"); //$NON-NLS-1$
        button.setToolTipText(button.getToolTipText() == null ? tip : button.getToolTipText() + " \u00b7 " + tip); //$NON-NLS-1$
        return button;
    }

    /**
     * Client property of a control, or value of the action behind a button: why it is greyed, a
     * String or a Supplier of one. The tooltip of a greyed control says it under what the control
     * does, where it used to leave the user to guess.
     */
    public static final String WHY_DISABLED = "Pono.whyDisabled"; //$NON-NLS-1$

    /** Says why the control is greyed while it is; see {@link #WHY_DISABLED}. */
    public static <C extends JComponent> C whyDisabled(C component, java.util.function.Supplier<String> reason) {
        component.putClientProperty(WHY_DISABLED, reason);
        // A control with no tooltip of its own is not asked for one at all.
        javax.swing.ToolTipManager.sharedInstance().registerComponent(component);
        return component;
    }

    /**
     * What the machine lacks for anything that moves it, or null when it lacks nothing.
     * 
     * @param machine Null before the configuration is loaded, which counts as not enabled.
     */
    public static String machineReason(org.openpnp.spi.Machine machine) {
        if (machine == null || !machine.isEnabled()) {
            return org.openpnp.Translations.getString("Ui.Disabled.MachineOff"); //$NON-NLS-1$
        }
        if (!machine.isHomed()) {
            return org.openpnp.Translations.getString("Ui.Disabled.NotHomed"); //$NON-NLS-1$
        }
        if (machine.isBusy()) {
            return org.openpnp.Translations.getString("Ui.Disabled.Busy"); //$NON-NLS-1$
        }
        return null;
    }

    /** The reason a greyed control gives, or null if it gives none. */
    static String reasonDisabled(JComponent component) {
        Object why = component.getClientProperty(WHY_DISABLED);
        if (why == null && component instanceof javax.swing.AbstractButton
                && ((javax.swing.AbstractButton) component).getAction() != null) {
            why = ((javax.swing.AbstractButton) component).getAction().getValue(WHY_DISABLED);
        }
        if (why instanceof java.util.function.Supplier) {
            why = ((java.util.function.Supplier<?>) why).get();
        }
        return why == null || why.toString().trim().isEmpty() ? null : why.toString();
    }

    /** The tooltip of a control, with the reason it is greyed under it while it is. */
    static String toolTip(JComponent component, String toolTip) {
        if (component.isEnabled()) {
            return toolTip;
        }
        String reason = reasonDisabled(component);
        if (reason == null) {
            return toolTip;
        }
        String what = toolTip == null ? "" //$NON-NLS-1$
                : toolTip.replaceAll("(?i)</?html>", "").trim(); //$NON-NLS-1$ //$NON-NLS-2$
        Color warn = warn();
        return "<html>" + (what.isEmpty() ? "" : what + "<br>") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + String.format("<span style='color:#%06x'>", warn.getRGB() & 0xffffff) //$NON-NLS-1$
                + reason.replace("<", "&lt;") + "</span></html>"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    /** A button of the stylesheet's shape that opens a menu. */
    public static JButton menuButton(String text, Icon icon, Size size, Variant variant,
            java.util.function.Supplier<javax.swing.JPopupMenu> menu) {
        JButton button = new MenuButton(text, icon, menu);
        style(button, size, variant, false);
        button.setFocusable(true);
        return button;
    }

    /**
     * The stylesheet's {@code .btn-group}: buttons side by side sharing their borders, with only
     * the group's outer corners rounded.
     */
    public static JComponent group(javax.swing.AbstractButton... buttons) {
        JPanel group = new JPanel() {
            private java.awt.Shape outline() {
                return new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth(), getHeight(),
                        2 * Tokens.R_SM, 2 * Tokens.R_SM);
            }

            @Override
            protected void paintChildren(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.clip(outline());
                    super.paintChildren(g2);
                }
                finally {
                    g2.dispose();
                }
                // The outer corners the clip cut from the buttons' square borders.
                Graphics2D g3 = (Graphics2D) g.create();
                try {
                    g3.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g3.setColor(borderStrong());
                    g3.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 2 * Tokens.R_SM, 2 * Tokens.R_SM);
                }
                finally {
                    g3.dispose();
                }
            }
        };
        group.setOpaque(false);
        group.setLayout(new java.awt.LayoutManager() {
            @Override
            public void addLayoutComponent(String name, Component comp) {
            }

            @Override
            public void removeLayoutComponent(Component comp) {
            }

            @Override
            public Dimension preferredLayoutSize(java.awt.Container parent) {
                int width = 0;
                int height = 0;
                for (Component c : parent.getComponents()) {
                    width += c.getPreferredSize().width;
                    height = Math.max(height, c.getPreferredSize().height);
                }
                // Neighbours overlap by their shared border.
                return new Dimension(width - Math.max(0, parent.getComponentCount() - 1), height);
            }

            @Override
            public Dimension minimumLayoutSize(java.awt.Container parent) {
                return preferredLayoutSize(parent);
            }

            @Override
            public void layoutContainer(java.awt.Container parent) {
                int x = 0;
                for (Component c : parent.getComponents()) {
                    int width = c.getPreferredSize().width;
                    c.setBounds(x, 0, width, parent.getHeight());
                    x += width - 1;
                }
            }
        });
        for (javax.swing.AbstractButton button : buttons) {
            Object style = button.getClientProperty(FlatClientProperties.STYLE);
            button.putClientProperty(FlatClientProperties.STYLE, (style == null ? "" : style + "; ") + "arc: 0"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            group.add(button);
        }
        group.setMaximumSize(group.getPreferredSize());
        return group;
    }

    /** The toggle counterpart of {@link Button}. */
    @SuppressWarnings("serial")
    public static class ToggleButton extends JToggleButton implements Dimmed {
        public ToggleButton(String text, Icon icon) {
            super(text, icon);
        }

        @Override
        public String getToolTipText() {
            return toolTip(this, super.getToolTipText());
        }

        @Override
        public void paint(Graphics g) {
            paintDimmed(this, g, super::paint);
        }

        @Override
        public Dimension getPreferredSize() {
            return sized(this, super.getPreferredSize());
        }
    }

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
        Danger,
        /** The err red as a fill, for stopping the machine: the one button that must stand out. */
        SolidDanger
    }

    /** Client property on a page's filter field, the one "/" goes to. */
    public static final String FILTER = "Pono.filter"; //$NON-NLS-1$

    /** Makes a field the filter "/" goes to, from anywhere on its page but a text field. */
    public static <T extends javax.swing.JTextField> T markFilter(T field) {
        field.putClientProperty(FILTER, Boolean.TRUE);
        return field;
    }

    /**
     * The filter "/" goes to from the given component: the nearest showing one, looking first
     * among what shares an ancestor with it. Null if the page has none.
     */
    public static javax.swing.JTextField filterFor(Component from, Component root) {
        for (Component c = from; c != null; c = c.getParent()) {
            javax.swing.JTextField found = findFilter(c);
            if (found != null) {
                return found;
            }
            if (c == root) {
                break;
            }
        }
        return from == null && root != null ? findFilter(root) : null;
    }

    private static javax.swing.JTextField findFilter(Component c) {
        if (c instanceof javax.swing.JTextField && c.isShowing() && c.isEnabled()
                && Boolean.TRUE.equals(((JComponent) c).getClientProperty(FILTER))) {
            return (javax.swing.JTextField) c;
        }
        if (c instanceof java.awt.Container && c.isShowing()) {
            for (Component child : ((java.awt.Container) c).getComponents()) {
                javax.swing.JTextField found = findFilter(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** A button in the stylesheet's shape. The action's text and icon are kept if it has them. */
    public static JButton button(Action action, Size size, Variant variant) {
        JButton button = new Button(action);
        style(button, size, variant, false);
        return button;
    }

    public static JButton button(String text, Icon icon, Size size, Variant variant) {
        JButton button = new Button(text, icon);
        style(button, size, variant, false);
        return button;
    }

    /** A square button holding only an icon. */
    public static JButton iconButton(Icon icon, Size size, Variant variant, String toolTip) {
        JButton button = new Button(null, icon);
        button.setToolTipText(toolTip);
        style(button, size, variant, true);
        return button;
    }

    public static JButton iconButton(Action action, Size size, Variant variant) {
        JButton button = new Button(action);
        button.setHideActionText(true);
        style(button, size, variant, true);
        return button;
    }

    public static JToggleButton toggle(Icon icon, Size size, String toolTip) {
        JToggleButton button = new ToggleButton(null, icon);
        button.setToolTipText(toolTip);
        style(button, size, Variant.Default, true);
        return button;
    }

    /**
     * FlatLaf's borderless button: no fill and no border until it is hovered, pressed or selected.
     * A style's "background: null" does not do this - it takes the button's colour away, and the
     * button then paints its parent's, a box where the mockups have none.
     */
    static String borderless(String foreground, String selectedBackground, String selectedForeground) {
        return "foreground: " + foreground + "; disabledText: " + foreground //$NON-NLS-1$ //$NON-NLS-2$
                + "; toolbar.hoverBackground: $Pono.hover; toolbar.pressedBackground: $Pono.surface3" //$NON-NLS-1$
                + "; toolbar.selectedBackground: " + selectedBackground //$NON-NLS-1$
                + "; toolbar.selectedForeground: " + selectedForeground //$NON-NLS-1$
                + "; toolbar.disabledSelectedBackground: " + selectedBackground //$NON-NLS-1$
                + "; toolbar.disabledSelectedForeground: " + selectedForeground; //$NON-NLS-1$
    }

    private static void borderless(javax.swing.AbstractButton button, String style) {
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_BORDERLESS);
        button.putClientProperty(FlatClientProperties.STYLE, style);
    }

    /** The FlatLaf style of a button variant: its colours, the same again for the disabled state. */
    static String colours(Variant variant) {
        // Background, border, foreground, and the rest of the states.
        String[] v;
        switch (variant) {
            case Ghost:
                // A dialog's Cancel is often its default button: still no fill, where the look
                // and feel filled it with the accent.
                return borderless("$Pono.text2", "$Pono.surface3", "$Pono.text") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        + "; default.background: null; default.foreground: $Pono.text2" //$NON-NLS-1$
                        + "; default.hoverBackground: $Pono.hover; default.pressedBackground: $Pono.surface3"; //$NON-NLS-1$
            case Primary:
                v = new String[] { "$Pono.accent", "$Pono.accent", "$Pono.onAccent", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        "hoverBackground: $Pono.accentStrong; hoverBorderColor: $Pono.accentStrong; " //$NON-NLS-1$
                                + "pressedBackground: $Pono.accentStrong" }; //$NON-NLS-1$
                break;
            case PrimaryOk:
                v = new String[] { "$Pono.ok", "$Pono.ok", "#05140b", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        "hoverBackground: darken($Pono.ok,5%); hoverBorderColor: darken($Pono.ok,5%); " //$NON-NLS-1$
                                + "pressedBackground: darken($Pono.ok,10%)" }; //$NON-NLS-1$
                break;
            case Danger:
                v = new String[] { "$Pono.errSoft", "fade($Pono.err,45%)", "$Pono.err", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        "hoverBackground: fade($Pono.err,25%); hoverBorderColor: fade($Pono.err,60%); " //$NON-NLS-1$
                                + "pressedBackground: fade($Pono.err,35%)" }; //$NON-NLS-1$
                break;
            case SolidDanger:
                v = new String[] { "$Pono.err", "$Pono.err", "#ffffff", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        "hoverBackground: darken($Pono.err,6%); hoverBorderColor: darken($Pono.err,6%); " //$NON-NLS-1$
                                + "pressedBackground: darken($Pono.err,12%)" }; //$NON-NLS-1$
                break;
            case Default:
            default:
                v = new String[] { "$Pono.surface2", "$Pono.borderStrong", "$Pono.text", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        "hoverBackground: $Pono.surface3; hoverBorderColor: $Pono.borderStrong; " //$NON-NLS-1$
                                + "pressedBackground: $Pono.surface3; selectedBackground: $Pono.accentSoft; selectedForeground: $Pono.accent" }; //$NON-NLS-1$
                break;
        }
        return "background: " + v[0] + "; borderColor: " + v[1] + "; foreground: " + v[2] //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + "; disabledBackground: " + v[0] + "; disabledBorderColor: " + v[1] //$NON-NLS-1$ //$NON-NLS-2$
                + "; disabledText: " + v[2] + "; " + v[3] //$NON-NLS-1$ //$NON-NLS-2$
                // The same as the default button of a dialog, which the look and feel otherwise
                // paints in colours of its own: white with an accent border in the light theme,
                // over the variant's.
                + "; default.background: " + v[0] + "; default.focusedBackground: " + v[0] //$NON-NLS-1$ //$NON-NLS-2$
                + "; default.borderColor: " + v[1] + "; default.foreground: " + v[2] //$NON-NLS-1$ //$NON-NLS-2$
                + "; default.borderWidth: 1" + defaultStates(v[3]); //$NON-NLS-1$
    }

    private static final java.util.regex.Pattern STATE_COLOUR = java.util.regex.Pattern
            .compile("\\b(hoverBackground|hoverBorderColor|pressedBackground):\\s*([^;]+)"); //$NON-NLS-1$

    /** The hover and pressed colours of a variant again, for when it is the default button. */
    static String defaultStates(String states) {
        StringBuilder out = new StringBuilder();
        java.util.regex.Matcher m = STATE_COLOUR.matcher(states);
        while (m.find()) {
            out.append("; default.").append(m.group(1)).append(": ").append(m.group(2).trim()); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return out.toString();
    }

    private static void style(javax.swing.AbstractButton button, Size size, Variant variant,
            boolean square) {
        // Tab reaches a button, and its border shows the focus. The buttons that move the machine
        // or run the job are made unfocusable where they are built, so that a space bar meant
        // for a table cannot press one.
        button.setFocusable(true);
        button.setIconTextGap(7);
        button.setFont(weighted(size.fontSize, Tokens.FW_BUTTON));
        StringBuilder style = new StringBuilder();
        style.append("arc: ").append(2 * Tokens.R_SM).append("; focusWidth: 0; borderWidth: 1; "); //$NON-NLS-1$ //$NON-NLS-2$
        style.append("minimumHeight: ").append(size.height).append("; "); //$NON-NLS-1$ //$NON-NLS-2$
        style.append("margin: 0,").append(square ? 0 : size.padding).append(",0,") //$NON-NLS-1$ //$NON-NLS-2$
                .append(square ? 0 : size.padding).append("; "); //$NON-NLS-1$
        if (square) {
            style.append("minimumWidth: ").append(size.height).append("; "); //$NON-NLS-1$ //$NON-NLS-2$
        }
        style.append(colours(variant));
        if (variant == Variant.Ghost) {
            borderless(button, style.toString());
        }
        else {
            button.putClientProperty(FlatClientProperties.STYLE, style.toString());
        }
        button.putClientProperty(HEIGHT, size.height);
        button.putClientProperty(SQUARE, square);
    }

    /**
     * Dress a toggle as one of the stylesheet's {@code .pills .pill}: 26 high, 7 pixel radius, no
     * border, secondary text, and the accent as a fill when selected.
     */
    public static void pill(javax.swing.AbstractButton button) {
        button.setFocusable(false);
        button.setIconTextGap(6);
        button.setFont(weighted(Tokens.FS_SMALL, Tokens.FW_BUTTON));
        borderless(button, "arc: 14; focusWidth: 0; minimumHeight: 26; margin: 0,11,0,11; " //$NON-NLS-1$
                + borderless("$Pono.text2", "$Pono.accent", "$Pono.onAccent")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    /**
     * One segment of the stylesheet's {@code .seg}: 24 high, at least 38 wide, 5 pixel radius, mono
     * 11.5 pixel text, the accent as a fill when selected. FlatLaf makes a one-character button
     * square whatever its minimum width, so a {@link ToggleButton} or {@link Button} gets its size
     * from here; other buttons get FlatLaf's.
     */
    public static void seg(javax.swing.AbstractButton button) {
        button.setFocusable(false);
        button.setFont(mono(Tokens.FS_AUX, Font.PLAIN));
        borderless(button, "arc: 10; focusWidth: 0; minimumHeight: 24; minimumWidth: 38; margin: 0,8,0,8; " //$NON-NLS-1$
                + borderless("$Pono.text2", "$Pono.accent", "$Pono.onAccent")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        button.putClientProperty(HEIGHT, 24);
        button.putClientProperty(MIN_WIDTH, 38);
    }

    /** The stylesheet's {@code .seg.tight .s}: at least 30 wide, 6 pixel padding. */
    public static void segTight(javax.swing.AbstractButton button) {
        seg(button);
        Object style = button.getClientProperty(FlatClientProperties.STYLE);
        button.putClientProperty(FlatClientProperties.STYLE,
                style + "; minimumWidth: 30; margin: 0,6,0,6"); //$NON-NLS-1$
        button.putClientProperty(MIN_WIDTH, 30);
    }

    /** An icon-only pill, 7 pixel padding, whose "on" state is the surface-3 fill. */
    public static void iconPill(javax.swing.AbstractButton button) {
        pill(button);
        borderless(button, "arc: 14; focusWidth: 0; minimumHeight: 26; minimumWidth: 30; margin: 0,7,0,7; " //$NON-NLS-1$
                + borderless("$Pono.text2", "$Pono.surface3", "$Pono.text")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
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
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    g2.setColor(borderStrong());
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
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
