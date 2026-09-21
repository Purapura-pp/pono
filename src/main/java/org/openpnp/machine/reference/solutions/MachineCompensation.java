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

package org.openpnp.machine.reference.solutions;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis;
import org.openpnp.machine.reference.axis.ReferenceLinearTransformAxis;
import org.openpnp.machine.reference.feeder.ReferenceSlotAutoFeeder;
import org.openpnp.model.Job;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.PlacementsHolderLocation;
import org.openpnp.spi.Axis;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Feeder;
import org.openpnp.spi.Head;
import org.openpnp.spi.HeadMountable;
import org.openpnp.spi.NozzleTip;
import org.openpnp.spi.base.AbstractAxis;
import org.openpnp.spi.base.AbstractHeadMountable;
import org.pmw.tinylog.Logger;

/**
 * The machine's frame, as the datum board measured it, turned into a correction.
 * <p>
 * The board gave the machine's millimetre along each axis and the angle its Y axis leans from
 * square. Their inverse is a linear transform from the controller's coordinates to true
 * millimetres; it goes into two {@link ReferenceLinearTransformAxis} that every head mountable
 * moves in, so that the controller axes go on counting their own millimetres underneath and the
 * backlash compensation, soft limits and homing that live there are untouched.
 * <p>
 * Every coordinate ever taught on the machine was taught in the controller's millimetre, so
 * all of them are carried across by the same transform - absolute positions by the transform
 * proper, offsets by its scale alone - and the cameras' Units per Pixel, which were calibrated
 * by moving the machine, are put into true millimetres too. The primary fiducial keeps its
 * coordinates, which is what the transform's offset is chosen for. Everything changed is
 * recorded so that it can be put back.
 */
public final class MachineCompensation {
    /** Controller millimetres per true millimetre along X and Y. */
    public final double scaleX;
    public final double scaleY;
    /** Degrees the controller's Y axis leans towards +X, short of square. Zero to leave it. */
    public final double shearDegrees;
    /** The point whose coordinates do not change: the primary fiducial, in controller units. */
    public final Location anchor;

    /** X' = fx*x + fxy*y + ox, Y' = fy*y + oy, from controller (raw) to true. */
    public final double fx, fxy, fy, ox, oy;

    public MachineCompensation(double scaleX, double scaleY, double shearDegrees, Location anchor) {
        if (!(scaleX > 0.9 && scaleX < 1.1 && scaleY > 0.9 && scaleY < 1.1)) {
            throw new IllegalArgumentException("A scale of " + scaleX + " / " + scaleY
                    + " is not a machine to compensate; it is a machine to look at.");
        }
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.shearDegrees = shearDegrees;
        this.anchor = anchor.convertToUnits(LengthUnit.Millimeters);
        double phi = Math.toRadians(shearDegrees);
        // D = [[sx, sy sin(phi)], [0, sy cos(phi)]] takes true to raw; this is its inverse.
        fx = 1 / scaleX;
        fxy = -Math.tan(phi) / scaleX;
        fy = 1 / (scaleY * Math.cos(phi));
        ox = this.anchor.getX() - (fx * this.anchor.getX() + fxy * this.anchor.getY());
        oy = this.anchor.getY() - fy * this.anchor.getY();
    }

    /** From what the datum board measured. */
    public static MachineCompensation of(MachineDiagnosticsResults.Datum datum, Location anchor,
            boolean includeSquareness) {
        return new MachineCompensation(datum.getScaleX(), datum.getScaleY(),
                includeSquareness ? datum.getShearDegrees() : 0, anchor);
    }

    /** An absolute position, controller units to true millimetres. Z and rotation unchanged. */
    public Location toTrue(Location raw) {
        Location r = raw.convertToUnits(LengthUnit.Millimeters);
        return new Location(LengthUnit.Millimeters, fx * r.getX() + fxy * r.getY() + ox,
                fy * r.getY() + oy, r.getZ(), r.getRotation()).convertToUnits(raw.getUnits());
    }

