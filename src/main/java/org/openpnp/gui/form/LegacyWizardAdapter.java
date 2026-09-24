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

package org.openpnp.gui.form;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;

import org.openpnp.gui.shell.Tokens;
import org.openpnp.gui.shell.Ui;

import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.ConstantSize;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpec;
import com.jgoodies.forms.layout.RowSpec;
import com.jgoodies.forms.layout.Sizes;

/**
 * Makes an old wizard sit in the properties column until its declarative form replaces it. The
 * wizards were drawn for a wide panel under the camera: fixed column widths and row heights,
 * four-column grids, titled borders around every group. In a 500 pixel column that meant rows
 * wider than the column and inputs cut off at the bottom. Shown in the column, a wizard gets:
 * <ul>
 * <li>its titled borders drawn as section headings, the stylesheet's .sec;</li>
 * <li>its fixed column widths and row heights relaxed to what the content needs;</li>
 * <li>a four-column grid that is still too wide folded into two, the second pair of each row
 * under the first;</li>
 * <li>the label beside a check box made to tick it.</li>
 * </ul>
 * Enum values in combo boxes are shown by their display names everywhere already. A few wizards
 * whose layout is their content are left as they are.
 * <p>
 * Temporary: it goes when the last wizard has been redone as a form.
 */
public final class LegacyWizardAdapter {
    private LegacyWizardAdapter() {
    }

    /** Laid out for what they show, a console, and left alone. */
    static final Set<String> EXCEPTIONS = Set.of("GcodeDriverConsole"); //$NON-NLS-1$

    private static final String ADAPTED = "Pono.legacyAdapted"; //$NON-NLS-1$
    /** A spec this small is a gap between columns, not a column to relax. */
    private static final int GAP = 16;
    /** The width left for a wizard in the properties column. */
    static final int WIDTH = Tokens.W_SIDE - 40;

    public static void adapt(JComponent wizard) {
        if (wizard == null || wizard.getClientProperty(ADAPTED) != null
                || EXCEPTIONS.contains(wizard.getClass().getSimpleName())
                || wizard instanceof FormWizard) {
            return;
        }
        wizard.putClientProperty(ADAPTED, Boolean.TRUE);
        walk(wizard);
        fitWidth(wizard);
    }

    // ---- the column's width -------------------------------------------------------------------

    /**
     * The wizard's scroll pane gives its content the column's width instead of scrolling it
     * sideways: a grid of default sized columns then gives each field what is left, down to the
     * field's minimum, which is what the column needs of a form drawn for a wider panel.
     */
    static void fitWidth(JComponent wizard) {
        javax.swing.JScrollPane scroll = firstScrollPane(wizard);
        if (scroll == null) {
            return;
        }
        Component view = scroll.getViewport().getView();
        if (!(view instanceof javax.swing.JPanel) || view instanceof WidthTracking) {
            return;
        }
        scroll.setViewportView(new WidthTracking(view));
        scroll.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    }

    /** The wizard's own scroll pane: the nearest one, looked for level by level. */
    private static javax.swing.JScrollPane firstScrollPane(JComponent wizard) {
        java.util.ArrayDeque<Component> queue = new java.util.ArrayDeque<>();
        queue.add(wizard);
        while (!queue.isEmpty()) {
            Component c = queue.poll();
            if (c instanceof javax.swing.JScrollPane) {
                return (javax.swing.JScrollPane) c;
            }
            if (c instanceof java.awt.Container) {
                for (Component child : ((java.awt.Container) c).getComponents()) {
                    queue.add(child);
                }
            }
        }
        return null;
    }

    /** A scroll pane's content held at the viewport's width, scrolling only up and down. */
    @SuppressWarnings("serial")
    public static final class WidthTracking extends javax.swing.JPanel implements javax.swing.Scrollable {
        public WidthTracking(Component view) {
            super(new java.awt.BorderLayout());
            setOpaque(false);
            add(view, java.awt.BorderLayout.CENTER);
        }

