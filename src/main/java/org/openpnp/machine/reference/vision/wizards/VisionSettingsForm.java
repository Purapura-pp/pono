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

package org.openpnp.machine.reference.vision.wizards;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.components.PipelineControls;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.machine.reference.vision.AbstractPartAlignment;
import org.openpnp.machine.reference.vision.ReferenceBottomVision;
import org.openpnp.machine.reference.vision.ReferenceFiducialLocator;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.AbstractVisionSettings;
import org.openpnp.model.BottomVisionSettings;
import org.openpnp.model.Configuration;
import org.openpnp.model.FiducialVisionSettings;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Package;
import org.openpnp.model.Part;
import org.openpnp.model.PartSettingsHolder;
import org.openpnp.model.Placement;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.NozzleTip;
import org.openpnp.spi.PartAlignment;
import org.openpnp.util.UiUtils;
import org.openpnp.util.VisionUtils;
import org.openpnp.vision.pipeline.CvPipeline;

/**
 * Bottom vision and fiducial vision settings, as mockup 11 draws the first: what they are called,
 * what uses them and whether they are on; the pipeline with its stages and parameters; how the
 * part is lined up and its size checked; and a test, which moves the machine. The wizards they
 * replace were a grid of labels and fields in titled boxes, with "Specialize", "Generalize" and
 * "Reset" in a row whatever they applied to.
 * <p>
 * The same forms serve the vision page, where the settings are shown by themselves, a part's and a
 * package's properties, where they are the ones the part or package uses, and the machine's bottom
 * vision and fiducial locator, where they are the defaults. What can be done with them depends on
 * where they are shown: settings can be made a part's own only where there is a part.
 * <p>
 * Built-in settings are shown and not changed.
 */
public final class VisionSettingsForm {
    private VisionSettingsForm() {
    }

    /** What both kinds have: a name, what uses them, whether they are on. */
    public abstract static class SettingsBean extends AbstractModelObject {
        private final AbstractVisionSettings base;
        protected final PartSettingsHolder holder;

        SettingsBean(AbstractVisionSettings base, PartSettingsHolder holder) {
            this.base = base;
            this.holder = holder;
        }

        public String getName() {
            return base.getName();
        }

        public void setName(String name) {
            base.setName(name);
        }

        /** The name of built-in settings in the user's language, "- Stock ... -" as it is saved. */
        public String getDisplayName() {
            return org.openpnp.gui.support.DisplayNames.visionSettingsName(base.getName());
        }

        public boolean isEnabled() {
            return base.isEnabled();
        }

        public void setEnabled(boolean enabled) {
            base.setEnabled(enabled);
        }

        public String getEnabledText() {
            return Translations.getString(base.isEnabled() ? "VisionSettingsForm.Enabled.On" //$NON-NLS-1$
                    : "VisionSettingsForm.Enabled.Off"); //$NON-NLS-1$
        }

        public abstract String getUsedIn();
    }

    // ---- bottom vision -------------------------------------------------------------------------

    public static class BottomBean extends SettingsBean {
        private final BottomVisionSettings settings;
        private final ReferenceBottomVision bottomVision;
        private boolean centerAfterTest = true;

        BottomBean(BottomVisionSettings settings, ReferenceBottomVision bottomVision, PartSettingsHolder holder) {
            super(settings, holder);
            this.settings = settings;
            this.bottomVision = bottomVision;
        }

        @Override
        public String getUsedIn() {
            return usedIn(settings.getUsedBottomVisionIn(), holder);
        }

        public BottomVisionSettings.PreRotateUsage getPreRotateUsage() {
            return settings.getPreRotateUsage();
        }

        public void setPreRotateUsage(BottomVisionSettings.PreRotateUsage usage) {
            settings.setPreRotateUsage(usage);
        }

        public BottomVisionSettings.MaxRotation getMaxRotation() {
            return settings.getMaxRotation();
        }

        public void setMaxRotation(BottomVisionSettings.MaxRotation rotation) {
            settings.setMaxRotation(rotation);
        }

        public BottomVisionSettings.PartSizeCheckMethod getCheckPartSizeMethod() {
            return settings.getCheckPartSizeMethod();
        }

