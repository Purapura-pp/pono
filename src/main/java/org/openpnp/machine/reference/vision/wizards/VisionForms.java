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

package org.openpnp.machine.reference.vision.wizards;

import java.util.ArrayList;
import java.util.List;

import javax.swing.ComboBoxModel;
import javax.swing.SwingUtilities;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.support.VisionSettingsComboBoxModel;
import org.openpnp.machine.reference.vision.OpenCvVisionProvider;
import org.openpnp.machine.reference.vision.ReferenceBottomVision;
import org.openpnp.machine.reference.vision.ReferenceFiducialLocator;
import org.openpnp.model.BottomVisionSettings;
import org.openpnp.model.FiducialVisionSettings;
import org.openpnp.model.AbstractVisionSettings;

/**
 * The machine's vision: bottom vision's defaults, the fiducial locator's, and the OpenCV
 * provider, which has nothing to set.
 */
public final class VisionForms {
    private VisionForms() {
    }

    /** The vision settings of a kind, as the settings list offers them. */
    private static <T extends AbstractVisionSettings> List<Object> settings(Class<T> kind) {
        ComboBoxModel<?> model = new VisionSettingsComboBoxModel(kind);
        List<Object> items = new ArrayList<>();
        for (int i = 0; i < model.getSize(); i++) {
            items.add(model.getElementAt(i));
        }
        return items;
    }

    /** Other settings bring other tabs: the tree shows the object again. */
    private static void reselect() {
        SwingUtilities.invokeLater(() -> { if (MainFrame.get() != null) { MainFrame.get().getMachineSetupTab().selectCurrentTreePath(); } });
    }

    public static FormWizard bottomVision(ReferenceBottomVision vision) {
        Object[] settings = {vision.getBottomVisionSettings()};
        return Form.of(vision).named("ReferenceBottomVisionConfigurationWizard.wizardName") //$NON-NLS-1$
                .section("VisionForms.Bottom", "eye") //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("enabled", "VisionForms.Bottom.Enabled", "VisionForms.Bottom.Enabled.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .choice("bottomVisionSettings", "VisionForms.Settings", settings(BottomVisionSettings.class), null) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("VisionForms.Bottom.Settings.Hint") //$NON-NLS-1$
                .toggle("preRotate", "VisionForms.Bottom.PreRotate", "VisionForms.Bottom.PreRotate.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .section("VisionForms.Bottom.Passes", "refresh") //$NON-NLS-1$ //$NON-NLS-2$
                .integer("maxVisionPasses", "VisionForms.Bottom.MaxPasses").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("ReferenceBottomVisionConfigurationWizard.GeneralPanel.MaxVisionPassesLabel.toolTipText") //$NON-NLS-1$
                .length("maxLinearOffset", "VisionForms.Bottom.MaxLinear").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .angle("maxAngularOffset", "VisionForms.Bottom.MaxAngular").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("VisionForms.Bottom.Good.Hint") //$NON-NLS-1$
                .onApply(f -> {
                    if (vision.getBottomVisionSettings() != settings[0]) {
                        settings[0] = vision.getBottomVisionSettings();
                        reselect();
                    }
                })
                .build();
    }

    public static FormWizard fiducialLocator(ReferenceFiducialLocator locator) {
        Object[] settings = {locator.getFiducialVisionSettings()};
        return Form.of(locator).named("ReferenceFiducialLocatorConfigurationWizard.wizardName") //$NON-NLS-1$
                .section("VisionForms.Fiducial", "target") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("fiducialVisionSettings", "VisionForms.Settings", settings(FiducialVisionSettings.class), null) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("VisionForms.Fiducial.Settings.Hint") //$NON-NLS-1$
                .toggle("enabledAveraging", "VisionForms.Fiducial.Average", "VisionForms.Fiducial.Average.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .length("maxDistance", "VisionForms.Fiducial.MaxDistance").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("VisionForms.Fiducial.MaxDistance.Hint") //$NON-NLS-1$
                .onApply(f -> {
                    if (locator.getFiducialVisionSettings() != settings[0]) {
                        settings[0] = locator.getFiducialVisionSettings();
                        reselect();
                    }
                })
                .build();
    }

    public static FormWizard openCv(OpenCvVisionProvider provider) {
        return Form.of(provider).named("VisionForms.OpenCv") //$NON-NLS-1$
                .section("VisionForms.OpenCv", "eye") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", Forms.paragraph(Translations.getString("VisionForms.OpenCv.Nothing"))) //$NON-NLS-1$ //$NON-NLS-2$
                .build();
    }
}
