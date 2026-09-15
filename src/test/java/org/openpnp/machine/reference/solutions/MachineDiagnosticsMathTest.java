package org.openpnp.machine.reference.solutions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsMath.GcodeSettingLine;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsMath.LinearFit;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsMath.MotionFit;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsMath.Stats;

/**
 * Covers the arithmetic that turns diagnostic measurements into the numbers a user acts on. The
 * move time fit in particular decides whether a machine is reported as running at the
 * acceleration its axes are configured for, so it is checked against times synthesised from known
 * parameters as well as against times with noise on them.
 */
public class MachineDiagnosticsMathTest {

    @Test
    public void statsSummariseASeries() {
        Stats stats = MachineDiagnosticsMath.stats(Arrays.asList(1.0, 2.0, 3.0, 4.0));
        assertEquals(4, stats.count);
        assertEquals(2.5, stats.mean, 1e-9);
        assertEquals(1.2909944, stats.stdDev, 1e-6);
        assertEquals(1.0, stats.min, 1e-9);
        assertEquals(4.0, stats.max, 1e-9);
        assertEquals(3.0, stats.getRange(), 1e-9);
    }

    /** A single measurement has no spread, rather than an undefined one. */
    @Test
    public void statsOfOneSampleHaveNoSpread() {
        Stats stats = MachineDiagnosticsMath.stats(Arrays.asList(7.5));
        assertEquals(1, stats.count);
        assertEquals(7.5, stats.mean, 1e-9);
        assertEquals(0.0, stats.stdDev, 1e-9);
        assertEquals(0.0, stats.getRange(), 1e-9);
    }

    @Test
    public void statsRejectAnEmptySeries() {
        assertThrows(IllegalArgumentException.class,
                () -> MachineDiagnosticsMath.stats(Arrays.asList()));
    }

    @Test
    public void linearFitRecoversALine() {
        double[] x = new double[] { 0, 1, 2, 3, 4 };
        double[] y = new double[] { 1, 3, 5, 7, 9 };
        LinearFit fit = MachineDiagnosticsMath.linearFit(x, y);
        assertEquals(2.0, fit.slope, 1e-9);
        assertEquals(1.0, fit.intercept, 1e-9);
        assertEquals(1.0, fit.rSquared, 1e-9);
        assertEquals(11.0, fit.valueAt(5), 1e-9);
    }

    @Test
    public void moveTimeIsTriangularBelowTheRampDistance() {
        // a=1000, v=200 reaches cruise at 40mm, so 10mm stays triangular: 2*sqrt(10/1000).
        assertEquals(2 * Math.sqrt(10.0 / 1000), MachineDiagnosticsMath.moveTime(10, 1000, 200), 1e-9);
        // 200mm is well past it: 200/200 + 200/1000.
        assertEquals(1.2, MachineDiagnosticsMath.moveTime(200, 1000, 200), 1e-9);
        // Exactly at the ramp distance the two branches agree.
        assertEquals(2 * Math.sqrt(40.0 / 1000), MachineDiagnosticsMath.moveTime(40, 1000, 200), 1e-9);
        assertEquals(0.0, MachineDiagnosticsMath.moveTime(0, 1000, 200), 1e-9);
    }

    @Test
    public void motionFitRecoversKnownParameters() {
        double acceleration = 250;
        double velocity = 120;
        double overhead = 0.11;
        double[] distances = new double[] { 0.5, 1, 2, 5, 10, 20, 50, 100, 200 };
        double[] times = new double[distances.length];
        for (int i = 0; i < distances.length; i++) {
            times[i] = overhead + MachineDiagnosticsMath.moveTime(distances[i], acceleration, velocity);
        }
        MotionFit fit = MachineDiagnosticsMath.fitMotion(distances, times);
        assertEquals(acceleration, fit.acceleration, acceleration * 0.02);
        assertEquals(velocity, fit.velocity, velocity * 0.02);
        assertEquals(overhead, fit.overhead, 0.005);
        assertTrue(fit.rmsResidual < 1e-3, "Residual should be tiny for exact data: " + fit.rmsResidual);
    }

