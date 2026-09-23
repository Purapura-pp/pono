/*
 * Copyright (C) 2023 Jason von Nieda <jason@vonnieda.org>, Tony Luken <tonyluken62+openpnp@gmail.com>
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

import java.awt.BorderLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

import org.openpnp.Translations;
import org.openpnp.events.DefinitionStructureChangedEvent;
import org.openpnp.events.PlacementSelectedEvent;
import org.openpnp.events.PlacementsHolderLocationSelectedEvent;
import org.openpnp.gui.components.AutoSelectTextTable;
import org.openpnp.gui.components.ExistingBoardOrPanelDialog;
import org.openpnp.gui.panelization.ChildFiducialSelectorDialog;
import org.openpnp.gui.panelization.PanelArrayBuilderDialog;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.gui.shell.DockPanel;
import org.openpnp.gui.shell.DockRenderers;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.ActionGroup;
import org.openpnp.gui.support.FileDialogs;
import org.openpnp.gui.support.Helpers;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.IdentifiableListCellRenderer;
import org.openpnp.gui.support.IdentifiableTableCellRenderer;
import org.openpnp.gui.support.LengthCellValue;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.gui.support.MonospacedFontTableCellRenderer;
import org.openpnp.gui.support.PartsComboBoxModel;
import org.openpnp.gui.support.RotationCellValue;
import org.openpnp.gui.support.TableUtils;
import org.openpnp.gui.tablemodel.PlacementsHolderLocationsTableModel;
import org.openpnp.gui.tablemodel.PlacementsHolderPlacementsTableModel;
import org.openpnp.gui.viewers.PlacementsHolderLocationViewerDialog;
import org.openpnp.model.Abstract2DLocatable.Side;
import org.openpnp.model.Board;
import org.openpnp.model.BoardLocation;
import org.openpnp.model.Configuration;
import org.openpnp.model.Configuration.TablesLinked;
import org.openpnp.model.Location;
import org.openpnp.model.Panel;
import org.openpnp.model.PanelLocation;
import org.openpnp.model.Part;
import org.openpnp.model.Placement;
import org.openpnp.model.PlacementsHolder;
import org.openpnp.model.PlacementsHolderLocation;
import org.openpnp.util.IdentifiableList;
import org.pmw.tinylog.Logger;

import java.awt.FileDialog;
import java.awt.Frame;

/**
 * The selected panel's definition on the panels page, as mockup 10 draws it: one dock with the
 * children and the panel's fiducials as its two tabs, Save and how much is unsaved above both,
 * and a toolbar with words on it in each. It was a titled box holding a split of two more titled
 * boxes, each with a row of unlabelled icons, and removing a child or a fiducial asked nothing.
 */
@SuppressWarnings("serial")
public class PanelDefinitionPanel extends JPanel implements PropertyChangeListener {
    private Preferences prefs = Preferences.userNodeForPackage(PanelDefinitionPanel.class);
    
    private AutoSelectTextTable fiducialTable;
    private PlacementsHolderPlacementsTableModel fiducialTableModel;
    private TableRowSorter<PlacementsHolderPlacementsTableModel> fiducialTableSorter;
    
    private ActionGroup fiducialSingleSelectionActionGroup;
    private ActionGroup fiducialMultiSelectionActionGroup;
    
    private AutoSelectTextTable childrenTable;
    private PlacementsHolderLocationsTableModel childrenTableModel;
    private TableRowSorter<PlacementsHolderLocationsTableModel> childrenTableSorter;
    
    private ActionGroup childrenSingleSelectionActionGroup;
    private ActionGroup childrenMultiSelectionActionGroup;
    private ActionGroup replaceChildrenSelectionActionGroup;
    
    private PanelLocation rootPanelLocation = new PanelLocation();
    private Panel panel;
    private PanelsPanel panelsPanel;
    
    private PlacementsHolderLocationViewerDialog panelViewer;

    private MainFrame frame;
    private Configuration configuration;
    private boolean dirty;

    private DockPanel.Tab childrenTab;
    private DockPanel.Tab fiducialsTab;
    private JButton addChildButton;
    private JTextField childrenFilter;
    /** "Modified · 2 places", beside Save, while the panel has changes that are not saved. */
    private final Chip modified = new Chip("", Chip.Tone.Warn, Chip.Shape.Status); //$NON-NLS-1$
    private final UnsavedChanges unsaved = new UnsavedChanges(this::updateSaveState);

    public PanelDefinitionPanel(Configuration configuration, PanelsPanel panelsPanel) {
    	this.configuration = configuration;
    	this.panelsPanel = panelsPanel;
    	frame = MainFrame.get();
        createUi();
        addChildAction.setEnabled(false);
        addChildButton.setEnabled(false);
        viewerAction.setEnabled(false);
        addFiducialAction.setEnabled(false);
        useChildFiducialAction.setEnabled(false);
        updateSaveState();
    }
    
    private void createUi() {
        setOpaque(false);
        
        fiducialSingleSelectionActionGroup = new ActionGroup(removeFiducialAction, setSideAction, 
                setEnabledAction);
        fiducialSingleSelectionActionGroup.setEnabled(false);

        fiducialMultiSelectionActionGroup = new ActionGroup(removeFiducialAction, setSideAction,
                setEnabledAction);
        fiducialMultiSelectionActionGroup.setEnabled(false);

        childrenSingleSelectionActionGroup = new ActionGroup(removeChildAction, setSideAction,  
                setEnabledAction, setCheckFidsAction, createArrayAction);
        childrenSingleSelectionActionGroup.setEnabled(false);

        childrenMultiSelectionActionGroup = new ActionGroup(removeChildAction, setSideAction,
                setEnabledAction, setCheckFidsAction);
        childrenMultiSelectionActionGroup.setEnabled(false);

        replaceChildrenSelectionActionGroup = new ActionGroup(replaceChildrenAction);
        replaceChildrenSelectionActionGroup.setEnabled(false);
        
        @SuppressWarnings({"unchecked", "rawtypes"})
        JComboBox<PartsComboBoxModel> partsComboBox = new JComboBox(new PartsComboBoxModel());
        partsComboBox.setMaximumRowCount(20);
        partsComboBox.setRenderer(new IdentifiableListCellRenderer<Part>());
        @SuppressWarnings({"unchecked", "rawtypes"})
        JComboBox<Side> sidesComboBox = new JComboBox(Side.values());
        
        setLayout(new BorderLayout(0, 0));

        // ---- the children ----------------------------------------------------------------------

        childrenTableModel = new PlacementsHolderLocationsTableModel(configuration) {
            
            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return columnIndex <= 1 || columnIndex >= 4;
            }

            // Numbers right and the rest left, as in every dock table; the model centres them
            // all for the job's table.
            @Override
            public int[] getColumnAlignments() {
                return new int[] {LEFT, LEFT, RIGHT, RIGHT, LEFT, RIGHT, RIGHT, RIGHT, RIGHT, LEFT, LEFT};
            }
        };
        childrenTableModel.setRootPanelLocation(rootPanelLocation);
        childrenTableSorter = new TableRowSorter<>(childrenTableModel);
        
