/*
 * Copyright (C) 2026 Pono contributors
 * 
 * This file is part of Pono, a modified version of OpenPnP.
 * 
 * Pono is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * Pono is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Pono. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package org.openpnp.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.jdesktop.beansbinding.AutoBinding.UpdateStrategy;
import org.openpnp.Translations;
import org.openpnp.gui.components.ComponentDecorators;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.AbstractConfigurationWizard;
import org.openpnp.gui.support.DoubleConverter;
import org.openpnp.gui.support.LengthConverter;
import org.openpnp.gui.support.MutableLocationProxy;
import org.openpnp.gui.support.PartsComboBoxModel;
import org.openpnp.model.Abstract2DLocatable.Side;
import org.openpnp.model.Configuration;
import org.openpnp.model.Placement;
import org.openpnp.model.Placement.ErrorHandling;
import org.openpnp.model.Placement.Type;
import org.openpnp.model.PlacementsHolderLocation;
import org.openpnp.spi.Feeder;

/**
 * The properties of one placement, as the mockups draw them: position, part, options, this run,
 * and notes, each a section that folds.
 * <p>
 * A wizard like any other underneath - wrapped bindings, dirty state, Apply and Reset - so the
 * properties column treats it exactly as it treats a feeder's sheets. The table used to be the
 * only place a placement could be edited, one cell at a time.
 */
@SuppressWarnings("serial")
public class PlacementInspector extends AbstractConfigurationWizard {
    private final Configuration configuration;
    private final JobPlacementsPanel owner;
    private final Placement placement;
    private final PlacementsHolderLocation<?> location;

    private final JTextField x = new JTextField();
    private final JTextField y = new JTextField();
    private final JTextField rotation = new JTextField();
    private final JComboBox<Side> side = new JComboBox<>(Side.values());
    private final JComboBox<Object> part = new JComboBox<>(new PartsComboBoxModel());
    private final JLabel packageLabel = new JLabel();
    private final Chip feederChip = new Chip("", Chip.Tone.Neutral, Chip.Shape.Status); //$NON-NLS-1$
    private final JLabel feederNote = Ui.t2(""); //$NON-NLS-1$
    private final JComboBox<Type> type = new JComboBox<>(Type.values());
    private final JComboBox<ErrorHandling> errorHandling = new JComboBox<>(ErrorHandling.values());
    private final Forms.Toggle enabled = new Forms.Toggle();
    private final Chip placedChip = new Chip("", Chip.Tone.Neutral, Chip.Shape.Status); //$NON-NLS-1$
    private final JTextField comments = new JTextField();

