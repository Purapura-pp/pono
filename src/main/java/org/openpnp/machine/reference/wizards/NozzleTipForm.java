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

package org.openpnp.machine.reference.wizards;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.components.CameraView;
import org.openpnp.gui.components.HsvIndicator;
import org.openpnp.gui.components.SimpleGraphView;
import org.openpnp.gui.components.TemplateImageControl;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.form.WeakForward;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Tokens;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.PipelineStages;
import org.openpnp.machine.reference.ContactProbeNozzle;
import org.openpnp.machine.reference.camera.ReferenceCamera;
import org.openpnp.machine.reference.ReferenceNozzle;
import org.openpnp.machine.reference.ReferenceNozzleTip;
import org.openpnp.machine.reference.ReferenceNozzleTip.VacuumMeasurementMethod;
import org.openpnp.machine.reference.ReferenceNozzleTip.VisionCalibration;
import org.openpnp.machine.reference.ReferenceNozzleTip.VisionCalibrationTrigger;
import org.openpnp.machine.reference.ReferenceNozzleTip.ZCalibrationTrigger;
import org.openpnp.machine.reference.ReferenceNozzleTipCalibration;
import org.openpnp.machine.reference.ReferenceNozzleTipCalibration.BackgroundCalibrationMethod;
import org.openpnp.machine.reference.ReferenceNozzleTipCalibration.ModelBasedRunoutCompensation;
import org.openpnp.machine.reference.ReferenceNozzleTipCalibration.RecalibrationTrigger;
import org.openpnp.machine.reference.ReferenceNozzleTipCalibration.RunoutCompensation;
import org.openpnp.machine.reference.ReferenceNozzleTipCalibration.RunoutCompensationAlgorithm;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.CalibrationStep;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Camera;
import org.openpnp.spi.HeadMountable;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.NozzleTip;
import org.openpnp.util.MovableUtils;
import org.openpnp.util.UiUtils;
import org.openpnp.util.VisionUtils;
import org.openpnp.vision.pipeline.CvPipeline;
import org.openpnp.vision.pipeline.ui.CvPipelineEditor;
import org.openpnp.vision.pipeline.ui.CvPipelineEditorDialog;
import org.openpnp.util.SimpleGraph;
import org.openpnp.vision.TemplateImage;

/**
 * A nozzle tip's sheets, as mockup 23 draws them: its settings, its runout calibration, the
 * background calibration measured with it, part detection by vacuum, and its tip changer.
 */
public final class NozzleTipForm {
    private NozzleTipForm() {
    }

    /** When part on checks are made, for the checklist of the part on section. */
    public enum PartOnCheck {
        AfterPick, Alignment, BeforePlace
    }

    /** When part off checks are made. */
    public enum PartOffCheck {
        AfterPlace, BeforePick
    }

    /** What the nozzle tip is to the cloning of tip changer settings: three buttons of a group. */
    public enum TemplateRole {
        Template, Clone, Locked
    }

    public static class Bean extends AbstractModelObject {
        private final ReferenceNozzleTip tip;
        private final ReferenceNozzleTipCalibration calibration;
        private boolean cloneLocations = true;
        private boolean cloneZCalibration = true;
        private boolean cloneVisionCalibration = true;

        public Bean(ReferenceNozzleTip tip) {
            this.tip = tip;
            this.calibration = tip.getCalibration();
            // Readings, graphs and levels a pick establishes change while the form is shown.
            WeakForward.listen(tip, this, (bean, e) -> bean.firePropertyChange(e.getPropertyName(), null, e.getNewValue()));
        }

        // ---- settings -------------------------------------------------------------------------

        public String getName() {
            return tip.getName();
        }

        public void setName(String name) {
            tip.setName(name);
        }

        public int getPickDwellMilliseconds() {
            return tip.getPickDwellMilliseconds();
        }

        public void setPickDwellMilliseconds(int milliseconds) {
            tip.setPickDwellMilliseconds(milliseconds);
        }

        public int getPlaceDwellMilliseconds() {
            return tip.getPlaceDwellMilliseconds();
        }

        public void setPlaceDwellMilliseconds(int milliseconds) {
            tip.setPlaceDwellMilliseconds(milliseconds);
        }

        public double getPlaceBlowOffLevel() {
            return tip.getPlaceBlowOffLevel();
        }

        public void setPlaceBlowOffLevel(double level) {
            tip.setPlaceBlowOffLevel(level);
        }

        public Length getMinPartDiameter() {
            return tip.getMinPartDiameter();
        }

        public void setMinPartDiameter(Length diameter) {
            tip.setMinPartDiameter(diameter);
        }

        public Length getMaxPartDiameter() {
            return tip.getMaxPartDiameter();
        }

        public void setMaxPartDiameter(Length diameter) {
            tip.setMaxPartDiameter(diameter);
        }

        public Length getMaxPartHeight() {
            return tip.getMaxPartHeight();
        }

        public void setMaxPartHeight(Length height) {
            tip.setMaxPartHeight(height);
        }

        public Length getMaxPickTolerance() {
            return tip.getMaxPickTolerance();
        }

        public void setMaxPickTolerance(Length tolerance) {
            tip.setMaxPickTolerance(tolerance);
        }

        public boolean isPushAndDragAllowed() {
            return tip.isPushAndDragAllowed();
        }

        public void setPushAndDragAllowed(boolean allowed) {
            tip.setPushAndDragAllowed(allowed);
        }

        public Length getDiameterLow() {
            return tip.getDiameterLow();
        }

        public void setDiameterLow(Length diameter) {
            tip.setDiameterLow(diameter);
        }

        // ---- runout calibration ---------------------------------------------------------------

        public boolean isCalibrationEnabled() {
            return calibration.isEnabled();
        }

        public void setCalibrationEnabled(boolean enabled) {
            calibration.setEnabled(enabled);
        }

        public RecalibrationTrigger getRecalibrationTrigger() {
            return calibration.getRecalibrationTrigger();
        }

        public void setRecalibrationTrigger(RecalibrationTrigger trigger) {
            calibration.setRecalibrationTrigger(trigger);
        }

        public boolean isFailHoming() {
            return calibration.isFailHoming();
        }

        public void setFailHoming(boolean fail) {
            calibration.setFailHoming(fail);
        }

        public RunoutCompensationAlgorithm getRunoutCompensationAlgorithm() {
            return calibration.getRunoutCompensationAlgorithm();
        }

        public void setRunoutCompensationAlgorithm(RunoutCompensationAlgorithm algorithm) {
            calibration.setRunoutCompensationAlgorithm(algorithm);
        }

        public int getAngleSubdivisions() {
            return calibration.getAngleSubdivisions();
        }

        public void setAngleSubdivisions(int subdivisions) {
            calibration.setAngleSubdivisions(subdivisions);
        }

        public int getAllowMisdetections() {
            return calibration.getAllowMisdetections();
        }

        public void setAllowMisdetections(int misdetections) {
            calibration.setAllowMisdetections(misdetections);
        }

        public Length getOffsetThresholdLength() {
            return calibration.getOffsetThresholdLength();
        }

        public void setOffsetThresholdLength(Length threshold) {
            calibration.setOffsetThresholdLength(threshold);
        }

        public Length getCalibrationTipDiameter() {
            return calibration.getCalibrationTipDiameter();
        }

        public void setCalibrationTipDiameter(Length diameter) {
            calibration.setCalibrationTipDiameter(diameter);
        }

        public Length getCalibrationZOffset() {
            return calibration.getCalibrationZOffset();
        }

        public void setCalibrationZOffset(Length offset) {
            calibration.setCalibrationZOffset(offset);
        }

        // ---- background calibration ------------------------------------------------------------

        public BackgroundCalibrationMethod getBackgroundCalibrationMethod() {
            return calibration.getBackgroundCalibrationMethod();
        }

        public void setBackgroundCalibrationMethod(BackgroundCalibrationMethod method) {
            calibration.setBackgroundCalibrationMethod(method);
        }

        public Length getMinimumDetailSize() {
            return calibration.getMinimumDetailSize();
        }

        public void setMinimumDetailSize(Length size) {
            calibration.setMinimumDetailSize(size);
        }

        public int getBackgroundTolHue() {
            return calibration.getBackgroundTolHue();
        }

        public void setBackgroundTolHue(int tolerance) {
            calibration.setBackgroundTolHue(tolerance);
        }

        public int getBackgroundTolSaturation() {
            return calibration.getBackgroundTolSaturation();
        }

