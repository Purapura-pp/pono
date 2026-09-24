package org.openpnp.gui.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.Translations;
import org.openpnp.gui.machinesettings.SetupChecks;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.ReferenceNozzleTip;
import org.openpnp.machine.reference.calibration.CalibrationPlan;
import org.openpnp.machine.reference.calibration.SettingChange;
import org.openpnp.machine.reference.presets.MachinePreset;
import org.openpnp.machine.reference.presets.MachinePresets;
import org.openpnp.model.CalibrationStep;
import org.openpnp.model.Configuration;
import org.openpnp.model.Solutions;

/** The calibration page's rows: what is merged, what goes where, and what is left to machine settings. */
public class CalibrationItemsTest {
    @TempDir
    Path tempDir;

    private static MachinePreset lumen() throws Exception {
        for (MachinePreset preset : MachinePresets.builtIn()) {
            if (preset.getName().equals("LumenPnP v4.1")) {
                return preset;
            }
        }
        throw new AssertionError("no LumenPnP preset");
    }

    /** The LumenPnP preset on a fresh configuration, collected as the page collects. */
    private List<CalibrationItem> collectedOnLumen() throws Exception {
        File configuration = tempDir.resolve("lumen").toFile();
        Configuration.initialize(configuration);
        Configuration.get().load();
        Configuration.get().save();
        MachinePresets.apply(lumen(), configuration);
        Configuration.initialize(configuration);
        Configuration.get().load();
        ReferenceMachine machine = (ReferenceMachine) Configuration.get().getMachine();
        List<Solutions.Issue> tagged = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> tagged.addAll(CalibrationPlan.scan(machine, machine.getSolutions())));
        CalibrationPlan plan = CalibrationPlan.of(machine, tagged);
        SwingUtilities.invokeAndWait(() -> {
            machine.getSolutions().findIssues();
            machine.getSolutions().publishIssues();
        });
        return CalibrationItems.of(plan, machine.getSolutions().getIssues(), SetupChecks.of(machine), List.of());
    }

    private static CalibrationItem find(List<CalibrationItem> items, CalibrationItem.Kind kind, String title) {
        for (CalibrationItem item : items) {
            if (item.getKind() == kind && item.getTitle().equals(title)) {
                return item;
            }
        }
        return null;
    }

    @Test
    public void lumenPnPSuggestsASettingOnceForAllTheNozzleTipsItIsAbout() throws Exception {
        List<CalibrationItem> items = collectedOnLumen();
        CalibrationItem misdetections = find(items, CalibrationItem.Kind.Suggestion,
                Translations.translateText("Misdetections tolerated"));
        assertNotNull(misdetections, items.toString());
        assertEquals(6, misdetections.getParts().size());
        assertEquals("5", String.valueOf(misdetections.commonValue(false)));
        assertEquals("1", String.valueOf(misdetections.commonValue(true)));
        assertEquals(CalibrationStep.NozzleTipCalibration, misdetections.getStep());
        for (CalibrationItem.Part part : misdetections.getParts()) {
            assertTrue(part.getElement() instanceof ReferenceNozzleTip);
            assertNotNull(part.getChange());
        }
    }

    @Test
    public void whatTheMachineSettingsPageShowsIsAHintAndNoIssue() throws Exception {
        List<CalibrationItem> items = collectedOnLumen();
        CalibrationItem diameter = null;
        for (CalibrationItem item : items) {
            for (Solutions.Issue issue : item.getIssues()) {
                assertFalse(issue.getUntranslatedIssue().contains("Min. Part Diameter"), item.toString());
                assertFalse(issue.getUntranslatedIssue().equals(
                        org.openpnp.machine.reference.solutions.CameraSolutions.NOT_CONNECTED), item.toString());
            }
            if (item.getCheck() != null && item.getCheck().kind.equals(SetupChecks.MIN_DIAMETER)) {
                diameter = item;
            }
        }
        assertNotNull(diameter);
        assertEquals(CalibrationItem.Kind.Hint, diameter.getKind());
        assertEquals(6, diameter.getParts().size());
    }

    @Test
    public void aStepThatMeasuresIsOneRowForTheElementsOfItsKind() throws Exception {
        List<CalibrationItem> items = collectedOnLumen();
        CalibrationItem backlash = null;
        for (CalibrationItem item : items) {
            if (item.getKind() == CalibrationItem.Kind.Measure && item.getStep() == CalibrationStep.XyBacklash) {
                assertNull(backlash, "one row for the backlash of x and y");
                backlash = item;
            }
        }
        assertNotNull(backlash);
        assertEquals(2, backlash.getSteps().size());
        // Not homed: the backlash waits for the steps before it.
        assertTrue(backlash.isWaiting());
        assertFalse(backlash.getUnsettledPrerequisites().isEmpty());
    }

    @Test
    public void theRowsComeInTheOrderOfTheirGroups() throws Exception {
        List<CalibrationItem> items = collectedOnLumen();
        int previous = -1;
        for (CalibrationItem item : items) {
            assertTrue(item.getKind().ordinal() >= previous, "out of order at " + item);
            previous = item.getKind().ordinal();
            if (item.getKind() == CalibrationItem.Kind.Done) {
                for (CalibrationPlan.Step step : item.getSteps()) {
                    assertEquals(CalibrationPlan.Status.Done, step.getStatus());
                }
            }
        }
    }

    @Test
    public void anIssueSaysWhatItWrites() {
        Solutions.Issue issue = new Solutions.PlainIssue(new Solutions.Subject() {
        }, "An issue.", "A solution.", Solutions.Severity.Suggestion, null).withChange("Resolution", () -> 2, () -> 3);
        SettingChange change = SettingChange.of(issue);
        assertNotNull(change);
        assertEquals("Resolution", change.getSettingName());
        assertEquals(2, change.getCurrentValue());
        assertEquals(3, change.getProposedValue());
        assertNull(SettingChange.of(new Solutions.PlainIssue(new Solutions.Subject() {
        }, "An issue.", "A solution.", Solutions.Severity.Suggestion, null)));
    }

    /** An element of the machine by its name. */
    static final class Thing implements Solutions.Subject, org.openpnp.model.Named {
        private String name;

        Thing(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void setName(String name) {
            this.name = name;
        }
    }

    /** Advice that is one value going into one setting. */
    static final class Align extends Solutions.Issue {
        Align(Thing thing) {
            super(thing, "Align " + thing.getName() + ".", "Enable the alignment.", Solutions.Severity.Suggestion,
                    null);
            withChange("Part-aligned rotation", () -> Boolean.FALSE, () -> Boolean.TRUE);
        }
    }

    /** Advice that does more than write a setting. */
    static final class Calibrate extends Solutions.Issue {
        Calibrate(Thing thing) {
            super(thing, "Calibrate " + thing.getName() + ".", "Run the calibration.", Solutions.Severity.Warning,
                    null);
        }
    }

    @Test
    public void theSameAdviceAboutSeveralElementsIsOneRowAndEachElementAPart() {
        List<Solutions.Issue> issues = List.of(new Align(new Thing("N1")), new Align(new Thing("N2")),
                new Calibrate(new Thing("N1")), new Calibrate(new Thing("N2")));
        List<CalibrationItem> items = CalibrationItems.of(null, issues, SetupChecks.none(), List.of());
        assertEquals(2, items.size(), items.toString());
        CalibrationItem align = items.get(0);
        assertEquals(CalibrationItem.Kind.Suggestion, align.getKind());
        assertEquals(Translations.translateText("Part-aligned rotation"), align.getTitle());
        assertEquals(List.of("N1", "N2"), List.of(align.getParts().get(0).getSubject(),
                align.getParts().get(1).getSubject()));
        assertEquals(Boolean.FALSE, align.commonValue(false));
        assertEquals(Boolean.TRUE, align.commonValue(true));
        // Named in each issue's own wording, the advice is what the row says.
        CalibrationItem calibrate = items.get(1);
        assertEquals(CalibrationItem.Kind.Hint, calibrate.getKind());
        assertEquals("Run the calibration.", calibrate.getTitle());
        assertEquals(2, calibrate.getParts().size());
    }

    @Test
    public void aDismissedSuggestionIsKeptWhereItCanBeTakenBack() throws Exception {
        Align dismissed = new Align(new Thing("N1"));
        dismissed.setState(Solutions.State.Dismissed);
        List<CalibrationItem> items = CalibrationItems.of(null, List.of(dismissed), SetupChecks.none(), List.of());
        assertEquals(1, items.size());
        assertEquals(CalibrationItem.Kind.Dismissed, items.get(0).getKind());
        assertTrue(items.get(0).canBeDismissed());
    }

    @Test
    public void whatWasMeasuredComesFirstAndInformationNotAtAll() {
        Solutions.Issue information = new Solutions.PlainIssue(new Solutions.Subject() {
        }, "Milestone.", "Done.", Solutions.Severity.Information, null);
        Solutions.Issue hint = new Solutions.PlainIssue(new Solutions.Subject() {
        }, "A hint.", "Do this.", Solutions.Severity.Warning, null);
        Pending measured = new Pending("XyBacklash:X", CalibrationStep.XyBacklash, null, "x", List.of(), List.of(),
                null, "", new Date(), "<a/>");
        List<CalibrationItem> items = CalibrationItems.of(null, List.of(information, hint), SetupChecks.none(),
                List.of(measured));
        assertEquals(2, items.size());
        assertEquals(CalibrationItem.Kind.Pending, items.get(0).getKind());
        assertEquals(CalibrationItem.Kind.Hint, items.get(1).getKind());
        assertEquals("A hint.", items.get(1).getTitle());
    }
}
