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

package org.openpnp.gui.shell;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.openpnp.Translations;

import com.formdev.flatlaf.FlatClientProperties;

/**
 * Type a few letters, get the command. Every menu item and every page of the rail is in here,
 * found by any part of its name, so nothing has to be remembered by where it lives.
 * <p>
 * Opened from the search box in the top bar or with Ctrl-K. Enter runs the highlighted command,
 * Escape closes.
 */
@SuppressWarnings("serial")
public class CommandPalette extends JDialog {
    /** One thing the palette can do: how it is named, and what running it does. */
    public static final class Command {
        final String path;
        final String name;
        final Runnable run;
        /** Null while the command can be run; otherwise why not, shown greyed after its name. */
        final String unavailable;

        public Command(String path, String name, Runnable run) {
            this(path, name, run, null);
        }

        public Command(String path, String name, Runnable run, String unavailable) {
            this.path = path;
            this.name = name;
            this.run = run;
            this.unavailable = unavailable;
        }

        @Override
        public String toString() {
            return path.isEmpty() ? name : path + "  ›  " + name; //$NON-NLS-1$
        }
    }

    private final List<Command> commands;
    private final JTextField field = new JTextField();
    private final DefaultListModel<Command> shown = new DefaultListModel<>();
    private final JList<Command> list = new JList<>(shown);

    public CommandPalette(Frame owner, List<Command> commands) {
        super(owner, true);
        this.commands = commands;
        setUndecorated(true);
        RoundedPanel panel = new RoundedPanel(10, Ui::surface, Ui::borderStrong);
        panel.setLayout(new BorderLayout(0, 6));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(panel);

        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                Translations.getString("TopBar.Search.Placeholder")); //$NON-NLS-1$
        field.putClientProperty(FlatClientProperties.STYLE, "arc: 12; minimumHeight: 32"); //$NON-NLS-1$
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filter();
            }
        });
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int index = list.getSelectedIndex();
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    list.setSelectedIndex(Math.min(shown.size() - 1, index + 1));
                    list.ensureIndexIsVisible(list.getSelectedIndex());
                }
                else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    list.setSelectedIndex(Math.max(0, index - 1));
                    list.ensureIndexIsVisible(list.getSelectedIndex());
                }
                else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    runSelected();
                }
                else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    dispose();
                }
            }
        });
        panel.add(field, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(10);
        list.setFont(Ui.font(Ui.BASE));
        // A command that cannot run now is listed greyed, with why, rather than left out: leaving
        // it out made it look as if the program had no such command.
        list.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(l, value, index, isSelected, cellHasFocus);
                Command command = (Command) value;
                if (command.unavailable != null) {
                    java.awt.Color muted = Ui.muted();
                    setText("<html>" + escape(command.toString()) + "&nbsp;&nbsp;<span style='color:" //$NON-NLS-1$ //$NON-NLS-2$
                            + String.format("#%06x", muted.getRGB() & 0xffffff) + "'>" //$NON-NLS-1$ //$NON-NLS-2$
                            + escape(command.unavailable) + "</span></html>"); //$NON-NLS-1$
                    setForeground(isSelected ? getForeground() : Ui.muted());
                }
                return this;
            }
        });
        // Escape closes wherever the focus is, and so does going to another window.
        getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "pono.close"); //$NON-NLS-1$
        getRootPane().getActionMap().put("pono.close", new javax.swing.AbstractAction() { //$NON-NLS-1$
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowLostFocus(java.awt.event.WindowEvent e) {
                dispose();
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    runSelected();
                }
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);

        filter();
        setSize(new Dimension(520, 380));
        setLocationRelativeTo(owner);
    }

    private void filter() {
        String needle = field.getText().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (Command command : commands) {
            if (needle.isEmpty() || command.toString().toLowerCase(Locale.ROOT).contains(needle)) {
                shown.addElement(command);
            }
        }
        if (!shown.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    private void runSelected() {
        Command command = list.getSelectedValue();
        if (command != null && command.unavailable != null) {
            // Greyed: stays open, the reason already on the line.
            java.awt.Toolkit.getDefaultToolkit().beep();
            return;
        }
        dispose();
        if (command != null) {
            command.run.run();
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
    }

    /** Every enabled item of the menu bar, named by its menu path. */
    public static List<Command> commandsOf(JMenuBar menuBar) {
        List<Command> commands = new ArrayList<>();
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu != null) {
                collect(menu, menu.getText(), commands);
            }
        }
        return commands;
    }

    private static void collect(JMenu menu, String path, List<Command> into) {
        for (Component child : menu.getMenuComponents()) {
            if (child instanceof JMenu) {
                collect((JMenu) child, path + " › " + ((JMenu) child).getText(), into); //$NON-NLS-1$
            }
            else if (child instanceof JMenuItem) {
                JMenuItem item = (JMenuItem) child;
                if (item.getText() != null && !item.getText().isEmpty()) {
                    String reason = null;
                    if (!item.isEnabled()) {
                        reason = item.getToolTipText() != null && !item.getToolTipText().isEmpty()
                                ? item.getToolTipText()
                                : Translations.getString("CommandPalette.Unavailable"); //$NON-NLS-1$
                    }
                    into.add(new Command(path, item.getText(), () -> item.doClick(), reason));
                }
            }
        }
    }

    public void open() {
        field.setText(""); //$NON-NLS-1$
        // Modal: setVisible does not return until the dialog closes, so the focus request has
        // to be queued rather than made afterwards.
        javax.swing.SwingUtilities.invokeLater(field::requestFocusInWindow);
        setVisible(true);
    }

    /** For Ctrl-K, which the hotkey table calls with no event to speak of. */
    public void actionPerformed(ActionEvent ignored) {
        open();
    }
}