        public void setBackgroundTolSaturation(int tolerance) {
            calibration.setBackgroundTolSaturation(tolerance);
        }

        public int getBackgroundTolValue() {
            return calibration.getBackgroundTolValue();
        }

        public void setBackgroundTolValue(int tolerance) {
            calibration.setBackgroundTolValue(tolerance);
        }

        // ---- part detection -------------------------------------------------------------------

        public VacuumMeasurementMethod getMethodPartOn() {
            return tip.getMethodPartOn();
        }

        public void setMethodPartOn(VacuumMeasurementMethod method) {
            tip.setMethodPartOn(method);
        }

        public boolean isEstablishPartOnLevel() {
            return tip.isEstablishPartOnLevel();
        }

        public void setEstablishPartOnLevel(boolean establish) {
            tip.setEstablishPartOnLevel(establish);
        }

        public Set<PartOnCheck> getPartOnChecks() {
            Set<PartOnCheck> checks = new LinkedHashSet<>();
            if (tip.isPartOnCheckAfterPick()) {
                checks.add(PartOnCheck.AfterPick);
            }
            if (tip.isPartOnCheckAlign()) {
                checks.add(PartOnCheck.Alignment);
            }
            if (tip.isPartOnCheckBeforePlace()) {
                checks.add(PartOnCheck.BeforePlace);
            }
            return checks;
        }

        public void setPartOnChecks(Set<PartOnCheck> checks) {
            tip.setPartOnCheckAfterPick(checks.contains(PartOnCheck.AfterPick));
            tip.setPartOnCheckAlign(checks.contains(PartOnCheck.Alignment));
            tip.setPartOnCheckBeforePlace(checks.contains(PartOnCheck.BeforePlace));
        }

        public double getVacuumLevelPartOnLow() {
            return tip.getVacuumLevelPartOnLow();
        }

        public void setVacuumLevelPartOnLow(double level) {
            tip.setVacuumLevelPartOnLow(level);
        }

        public double getVacuumLevelPartOnHigh() {
            return tip.getVacuumLevelPartOnHigh();
        }

        public void setVacuumLevelPartOnHigh(double level) {
            tip.setVacuumLevelPartOnHigh(level);
        }

        public Double getVacuumLevelPartOnReading() {
            return tip.getVacuumLevelPartOnReading();
        }

        public double getVacuumDifferencePartOnLow() {
            return tip.getVacuumDifferencePartOnLow();
        }

        public void setVacuumDifferencePartOnLow(double level) {
            tip.setVacuumDifferencePartOnLow(level);
        }

        public double getVacuumDifferencePartOnHigh() {
            return tip.getVacuumDifferencePartOnHigh();
        }

        public void setVacuumDifferencePartOnHigh(double level) {
            tip.setVacuumDifferencePartOnHigh(level);
        }

        public Double getVacuumDifferencePartOnReading() {
            return tip.getVacuumDifferencePartOnReading();
        }

        public VacuumMeasurementMethod getMethodPartOff() {
            return tip.getMethodPartOff();
        }

        public void setMethodPartOff(VacuumMeasurementMethod method) {
            tip.setMethodPartOff(method);
        }

        public boolean isEstablishPartOffLevel() {
            return tip.isEstablishPartOffLevel();
        }

        public void setEstablishPartOffLevel(boolean establish) {
            tip.setEstablishPartOffLevel(establish);
        }

        public Set<PartOffCheck> getPartOffChecks() {
            Set<PartOffCheck> checks = new LinkedHashSet<>();
            if (tip.isPartOffCheckAfterPlace()) {
                checks.add(PartOffCheck.AfterPlace);
            }
            if (tip.isPartOffCheckBeforePick()) {
                checks.add(PartOffCheck.BeforePick);
            }
            return checks;
        }

        public void setPartOffChecks(Set<PartOffCheck> checks) {
            tip.setPartOffCheckAfterPlace(checks.contains(PartOffCheck.AfterPlace));
            tip.setPartOffCheckBeforePick(checks.contains(PartOffCheck.BeforePick));
        }

        public double getVacuumLevelPartOffLow() {
            return tip.getVacuumLevelPartOffLow();
        }

        public void setVacuumLevelPartOffLow(double level) {
            tip.setVacuumLevelPartOffLow(level);
        }

        public double getVacuumLevelPartOffHigh() {
            return tip.getVacuumLevelPartOffHigh();
        }

        public void setVacuumLevelPartOffHigh(double level) {
            tip.setVacuumLevelPartOffHigh(level);
        }

        public Double getVacuumLevelPartOffReading() {
            return tip.getVacuumLevelPartOffReading();
        }

        public int getPartOffProbingMilliseconds() {
            return tip.getPartOffProbingMilliseconds();
        }

        public void setPartOffProbingMilliseconds(int milliseconds) {
            tip.setPartOffProbingMilliseconds(milliseconds);
        }

        public int getPartOffDwellMilliseconds() {
            return tip.getPartOffDwellMilliseconds();
        }

        public void setPartOffDwellMilliseconds(int milliseconds) {
            tip.setPartOffDwellMilliseconds(milliseconds);
        }

        public double getVacuumDifferencePartOffLow() {
            return tip.getVacuumDifferencePartOffLow();
        }

        public void setVacuumDifferencePartOffLow(double level) {
            tip.setVacuumDifferencePartOffLow(level);
        }

        public double getVacuumDifferencePartOffHigh() {
            return tip.getVacuumDifferencePartOffHigh();
        }

        public void setVacuumDifferencePartOffHigh(double level) {
            tip.setVacuumDifferencePartOffHigh(level);
        }

        public Double getVacuumDifferencePartOffReading() {
            return tip.getVacuumDifferencePartOffReading();
        }

        // ---- tip changer ----------------------------------------------------------------------

        public Location getChangerStartLocation() {
            return tip.getChangerStartLocation();
        }

        public void setChangerStartLocation(Location location) {
            tip.setChangerStartLocation(location);
        }

        public double getChangerStartToMidSpeed() {
            return tip.getChangerStartToMidSpeed();
        }

        public void setChangerStartToMidSpeed(double speed) {
            tip.setChangerStartToMidSpeed(speed);
        }

        public String getChangerActuatorPostStepOne() {
            return none(tip.getChangerActuatorPostStepOne());
        }

        public void setChangerActuatorPostStepOne(String actuator) {
            tip.setChangerActuatorPostStepOne(actuator);
        }

        public Location getChangerMidLocation() {
            return tip.getChangerMidLocation();
        }

        public void setChangerMidLocation(Location location) {
            tip.setChangerMidLocation(location);
        }

        public double getChangerMidToMid2Speed() {
            return tip.getChangerMidToMid2Speed();
        }

        public void setChangerMidToMid2Speed(double speed) {
            tip.setChangerMidToMid2Speed(speed);
        }

        public String getChangerActuatorPostStepTwo() {
            return none(tip.getChangerActuatorPostStepTwo());
        }

        public void setChangerActuatorPostStepTwo(String actuator) {
            tip.setChangerActuatorPostStepTwo(actuator);
        }

        public Location getChangerMidLocation2() {
            return tip.getChangerMidLocation2();
        }

        public void setChangerMidLocation2(Location location) {
            tip.setChangerMidLocation2(location);
        }

        public double getChangerMid2ToEndSpeed() {
            return tip.getChangerMid2ToEndSpeed();
        }

        public void setChangerMid2ToEndSpeed(double speed) {
            tip.setChangerMid2ToEndSpeed(speed);
        }

        public String getChangerActuatorPostStepThree() {
            return none(tip.getChangerActuatorPostStepThree());
        }

        public void setChangerActuatorPostStepThree(String actuator) {
            tip.setChangerActuatorPostStepThree(actuator);
        }

        public Location getChangerEndLocation() {
            return tip.getChangerEndLocation();
        }

        public void setChangerEndLocation(Location location) {
            tip.setChangerEndLocation(location);
        }

        public Location getTouchLocation() {
            return tip.getTouchLocation();
        }

        public void setTouchLocation(Location location) {
            tip.setTouchLocation(location);
        }

        public ZCalibrationTrigger getZCalibrationTrigger() {
            return tip.getzCalibrationTrigger();
        }

        public void setZCalibrationTrigger(ZCalibrationTrigger trigger) {
            tip.setzCalibrationTrigger(trigger);
        }

        public Length getCalibrationOffsetZ() {
            return tip.getCalibrationOffsetZ();
        }

        public boolean isZCalibrationFailHoming() {
            return tip.iszCalibrationFailHoming();
        }

