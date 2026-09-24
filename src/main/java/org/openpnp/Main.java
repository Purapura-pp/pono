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

package org.openpnp;

import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.swing.ToolTipManager;
import javax.swing.UIManager;

import org.openpnp.gui.MainFrame;
import org.openpnp.gui.components.ThemeDialog;
import org.openpnp.gui.components.ThemeInfo;
import org.openpnp.gui.components.ThemeSettingsPanel;
import org.openpnp.gui.theme.PonoThemes;
import org.openpnp.logging.ConsoleWriter;
import org.openpnp.logging.SystemLogger;
import org.openpnp.model.Configuration;
import org.pmw.tinylog.Configurator;
import org.pmw.tinylog.Level;
import org.pmw.tinylog.Logger;
import org.pmw.tinylog.writers.RollingFileWriter;
import org.apache.commons.io.FileUtils;

import com.formdev.flatlaf.FlatLaf;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

/**
 * Start with -Xdock:name=OpenPnP on Mac to make it prettier.
 * 
 * @author jason
 *
 */
public class Main {
    /** The program's name, the same in every language. */
    public static final String NAME = "Pono"; //$NON-NLS-1$

    public static String getVersion() {
        return getVersionString()+"_"+getBuildString();
    }

    public static String getVersionString() {
        String version = readVersionFile(new File("VERSION.txt"));
        if (version == null) {
            // Beside the jar as well as in the working directory: a packaged build is launched
            // from wherever the user happened to be, and an installed one from a menu entry with
            // no working directory worth speaking of. Both used to report no version at all.
            version = readVersionFile(versionFileBesideTheJar());
        }
        if (version == null) {
            // VERSION.txt is only present in packaged builds; a build run from the source tree
            // has no version of its own and says so.
            version = "n/a";
        }
        return version.strip();
    }

