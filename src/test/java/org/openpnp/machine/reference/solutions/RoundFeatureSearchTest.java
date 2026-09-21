package org.openpnp.machine.reference.solutions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The hysteresis map's search for something round on the table, on a synthetic table: a dark
 * hole well off the frame's centre, a square of the same size, and a hole cut by the frame's
 * edge. The first version of the search looked in the middle 2 mm of a 19 mm frame and would
 * have found none of them.
 */
public class RoundFeatureSearchTest {
    @BeforeAll
    public static void loadOpenCv() {
        nu.pattern.OpenCV.loadShared();
    }

    /** A grey anodised plate, 1920 by 1080, with what is drawn on it. */
    private static BufferedImage plate(java.util.function.Consumer<Graphics2D> draw) {
        BufferedImage image = new BufferedImage(1920, 1080, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(150, 150, 150));
            g.fillRect(0, 0, 1920, 1080);
            draw.accept(g);
        }
        finally {
            g.dispose();
        }
        return image;
    }

    @Test
    public void aHoleFarFromTheCentreIsFoundAndASquareIsNot() {
        BufferedImage image = plate(g -> {
            g.setColor(new Color(25, 25, 25));
            g.fillOval(1500 - 165, 200 - 165, 330, 330); // a 3.3 mm hole, top right
            g.fillRect(400 - 165, 800 - 165, 330, 330); // a square of the same size, bottom left
        });
        List<org.opencv.core.MatOfPoint> considered = new ArrayList<>();

        double[] best = MachineDiagnostics.bestRoundFeature(image, 150, 650, considered);

        assertNotNull(best, "the hole is anywhere in the frame, not only at its centre");
        assertEquals(1500, best[0], 2.0);
        assertEquals(200, best[1], 2.0);
        assertEquals(330, best[2], 8.0);
        assertTrue(best[3] > 0.85, "circularity " + best[3]);
        // Both shapes are of a size worth considering, in each threshold polarity.
        assertTrue(considered.size() >= 2, "considered " + considered.size());
        for (org.opencv.core.MatOfPoint c : considered) {
            c.release();
        }
    }

    @Test
    public void aBrightScrewHeadOnADarkPlateIsFoundToo() {
        BufferedImage image = plate(g -> {
            g.setColor(new Color(30, 30, 30));
            g.fillRect(0, 0, 1920, 1080);
            g.setColor(new Color(230, 230, 230));
            g.fillOval(700 - 280, 540 - 280, 560, 560); // a 5.6 mm head
        });
        List<org.opencv.core.MatOfPoint> considered = new ArrayList<>();

        double[] best = MachineDiagnostics.bestRoundFeature(image, 150, 650, considered);

        assertNotNull(best);
        assertEquals(700, best[0], 2.0);
        assertEquals(540, best[1], 2.0);
        for (org.opencv.core.MatOfPoint c : considered) {
            c.release();
        }
    }

    @Test
    public void aHoleCutByTheFrameEdgeOrOutOfSizeIsNotTaken() {
        BufferedImage image = plate(g -> {
            g.setColor(new Color(25, 25, 25));
            g.fillOval(1920 - 100 - 165, 540 - 165, 330, 330); // cut by the right edge's margin
            g.fillOval(300 - 40, 300 - 40, 80, 80); // too small: 0.8 mm
        });
        List<org.opencv.core.MatOfPoint> considered = new ArrayList<>();

        assertNull(MachineDiagnostics.bestRoundFeature(image, 150, 650, considered));
        for (org.opencv.core.MatOfPoint c : considered) {
            c.release();
        }
    }

    @Test
    public void theNearestOfSeveralHolesIsTaken() {
        BufferedImage image = plate(g -> {
            g.setColor(new Color(25, 25, 25));
            g.fillOval(300 - 165, 300 - 165, 330, 330);
            g.fillOval(1100 - 165, 600 - 165, 330, 330); // nearest the centre (960, 540)
            g.fillOval(1700 - 165, 900 - 165, 330, 330);
        });
        List<org.opencv.core.MatOfPoint> considered = new ArrayList<>();

        double[] best = MachineDiagnostics.bestRoundFeature(image, 150, 650, considered);

        assertNotNull(best);
        assertEquals(1100, best[0], 2.0);
        assertEquals(600, best[1], 2.0);
        for (org.opencv.core.MatOfPoint c : considered) {
            c.release();
        }
    }

    @Test
    public void targetsAreReadAsPairs() {
        List<double[]> targets = MachineDiagnostics.parseTargets("120, 80; 300,80 ; 120 400;junk; 5");
        assertEquals(3, targets.size());
        assertEquals(300, targets.get(1)[0], 1e-9);
        assertEquals(400, targets.get(2)[1], 1e-9);
        assertTrue(MachineDiagnostics.parseTargets("").isEmpty());
        assertTrue(MachineDiagnostics.parseTargets(null).isEmpty());
    }
}
