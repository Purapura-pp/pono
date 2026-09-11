import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis.BacklashCompensationMethod;
import org.openpnp.machine.reference.driver.ReferenceAdvancedMotionPlanner;
import org.openpnp.model.AxesLocation;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Motion;
import org.openpnp.model.Motion.MotionOption;
import org.openpnp.model.MotionProfile;
import org.openpnp.spi.Axis;
import org.openpnp.spi.HeadMountable;

/**
 * Covers fusing the final approach of a two-part backlash compensation into the move that follows
 * it, as the advanced motion planner does when the option is on.
 * <p>
 * A plan that has been fused wrongly is not a wrong number on a screen, it is a part placed
 * somewhere other than where the job says. So the negative cases here matter more than the
 * positive one: each of them is a condition under which fusing would change where the machine
 * ends up, or which side it arrives from.
 */
public class BacklashFusionTest {
    /** Opens the planner's own fusing, path solving and recorded moves to the test. */
    public static class TestPlanner extends ReferenceAdvancedMotionPlanner {
        private final List<Motion> recorded = new ArrayList<>();

        public void fuse(List<Motion> plan) {
            fuseBacklashFinalApproaches(plan);
        }

        public void solve(List<Motion> plan) throws Exception {
            new PlannerPath(plan).solve();
        }

        /** The moves the backlash compensation produces for one commanded move. */
        public List<Motion> compensate(HeadMountable hm, AxesLocation from, AxesLocation to) {
            recorded.clear();
            createBacklashCompensatedMotion(hm, 1.0, from, to);
            return recorded;
        }

        @Override
        protected Motion addMotion(HeadMountable hm, double speed, AxesLocation location0,
                AxesLocation location1, int options) {
            Motion motion = super.addMotion(hm, speed, location0, location1, options);
            recorded.add(motion);
            return motion;
        }
    }

    @TempDir
    Path tempDir;

