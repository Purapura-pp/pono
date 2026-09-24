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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.openpnp.Translations;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.ReferenceNozzleTip;
import org.openpnp.machine.reference.camera.OpenPnpCaptureCamera;
import org.openpnp.machine.reference.driver.AbstractReferenceDriver;
import org.openpnp.machine.reference.driver.AbstractReferenceDriver.CommunicationsType;
import org.openpnp.machine.reference.driver.SerialPortCommunications;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Driver;
import org.openpnp.spi.Head;
import org.openpnp.spi.NozzleTip;
import org.openpnp.spi.base.AbstractHead;

/**
 * What the machine's own definition is missing on this computer, or has wrong: the settings a
 * machine settings topic is for, not anything calibration measures. A configuration made on a
 * LumenPnP running Linux names a port and cameras this computer does not have, and its nozzle
 * tips a smallest part of nothing; these were issues among the calibration's, to be accepted.
 * <p>
 * A check that holds for several objects is one check about all of them: the six nozzle tips with
 * the same diameter are one thing to fix, not six.
 */
public final class SetupChecks {
    /** One thing to set, in which topic, about what. */
    public static final class Check {
        public final String topic;
        public final String kind;
        public final List<Object> subjects = new ArrayList<>();
        final String textKey;
        final String fixKey;
        final List<String> names = new ArrayList<>();
        private final Object[] extra;

        Check(String topic, String kind, String textKey, String fixKey, Object... extra) {
            this.topic = topic;
            this.kind = kind;
            this.textKey = textKey;
            this.fixKey = fixKey;
            this.extra = extra;
        }

        /** What is wrong, naming what it is about: "Port ttyACM0 is not on this computer". */
        public String text() {
            List<Object> args = new ArrayList<>();
            args.add(String.join("\u3001", names)); //$NON-NLS-1$
            args.addAll(Arrays.asList(extra));
            return String.format(Translations.getString(textKey), args.toArray());
        }

        /** What to do about it. */
        public String fix() {
            return Translations.getString(fixKey);
        }

        /** The objects it is about, by name: "N045、N08" or "6 nozzle tips". */
        public String subject() {
            if (names.size() > 3) {
                return String.format(Translations.getString("MachineSettings.Check.Many." + kind), //$NON-NLS-1$
                        names.size());
            }
            return String.join("\u3001", names); //$NON-NLS-1$
        }
    }

    public static final String PORT_MISSING = "PortMissing"; //$NON-NLS-1$
    public static final String NO_PORT = "NoPort"; //$NON-NLS-1$
    public static final String DEVICE_MISSING = "DeviceMissing"; //$NON-NLS-1$
    public static final String NO_DEVICE = "NoDevice"; //$NON-NLS-1$
    public static final String MIN_DIAMETER = "MinDiameter"; //$NON-NLS-1$
    public static final String MAX_DIAMETER = "MaxDiameter"; //$NON-NLS-1$
    public static final String PICK_TOLERANCE = "PickTolerance"; //$NON-NLS-1$
    public static final String NO_PUMP = "NoPump"; //$NON-NLS-1$

    private final List<Check> checks;

    private SetupChecks(List<Check> checks) {
        this.checks = checks;
    }

    public static SetupChecks none() {
        return new SetupChecks(Collections.emptyList());
    }

    public static SetupChecks of(ReferenceMachine machine) {
        Map<String, Check> found = new LinkedHashMap<>();
        ports(machine, found);
        cameras(machine, found);
        nozzleTips(machine, found);
        pumps(machine, found);
        return new SetupChecks(new ArrayList<>(found.values()));
    }

    public List<Check> all() {
        return Collections.unmodifiableList(checks);
    }

    public List<Check> of(String topic) {
        List<Check> of = new ArrayList<>();
        for (Check check : checks) {
            if (check.topic.equals(topic)) {
                of.add(check);
            }
        }
        return of;
    }

    public int count(String topic) {
        return of(topic).size();
    }

    /**
     * Whether an issue of Issues and Solutions says what one of these checks says: the calibration
     * page leaves it to the machine settings page, which shows it where it is fixed.
     */
    public static boolean covers(org.openpnp.model.Solutions.Issue issue) {
        Object subject = issue.getSubject();
        String text = issue.getUntranslatedIssue();
        if (text == null || !(issue instanceof org.openpnp.model.Solutions.PlainIssue)) {
            return false;
        }
        if (subject instanceof ReferenceNozzleTip) {
            return text.contains("Max. Pick Tolerance") || text.contains("Min. Part Diameter") //$NON-NLS-1$ //$NON-NLS-2$
                    || text.contains("Max. Part Diameter"); //$NON-NLS-1$
        }
        if (subject instanceof OpenPnpCaptureCamera) {
            return text.equals(org.openpnp.machine.reference.solutions.CameraSolutions.NOT_CONNECTED);
        }
        return false;
    }

    /** The check of a kind that is about the object, if any: for a hint beside its field. */
    public Check about(String kind, Object subject) {
        for (Check check : checks) {
            if (check.kind.equals(kind) && check.subjects.contains(subject)) {
                return check;
            }
        }
        return null;
    }

    private static void add(Map<String, Check> found, Check check, Object subject, String name) {
        Check into = found.computeIfAbsent(check.kind + check.textKey, k -> check);
        into.subjects.add(subject);
        into.names.add(name);
    }

    // ---- the checks ---------------------------------------------------------------------------

