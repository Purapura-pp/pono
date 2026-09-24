package org.openpnp.machine.reference.presets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.machine.reference.ReferenceHead.NozzleSolution;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.model.Configuration;
import org.openpnp.util.Relaunch;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Machine presets: the LumenPnP one Pono comes with, one made from a configuration without what
 * belongs to that machine on that computer, and applying one - the machine's definition replaced,
 * the feeders, this computer's port and cameras and the user's vision settings kept - into a
 * configuration the program loads.
 */
public class MachinePresetsTest {
    @TempDir
    Path tempDir;

    private static MachinePreset lumen() {
        for (MachinePreset preset : MachinePresets.builtIn()) {
            if (preset.getName().equals("LumenPnP v4.1")) {
                return preset;
            }
        }
        throw new AssertionError("no built-in LumenPnP preset");
    }

    /** A configuration folder holding the LumenPnP machine with what a real one on this computer has. */
    private File lumenConfiguration(String name, boolean withFeeder) throws Exception {
        File folder = tempDir.resolve(name).toFile();
        folder.mkdirs();
        Document machine;
        try (InputStream in = lumen().openMachine()) {
            machine = PresetXml.parse(in);
        }
        for (Element serial : PresetXml.descendants(PresetXml.machine(machine), "serial")) {
            serial.setAttribute("port-name", "COM5");
        }
        for (Element camera : PresetXml.descendants(PresetXml.machine(machine), "camera")) {
            if (camera.getAttribute("name").equals("Top")) {
                camera.setAttribute("unique-id", "top-camera-on-this-computer");
                camera.setAttribute("format-id", "3");
            }
        }
        if (withFeeder) {
            Element feeders = PresetXml.child(PresetXml.machine(machine), "feeders");
            Element feeder = machine.createElement("feeder");
            feeder.setAttribute("class", "org.openpnp.machine.reference.feeder.ReferenceTubeFeeder");
            feeder.setAttribute("id", "FDR-TEST");
            feeder.setAttribute("name", "F-01");
            feeder.setAttribute("enabled", "true");
            feeders.appendChild(feeder);
        }
        PresetXml.write(machine, new File(folder, "machine.xml"));
        try (InputStream in = lumen().openVision()) {
            Document vision = PresetXml.parse(in);
            Element mine = vision.createElement("vision-settings");
            mine.setAttribute("class", "org.openpnp.model.BottomVisionSettings");
            mine.setAttribute("id", "BVS_MINE");
            mine.setAttribute("name", "Mine");
            vision.getDocumentElement().appendChild(mine);
            PresetXml.write(vision, new File(folder, "vision-settings.xml"));
        }
        return folder;
    }

    @Test
    public void theLumenPnPPresetIsWhatTheMachineIs() throws Exception {
        MachinePreset preset = lumen();
        assertTrue(preset.isBuiltIn());
        assertEquals("Opulo", preset.getVendor());
        assertTrue(preset.hasVision());
        PresetXml.Summary summary = MachinePresets.summary(preset);
        assertEquals(NozzleSolution.DualNegated, summary.nozzles.solution);
        assertEquals(2, summary.nozzles.nozzles);
        assertEquals(List.of("N045", "N08", "N14", "N24", "N40", "N75"), summary.tips);
        assertEquals(433, summary.travelX, 1e-9);
        assertEquals(487, summary.travelY, 1e-9);
        assertEquals("Marlin bugfix-2.1.x", summary.firmware);
        assertEquals(115200, summary.baud);
        assertNull(summary.port, "the vendor's ttyACM0 is not the preset's");
        assertEquals(1, summary.camerasDown);
        assertEquals(1, summary.camerasUp);
        assertEquals(0, summary.feeders);
    }

    @Test
    public void aPresetLeavesOutThePortTheCamerasAndTheFeeders() throws Exception {
        File configuration = tempDir.resolve("config").toFile();
        File source = lumenConfiguration("source", true);
        MachinePreset preset = MachinePresets.create(configuration, source, source.getPath(), "Shop 2",
                "a LumenPnP", new MachinePresets.Options());
        assertEquals("Shop 2", preset.getName());
        assertFalse(preset.isBuiltIn());
        assertTrue(preset.getDirectory().getParentFile().getName().equals(MachinePresets.DIRECTORY));
        Document machine = PresetXml.parse(new File(preset.getDirectory(), "machine.xml"));
        for (Element serial : PresetXml.descendants(PresetXml.machine(machine), "serial")) {
            assertEquals("", serial.getAttribute("port-name"));
        }
        for (Element camera : PresetXml.descendants(PresetXml.machine(machine), "camera")) {
            assertEquals("", camera.getAttribute("unique-id"));
        }
        assertEquals(0, PresetXml.summary(machine).feeders);
        assertEquals(List.of(preset.getName()),
                MachinePresets.user(configuration).stream().map(MachinePreset::getName).collect(java.util.stream.Collectors.toList()));
    }

