/*
 * Copyright (C) 2026 Pono
 * 
 * This file is part of OpenPnP.
 * 
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * For more information about OpenPnP visit http://openpnp.org
 */

package org.openpnp.machine.reference.calibration;

/**
 * An issue whose solution is one value going into one setting: what the calibration page shows
 * as the step's change, from the value as it stands to the one accepting writes. Accepting the
 * issue writes it and undoing the issue puts back what was there, as for any issue.
 */
public interface SettingChange {
    /** The setting, as the issue's property labels it. */
    String getSettingName();

    /** The setting as it stands: a Length, a number, or an enum constant. */
    Object getCurrentValue();

    /** What accepting writes; adjustable before accepting where the issue offers it. */
    Object getProposedValue();
}
