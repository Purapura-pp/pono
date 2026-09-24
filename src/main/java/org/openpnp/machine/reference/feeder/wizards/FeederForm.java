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
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.JDialog;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Forms;
import org.openpnp.machine.reference.ReferenceFeeder;
import org.openpnp.machine.reference.feeder.AdvancedLoosePartFeeder;
import org.openpnp.machine.reference.feeder.ReferenceAutoFeeder;
import org.openpnp.machine.reference.feeder.ReferenceLoosePartFeeder;
import org.openpnp.machine.reference.feeder.ReferenceTrayFeeder;
import org.openpnp.machine.reference.feeder.ReferenceTubeFeeder;
import org.openpnp.model.Part;
import org.openpnp.spi.Actuator;
import org.openpnp.util.UiUtils;
import org.openpnp.vision.pipeline.CvPipeline;
import org.openpnp.vision.pipeline.ui.CvPipelineEditor;
import org.openpnp.vision.pipeline.ui.CvPipelineEditorDialog;

/**
 * The feeders' forms. What every feeder has - its part, how often feeding and picking are tried
 * again and, for most, where it is picked - is {@link #common}, the part the feeders' base
 * wizard was; each kind adds its own.
 */
public final class FeederForm {
    private FeederForm() {
    }

    /** The configuration's parts by name, for a feeder's part. */
    static List<Part> parts(ReferenceFeeder feeder) {
        List<Part> parts = new ArrayList<>();
        if (feeder.getMachine() != null && feeder.getMachine().getConfiguration() != null) {
            parts.addAll(feeder.getMachine().getConfiguration().getParts());
        }
        parts.sort(Comparator.comparing(Part::getId, String.CASE_INSENSITIVE_ORDER));
        return parts;
    }

