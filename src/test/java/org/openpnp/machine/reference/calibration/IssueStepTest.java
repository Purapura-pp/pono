package org.openpnp.machine.reference.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis.BacklashCompensationMethod;
import org.openpnp.machine.reference.solutions.MachineDiagnostics;
import org.openpnp.machine.reference.solutions.MachineDiagnostics.TestGroup;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults;
import org.openpnp.model.CalibrationStep;
import org.openpnp.model.Configuration;
import org.openpnp.model.Solutions;
import org.openpnp.spi.Axis;

/** Which issues belong to which calibration step, and the search that gathers them. */
public class IssueStepTest {
    @TempDir
    Path tempDir;

    private ReferenceMachine machine;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
        machine = (ReferenceMachine) Configuration.get().getMachine();
    }

    private List<Solutions.Issue> scan() throws Exception {
        List<Solutions.Issue> issues = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> issues.addAll(CalibrationPlan.scan(machine, machine.getSolutions())));
        return issues;
    }

    private static Solutions.Issue tagged(List<Solutions.Issue> issues, CalibrationStep step) {
        for (Solutions.Issue issue : issues) {
            if (issue.getCalibrationStep() == step) {
                return issue;
            }
        }
        return null;
    }

    @Test
    public void taggingLeavesTheIdentityAlone() {
        Solutions.Issue issue = new Solutions.PlainIssue(machine, "An issue.", "A solution.",
                Solutions.Severity.Warning, null);
        String fingerprint = issue.getFingerprint();
        assertNull(issue.getCalibrationStep(), "an issue is not about calibrating until it says so");
        assertSame(issue, issue.withCalibrationStep(CalibrationStep.SubPixel));
        assertEquals(fingerprint, issue.getFingerprint());
        assertSame(machine, issue.getCalibrationSubject(), "about its own subject unless told otherwise");
        Object axis = new Object();
        issue.withCalibrationStep(CalibrationStep.XyBacklash, axis);
        assertSame(axis, issue.getCalibrationSubject());
    }

    @Test
    public void theCalibrationSearchAsksAnUnhomedMachineToHome() throws Exception {
        Solutions.Issue home = tagged(scan(), CalibrationStep.Home);
        assertNotNull(home);
        assertEquals("To continue, the machine must be enabled and homed.", home.getUntranslatedIssue());
        assertSame(machine, home.getCalibrationSubject());
    }

    @Test
    public void aFindingSaysWhichStepItBelongsToAndWhatMeasuredIt() throws Exception {
        ReferenceControllerAxis z = null;
        for (Axis axis : machine.getAxes()) {
            if (axis.getType() == Axis.Type.Z && axis instanceof ReferenceControllerAxis) {
                z = (ReferenceControllerAxis) axis;
            }
        }
        z.setBacklashCompensationMethod(BacklashCompensationMethod.None);
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setZFocus(List.of(new MachineDiagnosticsResults.ZFocus(z.getId(),
                machine.getDefaultHead().getDefaultCamera().getId(), 31.42, 0.008, 0.02, -0.12, 5)));
        machine.getMachineDiagnostics().setLastResults(results);

        Solutions.Issue slack = tagged(scan(), CalibrationStep.ZBacklash);

        assertNotNull(slack);
        assertEquals(TestGroup.ZFocus, ((MachineDiagnostics.Finding) slack).getMeasuredBy());
        assertSame(z, slack.getCalibrationSubject());
        assertTrue(slack instanceof SettingChange, "its change is one setting from one value to another");
    }

    @Test
    public void theCalibrationSearchSeesWhatTheIssuesPageDismissed() throws Exception {
        Solutions.Issue home = tagged(scan(), CalibrationStep.Home);
        machine.getSolutions().setSolutionsIssueDismissed(home, true);

        Solutions.Issue again = tagged(scan(), CalibrationStep.Home);

        assertEquals(Solutions.State.Dismissed, again.getState(), "the record is shared, not copied");
    }
}
