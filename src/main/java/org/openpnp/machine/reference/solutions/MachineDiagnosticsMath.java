/*
 * Copyright (C) 2026 Pono contributors
 *
 * This file is part of OpenPnP.
 *
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 *
 * For more information about OpenPnP visit http://openpnp.org
 */

package org.openpnp.machine.reference.solutions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The arithmetic behind {@link MachineDiagnostics}, separated from it so that it can be tested
 * without a machine. Everything here is a pure function of its arguments.
 */
public class MachineDiagnosticsMath {

    private MachineDiagnosticsMath() {
    }

    /**
     * Mean, spread and extremes of a measurement series.
     */
    public static class Stats {
        public final int count;
        public final double mean;
        /** Sample standard deviation, i.e. divided by n-1. Zero for a single sample. */
        public final double stdDev;
        public final double min;
        public final double max;

        public Stats(int count, double mean, double stdDev, double min, double max) {
            this.count = count;
            this.mean = mean;
            this.stdDev = stdDev;
            this.min = min;
            this.max = max;
        }

        public double getRange() {
            return max - min;
        }

        @Override
        public String toString() {
            return String.format("n=%d mean=%.4f sd=%.4f min=%.4f max=%.4f",
                    count, mean, stdDev, min, max);
        }
    }

    /** What a decaying oscillation was seen doing: how big, how fast, how quickly it died. */
    public static final class Oscillation {
        /** Largest excursion, in the units of the samples. */
        public final double amplitude;
        /** Frequency from the zero crossings, or null when there were too few to count. */
        public final Double frequencyHz;
        /** Time constant of the exponential decay of the peaks, or null when too few peaks. */
        public final Double decaySeconds;
        /** How many times the signal crossed zero while still above the threshold. */
        public final int crossings;

        Oscillation(double amplitude, Double frequencyHz, Double decaySeconds, int crossings) {
            this.amplitude = amplitude;
            this.frequencyHz = frequencyHz;
            this.decaySeconds = decaySeconds;
            this.crossings = crossings;
        }
    }

    /**
     * Read an oscillation off a signed signal sampled at irregular times.
     * <p>
     * The frequency is the zero crossings over the stretch during which the signal was still
     * larger than {@code threshold}, halved; the decay is a straight line fitted to the log of
     * the local peaks, whose slope is minus one over the time constant. Nothing here can see a
     * frequency above half the sample rate: that folds into a lower one, and the caller says so.
     *
     * @param times   Sample times, ascending, seconds.
     * @param signal  Signed displacement at each time, already relative to where it ends up.
     * @param threshold Below this the signal counts as settled and is not read.
     */
    public static Oscillation oscillation(double[] times, double[] signal, double threshold) {
        if (times.length != signal.length || times.length < 3) {
            return new Oscillation(0, null, null, 0);
        }
        double amplitude = 0;
        int last = -1;
        for (int i = 0; i < signal.length; i++) {
            amplitude = Math.max(amplitude, Math.abs(signal[i]));
            if (Math.abs(signal[i]) > threshold) {
                last = i;
            }
        }
        if (last < 2) {
            return new Oscillation(amplitude, null, null, 0);
        }
        int crossings = 0;
        List<double[]> peaks = new ArrayList<>();
        for (int i = 1; i <= last; i++) {
            if (Math.signum(signal[i]) != Math.signum(signal[i - 1]) && signal[i] != 0
                    && signal[i - 1] != 0) {
                crossings++;
            }
            if (i < last && Math.abs(signal[i]) >= Math.abs(signal[i - 1])
                    && Math.abs(signal[i]) > Math.abs(signal[i + 1])
                    && Math.abs(signal[i]) > threshold) {
                peaks.add(new double[] { times[i], Math.abs(signal[i]) });
            }
        }
        Double frequency = null;
        double span = times[last] - times[0];
        if (crossings >= 2 && span > 0) {
            frequency = crossings / (2 * span);
        }
        Double decay = null;
        if (peaks.size() >= 3) {
            double[] t = new double[peaks.size()];
            double[] logPeak = new double[peaks.size()];
            for (int i = 0; i < peaks.size(); i++) {
                t[i] = peaks.get(i)[0];
                logPeak[i] = Math.log(peaks.get(i)[1]);
            }
            LinearFit fit = linearFit(t, logPeak);
            if (fit.slope < 0) {
                decay = -1 / fit.slope;
            }
        }
        return new Oscillation(amplitude, frequency, decay, crossings);
    }

