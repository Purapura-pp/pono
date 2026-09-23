package org.openpnp.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.openpnp.Translations;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.model.Board;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Placement;

/**
 * The boards and panels pages: what "modified · 2 places" counts, how a definition is listed,
 * and that an error message keeps the words it has in angle brackets.
 */
public class BoardsPageTest {
    private static String places(int n) {
        return String.format(Translations.getString("UnsavedChanges.Places"), n);
    }

    @Test
    public void aPlacementChangedTwiceIsOnePlace() {
        Board board = new Board();
        Placement r1 = new Placement("R1");
        board.addPlacement(r1);
        board.setDirty(false);
        UnsavedChanges unsaved = new UnsavedChanges(() -> {
        });
        unsaved.watch(board);
        assertNull(unsaved.describe(board), "a saved board has nothing to say");

        r1.setComments("near U3");
        r1.setComments("near U3, mind the polarity");
        assertTrue(board.isDirty());
        assertEquals(places(1), unsaved.describe(board));

        board.addPlacement(new Placement("R2"));
        assertEquals(places(2), unsaved.describe(board), "an added placement is a place of its own");
    }

    @Test
    public void savingStartsTheCountAgain() {
        Board board = new Board();
        Placement r1 = new Placement("R1");
        board.addPlacement(r1);
        UnsavedChanges unsaved = new UnsavedChanges(() -> {
        });
        unsaved.watch(board);
        r1.setComments("a");
        board.setDirty(false);
        assertNull(unsaved.describe(board));

        r1.setComments("b");
        assertEquals(places(1), unsaved.describe(board));
    }

    @Test
    public void aBoardChangedElsewhereIsModifiedWithoutACount() {
        Board board = new Board();
        UnsavedChanges unsaved = new UnsavedChanges(() -> {
        });
        board.setDirty(true);
        assertEquals(Translations.getString("UnsavedChanges.Modified"), unsaved.describe(board));
    }

    @Test
    public void aDefinitionIsListedByItsFolderFileAndSize() {
        assertEquals("jobs" + File.separator + "demo-board.board.xml",
                DefinitionList.where(new File(new File("config", "jobs"), "demo-board.board.xml")));
        Board board = new Board();
        board.setDimensions(new Location(LengthUnit.Millimeters, 160, 120.5, 0, 0));
        assertEquals("160 \u00d7 120.5 mm", DefinitionList.size(board));
        board.setDimensions(new Location(LengthUnit.Millimeters));
        assertEquals(Translations.getString("DefinitionList.NoSize"), DefinitionList.size(board));
    }

    @Test
    public void messagesKeepTheirAngleBrackets() {
        assertEquals("Could not load demo:\nno such file",
                Dialogs.plainText("<html>Could not load <b>demo</b>:<br>no such file</html>"));
        assertEquals("Expected <none>, and a < b", Dialogs.plainText("Expected <none>, and a < b"));
        assertEquals("<x> is not a board", Dialogs.plainText("&lt;x&gt; is not a board"));
    }
}
