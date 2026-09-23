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

package org.openpnp.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

import org.openpnp.Translations;
import org.openpnp.gui.components.AutoSelectTextTable;
import org.openpnp.gui.components.CameraView;
import org.openpnp.gui.components.reticle.FootprintReticle;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.importer.KicadModImporter;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.gui.shell.DockPanel;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.Helpers;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.tablemodel.FootprintTableModel;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.Configuration;
import org.openpnp.model.Footprint;
import org.openpnp.model.Footprint.Pad;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Package;
import org.openpnp.spi.Camera;
import org.openpnp.util.UiUtils;
import org.pmw.tinylog.Logger;

/**
 * A package's footprint as bottom vision sees it: the body's size, what generates the pads, and
 * the pads themselves, in a table as wide as the properties column. The wizard it replaces had
 * the fields in a grid of fourteen columns, the generators as four unlabelled icons, and the pads
 * table at a fixed size.
 */
public final class PackageVisionForm {
    private PackageVisionForm() {
    }

    public static class Bean extends AbstractModelObject {
        private final Footprint footprint;

        Bean(Footprint footprint) {
            this.footprint = footprint;
        }

        public LengthUnit getUnits() {
            return footprint.getUnits();
        }

        public void setUnits(LengthUnit units) {
            footprint.setUnits(units);
        }

        public double getBodyWidth() {
            return footprint.getBodyWidth();
        }

        public void setBodyWidth(double width) {
            footprint.setBodyWidth(width);
        }

        public double getBodyHeight() {
            return footprint.getBodyHeight();
        }

        public void setBodyHeight(double height) {
            footprint.setBodyHeight(height);
        }

        public double getOuterDimension() {
            return footprint.getOuterDimension();
        }

        public void setOuterDimension(double dimension) {
            footprint.setOuterDimension(dimension);
        }

        public double getInnerDimension() {
            return footprint.getInnerDimension();
        }

        public void setInnerDimension(double dimension) {
            footprint.setInnerDimension(dimension);
        }

        public int getPadCount() {
            return footprint.getPadCount();
        }

        public void setPadCount(int count) {
            footprint.setPadCount(count);
        }

        public double getPadPitch() {
            return footprint.getPadPitch();
        }

        public void setPadPitch(double pitch) {
            footprint.setPadPitch(pitch);
        }

        public double getPadAcross() {
            return footprint.getPadAcross();
        }

        public void setPadAcross(double across) {
            footprint.setPadAcross(across);
        }

        public double getPadRoundness() {
            return footprint.getPadRoundness();
        }

        public void setPadRoundness(double roundness) {
            footprint.setPadRoundness(roundness);
        }
    }

    public static FormWizard build(Configuration configuration, Package pkg) {
        Footprint footprint = pkg.getFootprint();
        FootprintTableModel tableModel = new FootprintTableModel(footprint, pkg);
        FormWizard[] form = new FormWizard[1];

        JPanel generators = Forms.row(
                generator(form, pkg, tableModel, Footprint.Generator.Dual, "PackageVisionForm.Generate.Dual", //$NON-NLS-1$
                        Icons.footprintDual),
                generator(form, pkg, tableModel, Footprint.Generator.Quad, "PackageVisionForm.Generate.Quad", //$NON-NLS-1$
                        Icons.footprintQuad),
                generator(form, pkg, tableModel, Footprint.Generator.Bga, "PackageVisionForm.Generate.Bga", //$NON-NLS-1$
                        Icons.footprintBga),
                generator(form, pkg, tableModel, Footprint.Generator.Kicad, "PackageVisionForm.Generate.Kicad", //$NON-NLS-1$
                        Icons.kicad));

        form[0] = Form.of(new Bean(footprint)).named("PackagesPanel.VisionTab.title") //$NON-NLS-1$
                .section("PackageVisionForm.Body", "pkg") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("units", "PackageVisionForm.Units", LengthUnit.class) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("bodyWidth", "PackageVisionForm.BodyWidth").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("bodyHeight", "PackageVisionForm.BodyLength").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .section("PackageVisionForm.Generate", "grid") //$NON-NLS-1$ //$NON-NLS-2$
                .note("PackageVisionForm.Generate.Note") //$NON-NLS-1$
                .decimal("outerDimension", "PackageVisionForm.OuterDimension").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("innerDimension", "PackageVisionForm.InnerDimension").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("padCount", "PackageVisionForm.PadCount").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("padPitch", "PackageVisionForm.PadPitch").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("padAcross", "PackageVisionForm.PadAcross").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("padRoundness", "PackageVisionForm.PadRoundness").unit("%").width(100) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .custom("", generators) //$NON-NLS-1$
                .section("PackageVisionForm.Pads", "footprint") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", pads(footprint, pkg, tableModel)) //$NON-NLS-1$
                .build();
        showReticle(configuration, footprint);
        return form[0];
    }

    /** A generator's button: the values on screen are applied first, since they are what it uses. */
    private static JButton generator(FormWizard[] form, Package pkg, FootprintTableModel tableModel,
            Footprint.Generator type, String key, javax.swing.Icon icon) {
        JButton button = Ui.button(Translations.getString(key), icon, Ui.Size.Sm, Ui.Variant.Default);
        button.setToolTipText(Translations.getString(key + ".ToolTip")); //$NON-NLS-1$
        button.addActionListener(e -> {
            form[0].apply();
            generate(button, pkg, tableModel, type);
        });
        return button;
    }