        childrenTable = new AutoSelectTextTable(childrenTableModel);
        // Enter edits the cell, Delete removes the selected children, which asks first.
        TableUtils.bindKeys(childrenTable, removeChildAction);
        AutoSelectTextTable.setEmptyText(childrenTable,
                Translations.getString("PanelDefinition.Children.Empty")); //$NON-NLS-1$
        TableColumnModel tcm = childrenTable.getColumnModel();
        // Z is always nought for a child, and the size is the child's own, shown on its page.
        tcm.removeColumn(tcm.getColumn(7));
        tcm.removeColumn(tcm.getColumn(3));
        tcm.removeColumn(tcm.getColumn(2));
        // The mockup's order: the side after the angle.
        tcm.moveColumn(2, 5);
        
        childrenTable.setRowSorter(childrenTableSorter);
        childrenTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        childrenTable.setDefaultEditor(Side.class, new DefaultCellEditor(sidesComboBox));
        childrenTable.setDefaultRenderer(LengthCellValue.class, new MonospacedFontTableCellRenderer());
        childrenTable.setDefaultRenderer(RotationCellValue.class, new MonospacedFontTableCellRenderer());
        childrenTable.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        
        TableUtils.setColumnAlignment(childrenTableModel, childrenTable);
        
        TableUtils.installColumnWidthSavers(childrenTable, prefs, "PanelDefinitionPanel.childrenTable"); //$NON-NLS-1$
        
        childrenTable.getModel().addTableModelListener(e -> SwingUtilities.invokeLater(() -> {
            fiducialTableModel.fireTableDataChanged();
        }));

        childrenTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            
            boolean updateLinkedTables = MainFrame.get().getNavigation().getSelectedComponent() == MainFrame.get().getPanelsTab() 
                    && configuration.getTablesLinked() == TablesLinked.Linked;

