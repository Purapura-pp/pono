package org.openpnp.gui;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;

import org.openpnp.Translations;
import org.openpnp.gui.components.AutoSelectTextTable;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.DockPanel;
import org.openpnp.gui.shell.DockRenderers;
import org.openpnp.gui.shell.PropertySheetPresenter;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.Helpers;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.gui.support.TableUtils;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.gui.tablemodel.VisionSettingsTableModel;
import org.openpnp.machine.reference.vision.AbstractPartAlignment;
import org.openpnp.machine.reference.vision.ReferenceFiducialLocator;
import org.openpnp.machine.reference.vision.wizards.VisionSettingsForm;
import org.openpnp.model.AbstractVisionSettings;
import org.openpnp.model.BottomVisionSettings;
import org.openpnp.model.Configuration;
import org.openpnp.model.Configuration.VisionSettingsConfigurationHolder;
import org.openpnp.model.FiducialVisionSettings;
import org.openpnp.model.PartSettingsHolder;
import org.openpnp.spi.PropertySheetHolder.PropertySheet;
import org.pmw.tinylog.Logger;
import org.simpleframework.xml.Serializer;

/**
 * The vision page, as mockup 11 draws it: one dock of the vision settings with a New for each
 * kind, delete, copy and paste and the filter; the kind as a tag, whether they are on as a switch,
 * the pipeline's stages and what uses them. The kind used to be a filter drop-down that also
 * decided what New made, and a new setting was called by its class name.
 */
@SuppressWarnings("serial")
public class VisionSettingsPanel extends JPanel implements WizardContainer {

    private final Configuration configuration;
    private final Frame frame;
    protected AbstractVisionSettings selectedVisionSettings;
    /** Set while the selection is put back after the user kept unapplied edits. */
    private boolean revertingSelection;

    private VisionSettingsTableModel tableModel;
    private TableRowSorter<VisionSettingsTableModel> tableSorter;
    private JTable table;
    private JTextField filter;
    private DockPanel.Tab settingsTab;