    /** Real timings scatter; the fit has to stay close to the truth rather than chase the noise. */
    @Test
    public void motionFitToleratesNoise() {
        double acceleration = 1000;
        double velocity = 200;
        double overhead = 0.05;
        double[] distances = new double[] { 1, 2, 5, 10, 20, 50, 100, 200, 400 };
        double[] noise = new double[] { 0.003, -0.002, 0.004, -0.001, 0.002, -0.003, 0.001, 0.003, -0.002 };
        double[] times = new double[distances.length];
        for (int i = 0; i < distances.length; i++) {
            times[i] = overhead + MachineDiagnosticsMath.moveTime(distances[i], acceleration, velocity)
                    + noise[i];
        }
        MotionFit fit = MachineDiagnosticsMath.fitMotion(distances, times);
        assertEquals(acceleration, fit.acceleration, acceleration * 0.25);
        assertEquals(velocity, fit.velocity, velocity * 0.1);
        assertTrue(fit.rmsResidual < 0.01, "Residual should stay at the noise level: " + fit.rmsResidual);
    }

    @Test
    public void motionFitNeedsThreeMoves() {
        assertThrows(IllegalArgumentException.class, () -> MachineDiagnosticsMath.fitMotion(
                new double[] { 1, 2 }, new double[] { 0.1, 0.2 }));
    }

    @Test
    public void shortMoveFitRecoversAcceleration() {
        double acceleration = 250;
        double overhead = 0.11;
        double[] distances = new double[] { 0.25, 0.5, 1, 2, 4 };
        double[] times = new double[distances.length];
        for (int i = 0; i < distances.length; i++) {
            times[i] = overhead + 2 * Math.sqrt(distances[i] / acceleration);
        }
        MotionFit fit = MachineDiagnosticsMath.fitAccelerationFromShortMoves(distances, times);
        assertEquals(acceleration, fit.acceleration, 1e-6);
        assertEquals(overhead, fit.overhead, 1e-9);
    }

    @Test
    public void velocityFollowsFromALongMove() {
        double acceleration = 1000;
        double velocity = 200;
        double overhead = 0.05;
        double time = overhead + MachineDiagnosticsMath.moveTime(200, acceleration, velocity);
        Double measured = MachineDiagnosticsMath.velocityFromMoveTime(200, time, acceleration, overhead);
        assertNotNull(measured);
        assertEquals(velocity, measured, 1e-6);
    }

    /** A move that never reached cruise says nothing about the velocity limit. */
    @Test
    public void velocityIsUnknownForATriangularMove() {
        double acceleration = 1000;
        double overhead = 0.05;
        double time = overhead + 2 * Math.sqrt(5.0 / acceleration);
        assertNull(MachineDiagnosticsMath.velocityFromMoveTime(5, time, acceleration, overhead));
        assertNull(MachineDiagnosticsMath.velocityFromMoveTime(5, overhead, acceleration, overhead));
    }

    @Test
    public void settleTimeIsTheStartOfTheFinalQuietRun() {
        double[] times = new double[] { 0.0, 0.1, 0.2, 0.3, 0.4 };
        double[] deviations = new double[] { 5.0, 2.0, 0.4, 0.1, 0.0 };
        assertEquals(0.2, MachineDiagnosticsMath.settleTime(times, deviations, 0.5), 1e-9);
    }

    /** A late disturbance restarts the run, rather than being averaged away. */
    @Test
    public void settleTimeIgnoresAnEarlyQuietPatch() {
        double[] times = new double[] { 0.0, 0.1, 0.2, 0.3, 0.4 };
        double[] deviations = new double[] { 0.1, 0.1, 3.0, 0.2, 0.0 };
        assertEquals(0.3, MachineDiagnosticsMath.settleTime(times, deviations, 0.5), 1e-9);
    }

    @Test
    public void settleTimeIsUnknownWhenTheImageNeverSettles() {
        double[] times = new double[] { 0.0, 0.1, 0.2 };
        double[] deviations = new double[] { 5.0, 4.0, 3.0 };
        assertNull(MachineDiagnosticsMath.settleTime(times, deviations, 0.5));
    }

    @Test
    public void settleTimeIsImmediateWhenNothingEverMoved() {
        double[] times = new double[] { 0.0, 0.1, 0.2 };
        double[] deviations = new double[] { 0.1, 0.05, 0.0 };
        assertEquals(0.0, MachineDiagnosticsMath.settleTime(times, deviations, 0.5), 1e-9);
    }

