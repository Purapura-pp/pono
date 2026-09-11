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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;

import javax.swing.JPanel;

import org.junit.jupiter.api.Test;
import org.openpnp.gui.shell.OverlayAnchorLayout.Anchor;

/**
 * Where the floating controls end up on the camera image.
 * <p>
 * A card in the wrong place is not a crash, it is a jog button that cannot be reached, or a
 * coordinate hidden behind the controls that are moving it. Both of those happened while this was
 * being written, and neither shows up in a screenshot of a window that happens to be large enough.
 */
public class OverlayAnchorLayoutTest {
    private static final int MARGIN = 10;

    private JPanel card(int width, int height) {
        JPanel card = new JPanel();
        card.setPreferredSize(new Dimension(width, height));
        return card;
    }

    private JPanel stage(int width, int height) {
        JPanel stage = new JPanel(new OverlayAnchorLayout());
        stage.setSize(width, height);
        return stage;
    }

    @Test
    public void theFillingComponentTakesEverythingAndTheRestKeepTheirMargins() {
        JPanel stage = stage(800, 600);
        JPanel image = card(100, 100);
        JPanel readout = card(200, 40);
        JPanel controls = card(300, 250);
        stage.add(image, Anchor.Fill);
        stage.add(readout, Anchor.SouthWest);
        stage.add(controls, Anchor.SouthEast);

        stage.doLayout();

        assertEquals(0, image.getX());
        assertEquals(0, image.getY());
        assertEquals(800, image.getWidth());
        assertEquals(600, image.getHeight());
        assertEquals(MARGIN, readout.getX());
        assertEquals(600 - 40 - MARGIN, readout.getY());
        assertEquals(800 - 300 - MARGIN, controls.getX());
        assertEquals(600 - 250 - MARGIN, controls.getY());
    }

    @Test
    public void aCardKeepsItsPreferredSizeUntilItWouldNotFit() {
        JPanel stage = stage(200, 150);
        JPanel controls = card(300, 250);
        stage.add(card(100, 100), Anchor.Fill);
        stage.add(controls, Anchor.SouthEast);

        stage.doLayout();

        assertEquals(200 - 2 * MARGIN, controls.getWidth());
        assertEquals(150 - 2 * MARGIN, controls.getHeight());
        assertEquals(MARGIN, controls.getX());
        assertEquals(MARGIN, controls.getY());
    }

    @Test
    public void theWesternCardMovesAboveTheEasternOneRatherThanUnderIt() {
        JPanel stage = stage(500, 600);
        JPanel readout = card(200, 40);
        JPanel controls = card(300, 250);
        stage.add(card(100, 100), Anchor.Fill);
        stage.add(readout, Anchor.SouthWest);
        stage.add(controls, Anchor.SouthEast);

        stage.doLayout();

        // 200 + 300 with margins between and either side does not fit across 500.
        assertEquals(600 - 250 - MARGIN, controls.getY());
        assertEquals(controls.getY() - 40 - MARGIN, readout.getY());
        assertEquals(MARGIN, readout.getX());
        assertTrue(readout.getY() + readout.getHeight() <= controls.getY(),
                "the readout must not end up behind the controls");
    }

    @Test
    public void theCardsReturnToOppositeCornersOnceThereIsRoom() {
        JPanel stage = stage(900, 600);
        JPanel readout = card(200, 40);
        JPanel controls = card(300, 250);
        stage.add(card(100, 100), Anchor.Fill);
        stage.add(readout, Anchor.SouthWest);
        stage.add(controls, Anchor.SouthEast);

        stage.doLayout();

        assertEquals(600 - 40 - MARGIN, readout.getY());
        assertEquals(600 - 250 - MARGIN, controls.getY());
    }

    @Test
    public void aHiddenCardDoesNotPushItsNeighbourAround() {
        JPanel stage = stage(500, 600);
        JPanel readout = card(200, 40);
        JPanel controls = card(300, 250);
        controls.setVisible(false);
        stage.add(card(100, 100), Anchor.Fill);
        stage.add(readout, Anchor.SouthWest);
        stage.add(controls, Anchor.SouthEast);

        stage.doLayout();

        assertEquals(600 - 40 - MARGIN, readout.getY());
    }

    @Test
    public void theInstructionsSitCentredAgainstTheTopEdge() {
        JPanel stage = stage(800, 600);
        JPanel instructions = card(400, 60);
        stage.add(card(100, 100), Anchor.Fill);
        stage.add(instructions, Anchor.North);

        stage.doLayout();

        assertEquals((800 - 400) / 2, instructions.getX());
        assertEquals(MARGIN, instructions.getY());
    }

    @Test
    public void onlyTheImageDecidesHowMuchRoomTheStageAsksFor() {
        JPanel stage = stage(800, 600);
        stage.add(card(320, 240), Anchor.Fill);
        stage.add(card(900, 700), Anchor.SouthEast);

        // A stage that asked for room for the controls too would let them dictate the split
        // between the camera and the panels beside it, which is the arrangement this replaced.
        assertEquals(new Dimension(320, 240), stage.getPreferredSize());
    }

    @Test
    public void aComponentAddedWithoutAnAnchorIsIgnoredRatherThanRefused() {
        JPanel stage = stage(800, 600);
        stage.add(card(100, 100), Anchor.Fill);

        // A layered pane passes a null constraint through on a plain add(), and a window that
        // will not open is a worse answer to that than a component in the wrong corner.
        stage.add(card(50, 50));

        stage.doLayout();
    }
}
