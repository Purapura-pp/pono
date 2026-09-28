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

package org.openpnp.gui.machinesettings;

import java.util.ArrayList;
import java.util.List;

import org.openpnp.machine.reference.calibration.SettingChange;
import org.openpnp.model.Solutions;
import org.openpnp.spi.Axis;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Driver;
import org.openpnp.spi.Head;
import org.openpnp.spi.MotionPlanner;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.NozzleTip;

/**
 * The issues of Issues and Solutions that are about the machine's own settings rather than its
 * calibration: a G-code command the controller should be sent, an axis without its letter. The
 * machine settings page shows each in the topic its subject is set in; the calibration page
 * leaves them out, as it does the setup checks.
 */
public final class SetupIssues {
    private SetupIssues() {
    }

    /**
     * Whether an issue belongs to the machine settings page: no calibration step carries it out, it
     * is more than information, no setup check already says it, and accepting it does more than
     * write one value, which the calibration page offers as a suggestion.
     */
    public static boolean isSetupIssue(Solutions.Issue issue) {
        return issue.getCalibrationStep() == null
                && issue.getSeverity().ordinal() > Solutions.Severity.Information.ordinal()
                && !SetupChecks.covers(issue)
                && !(issue.canBeAccepted() && SettingChange.of(issue) != null);
    }

    /** The topic an issue's subject is set in; the overview for what no topic sets. */
    public static String topicOf(Solutions.Issue issue) {
        Object subject = issue.getSubject();
        if (subject instanceof Driver) {
            return MachineSettingsPanel.CONNECTION;
        }
        if (subject instanceof Camera) {
            return MachineSettingsPanel.CAMERAS;
        }
        if (subject instanceof Nozzle || subject instanceof NozzleTip || subject instanceof Head) {
            return MachineSettingsPanel.NOZZLES;
        }
        if (subject instanceof Axis || subject instanceof MotionPlanner) {
            return MachineSettingsPanel.MOTION;
        }
        return MachineSettingsPanel.OVERVIEW;
    }

    /** The open setup issues among those the last search found, of a topic, or of all for null. */
    public static List<Solutions.Issue> open(List<Solutions.Issue> issues, String topic) {
        return inState(issues, topic, Solutions.State.Open);
    }

    /** The setup issues dismissed, of a topic: they can be opened again where they are shown. */
    public static List<Solutions.Issue> dismissed(List<Solutions.Issue> issues, String topic) {
        return inState(issues, topic, Solutions.State.Dismissed);
    }

    private static List<Solutions.Issue> inState(List<Solutions.Issue> issues, String topic, Solutions.State state) {
        List<Solutions.Issue> found = new ArrayList<>();
        for (Solutions.Issue issue : issues) {
            if (issue.getState() == state && isSetupIssue(issue) && (topic == null || topic.equals(topicOf(issue)))) {
                found.add(issue);
            }
        }
        return found;
    }
}
