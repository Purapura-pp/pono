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

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.KeyStroke;

/**
 * Every window-wide hotkey, in one place, with what it does.
 * <p>
 * They used to be spread over the frame's constructor, where two of them had quietly taken the
 * keys of standard commands: Safe Z sat on Ctrl+Shift+Z, which is Redo in the Edit menu, and a job
 * step sat on Ctrl+Shift+S, which a hand reaching for Save As presses. A hotkey here moves the
 * machine, so the list is checked against the editing commands it must never shadow.
 */
public final class Hotkeys {
    private static final int CTRL = InputEvent.CTRL_DOWN_MASK;
    private static final int CTRL_SHIFT = InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK;

    /**
     * Stops the machine: aborts a running job and disables the machine. Handled before anything
     * else, whichever window has the focus and whatever is being typed.
     */
    public static final KeyStroke STOP_MACHINE = KeyStroke.getKeyStroke(KeyEvent.VK_X, CTRL_SHIFT);

    public static final KeyStroke JOB_START_PAUSE = KeyStroke.getKeyStroke(KeyEvent.VK_R, CTRL_SHIFT);
    public static final KeyStroke JOB_STEP = KeyStroke.getKeyStroke(KeyEvent.VK_N, CTRL_SHIFT);
    public static final KeyStroke JOB_ABORT = KeyStroke.getKeyStroke(KeyEvent.VK_A, CTRL_SHIFT);

    /** The keys production mode keeps: those that start, pause, step and abort the job. */
    public static boolean runsTheJob(KeyStroke stroke) {
        return JOB_START_PAUSE.equals(stroke) || JOB_STEP.equals(stroke) || JOB_ABORT.equals(stroke);
    }

    public static final KeyStroke PARK_XY = KeyStroke.getKeyStroke(KeyEvent.VK_P, CTRL_SHIFT);
    public static final KeyStroke PARK_Z = KeyStroke.getKeyStroke(KeyEvent.VK_L, CTRL_SHIFT);
    public static final KeyStroke SAFE_Z = KeyStroke.getKeyStroke(KeyEvent.VK_U, CTRL_SHIFT);
    public static final KeyStroke DISCARD = KeyStroke.getKeyStroke(KeyEvent.VK_D, CTRL_SHIFT);

    public static final KeyStroke TOGGLE_JOG_CARD = KeyStroke.getKeyStroke(KeyEvent.VK_J, CTRL_SHIFT);
    public static final KeyStroke COMMAND_PALETTE = KeyStroke.getKeyStroke(KeyEvent.VK_K, CTRL);

    /** The jog keys, taken with Ctrl and with Ctrl+Shift; Shift picks the finer increments. */
    public static final int[] JOG_MODIFIERS = { CTRL, CTRL_SHIFT };
    public static final int JOG_Y_PLUS = KeyEvent.VK_UP;
    public static final int JOG_Y_MINUS = KeyEvent.VK_DOWN;
    public static final int JOG_X_MINUS = KeyEvent.VK_LEFT;
    public static final int JOG_X_PLUS = KeyEvent.VK_RIGHT;
    public static final int JOG_Z_PLUS = KeyEvent.VK_QUOTE;
    public static final int JOG_Z_MINUS = KeyEvent.VK_SLASH;
    public static final int JOG_C_PLUS = KeyEvent.VK_COMMA;
    public static final int JOG_C_MINUS = KeyEvent.VK_PERIOD;
    public static final int INCREMENT_LOWER = KeyEvent.VK_MINUS;
    public static final int INCREMENT_RAISE = KeyEvent.VK_EQUALS;
    public static final int HOME = KeyEvent.VK_H;

    /** Ctrl+Shift+F1 to F5 pick the five jog increments directly. */
    public static final int[] INCREMENT_KEYS = { KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F3,
            KeyEvent.VK_F4, KeyEvent.VK_F5 };

