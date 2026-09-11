/*
 * Copyright (C) 2020 <mark@makr.zone>
 * inspired and based on work
 * Copyright (C) 2011 Jason von Nieda <jason@vonnieda.org>
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

import java.awt.Color;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.Action;
import javax.swing.Icon;

import org.apache.commons.codec.digest.DigestUtils;
import org.openpnp.Translations;
import org.openpnp.spi.Axis;
import org.openpnp.spi.Camera;
import org.openpnp.spi.ControllerAxis;
import org.openpnp.spi.Machine;
import org.openpnp.spi.PropertySheetHolder;
import org.openpnp.util.UiUtils;
import org.openpnp.util.VisionUtils;
import org.openpnp.util.XmlSerialize;
import org.pmw.tinylog.Logger;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;

/**
 * The machine's setup advice: the issues found on it, the solution offered for each, and the
 * milestone the user is working towards.
 * <p>
 * This used to be a Swing table model, which is why it sits in the model package but was shaped by
 * the view. The table lives in SolutionsTableModel now; what remains here reports through property
 * changes and leaves presentation, including which icon stands in for a subject that has none, to
 * whoever is showing it.
 */
public class Solutions {

    @ElementList(required = false)
    private Set<String> dismissedSolutions = new HashSet<>();

    @ElementList(required = false)
    private Set<String> solvedSolutions = new HashSet<>();

    @Attribute(required = false)
    private boolean showIndicator = true;

    private boolean showSolved;

    private boolean showDismissed;

    public enum Milestone implements Subject, Named {
        Welcome(Translations.getString("Solutions.Milestone.Welcome.name"), //$NON-NLS-1$
                "welcome",
                Translations.getString("Solutions.Milestone.Welcome.description")), //$NON-NLS-1$

        Connect(Translations.getString("Solutions.Milestone.Connect.name"), //$NON-NLS-1$
                "connect",
                Translations.getString("Solutions.Milestone.Connect.description")), //$NON-NLS-1$

        Basics(Translations.getString("Solutions.Milestone.Basics.name"), //$NON-NLS-1$
                "basics",
                Translations.getString("Solutions.Milestone.Basics.description")), //$NON-NLS-1$

        Kinematics(Translations.getString("Solutions.Milestone.Kinematics.name"), //$NON-NLS-1$
                "kinematics",
                Translations.getString("Solutions.Milestone.Kinematics.description")), //$NON-NLS-1$

        Vision(Translations.getString("Solutions.Milestone.Vision.name"), //$NON-NLS-1$
                "vision",
                Translations.getString("Solutions.Milestone.Vision.description")), //$NON-NLS-1$

        Calibration(Translations.getString("Solutions.Milestone.Calibration.name"), //$NON-NLS-1$
                "calibration",
                Translations.getString("Solutions.Milestone.Calibration.description")), //$NON-NLS-1$

        Production(Translations.getString("Solutions.Milestone.Production.name"), //$NON-NLS-1$
                "production",
                Translations.getString("Solutions.Milestone.Production.description")), //$NON-NLS-1$

        Advanced(Translations.getString("Solutions.Milestone.Advanced.name"), //$NON-NLS-1$
                "advanced",
                Translations.getString("Solutions.Milestone.Advanced.description")); //$NON-NLS-1$

        final private String name;
        final private String tag;
        final private String description;

        private Milestone(String name, String tag, String description) {
            this.name = name;
            this.tag = tag;
            this.description = description;
        }

        public Milestone getPrevious() {
            if (ordinal()-1 >= 0) {
                return values()[ordinal() - 1];
            }
            return null;
        }

        public Milestone getNext() {
            if (ordinal()+1 < values().length) {
                return values()[ordinal() + 1];
            }
            return null;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void setName(String name) {}

        public String getDescription() {
            return description;
        }

        public String getAnchor() {
            return "#"+tag+"-milestone";
        }
    }
    @Attribute(required = false)
    private Milestone targetMilestone;

    // Lacking multiple inheritance, we can't inherit from AbstractModelObject 
    protected final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(propertyName, listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(propertyName, listener);
    }

    public interface Subject {
        /**
         * Report any detected issue and proposed solution in the list. 
         * @param report
         */
        public default void findIssues(Solutions solutions) {
        }
        public default String getSubjectText() {
            if (this instanceof Named) {
                return (this.getClass().getSimpleName()+(((Named) this).getName() != null ? " "+((Named) this).getName() : ""));
            }
            else if (this instanceof Identifiable) {
                return (this.getClass().getSimpleName()+" "+((Identifiable) this).getId());
            }
            else {
                return (this.getClass().getSimpleName());
            }
        }
        /**
         * @return The icon that identifies this subject, or null if it has none of its own. The
         * view substitutes a generic one rather than the model naming a GUI resource.
         */
        public default Icon getSubjectIcon() {
            if (this instanceof PropertySheetHolder) {
                return ((PropertySheetHolder)this).getPropertySheetHolderIcon();
            }
            return null;
        }
    }

