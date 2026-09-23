package org.openpnp.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.JButton;
import javax.swing.JRootPane;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;

import org.junit.jupiter.api.Test;
import org.openpnp.gui.theme.PonoDarkLaf;
import org.openpnp.gui.theme.PonoLightLaf;

/**
 * A dialog's default button - the one Enter presses - looks like the variant it is. The look and
 * feel paints default buttons in colours of its own: a primary button came out white with an
 * accent border in the light theme, and a Cancel with no fill came out filled with the accent in
 * the dark one.
 */
public class DefaultButtonTest {
    private static final Color UNDER = new Color(0xff00ff);

    /** The colour of the button's fill: left of its text, halfway down. */
    private static int fill(JButton button) {
        button.setSize(button.getPreferredSize());
        button.doLayout();
        BufferedImage image = new BufferedImage(button.getWidth(), button.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(UNDER);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        button.paint(g);
        g.dispose();
        return image.getRGB(5, image.getHeight() / 2) & 0xffffff;
    }

    private static JButton asDefault(JButton button) {
        JRootPane root = new JRootPane();
        root.getContentPane().setLayout(new FlowLayout());
        root.getContentPane().add(button);
        root.setDefaultButton(button);
        assertTrue(button.isDefaultButton());
        return button;
    }

    private static void check(LookAndFeel laf) throws Exception {
        LookAndFeel before = UIManager.getLookAndFeel();
        try {
            UIManager.setLookAndFeel(laf);
            int primary = fill(Ui.button("Close", null, Ui.Size.Sm, Ui.Variant.Primary));
            assertEquals(UIManager.getColor("Pono.accent").getRGB() & 0xffffff, primary, laf.getName());
            assertEquals(primary, fill(asDefault(Ui.button("Close", null, Ui.Size.Sm, Ui.Variant.Primary))),
                    laf.getName() + ": the default primary button");
            int danger = fill(Ui.button("Delete", null, Ui.Size.Sm, Ui.Variant.SolidDanger));
            assertEquals(danger, fill(asDefault(Ui.button("Delete", null, Ui.Size.Sm, Ui.Variant.SolidDanger))),
                    laf.getName() + ": the default red button");
            assertEquals(UNDER.getRGB() & 0xffffff,
                    fill(asDefault(Ui.button("Cancel", null, Ui.Size.Sm, Ui.Variant.Ghost))),
                    laf.getName() + ": a default Cancel has no fill");
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