    @Test
    public void aPresetCanTakeTheFeedersAlong() throws Exception {
        File configuration = tempDir.resolve("config").toFile();
        MachinePresets.Options options = new MachinePresets.Options();
        options.feeders = true;
        MachinePreset preset = MachinePresets.create(configuration, lumenConfiguration("source", true), "current",
                "With feeders", "", options);
        assertEquals(1, MachinePresets.summary(preset).feeders);
    }

    @Test
    public void renamingAndDeletingAreThePresetsFolder() throws Exception {
        File configuration = tempDir.resolve("config").toFile();
        MachinePreset preset = MachinePresets.create(configuration, lumenConfiguration("source", false), "current",
                "Old name", "", new MachinePresets.Options());
        MachinePreset renamed = MachinePresets.rename(preset, "New name");
        assertEquals("New name", renamed.getName());
        assertEquals("New name", MachinePresets.user(configuration).get(0).getName());
        MachinePresets.delete(renamed);
        assertFalse(renamed.getDirectory().exists());
        assertTrue(MachinePresets.user(configuration).isEmpty());
    }

    @Test
    public void applyingKeepsTheFeedersThePortTheCamerasAndTheVisionSettingsOfTheConfiguration() throws Exception {
        File configuration = lumenConfiguration("config", true);
        MachinePresets.apply(lumen(), configuration);

        Document machine = PresetXml.parse(new File(configuration, "machine.xml"));
        Element root = PresetXml.machine(machine);
        assertEquals("LumenPnP v4.1", root.getAttribute("preset-name"));
        assertEquals("true", root.getAttribute("preset-built-in"));
        PresetXml.Summary summary = PresetXml.summary(machine);
        assertEquals("COM5", summary.port);
        assertEquals(1, summary.feeders);
        boolean top = false;
        for (Element camera : PresetXml.descendants(root, "camera")) {
            if (camera.getAttribute("name").equals("Top")) {
                assertEquals("top-camera-on-this-computer", camera.getAttribute("unique-id"));
                assertEquals("3", camera.getAttribute("format-id"));
                top = true;
            }
        }
        assertTrue(top);
        Document vision = PresetXml.parse(new File(configuration, "vision-settings.xml"));
        boolean mine = false;
        int stock = 0;
        for (Element settings : PresetXml.children(vision.getDocumentElement(), "vision-settings")) {
            mine |= settings.getAttribute("id").equals("BVS_MINE");
            stock += settings.getAttribute("id").equals("BVS_Stock") ? 1 : 0;
        }
        assertTrue(mine, "the user's own vision settings are kept");
        assertEquals(1, stock, "the preset's replace those of the same id");
    }

    @Test
    public void theAppliedConfigurationLoadsAsTheMachineThePresetIs() throws Exception {
        File configuration = tempDir.resolve("fresh").toFile();
        Configuration.initialize(configuration);
        Configuration.get().load();
        Configuration.get().save();
        MachinePresets.apply(lumen(), configuration);

        Configuration.initialize(configuration);
        Configuration.get().load();
        ReferenceMachine machine = (ReferenceMachine) Configuration.get().getMachine();
        assertEquals("LumenPnP v4.1", machine.getPresetName());
        assertTrue(machine.isPresetBuiltIn());
        assertEquals(2, machine.getDefaultHead().getNozzles().size());
        assertEquals(6, machine.getNozzleTips().size());
    }

    @Test
    public void theFirmwareIsNamedWithoutWhatFollowsIt() {
        assertEquals("Marlin bugfix-2.1.x", PresetXml.firmwareName(
                "FIRMWARE_NAME:Marlin bugfix-2.1.x (Aug  3 2022 13:21:25) SOURCE_CODE_URL:github.com/MarlinFirmware/Marlin PROTOCOL_VERSION:1.0"));
        assertEquals("RepRapFirmware", PresetXml.firmwareName("FIRMWARE_NAME: RepRapFirmware FIRMWARE_VERSION: 3.4"));
        assertNull(PresetXml.firmwareName("ok"));
    }

    @Test
    public void startingAgainRunsTheSameProgramFromTheSameClassPath() {
        List<String> command = Relaunch.command();
        assertTrue(command.contains("-cp"));
        assertEquals("org.openpnp.Main", command.get(command.size() - 1));
        assertNotNull(command.get(0));
        assertTrue(Files.exists(Path.of(command.get(0))), command.get(0));
    }
}
