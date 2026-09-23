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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
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
import org.openpnp.gui.support.ActionGroup;
import org.openpnp.gui.support.Helpers;
import org.openpnp.gui.shell.PropertySheetPresenter;
import org.openpnp.gui.shell.PropertySheetPresenter.Result;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.IdentifiableListCellRenderer;
import org.openpnp.gui.support.IdentifiableTableCellRenderer;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.gui.support.MultisortTableHeaderCellRenderer;
import org.openpnp.gui.support.NamedListCellRenderer;
import org.openpnp.gui.support.NamedTableCellRenderer;
import org.openpnp.gui.support.PackagesComboBoxModel;
import org.openpnp.gui.support.VisionSettingsComboBoxModel;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.gui.tablemodel.PartsTableModel;
import org.openpnp.model.AbstractVisionSettings;
import org.openpnp.model.BottomVisionSettings;
import org.openpnp.model.Configuration;
import org.openpnp.model.Configuration.TablesLinked;
import org.openpnp.model.FiducialVisionSettings;
import org.openpnp.model.Part;
import org.openpnp.spi.Feeder;
import org.openpnp.spi.FiducialLocator;
import org.openpnp.spi.PartAlignment;
import org.openpnp.spi.PropertySheetHolder.PropertySheet;
import org.openpnp.util.UiUtils;
import org.openpnp.util.FeederUtils;
import org.pmw.tinylog.Logger;
import org.simpleframework.xml.Serializer;

@SuppressWarnings("serial")
public class PartsPanel extends JPanel implements WizardContainer {


    private static final String PREF_DIVIDER_POSITION = "PartsPanel.dividerPosition";

    final private Configuration configuration;
    final private Frame frame;

    private PartsTableModel tableModel;
    private TableRowSorter<PartsTableModel> tableSorter;
    private JTextField searchTextField;
    private JTable table;
    private ActionGroup singleSelectionActionGroup;
    private ActionGroup multiSelectionActionGroup;
    private Part selectedPart;
    private int priorRowIndex = -1;
    private final org.openpnp.gui.shell.DockPanel dock = new org.openpnp.gui.shell.DockPanel();
    private org.openpnp.gui.shell.DockPanel.Tab partsTab;
    private org.openpnp.gui.shell.DockPanel.Tab unusedTab;
    /** Where the note on a part just made goes, over the table. */
    private final JPanel bannerHolder = new JPanel(new BorderLayout());
    private HashMap<Class, Integer> lastSelectedTabIndex = new HashMap<>();

