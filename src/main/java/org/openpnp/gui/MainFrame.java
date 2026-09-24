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

package org.openpnp.gui;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.security.InvalidParameterException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.undo.UndoManager;

import org.openpnp.Main;
import org.openpnp.Translations;
import org.openpnp.gui.components.CameraPanel;
import org.openpnp.gui.components.ThemeDialog;
import org.openpnp.gui.importer.BoardImporter;
import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.gui.support.HeadCellValue;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.LengthCellValue;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.gui.support.OSXAdapter;
import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.gui.support.RotationCellValue;
import org.openpnp.gui.support.SwingUserInteraction;
import org.openpnp.gui.support.CameraItem;
import org.openpnp.spi.Camera;
import javax.swing.BorderFactory;
import javax.swing.SwingConstants;
import java.awt.GridBagLayout;
import java.awt.Container;
import java.awt.Insets;
import java.awt.Window;
import javax.swing.JScrollPane;
import org.openpnp.ConfigurationListener;
import org.openpnp.gui.shell.Hotkeys;
import org.openpnp.model.SaveCancelledException;
import org.openpnp.spi.Machine;
import org.openpnp.spi.MachineListener;
import org.openpnp.gui.shell.CameraStage;
import org.openpnp.gui.shell.CameraToolsBar;
import org.openpnp.gui.shell.PillBar;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.CommandPalette;
import org.openpnp.gui.shell.DroPanel;
import org.openpnp.gui.shell.InspectorPanel;
import org.openpnp.gui.shell.JogCard;
import org.openpnp.gui.shell.PropertySheetPresenter.Result;
import org.openpnp.gui.shell.NavigationRail;
import org.openpnp.gui.shell.OverlayAnchorLayout.Anchor;
import org.openpnp.gui.shell.OverlayCard;
import org.openpnp.gui.shell.StatusBarPanel;
import org.openpnp.gui.shell.TopBarPanel;
import org.openpnp.model.Board;
import org.openpnp.model.BoardLocation;
import org.openpnp.model.Configuration;
import org.openpnp.model.Configuration.TablesLinked;
import org.openpnp.model.LengthUnit;
import org.openpnp.scripting.ScriptFileWatcher;
import org.openpnp.util.UiUtils;
import org.pmw.tinylog.Logger;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;

/**
 * The main window of the application.
 */
@SuppressWarnings("serial")
public class MainFrame extends JFrame {
    private static final String PREF_WINDOW_X = "MainFrame.windowX"; //$NON-NLS-1$
    private static final int PREF_WINDOW_X_DEF = 0;
    private static final String PREF_WINDOW_Y = "MainFrame.windowY"; //$NON-NLS-1$
    private static final int PREF_WINDOW_Y_DEF = 0;
    private static final String PREF_WINDOW_WIDTH = "MainFrame.windowWidth"; //$NON-NLS-1$
    private static final int PREF_WINDOW_WIDTH_DEF = 1024;
    private static final String PREF_WINDOW_HEIGHT = "MainFrame.windowHeight"; //$NON-NLS-1$
    private static final int PREF_WINDOW_HEIGHT_DEF = 768;
    private static final String PREF_WINDOW_STYLE_MULTIPLE = "MainFrame.windowStyleMultiple"; //$NON-NLS-1$
    private static final boolean PREF_WINDOW_STYLE_MULTIPLE_DEF = false;
    private static final String PREF_INSPECTOR_WIDTH = "MainFrame.inspectorWidth"; //$NON-NLS-1$

    private static final String PREF_CAMERA_WINDOW_X = "CameraFrame.windowX"; //$NON-NLS-1$
    private static final int PREF_CAMERA_WINDOW_X_DEF = 0;
    private static final String PREF_CAMERA_WINDOW_Y = "CameraFrame.windowY"; //$NON-NLS-1$
    private static final int PREF_CAMERA_WINDOW_Y_DEF = 0;
    private static final String PREF_CAMERA_WINDOW_WIDTH = "CameraFrame.windowWidth"; //$NON-NLS-1$
    private static final int PREF_CAMERA_WINDOW_WIDTH_DEF = 800;
    private static final String PREF_CAMERA_WINDOW_HEIGHT = "CameraFrame.windowHeight"; //$NON-NLS-1$
    private static final int PREF_CAMERA_WINDOW_HEIGHT_DEF = 600;

    private static final int MINIMUM_WINDOW_SIZE = 50;
    private static final int MIN_WIDTH = 1024;
    private static final int MIN_HEIGHT = 640;

    private final Configuration configuration;

    private static MainFrame mainFrame;

    private MachineControlsPanel machineControlsPanel;
    private PanelsPanel panelsPanel;
    private BoardsPanel boardsPanel;
    private PartsPanel partsPanel;
    private PackagesPanel packagesPanel;
    private FeedersPanel feedersPanel;
    private JobPanel jobPanel;
    private CameraPanel cameraPanel;
    private JPanel panelCameraAndInstructions;
    private JPanel panelMachine;
    private MachineSetupPanel machineSetupPanel;
    private VisionSettingsPanel visionSettingsPanel;
    private JDialog frameCamera;
    private Map<KeyStroke, Action> hotkeyActionMap;
    /** The "/" that moved the focus to a filter, whose typed character is still to come. */
    private boolean slashTaken;
    private AbstractConfigurationWizard wizardWithActiveProcess = null;
    private UndoManager undoManager = new UndoManager();
    private boolean windowStyleMultiple;

    public static MainFrame get() {
        return mainFrame;
    }
    
    /**
     * @return the wizardWithActiveProcess
     */
    public synchronized AbstractConfigurationWizard getWizardWithActiveProcess() {
        return wizardWithActiveProcess;
    }

    /**
     * @param wizardWithActiveProcess the wizardWithActiveProcess to set
     */
    public synchronized void setWizardWithActiveProcess(AbstractConfigurationWizard wizardWithActiveProcess) {
        if (this.wizardWithActiveProcess == null) {
            if (wizardWithActiveProcess.getId() != null) {
                this.wizardWithActiveProcess = wizardWithActiveProcess;
            }
            else {
                throw new InvalidParameterException("Wizard does not have a valid Id.");
            }
        }
        else {
            throw new IllegalStateException(String.format("Another wizard (%s - %s) already has an active process, it must complete or be cancelled before this wizard can start another process.", this.wizardWithActiveProcess.getName(), ((PropertySheetWizardAdapter) (this.wizardWithActiveProcess.getWizardContainer())).getPropertySheetTitle()));
        }
    }
    
    public synchronized void clearWizardWithActiveProcess(AbstractConfigurationWizard wizardWithActiveProcess) {
        if (this.wizardWithActiveProcess == wizardWithActiveProcess) {
            this.wizardWithActiveProcess = null;
        }
    }

    public UndoManager getUndoManager() {
        return undoManager;
    }

    public MachineControlsPanel getMachineControls() {
        return machineControlsPanel;
    }

    public PanelsPanel getPanelsTab() {
        return panelsPanel;
    }

    public BoardsPanel getBoardsTab() {
        return boardsPanel;
    }

    public PartsPanel getPartsTab() {
        return partsPanel;
    }

    public PackagesPanel getPackagesTab() {
        return packagesPanel;
    }

    public VisionSettingsPanel getVisionSettingsTab() {
        return visionSettingsPanel;
    }

    public FeedersPanel getFeedersTab() {
        return feedersPanel;
    }

    public JobPanel getJobTab() {
        return jobPanel;
    }

    public CameraPanel getCameraViews() {
        return cameraPanel;
    }

    /**
     * The element tree. It is the machine settings page's advanced topic, and showing it with
     * {@link #showTab(Component)} shows that page and topic.
     */
    public MachineSetupPanel getMachineSetupTab() {
        return machineSetupPanel;
    }

    public org.openpnp.gui.machinesettings.MachineSettingsPanel getMachineSettings() {
        return machineSettingsPanel;
    }

    /** Goes to the machine settings page at a topic: where a hint about the machine's definition leads. */
    public void showMachineSettings(String topic) {
        if (machineSettingsPanel == null) {
            return;
        }
        showTab(machineSettingsPanel);
        machineSettingsPanel.showTopic(topic);
    }

    private org.openpnp.gui.machinesettings.MachineSettingsPanel machineSettingsPanel;

    public TopBarPanel getTopBar() {
        return topBarPanel;
    }

    private JPanel contentPane;
    private NavigationRail navigationRail;
    private InspectorPanel inspectorPanel;
    private JogCard jogCard;
    private JSplitPane splitPaneInspector;
    private CameraStage cameraStage;
    private OverlayCard instructionsCard;
    private DroPanel droPanel;
    private JSplitPane splitPaneMachineAndTabs;
    private JLabel lblInstructionsTitle;
    private JPanel panelInstructions;
    private JPanel panelInstructionActions;
    private JPanel panel_1;
    private JButton btnInstructionsNext;
    private JButton btnInstructionsCancel;
    private JTextPane lblInstructions;
    private JPanel panel_2;
    private ScheduledExecutorService scheduledExecutor;
    private JMenuBar menuBar;
    private JMenu mnImport;
    private JMenu mnScripts;
    private JMenu mnCommands;

    public NavigationRail getNavigation() {
        return navigationRail;
    }

    /**
     * The properties column down the right hand side. Panels hand it whatever their table has
     * selected rather than showing the property sheets themselves.
     */
    public InspectorPanel getInspector() {
        return inspectorPanel;
    }

    private JLabel unitsPerPixelChip;
    private OverlayCard unitsStrip;

    /** {@code 1 px = 0.0209 mm}, for the camera on show; blank while none or all are. */
    private void showUnitsPerPixel() {
        org.openpnp.gui.components.CameraView view = cameraPanel.getSelectedCameraView();
        Camera camera = view == null ? null : view.getCamera();
        if (camera == null || camera.getUnitsPerPixel() == null) {
            unitsPerPixelChip.setText(""); //$NON-NLS-1$
            unitsPerPixelChip.getParent().setVisible(false);
        }
        else {
            org.openpnp.model.Location upp = camera.getUnitsPerPixel()
                    .convertToUnits(configuration.getSystemUnits());
            unitsPerPixelChip.setText(String.format(java.util.Locale.US, "1 px = %.4f %s", //$NON-NLS-1$
                    Math.abs(upp.getX()), configuration.getSystemUnits().getShortName()));
            unitsPerPixelChip.getParent().setVisible(true);
        }
        cameraStage.revalidate();
    }

    /** The placement selected on the job page, whose package the outline switch draws. */
    private org.openpnp.model.Placement selectedPlacement() {
        if (jobPanel == null || jobPanel.getJobPlacementsPanel() == null) {
            return null;
        }
        return jobPanel.getJobPlacementsPanel().getSelection();
    }

    private org.openpnp.model.Footprint selectedFootprint() {
        org.openpnp.model.Placement placement = selectedPlacement();
        if (placement == null || placement.getPart() == null || placement.getPart().getPackage() == null) {
            return null;
        }
        return placement.getPart().getPackage().getFootprint();
    }

    /** "R12 · 0603", as the mockups' scene names the outline it draws. */
    private String selectedFootprintLabel() {
        org.openpnp.model.Placement placement = selectedPlacement();
        if (placement == null || placement.getPart() == null || placement.getPart().getPackage() == null) {
            return null;
        }
        return placement.getId() + " \u00b7 " + placement.getPart().getPackage().getId(); //$NON-NLS-1$
    }

    private LogPanel logPanel;

    /** The log page, where an error and what led up to it are, from the error dialog. */
    public void showLog() {
        if (logPanel != null) {
            navigationRail.setSelectedComponent(logPanel);
            toFront();
        }
    }

    private CalibrationPanel calibrationPanel;

    public CalibrationPanel getCalibrationTab() {
        return calibrationPanel;
    }

    /**
     * Goes to the calibration page with the step selected: the one of the kind for the subject,
     * or the first outstanding one when no kind is given.
     */
    public void showCalibrationStep(org.openpnp.model.CalibrationStep kind, Object subject) {
        if (calibrationPanel == null) {
            return;
        }
        showTab(calibrationPanel);
        calibrationPanel.show(kind, subject);
    }

    private boolean dockMaximised;
    private int dividerBeforeMaximise;

    /** Give the tables the whole middle of the window and back: the image folds away above them. */
    public void toggleDockMaximised() {
        if (!dockMaximised) {
            dividerBeforeMaximise = splitPaneMachineAndTabs.getDividerLocation();
            splitPaneMachineAndTabs.setDividerLocation(0);
        }
        else {
            splitPaneMachineAndTabs.setDividerLocation(dividerBeforeMaximise);
        }
        dockMaximised = !dockMaximised;
    }

    private boolean cameraFullScreen;
    private int dividerBeforeFullScreen;
    private boolean inspectorBeforeFullScreen;

    /**
     * Give the image the whole middle of the window and back again: the tables below it and the
     * properties column fold away, and the divider positions come back with them.
     */
    private void toggleCameraFullScreen() {
        if (!cameraFullScreen) {
            dividerBeforeFullScreen = splitPaneMachineAndTabs.getDividerLocation();
            inspectorBeforeFullScreen = inspectorPanel.isCollapsed();
            splitPaneMachineAndTabs.setDividerLocation(1.0);
            inspectorPanel.setCollapsed(true);
        }
        else {
            splitPaneMachineAndTabs.setDividerLocation(dividerBeforeFullScreen);
            inspectorPanel.setCollapsed(inspectorBeforeFullScreen);
        }
        cameraFullScreen = !cameraFullScreen;
    }

