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
import java.util.List;

import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;

/**
 * The LumenPnP staging plate, as far as the diagnostics need it: a checkerboard of round holes
 * over the whole bed, which is what the hysteresis map measures against.
 * <p>
 * From the plate's STEP model ({@code 3D_staging-plate_2026-08-16.step}): a 600 by 240 mm plate
 * with 3.2 mm holes on a 15 mm lattice of 39 columns by 15 rows, at every node whose two indices
 * sum to an even number in the drawing's frame - 293 nodes - less the five under the 45 mm hole
 * the bottom camera looks through (its own node, which is on the lattice, and the four diagonal
 * neighbours 21 mm from it) and less the eight in the end columns where the plate's own 5.3 mm
 * bolt holes sit 5 mm outside the node: 280 holes. The camera hole is mid-way along the field's
 * 570 mm and 45 mm in from one long edge.
 * <p>
 * The datum board is bolted to this lattice: its mounting holes are 30 mm apart in X and 15 mm
 * apart in Y with a 15 mm stagger, and its centre fiducial FID1 sits midway between two of them,
 * on a node with no hole, in the camera hole's column, 45 mm from the camera's node. So the
 * primary fiducial anchors the lattice in machine coordinates: the nearest holes are 15 mm from
 * it along X and along Y, and every hole is at the fiducial plus 15 mm times a pair of indices
 * whose sum is odd. Which way the plate lies along Y is read off the bottom camera's position:
 * the camera is 45 mm to one side of the fiducial, and the field's long side is on the other.
 * The plate's own drawing frame is never needed, and X does not matter, since the field and its
 * missing holes are symmetric about the camera's column.
 */
public final class StagingPlate {
    public static final double PITCH_MM = 15.0;
    public static final double HOLE_DIAMETER_MM = 3.2;
    /** The bottom camera's hole, and the lattice nodes it swallows. */
    public static final double CAMERA_HOLE_DIAMETER_MM = 45.0;
    /** The camera's node from FID1, in lattice indices, along the fiducial's column. */
    public static final int CAMERA_COLUMN = 0;
    public static final int CAMERA_ROW = -3;
    /** Columns either side of the fiducial: 19, so 570 mm across. */
    public static final int COLUMNS_EACH_SIDE = 19;
    /** Rows on the camera's side of the fiducial and on the other: 6 and 8, so 210 mm across. */
    public static final int ROWS_CAMERA_SIDE = 6;
    public static final int ROWS_FAR_SIDE = 8;
    /**
     * The rows, counted from the fiducial towards the far side, whose end-column nodes are given
     * over to the plate's bolt holes and have no lattice hole.
     */
    public static final int[] BOLT_ROWS = { -6, 0, 2, 8 };

    private StagingPlate() {
    }

    /**
     * Which way the plate lies along Y, from the fiducial: +1 when the camera is at lower Y than
     * the fiducial - the LumenPnP as built, the board behind the camera - and -1 when the plate
     * lies the other way round. Without the camera's position the plate is taken as built.
     */
    public static int ySign(Location fiducial, Location cameraHole) {
        if (cameraHole == null) {
            return 1;
        }
        Location f = fiducial.convertToUnits(LengthUnit.Millimeters);
        Location c = cameraHole.convertToUnits(LengthUnit.Millimeters);
        return c.getY() > f.getY() ? -1 : 1;
    }

