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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import org.w3c.dom.Document;

/**
 * The presets there are, and what can be done with them: the ones Pono comes with, listed in
 * its resources; the user's own in the presets folder of the configuration directory, made from
 * the machine as it is or from another configuration's folder, renamed and deleted there; and
 * applying one, which writes its machine definition into a configuration directory with what
 * that configuration keeps - its feeders, this computer's port and cameras - left in.
 */
public final class MachinePresets {
    /** The folder of the user's presets, in the configuration directory. */
    public static final String DIRECTORY = "presets"; //$NON-NLS-1$
    private static final String RESOURCES = "/config/presets/"; //$NON-NLS-1$

    private MachinePresets() {
    }

    /** What a new preset takes along besides the machine's definition. */
    public static final class Options {
        public boolean vision = true;
        public boolean records;
        public boolean feeders;
    }

    // ---- listing ------------------------------------------------------------------------------

    public static List<MachinePreset> builtIn() {
        List<MachinePreset> presets = new ArrayList<>();
        InputStream index = MachinePresets.class.getResourceAsStream(RESOURCES + "index.txt"); //$NON-NLS-1$
        if (index == null) {
            return presets;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(index, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String id = line.trim();
                if (id.isEmpty() || id.startsWith("#")) { //$NON-NLS-1$
                    continue;
                }
                String base = RESOURCES + id + "/"; //$NON-NLS-1$
                InputStream in = MachinePresets.class.getResourceAsStream(base + MachinePreset.PROPERTIES);
                if (in == null) {
                    continue;
                }
                presets.add(MachinePreset.builtIn(id, base, load(in)));
            }
        }
        catch (IOException e) {
            org.pmw.tinylog.Logger.warn(e, "The built-in presets could not be listed."); //$NON-NLS-1$
        }
        return presets;
    }

    public static List<MachinePreset> user(File configurationDirectory) {
        List<MachinePreset> presets = new ArrayList<>();
        File[] folders = new File(configurationDirectory, DIRECTORY).listFiles(File::isDirectory);
        if (folders == null) {
            return presets;
        }
        for (File folder : folders) {
            File properties = new File(folder, MachinePreset.PROPERTIES);
            if (!properties.isFile() || !new File(folder, MachinePreset.MACHINE).isFile()) {
                continue;
            }
            try {
                presets.add(MachinePreset.user(folder, load(Files.newInputStream(properties.toPath()))));
            }
            catch (IOException e) {
                org.pmw.tinylog.Logger.warn(e, "The preset in {} could not be read.", folder); //$NON-NLS-1$
            }
        }
        presets.sort(Comparator.comparing(MachinePreset::getCreated).thenComparing(MachinePreset::getName));
        return presets;
    }

    private static Properties load(InputStream in) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static void store(Properties properties, File folder) throws IOException {
        try (Writer writer = Files.newBufferedWriter(new File(folder, MachinePreset.PROPERTIES).toPath(),
                StandardCharsets.UTF_8)) {
            properties.store(writer, "Pono machine preset"); //$NON-NLS-1$
        }
    }

    /** Whether a folder is a configuration a preset can be made from: it has a machine.xml. */
    public static boolean isConfiguration(File folder) {
        return folder != null && new File(folder, MachinePreset.MACHINE).isFile();
    }

    // ---- the user's -------------------------------------------------------------------------------

