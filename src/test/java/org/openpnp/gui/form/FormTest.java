package org.openpnp.gui.form;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JTextField;

import org.junit.jupiter.api.Test;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.support.Wizard;
import org.openpnp.gui.support.WizardContainer;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;

/**
 * The declarative form: a field is bound to the property it names, an edit is written by Apply
 * and taken back by Reset, a check keeps a wrong value out, a condition hides a field, and a
 * misspelt property fails the form before anyone sees it.
 */
public class FormTest {
    public enum Method {
        FixedTime, Adaptive
    }

    /** A bean with one property of each kind a form edits. */
    public static class Sample extends AbstractModelObject {
        private String name = "F-08";
        private int count = 3;
        private double speed = 0.5;
        private Length width = new Length(8, LengthUnit.Millimeters);
        private boolean enabled = true;
        private Method method = Method.FixedTime;
        private Location location = new Location(LengthUnit.Millimeters, 312.88, 44.125, -32.4, 0);

        public String getName() {
            return name;
        }

        public void setName(String name) {
            String old = this.name;
            this.name = name;
            firePropertyChange("name", old, name);
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            int old = this.count;
            this.count = count;
            firePropertyChange("count", old, count);
        }

        public double getSpeed() {
            return speed;
        }

        public void setSpeed(double speed) {
            double old = this.speed;
            this.speed = speed;
            firePropertyChange("speed", old, speed);
        }

        public Length getWidth() {
            return width;
        }

        public void setWidth(Length width) {
            Length old = this.width;
            this.width = width;
            firePropertyChange("width", old, width);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            boolean old = this.enabled;
            this.enabled = enabled;
            firePropertyChange("enabled", old, enabled);
        }

        public Method getMethod() {
            return method;
        }

        public void setMethod(Method method) {
            Method old = this.method;
            this.method = method;
            firePropertyChange("method", old, method);
        }

        public Location getLocation() {
            return location;
        }

        public void setLocation(Location location) {
            Location old = this.location;
            this.location = location;
            firePropertyChange("location", old, location);
        }

        /** Announces the name changing to what it was, as a camera without its device does. */
        public void touch() {
            firePropertyChange("name", null, null);
        }
    }

    private static final WizardContainer CONTAINER = new WizardContainer() {
        @Override
        public void wizardCompleted(Wizard wizard) {
        }

        @Override
        public void wizardCancelled(Wizard wizard) {
        }
    };

    /** Millimetres to three places, as a default configuration shows them. */
    private static final org.openpnp.model.DisplayPreferences MM = new org.openpnp.model.DisplayPreferences() {
        @Override
        public LengthUnit getSystemUnits() {
            return LengthUnit.Millimeters;
        }

        @Override
        public String getLengthDisplayFormat() {
            return "%.3f";
        }

        @Override
        public String getLengthDisplayAlignedFormat() {
            return "%8.3f";
        }

        @Override
        public String getLengthDisplayFormatWithUnits() {
            return "%.3f mm";
        }

        @Override
        public String getLengthDisplayAlignedFormatWithUnits() {
            return "%8.3f mm";
        }

        @Override
        public String formatLength(Length length) {
            return String.format(java.util.Locale.ROOT, "%.3f", length.convertToUnits(LengthUnit.Millimeters).getValue());
        }

        @Override
        public int getVerticalScrollUnitIncrement() {
            return 16;
        }
    };

    private FormWizard form(Sample sample) {
        FormWizard form = Form.of(sample).preferences(MM)
                .section("Basic", "info")
                .text("name", "Name")
                .integer("count", "Count").validate(v -> !String.valueOf(v).trim().startsWith("-"), "not negative")
                .toggle("enabled", "Enabled", "used by jobs")
                .section("Geometry", "move")
                .length("width", "Tape width").width(84)
                .location("location", "Reference hole", false)
                .choice("method", "Method", Method.class)
                .decimal("speed", "Speed").visibleWhen("method", v -> v == Method.Adaptive)
                .build();
        form.setWizardContainer(CONTAINER);
        return form;
    }