    /**
     * The lattice hole nearest to a wanted position, anchored on the primary fiducial.
     *
     * @param fiducial   The primary fiducial, FID1 of the datum board, in machine coordinates.
     * @param wantedX    Where along X the hole is wanted, machine millimetres.
     * @param wantedY    Where along Y.
     * @param cameraHole The bottom camera's position in the same frame, which tells which way
     *                   the plate lies along Y; null if not known, and the plate is taken as
     *                   built.
     * @param stepInX    When the nearest node has no hole, whether to step to the next column
     *                   (true) or the next row (false). A reading of the Y axis wants its Y kept,
     *                   so it steps in X; a reading of the X axis steps in Y.
     * @return The hole's machine location at the fiducial's Z, or null if the wanted position is
     *         outside the plate's hole field.
     */
    public static Location nearestHole(Location fiducial, double wantedX, double wantedY,
            Location cameraHole, boolean stepInX) {
        Location f = fiducial.convertToUnits(LengthUnit.Millimeters);
        double di = (wantedX - f.getX()) / PITCH_MM;
        double dj = (wantedY - f.getY()) / PITCH_MM;
        long i = Math.round(di);
        long j = Math.round(dj);
        // A hole sits on the nodes whose index sum is odd. If the nearest node is not one,
        // step to the neighbour that is, towards the wanted position.
        if ((i + j) % 2 == 0) {
            if (stepInX) {
                i += (di - i) >= 0 ? 1 : -1;
            }
            else {
                j += (dj - j) >= 0 ? 1 : -1;
            }
        }
        Location hole = hole(fiducial, i, j, cameraHole);
        // A node the plate has no hole at - under the camera, or given over to a bolt - is
        // passed over for the next hole along the stepping direction, two nodes on, the nearer
        // side first.
        for (int k = 2; hole == null && k <= 4; k += 2) {
            boolean forward = stepInX ? (di - i) >= 0 : (dj - j) >= 0;
            for (int side : new int[] { forward ? 1 : -1, forward ? -1 : 1 }) {
                hole = stepInX ? hole(fiducial, i + side * k, j, cameraHole)
                        : hole(fiducial, i, j + side * k, cameraHole);
                if (hole != null) {
                    break;
                }
            }
        }
        return hole;
    }

    /** The hole at lattice indices (i, j) from FID1, or null where the plate has none. */
    public static Location hole(Location fiducial, long i, long j, Location cameraHole) {
        if ((i + j) % 2 == 0) {
            return null;
        }
        int sign = ySign(fiducial, cameraHole);
        long row = j * sign;
        if (Math.abs(i) > COLUMNS_EACH_SIDE || row < -ROWS_CAMERA_SIDE || row > ROWS_FAR_SIDE) {
            return null;
        }
        if (Math.abs(i) == COLUMNS_EACH_SIDE) {
            for (int boltRow : BOLT_ROWS) {
                if (row == boltRow) {
                    return null;
                }
            }
        }
        // The camera hole is drilled on the lattice, so the nodes it swallows are known by index:
        // its own and the eight around it, of which the four on the diagonals would have had
        // holes. The camera's measured position, a little off the node, is not used for this.
        if (Math.abs(i - CAMERA_COLUMN) <= 1 && Math.abs(row - CAMERA_ROW) <= 1) {
            return null;
        }
        Location f = fiducial.convertToUnits(LengthUnit.Millimeters);
        double x = f.getX() + i * PITCH_MM;
        double y = f.getY() + j * PITCH_MM;
        return new Location(LengthUnit.Millimeters, x, y, f.getZ(), 0).convertToUnits(fiducial.getUnits());
    }

    /** Every hole of the plate in machine coordinates, from FID1: 280 of them. */
    public static List<Location> allHoles(Location fiducial, Location cameraHole) {
        List<Location> holes = new ArrayList<>();
        int sign = ySign(fiducial, cameraHole);
        int jMin = Math.min(-ROWS_CAMERA_SIDE * sign, ROWS_FAR_SIDE * sign);
        int jMax = Math.max(-ROWS_CAMERA_SIDE * sign, ROWS_FAR_SIDE * sign);
        for (int j = jMin; j <= jMax; j++) {
            for (int i = -COLUMNS_EACH_SIDE; i <= COLUMNS_EACH_SIDE; i++) {
                Location hole = hole(fiducial, i, j, cameraHole);
                if (hole != null) {
                    holes.add(hole);
                }
            }
        }
        return holes;
    }

    /** The machine Y range the hole field covers, from FID1's Y, low then high. */
    public static double[] fieldYRange(Location fiducial, Location cameraHole) {
        Location f = fiducial.convertToUnits(LengthUnit.Millimeters);
        int sign = ySign(fiducial, cameraHole);
        double a = f.getY() - ROWS_CAMERA_SIDE * PITCH_MM * sign;
        double b = f.getY() + ROWS_FAR_SIDE * PITCH_MM * sign;
        return new double[] { Math.min(a, b), Math.max(a, b) };
    }
}