    public PartsPanel(Configuration configuration, Frame frame) {
        this.configuration = configuration;
        this.frame = frame;

        singleSelectionActionGroup = new ActionGroup(deletePartAction, pickPartAction, copyPartToClipboardAction);
        singleSelectionActionGroup.setEnabled(false);
        multiSelectionActionGroup = new ActionGroup(deletePartAction);
        multiSelectionActionGroup.setEnabled(false);

        setLayout(new BorderLayout(0, 0));
        setOpaque(false);
        setBorder(new javax.swing.border.EmptyBorder(0, 10, 10, 10));
        putClientProperty(MainFrame.DOCK_PAGE, Boolean.TRUE);
        tableModel = new PartsTableModel(configuration);
        tableSorter = new TableRowSorter<>(tableModel);

        // The mockup's toolbar: New part in the accent, delete, copy and paste, the pick that
        // moves the machine, and the filter at the right end.
        org.openpnp.gui.shell.DockPanel.Toolbar toolBar = new org.openpnp.gui.shell.DockPanel.Toolbar();
        toolBar.button(newPartAction, "plus", "Dock.Action.NewPart", org.openpnp.gui.shell.Ui.Variant.Primary); //$NON-NLS-1$ //$NON-NLS-2$
        toolBar.iconButton(deletePartAction, "trash"); //$NON-NLS-1$
        toolBar.separator();
        toolBar.button(copyPartToClipboardAction, "copy", "Dock.Action.Copy"); //$NON-NLS-1$ //$NON-NLS-2$
        toolBar.button(pastePartToClipboardAction, "upload", "Dock.Action.Paste"); //$NON-NLS-1$ //$NON-NLS-2$
        toolBar.separator();
        org.openpnp.gui.shell.Ui.movesMachine(toolBar.button(pickPartAction, "nozzle", "Dock.Action.PickPart")); //$NON-NLS-1$ //$NON-NLS-2$
        toolBar.glue();
        searchTextField = toolBar.filter(Translations.getString("PartsPanel.Filter.Placeholder")); //$NON-NLS-1$
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
        JComboBox packagesCombo = new JComboBox(new PackagesComboBoxModel());
        packagesCombo.setMaximumRowCount(20);
        packagesCombo.setRenderer(new IdentifiableListCellRenderer<org.openpnp.model.Package>());

        table = new AutoSelectTextTable(tableModel) {
            @Override
            public String getToolTipText(MouseEvent evt) {
                int column = convertColumnIndexToModel(columnAtPoint(evt.getPoint()));
                if(column==2) { return Translations.getString("PartsTableModel.Column.Height.toolTip"); } //$NON-NLS-1$
                if(column==3) { return Translations.getString("PartsTableModel.Column.ThroughBoardDepth.toolTip"); } //$NON-NLS-1$
                return null;
            }
        };
        // Enter edits the cell, Delete deletes what is selected, which asks first.
        org.openpnp.gui.support.TableUtils.bindKeys(table, deletePartAction);
        org.openpnp.gui.components.AutoSelectTextTable.setEmptyText(table,
                Translations.getString("PartsPanel.Empty")); //$NON-NLS-1$
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setDefaultEditor(org.openpnp.model.Package.class,
                new DefaultCellEditor(packagesCombo));
        table.setDefaultRenderer(org.openpnp.model.Package.class,
                new IdentifiableTableCellRenderer<org.openpnp.model.Package>());

        JComboBox<BottomVisionSettings> bottomVisionCombo = new JComboBox<>(
                new VisionSettingsComboBoxModel(BottomVisionSettings.class));
        bottomVisionCombo.setMaximumRowCount(20);
        bottomVisionCombo.setRenderer(new NamedListCellRenderer<>());
        table.setDefaultEditor(BottomVisionSettings.class,
                new DefaultCellEditor(bottomVisionCombo));

        JComboBox<FiducialVisionSettings> fiducialVisionCombo = new JComboBox<>(
                new VisionSettingsComboBoxModel(FiducialVisionSettings.class));
        fiducialVisionCombo.setMaximumRowCount(20);
        fiducialVisionCombo.setRenderer(new NamedListCellRenderer<>());
        table.setDefaultEditor(FiducialVisionSettings.class,
                new DefaultCellEditor(fiducialVisionCombo));

        table.setDefaultRenderer(AbstractVisionSettings.class,
                new NamedTableCellRenderer<AbstractVisionSettings>());

        table.setRowSorter(tableSorter);
        installRenderers();
        org.openpnp.gui.support.TableUtils.installColumnWidthSavers(table,
                java.util.prefs.Preferences.userNodeForPackage(PartsPanel.class), "PartsPanel.partsTable"); //$NON-NLS-1$
        // The mockup's order: ID, name, package, height, speed, the two visions, feeders,
        // placements. The through-board depth is in the part's form.
        int[] order = { 0, 1, 4, 2, 5, 6, 7, 9, 8 };
        for (int i = 0; i < order.length; i++) {
            int view = table.convertColumnIndexToView(order[i]);
            if (view >= 0 && view != i) {
                table.getColumnModel().moveColumn(view, i);
            }
        }

        // The wizards of the selected part are shown by the window's one properties column now.
        // The table and its toolbar are the dock's first tab; the second shows only the parts no
        // placement uses, and the table moves between the two holders as the tabs change.
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        page.add(toolBar, BorderLayout.NORTH);
        bannerHolder.setOpaque(false);
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(bannerHolder, BorderLayout.NORTH);
        center.add(org.openpnp.gui.shell.DockPanel.table(table), BorderLayout.CENTER);
        page.add(center, BorderLayout.CENTER);
        JPanel allHolder = new JPanel(new BorderLayout());
        allHolder.setOpaque(false);
        allHolder.add(page, BorderLayout.CENTER);
        JPanel unusedHolder = new JPanel(new BorderLayout());
        unusedHolder.setOpaque(false);
        partsTab = dock.addTab(org.openpnp.gui.shell.Ui.iconSm("parts"), //$NON-NLS-1$
                Translations.getString("PartsPanel.Tab.Parts"), allHolder); //$NON-NLS-1$
        unusedTab = dock.addTab(org.openpnp.gui.shell.Ui.iconSm("alert"), //$NON-NLS-1$
                Translations.getString("PartsPanel.Tab.Unused"), unusedHolder); //$NON-NLS-1$
        dock.addChangeListener(e -> {
            JPanel holder = dock.getSelectedTab() == unusedTab ? unusedHolder : allHolder;
            if (page.getParent() != holder) {
                holder.add(page, BorderLayout.CENTER);
            }
            search();
            dock.revalidate();
            dock.repaint();
        });
        dock.setMaximize(() -> MainFrame.get().toggleDockMaximised());
        tableModel.addTableModelListener(e -> countTabs());
        add(dock, BorderLayout.CENTER);

        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) {
                    return;
                }
                if (table.getSelectedRow() != priorRowIndex) {
                    int previous = priorRowIndex;
                    priorRowIndex = table.getSelectedRow();
                    if (!updateWizards() && previous >= 0
                            && previous < table.getRowCount()) {
                        // The user would not let go of unapplied edits.
                        priorRowIndex = previous;
                        table.setRowSelectionInterval(previous, previous);
                    }
                }
            }
        });
        
        configuration.addPropertyChangeListener("visionSettings", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                // Handle vision settings changes like selection changes, as the inherited settings might change. 
                updateWizards();
            }
        });

        tableModel.addTableModelListener(e -> {
            if (selectedPart != null && getSelectedPart() != selectedPart) { 
                // Reselect previously selected settings.
                Helpers.selectObjectTableRow(table, selectedPart);
            }
        });
    }

    public Part getSelectedPart() {
        List<Part> selections = getSelections();
        if (selections.size() != 1) {
            return null;
        }
        return selections.get(0);
    }

    private List<Part> getSelections() {
        List<Part> selections = new ArrayList<>();
        for (int selectedRow : table.getSelectedRows()) {
            selectedRow = table.convertRowIndexToModel(selectedRow);
            try {
                selections.add(tableModel.getRowObjectAt(selectedRow));
            }
            catch (IndexOutOfBoundsException e) {
                // sometimes this happens when deleting a row, if the gui state
                // updates after the model state
                Logger.warn("part selection index {} out of bounds", selectedRow);
            }
        }
        return selections;
    }

    /**
     * The filter matches the text as typed: it was read as a regular expression, so "(" matched
     * nothing and said nothing. The unused tab shows the parts no placement uses.
     */
    private void search() {
        String text = searchTextField.getText().trim();
        List<RowFilter<PartsTableModel, Object>> filters = new ArrayList<>();
        if (!text.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text))); //$NON-NLS-1$
        }
        if (dock.getSelectedTab() == unusedTab) {
            filters.add(new RowFilter<PartsTableModel, Object>() {
                @Override
                public boolean include(Entry<? extends PartsTableModel, ? extends Object> entry) {
                    return entry.getModel().getRowObjectAt((Integer) entry.getIdentifier()).getPlacementCount() == 0;
                }
            });
        }
        tableSorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
    }

    private Part lastSelected;

    /** "已新建元件 NEW-1 · 封装沿用了上次选的 R0603，高度 0.45 mm", with the undo. */
    private void showNewPartBanner(Part part, boolean fromSelection) {
        bannerHolder.removeAll();
        org.openpnp.gui.shell.RoundedPanel banner = new org.openpnp.gui.shell.RoundedPanel(10,
                org.openpnp.gui.shell.Ui::accentSoft, org.openpnp.gui.shell.Ui::border);
        banner.setLayout(new BorderLayout(10, 0));
        banner.setBorder(new javax.swing.border.EmptyBorder(8, 12, 8, 8));
        JLabel icon = new JLabel(org.openpnp.gui.shell.Ui.icon("info", 16, org.openpnp.gui.shell.Ui.accent())); //$NON-NLS-1$
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
        JLabel title = new JLabel(String.format(Translations.getString("PartsPanel.NewPart.Banner.Title"), part.getId())); //$NON-NLS-1$
        title.setFont(org.openpnp.gui.shell.Ui.weighted(org.openpnp.gui.shell.Ui.BASE, 600));
        String height = part.getHeight() == null ? "\u2014" //$NON-NLS-1$
                : String.format(java.util.Locale.ROOT, "%.2f mm", //$NON-NLS-1$
                        part.getHeight().convertToUnits(org.openpnp.model.LengthUnit.Millimeters).getValue());
        text.add(title);
        text.add(org.openpnp.gui.shell.Ui.t2(String.format(Translations.getString(fromSelection
                ? "PartsPanel.NewPart.Banner.FromSelection" : "PartsPanel.NewPart.Banner.First"), //$NON-NLS-1$ //$NON-NLS-2$
                part.getPackage().getId(), height)));
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new javax.swing.BoxLayout(actions, javax.swing.BoxLayout.X_AXIS));
        JButton undo = org.openpnp.gui.shell.Ui.button(Translations.getString("PartsPanel.NewPart.Banner.Undo"), //$NON-NLS-1$
                null, org.openpnp.gui.shell.Ui.Size.Xs, org.openpnp.gui.shell.Ui.Variant.Ghost);
        undo.addActionListener(e -> {
            configuration.removePart(part);
            tableModel.fireTableDataChanged();
            hideBanner();
        });
        JButton close = org.openpnp.gui.shell.Ui.iconButton(org.openpnp.gui.shell.Ui.iconSm("x"), //$NON-NLS-1$
                org.openpnp.gui.shell.Ui.Size.Xs, org.openpnp.gui.shell.Ui.Variant.Ghost,
                Translations.getString("PartsPanel.NewPart.Banner.Close")); //$NON-NLS-1$
        close.addActionListener(e -> hideBanner());
        actions.add(undo);
        actions.add(close);
        banner.add(icon, BorderLayout.WEST);
        banner.add(text, BorderLayout.CENTER);
        banner.add(actions, BorderLayout.EAST);
        bannerHolder.setBorder(new javax.swing.border.EmptyBorder(8, 10, 0, 10));
        bannerHolder.add(banner, BorderLayout.CENTER);
        bannerHolder.revalidate();
        bannerHolder.repaint();
    }

    private void hideBanner() {
        bannerHolder.removeAll();
        bannerHolder.setBorder(null);
        bannerHolder.revalidate();
        bannerHolder.repaint();
    }

    private void countTabs() {
        if (partsTab == null) {
            return;
        }
        int unused = 0;
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            if (tableModel.getRowObjectAt(row).getPlacementCount() == 0) {
                unused++;
            }
        }
        partsTab.setCount(tableModel.getRowCount());
        unusedTab.setCount(unused);
    }

    /** The mockup's cells: the ID in bold, the name in the secondary colour, numbers in mono. */
    private void installRenderers() {
        javax.swing.table.DefaultTableCellRenderer bold = new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setFont(table.getFont().deriveFont(java.awt.Font.BOLD));
                return this;
            }
        };
        javax.swing.table.DefaultTableCellRenderer secondary = new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    setForeground(org.openpnp.gui.shell.Ui.text2());
                }
                return this;
            }
        };
        table.getColumnModel().getColumn(0).setCellRenderer(bold);
        table.getColumnModel().getColumn(1).setCellRenderer(secondary);
        org.openpnp.gui.support.MonospacedFontTableCellRenderer mono = new org.openpnp.gui.support.MonospacedFontTableCellRenderer();
        mono.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        for (int column : new int[] { 2, 3, 5, 8, 9 }) {
            table.getColumnModel().getColumn(column).setCellRenderer(mono);
        }
    }

    public final Action newPartAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.add);
            putValue(NAME, Translations.getString("PartsPanel.Action.NewPart")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PartsPanel.Action.NewPart.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            if (configuration.getPackages().size() == 0) {
                MessageBoxes.errorBox(getTopLevelAncestor(),
                        Translations.getString("General.Error"), //$NON-NLS-1$
                        Translations.getString("PartsPanel.NewPart.NoPackages")); //$NON-NLS-1$
                return;
            }

            String id;
            while ((id = JOptionPane.showInputDialog(frame,
                    Translations.getString("PartsPanel.NewPart.EnterId"))) != null) { //$NON-NLS-1$
                id = id.trim();
                if (id.isEmpty()) {
                    break;
                }
                if (configuration.getPart(id) != null) {
                    MessageBoxes.errorBox(frame, Translations.getString("General.Error"), //$NON-NLS-1$
                            String.format(Translations.getString("PartsPanel.PartIdExists"), id)); //$NON-NLS-1$
                    continue;
                }
                Part part = new Part(id);
                // The package of the part selected before, and its height with it, rather than
                // silently the first package there is.
                Part template = getSelectedPart() != null ? getSelectedPart() : lastSelected;
                org.openpnp.model.Package pkg = template != null && template.getPackage() != null
                        ? template.getPackage() : configuration.getPackages().get(0);
                part.setPackage(pkg);
                if (template != null && template.getPackage() == pkg && template.getHeight() != null) {
                    part.setHeight(template.getHeight());
                }

                configuration.addPart(part);
                tableModel.fireTableDataChanged();
                Helpers.selectObjectTableRow(table, part);
                showNewPartBanner(part, template != null);
                break;
            }
        }
    };

    public final Action deletePartAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.delete);
            putValue(NAME, Translations.getString("PartsPanel.Action.DeletePart")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PartsPanel.Action.DeletePart.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            List<Part> selections = getSelections();
            List<String> ids = selections.stream().map(Part::getId).collect(Collectors.toList());
            if (org.openpnp.gui.shell.Dialogs.confirmDelete(getTopLevelAncestor(),
                    "Dialogs.Kind.Parts", ids)) { //$NON-NLS-1$
                for (Part part : selections) {
                    configuration.removePart(part);
                }
            }
        }
    };

    public final Action pickPartAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.pick);
            putValue(NAME, Translations.getString("PartsPanel.Action.PickPart")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PartsPanel.Action.PickPart.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                Part part = getSelectedPart();
                Feeder feeder = FeederUtils.findFeeder(configuration.getMachine(),part,null,null);
                if (feeder == null) {
                    throw new Exception(String.format(
                            Translations.getString("PartsPanel.PickPart.NoFeeder"), part.getId())); //$NON-NLS-1$
                }
                // Perform the whole Job like pick cycle as in the FeedersPanel. 
                MainFrame.get().getFeedersTab().pickFeeder(feeder);
            });
        }
    };

    public final Action copyPartToClipboardAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.copy);
            putValue(NAME, Translations.getString("PartsPanel.Action.CopyPartToClipboard")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PartsPanel.Action.CopyPartToClipboard.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            Part part = getSelectedPart();
            if (part == null) {
                return;
            }
            try {
                Serializer s = Configuration.createSerializer();
                StringWriter w = new StringWriter();
                s.write(part, w);
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

    public final Action pastePartToClipboardAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.paste);
            putValue(NAME, Translations.getString("PartsPanel.Action.PastePartFromClipboard")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("PartsPanel.Action.PastePartFromClipboard.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            String id;
            while ((id = JOptionPane.showInputDialog(frame,
                    Translations.getString("PartsPanel.PastePart.EnterId"))) != null) { //$NON-NLS-1$
                id = id.trim();
                if (id.isEmpty()) {
                    break;
                }
                if (configuration.getPart(id) == null) {
                    break;
                }
                MessageBoxes.errorBox(frame, Translations.getString("General.Error"), //$NON-NLS-1$
                        String.format(Translations.getString("PartsPanel.PartIdExists"), id)); //$NON-NLS-1$
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
                    Part part = ser.read(Part.class, s);
                    part.setId(id);
                    configuration.addPart(part);
                    tableModel.fireTableDataChanged();
                    Helpers.selectLastTableRow(table);
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

    /**
     * @return false when the user refused to leave unapplied edits behind, so that a caller
     *         reacting to a selection change can put the selection back.
     */
    public boolean updateWizards() {
        List<Part> selections = getSelections();

        if (selections.size() > 1) {
            singleSelectionActionGroup.setEnabled(false);
            multiSelectionActionGroup.setEnabled(true);
        }
        else {
            multiSelectionActionGroup.setEnabled(false);
            singleSelectionActionGroup.setEnabled(!selections.isEmpty());
        }

        Part selectedPart = getSelectedPart();
        this.selectedPart = selectedPart;
        if (selectedPart != null) {
            lastSelected = selectedPart;
        }

        // A part reports no property sheets of its own: its wizards come from the machine's part
        // alignments and its fiducial locator, so they are assembled here and handed over.
        Supplier<List<PropertySheet>> sheets = () -> {
            List<PropertySheet> built = new ArrayList<>();
            built.add(new org.openpnp.gui.support.PropertySheetWizardAdapter(
                    PartForm.build(configuration, selectedPart),
                    Translations.getString("PartsPanel.SettingsTab.title"))); //$NON-NLS-1$
            for (PartAlignment partAlignment : configuration.getMachine().getPartAlignments()) {
                Wizard wizard = partAlignment.getPartConfigurationWizard(selectedPart);
                if (wizard != null) {
                    built.add(PropertySheetPresenter.sheet(wizard.getWizardName(),
                            (JPanel) wizard));
                }
            }
            Wizard wizard =
                    configuration.getMachine().getFiducialLocator()
                            .getPartConfigurationWizard(selectedPart);
            if (wizard != null) {
                built.add(PropertySheetPresenter.sheet(wizard.getWizardName(), (JPanel) wizard));
            }
            return built;
        };
        MainFrame mainFrame = MainFrame.get();
        Result shown = mainFrame.getInspector().show(PartsPanel.this, selectedPart,
                PartsPanel.this,
                selectedPart == null ? null : selectedPart.getId(),
                selectedPart == null || selectedPart.getPackage() == null
                        ? null
                        : selectedPart.getPackage().getId(),
                Icons.footprintDual, sheets);
        if (shown == Result.Busy) {
            return true;
        }
        if (shown == Result.Cancelled) {
            return false;
        }

        if (selectedPart != null) {
            if (mainFrame.getNavigation().getSelectedComponent() == mainFrame.getPartsTab() 
                    && configuration.getTablesLinked() == TablesLinked.Linked) {
                mainFrame.getPackagesTab().selectPackageInTable(selectedPart.getPackage());
                mainFrame.getFeedersTab().selectFeederForPart(selectedPart);
                mainFrame.getVisionSettingsTab().selectVisionSettingsInTable(selectedPart);
            }
        }
        return true;
    }

    public void selectPartInTableAndUpdateLinks(Part part) {
        selectPartInTable(part);

        if(configuration.getTablesLinked() == TablesLinked.Linked)
        {
            MainFrame mainFrame = MainFrame.get();
            mainFrame.getPartsTab().selectPartInTable(part);
            if (part != null) {
                mainFrame.getPackagesTab().selectPackageInTable(part.getPackage());
            }
            mainFrame.getFeedersTab().selectFeederForPart(part);
            mainFrame.getVisionSettingsTab().selectVisionSettingsInTable(part);
        }
    }

    public void selectPartInTable(Part part) {
        if (getSelectedPart() != part) {
            Helpers.selectObjectTableRow(table, part);
        }
    }

    @Override
    public void wizardCompleted(Wizard wizard) {}

    @Override
    public void wizardCancelled(Wizard wizard) {}
}
