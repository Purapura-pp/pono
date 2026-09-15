/*
 * Copyright (C) 2026 Pono contributors
 * 
 * This file is part of Pono, a modified version of OpenPnP.
 * 
 * Pono is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * Pono is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Pono. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package org.openpnp.machine.reference.solutions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A reference artifact of known geometry that the machine can look at: a board with features
 * whose positions relative to each other are known to better than the machine is.
 * <p>
 * The one built in is the LumenPnP datum board, PCB-0004 REV00, which carries the machine's
 * primary calibration fiducial and, around it, six more copper fiducials, a 30 mm copper ruler,
 * a solder-mask grid and a pair of 5 mm discs. Its geometry is taken from the KiCad source, in
 * board coordinates with the centre fiducial FID1 at the origin, X towards FID7 and Y towards
 * the top edge of the board. Features defined in copper are placed by the same photoplot as the
 * fiducials and agree with them to about 0.02 mm; features defined in solder mask or silkscreen
 * carry the registration of that layer to the copper, typically 0.05 to 0.075 mm, and are
 * marked as such so that nothing precise is measured against them.
 */
public class DatumBoard {
    /** Which manufacturing layer places a feature, which is what its accuracy follows. */
    public enum Layer {
        Copper, Mask, Silk
    }

    /** A round feature: a fiducial dot or a disc. */
    public static final class Dot {
        public final String name;
        public final double x;
        public final double y;
        public final double diameterMm;
        public final Layer layer;

        public Dot(String name, double x, double y, double diameterMm, Layer layer) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.diameterMm = diameterMm;
            this.layer = layer;
        }
    }

    /** A ruler: equally spaced ticks along a line, the ticks running across it. */
    public static final class Ruler {
        /** Board coordinates of the first and last tick, measured at the ticks' base. */
        public final double x0, y0, x1, y1;
        public final double pitchMm;
        public final int ticks;
        public final double tickLengthMm;
        public final double tickWidthMm;
        public final Layer layer;

        public Ruler(double x0, double y0, double x1, double y1, double pitchMm, int ticks,
                double tickLengthMm, double tickWidthMm, Layer layer) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.pitchMm = pitchMm;
            this.ticks = ticks;
            this.tickLengthMm = tickLengthMm;
            this.tickWidthMm = tickWidthMm;
            this.layer = layer;
        }

        /** The middle of the ruler's tick band, which is where to point the camera. */
        public double[] centre() {
            return new double[] { (x0 + x1) / 2, (y0 + y1) / 2 };
        }
    }

    private final String name;
    private final Dot anchor;
    private final List<Dot> fiducials;
    private final List<Dot> discs;
    private final Ruler ruler;

    public DatumBoard(String name, Dot anchor, List<Dot> fiducials, List<Dot> discs, Ruler ruler) {
        this.name = name;
        this.anchor = anchor;
        this.fiducials = Collections.unmodifiableList(new ArrayList<>(fiducials));
        this.discs = Collections.unmodifiableList(new ArrayList<>(discs));
        this.ruler = ruler;
    }

    public String getName() {
        return name;
    }

    /** The feature the machine already knows the position of: its primary fiducial. */
    public Dot getAnchor() {
        return anchor;
    }

    /** Every copper fiducial including the anchor, in the order they are best found. */
    public List<Dot> getFiducials() {
        return fiducials;
    }

    /** The fiducials nearest the anchor, which is where the board's orientation is read from. */
    public List<Dot> getOrientationFiducials() {
        List<Dot> near = new ArrayList<>();
        for (Dot dot : fiducials) {
            if (dot != anchor && Math.hypot(dot.x - anchor.x, dot.y - anchor.y) < 20) {
                near.add(dot);
            }
        }
        return near;
    }

    public List<Dot> getDiscs() {
        return discs;
    }

    public Ruler getRuler() {
        return ruler;
    }

    /**
     * A board offset turned into the machine's frame under one of the eight ways a board can lie
     * on a table: four quarter turns, each either way up. The exact angle is fitted afterwards;
     * this only has to be near enough for the fiducials to be found where they are predicted.
     *
     * @param quarterTurns 0 to 3, anticlockwise.
     * @param mirrored     Whether board Y is reversed, as it is when the board is seen from the
     *                     other side or the camera image is flipped.
     */
    public static double[] orient(double x, double y, int quarterTurns, boolean mirrored) {
        double yy = mirrored ? -y : y;
        switch (((quarterTurns % 4) + 4) % 4) {
            case 1:
                return new double[] { -yy, x };
            case 2:
                return new double[] { -x, -yy };
            case 3:
                return new double[] { yy, -x };
            default:
                return new double[] { x, yy };
        }
    }

    /**
     * The LumenPnP datum board, PCB-0004 REV00, from
     * {@code pnp/pcb/datum/datum.kicad_pcb} of the LumenPnP repository. KiCad's Y axis points
     * down the sheet; board Y here points up, so a KiCad y becomes -(y - y_FID1).
     */
    public static DatumBoard lumenPnp() {
        Dot fid1 = new Dot("FID1", 0, 0, 1.0, Layer.Copper);
        List<Dot> fiducials = List.of(
                fid1,
                new Dot("FID2", -10, 5, 1.0, Layer.Copper),
                new Dot("FID3", 10, 5, 1.0, Layer.Copper),
                new Dot("FID4", -10, -10, 1.0, Layer.Copper),
                new Dot("FID5", 10, -10, 1.0, Layer.Copper),
                new Dot("FID6", -35, -20, 1.0, Layer.Copper),
                new Dot("FID7", 35, -20, 1.0, Layer.Copper));
        List<Dot> discs = List.of(
                new Dot("copper disc", -22.5, -7.5, 5.0, Layer.Mask),
                new Dot("white disc", 22.5, -7.5, 5.0, Layer.Silk));
        // 31 ticks from x = -15 to +15, 1 mm apart, standing 3 mm tall at their tallest from the
        // flat edge at y = -15 up to y = -12; the camera is aimed at the middle of that band.
        Ruler ruler = new Ruler(-15, -13.5, 15, -13.5, 1.0, 31, 3.0, 0.15, Layer.Copper);
        return new DatumBoard("LumenPnP datum PCB-0004", fid1, fiducials, discs, ruler);
    }
}