    /** Put the properties column at its stored width, or fold it to its sliver. */
    private void applyInspectorWidth() {
        int total = splitPaneInspector.getWidth();
        if (total <= 0) {
            return;
        }
        // At least 320 wide and at most 30 % of the window, whatever was dragged to.
        int width = inspectorPanel.isCollapsed() ? InspectorPanel.COLLAPSED_WIDTH
                : org.openpnp.gui.shell.PageLayouts.inspectorWidth(
                        prefs.getInt(PREF_INSPECTOR_WIDTH, InspectorPanel.PREFERRED_WIDTH), getWidth());
        // The divider is placed from the split's left edge; its right margin is not the column's.
        splitPaneInspector.setDividerLocation(total - splitPaneInspector.getInsets().right - width
                - splitPaneInspector.getDividerSize());
    }

    /**
     * One rail item, labelled from its own short key and explained by the tab title it replaces.
     */
    /** A page that is a dock of tabs itself, which is its card already: it is not put in another. */
    public static final String DOCK_PAGE = "Pono.dockPage"; //$NON-NLS-1$

    private void addNavigation(String key, Icon icon, Component page) {
        // A page is a card of its own below the camera. The job and feeders pages are a dock,
        // which is that card already; the others are put in one.
        Component view = page;
        boolean dock = page instanceof javax.swing.JComponent
                && Boolean.TRUE.equals(((javax.swing.JComponent) page).getClientProperty(DOCK_PAGE));
        if (page != jobPanel && page != feedersPanel && !dock) {
            org.openpnp.gui.shell.RoundedPanel card = org.openpnp.gui.shell.RoundedPanel.card();
            card.setLayout(new BorderLayout());
            card.add(page, BorderLayout.CENTER);
            view = card;
        }
        else if (page instanceof javax.swing.JComponent) {
            ((javax.swing.JComponent) page).setOpaque(false);
        }
        pageKeys.put(page, key);
        navigationRail.addPage(
                Translations.getString("MainFrame.Navigation." + key), //$NON-NLS-1$
                Translations.getString("MainFrame.RightComponent.tabs." + key), //$NON-NLS-1$
                icon, page, view);
    }

    // ---- each page's layout ---------------------------------------------------------------------

    private org.openpnp.gui.shell.PageLayouts pageLayouts;
    /** Each page's name in the layouts, which is its navigation key. */
    private final Map<Component, String> pageKeys = new HashMap<>();
    private org.openpnp.gui.shell.CameraToolsBar cameraToolsBar;
    private OverlayCard cameraModeCard;
    private OverlayCard stripHandle;
    private final Map<org.openpnp.gui.shell.PageLayouts.Camera, javax.swing.JToggleButton> cameraModeButtons =
            new java.util.EnumMap<>(org.openpnp.gui.shell.PageLayouts.Camera.class);
    /** The camera was made large for a wizard's instructions and goes back when they are done. */
    private boolean wizardEnlargedCamera;

