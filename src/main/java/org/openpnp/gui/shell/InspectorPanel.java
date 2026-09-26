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
import java.awt.Dimension;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
import javax.swing.border.EmptyBorder;

import org.openpnp.Translations;
import org.openpnp.gui.shell.PropertySheetPresenter.Result;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.spi.PropertySheetHolder;
import org.openpnp.spi.PropertySheetHolder.PropertySheet;

import com.formdev.flatlaf.FlatClientProperties;

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
public class InspectorPanel extends RoundedPanel {
    /**
     * The width to start with. The wizards this hosts were drawn for the full width of the window,
     * and at 380 pixels the strip feeder's had its right hand columns cut off; the user can drag
     * it from here.
     */
    public static final int PREFERRED_WIDTH = 500;

    /** What is left when it is folded away: enough for the button that brings it back. */
    public static final int COLLAPSED_WIDTH = 34;

    /**
     * A folded column dragged wider than this is unfolded; dragged less, it goes back to its sliver.
     * Folded at any other width it shows nothing but the unfold button in its corner.
     */
    public static final int UNFOLD_DRAG_WIDTH = 100;

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
        // The stylesheet's .side: a card of its own, 14 pixel corners, beside the page's.
        super(Tokens.R_LG, Ui::surface, Ui::border);
        setLayout(new BorderLayout());

        // Underlined tabs rather than the card tabs used elsewhere: these sit directly under the
        // name of the thing being edited, and a second row of boxes there reads as a second panel.
        // Tabs that do not fit go behind the button at the end of the row, as the stylesheet's
        // "more" tab has them, rather than scrolling out of sight.
        sheets.putClientProperty("JTabbedPane.tabType", "underlined"); //$NON-NLS-1$ //$NON-NLS-2$
        sheets.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        sheets.putClientProperty(FlatClientProperties.TABBED_PANE_TABS_POPUP_POLICY,
                FlatClientProperties.TABBED_PANE_POLICY_AS_NEEDED);
        sheets.putClientProperty(FlatClientProperties.TABBED_PANE_SCROLL_BUTTONS_POLICY,
                FlatClientProperties.TABBED_PANE_POLICY_NEVER);

        // The stylesheet's .side .hdr: 14 16 12 padding, the icon in a 38 pixel accent-soft
        // square with 10 pixel corners, the name at 15/700 over the kind of thing in 12 muted.
        nameLabel.setFont(Ui.weighted(Tokens.FS_TITLE, Tokens.FW_TITLE));
        typeLabel.setFont(Ui.font(Tokens.FS_SMALL));
        typeLabel.setForeground(Ui.muted());
        nothingSelected.setForeground(Ui.muted());
        nothingSelected.setFont(Ui.font(12.5f));

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(nameLabel);
        titles.add(javax.swing.Box.createVerticalStrut(1));
        titles.add(typeLabel);
        // A long name gives way with an ellipsis, and the whole of it is the tooltip.
        nameLabel.setMinimumSize(new Dimension(40, 10));
        typeLabel.setMinimumSize(new Dimension(40, 10));

