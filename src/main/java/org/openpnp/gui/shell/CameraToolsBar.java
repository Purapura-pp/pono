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

package org.openpnp.gui.shell;

import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Supplier;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.openpnp.Translations;
import org.openpnp.gui.components.CameraPanel;
import org.openpnp.gui.components.CameraView;
import org.openpnp.gui.components.reticle.CrosshairReticle;
import org.openpnp.gui.components.reticle.GridReticle;
import org.openpnp.gui.components.reticle.OutlineReticle;
import org.openpnp.gui.components.reticle.Reticle;
import org.openpnp.gui.components.reticle.RulerReticle;
import org.openpnp.gui.components.reticle.SceneReticle;
import org.openpnp.gui.machinesettings.Backups;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.model.Configuration;
import org.openpnp.model.Footprint;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Machine;
import org.openpnp.spi.base.AbstractActuator;
import org.openpnp.util.UiUtils;
import org.pmw.tinylog.Logger;

import com.formdev.flatlaf.FlatClientProperties;

/**
 * The tools in the top right corner of the image, as two of the stylesheet's glass cards: the
 * crosshair, grid, ruler and package outline switches, which can be on together, and the light
 * with its brightness, the zoom, a screenshot and full screen.
 * <p>
 * The reticles were only reachable through the right-click menu, which also holds their colours
 * and units, and only one could be drawn at a time; the menu stays for those. The light switch
 * was drawn by the camera view itself in the corner these cards cover, as a circle of rays that
 * looked like a busy indicator. The zoom readout follows the mouse wheel and clicking it goes
 * back to the whole image.
 */
@SuppressWarnings("serial")
public class CameraToolsBar extends JPanel {
    /** The keys the switches draw under, beside the default reticle the menu chooses. */
    static final String GRID = "Pono.grid"; //$NON-NLS-1$
    static final String RULER = "Pono.ruler"; //$NON-NLS-1$
    static final String OUTLINE = "Pono.outline"; //$NON-NLS-1$

    private final CameraPanel cameraPanel;
    private final Configuration configuration;
    private final JToggleButton crosshair = new Ui.ToggleButton(null, Ui.icon("crosshair", 16)); //$NON-NLS-1$
    private final JToggleButton grid = new Ui.ToggleButton(null, Ui.icon("grid", 16)); //$NON-NLS-1$
    private final JToggleButton ruler = new Ui.ToggleButton(null, Ui.icon("ruler", 16)); //$NON-NLS-1$
    private final JToggleButton outline = new Ui.ToggleButton(null, Ui.icon("footprint", 16)); //$NON-NLS-1$
    private final JToggleButton light = new Ui.ToggleButton(null, Ui.iconSm("zap")); //$NON-NLS-1$
    private final JButton zoom = new Ui.Button("100%", Ui.iconSm("search")); //$NON-NLS-1$ //$NON-NLS-2$
    private final Reticle outlineReticle;
    private CameraView followed;
    private final PropertyChangeListener zoomListener = e -> showZoom();
    /** The light's actuator says what it is doing when it has done it; asked twice a second. */
    private final Timer lightFollower = new Timer(500, e -> showLight());
    private final JSlider brightness = new JSlider(1, 100, 100);
    private final JLabel brightnessValue = Ui.mono("100%", 11f); //$NON-NLS-1$
    /** While the slider is dragged its brightness goes to the light at most this often. */
    private final Timer brightnessSender = new Timer(150, e -> sendBrightness(false));
    /** Set while the slider is moved to what the light has, which is not an edit. */
    private boolean showingBrightness;

