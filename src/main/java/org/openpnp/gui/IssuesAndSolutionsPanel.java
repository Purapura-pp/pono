/*
 * Copyright (C) 2021 <mark@makr.zone>
 * inspired and based on work
 * Copyright (C) 2011 Jason von Nieda <jason@vonnieda.org>
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
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;

import org.openpnp.ConfigurationListener;
import org.openpnp.Translations;
import org.openpnp.gui.components.AutoSelectTextTable;
import org.openpnp.gui.components.IssuePanel;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.gui.shell.DockPanel;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.PropertySheetPresenter;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.tablemodel.SolutionsTableModel;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.calibration.CalibrationPlan;
import org.openpnp.machine.reference.solutions.MachineDiagnostics;
import org.openpnp.model.CalibrationStep;
import org.openpnp.model.Configuration;
import org.openpnp.model.Solutions;
import org.openpnp.util.UiUtils;

/**
 * The issues page as mockups 13 to 15 have it: the issues in one table, with where each is dealt
 * with; the measurements that were the diagnostics page; and the machine's overview. Collect
 * all finds the issues and takes the measurements the calibration needs, and then says how many
 * steps the calibration page has to do.
 * <p>
 * An issue a calibration step deals with has no Accept here, only the way to its step: the
 * calibration page runs it with what it depends on first and saves after it.
 */
@SuppressWarnings("serial")
public class IssuesAndSolutionsPanel extends JPanel {
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm"); //$NON-NLS-1$

    final private Configuration configuration;
    final private MainFrame frame;
    private Solutions solutions;
    private SolutionsTableModel solutionsTableModel;
    private ReferenceMachine machine;
    private AutoSelectTextTable table;
    private TableRowSorter<SolutionsTableModel> tableSorter;

    private final DockPanel dock = new DockPanel();
    private final DockPanel.Tab issuesTab;
    private final DockPanel.Tab measureTab;
    private final DockPanel.Tab overviewTab;
    private final JPanel issuesPage = new JPanel(new BorderLayout());
    private final JPanel measureHolder = new JPanel(new BorderLayout());
    private final JPanel overviewHolder = new JPanel(new BorderLayout());
    private MeasurementsPanel measurements;
    private MachineOverviewPanel overview;
    private final JButton findButton;
    private final JComboBox<Solutions.Milestone> milestone = new JComboBox<>(Solutions.Milestone.values());
    private final JCheckBox showHandled = new JCheckBox(
            Translations.getString("IssuesAndSolutionsPanel.ShowHandled")); //$NON-NLS-1$
    private final JTextField filter;
    private final JLabel foot = Ui.muted(""); //$NON-NLS-1$
    private final JButton collect;
    private boolean finding;
    private Date lastFound;
    private boolean dirty = false;

    public IssuesAndSolutionsPanel(Configuration configuration, MainFrame frame) {
        this.configuration = configuration;
        this.frame = frame;
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(0, 10, 10, 10));

