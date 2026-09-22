package org.openpnp.machine.reference.solutions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.solutions.MachineDiagnostics.CompensationBasis;
import org.openpnp.machine.reference.solutions.MachineDiagnosticsResults.Datum;
import org.openpnp.model.Configuration;

/**
 * A compensation is made from the median of the recent board readings under the current
 * compensation, and judged against their scatter; readings under another compensation, or too
 * noisy to trust, do not count.
 */
public class CompensationBasisTest {
    @TempDir
    Path tempDir;

    private MachineDiagnostics diagnostics;
    private MachineDiagnosticsResults results;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
        ReferenceMachine machine = (ReferenceMachine) Configuration.get().getMachine();
        diagnostics = machine.getMachineDiagnostics();
        results = new MachineDiagnosticsResults();
        diagnostics.setLastResults(results);
    }

    private Datum reading(double scaleX, double scaleY, double shear, double residual, int generation) {
        Datum d = new Datum("board", "H1", scaleX, scaleY, shear, 0.3, false, residual, 7);
        d.setGeneration(generation);
        d.setMillis(System.currentTimeMillis());
        results.setDatum(d);
        results.addDatumToHistory(d);
        return d;
    }

    @Test
    public void theBasisIsTheMedianOfTheReadingsAndTheToleranceTheirScatter() {
        reading(1.01290, 1.01020, 0.196, 0.018, 0);
        reading(1.01340, 1.00970, 0.067, 0.016, 0);
        reading(1.01350, 1.01090, 0.077, 0.020, 0);

        CompensationBasis basis = diagnostics.getCompensationBasis();

        assertNotNull(basis);
        assertEquals(3, basis.readings);
        assertEquals(1.01340, basis.scaleX, 1e-9);
        assertEquals(1.01020, basis.scaleY, 1e-9);
        assertEquals(0.077, basis.shearDegrees, 1e-9);
        // Three readings do not say what the scatter is: the line is 0.1 % whatever they show,
        // and no tighter, because the seventh session's three agreed to 0.019 % and the machine
        // then read 0.09 % away from a compensation that was right.
        double sdX = basis.sdScaleX;
        assertTrue(sdX < 0.0004, "these three agree closely in X: sd " + sdX);
        assertEquals(0.001, basis.tolerance(sdX), 1e-12);
        // Y scatters by 0.06 %, more than the floor: 2.5 times that.
        assertTrue(basis.sdScaleY > 0.0004, "Y: sd " + basis.sdScaleY);
        assertEquals(2.5 * basis.sdScaleY, basis.tolerance(basis.sdScaleY), 1e-12);
        // Three readings of squareness 0.07 to 0.2 degrees apart do not agree on it, and the
        // squareness line is 2.5 times that scatter, wider than its 0.1 degree floor.
        assertFalse(basis.squarenessIsSettled());
        assertTrue(basis.sdShearDegrees > 0.04);
        assertEquals(2.5 * basis.sdShearDegrees, basis.shearTolerance(), 1e-12);
    }

    /** With six readings the scatter is known, and the line is 2.5 times it, no tighter than 0.05 %. */
    @Test
    public void sixReadingsKnowTheirScatterAndSetTheLineFromIt() {
        double[] xs = { 0.99821, 0.99841, 0.99796, 0.99783, 0.99863, 0.99927, 0.99842, 0.99791 };
        for (double x : xs) {
            reading(x, 0.9985, 0.02, 0.015, 0);
        }

        CompensationBasis basis = diagnostics.getCompensationBasis();

        assertEquals(8, basis.readings);
        // The eight quiet X readings of the sixth session scatter by 0.046 %.
        assertEquals(0.00046, basis.sdScaleX, 0.00003);
        assertEquals(2.5 * basis.sdScaleX, basis.tolerance(basis.sdScaleX), 1e-12);
        // Y did not scatter at all here; the line does not go under 0.05 %.
        assertEquals(0.0005, basis.tolerance(basis.sdScaleY), 1e-12);
        // And never over 0.3 %.
        assertEquals(0.003, basis.tolerance(0.01), 1e-12);
        // Shear: 2.5 times its scatter, between 0.1 and 0.3 degrees.
        assertEquals(0.1, basis.shearTolerance(), 1e-12);
    }

    @Test
    public void oneReadingHasNoScatterAndGetsTheDefaultTolerance() {
        reading(1.0136, 1.0124, 0.147, 0.03, 0);

        CompensationBasis basis = diagnostics.getCompensationBasis();

        assertEquals(1, basis.readings);
        assertTrue(Double.isNaN(basis.sdScaleX));
        assertEquals(0.001, basis.tolerance(basis.sdScaleX), 1e-12);
        assertEquals(0.1, basis.shearTolerance(), 1e-12);
        assertFalse(basis.squarenessIsSettled(), "one reading cannot agree with itself");
    }

    @Test
    public void readingsUnderAnotherCompensationOrTooNoisyDoNotCount() {
        reading(1.0136, 1.0124, 0.1, 0.02, 0);
        reading(1.0130, 1.0120, 0.1, 0.02, 0);
        results.setCompensationGeneration(1);
        reading(0.9981, 0.9976, 0.03, 0.017, 1);
        reading(1.0000, 0.9985, 0.12, 0.058, 1); // drift-wrecked: residual over the limit

        CompensationBasis basis = diagnostics.getCompensationBasis();

        assertEquals(1, basis.readings, "only the clean reading of the current generation");
        assertEquals(0.9981, basis.scaleX, 1e-9);

        results.removeDatumHistory(1);
        results.setCompensationGeneration(0);
        assertEquals(2, diagnostics.getCompensationBasis().readings);
    }

    @Test
    public void squarenessCountsAsSettledWhenTheReadingsAgree() {
        reading(1.0, 1.0, 0.150, 0.02, 0);
        reading(1.0, 1.0, 0.140, 0.02, 0);
        reading(1.0, 1.0, 0.160, 0.02, 0);

        assertTrue(diagnostics.getCompensationBasis().squarenessIsSettled());
    }

    @Test
    public void theHistoryKeepsTheLastEight() {
        for (int i = 0; i < 12; i++) {
            reading(1.0 + i * 0.0001, 1.0, 0, 0.01, 0);
        }
        assertEquals(8, results.getDatumHistory().size());
        assertEquals(1.0011, results.getDatumHistory().get(7).getScaleX(), 1e-12);
    }

    @Test
    public void nothingRecordedIsNoBasis() {
        assertNull(diagnostics.getCompensationBasis());
    }
}
