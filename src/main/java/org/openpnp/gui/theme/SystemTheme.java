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

package org.openpnp.gui.theme;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.pmw.tinylog.Logger;

/**
 * Reads the operating system's light/dark preference.
 * <p>
 * FlatLaf 3.7.2 exposes no API for this, and the project depends on jna but not
 * jna-platform, so the Windows registry read goes through {@code reg.exe} rather than
 * {@code Advapi32Util}. Every probe is a short-lived child process; nothing is cached,
 * because the value is only read when a theme is applied.
 */
public final class SystemTheme {
    /** A probe that hangs must not hold up startup. */
    private static final long PROBE_TIMEOUT_MS = 2000;
    /**
     * The answer is read once per theme change and once per rebuild of the theme list, and the
     * list is rebuilt on the event thread, so a short memo keeps a burst of those from spawning a
     * process each. Short enough that flipping the OS appearance is picked up by the next apply.
     */
    private static final long CACHE_TTL_MS = 2000;

    private static boolean cachedDark;
    private static long cachedAt;

    private SystemTheme() {
    }

    /**
     * @return true when the OS asks applications to use a dark appearance. Anything
     *         unreadable — unsupported OS, missing registry value, absent
     *         {@code gsettings}, timeout — reports light.
     */
    public static synchronized boolean isDark() {
        long now = System.currentTimeMillis();
        if (cachedAt != 0 && now - cachedAt < CACHE_TTL_MS) {
            return cachedDark;
        }
        cachedDark = probe();
        cachedAt = now;
        return cachedDark;
    }

    private static boolean probe() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                return isWindowsDark();
            }
            if (os.contains("mac") || os.contains("darwin")) {
                return isMacDark();
            }
            return isLinuxDark();
        }
        catch (Exception e) {
            Logger.debug(e, "Could not read the system theme, assuming light.");
            return false;
        }
    }

    /**
     * AppsUseLightTheme is 1 for light and 0 for dark, and is absent on Windows builds
     * older than 1809 — which is the same as "no dark preference".
     */
    private static boolean isWindowsDark() throws Exception {
        String output = run("reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v", "AppsUseLightTheme");
        for (String line : output.split("\n")) {
            if (!line.contains("AppsUseLightTheme")) {
                continue;
            }
            String[] fields = line.trim().split("\\s+");
            return Integer.decode(fields[fields.length - 1]) == 0;
        }
        return false;
    }

    /**
     * The AppleInterfaceStyle key only exists while dark mode is on; {@code defaults}
     * exits non-zero otherwise, which {@link #run} already turns into an empty string.
     */
    private static boolean isMacDark() throws Exception {
        return run("defaults", "read", "-g", "AppleInterfaceStyle")
                .toLowerCase(Locale.ROOT).contains("dark");
    }

    /**
     * GNOME 42 and later answer color-scheme with 'prefer-dark', 'default' or
     * 'prefer-light'. Older desktops only carry the theme name, where "dark" in the
     * name is the sole available signal.
     */
    private static boolean isLinuxDark() throws Exception {
        String scheme = run("gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
        if (!scheme.isEmpty()) {
            return scheme.toLowerCase(Locale.ROOT).contains("prefer-dark");
        }
        return run("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme")
                .toLowerCase(Locale.ROOT).contains("dark");
    }

    /**
     * @return the command's stdout, or an empty string if it failed, timed out or
     *         could not be started at all.
     */
    private static String run(String... command) throws Exception {
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        }
        catch (Exception e) {
            // The tool is simply not installed on this machine.
            Logger.trace(e, "Could not run {} while probing the system theme.", command[0]);
            return "";
        }
        try {
            // Waiting before reading is what makes the timeout real: reading to end of stream
            // first would block indefinitely on a probe that never closes its output, and the
            // timeout below would never be reached. Safe for these three commands because each
            // writes a few hundred bytes, well inside the pipe buffer, so nothing is lost by
            // letting them finish before it is read.
            if (!process.waitFor(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    || process.exitValue() != 0) {
                return "";
            }
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            return output.toString();
        }
        finally {
            // A no-op once the probe has exited, and the safety net if it has not.
            process.destroyForcibly();
        }
    }
}