    /**
     * Makes one of the user's from the configuration in a folder - the one in use, saved first by
     * the caller, or another's - without what belongs to that machine on that computer.
     *
     * @param source "current", or the folder's path, to say where it came from.
     */
    public static MachinePreset create(File configurationDirectory, File from, String source, String name,
            String description, Options options) throws Exception {
        if (!isConfiguration(from)) {
            throw new IOException("No machine.xml in " + from); //$NON-NLS-1$
        }
        File folder = newFolder(new File(configurationDirectory, DIRECTORY), name);
        Document machine = PresetXml.parse(new File(from, MachinePreset.MACHINE));
        PresetXml.stripForPreset(machine, options.feeders, options.records);
        PresetXml.write(machine, new File(folder, MachinePreset.MACHINE));
        File vision = new File(from, MachinePreset.VISION);
        if (options.vision && vision.isFile()) {
            Files.copy(vision.toPath(), new File(folder, MachinePreset.VISION).toPath());
        }
        Properties properties = new Properties();
        properties.setProperty("name", name.trim()); //$NON-NLS-1$
        properties.setProperty("description", description == null ? "" : description.trim()); //$NON-NLS-1$ //$NON-NLS-2$
        properties.setProperty("created", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())); //$NON-NLS-1$ //$NON-NLS-2$
        properties.setProperty("source", source); //$NON-NLS-1$
        store(properties, folder);
        return MachinePreset.user(folder, properties);
    }

    /** A folder named after the preset, with a number after it when that name is taken. */
    private static File newFolder(File presets, String name) throws IOException {
        String base = name.trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "-").replaceAll("^-+|-+$", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        if (base.isEmpty()) {
            base = "preset"; //$NON-NLS-1$
        }
        File folder = new File(presets, base);
        for (int i = 2; folder.exists(); i++) {
            folder = new File(presets, base + "-" + i); //$NON-NLS-1$
        }
        Files.createDirectories(folder.toPath());
        return folder;
    }

    public static MachinePreset rename(MachinePreset preset, String name) throws IOException {
        if (preset.isBuiltIn()) {
            throw new IOException("A built-in preset cannot be renamed."); //$NON-NLS-1$
        }
        Properties properties = new Properties();
        properties.putAll(preset.properties());
        properties.setProperty("name", name.trim()); //$NON-NLS-1$
        store(properties, preset.getDirectory());
        return MachinePreset.user(preset.getDirectory(), properties);
    }

    public static void delete(MachinePreset preset) throws IOException {
        if (preset.isBuiltIn()) {
            throw new IOException("A built-in preset cannot be deleted."); //$NON-NLS-1$
        }
        try (Stream<Path> paths = Files.walk(preset.getDirectory().toPath())) {
            List<Path> all = new ArrayList<>();
            paths.forEach(all::add);
            all.sort(Comparator.reverseOrder());
            for (Path path : all) {
                Files.delete(path);
            }
        }
    }

    // ---- applying ---------------------------------------------------------------------------------

    /**
     * Writes the preset's machine definition into the configuration directory, over its
     * machine.xml: the feeders of the machine there and this computer's port and cameras are
     * carried over, the preset's vision settings are merged in by id, and nothing else there is
     * touched - the parts, packages, boards and jobs are the user's. The configuration's own files
     * are to be saved, and backed up, before.
     */
    public static void apply(MachinePreset preset, File configurationDirectory) throws Exception {
        File machineFile = new File(configurationDirectory, MachinePreset.MACHINE);
        Document machine;
        try (InputStream in = preset.openMachine()) {
            machine = PresetXml.parse(in);
        }
        if (machineFile.isFile()) {
            PresetXml.carryOver(machine, PresetXml.parse(machineFile), preset.getName(), preset.isBuiltIn());
        }
        else {
            PresetXml.machine(machine).setAttribute("preset-name", preset.getName()); //$NON-NLS-1$
            PresetXml.machine(machine).setAttribute("preset-built-in", String.valueOf(preset.isBuiltIn())); //$NON-NLS-1$
        }
        if (preset.hasVision()) {
            File visionFile = new File(configurationDirectory, MachinePreset.VISION);
            Document vision;
            try (InputStream in = preset.openVision()) {
                vision = PresetXml.parse(in);
            }
            if (visionFile.isFile()) {
                Document current = PresetXml.parse(visionFile);
                PresetXml.mergeVision(current, vision);
                vision = current;
            }
            PresetXml.write(vision, visionFile);
        }
        PresetXml.write(machine, machineFile);
    }

    /** What a preset's machine is, in the words of its card. */
    public static PresetXml.Summary summary(MachinePreset preset) throws Exception {
        try (InputStream in = preset.openMachine()) {
            return PresetXml.summary(PresetXml.parse(in));
        }
    }

    /** What the machine in a configuration directory is, for comparing it with a preset. */
    public static PresetXml.Summary summary(File configurationDirectory) throws Exception {
        return PresetXml.summary(PresetXml.parse(new File(configurationDirectory, MachinePreset.MACHINE)));
    }
}
