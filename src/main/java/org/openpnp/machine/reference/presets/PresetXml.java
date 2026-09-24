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

package org.openpnp.machine.reference.presets;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.openpnp.model.LengthUnit;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * A machine.xml as a document rather than a machine: what a preset leaves out of it, what
 * applying one carries over from the configuration it replaces, and what it is in a few words.
 * Working on the file keeps the machine running in the program out of it until the restart,
 * which loads the new one the ordinary way.
 */
public final class PresetXml {
    private PresetXml() {
    }

    // ---- reading and writing ------------------------------------------------------------------

    public static Document parse(InputStream in) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory.newDocumentBuilder().parse(in);
    }

    public static Document parse(File file) throws Exception {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            return parse(in);
        }
    }

    public static void write(Document document, File file) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8"); //$NON-NLS-1$
        transformer.setOutputProperty(OutputKeys.STANDALONE, "yes"); //$NON-NLS-1$
        try (OutputStream out = Files.newOutputStream(file.toPath())) {
            transformer.transform(new DOMSource(document), new StreamResult(out));
        }
    }

    /** The machine element under openpnp-machine. */
    static Element machine(Document document) {
        return child(document.getDocumentElement(), "machine"); //$NON-NLS-1$
    }

    static Element child(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element && ((Element) node).getTagName().equals(name)) {
                return (Element) node;
            }
        }
        return null;
    }

    static List<Element> children(Element parent, String name) {
        List<Element> children = new ArrayList<>();
        if (parent == null) {
            return children;
        }
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element && ((Element) node).getTagName().equals(name)) {
                children.add((Element) node);
            }
        }
        return children;
    }

    static List<Element> descendants(Element root, String name) {
        List<Element> found = new ArrayList<>();
        if (root == null) {
            return found;
        }
        NodeList nodes = root.getElementsByTagName(name);
        for (int i = 0; i < nodes.getLength(); i++) {
            found.add((Element) nodes.item(i));
        }
        return found;
    }

    private static void clear(Element element) {
        if (element == null) {
            return;
        }
        while (element.getFirstChild() != null) {
            element.removeChild(element.getFirstChild());
        }
    }

    // ---- what a preset leaves out -------------------------------------------------------------

    /**
     * Takes out what belongs to one machine on one computer: the serial ports and the cameras'
     * devices always; the feeders and the calibration's records - what it solved, dismissed and
     * measured - unless they are to go with it.
     */
    public static void stripForPreset(Document document, boolean feeders, boolean records) {
        Element machine = machine(document);
        for (Element serial : descendants(machine, "serial")) { //$NON-NLS-1$
            serial.removeAttribute("port-name"); //$NON-NLS-1$
        }
        for (Element camera : descendants(machine, "camera")) { //$NON-NLS-1$
            camera.removeAttribute("unique-id"); //$NON-NLS-1$
            camera.removeAttribute("format-id"); //$NON-NLS-1$
        }
        if (!feeders) {
            clear(child(machine, "feeders")); //$NON-NLS-1$
        }
        if (!records) {
            Element solutions = child(machine, "solutions"); //$NON-NLS-1$
            clear(child(solutions, "dismissed-solutions")); //$NON-NLS-1$
            clear(child(solutions, "solved-solutions")); //$NON-NLS-1$
            Element diagnostics = child(machine, "machine-diagnostics"); //$NON-NLS-1$
            Element results = child(diagnostics, "last-results"); //$NON-NLS-1$
            if (results != null) {
                diagnostics.removeChild(results);
            }
        }
        machine.removeAttribute("preset-name"); //$NON-NLS-1$
        machine.removeAttribute("preset-built-in"); //$NON-NLS-1$
    }

    // ---- what applying one keeps --------------------------------------------------------------

    /**
     * Puts into the preset's machine what the configuration it replaces keeps: its feeders, and
     * the port and camera devices of this computer where the preset has none - by the driver's or
     * the camera's name, the first serial port otherwise - and records where it came from.
     */
    public static void carryOver(Document preset, Document current, String presetName, boolean builtIn) {
        Element into = machine(preset);
        Element from = machine(current);
        Element feeders = child(into, "feeders"); //$NON-NLS-1$
        Element kept = child(from, "feeders"); //$NON-NLS-1$
        if (feeders == null) {
            feeders = preset.createElement("feeders"); //$NON-NLS-1$
            into.appendChild(feeders);
        }
        clear(feeders);
        if (kept != null) {
            for (Node node = kept.getFirstChild(); node != null; node = node.getNextSibling()) {
                feeders.appendChild(preset.importNode(node, true));
            }
        }

        List<Element> drivers = children(child(from, "drivers"), "driver"); //$NON-NLS-1$ //$NON-NLS-2$
        String firstPort = null;
        for (Element driver : drivers) {
            String port = portOf(driver);
            if (port != null) {
                firstPort = port;
                break;
            }
        }
        for (Element driver : children(child(into, "drivers"), "driver")) { //$NON-NLS-1$ //$NON-NLS-2$
            Element serial = child(driver, "serial"); //$NON-NLS-1$
            if (serial == null || !serial.getAttribute("port-name").isEmpty()) { //$NON-NLS-1$
                continue;
            }
            String port = null;
            for (Element was : drivers) {
                if (was.getAttribute("name").equals(driver.getAttribute("name"))) { //$NON-NLS-1$ //$NON-NLS-2$
                    port = portOf(was);
                }
            }
            port = port != null ? port : firstPort;
            if (port != null) {
                serial.setAttribute("port-name", port); //$NON-NLS-1$
            }
        }

        Map<String, Element> cameras = new LinkedHashMap<>();
        for (Element camera : descendants(from, "camera")) { //$NON-NLS-1$
            cameras.put(camera.getAttribute("name"), camera); //$NON-NLS-1$
        }
        for (Element camera : descendants(into, "camera")) { //$NON-NLS-1$
            if (!camera.getAttribute("unique-id").isEmpty()) { //$NON-NLS-1$
                continue;
            }
            Element was = cameras.get(camera.getAttribute("name")); //$NON-NLS-1$
            if (was != null && was.getAttribute("class").equals(camera.getAttribute("class")) //$NON-NLS-1$ //$NON-NLS-2$
                    && !was.getAttribute("unique-id").isEmpty()) { //$NON-NLS-1$
                camera.setAttribute("unique-id", was.getAttribute("unique-id")); //$NON-NLS-1$ //$NON-NLS-2$
                if (!was.getAttribute("format-id").isEmpty()) { //$NON-NLS-1$
                    camera.setAttribute("format-id", was.getAttribute("format-id")); //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
        }
        into.setAttribute("preset-name", presetName); //$NON-NLS-1$
        into.setAttribute("preset-built-in", String.valueOf(builtIn)); //$NON-NLS-1$
    }

    private static String portOf(Element driver) {
        Element serial = child(driver, "serial"); //$NON-NLS-1$
        String port = serial == null ? "" : serial.getAttribute("port-name"); //$NON-NLS-1$ //$NON-NLS-2$
        return port.isEmpty() ? null : port;
    }

    /**
     * The preset's vision settings over the configuration's, by id: one of the same id is
     * replaced, a new one added, and the rest - what the parts and packages refer to - kept.
     */
    public static void mergeVision(Document into, Document from) {
        Element root = into.getDocumentElement();
        Map<String, Element> byId = new LinkedHashMap<>();
        for (Element settings : children(root, "vision-settings")) { //$NON-NLS-1$
            byId.put(settings.getAttribute("id"), settings); //$NON-NLS-1$
        }
        for (Element settings : children(from.getDocumentElement(), "vision-settings")) { //$NON-NLS-1$
            Node imported = into.importNode(settings, true);
            Element existing = byId.get(settings.getAttribute("id")); //$NON-NLS-1$
            if (existing != null) {
                root.replaceChild(imported, existing);
            }
            else {
                root.appendChild(imported);
            }
        }
    }

    // ---- what it is ---------------------------------------------------------------------------

    /** A machine.xml in the words a preset's card and the apply question use. */
    public static final class Summary {
        public final NozzleLayout nozzles;
        public final List<String> nozzleNames = new ArrayList<>();
        public final List<String> tips = new ArrayList<>();
        /** The X and Y travel between the soft limits in millimetres, or null where they are off. */
        public final Double travelX;
        public final Double travelY;
        public final List<String> cameras = new ArrayList<>();
        public final int camerasDown;
        public final int camerasUp;
        /** FIRMWARE_NAME as the controller reported it, the URL after it left out; null if never asked. */
        public final String firmware;
        /** The first driver's class, simple, and its baud rate and port where it has a serial port. */
        public final String driver;
        public final int baud;
        public final String port;
        /** Whether the driver reaches its controller over the network rather than a serial port. */
        public final boolean tcp;
        public final int feeders;
        public final String presetName;

        Summary(Document document) {
            Element machine = machine(document);
            Map<String, Element> axes = new LinkedHashMap<>();
            for (Element axis : children(child(machine, "axes"), "axis")) { //$NON-NLS-1$ //$NON-NLS-2$
                axes.put(axis.getAttribute("id"), axis); //$NON-NLS-1$
            }
            List<NozzleLayout.Kind> kinds = new ArrayList<>();
            int down = 0;
            int up = 0;
            for (Element head : children(child(machine, "heads"), "head")) { //$NON-NLS-1$ //$NON-NLS-2$
                for (Element nozzle : children(child(head, "nozzles"), "nozzle")) { //$NON-NLS-1$ //$NON-NLS-2$
                    nozzleNames.add(nozzle.getAttribute("name")); //$NON-NLS-1$
                    Element z = axes.get(nozzle.getAttribute("axis-Z-id")); //$NON-NLS-1$
                    kinds.add(z == null ? NozzleLayout.Kind.Other : NozzleLayout.kindOf(z.getAttribute("class"))); //$NON-NLS-1$
                }
                for (Element camera : children(child(head, "cameras"), "camera")) { //$NON-NLS-1$ //$NON-NLS-2$
                    cameras.add(camera.getAttribute("name")); //$NON-NLS-1$
                    if ("Up".equals(camera.getAttribute("looking"))) { //$NON-NLS-1$ //$NON-NLS-2$
                        up++;
                    }
                    else {
                        down++;
                    }
                }
            }
            for (Element camera : children(child(machine, "cameras"), "camera")) { //$NON-NLS-1$ //$NON-NLS-2$
                cameras.add(camera.getAttribute("name")); //$NON-NLS-1$
                if ("Down".equals(camera.getAttribute("looking"))) { //$NON-NLS-1$ //$NON-NLS-2$
                    down++;
                }
                else {
                    up++;
                }
            }
            camerasDown = down;
            camerasUp = up;
            nozzles = NozzleLayout.of(kinds);
            for (Element tip : children(child(machine, "nozzle-tips"), "nozzle-tip")) { //$NON-NLS-1$ //$NON-NLS-2$
                tips.add(tip.getAttribute("name")); //$NON-NLS-1$
            }
            travelX = travel(axes, "X"); //$NON-NLS-1$
            travelY = travel(axes, "Y"); //$NON-NLS-1$
            Element driverElement = null;
            for (Element each : children(child(machine, "drivers"), "driver")) { //$NON-NLS-1$ //$NON-NLS-2$
                if (child(each, "serial") != null || child(each, "detected-firmware") != null) { //$NON-NLS-1$ //$NON-NLS-2$
                    driverElement = each;
                    break;
                }
                if (driverElement == null) {
                    driverElement = each;
                }
            }
            if (driverElement == null) {
                // An old configuration keeps its one driver on the machine itself.
                driverElement = child(machine, "driver"); //$NON-NLS-1$
            }
            String className = driverElement == null ? "" : driverElement.getAttribute("class"); //$NON-NLS-1$ //$NON-NLS-2$
            driver = className.substring(className.lastIndexOf('.') + 1);
            Element serial = child(driverElement, "serial"); //$NON-NLS-1$
            tcp = driverElement != null && "tcp".equals(driverElement.getAttribute("communications")); //$NON-NLS-1$ //$NON-NLS-2$
            boolean serialUsed = driverElement != null && !tcp && serial != null;
            int rate = 0;
            try {
                rate = serialUsed ? Integer.parseInt(serial.getAttribute("baud")) : 0; //$NON-NLS-1$
            }
            catch (NumberFormatException e) {
                // No baud rate written: the driver's default applies, which is not ours to say.
            }
            baud = rate;
            port = serialUsed && !serial.getAttribute("port-name").isEmpty() ? serial.getAttribute("port-name") : null; //$NON-NLS-1$ //$NON-NLS-2$
            Element detected = child(driverElement, "detected-firmware"); //$NON-NLS-1$
            firmware = detected == null ? null : firmwareName(detected.getTextContent());
            Element feederList = child(machine, "feeders"); //$NON-NLS-1$
            feeders = feederList == null ? 0 : children(feederList, "feeder").size(); //$NON-NLS-1$
            String preset = machine.getAttribute("preset-name"); //$NON-NLS-1$
            presetName = preset.isEmpty() ? null : preset;
        }

        private static Double travel(Map<String, Element> axes, String type) {
            for (Element axis : axes.values()) {
                if (!type.equals(axis.getAttribute("type")) //$NON-NLS-1$
                        || NozzleLayout.kindOf(axis.getAttribute("class")) != NozzleLayout.Kind.Controller) { //$NON-NLS-1$
                    continue;
                }
                if (!"true".equals(axis.getAttribute("soft-limit-low-enabled")) //$NON-NLS-1$ //$NON-NLS-2$
                        || !"true".equals(axis.getAttribute("soft-limit-high-enabled"))) { //$NON-NLS-1$ //$NON-NLS-2$
                    return null;
                }
                Double low = millimetres(child(axis, "soft-limit-low")); //$NON-NLS-1$
                Double high = millimetres(child(axis, "soft-limit-high")); //$NON-NLS-1$
                return low == null || high == null ? null : high - low;
            }
            return null;
        }

        private static Double millimetres(Element length) {
            if (length == null) {
                return null;
            }
            try {
                double value = Double.parseDouble(length.getAttribute("value")); //$NON-NLS-1$
                String units = length.getAttribute("units"); //$NON-NLS-1$
                return units.isEmpty() ? value
                        : new org.openpnp.model.Length(value, LengthUnit.valueOf(units))
                                .convertToUnits(LengthUnit.Millimeters).getValue();
            }
            catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private static final Pattern FIRMWARE = Pattern.compile("FIRMWARE_NAME:\\s*(.*?)(?:\\s+[A-Z][A-Z0-9_]+:|$)", //$NON-NLS-1$
            Pattern.DOTALL);

    /** "Marlin bugfix-2.1.x" out of what M115 answered. */
    static String firmwareName(String report) {
        if (report == null) {
            return null;
        }
        Matcher matcher = FIRMWARE.matcher(report);
        if (!matcher.find()) {
            return null;
        }
        String name = matcher.group(1).trim();
        int bracket = name.indexOf('(');
        name = (bracket > 0 ? name.substring(0, bracket) : name).trim();
        return name.isEmpty() ? null : name;
    }

    public static Summary summary(Document document) {
        return new Summary(document);
    }
}
