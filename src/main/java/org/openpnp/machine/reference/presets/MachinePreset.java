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

package org.openpnp.machine.reference.presets;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Properties;

import org.openpnp.Translations;

/**
 * A machine definition to set a machine from: one that comes with Pono, read from its resources,
 * or one of the user's, a folder in the configuration directory. Either is a machine.xml, perhaps
 * a vision-settings.xml, and a preset.properties saying what it is.
 */
public final class MachinePreset {
    static final String PROPERTIES = "preset.properties"; //$NON-NLS-1$
    static final String MACHINE = "machine.xml"; //$NON-NLS-1$
    static final String VISION = "vision-settings.xml"; //$NON-NLS-1$

    private final String id;
    private final boolean builtIn;
    private final File directory;
    private final String resources;
    private final Properties properties;

    private MachinePreset(String id, boolean builtIn, File directory, String resources, Properties properties) {
        this.id = id;
        this.builtIn = builtIn;
        this.directory = directory;
        this.resources = resources;
        this.properties = properties;
    }

    static MachinePreset builtIn(String id, String resources, Properties properties) {
        return new MachinePreset(id, true, null, resources, properties);
    }

    static MachinePreset user(File directory, Properties properties) {
        return new MachinePreset(directory.getName(), false, directory, null, properties);
    }

    public String getId() {
        return id;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    /** The folder of one of the user's; null for a built-in one. */
    public File getDirectory() {
        return directory;
    }

    public String getName() {
        return properties.getProperty("name", id); //$NON-NLS-1$
    }

    /** Who makes the machine, for a built-in preset. */
    public String getVendor() {
        return properties.getProperty("vendor", ""); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** What it says of itself: a built-in one in the display language, the user's as written. */
    public String getDescription() {
        String key = properties.getProperty("description.key"); //$NON-NLS-1$
        if (key != null && Translations.has(key)) {
            return Translations.getString(key);
        }
        return properties.getProperty("description", ""); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** When one of the user's was made, as yyyy-MM-dd HH:mm; empty for a built-in one. */
    public String getCreated() {
        return properties.getProperty("created", ""); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** Where one of the user's came from: "current", or the folder it was imported from. */
    public String getSource() {
        return properties.getProperty("source", ""); //$NON-NLS-1$ //$NON-NLS-2$
    }

    Properties properties() {
        return properties;
    }

    public InputStream openMachine() throws IOException {
        return open(MACHINE);
    }

    public boolean hasVision() {
        if (builtIn) {
            return MachinePreset.class.getResource(resources + VISION) != null;
        }
        return new File(directory, VISION).isFile();
    }

    public InputStream openVision() throws IOException {
        return open(VISION);
    }

    private InputStream open(String name) throws IOException {
        if (builtIn) {
            InputStream in = MachinePreset.class.getResourceAsStream(resources + name);
            if (in == null) {
                throw new FileNotFoundException(resources + name);
            }
            return in;
        }
        return Files.newInputStream(new File(directory, name).toPath());
    }

    @Override
    public String toString() {
        return getName();
    }
}
