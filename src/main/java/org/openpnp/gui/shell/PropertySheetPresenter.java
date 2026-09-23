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

package org.openpnp.gui.shell;

import java.awt.Component;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import org.openpnp.Translations;
import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.spi.PropertySheetHolder;
import org.openpnp.spi.PropertySheetHolder.PropertySheet;

/**
 * Fills a tab strip with the property sheets of whatever is selected, and gets the user's answer
 * about unapplied edits before it takes those sheets away.
 * <p>
 * Every table in this program had its own copy of this: dispose the wizards on show, rebuild the
 * tabs, hand each wizard the container it reports back to, remember which tab was open for this
 * kind of thing, and - in the feeders panel only - ask what to do about edits that were never
 * applied. The copies had drifted. The feeders panel asked about unapplied edits and the machine
 * setup tree silently discarded them; the machine setup tree had a fallback for a sheet with no
 * title and the feeders panel would have shown a blank tab.
 * <p>
 * The prompt is a collaborator rather than a call to JOptionPane, because the question it asks is
 * the part worth testing and a modal dialog in a test is a test that hangs.
 */
public class PropertySheetPresenter {
    /** What to do with edits that were typed into a sheet but never applied. */
    public enum Choice {
        Apply,
        Discard,
        Cancel
    }

    public interface Prompt {
        /**
         * @param name What the user last selected, named as they saw it named.
         */
        Choice ask(String name);
    }

    public enum Result {
        /** The sheets on show are the ones that were asked for. */
        Shown,
        /** The user cancelled. Whoever asked should put its selection back where it was. */
        Cancelled,
        /** A question about the previous selection is already on screen. Do nothing. */
        Busy
    }

    /** The tab strip this fills. Owned by the caller, because it decides how it is presented. */
    private final JTabbedPane sheets;

    private final Prompt prompt;

    /**
     * The tab that was open, per kind of thing shown. Selecting a second feeder of the same type
     * lands on the tab you were last working in, which is the point of keeping it by class.
     */
    private final Map<Class<?>, Integer> lastTabByClass = new HashMap<>();

    /**
     * What is on show. Not a {@link PropertySheetHolder}: a part's sheets are assembled from the
     * machine's part alignments and its fiducial locator rather than reported by the part itself.
     */
    private Object shown;

    private String shownName;

    /**
     * Set while the prompt is up. Asking is modal, and a modal dialog lets the event queue run,
     * which is how a table selection listener ends up calling back in here mid-question.
     */
    private boolean asking;

    public PropertySheetPresenter(JTabbedPane sheets) {
        this(sheets, new DialogPrompt(sheets));
    }

    public PropertySheetPresenter(JTabbedPane sheets, Prompt prompt) {
        this.sheets = sheets;
        this.prompt = prompt;
    }

    /**
     * Show one thing's property sheets, having settled what happens to any unapplied edits in the
     * sheets already up.
     * 
     * @param holder    What to show. Null clears the strip.
     * @param container Where the wizards report back to.
     * @param name      What to call it in the question about unapplied edits.
     */
    public Result show(PropertySheetHolder holder, WizardContainer container, String name) {
        return show(holder, container, name,
                holder == null ? null : Arrays.asList(nonNull(holder.getPropertySheets())));
    }

