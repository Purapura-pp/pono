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

package org.openpnp.machine.reference.camera.wizards;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.components.CameraView;
import org.openpnp.gui.components.SimpleGraphView;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.form.WeakForward;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Ui;
import org.openpnp.machine.reference.camera.ReferenceCamera;
import org.openpnp.machine.reference.camera.calibration.AdvancedCalibration;
import org.openpnp.machine.reference.camera.calibration.CameraCalibrationUtils;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.CalibrationStep;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Camera;
import org.openpnp.util.SimpleGraph;
import org.openpnp.vision.pipeline.ui.MatView;
import org.pmw.tinylog.Logger;

/**
 * A camera's calibration tab: the advanced calibration's settings, what it found and how well
 * its model fits, the collection itself being a step of the calibration page; and the old lens
 * calibration, for as long as the advanced one does not take over.
 */
public final class CameraCalibrationForm {
    private CameraCalibrationForm() {
    }

    /** The advanced calibration's settings and findings, read as the form shows them. */
    public static class Bean extends AbstractModelObject {
        private final ReferenceCamera camera;
        private final AdvancedCalibration calibration;

        Bean(ReferenceCamera camera) {
            this.camera = camera;
            this.calibration = camera.getAdvancedCalibration();
            WeakForward.listen(calibration, this, (bean, e) -> bean.refresh());
        }

        void refresh() {
            for (String property : new String[] {"state", "offsets", "pixel", "accuracy", "fieldOfView", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                    "effectiveFieldOfView", "mountingError", "heights"}) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                firePropertyChange(property, null, ""); //$NON-NLS-1$
            }
        }

        boolean isValid() {
            return Boolean.TRUE.equals(calibration.isValid());
        }

        public boolean isOverriding() {
            return calibration.isOverridingOldTransformsAndDistortionCorrectionSettings();
        }

        public void setOverriding(boolean overriding) {
            calibration.setOverridingOldTransformsAndDistortionCorrectionSettings(overriding);
        }

        public boolean isEnabled() {
            return calibration.isEnabled();
        }

        public void setEnabled(boolean enabled) {
            calibration.setEnabled(enabled);
        }

        public int getAlphaPercent() {
            return calibration.getAlphaPercent();
        }

        public void setAlphaPercent(int alpha) {
            calibration.setAlphaPercent(alpha);
        }

        public int getRadialLines() {
            return calibration.getDesiredRadialLinesPerTestPattern();
        }

        public void setRadialLines(int lines) {
            calibration.setDesiredRadialLinesPerTestPattern(lines);
        }

        public Length getDefaultZ() {
            return camera.getDefaultZ();
        }

        public void setDefaultZ(Length z) {
            camera.setDefaultZ(z);
        }

        public boolean isLensEnabled() {
            return camera.getCalibration().isEnabled();
        }

        public void setLensEnabled(boolean enabled) {
            camera.getCalibration().setEnabled(enabled);
        }

        private LengthUnit units() {
            return camera.getMachine().getConfiguration().getSystemUnits();
        }

        private LengthUnit small() {
            return units() == LengthUnit.Inches ? LengthUnit.Mils : LengthUnit.Microns;
        }

        private static String length(Length length) {
            return String.format(Locale.US, "%.3f %s", length.getValue(), length.getUnits().getShortName()); //$NON-NLS-1$
        }

        public String getState() {
            if (isValid()) {
                return Translations.getString("CameraCalibrationForm.State.Valid"); //$NON-NLS-1$
            }
            return Translations.getString(Boolean.TRUE.equals(calibration.isDataAvailable())
                    ? "CameraCalibrationForm.State.Data" : "CameraCalibrationForm.State.None"); //$NON-NLS-1$ //$NON-NLS-2$
        }

        public String getOffsets() {
            if (!isValid()) {
                return null;
            }
            Location offsets = (camera.getHead() != null ? calibration.getCalibratedOffsets() : camera.getLocation())
                    .convertToUnits(units());
            return length(offsets.getLengthX()) + " \u00b7 " + length(offsets.getLengthY()) //$NON-NLS-1$
                    + " \u00b7 " + length(offsets.getLengthZ()); //$NON-NLS-1$
        }

