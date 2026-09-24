/*
 * Copyright (C) 2026 Pono
 * 
 * This file is part of OpenPnP.
 * 
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * For more information about OpenPnP visit http://openpnp.org
 */

package org.openpnp.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import org.openpnp.ConfigurationListener;
import org.openpnp.Translations;
import org.openpnp.gui.calibration.Banner;
import org.openpnp.gui.calibration.CalibrationItem;
import org.openpnp.gui.calibration.CalibrationItems;
import org.openpnp.gui.calibration.Html;
import org.openpnp.gui.calibration.IssueInputs;
import org.openpnp.gui.calibration.Pending;
import org.openpnp.gui.calibration.SettingsDiff;
import org.openpnp.gui.components.AutoSelectTextTable;
import org.openpnp.gui.components.IssuePanel;
import org.openpnp.gui.machinesettings.Backups;
import org.openpnp.gui.machinesettings.SetupChecks;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.gui.shell.DockPanel;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.PropertySheetPresenter;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.DisplayNames;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.calibration.CalibrationPlan;
import org.openpnp.machine.reference.calibration.CalibrationRunner;
import org.openpnp.machine.reference.calibration.SettingChange;
import org.openpnp.machine.reference.solutions.MachineDiagnostics;
import org.openpnp.model.CalibrationStep;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.Solutions;
import org.openpnp.util.UiUtils;
import org.pmw.tinylog.Logger;

/**
 * The calibration page, as mockups 29 and 30 have it.
 * <p>
 * Collecting finds what the machine needs without moving it, in groups: the suggestions, each with
 * the value it would write, one row for all the elements it is about; the steps that have to
 * measure, move the machine or have someone at it first; what was measured and waits to be
 * confirmed; and the hints, which are mostly for the machine settings page. A suggestion is applied
 * when the user says so. A measurement runs, and what it changed is in effect at once, but it is
 * the user who applies it or discards it; closing Pono without either discards it. The steps run
 * in the order they depend on each other, stop for what needs someone, and save after each.
 */
@SuppressWarnings("serial")
public class CalibrationPanel extends JPanel {
    private static final SimpleDateFormat DAY = new SimpleDateFormat("MM-dd"); //$NON-NLS-1$
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss"); //$NON-NLS-1$
    private static final SimpleDateFormat CLOCK = new SimpleDateFormat("HH:mm"); //$NON-NLS-1$
    /** The most changes the inspector lists before it says how many more there are. */
    private static final int LISTED = 14;

    /** What happened to a step in this session, which the plan does not know yet. */
    enum Live {
        Running, Waiting, Failed, Skipped, Done
    }

    /** A group's heading row: "有建议值 · 3". */
    static final class GroupRow {
        final CalibrationItem.Kind kind;
        int count;
        boolean folded;

        GroupRow(CalibrationItem.Kind kind) {
            this.kind = kind;
        }
    }

    static final class ItemRow {
        final CalibrationItem item;

        ItemRow(CalibrationItem item) {
            this.item = item;
        }
    }

    /** One element's row under an item that is open. */
    static final class PartRow {
        final CalibrationItem item;
        final CalibrationItem.Part part;
        final int index;

        PartRow(CalibrationItem item, int index) {
            this.item = item;
            this.index = index;
            this.part = item.getParts().get(index);
        }
    }

    /** "另外 4 个：N14、N24、N40、N75", under the first parts of an item about many. */
    static final class MoreRow {
        final CalibrationItem item;
        final int from;

        MoreRow(CalibrationItem item, int from) {
            this.item = item;
            this.from = from;
        }
    }

    /** What the last column of a row does: a button, a pair of them, or a link. */
    private static final class RowAction {
        final String label;
        final boolean moves;
        final boolean enabled;
        final Runnable run;
        String secondLabel;
        Runnable second;
        boolean link;

        RowAction(String label, boolean moves, boolean enabled, Runnable run) {
            this.label = label;
            this.moves = moves;
            this.enabled = enabled;
            this.run = run;
        }

        RowAction withSecond(String label, Runnable run) {
            this.secondLabel = label;
            this.second = run;
            return this;
        }

        RowAction asLink() {
            this.link = true;
            return this;
        }
    }

    /**
     * The machinery of a session on this machine, which notes the issues each step accepted:
     * reopening them is what discarding the step does.
     */
    private static final class Recording extends CalibrationRunner.OnMachine {
        private final Map<String, List<Solutions.Issue>> accepted = new ConcurrentHashMap<>();

        Recording(ReferenceMachine machine, Configuration configuration,
                java.util.function.Supplier<org.openpnp.model.Job> job) {
            super(machine, configuration, job);
        }

        @Override
        public void accept(CalibrationPlan.Step step, Solutions.Issue issue) throws Exception {
            try {
                super.accept(step, issue);
            }
            finally {
                if (issue.getState() == Solutions.State.Solved) {
                    accepted.computeIfAbsent(step.getKey(), k -> new CopyOnWriteArrayList<>()).add(issue);
                }
            }
        }

        List<Solutions.Issue> accepted(String key) {
            List<Solutions.Issue> issues = accepted.get(key);
            return issues == null ? Collections.emptyList() : new ArrayList<>(issues);
        }

        /**
         * Not after each step, as a calibration run on its own saves: what was measured is saved
         * when it is applied, and quitting without applying it leaves machine.xml as it was.
         */
        @Override
        public void save() {
        }
    }

    private final Configuration configuration;
    private final MainFrame frame;
    private ReferenceMachine machine;
    private CalibrationPlan plan;
    private List<Solutions.Issue> others = new ArrayList<>();
    private SetupChecks checks = SetupChecks.none();
    private Date collected;
    private boolean collecting;
    /** The copy of machine.xml made before this collection's first suggestion was applied. */
    private File backup;
    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private List<CalibrationItem> items = new ArrayList<>();
    private final Set<String> checked = new HashSet<>();
    /** The suggestions shown before: a new one comes ticked, one unticked stays so. */
    private final Set<String> seen = new HashSet<>();
    private final Set<String> open = new HashSet<>();
    /** The items opened all the way, past their first parts. */
    private final Set<String> allParts = new HashSet<>();
    private boolean doneOpen;

    private CalibrationRunner runner;
    private CalibrationRunner.Session lastSession;
    private final Map<String, Live> live = new HashMap<>();
    private final Map<String, Date> finishedAt = new HashMap<>();
    private final Map<String, String> before = new ConcurrentHashMap<>();
    private final Map<String, String> after = new ConcurrentHashMap<>();

    private final RowsModel model = new RowsModel();
    private final AutoSelectTextTable table = new AutoSelectTextTable(model);
    private final DockPanel dock = new DockPanel();
    private final DockPanel.Tab stepsTab;
    private final DockPanel.Tab measureTab;
    private final Forms.Segmented scope;
    private final JButton collect;
    private final JButton applySelected;
    private final JButton oneClick;
    private final JButton dismissSelected;
    private final JButton skip;
    private final JButton stop;
    private final JButton openReport;
    private final Banner collectedBanner = new Banner();
    private final Banner pendingBanner = new Banner();
    private final JTextArea log = new JTextArea();
    private final JScrollPane logScroll;
    private final JTextArea report = new JTextArea();
    private final JPanel measureHolder = new JPanel(new BorderLayout());
    private MeasurementsPanel measurements;

    public CalibrationPanel(Configuration configuration, MainFrame frame) {
        this.configuration = configuration;
        this.frame = frame;
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(0, 10, 10, 10));
        putClientProperty(MainFrame.DOCK_PAGE, Boolean.TRUE);

