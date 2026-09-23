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
import java.beans.IndexedPropertyChangeEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.openpnp.ConfigurationListener;
import org.openpnp.Translations;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.model.Configuration;
import org.openpnp.spi.PropertySheetHolder;
import org.openpnp.spi.PropertySheetHolder.PropertySheet;
import org.openpnp.util.BeanUtils;

@SuppressWarnings("serial")
public class MachineSetupPanel extends JPanel implements WizardContainer {


    private static final String PREF_DIVIDER_POSITION = "MachineSetupPanel.dividerPosition";

    private JTextField searchTextField;

    private JTree tree;
    private DefaultTreeModel treeModel;
    private JToolBar toolBar;
    private final Action action = new SwingAction();
    private JCheckBox cbExp;
    
    /**
     * These three variables are used to manage state so that if the user is flipping between nodes
     * of the same type we select the same tab that was previously selected. The idea is that if
     * you are looking at the Part Detection settings for a Nozzle Tip, and you switch to another
     * Nozzle Tip you probably want to look at the Part Detection settings for it, and not
     * whatever the first tab is.
     */

    private final Configuration configuration;

    public MachineSetupPanel(Configuration configuration) {
        this.configuration = configuration;

        setLayout(new BorderLayout(0, 0));

        JPanel panel = new JPanel();
        add(panel, BorderLayout.NORTH);
        panel.setLayout(new BorderLayout(0, 0));

        toolBar = new JToolBar();
        toolBar.setFloatable(false);
        panel.add(toolBar, BorderLayout.CENTER);

        JPanel panel_1 = new JPanel();
        panel.add(panel_1, BorderLayout.EAST);
        
                cbExp = new JCheckBox(Translations.getString("MachineSetupPanel.ExpandChkBox.text")); //$NON-NLS-1$
                panel_1.add(cbExp);
                cbExp.setAction(action);

        JLabel lblSearch = new JLabel(Translations.getString("MachineSetupPanel.SearchLabel.text")); //$NON-NLS-1$
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
        // The search does nothing yet: typing into it filtered nothing, which reads as "no such
        // element". Hidden until it works.
        lblSearch.setVisible(false);
        searchTextField.setVisible(false);

        // The property sheets of the selected node are shown by the window's one properties
        // column now, so the tree gets the whole panel.
        JScrollPane scrollPane = new JScrollPane();
        add(scrollPane, BorderLayout.CENTER);

        tree = new JTree();
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(treeCellRenderer);
        scrollPane.setViewportView(tree);

        tree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                selectCurrentTreePath();
            }
        });

        configuration.addListener(new ConfigurationListener() {
            @Override
            public void configurationLoaded(Configuration configuration) throws Exception {}

            @Override
            public void configurationComplete(Configuration configuration) throws Exception {
                tree.setModel(treeModel = new DefaultTreeModel(
                        new PropertySheetHolderTreeNode(configuration.getMachine(), null)));
                for (int i = 1; i < tree.getRowCount(); i++) {
                  if (cbExp.isSelected()) {
                    tree.expandRow(i);
                  }
                  else {
                    tree.collapseRow(i);
                  }
                }
            }
        });
    }

    private void search() {}

    @Override
    public void wizardCompleted(Wizard wizard) {}

    @Override
    public void wizardCancelled(Wizard wizard) {}

    /**
     * Select the node that edits this element, expanding the tree as far as needed to show it.
     * <p>
     * A page that shows what an element is - the diagnostics overview, a report - can hand the
     * user over to where it is set, rather than growing a second editor for the same fields.
     *
     * @return false if the element is not in the tree, which is what happens when it was removed
     *         while a page naming it was open.
     */
    public boolean selectPropertySheetHolder(PropertySheetHolder holder) {
        if (treeModel == null || holder == null) {
            return false;
        }
        TreePath path = find((PropertySheetHolderTreeNode) treeModel.getRoot(), holder);
        if (path == null) {
            return false;
        }
        tree.scrollPathToVisible(path);
        tree.setSelectionPath(path);
        return true;
    }

    private TreePath find(PropertySheetHolderTreeNode node, PropertySheetHolder holder) {
        if (node.getPropertySheetHolder() == holder) {
            List<TreeNode> ancestry = new ArrayList<>();
            for (TreeNode walk = node; walk != null; walk = walk.getParent()) {
                ancestry.add(0, walk);
            }
            return new TreePath(ancestry.toArray());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            TreePath found = find((PropertySheetHolderTreeNode) node.getChildAt(i), holder);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * The actions of every node on the way down to the path, not just the selected one: a nozzle
     * tip's actions belong beside the head's and the machine's.
     */
    private void fillToolbar(TreePath path) {
        toolBar.removeAll();
        if (path != null) {
            List<Object> pathsReverse = Arrays.asList(path.getPath());
            Collections.reverse(pathsReverse);
            for (Object o : pathsReverse) {
                PropertySheetHolderTreeNode node = (PropertySheetHolderTreeNode) o;
                Action[] actions = node.obj.getPropertySheetHolderActions();
                if (actions != null) {
                    for (Action action : actions) {
                        toolBar.add(action);
                    }
                }
            }
        }
        toolBar.revalidate();
        toolBar.repaint();
    }

    /** The path whose properties are on show, for putting back when the user keeps unapplied edits. */
    private TreePath shownPath;
    private boolean revertingSelection;

    public void selectCurrentTreePath() {
        if (revertingSelection) {
            return;
        }
        TreePath path = tree.getSelectionPath();
        fillToolbar(path);
        PropertySheetHolder holder = null;
        if (path != null) {
            PropertySheetHolderTreeNode node =
                    (PropertySheetHolderTreeNode) path.getLastPathComponent();
            if (node != null) {
                holder = node.obj;
            }
        }
        org.openpnp.gui.shell.PropertySheetPresenter.Result shown = MainFrame.get().getInspector()
                .show(MachineSetupPanel.this, holder,
                MachineSetupPanel.this,
                holder == null ? null : holder.getPropertySheetHolderTitle(),
                holder == null ? null : holder.getPropertySheetHolderIcon());
        if (shown == org.openpnp.gui.shell.PropertySheetPresenter.Result.Cancelled) {
            // The user kept the unapplied edits: the element they belong to stays selected, with
            // its own actions in the toolbar.
            revertingSelection = true;
            try {
                tree.setSelectionPath(shownPath);
            }
            finally {
                revertingSelection = false;
            }
            fillToolbar(shownPath);
            return;
        }
        if (shown == org.openpnp.gui.shell.PropertySheetPresenter.Result.Shown) {
            shownPath = path;
        }

        revalidate();
        repaint();
    }

    public class PropertySheetHolderTreeNode implements TreeNode {
        private final PropertySheetHolder obj;
        private final TreeNode parent;
        private final ArrayList<PropertySheetHolderTreeNode> children = new ArrayList<>();

        public PropertySheetHolderTreeNode(PropertySheetHolder obj, TreeNode parent) {
            this.obj = obj;
            this.parent = parent;
            loadChildren();
            /**
             * If the object we're creating a node for supports property change then we add
             * a listener. When we get an indexed changed we refresh our children and
             * when we get a non-indexed change we refresh ourself.
             * 
             * TODO: Note: Since we don't know which child got refreshed, we refresh them
             * all and this causes the JTree to collapse all the other children. This sucks
             * but there isn't a clean way to know which child changed without including the
             * property name somewhere.
             */
            BeanUtils.addPropertyChangeListener(obj, (PropertyChangeListener) (e) -> {
                SwingUtilities.invokeLater(() -> {
                    if (e instanceof IndexedPropertyChangeEvent) {
                        for (PropertySheetHolderTreeNode node : children) {
                            node.loadChildren();
                            treeModel.nodeStructureChanged(node);
                        }
                    }
                    else {
                        treeModel.nodeChanged(this);
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
        public Enumeration children() {
            return Collections.enumeration(children);
        }

        @Override
        public String toString() {
            return obj.getPropertySheetHolderTitle();
        }
    }

    private TreeCellRenderer treeCellRenderer = new DefaultTreeCellRenderer() {
        // http://stackoverflow.com/questions/20691946/set-icon-to-each-node-in-jtree
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row,
                    hasFocus);
            if (value instanceof PropertySheetHolderTreeNode) {
                PropertySheetHolderTreeNode node = (PropertySheetHolderTreeNode) value;
                PropertySheetHolder psh = node.getPropertySheetHolder();
                setIcon(psh.getPropertySheetHolderIcon());
            }
            return this;
        }
    };
    private class SwingAction extends AbstractAction {
        public SwingAction() {
            putValue(NAME, Translations.getString("MachineSetupPanel.Action.Expand")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("MachineSetupPanel.Action.Expand.Description")); //$NON-NLS-1$
        }
        
        public void actionPerformed(ActionEvent e) {
          for (int i = 1; i < tree.getRowCount(); i++) {
            if (cbExp.isSelected()) {
              tree.expandRow(i);
            }
            else {
              tree.collapseRow(i);
            }
          }
        }
    }
}
