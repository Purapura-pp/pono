package org.openpnp.machine.reference.solutions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.machine.reference.ReferenceHead;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis;
import org.openpnp.machine.reference.axis.ReferenceControllerAxis.BacklashCompensationMethod;
import org.openpnp.machine.reference.camera.AbstractSettlingCamera.SettleMethod;
import org.openpnp.machine.reference.camera.ReferenceCamera;
import org.openpnp.machine.reference.driver.NullDriver;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults.ControllerLimits;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults.FieldOfView;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults.Homing;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults.Motion;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults.Positioning;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults.Rotation;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults.Settling;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Solutions;
import org.openpnp.spi.Axis;

/**
 * Covers what the diagnostics contribute to Issues and Solutions: that a check fires when the
 * setting and the measurement disagree, that accepting writes the measured value, and that
 * nothing is reported about a machine nobody has measured.
 * <p>
 * The English wording of each issue is spelled out here because it is not only display text: the
 * fingerprint that remembers which issues the user dismissed is a hash of it, so changing a word
 * silently forgets every dismissal. A test that has to be edited alongside is the point.
 */
public class MachineDiagnosticsIssuesTest {
    private static final String FEED_RATE_CAPPED =
            "The axis is planned with a feed rate the controller will not allow.";
    private static final String ACCELERATION_CAPPED =
            "The axis is planned with an acceleration the controller will not allow.";
    private static final String RESOLUTION_TOO_FINE =
            "The axis resolution is finer than the smallest step the controller can make.";
    private static final String FEED_RATE_NOT_REACHED =
            "The axis never reaches the feed rate it is planned with.";
    private static final String ACCELERATION_NOT_REACHED =
            "The axis never reaches the acceleration it is planned with.";
    private static final String OFFSET_BELOW_BACKLASH =
            "One-sided backlash compensation is set to less offset than the axis has backlash.";
    private static final String SETTLES_AFTER_THE_WAIT =
            "The camera waits a fixed time that ends before the image has stopped moving.";
    private static final String WAITS_LONGER_THAN_NEEDED =
            "The camera waits considerably longer than the image takes to settle.";
    private static final String ROTATION_UNCOMPENSATED =
            "The rotation axis has backlash and no compensation set.";
    private static final String HOMING_SCATTERS =
            "Homing does not put the machine origin back in the same place.";
    private static final String SCALE_DISAGREES =
            "Units per Pixel does not agree with what the camera sees across its field of view.";
    private static final String CAMERA_IS_NOISY =
            "The camera cannot locate a standing fiducial to within half a pixel from frame to frame.";
    private static final String WAIT_SHORTER_THAN_LATENCY =
            "The camera waits a fixed time shorter than the delay of its own frames.";
    private static final String LOSES_STEPS =
            "The axis loses steps at the speed it is planned with.";
    private static final String Z_HAS_SLACK =
            "The Z axis has slack between moving down and moving up that is not compensated.";
    private static final String MILLIMETRE_IS_NOT_A_MILLIMETRE =
            "A commanded millimetre on the axis is not a millimetre on the table.";
    private static final String AXES_NOT_SQUARE =
            "The X and Y axes are not square to each other.";

    @TempDir
    Path tempDir;

