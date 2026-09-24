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
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultCellEditor;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

import org.openpnp.Translations;
import org.openpnp.events.FeederSelectedEvent;
import org.openpnp.gui.components.AutoSelectTextTable;
import org.openpnp.gui.components.ClassSelectionDialog;
import org.openpnp.gui.support.ActionGroup;
import org.openpnp.gui.support.FeederDescriptions;
import org.openpnp.gui.support.Helpers;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.shell.DockPanel;
import org.openpnp.gui.shell.DockRenderers;
import org.openpnp.gui.shell.PillBar;
import org.openpnp.gui.shell.PropertySheetPresenter.Result;
import org.openpnp.gui.shell.RoundedPanel;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.gui.tablemodel.FeedersTableModel;
import org.openpnp.machine.reference.vision.AbstractPartAlignment;
import org.openpnp.machine.reference.vision.ReferenceBottomVision;
import org.openpnp.machine.reference.ReferenceFeeder;
import org.openpnp.model.BoardLocation;
import org.openpnp.model.Configuration;
import org.openpnp.model.Configuration.TablesLinked;
import org.openpnp.model.Job;
import org.openpnp.model.Length;
import org.openpnp.model.Location;
import org.openpnp.model.Part;
import org.openpnp.model.Placement;
import org.openpnp.model.Placement.Type;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Feeder;
import org.openpnp.spi.JobProcessor.JobProcessorException;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.NozzleTip;
import org.openpnp.spi.PartAlignment;
import org.openpnp.spi.PropertySheetHolder.PropertySheet;
import org.openpnp.util.MovableUtils;
import org.openpnp.util.UiUtils;
import org.pmw.tinylog.Logger;

import com.google.common.eventbus.Subscribe;

@SuppressWarnings("serial")
public class FeedersPanel extends JPanel implements WizardContainer {
    private final Configuration configuration;
    private final MainFrame mainFrame;

    private static final String PREF_DIVIDER_POSITION = "FeedersPanel.dividerPosition";

    private JTable table;

    private FeedersTableModel tableModel;
    private TableRowSorter<FeedersTableModel> tableSorter;
    private JTextField searchTextField;

    private ActionGroup singleSelectActionGroup;
    private ActionGroup multiSelectActionGroup;

    
    private int priorRowIndex = -1;
    private DockPanel dock;
    private DockPanel.Tab feedersTab;
    private DockPanel.Tab attentionTab;
    private PillBar scope;
    