            List<PlacementsHolderLocation<?>> selections = getChildrenSelections();
            if (selections.size() > 1) {
                // multi select
                childrenSingleSelectionActionGroup.setEnabled(false);
                childrenMultiSelectionActionGroup.setEnabled(true);
                boolean allSameDefinition = true;
                boolean starting = true;
                PlacementsHolder<?> definition = null;
                for (PlacementsHolderLocation<?> phl : selections) {
                    if (starting) {
                        definition = phl.getPlacementsHolder().getDefinition();
                        starting = false;
                    }
                    else {
                        if (phl.getPlacementsHolder().getDefinition() != definition) {
                            allSameDefinition = false;
                            break;
                        }
                    }
                }
                replaceChildrenSelectionActionGroup.setEnabled(allSameDefinition);
                if (updateLinkedTables) {
                    configuration.getBus()
                        .post(new PlacementsHolderLocationSelectedEvent(null, PanelDefinitionPanel.this));
                    configuration.getBus()
                        .post(new PlacementSelectedEvent(null, null, PanelDefinitionPanel.this));
                }
            }
            else if (selections.size() == 1) {
                // single select
                childrenMultiSelectionActionGroup.setEnabled(false);
                childrenSingleSelectionActionGroup.setEnabled(true);
                replaceChildrenSelectionActionGroup.setEnabled(true);
                if (updateLinkedTables) {
                    configuration.getBus()
                        .post(new PlacementsHolderLocationSelectedEvent(selections.get(0), PanelDefinitionPanel.this));
                    configuration.getBus()
                        .post(new PlacementSelectedEvent(null, selections.get(0), PanelDefinitionPanel.this));
                }
            }
            else {
                // no select
                childrenSingleSelectionActionGroup.setEnabled(false);
                childrenMultiSelectionActionGroup.setEnabled(false);
                replaceChildrenSelectionActionGroup.setEnabled(false);
                if (updateLinkedTables) {
                    configuration.getBus()
                        .post(new PlacementsHolderLocationSelectedEvent(null, PanelDefinitionPanel.this));
                    configuration.getBus()
                        .post(new PlacementSelectedEvent(null, null, PanelDefinitionPanel.this));
                }
            }
        });
        childrenTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (e.getKeyChar() == ' ') {
                    PlacementsHolderLocation<?> child = getChildrenSelection();
                    if (child != null) {
                        child.setLocallyEnabled(!child.isLocallyEnabled());
                        refreshSelectedRow();
                    }
                }
                else {
                    super.keyTyped(e);
                }
            }
        });

        // The same settings for many children at once; each is changed in its cell too.
        JPopupMenu childrenPopupMenu = new JPopupMenu();

        JMenuItem changeChildrenMenu = new JMenuItem(replaceChildrenAction);
        childrenPopupMenu.add(changeChildrenMenu);
        
        JMenu setChildrenSideMenu = new JMenu(setSideAction);
        for (Side side : Side.values()) {
            setChildrenSideMenu.add(new SetChildrenSideAction(side));
        }
        childrenPopupMenu.add(setChildrenSideMenu);

        JMenu setChildrenEnabledMenu = new JMenu(setEnabledAction);
        setChildrenEnabledMenu.add(new SetChildrenEnabledAction(true));
        setChildrenEnabledMenu.add(new SetChildrenEnabledAction(false));
        childrenPopupMenu.add(setChildrenEnabledMenu);
        
        JMenu setChildrenCheckFidsMenu = new JMenu(setCheckFidsAction);
        setChildrenCheckFidsMenu.add(new SetCheckFidsAction(true));
        setChildrenCheckFidsMenu.add(new SetCheckFidsAction(false));
        childrenPopupMenu.add(setChildrenCheckFidsMenu);
        
        childrenTable.setComponentPopupMenu(childrenPopupMenu);

        JPanel childrenPage = new JPanel(new BorderLayout());
        childrenPage.setOpaque(false);
        childrenPage.add(DockPanel.table(childrenTable), BorderLayout.CENTER);
        // The mockup's cells: the id in bold, the side's badge with its chevron, enabled as a
        // switch and the fiducial check as a check.
        childrenTable.setDefaultRenderer(Side.class, DockRenderers.dropdown(DockRenderers.side()));
        columnRenderer(childrenTable, 0, DockRenderers.bold());
        columnRenderer(childrenTable, 9, DockRenderers.toggle());
        columnRenderer(childrenTable, 10, DockRenderers.check());

        DockPanel.Toolbar childrenTools = new DockPanel.Toolbar();
        addChildButton = childrenTools.menu("Dock.Action.AddChild", "plus", this::addChildMenu); //$NON-NLS-1$ //$NON-NLS-2$
        addChildButton.setToolTipText(Translations.getString("PanelDefinition.Children.Add.Description")); //$NON-NLS-1$
        childrenTools.button(createArrayAction, "grid", "Dock.Action.CreateArray"); //$NON-NLS-1$ //$NON-NLS-2$
        childrenTools.iconButton(removeChildAction, "trash"); //$NON-NLS-1$
        childrenTools.separator();
        childrenTools.button(viewerAction, "eye", "Dock.Action.BoardView"); //$NON-NLS-1$ //$NON-NLS-2$
        childrenTools.more(replaceChildrenAction);
        childrenTools.glue();
        childrenFilter = childrenTools.filter(Translations.getString("PanelDefinition.Children.Filter")); //$NON-NLS-1$
        childrenFilter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void removeUpdate(DocumentEvent e) {
                filterChildren();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                filterChildren();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterChildren();
            }
        });
        childrenPage.add(childrenTools, BorderLayout.NORTH);

        // ---- the panel's fiducials -------------------------------------------------------------

        fiducialTableModel = new PlacementsHolderPlacementsTableModel(configuration, this) {
            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                if (!super.isCellEditable(rowIndex, columnIndex)) {
                    return false;
                }
                if (getRowObjectAt(rowIndex).getId().contains(PanelLocation.ID_DELIMITTER) && columnIndex > 0) {
                    return false;
                }
                return true;
            }
        };
        fiducialTableSorter = new TableRowSorter<>(fiducialTableModel);
        
        fiducialTable = new AutoSelectTextTable(fiducialTableModel);
        TableUtils.bindKeys(fiducialTable, removeFiducialAction);
        AutoSelectTextTable.setEmptyText(fiducialTable,
                Translations.getString("PanelDefinition.PanelAlignment.Empty")); //$NON-NLS-1$
        // A fiducial is looked at, not placed: no rank, no error handling, no status, not placed
        // and no type.
        tcm = fiducialTable.getColumnModel();
        tcm.removeColumn(tcm.getColumn(11));
        tcm.removeColumn(tcm.getColumn(10));
        tcm.removeColumn(tcm.getColumn(9));
        tcm.removeColumn(tcm.getColumn(8));
        tcm.removeColumn(tcm.getColumn(7));
        
        fiducialTable.setRowSorter(fiducialTableSorter);
        fiducialTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fiducialTable.setDefaultEditor(Side.class, new DefaultCellEditor(sidesComboBox));
        fiducialTable.setDefaultEditor(Part.class, new DefaultCellEditor(partsComboBox));
        fiducialTable.setDefaultRenderer(Part.class, new IdentifiableTableCellRenderer<Part>());
        fiducialTable.setDefaultRenderer(LengthCellValue.class, new MonospacedFontTableCellRenderer());
        fiducialTable.setDefaultRenderer(RotationCellValue.class, new MonospacedFontTableCellRenderer());
        fiducialTable.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        
        TableUtils.setColumnAlignment(fiducialTableModel, fiducialTable);
        
        TableUtils.installColumnWidthSavers(fiducialTable, prefs, "PanelDefinitionPanel.fiducialTable"); //$NON-NLS-1$
        
        fiducialTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            
            boolean updateLinkedTables = MainFrame.get().getNavigation().getSelectedComponent() == MainFrame.get().getPanelsTab() 
                    && configuration.getTablesLinked() == TablesLinked.Linked;
            
            if (getFiducialSelections().size() > 1) {
                // multi select
                fiducialSingleSelectionActionGroup.setEnabled(false);
                fiducialMultiSelectionActionGroup.setEnabled(true);
                if (updateLinkedTables) {
                    configuration.getBus().post(new PlacementSelectedEvent(null,
                            rootPanelLocation, PanelDefinitionPanel.this));
                }
            }
            else {
                // single select, or no select
                fiducialMultiSelectionActionGroup.setEnabled(false);
                fiducialSingleSelectionActionGroup.setEnabled(getFiducialSelection() != null);
                if (updateLinkedTables) {
                    configuration.getBus().post(new PlacementSelectedEvent(getFiducialSelection(),
                            rootPanelLocation, PanelDefinitionPanel.this));
                }
            }
        });

        JPopupMenu fiducialPopupMenu = new JPopupMenu();

        JMenu setFiducialSideMenu = new JMenu(setSideAction);
        for (Side side : Side.values()) {
            setFiducialSideMenu.add(new SetFiducialSideAction(side));
        }
        fiducialPopupMenu.add(setFiducialSideMenu);

        JMenu setFiducialEnabledMenu = new JMenu(setEnabledAction);
        setFiducialEnabledMenu.add(new SetFiducialEnabledAction(true));
        setFiducialEnabledMenu.add(new SetFiducialEnabledAction(false));
        fiducialPopupMenu.add(setFiducialEnabledMenu);
        
        fiducialTable.setComponentPopupMenu(fiducialPopupMenu);

        JPanel fiducialsPage = new JPanel(new BorderLayout());
        fiducialsPage.setOpaque(false);
        fiducialsPage.add(DockPanel.table(fiducialTable), BorderLayout.CENTER);
        fiducialTable.setDefaultRenderer(Boolean.class, DockRenderers.check());
        fiducialTable.setDefaultRenderer(org.openpnp.gui.support.PartCellValue.class, DockRenderers.bold());
        fiducialTable.setDefaultRenderer(Side.class, DockRenderers.dropdown(DockRenderers.side()));
        fiducialTable.setDefaultRenderer(String.class, DockRenderers.muted());

        DockPanel.Toolbar fiducialTools = new DockPanel.Toolbar();
        fiducialTools.button(addFiducialAction, "plus", "Dock.Action.Add"); //$NON-NLS-1$ //$NON-NLS-2$
        fiducialTools.iconButton(removeFiducialAction, "trash"); //$NON-NLS-1$
        fiducialTools.separator();
        fiducialTools.button(useChildFiducialAction, "crosshair", "Dock.Action.UseChildFiducial"); //$NON-NLS-1$ //$NON-NLS-2$
        fiducialsPage.add(fiducialTools, BorderLayout.NORTH);
        fiducialsPage.add(DockPanel.foot(Translations.getString("PanelDefinition.PanelAlignment.Foot")), //$NON-NLS-1$
                BorderLayout.SOUTH);

        // ---- the dock: Save above both tabs ----------------------------------------------------

        DockPanel dock = new DockPanel();
        childrenTab = dock.addTab(Ui.iconSm("board"), //$NON-NLS-1$
                Translations.getString("PanelDefinition.Tab.Children"), childrenPage); //$NON-NLS-1$
        fiducialsTab = dock.addTab(Ui.iconSm("target"), //$NON-NLS-1$
                Translations.getString("PanelDefinition.Tab.Fiducials"), fiducialsPage); //$NON-NLS-1$
        JButton save = Ui.button(saveAction, Ui.Size.Sm, Ui.Variant.Primary);
        save.setIcon(Ui.iconSm("save")); //$NON-NLS-1$
        save.setFocusable(false);
        modified.withHeight(24);
        modified.setVisible(false);
        dock.setTools(modified, (javax.swing.JComponent) javax.swing.Box.createHorizontalStrut(6), save);
        dock.setMaximize(() -> MainFrame.get().toggleDockMaximised());
        childrenTableModel.addTableModelListener(e -> updateCounts());
        fiducialTableModel.addTableModelListener(e -> updateCounts());
        add(dock, BorderLayout.CENTER);

        configuration.getBus().register(this);
    }

    /** A column's renderer by its model index, where the column is shown. */
    private static void columnRenderer(JTable table, int modelColumn, javax.swing.table.TableCellRenderer renderer) {
        int view = table.convertColumnIndexToView(modelColumn);
        if (view >= 0) {
            table.getColumnModel().getColumn(view).setCellRenderer(renderer);
        }
    }

    private JPopupMenu addChildMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(new JMenuItem(addNewBoardAction));
        menu.add(new JMenuItem(addExistingBoardAction));
        menu.addSeparator();
        menu.add(new JMenuItem(addNewPanelAction));
        menu.add(new JMenuItem(addExistingPanelAction));
        return menu;
    }

    /** The filter as the words typed, over every column. */
    private void filterChildren() {
        String text = childrenFilter.getText().trim();
        childrenTableSorter.setRowFilter(text.isEmpty() ? null
                : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text))); //$NON-NLS-1$
    }

    private void updateCounts() {
        childrenTab.setCount(panel == null ? null : childrenTableModel.getRowCount());
        fiducialsTab.setCount(panel == null ? null : fiducialTableModel.getRowCount());
    }

    /** Save, and the chip beside it, as the panel on show stands: saved here, or with the job. */
    void updateSaveState() {
        String state = unsaved.describe(panel);
        saveAction.setEnabled(state != null && panel.getFile() != null);
        modified.setVisible(state != null);
        if (state != null) {
            modified.setText(state);
        }
        panelsPanel.repaintList();
    }

    public final Action saveAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("PanelDefinition.Action.Save")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelDefinition.Action.Save.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            Panel saved = panel;
            if (saved == null) {
                return;
            }
            try {
                configuration.savePanel(saved);
                MainFrame.get().setStatus(String.format(Translations.getString("PanelDefinition.Saved"), //$NON-NLS-1$
                        saved.getName(), DefinitionList.where(saved.getFile())));
            }
            catch (Exception e) {
                Logger.error(e, "Failed to save panel {}.", saved.getName());
                MessageBoxes.errorBox(getTopLevelAncestor(),
                        Translations.getString("PanelDefinition.SaveError"), e); //$NON-NLS-1$
            }
            updateSaveState();
        }
    };
    
    public void setPanel(Panel panel) throws IOException {
        this.panel = panel;
        unsaved.watch(panel);
        rootPanelLocation.setGlobalSide(Side.Top);
        rootPanelLocation.setPanel(panel);
        childrenTableModel.setPlacementsHolderLocations(rootPanelLocation.getChildren());
        fiducialTableModel.setPlacementsHolder(rootPanelLocation.getPanel());
        addChildAction.setEnabled(panel != null);
        addChildButton.setEnabled(panel != null);
        viewerAction.setEnabled(panel != null);
        addFiducialAction.setEnabled(panel != null);
        useChildFiducialAction.setEnabled(panel != null);
        if (panelViewer != null) {
            panelViewer.setPlacementsHolder(panel);
        }
        updateCounts();
        updateSaveState();
    }
    
    public void refresh() {
        childrenTableModel.fireTableDataChanged();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        boolean oldValue = this.dirty;
        this.dirty = dirty;
        firePropertyChange("dirty", oldValue, dirty); //$NON-NLS-1$
    }

    public void refreshSelectedRow() {
        int index = childrenTable.convertRowIndexToModel(childrenTable.getSelectedRow());
        childrenTableModel.fireTableRowsUpdated(index, index);
    }

    public Placement getFiducialSelection() {
        List<Placement> selectedFiducials = getFiducialSelections();
        if (selectedFiducials.isEmpty()) {
            return null;
        }
        return selectedFiducials.get(0);
    }

    public List<Placement> getFiducialSelections() {
        List<Placement> fiducials = new ArrayList<>();
        int[] selectedRows = fiducialTable.getSelectedRows();
        List<Placement> placements = new IdentifiableList<>(panel.getPlacements());
        placements.addAll(panel.getPseudoPlacements());
        for (int selectedRow : selectedRows) {
            selectedRow = fiducialTable.convertRowIndexToModel(selectedRow);
            fiducials.add(placements.get(selectedRow));
        }
        return fiducials;
    }

    public PlacementsHolderLocation<?> getChildrenSelection() {
        List<PlacementsHolderLocation<?>> selectedChildren = getChildrenSelections();
        if (selectedChildren.isEmpty()) {
            return null;
        }
        return selectedChildren.get(0);
    }

    public List<PlacementsHolderLocation<?>> getChildrenSelections() {
        List<PlacementsHolderLocation<?>> selectedChildren = new ArrayList<>();
        int[] selectedRows = childrenTable.getSelectedRows();
        for (int selectedRow : selectedRows) {
            selectedRow = childrenTable.convertRowIndexToModel(selectedRow);
            selectedChildren.add((PlacementsHolderLocation<?>) childrenTableModel.getRowObjectAt(selectedRow));
        }
        return selectedChildren;
    }

    public final Action addFiducialAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.add);
            putValue(NAME, Translations.getString("PanelDefinition.PanelAlignment.Add")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelDefinition.PanelAlignment.Add.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            if (configuration.getParts().size() == 0) {
                MessageBoxes.errorBox(getTopLevelAncestor(), Translations.getString("General.Error"), //$NON-NLS-1$
                        Translations.getString("PanelDefinition.PanelAlignment.Add.Error.NoParts")); //$NON-NLS-1$
                return;
            }

            String id = JOptionPane.showInputDialog(getTopLevelAncestor(),
                    Translations.getString("PanelDefinition.PanelAlignment.Add.EnterIdMessage")); //$NON-NLS-1$
            if (id == null) {
                return;
            }
            id = id.trim();
            if (id.isEmpty()) {
                return;
            }

            // Check if the new placement ID is unique
            for(Placement comparePlacement : rootPanelLocation.getPanel().getPlacements()) {
                if (comparePlacement.getId().equals(id)) {
                    MessageBoxes.errorBox(getTopLevelAncestor(), Translations.getString("General.Error"), //$NON-NLS-1$
                            Translations.getString("PanelDefinition.PanelAlignment.Add.Error.IdExists")); //$NON-NLS-1$
                    return;
                }
            }
            
            Placement placement = new Placement(id);

            placement.setPart(configuration.getParts().get(0));
            placement.setLocation(new Location(configuration.getSystemUnits()));
            placement.setSide(rootPanelLocation.getGlobalSide());
            placement.setType(Placement.Type.Fiducial);

            panel.addPlacement(placement);

            configuration.getBus()
                .post(new DefinitionStructureChangedEvent(panel, "placements", PanelDefinitionPanel.this)); //$NON-NLS-1$

            fiducialTableModel.fireTableDataChanged();
            
            Helpers.selectObjectTableRow(fiducialTable, placement);
        }
    };

    public final Action removeFiducialAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.delete);
            putValue(NAME, Translations.getString("PanelDefinition.PanelAlignment.Remove")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelDefinition.PanelAlignment.Remove.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            List<Placement> selections = getFiducialSelections();
            if (selections.isEmpty() || !Dialogs.confirmDelete(getTopLevelAncestor(), "Dialogs.Kind.PanelFiducials", //$NON-NLS-1$
                    selections.stream().map(Placement::getId).collect(Collectors.toList()),
                    Translations.getString("PanelDefinition.Delete.More"))) { //$NON-NLS-1$
                return;
            }
            for (Placement placement : selections) {
                if (panel.getPseudoPlacements().contains(placement)) {
                    panel.removePseudoPlacement(placement);
                }
                else {
                    panel.removePlacement(placement);
                }
                placement.dispose();
            }
            fiducialTableModel.fireTableDataChanged();
            MainFrame.get().setStatus(String.format(Translations.getString("PanelDefinition.PanelAlignment.Removed"), //$NON-NLS-1$
                    selections.size(), panel.getName()));
            
            configuration.getBus()
                .post(new DefinitionStructureChangedEvent(panel, "placements", PanelDefinitionPanel.this)); //$NON-NLS-1$
        }
    };

    public final Action useChildFiducialAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.useChildFiducial);
            putValue(NAME, Translations.getString("PanelDefinition.PanelAlignment.UseChildren")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelDefinition.PanelAlignment.UseChildren.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            ChildFiducialSelectorDialog dialog = new ChildFiducialSelectorDialog(configuration,
                    rootPanelLocation);
            dialog.setVisible(true);
            
            configuration.getBus()
                .post(new DefinitionStructureChangedEvent(panel, "placements", PanelDefinitionPanel.this)); //$NON-NLS-1$

            fiducialTableModel.fireTableDataChanged();
        }
    };

    public final Action addChildAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.add);
            putValue(NAME, Translations.getString("PanelDefinition.Children.Add")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelDefinition.Children.Add.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
        }
    };

    public final Action addNewBoardAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("PanelDefinition.Children.Add.NewBoard")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelDefinition.Children.Add.NewBoard.Description")); //$NON-NLS-1$
            putValue(MNEMONIC_KEY, KeyEvent.VK_N);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            String title = Translations.getString("PanelDefinition.Children.Add.NewBoard.DialogTitle"); //$NON-NLS-1$
            FileDialog fileDialog = FileDialogs.prepare(new FileDialog(frame, title, FileDialog.SAVE), title,
                    ".board.xml"); //$NON-NLS-1$
            fileDialog.setVisible(true);
            try {
                String filename = fileDialog.getFile();
                if (filename == null) {
                    return;
                }
                if (!filename.toLowerCase().endsWith(".board.xml")) { //$NON-NLS-1$
                    filename = filename + ".board.xml"; //$NON-NLS-1$
                }
                File file = new File(new File(fileDialog.getDirectory()), filename);

                BoardLocation newBoardLocation = addBoard(file);
                Helpers.selectObjectTableRow(childrenTable, newBoardLocation);
            }
            catch (Exception e) {
                Logger.error(e, "Failed to add a new board to panel {}.", panel.getName());
                MessageBoxes.errorBox(frame, 
                        Translations.getString("PanelDefinition.Children.Add.NewBoard.SaveError"), //$NON-NLS-1$
                        e.getMessage());
            }
        }
    };

    public final Action addExistingBoardAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("PanelDefinition.Children.Add.ExistingBoard")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelDefinition.Children.Add.ExistingBoard.Description")); //$NON-NLS-1$
            putValue(MNEMONIC_KEY, KeyEvent.VK_E);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            ExistingBoardOrPanelDialog existingBoardDialog = new ExistingBoardOrPanelDialog(
                    configuration, Board.class,
                    Translations.getString("PanelDefinition.Children.Add.ExistingBoard.DialogTitle")); //$NON-NLS-1$
            existingBoardDialog.setVisible(true);
            File file = existingBoardDialog.getFile();
            existingBoardDialog.dispose();
            if (file == null) {
                return;
            }
            try {
                BoardLocation newBoardLocation = addBoard(file);
                Helpers.selectObjectTableRow(childrenTable, newBoardLocation);
            }
            catch (Exception e) {
                Logger.error(e, "Failed to add existing board {} to panel {}.", file, panel.getName());
                MessageBoxes.errorBox(frame, 
                        Translations.getString("PanelDefinition.Children.Add.ExistingBoard.LoadError"), //$NON-NLS-1$
                        e.getMessage());
            }
        }
    };

    protected BoardLocation addBoard(File file) throws Exception {
        //Make a deep copy of the board's definition to add to the panel
        Board board = new Board(configuration.getBoard(file));
        
        BoardLocation boardLocation = new BoardLocation(board);
        boardLocation.setParent(rootPanelLocation);
        panel.addChild(boardLocation);
        childrenTableModel.fireTableDataChanged();
        
        configuration.getBus()
            .post(new DefinitionStructureChangedEvent(rootPanelLocation.getPanel(), "children",  //$NON-NLS-1$
                    PanelDefinitionPanel.this));
        
        return boardLocation;
    }
    
    public final Action addNewPanelAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("PanelDefinition.Children.Add.NewPanel")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelDefinition.Children.Add.NewPanel.Description")); //$NON-NLS-1$
