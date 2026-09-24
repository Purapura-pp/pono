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

package org.openpnp.gui.machinesettings;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.openpnp.ConfigurationListener;
import org.openpnp.Translations;
import org.openpnp.gui.MachineSetupPanel;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.gui.shell.NavigationRail;
import org.openpnp.gui.shell.PropertySheetPresenter;
import org.openpnp.gui.shell.Tokens;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.shell.WidthTracking;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.model.Configuration;
import org.openpnp.spi.Machine;

/**
 * The machine settings page, the mockups' 24 to 28: what the machine is, topic by topic - its
 * model and preset, its axes and their limits, its nozzles, its cameras, its connection - each
 * with how to arrive at its values on any machine beside it, and the element tree as the last,
 * advanced topic.
 * <p>
 * It takes the tree's place in the rail. The tree showed the machine as the configuration file
 * holds it, an axis's limits three levels down, and the number of nozzles could only be changed
 * by an issue that deleted the second one of a LumenPnP.
 */
@SuppressWarnings("serial")
public class MachineSettingsPanel extends JPanel {
    public static final String OVERVIEW = "Overview"; //$NON-NLS-1$
    public static final String PRESETS = "Presets"; //$NON-NLS-1$
    public static final String MOTION = "Motion"; //$NON-NLS-1$
    public static final String NOZZLES = "Nozzles"; //$NON-NLS-1$
    public static final String CAMERAS = "Cameras"; //$NON-NLS-1$
    public static final String CONNECTION = "Connection"; //$NON-NLS-1$
    public static final String ADVANCED = "Advanced"; //$NON-NLS-1$

    /** The key the window keeps this page's layout under; the tree keeps the one it always had. */
    public static final String LAYOUT = "MachineSettings"; //$NON-NLS-1$
    public static final String TREE_LAYOUT = "MachineSetup"; //$NON-NLS-1$

    private final Configuration configuration;
    private final MainFrame frame;
    private final MachineSetupPanel tree;
    private final Map<String, Topic> topics = new LinkedHashMap<>();
    private final Map<Topic, TopicItem> items = new LinkedHashMap<>();
    private final JPanel nav = new JPanel();
    private final JPanel cards = new JPanel(new CardLayout());
    private final Header header = new Header();
    private final JPanel foot = new JPanel();
    private final JLabel pending = Ui.t2(""); //$NON-NLS-1$
    private final JButton reset;
    private final JButton apply;
    private ReferenceMachine machine;
    private SetupChecks checks = SetupChecks.none();
    private Topic current;
    private boolean selecting;

