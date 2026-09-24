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

import java.util.Collections;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.openpnp.Translations;
import org.openpnp.events.FeederSelectedEvent;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.processes.RegionOfInterestProcess;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Ui;
import org.openpnp.machine.reference.feeder.ReferencePushPullFeeder;
import org.openpnp.machine.reference.feeder.ReferencePushPullFeeder.CalibrationTrigger;
import org.openpnp.machine.reference.feeder.ReferencePushPullFeeder.OcrWrongPartAction;
import org.openpnp.model.RegionOfInterest;
import org.openpnp.spi.Camera;
import org.openpnp.util.FeederVisionHelper.PipelineType;
import org.openpnp.util.MovableUtils;
import org.openpnp.util.OcrUtils;
import org.openpnp.util.UiUtils;
import org.openpnp.vision.pipeline.CvPipeline;
import org.openpnp.vision.pipeline.ui.CvPipelineEditor;
import org.openpnp.vision.pipeline.ui.CvPipelineEditorDialog;

/**
 * A push-pull feeder's configuration: where it picks and its sprocket holes, the tape, the
 * vision that calibrates it, OCR, and cloning from and to a template feeder.
 */
public final class PushPullForm {
    private PushPullForm() {
    }

    private static boolean ask(String title, String what, String action) {
        return Dialogs.ask(MainFrame.get(), Dialogs.Tone.Warn, "feeder", Translations.getString(title), what, null, //$NON-NLS-1$
                Dialogs.Choice.primary(Translations.getString(action))) == 0;
    }

