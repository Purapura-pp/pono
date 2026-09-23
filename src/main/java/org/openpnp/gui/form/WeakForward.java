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

package org.openpnp.gui.form;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.function.BiConsumer;

import javax.swing.SwingUtilities;

import org.openpnp.model.AbstractModelObject;

/**
 * Hears a machine object's changes for a form for as long as the form is there, and no longer.
 * A form is built each time its sheet is shown: a listener that held on to it kept every one of
 * them, with its controls, for as long as the machine object lived.
 */
public final class WeakForward {
    private WeakForward() {
    }

    /** The action, on the event thread, with the owner, while the owner is still there. */
    public static <T> void listen(AbstractModelObject source, T owner, BiConsumer<T, PropertyChangeEvent> action) {
        WeakReference<T> reference = new WeakReference<>(owner);
        PropertyChangeListener[] self = new PropertyChangeListener[1];
        self[0] = e -> {
            T held = reference.get();
            if (held == null) {
                source.removePropertyChangeListener(self[0]);
                return;
            }
            if (SwingUtilities.isEventDispatchThread()) {
                action.accept(held, e);
            }
            else {
                SwingUtilities.invokeLater(() -> action.accept(held, e));
            }
        };
        source.addPropertyChangeListener(self[0]);
    }
}
