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
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.openpnp.Translations;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.FeederDescriptions;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.AbstractVisionSettings;
import org.openpnp.model.Board;
import org.openpnp.model.BottomVisionSettings;
import org.openpnp.model.Configuration;
import org.openpnp.model.FiducialVisionSettings;
import org.openpnp.model.Length;
import org.openpnp.model.Package;
import org.openpnp.model.Part;
import org.openpnp.model.Placement;
import org.openpnp.spi.Feeder;

/**
 * A part's settings as mockup 07 has them: what it is and its package, how it is placed, the
 * vision it is aligned with, the feeders that carry it and the placements that use it. The
 * part's own settings sheet was one field, the pick retry count, in a titled border.
 */
public final class PartForm {
    private PartForm() {
    }

    /** What the form edits: the part, with its speed as the percentage the table shows. */
    public static class Bean extends AbstractModelObject {
        private final Part part;

        Bean(Part part) {
            this.part = part;
        }

        public String getId() {
            return part.getId();
        }

        public String getName() {
            return part.getName();
        }

        public void setName(String name) {
            part.setName(name);
        }

        public Package getPackage() {
            return part.getPackage();
        }

        public void setPackage(Package packag) {
            part.setPackage(packag);
        }

        public Length getHeight() {
            return part.getHeight();
        }

        public void setHeight(Length height) {
            part.setHeight(height);
        }

        public Length getThroughBoardDepth() {
            return part.getThroughBoardDepth();
        }

        public void setThroughBoardDepth(Length depth) {
            part.setThroughBoardDepth(depth);
        }

        public int getSpeedPercent() {
            return (int) Math.round(part.getSpeed() * 100);
        }

        public void setSpeedPercent(int percent) {
            part.setSpeed(Math.max(1, Math.min(100, percent)) / 100.0);
        }

        public int getPickRetryCount() {
            return part.getPickRetryCount();
        }

        public void setPickRetryCount(int count) {
            part.setPickRetryCount(count);
        }

        public BottomVisionSettings getBottomVisionSettings() {
            return part.getBottomVisionSettings();
        }

        public void setBottomVisionSettings(BottomVisionSettings settings) {
            part.setBottomVisionSettings(settings);
        }

        public FiducialVisionSettings getFiducialVisionSettings() {
            return part.getFiducialVisionSettings();
        }

        public void setFiducialVisionSettings(FiducialVisionSettings settings) {
            part.setFiducialVisionSettings(settings);
        }
    }

    public static FormWizard build(Configuration configuration, Part part) {
        List<Package> packages = new ArrayList<>(configuration.getPackages());
        packages.sort(Comparator.comparing(Package::getId, String.CASE_INSENSITIVE_ORDER));
        List<BottomVisionSettings> bottom = new ArrayList<>();
        bottom.add(null);
        List<FiducialVisionSettings> fiducial = new ArrayList<>();
        fiducial.add(null);
        for (AbstractVisionSettings settings : configuration.getVisionSettings()) {
            if (settings instanceof BottomVisionSettings) {
                bottom.add((BottomVisionSettings) settings);
            }
            else if (settings instanceof FiducialVisionSettings) {
                fiducial.add((FiducialVisionSettings) settings);
            }
        }
        List<Feeder> feeders = new ArrayList<>();
        for (Feeder feeder : configuration.getMachine().getFeeders()) {
            if (feeder.getPart() == part) {
                feeders.add(feeder);
            }
        }
        Set<String> uses = new LinkedHashSet<>();
        Set<String> boards = new LinkedHashSet<>();
        for (Board board : configuration.getBoards()) {
            for (Placement placement : board.getPlacements()) {
                if (placement.getPart() == part) {
                    uses.add(placement.getId());
                    boards.add(board.getName());
                }
            }
        }

        Form.Builder form = Form.of(new Bean(part)).named(part.getId())
                .section("PartForm.Basics", "info") //$NON-NLS-1$ //$NON-NLS-2$
                .readOnly("id", "PartForm.Id") //$NON-NLS-1$ //$NON-NLS-2$
                .text("name", "PartForm.Name") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("package", "PartForm.Package", packages, PartForm::describe) //$NON-NLS-1$ //$NON-NLS-2$
                .length("height", "PartForm.Height").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .length("throughBoardDepth", "PartForm.ThroughBoardDepth").width(120) //$NON-NLS-1$ //$NON-NLS-2$

                .section("PartForm.Placing", "move") //$NON-NLS-1$ //$NON-NLS-2$
                .integer("speedPercent", "PartForm.Speed").width(90).unit("%") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .presets(Arrays.asList(25, 50, 75, 100))
                .validate(v -> between(v, 1, 100), "PartForm.Speed.Range") //$NON-NLS-1$
                .integer("pickRetryCount", "PartForm.PickRetryCount").width(90) //$NON-NLS-1$ //$NON-NLS-2$
                .note("PartForm.PickRetryCount.Note") //$NON-NLS-1$

                .section("PartForm.Vision", "eye") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("bottomVisionSettings", "PartForm.BottomVision", bottom, //$NON-NLS-1$ //$NON-NLS-2$
                        v -> v == null ? Translations.getString("PartForm.FromPackage") : null) //$NON-NLS-1$
                .choice("fiducialVisionSettings", "PartForm.FiducialVision", fiducial, //$NON-NLS-1$ //$NON-NLS-2$
                        v -> v == null ? Translations.getString("PartForm.FromPackage") : null); //$NON-NLS-1$

        form.section("PartForm.Feeders", "feeder") //$NON-NLS-1$ //$NON-NLS-2$
                .note(String.format(Translations.getString("PartForm.Count"), feeders.size())) //$NON-NLS-1$
                .custom("", feeders(feeders)); //$NON-NLS-1$
        form.section("PartForm.UsedIn", "board") //$NON-NLS-1$ //$NON-NLS-2$
                .note(String.format(Translations.getString("PartForm.Places"), uses.size())) //$NON-NLS-1$
                .custom("", uses(uses, boards)); //$NON-NLS-1$
        return form.build();
    }

