/*
 * Copyright (C) 2019-2020 <mark@makr.zone>
 * based on the ReferenceStripFeederConfigurationWizard 
 * Copyright (C) 2011 Jason von Nieda <jason@vonnieda.org>
 * 
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

package org.openpnp.machine.reference.feeder.wizards;

import java.awt.Color;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;

import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.components.ComponentDecorators;
import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.gui.support.DoubleConverter;
import org.openpnp.gui.support.Helpers;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.IdentifiableListCellRenderer;
import org.openpnp.gui.support.IntegerConverter;
import org.openpnp.gui.support.LengthConverter;
import org.openpnp.gui.support.MutableLocationProxy;
import org.openpnp.gui.support.PartsComboBoxModel;
import org.openpnp.machine.reference.feeder.BlindsFeeder;
import org.openpnp.machine.reference.feeder.BlindsFeeder.OcrAction;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Part;
import org.openpnp.spi.Camera;
import org.openpnp.spi.HeadMountable;
import org.openpnp.util.UiUtils;

import com.jgoodies.forms.layout.ColumnSpec;
import com.jgoodies.forms.layout.FormLayout;
import com.jgoodies.forms.layout.FormSpecs;
import com.jgoodies.forms.layout.RowSpec;

@SuppressWarnings("serial")
public class BlindsFeederConfigurationWizard extends AbstractConfigurationWizard {
    private final BlindsFeeder feeder;

    public BlindsFeederConfigurationWizard(BlindsFeeder feeder) {
        this.feeder = feeder;

        panelPart = new JPanel();
        panelPart.setBorder(new TitledBorder(null,
                Translations.getString("BlindsFeederConfigurationWizard.panelPart.Border.title"), TitledBorder.LEADING, TitledBorder.TOP, null, //$NON-NLS-1$
                null));
        contentPanel.add(panelPart);
        panelPart.setLayout(new FormLayout(new ColumnSpec[] {
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(70dlu;default)"),
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(70dlu;default)"),
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(70dlu;default)"),
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(70dlu;default)"),
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(70dlu;default)"),
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("right:default:grow"),
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,},
            new RowSpec[] {
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,
                FormSpecs.RELATED_GAP_ROWSPEC,
                FormSpecs.DEFAULT_ROWSPEC,}));

        lblPart = new JLabel(Translations.getString("BlindsFeederConfigurationWizard.lblPart.text")); //$NON-NLS-1$
        panelPart.add(lblPart, "2, 2, right, default");

        comboBoxPart = new JComboBox();
        comboBoxPart.setModel(new PartsComboBoxModel());
        comboBoxPart.setRenderer(new IdentifiableListCellRenderer<Part>());
        panelPart.add(comboBoxPart, "4, 2, 7, 1, left, default");
        
        btnOcrDetect = new JButton(performOcrAction);
        panelPart.add(btnOcrDetect, "14, 2");

        lblRotationInTape = new JLabel(Translations.getString("BlindsFeederConfigurationWizard.lblRotationInTape.text")); //$NON-NLS-1$
        lblRotationInTape.setToolTipText(Translations.getString("BlindsFeederConfigurationWizard.lblRotationInTape.toolTipText")); //$NON-NLS-1$
        panelPart.add(lblRotationInTape, "2, 4, right, default");

        textFieldLocationRotation = new JTextField();
        panelPart.add(textFieldLocationRotation, "4, 4, fill, default");
        textFieldLocationRotation.setColumns(4);

        lblPartTopZ = new JLabel(Translations.getString("BlindsFeederConfigurationWizard.lblPartTopZ.text")); //$NON-NLS-1$
        lblPartTopZ.setToolTipText(Translations.getString("BlindsFeederConfigurationWizard.lblPartTopZ.toolTipText")); //$NON-NLS-1$
        panelPart.add(lblPartTopZ, "8, 4, right, default");

        textFieldPartZ = new JTextField();
        panelPart.add(textFieldPartZ, "10, 4, fill, default");
        textFieldPartZ.setColumns(8);

        btnCaptureToolZ = new JButton(captureToolCoordinatesAction);
        btnCaptureToolZ.setHideActionText(true);
        panelPart.add(btnCaptureToolZ, "14, 4, left, default");

        lblRetryCount = new JLabel(Translations.getString("BlindsFeederConfigurationWizard.lblRetryCount.text")); //$NON-NLS-1$
        panelPart.add(lblRetryCount, "2, 6, right, default");

        retryCountTf = new JTextField();
        panelPart.add(retryCountTf, "4, 6, fill, default");
        retryCountTf.setColumns(4);

        panelTapeSettings = new JPanel();
        contentPanel.add(panelTapeSettings);
        panelTapeSettings.setBorder(new TitledBorder(
                null, Translations.getString("BlindsFeederConfigurationWizard.panelTapeSettings.Border.title"), //$NON-NLS-1$
                TitledBorder.LEADING, TitledBorder.TOP, null, null));
        panelTapeSettings.setLayout(new FormLayout(new ColumnSpec[] {
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(70dlu;default)"),
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(70dlu;default)"),
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(70dlu;default)"),
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(70dlu;default)"),
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(70dlu;default)"),
                FormSpecs.DEFAULT_COLSPEC,},
                new RowSpec[] {
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,}));

        lblTapeLength = new JLabel(Translations.getString("BlindsFeederConfigurationWizard.lblTapeLength.text")); //$NON-NLS-1$
        lblTapeLength.setToolTipText(Translations.getString("BlindsFeederConfigurationWizard.lblTapeLength.toolTipText")); //$NON-NLS-1$
        panelTapeSettings.add(lblTapeLength, "2, 2, right, default");

        textFieldTapeLength = new JTextField();
        textFieldTapeLength.setEditable(false);
        textFieldTapeLength.setColumns(10);
        panelTapeSettings.add(textFieldTapeLength, "4, 2");

        lblFeederExtent = new JLabel(Translations.getString("BlindsFeederConfigurationWizard.lblFeederExtent.text")); //$NON-NLS-1$
        lblFeederExtent.setToolTipText(Translations.getString("BlindsFeederConfigurationWizard.lblFeederExtent.toolTipText")); //$NON-NLS-1$
        panelTapeSettings.add(lblFeederExtent, "8, 2, right, default");

        textFieldFeederExtent = new JTextField();
        textFieldFeederExtent.setEditable(false);
        textFieldFeederExtent.setText("");
        panelTapeSettings.add(textFieldFeederExtent, "10, 2");
        textFieldFeederExtent.setColumns(10);

        btnShowInfo = new JButton(showFeaturesAction);
        panelTapeSettings.add(btnShowInfo, "14, 2");

        btnAutoSetup = new JButton(autoSetup);
        btnAutoSetup.setToolTipText(Translations.getString("BlindsFeederConfigurationWizard.btnAutoSetup.toolTipText")); //$NON-NLS-1$
        panelTapeSettings.add(btnAutoSetup, "14, 4, 1, 3");

        lblPocketPitch = new JLabel(Translations.getString("BlindsFeederConfigurationWizard.lblPocketPitch.text")); //$NON-NLS-1$
        lblPocketPitch.setToolTipText(Translations.getString("BlindsFeederConfigurationWizard.lblPocketPitch.toolTipText")); //$NON-NLS-1$
        panelTapeSettings.add(lblPocketPitch, "2, 4, right, default");

        textFieldPocketPitch = new JTextField();
        panelTapeSettings.add(textFieldPocketPitch, "4, 4");
        textFieldPocketPitch.setColumns(5);

        lblPartSize = new JLabel(Translations.getString("BlindsFeederConfigurationWizard.lblPartSize.text")); //$NON-NLS-1$
        lblPartSize.setToolTipText(Translations.getString("BlindsFeederConfigurationWizard.lblPartSize.toolTipText")); //$NON-NLS-1$
        panelTapeSettings.add(lblPartSize, "8, 4, right, default");

        textFieldPocketSize = new JTextField();
        textFieldPocketSize.setColumns(5);
        panelTapeSettings.add(textFieldPocketSize, "10, 4");

        lblPocketCount = new JLabel(Translations.getString("BlindsFeederConfigurationWizard.lblPocketCount.text")); //$NON-NLS-1$
        panelTapeSettings.add(lblPocketCount, "2, 6, right, default");

        textFieldPocketCount = new JTextField();
        textFieldPocketCount.setEditable(false);
        panelTapeSettings.add(textFieldPocketCount, "4, 6");
        textFieldPocketCount.setColumns(10);

        lblPocketCenterline = new JLabel(Translations.getString("BlindsFeederConfigurationWizard.lblPocketCenterline.text")); //$NON-NLS-1$
        lblPocketCenterline.setToolTipText(Translations.getString("BlindsFeederConfigurationWizard.lblPocketCenterline.toolTipText")); //$NON-NLS-1$
        panelTapeSettings.add(lblPocketCenterline, "8, 6, right, default");

        textFieldPocketCenterline = new JTextField();
        panelTapeSettings.add(textFieldPocketCenterline, "10, 6");
        textFieldPocketCenterline.setColumns(5);

        lblFirstPocket = new JLabel(Translations.getString("BlindsFeederConfigurationWizard.lblFirstPocket.text")); //$NON-NLS-1$
        lblFirstPocket.setToolTipText(Translations.getString("BlindsFeederConfigurationWizard.lblFirstPocket.toolTipText")); //$NON-NLS-1$
        panelTapeSettings.add(lblFirstPocket, "2, 8, right, default");

        textFieldFirstPocket = new JTextField();
        panelTapeSettings.add(textFieldFirstPocket, "4, 8, fill, default");
        textFieldFirstPocket.setColumns(10);

        lblFeederNo = new JLabel(Translations.getString("BlindsFeederConfigurationWizard.lblFeederNo.text")); //$NON-NLS-1$
        panelTapeSettings.add(lblFeederNo, "8, 8, right, default");
        lblFeederNo.setToolTipText(Translations.getString("BlindsFeederConfigurationWizard.lblFeederNo.toolTipText")); //$NON-NLS-1$

        textFieldFeederNo = new JTextField();
        panelTapeSettings.add(textFieldFeederNo, "10, 8");
        textFieldFeederNo.setEditable(false);
        textFieldFeederNo.setColumns(10);

        lblLastPocket = new JLabel(Translations.getString("BlindsFeederConfigurationWizard.lblLastPocket.text")); //$NON-NLS-1$
        lblLastPocket.setToolTipText(Translations.getString("BlindsFeederConfigurationWizard.lblLastPocket.toolTipText")); //$NON-NLS-1$
        panelTapeSettings.add(lblLastPocket, "2, 10, right, default");

        textFieldLastPocket = new JTextField();
        panelTapeSettings.add(textFieldLastPocket, "4, 10");
        textFieldLastPocket.setColumns(5);

        lblFeedersTotal = new JLabel(Translations.getString("BlindsFeederConfigurationWizard.lblFeedersTotal.text")); //$NON-NLS-1$
        panelTapeSettings.add(lblFeedersTotal, "8, 10, right, default");
        lblFeedersTotal.setToolTipText(Translations.getString("BlindsFeederConfigurationWizard.lblFeedersTotal.toolTipText")); //$NON-NLS-1$

        textFieldFeedersTotal = new JTextField();
        panelTapeSettings.add(textFieldFeedersTotal, "10, 10");
        textFieldFeedersTotal.setEditable(false);
        textFieldFeedersTotal.setColumns(5);

        lblFeedCount = new JLabel(Translations.getString("BlindsFeederConfigurationWizard.lblFeedCount.text")); //$NON-NLS-1$
        panelTapeSettings.add(lblFeedCount, "2, 12, right, default");

        textFieldFeedCount = new JTextField();
        panelTapeSettings.add(textFieldFeedCount, "4, 12");
        textFieldFeedCount.setColumns(5);

        btnResetFeedCount = new JButton(resetFeedCountAction);
        panelTapeSettings.add(btnResetFeedCount, "14, 12");

        panelCover = new JPanel();
        panelCover.setBorder(new TitledBorder(null, Translations.getString("BlindsFeederConfigurationWizard.panelCover.Border.title"), TitledBorder.LEADING, //$NON-NLS-1$
                TitledBorder.TOP, null, null));
        contentPanel.add(panelCover);
        panelCover.setLayout(new FormLayout(new ColumnSpec[] {
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(70dlu;default)"),
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(70dlu;default)"),
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(70dlu;default)"),
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(70dlu;default)"),
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,
                FormSpecs.RELATED_GAP_COLSPEC,
                ColumnSpec.decode("max(70dlu;default)"),
                FormSpecs.RELATED_GAP_COLSPEC,
                FormSpecs.DEFAULT_COLSPEC,},
                new RowSpec[] {
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,
                        FormSpecs.RELATED_GAP_ROWSPEC,
                        FormSpecs.DEFAULT_ROWSPEC,}));

        lblCoverType = new JLabel(Translations.getString("BlindsFeederConfigurationWizard.lblCoverType.text")); //$NON-NLS-1$
        panelCover.add(lblCoverType, "2, 2, right, default");

        comboBoxCoverType = new JComboBox(BlindsFeeder.CoverType.values());
        panelCover.add(comboBoxCoverType, "4, 2, fill, default");

        lblCoverOpenClose = new JLabel(Translations.getString("BlindsFeederConfigurationWizard.lblCoverOpenClose.text")); //$NON-NLS-1$
        panelCover.add(lblCoverOpenClose, "8, 2, right, default");

        comboBoxCoverActuation = new JComboBox(BlindsFeeder.CoverActuation.values());
        panelCover.add(comboBoxCoverActuation, "10, 2, fill, default");

        btnOpenCover = new JButton(openCover);
        panelCover.add(btnOpenCover, "14, 2");

        lblPushSpeed = new JLabel(Translations.getString("BlindsFeederConfigurationWizard.lblPushSpeed.text")); //$NON-NLS-1$
        lblPushSpeed.setToolTipText(Translations.getString("BlindsFeederConfigurationWizard.lblPushSpeed.toolTipText")); //$NON-NLS-1$
        panelCover.add(lblPushSpeed, "2, 4, right, default");

        textFieldPushSpeed = new JTextField();
        panelCover.add(textFieldPushSpeed, "4, 4");
        textFieldPushSpeed.setColumns(10);

        lblPushZOffset = new JLabel(Translations.getString("BlindsFeederConfigurationWizard.lblPushZOffset.text")); //$NON-NLS-1$
        panelCover.add(lblPushZOffset, "8, 4, right, default");

        textFieldPushZOffset = new JTextField();
        panelCover.add(textFieldPushZOffset, "10, 4, fill, default");
        textFieldPushZOffset.setColumns(10);

        btnCloseThis = new JButton(closeCover);
        panelCover.add(btnCloseThis, "14, 4");

        btnOpenAll = new JButton(openAllCovers);
        btnOpenAll.setText("Open all Covers");
        panelCover.add(btnOpenAll, "14, 6");

        btnCloseAll = new JButton(closeAllCovers);
        btnCloseAll.setText("Close all Covers");
        btnCloseAll.setToolTipText(Translations.getString("BlindsFeederConfigurationWizard.btnCloseAll.toolTipText")); //$NON-NLS-1$
        panelCover.add(btnCloseAll, "14, 8");

        lblEdgeBeginDistance = new JLabel(Translations.getString("BlindsFeederConfigurationWizard.lblEdgeBeginDistance.text")); //$NON-NLS-1$
        lblEdgeBeginDistance.setToolTipText(Translations.getString("BlindsFeederConfigurationWizard.lblEdgeBeginDistance.toolTipText")); //$NON-NLS-1$
        panelCover.add(lblEdgeBeginDistance, "2, 10, right, default");

        textFieldEdgeOpeningDistance = new JTextField();
        panelCover.add(textFieldEdgeOpeningDistance, "4, 10");
        textFieldEdgeOpeningDistance.setColumns(10);

        lblEdgeEnd = new JLabel(Translations.getString("BlindsFeederConfigurationWizard.lblEdgeEnd.text")); //$NON-NLS-1$
        lblEdgeEnd.setToolTipText(Translations.getString("BlindsFeederConfigurationWizard.lblEdgeEnd.toolTipText")); //$NON-NLS-1$
        panelCover.add(lblEdgeEnd, "8, 10, right, default");

        textFieldEdgeClosingDistance = new JTextField();
        panelCover.add(textFieldEdgeClosingDistance, "10, 10, fill, default");
        textFieldEdgeClosingDistance.setColumns(10);

        btnCalibrateEdges = new JButton(calibrateEdgesAction);
        panelCover.add(btnCalibrateEdges, "14, 10");
    }

    @Override
    public void createBindings() {
        LengthConverter lengthConverter = new LengthConverter();
        IntegerConverter intConverter = new IntegerConverter();
        DoubleConverter doubleConverter =
                new DoubleConverter(getDisplayPreferences().getLengthDisplayFormat());

        MutableLocationProxy location = new MutableLocationProxy();
        bind(UpdateStrategy.READ_WRITE, feeder, "location", location, "location");

        addWrappedBinding(location, "rotation", textFieldLocationRotation, "text", doubleConverter);
        addWrappedBinding(location, "lengthZ", textFieldPartZ, "text", lengthConverter);

        addWrappedBinding(feeder, "part", comboBoxPart, "selectedItem");
        addWrappedBinding(feeder, "feedRetryCount", retryCountTf, "text", intConverter);

        addWrappedBinding(feeder, "tapeLength", textFieldTapeLength, "text", lengthConverter);
        addWrappedBinding(feeder, "feederExtent", textFieldFeederExtent, "text", lengthConverter);
        addWrappedBinding(feeder, "pocketCenterline", textFieldPocketCenterline, "text", lengthConverter);
        addWrappedBinding(feeder, "pocketPitch", textFieldPocketPitch, "text", lengthConverter);
        addWrappedBinding(feeder, "pocketSize", textFieldPocketSize, "text", lengthConverter);
        addWrappedBinding(feeder, "pocketCount", textFieldPocketCount, "text", intConverter);
        addWrappedBinding(feeder, "firstPocket", textFieldFirstPocket, "text", intConverter);
        addWrappedBinding(feeder, "lastPocket", textFieldLastPocket, "text", intConverter);
        addWrappedBinding(feeder, "feedCount", textFieldFeedCount, "text", intConverter);

        addWrappedBinding(feeder, "coverType", comboBoxCoverType, "selectedItem");
        addWrappedBinding(feeder, "coverActuation", comboBoxCoverActuation, "selectedItem");
        addWrappedBinding(feeder, "edgeOpenDistance", textFieldEdgeOpeningDistance, "text", lengthConverter);
        addWrappedBinding(feeder, "edgeClosedDistance", textFieldEdgeClosingDistance, "text", lengthConverter);
        addWrappedBinding(feeder, "pushSpeed", textFieldPushSpeed, "text", doubleConverter);
        addWrappedBinding(feeder, "pushZOffset", textFieldPushZOffset, "text", lengthConverter);

        addWrappedBinding(feeder, "feederNo", textFieldFeederNo, "text", intConverter);
        addWrappedBinding(feeder, "feedersTotal", textFieldFeedersTotal, "text", intConverter);

        ComponentDecorators.decorateWithAutoSelect(textFieldLocationRotation);
        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldPartZ);
        ComponentDecorators.decorateWithAutoSelect(retryCountTf);

        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldTapeLength);
        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldFeederExtent);
        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldPocketCenterline);
        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldPocketPitch);
        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldPocketSize);
        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldEdgeOpeningDistance);
        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldEdgeClosingDistance);
        ComponentDecorators.decorateWithAutoSelect(textFieldPushSpeed);
        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(textFieldPushZOffset);
        //ComponentDecorators.decorateWithAutoSelect(textFieldFirstPocket);
        //ComponentDecorators.decorateWithAutoSelect(textFieldLastPocket);
        ComponentDecorators.decorateWithAutoSelect(textFieldFeedCount);
    }

    public HeadMountable getTool() throws Exception {
        return MainFrame.get().getMachineControls().getSelectedNozzle();
    }

    private Action performOcrAction =
            new AbstractAction("OCR Detect") {
        {
            putValue(Action.SHORT_DESCRIPTION,
                    "Try to detect and set the part by OCR.");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            applyAction.actionPerformed(null);
            UiUtils.submitUiMachineTask(() -> {
                feeder.performOcr(feeder.getCamera(), OcrAction.ChangePart);
            });
        }
    };

    private Action captureToolCoordinatesAction =
            new AbstractAction("Get Tool Z", Icons.captureTool) {
        {
            putValue(Action.SHORT_DESCRIPTION,
                    "Capture the Z height that the tool is at.");
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            UiUtils.messageBoxOnException(() -> {
                Location l = getTool().getLocation();
                Helpers.copyLocationIntoTextFields(getDisplayPreferences(), l, null, null,
                        textFieldPartZ, null);
            });
        }
    };

    private Action showFeaturesAction =
            new AbstractAction("Show Features") {
        {
            putValue(Action.SHORT_DESCRIPTION,
                    "Show the features recognized by vision, taking the camera center and/or already set feeder properties into consideration.");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            UiUtils.submitUiMachineTask(() -> {
                feeder.showFeatures();
            });
        }
    };

    private Action calibrateEdgesAction =
            new AbstractAction("Calibrate Cover Edges") {
        {
            putValue(Action.SHORT_DESCRIPTION,
                    "Calibrate the cover edges against the nozzle tip to get precise open/close positioning.");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            applyAction.actionPerformed(e);
            UiUtils.submitUiMachineTask(() -> {
                feeder.calibrateCoverEdges();
            });
        };
    };

    private Action resetFeedCountAction =
            new AbstractAction("Reset") {
        {
            putValue(Action.SHORT_DESCRIPTION,
                    "Reset the Feed Count to 0 (for a newly loaded tape).");
        }
        @Override
        public void actionPerformed(ActionEvent e) {
            textFieldFeedCount.setText("0");
            applyAction.actionPerformed(e);
            UiUtils.submitUiMachineTask(() -> {
                // when resetting the feed count, we also assume we fumbled with the cover 
                feeder.setCoverPosition(new Length(Double.NaN, LengthUnit.Millimeters));
            });
        }
    };

    private Action autoSetup = new AbstractAction("Auto Setup", Icons.captureCamera) {
        {
            putValue(Action.SHORT_DESCRIPTION,
                    "Capture the blinds pitch, size and centerline from the current camera position.");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            applyAction.actionPerformed(e);
            UiUtils.submitUiMachineTask(() -> {
                Camera camera = getMachine()
                        .getDefaultHead()
                        .getDefaultCamera();

                feeder.findPocketsAndCenterline(camera);
            });
        }
    };

    private Action openCover = new AbstractAction("Open Cover", Icons.lockOpenOutline) {
        {
            putValue(Action.SHORT_DESCRIPTION,
                    "Open this cover using the nozzle tip.");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            applyAction.actionPerformed(e);
            UiUtils.submitUiMachineTask(() -> {
                feeder.actuateCover(MainFrame.get().getMachineControls().getSelectedNozzle(), true);
            });
        }
    };

    private Action closeCover = new AbstractAction("Close Cover", Icons.lockOutline) {
        {
            putValue(Action.SHORT_DESCRIPTION,
                    "Close this cover using the nozzle tip.");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            applyAction.actionPerformed(e);
            UiUtils.submitUiMachineTask(() -> {
                feeder.actuateCover(MainFrame.get().getMachineControls().getSelectedNozzle(), false);
            });
        }
    };

    private Action openAllCovers = new AbstractAction("Open All Covers") {
        {
            putValue(Action.SHORT_DESCRIPTION,
                    "Open the covers of all the enabled feeders of the machine.");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            applyAction.actionPerformed(e);
            UiUtils.submitUiMachineTask(() -> {
                BlindsFeeder.actuateAllFeederCovers(getMachine(),
                        MainFrame.get().getMachineControls().getSelectedNozzle(), true);
            });
        }
    };

    private Action closeAllCovers = new AbstractAction("Close All Covers") {
        {
            putValue(Action.SHORT_DESCRIPTION,
                    "Close the opened covers of all the feeders of the machine.");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            applyAction.actionPerformed(e);
            UiUtils.submitUiMachineTask(() -> {
                BlindsFeeder.actuateAllFeederCovers(getMachine(),
                        MainFrame.get().getMachineControls().getSelectedNozzle(), false);
            });
        }
    };

    private JLabel lblPart;
    private JPanel panelPart;
    private JTextField textFieldPartZ;
    private JComboBox comboBoxPart;
    private JLabel lblRotationInTape;
    private JTextField textFieldLocationRotation;
    private JLabel lblRetryCount;
    private JTextField retryCountTf;
    private JLabel lblPartTopZ;
    private JButton btnCaptureToolZ;
    private JLabel lblFeederNo;
    private JTextField textFieldFeederNo;
    private JLabel lblFeedersTotal;
    private JTextField textFieldFeedersTotal;
    private JLabel lblPocketPitch;
    private JTextField textFieldPocketPitch;
    private JPanel panelTapeSettings;
    private JLabel lblFeedCount;
    private JTextField textFieldFeedCount;
    private JButton btnResetFeedCount;
    private JButton btnAutoSetup;
    private JLabel lblTapeLength;
    private JTextField textFieldTapeLength;
    private JLabel lblPartSize;
    private JTextField textFieldPocketSize;
    private JLabel lblPocketCenterline;
    private JTextField textFieldPocketCenterline;
    private JLabel lblFeederExtent;
    private JTextField textFieldFeederExtent;
    private JLabel lblPocketCount;
    private JTextField textFieldPocketCount;
    private JTextField textFieldLastPocket;
    private JLabel lblLastPocket;
    private JButton btnOpenCover;
    private JLabel lblEdgeBeginDistance;
    private JTextField textFieldEdgeOpeningDistance;
    private JLabel lblPushSpeed;
    private JTextField textFieldPushSpeed;
    private JPanel panelCover;
    private JLabel lblCoverType;
    private JComboBox comboBoxCoverType;
    private JLabel lblCoverOpenClose;
    private JComboBox comboBoxCoverActuation;
    private JButton btnOpenAll;
    private JButton btnCloseAll;
    private JLabel lblPushZOffset;
    private JTextField textFieldPushZOffset;
    private JButton btnCloseThis;
    private JLabel lblEdgeEnd;
    private JTextField textFieldEdgeClosingDistance;
    private JButton btnCalibrateEdges;
    private JButton btnShowInfo;
    private JTextField textFieldFirstPocket;
    private JLabel lblFirstPocket;
    private JButton btnOcrDetect;
}

