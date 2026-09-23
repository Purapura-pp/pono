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

package org.openpnp.model;

/**
 * The user answered Cancel to one of the questions a save asks before it writes anything, so
 * nothing was written and whatever the save was part of - quitting, most often - should not go
 * ahead either.
 */
@SuppressWarnings("serial")
public class SaveCancelledException extends Exception {
    public SaveCancelledException() {
        super("Cancelled by the user."); //$NON-NLS-1$
    }
}
