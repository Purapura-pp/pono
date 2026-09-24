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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openpnp.Translations;
import org.openpnp.gui.calibration.CalibrationItem.Kind;
import org.openpnp.gui.calibration.CalibrationItem.Part;
import org.openpnp.gui.machinesettings.SetupChecks;
import org.openpnp.machine.reference.calibration.CalibrationPlan;
import org.openpnp.machine.reference.calibration.SettingChange;
import org.openpnp.model.Solutions;

/**
 * The calibration page's rows, from what a collection found.
 * <p>
 * An issue that says what it writes, and writes nothing else, is a suggestion however it was
 * found; the same suggestion about several elements is one row. A step that has to measure, move
 * the machine or have someone at it is a measurement, one row for the elements of a kind. What was
 * measured and not yet applied or discarded stands for its step. The machine settings checks, and
 * the issues of the setup that are neither, are hints; the issues the machine settings page shows
 * where they are fixed are left to it.
 */
public final class CalibrationItems {
    private CalibrationItems() {
    }

    /**
     * @param plan The plan, or null before the first collection.
     * @param others The issues of the whole search that no calibration step carries out.
     * @param checks What the machine settings page finds missing.
     * @param pending The steps measured and neither applied nor discarded yet.
     */
    public static List<CalibrationItem> of(CalibrationPlan plan, List<Solutions.Issue> others,
            SetupChecks checks, Collection<Pending> pending) {
        Map<String, CalibrationItem> items = new LinkedHashMap<>();
        Set<String> measured = new HashSet<>();
        for (Pending p : pending) {
            measured.add(p.getKey());
            item(items, Kind.Pending, "P|" + p.getKind(), p.getKind().getName(), p.getKind(), null) //$NON-NLS-1$
                    .add(new Part(p.getSubjectName(), p.getSubject(), null, null, p));
        }
        if (plan != null) {
            for (CalibrationPlan.Step step : plan.getSteps()) {
                if (measured.contains(step.getKey())) {
                    continue;
                }
                for (Solutions.Issue issue : step.getIssues()) {
                    if (SettingChange.of(issue) == null) {
                        continue;
                    }
                    if (issue.getState() == Solutions.State.Open && issue.canBeAccepted()) {
                        suggestion(items, issue, step);
                    }
                    else if (issue.getState() == Solutions.State.Dismissed) {
                        dismissed(items, issue, step);
                    }
                }
                steps(items, step);
            }
        }
        // The machine settings checks before the other hints: the machine does not work without them.
        if (checks != null) {
            for (SetupChecks.Check check : checks.all()) {
                CalibrationItem hint = item(items, Kind.Hint, "C|" + check.kind + "|" + check.text(), //$NON-NLS-1$ //$NON-NLS-2$
                        check.text(), null, check);
                for (Object subject : check.subjects) {
                    hint.add(new Part(CalibrationPlan.nameOf(subject), subject, null, null, null));
                }
            }
        }
        for (Solutions.Issue issue : others) {
            if (issue.getCalibrationStep() != null
                    || issue.getSeverity().ordinal() <= Solutions.Severity.Information.ordinal()
                    || SetupChecks.covers(issue)) {
                continue;
            }
            if (issue.getState() == Solutions.State.Open) {
                if (issue.canBeAccepted() && SettingChange.of(issue) != null) {
                    suggestion(items, issue, null);
                }
                else {
                    hint(items, issue);
                }
            }
            else if (issue.getState() == Solutions.State.Dismissed) {
                dismissed(items, issue, null);
            }
        }
        List<CalibrationItem> sorted = new ArrayList<>(items.values());
        sorted.sort(Comparator.comparingInt(i -> i.getKind().ordinal()));
        return sorted;
    }

