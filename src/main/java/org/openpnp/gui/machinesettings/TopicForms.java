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

import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;

/**
 * The forms of one topic, applied and reset together from the page's foot: each keeps its edits
 * until then, as every form does, but none shows a pair of buttons of its own.
 */
final class TopicForms implements WizardContainer {
    private final List<FormWizard> forms = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    /** Takes a form into the topic, shown among the others without a scroll bar of its own. */
    FormWizard add(FormWizard form) {
        form.setActionsShown(false);
        form.setEmbedded();
        form.setWizardContainer(this);
        form.getApplyAction().addPropertyChangeListener(e -> {
            if ("enabled".equals(e.getPropertyName())) { //$NON-NLS-1$
                changed();
            }
        });
        forms.add(form);
        return form;
    }

    /** Lets a form go, one the topic replaces as its selection changes; it is not disposed here. */
    void remove(FormWizard form) {
        forms.remove(form);
        changed();
    }

    boolean isEmpty() {
        return forms.isEmpty();
    }

    /** Whether a form has something on screen the objects do not hold. */
    boolean isDirty() {
        for (FormWizard form : forms) {
            if (form.hasEdits()) {
                return true;
            }
        }
        return false;
    }

    /** What Apply would write, form after form. */
    List<FormWizard.Change> changes() {
        List<FormWizard.Change> changes = new ArrayList<>();
        for (FormWizard form : forms) {
            if (form.hasEdits()) {
                changes.addAll(form.changes());
            }
        }
        return changes;
    }

    /** Applies the forms with edits; one whose Apply only lit up without any is reset instead. */
    void apply() {
        for (FormWizard form : new ArrayList<>(forms)) {
            if (form.hasEdits()) {
                form.apply();
            }
            else if (Boolean.TRUE.equals(form.isDirty())) {
                form.reset();
            }
        }
        changed();
    }

    void reset() {
        for (FormWizard form : new ArrayList<>(forms)) {
            if (Boolean.TRUE.equals(form.isDirty())) {
                form.reset();
            }
        }
        changed();
    }

    /** Shows again what the objects hold, after something other than the forms changed them. */
    void reload() {
        for (FormWizard form : forms) {
            form.reload();
        }
        changed();
    }

    void dispose() {
        for (FormWizard form : forms) {
            form.dispose();
        }
        forms.clear();
        changed();
    }

    void onChange(Runnable listener) {
        listeners.add(listener);
    }

    private void changed() {
        for (Runnable listener : new ArrayList<>(listeners)) {
            listener.run();
        }
    }

    @Override
    public void wizardCompleted(Wizard wizard) {
        changed();
    }

    @Override
    public void wizardCancelled(Wizard wizard) {
        changed();
    }
}
