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

package org.openpnp.machine.reference.wizards;

import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.machine.reference.ReferencePnpJobProcessor;
import org.openpnp.machine.reference.ReferencePnpJobProcessor.JobOrderHint;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.spi.PnpJobPlanner.Strategy;

/**
 * How a job is worked through: the order of the placements, when to change nozzle tips, how
 * often to try again, and when a feeder that keeps failing is given up on.
 */
public final class JobProcessorForm {
    private JobProcessorForm() {
    }

    public static class Bean extends AbstractModelObject {
        private final ReferencePnpJobProcessor processor;

        Bean(ReferencePnpJobProcessor processor) {
            this.processor = processor;
        }

        public JobOrderHint getJobOrder() {
            return processor.getJobOrder();
        }

        public void setJobOrder(JobOrderHint order) {
            processor.setJobOrder(order);
        }

        public Strategy getStrategy() {
            return processor.planner.getStrategy();
        }

        public void setStrategy(Strategy strategy) {
            processor.planner.setStrategy(strategy);
        }

        public boolean isOptimizeMultipleNozzles() {
            return processor.isOptimizeMultipleNozzles();
        }

        public void setOptimizeMultipleNozzles(boolean optimize) {
            processor.setOptimizeMultipleNozzles(optimize);
        }

        public boolean isPreRotateAllNozzles() {
            return processor.isPreRotateAllNozzles();
        }

        public void setPreRotateAllNozzles(boolean preRotate) {
            processor.setPreRotateAllNozzles(preRotate);
        }

        /** Attempts, not retries: the property's name says otherwise, three is one and two more. */
        public int getMaxVisionRetries() {
            return processor.getMaxVisionRetries();
        }

        public void setMaxVisionRetries(int attempts) {
            processor.setMaxVisionRetries(attempts);
        }

        public int getMaxPlacementRetries() {
            return processor.getMaxPlacementRetries();
        }

        public void setMaxPlacementRetries(int attempts) {
            processor.setMaxPlacementRetries(attempts);
        }

        public int getFeederFaultLimit() {
            return processor.getFeederFaultLimit();
        }

        public void setFeederFaultLimit(int limit) {
            processor.setFeederFaultLimit(limit);
        }

        public int getFeederFaultWindowSize() {
            return processor.getFeederFaultWindowSize();
        }

        public void setFeederFaultWindowSize(int size) {
            processor.setFeederFaultWindowSize(size);
        }

        public boolean isSteppingToNextMotion() {
            return processor.isSteppingToNextMotion();
        }

        public void setSteppingToNextMotion(boolean stepping) {
            processor.setSteppingToNextMotion(stepping);
        }
    }

    public static FormWizard build(ReferencePnpJobProcessor processor) {
        return Form.of(new Bean(processor)).named("JobProcessorForm.Title") //$NON-NLS-1$
                .section("JobProcessorForm.Order", "list") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("jobOrder", "MachineSetup.JobProcessors.ReferencePnpJobProcessor.Label.JobOrder", //$NON-NLS-1$ //$NON-NLS-2$
                        JobOrderHint.class)
                .note("JobProcessorForm.JobOrder.Note") //$NON-NLS-1$
                .choice("strategy", "MachineSetup.JobProcessors.ReferencePnpJobProcessor.lblPlannerStrategy.text", //$NON-NLS-1$ //$NON-NLS-2$
                        Strategy.class)
                .note("JobProcessorForm.Strategy.Note") //$NON-NLS-1$
                .toggle("optimizeMultipleNozzles", //$NON-NLS-1$
                        "ReferencePnpJobProcessorConfigurationWizard.lblOptimizeMultipleNozzles.text", //$NON-NLS-1$
                        "JobProcessorForm.OptimizeNozzles.Note") //$NON-NLS-1$
                .toggle("preRotateAllNozzles", "ReferencePnpJobProcessorConfigurationWizard.lblPreRotateAllNozzles.text", //$NON-NLS-1$ //$NON-NLS-2$
                        "JobProcessorForm.PreRotate.Note") //$NON-NLS-1$
                .hint("JobProcessorForm.PreRotate.Hint") //$NON-NLS-1$
                .section("JobProcessorForm.Retries", "refresh") //$NON-NLS-1$ //$NON-NLS-2$
                .integer("maxVisionRetries", "MachineSetup.JobProcessors.ReferencePnpJobProcessor.Label.MaxVisionRetries") //$NON-NLS-1$ //$NON-NLS-2$
                .unit("JobProcessorForm.Times").width(120) //$NON-NLS-1$
                .validate(JobProcessorForm::atLeastOne, "JobProcessorForm.AtLeastOne") //$NON-NLS-1$
                .integer("maxPlacementRetries", "MachineSetup.JobProcessors.ReferencePnpJobProcessor.Label.MaxPlacementRetries") //$NON-NLS-1$ //$NON-NLS-2$
                .unit("JobProcessorForm.Times").width(120) //$NON-NLS-1$
                .validate(JobProcessorForm::atLeastOne, "JobProcessorForm.AtLeastOne") //$NON-NLS-1$
                .note("JobProcessorForm.Attempts.Note") //$NON-NLS-1$
                .section("JobProcessorForm.FeederFaults", "feeder") //$NON-NLS-1$ //$NON-NLS-2$
                .integer("feederFaultLimit", "MachineSetup.JobProcessors.ReferencePnpJobProcessor.Label.FeederFaultLimit") //$NON-NLS-1$ //$NON-NLS-2$
                .unit("JobProcessorForm.Times").width(120) //$NON-NLS-1$
                .integer("feederFaultWindowSize", "MachineSetup.JobProcessors.ReferencePnpJobProcessor.Label.FeederFaultWindowSize") //$NON-NLS-1$ //$NON-NLS-2$
                .unit("JobProcessorForm.Picks").width(120) //$NON-NLS-1$
                .note("JobProcessorForm.FeederFaults.Note") //$NON-NLS-1$
                .section("JobProcessorForm.Stepping", "step").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("steppingToNextMotion", "ReferencePnpJobProcessorConfigurationWizard.lblStepsMotion.text", //$NON-NLS-1$ //$NON-NLS-2$
                        "JobProcessorForm.StepMotion.Note") //$NON-NLS-1$
                .build();
    }

    /** One attempt at the least: a number that is not one is the field's own error. */
    private static boolean atLeastOne(Object text) {
        try {
            return Integer.parseInt(String.valueOf(text).trim()) >= 1;
        }
        catch (NumberFormatException e) {
            return true;
        }
    }
}
