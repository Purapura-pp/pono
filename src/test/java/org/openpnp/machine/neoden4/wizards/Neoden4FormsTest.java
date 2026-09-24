package org.openpnp.machine.neoden4.wizards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Point;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.machine.neoden4.NeoDen4Driver;
import org.openpnp.machine.neoden4.NeoDen4FeederActuator;
import org.openpnp.machine.neoden4.Neoden4Camera;
import org.openpnp.machine.neoden4.Neoden4Feeder;
import org.openpnp.machine.neoden4.Neoden4Signaler;
import org.openpnp.machine.neoden4.Neoden4SwitcherCamera;
import org.openpnp.model.Configuration;
import org.openpnp.spi.Camera;

/**
 * The NeoDen4's forms, P9 W6: each shows only what the machine reads and writes it on Apply.
 */
public class Neoden4FormsTest {
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

    private static FormWizard contained(FormWizard form) {
        form.setWizardContainer(CONTAINER);
        return form;
    }

    @Test
    public void theDriverAndTheCamerasWriteOnApply() {
        NeoDen4Driver driver = new NeoDen4Driver();
        FormWizard form = contained(Neoden4Forms.driver(driver));
        double scale = driver.getScaleFactorX();
        form.setValue("scaleFactorX", "1.0512");
        form.setValue("homeCoordinateY", "12.5");
        assertEquals(scale, driver.getScaleFactorX(), 1e-12, "nothing is written before Apply");
        form.apply();
        assertEquals(1.0512, driver.getScaleFactorX(), 1e-12);
        assertEquals(12.5, driver.getHomeCoordinateY(), 1e-12);

        Neoden4Camera camera = new Neoden4Camera();
        FormWizard cameraForm = contained(Neoden4Forms.camera(camera));
        assertFalse(Form.properties(cameraForm).contains("shiftX"), "the shift the capture never reads is not shown");
        cameraForm.setValue("timeout", "2500");
        cameraForm.apply();
        assertEquals(2500, camera.getTimeout());

        Neoden4SwitcherCamera switcher = new Neoden4SwitcherCamera();
        FormWizard switcherForm = contained(Neoden4Forms.switcher(switcher));
        switcherForm.setValue("switcher", "2");
        switcherForm.setValue("exposure", "40");
        switcherForm.setValue("gain", "3");
        switcherForm.apply();
        assertEquals(2, switcher.getSwitcher());
        assertEquals(40, switcher.getExposure());
        assertEquals(3, switcher.getGain());
    }

    @Test
    public void theFeederActuatorShowsWhatItHas() {
        NeoDen4FeederActuator actuator = new NeoDen4FeederActuator();
        FormWizard form = contained(Neoden4Forms.feederActuator(actuator));
        assertFalse(Form.properties(form).contains("feedLength"), "the feed length is the actuation's value, not a setting");
        form.setValue("feederId", "7");
        form.setValue("peelLength", "80");
        form.apply();
        assertEquals(7, actuator.getFeederId());
        assertEquals(80, actuator.getPeelLength());
    }

    @Test
    public void theFeederWritesItsVisionAndCountsItsAreaFromTheMiddle() {
        Neoden4Feeder feeder = new Neoden4Feeder();
        FormWizard form = contained(Neoden4Forms.feeder(feeder));
        form.setValue("partRotationInTape", "90");
        form.set("visionEnabled", true);
        form.setValue("aoiX", "-40");
        form.setValue("aoiWidth", "80");
        form.apply();
        assertEquals(90, feeder.getPartRotationInTape());
        assertTrue(feeder.getVision().isEnabled());
        assertEquals(-40, feeder.getVision().getAreaOfInterest().getX());
        assertEquals(80, feeder.getVision().getAreaOfInterest().getWidth());

        Camera camera = mock(Camera.class);
        when(camera.getWidth()).thenReturn(1280);
        when(camera.getHeight()).thenReturn(1024);
        Point origin = new Neoden4Forms.Bean(feeder).origin(camera);
        assertEquals(640, origin.x, "the middle of the width, as the feeder counts it");
        assertEquals(512, origin.y);
    }

    @Test
    public void theBuzzerHasNothingToSet() {
        FormWizard form = contained(Neoden4Forms.signaler(new Neoden4Signaler()));
        assertTrue(Form.properties(form).isEmpty(), Form.properties(form).toString());
    }
}
