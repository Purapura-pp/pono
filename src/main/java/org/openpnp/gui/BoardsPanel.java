/*
 * Copyright (C) 2022 Jason von Nieda <jason@vonnieda.org>, Tony Luken <tonyluken62+openpnp@gmail.com>
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
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.TableRowSorter;

import org.openpnp.Translations;
import org.openpnp.events.PlacementsHolderLocationSelectedEvent;
import org.openpnp.events.PlacementsHolderSelectedEvent;
import org.openpnp.gui.components.AutoSelectTextTable;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.gui.shell.DockPanel;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.ActionGroup;
import org.openpnp.gui.support.FileDialogs;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.gui.support.TableUtils;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.gui.tablemodel.PlacementsHolderTableModel;
import org.openpnp.model.Board;
import org.openpnp.model.Configuration;
import org.openpnp.model.Configuration.TablesLinked;
import org.pmw.tinylog.Logger;

import com.google.common.eventbus.Subscribe;

/**
 * The boards page, as mockup 09 draws it: the board definitions listed down the left with New,
 * Open and the remove at the far end, and the selected board's placements beside them. The
 * boards were a table over the placements in a split, with three levels of titled boxes; removing
 * and cleaning up asked nothing and said nothing afterwards.
 */
@SuppressWarnings("serial")
public class BoardsPanel extends JPanel {
    final private Configuration configuration;
    final private MainFrame frame;

    private PlacementsHolderTableModel boardsTableModel;
    private JTable boardsTable;

    private ActionGroup singleSelectionActionGroup;
    private ActionGroup multiSelectionActionGroup;

    private final BoardPlacementsPanel boardPlacementsPanel;
    private final DockPanel.Tab definitionsTab;
    /** What uses the selected board: the job and how many times, a panel, or nothing. */
    private final JLabel usage = DockPanel.foot(""); //$NON-NLS-1$

    public BoardsPanel(Configuration configuration, MainFrame frame) {
        this.configuration = configuration;
        this.frame = frame;
        
        singleSelectionActionGroup = new ActionGroup(removeBoardAction, copyBoardAction);
        singleSelectionActionGroup.setEnabled(false);
        
        multiSelectionActionGroup = new ActionGroup(removeBoardAction);
        multiSelectionActionGroup.setEnabled(false);
        
        // The name and the size are changed in the properties column; the list only lists.
        boardsTableModel = new PlacementsHolderTableModel(configuration, 
                () -> configuration.getBoards(), Board.class) {
            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }
        };
        
        boardsTable = new AutoSelectTextTable(boardsTableModel) {
            @Override
            public String getToolTipText(MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                if (row >= 0) {
                    row = convertRowIndexToModel(row);
                    File file = configuration.getBoards().get(row).getFile();
                    return file == null ? null : file.toString();
                }
                return super.getToolTipText();
            }
        };

        boardsTable.setRowSorter(new TableRowSorter<>(boardsTableModel));
        boardsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        TableUtils.bindKeys(boardsTable, removeBoardAction);
        AutoSelectTextTable.setEmptyText(boardsTable, Translations.getString("BoardsPanel.Empty")); //$NON-NLS-1$

        boardsTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            
            boolean updateLinkedTables = 
                    MainFrame.get().getNavigation().getSelectedComponent() == MainFrame.get().getBoardsTab() 
                    && configuration.getTablesLinked() == TablesLinked.Linked;

