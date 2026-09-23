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

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import javax.swing.SwingConstants;

import org.openpnp.ConfigurationListener;
import org.openpnp.Translations;
import org.openpnp.gui.JogControlsPanel;
import org.openpnp.gui.MachineControlsPanel;
import org.openpnp.gui.ActuatorControlDialog;
import org.openpnp.gui.support.HeadMountableItem;
import org.openpnp.model.Configuration;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Head;
import org.openpnp.spi.HeadMountable;
import org.openpnp.spi.Machine;
import org.openpnp.spi.Nozzle;

import com.formdev.flatlaf.FlatClientProperties;

/**
 * The manual controls as one card on the image: the stylesheet's {@code .jog}.
 * <p>
 * The tool at the top, the X/Y pad with Z and C beside it, the step and the speed underneath, and
 * the five things done most often along the foot. Folded, it is one button in the corner. Nothing
 * here decides anything: the actions, the step and the speed belong to the panels that always had
 * them, so the hotkeys and everything else that reaches for them are untouched.
 * <p>
 * The actuators and the board protection switch, which the stylesheet leaves out, live behind the
 * card's "..." button: they are real controls and have to be somewhere.
 */
@SuppressWarnings("serial")
public class JogCard extends OverlayCard {
    private static final String PREF_EXPANDED = "MachineControlsPanel.jogControlsExpanded"; //$NON-NLS-1$
    /** The stylesheet's .jog: 328 wide with its 12 pixel padding. */
    private static final int WIDTH = 328;
    private static final int KEY = 40;
    /** The page the card is on, whose own folded or unfolded state it keeps. */
    private String page = ""; //$NON-NLS-1$

    private final Preferences prefs = Preferences.userNodeForPackage(MachineControlsPanel.class);
    private final MachineControlsPanel controls;
    private final JogControlsPanel jog;
    private final Configuration configuration;
    private final CardLayout faces = new CardLayout();
    private final JPanel expanded = new JPanel();
    private final JPanel collapsed = new JPanel(new BorderLayout());
    private final JPopupMenu more = new JPopupMenu();
    private boolean isExpanded;

    public JogCard(Configuration configuration, MachineControlsPanel controls) {
        this.configuration = configuration;
        this.controls = controls;
        this.jog = controls.getJogControlsPanel();
        setLayout(faces);
        setBorder(BorderFactory.createEmptyBorder());
        add(expanded, "expanded"); //$NON-NLS-1$
        add(collapsed, "collapsed"); //$NON-NLS-1$

        buildExpanded();
        buildCollapsed();
        buildMore();

        setExpanded(prefs.getBoolean(PREF_EXPANDED, true));
    }

