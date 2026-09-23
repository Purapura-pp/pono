package org.openpnp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** What a run records about its placements, and what it says about the time left. */
public class JobRunTest {
    @Test
    public void aPlacementGoesFromPlacingToPlacedWithHowLongItTook() throws Exception {
        JobRun run = new JobRun();
        List<Object> heard = new ArrayList<>();
        run.addPropertyChangeListener(JobRun.PROPERTY_RUN, e -> heard.add(e.getNewValue()));
        run.start();
        run.placing("B1\u21d2R12", "R12", "N1");
        assertEquals(JobRun.State.Placing, run.get("B1\u21d2R12").getState());
        assertEquals("N1", run.get("B1\u21d2R12").getNozzle());
        Thread.sleep(20);
        run.aligned("B1\u21d2R12", "R12", new Location(LengthUnit.Millimeters, 0.021, -0.008, 0, 0.6));
        run.placed("B1\u21d2R12", "R12");

        JobRun.PlacementRun placed = run.get("B1\u21d2R12");
        assertEquals(JobRun.State.Placed, placed.getState());
        assertTrue(placed.getDurationMillis() >= 20, "from planned to placed");
        assertEquals(0.021, placed.getAlignment().getX(), 1e-9);
        assertSame(placed, run.getLastPlacedRun());
        assertEquals(JobRun.EventKind.Placed, run.getLastPlaced().getKind());
        assertTrue(heard.size() >= 4, "every change is heard");
    }

    @Test
    public void failedPicksAreCountedAndAnErrorKeepsItsMessage() {
        JobRun run = new JobRun();
        run.start();
        run.placing("B1\u21d2D2", "D2", "N2");
        run.pickFailed("B1\u21d2D2", "D2", "F-08");
        run.pickFailed("B1\u21d2D2", "D2", "F-08");
        run.pickFailed("B1\u21d2D2", "D2", "F-08");
        run.failed("B1\u21d2D2", "D2", "No part detected on nozzle N2.");

        JobRun.PlacementRun failed = run.get("B1\u21d2D2");
        assertEquals(JobRun.State.Error, failed.getState());
        assertEquals(3, failed.getPickFailures());
        assertEquals("F-08", failed.getFeeder());
        assertEquals("No part detected on nozzle N2.", failed.getError());
        assertEquals(JobRun.EventKind.Error, run.getEvents().get(run.getEvents().size() - 1).getKind());
    }

    @Test
    public void theTimeLeftIsThePlacementsLeftAtTheRunsPace() throws Exception {
        JobRun run = new JobRun();
        run.start();
        assertTrue(Double.isNaN(run.getRemainingSeconds(10)), "no pace before two placements");
        run.placing("k1", "R1", "N1");
        run.placed("k1", "R1");
        Thread.sleep(50);
        run.placing("k2", "R2", "N1");
        run.placed("k2", "R2");
        double cycle = run.getCycleSeconds();
        assertTrue(cycle >= 0.04 && cycle < 1, "about the 50 ms between them: " + cycle);
        assertEquals(cycle * 10, run.getRemainingSeconds(10), 1e-9);
    }

    @Test
    public void aNewRunForgetsTheLastOne() {
        JobRun run = new JobRun();
        run.start();
        run.skipped("k", "R9");
        run.start();
        assertNull(run.get("k"));
        assertNull(run.getLastPlacedRun());
        assertEquals(1, run.getEvents().size(), "only its own start");
    }
}