            List<Board> selections = getSelections();
            if (selections.size() == 0) {
                singleSelectionActionGroup.setEnabled(false);
                multiSelectionActionGroup.setEnabled(false);
                getBoardPlacementsPanel().setBoard(null);
                if (updateLinkedTables) {
                    configuration.getBus()
                        .post(new PlacementsHolderSelectedEvent(null, BoardsPanel.this));
                }
            }
            else if (selections.size() == 1) {
                multiSelectionActionGroup.setEnabled(false);
                singleSelectionActionGroup.setEnabled(true);
                getBoardPlacementsPanel().setBoard(selections.get(0));
                if (updateLinkedTables) {
                    configuration.getBus()
                        .post(new PlacementsHolderSelectedEvent(selections.get(0), BoardsPanel.this));
                }
            }
            else {
                singleSelectionActionGroup.setEnabled(false);
                multiSelectionActionGroup.setEnabled(true);
                getBoardPlacementsPanel().setBoard(null);
                if (updateLinkedTables) {
                    configuration.getBus()
                        .post(new PlacementsHolderSelectedEvent(null, BoardsPanel.this));
                }
            }
            updateUsage();
            inspectBoard();
            MainFrame.get().updateMenuState(BoardsPanel.this);
        });

        setLayout(new BorderLayout(10, 0));
        setOpaque(false);
        setBorder(new javax.swing.border.EmptyBorder(0, 10, 10, 10));
        putClientProperty(MainFrame.DOCK_PAGE, Boolean.TRUE);

        // The mockup's list: New and Open named, Copy and Clean up behind "...", and the remove,
        // which takes the board off the list and leaves its file, at the far end.
        DockPanel.Toolbar listTools = new DockPanel.Toolbar();
        listTools.button(addNewBoardAction, "plus", "Dock.Action.New"); //$NON-NLS-1$ //$NON-NLS-2$
        listTools.button(addExistingBoardAction, "folder", "Dock.Action.Open"); //$NON-NLS-1$ //$NON-NLS-2$
        listTools.glue();
        listTools.more(copyBoardAction, null, cleanUpAction);
        listTools.iconButton(removeBoardAction, "x"); //$NON-NLS-1$
        JPanel listPage = new JPanel(new BorderLayout());
        listPage.setOpaque(false);
        listPage.add(listTools, BorderLayout.NORTH);
        listPage.add(DefinitionList.dress(boardsTable, holder -> holder.getPlacements().size()),
                BorderLayout.CENTER);
        listPage.add(usage, BorderLayout.SOUTH);
        DockPanel list = new DockPanel() {
            @Override
            public Dimension getPreferredSize() {
                // 290 as drawn, less on a narrow window, where the placements need the room.
                Dimension size = super.getPreferredSize();
                int page = BoardsPanel.this.getWidth();
                size.width = page <= 0 ? 290 : Math.max(220, Math.min(290, page * 3 / 10));
                return size;
            }
        };
        definitionsTab = list.addTab(Ui.iconSm("board"), //$NON-NLS-1$
                Translations.getString("BoardsPanel.Tab.Definitions"), listPage); //$NON-NLS-1$
        configuration.addPropertyChangeListener("boards", evt -> { //$NON-NLS-1$
            boardsTableModel.fireTableDataChanged();
            definitionsTab.setCount(configuration.getBoards().size());
            updateUsage();
        });
        definitionsTab.setCount(configuration.getBoards().size());
        add(list, BorderLayout.WEST);

        boardPlacementsPanel = new BoardPlacementsPanel(this, configuration);
        add(boardPlacementsPanel, BorderLayout.CENTER);
        boardsTableModel.addTableModelListener(e -> SwingUtilities.invokeLater(() -> {
            getBoardPlacementsPanel().refresh();
        }));

        // The job may have changed while another page was on show.
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                updateUsage();
            }
        });
        updateUsage();

        configuration.getBus().register(this);
    }
    
    public JTable getFiducialLocatableLocationsTable() {
        return boardsTable;
    }

    @Subscribe
    public void boardLocationSelected(PlacementsHolderLocationSelectedEvent event) {
        if (event.source == this || event.placementsHolderLocation == null || !(event.placementsHolderLocation.getPlacementsHolder() instanceof Board)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            selectBoard((Board) event.placementsHolderLocation.getPlacementsHolder().getDefinition());
        });
    }

    public void selectBoard(Board board) {
        if (board == null) {
            boardsTable.getSelectionModel().clearSelection();
            return;
        }
        int index = boardsTableModel.indexOf(board);
        if (index >= 0) {
            index = boardsTable.convertRowIndexToView(index);
            boardsTable.getSelectionModel().setSelectionInterval(index, index);
            boardsTable.scrollRectToVisible(
                    new Rectangle(boardsTable.getCellRect(index, 0, true)));
        }
    }

    public void refresh() {
        boardsTableModel.fireTableDataChanged();
    }

    public void refreshSelectedRow() {
        int index = boardsTable.convertRowIndexToModel(boardsTable.getSelectedRow());
        boardsTableModel.fireTableRowsUpdated(index, index);
    }

    /** The list drawn again: a board's dot comes and goes as it is changed and saved. */
    void repaintList() {
        boardsTable.repaint();
    }

    private void updateUsage() {
        List<Board> selections = getSelections();
        Board board = selections.size() == 1 ? selections.get(0) : null;
        usage.setText(DefinitionList.usage(configuration, board));
        usage.setToolTipText(DefinitionList.usageTip(configuration, board));
    }

    /** The selected board's name, file and size in the properties column. */
    void inspectBoard() {
        if (frame == null || frame.getInspector() == null) {
            return;
        }
        List<Board> selections = getSelections();
        Board board = selections.size() == 1 ? selections.get(0) : null;
        if (board == null) {
            frame.getInspector().show(this, null);
            return;
        }
        frame.getInspector().show(this, board, boardContainer, board.getName(),
                String.format(Translations.getString("BoardsPanel.Inspector.Subtitle"), //$NON-NLS-1$
                        board.getPlacements().size()),
                Ui.icon("board", 16, Ui.accent()), //$NON-NLS-1$
                () -> List.of(new PropertySheetWizardAdapter(DefinitionForm.build(configuration, board))));
    }

    private final WizardContainer boardContainer = new WizardContainer() {
        @Override
        public void wizardCompleted(Wizard wizard) {
            refreshSelectedRow();
            getBoardPlacementsPanel().updateSaveState();
        }

        @Override
        public void wizardCancelled(Wizard wizard) {
        }
    };

    public Board getSelection() {
        List<Board> selections = getSelections();
        if (selections.isEmpty()) {
            return null;
        }
        return selections.get(0);
    }

    public List<Board> getSelections() {
        ArrayList<Board> selections = new ArrayList<>();
        int[] selectedRows = boardsTable.getSelectedRows();
        for (int selectedRow : selectedRows) {
            selectedRow = boardsTable.convertRowIndexToModel(selectedRow);
            selections.add(configuration.getBoards().get(selectedRow));
        }
        return selections;
    }

    private static List<String> names(List<Board> boards) {
        return boards.stream().map(Board::getName).collect(Collectors.toList());
    }

    public final Action addBoardAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("BoardsPanel.Action.AddBoard")); //$NON-NLS-1$
            putValue(SMALL_ICON, Icons.add);
            putValue(SHORT_DESCRIPTION, Translations.getString("BoardsPanel.Action.AddBoard.Description")); //$NON-NLS-1$
            putValue(MNEMONIC_KEY, KeyEvent.VK_A);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {}
    };

    public final Action addNewBoardAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("BoardsPanel.Action.AddBoard.NewBoard")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, 
                    Translations.getString("BoardsPanel.Action.AddBoard.NewBoard.Description")); //$NON-NLS-1$
            putValue(MNEMONIC_KEY, KeyEvent.VK_N);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            String title = Translations.getString("BoardsPanel.Action.AddBoard.NewBoard.SaveDialog"); //$NON-NLS-1$
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

                Board board = addBoard(file);

                selectBoard(board);
            }
            catch (Exception e) {
                Logger.error(e, "Failed to create new board.");
                MessageBoxes.errorBox(frame, 
                        Translations.getString("BoardsPanel.Action.AddBoard.NewBoard.ErrorMessage"), e.getMessage()); //$NON-NLS-1$
            }
        }
    };

    public final Action addExistingBoardAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("BoardsPanel.Action.AddBoard.ExistingBoard")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, 
                    Translations.getString("BoardsPanel.Action.AddBoard.ExistingBoard.Description")); //$NON-NLS-1$
            putValue(MNEMONIC_KEY, KeyEvent.VK_E);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            FileDialog fileDialog = FileDialogs.prepare(new FileDialog(frame),
                    Translations.getString("BoardsPanel.AddBoard.FileDialog.Title"), ".board.xml"); //$NON-NLS-1$ //$NON-NLS-2$
            fileDialog.setVisible(true);
            try {
                if (fileDialog.getFile() == null) {
                    return;
                }
                File file = new File(new File(fileDialog.getDirectory()), fileDialog.getFile());

                Board board = addBoard(file);

                selectBoard(board);
            }
            catch (Exception e) {
                Logger.error(e, "Failed to add existing board {}.", fileDialog.getFile());
                MessageBoxes.errorBox(frame, 
                        Translations.getString("BoardsPanel.Action.AddBoard.ExistingBoard.ErrorMessage"), //$NON-NLS-1$
                        e.getMessage());
            }
        }
    };

    protected Board addBoard(File file) throws Exception {
        Board board = configuration.getBoard(file);
        // TODO: Move to a list property listener.
        boardsTableModel.fireTableDataChanged();
        return board;
    }
    
    public BoardPlacementsPanel getBoardPlacementsPanel() {
        return boardPlacementsPanel;
    }

    /**
     * Takes the selected boards off the list after asking; their files stay. A board the job or
     * a loaded panel uses stays too, and the result says which.
     */
    public final Action removeBoardAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.delete);
            putValue(NAME, Translations.getString("BoardsPanel.Action.RemoveBoard")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, 
                    Translations.getString("BoardsPanel.Action.RemoveBoard.Description")); //$NON-NLS-1$
            putValue(MNEMONIC_KEY, KeyEvent.VK_R);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            List<Board> selections = getSelections();
            List<Board> inUse = new ArrayList<>();
            List<Board> removable = new ArrayList<>();
            for (Board selection : selections) {
                (configuration.isInUse(selection) ? inUse : removable).add(selection);
            }
            if (removable.isEmpty()) {
                if (!inUse.isEmpty()) {
                    MessageBoxes.errorBox(BoardsPanel.this, 
                            Translations.getString("BoardsPanel.Action.RemoveBoard.ErrorBox.Title"), //$NON-NLS-1$
                            String.format(Translations.getString("BoardsPanel.Action.RemoveBoard.ErrorBox.MessageFormat"), //$NON-NLS-1$
                                    String.join(Translations.getString("DefinitionList.NameSeparator"), names(inUse)))); //$NON-NLS-1$
                }
                return;
            }
            if (!Dialogs.confirmRemove(frame, "Dialogs.Kind.Boards", names(removable))) { //$NON-NLS-1$
                return;
            }
            int removed = 0;
            for (Board board : removable) {
                if (configuration.removeBoard(board)) {
                    removed++;
                }
            }
            boardsTableModel.fireTableDataChanged();
            String result = String.format(Translations.getString("BoardsPanel.Removed"), removed); //$NON-NLS-1$
            if (!inUse.isEmpty()) {
                result += " \u00b7 " + String.format(Translations.getString("BoardsPanel.Removed.KeptInUse"), //$NON-NLS-1$ //$NON-NLS-2$
                        String.join(Translations.getString("DefinitionList.NameSeparator"), names(inUse))); //$NON-NLS-1$
            }
            frame.setStatus(result);
        }
    };
    
    public final Action copyBoardAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.copy);
            putValue(NAME, Translations.getString("BoardsPanel.Action.CopyBoard")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, 
                    Translations.getString("BoardsPanel.Action.CopyBoard.Description")); //$NON-NLS-1$
            // VK_COPY is the Copy key of a Sun keyboard, not a letter: the mnemonic never worked.
            putValue(MNEMONIC_KEY, KeyEvent.VK_C);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            Board boardToCopy = getSelection();
            String title = Translations.getString("BoardsPanel.Action.CopyBoard.SaveDialog"); //$NON-NLS-1$
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

                Board newBoard = new Board(boardToCopy);
                newBoard.setDefinition(newBoard);
                newBoard.setFile(file);
                newBoard.setName(file.getName());
                newBoard.setDirty(false);
                configuration.addBoard(newBoard);
                configuration.saveBoard(newBoard);
                boardsTableModel.fireTableDataChanged();
                selectBoard(newBoard);
                frame.setStatus(String.format(Translations.getString("BoardsPanel.Copied"), //$NON-NLS-1$
                        boardToCopy.getName(), DefinitionList.where(file)));
            }
            catch (Exception e) {
                Logger.error(e, "Failed to copy board {}.", boardToCopy.getName());
                MessageBoxes.errorBox(frame, 
                        Translations.getString("BoardsPanel.Action.CopyBoard.ErrorMessage"), //$NON-NLS-1$
                        e.getMessage());
            }
        }
    };

    /**
     * Takes every board nothing uses off the list after naming them; says so when there is
     * nothing to take off, which it used to do silently either way.
     */
    public final Action cleanUpAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.clean);
            putValue(NAME, Translations.getString("BoardsPanel.Action.CleanUp")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("BoardsPanel.Action.CleanUp.Description")); //$NON-NLS-1$
            putValue(MNEMONIC_KEY, KeyEvent.VK_D);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            List<Board> unused = new ArrayList<>();
            for (Board board : configuration.getBoards()) {
                if (!configuration.isInUse(board)) {
                    unused.add(board);
                }
            }
            if (unused.isEmpty()) {
                Dialogs.info(frame, Translations.getString("BoardsPanel.CleanUp.Nothing.Title"), //$NON-NLS-1$
                        Translations.getString("BoardsPanel.CleanUp.Nothing")); //$NON-NLS-1$
                return;
            }
            if (!Dialogs.confirmRemove(frame, "Dialogs.Kind.Boards", names(unused))) { //$NON-NLS-1$
                return;
            }
            int removed = 0;
            for (Board board : unused) {
                if (configuration.removeBoard(board)) {
                    removed++;
                }
            }
            boardsTableModel.fireTableDataChanged();
            frame.setStatus(String.format(Translations.getString("BoardsPanel.Removed"), removed)); //$NON-NLS-1$
        }
    };

}