    private static void generate(JComponent parent, Package pkg, FootprintTableModel tableModel,
            Footprint.Generator type) {
        Footprint footprint = pkg.getFootprint();
        UiUtils.messageBoxOnException(() -> {
            try {
                if (!footprint.getPads().isEmpty()) {
                    int chosen = Dialogs.ask(parent, Dialogs.Tone.Warn, "alert", //$NON-NLS-1$
                            String.format(Translations.getString("PackageVisionForm.Replace.Title"), //$NON-NLS-1$
                                    footprint.getPads().size()),
                            Translations.getString("PackageVisionForm.Replace.What"), null, //$NON-NLS-1$
                            Dialogs.Choice.danger(Translations.getString("PackageVisionForm.Replace.Action"))); //$NON-NLS-1$
                    if (chosen != 0) {
                        return;
                    }
                    footprint.removeAllPads();
                }
                if (type == Footprint.Generator.Kicad) {
                    // Reading a .kicad_mod asks the user to pick a file, so the dialog stays on
                    // this side and the footprint is only handed the finished pads.
                    for (Pad pad : new KicadModImporter().getPads()) {
                        footprint.addPad(pad);
                    }
                }
                else {
                    footprint.generate(type);
                }
            }
            finally {
                tableModel.fireTableDataChanged();
                pkg.fireFootprintChanged();
            }
        });
    }

    /** The pads with Add, Mark and delete over them; the table takes the column's width. */
    private static JComponent pads(Footprint footprint, Package pkg, FootprintTableModel tableModel) {
        AutoSelectTextTable table = new AutoSelectTextTable(tableModel);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        AutoSelectTextTable.setEmptyText(table, Translations.getString("PackageVisionForm.Pads.Empty")); //$NON-NLS-1$

        JButton add = Ui.button(Translations.getString("PackageVisionForm.Pads.Add"), Ui.iconSm("plus"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Default);
        JButton mark = Ui.button(Translations.getString("PackageVisionForm.Pads.Mark"), Ui.iconSm("target"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Default);
        mark.setToolTipText(Translations.getString("PackageVisionForm.Pads.Mark.ToolTip")); //$NON-NLS-1$
        JButton delete = Ui.button("", Ui.iconSm("trash"), Ui.Size.Sm, Ui.Variant.Ghost); //$NON-NLS-1$ //$NON-NLS-2$
        delete.setToolTipText(Translations.getString("PackageVisionForm.Pads.Delete")); //$NON-NLS-1$
        mark.setEnabled(false);
        delete.setEnabled(false);
        table.getSelectionModel().addListSelectionListener(e -> {
            boolean one = table.getSelectedRow() >= 0;
            mark.setEnabled(one);
            delete.setEnabled(one);
        });
        add.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(table.getTopLevelAncestor(),
                    Translations.getString("PackageVisionWizard.NewPad.EnterName")); //$NON-NLS-1$
            if (name == null || name.trim().isEmpty()) {
                return;
            }
            Pad pad = new Pad();
            pad.setName(name.trim());
            footprint.addPad(pad);
            tableModel.fireTableDataChanged();
            pkg.fireFootprintChanged();
            Helpers.selectLastTableRow(table);
        });
        mark.addActionListener(e -> {
            Pad pad = selected(table, tableModel);
            if (pad != null) {
                footprint.toggleMark(pad);
                tableModel.fireTableDataChanged();
                pkg.fireFootprintChanged();
            }
        });
        delete.addActionListener(e -> {
            Pad pad = selected(table, tableModel);
            if (pad != null && Dialogs.confirmDelete(table.getTopLevelAncestor(), "Dialogs.Kind.Pads", //$NON-NLS-1$
                    List.of(pad.getName()), Translations.getString("PackageVisionForm.Pads.Delete.More"))) { //$NON-NLS-1$
                footprint.removePad(pad);
                tableModel.fireTableDataChanged();
                pkg.fireFootprintChanged();
            }
        });

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JPanel tools = Forms.row(add, mark, delete);
        panel.add(tools, BorderLayout.NORTH);
        JScrollPane scroll = DockPanel.table(table);
        scroll.setPreferredSize(new Dimension(200, 220));
        scroll.setMinimumSize(new Dimension(120, 120));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private static Pad selected(AutoSelectTextTable table, FootprintTableModel tableModel) {
        int row = table.getSelectedRow();
        return row < 0 ? null : tableModel.getPad(table.convertRowIndexToModel(row));
    }

    /** The footprint drawn over the top camera's image and the bottom cameras', to check it against. */
    private static void showReticle(Configuration configuration, Footprint footprint) {
        MainFrame frame = MainFrame.get();
        if (frame == null || configuration == null || configuration.getMachine() == null) {
            return;
        }
        try {
            show(frame, configuration.getMachine().getDefaultHead().getDefaultCamera(), footprint);
            for (Camera camera : configuration.getMachine().getCameras()) {
                show(frame, camera, footprint);
            }
        }
        catch (Exception e) {
            Logger.error(e, "Failed to show the footprint reticle in the camera views.");
        }
    }

    private static void show(MainFrame frame, Camera camera, Footprint footprint) {
        CameraView view = frame.getCameraViews().getCameraView(camera);
        if (view != null) {
            view.removeReticle(PackageVisionForm.class.getName());
            view.setReticle(PackageVisionForm.class.getName(), new FootprintReticle(footprint));
        }
    }
}
