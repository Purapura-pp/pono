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
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.IndexedPropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.tree.TreeNode;

import org.openpnp.ConfigurationListener;
import org.openpnp.Translations;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.DockPanel;
import org.openpnp.gui.shell.DockRenderers;
import org.openpnp.gui.shell.PropertySheetPresenter;
import org.openpnp.gui.shell.Tokens;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.DisplayNames;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.TableUtils;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.model.Configuration;
import org.openpnp.model.Location;
import org.openpnp.model.Named;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Axis;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Driver;
import org.openpnp.spi.Feeder;
import org.openpnp.spi.FiducialLocator;
import org.openpnp.spi.Head;
import org.openpnp.spi.Machine;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.NozzleTip;
import org.openpnp.spi.PartAlignment;
import org.openpnp.spi.PropertySheetHolder;
import org.openpnp.spi.Signaler;
import org.openpnp.spi.base.SimplePropertySheetHolder;
import org.openpnp.util.BeanUtils;

/**
 * The machine page, as mockup 12 draws it: the machine's structure as a tree in a table - the name
 * as the interface calls things, "Camera Top", its type, its state and a line about it - with
 * the toolbar in the order the tree reads: what can be made under the element selected, what else
 * it does, expanding and folding, the search, and deleting it, at the far end and in red. The
 * toolbar was the icons of every node from the selected one up, so that deleting the node sat
 * next to making a new one in the node above; the search did nothing; the tree folded itself up
 * whenever something was added; and "expand" was a check box.
 */
@SuppressWarnings("serial")
public class MachineSetupPanel extends JPanel implements WizardContainer {
    private final Configuration configuration;

    private PropertySheetHolderTreeNode root;
    private final TreeModel model = new TreeModel();
    private final JTable table;
    private final JTextField searchTextField;
    private final JButton newButton;
    private final JPanel nodeActions = new JPanel();
    private final JButton deleteButton;
    private Action deleteAction;
    /** The nodes shown open, by where they are, "机器/贴装头/H1": kept as the tree is rebuilt. */
    private final Set<String> expanded = new HashSet<>();

