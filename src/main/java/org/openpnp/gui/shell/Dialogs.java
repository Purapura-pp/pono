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
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import org.openpnp.Translations;
import org.pmw.tinylog.Logger;

import com.formdev.flatlaf.FlatClientProperties;

/**
 * The program's three kinds of dialog, as the mockups' 19 draws them.
 * <p>
 * An error says in its title what happened, in its first sentence why, and in its second what to
 * do and where things stand now; the original message and the stack trace are under "Details",
 * folded. The same error within five seconds is shown once, and counted.
 * <p>
 * A question names its buttons by what they do - "Move there and set up", "Set up without
 * moving", "Cancel" - never "Yes" and "No"; the button the title asks about is on the right, and
 * one that moves the machine carries the mark that says so.
 * <p>
 * A dangerous action - deleting, aborting, overwriting the configuration - names what and how
 * many on its red button, and Cancel is where the focus starts and what Esc does.
 * <p>
 * There used to be one dialog, JOptionPane's, titled "Error" in English whatever the language,
 * holding the exception's message, or the whole stack trace when the message was empty, and
 * three hundred callers each deciding for themselves what a question's Yes and No meant.
 */
public final class Dialogs {
    private Dialogs() {
    }

    /** The tint of the icon box: what kind of dialog this is at a glance. */
    public enum Tone {
        Err, Warn, Info
    }

    /** One button of a dialog's foot. */
    public static final class Choice {
        final String label;
        final Icon icon;
        final Ui.Variant variant;
        boolean movesMachine;
        boolean left;
        Runnable action;

        public Choice(String label, Icon icon, Ui.Variant variant) {
            this.label = label;
            this.icon = icon;
            this.variant = variant;
        }

        public static Choice primary(String label) {
            return new Choice(label, null, Ui.Variant.Primary);
        }

        public static Choice plain(String label) {
            return new Choice(label, null, Ui.Variant.Default);
        }

        public static Choice cancel() {
            return new Choice(Translations.getString("Dialogs.Cancel"), null, Ui.Variant.Ghost); //$NON-NLS-1$
        }

        public static Choice danger(String label) {
            return new Choice(label, Ui.iconSm("trash"), Ui.Variant.SolidDanger); //$NON-NLS-1$
        }

        /** The button moves the machine, and says so. */
        public Choice movesMachine() {
            this.movesMachine = true;
            return this;
        }

        /** A button at the left of the foot that does something and leaves the dialog open. */
        public Choice utility(Runnable action) {
            this.left = true;
            this.action = action;
            return this;
        }
    }

    /** What a dialog says, in the order the stylesheet lays it out. */
    public static final class Content {
        Tone tone = Tone.Info;
        String icon = "info"; //$NON-NLS-1$
        String title;
        String what;
        String more;
        String list;
        String details;
        int width = 560;

        public Content tone(Tone tone, String icon) {
            this.tone = tone;
            this.icon = icon;
            return this;
        }

        public Content title(String title) {
            this.title = title;
            return this;
        }

        /** The first sentence: what happened, or what is being asked about. */
        public Content what(String what) {
            this.what = what;
            return this;
        }

        /** The second: what to do, what it will do, where things stand. */
        public Content more(String more) {
            this.more = more;
            return this;
        }

        /** The things concerned, one a line, in the mono box: the feeders about to be deleted. */
        public Content list(String list) {
            this.list = list;
            return this;
        }

        /** The original message and the stack, folded under "Details". */
        public Content details(String details) {
            this.details = details;
            return this;
        }

        public Content width(int width) {
            this.width = width;
            return this;
        }
    }