    private static final List<String> MARLIN_M503 = Arrays.asList(
            "echo:  G21    ; Units in mm (mm)",
            "echo:Steps per unit:",
            "echo:  M92 X320.00 Y320.00 Z40.00 A8.89 B8.89",
            "echo:Maximum feedrates (units/s):",
            "echo:  M203 X50.00 Y50.00 Z30.00 A200.00 B200.00",
            "echo:Maximum Acceleration (units/s2):",
            "echo:  M201 X250.00 Y250.00 Z100.00 A500.00 B500.00",
            "echo:Acceleration (units/s2): P<print_accel> R<retract_accel> T<travel_accel>",
            "echo:  M204 P250.00 R250.00 T250.00",
            "echo:Advanced: B<min_segment_time_us> S<min_feedrate> T<min_travel_feedrate> J<junc_dev>",
            "echo:  M205 B20000.00 S0.00 T0.00 J0.01",
            "ok");

    @Test
    public void marlinSettingsReportIsParsed() {
        Map<String, GcodeSettingLine> settings =
                MachineDiagnosticsMath.parseSettingsReport(MARLIN_M503);
        assertEquals(320.0, settings.get("M92").get("X"), 1e-9);
        assertEquals(40.0, settings.get("M92").get("Z"), 1e-9);
        assertEquals(8.89, settings.get("M92").get("B"), 1e-9);
        assertEquals(50.0, settings.get("M203").get("Y"), 1e-9);
        assertEquals(250.0, settings.get("M201").get("X"), 1e-9);
        assertEquals(250.0, settings.get("M204").get("T"), 1e-9);
        assertEquals(0.01, settings.get("M205").get("J"), 1e-9);
    }

    /**
     * Marlin prints a description naming the code before printing the code itself. Reading the
     * description as settings would turn its placeholder text into values.
     */
    @Test
    public void descriptionLinesAreNotReadAsSettings() {
        Map<String, GcodeSettingLine> settings =
                MachineDiagnosticsMath.parseSettingsReport(MARLIN_M503);
        assertEquals(250.0, settings.get("M204").get("P"), 1e-9);
        assertEquals(3, settings.get("M204").values.size());
        assertEquals(4, settings.get("M205").values.size());
    }

    @Test
    public void unparseableReportYieldsNoSettings() {
        assertTrue(MachineDiagnosticsMath.parseSettingsReport(
                Arrays.asList("$$", "$0=10", "Grbl 1.1f ['$' for help]")).isEmpty());
        assertTrue(MachineDiagnosticsMath.parseSettingsReport(null).isEmpty());
    }

    @Test
    public void seriesAcceptsCommasAndSpaces() throws Exception {
        double[] series = MachineDiagnosticsMath.parseSeries("0.5, 1 2;5  10");
        assertArrayEqualsWithin(new double[] { 0.5, 1, 2, 5, 10 }, series);
    }

    /** A dropped value would silently change what the test measured, so it is an error. */
    @Test
    public void seriesRejectsBadValues() {
        assertThrows(Exception.class, () -> MachineDiagnosticsMath.parseSeries("1, two, 3"));
        assertThrows(Exception.class, () -> MachineDiagnosticsMath.parseSeries("1, 0, 3"));
        assertThrows(Exception.class, () -> MachineDiagnosticsMath.parseSeries("1, -5"));
        assertThrows(Exception.class, () -> MachineDiagnosticsMath.parseSeries("  "));
    }

    @Test
    public void seriesRoundTripsThroughItsText() throws Exception {
        double[] series = new double[] { 0.5, 1, 2, 5, 10, 20, 50, 100, 200 };
        assertArrayEqualsWithin(series,
                MachineDiagnosticsMath.parseSeries(MachineDiagnosticsMath.formatSeries(series)));
        assertEquals("0.5, 1, 2", MachineDiagnosticsMath.formatSeries(new double[] { 0.5, 1, 2 }));
    }

    /** The median is what one wild frame cannot move, which is why it is the measurement. */
    @Test
    public void medianIgnoresOneWildFrame() {
        assertEquals(10.02, MachineDiagnosticsMath.median(
                java.util.List.of(10.01, 10.03, 10.02, 10.00, 47.0)), 1e-9);
        assertEquals(2.5, MachineDiagnosticsMath.median(java.util.List.of(4.0, 1.0, 2.0, 3.0)), 1e-9);
        assertEquals(7.0, MachineDiagnosticsMath.median(java.util.List.of(7.0)), 1e-9);
        assertTrue(Double.isNaN(MachineDiagnosticsMath.median(java.util.List.of())));
    }

    private static void assertArrayEqualsWithin(double[] expected, double[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], 1e-9);
        }
    }
}
