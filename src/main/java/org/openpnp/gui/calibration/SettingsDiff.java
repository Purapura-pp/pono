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

package org.openpnp.gui.calibration;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.openpnp.Translations;
import org.openpnp.model.LengthUnit;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * What changed in the machine between two copies of machine.xml, setting by setting: how the
 * calibration page says what a measurement changed before it is confirmed. Every element with a
 * name is a subject of its own, the axis x or the camera Top, and a setting is named from there.
 */
public final class SettingsDiff {
    /** One setting of one element, as it was and as it is. */
    public static final class Difference {
        private final String subject;
        private final String setting;
        private final String before;
        private final String after;

        Difference(String subject, String setting, String before, String after) {
            this.subject = subject;
            this.setting = setting;
            this.before = before;
            this.after = after;
        }

        public String getSubject() {
            return subject;
        }

        public String getSetting() {
            return setting;
        }

        public String getBefore() {
            return before;
        }

        public String getAfter() {
            return after;
        }

        @Override
        public String toString() {
            return subject + " \u00b7 " + setting + "  " + before + " \u2192 " + after; //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    /** What measuring writes besides settings: its results, and what was dealt with on the issues page. */
    private static final Set<String> SKIPPED = Set.of("machine-diagnostics", "solutions", //$NON-NLS-1$ //$NON-NLS-2$
            "dismissed-solutions", "solved-solutions"); //$NON-NLS-1$ //$NON-NLS-2$

    private SettingsDiff() {
    }

    public static List<Difference> between(String before, String after) throws Exception {
        List<Difference> differences = new ArrayList<>();
        if (before == null || after == null) {
            return differences;
        }
        compare(parse(before).getDocumentElement(), parse(after).getDocumentElement(),
                Translations.getString("CalibrationPlan.Machine"), "", differences); //$NON-NLS-1$ //$NON-NLS-2$
        return differences;
    }

    private static void compare(Element a, Element b, String subject, String path, List<Difference> out) {
        if (isSubject(b)) {
            subject = b.getAttribute("name"); //$NON-NLS-1$
            path = ""; //$NON-NLS-1$
        }
        if (isLength(a) && isLength(b)) {
            String was = value(a, "value"); //$NON-NLS-1$
            String is = value(b, "value"); //$NON-NLS-1$
            if (!was.equals(is)) {
                out.add(new Difference(subject, label(path, null), was, is));
            }
        }
        else {
            Set<String> attributes = new LinkedHashSet<>();
            attributes(a, attributes);
            attributes(b, attributes);
            attributes.remove("name"); //$NON-NLS-1$
            for (String attribute : attributes) {
                if (!Objects.equals(a.getAttribute(attribute), b.getAttribute(attribute))
                        || a.hasAttribute(attribute) != b.hasAttribute(attribute)) {
                    out.add(new Difference(subject, label(path, attribute), value(a, attribute),
                            value(b, attribute)));
                }
            }
        }
        Map<String, Element> was = children(a);
        Map<String, Element> is = children(b);
        for (Map.Entry<String, Element> entry : is.entrySet()) {
            Element child = entry.getValue();
            if (SKIPPED.contains(child.getTagName())) {
                continue;
            }
            String childPath = path.isEmpty() ? child.getTagName() : path + "/" + child.getTagName(); //$NON-NLS-1$
            Element before = was.get(entry.getKey());
            if (before == null) {
                out.add(new Difference(ownerOf(child, subject), label(childPath, null), "\u2014", //$NON-NLS-1$
                        Translations.getString("SettingsDiff.Added"))); //$NON-NLS-1$
            }
            else {
                compare(before, child, subject, childPath, out);
            }
        }
        for (Map.Entry<String, Element> entry : was.entrySet()) {
            Element child = entry.getValue();
            if (!is.containsKey(entry.getKey()) && !SKIPPED.contains(child.getTagName())) {
                String childPath = path.isEmpty() ? child.getTagName() : path + "/" + child.getTagName(); //$NON-NLS-1$
                out.add(new Difference(ownerOf(child, subject), label(childPath, null),
                        Translations.getString("SettingsDiff.Present"), "\u2014")); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
    }

    private static String ownerOf(Element element, String subject) {
        return isSubject(element) ? element.getAttribute("name") : subject; //$NON-NLS-1$
    }

    /**
     * An element of the machine, which has an id and a name: an axis, a camera, a nozzle tip. A
     * pipeline stage has a name and is part of what it is in.
     */
    private static boolean isSubject(Element element) {
        return !element.getAttribute("id").isEmpty() && !element.getAttribute("name").isEmpty(); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** A length as the configuration keeps one: a value and its units, and nothing else. */
    private static boolean isLength(Element element) {
        NamedNodeMap attributes = element.getAttributes();
        return attributes.getLength() == 2 && element.hasAttribute("value") && element.hasAttribute("units"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static void attributes(Element element, Set<String> into) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            into.add(attributes.item(i).getNodeName());
        }
    }

    /**
     * The element's children by what identifies them: the id, else the name, else the tag and
     * the place among its namesakes.
     */
    private static Map<String, Element> children(Element parent) {
        Map<String, Element> children = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element)) {
                continue;
            }
            Element child = (Element) node;
            String tag = child.getTagName();
            String key;
            if (!child.getAttribute("id").isEmpty()) { //$NON-NLS-1$
                key = tag + "#" + child.getAttribute("id"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            else if (!child.getAttribute("name").isEmpty()) { //$NON-NLS-1$
                key = tag + "@" + child.getAttribute("name"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            else {
                int n = counts.merge(tag, 1, Integer::sum);
                key = tag + "[" + n + "]"; //$NON-NLS-1$ //$NON-NLS-2$
            }
            children.put(key, child);
        }
        return children;
    }

    /**
     * "间隙补偿量" for the backlash offset of an axis, "头部偏移 X" for the x of a camera's head
     * offsets; the configuration's own name where there is no translation.
     */
    static String label(String path, String attribute) {
        String last = path.isEmpty() ? "" : path.substring(path.lastIndexOf('/') + 1); //$NON-NLS-1$
        if (attribute == null) {
            return named(last);
        }
        if (last.isEmpty()) {
            return named(attribute);
        }
        if (attribute.length() == 1 || "rotation".equals(attribute)) { //$NON-NLS-1$
            // The coordinate of a location: x, y, z, rotation.
            return named(last) + " " + (attribute.length() == 1 ? attribute.toUpperCase(Locale.ROOT) //$NON-NLS-1$
                    : named(attribute));
        }
        // Where the element has a name of its own, "标定 · 启用"; where it is only where the
        // setting is kept, as a pipeline stage is, the setting says it.
        return Translations.has("SettingsDiff." + last) ? named(last) + " \u00b7 " + named(attribute) //$NON-NLS-1$ //$NON-NLS-2$
                : named(attribute);
    }

    private static String named(String xmlName) {
        String key = "SettingsDiff." + xmlName; //$NON-NLS-1$
        return Translations.has(key) ? Translations.getString(key) : xmlName;
    }

    private static String value(Element element, String attribute) {
        if (!element.hasAttribute(attribute)) {
            return "\u2014"; //$NON-NLS-1$
        }
        String value = element.getAttribute(attribute);
        if ("true".equals(value)) { //$NON-NLS-1$
            return Translations.getString("Form.Change.On"); //$NON-NLS-1$
        }
        if ("false".equals(value)) { //$NON-NLS-1$
            return Translations.getString("Form.Change.Off"); //$NON-NLS-1$
        }
        String units = element.getAttribute("units"); //$NON-NLS-1$
        if (!"units".equals(attribute) && !units.isEmpty()) { //$NON-NLS-1$
            try {
                double number = Double.parseDouble(value);
                return number(number) + " " + LengthUnit.valueOf(units).getShortName(); //$NON-NLS-1$
            }
            catch (IllegalArgumentException e) {
                return value;
            }
        }
        try {
            return number(Double.parseDouble(value));
        }
        catch (NumberFormatException e) {
            return value;
        }
    }

    private static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e9) {
            return String.valueOf((long) value);
        }
        String text = String.format(Locale.ROOT, "%.4f", value); //$NON-NLS-1$
        return text.replaceAll("0+$", "").replaceAll("\\.$", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }
}
