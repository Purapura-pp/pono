package org.openpnp.machine.reference.camera.wizards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.camera.AbstractSettlingCamera.SettleMethod;
import org.openpnp.machine.reference.camera.AutoFocusProvider;
import org.openpnp.machine.reference.camera.ImageCamera;
import org.openpnp.machine.reference.camera.ReferenceCamera;
import org.openpnp.machine.reference.camera.SimulatedUpCamera;
import org.openpnp.machine.reference.camera.SwitcherCamera;
import org.openpnp.machine.reference.vision.OpenCvVisionProvider;
import org.openpnp.machine.reference.vision.ReferenceBottomVision;
import org.openpnp.machine.reference.vision.ReferenceFiducialLocator;
import org.openpnp.machine.reference.vision.wizards.VisionForms;
import org.openpnp.model.Configuration;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Camera;
import org.openpnp.spi.PartAlignment;

/**
 * The cameras' and the vision forms, P9 W3: each builds on properties that are there and writes
 * what is on screen when Apply is pressed, not before.
 */
public class CameraFormsTest {
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

    private ReferenceMachine machine;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
        machine = (ReferenceMachine) Configuration.get().getMachine();
    }

    private static FormWizard contained(FormWizard form) {
        form.setWizardContainer(CONTAINER);
        return form;
    }

    private ImageCamera top() throws Exception {
        return (ImageCamera) machine.getDefaultHead().getDefaultCamera();
    }

    private SimulatedUpCamera bottom() {
        for (Camera camera : machine.getCameras()) {
            if (camera instanceof SimulatedUpCamera) {
                return (SimulatedUpCamera) camera;
            }
        }
        throw new AssertionError("no up-looking camera");
    }

    private static double mm(org.openpnp.model.Length length) {
        return length.convertToUnits(LengthUnit.Millimeters).getValue();
    }

    @Test
    public void theGeneralFormWritesOnApply() throws Exception {
        ReferenceCamera camera = top();
        FormWizard form = contained(CameraForm.general(camera));
        assertTrue(Form.properties(form).containsAll(List.of("name", "looking", "previewFps", "suspendPreviewInTasks",
                "lightActuator", "beforeCaptureLightOn", "antiGlareLightOff", "rotation", "offsetX", "flipX",
                "cropWidth", "scaleHeight", "deinterlace", "redBalance", "blueGamma")), Form.properties(form).toString());
        form.setValue("previewFps", "7.5");
        form.set("flipX", true);
        form.setValue("cropWidth", "320");
        form.setValue("redBalance", "90");
        assertFalse(camera.isFlipX(), "nothing is written before Apply");
        form.apply();
        assertEquals(7.5, camera.getPreviewFps(), 1e-9);
        assertTrue(camera.isFlipX());
        assertEquals(320, camera.getCropWidth());
        assertEquals(0.9, camera.getRedBalance(), 1e-9);
    }

    @Test
    public void thePositionFormKeepsThePixelSizeExact() throws Exception {
        ReferenceCamera camera = top();
        FormWizard form = contained(CameraForm.position(camera));
        assertTrue(Form.properties(form).containsAll(List.of("axisX", "axisRotation", "headOffsets", "safeZ",
                "unitsPerPixelPrimary", "enableUnitsPerPixel3D", "defaultZ", "primaryUppZ", "unitsPerPixelSecondary")),
                Form.properties(form).toString());
        form.setLocation("unitsPerPixelPrimary", new Location(LengthUnit.Millimeters, 0.031712, 0.031845, 0, 0));
        form.set("enableUnitsPerPixel3D", true);
        form.setValue("primaryUppZ", "-5");
        form.apply();
        Location upp = camera.getUnitsPerPixelPrimary().convertToUnits(LengthUnit.Millimeters);
        assertEquals(0.031712, upp.getX(), 1e-9, "six places, not the lengths' three");
        assertEquals(0.031845, upp.getY(), 1e-9);
        assertEquals(-5, upp.getZ(), 1e-9, "the pixel's Z is written after its X and Y, and kept");
        assertTrue(camera.isEnableUnitsPerPixel3D());
    }

    @Test
    public void theFixedCameraHasALocationAndARoamingRadius() {
        SimulatedUpCamera camera = bottom();
        FormWizard form = contained(CameraForm.position(camera));
        assertTrue(Form.properties(form).containsAll(List.of("headOffsets", "roamingRadius", "unitsPerPixelPrimary")),
                Form.properties(form).toString());
        assertFalse(Form.properties(form).contains("axisX"), "a camera on no head has no axes");
        form.setValue("roamingRadius", "12");
        form.apply();
        assertEquals(12, mm(camera.getRoamingRadius()), 1e-9);
    }

    @Test
    public void theSettlingFormWritesOnApply() {
        SimulatedUpCamera camera = bottom();
        FormWizard form = contained(CameraForm.settling(camera));
        form.set("settleMethod", SettleMethod.FixedTime);
        form.setValue("settleTimeMs", "300");
        form.apply();
        assertEquals(SettleMethod.FixedTime, camera.getSettleMethod());
        assertEquals(300, camera.getSettleTimeMs());
    }

    @Test
    public void theCalibrationFormWritesOnApply() throws Exception {
        ReferenceCamera camera = top();
        FormWizard form = contained(CameraCalibrationForm.calibration(camera));
        assertTrue(Form.properties(form).containsAll(List.of("state", "overriding", "enabled", "alphaPercent",
                "radialLines", "defaultZ", "lensEnabled")), Form.properties(form).toString());
        form.set("overriding", true);
        form.setValue("alphaPercent", "40");
        form.setValue("radialLines", "24");
        form.apply();
        assertTrue(camera.getAdvancedCalibration().isOverridingOldTransformsAndDistortionCorrectionSettings());
        assertEquals(40, camera.getAdvancedCalibration().getAlphaPercent());
        assertEquals(24, camera.getAdvancedCalibration().getDesiredRadialLinesPerTestPattern());
    }

    @Test
    public void theDeviceFormsWriteOnApply() throws Exception {
        SimulatedUpCamera up = bottom();
        FormWizard simulated = contained(CameraDeviceForms.simulatedUp(up));
        simulated.setValue("viewWidth", "800");
        simulated.set("backgroundScenario", SimulatedUpCamera.BackgroundScenario.Dark);
        simulated.setLocation("simulatedUnitsPerPixel", new Location(LengthUnit.Millimeters, 0.012345, 0.012345, 0, 0));
        simulated.apply();
        assertEquals(800, up.getViewWidth());
        assertEquals(SimulatedUpCamera.BackgroundScenario.Dark, up.getBackgroundScenario());
        assertEquals(0.012345, up.getSimulatedUnitsPerPixel().convertToUnits(LengthUnit.Millimeters).getX(), 1e-9);

        ImageCamera image = top();
        FormWizard imageForm = contained(CameraDeviceForms.image(image));
        imageForm.setValue("simulatedScale", "1.5");
        imageForm.apply();
        assertEquals(1.5, image.getSimulatedScale(), 1e-9);

        SwitcherCamera switcher = new SwitcherCamera();
        FormWizard switcherForm = contained(CameraDeviceForms.switcher(switcher));
        switcherForm.setValue("switcher", "2");
        switcherForm.setValue("actuatorDelayMillis", "150");
        switcherForm.apply();
        assertEquals(2, switcher.getSwitcher());
        assertEquals(150, switcher.getActuatorDelayMillis());
    }

    @Test
    public void theAutoFocusFormWritesOnApply() throws Exception {
        AutoFocusProvider provider = new AutoFocusProvider();
        FormWizard form = contained(AutoFocusForm.build(top(), provider));
        form.setValue("averagedFrames", "4");
        form.set("showDiagnostics", true);
        form.apply();
        assertEquals(4, provider.getAveragedFrames());
        assertTrue(provider.isShowDiagnostics());
    }

    @Test
    public void theVisionFormsWriteOnApply() {
        ReferenceBottomVision bottomVision = null;
        for (PartAlignment alignment : machine.getPartAlignments()) {
            if (alignment instanceof ReferenceBottomVision) {
                bottomVision = (ReferenceBottomVision) alignment;
            }
        }
        FormWizard bottom = contained(VisionForms.bottomVision(bottomVision));
        bottom.set("preRotate", true);
        bottom.setValue("maxVisionPasses", "5");
        bottom.apply();
        assertTrue(bottomVision.isPreRotate());
        assertEquals(5, bottomVision.getMaxVisionPasses());

        ReferenceFiducialLocator locator = (ReferenceFiducialLocator) machine.getFiducialLocator();
        FormWizard fiducials = contained(VisionForms.fiducialLocator(locator));
        fiducials.set("enabledAveraging", true);
        fiducials.setValue("maxDistance", "2");
        fiducials.apply();
        assertTrue(locator.isEnabledAveraging());
        assertEquals(2, mm(locator.getMaxDistance()), 1e-9);

        contained(VisionForms.openCv(new OpenCvVisionProvider()));
    }
}
