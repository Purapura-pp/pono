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

package org.openpnp.gui.operator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.openpnp.model.BoardLocation;
import org.openpnp.model.Job;
import org.openpnp.model.JobRun;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Placement;
import org.openpnp.model.PlacementsHolderLocation;
import org.openpnp.spi.Feeder;
import org.openpnp.spi.Machine;

/**
 * What production mode shows, worked out from the job, its run and the machine at one moment:
 * the progress, the boards, the placement being placed, the feeders that need a hand, the latest
 * events. Nothing here touches Swing, so that it can be worked out and checked anywhere.
 */
public final class OperatorSummary {
    /** How far a board of the job has got. */
    public enum BoardState {
        Done, Running, Pending
    }

    /** A feeder the operator has to see to. */
    public static final class Attention {
        public final Feeder feeder;
        /** Empty rather than just low. */
        public final boolean empty;
        public final String partId;
        /** Parts left, or null when the feeder does not count them. */
        public final Integer left;
        /** The placements waiting for it, by their ids. */
        public final List<String> waiting;

        Attention(Feeder feeder, boolean empty, String partId, Integer left, List<String> waiting) {
            this.feeder = feeder;
            this.empty = empty;
            this.partId = partId;
            this.left = left;
            this.waiting = waiting;
        }
    }

    /** The placement being placed now. */
    public static final class Current {
        public final String placementId;
        public final String partId;
        public final String nozzle;
        public final String feeder;
        /** How far bottom vision found the part off the nozzle, in millimetres; NaN before it looked. */
        public final double alignmentMm;

        Current(String placementId, String partId, String nozzle, String feeder, double alignmentMm) {
            this.placementId = placementId;
            this.partId = partId;
            this.nozzle = nozzle;
            this.feeder = feeder;
            this.alignmentMm = alignmentMm;
        }
    }

    public final int placed;
    public final int total;
    public final List<BoardState> boards;
    /** The board being worked on, counted from 1, or 0 when none is. */
    public final int currentBoard;
    public final double cycleSeconds;
    public final double remainingSeconds;
    /** Since the run started, or 0 before it has. */
    public final long elapsedMillis;
    public final int errors;
    public final int skipped;
    public final Current current;
    public final List<Attention> attention;
    /** The latest first. */
    public final List<JobRun.Event> events;

    private OperatorSummary(int placed, int total, List<BoardState> boards, int currentBoard,
            double cycleSeconds, double remainingSeconds, long elapsedMillis, int errors, int skipped,
            Current current, List<Attention> attention, List<JobRun.Event> events) {
        this.placed = placed;
        this.total = total;
        this.boards = boards;
        this.currentBoard = currentBoard;
        this.cycleSeconds = cycleSeconds;
        this.remainingSeconds = remainingSeconds;
        this.elapsedMillis = elapsedMillis;
        this.errors = errors;
        this.skipped = skipped;
        this.current = current;
        this.attention = attention;
        this.events = events;
    }

    /**
     * @param running Whether the job is running now: the elapsed time and the time left are only
     *        said while it is.
     * @param eventsKept How many of the latest events to keep.
     */
    public static OperatorSummary of(Job job, Machine machine, boolean running, long now, int eventsKept) {
        JobRun run = job.getRun();
        int total = job.getTotalActivePlacements(job.getRootPanelLocation());
        int left = job.getActivePlacements(job.getRootPanelLocation());
        String currentKey = run.getCurrentKey();

        List<BoardState> boards = new ArrayList<>();
        int currentBoard = 0;
        BoardLocation currentLocation = null;
        for (BoardLocation board : job.getBoardLocations()) {
            if (!board.isEnabled() || job.getTotalActivePlacements(board) == 0) {
                continue;
            }
            boolean mine = currentKey != null && currentKey.startsWith(prefix(board));
            if (mine) {
                boards.add(BoardState.Running);
                currentBoard = boards.size();
                currentLocation = board;
            }
            else {
                boards.add(job.getActivePlacements(board) == 0 ? BoardState.Done : BoardState.Pending);
            }
        }

        Current current = null;
        JobRun.PlacementRun placing = run.getCurrent();
        if (placing != null) {
            Placement placement = currentLocation == null ? null : placement(currentLocation, placing.getPlacementId());
            Location alignment = placing.getAlignment();
            double alignmentMm = Double.NaN;
            if (alignment != null) {
                Location mm = alignment.convertToUnits(LengthUnit.Millimeters);
                alignmentMm = Math.hypot(mm.getX(), mm.getY());
            }
            current = new Current(placing.getPlacementId(),
                    placement == null || placement.getPart() == null ? null : placement.getPart().getId(),
                    placing.getNozzle(), placing.getFeeder(), alignmentMm);
        }

        List<JobRun.Event> all = run.getEvents();
        List<JobRun.Event> events = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && events.size() < eventsKept; i--) {
            events.add(all.get(i));
        }

        long started = run.getStartedMillis();
        return new OperatorSummary(total - left, total, Collections.unmodifiableList(boards), currentBoard,
                run.getCycleSeconds(), running ? run.getRemainingSeconds(left) : Double.NaN,
                running && started > 0 ? Math.max(0, now - started) : 0,
                run.count(JobRun.State.Error), run.count(JobRun.State.Skipped), current,
                attention(job, machine), Collections.unmodifiableList(events));
    }

    private static String prefix(PlacementsHolderLocation<?> location) {
        return location.getUniqueId() + PlacementsHolderLocation.ID_DELIMITTER;
    }

    private static Placement placement(BoardLocation board, String id) {
        for (Placement placement : board.getPlacementsHolder().getPlacements()) {
            if (placement.getId().equals(id)) {
                return placement;
            }
        }
        return null;
    }

    /** The feeders of the job's parts that are empty, then those that are low. */
    private static List<Attention> attention(Job job, Machine machine) {
        Set<String> parts = new HashSet<>();
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
        List<Attention> empty = new ArrayList<>();
        List<Attention> low = new ArrayList<>();
        for (Feeder feeder : machine.getFeeders()) {
            if (feeder.getPart() == null || !parts.contains(feeder.getPart().getId())) {
                continue;
            }
            if (feeder.isEmpty()) {
                empty.add(new Attention(feeder, true, feeder.getPart().getId(), feeder.getPartsLeft(),
                        job.getRun().waitingFor(feeder.getName())));
            }
            else if (feeder.isLow()) {
                low.add(new Attention(feeder, false, feeder.getPart().getId(), feeder.getPartsLeft(),
                        job.getRun().waitingFor(feeder.getName())));
            }
        }
        empty.addAll(low);
        return Collections.unmodifiableList(empty);
    }
}
