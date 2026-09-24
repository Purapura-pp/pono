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
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.machine.reference.feeder.ReferenceHeapFeeder;
import org.openpnp.machine.reference.feeder.ReferenceHeapFeeder.DropBox;
import org.openpnp.model.Part;
import org.openpnp.spi.Feeder;
import org.openpnp.spi.Nozzle;
import org.openpnp.util.UiUtils;
import org.openpnp.vision.pipeline.CvPipeline;
import org.openpnp.vision.pipeline.ui.CvPipelineEditor;
import org.openpnp.vision.pipeline.ui.CvPipelineEditorDialog;

/**
 * A heap feeder: the box its parts lie loose in and the way out of it, the vision that finds a
 * part, and the drop box a picked part is put down in to be seen alone. The drop boxes are
 * shared by the heap feeders and edited as they are.
 */
public final class HeapFeederForm {
    private HeapFeederForm() {
    }

    private static Nozzle nozzle(ReferenceHeapFeeder feeder) throws Exception {
        return feeder.getMachine().getDefaultHead().getDefaultNozzle();
    }

    public static FormWizard build(ReferenceHeapFeeder feeder) {
        DropBoxes boxes = new DropBoxes(feeder);
        Object[] box = {feeder.getDropBox()};
        FormWizard[] form = new FormWizard[1];
        form[0] = FeederForm.common(Form.of(feeder).named(feeder.getName()), feeder, false)
                .section("HeapFeederForm.Heap", "grid") //$NON-NLS-1$ //$NON-NLS-2$
                .location("location", "HeapFeederForm.Center", false).withZ().locationButtons() //$NON-NLS-1$ //$NON-NLS-2$
                .hint("HeapFeederForm.Center.Hint") //$NON-NLS-1$
                .decimal("boxDepth", "HeapFeederForm.Depth").unit("mm").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .decimal("lastFeedDepth", "HeapFeederForm.LastDepth").unit("mm").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .button("HeapFeederForm.LastDepth.Reset", "undo", f -> { //$NON-NLS-1$ //$NON-NLS-2$
                    f.setValue("lastFeedDepth", "0"); //$NON-NLS-1$ //$NON-NLS-2$
                    f.apply();
                })
                .hint("HeapFeederForm.LastDepth.Hint") //$NON-NLS-1$
                .location("way1", "HeapFeederForm.Way1", false).capture() //$NON-NLS-1$ //$NON-NLS-2$
                .location("way2", "HeapFeederForm.Way2", false).capture() //$NON-NLS-1$ //$NON-NLS-2$
                .location("way3", "HeapFeederForm.Way3", false).capture() //$NON-NLS-1$ //$NON-NLS-2$
                .hint("HeapFeederForm.Way.Hint") //$NON-NLS-1$
                .section("HeapFeederForm.Picking", "nozzle") //$NON-NLS-1$ //$NON-NLS-2$
                .integer("requiredVacuumDifference", "HeapFeederForm.Vacuum").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("HeapFeederForm.Vacuum.Hint") //$NON-NLS-1$
                .integer("throwAwayDropBoxContentAfterFailedFeeds", "HeapFeederForm.ThrowAway").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("HeapFeederForm.ThrowAway.Hint") //$NON-NLS-1$
                .toggle("pokeForParts", "HeapFeederForm.Poke", "HeapFeederForm.Poke.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .section("HeapFeederForm.Vision", "eye") //$NON-NLS-1$ //$NON-NLS-2$
                .pipeline("HeapFeederForm.FeederPipeline", () -> StripFeederForm.stages(feeder.getFeederPipeline()), //$NON-NLS-1$
                        () -> edit(feeder, feeder.getFeederPipeline(), feeder, "feeder", "FeederForm.Pipeline.Title"), //$NON-NLS-1$ //$NON-NLS-2$
                        feeder::resetFeederPipeline)
                .pipeline("HeapFeederForm.TrainingPipeline", () -> StripFeederForm.stages(feeder.getTrainingPipeline()), //$NON-NLS-1$
                        () -> edit(feeder, feeder.getTrainingPipeline(), feeder, "feeder", "FeederForm.TrainingPipeline.Title"), //$NON-NLS-1$ //$NON-NLS-2$
                        feeder::resetTrainingPipeline)
                .action("HeapFeederForm.Samples", "camera", //$NON-NLS-1$ //$NON-NLS-2$
                        () -> UiUtils.submitUiMachineTask(() -> feeder.getSamples(nozzle(feeder)))).movesMachine()
                .section("HeapFeederForm.DropBox", "download") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("dropBox", "HeapFeederForm.DropBox.Which", new ArrayList<Object>(ReferenceHeapFeeder.getDropBoxes(feeder.getMachine())), null) //$NON-NLS-1$ //$NON-NLS-2$
                .location("dropBoxLocation", "HeapFeederForm.DropBox.Bottom", false).withZ().locationButtons() //$NON-NLS-1$ //$NON-NLS-2$
                .location("dropBoxDropLocation", "HeapFeederForm.DropBox.Drop", false).withZ().locationButtons() //$NON-NLS-1$ //$NON-NLS-2$
                .action("HeapFeederForm.DropBox.Clean", "broom", //$NON-NLS-1$ //$NON-NLS-2$
                        () -> UiUtils.submitUiMachineTask(() -> feeder.getDropBox().clean(nozzle(feeder)))).movesMachine()
                .section("HeapFeederForm.DropBoxes", "layers").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", boxes) //$NON-NLS-1$
                .onChange(f -> {
                    Object chosen = f.value("dropBox"); //$NON-NLS-1$
                    if (chosen != box[0]) {
                        box[0] = chosen;
                        boxes.show((DropBox) chosen);
                    }
                })
                .build();
        boxes.form = form[0];
        return form[0];
    }

    private static void edit(ReferenceHeapFeeder feeder, CvPipeline pipeline, Object subject, String name,
            String titleKey) {
        UiUtils.messageBoxOnException(() -> {
            if (feeder.getPart() == null) {
                throw new Exception(String.format(Translations.getString("FeederForm.NoPart"), feeder.getName())); //$NON-NLS-1$
            }
            pipeline.setProperty("camera", feeder.getMachine().getDefaultHead().getDefaultCamera()); //$NON-NLS-1$
            pipeline.setProperty(name, subject);
            JDialog dialog = new CvPipelineEditorDialog(MainFrame.get(),
                    String.format(Translations.getString(titleKey), feeder.getPart().getId()), new CvPipelineEditor(pipeline));
            dialog.setVisible(true);
        });
    }

    /**
     * The drop boxes, shared by the heap feeders: a box's name, the part standing in for one not
     * recognized, and its part pipeline, edited as they are. New and deleted boxes show in the
     * choice above at once.
     */
    static final class DropBoxes extends JPanel {
        private final ReferenceHeapFeeder feeder;
        private final JTextField name = Forms.input(new JTextField(), false);
        private final JComboBox<Part> dummy = new JComboBox<>();
        private DropBox box;
        private boolean showing;
        FormWizard form;

        DropBoxes(ReferenceHeapFeeder feeder) {
            super(new java.awt.GridLayout(0, 1, 0, 6));
            this.feeder = feeder;
            setOpaque(false);
            dummy.addItem(null);
            for (Part part : FeederForm.parts(feeder)) {
                dummy.addItem(part);
            }
            JButton create = Ui.button(Translations.getString("HeapFeederForm.DropBox.New"), Ui.iconSm("plus"), Ui.Size.Sm, Ui.Variant.Default); //$NON-NLS-1$ //$NON-NLS-2$
            JButton delete = Ui.button(Translations.getString("HeapFeederForm.DropBox.Delete"), Ui.iconSm("trash"), Ui.Size.Sm, Ui.Variant.Default); //$NON-NLS-1$ //$NON-NLS-2$
            JButton edit = Ui.button(Translations.getString("HeapFeederForm.DropBox.Pipeline"), Ui.iconSm("edit"), Ui.Size.Sm, Ui.Variant.Default); //$NON-NLS-1$ //$NON-NLS-2$
            JButton reset = Ui.button(Translations.getString("HeapFeederForm.DropBox.PipelineReset"), Ui.iconSm("undo"), Ui.Size.Sm, Ui.Variant.Default); //$NON-NLS-1$ //$NON-NLS-2$
            add(Forms.row(Ui.t2(Translations.getString("HeapFeederForm.DropBox.Name")), name)); //$NON-NLS-1$
            add(Forms.row(Ui.t2(Translations.getString("HeapFeederForm.DropBox.Dummy")), dummy)); //$NON-NLS-1$
            add(Forms.row(edit, reset));
            add(Forms.row(create, delete));
            name.addActionListener(e -> rename());
            name.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    rename();
                }
            });
            dummy.addActionListener(e -> {
                if (!showing && box != null) {
                    box.setDummyPartForUnknown((Part) dummy.getSelectedItem());
                }
            });
            edit.addActionListener(e -> {
                if (box != null) {
                    DropBox shown = box;
                    UiUtils.messageBoxOnException(() -> {
                        CvPipeline pipeline = shown.getPartPipeline();
                        pipeline.setProperty("camera", feeder.getMachine().getDefaultHead().getDefaultCamera()); //$NON-NLS-1$
                        pipeline.setProperty("dropBox", shown); //$NON-NLS-1$
                        JDialog dialog = new CvPipelineEditorDialog(MainFrame.get(),
                                String.format(Translations.getString("FeederForm.Pipeline.Title"), shown.getName()), //$NON-NLS-1$
                                new CvPipelineEditor(pipeline));
                        dialog.setVisible(true);
                    });
                }
            });
            reset.addActionListener(e -> {
                if (box != null) {
                    box.resetPartPipeline();
                }
            });
            create.addActionListener(e -> {
                DropBox created = new DropBox();
                created.setName(Translations.getString("HeapFeederForm.DropBox.NewName")); //$NON-NLS-1$
                ReferenceHeapFeeder.getDropBoxes(feeder.getMachine()).add(created);
                refresh(created);
            });
            delete.addActionListener(e -> delete());
            show(feeder.getDropBox());
        }

        private void delete() {
            if (box == null) {
                return;
            }
            if (ReferenceHeapFeeder.getDropBoxes(feeder.getMachine()).size() < 2) {
                MessageBoxes.errorBox(MainFrame.get(), Translations.getString("General.Error"), //$NON-NLS-1$
                        Translations.getString("ReferenceHeapFeederConfigurationWizard.DeleteDropBox.LastOne")); //$NON-NLS-1$
                return;
            }
            for (Feeder other : feeder.getMachine().getFeeders()) {
                if (other != feeder && other instanceof ReferenceHeapFeeder
                        && ((ReferenceHeapFeeder) other).getDropBox() == box) {
                    MessageBoxes.errorBox(MainFrame.get(), Translations.getString("General.Error"), //$NON-NLS-1$
                            Translations.getString("ReferenceHeapFeederConfigurationWizard.DeleteDropBox.InUse")); //$NON-NLS-1$
                    return;
                }
            }
            ReferenceHeapFeeder.getDropBoxes(feeder.getMachine()).remove(box);
            refresh(ReferenceHeapFeeder.getDropBoxes(feeder.getMachine()).get(0));
        }

        private void refresh(DropBox shown) {
            if (form != null) {
                List<Object> all = new ArrayList<>(ReferenceHeapFeeder.getDropBoxes(feeder.getMachine()));
                form.setItems("dropBox", all); //$NON-NLS-1$
                form.set("dropBox", shown); //$NON-NLS-1$
            }
            show(shown);
        }

        void show(DropBox shown) {
            showing = true;
            box = shown;
            name.setText(shown == null ? "" : shown.getName()); //$NON-NLS-1$
            dummy.setSelectedItem(shown == null ? null : shown.getDummyPartForUnknown());
            showing = false;
        }

        private void rename() {
            if (box != null && !name.getText().equals(box.getName())) {
                box.setName(name.getText());
                if (form != null) {
                    form.repaint();
                }
            }
        }
    }
}
