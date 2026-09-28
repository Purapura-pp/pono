/*
 * Copyright (C) 2011 Jason von Nieda <jason@vonnieda.org>
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

package org.openpnp.gui.components;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.openpnp.ConfigurationListener;
import org.openpnp.Translations;
import org.openpnp.gui.shell.PillBar;
import org.openpnp.gui.support.CameraItem;
import org.openpnp.model.Configuration;
import org.openpnp.spi.Camera;
import org.openpnp.spi.base.AbstractCamera;
import org.pmw.tinylog.Logger;

/**
 * Shows a square grid of cameras or a blown up image from a single camera.
 */
@SuppressWarnings("serial")
public class CameraPanel extends JPanel {
    private static final String SHOW_NONE_ITEM = "Show None";
    private static final String SHOW_ALL_ITEM_H = "Show All Horizontal";
    private static final String SHOW_ALL_ITEM_V = "Show All Vertical";

    private Map<Camera, CameraView> cameraViews = new LinkedHashMap<>();

    // Created with the panel rather than in createUi(), which runs later on the event queue:
    // whoever places this control asks for it while the panel is being built.
    private final PillBar camerasCombo = new PillBar();
    private JPanel camerasPanel;

    private CameraView selectedCameraView;


    private static final String PREF_SELECTED_CAMERA_VIEW = "JobPanel.dividerPosition";
    private Preferences prefs = Preferences.userNodeForPackage(CameraPanel.class);

