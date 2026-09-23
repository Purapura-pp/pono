package org.openpnp.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.pmw.tinylog.Logger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.table.TableRowSorter;

import org.openpnp.Translations;
import org.openpnp.gui.components.AutoSelectTextTable;
import org.openpnp.gui.shell.InspectorPanel;
import org.openpnp.gui.shell.PropertySheetPresenter;
import org.openpnp.gui.support.Helpers;
import org.openpnp.gui.support.Icons;
import org.openpnp.spi.PropertySheetHolder.PropertySheet;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.gui.support.MultisortTableHeaderCellRenderer;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.gui.tablemodel.VisionSettingsTableModel;
import org.openpnp.machine.reference.vision.AbstractPartAlignment;
import org.openpnp.machine.reference.vision.ReferenceFiducialLocator;
import org.openpnp.machine.reference.vision.wizards.BottomVisionSettingsConfigurationWizard;
import org.openpnp.machine.reference.vision.wizards.FiducialVisionSettingsConfigurationWizard;
import org.openpnp.model.AbstractVisionSettings;
import org.openpnp.model.BottomVisionSettings;
import org.openpnp.model.Configuration;
import org.openpnp.model.Configuration.VisionSettingsConfigurationHolder;
import org.openpnp.model.FiducialVisionSettings;
import org.openpnp.model.PartSettingsHolder;
import org.simpleframework.xml.Serializer;

@SuppressWarnings("serial")
public class VisionSettingsPanel extends JPanel implements WizardContainer {

    private static final String PREF_DIVIDER_POSITION = "VisionSettingsPanel.dividerPosition";

    private final Configuration configuration;
    private final Frame frame;
    protected AbstractVisionSettings selectedVisionSettings;
    /** Set while the selection is put back after the user kept unapplied edits. */
    private boolean revertingSelection;

    private VisionSettingsTableModel tableModel;
    private TableRowSorter<VisionSettingsTableModel> tableSorter;
    private JTable table;
    private JComboBox visionTypeFilter;

    public VisionSettingsPanel(Configuration configuration, Frame frame) {
        this.configuration = configuration;
        this.frame = frame;

        setLayout(new BorderLayout(0, 0));

        createAndAddToolbar();

        tableModel = new VisionSettingsTableModel(configuration);
        tableSorter = new TableRowSorter<>(tableModel);

        table = new AutoSelectTextTable(tableModel);

        // Enter edits the cell, Delete deletes what is selected, which asks first.

        org.openpnp.gui.support.TableUtils.bindKeys(table, deleteSettingsAction);
        org.openpnp.gui.components.AutoSelectTextTable.setEmptyText(table,
                Translations.getString("VisionSettingsPanel.Empty")); //$NON-NLS-1$
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        table.setRowSorter(tableSorter);
        table.getTableHeader().setDefaultRenderer(new MultisortTableHeaderCellRenderer());

        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        table.getColumnModel().getColumn(1).setPreferredWidth(600);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }

