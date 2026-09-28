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

package org.openpnp.machine.reference.calibration;

import org.openpnp.util.SimpleGraph;

/**
 * How far a calibration step has got, told by the code that carries it out: which of the step's
 * phases it is in ({@link org.openpnp.model.CalibrationStep#getPhases()}), a line about what the
 * phase found, the chart it draws, why it chose what it chose, and when it waits for someone.
 * <p>
 * The calibrations report from the machine's task thread, not the one the calibration page runs
 * on, so this is one sink for the program rather than one per thread: one calibration runs at a
 * time. Nothing listens while no calibration page runs one, and the calls cost nothing then -
 * the calibrations are the same when Issues and Solutions or a script starts them.
 */
public final class CalibrationProgress {
    /** Where a step's progress goes: the runner, which tells the page. */
    public interface Sink {
        /** The step is in its phase with this index, from 0. */
        default void phase(int index) {
        }

        /** The step is in its phase with this key; a key the step has no phase for is ignored. */
        default void phase(String key) {
        }

        /** A measurement group started: the phase that runs it. */
        default void group(String name) {
        }

        /** A line about the phase, in the display language. */
        default void detail(String text) {
        }

        /** A chart the phase draws; it goes on filling in while the phase runs. */
        default void chart(String title, SimpleGraph graph) {
        }

        /** Why the step chose what it chose, in the display language. */
        default void decision(String text) {
        }

        /**
         * The step waits for someone to do what the instructions say. It goes on when proceed is
         * run, and gives up when cancel is: the step's own buttons, shown where the step is.
         */
        default void person(String instructions, Runnable proceed, Runnable cancel) {
        }

        /** Nobody is waited for any more. */
        default void personDone() {
        }
    }

    private static volatile Sink sink;

    private CalibrationProgress() {
    }

    /** From now on what the calibrations tell goes here, until {@link #detach}. */
    public static void attach(Sink to) {
        sink = to;
    }

    public static void detach(Sink from) {
        if (sink == from) {
            sink = null;
        }
    }

    public static boolean isAttached() {
        return sink != null;
    }

    public static void phase(int index) {
        Sink s = sink;
        if (s != null) {
            s.phase(index);
        }
    }

    /**
     * The phase by its key, as {@link org.openpnp.model.CalibrationStep#getPhases()} names it:
     * code that more than one step runs says where it is without knowing which step runs it.
     */
    public static void phase(String key) {
        Sink s = sink;
        if (s != null) {
            s.phase(key);
        }
    }

    public static void group(String name) {
        Sink s = sink;
        if (s != null) {
            s.group(name);
        }
    }

    public static void detail(String text) {
        Sink s = sink;
        if (s != null) {
            s.detail(text);
        }
    }

    public static void chart(String title, SimpleGraph graph) {
        Sink s = sink;
        if (s != null && graph != null) {
            s.chart(title, graph);
        }
    }

    public static void decision(String text) {
        Sink s = sink;
        if (s != null) {
            s.decision(text);
        }
    }

    /** @return Whether anyone was told: when nobody listens, the caller asks the way it always did. */
    public static boolean person(String instructions, Runnable proceed, Runnable cancel) {
        Sink s = sink;
        if (s == null) {
            return false;
        }
        s.person(instructions, proceed, cancel);
        return true;
    }

    public static void personDone() {
        Sink s = sink;
        if (s != null) {
            s.personDone();
        }
    }
}
