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

    /** A damped sine sampled at 30 frames a second reads back its frequency and its decay. */
    @Test
    public void aDampedOscillationReadsBackItsFrequencyAndDecay() {
        int n = 60;
        double[] t = new double[n];
        double[] s = new double[n];
        double frequency = 6.0;
        double tau = 0.25;
        for (int i = 0; i < n; i++) {
            t[i] = i / 30.0;
            s[i] = 8.0 * Math.exp(-t[i] / tau) * Math.sin(2 * Math.PI * frequency * t[i]);
        }

        MachineDiagnosticsMath.Oscillation seen = MachineDiagnosticsMath.oscillation(t, s, 0.3);

        // Five samples a period do not land on the crest; the amplitude read is a lower bound.
        assertTrue(seen.amplitude > 5.0 && seen.amplitude <= 8.0, "amplitude " + seen.amplitude);
        assertNotNull(seen.frequencyHz);
        assertEquals(frequency, seen.frequencyHz, 1.0);
        assertNotNull(seen.decaySeconds);
        assertEquals(tau, seen.decaySeconds, 0.08);
    }

    @Test
    public void aSignalThatNeverLeavesTheThresholdHasNoOscillation() {
        double[] t = { 0, 0.033, 0.066, 0.1, 0.133 };
        double[] s = { 0.1, -0.1, 0.05, -0.05, 0.02 };

        MachineDiagnosticsMath.Oscillation seen = MachineDiagnosticsMath.oscillation(t, s, 0.3);

        assertNull(seen.frequencyHz);
        assertNull(seen.decaySeconds);
        assertEquals(0.1, seen.amplitude, 1e-9);
    }

    /** A focus curve is flat on top over the depth of field; the peak is the middle of the top. */
    @Test
    public void theParabolicPeakOfAFlatToppedCurveIsItsMiddle() {
        double[] z = new double[21];
        double[] score = new double[21];
        for (int i = 0; i < z.length; i++) {
            z[i] = 8.6 + i * 0.05;
            // A broad hump centred on 9.10 with a flat top and some noise.
            double d = Math.abs(z[i] - 9.10);
            score[i] = 100 - 40 * Math.max(0, d - 0.1) * Math.max(0, d - 0.1) * 25
                    + ((i * 7) % 3 - 1) * 0.6;
        }

        assertEquals(9.10, MachineDiagnosticsMath.parabolicPeak(z, score), 0.03);
    }

    /** The peak of a focus curve lies between the samples; the parabola finds it. */
    @Test
    public void theParabolicPeakLiesBetweenTheSamples() {
        double[] z = { -0.10, -0.05, 0.00, 0.05, 0.10 };
        double truePeak = 0.02;
        double[] score = new double[z.length];
        for (int i = 0; i < z.length; i++) {
            score[i] = 100 - 1000 * Math.pow(z[i] - truePeak, 2);
        }

        assertEquals(truePeak, MachineDiagnosticsMath.parabolicPeak(z, score), 1e-6);
        // A peak on the edge of the sweep is reported as the edge, not extrapolated beyond it.
        assertEquals(0.10, MachineDiagnosticsMath.parabolicPeak(z,
                new double[] { 1, 2, 3, 4, 5 }), 1e-9);
    }

    /** The datum board's seven fiducials through a known scale, squareness and rotation. */
    @Test
    public void anAffineFitReadsBackScaleSquarenessAndRotation() {
        double[][] board = { { 0, 0 }, { -10, 5 }, { 10, 5 }, { -10, -10 }, { 10, -10 },
                { -35, -20 }, { 35, -20 } };
        double scaleX = 1.0025, scaleY = 0.9990;
        double shear = Math.toRadians(0.15);
        double rotation = Math.toRadians(-1.2);
        double[][] machine = new double[board.length][];
        for (int i = 0; i < board.length; i++) {
            // Scale, then lean Y by the shear, then rotate, then translate.
            double x = board[i][0] * scaleX + board[i][1] * scaleY * Math.sin(shear);
            double y = board[i][1] * scaleY * Math.cos(shear);
            machine[i] = new double[] {
                    217.3 + x * Math.cos(rotation) - y * Math.sin(rotation),
                    196.5 + x * Math.sin(rotation) + y * Math.cos(rotation) };
        }

        MachineDiagnosticsMath.Affine fit = MachineDiagnosticsMath.affineFit(board, machine);

        assertEquals(scaleX, fit.scaleX, 1e-6);
        assertEquals(scaleY, fit.scaleY, 1e-6);
        assertEquals(0.15, fit.shearDegrees, 1e-4);
        assertEquals(-1.2, fit.rotationDegrees, 1e-4);
        assertEquals(0, fit.rmsResidual, 1e-9);
        assertTrue(!fit.mirrored);
        assertEquals(217.3, fit.apply(0, 0)[0], 1e-9);
    }

    @Test
    public void aMirroredBoardIsRecognisedAsSuch() {
        double[][] board = { { 0, 0 }, { 10, 0 }, { 0, 10 }, { 10, 10 } };
        double[][] machine = { { 0, 0 }, { 10, 0 }, { 0, -10 }, { 10, -10 } };

        assertTrue(MachineDiagnosticsMath.affineFit(board, machine).mirrored);
    }

    /** One point off by 0.03 mm shows up as the residual, not as a change of scale. */
    @Test
    public void aStrayPointLandsInTheResiduals() {
        double[][] board = { { 0, 0 }, { -10, 5 }, { 10, 5 }, { -10, -10 }, { 10, -10 },
                { -35, -20 }, { 35, -20 } };
        double[][] machine = new double[board.length][];
        for (int i = 0; i < board.length; i++) {
            machine[i] = new double[] { 100 + board[i][0], 50 + board[i][1] };
        }
        machine[3][0] += 0.03;

        MachineDiagnosticsMath.Affine fit = MachineDiagnosticsMath.affineFit(board, machine);

        assertEquals(1.0, fit.scaleX, 5e-4);
        assertTrue(Math.abs(fit.residualsX[3]) > 0.015, "residual " + fit.residualsX[3]);
        assertTrue(fit.rmsResidual > 0.005 && fit.rmsResidual < 0.03);
    }

    /** Bright ticks on a dark ground, read to a fraction of a sample. */
    @Test
    public void tickCentresAreReadToSubSamplePrecision() {
        double[] profile = new double[400];
        java.util.Arrays.fill(profile, 20);
        double[] truth = { 40.3, 117.3, 194.3, 271.3, 348.3 };
        for (double centre : truth) {
            // An 11 sample wide tick with a soft edge, centred between samples.
            for (int k = -7; k <= 7; k++) {
                int i = (int) Math.round(centre) + k;
                double distance = Math.abs(i - centre);
                profile[i] += 200 * Math.max(0, Math.min(1, 6 - distance));
            }
        }

        double[] found = MachineDiagnosticsMath.tickCentres(profile, 4);

        assertEquals(truth.length, found.length);
        for (int i = 0; i < truth.length; i++) {
            assertEquals(truth[i], found[i], 0.15);
        }
        // Dark ticks on a bright ground read the same.
        double[] inverted = new double[profile.length];
        for (int i = 0; i < profile.length; i++) {
            inverted[i] = 255 - profile[i];
        }
        double[] foundDark = MachineDiagnosticsMath.tickCentres(inverted, 4);
        assertEquals(truth.length, foundDark.length);
        assertEquals(truth[2], foundDark[2], 0.15);
    }

    /**
     * A 2 mm belt pitch sampled every millimetre lands twice a period, which cannot separate the
     * amplitude from the phase; the ruler is therefore stepped in quarter millimetres.
     */
    @Test
    public void aSinusoidOfKnownPeriodReadsBackItsAmplitude() {
        double[] x = new double[121];
        double[] y = new double[121];
        for (int i = 0; i < x.length; i++) {
            x[i] = i * 0.25;
            y[i] = 0.004 + 0.012 * Math.sin(2 * Math.PI * x[i] / 2.0 + 0.7);
        }

        double[] fit = MachineDiagnosticsMath.sinusoidFit(x, y, 2.0);

        assertEquals(0.012, fit[0], 1e-9);
        assertEquals(0.004, fit[2], 1e-9);
    }

    /** Digits and edges in the tick band read as spikes; only the run a pitch apart is the ruler. */
    @Test
    public void onlyTheConsistentlySpacedRunOfTicksIsKept() {
        double[] features = { 40.0, 100.0, 177.0, 254.2, 331.0, 408.1, 484.9, 561.8, 700.0, 703.0 };

        double[] chain = MachineDiagnosticsMath.consistentChain(features, 77.0, 0.15);

        assertEquals(7, chain.length);
        assertEquals(100.0, chain[0], 1e-9);
        assertEquals(561.8, chain[6], 1e-9);
        // A single missing tick does not break the run: a gap of two pitches is allowed.
        double[] gappy = { 100.0, 177.0, 331.0, 408.0 };
        assertEquals(4, MachineDiagnosticsMath.consistentChain(gappy, 77.0, 0.15).length);
    }

    private static void assertArrayEqualsWithin(double[] expected, double[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], 1e-9);
        }
    }
}
