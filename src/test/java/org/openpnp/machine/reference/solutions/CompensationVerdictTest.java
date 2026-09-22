package org.openpnp.machine.reference.solutions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.openpnp.machine.reference.solutions.MachineDiagnostics.CompensationVerdict;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults.Datum;

/**
 * Whether a compensation stands: within the line and closer to true than before, on each axis.
 * The numbers are the seventh session's, where a right compensation was undone twice.
 */
public class CompensationVerdictTest {
    private static final double LINE = 0.001;
    private static final double SHEAR_LINE = 0.1;

    /** 2026-09-22 21:40: basis -0.164 % / -0.187 %, read back -0.092 % / +0.018 % - kept. */
    @Test
    public void theSeventhSessionsCompensationStands() {
        CompensationVerdict v = CompensationVerdict.judge(-0.001641, -0.001867, 0.0254,
                -0.00092, 0.00018, 0.046, LINE, LINE, SHEAR_LINE, true);

        assertTrue(v.kept, v.whatFailed());
        assertTrue(v.xWithin && v.xBetter && v.yWithin && v.yBetter);
        // The shear came back within its line, and the basis was within it too.
        assertTrue(v.shearWithin && v.shearBetter);
        assertEquals("", v.whatFailed());
    }

    /** The sixth session's: -0.19 % taken to +0.066 % and +0.053 % - kept as well. */
    @Test
    public void theSixthSessionsWouldHaveStoodToo() {
        assertTrue(CompensationVerdict.judge(-0.00188, -0.00239, 0.03, 0.00066, -0.00059, -0.048,
                LINE, LINE, SHEAR_LINE, false).kept);
        assertTrue(CompensationVerdict.judge(-0.00195, -0.00200, 0.04, 0.00053, -0.00027, -0.030,
                LINE, LINE, SHEAR_LINE, false).kept);
    }

    /** A transform that changed nothing reads the same as before, and is not kept. */
    @Test
    public void aCompensationThatChangedNothingIsNotKept() {
        CompensationVerdict v = CompensationVerdict.judge(-0.00164, -0.00187, 0, -0.00160, -0.00185, 0,
                LINE, LINE, SHEAR_LINE, false);

        assertFalse(v.kept);
        assertFalse(v.xWithin);
        assertFalse(v.yWithin);
        assertTrue(v.xBetter && v.yBetter, "a hair closer, which is not the point");
        assertEquals("X outside the line, Y outside the line", v.whatFailed());
    }

    /** Overshooting past true by more than the line is not kept either, however much it moved. */
    @Test
    public void overshootingIsNotKept() {
        CompensationVerdict v = CompensationVerdict.judge(-0.00164, -0.00187, 0, 0.00150, 0.00005, 0,
                LINE, LINE, SHEAR_LINE, false);

        assertFalse(v.kept);
        assertFalse(v.xWithin);
        assertTrue(v.xBetter, "0.15 is nearer than 0.164, for what that is worth");
        assertTrue(v.yWithin && v.yBetter);
    }

    /** Further from true than a basis that was outside the line: not kept, whatever else. */
    @Test
    public void furtherFromTrueThanTheBasisIsNotKept() {
        CompensationVerdict v = CompensationVerdict.judge(0.00150, -0.00187, 0, -0.00180, 0.00010, 0,
                LINE, LINE, SHEAR_LINE, false);

        assertFalse(v.kept);
        assertFalse(v.xWithin);
        assertFalse(v.xBetter);
        assertEquals("X outside the line, X no closer to true than before", v.whatFailed());
    }

    /** A basis already within the line has nothing to get closer to; within the line is enough. */
    @Test
    public void aBasisWithinTheLineNeedsOnlyToStayThere() {
        CompensationVerdict v = CompensationVerdict.judge(0.00030, 0.00020, 0, 0.00060, -0.00050, 0,
                LINE, LINE, SHEAR_LINE, false);

        assertTrue(v.kept);
    }

    /** The squareness is judged only when it was part of the compensation. */
    @Test
    public void theSquarenessCountsOnlyWhenItWasCompensated() {
        // Shear read back 0.2 degrees, over the line.
        assertTrue(CompensationVerdict.judge(-0.00164, -0.00187, 0.15, 0.0002, 0.0001, 0.2,
                LINE, LINE, SHEAR_LINE, false).kept);
        CompensationVerdict v = CompensationVerdict.judge(-0.00164, -0.00187, 0.15, 0.0002, 0.0001, 0.2,
                LINE, LINE, SHEAR_LINE, true);
        assertFalse(v.kept);
        assertEquals("squareness outside the line, squareness no closer to square than before",
                v.whatFailed());
        // Leaned back from 0.15 to 0.05: within and closer.
        assertTrue(CompensationVerdict.judge(-0.00164, -0.00187, 0.15, 0.0002, 0.0001, 0.05,
                LINE, LINE, SHEAR_LINE, true).kept);
    }

    /** Two verification readings are judged by their mean, with the worse residual. */
    @Test
    public void twoReadingsAreJudgedByTheirMean() {
        Datum first = new Datum("board", "H1", 0.99880, 1.00020, 0.05, 0.3, false, 0.010, 7);
        Datum second = new Datum("board", "H1", 0.99930, 1.00060, 0.07, 0.3, false, 0.014, 7);

        Datum mean = MachineDiagnostics.meanOf(List.of(first, second));

        assertEquals(0.99905, mean.getScaleX(), 1e-12);
        assertEquals(1.00040, mean.getScaleY(), 1e-12);
        assertEquals(0.06, mean.getShearDegrees(), 1e-12);
        assertEquals(0.014, mean.getRmsResidualMm(), 1e-12);
        assertEquals("board", mean.getBoard());
        // The first alone is outside a 0.1 % line; the mean is within it.
        assertFalse(CompensationVerdict.judge(-0.00164, -0.00187, 0, first.getScaleX() - 1,
                first.getScaleY() - 1, 0, LINE, LINE, SHEAR_LINE, false).kept);
        assertTrue(CompensationVerdict.judge(-0.00164, -0.00187, 0, mean.getScaleX() - 1,
                mean.getScaleY() - 1, 0, LINE, LINE, SHEAR_LINE, false).kept);
    }
}
