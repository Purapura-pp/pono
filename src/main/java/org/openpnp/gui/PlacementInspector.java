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

package org.openpnp.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;

import org.openpnp.Translations;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.FeederDescriptions;
import org.openpnp.gui.tablemodel.PlacementsHolderPlacementsTableModel.PlacementStatus;
import org.openpnp.model.Abstract2DLocatable.Side;
import org.openpnp.model.Configuration;
import org.openpnp.model.Job;
import org.openpnp.model.JobRun;
import org.openpnp.model.Location;
import org.openpnp.model.Part;
import org.openpnp.model.Placement;
import org.openpnp.model.Placement.ErrorHandling;
import org.openpnp.model.PlacementsHolderLocation;
import org.openpnp.spi.Feeder;

/**
 * The properties of one placement, as the mockup draws them: its position with the camera's and
 * the nozzle's capture beside the angle, its part with the package and the feeder that carries
 * it, its options, what this run did with it, and its notes, folded.
 * <p>
 * A declarative form. It reads and writes where the table does: the board's definition when the
 * board is edited as a whole - a top-level board used once in the job - and the placement of this
 * instance otherwise, where only whether it is enabled and how its errors are handled belong to
 * the instance and the rest is shown as it is. The form it replaces wrote the instance's copy
 * and the board never heard of the change.
 */
public final class PlacementInspector {
    private PlacementInspector() {
    }

    /** What the form edits, read and written where the placements table reads and writes it. */
    public static final class Bean {
        private final Placement instance;
        private final Placement definition;
        private final boolean editDefinition;

        Bean(Placement instance, boolean editDefinition) {
            this.instance = instance;
            Object definition = instance.getDefinition();
            this.definition = definition instanceof Placement ? (Placement) definition : instance;
            this.editDefinition = editDefinition;
        }

        public Location getLocation() {
            return instance.getLocation();
        }

        public void setLocation(Location location) {
            definition.setLocation(location);
        }

        public Side getSide() {
            return instance.getSide();
        }

        public void setSide(Side side) {
            definition.setSide(side);
        }

        public Part getPart() {
            return instance.getPart();
        }

        public void setPart(Part part) {
            definition.setPart(part);
        }

        public Placement.Type getType() {
            return instance.getType();
        }

        public void setType(Placement.Type type) {
            definition.setType(type);
        }

        public String getComments() {
            return instance.getComments();
        }

        public void setComments(String comments) {
            definition.setComments(comments);
        }

        public boolean isEnabled() {
            return instance.isEnabled();
        }

        public void setEnabled(boolean enabled) {
            (editDefinition ? definition : instance).setEnabled(enabled);
        }

        public ErrorHandling getErrorHandling() {
            return instance.getErrorHandling();
        }

        public void setErrorHandling(ErrorHandling errorHandling) {
            (editDefinition ? definition : instance).setErrorHandling(errorHandling);
        }

        /** Where it is, for a board that is not edited here. */
        public String getPositionText() {
            Location l = instance.getLocation();
            return String.format(java.util.Locale.ROOT, "X %.3f \u00b7 Y %.3f \u00b7 %.1f\u00b0", l.getX(), l.getY(), //$NON-NLS-1$
                    l.getRotation());
        }
    }

    /** A built form, and what keeps what the run did with the placement current in it. */
    public static final class Built {
        private final FormWizard wizard;
        private final Runnable refreshRun;

        Built(FormWizard wizard, Runnable refreshRun) {
            this.wizard = wizard;
            this.refreshRun = refreshRun;
        }

        public FormWizard getWizard() {
            return wizard;
        }

        /** Called when the run changes. */
        public void refreshRun() {
            refreshRun.run();
        }
    }

