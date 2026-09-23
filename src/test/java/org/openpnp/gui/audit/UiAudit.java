package org.openpnp.gui.audit;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JRadioButton;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.UIResource;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.text.JTextComponent;
import javax.swing.tree.TreePath;

import org.openpnp.gui.support.Wizard;

import com.formdev.flatlaf.extras.FlatSVGIcon;

/**
 * The checks the ruler makes on what one scene puts on screen. Everything is judged from the
 * component tree as laid out and from the photograph of it: text is read from the components,
 * backgrounds are read from the pixels, because a panel that paints its own rounded fill says
 * nothing about it in its properties.
 */
final class UiAudit {
    /** A check: its id in the findings file and its title in the report. */
    enum Check {
        Latin("\u82f1\u6587\u6216\u7c7b\u540d"),
        HeaderTruncated("\u8868\u5934\u88ab\u622a\u65ad"),
        CellTruncated("\u8868\u683c\u5185\u5bb9\u88ab\u622a\u65ad"),
        TextTruncated("\u6587\u5b57\u88ab\u622a\u6210\u201c\u2026\u201d"),
        InspectorTooWide("\u5c5e\u6027\u680f\u6a2a\u5411\u6eda\u52a8\u6216\u88ab\u88c1"),
        LegacyIcon("\u65e7\u56fe\u6807"),
        FontSize("\u5b57\u53f7\u4e0d\u5728\u9636\u68af\u5185"),
        DisabledWithoutReason("\u7070\u6389\u7684\u63a7\u4ef6\u6ca1\u6709\u60ac\u505c\u8bf4\u660e"),
        Clipped("\u63a7\u4ef6\u88ab\u88c1\u6389\u6216\u6324\u6ca1"),
        Contrast("\u6587\u5b57\u4e0e\u5e95\u8272\u5bf9\u6bd4\u4e0d\u8db3"),
        LegacyWizard("\u5c4f\u5e55\u4e0a\u7684\u65e7\u5411\u5bfc"),
        UnexpectedDialog("\u610f\u5916\u5f39\u51fa\u7684\u5bf9\u8bdd\u6846"),
        SceneSetup("\u573a\u666f\u51c6\u5907\u5931\u8d25"),
        WindowSize("\u7a97\u53e3\u5c3a\u5bf8\u4e0d\u7b26");

        final String title;

        Check(String title) {
            this.title = title;
        }
    }

    /** One thing found: which check, on which page, in which round, where, and what. */
    static final class Finding {
        final Check check;
        final String scene;
        final String round;
        final String where;
        final String text;

        Finding(Check check, String scene, String round, String where, String text) {
            this.check = check;
            this.scene = scene;
            this.round = round;
            this.where = where;
            this.text = text;
        }

        String key() {
            return check + "\u0001" + where + "\u0001" + text;
        }
    }

    /** What the checks compare against: the allowed words, the type scale, the landmarks. */
    static final class Rules {
        /** Latin words and phrases the Chinese interface keeps: trade terms, units, key names. */
        final Set<String> allowedWords;
        final List<String> allowedPhrases;
        /** Names the user gave things - part numbers, feeder names - which are data, not text. */
        final Set<String> dataWords;
        /** The font sizes the stylesheet uses. */
        final Set<Float> typeScale;
        /** Simple names of Pono's classes, to tell a class name from an English word. */
        final Set<String> classNames;

