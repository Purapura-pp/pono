package org.openpnp.gui.tablemodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.openpnp.Translations;
import org.openpnp.gui.tablemodel.PlacementsHolderPlacementsTableModel.PlacementStatus;
import org.openpnp.gui.tablemodel.PlacementsHolderPlacementsTableModel.Status;
import org.openpnp.model.JobRun;
import org.openpnp.model.Placement;

/** The status column: the run's state where it has one, the readiness otherwise. */
public class PlacementStatusTest {
    private static JobRun.PlacementRun run(java.util.function.Consumer<JobRun> steps) {
        JobRun run = new JobRun();
        run.start();
        steps.accept(run);
        return run.get("k");
    }

    @Test
    public void theRunsStateIsWhatTheColumnSays() {
        PlacementStatus placing = new PlacementStatus(Placement.Type.Placement, Status.Ready,
                run(r -> r.placing("k", "R12", "N1")), false);
        assertEquals(String.format(Translations.getString("JobPlacementsPanel.Status.Placing"), "N1"),
                placing.getText());
        PlacementStatus waiting = new PlacementStatus(Placement.Type.Placement, Status.Ready,
                run(r -> r.waitingForFeeder("k", "U3", "F-08")), false);
        assertEquals(String.format(Translations.getString("JobPlacementsPanel.Status.WaitingForFeeder"), "F-08"),
                waiting.getText());
        PlacementStatus failed = new PlacementStatus(Placement.Type.Placement, Status.Ready, run(r -> {
            r.pickFailed("k", "D2", "F-01");
            r.pickFailed("k", "D2", "F-01");
            r.pickFailed("k", "D2", "F-01");
            r.failed("k", "D2", "No part");
        }), false);
        assertEquals(String.format(Translations.getString("JobPlacementsPanel.Status.PickFailed"), 3),
                failed.getText());
        assertEquals("No part", failed.getDetail(), "the whole error, for the tooltip");
    }

    @Test
    public void withoutARunItSaysWhetherItIsPlacedOrReady() {
        assertEquals(Translations.getString("JobPlacementsPanel.Status.Placed"),
                new PlacementStatus(Placement.Type.Placement, Status.Ready, null, true).getText());
        assertEquals(Translations.getString("JobPlacementsPanel.Status.Pending"),
                new PlacementStatus(Placement.Type.Placement, Status.Ready, null, false).getText());
        assertEquals(Translations.getString("JobPlacementsPanel.StatusRenderer.StatusMissingFeeder"),
                new PlacementStatus(Placement.Type.Placement, Status.MissingFeeder, null, false).getText());
        assertEquals("\u2014", new PlacementStatus(Placement.Type.Fiducial, Status.Ready, null, false).getText(),
                "a fiducial is not placed");
    }

    @Test
    public void sortingPutsWhatNeedsAttentionFirst() {
        PlacementStatus error = new PlacementStatus(Placement.Type.Placement, Status.Ready,
                run(r -> r.failed("k", "D2", "x")), false);
        PlacementStatus missing = new PlacementStatus(Placement.Type.Placement, Status.MissingFeeder, null, false);
        PlacementStatus pending = new PlacementStatus(Placement.Type.Placement, Status.Ready, null, false);
        PlacementStatus placed = new PlacementStatus(Placement.Type.Placement, Status.Ready, null, true);
        List<PlacementStatus> sorted = new ArrayList<>(List.of(placed, pending, missing, error));
        Collections.sort(sorted);
        assertEquals(List.of(error, missing, pending, placed), sorted);
        assertTrue(error.toString().equals(error.getText()), "the filter matches what is read");
    }
}