    public static Built build(Configuration configuration, JobPlacementsPanel owner,
            PlacementsHolderLocation<?> location, Placement placement, boolean editDefinition) {
        Bean bean = new Bean(placement, editDefinition);
        JLabel packageLabel = Ui.t2(""); //$NON-NLS-1$
        Chip feederChip = new Chip("", Chip.Tone.Neutral, Chip.Shape.Status).withHeight(18); //$NON-NLS-1$
        JLabel feederNote = Ui.t2(""); //$NON-NLS-1$
        JPanel feederRow = Forms.row(feederChip, feederNote);
        Chip runChip = new Chip("", Chip.Tone.Neutral, Chip.Shape.Status); //$NON-NLS-1$
        JLabel alignment = Ui.mono("", 12f); //$NON-NLS-1$
        JLabel duration = Ui.mono("", 12f); //$NON-NLS-1$

        Form.Builder form = Form.of(bean).named(placement.getId()).ownedByJob();
        form.section("PlacementInspector.Position", "move") //$NON-NLS-1$ //$NON-NLS-2$
                .note(String.format(Translations.getString("PlacementInspector.BoardCoordinates"), //$NON-NLS-1$
                        configuration.getSystemUnits().getShortName()));
        if (editDefinition) {
            form.location("location", "PlacementInspector.Coordinates", false).planar(); //$NON-NLS-1$ //$NON-NLS-2$
            form.segmented("side", "PlacementInspector.Side", List.of(Side.Top, Side.Bottom)); //$NON-NLS-1$ //$NON-NLS-2$
        }
        else {
            form.readOnly("positionText", "PlacementInspector.Coordinates"); //$NON-NLS-1$ //$NON-NLS-2$
            form.readOnly("side", "PlacementInspector.Side"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        form.section("PlacementInspector.PartSection", "parts"); //$NON-NLS-1$ //$NON-NLS-2$
        if (editDefinition) {
            List<Part> parts = new ArrayList<>(configuration.getParts());
            parts.sort(Comparator.comparing(Part::getId, String.CASE_INSENSITIVE_ORDER));
            form.choice("part", "PlacementInspector.Part", parts, Part::getName); //$NON-NLS-1$ //$NON-NLS-2$
        }
        else {
            form.readOnly("part", "PlacementInspector.Part"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        form.custom("PlacementInspector.Package", packageLabel); //$NON-NLS-1$
        form.custom("PlacementInspector.Feeder", feederRow); //$NON-NLS-1$
        form.section("PlacementInspector.Options", "gear"); //$NON-NLS-1$ //$NON-NLS-2$
        if (editDefinition) {
            form.choice("type", "PlacementInspector.Type", Placement.Type.class); //$NON-NLS-1$ //$NON-NLS-2$
        }
        else {
            form.readOnly("type", "PlacementInspector.Type"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        form.choice("errorHandling", "PlacementInspector.ErrorHandling", ErrorHandling.class); //$NON-NLS-1$ //$NON-NLS-2$
        form.toggle("enabled", "PlacementInspector.Enabled", "PlacementInspector.EnabledNote"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        form.section("PlacementInspector.ThisRun", "clock").note("PlacementInspector.Live"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        form.custom("PlacementInspector.Status", Forms.row(runChip)); //$NON-NLS-1$
        form.custom("PlacementInspector.Alignment", alignment); //$NON-NLS-1$
        form.custom("PlacementInspector.Duration", duration); //$NON-NLS-1$
        String comments = placement.getComments();
        form.section("PlacementInspector.Notes", "info").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .note(comments == null || comments.isBlank() ? "" //$NON-NLS-1$
                        : Translations.getString("PlacementInspector.NotesOne")); //$NON-NLS-1$
        if (editDefinition) {
            form.text("comments", "PlacementInspector.Comments"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        else {
            form.readOnly("comments", "PlacementInspector.Comments"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        // The package and the feeder follow the part as it is chosen, before it is applied.
        form.onChange(wizard -> {
            Object chosen = editDefinition ? wizard.value("part") : bean.getPart(); //$NON-NLS-1$
            describePart(configuration, chosen instanceof Part ? (Part) chosen : null, packageLabel,
                    feederChip, feederNote);
        });
        Runnable refreshRun = () -> describeRun(owner, location, placement, runChip, alignment, duration);
        FormWizard wizard = form.build();
        describePart(configuration, placement.getPart(), packageLabel, feederChip, feederNote);
        refreshRun.run();
        return new Built(wizard, refreshRun);
    }

    /** The package with its height, and the feeder that carries the part with what it has left. */
    static void describePart(Configuration configuration, Part part, JLabel packageLabel, Chip feederChip,
            JLabel feederNote) {
        if (part == null || part.getPackage() == null) {
            packageLabel.setText("\u2014"); //$NON-NLS-1$
        }
        else {
            packageLabel.setText(part.getPackage().getId() + (part.isPartHeightUnknown() ? "" //$NON-NLS-1$
                    : "  " + String.format(Translations.getString("PlacementInspector.PackageHeight"), //$NON-NLS-1$ //$NON-NLS-2$
                            FeederDescriptions.length(part.getHeight()))));
        }
        Feeder found = null;
        if (part != null) {
            for (Feeder feeder : configuration.getMachine().getFeeders()) {
                if (feeder.isEnabled() && feeder.getPart() == part) {
                    found = feeder;
                    break;
                }
            }
        }
        feederNote.setBorder(new javax.swing.border.EmptyBorder(0, 8, 0, 0));
        if (part == null) {
            feederChip.setText(Translations.getString("PlacementInspector.Feeder.NoPart")); //$NON-NLS-1$
            feederChip.setTone(Chip.Tone.Neutral);
            feederNote.setText(""); //$NON-NLS-1$
        }
        else if (found == null) {
            feederChip.setText(Translations.getString("PlacementInspector.Feeder.None")); //$NON-NLS-1$
            feederChip.setTone(Chip.Tone.Err);
            feederNote.setText(""); //$NON-NLS-1$
        }
        else {
            feederChip.setText(found.getName());
            feederChip.setTone(Chip.Tone.Ok);
            feederNote.setText(FeederDescriptions.summary(found));
        }
    }

    /** What the current run did with the placement: its status, its alignment and how long it took. */
    static void describeRun(JobPlacementsPanel owner, PlacementsHolderLocation<?> location, Placement placement,
            Chip runChip, JLabel alignment, JLabel duration) {
        Job job = owner.getJobPanel().getJob();
        JobRun.PlacementRun run = job == null ? null : job.getRun().get(JobRun.key(location, placement.getId()));
        boolean placed = job != null && job.retrievePlacedStatus(location, placement.getId());
        PlacementStatus status = new PlacementStatus(placement.getType(), null, run, placed);
        JobRun.State state = status.getState();
        if (state == null) {
            runChip.setText(Translations.getString("PlacementInspector.NotPlaced")); //$NON-NLS-1$
            runChip.setTone(Chip.Tone.Neutral);
        }
        else {
            runChip.setText(state == JobRun.State.Placing
                    ? String.format(Translations.getString("PlacementInspector.PlacingOn"), run.getNozzle()) //$NON-NLS-1$
                    : status.getText());
            runChip.setTone(tone(state));
        }
        Location offsets = run == null ? null : run.getAlignment();
        alignment.setText(offsets == null ? "\u2014" //$NON-NLS-1$
                : String.format(java.util.Locale.ROOT, "\u0394x %+.3f  \u0394y %+.3f  \u0394\u03b8 %+.1f\u00b0", //$NON-NLS-1$
                        offsets.getX(), offsets.getY(), offsets.getRotation()));
        duration.setText(run == null || run.getDurationMillis() <= 0 ? "\u2014" //$NON-NLS-1$
                : String.format(java.util.Locale.ROOT, "%.2f s", run.getDurationMillis() / 1000.0)); //$NON-NLS-1$
    }

    private static Chip.Tone tone(JobRun.State state) {
        switch (state) {
            case Placed:
                return Chip.Tone.Ok;
            case Placing:
                return Chip.Tone.Run;
            case WaitingForFeeder:
                return Chip.Tone.Warn;
            case Error:
                return Chip.Tone.Err;
            default:
                return Chip.Tone.Skip;
        }
    }

    /** The heading for the properties column: the placement's id over where it sits. */
    public static String subtitle(PlacementsHolderLocation<?> location) {
        if (location == null || location.getPlacementsHolder() == null) {
            return Translations.getString("JobPlacementsPanel.Border.title"); //$NON-NLS-1$
        }
        return Translations.getString("JobPlacementsPanel.Border.title") + " \u00b7 " //$NON-NLS-1$ //$NON-NLS-2$
                + location.getPlacementsHolder().getName() + " \u00b7 " + location.getGlobalSide(); //$NON-NLS-1$
    }
}
