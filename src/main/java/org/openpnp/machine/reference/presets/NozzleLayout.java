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

package org.openpnp.machine.reference.presets;

import java.util.List;

import org.openpnp.machine.reference.ReferenceHead.NozzleSolution;

/**
 * How a head's nozzles move up and down, read off the kind of each one's Z axis: each on a motor
 * of its own, two on one motor with the second mapped the other way, or two on a cam. The head's
 * own record of it is not to be trusted - the LumenPnP configuration says standalone of a pair
 * that is negated. The same reading serves a machine loaded and a machine.xml not loaded.
 */
public final class NozzleLayout {
    /** What a nozzle's Z axis is. */
    public enum Kind {
        Controller, Mapped, Cam, Other
    }

    /** Null when the nozzles are none of the three: mixed, or with no Z axis. */
    public final NozzleSolution solution;
    public final int nozzles;
    /** How many times the solution is repeated: nozzles for standalone, pairs for the others. */
    public final int multiplier;

    private NozzleLayout(NozzleSolution solution, int nozzles, int multiplier) {
        this.solution = solution;
        this.nozzles = nozzles;
        this.multiplier = multiplier;
    }

    public static NozzleLayout of(List<Kind> zAxes) {
        int controller = 0;
        int mapped = 0;
        int cam = 0;
        int other = 0;
        for (Kind kind : zAxes) {
            switch (kind) {
                case Controller:
                    controller++;
                    break;
                case Mapped:
                    mapped++;
                    break;
                case Cam:
                    cam++;
                    break;
                default:
                    other++;
                    break;
            }
        }
        int n = zAxes.size();
        if (n == 0 || other > 0) {
            return new NozzleLayout(null, n, 0);
        }
        if (cam == n && n % 2 == 0) {
            return new NozzleLayout(NozzleSolution.DualCam, n, n / 2);
        }
        if (mapped > 0 && mapped == controller && cam == 0) {
            return new NozzleLayout(NozzleSolution.DualNegated, n, mapped);
        }
        if (controller == n) {
            return new NozzleLayout(NozzleSolution.Standalone, n, n);
        }
        return new NozzleLayout(null, n, 0);
    }

    /** The kind of an axis by its class name, as machine.xml gives it. */
    public static Kind kindOf(String className) {
        if (className == null) {
            return Kind.Other;
        }
        if (className.endsWith(".ReferenceCamCounterClockwiseAxis") || className.endsWith(".ReferenceCamClockwiseAxis")) {
            return Kind.Cam;
        }
        if (className.endsWith(".ReferenceMappedAxis")) {
            return Kind.Mapped;
        }
        if (className.endsWith(".ReferenceControllerAxis")) {
            return Kind.Controller;
        }
        return Kind.Other;
    }
}
