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
import java.io.IOException;
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
import org.openpnp.events.PlacementSelectedEvent;
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
import org.openpnp.model.Configuration;
import org.openpnp.model.Configuration.TablesLinked;
import org.openpnp.model.Panel;
import org.openpnp.model.PanelLocation;
import org.openpnp.model.Placement;
import org.pmw.tinylog.Logger;

import com.google.common.eventbus.Subscribe;

/**
 * The panels page, as mockup 10 draws it: the panel definitions listed down the left, as the
 * boards page lists its boards, and the selected panel's children and fiducials beside them. It
 * was a table of panels over the definition in a split, and the definition split again.
 */
@SuppressWarnings("serial")
public class PanelsPanel extends JPanel {
    final private Configuration configuration;
    final private MainFrame frame;

    private PlacementsHolderTableModel panelsTableModel;
    private JTable panelsTable;

    private ActionGroup singleSelectionActionGroup;
    private ActionGroup multiSelectionActionGroup;

    private PanelDefinitionPanel panelDefinitionPanel;
    private final DockPanel.Tab definitionsTab;
    private final JLabel usage = DockPanel.foot(""); //$NON-NLS-1$

    public PanelsPanel(Configuration configuration, MainFrame frame) {
        this.configuration = configuration;
        this.frame = frame;
        
        singleSelectionActionGroup = new ActionGroup(removePanelAction, copyPanelAction);
        singleSelectionActionGroup.setEnabled(false);
        
        multiSelectionActionGroup = new ActionGroup(removePanelAction);
        multiSelectionActionGroup.setEnabled(false);
        
        panelsTableModel = new PlacementsHolderTableModel(configuration, 
                () -> configuration.getPanels(), Panel.class) {
            @Override
            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return false;
            }
        };
        
        panelsTable = new AutoSelectTextTable(panelsTableModel) {
            @Override
            public String getToolTipText(MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                if (row >= 0) {
                    row = convertRowIndexToModel(row);
                    File file = configuration.getPanels().get(row).getFile();
                    return file == null ? null : file.toString();
                }
                return super.getToolTipText();
            }
        };

        panelsTable.setRowSorter(new TableRowSorter<>(panelsTableModel));
        panelsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        TableUtils.bindKeys(panelsTable, removePanelAction);
        AutoSelectTextTable.setEmptyText(panelsTable, Translations.getString("PanelsPanel.Empty")); //$NON-NLS-1$

        panelsTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            
            boolean updateLinkedTables = MainFrame.get().getNavigation().getSelectedComponent() == MainFrame.get().getPanelsTab() 
                    && configuration.getTablesLinked() == TablesLinked.Linked;

