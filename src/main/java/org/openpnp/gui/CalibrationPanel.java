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
import java.awt.Font;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.openpnp.ConfigurationListener;
import org.openpnp.Translations;
import org.openpnp.gui.components.AutoSelectTextTable;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.DockPanel;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.PropertySheetPresenter;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.DisplayNames;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.calibration.CalibrationPlan;
import org.openpnp.machine.reference.calibration.CalibrationRunner;
import org.openpnp.machine.reference.calibration.SettingChange;
import org.openpnp.model.CalibrationStep;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.util.UiUtils;

/**
 * The calibration page, as mockup 16 has it: the catalogue of calibration steps in its groups,
 * each with where it stands, what it is judged by and what it would change, run one at a time or
 * all that are outstanding in one go. The issues page finds; this page carries out, in the order
 * the steps depend on each other, stops for what needs someone at the machine, and saves after
 * each step. The steps table is also what the calibration state is.
 */
@SuppressWarnings("serial")
public class CalibrationPanel extends JPanel {
    private static final SimpleDateFormat DAY = new SimpleDateFormat("MM-dd"); //$NON-NLS-1$
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss"); //$NON-NLS-1$

    /** What happened to a step in this session, which the plan does not know yet. */
    enum Live {
        Running, Waiting, Failed, Skipped, Done
    }

    /** A group's heading row: "视觉 · 9 步 · 需要 2". */
    static final class GroupRow {
        final CalibrationStep.Group group;
        int steps;
        int outstanding;

        GroupRow(CalibrationStep.Group group) {
            this.group = group;
        }

        @Override
        public String toString() {
            return outstanding == 0
                    ? String.format(Translations.getString("CalibrationPanel.Group.Done"), group.getName(), steps) //$NON-NLS-1$
                    : String.format(Translations.getString("CalibrationPanel.Group.Needed"), group.getName(), steps, //$NON-NLS-1$
                            outstanding);
        }
    }

    private final Configuration configuration;
    private final MainFrame frame;
    private ReferenceMachine machine;
    private CalibrationPlan plan;
    private CalibrationRunner runner;
    private CalibrationRunner.Session lastSession;
    private final Map<String, Live> live = new HashMap<>();
    private final Map<String, Date> finishedAt = new HashMap<>();
    private final StepsModel model = new StepsModel();
    private final AutoSelectTextTable table = new AutoSelectTextTable(model);
    private final DockPanel dock = new DockPanel();
    private final DockPanel.Tab stepsTab;
    private final Forms.Segmented scope;
    private final JButton oneClick;
    private final JButton runSelected;
    private final JButton skip;
    private final JButton stop;
    private final JButton openReport;
    private final JTextArea log = new JTextArea();
    private final JTextArea report = new JTextArea();

    public CalibrationPanel(Configuration configuration, MainFrame frame) {
        this.configuration = configuration;
        this.frame = frame;
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(0, 10, 10, 10));
        putClientProperty(MainFrame.DOCK_PAGE, Boolean.TRUE);

