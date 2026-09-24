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

package org.openpnp.gui.machinesettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.openpnp.Translations;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis;
import org.openpnp.machine.reference.driver.GcodeDriver;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.spi.Axis;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Driver;
import org.openpnp.spi.Head;

/**
 * The machine in a line, as the head of the machine settings page says it: its nozzles and how
 * they move, the travel its soft limits allow, the firmware its controller reported, its cameras.
 */
final class MachineSummary {
    private final ReferenceMachine machine;
    private final LengthUnit units;

    MachineSummary(ReferenceMachine machine, LengthUnit units) {
        this.machine = machine;
        this.units = units;
    }

    /** The model it is, or plainly this machine. */
    String name() {
        String preset = machine.getPresetName();
        return preset != null && !preset.isEmpty() ? preset
                : Translations.getString("MachineSettings.Head.Unnamed"); //$NON-NLS-1$
    }

    /** Where the definition came from: a built-in preset, one of the user's, or none. */
    String origin() {
        String preset = machine.getPresetName();
        if (preset == null || preset.isEmpty()) {
            return Translations.getString("MachineSettings.Head.NoPreset"); //$NON-NLS-1$
        }
        return Translations.getString(machine.isPresetBuiltIn() ? "MachineSettings.Head.BuiltIn" //$NON-NLS-1$
                : "MachineSettings.Head.UserPreset"); //$NON-NLS-1$
    }

    String line() {
        List<String> parts = new ArrayList<>();
        Head head = head();
        if (head != null) {
            NozzleStructure structure = NozzleStructure.of(head);
            if (structure.nozzles == 1) {
                parts.add(Translations.getString("MachineSettings.Head.OneNozzle")); //$NON-NLS-1$
            }
            else if (structure.nozzles > 1) {
                parts.add(String.format(Translations.getString("MachineSettings.Head.Nozzles"), //$NON-NLS-1$
                        structure.nozzles, structure.name()));
            }
        }
        String travel = travel();
        if (travel != null) {
            parts.add(travel);
        }
        String firmware = firmware();
        if (firmware != null) {
            parts.add(firmware);
        }
        String cameras = cameras(head);
        if (cameras != null) {
            parts.add(cameras);
        }
        return String.join(" \u00b7 ", parts); //$NON-NLS-1$
    }

    private Head head() {
        try {
            return machine.getDefaultHead();
        }
        catch (Exception e) {
            return null;
        }
    }

    /** "Travel 433 x 487 mm", from the first X and Y axes whose both soft limits are on. */
    private String travel() {
        Length x = span(Axis.Type.X);
        Length y = span(Axis.Type.Y);
        if (x == null || y == null) {
            return null;
        }
        return String.format(Locale.US, Translations.getString("MachineSettings.Head.Travel"), //$NON-NLS-1$
                x.convertToUnits(units).getValue(), y.convertToUnits(units).getValue(), units.getShortName());
    }

    private Length span(Axis.Type type) {
        for (Axis axis : machine.getAxes()) {
            if (axis.getType() == type && axis instanceof ReferenceControllerAxis) {
                ReferenceControllerAxis controller = (ReferenceControllerAxis) axis;
                if (controller.isSoftLimitLowEnabled() && controller.isSoftLimitHighEnabled()) {
                    return controller.getSoftLimitHigh().subtract(controller.getSoftLimitLow());
                }
                return null;
            }
        }
        return null;
    }

    /** The firmware's name as the controller gave it to M115, without the URL after it. */
    private String firmware() {
        for (Driver driver : machine.getDrivers()) {
            if (driver instanceof GcodeDriver) {
                String name = ((GcodeDriver) driver).getFirmwareProperty("FIRMWARE_NAME", null); //$NON-NLS-1$
                if (name != null && !name.trim().isEmpty()) {
                    int bracket = name.indexOf('(');
                    return (bracket > 0 ? name.substring(0, bracket) : name).trim();
                }
            }
        }
        return null;
    }

    private String cameras(Head head) {
        int down = 0;
        int up = 0;
        if (head != null) {
            for (Camera camera : head.getCameras()) {
                if (camera.getLooking() == Camera.Looking.Down) {
                    down++;
                }
            }
        }
        for (Camera camera : machine.getCameras()) {
            if (camera.getLooking() == Camera.Looking.Up) {
                up++;
            }
        }
        if (down == 0 && up == 0) {
            return null;
        }
        if (down == 1 && up == 1) {
            return Translations.getString("MachineSettings.Head.TopAndBottom"); //$NON-NLS-1$
        }
        return String.format(Translations.getString("MachineSettings.Head.Cameras"), down + up); //$NON-NLS-1$
    }
}