    /** The vertex of the parabola through the best point and its two neighbours; the peak. */
    public static double parabolicPeak(double[] xs, double[] ys) {
        int best = 0;
        for (int i = 1; i < ys.length; i++) {
            if (ys[i] > ys[best]) {
                best = i;
            }
        }
        if (best == 0 || best == ys.length - 1) {
            return xs[best];
        }
        double x0 = xs[best - 1], x1 = xs[best], x2 = xs[best + 1];
        double y0 = ys[best - 1], y1 = ys[best], y2 = ys[best + 1];
        double denominator = (x0 - x1) * (x0 - x2) * (x1 - x2);
        if (denominator == 0) {
            return x1;
        }
        double a = (x2 * (y1 - y0) + x1 * (y0 - y2) + x0 * (y2 - y1)) / denominator;
        double b = (x2 * x2 * (y0 - y1) + x1 * x1 * (y2 - y0) + x0 * x0 * (y1 - y2)) / denominator;
        if (a >= 0) {
            return x1;
        }
        double vertex = -b / (2 * a);
        return Math.max(x0, Math.min(x2, vertex));
    }

    /**
     * A plane transform fitted between two sets of matching points, and what it is made of: the
     * scale along each axis, the angle by which the axes are not square, the rotation, and what
     * is left over at each point.
     */
    public static final class Affine {
        /** x' = a*x + b*y + tx, y' = c*x + d*y + ty. */
        public final double a, b, c, d, tx, ty;
        /** Length of the transformed unit vectors: how long a nominal millimetre comes out. */
        public final double scaleX, scaleY;
        /** Degrees by which the transformed Y axis leans towards +X, short of square. */
        public final double shearDegrees;
        /** Rotation of the transformed X axis, degrees. */
        public final double rotationDegrees;
        /** Whether the transform flips handedness, which a mirrored board would. */
        public final boolean mirrored;
        public final double[] residualsX, residualsY;
        public final double rmsResidual;

        Affine(double a, double b, double c, double d, double tx, double ty, double[] residualsX,
                double[] residualsY) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.tx = tx;
            this.ty = ty;
            this.residualsX = residualsX;
            this.residualsY = residualsY;
            scaleX = Math.hypot(a, c);
            scaleY = Math.hypot(b, d);
            rotationDegrees = Math.toDegrees(Math.atan2(c, a));
            double determinant = a * d - b * c;
            mirrored = determinant < 0;
            // The angle between the transformed axes, less the right angle they should make.
            double between = Math.toDegrees(Math.atan2(d, b)) - rotationDegrees;
            while (between > 180) {
                between -= 360;
            }
            while (between <= -180) {
                between += 360;
            }
            // Positive when the Y axis leans towards +X: the angle between the axes is short of
            // the right angle by this much.
            shearDegrees = mirrored ? -(between + 90) : 90 - between;
            double sum = 0;
            for (int i = 0; i < residualsX.length; i++) {
                sum += residualsX[i] * residualsX[i] + residualsY[i] * residualsY[i];
            }
            rmsResidual = residualsX.length > 0 ? Math.sqrt(sum / residualsX.length) : 0;
        }

