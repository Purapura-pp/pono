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

package org.openpnp.machine.reference.feeder.wizards;

import java.util.Locale;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.form.WeakForward;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.machine.reference.feeder.ReferenceRotatedTrayFeeder;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Part;
import org.openpnp.util.Utils2D;
import org.pmw.tinylog.Logger;

/**
 * A rotated tray: three corners and the rows and columns between them, from which the step
 * along a row and along a column and the tray's angle are worked out.
 */
public final class RotatedTrayForm {
    private RotatedTrayForm() {
    }

    private static final double RIGHT_ANGLE_TOLERANCE = 2.5;
    private static final double TOLERANCE_MM = 0.03;

    /** The tray's first corner, angle and pick height are the one location they share. */
    public static class Bean extends AbstractModelObject {
        private final ReferenceRotatedTrayFeeder feeder;

        Bean(ReferenceRotatedTrayFeeder feeder) {
            this.feeder = feeder;
            WeakForward.listen(feeder, this, (bean, e) -> {
                if ("feedCount".equals(e.getPropertyName()) || "remainingCount".equals(e.getPropertyName())) { //$NON-NLS-1$ //$NON-NLS-2$
                    bean.firePropertyChange("remaining", null, bean.getRemaining()); //$NON-NLS-1$
                }
            });
        }

        public Part getPart() { return feeder.getPart(); }
        public void setPart(Part part) { feeder.setPart(part); }
        public int getFeedRetryCount() { return feeder.getFeedRetryCount(); }
        public void setFeedRetryCount(int count) { feeder.setFeedRetryCount(count); }
        public int getPickRetryCount() { return feeder.getPickRetryCount(); }
        public void setPickRetryCount(int count) { feeder.setPickRetryCount(count); }

        public Location getCornerA() {
            return feeder.getLocation();
        }

        public void setCornerA(Location corner) {
            Location held = feeder.getLocation();
            Location xy = corner.convertToUnits(held.getUnits());
            feeder.setLocation(held.derive(xy.getX(), xy.getY(), null, null));
        }

        public Location getCornerB() { return feeder.getFirstRowLastComponentLocation(); }
        public void setCornerB(Location corner) { feeder.setFirstRowLastComponentLocation(corner); }
        public Location getCornerC() { return feeder.getLastComponentLocation(); }
        public void setCornerC(Location corner) { feeder.setLastComponentLocation(corner); }

        public Length getPickZ() {
            return feeder.getLocation().getLengthZ();
        }

        public void setPickZ(Length z) {
            feeder.setLocation(feeder.getLocation().deriveLengths(null, null, z, null));
        }

        public double getTrayRotation() {
            return feeder.getLocation().getRotation();
        }

        public void setTrayRotation(double rotation) {
            feeder.setLocation(feeder.getLocation().derive(null, null, null, rotation));
        }

        public int getTrayCountCols() { return feeder.getTrayCountCols(); }
        public void setTrayCountCols(int count) { feeder.setTrayCountCols(count); }
        public int getTrayCountRows() { return feeder.getTrayCountRows(); }
        public void setTrayCountRows(int count) { feeder.setTrayCountRows(count); }
        public int getFeedCount() { return feeder.getFeedCount(); }
        public void setFeedCount(int count) { feeder.setFeedCount(count); }
        public double getComponentRotationInTray() { return feeder.getComponentRotationInTray(); }
        public void setComponentRotationInTray(double rotation) { feeder.setComponentRotationInTray(rotation); }
        public Location getOffsets() { return feeder.getOffsets(); }
        public void setOffsets(Location offsets) { feeder.setOffsets(offsets); }

        public String getRemaining() {
            return String.valueOf(feeder.getRemainingCount());
        }
    }

