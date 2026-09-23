package org.openpnp.gui.audit;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

/**
 * Preferences that live in memory and die with the process. The ruler resizes the window, drags
 * the split panes and switches the theme; with the platform's preferences all of that would be
 * written into the registry the real Pono reads its layout from, and every photograph would start
 * from wherever the last session left the dividers.
 */
public class MemoryPreferencesFactory implements PreferencesFactory {
    private static final Preferences USER = new Node(null, "");
    private static final Preferences SYSTEM = new Node(null, "");

    @Override
    public Preferences userRoot() {
        return USER;
    }

    @Override
    public Preferences systemRoot() {
        return SYSTEM;
    }

    private static final class Node extends AbstractPreferences {
        private final Map<String, String> values = new HashMap<>();
        private final Map<String, Node> children = new HashMap<>();

        Node(Node parent, String name) {
            super(parent, name);
        }

        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            values.remove(key);
        }

        @Override
        protected void removeNodeSpi() {
            ((Node) parent()).children.remove(name());
        }

        @Override
        protected String[] keysSpi() {
            return values.keySet().toArray(new String[0]);
        }

        @Override
        protected String[] childrenNamesSpi() {
            return children.keySet().toArray(new String[0]);
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            return children.computeIfAbsent(name, n -> new Node(this, n));
        }

        @Override
        protected void syncSpi() {
        }

        @Override
        protected void flushSpi() {
        }
    }
}
