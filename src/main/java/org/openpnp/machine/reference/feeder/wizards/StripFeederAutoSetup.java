/*
 * Copyright (C) 2017 Jason von Nieda <jason@vonnieda.org>
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

import java.awt.Color;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;
import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.components.CameraView;
import org.openpnp.gui.components.CameraViewActionEvent;
import org.openpnp.gui.components.CameraViewActionListener;
import org.openpnp.gui.components.CameraViewFilter;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.machine.reference.camera.BufferedImageCamera;
import org.openpnp.machine.reference.feeder.ReferenceStripFeeder;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Camera;
import org.openpnp.util.HslColor;
import org.openpnp.util.MovableUtils;
import org.openpnp.util.OpenCvUtils;
import org.openpnp.util.UiUtils;
import org.openpnp.util.VisionUtils;
import org.openpnp.vision.FluentCv;
import org.openpnp.vision.Ransac;
import org.openpnp.vision.pipeline.CvPipeline;
import org.openpnp.vision.pipeline.CvStage;
import org.pmw.tinylog.Logger;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;

/**
 * A strip feeder's automatic setup: the user clicks the first and the second part in the tape on
 * the camera image, and the reference holes and the part pitch are worked out from the sprocket
 * holes found beside them. Taken out of the strip feeder's old form so that the declarative one
 * has it; it writes what it finds straight into the feeder, where the old one left the part pitch
 * in a text field to be applied, and it speaks the display language on the camera image, where the
 * old one's five lines were English.
 */
public class StripFeederAutoSetup {
    private final ReferenceStripFeeder feeder;
    private final Component parent;
    private final Consumer<Boolean> running;
    private final Runnable done;
    private Camera camera;
    private Location firstPartLocation;
    private List<Location> part1HoleLocations;
    private boolean logDebugInfo;
    private volatile boolean active;

    /**
     * @param parent  Where errors are shown.
     * @param running Told true when the setup starts and false when it ends, either way.
     * @param done    Called on the event thread once the feeder has what the setup found.
     */
    public StripFeederAutoSetup(ReferenceStripFeeder feeder, Component parent, Consumer<Boolean> running,
            Runnable done) {
        this.feeder = feeder;
        this.parent = parent;
        this.running = running;
        this.done = done;
    }

    public boolean isActive() {
        return active;
    }

    /** Starts, or cancels one under way. */
    public void toggle(boolean debug) {
        if (active) {
            cancel();
        }
        else {
            start(debug);
        }
    }

