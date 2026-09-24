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

import java.io.File;
import java.io.PrintWriter;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.JDialog;
import javax.swing.JFileChooser;

import org.apache.commons.io.IOUtils;
import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.gui.support.LengthConverter;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.machine.reference.feeder.BlindsFeeder;
import org.openpnp.machine.reference.feeder.BlindsFeeder.CoverActuation;
import org.openpnp.machine.reference.feeder.BlindsFeeder.CoverType;
import org.openpnp.machine.reference.feeder.BlindsFeeder.OcrAction;
import org.openpnp.machine.reference.feeder.BlindsFeeder.OcrTextOrientation;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Part;
import org.openpnp.spi.Feeder;
import org.openpnp.spi.Nozzle;
import org.openpnp.util.OcrUtils;
import org.openpnp.util.UiUtils;
import org.openpnp.vision.pipeline.CvPipeline;
import org.openpnp.vision.pipeline.ui.CvPipelineEditor;
import org.openpnp.vision.pipeline.ui.CvPipelineEditorDialog;
import org.pmw.tinylog.Logger;

/**
 * The blinds feeder: one feeder's tape, pockets and cover, and the array of feeders it is part
 * of, with the fiducials that locate the array and the vision and OCR they share.
 */
public final class BlindsFeederForm {
    private BlindsFeederForm() {
    }

    private static Nozzle nozzle() throws Exception {
        return MainFrame.get().getMachineControls().getSelectedNozzle();
    }

    /** The feeder's part angle and height are its location's angle and Z. */
    public static class Bean extends AbstractModelObject {
        private final BlindsFeeder feeder;

        Bean(BlindsFeeder feeder) {
            this.feeder = feeder;
        }

        public Part getPart() { return feeder.getPart(); }
        public void setPart(Part part) { feeder.setPart(part); }
        public int getFeedRetryCount() { return feeder.getFeedRetryCount(); }
        public void setFeedRetryCount(int count) { feeder.setFeedRetryCount(count); }
        public int getPickRetryCount() { return feeder.getPickRetryCount(); }
        public void setPickRetryCount(int count) { feeder.setPickRetryCount(count); }

        public double getRotationInTape() {
            return feeder.getLocation().getRotation();
        }

        public void setRotationInTape(double rotation) {
            feeder.setLocation(feeder.getLocation().derive(null, null, null, rotation));
        }

        public Length getPartZ() {
            return feeder.getLocation().getLengthZ();
        }

        public void setPartZ(Length z) {
            feeder.setLocation(feeder.getLocation().deriveLengths(null, null, z, null));
        }

        public Length getTapeLength() { return feeder.getTapeLength(); }
        public void setTapeLength(Length length) { feeder.setTapeLength(length); }
        public Length getFeederExtent() { return feeder.getFeederExtent(); }
        public void setFeederExtent(Length extent) { feeder.setFeederExtent(extent); }
        public Length getPocketCenterline() { return feeder.getPocketCenterline(); }
        public void setPocketCenterline(Length centerline) { feeder.setPocketCenterline(centerline); }
        public Length getPocketPitch() { return feeder.getPocketPitch(); }
        public void setPocketPitch(Length pitch) { feeder.setPocketPitch(pitch); }
        public Length getPocketSize() { return feeder.getPocketSize(); }
        public void setPocketSize(Length size) { feeder.setPocketSize(size); }
        public int getPocketCount() { return feeder.getPocketCount(); }
        public void setPocketCount(int count) { feeder.setPocketCount(count); }
        public int getFirstPocket() { return feeder.getFirstPocket(); }
        public void setFirstPocket(int pocket) { feeder.setFirstPocket(pocket); }
        public int getLastPocket() { return feeder.getLastPocket(); }
        public void setLastPocket(int pocket) { feeder.setLastPocket(pocket); }
        public int getFeedCount() { return feeder.getFeedCount(); }
        public void setFeedCount(int count) { feeder.setFeedCount(count); }
        public CoverType getCoverType() { return feeder.getCoverType(); }
        public void setCoverType(CoverType type) { feeder.setCoverType(type); }
        public CoverActuation getCoverActuation() { return feeder.getCoverActuation(); }
        public void setCoverActuation(CoverActuation actuation) { feeder.setCoverActuation(actuation); }
        public Length getEdgeOpenDistance() { return feeder.getEdgeOpenDistance(); }
        public void setEdgeOpenDistance(Length distance) { feeder.setEdgeOpenDistance(distance); }
        public Length getEdgeClosedDistance() { return feeder.getEdgeClosedDistance(); }
        public void setEdgeClosedDistance(Length distance) { feeder.setEdgeClosedDistance(distance); }
        public double getPushSpeed() { return feeder.getPushSpeed(); }
        public void setPushSpeed(double speed) { feeder.setPushSpeed(speed); }
        public Length getPushZOffset() { return feeder.getPushZOffset(); }
        public void setPushZOffset(Length offset) { feeder.setPushZOffset(offset); }
        public int getFeederNo() { return feeder.getFeederNo(); }
        public void setFeederNo(int number) { feeder.setFeederNo(number); }
        public int getFeedersTotal() { return feeder.getFeedersTotal(); }
        public void setFeedersTotal(int total) { feeder.setFeedersTotal(total); }
    }

