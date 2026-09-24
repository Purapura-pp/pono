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

package org.openpnp.machine.rapidplacer;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.machine.reference.feeder.wizards.FeederForm;
import org.openpnp.model.Location;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Feeder;
import org.openpnp.spi.base.AbstractMachine;
import org.openpnp.util.MovableUtils;
import org.openpnp.util.UiUtils;
import org.openpnp.util.Utils2D;
import org.openpnp.util.VisionUtils;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;

/**
 * A Rapid feeder: its address and pitch, and the scan that finds the feeders by their QR codes,
 * making a feeder for each code it has not seen.
 */
public final class RapidFeederForm {
    private RapidFeederForm() {
    }

    public static FormWizard build(RapidFeeder feeder) {
        FormWizard[] form = new FormWizard[1];
        form[0] = FeederForm.common(Form.of(feeder).named(feeder.getName()), feeder, true)
                .section("RapidFeederConfigurationWizard.panelRapidFeederConfig.Border.title", "feeder") //$NON-NLS-1$ //$NON-NLS-2$
                .text("address", "RapidFeederConfigurationWizard.Address.text") //$NON-NLS-1$ //$NON-NLS-2$
                .integer("pitch", "RapidFeederConfigurationWizard.Pitch.text").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .section("RapidFeederConfigurationWizard.panelRapidFeederScan.Border.title", "search") //$NON-NLS-1$ //$NON-NLS-2$
                .location("scanStartLocation", "RapidFeederConfigurationWizard.ScanStartLocation.text", false).capture() //$NON-NLS-1$ //$NON-NLS-2$
                .location("scanEndLocation", "RapidFeederConfigurationWizard.ScanEndLocation.text", false).capture() //$NON-NLS-1$ //$NON-NLS-2$
                .length("scanIncrement", "RapidFeederConfigurationWizard.ScanIncrement.text").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .action("RapidFeederForm.Scan", "search", () -> scan(form[0], feeder)).movesMachine() //$NON-NLS-1$ //$NON-NLS-2$
                .hint("RapidFeederForm.Scan.Hint") //$NON-NLS-1$
                .build();
        return form[0];
    }

    /** A QR code found on the machine: its text and roughly where its middle is. */
    static final class QrCode {
        final String text;
        final Location location;

        QrCode(Camera camera, Result result) {
            text = result.getText();
            // The result points' average, near enough to the code's middle.
            double x = 0;
            double y = 0;
            for (ResultPoint point : result.getResultPoints()) {
                x += point.getX();
                y += point.getY();
            }
            x /= result.getResultPoints().length;
            y /= result.getResultPoints().length;
            location = VisionUtils.getPixelLocation(camera, x, y);
        }

        @Override
        public int hashCode() {
            return text.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof QrCode && text.equals(((QrCode) other).text);
        }
    }

    private static Set<QrCode> qrCodes(Camera camera) throws Exception {
        BufferedImage image = camera.lightSettleAndCapture();
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        Set<QrCode> codes = new HashSet<>();
        for (Result result : new QRCodeMultiReader().decodeMultiple(bitmap)) {
            codes.add(new QrCode(camera, result));
        }
        return codes;
    }

    /**
     * The camera along the scan line at the scan increment, the codes it sees collected; each code
     * a feeder, found by name or made, put where its code is.
     */
    private static void scan(FormWizard form, RapidFeeder current) {
        form.apply();
        UiUtils.submitUiMachineTask(() -> {
            AbstractMachine machine = current.getMachine();
            Camera camera = machine.getDefaultHead().getDefaultCamera();
            Set<QrCode> codes = new HashSet<>();
            // Marginally up, so that the end point is scanned too.
            int last = (int) (current.getScanStartLocation().getLinearLengthTo(current.getScanEndLocation())
                    .divide(current.getScanIncrement()) + 1e-5);
            for (int step = 0; step <= last; step++) {
                Location at = Utils2D.getPointAlongLine(current.getScanStartLocation(), current.getScanEndLocation(),
                        current.getScanIncrement().multiply(step));
                MovableUtils.moveToLocationAtSafeZ(camera, at);
                try {
                    codes.addAll(qrCodes(camera));
                }
                catch (Exception e) {
                    // A frame between feeders shows no code: gaps are expected.
                }
            }
            for (QrCode code : codes) {
                RapidFeeder feeder = null;
                for (Feeder other : machine.getFeeders()) {
                    if (other instanceof RapidFeeder && other.getName().equals(code.text)) {
                        feeder = (RapidFeeder) other;
                        break;
                    }
                }
                if (feeder == null) {
                    feeder = new RapidFeeder();
                    feeder.setName(code.text);
                    machine.addFeeder(feeder);
                }
                feeder.setLocation(code.location);
                feeder.setAddress(code.text);
                if (feeder.getPart() == null && !machine.getConfiguration().getParts().isEmpty()) {
                    feeder.setPart(machine.getConfiguration().getParts().get(0));
                }
            }
        });
    }
}