        public void setZCalibrationFailHoming(boolean fail) {
            tip.setzCalibrationFailHoming(fail);
        }

        public VisionCalibration getVisionCalibration() {
            return tip.getVisionCalibration();
        }

        public void setVisionCalibration(VisionCalibration location) {
            tip.setVisionCalibration(location);
        }

        public Length getVisionCalibrationZAdjust() {
            return tip.getVisionCalibrationZAdjust();
        }

        public void setVisionCalibrationZAdjust(Length adjust) {
            tip.setVisionCalibrationZAdjust(adjust);
        }

        public VisionCalibrationTrigger getVisionCalibrationTrigger() {
            return tip.getVisionCalibrationTrigger();
        }

        public void setVisionCalibrationTrigger(VisionCalibrationTrigger trigger) {
            tip.setVisionCalibrationTrigger(trigger);
        }

        public Length getVisionTemplateDimensionX() {
            return tip.getVisionTemplateDimensionX();
        }

        public void setVisionTemplateDimensionX(Length width) {
            tip.setVisionTemplateDimensionX(width);
        }

        public Length getVisionTemplateDimensionY() {
            return tip.getVisionTemplateDimensionY();
        }

        public void setVisionTemplateDimensionY(Length height) {
            tip.setVisionTemplateDimensionY(height);
        }

        public Length getVisionTemplateTolerance() {
            return tip.getVisionTemplateTolerance();
        }

        public void setVisionTemplateTolerance(Length tolerance) {
            tip.setVisionTemplateTolerance(tolerance);
        }

        public Length getVisionCalibrationTolerance() {
            return tip.getVisionCalibrationTolerance();
        }

        public void setVisionCalibrationTolerance(Length precision) {
            tip.setVisionCalibrationTolerance(precision);
        }

        public int getVisionCalibrationMaxPasses() {
            return tip.getVisionCalibrationMaxPasses();
        }

        public void setVisionCalibrationMaxPasses(int passes) {
            tip.setVisionCalibrationMaxPasses(passes);
        }

        public double getVisionMatchMinimumScore() {
            return tip.getVisionMatchMinimumScore();
        }

        public void setVisionMatchMinimumScore(double score) {
            tip.setVisionMatchMinimumScore(score);
        }

        public Double getVisionMatchLastScore() {
            return tip.getVisionMatchLastScore();
        }

        public TemplateRole getTemplateRole() {
            return tip.isTemplateNozzleTip() ? TemplateRole.Template
                    : tip.isTemplateLocked() ? TemplateRole.Locked : TemplateRole.Clone;
        }

        public void setTemplateRole(TemplateRole role) {
            if (role == TemplateRole.Template) {
                tip.setTemplateLocked(false);
                tip.setTemplateNozzleTip(true);
            }
            else if (role == TemplateRole.Locked) {
                tip.setTemplateNozzleTip(false);
                tip.setTemplateLocked(true);
            }
            else {
                tip.setTemplateClone(true);
            }
        }

        /** Not kept: what a clone takes over, for the button that clones. */
        public boolean isCloneLocations() {
            return cloneLocations;
        }

        public void setCloneLocations(boolean clone) {
            cloneLocations = clone;
        }

        public boolean isCloneZCalibration() {
            return cloneZCalibration;
        }

        public void setCloneZCalibration(boolean clone) {
            cloneZCalibration = clone;
        }

        public boolean isCloneVisionCalibration() {
            return cloneVisionCalibration;
        }

        public void setCloneVisionCalibration(boolean clone) {
            cloneVisionCalibration = clone;
        }

        private static String none(String name) {
            return name == null || name.isEmpty() ? null : name;
        }
    }

    // ==== settings ==================================================================================

