package org.openpnp.gui.support;

import org.openpnp.gui.shell.Tokens;
import org.openpnp.gui.shell.Ui;
import org.pmw.tinylog.LogEntry;

import javax.swing.*;
import java.awt.*;

/**
 * One line of the log in the stylesheet's .log colours: debug and trace muted, information in the
 * text colour, warnings and errors in the status colours, an error on its soft fill. The colours
 * follow the theme; the debug lines used to be pure black, unreadable on the dark one, and the font
 * follows the user's font size instead of a fixed 13 point Monospaced.
 */
public class LogEntryListCellRenderer extends JTextField implements ListCellRenderer<LogEntry> {
    @Override
    public Component getListCellRendererComponent(JList<? extends LogEntry> list, LogEntry logEntry, int index, boolean isSelected, boolean cellHasFocus) {

        if (logEntry == null) {
            return this;
        }

        this.setText(logEntry.getRenderedLogEntry());
        this.setFont(Ui.mono(Tokens.FS_AUX, Font.PLAIN));
        this.setBorder(null);

        if (isSelected) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
        } else {
            switch(logEntry.getLevel()) {
                case ERROR:
                    setBackground(Ui.errSoft());
                    break;
                default:
                    setBackground(list.getBackground());
            }
            switch(logEntry.getLevel()) {
                case TRACE:
                case DEBUG:
                    setForeground(Ui.muted());
                    break;
                case INFO:
                    setForeground(Ui.text());
                    break;
                case WARNING:
                    setForeground(Ui.warn());
                    break;
                case ERROR:
                    setForeground(Ui.err());
                    break;
                default:
                    setForeground(list.getForeground());
                    break;
            }
        }

        return this;
    }
}