    /**
     * @param outline The footprint of what is selected, and its name, for the outline switch.
     */
    public CameraToolsBar(Configuration configuration, CameraPanel cameraPanel,
            Runnable toggleFullScreen, Supplier<Footprint> outline, Supplier<String> outlineLabel) {
        this.configuration = configuration;
        this.cameraPanel = cameraPanel;
        this.outlineReticle = new OutlineReticle(outline, outlineLabel);
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

        OverlayCard reticles = OverlayCard.strip();
        for (JToggleButton toggle : new JToggleButton[] { crosshair, grid, ruler, this.outline }) {
            Ui.iconPill(toggle);
            reticles.add(toggle);
        }
        crosshair.setToolTipText(Translations.getString("CameraTools.Crosshair")); //$NON-NLS-1$
        grid.setToolTipText(Translations.getString("CameraTools.Grid")); //$NON-NLS-1$
        ruler.setToolTipText(Translations.getString("CameraTools.Ruler")); //$NON-NLS-1$
        this.outline.setToolTipText(Translations.getString("CameraTools.Outline")); //$NON-NLS-1$
        crosshair.addActionListener(e -> {
            if (followed != null) {
                followed.setDefaultReticle(crosshair.isSelected() ? new SceneReticle() : null);
            }
            showReticles();
        });
        grid.addActionListener(e -> set(GRID, grid.isSelected() ? new GridReticle() : null));
        ruler.addActionListener(e -> set(RULER, ruler.isSelected() ? new RulerReticle() : null));
        this.outline.addActionListener(e -> set(OUTLINE, this.outline.isSelected() ? outlineReticle : null));
        add(reticles);
        add(Box.createHorizontalStrut(OverlayAnchorLayout.GAP));

        OverlayCard view = OverlayCard.strip();
        Ui.iconPill(light);
        light.setToolTipText(Translations.getString("CameraTools.Light")); //$NON-NLS-1$
        light.addActionListener(e -> {
            if (followed != null && followed.hasLight()) {
                followed.toggleLight();
            }
            showLight();
        });
        view.add(light);
        brightness.putClientProperty(FlatClientProperties.STYLE,
                "trackWidth: 3; thumbSize: 12,12; trackValueColor: $Pono.accent; trackColor: $Pono.surface3; " //$NON-NLS-1$
                        + "thumbColor: #ffffff; thumbBorderColor: $Pono.accent; focusedColor: null; " //$NON-NLS-1$
                        + "hoverThumbColor: #ffffff; pressedThumbColor: #ffffff"); //$NON-NLS-1$
        brightness.setOpaque(false);
        brightness.setFocusable(false);
        brightness.setPreferredSize(new Dimension(76, 26));
        Ui.whyDisabled(brightness, this::brightnessFixed);
        brightnessValue.setForeground(Ui.text2());
        brightnessValue.setHorizontalAlignment(SwingConstants.RIGHT);
        brightnessValue.setPreferredSize(new Dimension(30, 26));
        brightnessSender.setRepeats(false);
        brightness.addChangeListener(e -> {
            brightnessValue.setText(brightness.getValue() + "%"); //$NON-NLS-1$
            if (showingBrightness) {
                return;
            }
            if (!brightness.getValueIsAdjusting()) {
                brightnessSender.stop();
                sendBrightness(true);
            }
            else if (!brightnessSender.isRunning()) {
                brightnessSender.start();
            }
        });
        view.add(brightness);
        view.add(brightnessValue);
        Ui.pill(zoom);
        zoom.setToolTipText(Translations.getString("CameraTools.Zoom")); //$NON-NLS-1$
        zoom.addActionListener(e -> {
            if (followed != null) {
                followed.setZoom(1.0);
            }
        });
        view.add(zoom);
        JButton capture = new Ui.Button(null, Ui.iconSm("capture")); //$NON-NLS-1$
        Ui.iconPill(capture);
        capture.setToolTipText(Translations.getString("CameraTools.Screenshot")); //$NON-NLS-1$
        capture.addActionListener(e -> UiUtils.messageBoxOnException(this::screenshot));
        view.add(capture);
        JButton full = new Ui.Button(null, Ui.iconSm("maximize")); //$NON-NLS-1$
        Ui.iconPill(full);
        full.setToolTipText(Translations.getString("CameraTools.FullScreen")); //$NON-NLS-1$
        full.addActionListener(e -> toggleFullScreen.run());
        view.add(full);
        add(view);

        cameraPanel.addSelectionListener(this::follow);
        follow();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        lightFollower.start();
    }