    private ReferenceMachine machine;
    private MachineDiagnostics diagnostics;
    private Solutions solutions;
    private ReferenceHead head;
    private ReferenceCamera camera;
    private ReferenceControllerAxis xAxis;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
        machine = (ReferenceMachine) Configuration.get().getMachine();
        diagnostics = machine.getMachineDiagnostics();
        solutions = machine.getSolutions();
        head = (ReferenceHead) machine.getDefaultHead();
        camera = (ReferenceCamera) head.getDefaultCamera();
        xAxis = addAxis("X", Axis.Type.X);
    }

    @Test
    public void nothingIsReportedAboutAMachineThatWasNeverMeasured() {
        xAxis.setFeedratePerSecond(new Length(1000, LengthUnit.Millimeters));
        xAxis.setBacklashCompensationMethod(BacklashCompensationMethod.OneSidedPositioning);
        camera.setSettleMethod(SettleMethod.FixedTime);
        camera.setSettleTimeMs(1);

        List<String> reported = wordings(null);

        assertFalse(reported.contains(FEED_RATE_CAPPED));
        assertFalse(reported.contains(FEED_RATE_NOT_REACHED));
        assertFalse(reported.contains(OFFSET_BELOW_BACKLASH));
        assertFalse(reported.contains(SETTLES_AFTER_THE_WAIT));
    }

    @Test
    public void aLimitAboveWhatTheControllerAllowsIsReportedAndLowered() throws Exception {
        xAxis.setFeedratePerSecond(new Length(500, LengthUnit.Millimeters));
        xAxis.setAccelerationPerSecond2(new Length(2000, LengthUnit.Millimeters));
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setControllerLimits(
                List.of(new ControllerLimits(xAxis.getId(), null, 300.0, 800.0)));

        issue(results, FEED_RATE_CAPPED).setState(Solutions.State.Solved);
        issue(results, ACCELERATION_CAPPED).setState(Solutions.State.Solved);

        assertEquals(300, millimetres(xAxis.getFeedratePerSecond()));
        assertEquals(800, millimetres(xAxis.getAccelerationPerSecond2()));
    }

    /** A controller limit the axis stays under is what the settings are supposed to look like. */
    @Test
    public void aLimitTheControllerAllowsIsNotReported() {
        xAxis.setFeedratePerSecond(new Length(200, LengthUnit.Millimeters));
        xAxis.setAccelerationPerSecond2(new Length(500, LengthUnit.Millimeters));
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setControllerLimits(
                List.of(new ControllerLimits(xAxis.getId(), null, 300.0, 800.0)));

        List<String> reported = wordings(results);

        assertFalse(reported.contains(FEED_RATE_CAPPED));
        assertFalse(reported.contains(ACCELERATION_CAPPED));
    }

    @Test
    public void undoingAnAcceptedLimitPutsBackWhatWasThere() throws Exception {
        xAxis.setFeedratePerSecond(new Length(500, LengthUnit.Millimeters));
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setControllerLimits(
                List.of(new ControllerLimits(xAxis.getId(), null, 300.0, null)));
        Solutions.Issue issue = issue(results, FEED_RATE_CAPPED);

        issue.setState(Solutions.State.Solved);
        issue.setState(Solutions.State.Open);

        assertEquals(500, millimetres(xAxis.getFeedratePerSecond()));
    }

    @Test
    public void aResolutionFinerThanOneControllerStepIsReportedAndCoarsened() throws Exception {
        xAxis.setResolution(0.0001);
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setControllerLimits(
                List.of(new ControllerLimits(xAxis.getId(), 100.0, null, null)));

        issue(results, RESOLUTION_TOO_FINE).setState(Solutions.State.Solved);

        assertEquals(0.01, xAxis.getResolution(), 1e-9);
    }

    @Test
    public void aResolutionTheControllerCanMakeIsNotReported() {
        xAxis.setResolution(0.01);
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setControllerLimits(
                List.of(new ControllerLimits(xAxis.getId(), 100.0, null, null)));

        assertFalse(wordings(results).contains(RESOLUTION_TOO_FINE));
    }

    @Test
    public void aLimitTheAxisNeverReachesIsReportedAndSetToWhatItReached() throws Exception {
        xAxis.setFeedratePerSecond(new Length(500, LengthUnit.Millimeters));
        xAxis.setAccelerationPerSecond2(new Length(1000, LengthUnit.Millimeters));
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setMotion(List.of(new Motion(xAxis.getId(), 400, 200, 0.02, "mm")));

        issue(results, FEED_RATE_NOT_REACHED).setState(Solutions.State.Solved);
        issue(results, ACCELERATION_NOT_REACHED).setState(Solutions.State.Solved);

        assertEquals(200, millimetres(xAxis.getFeedratePerSecond()));
        assertEquals(400, millimetres(xAxis.getAccelerationPerSecond2()));
    }

    /** Within a tenth of the limit is the machine doing what it was asked, not a finding. */
    @Test
    public void aLimitTheAxisVirtuallyReachesIsNotReported() {
        xAxis.setFeedratePerSecond(new Length(500, LengthUnit.Millimeters));
        xAxis.setAccelerationPerSecond2(new Length(1000, LengthUnit.Millimeters));
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setMotion(List.of(new Motion(xAxis.getId(), 950, 460, 0.02, "mm")));

        List<String> reported = wordings(results);

        assertFalse(reported.contains(FEED_RATE_NOT_REACHED));
        assertFalse(reported.contains(ACCELERATION_NOT_REACHED));
    }

    /**
     * The backlash calibration is the one way the compensation is set: the finding points at it
     * and writes nothing. It used to raise the offset to 1.2 times the backlash on its own.
     */
    @Test
    public void backlashTheOneSidedOffsetDoesNotClearPointsAtTheCalibration() throws Exception {
        xAxis.setBacklashCompensationMethod(BacklashCompensationMethod.OneSidedPositioning);
        xAxis.setBacklashOffset(new Length(0.05, LengthUnit.Millimeters));
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setPositioning(List.of(new Positioning(xAxis.getId(), 0.02, 0.2, null)));

        Solutions.Issue issue = issue(results, OFFSET_BELOW_BACKLASH);
        assertFalse(issue.canBeAccepted(), "a pointer to the backlash calibration");
        assertEquals(org.openpnp.model.CalibrationStep.XyBacklash, issue.getCalibrationStep());
        assertEquals(MachineDiagnostics.TestGroup.XyPositioning,
                ((MachineDiagnostics.Finding) issue).getMeasuredBy());
        assertEquals(0.05, millimetres(xAxis.getBacklashOffset()), 1e-9);
    }

    /**
     * Every method is held to the backlash measured with compensation off: a directional one has
     * to set about half of it or more, and no compensation covers none of it.
     */
    @Test
    public void backlashIsHeldAgainstWhateverMethodCompensatesIt() {
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setPositioning(List.of(new Positioning(xAxis.getId(), 0.02, 0.2, null)));

        xAxis.setBacklashCompensationMethod(BacklashCompensationMethod.DirectionalCompensation);
        xAxis.setBacklashOffset(new Length(0.05, LengthUnit.Millimeters));
        assertTrue(wordings(results).contains(OFFSET_BELOW_BACKLASH));
        xAxis.setBacklashOffset(new Length(0.12, LengthUnit.Millimeters));
        assertFalse(wordings(results).contains(OFFSET_BELOW_BACKLASH));

        xAxis.setBacklashCompensationMethod(BacklashCompensationMethod.None);
        assertTrue(wordings(results).contains(OFFSET_BELOW_BACKLASH));

        results.setPositioning(List.of(new Positioning(xAxis.getId(), 0.005, 0.01, null)));
        assertFalse(wordings(results).contains(OFFSET_BELOW_BACKLASH), "too little to be worth it");
    }

    @Test
    public void aFixedWaitShorterThanTheImageNeedsIsReportedAndLengthened() throws Exception {
        camera.setSettleMethod(SettleMethod.FixedTime);
        camera.setSettleTimeMs(100);
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setSettling(List.of(new Settling(camera.getId(), 0.3, 20)));

        Solutions.Issue issue = issue(results, SETTLES_AFTER_THE_WAIT);
        issue.setState(Solutions.State.Solved);

        assertEquals(Solutions.Severity.Error, issue.getSeverity(),
                "vision measured on a moving image is not a suggestion");
        assertEquals(450, camera.getSettleTimeMs());
    }

    @Test
    public void aFixedWaitFarLongerThanNeededIsSuggestedShorter() throws Exception {
        camera.setSettleMethod(SettleMethod.FixedTime);
        camera.setSettleTimeMs(2000);
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setSettling(List.of(new Settling(camera.getId(), 0.3, 20)));

        Solutions.Issue issue = issue(results, WAITS_LONGER_THAN_NEEDED);
        issue.setState(Solutions.State.Solved);

        assertEquals(Solutions.Severity.Suggestion, issue.getSeverity());
        assertEquals(450, camera.getSettleTimeMs());
    }

    /** With any other method the camera waits until the image stops, so no time is set wrong. */
    @Test
    public void aCameraThatWaitsForTheImageIsNotReported() {
        camera.setSettleMethod(SettleMethod.Maximum);
        camera.setSettleTimeMs(1);
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setSettling(List.of(new Settling(camera.getId(), 0.3, 20)));

        List<String> reported = wordings(results);

        assertFalse(reported.contains(SETTLES_AFTER_THE_WAIT));
        assertFalse(reported.contains(WAITS_LONGER_THAN_NEEDED));
    }

    @Test
    public void rotationBacklashWithoutCompensationIsReportedAndCompensated() throws Exception {
        ReferenceControllerAxis rotation = addAxis("C", Axis.Type.Rotation);
        rotation.setBacklashCompensationMethod(BacklashCompensationMethod.None);
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setRotation(new Rotation(rotation.getId(), -0.5));

        issue(results, ROTATION_UNCOMPENSATED).setState(Solutions.State.Solved);

        assertEquals(BacklashCompensationMethod.DirectionalCompensation,
                rotation.getBacklashCompensationMethod());
        assertEquals(0.5, millimetres(rotation.getBacklashOffset()), 1e-9);
    }

    @Test
    public void rotationBacklashThatIsAlreadyCompensatedIsNotReported() throws Exception {
        ReferenceControllerAxis rotation = addAxis("C", Axis.Type.Rotation);
        rotation.setBacklashCompensationMethod(BacklashCompensationMethod.DirectionalCompensation);
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setRotation(new Rotation(rotation.getId(), -0.5));

        assertFalse(wordings(results).contains(ROTATION_UNCOMPENSATED));
    }

    /**
     * Homing scatter and a Units per Pixel error both have a solution already, in the shape of
     * the visual homing and advanced camera calibration steps. These say what was measured and
     * point at those, so there is one place that performs each calibration.
     */
    @Test
    public void homingScatterPointsAtTheVisualHomingSolutionRatherThanRepeatingIt() {
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setHoming(new Homing(head.getId(), 0.12, 3));

        Solutions.Issue issue = issue(results, HOMING_SCATTERS);

        assertFalse(issue.canBeAccepted(), "this one has nothing of its own to apply");
        assertTrue(issue.getUntranslatedSolution().contains("Enable Visual Homing"),
                "it has to name the solution that does the work");
    }

    @Test
    public void homingScatterIsNotReportedWhenVisualHomingAlreadyHandlesIt() {
        head.setVisualHomingMethod(ReferenceHead.VisualHomingMethod.ResetToFiducialLocation);
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setHoming(new Homing(head.getId(), 0.12, 3));

        assertFalse(wordings(results).contains(HOMING_SCATTERS));
    }

    @Test
    public void aScaleErrorAcrossTheFieldOfViewIsReportedOncePerCamera() {
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setFieldOfView(List.of(new FieldOfView(camera.getId(), "X", 0.012, 0.03),
                new FieldOfView(camera.getId(), "Y", -0.008, 0.02)));

        Solutions.Issue issue = issue(results, SCALE_DISAGREES);

        assertFalse(issue.canBeAccepted());
        assertTrue(issue.getExtendedDescription().contains("+1.20%"),
                "the worse of the two directions is the one worth describing");
    }

    @Test
    public void aScaleErrorTooSmallToMatterIsNotReported() {
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setFieldOfView(List.of(new FieldOfView(camera.getId(), "X", 0.001, 0.001)));

        assertFalse(wordings(results).contains(SCALE_DISAGREES));
    }

    @Test
    public void aNoisyCameraIsReportedWithWhatItCosts() {
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setVisionNoise(List.of(new MachineDiagnosticsResults.VisionNoise(camera.getId(),
                0.83, 0.0108, 2.1, 30, 28.5)));

        Solutions.Issue issue = issue(results, CAMERA_IS_NOISY);

        assertFalse(issue.canBeAccepted(), "lighting is not a setting this can write");
        // The numbers survive translation; the words around them do not.
        assertTrue(issue.getExtendedDescription().contains("0.830 px"));
        assertTrue(issue.getExtendedDescription().contains("2.100 px"));
    }

    @Test
    public void aQuietCameraIsNotReported() {
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setVisionNoise(List.of(new MachineDiagnosticsResults.VisionNoise(camera.getId(),
                0.12, 0.0016, 0.4, 30, 30)));

        assertFalse(wordings(results).contains(CAMERA_IS_NOISY));
    }

    @Test
    public void aSettleWaitShorterThanTheCameraLatencyIsRaisedAndLengthened() throws Exception {
        camera.setSettleMethod(SettleMethod.FixedTime);
        camera.setSettleTimeMs(50);
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setCameraLatency(List.of(new MachineDiagnosticsResults.CameraLatency(
                camera.getId(), 0.180, 30, 5.4)));

        issue(results, WAIT_SHORTER_THAN_LATENCY).setState(Solutions.State.Solved);

        assertTrue(camera.getSettleTimeMs() >= 180,
                "the wait has to cover the latency, got " + camera.getSettleTimeMs());
    }

    @Test
    public void theLatencyIssueStepsAsideOnceSettlingWasMeasured() {
        camera.setSettleMethod(SettleMethod.FixedTime);
        camera.setSettleTimeMs(50);
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setCameraLatency(List.of(new MachineDiagnosticsResults.CameraLatency(
                camera.getId(), 0.180, 30, 5.4)));
        results.setSettling(List.of(new Settling(camera.getId(), 0.4, 100)));
        diagnostics.setLastResults(results);

        List<String> reported = wordings(results);

        assertFalse(reported.contains(WAIT_SHORTER_THAN_LATENCY),
                "the settle issue carries the same conclusion with the better number");
        assertTrue(reported.contains(SETTLES_AFTER_THE_WAIT));
    }

    @Test
    public void lostStepsLowerTheFeedRateToTheHighestCleanSpeed() throws Exception {
        xAxis.setFeedratePerSecond(new Length(400, LengthUnit.Millimeters));
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setLostSteps(List.of(
                new MachineDiagnosticsResults.LostSteps(xAxis.getId(), 0.25, 4000, 0.003, 400),
                new MachineDiagnosticsResults.LostSteps(xAxis.getId(), 0.5, 4000, 0.004, 400),
                new MachineDiagnosticsResults.LostSteps(xAxis.getId(), 0.75, 4000, 0.310, 400),
                new MachineDiagnosticsResults.LostSteps(xAxis.getId(), 1.0, 4000, 1.250, 400)));

        Solutions.Issue issue = issue(results, LOSES_STEPS);
        assertTrue(issue.getExtendedDescription().contains("0.3100 mm"),
                "the first speed that lost steps is the one described");
        issue.setState(Solutions.State.Solved);

        assertEquals(200, millimetres(xAxis.getFeedratePerSecond()),
                "half the tested feed rate: the highest factor that came back clean");
    }

    @Test
    public void anAxisThatArrivedAtEverySpeedIsNotReported() {
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setLostSteps(List.of(
                new MachineDiagnosticsResults.LostSteps(xAxis.getId(), 0.5, 4000, 0.004, 400),
                new MachineDiagnosticsResults.LostSteps(xAxis.getId(), 1.0, 4000, -0.008, 400)));

        assertFalse(wordings(results).contains(LOSES_STEPS));
    }

    @Test
    public void lostStepsAreNotReportedOnceTheAxisWasSlowedBelowThatSpeed() {
        xAxis.setFeedratePerSecond(new Length(150, LengthUnit.Millimeters));
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setLostSteps(List.of(
                new MachineDiagnosticsResults.LostSteps(xAxis.getId(), 0.5, 4000, 0.004, 400),
                new MachineDiagnosticsResults.LostSteps(xAxis.getId(), 1.0, 4000, 0.9, 400)));

        assertFalse(wordings(results).contains(LOSES_STEPS));
    }

    @Test
    public void zSlackWithNoCompensationIsCompensatedInTheDirectionOfTravel() throws Exception {
        ReferenceControllerAxis zAxis = addAxis("Z", Axis.Type.Z);
        zAxis.setBacklashCompensationMethod(BacklashCompensationMethod.None);
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setZFocus(List.of(new MachineDiagnosticsResults.ZFocus(zAxis.getId(),
                camera.getId(), 31.42, 0.008, 0.02, -0.12, 5)));

        issue(results, Z_HAS_SLACK).setState(Solutions.State.Solved);

        assertEquals(BacklashCompensationMethod.DirectionalCompensation,
                zAxis.getBacklashCompensationMethod());
        assertEquals(0.12, millimetres(zAxis.getBacklashOffset()), 1e-9);
    }

    /** Directional compensation with an offset of zero is what most Z axes ship with. */
    @Test
    public void zSlackIsReportedWhenTheOffsetIsFarBelowIt() throws Exception {
        ReferenceControllerAxis zAxis = addAxis("Z", Axis.Type.Z);
        zAxis.setBacklashCompensationMethod(BacklashCompensationMethod.DirectionalCompensation);
        zAxis.setBacklashOffset(new Length(0, LengthUnit.Millimeters));
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setZFocus(List.of(new MachineDiagnosticsResults.ZFocus(zAxis.getId(),
                camera.getId(), 31.42, 0.008, 0.02, 0.09, 5)));

        assertTrue(wordings(results).contains(Z_HAS_SLACK));

        zAxis.setBacklashOffset(new Length(0.08, LengthUnit.Millimeters));
        assertFalse(wordings(results).contains(Z_HAS_SLACK),
                "an offset in the region of the slack is already doing the job");
    }

    @Test
    public void aScaleErrorAgainstTheBoardNamesTheStepsPerMillimetreThatWouldFixIt() {
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setDatum(new MachineDiagnosticsResults.Datum("LumenPnP datum PCB-0004",
                head.getId(), 1.0021, 1.0001, 0.02, -0.4, false, 0.012, 7));
        // The scale is measured through the camera, so it is the camera's X axis that is named.
        String cameraX = camera.getAxis(Axis.Type.X).getId();
        results.setControllerLimits(
                List.of(new ControllerLimits(cameraX, 80.0, 300.0, 800.0)));

        Solutions.Issue issue = issue(results, MILLIMETRE_IS_NOT_A_MILLIMETRE);

        assertFalse(issue.canBeAccepted(), "the firmware is not written from here");
        assertTrue(issue.getExtendedDescription().contains("+0.210%"));
        assertTrue(issue.getExtendedDescription().contains("80.1680"),
                "80 steps per mm scaled by 1.0021: " + issue.getExtendedDescription());
    }

    @Test
    public void axesOutOfSquareAreReportedWithTheTransformFactor() {
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setDatum(new MachineDiagnosticsResults.Datum("LumenPnP datum PCB-0004",
                head.getId(), 1.0001, 0.9999, 0.25, 0.1, false, 0.01, 7));

        Solutions.Issue issue = issue(results, AXES_NOT_SQUARE);

        assertFalse(issue.canBeAccepted());
        assertTrue(issue.getExtendedDescription().contains("-0.004363"),
                "minus the tangent of 0.25 degrees: " + issue.getExtendedDescription());
        List<String> reported = wordings(results);
        assertFalse(reported.contains(MILLIMETRE_IS_NOT_A_MILLIMETRE),
                "a hundredth of a percent is the board, not the machine");
    }

    /** The floor is kept per camera, and a new run replaces only the camera it measured. */
    @Test
    public void theNoiseFloorIsLookedUpByCamera() {
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setVisionNoise(List.of(
                new MachineDiagnosticsResults.VisionNoise("other", 0.9, 0.03, 3, 30, 30),
                new MachineDiagnosticsResults.VisionNoise(camera.getId(), 0.1, 0.001, 0.3, 30, 30)));

        assertEquals(0.1, results.getVisionNoise(camera.getId()).getSdPixels());
        assertEquals(0.9, results.getVisionNoise("other").getSdPixels());
        assertEquals(null, results.getVisionNoise("nobody"));
    }

    /**
     * The fingerprint is what remembers a dismissal, and it is a hash of the wording. If a
     * measured number reached the wording, every new measurement would produce an issue the
     * machine has never seen before and would ask about something already dismissed.
     */
    @Test
    public void theIdentityOfAnIssueDoesNotMoveWithTheMeasurement() {
        xAxis.setFeedratePerSecond(new Length(500, LengthUnit.Millimeters));
        MachineDiagnosticsResults slow = new MachineDiagnosticsResults();
        slow.setMotion(List.of(new Motion(xAxis.getId(), 900, 200, 0.02, "mm")));
        MachineDiagnosticsResults slower = new MachineDiagnosticsResults();
        slower.setMotion(List.of(new Motion(xAxis.getId(), 900, 100, 0.05, "mm")));

        String first = issue(slow, FEED_RATE_NOT_REACHED).getFingerprint();
        String second = issue(slower, FEED_RATE_NOT_REACHED).getFingerprint();

        assertEquals(first, second);
    }

    /** A measurement is of no use if it is gone by the next time the machine is started. */
    @Test
    public void conclusionsSurviveBeingSavedAndLoaded() throws Exception {
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setControllerLimits(
                List.of(new ControllerLimits(xAxis.getId(), 80.0, 300.0, 800.0)));
        results.setMotion(List.of(new Motion(xAxis.getId(), 400, 200, 0.02, "mm")));
        results.setPositioning(List.of(new Positioning(xAxis.getId(), 0.02, 0.2, 0.03)));
        results.setFieldOfView(List.of(new FieldOfView(camera.getId(), "X", 0.012, 0.03)));
        results.setSettling(List.of(new Settling(camera.getId(), 0.3, 20)));
        results.setHoming(new Homing(head.getId(), 0.12, 3));
        results.setRotation(new Rotation(xAxis.getId(), -0.5));
        results.setRun(MachineDiagnostics.TestGroup.Kinematics, 1757570000000L, "somewhere");
        diagnostics.setLastResults(results);
        Configuration.get().save();

        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
        MachineDiagnosticsResults reloaded = ((ReferenceMachine) Configuration.get().getMachine())
                .getMachineDiagnostics().getLastResults();

        assertNotNull(reloaded, "the results were not written to machine.xml at all");
        assertEquals(300, reloaded.getControllerLimits().get(0).getMaxFeedRate());
        assertEquals(200, reloaded.getMotion().get(0).getVelocity());
        assertEquals(0.2, reloaded.getPositioning().get(0).getBacklashMaxMm());
        assertEquals(0.03, reloaded.getPositioning().get(0).getEffectiveResolutionMm());
        assertEquals(0.012, reloaded.getFieldOfView().get(0).getScaleError());
        assertEquals(0.3, reloaded.getSettling().get(0).getSettleSeconds());
        assertEquals(0.12, reloaded.getHoming().getSpreadMm());
        assertEquals(-0.5, reloaded.getRotation().getBacklashDegrees());
        assertEquals(1757570000000L,
                reloaded.getRun(MachineDiagnostics.TestGroup.Kinematics).getMillis());
    }

    /** A limit reported for an axis that has since been deleted is ignored, not thrown over. */
    @Test
    public void conclusionsAboutSomethingThatIsGoneAreIgnored() {
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setControllerLimits(List.of(new ControllerLimits("nosuchaxis", 80.0, 1.0, 1.0)));
        results.setMotion(List.of(new Motion("nosuchaxis", 1, 1, 0.02, "mm")));
        results.setSettling(List.of(new Settling("nosuchcamera", 9.0, 20)));
        results.setHoming(new Homing("nosuchhead", 0.12, 3));

        List<String> reported = wordings(results);

        assertFalse(reported.contains(FEED_RATE_CAPPED));
        assertFalse(reported.contains(SETTLES_AFTER_THE_WAIT));
        assertFalse(reported.contains(HOMING_SCATTERS));
    }

    /**
     * That the machine asks the diagnostics at all, which the rest of this test takes as given.
     * Built on a machine with no camera and no head, because the camera solutions reach for the
     * camera views while looking for issues.
     */
    @Test
    public void theMachineAsksTheDiagnosticsForWhatItMeasured() throws Exception {
        ReferenceMachine bare = new ReferenceMachine();
        Configuration.get().setMachine(bare);
        bare.addDriver(new NullDriver());
        ReferenceControllerAxis axis = new ReferenceControllerAxis();
        axis.setName("X");
        axis.setType(Axis.Type.X);
        axis.setLetter("X");
        axis.setDriver(bare.getDrivers().get(0));
        axis.setFeedratePerSecond(new Length(500, LengthUnit.Millimeters));
        bare.addAxis(axis);
        MachineDiagnosticsResults results = new MachineDiagnosticsResults();
        results.setControllerLimits(List.of(new ControllerLimits(axis.getId(), null, 300.0, null)));
        bare.getMachineDiagnostics().setLastResults(results);
        Solutions bareSolutions = bare.getSolutions();
        bareSolutions.setTargetMilestone(Solutions.Milestone.Calibration);

        bareSolutions.findIssues();
        bareSolutions.publishIssues();

        List<String> reported = new ArrayList<>();
        for (Solutions.Issue issue : bareSolutions.getIssues()) {
            reported.add(issue.getUntranslatedIssue());
        }
        assertTrue(reported.contains(FEED_RATE_CAPPED), "reported instead: " + reported);
    }

    private ReferenceControllerAxis addAxis(String letter, Axis.Type type) throws Exception {
        ReferenceControllerAxis axis = new ReferenceControllerAxis();
        axis.setName(letter);
        axis.setType(type);
        axis.setLetter(letter);
        axis.setDriver(machine.getDrivers().get(0));
        machine.addAxis(axis);
        return axis;
    }

    /** The one issue carrying this wording, through the same path Find Issues takes. */
    private Solutions.Issue issue(MachineDiagnosticsResults results, String wording) {
        List<Solutions.Issue> found = new ArrayList<>();
        for (Solutions.Issue issue : report(results)) {
            if (issue.getUntranslatedIssue().equals(wording)) {
                found.add(issue);
            }
        }
        assertEquals(1, found.size(),
                "issues reading \"" + wording + "\", among " + wordings(results));
        return found.get(0);
    }

    private List<String> wordings(MachineDiagnosticsResults results) {
        List<String> wordings = new ArrayList<>();
        for (Solutions.Issue issue : report(results)) {
            wordings.add(issue.getUntranslatedIssue());
        }
        return wordings;
    }

    /**
     * The issues the machine holds after being told these conclusions.
     * <p>
     * The scan that opens the issue list is run at the Welcome milestone, because from the next
     * one on the camera solutions reach for the camera views while looking for issues and a
     * headless test has none. The diagnostics are then asked at the milestone they report at.
     * {@link #theMachineAsksTheDiagnosticsForWhatItMeasured} covers the path that Find Issues
     * itself takes.
     */
    private List<Solutions.Issue> report(MachineDiagnosticsResults results) {
        diagnostics.setLastResults(results);
        solutions.setTargetMilestone(Solutions.Milestone.Welcome);
        solutions.findIssues();
        solutions.setTargetMilestone(Solutions.Milestone.Calibration);
        diagnostics.setMachine(machine).findIssues(solutions);
        solutions.publishIssues();
        return solutions.getIssues();
    }

    private static double millimetres(Length length) {
        return length.convertToUnits(LengthUnit.Millimeters).getValue();
    }
}
