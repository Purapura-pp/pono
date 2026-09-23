package org.openpnp.spi.base;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JOptionPane;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.model.AxesLocation;
import org.openpnp.model.Configuration;
import org.openpnp.model.Length;
import org.openpnp.spi.ControllerAxis;
import org.openpnp.spi.Driver;
import org.openpnp.spi.Machine;
import org.openpnp.spi.PropertySheetHolder;
import org.simpleframework.xml.Attribute;

public abstract class AbstractDriver extends AbstractMachineElement implements Driver {

    @Attribute(required = false) 
    protected String id;

    @Attribute(required = false)
    protected String name;

    public AbstractDriver() {
        this.id = Configuration.createId("DRV");
        this.name = getClass().getSimpleName();
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        Object oldValue = this.name;
        this.name = name;
        firePropertyChange("name", oldValue, name);
    }

    @Override
    public boolean isSupportingPreMove() {
        return false;
    }


    @Override
    public Length getMinimumRate(int order) {
        // Get the minimum of all the axes' rate limits. 
        double rate = Double.POSITIVE_INFINITY;
        Machine machine = getMachine();
        for (ControllerAxis axis : getAxes(machine)) {
            double axisRate = axis.getMotionLimit(order);
            if (rate > axisRate && axisRate != 0) {
                rate = axisRate;
            }
        }
        // Take it at minimum planner speed.
        rate *= Math.pow(machine.getMotionPlanner().getMinimumSpeed(), order);
        // Make it the next lower decimal digit in driver units.
        double driverUnitFactor = Length.convertToUnits(1, AxesLocation.getUnits(), getUnits());
        rate = Math.pow(10, Math.floor(Math.log10(rate*driverUnitFactor)))/driverUnitFactor;
        if (!Double.isFinite(rate)) {
            rate = 1;
        }
        return new Length(rate, AxesLocation.getUnits());
    }

    @Override
    public Length getFeedRatePerSecond() {
        // Default implementation for feeders that don't implement an extra feed-rate. 
        // The axes' fee-rate will be used.
        return new Length(0, getUnits());
    }

    @Override
    public String toString() {
        return getName();
    }

    public List<ControllerAxis> getAxes(Machine machine) {
        List<ControllerAxis> list = new ArrayList<>(); 
        for (org.openpnp.spi.Axis axis : machine.getAxes()) {
            if (axis instanceof ControllerAxis) {
                if (((ControllerAxis) axis).getDriver() == this) {
                    list.add((ControllerAxis) axis);
                }
            }
        }
        return list;
    }

    @Override
    public PropertySheet[] getPropertySheets() {
        return new PropertySheet[] {
                new PropertySheetWizardAdapter(getConfigurationWizard()),
        };
    }

    @Override
    public Action[] getPropertySheetHolderActions() {
        return new Action[] { deleteAction, permutateUpAction, permutateDownAction };
    }

    @SuppressWarnings("serial")
    public Action deleteAction = new AbstractAction(Translations.getString("AbstractDriver.Action.DeleteDriver")) { //$NON-NLS-1$
        {
            putValue(SMALL_ICON, Icons.delete);
            putValue(NAME, Translations.getString("AbstractDriver.Action.DeleteDriver")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("AbstractDriver.Action.DeleteDriver.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            if (org.openpnp.gui.shell.Dialogs.confirmDelete(MainFrame.get(), "Dialogs.Kind.Drivers", //$NON-NLS-1$
                    java.util.List.of(getName()))) {
                getMachine().removeDriver(AbstractDriver.this);
            }
        }
    };

    @SuppressWarnings("serial")
    public Action permutateUpAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.arrowUp);
            putValue(NAME, Translations.getString("AbstractDriver.Action.PermutateUpDriver")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString("AbstractDriver.Action.PermutateUpDriver.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            getMachine().permutateDriver(AbstractDriver.this, -1);
        }
    };

    @SuppressWarnings("serial")
    public Action permutateDownAction = new AbstractAction() {
        {
            putValue(SMALL_ICON, Icons.arrowDown);
            putValue(NAME, Translations.getString("AbstractDriver.Action.PermutateDownDriver")); //$NON-NLS-1$
            putValue(SHORT_DESCRIPTION, Translations.getString(
                    "AbstractDriver.Action.PermutateDownDriver.Description")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            getMachine().permutateDriver(AbstractDriver.this, +1);
        }
    };

    @Override
    public Icon getPropertySheetHolderIcon() {
        return Icons.driver;
    }

    @Override
    public PropertySheetHolder[] getChildPropertySheetHolders() {
        return null;
    }

    @Override
    public String getPropertySheetHolderTitle() {
        return org.openpnp.gui.support.DisplayNames.title(this, getName());
    }

    public void createDefaults() throws Exception  {}

    /**
     * Migrates the driver for the new global axes implementation. 
     * 
     * Is marked a deprecated as it can be removed along with the old GcodeDriver Axes implementation, 
     * once migration of users is expected to be complete.  
     * 
     * @param machine
     * @throws Exception
     */
    @Deprecated
    public
    void migrateDriver(Machine machine) throws Exception {}
}