    public static FormWizard settings(ReferenceNozzleTip tip) {
        return Form.of(new Bean(tip)).named("NozzleTipForm.Title") //$NON-NLS-1$
                .section("NozzleTipForm.Basics", "nozzle") //$NON-NLS-1$ //$NON-NLS-2$
                .text("name", "ReferenceNozzleTipConfigurationWizard.PropertiesPanel.NameLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .section("NozzleTipForm.PickPlace", "download") //$NON-NLS-1$ //$NON-NLS-2$
                .integer("pickDwellMilliseconds", "NozzleForm.PickDwell").unit("ms").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .integer("placeDwellMilliseconds", "NozzleForm.PlaceDwell").unit("ms").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("NozzleTipForm.Dwell.Hint") //$NON-NLS-1$
                .decimal("placeBlowOffLevel", "ReferenceNozzleTipConfigurationWizard.PickAndPlacePanel.PlaceBlowOffLevelLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .width(120)
                .hint("NozzleTipForm.BlowOff.Hint") //$NON-NLS-1$
                .section("NozzleTipForm.Parts", "parts") //$NON-NLS-1$ //$NON-NLS-2$
                .length("minPartDiameter", "NozzleTipForm.MinPartDiameter").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("NozzleTipForm.MinPartDiameter.Hint") //$NON-NLS-1$
                .length("maxPartDiameter", "NozzleTipForm.MaxPartDiameter").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("NozzleTipForm.MaxPartDiameter.Hint") //$NON-NLS-1$
                .length("maxPartHeight", "NozzleTipForm.MaxPartHeight").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("NozzleTipForm.MaxPartHeight.Hint") //$NON-NLS-1$
                .length("maxPickTolerance", "NozzleTipForm.MaxPickTolerance").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("NozzleTipForm.MaxPickTolerance.Hint") //$NON-NLS-1$
                .section("NozzleTipForm.PushDrag", "move").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("pushAndDragAllowed", "NozzleTipForm.PushDrag.Allowed", "NozzleTipForm.PushDrag.Allowed.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("NozzleTipForm.PushDrag.Allowed.Hint") //$NON-NLS-1$
                .length("diameterLow", "ReferenceNozzleTipConfigurationWizard.PushAndDragPanel.OutsideDiameterLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .width(120)
                .visibleWhen("pushAndDragAllowed", Boolean.TRUE::equals) //$NON-NLS-1$
                .hint("NozzleTipForm.OutsideDiameter.Hint") //$NON-NLS-1$
                .build();
    }

    // ==== runout calibration ========================================================================

    public static FormWizard calibration(ReferenceNozzleTip tip) {
        ReferenceNozzleTipCalibration calibration = tip.getCalibration();
        FormWizard[] form = new FormWizard[1];
        RunoutPanel result = new RunoutPanel(tip);
        JButton now = Ui.button(Translations.getString("NozzleTipForm.CalibrateNow"), Ui.iconSm("target"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Primary);
        Ui.movesMachine(now);
        now.addActionListener(e -> {
            form[0].apply();
            UiUtils.submitUiMachineTask(() -> calibration.calibrate(uiCalibrationNozzle(tip)));
        });
        JButton camera = Ui.button(Translations.getString("NozzleTipForm.CalibrateCamera"), Ui.iconSm("camera"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Default);
        camera.setToolTipText(Translations.getString("NozzleTipForm.CalibrateCamera.ToolTip")); //$NON-NLS-1$
        Ui.movesMachine(camera);
        camera.addActionListener(e -> {
            form[0].apply();
            UiUtils.submitUiMachineTask(() -> calibration.calibrateCamera(uiCalibrationNozzle(tip)));
        });
        camera.setVisible(!advancedCameraCalibration());
        JButton center = Ui.button(Translations.getString("NozzleTipForm.MoveToCamera"), Ui.iconSm("move"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Default);
        Ui.movesMachine(center);
        center.addActionListener(e -> UiUtils.submitUiMachineTask(() -> {
            HeadMountable nozzle = uiCalibrationNozzle(tip);
            Camera bottom = VisionUtils.getBottomVisionCamera();
            MovableUtils.moveToLocationAtSafeZ(nozzle, calibration.getCalibrationLocation(bottom, nozzle));
            MovableUtils.fireTargetedUserAction(nozzle);
        }));
        JButton clear = Ui.button(Translations.getString("NozzleTipForm.ClearResults"), null, //$NON-NLS-1$
                Ui.Size.Sm, Ui.Variant.Ghost);
        clear.addActionListener(e -> calibration.resetAll());
        JPanel buttons = Forms.row(now, camera);
        buttons.add(Box.createHorizontalGlue());
        buttons.add(clear);
        JPanel more = Forms.row(center);
        more.add(Box.createHorizontalGlue());
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        for (JComponent part : new JComponent[] { result, buttons, more }) {
            part.setAlignmentX(0f);
            if (column.getComponentCount() > 0) {
                column.add(Box.createVerticalStrut(8));
            }
            column.add(part);
        }
        form[0] = Form.of(new Bean(tip)).named("ReferenceNozzleTip.Calibration.tab.title") //$NON-NLS-1$
                .section("NozzleTipForm.Runout", "target").measuredBy(CalibrationStep.NozzleTipCalibration, tip) //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("calibrationEnabled", "NozzleTipForm.Runout.Enabled", "NozzleTipForm.Runout.Enabled.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .choice("recalibrationTrigger", "NozzleTipForm.Runout.When", RecalibrationTrigger.class) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("calibrationEnabled", Boolean.TRUE::equals) //$NON-NLS-1$
                .toggle("failHoming", "NozzleTipForm.Runout.FailHoming", "NozzleTipForm.Runout.FailHoming.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen("recalibrationTrigger", v -> v == RecalibrationTrigger.MachineHome //$NON-NLS-1$
                        || v == RecalibrationTrigger.NozzleTipChange)
                .choice("runoutCompensationAlgorithm", "NozzleTipForm.Runout.Algorithm", RunoutCompensationAlgorithm.class) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("calibrationEnabled", Boolean.TRUE::equals) //$NON-NLS-1$
                .hint("NozzleTipForm.Runout.Algorithm.Hint") //$NON-NLS-1$
                .section("NozzleTipForm.Measurement", "sliders") //$NON-NLS-1$ //$NON-NLS-2$
                .integer("angleSubdivisions", "ReferenceNozzleTipCalibrationWizard.CalibrationPanel.AngleIncrementsLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .unit("NozzleTipForm.Angles").width(150) //$NON-NLS-1$
                .integer("allowMisdetections", "ReferenceNozzleTipCalibrationWizard.CalibrationPanel.AllowMisDetectsLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .unit("JobProcessorForm.Times").width(150) //$NON-NLS-1$
                .length("offsetThresholdLength", "ReferenceNozzleTipCalibrationWizard.CalibrationPanel.OffsetThresholdLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .width(150)
                .hint("NozzleTipForm.OffsetThreshold.Hint") //$NON-NLS-1$
                .length("calibrationTipDiameter", "ReferenceNozzleTipCalibrationWizard.CalibrationPanel.VisionDiameterLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .width(150)
                .hint("NozzleTipForm.VisionDiameter.Hint") //$NON-NLS-1$
                .length("calibrationZOffset", "NozzleTipForm.ZOffset").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("NozzleTipForm.ZOffset.Hint") //$NON-NLS-1$
                .pipeline("ReferenceNozzleTipCalibrationWizard.CalibrationPanel.PipelineLabel.text", //$NON-NLS-1$
                        () -> PipelineStages.summary(calibration.getPipeline()),
                        () -> UiUtils.messageBoxOnException(() -> editPipeline(tip)),
                        () -> resetPipeline(form[0], calibration))
                .section("NozzleTipForm.Result", "activity") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", column) //$NON-NLS-1$
                .build();
        return form[0];
    }

    /** Whether the bottom camera's advanced calibration has its position and rotation already. */
    private static boolean advancedCameraCalibration() {
        try {
            Camera camera = VisionUtils.getBottomVisionCamera();
            return camera instanceof ReferenceCamera && ((ReferenceCamera) camera).getAdvancedCalibration()
                    .isOverridingOldTransformsAndDistortionCorrectionSettings();
        }
        catch (Exception e) {
            return false;
        }
    }

    private static void editPipeline(ReferenceNozzleTip tip) throws Exception {
        Camera camera = VisionUtils.getBottomVisionCamera();
        ReferenceNozzleTipCalibration calibration = tip.getCalibration();
        // Where the nozzle is, as the nominal location when it is in view, which tests an
        // off-centre detection; the camera's centre otherwise.
        ReferenceNozzle nozzle = uiCalibrationNozzle(tip);
        Location location = nozzle.getLocation();
        Location distance = location.subtract(camera.getLocation());
        if (Math.abs(distance.getLengthX().divide(camera.getUnitsPerPixelAtZ().getLengthX())) >= camera.getWidth() / 2
                || Math.abs(distance.getLengthY().divide(camera.getUnitsPerPixelAtZ().getLengthY())) >= camera.getHeight() / 2
                || Math.abs(distance.getLengthZ().convertToUnits(LengthUnit.Millimeters).getValue()) >= 0.1) {
            location = calibration.getCalibrationLocation(camera, nozzle);
        }
        Location moveTo = location;
        UiUtils.confirmMoveToLocationAndAct(MainFrame.get(),
                String.format(Translations.getString("ReferenceNozzleTipCalibrationWizard.MoveNozzleToCameraCenter"), //$NON-NLS-1$
                        nozzle.getName()),
                nozzle, moveTo, true, () -> {
                    CvPipeline pipeline = calibration.getPreparedPipeline(camera, nozzle, moveTo);
                    JDialog dialog = new CvPipelineEditorDialog(MainFrame.get(),
                            Translations.getString("NozzleTipForm.Pipeline.Title"), new CvPipelineEditor(pipeline)); //$NON-NLS-1$
                    dialog.setVisible(true);
                });
    }

    private static void resetPipeline(FormWizard form, ReferenceNozzleTipCalibration calibration) {
        int chosen = Dialogs.ask(MainFrame.get(), Dialogs.Tone.Warn, "refresh", //$NON-NLS-1$
                Translations.getString("NozzleTipForm.Pipeline.Reset.Title"), //$NON-NLS-1$
                Translations.getString("NozzleTipForm.Pipeline.Reset.What"), null, //$NON-NLS-1$
                Dialogs.Choice.danger(Translations.getString("NozzleTipForm.Pipeline.Reset.Action"))); //$NON-NLS-1$
        if (chosen == 0) {
            calibration.resetPipeline();
            form.reload();
        }
    }

    /**
     * The nozzle a calibration of the tip runs on: the one it is on, or for the stand-in of a bare
     * nozzle the one selected in the machine controls when it is bare.
     */
    public static ReferenceNozzle uiCalibrationNozzle(ReferenceNozzleTip tip) throws Exception {
        if (tip.isUnloadedNozzleTipStandin()) {
            Nozzle nozzle = MainFrame.get().getMachineControls().getSelectedNozzle();
            if (nozzle instanceof ReferenceNozzle && ((ReferenceNozzle) nozzle).getCalibrationNozzleTip() == tip) {
                return (ReferenceNozzle) nozzle;
            }
            throw new Exception(Translations.getString("NozzleTipForm.UnloadFirst")); //$NON-NLS-1$
        }
        ReferenceNozzle nozzle = tip.getNozzleWhereLoaded();
        if (nozzle == null) {
            throw new Exception(Translations.getString("NozzleTipForm.LoadFirst")); //$NON-NLS-1$
        }
        return nozzle;
    }

    /** The calibration's result: its state, the runout drawn, and the numbers. */
    @SuppressWarnings("serial")
    static final class RunoutPanel extends JPanel {
        private final ReferenceNozzleTip tip;
        private final Chip state = new Chip("", Chip.Tone.Neutral, Chip.Shape.Status); //$NON-NLS-1$
        private final JLabel where = Ui.t2(""); //$NON-NLS-1$
        private final RunoutChart chart = new RunoutChart();
        private final JTextArea facts = Forms.paragraph(""); //$NON-NLS-1$

        RunoutPanel(ReferenceNozzleTip tip) {
            this.tip = tip;
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            JPanel head = Forms.row(state, where);
            head.add(Box.createHorizontalGlue());
            head.setAlignmentX(0f);
            add(head);
            add(Box.createVerticalStrut(8));
            JPanel box = roundBox(new BorderLayout(14, 0));
            box.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
            box.add(chart, BorderLayout.WEST);
            facts.setFont(Ui.font(Tokens.FS_AUX));
            // Beside the chart and centred on it, as the mockup has the numbers.
            JPanel middle = new JPanel(new java.awt.GridBagLayout());
            middle.setOpaque(false);
            java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
            gc.weightx = 1;
            gc.fill = java.awt.GridBagConstraints.HORIZONTAL;
            middle.add(facts, gc);
            box.add(middle, BorderLayout.CENTER);
            box.setAlignmentX(0f);
            add(box);
            WeakForward.listen(tip.getCalibration(), this, (panel, e) -> panel.refresh());
            WeakForward.listen(tip, this, (panel, e) -> panel.refresh());
            refresh();
        }

        void refresh() {
            ReferenceNozzle nozzle;
            try {
                nozzle = uiCalibrationNozzle(tip);
            }
            catch (Exception e) {
                nozzle = null;
            }
            RunoutCompensation model = nozzle == null ? null : tip.getCalibration().getRunoutCompensation(nozzle);
            if (!tip.getCalibration().isEnabled()) {
                state.setText(Translations.getString("NozzleTipForm.Status.Off")); //$NON-NLS-1$
                state.setTone(Chip.Tone.Neutral);
            }
            else if (nozzle == null) {
                state.setText(Translations.getString("NozzleTipForm.Status.NotLoaded")); //$NON-NLS-1$
                state.setTone(Chip.Tone.Neutral);
            }
            else if (model == null) {
                state.setText(Translations.getString("NozzleTipForm.Status.Uncalibrated")); //$NON-NLS-1$
                state.setTone(Chip.Tone.Warn);
            }
            else {
                state.setText(Translations.getString("NozzleTipForm.Status.Calibrated")); //$NON-NLS-1$
                state.setTone(Chip.Tone.Ok);
            }
            where.setText(nozzle == null ? Translations.getString("NozzleTipForm.Status.LoadFirst") //$NON-NLS-1$
                    : String.format(Translations.getString("NozzleTipForm.Status.On"), nozzle.getName())); //$NON-NLS-1$
            chart.setModel(model);
            facts.setText(facts(model));
            revalidate();
            repaint();
        }

        private static String facts(RunoutCompensation model) {
            List<Location> points = RunoutChart.points(model);
            if (model == null || points.isEmpty()) {
                return Translations.getString("NozzleTipForm.Facts.None"); //$NON-NLS-1$
            }
            String unit = points.get(0).getUnits().getShortName();
            List<String> lines = new ArrayList<>();
            if (model instanceof ModelBasedRunoutCompensation) {
                ModelBasedRunoutCompensation fitted = (ModelBasedRunoutCompensation) model;
                Location axis = fitted.getAxisOffset();
                lines.add(String.format(Locale.US, Translations.getString("NozzleTipForm.Facts.Runout"), //$NON-NLS-1$
                        String.format(Locale.US, "%.3f %s", fitted.getRadius(), unit))); //$NON-NLS-1$
                lines.add(String.format(Locale.US, Translations.getString("NozzleTipForm.Facts.Phase"), //$NON-NLS-1$
                        String.format(Locale.US, "%+.1f\u00b0", fitted.getPhaseShift()))); //$NON-NLS-1$
                lines.add(String.format(Translations.getString("NozzleTipForm.Facts.Angles"), points.size())); //$NON-NLS-1$
                lines.add(String.format(Locale.US, Translations.getString("NozzleTipForm.Facts.Axis"), //$NON-NLS-1$
                        String.format(Locale.US, "%+.3f, %+.3f %s", axis.getX(), axis.getY(), unit))); //$NON-NLS-1$
                if (fitted.getRmsError() != null) {
                    lines.add(String.format(Locale.US, Translations.getString("NozzleTipForm.Facts.Rms"), //$NON-NLS-1$
                            String.format(Locale.US, "%.3f %s", fitted.getRmsError(), unit))); //$NON-NLS-1$
                }
            }
            else {
                lines.add(String.format(Translations.getString("NozzleTipForm.Facts.Table"), points.size())); //$NON-NLS-1$
            }
            return String.join("\n", lines); //$NON-NLS-1$
        }
    }

    /** The mockup's result box: surface-2 with a border, rounded at 10. */
    @SuppressWarnings("serial")
    static JPanel roundBox(java.awt.LayoutManager layout) {
        JPanel box = new JPanel(layout) {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                try {
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Ui.surface2());
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
                    g2.setColor(Ui.border());
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
                }
                finally {
                    g2.dispose();
                }
            }
        };
        box.setOpaque(false);
        return box;
    }

    // ==== background calibration ====================================================================

    public static FormWizard background(ReferenceNozzleTip tip) {
        ReferenceNozzleTipCalibration calibration = tip.getCalibration();
        BackgroundPanel measured = new BackgroundPanel(tip);
        JButton problems = Ui.button(Translations.getString(
                "ReferenceNozzleTipCalibrationWizard.BackgroundCalibrationPanel.ShowProblemsButton.text"), //$NON-NLS-1$
                Ui.iconSm("camera"), Ui.Size.Sm, Ui.Variant.Default); //$NON-NLS-1$
        problems.setToolTipText(Translations.getString(
                "ReferenceNozzleTipCalibrationWizard.BackgroundCalibrationPanel.ShowProblemsButton.toolTipText")); //$NON-NLS-1$
        problems.addActionListener(e -> showBackgroundProblems(tip, true));
        JPanel problemsRow = Forms.row(problems);
        problemsRow.add(Box.createHorizontalGlue());
        return Form.of(new Bean(tip)).named("NozzleTipForm.Background.Title") //$NON-NLS-1$
                .section("NozzleTipForm.Background", "palette").measuredBy(CalibrationStep.NozzleTipCalibration, tip) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("backgroundCalibrationMethod", "NozzleTipForm.Background.Method", BackgroundCalibrationMethod.class) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("NozzleTipForm.Background.Method.Hint") //$NON-NLS-1$
                .length("minimumDetailSize", "ReferenceNozzleTipCalibrationWizard.BackgroundCalibrationPanel.MinDetailSizeLabel.title") //$NON-NLS-1$ //$NON-NLS-2$
                .width(150)
                .visibleWhen("backgroundCalibrationMethod", v -> v != BackgroundCalibrationMethod.None) //$NON-NLS-1$
                .hint("NozzleTipForm.Background.DetailSize.Hint") //$NON-NLS-1$
                .custom("NozzleTipForm.Background.Measured", measured) //$NON-NLS-1$
                .visibleWhen("backgroundCalibrationMethod", v -> v != BackgroundCalibrationMethod.None) //$NON-NLS-1$
                .integer("backgroundTolHue", "NozzleTipForm.Background.TolHue").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("backgroundCalibrationMethod", v -> v == BackgroundCalibrationMethod.BrightnessAndKeyColor) //$NON-NLS-1$
                .integer("backgroundTolSaturation", "NozzleTipForm.Background.TolSaturation").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("backgroundCalibrationMethod", v -> v == BackgroundCalibrationMethod.BrightnessAndKeyColor) //$NON-NLS-1$
                .integer("backgroundTolValue", "NozzleTipForm.Background.TolValue").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("backgroundCalibrationMethod", v -> v != BackgroundCalibrationMethod.None) //$NON-NLS-1$
                .hint("NozzleTipForm.Background.Tolerance.Hint") //$NON-NLS-1$
                .section("NozzleTipForm.Background.Last", "clock") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", measured.diagnostics) //$NON-NLS-1$
                .custom("", problemsRow) //$NON-NLS-1$
                .build();
    }

    /**
     * What the last background calibration measured: the ranges of hue, saturation and
     * brightness beside the colour they span, and the diagnostics in the display language.
     */
    @SuppressWarnings("serial")
    static final class BackgroundPanel extends JPanel {
        private final ReferenceNozzleTipCalibration calibration;
        private final HsvIndicator indicator = new HsvIndicator();
        private final JLabel[] ranges = { Ui.mono("", Tokens.FS_AUX), Ui.mono("", Tokens.FS_AUX), Ui.mono("", Tokens.FS_AUX) }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        final JTextArea diagnostics = Forms.paragraph(""); //$NON-NLS-1$

        BackgroundPanel(ReferenceNozzleTip tip) {
            this.calibration = tip.getCalibration();
            setOpaque(false);
            // The ranges above the colour they span: beside it, the column at 1366 had no room
            // left for the names.
            setLayout(new BorderLayout(0, 8));
            JPanel grid = new JPanel(new java.awt.GridBagLayout());
            grid.setOpaque(false);
            String[] names = { "NozzleTipForm.Background.Hue", "NozzleTipForm.Background.Saturation", //$NON-NLS-1$ //$NON-NLS-2$
                    "NozzleTipForm.Background.Value" }; //$NON-NLS-1$
            for (int i = 0; i < 3; i++) {
                java.awt.GridBagConstraints gc = new java.awt.GridBagConstraints();
                gc.gridy = i;
                gc.anchor = java.awt.GridBagConstraints.WEST;
                gc.insets = new java.awt.Insets(i == 0 ? 0 : 4, 0, 0, 12);
                grid.add(Ui.t2(Translations.getString(names[i])), gc);
                gc.gridx = 1;
                gc.weightx = 1;
                gc.insets = new java.awt.Insets(i == 0 ? 0 : 4, 0, 0, 0);
                grid.add(ranges[i], gc);
            }
            add(grid, BorderLayout.CENTER);
            JPanel swatch = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
            swatch.setOpaque(false);
            swatch.add(indicator);
            add(swatch, BorderLayout.SOUTH);
            WeakForward.listen(calibration, this, (panel, e) -> panel.refresh());
            refresh();
        }

        void refresh() {
            ranges[0].setText(range(calibration.getBackgroundMinHue(), calibration.getBackgroundMaxHue()));
            ranges[1].setText(range(calibration.getBackgroundMinSaturation(), calibration.getBackgroundMaxSaturation()));
            ranges[2].setText(range(calibration.getBackgroundMinValue(), calibration.getBackgroundMaxValue()));
            indicator.setMinHue(calibration.getBackgroundMinHue());
            indicator.setMaxHue(calibration.getBackgroundMaxHue());
            indicator.setMinSaturation(calibration.getBackgroundMinSaturation());
            indicator.setMaxSaturation(calibration.getBackgroundMaxSaturation());
            indicator.setMinValue(calibration.getBackgroundMinValue());
            indicator.setMaxValue(calibration.getBackgroundMaxValue());
            diagnostics.setText(diagnostics(calibration.getBackgroundDiagnostics()));
            revalidate();
            repaint();
        }

        private static String range(int min, int max) {
            return min + " \u2013 " + max; //$NON-NLS-1$
        }
    }

    /**
     * The calibration's report, written in English as lines of HTML, as plain lines in the
     * display language.
     */
    static String diagnostics(String report) {
        if (report == null || report.trim().isEmpty()) {
            return Translations.getString(
                    "ReferenceNozzleTipCalibrationWizard.BackgroundCalibrationPanel.BackgroundDiagnosticsLabel.text"); //$NON-NLS-1$
        }
        String text = Dialogs.plainText(report.replace("<hr/>", "<br/>")); //$NON-NLS-1$ //$NON-NLS-2$
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n")) { //$NON-NLS-1$
            String trimmed = line.trim().replaceAll("\\.\\.$", "."); //$NON-NLS-1$ //$NON-NLS-2$
            if (!trimmed.isEmpty()) {
                lines.add(Translations.translateText(trimmed));
            }
        }
        return String.join("\n", lines); //$NON-NLS-1$
    }

    /** The frames the background calibration found problems in, shown in the bottom camera's view. */
    public static void showBackgroundProblems(ReferenceNozzleTip tip, boolean noProblems) {
        UiUtils.messageBoxOnException(() -> {
            BufferedImage[] images = tip.getCalibration().getBackgroundCalibrationImages();
            if (images == null) {
                throw new Exception(String.format(Translations.getString("NozzleTipForm.Background.NoneRecorded"), //$NON-NLS-1$
                        tip.getName()));
            }
            int n = images.length;
            if (n == 0) {
                if (noProblems) {
                    throw new Exception(String.format(Translations.getString("NozzleTipForm.Background.NoProblems"), //$NON-NLS-1$
                            tip.getName()));
                }
                return;
            }
            Camera camera = VisionUtils.getBottomVisionCamera();
            camera.ensureCameraVisible();
            CameraView view = MainFrame.get().getCameraViews().getCameraView(camera);
            String[] texts = new String[n];
            for (int i = 0; i < n; i++) {
                texts[i] = String.format(Translations.getString("NozzleTipForm.Background.ProblemFrame"), //$NON-NLS-1$
                        i / 2 + 1, n / 2);
            }
            view.showFilteredImages(images, texts, 1000);
        });
    }

    // ==== part detection ============================================================================

    public static FormWizard partDetection(ReferenceNozzleTip tip) {
        Bean bean = new Bean(tip);
        SimpleGraphView onGraph = graph(bean, "vacuumPartOnGraph", tip.getVacuumPartOnGraph()); //$NON-NLS-1$
        SimpleGraphView offGraph = graph(bean, "vacuumPartOffGraph", tip.getVacuumPartOffGraph()); //$NON-NLS-1$
        return Form.of(bean).named("ReferenceNozzleTip.PartDetection.tab.title") //$NON-NLS-1$
                .section("NozzleTipForm.PartOn", "download") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("methodPartOn", "ReferenceNozzleTipPartDetectionWizard.PartOnVacuumSensingPanel.MeasurementMethodLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        VacuumMeasurementMethod.class)
                .toggle("establishPartOnLevel", "NozzleTipForm.EstablishLevel", "NozzleTipForm.EstablishLevel.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen("methodPartOn", NozzleTipForm::measures) //$NON-NLS-1$
                .hint("NozzleTipForm.EstablishPartOn.Hint") //$NON-NLS-1$
                .checklist("partOnChecks", "NozzleTipForm.Checks", Arrays.asList(PartOnCheck.values()), null) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("methodPartOn", NozzleTipForm::measures) //$NON-NLS-1$
                .decimal("vacuumLevelPartOnLow", "NozzleTipForm.VacuumLow").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("methodPartOn", NozzleTipForm::measures) //$NON-NLS-1$
                .decimal("vacuumLevelPartOnHigh", "NozzleTipForm.VacuumHigh").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("methodPartOn", NozzleTipForm::measures) //$NON-NLS-1$
                .readOnly("vacuumLevelPartOnReading", "NozzleTipForm.LastReading") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("methodPartOn", NozzleTipForm::measures) //$NON-NLS-1$
                .decimal("vacuumDifferencePartOnLow", "NozzleTipForm.DifferenceLow").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("methodPartOn", NozzleTipForm::difference) //$NON-NLS-1$
                .decimal("vacuumDifferencePartOnHigh", "NozzleTipForm.DifferenceHigh").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("methodPartOn", NozzleTipForm::difference) //$NON-NLS-1$
                .readOnly("vacuumDifferencePartOnReading", "NozzleTipForm.LastDifference") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("methodPartOn", NozzleTipForm::difference) //$NON-NLS-1$
                .custom("NozzleTipForm.Graph", onGraph) //$NON-NLS-1$
                .visibleWhen("methodPartOn", NozzleTipForm::measures) //$NON-NLS-1$
                .hint("NozzleTipForm.Graph.Hint") //$NON-NLS-1$
                .section("NozzleTipForm.PartOff", "upload") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("methodPartOff", "ReferenceNozzleTipPartDetectionWizard.PartOffVacuumSensingPanel.MeasurementMethodLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        VacuumMeasurementMethod.class)
                .toggle("establishPartOffLevel", "NozzleTipForm.EstablishLevel", "NozzleTipForm.EstablishLevel.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen("methodPartOff", NozzleTipForm::measures) //$NON-NLS-1$
                .hint("NozzleTipForm.EstablishPartOff.Hint") //$NON-NLS-1$
                .checklist("partOffChecks", "NozzleTipForm.Checks", Arrays.asList(PartOffCheck.values()), null) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("methodPartOff", NozzleTipForm::measures) //$NON-NLS-1$
                .decimal("vacuumLevelPartOffLow", "NozzleTipForm.VacuumLow").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("methodPartOff", NozzleTipForm::measures) //$NON-NLS-1$
                .decimal("vacuumLevelPartOffHigh", "NozzleTipForm.VacuumHigh").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("methodPartOff", NozzleTipForm::measures) //$NON-NLS-1$
                .readOnly("vacuumLevelPartOffReading", "NozzleTipForm.LastReading") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("methodPartOff", NozzleTipForm::measures) //$NON-NLS-1$
                .integer("partOffProbingMilliseconds", "NozzleTipForm.ValveOpen").unit("ms").width(150) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen("methodPartOff", NozzleTipForm::measures) //$NON-NLS-1$
                .integer("partOffDwellMilliseconds", "NozzleTipForm.ValveClosed").unit("ms").width(150) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen("methodPartOff", NozzleTipForm::measures) //$NON-NLS-1$
                .hint("NozzleTipForm.Valve.Hint") //$NON-NLS-1$
                .decimal("vacuumDifferencePartOffLow", "NozzleTipForm.DifferenceLow").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("methodPartOff", NozzleTipForm::difference) //$NON-NLS-1$
                .decimal("vacuumDifferencePartOffHigh", "NozzleTipForm.DifferenceHigh").width(150) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("methodPartOff", NozzleTipForm::difference) //$NON-NLS-1$
                .readOnly("vacuumDifferencePartOffReading", "NozzleTipForm.LastDifference") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("methodPartOff", NozzleTipForm::difference) //$NON-NLS-1$
                .custom("NozzleTipForm.Graph", offGraph) //$NON-NLS-1$
                .visibleWhen("methodPartOff", NozzleTipForm::measures) //$NON-NLS-1$
                .build();
    }

    private static boolean measures(Object method) {
        return method != null && method != VacuumMeasurementMethod.None;
    }

    private static boolean difference(Object method) {
        return method instanceof VacuumMeasurementMethod && ((VacuumMeasurementMethod) method).isDifferenceMethod();
    }

    /** A vacuum graph, drawn again when the bean says the tip has a new one. */
    private static SimpleGraphView graph(Bean bean, String property, SimpleGraph graph) {
        SimpleGraphView view = new SimpleGraphView();
        view.setFont(Ui.font(Tokens.FS_AUX));
        view.setGraph(graph);
        view.setPreferredSize(new java.awt.Dimension(200, 110));
        view.setMinimumSize(new java.awt.Dimension(60, 110));
        bean.addPropertyChangeListener(property, e -> view.setGraph((SimpleGraph) e.getNewValue()));
        return view;
    }

    // ==== tip changer ===============================================================================

    public static FormWizard changer(ReferenceNozzleTip tip) {
        List<String> actuators = new ArrayList<>();
        actuators.add(null);
        for (Actuator actuator : tip.getMachine().getActuators()) {
            actuators.add(actuator.getName());
        }
        boolean probing = ContactProbeNozzle.isConfigured(tip.getMachine());
        FormWizard[] form = new FormWizard[1];
        TemplateImageControl empty = new TemplateImageControl();
        TemplateImageControl occupied = new TemplateImageControl();
        empty.setTemplateImage(tip.getVisionTemplateImageEmpty());
        occupied.setTemplateImage(tip.getVisionTemplateImageOccupied());
        Bean bean = new Bean(tip);
        bean.addPropertyChangeListener("visionTemplateImageEmpty", e -> empty.setTemplateImage((TemplateImage) e.getNewValue())); //$NON-NLS-1$
        bean.addPropertyChangeListener("visionTemplateImageOccupied", e -> occupied.setTemplateImage((TemplateImage) e.getNewValue())); //$NON-NLS-1$
        Form.Builder b = Form.of(bean).named("ReferenceNozzleTip.ToolChanger.tab.title") //$NON-NLS-1$
                .section("NozzleTipForm.Route", "move") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", Forms.paragraph(Translations.getString("NozzleTipForm.Route.Note"))) //$NON-NLS-1$ //$NON-NLS-2$
                .location("changerStartLocation", "ReferenceNozzleTipToolChangerWizard.ChangerPanel.FirstLocationLabel.text", false) //$NON-NLS-1$ //$NON-NLS-2$
                .withZ().locationButtons()
                .decimal("changerStartToMidSpeed", "NozzleTipForm.Route.Speed12").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("changerActuatorPostStepOne", "NozzleTipForm.Route.Actuator1", actuators, null) //$NON-NLS-1$ //$NON-NLS-2$
                .location("changerMidLocation", "ReferenceNozzleTipToolChangerWizard.ChangerPanel.SecondLocationLabel.text", false) //$NON-NLS-1$ //$NON-NLS-2$
                .withZ().locationButtons()
                .decimal("changerMidToMid2Speed", "NozzleTipForm.Route.Speed23").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("changerActuatorPostStepTwo", "NozzleTipForm.Route.Actuator2", actuators, null) //$NON-NLS-1$ //$NON-NLS-2$
                .location("changerMidLocation2", "ReferenceNozzleTipToolChangerWizard.ChangerPanel.ThirdLocationLabel.text", false) //$NON-NLS-1$ //$NON-NLS-2$
                .withZ().locationButtons()
                .decimal("changerMid2ToEndSpeed", "NozzleTipForm.Route.Speed34").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("changerActuatorPostStepThree", "NozzleTipForm.Route.Actuator3", actuators, null) //$NON-NLS-1$ //$NON-NLS-2$
                .location("changerEndLocation", "ReferenceNozzleTipToolChangerWizard.ChangerPanel.LastLocationLabel.text", false) //$NON-NLS-1$ //$NON-NLS-2$
                .withZ().locationButtons();
        if (probing) {
            b.section("NozzleTipForm.ZCalibration", "nozzle") //$NON-NLS-1$ //$NON-NLS-2$
                    .location("touchLocation", "ReferenceNozzleTipToolChangerWizard.ChangerPanel.TouchLocationLabel.text", false) //$NON-NLS-1$ //$NON-NLS-2$
                    .withZ().locationButtons()
                    .hint("NozzleTipForm.Touch.Hint") //$NON-NLS-1$
                    .choice("ZCalibrationTrigger", "ReferenceNozzleTipToolChangerWizard.ChangerPanel.ZCalibrateLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                            ZCalibrationTrigger.class)
                    .toggle("ZCalibrationFailHoming", "NozzleTipForm.Runout.FailHoming", "NozzleTipForm.Runout.FailHoming.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    .visibleWhen("ZCalibrationTrigger", v -> v != ZCalibrationTrigger.Manual) //$NON-NLS-1$
                    .readOnly("calibrationOffsetZ", "NozzleTipForm.ZCalibration.Offset") //$NON-NLS-1$ //$NON-NLS-2$
                    .action("NozzleTipForm.ZCalibration.Now", "target", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                        form[0].apply();
                        UiUtils.submitUiMachineTask(() -> {
                            ReferenceNozzle nozzle = contactProbe(tip);
                            ((ContactProbeNozzle) nozzle).calibrateZ(tip);
                            nozzle.moveToSafeZ();
                        });
                    }).movesMachine()
                    .action("NozzleTipForm.ZCalibration.Reset", "undo", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                        form[0].apply();
                        UiUtils.messageBoxOnException(() -> ((ContactProbeNozzle) contactProbe(tip)).resetZCalibration());
                    });
        }
        b.section("ReferenceNozzleTipToolChangerWizard.VisionCalibrationPanel.Border.title", "camera") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("visionCalibration", "ReferenceNozzleTipToolChangerWizard.VisionCalibrationPanel.VisionLocationLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        VisionCalibration.class)
                .hint("NozzleTipForm.Vision.Hint") //$NON-NLS-1$
                .length("visionCalibrationZAdjust", "NozzleTipForm.Vision.AdjustZ").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("visionCalibration", NozzleTipForm::calibrates) //$NON-NLS-1$
                .hint("NozzleTipForm.Vision.AdjustZ.Hint") //$NON-NLS-1$
                .choice("visionCalibrationTrigger", "ReferenceNozzleTipToolChangerWizard.VisionCalibrationPanel.CalibrationTriggerLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        VisionCalibrationTrigger.class)
                .visibleWhen("visionCalibration", NozzleTipForm::calibrates) //$NON-NLS-1$
                .length("visionTemplateDimensionX", "ReferenceNozzleTipToolChangerWizard.VisionCalibrationPanel.TemplateWidthLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .width(120)
                .visibleWhen("visionCalibration", NozzleTipForm::calibrates) //$NON-NLS-1$
                .length("visionTemplateDimensionY", "ReferenceNozzleTipToolChangerWizard.VisionCalibrationPanel.TemplateHeightLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .width(120)
                .visibleWhen("visionCalibration", NozzleTipForm::calibrates) //$NON-NLS-1$
                .hint("NozzleTipForm.Vision.Template.Hint") //$NON-NLS-1$
                .length("visionTemplateTolerance", "ReferenceNozzleTipToolChangerWizard.VisionCalibrationPanel.ToleranceLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .width(120)
                .visibleWhen("visionCalibration", NozzleTipForm::calibrates) //$NON-NLS-1$
                .hint("ReferenceNozzleTipToolChangerWizard.VisionCalibrationPanel.ToleranceLabel.toolTipText") //$NON-NLS-1$
                .length("visionCalibrationTolerance", "ReferenceNozzleTipToolChangerWizard.VisionCalibrationPanel.WantedPrecisionLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .width(120)
                .visibleWhen("visionCalibration", NozzleTipForm::calibrates) //$NON-NLS-1$
                .hint("NozzleTipForm.Vision.Precision.Hint") //$NON-NLS-1$
                .integer("visionCalibrationMaxPasses", "NozzleTipForm.Vision.MaxPasses").unit("JobProcessorForm.Times").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen("visionCalibration", NozzleTipForm::calibrates) //$NON-NLS-1$
                .decimal("visionMatchMinimumScore", "ReferenceNozzleTipToolChangerWizard.VisionCalibrationPanel.MinScoreLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .width(120)
                .visibleWhen("visionCalibration", NozzleTipForm::calibrates) //$NON-NLS-1$
                .hint("NozzleTipForm.Vision.MinScore.Hint") //$NON-NLS-1$
                .readOnly("visionMatchLastScore", "ReferenceNozzleTipToolChangerWizard.VisionCalibrationPanel.LastScoreLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("visionCalibration", NozzleTipForm::calibrates) //$NON-NLS-1$
                .custom("ReferenceNozzleTipToolChangerWizard.VisionCalibrationPanel.TemplateEmptyLabel.text", //$NON-NLS-1$
                        template(empty, () -> form[0], tip, false))
                .visibleWhen("visionCalibration", NozzleTipForm::calibrates) //$NON-NLS-1$
                .custom("ReferenceNozzleTipToolChangerWizard.VisionCalibrationPanel.TemplateOccupiedLabel.text", //$NON-NLS-1$
                        template(occupied, () -> form[0], tip, true))
                .visibleWhen("visionCalibration", NozzleTipForm::calibrates) //$NON-NLS-1$
                .action("NozzleTipForm.Vision.Test", "play", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                    form[0].apply();
                    UiUtils.submitUiMachineTask(() -> {
                        tip.resetVisionCalibration();
                        tip.ensureVisionCalibration(true);
                        tip.resetVisionCalibration();
                    });
                }).movesMachine()
                .visibleWhen("visionCalibration", NozzleTipForm::calibrates) //$NON-NLS-1$
                .section("ReferenceNozzleTipToolChangerWizard.CloningSettingsPanel.Border.title", "copy").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .segmented("templateRole", "ReferenceNozzleTipToolChangerWizard.CloningSettingsPanel.BehaviorLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        Arrays.asList(TemplateRole.values()))
                .hint("NozzleTipForm.Template.Hint") //$NON-NLS-1$
                .toggle("cloneLocations", "NozzleTipForm.Clone.Locations", "NozzleTipForm.Clone.Locations.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen("templateRole", v -> v != TemplateRole.Locked) //$NON-NLS-1$
                .toggle("cloneZCalibration", "NozzleTipForm.Clone.ZCalibration", "NozzleTipForm.Clone.ZCalibration.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen("templateRole", v -> v != TemplateRole.Locked) //$NON-NLS-1$
                .toggle("cloneVisionCalibration", "NozzleTipForm.Clone.Vision", "NozzleTipForm.Clone.Vision.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen("templateRole", v -> v != TemplateRole.Locked) //$NON-NLS-1$
                .action("NozzleTipForm.Clone.FromTemplate", "download", () -> cloneFromTemplate(form[0], tip)) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("templateRole", v -> v == TemplateRole.Clone) //$NON-NLS-1$
                .action("NozzleTipForm.Clone.ToAll", "upload", () -> cloneToAll(form[0], tip)) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("templateRole", v -> v == TemplateRole.Template) //$NON-NLS-1$
                .action("NozzleTipForm.Clone.ReferenceZ", "nozzle", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                    form[0].apply();
                    UiUtils.submitUiMachineTask(() -> ContactProbeNozzle.referenceAllTouchLocationsZ(tip.getMachine()));
                }).movesMachine()
                .visibleWhen("templateRole", v -> probing && v == TemplateRole.Template); //$NON-NLS-1$
        form[0] = b.build();
        return form[0];
    }

    private static boolean calibrates(Object location) {
        return location != null && location != VisionCalibration.None;
    }

    private static ReferenceNozzle contactProbe(ReferenceNozzleTip tip) throws Exception {
        ReferenceNozzle nozzle = uiCalibrationNozzle(tip);
        if (!(nozzle instanceof ContactProbeNozzle)) {
            throw new Exception(String.format(Translations.getString("NozzleTipForm.NotContactProbe"), nozzle.getName())); //$NON-NLS-1$
        }
        return nozzle;
    }

    /** A template image of the slot, with the buttons that take it from the camera and clear it. */
    private static JComponent template(TemplateImageControl control, java.util.function.Supplier<FormWizard> form,
            ReferenceNozzleTip tip, boolean occupied) {
        JButton capture = Ui.button(Translations.getString("NozzleTipForm.Vision.Capture"), Ui.iconSm("camera"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Default);
        Ui.movesMachine(capture);
        capture.addActionListener(e -> {
            form.get().apply();
            UiUtils.submitUiMachineTask(() -> {
                TemplateImage image = new TemplateImage(captureTemplateImage(tip));
                if (occupied) {
                    tip.setVisionTemplateImageOccupied(image);
                }
                else {
                    tip.setVisionTemplateImageEmpty(image);
                }
            });
        });
        JButton clear = Ui.button(Translations.getString("NozzleTipForm.Vision.Clear"), null, //$NON-NLS-1$
                Ui.Size.Sm, Ui.Variant.Ghost);
        clear.addActionListener(e -> {
            if (occupied) {
                tip.setVisionTemplateImageOccupied(null);
            }
            else {
                tip.setVisionTemplateImageEmpty(null);
            }
        });
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        control.setAlignmentX(0f);
        panel.add(control);
        panel.add(Box.createVerticalStrut(6));
        JPanel buttons = Forms.row(capture, clear);
        buttons.add(Box.createHorizontalGlue());
        buttons.setAlignmentX(0f);
        panel.add(buttons);
        return panel;
    }

    /** The slot at the vision location, from the camera of the selected tool's head. */
    private static BufferedImage captureTemplateImage(ReferenceNozzleTip tip) throws Exception {
        Location location = tip.getVisionCalibration().getLocation(tip);
        if (location == null) {
            throw new Exception(Translations.getString("NozzleTipForm.Vision.LocationFirst")); //$NON-NLS-1$
        }
        location = location.add(new Location(tip.getVisionCalibrationZAdjust().getUnits(), 0, 0,
                tip.getVisionCalibrationZAdjust().getValue(), 0));
        Camera camera;
        try {
            camera = MainFrame.get().getMachineControls().getSelectedTool().getHead().getDefaultCamera();
        }
        catch (Exception e) {
            camera = null;
        }
        if (camera == null) {
            throw new Exception(Translations.getString("NozzleTipForm.Vision.NoCamera")); //$NON-NLS-1$
        }
        MovableUtils.moveToLocationAtSafeZ(camera, location);
        BufferedImage image = camera.lightSettleAndCapture();
        Location upp = camera.getUnitsPerPixelAtZ();
        int width = ((int) Math.ceil(tip.getVisionTemplateDimensionX().divide(upp.getLengthX()))) & ~1;
        int height = ((int) Math.ceil(tip.getVisionTemplateDimensionY().divide(upp.getLengthY()))) & ~1;
        return image.getSubimage((image.getWidth() - width) / 2, (image.getHeight() - height) / 2, width, height);
    }

    private static void cloneFromTemplate(FormWizard form, ReferenceNozzleTip tip) {
        form.apply();
        UiUtils.messageBoxOnException(() -> {
            if (!tip.getChangerStartLocation().isInitialized()) {
                throw new Exception(String.format(Translations.getString("NozzleTipForm.Clone.FirstLocationFirst"), //$NON-NLS-1$
                        tip.getName()));
            }
            ReferenceNozzleTip template = ReferenceNozzleTip.getTemplateNozzleTip(tip.getMachine());
            tip.assignNozzleTipChangerSettings(template, Boolean.TRUE.equals(form.value("cloneLocations")), //$NON-NLS-1$
                    Boolean.TRUE.equals(form.value("cloneZCalibration")), //$NON-NLS-1$
                    Boolean.TRUE.equals(form.value("cloneVisionCalibration"))); //$NON-NLS-1$
            form.reload();
        });
    }

    private static void cloneToAll(FormWizard form, ReferenceNozzleTip tip) {
        form.apply();
        UiUtils.messageBoxOnException(() -> {
            for (NozzleTip other : tip.getMachine().getNozzleTips()) {
                if (other != tip && other instanceof ReferenceNozzleTip) {
                    ((ReferenceNozzleTip) other).assignNozzleTipChangerSettings(tip,
                            Boolean.TRUE.equals(form.value("cloneLocations")), //$NON-NLS-1$
                            Boolean.TRUE.equals(form.value("cloneZCalibration")), //$NON-NLS-1$
                            Boolean.TRUE.equals(form.value("cloneVisionCalibration"))); //$NON-NLS-1$
                }
            }
            MainFrame.get().setStatus(Translations.getString("NozzleTipForm.Clone.ToAll.Done")); //$NON-NLS-1$
        });
    }
}
