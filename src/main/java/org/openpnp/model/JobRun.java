/*
 * Copyright (C) 2026 Pono
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

package org.openpnp.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What the job's current run did, placement by placement and moment by moment: the status the
 * placements table shows, the last placement and the time left in the status bar, the run log
 * on the job page. It lasts as long as the run and is not saved with the job; whether a placement
 * was placed is, as before, in the job's placed status.
 * <p>
 * The job processor writes it on the machine task thread and the pages read it on the event
 * thread. A listener hears {@link #PROPERTY_RUN} after every change, on the thread that made it.
 */
public class JobRun extends AbstractModelObject {
    public static final String PROPERTY_RUN = "run"; //$NON-NLS-1$
    /** The events kept; a long job's early ones go first. */
    private static final int EVENTS_KEPT = 2000;

    /** Where a placement stands in the run. */
    public enum State {
        /** Planned onto a nozzle and being picked, aligned and placed. */
        Placing,
        /** Its feeder ran out; the placement waits for the feeder to be refilled. */
        WaitingForFeeder,
        Placed,
        /** Left out of the run, as disabled. */
        Skipped,
        /** Given up on, with the error that made it. */
        Error
    }

    public enum EventKind {
        Started,
        Fiducials,
        Placed,
        Retry,
        FeederEmpty,
        Skipped,
        Error,
        Finished,
        Stopped
    }

    /** One placement in this run. */
    public static final class PlacementRun {
        private final String placementId;
        private volatile State state;
        private volatile String nozzle;
        private volatile String feeder;
        private volatile int pickFailures;
        private volatile String error;
        private volatile long startedMillis;
        private volatile long durationMillis;
        private volatile Location alignment;

        PlacementRun(String placementId) {
            this.placementId = placementId;
        }

        public String getPlacementId() {
            return placementId;
        }

        public State getState() {
            return state;
        }

        /** The nozzle it was planned onto, by name. */
        public String getNozzle() {
            return nozzle;
        }

        /** The feeder it was picked from, or that ran out, by name. */
        public String getFeeder() {
            return feeder;
        }

        /** How many picks of it failed and were retried. */
        public int getPickFailures() {
            return pickFailures;
        }

        public String getError() {
            return error;
        }

        /** From being planned to being placed, or 0 while it is not placed. */
        public long getDurationMillis() {
            return durationMillis;
        }

        /** How far the part sat off the nozzle, as bottom vision found it: x, y and rotation. Null if not aligned. */
        public Location getAlignment() {
            return alignment;
        }
    }

    /** Something that happened in the run. */
    public static final class Event {
        private final long millis;
        private final EventKind kind;
        private final String placementId;
        private final String detail;

        Event(long millis, EventKind kind, String placementId, String detail) {
            this.millis = millis;
            this.kind = kind;
            this.placementId = placementId;
            this.detail = detail;
        }

        public long getMillis() {
            return millis;
        }

        public EventKind getKind() {
            return kind;
        }

        /** The placement it is about, or null. */
        public String getPlacementId() {
            return placementId;
        }

        /** What else there is to say: the nozzle, the feeder, the error, the board. */
        public String getDetail() {
            return detail;
        }
    }

    private final Map<String, PlacementRun> placements = new ConcurrentHashMap<>();
    private final List<Event> events = new CopyOnWriteArrayList<>();
    private final List<Long> placedMillis = new CopyOnWriteArrayList<>();
    private volatile long startedMillis;

    /** The key a placement of a board or panel instance is kept under, as its placed status is. */
    public static String key(PlacementsHolderLocation<?> location, String placementId) {
        return location.getUniqueId() + PlacementsHolderLocation.ID_DELIMITTER + placementId;
    }

    /** A new run: everything the last one recorded is forgotten. */
    public void start() {
        placements.clear();
        events.clear();
        placedMillis.clear();
        lastPlaced = null;
        currentKey = null;
        startedMillis = System.currentTimeMillis();
        event(EventKind.Started, null, null);
    }

    public long getStartedMillis() {
        return startedMillis;
    }

    public void placing(String key, String placementId, String nozzle) {
        PlacementRun run = run(key, placementId);
        if (run.state != State.Placing) {
            run.startedMillis = System.currentTimeMillis();
        }
        run.state = State.Placing;
        run.nozzle = nozzle;
        currentKey = key;
        changed();
    }

    /** The feeder the placement's part is about to be fed from, by name. */
    public void feeding(String key, String placementId, String feeder) {
        run(key, placementId).feeder = feeder;
        changed();
    }

    private volatile String currentKey;

