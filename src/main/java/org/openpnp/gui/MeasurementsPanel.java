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
import java.awt.GridLayout;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.openpnp.Translations;
import org.openpnp.gui.components.AutoSelectTextTable;
import org.openpnp.gui.components.SimpleGraphView;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.gui.shell.DockPanel;
import org.openpnp.gui.shell.DockRenderers;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.calibration.CalibrationPlan;
import org.openpnp.machine.reference.solutions.MachineDiagnostics;
import org.openpnp.machine.reference.solutions.MachineDiagnostics.TestGroup;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults;
import org.openpnp.model.CalibrationStep;
import org.openpnp.spi.PropertySheetHolder.PropertySheet;
import org.openpnp.util.UiUtils;

/**
 * The issues page's Measure tab, as mockup 14 has it: the measurement groups to run on the left
 * with what each does and when it last ran, the progress log and the four graphs on the right,
 * and the selected group's parameters in the properties column. It was the diagnostics page,
 * whose parameters were thirty fields in one grid, each measuring for a group it did not name.
 */
@SuppressWarnings("serial")
public class MeasurementsPanel extends JPanel {
    /** The groups that only talk to the controller or read the configuration. */
    static final Set<TestGroup> STILL = EnumSet.of(TestGroup.Firmware, TestGroup.ConfigSnapshot);
    /** The groups that run the axes fast, over the whole travel, for minutes. */
    static final Set<TestGroup> LONG_TRAVEL = EnumSet.of(TestGroup.LostSteps, TestGroup.Kinematics,
            TestGroup.HysteresisMap, TestGroup.Homing, TestGroup.XyPositioning, TestGroup.DatumBoard);

    /** Which groups are ticked: what the calibration decides by, all of them, or by hand. */
    enum Preset {
        Calibration, All, Custom
    }

