package org.openpnp.gui.audit;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeMap;

import org.openpnp.gui.support.DisplayNames;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;

/**
 * Lists the enums the interface shows - every enum whose values() a page or a wizard puts in a
 * combo box, plus those shown in tables - with their constants, as the lines of the translation
 * file they need, English humanised from the constant. Keys already translated are marked.
 *
 * <pre>
 * java -cp target/openpnp-gui-0.0.1-alpha-SNAPSHOT.jar;target/test-classes \
 *     org.openpnp.gui.audit.EnumInventory out.properties
 * </pre>
 */
public class EnumInventory {
    public static void main(String[] args) throws Exception {
        Set<String> shown = Set.of(args.length > 1 ? args[1].split(",") : new String[0]);
        TreeMap<String, String> lines = new TreeMap<>();
        try (ScanResult scan = new ClassGraph().enableClassInfo().acceptPackages("org.openpnp").scan()) {
            for (ClassInfo info : scan.getAllEnums()) {
                if (info.getClasspathElementURL().toString().contains("test-classes")) {
                    continue;
                }
                String simple = info.getName().substring(info.getPackageName().length() + 1).replace('$', '.');
                String last = simple.substring(simple.lastIndexOf('.') + 1);
                if (!shown.isEmpty() && !shown.contains(simple) && !shown.contains(last)) {
                    continue;
                }
                Class<?> type = info.loadClass();
                for (Object constant : type.getEnumConstants()) {
                    Enum<?> value = (Enum<?>) constant;
                    // An enum with a text of its own is translated through it; the ones that
                    // show their constant need a display name.
                    if (!value.toString().equals(value.name())) {
                        continue;
                    }
                    lines.put(DisplayNames.key(value), DisplayNames.humanise(value.name()));
                }
            }
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(Paths.get(args[0]), StandardCharsets.UTF_8))) {
            lines.forEach((k, v) -> out.println(k + "=" + v));
        }
        System.out.println(lines.size() + " constants written to " + args[0]);
    }
}
