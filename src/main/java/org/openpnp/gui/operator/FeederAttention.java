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

package org.openpnp.gui.operator;

import java.util.HashSet;
import java.util.Set;

import org.openpnp.machine.reference.ReferenceFeeder;
import org.openpnp.model.BoardLocation;
import org.openpnp.model.Job;
import org.openpnp.model.Placement;
import org.openpnp.spi.Feeder;

/**
 * Which feeders want a look: one the job needs - it carries a part the job places, on the side
 * each board faces - whose picks failed, or that is out or running low. The feeders page's tab
 * and badge and production mode ask the same question here. The feeders page used to count every
 * feeder that was not ready, those switched off or without a part among them, and production
 * mode only the job's empty and low ones, with its own reading of which parts the job places.
 */
public final class FeederAttention {
    /** What is wrong with a feeder, the worst first. */
    public enum Trouble {
        Fault, Empty, Low, None
    }

    private FeederAttention() {
    }

    /** The ids of the parts the job places: enabled placements on enabled boards, on the side each faces. */
    public static Set<String> partsOf(Job job) {
        Set<String> parts = new HashSet<>();
        if (job == null) {
            return parts;
        }
        for (BoardLocation board : job.getBoardLocations()) {
            if (!board.isEnabled()) {
                continue;
            }
            for (Placement placement : board.getPlacementsHolder().getPlacements()) {
                if (placement.getType() == Placement.Type.Placement && placement.isEnabled()
                        && placement.getSide() == board.getGlobalSide() && placement.getPart() != null) {
                    parts.add(placement.getPart().getId());
                }
            }
        }
        return parts;
    }

    public static boolean isNeeded(Feeder feeder, Set<String> parts) {
        return feeder.getPart() != null && parts.contains(feeder.getPart().getId());
    }

    /**
     * Out before switched off: a feeder that runs out is switched off by the job, and read the
     * other way round it showed as switched off rather than empty.
     */
    public static Trouble troubleOf(Feeder feeder) {
        if (feeder.getPart() == null) {
            return Trouble.None;
        }
        if (feeder instanceof ReferenceFeeder && ((ReferenceFeeder) feeder).summariseJobFaults().startsWith("X")) { //$NON-NLS-1$
            return Trouble.Fault;
        }
        if (feeder.isEmpty()) {
            return Trouble.Empty;
        }
        if (feeder.isLow()) {
            return Trouble.Low;
        }
        return Trouble.None;
    }

    /** Needed by the job and in trouble: what the badge counts. */
    public static boolean wantsLook(Feeder feeder, Set<String> parts) {
        return isNeeded(feeder, parts) && troubleOf(feeder) != Trouble.None;
    }
}