    public Milestone getTargetMilestone() {
        if (targetMilestone == null) {
            migrateLevel();
        }
        return targetMilestone;
    }
    public void setTargetMilestone(Milestone targetMilestone) {
        Object oldValue = this.targetMilestone;
        this.targetMilestone = targetMilestone;
        propertyChangeSupport.firePropertyChange("targetMilestone", oldValue, targetMilestone);
    }

    public boolean isShowIndicator() {
        return showIndicator;
    }

    public void setShowIndicator(boolean showIndicator) {
        this.showIndicator = showIndicator;
    }

    public boolean isShowSolved() {
        return showSolved;
    }

    public void setShowSolved(boolean showSolved) {
        this.showSolved = showSolved;
    }

    public boolean isShowDismissed() {
        return showDismissed;
    }

    public void setShowDismissed(boolean showDismissed) {
        this.showDismissed = showDismissed;
    }

    public boolean isTargeting(Milestone targetMilestone) {
        return getTargetMilestone().ordinal() >= targetMilestone.ordinal();
    }
    public boolean isAtMostTargeting(Milestone targetMilestone) {
        return getTargetMilestone().ordinal() <= targetMilestone.ordinal();
    }

    public enum Severity {
        None(new Color(255, 255, 255)), 
        Information(new Color(255, 255, 255)),
        Suggestion(new Color(255, 255, 157)),
        Warning(new Color(255, 220, 157)),
        Error(new Color(255, 157, 157)),
        Fundamental(new Color(200, 220, 255));

        final public Color color;

        Severity(Color color) {
            this.color = color;
        }
    }

    public enum State {
        Open(new Color(255, 255, 255)),
        Solved(new Color(157, 255, 168)),
        Dismissed(new Color(220, 220, 220));

        /** Read by the table renderer, which no longer lives in this package. */
        final public Color color;

        State(Color color) {
            this.color = color;
        }
    }

