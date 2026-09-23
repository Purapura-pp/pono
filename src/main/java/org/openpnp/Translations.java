package org.openpnp;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.pmw.tinylog.Logger;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.Resource;
import io.github.classgraph.ScanResult;

public class Translations {
    private static final String BUNDLE_NAME = "org.openpnp.translations"; //$NON-NLS-1$

    private static final String TEXT_BUNDLE_NAME = "org.openpnp.texts"; //$NON-NLS-1$

    private static final String PATTERN_BUNDLE_NAME = "org.openpnp.patterns"; //$NON-NLS-1$

    /** Keys under this prefix are words a template may substitute, not templates themselves. */
    private static final String VOCABULARY_PREFIX = "Word."; //$NON-NLS-1$

    /**
     * A template needs one unbroken run of literal text at least this long before it is allowed to
     * match anything, so that a careless entry such as "%s." cannot swallow every string that
     * reaches it.
     */
    private static final int MINIMUM_LITERAL_RUN = 6;

    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?s"); //$NON-NLS-1$

    /** Where the bundles live, as a resource path rather than a package name. */
    private static final String BUNDLE_PATH = "org/openpnp"; //$NON-NLS-1$

    private static final Pattern BUNDLE_RESOURCE =
            Pattern.compile(".*/translations_(\\w+)\\.properties"); //$NON-NLS-1$

    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME, new UTF8Control());

    private static final ResourceBundle TEXT_BUNDLE = loadTextBundle();

    private static final Map<String, String> VOCABULARY = loadVocabulary();

    private static final List<ProsePattern> PROSE_PATTERNS = loadProsePatterns();

    /** Matching runs on every repaint of the issues table, so each answer is worked out once. */
    private static final Map<String, String> PROSE_CACHE = new ConcurrentHashMap<>();

    private static List<Locale> availableLocales;

    private Translations() {
    }

    public static String getString(String key) {
        try {
            return RESOURCE_BUNDLE.getString(key);
        } catch (MissingResourceException e) {
            return '!' + key + '!';
        }
    }

    /** Whether the key has a translation, in the display language or in English. */
    public static boolean has(String key) {
        return RESOURCE_BUNDLE.containsKey(key);
    }

    private static ResourceBundle loadTextBundle() {
        try {
            return ResourceBundle.getBundle(TEXT_BUNDLE_NAME, new UTF8Control());
        }
        catch (MissingResourceException e) {
            return null;
        }
    }

    private static ResourceBundle patternBundle(Locale locale) {
        try {
            return ResourceBundle.getBundle(PATTERN_BUNDLE_NAME, locale, new NoFallbackControl());
        }
        catch (MissingResourceException e) {
            return null;
        }
    }

    /**
     * The English words a template is allowed to substitute, mapped to the display language.
     * <p>
     * These are the roles the sources name themselves - the pump control actuator, the primary
     * fiducial - as opposed to the names a user gave their nozzles and cameras, which have no entry
     * here and are therefore left as they wrote them.
     */
    private static Map<String, String> loadVocabulary() {
        ResourceBundle english = patternBundle(Locale.ROOT);
        ResourceBundle localised = patternBundle(Locale.getDefault());
        if (english == null || localised == null) {
            return Collections.emptyMap();
        }
        Map<String, String> vocabulary = new HashMap<>();
        for (String key : english.keySet()) {
            if (!key.startsWith(VOCABULARY_PREFIX)) {
                continue;
            }
            try {
                vocabulary.put(english.getString(key), localised.getString(key));
            }
            catch (MissingResourceException e) {
                // The display language has not taken this word on. It stays English.
            }
        }
        return Collections.unmodifiableMap(vocabulary);
    }

    private static List<ProsePattern> loadProsePatterns() {
        ResourceBundle english = patternBundle(Locale.ROOT);
        ResourceBundle localised = patternBundle(Locale.getDefault());
        if (english == null || localised == null) {
            return Collections.emptyList();
        }
        List<ProsePattern> patterns = new ArrayList<>();
        for (String key : english.keySet()) {
            if (key.startsWith(VOCABULARY_PREFIX)) {
                continue;
            }
            String source = english.getString(key);
            String target;
            try {
                target = localised.getString(key);
            }
            catch (MissingResourceException e) {
                continue;
            }
            if (target.equals(source)) {
                // English itself, or a language that has not taken this template on. Either way
                // matching it would cost time and change nothing.
                continue;
            }
            ProsePattern pattern = ProsePattern.of(key, source, target);
            if (pattern != null) {
                patterns.add(pattern);
            }
        }
        // Most literal text first. "The %s actuator %s has no driver assigned." and
        // "The %s actuator %s has no %s assigned." both match the same string, and the one that
        // spells out what is missing is the one that should win.
        patterns.sort(Comparator.comparingInt((ProsePattern p) -> p.literalLength).reversed());
        return Collections.unmodifiableList(patterns);
    }

    /**
     * Translates a string the source code carries as English prose instead of as a key, keyed by
     * that English. The Issues and Solutions descriptions are written that way, and their English
     * wording doubles as their persisted identity - see Solutions.Issue.getFingerprint - so they
     * cannot be turned into keys without invalidating every issue a user has already dismissed or
     * marked as solved, and without the identity shifting whenever the display language changes.
     * <p>
     * A description the source assembles from variables is a different string on every machine, so
     * no whole-string key can reach it. Those are matched against the templates in
     * patterns_&lt;lang&gt;.properties instead, which lift the variable parts out and put them back
     * into the translation. Anything neither mechanism knows is returned unchanged.
     */
    public static String translateText(String english) {
        if (english == null || english.isEmpty()) {
            return english;
        }
        if (TEXT_BUNDLE != null) {
            try {
                return TEXT_BUNDLE.getString(english);
            }
            catch (MissingResourceException e) {
                // Not prose anyone translated whole. It may still be prose the source assembled.
            }
        }
        if (PROSE_PATTERNS.isEmpty()) {
            return english;
        }
        // Deliberately not computeIfAbsent: a template may hand a captured fragment back here, and
        // ConcurrentHashMap refuses to be updated from inside its own mapping function.
        String cached = PROSE_CACHE.get(english);
        if (cached != null) {
            return cached;
        }
        String translated = translateByTemplate(english);
        PROSE_CACHE.put(english, translated);
        return translated;
    }

    private static String translateByTemplate(String english) {
        for (ProsePattern pattern : PROSE_PATTERNS) {
            String translated = pattern.apply(english, VOCABULARY);
            if (translated != null) {
                return translated;
            }
        }
        return english;
    }

    /**
     * One entry of patterns.properties paired with its translation, ready to match.
     *
     * The English side is turned into a regular expression by quoting the literal runs and letting
     * each placeholder capture whatever sits between them, so the same sentence can be recognised
     * whatever the machine happens to call its nozzles and cameras.
     */
    static final class ProsePattern {
        private final Pattern matcher;
        private final String localised;
        private final int placeholders;
        private final int literalLength;

        private ProsePattern(Pattern matcher, String localised, int placeholders,
                int literalLength) {
            this.matcher = matcher;
            this.localised = localised;
            this.placeholders = placeholders;
            this.literalLength = literalLength;
        }

        /** Returns null, having said why, when the entry could not be made into a safe matcher. */
        static ProsePattern of(String key, String english, String localised) {
            StringBuilder regex = new StringBuilder("^"); //$NON-NLS-1$
            Matcher placeholder = PLACEHOLDER.matcher(english);
            int consumed = 0;
            int placeholders = 0;
            int literalLength = 0;
            int longestRun = 0;
            while (placeholder.find()) {
                String literal = english.substring(consumed, placeholder.start());
                regex.append(Pattern.quote(literal)).append("(.+?)"); //$NON-NLS-1$
                literalLength += literal.length();
                longestRun = Math.max(longestRun, literal.length());
                consumed = placeholder.end();
                placeholders++;
            }
            String tail = english.substring(consumed);
            regex.append(Pattern.quote(tail)).append("$"); //$NON-NLS-1$
            literalLength += tail.length();
            longestRun = Math.max(longestRun, tail.length());

            if (placeholders == 0) {
                Logger.warn("Translation template {} has no placeholder, so it is whole prose and " //$NON-NLS-1$
                        + "belongs in texts instead. Ignored.", key); //$NON-NLS-1$
                return null;
            }
            if (longestRun < MINIMUM_LITERAL_RUN) {
                Logger.warn("Translation template {} has no run of {} literal characters, so it is " //$NON-NLS-1$
                        + "too loose to match on safely. Ignored.", key, MINIMUM_LITERAL_RUN); //$NON-NLS-1$
                return null;
            }
            // DOTALL because some of these descriptions run to several lines.
            ProsePattern pattern = new ProsePattern(Pattern.compile(regex.toString(), Pattern.DOTALL),
                    localised, placeholders, literalLength);
            if (pattern.format(new Object[placeholders]) == null) {
                Logger.warn("Translation template {} does not accept the {} values its English " //$NON-NLS-1$
                        + "takes. Ignored.", key, placeholders); //$NON-NLS-1$
                return null;
            }
            return pattern;
        }

        /** The translated sentence, or null when this template does not describe that string. */
        String apply(String english, Map<String, String> vocabulary) {
            Matcher match = matcher.matcher(english);
            if (!match.matches()) {
                return null;
            }
            Object[] values = new Object[placeholders];
            for (int i = 0; i < placeholders; i++) {
                String value = match.group(i + 1);
                String word = vocabulary.get(value);
                // A role the source names in English is translated. Otherwise the captured text
                // goes back through the whole chain, because some of these sentences are built
                // around a clause rather than a name, and a clause may be translatable in its own
                // right. A name the user chose matches nothing and arrives as they wrote it.
                values[i] = word != null ? word : translateText(value);
            }
            return format(values);
        }

        private String format(Object[] values) {
            try {
                return String.format(localised, values);
            }
            catch (IllegalFormatException e) {
                return null;
            }
        }
    }

    /**
     * The locales the application can display, discovered from the translation bundles that are
     * actually on the classpath rather than from a list in the code, so that adding a language is
     * only a matter of adding its properties file.
     * <p>
     * Scans the classpath rather than a directory: once packaged, the bundles are inside the jar,
     * where File cannot list them.
     */
    public static synchronized List<Locale> getAvailableLocales() {
        if (availableLocales == null) {
            availableLocales = discoverLocales();
        }
        return availableLocales;
    }

    private static List<Locale> discoverLocales() {
        Set<Locale> locales = new HashSet<>();
        // The base bundle holds English and has no suffix to discover it by.
        locales.add(Locale.US);
        try (ScanResult scan = new ClassGraph().acceptPaths(BUNDLE_PATH).scan()) {
            for (Resource resource : scan.getAllResources()) {
                Matcher matcher = BUNDLE_RESOURCE.matcher("/" + resource.getPath());
                if (matcher.matches()) {
                    locales.add(localeOfSuffix(matcher.group(1)));
                }
            }
        }
        catch (Throwable t) {
            // Better to offer only English than to fail starting up over a menu.
            Logger.warn(t, "Could not scan for translation bundles."); //$NON-NLS-1$
        }
        // By language tag, not by display name: this list is cached, and it is first built before
        // Locale.setDefault has been given the configured locale, so display names here would be
        // ordered by whatever the system language happened to be. Callers that show the list sort
        // it for the user themselves.
        List<Locale> sorted = new ArrayList<>(locales);
        sorted.sort(Comparator.comparing(Locale::toLanguageTag));
        return Collections.unmodifiableList(sorted);
    }

    private static Locale localeOfSuffix(String suffix) {
        String[] parts = suffix.split("_", 3); //$NON-NLS-1$
        if (parts.length == 1) {
            return new Locale(parts[0]);
        }
        if (parts.length == 2) {
            return new Locale(parts[0], parts[1]);
        }
        return new Locale(parts[0], parts[1], parts[2]);
    }

    /**
     * The locale to display when the user has never picked one. Prefers an exact match for the
     * system locale, then a bundle whose language matches, and falls back to English.
     * <p>
     * Called before {@link Locale#setDefault} has been given the configured value, so the default
     * locale it reads is still the system one. Re-reading it afterwards yields the same answer,
     * because whatever this returns is itself one of the available locales.
     */
    public static Locale matchSystemLocale() {
        Locale system = Locale.getDefault();
        List<Locale> available = getAvailableLocales();
        for (Locale locale : available) {
            if (locale.equals(system)) {
                return locale;
            }
        }
        for (Locale locale : available) {
            if (locale.getLanguage().equals(system.getLanguage())) {
                return locale;
            }
        }
        return Locale.US;
    }

    /**
     * ResourceBundle answers a request it cannot satisfy with the default locale's bundle. That is
     * the right thing for the interface, but not here: the templates are recognised by comparing a
     * language against its own English, and a silent substitution would make every template in
     * every language look already translated.
     */
    private static class NoFallbackControl extends UTF8Control {
        @Override
        public Locale getFallbackLocale(String baseName, Locale locale) {
            return null;
        }
    }

    public static class UTF8Control extends ResourceBundle.Control {
        public ResourceBundle newBundle
                (String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
                throws IOException {
            String bundleName = toBundleName(baseName, locale);
            String resourceName = toResourceName(bundleName, "properties");
            ResourceBundle bundle = null;
            InputStream stream = null;
            if (reload) {
                URL url = loader.getResource(resourceName);
                if (url != null) {
                    URLConnection connection = url.openConnection();
                    if (connection != null) {
                        connection.setUseCaches(false);
                        stream = connection.getInputStream();
                    }
                }
            } else {
                stream = loader.getResourceAsStream(resourceName);
            }
            if (stream != null) {
                try {
                    bundle = new PropertyResourceBundle(new InputStreamReader(stream, StandardCharsets.UTF_8));
                } finally {
                    stream.close();
                }
            }
            return bundle;
        }
    }
}
