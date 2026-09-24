package org.openpnp.gui.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.openpnp.Translations;

/** What changed between two copies of machine.xml, setting by setting, and what it belongs to. */
public class SettingsDiffTest {
    private static String machine(String body) {
        return "<openpnp-machine><machine class=\"ReferenceMachine\">" + body + "</machine></openpnp-machine>";
    }

    private static String axis(String method, String offset) {
        return "<axes><axis class=\"ReferenceControllerAxis\" id=\"AXS1\" name=\"x\" backlash-compensation-method=\""
                + method + "\"><backlash-offset value=\"" + offset + "\" units=\"Millimeters\"/></axis></axes>";
    }

    @Test
    public void aLengthIsOneSettingOfTheElementItBelongsTo() throws Exception {
        List<SettingsDiff.Difference> differences = SettingsDiff.between(machine(axis("None", "0.0")),
                machine(axis("None", "0.012")));
        assertEquals(1, differences.size());
        SettingsDiff.Difference d = differences.get(0);
        assertEquals("x", d.getSubject());
        assertEquals(Translations.getString("SettingsDiff.backlash-offset"), d.getSetting());
        assertEquals("0 mm", d.getBefore());
        assertEquals("0.012 mm", d.getAfter());
    }

    @Test
    public void anAttributeOfTheElementIsASettingOfIt() throws Exception {
        List<SettingsDiff.Difference> differences = SettingsDiff.between(machine(axis("None", "0.0")),
                machine(axis("OneSidedPositioning", "0.0")));
        assertEquals(1, differences.size());
        assertEquals(Translations.getString("SettingsDiff.backlash-compensation-method"),
                differences.get(0).getSetting());
        assertEquals("None", differences.get(0).getBefore());
        assertEquals("OneSidedPositioning", differences.get(0).getAfter());
    }

    @Test
    public void aPipelineStageIsPartOfTheNozzleTipItIsIn() throws Exception {
        String before = machine("<nozzle-tips><nozzle-tip id=\"NT1\" name=\"N045\"><calibration><pipeline>"
                + "<stages><cv-stage name=\"cir\" super-sampling=\"1\"/></stages></pipeline></calibration>"
                + "</nozzle-tip></nozzle-tips>");
        String after = before.replace("super-sampling=\"1\"", "super-sampling=\"8\"");
        List<SettingsDiff.Difference> differences = SettingsDiff.between(before, after);
        assertEquals(1, differences.size());
        assertEquals("N045", differences.get(0).getSubject());
        assertEquals(Translations.getString("SettingsDiff.super-sampling"), differences.get(0).getSetting());
        assertEquals("1", differences.get(0).getBefore());
        assertEquals("8", differences.get(0).getAfter());
    }

    @Test
    public void measurementsAndTheIssuesRecordAreNotSettings() throws Exception {
        String before = machine(axis("None", "0.0") + "<machine-diagnostics><last-results when=\"1\"/>"
                + "</machine-diagnostics><solutions><solved-solutions><string>a</string></solved-solutions></solutions>");
        String after = machine(axis("None", "0.0") + "<machine-diagnostics><last-results when=\"2\"/>"
                + "</machine-diagnostics><solutions><solved-solutions><string>b</string></solved-solutions></solutions>");
        assertTrue(SettingsDiff.between(before, after).isEmpty());
    }

    @Test
    public void anElementThatWasNotThereIsNew() throws Exception {
        String before = machine("<cameras><camera id=\"C1\" name=\"Top\"/></cameras>");
        String after = machine("<cameras><camera id=\"C1\" name=\"Top\"><head-offsets units=\"Millimeters\" "
                + "x=\"1.0\" y=\"2.0\"/></camera></cameras>");
        List<SettingsDiff.Difference> differences = SettingsDiff.between(before, after);
        assertEquals(1, differences.size());
        assertEquals("Top", differences.get(0).getSubject());
        assertEquals(Translations.getString("SettingsDiff.head-offsets"), differences.get(0).getSetting());
        assertEquals(Translations.getString("SettingsDiff.Added"), differences.get(0).getAfter());
    }

    @Test
    public void theCoordinatesOfALocationAreSettingsOfTheirOwn() throws Exception {
        String before = machine("<cameras><camera id=\"C1\" name=\"Top\"><head-offsets units=\"Millimeters\" "
                + "x=\"1.0\" y=\"2.0\"/></camera></cameras>");
        String after = before.replace("y=\"2.0\"", "y=\"2.5\"");
        List<SettingsDiff.Difference> differences = SettingsDiff.between(before, after);
        assertEquals(1, differences.size());
        assertEquals(Translations.getString("SettingsDiff.head-offsets") + " Y", differences.get(0).getSetting());
        assertEquals("2 mm", differences.get(0).getBefore());
        assertEquals("2.5 mm", differences.get(0).getAfter());
    }
}
