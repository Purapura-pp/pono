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
