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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import org.junit.jupiter.api.Test;
import org.openpnp.gui.shell.PropertySheetPresenter.Result;

/**
 * The properties column follows the page on screen.
 * <p>
 * It did not: every table wrote straight into it, so a background scan on the issues page replaced
 * whatever the parts page had put there, and switching pages left the previous page's sheets
 * behind. Both were seen in screenshots of the running program before this existed.
 */
public class InspectorPanelTest {
    private final JPanel parts = new JPanel();
    private final JPanel issues = new JPanel();

    private static List<org.openpnp.spi.PropertySheetHolder.PropertySheet> sheets(String... titles) {
        return java.util.Arrays.stream(titles)
                .map(title -> PropertySheetPresenter.sheet(title, new JPanel()))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * The tab strip on show, or null when the column says nothing is selected - in which case the
     * strip is not in the column's component tree at all.
     */
    private static JTabbedPane shownTabs(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JTabbedPane) {
                return (JTabbedPane) child;
            }
            if (child instanceof Container) {
                JTabbedPane found = shownTabs((Container) child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    public void aRequestFromThePageOnScreenIsShown() {
        InspectorPanel inspector = new InspectorPanel();
        inspector.setActivePage(parts);

        assertEquals(Result.Shown,
                inspector.show(parts, "R1", null, "R1", "0402", null, () -> sheets("Settings")));

        JTabbedPane tabs = shownTabs(inspector);
        assertNotNull(tabs, "the parts page is on screen, so its sheets should be");
        assertEquals("Settings", tabs.getTitleAt(0));
    }

    @Test
    public void aRequestFromAnotherPageIsKeptOffScreenUntilThatPageIsShown() {
        InspectorPanel inspector = new InspectorPanel();
        inspector.setActivePage(parts);
        inspector.show(parts, "R1", null, "R1", "0402", null, () -> sheets("Settings"));

        // The issues page scans in the background and reports what it found.
        assertEquals(Result.Shown,
                inspector.show(issues, "an issue", null, "an issue", "Warning", null,
                        () -> sheets("Issue")));

        assertEquals("Settings", shownTabs(inspector).getTitleAt(0),
                "the parts page is still on screen, so its sheets stay");

        inspector.setActivePage(issues);

        assertEquals("Issue", shownTabs(inspector).getTitleAt(0));
    }

    @Test
    public void comingBackToAPageShowsWhatItHadSelected() {
        InspectorPanel inspector = new InspectorPanel();
        inspector.setActivePage(parts);
        inspector.show(parts, "R1", null, "R1", "0402", null, () -> sheets("Settings"));
        inspector.setActivePage(issues);
        assertNull(shownTabs(inspector), "nothing is selected on the issues page");

        inspector.setActivePage(parts);

        assertEquals("Settings", shownTabs(inspector).getTitleAt(0));
    }

    @Test
    public void theSheetsAreBuiltAgainEachTimeTheyAreShown() {
        // Presenting disposes of the wizards it replaces, so a page's sheets cannot be kept and
        // reused: they have to be made afresh when the user comes back to that page.
        AtomicInteger builds = new AtomicInteger();
        InspectorPanel inspector = new InspectorPanel();
        inspector.setActivePage(parts);
        inspector.show(parts, "R1", null, "R1", "0402", null, () -> {
            builds.incrementAndGet();
            return sheets("Settings");
        });
        assertEquals(1, builds.get());

        inspector.setActivePage(issues);
        inspector.setActivePage(parts);

        assertEquals(2, builds.get());
    }

    @Test
    public void aPageWithNothingSelectedClearsTheColumnWhenItComesOnScreen() {
        InspectorPanel inspector = new InspectorPanel();
        inspector.setActivePage(parts);
        inspector.show(parts, "R1", null, "R1", "0402", null, () -> sheets("Settings"));

        inspector.show(parts, null, null, null, null, null, () -> sheets());

        assertNull(shownTabs(inspector));
    }
}
