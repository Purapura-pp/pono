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

package org.openpnp.gui.calibration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.openpnp.Translations;
import org.openpnp.gui.machinesettings.SetupChecks;
import org.openpnp.machine.reference.calibration.CalibrationPlan;
import org.openpnp.machine.reference.calibration.SettingChange;
import org.openpnp.model.CalibrationStep;
import org.openpnp.model.Solutions;
import org.openpnp.spi.Axis;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Head;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.NozzleTip;

/**
 * One row of the calibration page: the same thing to do about one or more elements of the
 * machine, which opens into a row for each. The six nozzle tips whose misdetections are to be
 * reduced are one suggestion; the backlash of x and y is one measurement.
 */
public final class CalibrationItem {
    /** The groups of the page, in the order it shows them. */
    public enum Kind {
        /** Measured: what it changed is in effect, to be applied or discarded. */
        Pending,
        /** Accepting writes a value into a setting, nothing more: it can be applied as it stands. */
        Suggestion,
        /** A step that has to measure, move the machine, or have someone at it before it can say. */
        Measure,
        /** Not calibration: what the machine settings page is for, and issues of the setup. */
        Hint,
        /** Nothing to do. */
        Done,
        /** Decided against, on this page or the issues page. */
        Dismissed;

        public String getName() {
            return Translations.getString("CalibrationPanel.Group." + name()); //$NON-NLS-1$
        }
    }

    /** One element's share of an item. */
    public static final class Part {
        private final String subject;
        private final Object element;
        private final Solutions.Issue issue;
        private final CalibrationPlan.Step step;
        private final Pending pending;

        Part(String subject, Object element, Solutions.Issue issue, CalibrationPlan.Step step, Pending pending) {
            this.subject = subject;
            this.element = element;
            this.issue = issue;
            this.step = step;
            this.pending = pending;
        }

        /** What it is about, as the page names it. */
        public String getSubject() {
            return subject;
        }

        /** The axis, camera, nozzle or nozzle tip, or null. */
        public Object getElement() {
            return element;
        }

        /** The issue that carries it out, or null for a step with none or a check. */
        public Solutions.Issue getIssue() {
            return issue;
        }

        public CalibrationPlan.Step getStep() {
            return step;
        }

        public Pending getPending() {
            return pending;
        }

        /** What accepting the issue writes, or null. */
        public SettingChange getChange() {
            return SettingChange.of(issue);
        }
    }

    private final Kind kind;
    private final String key;
    private final String title;
    private final CalibrationStep step;
    private final SetupChecks.Check check;
    private final List<Part> parts = new ArrayList<>();
    private boolean waiting;

    CalibrationItem(Kind kind, String key, String title, CalibrationStep step, SetupChecks.Check check) {
        this.kind = kind;
        this.key = key;
        this.title = title;
        this.step = step;
        this.check = check;
    }

    public Kind getKind() {
        return kind;
    }

    /** Identifies the item across collections, so that what was selected or open stays so. */
    public String getKey() {
        return key;
    }

    public String getTitle() {
        return title;
    }

    /** The kind of calibration step, or null for an item that is not one. */
    public CalibrationStep getStep() {
        return step;
    }

    /** The machine settings check it is, or null. */
    public SetupChecks.Check getCheck() {
        return check;
    }

    public List<Part> getParts() {
        return Collections.unmodifiableList(parts);
    }

    void add(Part part) {
        parts.add(part);
    }

    /** A measurement that waits for a step before it. */
    public boolean isWaiting() {
        return waiting;
    }

    void setWaiting(boolean waiting) {
        this.waiting = waiting;
    }

    public List<Solutions.Issue> getIssues() {
        List<Solutions.Issue> issues = new ArrayList<>();
        for (Part part : parts) {
            if (part.issue != null && !issues.contains(part.issue)) {
                issues.add(part.issue);
            }
        }
        return issues;
    }

    public List<CalibrationPlan.Step> getSteps() {
        List<CalibrationPlan.Step> steps = new ArrayList<>();
        for (Part part : parts) {
            if (part.step != null && !steps.contains(part.step)) {
                steps.add(part.step);
            }
        }
        return steps;
    }

    public List<Pending> getPending() {
        List<Pending> pending = new ArrayList<>();
        for (Part part : parts) {
            if (part.pending != null) {
                pending.add(part.pending);
            }
        }
        return pending;
    }

    public boolean isNeedsPerson() {
        for (CalibrationPlan.Step s : getSteps()) {
            if (s.isNeedsPerson()) {
                return true;
            }
        }
        return false;
    }

    public boolean isMovesMachine() {
        for (CalibrationPlan.Step s : getSteps()) {
            if (s.isMovesMachine()) {
                return true;
            }
        }
        return false;
    }

    /** The steps not done that its steps wait for. */
    public List<CalibrationPlan.Step> getUnsettledPrerequisites() {
        Set<CalibrationPlan.Step> unsettled = new LinkedHashSet<>();
        for (CalibrationPlan.Step s : getSteps()) {
            unsettled.addAll(s.getUnsettledPrerequisites());
        }
        unsettled.removeAll(getSteps());
        return new ArrayList<>(unsettled);
    }

    /** Whether it can be dismissed: every part has an issue open or measured. */
    public boolean canBeDismissed() {
        if (parts.isEmpty() || check != null) {
            return false;
        }
        for (Part part : parts) {
            if (part.issue == null) {
                return false;
            }
        }
        return true;
    }

    /** "N045", or "6 个吸嘴头" for more than one. */
    public String getSubjects() {
        if (check != null) {
            return check.subject();
        }
        if (parts.size() == 1) {
            return parts.get(0).subject;
        }
        return count(parts.size(), parts.isEmpty() ? null : parts.get(0).element);
    }

    /** "6 个吸嘴头", "2 根轴", "3 项". */
    public static String count(int n, Object element) {
        String noun = element instanceof NozzleTip ? "NozzleTip" //$NON-NLS-1$
                : element instanceof Nozzle ? "Nozzle" //$NON-NLS-1$
                : element instanceof Camera ? "Camera" //$NON-NLS-1$
                : element instanceof Axis ? "Axis" //$NON-NLS-1$
                : element instanceof Head ? "Head" : "Other"; //$NON-NLS-1$ //$NON-NLS-2$
        return String.format(Translations.getString("CalibrationPanel.Count." + noun), n); //$NON-NLS-1$
    }

    /** One value for all its parts' changes, or null when they differ. */
    public Object commonValue(boolean proposed) {
        Object common = null;
        boolean first = true;
        for (Part part : parts) {
            SettingChange change = part.getChange();
            Object value = change == null ? null : proposed ? change.getProposedValue() : change.getCurrentValue();
            if (first) {
                common = value;
                first = false;
            }
            else if (!Objects.equals(String.valueOf(common), String.valueOf(value))) {
                return Differs.VALUE;
            }
        }
        return common;
    }

    /** What {@link #commonValue} gives when the parts do not agree. */
    public enum Differs {
        VALUE;

        @Override
        public String toString() {
            return Translations.getString("CalibrationPanel.Value.Differs"); //$NON-NLS-1$
        }
    }

    @Override
    public String toString() {
        return kind + " " + title + " \u00b7 " + getSubjects(); //$NON-NLS-1$ //$NON-NLS-2$
    }
}
