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
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import org.openpnp.ConfigurationListener;
import org.openpnp.Translations;
import org.openpnp.model.Configuration;
import org.openpnp.spi.Machine;
import org.openpnp.spi.MachineListener;

/**
 * The machine's state as one word and one colour, in the top bar.
 * <p>
 * Before this, the only indication was the power icon on the machine controls, which says whether
 * the machine is switched on but not whether it has been homed - and an un-homed machine refuses
 * most of what the operator is about to ask it for. Clicking the chip switches the machine on or
 * off, which is the action the state most often calls for.
 */
@SuppressWarnings("serial")
public class MachineStateChip extends JLabel {
    /** The stylesheet's .chip: 26 high, an 8 pixel LED, 12 pixel semibold text. */
    private static final int HEIGHT = 26;
    private static final int DOT_SIZE = 8;
    /** How much of the state colour is left in the chip's own background: rgba(.16). */
    private static final int BACKGROUND_ALPHA = 41;
    /** And in its border: rgba(.35). */
    private static final int BORDER_ALPHA = 89;

    private enum State {
        Disconnected("TopBar.MachineState.Disconnected", "Pono.textMuted", Color.GRAY), //$NON-NLS-1$ //$NON-NLS-2$
        NotHomed("TopBar.MachineState.NotHomed", "Pono.statusWarn", new Color(0xf5b840)), //$NON-NLS-1$ //$NON-NLS-2$
        Ready("TopBar.MachineState.Ready", "Pono.statusOk", new Color(0x34c77b)), //$NON-NLS-1$ //$NON-NLS-2$
        Running("TopBar.MachineState.Running", "Pono.statusRun", new Color(0x38bdf8)); //$NON-NLS-1$ //$NON-NLS-2$

        private final String textKey;
        private final String colorKey;
        private final Color fallback;

        State(String textKey, String colorKey, Color fallback) {
            this.textKey = textKey;
            this.colorKey = colorKey;
            this.fallback = fallback;
        }

        /**
         * The Pono.* keys only exist in the Pono themes. A user on FlatLaf Dark or the system look
         * and feel still needs a chip they can read, hence the literal fallback.
         */
        Color color() {
            Color color = UIManager.getColor(colorKey);
            return color != null ? color : fallback;
        }
    }

    /** Null until the configuration is complete: the window is built before the machine exists. */
    private Machine machine;
    private final Action toggleAction;
    private boolean busy;
    /** Why the machine last went off or failed to come on, as the driver said; null if it did not say. */
    private volatile String reason;

    public MachineStateChip(Configuration configuration, Action toggleAction) {
        this.toggleAction = toggleAction;
        setOpaque(false);
        setBorder(new EmptyBorder(new Insets(0, 10, 0, 10)));
        setIconTextGap(6);
        setFont(Ui.font(12f, java.awt.Font.BOLD));
        setIcon(new StateDot());
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(Translations.getString("TopBar.MachineState.toolTipText")); //$NON-NLS-1$
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (MachineStateChip.this.toggleAction.isEnabled()) {
                    MachineStateChip.this.toggleAction.actionPerformed(
                            new ActionEvent(MachineStateChip.this, ActionEvent.ACTION_PERFORMED, null));
                }
            }
        });
        configuration.addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationComplete(Configuration configuration) throws Exception {
                machine = configuration.getMachine();
                machine.addListener(machineListener);
                refresh();
            }
        });
        refresh();
    }

    /**
     * Machine events arrive on the machine task thread, so every one of them is bounced to the
     * event thread before it touches this component.
     */
    private final MachineListener machineListener = new MachineListener.Adapter() {
        @Override
        public void machineEnabled(Machine machine) {
            reason = null;
            refreshLater();
        }

        @Override
        public void machineDisabled(Machine machine, String reason) {
            MachineStateChip.this.reason = reason;
            refreshLater();
        }

        @Override
        public void machineEnableFailed(Machine machine, String reason) {
            MachineStateChip.this.reason = reason;
            refreshLater();
        }

        @Override
        public void machineHomed(Machine machine, boolean isHomed) {
            refreshLater();
        }

        @Override
        public void machineBusy(Machine machine, boolean busy) {
            MachineStateChip.this.busy = busy;
            refreshLater();
        }
    };

    private void refreshLater() {
        SwingUtilities.invokeLater(this::refresh);
    }

    private void refresh() {
        State state = state();
        setText(Translations.getString(state.textKey));
        setForeground(state.color());
        String toolTip = Translations.getString("TopBar.MachineState.toolTipText"); //$NON-NLS-1$
        if (state == State.Disconnected && reason != null && !reason.trim().isEmpty()) {
            toolTip = "<html>" + toolTip + "<br>" //$NON-NLS-1$ //$NON-NLS-2$
                    + String.format(Translations.getString("TopBar.MachineState.Reason"), //$NON-NLS-1$
                            reason.trim().replace("<", "&lt;")) //$NON-NLS-1$ //$NON-NLS-2$
                    + "</html>"; //$NON-NLS-1$
        }
        setToolTipText(toolTip);
        revalidate();
        repaint();
    }

    private State state() {
        if (busy) {
            return State.Running;
        }
        if (machine == null || !machine.isEnabled()) {
            return State.Disconnected;
        }
        return machine.isHomed() ? State.Ready : State.NotHomed;
    }

    @Override
    public java.awt.Dimension getPreferredSize() {
        java.awt.Dimension size = super.getPreferredSize();
        size.height = HEIGHT;
        return size;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Color color = state().color();
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), BACKGROUND_ALPHA));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), BORDER_ALPHA));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
        }
        finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }

    private final class StateDot implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color color = state().color();
                if (state() == State.Ready) {
                    g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), BORDER_ALPHA));
                    g2.fillOval(x - 3, y - 3, DOT_SIZE + 6, DOT_SIZE + 6);
                }
                g2.setColor(color);
                g2.fillOval(x, y, DOT_SIZE, DOT_SIZE);
            }
            finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return DOT_SIZE;
        }

        @Override
        public int getIconHeight() {
            return DOT_SIZE;
        }
    }
}
