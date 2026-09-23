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

package org.openpnp.gui.support;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.event.ContainerEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTable;
import javax.swing.ListCellRenderer;
import javax.swing.table.DefaultTableCellRenderer;

import org.openpnp.Translations;

/**
 * What the interface calls things. An enum value is looked up in the translations as
 * {@code Enum.<Type>.<VALUE>}, an object's type as {@code Type.<SimpleClassName>}; without a
 * translation the program's own name is shown, so nothing goes blank, and the UI audit lists what
 * is still English.
 * <p>
 * Only what is shown changes. The configuration keeps storing the constants, and the subject text
 * Issues and Solutions fingerprints its issues by keeps the English, so dismissed and solved issues
 * stay dismissed and solved.
 */
public final class DisplayNames {
    private DisplayNames() {
    }

    /** The translation key of an enum value: Enum.Placement.Type.Fiducial. */
    public static String key(Enum<?> value) {
        Class<?> type = value.getDeclaringClass();
        String name = type.getName().substring(type.getPackageName().length() + 1).replace('$', '.');
        return "Enum." + name + "." + value.name(); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** The name of an enum value as the interface shows it; anything else as its toString(). */
    public static String of(Object value) {
        if (value instanceof Enum) {
            String key = key((Enum<?>) value);
            if (Translations.has(key)) {
                return Translations.getString(key);
            }
        }
        return value == null ? "" : value.toString(); //$NON-NLS-1$
    }

    /** A constant as English words: UntilHomed is "Until homed", MAX_SPEED "Max speed". */
    public static String humanise(String constant) {
        String spaced = constant.replace('_', ' ').replaceAll("([a-z0-9])([A-Z])", "$1 $2") //$NON-NLS-1$ //$NON-NLS-2$
                .replaceAll("([A-Z])([A-Z][a-z])", "$1 $2").trim(); //$NON-NLS-1$ //$NON-NLS-2$
        if (spaced.equals(spaced.toUpperCase()) && spaced.length() > 3) {
            spaced = spaced.toLowerCase();
        }
        StringBuilder words = new StringBuilder();
        for (String word : spaced.split(" ")) { //$NON-NLS-1$
            if (word.isEmpty()) {
                continue;
            }
            boolean acronym = word.length() > 1 && word.equals(word.toUpperCase());
            String w = words.length() == 0 ? Character.toUpperCase(word.charAt(0)) + word.substring(1)
                    : acronym ? word : word.toLowerCase();
            words.append(words.length() == 0 ? "" : " ").append(w); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return words.toString();
    }

    /** Whether an enum constant is deprecated: kept for old configurations, not offered anew. */
    public static boolean isDeprecated(Enum<?> value) {
        try {
            return value.getDeclaringClass().getField(value.name()).isAnnotationPresent(Deprecated.class);
        }
        catch (NoSuchFieldException e) {
            return false;
        }
    }

    /**
     * The name of a type as the interface shows it: its own translation, or the nearest
     * superclass's, or the class name if none has one.
     */
    public static String typeName(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            String key = "Type." + c.getSimpleName(); //$NON-NLS-1$
            if (!c.getSimpleName().isEmpty() && Translations.has(key)) {
                return Translations.getString(key);
            }
        }
        return type.getSimpleName();
    }

    /** A sentence on what the type is for, or null. */
    public static String typeDescription(Class<?> type) {
        String key = "Type." + type.getSimpleName() + ".description"; //$NON-NLS-1$ //$NON-NLS-2$
        return Translations.has(key) ? Translations.getString(key) : null;
    }

    /**
     * The name of a vision setting as shown. The two the machine creates for itself are stored
     * under English names; they are shown in the display language.
     */
    public static String visionSettingsName(String name) {
        if ("- Default Machine Bottom Vision -".equals(name)) { //$NON-NLS-1$
            return Translations.getString("VisionSettings.Builtin.Bottom"); //$NON-NLS-1$
        }
        if ("- Default Machine Fiducial Locator -".equals(name)) { //$NON-NLS-1$
            return Translations.getString("VisionSettings.Builtin.Fiducial"); //$NON-NLS-1$
        }
        return name;
    }

    /** The title of a configured object: its type as the interface names it, and its name. */
    public static String title(Object object, String name) {
        String type = typeName(object.getClass());
        return name == null || name.isEmpty() ? type : type + " " + name; //$NON-NLS-1$
    }

    // ---- showing them -------------------------------------------------------------------------

    /**
     * A combo box's renderer, showing enum values by their display name, with the renderer it had
     * doing the rest. Deprecated values that are not selected are taken out of the list.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void install(JComboBox combo) {
        if (combo.getClientProperty(INSTALLED) != null || !holdsEnums(combo)) {
            return;
        }
        combo.putClientProperty(INSTALLED, Boolean.TRUE);
        ListCellRenderer delegate = combo.getRenderer();
        combo.setRenderer(new ListCellRenderer<Object>() {
            @Override
            public Component getListCellRendererComponent(JList<? extends Object> list,
                    Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component component = delegate.getListCellRendererComponent(list, value, index,
                        isSelected, cellHasFocus);
                if (value instanceof Enum && component instanceof JLabel) {
                    ((JLabel) component).setText(of(value));
                }
                return component;
            }
        });
        if (combo.getModel() instanceof DefaultComboBoxModel) {
            DefaultComboBoxModel model = (DefaultComboBoxModel) combo.getModel();
            List<Object> deprecated = new ArrayList<>();
            for (int i = 0; i < model.getSize(); i++) {
                Object item = model.getElementAt(i);
                if (item instanceof Enum && isDeprecated((Enum<?>) item) && item != model.getSelectedItem()) {
                    deprecated.add(item);
                }
            }
            for (Object item : deprecated) {
                model.removeElement(item);
            }
        }
    }

    private static boolean holdsEnums(JComboBox<?> combo) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i) instanceof Enum) {
                return true;
            }
        }
        return false;
    }

    private static final String INSTALLED = "Pono.displayNames"; //$NON-NLS-1$

    /** A table's cells of enum columns shown by their display names. */
    public static void install(JTable table) {
        if (table.getClientProperty(INSTALLED) != null) {
            return;
        }
        table.putClientProperty(INSTALLED, Boolean.TRUE);
        table.setDefaultRenderer(Enum.class, new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                setText(of(value));
            }
        });
    }

    private static boolean watching;

    /**
     * Shows every enum in every combo box and table by its display name, including those of the
     * wizards and dialogs built later: a component added anywhere is dressed as it arrives.
     */
    public static synchronized void installEverywhere() {
        if (watching) {
            return;
        }
        watching = true;
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event.getID() == ContainerEvent.COMPONENT_ADDED) {
                dress(((ContainerEvent) event).getChild());
            }
        }, AWTEvent.CONTAINER_EVENT_MASK);
    }

    @SuppressWarnings("rawtypes")
    private static void dress(Component component) {
        if (component instanceof JComboBox) {
            install((JComboBox) component);
        }
        else if (component instanceof JTable) {
            install((JTable) component);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                dress(child);
            }
        }
    }
}
