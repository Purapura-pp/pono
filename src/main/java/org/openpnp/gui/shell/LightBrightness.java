/*
 * Copyright (C) 2026 Pono contributors
 * 
 * This file is part of Pono, a modified version of OpenPnP.
 * 
 * Pono is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * Pono is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Pono. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package org.openpnp.gui.shell;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openpnp.machine.reference.driver.GcodeDriver;
import org.openpnp.machine.reference.driver.GcodeDriver.CommandType;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Actuator.ActuatorValueType;
import org.openpnp.spi.base.AbstractActuator;

/**
 * A camera light's brightness, as a fraction of its actuator's full scale: the ON value of a
 * numeric light actuator, which is what every capture that switches the light on sends.
 * <p>
 * A light that is only switched has its brightness written into its G-code, as LumenPnP's
 * {@code {True:M150 P45 R255 U255 B255}{False:M150 P0}}. Such a command can become the numeric
 * one that takes the brightness instead. Nothing else can: only the command says which of its
 * numbers is the brightness.
 */
final class LightBrightness {
    private LightBrightness() {
    }

    /** What a switched light becomes: its numeric command, and the brightness it had. */
    static final class Conversion {
        final String command;
        final double on;

        Conversion(String command, double on) {
            this.command = command;
            this.on = on;
        }
    }

    private static final Pattern SWITCHED = Pattern.compile("^\\{True:([^{}]*)\\}\\{False:([^{}]*)\\}$"); //$NON-NLS-1$
    /** M150 takes a NeoPixel's brightness as P; M42 and M106 take a pin's or fan's duty cycle as S. */
    private static final Pattern LEVEL = Pattern.compile(
            "^(?i:M150\\b.*?\\bP|(?:M42|M106)\\b.*?\\bS)(\\d+(?:\\.\\d+)?)\\b"); //$NON-NLS-1$
    private static final String VALUE = "{IntegerValue}"; //$NON-NLS-1$

    static boolean adjustable(Actuator actuator) {
        return actuator instanceof AbstractActuator && actuator.getValueType() == ActuatorValueType.Double;
    }

    static double fullScale(Actuator actuator) {
        return actuator instanceof AbstractActuator ? ((AbstractActuator) actuator).getFullScaleDouble()
                : AbstractActuator.DEFAULT_FULL_SCALE;
    }

    /** The ON value as a fraction of the full scale, from 0 to 1. */
    static double fraction(Actuator actuator) {
        Double on = ((AbstractActuator) actuator).getDefaultOnDouble();
        double scale = fullScale(actuator);
        return on == null || scale <= 0 ? 1 : Math.max(0, Math.min(1, on / scale));
    }

    /**
     * The value a fraction stands for: whole steps on a scale of ten or more, and at least one
     * step, since an ON value equal to the OFF value of 0 is refused when the light is switched.
     */
    static double value(Actuator actuator, double fraction) {
        double scale = fullScale(actuator);
        double value = Math.max(0, Math.min(1, fraction)) * scale;
        if (scale >= 10) {
            return Math.max(1, Math.round(value));
        }
        return Math.max(scale / 100, value);
    }

    /** The numeric command a switched light's command becomes, or null if it cannot become one. */
    static Conversion conversion(Actuator actuator) {
        if (!(actuator instanceof AbstractActuator) || actuator.getValueType() != ActuatorValueType.Boolean
                || !(actuator.getDriver() instanceof GcodeDriver)) {
            return null;
        }
        GcodeDriver driver = (GcodeDriver) actuator.getDriver();
        return parse(driver.getCommand(actuator, CommandType.ACTUATE_BOOLEAN_COMMAND));
    }

    /**
     * The numeric command for a switched one: its ON command with the brightness taken from the
     * value, which is 0 for OFF. Null unless the command is one ON and one OFF branch and the ON
     * branch sets a brightness above 0.
     */
    static Conversion parse(String switched) {
        if (switched == null) {
            return null;
        }
        Matcher branches = SWITCHED.matcher(switched.trim());
        if (!branches.matches()) {
            return null;
        }
        String on = branches.group(1).trim();
        Matcher level = LEVEL.matcher(on);
        if (!level.find()) {
            return null;
        }
        double brightness = Double.parseDouble(level.group(1));
        if (brightness <= 0) {
            return null;
        }
        return new Conversion(on.substring(0, level.start(1)) + VALUE + on.substring(level.end(1)), brightness);
    }

    /**
     * Makes a switched light numeric as the conversion says, OFF at 0 on a full scale of 255. The
     * command and the values are in place before the type changes, so a capture in between still
     * finds a complete light.
     */
    static void convert(Actuator actuator, Conversion conversion) {
        AbstractActuator light = (AbstractActuator) actuator;
        ((GcodeDriver) actuator.getDriver()).setCommand(actuator, CommandType.ACTUATE_DOUBLE_COMMAND,
                conversion.command);
        light.setDefaultOnDouble(conversion.on);
        light.setDefaultOffDouble(0.0);
        light.setFullScaleDouble(AbstractActuator.DEFAULT_FULL_SCALE);
        light.setValueType(ActuatorValueType.Double);
    }

    /** 45, or 0.5: the brightness as it was written. */
    static String format(double brightness) {
        return brightness == Math.rint(brightness) ? String.valueOf((long) brightness) : String.valueOf(brightness);
    }
}