    /**
     * The standard editing and file commands. No hotkey may take one of these: the global
     * dispatcher sees a key before the menu or the focused component does, so a hotkey on one of
     * them would move the machine instead of undoing, saving or copying.
     */
    public static final List<KeyStroke> RESERVED = Collections.unmodifiableList(List.of(
            KeyStroke.getKeyStroke(KeyEvent.VK_Z, CTRL),
            KeyStroke.getKeyStroke(KeyEvent.VK_Z, CTRL_SHIFT),
            KeyStroke.getKeyStroke(KeyEvent.VK_Y, CTRL),
            KeyStroke.getKeyStroke(KeyEvent.VK_S, CTRL),
            KeyStroke.getKeyStroke(KeyEvent.VK_S, CTRL_SHIFT),
            KeyStroke.getKeyStroke(KeyEvent.VK_O, CTRL),
            KeyStroke.getKeyStroke(KeyEvent.VK_N, CTRL),
            KeyStroke.getKeyStroke(KeyEvent.VK_C, CTRL),
            KeyStroke.getKeyStroke(KeyEvent.VK_V, CTRL),
            KeyStroke.getKeyStroke(KeyEvent.VK_X, CTRL),
            KeyStroke.getKeyStroke(KeyEvent.VK_A, CTRL),
            KeyStroke.getKeyStroke(KeyEvent.VK_F, CTRL),
            KeyStroke.getKeyStroke(KeyEvent.VK_E, CTRL)));

    /** One hotkey and the translation key of what it does, for the shortcut list. */
    public static final class Entry {
        public final KeyStroke keyStroke;
        public final String descriptionKey;

        Entry(KeyStroke keyStroke, String descriptionKey) {
            this.keyStroke = keyStroke;
            this.descriptionKey = descriptionKey;
        }
    }

    private Hotkeys() {
    }

