/*
 * Copyright (C) 2026 Pono contributors
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

package org.openpnp.machine.reference.wizards;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.components.AutoSelectTextTable;
import org.openpnp.gui.components.SimpleGraphView;
import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.gui.support.DoubleConverter;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.IntegerConverter;
import org.openpnp.gui.support.LongConverter;
import org.openpnp.gui.tablemodel.SolutionsTableModel;
import org.openpnp.machine.reference.ReferenceHead;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.ReferenceNozzleTip;
import org.openpnp.machine.reference.ReferenceNozzleTipCalibration;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis;
import org.openpnp.machine.reference.camera.AbstractSettlingCamera;
import org.openpnp.machine.reference.camera.ReferenceCamera;
import org.openpnp.machine.reference.driver.AbstractReferenceDriver;
import org.openpnp.machine.reference.driver.GcodeDriver;
import org.openpnp.machine.reference.solutions.MachineDiagnostics;
import org.openpnp.machine.reference.solutions.MachineDiagnostics.TestGroup;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Solutions;
import org.openpnp.spi.Axis;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Driver;
import org.openpnp.spi.Head;
import org.openpnp.spi.HeadMountable;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.NozzleTip;
import org.openpnp.spi.PropertySheetHolder;
import org.openpnp.spi.base.AbstractMachine;
import org.openpnp.util.UiUtils;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;

/**
 * The panel for {@link MachineDiagnostics}: which measurements to take, the parameters they are
 * taken with, and the results as they come in.
 */
@SuppressWarnings("serial")
public class MachineDiagnosticsWizard extends AbstractConfigurationWizard {

    private final ReferenceMachine machine;
    private final MachineDiagnostics diagnostics;

    /** A setting an element does not have, or a value it was never given. */
    private static final String NONE = "-"; //$NON-NLS-1$

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm"); //$NON-NLS-1$

    private final Map<TestGroup, JCheckBox> testChecks = new LinkedHashMap<>();
    private ElementSection axes;
    private ElementSection drivers;
    private ElementSection cameras;
    private ElementSection nozzles;
    private ElementSection calibration;
    private SolutionsTableModel issuesModel;
    private AutoSelectTextTable issuesTable;
    private JButton btnRun;
    private JButton btnStop;
    private JButton btnOpenReport;
    private JTextArea logArea;
    private JTextField repeats;
    private JTextField framesPerPoint;
    private JTextField stressSpeedFactors;
    private JTextField focusRange;
    private JTextField rulerStep;
    private JTextField measureSpeedFactor;
    private JTextField focusStep;
    private JTextField focusRepeats;
    private JTextField stressCycles;
    private JTextField stressDistance;
    private JTextField latencySpeedFactor;
    private JTextField noiseFrames;
    private JTextField backlashRepeats;
    private JTextField settleRepeats;
    private JTextField timingDistances;
    private JTextField positioningDistances;
    private JTextField speedFactors;
    private JTextField rotationTimingAngles;
    private JCheckBox timingIncludesZAndRotation;
    private JTextField stepTestDistance;
    private JTextField stepTestStep;
    private JTextField fieldOfViewGridSteps;
    private JTextField fieldOfViewGridFraction;
    private JTextField settleDistances;
    private JTextField settleSampleSeconds;
    private JTextField settleThresholdPixels;
    private JTextField homingCycles;
    private JTextField rotationTestAngles;
    private JTextField rotationApproachAngle;
    private JTextField rotationTestPartId;
    private JTextField machineSettleMs;
    private JTextArea firmwareCommands;
    private SimpleGraphView timingGraph;
    private SimpleGraphView positioningGraph;
    private SimpleGraphView stepGraph;
    private SimpleGraphView settleGraph;