        public String getPixel() {
            if (!isValid()) {
                return null;
            }
            return length(camera.getUnitsPerPixel(camera.getDefaultZ()).convertToUnits(small()).getLengthX());
        }

        public String getAccuracy() {
            if (!isValid()) {
                return null;
            }
            Length pixel = camera.getUnitsPerPixel(camera.getDefaultZ()).convertToUnits(small()).getLengthX();
            return length(pixel.multiply(calibration.getRmsError()));
        }

        public String getFieldOfView() {
            return isValid() ? String.format(Locale.US, "%.2f\u00b0 \u00d7 %.2f\u00b0", //$NON-NLS-1$
                    calibration.getWidthFov(), calibration.getHeightFov()) : null;
        }

        public String getEffectiveFieldOfView() {
            return isValid() ? String.format(Locale.US, "%.2f\u00b0 \u00d7 %.2f\u00b0", //$NON-NLS-1$
                    calibration.getVirtualWidthFov(), calibration.getVirtualHeightFov()) : null;
        }

        public String getMountingError() {
            return isValid() ? String.format(Locale.US, "X %.3f\u00b0 \u00b7 Y %.3f\u00b0 \u00b7 Z %.3f\u00b0", //$NON-NLS-1$
                    calibration.getRotationErrorX(), calibration.getRotationErrorY(), calibration.getRotationErrorZ())
                    : null;
        }