    public MachineSetupPanel(Configuration configuration) {
        this.configuration = configuration;

        setLayout(new BorderLayout(0, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(0, 10, 10, 10));
        putClientProperty(MainFrame.DOCK_PAGE, Boolean.TRUE);

        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        TableUtils.setColumnKinds(table, TableUtils.Kind.Name, TableUtils.Kind.Status, TableUtils.Kind.Status,
                TableUtils.Kind.Secondary);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectCurrentTreePath();
            }
        });
        // The chevron opens and closes a node, as does a double click and the arrow keys.
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int column = table.columnAtPoint(e.getPoint());
                if (row < 0 || table.convertColumnIndexToModel(column) != TreeModel.NAME) {
                    return;
                }
                Row shown = model.rows.get(row);
                int x = e.getX() - table.getCellRect(row, column, false).x;
                int chevron = 10 + shown.depth * INDENT;
                if ((x >= chevron && x < chevron + 18 && e.getClickCount() == 1) || e.getClickCount() == 2) {
                    toggle(shown);
                }
            }
        });
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "pono.expand"); //$NON-NLS-1$
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "pono.collapse"); //$NON-NLS-1$
        table.getActionMap().put("pono.expand", new AbstractAction() { //$NON-NLS-1$
            @Override
            public void actionPerformed(ActionEvent e) {
                Row row = selectedRow();
                if (row != null && !row.node.isLeaf() && !expanded.contains(row.node.key)) {
                    toggle(row);
                }
            }
        });
        table.getActionMap().put("pono.collapse", new AbstractAction() { //$NON-NLS-1$
            @Override
            public void actionPerformed(ActionEvent e) {
                Row row = selectedRow();
                if (row != null && expanded.contains(row.node.key)) {
                    toggle(row);
                }
            }
        });

        // The toolbar in the tree's own order: making something under the selected element, what
        // else it does, opening and folding, the search - and deleting it, apart, at the far end.
        DockPanel.Toolbar toolbar = new DockPanel.Toolbar();
        newButton = toolbar.menu("MachineSetupPanel.New", "plus", this::newMenu); //$NON-NLS-1$ //$NON-NLS-2$
        nodeActions.setOpaque(false);
        nodeActions.setLayout(new BoxLayout(nodeActions, BoxLayout.X_AXIS));
        toolbar.add(nodeActions);
        toolbar.separator();
        JButton expandAll = Ui.button(Translations.getString("MachineSetupPanel.ExpandAll"), Ui.iconSm("chevdown"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Ghost);
        expandAll.setFocusable(false);
        expandAll.addActionListener(e -> expandAll(true));
        toolbar.add(expandAll);
        JButton collapseAll = Ui.button(Translations.getString("MachineSetupPanel.CollapseAll"), Ui.iconSm("chevright"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Ghost);
        collapseAll.setFocusable(false);
        collapseAll.addActionListener(e -> expandAll(false));
        toolbar.add(collapseAll);
        toolbar.glue();
        searchTextField = toolbar.filter(Translations.getString("MachineSetupPanel.Search.Placeholder")); //$NON-NLS-1$
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
        toolbar.separator();
        deleteButton = Ui.button("", Ui.iconSm("trash"), Ui.Size.Sm, Ui.Variant.Danger); //$NON-NLS-1$ //$NON-NLS-2$
        deleteButton.setFocusable(false);
        deleteButton.addActionListener(e -> {
            if (deleteAction != null && deleteAction.isEnabled()) {
                deleteAction.actionPerformed(e);
            }
        });
        toolbar.add(deleteButton);

        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        page.add(toolbar, BorderLayout.NORTH);
        page.add(DockPanel.table(table), BorderLayout.CENTER);
        table.getColumnModel().getColumn(TreeModel.NAME).setCellRenderer(new NameRenderer());
        table.getColumnModel().getColumn(TreeModel.TYPE).setCellRenderer(DockRenderers.secondary());
        table.getColumnModel().getColumn(TreeModel.STATUS).setCellRenderer(DockRenderers.status(
                v -> ((State) v).tone, v -> ((State) v).text));
        table.getColumnModel().getColumn(TreeModel.NOTE).setCellRenderer(DockRenderers.secondary());
        TableUtils.installColumnWidthSavers(table, java.util.prefs.Preferences.userNodeForPackage(MachineSetupPanel.class),
                "MachineSetupPanel.tree"); //$NON-NLS-1$

        DockPanel dock = new DockPanel();
        dock.addTab(Ui.iconSm("tree"), Translations.getString("MachineSetupPanel.Tab.Structure"), page); //$NON-NLS-1$ //$NON-NLS-2$
        dock.setMaximize(() -> MainFrame.get().toggleDockMaximised());
        add(dock, BorderLayout.CENTER);
        fillToolbar(null);

        configuration.addListener(new ConfigurationListener() {
            @Override
            public void configurationLoaded(Configuration configuration) throws Exception {}

            @Override
            public void configurationComplete(Configuration configuration) throws Exception {
                root = new PropertySheetHolderTreeNode(configuration.getMachine(), null);
                openByDefault(root);
                model.rebuild();
            }
        });
    }

    /** The machine, its heads and what is on them are open to begin with, as the mockup shows them. */
    private void openByDefault(PropertySheetHolderTreeNode node) {
        PropertySheetHolder holder = node.getPropertySheetHolder();
        boolean heads = holder instanceof SimplePropertySheetHolder && !node.children.isEmpty()
                && node.children.get(0).getPropertySheetHolder() instanceof Head;
        if (node.parent == null || heads) {
            expanded.add(node.key);
            for (PropertySheetHolderTreeNode child : node.children) {
                openByDefault(child);
            }
        }
        else if (holder instanceof Head) {
            // The head and its groups: its nozzles, cameras and actuators are what is looked for.
            expanded.add(node.key);
            for (PropertySheetHolderTreeNode child : node.children) {
                expanded.add(child.key);
            }
        }
    }

    private void toggle(Row row) {
        if (row.node.isLeaf() || !searchTextField.getText().trim().isEmpty()) {
            return;
        }
        if (!expanded.remove(row.node.key)) {
            expanded.add(row.node.key);
        }
        model.rebuild();
    }

    private void expandAll(boolean open) {
        if (root == null) {
            return;
        }
        List<PropertySheetHolderTreeNode> all = new ArrayList<>();
        collect(root, all);
        for (PropertySheetHolderTreeNode node : all) {
            if (open) {
                expanded.add(node.key);
            }
            else if (node != root) {
                expanded.remove(node.key);
            }
        }
        model.rebuild();
    }

    private static void collect(PropertySheetHolderTreeNode node, List<PropertySheetHolderTreeNode> all) {
        all.add(node);
        for (PropertySheetHolderTreeNode child : node.children) {
            collect(child, all);
        }
    }

    /** Only what matches the words, by its name or its type, with the way down to it. */
    private void search() {
        model.rebuild();
    }

    private Row selectedRow() {
        int row = table.getSelectedRow();
        return row < 0 || row >= model.rows.size() ? null : model.rows.get(row);
    }

    @Override
    public void wizardCompleted(Wizard wizard) {
        table.repaint();
    }

    @Override
    public void wizardCancelled(Wizard wizard) {}

    /**
     * Select the node that edits this element, opening the tree as far as needed to show it.
     * <p>
     * A page that shows what an element is - the machine overview, a report - can hand the
     * user over to where it is set, rather than growing a second editor for the same fields.
     *
     * @return false if the element is not in the tree, which is what happens when it was removed
     *         while a page naming it was open.
     */
    public boolean selectPropertySheetHolder(PropertySheetHolder holder) {
        if (root == null || holder == null) {
            return false;
        }
        PropertySheetHolderTreeNode found = find(root, holder);
        if (found == null) {
            return false;
        }
        searchTextField.setText(""); //$NON-NLS-1$
        for (PropertySheetHolderTreeNode walk = found.parentNode(); walk != null; walk = walk.parentNode()) {
            expanded.add(walk.key);
        }
        model.rebuild();
        select(found.getPropertySheetHolder());
        return true;
    }

    private boolean select(PropertySheetHolder holder) {
        for (int i = 0; i < model.rows.size(); i++) {
            if (model.rows.get(i).node.getPropertySheetHolder() == holder) {
                table.setRowSelectionInterval(i, i);
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                return true;
            }
        }
        return false;
    }

    private PropertySheetHolderTreeNode find(PropertySheetHolderTreeNode node, PropertySheetHolder holder) {
        if (node.getPropertySheetHolder() == holder) {
            return node;
        }
        for (PropertySheetHolderTreeNode child : node.children) {
            PropertySheetHolderTreeNode found = find(child, holder);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // ---- the toolbar ---------------------------------------------------------------------------

    /** The nearest element from the selection up that something can be made under, and what. */
    private static final class Making {
        final PropertySheetHolderTreeNode under;
        final List<Action> actions;

        Making(PropertySheetHolderTreeNode under, List<Action> actions) {
            this.under = under;
            this.actions = actions;
        }
    }

    private static boolean isNew(Action action) {
        Object icon = action.getValue(Action.SMALL_ICON);
        return icon == Icons.add || icon == Icons.nozzleAdd || icon == Icons.nozzleTipAdd;
    }

    private static boolean isDelete(Action action) {
        Object icon = action.getValue(Action.SMALL_ICON);
        return icon == Icons.delete || icon == Icons.nozzleRemove || icon == Icons.nozzleTipRemove;
    }

    private static List<Action> actionsOf(PropertySheetHolderTreeNode node) {
        Action[] actions = node.getPropertySheetHolder().getPropertySheetHolderActions();
        return actions == null ? Collections.emptyList() : java.util.Arrays.asList(actions);
    }

    /**
     * What can be made at the selection: its own New, or its groups' - a head's cameras, nozzles
     * and actuators - or, failing that, the same one level up, and so on to the machine.
     */
    private Making making(PropertySheetHolderTreeNode node) {
        for (PropertySheetHolderTreeNode walk = node; walk != null; walk = walk.parentNode()) {
            List<Action> made = new ArrayList<>();
            for (Action action : actionsOf(walk)) {
                if (isNew(action)) {
                    made.add(action);
                }
            }
            if (!(walk.getPropertySheetHolder() instanceof SimplePropertySheetHolder)) {
                for (PropertySheetHolderTreeNode child : walk.children) {
                    if (child.getPropertySheetHolder() instanceof SimplePropertySheetHolder) {
                        for (Action action : actionsOf(child)) {
                            if (isNew(action)) {
                                made.add(action);
                            }
                        }
                    }
                }
            }
            if (!made.isEmpty()) {
                // A group makes what it holds under the element above it: a camera goes on the head.
                PropertySheetHolderTreeNode under = walk.getPropertySheetHolder() instanceof SimplePropertySheetHolder
                        && walk.parentNode() != null ? walk.parentNode() : walk;
                return new Making(under, made);
            }
        }
        return null;
    }

    private Making making;

    private JPopupMenu newMenu() {
        JPopupMenu menu = new JPopupMenu();
        if (making != null) {
            for (Action action : making.actions) {
                menu.add(new JMenuItem(action));
            }
        }
        return menu;
    }

    private void fillToolbar(PropertySheetHolderTreeNode node) {
        making = node == null ? null : making(node);
        newButton.setEnabled(making != null);
        newButton.setText(making == null ? Translations.getString("MachineSetupPanel.New") //$NON-NLS-1$
                : String.format(Translations.getString("MachineSetupPanel.NewUnder"), name(making.under))); //$NON-NLS-1$
        nodeActions.removeAll();
        deleteAction = null;
        if (node != null) {
            for (Action action : actionsOf(node)) {
                if (isNew(action)) {
                    continue;
                }
                if (isDelete(action)) {
                    deleteAction = action;
                    continue;
                }
                JButton button = Ui.button(action, Ui.Size.Sm, Ui.Variant.Default);
                button.setFocusable(false);
                nodeActions.add(javax.swing.Box.createHorizontalStrut(6));
                nodeActions.add(button);
            }
        }
        deleteButton.setVisible(deleteAction != null);
        if (deleteAction != null) {
            deleteButton.setText(String.format(Translations.getString("MachineSetupPanel.DeleteNamed"), name(node))); //$NON-NLS-1$
            Object tip = deleteAction.getValue(Action.SHORT_DESCRIPTION);
            deleteButton.setToolTipText(tip == null ? null : String.valueOf(tip));
            deleteButton.setEnabled(deleteAction.isEnabled());
        }
        nodeActions.revalidate();
        nodeActions.repaint();
        newButton.getParent().revalidate();
    }

    /** The path whose properties are on show, for putting back when the user keeps unapplied edits. */
    private PropertySheetHolderTreeNode shown;
    private boolean revertingSelection;

    public void selectCurrentTreePath() {
        if (revertingSelection) {
            return;
        }
        Row row = selectedRow();
        PropertySheetHolderTreeNode node = row == null ? null : row.node;
        fillToolbar(node);
        PropertySheetHolder holder = node == null ? null : node.getPropertySheetHolder();
        PropertySheetPresenter.Result result = MainFrame.get().getInspector()
                .show(MachineSetupPanel.this, holder,
                MachineSetupPanel.this,
                holder == null ? null : name(node),
                holder == null ? null : subtitle(node),
                holder == null ? null : Ui.icon(icon(node), 16, Ui.accent()),
                () -> {
                    org.openpnp.spi.PropertySheetHolder.PropertySheet[] sheets = holder.getPropertySheets();
                    return sheets == null ? List.of() : java.util.Arrays.asList(sheets);
                });
        if (result == PropertySheetPresenter.Result.Cancelled) {
            // The user kept the unapplied edits: the element they belong to stays selected, with
            // its own actions in the toolbar.
            revertingSelection = true;
            try {
                if (shown != null) {
                    select(shown.getPropertySheetHolder());
                }
            }
            finally {
                revertingSelection = false;
            }
            fillToolbar(shown);
            return;
        }
        if (result == PropertySheetPresenter.Result.Shown) {
            shown = node;
        }
    }

    // ---- what a node is called and says about itself ------------------------------------------

    /** The word the interface calls an element's kind by, with the icon; null for the rest. */
    private static String kindKey(PropertySheetHolder holder) {
        if (holder instanceof Machine) {
            return "MachineSetupPanel.Kind.Machine"; //$NON-NLS-1$
        }
        if (holder instanceof Head) {
            return "MachineSetupPanel.Kind.Head"; //$NON-NLS-1$
        }
        if (holder instanceof Nozzle) {
            return "MachineSetupPanel.Kind.Nozzle"; //$NON-NLS-1$
        }
        if (holder instanceof NozzleTip) {
            return "MachineSetupPanel.Kind.NozzleTip"; //$NON-NLS-1$
        }
        if (holder instanceof Camera) {
            return "MachineSetupPanel.Kind.Camera"; //$NON-NLS-1$
        }
        if (holder instanceof Actuator) {
            return "MachineSetupPanel.Kind.Actuator"; //$NON-NLS-1$
        }
        if (holder instanceof Axis) {
            return "MachineSetupPanel.Kind.Axis"; //$NON-NLS-1$
        }
        if (holder instanceof Driver) {
            return "MachineSetupPanel.Kind.Driver"; //$NON-NLS-1$
        }
        if (holder instanceof Signaler) {
            return "MachineSetupPanel.Kind.Signaler"; //$NON-NLS-1$
        }
        if (holder instanceof Feeder) {
            return "MachineSetupPanel.Kind.Feeder"; //$NON-NLS-1$
        }
        return null;
    }

    private static String icon(PropertySheetHolderTreeNode node) {
        PropertySheetHolder holder = node.getPropertySheetHolder();
        if (holder instanceof SimplePropertySheetHolder) {
            return node.children.isEmpty() ? "tree" : icon(node.children.get(0)); //$NON-NLS-1$
        }
        if (holder instanceof Machine || holder instanceof Head) {
            return "machine"; //$NON-NLS-1$
        }
        if (holder instanceof Nozzle || holder instanceof NozzleTip) {
            return "nozzle"; //$NON-NLS-1$
        }
        if (holder instanceof Camera) {
            return "camera"; //$NON-NLS-1$
        }
        if (holder instanceof Actuator) {
            return "zap"; //$NON-NLS-1$
        }
        if (holder instanceof Axis) {
            return "move"; //$NON-NLS-1$
        }
        if (holder instanceof Driver) {
            return "upload"; //$NON-NLS-1$
        }
        if (holder instanceof Signaler) {
            return "bell"; //$NON-NLS-1$
        }
        if (holder instanceof Feeder) {
            return "feeder"; //$NON-NLS-1$
        }
        if (holder instanceof PartAlignment || holder instanceof FiducialLocator) {
            return "eye"; //$NON-NLS-1$
        }
        return "gear"; //$NON-NLS-1$
    }

    /** "Camera Top", "Head H1", "Machine"; a group and anything else by its own title. */
    static String name(PropertySheetHolderTreeNode node) {
        PropertySheetHolder holder = node.getPropertySheetHolder();
        String kind = kindKey(holder);
        if (holder instanceof Machine) {
            return Translations.getString(kind);
        }
        if (kind != null && holder instanceof Named && ((Named) holder).getName() != null) {
            return Translations.getString(kind) + " " + ((Named) holder).getName(); //$NON-NLS-1$
        }
        String title = holder.getPropertySheetHolderTitle();
        return title == null ? "" : title; //$NON-NLS-1$
    }

    /** "图像相机（模拟） · 贴装头 H1": the type, and the head it is on. */
    private static String subtitle(PropertySheetHolderTreeNode node) {
        PropertySheetHolder holder = node.getPropertySheetHolder();
        if (holder instanceof SimplePropertySheetHolder) {
            return String.format(Translations.getString("MachineSetupPanel.Subtitle.Group"), node.children.size()); //$NON-NLS-1$
        }
        String type = DisplayNames.typeName(holder.getClass());
        for (PropertySheetHolderTreeNode walk = node.parentNode(); walk != null; walk = walk.parentNode()) {
            if (walk.getPropertySheetHolder() instanceof Head) {
                return type + " \u00b7 " + name(walk); //$NON-NLS-1$
            }
        }
        return type;
    }

    /** What a node's status column says, in a capsule. */
    static final class State {
        final String text;
        final Chip.Tone tone;

        State(String text, Chip.Tone tone) {
            this.text = text;
            this.tone = tone;
        }
    }

    private State state(PropertySheetHolderTreeNode node) {
        PropertySheetHolder holder = node.getPropertySheetHolder();
        if (holder instanceof Machine) {
            return ((Machine) holder).isEnabled()
                    ? new State(Translations.getString("MachineSetupPanel.State.Enabled"), Chip.Tone.Ok) //$NON-NLS-1$
                    : new State(Translations.getString("MachineSetupPanel.State.Disabled"), Chip.Tone.Neutral); //$NON-NLS-1$
        }
        if (holder instanceof Nozzle) {
            NozzleTip tip = ((Nozzle) holder).getNozzleTip();
            return tip == null ? new State(Translations.getString("MachineSetupPanel.State.NoTip"), Chip.Tone.Warn) //$NON-NLS-1$
                    : new State(tip.getName(), Chip.Tone.Ok);
        }
        if (holder instanceof SimplePropertySheetHolder && !node.children.isEmpty()) {
            PropertySheetHolder first = node.children.get(0).getPropertySheetHolder();
            if (first instanceof Feeder) {
                int empty = 0;
                for (PropertySheetHolderTreeNode child : node.children) {
                    if (child.getPropertySheetHolder() instanceof Feeder
                            && ((Feeder) child.getPropertySheetHolder()).isEnabled()
                            && ((Feeder) child.getPropertySheetHolder()).isEmpty()) {
                        empty++;
                    }
                }
                return empty == 0 ? null
                        : new State(String.format(Translations.getString("MachineSetupPanel.State.Empty"), empty), //$NON-NLS-1$
                                Chip.Tone.Warn);
            }
            if (first instanceof Driver) {
                boolean on = configuration.getMachine() != null && configuration.getMachine().isEnabled();
                return on ? new State(Translations.getString("MachineSetupPanel.State.Connected"), Chip.Tone.Ok) //$NON-NLS-1$
                        : new State(Translations.getString("MachineSetupPanel.State.NotConnected"), Chip.Tone.Neutral); //$NON-NLS-1$
            }
        }
        return null;
    }

    private String note(PropertySheetHolderTreeNode node) {
        PropertySheetHolder holder = node.getPropertySheetHolder();
        if (holder instanceof Machine) {
            return String.format(Translations.getString("MachineSetupPanel.Note.Machine"), //$NON-NLS-1$
                    configuration.getSystemUnits().getShortName(), ((Machine) holder).getHeads().size());
        }
        if (holder instanceof Head) {
            Location park = ((Head) holder).getParkLocation();
            return park == null ? "" : String.format(Translations.getString("MachineSetupPanel.Note.Head"), //$NON-NLS-1$ //$NON-NLS-2$
                    DefinitionList.number(park.getX()), DefinitionList.number(park.getY()));
        }
        if (holder instanceof Camera) {
            Camera camera = (Camera) holder;
            String looking = camera.getLooking() == null ? "" : DisplayNames.of(camera.getLooking()); //$NON-NLS-1$
            return camera.getWidth() > 0 ? looking + " \u00b7 " + camera.getWidth() + " \u00d7 " + camera.getHeight() //$NON-NLS-1$ //$NON-NLS-2$
                    : looking;
        }
        if (holder instanceof Actuator && ((Actuator) holder).getValueType() != null) {
            return DisplayNames.of(((Actuator) holder).getValueType());
        }
        if (holder instanceof SimplePropertySheetHolder && !node.children.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (PropertySheetHolderTreeNode child : node.children) {
                PropertySheetHolder item = child.getPropertySheetHolder();
                if (item instanceof Feeder) {
                    return ""; //$NON-NLS-1$
                }
                names.add(item instanceof Named && ((Named) item).getName() != null ? ((Named) item).getName()
                        : item.getPropertySheetHolderTitle());
                if (names.size() == 6) {
                    names.add("\u2026"); //$NON-NLS-1$
                    break;
                }
            }
            return String.join(" \u00b7 ", names); //$NON-NLS-1$
        }
        return ""; //$NON-NLS-1$
    }

    // ---- the tree as rows ----------------------------------------------------------------------

    private static final int INDENT = 18;

    /** A node on show, and how deep in the tree it is. */
    private static final class Row {
        final PropertySheetHolderTreeNode node;
        final int depth;

        Row(PropertySheetHolderTreeNode node, int depth) {
            this.node = node;
            this.depth = depth;
        }
    }

    private final class TreeModel extends AbstractTableModel {
        static final int NAME = 0;
        static final int TYPE = 1;
        static final int STATUS = 2;
        static final int NOTE = 3;

        private List<Row> rows = new ArrayList<>();

        /**
         * The rows as the tree is open, or with the search, the nodes that match and the way
         * down to them. What was selected stays selected.
         */
        void rebuild() {
            PropertySheetHolder selected = selectedRow() == null ? null : selectedRow().node.getPropertySheetHolder();
            List<Row> built = new ArrayList<>();
            String words = searchTextField.getText().trim().toLowerCase(Locale.ROOT);
            if (root != null) {
                if (words.isEmpty()) {
                    open(root, 0, built);
                }
                else {
                    matching(root, 0, words, built);
                }
            }
            rows = built;
            // The table drops its selection with the change: heard, that emptied the properties
            // column, and the selection put back unheard left the row selected with nothing shown.
            boolean kept = false;
            revertingSelection = true;
            try {
                fireTableDataChanged();
                kept = selected != null && select(selected);
            }
            finally {
                revertingSelection = false;
            }
            if (selected != null && !kept) {
                selectCurrentTreePath();
            }
        }

        private void open(PropertySheetHolderTreeNode node, int depth, List<Row> built) {
            built.add(new Row(node, depth));
            if (expanded.contains(node.key)) {
                for (PropertySheetHolderTreeNode child : node.children) {
                    open(child, depth + 1, built);
                }
            }
        }

        /** Whether the node or anything under it matches; the matches and their ancestors are added. */
        private boolean matching(PropertySheetHolderTreeNode node, int depth, String words, List<Row> built) {
            int at = built.size();
            boolean self = name(node).toLowerCase(Locale.ROOT).contains(words)
                    || DisplayNames.typeName(node.getPropertySheetHolder().getClass()).toLowerCase(Locale.ROOT).contains(words);
            built.add(new Row(node, depth));
            boolean below = false;
            for (PropertySheetHolderTreeNode child : node.children) {
                below |= matching(child, depth + 1, words, built);
            }
            if (!self && !below) {
                built.remove(at);
                return false;
            }
            return true;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int column) {
            switch (column) {
                case NAME:
                    return Translations.getString("MachineSetupPanel.Column.Name"); //$NON-NLS-1$
                case TYPE:
                    return Translations.getString("MachineSetupPanel.Column.Type"); //$NON-NLS-1$
                case STATUS:
                    return Translations.getString("MachineSetupPanel.Column.State"); //$NON-NLS-1$
                default:
                    return Translations.getString("MachineSetupPanel.Column.Note"); //$NON-NLS-1$
            }
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PropertySheetHolderTreeNode node = rows.get(rowIndex).node;
            PropertySheetHolder holder = node.getPropertySheetHolder();
            switch (columnIndex) {
                case NAME:
                    return name(node);
                case TYPE:
                    return holder instanceof SimplePropertySheetHolder ? "" //$NON-NLS-1$
                            : DisplayNames.typeName(holder.getClass());
                case STATUS:
                    return state(node);
                default:
                    return note(node);
            }
        }
    }

    /** The name cell: indented by depth, the chevron of a node with children, its icon, its name. */
    private final class NameRenderer implements TableCellRenderer {
        private final JPanel cell = new JPanel(null) {
            @Override
            public void doLayout() {
                int h = getHeight();
                int x = 10 + depth * INDENT;
                chevron.setBounds(x, 0, 18, h);
                x += 18;
                icon.setBounds(x, 0, 22, h);
                x += 22;
                int countWidth = count.isVisible() ? count.getPreferredSize().width + 6 : 0;
                int nameWidth = Math.min(name.getPreferredSize().width, Math.max(0, getWidth() - x - 10 - countWidth));
                name.setBounds(x, 0, nameWidth, h);
                count.setBounds(x + nameWidth + 6, 0, countWidth, h);
            }

            @Override
            public java.awt.Dimension getPreferredSize() {
                int width = 10 + depth * INDENT + 18 + 22 + name.getPreferredSize().width
                        + (count.isVisible() ? count.getPreferredSize().width + 6 : 0) + 10;
                return new java.awt.Dimension(width, 32);
            }
        };
        private final JLabel chevron = new JLabel();
        private final JLabel icon = new JLabel();
        private final JLabel name = new JLabel();
        private final JLabel count = Ui.muted(""); //$NON-NLS-1$
        private int depth;

        NameRenderer() {
            cell.add(chevron);
            cell.add(icon);
            cell.add(name);
            cell.add(count);
            chevron.setHorizontalAlignment(SwingConstants.CENTER);
            name.setFont(Ui.font(Tokens.FS_TABLE, java.awt.Font.BOLD));
            count.setFont(Ui.font(12f));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int column) {
            Row shown = model.rows.get(row);
            depth = shown.depth;
            boolean searching = !searchTextField.getText().trim().isEmpty();
            boolean open = searching || expanded.contains(shown.node.key);
            chevron.setIcon(shown.node.isLeaf() ? null : Ui.icon(open ? "chevdown" : "chevright", 14, Ui.muted())); //$NON-NLS-1$ //$NON-NLS-2$
            Icon kind = Ui.icon(icon(shown.node), 14, Ui.muted());
            icon.setIcon(kind);
            name.setText(String.valueOf(value));
            name.setForeground(table.getForeground());
            boolean group = shown.node.getPropertySheetHolder() instanceof SimplePropertySheetHolder;
            count.setVisible(group);
            count.setText(group ? String.valueOf(shown.node.children.size()) : ""); //$NON-NLS-1$
            cell.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            cell.setOpaque(true);
            return cell;
        }
    }

    public class PropertySheetHolderTreeNode implements TreeNode {
        private final PropertySheetHolder obj;
        private final TreeNode parent;
        private final ArrayList<PropertySheetHolderTreeNode> children = new ArrayList<>();
        /** Where the node is, by its parents' names and its own: what keeps it open when rebuilt. */
        final String key;

        public PropertySheetHolderTreeNode(PropertySheetHolder obj, TreeNode parent) {
            this.obj = obj;
            this.parent = parent;
            this.key = (parent instanceof PropertySheetHolderTreeNode ? ((PropertySheetHolderTreeNode) parent).key + "/" : "") //$NON-NLS-1$ //$NON-NLS-2$
                    + obj.getClass().getSimpleName() + ":" + obj.getPropertySheetHolderTitle(); //$NON-NLS-1$
            loadChildren();
            // A child added or removed rebuilds this node's part of the tree; what was open
            // stays open, where the tree used to fold itself up. Any other change is redrawn.
            BeanUtils.addPropertyChangeListener(obj, (PropertyChangeListener) (e) -> {
                SwingUtilities.invokeLater(() -> {
                    if (e instanceof IndexedPropertyChangeEvent) {
                        loadChildren();
                        model.rebuild();
                    }
                    else {
                        table.repaint();
                    }
                });
            }); 
        }

        private void loadChildren() {
            this.children.clear();
            PropertySheetHolder[] children = obj.getChildPropertySheetHolders();
            if (children != null) {
                for (PropertySheetHolder child : children) {
                    this.children.add(new PropertySheetHolderTreeNode(child, this));
                }
            }
        }

        PropertySheetHolderTreeNode parentNode() {
            return parent instanceof PropertySheetHolderTreeNode ? (PropertySheetHolderTreeNode) parent : null;
        }

        public PropertySheetHolder getPropertySheetHolder() {
            return obj;
        }

        @Override
        public TreeNode getChildAt(int childIndex) {
            return children.get(childIndex);
        }

        @Override
        public int getChildCount() {
            return children.size();
        }

        @Override
        public TreeNode getParent() {
            return parent;
        }

        @Override
        public int getIndex(TreeNode node) {
            return children.indexOf(node);
        }

        @Override
        public boolean getAllowsChildren() {
            return children.size() > 0;
        }

        @Override
        public boolean isLeaf() {
            return children.size() < 1;
        }

        @Override
        public Enumeration<? extends TreeNode> children() {
            return Collections.enumeration(children);
        }

        @Override
        public String toString() {
            return obj.getPropertySheetHolderTitle();
        }
    }
}