    /** Each group's parameters: property, label key, and i(nteger) d(ecimal) t(ext) b(oolean) a(rea). */
    private static final Map<TestGroup, String[][]> PARAMETERS = new EnumMap<>(TestGroup.class);
    private static final String[][] COMMON = { { "measureSpeedFactor", "MeasureSpeedFactor", "d" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            { "machineSettleMs", "MachineSettle", "i" } }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

    static {
        PARAMETERS.put(TestGroup.Firmware, new String[][] { { "firmwareCommands", "FirmwareCommands", "a" } }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        PARAMETERS.put(TestGroup.VisionNoise, new String[][] { { "noiseFrames", "NoiseFrames", "i" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                { "driftSeconds", "DriftSeconds", "i" } }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        PARAMETERS.put(TestGroup.CameraLatency, new String[][] { { "latencySpeedFactor", "LatencySpeedFactor", "d" } }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        PARAMETERS.put(TestGroup.Kinematics, new String[][] { { "timingDistances", "TimingDistances", "t" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                { "speedFactors", "SpeedFactors", "t" }, { "rotationTimingAngles", "RotationTimingAngles", "t" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                { "timingIncludesZAndRotation", "TimingIncludesZAndRotation", "b" } }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        PARAMETERS.put(TestGroup.LostSteps, new String[][] { { "stressSpeedFactors", "StressSpeedFactors", "t" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                { "stressCycles", "StressCycles", "i" }, { "stressDistanceMm", "StressDistance", "d" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                { "homeBeforeEachStressSpeed", "HomeBeforeEachStressSpeed", "b" } }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        PARAMETERS.put(TestGroup.XyPositioning, new String[][] { { "positioningDistances", "PositioningDistances", "t" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                { "repeats", "Repeats", "i" }, { "backlashRepeats", "BacklashRepeats", "i" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                { "framesPerPoint", "FramesPerPoint", "i" }, { "stepTestDistanceMm", "StepTestDistance", "d" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                { "stepTestStepMm", "StepTestStep", "d" }, { "fieldOfViewGridSteps", "FieldOfViewGridSteps", "i" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                { "fieldOfViewGridFraction", "FieldOfViewGridFraction", "d" } }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        PARAMETERS.put(TestGroup.CameraSettle, new String[][] { { "settleDistances", "SettleDistances", "t" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                { "settleRepeats", "SettleRepeats", "i" }, { "settleSampleSeconds", "SettleSampleSeconds", "d" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                { "settleThresholdPixels", "SettleThresholdPixels", "d" } }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        PARAMETERS.put(TestGroup.Homing, new String[][] { { "homingCycles", "HomingCycles", "i" } }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        PARAMETERS.put(TestGroup.RotationBacklash, new String[][] { { "rotationTestAngles", "RotationTestAngles", "t" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                { "rotationApproachAngle", "RotationApproachAngle", "d" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                { "rotationTestPartId", "RotationTestPartId", "t" } }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        PARAMETERS.put(TestGroup.ZFocus, new String[][] { { "focusRangeMm", "FocusRange", "d" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                { "focusStepMm", "FocusStep", "d" }, { "focusRepeats", "FocusRepeats", "i" } }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
        PARAMETERS.put(TestGroup.DatumBoard, new String[][] { { "rulerStepMm", "RulerStep", "d" } }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        PARAMETERS.put(TestGroup.HysteresisMap, new String[][] { { "hysteresisTargets", "HysteresisTargets", "t" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                { "hysteresisLatticePitchMm", "HysteresisLatticePitch", "d" } }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private static final SimpleDateFormat WHEN = new SimpleDateFormat("MM-dd HH:mm"); //$NON-NLS-1$
    private static final SimpleDateFormat DAY = new SimpleDateFormat("MM-dd"); //$NON-NLS-1$

    private final ReferenceMachine machine;
    private final MachineDiagnostics diagnostics;
    private final Component page;
    private final Runnable afterRun;
    private final GroupModel model = new GroupModel();
    private final AutoSelectTextTable table = new AutoSelectTextTable(model);
    private final Forms.Segmented preset;
    private final JButton start;
    private final JButton stop;
    private final JButton openReport;
    private final Chip runningChip = new Chip("", Chip.Tone.Run, Chip.Shape.Status); //$NON-NLS-1$
    private final JTextArea log = new JTextArea();
    private final SimpleGraphView[] graphs = new SimpleGraphView[4];
    private final JPanel toolbarHolder = new JPanel(new BorderLayout());
    private String shownLog = ""; //$NON-NLS-1$
    private TestGroup inspected;

    private final PropertyChangeListener listener = e -> SwingUtilities.invokeLater(() -> follow(e.getPropertyName()));

    /**
     * @param page     The page this is a tab of, for the properties column.
     * @param afterRun What to do once a run has finished: look for issues again.
     */
    public MeasurementsPanel(ReferenceMachine machine, Component page, Runnable afterRun) {
        this.machine = machine;
        this.diagnostics = machine.getMachineDiagnostics();
        this.page = page;
        this.afterRun = afterRun;
        setLayout(new BorderLayout());
        setOpaque(false);

        preset = new Forms.Segmented(java.util.Arrays.asList(Preset.values()),
                v -> Translations.getString("MeasurementsPanel.Preset." + ((Preset) v).name())); //$NON-NLS-1$
        preset.onChange(() -> applyPreset((Preset) preset.getSelectedItem()));
        start = Ui.button(Translations.getString("MeasurementsPanel.Start"), Ui.iconSm("play"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Primary);
        Ui.movesMachine(start);
        start.addActionListener(e -> run(model.selected()));
        stop = Ui.button(Translations.getString("MeasurementsPanel.Stop"), Ui.iconSm("stop"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Danger);
        stop.addActionListener(e -> diagnostics.abort());
        openReport = Ui.button(Translations.getString("MeasurementsPanel.OpenReport"), Ui.iconSm("external"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Ghost);
        openReport.addActionListener(e -> openReport());
        for (JButton b : new JButton[] { start, stop, openReport }) {
            b.setFocusable(false);
        }
        Ui.whyDisabled(start, () -> Translations.getString(diagnostics.isRunning() ? "MeasurementsPanel.Why.Running" //$NON-NLS-1$
                : "MeasurementsPanel.Why.NoGroup")); //$NON-NLS-1$
        Ui.whyDisabled(stop, () -> Translations.getString("MeasurementsPanel.Why.NotRunning")); //$NON-NLS-1$
        Ui.whyDisabled(openReport, () -> Translations.getString("MeasurementsPanel.Why.NoReport")); //$NON-NLS-1$
        DockPanel.Toolbar toolbar = new DockPanel.Toolbar();
        toolbar.add(preset);
        toolbar.separator();
        toolbar.add(start);
        toolbar.add(stop);
        toolbar.add(openReport);
        toolbar.glue();
        toolbar.add(runningChip);
        toolbarHolder.setOpaque(false);
        toolbarHolder.add(toolbar, BorderLayout.CENTER);
        add(toolbarHolder, BorderLayout.NORTH);

        table.setTableHeader(null);
        table.setRowHeight(48);
        table.setShowGrid(false);
        table.setShowHorizontalLines(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setCellRenderer(DockRenderers.check());
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(1).setCellRenderer(new GroupRenderer());
        table.getColumnModel().getColumn(2).setCellRenderer(new LastRenderer());
        table.getColumnModel().getColumn(2).setMaxWidth(96);
        table.getColumnModel().getColumn(2).setPreferredWidth(96);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                inspect();
            }
        });
        JScrollPane groups = new JScrollPane(table);
        groups.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Ui.border()));
        groups.setPreferredSize(new Dimension(330, 200));
        groups.getViewport().setBackground(Ui.surface());

        log.setEditable(false);
        log.setFont(Ui.mono(11.5f, Font.PLAIN));
        log.setBorder(new EmptyBorder(6, 10, 6, 10));
        JScrollPane logScroll = new JScrollPane(log);
        logScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.border()));
        logScroll.setPreferredSize(new Dimension(300, 150));
        JPanel charts = new JPanel(new GridLayout(2, 2, 10, 10));
        charts.setOpaque(false);
        charts.setBorder(new EmptyBorder(10, 10, 10, 10));
        String[] keys = { "Timing", "Positioning", "Step", "Settle" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        for (int i = 0; i < 4; i++) {
            graphs[i] = new SimpleGraphView();
            graphs[i].setFont(Ui.font(11f));
            JPanel chart = new JPanel(new BorderLayout(0, 4));
            chart.setOpaque(false);
            JLabel title = Ui.t2(Translations.getString("MachineDiagnosticsWizard.GraphsPanel." + keys[i] + ".text")); //$NON-NLS-1$ //$NON-NLS-2$
            title.setToolTipText(Translations.getString(
                    "MachineDiagnosticsWizard.GraphsPanel." + keys[i] + ".toolTipText")); //$NON-NLS-1$ //$NON-NLS-2$
            chart.add(title, BorderLayout.NORTH);
            chart.add(graphs[i], BorderLayout.CENTER);
            charts.add(chart);
        }
        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);
        right.add(logScroll, BorderLayout.NORTH);
        right.add(charts, BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(groups, BorderLayout.WEST);
        body.add(right, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        preset.setSelectedItem(Preset.Calibration);
        applyPreset(Preset.Calibration);
        follow(null);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        diagnostics.addPropertyChangeListener(listener);
        follow(null);
    }

    @Override
    public void removeNotify() {
        diagnostics.removePropertyChangeListener(listener);
        super.removeNotify();
    }

    /** The groups the calibration page decides by: what "what the calibration needs" ticks. */
    static Set<TestGroup> calibrationGroups() {
        Set<TestGroup> groups = EnumSet.noneOf(TestGroup.class);
        for (CalibrationStep step : CalibrationStep.values()) {
            groups.addAll(CalibrationPlan.decidedBy(step));
        }
        return groups;
    }

    private void applyPreset(Preset chosen) {
        if (chosen == null || chosen == Preset.Custom) {
            return;
        }
        Set<TestGroup> wanted = chosen == Preset.All ? EnumSet.allOf(TestGroup.class) : calibrationGroups();
        for (TestGroup group : TestGroup.values()) {
            model.selected.put(group, wanted.contains(group));
        }
        model.fireTableDataChanged();
        describeStart();
    }

    /** Ticks the groups given and runs them: what "collect everything" does. */
    public void runCalibrationGroups() {
        preset.setSelectedItem(Preset.Calibration);
        applyPreset(Preset.Calibration);
        run(model.selected());
    }

    private void describeStart() {
        int n = model.selected().size();
        start.setText(String.format(Translations.getString("MeasurementsPanel.Start.Count"), n)); //$NON-NLS-1$
        start.setEnabled(n > 0 && !diagnostics.isRunning());
    }

    private void follow(String property) {
        if (property == null || "log".equals(property)) { //$NON-NLS-1$
            showLog();
        }
        if (property == null || "timingGraph".equals(property)) { //$NON-NLS-1$
            graphs[0].setGraph(diagnostics.getTimingGraph());
        }
        if (property == null || "positioningGraph".equals(property)) { //$NON-NLS-1$
            graphs[1].setGraph(diagnostics.getPositioningGraph());
        }
        if (property == null || "stepGraph".equals(property)) { //$NON-NLS-1$
            graphs[2].setGraph(diagnostics.getStepGraph());
        }
        if (property == null || "settleGraph".equals(property)) { //$NON-NLS-1$
            graphs[3].setGraph(diagnostics.getSettleGraph());
        }
        if (property == null || "running".equals(property) || "lastReportDirectory".equals(property) //$NON-NLS-1$ //$NON-NLS-2$
                || "lastResults".equals(property)) { //$NON-NLS-1$
            boolean running = diagnostics.isRunning();
            stop.setEnabled(running);
            openReport.setEnabled(diagnostics.getLastReportDirectory() != null);
            runningChip.setText(Translations.getString("MeasurementsPanel.Running")); //$NON-NLS-1$
            runningChip.setVisible(running);
            describeStart();
            model.fireTableRowsUpdated(0, model.getRowCount() - 1);
        }
    }

    /** The log in the display language, a line at a time, only what is new translated. */
    private void showLog() {
        String raw = diagnostics.getLog();
        if (raw.startsWith(shownLog)) {
            String added = raw.substring(shownLog.length());
            if (!added.isEmpty()) {
                StringBuilder text = new StringBuilder();
                for (String line : added.split("\n", -1)) { //$NON-NLS-1$
                    text.append(line.isEmpty() ? line : Translations.translateText(line)).append('\n');
                }
                text.setLength(text.length() - 1);
                log.append(text.toString());
            }
        }
        else {
            log.setText(""); //$NON-NLS-1$
            for (String line : raw.split("\n")) { //$NON-NLS-1$
                log.append((line.isEmpty() ? line : Translations.translateText(line)) + "\n"); //$NON-NLS-1$
            }
        }
        shownLog = raw;
        log.setCaretPosition(log.getDocument().getLength());
    }

    private Runnable afterRunOnce;

    /** Also called once after the next run, and not after it again: what "collect all" waits for. */
    public void setAfterRunOnce(Runnable afterRunOnce) {
        this.afterRunOnce = afterRunOnce;
    }

    private void run(Set<TestGroup> selected) {
        Runnable once = afterRunOnce;
        afterRunOnce = null;
        if (selected.isEmpty() || diagnostics.isRunning() || !confirmMotion(selected)) {
            return;
        }
        UiUtils.submitUiMachineTask(() -> diagnostics.run(machine, selected), report -> {
            afterRun.run();
            if (once != null) {
                once.run();
            }
        }, t -> UiUtils.showError(t));
    }

    /** Says what is about to move before it moves, and where the stop is. */
    private boolean confirmMotion(Set<TestGroup> selected) {
        List<String> moving = new ArrayList<>();
        List<String> travelling = new ArrayList<>();
        for (TestGroup group : selected) {
            if (STILL.contains(group)) {
                continue;
            }
            moving.add(name(group));
            if (LONG_TRAVEL.contains(group)) {
                travelling.add(name(group));
            }
        }
        if (moving.isEmpty()) {
            return true;
        }
        String separator = Translations.getString("MachineDiagnosticsWizard.ConfirmMotion.Separator"); //$NON-NLS-1$
        String what = String.format(Translations.getString("MeasurementsPanel.Confirm.What"), //$NON-NLS-1$
                String.join(separator, moving));
        String more = (travelling.isEmpty() ? "" //$NON-NLS-1$
                : String.format(Translations.getString("MeasurementsPanel.Confirm.Travel"), //$NON-NLS-1$
                        String.join(separator, travelling)) + "\n") //$NON-NLS-1$
                + String.format(Translations.getString("MeasurementsPanel.Confirm.Stop"), //$NON-NLS-1$
                        org.openpnp.gui.shell.Hotkeys.describe(org.openpnp.gui.shell.Hotkeys.STOP_MACHINE));
        return Dialogs.ask(SwingUtilities.getWindowAncestor(this), Dialogs.Tone.Warn, "zap", //$NON-NLS-1$
                Translations.getString("MeasurementsPanel.Confirm.Title"), what, more, //$NON-NLS-1$
                new Dialogs.Choice(Translations.getString("MeasurementsPanel.Confirm.Start"), null, //$NON-NLS-1$
                        Ui.Variant.Primary).movesMachine()) == 0;
    }

    private void openReport() {
        UiUtils.messageBoxOnException(() -> {
            File directory = diagnostics.getLastReportDirectory();
            if (directory == null || !directory.isDirectory()) {
                throw new Exception(Translations.getString("MachineDiagnosticsWizard.Error.NoReport")); //$NON-NLS-1$
            }
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                throw new Exception(directory.getAbsolutePath());
            }
            Desktop.getDesktop().open(directory);
        });
    }

    static String name(TestGroup group) {
        return Translations.getString("MachineDiagnosticsWizard.Test." + group.name()); //$NON-NLS-1$
    }

    private static String what(TestGroup group) {
        String key = "MachineDiagnosticsWizard.Test." + group.name() + ".toolTipText"; //$NON-NLS-1$ //$NON-NLS-2$
        return Translations.has(key) ? Translations.getString(key).replaceAll("<[^>]+>", " ").trim() : ""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private MachineDiagnosticsResults.Run lastRun(TestGroup group) {
        MachineDiagnosticsResults results = diagnostics.getLastResults();
        return results == null ? null : results.getRun(group);
    }

    // ---- the properties column ---------------------------------------------------------------

    /** Shows the selected group in the properties column: what it does, its parameters, its last run. */
    public void inspect() {
        int row = table.getSelectedRow();
        TestGroup group = row < 0 ? null : TestGroup.values()[table.convertRowIndexToModel(row)];
        inspected = group;
        MainFrame frame = MainFrame.get();
        if (frame == null || frame.getInspector() == null) {
            return;
        }
        if (group == null) {
            frame.getInspector().show(page, null);
            return;
        }
        String subtitle = String.format(Translations.getString(STILL.contains(group)
                ? "MeasurementsPanel.Subtitle.Still" : "MeasurementsPanel.Subtitle.Moves"), name(group)); //$NON-NLS-1$ //$NON-NLS-2$
        frame.getInspector().show(page, group, null, name(group), subtitle,
                Ui.icon("activity", 16, Ui.accent()), //$NON-NLS-1$
                () -> {
                    List<PropertySheet> sheets = new ArrayList<>();
                    sheets.add(new PropertySheetWizardAdapter(form(group), name(group)));
                    return sheets;
                });
    }

    /** Whether the properties column shows one of these groups: the page asks when its tab changes. */
    public boolean isInspecting() {
        return inspected != null;
    }

    FormWizard form(TestGroup group) {
        Form.Builder form = Form.of(diagnostics).named(name(group))
                .section("MeasurementsPanel.What", "info") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", paragraph(what(group))); //$NON-NLS-1$
        String[][] parameters = PARAMETERS.get(group);
        if (parameters != null) {
            form.section("MeasurementsPanel.Parameters", "sliders"); //$NON-NLS-1$ //$NON-NLS-2$
            add(form, parameters);
        }
        if (!STILL.contains(group)) {
            form.section("MeasurementsPanel.Common", "gear").collapsed(); //$NON-NLS-1$ //$NON-NLS-2$
            add(form, COMMON);
        }
        MachineDiagnosticsResults.Run run = lastRun(group);
        MachineDiagnosticsResults results = diagnostics.getLastResults();
        form.section("MeasurementsPanel.Last", "clock") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("MeasurementsPanel.Last.When", Ui.t2(run == null //$NON-NLS-1$
                        ? Translations.getString("MeasurementsPanel.Never") //$NON-NLS-1$
                        : WHEN.format(run.getWhen()) + (run.getDurationMillis() > 0
                                ? " \u00b7 " + duration(run.getDurationMillis()) : ""))); //$NON-NLS-1$ //$NON-NLS-2$
        if (run != null && results != null && !results.isCurrent(group)) {
            form.custom("MeasurementsPanel.Last.State", //$NON-NLS-1$
                    new Chip(Translations.getString("MeasurementsPanel.Stale"), Chip.Tone.Warn, Chip.Shape.Status)); //$NON-NLS-1$
        }
        return form.build();
    }

    private static void add(Form.Builder form, String[][] parameters) {
        for (String[] p : parameters) {
            String label = "MachineDiagnosticsWizard.ParametersPanel." + p[1] + ".text"; //$NON-NLS-1$ //$NON-NLS-2$
            switch (p[2]) {
                case "i": //$NON-NLS-1$
                    form.integer(p[0], label).width(100);
                    break;
                case "d": //$NON-NLS-1$
                    form.decimal(p[0], label).width(100);
                    break;
                case "b": //$NON-NLS-1$
                    form.toggle(p[0], label, ""); //$NON-NLS-1$
                    break;
                case "a": //$NON-NLS-1$
                    form.textArea(p[0], label, 4);
                    break;
                default:
                    form.text(p[0], label);
                    break;
            }
        }
    }

    private static JComponent paragraph(String text) {
        return Forms.paragraph(text);
    }

    static String duration(long millis) {
        long seconds = Math.round(millis / 1000.0);
        return seconds < 60 ? String.format(Translations.getString("MeasurementsPanel.Seconds"), seconds) //$NON-NLS-1$
                : String.format(Translations.getString("MeasurementsPanel.Minutes"), (seconds + 30) / 60); //$NON-NLS-1$
    }

    // ---- the groups table --------------------------------------------------------------------

    private final class GroupModel extends AbstractTableModel {
        final Map<TestGroup, Boolean> selected = new EnumMap<>(TestGroup.class);

        Set<TestGroup> selected() {
            Set<TestGroup> groups = EnumSet.noneOf(TestGroup.class);
            for (Map.Entry<TestGroup, Boolean> e : selected.entrySet()) {
                if (Boolean.TRUE.equals(e.getValue())) {
                    groups.add(e.getKey());
                }
            }
            return groups;
        }

        @Override
        public int getRowCount() {
            return TestGroup.values().length;
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : Object.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0 && !diagnostics.isRunning();
        }

        @Override
        public Object getValueAt(int row, int column) {
            TestGroup group = TestGroup.values()[row];
            switch (column) {
                case 0:
                    return Boolean.TRUE.equals(selected.get(group));
                case 1:
                    return group;
                default:
                    return lastRun(group);
            }
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (column == 0) {
                selected.put(TestGroup.values()[row], Boolean.TRUE.equals(value));
                preset.setSelectedItem(Preset.Custom);
                describeStart();
                fireTableRowsUpdated(row, row);
            }
        }
    }

    /** "基准板 ⚡" over "5 个点的比例和垂直度 · 上次 6 分钟". */
    private final class GroupRenderer extends DefaultTableCellRenderer {
        private final JPanel cell = new JPanel(new BorderLayout(0, 2));
        private final JLabel title = new JLabel();
        private final JLabel line = new JLabel();

        GroupRenderer() {
            cell.setBorder(new EmptyBorder(6, 8, 6, 8));
            title.setFont(Ui.weighted(Ui.BASE, 600));
            title.setHorizontalTextPosition(SwingConstants.LEFT);
            title.setIconTextGap(6);
            line.setFont(Ui.font(11.5f));
            line.setForeground(Ui.text2());
            cell.add(title, BorderLayout.NORTH);
            cell.add(line, BorderLayout.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            TestGroup group = (TestGroup) value;
            title.setText(name(group));
            title.setIcon(STILL.contains(group) ? null : Ui.icon("zap", 12, Ui.warn())); //$NON-NLS-1$
            MachineDiagnosticsResults.Run run = lastRun(group);
            String what = what(group);
            if (what.length() > 40) {
                what = what.substring(0, 39) + "\u2026"; //$NON-NLS-1$
            }
            line.setText(run != null && run.getDurationMillis() > 0
                    ? what + " \u00b7 " + duration(run.getDurationMillis()) : what); //$NON-NLS-1$
            cell.setToolTipText(what(group));
            cell.setOpaque(true);
            cell.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            title.setForeground(table.getForeground());
            return cell;
        }
    }

    /** "09-22" when it ran, in the warning colour when a change since made it out of date. */
    private final class LastRenderer extends DefaultTableCellRenderer {
        private final JPanel cell = new JPanel(new java.awt.GridBagLayout());
        private final Chip chip = new Chip("", Chip.Tone.Ok, Chip.Shape.Status); //$NON-NLS-1$
        private final JLabel never = Ui.muted(Translations.getString("MeasurementsPanel.Never")); //$NON-NLS-1$

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            cell.removeAll();
            cell.setOpaque(true);
            cell.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            MachineDiagnosticsResults.Run run = (MachineDiagnosticsResults.Run) value;
            if (run == null) {
                never.setFont(Ui.font(11f));
                cell.add(never);
            }
            else {
                MachineDiagnosticsResults results = diagnostics.getLastResults();
                boolean current = results == null || results.isCurrent(TestGroup.values()[table.convertRowIndexToModel(row)]);
                chip.setText(DAY.format(run.getWhen()));
                chip.setTone(current ? Chip.Tone.Ok : Chip.Tone.Warn);
                cell.add(chip);
            }
            return cell;
        }
    }
}
