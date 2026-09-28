package org.openpnp.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.JButton;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;

import org.junit.jupiter.api.Test;
import org.openpnp.gui.theme.PonoDarkLaf;
import org.openpnp.gui.theme.PonoLightLaf;

/**
 * A disabled button is painted in disabled colours of its own, and a focused one keeps its fill.
 * <p>
 * Disabled buttons were the same colours at 45 % opacity: white words on a faded accent or green
 * all but disappeared. A focused button took FlatLaf Light's near-white focused fill, which took
 * the white words off Connect and Go to machine settings, and the red off Stop machine.
 */
public class ButtonStatesTest {
    private static final Color UNDER = new Color(0xff00ff);

    /** The colour of the button's fill: left of its text, halfway down. */
    private static Color fill(JButton button) {
        button.setSize(button.getPreferredSize());
        button.doLayout();
        BufferedImage image = new BufferedImage(button.getWidth(), button.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(UNDER);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        button.paint(g);
        g.dispose();
        return new Color(image.getRGB(5, image.getHeight() / 2));
    }

    private static Color disabled(Ui.Variant variant) {
        JButton button = Ui.button("Start", null, Ui.Size.Sm, variant);
        button.setEnabled(false);
        return fill(button);
    }

    private static void near(Color expected, Color actual, String what) {
        assertTrue(Math.abs(expected.getRed() - actual.getRed()) <= 3
                && Math.abs(expected.getGreen() - actual.getGreen()) <= 3
                && Math.abs(expected.getBlue() - actual.getBlue()) <= 3, what + ": " + expected + " expected, " + actual);
    }

    private static void check(LookAndFeel laf) throws Exception {
        LookAndFeel before = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel(laf);
            Color surface = UIManager.getColor("Pono.surface");
            near(Ui.mix(UIManager.getColor("Pono.accent"), surface, 0.13), disabled(Ui.Variant.Primary),
                    laf.getName() + ": a disabled primary button");
            near(Ui.mix(UIManager.getColor("Pono.ok"), surface, 0.15), disabled(Ui.Variant.PrimaryOk),
                    laf.getName() + ": a disabled Start");
            near(Ui.mix(UIManager.getColor("Pono.err"), surface, 0.12), disabled(Ui.Variant.SolidDanger),
                    laf.getName() + ": a disabled red button");
            // Painted at an opacity, the colour under it showed through.
            assertEquals(UIManager.getColor("Pono.surface2").getRGB(), disabled(Ui.Variant.Default).getRGB(),
                    laf.getName() + ": a disabled ordinary button");
            // Focus is the ring Ui paints round a button; the theme leaves the fill alone.
            assertNull(UIManager.getColor("Button.focusedBackground"), laf.getName());
            assertNull(UIManager.getColor("Button.default.focusedBackground"), laf.getName());
        }
        finally {
            UIManager.setLookAndFeel(before);
        }
    }

    @Test
    public void inTheLightTheme() throws Exception {
        check(new PonoLightLaf());
    }

    @Test
    public void inTheDarkTheme() throws Exception {
        check(new PonoDarkLaf());
    }
}