        @Override
        public java.awt.Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) {
            return orientation == javax.swing.SwingConstants.VERTICAL ? visible.height : visible.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            // Filling the viewport when there is less than it holds, as a plain panel does.
            return getParent() instanceof javax.swing.JViewport
                    && getParent().getHeight() > getPreferredSize().height;
        }
    }

    private static void walk(Component c) {
        if (c instanceof JComponent) {
            JComponent jc = (JComponent) c;
            section(jc);
            if (jc.getLayout() instanceof FormLayout) {
                FormLayout layout = (FormLayout) jc.getLayout();
                relax(jc, layout);
                if (jc.getPreferredSize().width > WIDTH) {
                    fold(jc, layout);
                }
                linkCheckBoxLabels(jc);
            }
        }
        if (c instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) c).getComponents()) {
                walk(child);
            }
        }
    }

    // ---- titled borders -----------------------------------------------------------------------

    static void section(JComponent c) {
        Border border = c.getBorder();
        if (border instanceof TitledBorder) {
            c.setBorder(new SectionBorder(((TitledBorder) border).getTitle()));
        }
        else if (border instanceof CompoundBorder
                && ((CompoundBorder) border).getOutsideBorder() instanceof TitledBorder) {
            CompoundBorder compound = (CompoundBorder) border;
            c.setBorder(new CompoundBorder(
                    new SectionBorder(((TitledBorder) compound.getOutsideBorder()).getTitle()),
                    compound.getInsideBorder()));
        }
    }

    /**
     * A titled border as the stylesheet's section heading: a hairline above, the title in the
     * section weight, the content inset as a section's is.
     */
    @SuppressWarnings("serial")
    static final class SectionBorder extends AbstractBorder {
        final String title;

        SectionBorder(String title) {
            this.title = title == null ? "" : title; //$NON-NLS-1$
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(title.isEmpty() ? 12 : 40, Tokens.PAD_SECTION, 12, Tokens.PAD_SECTION);
            return insets;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(Ui.border());
                g2.drawLine(x, y, x + width, y);
                if (!title.isEmpty()) {
                    g2.setFont(Ui.weighted(Tokens.FS_SECTION, Tokens.FW_SECTION));
                    g2.setColor(Ui.text());
                    g2.drawString(title, x + Tokens.PAD_SECTION, y + 14 + g2.getFontMetrics().getAscent());
                }
            }
            finally {
                g2.dispose();
            }
        }
    }

    // ---- fixed sizes --------------------------------------------------------------------------

    /** {@code max(70dlu;default)}: at least a fixed width, which seven columns of add up. */
    static boolean hasFixedLowerBound(FormSpec spec, Component c) {
        if (!(spec.getSize() instanceof com.jgoodies.forms.layout.BoundedSize)) {
            return false;
        }
        com.jgoodies.forms.layout.Size lower = ((com.jgoodies.forms.layout.BoundedSize) spec.getSize()).getLowerBound();
        return lower instanceof ConstantSize && ((ConstantSize) lower).getPixelSize(c) > GAP;
    }

    static boolean isGap(FormSpec spec, Component c) {
        return spec.getSize() instanceof ConstantSize
                && ((ConstantSize) spec.getSize()).getPixelSize(c) <= GAP;
    }

    /**
     * Fixed widths and heights become what the content asks for; gaps stay as they are. A
     * preferred width becomes a default one, which gives way to the minimum when the column is
     * narrower than the form.
     */
    static void relax(JComponent panel, FormLayout layout) {
        for (int i = 1; i <= layout.getColumnCount(); i++) {
            ColumnSpec spec = layout.getColumnSpec(i);
            if ((spec.getSize() instanceof ConstantSize && !isGap(spec, panel)) || spec.getSize() == Sizes.PREFERRED
                    || hasFixedLowerBound(spec, panel)) {
                layout.setColumnSpec(i, new ColumnSpec(spec.getDefaultAlignment(), Sizes.DEFAULT,
                        spec.getResizeWeight()));
            }
        }
        for (int i = 1; i <= layout.getRowCount(); i++) {
            RowSpec spec = layout.getRowSpec(i);
            if (spec.getSize() instanceof ConstantSize && !isGap(spec, panel)) {
                layout.setRowSpec(i, new RowSpec(spec.getDefaultAlignment(), Sizes.DEFAULT,
                        spec.getResizeWeight()));
            }
        }
        panel.revalidate();
    }

    /**
     * A grid of two label and field pairs a row becomes one pair a row, the second pair under the
     * first. Grids whose components span the pairs, or several rows, are left as they are, and so
     * is a grid whose label columns hold anything but labels: a row of a label, X, Y and a button
     * is one field, and folded it would put Y where a label goes.
     */
    static boolean fold(JComponent panel, FormLayout layout) {
        List<Integer> content = new ArrayList<>();
        for (int i = 1; i <= layout.getColumnCount(); i++) {
            if (!isGap(layout.getColumnSpec(i), panel)) {
                content.add(i);
            }
        }
        if (content.size() < 4) {
            return false;
        }
        int a = content.get(0);
        int b = content.get(1);
        int c = content.get(2);
        int d = content.get(3);
        Component[] components = panel.getComponents();
        List<CellConstraints> constraints = new ArrayList<>();
        for (Component component : components) {
            CellConstraints cc = layout.getConstraints(component);
            int first = cc.gridX;
            int lastColumn = cc.gridX + cc.gridWidth - 1;
            boolean firstPair = first >= a && lastColumn < c;
            boolean secondPair = first >= c && lastColumn <= d;
            if (cc.gridHeight > 1 || !(firstPair || secondPair)) {
                return false;
            }
            if ((first == a || first == c) && !(component instanceof JLabel)) {
                return false;
            }
            constraints.add(cc);
        }
        FormLayout folded = new FormLayout();
        folded.appendColumn(layout.getColumnSpec(a));
        folded.appendColumn(ColumnSpec.decode("6px")); //$NON-NLS-1$
        folded.appendColumn(new ColumnSpec(ColumnSpec.FILL, Sizes.DEFAULT, 1.0));
        for (int r = 1; r <= layout.getRowCount(); r++) {
            folded.appendRow(layout.getRowSpec(r));
            folded.appendRow(layout.getRowSpec(r));
        }
        panel.removeAll();
        panel.setLayout(folded);
        for (int i = 0; i < components.length; i++) {
            CellConstraints cc = constraints.get(i);
            boolean second = cc.gridX >= c;
            int column = (cc.gridX == a || cc.gridX == c) ? 1 : 3;
            int width = cc.gridWidth > 1 ? 3 : 1;
            CellConstraints moved = new CellConstraints(column, 2 * (cc.gridY - 1) + (second ? 2 : 1),
                    Math.min(width, 4 - column), 1, cc.hAlign, cc.vAlign);
            panel.add(components[i], moved);
        }
        panel.revalidate();
        return true;
    }

    // ---- check boxes --------------------------------------------------------------------------

    /**
     * A check box with no words of its own and a label to its left on the same row: the label is
     * made to tick it, as the words of a check box do.
     */
    static void linkCheckBoxLabels(JComponent panel) {
        if (!(panel.getLayout() instanceof FormLayout)) {
            return;
        }
        FormLayout layout = (FormLayout) panel.getLayout();
        for (Component component : panel.getComponents()) {
            if (!(component instanceof JCheckBox)) {
                continue;
            }
            JCheckBox box = (JCheckBox) component;
            if (box.getText() != null && !box.getText().isBlank()) {
                continue;
            }
            CellConstraints at = layout.getConstraints(box);
            JLabel nearest = null;
            int nearestColumn = 0;
            for (Component other : panel.getComponents()) {
                if (other instanceof JLabel) {
                    CellConstraints cc = layout.getConstraints(other);
                    if (cc.gridY == at.gridY && cc.gridX < at.gridX && cc.gridX > nearestColumn) {
                        nearest = (JLabel) other;
                        nearestColumn = cc.gridX;
                    }
                }
            }
            if (nearest != null && nearest.getLabelFor() == null) {
                JLabel label = nearest;
                label.setLabelFor(box);
                label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                label.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (box.isEnabled() && box.isShowing()) {
                            box.doClick();
                        }
                    }
                });
            }
        }
    }
}
