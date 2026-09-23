package org.openpnp.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.FontMetrics;
import java.util.List;

import javax.swing.JLabel;

import org.junit.jupiter.api.Test;

/**
 * A form's labels break into lines of the label column's width themselves: HTML in Swing kept a
 * run of Chinese on one line, and "停靠时全部升到 Safe Z" was cut at the column's edge.
 */
public class FormLabelWrapTest {
    private static final FontMetrics METRICS = new JLabel().getFontMetrics(new Font(Font.DIALOG, Font.PLAIN, 12));

    @Test
    public void chineseBreaksBetweenCharactersAndWordsStayWhole() {
        String text = "\u505c\u9760\u65f6\u5168\u90e8\u5347\u5230 Safe Z";
        int width = METRICS.stringWidth("\u505c\u9760\u65f6\u5168\u90e8\u5347\u5230");
        List<String> lines = Forms.Grid.wrap(text, METRICS, width);
        assertTrue(lines.size() >= 2, lines.toString());
        for (String line : lines) {
            assertTrue(METRICS.stringWidth(line) <= width, line);
        }
        assertEquals(text.replace(" ", ""), String.join("", lines).replace(" ", ""));
        assertTrue(lines.stream().noneMatch(line -> line.endsWith("Saf") || line.startsWith("e ")),
                "a word is not split: " + lines);
    }

    @Test
    public void aShortLabelIsOneLine() {
        assertEquals(List.of("\u540d\u79f0"), Forms.Grid.wrap("\u540d\u79f0", METRICS, 112));
    }
}
