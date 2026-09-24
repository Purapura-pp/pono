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

import java.util.Locale;

import org.openpnp.gui.support.DisplayNames;
import org.openpnp.machine.reference.camera.AbstractSettlingCamera.SettleMethod;
import org.openpnp.machine.reference.camera.ReferenceCamera;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;

/**
 * What the calibration measured of a camera, as text for the read-only fields of the cameras
 * topic: its pixel size, its height of focus, where an up-looking camera is, how it settles.
 * A bean the forms can read, which is why it is public.
 */
public final class CameraFacts extends AbstractModelObject {
    private final ReferenceCamera camera;
    private final LengthUnit units;

    CameraFacts(ReferenceCamera camera, LengthUnit units) {
        this.camera = camera;
        this.units = units;
    }

    public String getUnitsPerPixel() {
        Location upp = camera.getUnitsPerPixelPrimary();
        if (upp == null) {
            return null;
        }
        Location shown = upp.convertToUnits(units);
        return String.format(Locale.US, "%.5f \u00d7 %.5f %s/px", shown.getX(), shown.getY(), units.getShortName()); //$NON-NLS-1$
    }

    public String getDefaultZ() {
        if (camera.getDefaultZ() == null) {
            return null;
        }
        return String.format(Locale.US, "%.3f %s", camera.getDefaultZ().convertToUnits(units).getValue(), //$NON-NLS-1$
                units.getShortName());
    }

    public String getPosition() {
        Location at = camera.getHeadOffsets().convertToUnits(units);
        return String.format(Locale.US, "%.3f, %.3f, %.3f %s", at.getX(), at.getY(), at.getZ(), //$NON-NLS-1$
                units.getShortName());
    }

    public String getSettle() {
        SettleMethod method = camera.getSettleMethod();
        String name = method == null ? "\u2014" : DisplayNames.of(method); //$NON-NLS-1$
        return method == SettleMethod.FixedTime ? name + " \u00b7 " + camera.getSettleTimeMs() + " ms" : name; //$NON-NLS-1$ //$NON-NLS-2$
    }
}
