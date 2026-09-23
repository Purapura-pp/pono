package org.openpnp.machine.reference.feeder.wizards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.JTextField;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.Translations;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.machine.reference.feeder.ReferenceStripFeeder;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;

/**
 * The strip feeder's form: the mockup's four sections, the pick height written into the first
 * hole's Z and nothing else, a tape width preset put in by a click, the parts in the strip
 * worked out from its holes, and what an automatic setup wrote shown when the form reloads.
 */
public class StripFeederFormTest {
    private static final WizardContainer CONTAINER = new WizardContainer() {
        @Override
        public void wizardCompleted(Wizard wizard) {
        }

        @Override
        public void wizardCancelled(Wizard wizard) {
        }
    };

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
    }

    private static ReferenceStripFeeder strip() {
        ReferenceStripFeeder feeder = new ReferenceStripFeeder();
        feeder.setName("F-08");
        feeder.setVisionEnabled(false);
        feeder.setTapeWidth(new Length(8, LengthUnit.Millimeters));
        feeder.setPartPitch(new Length(4, LengthUnit.Millimeters));
        feeder.setReferenceHoleLocation(new Location(LengthUnit.Millimeters, 10, 20, -5, 0));
        feeder.setLastHoleLocation(new Location(LengthUnit.Millimeters, 50, 20, -4, 0));
        return feeder;
    }

    private static FormWizard form(ReferenceStripFeeder feeder) {
        FormWizard form = StripFeederForm.build(feeder);
        form.setWizardContainer(CONTAINER);
        return form;
    }

    private static <T extends Component> List<T> all(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                found.add(type.cast(c));
            }
            if (c instanceof Container) {
                found.addAll(all((Container) c, type));
            }
        }
        return found;
    }

    private static JTextField field(FormWizard form, String text) {
        for (JTextField field : all(form, JTextField.class)) {
            if (field.getText().equals(text)) {
                return field;
            }
        }
        throw new AssertionError("no field showing " + text);
    }

    private static AbstractButton button(FormWizard form, String text) {
        for (AbstractButton button : all(form, AbstractButton.class)) {
            if (text.equals(button.getText())) {
                return button;
            }
        }
        throw new AssertionError("no button saying " + text);
    }

    @Test
    public void theFormHasTheMockupsSections() {
        FormWizard form = form(strip());
        assertEquals(List.of("name", "slotName", "part", "enabled", "tapeWidth", "partPitch", "rotationInTape",
                "referenceHole", "lastHole", "pickZ", "visionEnabled", "extrapolationDistance",
                "parallaxDiameter", "parallaxAngle", "feedCount", "maxFeedCount", "partsLeft", "lowCount",
                "feedRetryCount", "pickRetryCount", "feedOptions"), Form.properties(form));
    }

    @Test
    public void thePickHeightIsTheFirstHolesZAndTheHolesKeepTheirOwn() {
        ReferenceStripFeeder feeder = strip();
        FormWizard form = form(feeder);
        field(form, "-5.000").setText("-6.500");
        field(form, "10.000").setText("11.000");
        form.apply();

        Location first = feeder.getReferenceHoleLocation().convertToUnits(LengthUnit.Millimeters);
        assertEquals(11, first.getX(), 1e-9);
        assertEquals(20, first.getY(), 1e-9);
        assertEquals(-6.5, first.getZ(), 1e-9);
        assertEquals(-4, feeder.getLastHoleLocation().getZ(), 1e-9, "the second hole's height is its own");
    }

    @Test
    public void aPresetPutsItsWidthIn() {
        ReferenceStripFeeder feeder = strip();
        FormWizard form = form(feeder);
        button(form, "12").doClick();
        field(form, "12.000");
        form.apply();
        assertEquals(12, feeder.getTapeWidth().convertToUnits(LengthUnit.Millimeters).getValue(), 1e-9);
    }

    @Test
    public void thePartsInTheStripAreCountedFromItsHoles() {
        ReferenceStripFeeder feeder = strip();
        assertEquals(11, StripFeederForm.partsInStrip(feeder), "40 mm at 4 mm, both ends counted");

        FormWizard form = form(feeder);
        button(form, Translations.getString("StripFeederForm.MaxFeedCount.Count")).doClick();
        form.apply();
        assertEquals(11, feeder.getMaxFeedCount());
        assertEquals(11, feeder.getPartsLeft());
    }

    @Test
    public void whatChangedBehindTheFormIsShownWhenItReloads() {
        ReferenceStripFeeder feeder = strip();
        FormWizard form = form(feeder);
        feeder.setReferenceHoleLocation(new Location(LengthUnit.Millimeters, 30, 40, -5, 0));
        form.reload();
        field(form, "30.000");
        field(form, "40.000");
        assertTrue(all(form, JTextField.class).stream().noneMatch(f -> f.getText().equals("10.000")));
    }
}