//            putValue(MNEMONIC_KEY, KeyEvent.VK_N);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            String title = Translations.getString("PanelDefinition.Children.Add.NewPanel.DialogTitle"); //$NON-NLS-1$
            FileDialog fileDialog = FileDialogs.prepare(new FileDialog(frame, title, FileDialog.SAVE), title,
                    ".panel.xml"); //$NON-NLS-1$
            fileDialog.setVisible(true);
            try {
                String filename = fileDialog.getFile();
                if (filename == null) {
                    return;
                }
                if (!filename.toLowerCase().endsWith(".panel.xml")) { //$NON-NLS-1$
                    filename = filename + ".panel.xml"; //$NON-NLS-1$
                }
                File file = new File(new File(fileDialog.getDirectory()), filename);

                PanelLocation newPanelLocation = addPanel(file);
                Helpers.selectObjectTableRow(childrenTable, newPanelLocation);
            }
            catch (Exception e) {
                Logger.error(e, "Failed to add a new sub-panel to panel {}.", panel.getName());
                MessageBoxes.errorBox(frame, 
                        Translations.getString("PanelDefinition.Children.Add.NewPanel.SaveError"), //$NON-NLS-1$
                        e.getMessage());
            }
        }
    };

    public final Action addExistingPanelAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("PanelDefinition.Children.Add.ExistingPanel")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelDefinition.Children.Add.ExistingPanel.Description")); //$NON-NLS-1$
