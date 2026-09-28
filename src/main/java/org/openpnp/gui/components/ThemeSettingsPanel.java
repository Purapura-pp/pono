package org.openpnp.gui.components;

import java.util.HashMap;
import java.util.Map;

/**
 * The font sizes a theme is applied at. A stored font size is read back by the name of this
 * class, which is why they are still here; the theme itself is put in place by
 * {@link org.openpnp.gui.theme.Themes#apply}.
 */
public final class ThemeSettingsPanel {
    public enum FontSize {
        SMALLEST(10, 0),
        BELOW_SMALLER(11, 05),
        SMALLER(12, 10),
        BELOW_SMALL(13, 15),
        SMALL(14, 20),
        BELOW_DEFAULT(15, 25),
        DEFAULT(16, 30),
        BELOW_LARGE(17, 35),
        LARGE(18, 40),
        BELOW_LARGER(19, 45),
        LARGER(20, 50),
        BELOW_HUGE(22, 55),
        HUGE(24, 60),
        BELOW_LARGEST(26, 65),
        LARGEST(28, 70);

        private final int size;
        private final int percent;
        private static final Map<Integer, FontSize> percentToSize = new HashMap<>();
        private static final Map<Integer, FontSize> fontSizeToSize = new HashMap<>();

        FontSize(int size, int percent) {
            this.size = size;
            this.percent = percent;
        }

        public int getSize() {
            return size;
        }

        public int getPercent() {
            return percent;
        }

        static {
            for (FontSize fSize : FontSize.values()) {
                percentToSize.put(fSize.getPercent(), fSize);
                fontSizeToSize.put(fSize.getSize(), fSize);
            }
        }

        public static FontSize fromPercent(int percent) {
            FontSize fSize = percentToSize.get(percent);
            if (fSize == null) {
                return DEFAULT;
            }
            return fSize;
        }

        public static FontSize fromSize(int size) {
            FontSize fSize = fontSizeToSize.get(size);
            if (fSize == null) {
                return DEFAULT;
            }
            return fSize;
        }
    }

    private ThemeSettingsPanel() {
    }
}