    public void start(boolean debug) {
        try {
            camera = feeder.getMachine().getDefaultHead().getDefaultCamera();
            if (camera.isUnitsPerPixelAtZCalibrated()) {
                // Units per pixel by height: the reference hole's Z decides the scale.
                Length z = feeder.getReferenceHoleLocation().getLengthZ();
                if (!z.isInitialized()) {
                    throw new Exception(Translations.getString("StripFeederAutoSetup.SetZFirst")); //$NON-NLS-1$
                }
                camera.moveTo(camera.getLocation().deriveLengths(null, null, z, null));
            }
        }
        catch (Exception ex) {
            MessageBoxes.errorBox(parent, Translations.getString(
                    "ReferenceStripFeederConfigurationWizard.AutoSetup.ErrorBox.Title"), ex); //$NON-NLS-1$
            return;
        }
        active = true;
        running.accept(true);
        CameraView cameraView = cameraView();
        cameraView.addActionListener(part1Clicked);
        cameraView.setText(Translations.getString("StripFeederAutoSetup.ClickFirst")); //$NON-NLS-1$
        MovableUtils.fireTargetedUserAction(camera);
        cameraView.flash();
        logDebugInfo = debug;
        cameraView.setCameraViewFilter(new CameraViewFilter() {
            private boolean hasShownError = false;

            @Override
            public BufferedImage filterCameraImage(Camera camera, BufferedImage image) {
                try {
                    BufferedImage bufferedImage = showHoles(camera, image);
                    hasShownError = false;
                    return bufferedImage;
                }
                catch (Exception e) {
                    if (!hasShownError) {
                        hasShownError = true;
                        UiUtils.showError(e);
                    }
                    else {
                        Logger.debug("{}: {}", "Error", e); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                }
                return null;
            }
        });
    }

    public void cancel() {
        CameraView cameraView = cameraView();
        if (cameraView != null) {
            cameraView.setText(null);
            cameraView.setCameraViewFilter(null);
            cameraView.removeActionListener(part1Clicked);
            cameraView.removeActionListener(part2Clicked);
        }
        active = false;
        running.accept(false);
    }

    private CameraView cameraView() {
        MainFrame frame = MainFrame.get();
        return frame == null || camera == null ? null : frame.getCameraViews().getCameraView(camera);
    }

    private void failed(Throwable t) {
        SwingUtilities.invokeLater(() -> {
            cancel();
            MessageBoxes.errorBox(parent, Translations.getString(
                    "ReferenceStripFeederConfigurationWizard.AutoSetup.ErrorBox.Title"), t); //$NON-NLS-1$
        });
    }

    private final CameraViewActionListener part1Clicked = new CameraViewActionListener() {
        @Override
        public void actionPerformed(final CameraViewActionEvent action) {
            firstPartLocation = action.getLocation();
            final CameraView cameraView = cameraView();
            cameraView.removeActionListener(this);
            feeder.getMachine().submit(() -> {
                cameraView.setText(Translations.getString("StripFeederAutoSetup.CheckingFirst")); //$NON-NLS-1$
                camera.moveTo(firstPartLocation);
                MovableUtils.fireTargetedUserAction(camera);
                part1HoleLocations = findHoles(camera);
                if (part1HoleLocations.size() < 1) {
                    throw new Exception(Translations.getString("StripFeederAutoSetup.NoHole")); //$NON-NLS-1$
                }
                cameraView.setText(Translations.getString("StripFeederAutoSetup.ClickSecond")); //$NON-NLS-1$
                cameraView.flash();
                cameraView.addActionListener(part2Clicked);
                return null;
            }, new FutureCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                }

                @Override
                public void onFailure(final Throwable t) {
                    failed(t);
                }
            });
        }
    };

    private final CameraViewActionListener part2Clicked = new CameraViewActionListener() {
        @Override
        public void actionPerformed(final CameraViewActionEvent action) {
            final Location secondPartLocation = action.getLocation();
            final CameraView cameraView = cameraView();
            cameraView.removeActionListener(this);
            feeder.getMachine().submit(() -> {
                cameraView.setText(Translations.getString("StripFeederAutoSetup.CheckingSecond")); //$NON-NLS-1$
                camera.moveTo(secondPartLocation);
                MovableUtils.fireTargetedUserAction(camera);
                List<Location> part2HoleLocations = findHoles(camera);
                if (part2HoleLocations.size() < 1) {
                    throw new Exception(Translations.getString("StripFeederAutoSetup.NoHole")); //$NON-NLS-1$
                }
                List<Location> referenceHoles = deriveReferenceHoles(firstPartLocation, secondPartLocation,
                        part1HoleLocations, part2HoleLocations);
                Location referenceHole1 = referenceHoles.get(0).derive(null, null, null, 0d)
                        .derive(feeder.getReferenceHoleLocation(), false, false, true, false);
                Location referenceHole2 = referenceHoles.get(1).derive(null, null, null, 0d)
                        .derive(feeder.getLastHoleLocation(), false, false, true, false);

                Length partPitch = firstPartLocation.getLinearLengthTo(secondPartLocation);
                // Round to the nearest 2mm (parts are spaced either 2mm or 4mm in the tape)
                Length partPitchMM = partPitch.convertToUnits(LengthUnit.Millimeters);
                long standardPitchIncrements = Math.round(partPitchMM.getValue() / 2.0);
                if (standardPitchIncrements == 0) {
                    throw new Exception(Translations.getString("StripFeederAutoSetup.SamePart")); //$NON-NLS-1$
                }
                partPitchMM = new Length(2.0 * standardPitchIncrements, LengthUnit.Millimeters);

                feeder.setReferenceHoleLocation(referenceHole1);
                feeder.setLastHoleLocation(referenceHole2);
                feeder.setPartPitch(partPitchMM.convertToUnits(firstPartLocation.getUnits()));

                feeder.setFeedCount(1);
                camera.moveTo(feeder.getPickLocation());
                MovableUtils.fireTargetedUserAction(camera);
                feeder.setFeedCount(0);

                cameraView.setText(Translations.getString("StripFeederAutoSetup.Complete")); //$NON-NLS-1$
                Thread.sleep(1500);
                SwingUtilities.invokeLater(() -> {
                    cancel();
                    done.run();
                });
                return null;
            }, new FutureCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                }

                @Override
                public void onFailure(final Throwable t) {
                    failed(t);
                }
            });
        }
    };

    private List<Location> findHoles(Camera camera) throws Exception {
        // Process the pipeline to clean up the image and detect the tape holes
        try (CvPipeline pipeline = pipeline(feeder, camera, true)) {
            pipeline.process();
            List<CvStage.Result.Circle> inLine = new FindHoles(camera, pipeline).invoke().getInLine();
            if (inLine.isEmpty()) {
                throw new Exception(String.format(Translations.getString("StripFeederAutoSetup.NoHoles"), //$NON-NLS-1$
                        feeder.getName()));
            }
            List<Location> holeLocations = new ArrayList<>();
            for (CvStage.Result.Circle result : inLine) {
                holeLocations.add(VisionUtils.getPixelLocation(camera, result.x, result.y));
            }
            return holeLocations;
        }
    }

    /**
     * Candidate holes on the image: orange are the lines tried, yellow the best of them, blue the
     * holes on it and green the two nearest the part.
     */
    private BufferedImage showHoles(Camera camera, BufferedImage image) throws Exception {
        BufferedImageCamera bufferedImageCamera = BufferedImageCamera.get(camera, image);
        try (CvPipeline pipeline = pipeline(feeder, bufferedImageCamera, true)) {
            pipeline.process();
            Mat resultMat = pipeline.getWorkingImage().clone();
            FindHoles findHolesResults = new FindHoles(camera, pipeline).invoke();
            List<CvStage.Result.Circle> inLine = findHolesResults.getInLine();
            drawLines(resultMat, findHolesResults.getLines(), Color.orange, 1);
            if (findHolesResults.getBestLine() != null) {
                drawLine(resultMat, findHolesResults.getBestLine(), Color.yellow, 2);
            }
            drawCircles(resultMat, inLine, inLine.size(), Color.blue);
            drawCircles(resultMat, inLine, 2, Color.green);
            BufferedImage showResult = OpenCvUtils.toBufferedImage(resultMat);
            resultMat.release();
            return showResult;
        }
    }

    private class FindHoles {
        private final Camera camera;
        private final CvPipeline pipeline;
        private List<Ransac.Line> lines;
        private Ransac.Line bestLine;
        private List<CvStage.Result.Circle> inLine;

        FindHoles(Camera camera, CvPipeline pipeline) {
            this.camera = camera;
            this.pipeline = pipeline;
        }

        List<Ransac.Line> getLines() {
            return lines;
        }

        Ransac.Line getBestLine() {
            return bestLine;
        }

        List<CvStage.Result.Circle> getInLine() {
            return inLine;
        }

        FindHoles invoke() throws Exception {
            List<CvStage.Result.Circle> results = pipeline.getExpectedResult(VisionUtils.PIPELINE_RESULTS_NAME)
                    .getExpectedListModel(CvStage.Result.Circle.class, null);
            // Sort by the distance to the camera center (which is over the part, not the hole)
            results.sort((a, b) -> {
                Double da = VisionUtils.getPixelLocation(camera, a.x, a.y).getLinearDistanceTo(camera.getLocation());
                Double db = VisionUtils.getPixelLocation(camera, b.x, b.y).getLinearDistanceTo(camera.getLocation());
                return da.compareTo(db);
            });
            double maxDistanceToLine = VisionUtils.toPixels(feeder.getHoleLineDistanceMax(), camera);
            double minDistancePx = VisionUtils.toPixels(feeder.getHoleDistanceMin(), camera);
            double maxDistancePx = VisionUtils.toPixels(feeder.getHoleDistanceMax(), camera);
            double holePitchPx = VisionUtils.toPixels(feeder.getHolePitch(), camera);
            double minHolePitchPx = VisionUtils.toPixels(feeder.getHolePitchMin(), camera);

            List<Point> points = new ArrayList<>();
            for (CvStage.Result.Circle circle : results) {
                points.add(new Point(circle.x, circle.y));
            }
            lines = Ransac.ransac(points, 100, maxDistanceToLine, holePitchPx, holePitchPx - minHolePitchPx, true);
            bestLine = null;
            for (Ransac.Line line : lines) {
                Location aLocation = VisionUtils.getPixelLocation(camera, line.a.x, line.a.y);
                Location bLocation = VisionUtils.getPixelLocation(camera, line.b.x, line.b.y);
                // The distance to the line *segment*: the line spans all the holes that meet the
                // criteria.
                Double distance = camera.getLocation().getLinearDistanceToLineSegment(aLocation, bLocation);
                Double distancePx = VisionUtils.toPixels(new Length(distance, camera.getLocation().getUnits()), camera);
                // The camera is over the part, not the hole: circles in the part are ignored.
                if ((distancePx >= minDistancePx) && (distancePx <= maxDistancePx)) {
                    // The longest line that is close enough: the lines come longest first.
                    bestLine = line;
                    break;
                }
            }
            inLine = new ArrayList<>();
            if (bestLine != null) {
                List<CvStage.Result.Circle> realLine = new ArrayList<>();
                for (CvStage.Result.Circle circle : results) {
                    if (FluentCv.pointToLineDistance(bestLine.a, bestLine.b, new Point(circle.x, circle.y))
                            <= maxDistanceToLine) {
                        realLine.add(circle);
                    }
                }
                // The average offset from the ideal centre positions.
                Point a = bestLine.a;
                Point b = bestLine.b;
                Point ab = new Point(b.x - a.x, b.y - a.y);
                double lineLen = Math.sqrt(ab.dot(ab));
                Point lineDir = new Point(ab.x / lineLen, ab.y / lineLen);
                Point totalOffsets = new Point();
                for (CvStage.Result.Circle circle : realLine) {
                    Point ap = new Point(circle.x - a.x, circle.y - a.y);
                    double distAlongLine = ap.dot(lineDir) / lineDir.dot(lineDir);
                    double fittedLen = (double) Math.round(distAlongLine / holePitchPx) * holePitchPx;
                    Point fittedPos = new Point(a.x + lineDir.x * fittedLen, a.y + lineDir.y * fittedLen);
                    totalOffsets.x += fittedPos.x - circle.x;
                    totalOffsets.y += fittedPos.y - circle.y;
                }
                Point avgOffset = new Point(totalOffsets.x / realLine.size(), totalOffsets.y / realLine.size());
                // The holes fitted to the best line at the expected spacing.
                Point fittedA = new Point(a.x - avgOffset.x, a.y - avgOffset.y);
                for (CvStage.Result.Circle circle : realLine) {
                    Point ap = new Point(circle.x - a.x, circle.y - a.y);
                    double distAlongLine = ap.dot(lineDir) / lineDir.dot(lineDir);
                    double fittedLen = (double) Math.round(distAlongLine / holePitchPx) * holePitchPx;
                    Point fittedP = new Point(fittedA.x + lineDir.x * fittedLen, fittedA.y + lineDir.y * fittedLen);
                    inLine.add(new CvStage.Result.Circle(fittedP.x, fittedP.y, circle.diameter));
                }
            }
            return this;
        }
    }

    private static void drawCircles(Mat mat, List<CvStage.Result.Circle> circles, int numToDraw, Color color) {
        Color centerColor = new HslColor(color).getComplementary();
        numToDraw = Math.min(numToDraw, circles.size());
        for (int i = 0; i < numToDraw; i++) {
            CvStage.Result.Circle circle = circles.get(i);
            Imgproc.circle(mat, new Point(circle.x, circle.y), (int) (circle.diameter / 2.0),
                    FluentCv.colorToScalar(color), 2, Imgproc.LINE_AA);
            Imgproc.circle(mat, new Point(circle.x, circle.y), 1, FluentCv.colorToScalar(centerColor), 2,
                    Imgproc.LINE_AA);
        }
    }

    private static void drawLines(Mat mat, List<Ransac.Line> lines, Color color, int thickness) {
        for (Ransac.Line line : lines) {
            drawLine(mat, line, color, thickness);
        }
    }

    private static void drawLine(Mat mat, Ransac.Line line, Color color, int thickness) {
        Imgproc.line(mat, line.a, line.b, FluentCv.colorToScalar(color), thickness);
    }

    private List<Location> deriveReferenceHoles(Location firstPartLocation, Location secondPartLocation,
            List<Location> part1HoleLocations, List<Location> part2HoleLocations) throws Exception {
        Location partsLocationRay = secondPartLocation.subtract(firstPartLocation);
        Point feedDirection = new Point(partsLocationRay.getX(), partsLocationRay.getY());

        // Only the pair of holes closest to each part.
        part1HoleLocations = part1HoleLocations.subList(0, Math.min(2, part1HoleLocations.size()));
        part2HoleLocations = part2HoleLocations.subList(0, Math.min(2, part2HoleLocations.size()));

        // Part 2's reference hole is the one farthest from part 1, in the direction of part 2.
        List<LocationsAlongRay> part2HoleLocationsAlongRay = new ArrayList<>(part2HoleLocations.size());
        for (Location partLocation : part2HoleLocations) {
            Location loc = partLocation.convertToUnits(partsLocationRay.getUnits());
            Point p = new Point(loc.getX() - firstPartLocation.getX(), loc.getY() - firstPartLocation.getY());
            part2HoleLocationsAlongRay.add(new LocationsAlongRay(partLocation, feedDirection.dot(p)));
        }
        part2HoleLocationsAlongRay.sort(null);
        List<Location> part2SortedLocations = new ArrayList<>(part2HoleLocationsAlongRay.size());
        part2HoleLocationsAlongRay.forEach(locationAlongRay -> part2SortedLocations.add(locationAlongRay.location));
        List<Location> part2ReverseSortedLocations = Lists.reverse(part2SortedLocations);
        Location part2ReferenceHole = part2ReverseSortedLocations.get(0);

        // Part 1's reference hole is the one closest to part 2's reference hole.
        List<Location> part1SortedLocations = VisionUtils.sortLocationsByDistance(part2ReferenceHole, part1HoleLocations);
        Location part1ReferenceHole = part1SortedLocations.get(0);
        double holePitchMin = feeder.getHolePitchMin().convertToUnits(part1ReferenceHole.getUnits()).getValue();
        double referenceHoleDistance = part1ReferenceHole.getLinearDistanceTo(part2ReferenceHole);
        // With a 2mm part pitch both parts can have the same reference hole: then the other one.
        if (referenceHoleDistance < holePitchMin) {
            part1ReferenceHole = part1SortedLocations.get(1);
        }

        // The holes must be to the right of the feed direction: (y, -x) is the vector rotated 90°
        // to the right.
        Point expectedHoleHalfspace = new Point(feedDirection.y, -feedDirection.x);
        Location hole1RelativeLocation = part1ReferenceHole.subtract(firstPartLocation);
        Location hole2RelativeLocation = part2ReferenceHole.subtract(firstPartLocation);
        double h1Dist = expectedHoleHalfspace.dot(new Point(hole1RelativeLocation.getX(), hole1RelativeLocation.getY()));
        double h2Dist = expectedHoleHalfspace.dot(new Point(hole2RelativeLocation.getX(), hole2RelativeLocation.getY()));
        boolean correctOrientation = (h1Dist > 0.0) && (h2Dist > 0.0);

        if (logDebugInfo) {
            Logger.info("deriveReferenceHoles"); //$NON-NLS-1$
            Logger.info("  feedDirection: " + feedDirection); //$NON-NLS-1$
            Logger.info("  firstPartLocation: " + firstPartLocation); //$NON-NLS-1$
            Logger.info("  secondPartLocation: " + secondPartLocation); //$NON-NLS-1$
            Logger.info("  part1HoleLocations: " + part1HoleLocations); //$NON-NLS-1$
            Logger.info("  part2HoleLocations: " + part2HoleLocations); //$NON-NLS-1$
            Logger.info("  part2HoleLocationsAlongRay: " + part2HoleLocationsAlongRay); //$NON-NLS-1$
            Logger.info("  referenceHoleDistance: " + referenceHoleDistance); //$NON-NLS-1$
            Logger.info("  h1Dist: " + h1Dist + ", h2Dist: " + h2Dist); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (!correctOrientation) {
            throw new Exception(Translations.getString("StripFeederAutoSetup.WrongOrientation")); //$NON-NLS-1$
        }
        List<Location> referenceHoles = new ArrayList<>();
        referenceHoles.add(part1ReferenceHole);
        referenceHoles.add(part2ReferenceHole);
        return referenceHoles;
    }

    private static class LocationsAlongRay implements Comparable<LocationsAlongRay> {
        final Location location;
        final double distance;

        LocationsAlongRay(Location location, double distance) {
            this.location = location;
            this.distance = distance;
        }

        @Override
        public int compareTo(LocationsAlongRay o) {
            return Double.compare(this.distance, o.distance);
        }

        @Override
        public String toString() {
            return String.format(Locale.US, "(%s, %s)", Double.toString(distance), location.toString()); //$NON-NLS-1$
        }
    }

    /** The feeder's hole finding pipeline set up for the camera, as the old form did. */
    static CvPipeline pipeline(ReferenceStripFeeder feeder, Camera camera, boolean clone) {
        Integer pxMinDistance = (int) VisionUtils.toPixels(feeder.getHolePitchMin(), camera);
        Integer pxMinDiameter = (int) VisionUtils.toPixels(feeder.getHoleDiameterMin(), camera);
        Integer pxMaxDiameter = (int) VisionUtils.toPixels(feeder.getHoleDiameterMax(), camera);
        try {
            CvPipeline pipeline = feeder.getPipeline();
            if (clone) {
                pipeline = pipeline.clone();
            }
            pipeline.setProperty("camera", camera); //$NON-NLS-1$
            pipeline.setProperty("feeder", feeder); //$NON-NLS-1$
            pipeline.setProperty("DetectFixedCirclesHough.minDistance", pxMinDistance); //$NON-NLS-1$
            pipeline.setProperty("DetectFixedCirclesHough.minDiameter", pxMinDiameter); //$NON-NLS-1$
            pipeline.setProperty("DetectFixedCirclesHough.maxDiameter", pxMaxDiameter); //$NON-NLS-1$
            pipeline.setProperty("sprocketHole.diameter", feeder.getHoleDiameter()); //$NON-NLS-1$
            // Search Range is half camera.
            Length range = camera.getWidth() > camera.getHeight()
                    ? camera.getUnitsPerPixelAtZ().getLengthY().multiply(camera.getHeight() / 2)
                    : camera.getUnitsPerPixelAtZ().getLengthX().multiply(camera.getWidth() / 2);
            pipeline.setProperty("sprocketHole.maxDistance", range); //$NON-NLS-1$
            return pipeline;
        }
        catch (CloneNotSupportedException e) {
            throw new Error(e);
        }
    }
}
