package org.openpnp.machine.reference.feeder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.model.Configuration;
import org.simpleframework.xml.Serializer;

/**
 * What a feeder has left: counted from what was loaded for a feeder that cannot count its own
 * parts, from the feeder's geometry for a strip with a length and for a tray, and the pick
 * that counts one and stamps the time.
 */
public class FeederStockTest {
    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
    }

    @Test
    public void aFeederThatCannotCountItsPartsCountsFromWhatWasLoaded() {
        ReferenceTubeFeeder feeder = new ReferenceTubeFeeder();
        assertNull(feeder.getPartsLeft(), "not told what it holds, it does not know");
        assertEquals(0, feeder.getLastPickMillis());

        feeder.refill(100);
        assertEquals(100, feeder.getPartsLeft());
        long before = System.currentTimeMillis();
        feeder.recordPick();
        feeder.recordPick();
        feeder.recordPick();
        assertEquals(97, feeder.getPartsLeft());
        assertTrue(feeder.getLastPickMillis() >= before);

        feeder.setLowCount(97);
        assertTrue(feeder.isLow(), "at the count it asks for more");
        assertFalse(feeder.isEmpty());
        for (int i = 0; i < 97; i++) {
            feeder.recordPick();
        }
        assertEquals(0, feeder.getPartsLeft());
        assertTrue(feeder.isEmpty());
        assertFalse(feeder.isLow(), "empty is not low: it is worse");

        feeder.refill(null);
        assertEquals(100, feeder.getPartsLeft(), "refilled with as many as last time");
    }

    @Test
    public void aStripWithALengthCountsFromItsFeedCount() {
        ReferenceStripFeeder feeder = new ReferenceStripFeeder();
        assertNull(feeder.getPartsLeft(), "no maximum feed count, no loaded count: not known");
        assertFalse(feeder.isCountedFromGeometry());

        List<String> fired = new ArrayList<>();
        feeder.addPropertyChangeListener("partsLeft", e -> fired.add(e.getOldValue() + ">" + e.getNewValue())); //$NON-NLS-1$
        feeder.setMaxFeedCount(50);
        assertTrue(feeder.isCountedFromGeometry());
        feeder.setFeedCount(48);
        assertEquals(2, feeder.getPartsLeft());
        assertEquals(List.of("null>50", "50>2"), fired, "the table hears of every change");

        feeder.setLowCount(5);
        assertTrue(feeder.isLow());
        feeder.recordPick();
        assertEquals(2, feeder.getPartsLeft(), "a strip counts its feeds, not its picks");

        feeder.refill(null);
        assertEquals(0, feeder.getFeedCount());
        assertEquals(50, feeder.getPartsLeft());
    }

    @Test
    public void aTrayCountsItsPockets() {
        ReferenceTrayFeeder feeder = new ReferenceTrayFeeder();
        feeder.setTrayCountX(3);
        feeder.setTrayCountY(4);
        assertEquals(12, feeder.getPartsLeft());
        feeder.setFeedCount(12);
        assertTrue(feeder.isEmpty());
        feeder.refill(null);
        assertEquals(12, feeder.getPartsLeft());
    }

    @Test
    public void theCountsAndTheLastPickAreSavedWithTheFeeder() throws Exception {
        ReferenceTubeFeeder feeder = new ReferenceTubeFeeder();
        feeder.setPart(null);
        feeder.refill(40);
        feeder.recordPick();
        feeder.setLowCount(5);
        long picked = feeder.getLastPickMillis();

        Serializer serializer = Configuration.createSerializer();
        StringWriter xml = new StringWriter();
        serializer.write(feeder, xml);
        ReferenceTubeFeeder read = serializer.read(ReferenceTubeFeeder.class, new StringReader(xml.toString()));

        assertEquals(39, read.getPartsLeft());
        assertEquals(5, read.getLowCount());
        assertEquals(picked, read.getLastPickMillis());
    }
}