    @Override
    public void removeNotify() {
        lightFollower.stop();
        super.removeNotify();
    }

    /** Point the switches at whichever view is on show. */
    private void follow() {
        if (followed != null) {
            followed.removePropertyChangeListener("zoom", zoomListener); //$NON-NLS-1$
        }
        followed = cameraPanel.getSelectedCameraView();
        boolean single = followed != null;
        for (JToggleButton toggle : new JToggleButton[] { crosshair, grid, ruler, outline }) {
            toggle.setEnabled(single);
        }
        zoom.setEnabled(single);
        if (followed != null) {
            followed.addPropertyChangeListener("zoom", zoomListener); //$NON-NLS-1$
        }
        showReticles();
        showZoom();
        showLight();
    }

    private void set(String key, Reticle reticle) {
        if (followed != null) {
            followed.setReticle(key, reticle);
            followed.repaint();
        }
        showReticles();
    }

    private void showReticles() {
        Reticle chosen = followed == null ? null : followed.getDefaultReticle();
        // A grid is a crosshair with more lines; the menu's grid lights the grid switch.
        crosshair.setSelected(chosen instanceof CrosshairReticle && !(chosen instanceof GridReticle));
        grid.setSelected(followed != null && (followed.getReticle(GRID) != null || chosen instanceof GridReticle));
        ruler.setSelected(followed != null && (followed.getReticle(RULER) != null || chosen instanceof RulerReticle));
        outline.setSelected(followed != null && followed.getReticle(OUTLINE) != null);
    }

    private void showLight() {
        boolean has = followed != null && followed.hasLight();
        light.setEnabled(has);
        light.setSelected(has && followed.isLightOn());
        light.setToolTipText(Translations.getString(has ? "CameraTools.Light" //$NON-NLS-1$
                : "CameraTools.Light.None")); //$NON-NLS-1$
        showBrightness(lightActuator());
    }

    /** The light actuator of the camera on show, or null. */
    private Actuator lightActuator() {
        Camera camera = followed == null ? null : followed.getCamera();
        return camera == null ? null : camera.getLightActuator();
    }

    /**
     * The slider follows the light's ON value, except while it is dragged or a value is on its
     * way. A switched light shows the brightness its command has, where the command says.
     */
    private void showBrightness(Actuator actuator) {
        boolean has = actuator != null;
        if (brightness.isVisible() != has) {
            brightness.setVisible(has);
            brightnessValue.setVisible(has);
            revalidate();
        }
        if (!has || brightness.getValueIsAdjusting() || brightnessSender.isRunning()) {
            return;
        }
        boolean adjustable = LightBrightness.adjustable(actuator);
        LightBrightness.Conversion conversion = adjustable ? null : LightBrightness.conversion(actuator);
        boolean enabled = adjustable || conversion != null;
        double fraction = adjustable ? LightBrightness.fraction(actuator)
                : conversion != null ? conversion.on / AbstractActuator.DEFAULT_FULL_SCALE : 1;
        showingBrightness = true;
        try {
            brightness.setValue((int) Math.max(1, Math.round(fraction * 100)));
        }
        finally {
            showingBrightness = false;
        }
        brightness.setEnabled(enabled);
        brightnessValue.setEnabled(enabled);
        brightnessValue.setText(enabled ? brightness.getValue() + "%" : "\u2014"); //$NON-NLS-1$ //$NON-NLS-2$
        String toolTip = enabled
                ? String.format(Translations.getString("CameraTools.Brightness"), brightness.getValue()) //$NON-NLS-1$
                : brightnessFixed();
        brightness.setToolTipText(toolTip);
        brightnessValue.setToolTipText(toolTip);
    }

