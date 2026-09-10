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

import org.openpnp.Translations;
import org.openpnp.gui.components.ThemeInfo;

/**
 * The three Pono entries offered at the top of the theme list.
 */
public final class PonoThemes {
    /**
     * Stored in {@code ThemeInfo.lafClassName} for the follow-the-system entry. It is a
     * marker rather than a loadable class: it has to survive Java serialization into the
     * preferences, and {@link #resolveLafClassName} maps it to the light or dark look and
     * feel every time the theme is applied. That indirection is what lets one stored
     * preference follow the OS.
     */
    public static final String FOLLOW_SYSTEM_LAF = "org.openpnp.gui.theme.PonoSystemLaf";

    private PonoThemes() {
    }

    /** The default for a fresh installation. */
    public static ThemeInfo followSystem() {
        return new ThemeInfo(Translations.getString("Theme.Pono.FollowSystem"), null, //$NON-NLS-1$
                SystemTheme.isDark(), null, FOLLOW_SYSTEM_LAF);
    }

    public static ThemeInfo light() {
        return new ThemeInfo(Translations.getString("Theme.Pono.Light"), null, false, null, //$NON-NLS-1$
                PonoLightLaf.class.getName());
    }

    public static ThemeInfo dark() {
        return new ThemeInfo(Translations.getString("Theme.Pono.Dark"), null, true, null, //$NON-NLS-1$
                PonoDarkLaf.class.getName());
    }

    /**
     * @return the look and feel class that a stored entry actually installs. Only the
     *         follow-the-system marker is translated; every other name is returned as is.
     */
    public static String resolveLafClassName(String lafClassName) {
        if (!FOLLOW_SYSTEM_LAF.equals(lafClassName)) {
            return lafClassName;
        }
        return SystemTheme.isDark() ? PonoDarkLaf.class.getName() : PonoLightLaf.class.getName();
    }
}
