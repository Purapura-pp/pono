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

package org.openpnp.gui.machinesettings;

import org.openpnp.Translations;
import org.openpnp.machine.reference.ReferenceHead.NozzleSolution;
import org.openpnp.machine.reference.axis.ReferenceCamClockwiseAxis;
import org.openpnp.machine.reference.axis.ReferenceCamCounterClockwiseAxis;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis;
import org.openpnp.machine.reference.axis.ReferenceMappedAxis;
import org.openpnp.spi.Axis;
import org.openpnp.spi.Head;
import org.openpnp.spi.Nozzle;

/**
 * How a head's nozzles move up and down, read off their Z axes: each on a motor of its own, two
 * on one motor with the second mapped the other way, or two on a cam. The head's own record of it
 * is not to be trusted - the LumenPnP configuration says standalone of a pair that is negated,
 * which is how accepting the issue built on it turned the pair into one nozzle.
 */
final class NozzleStructure {
    /** Null when the nozzles are none of the three: mixed, or with no Z axis. */
    final NozzleSolution solution;
    final int nozzles;
    /** How many times the solution is repeated: nozzles for standalone, pairs for the others. */
    final int multiplier;

    private NozzleStructure(NozzleSolution solution, int nozzles, int multiplier) {
        this.solution = solution;
        this.nozzles = nozzles;
        this.multiplier = multiplier;
    }

    static NozzleStructure of(Head head) {
        int controller = 0;
        int mapped = 0;
        int cam = 0;
        int other = 0;
        for (Nozzle nozzle : head.getNozzles()) {
            Axis z = nozzle.getAxisZ();
            if (z instanceof ReferenceCamCounterClockwiseAxis || z instanceof ReferenceCamClockwiseAxis) {
                cam++;
            }
            else if (z instanceof ReferenceMappedAxis) {
                mapped++;
            }
            else if (z instanceof ReferenceControllerAxis) {
                controller++;
            }
            else {
                other++;
            }
        }
        int n = head.getNozzles().size();
        if (n == 0 || other > 0) {
            return new NozzleStructure(null, n, 0);
        }
        if (cam == n && n % 2 == 0) {
            return new NozzleStructure(NozzleSolution.DualCam, n, n / 2);
        }
        if (mapped > 0 && mapped == controller && cam == 0) {
            return new NozzleStructure(NozzleSolution.DualNegated, n, mapped);
        }
        if (controller == n) {
            return new NozzleStructure(NozzleSolution.Standalone, n, n);
        }
        return new NozzleStructure(null, n, 0);
    }

    /** The number of nozzles a solution repeated so many times makes. */
    static int nozzles(NozzleSolution solution, int multiplier) {
        return solution == NozzleSolution.Standalone ? multiplier : 2 * multiplier;
    }

    static String name(NozzleSolution solution) {
        return Translations.getString("MachineSettings.Structure." //$NON-NLS-1$
                + (solution == null ? "Mixed" : solution.name())); //$NON-NLS-1$
    }

    String name() {
        return name(solution);
    }
}