    /** Every hotkey the window binds, in the order the shortcut list shows them. */
    public static List<Entry> all() {
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(STOP_MACHINE, "Hotkeys.StopMachine")); //$NON-NLS-1$
        entries.add(new Entry(JOB_START_PAUSE, "Hotkeys.JobStartPause")); //$NON-NLS-1$
        entries.add(new Entry(JOB_STEP, "Hotkeys.JobStep")); //$NON-NLS-1$
        entries.add(new Entry(JOB_ABORT, "Hotkeys.JobAbort")); //$NON-NLS-1$
        for (int modifiers : JOG_MODIFIERS) {
            String fine = modifiers == CTRL ? "" : ".Fine"; //$NON-NLS-1$ //$NON-NLS-2$
            entries.add(new Entry(KeyStroke.getKeyStroke(JOG_Y_PLUS, modifiers), "Hotkeys.JogYPlus" + fine)); //$NON-NLS-1$
            entries.add(new Entry(KeyStroke.getKeyStroke(JOG_Y_MINUS, modifiers), "Hotkeys.JogYMinus" + fine)); //$NON-NLS-1$
            entries.add(new Entry(KeyStroke.getKeyStroke(JOG_X_MINUS, modifiers), "Hotkeys.JogXMinus" + fine)); //$NON-NLS-1$
            entries.add(new Entry(KeyStroke.getKeyStroke(JOG_X_PLUS, modifiers), "Hotkeys.JogXPlus" + fine)); //$NON-NLS-1$
            entries.add(new Entry(KeyStroke.getKeyStroke(JOG_Z_PLUS, modifiers), "Hotkeys.JogZPlus" + fine)); //$NON-NLS-1$
            entries.add(new Entry(KeyStroke.getKeyStroke(JOG_Z_MINUS, modifiers), "Hotkeys.JogZMinus" + fine)); //$NON-NLS-1$
            entries.add(new Entry(KeyStroke.getKeyStroke(JOG_C_PLUS, modifiers), "Hotkeys.JogCPlus" + fine)); //$NON-NLS-1$
            entries.add(new Entry(KeyStroke.getKeyStroke(JOG_C_MINUS, modifiers), "Hotkeys.JogCMinus" + fine)); //$NON-NLS-1$
            entries.add(new Entry(KeyStroke.getKeyStroke(INCREMENT_LOWER, modifiers), "Hotkeys.IncrementLower")); //$NON-NLS-1$
            entries.add(new Entry(KeyStroke.getKeyStroke(INCREMENT_RAISE, modifiers), "Hotkeys.IncrementRaise")); //$NON-NLS-1$
            entries.add(new Entry(KeyStroke.getKeyStroke(HOME, modifiers), "Hotkeys.Home")); //$NON-NLS-1$
        }
        entries.add(new Entry(PARK_XY, "Hotkeys.ParkXy")); //$NON-NLS-1$
        entries.add(new Entry(PARK_Z, "Hotkeys.ParkZ")); //$NON-NLS-1$
        entries.add(new Entry(SAFE_Z, "Hotkeys.SafeZ")); //$NON-NLS-1$
        entries.add(new Entry(DISCARD, "Hotkeys.Discard")); //$NON-NLS-1$
        for (int i = 0; i < INCREMENT_KEYS.length; i++) {
            entries.add(new Entry(KeyStroke.getKeyStroke(INCREMENT_KEYS[i], CTRL_SHIFT),
                    "Hotkeys.Increment" + (i + 1))); //$NON-NLS-1$
        }
        entries.add(new Entry(TOGGLE_JOG_CARD, "Hotkeys.ToggleJogCard")); //$NON-NLS-1$
        entries.add(new Entry(COMMAND_PALETTE, "Hotkeys.CommandPalette")); //$NON-NLS-1$
        // Ctrl+1 to Ctrl+9: the rail's pages in order, handled by the window itself.
        entries.add(new Entry(KeyStroke.getKeyStroke(KeyEvent.VK_1, CTRL), "Hotkeys.Pages")); //$NON-NLS-1$
        entries.add(new Entry(KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, 0), "Hotkeys.Filter")); //$NON-NLS-1$
        entries.add(new Entry(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "Hotkeys.TableEdit")); //$NON-NLS-1$
        entries.add(new Entry(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "Hotkeys.TableDelete")); //$NON-NLS-1$
        return entries;
    }

    /** "Ctrl+Shift+X", the way a keycap or a tooltip writes it. */
    public static String describe(KeyStroke keyStroke) {
        StringBuilder text = new StringBuilder();
        int modifiers = keyStroke.getModifiers();
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
            text.append("Ctrl+"); //$NON-NLS-1$
        }
        if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) {
            text.append("Alt+"); //$NON-NLS-1$
        }
        if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
            text.append("Shift+"); //$NON-NLS-1$
        }
        if ((modifiers & InputEvent.META_DOWN_MASK) != 0) {
            text.append("Meta+"); //$NON-NLS-1$
        }
        text.append(keyName(keyStroke.getKeyCode()));
        return text.toString();
    }

    private static String keyName(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_UP:
                return "\u2191"; //$NON-NLS-1$
            case KeyEvent.VK_DOWN:
                return "\u2193"; //$NON-NLS-1$
            case KeyEvent.VK_LEFT:
                return "\u2190"; //$NON-NLS-1$
            case KeyEvent.VK_RIGHT:
                return "\u2192"; //$NON-NLS-1$
            case KeyEvent.VK_QUOTE:
                return "'"; //$NON-NLS-1$
            case KeyEvent.VK_SLASH:
                return "/"; //$NON-NLS-1$
            case KeyEvent.VK_COMMA:
                return ","; //$NON-NLS-1$
            case KeyEvent.VK_PERIOD:
                return "."; //$NON-NLS-1$
            case KeyEvent.VK_MINUS:
                return "-"; //$NON-NLS-1$
            case KeyEvent.VK_EQUALS:
                return "="; //$NON-NLS-1$
            default:
                return KeyEvent.getKeyText(keyCode);
        }
    }
}
