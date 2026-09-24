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

package org.openpnp.machine.reference.driver.wizards;

import java.awt.Cursor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JTextArea;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.form.WeakForward;
import org.openpnp.gui.shell.Forms;
import org.openpnp.machine.reference.driver.AbstractReferenceDriver;
import org.openpnp.machine.reference.driver.AbstractReferenceDriver.CommunicationsType;
import org.openpnp.machine.reference.driver.GcodeAsyncDriver;
import org.openpnp.machine.reference.driver.GcodeDriver;
import org.openpnp.machine.reference.driver.ReferenceDriverCommunications.LineEndingType;
import org.openpnp.machine.reference.driver.SerialPortCommunications;
import org.openpnp.model.LengthUnit;
import org.openpnp.spi.Driver.MotionControlType;
import org.openpnp.util.UiUtils;

/**
 * The drivers' forms: how every driver connects, and a G-code driver's settings and its
 * asynchronous variant's.
 */
public final class DriverForms {
    private DriverForms() {
    }

    private static final String TYPE = "communicationsType"; //$NON-NLS-1$

    private static final List<Integer> BAUDS = Arrays.asList(110, 134, 150, 200, 300, 600, 1200, 1800, 2400, 4800,
            9600, 14400, 19200, 38400, 56000, 57600, 76800, 115200, 128000, 153600, 230400, 250000, 256000, 307200,
            460800, 500000, 576000, 614400, 921600, 1000000, 1152000, 1500000, 2000000, 2500000, 3000000, 3500000,
            4000000);

    private static List<String> ports(AbstractReferenceDriver driver) {
        List<String> ports = new ArrayList<>();
        try {
            ports.addAll(Arrays.asList(SerialPortCommunications.getPortNames()));
        }
        catch (Throwable e) {
            // No serial library on this system: the port the driver has is still shown.
        }
        if (driver.getPortName() != null && !ports.contains(driver.getPortName())) {
            ports.add(0, driver.getPortName());
        }
        return ports;
    }