    /**
     * Shows a dialog and waits for it.
     * 
     * @param choices The foot's buttons, left to right; the last is the one the title asks about.
     * @param cancel The index of the choice Esc and the window's close button mean; -1 for none.
     * @param focus The index of the button the focus starts on.
     * @return The index of the choice made, or the cancel index when the dialog was closed.
     */
    public static int show(Component parent, Content content, List<Choice> choices, int cancel, int focus) {
        if (GraphicsEnvironment.isHeadless()) {
            Logger.info("{}: {}", content.title, content.what); //$NON-NLS-1$
            return cancel;
        }
        if (!SwingUtilities.isEventDispatchThread()) {
            AtomicInteger answer = new AtomicInteger(cancel);
            try {
                SwingUtilities.invokeAndWait(() -> answer.set(show(parent, content, choices, cancel, focus)));
            }
            catch (Exception e) {
                Logger.warn(e, "A dialog could not be shown: {}", content.title); //$NON-NLS-1$
            }
            return answer.get();
        }
        Window owner = parent == null ? null
                : parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, content.title, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        int[] chosen = { cancel };
        JPanel root = build(dialog, content, choices, chosen, cancel, focus);
        dialog.setContentPane(root);
        // The title is in the dialog's own header; the window's buttons go over it, as in the
        // main window.
        if (com.formdev.flatlaf.util.SystemInfo.isWindows_10_orLater
                && com.formdev.flatlaf.ui.FlatNativeWindowBorder.isSupported()) {
            dialog.getRootPane().putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT, true);
        }
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        if (presenter != null) {
            return presenter.applyAsInt(dialog);
        }
        dialog.setVisible(true);
        return chosen[0];
    }

    /** While set, where the dialogs go instead of the screen. */
    private static java.util.function.ToIntFunction<JDialog> presenter;

    /**
     * Hands every dialog, laid out, to the function instead of showing it, and takes what it
     * returns as the choice made; null shows them again. The function owns the dialog and disposes
     * of it. For the ruler, which photographs the dialogs the program really builds.
     */
    public static void setPresenter(java.util.function.ToIntFunction<JDialog> presenter) {
        Dialogs.presenter = presenter;
    }

    private static JPanel build(JDialog dialog, Content content, List<Choice> choices, int[] chosen,
            int cancel, int focus) {
        int textWidth = content.width - 90;
        int width = content.width;
        // The width is the dialog's; the height follows the content, which grows when the
        // details are opened.
        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(width, super.getPreferredSize().height);
            }
        };
        root.setBackground(Ui.surface());

        // ---- .dh: the icon box and the title ----
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(18, 20, 4, 20));
        RoundedPanel iconBox = new RoundedPanel(Tokens.R_MD,
                content.tone == Tone.Err ? Ui::errSoft : content.tone == Tone.Warn ? Ui::warnSoft : Ui::accentSoft,
                () -> null);
        iconBox.setLayout(new BorderLayout());
        iconBox.setPreferredSize(new Dimension(36, 36));
        JLabel icon = new JLabel(Ui.icon(content.icon, 18,
                content.tone == Tone.Err ? Ui.err() : content.tone == Tone.Warn ? Ui.warn() : Ui.accent()));
        icon.setHorizontalAlignment(JLabel.CENTER);
        iconBox.add(icon);
        JPanel iconHolder = new JPanel(new BorderLayout());
        iconHolder.setOpaque(false);
        iconHolder.add(iconBox, BorderLayout.NORTH);
        header.add(iconHolder, BorderLayout.WEST);
        JLabel title = new JLabel(html(content.title, textWidth - 40));
        title.setFont(Ui.weighted(Tokens.FS_TITLE, Tokens.FW_TITLE));
        title.setForeground(Ui.text());
        title.setBorder(new EmptyBorder(7, 0, 0, 0));
        JPanel titleHolder = new JPanel(new BorderLayout());
        titleHolder.setOpaque(false);
        titleHolder.add(title, BorderLayout.NORTH);
        header.add(titleHolder, BorderLayout.CENTER);
        // Room for the window's close button, which the look and feel draws over the header.
        JPanel placeholder = new JPanel();
        placeholder.setOpaque(false);
        placeholder.putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_PLACEHOLDER, "win"); //$NON-NLS-1$
        header.add(placeholder, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // ---- .db: what, more, the list, the details ----
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(4, 70, 16, 20));
        if (content.what != null) {
            body.add(paragraph(content.what, Ui.text(), textWidth));
        }
        if (content.list != null) {
            body.add(Box.createVerticalStrut(6));
            JLabel list = new JLabel(html(content.list, textWidth - 24));
            list.setFont(Ui.mono(12f, Font.PLAIN));
            list.setForeground(Ui.text());
            RoundedPanel box = new RoundedPanel(Tokens.R_SM, Ui::surface2, Ui::border);
            box.setLayout(new BorderLayout());
            box.setBorder(new EmptyBorder(8, 12, 8, 12));
            box.add(list);
            box.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(box);
            body.add(Box.createVerticalStrut(10));
        }
        if (content.more != null) {
            body.add(Box.createVerticalStrut(content.list == null ? 8 : 0));
            body.add(paragraph(content.more, Ui.text2(), textWidth));
        }
        if (content.details != null && !content.details.isEmpty()) {
            body.add(Box.createVerticalStrut(10));
            body.add(details(content.details, textWidth, dialog));
        }
        root.add(body, BorderLayout.CENTER);

        // ---- .df: utilities left, the choices right, the asked-about one last ----
        JPanel foot = new JPanel();
        foot.setBackground(Ui.surface2());
        foot.setOpaque(true);
        foot.setLayout(new BoxLayout(foot, BoxLayout.X_AXIS));
        foot.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.border()),
                new EmptyBorder(12, 20, 12, 20)));
        List<JButton> buttons = new ArrayList<>();
        for (Choice choice : choices) {
            if (choice.left) {
                JButton button = Ui.button(choice.label, choice.icon, Ui.Size.Sm, Ui.Variant.Ghost);
                button.setFocusable(true);
                button.addActionListener(e -> choice.action.run());
                foot.add(button);
                foot.add(Box.createHorizontalStrut(8));
            }
        }
        foot.add(Box.createHorizontalGlue());
        for (int i = 0; i < choices.size(); i++) {
            Choice choice = choices.get(i);
            if (choice.left) {
                buttons.add(null);
                continue;
            }
            JButton button = Ui.button(choice.label, choice.icon, Ui.Size.Sm, choice.variant);
            if (choice.movesMachine) {
                Ui.movesMachine(button);
            }
            // Tab reaches every button of a dialog, the one that moves the machine included:
            // here the focus is the answer being chosen.
            button.setFocusable(true);
            int index = i;
            button.addActionListener(e -> {
                chosen[0] = index;
                dialog.dispose();
            });
            foot.add(Box.createHorizontalStrut(8));
            foot.add(button);
            buttons.add(button);
        }
        root.add(foot, BorderLayout.SOUTH);

        // Esc is the cancel choice, or closing.
        JRootPane rootPane = dialog.getRootPane();
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "pono.cancel"); //$NON-NLS-1$
        rootPane.getActionMap().put("pono.cancel", new AbstractAction() { //$NON-NLS-1$
            @Override
            public void actionPerformed(ActionEvent e) {
                chosen[0] = cancel;
                dialog.dispose();
            }
        });
        if (focus >= 0 && focus < buttons.size() && buttons.get(focus) != null) {
            JButton start = buttons.get(focus);
            rootPane.setDefaultButton(start);
            dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowOpened(java.awt.event.WindowEvent e) {
                    start.requestFocusInWindow();
                }
            });
        }
        return root;
    }

    private static JLabel paragraph(String text, java.awt.Color colour, int width) {
        JLabel label = new JLabel(html(text, width));
        label.setFont(Ui.font(Tokens.FS_BODY));
        label.setForeground(colour);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 8, 0));
        return label;
    }

    /** The folded box of the original message and stack, with its heading as the switch. */
    private static JComponent details(String text, int width, JDialog dialog) {
        RoundedPanel box = new RoundedPanel(Tokens.R_SM, Ui::surface2, Ui::border);
        box.setLayout(new BorderLayout());
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel heading = new JLabel(Translations.getString("Dialogs.Details"), Ui.iconSm("chevright"), //$NON-NLS-1$ //$NON-NLS-2$
                JLabel.LEFT);
        heading.setFont(Ui.weighted(Tokens.FS_SMALL, Tokens.FW_SECTION));
        heading.setForeground(Ui.text2());
        heading.setIconTextGap(6);
        heading.setBorder(new EmptyBorder(7, 10, 7, 10));
        heading.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        box.add(heading, BorderLayout.NORTH);
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(false);
        area.setFont(Ui.mono(11f, Font.PLAIN));
        area.setForeground(Ui.text2());
        area.setOpaque(false);
        area.setBorder(new EmptyBorder(8, 10, 8, 10));
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.border()));
        // From the lines, not from the area's preferred size: a wrapping text area that has not
        // been laid out yet reports next to no height, and the details opened as a sliver.
        int lines = text.split("\n", -1).length; //$NON-NLS-1$
        int lineHeight = area.getFontMetrics(area.getFont()).getHeight();
        scroll.setPreferredSize(new Dimension(width, Math.min(180, lines * lineHeight + 16 + 2)));
        scroll.setVisible(false);
        box.add(scroll, BorderLayout.CENTER);
        heading.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                boolean open = !scroll.isVisible();
                scroll.setVisible(open);
                heading.setIcon(Ui.iconSm(open ? "chevdown" : "chevright")); //$NON-NLS-1$ //$NON-NLS-2$
                dialog.pack();
            }
        });
        box.putClientProperty("Pono.detailsText", area); //$NON-NLS-1$
        return box;
    }

    static String html(String text, int width) {
        String escaped = text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
                .replace(">", "&gt;").replace("\n", "<br>"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        return "<html><div style='width:" + width + "px'>" + escaped + "</div></html>"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    // ---- errors -------------------------------------------------------------------------------

    private static final ErrorMessages.Repeats REPEATS = new ErrorMessages.Repeats(5000);

    /**
     * An error: its title, its reason and advice in the user's words where the message is one
     * this program knows, the original and the stack under the details.
     * 
     * @param withContinuation Whether the operation can go on past it; the dialog then asks.
     * @return With a continuation, whether the user chose to go on.
     */
    public static boolean error(Component parent, String title, Throwable cause, String message,
            boolean withContinuation) {
        String original = message != null ? message
                : cause == null ? null : cause.getMessage() != null && !cause.getMessage().trim().isEmpty()
                        ? cause.getMessage() : cause.getClass().getSimpleName();
        ErrorMessages.Explained explained = ErrorMessages.explain(title, original);
        String key = explained.title + "\n" + original; //$NON-NLS-1$
        Logger.error(cause, "{}: {}", explained.title, original); //$NON-NLS-1$
        int repeats = REPEATS.seen(key, System.currentTimeMillis());
        if (repeats > 0 && !withContinuation) {
            // Shown moments ago: logged and counted rather than stacked on top of itself.
            return false;
        }
        StringBuilder details = new StringBuilder();
        if (original != null) {
            details.append(original);
        }
        if (cause != null) {
            java.io.StringWriter trace = new java.io.StringWriter();
            cause.printStackTrace(new java.io.PrintWriter(trace));
            details.append(details.length() > 0 ? "\n\n" : "").append(trace); //$NON-NLS-1$ //$NON-NLS-2$
        }
        int earlier = REPEATS.count(key) - 1;
        if (earlier > 0) {
            details.insert(0, Translations.getString("Dialogs.Error.Repeated") //$NON-NLS-1$
                    .replace("%d", String.valueOf(earlier)) + "\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        Content content = new Content().tone(Tone.Err, "alert").title(explained.title) //$NON-NLS-1$
                .what(explained.what).more(explained.more).details(details.toString()).width(600);
        String copy = details.toString();
        List<Choice> choices = new ArrayList<>();
        choices.add(new Choice(Translations.getString("Dialogs.CopyDetails"), Ui.iconSm("copy"), Ui.Variant.Ghost) //$NON-NLS-1$ //$NON-NLS-2$
                .utility(() -> java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(explained.title + "\n" + copy), null))); //$NON-NLS-1$
        choices.add(new Choice(Translations.getString("Dialogs.OpenLog"), Ui.iconSm("log"), Ui.Variant.Ghost) //$NON-NLS-1$ //$NON-NLS-2$
                .utility(Dialogs::openLog));
        if (withContinuation) {
            choices.add(Choice.cancel());
            choices.add(Choice.primary(Translations.getString("Dialogs.Continue"))); //$NON-NLS-1$
            return show(parent, content, choices, 2, 3) == 3;
        }
        choices.add(Choice.primary(Translations.getString("Dialogs.Close"))); //$NON-NLS-1$
        show(parent, content, choices, 2, 2);
        return false;
    }

    /** The log page, where the error and what led up to it are. */
    private static void openLog() {
        org.openpnp.gui.MainFrame frame = org.openpnp.gui.MainFrame.get();
        if (frame != null) {
            frame.showLog();
        }
    }

    /** A message that is not an error: something done, or something to know. */
    public static void info(Component parent, String title, String message) {
        Content content = new Content().tone(Tone.Info, "info").title(title).what(message); //$NON-NLS-1$
        show(parent, content, Arrays.asList(Choice.primary(Translations.getString("Dialogs.Close"))), 0, 0); //$NON-NLS-1$
    }

    // ---- questions ----------------------------------------------------------------------------

    /**
     * A question with buttons named by what they do.
     * 
     * @param actions The actions, left to right after Cancel; the last is the one asked about.
     * @return The index of the action chosen, -1 for Cancel.
     */
    public static int ask(Component parent, Tone tone, String icon, String title, String what,
            String more, Choice... actions) {
        List<Choice> choices = new ArrayList<>();
        choices.add(Choice.cancel());
        choices.addAll(Arrays.asList(actions));
        Content content = new Content().tone(tone, icon).title(title).what(what).more(more);
        // Enter must not do the dangerous thing: the focus starts on Cancel before a red button.
        Ui.Variant asked = choices.get(choices.size() - 1).variant;
        boolean dangerous = asked == Ui.Variant.SolidDanger || asked == Ui.Variant.Danger;
        int chosen = show(parent, content, choices, 0, dangerous ? 0 : choices.size() - 1);
        return chosen <= 0 ? -1 : chosen - 1;
    }

    /** The most names a delete confirmation lists before it says how many more. */
    private static final int LISTED = 10;

    /**
     * Deleting things: the title and the red button say what and how many, the list names them,
     * and the foot says where they can be found again. Cancel is where the focus starts.
     * 
     * @param kind The key of the kind of thing, as {@code Dialogs.Kind.Feeders}: its name, plural
     *             in English.
     * @return Whether the user chose to delete.
     */
    public static boolean confirmDelete(Component parent, String kind, List<String> names) {
        String what = Translations.getString(kind);
        int n = names.size();
        String title = n == 1
                ? String.format(Translations.getString("Dialogs.Delete.TitleOne"), what, names.get(0)) //$NON-NLS-1$
                : String.format(Translations.getString("Dialogs.Delete.Title"), n, what); //$NON-NLS-1$
        String action = n == 1
                ? String.format(Translations.getString("Dialogs.Delete.ButtonOne"), what) //$NON-NLS-1$
                : String.format(Translations.getString("Dialogs.Delete.Button"), n, what); //$NON-NLS-1$
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < Math.min(n, LISTED); i++) {
            list.append(i > 0 ? "\n" : "").append(names.get(i)); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (n > LISTED) {
            list.append('\n').append(String.format(Translations.getString("Dialogs.Delete.AndMore"), n - LISTED)); //$NON-NLS-1$
        }
        return confirmDanger(parent, title,
                n == 1 ? null : String.format(Translations.getString("Dialogs.Delete.What"), n, what), //$NON-NLS-1$
                n == 1 ? null : list.toString(), Translations.getString("Dialogs.Delete.More"), action); //$NON-NLS-1$
    }

    /**
     * Deleting one machine element whose question says more than its name - what still refers to
     * it - under its own title.
     * 
     * @param kind The key of the kind of element, as {@code Dialogs.Kind.Axes}.
     */
    public static boolean confirmDeleteElement(Component parent, String kind, String title, String message) {
        String what = Translations.getString(kind);
        return confirmDanger(parent, title, prepare(message), null, Translations.getString("Dialogs.Delete.More"), //$NON-NLS-1$
                String.format(Translations.getString("Dialogs.Delete.ButtonOne"), what)); //$NON-NLS-1$
    }

    /** A message some callers wrote as HTML, as the plain text the dialog lays out itself. */
    static String prepare(String message) {
        if (message == null) {
            return null;
        }
        return message.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&").trim(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
    }

    /**
     * A dangerous action: a red button naming what and how many, Cancel where the focus starts.
     * 
     * @return Whether the user chose to go ahead.
     */
    public static boolean confirmDanger(Component parent, String title, String what, String list,
            String more, String action) {
        Content content = new Content().tone(Tone.Err, "trash").title(title).what(what).list(list).more(more); //$NON-NLS-1$
        List<Choice> choices = Arrays.asList(Choice.cancel(), Choice.danger(action));
        return show(parent, content, choices, 0, 0) == 1;
    }
}
