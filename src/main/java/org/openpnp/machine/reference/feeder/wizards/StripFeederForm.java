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

package org.openpnp.machine.reference.feeder.wizards;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import javax.swing.JDialog;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.support.FeederDescriptions;
import org.openpnp.machine.reference.ReferenceFeeder;
import org.openpnp.machine.reference.feeder.ReferenceStripFeeder;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.Length;
import org.openpnp.model.Location;
import org.openpnp.model.Part;
import org.openpnp.spi.Camera;
import org.openpnp.util.UiUtils;
import org.openpnp.vision.pipeline.CvPipeline;
import org.openpnp.vision.pipeline.CvStage;
import org.openpnp.vision.pipeline.ui.CvPipelineEditor;
import org.openpnp.vision.pipeline.ui.CvPipelineEditorDialog;

/**
 * The strip feeder's properties as the mockup has them: what it is, the tape and where its holes
 * are, the vision that finds them, and how it feeds and what it has left.
 */
public final class StripFeederForm {
    private StripFeederForm() {
    }

    /**
     * What the form edits. The feeder's settings under the names the form gives them, and the
     * pick height, which the feeder keeps as the Z of its first reference hole, as a length of its
     * own: the holes are taken from the camera, the height from the nozzle.
     */
    public static class Bean extends AbstractModelObject {
        private final ReferenceStripFeeder feeder;

        Bean(ReferenceStripFeeder feeder) {
            this.feeder = feeder;
        }

        public String getName() {
            return feeder.getName();
        }

        public void setName(String name) {
            feeder.setName(name);
        }

        public String getSlotName() {
            return feeder.getSlotName();
        }

        public void setSlotName(String slotName) {
            feeder.setSlotName(slotName);
        }

        public Part getPart() {
            return feeder.getPart();
        }

        public void setPart(Part part) {
            feeder.setPart(part);
        }

        public boolean isEnabled() {
            return feeder.isEnabled();
        }

        public void setEnabled(boolean enabled) {
            feeder.setEnabled(enabled);
        }

        public Length getTapeWidth() {
            return feeder.getTapeWidth();
        }

        public void setTapeWidth(Length tapeWidth) {
            feeder.setTapeWidth(tapeWidth);
        }

        public Length getPartPitch() {
            return feeder.getPartPitch();
        }

        public void setPartPitch(Length partPitch) {
            feeder.setPartPitch(partPitch);
        }

        /** How the part sits in the tape, which the feeder keeps as the angle of its location. */
        public double getRotationInTape() {
            return feeder.getLocation().getRotation();
        }

        public void setRotationInTape(double rotation) {
            feeder.setLocation(feeder.getLocation().derive(null, null, null, rotation));
        }

        public Location getReferenceHole() {
            return feeder.getReferenceHoleLocation();
        }

        public void setReferenceHole(Location location) {
            Length oldZ = getPickZ();
            feeder.setReferenceHoleLocation(location);
            firePropertyChange("pickZ", oldZ, getPickZ()); //$NON-NLS-1$
        }

        public Location getLastHole() {
            return feeder.getLastHoleLocation();
        }

        public void setLastHole(Location location) {
            feeder.setLastHoleLocation(location);
        }

        public Length getPickZ() {
            return feeder.getReferenceHoleLocation().getLengthZ();
        }

        public void setPickZ(Length z) {
            Location old = feeder.getReferenceHoleLocation();
            feeder.setReferenceHoleLocation(old.deriveLengths(null, null, z, null));
            firePropertyChange("referenceHole", old, feeder.getReferenceHoleLocation()); //$NON-NLS-1$
        }

        public boolean isVisionEnabled() {
            return feeder.isVisionEnabled();
        }

        public void setVisionEnabled(boolean visionEnabled) {
            feeder.setVisionEnabled(visionEnabled);
        }

        public Length getExtrapolationDistance() {
            return feeder.getExtrapolationDistance();
        }

