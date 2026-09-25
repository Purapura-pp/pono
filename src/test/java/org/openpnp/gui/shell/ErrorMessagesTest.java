package org.openpnp.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * What an error dialog says: the known messages in the user's words with advice, the rest as they
 * are, and the same error within a few seconds counted rather than shown again.
 */
public class ErrorMessagesTest {
    private static final Locale ZH = Locale.SIMPLIFIED_CHINESE;

    @Test
    public void aKnownMessageIsSaidInChineseWithWhatToDo() {
        ErrorMessages.Explained e = ErrorMessages.explain("Error", "Feeder F-08 has no part.", ZH);
        assertEquals("飞达 F-08 未指定元件。", e.what);
        assertNotNull(e.more);
        assertTrue(e.more.contains("飞达页"));
    }

    @Test
    public void thePartsOfTheMessageGoIntoTheSentence() {
        ErrorMessages.Explained e = ErrorMessages.explain("Error",
                "Can't move Y to 350.12, higher than soft limit 350.00.", ZH);
        assertEquals("Y 轴目标位置 350.12 高于软限位 350。", e.what);
    }

    /** As the motion planner writes it: every coordinate with %f and its unit right after. */
    @Test
    public void theNumbersArePutAsAPersonWritesThem() {
        ErrorMessages.Explained e = ErrorMessages.explain("Error",
                "Can't move y to 350.120000mm, higher than soft limit 350.000000mm.", ZH);
        assertEquals("y 轴目标位置 350.12 mm 高于软限位 350 mm。", e.what);
        assertEquals("0.05 mm", ErrorMessages.tidy("0.050000mm"));
        assertEquals("-12.5 mm", ErrorMessages.tidy("-12.500mm"));
        assertEquals("100", ErrorMessages.tidy("100"));
        assertEquals("1.5 in", ErrorMessages.tidy("1.5in"));
        assertEquals("N1", ErrorMessages.tidy("N1"));
    }

    @Test
    public void anUnknownMessageIsShownAsItIs() {
        ErrorMessages.Explained e = ErrorMessages.explain("Camera Error", "Something nobody wrote a rule for", ZH);
        assertEquals("Something nobody wrote a rule for", e.what);
        assertNull(e.more);
        assertEquals("Camera Error", e.title, "a title that says something is kept");
    }

    @Test
    public void aTitleThatSaysNothingBecomesOneThatDoes() {
        ErrorMessages.Explained e = ErrorMessages.explain("Error", "x", ZH);
        assertTrue(!e.title.equals("Error"));
    }

    @Test
    public void anEmptyMessageSaysThatNoReasonWasGiven() {
        ErrorMessages.Explained e = ErrorMessages.explain(null, "  ", ZH);
        assertNotNull(e.what);
        assertTrue(!e.what.trim().isEmpty());
    }

    @Test
    public void otherLanguagesSeeTheOriginal() {
        ErrorMessages.Explained e = ErrorMessages.explain("Error", "Feeder F-08 has no part.", Locale.GERMAN);
        assertEquals("Feeder F-08 has no part.", e.what);
    }

    @Test
    public void theSameErrorWithinTheWindowIsCountedNotShown() {
        ErrorMessages.Repeats repeats = new ErrorMessages.Repeats(5000);
        assertEquals(0, repeats.seen("a", 1000), "the first is shown");
        assertEquals(1, repeats.seen("a", 2000), "the second is not");
        assertEquals(2, repeats.seen("a", 3000));
        assertEquals(3, repeats.count("a"));
        assertEquals(0, repeats.seen("b", 3000), "another error is shown");
        assertEquals(0, repeats.seen("a", 9000), "after the window it is shown again");
        assertEquals(1, repeats.count("a"));
    }
}
