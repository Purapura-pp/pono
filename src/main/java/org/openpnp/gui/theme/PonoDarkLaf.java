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

package org.openpnp.gui.theme;

import com.formdev.flatlaf.FlatDarkLaf;

/**
 * Pono's dark look and feel.
 * <p>
 * The colours and metrics are not in this class: FlatLaf loads
 * {@code PonoDarkLaf.properties} from this package by class name, on top of the
 * FlatDarkLaf and FlatLaf defaults. See
 * <a href="https://www.formdev.com/flatlaf/properties-files/">properties files</a>.
 */
public class PonoDarkLaf extends FlatDarkLaf {
    private static final long serialVersionUID = 1L;

    public static final String NAME = "Pono Dark";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Pono dark theme";
    }
}
