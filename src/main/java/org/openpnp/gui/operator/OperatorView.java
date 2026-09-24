/*
 * Copyright (C) 2026 Pono
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

package org.openpnp.gui.operator;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import org.openpnp.Translations;
import org.openpnp.gui.JobPanel;
import org.openpnp.gui.JobRunLogPanel;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.RoundedPanel;
import org.openpnp.gui.shell.Tokens;
import org.openpnp.gui.shell.Ui;
import org.openpnp.model.Configuration;
import org.openpnp.model.Job;
import org.openpnp.model.JobRun;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;

/**
 * Production mode's column, as mockup 03 has it: the job's progress, the big buttons, the feeders
 * that need a hand and the latest events; and the banner over the camera saying what is being
 * placed. It shows; it edits nothing but a feeder's count when the operator says it is refilled.
 */
@SuppressWarnings("serial")
public class OperatorView extends JPanel {
    /** The column's width, as the mockup's. */
    private static final int SIDE = 440;
    private static final int EVENTS = 12;

    private final Configuration configuration;
    private final JobPanel jobPanel;
    private final JPanel stage = new JPanel(new BorderLayout());

    private final JLabel elapsed = Ui.muted(""); //$NON-NLS-1$
    private final Ring ring = new Ring();
    private final JLabel currentBoard = value();
    private final JLabel remaining = value();
    private final JLabel cycle = value();
    private final JLabel errors = value();
    private final JLabel skipped = value();
    private final JPanel boards = new JPanel(new GridLayout(0, 8, 6, 6));
    private final JButton startPause;
    private final JButton step;
    private final JButton stop;
    private final JLabel attentionCount = Ui.muted(""); //$NON-NLS-1$
    private final JPanel attention = column();
    private final JPanel events = column();

    private final JPanel banner = new JPanel();
    private final JLabel bannerTitle = new JLabel();
    private final JLabel bannerDetail = Ui.t2(""); //$NON-NLS-1$
    private final Chip bannerAlignment = new Chip("", Chip.Tone.Neutral, Chip.Shape.Chip); //$NON-NLS-1$

    /** A second's tick for the clock and the time run, and a quarter's for a burst of run changes. */
    private final Timer tick = new Timer(1000, e -> refresh());
    private final Timer soon = new Timer(250, e -> refresh());
    private final java.beans.PropertyChangeListener runListener = e -> SwingUtilities.invokeLater(() -> {
        if (!soon.isRunning()) {
            soon.start();
        }
    });
    private JobRun heard;
    private String shown;

