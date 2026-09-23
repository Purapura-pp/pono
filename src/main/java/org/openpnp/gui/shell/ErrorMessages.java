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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openpnp.Translations;
import org.pmw.tinylog.Logger;

/**
 * What an error message means to the person reading it.
 * <p>
 * The program's messages are English, written for whoever wrote the code: "Feeder F-08 has no
 * part.", "Location is outside of the soft limit". There are several hundred of them; the ones a
 * user meets most often are translated here by pattern, with a sentence on what to do, from
 * {@code error-messages_<language>.tsv} beside this class. A message none of them matches is
 * shown as it is. The original always goes under the dialog's details.
 */
public final class ErrorMessages {
    private ErrorMessages() {
    }

    /** A title, a reason and advice for a dialog. */
    public static final class Explained {
        public final String title;
        public final String what;
        public final String more;

        Explained(String title, String what, String more) {
            this.title = title;
            this.what = what;
            this.more = more;
        }
    }

    private static final class Rule {
        final Pattern pattern;
        final String what;
        final String more;

        Rule(Pattern pattern, String what, String more) {
            this.pattern = pattern;
            this.what = what;
            this.more = more;
        }
    }

    private static final Map<String, List<Rule>> RULES = new HashMap<>();

    /** The titles callers pass that say nothing: they become "Something went wrong". */
    private static boolean generic(String title) {
        return title == null || title.trim().isEmpty() || title.trim().equalsIgnoreCase("Error") //$NON-NLS-1$
                || title.trim().equalsIgnoreCase("Exception"); //$NON-NLS-1$
    }

    public static Explained explain(String title, String message) {
        // The display language, as the translation bundles are chosen by.
        return explain(title, message, Locale.getDefault());
    }

    static Explained explain(String title, String message, Locale locale) {
        String shownTitle = generic(title) ? Translations.getString("Dialogs.Error.Title") : title; //$NON-NLS-1$
        if (message == null || message.trim().isEmpty()) {
            return new Explained(shownTitle, Translations.getString("Dialogs.Error.NoMessage"), null); //$NON-NLS-1$
        }
        String text = message.trim();
        for (Rule rule : rules(locale == null ? "" : locale.getLanguage())) { //$NON-NLS-1$
            Matcher m = rule.pattern.matcher(text);
            if (m.find()) {
                return new Explained(shownTitle, substitute(rule.what, m),
                        rule.more == null ? null : substitute(rule.more, m));
            }
        }
        // Prose the translation bundles know, whole or by template; otherwise the message itself.
        return new Explained(shownTitle, Translations.translateText(text), null);
    }

    private static String substitute(String template, Matcher m) {
        String out = template;
        for (int i = m.groupCount(); i >= 1; i--) {
            String group = m.group(i);
            out = out.replace("$" + i, group == null ? "" : tidy(group)); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return out;
    }

    private static final Pattern TRAILING_ZEROS = Pattern.compile("(-?\\d+)\\.(\\d*?)0+(?!\\d)"); //$NON-NLS-1$
    private static final Pattern UNIT = Pattern.compile("(\\d)(mm|cm|in|mil|um)\\b"); //$NON-NLS-1$

    /**
     * A number as a person writes it: the motion planner prints coordinates with %f, so a limit
     * read "350.000000mm". That is "350 mm", and "350.120000mm" is "350.12 mm".
     */
    static String tidy(String text) {
        Matcher m = TRAILING_ZEROS.matcher(text);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(
                    m.group(2).isEmpty() ? m.group(1) : m.group(1) + "." + m.group(2))); //$NON-NLS-1$
        }
        m.appendTail(out);
        return UNIT.matcher(out.toString()).replaceAll("$1 $2"); //$NON-NLS-1$
    }

    private static synchronized List<Rule> rules(String language) {
        return RULES.computeIfAbsent(language, ErrorMessages::load);
    }

    /** One rule a line: a pattern, the sentence saying what it means, the advice; tab separated. */
    private static List<Rule> load(String language) {
        List<Rule> rules = new ArrayList<>();
        if (language.isEmpty()) {
            return rules;
        }
        try (InputStream in = ErrorMessages.class.getResourceAsStream("error-messages_" + language + ".tsv")) { //$NON-NLS-1$ //$NON-NLS-2$
            if (in == null) {
                return rules;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) { //$NON-NLS-1$
                    continue;
                }
                String[] parts = line.split("\t"); //$NON-NLS-1$
                if (parts.length < 2) {
                    continue;
                }
                rules.add(new Rule(Pattern.compile(parts[0], Pattern.CASE_INSENSITIVE),
                        parts[1], parts.length > 2 && !parts[2].isEmpty() ? parts[2] : null));
            }
        }
        catch (Exception e) {
            Logger.warn(e, "The error message translations for {} could not be read.", language); //$NON-NLS-1$
        }
        return rules;
    }

    /**
     * Errors seen lately: the same one within the window is not shown again, only counted. A
     * failing step in a loop used to stack one dialog on another until the user had clicked
     * through dozens of them.
     */
    public static final class Repeats {
        private final long window;
        private final Map<String, long[]> seen = new HashMap<>();

        public Repeats(long windowMillis) {
            this.window = windowMillis;
        }

        /**
         * Records the error.
         * 
         * @return How many times it has been seen within the window before this one; 0 when it
         *         is to be shown.
         */
        public synchronized int seen(String key, long now) {
            long[] entry = seen.get(key);
            if (entry == null || now - entry[0] > window) {
                seen.put(key, new long[] { now, 1, 0 });
                return 0;
            }
            entry[1]++;
            entry[2]++;
            return (int) entry[2];
        }

        /** How many times the error has been seen since it was last shown, that time included. */
        public synchronized int count(String key) {
            long[] entry = seen.get(key);
            return entry == null ? 0 : (int) entry[1];
        }
    }
}
