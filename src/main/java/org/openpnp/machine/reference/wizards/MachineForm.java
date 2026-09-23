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

import javax.swing.SwingUtilities;

import org.openpnp.gui.MainFrame;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.model.Length;
import org.openpnp.model.Location;
import org.openpnp.spi.MotionPlanner;
import org.pmw.tinylog.Logger;

/**
 * The machine's own settings: what it does when it is switched on and homed, how it moves, and
 * the two places it keeps - where parts are thrown away and where a new board goes. The wizard it
 * replaces had them as eight check boxes and fields in a grid and the two locations as four bare
 * fields each.
 */
public final class MachineForm {
    private MachineForm() {
    }

    public static class Bean extends AbstractModelObject {
        private final ReferenceMachine machine;

        Bean(ReferenceMachine machine) {
            this.machine = machine;
        }

        public boolean isHomeAfterEnabled() {
            return machine.getHomeAfterEnabled();
        }

        public void setHomeAfterEnabled(boolean home) {
            machine.setHomeAfterEnabled(home);
        }

        public boolean isParkAfterHomed() {
            return machine.isParkAfterHomed();
        }

        public void setParkAfterHomed(boolean park) {
            machine.setParkAfterHomed(park);
        }

        public boolean isSafeZPark() {
            return machine.isSafeZPark();
        }

        public void setSafeZPark(boolean park) {
            machine.setSafeZPark(park);
        }

        public boolean isAutoToolSelect() {
            return machine.isAutoToolSelect();
        }

        public void setAutoToolSelect(boolean select) {
            machine.setAutoToolSelect(select);
        }

        public Length getUnsafeZRoamingDistance() {
            return machine.getUnsafeZRoamingDistance();
        }

        public void setUnsafeZRoamingDistance(Length distance) {
            machine.setUnsafeZRoamingDistance(distance);
        }

        public Class<?> getMotionPlanner() {
            return machine.getMotionPlanner().getClass();
        }

        /**
         * Another kind of motion planner, new with its defaults. The properties column is shown
         * again afterwards: the new planner's sheets are not the old one's.
         */
        public void setMotionPlanner(Class<?> type) {
            if (type == null || type == machine.getMotionPlanner().getClass()) {
                return;
            }
            try {
                machine.setMotionPlanner((MotionPlanner) type.getDeclaredConstructor().newInstance());
            }
            catch (Exception e) {
                Logger.error(e, "Could not make a {} motion planner.", type.getSimpleName());
                return;
            }
            SwingUtilities.invokeLater(() -> {
                MainFrame frame = MainFrame.get();
                if (frame != null && frame.getMachineSetupTab() != null) {
                    frame.getMachineSetupTab().selectCurrentTreePath();
                }
            });
        }

        public boolean isPoolScriptingEngines() {
            return machine.isPoolScriptingEngines();
        }

        public void setPoolScriptingEngines(boolean pool) {
            machine.setPoolScriptingEngines(pool);
        }

        public boolean isAutoLoadMostRecentJob() {
            return machine.isAutoLoadMostRecentJob();
        }

        public void setAutoLoadMostRecentJob(boolean load) {
            machine.setAutoLoadMostRecentJob(load);
        }

        public Location getDiscardLocation() {
            return machine.getDiscardLocation();
        }

        public void setDiscardLocation(Location location) {
            machine.setDiscardLocation(location);
        }

        public Location getDefaultBoardLocation() {
            return machine.getDefaultBoardLocation();
        }

        public void setDefaultBoardLocation(Location location) {
            machine.setDefaultBoardLocation(location);
        }
    }

    public static FormWizard build(ReferenceMachine machine) {
        return Form.of(new Bean(machine)).named("MachineForm.Title") //$NON-NLS-1$
                .section("MachineForm.Start", "power") //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("homeAfterEnabled", "MachineForm.HomeAfterEnabled", "MachineForm.HomeAfterEnabled.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .toggle("parkAfterHomed", "MachineForm.ParkAfterHomed", "MachineForm.ParkAfterHomed.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .toggle("safeZPark", "MachineForm.SafeZPark", "MachineForm.SafeZPark.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .toggle("autoLoadMostRecentJob", "MachineForm.AutoLoadJob", "MachineForm.AutoLoadJob.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .section("MachineForm.Motion", "move") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("motionPlanner", "ReferenceMachineConfigurationWizard.PanelGeneral.MotionPlanningLabel.text", //$NON-NLS-1$ //$NON-NLS-2$
                        new java.util.ArrayList<Class<?>>(machine.getCompatibleMotionPlannerClasses()), null)
                .note("MachineForm.MotionPlanner.Note") //$NON-NLS-1$
                .length("unsafeZRoamingDistance", "ReferenceMachineConfigurationWizard.PanelGeneral.UnsafeZRoamingLabel.text") //$NON-NLS-1$ //$NON-NLS-2$
                .width(120)
                .note("MachineForm.UnsafeZRoaming.Note") //$NON-NLS-1$
                .toggle("autoToolSelect", "MachineForm.AutoToolSelect", "MachineForm.AutoToolSelect.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .section("ReferenceMachineConfigurationWizard.PanelLocations.Border.title", "pin") //$NON-NLS-1$ //$NON-NLS-2$
                .location("discardLocation", "ReferenceMachineConfigurationWizard.PanelLocations.DiscardLocationLabel.text", true) //$NON-NLS-1$ //$NON-NLS-2$
                .locationButtons()
                .location("defaultBoardLocation", "ReferenceMachineConfigurationWizard.PanelLocations.DefaultBoardLocationLabel.text", true) //$NON-NLS-1$ //$NON-NLS-2$
                .locationButtons()
                .section("MachineForm.Scripting", "file").collapsed() //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("poolScriptingEngines", "MachineForm.PoolScripting", "MachineForm.PoolScripting.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .hint("MachineForm.PoolScripting.Hint") //$NON-NLS-1$
                .build();
    }
}