    private static List<String> stages(ReferencePushPullFeeder feeder) {
        try {
            return StripFeederForm.stages(feeder.getCvPipeline(feeder.getCamera(), false, false, false));
        }
        catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static void editPipeline(ReferencePushPullFeeder feeder) {
        UiUtils.messageBoxOnException(() -> UiUtils.confirmMoveToLocationAndAct(MainFrame.get(),
                Translations.getString("DialogMessages.MoveCameraToFeederVision"), //$NON-NLS-1$
                feeder.getCamera(), feeder.getNominalVisionLocation(), true, () -> {
                    Camera camera = feeder.getCamera();
                    CvPipeline pipeline = feeder.getCvPipeline(camera, false, true, true);
                    JDialog dialog = new CvPipelineEditorDialog(MainFrame.get(),
                            String.format(Translations.getString("StripFeederForm.Pipeline.Title"), feeder.getName()), //$NON-NLS-1$
                            new CvPipelineEditor(pipeline));
                    dialog.setVisible(true);
                }));
    }

    /** What OCR reports, shown on the event thread once the machine is done. */
    private static void report(StringBuilder report) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(MainFrame.get(),
                "<html>" + (report.length() == 0 ? Translations.getString("PushPullForm.Ocr.Nothing") : report) + "</html>", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                Translations.getString("DialogMessages.OcrReport.Title"), JOptionPane.INFORMATION_MESSAGE)); //$NON-NLS-1$
    }

    public static FormWizard build(ReferencePushPullFeeder feeder) {
        Cloning cloning = new Cloning(feeder);
        FormWizard[] form = new FormWizard[1];
        form[0] = FeederForm.common(Form.of(feeder).named(feeder.getName()), feeder, false)
                .section("PushPullForm.Locations", "crosshair") //$NON-NLS-1$ //$NON-NLS-2$
                .location("location", "PushPullForm.Pick", false).withZ().locationButtons() //$NON-NLS-1$ //$NON-NLS-2$
                .angle("rotationInFeeder", "PushPullForm.Rotation").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("normalizePickLocation", "PushPullForm.Normalize", "PushPullForm.Normalize.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .location("hole1Location", "PushPullForm.Hole1", false).capture() //$NON-NLS-1$ //$NON-NLS-2$
                .location("hole2Location", "PushPullForm.Hole2", false).capture() //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("snapToAxis", "PushPullForm.Snap", "PushPullForm.Snap.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .action("PushPullForm.AutoSetup", "capture", () -> autoSetup(form[0], feeder)).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .hint("PushPullForm.AutoSetup.Hint") //$NON-NLS-1$
                .action("PushPullForm.PlusOne", "plus", () -> plusOne(form[0], feeder)).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .section("PushPullForm.Tape", "feeder") //$NON-NLS-1$ //$NON-NLS-2$
                .length("partPitch", "PushPullForm.PartPitch").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .length("feedPitch", "PushPullForm.FeedPitch").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("feedMultiplier", "PushPullForm.Multiplier").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("feedCount", "PushPullForm.FeedCount").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .button("PushPullForm.FeedCount.Reset", "undo", f -> { //$NON-NLS-1$ //$NON-NLS-2$
                    if (ask("PushPullForm.FeedCount.Reset.Title", Translations.getString("PushPullForm.FeedCount.Reset.What"), //$NON-NLS-1$ //$NON-NLS-2$
                            "PushPullForm.FeedCount.Reset.Action")) { //$NON-NLS-1$
                        f.setValue("feedCount", "0"); //$NON-NLS-1$ //$NON-NLS-2$
                        f.apply();
                    }
                })
                .action("PushPullForm.Discard", "trash", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                    form[0].apply();
                    UiUtils.messageBoxOnException(feeder::discardParts);
                })
                .hint("PushPullForm.Discard.Hint") //$NON-NLS-1$
                .section("PushPullForm.Vision", "eye") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("pipelineType", "PushPullForm.PipelineType", PipelineType.class) //$NON-NLS-1$ //$NON-NLS-2$
                .pipeline("FeederForm.Pipeline", () -> stages(feeder), () -> editPipeline(feeder), () -> { //$NON-NLS-1$
                    PipelineType type = (PipelineType) form[0].value("pipelineType"); //$NON-NLS-1$
                    if (ask("PushPullForm.Pipeline.Reset.Title", //$NON-NLS-1$
                            String.format(Translations.getString("PushPullForm.Pipeline.Reset.What"), //$NON-NLS-1$
                                    org.openpnp.gui.support.DisplayNames.of(type)),
                            "PushPullForm.Pipeline.Reset.Action")) { //$NON-NLS-1$
                        form[0].apply();
                        UiUtils.messageBoxOnException(() -> feeder.resetPipeline(type));
                    }
                })
                .action("PushPullForm.Preview", "eye", () -> UiUtils.submitUiMachineTask(feeder::showFeatures)).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .choice("calibrationTrigger", "PushPullForm.CalibrationTrigger", CalibrationTrigger.class) //$NON-NLS-1$ //$NON-NLS-2$
                .length("precisionWanted", "PushPullForm.PrecisionWanted").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("calibrationCount", "PushPullForm.CalibrationCount").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .length("precisionAverage", "PushPullForm.PrecisionAverage").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .length("precisionConfidenceLimit", "PushPullForm.ConfidenceLimit").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .action("PushPullForm.ResetStatistics", "undo", //$NON-NLS-1$ //$NON-NLS-2$
                        () -> UiUtils.messageBoxOnException(feeder::resetCalibrationStatistics))
                .section("PushPullForm.Ocr", "search") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("ocrWrongPartAction", "PushPullForm.WrongPart", OcrWrongPartAction.class) //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("ocrStopAfterWrongPart", "PushPullForm.StopAfterWrong", "PushPullForm.StopAfterWrong.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .toggle("ocrDiscoverOnJobStart", "PushPullForm.DiscoverOnStart", "PushPullForm.DiscoverOnStart.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .choice("ocrFontName", "PushPullForm.Font", OcrUtils.createFontSelectionList(feeder.getOcrFontName(), true), null) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("ocrFontSizePt", "PushPullForm.FontSize").unit("pt").width(100) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .action("PushPullForm.OcrRegion", "crosshair", () -> ocrRegion(form[0], feeder)).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .action("PushPullForm.PartByOcr", "search", () -> partByOcr(form[0], feeder)).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .action("PushPullForm.AllOcr", "search", () -> allOcr(form[0], feeder)).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .section("PushPullForm.Cloning", "copy").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("usedAsTemplate", "PushPullForm.Template", "PushPullForm.Template.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .custom("", cloning) //$NON-NLS-1$
                .onReload(f -> cloning.status())
                .build();
        cloning.form = form[0];
        return form[0];
    }

    private static void autoSetup(FormWizard form, ReferencePushPullFeeder feeder) {
        // A feeder without a pick location yet is a fresh one: nothing to overwrite.
        if (feeder.getLocation().multiply(1, 1, 0, 0).isInitialized()
                && !ask("PushPullForm.AutoSetup.Title", Translations.getString(feeder.isUsedAsTemplate() //$NON-NLS-1$
                        ? "PushPullForm.AutoSetup.Template" : "PushPullForm.AutoSetup.What"), //$NON-NLS-1$ //$NON-NLS-2$
                        "PushPullForm.AutoSetup.Action")) { //$NON-NLS-1$
            return;
        }
        form.apply();
        UiUtils.submitUiMachineTask(feeder::autoSetup);
    }

    private static void plusOne(FormWizard form, ReferencePushPullFeeder feeder) {
        form.apply();
        UiUtils.messageBoxOnException(() -> {
            ReferencePushPullFeeder created = feeder.createNewInRow();
            UiUtils.submitUiMachineTask(() -> {
                Camera camera = feeder.getCamera();
                MovableUtils.moveToLocationAtSafeZ(camera, created.getPickLocation(0, null));
                MovableUtils.fireTargetedUserAction(camera);
                created.autoSetup();
                SwingUtilities.invokeLater(() -> feeder.getMachine().getConfiguration().getBus()
                        .post(new FeederSelectedEvent(created, PushPullForm.class)));
            });
        });
    }

    private static void ocrRegion(FormWizard form, ReferencePushPullFeeder feeder) {
        form.apply();
        UiUtils.submitUiMachineTask(() -> {
            MovableUtils.moveToLocationAtSafeZ(feeder.getCamera(), feeder.getNominalVisionLocation());
            MovableUtils.fireTargetedUserAction(feeder.getCamera());
            SwingUtilities.invokeAndWait(() -> UiUtils.messageBoxOnException(() -> {
                new RegionOfInterestProcess(MainFrame.get(), feeder.getCamera(),
                        Translations.getString("PushPullForm.OcrRegion"), true) { //$NON-NLS-1$
                    @Override
                    public void setResult(RegionOfInterest roi) {
                        feeder.setOcrRegion(roi);
                    }
                };
            }));
        });
    }

    /** OCR here, the part it reads assigned; the report shown on the event thread. */
    private static void partByOcr(FormWizard form, ReferencePushPullFeeder feeder) {
        form.apply();
        UiUtils.submitUiMachineTask(() -> {
            MovableUtils.moveToLocationAtSafeZ(feeder.getCamera(), feeder.getOcrLocation());
            MovableUtils.fireTargetedUserAction(feeder.getCamera());
            StringBuilder report = new StringBuilder();
            feeder.performOcr(OcrWrongPartAction.ChangePart, false, report);
            report(report);
        });
    }

    private static void allOcr(FormWizard form, ReferencePushPullFeeder feeder) {
        form.apply();
        UiUtils.submitUiMachineTask(() -> {
            StringBuilder report = new StringBuilder();
            feeder.performOcrOnAllFeeders(null, false, report);
            report(report);
        });
    }

    /**
     * Cloning from the template feeder, or from this one as the template to all it fits: which
     * settings, and the template's state in words.
     */
    static final class Cloning extends JPanel {
        private final ReferencePushPullFeeder feeder;
        private final JLabel status = Ui.t2(""); //$NON-NLS-1$
        private final JCheckBox location = new JCheckBox(Translations.getString("PushPullForm.Clone.Location")); //$NON-NLS-1$
        private final JCheckBox tape = new JCheckBox(Translations.getString("PushPullForm.Clone.Tape"), true); //$NON-NLS-1$
        private final JCheckBox pushPull = new JCheckBox(Translations.getString("PushPullForm.Clone.PushPull"), true); //$NON-NLS-1$
        private final JCheckBox vision = new JCheckBox(Translations.getString("PushPullForm.Clone.Vision"), true); //$NON-NLS-1$
        FormWizard form;

        Cloning(ReferencePushPullFeeder feeder) {
            super(new java.awt.GridLayout(0, 1, 0, 4));
            this.feeder = feeder;
            setOpaque(false);
            for (JCheckBox box : new JCheckBox[] {location, tape, pushPull, vision}) {
                box.setOpaque(false);
            }
            JButton from = Ui.button(Translations.getString("PushPullForm.Clone.From"), Ui.iconSm("download"), Ui.Size.Sm, Ui.Variant.Default); //$NON-NLS-1$ //$NON-NLS-2$
            JButton to = Ui.button(Translations.getString("PushPullForm.Clone.To"), Ui.iconSm("upload"), Ui.Size.Sm, Ui.Variant.Default); //$NON-NLS-1$ //$NON-NLS-2$
            from.addActionListener(e -> cloneFrom());
            to.addActionListener(e -> cloneTo());
            add(status);
            add(Forms.row(location, tape));
            add(Forms.row(pushPull, vision));
            add(Forms.row(from, to));
            status();
        }

        void status() {
            String text = feeder.getCloneTemplateStatus();
            status.setText(text == null ? "" : text); //$NON-NLS-1$
        }

        private boolean anything() {
            return tape.isSelected() || pushPull.isSelected() || vision.isSelected();
        }

        private void cloneFrom() {
            UiUtils.messageBoxOnException(() -> {
                if (feeder.isUsedAsTemplate()) {
                    throw new Exception(Translations.getString("PushPullForm.Clone.IsTemplate")); //$NON-NLS-1$
                }
                if (!anything()) {
                    throw new Exception(Translations.getString("PushPullForm.Clone.Nothing")); //$NON-NLS-1$
                }
                form.apply();
                if (feeder.getTemplateFeeder(null) == null) {
                    throw new Exception(Translations.getString("PushPullForm.Clone.NoTemplate")); //$NON-NLS-1$
                }
                if (ask("PushPullForm.Clone.From.Title", Translations.getString("PushPullForm.Clone.From.What"), //$NON-NLS-1$ //$NON-NLS-2$
                        "PushPullForm.Clone.From")) { //$NON-NLS-1$
                    feeder.smartClone(null, location.isSelected(), tape.isSelected(), pushPull.isSelected(),
                            vision.isSelected(), vision.isSelected());
                    form.reload();
                }
            });
        }

        private void cloneTo() {
            UiUtils.messageBoxOnException(() -> {
                if (!feeder.isUsedAsTemplate()) {
                    throw new Exception(Translations.getString("PushPullForm.Clone.NotTemplate")); //$NON-NLS-1$
                }
                if (!anything()) {
                    throw new Exception(Translations.getString("PushPullForm.Clone.Nothing")); //$NON-NLS-1$
                }
                form.apply();
                if (feeder.getCompatibleFeeders().isEmpty()) {
                    throw new Exception(Translations.getString("PushPullForm.Clone.NoTargets")); //$NON-NLS-1$
                }
                if (ask("PushPullForm.Clone.To.Title", //$NON-NLS-1$
                        String.format(Translations.getString("PushPullForm.Clone.To.What"), feeder.getCompatibleFeeders().size()), //$NON-NLS-1$
                        "PushPullForm.Clone.To")) { //$NON-NLS-1$
                    for (ReferencePushPullFeeder target : feeder.getCompatibleFeeders()) {
                        target.cloneFeederSettings(location.isSelected(), tape.isSelected(), pushPull.isSelected(),
                                vision.isSelected(), vision.isSelected(), feeder);
                    }
                }
            });
        }
    }
}
