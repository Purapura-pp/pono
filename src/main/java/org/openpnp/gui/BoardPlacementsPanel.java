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
import java.awt.Component;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultCellEditor;
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
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

import org.openpnp.Translations;
import org.openpnp.events.DefinitionStructureChangedEvent;
import org.openpnp.events.PlacementSelectedEvent;
import org.openpnp.gui.components.AutoSelectTextTable;
import org.openpnp.gui.importer.BoardImporter;
import org.openpnp.gui.importer.SolderPasteGerberImporter;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.DockPanel;
import org.openpnp.gui.shell.DockRenderers;
import org.openpnp.gui.shell.PropertySheetPresenter.Result;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.ActionGroup;
import org.openpnp.gui.support.Helpers;
import org.openpnp.gui.support.IdentifiableListCellRenderer;
import org.openpnp.gui.support.IdentifiableTableCellRenderer;
import org.openpnp.gui.support.LengthCellValue;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.gui.support.MonospacedFontTableCellRenderer;
import org.openpnp.gui.support.MultisortTableHeaderCellRenderer;
import org.openpnp.gui.support.PartsComboBoxModel;
import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.gui.support.RotationCellValue;
import org.openpnp.gui.support.TableUtils;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.gui.tablemodel.PlacementsHolderPlacementsTableModel;
import org.openpnp.gui.viewers.PlacementsHolderLocationViewerDialog;
import org.openpnp.model.Abstract2DLocatable.Side;
import org.openpnp.model.Board;
import org.openpnp.model.BoardLocation;
import org.openpnp.model.BoardPad;
import org.openpnp.model.Configuration;
import org.openpnp.model.Configuration.TablesLinked;
import org.openpnp.model.Location;
import org.openpnp.model.Part;
import org.openpnp.model.Placement;
import org.openpnp.model.Placement.ErrorHandling;
import org.openpnp.model.Placement.Type;
import org.openpnp.util.IdentifiableList;
import org.pmw.tinylog.Logger;

import com.google.common.eventbus.Subscribe;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;

/**
 * The placements of the board selected on the boards page, as mockup 09 draws them: a dock with
 * Save and how much is unsaved, add and delete, the importers and the board's picture, the
 * filter; the side, the type, the error handling and whether a placement is enabled changed in the
 * table itself, and the selected placement's properties in the window's properties column. It was
 * a titled box under the boards in a split, a row of unlabelled icons, and these four values were
 * set from the right-click menu.
 */
@SuppressWarnings("serial")
public class BoardPlacementsPanel extends JPanel {
    private JTable table;
    private PlacementsHolderPlacementsTableModel tableModel;
    private TableRowSorter<PlacementsHolderPlacementsTableModel> tableSorter;
    private ActionGroup singleSelectionActionGroup;
    private ActionGroup multiSelectionActionGroup;
    private BoardsPanel boardsPanel;
    private Board board;
    private Preferences prefs = Preferences.userNodeForPackage(BoardPlacementsPanel.class);
    protected PlacementsHolderLocationViewerDialog boardViewer;
    private List<BoardImporter> boardImporters;
    private final Configuration configuration;
    private DockPanel.Tab placementsTab;
    /** "Modified · 2 places", beside Save, while the board has changes that are not saved. */
    private final Chip modified = new Chip("", Chip.Tone.Warn, Chip.Shape.Status); //$NON-NLS-1$
    private JTextField searchTextField;

    private final UnsavedChanges unsaved = new UnsavedChanges(this::updateSaveState);

    public BoardPlacementsPanel(BoardsPanel boardsPanel, Configuration configuration) {
        this.boardsPanel = boardsPanel;
        this.configuration = configuration;
        boardImporters = scanForBoardImporters();
        createUi();
    }

    /**
     * Scans the importer's package for BoardImporters
     * @return the list of BoardImporters that were found
     */
    @SuppressWarnings("unchecked")
    private List<BoardImporter> scanForBoardImporters() {
        List<BoardImporter> boardImporters = new ArrayList<>();
        try (ScanResult scanResult = new ClassGraph().enableClassInfo()
                .acceptPackages(BoardImporter.class.getPackage().getName()).scan()) {
            ClassInfoList importerClassInfoList = scanResult.
                    getClassesImplementing(BoardImporter.class.getCanonicalName());
            for (ClassInfo boardImporterInfo : importerClassInfoList) {
                BoardImporter boardImporter;
                try {
                    boardImporter = ((Class<? extends BoardImporter>) boardImporterInfo.loadClass())
                            .getDeclaredConstructor().newInstance();
                }
                catch (Exception e) {
                    throw new Error(e);
                }
                
                //For now, skip the solder paste importer
                Logger.trace(boardImporter.getClass().getSimpleName());
                if (boardImporter.getClass() == SolderPasteGerberImporter.class) {
                    continue;
                }
                
                boardImporters.add(boardImporter);
            }
        }
        return boardImporters;
    }
    
