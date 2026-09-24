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
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;

import org.openpnp.gui.shell.DockPanel;
import org.openpnp.gui.shell.RoundedPanel;
import org.openpnp.gui.shell.Tokens;
import org.openpnp.gui.shell.Ui;

/** The tables inside a topic: as the dock draws them, as tall as their rows, in a rounded box. */
final class Tables {
    private Tables() {
    }

    /**
     * The stylesheet's {@code .boxed}: the table with its header and every row, scrolled with the
     * page around it rather than on its own.
     */
    static JComponent boxed(JTable table) {
        JScrollPane scroll = DockPanel.table(table);
        table.setFillsViewportHeight(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setWheelScrollingEnabled(false);
        Runnable fit = () -> {
            int height = table.getTableHeader().getPreferredSize().height
                    + table.getRowCount() * table.getRowHeight();
            scroll.setPreferredSize(new Dimension(10, height + 1));
            scroll.revalidate();
        };
        table.getModel().addTableModelListener(e -> fit.run());
        fit.run();
        RoundedPanel box = new RoundedPanel(Tokens.R_MD, Ui::surface, Ui::border);
        box.setLayout(new BorderLayout());
        box.setBorder(new EmptyBorder(1, 1, 1, 1));
        box.add(scroll, BorderLayout.CENTER);
        return box;
    }
}
