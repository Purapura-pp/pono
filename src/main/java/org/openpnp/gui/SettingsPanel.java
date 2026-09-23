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

package org.openpnp.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.prefs.Preferences;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

import org.openpnp.Main;
import org.openpnp.Translations;
import org.openpnp.gui.components.ThemeInfo;
import org.openpnp.gui.components.ThemeSettingsPanel;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Tokens;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.theme.PonoThemes;
import org.openpnp.model.Configuration;
import org.openpnp.model.LengthUnit;

/**
 * The settings page, the mockups' 18: appearance, language and units, the operator, saving and
 * backups, the keyboard shortcuts and what this is, each a section of one page with a list of
 * them down its left. A change takes effect as it is made.
 * <p>
 * It replaces the appearance dialog, which offered every look and feel on the classpath - Metal
 * and Nimbus among them, which pull the shell out of shape - and a font slider, while the
 * language and the units were in the View menu and the rest nowhere.
 */
@SuppressWarnings("serial")
public class SettingsPanel extends JPanel {
    /** The operator's name, which production mode shows in its status bar. */
    public static final String PREF_OPERATOR = "Pono.operatorName"; //$NON-NLS-1$
    /** Whether an applied setting saves itself a few seconds later. */
    public static final String PREF_AUTOSAVE = "Pono.autosave"; //$NON-NLS-1$

    private final Configuration configuration;
    private final MainFrame frame;
    private final Preferences prefs = Preferences.userNodeForPackage(MainFrame.class);
    private final JPanel body = new JPanel();
    private final List<JComponent> sections = new ArrayList<>();
    private final JScrollPane scroll;

    public SettingsPanel(Configuration configuration, MainFrame frame) {
        this.configuration = configuration;
        this.frame = frame;
        setLayout(new BorderLayout());
        setOpaque(false);

        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(6, 0, 20, 0));
        addSection("palette", "SettingsPanel.Appearance", appearance()); //$NON-NLS-1$ //$NON-NLS-2$
        addSection("globe", "SettingsPanel.Language", language()); //$NON-NLS-1$ //$NON-NLS-2$
        addSection("user", "SettingsPanel.Operator", operator()); //$NON-NLS-1$ //$NON-NLS-2$
        addSection("save", "SettingsPanel.Saving", saving()); //$NON-NLS-1$ //$NON-NLS-2$
        addSection("keyboard", "SettingsPanel.Keys", keys()); //$NON-NLS-1$ //$NON-NLS-2$
        addSection("info", "SettingsPanel.About", about()); //$NON-NLS-1$ //$NON-NLS-2$
        body.add(Box.createVerticalGlue());

        scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        add(nav(), BorderLayout.WEST);
    }

    private void addSection(String icon, String key, JComponent content) {
        Forms.Section section = new Forms.Section(icon, Translations.getString(key));
        section.content(content);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.putClientProperty("Pono.settingsKey", key); //$NON-NLS-1$
        sections.add(section);
        body.add(section);
    }

    /** The stylesheet's .set-nav: the sections, the one on show marked, a click scrolls to it. */
    private JComponent nav() {
        JPanel nav = new JPanel();
        nav.setOpaque(false);
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createMatteBorder(0, 0, 0, 1, Ui.border()),
                new EmptyBorder(12, 10, 12, 10)));
        List<NavItem> items = new ArrayList<>();
        String[][] entries = { { "palette", "SettingsPanel.Appearance" }, { "globe", "SettingsPanel.Language" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                { "user", "SettingsPanel.Operator" }, { "save", "SettingsPanel.Saving" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                { "keyboard", "SettingsPanel.Keys" }, { "info", "SettingsPanel.About" } }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        for (int i = 0; i < entries.length; i++) {
            JComponent target = sections.get(i);
            NavItem item = new NavItem(entries[i][0], Translations.getString(entries[i][1]));
            item.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    for (NavItem other : items) {
                        other.active = other == item;
                        other.repaint();
                    }
                    body.scrollRectToVisible(new java.awt.Rectangle(0, target.getY(), 1, scroll.getViewport().getHeight()));
                }
            });
            items.add(item);
            nav.add(item);
            nav.add(Box.createVerticalStrut(2));
        }
        items.get(0).active = true;
        nav.add(Box.createVerticalGlue());
        nav.setPreferredSize(new Dimension(190, 10));
        return nav;
    }

    /** One entry of the section list: an icon and a name, on a rounded block when it is the one. */
    private static final class NavItem extends JLabel {
        boolean active;

        NavItem(String icon, String text) {
            super(text, Ui.icon(icon, 16), LEFT);
            setIconTextGap(10);
            setFont(Ui.font(Tokens.FS_BODY));
            setBorder(new EmptyBorder(8, 10, 8, 10));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (active) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Ui.accentSoft());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 2 * Tokens.R_SM, 2 * Tokens.R_SM);
                }
                finally {
                    g2.dispose();
                }
            }
            setForeground(active ? Ui.accent() : Ui.text2());
            super.paintComponent(g);
        }
    }

    // ---- appearance ---------------------------------------------------------------------------

    private JComponent appearance() {
        Forms.Grid grid = new Forms.Grid(Tokens.FORM_LABEL);
        JPanel cards = new JPanel();
        cards.setOpaque(false);
        cards.setLayout(new BoxLayout(cards, BoxLayout.X_AXIS));
        List<ThemeCard> all = new ArrayList<>();
        ThemeInfo current = configuration.getThemeInfo() == null ? PonoThemes.dark() : configuration.getThemeInfo();
        ThemeInfo[] themes = { PonoThemes.dark(), PonoThemes.light(), PonoThemes.followSystem() };
        String[] labels = { "SettingsPanel.Theme.Dark", "SettingsPanel.Theme.Light", "SettingsPanel.Theme.System" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        for (int i = 0; i < themes.length; i++) {
            ThemeInfo theme = themes[i];
            ThemeCard card = new ThemeCard(Translations.getString(labels[i]), i);
            card.on = theme.equals(current);
            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    for (ThemeCard other : all) {
                        other.on = other == card;
                        other.repaint();
                    }
                    applyTheme(theme, configuration.getFontSize());
                }
            });
            all.add(card);
            if (i > 0) {
                cards.add(Box.createHorizontalStrut(12));
            }
            cards.add(card);
        }
        cards.add(Box.createHorizontalGlue());
        grid.row(Translations.getString("SettingsPanel.Theme"), cards); //$NON-NLS-1$
        grid.row("", Ui.muted(Translations.getString("SettingsPanel.Theme.Note"))); //$NON-NLS-1$ //$NON-NLS-2$

        // The body text at 12, 13 or 14: the stylesheet's scale, around its 13.
        ThemeSettingsPanel.FontSize[] sizes = { ThemeSettingsPanel.FontSize.SMALLER,
                ThemeSettingsPanel.FontSize.BELOW_SMALL, ThemeSettingsPanel.FontSize.SMALL };
        List<Object> sizeItems = new ArrayList<>(java.util.Arrays.asList((Object[]) sizes));
        Forms.Segmented size = new Forms.Segmented(sizeItems, item -> Translations.getString(
                "SettingsPanel.FontSize." + ((ThemeSettingsPanel.FontSize) item).name())); //$NON-NLS-1$
        ThemeSettingsPanel.FontSize stored = configuration.getFontSize();
        size.setSelectedItem(stored != null && sizeItems.contains(stored) ? stored : ThemeSettingsPanel.FontSize.BELOW_SMALL);
        size.onChange(() -> {
            ThemeSettingsPanel.FontSize chosen = (ThemeSettingsPanel.FontSize) size.getSelectedItem();
            configuration.setFontSize(chosen);
            ThemeInfo theme = configuration.getThemeInfo();
            applyTheme(theme == null ? PonoThemes.dark() : theme, chosen);
        });
        grid.row(Translations.getString("SettingsPanel.FontSize"), row(size)); //$NON-NLS-1$
        return grid;
    }

    private void applyTheme(ThemeInfo theme, ThemeSettingsPanel.FontSize fontSize) {
        new ThemeSettingsPanel().setTheme(theme, fontSize, configuration.isAlternateRows());
        configuration.setThemeInfo(theme);
        org.openpnp.gui.components.ThemeDialog.getInstance().setOldTheme(theme);
    }

    /** The stylesheet's .theme-card: a small picture of the window in the theme, and its name. */
    private static final class ThemeCard extends JComponent {
        final String name;
        final int kind;
        boolean on;

        ThemeCard(String name, int kind) {
            this.name = name;
            this.kind = kind;
            setPreferredSize(new Dimension(132, 104));
            setMaximumSize(getPreferredSize());
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int ph = 72;
                java.awt.Shape clip = new java.awt.geom.RoundRectangle2D.Float(0, 0, w, ph, 2 * Tokens.R_SM, 2 * Tokens.R_SM);
                Color bg = kind == 1 ? new Color(0xeceff3) : new Color(0x0d1015);
                Color surface = kind == 1 ? Color.WHITE : new Color(0x1a2028);
                Color accent = kind == 1 ? new Color(0x2f6fe0) : new Color(0x4f8cff);
                g2.setClip(clip);
                g2.setColor(bg);
                g2.fillRect(0, 0, w, ph);
                if (kind == 2) {
                    g2.setColor(new Color(0xeceff3));
                    g2.fillRect(w / 2, 0, w - w / 2, ph);
                }
                g2.setColor(kind == 2 ? new Color(0x6c7787) : surface);
                g2.fillRoundRect(8, 8, 18, ph - 16, 6, 6);
                g2.fillRoundRect(32, 8, w - 40, 14, 6, 6);
                g2.fillRoundRect(32, 26, w - 40, ph - 34, 6, 6);
                g2.setColor(accent);
                g2.fillRect(32, 26, 3, ph - 34);
                g2.setClip(null);
                g2.setColor(on ? Ui.accent() : Ui.border());
                g2.setStroke(new java.awt.BasicStroke(on ? 2f : 1f));
                g2.drawRoundRect(on ? 1 : 0, on ? 1 : 0, w - (on ? 3 : 1), ph - (on ? 3 : 1), 2 * Tokens.R_SM, 2 * Tokens.R_SM);
                g2.setFont(on ? Ui.weighted(Tokens.FS_SMALL, Tokens.FW_SECTION) : Ui.font(Tokens.FS_SMALL));
                g2.setColor(on ? Ui.accent() : Ui.text2());
                String text = (on ? "\u2713 " : "") + name; //$NON-NLS-1$ //$NON-NLS-2$
                g2.drawString(text, 2, ph + 22);
            }
            finally {
                g2.dispose();
            }
        }
    }

    // ---- language and units -------------------------------------------------------------------

    private JComponent language() {
        Forms.Grid grid = new Forms.Grid(Tokens.FORM_LABEL);
        List<Locale> locales = new ArrayList<>(Translations.getAvailableLocales());
        Collator collator = Collator.getInstance();
        locales.sort((a, b) -> collator.compare(a.getDisplayName(), b.getDisplayName()));
        JComboBox<Locale> language = Forms.dropdown(new JComboBox<>(locales.toArray(new Locale[0])));
        language.setRenderer(Forms.described(item -> ((Locale) item).getDisplayName((Locale) item), null));
        language.setSelectedItem(configuration.getLocale());
        language.addActionListener(e -> {
            Locale chosen = (Locale) language.getSelectedItem();
            if (chosen != null && !chosen.equals(configuration.getLocale())) {
                configuration.setLocale(chosen);
            }
        });
        language.setPreferredSize(new Dimension(220, 30));
        language.setMaximumSize(new Dimension(220, 30));
        grid.row(Translations.getString("SettingsPanel.Language.Language"), //$NON-NLS-1$
                row(language, Ui.muted(Translations.getString("SettingsPanel.RestartNote")))); //$NON-NLS-1$

        List<Object> units = new ArrayList<>(java.util.Arrays.asList(LengthUnit.Millimeters, LengthUnit.Inches));
        Forms.Segmented unit = new Forms.Segmented(units, item -> ((LengthUnit) item).getShortName());
        unit.setSelectedItem(configuration.getSystemUnits());
        unit.onChange(() -> configuration.setSystemUnits((LengthUnit) unit.getSelectedItem()));
        grid.row(Translations.getString("SettingsPanel.Language.Units"), //$NON-NLS-1$
                row(unit, Ui.muted(Translations.getString("SettingsPanel.RestartNote")))); //$NON-NLS-1$

        JTextField decimals = Forms.input(new JTextField(String.valueOf(decimals())), true);
        decimals.setPreferredSize(new Dimension(90, 30));
        decimals.setMaximumSize(new Dimension(90, 30));
        decimals.addActionListener(e -> applyDecimals(decimals));
        decimals.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                applyDecimals(decimals);
            }
        });
        grid.row(Translations.getString("SettingsPanel.Language.Decimals"), //$NON-NLS-1$
                row(decimals, Ui.muted(Translations.getString("SettingsPanel.Language.Decimals.Note")))); //$NON-NLS-1$
        return grid;
    }

    /** The places after the point the lengths are shown with, read off the display format. */
    private int decimals() {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\.(\\d+)f").matcher(configuration.getLengthDisplayFormat()); //$NON-NLS-1$
        return m.find() ? Integer.parseInt(m.group(1)) : 3;
    }

    private void applyDecimals(JTextField field) {
        try {
            int places = Math.max(0, Math.min(6, Integer.parseInt(field.getText().trim())));
            field.setText(String.valueOf(places));
            configuration.setLengthDisplayFormat("%." + places + "f"); //$NON-NLS-1$ //$NON-NLS-2$
            configuration.setLengthDisplayAlignedFormat("%" + (places + 7) + "." + places + "f"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            field.putClientProperty(com.formdev.flatlaf.FlatClientProperties.OUTLINE, null);
        }
        catch (NumberFormatException e) {
            // Said where it happened rather than dropped: the field is marked and keeps the text.
            field.putClientProperty(com.formdev.flatlaf.FlatClientProperties.OUTLINE,
                    com.formdev.flatlaf.FlatClientProperties.OUTLINE_ERROR);
            field.setToolTipText(Translations.getString("SettingsPanel.Language.Decimals.Invalid")); //$NON-NLS-1$
        }
    }

    // ---- operator, saving ---------------------------------------------------------------------

    private JComponent operator() {
        Forms.Grid grid = new Forms.Grid(Tokens.FORM_LABEL);
        JTextField name = Forms.input(new JTextField(prefs.get(PREF_OPERATOR, "")), false); //$NON-NLS-1$
        name.setPreferredSize(new Dimension(220, 30));
        name.setMaximumSize(new Dimension(220, 30));
        Runnable store = () -> prefs.put(PREF_OPERATOR, name.getText().trim());
        name.addActionListener(e -> store.run());
        name.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                store.run();
            }
        });
        grid.row(Translations.getString("SettingsPanel.Operator.Name"), //$NON-NLS-1$
                row(name, Ui.muted(Translations.getString("SettingsPanel.Operator.Note")))); //$NON-NLS-1$
        return grid;
    }

    private JComponent saving() {
        Forms.Grid grid = new Forms.Grid(Tokens.FORM_LABEL);
        Forms.Toggle autosave = new Forms.Toggle();
        autosave.setSelected(prefs.getBoolean(PREF_AUTOSAVE, true));
        autosave.onChange(() -> prefs.putBoolean(PREF_AUTOSAVE, autosave.isSelected()));
        grid.row(Translations.getString("SettingsPanel.Saving.Autosave"), //$NON-NLS-1$
                Forms.toggleRow(autosave, Translations.getString("SettingsPanel.Saving.Autosave.Note"))); //$NON-NLS-1$
        grid.row(Translations.getString("SettingsPanel.Saving.Backups"), //$NON-NLS-1$
                Ui.t2(Translations.getString("SettingsPanel.Saving.Backups.Note"))); //$NON-NLS-1$
        String directory = configuration.getConfigurationDirectory() == null ? "" //$NON-NLS-1$
                : configuration.getConfigurationDirectory().getAbsolutePath();
        JButton open = Ui.button(Translations.getString("SettingsPanel.Saving.Open"), Ui.iconSm("folder"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Default);
        open.setFocusable(true);
        open.addActionListener(e -> org.openpnp.util.UiUtils.openFolder(this, configuration.getConfigurationDirectory()));
        grid.row(Translations.getString("SettingsPanel.Saving.Directory"), //$NON-NLS-1$
                Forms.row(Forms.readOnly(directory), open));
        return grid;
    }

    // ---- keys, about --------------------------------------------------------------------------

    private JComponent keys() {
        Forms.Grid grid = new Forms.Grid(Tokens.FORM_LABEL);
        JButton show = Ui.button(Translations.getString("SettingsPanel.Keys.Show"), Ui.iconSm("keyboard"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Default);
        show.setFocusable(true);
        show.addActionListener(e -> frame.showHotkeys());
        grid.row(Translations.getString("SettingsPanel.Keys.All"), //$NON-NLS-1$
                row(show, Ui.muted(Translations.getString("SettingsPanel.Keys.Note")))); //$NON-NLS-1$
        return grid;
    }

    private JComponent about() {
        Forms.Grid grid = new Forms.Grid(Tokens.FORM_LABEL);
        grid.row(Translations.getString("SettingsPanel.About.Version"), //$NON-NLS-1$
                Forms.readOnly(Main.getVersion()));
        JButton about = Ui.button(Translations.getString("SettingsPanel.About.Show"), Ui.iconSm("info"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Default);
        about.setFocusable(true);
        about.addActionListener(e -> frame.showAbout());
        grid.row("", row(about)); //$NON-NLS-1$
        return grid;
    }

    private static JPanel row(JComponent... parts) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                row.add(Box.createHorizontalStrut(10));
            }
            parts[i].setAlignmentY(Component.CENTER_ALIGNMENT);
            row.add(parts[i]);
        }
        row.add(Box.createHorizontalGlue());
        return row;
    }
}