    /** The key of the placement being placed now, or null: the one planned last and not yet done. */
    public String getCurrentKey() {
        PlacementRun current = getCurrent();
        return current == null ? null : currentKey;
    }

    /** The placement being placed now, or null. */
    public PlacementRun getCurrent() {
        String key = currentKey;
        PlacementRun run = key == null ? null : placements.get(key);
        return run != null && run.state == State.Placing ? run : null;
    }

    /** How many placements of the run are in the state. */
    public int count(State state) {
        int count = 0;
        for (PlacementRun run : placements.values()) {
            if (run.state == state) {
                count++;
            }
        }
        return count;
    }

    /** The placements waiting for the feeder named, by their ids. */
    public List<String> waitingFor(String feeder) {
        List<String> waiting = new ArrayList<>();
        for (PlacementRun run : placements.values()) {
            if (run.state == State.WaitingForFeeder && feeder.equals(run.feeder)) {
                waiting.add(run.placementId);
            }
        }
        return waiting;
    }

    public void waitingForFeeder(String key, String placementId, String feeder) {
        PlacementRun run = run(key, placementId);
        run.state = State.WaitingForFeeder;
        run.feeder = feeder;
        event(EventKind.FeederEmpty, placementId, feeder);
    }

    public void pickFailed(String key, String placementId, String feeder) {
        PlacementRun run = run(key, placementId);
        run.pickFailures++;
        run.feeder = feeder;
        event(EventKind.Retry, placementId, feeder);
    }

    public void aligned(String key, String placementId, Location offsets) {
        run(key, placementId).alignment = offsets;
        changed();
    }

    public void placed(String key, String placementId) {
        PlacementRun run = run(key, placementId);
        long now = System.currentTimeMillis();
        run.state = State.Placed;
        run.durationMillis = run.startedMillis > 0 ? now - run.startedMillis : 0;
        run.error = null;
        placedMillis.add(now);
        lastPlaced = run;
        event(EventKind.Placed, placementId, run.nozzle);
    }

    private volatile PlacementRun lastPlaced;

    /** The placement placed last, with how long it took, or null before the first. */
    public PlacementRun getLastPlacedRun() {
        return lastPlaced;
    }

    public void skipped(String key, String placementId) {
        run(key, placementId).state = State.Skipped;
        changed();
    }

    public void failed(String key, String placementId, String message) {
        PlacementRun run = run(key, placementId);
        run.state = State.Error;
        run.error = message;
        event(EventKind.Error, placementId, message);
    }

    /** The fiducials of the boards and panels named were checked; message is null when they were found. */
    public void fiducials(String boards, String message) {
        event(message == null ? EventKind.Fiducials : EventKind.Error, null,
                message == null ? boards : boards + ": " + message); //$NON-NLS-1$
    }

    public void finished(int placed) {
        event(EventKind.Finished, null, String.valueOf(placed));
    }

    public void stopped() {
        event(EventKind.Stopped, null, null);
    }

    /** The placement's record in this run, or null if the run has not reached it. */
    public PlacementRun get(String key) {
        return placements.get(key);
    }

    public List<Event> getEvents() {
        return Collections.unmodifiableList(events);
    }

    /** The last placement placed, or null. */
    public Event getLastPlaced() {
        for (int i = events.size() - 1; i >= 0; i--) {
            Event event = events.get(i);
            if (event.kind == EventKind.Placed) {
                return event;
            }
        }
        return null;
    }

    public int getPlacedCount() {
        return placedMillis.size();
    }

    /**
     * The time between placements, averaged over the run so far: what one more placement costs,
     * nozzles working in parallel and all. NaN before there are two.
     */
    public double getCycleSeconds() {
        List<Long> placed = new ArrayList<>(placedMillis);
        if (placed.size() < 2) {
            return Double.NaN;
        }
        return (placed.get(placed.size() - 1) - placed.get(0)) / 1000.0 / (placed.size() - 1);
    }

    /** The time the placements still to do would take at the run's pace; NaN when it has none yet. */
    public double getRemainingSeconds(int placementsLeft) {
        double cycle = getCycleSeconds();
        return Double.isNaN(cycle) ? Double.NaN : cycle * Math.max(0, placementsLeft);
    }

    private PlacementRun run(String key, String placementId) {
        return placements.computeIfAbsent(key, k -> new PlacementRun(placementId));
    }

    private void event(EventKind kind, String placementId, String detail) {
        events.add(new Event(System.currentTimeMillis(), kind, placementId, detail));
        while (events.size() > EVENTS_KEPT) {
            events.remove(0);
        }
        changed();
    }

    private void changed() {
        firePropertyChange(PROPERTY_RUN, null, this);
    }
}
