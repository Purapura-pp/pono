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

package org.openpnp.gui.calibration;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

import org.openpnp.Translations;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Tokens;
import org.openpnp.gui.shell.Ui;
import org.openpnp.util.UiUtils;

/**
 * The calibration page's report tab: what the last run did, and every report on disk, the
 * calibration's and the measurements', newest first, each a click from its folder. The toolbar's
 * Open report used to open the last run's folder only, and said to take measurements first when
 * there was none.
 */
@SuppressWarnings("serial")
public class ReportsPane extends JPanel {
    /** The folder both write their reports into, one folder per run named by its time. */
    public static final String FOLDER = "diagnostics"; //$NON-NLS-1$
    private static final String CALIBRATION = "-calibration"; //$NON-NLS-1$
    private static final SimpleDateFormat STAMP = new SimpleDateFormat("yyyyMMdd-HHmmss"); //$NON-NLS-1$
    private static final SimpleDateFormat SHOWN = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"); //$NON-NLS-1$

    private final Supplier<File> configurationDirectory;
    private final JTextArea last = new JTextArea();
    private final JPanel lastBlock = new JPanel(new BorderLayout());
    private final JPanel list = new JPanel();

    public ReportsPane(Supplier<File> configurationDirectory) {
        super(new BorderLayout());
        this.configurationDirectory = configurationDirectory;
        setOpaque(false);
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(12, 14, 12, 14));

        last.setEditable(false);
        last.setFont(Ui.mono(12f, Font.PLAIN));
        last.setOpaque(false);
        last.setBorder(new EmptyBorder(6, 0, 0, 0));
        lastBlock.setOpaque(false);
        lastBlock.add(heading(Translations.getString("CalibrationPanel.Reports.Last")), BorderLayout.NORTH); //$NON-NLS-1$
        lastBlock.add(last, BorderLayout.CENTER);
        lastBlock.setBorder(new EmptyBorder(0, 0, 14, 0));
        lastBlock.setAlignmentX(Component.LEFT_ALIGNMENT);
        lastBlock.setVisible(false);
        body.add(lastBlock);

        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(list);
        body.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        add(scroll, BorderLayout.CENTER);
        refresh();
    }

    /** What the last calibration run did, as its summary reads. */
    public void setLastRun(String text) {
        last.setText(text);
        last.setCaretPosition(0);
        lastBlock.setVisible(text != null && !text.isEmpty());
        refresh();
    }

    /** Looks at the reports on disk again. */
    public void refresh() {
        list.removeAll();
        List<File> reports = reports();
        if (reports.isEmpty() && !lastBlock.isVisible()) {
            list.add(empty());
        }
        else if (!reports.isEmpty()) {
            list.add(heading(String.format(Translations.getString("CalibrationPanel.Reports.All"), reports.size()))); //$NON-NLS-1$
            list.add(Box.createVerticalStrut(6));
            for (File report : reports) {
                list.add(row(report));
            }
        }
        list.revalidate();
        list.repaint();
    }

    /** The report folders, newest first: a name starts with the time the run began. */
    private List<File> reports() {
        File directory = configurationDirectory.get();
        File[] folders = directory == null ? null : new File(directory, FOLDER).listFiles(File::isDirectory);
        List<File> reports = new ArrayList<>();
        if (folders != null) {
            for (File folder : folders) {
                if (when(folder) != null) {
                    reports.add(folder);
                }
            }
        }
        reports.sort(Comparator.comparing(File::getName).reversed());
        return reports;
    }

    private static Date when(File folder) {
        String name = folder.getName();
        if (name.length() < 15) {
            return null;
        }
        try {
            synchronized (STAMP) {
                return STAMP.parse(name.substring(0, 15));
            }
        }
        catch (ParseException e) {
            return null;
        }
    }

    private Line row(File folder) {
        boolean calibration = folder.getName().endsWith(CALIBRATION);
        Line row = new Line();
        JLabel time = new JLabel(SHOWN.format(when(folder)));
        time.setFont(Ui.mono(12f, Font.PLAIN));
        row.add(time);
        row.add(Box.createHorizontalStrut(12));
        row.add(new Chip(Translations.getString(calibration ? "CalibrationPanel.Reports.Calibration" //$NON-NLS-1$
                : "CalibrationPanel.Reports.Measurement"), calibration ? Chip.Tone.Run : Chip.Tone.Pending, //$NON-NLS-1$
                Chip.Shape.Status));
        row.add(Box.createHorizontalGlue());
        JButton open = Ui.button(Translations.getString("CalibrationPanel.Reports.Open"), Ui.iconSm("folder"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Xs, Ui.Variant.Ghost);
        open.setFocusable(false);
        open.addActionListener(e -> UiUtils.messageBoxOnException(() -> {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                throw new Exception(folder.getAbsolutePath());
            }
            Desktop.getDesktop().open(folder);
        }));
        row.add(open);
        return row;
    }

    private Line empty() {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        JLabel icon = new JLabel(Ui.icon("file", 26, Ui.muted())); //$NON-NLS-1$
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel title = new JLabel(Translations.getString("CalibrationPanel.Report.None")); //$NON-NLS-1$
        title.setFont(Ui.weighted(13.5f, Tokens.FW_SECTION));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        javax.swing.JTextArea text = Forms.paragraph(Translations.getString("CalibrationPanel.Reports.Empty")); //$NON-NLS-1$
        text.setAlignmentX(Component.CENTER_ALIGNMENT);
        text.setMaximumSize(new Dimension(420, Integer.MAX_VALUE));
        column.add(Box.createVerticalStrut(24));
        column.add(icon);
        column.add(Box.createVerticalStrut(6));
        column.add(title);
        column.add(Box.createVerticalStrut(4));
        column.add(text);
        Line row = new Line();
        row.setBorder(null);
        row.add(Box.createHorizontalGlue());
        row.add(column);
        row.add(Box.createHorizontalGlue());
        return row;
    }

    private static JLabel heading(String text) {
        JLabel heading = new JLabel(text);
        heading.setFont(Ui.weighted(12.5f, Tokens.FW_SECTION));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        return heading;
    }

    /** A line of the list, as wide as the list and as tall as what is in it. */
    private static final class Line extends JPanel {
        Line() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.border()),
                    new EmptyBorder(6, 2, 6, 2)));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }
}
