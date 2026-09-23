package org.openpnp.gui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The themes and the Tokens class say what design/mockups/mock.css says. A colour changed in the
 * stylesheet and not regenerated would otherwise stay the old colour in the program, which is how
 * the dark theme came to use the card colour for the window.
 */
public class DesignTokensTest {
    private static final String REGENERATE = "out of date with design/mockups/mock.css: run tools/design-tokens/generate.ps1";

    @Test
    public void theThemesCarryTheStylesheetsTokens() throws Exception {
        DesignTokens tokens = DesignTokens.read(DesignTokens.CSS);
        for (Map.Entry<java.nio.file.Path, String> theme : Map.of(DesignTokens.DARK, "dark",
                DesignTokens.LIGHT, "light").entrySet()) {
            String block = DesignTokens.generatedBlock(DesignTokens.readFile(theme.getKey()));
            assertNotNull(block, theme.getKey() + " has no generated block: " + REGENERATE);
            assertEquals(tokens.properties(theme.getValue()), block, theme.getKey() + " is " + REGENERATE);
        }
    }

    @Test
    public void theTokensClassCarriesTheStylesheetsTokens() throws Exception {
        DesignTokens tokens = DesignTokens.read(DesignTokens.CSS);
        assertEquals(tokens.java(), DesignTokens.readFile(DesignTokens.JAVA), DesignTokens.JAVA + " is " + REGENERATE);
    }

    /** Every icon of the mockups' sprite is a file of the program's icon set, drawn the same. */
    @Test
    public void theIconSetIsTheMockupsSprite() throws Exception {
        for (Map.Entry<String, String> icon : DesignTokens.icons().entrySet()) {
            java.nio.file.Path file = DesignTokens.ICONS.resolve(icon.getKey());
            assertEquals(icon.getValue(), java.nio.file.Files.exists(file) ? DesignTokens.readFile(file) : null,
                    file + " is " + REGENERATE);
        }
    }

    @Test
    public void coloursAreReadAsTheStylesheetWritesThem() throws Exception {
        assertEquals("#4f8cff29", DesignTokens.colour("rgba(79, 140, 255, .16)"));
        assertEquals("#ffffff", DesignTokens.colour("#fff"));
        assertEquals("#0d1015", DesignTokens.colour("#0D1015"));
        DesignTokens tokens = DesignTokens.read(DesignTokens.CSS);
        // The window behind the cards, and the cards: the two the dark theme had swapped.
        assertEquals("#0d1015", tokens.colours("dark").get("bg"));
        assertEquals("#141920", tokens.colours("dark").get("surface"));
        assertEquals("#f5f7fa", tokens.colours("light").get("surface-2"));
        assertEquals("Pono.surface2", DesignTokens.key("surface-2"));
        assertEquals("Pono.borderStrong", DesignTokens.key("border-strong"));
    }
}
