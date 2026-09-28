/*
 * Copyright (C) 2026 Pono contributors
 * 
 * This file is part of Pono, a modified version of OpenPnP.
 * 
 * Pono is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * Pono is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Pono. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package org.openpnp.gui.theme;

import java.awt.Font;
import java.io.FileInputStream;

import javax.swing.UIManager;

import org.openpnp.gui.components.ThemeInfo;
import org.openpnp.gui.components.ThemeSettingsPanel;
import org.pmw.tinylog.Logger;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatPropertiesLaf;
import com.formdev.flatlaf.IntelliJTheme;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;

/** Puts a theme and a font size in place: at start, from the settings page and from the top bar. */
public final class Themes {
    private static final String THEMES_PACKAGE = "/com/formdev/flatlaf/intellijthemes/themes/"; //$NON-NLS-1$

    private Themes() {
    }

    /** @param fontSize The size to set, or null to keep the look and feel's. */
    public static void apply(ThemeInfo themeInfo, ThemeSettingsPanel.FontSize fontSize) {
        if (themeInfo == null) {
            return;
        }
        if (themeInfo.getLafClassName() != null) {
            // Resolved on every apply, not once when stored, so that the follow-the-system
            // entry picks up an OS appearance change without rewriting the preference.
            String lafClassName = PonoThemes.resolveLafClassName(themeInfo.getLafClassName());
            FlatAnimatedLafChange.showSnapshot();
            if (!lafClassName.equals(UIManager.getLookAndFeel().getClass().getName())) {
                if (lafClassName.equals("com.sun.java.swing.plaf.gtk.GTKLookAndFeel")) { //$NON-NLS-1$
                    UIManager.put("Slider.paintValue", Boolean.FALSE); //$NON-NLS-1$
                }
                try {
                    UIManager.setLookAndFeel(lafClassName);
                }
                catch (Exception e) {
                    Logger.error(e, "Failed to apply look and feel {}, keeping the current one.", lafClassName); //$NON-NLS-1$
                }
            }
        }
        else if (themeInfo.getThemeFile() != null) {
            FlatAnimatedLafChange.showSnapshot();
            try {
                if (themeInfo.getThemeFile().getName().endsWith(".properties")) { //$NON-NLS-1$
                    FlatLaf.install(new FlatPropertiesLaf(themeInfo.getName(), themeInfo.getThemeFile()));
                }
                else {
                    FlatLaf.install(IntelliJTheme.createLaf(new FileInputStream(themeInfo.getThemeFile())));
                }
            }
            catch (Exception e) {
                Logger.error(e, "Failed to apply theme file {}, keeping the current one.", themeInfo.getThemeFile()); //$NON-NLS-1$
            }
        }
        else if (themeInfo.getResourceName() != null) {
            FlatAnimatedLafChange.showSnapshot();
            IntelliJTheme.install(Themes.class.getResourceAsStream(THEMES_PACKAGE + themeInfo.getResourceName()));
        }
        if (fontSize != null) {
            Font font = UIManager.getDefaults().getFont("defaultFont"); //$NON-NLS-1$
            if (font != null) {
                UIManager.put("defaultFont", font.deriveFont((float) fontSize.getSize())); //$NON-NLS-1$
            }
        }
        // The mockups' tables separate rows by a hairline, not by stripes; the stripes also fought
        // the status capsules and the selection for attention. The stored preference is ignored.
        UIManager.put("Table.alternateRowColor", null); //$NON-NLS-1$
        FlatLaf.updateUI();
        FlatAnimatedLafChange.hideSnapshotWithAnimation();
    }
}