    /** How the driver reaches its controller, the part every driver has. */
    public static FormWizard communications(AbstractReferenceDriver driver) {
        List<Integer> bauds = new ArrayList<>(BAUDS);
        if (!bauds.contains(driver.getBaud())) {
            bauds.add(driver.getBaud());
        }
        return Form.of(driver).named("DriverForms.Communications") //$NON-NLS-1$
                .section("DriverForms.Basics", "machine") //$NON-NLS-1$ //$NON-NLS-2$
                .text("name", "AbstractReferenceDriverConfigurationWizard.ControllerPanel.NameLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("syncInitialLocation", "DriverForms.SyncInitial", "DriverForms.SyncInitial.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .toggle("allowUnhomedMotion", "DriverForms.Unhomed", "DriverForms.Unhomed.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen("syncInitialLocation", Boolean.TRUE::equals) //$NON-NLS-1$
                .section("AbstractReferenceDriverConfigurationWizard.CommunicationMethodPanel.Border.title", "zap") //$NON-NLS-1$ //$NON-NLS-2$
                .segmented(TYPE, "AbstractReferenceDriverConfigurationWizard.CommunicationMethodPanel.CommunicationTypeLabel.text", //$NON-NLS-1$
                        CommunicationsType.class)
                .choice("lineEndingType", "DriverForms.LineEndings", LineEndingType.class) //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("connectionKeepAlive", "AbstractReferenceDriverConfigurationWizard.CommunicationMethodPanel.KeepAliveLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        "DriverForms.KeepAlive.Note") //$NON-NLS-1$
                .section("AbstractReferenceDriverConfigurationWizard.CommunicationMethodPanel.SerialPortLabel.text", "power") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("portName", "AbstractReferenceDriverConfigurationWizard.CommunicationMethodPanel.PortLabel.text", ports(driver), null) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen(TYPE, CommunicationsType.serial::equals)
                .choice("baud", "AbstractReferenceDriverConfigurationWizard.CommunicationMethodPanel.BaudLabel.text", bauds, null) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen(TYPE, CommunicationsType.serial::equals)
                .choice("parity", "AbstractReferenceDriverConfigurationWizard.CommunicationMethodPanel.ParityLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        SerialPortCommunications.Parity.class)
                .visibleWhen(TYPE, CommunicationsType.serial::equals)
                .choice("dataBits", "AbstractReferenceDriverConfigurationWizard.CommunicationMethodPanel.DataBitsLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        SerialPortCommunications.DataBits.class)
                .visibleWhen(TYPE, CommunicationsType.serial::equals)
                .choice("stopBits", "AbstractReferenceDriverConfigurationWizard.CommunicationMethodPanel.StopBitsLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        SerialPortCommunications.StopBits.class)
                .visibleWhen(TYPE, CommunicationsType.serial::equals)
                .choice("flowControl", "AbstractReferenceDriverConfigurationWizard.CommunicationMethodPanel.FlowControlLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        SerialPortCommunications.FlowControl.class)
                .visibleWhen(TYPE, CommunicationsType.serial::equals)
                .toggle("setDtr", "DriverForms.Dtr", "DriverForms.Dtr.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen(TYPE, CommunicationsType.serial::equals)
                .toggle("setRts", "DriverForms.Rts", "DriverForms.Rts.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen(TYPE, CommunicationsType.serial::equals)
                .section("AbstractReferenceDriverConfigurationWizard.TCPPanel.Border.title", "globe") //$NON-NLS-1$ //$NON-NLS-2$
                .text("ipAddress", "AbstractReferenceDriverConfigurationWizard.TCPPanel.IPAddressLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen(TYPE, CommunicationsType.tcp::equals)
                .hint("AbstractReferenceDriverConfigurationWizard.TCPPanel.IPAddressLabel.toolTipText") //$NON-NLS-1$
                .integer("port", "AbstractReferenceDriverConfigurationWizard.TCPPanel.PortLabel.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen(TYPE, CommunicationsType.tcp::equals)
                .onApply(f -> {
                    if (driver.isSyncInitialLocation() && driver.getMachine().isEnabled()) {
                        // Enabled just now, perhaps: the location is read at once.
                        UiUtils.submitUiMachineTask(() -> {
                            driver.getReportedLocation(-1);
                        });
                    }
                })
                .build();
    }

    public static FormWizard gcodeSettings(GcodeDriver driver) {
        JTextArea firmware = Forms.paragraph(driver.getFirmwareConfiguration());
        WeakForward.listen(driver, firmware, (area, e) -> {
            if ("firmwareConfiguration".equals(e.getPropertyName())) { //$NON-NLS-1$
                area.setText((String) e.getNewValue());
            }
        });
        FormWizard[] form = new FormWizard[1];
        form[0] = Form.of(driver).named("DriverForms.Settings") //$NON-NLS-1$
                .section("DriverForms.Motion", "move") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("motionControlType", "GcodeDriverSettings.SettingsPanel.MotionControlTypeLabel.text", MotionControlType.class) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("DriverForms.MotionControl.Hint") //$NON-NLS-1$
                .choice("units", "GcodeDriverSettings.SettingsPanel.UnitsLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        List.of(LengthUnit.Millimeters, LengthUnit.Inches), null)
                .integer("maxFeedRate", "DriverForms.MaxFeedRate").unit("DriverForms.PerMinute").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("DriverForms.MaxFeedRate.Hint") //$NON-NLS-1$
                .section("DriverForms.Timing", "clock") //$NON-NLS-1$ //$NON-NLS-2$
                .integer("timeoutMilliseconds", "DriverForms.Timeout").unit("ms").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .integer("connectWaitTimeMilliseconds", "DriverForms.ConnectWait").unit("ms").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .integer("dollarWaitTimeMilliseconds", "DriverForms.DollarWait").unit("ms").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("DriverForms.DollarWait.Hint") //$NON-NLS-1$
                .section("DriverForms.Gcode", "file") //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("removeComments", "DriverForms.RemoveComments", "DriverForms.RemoveComments.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .toggle("compressGcode", "DriverForms.Compress", "DriverForms.Compress.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .text("compressionExcludes", "DriverForms.CompressExcludes") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleWhen("compressGcode", Boolean.TRUE::equals) //$NON-NLS-1$
                .hint("DriverForms.CompressExcludes.Hint") //$NON-NLS-1$
                .toggle("backslashEscapedCharactersEnabled", "DriverForms.Escapes", "DriverForms.Escapes.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .toggle("supportingPreMove", "DriverForms.PreMove", "DriverForms.PreMove.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .toggle("usingLetterVariables", "DriverForms.LetterVariables", "DriverForms.LetterVariables.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .toggle("loggingGcode", "DriverForms.Log", "DriverForms.Log.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .section("DriverForms.OnChange", "filter").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("sendOnChangeFeedRate", "DriverForms.OnChange.FeedRate", "DriverForms.OnChange.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .toggle("sendOnChangeAcceleration", "DriverForms.OnChange.Acceleration", "DriverForms.OnChange.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .toggle("sendOnChangeJerk", "DriverForms.OnChange.Jerk", "DriverForms.OnChange.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .section("DriverForms.Firmware", "info") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", firmware) //$NON-NLS-1$
                .action("GcodeDriverSettings.SettingsPanel.DetectFirmwareButton.text", "search", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                    MainFrame.get().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    firmware.setText(Translations.getString(
                            "GcodeDriverSettings.SettingsPanel.FirmwareConfigurationTextArea.Detecting.text")); //$NON-NLS-1$
                    try {
                        UiUtils.messageBoxOnException(() -> driver.detectFirmware(false, true));
                    }
                    finally {
                        MainFrame.get().setCursor(Cursor.getDefaultCursor());
                        firmware.setText(driver.getFirmwareConfiguration());
                    }
                })
                .onReload(f -> firmware.setText(driver.getFirmwareConfiguration()))
                .build();
        return form[0];
    }

    public static FormWizard asyncSettings(GcodeAsyncDriver driver) {
        return Form.of(driver).named("GCodeAsyncDriver.AdvancedSettings.title") //$NON-NLS-1$
                .section("DriverForms.Async", "activity") //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("confirmationFlowControl", "DriverForms.Confirmation", "DriverForms.Confirmation.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .toggle("reportedLocationConfirmation", "DriverForms.LocationConfirmation", "DriverForms.LocationConfirmation.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .section("GcodeAsyncDriverSettings.InterpolationPanel.Border.title", "sliders") //$NON-NLS-1$ //$NON-NLS-2$
                .integer("interpolationMaxSteps", "DriverForms.MaxSteps").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("DriverForms.MaxSteps.Hint") //$NON-NLS-1$
                .integer("interpolationJerkSteps", "DriverForms.JerkSteps").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("DriverForms.JerkSteps.Hint") //$NON-NLS-1$
                .decimal("interpolationTimeStep", "DriverForms.TimeStep").format("%f").unit("s").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                .integer("interpolationMinStep", "DriverForms.MinStep").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("DriverForms.MinStep.Hint") //$NON-NLS-1$
                .length("junctionDeviation", "DriverForms.JunctionDeviation").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("DriverForms.JunctionDeviation.Hint") //$NON-NLS-1$
                .build();
    }
}