    /** Fold the card to its button and back. Bound to Ctrl-Shift-J by the window. */
    public final Action toggleAction = new AbstractAction(
            Translations.getString("MachineControls.Action.ToggleJogControls")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent e) {
            setExpanded(!isExpanded);
        }
    };

    public void setExpanded(boolean expanded) {
        isExpanded = expanded;
        prefs.putBoolean(PREF_EXPANDED + page, expanded);
        faces.show(this, expanded ? "expanded" : "collapsed"); //$NON-NLS-1$ //$NON-NLS-2$
        setBorder(expanded ? BorderFactory.createEmptyBorder(12, 12, 12, 12)
                : BorderFactory.createEmptyBorder());
        revalidate();
        if (getParent() != null) {
            getParent().revalidate();
            getParent().repaint();
        }
    }

    /**
     * The page on show: each keeps whether the card is folded. The feeders page starts folded,
     * since its table and the feeder's form need the image more than the jog keys do; the others
     * start open.
     */
    public void setPage(String page, boolean foldedByDefault) {
        this.page = page == null || page.isEmpty() ? "" : "." + page; //$NON-NLS-1$ //$NON-NLS-2$
        boolean fallback = this.page.isEmpty() ? prefs.getBoolean(PREF_EXPANDED, true) : !foldedByDefault;
        setExpanded(prefs.getBoolean(PREF_EXPANDED + this.page, fallback));
    }

    private JPanel speedRow;
    private JPanel footRow;
    private java.awt.Component speedGap;
    private java.awt.Component footGap;
    private boolean compact;

    /**
     * Where the image is too short for the whole card, the speed and the foot's buttons go and
     * the pads, the tool and the step stay: the keys are what the card is for, and every one of
     * those buttons is also in the Machine menu and on a hotkey.
     */
    private void setCompact(boolean compact) {
        if (this.compact == compact || speedRow == null) {
            return;
        }
        this.compact = compact;
        speedRow.setVisible(!compact);
        footRow.setVisible(!compact);
        speedGap.setVisible(!compact);
        footGap.setVisible(!compact);
        revalidate();
        if (getParent() != null) {
            getParent().revalidate();
        }
    }

    private final java.awt.event.ComponentListener stageListener = new java.awt.event.ComponentAdapter() {
        @Override
        public void componentResized(java.awt.event.ComponentEvent e) {
            fitStage();
        }
    };

    private void fitStage() {
        java.awt.Container stage = getParent();
        if (stage == null || speedRow == null || stage.getHeight() <= 0) {
            return;
        }
        // Room below the camera tools along the top: their 32 pixel row with its shadow, and a
        // margin above, between and below.
        int room = stage.getHeight() - 32 - OverlayCard.SHADOW_TOP - OverlayCard.SHADOW_BOTTOM
                - 3 * OverlayAnchorLayout.MARGIN;
        int full = expanded.getPreferredSize().height + getInsets().top + getInsets().bottom
                + (compact ? speedRow.getPreferredSize().height + footRow.getPreferredSize().height + 20 : 0);
        javax.swing.SwingUtilities.invokeLater(() -> setCompact(isExpanded && full > room));
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (getParent() != null) {
            getParent().addComponentListener(stageListener);
        }
    }

    @Override
    public void removeNotify() {
        if (getParent() != null) {
            getParent().removeComponentListener(stageListener);
        }
        super.removeNotify();
    }

    private void buildExpanded() {
        expanded.setOpaque(false);
        expanded.setLayout(new BoxLayout(expanded, BoxLayout.Y_AXIS));

        // ---- head: the tool, Home, "...", fold ----
        JPanel head = row(8);
        JComboBox<?> tool = controls.getHeadMountableCombo();
        tool.putClientProperty(FlatClientProperties.STYLE,
                "arc: 12; background: $Pono.surface2; borderColor: $Pono.border; " //$NON-NLS-1$
                        + "buttonBackground: null; buttonArrowColor: $Pono.textMuted; focusWidth: 0"); //$NON-NLS-1$
        tool.setFont(Ui.font(Ui.BASE));
        tool.setRenderer(new ToolRenderer());
        tool.setPreferredSize(new Dimension(tool.getPreferredSize().width, 30));
        tool.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        head.add(tool);
        JButton home = Ui.iconButton(Ui.icon("home"), Ui.Size.Sm, Ui.Variant.Default, //$NON-NLS-1$
                Translations.getString("JogCard.Home")); //$NON-NLS-1$
        home.addActionListener(e -> controls.homeAction.actionPerformed(e));
        controls.homeAction.addPropertyChangeListener(e -> home.setEnabled(controls.homeAction.isEnabled()));
        home.setEnabled(controls.homeAction.isEnabled());
        Ui.whyDisabled(home, this::machineReason);
        head.add(home);
        JButton moreButton = Ui.iconButton(Ui.icon("more"), Ui.Size.Sm, Ui.Variant.Default, //$NON-NLS-1$
                Translations.getString("JogCard.More")); //$NON-NLS-1$
        moreButton.addActionListener(e -> more.show(moreButton, 0, moreButton.getHeight()));
        head.add(moreButton);
        JButton fold = Ui.iconButton(Ui.icon("chevdown"), Ui.Size.Sm, Ui.Variant.Default, //$NON-NLS-1$
                Translations.getString("JogCard.Fold")); //$NON-NLS-1$
        fold.addActionListener(e -> setExpanded(false));
        head.add(fold);
        expanded.add(head);
        expanded.add(Box.createVerticalStrut(10));

        // ---- pads: the X/Y pad, then Z, then C ----
        JPanel pads = new JPanel();
        pads.setOpaque(false);
        pads.setLayout(new BoxLayout(pads, BoxLayout.X_AXIS));
        pads.setAlignmentX(LEFT_ALIGNMENT);
        JPanel dpad = new JPanel(new GridLayout(3, 3, 5, 5));
        dpad.setOpaque(false);
        dpad.add(blank());
        dpad.add(key(jog.yPlusAction, "up")); //$NON-NLS-1$
        dpad.add(blank());
        dpad.add(key(jog.xMinusAction, "left")); //$NON-NLS-1$
        dpad.add(centre("XY", KEY)); //$NON-NLS-1$
        dpad.add(key(jog.xPlusAction, "right")); //$NON-NLS-1$
        dpad.add(blank());
        dpad.add(key(jog.yMinusAction, "down")); //$NON-NLS-1$
        dpad.add(blank());
        dpad.setMaximumSize(dpad.getPreferredSize());
        pads.add(dpad);
        pads.add(Box.createHorizontalGlue());
        pads.add(axis("Z", key(jog.zPlusAction, "up"), parkKey(jog.zParkAction), //$NON-NLS-1$ //$NON-NLS-2$
                key(jog.zMinusAction, "down"))); //$NON-NLS-1$
        pads.add(Box.createHorizontalStrut(12));
        pads.add(axis("C", key(jog.cPlusAction, "rccw"), parkKey(jog.cParkAction), //$NON-NLS-1$ //$NON-NLS-2$
                key(jog.cMinusAction, "rcw"))); //$NON-NLS-1$
        expanded.add(pads);
        expanded.add(Box.createVerticalStrut(10));

        // ---- step ----
        JPanel step = row(10);
        JLabel stepLabel = Ui.t2(Translations.getString("JogCard.Step")); //$NON-NLS-1$
        stepLabel.setFont(Ui.font(11f));
        step.add(stepLabel);
        JLabel units = Ui.mono(configuration.getSystemUnits().getShortName() + " / \u00b0", 11f); //$NON-NLS-1$
        configuration.addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationComplete(Configuration configuration) throws Exception {
                units.setText(configuration.getSystemUnits().getShortName() + " / \u00b0"); //$NON-NLS-1$
            }
        });
        step.add(units);
        step.add(Box.createHorizontalGlue());
        jog.getIncrementSelector().setTight(true);
        step.add(jog.getIncrementSelector());
        expanded.add(step);
        speedGap = Box.createVerticalStrut(10);
        expanded.add(speedGap);

        // ---- speed ----
        JPanel speed = row(10);
        speedRow = speed;
        JLabel speedLabel = Ui.t2(Translations.getString("JogCard.Speed")); //$NON-NLS-1$
        speedLabel.setFont(Ui.font(11f));
        speed.add(speedLabel);
        JSlider slider = jog.getSpeedSlider();
        slider.putClientProperty(FlatClientProperties.STYLE,
                "trackWidth: 4; thumbSize: 14,14; trackValueColor: $Pono.accent; trackColor: $Pono.surface3; " //$NON-NLS-1$
                        + "thumbColor: #ffffff; thumbBorderColor: $Pono.accent; focusedColor: null; " //$NON-NLS-1$
                        + "hoverThumbColor: #ffffff; pressedThumbColor: #ffffff"); //$NON-NLS-1$
        slider.setPaintTicks(false);
        slider.setOpaque(false);
        speed.add(slider);
        JLabel percent = Ui.mono(slider.getValue() + "%", 11f); //$NON-NLS-1$
        percent.setHorizontalAlignment(SwingConstants.RIGHT);
        percent.setPreferredSize(new Dimension(36, 20));
        percent.setMinimumSize(new Dimension(36, 20));
        slider.addChangeListener(e -> percent.setText(slider.getValue() + "%")); //$NON-NLS-1$
        speed.add(percent);
        expanded.add(speed);
        footGap = Box.createVerticalStrut(10);
        expanded.add(footGap);

        // ---- foot ----
        JPanel foot = new JPanel(new GridLayout(1, 5, 6, 0));
        footRow = foot;
        foot.setOpaque(false);
        foot.setAlignmentX(LEFT_ALIGNMENT);
        foot.add(footButton(Translations.getString("JogCard.ParkXY"), jog.xyParkAction)); //$NON-NLS-1$
        foot.add(footButton(Translations.getString("JogCard.ParkZ"), jog.zParkAction)); //$NON-NLS-1$
        foot.add(footButton(Translations.getString("JogCard.SafeZ"), jog.safezAction)); //$NON-NLS-1$
        foot.add(footButton(Translations.getString("JogCard.Discard"), jog.discardAction)); //$NON-NLS-1$
        foot.add(footButton(Translations.getString("JogCard.Recycle"), jog.recycleAction)); //$NON-NLS-1$
        expanded.add(foot);

        int content = WIDTH - 24;
        expanded.setPreferredSize(new Dimension(content, expanded.getPreferredSize().height));
        for (Component c : expanded.getComponents()) {
            if (c instanceof JComponent) {
                ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
                if (c != pads) {
                    c.setMaximumSize(new Dimension(content, c.getMaximumSize().height));
                }
            }
        }
    }

    private void buildCollapsed() {
        collapsed.setOpaque(false);
        JButton open = new JButton(Translations.getString("JogCard.Open"), Ui.icon("move")); //$NON-NLS-1$ //$NON-NLS-2$
        open.setFocusable(false);
        open.setIconTextGap(7);
        open.setFont(Ui.font(Ui.BASE, Font.BOLD));
        open.putClientProperty(FlatClientProperties.STYLE,
                "arc: 20; focusWidth: 0; borderWidth: 0; minimumHeight: 38; margin: 0,12,0,12; " //$NON-NLS-1$
                        + "background: null; borderColor: null; hoverBackground: $Pono.hover; pressedBackground: $Pono.surface3"); //$NON-NLS-1$
        open.addActionListener(e -> setExpanded(true));
        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.X_AXIS));
        inner.add(open);
        // The key that opens it, as it is really bound.
        JLabel key = Ui.kbd("Ctrl Shift J"); //$NON-NLS-1$
        key.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10),
                key.getBorder()));
        inner.add(key);
        collapsed.add(inner, BorderLayout.CENTER);
    }

    /** What the stylesheet leaves off the card and this program still has: actuators, safety. */
    private void buildMore() {
        configuration.addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationComplete(Configuration configuration) throws Exception {
                fillMore(configuration.getMachine());
            }
        });
    }

    private void fillMore(Machine machine) {
        more.removeAll();
        JCheckBoxMenuItem protection = new JCheckBoxMenuItem(
                Translations.getString("JogControlsPanel.Label.BoardProtection"), //$NON-NLS-1$
                jog.isBoardProtectionEnabled());
        protection.setToolTipText(
                Translations.getString("JogControlsPanel.Label.BoardProtection.Description")); //$NON-NLS-1$
        protection.addActionListener(e -> jog.setBoardProtectionEnabled(protection.isSelected()));
        more.add(protection);
        more.addSeparator();
        JMenuItem heading = new JMenuItem(Translations.getString("JogControlsPanel.Tab.Actuators")); //$NON-NLS-1$
        heading.setEnabled(false);
        more.add(heading);
        for (Actuator actuator : machine.getActuators()) {
            more.add(actuatorItem(actuator));
        }
        for (Head head : machine.getHeads()) {
            for (Actuator actuator : head.getActuators()) {
                more.add(actuatorItem(actuator));
            }
        }
    }

    private JMenuItem actuatorItem(Actuator actuator) {
        String name = actuator.getHead() == null ? actuator.getName()
                : actuator.getHead().getName() + ":" + actuator.getName(); //$NON-NLS-1$
        JMenuItem item = new JMenuItem(name);
        item.addActionListener(e -> {
            ActuatorControlDialog dialog = new ActuatorControlDialog(actuator);
            dialog.pack();
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        });
        return item;
    }

    // ---- pieces --------------------------------------------------------------------------------

    private static JPanel row(int gap) {
        JPanel row = new JPanel() {
            @Override
            public Component add(Component component) {
                if (getComponentCount() > 0) {
                    super.add(Box.createHorizontalStrut(gap));
                }
                if (component instanceof JComponent) {
                    ((JComponent) component).setAlignmentY(CENTER_ALIGNMENT);
                }
                return super.add(component);
            }
        };
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(LEFT_ALIGNMENT);
        return row;
    }

    /** Why the keys are greyed: what the machine lacks for moving. */
    private String machineReason() {
        return Ui.machineReason(configuration.getMachine());
    }

    /** A 40 pixel jog key: 9 pixel arc, surface-2 on a strong border, one icon. */
    private JButton key(Action action, String icon) {
        JButton key = Ui.whyDisabled(new Ui.Button(action), this::machineReason);
        key.setHideActionText(true);
        key.setIcon(Ui.icon(icon));
        key.setFocusable(false);
        key.putClientProperty(FlatClientProperties.STYLE,
                "arc: 18; focusWidth: 0; borderWidth: 1; margin: 0,0,0,0; minimumWidth: 40; minimumHeight: 40; " //$NON-NLS-1$
                        + "background: $Pono.surface2; borderColor: $Pono.borderStrong; foreground: $Label.foreground; " //$NON-NLS-1$
                        + "hoverBackground: $Pono.surface3; pressedBackground: $Pono.accentSoft"); //$NON-NLS-1$
        Dimension size = new Dimension(KEY, KEY);
        key.setPreferredSize(size);
        key.setMinimumSize(size);
        key.setMaximumSize(size);
        return key;
    }

    /** The muted centre of a pad: a label, not a key, in a key's outline. */
    private static JComponent centre(String text, int height) {
        RoundedPanel c = new RoundedPanel(9, Ui::surface3, Ui::borderStrong);
        c.setLayout(new BorderLayout());
        JLabel label = Ui.t2(text);
        label.setFont(Ui.font(12f, Font.BOLD));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        c.add(label, BorderLayout.CENTER);
        Dimension size = new Dimension(KEY, height);
        c.setPreferredSize(size);
        c.setMinimumSize(size);
        c.setMaximumSize(size);
        return c;
    }

    /** The "P" key between an axis's two arrows: park it. 26 high, as the stylesheet's. */
    private JButton parkKey(Action action) {
        JButton key = Ui.whyDisabled(new Ui.Button(action), this::machineReason);
        key.setHideActionText(true);
        key.setIcon(null);
        key.setText("P"); //$NON-NLS-1$
        key.setFocusable(false);
        key.setFont(Ui.font(12f, Font.BOLD));
        key.putClientProperty(FlatClientProperties.STYLE,
                "arc: 18; focusWidth: 0; borderWidth: 1; margin: 0,0,0,0; minimumWidth: 40; minimumHeight: 26; " //$NON-NLS-1$
                        + "background: $Pono.surface3; borderColor: $Pono.borderStrong; foreground: $Pono.textMuted; " //$NON-NLS-1$
                        + "hoverBackground: $Pono.surface2; pressedBackground: $Pono.accentSoft"); //$NON-NLS-1$
        Dimension size = new Dimension(KEY, 26);
        key.setPreferredSize(size);
        key.setMinimumSize(size);
        key.setMaximumSize(size);
        return key;
    }

    private static JComponent blank() {
        JPanel blank = new JPanel();
        blank.setOpaque(false);
        blank.setPreferredSize(new Dimension(KEY, KEY));
        return blank;
    }

    /** An axis column: its letter, up, park, down. */
    private static JComponent axis(String letter, JComponent up, JComponent park, JComponent down) {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        JLabel label = Ui.t2(letter);
        label.setFont(Ui.font(10f, Font.BOLD));
        label.setAlignmentX(CENTER_ALIGNMENT);
        label.setPreferredSize(new Dimension(KEY, 14));
        label.setMaximumSize(new Dimension(KEY, 14));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        column.add(label);
        column.add(Box.createVerticalStrut(5));
        for (JComponent key : new JComponent[] { up, park, down }) {
            key.setAlignmentX(CENTER_ALIGNMENT);
            column.add(key);
            if (key != down) {
                column.add(Box.createVerticalStrut(5));
            }
        }
        column.setMaximumSize(column.getPreferredSize());
        return column;
    }

    private JButton footButton(String text, Action action) {
        JButton button = Ui.button(text, null, Ui.Size.Xs, Ui.Variant.Default);
        // They move the machine: no focus stop for a stray space bar.
        button.setFocusable(false);
        // Five across 320 pixels leaves 59 each; the sheet's 8 pixel padding does not fit CJK text.
        button.putClientProperty(FlatClientProperties.STYLE,
                button.getClientProperty(FlatClientProperties.STYLE) + "; margin: 0,3,0,3"); //$NON-NLS-1$
        button.setPreferredSize(null);
        button.addActionListener(e -> action.actionPerformed(e));
        action.addPropertyChangeListener(e -> button.setEnabled(action.isEnabled()));
        button.setEnabled(action.isEnabled());
        return Ui.whyDisabled(button, this::machineReason);
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
    }

    /** {@code N1 · NT1  head H1}: the tool's name, its tip, and where it is. */
    private final class ToolRenderer extends javax.swing.DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof HeadMountableItem) {
                HeadMountable item = ((HeadMountableItem) value).getItem();
                // The tool and its tip at 600, where it is in the muted colour at 500.
                StringBuilder text = new StringBuilder("<html><b>").append(escape(item.getName())); //$NON-NLS-1$
                if (item instanceof Nozzle && ((Nozzle) item).getNozzleTip() != null) {
                    text.append(" \u00b7 ").append(escape(((Nozzle) item).getNozzleTip().getName())); //$NON-NLS-1$
                }
                text.append("</b>"); //$NON-NLS-1$
                if (item.getHead() != null) {
                    java.awt.Color muted = Ui.muted();
                    text.append("&nbsp;&nbsp;<span style='font-weight:normal;color:") //$NON-NLS-1$
                            .append(String.format("#%06x", muted.getRGB() & 0xffffff)).append("'>") //$NON-NLS-1$ //$NON-NLS-2$
                            .append(escape(Translations.getString("CameraPanel.Show.HeadPrefix") //$NON-NLS-1$
                                    + item.getHead().getName()))
                            .append("</span>"); //$NON-NLS-1$
                }
                setText(text.append("</html>").toString()); //$NON-NLS-1$
                setIcon(Ui.iconSm(item instanceof Nozzle ? "nozzle" //$NON-NLS-1$
                        : item instanceof org.openpnp.spi.Camera ? "camera" : "zap")); //$NON-NLS-1$ //$NON-NLS-2$
                setIconTextGap(8);
            }
            return this;
        }
    }
}
