package org.openpnp.machine.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;

import javax.swing.JTextField;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.machine.reference.wizards.NozzleForm;
import org.openpnp.model.Configuration;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;

/**
 * A nozzle's settings change behind a form of the machine settings page that was built before and
 * is not told - in the element tree's form for the same nozzle, by a calibration: applying another
 * field of the form keeps what was set, and the form shows it once it is read again.
 */
public class ChangedBehindTheFormTest {
    @TempDir
    Path tempDir;

    private ReferenceMachine machine;
    private ReferenceNozzle nozzle;

    private static final Location MEASURED = new Location(LengthUnit.Millimeters, 1.25, -2.5, 0, 0);

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
        machine = (ReferenceMachine) Configuration.get().getMachine();
        nozzle = (ReferenceNozzle) machine.getDefaultHead().getDefaultNozzle();
    }

    @AfterEach
    public void tearDown() throws Exception {
        machine.close();
    }

    @Test
    public void applyingTheNameKeepsADwellSetElsewhere() {
        FormWizard form = NozzleForm.settings(nozzle);
        form.setWizardContainer(CONTAINER);
        int dwell = nozzle.getPickDwellMilliseconds() + 123;
        nozzle.setPickDwellMilliseconds(dwell);

        field(form, nozzle.getName()).setText("N9");
        form.apply();

        assertEquals("N9", nozzle.getName());
        assertEquals(dwell, nozzle.getPickDwellMilliseconds(), "the dwell on screen was not edited");
    }

    @Test
    public void applyingTheNameKeepsTheOffsetACalibrationMeasured() {
        FormWizard form = NozzleForm.settings(nozzle);
        form.setWizardContainer(CONTAINER);
        nozzle.setHeadOffsets(MEASURED);

        field(form, nozzle.getName()).setText("N9");
        form.apply();

        assertEquals(MEASURED, nozzle.getHeadOffsets());
    }

    @Test
    public void readAgainTheFormShowsWhatWasSetElsewhere() {
        FormWizard form = NozzleForm.settings(nozzle);
        form.setWizardContainer(CONTAINER);
        int dwell = nozzle.getPickDwellMilliseconds() + 123;
        nozzle.setPickDwellMilliseconds(dwell);
        nozzle.setHeadOffsets(MEASURED);

        form.reload();

        field(form, String.valueOf(dwell));
        Location shown = form.location("headOffsets").convertToUnits(LengthUnit.Millimeters);
        assertEquals(1.25, shown.getX(), 1e-6);
        assertEquals(-2.5, shown.getY(), 1e-6);
    }

    private static JTextField field(Container root, String text) {
        for (Component c : root.getComponents()) {
            if (c instanceof JTextField && ((JTextField) c).getText().equals(text)) {
                return (JTextField) c;
            }
            if (c instanceof Container) {
                try {
                    return field((Container) c, text);
                }
                catch (AssertionError e) {
                    // Not in there.
                }
            }
        }
        throw new AssertionError("no field showing " + text);
    }

    private static final WizardContainer CONTAINER = new WizardContainer() {
        @Override
        public void wizardCompleted(Wizard wizard) {
        }

        @Override
        public void wizardCancelled(Wizard wizard) {
        }
    };
}