    /**
     * Calls back when the user lets go of a split's divider: a drag, as against the window being
     * resized, which moves the divider too. The divider is made again by a theme change, so it is
     * looked for again then.
     */
    private static void onDividerReleased(JSplitPane split, Runnable released) {
        java.awt.event.MouseAdapter listener = new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                SwingUtilities.invokeLater(released);
            }
        };
        Runnable attach = () -> {
            if (split.getUI() instanceof javax.swing.plaf.basic.BasicSplitPaneUI) {
                java.awt.Component divider = ((javax.swing.plaf.basic.BasicSplitPaneUI) split.getUI()).getDivider();
                divider.removeMouseListener(listener);
                divider.addMouseListener(listener);
            }
        };
        attach.run();
        split.addPropertyChangeListener("UI", e -> SwingUtilities.invokeLater(attach)); //$NON-NLS-1$
    }

    /** Lays the window out as the page on show wants it: its camera, its properties column. */
    private void applyPageLayout(Component page) {
        String key = pageKey(page);
        if (key == null || pageLayouts == null) {
            return;
        }
        if (cameraFullScreen) {
            toggleCameraFullScreen();
        }
        if (!windowStyleMultiple) {
            org.openpnp.gui.shell.PageLayouts.Camera camera = pageLayouts.camera(key);
            if (wizardEnlargedCamera || (instructionsCard != null && instructionsCard.isVisible())) {
                camera = org.openpnp.gui.shell.PageLayouts.Camera.Large;
            }
            setCameraMode(key, camera, false);
        }
        applyInspectorPolicy();
    }

    /**
     * Puts the camera at a size on a page: the page's large camera, the strip, or none.
     * 
     * @param store Whether the page is to keep it, as it does when the user asked for it.
     */
    private void setCameraMode(String key, org.openpnp.gui.shell.PageLayouts.Camera camera, boolean store) {
        if (store) {
            pageLayouts.setCamera(key, camera);
        }
        int height = splitPaneMachineAndTabs.getHeight() - splitPaneMachineAndTabs.getDividerSize();
        if (height <= 0) {
            // Not laid out yet: once it is.
            SwingUtilities.invokeLater(() -> {
                if (splitPaneMachineAndTabs.getHeight() > 0) {
                    setCameraMode(key, camera, false);
                }
            });
            showCameraMode(camera);
            return;
        }
        // A large camera takes what the window gains; the strip stays a strip.
        splitPaneMachineAndTabs.setResizeWeight(camera == org.openpnp.gui.shell.PageLayouts.Camera.Large ? 1.0 : 0.0);
        splitPaneMachineAndTabs.setDividerLocation(pageLayouts.dividerFor(key, camera, height));
        showCameraMode(camera);
    }

    /**
     * What the image carries at a size: the strip has the camera choice, its readout in the
     * compact size, the three sizes and the handle to drag it larger; the large camera has the
     * view tools and the machine controls.
     */
    private void showCameraMode(org.openpnp.gui.shell.PageLayouts.Camera camera) {
        // Production mode keeps the camera's own overlays hidden whatever page is switched to.
        if (cameraStage == null || operatorMode) {
            return;
        }
        boolean strip = camera == org.openpnp.gui.shell.PageLayouts.Camera.Small;
        // Hidden, the stage is a sliver under the page: the large camera's cards would be cut there.
        boolean large = camera == org.openpnp.gui.shell.PageLayouts.Camera.Large;
        if (cameraToolsBar != null) {
            cameraToolsBar.setVisible(large);
        }
        if (jogCard != null) {
            jogCard.setVisible(large);
        }
        if (cameraModeCard != null) {
            cameraModeCard.setVisible(strip);
            stripHandle.setVisible(strip);
        }
        if (unitsStrip != null) {
            unitsStrip.setVisible(large);
        }
        if (droPanel != null) {
            droPanel.setForcedCompact(strip);
        }
        for (Map.Entry<org.openpnp.gui.shell.PageLayouts.Camera, javax.swing.JToggleButton> entry : cameraModeButtons.entrySet()) {
            entry.getValue().setSelected(entry.getKey() == camera);
        }
        cameraStage.revalidate();
        cameraStage.repaint();
    }

    /** The View menu's and the strip's choice of camera size for the page on show. */
    private void chooseCameraMode(org.openpnp.gui.shell.PageLayouts.Camera camera) {
        String key = pageKey(navigationRail.getSelectedComponent());
        if (key != null && !windowStyleMultiple) {
            if (cameraFullScreen) {
                toggleCameraFullScreen();
            }
            setCameraMode(key, camera, true);
        }
    }

    /** The strip's "large / small / hidden" pills and its handle, built once with the stage. */
    private void buildCameraModeControls() {
        cameraModeCard = OverlayCard.strip();
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        for (org.openpnp.gui.shell.PageLayouts.Camera camera : org.openpnp.gui.shell.PageLayouts.Camera.values()) {
            javax.swing.JToggleButton pill = new org.openpnp.gui.shell.Ui.ToggleButton(
                    Translations.getString("MainFrame.Camera." + camera.name()), null); //$NON-NLS-1$
            org.openpnp.gui.shell.Ui.pill(pill);
            pill.setToolTipText(Translations.getString("MainFrame.Camera." + camera.name() + ".toolTipText")); //$NON-NLS-1$ //$NON-NLS-2$
            pill.addActionListener(e -> chooseCameraMode(camera));
            group.add(pill);
            cameraModeButtons.put(camera, pill);
            cameraModeCard.add(pill);
        }
        cameraModeCard.setVisible(false);
        cameraStage.anchor(cameraModeCard, Anchor.NorthEast);
        stripHandle = new OverlayCard();
        stripHandle.setLayout(new BorderLayout());
        stripHandle.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        JLabel handle = new JLabel(Translations.getString("MainFrame.Camera.DragToEnlarge"), //$NON-NLS-1$
                org.openpnp.gui.shell.Ui.icon("grip", 12), SwingConstants.CENTER); //$NON-NLS-1$
        handle.setFont(org.openpnp.gui.shell.Ui.font(11f));
        handle.setForeground(org.openpnp.gui.shell.Ui.text2());
        handle.setIconTextGap(6);
        stripHandle.add(handle);
        stripHandle.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        stripHandle.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                chooseCameraMode(org.openpnp.gui.shell.PageLayouts.Camera.Large);
            }
        });
        stripHandle.setVisible(false);
        cameraStage.anchor(stripHandle, Anchor.South);
    }

    /** The properties column as the page on show wants it, at a width the window allows. */
    private void applyInspectorPolicy() {
        String key = pageKey(navigationRail.getSelectedComponent());
        if (key == null || inspectorPanel == null) {
            return;
        }
        boolean shown = pageLayouts.inspectorShown(key, inspectorPanel.hasContent());
        applyingInspector = true;
        try {
            inspectorPanel.setCollapsed(!shown);
        }
        finally {
            applyingInspector = false;
        }
        applyInspectorWidth();
    }

    /** Set while the column is folded by the page's policy rather than by the user. */
    private boolean applyingInspector;

    public Map<KeyStroke, Action> getHotkeyActionMap() {
        return hotkeyActionMap;
    }

    private Preferences prefs = Preferences.userNodeForPackage(MainFrame.class);

    private ActionListener instructionsCancelActionListener;
    private ActionListener instructionsProceedActionListener;

    private ScriptFileWatcher scriptFileWatcher;
    private JMenuItem mnEditRemoveBoard;
    private JMenu mnEditAddBoard;
    private JMenuItem mnCaptureToolLocation;

    private boolean isShiftDown;
    public boolean getShiftDown() {
        return isShiftDown;
    }

    public MainFrame(Configuration configuration) {
        mainFrame = this;
        this.configuration = configuration;
        // Enum values in every combo box and table, the wizards' included, by their display names.
        org.openpnp.gui.support.DisplayNames.installEverywhere();
        // From here on the model can ask the user things. Without this it answers itself and logs,
        // which is what a script or a test gets.
        configuration.setUserInteraction(new SwingUserInteraction());
        LengthCellValue.setConfiguration(configuration);
        RotationCellValue.setConfiguration(configuration);
        HeadCellValue.setConfiguration(configuration);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // Get handlers for Mac application menu in place.
        boolean macOsXMenus = registerForMacOSXEvents();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                quit();
            }
        });

        // Nothing stored yet: the program has not been run here before.
        boolean firstRun = prefs.get(PREF_WINDOW_WIDTH, null) == null;
        if (prefs.getInt(PREF_WINDOW_WIDTH, MINIMUM_WINDOW_SIZE) < MINIMUM_WINDOW_SIZE) {
            prefs.putInt(PREF_WINDOW_WIDTH, PREF_WINDOW_WIDTH_DEF);
        }

        if (prefs.getInt(PREF_WINDOW_HEIGHT, MINIMUM_WINDOW_SIZE) < MINIMUM_WINDOW_SIZE) {
            prefs.putInt(PREF_WINDOW_HEIGHT, PREF_WINDOW_HEIGHT_DEF);
        }

        windowStyleMultiple = prefs.getBoolean(PREF_WINDOW_STYLE_MULTIPLE, PREF_WINDOW_STYLE_MULTIPLE_DEF);

        setBounds(prefs.getInt(PREF_WINDOW_X, PREF_WINDOW_X_DEF),
                prefs.getInt(PREF_WINDOW_Y, PREF_WINDOW_Y_DEF),
                prefs.getInt(PREF_WINDOW_WIDTH, PREF_WINDOW_WIDTH_DEF),
                prefs.getInt(PREF_WINDOW_HEIGHT, PREF_WINDOW_HEIGHT_DEF));

        // The shell needs 1024 by 640: below that the top bar's controls and the jog card no
        // longer fit, however the rest gives way.
        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        // Within the usable part of the screen it is mostly on - not under the task bar, not
        // hanging off a monitor that has since been unplugged - and no larger than that.
        setBounds(org.openpnp.gui.shell.ScreenFit.clamp(getBounds(), new Dimension(MIN_WIDTH, MIN_HEIGHT)));
        if (firstRun) {
            // A first start fills the screen; the laptop screens this runs on have no room to spare.
            setExtendedState(getExtendedState() | java.awt.Frame.MAXIMIZED_BOTH);
        }
        // Every dialog fits the screen too, scrolling when its content does not.
        org.openpnp.gui.shell.ScreenFit.installForDialogs();
        jobPanel = new JobPanel(configuration, this);
        panelsPanel = new PanelsPanel(configuration, this);
        boardsPanel = new BoardsPanel(configuration, this);
        partsPanel = new PartsPanel(configuration, this);
        packagesPanel = new PackagesPanel(configuration, this);
        feedersPanel = new FeedersPanel(configuration, this);
        machineSetupPanel = new MachineSetupPanel(configuration);
        machineSettingsPanel = new org.openpnp.gui.machinesettings.MachineSettingsPanel(configuration, this,
                machineSetupPanel);
        visionSettingsPanel = new VisionSettingsPanel(configuration, this);

        menuBar = new JMenuBar();

        // File
        //////////////////////////////////////////////////////////////////////
        JMenu mnFile = new JMenu(Translations.getString("Menu.File")); //$NON-NLS-1$
        mnFile.setMnemonic(KeyEvent.VK_F);
        menuBar.add(mnFile);

        mnFile.add(new JMenuItem(jobPanel.newJobAction));
        mnFile.add(new JMenuItem(jobPanel.openJobAction));

        mnFile.add(jobPanel.mnOpenRecent);

        mnFile.addSeparator();
        mnFile.add(new JMenuItem(jobPanel.saveJobAction));
        mnFile.add(new JMenuItem(jobPanel.saveJobAsAction));
        mnFile.addSeparator();
        mnFile.add(new JMenuItem(saveConfigAction));


        // File -> Import
        //////////////////////////////////////////////////////////////////////
        mnFile.addSeparator();
        mnImport = new JMenu(Translations.getString("BoardsPanel.BoardPlacements.Action.Import")); //$NON-NLS-1$
        mnImport.setMnemonic(KeyEvent.VK_I);
        mnImport.setEnabled(false);
        mnFile.add(mnImport);


        if (!macOsXMenus) {
            mnFile.addSeparator();
            mnFile.add(new JMenuItem(quitAction));
        }

        // Edit
        //////////////////////////////////////////////////////////////////////
        JMenu mnEdit = new JMenu(Translations.getString("Menu.Edit")); //$NON-NLS-1$
        mnEdit.setMnemonic(KeyEvent.VK_E);
        menuBar.add(mnEdit);

        // No Undo or Redo: nothing in the program ever records an edit to undo, so the two items
        // only ever did nothing, which a user who relies on them finds out too late.
        mnEditAddBoard = new JMenu(jobPanel.addBoardAction);
        mnEditAddBoard.add(new JMenuItem(jobPanel.addNewBoardAction));
        mnEditAddBoard.add(new JMenuItem(jobPanel.addExistingBoardAction));
        mnEditAddBoard.addSeparator();
        mnEditAddBoard.add(new JMenuItem(jobPanel.addNewPanelAction));
        mnEditAddBoard.add(new JMenuItem(jobPanel.addExistingPanelAction));
        mnEdit.add(mnEditAddBoard);
        mnEditRemoveBoard = new JMenuItem(jobPanel.removeBoardAction);
        mnEdit.add(mnEditRemoveBoard);
        mnEdit.addSeparator();
        mnCaptureToolLocation = new JMenuItem(jobPanel.captureToolBoardLocationAction);
        mnEdit.add(mnCaptureToolLocation);

        // View
        //////////////////////////////////////////////////////////////////////
        JMenu mnView = new JMenu(Translations.getString("Menu.View")); //$NON-NLS-1$
        mnView.setMnemonic(KeyEvent.VK_V);
        menuBar.add(mnView);

        // The units and the language are on the settings page now, with the theme.
        JMenuItem settingsItem = new JMenuItem(Translations.getString("Menu.View.Settings")); //$NON-NLS-1$
        settingsItem.addActionListener(e -> showSettings());
        mnView.add(settingsItem);
        JMenuItem operatorItem = new JMenuItem(Translations.getString("Menu.View.OperatorMode")); //$NON-NLS-1$
        operatorItem.addActionListener(e -> setOperatorMode(true));
        mnView.add(operatorItem);
        mnView.addSeparator();

        // View -> Tables Linked
        JMenuItem menuItem;
        ButtonGroup buttonGroup = new ButtonGroup();
        JMenu tablesLinked = new JMenu(Translations.getString("Menu.View.TablesLinked")); //$NON-NLS-1$
        mnView.add(tablesLinked);

        menuItem = new JCheckBoxMenuItem(tablesUnlinkedSelected);
        buttonGroup.add(menuItem);
        if (configuration.getTablesLinked() == TablesLinked.Unlinked) {
            menuItem.setSelected(true);
        }
        tablesLinked.add(menuItem);
        menuItem = new JCheckBoxMenuItem(tablesLinkedSelected);
        buttonGroup.add(menuItem);
        if (configuration.getTablesLinked() == TablesLinked.Linked) {
            menuItem.setSelected(true);
        }
        tablesLinked.add(menuItem);
        
        

        // View, continued: what used to be the Window menu. The top bar has room for the
        // mockups' six menus, and a menu of two items was one more place to look.
        mnView.addSeparator();
        JCheckBoxMenuItem windowStyleMultipleMenuItem =
                new JCheckBoxMenuItem(windowStyleMultipleSelected);
        mnView.add(windowStyleMultipleMenuItem);
        if (windowStyleMultiple) {
            windowStyleMultipleMenuItem.setSelected(true);
            // The camera's own window can be closed or lost behind others; this brings it back.
            JMenuItem showCameraWindow = new JMenuItem(Translations.getString("Menu.View.ShowCameraWindow")); //$NON-NLS-1$
            showCameraWindow.addActionListener(e -> {
                if (frameCamera != null) {
                    frameCamera.setVisible(true);
                    frameCamera.toFront();
                }
            });
            mnView.add(showCameraWindow);
        }
        else {
            // The camera's size on the page on show, as the strip's pills choose it.
            JMenu mnCamera = new JMenu(Translations.getString("Menu.View.Camera")); //$NON-NLS-1$
            for (org.openpnp.gui.shell.PageLayouts.Camera size : org.openpnp.gui.shell.PageLayouts.Camera.values()) {
                JMenuItem item = new JMenuItem(Translations.getString("MainFrame.Camera." + size.name())); //$NON-NLS-1$
                item.addActionListener(e -> chooseCameraMode(size));
                mnCamera.add(item);
            }
            mnView.add(mnCamera);
        }
        mnView.add(new JMenuItem(editThemeAction));

        // Machine, with what used to be the Job menu: running a job is running the machine.
        //////////////////////////////////////////////////////////////////////
        mnCommands = new JMenu(Translations.getString("Menu.Machine")); //$NON-NLS-1$
        mnCommands.setMnemonic(KeyEvent.VK_M);
        menuBar.add(mnCommands);
        mnCommands.addSeparator();
        mnCommands.add(new JMenuItem(jobPanel.startPauseResumeJobAction));
        mnCommands.add(new JMenuItem(jobPanel.stepJobAction));
        mnCommands.add(new JMenuItem(jobPanel.stopJobAction));
        mnCommands.addSeparator();
        mnCommands.add(new JMenuItem(jobPanel.resetAllPlacedAction));

        // Scripts
        /////////////////////////////////////////////////////////////////////
        mnScripts = new JMenu(Translations.getString("Menu.Scripts")); //$NON-NLS-1$
        mnScripts.setMnemonic(KeyEvent.VK_S);
        menuBar.add(mnScripts);

        // Help
        /////////////////////////////////////////////////////////////////////
        JMenu mnHelp = new JMenu(Translations.getString("Menu.Help")); //$NON-NLS-1$
        mnHelp.setMnemonic(KeyEvent.VK_H);
        menuBar.add(mnHelp);
        if (!macOsXMenus) {
            mnHelp.add(new JMenuItem(aboutAction));
        }
        mnHelp.add(hotkeysAction);
        mnHelp.add(new AbstractAction(Translations.getString("Menu.Help.ControlGallery")) { //$NON-NLS-1$
            @Override
            public void actionPerformed(ActionEvent e) {
                org.openpnp.gui.shell.ControlGallery.showGallery(MainFrame.this);
            }
        });
        mnHelp.addSeparator();
        mnHelp.add(quickStartLinkAction);
        mnHelp.add(setupAndCalibrationLinkAction);
        mnHelp.add(userManualLinkAction);
        mnHelp.addSeparator();
        mnHelp.add(changeLogAction);
        mnHelp.addSeparator();
        mnHelp.add(submitDiagnosticsAction);
        if (isInstallerAvailable()) {
            mnHelp.add(new JMenuItem(checkForUpdatesAction));
        }

        contentPane = new JPanel();
        // No margin round the window: the top bar, the rail and the status bar go to its edges,
        // and the cards between them keep the stylesheet's 10 pixels from each other.
        contentPane.setBorder(null);
        // The window between the cards is the stylesheet's --bg; the panels are --surface.
        contentPane.setBackground(org.openpnp.gui.shell.Ui.bg());
        setContentPane(contentPane);
        contentPane.setLayout(new BorderLayout(0, 0));

        // The camera sits above the page rather than beside it: the tables underneath are wide
        // rows of short fields, and the image is worth more wide than tall. This is also what
        // gives the middle its width back now that the properties column has the right hand side.
        splitPaneMachineAndTabs = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPaneMachineAndTabs.setBorder(null);
        splitPaneMachineAndTabs.setContinuousLayout(true);

        // The properties column is the right side of a split rather than a fixed strip, because
        // the wizards it hosts were drawn for the full width of the window and no one width suits
        // them all. What is stored is the column's width, not the divider's position: the
        // position depends on how wide the window happens to be.
        splitPaneInspector = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPaneInspector.setContinuousLayout(true);
        splitPaneInspector.setResizeWeight(1.0);
        splitPaneInspector.setLeftComponent(splitPaneMachineAndTabs);
        contentPane.add(splitPaneInspector, BorderLayout.CENTER);
        // The camera, the page and the properties column are cards 10 pixels apart on the
        // window's colour; the dividers are those gaps, with the grip dots to say they can be
        // dragged.
        for (JSplitPane split : new JSplitPane[] { splitPaneInspector, splitPaneMachineAndTabs }) {
            split.setBorder(null);
            split.setDividerSize(org.openpnp.gui.shell.Tokens.GAP_CARD);
            split.setBackground(org.openpnp.gui.shell.Ui.bg());
            split.setOpaque(true);
            split.putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE,
                    "gripDotCount: 3; gripDotSize: 3; gripGap: 3; gripColor: $Pono.textMuted; style: grip"); //$NON-NLS-1$
        }
        splitPaneInspector.setBorder(new EmptyBorder(org.openpnp.gui.shell.Tokens.GAP_CARD,
                org.openpnp.gui.shell.Tokens.GAP_CARD, org.openpnp.gui.shell.Tokens.GAP_CARD,
                org.openpnp.gui.shell.Tokens.GAP_CARD));

        panelMachine = new JPanel();
        panelMachine.setBackground(org.openpnp.gui.shell.Ui.bg());
        splitPaneMachineAndTabs.setLeftComponent(panelMachine);
        panelMachine.setLayout(new BorderLayout(0, 0));

        // Add global hotkeys for the arrow keys
        hotkeyActionMap = new HashMap<>();

        Toolkit.getDefaultToolkit().getSystemEventQueue().push(new EventQueue() {
            @Override
            protected void dispatchEvent(AWTEvent event) {
                if (event instanceof KeyEvent) {
                    KeyStroke ks = KeyStroke.getKeyStrokeForEvent((KeyEvent) event);
                    // Stopping the machine is taken first and everywhere: in a text field, in a
                    // dialog, in the camera window. It is the one key that must never be eaten.
                    if (Hotkeys.STOP_MACHINE.equals(ks) && stopMachineAction.isEnabled()) {
                        stopMachineAction.actionPerformed(null);
                        return;
                    }
                    // The command search opens from anywhere in the main window, a text field
                    // included: Ctrl+K types nothing, so there is nothing for it to take away.
                    if (Hotkeys.COMMAND_PALETTE.equals(ks) && !operatorMode && ((KeyEvent) event).getID() == KeyEvent.KEY_PRESSED
                            && KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() == MainFrame.this) {
                        SwingUtilities.invokeLater(MainFrame.this::openCommandPalette);
                        return;
                    }
                    // Ctrl+1 to Ctrl+9 go to the rail's pages, in its order, wherever the focus is.
                    if (navigationRail != null && !operatorMode && ((KeyEvent) event).getID() == KeyEvent.KEY_PRESSED
                            && ((KeyEvent) event).getModifiersEx() == KeyEvent.CTRL_DOWN_MASK
                            && ((KeyEvent) event).getKeyCode() >= KeyEvent.VK_1
                            && ((KeyEvent) event).getKeyCode() <= KeyEvent.VK_9
                            && KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() == MainFrame.this) {
                        int index = ((KeyEvent) event).getKeyCode() - KeyEvent.VK_1;
                        java.util.List<Component> pages = navigationRail.getPageComponents();
                        if (index < pages.size() && pages.get(index) != settingsPanel) {
                            showTab(pages.get(index));
                            return;
                        }
                    }
                    // "/" goes to the page's filter from anywhere but text. It is taken here, as a
                    // table would otherwise start editing its cell with it, and the "/" typed
                    // after it is dropped rather than landing in the filter.
                    if (((KeyEvent) event).getID() == KeyEvent.KEY_TYPED && slashTaken) {
                        slashTaken = false;
                        if (((KeyEvent) event).getKeyChar() == '/') {
                            return;
                        }
                    }
                    if (((KeyEvent) event).getID() == KeyEvent.KEY_PRESSED
                            && (((KeyEvent) event).getKeyCode() == KeyEvent.VK_SLASH
                                    || ((KeyEvent) event).getKeyCode() == KeyEvent.VK_DIVIDE)
                            && ((KeyEvent) event).getModifiersEx() == 0 && !operatorMode
                            && KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() == MainFrame.this
                            && !UiUtils.isTextInputFocused()) {
                        javax.swing.JTextField filter = org.openpnp.gui.shell.Ui.filterFor(KeyboardFocusManager
                                .getCurrentKeyboardFocusManager().getFocusOwner(), getContentPane());
                        if (filter != null) {
                            filter.requestFocusInWindow();
                            filter.selectAll();
                            slashTaken = true;
                            return;
                        }
                    }
                    // Everything else moves the machine or changes the job, so it only applies
                    // where the user is looking at the machine: the main window or the camera
                    // window, with no dialog in front of them, and not while text is being
                    // typed - Ctrl+Shift+Arrow selects words, and a table cell being edited is
                    // text too.
                    if (machineHotkeysApply() && !UiUtils.isTextInputFocused()) {
                        Action action = hotkeyActionMap.get(ks);
                        // Production mode runs the job and nothing else: no jogging, no parking.
                        if (action != null && action.isEnabled() && (!operatorMode || Hotkeys.runsTheJob(ks))) {
                            action.actionPerformed(null);
                            return;
                        }
                    }
                }
                super.dispatchEvent(event);
            }
        });
        cameraPanel = new CameraPanel();
        // Cards cover the image's corners here, so the views keep their own text out from under them.
        cameraPanel.setStageMode(true);

        // The stage is the camera image with everything that belongs to it floating on top. It is
        // what the multiple-windows mode pops out, hence the wrapper panel it lives in.
        panelCameraAndInstructions = new JPanel();
        panelCameraAndInstructions.setBackground(org.openpnp.gui.shell.Ui.bg());
        panelCameraAndInstructions.setLayout(new BorderLayout(0, 0));
        panelMachine.add(panelCameraAndInstructions, BorderLayout.CENTER);

        // The stylesheet's .instr: a 640 pixel banner with a 3 pixel accent rule down its left,
        // the activity mark in a circle, the title over the text, and the buttons at the right.
        panelInstructions = new JPanel() {
            /**
             * 640 wide, and as high as the text wraps to at that width. The banner floats over
             * the image and is laid out at its preferred size, so the height has to come from
             * the text: a fixed 640 x 0 left the card its 12 pixels of padding and nothing else,
             * the instructions and the Next button clipped away.
             */
            @Override
            public Dimension getPreferredSize() {
                int width = INSTRUCTIONS_WIDTH;
                Container stage = SwingUtilities.getAncestorOfClass(CameraStage.class, this);
                if (stage != null && stage.getWidth() > 0) {
                    width = Math.min(width, stage.getWidth() - 40);
                }
                Insets insets = getInsets();
                int side = 0;
                if (panelInstructionActions != null) {
                    side += panelInstructionActions.getPreferredSize().width + 12;
                }
                side += 34 + 12;
                if (lblInstructions != null) {
                    lblInstructions.setSize(Math.max(120, width - insets.left - insets.right - side),
                            Short.MAX_VALUE);
                }
                return new Dimension(width, super.getPreferredSize().height);
            }
        };
        panelInstructions.setVisible(false);
        panelInstructions.setOpaque(false);
        // The accent rule is the card's own left edge, painted by the card; this is the padding
        // inside it, the stylesheet's 12 by 14 less the card's own.
        panelInstructions.setBorder(new EmptyBorder(6, 9, 6, 6));
        panelInstructions.setLayout(new BorderLayout(12, 0));

        // The wizard sets this per step, which is what the etched border's title used to carry.
        lblInstructionsTitle = new JLabel(Translations.getString("General.Instructions")); //$NON-NLS-1$
        lblInstructionsTitle.setFont(org.openpnp.gui.shell.Ui.font(org.openpnp.gui.shell.Ui.BASE, Font.BOLD));
        lblInstructionsTitle.setBorder(new EmptyBorder(0, 0, 2, 0));

        panelInstructionActions = new JPanel();
        panelInstructionActions.setAlignmentY(Component.BOTTOM_ALIGNMENT);
        panelInstructions.add(panelInstructionActions, BorderLayout.EAST);
        panelInstructionActions.setLayout(new BorderLayout(0, 0));

        panel_2 = new JPanel();
        FlowLayout flowLayout_2 = (FlowLayout) panel_2.getLayout();
        flowLayout_2.setVgap(0);
        flowLayout_2.setHgap(0);
        panelInstructionActions.add(panel_2, BorderLayout.SOUTH);

        btnInstructionsCancel = org.openpnp.gui.shell.Ui.button(
                Translations.getString("General.Cancel"), null, //$NON-NLS-1$
                org.openpnp.gui.shell.Ui.Size.Sm, org.openpnp.gui.shell.Ui.Variant.Default);
        btnInstructionsCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                if (instructionsCancelActionListener != null) {
                    instructionsCancelActionListener.actionPerformed(arg0);
                }
            }
        });
        panel_2.add(btnInstructionsCancel);

        btnInstructionsNext = org.openpnp.gui.shell.Ui.button(
                Translations.getString("General.Next"), //$NON-NLS-1$
                org.openpnp.gui.shell.Ui.iconSm("chevright"), //$NON-NLS-1$
                org.openpnp.gui.shell.Ui.Size.Sm, org.openpnp.gui.shell.Ui.Variant.Primary);
        btnInstructionsNext.setHorizontalTextPosition(SwingConstants.LEFT);
        btnInstructionsNext.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                if (instructionsProceedActionListener != null) {
                    instructionsProceedActionListener.actionPerformed(arg0);
                }
            }
        });
        panel_2.add(btnInstructionsNext);

        panel_1 = new JPanel();
        panel_1.setOpaque(false);
        panelInstructions.add(panel_1, BorderLayout.CENTER);
        panel_1.setLayout(new BorderLayout(0, 0));
        panel_1.add(lblInstructionsTitle, BorderLayout.NORTH);
        flowLayout_2.setHgap(6);
        panel_2.setOpaque(false);
        panelInstructionActions.setOpaque(false);
        panelInstructionActions.remove(panel_2);
        panelInstructionActions.setLayout(new GridBagLayout());
        panelInstructionActions.add(panel_2);

        lblInstructions = new JTextPane();
        // does not seem to work with html
        //lblInstructions.setFont(new Font("Lucida Grande", Font.PLAIN, 14)); //$NON-NLS-1$
        // instead use the HONOR_DISPLAY_PROPERTIES to set the proper system dialog font and size 
        lblInstructions.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        lblInstructions.setOpaque(false);
        lblInstructions.setFont(org.openpnp.gui.shell.Ui.font(12f));
        lblInstructions.setForeground(org.openpnp.gui.shell.Ui.text2());
        lblInstructions.setContentType("text/html"); //$NON-NLS-1$
        lblInstructions.setEditable(false);
        panel_1.add(lblInstructions);

        labelIcon = new JLabel(); 
        labelIcon.setIcon(Icons.processActivity1Icon);
        labelIcon.setHorizontalAlignment(SwingConstants.CENTER);
        org.openpnp.gui.shell.RoundedPanel stepCircle = new org.openpnp.gui.shell.RoundedPanel(34,
                org.openpnp.gui.shell.Ui::accentSoft, () -> null);
        stepCircle.setLayout(new BorderLayout());
        stepCircle.setPreferredSize(new Dimension(34, 34));
        stepCircle.add(labelIcon, BorderLayout.CENTER);
        JPanel stepHolder = new JPanel(new GridBagLayout());
        stepHolder.setOpaque(false);
        stepHolder.add(stepCircle);
        panelInstructions.add(stepHolder, BorderLayout.WEST);

        machineControlsPanel = new MachineControlsPanel(configuration, jobPanel);
        droPanel = new DroPanel(configuration);

        // The machine's own commands come first, above the job's.
        mnCommands.insert(new JMenuItem(machineControlsPanel.homeAction), 0);
        mnCommands.insert(new JMenuItem(machineControlsPanel.startStopMachineAction), 1);

        JogControlsPanel jog = machineControlsPanel.getJogControlsPanel();
        for (int mask : Hotkeys.JOG_MODIFIERS) {
            hotkeyActionMap.put(KeyStroke.getKeyStroke(Hotkeys.JOG_Y_PLUS, mask), jog.yPlusAction);
            hotkeyActionMap.put(KeyStroke.getKeyStroke(Hotkeys.JOG_Y_MINUS, mask), jog.yMinusAction);
            hotkeyActionMap.put(KeyStroke.getKeyStroke(Hotkeys.JOG_X_MINUS, mask), jog.xMinusAction);
            hotkeyActionMap.put(KeyStroke.getKeyStroke(Hotkeys.JOG_X_PLUS, mask), jog.xPlusAction);
            hotkeyActionMap.put(KeyStroke.getKeyStroke(Hotkeys.JOG_Z_PLUS, mask), jog.zPlusAction);
            hotkeyActionMap.put(KeyStroke.getKeyStroke(Hotkeys.JOG_Z_MINUS, mask), jog.zMinusAction);
            hotkeyActionMap.put(KeyStroke.getKeyStroke(Hotkeys.JOG_C_PLUS, mask), jog.cPlusAction);
            hotkeyActionMap.put(KeyStroke.getKeyStroke(Hotkeys.JOG_C_MINUS, mask), jog.cMinusAction);
            hotkeyActionMap.put(KeyStroke.getKeyStroke(Hotkeys.INCREMENT_LOWER, mask),
                    jog.lowerIncrementAction);
            hotkeyActionMap.put(KeyStroke.getKeyStroke(Hotkeys.INCREMENT_RAISE, mask),
                    jog.raiseIncrementAction);
            hotkeyActionMap.put(KeyStroke.getKeyStroke(Hotkeys.HOME, mask), machineControlsPanel.homeAction);
        }
        // Starting or stepping a job from the keyboard only where the job is in view: a key that
        // sets the machine going from the parts page, with the job out of sight, is a surprise.
        hotkeyActionMap.put(Hotkeys.JOB_START_PAUSE, onJobPage(jobPanel.startPauseResumeJobAction));
        hotkeyActionMap.put(Hotkeys.JOB_STEP, onJobPage(jobPanel.stepJobAction));
        // Aborting is wanted wherever the user happens to be.
        hotkeyActionMap.put(Hotkeys.JOB_ABORT, jobPanel.stopJobAction);
        hotkeyActionMap.put(Hotkeys.PARK_XY, jog.xyParkAction);
        hotkeyActionMap.put(Hotkeys.PARK_Z, jog.zParkAction);
        hotkeyActionMap.put(Hotkeys.SAFE_Z, jog.safezAction);
        hotkeyActionMap.put(Hotkeys.DISCARD, jog.discardAction);
        Action[] increments = { jog.setIncrement1Action, jog.setIncrement2Action,
                jog.setIncrement3Action, jog.setIncrement4Action, jog.setIncrement5Action };
        for (int i = 0; i < increments.length; i++) {
            hotkeyActionMap.put(KeyStroke.getKeyStroke(Hotkeys.INCREMENT_KEYS[i],
                    KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK), increments[i]);
        }
        // Ctrl-Shift-J folds the jog controls off the camera image and back. A bare J would fire
        // whenever the focus is not in a text field, which includes every table in the window.
        hotkeyActionMap.put(Hotkeys.TOGGLE_JOG_CARD,
                new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        jogCard.toggleAction.actionPerformed(e);
                    }
                });

        isShiftDown = false;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(
            new KeyEventDispatcher() {
                public boolean dispatchKeyEvent(KeyEvent e) {
                    isShiftDown = e.isShiftDown();
                    return false;
                }
            });

        navigationRail = new NavigationRail();
        splitPaneMachineAndTabs.setRightComponent(navigationRail.getPages());

        // Each page keeps its own camera size and divider; see PageLayouts. Only a drag of the
        // divider is remembered - not a window resize moving it, and not full screen - and the
        // camera may be hidden altogether, so neither side insists on a minimum.
        pageLayouts = new org.openpnp.gui.shell.PageLayouts(prefs);
        panelMachine.setMinimumSize(new Dimension(0, 0));
        navigationRail.getPages().setMinimumSize(new Dimension(0, 0));
        // The page's layout can only be applied once the split has a height.
        splitPaneMachineAndTabs.addComponentListener(new java.awt.event.ComponentAdapter() {
            private boolean applied;

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (!applied && splitPaneMachineAndTabs.getHeight() > 0) {
                    applied = true;
                    SwingUtilities.invokeLater(() -> applyPageLayout(navigationRail.getSelectedComponent()));
                }
            }
        });
        onDividerReleased(splitPaneMachineAndTabs, () -> {
            String key = pageKey(navigationRail.getSelectedComponent());
            if (key == null || cameraFullScreen || dockMaximised || windowStyleMultiple) {
                return;
            }
            showCameraMode(pageLayouts.dragged(key, splitPaneMachineAndTabs.getDividerLocation()));
        });

        // The rail's own label is short enough to sit under an icon; the tab title it replaces
        // stays on as the tooltip, since that is the name the wiki and the menus use. The icons
        // are the mockups' 20 pixel set, which follows the theme's text colour.
        addNavigation("Job", org.openpnp.gui.shell.Ui.icon("job", 20), jobPanel); //$NON-NLS-1$ //$NON-NLS-2$
        addNavigation("Feeders", org.openpnp.gui.shell.Ui.icon("feeder", 20), feedersPanel); //$NON-NLS-1$ //$NON-NLS-2$
        addNavigation("Parts", org.openpnp.gui.shell.Ui.icon("parts", 20), partsPanel); //$NON-NLS-1$ //$NON-NLS-2$
        addNavigation("Packages", org.openpnp.gui.shell.Ui.icon("pkg", 20), packagesPanel); //$NON-NLS-1$ //$NON-NLS-2$
        addNavigation("Boards", org.openpnp.gui.shell.Ui.icon("board", 20), boardsPanel); //$NON-NLS-1$ //$NON-NLS-2$
        addNavigation("Panels", org.openpnp.gui.shell.Ui.icon("layers", 20), panelsPanel); //$NON-NLS-1$ //$NON-NLS-2$
        addNavigation("Vision", org.openpnp.gui.shell.Ui.icon("eye", 20), visionSettingsPanel); //$NON-NLS-1$ //$NON-NLS-2$
        navigationRail.addGap();
        // The machine's own settings by topic, the element tree the last of them.
        addNavigation("MachineSettings", org.openpnp.gui.shell.Ui.icon("sliders", 20), machineSettingsPanel); //$NON-NLS-1$ //$NON-NLS-2$
        // Collects what the machine needs and carries it out; the issues page and the
        // diagnostics page before it are in it.
        calibrationPanel = new CalibrationPanel(configuration, this);
        addNavigation("Calibration", org.openpnp.gui.shell.Ui.icon("target", 20), calibrationPanel); //$NON-NLS-1$ //$NON-NLS-2$
        logPanel = new LogPanel(configuration);
        addNavigation("Log", org.openpnp.gui.shell.Ui.icon("log", 20), logPanel); //$NON-NLS-1$ //$NON-NLS-2$
        // Settings is a page of its own at the foot of the rail: appearance, language and units,
        // the operator, saving - which were an appearance dialog and scattered menu items.
        settingsPanel = new SettingsPanel(configuration, this);
        org.openpnp.gui.shell.RoundedPanel settingsCard = org.openpnp.gui.shell.RoundedPanel.card();
        settingsCard.setLayout(new BorderLayout());
        settingsCard.add(settingsPanel, BorderLayout.CENTER);
        pageKeys.put(settingsPanel, "Settings"); //$NON-NLS-1$
        navigationRail.addFootPage(
                Translations.getString("MainFrame.Navigation.Settings"), //$NON-NLS-1$
                Translations.getString("MainFrame.Navigation.Settings.toolTipText"), //$NON-NLS-1$
                org.openpnp.gui.shell.Ui.icon("gear", 20), settingsPanel, settingsCard); //$NON-NLS-1$
        contentPane.add(navigationRail, BorderLayout.WEST);

        navigationRail.addChangeListener(new ChangeListener() {
            private boolean reverting;

            @Override
            public void stateChanged(ChangeEvent e) {
                Component page = navigationRail.getSelectedComponent();
                updateMenuState(page);
                if (reverting || inspectorPanel == null) {
                    return;
                }
                // The machine settings page's own forms are asked about as the column's are.
                if (inspectorPanel.getActivePage() == machineSettingsPanel && page != machineSettingsPanel
                        && !machineSettingsPanel.settleUnappliedEdits()) {
                    reverting = true;
                    try {
                        navigationRail.setSelectedComponent(machineSettingsPanel);
                    }
                    finally {
                        reverting = false;
                    }
                    return;
                }
                // The properties column follows the page. If the user will not let go of
                // unapplied edits in the sheets it would replace, the page switch is undone.
                if (inspectorPanel.setActivePage(page) == Result.Cancelled) {
                    reverting = true;
                    try {
                        navigationRail.setSelectedComponent(inspectorPanel.getActivePage());
                    }
                    finally {
                        reverting = false;
                    }
                    return;
                }
                applyPageLayout(page);
                if (jogCard != null) {
                    jogCard.setPage(pageKey(page), page == feedersPanel);
                }
            }});
        
        topBarPanel = new TopBarPanel(configuration, jobPanel, machineControlsPanel, menuBar,
                () -> showCalibrationStep(null, null), this::openCommandPalette,
                stopMachineAction, this::saveConfig);
        contentPane.add(topBarPanel, BorderLayout.NORTH);
        // The top bar is the title bar, where the look and feel can put the window's buttons in
        // it: a separate title bar cost 30 pixels of height for a name the top bar already shows.
        // Elsewhere the title bar stays as it is.
        if (com.formdev.flatlaf.util.SystemInfo.isWindows_10_orLater
                && com.formdev.flatlaf.ui.FlatNativeWindowBorder.isSupported()) {
            getRootPane().putClientProperty(com.formdev.flatlaf.FlatClientProperties.FULL_WINDOW_CONTENT, true);
            topBarPanel.reserveWindowButtons();
        }
        hotkeyActionMap.put(Hotkeys.COMMAND_PALETTE,
                new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        openCommandPalette();
                    }
                });

        statusBarPanel = new StatusBarPanel(configuration);
        contentPane.add(statusBarPanel, BorderLayout.SOUTH);

        // An applied setting is saved on its own a little after the last change, and the top bar
        // says whether there is anything unsaved in the meantime.
        autosaveTimer.setRepeats(false);
        configuration.addPropertyChangeListener("dirty", e -> SwingUtilities.invokeLater(() -> { //$NON-NLS-1$
            boolean dirty = configuration.isDirty();
            topBarPanel.setConfigurationDirty(dirty);
            if (dirty && prefs.getBoolean(SettingsPanel.PREF_AUTOSAVE, true)) {
                autosaveTimer.restart();
            }
            else {
                autosaveTimer.stop();
            }
        }));
        configuration.addListener(new ConfigurationListener.Adapter() {
            @Override
            public void configurationComplete(Configuration configuration) throws Exception {
                configuration.getMachine().addListener(disconnectReporter);
                // A solution changes settings, and dismissing or reopening an issue changes the
                // lists machine.xml keeps: either way there is something to save.
                if (configuration.getMachine() instanceof org.openpnp.machine.reference.ReferenceMachine) {
                    ((org.openpnp.machine.reference.ReferenceMachine) configuration.getMachine()).getSolutions()
                            .addPropertyChangeListener("issue", e -> configuration.setDirty(true)); //$NON-NLS-1$
                }
            }
        });

        // One properties column for the whole window, where every table used to keep its own
        // below itself behind a split divider.
        inspectorPanel = new InspectorPanel();
        inspectorPanel.setMinimumSize(new Dimension(InspectorPanel.COLLAPSED_WIDTH, 0));
        inspectorPanel.addPropertyChangeListener("collapsed", e -> { //$NON-NLS-1$
            if (!applyingInspector && pageLayouts != null) {
                // Folded or unfolded by hand: that is what this page wants from now on.
                String key = pageKey(navigationRail.getSelectedComponent());
                if (key != null) {
                    pageLayouts.setInspector(key, inspectorPanel.isCollapsed()
                            ? org.openpnp.gui.shell.PageLayouts.Inspector.Hide
                            : org.openpnp.gui.shell.PageLayouts.Inspector.Show);
                }
            }
            applyInspectorWidth();
        });
        inspectorPanel.addPropertyChangeListener(InspectorPanel.PROPERTY_CONTENT, e -> applyInspectorPolicy());
        inspectorPanel.setActivePage(navigationRail.getSelectedComponent());
        splitPaneInspector.setRightComponent(inspectorPanel);
        // Only a width the user dragged to is worth remembering; the collapsed sliver and the
        // positions set while the window is still finding its size are not.
        onDividerReleased(splitPaneInspector, () -> {
            if (!inspectorPanel.isCollapsed() && splitPaneInspector.getWidth() > 0) {
                int width = splitPaneInspector.getWidth() - splitPaneInspector.getInsets().right
                        - splitPaneInspector.getDividerLocation() - splitPaneInspector.getDividerSize();
                if (width > InspectorPanel.COLLAPSED_WIDTH) {
                    prefs.putInt(PREF_INSPECTOR_WIDTH, width);
                    applyInspectorWidth();
                }
            }
        });
        // The column's width follows the window within its limits.
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                applyInspectorWidth();
            }
        });
        // The divider can only be placed once the split has a width.
        SwingUtilities.invokeLater(this::applyInspectorWidth);

        // No title on the camera: the camera selector inside it already says which one this is,
        // and the etched box only cost the view a few pixels on every edge.
        cameraPanel.setBorder(null);
        cameraStage = new CameraStage(cameraPanel);
        // Top left: which camera, and how big a pixel is. Top right: the view tools.
        PillBar cameraSelector = cameraPanel.getCameraSelector();
        cameraSelector.setLabeller(item -> {
            if (item instanceof CameraItem) {
                Camera camera = ((CameraItem) item).getCamera();
                return camera.getHead() == null ? camera.getName()
                        : camera.getName() + " \u00b7 " //$NON-NLS-1$
                                + Translations.getString("CameraPanel.Show.HeadPrefix") //$NON-NLS-1$
                                + camera.getHead().getName();
            }
            return Translations.getString("CameraPanel.Show." //$NON-NLS-1$
                    + String.valueOf(item).replace(" ", "")); //$NON-NLS-1$ //$NON-NLS-2$
        });
        // The stylesheet offers the cameras and one side-by-side view; the other two choices stay
        // in the model for a stored preference that names them.
        cameraSelector.setHidden(item -> !(item instanceof CameraItem)
                && !"Show All Horizontal".equals(String.valueOf(item))); //$NON-NLS-1$
        cameraSelector.setOrder(item -> item instanceof CameraItem ? 0 : 1);
        // A camera on a head carries the camera icon, the side-by-side view the panel icon.
        cameraSelector.setIconer(item -> {
            if (item instanceof CameraItem) {
                return ((CameraItem) item).getCamera().getHead() == null ? null
                        : org.openpnp.gui.shell.Ui.iconSm("camera"); //$NON-NLS-1$
            }
            return org.openpnp.gui.shell.Ui.iconSm("panel"); //$NON-NLS-1$
        });
        OverlayCard selectorStrip = OverlayCard.strip();
        selectorStrip.add(cameraSelector);
        cameraStage.anchor(selectorStrip, Anchor.NorthWest);
        // The scale is one glass chip of its own, 32 high, in the monospaced figures.
        unitsPerPixelChip = new JLabel();
        unitsPerPixelChip.setFont(org.openpnp.gui.shell.Ui.mono(12f, java.awt.Font.PLAIN));
        unitsPerPixelChip.setForeground(org.openpnp.gui.shell.Ui.text2());
        unitsStrip = new OverlayCard() {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                return new Dimension(size.width, 32 + OverlayCard.SHADOW_TOP + OverlayCard.SHADOW_BOTTOM);
            }
        };
        unitsStrip.setLayout(new BorderLayout());
        unitsStrip.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        unitsStrip.add(unitsPerPixelChip);
        cameraStage.anchor(unitsStrip, Anchor.NorthWest);
        cameraPanel.addSelectionListener(this::showUnitsPerPixel);
        cameraToolsBar = new CameraToolsBar(configuration, cameraPanel, this::toggleCameraFullScreen,
                this::selectedFootprint, this::selectedFootprintLabel);
        cameraStage.anchor(cameraToolsBar, Anchor.NorthEast);
        // The readout goes bottom left and the machine controls bottom right, as the mockups have
        // them; the instructions arrive at the top, over the image they are talking about.
        cameraStage.overlay(droPanel, Anchor.SouthWest);
        jogCard = new JogCard(configuration, machineControlsPanel);
        cameraStage.anchor(jogCard, Anchor.SouthEast);
        instructionsCard = cameraStage.overlay(panelInstructions, Anchor.North);
        instructionsCard.setAccentEdge(true);
        instructionsCard.setVisible(false);
        buildCameraModeControls();
        panelCameraAndInstructions.add(cameraStage, BorderLayout.CENTER);

        operatorView = new org.openpnp.gui.operator.OperatorView(configuration, jobPanel);
        operatorBanner = cameraStage.overlay(operatorView.getBanner(), Anchor.North);
        operatorBanner.setVisible(false);
        operatorView.onRefresh(s -> {
            int empty = 0;
            for (org.openpnp.gui.operator.OperatorSummary.Attention a : s.attention) {
                empty += a.empty ? 1 : 0;
            }
            topBarPanel.showOperator(empty, s.attention.size() - empty);
        });
        topBarPanel.setOperatorActions(() -> setOperatorMode(true), () -> setOperatorMode(false), hotkeysAction);

        splitPaneMachineAndTabs.setResizeWeight(0.5);

        addImporterMenuOptions();

        addComponentListener(mainFrameListener);
        
        boolean configurationLoaded = false;
        while (!configurationLoaded) {
	        try {
	            configuration.load();
	            scriptFileWatcher = new ScriptFileWatcher(configuration.getScripting());
	            scriptFileWatcher.setMenu(mnScripts);
	            
	            // The welcome and its three steps, at every start until the user says not to.
	            if (configuration.getMachine().getProperty("Welcome2_0_Dialog_Shown") == null) {
	                Welcome2_0Dialog dialog = new Welcome2_0Dialog(this, configuration);
	                dialog.setLocationRelativeTo(this);
	                dialog.setVisible(true);
	                if (dialog.isDontShowAgain()) {
	                    configuration.getMachine().setProperty("Welcome2_0_Dialog_Shown", true);
	                }
	            }
	            configurationLoaded = true;    
	        }
	        catch (Exception e) {
	            Logger.error(e, "Failed to load the configuration from {}.", //$NON-NLS-1$
	                    configuration.getConfigurationDirectory());
	            // The directory is the machine's whole setup: the way back is the file that failed
	            // or a backup of it, never starting over, which the message used to suggest.
	            java.io.File directory = configuration.getConfigurationDirectory();
	            java.io.StringWriter stack = new java.io.StringWriter();
	            e.printStackTrace(new java.io.PrintWriter(stack));
	            org.openpnp.gui.shell.Dialogs.Content content = new org.openpnp.gui.shell.Dialogs.Content()
	                    .tone(org.openpnp.gui.shell.Dialogs.Tone.Err, "alert") //$NON-NLS-1$
	                    .title(Translations.getString("MainFrame.LoadConfig.Error.Title")) //$NON-NLS-1$
	                    .what(String.format(Translations.getString("MainFrame.LoadConfig.Error.What"), //$NON-NLS-1$
	                            org.openpnp.gui.shell.ErrorMessages.explain(null, e.getMessage()).what))
	                    .more(String.format(Translations.getString("MainFrame.LoadConfig.Error.More"), //$NON-NLS-1$
	                            directory.getAbsolutePath()))
	                    .details(stack.toString());
	            java.util.List<org.openpnp.gui.shell.Dialogs.Choice> choices = java.util.Arrays.asList(
	                    org.openpnp.gui.shell.Dialogs.Choice
	                            .plain(Translations.getString("MainFrame.LoadConfig.Error.OpenFolder")) //$NON-NLS-1$
	                            .utility(() -> UiUtils.openFolder(this, directory)),
	                    org.openpnp.gui.shell.Dialogs.Choice
	                            .plain(Translations.getString("MainFrame.LoadConfig.Error.Quit")), //$NON-NLS-1$
	                    org.openpnp.gui.shell.Dialogs.Choice
	                            .primary(Translations.getString("MainFrame.LoadConfig.Error.Retry"))); //$NON-NLS-1$
	            if (org.openpnp.gui.shell.Dialogs.show(this, content, choices, 1, 2) != 2) {
	            	System.exit(1);
	            }
	        }
	    }
        splitWindows();
    }

    // 20161222 - ldpgh/lutz_dd
    /**
     * Pop the camera out into a window of its own, for a second monitor.
     * <p>
     * The machine controls used to get a window of their own as well. They float over the camera
     * image now, so they travel with it and there is nothing left to put in a second window; the
     * preference keys that sized it are gone with it.
     */
    public void splitWindows() {
        if (windowStyleMultiple) {
            // pin panelCameraAndInstructions to a separate JFrame
            frameCamera = new JDialog(this, "Pono - Camera", false); //$NON-NLS-1$
            // as of today no smart way found to get an adjusted size
            // ... so main window size is used for the camera window
            frameCamera.getContentPane().add(panelCameraAndInstructions);
            frameCamera.setVisible(true);
            frameCamera.addComponentListener(cameraWindowListener);

            if (prefs.getInt(PREF_CAMERA_WINDOW_WIDTH, 50) < 50) {
                prefs.putInt(PREF_CAMERA_WINDOW_WIDTH, PREF_CAMERA_WINDOW_WIDTH_DEF);
            }

            if (prefs.getInt(PREF_CAMERA_WINDOW_HEIGHT, 50) < 50) {
                prefs.putInt(PREF_CAMERA_WINDOW_HEIGHT, PREF_CAMERA_WINDOW_HEIGHT_DEF);
            }

            frameCamera.setBounds(prefs.getInt(PREF_CAMERA_WINDOW_X, PREF_CAMERA_WINDOW_X_DEF),
                    prefs.getInt(PREF_CAMERA_WINDOW_Y, PREF_CAMERA_WINDOW_Y_DEF),
                    prefs.getInt(PREF_CAMERA_WINDOW_WIDTH, PREF_CAMERA_WINDOW_WIDTH_DEF),
                    prefs.getInt(PREF_CAMERA_WINDOW_HEIGHT, PREF_CAMERA_WINDOW_HEIGHT_DEF));

            // The camera left the split, so give the whole height to the page below it.
            splitPaneMachineAndTabs.setDividerLocation(0);
        }
        else {
            panelMachine.add(panelCameraAndInstructions, BorderLayout.CENTER);
            // The page on show decides the camera's size, once the window has one.
            SwingUtilities.invokeLater(() -> applyPageLayout(navigationRail.getSelectedComponent()));
        }
    }
    
    public boolean isInstallerAvailable() {
        try {
            Class.forName("com.install4j.api.launcher.ApplicationLauncher"); //$NON-NLS-1$
            return true;
        }
        catch (Throwable e) {
            return false;
        }
    }

    public DroPanel getDroPanel() {
        return droPanel;
    }

    private void addImporterMenuOptions() {
        for (BoardImporter bi : boardsPanel.getBoardPlacementsPanel().getBoardImporters()) {
            final BoardImporter boardImporter = bi;
            JMenuItem menuItem = new JMenuItem(new AbstractAction() {
                {
                    putValue(NAME, boardImporter.getImporterName());
                    putValue(SHORT_DESCRIPTION, boardImporter.getImporterDescription());
                    putValue(MNEMONIC_KEY, KeyEvent.VK_I);
                }

                @Override
                public void actionPerformed(ActionEvent e) {
                    if (navigationRail.getSelectedComponent() == jobPanel) {
                        boardsPanel.selectBoard((Board) jobPanel.getSelection().getPlacementsHolder().getDefinition());
                    }
                    boardsPanel.getBoardPlacementsPanel().importBoard(boardImporter.getClass());
                }
            });
            mnImport.add(menuItem);
        }
    }


    /**
     * Enables/disables the Import Board, Add Board/Panel, and Remove Board(s)/Panel(s) menu items
     * appropriately depending on which tab is selected and what is selected within the tab
     * @param selectedTab - the selected tab
     */
    public void updateMenuState(Component selectedTab) {
        if (selectedTab != navigationRail.getSelectedComponent()) {
            return;
        }
        if (selectedTab == jobPanel) {
            if (jobPanel.getSelections().size() == 1 && jobPanel.getSelection() instanceof BoardLocation &&
                    jobPanel.getJob().instanceCount(jobPanel.getSelection().getPlacementsHolder()) == 1 &&
                    jobPanel.getJob().getRootPanelLocation().getChildren().containsAll(jobPanel.getSelections())) {
                mnImport.setEnabled(true);
            }
            else {
                mnImport.setEnabled(false);
            }
            mnEditAddBoard.setEnabled(true);
            if (jobPanel.getSelections().size() >= 1) {
                mnEditRemoveBoard.setEnabled(jobPanel.getJob().getRootPanelLocation().getChildren().
                        containsAll(jobPanel.getSelections()));
            }
            else {
                mnEditRemoveBoard.getAction().setEnabled(false);
            }
            if (jobPanel.getSelections().size() == 1 && jobPanel.getJob().getRootPanelLocation().getChildren().containsAll(jobPanel.getSelections()) ) {
                mnCaptureToolLocation.setEnabled(true);
            }
            else {
                mnCaptureToolLocation.setEnabled(false);
            }
        }
        else if (selectedTab == panelsPanel) {
            mnImport.setEnabled(false);
            mnEditAddBoard.setEnabled(false);
            mnEditRemoveBoard.setEnabled(false);
            mnCaptureToolLocation.setEnabled(false);
        }
        else if (selectedTab == boardsPanel) {
            if (boardsPanel.getSelections().size() == 1) {
                mnImport.setEnabled(true);
            }
            else {
                mnImport.setEnabled(false);
            }
            mnEditAddBoard.setEnabled(false);
            mnEditRemoveBoard.setEnabled(false);
            mnCaptureToolLocation.setEnabled(false);
        }
        else {
            mnImport.setEnabled(false);
            mnEditAddBoard.setEnabled(false);
            mnEditRemoveBoard.setEnabled(false);
            mnCaptureToolLocation.setEnabled(false);
        }
    }
    
    public void showInstructions(String title, String instructions, boolean showCancelButton,
            boolean showProceedButton, String proceedButtonText,
            ActionListener cancelActionListener, ActionListener proceedActionListener) {
        showInstructions(title, instructions, 0, 0, showCancelButton, showProceedButton,
                proceedButtonText, cancelActionListener, proceedActionListener);
    }

    /**
     * @param step  Which step this is, from one, shown in the banner's circle as "2/4"; zero
     *              for a wizard that does not count its steps, which shows the busy mark.
     * @param steps How many steps there are.
     */
    public void showInstructions(String title, String instructions, int step, int steps,
            boolean showCancelButton, boolean showProceedButton, String proceedButtonText,
            ActionListener cancelActionListener, ActionListener proceedActionListener) {
        boolean counted = step > 0 && steps > 0;
        labelIcon.setText(counted ? step + "/" + steps : null); //$NON-NLS-1$
        labelIcon.setForeground(org.openpnp.gui.shell.Ui.accent());
        labelIcon.setFont(org.openpnp.gui.shell.Ui.font(12f, Font.BOLD));
        if (counted) {
            labelIcon.setIcon(null);
        }
        instructionsCounted = counted;
        // A wizard points at the image: a page with the camera as a strip, or none, gets it
        // large until the wizard is done.
        String page = pageKey(navigationRail.getSelectedComponent());
        if (page != null && !windowStyleMultiple && pageLayouts != null
                && pageLayouts.camera(page) != org.openpnp.gui.shell.PageLayouts.Camera.Large) {
            wizardEnlargedCamera = true;
            setCameraMode(page, org.openpnp.gui.shell.PageLayouts.Camera.Large, false);
        }
        statusBarPanel.setWizardLink(this::showWizard);
        setStatusState(Translations.getString("StatusBar.State.Wizard"), Chip.Tone.Run); //$NON-NLS-1$
        setStatus(counted ? title + " \u00b7 " + step + "/" + steps : title); //$NON-NLS-1$ //$NON-NLS-2$
        lblInstructionsTitle.setText(title);
        lblInstructions.setText(instructions);
        btnInstructionsCancel.setVisible(showCancelButton);
        btnInstructionsNext.setVisible(showProceedButton);
        btnInstructionsNext.setText(proceedButtonText);
        instructionsCancelActionListener = cancelActionListener;
        instructionsProceedActionListener = proceedActionListener;
        panelInstructions.setVisible(true);
        // The card is what the overlay lays out, so it is the one that has to appear.
        instructionsCard.setVisible(true);
        cameraStage.revalidate();
        instructionsCard.repaint();
        if (scheduledExecutor == null) {
            scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
            scheduledExecutor.scheduleAtFixedRate(new Runnable() {
                public void run() {
                    if (!instructionsCounted) {
                        labelIcon.setIcon(labelIcon.getIcon() == Icons.processActivity1Icon ? Icons.processActivity2Icon : Icons.processActivity1Icon);
                    }
                }
            }, 0, 1000, TimeUnit.MILLISECONDS);
        }
    }

    /** Whether the banner's circle shows the step count rather than the busy mark. */
    private volatile boolean instructionsCounted;

    public void hideInstructions() {
        boolean running = jobPanel != null && jobPanel.isJobRunning();
        setStatusState(Translations.getString(running ? "StatusBar.State.Running" //$NON-NLS-1$
                : "StatusBar.State.Idle"), running ? Chip.Tone.Run : Chip.Tone.Pending); //$NON-NLS-1$
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
            scheduledExecutor = null;
        }
        panelInstructions.setVisible(false);
        instructionsCard.setVisible(false);
        statusBarPanel.setWizardLink(null);
        if (wizardEnlargedCamera) {
            wizardEnlargedCamera = false;
            applyPageLayout(navigationRail.getSelectedComponent());
        }
        cameraStage.revalidate();
        cameraStage.repaint();
    }

    /** Brings a wizard's instructions into view: the camera large, or its window to the front. */
    private void showWizard() {
        if (windowStyleMultiple && frameCamera != null) {
            frameCamera.setVisible(true);
            frameCamera.toFront();
            return;
        }
        String page = pageKey(navigationRail.getSelectedComponent());
        if (page != null && pageLayouts.camera(page) != org.openpnp.gui.shell.PageLayouts.Camera.Large) {
            wizardEnlargedCamera = true;
        }
        if (page != null) {
            setCameraMode(page, org.openpnp.gui.shell.PageLayouts.Camera.Large, false);
        }
        toFront();
    }

    public boolean registerForMacOSXEvents() {
        if ((System.getProperty("os.name").toLowerCase().startsWith("mac os x"))) { //$NON-NLS-1$ //$NON-NLS-2$
            try {
                // Generate and register the OSXAdapter, passing it a hash of
                // all the methods we wish to
                // use as delegates for various
                // com.apple.eawt.ApplicationListener methods
                OSXAdapter.setQuitHandler(this,
                        getClass().getDeclaredMethod("quit", (Class[]) null)); //$NON-NLS-1$
                OSXAdapter.setAboutHandler(this,
                        getClass().getDeclaredMethod("about", (Class[]) null)); //$NON-NLS-1$
                // OSXAdapter.setPreferencesHandler(this, getClass()
                // .getDeclaredMethod("preferences", (Class[]) null));
                // OSXAdapter.setFileHandler(
                // this,
                // getClass().getDeclaredMethod("loadImageFile",
                // new Class[] { String.class }));
                return true;
            }
            catch (Exception e) {
                System.err.println("Error while loading the OSXAdapter: " + e.getMessage()); //$NON-NLS-1$
            }
        }
        return false;
    }

    public void about() {
        AboutDialog dialog = new AboutDialog(this);
        dialog.setSize(750, 550);
        dialog.setLocationRelativeTo(null);
        dialog.setModal(true);
        dialog.setVisible(true);
    }

    /** The stylesheet's .instr banner width. */
    private static final int INSTRUCTIONS_WIDTH = 640;

    /** Autosave's delay after the last change: long enough that a run of edits saves once. */
    private static final int AUTOSAVE_DELAY_MS = 3000;
    private final javax.swing.Timer autosaveTimer = new javax.swing.Timer(AUTOSAVE_DELAY_MS,
            e -> autosave());

    /**
     * Saves the configuration without asking about boards and panels, unless the machine is in
     * the middle of something: its tasks change the configuration from their own thread, and a
     * save that serialises it meanwhile could catch it half changed. It tries again later.
     */
    private void autosave() {
        Machine machine = configuration.getMachine();
        if ((machine != null && machine.isBusy()) || jobPanel.isJobRunning()) {
            autosaveTimer.restart();
            return;
        }
        try {
            configuration.autosave();
            setStatus(String.format(Translations.getString("MainFrame.Autosave.Saved"), //$NON-NLS-1$
                    new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()))); //$NON-NLS-1$
        }
        catch (Exception e) {
            Logger.warn(e, "Autosaving the configuration failed."); //$NON-NLS-1$
            setStatus(String.format(Translations.getString("MainFrame.Autosave.Failed"), //$NON-NLS-1$
                    e.getMessage()));
        }
    }

    private org.openpnp.gui.operator.OperatorView operatorView;
    private OverlayCard operatorBanner;
    private boolean operatorMode;
    private JPanel operatorHolder;

    public boolean isOperatorMode() {
        return operatorMode;
    }

    /**
     * Production mode, mockup 03: the camera in the middle with what is being placed over it, the
     * job's progress, the big buttons, the feeders to see to and the latest events beside it. The
     * pages, the properties and the menus are out of reach until it is left, and of the machine's
     * keys only those that run the job and stop the machine are taken.
     */
    public void setOperatorMode(boolean on) {
        if (on == operatorMode || operatorView == null) {
            return;
        }
        if (on && inspectorPanel != null && !inspectorPanel.getPresenter().settleUnappliedEdits()) {
            return;
        }
        operatorMode = on;
        boolean stageAtHome = on ? panelCameraAndInstructions.getParent() == panelMachine
                : panelCameraAndInstructions.getParent() == operatorView.getStage();
        if (on) {
            if (stageAtHome) {
                panelMachine.remove(panelCameraAndInstructions);
                operatorView.getStage().add(panelCameraAndInstructions, BorderLayout.CENTER);
            }
            if (operatorHolder == null) {
                operatorHolder = new JPanel(new BorderLayout());
                operatorHolder.setBackground(org.openpnp.gui.shell.Ui.bg());
                operatorHolder.setBorder(new EmptyBorder(org.openpnp.gui.shell.Tokens.GAP_CARD + 2,
                        org.openpnp.gui.shell.Tokens.GAP_CARD + 2, org.openpnp.gui.shell.Tokens.GAP_CARD + 2, 0));
                operatorHolder.add(operatorView, BorderLayout.CENTER);
            }
            // Out of the window while the workbench shows, it missed any change of theme since.
            SwingUtilities.updateComponentTreeUI(operatorHolder);
            contentPane.remove(splitPaneInspector);
            contentPane.add(operatorHolder, BorderLayout.CENTER);
            navigationRail.setVisible(false);
            // The image and what is being placed over it; the controls that move the machine by
            // hand and the view tools are the workbench's.
            for (Component c : new Component[] { jogCard, cameraToolsBar, cameraModeCard, stripHandle, unitsStrip }) {
                if (c != null) {
                    c.setVisible(false);
                }
            }
            instructionsCard.setVisible(false);
        }
        else {
            operatorBanner.setVisible(false);
            if (stageAtHome) {
                operatorView.getStage().remove(panelCameraAndInstructions);
                panelMachine.add(panelCameraAndInstructions, BorderLayout.CENTER);
            }
            contentPane.remove(operatorHolder);
            contentPane.add(splitPaneInspector, BorderLayout.CENTER);
            navigationRail.setVisible(true);
            applyPageLayout(navigationRail.getSelectedComponent());
        }
        topBarPanel.setOperatorMode(on);
        statusBarPanel.setOperatorMode(on, java.util.prefs.Preferences.userNodeForPackage(SettingsPanel.class)
                .get(SettingsPanel.PREF_OPERATOR, "")); //$NON-NLS-1$
        operatorView.setActive(on);
        contentPane.revalidate();
        contentPane.repaint();
    }

    /** Whether a key that moves the machine or changes the job should be taken now. */
    private boolean machineHotkeysApply() {
        Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        return active == this || (frameCamera != null && active == frameCamera);
    }

    /**
     * The action, but only while the job page is showing; elsewhere the key says where it works
     * instead of starting the machine on a job the user is not looking at.
     */
    private Action onJobPage(Action action) {
        return new AbstractAction() {
            @Override
            public boolean isEnabled() {
                return action.isEnabled();
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                if (!operatorMode && navigationRail.getSelectedComponent() != jobPanel) {
                    setStatus(Translations.getString("MainFrame.Hotkey.JobPageOnly")); //$NON-NLS-1$
                    return;
                }
                action.actionPerformed(e);
            }
        };
    }

    /**
     * Stops the machine, from anywhere: aborts a running job, then disables the machine. While the
     * machine is busy the disabling bypasses the task queue, as the machine controls' own switch
     * does for an emergency, because a queued stop would wait for the very motion it is meant to
     * interrupt.
     */
    public final Action stopMachineAction = new AbstractAction(Translations.getString("TopBar.StopMachine")) { //$NON-NLS-1$
        {
            putValue(SHORT_DESCRIPTION, String.format(
                    Translations.getString("TopBar.StopMachine.toolTipText"), //$NON-NLS-1$
                    Hotkeys.describe(Hotkeys.STOP_MACHINE)));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            final Machine machine = configuration.getMachine();
            if (machine == null) {
                return;
            }
            if (jobPanel.isJobRunning() && jobPanel.stopJobAction.isEnabled()) {
                jobPanel.stopJobAction.actionPerformed(e);
            }
            if (!machine.isEnabled()) {
                setStatus(Translations.getString("TopBar.StopMachine.AlreadyStopped")); //$NON-NLS-1$
                return;
            }
            Runnable disable = () -> {
                try {
                    machine.setEnabled(false);
                }
                catch (Exception ex) {
                    Logger.error(ex, "Stopping the machine failed."); //$NON-NLS-1$
                    SwingUtilities.invokeLater(() -> UiUtils.showError(ex));
                }
            };
            if (machine.isBusy()) {
                Thread thread = new Thread(disable, "Pono stop machine"); //$NON-NLS-1$
                thread.setDaemon(true);
                thread.start();
            }
            else {
                UiUtils.submitUiMachineTask(() -> {
                    disable.run();
                    return null;
                });
            }
        }
    };

    /**
     * Says why the machine went off, which the driver reports and nobody used to show: the state
     * chip only ever said "disconnected".
     */
    private final MachineListener disconnectReporter = new MachineListener.Adapter() {
        @Override
        public void machineEnabled(Machine machine) {
            SwingUtilities.invokeLater(() -> statusBarPanel.setMachineEnabled(true));
        }

        @Override
        public void machineDisabled(Machine machine, String reason) {
            SwingUtilities.invokeLater(() -> statusBarPanel.setMachineEnabled(false));
            report(Translations.getString("MainFrame.Machine.Disabled"), reason); //$NON-NLS-1$
        }

        @Override
        public void machineEnableFailed(Machine machine, String reason) {
            report(Translations.getString("MainFrame.Machine.EnableFailed"), reason); //$NON-NLS-1$
        }

        @Override
        public void machineBusy(Machine machine, boolean busy) {
            SwingUtilities.invokeLater(() -> statusBarPanel.setBusy(busy));
        }

        private void report(String what, String reason) {
            if (reason == null || reason.trim().isEmpty()) {
                return;
            }
            setStatus(String.format(what, reason.trim()));
        }
    };

    /** The window-wide hotkeys, listed from the one place they are bound. */
    private final Action hotkeysAction = new AbstractAction(Translations.getString("Menu.Help.Hotkeys")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent e) {
            StringBuilder html = new StringBuilder("<html><table cellpadding='2'>"); //$NON-NLS-1$
            for (Hotkeys.Entry entry : Hotkeys.all()) {
                html.append("<tr><td><b>").append(Hotkeys.describe(entry.keyStroke)) //$NON-NLS-1$
                        .append("</b></td><td>").append(Translations.getString(entry.descriptionKey)) //$NON-NLS-1$
                        .append("</td></tr>"); //$NON-NLS-1$
            }
            html.append("</table></html>"); //$NON-NLS-1$
            JLabel label = new JLabel(html.toString());
            JScrollPane scroll = new JScrollPane(label);
            scroll.setBorder(null);
            scroll.setPreferredSize(new Dimension(460, Math.min(560,
                    label.getPreferredSize().height + 8)));
            JOptionPane.showMessageDialog(MainFrame.this, scroll,
                    Translations.getString("Menu.Help.Hotkeys"), JOptionPane.PLAIN_MESSAGE); //$NON-NLS-1$
        }
    };

    public boolean saveConfig() {
        // Save the configuration
        try {
            Preferences.userRoot().flush();
        }
        catch (Exception e) {
            MessageBoxes.errorBox(MainFrame.this,
                    Translations.getString("MainFrame.SavePreferences.ErrorBox.Title"), e); //$NON-NLS-1$
        }
        
        try {
            configuration.save();
            setStatus(String.format(Translations.getString("MainFrame.Autosave.Saved"), //$NON-NLS-1$
                    new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()))); //$NON-NLS-1$
        }
        catch (SaveCancelledException e) {
            return false;
        }
        catch (Exception e) {
			String message = String.format(
					Translations.getString("MainFrame.SaveConfig.Error.Message"), e.getMessage()); //$NON-NLS-1$
			message = message.replaceAll("\n", "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
			message = message.replaceAll("\r", ""); //$NON-NLS-1$ //$NON-NLS-2$
			message = "<html><body width=\"400\">" + message + "</body></html>"; //$NON-NLS-1$ //$NON-NLS-2$
			JOptionPane.showMessageDialog(this, message,
					Translations.getString("MainFrame.SaveConfig.Error.Title"), //$NON-NLS-1$
					JOptionPane.ERROR_MESSAGE);
			return false;
        }

        Logger.debug("Config saved successfully!"); //$NON-NLS-1$
        return true;
    }

    public boolean quit() {
        Logger.info("Shutting down..."); //$NON-NLS-1$
        if (!settleBeforeQuit() || !saveBeforeQuit()) {
            return false;
        }
        shutDown(false);
        return true;
    }

    /**
     * Starts Pono again on a configuration written over the one loaded, a preset applied: the
     * questions quitting asks were asked before it was written, and the machine loaded now is
     * not saved over it.
     */
    public void restart() {
        Logger.info("Restarting..."); //$NON-NLS-1$
        configuration.freezeMachineFiles();
        saveBeforeQuit();
        shutDown(true);
    }

    /**
     * What quitting asks about before anything is written: a running job, edits not applied, a
     * job not saved. False when the user would rather stay.
     */
    public boolean settleBeforeQuit() {
        // A running job first: quitting under it used to save, ask about the job file and switch
        // the machine off in the middle of a placement, without a word about the job itself.
        if (jobPanel.isJobRunning()) {
            // Stopping a job is one of the dangerous things: red, named, Cancel where the focus is.
            boolean stop = org.openpnp.gui.shell.Dialogs.confirmDanger(this,
                    Translations.getString("MainFrame.Quit.JobRunning.Title"), //$NON-NLS-1$
                    Translations.getString("MainFrame.Quit.JobRunning.Message"), null, null, //$NON-NLS-1$
                    Translations.getString("MainFrame.Quit.JobRunning.Stop")); //$NON-NLS-1$
            if (!stop) {
                return false;
            }
            jobPanel.stopJobAction.actionPerformed(null);
        }
        // Then whatever was typed into the properties column and not applied yet.
        if (inspectorPanel != null && !inspectorPanel.getPresenter().settleUnappliedEdits()) {
            return false;
        }
        if (machineSettingsPanel != null && !machineSettingsPanel.settleUnappliedEdits()) {
            return false;
        }
        // What calibration measured and nobody confirmed is discarded, unless applied now.
        if (calibrationPanel != null && !calibrationPanel.settleBeforeQuit(this)) {
            return false;
        }
        if (!jobPanel.checkForModifications()) {
            return false;
        }
        try {
            Preferences.userRoot().flush();
        }
        catch (Exception e) {
            Logger.warn(e, "Failed to flush user preferences while shutting down."); //$NON-NLS-1$
        }
        return true;
    }

    /** The configuration saved on the way out; false when the user would rather stay after it failed. */
    private boolean saveBeforeQuit() {
        try {
            configuration.save();
        }
        catch (SaveCancelledException e) {
            return false;
        }
        catch (Exception e) {
            String message = String.format(
                    Translations.getString("MainFrame.QuitSaveConfig.Error.Message"), e.getMessage()); //$NON-NLS-1$
            // Quitting now loses what was not saved: the button says so.
            int result = org.openpnp.gui.shell.Dialogs.ask(this, org.openpnp.gui.shell.Dialogs.Tone.Err, "save", //$NON-NLS-1$
                    Translations.getString("MainFrame.SaveConfig.Error.Title"), message.replace("\r", ""), null, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    new org.openpnp.gui.shell.Dialogs.Choice(Translations.getString("MainFrame.QuitSaveConfig.QuitAnyway"), //$NON-NLS-1$
                            null, org.openpnp.gui.shell.Ui.Variant.SolidDanger));
            if (result != 0) {
                return false;
            }
        }
        return true;
    }

    /** The machine switched off and closed, the program started again if asked, and this one ended. */
    private void shutDown(boolean again) {
        try {
            configuration.getMachine().setEnabled(false);
        }
        catch (Exception e) {
            Logger.error(e, "Failed to disable the machine while shutting down."); //$NON-NLS-1$
        }
        try {
            configuration.getMachine().close();
        }
        catch (Exception e) {
            Logger.error(e, "Failed to close the machine while shutting down."); //$NON-NLS-1$
        }
        if (again) {
            // Once the machine has let go of its port and cameras, which the new one opens.
            try {
                org.openpnp.util.Relaunch.start();
            }
            catch (Exception e) {
                Logger.error(e, "Could not start again: start Pono by hand."); //$NON-NLS-1$
            }
        }
        Logger.info("Shutdown complete, exiting."); //$NON-NLS-1$
        System.exit(0);
    }

    public void setStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            statusBarPanel.setStatus(status);
        });
    }

    /** The pill at the left of the status bar: what mode the window is in. */
    public void setStatusState(String text, Chip.Tone tone) {
        SwingUtilities.invokeLater(() -> statusBarPanel.setState(text, tone));
    }

    public StatusBarPanel getStatusBar() {
        return statusBarPanel;
    }
    
    public void setPlacementCompletionStatus(int totalPlacementsCompleted, int totalPlacements, int boardPlacementsCompleted, int boardPlacements) {
        SwingUtilities.invokeLater(() -> {
            statusBarPanel.setProgress(totalPlacementsCompleted, totalPlacements,
                    boardPlacementsCompleted, boardPlacements);
            topBarPanel.setProgress(totalPlacementsCompleted, totalPlacements);
        });
    }

    /**
     * Every menu item and every page, found by typing part of its name. Built on each opening,
     * because which items are enabled changes with the machine's state.
     */
    private void openCommandPalette() {
        List<CommandPalette.Command> commands = new ArrayList<>();
        for (Component page : navigationRail.getPageComponents()) {
            String name = navigationRail.getLabel(page);
            commands.add(new CommandPalette.Command(
                    Translations.getString("CommandPalette.Path.Pages"), name, //$NON-NLS-1$
                    () -> showTab(page)));
        }
        commands.addAll(CommandPalette.commandsOf(menuBar));
        new CommandPalette(this, commands).open();
    }

    /**
     * Show the page a panel lives on. Takes the panel rather than its title, which is translated
     * and so could only ever be matched by a caller that knew the display language.
     */
    public void showTab(Component page) {
        if (page == machineSetupPanel && machineSettingsPanel != null) {
            navigationRail.setSelectedComponent(machineSettingsPanel);
            machineSettingsPanel.showAdvanced();
            return;
        }
        navigationRail.setSelectedComponent(page);
    }

    /**
     * A page changed what it is laid out as, as the machine settings page does between its topics
     * and its tree: the camera and the properties column follow if it is on show.
     */
    public void pageLayoutChanged(Component page) {
        if (navigationRail == null || navigationRail.getSelectedComponent() != page) {
            return;
        }
        applyPageLayout(page);
        if (jogCard != null) {
            jogCard.setPage(pageKey(page), page == feedersPanel);
        }
    }

    /** The key a page's layout is kept under: its navigation key, or the one it says it is now. */
    private String pageKey(Component page) {
        if (page != null && page == machineSettingsPanel) {
            return machineSettingsPanel.layoutKey();
        }
        return pageKeys.get(page);
    }

    private ComponentListener mainFrameListener = new ComponentAdapter() {
        @Override
        public void componentMoved(ComponentEvent e) {
            prefs.putInt(PREF_WINDOW_X, getLocation().x);
            prefs.putInt(PREF_WINDOW_Y, getLocation().y);
        }

        @Override
        public void componentResized(ComponentEvent e) {
            prefs.putInt(PREF_WINDOW_WIDTH, getSize().width);
            prefs.putInt(PREF_WINDOW_HEIGHT, getSize().height);
            if (!windowStyleMultiple) {
                /* when resizing then divider is limited apparently by panel constraints
                   but they are somehow magically changed constraining e.g. jog panel
                   is not reflected by outer panel as expected.
                */
                Dimension size = splitPaneMachineAndTabs.getSize();
                Dimension dim = splitPaneMachineAndTabs.getTopComponent().getMinimumSize();
                dim.height = (int) Math.min(Math.round(size.height * 0.1), 200);
                splitPaneMachineAndTabs.getTopComponent().setMinimumSize(dim);
                dim = splitPaneMachineAndTabs.getBottomComponent().getMinimumSize();
                dim.height = (int) Math.min(Math.round(size.height * 0.1), 150);
                splitPaneMachineAndTabs.getBottomComponent().setMinimumSize(dim);
            }
        }
    };

    private ComponentListener cameraWindowListener = new ComponentAdapter() {
        @Override
        public void componentMoved(ComponentEvent e) {
            prefs.putInt(PREF_CAMERA_WINDOW_X, frameCamera.getLocation().x);
            prefs.putInt(PREF_CAMERA_WINDOW_Y, frameCamera.getLocation().y);
        }

        @Override
        public void componentResized(ComponentEvent e) {
            prefs.putInt(PREF_CAMERA_WINDOW_WIDTH, frameCamera.getSize().width);
            prefs.putInt(PREF_CAMERA_WINDOW_HEIGHT, frameCamera.getSize().height);
        }
    };

    
    private Action inchesUnitSelected = new AbstractAction(LengthUnit.Inches.name()) {
        {
            putValue(MNEMONIC_KEY, KeyEvent.VK_I);
        }
        
        @Override
        public void actionPerformed(ActionEvent arg0) {
            configuration.setSystemUnits(LengthUnit.Inches);
            MessageBoxes.infoBox(Translations.getString("CommonWords.notice"), //$NON-NLS-1$
                  Translations.getString("CommonPhrases.restartToTakeEffect")); //$NON-NLS-1$
      }
    };

    private Action millimetersUnitSelected = new AbstractAction(LengthUnit.Millimeters.name()) {
        {
            putValue(MNEMONIC_KEY, KeyEvent.VK_M);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            configuration.setSystemUnits(LengthUnit.Millimeters);
            MessageBoxes.infoBox(Translations.getString("CommonWords.notice"), //$NON-NLS-1$
                    Translations.getString("CommonPhrases.restartToTakeEffect")); //$NON-NLS-1$
        }
    };

    private Action tablesUnlinkedSelected = new AbstractAction(TablesLinked.Unlinked.name()) {
        @Override
        public void actionPerformed(ActionEvent arg0) {
            configuration.setTablesLinked(TablesLinked.Unlinked);
        }
    };

    private Action tablesLinkedSelected = new AbstractAction(TablesLinked.Linked.name()) {
        @Override
        public void actionPerformed(ActionEvent arg0) {
            configuration.setTablesLinked(TablesLinked.Linked);
        }
    };

    private Action windowStyleMultipleSelected = new AbstractAction(Translations.getString("Menu.Window.MultipleStyle")) { //$NON-NLS-1$
        {
            putValue(MNEMONIC_KEY, KeyEvent.VK_M);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            boolean multiple = arg0 != null && arg0.getSource() instanceof javax.swing.AbstractButton
                    && ((javax.swing.AbstractButton) arg0.getSource()).isSelected();
            prefs.putBoolean(PREF_WINDOW_STYLE_MULTIPLE, multiple);
            MessageBoxes.infoBox(Translations.getString("CommonPhrases.windowsStyleChanged"), //$NON-NLS-1$
                    Translations.getString("CommonPhrases.windowsStyleChangedRestartToTakeEffect")); //$NON-NLS-1$
        }
    };

    /** The settings page, where the appearance dialog used to open. */
    private Action editThemeAction = new AbstractAction(Translations.getString("Menu.Window.Theme")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            showSettings();
        }
    };

    private SettingsPanel settingsPanel;

    public void showSettings() {
        if (settingsPanel != null) {
            navigationRail.setSelectedComponent(settingsPanel);
        }
    }

    /** The keyboard shortcuts, as Help lists them; for the settings page. */
    public void showHotkeys() {
        hotkeysAction.actionPerformed(null);
    }

    /** What this is, as Help says it; for the settings page. */
    public void showAbout() {
        aboutAction.actionPerformed(null);
    }

    private Action saveConfigAction = new AbstractAction(Translations.getString("Menu.File.SaveConfiguration")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
			saveConfig();
        }
    };

    private Action quitAction = new AbstractAction(Translations.getString("Menu.File.Exit")) { //$NON-NLS-1$
        {
            putValue(MNEMONIC_KEY, KeyEvent.VK_X);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            quit();
        }
    };

    private Action aboutAction = new AbstractAction(Translations.getString("Menu.Help.About")) { //$NON-NLS-1$
        {
            putValue(MNEMONIC_KEY, KeyEvent.VK_A);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            about();
        }
    };
    
    private Action checkForUpdatesAction = new AbstractAction(Translations.getString("Menu.Help.CheckForUpdates")) { //$NON-NLS-1$
        {
            putValue(MNEMONIC_KEY, KeyEvent.VK_U);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            try {
                Class applicationLauncher = Class.forName("com.install4j.api.launcher.ApplicationLauncher"); //$NON-NLS-1$
                Class callback = Class.forName("com.install4j.api.launcher.ApplicationLauncher$Callback"); //$NON-NLS-1$
                Method launchApplication = applicationLauncher.getMethod("launchApplication", String.class, String[].class, boolean.class, callback); //$NON-NLS-1$
                launchApplication.invoke(null, "125", null, false, null); //$NON-NLS-1$
            }
            catch (Exception e) {
                MessageBoxes.errorBox(MainFrame.this,
                        Translations.getString("MainFrame.CheckForUpdates.Error.Title"), e); //$NON-NLS-1$
            }
        }
    };
    
    private Action quickStartLinkAction = new AbstractAction(Translations.getString("Menu.Help.QuickStart")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.browseUri("https://github.com/openpnp/openpnp/wiki/Quick-Start"); //$NON-NLS-1$
        }
    };
    
    private Action setupAndCalibrationLinkAction = new AbstractAction(Translations.getString("Menu.Help.SetupAndCalibration")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.browseUri("https://github.com/openpnp/openpnp/wiki/Setup-and-Calibration"); //$NON-NLS-1$
        }
    };
    
    /** Also the welcome window's "manual" link. */
    public final Action userManualLinkAction = new AbstractAction(Translations.getString("Menu.Help.UserManual")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.browseUri("https://github.com/openpnp/openpnp/wiki/User-Manual"); //$NON-NLS-1$
        }
    };
    
    private Action changeLogAction = new AbstractAction(Translations.getString("Menu.Help.ChangeLog")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.browseUri(Main.getSourceUri()+"CHANGES.md"); //$NON-NLS-1$
        }
    };
    
    private Action submitDiagnosticsAction = new AbstractAction(Translations.getString("Menu.Help.SubmitDiagnostics")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            SubmitDiagnosticsDialog dialog = new SubmitDiagnosticsDialog();
            dialog.setModal(true);
            dialog.setSize(620, 720);
            dialog.setLocationRelativeTo(MainFrame.get());
            dialog.setVisible(true);
        }
    };
    
    public final Action undoAction = new AbstractAction(Translations.getString("Menu.Edit.Undo")) { //$NON-NLS-1$
        {
            putValue(MNEMONIC_KEY, KeyEvent.VK_Z);
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke('Z',
                    Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            try {
                undoManager.undo();
            }
            catch (Exception e) {
                Logger.debug(e, "Nothing was undone."); //$NON-NLS-1$
            }
        }
    };
    
    public final Action redoAction = new AbstractAction(Translations.getString("Menu.Edit.Redo")) { //$NON-NLS-1$
        {
//            putValue(MNEMONIC_KEY, KeyEvent.VK_Y);
            putValue(ACCELERATOR_KEY, KeyStroke.getKeyStroke('Z',
                    Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() | KeyEvent.SHIFT_MASK));
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            try {
                undoManager.redo();
            }
            catch (Exception e) {
                Logger.debug(e, "Nothing was redone."); //$NON-NLS-1$
            }
        }
    };
    
    public class LanguageSelectionAction extends AbstractAction {
        private final Locale locale;
        
        public LanguageSelectionAction(Locale locale) {
            this.locale = locale;
            this.putValue(NAME, locale.getDisplayName());
        }
        
        public Locale getLocale() {
            return locale;
        }
        
        public void actionPerformed(ActionEvent arg0) {
            configuration.setLocale(locale);
            MessageBoxes.infoBox(Translations.getString("CommonWords.notice"), //$NON-NLS-1$
                    Translations.getString("CommonPhrases.restartToTakeEffect")); //$NON-NLS-1$
      }
    }
    
    private TopBarPanel topBarPanel;
    private StatusBarPanel statusBarPanel;
    private JLabel labelIcon;
}
