package org.openpnp.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.openpnp.gui.support.DisplayNames;
import org.openpnp.gui.support.PipelineStages;
import org.openpnp.vision.pipeline.CvPipeline;
import org.openpnp.vision.pipeline.stages.BlurGaussian;
import org.openpnp.vision.pipeline.stages.DrawRotatedRects;
import org.openpnp.vision.pipeline.stages.FindContours;
import org.openpnp.vision.pipeline.stages.ImageCapture;
import org.openpnp.vision.pipeline.stages.ImageRecall;
import org.openpnp.vision.pipeline.stages.MaskCircle;
import org.openpnp.vision.pipeline.stages.MinAreaRect;
import org.openpnp.vision.pipeline.stages.ParameterNumeric;
import org.openpnp.vision.pipeline.stages.Threshold;

/**
 * The vision page: a pipeline in a line is what it does, "阈值 › 找轮廓 › 最小外接矩形", and the
 * built-in settings' names are the user's language.
 */
public class VisionPageTest {
    private static String name(Class<?> type) {
        return DisplayNames.typeName(type);
    }

    @Test
    public void aPipelineIsSummedUpByTheStagesThatLook() {
        CvPipeline pipeline = new CvPipeline();
        pipeline.add(new ImageCapture());
        pipeline.add(new ParameterNumeric());
        pipeline.add(new BlurGaussian());
        pipeline.add(new MaskCircle());
        pipeline.add(new Threshold());
        pipeline.add(new FindContours());
        pipeline.add(new FindContours());
        pipeline.add(new MinAreaRect());
        pipeline.add(new ImageRecall());
        pipeline.add(new DrawRotatedRects());
        assertEquals(List.of(name(Threshold.class), name(FindContours.class), name(MinAreaRect.class)),
                PipelineStages.summary(pipeline));
    }

    @Test
    public void aPipelineThatOnlyPreparesShowsWhatItPrepares() {
        CvPipeline pipeline = new CvPipeline();
        pipeline.add(new ImageCapture());
        pipeline.add(new BlurGaussian());
        assertEquals(List.of(name(BlurGaussian.class)), PipelineStages.summary(pipeline));

        CvPipeline plumbing = new CvPipeline();
        plumbing.add(new ImageCapture());
        assertEquals(List.of(name(ImageCapture.class)), PipelineStages.summary(plumbing));
    }

    @Test
    public void builtInSettingsAreNamedInTheUsersLanguage() {
        for (String builtIn : new String[] { "- Stock Bottom Vision Settings -",
                "- Rectlinear Symmetry Bottom Vision Settings -", "- Whole Part Body Bottom Vision Settings -",
                "- Stock Fiducial Vision Settings -", "- Footprint Fiducial Vision Settings -",
                "- Default Machine Bottom Vision -", "- Default Machine Fiducial Locator -" }) {
            String shown = DisplayNames.visionSettingsName(builtIn);
            assertEquals(false, shown.startsWith("- ") || shown.startsWith("!"), builtIn + " is shown as " + shown);
        }
        assertEquals("My settings", DisplayNames.visionSettingsName("My settings"));
    }
}