        public void setCheckPartSizeMethod(BottomVisionSettings.PartSizeCheckMethod method) {
            settings.setCheckPartSizeMethod(method);
        }

        public int getCheckSizeTolerancePercent() {
            return settings.getCheckSizeTolerancePercent();
        }

        public void setCheckSizeTolerancePercent(int percent) {
            settings.setCheckSizeTolerancePercent(percent);
        }

        public boolean isAsymmetric() {
            return settings.isAsymmetric();
        }

        public void setAsymmetric(boolean asymmetric) {
            settings.setAsymmetric(asymmetric);
        }

        public String getAsymmetricText() {
            return Translations.getString(settings.isAsymmetric() ? "VisionSettingsForm.Yes" //$NON-NLS-1$
                    : "VisionSettingsForm.No"); //$NON-NLS-1$
        }

        public Location getVisionOffset() {
            return settings.getVisionOffset();
        }

        public void setVisionOffset(Location offset) {
            settings.setVisionOffset(offset);
        }

        /** The machine's, not the settings': the angle every test of bottom vision turns the part to. */
        public double getTestAlignmentAngle() {
            return bottomVision == null ? 0 : bottomVision.getTestAlignmentAngle();
        }

        public void setTestAlignmentAngle(double angle) {
            if (bottomVision != null) {
                bottomVision.setTestAlignmentAngle(angle);
            }
        }

        public boolean isCenterAfterTest() {
            return centerAfterTest;
        }

        public void setCenterAfterTest(boolean center) {
            this.centerAfterTest = center;
        }
    }