    public PlacementInspector(Configuration configuration, JobPlacementsPanel owner,
            PlacementsHolderLocation<?> location, Placement placement) {
        this.configuration = configuration;
        this.owner = owner;
        this.location = location;
        this.placement = placement;

        contentPanel.setOpaque(false);
        getScrollPane().setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        getScrollPane().setViewportView(new WidthTrackingPanel(contentPanel));
        part.setRenderer(new javax.swing.DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof org.openpnp.model.Part) {
                    setText(((org.openpnp.model.Part) value).getId());
                }
                return this;
            }
        });
        part.setPrototypeDisplayValue("R0805-1K"); //$NON-NLS-1$
        contentPanel.add(positionSection());
        contentPanel.add(partSection());
        contentPanel.add(optionsSection());
        contentPanel.add(runSection());
        contentPanel.add(notesSection());
        contentPanel.add(Box.createVerticalGlue());
    }

    private Component positionSection() {
        String units = configuration.getSystemUnits().getShortName();
        Forms.Grid grid = new Forms.Grid();
        grid.row(Translations.getString("PlacementInspector.Coordinates"), //$NON-NLS-1$
                Forms.row(Forms.input(x, true, "X"), Forms.input(y, true, "Y"))); //$NON-NLS-1$ //$NON-NLS-2$
        grid.row(Translations.getString("PlacementInspector.Rotation"), //$NON-NLS-1$
                Forms.inputWithUnit(rotation, "\u00b0")); //$NON-NLS-1$
        grid.row(Translations.getString("PlacementInspector.Side"), Forms.dropdown(side)); //$NON-NLS-1$

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        JButton camera = Ui.button(owner.moveCameraToPlacementLocation, Ui.Size.Sm, Ui.Variant.Default);
        camera.setText(Translations.getString("Dock.Action.MoveCamera")); //$NON-NLS-1$
        camera.setIcon(Ui.iconSm("camera")); //$NON-NLS-1$
        camera.setPreferredSize(null);
        JButton capture = Ui.button(owner.captureCameraPlacementLocation, Ui.Size.Sm, Ui.Variant.Default);
        capture.setText(Translations.getString("PlacementInspector.Capture")); //$NON-NLS-1$
        capture.setIcon(Ui.iconSm("target")); //$NON-NLS-1$
        capture.setPreferredSize(null);
        buttons.add(camera);
        buttons.add(Box.createHorizontalStrut(6));
        buttons.add(capture);
        buttons.add(Box.createHorizontalGlue());
        grid.row("", buttons); //$NON-NLS-1$

        return new Forms.Section("move", Translations.getString("PlacementInspector.Position")) //$NON-NLS-1$ //$NON-NLS-2$
                .withRight(units).content(grid);
    }

    private Component partSection() {
        Forms.Grid grid = new Forms.Grid();
        grid.row(Translations.getString("PlacementInspector.Part"), Forms.dropdown(part)); //$NON-NLS-1$
        grid.row(Translations.getString("PlacementInspector.Package"), Forms.readOnly(packageLabel)); //$NON-NLS-1$
        grid.row(Translations.getString("PlacementInspector.Feeder"), Forms.statusRow(feederChip, null)); //$NON-NLS-1$
        ((JPanel) grid.getComponent(grid.getComponentCount() - 1)).add(feederNote);
        part.addActionListener(e -> describePart());
        return new Forms.Section("parts", Translations.getString("PlacementInspector.PartSection")) //$NON-NLS-1$ //$NON-NLS-2$
                .content(grid);
    }

    private Component optionsSection() {
        Forms.Grid grid = new Forms.Grid();
        grid.row(Translations.getString("PlacementInspector.Type"), Forms.dropdown(type)); //$NON-NLS-1$
        grid.row(Translations.getString("PlacementInspector.ErrorHandling"), Forms.dropdown(errorHandling)); //$NON-NLS-1$
        grid.row(Translations.getString("PlacementInspector.Enabled"), //$NON-NLS-1$
                Forms.toggleRow(enabled, Translations.getString("PlacementInspector.EnabledNote"))); //$NON-NLS-1$
        return new Forms.Section("gear", Translations.getString("PlacementInspector.Options")) //$NON-NLS-1$ //$NON-NLS-2$
                .content(grid);
    }

    private Component runSection() {
        Forms.Grid grid = new Forms.Grid();
        grid.row(Translations.getString("PlacementInspector.Status"), Forms.statusRow(placedChip, null)); //$NON-NLS-1$
        return new Forms.Section("play", Translations.getString("PlacementInspector.ThisRun")) //$NON-NLS-1$ //$NON-NLS-2$
                .content(grid);
    }

    private Component notesSection() {
        Forms.Grid grid = new Forms.Grid();
        grid.row(Translations.getString("PlacementInspector.Comments"), Forms.input(comments, false)); //$NON-NLS-1$
        return new Forms.Section("info", Translations.getString("PlacementInspector.Notes")) //$NON-NLS-1$ //$NON-NLS-2$
                .content(grid);
    }

    @Override
    public void createBindings() {
        LengthConverter lengthConverter = new LengthConverter();
        DoubleConverter doubleConverter = new DoubleConverter(configuration.getLengthDisplayFormat());

        MutableLocationProxy proxy = new MutableLocationProxy();
        bind(UpdateStrategy.READ_WRITE, placement, "location", proxy, "location"); //$NON-NLS-1$ //$NON-NLS-2$
        addWrappedBinding(proxy, "lengthX", x, "text", lengthConverter); //$NON-NLS-1$ //$NON-NLS-2$
        addWrappedBinding(proxy, "lengthY", y, "text", lengthConverter); //$NON-NLS-1$ //$NON-NLS-2$
        addWrappedBinding(proxy, "rotation", rotation, "text", doubleConverter); //$NON-NLS-1$ //$NON-NLS-2$
        addWrappedBinding(placement, "side", side, "selectedItem"); //$NON-NLS-1$ //$NON-NLS-2$
        addWrappedBinding(placement, "part", part, "selectedItem"); //$NON-NLS-1$ //$NON-NLS-2$
        addWrappedBinding(placement, "type", type, "selectedItem"); //$NON-NLS-1$ //$NON-NLS-2$
        addWrappedBinding(placement, "errorHandling", errorHandling, "selectedItem"); //$NON-NLS-1$ //$NON-NLS-2$
        addWrappedBinding(placement, "enabled", enabled, "selected"); //$NON-NLS-1$ //$NON-NLS-2$
        addWrappedBinding(placement, "comments", comments, "text"); //$NON-NLS-1$ //$NON-NLS-2$

        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(x);
        ComponentDecorators.decorateWithAutoSelectAndLengthConversion(y);
        ComponentDecorators.decorateWithAutoSelect(rotation);
        ComponentDecorators.decorateWithAutoSelect(comments);
    }

    @Override
    protected void loadFromModel() {
        super.loadFromModel();
        describePart();
        describeRun();
    }

    @Override
    protected void saveToModel() {
        super.saveToModel();
        owner.refresh();
    }

    /** The package and the feeder follow the part chosen, before it is applied. */
    private void describePart() {
        Object chosen = part.getSelectedItem();
        org.openpnp.model.Part p = chosen instanceof org.openpnp.model.Part ? (org.openpnp.model.Part) chosen : null;
        packageLabel.setText(p == null || p.getPackage() == null ? "\u2014" : p.getPackage().getId()); //$NON-NLS-1$
        Feeder found = null;
        if (p != null) {
            for (Feeder feeder : configuration.getMachine().getFeeders()) {
                if (feeder.isEnabled() && feeder.getPart() == p) {
                    found = feeder;
                    break;
                }
            }
        }
        if (p == null) {
            feederChip.setText(Translations.getString("PlacementInspector.Feeder.NoPart")); //$NON-NLS-1$
            feederChip.setTone(Chip.Tone.Neutral);
            feederNote.setText(""); //$NON-NLS-1$
        }
        else if (found == null) {
            feederChip.setText(Translations.getString("PlacementInspector.Feeder.None")); //$NON-NLS-1$
            feederChip.setTone(Chip.Tone.Err);
            feederNote.setText(""); //$NON-NLS-1$
        }
        else {
            feederChip.setText(Translations.getString("PlacementInspector.Feeder.Ready")); //$NON-NLS-1$
            feederChip.setTone(Chip.Tone.Ok);
            feederNote.setText(found.getName());
        }
        feederNote.setBorder(new javax.swing.border.EmptyBorder(0, 8, 0, 0));
    }

    private void describeRun() {
        boolean placed = owner.getJobPanel().getJob() != null
                && owner.getJobPanel().getJob().retrievePlacedStatus(location, placement.getId());
        placedChip.setText(Translations.getString(placed ? "PlacementInspector.Placed" //$NON-NLS-1$
                : "PlacementInspector.NotPlaced")); //$NON-NLS-1$
        placedChip.setTone(placed ? Chip.Tone.Ok : Chip.Tone.Neutral);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(Math.max(size.width, 300), size.height);
    }

    /** The heading for the properties column: the placement's id over where it sits. */
    public static String subtitle(PlacementsHolderLocation<?> location) {
        if (location == null || location.getPlacementsHolder() == null) {
            return Translations.getString("JobPlacementsPanel.Border.title"); //$NON-NLS-1$
        }
        return Translations.getString("JobPlacementsPanel.Border.title") + " \u00b7 " //$NON-NLS-1$ //$NON-NLS-2$
                + location.getPlacementsHolder().getName() + " \u00b7 " + location.getGlobalSide(); //$NON-NLS-1$
    }

    /** The properties column shows this instead of the wizard's own layout. */
    @Override
    public JPanel getWizardPanel() {
        return this;
    }

    /** Makes the form as wide as the column and no wider, so the fields shrink rather than scroll. */
    private static final class WidthTrackingPanel extends JPanel implements javax.swing.Scrollable {
        WidthTrackingPanel(Component content) {
            super(new BorderLayout());
            setOpaque(false);
            add(content, BorderLayout.CENTER);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
