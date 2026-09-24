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

import javax.swing.JComponent;

import org.openpnp.Translations;

/**
 * One topic of the machine settings page: its name and icon in the list, what it shows, and the
 * forms whose edits the page's foot applies. Its view is built when it is first shown and again
 * after the machine it shows has changed shape.
 */
abstract class Topic {
    final String key;
    final String icon;
    final TopicForms forms = new TopicForms();
    private JComponent view;

    Topic(String key, String icon) {
        this.key = key;
        this.icon = icon;
    }

    String title() {
        return Translations.getString("MachineSettings.Topic." + key); //$NON-NLS-1$
    }

    final JComponent view() {
        if (view == null) {
            view = build();
        }
        return view;
    }

    final boolean isBuilt() {
        return view != null;
    }

    protected abstract JComponent build();

    /** The machine changed shape under the topic: its forms go, and the view is built afresh. */
    void discard() {
        forms.dispose();
        view = null;
    }

    /** The topic came on screen. */
    void shown() {
    }

    /** The topic left the screen. */
    void hidden() {
    }
}