        DockPanel.Toolbar toolbar = new DockPanel.Toolbar();
        collect = Ui.button(Translations.getString("CalibrationPanel.Collect"), Ui.iconSm("refresh"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Default);
        collect.setToolTipText(Translations.getString("CalibrationPanel.Collect.toolTipText")); //$NON-NLS-1$
        collect.addActionListener(e -> collect());
        applySelected = Ui.button("", Ui.iconSm("check"), Ui.Size.Sm, Ui.Variant.Primary); //$NON-NLS-1$ //$NON-NLS-2$
        applySelected.setToolTipText(Translations.getString("CalibrationPanel.ApplySelected.toolTipText")); //$NON-NLS-1$
        applySelected.addActionListener(e -> apply(checkedIssues()));
        oneClick = Ui.button("", null, Ui.Size.Sm, Ui.Variant.Default); //$NON-NLS-1$
        Ui.movesMachine(oneClick);
        oneClick.addActionListener(e -> measure(measureSteps()));
        dismissSelected = Ui.button(Translations.getString("CalibrationPanel.DismissSelected"), null, //$NON-NLS-1$
                Ui.Size.Sm, Ui.Variant.Ghost);
        dismissSelected.setToolTipText(Translations.getString("CalibrationPanel.DismissSelected.toolTipText")); //$NON-NLS-1$
        dismissSelected.addActionListener(e -> dismiss(checkedIssues()));
        skip = Ui.button(Translations.getString("CalibrationPanel.Skip"), Ui.iconSm("chevright"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Default);
        skip.setToolTipText(Translations.getString("CalibrationPanel.Skip.toolTipText")); //$NON-NLS-1$
        skip.addActionListener(e -> {
            if (runner != null) {
                runner.skipStep();
            }
        });
        stop = Ui.button(Translations.getString("CalibrationPanel.Stop"), Ui.iconSm("stop"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Danger);
        stop.setToolTipText(Translations.getString("CalibrationPanel.Stop.toolTipText")); //$NON-NLS-1$
        stop.addActionListener(e -> {
            if (runner != null) {
                runner.requestStop();
            }
        });
        openReport = Ui.button(Translations.getString("CalibrationPanel.OpenReport"), Ui.iconSm("external"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Ghost);
        openReport.setToolTipText(Translations.getString("CalibrationPanel.OpenReport.toolTipText")); //$NON-NLS-1$
        openReport.addActionListener(e -> openReport());
        for (JButton b : new JButton[] { collect, applySelected, oneClick, dismissSelected, skip, stop, openReport }) {
            b.setFocusable(false);
        }
        Ui.whyDisabled(collect, () -> Translations.getString(isRunning() ? "CalibrationPanel.Why.Running" //$NON-NLS-1$
                : "CalibrationPanel.Why.Collecting")); //$NON-NLS-1$
        Ui.whyDisabled(applySelected, () -> Translations.getString(isRunning() ? "CalibrationPanel.Why.Running" //$NON-NLS-1$
                : "CalibrationPanel.Why.NoneChecked")); //$NON-NLS-1$
        Ui.whyDisabled(dismissSelected, () -> Translations.getString(isRunning() ? "CalibrationPanel.Why.Running" //$NON-NLS-1$
                : "CalibrationPanel.Why.NoneChecked")); //$NON-NLS-1$
        Ui.whyDisabled(oneClick, () -> Translations.getString(isRunning() ? "CalibrationPanel.Why.Running" //$NON-NLS-1$
                : "CalibrationPanel.Why.NothingToMeasure")); //$NON-NLS-1$
        Ui.whyDisabled(skip, () -> Translations.getString("CalibrationPanel.Why.NotWaiting")); //$NON-NLS-1$
        Ui.whyDisabled(stop, () -> Translations.getString("CalibrationPanel.Why.NotRunning")); //$NON-NLS-1$
        scope = new Forms.Segmented(java.util.Arrays.asList(Boolean.TRUE, Boolean.FALSE),
                v -> Translations.getString(Boolean.TRUE.equals(v) ? "CalibrationPanel.Scope.Needed" //$NON-NLS-1$
                        : "CalibrationPanel.Scope.All")); //$NON-NLS-1$
        scope.setSelectedItem(Boolean.TRUE);
        scope.onChange(() -> rebuild());
        toolbar.add(collect);
        toolbar.add(applySelected);
        toolbar.add(oneClick);
        toolbar.add(dismissSelected);
        toolbar.add(skip);
        toolbar.add(stop);
        toolbar.add(openReport);
        toolbar.glue();
        toolbar.add(scope);

        table.setAutoCreateRowSorter(false);
        table.setRowSorter(null);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        CellRenderer renderer = new CellRenderer();
        table.setDefaultRenderer(Object.class, renderer);
        table.setDefaultRenderer(Boolean.class, renderer);
        AutoSelectTextTable.setEmptyText(table, Translations.getString("CalibrationPanel.Empty")); //$NON-NLS-1$
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                inspect();
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int column = table.columnAtPoint(e.getPoint());
                if (row < 0 || row >= model.rows.size()) {
                    return;
                }
                clicked(model.rows.get(row), row, column, e.getPoint());
            }
        });
        int[] widths = { 30, 230, 140, 120, 260, 150 };
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(0).setMaxWidth(34);
        org.openpnp.gui.support.TableUtils.installColumnWidthSavers(table,
                java.util.prefs.Preferences.userNodeForPackage(CalibrationPanel.class), "CalibrationPanel.items"); //$NON-NLS-1$

        log.setEditable(false);
        log.setFont(Ui.mono(11.5f, Font.PLAIN));
        log.setBorder(new EmptyBorder(6, 10, 6, 10));
        logScroll = new JScrollPane(log);
        logScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.border()));
        logScroll.setPreferredSize(new Dimension(300, 96));
        logScroll.setVisible(false);

        JPanel banners = new JPanel();
        banners.setOpaque(false);
        banners.setLayout(new BoxLayout(banners, BoxLayout.Y_AXIS));
        banners.setBorder(new EmptyBorder(0, 10, 0, 10));
        for (Banner banner : new Banner[] { collectedBanner, pendingBanner }) {
            banner.setAlignmentX(Component.LEFT_ALIGNMENT);
            JPanel spaced = new JPanel(new BorderLayout());
            spaced.setOpaque(false);
            spaced.setBorder(new EmptyBorder(8, 0, 0, 0));
            spaced.add(banner, BorderLayout.CENTER);
            spaced.setAlignmentX(Component.LEFT_ALIGNMENT);
            banners.add(spaced);
        }
        pendingBanner.setTone(Banner.Tone.Warn);
        pendingBanner.getParent().setVisible(false);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(banners, BorderLayout.NORTH);
        top.add(toolbar, BorderLayout.CENTER);
        JPanel tableArea = new JPanel(new BorderLayout());
        tableArea.setOpaque(false);
        tableArea.setBorder(new EmptyBorder(8, 0, 0, 0));
        tableArea.add(DockPanel.table(table), BorderLayout.CENTER);

        JPanel steps = new JPanel(new BorderLayout());
        steps.setOpaque(false);
        steps.add(top, BorderLayout.NORTH);
        steps.add(tableArea, BorderLayout.CENTER);
        steps.add(logScroll, BorderLayout.SOUTH);
        report.setEditable(false);
        report.setFont(Ui.mono(12f, Font.PLAIN));
        report.setBorder(new EmptyBorder(10, 12, 10, 12));
        report.setText(Translations.getString("CalibrationPanel.Report.None")); //$NON-NLS-1$
        JScrollPane reportScroll = new JScrollPane(report);
        reportScroll.setBorder(null);
        measureHolder.setOpaque(false);
        stepsTab = dock.addTab(Ui.iconSm("target"), Translations.getString("CalibrationPanel.Tab.Steps"), steps); //$NON-NLS-1$ //$NON-NLS-2$
        measureTab = dock.addTab(Ui.iconSm("activity"), Translations.getString("CalibrationPanel.Tab.Measure"), //$NON-NLS-1$ //$NON-NLS-2$
                measureHolder);
        dock.addTab(Ui.iconSm("file"), Translations.getString("CalibrationPanel.Tab.Report"), reportScroll); //$NON-NLS-1$ //$NON-NLS-2$
        dock.setMaximize(() -> frame.toggleDockMaximised());
        dock.addChangeListener(e -> inspect());
        add(dock, BorderLayout.CENTER);

        configuration.addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationComplete(Configuration configuration) throws Exception {
                if (configuration.getMachine() instanceof ReferenceMachine) {
                    machine = (ReferenceMachine) configuration.getMachine();
                    // Solutions asks for a search rather than calling a page, so that changing
                    // the milestone does not have to know there is a GUI.
                    machine.getSolutions().addPropertyChangeListener("rescanRequested", //$NON-NLS-1$
                            e -> SwingUtilities.invokeLater(() -> collect()));
                    SwingUtilities.invokeLater(() -> {
                        measurements = new MeasurementsPanel(machine, CalibrationPanel.this, () -> refresh());
                        measureHolder.add(measurements, BorderLayout.CENTER);
                        refresh();
                    });
                    // The first whole search after a delay, clear of the configuration loading
                    // and the cameras starting.
                    javax.swing.Timer first = new javax.swing.Timer(5000, e -> collect());
                    first.setRepeats(false);
                    first.start();
                }
            }
        });
        describe();
    }

    // ---- collecting ----------------------------------------------------------------------------

    /**
     * Collects what the machine needs: the whole search of Issues and Solutions, away from the
     * event thread, which it froze for as long as it took, then the calibration's own search and
     * the machine settings checks. Nothing moves.
     */
    public void collect() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::collect);
            return;
        }
        if (machine == null || collecting || isRunning()) {
            return;
        }
        collecting = true;
        describe();
        frame.getStatusBar().setBusy(true);
        Solutions solutions = machine.getSolutions();
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                solutions.findIssues();
                return null;
            }

            @Override
            protected void done() {
                collecting = false;
                frame.getStatusBar().setBusy(false);
                try {
                    get();
                    solutions.publishIssues();
                }
                catch (Exception e) {
                    UiUtils.showError(e.getCause() != null ? e.getCause() : e);
                }
                backup = null;
                refresh();
            }
        }.execute();
    }

    /**
     * An issue changed the machine by itself, a calibration it started having finished: the
     * element tree shows the machine again and the rows are laid out anew.
     */
    public void solutionChanged() {
        SwingUtilities.invokeLater(() -> {
            if (frame.getMachineSetupTab() != null) {
                frame.getMachineSetupTab().selectCurrentTreePath();
            }
            refresh();
        });
    }

    /**
     * Lays the rows out again from the issues the last whole search found and a new calibration
     * search, which is quick and moves nothing. On the event thread.
     */
    public void refresh() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }
        if (machine == null || isRunning()) {
            return;
        }
        UiUtils.messageBoxOnException(() -> {
            plan = CalibrationPlan.of(machine, CalibrationPlan.scan(machine, machine.getSolutions()));
            others = new ArrayList<>(machine.getSolutions().getIssues());
            checks = SetupChecks.of(machine);
            collected = new Date();
            rebuild();
        });
    }

    private boolean isRunning() {
        return runner != null;
    }

    private void rebuild() {
        String selected = keyOf(selectedRow());
        items = CalibrationItems.of(plan, others, checks, pending.values());
        boolean all = Boolean.FALSE.equals(scope.getSelectedItem());
        model.rows.clear();
        GroupRow group = null;
        for (CalibrationItem item : items) {
            if (item.getKind() == CalibrationItem.Kind.Dismissed && !all) {
                continue;
            }
            if (item.getKind() == CalibrationItem.Kind.Suggestion) {
                if (seen.add(item.getKey())) {
                    checked.add(item.getKey());
                }
            }
            if (group == null || group.kind != item.getKind()) {
                group = new GroupRow(item.getKind());
                group.folded = item.getKind() == CalibrationItem.Kind.Done && !doneOpen && !all;
                model.rows.add(group);
            }
            group.count++;
            if (group.folded) {
                continue;
            }
            model.rows.add(new ItemRow(item));
            int parts = item.getParts().size();
            if (open.contains(item.getKey()) && parts > 1) {
                int shown = parts > 3 && !allParts.contains(item.getKey()) ? 2 : parts;
                for (int i = 0; i < shown; i++) {
                    model.rows.add(new PartRow(item, i));
                }
                if (shown < parts) {
                    model.rows.add(new MoreRow(item, shown));
                }
            }
        }
        model.fireTableDataChanged();
        for (int row = 0; row < model.rows.size(); row++) {
            Object r = model.rows.get(row);
            table.setRowHeight(row, r instanceof ItemRow ? 34 : r instanceof PartRow ? 32 : 28);
            if (selected != null && selected.equals(keyOf(r))) {
                table.setRowSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
            }
        }
        describe();
        inspect();
    }

    /** Identifies a row across rebuilds. */
    private static String keyOf(Object row) {
        if (row instanceof ItemRow) {
            return "I|" + ((ItemRow) row).item.getKey(); //$NON-NLS-1$
        }
        if (row instanceof PartRow) {
            return "P|" + ((PartRow) row).item.getKey() + "|" + ((PartRow) row).index; //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (row instanceof GroupRow) {
            return "G|" + ((GroupRow) row).kind; //$NON-NLS-1$
        }
        if (row instanceof MoreRow) {
            return "M|" + ((MoreRow) row).item.getKey(); //$NON-NLS-1$
        }
        return null;
    }

    private Object selectedRow() {
        int row = table.getSelectedRow();
        return row < 0 || row >= model.rows.size() ? null : model.rows.get(row);
    }

    private List<CalibrationItem> itemsOf(CalibrationItem.Kind kind) {
        List<CalibrationItem> of = new ArrayList<>();
        for (CalibrationItem item : items) {
            if (item.getKind() == kind) {
                of.add(item);
            }
        }
        return of;
    }

    private List<Solutions.Issue> checkedIssues() {
        List<Solutions.Issue> issues = new ArrayList<>();
        for (CalibrationItem item : itemsOf(CalibrationItem.Kind.Suggestion)) {
            if (checked.contains(item.getKey())) {
                issues.addAll(item.getIssues());
            }
        }
        return issues;
    }

    /** The steps of every measurement row, which one-click calibration runs. */
    private List<CalibrationPlan.Step> measureSteps() {
        List<CalibrationPlan.Step> steps = new ArrayList<>();
        for (CalibrationItem item : itemsOf(CalibrationItem.Kind.Measure)) {
            steps.addAll(item.getSteps());
        }
        return steps;
    }

    /** Selects the step, or the first thing to do when none is given, and shows the page. */
    public void show(CalibrationStep kind, Object subject) {
        refresh();
        dock.select(stepsTab);
        for (CalibrationItem item : items) {
            boolean match = kind == null ? item.getKind().ordinal() <= CalibrationItem.Kind.Measure.ordinal()
                    : item.getStep() == kind;
            if (!match) {
                continue;
            }
            int index = -1;
            for (int i = 0; i < item.getParts().size(); i++) {
                if (subject != null && item.getParts().get(i).getElement() == subject) {
                    index = i;
                }
            }
            reveal(item, index);
            return;
        }
    }

    /** Selects the item, or the part of it, opening what hides it. */
    private void reveal(CalibrationItem item, int part) {
        if (item.getKind() == CalibrationItem.Kind.Done) {
            doneOpen = true;
        }
        if (item.getKind() == CalibrationItem.Kind.Dismissed) {
            scope.setSelectedItem(Boolean.FALSE);
        }
        if (part >= 0 && item.getParts().size() > 1) {
            open.add(item.getKey());
        }
        rebuild();
        String wanted = part >= 0 && item.getParts().size() > 1
                ? "P|" + item.getKey() + "|" + part : "I|" + item.getKey(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        for (int row = 0; row < model.rows.size(); row++) {
            if (wanted.equals(keyOf(model.rows.get(row)))) {
                table.setRowSelectionInterval(row, row);
                table.scrollRectToVisible(table.getCellRect(row, 0, true));
                return;
            }
        }
    }

    /**
     * Selects the first item of a kind, opened when it is about several elements. The UI ruler
     * photographs the page this way.
     */
    public boolean revealFirst(CalibrationItem.Kind kind) {
        for (CalibrationItem item : items) {
            if (item.getKind() == kind) {
                if (item.getParts().size() > 1) {
                    open.add(item.getKey());
                }
                reveal(item, -1);
                return true;
            }
        }
        return false;
    }

    private void describe() {
        int suggestionsChecked = 0;
        for (CalibrationItem item : itemsOf(CalibrationItem.Kind.Suggestion)) {
            if (checked.contains(item.getKey())) {
                suggestionsChecked++;
            }
        }
        int toMeasure = measureSteps().size();
        applySelected.setText(String.format(Translations.getString("CalibrationPanel.ApplySelected"), //$NON-NLS-1$
                suggestionsChecked));
        applySelected.setEnabled(!isRunning() && suggestionsChecked > 0);
        dismissSelected.setEnabled(!isRunning() && suggestionsChecked > 0);
        oneClick.setText(String.format(Translations.getString("CalibrationPanel.OneClick"), toMeasure)); //$NON-NLS-1$
        oneClick.setEnabled(!isRunning() && toMeasure > 0);
        collect.setEnabled(!isRunning() && !collecting && machine != null);
        skip.setVisible(isRunning());
        stop.setVisible(isRunning());
        skip.setEnabled(isRunning() && runner.isWaitingForPerson());
        stop.setEnabled(isRunning());
        openReport.setVisible(lastSession != null && lastSession.getReportDirectory() != null);
        logScroll.setVisible(isRunning() || lastSession != null);
        stepsTab.setCount(itemsOf(CalibrationItem.Kind.Suggestion).size() + itemsOf(CalibrationItem.Kind.Measure).size()
                + itemsOf(CalibrationItem.Kind.Pending).size());
        describeBanners();
        badge();
    }

    private void describeBanners() {
        if (collecting) {
            collectedBanner.setTitle(Translations.getString("CalibrationPanel.Banner.Collecting")); //$NON-NLS-1$
            collectedBanner.setText(Translations.getString("CalibrationPanel.Banner.CollectingText")); //$NON-NLS-1$
        }
        else if (collected == null) {
            collectedBanner.setTitle(Translations.getString("CalibrationPanel.Banner.NotYet")); //$NON-NLS-1$
            collectedBanner.setText(Translations.getString("CalibrationPanel.Banner.NotYetText")); //$NON-NLS-1$
        }
        else {
            collectedBanner.setTitle(String.format(Translations.getString("CalibrationPanel.Banner.Collected"), //$NON-NLS-1$
                    CLOCK.format(collected)));
            collectedBanner.setText(String.format(Translations.getString("CalibrationPanel.Banner.CollectedText"), //$NON-NLS-1$
                    itemsOf(CalibrationItem.Kind.Suggestion).size(), itemsOf(CalibrationItem.Kind.Measure).size(),
                    itemsOf(CalibrationItem.Kind.Hint).size()));
        }
        List<CalibrationItem> measured = itemsOf(CalibrationItem.Kind.Pending);
        pendingBanner.getParent().setVisible(!measured.isEmpty());
        if (!measured.isEmpty()) {
            List<String> titles = new ArrayList<>();
            for (CalibrationItem item : measured) {
                titles.add(item.getTitle());
            }
            pendingBanner.setTitle(String.format(Translations.getString("CalibrationPanel.Banner.Pending"), //$NON-NLS-1$
                    String.join(Translations.getString("CalibrationPanel.ListSeparator"), titles))); //$NON-NLS-1$
            pendingBanner.setText(Translations.getString("CalibrationPanel.Banner.PendingText")); //$NON-NLS-1$
            JButton discardAll = Ui.button(Translations.getString("CalibrationPanel.DiscardAll"), null, //$NON-NLS-1$
                    Ui.Size.Sm, Ui.Variant.Default);
            discardAll.setEnabled(!isRunning());
            discardAll.addActionListener(e -> discard(new ArrayList<>(pending.values())));
            JButton keepAll = Ui.button(Translations.getString("CalibrationPanel.KeepAll"), null, //$NON-NLS-1$
                    Ui.Size.Sm, Ui.Variant.Primary);
            keepAll.setEnabled(!isRunning());
            keepAll.addActionListener(e -> keep(new ArrayList<>(pending.values())));
            pendingBanner.setActions(discardAll, keepAll);
        }
        revalidate();
    }

    /**
     * The rail's count: what there is to do here, the suggestions, measurements and what waits.
     * The bell counts the hints as well, in red while something waits to be confirmed or the
     * machine settings lack what the machine needs.
     */
    private void badge() {
        int count = itemsOf(CalibrationItem.Kind.Suggestion).size() + itemsOf(CalibrationItem.Kind.Measure).size()
                + itemsOf(CalibrationItem.Kind.Pending).size();
        if (frame.getNavigation() != null) {
            frame.getNavigation().setBadge(this, count, org.openpnp.gui.shell.NavigationRail.Badge.Warn);
        }
        if (frame.getTopBar() != null) {
            List<CalibrationItem> hints = itemsOf(CalibrationItem.Kind.Hint);
            boolean severe = !itemsOf(CalibrationItem.Kind.Pending).isEmpty();
            for (CalibrationItem hint : hints) {
                severe |= hint.getCheck() != null;
            }
            frame.getTopBar().setNotifications(count, hints.size(), severe);
        }
    }

    // ---- what the rows do ----------------------------------------------------------------------

    private void clicked(Object row, int index, int column, java.awt.Point point) {
        if (row instanceof GroupRow) {
            GroupRow group = (GroupRow) row;
            if (group.kind == CalibrationItem.Kind.Done) {
                doneOpen = !doneOpen;
                rebuild();
            }
            return;
        }
        if (row instanceof MoreRow) {
            allParts.add(((MoreRow) row).item.getKey());
            rebuild();
            return;
        }
        if (row instanceof ItemRow && column == 2 && ((ItemRow) row).item.getParts().size() > 1) {
            String key = ((ItemRow) row).item.getKey();
            if (!open.remove(key)) {
                open.add(key);
                allParts.remove(key);
            }
            rebuild();
            return;
        }
        if (column == 5) {
            RowAction action = actionOf(row);
            if (action == null || !action.enabled) {
                return;
            }
            if (action.second != null && isSecond(index, column, point)) {
                action.second.run();
            }
            else {
                action.run.run();
            }
        }
    }

    /** Whether the click fell on the first of a pair of buttons, as the cell lays them out. */
    private boolean isSecond(int row, int column, java.awt.Point point) {
        java.awt.Rectangle cell = table.getCellRect(row, column, false);
        Component rendered = table.prepareRenderer(table.getCellRenderer(row, column), row, column);
        rendered.setBounds(0, 0, cell.width, cell.height);
        if (rendered instanceof java.awt.Container) {
            ((java.awt.Container) rendered).doLayout();
            Component hit = ((java.awt.Container) rendered).getComponentAt(point.x - cell.x, point.y - cell.y);
            return hit != null && "second".equals(hit.getName()); //$NON-NLS-1$
        }
        return false;
    }

    private RowAction actionOf(Object row) {
        CalibrationItem item = itemOf(row);
        if (item == null || row instanceof MoreRow) {
            return null;
        }
        CalibrationItem.Part part = row instanceof PartRow ? ((PartRow) row).part : null;
        List<Solutions.Issue> issues = part == null ? item.getIssues()
                : part.getIssue() == null ? List.of() : List.of(part.getIssue());
        switch (item.getKind()) {
            case Suggestion:
                return new RowAction(Translations.getString("CalibrationPanel.Action.Apply"), false, !isRunning(), //$NON-NLS-1$
                        () -> apply(issues));
            case Measure: {
                List<CalibrationPlan.Step> steps = part == null ? item.getSteps() : List.of(part.getStep());
                boolean waiting = part == null ? item.isWaiting()
                        : part.getStep().getStatus() == CalibrationPlan.Status.Waiting;
                return new RowAction(Translations.getString(item.getStep() == CalibrationStep.Home
                        ? "CalibrationPanel.Action.Home" : "CalibrationPanel.Action.Measure"), true, //$NON-NLS-1$ //$NON-NLS-2$
                        !isRunning() && !waiting, () -> measure(steps));
            }
            case Pending: {
                List<Pending> of = part == null ? item.getPending() : List.of(part.getPending());
                boolean discardable = true;
                for (Pending p : of) {
                    discardable &= p.canBeDiscarded();
                }
                RowAction keep = new RowAction(Translations.getString("CalibrationPanel.Action.Apply"), false, //$NON-NLS-1$
                        !isRunning(), () -> keep(of));
                return discardable ? keep.withSecond(Translations.getString("CalibrationPanel.Action.DiscardShort"), //$NON-NLS-1$
                        () -> discard(of)) : keep;
            }
            case Hint:
                if (item.getCheck() != null) {
                    String topic = item.getCheck().topic;
                    return new RowAction(String.format(Translations.getString("CalibrationPanel.Action.Settings"), //$NON-NLS-1$
                            Translations.getString("MachineSettings.Topic." + topic)), false, true, //$NON-NLS-1$
                            () -> frame.showMachineSettings(topic)).asLink();
                }
                if (!issues.isEmpty() && issues.get(0).canBeAccepted()) {
                    return new RowAction(Translations.getString("CalibrationPanel.Action.Accept"), false, //$NON-NLS-1$
                            !isRunning(), () -> apply(issues));
                }
                return null;
            case Dismissed:
                return new RowAction(Translations.getString("CalibrationPanel.Action.Restore"), false, !isRunning(), //$NON-NLS-1$
                        () -> restore(part == null ? item.getParts() : List.of(part)));
            default:
                return null;
        }
    }

    private static CalibrationItem itemOf(Object row) {
        return row instanceof ItemRow ? ((ItemRow) row).item
                : row instanceof PartRow ? ((PartRow) row).item
                : row instanceof MoreRow ? ((MoreRow) row).item : null;
    }

    /** Applies suggestions: writes what they propose, after a copy of machine.xml, and saves. */
    private void apply(List<Solutions.Issue> issues) {
        if (issues.isEmpty() || isRunning()) {
            return;
        }
        UiUtils.messageBoxOnException(() -> {
            if (backup == null) {
                backup = Backups.backup(configuration, "suggestions"); //$NON-NLS-1$
            }
            for (Solutions.Issue issue : issues) {
                if (issue.getState() != Solutions.State.Solved) {
                    issue.setStateCall(Solutions.State.Solved);
                }
            }
            configuration.save();
        });
        refresh();
    }

    private void dismiss(List<Solutions.Issue> issues) {
        if (issues.isEmpty() || isRunning()) {
            return;
        }
        UiUtils.messageBoxOnException(() -> {
            for (Solutions.Issue issue : issues) {
                if (issue.getState() == Solutions.State.Open) {
                    issue.setStateCall(Solutions.State.Dismissed);
                }
            }
        });
        refresh();
    }

    /** Takes back a dismissal: the issue or the step's issues are open again. */
    private void restore(List<CalibrationItem.Part> parts) {
        UiUtils.messageBoxOnException(() -> {
            for (CalibrationItem.Part part : parts) {
                List<Solutions.Issue> issues = new ArrayList<>();
                if (part.getIssue() != null) {
                    issues.add(part.getIssue());
                }
                else if (part.getStep() != null) {
                    issues.addAll(part.getStep().getIssues());
                }
                for (Solutions.Issue issue : issues) {
                    if (issue.getState() == Solutions.State.Dismissed) {
                        issue.setStateCall(Solutions.State.Open);
                    }
                }
            }
        });
        refresh();
    }

    /** Applies what was measured: it is in effect already, and now it is saved. */
    private void keep(List<Pending> kept) {
        for (Pending p : kept) {
            pending.remove(p.getKey());
        }
        UiUtils.messageBoxOnException(() -> configuration.save());
        refresh();
    }

    /** Discards everything measured and not applied. The UI ruler starts a scene clean this way. */
    public void discardPending() {
        discard(new ArrayList<>(pending.values()));
    }

    /**
     * Discards what was measured: each issue a step accepted is reopened, last first, which puts
     * back the values it found, a frame compensation is taken out, and the configuration saved.
     */
    private void discard(List<Pending> discarded) {
        if (discarded.isEmpty() || isRunning()) {
            return;
        }
        UiUtils.messageBoxOnException(() -> {
            for (Pending p : discarded) {
                if (p.getCompensation() != null && p.getCompensation().canBeUndone()) {
                    machine.getMachineDiagnostics().undoCompensation(machine, p.getCompensation());
                }
                List<Solutions.Issue> accepted = new ArrayList<>(p.getAccepted());
                Collections.reverse(accepted);
                for (Solutions.Issue issue : accepted) {
                    if (issue.getState() == Solutions.State.Solved) {
                        issue.setStateCall(Solutions.State.Open);
                    }
                }
                pending.remove(p.getKey());
            }
            configuration.save();
        });
        refresh();
    }

    /**
     * What was measured and neither applied nor discarded is discarded when Pono closes, unless
     * the user applies it here.
     * 
     * @return False to stay open.
     */
    public boolean settleBeforeQuit(Component parent) {
        if (pending.isEmpty()) {
            return true;
        }
        List<String> titles = new ArrayList<>();
        for (Pending p : pending.values()) {
            titles.add(p.getKind().getName() + " \u00b7 " + p.getSubjectName()); //$NON-NLS-1$
        }
        Dialogs.Content content = new Dialogs.Content().tone(Dialogs.Tone.Warn, "alert") //$NON-NLS-1$
                .title(String.format(Translations.getString("CalibrationPanel.Quit.Title"), pending.size())) //$NON-NLS-1$
                .what(Translations.getString("CalibrationPanel.Quit.What")) //$NON-NLS-1$
                .list(String.join("\n", titles)); //$NON-NLS-1$
        int answer = Dialogs.show(parent, content, List.of(Dialogs.Choice.cancel(),
                Dialogs.Choice.plain(Translations.getString("CalibrationPanel.Quit.Keep")), //$NON-NLS-1$
                Dialogs.Choice.primary(Translations.getString("CalibrationPanel.Quit.Discard"))), 0, 2); //$NON-NLS-1$
        if (answer == 2) {
            discard(new ArrayList<>(pending.values()));
            return pending.isEmpty();
        }
        if (answer == 1) {
            keep(new ArrayList<>(pending.values()));
            return true;
        }
        return false;
    }

    /**
     * Treats the first suggestion as a measurement would be treated: what it writes is in effect
     * and waits to be applied or discarded. The UI ruler photographs a measured row this way,
     * without a machine to measure with.
     */
    public boolean previewPending() {
        List<CalibrationItem> suggestions = itemsOf(CalibrationItem.Kind.Suggestion);
        if (suggestions.isEmpty() || machine == null) {
            return false;
        }
        CalibrationItem item = suggestions.get(0);
        UiUtils.messageBoxOnException(() -> {
            // One element at a time, as a run measures one step at a time.
            for (CalibrationItem.Part part : item.getParts()) {
                String was = configuration.machineXml();
                part.getIssue().setStateCall(Solutions.State.Solved);
                String is = configuration.machineXml();
                CalibrationStep kind = item.getStep() != null ? item.getStep() : CalibrationStep.SubPixel;
                String key = kind.name() + ":" + part.getSubject(); //$NON-NLS-1$
                pending.put(key, new Pending(key, kind, part.getElement(), part.getSubject(),
                        SettingsDiff.between(was, is), List.of(part.getIssue()), null, "", new Date(), was)); //$NON-NLS-1$
            }
        });
        refresh();
        return !pending.isEmpty();
    }

    // ---- running -----------------------------------------------------------------------------

    private void measure(List<CalibrationPlan.Step> steps) {
        if (steps.isEmpty() || isRunning() || plan == null) {
            return;
        }
        Set<String> leaveOut = confirm(steps);
        if (leaveOut == null) {
            return;
        }
        List<String> keys = new ArrayList<>();
        for (CalibrationPlan.Step step : steps) {
            keys.add(step.getKey());
        }
        start(keys, leaveOut);
    }

    /**
     * Lists the steps about to run and lets the ones that need someone be left out this time.
     * 
     * @return The steps to leave out, or null for Cancel.
     */
    private Set<String> confirm(List<CalibrationPlan.Step> steps) {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        Map<String, JCheckBox> leaveOut = new HashMap<>();
        for (CalibrationPlan.Step step : steps) {
            if (step.isNeedsPerson()) {
                JCheckBox box = Forms.check(String.format(
                        Translations.getString("CalibrationPanel.Confirm.SkipThisTime"), step.getTitle())); //$NON-NLS-1$
                box.setAlignmentX(Component.LEFT_ALIGNMENT);
                leaveOut.put(step.getKey(), box);
                body.add(box);
            }
            else {
                JLabel line = Ui.t2("\u00b7 " + step.getTitle()); //$NON-NLS-1$
                line.setAlignmentX(Component.LEFT_ALIGNMENT);
                body.add(line);
            }
            body.add(Box.createVerticalStrut(3));
        }
        Dialogs.Content content = new Dialogs.Content().tone(Dialogs.Tone.Warn, "zap") //$NON-NLS-1$
                .title(String.format(Translations.getString("CalibrationPanel.Confirm.Title"), steps.size())) //$NON-NLS-1$
                .what(Translations.getString("CalibrationPanel.Confirm.What")) //$NON-NLS-1$
                .more(Translations.getString("CalibrationPanel.Confirm.Backup")) //$NON-NLS-1$
                .body(body);
        int answer = Dialogs.show(SwingUtilities.getWindowAncestor(this), content,
                List.of(Dialogs.Choice.cancel(), new Dialogs.Choice(
                        Translations.getString("CalibrationPanel.Confirm.Start"), Ui.icon("zap", 13, Ui.warn()), //$NON-NLS-1$ //$NON-NLS-2$
                        Ui.Variant.Primary)),
                0, 1);
        if (answer != 1) {
            return null;
        }
        Set<String> skipped = new HashSet<>();
        for (Map.Entry<String, JCheckBox> e : leaveOut.entrySet()) {
            if (e.getValue().isSelected()) {
                skipped.add(e.getKey());
            }
        }
        return skipped;
    }

    /** machine.xml as it stands, or null when it cannot be written, which leaves nothing to compare. */
    private String snapshot() {
        try {
            return configuration.machineXml();
        }
        catch (Exception e) {
            Logger.warn(e, "Calibration: the machine could not be written out to compare."); //$NON-NLS-1$
            return null;
        }
    }

    private void start(List<String> keys, Set<String> leaveOut) {
        live.clear();
        log.setText(""); //$NON-NLS-1$
        before.clear();
        after.clear();
        for (String key : keys) {
            Pending older = pending.get(key);
            if (older != null && older.getBeforeXml() != null) {
                // Measured again before it was applied: discarding goes back to before the first.
                before.put(key, older.getBeforeXml());
            }
        }
        CalibrationPlan started = plan;
        Recording machinery = new Recording(machine, configuration,
                () -> frame.getJobTab() == null ? null : frame.getJobTab().getJob());
        runner = new CalibrationRunner(machinery, new CalibrationRunner.Listener() {
            @Override
            public void stepStarted(CalibrationPlan.Step step) {
                String xml = snapshot();
                if (xml != null) {
                    before.putIfAbsent(step.getKey(), xml);
                }
                SwingUtilities.invokeLater(() -> {
                    live.put(step.getKey(), Live.Running);
                    model.fireTableDataChanged();
                    frame.getStatusBar().setBusy(true);
                });
            }

            @Override
            public void stepFinished(CalibrationPlan.Step step, CalibrationRunner.Outcome outcome, String message) {
                if (outcome == CalibrationRunner.Outcome.Done) {
                    String xml = snapshot();
                    if (xml != null) {
                        after.put(step.getKey(), xml);
                    }
                }
                SwingUtilities.invokeLater(() -> {
                    live.put(step.getKey(), outcome == CalibrationRunner.Outcome.Done ? Live.Done
                            : outcome == CalibrationRunner.Outcome.Skipped ? Live.Skipped : Live.Failed);
                    finishedAt.put(step.getKey(), new Date());
                    frame.hideInstructions();
                    model.fireTableDataChanged();
                    describe();
                });
            }

            @Override
            public void waitingForPerson(CalibrationPlan.Step step) {
                SwingUtilities.invokeLater(() -> {
                    live.put(step.getKey(), Live.Waiting);
                    model.fireTableDataChanged();
                    describe();
                    frame.showInstructions(String.format(Translations.getString("CalibrationPanel.Person.Title"), //$NON-NLS-1$
                            step.getTitle()), step.getKind().getDescription(), true, true,
                            Translations.getString("CalibrationPanel.Person.Continue"), //$NON-NLS-1$
                            e -> runner.skipStep(), e -> runner.continueAfterPerson());
                });
            }

            @Override
            public void log(String line) {
                SwingUtilities.invokeLater(() -> {
                    log.append(TIME.format(new Date()) + "  " + Translations.translateText(line) + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
                    log.setCaretPosition(log.getDocument().getLength());
                });
            }

            @Override
            public void finished(CalibrationRunner.Session session) {
                SwingUtilities.invokeLater(() -> finish(session, machinery, started));
            }
        });
        describe();
        runner.start(keys, leaveOut).whenComplete((session, t) -> SwingUtilities.invokeLater(() -> {
            if (t != null) {
                runner = null;
                frame.getStatusBar().setBusy(false);
                frame.hideInstructions();
                UiUtils.showError(t);
                describe();
                refresh();
            }
        }));
    }

    private void finish(CalibrationRunner.Session session, Recording machinery, CalibrationPlan started) {
        lastSession = session;
        runner = null;
        frame.getStatusBar().setBusy(false);
        frame.hideInstructions();
        for (CalibrationRunner.Result result : session.getResults()) {
            if (result.getOutcome() != CalibrationRunner.Outcome.Done) {
                continue;
            }
            String key = result.getKey();
            CalibrationPlan.Step step = started == null ? null : started.getStep(key);
            String was = before.get(key);
            String is = after.containsKey(key) ? after.get(key) : snapshot();
            if (step == null || was == null || is == null) {
                continue;
            }
            List<SettingsDiff.Difference> differences;
            try {
                differences = SettingsDiff.between(was, is);
            }
            catch (Exception e) {
                Logger.warn(e, "Calibration: comparing machine.xml before and after {} failed.", key); //$NON-NLS-1$
                differences = List.of();
            }
            MachineDiagnostics.CompensationOutcome compensation = step.getKind() == CalibrationStep.FrameCompensation
                    ? machinery.getLastCompensation() : null;
            if (differences.isEmpty() && (compensation == null || !compensation.canBeUndone())) {
                continue;
            }
            List<Solutions.Issue> accepted = new ArrayList<>();
            Pending older = pending.get(key);
            if (older != null) {
                accepted.addAll(older.getAccepted());
            }
            accepted.addAll(machinery.accepted(key));
            pending.put(key, new Pending(key, step.getKind(), step.getSubject(), step.getSubjectName(), differences,
                    accepted, compensation, result.getMessage(), new Date(), was));
        }
        StringBuilder text = new StringBuilder();
        text.append(String.format(Translations.getString("CalibrationPanel.Report.Header"), //$NON-NLS-1$
                new SimpleDateFormat("yyyy-MM-dd HH:mm").format(session.getStarted()), //$NON-NLS-1$
                session.getBackup() == null ? "\u2014" : session.getBackup().getName())); //$NON-NLS-1$
        text.append("\n\n"); //$NON-NLS-1$
        for (CalibrationRunner.Result result : session.getResults()) {
            text.append(result.getOutcome().getName()).append("  ").append(result.getTitle()); //$NON-NLS-1$
            if (result.getMessage() != null && !result.getMessage().isEmpty()) {
                text.append("  \u2014 ").append(Translations.translateText(result.getMessage())); //$NON-NLS-1$
            }
            text.append('\n');
            for (String change : result.getChanges()) {
                text.append("    ").append(change).append('\n'); //$NON-NLS-1$
            }
        }
        if (session.getFailure() != null) {
            text.append('\n').append(Translations.translateText(session.getFailure())).append('\n');
        }
        report.setText(text.toString());
        report.setCaretPosition(0);
        live.clear();
        before.clear();
        after.clear();
        refresh();
        // What the steps changed is found again, the issues outside calibration with it.
        collect();
    }

    private void openReport() {
        UiUtils.messageBoxOnException(() -> {
            File directory = lastSession == null ? null : lastSession.getReportDirectory();
            if (directory == null || !directory.isDirectory()) {
                throw new Exception(Translations.getString("MachineDiagnosticsWizard.Error.NoReport")); //$NON-NLS-1$
            }
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                throw new Exception(directory.getAbsolutePath());
            }
            Desktop.getDesktop().open(directory);
        });
    }

    // ---- the properties column ---------------------------------------------------------------

    private void inspect() {
        if (frame.getInspector() == null) {
            return;
        }
        if (dock.getSelectedTab() == measureTab) {
            if (measurements != null) {
                measurements.inspect();
            }
            return;
        }
        Object row = selectedRow();
        CalibrationItem item = itemOf(row);
        if (item == null || dock.getSelectedTab() != stepsTab) {
            frame.getInspector().show(this, null);
            return;
        }
        CalibrationItem.Part part = row instanceof PartRow ? ((PartRow) row).part
                : item.getParts().size() == 1 ? item.getParts().get(0) : null;
        String subtitle = subtitle(item, part);
        // Built when it is shown, in the theme of the moment.
        frame.getInspector().show(this, keyOf(row), null, item.getTitle(), subtitle,
                Ui.icon(iconOf(item.getKind()), 16, Ui.accent()),
                () -> List.of(PropertySheetPresenter.sheet(item.getTitle(), pane(item, part))));
    }

    private static String iconOf(CalibrationItem.Kind kind) {
        switch (kind) {
            case Suggestion:
                return "edit"; //$NON-NLS-1$
            case Measure:
                return "target"; //$NON-NLS-1$
            case Pending:
                return "activity"; //$NON-NLS-1$
            case Hint:
                return "info"; //$NON-NLS-1$
            default:
                return "check"; //$NON-NLS-1$
        }
    }

    /** "有建议值 · 6 个吸嘴头 · 不移动机器". */
    private String subtitle(CalibrationItem item, CalibrationItem.Part part) {
        String subjects = part != null ? part.getSubject() : item.getSubjects();
        List<String> bits = new ArrayList<>();
        bits.add(item.getKind() == CalibrationItem.Kind.Hint && item.getCheck() != null
                ? String.format(Translations.getString("CalibrationPanel.Subtitle.Settings"), //$NON-NLS-1$
                        Translations.getString("MachineSettings.Topic." + item.getCheck().topic)) //$NON-NLS-1$
                : item.getKind().getName());
        bits.add(subjects);
        switch (item.getKind()) {
            case Suggestion:
                bits.add(Translations.getString("CalibrationPanel.Subtitle.Still")); //$NON-NLS-1$
                break;
            case Measure:
                if (item.isMovesMachine()) {
                    bits.add(Translations.getString("CalibrationPanel.Subtitle.Moves")); //$NON-NLS-1$
                }
                if (item.isNeedsPerson()) {
                    bits.add(Translations.getString("CalibrationPanel.Subtitle.Person")); //$NON-NLS-1$
                }
                break;
            case Pending: {
                Pending p = part != null ? part.getPending() : item.getPending().get(0);
                bits.add(String.format(Translations.getString("CalibrationPanel.Subtitle.Measured"), //$NON-NLS-1$
                        CLOCK.format(p.getWhen())));
                break;
            }
            default:
                break;
        }
        return String.join(" \u00b7 ", bits); //$NON-NLS-1$
    }

    private JPanel pane(CalibrationItem item, CalibrationItem.Part part) {
        JPanel sections = new JPanel();
        sections.setOpaque(false);
        sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));
        List<JButton> buttons = new ArrayList<>();
        List<CalibrationItem.Part> parts = part != null ? List.of(part) : item.getParts();
        List<Solutions.Issue> issues = new ArrayList<>();
        for (CalibrationItem.Part p : parts) {
            if (p.getIssue() != null) {
                issues.add(p.getIssue());
            }
        }
        switch (item.getKind()) {
            case Suggestion:
                suggestionPane(sections, buttons, item, parts, issues);
                break;
            case Measure:
                measurePane(sections, buttons, item, parts, issues);
                break;
            case Pending:
                pendingPane(sections, buttons, item, parts);
                break;
            case Hint:
                hintPane(sections, buttons, item, parts, issues);
                break;
            case Done:
                if (item.getStep() != null) {
                    sections.add(section("info", "CalibrationPanel.Section.What", //$NON-NLS-1$ //$NON-NLS-2$
                            Forms.paragraph(item.getStep().getDescription())));
                }
                break;
            case Dismissed: {
                String what = !issues.isEmpty() ? issues.get(0).getIssue() + "\n" + issues.get(0).getSolution() //$NON-NLS-1$
                        : item.getStep() != null ? item.getStep().getDescription() : ""; //$NON-NLS-1$
                sections.add(section("info", "CalibrationPanel.Section.What", Forms.paragraph(what))); //$NON-NLS-1$ //$NON-NLS-2$
                JButton restore = Ui.button(Translations.getString("CalibrationPanel.Action.Restore"), null, //$NON-NLS-1$
                        Ui.Size.Md, Ui.Variant.Primary);
                restore.setToolTipText(Translations.getString("CalibrationPanel.Action.Restore.toolTipText")); //$NON-NLS-1$
                restore.setEnabled(!isRunning());
                restore.addActionListener(e -> restore(parts));
                buttons.add(restore);
                break;
            }
            default:
                break;
        }
        sections.add(Box.createVerticalGlue());

        JPanel foot = new JPanel();
        foot.setOpaque(false);
        foot.setLayout(new BoxLayout(foot, BoxLayout.X_AXIS));
        foot.setBorder(new EmptyBorder(10, 12, 12, 12));
        foot.add(Box.createHorizontalGlue());
        for (int i = 0; i < buttons.size(); i++) {
            if (i > 0) {
                foot.add(Box.createHorizontalStrut(6));
            }
            foot.add(buttons.get(i));
        }
        JPanel pane = new JPanel(new BorderLayout());
        pane.setOpaque(false);
        JScrollPane scroll = new JScrollPane(new org.openpnp.gui.shell.WidthTracking(sections));
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        pane.add(scroll, BorderLayout.CENTER);
        if (!buttons.isEmpty()) {
            pane.add(foot, BorderLayout.SOUTH);
        }
        return pane;
    }

    private void suggestionPane(JPanel sections, List<JButton> buttons, CalibrationItem item,
            List<CalibrationItem.Part> parts, List<Solutions.Issue> issues) {
        Solutions.Issue first = issues.isEmpty() ? null : issues.get(0);
        if (first != null) {
            String why = Html.plain(first.getExtendedDescription());
            sections.add(section("info", "CalibrationPanel.Section.Why", //$NON-NLS-1$ //$NON-NLS-2$
                    Forms.paragraph(first.getIssue() + "\n" + (why.isEmpty() ? first.getSolution() : why)))); //$NON-NLS-1$
        }
        JPanel grid = grid();
        List<Runnable> updates = new ArrayList<>();
        int row = 0;
        header(grid, row++, "CalibrationPanel.Change.Subject", "CalibrationPanel.Change.Now", //$NON-NLS-1$ //$NON-NLS-2$
                "CalibrationPanel.Change.New"); //$NON-NLS-1$
        for (CalibrationItem.Part p : parts) {
            if (row > LISTED) {
                cell(grid, row++, 0, 4, Ui.muted(String.format(Translations.getString("CalibrationPanel.Change.More"), //$NON-NLS-1$
                        parts.size() - LISTED)));
                break;
            }
            SettingChange change = p.getChange();
            cell(grid, row, 0, 1, Ui.t2(p.getSubject()));
            cell(grid, row, 1, 1, Ui.t2(value(change == null ? null : change.getCurrentValue())));
            cell(grid, row, 2, 1, Ui.muted("\u2192")); //$NON-NLS-1$
            IssueInputs.Editor own = p.getIssue() == null ? null
                    : IssueInputs.editor(p.getIssue(), configuration.getSystemUnits(), () -> table.repaint());
            if (own != null) {
                cell(grid, row, 3, 1, own.component);
                updates.add(own.reload);
            }
            else {
                JLabel proposed = new JLabel(value(change == null ? null : change.getProposedValue()));
                proposed.setFont(Ui.weighted(Ui.BASE, 600));
                cell(grid, row, 3, 1, proposed);
                updates.add(() -> proposed.setText(value(change == null ? null : change.getProposedValue())));
            }
            row++;
        }
        JPanel what = new JPanel();
        what.setOpaque(false);
        what.setLayout(new BoxLayout(what, BoxLayout.Y_AXIS));
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        what.add(grid);
        if (first != null && IssueInputs.editable(first) && (issues.size() > 1
                || IssueInputs.editor(first, configuration.getSystemUnits(), () -> {
                }) == null)) {
            what.add(Box.createVerticalStrut(10));
            JLabel label = Ui.muted(Translations.getString(issues.size() > 1 ? "CalibrationPanel.Inputs.All" //$NON-NLS-1$
                    : "CalibrationPanel.Inputs.One")); //$NON-NLS-1$
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            what.add(label);
            what.add(Box.createVerticalStrut(4));
            JComponent editors = IssueInputs.editors(issues,
                    configuration.getSystemUnits(), () -> {
                        for (Runnable update : updates) {
                            update.run();
                        }
                        table.repaint();
                    });
            editors.setAlignmentX(Component.LEFT_ALIGNMENT);
            what.add(editors);
        }
        sections.add(section("edit", "CalibrationPanel.Section.Change", what)); //$NON-NLS-1$ //$NON-NLS-2$
        sections.add(section("file", "CalibrationPanel.Section.Source", Forms.paragraph(source(item)))); //$NON-NLS-1$ //$NON-NLS-2$

        JButton dismiss = Ui.button(Translations.getString("CalibrationPanel.Action.Dismiss"), null, //$NON-NLS-1$
                Ui.Size.Md, Ui.Variant.Default);
        dismiss.setToolTipText(Translations.getString("CalibrationPanel.Action.Dismiss.toolTipText")); //$NON-NLS-1$
        dismiss.setEnabled(!isRunning());
        dismiss.addActionListener(e -> dismiss(issues));
        JButton apply = Ui.button(issues.size() > 1
                ? String.format(Translations.getString("CalibrationPanel.Action.ApplyTo"), //$NON-NLS-1$
                        CalibrationItem.count(issues.size(), parts.get(0).getElement()))
                : Translations.getString("CalibrationPanel.Action.Apply"), null, Ui.Size.Md, Ui.Variant.Primary); //$NON-NLS-1$
        apply.setToolTipText(Translations.getString("CalibrationPanel.Action.Apply.toolTipText")); //$NON-NLS-1$
        apply.setEnabled(!isRunning());
        apply.addActionListener(e -> apply(issues));
        buttons.add(dismiss);
        buttons.add(apply);
    }

    private void measurePane(JPanel sections, List<JButton> buttons, CalibrationItem item,
            List<CalibrationItem.Part> parts, List<Solutions.Issue> issues) {
        if (item.getStep() != null) {
            sections.add(section("info", "CalibrationPanel.Section.What", //$NON-NLS-1$ //$NON-NLS-2$
                    Forms.paragraph(item.getStep().getDescription())));
        }
        JPanel why = new JPanel();
        why.setOpaque(false);
        why.setLayout(new BoxLayout(why, BoxLayout.Y_AXIS));
        List<CalibrationPlan.Step> steps = new ArrayList<>();
        for (CalibrationItem.Part p : parts) {
            if (p.getStep() != null) {
                steps.add(p.getStep());
            }
        }
        for (CalibrationPlan.Step step : steps) {
            List<CalibrationPlan.Basis> basis = step.getBasis(plan == null ? null : plan.getResults());
            String line = basis.isEmpty() ? step.getStatus().getName() : describe(basis.get(0));
            JTextArea note = Forms.paragraph(steps.size() > 1 ? step.getSubjectName() + "  " + line : line); //$NON-NLS-1$
            note.setAlignmentX(Component.LEFT_ALIGNMENT);
            why.add(note);
            why.add(Box.createVerticalStrut(4));
        }
        sections.add(section("activity", "CalibrationPanel.Section.WhyMeasure", why)); //$NON-NLS-1$ //$NON-NLS-2$
        Set<CalibrationPlan.Step> before = new java.util.LinkedHashSet<>();
        for (CalibrationPlan.Step step : steps) {
            before.addAll(step.getPrerequisites());
        }
        before.removeAll(steps);
        if (!before.isEmpty()) {
            JPanel chips = new JPanel(new org.openpnp.gui.support.WrapLayout(FlowLayout.LEFT, 6, 4));
            chips.setOpaque(false);
            for (CalibrationPlan.Step p : before) {
                boolean settled = p.getStatus().isSettled();
                chips.add(new Chip(settled ? p.getTitle()
                        : String.format(Translations.getString("CalibrationPanel.Before.First"), p.getTitle()), //$NON-NLS-1$
                        settled ? Chip.Tone.Ok : Chip.Tone.Warn, Chip.Shape.Status));
            }
            sections.add(section("check", "CalibrationPanel.Section.Before", chips)); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (issues.size() == 1 && machine != null) {
            IssuePanel controls = new IssuePanel(issues.get(0), machine).controlsOnly();
            if (controls.hasControls()) {
                controls.setOpaque(false);
                sections.add(section("sliders", "CalibrationPanel.Section.Inputs", controls)); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
        else if (issues.size() > 1 && IssueInputs.editable(issues.get(0))) {
            JPanel inputs = new JPanel();
            inputs.setOpaque(false);
            inputs.setLayout(new BoxLayout(inputs, BoxLayout.Y_AXIS));
            JLabel label = Ui.muted(Translations.getString("CalibrationPanel.Inputs.All")); //$NON-NLS-1$
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            inputs.add(label);
            inputs.add(Box.createVerticalStrut(4));
            JComponent editors = IssueInputs.editors(issues,
                    configuration.getSystemUnits(), () -> table.repaint());
            editors.setAlignmentX(Component.LEFT_ALIGNMENT);
            inputs.add(editors);
            sections.add(section("sliders", "CalibrationPanel.Section.Inputs", inputs)); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (item.canBeDismissed()) {
            JButton dismiss = Ui.button(Translations.getString("CalibrationPanel.Action.Dismiss"), null, //$NON-NLS-1$
                    Ui.Size.Md, Ui.Variant.Default);
            dismiss.setToolTipText(Translations.getString("CalibrationPanel.Action.Dismiss.toolTipText")); //$NON-NLS-1$
            dismiss.setEnabled(!isRunning());
            dismiss.addActionListener(e -> dismiss(issues));
            buttons.add(dismiss);
        }
        boolean waiting = false;
        for (CalibrationPlan.Step step : steps) {
            waiting |= step.getStatus() == CalibrationPlan.Status.Waiting;
        }
        JButton run = Ui.button(Translations.getString(item.getStep() == CalibrationStep.Home
                ? "CalibrationPanel.Action.Home" : "CalibrationPanel.Action.Measure"), null, //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Md, Ui.Variant.Primary);
        Ui.movesMachine(run);
        boolean blocked = waiting;
        Ui.whyDisabled(run, () -> Translations.getString(blocked ? "CalibrationPanel.Why.Waiting" //$NON-NLS-1$
                : "CalibrationPanel.Why.Running")); //$NON-NLS-1$
        run.setEnabled(!isRunning() && !waiting);
        run.addActionListener(e -> measure(steps));
        buttons.add(run);
    }

    private void pendingPane(JPanel sections, List<JButton> buttons, CalibrationItem item,
            List<CalibrationItem.Part> parts) {
        List<Pending> measured = new ArrayList<>();
        for (CalibrationItem.Part p : parts) {
            if (p.getPending() != null) {
                measured.add(p.getPending());
            }
        }
        JPanel results = new JPanel();
        results.setOpaque(false);
        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
        // What several elements measured alike is said once, naming them.
        Map<String, List<String>> said = new LinkedHashMap<>();
        for (Pending p : measured) {
            for (String result : p.getResults()) {
                said.computeIfAbsent(result, k -> new ArrayList<>()).add(p.getSubjectName());
            }
        }
        for (Map.Entry<String, List<String>> result : said.entrySet()) {
            boolean everyOne = result.getValue().size() == measured.size();
            String names = String.join(Translations.getString("CalibrationPanel.ListSeparator"), result.getValue()); //$NON-NLS-1$
            JTextArea note = Forms.paragraph(measured.size() > 1 && !everyOne ? names + "  " + result.getKey() //$NON-NLS-1$
                    : result.getKey());
            note.setAlignmentX(Component.LEFT_ALIGNMENT);
            results.add(note);
            results.add(Box.createVerticalStrut(4));
        }
        if (results.getComponentCount() == 0) {
            results.add(Forms.paragraph(Translations.getString("CalibrationPanel.Measured.NoWords"))); //$NON-NLS-1$
        }
        sections.add(section("activity", "CalibrationPanel.Section.Measured", results)); //$NON-NLS-1$ //$NON-NLS-2$

        JPanel grid = grid();
        int row = 0;
        header(grid, row++, "CalibrationPanel.Change.Setting", "CalibrationPanel.Change.Before", //$NON-NLS-1$ //$NON-NLS-2$
                "CalibrationPanel.Change.After"); //$NON-NLS-1$
        int total = 0;
        for (Pending p : measured) {
            total += p.getDifferences().size();
        }
        int shown = 0;
        for (Pending p : measured) {
            for (SettingsDiff.Difference d : p.getDifferences()) {
                if (shown == LISTED) {
                    break;
                }
                String setting = measured.size() > 1 || !d.getSubject().equals(p.getSubjectName())
                        ? d.getSubject() + " \u00b7 " + d.getSetting() : d.getSetting(); //$NON-NLS-1$
                JLabel name = Ui.t2(setting);
                name.setToolTipText(setting);
                cell(grid, row, 0, 1, name);
                cell(grid, row, 1, 1, Ui.t2(d.getBefore()));
                cell(grid, row, 2, 1, Ui.muted("\u2192")); //$NON-NLS-1$
                JLabel is = new JLabel(d.getAfter());
                is.setFont(Ui.weighted(Ui.BASE, 600));
                cell(grid, row, 3, 1, is);
                row++;
                shown++;
            }
        }
        if (total > shown) {
            cell(grid, row, 0, 4, Ui.muted(String.format(Translations.getString("CalibrationPanel.Change.More"), //$NON-NLS-1$
                    total - shown)));
        }
        sections.add(section("edit", "CalibrationPanel.Section.Change", grid)); //$NON-NLS-1$ //$NON-NLS-2$

        boolean discardable = true;
        for (Pending p : measured) {
            discardable &= p.canBeDiscarded();
        }
        String ifDiscarded = Translations.getString("CalibrationPanel.Discard.What"); //$NON-NLS-1$
        if (!discardable) {
            ifDiscarded += "\n" + Translations.getString("CalibrationPanel.Discard.Cannot"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        sections.add(section("undo", "CalibrationPanel.Section.Discard", Forms.paragraph(ifDiscarded))); //$NON-NLS-1$ //$NON-NLS-2$

        JButton discard = Ui.button(Translations.getString("CalibrationPanel.Action.Discard"), null, //$NON-NLS-1$
                Ui.Size.Md, Ui.Variant.Default);
        discard.setToolTipText(Translations.getString("CalibrationPanel.Action.Discard.toolTipText")); //$NON-NLS-1$
        discard.setEnabled(!isRunning() && discardable);
        Ui.whyDisabled(discard, () -> Translations.getString(isRunning() ? "CalibrationPanel.Why.Running" //$NON-NLS-1$
                : "CalibrationPanel.Discard.Cannot")); //$NON-NLS-1$
        discard.addActionListener(e -> discard(measured));
        JButton keep = Ui.button(Translations.getString("CalibrationPanel.Action.Apply"), null, //$NON-NLS-1$
                Ui.Size.Md, Ui.Variant.Primary);
        keep.setToolTipText(Translations.getString("CalibrationPanel.Action.Keep.toolTipText")); //$NON-NLS-1$
        keep.setEnabled(!isRunning());
        keep.addActionListener(e -> keep(measured));
        buttons.add(discard);
        buttons.add(keep);
    }

    private void hintPane(JPanel sections, List<JButton> buttons, CalibrationItem item,
            List<CalibrationItem.Part> parts, List<Solutions.Issue> issues) {
        SetupChecks.Check check = item.getCheck();
        if (check != null) {
            sections.add(section("info", "CalibrationPanel.Section.Fix", Forms.paragraph(check.fix()))); //$NON-NLS-1$ //$NON-NLS-2$
            JButton go = Ui.button(String.format(Translations.getString("CalibrationPanel.Action.Settings"), //$NON-NLS-1$
                    Translations.getString("MachineSettings.Topic." + check.topic)), null, Ui.Size.Md, //$NON-NLS-1$
                    Ui.Variant.Primary);
            go.addActionListener(e -> frame.showMachineSettings(check.topic));
            buttons.add(go);
            return;
        }
        Solutions.Issue first = issues.isEmpty() ? null : issues.get(0);
        if (first == null) {
            return;
        }
        String more = Html.plain(first.getExtendedDescription());
        sections.add(section("info", "CalibrationPanel.Section.What", Forms.paragraph(first.getIssue() + "\n" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + first.getSolution() + (more.isEmpty() ? "" : "\n" + more)))); //$NON-NLS-1$ //$NON-NLS-2$
        if (issues.size() > 1) {
            List<String> names = new ArrayList<>();
            for (CalibrationItem.Part p : parts) {
                names.add(p.getSubject());
            }
            sections.add(section("layers", "CalibrationPanel.Section.About", //$NON-NLS-1$ //$NON-NLS-2$
                    Forms.paragraph(String.join(Translations.getString("CalibrationPanel.ListSeparator"), names)))); //$NON-NLS-1$
        }
        else if (machine != null) {
            IssuePanel controls = new IssuePanel(first, machine).controlsOnly();
            if (controls.hasControls()) {
                controls.setOpaque(false);
                sections.add(section("sliders", "CalibrationPanel.Section.Inputs", controls)); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
        JButton dismiss = Ui.button(Translations.getString("CalibrationPanel.Action.Dismiss"), null, //$NON-NLS-1$
                Ui.Size.Md, Ui.Variant.Default);
        dismiss.setToolTipText(Translations.getString("CalibrationPanel.Action.Dismiss.toolTipText")); //$NON-NLS-1$
        dismiss.setEnabled(!isRunning());
        dismiss.addActionListener(e -> dismiss(issues));
        buttons.add(dismiss);
        if (first.canBeAccepted()) {
            JButton accept = Ui.button(Translations.getString("CalibrationPanel.Action.Accept"), null, //$NON-NLS-1$
                    Ui.Size.Md, Ui.Variant.Primary);
            accept.setToolTipText(Translations.getString("CalibrationPanel.Action.Accept.toolTipText")); //$NON-NLS-1$
            accept.setEnabled(!isRunning());
            accept.addActionListener(e -> apply(issues));
            buttons.add(accept);
        }
    }

    /** "19:40 采集 · 吸嘴头跳动与背景 · 应用前备份 machine.xml". */
    private String source(CalibrationItem item) {
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Translations.getString("CalibrationPanel.Source.Collected"), //$NON-NLS-1$
                collected == null ? "\u2014" : CLOCK.format(collected))); //$NON-NLS-1$
        if (item.getStep() != null) {
            lines.add(String.format(Translations.getString("CalibrationPanel.Source.Step"), item.getStep().getName())); //$NON-NLS-1$
        }
        lines.add(backup == null ? Translations.getString("CalibrationPanel.Source.Backup") //$NON-NLS-1$
                : String.format(Translations.getString("CalibrationPanel.Source.BackedUp"), backup.getName())); //$NON-NLS-1$
        return String.join("\n", lines); //$NON-NLS-1$
    }

    private static Forms.Section section(String icon, String titleKey, JComponent content) {
        Forms.Section section = new Forms.Section(icon, Translations.getString(titleKey));
        section.content(content);
        return section;
    }

    private static JPanel grid() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        return grid;
    }

    private static void header(JPanel grid, int row, String first, String second, String third) {
        cell(grid, row, 0, 1, Ui.muted(Translations.getString(first)));
        cell(grid, row, 1, 1, Ui.muted(Translations.getString(second)));
        cell(grid, row, 3, 1, Ui.muted(Translations.getString(third)));
    }

    private static void cell(JPanel grid, int row, int column, int span, JComponent component) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = column;
        c.gridy = row;
        c.gridwidth = span;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(row == 0 ? 0 : 5, 0, 0, 10);
        c.weightx = column == 0 ? 1 : 0;
        c.fill = column == 0 ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        grid.add(component, c);
    }

    /** "问题 · 没设软限位" or "测量 · XY 定位 09-22". */
    private static String describe(CalibrationPlan.Basis basis) {
        if (basis.getIssue() != null) {
            return String.format(Translations.getString("CalibrationPanel.Basis.Issue"), basis.getIssue().getIssue()); //$NON-NLS-1$
        }
        if (basis.getGroup() != null) {
            return basis.getWhen() == null
                    ? String.format(Translations.getString("CalibrationPanel.Basis.Missing"), MeasurementsPanel.name(basis.getGroup())) //$NON-NLS-1$
                    : String.format(Translations.getString("CalibrationPanel.Basis.Measured"), //$NON-NLS-1$
                            MeasurementsPanel.name(basis.getGroup()), DAY.format(basis.getWhen()));
        }
        return "\u2014"; //$NON-NLS-1$
    }

    static String value(Object value) {
        if (value == null) {
            return "\u2014"; //$NON-NLS-1$
        }
        if (value instanceof CalibrationItem.Differs) {
            return value.toString();
        }
        if (value instanceof Length) {
            Length length = (Length) value;
            String number = String.format(Locale.ROOT, "%.4f", length.getValue()) //$NON-NLS-1$
                    .replaceAll("0+$", "").replaceAll("\\.$", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            return number + (length.getUnits() == null ? "" : " " + length.getUnits().getShortName()); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (value instanceof Boolean) {
            return Translations.getString((Boolean) value ? "Form.Change.On" : "Form.Change.Off"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (value instanceof Double || value instanceof Float) {
            return String.format(Locale.ROOT, "%.4f", ((Number) value).doubleValue()) //$NON-NLS-1$
                    .replaceAll("0+$", "").replaceAll("\\.$", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        }
        if (value instanceof Number) {
            return value.toString();
        }
        return DisplayNames.of(value);
    }

    // ---- the table ---------------------------------------------------------------------------

    private String status(Object row, Chip chip) {
        CalibrationItem item = itemOf(row);
        CalibrationItem.Part part = row instanceof PartRow ? ((PartRow) row).part : null;
        List<CalibrationPlan.Step> steps = part != null
                ? (part.getStep() == null ? List.of() : List.of(part.getStep())) : item.getSteps();
        for (CalibrationPlan.Step step : steps) {
            Live now = live.get(step.getKey());
            if (now != null) {
                chip.setTone(now == Live.Running ? Chip.Tone.Run : now == Live.Waiting ? Chip.Tone.Warn
                        : now == Live.Failed ? Chip.Tone.Err : now == Live.Done ? Chip.Tone.Ok : Chip.Tone.Skip);
                return Translations.getString("CalibrationPanel.Live." + now.name()); //$NON-NLS-1$
            }
        }
        switch (item.getKind()) {
            case Suggestion:
                chip.setTone(Chip.Tone.Run);
                return Translations.getString("CalibrationPanel.Status.Suggestion"); //$NON-NLS-1$
            case Measure: {
                boolean waiting = part != null ? part.getStep().getStatus() == CalibrationPlan.Status.Waiting
                        : item.isWaiting();
                if (waiting) {
                    List<CalibrationPlan.Step> first = part != null ? part.getStep().getUnsettledPrerequisites()
                            : item.getUnsettledPrerequisites();
                    chip.setTone(Chip.Tone.Pending);
                    return first.isEmpty() ? Translations.getString("CalibrationPanel.Status.Waiting") //$NON-NLS-1$
                            : String.format(Translations.getString("CalibrationPanel.Status.WaitingFor"), //$NON-NLS-1$
                                    first.get(0).getKind().getName());
                }
                chip.setTone(Chip.Tone.Warn);
                return Translations.getString(item.isNeedsPerson() ? "CalibrationPanel.Status.Person" //$NON-NLS-1$
                        : "CalibrationPanel.Status.Measure"); //$NON-NLS-1$
            }
            case Pending:
                chip.setTone(Chip.Tone.Warn);
                return Translations.getString("CalibrationPanel.Status.Pending"); //$NON-NLS-1$
            case Hint:
                chip.setTone(item.getCheck() == null && !item.getIssues().isEmpty()
                        && item.getIssues().get(0).canBeAccepted() ? Chip.Tone.Run : Chip.Tone.Neutral);
                return Translations.getString(item.getCheck() != null ? "CalibrationPanel.Status.Settings" //$NON-NLS-1$
                        : "CalibrationPanel.Status.Hint"); //$NON-NLS-1$
            case Done:
                chip.setTone(Chip.Tone.Ok);
                return Translations.getString("CalibrationPanel.Status.Done"); //$NON-NLS-1$
            default:
                chip.setTone(Chip.Tone.Skip);
                return Translations.getString("CalibrationPanel.Status.Dismissed"); //$NON-NLS-1$
        }
    }

    /** "5 → 1", "测完给出", or the first change a measurement made. */
    private static String change(Object row) {
        CalibrationItem item = itemOf(row);
        CalibrationItem.Part part = row instanceof PartRow ? ((PartRow) row).part : null;
        switch (item.getKind()) {
            case Suggestion: {
                if (part != null) {
                    SettingChange change = part.getChange();
                    return change == null ? "\u2014" //$NON-NLS-1$
                            : value(change.getCurrentValue()) + "  \u2192  " + value(change.getProposedValue()); //$NON-NLS-1$
                }
                return value(item.commonValue(false)) + "  \u2192  " + value(item.commonValue(true)); //$NON-NLS-1$
            }
            case Measure:
                return Translations.getString("CalibrationPanel.Value.AfterMeasuring"); //$NON-NLS-1$
            case Pending: {
                // "x 0.1 → 0.22   y 0.1 → 0.15": the first change of each element measured.
                List<Pending> measured = part != null ? List.of(part.getPending()) : item.getPending();
                List<String> firsts = new ArrayList<>();
                int more = 0;
                for (Pending p : measured) {
                    if (p.getDifferences().isEmpty()) {
                        continue;
                    }
                    if (firsts.size() == 2) {
                        more += p.getDifferences().size();
                        continue;
                    }
                    SettingsDiff.Difference first = p.getDifferences().get(0);
                    firsts.add((measured.size() > 1 ? p.getSubjectName() + " " : "") //$NON-NLS-1$ //$NON-NLS-2$
                            + first.getBefore() + " \u2192 " + first.getAfter()); //$NON-NLS-1$
                    more += p.getDifferences().size() - 1;
                }
                if (firsts.isEmpty()) {
                    return Translations.getString("CalibrationPanel.Value.Compensation"); //$NON-NLS-1$
                }
                return String.join("   ", firsts) //$NON-NLS-1$
                        + (more > 0 ? "   " + String.format(Translations.getString("CalibrationPanel.Value.AndMore"), //$NON-NLS-1$ //$NON-NLS-2$
                                more) : ""); //$NON-NLS-1$
            }
            default:
                return ""; //$NON-NLS-1$
        }
    }

    private final class RowsModel extends AbstractTableModel {
        final List<Object> rows = new ArrayList<>();
        final String[] columns = { "", //$NON-NLS-1$
                Translations.getString("CalibrationPanel.Column.Item"), //$NON-NLS-1$
                Translations.getString("CalibrationPanel.Column.Subject"), //$NON-NLS-1$
                Translations.getString("CalibrationPanel.Column.Status"), //$NON-NLS-1$
                Translations.getString("CalibrationPanel.Column.Change"), //$NON-NLS-1$
                "" }; //$NON-NLS-1$

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : Object.class;
        }

        private boolean checkable(int row) {
            Object r = rows.get(row);
            return r instanceof ItemRow && ((ItemRow) r).item.getKind() == CalibrationItem.Kind.Suggestion;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0 && checkable(row) && !isRunning();
        }

        @Override
        public Object getValueAt(int row, int column) {
            Object r = rows.get(row);
            if (column == 0) {
                return checkable(row) ? checked.contains(((ItemRow) r).item.getKey()) : null;
            }
            return r;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column == 0 && checkable(row)) {
                String key = ((ItemRow) rows.get(row)).item.getKey();
                if (Boolean.TRUE.equals(value)) {
                    checked.add(key);
                }
                else {
                    checked.remove(key);
                }
                describe();
            }
        }
    }

    /**
     * A line across several columns, which the table has no way to span: every cell it covers
     * paints the same line, shifted by where the cell starts, and together they read as one.
     */
    private static final class Span extends JComponent {
        private String text = ""; //$NON-NLS-1$
        private javax.swing.Icon icon;
        private java.awt.Color background;
        private int indent;
        private int offset;

        void set(String text, javax.swing.Icon icon, Font font, java.awt.Color foreground, java.awt.Color background,
                int indent, int offset) {
            this.text = text;
            this.icon = icon;
            this.background = background;
            this.indent = indent;
            this.offset = offset;
            setFont(font);
            setForeground(foreground);
            setToolTipText(text);
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            try {
                g2.setColor(background);
                g2.fillRect(0, 0, getWidth(), getHeight());
                Object hints = java.awt.Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints"); //$NON-NLS-1$
                if (hints instanceof Map) {
                    g2.addRenderingHints((Map<?, ?>) hints);
                }
                g2.translate(-offset, 0);
                int x = indent;
                if (icon != null) {
                    icon.paintIcon(this, g2, x, (getHeight() - icon.getIconHeight()) / 2);
                    x += icon.getIconWidth() + 6;
                }
                g2.setFont(getFont());
                g2.setColor(getForeground());
                java.awt.FontMetrics metrics = g2.getFontMetrics();
                g2.drawString(text, x, (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
            }
            finally {
                g2.dispose();
            }
        }
    }

    /** The group headings across the row, an item in bold, its parts indented, a status capsule. */
    private final class CellRenderer implements TableCellRenderer {
        private final DefaultTableCellRenderer text = new DefaultTableCellRenderer();
        private final JCheckBox box = new JCheckBox();
        private final JPanel boxCell = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 7));
        private final JPanel pillCell = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        private final Chip chip = new Chip("", Chip.Tone.Ok, Chip.Shape.Status); //$NON-NLS-1$
        private final JPanel actionCell = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        private final JButton second = Ui.button("", null, Ui.Size.Sm, Ui.Variant.Default); //$NON-NLS-1$
        private final JButton action = Ui.button("", null, Ui.Size.Sm, Ui.Variant.Default); //$NON-NLS-1$
        private final JLabel link = new JLabel();
        private final Span span = new Span();

        CellRenderer() {
            box.setOpaque(false);
            boxCell.add(box);
            pillCell.add(chip);
            second.setName("second"); //$NON-NLS-1$
            actionCell.add(second);
            actionCell.add(action);
            actionCell.add(link);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            Object r = model.rows.get(row);
            java.awt.Color background = isSelected ? table.getSelectionBackground()
                    : r instanceof GroupRow ? Ui.surface2() : table.getBackground();
            if (r instanceof GroupRow) {
                GroupRow group = (GroupRow) r;
                String hintKey = "CalibrationPanel.GroupHint." + group.kind.name(); //$NON-NLS-1$
                String heading = Translations.has(hintKey)
                        ? String.format(Translations.getString("CalibrationPanel.Group.HeadingHint"), //$NON-NLS-1$
                                group.kind.getName(), group.count, Translations.getString(hintKey))
                        : String.format(Translations.getString("CalibrationPanel.Group.Heading"), //$NON-NLS-1$
                                group.kind.getName(), group.count);
                span.set(heading, group.kind == CalibrationItem.Kind.Done
                        ? Ui.icon(group.folded ? "chevright" : "chevdown", 12, Ui.muted()) : null, //$NON-NLS-1$ //$NON-NLS-2$
                        Ui.weighted(12f, 600), isSelected ? table.getSelectionForeground() : Ui.text2(), background,
                        12, x(table, column));
                return span;
            }
            CalibrationItem item = itemOf(r);
            if (column == 0) {
                if (value instanceof Boolean) {
                    box.setSelected((Boolean) value);
                    boxCell.setBackground(background);
                    boxCell.setOpaque(true);
                    return boxCell;
                }
                JLabel label = plain(table, "", isSelected, background); //$NON-NLS-1$
                if (r instanceof ItemRow && item.getKind() == CalibrationItem.Kind.Hint) {
                    label.setIcon(Ui.icon("alert", 14, Ui.warn())); //$NON-NLS-1$
                    label.setHorizontalAlignment(SwingConstants.CENTER);
                    label.setBorder(null);
                }
                return label;
            }
            if (r instanceof MoreRow) {
                MoreRow more = (MoreRow) r;
                if (column >= 1 && column <= 3) {
                    // Across the item, subject and status columns.
                    List<String> names = new ArrayList<>();
                    for (int i = more.from; i < item.getParts().size(); i++) {
                        names.add(item.getParts().get(i).getSubject());
                    }
                    span.set(String.format(Translations.getString("CalibrationPanel.More"), names.size(), //$NON-NLS-1$
                            String.join(Translations.getString("CalibrationPanel.ListSeparator"), names)), null, //$NON-NLS-1$
                            table.getFont(), isSelected ? table.getSelectionForeground() : Ui.muted(), background, 30,
                            x(table, column) - x(table, 1));
                    return span;
                }
                String change = column == 4 ? change(new PartRow(item, more.from)) : ""; //$NON-NLS-1$
                JLabel label = plain(table, change, isSelected, background);
                label.setFont(Ui.mono(12f, Font.PLAIN));
                label.setForeground(isSelected ? table.getSelectionForeground() : Ui.muted());
                return label;
            }
            boolean isPart = r instanceof PartRow;
            switch (column) {
                case 1: {
                    JLabel label = plain(table, isPart ? "" : item.getTitle(), isSelected, background); //$NON-NLS-1$
                    label.setFont(isPart ? table.getFont() : table.getFont().deriveFont(Font.BOLD));
                    label.setToolTipText(isPart ? null : item.getTitle());
                    if (!isPart && item.isNeedsPerson() && item.getKind() == CalibrationItem.Kind.Measure) {
                        label.setIcon(Ui.icon("hand", 13, Ui.warn())); //$NON-NLS-1$
                        label.setHorizontalTextPosition(SwingConstants.LEFT);
                    }
                    return label;
                }
                case 2: {
                    String subjects = isPart ? ((PartRow) r).part.getSubject() : item.getSubjects();
                    JLabel label = plain(table, subjects, isSelected, background);
                    label.setForeground(isSelected ? table.getSelectionForeground() : Ui.text2());
                    label.setToolTipText(subjects);
                    if (isPart) {
                        label.setBorder(new EmptyBorder(0, 22, 0, 4));
                    }
                    else if (item.getParts().size() > 1) {
                        label.setIcon(Ui.icon(open.contains(item.getKey()) ? "chevdown" : "chevright", 12, //$NON-NLS-1$ //$NON-NLS-2$
                                Ui.muted()));
                    }
                    return label;
                }
                case 3: {
                    chip.setText(status(r, chip));
                    pillCell.setOpaque(true);
                    pillCell.setBackground(background);
                    return pillCell;
                }
                case 4: {
                    String change = change(r);
                    JLabel label = plain(table, change, isSelected, background);
                    label.setToolTipText(change.isEmpty() ? null : change);
                    if (item.getKind() == CalibrationItem.Kind.Measure) {
                        label.setForeground(isSelected ? table.getSelectionForeground() : Ui.muted());
                    }
                    else {
                        label.setFont(Ui.mono(12f, Font.PLAIN));
                    }
                    return label;
                }
                default: {
                    RowAction rowAction = actionOf(r);
                    actionCell.setOpaque(true);
                    actionCell.setBackground(background);
                    boolean asLink = rowAction != null && rowAction.link;
                    action.setVisible(rowAction != null && !asLink);
                    second.setVisible(rowAction != null && rowAction.second != null);
                    link.setVisible(asLink);
                    if (asLink) {
                        link.setText(rowAction.label);
                        link.setForeground(isSelected ? table.getSelectionForeground() : Ui.accent());
                        link.setFont(Ui.font(12f));
                    }
                    else if (rowAction != null) {
                        action.setText(rowAction.label);
                        action.setIcon(rowAction.moves ? Ui.icon("zap", 13, Ui.warn()) : null); //$NON-NLS-1$
                        action.setHorizontalTextPosition(SwingConstants.LEFT);
                        action.setEnabled(rowAction.enabled);
                        if (rowAction.second != null) {
                            second.setText(rowAction.secondLabel);
                            second.setEnabled(rowAction.enabled);
                        }
                    }
                    return actionCell;
                }
            }
        }

        /** Where the column starts, as the table lays its columns out now. */
        private int x(JTable table, int column) {
            int x = 0;
            for (int i = 0; i < column; i++) {
                x += table.getColumnModel().getColumn(i).getWidth();
            }
            return x;
        }

        private JLabel plain(JTable table, String value, boolean isSelected, java.awt.Color background) {
            text.getTableCellRendererComponent(table, value, isSelected, false, 0, 1);
            text.setHorizontalAlignment(SwingConstants.LEADING);
            text.setIcon(null);
            text.setBorder(new EmptyBorder(0, 8, 0, 4));
            text.setFont(table.getFont());
            text.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
            text.setBackground(background);
            text.setOpaque(true);
            text.setToolTipText(null);
            text.setHorizontalTextPosition(SwingConstants.RIGHT);
            return text;
        }
    }
}