    public boolean confirm(String message, boolean warning) {
        return Configuration.get().getUserInteraction()
                .confirm(warning ? "Warning" : "Question", message); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public static abstract class Issue extends AbstractModelObject {
        final Subject subject;
        final String issue;
        final String solution;
        final Severity severity;
        final String uri;
        private State state;
        private Object choice;

        public Issue(Subject subject, String issue, String solution, Severity severity, String uri) {
            super();
            this.subject = subject; 
            this.issue = issue;
            this.solution = solution;
            this.severity = severity;
            this.uri = uri;
            if (solution.isEmpty()) {
                state = State.Dismissed;
            }
            else {
                state = State.Open;
            }
        }
        public Subject getSubject() {
            return subject;
        }
        public String getIssue() {
            return Translations.translateText(issue);
        }
        public String getSolution() {
            return Translations.translateText(solution);
        }
        /**
         * The English wording as the source wrote it, which is what identifies this issue - see
         * getFingerprint. Compare against this rather than against getIssue, which follows the
         * display language.
         */
        public String getUntranslatedIssue() {
            return issue;
        }

        /** The solution as the source wrote it, for the same reason as getUntranslatedIssue. */
        public String getUntranslatedSolution() {
            return solution;
        }
        public Severity getSeverity() {
            return severity;
        }
        public String getFingerprint() {
            // The untranslated fields, deliberately. This identity is persisted in machine.xml to
            // remember which issues the user dismissed or solved, so it must not move when the
            // display language does.
            return DigestUtils.shaHex(subject.getSubjectText()+"\n"+issue+"\n"+solution);
        }

        public State getState() {
            return state;
        }

        public void setStateCall(State state) throws Exception {
            // The untranslated wording: a log in the user's language is of no use to whoever ends
            // up reading it to help them.
            Logger.debug("About to set state "+state+" (from "+getState()+") on "+getSubject().getSubjectText()+": "+issue);
            setState(state);
        }

        public void setState(State state) throws Exception {
            Object oldValue = this.state;
            this.state = state;
            firePropertyChange("state", oldValue, state);
        }

        public boolean isUnhandled() {
            return state == State.Open;
        }

        public Object getChoice() {
            return choice;
        }
        public void setChoice(Object choice) {
            this.choice = choice;
        }

        /**
         * @return true if this solution is triggered by a machine condition that absolutely needs to be 
         * addressed. This will force a solution to be unsolved, even is marked as "solved". Note, it will not
         * force open a solution that is marked as "dismissed".
         */
        public boolean isForcedUnsolved() {
            return false;
        }
        void setInitialState(State state) {
            this.state = state;
        }
        public String getUri() {
            return uri;
        }
        public boolean canBeAccepted() {
            return true;
        }
        public boolean canBeUndone() {
            return true;
        }

        /**
         * Ultra simple custom property support. 
         *
         */
        public abstract class CustomProperty {
            private final String label;
            private final String toolTip;

            public CustomProperty(String label, String toolTip) {
                super();
                this.label = label;
                this.toolTip = toolTip;
            }

            public String getLabel() {
                return label;
            }
            public String getToolTip() {
                return toolTip;
            }
        }
        public abstract class StringProperty extends CustomProperty {
            public StringProperty(String label, String toolTip) {
                super(label, toolTip);
            }
            public String [] getSuggestions() { 
                return null; 
            }
            public abstract String get();
            public abstract void set(String value) throws Exception;
            public String getSuggestionToolTip() { 
                return getToolTip();
            }
        }
        public abstract class MultiLineTextProperty extends StringProperty {
            public MultiLineTextProperty(String label, String toolTip) {
                super(label, toolTip);
            }
        }
        public abstract class BooleanProperty extends CustomProperty {
            public BooleanProperty(String label, String toolTip) {
                super(label, toolTip);
            }
            public abstract boolean get();
            public abstract void set(boolean value);
        }
        public abstract class IntegerProperty extends CustomProperty {
            private final int min;
            private final int max;

            public IntegerProperty(String label, String toolTip, int min, int max) {
                super(label, toolTip);
                this.min = min;
                this.max = max;
            }
            public int getMin() {
                return min;
            }
            public int getMax() {
                return max;
            }
            public abstract int get();
            public abstract void set(int value) throws Exception;
        }
        public abstract class DoubleProperty extends CustomProperty {
            private final double min;
            private final double max;

            public DoubleProperty(String label, String toolTip, double min, double max) {
                super(label, toolTip);
                this.min = min;
                this.max = max;
            }
            public double getMin() {
                return min;
            }
            public double getMax() {
                return max;
            }
            public abstract double get();
            public abstract void set(double value)  throws Exception;
        }
        public abstract class LengthProperty extends CustomProperty {
            public LengthProperty(String label, String toolTip) {
                super(label, toolTip);
            }
            public abstract Length get();
            public abstract void set(Length value)  throws Exception;
        }
        public abstract class ActionProperty extends CustomProperty {
            public ActionProperty(String label, String toolTip) {
                super(label, toolTip);
            }
            public abstract Action get();
        }

        public CustomProperty [] getProperties() {
            return new CustomProperty[] {};
        }

        /**
         * Ultra-simple multiple-choices system. 
         * @return
         */
        public class Choice {
            final private Object value;
            final private Icon icon;
            final private String description;

            public Choice(Object value, String description, Icon icon) {
                super();
                this.value = value;
                this.description = description;
                this.icon = icon;
            }
            public Object getValue() {
                return value;
            }
            public Icon getIcon() {
                return icon;
            }
            public String getDescription() {
                return Translations.translateText(description);
            }
        }

        public Choice [] getChoices() {
            return new Choice[] {};
        }

        public void activate() throws Exception {
        }

        /**
         * The longer explanation the panel shows under the solution, in the display language.
         * <p>
         * Final, and paired with {@link #extendedDescription()} for subclasses to override,
         * because this one has to translate what they return. getIssue and getSolution translate
         * the fields they were handed; an overridable getter here would quietly bypass that, which
         * is how this panel came to be the one part of the feature that was never translated in
         * any language.
         */
        public final String getExtendedDescription() {
            return Translations.translateText(extendedDescription());
        }

        /**
         * Override to explain the solution at length, in English. Null for no explanation.
         */
        protected String extendedDescription() {
            return null;
        }

        public Icon getExtendedIcon() {
            return null;
        }
    }

    public static class PlainIssue extends Issue {

        public PlainIssue(Subject subject, String issue, String solution, Severity severity,
                String uri) {
            super(subject, issue, solution, severity, uri);
        }

        @Override
        public void setState(State state) throws Exception {
            if (state == State.Solved) {
                UiUtils.browseUri(uri);
            }
            super.setState(state);
        }
        public boolean canBeAccepted() {
            return false;
        }
    }

    private List<Issue> pendingIssues = null;
    private List<Issue> issues = new ArrayList<>();

    public boolean isSolutionsIssueDismissed(Issue issue) {
        return dismissedSolutions.contains(issue.getFingerprint());
    }
    public void setSolutionsIssueDismissed(Issue issue, boolean dismissed) {
        if (dismissed) {
            solvedSolutions.remove(issue.getFingerprint());
            dismissedSolutions.add(issue.getFingerprint()); 
        }
        else {
            dismissedSolutions.remove(issue.getFingerprint());
        }
    }

    public boolean isSolutionsIssueSolved(Issue issue) {
        return solvedSolutions.contains(issue.getFingerprint());
    }

    public void setSolutionsIssueSolved(Issue issue, boolean solved) {
        if (solved) {
            dismissedSolutions.remove(issue.getFingerprint());
            solvedSolutions.add(issue.getFingerprint()); 
        }
        else {
            solvedSolutions.remove(issue.getFingerprint());
        }
    }

    public List<Issue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public Machine getMachine() {
        Machine machine = Configuration.get().getMachine();
        return machine;
    }

    /**
     * Perform the Issues & Solutions search. This opens a list of pending issues, i.e. it does not
     * directly affect the table model. Pending issues will only be made visible when 
     * {@link #publishIssues()} is called.    
     */
    public synchronized void findIssues() {
        pendingIssues = new ArrayList<>();
        Machine machine = getMachine();
        machine.findIssues(this);
        // Add the milestone completion solution.
        Milestone targetMilestone = getTargetMilestone();
        pendingIssues.add(new Solutions.Issue(
                targetMilestone, 
                Translations.getString("Solutions.Issue.CompleteMilestone")
                        + " " + targetMilestone.getName(),
                targetMilestone.getDescription(), 
                Solutions.Severity.Information,
                "https://github.com/openpnp/openpnp/wiki/Issues-and-Solutions"+targetMilestone.getAnchor()) {
            {
                setChoice(targetMilestone.getNext());
            }
            @Override
            public void setState(Solutions.State state) throws Exception {
                if (state == State.Solved) {
                    boolean ok = true;
                    if (getChoice() == targetMilestone.getNext()) {
                        // Check if proceeding to the next level is ok.
                        // Make a fresh findIssues (this will only affect the pendingIssues, not the actual ones).
                        findIssues();
                        StringBuilder str = new StringBuilder();
                        str.append("<ul>");
                        for (Issue issue : pendingIssues) {
                            if (issue.isUnhandled() 
                                    && !issue.getFingerprint().equals(getFingerprint())) {
                                // There is still an open issue.
                                ok = false;
                                str.append("<li>");
                                str.append(XmlSerialize.escapeXml(issue.getSubject().getSubjectText()));
                                str.append(": ");
                                str.append(XmlSerialize.escapeXml(issue.getIssue()));
                                str.append("</li>");
                            }
                        }
                        str.append("</ul>");
                        pendingIssues = null;
                        if (!ok) {
                            if (confirm(String.format(Translations.getString("Solutions.Issue.CompleteMilestone.Confirm"
                                    ), targetMilestone, str.toString()),
                                    true)) {
                                ok = true;
                            }
                        }
                    }
                    if (ok) {
                        super.setState(state);
                        // Changing the milestone changes which issues apply, so a rescan is
                        // needed. Requesting it rather than calling the tab keeps this out of
                        // the GUI; whoever is showing the issues listens for it.
                        setTargetMilestone((Milestone) getChoice());
                        propertyChangeSupport.firePropertyChange("rescanRequested", false, true);
                    }
                }
                else {
                    super.setState(state);
                }
            }

            @Override
            public Solutions.Issue.Choice[] getChoices() {
                return new Solutions.Issue.Choice[] {
                        (targetMilestone.getNext() == null ? null :
                                new Solutions.Issue.Choice(targetMilestone.getNext(),
                                        String.format(Translations.getString(
                                                        "Solutions.Issue.CompleteMilestone.Choice.0"
                                                ), targetMilestone.getNext().getName(),
                                                targetMilestone.getName(),
                                                XmlSerialize.escapeXml(targetMilestone.getDescription()),
                                                targetMilestone.getNext().getName(),
                                                XmlSerialize.escapeXml(targetMilestone.getNext().getDescription())),
                                        null)),
                        (targetMilestone.getPrevious() == null ? null :
                                new Solutions.Issue.Choice(targetMilestone.getPrevious(),
                                        String.format(Translations.getString(
                                                        "Solutions.Issue.CompleteMilestone.Choice.1"
                                                ), targetMilestone.getPrevious().getName(),
                                                targetMilestone.getPrevious().getName(),
                                                XmlSerialize.escapeXml(targetMilestone.getPrevious().getDescription())),
                                        null)),
                };
            }
        });
        for (Issue issue : new ArrayList<>(pendingIssues)) {
            if (isSolutionsIssueDismissed(issue)) {
                if (isShowDismissed()) {
                    issue.setInitialState(State.Dismissed);
                }
                else {
                    pendingIssues.remove(issue);
                }
            }
            else if (isSolutionsIssueSolved(issue)) {
                if (issue.isForcedUnsolved()) {
                    setSolutionsIssueSolved(issue, false);
                }
                else if (isShowSolved()) {
                    issue.setInitialState(State.Solved);
                }
                else {
                    pendingIssues.remove(issue);
                }
            }
        }
    }

    /**
     * Adds the issue to the list of pending issues that {@link #findIssues()} has opened.
     * This should only be called from inside {@link #Subject.findIssues(Solutions)}
     * 
     * @param issue
     * @return true if the issue was already marked as solved.  
     */
    public synchronized boolean add(Issue issue) {
        // Do not allow duplicates. These will sometimes be generated when multiple machine objects 
        // check the same issue that is relevant to them. 
        String fingerprint = issue.getFingerprint();
        for (Issue duplicate : pendingIssues) {
            if (duplicate.getFingerprint().equals(fingerprint)) {
                return true;
            }
        }
        pendingIssues.add(issue);
        return isSolutionsIssueSolved(issue);
    }

    /**
     * Publish the pending issues that {@link #findIssues()} has found i.e. that were added using {@link #add(Issue)}.
     * This makes them visible trough the TableModel of this.
     */
    public synchronized void publishIssues() {
        // Go through the issues and install listeners to update the dismissedTroubleshooting.
        for (Issue issue : pendingIssues) {
            issue.addPropertyChangeListener("state", e -> {
                if (e.getOldValue() == Solutions.State.Dismissed) {
                    setSolutionsIssueDismissed(issue, false);
                }
                if (issue.getState() == Solutions.State.Dismissed) {
                    setSolutionsIssueDismissed(issue, true);
                }
                propertyChangeSupport.firePropertyChange("issue", null, issue);
            });
        }
        // Sort by state (initially only Open and Dismissed possible) and place Fundamentals first.
        pendingIssues.sort(new Comparator<Issue>() {
            @Override
            public int compare(Issue o1, Issue o2) {
                if (o1.getSeverity() == Severity.Fundamental && o2.getSeverity() != Severity.Fundamental) {
                    return -1;
                }
                else if (o2.getSeverity() == Severity.Fundamental) {
                    return 1;
                }
                else {
                    return 0;
                }
            }
        });
        List<Issue> oldValue = this.issues;
        this.issues = pendingIssues;
        pendingIssues = null;
        propertyChangeSupport.firePropertyChange("issues", oldValue, this.issues);
    }

    public Issue getIssue(int index) {
        return issues.get(index);
    }

    @Deprecated
    public void migrateDismissedSolutions(Set<String> dismissedSolutions) {
        this.dismissedSolutions = dismissedSolutions;
    }

    @Deprecated
    private void migrateLevel() {
        // Migration of an older configuration, try reconstructing the level. This is only a very crude heuristic.
        if (getMachine().getDrivers().isEmpty() || getMachine().getDrivers().get(0).isPlaceholder()) {
            targetMilestone = Milestone.Welcome;
        }
        else if (getMachine().getMotionPlanner().isPlaceholder()) {
            targetMilestone = Milestone.Calibration;
            try {
                for (Camera camera : new Camera[] {
                        getMachine().getDefaultHead().getDefaultCamera(), 
                        VisionUtils.getBottomVisionCamera() }) {
                    if (camera.getUnitsPerPixel().getX() == 0 || camera.getUnitsPerPixel().getY() == 0) {
                        targetMilestone = Milestone.Vision;
                    }
                }
            }
            catch (Exception e) {
                targetMilestone = Milestone.Connect;
            }
            for (Axis axis : getMachine().getAxes()) {
                if (axis instanceof ControllerAxis) {
                    if (((ControllerAxis) axis).getDriver() == null 
                            || ((ControllerAxis) axis).getLetter().isEmpty()) {
                        targetMilestone = Milestone.Basics;
                    }
                }
            }
        }
        else {
            targetMilestone = Milestone.Advanced;
        }
    }
}
