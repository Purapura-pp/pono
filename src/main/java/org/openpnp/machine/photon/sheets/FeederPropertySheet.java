package org.openpnp.machine.photon.sheets;

import org.openpnp.Translations;
import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.machine.photon.PhotonFeeder;
import org.openpnp.machine.photon.sheets.gui.PhotonForms;
import org.openpnp.spi.PropertySheetHolder;

import javax.swing.*;

public class FeederPropertySheet implements PropertySheetHolder.PropertySheet {
    private final PhotonFeeder feeder;

    public FeederPropertySheet(PhotonFeeder feeder) {
        this.feeder = feeder;
    }

    @Override
    public String getPropertySheetTitle() {
        return Translations.getString("PhotonForms.Feeder"); //$NON-NLS-1$
    }

    @Override
    public JPanel getPropertySheetPanel() {
        return new PropertySheetWizardAdapter(PhotonForms.feeder(feeder)).getPropertySheetPanel();
    }
}
