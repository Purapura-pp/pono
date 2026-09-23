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

package org.openpnp.gui.support;

import java.awt.Component;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.shell.Dialogs;

/**
 * The program's message boxes, which are {@link Dialogs} now: an error has a title in the user's
 * language, a reason and advice where the message is a known one, and the original with its stack
 * under "Details"; the same error is not stacked on top of itself.
 */
public class MessageBoxes {

    /**
     * A message some callers wrote as HTML, as plain text: the dialog lays its own paragraphs out.
     * A message that is not HTML is taken as it is, entities and all.
     */
    static String prepareMessage(String message) {
        if (message == null) {
            return ""; //$NON-NLS-1$
        }
        if (message.contains("<html") || message.contains("<br")) { //$NON-NLS-1$ //$NON-NLS-2$
            return Dialogs.plainText(message);
        }
        return message.replace("\r", "").trim(); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public static boolean errorBox(Component parent, String title, Throwable cause, boolean withContinuation) {
        String message = cause == null || cause.getMessage() == null ? null : prepareMessage(cause.getMessage());
        return Dialogs.error(parent, title, cause, message, withContinuation);
    }

    public static void errorBox(Component parent, String title, Throwable cause) {
        errorBox(parent, title, cause, false);
    }

    public static void errorBox(Component parent, String title, String message) {
        Dialogs.error(parent, title, null, prepareMessage(message), false);
    }

    /** An error that can be tried again: "Try again" or "Cancel". */
    public static boolean errorBoxWithRetry(Component parent, String title, String message) {
        return Dialogs.ask(parent, Dialogs.Tone.Err, "alert", title, prepareMessage(message), null, //$NON-NLS-1$
                Dialogs.Choice.primary(Translations.getString("Dialogs.Retry"))) == 0; //$NON-NLS-1$
    }

    public static void infoBox(String title, String message) {
        Dialogs.info(MainFrame.get(), title, prepareMessage(message));
    }

    public static void notYetImplemented(Component parent) {
        errorBox(parent, Translations.getString("Dialogs.NotYetImplemented.Title"), //$NON-NLS-1$
                Translations.getString("Dialogs.NotYetImplemented.Message")); //$NON-NLS-1$
    }
}