    public VisionSettingsPanel(Configuration configuration, Frame frame) {
        this.configuration = configuration;
        this.frame = frame;

        setLayout(new BorderLayout(0, 0));
        setOpaque(false);
        setBorder(new javax.swing.border.EmptyBorder(0, 10, 10, 10));
        putClientProperty(MainFrame.DOCK_PAGE, Boolean.TRUE);

        tableModel = new VisionSettingsTableModel(configuration);
        tableSorter = new TableRowSorter<>(tableModel);

        table = new AutoSelectTextTable(tableModel);

        // Enter edits the cell, Delete deletes what is selected, which asks first.
        TableUtils.bindKeys(table, deleteSettingsAction);
        AutoSelectTextTable.setEmptyText(table,
                Translations.getString("VisionSettingsPanel.Empty")); //$NON-NLS-1$
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setRowSorter(tableSorter);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            SwingUtilities.invokeLater(this::inspect);
        });
        tableModel.addTableModelListener(e -> {
            if (selectedVisionSettings != null) { 
                // Reselect previously selected settings.
                Helpers.selectObjectTableRow(table, selectedVisionSettings);
            }
            settingsTab.setCount(tableModel.getRowCount());
        });

        DockPanel.Toolbar toolbar = new DockPanel.Toolbar();
        toolbar.button(newBottomAction, "plus", "Dock.Action.NewBottomVision", Ui.Variant.Primary); //$NON-NLS-1$ //$NON-NLS-2$
        toolbar.button(newFiducialAction, "plus", "Dock.Action.NewFiducialVision"); //$NON-NLS-1$ //$NON-NLS-2$
        toolbar.iconButton(deleteSettingsAction, "trash"); //$NON-NLS-1$
        toolbar.separator();
        toolbar.button(copyPackageToClipboardAction, "copy", "Dock.Action.Copy"); //$NON-NLS-1$ //$NON-NLS-2$
        toolbar.button(pastePackageToClipboardAction, "upload", "Dock.Action.Paste"); //$NON-NLS-1$ //$NON-NLS-2$
        toolbar.glue();
        filter = toolbar.filter(Translations.getString("VisionSettingsPanel.Filter.Placeholder")); //$NON-NLS-1$
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void removeUpdate(DocumentEvent e) {
                filterTable();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                filterTable();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterTable();
            }
        });

        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        page.add(toolbar, BorderLayout.NORTH);
        page.add(DockPanel.table(table), BorderLayout.CENTER);
        // The mockup's cells: the name in bold, the kind as a tag, a switch, the stages in grey.
        table.getColumnModel().getColumn(VisionSettingsTableModel.NAME).setCellRenderer(DockRenderers.bold());
        table.getColumnModel().getColumn(VisionSettingsTableModel.TYPE).setCellRenderer(DockRenderers.status(
                v -> Chip.Tone.Accent, String::valueOf));
        table.getColumnModel().getColumn(VisionSettingsTableModel.ENABLED).setCellRenderer(DockRenderers.toggle());
        table.getColumnModel().getColumn(VisionSettingsTableModel.PIPELINE).setCellRenderer(DockRenderers.secondary());
        table.getColumnModel().getColumn(VisionSettingsTableModel.USED_IN).setCellRenderer(DockRenderers.secondary());
        TableUtils.installColumnWidthSavers(table, java.util.prefs.Preferences.userNodeForPackage(VisionSettingsPanel.class),
                "VisionSettingsPanel.visionSettingsTable"); //$NON-NLS-1$

        DockPanel dock = new DockPanel();
        settingsTab = dock.addTab(Ui.iconSm("eye"), //$NON-NLS-1$
                Translations.getString("VisionSettingsPanel.Tab.Settings"), page); //$NON-NLS-1$
        settingsTab.setCount(tableModel.getRowCount());
        dock.setMaximize(() -> MainFrame.get().toggleDockMaximised());
        add(dock, BorderLayout.CENTER);
    }

    /** The selected settings' form in the window's properties column. */
    private void inspect() {
        if (revertingSelection) {
            return;
        }
        AbstractVisionSettings selectedVisionSettings = getSelection();
        Supplier<List<PropertySheet>> sheets = () -> {
            List<PropertySheet> built = new ArrayList<>();
            Wizard wizard = configurationWizardFor(configuration, selectedVisionSettings);
            if (wizard != null) {
                built.add(new PropertySheetWizardAdapter(wizard));
            }
            return built;
        };
        PropertySheetPresenter.Result shown = MainFrame.get().getInspector()
                .show(VisionSettingsPanel.this,
                selectedVisionSettings, VisionSettingsPanel.this,
                selectedVisionSettings == null ? null
                        : org.openpnp.gui.support.DisplayNames.visionSettingsName(selectedVisionSettings.getName()),
                selectedVisionSettings == null ? null : subtitle(selectedVisionSettings),
                Ui.icon("eye", 16, Ui.accent()), sheets); //$NON-NLS-1$
        if (shown == PropertySheetPresenter.Result.Cancelled) {
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
        if (shown == PropertySheetPresenter.Result.Shown && selectedVisionSettings != null) {
            this.selectedVisionSettings = selectedVisionSettings;
        }
    }

    /** "Bottom vision · used by 6 parts and 8 packages". */
    private static String subtitle(AbstractVisionSettings settings) {
        int parts = 0;
        int packages = 0;
        for (PartSettingsHolder holder : settings.getUsedIn()) {
            if (holder instanceof org.openpnp.model.Part) {
                parts++;
            }
            else if (holder instanceof org.openpnp.model.Package) {
                packages++;
            }
        }
        String kind = VisionSettingsTableModel.kind(settings);
        if (parts == 0 && packages == 0) {
            return kind;
        }
        return kind + " \u00b7 " + String.format(Translations.getString("VisionSettingsPanel.UsedBy"), parts, packages); //$NON-NLS-1$ //$NON-NLS-2$
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

    /**
     * A name no settings have yet: the name itself, or with a number after it - "New bottom
     * vision 2". New settings used to be called by their class name, and a pasted copy of an
     * existing one got " (Copy)" in English.
     */
    String uniqueName(String name) {
        java.util.Set<String> taken = new java.util.HashSet<>();
        for (AbstractVisionSettings settings : configuration.getVisionSettings()) {
            taken.add(settings.getName());
        }
        if (!taken.contains(name)) {
            return name;
        }
        for (int n = 2;; n++) {
            String numbered = name + " " + n; //$NON-NLS-1$
            if (!taken.contains(numbered)) {
                return numbered;
            }
        }
    }

    private void newSettings(AbstractVisionSettings visionSettings, String nameKey) {
        visionSettings.resetToDefault();
        visionSettings.setName(uniqueName(Translations.getString(nameKey)));
        configuration.addVisionSettings(visionSettings);
        filter.setText(""); //$NON-NLS-1$
        Helpers.selectObjectTableRow(table, visionSettings);
    }

    public final Action newBottomAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.add);
            putValue(NAME, Translations.getString("VisionSettingsPanel.Action.NewBottom")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("VisionSettingsPanel.Action.NewBottom.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            newSettings(new BottomVisionSettings(), "VisionSettingsPanel.NewBottomName"); //$NON-NLS-1$
        }
    };

    public final Action newFiducialAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.add);
            putValue(NAME, Translations.getString("VisionSettingsPanel.Action.NewFiducial")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("VisionSettingsPanel.Action.NewFiducial.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            newSettings(new FiducialVisionSettings(), "VisionSettingsPanel.NewFiducialName"); //$NON-NLS-1$
        }
    };

    /**
     * The form of a set of vision settings shown by themselves, which is not a part's or a
     * package's: they cannot be made anyone's own from here.
     */
    private static Wizard configurationWizardFor(Configuration configuration, AbstractVisionSettings visionSettings) {
        if (visionSettings instanceof BottomVisionSettings) {
            return VisionSettingsForm.bottom(configuration, (BottomVisionSettings) visionSettings, null);
        }
        if (visionSettings instanceof FiducialVisionSettings) {
            return VisionSettingsForm.fiducial(configuration, (FiducialVisionSettings) visionSettings, null);
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
                VisionSettingsConfigurationHolder holder = ser.read(VisionSettingsConfigurationHolder.class, s);
                table.clearSelection();
                filter.setText(""); //$NON-NLS-1$
                for (AbstractVisionSettings visionSettings : holder.visionSettings) {
                    visionSettings.setId(Configuration.createId(visionSettings.getId().substring(0, 3)));
                    // A pasted copy of settings that are here already is "... copy", in the
                    // user's language, numbered if that is taken too.
                    boolean taken = configuration.getVisionSettings().stream()
                            .anyMatch(other -> other.getName().equals(visionSettings.getName()));
                    if (taken) {
                        visionSettings.setName(uniqueName(String.format(
                                Translations.getString("VisionSettingsPanel.CopyName"), visionSettings.getName()))); //$NON-NLS-1$
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
        tableModel.fireTableDataChanged();
    }

    @Override
    public void wizardCancelled(Wizard wizard) {

    }

    /**
     * The settings a part or a package uses, of the kind selected here now: bottom vision unless
     * fiducial vision is what is being looked at.
     */
    public void selectVisionSettingsInTable(PartSettingsHolder partSettingsHolder) {
        AbstractVisionSettings visionSettings = getSelection() instanceof FiducialVisionSettings
                ? ReferenceFiducialLocator.getDefault().getInheritedVisionSettings(partSettingsHolder)
                : AbstractPartAlignment.getInheritedVisionSettings(partSettingsHolder, true);
        selectVisionSettingsInTable(visionSettings);
    }

    public void selectVisionSettingsInTable(AbstractVisionSettings visionSettings) {
        if (getSelection() != visionSettings) {
            Helpers.selectObjectTableRow(table, visionSettings);
        }
    }

    /** The filter as the words typed, over the name, the kind, the stages and the use. */
    protected void filterTable() {
        String text = filter.getText().trim();
        tableSorter.setRowFilter(text.isEmpty() ? null
                : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text))); //$NON-NLS-1$
    }
}
