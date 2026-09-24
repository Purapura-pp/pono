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

package org.openpnp.machine.reference.axis.wizards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.openpnp.Translations;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.form.WeakForward;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.LengthConverter;
import org.openpnp.machine.reference.axis.ReferenceCamClockwiseAxis;
import org.openpnp.machine.reference.axis.ReferenceCamCounterClockwiseAxis;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis.BacklashCompensationMethod;
import org.openpnp.machine.reference.axis.ReferenceLinearTransformAxis;
import org.openpnp.machine.reference.axis.ReferenceMappedAxis;
import org.openpnp.machine.reference.axis.ReferenceVirtualAxis;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.CalibrationStep;
import org.openpnp.model.Length;
import org.openpnp.spi.Axis;
import org.openpnp.spi.Driver;
import org.openpnp.spi.base.AbstractAxis;
import org.openpnp.spi.base.AbstractControllerAxis;
import org.openpnp.spi.base.AbstractMachine;
import org.openpnp.util.SimpleGraph;
import org.openpnp.util.UiUtils;

/**
 * The axes' forms. Every axis has a name and a kind, the section the axes' base wizard was;
 * a controller axis has its driver, limits and kinematics, and a tab for its backlash; the
 * transformed axes have the axes they follow and how.
 */
public final class AxisForm {
    private AxisForm() {
    }

    /** A form's bean for an axis: its name and kind, the part every axis has. */
    public static class Bean extends AbstractModelObject {
        protected final AbstractAxis axis;

        protected Bean(AbstractAxis axis) {
            this.axis = axis;
        }

        public Axis.Type getType() {
            return axis.getType();
        }

        public void setType(Axis.Type type) {
            axis.setType(type);
        }

        public String getName() {
            return axis.getName();
        }

        public void setName(String name) {
            axis.setName(name);
        }
    }

