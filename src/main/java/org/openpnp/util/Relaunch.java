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

package org.openpnp.util;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.openpnp.Main;
import org.pmw.tinylog.Logger;

/**
 * Starts the program again as this one was started - the same Java, its options, the class path
 * and the folder it runs in - for when this one is about to exit onto a configuration it cannot
 * load in place.
 */
public final class Relaunch {
    private Relaunch() {
    }

    public static List<String> command() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        File bin = new File(System.getProperty("java.home"), "bin"); //$NON-NLS-1$ //$NON-NLS-2$
        File javaw = new File(bin, "javaw.exe"); //$NON-NLS-1$
        File java = new File(bin, windows ? "java.exe" : "java"); //$NON-NLS-1$ //$NON-NLS-2$
        List<String> command = new ArrayList<>();
        // javaw on Windows: the new window without a console of its own.
        command.add((windows && javaw.isFile() ? javaw : java).getAbsolutePath());
        for (String option : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            // A debugger's agent would find its port taken by the program it was attached to.
            if (!option.startsWith("-agentlib:jdwp") && !option.startsWith("-Xrunjdwp")) { //$NON-NLS-1$ //$NON-NLS-2$
                command.add(option);
            }
        }
        command.add("-cp"); //$NON-NLS-1$
        command.add(System.getProperty("java.class.path")); //$NON-NLS-1$
        command.add(Main.class.getName());
        return command;
    }

    public static void start() throws IOException {
        List<String> command = command();
        Logger.info("Starting again: {}", String.join(" ", command)); //$NON-NLS-1$ //$NON-NLS-2$
        new ProcessBuilder(command).directory(new File(System.getProperty("user.dir"))).start(); //$NON-NLS-1$
    }
}
