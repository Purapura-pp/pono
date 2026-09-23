package org.openpnp.gui.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.formdev.flatlaf.extras.FlatSVGIcon;

/**
 * The ruler's own judgement, which every later stage is accepted by: a check that lets English
 * through, or flags a part number, would make "zero warnings" mean nothing.
 */
public class UiAuditTest {
    private final UiAudit.Rules rules = new UiAudit.Rules(Set.of("Top", "mm", "px", "Ctrl", "Shift"),
            List.of("Safe Z", "G-code"), Set.of("demo-board.job.xml", "demo-board", "NullDriver"),
            Set.of(11.5f, 12.5f, 13f), Set.of("Placement", "ReferenceStripFeeder"));

    @Test
    public void englishWordsAreFoundAndClassNamesAreMarked() {
        assertEquals("\u7c7b\u540d\u3000\u300cPlacement (\u8d34\u7247\u4f4d)\u300d",
                UiAudit.latin("Placement (\u8d34\u7247\u4f4d)", rules));
        assertEquals("\u300cDefault\u300d", UiAudit.latin("Default", rules));
        assertNotNull(UiAudit.latin("<html><b>Include Solved?</b> \u663e\u793a</html>", rules));
    }

    @Test
    public void tradeTermsCodesAndTheUsersNamesAreNotEnglishText() {
        assertNull(UiAudit.latin("\u62ac\u5230 Safe Z", rules));
        assertNull(UiAudit.latin("G-code \u6307\u4ee4", rules));
        assertNull(UiAudit.latin("F-08 \u00b7 \u6599\u5e26\u98de\u8fbe \u00b7 R0603-10K", rules));
        assertNull(UiAudit.latin("Top \u00b7 \u5934 H1 \u00b7 1 px = 0.0139 mm", rules));
        assertNull(UiAudit.latin("demo-board.job.xml", rules));
        assertNull(UiAudit.latin("Ctrl+Shift+X", rules), "one-letter keys and listed key names");
        assertNull(UiAudit.latin("X / Y", rules));
    }

    @Test
    public void contrastIsTheWcagRatio() {
        assertEquals(21.0, UiAudit.contrastRatio(Color.BLACK, Color.WHITE), 0.01);
        assertEquals(1.0, UiAudit.contrastRatio(Color.GRAY, Color.GRAY), 0.001);
        // Black text on the dark theme's surface: the case the check is there for.
        assertTrue(UiAudit.contrastRatio(Color.BLACK, new Color(0x141920)) < 1.3);
        // The mockups' muted text on the dark surface passes the threshold the check uses.
        assertTrue(UiAudit.contrastRatio(new Color(0x6c7787), new Color(0x141920)) > 3.0);
    }

    @Test
    public void onlyIconsFromTheNewSetCountAsNew() {
        assertNull(UiAudit.legacyIcon(new FlatSVGIcon("icons/pono/board.svg", 18, 18)));
        assertEquals("icons/board.svg", UiAudit.legacyIcon(new FlatSVGIcon("icons/board.svg", 18, 18)));
        assertNull(UiAudit.legacyIcon(null));
    }
}