    /** The section every axis starts with, for a bean that is a {@link Bean}. */
    public static Form.Builder basics(Form.Builder form) {
        return form.section("AxisForm.Basics", "move") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("type", "AbstractAxisConfigurationWizard.PropertiesPanel.TypeLabel.text", Axis.Type.class) //$NON-NLS-1$ //$NON-NLS-2$
                .text("name", "AbstractAxisConfigurationWizard.PropertiesPanel.NameLabel.text"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    // ==== controller axis ===========================================================================

    public static class ControllerBean extends Bean {
        private final ReferenceControllerAxis controller;

        public ControllerBean(ReferenceControllerAxis axis) {
            super(axis);
            this.controller = axis;
            // The backlash tests' graphs arrive while the tab is shown.
            WeakForward.listen(axis, this, (bean, e) -> bean.firePropertyChange(e.getPropertyName(), null, e.getNewValue()));
        }

        public Driver getDriver() {
            return controller.getDriver();
        }

        public void setDriver(Driver driver) {
            controller.setDriver(driver);
        }

        public String getLetter() {
            return controller.getLetter();
        }

        public void setLetter(String letter) {
            controller.setLetter(letter);
        }

        public boolean isInvertLinearRotational() {
            return controller.isInvertLinearRotational();
        }

        public void setInvertLinearRotational(boolean invert) {
            controller.setInvertLinearRotational(invert);
        }

        public Length getHomeCoordinate() {
            return controller.getHomeCoordinate();
        }

        public void setHomeCoordinate(Length coordinate) {
            controller.setHomeCoordinate(coordinate);
        }

        public double getResolution() {
            return controller.getResolution();
        }

        public void setResolution(double resolution) {
            controller.setResolution(resolution);
        }

        public String getPreMoveCommand() {
            return controller.getPreMoveCommand();
        }

        public void setPreMoveCommand(String command) {
            controller.setPreMoveCommand(command);
        }

        public boolean isLimitRotation() {
            return controller.isLimitRotation();
        }

        public void setLimitRotation(boolean limit) {
            controller.setLimitRotation(limit);
        }

        public boolean isWrapAroundRotation() {
            return controller.isWrapAroundRotation();
        }

        public void setWrapAroundRotation(boolean wrap) {
            controller.setWrapAroundRotation(wrap);
        }

        public boolean isSoftLimitLowEnabled() {
            return controller.isSoftLimitLowEnabled();
        }

        public void setSoftLimitLowEnabled(boolean enabled) {
            controller.setSoftLimitLowEnabled(enabled);
        }

        public Length getSoftLimitLow() {
            return controller.getSoftLimitLow();
        }

        public void setSoftLimitLow(Length limit) {
            controller.setSoftLimitLow(limit);
        }

        public boolean isSoftLimitHighEnabled() {
            return controller.isSoftLimitHighEnabled();
        }

        public void setSoftLimitHighEnabled(boolean enabled) {
            controller.setSoftLimitHighEnabled(enabled);
        }

        public Length getSoftLimitHigh() {
            return controller.getSoftLimitHigh();
        }

        public void setSoftLimitHigh(Length limit) {
            controller.setSoftLimitHigh(limit);
        }

        public boolean isSafeZoneLowEnabled() {
            return controller.isSafeZoneLowEnabled();
        }

        public void setSafeZoneLowEnabled(boolean enabled) {
            controller.setSafeZoneLowEnabled(enabled);
        }

        public Length getSafeZoneLow() {
            return controller.getSafeZoneLow();
        }

        public void setSafeZoneLow(Length limit) {
            controller.setSafeZoneLow(limit);
        }

        public boolean isSafeZoneHighEnabled() {
            return controller.isSafeZoneHighEnabled();
        }

        public void setSafeZoneHighEnabled(boolean enabled) {
            controller.setSafeZoneHighEnabled(enabled);
        }

        public Length getSafeZoneHigh() {
            return controller.getSafeZoneHigh();
        }

        public void setSafeZoneHigh(Length limit) {
            controller.setSafeZoneHigh(limit);
        }

        public Length getFeedratePerSecond() {
            return controller.getFeedratePerSecond();
        }

        public void setFeedratePerSecond(Length feedrate) {
            controller.setFeedratePerSecond(feedrate);
        }

        public Length getAccelerationPerSecond2() {
            return controller.getAccelerationPerSecond2();
        }

        public void setAccelerationPerSecond2(Length acceleration) {
            controller.setAccelerationPerSecond2(acceleration);
        }

        public Length getJerkPerSecond3() {
            return controller.getJerkPerSecond3();
        }

        public void setJerkPerSecond3(Length jerk) {
            controller.setJerkPerSecond3(jerk);
        }

        public BacklashCompensationMethod getBacklashCompensationMethod() {
            return controller.getBacklashCompensationMethod();
        }

        public void setBacklashCompensationMethod(BacklashCompensationMethod method) {
            controller.setBacklashCompensationMethod(method);
        }

        public Length getAcceptableTolerance() {
            return controller.getAcceptableTolerance();
        }

        public void setAcceptableTolerance(Length tolerance) {
            controller.setAcceptableTolerance(tolerance);
        }

        public Length getBacklashOffset() {
            return controller.getBacklashOffset();
        }

        public void setBacklashOffset(Length offset) {
            controller.setBacklashOffset(offset);
        }

        public Length getSneakUpOffset() {
            return controller.getSneakUpOffset();
        }

        public void setSneakUpOffset(Length offset) {
            controller.setSneakUpOffset(offset);
        }

        public double getBacklashSpeedFactor() {
            return controller.getBacklashSpeedFactor();
        }

        public void setBacklashSpeedFactor(double factor) {
            controller.setBacklashSpeedFactor(factor);
        }
    }

    public static FormWizard controller(ReferenceControllerAxis axis) {
        AbstractMachine machine = axis.getMachine();
        List<Driver> drivers = new ArrayList<>();
        drivers.add(null);
        if (machine != null) {
            drivers.addAll(machine.getDrivers());
        }
        String units = axis.getDriver() != null && axis.getDriver().getUnits() != null
                ? axis.getDriver().getUnits().getShortName() : null;
        Form.Builder form = basics(Form.of(new ControllerBean(axis)).named("AxisForm.Controller.Title")); //$NON-NLS-1$
        form.section("ReferenceControllerAxisConfigurationWizard.ControllerSettingsPanel.Border.title", "machine") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("driver", "ReferenceControllerAxisConfigurationWizard.ControllerSettingsPanel.DriverLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        drivers, null)
                .text("letter", "ReferenceControllerAxisConfigurationWizard.ControllerSettingsPanel.AxisLetterLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .width(80)
                .hint("ReferenceControllerAxisConfigurationWizard.ControllerSettingsPanel.AxisLetterLabel.toolTipText") //$NON-NLS-1$
                .toggle("invertLinearRotational", "AxisForm.Invert", "AxisForm.Invert.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("AxisForm.Invert.Hint") //$NON-NLS-1$
                .length("homeCoordinate", "ReferenceControllerAxisConfigurationWizard.ControllerSettingsPanel.HomeCoordinateLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .width(150)
                .decimal("resolution", "ReferenceControllerAxisConfigurationWizard.ControllerSettingsPanel.Resolution.text") //$NON-NLS-1$ //$NON-NLS-2$
                .format("%.6f").width(150); //$NON-NLS-1$
        if (units != null) {
            form.unit(units);
        }
        form.liveHint(f -> stepsPerUnit(f.value("resolution"), units)) //$NON-NLS-1$
                .textArea("preMoveCommand", "ReferenceControllerAxisConfigurationWizard.ControllerSettingsPanel.PreMoveCommandLabel.text", 2) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("driver", d -> d instanceof Driver && ((Driver) d).isSupportingPreMove()) //$NON-NLS-1$
                .hint("AxisForm.PreMove.Hint") //$NON-NLS-1$
                .section("AxisForm.Rotation", "rcw") //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("limitRotation", "ReferenceControllerAxisConfigurationWizard.ControllerSettingsPanel.LimitToRangeLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        "AxisForm.LimitRotation.Note") //$NON-NLS-1$
                .visibleWhen("type", t -> t == Axis.Type.Rotation) //$NON-NLS-1$
                .toggle("wrapAroundRotation", "ReferenceControllerAxisConfigurationWizard.ControllerSettingsPanel.WrapAroundLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        "AxisForm.WrapAround.Note") //$NON-NLS-1$
                .visibleWhen("type", t -> t == Axis.Type.Rotation) //$NON-NLS-1$
                .hint("AxisForm.WrapAround.Hint") //$NON-NLS-1$
                .section("AxisForm.SoftLimits", "ruler").measuredBy(CalibrationStep.SoftLimits, axis); //$NON-NLS-1$ //$NON-NLS-2$
        limit(form, axis, "softLimitLow", "ReferenceControllerAxisConfigurationWizard.KinematicsPanel.SoftLimitLowLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                AxisForm::softLimitsShown);
        limit(form, axis, "softLimitHigh", "ReferenceControllerAxisConfigurationWizard.KinematicsPanel.SoftLimitHighLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                AxisForm::softLimitsShown);
        form.section("AxisForm.SafeZone", "up"); //$NON-NLS-1$ //$NON-NLS-2$
        limit(form, axis, "safeZoneLow", "ReferenceControllerAxisConfigurationWizard.KinematicsPanel.SafeZoneLowLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                f -> f.value("type") != Axis.Type.Rotation); //$NON-NLS-1$
        limit(form, axis, "safeZoneHigh", "ReferenceControllerAxisConfigurationWizard.KinematicsPanel.SafeZoneHighLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                f -> f.value("type") != Axis.Type.Rotation); //$NON-NLS-1$
        String system = machine != null && machine.getConfiguration() != null
                ? machine.getConfiguration().getSystemUnits().getShortName() : "mm"; //$NON-NLS-1$
        return form
                .section("ReferenceControllerAxisConfigurationWizard.KinematicsPanel.Border.title", "sliders") //$NON-NLS-1$ //$NON-NLS-2$
                .length("feedratePerSecond", "AxisForm.Feedrate").unit(system + "/s").width(150) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .liveHint(f -> perMinute(f.value("feedratePerSecond"))) //$NON-NLS-1$
                .length("accelerationPerSecond2", "AxisForm.Acceleration").unit(system + "/s\u00b2").width(150) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .length("jerkPerSecond3", "AxisForm.Jerk").unit(system + "/s\u00b3").width(150) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("AxisForm.Kinematics.Hint") //$NON-NLS-1$
                .build();
    }

    /**
     * A limit: its switch, then its coordinate while it is on, with the buttons that read the
     * axis's position into it and that move the axis there. For a form of a {@link ControllerBean}.
     */
    public static void limit(Form.Builder form, ReferenceControllerAxis axis, String property, String label,
            java.util.function.Predicate<FormWizard> shown) {
        String enabled = property + "Enabled"; //$NON-NLS-1$
        form.toggle(enabled, label, "AxisForm.Limit.Enabled") //$NON-NLS-1$
                .visibleIf(shown)
                .length(property, "AxisForm.Limit.At").width(150) //$NON-NLS-1$
                .visibleIf(f -> Boolean.TRUE.equals(f.value(enabled)) && shown.test(f))
                .iconButton("capture", "AxisForm.Limit.Capture", f -> capture(f, axis, property)) //$NON-NLS-1$ //$NON-NLS-2$
                .iconButton("target", "AxisForm.Limit.Move", f -> move(f, axis, property)) //$NON-NLS-1$ //$NON-NLS-2$
                .movesMachine();
    }

    /** A linear axis has soft limits, and a rotation axis while it is limited to a range. */
    public static boolean softLimitsShown(FormWizard form) {
        return form.value("type") != Axis.Type.Rotation || Boolean.TRUE.equals(form.value("limitRotation")); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** The axis's position as its controller has it, into the field; it reads, it does not move. */
    private static void capture(FormWizard form, ReferenceControllerAxis axis, String property) {
        UiUtils.submitUiMachineTask(() -> {
            Length position = axis.getDriverLengthCoordinate();
            SwingUtilities.invokeLater(() -> form.setValue(property, new LengthConverter().convertForward(position)));
        });
    }

    /** The axis to the coordinate on screen. */
    private static void move(FormWizard form, ReferenceControllerAxis axis, String property) {
        UiUtils.messageBoxOnException(() -> {
            Length target = new LengthConverter().convertReverse(String.valueOf(form.value(property)));
            UiUtils.submitUiMachineTask(() -> axis.moveAxis(target));
        });
    }

    /** Steps per unit, the reciprocal of the resolution, as the controller's settings give them. */
    static String stepsPerUnit(Object resolutionText, String units) {
        double resolution = Double.parseDouble(String.valueOf(resolutionText).trim());
        if (resolution <= 0) {
            return null;
        }
        return String.format(Locale.US, Translations.getString("AxisForm.StepsPerUnit"), //$NON-NLS-1$
                trim(1 / resolution), units == null ? Translations.getString("AxisForm.DriverUnit") : units); //$NON-NLS-1$
    }

    /** The feed rate per minute, as a controller's configuration usually states it. */
    public static String perMinute(Object feedrateText) {
        Length perSecond = new LengthConverter().convertReverse(String.valueOf(feedrateText));
        return String.format(Locale.US, Translations.getString("AxisForm.PerMinute"), //$NON-NLS-1$
                trim(perSecond.getValue() * 60), perSecond.getUnits().getShortName());
    }

    private static String trim(double value) {
        String text = String.format(Locale.US, "%.4f", value); //$NON-NLS-1$
        return text.contains(".") ? text.replaceAll("0+$", "").replaceAll("\\.$", "") : text; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
    }

    // ==== backlash ==================================================================================

    public static FormWizard backlash(ReferenceControllerAxis axis) {
        ControllerBean bean = new ControllerBean(axis);
        boolean planar = axis.getType() == Axis.Type.X || axis.getType() == Axis.Type.Y;
        CalibrationStep step = planar ? CalibrationStep.XyBacklash
                : axis.getType() == Axis.Type.Z ? CalibrationStep.ZBacklash : CalibrationStep.RotationBacklash;
        FormWizard[] form = new FormWizard[1];
        Form.Builder b = Form.of(bean).named("ReferenceControllerAxis.BacklashCompensationConfigurationWizard.title") //$NON-NLS-1$
                .section("AxisForm.Backlash", "rcw").measuredBy(step, axis) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("backlashCompensationMethod", //$NON-NLS-1$
                        "BacklashCompensationConfigurationWizard.BacklashDiagnosticsPanel.CompensationMethodLabel.text", //$NON-NLS-1$
                        BacklashCompensationMethod.class)
                .hint("AxisForm.Backlash.Method.Hint") //$NON-NLS-1$
                .length("backlashOffset", "AxisForm.Backlash.Offset").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("backlashCompensationMethod", m -> m != BacklashCompensationMethod.None) //$NON-NLS-1$
                .length("sneakUpOffset", "AxisForm.Backlash.SneakUp").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("backlashCompensationMethod", m -> m == BacklashCompensationMethod.DirectionalSneakUp) //$NON-NLS-1$
                .hint("AxisForm.Backlash.SneakUp.Hint") //$NON-NLS-1$
                .decimal("backlashSpeedFactor", "AxisForm.Backlash.SpeedFactor").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("backlashCompensationMethod", m -> m instanceof BacklashCompensationMethod //$NON-NLS-1$
                        && ((BacklashCompensationMethod) m).isSpeedControlledMethod())
                .hint("AxisForm.Backlash.SpeedFactor.Hint"); //$NON-NLS-1$
        if (planar) {
            b.length("acceptableTolerance", "AxisForm.Backlash.Tolerance").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                    .hint("AxisForm.Backlash.Tolerance.Hint") //$NON-NLS-1$
                    .action("BacklashCompensationConfigurationWizard.Action.Calibrate", "target", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                        form[0].apply();
                        org.openpnp.gui.MainFrame frame = org.openpnp.gui.MainFrame.get();
                        if (frame != null) {
                            frame.showCalibrationStep(step, axis);
                        }
                    })
                    .section("AxisForm.Backlash.Tests", "activity"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (planar && axis.getStepTestGraph() == null && axis.getBacklashDistanceTestGraph() == null
                && axis.getBacklashSpeedTestGraph() == null) {
            // Three empty graphs said nothing a sentence does not.
            b.custom("", org.openpnp.gui.shell.Forms.paragraph(Translations.getString("AxisForm.Backlash.NotYet"))); //$NON-NLS-1$ //$NON-NLS-2$
        }
        else if (planar) {
            b.custom("AxisForm.Backlash.StepTest", graph(bean, "stepTestGraph", axis.getStepTestGraph(), 100)) //$NON-NLS-1$ //$NON-NLS-2$
                    .hint("AxisForm.Backlash.StepTest.Hint") //$NON-NLS-1$
                    .custom("AxisForm.Backlash.DistanceTest", //$NON-NLS-1$
                            graph(bean, "backlashDistanceTestGraph", axis.getBacklashDistanceTestGraph(), 160)) //$NON-NLS-1$
                    .hint("AxisForm.Backlash.DistanceTest.Hint") //$NON-NLS-1$
                    .custom("AxisForm.Backlash.SpeedTest", //$NON-NLS-1$
                            graph(bean, "backlashSpeedTestGraph", axis.getBacklashSpeedTestGraph(), 100)) //$NON-NLS-1$
                    .hint("AxisForm.Backlash.SpeedTest.Hint"); //$NON-NLS-1$
        }
        form[0] = b.build();
        return form[0];
    }

    /** One of the backlash tests' graphs, drawn again when the axis has a new one. */
    private static org.openpnp.gui.components.SimpleGraphView graph(ControllerBean bean, String property,
            SimpleGraph graph, int height) {
        org.openpnp.gui.components.SimpleGraphView view = new org.openpnp.gui.components.SimpleGraphView();
        view.setFont(org.openpnp.gui.shell.Ui.font(org.openpnp.gui.shell.Tokens.FS_AUX));
        view.setGraph(graph);
        view.setPreferredSize(new java.awt.Dimension(200, height));
        view.setMinimumSize(new java.awt.Dimension(60, height));
        bean.addPropertyChangeListener(property, e -> view.setGraph((SimpleGraph) e.getNewValue()));
        return view;
    }

    // ==== virtual axis ==============================================================================

    public static class VirtualBean extends Bean {
        private final ReferenceVirtualAxis virtual;

        VirtualBean(ReferenceVirtualAxis axis) {
            super(axis);
            this.virtual = axis;
        }

        public Length getHomeCoordinate() {
            return virtual.getHomeCoordinate();
        }

        public void setHomeCoordinate(Length coordinate) {
            virtual.setHomeCoordinate(coordinate);
        }
    }

    public static FormWizard virtual(ReferenceVirtualAxis axis) {
        return basics(Form.of(new VirtualBean(axis)).named("AxisForm.Virtual.Title")) //$NON-NLS-1$
                .section("ReferenceVirtualAxisConfigurationWizard.TransformationPanel.Border.title", "layers") //$NON-NLS-1$ //$NON-NLS-2$
                .length("homeCoordinate", "AxisForm.Virtual.Home").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("AxisForm.Virtual.Home.Hint") //$NON-NLS-1$
                .build();
    }

    // ==== mapped axis ===============================================================================

    public static class MappedBean extends Bean {
        private final ReferenceMappedAxis mapped;

        MappedBean(ReferenceMappedAxis axis) {
            super(axis);
            this.mapped = axis;
        }

        public AbstractAxis getInputAxis() {
            return mapped.getInputAxis();
        }

        public void setInputAxis(AbstractAxis input) {
            mapped.setInputAxis(input);
        }

        public Length getMapInput0() {
            return mapped.getMapInput0();
        }

        public void setMapInput0(Length input) {
            mapped.setMapInput0(input);
        }

        public Length getMapOutput0() {
            return mapped.getMapOutput0();
        }

        public void setMapOutput0(Length output) {
            mapped.setMapOutput0(output);
        }

        public Length getMapInput1() {
            return mapped.getMapInput1();
        }

        public void setMapInput1(Length input) {
            mapped.setMapInput1(input);
        }

        public Length getMapOutput1() {
            return mapped.getMapOutput1();
        }

        public void setMapOutput1(Length output) {
            mapped.setMapOutput1(output);
        }
    }

    public static FormWizard mapped(ReferenceMappedAxis axis) {
        return basics(Form.of(new MappedBean(axis)).named("AxisForm.Mapped.Title")) //$NON-NLS-1$
                .section("ReferenceMappedAxisConfigurationWizard.panelTransformation.Border.title", "layers") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("inputAxis", "ReferenceMappedAxisConfigurationWizard.lblInputAxis.text", //$NON-NLS-1$ //$NON-NLS-2$
                        axes(axis.getMachine(), AbstractControllerAxis.class, null, axis), AxisForm::kind)
                .length("mapInput0", "AxisForm.Mapped.InputA").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .length("mapOutput0", "AxisForm.Mapped.OutputA").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .length("mapInput1", "AxisForm.Mapped.InputB").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .length("mapOutput1", "AxisForm.Mapped.OutputB").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("AxisForm.Mapped.Hint") //$NON-NLS-1$
                .build();
    }

    // ==== linear transform axis ======================================================================

    public static class LinearBean extends Bean {
        private final ReferenceLinearTransformAxis linear;

        LinearBean(ReferenceLinearTransformAxis axis) {
            super(axis);
            this.linear = axis;
        }

        public AbstractAxis getInputAxisX() {
            return linear.getInputAxisX();
        }

        public void setInputAxisX(AbstractAxis input) {
            linear.setInputAxisX(input);
        }

        public double getFactorX() {
            return linear.getFactorX();
        }

        public void setFactorX(double factor) {
            linear.setFactorX(factor);
        }

        public AbstractAxis getInputAxisY() {
            return linear.getInputAxisY();
        }

        public void setInputAxisY(AbstractAxis input) {
            linear.setInputAxisY(input);
        }

        public double getFactorY() {
            return linear.getFactorY();
        }

        public void setFactorY(double factor) {
            linear.setFactorY(factor);
        }

        public AbstractAxis getInputAxisZ() {
            return linear.getInputAxisZ();
        }

        public void setInputAxisZ(AbstractAxis input) {
            linear.setInputAxisZ(input);
        }

        public double getFactorZ() {
            return linear.getFactorZ();
        }

        public void setFactorZ(double factor) {
            linear.setFactorZ(factor);
        }

        public AbstractAxis getInputAxisRotation() {
            return linear.getInputAxisRotation();
        }

        public void setInputAxisRotation(AbstractAxis input) {
            linear.setInputAxisRotation(input);
        }

        public double getFactorRotation() {
            return linear.getFactorRotation();
        }

        public void setFactorRotation(double factor) {
            linear.setFactorRotation(factor);
        }

        public Length getOffset() {
            return linear.getOffset();
        }

        public void setOffset(Length offset) {
            linear.setOffset(offset);
        }

        public boolean isCompensation() {
            return linear.isCompensation();
        }

        public void setCompensation(boolean compensation) {
            linear.setCompensation(compensation);
        }
    }

    public static FormWizard linearTransform(ReferenceLinearTransformAxis axis) {
        AbstractMachine machine = axis.getMachine();
        Class<? extends Axis> inputs = org.openpnp.spi.LinearInputAxis.class;
        return basics(Form.of(new LinearBean(axis)).named("AxisForm.Linear.Title")) //$NON-NLS-1$
                .section("ReferenceLinearTransformAxisConfigurationWizard.panelTransformation.Border.title", "layers") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", org.openpnp.gui.shell.Forms.paragraph(Translations.getString("AxisForm.Linear.Note"))) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("inputAxisX", "AxisForm.Linear.InputX", axes(machine, inputs, Axis.Type.X, axis), null) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("factorX", "AxisForm.Linear.FactorX").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("inputAxisY", "AxisForm.Linear.InputY", axes(machine, inputs, Axis.Type.Y, axis), null) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("factorY", "AxisForm.Linear.FactorY").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("inputAxisZ", "AxisForm.Linear.InputZ", axes(machine, inputs, Axis.Type.Z, axis), null) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("factorZ", "AxisForm.Linear.FactorZ").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("inputAxisRotation", "AxisForm.Linear.InputRotation", //$NON-NLS-1$ //$NON-NLS-2$
                        axes(machine, inputs, Axis.Type.Rotation, axis), null)
                .decimal("factorRotation", "AxisForm.Linear.FactorRotation").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .length("offset", "ReferenceLinearTransformAxisConfigurationWizard.lblOffset.text").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("compensation", "AxisForm.Linear.Compensation", "AxisForm.Linear.Compensation.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("AxisForm.Linear.Compensation.Hint") //$NON-NLS-1$
                .build();
    }

    // ==== cam axes ==================================================================================

    public static class CamBean extends Bean {
        private final AbstractAxis cam;

        CamBean(AbstractAxis axis) {
            super(axis);
            this.cam = axis;
        }

        public AbstractAxis getInputAxis() {
            return ((org.openpnp.spi.base.AbstractSingleTransformedAxis) cam).getInputAxis();
        }

        public void setInputAxis(AbstractAxis input) {
            ((org.openpnp.spi.base.AbstractSingleTransformedAxis) cam).setInputAxis(input);
        }

        public Length getCamRadius() {
            return counterClockwise().getCamRadius();
        }

        public void setCamRadius(Length radius) {
            counterClockwise().setCamRadius(radius);
        }

        public double getCamArmsAngle() {
            return counterClockwise().getCamArmsAngle();
        }

        public void setCamArmsAngle(double angle) {
            counterClockwise().setCamArmsAngle(angle);
        }

        public Length getCamWheelRadius() {
            return counterClockwise().getCamWheelRadius();
        }

        public void setCamWheelRadius(Length radius) {
            counterClockwise().setCamWheelRadius(radius);
        }

        public Length getCamWheelGap() {
            return counterClockwise().getCamWheelGap();
        }

        public void setCamWheelGap(Length gap) {
            counterClockwise().setCamWheelGap(gap);
        }

        private ReferenceCamCounterClockwiseAxis counterClockwise() {
            return (ReferenceCamCounterClockwiseAxis) cam;
        }
    }

    public static FormWizard camCounterClockwise(ReferenceCamCounterClockwiseAxis axis) {
        boolean deprecated = axis.getCamWheelRadius().getValue() != 0 || axis.getCamWheelGap().getValue() != 0;
        Form.Builder b = basics(Form.of(new CamBean(axis)).named("AxisForm.CamCcw.Title")) //$NON-NLS-1$
                .section("ReferenceCamCounterClockwiseAxisConfigurationWizard.panelTransformation.Border.title", "rcw") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("inputAxis", "ReferenceCamCounterClockwiseAxisConfigurationWizard.lblInputAxis.text", //$NON-NLS-1$ //$NON-NLS-2$
                        axes(axis.getMachine(), AbstractControllerAxis.class, null, axis), AxisForm::kind)
                .length("camRadius", "ReferenceCamCounterClockwiseAxisConfigurationWizard.lblCamRadius.text").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .angle("camArmsAngle", "ReferenceCamCounterClockwiseAxisConfigurationWizard.lblArmsAngle.text").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("ReferenceCamCounterClockwiseAxisConfigurationWizard.lblArmsAngle.toolTipText"); //$NON-NLS-1$
        if (deprecated) {
            b.length("camWheelRadius", "ReferenceCamCounterClockwiseAxisConfigurationWizard.lblCamWheelRadius.text").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                    .length("camWheelGap", "ReferenceCamCounterClockwiseAxisConfigurationWizard.lblCamWheelGap.text").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                    .hint("AxisForm.CamCcw.Deprecated"); //$NON-NLS-1$
        }
        return b.custom("", new JLabel(Icons.camAxisTransform)) //$NON-NLS-1$
                .build();
    }

    public static FormWizard camClockwise(ReferenceCamClockwiseAxis axis) {
        return basics(Form.of(new CamBean(axis)).named("AxisForm.CamCw.Title")) //$NON-NLS-1$
                .section("ReferenceCamClockwiseAxisConfigurationWizard.panelTransformation.Border.title", "rcw") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("inputAxis", "ReferenceCamClockwiseAxisConfigurationWizard.lblInputAxis.text", //$NON-NLS-1$ //$NON-NLS-2$
                        axes(axis.getMachine(), ReferenceCamCounterClockwiseAxis.class, null, axis), AxisForm::kind)
                .hint("AxisForm.CamCw.Hint") //$NON-NLS-1$
                .build();
    }

    // ==== shared ====================================================================================

    /** None, then the machine's axes of a class and, if given, a kind, by name; the axis itself left out. */
    static List<AbstractAxis> axes(AbstractMachine machine, Class<? extends Axis> kind, Axis.Type type, Axis self) {
        List<AbstractAxis> axes = new ArrayList<>();
        if (machine != null) {
            for (Axis axis : machine.getAxes()) {
                if (axis != self && axis instanceof AbstractAxis && kind.isInstance(axis)
                        && (type == null || axis.getType() == type)) {
                    axes.add((AbstractAxis) axis);
                }
            }
        }
        axes.sort(Comparator.comparing(Axis::getName, Comparator.nullsFirst(Comparator.naturalOrder())));
        axes.add(0, null);
        return axes;
    }

    /** An axis's kind, grey after its name in a choice of axes of every kind. */
    private static String kind(AbstractAxis axis) {
        return axis == null ? null : org.openpnp.gui.support.DisplayNames.of(axis.getType());
    }
}