    /** The inverse: true millimetres back to controller units. */
    public Location toRaw(Location trueLocation) {
        Location t = trueLocation.convertToUnits(LengthUnit.Millimeters);
        double y = (t.getY() - oy) / fy;
        double x = (t.getX() - ox - fxy * y) / fx;
        return new Location(LengthUnit.Millimeters, x, y, t.getZ(), t.getRotation())
                .convertToUnits(trueLocation.getUnits());
    }

    /** A difference of positions - an offset - which the transform scales but does not shift. */
    public Location toTrueVector(Location raw) {
        Location r = raw.convertToUnits(LengthUnit.Millimeters);
        return new Location(LengthUnit.Millimeters, fx * r.getX() + fxy * r.getY(), fy * r.getY(),
                r.getZ(), r.getRotation()).convertToUnits(raw.getUnits());
    }

    public Location toRawVector(Location trueVector) {
        Location t = trueVector.convertToUnits(LengthUnit.Millimeters);
        double y = t.getY() / fy;
        double x = (t.getX() - fxy * y) / fx;
        return new Location(LengthUnit.Millimeters, x, y, t.getZ(), t.getRotation())
                .convertToUnits(trueVector.getUnits());
    }

    /** Units per Pixel, calibrated in controller millimetres, in true millimetres. */
    public Location unitsPerPixelToTrue(Location upp) {
        Location u = upp.convertToUnits(LengthUnit.Millimeters);
        return new Location(LengthUnit.Millimeters, u.getX() / scaleX, u.getY() / scaleY, u.getZ(),
                u.getRotation()).convertToUnits(upp.getUnits());
    }

    public Location unitsPerPixelToRaw(Location upp) {
        Location u = upp.convertToUnits(LengthUnit.Millimeters);
        return new Location(LengthUnit.Millimeters, u.getX() * scaleX, u.getY() * scaleY, u.getZ(),
                u.getRotation()).convertToUnits(upp.getUnits());
    }

    // ---- what a Location property is ----------------------------------------------------------

    /** How a Location-typed property is carried across, decided from its name. */
    public enum Kind {
        /** A position on the machine. */
        Absolute,
        /** A difference of positions: head offsets, tray pitch. */
        Vector,
        /** Units per pixel, pixels, homing, or otherwise not a machine coordinate. */
        Skip
    }

    public static Kind kindOf(String propertyName) {
        String name = propertyName;
        String lower = name.toLowerCase();
        // The homing fiducial location is a camera location like any other: visual homing moves
        // the camera to it and converts it to raw through the axes, so it is carried across.
        if (lower.contains("unitsperpixel") || lower.contains("templateimage")
                || lower.contains("safez") || lower.contains("pixel")) {
            return Kind.Skip;
        }
        if (lower.contains("offset") || lower.equals("partpick") || lower.equals("visionoffset")) {
            return Kind.Vector;
        }
        if (lower.endsWith("location") || lower.startsWith("hole") || lower.startsWith("fiducial")
                || lower.startsWith("way") || lower.startsWith("feed") || lower.equals("drop")
                || lower.contains("location")) {
            return Kind.Absolute;
        }
        return Kind.Skip;
    }

    // ---- applying ----------------------------------------------------------------------------

    /** One property put through the transform, with what it was, so that it can be put back. */
    public static final class Change {
        public final Object target;
        public final String property;
        public final Kind kind;
        public final Location before;
        public final Location after;
        private final Method setter;

        Change(Object target, String property, Kind kind, Location before, Location after, Method setter) {
            this.target = target;
            this.property = property;
            this.kind = kind;
            this.before = before;
            this.after = after;
            this.setter = setter;
        }

        void undo() throws Exception {
            setter.invoke(target, before);
        }

        @Override
        public String toString() {
            return String.format("%s.%s (%s): %s -> %s", describe(target), property, kind,
                    before, after);
        }
    }

    /** Everything that was done, in the order it was done. */
    public static final class Applied {
        public final List<Change> changes = new ArrayList<>();
        /** Location properties that were seen but left alone, for the user to look at. */
        public final List<String> skipped = new ArrayList<>();
        /** Location properties that were all zero - never taught - and stay so. */
        public final List<String> unset = new ArrayList<>();
        public final List<ReferenceLinearTransformAxis> createdAxes = new ArrayList<>();
        final Map<ReferenceLinearTransformAxis, double[]> previousFactors = new IdentityHashMap<>();
        final Map<AbstractHeadMountable, AbstractAxis[]> previousAxes = new IdentityHashMap<>();

