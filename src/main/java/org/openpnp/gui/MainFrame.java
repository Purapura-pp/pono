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
import org.openpnp.gui.shell.CameraStage;
import org.openpnp.gui.shell.CameraToolsBar;
import org.openpnp.gui.shell.PillBar;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.CommandPalette;
import org.openpnp.gui.shell.DroPanel;
import org.openpnp.gui.shell.InspectorPanel;
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
    private static final String PREF_CAMERA_DIVIDER_POSITION = "MainFrame.cameraDividerPosition"; //$NON-NLS-1$
    /**
     * A share of the height rather than a number of pixels, because the floating controls are a
     * fixed size: a fraction leaves the image room to grow with the window, where 400 pixels on a
     * laptop is a strip of image with the jog card sitting on all of it.
     */
    private static final double CAMERA_HEIGHT_SHARE = 0.55;
    private static final String PREF_INSPECTOR_COLLAPSED = "MainFrame.inspectorCollapsed"; //$NON-NLS-1$
    private static final boolean PREF_INSPECTOR_COLLAPSED_DEF = false;
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
    private IssuesAndSolutionsPanel issuesAndSolutionsPanel;
    private VisionSettingsPanel visionSettingsPanel;
    private JDialog frameCamera;
    private Map<KeyStroke, Action> hotkeyActionMap;
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

    public MachineSetupPanel getMachineSetupTab() {
        return machineSetupPanel;
    }

    public IssuesAndSolutionsPanel getIssuesAndSolutionsTab() {
        return issuesAndSolutionsPanel;
    }

    private JPanel contentPane;
    private NavigationRail navigationRail;
    private InspectorPanel inspectorPanel;
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
    private JMenu mnWindows;

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

    private Chip unitsPerPixelChip;

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
        int width = inspectorPanel.isCollapsed() ? InspectorPanel.COLLAPSED_WIDTH
                : prefs.getInt(PREF_INSPECTOR_WIDTH, InspectorPanel.PREFERRED_WIDTH);
        splitPaneInspector.setDividerLocation(total - width - splitPaneInspector.getDividerSize());
    }

    /**
     * One rail item, labelled from its own short key and explained by the tab title it replaces.
     */
    private void addNavigation(String key, Icon icon, Component page) {
        navigationRail.addPage(
                Translations.getString("MainFrame.Navigation." + key), //$NON-NLS-1$
                Translations.getString("MainFrame.RightComponent.tabs." + key), //$NON-NLS-1$
                icon, page);
    }

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

        // Ensure the window is within the bounds of a screen.
        Rectangle windowBounds = getBounds();
        boolean isWithinScreen = false;
        for (GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle screenBounds = gd.getDefaultConfiguration().getBounds();
            if (windowBounds.intersects(screenBounds)) {
                isWithinScreen = true;
                break;
            }
        }
        if (!isWithinScreen) {
        	// If the window is not within any screen, reset it to the default position.
            setBounds(PREF_WINDOW_X_DEF, PREF_WINDOW_Y_DEF, PREF_WINDOW_WIDTH_DEF, PREF_WINDOW_HEIGHT_DEF);
        }
        jobPanel = new JobPanel(configuration, this);
        panelsPanel = new PanelsPanel(configuration, this);
        boardsPanel = new BoardsPanel(configuration, this);
        partsPanel = new PartsPanel(configuration, this);
        packagesPanel = new PackagesPanel(configuration, this);
        feedersPanel = new FeedersPanel(configuration, this);
        machineSetupPanel = new MachineSetupPanel(configuration);
        issuesAndSolutionsPanel = new IssuesAndSolutionsPanel(configuration, this);
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

        mnEdit.add(new JMenuItem(undoAction));
        mnEdit.add(new JMenuItem(redoAction));
        mnEdit.addSeparator();
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

        // View -> System Units
        ButtonGroup buttonGroup = new ButtonGroup();
        JMenu mnUnits = new JMenu(Translations.getString("Menu.View.SystemUnits")); //$NON-NLS-1$
        mnUnits.setMnemonic(KeyEvent.VK_S);
        mnView.add(mnUnits);

        JMenuItem menuItem;
        menuItem = new JCheckBoxMenuItem(inchesUnitSelected);
        buttonGroup.add(menuItem);
        if (configuration.getSystemUnits() == LengthUnit.Inches) {
            menuItem.setSelected(true);
        }
        mnUnits.add(menuItem);
        menuItem = new JCheckBoxMenuItem(millimetersUnitSelected);
        buttonGroup.add(menuItem);
        if (configuration.getSystemUnits() == LengthUnit.Millimeters) {
            menuItem.setSelected(true);
        }
        mnUnits.add(menuItem);
        
        // View -> Tables Linked
        buttonGroup = new ButtonGroup();
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
        
        // View -> Language
        buttonGroup = new ButtonGroup();
        JMenu mnLanguage = new JMenu(Translations.getString("Menu.View.Language")); //$NON-NLS-1$
        mnView.add(mnLanguage);

        // Discovered from the bundles that are present, so adding a language does not mean
        // editing this list. See Translations.getAvailableLocales.
        Locale selectedLocale = configuration.getLocale();
        List<Locale> locales = new ArrayList<>(Translations.getAvailableLocales());
        Collator collator = Collator.getInstance();
        locales.sort((a, b) -> collator.compare(a.getDisplayName(), b.getDisplayName()));
        for (Locale locale : locales) {
            menuItem = new JCheckBoxMenuItem(new LanguageSelectionAction(locale));
            buttonGroup.add(menuItem);
            mnLanguage.add(menuItem);
            if (locale.equals(selectedLocale)) {
                menuItem.setSelected(true);
            }
        }
        

        // Job
        //////////////////////////////////////////////////////////////////////
        JMenu mnJob = new JMenu(Translations.getString("Menu.Job")); //$NON-NLS-1$
        mnJob.setMnemonic(KeyEvent.VK_J);
        menuBar.add(mnJob);

        mnJob.add(new JMenuItem(jobPanel.startPauseResumeJobAction));
        mnJob.add(new JMenuItem(jobPanel.stepJobAction));
        mnJob.add(new JMenuItem(jobPanel.stopJobAction));
        
        mnJob.addSeparator();
        
        mnJob.add(new JMenuItem(jobPanel.resetAllPlacedAction));

        // Machine
        //////////////////////////////////////////////////////////////////////
        JMenu mnCommands = new JMenu(Translations.getString("Menu.Machine")); //$NON-NLS-1$
        mnCommands.setMnemonic(KeyEvent.VK_M);
        menuBar.add(mnCommands);
        mnCommands.addSeparator();

        // Scripts
        /////////////////////////////////////////////////////////////////////
        mnScripts = new JMenu(Translations.getString("Menu.Scripts")); //$NON-NLS-1$
        mnScripts.setMnemonic(KeyEvent.VK_S);
        menuBar.add(mnScripts);

        // Windows
        /////////////////////////////////////////////////////////////////////
        mnWindows = new JMenu(Translations.getString("Menu.Window")); //$NON-NLS-1$
        mnWindows.setMnemonic(KeyEvent.VK_W);
        menuBar.add(mnWindows);

        JCheckBoxMenuItem windowStyleMultipleMenuItem =
                new JCheckBoxMenuItem(windowStyleMultipleSelected);
        mnWindows.add(windowStyleMultipleMenuItem);
        if (windowStyleMultiple) {
            windowStyleMultipleMenuItem.setSelected(true);
        }

        mnWindows.add(new JMenuItem(editThemeAction));

        // Help
        /////////////////////////////////////////////////////////////////////
        JMenu mnHelp = new JMenu(Translations.getString("Menu.Help")); //$NON-NLS-1$
        mnHelp.setMnemonic(KeyEvent.VK_H);
        menuBar.add(mnHelp);
        if (!macOsXMenus) {
            mnHelp.add(new JMenuItem(aboutAction));
        }
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
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
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
        splitPaneInspector.setBorder(null);
        splitPaneInspector.setContinuousLayout(true);
        splitPaneInspector.setResizeWeight(1.0);
        splitPaneInspector.setLeftComponent(splitPaneMachineAndTabs);
        contentPane.add(splitPaneInspector, BorderLayout.CENTER);

        panelMachine = new JPanel();
        splitPaneMachineAndTabs.setLeftComponent(panelMachine);
        panelMachine.setLayout(new BorderLayout(0, 0));

        // Add global hotkeys for the arrow keys
        hotkeyActionMap = new HashMap<>();

        Toolkit.getDefaultToolkit().getSystemEventQueue().push(new EventQueue() {
            @Override
            protected void dispatchEvent(AWTEvent event) {
                if (event instanceof KeyEvent) {
                    // Skip hotkey processing if a text input component has focus.
                    // This prevents accidental machine motion when editing text
                    // (e.g., using Ctrl+Shift+Arrow to select words).
                    if (!UiUtils.isTextInputFocused()) {
                        KeyStroke ks = KeyStroke.getKeyStrokeForEvent((KeyEvent) event);
                        Action action = hotkeyActionMap.get(ks);
                        if (action != null && action.isEnabled()) {
                            action.actionPerformed(null);
                            return;
                        }
                    }
                }
                super.dispatchEvent(event);
            }
        });
        cameraPanel = new CameraPanel();

        // The stage is the camera image with everything that belongs to it floating on top. It is
        // what the multiple-windows mode pops out, hence the wrapper panel it lives in.
        panelCameraAndInstructions = new JPanel();
        panelCameraAndInstructions.setLayout(new BorderLayout(0, 0));
        panelMachine.add(panelCameraAndInstructions, BorderLayout.CENTER);

        panelInstructions = new JPanel();
        panelInstructions.setVisible(false);
        panelInstructions.setBorder(new EmptyBorder(4, 4, 4, 4));
        panelInstructions.setLayout(new BorderLayout(0, 0));

        // The wizard sets this per step, which is what the etched border's title used to carry.
        lblInstructionsTitle = new JLabel(Translations.getString("General.Instructions")); //$NON-NLS-1$
        panelInstructions.add(lblInstructionsTitle, BorderLayout.NORTH);

        panelInstructionActions = new JPanel();
        panelInstructionActions.setAlignmentY(Component.BOTTOM_ALIGNMENT);
        panelInstructions.add(panelInstructionActions, BorderLayout.EAST);
        panelInstructionActions.setLayout(new BorderLayout(0, 0));

        panel_2 = new JPanel();
        FlowLayout flowLayout_2 = (FlowLayout) panel_2.getLayout();
        flowLayout_2.setVgap(0);
        flowLayout_2.setHgap(0);
        panelInstructionActions.add(panel_2, BorderLayout.SOUTH);

        btnInstructionsCancel = new JButton(Translations.getString("General.Cancel")); //$NON-NLS-1$
        btnInstructionsCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                if (instructionsCancelActionListener != null) {
                    instructionsCancelActionListener.actionPerformed(arg0);
                }
            }
        });
        panel_2.add(btnInstructionsCancel);

        btnInstructionsNext = new JButton(Translations.getString("General.Next")); //$NON-NLS-1$
        btnInstructionsNext.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                if (instructionsProceedActionListener != null) {
                    instructionsProceedActionListener.actionPerformed(arg0);
                }
            }
        });
        panel_2.add(btnInstructionsNext);

        panel_1 = new JPanel();
        panelInstructions.add(panel_1, BorderLayout.CENTER);
        panel_1.setLayout(new BorderLayout(0, 0));

        lblInstructions = new JTextPane();
        // does not seem to work with html
        //lblInstructions.setFont(new Font("Lucida Grande", Font.PLAIN, 14)); //$NON-NLS-1$
        // instead use the HONOR_DISPLAY_PROPERTIES to set the proper system dialog font and size 
        lblInstructions.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        lblInstructions.setBackground(UIManager.getColor("Panel.background")); //$NON-NLS-1$
        lblInstructions.setContentType("text/html"); //$NON-NLS-1$
        lblInstructions.setEditable(false);
        panel_1.add(lblInstructions);

        labelIcon = new JLabel(); 
        labelIcon.setIcon(Icons.processActivity1Icon);
        panelInstructions.add(labelIcon, BorderLayout.WEST);

        machineControlsPanel = new MachineControlsPanel(configuration, jobPanel);
        droPanel = new DroPanel(configuration);

        mnCommands.add(new JMenuItem(machineControlsPanel.homeAction));
        mnCommands.add(new JMenuItem(machineControlsPanel.startStopMachineAction));

        int[] ctrl_shift_mask = {KeyEvent.CTRL_DOWN_MASK, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK};
        for (int mask : ctrl_shift_mask) {
            hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, mask),
                    machineControlsPanel.getJogControlsPanel().yPlusAction);
            hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, mask),
                    machineControlsPanel.getJogControlsPanel().yMinusAction);
            hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, mask),
                    machineControlsPanel.getJogControlsPanel().xMinusAction);
            hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, mask),
                    machineControlsPanel.getJogControlsPanel().xPlusAction);
            hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_QUOTE, mask),
                    machineControlsPanel.getJogControlsPanel().zPlusAction);
            hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, mask),
                    machineControlsPanel.getJogControlsPanel().zMinusAction);
            hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, mask),
                    machineControlsPanel.getJogControlsPanel().cPlusAction);
            hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, mask),
                    machineControlsPanel.getJogControlsPanel().cMinusAction);
            hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, mask),
                    machineControlsPanel.getJogControlsPanel().lowerIncrementAction);
            hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, mask),
                    machineControlsPanel.getJogControlsPanel().raiseIncrementAction);
            hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_H, mask),
                    machineControlsPanel.homeAction);
        }
        hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK),
                jobPanel.startPauseResumeJobAction); // Ctrl-Shift-R for Start
        hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK),
                jobPanel.stepJobAction); // Ctrl-Shift-S for Step
        hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_A, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK),
                jobPanel.stopJobAction); // Ctrl-Shift-A for Stop
        hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK),
                machineControlsPanel.getJogControlsPanel().xyParkAction); // Ctrl-Shift-P for xyPark
        hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK),
                machineControlsPanel.getJogControlsPanel().zParkAction); // Ctrl-Shift-P for zPark
        hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK),
                machineControlsPanel.getJogControlsPanel().safezAction); // Ctrl-Shift-Z for safezAction
        hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK),
                machineControlsPanel.getJogControlsPanel().discardAction); // Ctrl-Shift-D for discard
        hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F1, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK),
                machineControlsPanel.getJogControlsPanel().setIncrement1Action);
        hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK),
                machineControlsPanel.getJogControlsPanel().setIncrement2Action);
        hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F3, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK),
                machineControlsPanel.getJogControlsPanel().setIncrement3Action);
        hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F4, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK),
                machineControlsPanel.getJogControlsPanel().setIncrement4Action);
        hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK),
                machineControlsPanel.getJogControlsPanel().setIncrement5Action);
        // Ctrl-Shift-J folds the jog controls off the camera image and back. A bare J would fire
        // whenever the focus is not in a text field, which includes every table in the window.
        hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_J, KeyEvent.CTRL_DOWN_MASK | KeyEvent.SHIFT_DOWN_MASK),
                machineControlsPanel.toggleJogControlsAction);

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

        // A new key rather than MainFrame.dividerPosition: the old one holds a distance from the
        // left edge, and reading it as a distance from the top would put the divider somewhere
        // arbitrary for everyone upgrading. Until there is a stored one, the share above decides,
        // which can only be applied once the split has a height.
        int storedDivider = prefs.getInt(PREF_CAMERA_DIVIDER_POSITION, -1);
        if (storedDivider > 0) {
            splitPaneMachineAndTabs.setDividerLocation(storedDivider);
        }
        else {
            SwingUtilities.invokeLater(
                    () -> splitPaneMachineAndTabs.setDividerLocation(CAMERA_HEIGHT_SHARE));
        }
        splitPaneMachineAndTabs.addPropertyChangeListener("dividerLocation", //$NON-NLS-1$
                new PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        prefs.putInt(PREF_CAMERA_DIVIDER_POSITION,
                                splitPaneMachineAndTabs.getDividerLocation());
                    }
                });

        // The rail's own label is short enough to sit under an icon; the tab title it replaces
        // stays on as the tooltip, since that is the name the wiki and the menus use.
        // The icons are the ones the library already has. Two of them are only nearly right -
        // there is no parts icon and no gear - so they were chosen to be told apart at a glance,
        // which matters more in a rail of eleven than being the perfect metaphor.
        addNavigation("Job", Icons.place, jobPanel); //$NON-NLS-1$
        addNavigation("Feeders", Icons.feeder, feedersPanel); //$NON-NLS-1$
        addNavigation("Parts", Icons.footprintDual, partsPanel); //$NON-NLS-1$
        addNavigation("Packages", Icons.footprintQuad, packagesPanel); //$NON-NLS-1$
        addNavigation("Boards", Icons.board, boardsPanel); //$NON-NLS-1$
        addNavigation("Panels", Icons.panel, panelsPanel); //$NON-NLS-1$
        addNavigation("Vision", Icons.captureCamera, visionSettingsPanel); //$NON-NLS-1$
        navigationRail.addGap();
        addNavigation("MachineSetup", Icons.axisCartesian, machineSetupPanel); //$NON-NLS-1$
        addNavigation("IssuesAndSolutions", Icons.solutions, issuesAndSolutionsPanel); //$NON-NLS-1$
        LogPanel logPanel = new LogPanel();
        addNavigation("Log", Icons.info, logPanel); //$NON-NLS-1$
        // Settings opens the appearance dialog for now; the plan is for it to gather the settings
        // that are spread across the menus.
        navigationRail.addAction(
                Translations.getString("MainFrame.Navigation.Settings"), //$NON-NLS-1$
                Translations.getString("MainFrame.Navigation.Settings.toolTipText"), //$NON-NLS-1$
                Icons.driver, e -> ThemeDialog.showThemeDialog(MainFrame.this));
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
                }
            }});
        
        topBarPanel = new TopBarPanel(configuration, jobPanel, machineControlsPanel, menuBar,
                () -> showTab(issuesAndSolutionsPanel), this::openCommandPalette);
        contentPane.add(topBarPanel, BorderLayout.NORTH);
        hotkeyActionMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_K, KeyEvent.CTRL_DOWN_MASK),
                new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        openCommandPalette();
                    }
                });

        statusBarPanel = new StatusBarPanel(configuration);
        contentPane.add(statusBarPanel, BorderLayout.SOUTH);

        // One properties column for the whole window, where every table used to keep its own
        // below itself behind a split divider.
        inspectorPanel = new InspectorPanel();
        inspectorPanel.setMinimumSize(new Dimension(InspectorPanel.COLLAPSED_WIDTH, 0));
        inspectorPanel.setCollapsed(prefs.getBoolean(PREF_INSPECTOR_COLLAPSED,
                PREF_INSPECTOR_COLLAPSED_DEF));
        inspectorPanel.addPropertyChangeListener("collapsed", e -> { //$NON-NLS-1$
            prefs.putBoolean(PREF_INSPECTOR_COLLAPSED, inspectorPanel.isCollapsed());
            applyInspectorWidth();
        });
        inspectorPanel.setActivePage(navigationRail.getSelectedComponent());
        splitPaneInspector.setRightComponent(inspectorPanel);
        splitPaneInspector.addPropertyChangeListener("dividerLocation", evt -> { //$NON-NLS-1$
            // Only a width the user dragged to is worth remembering; the collapsed sliver and the
            // positions set while the window is still finding its size are not.
            if (!inspectorPanel.isCollapsed() && splitPaneInspector.isShowing()
                    && splitPaneInspector.getWidth() > 0) {
                int width = splitPaneInspector.getWidth() - splitPaneInspector.getDividerLocation()
                        - splitPaneInspector.getDividerSize();
                if (width > InspectorPanel.COLLAPSED_WIDTH) {
                    prefs.putInt(PREF_INSPECTOR_WIDTH, width);
                }
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
        JPanel topLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        topLeft.setOpaque(false);
        OverlayCard selectorStrip = OverlayCard.strip();
        selectorStrip.add(cameraSelector);
        topLeft.add(selectorStrip);
        unitsPerPixelChip = new Chip("", Chip.Tone.Neutral, Chip.Shape.Chip); //$NON-NLS-1$
        unitsPerPixelChip.setFont(org.openpnp.gui.shell.Ui.mono(12f, java.awt.Font.PLAIN));
        OverlayCard unitsStrip = new OverlayCard();
        unitsStrip.setLayout(new BorderLayout());
        unitsStrip.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        unitsStrip.add(unitsPerPixelChip);
        topLeft.add(unitsStrip);
        cameraPanel.addSelectionListener(this::showUnitsPerPixel);
        cameraStage.anchor(topLeft, Anchor.NorthWest);
        cameraStage.anchor(
                new CameraToolsBar(configuration, cameraPanel, this::toggleCameraFullScreen),
                Anchor.NorthEast);
        // The readout goes bottom left and the machine controls bottom right, as the mockups have
        // them; the instructions arrive at the top, over the image they are talking about.
        cameraStage.overlay(droPanel, Anchor.SouthWest);
        cameraStage.overlay(machineControlsPanel, Anchor.SouthEast);
        instructionsCard = cameraStage.overlay(panelInstructions, Anchor.North);
        instructionsCard.setVisible(false);
        panelCameraAndInstructions.add(cameraStage, BorderLayout.CENTER);

        splitPaneMachineAndTabs.setResizeWeight(0.5);

        addImporterMenuOptions();

        addComponentListener(mainFrameListener);
        
        boolean configurationLoaded = false;
        while (!configurationLoaded) {
	        try {
	            configuration.load();
	            scriptFileWatcher = new ScriptFileWatcher(configuration.getScripting());
	            scriptFileWatcher.setMenu(mnScripts);
	            
	            if (configuration.getMachine().getProperty("Welcome2_0_Dialog_Shown") == null) {
	                Welcome2_0Dialog dialog = new Welcome2_0Dialog(this);
	                dialog.setSize(750, 550);
	                dialog.setLocationRelativeTo(null);
	                dialog.setModal(true);
	                dialog.setVisible(true);
	                configuration.getMachine().setProperty("Welcome2_0_Dialog_Shown", true);
	            }
	            configurationLoaded = true;    
	        }
	        catch (Exception e) {
	            Logger.error(e, "Failed to load the configuration from {}.", //$NON-NLS-1$
	                    configuration.getConfigurationDirectory());
	            if (!MessageBoxes.errorBoxWithRetry(this,
	                    Translations.getString("MainFrame.LoadConfig.Error.Title"), //$NON-NLS-1$
	                    String.format(Translations.getString("MainFrame.LoadConfig.Error.Message"), //$NON-NLS-1$
	                            e.getMessage(),
	                            configuration.getConfigurationDirectory().getAbsolutePath()))) {
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
            // A value of 0 means the camera was in its own window last time, which left the
            // divider collapsed. Give it its share back rather than no height at all.
            if (0 == prefs.getInt(PREF_CAMERA_DIVIDER_POSITION, -1)) {
                SwingUtilities.invokeLater(
                        () -> splitPaneMachineAndTabs.setDividerLocation(CAMERA_HEIGHT_SHARE));
            }
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
        setStatusState(Translations.getString("StatusBar.State.Wizard"), Chip.Tone.Run); //$NON-NLS-1$
        setStatus(title);
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
                    labelIcon.setIcon(labelIcon.getIcon() == Icons.processActivity1Icon ? Icons.processActivity2Icon : Icons.processActivity1Icon);
                }
            }, 0, 1000, TimeUnit.MILLISECONDS);
        }
    }

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
        cameraStage.revalidate();
        cameraStage.repaint();
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
        try {
            Preferences.userRoot().flush();
        }
        catch (Exception e) {
            Logger.warn(e, "Failed to flush user preferences while shutting down."); //$NON-NLS-1$
        }

        // Save the configuration
        try {
            configuration.save();
        }
        catch (Exception e) {
            String message = String.format(
                    Translations.getString("MainFrame.QuitSaveConfig.Error.Message"), e.getMessage()); //$NON-NLS-1$
            message = message.replaceAll("\n", "<br/>"); //$NON-NLS-1$ //$NON-NLS-2$
            message = message.replaceAll("\r", ""); //$NON-NLS-1$ //$NON-NLS-2$
            message = "<html><body width=\"400\">" + message + "</body></html>"; //$NON-NLS-1$ //$NON-NLS-2$
            int result = JOptionPane.showConfirmDialog(this, message,
                    Translations.getString("MainFrame.SaveConfig.Error.Title"), //$NON-NLS-1$
                    JOptionPane.YES_NO_OPTION);
            if (result != JOptionPane.YES_OPTION) {
                return false;
            }
        }
        if (!jobPanel.checkForModifications()) {
            return false;
        }
        // Attempt to stop the machine on quit
        try {
            configuration.getMachine().setEnabled(false);
        }
        catch (Exception e) {
            Logger.error(e, "Failed to disable the machine while shutting down."); //$NON-NLS-1$
        }
        // Attempt to stop the machine on quit
        try {
            configuration.getMachine().close();
        }
        catch (Exception e) {
            Logger.error(e, "Failed to close the machine while shutting down."); //$NON-NLS-1$
        }
        Logger.info("Shutdown complete, exiting."); //$NON-NLS-1$
        System.exit(0);
        return true;
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
        navigationRail.setSelectedComponent(page);
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
            if (mnWindows.getItem(0).isSelected()) {
                prefs.putBoolean(PREF_WINDOW_STYLE_MULTIPLE, true);
            }
            else {
                prefs.putBoolean(PREF_WINDOW_STYLE_MULTIPLE, false);
            }
            MessageBoxes.infoBox(Translations.getString("CommonPhrases.windowsStyleChanged"), //$NON-NLS-1$
                    Translations.getString("CommonPhrases.windowsStyleChangedRestartToTakeEffect")); //$NON-NLS-1$
        }
    };

    private Action editThemeAction = new AbstractAction(Translations.getString("Menu.Window.Theme")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            ThemeDialog.showThemeDialog(mainFrame);
        }
    };

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
    
    private Action userManualLinkAction = new AbstractAction(Translations.getString("Menu.Help.UserManual")) { //$NON-NLS-1$
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
