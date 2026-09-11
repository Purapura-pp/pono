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

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.EnumSet;
import java.util.LinkedHashMap;
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
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;

import org.openpnp.Translations;
import org.openpnp.gui.components.SimpleGraphView;
import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.gui.support.DoubleConverter;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.IntegerConverter;
import org.openpnp.gui.support.LongConverter;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.solutions.MachineDiagnostics;
import org.openpnp.machine.reference.solutions.MachineDiagnostics.TestGroup;
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

    private final Map<TestGroup, JCheckBox> testChecks = new LinkedHashMap<>();
    private JButton btnRun;
    private JButton btnStop;
    private JButton btnOpenReport;
    private JTextArea logArea;
    private JTextField repeats;
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
        createTestsPanel();
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
            UiUtils.submitUiMachineTask(() -> {
                diagnostics.run(machine, selected);
            });
        }
    };

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

    private void createTestsPanel() {
        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"),
                Translations.getString("MachineDiagnosticsWizard.TestsPanel.Border.title"), //$NON-NLS-1$
                TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0)));
        contentPanel.add(panel);
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
                        FormSpecs.DEFAULT_ROWSPEC, }));

        // Firmware and the snapshot move nothing, so they are the safe pair to start with and are
        // the only ones on by default.
        addTestCheck(panel, TestGroup.Firmware, "2, 2", true);
        addTestCheck(panel, TestGroup.ConfigSnapshot, "4, 2", true);
        addTestCheck(panel, TestGroup.Kinematics, "2, 4", false);
        addTestCheck(panel, TestGroup.XyPositioning, "4, 4", false);
        addTestCheck(panel, TestGroup.CameraSettle, "2, 6", false);
        addTestCheck(panel, TestGroup.Homing, "4, 6", false);
        addTestCheck(panel, TestGroup.RotationBacklash, "2, 8", false);

        btnRun = new JButton(runAction);
        panel.add(btnRun, "2, 10");
        btnStop = new JButton(stopAction);
        panel.add(btnStop, "4, 10");
        btnOpenReport = new JButton(openReportAction);
        panel.add(btnOpenReport, "6, 10, left, default");
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
        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"),
                Translations.getString("MachineDiagnosticsWizard.ParametersPanel.Border.title"), //$NON-NLS-1$
                TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0)));
        contentPanel.add(panel);
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
        RowSpec[] rows = new RowSpec[22];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = i % 2 == 0 ? FormSpecs.RELATED_GAP_ROWSPEC : FormSpecs.DEFAULT_ROWSPEC;
        }
        panel.setLayout(new FormLayout(columns, rows));

        repeats = addField(panel, "Repeats", "2, 2", "4, 2");
        machineSettleMs = addField(panel, "MachineSettle", "6, 2", "8, 2");
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
        panel.add(lblFirmwareCommands, "2, 22, right, top");
        firmwareCommands = new JTextArea();
        firmwareCommands.setRows(4);
        JScrollPane firmwareScroll = new JScrollPane(firmwareCommands);
        firmwareScroll.setPreferredSize(new Dimension(200, 70));
        panel.add(firmwareScroll, "4, 22, 5, 1, fill, fill");
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
        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"),
                Translations.getString("MachineDiagnosticsWizard.ProgressPanel.Border.title"), //$NON-NLS-1$
                TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0)));
        contentPanel.add(panel);
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
        JPanel panel = new JPanel();
        panel.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"),
                Translations.getString("MachineDiagnosticsWizard.GraphsPanel.Border.title"), //$NON-NLS-1$
                TitledBorder.LEADING, TitledBorder.TOP, null, new Color(0, 0, 0)));
        contentPanel.add(panel);
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