        public void setExtrapolationDistance(Length distance) {
            feeder.setExtrapolationDistance(distance);
        }

        public Length getParallaxDiameter() {
            return feeder.getParallaxDiameter();
        }

        public void setParallaxDiameter(Length diameter) {
            feeder.setParallaxDiameter(diameter);
        }

        public double getParallaxAngle() {
            return feeder.getParallaxAngle();
        }

        public void setParallaxAngle(double angle) {
            feeder.setParallaxAngle(angle);
        }

        public int getFeedCount() {
            return feeder.getFeedCount();
        }

        public void setFeedCount(int feedCount) {
            feeder.setFeedCount(feedCount);
        }

        public int getMaxFeedCount() {
            return feeder.getMaxFeedCount();
        }

        public void setMaxFeedCount(int maxFeedCount) {
            feeder.setMaxFeedCount(maxFeedCount);
        }

        public String getPartsLeft() {
            return FeederDescriptions.partsLeft(feeder);
        }

        public int getLowCount() {
            return feeder.getLowCount();
        }

        public void setLowCount(int lowCount) {
            feeder.setLowCount(lowCount);
        }

        public int getFeedRetryCount() {
            return feeder.getFeedRetryCount();
        }

        public void setFeedRetryCount(int count) {
            feeder.setFeedRetryCount(count);
        }

        public int getPickRetryCount() {
            return feeder.getPickRetryCount();
        }

        public void setPickRetryCount(int count) {
            feeder.setPickRetryCount(count);
        }

        public ReferenceFeeder.FeedOptions getFeedOptions() {
            return feeder.getFeedOptions();
        }

        public void setFeedOptions(ReferenceFeeder.FeedOptions options) {
            feeder.setFeedOptions(options);
        }
    }

    public static FormWizard build(ReferenceStripFeeder feeder) {
        Bean bean = new Bean(feeder);
        FormWizard[] form = new FormWizard[1];
        List<Part> parts = new ArrayList<>();
        if (feeder.getMachine() != null && feeder.getMachine().getConfiguration() != null) {
            parts.addAll(feeder.getMachine().getConfiguration().getParts());
        }
        parts.sort(Comparator.comparing(Part::getId, String.CASE_INSENSITIVE_ORDER));
        StripFeederAutoSetup[] setup = new StripFeederAutoSetup[1];

        Form.Builder builder = Form.of(bean).named(feeder.getName())
                .section("StripFeederForm.Basics", "info") //$NON-NLS-1$ //$NON-NLS-2$
                .text("name", "StripFeederForm.Name") //$NON-NLS-1$ //$NON-NLS-2$
                .text("slotName", "StripFeederForm.Slot").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("part", "StripFeederForm.Part", parts, Part::getName) //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("enabled", "StripFeederForm.Enabled", "StripFeederForm.Enabled.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

                .section("StripFeederForm.Tape", "move").note("mm") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .length("tapeWidth", "StripFeederForm.TapeWidth").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .presets(Arrays.asList(8, 12, 16, 24))
                .length("partPitch", "StripFeederForm.PartPitch").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .presets(Arrays.asList(2, 4, 8))
                .angle("rotationInTape", "StripFeederForm.RotationInTape").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .location("referenceHole", "StripFeederForm.ReferenceHole", false).capture() //$NON-NLS-1$ //$NON-NLS-2$
                .location("lastHole", "StripFeederForm.LastHole", false).capture() //$NON-NLS-1$ //$NON-NLS-2$
                .length("pickZ", "StripFeederForm.PickZ").width(120).probe("referenceHole") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .action("StripFeederForm.AutoSetup", "zap", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                    if (setup[0] == null) {
                        setup[0] = new StripFeederAutoSetup(feeder, form[0], running -> {
                        }, () -> form[0].reload());
                    }
                    setup[0].toggle(false);
                }).movesMachine()

