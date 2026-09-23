package org.openpnp.gui.theme;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The design tokens, read from the one place they are defined - {@code design/mockups/mock.css} -
 * and written into both Pono themes and into {@code org.openpnp.gui.shell.Tokens}.
 *
 * <pre>
 * tools/design-tokens/generate.ps1
 * </pre>
 *
 * The themes keep their hand-written parts; only the block between {@link #BEGIN} and {@link #END}
 * is replaced. DesignTokensTest fails when a generated file no longer matches the stylesheet, so a
 * colour changed in the stylesheet cannot quietly stay the old colour in the program.
 */
public final class DesignTokens {
    static final Path CSS = Paths.get("design/mockups/mock.css");
    static final Path DARK = Paths.get("src/main/resources/org/openpnp/gui/theme/PonoDarkLaf.properties");
    static final Path LIGHT = Paths.get("src/main/resources/org/openpnp/gui/theme/PonoLightLaf.properties");
    static final Path JAVA = Paths.get("src/main/java/org/openpnp/gui/shell/Tokens.java");

    static final String BEGIN = "#---- generated from design/mockups/mock.css by tools/design-tokens/generate.ps1: do not edit ----";
    static final String END = "#---- end of generated tokens ----";

    private static final Pattern BLOCK = Pattern.compile(":root(\\[data-theme=\"(dark|light)\"\\])?\\s*\\{([^}]*)\\}");
    private static final Pattern DECLARATION = Pattern.compile("--([a-z0-9-]+)\\s*:\\s*([^;]+);");
    private static final Pattern RGBA = Pattern.compile("rgba?\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*(?:,\\s*([0-9.]+)\\s*)?\\)");

    /** The variables of :root, then of each theme's block, in the stylesheet's order. */
    private final Map<String, String> root = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> themes = new LinkedHashMap<>();

    private DesignTokens() {
    }

    static DesignTokens read(Path css) throws IOException {
        String text = new String(Files.readAllBytes(css), StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", ""); //$NON-NLS-1$ //$NON-NLS-2$
        DesignTokens tokens = new DesignTokens();
        tokens.themes.put("dark", new LinkedHashMap<>()); //$NON-NLS-1$
        tokens.themes.put("light", new LinkedHashMap<>()); //$NON-NLS-1$
        Matcher block = BLOCK.matcher(text);
        while (block.find()) {
            Map<String, String> target = block.group(2) == null ? tokens.root
                    : tokens.themes.get(block.group(2));
            Matcher declaration = DECLARATION.matcher(block.group(3));
            while (declaration.find()) {
                target.put(declaration.group(1), declaration.group(2).trim());
            }
        }
        if (tokens.root.isEmpty() || tokens.themes.get("dark").isEmpty()) { //$NON-NLS-1$
            throw new IOException("no :root variables found in " + css.toAbsolutePath());
        }
        return tokens;
    }

    /** The theme's variables: :root, overridden by the theme's own block. */
    Map<String, String> variables(String theme) {
        Map<String, String> merged = new LinkedHashMap<>(root);
        merged.putAll(themes.get(theme));
        return merged;
    }

    /** Every colour variable of the theme as #rrggbb or #rrggbbaa, keyed by its stylesheet name. */
    Map<String, String> colours(String theme) {
        Map<String, String> colours = new LinkedHashMap<>();
        for (Map.Entry<String, String> variable : variables(theme).entrySet()) {
            String colour = colour(variable.getValue());
            if (colour != null) {
                colours.put(variable.getKey(), colour);
            }
        }
        return colours;
    }

    static String colour(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.matches("#[0-9a-f]{3}")) { //$NON-NLS-1$
            return "#" + v.charAt(1) + v.charAt(1) + v.charAt(2) + v.charAt(2) + v.charAt(3) + v.charAt(3); //$NON-NLS-1$
        }
        if (v.matches("#[0-9a-f]{6}([0-9a-f]{2})?")) { //$NON-NLS-1$
            return v;
        }
        Matcher rgba = RGBA.matcher(v);
        if (rgba.matches()) {
            String rgb = String.format("#%02x%02x%02x", Integer.parseInt(rgba.group(1)), //$NON-NLS-1$
                    Integer.parseInt(rgba.group(2)), Integer.parseInt(rgba.group(3)));
            return rgba.group(4) == null ? rgb
                    : rgb + String.format("%02x", Math.round(Double.parseDouble(rgba.group(4)) * 255)); //$NON-NLS-1$
        }
        return null;
    }

    int px(String name) {
        return (int) Math.round(number(name));
    }

    double number(String name) {
        String value = root.get(name);
        if (value == null) {
            throw new IllegalArgumentException("mock.css has no --" + name);
        }
        return Double.parseDouble(value.replace("px", "").trim()); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** The stylesheet name as the theme key: surface-2 is Pono.surface2, border-strong Pono.borderStrong. */
    static String key(String name) {
        StringBuilder key = new StringBuilder("Pono."); //$NON-NLS-1$
        boolean upper = false;
        for (char c : name.toCharArray()) {
            if (c == '-') {
                upper = true;
            }
            else {
                key.append(upper && Character.isLetter(c) ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return key.toString();
    }

    /** The generated block of one theme's properties file. */
    String properties(String theme) {
        Map<String, String> c = colours(theme);
        StringBuilder p = new StringBuilder();
        p.append(BEGIN).append('\n');
        p.append("#\n# Panels are the surface; the window between the cards is Pono.bg, painted by the shell.\n"); //$NON-NLS-1$
        p.append("@background = ").append(c.get("surface")).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        p.append("@foreground = ").append(c.get("text")).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        p.append("@buttonBackground = ").append(c.get("surface-2")).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        p.append("@componentBackground = ").append(c.get("surface-2")).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        p.append("@menuBackground = ").append(c.get("surface")).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        p.append("@accentColor = ").append(c.get("accent")).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        p.append("Component.borderColor = ").append(c.get("border-strong")).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        p.append("Component.disabledBorderColor = ").append(c.get("border")).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        p.append("Separator.foreground = ").append(c.get("border")).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        p.append("Table.gridColor = ").append(c.get("border")).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        p.append("TableHeader.background = ").append(c.get("surface-2")).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        p.append("TableHeader.separatorColor = ").append(c.get("border")).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        p.append("TableHeader.bottomSeparatorColor = ").append(c.get("border")).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        p.append("TableHeader.foreground = ").append(c.get("muted")).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        p.append("#\n# FlatLaf's arc is a diameter; the stylesheet's radius is half of it.\n"); //$NON-NLS-1$
        p.append("Component.arc = ").append(2 * px("r-sm")).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        p.append("Button.arc = ").append(2 * px("r-sm")).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        p.append("TextComponent.arc = ").append(2 * px("r-sm")).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        p.append("defaultFont = ").append(px("fs-body")).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        p.append("Table.rowHeight = ").append(px("h-row")).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        p.append("#\n# The stylesheet's colours under their own names.\n"); //$NON-NLS-1$
        for (Map.Entry<String, String> colour : c.entrySet()) {
            p.append(key(colour.getKey())).append(" = ").append(colour.getValue()).append('\n'); //$NON-NLS-1$
        }
        p.append("#\n# The names the shell used before the stylesheet was the source.\n"); //$NON-NLS-1$
        String[][] aliases = { { "textSecondary", "text-2" }, { "textMuted", "muted" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                { "statusOk", "ok" }, { "statusWarn", "warn" }, { "statusErr", "err" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                { "statusRun", "info" }, { "cameraBackground", "camera-bg" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                { "overlayBackground", "overlay" }, { "rail.background", "surface" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                { "rail.hoverBackground", "hover" }, { "rail.selectedBackground", "accent-soft" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                { "rail.selectedIndicator", "accent" } }; //$NON-NLS-1$ //$NON-NLS-2$
        for (String[] alias : aliases) {
            p.append("Pono.").append(alias[0]).append(" = ").append(c.get(alias[1])).append('\n'); //$NON-NLS-1$ //$NON-NLS-2$
        }
        p.append(END).append('\n');
        return p.toString();
    }

    /** The file with its generated block replaced, or the block appended if it has none yet. */
    static String replaceBlock(String file, String block) {
        int begin = file.indexOf(BEGIN);
        int end = file.indexOf(END);
        if (begin < 0 || end < begin) {
            return file.endsWith("\n") ? file + "\n" + block : file + "\n\n" + block; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        return file.substring(0, begin) + block + file.substring(end + END.length() + 1);
    }

    static String generatedBlock(String file) {
        int begin = file.indexOf(BEGIN);
        int end = file.indexOf(END);
        return begin < 0 || end < begin ? null : file.substring(begin, end + END.length() + 1);
    }

    /** The Tokens class: radii, the type scale and the spacing as numbers, the colours as keys. */
    String java() {
        StringBuilder j = new StringBuilder();
        j.append("package org.openpnp.gui.shell;\n\n"); //$NON-NLS-1$
        j.append("/**\n * The design tokens of {@code design/mockups/mock.css} as constants. Generated by\n"); //$NON-NLS-1$
        j.append(" * {@code tools/design-tokens/generate.ps1} (org.openpnp.gui.theme.DesignTokens in the test\n"); //$NON-NLS-1$
        j.append(" * sources): do not edit, DesignTokensTest compares this file with the stylesheet.\n"); //$NON-NLS-1$
        j.append(" * <p>\n * Sizes are CSS pixels at the default 13 pixel body text; radii are radii, so Java2D and\n"); //$NON-NLS-1$
        j.append(" * FlatLaf, which take a diameter, want twice the number.\n */\n"); //$NON-NLS-1$
        j.append("public final class Tokens {\n    private Tokens() {\n    }\n\n"); //$NON-NLS-1$
        for (Map.Entry<String, String> variable : root.entrySet()) {
            String name = variable.getKey();
            String value = variable.getValue();
            String constant = name.toUpperCase(Locale.ROOT).replace('-', '_');
            if (name.startsWith("fs-")) { //$NON-NLS-1$
                j.append("    public static final float ").append(constant).append(" = ") //$NON-NLS-1$ //$NON-NLS-2$
                        .append(trim(number(name))).append("f;\n"); //$NON-NLS-1$
            }
            else if (name.startsWith("fw-")) { //$NON-NLS-1$
                j.append("    public static final int ").append(constant).append(" = ") //$NON-NLS-1$ //$NON-NLS-2$
                        .append(value.trim()).append(";\n"); //$NON-NLS-1$
            }
            else if (value.matches("[0-9.]+px")) { //$NON-NLS-1$
                j.append("    public static final int ").append(constant).append(" = ") //$NON-NLS-1$ //$NON-NLS-2$
                        .append(px(name)).append(";\n"); //$NON-NLS-1$
            }
        }
        j.append("\n    /** The colours' theme keys: the same name in both themes. */\n"); //$NON-NLS-1$
        for (String name : colours("dark").keySet()) { //$NON-NLS-1$
            j.append("    public static final String ").append(name.toUpperCase(Locale.ROOT).replace('-', '_')) //$NON-NLS-1$
                    .append(" = \"").append(key(name)).append("\";\n"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        j.append("}\n"); //$NON-NLS-1$
        return j.toString();
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
    }

    static final Path ICONS_JS = Paths.get("design/mockups/icons.js");
    static final Path ICONS = Paths.get("src/main/resources/icons/pono");
    private static final Pattern ICON = Pattern.compile("(?m)^\\s*([a-z0-9]+):\\s*'(.*)',?\\s*$");

    /**
     * The mockups' sprite, one SVG file per icon, drawn in black: the program's colour filter
     * turns black into whatever the component paints its text in. currentColor, which the
     * browser resolves, becomes that black too.
     */
    static Map<String, String> icons() throws IOException {
        Map<String, String> icons = new java.util.TreeMap<>();
        Matcher m = ICON.matcher(readFile(ICONS_JS));
        while (m.find()) {
            icons.put(m.group(1) + ".svg", //$NON-NLS-1$
                    "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" width=\"24\" height=\"24\" " //$NON-NLS-1$
                            + "fill=\"none\" stroke=\"#000000\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\">" //$NON-NLS-1$
                            + m.group(2).replace("currentColor", "#000000") + "</svg>\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
        if (icons.isEmpty()) {
            throw new IOException("no icons found in " + ICONS_JS.toAbsolutePath());
        }
        return icons;
    }

    static String readFile(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).replace("\r\n", "\n"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public static void main(String[] args) throws IOException {
        DesignTokens tokens = read(CSS);
        for (Map.Entry<Path, String> theme : Map.of(DARK, "dark", LIGHT, "light").entrySet()) { //$NON-NLS-1$ //$NON-NLS-2$
            String file = readFile(theme.getKey());
            Files.write(theme.getKey(), replaceBlock(file, tokens.properties(theme.getValue()))
                    .getBytes(StandardCharsets.UTF_8));
        }
        Files.write(JAVA, tokens.java().getBytes(StandardCharsets.UTF_8));
        for (Map.Entry<String, String> icon : icons().entrySet()) {
            Files.write(ICONS.resolve(icon.getKey()), icon.getValue().getBytes(StandardCharsets.UTF_8));
        }
        System.out.println("design tokens written: " + DARK + ", " + LIGHT + ", " + JAVA + ", "
                + icons().size() + " icons in " + ICONS);
    }
}