        /** The heights the last collection was at. */
        public String getHeights() {
            List<Length> heights = heights(calibration);
            if (heights.isEmpty()) {
                return null;
            }
            StringBuilder text = new StringBuilder();
            for (Length height : heights) {
                if (text.length() > 0) {
                    text.append(" \u00b7 "); //$NON-NLS-1$
                }
                text.append(length(height.convertToUnits(units())));
            }
            return text.toString();
        }
    }

    static List<Length> heights(AdvancedCalibration calibration) {
        List<Length> heights = new ArrayList<>();
        double[][][] points = calibration.getSavedTestPattern3dPointsList();
        if (points != null) {
            for (double[][] pattern : points) {
                heights.add(new Length(pattern[0][2], LengthUnit.Millimeters));
            }
        }
        return heights;
    }

    public static FormWizard calibration(ReferenceCamera camera) {
        Bean bean = new Bean(camera);
        boolean onHead = camera.getHead() != null;
        CalibrationStep step = camera.getLooking() == Camera.Looking.Up
                ? CalibrationStep.AdvancedUpCamera : CalibrationStep.AdvancedDownCamera;
        Residuals residuals = new Residuals(camera);
        LensCalibration lens = new LensCalibration(camera);
        int[] alpha = {camera.getAdvancedCalibration().getAlphaPercent()};
        return Form.of(bean).named("CameraCalibrationForm.Title") //$NON-NLS-1$
                .section("CameraCalibrationForm.Advanced", "target") //$NON-NLS-1$ //$NON-NLS-2$
                .measuredBy(step, camera)
                .readOnly("state", "CameraCalibrationForm.State") //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("overriding", "CameraCalibrationForm.Overriding", "CameraCalibrationForm.Overriding.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .toggle("enabled", "CameraCalibrationForm.Enabled", "CameraCalibrationForm.Enabled.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleIf(f -> bean.isValid() && Boolean.TRUE.equals(f.value("overriding"))) //$NON-NLS-1$
                .integer("alphaPercent", "CameraCalibrationForm.Alpha").unit("%").width(120) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .validate(v -> v instanceof Integer && (Integer) v >= 0 && (Integer) v <= 100, "CameraCalibrationForm.Alpha.Range") //$NON-NLS-1$
                .hint("CameraCalibrationForm.Alpha.Hint") //$NON-NLS-1$
                .integer("radialLines", "CameraCalibrationForm.RadialLines").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .hint("CameraCalibrationForm.RadialLines.Hint") //$NON-NLS-1$
                .length("defaultZ", "CameraCalibrationForm.DefaultZ").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleIf(f -> onHead)
                .hint("CameraCalibrationForm.DefaultZ.Hint") //$NON-NLS-1$
                .section("CameraCalibrationForm.Results", "check") //$NON-NLS-1$ //$NON-NLS-2$
                .readOnly("offsets", onHead ? "CameraCalibrationForm.Offsets" : "CameraCalibrationForm.Location") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleIf(f -> bean.isValid())
                .readOnly("pixel", "CameraCalibrationForm.Pixel") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleIf(f -> bean.isValid())
                .readOnly("accuracy", "CameraCalibrationForm.Accuracy") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleIf(f -> bean.isValid())
                .hint("CameraCalibrationForm.Accuracy.Hint") //$NON-NLS-1$
                .readOnly("fieldOfView", "CameraCalibrationForm.FieldOfView") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleIf(f -> bean.isValid())
                .readOnly("effectiveFieldOfView", "CameraCalibrationForm.EffectiveFieldOfView") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleIf(f -> bean.isValid())
                .readOnly("mountingError", "CameraCalibrationForm.MountingError") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleIf(f -> bean.isValid())
                .hint("CameraCalibrationForm.MountingError.Hint") //$NON-NLS-1$
                .readOnly("heights", "CameraCalibrationForm.Heights") //$NON-NLS-1$ //$NON-NLS-2$
                .visibleIf(f -> bean.isValid())
                .section("CameraCalibrationForm.Diagnostics", "activity").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", residuals) //$NON-NLS-1$
                .visibleIf(f -> bean.isValid())
                .section("CameraCalibrationForm.Lens", "camera").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", Forms.paragraph(Translations.getString("CameraForm.Overridden"))) //$NON-NLS-1$ //$NON-NLS-2$
                .visibleIf(f -> CameraForm.overridden(camera))
                .toggle("lensEnabled", "CameraCalibrationForm.Lens.Enabled", "CameraCalibrationForm.Lens.Enabled.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleIf(f -> !CameraForm.overridden(camera))
                .custom("", lens) //$NON-NLS-1$
                .visibleIf(f -> !CameraForm.overridden(camera))
                .onReload(f -> {
                    bean.refresh();
                    residuals.update();
                })
                .onApply(f -> {
                    // The camera's location follows the calibration's offsets as they are used.
                    camera.setHeadOffsets(camera.getHeadOffsets());
                    if (camera.getAdvancedCalibration().getAlphaPercent() != alpha[0]) {
                        alpha[0] = camera.getAdvancedCalibration().getAlphaPercent();
                        camera.clearCalibrationCache();
                    }
                    bean.refresh();
                    residuals.update();
                })
                .build();
    }

    /**
     * How well the model fits, at one of the heights: each point's error in the order collected,
     * the errors as a scatter with the circle beyond which a point was left out, and a map of the
     * errors over the picture.
     */
    static final class Residuals extends JPanel {
        private final ReferenceCamera camera;
        private final JComboBox<String> height = new JComboBox<>();
        private final JCheckBox outliers = new JCheckBox(
                Translations.getString("CameraCalibrationForm.Outliers")); //$NON-NLS-1$
        private final SimpleGraphView sequence = new SimpleGraphView();
        private final SimpleGraphView scatter = new SimpleGraphView();
        private final MatView map = new MatView();
        private boolean updating;

        Residuals(ReferenceCamera camera) {
            super(new BorderLayout(0, 8));
            this.camera = camera;
            setOpaque(false);
            outliers.setOpaque(false);
            height.setToolTipText(Translations.getString("CameraCalibrationForm.Height.Tip")); //$NON-NLS-1$
            add(Forms.row(Ui.t2(Translations.getString("CameraCalibrationForm.Height")), height, outliers), //$NON-NLS-1$
                    BorderLayout.NORTH);
            JPanel plots = new JPanel(new GridLayout(0, 1, 0, 8));
            plots.setOpaque(false);
            sequence.setPreferredSize(new Dimension(360, 140));
            scatter.setPreferredSize(new Dimension(360, 220));
            map.setPreferredSize(new Dimension(360, 220));
            plots.add(caption(sequence, "CameraCalibrationForm.Sequence")); //$NON-NLS-1$
            plots.add(caption(scatter, "CameraCalibrationForm.Scatter")); //$NON-NLS-1$
            plots.add(caption(map, "CameraCalibrationForm.Map")); //$NON-NLS-1$
            add(plots, BorderLayout.CENTER);
            height.addActionListener(e -> {
                if (!updating) {
                    draw();
                }
            });
            outliers.addActionListener(e -> draw());
        }

        private static JPanel caption(javax.swing.JComponent view, String key) {
            JPanel panel = new JPanel(new BorderLayout(0, 4));
            panel.setOpaque(false);
            panel.add(Ui.t2(Translations.getString(key)), BorderLayout.NORTH);
            panel.add(view, BorderLayout.CENTER);
            return panel;
        }

        void update() {
            updating = true;
            int selected = Math.max(0, height.getSelectedIndex());
            height.removeAllItems();
            LengthUnit units = camera.getMachine().getConfiguration().getSystemUnits();
            for (Length z : heights(camera.getAdvancedCalibration())) {
                height.addItem(String.format(Locale.US, "Z %.3f %s", z.convertToUnits(units).getValue(), //$NON-NLS-1$
                        units.getShortName()));
            }
            if (height.getItemCount() > 0) {
                height.setSelectedIndex(Math.min(selected, height.getItemCount() - 1));
            }
            updating = false;
            draw();
        }

        private void draw() {
            AdvancedCalibration calibration = camera.getAdvancedCalibration();
            int index = height.getSelectedIndex();
            if (!Boolean.TRUE.equals(calibration.isValid()) || index < 0) {
                sequence.setGraph(null);
                scatter.setGraph(null);
                return;
            }
            try {
                LengthUnit small = camera.getMachine().getConfiguration().getSystemUnits() == LengthUnit.Inches
                        ? LengthUnit.Mils : LengthUnit.Microns;
                double pixel = camera.getUnitsPerPixel(heights(calibration).get(index))
                        .convertToUnits(small).getLengthX().getValue();
                List<double[]> errors = outliers.isSelected()
                        ? CameraCalibrationUtils.computeResidualErrors(calibration.getSavedTestPatternImagePointsList(),
                                calibration.getModeledImagePointsList(), index)
                        : CameraCalibrationUtils.computeResidualErrors(calibration.getSavedTestPatternImagePointsList(),
                                calibration.getModeledImagePointsList(), index, calibration.getOutlierPointList());
                String label = Translations.getString("CameraCalibrationForm.Residual") + " [" + small.getShortName() + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

                SimpleGraph inOrder = new SimpleGraph();
                inOrder.setRelativePaddingLeft(0.10);
                SimpleGraph.DataScale scale = new SimpleGraph.DataScale(label);
                scale.setRelativePaddingTop(0.05);
                scale.setRelativePaddingBottom(0.05);
                scale.setSymmetricIfSigned(true);
                scale.setColor(Color.GRAY);
                SimpleGraph.DataRow x = new SimpleGraph.DataRow("X", Color.RED); //$NON-NLS-1$
                SimpleGraph.DataRow y = new SimpleGraph.DataRow("Y", Color.GREEN); //$NON-NLS-1$
                int i = 0;
                for (double[] error : errors) {
                    x.recordDataPoint(i, pixel * error[0]);
                    y.recordDataPoint(i, pixel * error[1]);
                    i++;
                }
                scale.addDataRow(x);
                scale.addDataRow(y);
                inOrder.addDataScale(scale);
                sequence.setGraph(inOrder);

                SimpleGraph spread = new SimpleGraph();
                spread.setRelativePaddingLeft(0.10);
                SimpleGraph.DataScale square = new SimpleGraph.DataScale(label);
                square.setRelativePaddingTop(0.05);
                square.setRelativePaddingBottom(0.05);
                square.setSymmetricIfSigned(true);
                square.setSquareAspectRatio(true);
                square.setColor(Color.GRAY);
                SimpleGraph.DataRow points = new SimpleGraph.DataRow("XY", Color.RED); //$NON-NLS-1$
                points.setLineShown(false);
                points.setMarkerShown(true);
                for (double[] error : errors) {
                    points.recordDataPoint(pixel * error[0], pixel * error[1]);
                }
                square.addDataRow(points);
                // The circle in two halves, a row's points running one way.
                SimpleGraph.DataRow top = new SimpleGraph.DataRow("CircleT", Color.GREEN); //$NON-NLS-1$
                SimpleGraph.DataRow bottom = new SimpleGraph.DataRow("CircleB", Color.GREEN); //$NON-NLS-1$
                double radius = pixel * calibration.getRmsError() * CameraCalibrationUtils.sigmaThresholdForRejectingOutliers;
                for (int k = 0; k <= 45; k++) {
                    top.recordDataPoint(radius * Math.cos(k * 2 * Math.PI / 90), radius * Math.sin(k * 2 * Math.PI / 90));
                    bottom.recordDataPoint(radius * Math.cos((k + 45) * 2 * Math.PI / 90),
                            radius * Math.sin((k + 45) * 2 * Math.PI / 90));
                }
                square.addDataRow(top);
                square.addDataRow(bottom);
                spread.addDataScale(square);
                scatter.setGraph(spread);

                int width = camera.getCropWidth() > 0
                        ? Math.min(camera.getCropWidth(), calibration.getRawCroppedImageWidth())
                        : calibration.getRawCroppedImageWidth();
                int height = camera.getCropHeight() > 0
                        ? Math.min(camera.getCropHeight(), calibration.getRawCroppedImageHeight())
                        : calibration.getRawCroppedImageHeight();
                Mat image = CameraCalibrationUtils.generateErrorImage(new Size(width, height), index,
                        calibration.getSavedTestPatternImagePointsList(), calibration.getModeledImagePointsList(),
                        outliers.isSelected() ? null : calibration.getOutlierPointList());
                map.setMat(image);
                image.release();
            }
            catch (Exception e) {
                Logger.warn(e, "Failed to draw the calibration residuals of camera {}.", camera.getName()); //$NON-NLS-1$
            }
        }
    }

    /**
     * The old lens calibration: a card held in front of the camera, a frame taken at each flash.
     * The button starts and stops it.
     */
    static final class LensCalibration extends JPanel {
        private final ReferenceCamera camera;
        private final JButton button = new JButton();
        private boolean running;

        LensCalibration(ReferenceCamera camera) {
            super(new BorderLayout());
            this.camera = camera;
            setOpaque(false);
            add(Forms.row(button), BorderLayout.WEST);
            button.addActionListener(e -> {
                if (running) {
                    cancel();
                }
                else {
                    start();
                }
            });
            display(false);
        }

        private void display(boolean running) {
            this.running = running;
            button.setText(Translations.getString(running
                    ? "ReferenceCameraCalibrationConfigurationWizard.Action.CancelCalibration" //$NON-NLS-1$
                    : "ReferenceCameraCalibrationConfigurationWizard.Action.StartCalibration")); //$NON-NLS-1$
        }

        private void start() {
            MainFrame.get().getCameraViews().setSelectedCamera(camera);
            CameraView view = MainFrame.get().getCameraViews().getCameraView(camera);
            display(true);
            view.setText(Translations.getString("CameraCalibrationForm.Lens.Instructions")); //$NON-NLS-1$
            view.flash();
            camera.startCalibration((current, max, finished) -> {
                if (finished) {
                    view.setText(null);
                    display(false);
                }
                else {
                    view.setText(String.format(Translations.getString("CameraCalibrationForm.Lens.Progress"), //$NON-NLS-1$
                            current, max));
                }
                view.flash();
            });
        }

        private void cancel() {
            display(false);
            camera.cancelCalibration();
            CameraView view = MainFrame.get().getCameraViews().getCameraView(camera);
            view.setText(null);
            view.flash();
        }
    }
}