    public static FormWizard feeder(BlindsFeeder feeder) {
        FormWizard[] form = new FormWizard[1];
        form[0] = Form.of(new Bean(feeder)).named("FeederForm.Configuration") //$NON-NLS-1$
                .section("AbstractReferenceFeederConfigurationWizard.GeneralPanel.Border.title", "feeder") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("part", "AbstractReferenceFeederConfigurationWizard.GeneralPanel.PartLabel.text", FeederForm.parts(feeder), Part::getName) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("feedRetryCount", "AbstractReferenceFeederConfigurationWizard.GeneralPanel.FeedRetryCountLabel.text").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .angle("rotationInTape", "PushPullForm.Rotation").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .length("partZ", "BlindsFeederForm.PartZ").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .iconButton("capture", "BlindsFeederForm.PartZ.Capture", f -> UiUtils.messageBoxOnException( //$NON-NLS-1$ //$NON-NLS-2$
                        () -> f.setValue("partZ", new LengthConverter().convertForward(nozzle().getLocation().getLengthZ())))) //$NON-NLS-1$
                .action("BlindsFeederForm.Ocr", "search", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                    form[0].apply();
                    UiUtils.submitUiMachineTask(() -> feeder.performOcr(feeder.getCamera(), OcrAction.ChangePart));
                }).movesMachine()
                .section("BlindsFeederForm.Tape", "grid") //$NON-NLS-1$ //$NON-NLS-2$
                .integer("feederNo", "BlindsFeederForm.FeederNo").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("feedersTotal", "BlindsFeederForm.FeedersTotal").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .length("tapeLength", "BlindsFeederForm.TapeLength").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .length("feederExtent", "BlindsFeederForm.Extent").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .length("pocketCenterline", "BlindsFeederForm.Centerline").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .length("pocketPitch", "BlindsFeederForm.Pitch").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .length("pocketSize", "BlindsFeederForm.Size").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("pocketCount", "BlindsFeederForm.Pockets").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("firstPocket", "BlindsFeederForm.First").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("lastPocket", "BlindsFeederForm.Last").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("feedCount", "BlindsFeederForm.FeedCount").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .button("PushPullForm.FeedCount.Reset", "undo", f -> { //$NON-NLS-1$ //$NON-NLS-2$
                    f.setValue("feedCount", "0"); //$NON-NLS-1$ //$NON-NLS-2$
                    f.apply();
                    // A new tape: the cover was very likely handled too.
                    UiUtils.submitUiMachineTask(() -> feeder.setCoverPosition(new Length(Double.NaN, LengthUnit.Millimeters)));
                })
                .action("BlindsFeederForm.AutoSetup", "capture", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                    form[0].apply();
                    UiUtils.submitUiMachineTask(() -> feeder.findPocketsAndCenterline(
                            feeder.getMachine().getDefaultHead().getDefaultCamera()));
                }).movesMachine()
                .hint("BlindsFeederForm.AutoSetup.Hint") //$NON-NLS-1$
                .action("BlindsFeederForm.Features", "eye", () -> UiUtils.submitUiMachineTask(feeder::showFeatures)).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .section("BlindsFeederForm.Cover", "lock") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("coverType", "BlindsFeederForm.CoverType", CoverType.class) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("coverActuation", "BlindsFeederForm.CoverActuation", CoverActuation.class) //$NON-NLS-1$ //$NON-NLS-2$
                .length("edgeOpenDistance", "BlindsFeederForm.EdgeOpen").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .length("edgeClosedDistance", "BlindsFeederForm.EdgeClosed").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .percent("pushSpeed", "BlindsFeederForm.PushSpeed").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .length("pushZOffset", "BlindsFeederForm.PushZ").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .action("BlindsFeederForm.CalibrateEdges", "target", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                    form[0].apply();
                    UiUtils.submitUiMachineTask(feeder::calibrateCoverEdges);
                }).movesMachine()
                .action("BlindsFeederForm.Open", "unlock", () -> cover(form[0], feeder, true)).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .button("BlindsFeederForm.Close", "lock", f -> cover(f, feeder, false)).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .action("BlindsFeederForm.OpenAll", "unlock", () -> allCovers(form[0], feeder, true)).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .button("BlindsFeederForm.CloseAll", "lock", f -> allCovers(f, feeder, false)).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .build();
        return form[0];
    }

    private static void cover(FormWizard form, BlindsFeeder feeder, boolean open) {
        form.apply();
        UiUtils.submitUiMachineTask(() -> feeder.actuateCover(nozzle(), open));
    }

    private static void allCovers(FormWizard form, BlindsFeeder feeder, boolean open) {
        form.apply();
        UiUtils.submitUiMachineTask(() -> BlindsFeeder.actuateAllFeederCovers(feeder.getMachine(), nozzle(), open));
    }

    /** The group names the machine's blinds feeders have. */
    private static String groups(BlindsFeeder feeder) {
        Set<String> names = new TreeSet<>();
        for (Feeder other : feeder.getMachine().getFeeders()) {
            if (other instanceof BlindsFeeder && ((BlindsFeeder) other).getFeederGroupName() != null) {
                names.add(((BlindsFeeder) other).getFeederGroupName());
            }
        }
        return String.join(Translations.getString("BlindsFeederForm.Separator"), names); //$NON-NLS-1$
    }

    private static boolean ask(String title, String what, String action) {
        return Dialogs.ask(MainFrame.get(), Dialogs.Tone.Warn, "feeder", Translations.getString(title), //$NON-NLS-1$
                Translations.getString(what), null, Dialogs.Choice.primary(Translations.getString(action))) == 0;
    }

    public static FormWizard array(BlindsFeeder feeder) {
        FormWizard[] form = new FormWizard[1];
        form[0] = Form.of(feeder).named("BlindsFeederForm.Array") //$NON-NLS-1$
                .section("BlindsFeederForm.Array", "grid") //$NON-NLS-1$ //$NON-NLS-2$
                .text("feederGroupName", "BlindsFeederForm.Group") //$NON-NLS-1$ //$NON-NLS-2$
                .liveHint(f -> String.format(Translations.getString("BlindsFeederForm.Group.Hint"), groups(feeder))) //$NON-NLS-1$
                .location("fiducial1Location", "BlindsFeederForm.Fiducial1", false).capture() //$NON-NLS-1$ //$NON-NLS-2$
                .location("fiducial2Location", "BlindsFeederForm.Fiducial2", false).capture() //$NON-NLS-1$ //$NON-NLS-2$
                .location("fiducial3Location", "BlindsFeederForm.Fiducial3", false).capture() //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("normalize", "BlindsFeederForm.Normalize", "BlindsFeederForm.Normalize.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .action("BlindsFeederForm.CalibrateFiducials", "target", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                    form[0].apply();
                    UiUtils.submitUiMachineTask(feeder::calibrateFeederLocations);
                }).movesMachine()
                .action("BlindsFeederForm.Extract", "download", BlindsFeederForm::extract) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("BlindsFeederForm.Extract.Hint") //$NON-NLS-1$
                .section("BlindsFeederForm.Vision", "eye") //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("visionEnabled", "DragFeederForm.UseVision", "BlindsFeederForm.UseVision.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .pipeline("FeederForm.Pipeline", () -> StripFeederForm.stages(feeder.getPipeline()), //$NON-NLS-1$
                        () -> UiUtils.messageBoxOnException(() -> editPipeline(feeder)), feeder::resetPipeline)
                .action("BlindsFeederForm.PipelineToAll", "copy", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                    if (ask("BlindsFeederForm.ToAll.Title", "BlindsFeederForm.PipelineToAll.What", "BlindsFeederForm.ToAll.Action")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        UiUtils.messageBoxOnException(feeder::setPipelineToAllFeeders);
                    }
                })
                .section("BlindsFeederForm.OcrSection", "search") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("ocrAction", "BlindsFeederForm.OcrAction", OcrAction.class) //$NON-NLS-1$ //$NON-NLS-2$
                .length("ocrMargin", "BlindsFeederForm.OcrMargin").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("ocrFontName", "PushPullForm.Font", OcrUtils.createFontSelectionList(feeder.getOcrFontName(), true), null) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("ocrFontSizePt", "PushPullForm.FontSize").unit("pt").width(100) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .choice("ocrTextOrientation", "BlindsFeederForm.OcrOrientation", OcrTextOrientation.class) //$NON-NLS-1$ //$NON-NLS-2$
                .action("BlindsFeederForm.OcrToAll", "copy", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                    if (ask("BlindsFeederForm.ToAll.Title", "BlindsFeederForm.OcrToAll.What", "BlindsFeederForm.ToAll.Action")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        form[0].apply();
                        UiUtils.messageBoxOnException(feeder::setOcrSettingsToAllFeeders);
                    }
                })
                .build();
        return form[0];
    }

    /** A new pipeline shared by the array's feeders, edited: setting it gives each the same one. */
    private static void editPipeline(BlindsFeeder feeder) throws Exception {
        feeder.setPipeline(feeder.getPipeline().clone());
        CvPipeline pipeline = feeder.getCvPipeline(feeder.getCamera(), false, feeder.getOcrAction());
        JDialog dialog = new CvPipelineEditorDialog(MainFrame.get(),
                String.format(Translations.getString("StripFeederForm.Pipeline.Title"), feeder.getName()), //$NON-NLS-1$
                new CvPipelineEditor(pipeline));
        dialog.setVisible(true);
    }

    /** The OpenSCAD files the feeders are printed from, into a folder, opened where possible. */
    private static void extract() {
        UiUtils.messageBoxOnException(() -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(MainFrame.get()) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            File directory = chooser.getSelectedFile();
            boolean opened = true;
            for (String name : new String[] {"BlindsFeeder-Library.scad", "BlindsFeeder-3DPrinting.scad"}) { //$NON-NLS-1$ //$NON-NLS-2$
                File file = new File(directory, name);
                if (file.exists()) {
                    throw new Exception(String.format(Translations.getString("BlindsFeederForm.Extract.Exists"), file.getAbsolutePath())); //$NON-NLS-1$
                }
                try (PrintWriter out = new PrintWriter(file.getAbsolutePath())) {
                    out.print(IOUtils.toString(BlindsFeeder.class.getResource(name)));
                }
                try {
                    java.awt.Desktop.getDesktop().edit(file);
                }
                catch (Exception e) {
                    Logger.error(e);
                    opened = false;
                }
            }
            if (!opened) {
                MessageBoxes.infoBox(Translations.getString("BlindsFeederForm.Extract"), //$NON-NLS-1$
                        String.format(Translations.getString("BlindsFeederForm.Extract.NotOpened"), directory.getAbsolutePath())); //$NON-NLS-1$
            }
        });
    }
}
