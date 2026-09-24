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
import java.util.Date;
import java.util.List;

import org.openpnp.machine.reference.solutions.MachineDiagnostics;
import org.openpnp.model.CalibrationStep;
import org.openpnp.model.Solutions;

/**
 * A calibration step that measured and changed the machine, and waits to be applied or
 * discarded. What it changed is in effect, and saved, from the moment it was measured; discarding
 * takes it out again the way each change knows how: the issues it accepted are reopened, which
 * puts back what they found, and a frame compensation is taken out.
 */
public final class Pending {
    private final String key;
    private final CalibrationStep kind;
    private final Object subject;
    private final String subjectName;
    private final List<SettingsDiff.Difference> differences;
    private final List<Solutions.Issue> accepted;
    private final MachineDiagnostics.CompensationOutcome compensation;
    private final String message;
    private final Date when;
    private final String beforeXml;

    /**
     * @param beforeXml machine.xml as it was before the step first measured, which a step measured
     *            again before it was applied keeps.
     */
    public Pending(String key, CalibrationStep kind, Object subject, String subjectName,
            List<SettingsDiff.Difference> differences, List<Solutions.Issue> accepted,
            MachineDiagnostics.CompensationOutcome compensation, String message, Date when, String beforeXml) {
        this.beforeXml = beforeXml;
        this.key = key;
        this.kind = kind;
        this.subject = subject;
        this.subjectName = subjectName;
        this.differences = new ArrayList<>(differences);
        this.accepted = new ArrayList<>(accepted);
        this.compensation = compensation;
        this.message = message;
        this.when = when;
    }

    /** The step, as {@link org.openpnp.machine.reference.calibration.CalibrationPlan.Step#getKey()}. */
    public String getKey() {
        return key;
    }

    public CalibrationStep getKind() {
        return kind;
    }

    public Object getSubject() {
        return subject;
    }

    public String getSubjectName() {
        return subjectName;
    }

    /** What changed in machine.xml, setting by setting. */
    public List<SettingsDiff.Difference> getDifferences() {
        return Collections.unmodifiableList(differences);
    }

    /** The issues the step accepted, in the order it accepted them. */
    public List<Solutions.Issue> getAccepted() {
        return Collections.unmodifiableList(accepted);
    }

    /** The frame compensation it kept, or null. */
    public MachineDiagnostics.CompensationOutcome getCompensation() {
        return compensation;
    }

    /** What the run said about the step, or empty. */
    public String getMessage() {
        return message == null ? "" : message; //$NON-NLS-1$
    }

    public Date getWhen() {
        return when;
    }

    /** machine.xml before the step first measured. */
    public String getBeforeXml() {
        return beforeXml;
    }

    /**
     * Whether discarding can take it all out again: every issue it accepted can be undone, and
     * a compensation it kept can be taken out.
     */
    public boolean canBeDiscarded() {
        for (Solutions.Issue issue : accepted) {
            if (!issue.canBeUndone()) {
                return false;
            }
        }
        return compensation == null || compensation.canBeUndone();
    }

    /** What was measured, in the words of the issues it accepted and of the run. */
    public List<String> getResults() {
        List<String> results = new ArrayList<>();
        for (Solutions.Issue issue : accepted) {
            String text = Html.plain(issue.getExtendedDescription());
            if (!text.isEmpty() && !results.contains(text)) {
                results.add(text);
            }
        }
        if (compensation != null && compensation.message != null && !compensation.message.isEmpty()) {
            results.add(org.openpnp.Translations.translateText(compensation.message));
        }
        if (!getMessage().isEmpty()) {
            results.add(org.openpnp.Translations.translateText(getMessage()));
        }
        return results;
    }
}
