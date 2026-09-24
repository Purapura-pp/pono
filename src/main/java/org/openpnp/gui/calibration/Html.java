/*
 * Copyright (C) 2026 Pono
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

package org.openpnp.gui.calibration;

/** The text of the HTML an issue explains itself in, for a paragraph that wraps. */
public final class Html {
    private Html() {
    }

    public static String plain(String html) {
        if (html == null) {
            return ""; //$NON-NLS-1$
        }
        String text = html.replaceAll("(?i)<br\\s*/?>|</p>|</h\\d>|</li>|</tr>", "\n") //$NON-NLS-1$ //$NON-NLS-2$
                .replaceAll("(?i)<li>", "\u00b7 ") //$NON-NLS-1$ //$NON-NLS-2$
                .replaceAll("<[^>]*>", "") //$NON-NLS-1$ //$NON-NLS-2$
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
                .replace("&quot;", "\"").replace("&amp;", "&"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        StringBuilder out = new StringBuilder();
        for (String line : text.split("\n")) { //$NON-NLS-1$
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(trimmed);
        }
        return out.toString();
    }

    /** The heading of a choice's description, which says it in a few words. */
    static String heading(String html) {
        if (html == null) {
            return ""; //$NON-NLS-1$
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?is)<h\\d>(.*?)</h\\d>").matcher(html); //$NON-NLS-1$
        if (m.find()) {
            return plain(m.group(1));
        }
        String plain = plain(html);
        int line = plain.indexOf('\n');
        return line < 0 ? plain : plain.substring(0, line);
    }
}