    /** The sections every feeder has, for a form on the feeder itself. */
    public static Form.Builder common(Form.Builder form, ReferenceFeeder feeder, boolean pickLocation) {
        form.section("AbstractReferenceFeederConfigurationWizard.GeneralPanel.Border.title", "feeder") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("part", "AbstractReferenceFeederConfigurationWizard.GeneralPanel.PartLabel.text", parts(feeder), //$NON-NLS-1$ //$NON-NLS-2$
                        Part::getName)
                .integer("feedRetryCount", "AbstractReferenceFeederConfigurationWizard.GeneralPanel.FeedRetryCountLabel.text").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("pickRetryCount", "AbstractReferenceFeederConfigurationWizard.GeneralPanel.PickRetryCountLabel.text").width(100); //$NON-NLS-1$ //$NON-NLS-2$
        if (pickLocation) {
            form.section("AbstractReferenceFeederConfigurationWizard.PickLocationPanel.Border.title", "crosshair") //$NON-NLS-1$ //$NON-NLS-2$
                    .location("location", "FeederForm.PickLocation", true).locationButtons(); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return form;
    }

    /** A feeder's pipeline in the editor, the default camera and the feeder at hand to it. */
    static void editPipeline(ReferenceFeeder feeder, CvPipeline pipeline, String titleKey) throws Exception {
        if (feeder.getPart() == null) {
            throw new Exception(String.format(Translations.getString("FeederForm.NoPart"), feeder.getName())); //$NON-NLS-1$
        }
        pipeline.setProperty("camera", feeder.getMachine().getDefaultHead().getDefaultCamera()); //$NON-NLS-1$
        pipeline.setProperty("feeder", feeder); //$NON-NLS-1$
        CvPipelineEditor editor = new CvPipelineEditor(pipeline);
        JDialog dialog = new CvPipelineEditorDialog(MainFrame.get(),
                String.format(Translations.getString(titleKey), feeder.getPart().getId()), editor);
        dialog.setVisible(true);
    }

    private static Runnable edit(ReferenceFeeder feeder, Supplier<CvPipeline> pipeline, String titleKey) {
        return () -> UiUtils.messageBoxOnException(() -> editPipeline(feeder, pipeline.get(), titleKey));
    }

    public static FormWizard tube(ReferenceTubeFeeder feeder) {
        return common(Form.of(feeder).named(feeder.getName()), feeder, true).build();
    }

    public static FormWizard loosePart(ReferenceLoosePartFeeder feeder) {
        return common(Form.of(feeder).named(feeder.getName()), feeder, true)
                .section("ReferenceLoosePartFeederConfigurationWizard.Vision.Border.title", "eye") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", Forms.paragraph(Translations.getString("FeederForm.Experimental"))) //$NON-NLS-1$ //$NON-NLS-2$
                .pipeline("FeederForm.Pipeline", () -> StripFeederForm.stages(feeder.getPipeline()), //$NON-NLS-1$
                        edit(feeder, feeder::getPipeline, "FeederForm.Pipeline.Title"), feeder::resetPipeline) //$NON-NLS-1$
                .build();
    }

    public static FormWizard advancedLoosePart(AdvancedLoosePartFeeder feeder) {
        return common(Form.of(feeder).named(feeder.getName()), feeder, true)
                .section("AdvancedLoosePartFeederConfigurationWizard.Vision.Border.title", "eye") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", Forms.paragraph(Translations.getString("FeederForm.Experimental"))) //$NON-NLS-1$ //$NON-NLS-2$
                .pipeline("AdvancedLoosePartFeederConfigurationWizard.lblFeedPipeline.text", //$NON-NLS-1$
                        () -> StripFeederForm.stages(feeder.getPipeline()),
                        edit(feeder, feeder::getPipeline, "FeederForm.Pipeline.Title"), feeder::resetPipeline) //$NON-NLS-1$
                .pipeline("AdvancedLoosePartFeederConfigurationWizard.lblTrainingPipeline.text", //$NON-NLS-1$
                        () -> StripFeederForm.stages(feeder.getTrainingPipeline()),
                        edit(feeder, feeder::getTrainingPipeline, "FeederForm.TrainingPipeline.Title"), //$NON-NLS-1$
                        feeder::resetTrainingPipeline)
                .build();
    }

    public static FormWizard tray(ReferenceTrayFeeder feeder) {
        FormWizard[] form = new FormWizard[1];
        form[0] = common(Form.of(feeder).named(feeder.getName()), feeder, true)
                .section("FeederForm.Tray", "grid") //$NON-NLS-1$ //$NON-NLS-2$
                .location("offsets", "FeederForm.Tray.Offsets", false) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("FeederForm.Tray.Offsets.Hint") //$NON-NLS-1$
                .integer("trayCountX", "FeederForm.Tray.CountX").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("trayCountY", "FeederForm.Tray.CountY").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("feedCount", "ReferenceTrayFeederConfigurationWizard.lblFeedCount.text").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .button("FeederForm.Tray.Reset", "undo", f -> { //$NON-NLS-1$ //$NON-NLS-2$
                    f.setValue("feedCount", "0"); //$NON-NLS-1$ //$NON-NLS-2$
                    f.apply();
                })
                .onApply(f -> {
                    // A tray of more than one part along a side needs the step between them.
                    if ((feeder.getOffsets().getX() == 0 && feeder.getTrayCountX() > 1)
                            || (feeder.getOffsets().getY() == 0 && feeder.getTrayCountY() > 1)) {
                        org.openpnp.gui.support.MessageBoxes.errorBox(MainFrame.get(),
                                Translations.getString("General.Error"), //$NON-NLS-1$
                                Translations.getString(feeder.getOffsets().getX() == 0 && feeder.getTrayCountX() > 1
                                        ? "ReferenceTrayFeederConfigurationWizard.Validation.XOffsetRequired" //$NON-NLS-1$
                                        : "ReferenceTrayFeederConfigurationWizard.Validation.YOffsetRequired")); //$NON-NLS-1$
                    }
                })
                .build();
        return form[0];
    }

    public static FormWizard auto(ReferenceAutoFeeder feeder) {
        List<String> actuators = new ArrayList<>();
        actuators.add(null);
        for (Actuator actuator : feeder.getMachine().getActuators()) {
            actuators.add(actuator.getName());
        }
        for (String name : new String[] {feeder.getActuatorName(), feeder.getPostPickActuatorName()}) {
            if (name != null && !name.isEmpty() && !actuators.contains(name)) {
                actuators.add(name);
            }
        }
        return common(Form.of(feeder).named(feeder.getName()), feeder, true)
                .section("ReferenceAutoFeederConfigurationWizard.ActuatorsPanel.Border.title", "zap") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("actuatorName", "FeederForm.Auto.Feed", actuators, null) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("actuatorValue", "FeederForm.Auto.FeedValue").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Test", "play", f -> test(f, feeder, true)).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .choice("postPickActuatorName", "FeederForm.Auto.PostPick", actuators, null) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("postPickActuatorValue", "FeederForm.Auto.PostPickValue").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .button("SchultzForm.Test", "play", f -> test(f, feeder, false)).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .hint("FeederForm.Auto.Value.Hint") //$NON-NLS-1$
                .toggle("moveBeforeFeed", "FeederForm.Auto.MoveBeforeFeed", "FeederForm.Auto.MoveBeforeFeed.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .toggle("recycleSupport", "FeederForm.Auto.Recycle", "FeederForm.Auto.Recycle.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .build();
    }

    /** The feed or post-pick actuator switched as the form says, after Apply. */
    private static void test(FormWizard form, ReferenceAutoFeeder feeder, boolean feed) {
        form.apply();
        UiUtils.submitUiMachineTask(() -> {
            String name = feed ? feeder.getActuatorName() : feeder.getPostPickActuatorName();
            if (name == null || name.isEmpty()) {
                throw new Exception(String.format(Translations.getString("FeederForm.Auto.NoActuator"), feeder.getName())); //$NON-NLS-1$
            }
            Actuator actuator = feeder.getMachine().getActuatorByName(name);
            if (actuator == null) {
                throw new Exception(String.format(Translations.getString("FeederForm.Auto.UnknownActuator"), name)); //$NON-NLS-1$
            }
            // As an Object, the value is read as the actuator's own value type.
            actuator.actuate((Object) (feed ? feeder.getActuatorValue() : feeder.getPostPickActuatorValue()));
        });
    }
}