    private void createUi() {
        setOpaque(false);
        setLayout(new BorderLayout(0, 0));

        singleSelectionActionGroup = new ActionGroup(removeAction, 
                setTypeAction, setSideAction, setErrorHandlingAction, setEnabledAction);
        singleSelectionActionGroup.setEnabled(false);

        multiSelectionActionGroup = new ActionGroup(removeAction,
                setTypeAction, setSideAction, setErrorHandlingAction, setEnabledAction);
        multiSelectionActionGroup.setEnabled(false);

        @SuppressWarnings("unchecked")
        JComboBox<PartsComboBoxModel> partsComboBox = new JComboBox<>(new PartsComboBoxModel());
        partsComboBox.setMaximumRowCount(20);
        partsComboBox.setRenderer(new IdentifiableListCellRenderer<Part>());
        JComboBox<Side> sidesComboBox = new JComboBox<>(Side.values());
        // Note we don't use Type.values() here because there are a couple Types that are only
        // there for backwards compatibility and we don't want them in the list.
        JComboBox<Type> typesComboBox = new JComboBox<>(new Type[] { Type.Placement, Type.Fiducial });
        JComboBox<ErrorHandling> errorHandlingComboBox = new JComboBox<>(ErrorHandling.values());
        
        tableModel = new PlacementsHolderPlacementsTableModel(configuration, this);
        tableSorter = new TableRowSorter<>(tableModel);
        
        table = new AutoSelectTextTable(tableModel) {
            @Override
            public String getToolTipText(MouseEvent evt) {
                int column = convertColumnIndexToModel(columnAtPoint(evt.getPoint()));
                if(column==11) { return Translations.getString("BoardsPanel.BoardPlacements.Placements.Rank.toolTip"); } //$NON-NLS-1$
                return super.getToolTipText(evt);
            }
        };
        // Enter edits the cell, Delete removes the selected placements, which asks first.
        TableUtils.bindKeys(table, removeAction);
        AutoSelectTextTable.setEmptyText(table,
                Translations.getString("BoardPlacementsPanel.Empty")); //$NON-NLS-1$
        
        // On a narrow window the comments give way first, then the error handling: both are in
        // the properties column too, and the error handling in the right-click menu.
        TableUtils.setColumnKinds(table, TableUtils.Kind.Check, TableUtils.Kind.Id, TableUtils.Kind.Name,
                TableUtils.Kind.Status, TableUtils.Kind.Number, TableUtils.Kind.Number, TableUtils.Kind.Number,
                TableUtils.Kind.Status, TableUtils.Kind.Secondary, TableUtils.Kind.Status, TableUtils.Kind.Secondary,
                TableUtils.Kind.Secondary, TableUtils.Kind.Secondary);
        // Neither the placed column nor the status is a board's: both are about a run of the job.
        TableColumnModel tcm = table.getColumnModel();
        tcm.removeColumn(tcm.getColumn(9));
        tcm.removeColumn(tcm.getColumn(8));
        
        table.setRowSorter(tableSorter);
        table.getTableHeader().setDefaultRenderer(new MultisortTableHeaderCellRenderer());
        
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setDefaultEditor(Side.class, new DefaultCellEditor(sidesComboBox));
        table.setDefaultEditor(Part.class, new DefaultCellEditor(partsComboBox));
        table.setDefaultEditor(Type.class, new DefaultCellEditor(typesComboBox));
        table.setDefaultEditor(ErrorHandling.class, new DefaultCellEditor(errorHandlingComboBox));
        table.setDefaultRenderer(Part.class, new IdentifiableTableCellRenderer<Part>());
        table.setDefaultRenderer(LengthCellValue.class, new MonospacedFontTableCellRenderer());
        table.setDefaultRenderer(RotationCellValue.class, new MonospacedFontTableCellRenderer());
        table.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        
        TableUtils.setColumnAlignment(tableModel, table);
        
        TableUtils.installColumnWidthSavers(table, prefs, "BoardPlacementsPanel.placementsTable"); //$NON-NLS-1$
        
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }

