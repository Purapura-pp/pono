package org.openpnp.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A fiducial vision setting assigned to a part is in use. Only bottom vision used to be counted,
 * so the "assigned to" column showed it unused and it could be deleted from under the part.
 */
public class VisionSettingsUsageTest {
    @TempDir
    Path tempDir;

    private Configuration configuration;

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        configuration = Configuration.get();
        configuration.load();
    }

    @Test
    public void aFiducialVisionSettingAssignedToAPartIsInUse() {
        FiducialVisionSettings fiducialVision = new FiducialVisionSettings("FVS-USAGE-TEST");
        configuration.addVisionSettings(fiducialVision);
        assertFalse(fiducialVision.getUsedIn().stream().anyMatch(h -> "FIDUCIAL-USAGE-TEST".equals(h.getId())));

        Part part = new Part("FIDUCIAL-USAGE-TEST");
        part.setFiducialVisionSettings(fiducialVision);
        configuration.addPart(part);

        assertTrue(fiducialVision.getUsedIn().contains(part));
        assertTrue(fiducialVision.getUsedFiducialVisionIn().contains(part));
        assertFalse(fiducialVision.getUsedBottomVisionIn().contains(part));
    }
}
