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

package org.openpnp.model;

import org.pmw.tinylog.Logger;

/**
 * How code below the GUI asks the user a question or tells them something went wrong.
 * <p>
 * The model used to call JOptionPane directly, which meant it could only run under a GUI: with no
 * window to parent the dialog to, a script or a test either blocks on a dialog nobody will see or
 * throws HeadlessException. Configuration holds one of these instead, the GUI installs a Swing
 * implementation at start up, and everything else gets the non-interactive default.
 * 
 * @see Configuration#setUserInteraction(UserInteraction)
 */
public interface UserInteraction {
    /**
     * Asks the user to confirm something they would otherwise not be aware of.
     * 
     * @param title
     * @param message
     * @return true if the user agreed.
     */
    boolean confirm(String title, String message);

    /**
     * Tells the user about a failure that has already been handled, i.e. one the caller is not
     * going to throw.
     * 
     * @param title
     * @param message
     */
    void reportError(String title, String message);

    /** The three answers to "save your changes?". */
    enum SaveChoice {
        Save,
        Discard,
        /** Neither: whatever asked should not go ahead. */
        Cancel
    }

    /**
     * Asks whether to save changes that would otherwise be lost. Cancel is its own answer: it
     * used to be folded into "don't save", which threw the changes away on the one button a user
     * presses to get out of a question.
     */
    default SaveChoice askToSave(String title, String message) {
        return confirm(title, message) ? SaveChoice.Save : SaveChoice.Discard;
    }

    /**
     * The implementation used when nobody is watching. Questions are declined rather than answered
     * on the user's behalf, which is the branch that changes nothing on disk, and both the question
     * and the answer are logged so that an unattended run leaves a trace of what it decided.
     * 
     * @return
     */
    static UserInteraction nonInteractive() {
        return new UserInteraction() {
            @Override
            public boolean confirm(String title, String message) {
                Logger.info("Declining \"{}\" ({}) because there is no user to ask.", title,
                        message.replace('\n', ' '));
                return false;
            }

            @Override
            public void reportError(String title, String message) {
                Logger.error("{}: {}", title, message);
            }
        };
    }
}
