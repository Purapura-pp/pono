package org.openpnp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.model.Solutions.Issue;
import org.openpnp.model.Solutions.Severity;
import org.openpnp.model.Solutions.State;
import org.openpnp.model.Solutions.Subject;

/**
 * Solutions used to be a Swing table model that also put up its own dialogs, which is why it sits
 * in the model package but could only really run under a GUI. It now reports through property
 * changes and asks questions through UserInteraction; the calibration page lays its issues out.
 * <p>
 * These run with no GUI: MainFrame is never constructed and no Swing table is attached.
 */
public class SolutionsTest {
    @TempDir
    Path tempDir;

    private static class TestSubject implements Subject {
        @Override
        public String getSubjectText() {
            return "test subject";
        }
    }

    private static Issue anIssue(Subject subject, String text) {
        return new Solutions.PlainIssue(subject, text, "do something", Severity.Warning,
                "https://example.invalid");
    }

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
    }

    @Test
    public void aSubjectWithNoIconOfItsOwnReportsNullRatherThanNamingAGuiResource() {
        assertNull(new TestSubject().getSubjectIcon(),
                "the view decides what a subject without an icon looks like");
    }

    @Test
    public void milestonesCarryNoIconEither() {
        assertNull(Solutions.Milestone.Welcome.getSubjectIcon());
    }

    /** add() is only valid between findIssues() and publishIssues(), as the subjects use it. */
    private static Solutions withPendingIssue(Issue issue) {
        Solutions solutions = new Solutions();
        solutions.findIssues();
        solutions.add(issue);
        return solutions;
    }

    private static long countIssuesReading(Solutions solutions, String text) {
        // The untranslated wording, so this does not depend on the machine's display language.
        return solutions.getIssues().stream()
                .filter(i -> text.equals(i.getUntranslatedIssue())).count();
    }

    @Test
    public void publishingIssuesReportsThemAsAPropertyChange() {
        Solutions solutions = withPendingIssue(anIssue(new TestSubject(), "something is wrong"));
        List<String> events = new ArrayList<>();
        solutions.addPropertyChangeListener("issues", e -> events.add("issues"));

        solutions.publishIssues();

        assertEquals(List.of("issues"), events,
                "the table used to be told directly; now it listens");
        assertEquals(1, countIssuesReading(solutions, "something is wrong"));
    }

    @Test
    public void aDuplicateIssueIsNotPublishedTwice() {
        Subject subject = new TestSubject();
        Solutions solutions = withPendingIssue(anIssue(subject, "same text"));

        solutions.add(anIssue(subject, "same text"));
        solutions.publishIssues();

        assertEquals(1, countIssuesReading(solutions, "same text"),
                "issues are deduplicated by fingerprint, not by identity");
    }

    @Test
    public void changingAnIssueStateReportsThatSingleIssue() throws Exception {
        Issue issue = anIssue(new TestSubject(), "something is wrong");
        Solutions solutions = withPendingIssue(issue);
        solutions.publishIssues();
        List<Object> reported = new ArrayList<>();
        solutions.addPropertyChangeListener("issue", e -> reported.add(e.getNewValue()));

        issue.setState(State.Dismissed);

        assertEquals(1, reported.size());
        assertSame(issue, reported.get(0));
    }

    @Test
    public void confirmingGoesThroughTheUserInteractionSeam() {
        List<String> asked = new ArrayList<>();
        Configuration.get().setUserInteraction(new UserInteraction() {
            @Override
            public boolean confirm(String title, String message) {
                asked.add(title);
                return true;
            }

            @Override
            public void reportError(String title, String message) {
            }
        });

        assertTrue(new Solutions().confirm("really?", true));
        assertEquals(List.of("Warning"), asked, "the warning flag selects the title");
    }

    @Test
    public void withNobodyToAskConfirmationIsDeclinedRatherThanBlockingOnADialog() {
        assertFalse(new Solutions().confirm("really?", false));
    }
}
