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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.gui.shell.PropertySheetPresenter.Choice;
import org.openpnp.gui.shell.PropertySheetPresenter.Result;
import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.model.Configuration;
import org.openpnp.spi.PropertySheetHolder;

/**
 * The one copy of the logic every table used to keep its own version of.
 * <p>
 * The part worth pinning down is what happens to edits nobody applied: answering Cancel has to
 * leave the sheets exactly as they were, because the table that asked is about to put its
 * selection back to match. The rest - disposing the wizards that are going away, remembering which
 * tab was open for this kind of thing - was silently different between the copies.
 */
public class PropertySheetPresenterTest {
    @TempDir
    Path tempDir;

    private JTabbedPane sheets;
    private StubPrompt prompt;
    private PropertySheetPresenter presenter;
    private WizardContainer container;

    @BeforeEach
    public void setUp() throws Exception {
        // A configuration wizard reads the configuration while it is being built, so there has to
        // be one for a stub wizard to exist at all.
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
        sheets = new JTabbedPane();
        prompt = new StubPrompt();
        presenter = new PropertySheetPresenter(sheets, prompt);
        container = new WizardContainer() {
            @Override
            public void wizardCompleted(Wizard wizard) {
            }

            @Override
            public void wizardCancelled(Wizard wizard) {
            }
        };
    }

    @Test
    public void theSheetsOfTheThingSelectedBecomeTheTabs() {
        StubHolder holder = new StubHolder("N1", new StubWizard("Nozzle"), new StubWizard("Vision"));

        assertEquals(Result.Shown, presenter.show(holder, container, "N1"));

        assertEquals(2, sheets.getTabCount());
        assertEquals("Nozzle", sheets.getTitleAt(0));
        assertEquals("Vision", sheets.getTitleAt(1));
        assertSame(holder, presenter.getShown());
    }

    @Test
    public void aSheetWithNoTitleGetsOneRatherThanAnEmptyTab() {
        // The machine setup tree had this fallback and the feeders panel did not.
        StubHolder holder = new StubHolder("A driver", new StubWizard(null));

        presenter.show(holder, container, "A driver");

        assertEquals(1, sheets.getTabCount());
        assertFalse(sheets.getTitleAt(0).isEmpty());
    }

    @Test
    public void everyWizardIsToldWhereToReportBack() {
        StubWizard wizard = new StubWizard("Nozzle");

        presenter.show(new StubHolder("N1", wizard), container, "N1");

        assertSame(container, wizard.getWizardContainer());
    }

    @Test
    public void theWizardsBeingTakenAwayAreDisposed() {
        StubWizard first = new StubWizard("Nozzle");
        presenter.show(new StubHolder("N1", first), container, "N1");

        presenter.show(new StubHolder("N2", new StubWizard("Nozzle")), container, "N2");

        assertTrue(first.disposed, "a wizard left listening after it is off screen keeps its "
                + "subject alive and keeps answering property changes");
    }

    @Test
    public void showingNothingClearsTheStrip() {
        presenter.show(new StubHolder("N1", new StubWizard("Nozzle")), container, "N1");

        assertEquals(Result.Shown, presenter.show(null, container, null));

        assertEquals(0, sheets.getTabCount());
        assertNull(presenter.getShown());
    }

    @Test
    public void theTabYouWereWorkingInComesBackForTheSameKindOfThing() {
        presenter.show(new StubHolder("N1", new StubWizard("A"), new StubWizard("B"),
                new StubWizard("C")), container, "N1");
        sheets.setSelectedIndex(2);

        presenter.show(new StubHolder("N2", new StubWizard("A"), new StubWizard("B"),
                new StubWizard("C")), container, "N2");

        assertEquals(2, sheets.getSelectedIndex());
    }

    @Test
    public void aRememberedTabPastTheEndOfAShorterStripIsClamped() {
        presenter.show(new StubHolder("N1", new StubWizard("A"), new StubWizard("B"),
                new StubWizard("C")), container, "N1");
        sheets.setSelectedIndex(2);

        // The same class does not always produce the same sheets, and clicking about quickly used
        // to throw an index out of bounds here.
        presenter.show(new StubHolder("N2", new StubWizard("A")), container, "N2");

        assertEquals(0, sheets.getSelectedIndex());
    }

    @Test
    public void unappliedEditsAreNotMentionedWhenThereAreNone() {
        presenter.show(new StubHolder("N1", new StubWizard("A")), container, "N1");

        presenter.show(new StubHolder("N2", new StubWizard("A")), container, "N2");

        assertTrue(prompt.asked.isEmpty(), "asked about nothing: " + prompt.asked);
    }