    private static void ports(ReferenceMachine machine, Map<String, Check> found) {
        List<String> ports = null;
        for (Driver driver : machine.getDrivers()) {
            if (!(driver instanceof AbstractReferenceDriver)) {
                continue;
            }
            AbstractReferenceDriver reference = (AbstractReferenceDriver) driver;
            if (reference.isInSimulationMode() || reference.getCommunicationsType() != CommunicationsType.serial) {
                continue;
            }
            String port = reference.getPortName();
            if (port == null || port.trim().isEmpty()) {
                add(found, new Check(MachineSettingsPanel.CONNECTION, NO_PORT,
                        "MachineSettings.Check.NoPort", "MachineSettings.Check.NoPort.Fix"), //$NON-NLS-1$ //$NON-NLS-2$
                        driver, driver.getName());
                continue;
            }
            if (ports == null) {
                try {
                    ports = Arrays.asList(SerialPortCommunications.getPortNames());
                }
                catch (Throwable e) {
                    // No serial library here: nothing can be said about the ports.
                    return;
                }
            }
            if (!ports.contains(port)) {
                add(found, new Check(MachineSettingsPanel.CONNECTION, PORT_MISSING,
                        "MachineSettings.Check.PortMissing", "MachineSettings.Check.PortMissing.Fix", port), //$NON-NLS-1$ //$NON-NLS-2$
                        driver, driver.getName());
            }
        }
    }

    private static void cameras(ReferenceMachine machine, Map<String, Check> found) {
        for (Camera camera : allCameras(machine)) {
            if (!(camera instanceof OpenPnpCaptureCamera)) {
                continue;
            }
            OpenPnpCaptureCamera capture = (OpenPnpCaptureCamera) camera;
            try {
                if (capture.getDeviceId() == null) {
                    add(found, new Check(MachineSettingsPanel.CAMERAS, NO_DEVICE,
                            "MachineSettings.Check.NoDevice", "MachineSettings.Check.NoDevice.Fix"), //$NON-NLS-1$ //$NON-NLS-2$
                            camera, camera.getName());
                }
                else if (!capture.isDeviceAvailable()) {
                    add(found, new Check(MachineSettingsPanel.CAMERAS, DEVICE_MISSING,
                            "MachineSettings.Check.DeviceMissing", "MachineSettings.Check.DeviceMissing.Fix"), //$NON-NLS-1$ //$NON-NLS-2$
                            camera, camera.getName());
                }
            }
            catch (Throwable e) {
                // The capture library did not load: the camera cannot be asked.
            }
        }
    }

    /** The head's cameras and the machine's, the bottom one among them. */
    static List<Camera> allCameras(ReferenceMachine machine) {
        List<Camera> cameras = new ArrayList<>();
        for (Head head : machine.getHeads()) {
            cameras.addAll(head.getCameras());
        }
        cameras.addAll(machine.getCameras());
        return cameras;
    }

    /**
     * The nozzle tips' part diameters and pick tolerance, as the nozzle tip calibration needs
     * them: the rule is the one Issues and Solutions applied, and only where the calibration is on.
     */
    private static void nozzleTips(ReferenceMachine machine, Map<String, Check> found) {
        for (NozzleTip tip : machine.getNozzleTips()) {
            if (!(tip instanceof ReferenceNozzleTip)) {
                continue;
            }
            ReferenceNozzleTip nozzleTip = (ReferenceNozzleTip) tip;
            if (!nozzleTip.getCalibration().isEnabled()) {
                continue;
            }
            if (nozzleTip.getMaxPickTolerance().compareTo(new Length(1.0, LengthUnit.Millimeters)) > 0) {
                add(found, new Check(MachineSettingsPanel.NOZZLES, PICK_TOLERANCE,
                        "MachineSettings.Check.PickTolerance", "MachineSettings.Check.PickTolerance.Fix"), //$NON-NLS-1$ //$NON-NLS-2$
                        tip, tip.getName());
            }
            else if (!minimumValid(nozzleTip.getMinPartDiameter(), nozzleTip.getMaxPickTolerance())) {
                add(found, new Check(MachineSettingsPanel.NOZZLES, MIN_DIAMETER,
                        "MachineSettings.Check.MinDiameter", "MachineSettings.Check.MinDiameter.Fix"), //$NON-NLS-1$ //$NON-NLS-2$
                        tip, tip.getName());
            }
            else if (nozzleTip.getMinPartDiameter().compareTo(nozzleTip.getMaxPartDiameter()) >= 0) {
                add(found, new Check(MachineSettingsPanel.NOZZLES, MAX_DIAMETER,
                        "MachineSettings.Check.MaxDiameter", "MachineSettings.Check.MaxDiameter.Fix"), //$NON-NLS-1$ //$NON-NLS-2$
                        tip, tip.getName());
            }
        }
    }

    /** A smallest part must be larger than twice the pick tolerance, or the tip's bore is all there is. */
    static boolean minimumValid(Length minimum, Length pickTolerance) {
        return minimum != null && pickTolerance != null && minimum.compareTo(pickTolerance.multiply(2)) > 0;
    }

    private static void pumps(ReferenceMachine machine, Map<String, Check> found) {
        for (Head head : machine.getHeads()) {
            if (head instanceof AbstractHead && ((AbstractHead) head).getPumpActuator() == null
                    && ((AbstractHead) head).getVacuumPumpControl() != AbstractHead.VacuumPumpControl.None
                    && !head.getNozzles().isEmpty()) {
                add(found, new Check(MachineSettingsPanel.NOZZLES, NO_PUMP,
                        "MachineSettings.Check.NoPump", "MachineSettings.Check.NoPump.Fix"), //$NON-NLS-1$ //$NON-NLS-2$
                        head, head.getName());
            }
        }
    }
}