    public FeedersPanel(Configuration configuration, MainFrame mainFrame) {
        this.configuration = configuration;
        this.mainFrame = mainFrame;

        setLayout(new BorderLayout(0, 0));
        tableModel = new FeedersTableModel(configuration);

        // The stylesheet's toolbar: New feeder in the accent, the movements and the feed/pick
        // pair with words on them, the scope segments, and the filter at the right end.
        DockPanel.Toolbar toolBar = new DockPanel.Toolbar();
        // New feeder opens the list of kinds, as the mockup's caret says, rather than a dialog.
        JButton btnNewFeeder = Ui.menuButton(Translations.getString("Dock.Action.NewFeeder"), //$NON-NLS-1$
                Ui.iconSm("plus"), Ui.Size.Sm, Ui.Variant.Primary, this::newFeederMenu); //$NON-NLS-1$
        btnNewFeeder.setFocusable(false);
        btnNewFeeder.setToolTipText(String.valueOf(newFeederAction.getValue(Action.SHORT_DESCRIPTION)));
        toolBar.add(btnNewFeeder);
        toolBar.iconButton(deleteFeederAction, "trash"); //$NON-NLS-1$
        toolBar.separator();
        toolBar.button(moveCameraToPickLocation, "camera", "Dock.Action.MoveCameraHere"); //$NON-NLS-1$ //$NON-NLS-2$
        toolBar.button(moveToolToPickLocation, "nozzle", "Dock.Action.MoveToolHere"); //$NON-NLS-1$ //$NON-NLS-2$
        toolBar.separator();
        toolBar.button(feedFeederAction, "step", "Dock.Action.Feed"); //$NON-NLS-1$ //$NON-NLS-2$
        toolBar.button(pickFeederAction, "hand", "Dock.Action.Pick"); //$NON-NLS-1$ //$NON-NLS-2$
        toolBar.separator();
        scope = new PillBar();
        scope.setLabeller(item -> Translations.getString("FeedersPanel.Filter." + item)); //$NON-NLS-1$
        for (Scope choice : Scope.values()) {
            scope.addItem(choice);
        }
        scope.setSelectedItem(Scope.All);
        scope.addActionListener(e -> search());
        for (Component pill : scope.getComponents()) {
            if (pill instanceof javax.swing.AbstractButton) {
                Ui.seg((javax.swing.AbstractButton) pill);
                ((javax.swing.AbstractButton) pill).setFont(Ui.font(11.5f));
            }
        }
        RoundedPanel scopeBox = new RoundedPanel(6, Ui::surface2, Ui::border);
        scopeBox.setLayout(new BorderLayout());
        scopeBox.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        scopeBox.add(scope);
        toolBar.add(scopeBox);
        toolBar.glue();
        searchTextField = toolBar.filter(
                Translations.getString("FeedersPanel.Filter.Placeholder")); //$NON-NLS-1$
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
        JComboBox<Type> feedOptionsComboBox = new JComboBox(ReferenceFeeder.FeedOptions.values());
        JComboBox<Type> priorityComboBox = new JComboBox(ReferenceFeeder.Priority.values());

		table = new AutoSelectTextTable(tableModel);

		// Enter edits the cell, Delete deletes what is selected, which asks first.

		org.openpnp.gui.support.TableUtils.bindKeys(table, deleteFeederAction);
		org.openpnp.gui.components.AutoSelectTextTable.setEmptyText(table,
		        Translations.getString("FeedersPanel.Empty")); //$NON-NLS-1$
        table.setDefaultEditor(ReferenceFeeder.FeedOptions.class, new DefaultCellEditor(feedOptionsComboBox));
        table.setDefaultEditor(ReferenceFeeder.Priority.class, new DefaultCellEditor(priorityComboBox));

        tableSorter = new TableRowSorter<>(tableModel);
        // The filter and the sort read what the cells show: "余量不足", not the status's name.
        tableSorter.setStringConverter(new javax.swing.table.TableStringConverter() {
            @Override
            public String toString(javax.swing.table.TableModel model, int row, int column) {
                Object value = model.getValueAt(row, column);
                if (value instanceof FeedersTableModel.Status) {
                    return statusText(value);
                }
                if (column == FeedersTableModel.LAST_PICK) {
                    return value == null ? "" : FeederDescriptions.since((Long) value, System.currentTimeMillis()); //$NON-NLS-1$
                }
                return value == null ? "" : value.toString(); //$NON-NLS-1$
            }
        });
        installRenderers();
        // Its own key: the columns are not the ones the widths under the old key were saved for.
        org.openpnp.gui.support.TableUtils.installColumnWidthSavers(table,
                java.util.prefs.Preferences.userNodeForPackage(FeedersPanel.class), "FeedersPanel.feedersTable"); //$NON-NLS-1$
        // "3 分钟前" becomes "4 分钟前" without anything about the feeder changing.
        new javax.swing.Timer(30_000, e -> {
            if (table.isShowing()) {
                table.repaint();
            }
        }).start();

        // The property sheets of the selected feeder are shown by the window's one properties
        // column now. The table and its toolbar are one tab of the dock; a second tab shows only
        // the feeders that want a look.
        table.setDefaultRenderer(Boolean.class, DockRenderers.toggle());
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        page.add(toolBar, BorderLayout.NORTH);
        page.add(DockPanel.table(table), BorderLayout.CENTER);
        dock = new DockPanel();
        dock.setMaximize(() -> mainFrame.toggleDockMaximised());
        // Both tabs show the same table, the second one narrowed: the table moves between two
        // holders as the tabs change, since a component can only be in one place.
        JPanel allHolder = new JPanel(new BorderLayout());
        allHolder.setOpaque(false);
        allHolder.add(page, BorderLayout.CENTER);
        JPanel attentionHolder = new JPanel(new BorderLayout());
        attentionHolder.setOpaque(false);
        feedersTab = dock.addTab(Ui.iconSm("feeder"), //$NON-NLS-1$
                Translations.getString("FeedersPanel.Tab.Feeders"), allHolder); //$NON-NLS-1$
        attentionTab = dock.addTab(Ui.iconSm("alert"), //$NON-NLS-1$
                Translations.getString("FeedersPanel.Tab.Attention"), attentionHolder); //$NON-NLS-1$
        dock.addChangeListener(e -> {
            JPanel holder = dock.getSelectedTab() == attentionTab ? attentionHolder : allHolder;
            if (page.getParent() != holder) {
                holder.add(page, BorderLayout.CENTER);
            }
            search();
            dock.revalidate();
            dock.repaint();
        });
        tableModel.addTableModelListener(e -> countTabs());
        setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        add(dock, BorderLayout.CENTER);
        table.setRowSorter(tableSorter);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        singleSelectActionGroup = new ActionGroup(deleteFeederAction, feedFeederAction,
                pickFeederAction, moveCameraToPickLocation, moveToolToPickLocation,
                setEnabledAction, setFeedOptionsAction, refillAction);
        singleSelectActionGroup.setEnabled(false);
        
        multiSelectActionGroup = new ActionGroup(deleteFeederAction, setEnabledAction, setFeedOptionsAction,
                refillAction);
        multiSelectActionGroup.setEnabled(false);
        
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) {
                    return;
                }
                List<Feeder> selections = getSelections();
                if (selections.size() == 0) {
                    singleSelectActionGroup.setEnabled(false);
                    multiSelectActionGroup.setEnabled(false);
                }
                else if (selections.size() == 1) {
                    multiSelectActionGroup.setEnabled(false);
                    singleSelectActionGroup.setEnabled(true);
                }
                else {
                    singleSelectActionGroup.setEnabled(false);
                    multiSelectActionGroup.setEnabled(true);
                }

