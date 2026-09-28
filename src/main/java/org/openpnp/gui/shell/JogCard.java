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
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.openpnp.ConfigurationListener;
import org.openpnp.Translations;
import org.openpnp.gui.ActuatorControlDialog;
import org.openpnp.gui.JogControlsPanel;
import org.openpnp.gui.MachineControlsPanel;
import org.openpnp.gui.support.HeadMountableItem;
import org.openpnp.model.Configuration;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Head;
import org.openpnp.spi.HeadMountable;
import org.openpnp.spi.Machine;
import org.openpnp.spi.MachineListener;
import org.openpnp.spi.Nozzle;

import com.formdev.flatlaf.FlatClientProperties;

/**
 * The manual controls, docked at the right of the camera card: the stylesheet's {@code .jog} as a
 * column beside the image rather than a card over it.
 * <p>
 * The card chooses one of four sizes by the height it has, each laid out whole: the full one with
 * the machine's state and the one thing to do next on a line of their own; a compact one with
 * those, the tool and Home on one line and smaller keys; a smallest one with the speed and the
 * five actions of the foot moved into the "..." menu; and a wide one for a very short window, the
 * keys at the left and the rest at the right. Shorter still, it folds to a strip. It used to be
 * cut off where the image was too short for it, and covered the right of the picture.
 * <p>
 * The first line's button follows the machine: Connect while it is off, Home - in the accent -
 * while it is on and not homed, and Home as an ordinary button once it is. It sat beside the tool
 * as a button greyed out, and hard to read, whenever the machine was off.
 * <p>
 * Nothing here decides anything: the actions, the step and the speed belong to the panels that
 * always had them, so the hotkeys and everything else that reaches for them are untouched.
 */
@SuppressWarnings("serial")
public class JogCard extends JPanel {
    /** The four sizes. */
    public enum Level {
        Full, Compact, Min, Wide
    }

    public static final int WIDTH = 296;
    public static final int WIDE_WIDTH = 440;
    public static final int FOLDED_WIDTH = 30;
    /** What the readout adds at the top of the column, when the tool row has no room for it. */
    public static final int DRO_NEEDED = 70;

    private static final String PREF_EXPANDED = "MachineControlsPanel.jogControlsExpanded"; //$NON-NLS-1$

    /** The height a size needs, its padding included: what the card chooses a size by. */
    public static int needed(Level level) {
        switch (level) {
            case Full:
                return 346;
            case Compact:
                return 266;
            case Min:
                // Its note on two lines, as the English one is.
                return 236;
            default:
                return 142;
        }
    }

    /** A size's keys and rows. */
    private static final class Metrics {
        final int pad, key, park, label, gap, step;

        Metrics(int pad, int key, int park, int label, int gap, int step) {
            this.pad = pad;
            this.key = key;
            this.park = park;
            this.label = label;
            this.gap = gap;
            this.step = step;
        }

        static Metrics of(Level level) {
            switch (level) {
                case Full:
                    return new Metrics(12, 40, 26, 14, 5, 28);
                case Compact:
                    return new Metrics(10, 34, 22, 12, 4, 26);
                default:
                    return new Metrics(10, 30, 20, 12, 3, 26);
            }
        }
    }

    /** What the machine is doing, as the first line says it. */
    private enum State {
        Off, NotHomed, Homing, Busy, Homed
    }

    /** The page the card is on, whose own folded or unfolded state it keeps. */
    private String page = ""; //$NON-NLS-1$

    private final Preferences prefs = Preferences.userNodeForPackage(MachineControlsPanel.class);
    private final MachineControlsPanel controls;
    private final JogControlsPanel jog;
    private final Configuration configuration;
    private final JPopupMenu more = new JPopupMenu();
    private Level level = Level.Full;
    private boolean expanded = true;
    /** Folded because the card is too short for the smallest size, whatever the user chose. */
    private boolean autoFolded;
    /** The readout, when it is at the top of the column rather than in the tool row. */
    private DroPanel readout;
    private boolean busy;

