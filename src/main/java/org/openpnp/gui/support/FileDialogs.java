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

package org.openpnp.gui.support;

import java.awt.FileDialog;
import java.util.Locale;

/**
 * A file dialog that says what it is for and lists only the files it can take.
 * <p>
 * The dialogs had no title, and a filename filter, which the Windows dialog ignores: opening a
 * job listed every file in the folder. On Windows the filter has to be the file name's wildcard,
 * {@code *.job.xml}, which is also shown in the name box; elsewhere the filename filter does it.
 */
public final class FileDialogs {
    private FileDialogs() {
    }

    /**
     * @param title    What the dialog is for, "Open job".
     * @param suffixes The file name endings it lists, ".job.xml"; none for any file.
     */
    public static FileDialog prepare(FileDialog dialog, String title, String... suffixes) {
        dialog.setTitle(title);
        if (suffixes.length > 0) {
            dialog.setFilenameFilter((dir, name) -> matches(name, suffixes));
            StringBuilder wildcard = new StringBuilder();
            for (String suffix : suffixes) {
                wildcard.append(wildcard.length() > 0 ? ";" : "").append('*').append(suffix); //$NON-NLS-1$ //$NON-NLS-2$
            }
            dialog.setFile(wildcard.toString());
        }
        return dialog;
    }

    /** Whether a file name ends in one of the suffixes, whatever its case. */
    public static boolean matches(String name, String... suffixes) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String suffix : suffixes) {
            if (lower.endsWith(suffix.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