    public OperatorView(Configuration configuration, JobPanel jobPanel) {
        super(new BorderLayout(Tokens.GAP_CARD, 0));
        this.configuration = configuration;
        this.jobPanel = jobPanel;
        setOpaque(false);
        soon.setRepeats(false);
        stage.setOpaque(false);
        add(stage, BorderLayout.CENTER);

        startPause = big("OperatorView.Start", "play", Ui.Variant.PrimaryOk, jobPanel.startPauseResumeJobAction); //$NON-NLS-1$ //$NON-NLS-2$
        step = big("TopBar.Job.Step", "step", Ui.Variant.Default, jobPanel.stepJobAction); //$NON-NLS-1$ //$NON-NLS-2$
        stop = big("TopBar.Job.Stop", "stop", Ui.Variant.SolidDanger, jobPanel.stopJobAction); //$NON-NLS-1$ //$NON-NLS-2$

        Ui.whyDisabled(startPause, () -> reason(jobPanel.startPauseResumeJobAction));
        Ui.whyDisabled(step, () -> reason(jobPanel.stepJobAction));
        Ui.whyDisabled(stop, () -> reason(jobPanel.stopJobAction));

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.add(progressCard());
        stack.add(Box.createVerticalStrut(12));
        JPanel buttons = new JPanel(new GridLayout(1, 3, 10, 0));
        buttons.setOpaque(false);
        buttons.add(startPause);
        buttons.add(step);
        buttons.add(stop);
        stack.add(fill(buttons));
        stack.add(Box.createVerticalStrut(12));
        stack.add(card("alert", "OperatorView.Attention", attentionCount, attention)); //$NON-NLS-1$ //$NON-NLS-2$
        stack.add(Box.createVerticalStrut(12));
        JLabel log = Ui.muted(Translations.getString("OperatorView.Events.Log")); //$NON-NLS-1$
        log.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        log.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                showLog();
            }
        });
        // The events take what height is left, and show as many whole rows as fit in it.
        JPanel eventsBody = new JPanel(new BorderLayout());
        eventsBody.setOpaque(false);
        eventsBody.add(events, BorderLayout.NORTH);
        eventsBody.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                fitEvents(eventsBody.getHeight());
            }
        });
        this.eventsBody = eventsBody;
        JPanel side = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(UIScale.scale(SIDE), super.getPreferredSize().height);
            }
        };
        side.setOpaque(false);
        side.add(stack, BorderLayout.NORTH);
        side.add(card("clock", "OperatorView.Events", log, eventsBody), BorderLayout.CENTER); //$NON-NLS-1$ //$NON-NLS-2$
        add(side, BorderLayout.EAST);

        buildBanner();
        jobPanel.addPropertyChangeListener(JobPanel.PROPERTY_JOB_STATE, e -> refresh());
        jobPanel.addPropertyChangeListener(JobPanel.PROPERTY_JOB_RUNNING, e -> refresh());
    }

    /** Where the camera's stage goes while production mode is on. */
    public JPanel getStage() {
        return stage;
    }

    /** The banner the camera's stage carries at its top while production mode is on. */
    public JComponent getBanner() {
        return banner;
    }

    /** Starts or stops following the job: only while it is shown. */
    public void setActive(boolean active) {
        this.active = active;
        if (active) {
            tick.start();
            refresh();
        }
        else {
            tick.stop();
            soon.stop();
            listen(null);
        }
    }

    private void listen(JobRun run) {
        if (heard == run) {
            return;
        }
        if (heard != null) {
            heard.removePropertyChangeListener(JobRun.PROPERTY_RUN, runListener);
        }
        heard = run;
        if (run != null) {
            run.addPropertyChangeListener(JobRun.PROPERTY_RUN, runListener);
        }
    }

    private boolean active;

    /** Everything shown, worked out again, while production mode is on. */
    public void refresh() {
        Job job = jobPanel.getJob();
        if (!active || job == null) {
            return;
        }
        listen(job.getRun());
        boolean running = jobPanel.isJobRunning();
        OperatorSummary s = OperatorSummary.of(job, configuration.getMachine(), running,
                System.currentTimeMillis(), EVENTS);

        elapsed.setText(s.elapsedMillis > 0
                ? String.format(Translations.getString("OperatorView.Progress.Elapsed"), //$NON-NLS-1$
                        JobPanel.duration(s.elapsedMillis / 1000.0))
                : Translations.getString(jobPanel.isJobPaused() ? "OperatorView.Paused" : "OperatorView.Idle")); //$NON-NLS-1$ //$NON-NLS-2$
        ring.set(s.placed, s.total);
        currentBoard.setText(s.boards.isEmpty() ? "\u2014" //$NON-NLS-1$
                : (s.currentBoard == 0 ? "\u2014" : String.valueOf(s.currentBoard)) + " / " + s.boards.size()); //$NON-NLS-1$ //$NON-NLS-2$
        remaining.setText(Double.isNaN(s.remainingSeconds) ? "\u2014" : JobPanel.duration(s.remainingSeconds)); //$NON-NLS-1$
        cycle.setText(Double.isNaN(s.cycleSeconds) ? "\u2014" //$NON-NLS-1$
                : String.format(Translations.getString("OperatorView.Cycle.Value"), s.cycleSeconds)); //$NON-NLS-1$
        errors.setText(String.valueOf(s.errors));
        errors.setForeground(s.errors > 0 ? Ui.errText() : Ui.text());
        skipped.setText(String.valueOf(s.skipped));
        showBoards(s);
        showButtons();
        showAttention(s);
        showEvents(s);
        showBanner(s);
        if (refreshed != null) {
            refreshed.accept(s);
        }
    }

    private java.util.function.Consumer<OperatorSummary> refreshed;

    /** Told what was shown after every refresh: the top bar counts the feeders from it. */
    public void onRefresh(java.util.function.Consumer<OperatorSummary> refreshed) {
        this.refreshed = refreshed;
    }

    // ----- the progress card --------------------------------------------------------------------

    private JComponent progressCard() {
        JPanel body = new JPanel(new BorderLayout(18, 0));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(16, 16, 8, 16));
        body.add(ring, BorderLayout.WEST);
        JPanel kv = new JPanel(new GridLayout(2, 2, 14, 10));
        kv.setOpaque(false);
        kv.add(pair("OperatorView.CurrentBoard", currentBoard)); //$NON-NLS-1$
        kv.add(pair("OperatorView.Remaining", remaining)); //$NON-NLS-1$
        kv.add(pair("OperatorView.Cycle", cycle)); //$NON-NLS-1$
        JPanel errorsSkipped = new JPanel();
        errorsSkipped.setOpaque(false);
        errorsSkipped.setLayout(new BoxLayout(errorsSkipped, BoxLayout.X_AXIS));
        errorsSkipped.add(errors);
        JLabel slash = value();
        slash.setText(" / "); //$NON-NLS-1$
        errorsSkipped.add(slash);
        errorsSkipped.add(skipped);
        errorsSkipped.add(Box.createHorizontalGlue());
        kv.add(pair("OperatorView.ErrorsSkipped", errorsSkipped)); //$NON-NLS-1$
        JPanel kvHolder = new JPanel(new BorderLayout());
        kvHolder.setOpaque(false);
        kvHolder.add(kv, BorderLayout.CENTER);
        body.add(kvHolder, BorderLayout.CENTER);
        boards.setOpaque(false);
        boards.setBorder(new EmptyBorder(4, 16, 14, 16));
        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.add(body, BorderLayout.NORTH);
        content.add(boards, BorderLayout.CENTER);
        return card("job", "OperatorView.Progress", elapsed, content); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static JComponent pair(String key, JComponent value) {
        JPanel pair = new JPanel();
        pair.setOpaque(false);
        pair.setLayout(new BoxLayout(pair, BoxLayout.Y_AXIS));
        JLabel k = Ui.muted(Translations.getString(key));
        k.setFont(Ui.font(11f));
        k.setAlignmentX(LEFT_ALIGNMENT);
        value.setAlignmentX(LEFT_ALIGNMENT);
        pair.add(k);
        pair.add(Box.createVerticalStrut(2));
        pair.add(value);
        return pair;
    }

    private static JLabel value() {
        JLabel label = new JLabel("\u2014"); //$NON-NLS-1$
        label.setFont(Ui.mono(16f, Font.BOLD));
        return label;
    }

    private void showBoards(OperatorSummary s) {
        String key = s.boards.toString() + s.currentBoard;
        if (key.equals(boards.getName())) {
            return;
        }
        boards.setName(key);
        boards.removeAll();
        for (int i = 0; i < s.boards.size(); i++) {
            boards.add(new BoardCell(i + 1, s.boards.get(i)));
        }
        boards.setVisible(!s.boards.isEmpty());
        boards.revalidate();
        boards.repaint();
    }

    // ----- the big buttons ------------------------------------------------------------------------

    private JButton big(String key, String icon, Ui.Variant variant, Action action) {
        JButton button = Ui.button(Translations.getString(key), Ui.icon(icon, 18), Ui.Size.Md, variant);
        button.setFont(Ui.font(15f, Font.BOLD));
        button.setFocusable(false);
        button.addActionListener(e -> {
            if (action.isEnabled()) {
                action.actionPerformed(e);
            }
        });
        button.setPreferredSize(new Dimension(UIScale.scale(120), UIScale.scale(52)));
        return button;
    }

    private void showButtons() {
        Action startPauseAction = jobPanel.startPauseResumeJobAction;
        boolean running = jobPanel.isJobRunning();
        String key = running ? "TopBar.Job.Pause" : jobPanel.isJobPaused() ? "TopBar.Job.Resume" : "OperatorView.Start"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        if (!key.equals(startPause.getName())) {
            startPause.setName(key);
            startPause.setText(Translations.getString(key));
            startPause.setIcon(Ui.icon(running ? "pause" : "play", 18)); //$NON-NLS-1$ //$NON-NLS-2$
            // Pausing is the warning's colour, as the mockup has it; starting is the go green.
            String style = (String) startPause.getClientProperty(FlatClientProperties.STYLE);
            String base = style == null ? "" : style.replaceAll(";?\\s*/\\*warn\\*/.*$", ""); //$NON-NLS-1$ //$NON-NLS-2$
            startPause.putClientProperty(FlatClientProperties.STYLE, !running ? base : base
                    + "; /*warn*/ background: $Pono.warnSoft; borderColor: fade($Pono.warn,50%); foreground: $Pono.warn;" //$NON-NLS-1$
                    + " hoverBackground: fade($Pono.warn,25%); pressedBackground: fade($Pono.warn,35%)"); //$NON-NLS-1$
        }
        startPause.setEnabled(startPauseAction.isEnabled());
        step.setEnabled(jobPanel.stepJobAction.isEnabled());
        stop.setEnabled(jobPanel.stopJobAction.isEnabled());
    }

    // ----- what needs a hand ------------------------------------------------------------------------

    private void showAttention(OperatorSummary s) {
        attention.removeAll();
        attentionCount.setText(String.format(Translations.getString("OperatorView.Attention.Count"), s.attention.size())); //$NON-NLS-1$
        if (s.attention.isEmpty()) {
            attention.add(line(Ui.muted(Translations.getString("OperatorView.Attention.None")), null, null)); //$NON-NLS-1$
        }
        for (OperatorSummary.Attention item : s.attention) {
            Chip chip = new Chip(Translations.getString(item.empty ? "OperatorView.Attention.Empty" : "OperatorView.Attention.Low"), //$NON-NLS-1$ //$NON-NLS-2$
                    item.empty ? Chip.Tone.Err : Chip.Tone.Warn, Chip.Shape.Status);
            String detail = item.empty && !item.waiting.isEmpty()
                    ? String.format(Translations.getString("OperatorView.Attention.Waiting"), item.partId, item.waiting.get(0)) //$NON-NLS-1$
                    : item.left != null
                            ? String.format(Translations.getString("OperatorView.Attention.Left"), item.partId, item.left) //$NON-NLS-1$
                            : item.partId;
            JButton act = Ui.button(Translations.getString(item.empty ? "OperatorView.Attention.RefillAndGo" : "OperatorView.Attention.Refilled"), //$NON-NLS-1$ //$NON-NLS-2$
                    null, Ui.Size.Xs, item.empty ? Ui.Variant.Primary : Ui.Variant.Default);
            act.setFocusable(false);
            act.addActionListener(e -> refilled(item));
            attention.add(line(chip, text(item.feeder.getName(), detail), act));
        }
        attention.revalidate();
        attention.repaint();
    }

    /**
     * The feeder is full again: it counts from its new stock and is enabled, as running out left it
     * disabled; a job paused for it goes on.
     */
    private void refilled(OperatorSummary.Attention item) {
        item.feeder.refill(null);
        if (item.empty) {
            item.feeder.setEnabled(true);
            if (jobPanel.isJobPaused() && jobPanel.startPauseResumeJobAction.isEnabled()) {
                jobPanel.startPauseResumeJobAction.actionPerformed(null);
            }
        }
        configuration.setDirty(true);
        refresh();
    }

    // ----- the latest events -------------------------------------------------------------------------

    private void showEvents(OperatorSummary s) {
        String key = s.events.isEmpty() ? "" : s.events.get(0).getMillis() + "/" + s.events.size(); //$NON-NLS-1$ //$NON-NLS-2$
        if (key.equals(shown)) {
            return;
        }
        shown = key;
        events.removeAll();
        if (s.events.isEmpty()) {
            events.add(line(Ui.muted(Translations.getString("OperatorView.Events.None")), null, null)); //$NON-NLS-1$
        }
        SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss"); //$NON-NLS-1$
        for (JobRun.Event event : s.events) {
            Chip chip = new Chip(Translations.getString("JobRunLogPanel.Kind." + event.getKind()), tone(event.getKind()), //$NON-NLS-1$
                    Chip.Shape.Status);
            JLabel text = new JLabel(JobRunLogPanel.describe(event));
            text.setFont(Ui.font(12f));
            JLabel at = Ui.muted(time.format(new Date(event.getMillis())));
            at.setFont(Ui.mono(11f, Font.PLAIN));
            events.add(line(chip, text, at));
        }
        fitEvents(eventsBody.getHeight());
        events.repaint();
    }

    private JPanel eventsBody;

    /** The rows that fit whole in the height the card has; the rest wait for the log. */
    private void fitEvents(int height) {
        int used = 0;
        for (Component row : events.getComponents()) {
            int h = row.getPreferredSize().height;
            boolean fits = used + h <= height;
            if (row.isVisible() != fits) {
                row.setVisible(fits);
            }
            if (fits) {
                used += h;
            }
        }
        events.revalidate();
    }

    private static String reason(Action action) {
        Object reason = action.getValue(Ui.WHY_DISABLED);
        return reason == null ? null : reason.toString();
    }

    static Chip.Tone tone(JobRun.EventKind kind) {
        switch (kind) {
            case Placed:
            case Finished:
                return Chip.Tone.Ok;
            case Retry:
                return Chip.Tone.Warn;
            case FeederEmpty:
            case Error:
                return Chip.Tone.Err;
            case Skipped:
                return Chip.Tone.Skip;
            case Fiducials:
                return Chip.Tone.Run;
            default:
                return Chip.Tone.Neutral;
        }
    }

    private void showLog() {
        Job job = jobPanel.getJob();
        if (job == null) {
            return;
        }
        JobRunLogPanel log = new JobRunLogPanel();
        log.setRun(job.getRun());
        log.refresh();
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                Translations.getString("OperatorView.Events.LogTitle"), java.awt.Dialog.ModalityType.MODELESS); //$NON-NLS-1$
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setContentPane(log);
        dialog.setSize(UIScale.scale(760), UIScale.scale(480));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // ----- the banner over the camera ------------------------------------------------------------

    private void buildBanner() {
        banner.setOpaque(false);
        banner.setLayout(new BoxLayout(banner, BoxLayout.X_AXIS));
        banner.setBorder(new EmptyBorder(10, 16, 10, 16));
        JComponent pulse = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int d = UIScale.scale(10);
                int halo = UIScale.scale(4);
                int x = (getWidth() - d) / 2;
                int y = (getHeight() - d) / 2;
                g2.setColor(Ui.okSoft());
                g2.fillOval(x - halo, y - halo, d + 2 * halo, d + 2 * halo);
                g2.setColor(Ui.ok());
                g2.fillOval(x, y, d, d);
                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(UIScale.scale(18), UIScale.scale(18));
            }

            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        bannerTitle.setFont(Ui.font(15f, Font.BOLD));
        bannerDetail.setFont(Ui.font(13f));
        banner.add(pulse);
        banner.add(Box.createHorizontalStrut(14));
        banner.add(bannerTitle);
        banner.add(Box.createHorizontalStrut(8));
        banner.add(bannerDetail);
        banner.add(Box.createHorizontalStrut(14));
        banner.add(bannerAlignment);
        for (Component c : banner.getComponents()) {
            if (c instanceof JComponent) {
                ((JComponent) c).setAlignmentY(CENTER_ALIGNMENT);
            }
        }
    }

    private void showBanner(OperatorSummary s) {
        OperatorSummary.Current c = s.current;
        Component card = banner.getParent();
        if (card != null) {
            card.setVisible(c != null);
        }
        if (c == null) {
            return;
        }
        bannerTitle.setText(String.format(Translations.getString("OperatorView.Placing"), c.placementId)); //$NON-NLS-1$
        StringBuilder detail = new StringBuilder();
        if (c.partId != null) {
            detail.append(c.partId);
        }
        if (c.nozzle != null) {
            detail.append(detail.length() > 0 ? " \u00b7 " : "").append(String.format(Translations.getString("OperatorView.Nozzle"), c.nozzle)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        if (c.feeder != null) {
            detail.append(detail.length() > 0 ? " \u00b7 " : "").append(String.format(Translations.getString("OperatorView.Feeder"), c.feeder)); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        bannerDetail.setText(detail.toString());
        bannerAlignment.setVisible(!Double.isNaN(c.alignmentMm));
        if (!Double.isNaN(c.alignmentMm)) {
            bannerAlignment.setText(String.format(Translations.getString("OperatorView.Alignment"), c.alignmentMm)); //$NON-NLS-1$
        }
        banner.revalidate();
    }

    // ----- parts ---------------------------------------------------------------------------------------

    /** The stylesheet's {@code .card} with its {@code .ch} head: an icon, a title, a note at the right. */
    private static JComponent card(String icon, String titleKey, JComponent right, JComponent body) {
        RoundedPanel card = RoundedPanel.card();
        card.setLayout(new BorderLayout());
        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.X_AXIS));
        head.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.border()), new EmptyBorder(11, 16, 11, 16)));
        JLabel title = new JLabel(Translations.getString(titleKey), Ui.iconSm(icon), JLabel.LEADING);
        title.setFont(Ui.font(13f, Font.BOLD));
        title.setIconTextGap(8);
        head.add(title);
        head.add(Box.createHorizontalGlue());
        right.setFont(Ui.font(11f));
        head.add(right);
        card.add(head, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return fill(card);
    }

    /** As wide as the column, as tall as it needs. */
    private static JComponent fill(JComponent c) {
        c.setAlignmentX(LEFT_ALIGNMENT);
        JPanel holder = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        holder.setOpaque(false);
        holder.setAlignmentX(LEFT_ALIGNMENT);
        holder.add(c, BorderLayout.CENTER);
        return holder;
    }

    private static JPanel column() {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        return column;
    }

    /** A {@code .li} row: a status, what it is about, and something at its right end. */
    private static JComponent line(JComponent status, JComponent middle, JComponent end) {
        JPanel row = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.border()), new EmptyBorder(8, 16, 8, 16)));
        row.setAlignmentX(LEFT_ALIGNMENT);
        status.setAlignmentY(CENTER_ALIGNMENT);
        row.add(status);
        if (middle != null) {
            row.add(Box.createHorizontalStrut(10));
            middle.setAlignmentY(CENTER_ALIGNMENT);
            middle.setMinimumSize(new Dimension(0, middle.getPreferredSize().height));
            row.add(middle);
        }
        row.add(Box.createHorizontalGlue());
        if (end != null) {
            row.add(Box.createHorizontalStrut(10));
            end.setAlignmentY(CENTER_ALIGNMENT);
            row.add(end);
        }
        return row;
    }

    /** A name in bold and what follows it in the secondary colour. */
    private static JComponent text(String bold, String rest) {
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.X_AXIS));
        JLabel name = new JLabel(bold);
        name.setFont(Ui.font(12f, Font.BOLD));
        JLabel more = Ui.t2(rest);
        more.setFont(Ui.font(12f));
        text.add(name);
        text.add(Box.createHorizontalStrut(6));
        text.add(more);
        return text;
    }

    /** The ring of the placements done, with the percentage and the count in its middle. */
    private static final class Ring extends JComponent {
        private int done;
        private int total;

        void set(int done, int total) {
            if (this.done != done || this.total != total) {
                this.done = done;
                this.total = total;
                repaint();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(UIScale.scale(132), UIScale.scale(132));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            float stroke = UIScale.scale(10f);
            int size = Math.min(getWidth(), getHeight());
            double inset = stroke / 2 + UIScale.scale(4f);
            double d = size - 2 * inset;
            g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Ui.surface3());
            g2.draw(new Arc2D.Double(inset, inset, d, d, 0, 360, Arc2D.OPEN));
            double fraction = total > 0 ? Math.min(1, done / (double) total) : 0;
            if (fraction > 0) {
                g2.setColor(Ui.accent());
                g2.draw(new Arc2D.Double(inset, inset, d, d, 90, -360 * fraction, Arc2D.OPEN));
            }
            String percent = Math.round(fraction * 100) + "%"; //$NON-NLS-1$
            String count = String.format(Translations.getString("OperatorView.Progress.Count"), done, total); //$NON-NLS-1$
            g2.setFont(Ui.mono(30f, Font.BOLD));
            FontMetrics big = g2.getFontMetrics();
            Font small = Ui.font(11f);
            FontMetrics smallMetrics = g2.getFontMetrics(small);
            int textHeight = big.getAscent() + UIScale.scale(4) + smallMetrics.getAscent();
            int top = (size - textHeight) / 2;
            g2.setColor(Ui.text());
            g2.drawString(percent, (size - big.stringWidth(percent)) / 2, top + big.getAscent());
            g2.setFont(small);
            g2.setColor(Ui.muted());
            g2.drawString(count, (size - smallMetrics.stringWidth(count)) / 2, top + textHeight);
            g2.dispose();
        }
    }

    /** One board of the job: done, being placed, still to do. */
    private static final class BoardCell extends JComponent {
        private final int number;
        private final OperatorSummary.BoardState state;

        BoardCell(int number, OperatorSummary.BoardState state) {
            this.number = number;
            this.state = state;
            setToolTipText(Translations.getString("OperatorView.Board." + state)); //$NON-NLS-1$
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(UIScale.scale(40), UIScale.scale(26));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color fill;
            Color line;
            Color text;
            switch (state) {
                case Done:
                    fill = Ui.okSoft();
                    line = Ui.alpha(Ui.ok(), 0.35);
                    text = Ui.okText();
                    break;
                case Running:
                    fill = Ui.accent();
                    line = Ui.accent();
                    text = Ui.onAccent();
                    break;
                default:
                    fill = Ui.surface3();
                    line = Ui.border();
                    text = Ui.muted();
                    break;
            }
            int arc = UIScale.scale(12);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.setColor(line);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.setFont(Ui.mono(11f, state == OperatorSummary.BoardState.Running ? Font.BOLD : Font.PLAIN));
            FontMetrics fm = g2.getFontMetrics();
            String s = String.valueOf(number);
            g2.setColor(text);
            g2.drawString(s, (getWidth() - fm.stringWidth(s)) / 2, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }
}
