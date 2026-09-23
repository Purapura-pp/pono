package org.openpnp.gui.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.Translations;
import org.openpnp.gui.tablemodel.FeedersTableModel;
import org.openpnp.gui.tablemodel.FeedersTableModel.Status;
import org.openpnp.machine.reference.feeder.ReferenceStripFeeder;
import org.openpnp.machine.reference.feeder.ReferenceTrayFeeder;
import org.openpnp.machine.reference.feeder.ReferenceTubeFeeder;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Part;

/**
 * The feeders table's words: the tape as "8 / 4 mm", a tray's grid, what is left, how long ago
 * the last pick was, and which status a feeder has when several would apply.
 */
public class FeederDescriptionsTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
    }

    private static ReferenceStripFeeder strip() {
        ReferenceStripFeeder feeder = new ReferenceStripFeeder();
        feeder.setTapeWidth(new Length(8, LengthUnit.Millimeters));
        feeder.setPartPitch(new Length(4, LengthUnit.Millimeters));
        return feeder;
    }

    @Test
    public void theTapeIsItsWidthAndItsPitch() {
        assertEquals("8 / 4 mm", FeederDescriptions.tape(strip()));
        ReferenceTrayFeeder tray = new ReferenceTrayFeeder();
        tray.setTrayCountX(5);
        tray.setTrayCountY(8);
        assertEquals(String.format(Translations.getString("FeederDescriptions.Grid"), 5, 8),
                FeederDescriptions.tape(tray));
        assertEquals("\u2014", FeederDescriptions.tape(new ReferenceTubeFeeder()), "no tape, a dash");
    }

    @Test
    public void theSummarySaysTheTapeAndWhatIsLeft() {
        ReferenceStripFeeder feeder = strip();
        feeder.setMaxFeedCount(1240);
        String summary = FeederDescriptions.summary(feeder);
        assertTrue(summary.startsWith(String.format(Translations.getString("FeederDescriptions.Tape"), "8")), summary);
        assertTrue(summary.endsWith(String.format(Translations.getString("FeederDescriptions.Left"), "1,240")), summary);
    }

    @Test
    public void theLastPickIsSaidAsHowLongAgo() {
        long now = LocalDate.of(2026, 9, 23).atTime(LocalTime.of(15, 0)).atZone(ZoneId.systemDefault())
                .toInstant().toEpochMilli();
        assertEquals("\u2014", FeederDescriptions.since(0, now));
        assertEquals(Translations.getString("FeederDescriptions.JustNow"),
                FeederDescriptions.since(now - 20_000, now));
        assertEquals(String.format(Translations.getString("FeederDescriptions.MinutesAgo"), 3),
                FeederDescriptions.since(now - 3 * 60_000, now));
        assertEquals(String.format(Translations.getString("FeederDescriptions.HoursAgo"), 2),
                FeederDescriptions.since(now - 2 * 3_600_000, now));
        assertEquals(Translations.getString("FeederDescriptions.Yesterday"),
                FeederDescriptions.since(now - 20 * 3_600_000L, now));
    }

    @Test
    public void theStatusIsTheWorstThatApplies() {
        ReferenceTubeFeeder feeder = new ReferenceTubeFeeder();
        feeder.setEnabled(false);
        assertEquals(Status.Disabled, FeedersTableModel.statusOf(feeder));
        feeder.setEnabled(true);
        feeder.setPart(null);
        assertEquals(Status.NoPart, FeedersTableModel.statusOf(feeder));
        feeder.setPart(new Part("R0603-10K"));
        assertEquals(Status.Ready, FeedersTableModel.statusOf(feeder), "not told what it holds: nothing to warn of");
        feeder.refill(10);
        feeder.setLowCount(10);
        assertEquals(Status.Low, FeedersTableModel.statusOf(feeder));
        for (int i = 0; i < 10; i++) {
            feeder.recordPick();
        }
        assertEquals(Status.Empty, FeedersTableModel.statusOf(feeder));
    }
}
