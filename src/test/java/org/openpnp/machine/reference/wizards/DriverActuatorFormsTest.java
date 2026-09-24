package org.openpnp.machine.reference.wizards;

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
import org.openpnp.machine.reference.HttpActuator;
import org.openpnp.machine.reference.ReferenceActuator;
import org.openpnp.machine.reference.ReferenceActuator.MachineStateActuation;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.ScriptActuator;
import org.openpnp.machine.reference.actuator.ThermistorToLinearSensorActuator;
import org.openpnp.machine.reference.actuator.wizards.ThermistorForm;
import org.openpnp.machine.reference.driver.AbstractReferenceDriver.CommunicationsType;
import org.openpnp.machine.reference.driver.GcodeAsyncDriver;
import org.openpnp.machine.reference.driver.GcodeDriver;
import org.openpnp.machine.reference.driver.wizards.DriverForms;
import org.openpnp.machine.reference.driver.wizards.GcodeForms;
import org.openpnp.machine.reference.signaler.SoundSignaler;
import org.openpnp.machine.reference.signaler.wizards.SignalerForms;
import org.openpnp.model.Configuration;
import org.openpnp.model.LengthUnit;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Actuator.ActuatorValueType;
import org.openpnp.spi.base.AbstractActuator.ActuatorCoordinationEnumType;

/**
 * The drivers', actuators' and signalers' forms, P9 W4: each builds on properties that are there
 * and writes what is on screen when Apply is pressed, not before.
 */
public class DriverActuatorFormsTest {
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

    private ReferenceActuator headActuator() throws Exception {
        for (Actuator actuator : machine.getDefaultHead().getActuators()) {
            if (actuator instanceof ReferenceActuator) {
                return (ReferenceActuator) actuator;
            }
        }
        throw new AssertionError("no head actuator");
    }

    @Test
    public void theActuatorFormWritesOnApply() throws Exception {
        ReferenceActuator actuator = headActuator();
        FormWizard form = contained(ActuatorForm.reference(actuator));
        assertTrue(Form.properties(form).containsAll(List.of("name", "driver", "valueType", "defaultOnDouble",
                "defaultOffString", "index", "enabledActuation", "homedActuation", "coordinatedBeforeActuateEnum",
                "axisX", "headOffsets", "safeZ", "interlockActuator")), Form.properties(form).toString());
        form.set("valueType", ActuatorValueType.Double);
        form.setValue("defaultOnDouble", "2.5");
        form.set("homedActuation", MachineStateActuation.ActuateOn);
        form.set("coordinatedAfterActuateEnum", ActuatorCoordinationEnumType.WaitForUnconditionalCoordination);
        assertFalse(actuator.getValueType() == ActuatorValueType.Double, "nothing is written before Apply");
        form.apply();
        assertEquals(ActuatorValueType.Double, actuator.getValueType());
        assertEquals(2.5, actuator.getDefaultOnDouble(), 1e-9);
        assertEquals(MachineStateActuation.ActuateOn, actuator.getHomedActuation());
        assertEquals(ActuatorCoordinationEnumType.WaitForUnconditionalCoordination,
                actuator.getCoordinatedAfterActuateEnum());
    }

    @Test
    public void theScriptAndHttpActuatorsWriteOnApply() {
        ScriptActuator script = new ScriptActuator();
        FormWizard scriptForm = contained(ActuatorForm.script(script));
        assertFalse(Form.properties(scriptForm).contains("axisX"), "an actuator on no head has no axes");
        scriptForm.setValue("scriptName", "Lights.js");
        scriptForm.apply();
        assertEquals("Lights.js", script.getScriptName());

        HttpActuator http = new HttpActuator();
        FormWizard httpForm = contained(ActuatorForm.http(http));
        httpForm.setValue("onUrl", "http://feeder/on");
        httpForm.setValue("regex", "t=(.*)");
        httpForm.apply();
        assertEquals("http://feeder/on", http.getOnUrl());
        assertEquals("t=(.*)", http.getRegex());
    }