    public static FormWizard build(ReferenceRotatedTrayFeeder feeder) {
        JLabel illustration = new JLabel();
        try {
            illustration.setIcon(new ImageIcon(ImageIO.read(
                    RotatedTrayForm.class.getResourceAsStream("/illustrations/rotatedtrayfeeder.png")))); //$NON-NLS-1$
        }
        catch (Exception e) {
            Logger.warn(e, "Failed to load the rotated tray feeder illustration."); //$NON-NLS-1$
        }
        FormWizard[] form = new FormWizard[1];
        form[0] = FeederForm.common(Form.of(new Bean(feeder)).named(feeder.getName()), feeder, false)
                .section("ReferenceRotatedTrayFeederConfigurationWizard.TrayComponentLocations", "grid") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", illustration) //$NON-NLS-1$
                .location("cornerA", "RotatedTrayForm.CornerA", false).capture() //$NON-NLS-1$ //$NON-NLS-2$
                .location("cornerB", "RotatedTrayForm.CornerB", false).capture() //$NON-NLS-1$ //$NON-NLS-2$
                .location("cornerC", "RotatedTrayForm.CornerC", false).capture() //$NON-NLS-1$ //$NON-NLS-2$
                .hint("RotatedTrayForm.Corners.Hint") //$NON-NLS-1$
                .length("pickZ", "ReferenceRotatedTrayFeederConfigurationWizard.ZHeight").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .angle("componentRotationInTray", "RotatedTrayForm.ComponentRotation").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("ReferenceRotatedTrayFeederConfigurationWizard.ComponentRotation.ToolTip") //$NON-NLS-1$
                .section("ReferenceRotatedTrayFeederConfigurationWizard.TrayParameters", "list") //$NON-NLS-1$ //$NON-NLS-2$
                .integer("trayCountCols", "RotatedTrayForm.Columns").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("trayCountRows", "RotatedTrayForm.Rows").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("feedCount", "ReferenceRotatedTrayFeederConfigurationWizard.FeedCount").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .button("ReferenceRotatedTrayFeederConfigurationWizard.Reset", "undo", f -> f.setValue("feedCount", "0")) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                .readOnly("remaining", "RotatedTrayForm.Remaining") //$NON-NLS-1$ //$NON-NLS-2$
                .section("RotatedTrayForm.Steps", "ruler") //$NON-NLS-1$ //$NON-NLS-2$
                .action("ReferenceRotatedTrayFeederConfigurationWizard.CalculateOffsetsAndTrayRotation", "zap", //$NON-NLS-1$ //$NON-NLS-2$
                        () -> calculate(form[0]))
                .location("offsets", "RotatedTrayForm.Offsets", false) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("RotatedTrayForm.Offsets.Hint") //$NON-NLS-1$
                .angle("trayRotation", "RotatedTrayForm.TrayRotation").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("ReferenceRotatedTrayFeederConfigurationWizard.TrayRotation.ToolTip") //$NON-NLS-1$
                .onApply(f -> check(f))
                .build();
        return form[0];
    }

    private static int integer(FormWizard form, String property) {
        try {
            return Integer.parseInt(String.valueOf(form.value(property)).trim());
        }
        catch (NumberFormatException e) {
            return 0;
        }
    }

