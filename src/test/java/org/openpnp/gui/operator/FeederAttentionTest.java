package org.openpnp.gui.operator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.gui.tablemodel.FeedersTableModel;
import org.openpnp.machine.reference.feeder.ReferenceTubeFeeder;
import org.openpnp.model.Abstract2DLocatable.Side;
import org.openpnp.model.Board;
import org.openpnp.model.BoardLocation;
import org.openpnp.model.Configuration;
import org.openpnp.model.Job;
import org.openpnp.model.Part;
import org.openpnp.model.Placement;

/**
 * One judgement of which feeders want a look, for the feeders page's badge and for production
 * mode: the feeders page counted every feeder that was not ready, those switched off or without a
 * part among them, and showed a feeder the job had switched off for running out as switched off.
 */
public class FeederAttentionTest {
    @TempDir
    Path tempDir;

    private Part resistor;
    private Part capacitor;
    private Part diode;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
        resistor = new Part("R0603-10K");
        capacitor = new Part("C0402-100N");
        diode = new Part("SOD-123");
        Configuration.get().addPart(resistor);
        Configuration.get().addPart(capacitor);
        Configuration.get().addPart(diode);
    }

    private static Placement placement(String id, Part part, Side side) {
        Placement placement = new Placement(id);
        placement.setPart(part);
        placement.setSide(side);
        return placement;
    }

    private Job job() {
        Board board = new Board();
        board.setName("cell");
        board.addPlacement(placement("R1", resistor, Side.Top));
        board.addPlacement(placement("C1", capacitor, Side.Bottom));
        Job job = new Job();
        BoardLocation location = new BoardLocation(new Board(board));
        location.setSide(Side.Top);
        job.addBoardOrPanelLocation(location);
        return job;
    }

    private static ReferenceTubeFeeder feeder(Part part, int stock, int picked) {
        ReferenceTubeFeeder feeder = new ReferenceTubeFeeder();
        feeder.setPart(part);
        feeder.refill(stock);
        for (int i = 0; i < picked; i++) {
            feeder.recordPick();
        }
        return feeder;
    }

    @Test
    public void theJobsPartsAreThoseOnTheSideEachBoardFaces() {
        Set<String> parts = FeederAttention.partsOf(job());
        assertEquals(Set.of("R0603-10K"), parts, "the capacitor is on the bottom of a board facing up");
        assertTrue(FeederAttention.partsOf(null).isEmpty());
    }

    @Test
    public void onlyTheJobsFeedersInTroubleAreCounted() {
        Set<String> parts = FeederAttention.partsOf(job());
        ReferenceTubeFeeder out = feeder(resistor, 2, 2);
        assertTrue(FeederAttention.wantsLook(out, parts));
        assertEquals(FeederAttention.Trouble.Empty, FeederAttention.troubleOf(out));

        ReferenceTubeFeeder full = feeder(resistor, 20, 0);
        assertFalse(FeederAttention.wantsLook(full, parts));

        ReferenceTubeFeeder unused = feeder(diode, 2, 2);
        assertFalse(FeederAttention.wantsLook(unused, parts), "empty, but the job does not place its part");

        ReferenceTubeFeeder bare = new ReferenceTubeFeeder();
        bare.setEnabled(false);
        assertFalse(FeederAttention.wantsLook(bare, parts), "switched off without a part: not the job's");
    }

    @Test
    public void aFeederSwitchedOffForRunningOutReadsAsEmpty() {
        ReferenceTubeFeeder out = feeder(resistor, 2, 2);
        out.setEnabled(false);
        assertEquals(FeedersTableModel.Status.Empty, FeedersTableModel.statusOf(out));
        ReferenceTubeFeeder off = feeder(resistor, 20, 0);
        off.setEnabled(false);
        assertEquals(FeedersTableModel.Status.Disabled, FeedersTableModel.statusOf(off));
    }
}