    public MachineSettingsPanel(Configuration configuration, MainFrame frame, MachineSetupPanel tree) {
        this.configuration = configuration;
        this.frame = frame;
        this.tree = tree;
        tree.setInspectorPage(this, this::isAdvanced);
        setLayout(new BorderLayout());
        setOpaque(false);

        nav.setOpaque(false);
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Ui.border()),
                new EmptyBorder(10, 10, 10, 10)));
        nav.setPreferredSize(new Dimension(200, 10));

        cards.setOpaque(false);
        foot.setLayout(new BoxLayout(foot, BoxLayout.X_AXIS));
        foot.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.border()),
                new EmptyBorder(8, 18, 8, 12)));
        foot.setOpaque(true);
        foot.setBackground(Ui.surface2());
        reset = Ui.button(Translations.getString("AbstractConfigurationWizard.Action.Reset"), null, //$NON-NLS-1$
                Ui.Size.Sm, Ui.Variant.Default);
        apply = Ui.button(Translations.getString("AbstractConfigurationWizard.Action.Apply"), null, //$NON-NLS-1$
                Ui.Size.Sm, Ui.Variant.Primary);
        reset.setToolTipText(Translations.getString("MachineSettings.Foot.Reset.ToolTip")); //$NON-NLS-1$
        apply.setToolTipText(Translations.getString("MachineSettings.Foot.Apply.ToolTip")); //$NON-NLS-1$
        reset.addActionListener(e -> {
            if (current != null) {
                current.forms.reset();
            }
        });
        apply.addActionListener(e -> {
            if (current != null) {
                current.forms.apply();
                refreshChecks();
            }
        });
        pending.setFont(Ui.font(Tokens.FS_SMALL));
        // The words give way before Reset and Apply do; they are whole on hover.
        pending.setMinimumSize(new Dimension(0, 20));
        foot.add(pending);
        foot.add(Box.createHorizontalGlue());
        foot.add(reset);
        foot.add(Box.createHorizontalStrut(8));
        foot.add(apply);

        JPanel main = new JPanel(new BorderLayout());
        main.setOpaque(false);
        main.add(header, BorderLayout.NORTH);
        main.add(cards, BorderLayout.CENTER);
        main.add(foot, BorderLayout.SOUTH);
        add(nav, BorderLayout.WEST);
        add(main, BorderLayout.CENTER);

        configuration.addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationComplete(Configuration configuration) throws Exception {
                Machine loaded = configuration.getMachine();
                if (loaded instanceof ReferenceMachine) {
                    SwingUtilities.invokeLater(() -> load((ReferenceMachine) loaded));
                }
            }
        });
    }

    private void load(ReferenceMachine machine) {
        this.machine = machine;
        add(new OverviewTopic(machine));
        add(new PresetsTopic(this, machine));
        add(new MotionTopic(this, machine));
        add(new NozzlesTopic(this, machine));
        add(new CamerasTopic(this, machine));
        add(new ConnectionTopic(this, machine));
        nav.add(Box.createVerticalStrut(4));
        JComponent rule = new JPanel();
        rule.setOpaque(true);
        rule.setBackground(Ui.border());
        rule.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        rule.setPreferredSize(new Dimension(1, 1));
        rule.setAlignmentX(Component.LEFT_ALIGNMENT);
        nav.add(rule);
        nav.add(Box.createVerticalStrut(6));
        add(new AdvancedTopic(tree));
        nav.add(Box.createVerticalGlue());
        JButton save = Ui.button(Translations.getString("MachineSettings.Head.SaveAsPreset"), Ui.iconSm("save"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Default);
        save.setToolTipText(Translations.getString("MachineSettings.Head.SaveAsPreset.ToolTip")); //$NON-NLS-1$
        save.addActionListener(e -> ((PresetsTopic) topics.get(PRESETS)).create(true));
        header.actions.add(save);
        header.show(machine, units());
        refreshChecks();
        select(topics.get(OVERVIEW));
    }

    private void add(Topic topic) {
        topics.put(topic.key, topic);
        TopicItem item = new TopicItem(topic);
        items.put(topic, item);
        item.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                select(topic);
            }
        });
        nav.add(item);
        nav.add(Box.createVerticalStrut(2));
        topic.forms.onChange(() -> {
            if (topic == current) {
                describeFoot();
            }
        });
    }

    /** Shows a topic by its key, the ones above: what the calibration page's hints lead to. */
    public void showTopic(String key) {
        Topic topic = topics.get(key);
        if (topic != null) {
            select(topic);
        }
    }

    /** The presets topic with the new preset dialog open over it. */
    public void newPreset() {
        showTopic(PRESETS);
        Topic presets = topics.get(PRESETS);
        if (presets instanceof PresetsTopic) {
            ((PresetsTopic) presets).create(false);
        }
    }

    /** The presets topic, applying the preset of that name: what it changes is asked first. */
    public void applyPreset(String name) {
        showTopic(PRESETS);
        Topic presets = topics.get(PRESETS);
        if (presets instanceof PresetsTopic) {
            ((PresetsTopic) presets).apply(name);
        }
    }

    /** The element tree, where the other pages' links to an element of the machine go. */
    public void showAdvanced() {
        showTopic(ADVANCED);
    }

    /** The tree with an element selected, its forms in the properties column: where a topic stops. */
    void showInTree(Object element) {
        showAdvanced();
        if (isAdvanced() && element instanceof org.openpnp.spi.PropertySheetHolder) {
            tree.selectPropertySheetHolder((org.openpnp.spi.PropertySheetHolder) element);
        }
    }

    public boolean isAdvanced() {
        return current != null && ADVANCED.equals(current.key);
    }

    /** The layout the window gives the page: the tree's for the tree, the page's own otherwise. */
    public String layoutKey() {
        return isAdvanced() ? TREE_LAYOUT : LAYOUT;
    }

    private void select(Topic topic) {
        if (topic == null || topic == current || selecting) {
            return;
        }
        selecting = true;
        try {
            if (!settleUnappliedEdits()) {
                return;
            }
            if (!ADVANCED.equals(topic.key)) {
                // The tree's selection goes from the properties column with the tree; its
                // unapplied edits are asked about first, and the tree stays if they are kept.
                PropertySheetPresenter.Result result = frame.getInspector().show(this,
                        (java.util.function.Supplier<org.openpnp.gui.shell.InspectorPanel.Inspection>) null);
                if (result == PropertySheetPresenter.Result.Cancelled) {
                    return;
                }
            }
            Topic previous = current;
            if (previous != null) {
                previous.hidden();
            }
            current = topic;
            if (!topic.isBuilt() || topic.view().getParent() != cards) {
                cards.add(topic.view(), topic.key);
            }
            ((CardLayout) cards.getLayout()).show(cards, topic.key);
            for (Map.Entry<Topic, TopicItem> entry : items.entrySet()) {
                entry.getValue().setActive(entry.getKey() == topic);
            }
            topic.shown();
            if (ADVANCED.equals(topic.key)) {
                tree.selectCurrentTreePath();
            }
            describeFoot();
            cards.revalidate();
            cards.repaint();
            frame.pageLayoutChanged(this);
        }
        finally {
            selecting = false;
        }
    }

    /**
     * Asks about the edits on the topic on show that were never applied, and does what the user
     * says: applies them, or throws them away. False when the user would rather stay.
     */
    public boolean settleUnappliedEdits() {
        if (current == null || !current.forms.isDirty()) {
            return true;
        }
        int selection = askUnapplied(this, current.title());
        if (selection == 1) {
            current.forms.apply();
            refreshChecks();
            return true;
        }
        if (selection == 0) {
            current.forms.reset();
            return true;
        }
        return false;
    }

    /**
     * The question the properties column asks about edits that were never applied, about what the
     * user was editing: 1 to apply them, 0 to throw them away, anything else to stay.
     */
    static int askUnapplied(Component parent, String name) {
        return Dialogs.ask(parent, Dialogs.Tone.Warn, "edit", //$NON-NLS-1$
                Translations.getString("PropertySheetPresenter.ApplyChanges.Title"), //$NON-NLS-1$
                Translations.getString("PropertySheetPresenter.ApplyChanges.Message").replace("%s", name), //$NON-NLS-1$ //$NON-NLS-2$
                null,
                Dialogs.Choice.plain(Translations.getString("PropertySheetPresenter.ApplyChanges.Discard")), //$NON-NLS-1$
                Dialogs.Choice.primary(Translations.getString("PropertySheetPresenter.ApplyChanges.Apply"))); //$NON-NLS-1$
    }

    /** The foot says what Apply would write, and is there only for a topic with forms. */
    private void describeFoot() {
        boolean forms = current != null && !current.forms.isEmpty();
        foot.setVisible(forms);
        if (!forms) {
            return;
        }
        List<FormWizard.Change> changes = current.forms.changes();
        boolean dirty = current.forms.isDirty();
        if (changes.isEmpty()) {
            pending.setText(Translations.getString(dirty ? "MachineSettings.Foot.Edited" //$NON-NLS-1$
                    : "MachineSettings.Foot.None")); //$NON-NLS-1$
            pending.setFont(Ui.font(Tokens.FS_SMALL));
        }
        else {
            FormWizard.Change first = changes.get(0);
            String text = String.format(Translations.getString("MachineSettings.Foot.Pending"), //$NON-NLS-1$
                    changes.size(), first.label, first.before, first.after);
            if (changes.size() > 1) {
                text += Translations.getString("MachineSettings.Foot.More"); //$NON-NLS-1$
            }
            pending.setText(text);
            pending.setFont(Ui.weighted(Tokens.FS_SMALL, Tokens.FW_SECTION));
            List<String> all = new ArrayList<>();
            for (FormWizard.Change change : changes) {
                all.add(change.toString());
            }
            pending.setToolTipText(String.join("\n", all)); //$NON-NLS-1$
        }
        reset.setEnabled(dirty);
        apply.setEnabled(dirty);
    }

    // ---- what the settings are missing --------------------------------------------------------

    /**
     * Looks again at what the machine's own definition is missing on this computer - a port, a
     * camera - and puts the counts on the topics and on the rail.
     */
    public void refreshChecks() {
        if (machine == null) {
            return;
        }
        checks = SetupChecks.of(machine);
        for (Map.Entry<Topic, TopicItem> entry : items.entrySet()) {
            entry.getValue().setCount(checks.count(entry.getKey().key));
        }
        if (frame.getNavigation() != null) {
            frame.getNavigation().setBadge(this, checks.all().size(), NavigationRail.Badge.Warn);
        }
        header.show(machine, units());
    }

    public SetupChecks getChecks() {
        return checks;
    }

    ReferenceMachine getMachine() {
        return machine;
    }

    Configuration getConfiguration() {
        return configuration;
    }

    /** The units lengths are shown in. */
    org.openpnp.model.LengthUnit units() {
        return configuration.getSystemUnits();
    }

    MainFrame getFrame() {
        return frame;
    }

    /** The machine changed shape - nozzles added, a preset applied: every topic is built again. */
    void rebuild() {
        for (Topic topic : topics.values()) {
            if (topic != current && topic.isBuilt()) {
                cards.remove(topic.view());
                topic.discard();
            }
        }
        if (current != null && current.isBuilt() && !ADVANCED.equals(current.key)) {
            cards.remove(current.view());
            current.discard();
            cards.add(current.view(), current.key);
            ((CardLayout) cards.getLayout()).show(cards, current.key);
            current.shown();
        }
        refreshChecks();
        describeFoot();
        cards.revalidate();
        cards.repaint();
    }

    // ---- pieces -------------------------------------------------------------------------------

    /**
     * A topic's page: its sections one under another, scrolled together, and the guide at the
     * right. The stylesheet's {@code .ms-form} and {@code .guide}.
     */
    static JComponent page(JComponent guide, JComponent... sections) {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        for (JComponent section : sections) {
            JComponent capped = capped(section);
            capped.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(capped);
        }
        body.add(Box.createVerticalGlue());
        JScrollPane scroll = new JScrollPane(new WidthTracking(body));
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        page.add(scroll, BorderLayout.CENTER);
        if (guide != null) {
            page.add(guide, BorderLayout.EAST);
        }
        return page;
    }

    /**
     * The component held at the height it wants. A column of them in a box layout that is taller
     * than they are would otherwise hand the spare height out among them - a form, a paragraph -
     * and spread them apart.
     */
    static JComponent capped(JComponent component) {
        JPanel holder = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        holder.setOpaque(false);
        holder.add(component, BorderLayout.CENTER);
        return holder;
    }

    /** One topic in the list: its icon and name, a rounded block while it is on show, and a count. */
    private static final class TopicItem extends JPanel {
        private final String icon;
        private final JLabel label;
        private final JLabel count = new JLabel();
        private boolean active;

        TopicItem(Topic topic) {
            super(new BorderLayout(6, 0));
            setOpaque(false);
            icon = topic.icon;
            label = new JLabel(topic.title(), Ui.icon(icon, 16, Ui.text2()), JLabel.LEFT);
            label.setIconTextGap(10);
            label.setFont(Ui.font(Tokens.FS_BODY));
            add(label, BorderLayout.CENTER);
            count.setFont(Ui.weighted(Tokens.FS_TAG, Tokens.FW_MICRO));
            count.setHorizontalAlignment(JLabel.CENTER);
            count.setVisible(false);
            add(count, BorderLayout.EAST);
            setBorder(new EmptyBorder(8, 10, 8, 8));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            setToolTipText(topic.title());
        }

        void setActive(boolean active) {
            this.active = active;
            label.setFont(active ? Ui.weighted(Tokens.FS_BODY, Tokens.FW_SECTION) : Ui.font(Tokens.FS_BODY));
            label.setIcon(Ui.icon(icon, 16, active ? Ui.accent() : Ui.text2()));
            repaint();
        }

        void setCount(int n) {
            count.setText(String.valueOf(n));
            count.setVisible(n > 0);
            count.setPreferredSize(new Dimension(Math.max(18, count.getPreferredSize().width), 18));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (active) {
                    g2.setColor(Ui.accentSoft());
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 2 * Tokens.R_SM, 2 * Tokens.R_SM);
                }
                if (count.isVisible()) {
                    java.awt.Rectangle r = count.getBounds();
                    int h = 18;
                    int y = r.y + (r.height - h) / 2;
                    g2.setColor(Ui.warnSoft());
                    g2.fillRoundRect(r.x, y, r.width, h, h, h);
                }
            }
            finally {
                g2.dispose();
            }
            label.setForeground(active ? Ui.accent() : Ui.text2());
            count.setForeground(Ui.warnText());
            super.paintComponent(g);
        }

        @Override
        public void updateUI() {
            super.updateUI();
            // The tinted icon is drawn in the theme's colours of the moment.
            if (label != null) {
                label.setIcon(Ui.icon(icon, 16, active ? Ui.accent() : Ui.text2()));
            }
        }
    }

    /**
     * The row along the top, the stylesheet's {@code .ms-head}: the machine by the name of the
     * model it is, where that came from, and in a line what it has.
     */
    private static final class Header extends JPanel {
        private final JLabel name = new JLabel();
        private final Chip tag = new Chip("", Chip.Tone.Neutral, Chip.Shape.Status).withLed(false); //$NON-NLS-1$
        private final JLabel summary = Ui.muted(""); //$NON-NLS-1$
        final JPanel actions = new JPanel();

        Header() {
            super(new BorderLayout(12, 0));
            setOpaque(false);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.border()),
                    new EmptyBorder(12, 18, 12, 18)));
            JLabel icon = new JLabel(Ui.icon("machine", 20, Ui.accent())) { //$NON-NLS-1$
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    try {
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(Ui.accentSoft());
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 2 * Tokens.R_MD, 2 * Tokens.R_MD);
                    }
                    finally {
                        g2.dispose();
                    }
                    super.paintComponent(g);
                }
            };
            icon.setHorizontalAlignment(JLabel.CENTER);
            icon.setPreferredSize(new Dimension(40, 40));
            add(icon, BorderLayout.WEST);
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            JPanel line = new JPanel();
            line.setOpaque(false);
            line.setLayout(new BoxLayout(line, BoxLayout.X_AXIS));
            name.setFont(Ui.weighted(Tokens.FS_TITLE, Tokens.FW_TITLE));
            line.add(name);
            line.add(Box.createHorizontalStrut(8));
            line.add(tag);
            line.add(Box.createHorizontalGlue());
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            summary.setFont(Ui.font(Tokens.FS_SMALL));
            summary.setAlignmentX(Component.LEFT_ALIGNMENT);
            text.add(line);
            text.add(Box.createVerticalStrut(2));
            text.add(summary);
            add(text, BorderLayout.CENTER);
            actions.setOpaque(false);
            actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
            add(actions, BorderLayout.EAST);
        }

        void show(ReferenceMachine machine, org.openpnp.model.LengthUnit units) {
            MachineSummary summaryOf = new MachineSummary(machine, units);
            name.setText(summaryOf.name());
            tag.setText(summaryOf.origin());
            summary.setText(summaryOf.line());
            summary.setToolTipText(summaryOf.line());
            revalidate();
            repaint();
        }
    }
}
