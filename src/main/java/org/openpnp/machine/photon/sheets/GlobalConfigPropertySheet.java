package org.openpnp.machine.photon.sheets;

import org.openpnp.Translations;
import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.machine.photon.sheets.gui.PhotonForms;
import org.openpnp.spi.Machine;
import org.openpnp.spi.PropertySheetHolder;

import javax.swing.*;

public class GlobalConfigPropertySheet implements PropertySheetHolder.PropertySheet {
    private final Machine machine;

    public GlobalConfigPropertySheet(Machine machine) {
        this.machine = machine;
    }

    @Override
    public String getPropertySheetTitle() {
        return Translations.getString("PhotonForms.Global"); //$NON-NLS-1$
    }

    @Override
    public JPanel getPropertySheetPanel() {
        return new PropertySheetWizardAdapter(PhotonForms.global(machine)).getPropertySheetPanel();
    }
}