        public double[] apply(double x, double y) {
            return new double[] { a * x + b * y + tx, c * x + d * y + ty };
        }
    }

    /**
     * Least squares fit of the six-parameter affine transform taking {@code from} to {@code to}.
     * Three points determine it; more leave residuals, which is the point of having more.
     */
    public static Affine affineFit(double[][] from, double[][] to) {
        int n = from.length;
        if (n < 3 || to.length != n) {
            throw new IllegalArgumentException("An affine fit needs at least three matched points.");
        }
        // Normal equations for x' and y' separately, each in (x, y, 1).
        double[][] m = new double[3][3];
        double[] rx = new double[3];
        double[] ry = new double[3];
        for (int i = 0; i < n; i++) {
            double[] p = { from[i][0], from[i][1], 1 };
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    m[r][c] += p[r] * p[c];
                }
                rx[r] += p[r] * to[i][0];
                ry[r] += p[r] * to[i][1];
            }
        }
        double[] px = solve3(m, rx);
        double[] py = solve3(m, ry);
        double[] residualsX = new double[n];
        double[] residualsY = new double[n];
        for (int i = 0; i < n; i++) {
            residualsX[i] = to[i][0] - (px[0] * from[i][0] + px[1] * from[i][1] + px[2]);
            residualsY[i] = to[i][1] - (py[0] * from[i][0] + py[1] * from[i][1] + py[2]);
        }
        return new Affine(px[0], px[1], py[0], py[1], px[2], py[2], residualsX, residualsY);
    }

    private static double[] solve3(double[][] m, double[] r) {
        double[][] a = { m[0].clone(), m[1].clone(), m[2].clone() };
        double[] b = r.clone();
        for (int col = 0; col < 3; col++) {
            int pivot = col;
            for (int row = col + 1; row < 3; row++) {
                if (Math.abs(a[row][col]) > Math.abs(a[pivot][col])) {
                    pivot = row;
                }
            }
            double[] t = a[col];
            a[col] = a[pivot];
            a[pivot] = t;
            double tb = b[col];
            b[col] = b[pivot];
            b[pivot] = tb;
            if (Math.abs(a[col][col]) < 1e-12) {
                throw new IllegalArgumentException("The points do not span a plane.");
            }
            for (int row = 0; row < 3; row++) {
                if (row == col) {
                    continue;
                }
                double f = a[row][col] / a[col][col];
                for (int k = col; k < 3; k++) {
                    a[row][k] -= f * a[col][k];
                }
                b[row] -= f * b[col];
            }
        }
        return new double[] { b[0] / a[0][0], b[1] / a[1][1], b[2] / a[2][2] };
    }

    /**
     * Sub-pixel positions of the ticks in a one-dimensional intensity profile: runs of samples
     * that stand out from the background by more than {@code contrast}, each reduced to the
     * centroid of how far it stands out. Bright ticks on dark and dark ticks on bright are both
     * read; whichever side of the median has the larger excursions is taken as the ticks.
     *
     * @param profile  One value per column (or row), the tick band summed across.
     * @param minWidth Runs shorter than this many samples are noise, not ticks.
     */
    public static double[] tickCentres(double[] profile, int minWidth) {
        if (profile.length < 3) {
            return new double[0];
        }
        List<Double> sorted = new ArrayList<>();
        for (double v : profile) {
            sorted.add(v);
        }
        double median = median(sorted);
        double above = 0;
        double below = 0;
        for (double v : profile) {
            above = Math.max(above, v - median);
            below = Math.max(below, median - v);
        }
        boolean bright = above >= below;
        double excursion = bright ? above : below;
        if (excursion <= 0) {
            return new double[0];
        }
        double threshold = excursion * 0.5;
        List<Double> centres = new ArrayList<>();
        int start = -1;
        for (int i = 0; i <= profile.length; i++) {
            double deviation = i < profile.length ? (bright ? profile[i] - median : median - profile[i]) : 0;
            if (deviation > threshold) {
                if (start < 0) {
                    start = i;
                }
            }
            else if (start >= 0) {
                if (i - start >= minWidth) {
                    // The run was found at half height; the centroid is taken over the whole
                    // foot of the tick, out to where it sinks into the background, so that a
                    // tick centred between two samples is not pulled towards the one whose
                    // shoulder cleared the threshold.
                    double foot = excursion * 0.1;
                    int from = start;
                    while (from > 0 && (bright ? profile[from - 1] - median : median - profile[from - 1]) > foot) {
                        from--;
                    }
                    int to = i;
                    while (to < profile.length && (bright ? profile[to] - median : median - profile[to]) > foot) {
                        to++;
                    }
                    double weight = 0;
                    double moment = 0;
                    for (int k = from; k < to; k++) {
                        double d = (bright ? profile[k] - median : median - profile[k]) - foot;
                        if (d > 0) {
                            weight += d;
                            moment += d * k;
                        }
                    }
                    centres.add(moment / weight);
                }
                start = -1;
            }
        }
        double[] result = new double[centres.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = centres.get(i);
        }
        return result;
    }

    /** Amplitude and phase of a sinusoid of known period fitted to samples: the periodic error. */
    public static double[] sinusoidFit(double[] x, double[] y, double period) {
        double sc = 0, ss = 0, scc = 0, sss = 0, scs = 0, s1 = 0, ssum = 0, csum = 0, n = x.length;
        double sy = 0;
        for (int i = 0; i < x.length; i++) {
            double w = 2 * Math.PI * x[i] / period;
            double c = Math.cos(w);
            double s = Math.sin(w);
            sc += c * y[i];
            ss += s * y[i];
            scc += c * c;
            sss += s * s;
            scs += c * s;
            csum += c;
            ssum += s;
            sy += y[i];
        }
        // Solve [scc scs csum; scs sss ssum; csum ssum n] [A B C] = [sc ss sy].
        double[][] m = { { scc, scs, csum }, { scs, sss, ssum }, { csum, ssum, n } };
        double[] p = solve3(m, new double[] { sc, ss, sy });
        double amplitude = Math.hypot(p[0], p[1]);
        double phase = Math.atan2(p[1], p[0]);
        return new double[] { amplitude, phase, p[2] };
    }

    /** The median: the middle value, or the mean of the two middle values. */
    public static double median(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return Double.NaN;
        }
        List<Double> sorted = new java.util.ArrayList<>(values);
        java.util.Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
    }

    public static Stats stats(List<Double> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("No values to summarise.");
        }
        double sum = 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double value : values) {
            sum += value;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        double mean = sum / values.size();
        double sumSquares = 0;
        for (double value : values) {
            sumSquares += (value - mean) * (value - mean);
        }
        double stdDev = values.size() > 1 ? Math.sqrt(sumSquares / (values.size() - 1)) : 0.0;
        return new Stats(values.size(), mean, stdDev, min, max);
    }

    /**
     * A least squares straight line through a set of points.
     */
    public static class LinearFit {
        public final double slope;
        public final double intercept;
        /** Coefficient of determination. One for a perfect fit, zero when the line explains nothing. */
        public final double rSquared;
        public final int count;

        public LinearFit(double slope, double intercept, double rSquared, int count) {
            this.slope = slope;
            this.intercept = intercept;
            this.rSquared = rSquared;
            this.count = count;
        }

        public double valueAt(double x) {
            return slope * x + intercept;
        }

        @Override
        public String toString() {
            return String.format("y = %.6f x + %.6f (r2=%.4f, n=%d)", slope, intercept, rSquared, count);
        }
    }

    public static LinearFit linearFit(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Coordinate arrays differ in length.");
        }
        if (x.length < 2) {
            throw new IllegalArgumentException("A line needs at least two points.");
        }
        double sumX = 0;
        double sumY = 0;
        for (int i = 0; i < x.length; i++) {
            sumX += x[i];
            sumY += y[i];
        }
        double meanX = sumX / x.length;
        double meanY = sumY / y.length;
        double sxx = 0;
        double sxy = 0;
        for (int i = 0; i < x.length; i++) {
            sxx += (x[i] - meanX) * (x[i] - meanX);
            sxy += (x[i] - meanX) * (y[i] - meanY);
        }
        if (sxx == 0) {
            throw new IllegalArgumentException("All the points share one x, no line can be fitted.");
        }
        double slope = sxy / sxx;
        double intercept = meanY - slope * meanX;
        double ssTotal = 0;
        double ssResidual = 0;
        for (int i = 0; i < x.length; i++) {
            ssTotal += (y[i] - meanY) * (y[i] - meanY);
            double residual = y[i] - (slope * x[i] + intercept);
            ssResidual += residual * residual;
        }
        double rSquared = ssTotal == 0 ? 1.0 : 1.0 - ssResidual / ssTotal;
        return new LinearFit(slope, intercept, rSquared, x.length);
    }

    /**
     * The time a constant acceleration controller needs for a move of the given distance,
     * excluding any fixed per-command overhead. Triangular below the distance where the cruise
     * velocity is reached, trapezoidal above it.
     */
    public static double moveTime(double distance, double acceleration, double velocity) {
        if (distance <= 0) {
            return 0;
        }
        double rampDistance = velocity * velocity / acceleration;
        if (distance <= rampDistance) {
            return 2 * Math.sqrt(distance / acceleration);
        }
        return distance / velocity + velocity / acceleration;
    }

    /**
     * Acceleration, cruise velocity and fixed overhead as measured, which is what the machine
     * really does rather than what the axis settings ask for.
     */
    public static class MotionFit {
        public final double acceleration;
        public final double velocity;
        /** Fixed per-move cost: command round trip, planner and completion handshake. */
        public final double overhead;
        public final double rmsResidual;
        public final int count;

        public MotionFit(double acceleration, double velocity, double overhead, double rmsResidual,
                int count) {
            this.acceleration = acceleration;
            this.velocity = velocity;
            this.overhead = overhead;
            this.rmsResidual = rmsResidual;
            this.count = count;
        }

        public double timeAt(double distance) {
            return overhead + moveTime(distance, acceleration, velocity);
        }

        @Override
        public String toString() {
            return String.format("a=%.1f v=%.1f overhead=%.4fs rms=%.4fs n=%d",
                    acceleration, velocity, overhead, rmsResidual, count);
        }
    }

    private static final int FIT_GRID_STEPS = 32;
    private static final int FIT_REFINEMENTS = 7;

    /**
     * Fit acceleration, velocity and overhead to a set of measured move times.
     * <p>
     * The model is not linear in its parameters and the samples are few, so this searches a
     * log-spaced grid and refines it around the best cell rather than using a gradient method,
     * which on this model walks off into an arbitrarily large acceleration whenever the short
     * moves are dominated by the overhead. For a given acceleration and velocity the best
     * overhead follows in closed form, so only two dimensions are searched.
     *
     * @param distances Move distances, in the same length unit as the wanted result.
     * @param times Measured times in seconds, one per distance.
     */
    public static MotionFit fitMotion(double[] distances, double[] times) {
        if (distances.length != times.length) {
            throw new IllegalArgumentException("Distance and time arrays differ in length.");
        }
        if (distances.length < 3) {
            throw new IllegalArgumentException("Fitting acceleration, velocity and overhead needs "
                    + "at least three moves.");
        }
        double aLow = 1;
        double aHigh = 1e6;
        double vLow = 1;
        double vHigh = 1e5;
        double bestA = Double.NaN;
        double bestV = Double.NaN;
        double bestOverhead = 0;
        double bestResidual = Double.POSITIVE_INFINITY;
        for (int refinement = 0; refinement < FIT_REFINEMENTS; refinement++) {
            double aStep = Math.log(aHigh / aLow) / (FIT_GRID_STEPS - 1);
            double vStep = Math.log(vHigh / vLow) / (FIT_GRID_STEPS - 1);
            for (int i = 0; i < FIT_GRID_STEPS; i++) {
                double a = aLow * Math.exp(aStep * i);
                for (int j = 0; j < FIT_GRID_STEPS; j++) {
                    double v = vLow * Math.exp(vStep * j);
                    double overheadSum = 0;
                    for (int k = 0; k < distances.length; k++) {
                        overheadSum += times[k] - moveTime(distances[k], a, v);
                    }
                    // A negative overhead has no physical meaning; the constrained optimum is zero.
                    double overhead = Math.max(0, overheadSum / distances.length);
                    double squares = 0;
                    for (int k = 0; k < distances.length; k++) {
                        double residual = times[k] - overhead - moveTime(distances[k], a, v);
                        squares += residual * residual;
                    }
                    double residual = Math.sqrt(squares / distances.length);
                    if (residual < bestResidual) {
                        bestResidual = residual;
                        bestA = a;
                        bestV = v;
                        bestOverhead = overhead;
                    }
                }
            }
            aLow = Math.max(1e-3, bestA * Math.exp(-aStep));
            aHigh = bestA * Math.exp(aStep);
            vLow = Math.max(1e-3, bestV * Math.exp(-vStep));
            vHigh = bestV * Math.exp(vStep);
        }
        return new MotionFit(bestA, bestV, bestOverhead, bestResidual, distances.length);
    }

    /**
     * Acceleration and overhead from moves short enough to stay triangular, where the time is
     * linear in the square root of the distance and no velocity limit is involved.
     *
     * @return A fit whose velocity is {@link Double#POSITIVE_INFINITY}, since these moves say
     *         nothing about it.
     */
    public static MotionFit fitAccelerationFromShortMoves(double[] distances, double[] times) {
        double[] rootDistances = new double[distances.length];
        for (int i = 0; i < distances.length; i++) {
            rootDistances[i] = Math.sqrt(distances[i]);
        }
        LinearFit fit = linearFit(rootDistances, times);
        if (fit.slope <= 0) {
            throw new IllegalArgumentException("Move times do not grow with distance, so no "
                    + "acceleration can be derived from them.");
        }
        double acceleration = 4 / (fit.slope * fit.slope);
        double overhead = Math.max(0, fit.intercept);
        double squares = 0;
        for (int i = 0; i < distances.length; i++) {
            double residual = times[i] - overhead - moveTime(distances[i], acceleration,
                    Double.POSITIVE_INFINITY);
            squares += residual * residual;
        }
        return new MotionFit(acceleration, Double.POSITIVE_INFINITY, overhead,
                Math.sqrt(squares / distances.length), distances.length);
    }

    /**
     * The cruise velocity implied by a single trapezoidal move, given an acceleration and overhead
     * established from the short moves.
     *
     * @return The velocity, or null if the move was too short to reach cruise, in which case its
     *         time carries no velocity information.
     */
    public static Double velocityFromMoveTime(double distance, double time, double acceleration,
            double overhead) {
        double moveTime = time - overhead;
        if (moveTime <= 0) {
            return null;
        }
        // v² - a·t·v + a·d = 0, of whose two roots the smaller is the cruise velocity; the larger
        // one describes a ramp that would overshoot the distance.
        double discriminant = acceleration * acceleration * moveTime * moveTime
                - 4 * acceleration * distance;
        if (discriminant < 0) {
            // The move never left the ramp, so it was triangular after all.
            return null;
        }
        return (acceleration * moveTime - Math.sqrt(discriminant)) / 2;
    }

    /**
     * The moment a camera image stopped moving, from a series of measured positions.
     *
     * @param times Sample times in seconds, ascending.
     * @param deviations Distance of each sample from the settled position, in any one unit.
     * @param threshold Deviation at or below which a sample counts as settled.
     * @return The time of the first sample of the final settled run, or null if the series never
     *         settles, i.e. if the last sample itself is beyond the threshold.
     */
    public static Double settleTime(double[] times, double[] deviations, double threshold) {
        if (times.length != deviations.length) {
            throw new IllegalArgumentException("Time and deviation arrays differ in length.");
        }
        if (times.length == 0) {
            return null;
        }
        int firstSettled = times.length;
        for (int i = times.length - 1; i >= 0; i--) {
            if (deviations[i] > threshold) {
                break;
            }
            firstSettled = i;
        }
        if (firstSettled == times.length) {
            return null;
        }
        return times[firstSettled];
    }

    /**
     * The axis word values of one G-code parameter line, e.g. X, Y, Z of an <code>M92</code>, or
     * P, R, T of an <code>M204</code>.
     */
    public static class GcodeSettingLine {
        public final String code;
        public final Map<String, Double> values;
        public final String raw;

        public GcodeSettingLine(String code, Map<String, Double> values, String raw) {
            this.code = code;
            this.values = values;
            this.raw = raw;
        }

        public Double get(String letter) {
            return values.get(letter.toUpperCase());
        }

        @Override
        public String toString() {
            return code + " " + values;
        }
    }

    /**
     * The M-code has to be the first word of the line, so that the prose Marlin prints above each
     * settings line - which names the very code that follows it - is not read as a second copy of
     * the settings.
     */
    private static final Pattern SETTING_LINE = Pattern.compile(
            "^(?:ok\\s+)?(?:echo\\s*:\\s*)?\\s*([GM]\\d+)\\b(.*)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern WORD = Pattern.compile("([A-Za-z])\\s*(-?\\d+(?:\\.\\d+)?)");

    /**
     * Parse the settings report of a controller, as produced by <code>M503</code>, into one entry
     * per G-code found. A code reported more than once keeps its last occurrence, which is what
     * a firmware that echoes both a default and an override means by it.
     * <p>
     * This understands the Marlin layout. Other firmwares report their settings differently and
     * will yield fewer entries or none, which is why the raw text is reported alongside.
     */
    public static Map<String, GcodeSettingLine> parseSettingsReport(List<String> lines) {
        Map<String, GcodeSettingLine> settings = new LinkedHashMap<>();
        if (lines == null) {
            return settings;
        }
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String text = line.trim();
            int comment = text.indexOf(';');
            if (comment >= 0) {
                text = text.substring(0, comment).trim();
            }
            Matcher matcher = SETTING_LINE.matcher(text);
            if (!matcher.matches()) {
                continue;
            }
            String code = matcher.group(1).toUpperCase();
            Map<String, Double> values = new LinkedHashMap<>();
            Matcher word = WORD.matcher(matcher.group(2));
            while (word.find()) {
                values.put(word.group(1).toUpperCase(), Double.valueOf(word.group(2)));
            }
            if (!values.isEmpty()) {
                settings.put(code, new GcodeSettingLine(code, values, line.trim()));
            }
        }
        return settings;
    }

    /**
     * Parse a distance or speed series as typed into the diagnostics panel: values separated by
     * commas or whitespace. Values that are not numbers, and values that are not positive, are
     * rejected rather than skipped, because silently dropping one changes what the test measured.
     */
    public static double[] parseSeries(String text) throws Exception {
        List<Double> values = new ArrayList<>();
        if (text != null) {
            for (String token : text.split("[,;\\s]+")) {
                if (token.isEmpty()) {
                    continue;
                }
                double value;
                try {
                    value = Double.parseDouble(token);
                }
                catch (NumberFormatException e) {
                    throw new Exception("\"" + token + "\" is not a number.");
                }
                if (!(value > 0)) {
                    throw new Exception("\"" + token + "\" is not greater than zero.");
                }
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            throw new Exception("The series is empty.");
        }
        double[] series = new double[values.size()];
        for (int i = 0; i < series.length; i++) {
            series[i] = values.get(i);
        }
        return series;
    }

    public static String formatSeries(double[] series) {
        StringBuilder text = new StringBuilder();
        for (double value : series) {
            if (text.length() > 0) {
                text.append(", ");
            }
            if (value == Math.rint(value) && Math.abs(value) < 1e9) {
                text.append(Long.toString((long) value));
            }
            else {
                text.append(String.format("%s", trimTrailingZeroes(value)));
            }
        }
        return text.toString();
    }

    private static String trimTrailingZeroes(double value) {
        String text = String.format("%.4f", value);
        text = text.replaceAll("0+$", "");
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