    /**
     * Show sheets that were assembled rather than reported. A part's sheets come from the
     * machine's part alignments and its fiducial locator, and a part is not a sheet holder.
     * 
     * @param subject What the sheets are about. Used to remember which tab was open for this kind
     *                of thing, and reported back by {@link #getShown()}.
     */
    public Result show(Object subject, WizardContainer container, String name,
            List<PropertySheet> propertySheets) {
        if (asking) {
            return Result.Busy;
        }
        if (!settleUnappliedEdits()) {
            return Result.Cancelled;
        }
        disposeShownWizards();
        sheets.removeAll();
        shown = subject;
        shownName = name;
        if (subject == null || propertySheets == null) {
            return Result.Shown;
        }
        for (PropertySheet propertySheet : propertySheets) {
            JPanel panel = propertySheet.getPropertySheetPanel();
            if (panel == null) {
                continue;
            }
            if (panel instanceof AbstractConfigurationWizard) {
                ((AbstractConfigurationWizard) panel).setWizardContainer(container);
            }
            // Until each wizard is redone as a declarative form, it is made to fit the column.
            org.openpnp.gui.form.LegacyWizardAdapter.adapt(panel);
            String title = propertySheet.getPropertySheetTitle();
            if (title == null) {
                title = Translations.getString("PropertySheetPresenter.Tab.Configuration"); //$NON-NLS-1$
            }
            sheets.addTab(title, panel);
        }
        Integer lastTab = lastTabByClass.get(subject.getClass());
        if (lastTab != null && sheets.getTabCount() > 0) {
            // Clamped, because the same class does not always produce the same number of sheets -
            // and because clicking about quickly used to land an index past the end of the strip.
            sheets.setSelectedIndex(Math.max(0, Math.min(sheets.getTabCount() - 1, lastTab)));
        }
        return Result.Shown;
    }

    /** One sheet, for callers holding a wizard rather than something that reports sheets. */
    public static PropertySheet sheet(String title, JPanel panel) {
        return new PropertySheet() {
            @Override
            public String getPropertySheetTitle() {
                return title;
            }

            @Override
            public JPanel getPropertySheetPanel() {
                return panel;
            }
        };
    }

    private static PropertySheet[] nonNull(PropertySheet[] propertySheets) {
        return propertySheets == null ? new PropertySheet[0] : propertySheets;
    }

    /**
     * Ask about unapplied edits in the sheets on show, and act on the answer.
     * 
     * @return false if the user cancelled, meaning the sheets stay as they are.
     */
    public boolean settleUnappliedEdits() {
        if (shown != null) {
            lastTabByClass.put(shown.getClass(), Math.max(0, sheets.getSelectedIndex()));
        }
        if (shown == null || !isDirty()) {
            return true;
        }
        Choice choice;
        asking = true;
        try {
            choice = prompt.ask(shownName == null ? String.valueOf(shown) : shownName);
        }
        finally {
            asking = false;
        }
        if (choice == Choice.Cancel) {
            return false;
        }
        if (choice == Choice.Apply) {
            for (Component component : sheets.getComponents()) {
                if (component instanceof AbstractConfigurationWizard) {
                    AbstractConfigurationWizard wizard = (AbstractConfigurationWizard) component;
                    if (Boolean.TRUE.equals(wizard.isDirty())) {
                        wizard.apply();
                    }
                }
            }
        }
        return true;
    }

    /** Whether any sheet on show has edits that were never applied. */
    public boolean isDirty() {
        for (Component component : sheets.getComponents()) {
            if (component instanceof AbstractConfigurationWizard
                    && Boolean.TRUE.equals(((AbstractConfigurationWizard) component).isDirty())) {
                return true;
            }
        }
        return false;
    }

    /** What is on show, or null. */
    public Object getShown() {
        return shown;
    }

    private void disposeShownWizards() {
        for (Component component : sheets.getComponents()) {
            if (component instanceof AbstractConfigurationWizard) {
                ((AbstractConfigurationWizard) component).dispose();
            }
        }
    }

    /**
     * The question as the user has always been asked it, in a dialog - over the window it is
     * about, where it used to open in the middle of the primary screen whichever screen that was.
     */
    static class DialogPrompt implements Prompt {
        private final Component parent;

        DialogPrompt(Component parent) {
            this.parent = parent;
        }

        @Override
        public Choice ask(String name) {
            int selection = JOptionPane.showConfirmDialog(parent,
                    Translations.getString("PropertySheetPresenter.ApplyChanges.Message") //$NON-NLS-1$
                            .replace("%s", String.valueOf(name)), //$NON-NLS-1$
                    Translations.getString("PropertySheetPresenter.ApplyChanges.Title"), //$NON-NLS-1$
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null);
            switch (selection) {
                case JOptionPane.YES_OPTION:
                    return Choice.Apply;
                case JOptionPane.NO_OPTION:
                    return Choice.Discard;
                default:
                    return Choice.Cancel;
            }
        }
    }
}
