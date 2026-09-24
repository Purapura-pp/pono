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

package org.openpnp.gui.machinesettings;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.openpnp.model.Configuration;
import org.pmw.tinylog.Logger;

/**
 * The machine's configuration as it was before a change the page makes all at once - the nozzles
 * rebuilt, a preset applied - saved first and copied beside itself, as the calibration does.
 */
public final class Backups {
    private Backups() {
    }

    /** machine.xml.before-nozzles-2026-09-24_20.10.05, in the configuration directory. */
    public static File backup(Configuration configuration, String what) throws Exception {
        configuration.save();
        return copy(configuration.getConfigurationDirectory(), "machine.xml", what, stamp()); //$NON-NLS-1$
    }

    /**
     * machine.xml and vision-settings.xml, saved first and each copied beside itself, for a change
     * that writes both: a preset applied.
     */
    static List<File> backupMachineFiles(Configuration configuration, String what) throws Exception {
        configuration.save();
        String stamp = stamp();
        List<File> backups = new ArrayList<>();
        for (String name : new String[] { "machine.xml", "vision-settings.xml" }) { //$NON-NLS-1$ //$NON-NLS-2$
            if (new File(configuration.getConfigurationDirectory(), name).isFile()) {
                backups.add(copy(configuration.getConfigurationDirectory(), name, what, stamp));
            }
        }
        return backups;
    }

    /** Puts backed up files back where they were, over what was written since. */
    static void restore(File directory, List<File> backups) {
        for (File backup : backups) {
            String name = backup.getName();
            File original = new File(directory, name.substring(0, name.indexOf(".before-"))); //$NON-NLS-1$
            try {
                Files.copy(backup.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            catch (Exception e) {
                Logger.error(e, "Could not put {} back from {}.", original, backup); //$NON-NLS-1$
            }
        }
    }

    private static String stamp() {
        return new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()); //$NON-NLS-1$
    }

    private static File copy(File directory, String name, String what, String stamp) throws Exception {
        File backup = new File(directory, name + ".before-" + what + "-" + stamp); //$NON-NLS-1$ //$NON-NLS-2$
        Files.copy(new File(directory, name).toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        Logger.info("{} backed up to {}", name, backup); //$NON-NLS-1$
        return backup;
    }
}