    private static String readVersionFile(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            return FileUtils.readFileToString(file);
        }
        catch (Exception e) {
            Logger.warn(e, "Could not read the version from {}.", file);
            return null;
        }
    }

    /**
     * @return VERSION.txt in the directory holding the jar, or in the directory above it, which is
     *         where a packaged build keeps it - or null if the jar's own location is unknowable.
     */
    private static File versionFileBesideTheJar() {
        try {
            File jar = new File(
                    Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File directory = jar.getParentFile();
            if (directory == null) {
                return null;
            }
            File beside = new File(directory, "VERSION.txt");
            if (beside.isFile() || directory.getParentFile() == null) {
                return beside;
            }
            return new File(directory.getParentFile(), "VERSION.txt");
        }
        catch (Exception e) {
            Logger.warn(e, "Could not work out where this build was installed.");
            return null;
        }
    }

    public static String getBuildString() {
        String version = Main.class.getPackage().getImplementationVersion();
        if (version == null) {
            version = "INTERNAL BUILD";
        }
        // A build that could not read its revision leaves Maven's placeholder in the manifest.
        return version.replaceAll("\\.?\\$\\{[^}]*\\}", ""); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public static String getSourceUri() {
        String version = Main.class.getPackage().getImplementationVersion();
        if (version == null) {
            // Select the test branch, as this is likely a developer running OpenPnP.
            version = "test"; 
        }
        else {
            // Take the hash.
            version = version.substring(version.indexOf(".")+1);
        }
        // Pono's commits only exist in Pono's repository; the upstream one would 404 on every hash.
        return "https://github.com/Purapura-pp/pono/blob/"+version+"/";
    }

    private static void configureLogging(File configurationDirectory) {
        File logDirectory = new File(configurationDirectory, "log");
        File logFile = new File(logDirectory, "OpenPnP.log");
        Configurator
            .currentConfig()
            .writer(new RollingFileWriter(logFile.getAbsolutePath(), 100))
            .addWriter(new ConsoleWriter(System.out, System.err))
            .activate();
        Configurator.currentConfig()
            .formatPattern("{date:yyyy-MM-dd HH:mm:ss.SSS} {class_name} {level}: {message}")
            .activate();

        // Redirect the stdout and stderr to the LogPanel
        SystemLogger out = new SystemLogger(System.out, Level.INFO);
        SystemLogger err = new SystemLogger(System.err, Level.ERROR);
        System.setOut(out);
        System.setErr(err);
    }
    
    private static void monkeyPatchBeansBinding() {
        // This hack fixes a bug in BeansBinding that will never be released due to to the library
        // being abandoned. The bug is that in BeansBinding.bind, it chooses to call an uncached
        // introspection method rather than a cached one. This causes each binding to take upwards
        // of 50ms on my machine. On a form with many bindings this can cause a huge load time
        // when loading wizards. This was most apparent on Feeders.
        // Note that the bug was fixed in Subversion in revision 629:
        // https://java.net/projects/beansbinding/sources/svn/revision/629
        // But it is unlikely this will ever be released to Maven.
        // This hack was found at http://blog.marcnuri.com/beansbinding-performance-issue-37/
        try {
            ClassPool cp = ClassPool.getDefault();
            CtClass cc = cp.get("org.jdesktop.beansbinding.ELProperty");
            CtMethod m = cc.getDeclaredMethod("getBeanInfo");
            m.setBody("{" +
            // "assert $1 != null;" +
                    "try {" + "return java.beans.Introspector.getBeanInfo($1.getClass());"
                    + "} catch (java.beans.IntrospectionException ie) {"
                    + "throw new org.jdesktop.beansbinding.PropertyResolutionException(\"Exception while introspecting \" + $1.getClass().getName(), ie);"
                    + "} }");
            Class c = cc.toClass();
            cc = cp.get("org.jdesktop.beansbinding.BeanProperty");
            m = cc.getDeclaredMethod("getBeanInfo");
            m.setBody("{" +
            // "assert $1 != null;" +
                    "try {" + "return java.beans.Introspector.getBeanInfo($1.getClass());"
                    + "} catch (java.beans.IntrospectionException ie) {"
                    + "throw new org.jdesktop.beansbinding.PropertyResolutionException(\"Exception while introspecting \" + $1.getClass().getName(), ie);"
                    + "} }");
            c = cc.toClass();
        }
        catch (NotFoundException ex) {
            Logger.error(ex, "Could not patch beansbinding, wizards may load slowly.");
        }
        catch (CannotCompileException ex) {
            Logger.error(ex, "Could not patch beansbinding, wizards may load slowly.");
        }
    }

    public static void main(String[] args) {
        monkeyPatchBeansBinding();
        
        for (String s : args) {
            if (s.equals("--version")) {
                System.out.println(getVersion());
                System.exit(0);
            }
        }
        
        // http://developer.apple.com/library/mac/#documentation/Java/Conceptual/Java14Development/07-NativePlatformIntegration/NativePlatformIntegration.html#//apple_ref/doc/uid/TP40001909-212952-TPXREF134
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception e) {
            throw new Error(e);
        }

        File configurationDirectory = new File(System.getProperty("user.home"));
        configurationDirectory = new File(configurationDirectory, ".openpnp2");

        if (System.getProperty("configDir") != null) {
            configurationDirectory = new File(System.getProperty("configDir"));
        }

        configurationDirectory.mkdirs();

        configureLogging(configurationDirectory);

        Configuration.initialize(configurationDirectory);
        final Configuration configuration = Configuration.get();
        Locale.setDefault(Configuration.get().getLocale());

        // Numeric readouts such as the DRO need digits of equal width.
        FlatLaf.setPreferredMonospacedFontFamily(preferredMonospacedFontFamily());

        ThemeInfo theme = configuration.getThemeInfo();
        if (theme == null) {
            // Nothing stored yet. Without this, setTheme(null, ...) returns immediately and
            // the user is left on the system look and feel installed further up.
            theme = PonoThemes.followSystem();
        }
        new ThemeSettingsPanel().setTheme(theme, configuration.getFontSize(), configuration.isAlternateRows());
        ThemeDialog.getInstance().setOldTheme(theme);
        ToolTipManager.sharedInstance().setDismissDelay(60000);

        EventQueue.invokeLater(new Runnable() {
            public void run() {
                try {
                    MainFrame frame = new MainFrame(configuration);
                    frame.setVisible(true);
                    Logger.info(String.format("Bienvenue, Bienvenido, Willkommen, Hello, Namaskar, Welkom, Bonjour to Pono version %s.", Main.getVersion()));
                    configuration.getScripting().on("Startup", null);
                }
                catch (Exception e) {
                    Logger.error(e, "Could not start the Pono user interface.");
                }
            }
        });
    }

    /**
     * @return the first of Pono's preferred monospaced families that is actually installed,
     *         falling back to whatever the platform calls monospaced. Handing FlatLaf a
     *         family that is not present leaves the readouts proportionally spaced.
     */
    private static String preferredMonospacedFontFamily() {
        Set<String> installed = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String family : new String[] { "Cascadia Mono", "JetBrains Mono", "Consolas" }) {
            if (installed.contains(family)) {
                return family;
            }
        }
        return Font.MONOSPACED;
    }
}
