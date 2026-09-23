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

package org.openpnp.gui.form;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.swing.JComponent;

import org.openpnp.Translations;

/**
 * A properties form described by its fields. Each object's form is one list of what it has to
 * show, in the order it shows them; the layout, the bindings, the apply and reset behaviour and
 * the look come from here, the same for every form.
 *
 * <pre>
 * Form.of(feeder)
 *     .section("\u57fa\u672c", "info").text("name", "\u540d\u79f0").toggle("enabled", "\u542f\u7528", "\u5141\u8bb8\u4efb\u52a1\u4f7f\u7528")
 *     .section("\u6599\u5e26\u51e0\u4f55", "move").length("tapeWidth", "\u6599\u5e26\u5bbd\u5ea6").width(84)
 *     .location("referenceHoleLocation", "\u53c2\u8003\u5b54 1", false)
 *     .action("\u81ea\u52a8\u8bbe\u7f6e", "zap", feeder::autoSetup).movesMachine()
 *     .build();
 * </pre>
 *
 * The rules every form keeps: one column, labels that wrap rather than being cut, units inside
 * the fields, rarely used sections folded, fields shown only when they mean something, errors
 * under the field they are about, nothing written until Apply, and a mark on every button that
 * moves the machine. Labels are translation keys where the old wizard had one, so translations
 * already made are kept; anything else is shown as given.
 * <p>
 * A property the bean does not have fails {@link Builder#build()} at once, which is what the form
 * tests rely on to catch a misspelt name.
 */
public final class Form {
    private Form() {
    }

    public static Builder of(Object bean) {
        return new Builder(bean);
    }

    /** What a field edits, and so which control it gets and how its text is converted. */
    enum Kind {
        Text, Integer, Decimal, Length, Angle, Location, Choice, Segmented, Toggle, ReadOnly,
        Pipeline, Action, Custom
    }

    /** One field, as the builder collected it. */
    static final class Field {
        final Kind kind;
        final String property;
        final String label;
        String unit;
        String note;
        int width;
        boolean movesMachine;
        boolean withRotation;
        boolean locationButtons;
        String icon;
        Runnable action;
        Runnable reset;
        java.util.function.Supplier<List<String>> stages;
        JComponent custom;
        List<?> items;
        Function<Object, String> itemNote;
        String visibleProperty;
        Predicate<Object> visibleWhen;
        final List<Predicate<Object>> checks = new ArrayList<>();
        final List<String> messages = new ArrayList<>();

        Field(Kind kind, String property, String label) {
            this.kind = kind;
            this.property = property;
            this.label = label;
        }
    }

    /** A titled group of fields. */
    static final class Section {
        final String title;
        final String icon;
        boolean collapsed;
        String note;
        final List<Field> fields = new ArrayList<>();

