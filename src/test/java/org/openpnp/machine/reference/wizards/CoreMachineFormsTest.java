package org.openpnp.machine.reference.wizards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.machine.reference.ContactProbeNozzle;
import org.openpnp.machine.reference.ReferenceHead;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.ReferenceNozzle;
import org.openpnp.machine.reference.ReferenceNozzleTip;
import org.openpnp.machine.reference.ReferencePnpJobProcessor;
import org.openpnp.machine.reference.SimulationModeMachine;
import org.openpnp.machine.reference.ReferenceNozzleTipCalibration.BackgroundCalibrationMethod;
import org.openpnp.model.Configuration;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.NozzleTip;
import org.openpnp.spi.base.AbstractHead.VisualHomingMethod;

/**
 * The core machine's forms, P9 W1: the machine, the simulated machine, the head, the job
 * processor, the nozzle's five sheets, the contact probe nozzle and the nozzle tip's five sheets.
 * Each binds to properties that are there - a form with a misspelt one does not build - and
 * writes what is on screen when Apply is pressed, not before.
 */
public class CoreMachineFormsTest {
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
    private ReferenceHead head;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
        machine = (ReferenceMachine) Configuration.get().getMachine();
        head = (ReferenceHead) machine.getDefaultHead();
    }

    private static FormWizard contained(FormWizard form) {
        form.setWizardContainer(CONTAINER);
        return form;
    }

    private static Location mm(double x, double y, double z, double c) {
        return new Location(LengthUnit.Millimeters, x, y, z, c);
    }

    @Test
    public void theMachineFormWritesOnApply() {
        FormWizard form = contained(MachineForm.build(machine));
        assertTrue(Form.properties(form).containsAll(List.of("homeAfterEnabled", "motionPlanner",
                "unsafeZRoamingDistance", "discardLocation", "defaultBoardLocation")), Form.properties(form).toString());
        boolean parked = machine.isParkAfterHomed();
        form.set("parkAfterHomed", !parked);
        form.setValue("unsafeZRoamingDistance", "12.5");
        form.setLocation("discardLocation", mm(1, 2, 3, 4));
        assertEquals(parked, machine.isParkAfterHomed(), "nothing is written before Apply");
        form.apply();
        assertEquals(!parked, machine.isParkAfterHomed());
        assertEquals(12.5, machine.getUnsafeZRoamingDistance().convertToUnits(LengthUnit.Millimeters).getValue(), 1e-9);
        assertEquals(mm(1, 2, 3, 4), machine.getDiscardLocation().convertToUnits(LengthUnit.Millimeters));
    }

    @Test
    public void theSimulationFormWritesItsImperfections() {
        SimulationModeMachine simulated = new SimulationModeMachine();
        FormWizard form = contained(SimulationMachineForm.build(simulated));
        form.setValue("simulatedCameraNoise", "7");
        form.setValue("simulatedNonSquarenessFactor", "0.002");
        form.set("pickAndPlaceChecking", true);
        form.apply();
        assertEquals(7, simulated.getSimulatedCameraNoise());
        assertEquals(0.002, simulated.getSimulatedNonSquarenessFactor(), 1e-9);
        assertTrue(simulated.isPickAndPlaceChecking());
    }

    @Test
    public void theHeadFormWritesHomingParkAndPump() {
        FormWizard form = contained(HeadForm.build(head));
        assertTrue(Form.properties(form).containsAll(List.of("parkLocation", "visualHomingMethod",
                "calibrationPrimaryFiducialLocation", "probeActuator", "pumpActuator")), Form.properties(form).toString());
        Actuator pump = head.getActuatorByName("PUMP");
        form.set("visualHomingMethod", VisualHomingMethod.ResetToFiducialLocation);
        form.setLocation("parkLocation", mm(10, 20, 0, 0));
        form.setLocation("calibrationPrimaryFiducialLocation", mm(5, 6, -1, 0));
        form.set("pumpActuator", pump);
        form.setValue("pumpOnWaitMilliseconds", "250");
        form.apply();
        assertEquals(VisualHomingMethod.ResetToFiducialLocation, head.getVisualHomingMethod());
        assertEquals(10, head.getParkLocation().convertToUnits(LengthUnit.Millimeters).getX(), 1e-9);
        assertEquals(-1, head.getCalibrationPrimaryFiducialLocation().convertToUnits(LengthUnit.Millimeters).getZ(), 1e-9);
        assertEquals(pump, head.getPumpActuator());
        assertEquals(250, head.getPumpOnWaitMilliseconds());
        form.set("pumpActuator", null);
        form.apply();
        assertEquals(null, head.getPumpActuator(), "none is a choice too");
    }

    @Test
    public void theJobProcessorFormWritesAttemptsAndOrder() {
        ReferencePnpJobProcessor processor = (ReferencePnpJobProcessor) machine.getPnpJobProcessor();
        FormWizard form = contained(JobProcessorForm.build(processor));
        form.setValue("maxVisionRetries", "4");
        form.setValue("feederFaultLimit", "3");
        form.set("jobOrder", ReferencePnpJobProcessor.JobOrderHint.PickLocation);
        form.apply();
        assertEquals(4, processor.getMaxVisionRetries());
        assertEquals(3, processor.getFeederFaultLimit());
        assertEquals(ReferencePnpJobProcessor.JobOrderHint.PickLocation, processor.getJobOrder());
    }

    @Test
    public void theNozzleSheetsWriteOnApply() throws Exception {
        ReferenceNozzle nozzle = (ReferenceNozzle) head.getDefaultNozzle();
        FormWizard settings = contained(NozzleForm.settings(nozzle));
        assertTrue(Form.properties(settings).containsAll(List.of("name", "axisX", "axisZ", "headOffsets",
                "rotationMode", "safeZ", "pickDwellMilliseconds")), Form.properties(settings).toString());
        settings.setValue("name", "N9");
        settings.setValue("pickDwellMilliseconds", "15");
        settings.setLocation("headOffsets", mm(-20, 1, 0, 0));
        settings.apply();
        assertEquals("N9", nozzle.getName());
        assertEquals(15, nozzle.getPickDwellMilliseconds());
        assertEquals(-20, nozzle.getHeadOffsets().convertToUnits(LengthUnit.Millimeters).getX(), 1e-9);

        NozzleTip tip = machine.getNozzleTips().get(0);
        FormWizard tips = contained(NozzleForm.nozzleTips(nozzle));
        tips.set("compatibleNozzleTips", Set.of());
        assertTrue(nozzle.getCompatibleNozzleTips().contains(tip), "a switch is not written as it is flipped");
        tips.apply();
        assertFalse(nozzle.getCompatibleNozzleTips().contains(tip));
        tips.set("compatibleNozzleTips", Set.of(tip));
        tips.apply();
        assertTrue(nozzle.getCompatibleNozzleTips().contains(tip));

        FormWizard vacuum = contained(NozzleForm.vacuum(nozzle));
        vacuum.set("blowOffClosingValve", true);
        vacuum.apply();
        assertTrue(nozzle.isBlowOffClosingValve());

        FormWizard changer = contained(NozzleForm.changer(nozzle));
        changer.set("changerEnabled", false);
        changer.setLocation("manualNozzleTipChangeLocation", mm(100, 50, -5, 0));
        changer.apply();
        assertFalse(nozzle.isChangerEnabled());
        assertEquals(-5, nozzle.getManualNozzleTipChangeLocation().convertToUnits(LengthUnit.Millimeters).getZ(), 1e-9);

        FormWizard offset = contained(NozzleForm.offset(nozzle));
        assertTrue(Form.properties(offset).containsAll(List.of("camera", "includeZ", "markLocation", "headOffsets")));
    }

    @Test
    public void theContactProbeFormWritesItsProbing() throws Exception {
        ContactProbeNozzle nozzle = new ContactProbeNozzle();
        head.addNozzle(nozzle);
        FormWizard form = contained(ContactProbeNozzleForm.build(nozzle));
        form.set("contactProbeMethod", ContactProbeNozzle.ContactProbeMethod.VacuumSense);
        form.setValue("contactProbeDepthZ", "3");
        form.setValue("sniffleDwellTime", "40");
        form.apply();
        assertEquals(ContactProbeNozzle.ContactProbeMethod.VacuumSense, nozzle.getContactProbeMethod());
        assertEquals(3, nozzle.getContactProbeDepthZ().convertToUnits(LengthUnit.Millimeters).getValue(), 1e-9);
        assertEquals(40, nozzle.getSniffleDwellTime());
    }

    @Test
    public void theNozzleTipSheetsWriteOnApply() {
        ReferenceNozzleTip tip = (ReferenceNozzleTip) machine.getNozzleTips().get(0);
        FormWizard settings = contained(NozzleTipForm.settings(tip));
        settings.setValue("maxPartHeight", "4");
        settings.set("pushAndDragAllowed", true);
        settings.apply();
        assertEquals(4, tip.getMaxPartHeight().convertToUnits(LengthUnit.Millimeters).getValue(), 1e-9);
        assertTrue(tip.isPushAndDragAllowed());

        FormWizard calibration = contained(NozzleTipForm.calibration(tip));
        calibration.set("calibrationEnabled", true);
        calibration.setValue("angleSubdivisions", "8");
        calibration.apply();
        assertTrue(tip.getCalibration().isEnabled());
        assertEquals(8, tip.getCalibration().getAngleSubdivisions());

        FormWizard background = contained(NozzleTipForm.background(tip));
        background.set("backgroundCalibrationMethod", BackgroundCalibrationMethod.BrightnessAndKeyColor);
        background.setValue("backgroundTolHue", "12");
        background.apply();
        assertEquals(BackgroundCalibrationMethod.BrightnessAndKeyColor, tip.getCalibration().getBackgroundCalibrationMethod());
        assertEquals(12, tip.getCalibration().getBackgroundTolHue());

        FormWizard detection = contained(NozzleTipForm.partDetection(tip));
        detection.set("partOnChecks", Set.of(NozzleTipForm.PartOnCheck.AfterPick));
        detection.set("partOffChecks", Set.of(NozzleTipForm.PartOffCheck.BeforePick));
        detection.apply();
        assertTrue(tip.isPartOnCheckAfterPick());
        assertFalse(tip.isPartOnCheckAlign());
        assertFalse(tip.isPartOnCheckBeforePlace());
        assertTrue(tip.isPartOffCheckBeforePick());
        assertFalse(tip.isPartOffCheckAfterPlace());

        FormWizard changer = contained(NozzleTipForm.changer(tip));
        changer.setLocation("changerStartLocation", mm(200, 10, -12, 0));
        changer.setValue("changerStartToMidSpeed", "0.5");
        changer.set("templateRole", NozzleTipForm.TemplateRole.Locked);
        changer.apply();
        assertEquals(200, tip.getChangerStartLocation().convertToUnits(LengthUnit.Millimeters).getX(), 1e-9);
        assertEquals(0.5, tip.getChangerStartToMidSpeed(), 1e-9);
        assertTrue(tip.isTemplateLocked());
        assertFalse(tip.isTemplateNozzleTip());
        changer.set("templateRole", NozzleTipForm.TemplateRole.Template);
        changer.apply();
        assertTrue(tip.isTemplateNozzleTip());
        assertFalse(tip.isTemplateLocked());
    }

    @Test
    public void backgroundDiagnosticsBecomeLines() {
        String report = "<html>Non-color-keyed background elements are quite dark. Perfect!<br/><hr/>"
                + "The key color is very consistent. Perfect!<br/><hr/>The key color is not vivid enough.<br/>"
                + "Eliminate light sources that reflect on the nozzle tip..<br/></html>";
        String[] lines = NozzleTipForm.diagnostics(report).split("\n");
        assertEquals(4, lines.length, String.join("|", lines));
        assertFalse(lines[3].endsWith(".."), lines[3]);
    }
}
