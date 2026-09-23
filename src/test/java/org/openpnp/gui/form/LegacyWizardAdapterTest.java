package org.openpnp.gui.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;

import org.junit.jupiter.api.Test;

import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;

/**
 * The adapter that fits the old wizards into the properties column until they are redone: titled
 * borders become headings, fixed sizes give way, a four-column grid folds, a check box's label
 * ticks it.
 */
public class LegacyWizardAdapterTest {
    /** The shape most wizards have: two label and field pairs a row, in fixed widths. */
    private static JPanel wideWizard() {
        JPanel panel = new JPanel(new FormLayout(
                new ColumnSpec[] { FormSpecs.RELATED_GAP_COLSPEC, ColumnSpec.decode("120px"),
                        FormSpecs.RELATED_GAP_COLSPEC, ColumnSpec.decode("200px"),
                        FormSpecs.RELATED_GAP_COLSPEC, ColumnSpec.decode("120px"),
                        FormSpecs.RELATED_GAP_COLSPEC, ColumnSpec.decode("200px") },
                new RowSpec[] { FormSpecs.RELATED_GAP_ROWSPEC, RowSpec.decode("20px"),
                        FormSpecs.RELATED_GAP_ROWSPEC, RowSpec.decode("20px") }));
        panel.setBorder(new TitledBorder("General"));
        panel.add(new JLabel("Name"), "2, 2");
        panel.add(new JTextField("F-08", 20), "4, 2");
        panel.add(new JLabel("Pitch"), "6, 2");
        panel.add(new JTextField("4", 20), "8, 2");
        panel.add(new JLabel("Enabled"), "2, 4");
        panel.add(new JCheckBox(), "4, 4");
        return panel;
    }

    @Test
    public void aColumnOfAtLeastAFixedWidthGivesWay() {
        JPanel panel = new JPanel(new FormLayout(
                new ColumnSpec[] { ColumnSpec.decode("right:max(70dlu;default)"), FormSpecs.RELATED_GAP_COLSPEC,
                        ColumnSpec.decode("max(70dlu;default)") },
                new RowSpec[] { FormSpecs.DEFAULT_ROWSPEC }));
        panel.add(new JLabel("X"), "1, 1");
        panel.add(new JTextField(), "3, 1");
        LegacyWizardAdapter.adapt(panel);
        FormLayout layout = (FormLayout) panel.getLayout();
        assertSame(com.jgoodies.forms.layout.Sizes.DEFAULT, layout.getColumnSpec(1).getSize());
        assertSame(com.jgoodies.forms.layout.Sizes.DEFAULT, layout.getColumnSpec(3).getSize());
        assertEquals(ColumnSpec.RIGHT, layout.getColumnSpec(1).getDefaultAlignment(), "alignment kept");
    }

    @Test
    public void aTitledBorderBecomesASectionHeading() {
        JPanel panel = wideWizard();
        LegacyWizardAdapter.adapt(panel);
        assertTrue(panel.getBorder() instanceof LegacyWizardAdapter.SectionBorder);
        assertEquals("General", ((LegacyWizardAdapter.SectionBorder) panel.getBorder()).title);
    }

    @Test
    public void aGridTooWideForTheColumnFoldsItsSecondPairUnderTheFirst() {
        JPanel panel = wideWizard();
        LegacyWizardAdapter.adapt(panel);
        FormLayout layout = (FormLayout) panel.getLayout();
        assertEquals(3, layout.getColumnCount(), "label, gap, field");
        JLabel pitch = null;
        for (java.awt.Component c : panel.getComponents()) {
            if (c instanceof JLabel && "Pitch".equals(((JLabel) c).getText())) {
                pitch = (JLabel) c;
            }
        }
        CellConstraints cc = layout.getConstraints(pitch);
        assertEquals(1, cc.gridX, "the second pair's label goes to the label column");
        assertEquals(4, cc.gridY, "on the row after the first pair's");
        assertTrue(panel.getPreferredSize().width <= LegacyWizardAdapter.WIDTH + 200);
    }

    @Test
    public void aRowOfOneLocationIsNotFolded() {
        JPanel panel = new JPanel(new FormLayout(
                new ColumnSpec[] { ColumnSpec.decode("120px"), FormSpecs.RELATED_GAP_COLSPEC,
                        ColumnSpec.decode("200px"), FormSpecs.RELATED_GAP_COLSPEC,
                        ColumnSpec.decode("200px"), FormSpecs.RELATED_GAP_COLSPEC,
                        ColumnSpec.decode("200px") },
                new RowSpec[] { FormSpecs.DEFAULT_ROWSPEC }));
        panel.add(new JLabel("Offset"), "1, 1");
        panel.add(new JTextField("0.000", 20), "3, 1");
        panel.add(new JTextField("0.000", 20), "5, 1");
        panel.add(new javax.swing.JButton("Detect offset"), "7, 1");
        LegacyWizardAdapter.adapt(panel);
        assertEquals(7, ((FormLayout) panel.getLayout()).getColumnCount(), "X and Y stay side by side");
    }

    @Test
    public void theLabelOfACheckBoxTicksIt() {
        JPanel panel = wideWizard();
        LegacyWizardAdapter.adapt(panel);
        JCheckBox box = null;
        JLabel enabled = null;
        for (java.awt.Component c : panel.getComponents()) {
            if (c instanceof JCheckBox) {
                box = (JCheckBox) c;
            }
            if (c instanceof JLabel && "Enabled".equals(((JLabel) c).getText())) {
                enabled = (JLabel) c;
            }
        }
        assertSame(box, enabled.getLabelFor());
    }

    @Test
    public void theWizardsScrollPaneKeepsItToTheColumnsWidth() {
        JPanel wizard = new JPanel(new java.awt.BorderLayout());
        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(wideWizard());
        wizard.add(scroll);
        LegacyWizardAdapter.adapt(wizard);
        assertTrue(scroll.getViewport().getView() instanceof LegacyWizardAdapter.WidthTracking);
        assertEquals(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
                scroll.getHorizontalScrollBarPolicy());
    }

    @Test
    public void aTablesScrollPaneIsLeftAlone() {
        JPanel wizard = new JPanel(new java.awt.BorderLayout());
        javax.swing.JTable table = new javax.swing.JTable(2, 2);
        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(table);
        wizard.add(scroll);
        LegacyWizardAdapter.adapt(wizard);
        assertSame(table, scroll.getViewport().getView());
    }

    @Test
    public void aWizardIsAdaptedOnce() {
        JPanel panel = wideWizard();
        LegacyWizardAdapter.adapt(panel);
        int count = panel.getComponentCount();
        LegacyWizardAdapter.adapt(panel);
        assertEquals(count, panel.getComponentCount());
    }
}