    @Test
    public void unappliedEditsAreOfferedByTheNameTheUserSaw() {
        StubWizard dirty = new StubWizard("A");
        dirty.dirty = true;
        presenter.show(new StubHolder("N1", dirty), container, "Nozzle 1");
        prompt.answer = Choice.Apply;

        presenter.show(new StubHolder("N2", new StubWizard("A")), container, "Nozzle 2");

        assertEquals(List.of("Nozzle 1"), prompt.asked);
        assertTrue(dirty.applied);
    }

    @Test
    public void discardingUnappliedEditsShowsTheNewSelectionWithoutApplyingThem() {
        StubWizard dirty = new StubWizard("A");
        dirty.dirty = true;
        presenter.show(new StubHolder("N1", dirty), container, "N1");
        prompt.answer = Choice.Discard;

        StubHolder second = new StubHolder("N2", new StubWizard("A"));
        assertEquals(Result.Shown, presenter.show(second, container, "N2"));

        assertFalse(dirty.applied);
        assertSame(second, presenter.getShown());
    }

    @Test
    public void cancellingLeavesTheSheetsExactlyAsTheyWere() {
        StubWizard dirty = new StubWizard("A");
        dirty.dirty = true;
        StubHolder first = new StubHolder("N1", dirty);
        presenter.show(first, container, "N1");
        prompt.answer = Choice.Cancel;

        assertEquals(Result.Cancelled, presenter.show(new StubHolder("N2", new StubWizard("A")),
                container, "N2"));

        // The table that asked is about to put its selection back, so these have to match it.
        assertSame(first, presenter.getShown());
        assertFalse(dirty.disposed);
        assertFalse(dirty.applied);
        assertEquals(1, sheets.getTabCount());
    }

    @Test
    public void aCallArrivingWhileTheQuestionIsUpIsIgnored() {
        StubWizard dirty = new StubWizard("A");
        dirty.dirty = true;
        presenter.show(new StubHolder("N1", dirty), container, "N1");
        // Asking is modal, which lets the event queue run, which is how a selection listener ends
        // up calling back in here mid-question.
        prompt.reentrant = () -> assertEquals(Result.Busy,
                presenter.show(new StubHolder("N3", new StubWizard("A")), container, "N3"));
        prompt.answer = Choice.Discard;

        presenter.show(new StubHolder("N2", new StubWizard("A")), container, "N2");

        assertEquals("N2", presenter.getShown().getPropertySheetHolderTitle());
    }

    private static class StubPrompt implements PropertySheetPresenter.Prompt {
        final List<String> asked = new ArrayList<>();
        Choice answer = Choice.Discard;
        Runnable reentrant;

        @Override
        public Choice ask(String name) {
            asked.add(name);
            if (reentrant != null) {
                Runnable once = reentrant;
                reentrant = null;
                once.run();
            }
            return answer;
        }
    }

    @SuppressWarnings("serial")
    private static class StubWizard extends AbstractConfigurationWizard {
        private final String title;
        boolean dirty;
        boolean applied;
        boolean disposed;

        StubWizard(String title) {
            this.title = title;
        }

        @Override
        public Boolean isDirty() {
            return dirty;
        }

        @Override
        public void apply() {
            applied = true;
            dirty = false;
        }

        @Override
        public void dispose() {
            disposed = true;
        }

        @Override
        public void createBindings() {
        }

        @Override
        public void loadFromModel() {
        }

        @Override
        public void saveToModel() {
        }
    }

    private static class StubHolder implements PropertySheetHolder {
        private final String title;
        private final StubWizard[] wizards;

        StubHolder(String title, StubWizard... wizards) {
            this.title = title;
            this.wizards = wizards;
        }

        @Override
        public String getPropertySheetHolderTitle() {
            return title;
        }

        @Override
        public PropertySheetHolder[] getChildPropertySheetHolders() {
            return null;
        }

        @Override
        public PropertySheet[] getPropertySheets() {
            PropertySheet[] sheets = new PropertySheet[wizards.length];
            for (int index = 0; index < wizards.length; index++) {
                StubWizard wizard = wizards[index];
                sheets[index] = new PropertySheet() {
                    @Override
                    public String getPropertySheetTitle() {
                        return wizard.title;
                    }

                    @Override
                    public JPanel getPropertySheetPanel() {
                        return wizard;
                    }
                };
            }
            return sheets;
        }

        @Override
        public Action[] getPropertySheetHolderActions() {
            return null;
        }

        @Override
        public Icon getPropertySheetHolderIcon() {
            return null;
        }
    }
}
