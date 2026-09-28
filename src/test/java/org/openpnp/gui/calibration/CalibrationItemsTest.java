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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.Translations;
import org.openpnp.gui.machinesettings.SetupChecks;
import org.openpnp.gui.machinesettings.SetupIssues;
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

    private ReferenceMachine machine;

    /** A collection opens the machine's cameras, which render frames until the machine is closed. */
    @AfterEach
    public void tearDown() throws Exception {
        if (machine != null) {
            machine.close();
        }
    }

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
        machine = (ReferenceMachine) Configuration.get().getMachine();
        List<Solutions.Issue> tagged = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> tagged.addAll(CalibrationPlan.scan(machine, machine.getSolutions())));
        CalibrationPlan plan = CalibrationPlan.of(machine, tagged);
        SwingUtilities.invokeAndWait(() -> {
            machine.getSolutions().findIssues();
            machine.getSolutions().publishIssues();
        });
        return CalibrationItems.of(plan, machine.getSolutions().getIssues(), List.of());
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
    public void whatTheMachineSettingsPageShowsIsNotOnTheCalibrationPage() throws Exception {
        List<CalibrationItem> items = collectedOnLumen();
        for (CalibrationItem item : items) {
            for (Solutions.Issue issue : item.getIssues()) {
                assertFalse(issue.getUntranslatedIssue().contains("Min. Part Diameter"), item.toString());
                assertFalse(issue.getUntranslatedIssue().equals(
                        org.openpnp.machine.reference.solutions.CameraSolutions.NOT_CONNECTED), item.toString());
                assertFalse(SetupIssues.isSetupIssue(issue), "a setup issue among the rows: " + item);
            }
        }
        // The nozzle tips' smallest part is one of the machine settings checks, not a row here.
        assertTrue(SetupChecks.of(machine).count(org.openpnp.gui.machinesettings.MachineSettingsPanel.NOZZLES) > 0);
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
    public void theGroupsNoStepMeasuresAreTheDiagnosticsOneRowEach() throws Exception {
        List<CalibrationItem> items = collectedOnLumen();
        List<org.openpnp.machine.reference.solutions.MachineDiagnostics.TestGroup> groups = new ArrayList<>();
        for (CalibrationItem item : items) {
            if (item.getKind() == CalibrationItem.Kind.Diagnostic) {
                groups.add(item.getGroup());
                assertNull(item.getStep(), "a diagnostic carries out no step");
                assertNull(item.getWay());
                assertEquals(1, item.getParts().size());
                assertEquals(MeasurementForms.name(item.getGroup()), item.getTitle());
            }
            else {
                assertNull(item.getGroup(), item.toString());
            }
        }
        assertEquals(new ArrayList<>(CalibrationPlan.diagnosticGroups()), groups);
        CalibrationItem backlash = null;
        for (CalibrationItem item : items) {
            if (item.getKind() == CalibrationItem.Kind.Measure && item.getStep() == CalibrationStep.XyBacklash) {
                backlash = item;
            }
        }
        assertNotNull(backlash);
        assertEquals(CalibrationStep.Way.Auto, backlash.getWay(), "a step's row says how it is carried out");
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
    public void theSameAdviceAboutSeveralElementsIsOneRowAndTheSetupIssuesAreLeftOut() {
        Calibrate setup = new Calibrate(new Thing("N1"));
        List<Solutions.Issue> issues = List.of(new Align(new Thing("N1")), new Align(new Thing("N2")),
                setup, new Calibrate(new Thing("N2")));
        List<CalibrationItem> items = CalibrationItems.of(null, issues, List.of());
        assertEquals(1, items.size(), items.toString());
        CalibrationItem align = items.get(0);
        assertEquals(CalibrationItem.Kind.Suggestion, align.getKind());
        assertEquals(Translations.translateText("Part-aligned rotation"), align.getTitle());
        assertEquals(List.of("N1", "N2"), List.of(align.getParts().get(0).getSubject(),
                align.getParts().get(1).getSubject()));
        assertEquals(Boolean.FALSE, align.commonValue(false));
        assertEquals(Boolean.TRUE, align.commonValue(true));
        // Advice that does more than write one value, and no step carries out, is the machine
        // settings page's.
        assertTrue(SetupIssues.isSetupIssue(setup));
    }

    @Test
    public void aDismissedSuggestionIsKeptWhereItCanBeTakenBack() throws Exception {
        Align dismissed = new Align(new Thing("N1"));
        dismissed.setState(Solutions.State.Dismissed);
        List<CalibrationItem> items = CalibrationItems.of(null, List.of(dismissed), List.of());
        assertEquals(1, items.size());
        assertEquals(CalibrationItem.Kind.Dismissed, items.get(0).getKind());
        assertTrue(items.get(0).canBeDismissed());
    }

    @Test
    public void whatWasMeasuredComesFirstAndNeitherInformationNorSetupIssuesAreRows() {
        Solutions.Issue information = new Solutions.PlainIssue(new Solutions.Subject() {
        }, "Milestone.", "Done.", Solutions.Severity.Information, null);
        Solutions.Issue setup = new Solutions.PlainIssue(new Solutions.Subject() {
        }, "A setup issue.", "Do this.", Solutions.Severity.Warning, null);
        Pending measured = new Pending("XyBacklash:X", CalibrationStep.XyBacklash, null, "x", List.of(), List.of(),
                null, "", new Date(), "<a/>");
        List<CalibrationItem> items = CalibrationItems.of(null, List.of(information, setup), List.of(measured));
        assertEquals(1, items.size());
        assertEquals(CalibrationItem.Kind.Pending, items.get(0).getKind());
        assertTrue(SetupIssues.isSetupIssue(setup));
        assertFalse(SetupIssues.isSetupIssue(information));
    }
}
