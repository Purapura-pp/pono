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

import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;

/**
 * The LumenPnP staging plate, as far as the diagnostics need it: a checkerboard of round holes
 * over the whole bed, which is what the hysteresis map measures against.
 * <p>
 * From the plate's STEP model ({@code 3D_staging-plate_2026-08-16.step}): a 600 by 240 mm plate
 * carrying 280 holes of 3.2 mm on a 15 mm lattice, every other node - the nodes whose two
 * indices sum to an even number - over a field 570 by 210 mm, less the five nodes under the
 * 45 mm hole the bottom camera looks through. The datum board's four mounting holes are 30 mm
 * apart in X and 15 mm apart in Y with a 15 mm stagger, which is this lattice; its centre
 * fiducial FID1 sits midway between two of them, on a node that has no hole. So the primary
 * fiducial anchors the lattice: the nearest holes are 15 mm from it along X and along Y, and
 * every hole is at the fiducial plus 15 mm times a pair of indices whose sum is odd.
 * <p>
 * The plate's own drawing frame is not needed. The lattice's phase is read off the fiducial,
 * its parity off the datum board's mounting, and its pitch is 15 mm whichever way the plate
 * lies, since a checkerboard looks the same turned or mirrored.
 */
public final class StagingPlate {
    public static final double PITCH_MM = 15.0;
    public static final double HOLE_DIAMETER_MM = 3.2;
    /** The bottom camera's hole, and the lattice nodes it swallows. */
    public static final double CAMERA_HOLE_DIAMETER_MM = 45.0;
    /**
     * How far from FID1 the hole field reaches, along X and along Y, generously: the field is
     * 570 by 210 mm and the datum board sits about 135 mm from its left end and 90 mm from one
     * of its long edges, so these cover it whichever way round the plate lies. A node predicted
     * where the plate has ended costs one look and nothing more.
     */
    public static final double FIELD_HALF_X_MM = 290;
    public static final double FIELD_HALF_Y_MM = 120;

    private StagingPlate() {
    }

    /**
     * The lattice hole nearest to a wanted position, anchored on the primary fiducial.
     *
     * @param fiducial   The primary fiducial, FID1 of the datum board, in machine coordinates.
     * @param wantedX    Where along X the hole is wanted, machine millimetres.
     * @param wantedY    Where along Y.
     * @param cameraHole The bottom camera's position in the same frame, whose neighbourhood has
     *                   no holes; null if not known.
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
        double x = f.getX() + i * PITCH_MM;
        double y = f.getY() + j * PITCH_MM;
        if (Math.abs(x - f.getX()) > FIELD_HALF_X_MM || Math.abs(y - f.getY()) > FIELD_HALF_Y_MM) {
            return null;
        }
        if (cameraHole != null) {
            Location c = cameraHole.convertToUnits(LengthUnit.Millimeters);
            if (Math.hypot(x - c.getX(), y - c.getY()) < CAMERA_HOLE_DIAMETER_MM / 2 + PITCH_MM / 2) {
                return null;
            }
        }
        return new Location(LengthUnit.Millimeters, x, y, f.getZ(), 0).convertToUnits(fiducial.getUnits());
    }
}
