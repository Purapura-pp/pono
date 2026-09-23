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

package org.openpnp.gui.support;

import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.spi.base.AbstractFeeder;

/**
 * What a feeder has left, when it was last picked from, the count at which it asks for more,
 * and the refill: the same sheet for every kind of feeder whose own form does not have them.
 */
public final class FeederStockForm {
    private FeederStockForm() {
    }

    public static class Bean extends AbstractModelObject {
        private final AbstractFeeder feeder;

        Bean(AbstractFeeder feeder) {
            this.feeder = feeder;
        }

        public String getSlotName() {
            return feeder.getSlotName();
        }

        public void setSlotName(String slotName) {
            feeder.setSlotName(slotName);
        }

        public String getPartsLeft() {
            return FeederDescriptions.partsLeft(feeder);
        }

        public int getLoadedCount() {
            return feeder.getLoadedCount();
        }

        public void setLoadedCount(int loadedCount) {
            feeder.setLoadedCount(loadedCount);
        }

        public int getLowCount() {
            return feeder.getLowCount();
        }

        public void setLowCount(int lowCount) {
            feeder.setLowCount(lowCount);
        }

        public String getLastPick() {
            return FeederDescriptions.when(feeder.getLastPickMillis());
        }
    }

    public static FormWizard build(AbstractFeeder feeder) {
        FormWizard[] form = new FormWizard[1];
        Form.Builder builder = Form.of(new Bean(feeder)).named(feeder.getName())
                .section("FeederStockForm.Section", "layers"); //$NON-NLS-1$ //$NON-NLS-2$
        if (FeederDescriptions.slotEditable(feeder)) {
            builder.text("slotName", "FeederStockForm.Slot").width(100); //$NON-NLS-1$ //$NON-NLS-2$
        }
        builder.readOnly("partsLeft", "FeederStockForm.PartsLeft"); //$NON-NLS-1$ //$NON-NLS-2$
        if (!feeder.isCountedFromGeometry()) {
            builder.integer("loadedCount", "FeederStockForm.LoadedCount").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                    .note("FeederStockForm.LoadedCount.Note") //$NON-NLS-1$
                    .validate(FeederStockForm::nonNegative, "FeederStockForm.NotNegative"); //$NON-NLS-1$
        }
        builder.integer("lowCount", "FeederStockForm.LowCount").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .note("FeederStockForm.LowCount.Note") //$NON-NLS-1$
                .validate(FeederStockForm::nonNegative, "FeederStockForm.NotNegative") //$NON-NLS-1$
                .readOnly("lastPick", "FeederStockForm.LastPick") //$NON-NLS-1$ //$NON-NLS-2$
                .action("FeederStockForm.Refill", "refresh", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                    feeder.refill(null);
                    form[0].reload();
                });
        form[0] = builder.build();
        return form[0];
    }

    static boolean nonNegative(Object value) {
        try {
            return Integer.parseInt(String.valueOf(value).trim()) >= 0;
        }
        catch (NumberFormatException e) {
            return false;
        }
    }
}
