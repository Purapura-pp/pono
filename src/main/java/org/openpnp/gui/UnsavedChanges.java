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

package org.openpnp.gui;

import java.beans.IndexedPropertyChangeEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.swing.SwingUtilities;

import org.openpnp.Translations;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.Panel;
import org.openpnp.model.Placement;
import org.openpnp.model.PlacementsHolder;
import org.openpnp.model.PlacementsHolderLocation;

/**
 * How much has changed on the board or panel on show since it was last saved, in places: a
 * placement, a child or a fiducial changed, added or deleted is one place however often it is
 * changed, and so is the name or the size. The model only knows that something did.
 * <p>
 * Only what changes while the page shows it is counted; a definition changed elsewhere, from the
 * job page, is "modified" without a count.
 */
final class UnsavedChanges {
    private final Map<PlacementsHolder<?>, Set<String>> changed = new WeakHashMap<>();
    private final Runnable onChange;
    private final PropertyChangeListener listener = this::changed;
    private PlacementsHolder<?> watched;

    /** @param onChange Run on the event queue after a change: Save and its chip brought up to date. */
    UnsavedChanges(Runnable onChange) {
        this.onChange = onChange;
    }

    /** The definition on show now; null for none. */
    void watch(PlacementsHolder<?> holder) {
        if (holder == watched) {
            return;
        }
        attach(watched, false);
        watched = holder;
        attach(holder, true);
    }

    /** "Modified · 2 places", "Modified" where nothing was counted, or null while it is saved. */
    String describe(PlacementsHolder<?> holder) {
        if (holder == null || !holder.isDirty()) {
            return null;
        }
        Set<String> places = changed.get(holder);
        return places == null || places.isEmpty() ? Translations.getString("UnsavedChanges.Modified") //$NON-NLS-1$
                : String.format(Translations.getString("UnsavedChanges.Places"), places.size()); //$NON-NLS-1$
    }

    private void attach(PlacementsHolder<?> holder, boolean on) {
        if (holder == null) {
            return;
        }
        listen(holder, on);
        for (Placement placement : holder.getPlacements()) {
            listen(placement, on);
        }
        if (holder instanceof Panel) {
            for (PlacementsHolderLocation<?> child : ((Panel) holder).getChildren()) {
                listen(child, on);
            }
            for (Placement fiducial : ((Panel) holder).getPseudoPlacements()) {
                listen(fiducial, on);
            }
        }
    }

    private void listen(Object object, boolean on) {
        if (object instanceof AbstractModelObject) {
            AbstractModelObject model = (AbstractModelObject) object;
            model.removePropertyChangeListener(listener);
            if (on) {
                model.addPropertyChangeListener(listener);
            }
        }
    }

    private void changed(PropertyChangeEvent e) {
        PlacementsHolder<?> holder = watched;
        if (holder == null || "dirty".equals(e.getPropertyName()) && e.getSource() != holder) { //$NON-NLS-1$
            return;
        }
        Set<String> places = changed.computeIfAbsent(holder, h -> new HashSet<>());
        if (e.getSource() == holder) {
            if ("dirty".equals(e.getPropertyName())) { //$NON-NLS-1$
                if (!holder.isDirty()) {
                    places.clear();
                }
            }
            else if (e instanceof IndexedPropertyChangeEvent) {
                // A placement, a child or a fiducial added or taken away.
                if (e.getOldValue() != null) {
                    listen(e.getOldValue(), false);
                    places.add(key(e.getOldValue()));
                }
                if (e.getNewValue() != null) {
                    listen(e.getNewValue(), true);
                    places.add(key(e.getNewValue()));
                }
            }
            else {
                places.add(e.getPropertyName());
            }
        }
        else {
            places.add(key(e.getSource()));
        }
        SwingUtilities.invokeLater(onChange);
    }

    private static String key(Object object) {
        if (object instanceof Placement) {
            return "placement:" + ((Placement) object).getId(); //$NON-NLS-1$
        }
        if (object instanceof PlacementsHolderLocation) {
            return "child:" + ((PlacementsHolderLocation<?>) object).getId(); //$NON-NLS-1$
        }
        return String.valueOf(System.identityHashCode(object));
    }
}
