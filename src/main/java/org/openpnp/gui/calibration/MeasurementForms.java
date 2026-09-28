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

package org.openpnp.gui.calibration;

import java.awt.Component;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openpnp.Translations;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Ui;
import org.openpnp.machine.reference.solutions.MachineDiagnostics;
import org.openpnp.machine.reference.solutions.MachineDiagnostics.TestGroup;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults;

/**
 * What the calibration page says about the measurement groups: their names and what they do,
 * their parameters as a form, and what is about to move before one runs. The groups a step
 * measures as one of its phases have their parameters beside the step; the rest are the page's
 * diagnostics, which only report.
 */
public final class MeasurementForms {
    /** The groups that only talk to the controller or read the configuration. */
    public static final Set<TestGroup> STILL = EnumSet.of(TestGroup.Firmware, TestGroup.ConfigSnapshot);
    /** The groups that run the axes fast, over the whole travel, for minutes. */
    static final Set<TestGroup> LONG_TRAVEL = EnumSet.of(TestGroup.LostSteps, TestGroup.Kinematics,
            TestGroup.HysteresisMap, TestGroup.Homing, TestGroup.XyPositioning, TestGroup.DatumBoard);

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

    private MeasurementForms() {
    }

    public static String name(TestGroup group) {
        return Translations.getString("MachineDiagnosticsWizard.Test." + group.name()); //$NON-NLS-1$
    }

    /** What the group does, in a sentence or two, without the markup of its tooltip. */
    public static String what(TestGroup group) {
        String key = "MachineDiagnosticsWizard.Test." + group.name() + ".toolTipText"; //$NON-NLS-1$ //$NON-NLS-2$
        return Translations.has(key) ? Translations.getString(key).replaceAll("<[^>]+>", " ").trim() : ""; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    public static boolean movesMachine(TestGroup group) {
        return !STILL.contains(group);
    }

    /** "12 秒", "6 分钟". */
    public static String duration(long millis) {
        long seconds = Math.round(millis / 1000.0);
        return seconds < 60 ? String.format(Translations.getString("MeasurementForms.Seconds"), seconds) //$NON-NLS-1$
                : String.format(Translations.getString("MeasurementForms.Minutes"), (seconds + 30) / 60); //$NON-NLS-1$
    }

    /** When the group last ran, or null for never. */
    public static MachineDiagnosticsResults.Run lastRun(MachineDiagnostics diagnostics, TestGroup group) {
        MachineDiagnosticsResults results = diagnostics.getLastResults();
        return results == null ? null : results.getRun(group);
    }

    /** The group's form: what it does, its parameters, the ones all moving groups share, its last run. */
    public static FormWizard form(MachineDiagnostics diagnostics, TestGroup group) {
        Form.Builder form = Form.of(diagnostics).named(name(group))
                .section("MeasurementForms.What", "info") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", Forms.paragraph(what(group))); //$NON-NLS-1$
        String[][] parameters = PARAMETERS.get(group);
        if (parameters != null) {
            form.section("MeasurementForms.Parameters", "sliders"); //$NON-NLS-1$ //$NON-NLS-2$
            add(form, parameters);
        }
        if (movesMachine(group)) {
            form.section("MeasurementForms.Common", "gear").collapsed(); //$NON-NLS-1$ //$NON-NLS-2$
            add(form, COMMON);
        }
        MachineDiagnosticsResults.Run run = lastRun(diagnostics, group);
        MachineDiagnosticsResults results = diagnostics.getLastResults();
        form.section("MeasurementForms.Last", "clock") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("MeasurementForms.Last.When", Ui.t2(run == null //$NON-NLS-1$
                        ? Translations.getString("MeasurementForms.Never") //$NON-NLS-1$
                        : WHEN.format(run.getWhen()) + (run.getDurationMillis() > 0
                                ? " \u00b7 " + duration(run.getDurationMillis()) : ""))); //$NON-NLS-1$ //$NON-NLS-2$
        if (run != null && results != null && !results.isCurrent(group)) {
            form.custom("MeasurementForms.Last.State", //$NON-NLS-1$
                    new Chip(Translations.getString("MeasurementForms.Stale"), Chip.Tone.Warn, Chip.Shape.Status)); //$NON-NLS-1$
        }
        return form.build();
    }

    /** Whether a group has parameters of its own to show beside the step that measures it. */
    public static boolean hasParameters(TestGroup group) {
        return PARAMETERS.containsKey(group);
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

    /** Says what is about to move before it moves, and where the stop is. */
    public static boolean confirmMotion(Component parent, Set<TestGroup> groups) {
        List<String> moving = new ArrayList<>();
        List<String> travelling = new ArrayList<>();
        for (TestGroup group : groups) {
            if (!movesMachine(group)) {
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
        String what = String.format(Translations.getString("MeasurementForms.Confirm.What"), //$NON-NLS-1$
                String.join(separator, moving));
        String more = (travelling.isEmpty() ? "" //$NON-NLS-1$
                : String.format(Translations.getString("MeasurementForms.Confirm.Travel"), //$NON-NLS-1$
                        String.join(separator, travelling)) + "\n") //$NON-NLS-1$
                + String.format(Translations.getString("MeasurementForms.Confirm.Stop"), //$NON-NLS-1$
                        org.openpnp.gui.shell.Hotkeys.describe(org.openpnp.gui.shell.Hotkeys.STOP_MACHINE));
        return Dialogs.ask(parent, Dialogs.Tone.Warn, "zap", //$NON-NLS-1$
                Translations.getString("MeasurementForms.Confirm.Title"), what, more, //$NON-NLS-1$
                new Dialogs.Choice(Translations.getString("MeasurementForms.Confirm.Start"), null, //$NON-NLS-1$
                        Ui.Variant.Primary).movesMachine()) == 0;
    }
}
