package org.openpnp.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.nio.file.Path;

import javax.swing.LookAndFeel;
import javax.swing.UIManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.gui.MachineControlsPanel;
import org.openpnp.gui.theme.PonoLightLaf;
import org.openpnp.model.Configuration;

/**
 * Each size of the manual controls fits the height the camera card chooses it for, and the width
 * of its column: the card used to be cut off at the bottom where the image was short, and a size
 * that promises less than it takes would be cut off again.
 */
public class JogCardTest {
    @TempDir
    Path tempDir;

    private LookAndFeel before;
    private JogCard jog;

    @BeforeEach
    public void setUp() throws Exception {
        before = UIManager.getLookAndFeel();
        UIManager.setLookAndFeel(new PonoLightLaf());
        // Not loaded: once loaded, the controls reach for the main window, which a test has not got.
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        jog = new JogCard(Configuration.get(), new MachineControlsPanel(Configuration.get(), null));
    }

    @AfterEach
    public void tearDown() throws Exception {
        UIManager.setLookAndFeel(before);
    }

    private Component face() {
        return jog.getComponent(0);
    }

    @Test
    public void everySizeFitsTheHeightItIsChosenForAndItsColumn() {
        for (JogCard.Level level : JogCard.Level.values()) {
            jog.setLevel(level);
            assertTrue(face().getPreferredSize().height <= JogCard.needed(level),
                    level + ": " + face().getPreferredSize().height + " high for " + JogCard.needed(level));
            assertTrue(face().getPreferredSize().width <= jog.columnWidth(),
                    level + ": " + face().getPreferredSize().width + " wide for " + jog.columnWidth());
        }
    }

    @Test
    public void withTheReadoutEverySizeFitsWhatItAddsToo() {
        jog.setReadout(new DroPanel(Configuration.get()));
        for (JogCard.Level level : JogCard.Level.values()) {
            jog.setLevel(level);
            assertTrue(face().getPreferredSize().height <= JogCard.needed(level) + JogCard.DRO_NEEDED,
                    level + ": " + face().getPreferredSize().height);
        }
    }

    @Test
    public void foldedItIsAStripAndSaysWhyWhenItHadNoRoom() {
        jog.setExpanded(false);
        assertEquals(JogCard.FOLDED_WIDTH, jog.columnWidth());
        jog.setExpanded(true);
        jog.setAutoFolded(true);
        assertTrue(jog.showsFolded());
        assertEquals(JogCard.FOLDED_WIDTH, jog.columnWidth());
        jog.setAutoFolded(false);
        assertEquals(JogCard.WIDTH, jog.columnWidth());
    }

    @Test
    public void theFirstLineOffersToConnectAMachineThatIsOff() {
        jog.setLevel(JogCard.Level.Full);
        javax.swing.JButton primary = find(jog, javax.swing.JButton.class, b -> b.getText() != null && !b.getText().isEmpty()
                && b.getIcon() != null && b.getWidth() >= 0 && b.getText().equals(org.openpnp.Translations.getString("TopBar.Connect")));
        assertTrue(primary != null, "Connect, not a greyed out Home");
    }

    private static <T extends Component> T find(Component c, Class<T> type, java.util.function.Predicate<T> test) {
        if (type.isInstance(c) && test.test(type.cast(c))) {
            return type.cast(c);
        }
        if (c instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) c).getComponents()) {
                T found = find(child, type, test);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
