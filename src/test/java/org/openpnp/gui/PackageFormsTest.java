package org.openpnp.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.model.Configuration;
import org.openpnp.model.Package;
import org.openpnp.model.VisionCompositing;

/**
 * The package page's forms: settings, vision and compositing are declarative, and each writes to
 * the package on Apply.
 */
public class PackageFormsTest {
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

    private Configuration configuration;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        configuration = Configuration.get();
        configuration.load();
    }

    private static FormWizard contained(FormWizard form) {
        form.setWizardContainer(CONTAINER);
        return form;
    }

    @Test
    public void theSettingsFormWritesTheVacuumLevels() {
        Package pkg = new Package("SOT-23");
        FormWizard form = contained(PackageForm.build(configuration, pkg));
        assertEquals(List.of("id", "description", "tapeSpecification", "pickVacuumLevel", "placeBlowOffLevel"),
                Form.properties(form));
        form.setValue("description", "small outline");
        form.setValue("pickVacuumLevel", "42.5");
        form.apply();
        assertEquals("small outline", pkg.getDescription());
        assertEquals(42.5, pkg.getPickVacuumLevel(), 1e-9);
    }

    @Test
    public void theVisionFormWritesTheFootprint() {
        Package pkg = new Package("QFN-32");
        FormWizard form = contained(PackageVisionForm.build(configuration, pkg));
        assertTrue(Form.properties(form).containsAll(List.of("units", "bodyWidth", "bodyHeight", "padCount",
                "padPitch")), Form.properties(form).toString());
        form.setValue("bodyWidth", "5");
        form.setValue("padCount", "32");
        form.apply();
        assertEquals(5, pkg.getFootprint().getBodyWidth(), 1e-9);
        assertEquals(32, pkg.getFootprint().getPadCount());
    }

    @Test
    public void theCompositingFormWritesItsSettings() {
        Package pkg = new Package("BGA-256");
        FormWizard form = contained(PackageCompositingForm.build(configuration, pkg));
        assertEquals(List.of("compositingMethod", "extraShots", "maxPickTolerance", "minLeverageFactor",
                "allowInside"), Form.properties(form));
        form.setValue("extraShots", "2");
        form.apply();
        VisionCompositing compositing = pkg.getVisionCompositing();
        assertEquals(2, compositing.getExtraShots());
    }
}