                if (table.getSelectedRow() != priorRowIndex) {
                    Feeder feeder = getSelection();
                    // "料带飞达 · ReferenceStripFeeder · 槽位 B2" under the name, as the mockup has it.
                    Result shown = mainFrame.getInspector().show(FeedersPanel.this, feeder,
                            FeedersPanel.this,
                            feeder == null ? null : feeder.getName(),
                            feeder == null ? null : FeederDescriptions.subtitle(feeder),
                            Ui.icon("feeder", 16, Ui.accent()), //$NON-NLS-1$
                            () -> {
                                PropertySheet[] sheets = feeder.getPropertySheets();
                                return sheets == null ? List.of() : java.util.Arrays.asList(sheets);
                            });
                    if (shown == Result.Busy) {
                        // A question about the feeder selected before this one is on screen, and
                        // asking it let the event queue run us again.
                        return;
                    }
                    if (shown == Result.Cancelled) {
                        table.setRowSelectionInterval(priorRowIndex, priorRowIndex);
                        return;
                    }

                    // The selected row may have moved while the question about unapplied changes
                    // was on screen.
                    priorRowIndex = table.getSelectedRow();

                    if (feeder != null) {
                        if (mainFrame.getNavigation().getSelectedComponent() == mainFrame.getFeedersTab()
                              &&  configuration.getTablesLinked() == TablesLinked.Linked
                              && feeder.getPart() != null) {
                            mainFrame.getPartsTab().selectPartInTableAndUpdateLinks(feeder.getPart());
                        }
                    }

                    configuration.getBus().post(new FeederSelectedEvent(feeder, FeedersPanel.this));
                }
            }
        });

        configuration.getBus().register(this);
        
        JPopupMenu popupMenu = new JPopupMenu();

        JMenu setEnabledMenu = new JMenu(setEnabledAction);
        setEnabledMenu.add(new SetEnabledAction(true));
        setEnabledMenu.add(new SetEnabledAction(false));
        popupMenu.add(setEnabledMenu);
        JMenu setFeedOptionsMenu = new JMenu(setFeedOptionsAction);
        for (ReferenceFeeder.FeedOptions opt : ReferenceFeeder.FeedOptions.values()) {
            setFeedOptionsMenu.add(new SetFeedOptionsAction(opt));
        }
        popupMenu.add(setFeedOptionsMenu);
        popupMenu.addSeparator();
        popupMenu.add(refillAction);

        table.setComponentPopupMenu(popupMenu);
    }

    /** The kinds of feeder the machine takes, by their names, for New feeder's menu. */
    private JPopupMenu newFeederMenu() {
        JPopupMenu menu = new JPopupMenu();
        for (Class<? extends Feeder> type : configuration.getMachine().getCompatibleFeederClasses()) {
            javax.swing.JMenuItem item = new javax.swing.JMenuItem(
                    org.openpnp.gui.support.DisplayNames.typeName(type));
            item.setToolTipText(type.getSimpleName());
            item.addActionListener(e -> newFeeder(null, type));
            menu.add(item);
        }
        return menu;
    }

    /**
     * The mockup's cells: the name in bold, a feeder without a part saying so in grey, the
     * numbers in the mono face, what is left in yellow when low and in red when empty, the status
     * as a pill and the last pick as a time ago with the time itself in the tooltip.
     */
    private void installRenderers() {
        // What gives way on a narrow window: the type and the last pick, which the inspector has
        // too. The names, the part included, and the status are not cut below what they say.
        org.openpnp.gui.support.TableUtils.setColumnKinds(table, org.openpnp.gui.support.TableUtils.Kind.Check,
                org.openpnp.gui.support.TableUtils.Kind.Id, org.openpnp.gui.support.TableUtils.Kind.Secondary,
                org.openpnp.gui.support.TableUtils.Kind.Id, org.openpnp.gui.support.TableUtils.Kind.Id,
                org.openpnp.gui.support.TableUtils.Kind.Number, org.openpnp.gui.support.TableUtils.Kind.Number,
                org.openpnp.gui.support.TableUtils.Kind.Status, org.openpnp.gui.support.TableUtils.Kind.Secondary,
                org.openpnp.gui.support.TableUtils.Kind.Secondary, org.openpnp.gui.support.TableUtils.Kind.Secondary,
                org.openpnp.gui.support.TableUtils.Kind.Secondary);
        javax.swing.table.TableColumnModel columns = table.getColumnModel();
        columns.getColumn(FeedersTableModel.NAME).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setFont(table.getFont().deriveFont(Font.BOLD));
                return this;
            }
        });
        columns.getColumn(FeedersTableModel.PART).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table,
                        value == null ? Translations.getString("FeedersPanel.NoPart") : value, //$NON-NLS-1$
                        isSelected, hasFocus, row, column);
                if (!isSelected) {
                    setForeground(value == null ? Ui.muted() : table.getForeground());
                }
                return this;
            }
        });
        columns.getColumn(FeedersTableModel.SLOT).setCellRenderer(mono(SwingConstants.LEFT));
        columns.getColumn(FeedersTableModel.TAPE).setCellRenderer(mono(SwingConstants.RIGHT));
        columns.getColumn(FeedersTableModel.LEFT).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                Integer left = (Integer) value;
                super.getTableCellRendererComponent(table,
                        left == null ? "\u2014" : FeederDescriptions.count(left), //$NON-NLS-1$
                        isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.RIGHT);
                setFont(Ui.mono(table.getFont().getSize2D(), Font.PLAIN));
                if (!isSelected) {
                    Feeder feeder = tableModel.getRowObjectAt(table.convertRowIndexToModel(row));
                    setForeground(left == null ? Ui.muted()
                            : feeder.isEmpty() ? Ui.errText() : feeder.isLow() ? Ui.warnText() : table.getForeground());
                }
                return this;
            }
        });
        columns.getColumn(FeedersTableModel.STATUS).setCellRenderer(
                new org.openpnp.gui.support.StatusPillRenderer(FeedersPanel::toneOf, FeedersPanel::statusText));
        columns.getColumn(FeedersTableModel.LAST_PICK).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                long millis = value == null ? 0 : (Long) value;
                super.getTableCellRendererComponent(table,
                        FeederDescriptions.since(millis, System.currentTimeMillis()), isSelected, hasFocus, row,
                        column);
                if (!isSelected) {
                    setForeground(Ui.muted());
                }
                setToolTipText(millis > 0 ? FeederDescriptions.when(millis) : null);
                return this;
            }
        });
    }

    private static DefaultTableCellRenderer mono(int alignment) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(alignment);
                setFont(Ui.mono(table.getFont().getSize2D(), Font.PLAIN));
                return this;
            }
        };
    }

    static org.openpnp.gui.support.StatusPillRenderer.Tone toneOf(Object value) {
        switch ((FeedersTableModel.Status) value) {
            case Ready:
                return org.openpnp.gui.support.StatusPillRenderer.Tone.Ok;
            case Low:
            case NoPart:
                return org.openpnp.gui.support.StatusPillRenderer.Tone.Warning;
            case Empty:
            case Fault:
                return org.openpnp.gui.support.StatusPillRenderer.Tone.Error;
            default:
                return org.openpnp.gui.support.StatusPillRenderer.Tone.Muted;
        }
    }

    static String statusText(Object value) {
        return Translations.getString("FeedersPanel.Status." + ((FeedersTableModel.Status) value).name()); //$NON-NLS-1$
    }

    @Subscribe
    public void feederSelected(FeederSelectedEvent event) {
        if (event.source == this) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            mainFrame.showTab(mainFrame.getFeedersTab());
            
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                if (tableModel.getRowObjectAt(i) == event.feeder) {
                    int index = table.convertRowIndexToView(i);
                    table.getSelectionModel().setSelectionInterval(index, index);
                    table.scrollRectToVisible(new Rectangle(table.getCellRect(index, 0, true)));
                    break;
                }
            }
        });
    }

    /**
     * Activate the Feeders tab and show the Feeder for the specified Part. If none exists, prompt
     * the user to create a new one.
     * 
     * @param part
     */
    public void showFeederForPart(Part part) {
        mainFrame.showTab(mainFrame.getFeedersTab());
        searchTextField.setText("");
        search();

        Feeder feeder = findFeeder(part, true);
        // Prefer enabled feeders but fall back to disabled ones.
        if (feeder == null) {
            feeder = findFeeder(part, false);
        }
        if (feeder == null) {
            newFeeder(part);
        }
        else {
            Helpers.selectObjectTableRow(table, feeder);
        }
    }

    private Feeder findFeeder(Part part, boolean enabled) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Feeder feeder = tableModel.getRowObjectAt(i); 
            if (feeder.getPart() == part && feeder.isEnabled() == enabled) {
                return feeder;
            }
        }
        return null;
    }

    public Feeder getSelection() {
        List<Feeder> selections = getSelections();
        if (selections.size() != 1) {
            return null;
        }
        return selections.get(0);
    }

    public List<Feeder> getSelections() {
        ArrayList<Feeder> selections = new ArrayList<>();
        int[] selectedRows = table.getSelectedRows();
        for (int selectedRow : selectedRows) {
            selectedRow = table.convertRowIndexToModel(selectedRow);
            selections.add(configuration.getMachine().getFeeders().get(selectedRow));
        }
        return selections;
    }

    /** What the segments beside the filter narrow the table to. */
    private enum Scope {
        All, Enabled, InJob
    }

    private void search() {
        RowFilter<FeedersTableModel, Object> text = null;
        // If current expression doesn't parse, don't update.
        try {
            text = RowFilter.regexFilter("(?i)" + searchTextField.getText().trim());
        }
        catch (PatternSyntaxException e) {
            Logger.warn(e, "Search failed");
            return;
        }
        List<RowFilter<FeedersTableModel, Object>> filters = new ArrayList<>();
        filters.add(text);
        Scope chosen = scope == null ? Scope.All : (Scope) scope.getSelectedItem();
        boolean attention = dock != null && dock.getSelectedTab() == attentionTab;
        filters.add(new RowFilter<FeedersTableModel, Object>() {
            @Override
            public boolean include(Entry<? extends FeedersTableModel, ?> entry) {
                Feeder feeder = entry.getModel().getRowObjectAt((Integer) entry.getIdentifier());
                if (attention && !needsAttention(feeder)) {
                    return false;
                }
                switch (chosen) {
                    case Enabled:
                        return feeder.isEnabled();
                    case InJob:
                        return feeder.getPart() != null && isUsedByJob(feeder.getPart());
                    default:
                        return true;
                }
            }
        });
        tableSorter.setRowFilter(RowFilter.andFilter(filters));
        countTabs();
    }

    /**
     * A feeder that is switched off, has no part, is running low or out, or failed its last pick
     * is one the operator should look at.
     */
    private boolean needsAttention(Feeder feeder) {
        return FeedersTableModel.statusOf(feeder) != FeedersTableModel.Status.Ready;
    }

    private boolean isUsedByJob(Part part) {
        Job job = mainFrame.getJobTab().getJob();
        if (job == null) {
            return false;
        }
        for (BoardLocation boardLocation : job.getBoardLocations()) {
            if (!boardLocation.isEnabled()) {
                continue;
            }
            for (Placement placement : boardLocation.getBoard().getPlacements()) {
                if (placement.getType() == Placement.Type.Placement && placement.isEnabled()
                        && placement.getPart() == part) {
                    return true;
                }
            }
        }
        return false;
    }

    private void countTabs() {
        if (feedersTab == null) {
            return;
        }
        feedersTab.setCount(tableModel.getRowCount());
        int attention = 0;
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            if (needsAttention(tableModel.getRowObjectAt(row))) {
                attention++;
            }
        }
        attentionTab.setCount(attention);
        // The same count on the rail, in yellow: something to look at, not something broken.
        if (mainFrame.getNavigation() != null) {
            mainFrame.getNavigation().setBadge(this, attention,
                    org.openpnp.gui.shell.NavigationRail.Badge.Warn);
        }
    }

    @Override
    public void wizardCompleted(Wizard wizard) {
        // Repaint the table so that any changed fields get updated.
        table.repaint();
    }

    @Override
    public void wizardCancelled(Wizard wizard) {}

    private void newFeeder(Part part) {
        newFeeder(part, null);
    }

    /** A new feeder of the kind given, or of the kind chosen in the dialog when none is. */
    private void newFeeder(Part part, Class<? extends Feeder> kind) {
        // Adding a feeder moves the selection, so settle any unapplied edits first.
        if (!mainFrame.getInspector().getPresenter().settleUnappliedEdits()) {
            return;
        }
        
        if (configuration.getParts().size() == 0) {
            MessageBoxes.errorBox(getTopLevelAncestor(),
                    Translations.getString("General.Error"), //$NON-NLS-1$
                    Translations.getString("FeedersPanel.NewFeeder.NoParts")); //$NON-NLS-1$
            return;
        }

        Class<? extends Feeder> feederClass = kind;
        if (feederClass == null) {
            String title;
            if (part == null) {
                title = Translations.getString("FeedersPanel.SelectFeederImplementationDialog.Select.title"); //$NON-NLS-1$
            }
            else {
                title = Translations.getString("FeedersPanel.SelectFeederImplementationDialog.SelectFor.title" //$NON-NLS-1$
                ) + " " + part.getId() + "..."; //$NON-NLS-1$ //$NON-NLS-2$
            }
            ClassSelectionDialog<Feeder> dialog =
                    new ClassSelectionDialog<>(JOptionPane.getFrameForComponent(FeedersPanel.this),
                            title, Translations.getString(
                                    "FeedersPanel.SelectFeederImplementationDialog.Description"), //$NON-NLS-1$
                            configuration.getMachine().getCompatibleFeederClasses());
            dialog.setVisible(true);
            feederClass = dialog.getSelectedClass();
            if (feederClass == null) {
                return;
            }
        }
        try {
            
            Feeder feeder = feederClass.newInstance();

            feeder.setPart(part == null ? configuration.getParts().get(0) : part);

            configuration.getMachine().addFeeder(feeder);
            tableModel.refresh();

            searchTextField.setText("");
            search();

            Helpers.selectLastTableRow(table);
        }

        catch (Exception e) {
            MessageBoxes.errorBox(JOptionPane.getFrameForComponent(FeedersPanel.this),
                    Translations.getString("FeedersPanel.Feeder.ErrorBox.Title"), e); //$NON-NLS-1$
        }
    }
    
    public void updateView() {
    	tableModel.fireTableChanged(null);
    }

    protected Location preliminaryPickLocation(Feeder feeder, Nozzle nozzle) throws Exception {
        Location pickLocation = feeder.getPickLocation();
        if (feeder.isPartHeightAbovePickLocation()) {
            Length partHeight = nozzle.getSafePartHeight(feeder.getPart());
            pickLocation = pickLocation.add(new Location(partHeight.getUnits(), 0, 0, partHeight.getValue(), 0));
        }
        return pickLocation;
    }

    public Action newFeederAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.add);
            putValue(NAME, Translations.getString("FeedersPanel.Action.NewFeeder")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("FeedersPanel.Action.NewFeeder.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            newFeeder(null);
        }
    };

    public Action deleteFeederAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.delete);
            putValue(NAME, Translations.getString("FeedersPanel.Action.DeleteFeeder")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("FeedersPanel.Action.DeleteFeeder.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            List<Feeder> selections = getSelections();
            // "F-13 · C0402-100N": the name, and the part it carries, which is how feeders are told apart.
            List<String> ids = selections.stream()
                    .map(f -> f.getPart() == null ? f.getName() : f.getName() + " \u00b7 " + f.getPart().getId()) //$NON-NLS-1$
                    .collect(Collectors.toList());
            if (org.openpnp.gui.shell.Dialogs.confirmDelete(getTopLevelAncestor(),
                    "Dialogs.Kind.Feeders", ids)) { //$NON-NLS-1$
                for (Feeder feeder : selections) {
                    configuration.getMachine().removeFeeder(feeder);
                    tableModel.refresh();
                }
            }
        }
    };

    public Action feedFeederAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.feed);
            putValue(NAME, Translations.getString("FeedersPanel.Action.FeedFeeder")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("FeedersPanel.Action.FeedFeeder.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                Feeder feeder = getSelection();
                // Do the feed and get the nozzle that would be used for the subsequent pick. 
                Nozzle nozzle = feedFeeder(feeder);
            });
        }
    };

    public Action pickFeederAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.pick);
            putValue(NAME, Translations.getString("FeedersPanel.Action.PickFeeder")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("FeedersPanel.Action.PickFeeder.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                Feeder feeder = getSelection();

                pickFeeder(feeder);
            });
        }
    };

    /**
     * Perform a job-like feed operations sequence. 
     * 
     * @param feeder
     * @return the nozzle to be used for a subsequent pick.
     * @throws Exception
     */
    public Nozzle feedFeeder(Feeder feeder) throws Exception {
        if (feeder.getPart() == null) {
            throw new Exception("Feeder "+feeder.getName()+" has no part.");
        }
        // Simulate a "one feeder" job, prepare the feeder.
        if (feeder.getJobPreparationLocation() != null) {
            feeder.prepareForJob(true);
        }
        feeder.prepareForJob(false);

        Nozzle nozzle = getCompatibleNozzleAndTip(feeder, true);

        // Like in the JobProcessor, make sure it is calibrated.
        if (!nozzle.isCalibrated()) {
            nozzle.calibrate();
        }

        Map<String, Object> globals = new HashMap<>();
        globals.put("nozzle", nozzle);
        globals.put("feeder", feeder);
        globals.put("part", feeder.getPart());

        // Perform the feed.
        nozzle.moveToSafeZ();
        configuration.getScripting().on("Feeder.BeforeFeed", globals);
        feeder.feed(nozzle);
        configuration.getScripting().on("Feeder.AfterFeed", globals);
        return nozzle;
    }

    /**
     * Perform a job-like feed and pick operations sequence. 
     * 
     * @param feeder
     * @throws Exception
     * @throws JobProcessorException
     */
    public void pickFeeder(Feeder feeder) throws Exception, JobProcessorException {
        // Do the feed an get the nozzle for the pick.
        Nozzle nozzle = feedFeeder(feeder);

        // Perform the vacuum check, if enabled.
        if (nozzle.isPartOffEnabled(Nozzle.PartOffStep.BeforePick)) {
            // Part-off check can only be done at safe Z. An explicit move to safe Z is needed, because some feeder classes 
            // may move the nozzle to (near) the pick location i.e. down in Z in feed().
            nozzle.moveToSafeZ();
            if(!nozzle.isPartOff()) {
                throw new JobProcessorException(nozzle, "Part vacuum-detected on nozzle before pick.");
            }
        }

        // Make sure the nozzle can articulate from pick to placement. 
        Location placementLocation = getTestPlacementLocation(feeder.getPart());
        nozzle.prepareForPickAndPlaceArticulation(feeder.getPickLocation(), 
                placementLocation);

        // Go to the pick location and pick.
        nozzle.moveToPickLocation(feeder);
        nozzle.pick(feeder.getPart(),feeder);
        nozzle.moveToSafeZ();

        // After the pick. 
        feeder.postPick(nozzle);

        // Perform the vacuum check, if enabled.
        if (nozzle.isPartOnEnabled(Nozzle.PartOnStep.AfterPick)) {
            if(!nozzle.isPartOn()) {
                throw new JobProcessorException(nozzle, "No part detected.");
            }
        }
        // The part is now on the nozzle.
        feeder.recordPick();
        MovableUtils.fireTargetedUserAction(nozzle);
        if (MainFrame.get().getNavigation().getSelectedComponent() == MainFrame.get().getFeedersTab() 
                && configuration.getTablesLinked() == TablesLinked.Linked) {
            MainFrame.get().getPartsTab().selectPartInTableAndUpdateLinks(feeder.getPart());
        }
    }

    /**
     * Create a test placement location from the discard location and the test alignment angle. 
     * 
     * @param part
     * @return
     */
    public Location getTestPlacementLocation(Part part) {
        PartAlignment aligner = AbstractPartAlignment.getPartAlignment(part);
        Location placementLocation = configuration.getMachine().getDiscardLocation();
        placementLocation = new Location(placementLocation.getUnits(),
                placementLocation.getX(), 
                placementLocation.getY(), 
                placementLocation.getZ(), 
                (aligner instanceof ReferenceBottomVision ? 
                        ((ReferenceBottomVision)aligner).getTestAlignmentAngle() 
                        : 0.0));
        return placementLocation;
    }

    protected static Nozzle getCompatibleNozzleAndTip(Feeder feeder, boolean allowNozzleTipChange) throws Exception {
        if (feeder.getPart() == null) {
            throw new Exception("Feeder has not part set.");
        }
        // Check the nozzle tip package compatibility.
        Nozzle nozzle = MainFrame.get().getMachineControls().getSelectedNozzle();
        org.openpnp.model.Package packag = feeder.getPart().getPackage();
        if (nozzle.getNozzleTip() == null || 
                !packag.getCompatibleNozzleTips().contains(nozzle.getNozzleTip())) {
            // Wrong nozzle tip, try find one that works.
            Nozzle altNozzle = null;
            // Try find a good nozzle tip.
            for (NozzleTip nozzleTip : packag.getCompatibleNozzleTips()) {
                Nozzle nozzle2 = nozzleTip.getNozzleWhereLoaded();
                if (nozzle2 == null 
                        && nozzle.getCompatibleNozzleTips().contains(nozzleTip)) {
                    // Found a compatible one. 
                    if (nozzle.isNozzleTipChangedOnManualFeed() && allowNozzleTipChange) {
                        // Unload and load like the JobProcessor.
                        nozzle.unloadNozzleTip();
                        nozzle.loadNozzleTip(nozzleTip);
                        return nozzle; // Success.
                    }
                }
                if (altNozzle == null && nozzle2 != null){
                    altNozzle = nozzle2;
                }
            }
            String errMsg = "";
            if (nozzle.getNozzleTip() == null) {
                errMsg += "No nozzle tip loaded on nozzle "+nozzle.getName()+". ";
            }
            else {
                errMsg += "Nozzle "+nozzle.getName()+" loaded nozzle tip "+
                        nozzle.getNozzleTip().getName()+" is not compatible with package "+packag.getId()+". ";
                if (nozzle.getPart() != null) {
                    errMsg += "There is already a part "+nozzle.getPart().getId()+" loaded. "; 
                }
            }
            if (altNozzle != null) {
                errMsg += "Consider selecting nozzle "+altNozzle.getName()+", "
                        + "it has compatible nozzle tip "+altNozzle.getNozzleTip().getName()+" loaded. ";
            }
            else if (allowNozzleTipChange && !nozzle.isNozzleTipChangedOnManualFeed()) { 
                errMsg += "You may want to enable automatic nozzle tip change on manual pick on the "
                        + "Nozzle / Tool Changer. ";
            }
            errMsg += "The pick will always be performed with the nozzle selected in the Machine Controls. ";
            throw new Exception(errMsg);
        }
        return nozzle;
    }

    public Action moveCameraToPickLocation = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.centerCameraOnFeeder);
            putValue(NAME, Translations.getString("FeedersPanel.Action.MoveCameraToPick")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION,Translations.getString("FeedersPanel.Action.MoveCameraToPick.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                Feeder feeder = getSelection();
                Camera camera = MainFrame.get().getMachineControls().getSelectedTool().getHead()
                        .getDefaultCamera();
                Nozzle nozzle;
                try {
                    nozzle = getCompatibleNozzleAndTip(feeder, false);
                }
                catch (Exception e) {
                    nozzle = MainFrame.get().getMachineControls().getSelectedNozzle();
                }
                Location pickLocation = preliminaryPickLocation(feeder, nozzle);
                MovableUtils.moveToLocationAtSafeZ(camera, pickLocation);
                MovableUtils.fireTargetedUserAction(camera);
            });
        }
    };

    public Action moveToolToPickLocation = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.centerNozzleOnFeeder);
            putValue(NAME, Translations.getString("FeedersPanel.Action.MoveToolToPick")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION,Translations.getString("FeedersPanel.Action.MoveToolToPick.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.submitUiMachineTask(() -> {
                Feeder feeder = getSelection();
                Nozzle nozzle = getCompatibleNozzleAndTip(feeder, true);

                Location pickLocation = preliminaryPickLocation(feeder, nozzle);
                MovableUtils.moveToLocationAtSafeZ(nozzle, pickLocation);
                MovableUtils.fireTargetedUserAction(nozzle);
            });
        }
    };
    
    /**
     * The selected feeders have been refilled: a strip or a tray starts again from its first part,
     * any other from the count it was told it holds.
     */
    public final Action refillAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("FeedersPanel.Action.Refill")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("FeedersPanel.Action.Refill.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            for (Feeder f : getSelections()) {
                f.refill(null);
            }
            configuration.setDirty(true);
            table.repaint();
        }
    };

    public final Action setEnabledAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("FeedersPanel.Action.SetEnabled")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("FeedersPanel.Action.SetEnabled.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {}
    };

    class SetEnabledAction extends AbstractAction {
        final Boolean value;

        public SetEnabledAction(Boolean value) {
            this.value = value;
            String name = value ? 
                    Translations.getString("General.Enabled") :  //$NON-NLS-1$
                    Translations.getString("General.Disabled"); //$NON-NLS-1$
            putValue(NAME, name);
            putValue(SHORT_DESCRIPTION, Translations.getString("FeedersPanel.Action.SetEnabled.ToolTip") + " " + name); //$NON-NLS-1$ //$NON-NLS-2$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            for (Feeder f : getSelections()) {
                f.setEnabled(value);
            }
            table.repaint();
        }
    };

    public final Action setFeedOptionsAction = new AbstractAction() {
        {
            putValue(NAME, Translations.getString("FeedersPanel.Action.SetFeedOptions")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("FeedersPanel.Action.SetFeedOptions.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {}
    };

    class SetFeedOptionsAction extends AbstractAction {
        final ReferenceFeeder.FeedOptions value;

        public SetFeedOptionsAction(ReferenceFeeder.FeedOptions value) {
            this.value = value;
            String name = value.toString();
            putValue(NAME, name);
            putValue(SHORT_DESCRIPTION, Translations.getString("FeedersPanel.Action.SetFeedOptions.ToolTip") + " " + value); //$NON-NLS-1$ //$NON-NLS-2$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            for (Feeder f : getSelections()) {
                if (f instanceof ReferenceFeeder) {
                    ((ReferenceFeeder)f).setFeedOptions(value);
                }
            }
            table.repaint();
        }
    };

    public void selectFeederInTable(Feeder feeder) {
        Helpers.selectObjectTableRow(table, feeder);
    }
    public void selectFeederForPart(Part part) {
        if (getSelection() == null || getSelection().getPart() != part) {
            Feeder feeder = findFeeder(part, true);
            // Prefer enabled feeders but fall back to disabled ones.
            if (feeder == null) {
                feeder = findFeeder(part, false);
            }
            if (feeder != null) {
                Helpers.selectObjectTableRow(table, feeder);
            }
        }
    }

    public void refresh(Feeder f) {
        tableModel.refresh(f);
    }
}