    /** "1.6 × 0.8 mm · 2 焊盘" after the package's name. */
    static String describe(Package packag) {
        if (packag == null || packag.getFootprint() == null) {
            return null;
        }
        org.openpnp.model.Footprint footprint = packag.getFootprint();
        return String.format(Translations.getString("PartForm.Package.Note"), //$NON-NLS-1$
                footprint.getBodyWidth(), footprint.getBodyHeight(), footprint.getPads().size());
    }

    private static boolean between(Object value, int low, int high) {
        try {
            int v = Integer.parseInt(String.valueOf(value).trim());
            return v >= low && v <= high;
        }
        catch (NumberFormatException e) {
            return false;
        }
    }

    /** Each feeder as a capsule with what it holds, and the way to it on the feeders page. */
    private static JComponent feeders(List<Feeder> feeders) {
        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        if (feeders.isEmpty()) {
            list.add(Ui.muted(Translations.getString("PartForm.NoFeeder"))); //$NON-NLS-1$
        }
        for (Feeder feeder : feeders) {
            JPanel row = new JPanel();
            row.setOpaque(false);
            row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
            row.setAlignmentX(0);
            row.add(new Chip(feeder.getName(), feeder.isEnabled() ? Chip.Tone.Ok : Chip.Tone.Skip, Chip.Shape.Status));
            row.add(javax.swing.Box.createHorizontalStrut(8));
            row.add(Ui.t2(FeederDescriptions.summary(feeder)));
            row.add(javax.swing.Box.createHorizontalGlue());
            JButton go = Ui.button(Translations.getString("PartForm.GoToFeeder"), null, Ui.Size.Xs, Ui.Variant.Ghost); //$NON-NLS-1$
            go.addActionListener(e -> {
                MainFrame frame = MainFrame.get();
                if (frame != null) {
                    frame.showTab(frame.getFeedersTab());
                    frame.getFeedersTab().selectFeederInTable(feeder);
                }
            });
            row.add(go);
            list.add(row);
        }
        return list;
    }

    /** The placements' references as tags, and the boards they are on. */
    private static JComponent uses(Set<String> uses, Set<String> boards) {
        JPanel tags = new JPanel(new org.openpnp.gui.support.WrapLayout(java.awt.FlowLayout.LEFT, 4, 4));
        tags.setOpaque(false);
        if (uses.isEmpty()) {
            tags.add(Ui.muted(Translations.getString("PartForm.Unused"))); //$NON-NLS-1$
            return tags;
        }
        int shown = 0;
        for (String id : uses) {
            if (shown++ == 24) {
                tags.add(Ui.muted(String.format(Translations.getString("PartForm.More"), uses.size() - 24))); //$NON-NLS-1$
                break;
            }
            tags.add(new Chip(id, Chip.Tone.Neutral, Chip.Shape.Status));
        }
        tags.add(Ui.t2(String.join(" \u00b7 ", boards))); //$NON-NLS-1$
        return tags;
    }
}
