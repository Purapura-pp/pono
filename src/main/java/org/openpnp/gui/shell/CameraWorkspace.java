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

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.openpnp.gui.components.CameraPanel;

/**
 * The camera card: the pictures in a window of their own, with the controls that belong to them
 * round it rather than over it - the camera choice, the readout and the view tools in a row along
 * the top, the manual controls down the right, and the machine panel beside the pictures where
 * there is room. See {@link CameraWorkspaceLayout} for where each goes.
 * <p>
 * This replaces a stage on which the readout, the manual controls and the tools floated over the
 * image: the readout covered the bottom left of the picture, the controls its right, and on a short
 * window the controls were cut off at the bottom.
 */
@SuppressWarnings("serial")
public class CameraWorkspace extends JPanel {
    /** Fired with the card's height when nothing is to spare above and below the pictures. */
    public static final String PROPERTY_FITTED_HEIGHT = "fittedHeight"; //$NON-NLS-1$

    private final CameraPanel video;
    private final JComponent selector;
    private final DroPanel dro;
    private final CameraToolsBar tools;
    private final JogCard jog;
    private final MachineInfoPanel info;
    private final JComponent instructions;
    private final JComponent operatorBanner;
    private final JComponent sizes;
    private final JComponent handle;
    private final JComponent stripLine;

    private CameraWorkspaceLayout.Mode mode = CameraWorkspaceLayout.Mode.Large;
    private boolean fit;
    private int wanted;
    private int fittedHeight;
    private CameraWorkspaceLayout.Result last;

    public CameraWorkspace(CameraPanel video, JComponent selector, DroPanel dro, CameraToolsBar tools,
            JogCard jog, MachineInfoPanel info, JComponent instructions, JComponent operatorBanner,
            JComponent sizes, JComponent handle) {
        this.video = video;
        this.selector = selector;
        this.dro = dro;
        this.tools = tools;
        this.jog = jog;
        this.info = info;
        this.instructions = instructions;
        this.operatorBanner = operatorBanner;
        this.sizes = sizes;
        this.handle = handle;
        this.stripLine = jog.getStripLine();
        setOpaque(true);
        setBackground(Ui.surface());
        setLayout(new WorkspaceLayout());
        for (Component c : new Component[] { selector, dro, tools, instructions, operatorBanner, video, info, jog,
                sizes, stripLine, handle }) {
            add(c);
        }
        video.addPropertyChangeListener(CameraPanel.PROPERTY_FIT, e -> revalidate());
        jog.addPropertyChangeListener(JogCard.PROPERTY_COLUMN, e -> revalidate());
        applyMode();
    }

    public CameraWorkspaceLayout.Mode getMode() {
        return mode;
    }

