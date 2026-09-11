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
import java.awt.Dimension;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

import org.openpnp.Translations;
import org.openpnp.gui.shell.PropertySheetPresenter.Result;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.spi.PropertySheetHolder;

/**
 * The one column that shows the properties of whatever is selected, wherever it was selected.
 * <p>
 * Each table used to carry its own copy of this below itself, behind a split divider: nine panels,
 * nine dividers to drag, nine remembered positions, and a property sheet that got whatever height
 * was left over at the bottom of the window. There is one of them now, down the right hand side,
 * and it says what it is showing - which the panels never did, because the answer was always
 * "whatever is selected in the table above".
 * <p>
 * The sheets are tabs in this first version, one per property sheet, each keeping its own Apply and
 * Reset. Grouping them into an accordion with one Apply for the lot would change what those
 * buttons mean, and that is a decision about the wizards rather than about the layout.
 */
@SuppressWarnings("serial")
public class InspectorPanel extends JPanel {
    /** Wide enough for the widest wizard's label column without crowding the tables. */
    public static final int PREFERRED_WIDTH = 380;

    /** What is left when it is folded away: enough for the button that brings it back. */
    private static final int COLLAPSED_WIDTH = 34;

    private final JTabbedPane sheets = new JTabbedPane(JTabbedPane.TOP);

    private final PropertySheetPresenter presenter = new PropertySheetPresenter(sheets);

    private final JLabel iconLabel = new JLabel();

    private final JLabel nameLabel = new JLabel();

    private final JLabel typeLabel = new JLabel();

    private final JPanel header = new JPanel(new BorderLayout(8, 0));

    private final JPanel body = new JPanel(new BorderLayout());

    private final JLabel nothingSelected =
            new JLabel(Translations.getString("InspectorPanel.NothingSelected"), //$NON-NLS-1$
                    SwingConstants.CENTER);

    private boolean collapsed;

    public InspectorPanel() {
        setLayout(new BorderLayout());
        Color border = UIManager.getColor("Pono.borderStrong"); //$NON-NLS-1$
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0,
                border != null ? border : getBackground().darker()));

        // Underlined tabs rather than the card tabs used elsewhere: these sit directly under the
        // name of the thing being edited, and a second row of boxes there reads as a second panel.
        sheets.putClientProperty("JTabbedPane.tabType", "underlined"); //$NON-NLS-1$ //$NON-NLS-2$
        sheets.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        Font font = UIManager.getFont("Label.font"); //$NON-NLS-1$
        nameLabel.setFont(font.deriveFont(Font.BOLD, font.getSize2D() * 1.1f));
        typeLabel.setFont(font.deriveFont(font.getSize2D() * 0.85f));
        Color secondary = UIManager.getColor("Pono.textSecondary"); //$NON-NLS-1$
        if (secondary != null) {
            typeLabel.setForeground(secondary);
        }
        Color muted = UIManager.getColor("Pono.textMuted"); //$NON-NLS-1$
        if (muted != null) {
            nothingSelected.setForeground(muted);
        }

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(nameLabel);
        titles.add(typeLabel);

        header.setBorder(new EmptyBorder(8, 10, 8, 6));
        Color surface = UIManager.getColor("Pono.surface2"); //$NON-NLS-1$
        if (surface != null) {
            header.setBackground(surface);
        }
        header.add(iconLabel, BorderLayout.WEST);
        header.add(titles, BorderLayout.CENTER);
        header.add(collapseButton("\u203a"), BorderLayout.EAST); //$NON-NLS-1$
        add(header, BorderLayout.NORTH);

        body.add(nothingSelected, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        // The sliver left behind when the panel is folded away, holding the button that unfolds it.
        strip.add(collapseButton("\u2039"), BorderLayout.NORTH); //$NON-NLS-1$
        strip.setVisible(false);
        add(strip, BorderLayout.WEST);

        clear();
    }

    private JButton collapseButton(String arrow) {
        JButton button = new JButton(toggleCollapsedAction);
        button.setText(arrow);
        button.setMargin(new Insets(2, 4, 2, 4));
        button.setFocusable(false);
        return button;
    }

    /**
     * Show one thing's properties.
     * 
     * @param holder    What to show. Null clears the panel.
     * @param container Where the wizards report back to, which is still the panel that owns the
     *                  table: a wizard completing may have to refresh a row or a board's outline.
     * @param title     The name as the table shows it, which is not always the holder's own title.
     * @return Cancelled when the user would not let go of unapplied edits, in which case the
     *         caller should put its selection back where it was.
     */
    public Result show(PropertySheetHolder holder, WizardContainer container, String title,
            Icon icon) {
        Result result = presenter.show(holder, container, title);
        if (result != Result.Shown) {
            return result;
        }
        if (holder == null || sheets.getTabCount() == 0) {
            clear();
            return result;
        }
        iconLabel.setIcon(icon != null ? icon : holder.getPropertySheetHolderIcon());
        nameLabel.setText(title != null ? title : holder.getPropertySheetHolderTitle());
        typeLabel.setText(typeOf(holder));
        setBody(sheets);
        return result;
    }

    /** Show nothing, and say so rather than leaving an empty tab strip. */
    public void clear() {
        iconLabel.setIcon(null);
        nameLabel.setText(Translations.getString("InspectorPanel.Title")); //$NON-NLS-1$
        typeLabel.setText(" "); //$NON-NLS-1$
        setBody(nothingSelected);
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        boolean was = this.collapsed;
        this.collapsed = collapsed;
        header.setVisible(!collapsed);
        body.setVisible(!collapsed);
        strip.setVisible(collapsed);
        revalidate();
        repaint();
        firePropertyChange("collapsed", was, collapsed); //$NON-NLS-1$
    }

    public final Action toggleCollapsedAction = new AbstractAction() {
        {
            putValue(SHORT_DESCRIPTION,
                    Translations.getString("InspectorPanel.Action.Toggle.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            setCollapsed(!collapsed);
        }
    };

    private final JPanel strip = new JPanel(new BorderLayout());

    /**
     * What kind of thing is being edited, under its name. A feeder is a strip feeder or a tray
     * feeder, and which one it is decides what the sheets below even contain.
     */
    private String typeOf(PropertySheetHolder holder) {
        String name = holder.getClass().getSimpleName();
        return name.startsWith("Reference") ? name.substring("Reference".length()) : name; //$NON-NLS-1$ //$NON-NLS-2$
    }

    private void setBody(Component content) {
        if (body.getComponentCount() == 1 && body.getComponent(0) == content) {
            return;
        }
        body.removeAll();
        body.add(content, BorderLayout.CENTER);
        body.revalidate();
        body.repaint();
    }

    public PropertySheetPresenter getPresenter() {
        return presenter;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(collapsed ? COLLAPSED_WIDTH : PREFERRED_WIDTH, size.height);
    }
}