        Rules(Set<String> allowedWords, List<String> allowedPhrases, Set<String> dataWords,
                Set<Float> typeScale, Set<String> classNames) {
            this.allowedWords = allowedWords;
            this.allowedPhrases = allowedPhrases;
            this.dataWords = dataWords;
            this.typeScale = typeScale;
            this.classNames = classNames;
        }
    }

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z][A-Za-z0-9_.'\\-]*");
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    /** Below this even large text is hard to read: WCAG's threshold for large text. */
    private static final double MIN_CONTRAST = 3.0;

    private final Rules rules;
    private final Map<Component, String> landmarks;
    private final JComponent root;
    private final BufferedImage shot;
    private final double scale;
    private final String scene;
    private final String round;
    private final List<Finding> findings = new ArrayList<>();
    /** Font sizes off the scale, counted per size and kind of component rather than one by one. */
    private final Map<String, int[]> offScale = new LinkedHashMap<>();
    private final Map<String, String> offScaleExample = new HashMap<>();
    private final JList<Object> listForRenderers = new JList<>();

    UiAudit(Rules rules, Map<Component, String> landmarks, JComponent root, BufferedImage shot,
            double scale, String scene, String round) {
        this.rules = rules;
        this.landmarks = landmarks;
        this.root = root;
        this.shot = shot;
        this.scale = scale;
        this.scene = scene;
        this.round = round;
    }

    List<Finding> run() {
        walk(root);
        for (Map.Entry<String, int[]> entry : offScale.entrySet()) {
            String[] parts = entry.getKey().split("\u0001", 3);
            add(Check.FontSize, parts[2], parts[0] + " px \u00b7 " + parts[1] + " \u00d7 "
                    + entry.getValue()[0] + "\uff0c\u4f8b\u5982\u300c"
                    + offScaleExample.get(entry.getKey()) + "\u300d");
        }
        return findings;
    }

    /**
     * Whether the component is on show within what is audited: it and every container up to the
     * root visible. For a window on the screen that is isShowing; it also holds for a dialog laid
     * out and painted without being shown, which is how the ruler photographs dialogs behind
     * whatever else is on the screen.
     */
    private boolean shown(Component c) {
        for (Component p = c; p != null; p = p.getParent()) {
            if (!p.isVisible()) {
                return false;
            }
            if (p == root) {
                return true;
            }
        }
        return false;
    }

    private void walk(Component c) {
        if (!shown(c)) {
            return;
        }
        if (c instanceof JComponent) {
            JComponent jc = (JComponent) c;
            if (jc.getVisibleRect().isEmpty()) {
                squeezed(jc);
                return;
            }
            inspect(jc);
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                walk(child);
            }
        }
    }

    private void inspect(JComponent c) {
        if (c instanceof Wizard && !(c instanceof org.openpnp.gui.form.FormWizard)) {
            add(Check.LegacyWizard, where(c), c.getClass().getName());
        }
        titledBorder(c);
        if (c instanceof JLabel) {
            JLabel label = (JLabel) c;
            text(c, label.getText());
            icon(c, label.getIcon());
            truncated(c, label.getText(), label.getIcon(), label.getVerticalAlignment(),
                    label.getHorizontalAlignment(), label.getVerticalTextPosition(),
                    label.getHorizontalTextPosition(), label.getIconTextGap());
            font(c, label.getText());
            contrast(c, c.getForeground(), label.getText());
            clipped(c);
        }
        else if (c instanceof AbstractButton && !(c instanceof JMenuItem)) {
            AbstractButton button = (AbstractButton) c;
            text(c, button.getText());
            icon(c, button.getIcon());
            truncated(c, button.getText(), button.getIcon(), button.getVerticalAlignment(),
                    button.getHorizontalAlignment(), button.getVerticalTextPosition(),
                    button.getHorizontalTextPosition(), button.getIconTextGap());
            font(c, button.getText());
            if (c instanceof JCheckBox || c instanceof JRadioButton) {
                contrast(c, c.getForeground(), button.getText());
            }
            disabled(c, button.getText());
            clipped(c);
        }
        else if (c instanceof JMenuItem) {
            // Only the menu bar's own menus are on screen, and they are read like buttons.
            text(c, ((JMenuItem) c).getText());
        }
        if (c instanceof JTextComponent) {
            font(c, ((JTextComponent) c).getText());
            disabled(c, null);
            clipped(c);
        }
        if (c instanceof JComboBox) {
            combo((JComboBox<?>) c);
            disabled(c, null);
            clipped(c);
        }
        if (c instanceof JSpinner || c instanceof JSlider) {
            disabled(c, null);
        }
        if (c instanceof JTabbedPane) {
            JTabbedPane tabs = (JTabbedPane) c;
            for (int i = 0; i < tabs.getTabCount(); i++) {
                text(c, tabs.getTitleAt(i));
            }
            font(c, tabs.getTabCount() > 0 ? tabs.getTitleAt(0) : null);
        }
        if (c instanceof JTable) {
            table((JTable) c);
        }
        if (c instanceof JTree) {
            tree((JTree) c);
        }
        if (c instanceof JScrollPane && isIn(c, "\u5c5e\u6027\u680f")) {
            tooWide((JScrollPane) c);
        }
    }

    // ----- text --------------------------------------------------------------------------------

    private void text(JComponent c, String text) {
        String bad = latin(text);
        if (bad != null) {
            add(Check.Latin, where(c), bad);
        }
    }

    private String latin(String text) {
        return latin(text, rules);
    }

    /** The text if it has Latin words the interface should not show, marked when one is a class. */
    static String latin(String text, Rules rules) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String plain = TAG.matcher(text).replaceAll(" ").replace("&nbsp;", " ").trim();
        String scan = plain;
        for (String phrase : rules.allowedPhrases) {
            scan = scan.replace(phrase, " ");
        }
        boolean found = false;
        boolean className = false;
        Matcher m = TOKEN.matcher(scan);
        while (m.find()) {
            String token = m.group().replaceAll("[.'\\-]+$", ""); //$NON-NLS-1$
            if (token.length() < 2 || token.chars().anyMatch(Character::isDigit)
                    || rules.allowedWords.contains(token) || rules.dataWords.contains(token)
                    || rules.dataWords.contains(m.group())) {
                continue;
            }
            found = true;
            className |= rules.classNames.contains(token);
        }
        if (!found) {
            return null;
        }
        return (className ? "\u7c7b\u540d\u3000" : "") + "\u300c" + shorten(plain, 90) + "\u300d";
    }

    private void titledBorder(JComponent c) {
        Border border = c.getBorder();
        while (border != null) {
            if (border instanceof TitledBorder) {
                TitledBorder titled = (TitledBorder) border;
                text(c, titled.getTitle());
                Color color = titled.getTitleColor();
                if (color != null && !(color instanceof UIResource)) {
                    contrast(c, color, titled.getTitle());
                }
                return;
            }
            border = border instanceof CompoundBorder ? ((CompoundBorder) border).getOutsideBorder()
                    : null;
        }
    }

    private void combo(JComboBox<?> combo) {
        @SuppressWarnings("unchecked")
        ListCellRenderer<Object> renderer = (ListCellRenderer<Object>) combo.getRenderer();
        for (int i = 0; i < combo.getItemCount(); i++) {
            Object item = combo.getItemAt(i);
            if (!(item instanceof Enum)) {
                continue;
            }
            Component rendered = renderer.getListCellRendererComponent(listForRenderers, item, i,
                    false, false);
            String text = rendered instanceof JLabel ? ((JLabel) rendered).getText()
                    : String.valueOf(item);
            String bad = latin(text);
            if (bad != null) {
                add(Check.Latin, where(combo) + " \u203a \u4e0b\u62c9\u9879", bad);
            }
        }
        Object selected = combo.getSelectedItem();
        font(combo, selected == null ? null : String.valueOf(selected));
    }

    private void tree(JTree tree) {
        Rectangle visible = tree.getVisibleRect();
        for (int row = 0; row < tree.getRowCount(); row++) {
            Rectangle bounds = tree.getRowBounds(row);
            if (bounds == null || !bounds.intersects(visible)) {
                continue;
            }
            TreePath path = tree.getPathForRow(row);
            Object node = path.getLastPathComponent();
            Component rendered = tree.getCellRenderer().getTreeCellRendererComponent(tree, node,
                    tree.isRowSelected(row), tree.isExpanded(row), tree.getModel().isLeaf(node), row,
                    false);
            String text = rendered instanceof JLabel ? ((JLabel) rendered).getText()
                    : String.valueOf(node);
            String bad = latin(text);
            if (bad != null) {
                add(Check.Latin, where(tree) + " \u203a \u6811\u8282\u70b9", bad);
            }
        }
        font(tree, tree.getRowCount() > 0 ? String.valueOf(tree.getPathForRow(0)) : null);
    }

    // ----- tables ------------------------------------------------------------------------------

    private void table(JTable table) {
        String where = where(table);
        font(table, "\u8868\u683c\u5185\u5bb9");
        JTableHeader header = table.getTableHeader();
        if (header != null && shown(header)) {
            font(header, "\u8868\u5934");
            for (int col = 0; col < table.getColumnCount(); col++) {
                TableColumn column = table.getColumnModel().getColumn(col);
                Object value = column.getHeaderValue();
                String name = value == null ? "" : value.toString();
                String bad = latin(name);
                if (bad != null) {
                    add(Check.Latin, where + " \u203a \u8868\u5934", bad);
                }
                TableCellRenderer renderer = column.getHeaderRenderer() != null
                        ? column.getHeaderRenderer() : header.getDefaultRenderer();
                Component rendered = renderer.getTableCellRendererComponent(table, value, false,
                        false, -1, col);
                int need = rendered.getPreferredSize().width;
                if (!name.isBlank() && need > column.getWidth()) {
                    add(Check.HeaderTruncated, where, "\u300c" + shorten(name, 40) + "\u300d \u9700\u8981 "
                            + need + " px\uff0c\u53ea\u6709 " + column.getWidth() + " px");
                }
            }
        }
        Rectangle visible = table.getVisibleRect();
        int first = table.rowAtPoint(visible.getLocation());
        int last = table.rowAtPoint(new java.awt.Point(visible.x, visible.y + visible.height - 1));
        if (first < 0) {
            return;
        }
        if (last < 0) {
            last = table.getRowCount() - 1;
        }
        int truncated = 0;
        String truncatedExample = null;
        int dim = 0;
        String dimExample = null;
        for (int row = first; row <= last; row++) {
            for (int col = 0; col < table.getColumnCount(); col++) {
                Object value = table.getValueAt(row, col);
                Component rendered = table.prepareRenderer(table.getCellRenderer(row, col), row, col);
                String text = rendered instanceof JLabel ? ((JLabel) rendered).getText() : null;
                if (value instanceof Enum) {
                    String bad = latin(text == null ? value.toString() : text);
                    if (bad != null) {
                        add(Check.Latin, where + " \u203a \u300c" + table.getColumnName(col)
                                + "\u300d\u5217", bad);
                    }
                }
                if (text == null || text.isBlank() || text.startsWith("<html")) { //$NON-NLS-1$
                    continue;
                }
                int width = table.getColumnModel().getColumn(col).getWidth();
                if (needs((JLabel) rendered, text) > width) {
                    truncated++;
                    if (truncatedExample == null) {
                        truncatedExample = table.getColumnName(col) + "\uff1a" + shorten(text, 30);
                    }
                }
                Rectangle cell = table.getCellRect(row, col, false);
                Color background = backgroundOf(table, cell);
                if (background != null && rendered.isEnabled()
                        && contrastRatio(rendered.getForeground(), background) < MIN_CONTRAST) {
                    dim++;
                    if (dimExample == null) {
                        dimExample = table.getColumnName(col) + "\uff1a" + shorten(text, 30) + " "
                                + hex(rendered.getForeground()) + " / " + hex(background);
                    }
                }
            }
        }
        if (truncated > 0) {
            add(Check.CellTruncated, where, truncated + " \u683c\uff0c\u4f8b\u5982\u300c"
                    + truncatedExample + "\u300d");
        }
        if (dim > 0) {
            add(Check.Contrast, where, dim + " \u683c\uff0c\u4f8b\u5982\u300c" + dimExample
                    + "\u300d");
        }
    }

    /**
     * The width a label needs for its text as painted at the round's scale. Measured at 100 %, as
     * Swing lays out, a cell can look as if it fits and still be painted "R..." at 150 %.
     */
    private double needs(JLabel label, String text) {
        java.awt.Insets insets = label.getInsets();
        Icon icon = label.getIcon();
        double width = insets.left + insets.right + textWidth(label.getFont(), text);
        if (icon != null) {
            width += icon.getIconWidth() + label.getIconTextGap();
        }
        return width;
    }

    private double textWidth(Font font, String text) {
        java.awt.font.FontRenderContext context = new java.awt.font.FontRenderContext(
                java.awt.geom.AffineTransform.getScaleInstance(scale, scale), true, false);
        return font.getStringBounds(text, context).getWidth();
    }

    // ----- layout ------------------------------------------------------------------------------

    /**
     * Whether the text still fits: laid out as the look and feel lays it out, the label comes back
     * elided when it does not.
     */
    private void truncated(JComponent c, String text, Icon icon, int verticalAlignment,
            int horizontalAlignment, int verticalTextPosition, int horizontalTextPosition,
            int gap) {
        if (text == null || text.isBlank() || text.startsWith("<html")) { //$NON-NLS-1$
            return;
        }
        java.awt.Insets insets = c.getInsets();
        Rectangle view = new Rectangle(insets.left, insets.top,
                c.getWidth() - insets.left - insets.right, c.getHeight() - insets.top - insets.bottom);
        FontMetrics metrics = c.getFontMetrics(c.getFont());
        String laidOut = SwingUtilities.layoutCompoundLabel(c, metrics, text, icon,
                verticalAlignment, horizontalAlignment, verticalTextPosition,
                horizontalTextPosition, view, new Rectangle(), new Rectangle(),
                text.isEmpty() || icon == null ? 0 : gap);
        if (!laidOut.equals(text)) {
            add(Check.TextTruncated, where(c), "\u300c" + shorten(text, 60) + "\u300d \u663e\u793a\u6210\u300c"
                    + laidOut + "\u300d");
        }
        else if (c instanceof JLabel && needs((JLabel) c, text) > c.getWidth() + 0.5) {
            add(Check.TextTruncated, where(c), "\u300c" + shorten(text, 60) + "\u300d \u5728 "
                    + Math.round(scale * 100) + "% \u4e0b\u653e\u4e0d\u4e0b");
        }
    }

    /** Cut off by the edge of the window or of a parent too small for it, outside a scroll pane. */
    private void clipped(JComponent c) {
        if (SwingUtilities.getAncestorOfClass(JViewport.class, c) != null) {
            return;
        }
        Rectangle visible = c.getVisibleRect();
        int hiddenWidth = c.getWidth() - visible.width;
        int hiddenHeight = c.getHeight() - visible.height;
        if (hiddenWidth > 1 || hiddenHeight > 1) {
            add(Check.Clipped, where(c), describe(c) + " \u88ab\u88c1\u6389 "
                    + (hiddenWidth > 1 ? "\u5bbd " + hiddenWidth + " px " : "")
                    + (hiddenHeight > 1 ? "\u9ad8 " + hiddenHeight + " px" : ""));
        }
    }

    /** A control that is on screen by its flags but has been laid out to nothing. */
    private void squeezed(JComponent c) {
        if (!(c instanceof AbstractButton || c instanceof JLabel || c instanceof JComboBox
                || c instanceof JTextComponent)) {
            return;
        }
        if (SwingUtilities.getAncestorOfClass(JViewport.class, c) != null) {
            return;
        }
        String text = c instanceof JLabel ? ((JLabel) c).getText()
                : c instanceof AbstractButton ? ((AbstractButton) c).getText() : null;
        Icon icon = c instanceof JLabel ? ((JLabel) c).getIcon()
                : c instanceof AbstractButton ? ((AbstractButton) c).getIcon() : null;
        if ((text == null || text.isBlank()) && icon == null && !(c instanceof JComboBox)) {
            return;
        }
        if (c.getWidth() <= 1 || c.getHeight() <= 1) {
            add(Check.Clipped, where(c), describe(c) + " \u88ab\u6324\u6210 " + c.getWidth()
                    + " \u00d7 " + c.getHeight() + " px");
        }
    }

    private void tooWide(JScrollPane pane) {
        JViewport viewport = pane.getViewport();
        Component view = viewport == null ? null : viewport.getView();
        if (view == null) {
            return;
        }
        // A view held at the viewport's width gives way down to its minimum; beyond that it is cut.
        boolean tracks = view instanceof javax.swing.Scrollable
                && ((javax.swing.Scrollable) view).getScrollableTracksViewportWidth();
        int need = tracks ? view.getMinimumSize().width : view.getPreferredSize().width;
        int have = viewport.getExtentSize().width;
        if (need > have + 1 && !(view instanceof JTable) && !(view instanceof JTree)
                && !(view instanceof JList)) {
            add(Check.InspectorTooWide, where(pane),
                    view.getClass().getSimpleName() + " \u8981 " + need + " px\uff0c\u5c5e\u6027\u680f\u53ea\u6709 "
                            + have + " px");
        }
    }

    // ----- appearance --------------------------------------------------------------------------

    private void icon(JComponent c, Icon icon) {
        String legacy = legacyIcon(icon);
        if (legacy != null) {
            add(Check.LegacyIcon, where(c), legacy);
        }
    }

    /** The icon's name when it is not from the new set; null for new, painted or look and feel. */
    static String legacyIcon(Icon icon) {
        if (icon == null) {
            return null;
        }
        if (icon instanceof FlatSVGIcon) {
            String name = ((FlatSVGIcon) icon).getName();
            // Diagrams are pictures, not icons, and are drawn as they are meant to be seen.
            return name != null && (name.startsWith("icons/pono/") || name.startsWith("icons/diagrams/")) //$NON-NLS-1$ //$NON-NLS-2$
                    ? null : name;
        }
        if (icon instanceof ImageIcon) {
            String description = ((ImageIcon) icon).getDescription();
            return description == null ? "ImageIcon" //$NON-NLS-1$
                    : description.substring(description.lastIndexOf('/') + 1);
        }
        String type = icon.getClass().getName();
        if (type.startsWith("com.formdev.") || type.startsWith("javax.swing.") //$NON-NLS-1$ //$NON-NLS-2$
                || type.startsWith("sun.") || type.startsWith("org.openpnp.gui.shell.")) { //$NON-NLS-1$ //$NON-NLS-2$
            return null;
        }
        return type;
    }

    private void font(JComponent c, String text) {
        Font font = c.getFont();
        if (font == null || text == null || text.isBlank()) {
            return;
        }
        float size = font.getSize2D();
        for (float allowed : rules.typeScale) {
            if (Math.abs(allowed - size) < 0.05f) {
                return;
            }
        }
        String key = String.format(Locale.ROOT, "%.1f", size) + "\u0001"
                + kind(c) + "\u0001" + landmark(c);
        offScale.computeIfAbsent(key, k -> new int[1])[0]++;
        offScaleExample.putIfAbsent(key, shorten(TAG.matcher(text).replaceAll(""), 24));
    }

    private void disabled(JComponent c, String text) {
        if (c.isEnabled()) {
            return;
        }
        String tip = c.getToolTipText();
        if (tip == null || tip.isBlank()) {
            add(Check.DisabledWithoutReason, where(c), describe(c)
                    + (text == null || text.isBlank() ? "" : "\u300c" + shorten(text, 40) + "\u300d"));
        }
    }

    private void contrast(JComponent c, Color foreground, String text) {
        if (text == null || text.isBlank() || foreground == null || !c.isEnabled()) {
            return;
        }
        Color background = backgroundOf(c, c.getVisibleRect());
        if (background == null) {
            return;
        }
        double ratio = contrastRatio(foreground, background);
        if (ratio < MIN_CONTRAST) {
            add(Check.Contrast, where(c), String.format(Locale.ROOT, "%.1f\uff1a", ratio) + hex(foreground)
                    + " / " + hex(background) + (foreground instanceof UIResource ? "" : "\uff08\u5199\u6b7b\u7684\u989c\u8272\uff09")
                    + "\u300c" + shorten(TAG.matcher(text).replaceAll(""), 40) + "\u300d");
        }
    }

    /**
     * The colour most of the area is painted in, read from the photograph: the background as it
     * was painted, whichever component painted it.
     */
    private Color backgroundOf(JComponent c, Rectangle area) {
        Rectangle r = SwingUtilities.convertRectangle(c, area, root);
        int x0 = Math.max(0, (int) Math.floor(r.x * scale));
        int y0 = Math.max(0, (int) Math.floor(r.y * scale));
        int x1 = Math.min(shot.getWidth(), (int) Math.ceil((r.x + r.width) * scale));
        int y1 = Math.min(shot.getHeight(), (int) Math.ceil((r.y + r.height) * scale));
        if (x1 - x0 < 2 || y1 - y0 < 2) {
            return null;
        }
        int step = Math.max(1, (int) Math.sqrt((double) (x1 - x0) * (y1 - y0) / 20000));
        Map<Integer, long[]> buckets = new HashMap<>();
        long[] best = null;
        for (int y = y0; y < y1; y += step) {
            for (int x = x0; x < x1; x += step) {
                int rgb = shot.getRGB(x, y);
                int key = ((rgb >> 19) & 0x1f) << 10 | ((rgb >> 11) & 0x1f) << 5 | ((rgb >> 3) & 0x1f);
                long[] bucket = buckets.computeIfAbsent(key, k -> new long[4]);
                bucket[0]++;
                bucket[1] += (rgb >> 16) & 0xff;
                bucket[2] += (rgb >> 8) & 0xff;
                bucket[3] += rgb & 0xff;
                if (best == null || bucket[0] > best[0]) {
                    best = bucket;
                }
            }
        }
        return best == null ? null
                : new Color((int) (best[1] / best[0]), (int) (best[2] / best[0]), (int) (best[3] / best[0]));
    }

    static double contrastRatio(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(Color color) {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen())
                + 0.0722 * channel(color.getBlue());
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    // ----- where -------------------------------------------------------------------------------

    private void add(Check check, String where, String text) {
        findings.add(new Finding(check, scene, round, where, text));
    }

    /** The landmarks above the component, outermost first, and what the component is. */
    private String where(Component c) {
        return landmark(c) + " \u203a " + kind(c);
    }

    private String landmark(Component c) {
        List<String> path = new ArrayList<>();
        String wizard = null;
        for (Component a = c.getParent(); a != null; a = a.getParent()) {
            String label = landmarks.get(a);
            if (label != null) {
                path.add(0, label);
            }
            else if (wizard == null && a instanceof Wizard) {
                wizard = a.getClass().getSimpleName();
            }
            else if (a instanceof JDialog) {
                path.add(0, "\u5bf9\u8bdd\u6846\u300c" + ((JDialog) a).getTitle() + "\u300d");
            }
        }
        if (wizard != null) {
            path.add(wizard);
        }
        return path.isEmpty() ? "\u7a97\u53e3" : String.join(" \u203a ", path);
    }

    private boolean isIn(Component c, String landmark) {
        for (Component a = c; a != null; a = a.getParent()) {
            if (landmark.equals(landmarks.get(a))) {
                return true;
            }
        }
        return false;
    }

    private static String kind(Component c) {
        Class<?> type = c.getClass();
        while (type.getSimpleName().isEmpty()) {
            type = type.getSuperclass();
        }
        return type.getSimpleName();
    }

    private static String describe(JComponent c) {
        String text = c instanceof JLabel ? ((JLabel) c).getText()
                : c instanceof AbstractButton ? ((AbstractButton) c).getText() : null;
        if (text == null || text.isBlank()) {
            text = c.getToolTipText();
        }
        if ((text == null || text.isBlank()) && c instanceof AbstractButton
                && ((AbstractButton) c).getAction() != null) {
            Object name = ((AbstractButton) c).getAction().getValue(javax.swing.Action.NAME);
            text = name == null ? null : name.toString();
        }
        String kind = c instanceof JToggleButton ? "\u5207\u6362\u6309\u94ae"
                : c instanceof AbstractButton ? "\u6309\u94ae" : c instanceof JLabel ? "\u6807\u7b7e"
                        : c instanceof JComboBox ? "\u4e0b\u62c9\u6846"
                                : c instanceof JTextComponent ? "\u8f93\u5165\u6846" : kind(c);
        return kind + (text == null || text.isBlank() ? ""
                : "\u300c" + shorten(TAG.matcher(text).replaceAll(""), 30) + "\u300d");
    }

    static String shorten(String text, int max) {
        String single = text.replaceAll("\\s+", " ").trim(); //$NON-NLS-1$ //$NON-NLS-2$
        return single.length() <= max ? single : single.substring(0, max - 1) + "\u2026";
    }

    static String hex(Color color) {
        return String.format("#%06x", color.getRGB() & 0xffffff); //$NON-NLS-1$
    }
}