    private final JButton primary = Ui.button("", Ui.icon("home"), Ui.Size.Md, Ui.Variant.Primary); //$NON-NLS-1$ //$NON-NLS-2$
    private final JComponent stateDot = new Dot();
    private final JLabel stateTitle = new JLabel();
    /** The same line for the camera strip, which has no room for the card. */
    private final JButton stripPrimary = Ui.button("", Ui.icon("home", 14), Ui.Size.Sm, Ui.Variant.Primary); //$NON-NLS-1$ //$NON-NLS-2$
    private final JComponent stripDot = new Dot();
    private final JLabel stripTitle = new JLabel();
    private JPanel stripLine;
    private final JLabel stateSub = Ui.t2(""); //$NON-NLS-1$
    private final JLabel percent = Ui.mono("100%", 11f); //$NON-NLS-1$
    private final List<JButton> footButtons = new ArrayList<>();
    private final List<Action> footActions = new ArrayList<>();
    private final List<String> footLabels = new ArrayList<>();
    private final JButton moreButton = Ui.iconButton(Ui.icon("more"), Ui.Size.Sm, Ui.Variant.Ghost, //$NON-NLS-1$
            Translations.getString("JogCard.More")); //$NON-NLS-1$
    private final JButton foldButton = Ui.iconButton(Ui.icon("chevright"), Ui.Size.Sm, Ui.Variant.Ghost, //$NON-NLS-1$
            Translations.getString("JogCard.Fold")); //$NON-NLS-1$
    private final FoldedTab folded = new FoldedTab();

    public JogCard(Configuration configuration, MachineControlsPanel controls) {
        this.configuration = configuration;
        this.controls = controls;
        this.jog = controls.getJogControlsPanel();
        setLayout(new BorderLayout());
        setOpaque(false);

        JComboBox<?> tool = controls.getHeadMountableCombo();
        tool.putClientProperty(FlatClientProperties.STYLE,
                "arc: 12; background: $Pono.surface2; borderColor: $Pono.border; " //$NON-NLS-1$
                        + "buttonBackground: null; buttonArrowColor: $Pono.textMuted; focusWidth: 0"); //$NON-NLS-1$
        tool.setFont(Ui.font(Ui.BASE));
        tool.setRenderer(new ToolRenderer());
        jog.getIncrementSelector().setTight(true);
        JSlider slider = jog.getSpeedSlider();
        slider.putClientProperty(FlatClientProperties.STYLE,
                "trackWidth: 4; thumbSize: 14,14; trackValueColor: $Pono.accent; trackColor: $Pono.surface3; " //$NON-NLS-1$
                        + "thumbColor: #ffffff; thumbBorderColor: $Pono.accent; focusedColor: null; " //$NON-NLS-1$
                        + "hoverThumbColor: #ffffff; pressedThumbColor: #ffffff"); //$NON-NLS-1$
        slider.setPaintTicks(false);
        slider.setOpaque(false);
        percent.setHorizontalAlignment(SwingConstants.RIGHT);
        percent.setText(slider.getValue() + "%"); //$NON-NLS-1$
        slider.addChangeListener(e -> percent.setText(slider.getValue() + "%")); //$NON-NLS-1$

        stateTitle.setFont(Ui.font(13.5f, Font.BOLD));
        stateSub.setFont(Ui.font(11f));
        stripTitle.setFont(Ui.font(12.5f, Font.BOLD));
        primary.setFocusable(false);
        primary.addActionListener(this::primaryPressed);
        stripPrimary.setFocusable(false);
        stripPrimary.addActionListener(this::primaryPressed);
        Ui.whyDisabled(stripPrimary, this::machineReason);
        controls.homeAction.addPropertyChangeListener(e -> showState());
        controls.startStopMachineAction.addPropertyChangeListener(e -> showState());
        Ui.whyDisabled(primary, this::machineReason);

        foot("JogCard.ParkXY", jog.xyParkAction); //$NON-NLS-1$
        foot("JogCard.ParkZ", jog.zParkAction); //$NON-NLS-1$
        foot("JogCard.SafeZ", jog.safezAction); //$NON-NLS-1$
        foot("JogCard.Discard", jog.discardAction); //$NON-NLS-1$
        foot("JogCard.Recycle", jog.recycleAction); //$NON-NLS-1$

        moreButton.setFocusable(false);
        moreButton.addActionListener(e -> {
            fillMore();
            more.show(moreButton, 0, moreButton.getHeight());
        });
        foldButton.setFocusable(false);
        foldButton.addActionListener(e -> setExpanded(false));

        watchMachine();
        expanded = prefs.getBoolean(PREF_EXPANDED, true);
        rebuild();
    }

    // ---- what the card asks of the column -----------------------------------------------------

    /** The width the column takes at the current size, or folded. */
    public int columnWidth() {
        return showsFolded() ? FOLDED_WIDTH : level == Level.Wide ? WIDE_WIDTH : WIDTH;
    }

    public boolean showsFolded() {
        return !expanded || autoFolded;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        if (this.level != level) {
            this.level = level;
            rebuild();
        }
    }

