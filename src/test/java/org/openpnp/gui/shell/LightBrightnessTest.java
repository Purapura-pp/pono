package org.openpnp.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.machine.reference.ReferenceActuator;
import org.openpnp.machine.reference.driver.GcodeDriver;
import org.openpnp.machine.reference.driver.GcodeDriver.CommandType;
import org.openpnp.model.Configuration;
import org.openpnp.spi.Actuator.ActuatorValueType;

/**
 * A camera light's brightness on the camera tools: a numeric light's ON value as a fraction of
 * its full scale, and a switched light whose command has its brightness in it made numeric.
 */
public class LightBrightnessTest {
    private static final String LUMENPNP = "{True:M150 P45 R255 U255 B255}{False:M150 P0}";

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
    }

    @Test
    public void theBrightnessInASwitchedCommandIsFound() {
        LightBrightness.Conversion lumen = LightBrightness.parse(LUMENPNP);
        assertNotNull(lumen);
        assertEquals("M150 P{IntegerValue} R255 U255 B255", lumen.command);
        assertEquals(45, lumen.on);

        LightBrightness.Conversion fan = LightBrightness.parse("{True:M106 P1 S255}{False:M107 P1}");
        assertNotNull(fan);
        assertEquals("M106 P1 S{IntegerValue}", fan.command, "the duty cycle, not the fan's index");
        assertEquals(255, fan.on);

        LightBrightness.Conversion pin = LightBrightness.parse("{True:M42 P35 S128}{False:M42 P35 S0}");
        assertNotNull(pin);
        assertEquals("M42 P35 S{IntegerValue}", pin.command);
    }

    @Test
    public void aCommandThatDoesNotSayItsBrightnessIsLeftAlone() {
        assertNull(LightBrightness.parse(null));
        assertNull(LightBrightness.parse("{True:M106}{False:M107}"), "a switch with no level");
        assertNull(LightBrightness.parse("{True:M150 P0}{False:M150 P0}"), "ON is already dark");
        assertNull(LightBrightness.parse("M42 P35 S255\nG4 P{True:100}{False:200}\nM42 P35 S0"),
                "more than one ON and one OFF branch");
        assertNull(LightBrightness.parse("{True:M1500 P45}{False:M1500 P0}"), "another command");
    }

    @Test
    public void aFractionIsAWholeStepAndNeverOff() throws Exception {
        ReferenceActuator light = new ReferenceActuator();
        light.setValueType(ActuatorValueType.Double);
        light.setDefaultOnDouble(45.0);
        assertTrue(LightBrightness.adjustable(light));
        assertEquals(45 / 255.0, LightBrightness.fraction(light), 1e-9);
        assertEquals(128, LightBrightness.value(light, 0.5));
        assertEquals(1, LightBrightness.value(light, 0), "0 would be the OFF value");
        assertEquals(255, LightBrightness.value(light, 1));

        light.setFullScaleDouble(1.0);
        assertEquals(0.5, LightBrightness.value(light, 0.5), 1e-9, "a fraction on a scale of one");
        assertEquals(0.01, LightBrightness.value(light, 0), 1e-9);
    }

    @Test
    public void aSwitchedLumenPnpLightBecomesNumeric() throws Exception {
        GcodeDriver driver = new GcodeDriver();
        ReferenceActuator light = new ReferenceActuator();
        light.setDriver(driver);
        driver.setCommand(light, CommandType.ACTUATE_BOOLEAN_COMMAND, LUMENPNP);
        assertFalse(LightBrightness.adjustable(light));

        LightBrightness.Conversion conversion = LightBrightness.conversion(light);
        assertNotNull(conversion);
        LightBrightness.convert(light, conversion);

        assertTrue(LightBrightness.adjustable(light));
        assertEquals("M150 P{IntegerValue} R255 U255 B255",
                driver.getCommand(light, CommandType.ACTUATE_DOUBLE_COMMAND));
        assertEquals(45.0, light.getDefaultOnDouble());
        assertEquals(0.0, light.getDefaultOffDouble());
        assertEquals(255.0, light.getFullScaleDouble());
        assertNull(LightBrightness.conversion(light), "a numeric light needs no conversion");
    }

    @Test
    public void aLightWithoutACommandCannotBeMadeNumeric() {
        ReferenceActuator light = new ReferenceActuator();
        assertNull(LightBrightness.conversion(light), "no driver");
        light.setDriver(new GcodeDriver());
        assertNull(LightBrightness.conversion(light), "no command");
    }
}