        /** Put everything back: the properties, the axes, the head mountables. */
        public void undo(ReferenceMachine machine) throws Exception {
            for (int i = changes.size() - 1; i >= 0; i--) {
                changes.get(i).undo();
            }
            for (Map.Entry<AbstractHeadMountable, AbstractAxis[]> e : previousAxes.entrySet()) {
                e.getKey().setAxisX(e.getValue()[0]);
                e.getKey().setAxisY(e.getValue()[1]);
            }
            for (Map.Entry<ReferenceLinearTransformAxis, double[]> e : previousFactors.entrySet()) {
                double[] p = e.getValue();
                e.getKey().setFactorX(p[0]);
                e.getKey().setFactorY(p[1]);
                e.getKey().setOffset(new Length(p[2], LengthUnit.Millimeters));
            }
            for (ReferenceLinearTransformAxis axis : createdAxes) {
                machine.removeAxis(axis);
            }
        }
    }

    /**
     * Put the compensation into the machine: the two transform axes, every head mountable moved
     * onto them, and every taught coordinate carried across. Nothing is saved here; the caller
     * saves once it has verified, or undoes.
     *
     * @param rawX The controller X axis the head mountables move in today.
     * @param job  The open job, whose board positions are machine coordinates; null for none.
     */
    public Applied apply(ReferenceMachine machine, ReferenceControllerAxis rawX,
            ReferenceControllerAxis rawY, Job job) throws Exception {
        Applied applied = new Applied();
        // The axes. An earlier compensation is updated rather than stacked.
        ReferenceLinearTransformAxis trueX = existingCompensation(machine, rawX, Axis.Type.X);
        ReferenceLinearTransformAxis trueY = existingCompensation(machine, rawY, Axis.Type.Y);
        if (trueX == null) {
            trueX = new ReferenceLinearTransformAxis();
            trueX.setType(Axis.Type.X);
            trueX.setName(rawX.getName() + " compensated");
            trueX.setInputAxisX(rawX);
            trueX.setInputAxisY(rawY);
            trueX.setCompensation(true);
            machine.addAxis(trueX);
            applied.createdAxes.add(trueX);
        }
        else {
            applied.previousFactors.put(trueX, new double[] { trueX.getFactorX(), trueX.getFactorY(),
                    trueX.getOffset().convertToUnits(LengthUnit.Millimeters).getValue() });
        }
        if (trueY == null) {
            trueY = new ReferenceLinearTransformAxis();
            trueY.setType(Axis.Type.Y);
            trueY.setName(rawY.getName() + " compensated");
            trueY.setInputAxisY(rawY);
            trueY.setCompensation(true);
            machine.addAxis(trueY);
            applied.createdAxes.add(trueY);
        }
        else {
            applied.previousFactors.put(trueY, new double[] { trueY.getFactorX(), trueY.getFactorY(),
                    trueY.getOffset().convertToUnits(LengthUnit.Millimeters).getValue() });
        }
        if (applied.previousFactors.isEmpty()) {
            trueX.setFactorX(fx);
            trueX.setFactorY(fxy);
            trueX.setOffset(new Length(ox, LengthUnit.Millimeters));
            trueY.setFactorX(0);
            trueY.setFactorY(fy);
            trueY.setOffset(new Length(oy, LengthUnit.Millimeters));
        }
        else {
            // A compensation already stands, and this one was measured through it: the board
            // was read in the compensated coordinates. So this transform is composed onto the
            // previous one rather than replacing it - X'' = fx X' + fxy Y' + ox with
            // X' = a x + b y + c and Y' = d y + e.
            double a = trueX.getFactorX(), b = trueX.getFactorY();
            double c = trueX.getOffset().convertToUnits(LengthUnit.Millimeters).getValue();
            double d = trueY.getFactorY();
            double e = trueY.getOffset().convertToUnits(LengthUnit.Millimeters).getValue();
            trueX.setFactorX(fx * a);
            trueX.setFactorY(fx * b + fxy * d);
            trueX.setOffset(new Length(fx * c + fxy * e + ox, LengthUnit.Millimeters));
            trueY.setFactorX(0);
            trueY.setFactorY(fy * d);
            trueY.setOffset(new Length(fy * e + oy, LengthUnit.Millimeters));
        }

        // Every head mountable that moved in the raw axes moves in the true ones.
        for (Head head : machine.getHeads()) {
            for (HeadMountable hm : head.getHeadMountables()) {
                if (hm instanceof AbstractHeadMountable) {
                    AbstractHeadMountable a = (AbstractHeadMountable) hm;
                    if (a.getAxisX() == rawX || a.getAxisY() == rawY) {
                        applied.previousAxes.put(a, new AbstractAxis[] { a.getAxisX(), a.getAxisY() });
                        if (a.getAxisX() == rawX) {
                            a.setAxisX(trueX);
                        }
                        if (a.getAxisY() == rawY) {
                            a.setAxisY(trueY);
                        }
                    }
                }
            }
        }

        // The coordinates.
        carry(applied, machine, machine);
        for (Head head : machine.getHeads()) {
            carry(applied, head, machine);
            for (HeadMountable hm : head.getHeadMountables()) {
                carry(applied, hm, machine);
            }
        }
        for (Camera camera : machine.getCameras()) {
            // A camera off the head: its "head offsets" are its position on the machine.
            carryCameraOffTheHead(applied, camera);
        }
        for (NozzleTip tip : machine.getNozzleTips()) {
            carry(applied, tip, machine);
        }
        for (Feeder feeder : machine.getFeeders()) {
            carry(applied, feeder, machine);
        }
        try {
            for (ReferenceSlotAutoFeeder.Bank bank : ReferenceSlotAutoFeeder.getBanks(machine)) {
                carry(applied, bank, machine);
                Object feeders = bank.getClass().getMethod("getFeeders").invoke(bank);
                if (feeders instanceof Iterable) {
                    for (Object slotFeeder : (Iterable<?>) feeders) {
                        carry(applied, slotFeeder, machine);
                    }
                }
            }
        }
        catch (Exception e) {
            Logger.trace(e, "Machine compensation: slot auto feeder banks");
        }
        if (job != null) {
            for (PlacementsHolderLocation<?> location : job.getBoardAndPanelLocations()) {
                carryOne(applied, location, "location", Kind.Absolute,
                        location.getLocation(), location.getClass().getMethod("setLocation", Location.class));
            }
        }
        // Cameras: Units per Pixel into true millimetres.
        for (Camera camera : allCameras(machine)) {
            carryUnitsPerPixel(applied, camera);
        }
        return applied;
    }

