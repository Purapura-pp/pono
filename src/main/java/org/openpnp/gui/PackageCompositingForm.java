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

package org.openpnp.gui;

import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.openpnp.Translations;
import org.openpnp.gui.components.VisionCompositingPreview;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.support.DisplayNames;
import org.openpnp.machine.reference.vision.ReferenceBottomVision;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.BottomVisionSettings;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.Location;
import org.openpnp.model.Package;
import org.openpnp.model.VisionCompositing;
import org.openpnp.model.VisionCompositing.Composite;
import org.openpnp.model.VisionCompositing.Shot;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Head;
import org.openpnp.spi.Machine;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.NozzleTip;
import org.openpnp.util.VisionUtils;

/**
 * How bottom vision composes a part too large for one frame from several shots: the method and its
 * limits, and what it makes of this package's footprint, drawn, with how many shots it takes. The
 * wizard it replaces had the fields in a grid and a preview at least 480 pixels wide, wider than
 * the properties column.
 */
public final class PackageCompositingForm {
    private PackageCompositingForm() {
    }

    public static class Bean extends AbstractModelObject {
        private final VisionCompositing compositing;

        Bean(VisionCompositing compositing) {
            this.compositing = compositing;
        }

        public VisionCompositing.CompositingMethod getCompositingMethod() {
            return compositing.getCompositingMethod();
        }

        public void setCompositingMethod(VisionCompositing.CompositingMethod method) {
            compositing.setCompositingMethod(method);
        }

        public int getExtraShots() {
            return compositing.getExtraShots();
        }

        public void setExtraShots(int shots) {
            compositing.setExtraShots(shots);
        }

        public Length getMaxPickTolerance() {
            return compositing.getMaxPickTolerance();
        }

        public void setMaxPickTolerance(Length tolerance) {
            compositing.setMaxPickTolerance(tolerance);
        }

        public double getMinLeverageFactor() {
            return compositing.getMinLeverageFactor();
        }

        public void setMinLeverageFactor(double factor) {
            compositing.setMinLeverageFactor(factor);
        }

        public boolean isAllowInside() {
            return compositing.isAllowInside();
        }

        public void setAllowInside(boolean allow) {
            compositing.setAllowInside(allow);
        }
    }

    public static FormWizard build(Configuration configuration, Package pkg) {
        VisionCompositingPreview preview = new VisionCompositingPreview() {
            // As wide as the column: the preview wanted 480 pixels whatever it was given.
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(200, 240);
            }
        };
        JTextArea status = Forms.paragraph(""); //$NON-NLS-1$
        Runnable compute = () -> compute(configuration, pkg, preview, status);
        FormWizard[] form = new FormWizard[1];
        form[0] = Form.of(new Bean(pkg.getVisionCompositing())).named("PackagesPanel.VisionCompositingTab.title") //$NON-NLS-1$
                .section("PackageCompositingForm.Compositing", "layers") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("compositingMethod", "PackageCompositingForm.Method", VisionCompositing.CompositingMethod.class) //$NON-NLS-1$ //$NON-NLS-2$
                .integer("extraShots", "PackageCompositingForm.ExtraShots").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .note("PackageCompositingForm.ExtraShots.Note") //$NON-NLS-1$
                .length("maxPickTolerance", "PackageCompositingForm.MaxPickTolerance").width(120) //$NON-NLS-1$ //$NON-NLS-2$
                .decimal("minLeverageFactor", "PackageCompositingForm.MinLeverage").width(100) //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("allowInside", "PackageCompositingForm.AllowInside", //$NON-NLS-1$ //$NON-NLS-2$
                        "PackageCompositingForm.AllowInside.Note") //$NON-NLS-1$
                .section("PackageCompositingForm.Preview", "eye") //$NON-NLS-1$ //$NON-NLS-2$
                .custom("", status) //$NON-NLS-1$
                .custom("", preview) //$NON-NLS-1$
                .action("PackageCompositingForm.Recompute", "refresh", () -> { //$NON-NLS-1$ //$NON-NLS-2$
                    form[0].apply();
                    compute.run();
                })
                .build();
        watchFootprint(pkg, preview, compute);
        SwingUtilities.invokeLater(compute);
        return form[0];
    }

    /**
     * Recomputes when the footprint changes, for as long as the preview is there to show it: the
     * listener holds the preview weakly and takes itself off once it is gone. The wizard added a
     * listener on every visit and, until it was taken off on dispose, never removed one.
     */
    private static void watchFootprint(Package pkg, VisionCompositingPreview preview, Runnable compute) {
        WeakReference<VisionCompositingPreview> shown = new WeakReference<>(preview);
        WeakReference<Runnable> work = new WeakReference<>(compute);
        preview.putClientProperty(PackageCompositingForm.class, compute);
        PropertyChangeListener listener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                Runnable run = work.get();
                if (shown.get() == null || run == null) {
                    pkg.removePropertyChangeListener("footprint", this); //$NON-NLS-1$
                    return;
                }
                if (shown.get().isShowing()) {
                    run.run();
                }
            }
        };
        pkg.addPropertyChangeListener("footprint", listener); //$NON-NLS-1$
    }

    /** What the compositing makes of the footprint, with the first nozzle and tip that can pick it. */
    private static void compute(Configuration configuration, Package pkg, VisionCompositingPreview preview,
            JTextArea status) {
        try {
            Machine machine = configuration.getMachine();
            Camera camera = VisionUtils.getBottomVisionCamera();
            Nozzle nozzle = null;
            NozzleTip nozzleTip = null;
            for (Head head : machine.getHeads()) {
                for (Nozzle n : head.getNozzles()) {
                    for (NozzleTip tip : n.getCompatibleNozzleTips()) {
                        if (pkg.getCompatibleNozzleTips().contains(tip)) {
                            nozzle = n;
                            nozzleTip = tip;
                            break;
                        }
                    }
                    if (nozzleTip != null) {
                        break;
                    }
                }
                if (nozzleTip != null) {
                    break;
                }
            }
            if (nozzleTip == null) {
                throw new Exception(String.format(Translations.getString("PackageCompositingForm.NoNozzleTip"), //$NON-NLS-1$
                        pkg.getId()));
            }
            BottomVisionSettings settings = ReferenceBottomVision.getDefault().getInheritedVisionSettings(pkg);
            Composite composite = pkg.getVisionCompositing().new Composite(pkg, settings, nozzle, nozzleTip, camera,
                    Location.origin);
            int minShots = 0;
            int maxShots = 0;
            for (Shot shot : composite.getCompositeShots()) {
                maxShots++;
                if (!shot.isOptional()) {
                    minShots++;
                }
            }
            preview.setComposite(composite);
            String solution = DisplayNames.of(composite.getCompositingSolution());
            status.setText(composite.getCompositingSolution().isInvalid()
                    ? String.format(Translations.getString("PackageCompositingForm.Invalid"), solution, //$NON-NLS-1$
                            composite.getDiagnostics())
                    : String.format(Translations.getString("PackageCompositingForm.Solution"), solution, minShots, //$NON-NLS-1$
                            maxShots, String.format(java.util.Locale.ROOT, "%.2f", composite.getComputeTime()))); //$NON-NLS-1$
        }
        catch (Exception e) {
            preview.setComposite(null);
            status.setText(String.format(Translations.getString("PackageCompositingForm.Error"), e.getMessage())); //$NON-NLS-1$
        }
    }
}