            List<Panel> selections = getSelections();
            Panel shown = selections.size() == 1 ? selections.get(0) : null;
            singleSelectionActionGroup.setEnabled(selections.size() == 1);
            multiSelectionActionGroup.setEnabled(selections.size() > 1);
            try {
                panelDefinitionPanel.setPanel(shown);
            }
            catch (IOException e1) {
                Logger.error(e1, "Failed to show the definition of panel {}.",
                        shown == null ? null : shown.getName());
            }
            if (updateLinkedTables) {
                configuration.getBus()
                    .post(new PlacementsHolderSelectedEvent(shown, PanelsPanel.this));
            }
            updateUsage();
            inspectPanel();
        });

        setLayout(new BorderLayout(10, 0));
        setOpaque(false);
        setBorder(new javax.swing.border.EmptyBorder(0, 10, 10, 10));
        putClientProperty(MainFrame.DOCK_PAGE, Boolean.TRUE);

        DockPanel.Toolbar listTools = new DockPanel.Toolbar();
        listTools.button(addNewPanelAction, "plus", "Dock.Action.New"); //$NON-NLS-1$ //$NON-NLS-2$
        listTools.button(addExistingPanelAction, "folder", "Dock.Action.Open"); //$NON-NLS-1$ //$NON-NLS-2$
        listTools.glue();
        listTools.more(copyPanelAction, null, cleanUpAction);
        listTools.iconButton(removePanelAction, "x"); //$NON-NLS-1$
        JPanel listPage = new JPanel(new BorderLayout());
        listPage.setOpaque(false);
        listPage.add(listTools, BorderLayout.NORTH);
        listPage.add(DefinitionList.dress(panelsTable, holder -> ((Panel) holder).getChildren().size()),
                BorderLayout.CENTER);
        listPage.add(usage, BorderLayout.SOUTH);
        DockPanel list = new DockPanel() {
            @Override
            public Dimension getPreferredSize() {
                // 260 as drawn, less on a narrow window, where the children need the room.
                Dimension size = super.getPreferredSize();
                int page = PanelsPanel.this.getWidth();
                size.width = page <= 0 ? 260 : Math.max(220, Math.min(260, page * 3 / 10));
                return size;
            }
        };
        definitionsTab = list.addTab(Ui.iconSm("layers"), //$NON-NLS-1$
                Translations.getString("PanelsPanel.Tab.Definitions"), listPage); //$NON-NLS-1$
        configuration.addPropertyChangeListener("panels", evt -> { //$NON-NLS-1$
            panelsTableModel.fireTableDataChanged();
            definitionsTab.setCount(configuration.getPanels().size());
            updateUsage();
        });
        definitionsTab.setCount(configuration.getPanels().size());
        add(list, BorderLayout.WEST);

        panelDefinitionPanel = new PanelDefinitionPanel(configuration, this);
        add(panelDefinitionPanel, BorderLayout.CENTER);
        panelsTableModel.addTableModelListener(e -> SwingUtilities.invokeLater(() -> {
            panelDefinitionPanel.refresh();
        }));

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                updateUsage();
            }
        });
        updateUsage();

        configuration.getBus().register(this);
    }
    
    public JTable getPanelsTable() {
        return panelsTable;
    }

    @Subscribe
    public void panelLocationSelected(PlacementsHolderLocationSelectedEvent event) {
        if (event.source == this || event.source == panelDefinitionPanel || event.placementsHolderLocation == null) {
            return;
        }
        if (event.placementsHolderLocation.getPlacementsHolder() instanceof Panel) {
            SwingUtilities.invokeLater(() -> {
                selectPanel((Panel) event.placementsHolderLocation.getPlacementsHolder().getDefinition());
            });
        }
        else if (event.placementsHolderLocation.getParent() instanceof PanelLocation) {
            SwingUtilities.invokeLater(() -> {
                selectPanel((Panel) event.placementsHolderLocation.getParent().getPlacementsHolder().getDefinition());
                panelDefinitionPanel.selectChild(event.placementsHolderLocation);
            });
        }
    }

    @Subscribe
    public void placementSelected(PlacementSelectedEvent event) {
        if (event.source == this || event.source == panelDefinitionPanel || 
                event.placementsHolderLocation == null || !(event.placementsHolderLocation.getPlacementsHolder() instanceof Panel)) {
            return;
        }
        Placement placement = event.placement == null ? null : (Placement) event.placement.getDefinition();
        SwingUtilities.invokeLater(() -> {
            selectPanel((Panel) event.placementsHolderLocation.getPlacementsHolder().getDefinition());
            panelDefinitionPanel.selectFiducial(placement);
        });
    }

    private void selectPanel(Panel panel) {
        if (panel == null) {
            panelsTable.getSelectionModel().clearSelection();
            return;
        }
        for (int i = 0; i < panelsTableModel.getRowCount(); i++) {
            if (configuration.getPanels().get(i) == panel) {
                int index = panelsTable.convertRowIndexToView(i);
                panelsTable.getSelectionModel().setSelectionInterval(index, index);
                panelsTable.scrollRectToVisible(
                        new Rectangle(panelsTable.getCellRect(index, 0, true)));
                break;
            }
        }
    }

    public void refresh() {
        panelsTableModel.fireTableDataChanged();
    }

    public void refreshSelectedRow() {
        int index = panelsTable.convertRowIndexToModel(panelsTable.getSelectedRow());
        panelsTableModel.fireTableRowsUpdated(index, index);
    }

    /** The list drawn again: a panel's dot comes and goes as it is changed and saved. */
    void repaintList() {
        panelsTable.repaint();
    }

    private void updateUsage() {
        List<Panel> selections = getSelections();
        Panel panel = selections.size() == 1 ? selections.get(0) : null;
        usage.setText(DefinitionList.usage(configuration, panel));
        usage.setToolTipText(DefinitionList.usageTip(configuration, panel));
    }

    /** The selected panel's name, file and size in the properties column. */
    void inspectPanel() {
        if (frame == null || frame.getInspector() == null) {
            return;
        }
        List<Panel> selections = getSelections();
        Panel panel = selections.size() == 1 ? selections.get(0) : null;
        if (panel == null) {
            frame.getInspector().show(this, null);
            return;
        }
        frame.getInspector().show(this, panel, panelContainer, panel.getName(),
                String.format(Translations.getString("PanelsPanel.Inspector.Subtitle"), //$NON-NLS-1$
                        panel.getChildren().size()),
                Ui.icon("layers", 16, Ui.accent()), //$NON-NLS-1$
                () -> List.of(new PropertySheetWizardAdapter(DefinitionForm.build(configuration, panel))));
    }

    private final WizardContainer panelContainer = new WizardContainer() {
        @Override
        public void wizardCompleted(Wizard wizard) {
            refreshSelectedRow();
            panelDefinitionPanel.updateSaveState();
        }

        @Override
        public void wizardCancelled(Wizard wizard) {
        }
    };

    public Panel getSelection() {
        List<Panel> selections = getSelections();
        if (selections.isEmpty()) {
            return null;
        }
        return selections.get(0);
    }

    public List<Panel> getSelections() {
        ArrayList<Panel> selections = new ArrayList<>();
        int[] selectedRows = panelsTable.getSelectedRows();
        for (int selectedRow : selectedRows) {
            selectedRow = panelsTable.convertRowIndexToModel(selectedRow);
            selections.add(configuration.getPanels().get(selectedRow));
        }
        return selections;
    }

    private static List<String> names(List<Panel> panels) {
        return panels.stream().map(Panel::getName).collect(Collectors.toList());
    }

    public final Action addPanelAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("PanelsPanel.Action.AddPanel")); //$NON-NLS-1$
            putValue(SMALL_ICON, Icons.add);
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelsPanel.Action.AddPanel.Description")); //$NON-NLS-1$
            putValue(MNEMONIC_KEY, KeyEvent.VK_A);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {}
    };

    public final Action addNewPanelAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("PanelsPanel.Action.AddPanel.NewPanel")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelsPanel.Action.AddPanel.NewPanel.Description")); //$NON-NLS-1$
            putValue(MNEMONIC_KEY, KeyEvent.VK_N);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            String title = Translations.getString("PanelsPanel.Action.AddPanel.NewPanel.SaveDialog"); //$NON-NLS-1$
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

                Panel panel = addPanel(file);

                selectPanel(panel);
            }
            catch (Exception e) {
                Logger.error(e, "Failed to create new panel.");
                MessageBoxes.errorBox(frame, 
                        Translations.getString("PanelsPanel.Action.AddPanel.NewPanel.ErrorMessage"), //$NON-NLS-1$
                        e.getMessage());
            }
        }
    };

    public final Action addExistingPanelAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("PanelsPanel.Action.AddPanel.ExistingPanel")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelsPanel.Action.AddPanel.ExistingPanel.Description")); //$NON-NLS-1$
            putValue(MNEMONIC_KEY, KeyEvent.VK_E);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            FileDialog fileDialog = FileDialogs.prepare(new FileDialog(frame),
                    Translations.getString("PanelsPanel.AddPanel.FileDialog.Title"), ".panel.xml"); //$NON-NLS-1$ //$NON-NLS-2$
            fileDialog.setVisible(true);
            try {
                if (fileDialog.getFile() == null) {
                    return;
                }
                File file = new File(new File(fileDialog.getDirectory()), fileDialog.getFile());

                Panel panel = addPanel(file);

                selectPanel(panel);
            }
            catch (Exception e) {
                Logger.error(e, "Failed to add existing panel {}.", fileDialog.getFile());
                MessageBoxes.errorBox(frame, 
                        Translations.getString("PanelsPanel.Action.AddPanel.ExistingPanel.ErrorMessage"), //$NON-NLS-1$
                        e.getMessage());
            }
        }
    };

    protected Panel addPanel(File file) throws Exception {
        Panel panel = configuration.getPanel(file);
        // TODO: Move to a list property listener.
        panelsTableModel.fireTableDataChanged();
        return panel;
    }
    
    /**
     * Takes the selected panels off the list after asking; their files stay. A panel the job or
     * another panel uses stays too, and the result says which.
     */
    public final Action removePanelAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.delete);
            putValue(NAME, Translations.getString("PanelsPanel.Action.RemovePanel")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelsPanel.Action.RemovePanel.Description")); //$NON-NLS-1$
            putValue(MNEMONIC_KEY, KeyEvent.VK_R);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            List<Panel> inUse = new ArrayList<>();
            List<Panel> removable = new ArrayList<>();
            for (Panel selection : getSelections()) {
                (configuration.isInUse(selection) ? inUse : removable).add(selection);
            }
            if (removable.isEmpty()) {
                if (!inUse.isEmpty()) {
                    MessageBoxes.errorBox(PanelsPanel.this, 
                            Translations.getString("PanelsPanel.Action.RemovePanel.ErrorBox.Title"),  //$NON-NLS-1$
                            String.format(Translations.getString("PanelsPanel.Action.RemovePanel.ErrorBox.Message"), //$NON-NLS-1$
                                    String.join(Translations.getString("DefinitionList.NameSeparator"), names(inUse)))); //$NON-NLS-1$
                }
                return;
            }
            if (!Dialogs.confirmRemove(frame, "Dialogs.Kind.Panels", names(removable))) { //$NON-NLS-1$
                return;
            }
            int removed = 0;
            for (Panel panel : removable) {
                if (configuration.removePanel(panel)) {
                    removed++;
                }
            }
            panelsTableModel.fireTableDataChanged();
            String result = String.format(Translations.getString("PanelsPanel.Removed"), removed); //$NON-NLS-1$
            if (!inUse.isEmpty()) {
                result += " \u00b7 " + String.format(Translations.getString("PanelsPanel.Removed.KeptInUse"), //$NON-NLS-1$ //$NON-NLS-2$
                        String.join(Translations.getString("DefinitionList.NameSeparator"), names(inUse))); //$NON-NLS-1$
            }
            frame.setStatus(result);
        }
    };
    
    public final Action copyPanelAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.copy);
            putValue(NAME, Translations.getString("PanelsPanel.Action.CopyPanel")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelsPanel.Action.CopyPanel.Description")); //$NON-NLS-1$
            // VK_COPY is the Copy key of a Sun keyboard, not a letter: the mnemonic never worked.
            putValue(MNEMONIC_KEY, KeyEvent.VK_C);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            Panel panelToCopy = getSelection();
            String title = Translations.getString("PanelsPanel.Action.CopyPanel.SaveDialog"); //$NON-NLS-1$
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

                Panel newPanel = new Panel(panelToCopy);
                newPanel.setDefinition(newPanel);
                newPanel.setFile(file);
                newPanel.setName(file.getName());
                newPanel.setDirty(false);
                configuration.addPanel(newPanel);
                configuration.savePanel(newPanel);
                panelsTableModel.fireTableDataChanged();
                selectPanel(newPanel);
                frame.setStatus(String.format(Translations.getString("PanelsPanel.Copied"), //$NON-NLS-1$
                        panelToCopy.getName(), DefinitionList.where(file)));
            }
            catch (Exception e) {
                Logger.error(e, "Failed to copy panel {}.", panelToCopy.getName());
                MessageBoxes.errorBox(frame, 
                        Translations.getString("PanelsPanel.Action.CopyPanel.ErrorMessage"),  //$NON-NLS-1$
                        e.getMessage());
            }
        }
    };

    /**
     * Takes every panel nothing uses off the list after naming them; says so when there is
     * nothing to take off.
     */
    public final Action cleanUpAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.clean);
            putValue(NAME, Translations.getString("PanelsPanel.Action.CleanUp")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PanelsPanel.Action.CleanUp.Description")); //$NON-NLS-1$
            putValue(MNEMONIC_KEY, KeyEvent.VK_D);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            // A panel used only by an unused panel goes with it: the list is asked again until
            // nothing more comes off.
            List<Panel> unused = new ArrayList<>();
            List<Panel> remaining = new ArrayList<>(configuration.getPanels());
            boolean found = true;
            while (found) {
                found = false;
                for (Panel panel : new ArrayList<>(remaining)) {
                    if (!inUseBy(panel, remaining)) {
                        unused.add(panel);
                        remaining.remove(panel);
                        found = true;
                    }
                }
            }
            if (unused.isEmpty()) {
                Dialogs.info(frame, Translations.getString("PanelsPanel.CleanUp.Nothing.Title"), //$NON-NLS-1$
                        Translations.getString("PanelsPanel.CleanUp.Nothing")); //$NON-NLS-1$
                return;
            }
            if (!Dialogs.confirmRemove(frame, "Dialogs.Kind.Panels", names(unused))) { //$NON-NLS-1$
                return;
            }
            int removed = 0;
            for (Panel panel : unused) {
                if (configuration.removePanel(panel)) {
                    removed++;
                }
            }
            panelsTableModel.fireTableDataChanged();
            frame.setStatus(String.format(Translations.getString("PanelsPanel.Removed"), removed)); //$NON-NLS-1$
        }
    };

    /** Whether the job uses the panel, or one of the panels that stay on the list does. */
    private boolean inUseBy(Panel panel, List<Panel> remaining) {
        org.openpnp.model.Job job = configuration.getJob();
        if (job != null && job.instanceCount(panel) > 0) {
            return true;
        }
        for (Panel other : remaining) {
            if (other.getDefinition() != panel.getDefinition() && other.getInstanceCount(panel) > 0) {
                return true;
            }
        }
        return false;
    }

}
