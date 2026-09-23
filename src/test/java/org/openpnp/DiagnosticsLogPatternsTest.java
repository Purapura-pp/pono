package org.openpnp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.openpnp.Translations.ProsePattern;

/**
 * The measurement log on the issues page is translated line by line: every line the diagnostics
 * write with values in it has a template that recognises it as the source formats it. Exercised at
 * the templates, as TranslationPatternsTest does, so that it says the same on any machine.
 */
public class DiagnosticsLogPatternsTest {
    private static Properties bundle(String name) throws IOException {
        Properties properties = new Properties();
        try (InputStream stream = Translations.class.getClassLoader()
                .getResourceAsStream("org/openpnp/" + name)) {
            assertNotNull(stream, name + " is not on the classpath");
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }

    /** What the line becomes, through its template, with the words the vocabulary knows. */
    private static String translate(String key, String line) throws IOException {
        Properties english = bundle("patterns.properties");
        Properties chinese = bundle("patterns_zh_CN.properties");
        Map<String, String> vocabulary = new HashMap<>();
        for (String word : english.stringPropertyNames()) {
            if (word.startsWith("Word.") && chinese.containsKey(word)) {
                vocabulary.put(english.getProperty(word), chinese.getProperty(word));
            }
        }
        ProsePattern pattern = ProsePattern.of(key, english.getProperty(key), chinese.getProperty(key));
        assertNotNull(pattern, key + " should compile");
        String translated = pattern.apply(line, vocabulary);
        assertNotNull(translated, key + " should recognise \"" + line + "\"");
        return translated;
    }

    private static boolean chinese(String text) {
        return text.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN);
    }

    @Test
    public void theLinesWithValuesInThemAreRecognised() throws IOException {
        String[][] lines = {
                { "MachineDiagnostics.Log.ReportDirectory", String.format("Report directory: %s", "C:\\pono\\diagnostics\\1") },
                { "MachineDiagnostics.Log.Done", String.format("Done. Report written to %s", "C:\\r\\report.html") },
                { "MachineDiagnostics.Log.VisionNoise", String.format("Vision noise: sd %.3f px (%.4f mm) over %d frames at %.1f fps", 0.123, 0.0017, 30, 24.0) },
                { "MachineDiagnostics.Log.Latency", String.format("Camera latency: %.0f ms (%.1f frames at %.1f fps)", 120.0, 2.9, 24.0) },
                { "MachineDiagnostics.Log.Basis", String.format("Compensation basis: %d reading(s), scale X %+.3f%% (sd %.3f%%), Y %+.3f%% (sd %.3f%%), shear %+.3f deg (sd %.3f)", 3, -0.09, 0.01, 0.02, 0.01, 0.07, 0.01) },
                { "MachineDiagnostics.Log.Backlash", String.format("%s backlash at %.2fx over %.1fmm: %.4f (sd %.4f)", "x", 1.0, 10.0, 0.0123, 0.001) },
                { "MachineDiagnostics.Log.StepTest", String.format("%s step test at %.2fx: %d stalled steps, largest jump %.4f mm", "y", 0.5, 0, 0.0123) },
                { "MachineDiagnostics.Log.Settle", String.format("Settle after %.0fmm: %.0f ms", 50.0, 180.0) },
                { "MachineDiagnostics.Log.HomingCycle", String.format("Homing cycle %d of %d", 2, 5) },
                { "MachineDiagnostics.Log.FiducialFound", String.format("%s: fiducial found %.3f mm from where it is configured (%+.3f, %+.3f)", "Hop 3", 0.012, 0.004, -0.011) },
                { "MachineDiagnostics.Log.ZFocus", String.format("Z focus from %s: %.4f mm", "above", -18.5) },
                { "MachineDiagnostics.Log.Rotation", String.format("Rotation at %.0f deg: backlash %.4f deg", 90.0, 0.1234) },
        };
        for (String[] line : lines) {
            String translated = translate(line[0], line[1]);
            assertTrue(chinese(translated), line[1] + " became " + translated);
        }
    }

    @Test
    public void aGroupAndAnEnglishWordAreTranslatedInTheLine() throws IOException {
        assertEquals("视觉噪声底 失败：timeout",
                translate("MachineDiagnostics.Log.GroupFailed", "VisionNoise FAILED: timeout"));
        assertTrue(translate("MachineDiagnostics.Log.ZFocus", "Z focus from above: -18.5000 mm").contains("上方"));
    }

    @Test
    public void thePercentSignsOfTheBasisSurvive() throws IOException {
        String translated = translate("MachineDiagnostics.Log.Basis", String.format(
                "Compensation basis: %d reading(s), scale X %+.3f%% (sd %.3f%%), Y %+.3f%% (sd %.3f%%), shear %+.3f deg (sd %.3f)",
                3, -0.09, 0.01, 0.02, 0.01, 0.07, 0.01));
        assertTrue(translated.contains("-0.090%"), translated);
    }
}
