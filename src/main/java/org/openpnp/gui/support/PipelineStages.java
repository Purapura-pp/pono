/*
 * Copyright (C) 2026 Pono
 * 
 * This file is part of OpenPnP.
 * 
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * For more information about OpenPnP visit http://openpnp.org
 */

package org.openpnp.gui.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.openpnp.vision.pipeline.CvPipeline;
import org.openpnp.vision.pipeline.CvStage;

/**
 * What a pipeline does, in a line: its enabled stages by what they do, "阈值 › 找轮廓 › 最小外接矩形",
 * as the mockups write it. The stages that only fetch, store, draw or expose a parameter are left
 * out - the stock bottom vision pipeline has fifteen stages and does four things.
 */
public final class PipelineStages {
    private PipelineStages() {
    }

    /** Stages that move images and results about rather than look at them. */
    private static final Set<String> PLUMBING = Set.of("ImageCapture", "ImageRecall", "ImageRead", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            "ImageWrite", "ImageWriteDebug", "ParameterBool", "ParameterNumeric", "SetColor", "SetResult", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
            "SetModel", "ReadModelProperty", "ComposeResult", "ActuatorWrite", "ConvertColor"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$

    /** Stages that prepare the image for the ones that look at it. */
    private static boolean preparing(String type) {
        return type.startsWith("Blur") || type.startsWith("Mask") || type.startsWith("Histogram") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                || type.equals("Normalize"); //$NON-NLS-1$
    }

    /**
     * The stages that do the looking, by their display names, a stage used twice in a row once;
     * with the ones that prepare the image where there are no others, and every enabled stage
     * where there is nothing else.
     */
    public static List<String> summary(CvPipeline pipeline) {
        List<String> all = new ArrayList<>();
        List<String> working = new ArrayList<>();
        List<String> looking = new ArrayList<>();
        if (pipeline != null) {
            for (CvStage stage : pipeline.getStages()) {
                if (!stage.isEnabled()) {
                    continue;
                }
                String type = stage.getClass().getSimpleName();
                String name = DisplayNames.typeName(stage.getClass());
                add(all, name);
                if (!PLUMBING.contains(type) && !type.startsWith("Draw")) { //$NON-NLS-1$
                    add(working, name);
                    if (!preparing(type)) {
                        add(looking, name);
                    }
                }
            }
        }
        return !looking.isEmpty() ? looking : !working.isEmpty() ? working : all;
    }

    private static void add(List<String> names, String name) {
        if (names.isEmpty() || !names.get(names.size() - 1).equals(name)) {
            names.add(name);
        }
    }

    /** The summary as one line. */
    public static String text(CvPipeline pipeline) {
        return String.join(" \u203a ", summary(pipeline)); //$NON-NLS-1$
    }
}
