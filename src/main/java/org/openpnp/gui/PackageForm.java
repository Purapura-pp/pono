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

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.openpnp.Translations;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Ui;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.Configuration;
import org.openpnp.model.Package;
import org.openpnp.spi.NozzleTip;

/**
 * A package's settings: what it is, the vacuum it is picked and placed with, and the nozzle tips
 * that can pick it. The two were separate sheets - two fields in a titled border, and a table of
 * nozzle tips with a check box each.
 */
public final class PackageForm {
    private PackageForm() {
    }

    public static class Bean extends AbstractModelObject {
        private final Package packag;

        Bean(Package packag) {
            this.packag = packag;
        }

        public String getId() {
            return packag.getId();
        }

        public String getDescription() {
            return packag.getDescription();
        }

        public void setDescription(String description) {
            packag.setDescription(description);
        }

        public String getTapeSpecification() {
            return packag.getTapeSpecification();
        }

        public void setTapeSpecification(String tape) {
            packag.setTapeSpecification(tape);
        }

        public double getPickVacuumLevel() {
            return packag.getPickVacuumLevel();
        }

        public void setPickVacuumLevel(double level) {
            packag.setPickVacuumLevel(level);
        }

        public double getPlaceBlowOffLevel() {
            return packag.getPlaceBlowOffLevel();
        }

        public void setPlaceBlowOffLevel(double level) {
            packag.setPlaceBlowOffLevel(level);
        }
    }

    public static FormWizard build(Configuration configuration, Package packag) {
        return Form.of(new Bean(packag)).named(packag.getId())
                .section("PackageForm.Basics", "info") //$NON-NLS-1$ //$NON-NLS-2$
                .readOnly("id", "PackageForm.Id") //$NON-NLS-1$ //$NON-NLS-2$
                .text("description", "PackageForm.Description") //$NON-NLS-1$ //$NON-NLS-2$
                .text("tapeSpecification", "PackageForm.Tape") //$NON-NLS-1$ //$NON-NLS-2$
                .section("PackageForm.Vacuum", "nozzle") //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("pickVacuumLevel", "PackageForm.PickVacuum").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("placeBlowOffLevel", "PackageForm.PlaceBlowOff").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .section("PackageForm.NozzleTips", "nozzle") //$NON-NLS-1$ //$NON-NLS-2$
                .note(Translations.getString("PackageForm.NozzleTips.Note")) //$NON-NLS-1$
                .custom("", nozzleTips(configuration, packag)) //$NON-NLS-1$
                .build();
    }

    /**
     * A switch for each nozzle tip, written as it is flipped, as the check boxes were: whether a
     * tip can pick a package is not something to hold back until Apply.
     */
    private static JComponent nozzleTips(Configuration configuration, Package packag) {
        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        if (configuration.getMachine().getNozzleTips().isEmpty()) {
            list.add(Ui.muted(Translations.getString("PackageForm.NoNozzleTips"))); //$NON-NLS-1$
        }
        for (NozzleTip tip : configuration.getMachine().getNozzleTips()) {
            Forms.Toggle toggle = new Forms.Toggle();
            toggle.setSelected(packag.getCompatibleNozzleTips().contains(tip));
            toggle.onChange(() -> {
                if (toggle.isSelected()) {
                    packag.addCompatibleNozzleTip(tip);
                }
                else {
                    packag.removeCompatibleNozzleTip(tip);
                }
                configuration.setDirty(true);
            });
            JPanel row = Forms.toggleRow(toggle, tip.getName());
            row.setAlignmentX(0);
            list.add(row);
        }
        return list;
    }
}
