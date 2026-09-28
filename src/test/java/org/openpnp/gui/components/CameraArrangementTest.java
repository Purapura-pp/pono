package org.openpnp.gui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;

import org.junit.jupiter.api.Test;

/**
 * The pictures fill what they are given without black bars, and two go side by side where that
 * gives the larger pictures: the "side by side" choice put them one over the other whatever the
 * room, two small pictures in a wide window.
 */
public class CameraArrangementTest {
    private static final double FOUR_THREE = 4.0 / 3.0;

    @Test
    public void onePictureIsAsLargeAsTheRoomAllowsUnderItsBar() {
        CameraArrangement.Result wide = CameraArrangement.arrange(1000, 422, new double[] { FOUR_THREE });
        Rectangle picture = wide.picture(0);
        assertEquals(400, picture.height, "the height limits it");
        assertEquals(533, picture.width);
        assertEquals(422, wide.size.height, "no room left above or below");

        CameraArrangement.Result tall = CameraArrangement.arrange(400, 1000, new double[] { FOUR_THREE });
        assertEquals(400, tall.picture(0).width, "the width limits it");
        assertEquals(300, tall.picture(0).height);
        assertEquals(400, tall.size.width);
    }

    @Test
    public void twoGoSideBySideInAWideRoomAndOneOverTheOtherInATallOne() {
        double[] two = { FOUR_THREE, FOUR_THREE };
        CameraArrangement.Result wide = CameraArrangement.arrange(1003, 535, two);
        assertTrue(wide.sideBySide);
        assertEquals(wide.slots.get(0).y, wide.slots.get(1).y, "on one line");
        assertEquals(497, wide.picture(0).width, "half the width, less the gap");

        CameraArrangement.Result tall = CameraArrangement.arrange(500, 900, two);
        assertFalse(tall.sideBySide);
        assertEquals(tall.slots.get(0).x, tall.slots.get(1).x, "one over the other");
    }

    @Test
    public void aCardALittleTallerStillHasThemSideBySide() {
        double[] topAndBottom = { 1.951, FOUR_THREE };
        CameraArrangement.Result r = CameraArrangement.arrange(691, 516, topAndBottom);
        assertTrue(r.sideBySide, "one over the other would be under a fifth larger");
    }

    @Test
    public void picturesOfDifferentShapesShareAHeightSideBySide() {
        CameraArrangement.Result r = CameraArrangement.arrange(1200, 400, new double[] { FOUR_THREE, 16.0 / 9.0 });
        assertTrue(r.sideBySide);
        assertEquals(r.picture(0).height, r.picture(1).height);
        assertTrue(r.picture(1).width > r.picture(0).width, "the wider picture is wider");
        assertTrue(r.size.width <= 1200);
    }
}