    public CameraPanel() {
		SwingUtilities.invokeLater(() -> {
			createUi();
		});
        Configuration.get().addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationComplete(Configuration configuration) throws Exception {
            	SwingUtilities.invokeLater(() -> {
                    for (Camera camera : configuration.getMachine().getAllCameras()) {
                        addCamera(camera);
                    }

                    String selectedCameraView = prefs.get(PREF_SELECTED_CAMERA_VIEW, null);
                    if (selectedCameraView != null) {
                        for (int i = 0; i < camerasCombo.getItemCount(); i++) {
                            Object o = camerasCombo.getItemAt(i);
                            if (o.toString().equals(selectedCameraView)) {
                                camerasCombo.setSelectedItem(o);
                            }
                        }
                    }
                    camerasCombo.addActionListener((event) -> {
                        try {
                            prefs.put(PREF_SELECTED_CAMERA_VIEW,
                                    camerasCombo.getSelectedItem().toString());
                            prefs.flush();
                        }
                        catch (Exception e) {
                            Logger.warn(e, "Failed to store the selected camera view preference.");
                        }
                    });           		
            	});
            }
        });
    }

    private boolean stageMode;

    /**
     * See {@link CameraView#setStageMode(boolean)}: for every view, those to come included. On the
     * main window each picture is shown whole under a bar with its camera's name and scale, and
     * the panel is only as large as the pictures: see {@link #fittedSize(int, int)}.
     */
    public void setStageMode(boolean stageMode) {
        this.stageMode = stageMode;
        setOpaque(!stageMode);
        for (CameraView view : cameraViews.values()) {
            view.setStageMode(stageMode);
            if (stageMode) {
                view.setBackground(org.openpnp.gui.shell.Ui.cameraBg());
            }
        }
        if (camerasPanel != null) {
            relayoutPanel();
        }
    }

    /** Fired when the size the pictures want changes: another camera, or pictures of another shape. */
    public static final String PROPERTY_FIT = "fit"; //$NON-NLS-1$
    private int fitChanges;

    private java.util.function.Function<Camera, String> scaleText = camera -> {
        org.openpnp.model.Location upp = camera.getUnitsPerPixel();
        return upp == null ? "" : String.format(java.util.Locale.US, "%.4f %s/px", //$NON-NLS-1$ //$NON-NLS-2$
                Math.abs(upp.getX()), upp.getUnits().getShortName());
    };

    /** What the bar over a picture says of its scale, in the units the window shows. */
    public void setScaleText(java.util.function.Function<Camera, String> scaleText) {
        this.scaleText = scaleText;
        repaint();
    }

    /** The views on show, in their order. */
    private final java.util.List<CameraView> shown = new java.util.ArrayList<>();

    private double[] shownAspects() {
        double[] aspects = new double[shown.size()];
        for (int i = 0; i < aspects.length; i++) {
            aspects[i] = shown.get(i).getImageAspect();
        }
        return aspects;
    }

    /**
     * The size the pictures on show take in a rectangle of the given size: each whole and as large
     * as it fits, under its bar, and no black round it. With none on show, the whole rectangle.
     */
    public java.awt.Dimension fittedSize(int width, int height) {
        if (!stageMode || shown.isEmpty()) {
            return new java.awt.Dimension(width, height);
        }
        return CameraArrangement.arrange(width, height, shownAspects()).size;
    }

    /** Whether the pictures on show would be side by side in a rectangle of this size. */
    public boolean sideBySide(int width, int height) {
        return stageMode && CameraArrangement.arrange(width, height, shownAspects()).sideBySide;
    }

    private final PropertyChangeListener aspectListener = e -> fitChanged();

    private void fitChanged() {
        revalidate();
        repaint();
        firePropertyChange(PROPERTY_FIT, fitChanges, ++fitChanges);
    }

    /** The slots of the shown views, placed as CameraArrangement says, centred in what they get. */
    private final java.awt.LayoutManager stageLayout = new java.awt.LayoutManager() {
        @Override
        public void layoutContainer(java.awt.Container parent) {
            CameraArrangement.Result result = CameraArrangement.arrange(parent.getWidth(), parent.getHeight(), shownAspects());
            int x = (parent.getWidth() - result.size.width) / 2;
            int y = (parent.getHeight() - result.size.height) / 2;
            for (int i = 0; i < parent.getComponentCount(); i++) {
                java.awt.Component c = parent.getComponent(i);
                if (i < result.slots.size()) {
                    java.awt.Rectangle r = result.slots.get(i);
                    c.setBounds(x + r.x, y + r.y, r.width, r.height);
                }
                else {
                    c.setBounds(0, 0, 0, 0);
                }
            }
        }

        @Override
        public java.awt.Dimension preferredLayoutSize(java.awt.Container parent) {
            return new java.awt.Dimension(0, 0);
        }

        @Override
        public java.awt.Dimension minimumLayoutSize(java.awt.Container parent) {
            return new java.awt.Dimension(0, 0);
        }

        @Override
        public void addLayoutComponent(String name, java.awt.Component comp) {
        }

        @Override
        public void removeLayoutComponent(java.awt.Component comp) {
        }
    };

    /** A view under the bar that names its camera, with rounded corners over both. */
    private final class Slot extends JPanel {
        private final CameraView view;
        private final javax.swing.JComponent caption = new javax.swing.JComponent() {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                try {
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                            java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(org.openpnp.gui.shell.Ui.mix(Color.WHITE, org.openpnp.gui.shell.Ui.cameraBg(), 0.07));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    java.awt.Font nameFont = org.openpnp.gui.shell.Ui.font(12f, java.awt.Font.BOLD);
                    java.awt.FontMetrics nm = g2.getFontMetrics(nameFont);
                    int baseline = (getHeight() + nm.getAscent() - nm.getDescent()) / 2;
                    g2.setFont(nameFont);
                    g2.setColor(new Color(0xc9d1dc));
                    String name = view.getCamera() == null ? "" : view.getCamera().getName(); //$NON-NLS-1$
                    g2.drawString(name, 10, baseline);
                    String scale = view.getCamera() == null ? "" : scaleText.apply(view.getCamera()); //$NON-NLS-1$
                    java.awt.Font scaleFont = org.openpnp.gui.shell.Ui.mono(11f, java.awt.Font.PLAIN);
                    int x = 10 + nm.stringWidth(name) + 8;
                    // Left out where it would not fit, rather than cut.
                    if (x + g2.getFontMetrics(scaleFont).stringWidth(scale) <= getWidth() - 8) {
                        g2.setFont(scaleFont);
                        g2.setColor(new Color(0x7f8a9c));
                        g2.drawString(scale, x, baseline);
                    }
                }
                finally {
                    g2.dispose();
                }
            }
        };
        /**
         * The corners in the card's colour, over the view: it repaints itself for every frame and
         * would paint square ones. The slot does not draw optimised, so the mask is painted again
         * with the view.
         */
        private final javax.swing.JComponent corners = new javax.swing.JComponent() {
            @Override
            public boolean contains(int x, int y) {
                return false;
            }

            @Override
            protected void paintComponent(java.awt.Graphics g) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                try {
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    float arc = 2 * org.openpnp.gui.shell.Tokens.R_MD;
                    java.awt.geom.Area area = new java.awt.geom.Area(new java.awt.Rectangle(0, 0, getWidth(), getHeight()));
                    area.subtract(new java.awt.geom.Area(new java.awt.geom.RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc)));
                    java.awt.Container host = Slot.this.getParent();
                    while (host != null && !host.isOpaque()) {
                        host = host.getParent();
                    }
                    g2.setColor(host != null ? host.getBackground() : org.openpnp.gui.shell.Ui.surface());
                    g2.fill(area);
                }
                finally {
                    g2.dispose();
                }
            }
        };

        Slot(CameraView view) {
            this.view = view;
            setLayout(null);
            setOpaque(false);
            add(corners);
            add(caption);
            add(view);
        }

        @Override
        public boolean isOptimizedDrawingEnabled() {
            return false;
        }

        @Override
        public void doLayout() {
            int w = getWidth(), h = getHeight();
            corners.setBounds(0, 0, w, h);
            caption.setBounds(0, 0, w, CameraArrangement.CAPTION);
            view.setBounds(0, CameraArrangement.CAPTION, w, Math.max(0, h - CameraArrangement.CAPTION));
        }
    }

    public void addCamera(Camera camera) {
        CameraView cameraView = new CameraView();
        cameraView.setStageMode(stageMode);
        if (stageMode) {
            cameraView.setBackground(org.openpnp.gui.shell.Ui.cameraBg());
        }
        cameraView.setCamera(camera);
        cameraViews.put(camera, cameraView);
        camerasCombo.addItem(new CameraItem(camera));
        if (cameraViews.size() == 1) {
            // First camera being added, so select it
            camerasCombo.setSelectedIndex(1);
        }
        else if (cameraViews.size() == 2) {
            // Otherwise this is the second camera so mix in the
            // show all item.
            camerasCombo.insertItemAt(SHOW_ALL_ITEM_H, 1);
            camerasCombo.insertItemAt(SHOW_ALL_ITEM_V, 2);
        }
        if (camera instanceof AbstractCamera) {
            ((AbstractCamera) camera).addPropertyChangeListener("shownInMultiCameraView", 
                    new PropertyChangeListener() {
                        
                        @Override
                        public void propertyChange(PropertyChangeEvent evt) {
                            if (evt.getOldValue() == null 
                                    || ! evt.getOldValue().equals(evt.getNewValue())) {
                                SwingUtilities.invokeLater(()->relayoutPanel());
                            }
                        }
                    });
        }
        relayoutPanel();
    }
    
    public void removeCamera(Camera camera) {
        CameraView cameraView = cameraViews.remove(camera);
        if (cameraView == null) {
            return;
        }
        for (int i = 0; i < camerasCombo.getItemCount(); i++) {
            Object o = camerasCombo.getItemAt(i);
            if (o instanceof CameraItem) {
                CameraItem cameraItem = (CameraItem) o;
                if (cameraItem.getCamera() == camera) {
                    camerasCombo.removeItemAt(i);
                    break;
                }
            }
        }
        relayoutPanel();
    }
    
    private void createUi() {
        camerasPanel = new JPanel();

        // A camera's own text names its head too, which a pill has no room for and the view
        // below it shows anyway.
        camerasCombo.setLabeller(item -> item instanceof CameraItem
                ? ((CameraItem) item).getCamera().getName()
                : Translations.getString("CameraPanel.Show." //$NON-NLS-1$
                        + String.valueOf(item).replace(" ", ""))); //$NON-NLS-1$
        camerasCombo.addActionListener(cameraSelectedAction);

        setLayout(new BorderLayout());

        camerasCombo.addItem(SHOW_NONE_ITEM);

        // The selector is placed by whoever shows this panel. On the main window it floats over
        // the image rather than taking a strip above it.
        add(camerasPanel);
    }

    /**
     * Make sure the given Camera is visible in the UI. If All Cameras is selected we do nothing,
     * otherwise we select the specified Camera.
     * 
     * @param camera
     * @return The CameraView.
     */
    public CameraView ensureCameraVisible(Camera camera) {
        if (camerasCombo.getSelectedItem().equals(SHOW_ALL_ITEM_H) || camerasCombo.getSelectedItem().equals(SHOW_ALL_ITEM_V)) {
            return getCameraView(camera);
        }
        return setSelectedCamera(camera);
    }

    public CameraView setSelectedCamera(Camera camera) {
        if (selectedCameraView != null && selectedCameraView.getCamera() == camera) {
            return selectedCameraView;
        }
        for (int i = 0; i < camerasCombo.getItemCount(); i++) {
            Object o = camerasCombo.getItemAt(i);
            if (o instanceof CameraItem) {
                Camera c = ((CameraItem) o).getCamera();
                if (c == camera) {
                    camerasCombo.setSelectedIndex(i);
                    return selectedCameraView;
                }
            }
        }
        return null;
    }

    /**
     * The control that chooses which camera is shown. It is not added to this panel: the main
     * window floats it over the image, and a wizard that only ever shows one camera leaves it out.
     */
    public PillBar getCameraSelector() {
        return camerasCombo;
    }

    public CameraView getCameraView(Camera camera) {
        return cameraViews.get(camera);
    }

    private final java.util.List<Runnable> selectionListeners = new java.util.ArrayList<>();

    /** Called on the event thread whenever which camera is shown changes. */
    public void addSelectionListener(Runnable listener) {
        selectionListeners.add(listener);
    }

    /** The one view on show, or null while none or all of the cameras are. */
    public CameraView getSelectedCameraView() {
        return selectedCameraView;
    }

    private void relayoutPanel() {
        selectedCameraView = null;
        for (CameraView view : shown) {
            view.removePropertyChangeListener(CameraView.PROPERTY_IMAGE_ASPECT, aspectListener);
        }
        shown.clear();
        camerasPanel.removeAll();
        if (stageMode) {
            relayoutStage();
            return;
        }
        if (camerasCombo.getSelectedItem().equals(SHOW_NONE_ITEM)) {
            camerasPanel.setLayout(new BorderLayout());
            JPanel panel = new JPanel();
            panel.setBackground(Color.black);
            camerasPanel.add(panel);
            selectedCameraView = null;
        }
        else if (camerasCombo.getSelectedItem().equals(SHOW_ALL_ITEM_H) || camerasCombo.getSelectedItem().equals(SHOW_ALL_ITEM_V)) {
            LinkedHashMap<Camera, CameraView> cameraViews = new LinkedHashMap<>();
            for (Entry<Camera, CameraView> entry : this.cameraViews.entrySet()) {
                if (entry.getKey().isShownInMultiCameraView()) {
                    cameraViews.put(entry.getKey(), entry.getValue());
                }
            }

            int rows = (int) Math.ceil(Math.sqrt(cameraViews.size()));
            if (rows == 0) {
                rows = 1;
            }
            if (camerasCombo.getSelectedItem().equals(SHOW_ALL_ITEM_H)) {
                camerasPanel.setLayout(new GridLayout(rows, 0, 1, 1));
            }
            else {
                camerasPanel.setLayout(new GridLayout(0, rows, 1, 1));
            }

            for (CameraView cameraView : cameraViews.values()) {
                cameraView.setShowName(true);
                camerasPanel.add(cameraView);
                if (cameraViews.size() == 1) {
                    selectedCameraView = cameraView;
                }
            }
            if (cameraViews.size() > 2) {
                for (int i = 0; i < (rows * rows) - cameraViews.size(); i++) {
                    JPanel panel = new JPanel();
                    panel.setBackground(Color.black);
                    camerasPanel.add(panel);
                }
            }
            selectedCameraView = null;
        }
        else {
            camerasPanel.setLayout(new BorderLayout());
            Camera camera = ((CameraItem) camerasCombo.getSelectedItem()).getCamera();
            CameraView cameraView = getCameraView(camera);
            cameraView.setShowName(false);
            camerasPanel.add(cameraView);

            selectedCameraView = cameraView;
        }
        revalidate();
        repaint();
        for (Runnable listener : new java.util.ArrayList<>(selectionListeners)) {
            listener.run();
        }
    }

    /** The main window's arrangement: the pictures whole, each under its bar, and nothing else. */
    private void relayoutStage() {
        Object item = camerasCombo.getSelectedItem();
        camerasPanel.setOpaque(false);
        camerasPanel.setLayout(stageLayout);
        if (SHOW_ALL_ITEM_H.equals(item) || SHOW_ALL_ITEM_V.equals(item)) {
            for (Entry<Camera, CameraView> entry : cameraViews.entrySet()) {
                if (entry.getKey().isShownInMultiCameraView()) {
                    shown.add(entry.getValue());
                }
            }
            if (shown.size() == 1) {
                selectedCameraView = shown.get(0);
            }
        }
        else if (item instanceof CameraItem) {
            CameraView view = getCameraView(((CameraItem) item).getCamera());
            if (view != null) {
                shown.add(view);
                selectedCameraView = view;
            }
        }
        for (CameraView view : shown) {
            view.setShowName(false);
            view.addPropertyChangeListener(CameraView.PROPERTY_IMAGE_ASPECT, aspectListener);
            camerasPanel.add(new Slot(view));
        }
        fitChanged();
        for (Runnable listener : new java.util.ArrayList<>(selectionListeners)) {
            listener.run();
        }
    }

    private AbstractAction cameraSelectedAction = new AbstractAction("") {
        @Override
        public void actionPerformed(ActionEvent ev) {
            relayoutPanel();
        }
    };
}
