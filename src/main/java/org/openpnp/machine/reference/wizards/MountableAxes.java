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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.openpnp.gui.form.Form;
import org.openpnp.model.AbstractModelObject;
import org.openpnp.spi.Axis;
import org.openpnp.spi.base.AbstractAxis;
import org.openpnp.spi.base.AbstractHeadMountable;
import org.openpnp.spi.base.AbstractMachine;

/**
 * The axes a nozzle, a camera or an actuator on the head moves with: the part of their forms
 * that is the same for all three. Each of the four is one of the machine's axes of its kind, or
 * none.
 */
public final class MountableAxes {
    private MountableAxes() {
    }

    /** A form's bean for something on the head, with its four axes. */
    public static class Bean extends AbstractModelObject {
        protected final AbstractHeadMountable mountable;

        protected Bean(AbstractHeadMountable mountable) {
            this.mountable = mountable;
        }

        public AbstractAxis getAxisX() {
            return mountable.getAxisX();
        }

        public void setAxisX(AbstractAxis axis) {
            mountable.setAxisX(axis);
        }

        public AbstractAxis getAxisY() {
            return mountable.getAxisY();
        }

        public void setAxisY(AbstractAxis axis) {
            mountable.setAxisY(axis);
        }

        public AbstractAxis getAxisZ() {
            return mountable.getAxisZ();
        }

        public void setAxisZ(AbstractAxis axis) {
            mountable.setAxisZ(axis);
        }

        public AbstractAxis getAxisRotation() {
            return mountable.getAxisRotation();
        }

        public void setAxisRotation(AbstractAxis axis) {
            mountable.setAxisRotation(axis);
        }
    }

    /** The section of the four axes, for a bean that is a {@link Bean}. */
    public static Form.Builder section(Form.Builder form, AbstractMachine machine) {
        return form.section("MountableAxes.Title", "move") //$NON-NLS-1$ //$NON-NLS-2$
                .choice("axisX", "MountableAxes.X", of(machine, Axis.Type.X), null) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("axisY", "MountableAxes.Y", of(machine, Axis.Type.Y), null) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("axisZ", "MountableAxes.Z", of(machine, Axis.Type.Z), null) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("axisRotation", "MountableAxes.Rotation", of(machine, Axis.Type.Rotation), null); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** None, then the machine's axes of the kind by name. */
    static List<AbstractAxis> of(AbstractMachine machine, Axis.Type type) {
        List<AbstractAxis> axes = new ArrayList<>();
        for (Axis axis : machine.getAxes()) {
            if (axis instanceof AbstractAxis && axis.getType() == type) {
                axes.add((AbstractAxis) axis);
            }
        }
        axes.sort(Comparator.comparing(Axis::getName, Comparator.nullsFirst(Comparator.naturalOrder())));
        axes.add(0, null);
        return axes;
    }
}