    /** Why the slider is greyed: a switched light whose command does not say its brightness. */
    private String brightnessFixed() {
        Actuator actuator = lightActuator();
        return actuator == null ? null
                : String.format(Translations.getString("CameraTools.Brightness.Fixed"), actuator.getName()); //$NON-NLS-1$
    }

    /**
     * The slider's brightness to the light: its ON value, saved with the configuration, and the
     * light switched on at it when the machine can take a command. A switched light is made
     * numeric first, once the drag has ended and the user has agreed.
     */
    private void sendBrightness(boolean released) {
        Actuator actuator = lightActuator();
        if (actuator == null) {
            return;
        }
        // Read before asking: while the question is open the slider follows the light again.
        double fraction = brightness.getValue() / 100.0;
        if (!LightBrightness.adjustable(actuator)) {
            LightBrightness.Conversion conversion = released ? LightBrightness.conversion(actuator) : null;
            if (conversion == null || !convert(actuator, conversion)) {
                if (released) {
                    showLight();
                }
                return;
            }
        }
        double value = LightBrightness.value(actuator, fraction);
        ((AbstractActuator) actuator).setDefaultOnDouble(value);
        configuration.setDirty(true);
        Machine machine = configuration.getMachine();
        if (machine != null && machine.isEnabled() && !machine.isBusy()) {
            UiUtils.submitUiMachineTask(() -> actuator.actuate(value));
        }
        if (released) {
            showLight();
        }
    }

    /** Asks, backs machine.xml up and makes the switched light numeric; whether it was done. */
    private boolean convert(Actuator actuator, LightBrightness.Conversion conversion) {
        String title = String.format(Translations.getString("CameraTools.Brightness.Convert.Title"), //$NON-NLS-1$
                actuator.getName());
        int chosen = Dialogs.ask(SwingUtilities.getWindowAncestor(this), Dialogs.Tone.Warn, "zap", title, //$NON-NLS-1$
                String.format(Translations.getString("CameraTools.Brightness.Convert.What"), //$NON-NLS-1$
                        actuator.getName(), LightBrightness.format(conversion.on), conversion.command),
                Translations.getString("CameraTools.Brightness.Convert.More"), //$NON-NLS-1$
                Dialogs.Choice.primary(Translations.getString("CameraTools.Brightness.Convert.Action"))); //$NON-NLS-1$
        if (chosen != 0) {
            return false;
        }
        try {
            Backups.backup(configuration, "light"); //$NON-NLS-1$
        }
        catch (Exception e) {
            MessageBoxes.errorBox(this, title, e);
            return false;
        }
        LightBrightness.convert(actuator, conversion);
        configuration.setDirty(true);
        return true;
    }

    private void showZoom() {
        double factor = followed == null ? 1.0 : followed.getZoom();
        zoom.setText(Math.round(factor * 100) + "%"); //$NON-NLS-1$
    }

    /**
     * Save what the view is showing - image, reticle and all - as a PNG under the configuration
     * directory, and open the folder it went to.
     */
    private void screenshot() throws Exception {
        if (followed == null || followed.getWidth() <= 0) {
            throw new Exception(Translations.getString("CameraTools.Screenshot.NoView")); //$NON-NLS-1$
        }
        BufferedImage image = new BufferedImage(followed.getWidth(), followed.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        try {
            followed.paint(g);
        }
        finally {
            g.dispose();
        }
        File directory = new File(configuration.getConfigurationDirectory(), "screenshots"); //$NON-NLS-1$
        directory.mkdirs();
        String name = followed.getCamera() == null ? "camera" : followed.getCamera().getName(); //$NON-NLS-1$
        File file = new File(directory, name + "-" //$NON-NLS-1$
                + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".png"); //$NON-NLS-1$ //$NON-NLS-2$
        ImageIO.write(image, "png", file); //$NON-NLS-1$
        Logger.info("Saved a screenshot of {} to {}.", name, file); //$NON-NLS-1$
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(directory);
        }
    }
}