    private static double decimal(FormWizard form, String property) {
        try {
            return Double.parseDouble(String.valueOf(form.value(property)).trim());
        }
        catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * The step along a row and along a column and the tray's angle, from the corners and the
     * counts as they are on screen: {x, y, angle} in millimetres and degrees.
     */
    static double[] steps(Location a, Location b, Location c, int columns, int rows, double angle) throws Exception {
        if (columns < 1 || rows < 1) {
            throw new Exception(Translations.getString("ReferenceRotatedTrayFeederConfigurationWizard.ErrorMessage.AtLeastOneRowAndOneColumn")); //$NON-NLS-1$
        }
        Length ab = a.getLinearLengthTo(b);
        if (ab.getValue() > 0 && columns == 1) {
            throw new Exception(Translations.getString("ReferenceRotatedTrayFeederConfigurationWizard.ErrorMessage.SingleColumnInconsistency")); //$NON-NLS-1$
        }
        if (ab.getValue() == 0 && columns > 1) {
            throw new Exception(String.format(Translations.getString("ReferenceRotatedTrayFeederConfigurationWizard.ErrorMessage.MultipleColumnInconsistency"), columns)); //$NON-NLS-1$
        }
        Length bc = b.getLinearLengthTo(c);
        if (bc.getValue() > 0 && rows == 1) {
            throw new Exception(Translations.getString("ReferenceRotatedTrayFeederConfigurationWizard.ErrorMessage.SingleRowInconsistency")); //$NON-NLS-1$
        }
        if (bc.getValue() == 0 && rows > 1) {
            throw new Exception(String.format(Translations.getString("ReferenceRotatedTrayFeederConfigurationWizard.ErrorMessage.MultipleRowInconsistency"), rows)); //$NON-NLS-1$
        }
        double columnStep = columns > 1 ? ab.convertToUnits(LengthUnit.Millimeters).getValue() / (columns - 1) : 0;
        double rowStep = rows > 1 ? bc.convertToUnits(LengthUnit.Millimeters).getValue() / (rows - 1) : 0;
        double rowAngle = Utils2D.getAngleFromPoint(a, b);
        double columnAngle = Utils2D.getAngleFromPoint(b, c);
        if (rows > 1 && columns > 1) {
            double corner = Utils2D.normalizeAngle180(rowAngle - columnAngle);
            if (Math.abs(corner) < 90 - RIGHT_ANGLE_TOLERANCE || Math.abs(corner) > 90 + RIGHT_ANGLE_TOLERANCE) {
                throw new Exception(String.format(Translations.getString("ReferenceRotatedTrayFeederConfigurationWizard.ErrorMessage.TrayAngleNot90"), Math.abs(corner))); //$NON-NLS-1$
            }
            // Corners taken the other way round the tray than the illustration's: rows step back.
            if (corner < 0) {
                rowStep = -rowStep;
            }
        }
        double rotation = angle;
        if (columns > 1) {
            rotation = rowAngle;
        }
        else if (rows > 1) {
            rotation = columnAngle + 90;
        }
        return new double[] {columnStep, rowStep, rotation};
    }

    private static double[] steps(FormWizard form) throws Exception {
        return steps(form.location("cornerA"), form.location("cornerB"), form.location("cornerC"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                integer(form, "trayCountCols"), integer(form, "trayCountRows"), decimal(form, "trayRotation")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    private static void calculate(FormWizard form) {
        try {
            double[] steps = steps(form);
            form.setLocation("offsets", new Location(LengthUnit.Millimeters, steps[0], steps[1], 0, 0)); //$NON-NLS-1$
            form.setValue("trayRotation", String.format(Locale.US, "%.3f", steps[2])); //$NON-NLS-1$ //$NON-NLS-2$
        }
        catch (Exception e) {
            MessageBoxes.errorBox(MainFrame.get(),
                    Translations.getString("ReferenceRotatedTrayFeederConfigurationWizard.Error"), e.getMessage()); //$NON-NLS-1$
        }
    }

    /** The steps as written against those the corners give: saved either way, but said. */
    private static void check(FormWizard form) {
        try {
            double[] steps = steps(form);
            Location offsets = form.location("offsets").convertToUnits(LengthUnit.Millimeters); //$NON-NLS-1$
            if (Math.abs(steps[0] - offsets.getX()) > TOLERANCE_MM || Math.abs(steps[1] - offsets.getY()) > TOLERANCE_MM
                    || Math.abs(steps[2] - decimal(form, "trayRotation")) > TOLERANCE_MM) { //$NON-NLS-1$
                throw new Exception(Translations.getString("ReferenceRotatedTrayFeederConfigurationWizard.ErrorMessage.OffsetsAndRotationInconsistency")); //$NON-NLS-1$
            }
        }
        catch (Exception e) {
            if (MainFrame.get() != null) {
                MessageBoxes.errorBox(MainFrame.get(),
                        Translations.getString("ReferenceRotatedTrayFeederConfigurationWizard.Error"), e.getMessage()); //$NON-NLS-1$
            }
        }
    }
}
