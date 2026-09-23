package org.openpnp.gui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import java.util.List;

import javax.swing.JTable;

import org.junit.jupiter.api.Test;

/** The line an empty table shows, broken to its width. */
public class EmptyTableTextTest {
    private static FontMetrics metrics() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        return image.createGraphics().getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
    }

    @Test
    public void shortTextStaysOnOneLine() {
        assertEquals(List.of("No feeders yet."), AutoSelectTextTable.wrap("No feeders yet.", metrics(), 400));
    }

    @Test
    public void longTextBreaksAtSpacesAndEveryLineFits() {
        FontMetrics fm = metrics();
        String text = "No placements on this board. Import a placement file, or add one above.";
        List<String> lines = AutoSelectTextTable.wrap(text, fm, 180);
        assertTrue(lines.size() > 1, lines.toString());
        for (String line : lines) {
            assertTrue(fm.stringWidth(line) <= 180, line);
            assertEquals(line.trim(), line);
        }
        assertEquals(text, String.join(" ", lines));
    }

    @Test
    public void chineseBreaksAnywhere() {
        FontMetrics fm = metrics();
        String text = "\u8fd9\u5757\u677f\u4e0a\u8fd8\u6ca1\u6709\u8d34\u7247\u4f4d\u3002\u5bfc\u5165\u8d34\u7247\u5750\u6807\u6587\u4ef6\u3002";
        List<String> lines = AutoSelectTextTable.wrap(text, fm, 60);
        assertTrue(lines.size() > 1, lines.toString());
        assertEquals(text, String.join("", lines));
    }

    @Test
    public void settingTheTextLetsAnEmptyTableFillItsViewport() {
        JTable table = new AutoSelectTextTable();
        table.setFillsViewportHeight(false);
        AutoSelectTextTable.setEmptyText(table, "Nothing here");
        assertTrue(table.getFillsViewportHeight());
        assertEquals("Nothing here", table.getClientProperty(AutoSelectTextTable.EMPTY_TEXT));
    }
}