            boolean updateLinkedTables = MainFrame.get().getNavigation().getSelectedComponent() == MainFrame.get().getBoardsTab() 
                    && configuration.getTablesLinked() == TablesLinked.Linked;
            
            if (getSelections().size() > 1) {
                // multi select
                singleSelectionActionGroup.setEnabled(false);
                multiSelectionActionGroup.setEnabled(true);
            }
            else {
                // single select, or no select
                multiSelectionActionGroup.setEnabled(false);
                singleSelectionActionGroup.setEnabled(getSelection() != null);
                MainFrame mainFrame = MainFrame.get();
                Component selectedComponent = mainFrame.getNavigation().getSelectedComponent();
                if (updateLinkedTables) {
                    configuration.getBus().post(new PlacementSelectedEvent(getSelection(),
                            new BoardLocation(board), BoardPlacementsPanel.this));
                }
                if (getSelection() != null
                        && (selectedComponent == mainFrame.getJobTab() ||
                                selectedComponent == mainFrame.getBoardsTab())
                        && configuration.getTablesLinked() == TablesLinked.Linked) {
                    Part selectedPart = getSelection().getPart();
                    mainFrame.getPartsTab().selectPartInTableAndUpdateLinks(selectedPart);
                }
            }
            inspect();
        });
        
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (e.getKeyChar() == ' ') {
                    Placement placement = getSelection();
                    if (placement != null) {
                        placement.setEnabled(!placement.isEnabled());
                        refreshSelectedRow();
                    }
                }
                else {
                    super.keyTyped(e);
                }
            }
        });
        
        // The same four settings for many placements at once; each is changed in its cell too.
        JPopupMenu popupMenu = new JPopupMenu();

        JMenu setTypeMenu = new JMenu(setTypeAction);
        setTypeMenu.add(new SetTypeAction(Placement.Type.Placement));
        setTypeMenu.add(new SetTypeAction(Placement.Type.Fiducial));
        popupMenu.add(setTypeMenu);

        JMenu setSideMenu = new JMenu(setSideAction);
        for (Side side : Side.values()) {
            setSideMenu.add(new SetSideAction(side));
        }
        popupMenu.add(setSideMenu);

        JMenu setEnabledMenu = new JMenu(setEnabledAction);
        setEnabledMenu.add(new SetEnabledAction(true));
        setEnabledMenu.add(new SetEnabledAction(false));
        popupMenu.add(setEnabledMenu);

        JMenu setErrorHandlingMenu = new JMenu(setErrorHandlingAction);
        setErrorHandlingMenu.add(new SetErrorHandlingAction(ErrorHandling.Default));
        setErrorHandlingMenu.add(new SetErrorHandlingAction(ErrorHandling.Alert));
        setErrorHandlingMenu.add(new SetErrorHandlingAction(ErrorHandling.Defer));
        popupMenu.add(setErrorHandlingMenu);

        table.setComponentPopupMenu(popupMenu);

        // The mockup's cells: the check, the designator in bold, the side's badge, and the three
        // values changed from a list with their chevrons; the comments in grey.
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        page.add(DockPanel.table(table), BorderLayout.CENTER);
        table.setDefaultRenderer(Boolean.class, DockRenderers.check());
        table.setDefaultRenderer(org.openpnp.gui.support.PartCellValue.class, DockRenderers.bold());
        table.setDefaultRenderer(Side.class, DockRenderers.dropdown(DockRenderers.side(false)));
        table.setDefaultRenderer(Type.class, DockRenderers.dropdown(new TypeRenderer()));
        table.setDefaultRenderer(ErrorHandling.class, DockRenderers.dropdown(new ErrorHandlingRenderer(),
                (t, row) -> !isFiducial(t, row)));
        table.setDefaultRenderer(String.class, DockRenderers.muted());

        DockPanel.Toolbar toolbar = new DockPanel.Toolbar();
        toolbar.button(saveAction, "save", "Dock.Action.Save", Ui.Variant.Primary); //$NON-NLS-1$ //$NON-NLS-2$
        modified.withHeight(24);
        modified.setVisible(false);
        toolbar.add(modified);
        toolbar.separator();
        toolbar.button(newAction, "plus", "Dock.Action.Add"); //$NON-NLS-1$ //$NON-NLS-2$
        toolbar.iconButton(removeAction, "trash"); //$NON-NLS-1$
        toolbar.separator();
        toolbar.menu("Dock.Action.Import", "download", this::importMenu).setToolTipText( //$NON-NLS-1$ //$NON-NLS-2$
                Translations.getString("BoardsPanel.BoardPlacements.Action.Import.Description")); //$NON-NLS-1$
        toolbar.button(viewerAction, "eye", "Dock.Action.BoardView"); //$NON-NLS-1$ //$NON-NLS-2$
        toolbar.glue();
        searchTextField = toolbar.filter(Translations.getString("BoardPlacementsPanel.Filter.Placeholder")); //$NON-NLS-1$
        searchTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void removeUpdate(DocumentEvent e) {
                search();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                search();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                search();
            }
        });
        page.add(toolbar, BorderLayout.NORTH);
        page.add(DockPanel.foot(Translations.getString("BoardPlacementsPanel.Foot")), BorderLayout.SOUTH); //$NON-NLS-1$

        DockPanel dock = new DockPanel();
        placementsTab = dock.addTab(Ui.iconSm("parts"), //$NON-NLS-1$
                Translations.getString("BoardPlacementsPanel.Tab.Placements"), page); //$NON-NLS-1$
        dock.setMaximize(() -> MainFrame.get().toggleDockMaximised());
        tableModel.addTableModelListener(e -> placementsTab.setCount(board == null ? null : tableModel.getRowCount()));
        add(dock, BorderLayout.CENTER);

        newAction.setEnabled(false);
        importAction.setEnabled(false);
        viewerAction.setEnabled(false);
        updateSaveState();

        configuration.getBus().register(this);
    }

    private static boolean isFiducial(JTable table, int viewRow) {
        Object type = table.getModel().getValueAt(table.convertRowIndexToModel(viewRow), 7);
        return type == Type.Fiducial;
    }

    @Subscribe
    public void placementSelectedEventHandler(PlacementSelectedEvent event) {
        if (event.source == this || event.placementsHolderLocation == null || !(event.placementsHolderLocation.getPlacementsHolder() instanceof Board)) {
            return;
        }
        Placement placement = event.placement == null ? null : (Placement) event.placement.getDefinition();
        SwingUtilities.invokeLater(() -> {
            selectPlacement(placement);
        });
    }

    private void search() {
        updateRowFilter();
    }
    
    public void refresh() {
        tableModel.fireTableDataChanged();
    }

    public void refreshSelectedRow() {
        int index = table.convertRowIndexToModel(table.getSelectedRow());
        tableModel.fireTableRowsUpdated(index, index);
    }

    public void selectPlacement(Placement placement) {
        if (placement == null) {
            table.getSelectionModel().clearSelection();
            return;
        }
        int index = tableModel.indexOf(placement);
        if (index >= 0) {
            index = table.convertRowIndexToView(index);
            table.getSelectionModel().setSelectionInterval(index, index);
            table.scrollRectToVisible(new Rectangle(table.getCellRect(index, 0, true)));
        }
    }
    
    /** The filter as the words typed: "(" used to be the start of a pattern, and hid nothing. */
    private void updateRowFilter() {
        String text = searchTextField.getText().trim();
        tableSorter.setRowFilter(text.isEmpty() ? null
                : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text))); //$NON-NLS-1$
    }
    
    public void setBoard(Board board) {
        unsaved.watch(board);
        this.board = board;
        tableModel.setPlacementsHolder(board);
        newAction.setEnabled(board != null);
        importAction.setEnabled(board != null);
        viewerAction.setEnabled(board != null);
        if (boardViewer != null) {
            boardViewer.setPlacementsHolder(board);
        }
        placementsTab.setCount(board == null ? null : tableModel.getRowCount());
        updateRowFilter();
        updateSaveState();
    }

    /** Save, and the chip beside it, as the board on show stands: saved here, or with the job. */
    void updateSaveState() {
        String state = unsaved.describe(board);
        saveAction.setEnabled(state != null && board.getFile() != null);
        modified.setVisible(state != null);
        if (state != null) {
            modified.setText(state);
        }
        boardsPanel.repaintList();
    }

    // ---- the properties column -----------------------------------------------------------------

    /**
     * The selected placement's form in the properties column; with none or several selected, the
     * board's.
     */
    void inspect() {
        MainFrame frame = MainFrame.get();
        if (frame == null || frame.getInspector() == null) {
            return;
        }
        List<Placement> selections = getSelections();
        if (selections.size() != 1 || board == null) {
            boardsPanel.inspectBoard();
            return;
        }
        Placement placement = selections.get(0);
        Board shownBoard = board;
        Result shown = frame.getInspector().show(boardsPanel, placement, inspectorContainer, placement.getId(),
                PlacementInspector.subtitle(shownBoard), Ui.icon("parts", 16, Ui.accent()), //$NON-NLS-1$
                () -> List.of(new PropertySheetWizardAdapter(
                        PlacementInspector.buildDefinition(configuration, placement))));
        if (shown == Result.Cancelled) {
            // The user kept unapplied edits on the previous placement: put the selection back.
            SwingUtilities.invokeLater(() -> {
                Object previous = frame.getInspector().getPresenter().getShown();
                if (previous instanceof Placement) {
                    Helpers.selectObjectTableRow(table, previous);
                }
            });
        }
    }

    private final WizardContainer inspectorContainer = new WizardContainer() {
        @Override
        public void wizardCompleted(Wizard wizard) {
            refresh();
            configuration.getBus().post(new DefinitionStructureChangedEvent(board, "placements", //$NON-NLS-1$
                    BoardPlacementsPanel.this));
        }

        @Override
        public void wizardCancelled(Wizard wizard) {
        }
    };

    public Placement getSelection() {
        List<Placement> selectedPlacements = getSelections();
        if (selectedPlacements.isEmpty()) {
            return null;
        }
        return selectedPlacements.get(0);
    }

    public List<Placement> getSelections() {
        ArrayList<Placement> placements = new ArrayList<>();
        if (board == null) {
            return placements;
        }
        int[] selectedRows = table.getSelectedRows();
        for (int selectedRow : selectedRows) {
            selectedRow = table.convertRowIndexToModel(selectedRow);
            placements.add(board.getPlacements().get(selectedRow));
        }
        return placements;
    }

    public List<BoardImporter> getBoardImporters() {
        return boardImporters;
    }

    public final Action saveAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("BoardPlacementsPanel.Action.Save")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("BoardPlacementsPanel.Action.Save.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            Board saved = board;
            if (saved == null) {
                return;
            }
            try {
                configuration.saveBoard(saved);
                MainFrame.get().setStatus(String.format(Translations.getString("BoardPlacementsPanel.Saved"), //$NON-NLS-1$
                        saved.getName(), DefinitionList.where(saved.getFile())));
            }
            catch (Exception e) {
                Logger.error(e, "Failed to save board {}.", saved.getName());
                MessageBoxes.errorBox(getTopLevelAncestor(),
                        Translations.getString("BoardPlacementsPanel.SaveError"), e); //$NON-NLS-1$
            }
            updateSaveState();
        }
    };

    public final Action newAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("BoardsPanel.BoardPlacements.Action.NewPlacement")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("BoardsPanel.BoardPlacements.Action.NewPlacement.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            if (configuration.getParts().size() == 0) {
                MessageBoxes.errorBox(getTopLevelAncestor(), Translations.getString("General.Error"), //$NON-NLS-1$
                        Translations.getString("BoardsPanel.BoardPlacements.NewPlacement.ErrorMessageBox.NoPartsMessage")); //$NON-NLS-1$
                return;
            }

            String id = JOptionPane.showInputDialog(getTopLevelAncestor(),
                    Translations.getString("BoardsPanel.BoardPlacements.NewPlacement.InputDialog.enterIdMessage")); //$NON-NLS-1$
            if (id == null) {
                return;
            }
            id = id.trim();
            if (id.isEmpty()) {
                return;
            }

            // Check if the new placement ID is unique
            for(Placement compareplacement : board.getPlacements()) {
                if (compareplacement.getId().equals(id)) {
                    MessageBoxes.errorBox(getTopLevelAncestor(), Translations.getString("General.Error"), //$NON-NLS-1$
                            Translations.getString("BoardsPanel.BoardPlacements.NewPlacement.ErrorMessageBox.IdAlreadyExistsMessage")); //$NON-NLS-1$
                    return;
                }
            }
            
            Placement placement = new Placement(id);

            placement.setPart(configuration.getParts().get(0));
            placement.setLocation(new Location(configuration.getSystemUnits()));
            placement.setSide(Side.Top);

            board.addPlacement(placement);
            tableModel.fireTableDataChanged();
            Helpers.selectLastTableRow(table);

            configuration.getBus()
                .post(new DefinitionStructureChangedEvent(board, "placements", BoardPlacementsPanel.this)); //$NON-NLS-1$
        }
    };

    public final Action removeAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("BoardsPanel.BoardPlacements.Action.RemovePlacement")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("BoardsPanel.BoardPlacements.Action.RemovePlacement.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            List<Placement> selections = getSelections();
            if (selections.isEmpty() || !org.openpnp.gui.shell.Dialogs.confirmDelete(getTopLevelAncestor(),
                    "Dialogs.Kind.Placements", //$NON-NLS-1$
                    selections.stream().map(Placement::getId).collect(java.util.stream.Collectors.toList()),
                    Translations.getString("BoardPlacementsPanel.Delete.More"))) { //$NON-NLS-1$
                return;
            }
            for (Placement placement : selections) {
                board.removePlacement(placement);
            }
            tableModel.fireTableDataChanged();
            MainFrame.get().setStatus(String.format(Translations.getString("BoardPlacementsPanel.Deleted"), //$NON-NLS-1$
                    selections.size(), board.getName()));

            configuration.getBus()
                .post(new DefinitionStructureChangedEvent(board, "placements", BoardPlacementsPanel.this)); //$NON-NLS-1$
        }
    };

    public void importBoard(Class<? extends BoardImporter> boardImporterClass) {
        if (boardsPanel.getSelection() == null) {
            MessageBoxes.errorBox(getTopLevelAncestor(), Translations.getString("BoardsPanel.BoardPlacements.Importer.Fail"), //$NON-NLS-1$
                    Translations.getString("BoardsPanel.BoardPlacements.Importer.Fail.Message")); //$NON-NLS-1$
            return;
        }
        
        BoardImporter boardImporter;
        try {
            boardImporter = boardImporterClass.newInstance();
        }
        catch (Exception e) {
            MessageBoxes.errorBox(getTopLevelAncestor(), Translations.getString("BoardsPanel.BoardPlacements.Importer.Fail"), e); //$NON-NLS-1$
            return;
        }

        try {
            Board importedBoard = boardImporter.importBoard((Frame) getTopLevelAncestor());
            if (importedBoard != null) {
                IdentifiableList<Placement> existingPlacements = board.getPlacements();
                int importOption = 1;
                if (!existingPlacements.isEmpty()) {
                    //Option 0: Merge imported placements with existing placements - existing 
                    //          placements with Ids matching those in the imported set are updated,
                    //          existing placements with Ids that don't match any in the imported 
                    //          set are left unchanged, and placements in the imported set that 
                    //          don't match any in the existing set are added 
                    //Option 1: Import after deleting all existing placements
                    //Option 2: Cancel the import
                    importOption = org.openpnp.gui.shell.Dialogs.ask(getTopLevelAncestor(),
                            org.openpnp.gui.shell.Dialogs.Tone.Warn, "alert", //$NON-NLS-1$
                            Translations.getString("BoardsPanel.BoardPlacements.Importer.Ask.Title"), //$NON-NLS-1$
                            Translations.getString("BoardsPanel.BoardPlacements.Importer.Ask.What"), //$NON-NLS-1$
                            Translations.getString("BoardsPanel.BoardPlacements.Importer.Ask.More"), //$NON-NLS-1$
                            org.openpnp.gui.shell.Dialogs.Choice.plain(
                                    Translations.getString("BoardsPanel.BoardPlacements.Importer.OptionsBox.Merge")), //$NON-NLS-1$
                            org.openpnp.gui.shell.Dialogs.Choice.danger(
                                    Translations.getString("BoardsPanel.BoardPlacements.Importer.Ask.Replace"))); //$NON-NLS-1$
                    if (importOption < 0) {
                        return;
                    }
                }
                if (importOption == 1) {
                    board.removeAllPlacements();
                }
                int imported = 0;
                for (Placement placement : importedBoard.getPlacements()) {
                    imported++;
                    if (importOption == 0 && (existingPlacements.get(placement.getId()) != null)) {
                        Placement existingPlacement = existingPlacements.get(placement.getId());
                        existingPlacement.setPart(placement.getPart());
                        existingPlacement.setSide(placement.getSide());
                        existingPlacement.setLocation(placement.getLocation());
                        existingPlacement.setComments(placement.getComments());
                    }
                    else {
                        Placement newPlacement = new Placement(placement);
                        newPlacement.setDefinition(newPlacement);
                        board.addPlacement(newPlacement);
                    }
                }
                for (BoardPad pad : importedBoard.getSolderPastePads()) {
                    // TODO: This is a temporary hack until we redesign the
                    // importer
                    // interface to be more intuitive. The Gerber importer tends
                    // to return everything in Inches, so this is a method to
                    // try to get it closer to what the user expects to see.
                    pad.setLocation(pad.getLocation()
                            .convertToUnits(boardsPanel.getSelection().getDimensions().getUnits()));
                    board.addSolderPastePad(pad);
                }
                
                importedBoard.dispose();
                
                tableModel.fireTableDataChanged();
                MainFrame.get().setStatus(String.format(Translations.getString("BoardPlacementsPanel.Imported"), //$NON-NLS-1$
                        imported, board.getName()));
                
                configuration.getBus()
                    .post(new DefinitionStructureChangedEvent(board, "placements", BoardPlacementsPanel.this)); //$NON-NLS-1$
            }
        }
        catch (Exception e) {
            MessageBoxes.errorBox(getTopLevelAncestor(), Translations.getString("BoardsPanel.BoardPlacements.Importer.Fail"), e); //$NON-NLS-1$
        }
    }

    /** The importers, each with its description; greyed with no board to import into. */
    private JPopupMenu importMenu() {
        JPopupMenu menu = new JPopupMenu();
        for (BoardImporter bi : boardImporters) {
            final BoardImporter boardImporter = bi;
            JMenuItem item = new JMenuItem(new AbstractAction() {
                {
                    putValue(NAME, boardImporter.getImporterName());
                    putValue(SHORT_DESCRIPTION, boardImporter.getImporterDescription());
                }

                @Override
                public void actionPerformed(ActionEvent e) {
                    importBoard(boardImporter.getClass());
                    refresh();
                }
            });
            item.setEnabled(importAction.isEnabled());
            menu.add(item);
        }
        return menu;
    }

    public final Action importAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("BoardsPanel.BoardPlacements.Action.Import")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("BoardsPanel.BoardPlacements.Action.Import.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
        }
    };

    public final Action viewerAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("BoardsPanel.BoardPlacements.Action.View")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("BoardsPanel.BoardPlacements.Action.View.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            if (boardViewer == null) {
                boardViewer = new PlacementsHolderLocationViewerDialog(configuration,
                        new BoardLocation(board), false, null);
                boardViewer.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        boardViewer = null;
                    }
                });
            }
            else {
                boardViewer.setExtendedState(Frame.NORMAL);
            }
            boardViewer.setVisible(true);
        }
    };

    public final Action setTypeAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("BoardsPanel.BoardPlacements.Action.SetType")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("BoardsPanel.BoardPlacements.Action.SetType.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {}
    };

    class SetTypeAction extends AbstractAction {
        final Placement.Type type;

        public SetTypeAction(Placement.Type type) {
            this.type = type;
            String name;
            if (type == Placement.Type.Fiducial) {
                name = Translations.getString("Placement.Type.Fiducial"); //$NON-NLS-1$
            }
            else if (type == Placement.Type.Placement) {
                name = Translations.getString("Placement.Type.Placement"); //$NON-NLS-1$
            }
            else {
                name = type.toString();
            }
            putValue(NAME, name);
            putValue(SHORT_DESCRIPTION, Translations.getString("BoardsPanel.BoardPlacements.Action.SetType.ToolTip") + //$NON-NLS-1$
                    " " + name); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            for (Placement placement : getSelections()) {
                placement.setType(type);
                tableModel.fireTableCellUpdated(placement, 
                        Translations.getString("PlacementsHolderPlacementsTableModel.ColumnName.Type")); //$NON-NLS-1$
            }
        }
    };

    public final Action setSideAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("BoardsPanel.BoardPlacements.Action.SetSide")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("BoardsPanel.BoardPlacements.Action.SetSide.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {}
    };

    class SetSideAction extends AbstractAction {
        final Side side;

        public SetSideAction(Side side) {
            this.side = side;
            String name;
            if (side == Side.Top) {
                name = Translations.getString("Placement.Side.Top"); //$NON-NLS-1$
            }
            else {
                name = Translations.getString("Placement.Side.Bottom"); //$NON-NLS-1$
            }
            putValue(NAME, name);
            putValue(SHORT_DESCRIPTION, Translations.getString("BoardsPanel.BoardPlacements.Action.SetSide.ToolTip") + //$NON-NLS-1$
                    " " + name); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            for (Placement placement : getSelections()) {
                placement.setSide(side);
                tableModel.fireTableCellUpdated(placement, 
                        Translations.getString("PlacementsHolderPlacementsTableModel.ColumnName.Side")); //$NON-NLS-1$
            }
        }
    };
    
    public final Action setErrorHandlingAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("BoardsPanel.BoardPlacements.Action.SetErrorHandling")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("BoardsPanel.BoardPlacements.Action.SetErrorHandling.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {}
    };

    class SetErrorHandlingAction extends AbstractAction {
        Placement.ErrorHandling errorHandling;

        public SetErrorHandlingAction(Placement.ErrorHandling errorHandling) {
            this.errorHandling = errorHandling;
            String name;
            switch(errorHandling) {
            case Alert:
            default:
                name = Translations.getString("Placement.ErrorHandling.Alert"); //$NON-NLS-1$
                break;
            case Defer:
                name = Translations.getString("Placement.ErrorHandling.Defer"); //$NON-NLS-1$
                break;
            case Default:
                name = Translations.getString("Placement.ErrorHandling.Default"); //$NON-NLS-1$
                break;
            }
            putValue(NAME, name);
            putValue(SHORT_DESCRIPTION, Translations.getString("BoardsPanel.BoardPlacements.Action.SetErrorHandling.ToolTip") + //$NON-NLS-1$
                    " " + name); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            for (Placement placement : getSelections()) {
                //First set it to the "wrong" state so that when we set it to the correct state,
                //the change is sure to propagate to all instances defined by the Placement
                if (errorHandling == Placement.ErrorHandling.Alert) {
                    placement.setErrorHandling(Placement.ErrorHandling.Defer);
                }
                else {
                    placement.setErrorHandling(Placement.ErrorHandling.Alert);
                }
                placement.setErrorHandling(errorHandling);
                tableModel.fireTableCellUpdated(placement, 
                        Translations.getString("PlacementsHolderPlacementsTableModel.ColumnName.ErrorHandling")); //$NON-NLS-1$
            }
        }
    };
    
    public final Action setEnabledAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("BoardsPanel.BoardPlacements.Action.SetEnabled")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("BoardsPanel.BoardPlacements.Action.SetEnabled.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {}
    };

    class SetEnabledAction extends AbstractAction {
        final Boolean enabled;

        public SetEnabledAction(Boolean enabled) {
            this.enabled = enabled;
            String name = enabled ? 
                    Translations.getString("General.Enabled") :  //$NON-NLS-1$
                    Translations.getString("General.Disabled"); //$NON-NLS-1$
            putValue(NAME, name);
            putValue(SHORT_DESCRIPTION, Translations.getString("BoardsPanel.BoardPlacements.Action.SetEnabled.ToolTip") +  //$NON-NLS-1$
                    " " + name); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            for (Placement placement : getSelections()) {
                //First set it to the "wrong" state so that when we set it to the correct state,
                //the change is sure to propagate to all instances defined by the Placement
                placement.setEnabled(!enabled);
                placement.setEnabled(enabled);
                tableModel.fireTableCellUpdated(placement, 
                        Translations.getString("PlacementsHolderPlacementsTableModel.ColumnName.Enabled")); //$NON-NLS-1$
            }
        }
    };

    /** The placement's type by its display name, in the table's own colours; see JobPlacementsPanel. */
    static class TypeRenderer extends DefaultTableCellRenderer {
        @Override
        public void setValue(Object value) {
            setText(value == null ? "" : org.openpnp.gui.support.DisplayNames.of(value)); //$NON-NLS-1$
        }
    }

    /**
     * The error handling by its display name; a dash for a fiducial, which is looked at and never
     * picked, so that nothing about it can go wrong the way a pick does.
     */
    static class ErrorHandlingRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            boolean fiducial = isFiducial(table, row);
            setText(fiducial ? DockPanel.dash()
                    : value == null ? "" : org.openpnp.gui.support.DisplayNames.of(value)); //$NON-NLS-1$
            setForeground(fiducial ? Ui.muted() : isSelected ? table.getSelectionForeground() : Ui.text2());
            return this;
        }
    }
}