                .section("StripFeederForm.Vision", "eye") //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("visionEnabled", "StripFeederForm.UseVision", "StripFeederForm.UseVision.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .pipeline("StripFeederForm.Pipeline", () -> stages(feeder.getPipeline()), //$NON-NLS-1$
                        () -> editPipeline(feeder), feeder::resetPipeline)
                .length("extrapolationDistance", "StripFeederForm.ExtrapolationDistance").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("visionEnabled", Boolean.TRUE::equals) //$NON-NLS-1$
                .length("parallaxDiameter", "StripFeederForm.ParallaxDiameter").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("visionEnabled", Boolean.TRUE::equals) //$NON-NLS-1$
                .decimal("parallaxAngle", "StripFeederForm.ParallaxAngle").width(120).unit("\u00b0") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen("visionEnabled", Boolean.TRUE::equals) //$NON-NLS-1$
                .action("StripFeederForm.ResetVision", "undo", feeder::resetVision) //$NON-NLS-1$ //$NON-NLS-2$

                .section("StripFeederForm.Feeding", "gear") //$NON-NLS-1$ //$NON-NLS-2$
                .integer("feedCount", "StripFeederForm.FeedCount").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("maxFeedCount", "StripFeederForm.MaxFeedCount").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .button("StripFeederForm.MaxFeedCount.Count", null, f -> f.setValue("maxFeedCount", //$NON-NLS-1$ //$NON-NLS-2$
                        String.valueOf(partsInStrip(feeder))))
                .readOnly("partsLeft", "StripFeederForm.PartsLeft") //$NON-NLS-1$ //$NON-NLS-2$
                .integer("lowCount", "StripFeederForm.LowCount").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .note("StripFeederForm.LowCount.Note") //$NON-NLS-1$
                .validate(v -> nonNegative(v), "StripFeederForm.NotNegative") //$NON-NLS-1$
                .integer("feedRetryCount", "StripFeederForm.FeedRetryCount").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("pickRetryCount", "StripFeederForm.PickRetryCount").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("feedOptions", "StripFeederForm.FeedOptions", ReferenceFeeder.FeedOptions.class) //$NON-NLS-1$ //$NON-NLS-2$
                .action("StripFeederForm.Refill", "refresh", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                    feeder.refill(null);
                    form[0].reload();
                });
        form[0] = builder.build();
        return form[0];
    }

    /** The parts between the two reference holes at the part pitch, both ends counted. */
    static int partsInStrip(ReferenceStripFeeder feeder) {
        Length length = feeder.getReferenceHoleLocation().getLinearLengthTo(feeder.getLastHoleLocation());
        if (feeder.getPartPitch().getValue() <= 0) {
            return 0;
        }
        return 1 + (int) Math.round(length.divide(feeder.getPartPitch()));
    }

    private static boolean nonNegative(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value).trim()) >= 0;
        }
        catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * The enabled stages by what they do, "取图 › 模糊 › 圆对称检测": their own names are what
     * the pipeline refers to them by, "0" and "results".
     */
    private static List<String> stages(CvPipeline pipeline) {
        List<String> names = new ArrayList<>();
        if (pipeline != null) {
            for (CvStage stage : pipeline.getStages()) {
                if (stage.isEnabled()) {
                    names.add(org.openpnp.gui.support.DisplayNames.typeName(stage.getClass()));
                }
            }
        }
        return names;
    }

    private static void editPipeline(ReferenceStripFeeder feeder) {
        UiUtils.messageBoxOnException(() -> {
            Camera camera = feeder.getMachine().getDefaultHead().getDefaultCamera();
            CvPipeline pipeline = StripFeederAutoSetup.pipeline(feeder, camera, false);
            CvPipelineEditor editor = new CvPipelineEditor(pipeline);
            JDialog dialog = new CvPipelineEditorDialog(MainFrame.get(),
                    String.format(Translations.getString("StripFeederForm.Pipeline.Title"), feeder.getName()), //$NON-NLS-1$
                    editor);
            dialog.setVisible(true);
        });
    }

}
