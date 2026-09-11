/*
 * Copyright (C) 2011 Jason von Nieda <jason@vonnieda.org>
 * 
 * This file is package of OpenPnP.
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
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableRowSorter;

import org.openpnp.Translations;
import org.openpnp.gui.components.AutoSelectTextTable;
import org.openpnp.gui.components.CameraView;
import org.openpnp.gui.support.ActionGroup;
import org.openpnp.gui.support.Helpers;
import org.openpnp.gui.shell.PropertySheetPresenter;
import org.openpnp.gui.shell.PropertySheetPresenter.Result;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.gui.support.MultisortTableHeaderCellRenderer;
import org.openpnp.gui.support.NamedListCellRenderer;
import org.openpnp.gui.support.NamedTableCellRenderer;
import org.openpnp.gui.support.VisionSettingsComboBoxModel;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.gui.tablemodel.PackagesTableModel;
import org.openpnp.gui.wizards.PackageCompositingWizard;
import org.openpnp.gui.wizards.PackageNozzleTipsWizard;
import org.openpnp.gui.wizards.PackageSettingsWizard;
import org.openpnp.gui.wizards.PackageVisionWizard;
import org.openpnp.model.AbstractVisionSettings;
import org.openpnp.model.BottomVisionSettings;
import org.openpnp.model.Configuration;
import org.openpnp.model.Configuration.TablesLinked;
import org.openpnp.model.FiducialVisionSettings;
import org.openpnp.model.Package;
import org.openpnp.model.Part;
import org.openpnp.spi.Camera;
import org.openpnp.spi.FiducialLocator;
import org.openpnp.spi.Machine;
import org.openpnp.spi.PartAlignment;
import org.openpnp.spi.PropertySheetHolder.PropertySheet;
import org.pmw.tinylog.Logger;
import org.simpleframework.xml.Serializer;

@SuppressWarnings("serial")
public class PackagesPanel extends JPanel implements WizardContainer {


    private static final String PREF_DIVIDER_POSITION = "PackagesPanel.dividerPosition";

    final private Configuration configuration;
    final private Frame frame;

    private PackagesTableModel tableModel;
    private TableRowSorter<PackagesTableModel> tableSorter;
    private JTextField searchTextField;
    private JTable table;
    private ActionGroup singleSelectionActionGroup;
    private ActionGroup multiSelectionActionGroup;
    private Package selectedPackage;

    public PackagesPanel(Configuration configuration, Frame frame) {
        this.configuration = configuration;
        this.frame = frame;

        singleSelectionActionGroup = new ActionGroup(deletePackageAction, copyPackageToClipboardAction);
        singleSelectionActionGroup.setEnabled(false);
        multiSelectionActionGroup = new ActionGroup(deletePackageAction);
        multiSelectionActionGroup.setEnabled(false);
        
        setLayout(new BorderLayout(0, 0));
        tableModel = new PackagesTableModel(configuration);
        tableSorter = new TableRowSorter<>(tableModel);

        JPanel toolbarAndSearch = new JPanel();
        add(toolbarAndSearch, BorderLayout.NORTH);
        toolbarAndSearch.setLayout(new BorderLayout(0, 0));

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolbarAndSearch.add(toolBar);

        JPanel panel_1 = new JPanel();
        toolbarAndSearch.add(panel_1, BorderLayout.EAST);

        JLabel lblSearch = new JLabel(Translations.getString("PackagesPanel.SearchLabel.text")); //$NON-NLS-1$
        panel_1.add(lblSearch);

        searchTextField = new JTextField();
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
        panel_1.add(searchTextField);
        searchTextField.setColumns(15);


        table = new AutoSelectTextTable(tableModel) {
            @Override
            public String getToolTipText(MouseEvent evt) {
                int column = convertColumnIndexToModel(columnAtPoint(evt.getPoint()));
                if(column==2) { return Translations.getString("PackagesTableModel.Column.TapeSpecification.toolTip"); } //$NON-NLS-1$
                return null;
            }
        };
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JComboBox<BottomVisionSettings> bottomVisionCombo = new JComboBox<>(
                new VisionSettingsComboBoxModel(BottomVisionSettings.class));
        bottomVisionCombo.setMaximumRowCount(20);
        bottomVisionCombo.setRenderer(new NamedListCellRenderer<>());
        table.setDefaultEditor(BottomVisionSettings.class,
                new DefaultCellEditor(bottomVisionCombo));

        JComboBox<BottomVisionSettings> fiducialVisionCombo = new JComboBox<>(
                new VisionSettingsComboBoxModel(FiducialVisionSettings.class));
        fiducialVisionCombo.setMaximumRowCount(20);
        fiducialVisionCombo.setRenderer(new NamedListCellRenderer<>());
        table.setDefaultEditor(FiducialVisionSettings.class,
                new DefaultCellEditor(fiducialVisionCombo));

        table.setDefaultRenderer(AbstractVisionSettings.class,
                new NamedTableCellRenderer<AbstractVisionSettings>());

        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            private int priorRow = -1;

            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) {
                    return;
                }
                int previous = priorRow;
                priorRow = table.getSelectedRow();
                if (!firePackageSelectionChanged() && previous >= 0
                        && previous < table.getRowCount()) {
                    // The user would not let go of unapplied edits.
                    priorRow = previous;
                    table.setRowSelectionInterval(previous, previous);
                }
            }
        });

        configuration.addPropertyChangeListener("visionSettings", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                // Handle vision settings changes like selection changes, as the inherited settings might change. 
                firePackageSelectionChanged();
            }
        });

        tableModel.addTableModelListener(e -> {
            if (selectedPackage != null) { 
                // Reselect previously selected settings.
                Helpers.selectObjectTableRow(table, selectedPackage);
            }
        });

        table.setRowSorter(tableSorter);
        table.getTableHeader().setDefaultRenderer(new MultisortTableHeaderCellRenderer());

        // The wizards of the selected package are shown by the window's one properties column now.
        add(new JScrollPane(table), BorderLayout.CENTER);

        toolBar.add(newPackageAction);
        toolBar.add(deletePackageAction);
        toolBar.addSeparator();
        toolBar.add(copyPackageToClipboardAction);
        toolBar.add(pastePackageToClipboardAction);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent e) {
                try {
                    Camera camera =
                            configuration.getMachine().getDefaultHead().getDefaultCamera();
                    CameraView cameraView = MainFrame.get().getCameraViews().getCameraView(camera);
                    if (cameraView == null) {
                        return;
                    }
                    cameraView.removeReticle(PackageVisionWizard.class.getName());
                }
                catch (Exception e1) {
                    Logger.debug(e1, "Failed to remove the package vision reticle from the camera view.");
                }
            }
        });
    }

    public Package getSelectedPackage() {
        List<Package> selections = getSelections();
        if (selections.size() != 1) {
            return null;
        }
        return selections.get(0);
    }

    private List<Package> getSelections() {
        List<Package> selections = new ArrayList<>();
        for (int selectedRow : table.getSelectedRows()) {
            selectedRow = table.convertRowIndexToModel(selectedRow);
            try {
                selections.add(tableModel.getRowObjectAt(selectedRow));
            }
            catch (IndexOutOfBoundsException e) {
                // sometimes this happens when deleting a row, if the gui state
                // updates after the model state
                Logger.warn("package selection index {} out of bounds", selectedRow);
            }
        }
        return selections;
    }
    private void search() {
        RowFilter<PackagesTableModel, Object> rf = null;
        // If current expression doesn't parse, don't update.
        try {
            rf = RowFilter.regexFilter("(?i)" + searchTextField.getText().trim());
        }
        catch (PatternSyntaxException e) {
            Logger.warn(e, "Search failed");
            return;
        }
        tableSorter.setRowFilter(rf);
    }

    public final Action newPackageAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.add);
            putValue(NAME, Translations.getString("PackagesPanel.Action.NewPackage")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PackagesPanel.Action.NewPackage.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            String id;
            while ((id = JOptionPane.showInputDialog(frame,
                    Translations.getString("PackagesPanel.NewPackage.EnterId"))) != null) { //$NON-NLS-1$
                id = id.trim();
                if (id.isEmpty()) {
                    break;
                }
                if (configuration.getPackage(id) != null) {
                    MessageBoxes.errorBox(frame, Translations.getString("General.Error"), //$NON-NLS-1$
                            String.format(
                                    Translations.getString("PackagesPanel.PackageIdExists"), id)); //$NON-NLS-1$
                    continue;
                }
                Package this_package = new Package(id);

                configuration.addPackage(this_package);
                tableModel.fireTableDataChanged();
                Helpers.selectObjectTableRow(table, this_package);
                break;
            }
        }
    };

    public final Action deletePackageAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.delete);
            putValue(NAME, Translations.getString("PackagesPanel.Action.DeletePackage")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PackagesPanel.Action.DeletePackage.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            // Check to make sure there are no parts using this package.
            List<Package> selections = getSelections();
            for (Package pkg : selections) {
                for (Part part : configuration.getParts()) {
                    if (part.getPackage() == pkg) {
                        MessageBoxes.errorBox(getTopLevelAncestor(),
                                Translations.getString("CommonWords.error"), //$NON-NLS-1$
                                pkg.getId() + " " + Translations.getString( //$NON-NLS-1$
                                        "CommonPhrases.cannotBeDeletedUsedBy" //$NON-NLS-1$
                                ) + " " + part.getId()); //$NON-NLS-1$
                        return;
                    }
                }
            }
            
            List<String> ids = selections.stream().map(Package::getId).collect(Collectors.toList());
            String formattedIds;
            if (ids.size() <= 3) {
                formattedIds = String.join(", ", ids);
            }
            else {
                formattedIds = String.join(", ", ids.subList(0, 3)) + ", and " + (ids.size() - 3) + " others";
            }
            
            int ret = JOptionPane.showConfirmDialog(getTopLevelAncestor(),
                    Translations.getString("DialogMessages.ConfirmDelete.text" //$NON-NLS-1$
                    ) + " " + formattedIds + "?", //$NON-NLS-1$ //$NON-NLS-1$
                    Translations.getString("DialogMessages.ConfirmDelete.title" //$NON-NLS-1$
                    ) + selections.size() + " " + Translations.getString(
                                    "CommonWords.packages") + "?", JOptionPane.YES_NO_OPTION); //$NON-NLS-1$ //$NON-NLS-2$
            if (ret == JOptionPane.YES_OPTION) {
                for (Package pkg : selections) {
                    configuration.removePackage(pkg);
                }
            }
        }
    };

    public final Action copyPackageToClipboardAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.copy);
            putValue(NAME, Translations.getString("PackagesPanel.Action.CopyPackage")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PackagesPanel.Action.CopyPackage.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            Package pkg = getSelectedPackage();
            if (pkg == null) {
                return;
            }
            try {
                Serializer s = Configuration.createSerializer();
                StringWriter w = new StringWriter();
                s.write(pkg, w);
                StringSelection stringSelection = new StringSelection(w.toString());
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(stringSelection, null);
            }
            catch (Exception e) {
                MessageBoxes.errorBox(getTopLevelAncestor(),
                        Translations.getString("DialogMessages.CopyFailed"), e); //$NON-NLS-1$
            }
        }
    };

    public final Action pastePackageToClipboardAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.paste);
            putValue(NAME, Translations.getString("PackagesPanel.Action.PastePackage")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PackagesPanel.Action.PastePackage.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            String id;
            while ((id = JOptionPane.showInputDialog(frame,
                    Translations.getString("PackagesPanel.PastePackage.EnterId"))) != null) { //$NON-NLS-1$
                id = id.trim();
                if (id.isEmpty()) {
                    break;
                }
                if (configuration.getPackage(id) == null) {
                    break;
                }
                MessageBoxes.errorBox(frame, Translations.getString("General.Error"), //$NON-NLS-1$
                        String.format(
                                Translations.getString("PackagesPanel.PackageIdExists"), id)); //$NON-NLS-1$
            }
            if (id == null || id.isEmpty()) {
                return;
            }
            try {
                try {
                    configuration.lockListeners();
                    Serializer ser = Configuration.createSerializer();
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    String s = (String) clipboard.getData(DataFlavor.stringFlavor);
                    StringReader r = new StringReader(s);
                    Package pkg = ser.read(Package.class, s);
                    pkg.setId(id);
                    configuration.addPackage(pkg);
                    tableModel.fireTableDataChanged();
                    Helpers.selectObjectTableRow(table, pkg);
                } finally {
                    configuration.unlockListeners();
                }
            }
            catch (Exception e) {
                MessageBoxes.errorBox(getTopLevelAncestor(),
                        Translations.getString("DialogMessages.PasteFailed"), e); //$NON-NLS-1$
            }
        }
    };

    @Override
    public void wizardCompleted(Wizard wizard) {}

    @Override
    public void wizardCancelled(Wizard wizard) {}

    /**
     * @return false when the user refused to leave unapplied edits behind, so that a caller
     *         reacting to a selection change can put the selection back.
     */
    public boolean firePackageSelectionChanged() {
        List<Package> selections = getSelections();

        if (selections.size() > 1) {
            singleSelectionActionGroup.setEnabled(false);
            multiSelectionActionGroup.setEnabled(true);
        }
        else {
            multiSelectionActionGroup.setEnabled(false);
            singleSelectionActionGroup.setEnabled(!selections.isEmpty());
        }

        Package selectedPackage = getSelectedPackage();
        if (selectedPackage != null) {
            this.selectedPackage = selectedPackage; 
        }

        // A package reports no property sheets of its own: four of these wizards are built here
        // and the rest come from the machine's part alignments and its fiducial locator.
        List<PropertySheet> sheets = new ArrayList<>();
        if (selectedPackage != null) {
            sheets.add(PropertySheetPresenter.sheet(
                    Translations.getString("PackagesPanel.NozzleTipsTab.title"), //$NON-NLS-1$
                    new PackageNozzleTipsWizard(selectedPackage)));
            sheets.add(PropertySheetPresenter.sheet(
                    Translations.getString("PackagesPanel.SettingsTab.title"), //$NON-NLS-1$
                    new PackageSettingsWizard(selectedPackage)));
            sheets.add(PropertySheetPresenter.sheet(
                    Translations.getString("PackagesPanel.VisionTab.title"), //$NON-NLS-1$
                    new PackageVisionWizard(selectedPackage)));
            sheets.add(PropertySheetPresenter.sheet(
                    Translations.getString("PackagesPanel.VisionCompositingTab.title"), //$NON-NLS-1$
                    new PackageCompositingWizard(selectedPackage)));
            Machine machine = configuration.getMachine();
            for (PartAlignment partAlignment : machine.getPartAlignments()) {
                Wizard wizard = partAlignment.getPartConfigurationWizard(selectedPackage);
                if (wizard != null) {
                    sheets.add(PropertySheetPresenter.sheet(wizard.getWizardName(),
                            (JPanel) wizard));
                }
            }
            Wizard wizard =
                    machine.getFiducialLocator().getPartConfigurationWizard(selectedPackage);
            if (wizard != null) {
                sheets.add(PropertySheetPresenter.sheet(wizard.getWizardName(), (JPanel) wizard));
            }
        }
        Package shownPackage = getSelectedPackage();
        if (MainFrame.get().getInspector().show(shownPackage, PackagesPanel.this,
                shownPackage == null ? null : shownPackage.getId(),
                Translations.getString("MainFrame.RightComponent.tabs.Packages"), //$NON-NLS-1$
                Icons.footprintQuad, sheets) == Result.Cancelled) {
            return false;
        }

        if (selectedPackage != null) {
            MainFrame mainFrame = MainFrame.get();
            if (mainFrame.getNavigation().getSelectedComponent() == mainFrame.getPackagesTab() 
                    && configuration.getTablesLinked() == TablesLinked.Linked) {
                 mainFrame.getVisionSettingsTab().selectVisionSettingsInTable(selectedPackage);
            }
        }

        revalidate();
        repaint();
        return true;
    }

    public void selectPackageInTable(Package packag) {
        if (getSelectedPackage() != packag) {
            Helpers.selectObjectTableRow(table, packag);
        }
    }
}