        Section(String title, String icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    /** A label as the form shows it: the translation of a key, or the text itself. */
    static String resolve(String keyOrText) {
        if (keyOrText == null) {
            return ""; //$NON-NLS-1$
        }
        return keyOrText.indexOf('.') > 0 && !keyOrText.contains(" ") && Translations.has(keyOrText) //$NON-NLS-1$
                ? Translations.getString(keyOrText) : keyOrText;
    }

    public static final class Builder {
        final Object bean;
        final List<Section> sections = new ArrayList<>();
        private Section section;
        private Field last;
        String name;

        Builder(Object bean) {
            this.bean = bean;
        }

        org.openpnp.model.DisplayPreferences preferences;

        /** The form's name, shown where the properties column needs one. */
        public Builder named(String name) {
            this.name = resolve(name);
            return this;
        }

        /**
         * The units and formats to show values in; without this, the running configuration's.
         * A form tested outside the application is given its own.
         */
        public Builder preferences(org.openpnp.model.DisplayPreferences preferences) {
            this.preferences = preferences;
            return this;
        }

        /** Starts a section. The icon is one of the mockups' icon set. */
        public Builder section(String title, String icon) {
            section = new Section(resolve(title), icon);
            sections.add(section);
            last = null;
            return this;
        }

        /** The current section starts folded. */
        public Builder collapsed() {
            current().collapsed = true;
            return this;
        }

        /**
         * A note: at the right of the section's heading when no field has been added yet, beside
         * the last field otherwise.
         */
        public Builder note(String text) {
            if (last == null) {
                current().note = resolve(text);
            }
            else {
                last.note = resolve(text);
            }
            return this;
        }

        public Builder text(String property, String label) {
            return add(Kind.Text, property, label);
        }

        public Builder integer(String property, String label) {
            return add(Kind.Integer, property, label);
        }

        public Builder decimal(String property, String label) {
            return add(Kind.Decimal, property, label);
        }

        /** A Length, shown in the display units, with the unit in the field. */
        public Builder length(String property, String label) {
            return add(Kind.Length, property, label);
        }

        /** An angle in degrees. */
        public Builder angle(String property, String label) {
            return add(Kind.Angle, property, label).unit("\u00b0"); //$NON-NLS-1$
        }

        /** A Location: X and Y, optionally Z and C, on one or two rows. */
        public Builder location(String property, String label, boolean withRotation) {
            add(Kind.Location, property, label);
            last.withRotation = withRotation;
            return this;
        }

        /** One of a list, enums by their display names; the note is grey after the name. */
        @SuppressWarnings("unchecked")
        public <T> Builder choice(String property, String label, List<T> items, Function<T, String> note) {
            add(Kind.Choice, property, label);
            last.items = items;
            last.itemNote = note == null ? null : v -> note.apply((T) v);
            return this;
        }

        /** One of an enum's values that are not deprecated. */
        public <E extends Enum<E>> Builder choice(String property, String label, Class<E> type) {
            return choice(property, label, current(type), null);
        }

        /** One of a few values as a row of segments, such as a tape width. */
        public Builder segmented(String property, String label, List<?> items) {
            add(Kind.Segmented, property, label);
            last.items = items;
            return this;
        }

        /** One of an enum's few values that are not deprecated, as segments. */
        public <E extends Enum<E>> Builder segmented(String property, String label, Class<E> type) {
            return segmented(property, label, current(type));
        }

        private static <E extends Enum<E>> List<E> current(Class<E> type) {
            List<E> values = new ArrayList<>();
            for (E value : type.getEnumConstants()) {
                if (!org.openpnp.gui.support.DisplayNames.isDeprecated(value)) {
                    values.add(value);
                }
            }
            return values;
        }

        /**
         * The last location gets the buttons that move the camera or the tool to it and capture
         * where they are.
         */
        public Builder locationButtons() {
            if (requireLast().kind != Kind.Location) {
                throw new IllegalStateException("location buttons belong to a location"); //$NON-NLS-1$
            }
            last.locationButtons = true;
            return this;
        }

        /**
         * A vision pipeline: its stages, read again whenever the form reloads, and the buttons that
         * edit it and put it back to the default.
         */
        public Builder pipeline(String label, java.util.function.Supplier<List<String>> stages,
                Runnable edit, Runnable reset) {
            add(Kind.Pipeline, null, label);
            last.stages = stages;
            last.action = edit;
            last.reset = reset;
            return this;
        }

        /** A boolean as a switch, with a few words on what it does. */
        public Builder toggle(String property, String label, String explanation) {
            add(Kind.Toggle, property, label);
            last.note = resolve(explanation);
            return this;
        }

        /** A value that is shown, not edited. */
        public Builder readOnly(String property, String label) {
            return add(Kind.ReadOnly, property, label);
        }

        /** A button in the form, such as a measurement or an automatic setup. */
        public Builder action(String label, String icon, Runnable action) {
            add(Kind.Action, null, label);
            last.icon = icon;
            last.action = action;
            return this;
        }

        /** Content no field type describes: a chart, a console, a tool changer path. */
        public Builder custom(String label, JComponent component) {
            add(Kind.Custom, null, label);
            last.custom = component;
            return this;
        }

        /**
         * The last field is shown only while another field's value, as it stands on screen,
         * satisfies the condition: a method choice deciding which of its settings mean anything.
         */
        public Builder visibleWhen(String property, Predicate<Object> condition) {
            requireLast().visibleProperty = property;
            last.visibleWhen = condition;
            return this;
        }

        /**
         * The last field's value must pass this before Apply writes anything; the message is shown
         * under the field while it does not.
         */
        public Builder validate(Predicate<Object> valid, String message) {
            requireLast().checks.add(valid);
            last.messages.add(resolve(message));
            return this;
        }

        public Builder unit(String unit) {
            requireLast().unit = unit;
            return this;
        }

        /** A fixed field width in pixels, for a short number that should not span the row. */
        public Builder width(int pixels) {
            requireLast().width = pixels;
            return this;
        }

        /** The last button moves the machine, and is marked so. */
        public Builder movesMachine() {
            requireLast().movesMachine = true;
            return this;
        }

        public FormWizard build() {
            for (Section s : sections) {
                for (Field field : s.fields) {
                    if (field.property != null) {
                        requireProperty(bean.getClass(), field.property, field.kind != Kind.ReadOnly);
                    }
                }
            }
            return new FormWizard(this);
        }

        private Builder add(Kind kind, String property, String label) {
            Section into = current();
            last = new Field(kind, property, resolve(label));
            into.fields.add(last);
            return this;
        }

        private Section current() {
            if (section == null) {
                section("", "info"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            return section;
        }

        private Field requireLast() {
            if (last == null) {
                throw new IllegalStateException("no field to apply this to");
            }
            return last;
        }
    }

    /**
     * Fails unless the type has the property, readable and, for an edited field, writable. This
     * is what catches a form that names a property the object does not have.
     */
    static PropertyDescriptor requireProperty(Class<?> type, String property, boolean writable) {
        try {
            BeanInfo info = Introspector.getBeanInfo(type);
            for (PropertyDescriptor descriptor : info.getPropertyDescriptors()) {
                if (descriptor.getName().equals(property)) {
                    if (descriptor.getReadMethod() == null) {
                        throw new IllegalArgumentException(type.getSimpleName() + "." + property + " cannot be read"); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                    if (writable && descriptor.getWriteMethod() == null) {
                        throw new IllegalArgumentException(type.getSimpleName() + "." + property + " cannot be written"); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                    return descriptor;
                }
            }
        }
        catch (IntrospectionException e) {
            throw new IllegalArgumentException(e);
        }
        throw new IllegalArgumentException(type.getSimpleName() + " has no property " + property); //$NON-NLS-1$
    }

    /** The properties a built form binds to, for the form tests. */
    public static List<String> properties(FormWizard form) {
        List<String> properties = new ArrayList<>();
        for (Section s : form.spec.sections) {
            for (Field field : s.fields) {
                if (field.property != null) {
                    properties.add(field.property);
                }
            }
        }
        return properties;
    }

    static List<Object> list(Object... items) {
        return Arrays.asList(items);
    }

    static Supplier<String> constant(String text) {
        return () -> text;
    }
}
