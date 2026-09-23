package org.openpnp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Saving asks about modified boards and panels before it writes anything, Cancel means cancel,
 * and autosave asks nothing at all.
 */
public class ConfigurationSaveTest {
    @TempDir
    Path tempDir;

    private Configuration configuration;
    private File directory;
    private final List<String> asked = new ArrayList<>();
    private UserInteraction.SaveChoice answer = UserInteraction.SaveChoice.Cancel;

    @BeforeEach
    public void setUp() throws Exception {
        directory = tempDir.resolve(".openpnp").toFile();
        Configuration.initialize(directory);
        configuration = Configuration.get();
        configuration.load();
        configuration.setUserInteraction(new UserInteraction() {
            @Override
            public boolean confirm(String title, String message) {
                throw new AssertionError("confirm() is not how a save asks any more: " + title);
            }

            @Override
            public void reportError(String title, String message) {
                throw new AssertionError(title + ": " + message);
            }

            @Override
            public SaveChoice askToSave(String title, String message) {
                asked.add(title);
                return answer;
            }
        });
    }

    private Board modifiedBoard() throws Exception {
        Board board = configuration.getBoard(new File(directory, "test.board.xml"));
        board.setName("changed");
        board.setDirty(true);
        return board;
    }

    @Test
    public void aFreshlyLoadedConfigurationIsNotDirtyAndAChangeMakesIt() {
        assertFalse(configuration.isDirty());
        configuration.addPart(new Part("DIRTY-TEST"));
        assertTrue(configuration.isDirty());
    }

    /** Cancel leaves everything as it was: not a single file is written. */
    @Test
    public void cancellingTheBoardQuestionWritesNothing() throws Exception {
        modifiedBoard();
        File machineXml = new File(directory, "machine.xml");
        assertTrue(machineXml.delete());
        answer = UserInteraction.SaveChoice.Cancel;

        assertThrows(SaveCancelledException.class, () -> configuration.save());

        assertEquals(1, asked.size());
        assertFalse(machineXml.exists(), "nothing may be written before the questions are answered");
    }

    @Test
    public void dontSaveWritesTheConfigurationButNotTheBoard() throws Exception {
        Board board = modifiedBoard();
        File machineXml = new File(directory, "machine.xml");
        assertTrue(machineXml.delete());
        answer = UserInteraction.SaveChoice.Discard;

        configuration.save();

        assertTrue(machineXml.exists());
        assertTrue(board.isDirty(), "the board was not saved, so it still has its changes");
        assertFalse(configuration.isDirty());
    }

    @Test
    public void saveWritesTheBoardToo() throws Exception {
        Board board = modifiedBoard();
        answer = UserInteraction.SaveChoice.Save;

        configuration.save();

        assertFalse(board.isDirty());
        assertTrue(new File(directory, "test.board.xml").length() > 0);
    }

    @Test
    public void removingAModifiedBoardCanBeCancelled() throws Exception {
        Board board = modifiedBoard();
        answer = UserInteraction.SaveChoice.Cancel;

        assertFalse(configuration.removeBoard(board));
        assertTrue(configuration.getBoards().contains(board), "cancelled, so the board stays");

        answer = UserInteraction.SaveChoice.Discard;
        assertTrue(configuration.removeBoard(board));
        assertFalse(configuration.getBoards().contains(board));
    }

    /** Autosave is for the settings; boards are documents the user saves on purpose. */
    @Test
    public void autosaveAsksNothingAndWritesTheConfiguration() throws Exception {
        modifiedBoard();
        Package pkg = new Package("AUTOSAVE-PKG");
        configuration.addPackage(pkg);
        Part part = new Part("AUTOSAVE-TEST");
        part.setPackage(pkg);
        configuration.addPart(part);
        assertTrue(configuration.isDirty());
        File machineXml = new File(directory, "machine.xml");
        assertTrue(machineXml.delete());

        configuration.autosave();

        assertTrue(asked.isEmpty());
        assertTrue(machineXml.exists());
        assertFalse(configuration.isDirty());
    }
}
