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
import java.awt.Component;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

import org.openpnp.Translations;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Tokens;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.shell.WidthTracking;

/**
 * The column at the right of a topic, the stylesheet's {@code .guide}: how to arrive at the
 * topic's values on any machine, as a few headed lists. The words are one translation per topic
 * in a markup of lines - "## " a heading, "1. " a step, "- " a point, anything else a paragraph -
 * so that a translator works with sentences and not with components.
 */
final class Guide {
    static final int WIDTH = 350;

    private Guide() {
    }

    /** The column for a topic's guide, with the buttons under it that lead somewhere. */
    static JComponent of(String text, JComponent... links) {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(14, 18, 14, 18));
        JLabel title = new JLabel(Translations.getString("MachineSettings.Guide"), //$NON-NLS-1$
                Ui.icon("book", 14, Ui.accent()), JLabel.LEFT); //$NON-NLS-1$
        title.setIconTextGap(8);
        title.setFont(Ui.weighted(Tokens.FS_BODY, Tokens.FW_TITLE));
        add(body, title, 0);
        int number = 0;
        boolean first = true;
        for (String line : text.split("\n")) { //$NON-NLS-1$
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("## ")) { //$NON-NLS-1$
                JLabel heading = new JLabel(trimmed.substring(3));
                heading.setFont(Ui.weighted(Tokens.FS_SMALL + 0.5f, Tokens.FW_SECTION));
                add(body, heading, first ? 12 : 16);
                number = 0;
            }
            else if (trimmed.matches("\\d+\\.\\s.*")) { //$NON-NLS-1$
                number++;
                add(body, item(number + ".", trimmed.substring(trimmed.indexOf(' ') + 1)), 6); //$NON-NLS-1$
            }
            else if (trimmed.startsWith("- ")) { //$NON-NLS-1$
                add(body, item("\u2022", trimmed.substring(2)), 6); //$NON-NLS-1$
            }
            else {
                add(body, paragraph(trimmed), 6);
                number = 0;
            }
            first = false;
        }
        for (JComponent link : links) {
            add(body, link, 10);
        }
        body.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(new WidthTracking(body));
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel column = new JPanel(new BorderLayout()) {
            @Override
            public void updateUI() {
                super.updateUI();
                setBackground(Ui.surface2());
                setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Ui.border()));
            }
        };
        column.setOpaque(true);
        column.add(scroll, BorderLayout.CENTER);
        column.setPreferredSize(new Dimension(WIDTH, 10));
        column.setMinimumSize(new Dimension(WIDTH, 10));
        return column;
    }

    private static void add(JPanel body, JComponent component, int gap) {
        if (gap > 0 && body.getComponentCount() > 0) {
            body.add(Box.createVerticalStrut(gap));
        }
        JComponent capped = MachineSettingsPanel.capped(component);
        capped.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(capped);
    }

    /** A step or a point: its number or bullet in a column of its own, the words wrapping beside it. */
    private static JComponent item(String marker, String text) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        JLabel mark = Ui.t2(marker);
        mark.setFont(Ui.font(Tokens.FS_SMALL));
        mark.setVerticalAlignment(JLabel.TOP);
        mark.setPreferredSize(new Dimension(16, mark.getPreferredSize().height));
        row.add(mark, BorderLayout.WEST);
        row.add(paragraph(text), BorderLayout.CENTER);
        return row;
    }

    private static JTextArea paragraph(String text) {
        JTextArea area = Forms.paragraph(text);
        area.setFont(Ui.font(Tokens.FS_SMALL));
        area.setForeground(Ui.text2());
        return area;
    }
}
