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

import org.openpnp.Translations;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.Configuration;
import org.openpnp.model.Package;
import org.openpnp.spi.NozzleTip;

/**
 * A package's settings: what it is, the vacuum it is picked and placed with, and the nozzle tips
 * that can pick it, written as the rest is when Apply is pressed. The two were separate sheets -
 * two fields in a titled border, and a table of nozzle tips with a check box each written as it
 * was clicked.
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

        /** The nozzle tips that can pick it: Apply adds the ones switched on and removes the others. */
        public java.util.Set<NozzleTip> getCompatibleNozzleTips() {
            return new java.util.LinkedHashSet<>(packag.getCompatibleNozzleTips());
        }

        public void setCompatibleNozzleTips(java.util.Set<NozzleTip> tips) {
            for (NozzleTip tip : new java.util.ArrayList<>(packag.getCompatibleNozzleTips())) {
                if (!tips.contains(tip)) {
                    packag.removeCompatibleNozzleTip(tip);
                }
            }
            for (NozzleTip tip : tips) {
                if (!packag.getCompatibleNozzleTips().contains(tip)) {
                    packag.addCompatibleNozzleTip(tip);
                }
            }
        }
    }

    public static FormWizard build(Configuration configuration, Package packag) {
        java.util.List<NozzleTip> tips = new java.util.ArrayList<>(configuration.getMachine().getNozzleTips());
        Form.Builder form = Form.of(new Bean(packag)).named(packag.getId())
                .section("PackageForm.Basics", "info") //$NON-NLS-1$ //$NON-NLS-2$
                .readOnly("id", "PackageForm.Id") //$NON-NLS-1$ //$NON-NLS-2$
                .text("description", "PackageForm.Description") //$NON-NLS-1$ //$NON-NLS-2$
                .text("tapeSpecification", "PackageForm.Tape") //$NON-NLS-1$ //$NON-NLS-2$
                .section("PackageForm.Vacuum", "nozzle") //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("pickVacuumLevel", "PackageForm.PickVacuum").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("placeBlowOffLevel", "PackageForm.PlaceBlowOff").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .section("PackageForm.NozzleTips", "nozzle") //$NON-NLS-1$ //$NON-NLS-2$
                .note(Translations.getString("PackageForm.NozzleTips.Note")) //$NON-NLS-1$
                .checklist("compatibleNozzleTips", "NozzleForm.Compatible.Label", tips, null); //$NON-NLS-1$ //$NON-NLS-2$
        if (tips.isEmpty()) {
            form.hint("PackageForm.NoNozzleTips"); //$NON-NLS-1$
        }
        return form.build();
    }
}
