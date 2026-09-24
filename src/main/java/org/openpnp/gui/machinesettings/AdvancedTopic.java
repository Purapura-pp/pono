/*
 * Copyright (C) 2026 Pono contributors
 * 
 * This file is part of Pono, a modified version of OpenPnP.
 * 
 * Pono is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * Pono is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Pono. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package org.openpnp.gui.machinesettings;

import java.awt.BorderLayout;

import javax.swing.JComponent;
import javax.swing.JPanel;

import org.openpnp.gui.MachineSetupPanel;

/**
 * Every element of the machine as the configuration holds it: the tree the machine page was, its
 * selection's forms in the properties column. What the other topics do not show is set here.
 */
final class AdvancedTopic extends Topic {
    private final MachineSetupPanel tree;

    AdvancedTopic(MachineSetupPanel tree) {
        super(MachineSettingsPanel.ADVANCED, "tree"); //$NON-NLS-1$
        this.tree = tree;
    }

    @Override
    protected JComponent build() {
        JPanel view = new JPanel(new BorderLayout());
        view.setOpaque(false);
        view.add(tree.getStructure(), BorderLayout.CENTER);
        return view;
    }

    /**
     * The tree was made with the window and is outside it until the topic is first shown, so a
     * theme chosen in the meantime has not reached it.
     */
    @Override
    void shown() {
        javax.swing.SwingUtilities.updateComponentTreeUI(tree.getStructure());
    }

    /** The tree is the same one however often the machine changes: it rebuilds itself. */
    @Override
    void discard() {
    }
}