    private static ReferenceLinearTransformAxis existingCompensation(ReferenceMachine machine,
            ReferenceControllerAxis raw, Axis.Type type) {
        for (Axis axis : machine.getAxes()) {
            if (axis instanceof ReferenceLinearTransformAxis && axis.getType() == type) {
                ReferenceLinearTransformAxis t = (ReferenceLinearTransformAxis) axis;
                if (t.isCompensation() && (type == Axis.Type.X ? t.getInputAxisX() == raw
                        : t.getInputAxisY() == raw)) {
                    return t;
                }
            }
        }
        return null;
    }

    private static List<Camera> allCameras(ReferenceMachine machine) {
        List<Camera> cameras = new ArrayList<>(machine.getCameras());
        for (Head head : machine.getHeads()) {
            cameras.addAll(head.getCameras());
        }
        return cameras;
    }

    private void carryUnitsPerPixel(Applied applied, Camera camera) throws Exception {
        Location upp = camera.getUnitsPerPixel();
        if (upp != null) {
            Method setter = camera.getClass().getMethod("setUnitsPerPixel", Location.class);
            Location after = unitsPerPixelToTrue(upp);
            setter.invoke(camera, after);
            applied.changes.add(new Change(camera, "unitsPerPixel", Kind.Vector, upp, after, setter));
        }
        try {
            Method getSecondary = camera.getClass().getMethod("getUnitsPerPixelSecondary");
            Method setSecondary = camera.getClass().getMethod("setUnitsPerPixelSecondary", Location.class);
            Location secondary = (Location) getSecondary.invoke(camera);
            if (secondary != null && (secondary.getX() != 0 || secondary.getY() != 0)) {
                Location after = unitsPerPixelToTrue(secondary);
                setSecondary.invoke(camera, after);
                applied.changes.add(new Change(camera, "unitsPerPixelSecondary", Kind.Vector,
                        secondary, after, setSecondary));
            }
        }
        catch (NoSuchMethodException e) {
            // Not every camera has a secondary.
        }
    }