    private ReferenceMachine machine;
    private TestPlanner planner;
    private HeadMountable nozzle;
    private ReferenceControllerAxis xAxis;
    private ReferenceControllerAxis yAxis;
    private ReferenceControllerAxis zAxis;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
        machine = (ReferenceMachine) Configuration.get().getMachine();
        planner = new TestPlanner();
        planner.setMachine(machine);
        planner.setAllowContinuousMotion(true);
        planner.setFuseBacklashFinalApproach(true);
        nozzle = machine.getDefaultHead().getDefaultNozzle();
        xAxis = addAxis("X", Axis.Type.X);
        yAxis = addAxis("Y", Axis.Type.Y);
        zAxis = addAxis("Z", Axis.Type.Z);
    }

    /**
     * The case this exists for: [move over the target, compensating approach, Z descent] becomes
     * [move over the target, one coordinated line down to the part].
     */
    @Test
    public void theApproachAndTheDescentBecomeOneMove() {
        List<Motion> plan = new ArrayList<>();
        plan.add(motion(at(0, 0, 20), at(10.1, 20, 20), 1.0));
        Motion approach = motion(at(10.1, 20, 20), at(10, 20, 20), 0.25,
                MotionOption.BacklashFinalApproach);
        plan.add(approach);
        Motion descent = motion(at(10, 20, 20), at(10, 20, 0), 1.0);
        plan.add(descent);

        planner.fuse(plan);

        assertEquals(2, plan.size(), "the descent was not fused into the approach");
        Motion fused = plan.get(1);
        assertEquals(10.1, coordinate(fused.getLocation0(), xAxis), 1e-9,
                "the fused move has to start where the compensating one did");
        assertEquals(0, coordinate(fused.getLocation1(), zAxis), 1e-9,
                "the fused move has to end where the descent did");
        assertEquals(10, coordinate(fused.getLocation1(), xAxis), 1e-9,
                "the target coordinate is unchanged, so the approach side is too");
        assertEquals(descent.getNominalSpeed(), fused.getNominalSpeed(),
                "the descent's speed governs the fused move");
        assertFalse(fused.hasOption(MotionOption.BacklashFinalApproach),
                "a fused move must not be fused again");
    }

    /** The compensation exists to arrive from one side; a move that takes X off it cannot join. */
    @Test
    public void aFollowingMoveThatTouchesACompensatedAxisIsNotFused() {
        List<Motion> plan = new ArrayList<>();
        plan.add(motion(at(10.1, 20, 20), at(10, 20, 20), 0.25,
                MotionOption.BacklashFinalApproach));
        // The descent also carries X onwards, which would undo the approach it was fused with.
        plan.add(motion(at(10, 20, 20), at(10.5, 20, 0), 1.0));

        planner.fuse(plan);

        assertEquals(2, plan.size(), "X moved in both segments, so they must stay separate");
    }

    /** With two axes compensated, either one of them moving again is enough to refuse. */
    @Test
    public void aFollowingMoveThatTouchesTheSecondCompensatedAxisIsNotFused() {
        List<Motion> plan = new ArrayList<>();
        plan.add(motion(at(10.1, 20.1, 20), at(10, 20, 20), 0.25,
                MotionOption.BacklashFinalApproach));
        plan.add(motion(at(10, 20, 20), at(10, 20.5, 0), 1.0));

        planner.fuse(plan);

        assertEquals(2, plan.size(), "Y was compensated and then moved again");
    }

    /** A jog has no end yet, so there is nothing to fuse into. */
    @Test
    public void aJogIsNotFused() {
        List<Motion> plan = new ArrayList<>();
        plan.add(motion(at(10.1, 20, 20), at(10, 20, 20), 0.25,
                MotionOption.BacklashFinalApproach));
        plan.add(motion(at(10, 20, 20), at(10, 20, 0), 1.0, MotionOption.JogMotion));

        planner.fuse(plan);

        assertEquals(2, plan.size());
    }

    /**
     * The fused move runs at the descent's speed, and the compensated axis covers its share of the
     * distance in that time. Too short a descent and that share is faster than the slow approach
     * the user asked for, which is the one thing the compensation is precise about.
     */
    @Test
    public void aDescentTooShortToKeepTheApproachSlowIsNotFused() {
        xAxis.setBacklashSpeedFactor(0.25);
        List<Motion> plan = new ArrayList<>();
        plan.add(motion(at(10.1, 20, 20), at(10, 20, 20), 0.25,
                MotionOption.BacklashFinalApproach));
        // 0.1 mm of X against 0.2 mm of Z: X would cover nearly half the distance.
        plan.add(motion(at(10, 20, 20), at(10, 20, 19.8), 1.0));

        planner.fuse(plan);

        assertEquals(2, plan.size(), "the approach would have run at more than a quarter speed");
    }

    @Test
    public void aLongerDescentKeepsTheApproachSlowEnoughToFuse() {
        xAxis.setBacklashSpeedFactor(0.25);
        List<Motion> plan = new ArrayList<>();
        plan.add(motion(at(10.1, 20, 20), at(10, 20, 20), 0.25,
                MotionOption.BacklashFinalApproach));
        plan.add(motion(at(10, 20, 20), at(10, 20, 19), 1.0));

        planner.fuse(plan);

        assertEquals(1, plan.size(), "0.1 mm of X over 1 mm of Z is a tenth of the speed");
    }

    /** Without the marker there is nothing to recognise, whatever the geometry looks like. */
    @Test
    public void anUnmarkedMoveIsNeverFused() {
        List<Motion> plan = new ArrayList<>();
        plan.add(motion(at(10.1, 20, 20), at(10, 20, 20), 0.25));
        plan.add(motion(at(10, 20, 20), at(10, 20, 0), 1.0));

        planner.fuse(plan);

        assertEquals(2, plan.size());
    }

    /** Off by default, because the fused move carries the lateral travel through the descent. */
    @Test
    public void nothingIsFusedUnlessTheOptionIsOn() {
        ReferenceAdvancedMotionPlanner fresh = new ReferenceAdvancedMotionPlanner();
        assertFalse(fresh.isFuseBacklashFinalApproach(),
                "a machine that is upgraded must not change how it moves");
    }

    /** The fused path still has to be solvable, which is what the planner does with it next. */
    @Test
    public void theFusedPathSolvesWithoutAnInvalidProfile() throws Exception {
        List<Motion> plan = new ArrayList<>();
        plan.add(motion(at(0, 0, 20), at(10.1, 20, 20), 1.0));
        plan.add(motion(at(10.1, 20, 20), at(10, 20, 20), 0.25,
                MotionOption.BacklashFinalApproach));
        plan.add(motion(at(10, 20, 20), at(10, 20, 0), 1.0));

        planner.fuse(plan);
        planner.solve(plan);

        assertEquals(2, plan.size());
        for (Motion motion : plan) {
            for (MotionProfile profile : motion.getAxesProfiles()) {
                assertNull(profile.checkValidity(), "a solved profile is invalid: " + profile);
            }
        }
    }

    /**
     * The marker is what makes any of this possible, and it is set in the base planner, so a
     * machine with no advanced planner still produces it - harmlessly, since nothing reads it.
     */
    @Test
    public void theCompensatingSegmentIsTheOneThatCarriesTheMarker() {
        xAxis.setBacklashCompensationMethod(BacklashCompensationMethod.OneSidedPositioning);
        xAxis.setBacklashOffset(new Length(0.1, LengthUnit.Millimeters));

        List<Motion> moves = planner.compensate(nozzle, at(0, 20, 20), at(10, 20, 20));

        assertEquals(2, moves.size(), "one-sided positioning drives past the target and back");
        assertFalse(moves.get(0).hasOption(MotionOption.BacklashFinalApproach),
                "the move that drives past the target is not the approach");
        Motion approach = moves.get(1);
        assertTrue(approach.hasOption(MotionOption.BacklashFinalApproach),
                "the second of the two compensating moves is the one to mark");
        assertEquals(10.1, coordinate(approach.getLocation0(), xAxis), 1e-9,
                "it starts past the target, by the backlash offset");
        assertSame(nozzle, approach.getHeadMountable());
        assertNotNull(approach.getLocation1());
    }

    private ReferenceControllerAxis addAxis(String letter, Axis.Type type) throws Exception {
        ReferenceControllerAxis axis = new ReferenceControllerAxis();
        axis.setName(letter);
        axis.setType(type);
        axis.setLetter(letter);
        axis.setDriver(machine.getDrivers().get(0));
        machine.addAxis(axis);
        return axis;
    }

    private AxesLocation at(double x, double y, double z) {
        return new AxesLocation(xAxis, new Length(x, LengthUnit.Millimeters))
                .put(new AxesLocation(yAxis, new Length(y, LengthUnit.Millimeters)))
                .put(new AxesLocation(zAxis, new Length(z, LengthUnit.Millimeters)));
    }

    private Motion motion(AxesLocation from, AxesLocation to, double speed,
            MotionOption... options) {
        return new Motion(nozzle, from, to, speed, options);
    }

    private static double coordinate(AxesLocation location, ReferenceControllerAxis axis) {
        return location.getLengthCoordinate(axis).convertToUnits(LengthUnit.Millimeters).getValue();
    }
}