//            putValue(MNEMONIC_KEY, KeyEvent.VK_E);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            ExistingBoardOrPanelDialog existingPanelDialog = new ExistingBoardOrPanelDialog(
                    configuration, Panel.class, 
                    Translations.getString("PanelDefinition.Children.Add.ExistingPanel.DialogTitle")); //$NON-NLS-1$
            existingPanelDialog.setVisible(true);
            File file = existingPanelDialog.getFile();
            existingPanelDialog.dispose();
            if (file == null) {
                return;
            }
            try {
                PanelLocation newPanelLocation = addPanel(file);
                Helpers.selectObjectTableRow(childrenTable, newPanelLocation);
            }
            catch (Exception e) {
                Logger.error(e, "Failed to add existing sub-panel {} to panel {}.", file, panel.getName());
                MessageBoxes.errorBox(frame, 
                        Translations.getString("PanelDefinition.Children.Add.ExistingPanel.LoadError"), //$NON-NLS-1$
                        e.getMessage());
            }
        }
    };

    protected PanelLocation addPanel(File file) throws Exception {
        //Make a deep copy of the panel's definition to add to the panel
        Panel newPanel = new Panel(configuration.getPanel(file));
        
        PanelLocation panelLocation = new PanelLocation(newPanel);
        verifyNoCircularReferences(rootPanelLocation, panelLocation);
        panel.addChild(panelLocation);
        PanelLocation.setParentsOfAllDescendants(rootPanelLocation);
        childrenTableModel.fireTableDataChanged();
        
        configuration.getBus()
            .post(new DefinitionStructureChangedEvent(panel, "children", PanelDefinitionPanel.this)); //$NON-NLS-1$
        
        return panelLocation;
    }
    
    private void verifyNoCircularReferences(PanelLocation root, PanelLocation decendant) throws Exception {
        if (decendant.getPanel().getFile().equals(root.getPanel().getFile())) {
            throw new Exception(Translations.getString("PanelDefinition.Children.Add.CircularReferenceError")); //$NON-NLS-1$
        }
        for (PlacementsHolderLocation<?> child : decendant.getChildren()) {
            if (child instanceof PanelLocation) {
                verifyNoCircularReferences(root, (PanelLocation) child);
            }
        }
    }
    
    public final Action removeChildAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.delete);
            putValue(NAME, Translations.getString("PanelDefinition.Children.Remove")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelDefinition.Children.Remove.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            List<PlacementsHolderLocation<?>> selectedChildren = getChildrenSelections();
            if (selectedChildren.isEmpty() || !Dialogs.confirmDelete(getTopLevelAncestor(), "Dialogs.Kind.PanelChildren", //$NON-NLS-1$
                    selectedChildren.stream().map(PlacementsHolderLocation::getId).collect(Collectors.toList()),
                    Translations.getString("PanelDefinition.Delete.More"))) { //$NON-NLS-1$
                return;
            }
            for (PlacementsHolderLocation<?> child : selectedChildren) {
                rootPanelLocation.getPanel().getDefinition().removeChild(child);
            }
            childrenTableModel.fireTableDataChanged();
            MainFrame.get().setStatus(String.format(Translations.getString("PanelDefinition.Children.Removed"), //$NON-NLS-1$
                    selectedChildren.size(), panel.getName()));
            
            configuration.getBus()
                .post(new DefinitionStructureChangedEvent(rootPanelLocation.getPanel(), "children", //$NON-NLS-1$
                        PanelDefinitionPanel.this));
        }
    };

    public final Action createArrayAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.autoPanelize);
            putValue(NAME, Translations.getString("PanelDefinition.Children.CreateArray")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelDefinition.Children.CreateArray.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            PlacementsHolderLocation<?> child = getChildrenSelection();
            PanelArrayBuilderDialog dlg = new PanelArrayBuilderDialog(configuration,
                    rootPanelLocation, child, () -> refresh());
            dlg.setVisible(true);
            
            configuration.getBus()
            .post(new DefinitionStructureChangedEvent(rootPanelLocation.getPanel(), "children", //$NON-NLS-1$
                    PanelDefinitionPanel.this));
            
            Helpers.selectObjectTableRow(childrenTable, child);
        }
    };
    
    public final Action viewerAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.colorTrue);
            putValue(NAME, Translations.getString("BoardsPanel.BoardPlacements.Action.View")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION,
                    Translations.getString("PanelDefinition.Children.ViewPanel.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            if (panelViewer == null) {
                panelViewer = new PlacementsHolderLocationViewerDialog(configuration,
                        rootPanelLocation, false, null);
                panelViewer.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        panelViewer = null;
                    }
                });
            }
            else {
                panelViewer.setExtendedState(Frame.NORMAL);
            }
            panelViewer.setVisible(true);
        }
    };

    public final Action replaceChildrenAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("PanelDefinition.Children.Replace")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelDefinition.Children.Replace.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            List<PlacementsHolderLocation<?>> selectedChildren = getChildrenSelections();
            ExistingBoardOrPanelDialog existingBoardOrPanelDialog;
            File file;
            if (selectedChildren.get(0) instanceof BoardLocation) {
                existingBoardOrPanelDialog = new ExistingBoardOrPanelDialog(
                        configuration, Board.class,
                        Translations.getString("PanelDefinition.Children.Replace.ExistingBoard.DialogTitle")); //$NON-NLS-1$
            }
            else {
                existingBoardOrPanelDialog = new ExistingBoardOrPanelDialog(
                        configuration, Panel.class,
                        Translations.getString("PanelDefinition.Children.Replace.ExistingPanel.DialogTitle")); //$NON-NLS-1$
            }

            existingBoardOrPanelDialog.setVisible(true);
            file = existingBoardOrPanelDialog.getFile();
            existingBoardOrPanelDialog.dispose();
            if (file == null) {
                return;
            }
                
            try {
                if (selectedChildren.get(0) instanceof BoardLocation) {
                    for (PlacementsHolderLocation<?> oldChild : selectedChildren) {
                        //Make a deep copy of the board's definition to add to the panel
                        Board board = new Board(configuration.getBoard(file));
                        
                        BoardLocation boardLocation = new BoardLocation(board);
                        boardLocation.setParent(oldChild.getParent());
                        panel.replaceChild(oldChild, boardLocation);
                    }
                }
                else {
                    for (PlacementsHolderLocation<?> oldChild : selectedChildren) {
                        //Make a deep copy of the panel's definition to add to the panel
                        Panel panelCopy = new Panel(configuration.getPanel(file));
                        
                        PanelLocation panelLocation = new PanelLocation(panelCopy);
                        panelLocation.setParent(oldChild.getParent());
                        PanelLocation.setParentsOfAllDescendants(panelLocation);
                        panel.replaceChild(oldChild, panelLocation);
                    }
                }
                
                childrenTableModel.fireTableDataChanged();
                
                configuration.getBus()
                    .post(new DefinitionStructureChangedEvent(rootPanelLocation.getPanel(), "children", //$NON-NLS-1$
                            PanelDefinitionPanel.this));
            }
            catch (Exception e) {
                Logger.error(e, "Failed to replace the selected children of panel {} with {}.",
                        panel.getName(), file);
                MessageBoxes.errorBox(frame, 
                        Translations.getString("PanelDefinition.Children.Replace.LoadError"), //$NON-NLS-1$
                        e.getMessage());
            }
        }
    };

    public final Action setSideAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("PanelDefinition.SetSide")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelDefinition.SetSide.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {}
    };

    class SetFiducialSideAction extends AbstractAction {
        final Side side;

        public SetFiducialSideAction(Side side) {
            this.side = side;
            String name = side == Side.Top ?
                    Translations.getString("Placement.Side.Top") : //$NON-NLS-1$
                    Translations.getString("Placement.Side.Bottom"); //$NON-NLS-1$
            putValue(NAME, name); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelDefinition.SetSide.Fiducials.Description") + //$NON-NLS-1$
                    " " + name); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            List<Placement> selections = getFiducialSelections();
            for (Placement fiducial : selections) {
                fiducial.setSide(side);
                fiducialTableModel.fireTableCellUpdated(fiducial, 
                        Translations.getString("PlacementsHolderPlacementsTableModel.ColumnName.Side")); //$NON-NLS-1$
            }
            Helpers.selectObjectTableRows(fiducialTable, selections);
        }
    };
    
    class SetChildrenSideAction extends AbstractAction {
        final Side side;

        public SetChildrenSideAction(Side side) {
            this.side = side;
            String name = side == Side.Top ?
                    Translations.getString("Placement.Side.Top") : //$NON-NLS-1$
                    Translations.getString("Placement.Side.Bottom"); //$NON-NLS-1$
            putValue(NAME, name); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelDefinition.SetSide.Children.Description") +  //$NON-NLS-1$
                    " " + name); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            List<PlacementsHolderLocation<?>> selections = getChildrenSelections();
            for (PlacementsHolderLocation<?> child : selections) {
                child.getDefinition().setGlobalSide(side);
                childrenTableModel.fireTableCellDecendantsUpdated(child, 
                        Translations.getString("PlacementsHolderLocationsTableModel.ColumnName.Side")); //$NON-NLS-1$
            }
            Helpers.selectObjectTableRows(childrenTable, selections);
        }
    };
    
    public final Action setEnabledAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("PanelDefinition.SetEnabled")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelDefinition.SetEnabled.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {}
    };

    class SetFiducialEnabledAction extends AbstractAction {
        final Boolean enabled;

        public SetFiducialEnabledAction(Boolean enabled) {
            this.enabled = enabled;
            String name = enabled ? 
                    Translations.getString("General.Enabled") : //$NON-NLS-1$
                    Translations.getString("General.Disabled"); //$NON-NLS-1$
            putValue(NAME, name);
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelDefinition.SetEnabled.Fiducials.Description") //$NON-NLS-1$
                    + " " + name); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            List<Placement> selections = getFiducialSelections();
            for (Placement fiducial : selections) {
                fiducial.setEnabled(enabled);
                fiducialTableModel.fireTableCellUpdated(fiducial, 
                        Translations.getString("PlacementsHolderPlacementsTableModel.ColumnName.Enabled")); //$NON-NLS-1$
            }
            Helpers.selectObjectTableRows(fiducialTable, selections);
        }
    };

    class SetChildrenEnabledAction extends AbstractAction {
        final Boolean enabled;

        public SetChildrenEnabledAction(Boolean enabled) {
            this.enabled = enabled;
            String name = enabled ?
                    Translations.getString("General.Enabled") : //$NON-NLS-1$
                    Translations.getString("General.Disabled"); //$NON-NLS-1$
            putValue(NAME, name);
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelDefinition.SetEnabled.Children.Description") //$NON-NLS-1$
                    + " " + name); //$NON-NLS-2$ //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            List<PlacementsHolderLocation<?>> selections = getChildrenSelections();
            for (PlacementsHolderLocation<?> child : selections) {
                child.getDefinition().setLocallyEnabled(enabled);
                childrenTableModel.fireTableCellDecendantsUpdated(child, 
                        Translations.getString("PlacementsHolderLocationsTableModel.ColumnName.Enabled")); //$NON-NLS-1$
            }
            Helpers.selectObjectTableRows(childrenTable, selections);
        }
    };

    public final Action setCheckFidsAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("PanelDefinition.SetCheckFids")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelDefinition.SetCheckFids.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {}
    };

    class SetCheckFidsAction extends AbstractAction {
        final Boolean value;

        public SetCheckFidsAction(Boolean value) {
            this.value = value;
            String name = value ?
                    Translations.getString("Fiducial.Check.Check") :  //$NON-NLS-1$
                    Translations.getString("Fiducial.Check.NoCheck"); //$NON-NLS-1$
            putValue(NAME, name);
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelDefinition.SetCheckFids.Children.Description") //$NON-NLS-1$
                    + " " + name); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            List<PlacementsHolderLocation<?>> selections = getChildrenSelections();
            for (PlacementsHolderLocation<?> child : selections) {
                child.getDefinition().setCheckFiducials(value);
                childrenTableModel.fireTableCellUpdated(child, 
                        Translations.getString("PlacementsHolderLocationsTableModel.ColumnName.CheckFids")); //$NON-NLS-1$
            }
            Helpers.selectObjectTableRows(childrenTable, selections);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName() == "children") { //$NON-NLS-1$
            childrenTableModel.setPlacementsHolderLocations(rootPanelLocation.getChildren());
            childrenTableModel.fireTableDataChanged();   
        }
        if (evt.getSource() != this && evt.getPropertyName() != "dirty") { //$NON-NLS-1$
            setDirty(true);
        }
    }

    public void selectFiducial(Placement placement) {
        if (placement == null) {
            fiducialTable.getSelectionModel().clearSelection();
            return;
        }
        for (int i = 0; i < fiducialTableModel.getRowCount(); i++) {
            if (fiducialTableModel.getRowObjectAt(i) == placement.getDefinition()) {
                int index = fiducialTable.convertRowIndexToView(i);
                fiducialTable.getSelectionModel().setSelectionInterval(index, index);
                fiducialTable.scrollRectToVisible(new Rectangle(fiducialTable.getCellRect(index, 0, true)));
                break;
            }
        }
    }
    
    public void selectChild(PlacementsHolderLocation<?> child) {
        if (child == null) {
            childrenTable.getSelectionModel().clearSelection();
            return;
        }
        for (int i = 0; i < childrenTableModel.getRowCount(); i++) {
            if (childrenTableModel.getRowObjectAt(i) == child.getDefinition()) {
                int index = childrenTable.convertRowIndexToView(i);
                childrenTable.getSelectionModel().setSelectionInterval(index, index);
                childrenTable.scrollRectToVisible(new Rectangle(childrenTable.getCellRect(index, 0, true)));
                break;
            }
        }
    }
}
