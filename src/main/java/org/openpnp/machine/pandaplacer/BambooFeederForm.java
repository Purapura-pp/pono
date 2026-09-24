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

package org.openpnp.machine.pandaplacer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JDialog;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.gui.support.DisplayNames;
import org.openpnp.machine.pandaplacer.AbstractPandaplacerVisionFeeder.CalibrationTrigger;
import org.openpnp.machine.reference.feeder.wizards.FeederForm;
import org.openpnp.spi.Actuator;
import org.openpnp.util.FeederVisionHelper.PipelineType;
import org.openpnp.util.UiUtils;
import org.openpnp.vision.pipeline.CvPipeline;
import org.openpnp.vision.pipeline.CvStage;
import org.openpnp.vision.pipeline.ui.CvPipelineEditor;
import org.openpnp.vision.pipeline.ui.CvPipelineEditorDialog;

/**
 * A Bamboo feeder with auto vision: where it picks and its sprocket holes, the tape, the vision
 * that calibrates it and the actuators that feed it.
 */
public final class BambooFeederForm {
    private BambooFeederForm() {
    }

    private static boolean ask(String title, String what, String action) {
        return Dialogs.ask(MainFrame.get(), Dialogs.Tone.Warn, "feeder", Translations.getString(title), what, null, //$NON-NLS-1$
                Dialogs.Choice.primary(Translations.getString(action))) == 0;
    }

    private static List<String> stages(BambooFeederAutoVision feeder) {
        List<String> names = new ArrayList<>();
        try {
            CvPipeline pipeline = feeder.getCvPipeline(feeder.getCamera(), false, false);
            for (CvStage stage : pipeline.getStages()) {
                if (stage.isEnabled()) {
                    names.add(DisplayNames.typeName(stage.getClass()));
                }
            }
        }
        catch (Exception e) {
            return Collections.emptyList();
        }
        return names;
    }

    private static void editPipeline(BambooFeederAutoVision feeder) {
        UiUtils.messageBoxOnException(() -> UiUtils.confirmMoveToLocationAndAct(MainFrame.get(),
                Translations.getString("DialogMessages.MoveCameraToFeederVision"), //$NON-NLS-1$
                feeder.getCamera(), feeder.getNominalVisionLocation(), true, () -> {
                    CvPipeline pipeline = feeder.getCvPipeline(feeder.getCamera(), false, true);
                    JDialog dialog = new CvPipelineEditorDialog(MainFrame.get(),
                            String.format(Translations.getString("StripFeederForm.Pipeline.Title"), feeder.getName()), //$NON-NLS-1$
                            new CvPipelineEditor(pipeline));
                    dialog.setVisible(true);
                }));
    }

    public static FormWizard build(BambooFeederAutoVision feeder) {
        List<Actuator> actuators = new ArrayList<>();
        actuators.add(null);
        actuators.addAll(feeder.getMachine().getActuators());
        FormWizard[] form = new FormWizard[1];
        form[0] = FeederForm.common(Form.of(feeder).named(feeder.getName()), feeder, false)
                .section("PushPullForm.Locations", "crosshair") //$NON-NLS-1$ //$NON-NLS-2$
                .location("location", "PushPullForm.Pick", false).withZ().locationButtons() //$NON-NLS-1$ //$NON-NLS-2$
                .angle("rotationInFeeder", "PushPullForm.Rotation").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("normalizePickLocation", "PushPullForm.Normalize", "PushPullForm.Normalize.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .location("hole1Location", "PushPullForm.Hole1", false).capture() //$NON-NLS-1$ //$NON-NLS-2$
                .location("hole2Location", "PushPullForm.Hole2", false).capture() //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("snapToAxis", "PushPullForm.Snap", "PushPullForm.Snap.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .action("PushPullForm.AutoSetup", "capture", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                    if (feeder.getLocation().multiply(1, 1, 0, 0).isInitialized()
                            && !ask("PushPullForm.AutoSetup.Title", Translations.getString("PushPullForm.AutoSetup.What"), //$NON-NLS-1$ //$NON-NLS-2$
                                    "PushPullForm.AutoSetup.Action")) { //$NON-NLS-1$
                        return;
                    }
                    form[0].apply();
                    UiUtils.submitUiMachineTask(feeder::autoSetup);
                }).movesMachine()
                .hint("PushPullForm.AutoSetup.Hint") //$NON-NLS-1$
                .section("PushPullForm.Tape", "feeder") //$NON-NLS-1$ //$NON-NLS-2$
                .length("partPitch", "PushPullForm.PartPitch").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .length("feedPitch", "PushPullForm.FeedPitch").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("BambooFeederForm.Pitch.Hint") //$NON-NLS-1$
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
                    UiUtils.messageBoxOnException(() -> {
                        // Up to the next whole feed cycle, as the parts of the last one are gone.
                        long cycle = feeder.getPartsPerFeedCycle();
                        feeder.setFeedCount(((feeder.getFeedCount() - 1) / cycle + 1) * cycle);
                        feeder.resetCalibration();
                        form[0].reload();
                    });
                })
                .hint("PushPullForm.Discard.Hint") //$NON-NLS-1$
                .section("PushPullForm.Vision", "eye") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("pipelineType", "PushPullForm.PipelineType", PipelineType.class) //$NON-NLS-1$ //$NON-NLS-2$
                .pipeline("FeederForm.Pipeline", () -> stages(feeder), () -> editPipeline(feeder), () -> { //$NON-NLS-1$
                    PipelineType type = (PipelineType) form[0].value("pipelineType"); //$NON-NLS-1$
                    if (ask("PushPullForm.Pipeline.Reset.Title", //$NON-NLS-1$
                            String.format(Translations.getString("PushPullForm.Pipeline.Reset.What"), DisplayNames.of(type)), //$NON-NLS-1$
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
                .section("ReferenceAutoFeederConfigurationWizard.ActuatorsPanel.Border.title", "zap") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("feedActuator", "FeederForm.Auto.Feed", actuators, null) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("feedActuatorValue", "FeederForm.Auto.FeedValue").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Test", "play", f -> test(f, feeder, true)) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("postPickActuator", "FeederForm.Auto.PostPick", actuators, null) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("postPickActuatorValue", "FeederForm.Auto.PostPickValue").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Test", "play", f -> test(f, feeder, false)) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("FeederForm.Auto.Value.Hint") //$NON-NLS-1$
                .toggle("moveBeforeFeed", "FeederForm.Auto.MoveBeforeFeed", "FeederForm.Auto.MoveBeforeFeed.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .build();
        return form[0];
    }

    private static void test(FormWizard form, BambooFeederAutoVision feeder, boolean feed) {
        form.apply();
        UiUtils.submitUiMachineTask(() -> {
            Actuator actuator = feed ? feeder.getFeedActuator() : feeder.getPostPickActuator();
            if (actuator == null) {
                throw new Exception(String.format(Translations.getString("FeederForm.Auto.NoActuator"), feeder.getName())); //$NON-NLS-1$
            }
            // As an Object, the value is read as the actuator's own value type.
            actuator.actuate((Object) (feed ? feeder.getFeedActuatorValue() : feeder.getPostPickActuatorValue()));
        });
    }
}