    public void setMode(CameraWorkspaceLayout.Mode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            applyMode();
            revalidate();
            repaint();
        }
    }

    /**
     * Whether the room above and below the pictures is the page's: true for the large camera in
     * the window's split, false in full screen, in a window of its own and in production mode.
     */
    public void setFit(boolean fit) {
        if (this.fit != fit) {
            this.fit = fit;
            revalidate();
        }
    }

    /**
     * The height the page would give the card if its pictures left none of it: the fitted height
     * is worked out from this, not from the height the card has.
     */
    public void setWantedHeight(int wanted) {
        if (this.wanted != wanted) {
            this.wanted = wanted;
            revalidate();
        }
    }

    /** Of the wanted height, what the pictures and the manual controls use. */
    public int getFittedHeight() {
        return fittedHeight;
    }

    private void applyMode() {
        boolean large = mode == CameraWorkspaceLayout.Mode.Large;
        boolean strip = mode == CameraWorkspaceLayout.Mode.Strip;
        selector.setVisible(large || strip);
        tools.setVisible(large);
        jog.setVisible(large);
        sizes.setVisible(strip);
        stripLine.setVisible(strip);
        handle.setVisible(strip);
        if (!large) {
            info.setVisible(false);
            // The strip shows the readout at its left, never in the manual controls.
            if (dro.getParent() != this) {
                jog.setReadout(null);
                add(dro);
            }
        }
        dro.setVisible(large || strip);
        if (strip) {
            dro.setForm(DroPanel.Form.Grid);
        }
    }

    /** The banner along the top: the production banner in production mode, else a wizard's instructions. */
    private JComponent banner() {
        if (mode == CameraWorkspaceLayout.Mode.Operator) {
            return operatorBanner.isVisible() ? operatorBanner : null;
        }
        return mode == CameraWorkspaceLayout.Mode.Large && instructions.isVisible() ? instructions : null;
    }

    private CameraWorkspaceLayout.Input input() {
        CameraWorkspaceLayout.Input in = new CameraWorkspaceLayout.Input();
        in.width = getWidth();
        in.height = getHeight();
        in.wanted = wanted;
        in.mode = mode;
        in.fit = fit && mode == CameraWorkspaceLayout.Mode.Large;
        JComponent banner = banner();
        if (banner != null) {
            in.banner = Math.min(banner.getPreferredSize().height, Math.max(0, getHeight() / 2));
        }
        in.selector = selector.getPreferredSize().width;
        in.droRow = dro.widthFor(DroPanel.Form.Row);
        in.droBare = dro.widthFor(DroPanel.Form.RowBare);
        in.droTight = dro.widthFor(DroPanel.Form.RowTight);
        in.droGridWidth = dro.widthFor(DroPanel.Form.Grid);
        in.droGridHeight = dro.heightFor(DroPanel.Form.Grid);
        in.tools = tools.widthFor(false);
        in.toolsCompact = tools.widthFor(true);
        in.folded = !jog.isExpanded();
        in.infoHeight = info.getPreferredSize().height;
        in.stripLeftWidth = Math.max(selector.getPreferredSize().width, dro.widthFor(DroPanel.Form.Grid));
        in.stripRightWidth = Math.max(sizes.getPreferredSize().width,
                Math.max(stripLine.getPreferredSize().width, handle.getPreferredSize().width));
        in.pictures = (w, h) -> video.fittedSize(w, h);
        return in;
    }

    /**
     * What the plan changes in the tree - the readout moving into the manual controls and back,
     * their size, the tools' form - done after the layout rather than during it.
     */
    private void apply(CameraWorkspaceLayout.Result r) {
        if (mode == CameraWorkspaceLayout.Mode.Large) {
            if (r.droInColumn && dro.getParent() == this) {
                remove(dro);
                jog.setReadout(dro);
            }
            else if (!r.droInColumn && dro.getParent() != this) {
                jog.setReadout(null);
                add(dro);
            }
            dro.setForm(r.droForm);
            tools.setCompact(r.toolsCompact);
            jog.setLevel(r.level);
            jog.setAutoFolded(r.autoFolded);
        }
        revalidate();
        repaint();
    }

    private boolean differs(CameraWorkspaceLayout.Result r) {
        if (mode != CameraWorkspaceLayout.Mode.Large) {
            return false;
        }
        return r.droInColumn != (dro.getParent() != this) || (!r.droInColumn && r.droForm != dro.getForm())
                || r.toolsCompact != tools.isCompact() || r.level != jog.getLevel() || r.autoFolded != jog.isAutoFolded();
    }

    private final class WorkspaceLayout implements LayoutManager {
        @Override
        public void layoutContainer(Container parent) {
            if (getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            CameraWorkspaceLayout.Result r = CameraWorkspaceLayout.lay(input());
            last = r;
            JComponent banner = banner();
            for (JComponent b : new JComponent[] { instructions, operatorBanner }) {
                if (b != banner) {
                    b.setBounds(0, 0, 0, 0);
                }
            }
            if (banner != null && r.banner != null) {
                banner.setBounds(r.banner);
            }
            video.setBounds(r.video);
            if (mode == CameraWorkspaceLayout.Mode.Large) {
                centre(selector, r.selector);
                centre(tools, r.tools);
                if (r.dro != null && dro.getParent() == CameraWorkspace.this) {
                    centre(dro, r.dro);
                }
                jog.setBounds(r.column);
                info.setVisible(r.info != null);
                if (r.info != null) {
                    info.setBounds(r.info);
                }
            }
            else if (mode == CameraWorkspaceLayout.Mode.Strip) {
                Dimension s = selector.getPreferredSize();
                selector.setBounds(12, 10, s.width, s.height);
                Dimension d = dro.getPreferredSize();
                dro.setBounds(14, 10 + s.height + 10, d.width, d.height);
                int right = r.stripRight.x + r.stripRight.width - 12;
                int y = 10;
                for (JComponent c : new JComponent[] { sizes, stripLine, handle }) {
                    Dimension p = c.getPreferredSize();
                    c.setBounds(right - p.width, y, p.width, p.height);
                    y += p.height + 8;
                }
            }
            if (r.fittedHeight != fittedHeight) {
                int was = fittedHeight;
                fittedHeight = r.fittedHeight;
                SwingUtilities.invokeLater(() -> firePropertyChange(PROPERTY_FITTED_HEIGHT, was, fittedHeight));
            }
            if (differs(r)) {
                SwingUtilities.invokeLater(() -> {
                    CameraWorkspaceLayout.Result now = last;
                    if (now != null && differs(now)) {
                        apply(now);
                    }
                });
            }
        }

        /** A part at its preferred height, centred in its band of the tool row. */
        private void centre(Component c, Rectangle band) {
            int h = Math.min(band.height, c.getPreferredSize().height);
            c.setBounds(band.x, band.y + (band.height - h) / 2, band.width, h);
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return new Dimension(0, 0);
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return new Dimension(0, 0);
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }
    }

    /**
     * The card: its colour inside 14 pixel corners, the window's colour outside them, a hairline
     * round it, and the lines under the tool row, down the manual controls and between the
     * strip's parts.
     */
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            int w = getWidth(), h = getHeight();
            Container parent = getParent();
            g2.setColor(parent != null ? parent.getBackground() : Ui.bg());
            g2.fillRect(0, 0, w, h);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float arc = 2 * Tokens.R_LG;
            g2.setColor(getBackground());
            g2.fill(new RoundRectangle2D.Float(0, 0, w, h, arc, arc));
            g2.setColor(Ui.border());
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, arc, arc));
            CameraWorkspaceLayout.Result r = last;
            if (r == null) {
                return;
            }
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            if (mode == CameraWorkspaceLayout.Mode.Large) {
                g2.fillRect(1, r.barLine, w - 2, 1);
                if (r.columnLine >= 0) {
                    g2.fillRect(r.columnLine, r.barLine + 1, 1, h - r.barLine - 2);
                }
            }
            else if (mode == CameraWorkspaceLayout.Mode.Strip && r.stripLeft != null) {
                g2.fillRect(r.stripLeft.width, 1, 1, h - 2);
                g2.fillRect(r.stripRight.x - 1, 1, 1, h - 2);
            }
        }
        finally {
            g2.dispose();
        }
    }
}