    private void carryCameraOffTheHead(Applied applied, Camera camera) throws Exception {
        if (camera.getHead() != null) {
            return;
        }
        try {
            Method getter = camera.getClass().getMethod("getHeadOffsets");
            Method setter = camera.getClass().getMethod("setHeadOffsets", Location.class);
            Location before = (Location) getter.invoke(camera);
            if (before != null) {
                carryOne(applied, camera, "headOffsets", Kind.Absolute, before, setter);
            }
        }
        catch (NoSuchMethodException e) {
            applied.skipped.add(describe(camera) + ".headOffsets (no accessor)");
        }
    }

    /** Every writable Location property of an object, by the kind its name says it is. */
    private void carry(Applied applied, Object target, ReferenceMachine machine) throws Exception {
        BeanInfo info = Introspector.getBeanInfo(target.getClass());
        for (PropertyDescriptor pd : info.getPropertyDescriptors()) {
            if (pd.getPropertyType() != Location.class || pd.getReadMethod() == null
                    || pd.getWriteMethod() == null) {
                continue;
            }
            if (target instanceof Camera && ((Camera) target).getHead() == null
                    && pd.getName().equals("headOffsets")) {
                // Off the head it is a position, handled by carryCameraOffTheHead.
                continue;
            }
            Kind kind = kindOf(pd.getName());
            Location before;
            try {
                before = (Location) pd.getReadMethod().invoke(target);
            }
            catch (Exception e) {
                applied.skipped.add(describe(target) + "." + pd.getName() + " (" + e.getMessage() + ")");
                continue;
            }
            if (before == null) {
                continue;
            }
            if (kind == Kind.Skip) {
                if (!pd.getName().toLowerCase().contains("unitsperpixel")) {
                    applied.skipped.add(describe(target) + "." + pd.getName());
                }
                continue;
            }
            carryOne(applied, target, pd.getName(), kind, before, pd.getWriteMethod());
        }
    }

    /** All four coordinates zero: never taught. A position that was never taught stays untaught. */
    static boolean isUnset(Location location) {
        return location.getX() == 0 && location.getY() == 0 && location.getZ() == 0
                && location.getRotation() == 0;
    }

    private void carryOne(Applied applied, Object target, String property, Kind kind,
            Location before, Method setter) throws Exception {
        if (isUnset(before)) {
            applied.unset.add(describe(target) + "." + property);
            return;
        }
        Location after = kind == Kind.Absolute ? toTrue(before) : toTrueVector(before);
        if (after.equals(before)) {
            return;
        }
        setter.invoke(target, after);
        applied.changes.add(new Change(target, property, kind, before, after, setter));
    }

    static String describe(Object target) {
        String name = null;
        try {
            Method getName = target.getClass().getMethod("getName");
            Object n = getName.invoke(target);
            name = n == null ? null : n.toString();
        }
        catch (Exception e) {
            // Not everything is named.
        }
        String type = target.getClass().getSimpleName();
        return name == null ? type : type + " " + name;
    }

    /** The changes as lines for a report. */
    public static List<String> describe(Applied applied) {
        List<String> lines = new ArrayList<>();
        for (Change change : applied.changes) {
            lines.add(change.toString());
        }
        return Collections.unmodifiableList(lines);
    }

    @Override
    public String toString() {
        return String.format("scale X %.5f, Y %.5f, shear %.4f deg; X' = %.6f x %+.6f y %+.4f, "
                + "Y' = %.6f y %+.4f", scaleX, scaleY, shearDegrees, fx, fxy, ox, fy, oy);
    }
}
