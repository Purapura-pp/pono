package org.openpnp.gui.operator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.machine.reference.feeder.ReferenceTubeFeeder;
import org.openpnp.model.Abstract2DLocatable.Side;
import org.openpnp.model.Board;
import org.openpnp.model.BoardLocation;
import org.openpnp.model.Configuration;
import org.openpnp.model.Job;
import org.openpnp.model.JobRun;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Part;
import org.openpnp.model.Placement;
import org.openpnp.spi.Machine;

/**
 * Production mode's figures, P10: worked out from the job, its run and the machine, as the
 * mockup's column shows them.
 */
public class OperatorSummaryTest {
    @TempDir
    Path tempDir;

    private Machine machine;
    private Part resistor;
    private Part capacitor;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
        machine = Configuration.get().getMachine();
        resistor = new Part("R0603-10K");
        capacitor = new Part("C0402-100N");
        Configuration.get().addPart(resistor);
        Configuration.get().addPart(capacitor);
    }

    private Job job(int boards) {
        Board board = new Board();
        board.setName("cell");
        board.addPlacement(placement("R1", resistor));
        board.addPlacement(placement("C1", capacitor));
        Job job = new Job();
        for (int i = 0; i < boards; i++) {
            BoardLocation location = new BoardLocation(new Board(board));
            location.setSide(Side.Top);
            job.addBoardOrPanelLocation(location);
        }
        return job;
    }

    private static Placement placement(String id, Part part) {
        Placement placement = new Placement(id);
        placement.setPart(part);
        placement.setSide(Side.Top);
        return placement;
    }

    @Test
    public void theProgressTheBoardsAndThePlacementBeingPlaced() {
        Job job = job(3);
        List<BoardLocation> boards = job.getBoardLocations();
        job.storePlacedStatus(boards.get(0), "R1", true);
        job.storePlacedStatus(boards.get(0), "C1", true);
        JobRun run = job.getRun();
        run.start();
        String key = JobRun.key(boards.get(1), "R1");
        run.placing(key, "R1", "N1");
        run.feeding(key, "R1", "F-01");
        run.aligned(key, "R1", new Location(LengthUnit.Millimeters, 0.03, 0.04, 0, 0));

        OperatorSummary s = OperatorSummary.of(job, machine, true, System.currentTimeMillis(), 5);
        assertEquals(2, s.placed);
        assertEquals(6, s.total);
        assertEquals(List.of(OperatorSummary.BoardState.Done, OperatorSummary.BoardState.Running,
                OperatorSummary.BoardState.Pending), s.boards);
        assertEquals(2, s.currentBoard);
        assertEquals("R1", s.current.placementId);
        assertEquals("R0603-10K", s.current.partId);
        assertEquals("N1", s.current.nozzle);
        assertEquals("F-01", s.current.feeder);
        assertEquals(0.05, s.current.alignmentMm, 1e-9);

        run.placed(key, "R1");
        s = OperatorSummary.of(job, machine, true, System.currentTimeMillis(), 5);
        assertNull(s.current, "nothing is being placed once it is placed");
        assertEquals(JobRun.EventKind.Placed, s.events.get(0).getKind(), "the latest event first");
        assertEquals(0, s.currentBoard);
    }

    @Test
    public void theEmptyAndLowFeedersOfTheJobsParts() throws Exception {
        Job job = job(1);
        ReferenceTubeFeeder low = new ReferenceTubeFeeder();
        low.setName("F-03");
        low.setPart(capacitor);
        low.refill(20);
        low.setLowCount(15);
        for (int i = 0; i < 8; i++) {
            low.recordPick();
        }
        ReferenceTubeFeeder empty = new ReferenceTubeFeeder();
        empty.setName("F-12");
        empty.setPart(resistor);
        empty.refill(2);
        empty.recordPick();
        empty.recordPick();
        Part other = new Part("SOT-23");
        Configuration.get().addPart(other);
        ReferenceTubeFeeder unrelated = new ReferenceTubeFeeder();
        unrelated.setName("F-20");
        unrelated.setPart(other);
        unrelated.refill(1);
        unrelated.recordPick();
        machine.addFeeder(low);
        machine.addFeeder(empty);
        machine.addFeeder(unrelated);
        JobRun run = job.getRun();
        run.start();
        run.waitingForFeeder(JobRun.key(job.getBoardLocations().get(0), "R1"), "R1", "F-12");

        OperatorSummary s = OperatorSummary.of(job, machine, true, System.currentTimeMillis(), 5);
        assertEquals(2, s.attention.size(), "a feeder of a part the job does not place is not the job's");
        assertTrue(s.attention.get(0).empty, "the empty one first");
        assertEquals("F-12", s.attention.get(0).feeder.getName());
        assertEquals(List.of("R1"), s.attention.get(0).waiting);
        assertEquals("F-03", s.attention.get(1).feeder.getName());
        assertEquals(Integer.valueOf(12), s.attention.get(1).left);
    }

    @Test
    public void theTimeLeftAndRunForAreOnlySaidWhileTheJobRuns() {
        Job job = job(1);
        job.getRun().start();
        OperatorSummary s = OperatorSummary.of(job, machine, false, System.currentTimeMillis() + 5000, 5);
        assertEquals(0, s.elapsedMillis);
        assertTrue(Double.isNaN(s.remainingSeconds));
        s = OperatorSummary.of(job, machine, true, job.getRun().getStartedMillis() + 5000, 5);
        assertEquals(5000, s.elapsedMillis);
    }
}