        header.setOpaque(false);
        header.setLayout(new BorderLayout(12, 0));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.border()),
                new EmptyBorder(14, 16, 12, 12)));
        iconBox.setLayout(new BorderLayout());
        iconBox.setPreferredSize(new Dimension(38, 38));
        iconLabel.setForeground(Ui.accent());
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconBox.add(iconLabel, BorderLayout.CENTER);
        JPanel iconHolder = new JPanel(new java.awt.GridBagLayout());
        iconHolder.setOpaque(false);
        iconHolder.add(iconBox);
        header.add(iconHolder, BorderLayout.WEST);
        header.add(titles, BorderLayout.CENTER);
        JPanel headerTools = new JPanel();
        headerTools.setOpaque(false);
        headerTools.setLayout(new BoxLayout(headerTools, BoxLayout.X_AXIS));
        moreButton.setVisible(false);
        headerTools.add(moreButton);
        headerTools.add(javax.swing.Box.createHorizontalStrut(4));
        JButton fold = Ui.iconButton(Ui.iconSm("chevright"), Ui.Size.Xs, Ui.Variant.Ghost, //$NON-NLS-1$
                Translations.getString("InspectorPanel.Action.Toggle.Description")); //$NON-NLS-1$
        fold.addActionListener(toggleCollapsedAction);
        headerTools.add(fold);
        JPanel headerToolsHolder = new JPanel(new java.awt.GridBagLayout());
        headerToolsHolder.setOpaque(false);
        headerToolsHolder.add(headerTools);
        header.add(headerToolsHolder, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        body.setOpaque(false);
        body.add(nothingSelected, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        // The stylesheet's .side .foot: Reset and Apply sharing the width, on surface-2 under a
        // hairline.
        footer.setOpaque(true);
        footer.setBackground(Ui.surface2());
        footer.setLayout(new java.awt.GridLayout(1, 2, 8, 0));
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.border()),
                new EmptyBorder(10, 16, 10, 16)));
        resetButton.addActionListener(e -> forEachWizard(org.openpnp.gui.support.AbstractConfigurationWizard::reset));
        footer.add(resetButton);
        applyButton.addActionListener(e -> forEachWizard(org.openpnp.gui.support.AbstractConfigurationWizard::apply));
        footer.add(applyButton);
        footer.setVisible(false);
        add(footer, BorderLayout.SOUTH);

        // The sliver left behind when the panel is folded away, holding the button that unfolds it.
        JButton unfold = Ui.iconButton(Ui.iconSm("chevleft"), Ui.Size.Xs, Ui.Variant.Ghost, //$NON-NLS-1$
                Translations.getString("InspectorPanel.Action.Toggle.Description")); //$NON-NLS-1$
        unfold.addActionListener(toggleCollapsedAction);
        strip.setOpaque(false);
        // Below the card's 14 pixel corners, which would otherwise clip the button to a sliver.
        strip.setBorder(new EmptyBorder(Tokens.R_LG, 5, 0, 5));
        strip.add(unfold, BorderLayout.NORTH);
        strip.setVisible(false);
        add(strip, BorderLayout.WEST);

        sheets.putClientProperty(FlatClientProperties.STYLE,
                "tabHeight: 36; underlineColor: $Pono.accent; tabSelectionHeight: 2; " //$NON-NLS-1$
                        + "selectedForeground: $Label.foreground; foreground: $Pono.textSecondary; " //$NON-NLS-1$
                        + "tabInsets: 0,14,0,14; contentSeparatorHeight: 1; tabSeparatorsFullHeight: false"); //$NON-NLS-1$
        sheets.setFont(Ui.font(12.5f));
        // One sheet needs no tab strip: the stylesheet's accordion forms are one sheet each.
        sheets.putClientProperty(FlatClientProperties.TABBED_PANE_HIDE_TAB_AREA_WITH_ONE_TAB, true);

        clear();
    }

    private final RoundedPanel iconBox = new RoundedPanel(Tokens.R_MD, Ui::accentSoft, () -> null);
    private final JButton moreButton = Ui.iconButton(Ui.icon("more"), Ui.Size.Sm, Ui.Variant.Ghost, //$NON-NLS-1$
            Translations.getString("InspectorPanel.More")); //$NON-NLS-1$
    private final JPanel footer = new JPanel();
    private final JButton resetButton = Ui.button(Translations.getString("AbstractConfigurationWizard.Action.Reset"), //$NON-NLS-1$
            null, Ui.Size.Md, Ui.Variant.Default);
    private final JButton applyButton = Ui.button(Translations.getString("AbstractConfigurationWizard.Action.Apply"), //$NON-NLS-1$
            null, Ui.Size.Md, Ui.Variant.Primary);
    private final java.beans.PropertyChangeListener dirtyListener = e -> followDirty();

    /** The wizards currently on show, whose Reset and Apply the footer stands in for. */
    private java.util.List<org.openpnp.gui.support.AbstractConfigurationWizard> wizards() {
        java.util.List<org.openpnp.gui.support.AbstractConfigurationWizard> found = new java.util.ArrayList<>();
        for (int i = 0; i < sheets.getTabCount(); i++) {
            collectWizards(sheets.getComponentAt(i), found);
        }
        return found;
    }

    private static void collectWizards(Component component,
            java.util.List<org.openpnp.gui.support.AbstractConfigurationWizard> into) {
        if (component instanceof org.openpnp.gui.support.AbstractConfigurationWizard) {
            into.add((org.openpnp.gui.support.AbstractConfigurationWizard) component);
        }
        else if (component instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) component).getComponents()) {
                collectWizards(child, into);
            }
        }
    }

    private void forEachWizard(java.util.function.Consumer<org.openpnp.gui.support.AbstractConfigurationWizard> action) {
        for (org.openpnp.gui.support.AbstractConfigurationWizard wizard : wizards()) {
            if (wizard.isDirty()) {
                action.accept(wizard);
            }
        }
    }

    /** One footer for all the sheets: it lights up while any of them has something to apply. */
    private void adoptWizards() {
        boolean any = false;
        for (org.openpnp.gui.support.AbstractConfigurationWizard wizard : wizards()) {
            wizard.setActionsShown(false);
            wizard.getApplyAction().removePropertyChangeListener(dirtyListener);
            wizard.getApplyAction().addPropertyChangeListener(dirtyListener);
            any = true;
        }
        footer.setVisible(any && !collapsed);
        followDirty();
    }

    private void followDirty() {
        boolean dirty = false;
        for (org.openpnp.gui.support.AbstractConfigurationWizard wizard : wizards()) {
            dirty |= wizard.isDirty();
        }
        resetButton.setEnabled(dirty);
        applyButton.setEnabled(dirty);
        if (resetButton.getClientProperty(Ui.WHY_DISABLED) == null) {
            java.util.function.Supplier<String> none = () -> Translations.getString("InspectorPanel.Disabled.NoChanges"); //$NON-NLS-1$
            Ui.whyDisabled(resetButton, none);
            Ui.whyDisabled(applyButton, none);
        }
    }

    /** The "..." menu in the header: the page's actions on the thing shown, such as delete. */
    public void setMoreMenu(javax.swing.JPopupMenu menu) {
        for (java.awt.event.ActionListener l : moreButton.getActionListeners()) {
            moreButton.removeActionListener(l);
        }
        moreButton.setVisible(menu != null);
        if (menu != null) {
            moreButton.addActionListener(e -> menu.show(moreButton, 0, moreButton.getHeight()));
        }
    }

    /**
     * One thing to inspect: what it is, where its wizards report back to, and how to head it.
     * <p>
     * Built afresh each time it is presented rather than kept: presenting disposes of the wizards
     * it replaces, so a page's sheets have to be made again when the user comes back to that page.
     */
    public static final class Inspection {
        final Object subject;
        final WizardContainer container;
        final String title;
        final String type;
        final Icon icon;
        final List<PropertySheet> sheets;

        public Inspection(Object subject, WizardContainer container, String title, String type,
                Icon icon, List<PropertySheet> sheets) {
            this.subject = subject;
            this.container = container;
            this.title = title;
            this.type = type;
            this.icon = icon;
            this.sheets = sheets;
        }

        /** A holder's own sheets, headed by its own title and icon unless told otherwise. */
        public static Inspection of(PropertySheetHolder holder, WizardContainer container,
                String title, Icon icon) {
            PropertySheet[] reported = holder.getPropertySheets();
            return new Inspection(holder, container,
                    title != null ? title : holder.getPropertySheetHolderTitle(),
                    typeOf(holder),
                    icon != null ? icon : holder.getPropertySheetHolderIcon(),
                    reported == null ? List.of() : Arrays.asList(reported));
        }
    }

    /**
     * What each page last asked to have shown. Only the active page's request is on screen: the
     * others are kept so that they can be presented when the user comes back to their page.
     */
    private final Map<Component, Supplier<Inspection>> requests = new HashMap<>();

    private Component activePage;

    /**
     * Show what a page has selected, if that page is the one on screen.
     * <p>
     * Every table used to write straight into this column, whichever page the user was looking
     * at - so the issues page's background scan replaced whatever the parts page had put here,
     * and switching pages left the previous page's sheets behind. A request from a page that is
     * not active is remembered and presented when the page is.
     * 
     * @param page       The navigation page asking, which is how the request is matched to what is
     *                   on screen.
     * @param inspection How to build what to show; null for nothing selected.
     * @return Cancelled when the user would not let go of unapplied edits, in which case the
     *         caller should put its selection back where it was. A request from a page that is
     *         not on screen is always Shown, since nobody was asked anything.
     */
    public Result show(Component page, Supplier<Inspection> inspection) {
        requests.put(page, inspection);
        if (page != activePage) {
            return Result.Shown;
        }
        return present(inspection);
    }

    /**
     * Show one thing's properties.
     * 
     * @param holder    What to show. Null clears the panel.
     * @param container Where the wizards report back to, which is still the panel that owns the
     *                  table: a wizard completing may have to refresh a row or a board's outline.
     * @param title     The name as the table shows it, which is not always the holder's own title.
     */
    public Result show(Component page, PropertySheetHolder holder, WizardContainer container,
            String title, Icon icon) {
        return show(page, holder == null ? null
                : () -> Inspection.of(holder, container, title, icon));
    }

    /**
     * Show sheets that were assembled rather than reported, as a part's are: they come from the
     * machine's part alignments and its fiducial locator, and a part reports no sheets of its own.
     * 
     * @param type   What kind of thing this is, under its name. A package for a part, the driver's
     *               class for a driver - whatever the table beside it would have called it.
     * @param sheets Builds the sheets. Called again whenever they have to be shown afresh.
     */
    public Result show(Component page, Object subject, WizardContainer container, String title,
            String type, Icon icon, Supplier<List<PropertySheet>> sheets) {
        return show(page, subject == null ? null
                : () -> new Inspection(subject, container, title, type, icon, sheets.get()));
    }

    /**
     * The page now on screen. Its last request is presented, and if the user will not let go of
     * unapplied edits in the sheets being replaced, the answer is Cancelled and the caller should
     * put the previous page back.
     */
    public Result setActivePage(Component page) {
        Component previous = activePage;
        activePage = page;
        Result result = present(requests.get(page));
        if (result == Result.Cancelled) {
            activePage = previous;
        }
        return result;
    }

    public Component getActivePage() {
        return activePage;
    }

    private Result present(Supplier<Inspection> request) {
        Inspection inspection = request == null ? null : request.get();
        if (inspection == null) {
            Result result = presenter.show(null, null, null, null);
            if (result == Result.Shown) {
                clear();
            }
            return result;
        }
        Result result = presenter.show(inspection.subject, inspection.container,
                inspection.title, inspection.sheets);
        if (result != Result.Shown) {
            return result;
        }
        if (sheets.getTabCount() == 0) {
            clear();
            return result;
        }
        iconLabel.setIcon(inspection.icon);
        iconBox.setVisible(inspection.icon != null);
        String name = inspection.title == null ? String.valueOf(inspection.subject) : inspection.title;
        nameLabel.setText(name);
        nameLabel.setToolTipText(name);
        typeLabel.setText(inspection.type == null || inspection.type.isEmpty() ? " " //$NON-NLS-1$
                : inspection.type);
        typeLabel.setToolTipText(inspection.type == null || inspection.type.isEmpty() ? null : inspection.type);
        setBody(sheets);
        adoptWizards();
        setMoreMenu(actionsMenu(inspection.subject));
        return result;
    }

    /**
     * The "..." menu of a thing that reports actions of its own - a machine element, a feeder -
     * which were only on the machine setup page's toolbar; null for one that has none.
     */
    private static javax.swing.JPopupMenu actionsMenu(Object subject) {
        if (!(subject instanceof PropertySheetHolder)) {
            return null;
        }
        Action[] actions = ((PropertySheetHolder) subject).getPropertySheetHolderActions();
        if (actions == null || actions.length == 0) {
            return null;
        }
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        for (Action action : actions) {
            if (action != null) {
                menu.add(action);
            }
        }
        return menu.getComponentCount() == 0 ? null : menu;
    }

    /** Show nothing, and say so rather than leaving an empty tab strip. */
    public void clear() {
        iconLabel.setIcon(null);
        iconBox.setVisible(false);
        nameLabel.setText(Translations.getString("InspectorPanel.Title")); //$NON-NLS-1$
        typeLabel.setText(" "); //$NON-NLS-1$
        footer.setVisible(false);
        setMoreMenu(null);
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
        footer.setVisible(!collapsed && !wizards().isEmpty());
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
    public static String typeOf(Object holder) {
        // The name the user knows, "料带飞达"; the class is for the configuration file, and is
        // shown only where there is no name for it yet.
        String simple = holder.getClass().getSimpleName();
        String display = org.openpnp.gui.support.DisplayNames.typeName(holder.getClass());
        return display == null || display.isEmpty() ? simple : display;
    }

    /** Fired when the column goes from showing nothing to showing something, or back. */
    public static final String PROPERTY_CONTENT = "content"; //$NON-NLS-1$

    /** Whether there are sheets on show, as against "nothing selected". */
    public boolean hasContent() {
        return body.getComponentCount() == 1 && body.getComponent(0) == sheets;
    }

    private void setBody(Component content) {
        if (body.getComponentCount() == 1 && body.getComponent(0) == content) {
            return;
        }
        boolean had = hasContent();
        body.removeAll();
        body.add(content, BorderLayout.CENTER);
        body.revalidate();
        body.repaint();
        firePropertyChange(PROPERTY_CONTENT, had, hasContent());
    }

    public PropertySheetPresenter getPresenter() {
        return presenter;
    }

    /**
     * The sheets and the "nothing selected" note take turns in the body, and a theme change
     * reaches only the one on show: the other kept the old theme's colours - white tabs with dark
     * text in the dark theme, after switching while nothing was selected.
     */
    @Override
    public void updateUI() {
        super.updateUI();
        // Null while the superclass constructor is still running.
        if (sheets != null && sheets.getParent() == null) {
            javax.swing.SwingUtilities.updateComponentTreeUI(sheets);
        }
        if (nothingSelected != null && nothingSelected.getParent() == null) {
            javax.swing.SwingUtilities.updateComponentTreeUI(nothingSelected);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(collapsed ? COLLAPSED_WIDTH : PREFERRED_WIDTH, size.height);
    }
}