    public static FormWizard bottom(Configuration configuration, BottomVisionSettings settings,
            PartSettingsHolder holder) {
        ReferenceBottomVision bottomVision =
                (ReferenceBottomVision) AbstractPartAlignment.getPartAlignment(holder, true);
        BottomBean bean = new BottomBean(settings, bottomVision, holder);
        boolean stock = settings.isStockSetting();
        FormWizard[] form = new FormWizard[1];

        PipelineControls pipeline = new PipelineControls() {
            @Override
            public void configurePipeline(CvPipeline cvPipeline, Map<String, Object> assignments, boolean edit)
                    throws Exception {
                UiUtils.messageBoxOnException(() -> {
                    if (edit) {
                        form[0].apply();
                    }
                    configureBottomPipeline(this, bottomVision, settings, holder, bean.getTestAlignmentAngle(),
                            cvPipeline, assignments, edit);
                });
            }

            @Override
            public Camera getCamera() throws Exception {
                return VisionUtils.getBottomVisionCamera();
            }

            @Override
            public void resetPipeline() throws Exception {
                if (confirm("VisionSettingsForm.ResetPipeline")) { //$NON-NLS-1$
                    UiUtils.messageBoxOnException(() -> {
                        form[0].apply();
                        ReferenceBottomVision root = ReferenceBottomVision.getDefault();
                        setPipeline(root.getBottomVisionSettings() == settings
                                ? ReferenceBottomVision.createStockPipeline("Default") //$NON-NLS-1$
                                : root.getBottomVisionSettings().getPipeline().clone());
                    });
                }
            }
        };
        writeThrough(configuration, settings, pipeline, stock);

        // Named for what it is: the name is the title of its tab beside a part's other sheets.
        Form.Builder b = Form.of(bean).named("BottomVisionSettingsConfigurationWizard.wizardName"); //$NON-NLS-1$
        basics(b, stock, "VisionSettingsForm.Enabled.Bottom"); //$NON-NLS-1$
        manage(b, configuration, form, settings, holder, stock, bottomVision == null ? null
                : bottomVision.getParentHolder(holder), true);
        b.section("VisionSettingsForm.Pipeline", "activity") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", pipeline); //$NON-NLS-1$
        b.section("VisionSettingsForm.Align", "move"); //$NON-NLS-1$ //$NON-NLS-2$
        if (stock) {
            b.readOnly("preRotateUsage", "VisionSettingsForm.PreRotate") //$NON-NLS-1$ //$NON-NLS-2$
                    .readOnly("maxRotation", "VisionSettingsForm.Rotation") //$NON-NLS-1$ //$NON-NLS-2$
                    .readOnly("asymmetricText", "VisionSettingsForm.Asymmetric"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        else {
            b.choice("preRotateUsage", "VisionSettingsForm.PreRotate", BottomVisionSettings.PreRotateUsage.class) //$NON-NLS-1$ //$NON-NLS-2$
                    .choice("maxRotation", "VisionSettingsForm.Rotation", BottomVisionSettings.MaxRotation.class) //$NON-NLS-1$ //$NON-NLS-2$
                    .note("VisionSettingsForm.Rotation.Note") //$NON-NLS-1$
                    .toggle("asymmetric", "VisionSettingsForm.Asymmetric", "VisionSettingsForm.Asymmetric.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    .location("visionOffset", "VisionSettingsForm.VisionOffset", false) //$NON-NLS-1$ //$NON-NLS-2$
                    .visibleWhen("asymmetric", Boolean.TRUE::equals) //$NON-NLS-1$
                    .action("VisionSettingsForm.DetectOffset", "target", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                        form[0].apply();
                        UiUtils.submitUiMachineTask(() -> {
                            Location offset = detectVisionOffset(bottomVision, settings, holder);
                            javax.swing.SwingUtilities.invokeLater(() -> {
                                settings.setVisionOffset(offset);
                                if (configuration != null) {
                                    configuration.setDirty(true);
                                }
                                form[0].reload();
                            });
                        });
                    }).movesMachine().visibleWhen("asymmetric", Boolean.TRUE::equals); //$NON-NLS-1$
        }
        b.section("VisionSettingsForm.SizeCheck", "ruler"); //$NON-NLS-1$ //$NON-NLS-2$
        if (stock) {
            b.readOnly("checkPartSizeMethod", "VisionSettingsForm.SizeCheckMethod") //$NON-NLS-1$ //$NON-NLS-2$
                    .readOnly("checkSizeTolerancePercent", "VisionSettingsForm.SizeTolerance"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        else {
            b.choice("checkPartSizeMethod", "VisionSettingsForm.SizeCheckMethod", //$NON-NLS-1$ //$NON-NLS-2$
                    BottomVisionSettings.PartSizeCheckMethod.class)
                    .integer("checkSizeTolerancePercent", "VisionSettingsForm.SizeTolerance").unit("%").width(100); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        if (bottomVision != null) {
            b.section("VisionSettingsForm.Test", "target").note("VisionSettingsForm.MovesMachine") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    .decimal("testAlignmentAngle", "VisionSettingsForm.TestAngle").unit("\u00b0").width(100) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    .toggle("centerAfterTest", "VisionSettingsForm.CenterAfterTest", //$NON-NLS-1$ //$NON-NLS-2$
                            "VisionSettingsForm.CenterAfterTest.Note") //$NON-NLS-1$
                    .action("VisionSettingsForm.TestAlignment", "nozzle", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                        form[0].apply();
                        boolean center = bean.isCenterAfterTest();
                        double angle = bean.getTestAlignmentAngle();
                        UiUtils.submitUiMachineTask(() -> {
                            Nozzle nozzle = nozzleWithPart(bottomVision, settings, holder);
                            alignAndCenter(bottomVision, nozzle, angle, center);
                        });
                    }).movesMachine();
        }
        form[0] = b.build();
        return form[0];
    }

    /** The pipeline with what it is prepared with when it runs, and its editor with the nozzle moved over the camera. */
    private static void configureBottomPipeline(PipelineControls controls, ReferenceBottomVision bottomVision,
            BottomVisionSettings settings, PartSettingsHolder holder, double angle, CvPipeline pipeline,
            Map<String, Object> assignments, boolean edit) throws Exception {
        Camera camera = VisionUtils.getBottomVisionCamera();
        Nozzle nozzle = MainFrame.get().getMachineControls().getSelectedNozzle();
        Part part = nozzle.getPart();
        Package pkg = null;
        String from = null;
        if (part != null) {
            pkg = part.getPackage();
            from = String.format(Translations.getString("VisionSettingsForm.PackageFrom.Nozzle"), //$NON-NLS-1$
                    part.getId(), nozzle.getName());
        }
        else if (holder instanceof Part) {
            part = (Part) holder;
            pkg = part.getPackage();
            from = String.format(Translations.getString("VisionSettingsForm.PackageFrom.Part"), part.getId()); //$NON-NLS-1$
        }
        else if (holder instanceof Package) {
            pkg = (Package) holder;
            from = Translations.getString("VisionSettingsForm.PackageFrom.Here"); //$NON-NLS-1$
        }
        else if (MainFrame.get().getPartsTab().getSelectedPart() != null) {
            part = MainFrame.get().getPartsTab().getSelectedPart();
            pkg = part.getPackage();
            from = String.format(Translations.getString("VisionSettingsForm.PackageFrom.PartsPage"), part.getId()); //$NON-NLS-1$
        }
        else if (MainFrame.get().getPackagesTab().getSelectedPackage() != null) {
            pkg = MainFrame.get().getPackagesTab().getSelectedPackage();
            from = Translations.getString("VisionSettingsForm.PackageFrom.PackagesPage"); //$NON-NLS-1$
        }
        if (pkg == null) {
            throw new Exception(String.format(Translations.getString("VisionSettingsForm.Error.NoPackage"), //$NON-NLS-1$
                    nozzle.getName()));
        }
        NozzleTip tip = nozzle.getNozzleTip();
        if (tip == null) {
            throw new Exception(String.format(Translations.getString("VisionSettingsForm.Error.NoNozzleTip"), //$NON-NLS-1$
                    nozzle.getName()));
        }
        if (!pkg.getCompatibleNozzleTips().contains(tip)) {
            throw new Exception(String.format(Translations.getString("VisionSettingsForm.Error.TipCannotPick"), //$NON-NLS-1$
                    tip.getName(), nozzle.getName(), pkg.getId(), from));
        }
        Location location = bottomVision.getCameraLocationAtPartHeight(part, camera, nozzle, angle);
        bottomVision.preparePipeline(pipeline, assignments, camera, pkg, nozzle, tip, location, location,
                settings);
        if (edit) {
            controls.openPipelineEditor(Translations.getString("BottomVisionSettingsConfigurationWizard.PipelineTitle"), //$NON-NLS-1$
                    pipeline,
                    String.format(Translations.getString("BottomVisionSettingsConfigurationWizard.MoveNozzleToAlignment"), //$NON-NLS-1$
                            nozzle.getName()),
                    nozzle, location);
        }
    }

    /** The selected nozzle, if it holds a part these settings are the ones for. */
    private static Nozzle nozzleWithPart(ReferenceBottomVision bottomVision, BottomVisionSettings settings,
            PartSettingsHolder holder) throws Exception {
        Nozzle nozzle = MainFrame.get().getMachineControls().getSelectedNozzle();
        Part part = nozzle.getPart();
        if (part == null) {
            throw new Exception(String.format(Translations.getString("VisionSettingsForm.Error.NoPart"), //$NON-NLS-1$
                    nozzle.getName()));
        }
        if (holder instanceof Part && part != holder) {
            throw new Exception(String.format(Translations.getString("VisionSettingsForm.Error.WrongPart"), //$NON-NLS-1$
                    nozzle.getName(), part.getId(), ((Part) holder).getId()));
        }
        if (holder instanceof Package && part.getPackage() != holder) {
            throw new Exception(String.format(Translations.getString("VisionSettingsForm.Error.WrongPackage"), //$NON-NLS-1$
                    nozzle.getName(), part.getPackage().getId(), ((Package) holder).getId()));
        }
        if (bottomVision == null) {
            throw new Exception(Translations.getString("VisionSettingsForm.Error.NoBottomVision")); //$NON-NLS-1$
        }
        if (bottomVision.getInheritedVisionSettings(part) != settings) {
            throw new Exception(String.format(Translations.getString("VisionSettingsForm.Error.NotTheseSettings"), //$NON-NLS-1$
                    part.getId()));
        }
        return nozzle;
    }

    /**
     * Runs bottom vision on the part the nozzle holds and, if asked, turns and moves the part so
     * that it sits centred over the camera at the angle, as it would be placed, and shows it.
     */
    static void alignAndCenter(ReferenceBottomVision bottomVision, Nozzle nozzle, double angle,
            boolean centerAfterTest) throws Exception {
        Camera camera = VisionUtils.getBottomVisionCamera();
        Placement dummy = new Placement("Dummy"); //$NON-NLS-1$
        dummy.setLocation(new Location(LengthUnit.Millimeters, 0, 0, 0, angle));
        Double rotationBefore = nozzle.getRotationModeOffset();
        PartAlignment.PartAlignmentOffset alignmentOffset =
                VisionUtils.findPartAlignmentOffsets(bottomVision, nozzle.getPart(), null, dummy, nozzle);
        Location offsets = alignmentOffset.getLocation();
        Double rotationAfter = nozzle.getRotationModeOffset();
        if (!centerAfterTest) {
            return;
        }
        // Where the part would be centred over the camera.
        Location cameraLocation = bottomVision.getCameraLocationAtPartHeight(nozzle.getPart(), camera, nozzle, angle);
        Location centeredLocation;
        if (alignmentOffset.getPreRotated()) {
            centeredLocation = cameraLocation.subtractWithRotation(alignmentOffset.getLocation());
        }
        else {
            // Turned afterwards: rotate the origin about the offsets by the difference between
            // the angle vision saw and the placement's, take that angle, move it to the camera,
            // and take the offsets off to have the part there rather than the nozzle.
            centeredLocation = new Location(LengthUnit.Millimeters).rotateXyCenterPoint(offsets,
                    cameraLocation.getRotation() - offsets.getRotation());
            centeredLocation = centeredLocation.derive(null, null, null,
                    cameraLocation.getRotation() - offsets.getRotation());
            centeredLocation = centeredLocation.add(cameraLocation);
            centeredLocation = centeredLocation.subtract(offsets);
        }
        nozzle.moveTo(centeredLocation);
        BufferedImage image = camera.lightSettleAndCapture();
        // A moment for the result of the last pass to be seen.
        Thread.sleep(500);
        double rotationOffset = (rotationAfter != null ? rotationAfter : 0)
                - (rotationBefore != null ? rotationBefore : 0);
        Location shown = offsets.deriveLengths(null, null, null, offsets.getRotation() + rotationOffset);
        bottomVision.displayResult(image, nozzle.getPart(), shown, camera, nozzle);
    }

    /** The offset of an asymmetric part: where the user centred it, against where vision centres it. */
    private static Location detectVisionOffset(ReferenceBottomVision bottomVision, BottomVisionSettings settings,
            PartSettingsHolder holder) throws Exception {
        Nozzle nozzle = nozzleWithPart(bottomVision, settings, holder);
        Location center = nozzle.getLocation();
        alignAndCenter(bottomVision, nozzle, 0.0, true);
        return center.subtract(nozzle.getLocation()).add(settings.getVisionOffset());
    }

    // ---- fiducial vision -----------------------------------------------------------------------

    public static class FiducialBean extends SettingsBean {
        private final FiducialVisionSettings settings;

        FiducialBean(FiducialVisionSettings settings, PartSettingsHolder holder) {
            super(settings, holder);
            this.settings = settings;
        }

        @Override
        public String getUsedIn() {
            return usedIn(settings.getUsedFiducialVisionIn(), holder);
        }

        public int getMaxVisionPasses() {
            return settings.getMaxVisionPasses();
        }

        public void setMaxVisionPasses(int passes) {
            settings.setMaxVisionPasses(passes);
        }

        public Length getMaxLinearOffset() {
            return settings.getMaxLinearOffset();
        }

        public void setMaxLinearOffset(Length offset) {
            settings.setMaxLinearOffset(offset);
        }

        public Length getParallaxDiameter() {
            return settings.getParallaxDiameter();
        }

        public void setParallaxDiameter(Length diameter) {
            settings.setParallaxDiameter(diameter);
        }

        public double getParallaxAngle() {
            return settings.getParallaxAngle();
        }

        public void setParallaxAngle(double angle) {
            settings.setParallaxAngle(angle);
        }
    }

    public static FormWizard fiducial(Configuration configuration, FiducialVisionSettings settings,
            PartSettingsHolder holder) {
        ReferenceFiducialLocator locator = configuration == null || configuration.getMachine() == null
                || !(configuration.getMachine().getFiducialLocator() instanceof ReferenceFiducialLocator) ? null
                        : (ReferenceFiducialLocator) configuration.getMachine().getFiducialLocator();
        FiducialBean bean = new FiducialBean(settings, holder);
        boolean stock = settings.isStockSetting();
        FormWizard[] form = new FormWizard[1];

        PipelineControls pipeline = new PipelineControls() {
            @Override
            public void configurePipeline(CvPipeline cvPipeline, Map<String, Object> assignments, boolean edit)
                    throws Exception {
                UiUtils.messageBoxOnException(() -> {
                    if (edit) {
                        form[0].apply();
                    }
                    locator.preparePipeline(getPipeline(), getPipelineParameterAssignments(),
                            locator.getVisionCamera(), holder, Location.origin);
                    if (edit) {
                        openPipelineEditor(Translations.getString("FiducialVisionSettingsConfigurationWizard.PipelineTitle"), //$NON-NLS-1$
                                getPipeline());
                    }
                });
            }

            @Override
            public Camera getCamera() throws Exception {
                return locator.getVisionCamera();
            }

            @Override
            public void resetPipeline() throws Exception {
                if (confirm("VisionSettingsForm.ResetPipeline")) { //$NON-NLS-1$
                    UiUtils.messageBoxOnException(() -> {
                        form[0].apply();
                        setPipeline(locator.getFiducialVisionSettings() == settings
                                ? ReferenceFiducialLocator.createStockPipeline("Default") //$NON-NLS-1$
                                : locator.getFiducialVisionSettings().getPipeline().clone());
                    });
                }
            }
        };
        writeThrough(configuration, settings, pipeline, stock || locator == null);

        Form.Builder b = Form.of(bean).named("FiducialVisionSettingsConfigurationWizard.wizardName"); //$NON-NLS-1$
        basics(b, stock, "VisionSettingsForm.Enabled.Fiducial"); //$NON-NLS-1$
        manage(b, configuration, form, settings, holder, stock, locator == null ? null
                : locator.getParentHolder(holder), false);
        b.section("VisionSettingsForm.Pipeline", "activity") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", pipeline); //$NON-NLS-1$
        b.section("VisionSettingsForm.Locate", "crosshair"); //$NON-NLS-1$ //$NON-NLS-2$
        if (stock) {
            b.readOnly("maxVisionPasses", "VisionSettingsForm.MaxPasses") //$NON-NLS-1$ //$NON-NLS-2$
                    .readOnly("maxLinearOffset", "VisionSettingsForm.MaxOffset") //$NON-NLS-1$ //$NON-NLS-2$
                    .readOnly("parallaxDiameter", "VisionSettingsForm.ParallaxDiameter") //$NON-NLS-1$ //$NON-NLS-2$
                    .readOnly("parallaxAngle", "VisionSettingsForm.ParallaxAngle"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        else {
            b.integer("maxVisionPasses", "VisionSettingsForm.MaxPasses").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                    .note("VisionSettingsForm.MaxPasses.Note") //$NON-NLS-1$
                    .length("maxLinearOffset", "VisionSettingsForm.MaxOffset").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                    .note("VisionSettingsForm.MaxOffset.Note") //$NON-NLS-1$
                    .length("parallaxDiameter", "VisionSettingsForm.ParallaxDiameter").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                    .note("VisionSettingsForm.ParallaxDiameter.Note") //$NON-NLS-1$
                    .decimal("parallaxAngle", "VisionSettingsForm.ParallaxAngle").unit("\u00b0").width(100); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        // The test finds the fiducial by its footprint, which a part or a package has.
        if (locator != null && (holder instanceof Part || holder instanceof Package)) {
            b.section("VisionSettingsForm.Test", "target").note("VisionSettingsForm.MovesMachine") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    .action("VisionSettingsForm.TestFiducial", "camera", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                        form[0].apply();
                        UiUtils.submitUiMachineTask(() -> {
                            Camera camera = locator.getVisionCamera();
                            camera.moveTo(locator.getFiducialLocation(camera.getLocation(), holder));
                        });
                    }).movesMachine();
        }
        form[0] = b.build();
        return form[0];
    }

    // ---- shared --------------------------------------------------------------------------------

    private static void basics(Form.Builder b, boolean stock, String enabledNote) {
        b.section("VisionSettingsForm.Basics", "info"); //$NON-NLS-1$ //$NON-NLS-2$
        if (stock) {
            b.note("VisionSettingsForm.Stock") //$NON-NLS-1$
                    .readOnly("displayName", "VisionSettingsForm.Name") //$NON-NLS-1$ //$NON-NLS-2$
                    .readOnly("usedIn", "VisionSettingsForm.UsedIn") //$NON-NLS-1$ //$NON-NLS-2$
                    .readOnly("enabledText", "VisionSettingsForm.Enabled"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        else {
            b.text("name", "VisionSettingsForm.Name") //$NON-NLS-1$ //$NON-NLS-2$
                    .readOnly("usedIn", "VisionSettingsForm.UsedIn") //$NON-NLS-1$ //$NON-NLS-2$
                    .toggle("enabled", "VisionSettingsForm.Enabled", enabledNote); //$NON-NLS-1$
        }
    }

    /**
     * Making the settings a part's or a package's own, optimizing them where they are the
     * machine's defaults, taking a package's parts' own settings back, and resetting them to the
     * defaults - each only where it applies. The row of three buttons showed all of them, greyed
     * where they did not apply.
     * 
     * @param parent The holder the settings would be inherited from; null where they are shown by
     *               themselves or as the machine's.
     */
    private static void manage(Form.Builder b, Configuration configuration, FormWizard[] form,
            AbstractVisionSettings settings, PartSettingsHolder holder, boolean stock, PartSettingsHolder parent,
            boolean bottom) {
        boolean specialize = holder != null && parent != null;
        boolean optimize = holder != null && parent == null;
        boolean generalize = holder != null && !(holder instanceof Part);
        if (!specialize && !optimize && !generalize && stock) {
            return;
        }
        b.section("VisionSettingsForm.Manage", "layers").collapsed(); //$NON-NLS-1$ //$NON-NLS-2$
        if (specialize) {
            b.note(String.format(Translations.getString("VisionSettingsForm.Specialize.Note"), holder.getShortName())) //$NON-NLS-1$
                    .action(String.format(Translations.getString("VisionSettingsForm.Specialize"), //$NON-NLS-1$
                            holder.getShortName()), "copy", () -> { //$NON-NLS-1$
                                form[0].apply();
                                UiUtils.messageBoxOnException(() -> specialize(configuration, settings, holder, bottom));
                            });
        }
        if (optimize) {
            b.action("VisionSettingsForm.Optimize", "zap", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                form[0].apply();
                UiUtils.messageBoxOnException(() -> {
                    if (bottom) {
                        ReferenceBottomVision.getDefault().optimizeVisionSettings(configuration);
                    }
                    else {
                        ReferenceFiducialLocator.getDefault().optimizeVisionSettings(configuration);
                    }
                    configuration.fireVisionSettingsChanged();
                });
            });
        }
        if (generalize) {
            b.action(String.format(Translations.getString("VisionSettingsForm.Generalize"), holder.getShortName()), //$NON-NLS-1$
                    "undo", () -> UiUtils.messageBoxOnException(() -> generalize(holder, bottom))); //$NON-NLS-1$
        }
        if (!stock) {
            b.action("VisionSettingsForm.Reset", "refresh", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                if (!confirm("VisionSettingsForm.Reset")) { //$NON-NLS-1$
                    return;
                }
                UiUtils.messageBoxOnException(() -> {
                    if (bottom) {
                        BottomVisionSettings root = ReferenceBottomVision.getDefault().getBottomVisionSettings();
                        if (root == settings) {
                            settings.resetToDefault();
                        }
                        else {
                            ((BottomVisionSettings) settings).setValues(root);
                        }
                    }
                    else {
                        FiducialVisionSettings root = ReferenceFiducialLocator.getDefault().getFiducialVisionSettings();
                        if (root == settings) {
                            settings.resetToDefault();
                        }
                        else {
                            ((FiducialVisionSettings) settings).setValues(root);
                        }
                    }
                    form[0].reload();
                });
            });
        }
    }

    /** A copy of the settings, named for the part or package, and made its own. */
    private static void specialize(Configuration configuration, AbstractVisionSettings settings,
            PartSettingsHolder holder, boolean bottom) throws Exception {
        List<PartSettingsHolder> used = bottom ? settings.getUsedBottomVisionIn() : settings.getUsedFiducialVisionIn();
        if (used.size() == 1 && used.get(0) == holder) {
            throw new Exception(String.format(Translations.getString("VisionSettingsForm.Error.AlreadyOwn"), //$NON-NLS-1$
                    holder.getShortName()));
        }
        if (bottom) {
            BottomVisionSettings own = new BottomVisionSettings();
            own.setValues((BottomVisionSettings) settings);
            own.setName(holder.getShortName());
            holder.setBottomVisionSettings(own);
            configuration.addVisionSettings(own);
        }
        else {
            FiducialVisionSettings own = new FiducialVisionSettings();
            own.setValues((FiducialVisionSettings) settings);
            own.setName(holder.getShortName());
            holder.setFiducialVisionSettings(own);
            configuration.addVisionSettings(own);
        }
    }

    /** The package's parts, or everything, go back to inheriting these settings, after asking. */
    private static void generalize(PartSettingsHolder holder, boolean bottom) throws Exception {
        List<PartSettingsHolder> own = bottom ? holder.getSpecializedBottomVisionIn()
                : holder.getSpecializedFiducialVisionIn();
        if (own.isEmpty()) {
            throw new Exception(String.format(Translations.getString("VisionSettingsForm.Error.NothingOwn"), //$NON-NLS-1$
                    holder.getShortName()));
        }
        int chosen = Dialogs.ask(MainFrame.get(), Dialogs.Tone.Warn, "alert", //$NON-NLS-1$
                String.format(Translations.getString("VisionSettingsForm.Generalize.Title"), own.size()), //$NON-NLS-1$
                new AbstractVisionSettings.ListConverter(false).convertForward(own),
                Translations.getString("VisionSettingsForm.Generalize.More"), //$NON-NLS-1$
                Dialogs.Choice.primary(String.format(Translations.getString("VisionSettingsForm.Generalize"), //$NON-NLS-1$
                        holder.getShortName())));
        if (chosen != 0) {
            return;
        }
        if (bottom) {
            holder.generalizeBottomVisionSettings();
        }
        else {
            holder.generalizeFiducialVisionSettings();
        }
    }

    /**
     * The pipeline and its parameters are written as they change, as the wizard wrote the
     * pipeline: an edit in the pipeline editor and a slider moved over a live camera image are
     * not held back until Apply.
     */
    private static void writeThrough(Configuration configuration, AbstractVisionSettings settings,
            PipelineControls pipeline, boolean readOnly) {
        pipeline.setPipeline(settings.getPipeline());
        pipeline.setPipelineParameterAssignments(settings.getPipelineParameterAssignments());
        pipeline.setEditable(!readOnly);
        pipeline.setResetable(!readOnly);
        pipeline.setEnabled(!readOnly);
        pipeline.addPropertyChangeListener("pipeline", e -> { //$NON-NLS-1$
            if (e.getNewValue() instanceof CvPipeline) {
                settings.setPipeline((CvPipeline) e.getNewValue());
                if (configuration != null) {
                    configuration.setDirty(true);
                }
            }
        });
        pipeline.addPropertyChangeListener("pipelineParameterAssignments", e -> { //$NON-NLS-1$
            settings.setPipelineParameterAssignments(pipeline.getPipelineParameterAssignments());
            if (configuration != null) {
                configuration.setDirty(true);
            }
        });
    }

    /** What uses the settings, by name. */
    static String usedIn(List<PartSettingsHolder> used, PartSettingsHolder holder) {
        String names = used == null ? "" : org.openpnp.gui.support.DisplayNames.usedIn(used); //$NON-NLS-1$
        return names.isEmpty() ? Translations.getString("VisionSettingsForm.UsedIn.Nothing") : names; //$NON-NLS-1$
    }

    private static boolean confirm(String key) {
        return Dialogs.ask(MainFrame.get(), Dialogs.Tone.Warn, "alert", //$NON-NLS-1$
                Translations.getString(key + ".Title"), //$NON-NLS-1$
                Translations.getString(key + ".What"), null, //$NON-NLS-1$
                Dialogs.Choice.primary(Translations.getString(key + ".Action"))) == 0; //$NON-NLS-1$
    }
}
