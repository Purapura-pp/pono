package org.openpnp.gui.machinesettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.openpnp.model.Solutions;
import org.openpnp.spi.Axis;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Driver;
import org.openpnp.spi.Feeder;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.NozzleTip;

/** The issues the machine settings page shows rather than the calibration page, and where. */
public class SetupIssuesTest {
    /** Advice that is accepted, about something of a kind. */
    private static final class Advice extends Solutions.Issue {
        Advice(Solutions.Subject subject, Solutions.Severity severity) {
            super(subject, "An issue.", "A solution.", severity, null);
        }
    }

    private static Solutions.Subject subject(Class<?> type) {
        return (Solutions.Subject) mock(type, withSettings().extraInterfaces(Solutions.Subject.class));
    }

    private static Solutions.Issue about(Class<?> type) {
        return new Advice(subject(type), Solutions.Severity.Warning);
    }

    @Test
    public void anIssueIsShownInTheTopicItsSubjectIsSetIn() {
        assertEquals(MachineSettingsPanel.CONNECTION, SetupIssues.topicOf(about(Driver.class)));
        assertEquals(MachineSettingsPanel.CAMERAS, SetupIssues.topicOf(about(Camera.class)));
        assertEquals(MachineSettingsPanel.NOZZLES, SetupIssues.topicOf(about(Nozzle.class)));
        assertEquals(MachineSettingsPanel.NOZZLES, SetupIssues.topicOf(about(NozzleTip.class)));
        assertEquals(MachineSettingsPanel.MOTION, SetupIssues.topicOf(about(Axis.class)));
        assertEquals(MachineSettingsPanel.OVERVIEW, SetupIssues.topicOf(about(Feeder.class)),
                "what no topic sets is in the overview");
    }

    @Test
    public void aSetupIssueIsOneNoCalibrationCarriesOutAndThatIsMoreThanOneValue() throws Exception {
        Solutions.Issue setup = about(Driver.class);
        Solutions.Issue information = new Advice(subject(Driver.class), Solutions.Severity.Information);
        Solutions.Issue oneValue = about(Driver.class).withChange("Baud", () -> 9600, () -> 115200);
        assertTrue(SetupIssues.isSetupIssue(setup));
        assertFalse(SetupIssues.isSetupIssue(information), "information is not for anyone to fix");
        assertFalse(SetupIssues.isSetupIssue(oneValue), "one value to write is a calibration suggestion");

        List<Solutions.Issue> all = List.of(setup, information, oneValue);
        assertEquals(List.of(setup), SetupIssues.open(all, MachineSettingsPanel.CONNECTION));
        assertEquals(List.of(), SetupIssues.open(all, MachineSettingsPanel.CAMERAS));

        setup.setState(Solutions.State.Dismissed);

        assertEquals(List.of(), SetupIssues.open(all, MachineSettingsPanel.CONNECTION));
        assertEquals(List.of(setup), SetupIssues.dismissed(all, MachineSettingsPanel.CONNECTION));
    }
}