            SwingUtilities.invokeLater(() -> {
                if (revertingSelection) {
                    return;
                }
                AbstractVisionSettings selectedVisionSettings = getSelection();

                // One wizard, whichever kind of vision settings this is.
                Supplier<List<PropertySheet>> sheets = () -> {
                    List<PropertySheet> built = new ArrayList<>();
                    Wizard wizard = configurationWizardFor(selectedVisionSettings);
                    if (wizard != null) {
                        built.add(PropertySheetPresenter.sheet(wizard.getWizardName(),
                                (JPanel) wizard));
                    }
                    return built;
                };
                org.openpnp.gui.shell.PropertySheetPresenter.Result shown = MainFrame.get().getInspector()
                        .show(VisionSettingsPanel.this,
                        selectedVisionSettings, VisionSettingsPanel.this,
                        selectedVisionSettings == null ? null : selectedVisionSettings.getName(),
                        selectedVisionSettings == null
                                ? null
                                : InspectorPanel.typeOf(selectedVisionSettings),
                        Icons.captureCamera, sheets);
                if (shown == org.openpnp.gui.shell.PropertySheetPresenter.Result.Cancelled) {
                    // The user kept the unapplied edits: the settings they belong to stay selected.
                    revertingSelection = true;
                    try {
                        if (this.selectedVisionSettings != null) {
                            Helpers.selectObjectTableRow(table, this.selectedVisionSettings);
                        }
                        else {
                            table.clearSelection();
                        }
                    }
                    finally {
                        revertingSelection = false;
                    }
                    return;
                }
                if (shown == org.openpnp.gui.shell.PropertySheetPresenter.Result.Shown
                        && selectedVisionSettings != null) {
                    this.selectedVisionSettings = selectedVisionSettings;
                }
            });
        });
        tableModel.addTableModelListener(e -> {
            if (selectedVisionSettings != null) { 
                // Reselect previously selected settings.
                Helpers.selectObjectTableRow(table, selectedVisionSettings);
            }
        });
        filterTable();

        // The wizard of the selected settings is shown by the window's one properties column now.
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    private void createAndAddToolbar() {
        JPanel toolbarPanel = new JPanel();
        add(toolbarPanel, BorderLayout.NORTH);
        toolbarPanel.setLayout(new BorderLayout(0, 0));

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolbarPanel.add(toolBar);
        
        JPanel filterPanel = new JPanel();
        toolbarPanel.add(filterPanel, BorderLayout.EAST);
        
        JLabel lblFilterType = new JLabel(Translations.getString("VisionSettingsPanel.TypeLabel.text")); //$NON-NLS-1$
        filterPanel.add(lblFilterType);
        
        visionTypeFilter = new JComboBox(VisionTypeFilter.values());
        filterPanel.add(visionTypeFilter);
        visionTypeFilter.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent arg0) {
                filterTable();
            }
        });

        toolBar.add(newSettingsAction);
        toolBar.add(deleteSettingsAction);
        toolBar.addSeparator();
        toolBar.add(copyPackageToClipboardAction);
        toolBar.add(pastePackageToClipboardAction);

        toolBar.addSeparator();
    }

    protected enum VisionTypeFilter {
        BottomVision,
        FiducialVision
    }

    private AbstractVisionSettings getSelection() {
        List<AbstractVisionSettings> selections = getSelections();
        if (selections.size() != 1) {
            return null;
        }
        return selections.get(0);
    }

    private List<AbstractVisionSettings> getSelections() {
        List<AbstractVisionSettings> selections = new ArrayList<>();
        for (int selectedRow : table.getSelectedRows()) {
            selectedRow = table.convertRowIndexToModel(selectedRow);
            try {
                selections.add(tableModel.getRowObjectAt(selectedRow));
            }
            catch (IndexOutOfBoundsException e) {
                // sometimes this happens when deleting a row, if the gui state
                // updates after the model state
                Logger.warn("vision settings selection index {} out of bounds", selectedRow);
            }
        }
        return selections;
    }

    public final Action newSettingsAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.add);
            putValue(NAME, Translations.getString("VisionSettingsPanel.Action.NewSettings")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("VisionSettingsPanel.Action.NewSettings.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            final VisionTypeFilter filterType = (VisionTypeFilter) visionTypeFilter.getSelectedItem();
            AbstractVisionSettings visionSettings = null;
            switch (filterType) {
                case BottomVision:
                    visionSettings = new BottomVisionSettings();
                    break;
                case FiducialVision:
                    visionSettings = new FiducialVisionSettings();
                    break;
            }
            visionSettings.resetToDefault();
            visionSettings.setName(visionSettings.getClass().getSimpleName());
            configuration.addVisionSettings(visionSettings);
            Helpers.selectObjectTableRow(table, visionSettings);
        }
    };

    /**
     * Creates the wizard for a set of vision settings. This dispatches on the concrete type, the
     * same way newSettingsAction above decides which one to create, so that the settings classes
     * themselves do not have to name a wizard from the reference machine.
     * 
     * @param visionSettings
     * @return The wizard, or null if there is none for this type.
     */
    private static Wizard configurationWizardFor(AbstractVisionSettings visionSettings) {
        if (visionSettings instanceof BottomVisionSettings) {
            return new BottomVisionSettingsConfigurationWizard(
                    (BottomVisionSettings) visionSettings, null);
        }
        if (visionSettings instanceof FiducialVisionSettings) {
            return new FiducialVisionSettingsConfigurationWizard(
                    (FiducialVisionSettings) visionSettings, null);
        }
        return null;
    }

    public final Action deleteSettingsAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.delete);
            putValue(NAME, Translations.getString("VisionSettingsPanel.Action.DeleteSettings")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString(
                    "VisionSettingsPanel.Action.DeleteSettings.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            List<AbstractVisionSettings> selections = getSelections();

            List<PartSettingsHolder> usedIn = new ArrayList<>();
            for (AbstractVisionSettings settings : selections) {
                // A fiducial vision setting in use is as much in use as a bottom vision one; it
                // used to be deleted from under the parts that referred to it.
                usedIn.addAll(settings.getUsedIn());
            }

            if (!usedIn.isEmpty()) {
                String errorNames = new AbstractVisionSettings.ListConverter(false).convertForward(usedIn);
                MessageBoxes.errorBox(getTopLevelAncestor(), Translations.getString("CommonWords.error"), //$NON-NLS-1$
                        Translations.getString("CommonPhrases.selectionCannotBeDeletedUsedBy") //$NON-NLS-1$
                                + " " + errorNames + "."); //$NON-NLS-1$ //$NON-NLS-2$
                return;
            }

            List<String> names = selections.stream()
                    .map(s -> org.openpnp.gui.support.DisplayNames.visionSettingsName(s.getName()))
                    .collect(Collectors.toList());
            if (org.openpnp.gui.shell.Dialogs.confirmDelete(getTopLevelAncestor(),
                    "Dialogs.Kind.VisionSettings", names)) { //$NON-NLS-1$
                for (AbstractVisionSettings visionSettings : selections) {
                    configuration.removeVisionSettings(visionSettings);
                }
            }
        }
    };

    public final Action copyPackageToClipboardAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.copy);
            putValue(NAME, Translations.getString("VisionSettingsPanel.Action.CopySettingsToClipboard")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString(
                    "VisionSettingsPanel.Action.CopySettingsToClipboard.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            List<AbstractVisionSettings> visionSettings = getSelections();
            if (visionSettings.isEmpty()) {
                return;
            }
            try {
                VisionSettingsConfigurationHolder holder = new VisionSettingsConfigurationHolder();
                holder.visionSettings.addAll(visionSettings);
                Serializer s = Configuration.createSerializer();
                StringWriter w = new StringWriter();
                s.write(holder, w);
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
            putValue(NAME, Translations.getString("VisionSettingsPanel.Action.CreateSettingsFromClipboard")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString(
                    "VisionSettingsPanel.Action.CreateSettingsFromClipboard.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            try {
                Serializer ser = Configuration.createSerializer();
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                String s = (String) clipboard.getData(DataFlavor.stringFlavor);
                StringReader r = new StringReader(s);
                VisionSettingsConfigurationHolder holder = ser.read(VisionSettingsConfigurationHolder.class, s);
                table.clearSelection();
                for (AbstractVisionSettings visionSettings : holder.visionSettings) {
                    visionSettings.setId(Configuration.createId(visionSettings.getId().substring(0, 3)));
                    for (AbstractVisionSettings visionSettings2 : configuration.getVisionSettings()) {
                        if (visionSettings2.getName().equals(visionSettings.getName())) {
                            visionSettings.setName(visionSettings+" (Copy)");
                            break;
                        }
                    }
                    configuration.addVisionSettings(visionSettings);
                }
                Helpers.selectObjectTableRows(table, holder.visionSettings);
            }
            catch (Exception e) {
                MessageBoxes.errorBox(getTopLevelAncestor(),
                        Translations.getString("DialogMessages.PasteFailed"), e); //$NON-NLS-1$
            }
        }
    };

    @Override
    public void wizardCompleted(Wizard wizard) {

    }

    @Override
    public void wizardCancelled(Wizard wizard) {

    }

    public void selectVisionSettingsInTable(PartSettingsHolder partSettingsHolder) {
        final VisionTypeFilter filterType = (VisionTypeFilter) visionTypeFilter.getSelectedItem();
        AbstractVisionSettings visionSettings = null;
        switch (filterType) {
            case BottomVision:
                visionSettings = AbstractPartAlignment.getInheritedVisionSettings(partSettingsHolder, true);
                break;
            case FiducialVision:
                visionSettings = ReferenceFiducialLocator.getDefault().getInheritedVisionSettings(partSettingsHolder);
                break;
        }
        selectVisionSettingsInTable(visionSettings);
    }

    public void selectVisionSettingsInTable(AbstractVisionSettings visionSettings) {
        if (getSelection() != visionSettings) {
            Helpers.selectObjectTableRow(table, visionSettings);
        }
    }

    protected void filterTable() {
        final VisionTypeFilter filterType = (VisionTypeFilter) visionTypeFilter.getSelectedItem();
        tableSorter.setRowFilter(new RowFilter<VisionSettingsTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends VisionSettingsTableModel, ? extends Integer> entry) {
                AbstractVisionSettings visionSettings = tableModel.getRowObjectAt(entry.getIdentifier());
                switch (filterType) {
                    case BottomVision:
                        return visionSettings instanceof BottomVisionSettings;
                    case FiducialVision:
                        return visionSettings instanceof FiducialVisionSettings;
                    default: 
                        return false;
                }
            }
        });
    }
}
