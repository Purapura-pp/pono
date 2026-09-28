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

package org.openpnp.model;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.pmw.tinylog.Logger;

/**
 * The configuration's files as they were before a change the program makes all at once -
 * suggestions applied, the nozzles rebuilt, a preset applied, a calibration run, a frame
 * compensation - each backup a folder under backups named by its time and what made it, beside the
 * folders saving makes there. They used to be copied beside machine.xml as
 * machine.xml.before-what-time, in three different time formats, where nothing listed them and
 * nothing restored them.
 */
public final class Backups {
    /** The time a backup folder is named by, the one saving names its folders by. */
    public static final String STAMP = "yyyy-MM-dd_HH.mm.ss"; //$NON-NLS-1$
    /** What made a folder that saving made. */
    public static final String SAVED = "save"; //$NON-NLS-1$
    /** The files a restore puts back: the ones starting again does not write over. */
    public static final List<String> RESTORED = List.of("machine.xml", "vision-settings.xml"); //$NON-NLS-1$ //$NON-NLS-2$

    private Backups() {
    }

    /** One folder of backed up files: when, what made it, and the files in it. */
    public static final class Backup {
        public final File folder;
        public final Date when;
        /** {@link #SAVED}, or the name the change gave itself: "nozzles", "preset", ... */
        public final String source;

        Backup(File folder, Date when, String source) {
            this.folder = folder;
            this.when = when;
            this.source = source;
        }

        /** The files a restore would put back, of those this backup holds. */
        public List<File> restorable() {
            List<File> files = new ArrayList<>();
            for (String name : RESTORED) {
                File file = new File(folder, name);
                if (file.isFile()) {
                    files.add(file);
                }
            }
            return files;
        }
    }

    /** Where the backups go: backups beside the configuration, or where -Dbackups says. */
    public static File directory(File configurationDirectory) {
        String elsewhere = System.getProperty("backups"); //$NON-NLS-1$
        return elsewhere != null ? new File(elsewhere) : new File(configurationDirectory, "backups"); //$NON-NLS-1$
    }

    /** machine.xml before a change, saved first if it has changes not written yet. */
    public static File backup(Configuration configuration, String what) throws Exception {
        if (configuration.isDirty()) {
            configuration.save();
        }
        return copy(configuration.getConfigurationDirectory(), what, "machine.xml"); //$NON-NLS-1$
    }

    /** machine.xml and vision-settings.xml before a change that writes both: a preset applied. */
    public static List<File> backupMachineFiles(Configuration configuration, String what) throws Exception {
        if (configuration.isDirty()) {
            configuration.save();
        }
        File folder = copy(configuration.getConfigurationDirectory(), what, RESTORED.toArray(new String[0]));
        List<File> files = new ArrayList<>();
        for (String name : RESTORED) {
            File file = new File(folder, name);
            if (file.isFile()) {
                files.add(file);
            }
        }
        return files;
    }

    /**
     * The files as they are on disk, without saving first: the configuration before what is in
     * effect but not to be written yet, calibration results waiting to be applied.
     *
     * @return The backup's folder.
     */
    public static File copy(File configurationDirectory, String what, String... names) throws Exception {
        File folder = new File(directory(configurationDirectory),
                new SimpleDateFormat(STAMP).format(new Date()) + "-" + what); //$NON-NLS-1$
        for (int n = 2; folder.exists(); n++) {
            folder = new File(folder.getParentFile(), folder.getName().replaceAll("~\\d+$", "") + "~" + n); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        folder.mkdirs();
        for (String name : names) {
            File file = new File(configurationDirectory, name);
            if (file.isFile()) {
                Files.copy(file.toPath(), new File(folder, name).toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
        Logger.info("{} backed up to {}", Arrays.toString(names), folder); //$NON-NLS-1$
        return folder;
    }

    /** The backups there are, newest first. */
    public static List<Backup> list(File configurationDirectory) {
        List<Backup> backups = new ArrayList<>();
        File[] folders = directory(configurationDirectory).listFiles(File::isDirectory);
        if (folders == null) {
            return backups;
        }
        for (File folder : folders) {
            String name = folder.getName();
            if (name.length() < STAMP.length()) {
                continue;
            }
            Date when;
            try {
                when = new SimpleDateFormat(STAMP).parse(name.substring(0, STAMP.length()));
            }
            catch (ParseException e) {
                continue;
            }
            String rest = name.substring(STAMP.length());
            String source = rest.startsWith("-") ? rest.substring(1).replaceAll("~\\d+$", "") : SAVED; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            backups.add(new Backup(folder, when, source));
        }
        backups.sort(Comparator.comparing((Backup b) -> b.when).thenComparing(b -> b.folder.getName()).reversed());
        return backups;
    }

    /**
     * Puts backed up files back where they were, over what was written since.
     *
     * @return The backups that could not be put back, which the user is to copy back by hand.
     */
    public static List<File> restore(File configurationDirectory, List<File> backups) {
        List<File> left = new ArrayList<>();
        for (File backup : backups) {
            File original = new File(configurationDirectory, backup.getName());
            try {
                Files.copy(backup.toPath(), original.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            catch (Exception e) {
                Logger.error(e, "Could not put {} back from {}.", original, backup); //$NON-NLS-1$
                left.add(backup);
            }
        }
        return left;
    }
}