    /**
     * Results arrive on the machine thread, so every update they prompt is pushed onto the event
     * thread. Attached and detached with the panel rather than in the constructor, so that
     * opening this page repeatedly does not leave a listener behind on the machine each time.
     */
    private final PropertyChangeListener resultListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent event) {
            SwingUtilities.invokeLater(() -> applyResult(event.getPropertyName()));
        }
    };

    public MachineDiagnosticsWizard(ReferenceMachine machine) {
        this.machine = machine;
        this.diagnostics = machine.getMachineDiagnostics();
        // What to run comes first: it is what the page is for, and it was buried under six
        // tables, which is where the user could not find it.
        createTestsPanel();
        createOverviewPanel();
        createCalibrationPanel();
        createIssuesPanel();
        createParametersPanel();
        createProgressPanel();
        createGraphsPanel();
        adaptDialog();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        diagnostics.addPropertyChangeListener(resultListener);
        applyResult(null);
        describeMachine();
    }

    @Override
    public void removeNotify() {
        diagnostics.removePropertyChangeListener(resultListener);
        super.removeNotify();
    }

    private void applyResult(String property) {
        if (property == null || "log".equals(property)) {
            String log = diagnostics.getLog();
            if (!log.equals(logArea.getText())) {
                logArea.setText(log);
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
        }
        if (property == null || "timingGraph".equals(property)) {
            timingGraph.setGraph(diagnostics.getTimingGraph());
        }
        if (property == null || "positioningGraph".equals(property)) {
            positioningGraph.setGraph(diagnostics.getPositioningGraph());
        }
        if (property == null || "stepGraph".equals(property)) {
            stepGraph.setGraph(diagnostics.getStepGraph());
        }
        if (property == null || "settleGraph".equals(property)) {
            settleGraph.setGraph(diagnostics.getSettleGraph());
        }
        if (property == null || "running".equals(property)
                || "lastReportDirectory".equals(property)) {
            adaptDialog();
        }
        if ("lastResults".equals(property)) {
            describeMachine();
        }
    }

    /**
     * Fill the read-only sections from the machine as it stands. Rebuilt rather than bound,
     * because there is nothing to edit here to lose and a machine is small enough to walk.
     */
    private void describeMachine() {
        describeAxes();
        describeDrivers();
        describeCameras();
        describeNozzles();
        describeCalibration();
        describeRuns();
    }

    /** Each test group's check says when it last ran and how long it took. */
    private void describeRuns() {
        MachineDiagnosticsResults results = diagnostics.getLastResults();
        for (Map.Entry<TestGroup, JCheckBox> entry : testChecks.entrySet()) {
            MachineDiagnosticsResults.Run run = results == null ? null
                    : results.getRun(entry.getKey());
            String name = Translations.getString(
                    "MachineDiagnosticsWizard.Test." + entry.getKey().name()); //$NON-NLS-1$
            if (run == null) {
                entry.getValue().setText(name);
                continue;
            }
            String when = DATE_FORMAT.format(run.getWhen());
            String took = run.getDurationMillis() <= 0 ? "" //$NON-NLS-1$
                    : " \u00b7 " + duration(run.getDurationMillis()); //$NON-NLS-1$
            entry.getValue().setText("<html>" + name + " <font color='gray'>" + when + took //$NON-NLS-1$ //$NON-NLS-2$
                    + "</font></html>"); //$NON-NLS-1$
        }
    }

    private static String duration(long millis) {
        long seconds = Math.round(millis / 1000.0);
        if (seconds < 60) {
            return seconds + " s"; //$NON-NLS-1$
        }
        return String.format("%d min %02d s", seconds / 60, seconds % 60); //$NON-NLS-1$
    }

    private void adaptDialog() {
        boolean running = diagnostics.isRunning();
        btnRun.setEnabled(!running);
        btnStop.setEnabled(running);
        btnOpenReport.setEnabled(diagnostics.getLastReportDirectory() != null);
    }

    private Action runAction = new AbstractAction(Translations.getString(
            "MachineDiagnosticsWizard.Action.Run"), Icons.start) { //$NON-NLS-1$
        {
            putValue(Action.SHORT_DESCRIPTION, Translations.getString(
                    "MachineDiagnosticsWizard.Action.Run.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // Take the parameters on screen before measuring with them, otherwise a value just
            // typed is not the one the run uses.
            applyAction.actionPerformed(e);
            Set<TestGroup> selected = EnumSet.noneOf(TestGroup.class);
            for (Map.Entry<TestGroup, JCheckBox> entry : testChecks.entrySet()) {
                if (entry.getValue().isSelected()) {
                    selected.add(entry.getKey());
                }
            }
            UiUtils.submitUiMachineTask(() -> diagnostics.run(machine, selected),
                    (report) -> reportIssues(), (t) -> UiUtils.showError(t));
        }
    };

    /**
     * Ask Issues and Solutions to look again, now that there is something measured to look at.
     * <p>
     * A conclusion the run reached is of no use sitting in a report the user has to open and
     * read; the checks that turn one into an issue live on the machine and only run when asked.
     * Called on the event thread, because publishing tells the tables to rebuild themselves.
     */
    private void reportIssues() {
        UiUtils.messageBoxOnException(() -> {
            machine.getSolutions().findIssues();
            machine.getSolutions().publishIssues();
        });
    }

    private Action stopAction = new AbstractAction(Translations.getString(
            "MachineDiagnosticsWizard.Action.Stop"), Icons.stop) { //$NON-NLS-1$
        {
            putValue(Action.SHORT_DESCRIPTION, Translations.getString(
                    "MachineDiagnosticsWizard.Action.Stop.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            diagnostics.abort();
        }
    };

    private Action openReportAction = new AbstractAction(Translations.getString(
            "MachineDiagnosticsWizard.Action.OpenReport"), Icons.export) { //$NON-NLS-1$
        {
            putValue(Action.SHORT_DESCRIPTION, Translations.getString(
                    "MachineDiagnosticsWizard.Action.OpenReport.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            UiUtils.messageBoxOnException(() -> {
                File directory = diagnostics.getLastReportDirectory();
                if (directory == null || !directory.isDirectory()) {
                    throw new Exception(Translations.getString(
                            "MachineDiagnosticsWizard.Error.NoReport")); //$NON-NLS-1$
                }
                if (!Desktop.isDesktopSupported()
                        || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    throw new Exception(directory.getAbsolutePath());
                }
                Desktop.getDesktop().open(directory);
            });
        }
    };

    // What the machine is defined as, which is what everything below was measured against. Read
    // only: every one of these fields is already edited in the Machine Setup tree, and a second
    // editor for the same field is two sets of validation that drift apart.

    private void createOverviewPanel() {
        axes = new ElementSection("Axes", "Name", "Type", "Letter", "Driver", "SoftLimits",
                "SafeZone", "Moves");
        drivers = new ElementSection("Drivers", "Name", "Kind", "Communications", "MotionControl",
                "MoveComplete", "PositionReport");
        cameras = new ElementSection("Cameras", "Name", "Looking", "UnitsPerPixel", "Settling",
                "LensCalibration");
        nozzles = new ElementSection("Nozzles", "Name", "NozzleTip", "HeadOffsets",
                "RotationMode");
    }

    private void describeAxes() {
        axes.clear();
        for (Axis axis : machine.getAxes()) {
            String letter = NONE;
            String driver = NONE;
            String softLimits = NONE;
            String safeZone = NONE;
            if (axis instanceof ReferenceControllerAxis) {
                ReferenceControllerAxis controllerAxis = (ReferenceControllerAxis) axis;
                letter = controllerAxis.getLetter();
                driver = controllerAxis.getDriver() != null
                        ? controllerAxis.getDriver().getName() : NONE;
                softLimits = range(
                        controllerAxis.isSoftLimitLowEnabled() ? controllerAxis.getSoftLimitLow()
                                : null,
                        controllerAxis.isSoftLimitHighEnabled() ? controllerAxis.getSoftLimitHigh()
                                : null);
                safeZone = range(
                        controllerAxis.isSafeZoneLowEnabled() ? controllerAxis.getSafeZoneLow()
                                : null,
                        controllerAxis.isSafeZoneHighEnabled() ? controllerAxis.getSafeZoneHigh()
                                : null);
            }
            axes.add(axis, axis.getName(), axis.getType(), letter, driver, softLimits, safeZone,
                    movedBy(axis));
        }
    }

    /** The nozzles and cameras that this axis moves, which is what makes it matter. */
    private String movedBy(Axis axis) {
        List<String> movables = new ArrayList<>();
        for (Head head : machine.getHeads()) {
            for (HeadMountable movable : head.getHeadMountables()) {
                if (movable.getMappedAxes(machine).contains(axis)) {
                    movables.add(movable.getName());
                }
            }
        }
        return movables.isEmpty() ? NONE : String.join(", ", movables);
    }

    private void describeDrivers() {
        drivers.clear();
        for (Driver driver : machine.getDrivers()) {
            String communications = NONE;
            if (driver instanceof AbstractReferenceDriver) {
                communications = String.valueOf(
                        ((AbstractReferenceDriver) driver).getCommunicationsType());
            }
            String moveComplete = NONE;
            String positionReport = NONE;
            if (driver instanceof GcodeDriver) {
                GcodeDriver gcodeDriver = (GcodeDriver) driver;
                moveComplete = oneLine(gcodeDriver.getCommand(null,
                        GcodeDriver.CommandType.MOVE_TO_COMPLETE_COMMAND));
                positionReport = oneLine(gcodeDriver.getCommand(null,
                        GcodeDriver.CommandType.POSITION_REPORT_REGEX));
            }
            drivers.add(driver, driver.getName(), driver.getClass().getSimpleName(), communications,
                    driver.getMotionControlType(), moveComplete, positionReport);
        }
    }

    private void describeCameras() {
        cameras.clear();
        for (Camera camera : machine.getAllCameras()) {
            String settling = NONE;
            if (camera instanceof AbstractSettlingCamera) {
                AbstractSettlingCamera settlingCamera = (AbstractSettlingCamera) camera;
                settling = settlingCamera.getSettleMethod()
                        + (settlingCamera.getSettleMethod()
                                == AbstractSettlingCamera.SettleMethod.FixedTime
                                        ? ", " + settlingCamera.getSettleTimeMs() + " ms" : "");
            }
            cameras.add(camera instanceof PropertySheetHolder ? camera : null, camera.getName(),
                    camera.getLooking(), camera.getUnitsPerPixel(), settling,
                    lensCalibration(camera));
        }
    }

    private String lensCalibration(Camera camera) {
        if (!(camera instanceof ReferenceCamera)) {
            return NONE;
        }
        ReferenceCamera referenceCamera = (ReferenceCamera) camera;
        if (referenceCamera.getAdvancedCalibration().isEnabled()) {
            return String.format("%s (rms %.3f)",
                    Translations.getString("MachineDiagnosticsWizard.State.Advanced"), //$NON-NLS-1$
                    referenceCamera.getAdvancedCalibration().getRmsError());
        }
        if (referenceCamera.getCalibration().isEnabled()) {
            return Translations.getString("MachineDiagnosticsWizard.State.On"); //$NON-NLS-1$
        }
        return Translations.getString("MachineDiagnosticsWizard.State.Off"); //$NON-NLS-1$
    }

    private void describeNozzles() {
        nozzles.clear();
        for (Head head : machine.getHeads()) {
            for (Nozzle nozzle : head.getNozzles()) {
                nozzles.add(nozzle, nozzle.getName(),
                        nozzle.getNozzleTip() != null ? nozzle.getNozzleTip().getName() : NONE,
                        nozzle.getHeadOffsets(), nozzle.getRotationMode());
            }
        }
    }

    // How the calibrations stand. Health rather than settings: a row says what state a
    // calibration is in and opens where it is done, and the fixes themselves are Issues and
    // Solutions' business.

    private void createCalibrationPanel() {
        calibration = new ElementSection("Calibration", "What", "Health", "State");
    }

    private void describeCalibration() {
        calibration.clear();
        MachineDiagnosticsResults results = diagnostics.getLastResults();
        for (Head head : machine.getHeads()) {
            if (head instanceof ReferenceHead) {
                describeHeadCalibration((ReferenceHead) head);
            }
        }
        for (Camera camera : machine.getAllCameras()) {
            describeCameraCalibration(camera);
        }
        for (Head head : machine.getHeads()) {
            for (Nozzle nozzle : head.getNozzles()) {
                boolean set = nozzle.getHeadOffsets().getLinearLengthTo(
                        new Location(nozzle.getHeadOffsets().getUnits())).getValue() > 0;
                addCalibrationRow(nozzle, "NozzleOffsets",
                        set ? Solutions.Severity.Information : Solutions.Severity.Warning,
                        set ? nozzle.getHeadOffsets().toString() : notSet(), nozzle.getName());
            }
        }
        for (NozzleTip nozzleTip : machine.getNozzleTips()) {
            describeNozzleTipCalibration(nozzleTip);
        }
        for (Axis axis : machine.getAxes()) {
            if (axis instanceof ReferenceControllerAxis
                    && (axis.getType() == Axis.Type.X || axis.getType() == Axis.Type.Y)) {
                describeBacklash((ReferenceControllerAxis) axis, results);
            }
        }
        for (TestGroup group : TestGroup.values()) {
            MachineDiagnosticsResults.Run run = results != null ? results.getRun(group) : null;
            addCalibrationRow(null, "LastRun", Solutions.Severity.Information,
                    run == null ? Translations.getString(
                            "MachineDiagnosticsWizard.State.Never") //$NON-NLS-1$
                            : DATE_FORMAT.format(run.getWhen()),
                    Translations.getString("MachineDiagnosticsWizard.Test." + group.name())); //$NON-NLS-1$
        }
    }

    private void describeHeadCalibration(ReferenceHead head) {
        Location primary = head.getCalibrationPrimaryFiducialLocation();
        boolean primarySet = primary != null && !(primary.getX() == 0 && primary.getY() == 0);
        addCalibrationRow(head, "PrimaryFiducial",
                primarySet ? Solutions.Severity.Information : Solutions.Severity.Warning,
                primarySet ? primary.toString() : notSet(), head.getName());
        Location secondary = head.getCalibrationSecondaryFiducialLocation();
        boolean secondarySet = secondary != null && !(secondary.getX() == 0 && secondary.getY() == 0);
        addCalibrationRow(head, "SecondaryFiducial",
                secondarySet ? Solutions.Severity.Information : Solutions.Severity.Suggestion,
                secondarySet ? secondary.toString() : notSet(), head.getName());
        if (head.getVisualHomingMethod() == ReferenceHead.VisualHomingMethod.None) {
            addCalibrationRow(head, "VisualHoming", Solutions.Severity.Suggestion,
                    Translations.getString("MachineDiagnosticsWizard.State.Off"), //$NON-NLS-1$
                    head.getName());
        }
        else {
            Location homing = head.getHomingFiducialLocation();
            Length apart = primarySet && homing != null ? primary.getLinearLengthTo(homing) : null;
            addCalibrationRow(head, "VisualHoming",
                    apart != null && apart.convertToUnits(LengthUnit.Millimeters).getValue() > 0.2
                            ? Solutions.Severity.Warning : Solutions.Severity.Information,
                    apart == null ? String.valueOf(head.getVisualHomingMethod())
                            : String.format("%s, %.4f mm %s", head.getVisualHomingMethod(),
                                    apart.convertToUnits(LengthUnit.Millimeters).getValue(),
                                    Translations.getString(
                                            "MachineDiagnosticsWizard.State.FromPrimary")), //$NON-NLS-1$
                    head.getName());
        }
    }

    private void describeCameraCalibration(Camera camera) {
        PropertySheetHolder target = camera instanceof PropertySheetHolder ? camera : null;
        boolean calibrated = camera instanceof ReferenceCamera
                && (((ReferenceCamera) camera).getCalibration().isEnabled()
                        || ((ReferenceCamera) camera).getAdvancedCalibration().isEnabled());
        addCalibrationRow(target, "LensCalibration",
                calibrated ? Solutions.Severity.Information : Solutions.Severity.Suggestion,
                lensCalibration(camera), camera.getName());
        boolean initialized = camera.getUnitsPerPixel().isInitialized();
        addCalibrationRow(target, "UnitsPerPixel",
                initialized ? Solutions.Severity.Information : Solutions.Severity.Warning,
                initialized ? camera.getUnitsPerPixel().toString() : notSet(), camera.getName());
    }

    private void describeNozzleTipCalibration(NozzleTip nozzleTip) {
        if (!(nozzleTip instanceof ReferenceNozzleTip)) {
            return;
        }
        ReferenceNozzleTipCalibration runout = ((ReferenceNozzleTip) nozzleTip).getCalibration();
        addCalibrationRow(nozzleTip, "RunOut",
                runout.isEnabled() ? Solutions.Severity.Information : Solutions.Severity.Suggestion,
                runout.isEnabled()
                        ? String.format("%s, %s", runout.getRunoutCompensationAlgorithm(),
                                runout.getRecalibrationTrigger())
                        : Translations.getString("MachineDiagnosticsWizard.State.Off"), //$NON-NLS-1$
                nozzleTip.getName());
    }

    private void describeBacklash(ReferenceControllerAxis axis,
            MachineDiagnosticsResults results) {
        Double measured = null;
        if (results != null) {
            for (MachineDiagnosticsResults.Positioning positioning : results.getPositioning()) {
                if (positioning.getAxisId().equals(axis.getId())) {
                    measured = positioning.getBacklashMaxMm();
                }
            }
        }
        String state = String.format("%s, %s", axis.getBacklashCompensationMethod(),
                axis.getBacklashOffset());
        Solutions.Severity health = Solutions.Severity.Information;
        if (measured != null) {
            state += String.format(", %s %.4f mm", Translations.getString(
                    "MachineDiagnosticsWizard.State.Measured"), measured); //$NON-NLS-1$
            if (measured > axis.getBacklashOffset().convertToUnits(LengthUnit.Millimeters)
                    .getValue()) {
                health = Solutions.Severity.Warning;
            }
        }
        addCalibrationRow(axis, "Backlash", health, state, axis.getName());
    }

    /**
     * @param element What the row is about, which the button opens. Null for a row that describes
     *        no single element, such as when a test group last ran.
     * @param subject The element's name, put into the row's description rather than into a column
     *        of its own, so that the phrasing can put it where the language wants it.
     */
    private void addCalibrationRow(PropertySheetHolder element, String key,
            Solutions.Severity health, String state, String subject) {
        calibration.add(element, String.format(Translations.getString(
                "MachineDiagnosticsWizard.Calibration." + key), subject), health, state); //$NON-NLS-1$
    }

    // What the measurements turned into, which is the point of taking them.

    private void createIssuesPanel() {
        JPanel panel = titledPanel("IssuesPanel");
        panel.setLayout(new BorderLayout(0, 0));
        issuesModel = new SolutionsTableModel(machine.getSolutions());
        issuesTable = new AutoSelectTextTable(issuesModel);
        TableRowSorter<SolutionsTableModel> sorter = new TableRowSorter<>(issuesModel);
        // Only what these checks found. The machine's other issues have a page of their own; what
        // belongs here is the loop from a measurement to the setting it disagrees with.
        sorter.setRowFilter(new RowFilter<SolutionsTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends SolutionsTableModel, ? extends Integer> entry) {
                return entry.getModel().getIssue(entry.getIdentifier())
                        instanceof MachineDiagnostics.Finding;
            }
        });
        issuesTable.setRowSorter(sorter);
        SolutionsTableModel.applyTableUi(issuesTable);
        issuesTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane scroll = new JScrollPane(issuesTable);
        scroll.setPreferredSize(new Dimension(600, 140));
        panel.add(scroll, BorderLayout.CENTER);
        JPanel buttons = new JPanel();
        buttons.add(new JButton(lookAgainAction));
        buttons.add(new JButton(acceptAction));
        buttons.add(new JButton(dismissAction));
        buttons.add(new JButton(reopenAction));
        panel.add(buttons, BorderLayout.SOUTH);
    }

    private List<Solutions.Issue> selectedIssues() {
        List<Solutions.Issue> issues = new ArrayList<>();
        for (int row : issuesTable.getSelectedRows()) {
            issues.add(issuesModel.getIssue(issuesTable.convertRowIndexToModel(row)));
        }
        return issues;
    }

    private Action lookAgainAction = new AbstractAction(Translations.getString(
            "MachineDiagnosticsWizard.Action.LookAgain"), Icons.solutions) { //$NON-NLS-1$
        {
            putValue(Action.SHORT_DESCRIPTION, Translations.getString(
                    "MachineDiagnosticsWizard.Action.LookAgain.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            reportIssues();
        }
    };

    private Action acceptAction = new AbstractAction(Translations.getString(
            "MachineDiagnosticsWizard.Action.Accept"), Icons.accept) { //$NON-NLS-1$
        {
            putValue(Action.SHORT_DESCRIPTION, Translations.getString(
                    "MachineDiagnosticsWizard.Action.Accept.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            UiUtils.messageBoxOnException(() -> {
                for (Solutions.Issue issue : selectedIssues()) {
                    if (issue.canBeAccepted()) {
                        if (issue.getState() != Solutions.State.Solved) {
                            issue.setStateCall(Solutions.State.Solved);
                        }
                    }
                    // An issue with nothing of its own to apply is handled as a dismissal, the
                    // way the Issues and Solutions page handles one.
                    else if (issue.getState() != Solutions.State.Dismissed) {
                        issue.setStateCall(Solutions.State.Dismissed);
                    }
                }
                describeMachine();
            });
        }
    };

    private Action dismissAction = new AbstractAction(Translations.getString(
            "MachineDiagnosticsWizard.Action.Dismiss"), Icons.dismiss) { //$NON-NLS-1$
        {
            putValue(Action.SHORT_DESCRIPTION, Translations.getString(
                    "MachineDiagnosticsWizard.Action.Dismiss.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            UiUtils.messageBoxOnException(() -> {
                for (Solutions.Issue issue : selectedIssues()) {
                    if (issue.getState() != Solutions.State.Dismissed) {
                        issue.setStateCall(Solutions.State.Dismissed);
                    }
                }
            });
        }
    };

    private Action reopenAction = new AbstractAction(Translations.getString(
            "MachineDiagnosticsWizard.Action.Reopen"), Icons.undo) { //$NON-NLS-1$
        {
            putValue(Action.SHORT_DESCRIPTION, Translations.getString(
                    "MachineDiagnosticsWizard.Action.Reopen.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            UiUtils.messageBoxOnException(() -> {
                for (Solutions.Issue issue : selectedIssues()) {
                    if (issue.getState() != Solutions.State.Open) {
                        issue.setStateCall(Solutions.State.Open);
                    }
                }
                describeMachine();
            });
        }
    };

    /**
     * A titled panel of the kind this page is made of, added in the order it is created.
     */
    private JPanel titledPanel(String key) {
        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"), //$NON-NLS-1$
                Translations.getString(
                        "MachineDiagnosticsWizard." + key + ".Border.title"), //$NON-NLS-1$
                TitledBorder.LEADING, TitledBorder.TOP, null, null));
        contentPanel.add(panel);
        return panel;
    }

    /** A value a setting was never given, said in words rather than left blank. */
    private static String notSet() {
        return Translations.getString("MachineDiagnosticsWizard.State.NotSet"); //$NON-NLS-1$
    }

    private static String range(Length low, Length high) {
        if (low == null && high == null) {
            return NONE;
        }
        return (low == null ? NONE : low.toString()) + " .. "
                + (high == null ? NONE : high.toString());
    }

    /** A G-code command as one line, so that a multi-line one does not stretch its row. */
    private static String oneLine(String command) {
        return command == null ? NONE : command.replace("\n", " | ").trim(); //$NON-NLS-1$
    }

    /**
     * A read-only table of machine elements, and the button that opens the selected one where it
     * is set. The rows stand for the elements they describe, so that this page can hand the user
     * over to the wizard that edits an element rather than editing the same fields itself.
     */
    private class ElementSection {
        private final ElementTableModel model;
        private final AutoSelectTextTable table;

        ElementSection(String key, String... columnKeys) {
            String[] columns = new String[columnKeys.length];
            for (int i = 0; i < columnKeys.length; i++) {
                columns[i] = Translations.getString("MachineDiagnosticsWizard.Column." //$NON-NLS-1$
                        + columnKeys[i]);
            }
            model = new ElementTableModel(columns);
            table = new AutoSelectTextTable(model);
            SolutionsTableModel.applyTableUi(table);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        edit();
                    }
                }
            });
            JPanel panel = titledPanel(key);
            panel.setLayout(new BorderLayout(0, 0));
            JScrollPane scroll = new JScrollPane(table);
            scroll.setPreferredSize(new Dimension(600, 90));
            panel.add(scroll, BorderLayout.CENTER);
            JPanel buttons = new JPanel();
            buttons.add(new JButton(new AbstractAction(Translations.getString(
                    "MachineDiagnosticsWizard.Action.Edit"), Icons.navigateNext) { //$NON-NLS-1$
                {
                    putValue(Action.SHORT_DESCRIPTION, Translations.getString(
                            "MachineDiagnosticsWizard.Action.Edit.Description")); //$NON-NLS-1$
                }

                @Override
                public void actionPerformed(ActionEvent e) {
                    edit();
                }
            }));
            panel.add(buttons, BorderLayout.SOUTH);
        }

        void clear() {
            model.clear();
        }

        void add(PropertySheetHolder element, Object... values) {
            model.add(element, values);
        }

        /** Open what the selected row is about, in the tree that edits it. */
        private void edit() {
            int row = table.getSelectedRow();
            PropertySheetHolder element = row < 0 ? null
                    : model.getElement(table.convertRowIndexToModel(row));
            MainFrame frame = MainFrame.get();
            if (element == null || frame == null) {
                return;
            }
            frame.showTab(frame.getMachineSetupTab());
            frame.getMachineSetupTab().selectPropertySheetHolder(element);
        }
    }

    /** The rows of an {@link ElementSection}, each remembering the element it describes. */
    private static class ElementTableModel extends AbstractTableModel {
        private final String[] columns;
        private final List<PropertySheetHolder> elements = new ArrayList<>();
        private final List<Object[]> rows = new ArrayList<>();

        ElementTableModel(String[] columns) {
            this.columns = columns;
        }

        void clear() {
            elements.clear();
            rows.clear();
            fireTableDataChanged();
        }

        void add(PropertySheetHolder element, Object[] values) {
            elements.add(element);
            rows.add(values);
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
        }

        PropertySheetHolder getElement(int row) {
            return elements.get(row);
        }

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
            // The health column carries a Severity, which the Issues and Solutions renderer
            // colours; everything else is read as text.
            for (Object[] row : rows) {
                if (row[column] != null) {
                    return row[column] instanceof Solutions.Severity ? Solutions.Severity.class
                            : String.class;
                }
            }
            return String.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Object getValueAt(int row, int column) {
            Object value = rows.get(row)[column];
            return value instanceof Solutions.Severity ? value : String.valueOf(value);
        }
    }

    private void createTestsPanel() {
        JPanel panel = titledPanel("TestsPanel");
        panel.setLayout(new FormLayout(new ColumnSpec[] {
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(120dlu;default)"),
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(120dlu;default)"),
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("default:grow"), },
                new RowSpec[] {
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC, }));

        // Firmware and the snapshot move nothing, so they are the safe pair to start with and are
        // the only ones on by default. The rest are in the order they run.
        addTestCheck(panel, TestGroup.Firmware, "2, 2", true);
        addTestCheck(panel, TestGroup.ConfigSnapshot, "4, 2", true);
        addTestCheck(panel, TestGroup.VisionNoise, "2, 4", false);
        addTestCheck(panel, TestGroup.CameraLatency, "4, 4", false);
        addTestCheck(panel, TestGroup.Kinematics, "2, 6", false);
        addTestCheck(panel, TestGroup.LostSteps, "4, 6", false);
        addTestCheck(panel, TestGroup.XyPositioning, "2, 8", false);
        addTestCheck(panel, TestGroup.CameraSettle, "4, 8", false);
        addTestCheck(panel, TestGroup.Homing, "2, 10", false);
        addTestCheck(panel, TestGroup.RotationBacklash, "4, 10", false);
        addTestCheck(panel, TestGroup.ZFocus, "2, 12", false);
        addTestCheck(panel, TestGroup.DatumBoard, "4, 12", false);

        btnRun = new JButton(runAction);
        panel.add(btnRun, "2, 14");
        btnStop = new JButton(stopAction);
        panel.add(btnStop, "4, 14");
        btnOpenReport = new JButton(openReportAction);
        panel.add(btnOpenReport, "6, 14, left, default");
    }

    private void addTestCheck(JPanel panel, TestGroup group, String constraints, boolean selected) {
        JCheckBox check = new JCheckBox(Translations.getString(
                "MachineDiagnosticsWizard.Test." + group.name())); //$NON-NLS-1$
        check.setToolTipText(Translations.getString(
                "MachineDiagnosticsWizard.Test." + group.name() + ".toolTipText")); //$NON-NLS-1$
        check.setSelected(selected);
        panel.add(check, constraints);
        testChecks.put(group, check);
    }

    private void createParametersPanel() {
        JPanel panel = titledPanel("ParametersPanel");
        ColumnSpec[] columns = new ColumnSpec[] {
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(90dlu;default)"),
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(70dlu;default)"),
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(90dlu;default)"),
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(70dlu;default)"),
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("default:grow"), };
        RowSpec[] rows = new RowSpec[36];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = i % 2 == 0 ? FormSpecs.RELATED_GAP_ROWSPEC : FormSpecs.DEFAULT_ROWSPEC;
        }
        panel.setLayout(new FormLayout(columns, rows));

        repeats = addField(panel, "Repeats", "2, 2", "4, 2");
        machineSettleMs = addField(panel, "MachineSettle", "6, 2", "8, 2");
        framesPerPoint = addField(panel, "FramesPerPoint", "2, 22", "4, 22");
        noiseFrames = addField(panel, "NoiseFrames", "6, 22", "8, 22");
        backlashRepeats = addField(panel, "BacklashRepeats", "2, 24", "4, 24");
        settleRepeats = addField(panel, "SettleRepeats", "6, 24", "8, 24");
        stressSpeedFactors = addField(panel, "StressSpeedFactors", "2, 26", "4, 26");
        stressCycles = addField(panel, "StressCycles", "6, 26", "8, 26");
        stressDistance = addField(panel, "StressDistance", "2, 28", "4, 28");
        latencySpeedFactor = addField(panel, "LatencySpeedFactor", "6, 28", "8, 28");
        focusRange = addField(panel, "FocusRange", "2, 30", "4, 30");
        focusStep = addField(panel, "FocusStep", "6, 30", "8, 30");
        focusRepeats = addField(panel, "FocusRepeats", "2, 32", "4, 32");
        rulerStep = addField(panel, "RulerStep", "6, 32", "8, 32");
        measureSpeedFactor = addField(panel, "MeasureSpeedFactor", "2, 34", "4, 34");
        timingDistances = addField(panel, "TimingDistances", "2, 4", "4, 4");
        rotationTimingAngles = addField(panel, "RotationTimingAngles", "6, 4", "8, 4");
        timingIncludesZAndRotation = new JCheckBox(Translations.getString(
                "MachineDiagnosticsWizard.ParametersPanel.TimingIncludesZAndRotation.text")); //$NON-NLS-1$
        timingIncludesZAndRotation.setToolTipText(Translations.getString(
                "MachineDiagnosticsWizard.ParametersPanel.TimingIncludesZAndRotation.toolTipText")); //$NON-NLS-1$
        panel.add(timingIncludesZAndRotation, "2, 6, 3, 1");
        positioningDistances = addField(panel, "PositioningDistances", "2, 8", "4, 8");
        speedFactors = addField(panel, "SpeedFactors", "6, 8", "8, 8");
        stepTestDistance = addField(panel, "StepTestDistance", "2, 10", "4, 10");
        stepTestStep = addField(panel, "StepTestStep", "6, 10", "8, 10");
        fieldOfViewGridSteps = addField(panel, "FieldOfViewGridSteps", "2, 12", "4, 12");
        fieldOfViewGridFraction = addField(panel, "FieldOfViewGridFraction", "6, 12", "8, 12");
        settleDistances = addField(panel, "SettleDistances", "2, 14", "4, 14");
        settleSampleSeconds = addField(panel, "SettleSampleSeconds", "6, 14", "8, 14");
        settleThresholdPixels = addField(panel, "SettleThresholdPixels", "2, 16", "4, 16");
        homingCycles = addField(panel, "HomingCycles", "6, 16", "8, 16");
        rotationTestAngles = addField(panel, "RotationTestAngles", "2, 18", "4, 18");
        rotationApproachAngle = addField(panel, "RotationApproachAngle", "6, 18", "8, 18");
        rotationTestPartId = addField(panel, "RotationTestPartId", "2, 20", "4, 20");

        JLabel lblFirmwareCommands = new JLabel(Translations.getString(
                "MachineDiagnosticsWizard.ParametersPanel.FirmwareCommands.text")); //$NON-NLS-1$
        lblFirmwareCommands.setToolTipText(Translations.getString(
                "MachineDiagnosticsWizard.ParametersPanel.FirmwareCommands.toolTipText")); //$NON-NLS-1$
        panel.add(lblFirmwareCommands, "2, 36, right, top");
        firmwareCommands = new JTextArea();
        firmwareCommands.setRows(4);
        JScrollPane firmwareScroll = new JScrollPane(firmwareCommands);
        firmwareScroll.setPreferredSize(new Dimension(200, 70));
        panel.add(firmwareScroll, "4, 36, 5, 1, fill, fill");
    }

    private JTextField addField(JPanel panel, String key, String labelConstraints,
            String fieldConstraints) {
        JLabel label = new JLabel(Translations.getString(
                "MachineDiagnosticsWizard.ParametersPanel." + key + ".text")); //$NON-NLS-1$
        label.setToolTipText(Translations.getString(
                "MachineDiagnosticsWizard.ParametersPanel." + key + ".toolTipText")); //$NON-NLS-1$
        panel.add(label, labelConstraints + ", right, default");
        JTextField field = new JTextField();
        field.setColumns(10);
        panel.add(field, fieldConstraints + ", fill, default");
        return field;
    }

    private void createProgressPanel() {
        JPanel panel = titledPanel("ProgressPanel");
        panel.setLayout(new FormLayout(new ColumnSpec[] {
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("default:grow"), },
                new RowSpec[] {
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        RowSpec.decode("default:grow"), }));
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setPreferredSize(new Dimension(600, 180));
        panel.add(scroll, "2, 2, fill, fill");
    }

    private void createGraphsPanel() {
        JPanel panel = titledPanel("GraphsPanel");
        panel.setLayout(new FormLayout(new ColumnSpec[] {
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(90dlu;default)"),
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("default:grow"), },
                new RowSpec[] {
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        RowSpec.decode("default:grow"),
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        RowSpec.decode("default:grow"),
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        RowSpec.decode("default:grow"),
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        RowSpec.decode("default:grow"), }));
        timingGraph = addGraph(panel, "Timing", "2, 2", "4, 2");
        positioningGraph = addGraph(panel, "Positioning", "2, 4", "4, 4");
        stepGraph = addGraph(panel, "Step", "2, 6", "4, 6");
        settleGraph = addGraph(panel, "Settle", "2, 8", "4, 8");
    }

    private SimpleGraphView addGraph(JPanel panel, String key, String labelConstraints,
            String graphConstraints) {
        JLabel label = new JLabel(Translations.getString(
                "MachineDiagnosticsWizard.GraphsPanel." + key + ".text")); //$NON-NLS-1$
        label.setToolTipText(Translations.getString(
                "MachineDiagnosticsWizard.GraphsPanel." + key + ".toolTipText")); //$NON-NLS-1$
        panel.add(label, labelConstraints + ", right, top");
        SimpleGraphView graph = new SimpleGraphView();
        graph.setPreferredSize(new Dimension(400, 140));
        graph.setFont(new Font("Dialog", Font.PLAIN, 11));
        panel.add(graph, graphConstraints + ", fill, fill");
        return graph;
    }

    @Override
    protected AbstractMachine getMachine() {
        return machine;
    }

    @Override
    public void createBindings() {
        IntegerConverter integerConverter = new IntegerConverter();
        LongConverter longConverter = new LongConverter();
        DoubleConverter doubleConverter = new DoubleConverter("%f");

        addWrappedBinding(diagnostics, "repeats", repeats, "text", integerConverter);
        addWrappedBinding(diagnostics, "machineSettleMs", machineSettleMs, "text", longConverter);
        addWrappedBinding(diagnostics, "timingDistances", timingDistances, "text");
        addWrappedBinding(diagnostics, "rotationTimingAngles", rotationTimingAngles, "text");
        addWrappedBinding(diagnostics, "timingIncludesZAndRotation", timingIncludesZAndRotation,
                "selected");
        addWrappedBinding(diagnostics, "positioningDistances", positioningDistances, "text");
        addWrappedBinding(diagnostics, "speedFactors", speedFactors, "text");
        addWrappedBinding(diagnostics, "stepTestDistanceMm", stepTestDistance, "text",
                doubleConverter);
        addWrappedBinding(diagnostics, "stepTestStepMm", stepTestStep, "text", doubleConverter);
        addWrappedBinding(diagnostics, "fieldOfViewGridSteps", fieldOfViewGridSteps, "text",
                integerConverter);
        addWrappedBinding(diagnostics, "fieldOfViewGridFraction", fieldOfViewGridFraction, "text",
                doubleConverter);
        addWrappedBinding(diagnostics, "settleDistances", settleDistances, "text");
        addWrappedBinding(diagnostics, "settleSampleSeconds", settleSampleSeconds, "text",
                doubleConverter);
        addWrappedBinding(diagnostics, "settleThresholdPixels", settleThresholdPixels, "text",
                doubleConverter);
        addWrappedBinding(diagnostics, "homingCycles", homingCycles, "text", integerConverter);
        addWrappedBinding(diagnostics, "framesPerPoint", framesPerPoint, "text", integerConverter);
        addWrappedBinding(diagnostics, "noiseFrames", noiseFrames, "text", integerConverter);
        addWrappedBinding(diagnostics, "backlashRepeats", backlashRepeats, "text", integerConverter);
        addWrappedBinding(diagnostics, "settleRepeats", settleRepeats, "text", integerConverter);
        addWrappedBinding(diagnostics, "stressSpeedFactors", stressSpeedFactors, "text");
        addWrappedBinding(diagnostics, "stressCycles", stressCycles, "text", integerConverter);
        addWrappedBinding(diagnostics, "stressDistanceMm", stressDistance, "text", doubleConverter);
        addWrappedBinding(diagnostics, "latencySpeedFactor", latencySpeedFactor, "text", doubleConverter);
        addWrappedBinding(diagnostics, "focusRangeMm", focusRange, "text", doubleConverter);
        addWrappedBinding(diagnostics, "focusStepMm", focusStep, "text", doubleConverter);
        addWrappedBinding(diagnostics, "focusRepeats", focusRepeats, "text", integerConverter);
        addWrappedBinding(diagnostics, "rulerStepMm", rulerStep, "text", doubleConverter);
        addWrappedBinding(diagnostics, "measureSpeedFactor", measureSpeedFactor, "text", doubleConverter);
        addWrappedBinding(diagnostics, "rotationTestAngles", rotationTestAngles, "text");
        addWrappedBinding(diagnostics, "rotationApproachAngle", rotationApproachAngle, "text",
                doubleConverter);
        addWrappedBinding(diagnostics, "rotationTestPartId", rotationTestPartId, "text");
        addWrappedBinding(diagnostics, "firmwareCommands", firmwareCommands, "text");

        // The graphs and the log are results rather than settings, and they arrive on the machine
        // thread, so they are not bound here. See resultListener.
    }

    @Override
    public void validateInput() throws Exception {
        if (diagnostics.isRunning()) {
            throw new Exception(Translations.getString(
                    "MachineDiagnosticsWizard.Error.Running")); //$NON-NLS-1$
        }
    }
}
