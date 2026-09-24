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
import java.util.Date;

import org.openpnp.model.Configuration;
import org.pmw.tinylog.Logger;

/**
 * The machine's configuration as it was before a change the page makes all at once - the nozzles
 * rebuilt, a preset applied - saved first and copied beside itself, as the calibration does.
 */
final class Backups {
    private Backups() {
    }

    /** machine.xml.before-nozzles-2026-09-24_20.10.05, in the configuration directory. */
    static File backup(Configuration configuration, String what) throws Exception {
        configuration.save();
        File directory = configuration.getConfigurationDirectory();
        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()); //$NON-NLS-1$
        File backup = new File(directory, "machine.xml.before-" + what + "-" + stamp); //$NON-NLS-1$ //$NON-NLS-2$
        Files.copy(new File(directory, "machine.xml").toPath(), backup.toPath(), //$NON-NLS-1$
                StandardCopyOption.COPY_ATTRIBUTES);
        Logger.info("Machine configuration backed up to {}", backup); //$NON-NLS-1$
        return backup;
    }
}