    /** Where the step stands, as a row of its kind: done, dismissed, or to be measured. */
    private static void steps(Map<String, CalibrationItem> items, CalibrationPlan.Step step) {
        String kind = step.getKind().name();
        switch (step.getStatus()) {
            case Done:
                item(items, Kind.Done, "D|" + kind, step.getKind().getName(), step.getKind(), null) //$NON-NLS-1$
                        .add(part(step, null));
                return;
            case Dismissed:
                item(items, Kind.Dismissed, "X|" + kind, step.getKind().getName(), step.getKind(), null) //$NON-NLS-1$
                        .add(part(step, null));
                return;
            default:
                break;
        }
        Solutions.Issue action = null;
        boolean onlySuggestions = !step.getActions().isEmpty();
        for (Solutions.Issue issue : step.getActions()) {
            if (SettingChange.of(issue) == null) {
                onlySuggestions = false;
                if (action == null) {
                    action = issue;
                }
            }
        }
        if (onlySuggestions) {
            // Its suggestions stand for it.
            return;
        }
        boolean waiting = step.getStatus() == CalibrationPlan.Status.Waiting;
        CalibrationItem measure = item(items, Kind.Measure, "M|" + kind + (waiting ? "|waiting" : ""), //$NON-NLS-1$ //$NON-NLS-2$
                step.getKind().getName(), step.getKind(), null);
        measure.setWaiting(waiting);
        measure.add(part(step, action));
    }

    private static Part part(CalibrationPlan.Step step, Solutions.Issue issue) {
        return new Part(step.getSubjectName(), step.getSubject(), issue, step, null);
    }

    private static void suggestion(Map<String, CalibrationItem> items, Solutions.Issue issue,
            CalibrationPlan.Step step) {
        SettingChange change = SettingChange.of(issue);
        item(items, Kind.Suggestion, "S|" + typeOf(issue), Translations.translateText(change.getSettingName()), //$NON-NLS-1$
                step == null ? null : step.getKind(), null)
                .add(new Part(CalibrationPlan.nameOf(issue.getSubject()), issue.getSubject(), issue, step, null));
    }

    private static void dismissed(Map<String, CalibrationItem> items, Solutions.Issue issue,
            CalibrationPlan.Step step) {
        SettingChange change = SettingChange.of(issue);
        String title = change != null ? Translations.translateText(change.getSettingName()) : issue.getIssue();
        item(items, Kind.Dismissed, "Y|" + typeOf(issue), title, step == null ? null : step.getKind(), null) //$NON-NLS-1$
                .add(new Part(CalibrationPlan.nameOf(issue.getSubject()), issue.getSubject(), issue, step, null));
    }

    private static void hint(Map<String, CalibrationItem> items, Solutions.Issue issue) {
        String key = "H|" + typeOf(issue); //$NON-NLS-1$
        CalibrationItem existing = items.get(key);
        String title = issue.getIssue();
        if (existing != null && !existing.getTitle().equals(title)) {
            // The same advice about several elements, each named in its own wording: the advice
            // is what they share.
            CalibrationItem merged = new CalibrationItem(Kind.Hint, key, issue.getSolution(), null, null);
            for (Part part : existing.getParts()) {
                merged.add(part);
            }
            items.put(key, merged);
        }
        item(items, Kind.Hint, key, title, null, null)
                .add(new Part(CalibrationPlan.nameOf(issue.getSubject()), issue.getSubject(), issue, null, null));
    }

    /**
     * What makes issues the same advice: where they come from, and the solution as the source
     * wrote it, which does not name the element where the issue does.
     */
    private static String typeOf(Solutions.Issue issue) {
        return issue.getClass().getName() + "|" + issue.getUntranslatedSolution(); //$NON-NLS-1$
    }

    private static CalibrationItem item(Map<String, CalibrationItem> items, Kind kind, String key, String title,
            org.openpnp.model.CalibrationStep step, SetupChecks.Check check) {
        return items.computeIfAbsent(key, k -> new CalibrationItem(kind, k, title, step, check));
    }
}
