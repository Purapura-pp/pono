package org.openpnp.gui.support;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.text.SimpleDateFormat;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.border.EmptyBorder;

import org.openpnp.Translations;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.Tokens;
import org.openpnp.gui.shell.Ui;
import org.pmw.tinylog.Level;
import org.pmw.tinylog.LogEntry;

/**
 * One line of the log in the stylesheet's {@code .log}: the time, the level as a capsule in its
 * status colour, the class that logged it in grey, and the message; an error on its soft fill.
 * Every part is in the user's font size - the time and the class in the monospaced figures. It was
 * the whole rendered line in one colour per level.
 */
@SuppressWarnings("serial")
public class LogEntryListCellRenderer extends JPanel implements ListCellRenderer<LogEntry> {
    private final JLabel time = new JLabel();
    private final Chip level = new Chip("", Chip.Tone.Neutral, Chip.Shape.Status).withLed(false); //$NON-NLS-1$
    private final JLabel source = new JLabel();
    private final JLabel message = new JLabel();
    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss.SSS"); //$NON-NLS-1$

    /** The class column's width, as the mockup's 190 pixels. */
    private static final int SOURCE_WIDTH = 190;

    public LogEntryListCellRenderer() {
        super(null);
        add(time);
        add(level);
        add(source);
        add(message);
        setBorder(new EmptyBorder(0, 12, 0, 12));
    }

    @Override
    public void doLayout() {
        int h = getHeight();
        int x = getInsets().left;
        int timeWidth = time.getPreferredSize().width;
        time.setBounds(x, 0, timeWidth, h);
        x += timeWidth + 12;
        Dimension chip = level.getPreferredSize();
        int levelWidth = Math.max(chip.width, 44);
        level.setBounds(x, (h - chip.height) / 2, chip.width, chip.height);
        x += levelWidth + 12;
        source.setBounds(x, 0, SOURCE_WIDTH, h);
        x += SOURCE_WIDTH + 12;
        message.setBounds(x, 0, Math.max(0, getWidth() - getInsets().right - x), h);
    }

    @Override
    public Dimension getPreferredSize() {
        int height = Math.max(message.getPreferredSize().height, level.getPreferredSize().height) + 8;
        return new Dimension(getInsets().left + time.getPreferredSize().width + 12 + 44 + 12 + SOURCE_WIDTH + 12
                + message.getPreferredSize().width + getInsets().right, height);
    }

    /** The height of one line in the current font size, for the list's fixed row height. */
    public int rowHeight() {
        message.setFont(Ui.font(Tokens.FS_AUX));
        level.setText(Translations.getString("LogPanel.Level.INFO")); //$NON-NLS-1$
        return Math.max(message.getPreferredSize().height, level.getPreferredSize().height) + 8;
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends LogEntry> list, LogEntry logEntry, int index,
            boolean isSelected, boolean cellHasFocus) {
        if (logEntry == null) {
            return this;
        }
        Font mono = Ui.mono(Tokens.FS_AUX, Font.PLAIN);
        time.setFont(mono);
        source.setFont(mono);
        message.setFont(Ui.font(Tokens.FS_AUX));
        time.setText(logEntry.getDate() == null ? "" : clock.format(logEntry.getDate())); //$NON-NLS-1$
        Level entryLevel = logEntry.getLevel() == null ? Level.INFO : logEntry.getLevel();
        level.setText(Translations.getString("LogPanel.Level." + entryLevel.name())); //$NON-NLS-1$
        level.setTone(tone(entryLevel));
        String className = logEntry.getClassName();
        source.setText(className == null ? "" : className.substring(className.lastIndexOf('.') + 1)); //$NON-NLS-1$
        String text = logEntry.getMessage() == null ? "" : logEntry.getMessage(); //$NON-NLS-1$
        if (logEntry.getException() != null) {
            text = text.isEmpty() ? logEntry.getException().toString() : text + " \u2014 " + logEntry.getException(); //$NON-NLS-1$
        }
        if (text.isEmpty() && logEntry.getRenderedLogEntry() != null) {
            text = logEntry.getRenderedLogEntry().trim();
        }
        message.setText(text.replace('\n', ' '));
        setToolTipText(logEntry.getRenderedLogEntry());

        if (isSelected) {
            setBackground(list.getSelectionBackground());
        }
        else {
            setBackground(entryLevel == Level.ERROR ? Ui.errSoft() : list.getBackground());
        }
        time.setForeground(isSelected ? Ui.text2() : Ui.muted());
        source.setForeground(isSelected ? Ui.text2() : Ui.muted());
        switch (entryLevel) {
            case TRACE:
            case DEBUG:
                message.setForeground(isSelected ? Ui.text2() : Ui.muted());
                break;
            case WARNING:
                message.setForeground(Ui.warnText());
                break;
            case ERROR:
                message.setForeground(Ui.errText());
                break;
            default:
                message.setForeground(Ui.text());
                break;
        }
        setOpaque(true);
        return this;
    }

    private static Chip.Tone tone(Level level) {
        switch (level) {
            case WARNING:
                return Chip.Tone.Warn;
            case ERROR:
                return Chip.Tone.Err;
            case INFO:
                return Chip.Tone.Accent;
            default:
                return Chip.Tone.Neutral;
        }
    }
}