        DockPanel.Toolbar toolbar = new DockPanel.Toolbar();
        findButton = Ui.button(Translations.getString("IssuesAndSolutionsPanel.Action.FindSolution"), //$NON-NLS-1$
                Ui.iconSm("search"), Ui.Size.Sm, Ui.Variant.Default); //$NON-NLS-1$
        findButton.setToolTipText(Translations.getString("IssuesAndSolutionsPanel.Action.FindSolution.Description")); //$NON-NLS-1$
        findButton.setFocusable(false);
        findButton.addActionListener(e -> findIssuesAndSolutions());
        Ui.whyDisabled(findButton, () -> Translations.getString("IssuesAndSolutionsPanel.Why.Finding")); //$NON-NLS-1$
        toolbar.add(findButton);
        toolbar.add(Ui.t2(Translations.getString("IssuesAndSolutionsPanel.Target"))); //$NON-NLS-1$
        Forms.dropdown(milestone);
        milestone.setRenderer(Forms.described(v -> ((Solutions.Milestone) v).getName(), v -> null));
        milestone.setToolTipText(Translations.getString("IssuesAndSolutionsPanel.MilestoneLabel.toolTipText")); //$NON-NLS-1$
        milestone.setMaximumSize(new java.awt.Dimension(150, 28));
        milestone.addActionListener(e -> {
            Solutions.Milestone chosen = (Solutions.Milestone) milestone.getSelectedItem();
            if (solutions != null && chosen != null && chosen != solutions.getTargetMilestone()) {
                solutions.setTargetMilestone(chosen);
                findIssuesAndSolutions();
            }
        });
        toolbar.add(milestone);
        showHandled.setOpaque(false);
        showHandled.setFont(Ui.font(12f));
        showHandled.setToolTipText(Translations.getString("IssuesAndSolutionsPanel.IncludeSolvedLabel.toolTipText")); //$NON-NLS-1$
        showHandled.addActionListener(e -> {
            if (solutions != null) {
                solutions.setShowSolved(showHandled.isSelected());
                solutions.setShowDismissed(showHandled.isSelected());
                findIssuesAndSolutions();
            }
        });
        toolbar.add(showHandled);
        toolbar.glue();
        filter = toolbar.filter(Translations.getString("IssuesAndSolutionsPanel.Filter")); //$NON-NLS-1$
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
        issuesPage.setOpaque(false);
        issuesPage.add(toolbar, BorderLayout.NORTH);
        foot.setFont(Ui.font(11.5f));
        foot.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.border()),
                new EmptyBorder(6, 12, 6, 12)));
        issuesPage.add(foot, BorderLayout.SOUTH);
        measureHolder.setOpaque(false);
        overviewHolder.setOpaque(false);

        issuesTab = dock.addTab(Ui.iconSm("alert"), Translations.getString("IssuesAndSolutionsPanel.Tab.Issues"), //$NON-NLS-1$ //$NON-NLS-2$
                issuesPage);
        measureTab = dock.addTab(Ui.iconSm("activity"), //$NON-NLS-1$
                Translations.getString("IssuesAndSolutionsPanel.Tab.Measure"), measureHolder); //$NON-NLS-1$
        overviewTab = dock.addTab(Ui.iconSm("machine"), //$NON-NLS-1$
                Translations.getString("IssuesAndSolutionsPanel.Tab.Overview"), overviewHolder); //$NON-NLS-1$
        collect = Ui.button(Translations.getString("IssuesAndSolutionsPanel.CollectAll"), Ui.iconSm("refresh"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Xs, Ui.Variant.Primary);
        collect.setToolTipText(Translations.getString("IssuesAndSolutionsPanel.CollectAll.toolTipText")); //$NON-NLS-1$
        collect.setFocusable(false);
        Ui.movesMachine(collect);
        collect.addActionListener(e -> collectAll());
        dock.setTools(collect);
        dock.setMaximize(() -> frame.toggleDockMaximised());
        dock.addChangeListener(e -> inspectTab());
        add(dock, BorderLayout.CENTER);

        configuration.addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationComplete(Configuration configuration) throws Exception {
                machine = (ReferenceMachine) configuration.getMachine();
                solutions = machine.getSolutions();
                solutionsTableModel = new SolutionsTableModel(solutions);
                milestone.setSelectedItem(solutions.getTargetMilestone());
                showHandled.setSelected(solutions.isShowSolved() || solutions.isShowDismissed());
                // Solutions asks for a rescan rather than calling this page, so that changing the
                // milestone does not have to know a GUI exists.
                solutions.addPropertyChangeListener("rescanRequested", //$NON-NLS-1$
                        e -> SwingUtilities.invokeLater(() -> findIssuesAndSolutions()));
                buildTable();
                measurements = new MeasurementsPanel(machine, IssuesAndSolutionsPanel.this,
                        () -> findIssuesAndSolutions());
                measureHolder.add(measurements, BorderLayout.CENTER);
                overview = new MachineOverviewPanel(machine);
                overviewHolder.add(overview, BorderLayout.CENTER);
                describeFoot();
                // The first search after a delay, which keeps it clear of the configuration
                // loading and the cameras starting.
                new java.util.Timer().schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        SwingUtilities.invokeLater(() -> findIssuesAndSolutions());
                    }
                }, 5000);
            }
        });
    }

    private void buildTable() {
        tableSorter = new TableRowSorter<>(solutionsTableModel);
        table = new AutoSelectTextTable(solutionsTableModel) {
            @Override
            public String getToolTipText(MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                int col = columnAtPoint(e.getPoint());
                if (row >= 0 && col >= 0) {
                    String tip = solutionsTableModel.getToolTipAt(convertRowIndexToModel(row),
                            convertColumnIndexToModel(col));
                    if (tip != null) {
                        return tip;
                    }
                }
                return super.getToolTipText(e);
            }
        };
        table.setRowSorter(tableSorter);
        SolutionsTableModel.applyTableUi(table);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        AutoSelectTextTable.setEmptyText(table, Translations.getString("IssuesAndSolutionsPanel.Empty")); //$NON-NLS-1$
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectionActions();
            }
        });
        solutionsTableModel.addTableModelListener(e -> {
            solutionChanged();
            describeFoot();
        });
        org.openpnp.gui.support.TableUtils.installColumnWidthSavers(table,
                java.util.prefs.Preferences.userNodeForPackage(IssuesAndSolutionsPanel.class),
                "IssuesAndSolutionsPanel.issues"); //$NON-NLS-1$
        issuesPage.add(DockPanel.table(table), BorderLayout.CENTER);
        issuesPage.revalidate();
    }

    private void applyFilter() {
        if (tableSorter == null) {
            return;
        }
        String text = filter.getText().trim();
        tableSorter.setRowFilter(text.isEmpty() ? null
                : RowFilter.regexFilter("(?i)" + Pattern.quote(text))); //$NON-NLS-1$
    }

    /** "上次查找 16:40 · 9 个问题，5 个在校准页处理". */
    private void describeFoot() {
        if (solutions == null) {
            return;
        }
        int all = 0;
        int calibration = 0;
        for (Solutions.Issue issue : solutions.getIssues()) {
            if (issue.getSubject() instanceof Solutions.Milestone) {
                continue;
            }
            all++;
            if (issue.getCalibrationStep() != null) {
                calibration++;
            }
        }
        String counts = String.format(Translations.getString("IssuesAndSolutionsPanel.Foot.Counts"), all, calibration); //$NON-NLS-1$
        foot.setText(finding ? Translations.getString("IssuesAndSolutionsPanel.Foot.Finding") //$NON-NLS-1$
                : lastFound == null ? Translations.getString("IssuesAndSolutionsPanel.Foot.Never") //$NON-NLS-1$
                        : String.format(Translations.getString("IssuesAndSolutionsPanel.Foot.Last"), //$NON-NLS-1$
                                TIME.format(lastFound), counts));
    }

    private List<Solutions.Issue> getSelections() {
        List<Solutions.Issue> selections = new ArrayList<>();
        if (table == null) {
            return selections;
        }
        for (int selectedRow : table.getSelectedRows()) {
            selections.add(solutionsTableModel.getIssue(table.convertRowIndexToModel(selectedRow)));
        }
        return selections;
    }

    /** The properties column follows the tab: the issue selected, the group selected, or nothing. */
    private void inspectTab() {
        if (dock.getSelectedTab() == measureTab && measurements != null) {
            measurements.inspect();
        }
        else if (dock.getSelectedTab() == overviewTab) {
            if (overview != null) {
                overview.refresh();
            }
            frame.getInspector().show(this, null);
        }
        else {
            selectionActions();
        }
    }

    protected void selectionActions() {
        if (dock.getSelectedTab() != issuesTab) {
            return;
        }
        List<Solutions.Issue> issues = getSelections();
        Solutions.Issue issue = issues.size() == 1 ? issues.get(0) : null;
        if (issue == null) {
            frame.getInspector().show(this, null);
            updateIssueIndicator();
            return;
        }
        String subject = SolutionsTableModel.subjectText(issue.getSubject());
        // Built when it is shown, in the theme of the moment: built ahead it kept the colours of
        // the theme it was made in.
        frame.getInspector().show(this, issue, null, issue.getIssue(),
                String.format(Translations.getString("IssuesAndSolutionsPanel.Inspector.Subtitle"), subject, //$NON-NLS-1$
                        org.openpnp.gui.support.DisplayNames.of(issue.getSeverity())),
                Ui.icon("alert", 16, Ui.accent()), //$NON-NLS-1$
                () -> List.of(PropertySheetPresenter.sheet(
                        Translations.getString("MainFrame.RightComponent.tabs.IssuesAndSolutions"), //$NON-NLS-1$
                        issuePane(issue))));
        updateIssueIndicator();
    }

    /** What the issue says, what it was judged by, and how it is dealt with, over its buttons. */
    private JPanel issuePane(Solutions.Issue issue) {
        JPanel sections = new JPanel();
        sections.setOpaque(false);
        sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));
        Forms.Section what = new Forms.Section("info", Translations.getString("IssuesAndSolutionsPanel.Section.What")); //$NON-NLS-1$ //$NON-NLS-2$
        what.content(note(issue.getSolution()));
        sections.add(what);
        if (issue instanceof MachineDiagnostics.Finding && ((MachineDiagnostics.Finding) issue).getMeasuredBy() != null) {
            MachineDiagnostics.Finding finding = (MachineDiagnostics.Finding) issue;
            Forms.Section basis = new Forms.Section("activity", Translations.getString("IssuesAndSolutionsPanel.Section.Basis")); //$NON-NLS-1$ //$NON-NLS-2$
            basis.content(note(String.format(Translations.getString("IssuesAndSolutionsPanel.Basis.Measured"), //$NON-NLS-1$
                    MeasurementsPanel.name(finding.getMeasuredBy()))));
            sections.add(basis);
        }
        CalibrationStep step = issue.getCalibrationStep();
        Forms.Section how = new Forms.Section("target", Translations.getString("IssuesAndSolutionsPanel.Section.How")); //$NON-NLS-1$ //$NON-NLS-2$
        if (step != null) {
            how.content(note(String.format(Translations.getString("IssuesAndSolutionsPanel.How.Calibration"), //$NON-NLS-1$
                    step.getName(), step.getDescription())));
            sections.add(how);
        }
        else if (machine != null) {
            IssuePanel controls = new IssuePanel(issue, machine).controlsOnly();
            if (controls.hasControls()) {
                controls.setOpaque(false);
                how.content(controls);
                sections.add(how);
            }
        }
        sections.add(Box.createVerticalGlue());

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.setBorder(new EmptyBorder(10, 12, 12, 12));
        JButton dismiss = Ui.button(Translations.getString("IssuesAndSolutionsPanel.Action.DismissSolution"), null, //$NON-NLS-1$
                Ui.Size.Md, Ui.Variant.Default);
        dismiss.setEnabled(issue.getState() != Solutions.State.Dismissed);
        Ui.whyDisabled(dismiss, () -> Translations.getString("IssuesAndSolutionsPanel.Why.Dismissed")); //$NON-NLS-1$
        dismiss.addActionListener(e -> setState(Solutions.State.Dismissed));
        JButton copy = Ui.button(Translations.getString("IssuesAndSolutionsPanel.Copy"), Ui.iconSm("copy"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Md, Ui.Variant.Default);
        copy.setToolTipText(Translations.getString("IssuesAndSolutionsPanel.Copy.toolTipText")); //$NON-NLS-1$
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new StringSelection(SolutionsTableModel.subjectText(issue.getSubject()) + "\n" + issue.getIssue() //$NON-NLS-1$
                        + "\n" + issue.getSolution()), null)); //$NON-NLS-1$
        buttons.add(dismiss);
        buttons.add(Box.createHorizontalStrut(6));
        buttons.add(copy);
        if (issue.getState() != Solutions.State.Open) {
            JButton reopen = Ui.button(Translations.getString("IssuesAndSolutionsPanel.Action.ReopenSolution"), null, //$NON-NLS-1$
                    Ui.Size.Md, Ui.Variant.Default);
            reopen.addActionListener(e -> setState(Solutions.State.Open));
            buttons.add(Box.createHorizontalStrut(6));
            buttons.add(reopen);
        }
        buttons.add(Box.createHorizontalGlue());
        JButton primary;
        if (step != null) {
            primary = Ui.button(String.format(Translations.getString("IssuesAndSolutionsPanel.GoToCalibration"), //$NON-NLS-1$
                    step.getName()), null, Ui.Size.Md, Ui.Variant.Primary);
            primary.addActionListener(e -> frame.showCalibrationStep(step, issue.getCalibrationSubject()));
        }
        else {
            primary = Ui.button(Translations.getString("IssuesAndSolutionsPanel.Action.AcceptSolution"), null, //$NON-NLS-1$
                    Ui.Size.Md, Ui.Variant.Primary);
            primary.setEnabled(issue.getState() == Solutions.State.Open);
            Ui.whyDisabled(primary, () -> Translations.getString("IssuesAndSolutionsPanel.Why.NotOpen")); //$NON-NLS-1$
            primary.addActionListener(e -> setState(issue.canBeAccepted() ? Solutions.State.Solved
                    : Solutions.State.Dismissed));
        }
        buttons.add(primary);

        JPanel pane = new JPanel(new BorderLayout());
        pane.setOpaque(false);
        JScrollPane scroll = new JScrollPane(new org.openpnp.gui.form.LegacyWizardAdapter.WidthTracking(sections));
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        pane.add(scroll, BorderLayout.CENTER);
        pane.add(buttons, BorderLayout.SOUTH);
        return pane;
    }

    private static JComponent note(String text) {
        return Forms.paragraph(text);
    }

    private void setState(Solutions.State state) {
        UiUtils.messageBoxOnException(() -> {
            for (Solutions.Issue issue : getSelections()) {
                if (issue.getState() != state) {
                    issue.setStateCall(state);
                }
            }
        });
    }

    /**
     * The count on the rail: the issues dealt with on this page. The ones a calibration step deals
     * with are counted on the calibration page's item, as the steps they need.
     */
    public void updateIssueIndicator() {
        if (machine == null || !machine.getSolutions().isShowIndicator()) {
            return;
        }
        int unhandled = 0;
        boolean warning = false;
        for (Solutions.Issue issue : machine.getSolutions().getIssues()) {
            if (issue.getState() == Solutions.State.Open && issue.getCalibrationStep() == null
                    && issue.getSeverity().ordinal() > Solutions.Severity.Information.ordinal()) {
                unhandled++;
                warning |= issue.getSeverity().ordinal() >= Solutions.Severity.Warning.ordinal();
            }
        }
        if (frame.getNavigation() != null) {
            frame.getNavigation().setBadge(frame.getIssuesAndSolutionsTab(), unhandled,
                    warning ? org.openpnp.gui.shell.NavigationRail.Badge.Err
                            : org.openpnp.gui.shell.NavigationRail.Badge.Warn);
        }
        issuesTab.setCount(unhandled);
    }

    /**
     * Looks for issues away from the event thread, which the search froze for as long as it took,
     * and shows them when it is done.
     */
    public void findIssuesAndSolutions() {
        if (solutions == null || finding) {
            return;
        }
        finding = true;
        findButton.setEnabled(false);
        describeFoot();
        if (frame.getStatusBar() != null) {
            frame.getStatusBar().setBusy(true);
        }
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                solutions.findIssues();
                return null;
            }

            @Override
            protected void done() {
                finding = false;
                findButton.setEnabled(true);
                if (frame.getStatusBar() != null) {
                    frame.getStatusBar().setBusy(false);
                }
                try {
                    get();
                    solutions.publishIssues();
                    lastFound = new Date();
                    if (table != null && table.getRowCount() > 0 && table.getSelectedRow() < 0) {
                        table.setRowSelectionInterval(0, 0);
                    }
                }
                catch (Exception e) {
                    UiUtils.showError(e.getCause() != null ? e.getCause() : e);
                }
                describeFoot();
                updateIssueIndicator();
                if (frame.getCalibrationTab() != null) {
                    frame.getCalibrationTab().refresh();
                }
            }
        }.execute();
    }

    /**
     * Finds the issues and takes the measurements the calibration decides by, then says how many
     * steps the calibration page has to do and offers to go there.
     */
    private void collectAll() {
        if (measurements == null) {
            return;
        }
        findIssuesAndSolutions();
        dock.select(measureTab);
        measurements.setAfterRunOnce(() -> {
            findIssuesAndSolutions();
            SwingUtilities.invokeLater(() -> {
                int needed = CalibrationPlan.of(machine, CalibrationPlan.scan(machine, solutions))
                        .getOutstanding().size();
                if (needed > 0 && Dialogs.ask(SwingUtilities.getWindowAncestor(this), Dialogs.Tone.Info, "zap", //$NON-NLS-1$
                        String.format(Translations.getString("IssuesAndSolutionsPanel.Collected.Title"), needed), //$NON-NLS-1$
                        Translations.getString("IssuesAndSolutionsPanel.Collected.What"), null, //$NON-NLS-1$
                        new Dialogs.Choice(Translations.getString("IssuesAndSolutionsPanel.Collected.Go"), null, //$NON-NLS-1$
                                Ui.Variant.Primary)) == 0) {
                    frame.showCalibrationStep(null, null);
                }
            });
        });
        measurements.runCalibrationGroups();
    }

    protected void notifySolutionsChanged() {
        if (dirty) {
            dirty = false;
            // Reselect the Machine Setup tree path to reload the wizard with potentially different
            // settings and property sheets. Otherwise each and every modified setting would need
            // property change firing support, which is clearly not the case.
            MainFrame.get().getMachineSetupTab().selectCurrentTreePath();
            selectionActions();
        }
    }

    /**
     * Rebuild the UI as needed, when solutions have changed state, perhaps asynchronously.
     */
    public void solutionChanged() {
        dirty = true;
        SwingUtilities.invokeLater(() -> notifySolutionsChanged());
    }
}