    public boolean isAutoFolded() {
        return autoFolded;
    }

    public void setAutoFolded(boolean autoFolded) {
        if (this.autoFolded != autoFolded) {
            this.autoFolded = autoFolded;
            rebuild();
        }
    }

    /** The readout to show at the top of the column, or null when the tool row shows it. */
    public void setReadout(DroPanel readout) {
        if (this.readout != readout) {
            this.readout = readout;
            rebuild();
        }
    }

    public boolean isExpanded() {
        return expanded;
    }

    /** The machine's state and the button that does what is next, on one line for the camera strip. */
    public JComponent getStripLine() {
        if (stripLine == null) {
            stripLine = row(8, stripDot, stripTitle, stripPrimary);
            showState();
        }
        return stripLine;
    }

    /** Fold the card to its strip and back. Bound to Ctrl-Shift-J by the window. */
    public final Action toggleAction = new AbstractAction(
            Translations.getString("MachineControls.Action.ToggleJogControls")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent e) {
            setExpanded(!expanded);
        }
    };

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        prefs.putBoolean(PREF_EXPANDED + page, expanded);
        rebuild();
    }

    /**
     * The page on show: each keeps whether the card is folded. The feeders page starts folded,
     * since its table and the feeder's form need the image more than the jog keys do; the others
     * start open.
     */
    public void setPage(String page, boolean foldedByDefault) {
        this.page = page == null || page.isEmpty() ? "" : "." + page; //$NON-NLS-1$ //$NON-NLS-2$
        boolean fallback = this.page.isEmpty() ? prefs.getBoolean(PREF_EXPANDED, true) : !foldedByDefault;
        boolean wanted = prefs.getBoolean(PREF_EXPANDED + this.page, fallback);
        if (wanted != expanded) {
            expanded = wanted;
            rebuild();
        }
    }

    /** Fired when the column's width changes: another size, folded or unfolded. */
    public static final String PROPERTY_COLUMN = "column"; //$NON-NLS-1$
    private int shape;

    private void rebuild() {
        removeAll();
        if (showsFolded()) {
            folded.setToolTipText(Translations.getString(autoFolded ? "JogCard.AutoFolded.ToolTip" //$NON-NLS-1$
                    : "JogCard.Folded.ToolTip")); //$NON-NLS-1$
            add(folded, BorderLayout.CENTER);
        }
        else {
            add(face(level), BorderLayout.CENTER);
        }
        showState();
        revalidate();
        repaint();
        firePropertyChange(PROPERTY_COLUMN, shape, ++shape);
    }

    // ---- the faces ----------------------------------------------------------------------------

    private JPanel face(Level level) {
        Metrics m = Metrics.of(level);
        JPanel face = stack(8);
        face.setBorder(new EmptyBorder(m.pad, m.pad, m.pad, m.pad));
        if (level == Level.Wide) {
            JPanel right = stack(8);
            addReadout(right);
            right.add(row(6, stateDot, grow(stateTitle), primary, moreButton));
            right.add(row(0, grow(controls.getHeadMountableCombo())));
            right.add(stepRow(24, false));
            JComponent pads = pads(m);
            right.add(note("JogCard.InMenu.Short", //$NON-NLS-1$
                    WIDE_WIDTH - 2 * m.pad - pads.getPreferredSize().width - 16));
            face.add(row(16, pads, grow(right)));
            styleHeader(Ui.Size.Sm, 30);
            return face;
        }
        addReadout(face);
        if (level == Level.Full) {
            JPanel words = stack(1);
            words.add(stateTitle);
            words.add(stateSub);
            face.add(row(10, stateDot, grow(words), primary));
            face.add(row(6, grow(controls.getHeadMountableCombo()), moreButton, foldButton));
            styleHeader(Ui.Size.Md, 32);
        }
        else {
            face.add(row(6, stateDot, grow(controls.getHeadMountableCombo()), primary, moreButton));
            styleHeader(Ui.Size.Sm, 30);
        }
        face.add(pads(m));
        face.add(stepRow(m.step, true));
        if (level == Level.Min) {
            face.add(note("JogCard.InMenu", WIDTH - 2 * m.pad)); //$NON-NLS-1$
        }
        else {
            face.add(speedRow());
            // The five share the column's width, however long their words: in English they asked
            // for a few pixels more than the column has, and the card was cut at the right.
            JPanel foot = new JPanel(new GridLayout(1, footButtons.size(), 4, 0)) {
                @Override
                public Dimension getPreferredSize() {
                    // Qualified: in a component, WIDTH alone is ImageObserver's.
                    return new Dimension(JogCard.WIDTH - 2 * m.pad, super.getPreferredSize().height);
                }
            };
            foot.setOpaque(false);
            for (JButton button : footButtons) {
                foot.add(button);
            }
            face.add(foot);
        }
        return face;
    }

    private void addReadout(JPanel column) {
        if (readout != null) {
            readout.setForm(DroPanel.Form.Grid);
            column.add(readout);
            JComponent rule = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    g.setColor(Ui.border());
                    g.fillRect(0, getHeight() / 2, getWidth(), 1);
                }
            };
            rule.setPreferredSize(new Dimension(1, 3));
            column.add(rule);
        }
    }

    private void styleHeader(Ui.Size size, int toolHeight) {
        JComboBox<?> tool = controls.getHeadMountableCombo();
        tool.setPreferredSize(new Dimension(80, toolHeight));
        tool.setMinimumSize(new Dimension(40, toolHeight));
        primaryHeight = size;
        showState();
    }

    /** A line of small print that wraps at the width given rather than widening the card. */
    private JComponent note(String key, int width) {
        JLabel note = Ui.muted("<html>" + Translations.getString(key) + "</html>"); //$NON-NLS-1$ //$NON-NLS-2$
        note.setFont(Ui.font(11f));
        return Ui.wrapAt(note, width);
    }

    private JComponent stepRow(int height, boolean units) {
        JLabel label = Ui.t2(Translations.getString("JogCard.Step")); //$NON-NLS-1$
        label.setFont(Ui.font(11f));
        List<Component> parts = new ArrayList<>();
        parts.add(label);
        if (units) {
            parts.add(Ui.mono(configuration.getSystemUnits().getShortName() + " / \u00b0", 10.5f)); //$NON-NLS-1$
        }
        parts.add(grow(new JPanel(null) {
            {
                setOpaque(false);
            }
        }));
        parts.add(jog.getIncrementSelector());
        JPanel row = row(8, parts.toArray(new Component[0]));
        row.setPreferredSize(new Dimension(0, height));
        return row;
    }

    private JComponent speedRow() {
        JLabel label = Ui.t2(Translations.getString("JogCard.Speed")); //$NON-NLS-1$
        label.setFont(Ui.font(11f));
        percent.setPreferredSize(new Dimension(36, 18));
        JPanel row = row(10, label, grow(jog.getSpeedSlider()), percent);
        row.setPreferredSize(new Dimension(0, 18));
        return row;
    }

    /** The X/Y pad, then Z and C, each with its park key between the arrows. */
    private JComponent pads(Metrics m) {
        JPanel dpad = new JPanel(new GridLayout(3, 3, m.gap, m.gap));
        dpad.setOpaque(false);
        dpad.add(blank(m.key));
        dpad.add(key(jog.yPlusAction, "up", m.key)); //$NON-NLS-1$
        dpad.add(blank(m.key));
        dpad.add(key(jog.xMinusAction, "left", m.key)); //$NON-NLS-1$
        dpad.add(centre("XY", m.key)); //$NON-NLS-1$
        dpad.add(key(jog.xPlusAction, "right", m.key)); //$NON-NLS-1$
        dpad.add(blank(m.key));
        dpad.add(key(jog.yMinusAction, "down", m.key)); //$NON-NLS-1$
        dpad.add(blank(m.key));
        JPanel axes = row(10, axis("Z", key(jog.zPlusAction, "up", m.key), parkKey(jog.zParkAction, m), //$NON-NLS-1$ //$NON-NLS-2$
                key(jog.zMinusAction, "down", m.key), m), //$NON-NLS-1$
                axis("C", key(jog.cPlusAction, "rccw", m.key), parkKey(jog.cParkAction, m), //$NON-NLS-1$ //$NON-NLS-2$
                        key(jog.cMinusAction, "rcw", m.key), m)); //$NON-NLS-1$
        JPanel gap = new JPanel(null);
        gap.setOpaque(false);
        return row(10, dpad, grow(gap), axes);
    }

    // ---- the first line -----------------------------------------------------------------------

    private Ui.Size primaryHeight = Ui.Size.Md;

    private State state() {
        Machine machine = configuration.getMachine();
        if (machine == null || !machine.isEnabled()) {
            return State.Off;
        }
        if (busy) {
            return machine.isHomed() ? State.Busy : State.Homing;
        }
        return machine.isHomed() ? State.Homed : State.NotHomed;
    }

    private Color stateColor() {
        switch (state()) {
            case Off:
                return Ui.err();
            case NotHomed:
                return Ui.warn();
            case Homing:
            case Busy:
                return Ui.accent();
            default:
                return Ui.ok();
        }
    }

    /** The first line as the machine stands: the dot, the words, and the button that does what is next. */
    private void showState() {
        State state = state();
        String key = "JogCard.State." + state.name(); //$NON-NLS-1$
        stateTitle.setText(Translations.getString(key));
        stateSub.setText(Translations.getString(key + ".Sub")); //$NON-NLS-1$
        stateTitle.setToolTipText(stateSub.getText());
        stateDot.setToolTipText(stateTitle.getText() + " \u00b7 " + stateSub.getText()); //$NON-NLS-1$
        stateDot.repaint();
        Ui.Variant variant;
        String text;
        String icon;
        boolean enabled;
        String toolTip;
        switch (state) {
            case Off:
                variant = Ui.Variant.Primary;
                text = Translations.getString("TopBar.Connect"); //$NON-NLS-1$
                icon = "zap"; //$NON-NLS-1$
                enabled = controls.startStopMachineAction.isEnabled();
                toolTip = Translations.getString("TopBar.Connect.toolTipText"); //$NON-NLS-1$
                break;
            case NotHomed:
                variant = Ui.Variant.Primary;
                text = Translations.getString("JogCard.Home.Label"); //$NON-NLS-1$
                icon = "home"; //$NON-NLS-1$
                enabled = controls.homeAction.isEnabled();
                toolTip = Translations.getString("JogCard.Home"); //$NON-NLS-1$
                break;
            case Homing:
                variant = Ui.Variant.Primary;
                text = Translations.getString("JogCard.Homing"); //$NON-NLS-1$
                icon = "home"; //$NON-NLS-1$
                enabled = false;
                toolTip = Translations.getString("JogCard.Home"); //$NON-NLS-1$
                break;
            default:
                variant = Ui.Variant.Default;
                text = Translations.getString("JogCard.Home.Label"); //$NON-NLS-1$
                icon = "home"; //$NON-NLS-1$
                enabled = controls.homeAction.isEnabled();
                toolTip = Translations.getString("JogCard.Home"); //$NON-NLS-1$
                break;
        }
        String keyed = toolTip + (state == State.Off ? "" : " (Ctrl+H)"); //$NON-NLS-1$ //$NON-NLS-2$
        for (JButton button : new JButton[] { primary, stripPrimary }) {
            Ui.Size size = button == primary ? primaryHeight : Ui.Size.Sm;
            button.setText(text);
            button.setIcon(Ui.icon(icon, size == Ui.Size.Md ? 16 : 14));
            Ui.restyle(button, size, variant);
            // Restyling makes a button a focus stop again, and this one moves the machine.
            button.setFocusable(false);
            button.setEnabled(enabled);
            button.setToolTipText(keyed);
        }
        stripTitle.setText(stateTitle.getText());
        stripTitle.setToolTipText(stateSub.getText());
        stripDot.repaint();
    }

    /** The machine's state as a coloured dot. */
    private final class Dot extends JComponent {
        Dot() {
            setPreferredSize(new Dimension(10, 10));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(stateColor());
                int s = Math.min(getWidth(), getHeight());
                g2.fillOval((getWidth() - s) / 2, (getHeight() - s) / 2, s, s);
            }
            finally {
                g2.dispose();
            }
        }
    }

    private void primaryPressed(ActionEvent e) {
        if (state() == State.Off) {
            if (controls.startStopMachineAction.isEnabled()) {
                controls.startStopMachineAction.actionPerformed(e);
            }
        }
        else if (controls.homeAction.isEnabled()) {
            controls.homeAction.actionPerformed(e);
        }
    }

    private void watchMachine() {
        MachineListener listener = new MachineListener.Adapter() {
            @Override
            public void machineEnabled(Machine machine) {
                SwingUtilities.invokeLater(JogCard.this::showState);
            }

            @Override
            public void machineDisabled(Machine machine, String reason) {
                SwingUtilities.invokeLater(JogCard.this::showState);
            }

            @Override
            public void machineHomed(Machine machine, boolean isHomed) {
                SwingUtilities.invokeLater(JogCard.this::showState);
            }

            @Override
            public void machineBusy(Machine machine, boolean busy) {
                SwingUtilities.invokeLater(() -> {
                    JogCard.this.busy = busy;
                    showState();
                });
            }
        };
        configuration.addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationComplete(Configuration configuration) throws Exception {
                configuration.getMachine().addListener(listener);
                SwingUtilities.invokeLater(JogCard.this::showState);
            }
        });
    }

    // ---- the "..." menu -----------------------------------------------------------------------

    /**
     * What the card leaves out: folding, the speed and the five actions when the size has no
     * room for them, the board protection switch and the actuators.
     */
    private void fillMore() {
        more.removeAll();
        JMenuItem fold = new JMenuItem(Translations.getString("JogCard.Fold") + "  (Ctrl+Shift+J)"); //$NON-NLS-1$ //$NON-NLS-2$
        fold.addActionListener(e -> setExpanded(false));
        more.add(fold);
        more.addSeparator();
        if (level == Level.Min || level == Level.Wide) {
            for (int i = 0; i < footActions.size(); i++) {
                Action action = footActions.get(i);
                JMenuItem item = new JMenuItem(footLabels.get(i));
                item.setEnabled(action.isEnabled());
                item.addActionListener(action::actionPerformed);
                more.add(item);
            }
            JMenu speed = new JMenu(Translations.getString("JogCard.Speed")); //$NON-NLS-1$
            ButtonGroup group = new ButtonGroup();
            JSlider slider = jog.getSpeedSlider();
            for (int value : new int[] { 10, 25, 50, 75, 100 }) {
                JRadioButtonMenuItem item = new JRadioButtonMenuItem(value + "%", slider.getValue() == value); //$NON-NLS-1$
                item.addActionListener(e -> slider.setValue(value));
                group.add(item);
                speed.add(item);
            }
            more.add(speed);
            more.addSeparator();
        }
        JCheckBoxMenuItem protection = new JCheckBoxMenuItem(
                Translations.getString("JogControlsPanel.Label.BoardProtection"), //$NON-NLS-1$
                jog.isBoardProtectionEnabled());
        protection.setToolTipText(Translations.getString("JogControlsPanel.Label.BoardProtection.Description")); //$NON-NLS-1$
        protection.addActionListener(e -> jog.setBoardProtectionEnabled(protection.isSelected()));
        more.add(protection);
        Machine machine = configuration.getMachine();
        if (machine == null) {
            return;
        }
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

    // ---- pieces -------------------------------------------------------------------------------

    private void foot(String key, Action action) {
        String text = Translations.getString(key);
        JButton button = Ui.button(text, null, Ui.Size.Xs, Ui.Variant.Default);
        button.setFont(Ui.weighted(11f, Tokens.FW_BUTTON));
        // They move the machine: no focus stop for a stray space bar.
        button.setFocusable(false);
        // Five across 272 pixels leaves 50 each; the sheet's 8 pixel padding does not fit CJK text.
        button.putClientProperty(FlatClientProperties.STYLE,
                button.getClientProperty(FlatClientProperties.STYLE) + "; margin: 0,2,0,2"); //$NON-NLS-1$
        button.setPreferredSize(null);
        button.addActionListener(action::actionPerformed);
        action.addPropertyChangeListener(e -> button.setEnabled(action.isEnabled()));
        button.setEnabled(action.isEnabled());
        // What the machine lacks first; with nothing lacking, the action's own reason: Recycle
        // wants a part on the nozzle and a feeder to take it back.
        Ui.whyDisabled(button, () -> {
            String reason = machineReason();
            Object own = action.getValue(Ui.WHY_DISABLED);
            return reason != null ? reason : own == null ? null : own.toString();
        });
        footButtons.add(button);
        footActions.add(action);
        footLabels.add(text);
    }

    /** Why the keys are greyed: what the machine lacks for moving. */
    private String machineReason() {
        return Ui.machineReason(configuration.getMachine());
    }

    private static String keyStyle(int size, String background, String foreground) {
        return "arc: 18; focusWidth: 0; borderWidth: 1; margin: 0,0,0,0; minimumWidth: " + size //$NON-NLS-1$
                + "; minimumHeight: " + size + "; background: " + background //$NON-NLS-1$ //$NON-NLS-2$
                + "; borderColor: $Pono.borderStrong; foreground: " + foreground //$NON-NLS-1$
                + "; hoverBackground: $Pono.surface3; pressedBackground: $Pono.accentSoft" //$NON-NLS-1$
                + "; disabledBackground: $Pono.surface2; disabledBorderColor: $Pono.border; disabledText: $Pono.textMuted" //$NON-NLS-1$
                + "; focusedBackground: null"; //$NON-NLS-1$
    }

    /** A jog key: surface-2 on a strong border, one icon; greyed with its own colours. */
    private JButton key(Action action, String icon, int size) {
        JButton key = Ui.whyDisabled(new Ui.Button(action), this::machineReason);
        key.setHideActionText(true);
        key.setIcon(Ui.icon(icon, Math.max(14, Math.round(size * 0.42f))));
        key.setFocusable(false);
        key.putClientProperty(FlatClientProperties.STYLE, keyStyle(size, "$Pono.surface2", "$Label.foreground")); //$NON-NLS-1$ //$NON-NLS-2$
        key.putClientProperty(Ui.OWN_DISABLED, Boolean.TRUE);
        key.putClientProperty(Ui.DISABLED_FOREGROUND, (java.util.function.Supplier<Color>) Ui::muted);
        fix(key, size, size);
        return key;
    }

    /** The "P" key between an axis's two arrows: park it. */
    private JButton parkKey(Action action, Metrics m) {
        JButton key = Ui.whyDisabled(new Ui.Button(action), this::machineReason);
        key.setHideActionText(true);
        key.setIcon(null);
        key.setText("P"); //$NON-NLS-1$
        key.setFocusable(false);
        key.setFont(Ui.font(m.key >= 40 ? 12f : 11f, Font.BOLD));
        key.putClientProperty(FlatClientProperties.STYLE, keyStyle(m.park, "$Pono.surface3", "$Pono.textMuted")); //$NON-NLS-1$ //$NON-NLS-2$
        key.putClientProperty(Ui.OWN_DISABLED, Boolean.TRUE);
        fix(key, m.key, m.park);
        return key;
    }

    /** The muted centre of a pad: a label, not a key, in a key's outline. */
    private static JComponent centre(String text, int size) {
        RoundedPanel c = new RoundedPanel(9, Ui::surface3, Ui::borderStrong);
        c.setLayout(new BorderLayout());
        JLabel label = Ui.t2(text);
        label.setFont(Ui.font(size >= 40 ? 12f : 11f, Font.BOLD));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        c.add(label, BorderLayout.CENTER);
        fix(c, size, size);
        return c;
    }

    private static JComponent blank(int size) {
        JPanel blank = new JPanel();
        blank.setOpaque(false);
        fix(blank, size, size);
        return blank;
    }

    /** An axis column: its letter, up, park, down. */
    private static JComponent axis(String letter, JComponent up, JComponent park, JComponent down, Metrics m) {
        JLabel label = Ui.t2(letter);
        label.setFont(Ui.font(10f, Font.BOLD));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        fix(label, m.key, m.label);
        JPanel column = stack(m.gap);
        column.add(label);
        column.add(up);
        column.add(park);
        column.add(down);
        return column;
    }

    private static void fix(JComponent c, int width, int height) {
        Dimension size = new Dimension(width, height);
        c.setPreferredSize(size);
        c.setMinimumSize(size);
        c.setMaximumSize(size);
    }

    /** Marks the one child of a row that takes the width the others leave. */
    private static final String GROW = "Pono.grow"; //$NON-NLS-1$

    private static <T extends Component> T grow(T c) {
        if (c instanceof JComponent) {
            ((JComponent) c).putClientProperty(GROW, Boolean.TRUE);
        }
        return c;
    }

    private static boolean grows(Component c) {
        return c instanceof JComponent && Boolean.TRUE.equals(((JComponent) c).getClientProperty(GROW));
    }

    /** Children top to bottom at their preferred heights and the whole width, this far apart. */
    private static JPanel stack(int gap) {
        JPanel panel = new JPanel(new LayoutManager() {
            @Override
            public void layoutContainer(Container parent) {
                Insets in = parent.getInsets();
                int y = in.top;
                int w = parent.getWidth() - in.left - in.right;
                for (Component c : parent.getComponents()) {
                    if (!c.isVisible()) {
                        continue;
                    }
                    Dimension p = c.getPreferredSize();
                    // A child held to its size - a key, a label - keeps it, at the left; the
                    // rest take the width.
                    boolean held = c.getMaximumSize().width == p.width && c.getMaximumSize().height == p.height;
                    c.setBounds(in.left, y, held ? Math.min(w, p.width) : w, p.height);
                    y += p.height + gap;
                }
            }

            @Override
            public Dimension preferredLayoutSize(Container parent) {
                Insets in = parent.getInsets();
                int w = 0, h = 0, n = 0;
                for (Component c : parent.getComponents()) {
                    if (c.isVisible()) {
                        Dimension p = c.getPreferredSize();
                        w = Math.max(w, p.width);
                        h += p.height;
                        n++;
                    }
                }
                return new Dimension(w + in.left + in.right, h + Math.max(0, n - 1) * gap + in.top + in.bottom);
            }

            @Override
            public Dimension minimumLayoutSize(Container parent) {
                return preferredLayoutSize(parent);
            }

            @Override
            public void addLayoutComponent(String name, Component comp) {
            }

            @Override
            public void removeLayoutComponent(Component comp) {
            }
        });
        panel.setOpaque(false);
        return panel;
    }

    /**
     * Children left to right at their preferred widths, this far apart and centred in the row's
     * height; the one marked to grow takes the width left over.
     */
    private static JPanel row(int gap, Component... children) {
        JPanel panel = new JPanel(new LayoutManager() {
            @Override
            public void layoutContainer(Container parent) {
                Insets in = parent.getInsets();
                int w = parent.getWidth() - in.left - in.right;
                int h = parent.getHeight() - in.top - in.bottom;
                int fixed = 0, n = 0;
                for (Component c : parent.getComponents()) {
                    if (c.isVisible()) {
                        n++;
                        if (!grows(c)) {
                            fixed += c.getPreferredSize().width;
                        }
                    }
                }
                int spare = Math.max(0, w - fixed - Math.max(0, n - 1) * gap);
                int x = in.left;
                for (Component c : parent.getComponents()) {
                    if (!c.isVisible()) {
                        continue;
                    }
                    Dimension p = c.getPreferredSize();
                    int cw = grows(c) ? spare : p.width;
                    int ch = Math.min(h, p.height);
                    c.setBounds(x, in.top + (h - ch) / 2, cw, ch);
                    x += cw + gap;
                }
            }

            @Override
            public Dimension preferredLayoutSize(Container parent) {
                Insets in = parent.getInsets();
                int w = 0, h = 0, n = 0;
                for (Component c : parent.getComponents()) {
                    if (c.isVisible()) {
                        Dimension p = c.getPreferredSize();
                        w += grows(c) ? Math.min(p.width, 60) : p.width;
                        h = Math.max(h, p.height);
                        n++;
                    }
                }
                return new Dimension(w + Math.max(0, n - 1) * gap + in.left + in.right, h + in.top + in.bottom);
            }

            @Override
            public Dimension minimumLayoutSize(Container parent) {
                return preferredLayoutSize(parent);
            }

            @Override
            public void addLayoutComponent(String name, Component comp) {
            }

            @Override
            public void removeLayoutComponent(Component comp) {
            }
        });
        panel.setOpaque(false);
        for (Component c : children) {
            panel.add(c);
        }
        return panel;
    }

    /** The folded card: a strip down the right of the image, its name written down it. */
    private final class FoldedTab extends JComponent {
        FoldedTab() {
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!autoFolded) {
                        setExpanded(true);
                    }
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(FOLDED_WIDTH, 120);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                g2.setColor(Ui.surface2());
                g2.fillRoundRect(2, 4, w - 4, h - 8, 2 * Tokens.R_MD, 2 * Tokens.R_MD);
                g2.setColor(Ui.border());
                g2.drawRoundRect(2, 4, w - 5, h - 9, 2 * Tokens.R_MD, 2 * Tokens.R_MD);
                Ui.icon(autoFolded ? "chevdown" : "chevleft", 14).paintIcon(this, g2, (w - 14) / 2, 14); //$NON-NLS-1$ //$NON-NLS-2$
                g2.setColor(autoFolded ? Ui.muted() : Ui.text2());
                g2.setFont(Ui.font(12f, Font.BOLD));
                String text = Translations.getString("JogCard.Open"); //$NON-NLS-1$
                // Written downwards, one character under the other, as Chinese is set vertically.
                int y = 44;
                java.awt.FontMetrics fm = g2.getFontMetrics();
                for (int i = 0; i < text.length(); i++) {
                    String c = text.substring(i, i + 1);
                    g2.drawString(c, (w - fm.stringWidth(c)) / 2, y + fm.getAscent());
                    y += fm.getHeight();
                }
            }
            finally {
                g2.dispose();
            }
        }
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
    }

    /** The head is named beside a tool only where there is more than one to tell apart. */
    private int heads() {
        Machine machine = configuration.getMachine();
        return machine == null ? 0 : machine.getHeads().size();
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
                if (item.getHead() != null && heads() > 1) {
                    Color muted = Ui.muted();
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