    private static <T extends Component> List<T> all(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component c : root.getComponents()) {
            if (type.isInstance(c)) {
                found.add(type.cast(c));
            }
            if (c instanceof Container) {
                found.addAll(all((Container) c, type));
            }
        }
        return found;
    }

    private static JTextField field(FormWizard form, String text) {
        for (JTextField field : all(form, JTextField.class)) {
            if (field.getText().equals(text)) {
                return field;
            }
        }
        throw new AssertionError("no field showing " + text);
    }

    @Test
    public void everyFieldShowsItsPropertyAndApplyWritesTheEdits() {
        Sample sample = new Sample();
        FormWizard form = form(sample);
        assertEquals(List.of("name", "count", "enabled", "width", "location", "method", "speed"),
                Form.properties(form));

        field(form, "F-08").setText("F-09");
        field(form, "3").setText("5");
        field(form, "312.880").setText("300.000");
        assertEquals("F-08", sample.getName(), "nothing is written before Apply");

        form.apply();
        assertEquals("F-09", sample.getName());
        assertEquals(5, sample.getCount());
        assertEquals(300.0, sample.getLocation().getX(), 1e-9);
        assertEquals(44.125, sample.getLocation().getY(), 1e-9);
    }

    @Test
    public void theChangesSayWhatApplyWouldWriteFieldByField() {
        Sample sample = new Sample();
        FormWizard form = form(sample);
        assertTrue(form.changes().isEmpty());
        assertFalse(form.hasEdits());

        field(form, "8.000").setText("10");
        form.set("enabled", false);
        field(form, "44.125").setText("40");
        List<String> changes = new ArrayList<>();
        for (FormWizard.Change change : form.changes()) {
            changes.add(change.label + ": " + change.before + " -> " + change.after);
        }
        assertEquals(List.of(
                "Enabled: " + org.openpnp.Translations.getString("Form.Change.On") + " -> "
                        + org.openpnp.Translations.getString("Form.Change.Off"),
                "Tape width: 8.000 mm -> 10.000 mm",
                "Reference hole Y: 44.125 mm -> 40.000 mm"), changes);
        assertTrue(form.hasEdits());

        form.apply();
        assertTrue(form.changes().isEmpty());
        assertFalse(form.hasEdits());
    }

    @Test
    public void aPropertyAnnouncedUnchangedIsNotAnEdit() {
        Sample sample = new Sample();
        FormWizard form = form(sample);
        sample.touch();
        assertTrue(form.changes().isEmpty());
        assertFalse(form.hasEdits(), "Apply may light up, but nothing on screen differs");
    }

    @Test
    public void resetTakesTheEditsBack() {
        Sample sample = new Sample();
        FormWizard form = form(sample);
        field(form, "F-08").setText("changed");
        form.reset();
        field(form, "F-08");
        assertEquals("F-08", sample.getName());
    }

    @Test
    public void aFailingCheckKeepsTheValueOut() {
        Sample sample = new Sample();
        FormWizard form = form(sample);
        field(form, "3").setText("-1");
        assertThrows(Exception.class, form::validateInput);
        assertEquals(3, sample.getCount());
    }

    @Test
    public void aConditionShowsAFieldOnlyWhenItMeansSomething() {
        Sample sample = new Sample();
        FormWizard form = form(sample);
        JTextField speed = field(form, "0.500");
        form.getWizardPanel().doLayout();
        assertFalse(speed.isVisible(), "the adaptive setting is hidden for a fixed time");
        @SuppressWarnings("unchecked")
        JComboBox<Object> method = all(form, JComboBox.class).get(0);
        method.setSelectedItem(Method.Adaptive);
        javax.swing.SwingUtilities.invokeLater(() -> {
        });
        flush();
        assertTrue(speed.isVisible());
    }

    @Test
    public void aMisspeltPropertyFailsTheForm() {
        assertThrows(IllegalArgumentException.class,
                () -> Form.of(new Sample()).text("nmae", "Name").build());
    }

    @Test
    public void theToggleIsTheBooleanProperty() {
        Sample sample = new Sample();
        FormWizard form = form(sample);
        Forms.Toggle toggle = all(form, Forms.Toggle.class).get(0);
        assertTrue(toggle.isSelected());
        toggle.setSelected(false);
        form.apply();
        assertFalse(sample.isEnabled());
    }

    @Test
    public void segmentsPickAnEnumValueAndApplyWritesIt() {
        Sample sample = new Sample();
        FormWizard form = Form.of(sample).preferences(MM)
                .segmented("method", "Method", Method.class)
                .build();
        form.setWizardContainer(CONTAINER);
        Forms.Segmented segments = all(form, Forms.Segmented.class).get(0);
        assertEquals(Method.FixedTime, segments.getSelectedItem());
        List<javax.swing.JToggleButton> buttons = all(segments, javax.swing.JToggleButton.class);
        assertEquals(2, buttons.size());
        buttons.get(1).doClick();
        assertEquals(Method.FixedTime, sample.getMethod(), "nothing is written before Apply");
        form.apply();
        assertEquals(Method.Adaptive, sample.getMethod());
    }

    @Test
    public void aLocationCanCarryItsCaptureAndMoveButtons() {
        Sample sample = new Sample();
        FormWizard form = Form.of(sample).preferences(MM)
                .location("location", "Reference hole", true).locationButtons()
                .build();
        assertEquals(1, all(form, org.openpnp.gui.components.LocationButtonsPanel.class).size());
        assertThrows(IllegalStateException.class,
                () -> Form.of(sample).text("name", "Name").locationButtons());
    }

    @Test
    public void aPipelineShowsItsStagesAgainAfterAnEdit() {
        List<String> stages = new ArrayList<>(List.of("ImageCapture", "Threshold"));
        FormWizard form = Form.of(new Sample()).preferences(MM)
                .pipeline("Pipeline", () -> stages, () -> stages.add("DetectCircles"), null)
                .build();
        assertEquals(2, all(form, org.openpnp.gui.shell.Chip.class).size());
        javax.swing.JButton edit = all(form, javax.swing.JButton.class).get(0);
        edit.doClick();
        assertEquals(3, all(form, org.openpnp.gui.shell.Chip.class).size());
        assertFalse(all(form, javax.swing.JButton.class).get(1).isEnabled(), "no reset to offer");
    }

    private static void flush() {
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> {
            });
            javax.swing.SwingUtilities.invokeAndWait(() -> {
            });
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