        DockPanel.Toolbar toolbar = new DockPanel.Toolbar();
        oneClick = Ui.button("", Ui.iconSm("play"), Ui.Size.Sm, Ui.Variant.Primary); //$NON-NLS-1$ //$NON-NLS-2$
        Ui.movesMachine(oneClick);
        oneClick.addActionListener(e -> runOutstanding());
        runSelected = Ui.button(Translations.getString("CalibrationPanel.RunSelected"), Ui.iconSm("step"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Default);
        Ui.movesMachine(runSelected);
        runSelected.addActionListener(e -> runSelectedSteps());
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
        openReport.addActionListener(e -> openReport());
        for (JButton b : new JButton[] { oneClick, runSelected, skip, stop, openReport }) {
            b.setFocusable(false);
        }
        Ui.whyDisabled(oneClick, () -> Translations.getString(isRunning() ? "CalibrationPanel.Why.Running" //$NON-NLS-1$
                : "CalibrationPanel.Why.NothingOutstanding")); //$NON-NLS-1$
        Ui.whyDisabled(runSelected, () -> Translations.getString(isRunning() ? "CalibrationPanel.Why.Running" //$NON-NLS-1$
                : "CalibrationPanel.Why.NoSelection")); //$NON-NLS-1$
        Ui.whyDisabled(skip, () -> Translations.getString("CalibrationPanel.Why.NotWaiting")); //$NON-NLS-1$
        Ui.whyDisabled(stop, () -> Translations.getString("CalibrationPanel.Why.NotRunning")); //$NON-NLS-1$
        Ui.whyDisabled(openReport, () -> Translations.getString("CalibrationPanel.Why.NoReport")); //$NON-NLS-1$
        scope = new Forms.Segmented(java.util.Arrays.asList(Boolean.FALSE, Boolean.TRUE),
                v -> Translations.getString(Boolean.TRUE.equals(v) ? "CalibrationPanel.Scope.Needed" //$NON-NLS-1$
                        : "CalibrationPanel.Scope.All")); //$NON-NLS-1$
        scope.setSelectedItem(Boolean.TRUE);
        scope.onChange(() -> rebuild());
        toolbar.add(oneClick);
        toolbar.add(runSelected);
        toolbar.add(skip);
        toolbar.add(stop);
        toolbar.separator();
        toolbar.add(openReport);
        toolbar.glue();
        toolbar.add(scope);

        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setDefaultRenderer(Object.class, new CellRenderer());
        // What a step rests on and what it changed run on to a second line rather than being cut.
        org.openpnp.gui.support.WrappingCells.install(table, 3, 4);
        AutoSelectTextTable.setEmptyText(table, Translations.getString("CalibrationPanel.Empty")); //$NON-NLS-1$
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                inspect();
                describeButtons();
            }
        });
        org.openpnp.gui.support.TableUtils.installColumnWidthSavers(table,
                java.util.prefs.Preferences.userNodeForPackage(CalibrationPanel.class), "CalibrationPanel.steps"); //$NON-NLS-1$

        log.setEditable(false);
        log.setFont(Ui.mono(11.5f, Font.PLAIN));
        log.setBorder(new EmptyBorder(6, 10, 6, 10));
        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.border()));
        logScroll.setPreferredSize(new Dimension(300, 96));

        JPanel steps = new JPanel(new BorderLayout());
        steps.setOpaque(false);
        steps.add(toolbar, BorderLayout.NORTH);
        steps.add(DockPanel.table(table), BorderLayout.CENTER);
        steps.add(logScroll, BorderLayout.SOUTH);
        report.setEditable(false);
        report.setFont(Ui.mono(12f, Font.PLAIN));
        report.setBorder(new EmptyBorder(10, 12, 10, 12));
        report.setText(Translations.getString("CalibrationPanel.Report.None")); //$NON-NLS-1$
        JScrollPane reportScroll = new JScrollPane(report);
        reportScroll.setBorder(null);
        stepsTab = dock.addTab(Ui.iconSm("target"), Translations.getString("CalibrationPanel.Tab.Steps"), steps); //$NON-NLS-1$ //$NON-NLS-2$
        dock.addTab(Ui.iconSm("file"), Translations.getString("CalibrationPanel.Tab.Report"), reportScroll); //$NON-NLS-1$ //$NON-NLS-2$
        dock.setMaximize(() -> frame.toggleDockMaximised());
        dock.addChangeListener(e -> inspect());
        add(dock, BorderLayout.CENTER);

        configuration.addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationComplete(Configuration configuration) throws Exception {
                if (configuration.getMachine() instanceof ReferenceMachine) {
                    machine = (ReferenceMachine) configuration.getMachine();
                    SwingUtilities.invokeLater(() -> refresh());
                }
            }
        });
        describeButtons();
    }

    // ---- the plan ----------------------------------------------------------------------------

    /** Searches for what the calibration needs and lays the steps out again. On the event thread. */
    public void refresh() {
        if (machine == null || isRunning()) {
            return;
        }
        UiUtils.messageBoxOnException(() -> {
            plan = CalibrationPlan.of(machine, CalibrationPlan.scan(machine, machine.getSolutions()));
            rebuild();
        });
    }

    private boolean isRunning() {
        return runner != null;
    }

    private void rebuild() {
        List<String> selectedKeys = selectedKeys();
        model.rows.clear();
        if (plan != null) {
            boolean onlyNeeded = Boolean.TRUE.equals(scope.getSelectedItem());
            GroupRow group = null;
            for (CalibrationPlan.Step step : plan.getSteps()) {
                CalibrationStep.Group g = step.getKind().getGroup();
                if (group == null || group.group != g) {
                    group = new GroupRow(g);
                    model.rows.add(group);
                }
                group.steps++;
                boolean outstanding = !step.getStatus().isSettled() || live.containsKey(step.getKey());
                if (!step.getStatus().isSettled()) {
                    group.outstanding++;
                }
                if (!onlyNeeded || outstanding) {
                    model.rows.add(step);
                }
            }
            // A group all of whose steps are left out by the filter goes with them.
            List<Object> kept = new ArrayList<>();
            for (int i = 0; i < model.rows.size(); i++) {
                Object row = model.rows.get(i);
                boolean empty = row instanceof GroupRow
                        && (i + 1 == model.rows.size() || model.rows.get(i + 1) instanceof GroupRow);
                if (!empty) {
                    kept.add(row);
                }
            }
            model.rows.clear();
            model.rows.addAll(kept);
        }
        model.fireTableDataChanged();
        for (int row = 0; row < model.rows.size(); row++) {
            Object r = model.rows.get(row);
            if (r instanceof CalibrationPlan.Step && selectedKeys.contains(((CalibrationPlan.Step) r).getKey())) {
                int view = table.convertRowIndexToView(row);
                table.getSelectionModel().addSelectionInterval(view, view);
            }
        }
        describeButtons();
        badge();
    }

    private List<String> selectedKeys() {
        List<String> keys = new ArrayList<>();
        for (CalibrationPlan.Step step : selectedSteps()) {
            keys.add(step.getKey());
        }
        return keys;
    }

    private List<CalibrationPlan.Step> selectedSteps() {
        List<CalibrationPlan.Step> steps = new ArrayList<>();
        for (int view : table.getSelectedRows()) {
            Object row = model.rows.get(table.convertRowIndexToModel(view));
            if (row instanceof CalibrationPlan.Step) {
                steps.add((CalibrationPlan.Step) row);
            }
        }
        return steps;
    }

    /** Selects the step, or the first outstanding one when none is given, and shows the page. */
    public void show(CalibrationStep kind, Object subject) {
        scope.setSelectedItem(Boolean.FALSE);
        refresh();
        dock.select(stepsTab);
        for (int row = 0; row < model.rows.size(); row++) {
            Object r = model.rows.get(row);
            if (!(r instanceof CalibrationPlan.Step)) {
                continue;
            }
            CalibrationPlan.Step step = (CalibrationPlan.Step) r;
            boolean match = kind == null ? !step.getStatus().isSettled()
                    : step.getKind() == kind && (subject == null || step.getSubject() == subject
                            || plan.getStep(kind, subject) == step);
            if (match) {
                int view = table.convertRowIndexToView(row);
                table.setRowSelectionInterval(view, view);
                table.scrollRectToVisible(table.getCellRect(view, 0, true));
                return;
            }
        }
    }

    private void describeButtons() {
        int outstanding = plan == null ? 0 : plan.getOutstanding().size();
        oneClick.setText(String.format(Translations.getString("CalibrationPanel.OneClick"), outstanding)); //$NON-NLS-1$
        oneClick.setEnabled(!isRunning() && outstanding > 0);
        runSelected.setEnabled(!isRunning() && !selectedSteps().isEmpty());
        skip.setEnabled(isRunning() && runner.isWaitingForPerson());
        stop.setEnabled(isRunning());
        openReport.setEnabled(lastSession != null && lastSession.getReportDirectory() != null);
        stepsTab.setCount(outstanding);
    }

    /** The rail's count: the steps needed, in amber when one of them waits for someone. */
    private void badge() {
        if (frame.getNavigation() == null || plan == null) {
            return;
        }
        int needed = 0;
        for (CalibrationPlan.Step step : plan.getOutstanding()) {
            if (step.getStatus() != CalibrationPlan.Status.Suggested) {
                needed++;
            }
        }
        frame.getNavigation().setBadge(this, needed, org.openpnp.gui.shell.NavigationRail.Badge.Warn);
    }

    // ---- running -----------------------------------------------------------------------------

    private void runOutstanding() {
        if (plan == null || isRunning()) {
            return;
        }
        List<CalibrationPlan.Step> steps = plan.getOutstanding();
        Set<String> skip = confirm(steps);
        if (skip == null) {
            return;
        }
        List<String> keys = new ArrayList<>();
        for (CalibrationPlan.Step step : steps) {
            keys.add(step.getKey());
        }
        start(keys, skip);
    }

    private void runSelectedSteps() {
        List<CalibrationPlan.Step> steps = selectedSteps();
        if (steps.isEmpty() || isRunning()) {
            return;
        }
        Set<String> skip = confirm(steps);
        if (skip == null) {
            return;
        }
        List<String> keys = new ArrayList<>();
        for (CalibrationPlan.Step step : steps) {
            keys.add(step.getKey());
        }
        start(keys, skip);
    }

    /**
     * Lists the steps about to run and lets the ones that need someone be left out this time.
     * 
     * @return The steps to leave out, or null for Cancel.
     */
    private Set<String> confirm(List<CalibrationPlan.Step> steps) {
        JPanel message = new JPanel();
        message.setLayout(new BoxLayout(message, BoxLayout.Y_AXIS));
        JLabel what = new JLabel(String.format(Translations.getString("CalibrationPanel.Confirm.What"), steps.size())); //$NON-NLS-1$
        message.add(what);
        message.add(Box.createVerticalStrut(8));
        Map<String, JCheckBox> leaveOut = new HashMap<>();
        for (CalibrationPlan.Step step : steps) {
            if (step.isNeedsPerson()) {
                JCheckBox box = new JCheckBox(String.format(Translations.getString("CalibrationPanel.Confirm.SkipThisTime"), //$NON-NLS-1$
                        step.getTitle()));
                leaveOut.put(step.getKey(), box);
                message.add(box);
            }
            else {
                message.add(new JLabel("\u00b7 " + step.getTitle())); //$NON-NLS-1$
            }
        }
        message.add(Box.createVerticalStrut(8));
        message.add(new JLabel(Translations.getString("CalibrationPanel.Confirm.Backup"))); //$NON-NLS-1$
        String start = Translations.getString("CalibrationPanel.Confirm.Start"); //$NON-NLS-1$
        String cancel = Translations.getString("Dialog.Cancel"); //$NON-NLS-1$
        int answer = JOptionPane.showOptionDialog(SwingUtilities.getWindowAncestor(this), message,
                Translations.getString("CalibrationPanel.Confirm.Title"), JOptionPane.OK_CANCEL_OPTION, //$NON-NLS-1$
                JOptionPane.WARNING_MESSAGE, null, new Object[] { start, cancel }, cancel);
        if (answer != 0) {
            return null;
        }
        Set<String> skip = new HashSet<>();
        for (Map.Entry<String, JCheckBox> e : leaveOut.entrySet()) {
            if (e.getValue().isSelected()) {
                skip.add(e.getKey());
            }
        }
        return skip;
    }

    private void start(List<String> keys, Set<String> skip) {
        live.clear();
        log.setText(""); //$NON-NLS-1$
        CalibrationRunner.OnMachine machinery = new CalibrationRunner.OnMachine(machine, configuration,
                () -> frame.getJobTab() == null ? null : frame.getJobTab().getJob());
        runner = new CalibrationRunner(machinery, new CalibrationRunner.Listener() {
            @Override
            public void stepStarted(CalibrationPlan.Step step) {
                SwingUtilities.invokeLater(() -> {
                    live.put(step.getKey(), Live.Running);
                    model.fireTableDataChanged();
                    frame.getStatusBar().setBusy(true);
                });
            }

            @Override
            public void stepFinished(CalibrationPlan.Step step, CalibrationRunner.Outcome outcome, String message) {
                SwingUtilities.invokeLater(() -> {
                    live.put(step.getKey(), outcome == CalibrationRunner.Outcome.Done ? Live.Done
                            : outcome == CalibrationRunner.Outcome.Skipped ? Live.Skipped : Live.Failed);
                    finishedAt.put(step.getKey(), new Date());
                    frame.hideInstructions();
                    model.fireTableDataChanged();
                    describeButtons();
                });
            }

            @Override
            public void waitingForPerson(CalibrationPlan.Step step) {
                SwingUtilities.invokeLater(() -> {
                    live.put(step.getKey(), Live.Waiting);
                    model.fireTableDataChanged();
                    describeButtons();
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
                SwingUtilities.invokeLater(() -> finish(session));
            }
        });
        describeButtons();
        runner.start(keys, skip).whenComplete((session, t) -> SwingUtilities.invokeLater(() -> {
            if (t != null) {
                runner = null;
                frame.getStatusBar().setBusy(false);
                frame.hideInstructions();
                UiUtils.showError(t);
                describeButtons();
                refresh();
            }
        }));
    }

    private void finish(CalibrationRunner.Session session) {
        lastSession = session;
        runner = null;
        frame.getStatusBar().setBusy(false);
        frame.hideInstructions();
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
        describeButtons();
        // What the steps changed is found again, on both pages.
        if (frame.getIssuesAndSolutionsTab() != null) {
            frame.getIssuesAndSolutionsTab().findIssuesAndSolutions();
        }
        refresh();
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
        List<CalibrationPlan.Step> steps = selectedSteps();
        CalibrationPlan.Step step = steps.size() == 1 ? steps.get(0) : null;
        if (step == null || frame.getInspector() == null) {
            if (frame.getInspector() != null) {
                frame.getInspector().show(this, null);
            }
            return;
        }
        String subtitle = String.format(Translations.getString(step.isMovesMachine()
                ? "CalibrationPanel.Subtitle.Moves" : "CalibrationPanel.Subtitle.Still"), step.getSubjectName()); //$NON-NLS-1$ //$NON-NLS-2$
        // Built when it is shown, in the theme of the moment.
        frame.getInspector().show(this, step, null, step.getKind().getName(), subtitle,
                Ui.icon("target", 16, Ui.accent()), //$NON-NLS-1$
                () -> List.of(PropertySheetPresenter.sheet(step.getKind().getName(), stepPane(step))));
    }

    /** What the step does, why it is needed, what it will change, and what comes before it. */
    private JPanel stepPane(CalibrationPlan.Step step) {
        JPanel sections = new JPanel();
        sections.setOpaque(false);
        sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));
        Forms.Section what = new Forms.Section("info", Translations.getString("CalibrationPanel.Section.What")); //$NON-NLS-1$ //$NON-NLS-2$
        what.content(note(step.getKind().getDescription()));
        sections.add(what);

        List<CalibrationPlan.Basis> basis = step.getBasis(plan.getResults());
        Forms.Section why = new Forms.Section("activity", Translations.getString("CalibrationPanel.Section.Why")); //$NON-NLS-1$ //$NON-NLS-2$
        JPanel whyRows = new JPanel();
        whyRows.setOpaque(false);
        whyRows.setLayout(new BoxLayout(whyRows, BoxLayout.Y_AXIS));
        whyRows.add(Ui.t2(String.format(Translations.getString("CalibrationPanel.Why.Status"), step.getStatus().getName()))); //$NON-NLS-1$
        for (CalibrationPlan.Basis b : basis) {
            whyRows.add(Box.createVerticalStrut(4));
            whyRows.add(note(describe(b)));
        }
        why.content(whyRows);
        sections.add(why);

        List<SettingChange> changes = step.getChanges();
        if (!changes.isEmpty()) {
            Forms.Section change = new Forms.Section("edit", Translations.getString("CalibrationPanel.Section.Change")); //$NON-NLS-1$ //$NON-NLS-2$
            JPanel rows = new JPanel(new java.awt.GridLayout(0, 3, 8, 4));
            rows.setOpaque(false);
            rows.add(Ui.muted(Translations.getString("CalibrationPanel.Change.Setting"))); //$NON-NLS-1$
            rows.add(Ui.muted(Translations.getString("CalibrationPanel.Change.Now"))); //$NON-NLS-1$
            rows.add(Ui.muted(Translations.getString("CalibrationPanel.Change.New"))); //$NON-NLS-1$
            for (SettingChange c : changes) {
                rows.add(Ui.t2(c.getSettingName()));
                rows.add(Ui.t2(value(c.getCurrentValue())));
                JLabel proposed = new JLabel(value(c.getProposedValue()));
                proposed.setFont(Ui.weighted(Ui.BASE, 600));
                rows.add(proposed);
            }
            change.content(rows);
            sections.add(change);
        }

        if (!step.getPrerequisites().isEmpty()) {
            Forms.Section before = new Forms.Section("check", Translations.getString("CalibrationPanel.Section.Before")); //$NON-NLS-1$ //$NON-NLS-2$
            JPanel chips = new JPanel(new org.openpnp.gui.support.WrapLayout(java.awt.FlowLayout.LEFT, 6, 4));
            chips.setOpaque(false);
            for (CalibrationPlan.Step p : step.getPrerequisites()) {
                boolean settled = p.getStatus().isSettled();
                chips.add(new Chip(settled ? p.getTitle()
                        : String.format(Translations.getString("CalibrationPanel.Before.First"), p.getTitle()), //$NON-NLS-1$
                        settled ? Chip.Tone.Ok : Chip.Tone.Warn, Chip.Shape.Status));
            }
            before.content(chips);
            sections.add(before);
        }
        sections.add(Box.createVerticalGlue());

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.setBorder(new EmptyBorder(10, 12, 12, 12));
        JButton run = Ui.button(Translations.getString("CalibrationPanel.RunThis"), null, Ui.Size.Md, Ui.Variant.Primary); //$NON-NLS-1$
        Ui.movesMachine(run);
        run.setEnabled(!isRunning());
        run.addActionListener(e -> runSelectedSteps());
        buttons.add(Box.createHorizontalGlue());
        buttons.add(run);

        JPanel pane = new JPanel(new BorderLayout());
        pane.setOpaque(false);
        JScrollPane scroll = new JScrollPane(new org.openpnp.gui.shell.WidthTracking(sections));
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        pane.add(scroll, BorderLayout.CENTER);
        pane.add(buttons, BorderLayout.SOUTH);
        return pane;
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

    private static String value(Object value) {
        if (value == null) {
            return "\u2014"; //$NON-NLS-1$
        }
        if (value instanceof Length) {
            return String.format(java.util.Locale.ROOT, "%.3f mm", //$NON-NLS-1$
                    ((Length) value).convertToUnits(LengthUnit.Millimeters).getValue());
        }
        return DisplayNames.of(value);
    }

    private static JComponent note(String text) {
        return Forms.paragraph(text);
    }

    // ---- the table ---------------------------------------------------------------------------

    private final class StepsModel extends AbstractTableModel {
        final List<Object> rows = new ArrayList<>();
        final String[] columns = { Translations.getString("CalibrationPanel.Column.Step"), //$NON-NLS-1$
                Translations.getString("CalibrationPanel.Column.Subject"), //$NON-NLS-1$
                Translations.getString("CalibrationPanel.Column.Status"), //$NON-NLS-1$
                Translations.getString("CalibrationPanel.Column.Basis"), //$NON-NLS-1$
                Translations.getString("CalibrationPanel.Column.Change"), //$NON-NLS-1$
                Translations.getString("CalibrationPanel.Column.Person"), //$NON-NLS-1$
                Translations.getString("CalibrationPanel.Column.Last") }; //$NON-NLS-1$

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
        public Object getValueAt(int row, int column) {
            Object r = rows.get(row);
            if (r instanceof GroupRow) {
                return column == 0 ? r : ""; //$NON-NLS-1$
            }
            CalibrationPlan.Step step = (CalibrationPlan.Step) r;
            switch (column) {
                case 0:
                    return step.getKind().getName();
                case 1:
                    return step.getSubjectName();
                case 2:
                    return step;
                case 3: {
                    List<CalibrationPlan.Basis> basis = plan == null ? List.of() : step.getBasis(plan.getResults());
                    return basis.isEmpty() ? "\u2014" : describe(basis.get(0)); //$NON-NLS-1$
                }
                case 4: {
                    List<SettingChange> changes = step.getChanges();
                    if (changes.isEmpty()) {
                        return "\u2014"; //$NON-NLS-1$
                    }
                    SettingChange c = changes.get(0);
                    return value(c.getCurrentValue()) + " \u2192 " + value(c.getProposedValue()) //$NON-NLS-1$
                            + (changes.size() > 1 ? " +" + (changes.size() - 1) : ""); //$NON-NLS-1$ //$NON-NLS-2$
                }
                case 5:
                    return step.isNeedsPerson() ? Translations.getString("CalibrationPanel.Person") : ""; //$NON-NLS-1$ //$NON-NLS-2$
                default: {
                    Date at = finishedAt.get(step.getKey());
                    return at == null ? "\u2014" : DAY.format(at); //$NON-NLS-1$
                }
            }
        }
    }

    /** A group's heading across the row, the step in bold, the status a capsule, a person's hand. */
    private final class CellRenderer extends DefaultTableCellRenderer {
        private final JPanel pillCell = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 6));
        private final Chip chip = new Chip("", Chip.Tone.Ok, Chip.Shape.Status); //$NON-NLS-1$

        CellRenderer() {
            pillCell.add(chip);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            Object r = model.rows.get(table.convertRowIndexToModel(row));
            if (r instanceof CalibrationPlan.Step && column == 2) {
                CalibrationPlan.Step step = (CalibrationPlan.Step) r;
                Live now = live.get(step.getKey());
                if (now != null) {
                    chip.setText(Translations.getString("CalibrationPanel.Live." + now.name())); //$NON-NLS-1$
                    chip.setTone(now == Live.Running ? Chip.Tone.Run : now == Live.Waiting ? Chip.Tone.Warn
                            : now == Live.Failed ? Chip.Tone.Err : now == Live.Done ? Chip.Tone.Ok : Chip.Tone.Skip);
                }
                else {
                    chip.setText(step.getStatus().getName());
                    switch (step.getStatus()) {
                        case Done:
                            chip.setTone(Chip.Tone.Ok);
                            break;
                        case Needed:
                            chip.setTone(Chip.Tone.Warn);
                            break;
                        case Suggested:
                            chip.setTone(Chip.Tone.Run);
                            break;
                        case Dismissed:
                            chip.setTone(Chip.Tone.Skip);
                            break;
                        default:
                            chip.setTone(Chip.Tone.Pending);
                            break;
                    }
                }
                pillCell.setOpaque(true);
                pillCell.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
                return pillCell;
            }
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setIcon(null);
            if (r instanceof GroupRow) {
                setFont(Ui.weighted(12f, 600));
                setForeground(Ui.text2());
                if (!isSelected) {
                    setBackground(Ui.surface2());
                }
                return this;
            }
            setFont(column == 0 ? table.getFont().deriveFont(Font.BOLD) : table.getFont());
            if (!isSelected) {
                setForeground(column == 0 ? table.getForeground() : column == 5 ? Ui.warnText() : Ui.text2());
                setBackground(table.getBackground());
            }
            if (column == 5 && value != null && !String.valueOf(value).isEmpty()) {
                setIcon(Ui.icon("hand", 13, Ui.warn())); //$NON-NLS-1$
            }
            return this;
        }
    }
}