    @Test
    public void theThermistorAndSignalerFormsWriteOnApply() {
        ThermistorToLinearSensorActuator thermistor = new ThermistorToLinearSensorActuator();
        FormWizard conversion = contained(ThermistorForm.build(thermistor));
        conversion.setValue("vRef", "3.3");
        conversion.setValue("a", "0.001");
        conversion.apply();
        assertEquals(3.3, thermistor.getvRef(), 1e-9);
        assertEquals(0.001, thermistor.getA(), 1e-12);

        SoundSignaler sound = new SoundSignaler();
        FormWizard sounds = contained(SignalerForms.sound(sound));
        sounds.set("enableFinishedSound", !sound.isEnableFinishedSound());
        boolean expected = !sound.isEnableFinishedSound();
        sounds.apply();
        assertEquals(expected, sound.isEnableFinishedSound());
    }

    @Test
    public void theDriverFormsWriteOnApply() throws Exception {
        GcodeAsyncDriver driver = new GcodeAsyncDriver();
        machine.addDriver(driver);
        FormWizard connection = contained(DriverForms.communications(driver));
        connection.set("communicationsType", CommunicationsType.tcp);
        connection.setValue("ipAddress", "10.0.0.7");
        connection.setValue("port", "23");
        connection.apply();
        assertEquals(CommunicationsType.tcp, driver.getCommunicationsType());
        assertEquals("10.0.0.7", driver.getIpAddress());
        assertEquals(23, driver.getPort());

        FormWizard settings = contained(DriverForms.gcodeSettings(driver));
        settings.set("units", LengthUnit.Inches);
        settings.setValue("timeoutMilliseconds", "1234");
        settings.set("compressGcode", true);
        settings.apply();
        assertEquals(LengthUnit.Inches, driver.getUnits());
        assertEquals(1234, driver.getTimeoutMilliseconds());
        assertTrue(driver.isCompressGcode());

        FormWizard async = contained(DriverForms.asyncSettings(driver));
        async.setValue("interpolationMaxSteps", "77");
        async.apply();
        assertEquals(77, driver.getInterpolationMaxSteps());

        contained(GcodeForms.gcodes(driver));
        contained(GcodeForms.console(driver));
    }

    @Test
    public void theCommandsWaitForApply() throws Exception {
        GcodeDriver driver = new GcodeDriver();
        machine.addDriver(driver);
        FormWizard form = contained(GcodeForms.gcodes(driver));
        GcodeForms.Commands commands = null;
        for (java.awt.Component c : components(form)) {
            if (c instanceof GcodeForms.Commands) {
                commands = (GcodeForms.Commands) c;
            }
        }
        assertTrue(commands != null, "the commands' block is on the form");
        javax.swing.JTextArea text = null;
        GcodeDriver.CommandType type = null;
        for (java.awt.Component c : components(commands)) {
            if (c instanceof javax.swing.JTextArea) {
                text = (javax.swing.JTextArea) c;
            }
            if (c instanceof javax.swing.JComboBox
                    && ((javax.swing.JComboBox<?>) c).getSelectedItem() instanceof GcodeDriver.CommandType) {
                type = (GcodeDriver.CommandType) ((javax.swing.JComboBox<?>) c).getSelectedItem();
            }
        }
        String before = command(driver, type);
        text.setText("G4 P1 ; test");
        assertEquals(before, command(driver, type), "nothing is written before Apply");
        form.apply();
        assertEquals("G4 P1 ; test", command(driver, type));
    }

    private static String command(GcodeDriver driver, GcodeDriver.CommandType type) {
        GcodeDriver.Command command = driver.getCommand(null, type, false);
        return command == null ? null : command.getCommand();
    }

    private static List<java.awt.Component> components(java.awt.Container root) {
        List<java.awt.Component> all = new java.util.ArrayList<>();
        for (java.awt.Component c : root.getComponents()) {
            all.add(c);
            if (c instanceof java.awt.Container) {
                all.addAll(components((java.awt.Container) c));
            }
        }
        return all;
    }
}
