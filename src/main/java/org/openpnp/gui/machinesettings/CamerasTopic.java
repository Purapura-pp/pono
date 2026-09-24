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

package org.openpnp.gui.machinesettings;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import org.openpnp.Translations;
import org.openpnp.gui.components.CameraView;
import org.openpnp.gui.form.Form;
import org.openpnp.gui.form.FormWizard;
import org.openpnp.gui.shell.Chip;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.RoundedPanel;
import org.openpnp.gui.shell.Tokens;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.Wizard;
import org.openpnp.machine.reference.ReferenceMachine;
import org.openpnp.machine.reference.camera.ReferenceCamera;
import org.openpnp.model.CalibrationStep;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Camera;

/**
 * The cameras, the mockups' 27: which device each one is on this computer, with a picture to tell
 * two of the same model apart, how it is mounted and lit, and what the calibration measured of it,
 * shown with where it came from.
 */
final class CamerasTopic extends Topic {
    private final MachineSettingsPanel page;
    private final ReferenceMachine machine;
    private final List<ReferenceCamera> cameras = new ArrayList<>();
    private final List<CameraCard> cards = new ArrayList<>();
    private final JPanel cameraHolder = new JPanel(new BorderLayout(16, 0));
    private final List<FormWizard> cameraForms = new ArrayList<>();
    private ReferenceCamera selected;
    /** One view for the topic's life: each holds a thread of its own. */
    private CameraView preview;

    CamerasTopic(MachineSettingsPanel page, ReferenceMachine machine) {
        super(MachineSettingsPanel.CAMERAS, "camera"); //$NON-NLS-1$
        this.page = page;
        this.machine = machine;
    }

    @Override
    protected JComponent build() {
        cameras.clear();
        cards.clear();
        for (Camera camera : SetupChecks.allCameras(machine)) {
            if (camera instanceof ReferenceCamera) {
                cameras.add((ReferenceCamera) camera);
            }
        }
        if (cameras.isEmpty()) {
            return Forms.emptyState("camera", title(), Translations.getString("MachineSettings.Cameras.None")); //$NON-NLS-1$ //$NON-NLS-2$
        }
        JPanel row = new JPanel(new GridLayout(1, Math.max(2, cameras.size()), 10, 0));
        row.setOpaque(false);
        for (ReferenceCamera camera : cameras) {
            CameraCard card = new CameraCard(camera);
            cards.add(card);
            row.add(card);
        }
        Forms.Section list = new Forms.Section("camera", Translations.getString("MachineSettings.Topic.Cameras")) //$NON-NLS-1$ //$NON-NLS-2$
                .withRight(String.format(Translations.getString("MachineSettings.Cameras.Count"), cameras.size())) //$NON-NLS-1$
                .content(row);
        cameraHolder.setOpaque(false);
        JButton calibration = Ui.button(Translations.getString("MachineSettings.Guide.ToCalibration"), //$NON-NLS-1$
                Ui.iconSm("target"), Ui.Size.Sm, Ui.Variant.Ghost); //$NON-NLS-1$
        calibration.addActionListener(e -> page.getFrame().showCalibrationStep(null, null));
        JComponent view = MachineSettingsPanel.page(
                Guide.of(Translations.getString("MachineSettings.Guide.Cameras"), calibration), //$NON-NLS-1$
                list, cameraHolder);
        select(cameras.get(0));
        return view;
    }

    @Override
    void shown() {
        if (preview != null && preview.getCamera() != null && !streaming) {
            preview.getCamera().startContinuousCapture(preview);
            streaming = true;
        }
        for (CameraCard card : cards) {
            card.describe();
        }
    }

    /** The preview's frames stop while the topic is not on show; the view keeps its camera. */
    @Override
    void hidden() {
        if (preview != null && preview.getCamera() != null && streaming) {
            preview.getCamera().stopContinuousCapture(preview);
            streaming = false;
        }
    }

    private boolean streaming;

    @Override
    void discard() {
        hidden();
        super.discard();
    }

    private void select(ReferenceCamera camera) {
        if (camera == selected) {
            return;
        }
        for (FormWizard form : cameraForms) {
            if (form.hasEdits()) {
                int choice = MachineSettingsPanel.askUnapplied(cameraHolder, selected.getName());
                if (choice == 1) {
                    form.apply();
                }
                else if (choice == 0) {
                    form.reset();
                }
                else {
                    return;
                }
            }
        }
        for (FormWizard form : cameraForms) {
            forms.remove(form);
            form.dispose();
        }
        cameraForms.clear();
        selected = camera;
        for (CameraCard card : cards) {
            card.setOn(card.camera == camera);
        }

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        SetupChecks.Check missing = page.getChecks().about(SetupChecks.DEVICE_MISSING, camera);
        if (missing == null) {
            missing = page.getChecks().about(SetupChecks.NO_DEVICE, camera);
        }
        if (missing != null) {
            column.add(MachineSettingsPanel.capped(hint(missing)));
        }
        Wizard device = camera.getConfigurationWizard();
        if (device instanceof FormWizard) {
            FormWizard form = forms.add((FormWizard) device);
            ((FormWizard) device).getApplyAction().addPropertyChangeListener(e -> {
                if ("enabled".equals(e.getPropertyName()) && !Boolean.TRUE.equals(e.getNewValue())) { //$NON-NLS-1$
                    page.refreshChecks();
                    for (CameraCard card : cards) {
                        card.describe();
                    }
                }
            });
            cameraForms.add(form);
            column.add(MachineSettingsPanel.capped(form));
        }
        FormWizard mount = forms.add(mount(camera));
        cameraForms.add(mount);
        column.add(MachineSettingsPanel.capped(mount));
        FormWizard facts = forms.add(facts(camera));
        cameraForms.add(facts);
        column.add(MachineSettingsPanel.capped(facts));

        if (preview == null) {
            preview = new CameraView();
        }
        preview.setCamera(camera);
        streaming = true;
        RoundedPanel frame = new RoundedPanel(Tokens.R_MD, Ui::cameraBg, Ui::border);
        frame.setLayout(new BorderLayout());
        frame.add(preview, BorderLayout.CENTER);
        frame.setPreferredSize(new Dimension(300, 210));
        Forms.Section previewSection = new Forms.Section("camera", //$NON-NLS-1$
                String.format(Translations.getString("MachineSettings.Cameras.Preview"), camera.getName())); //$NON-NLS-1$
        previewSection.content(frame);
        JPanel side = new JPanel(new BorderLayout());
        side.setOpaque(false);
        side.add(previewSection, BorderLayout.NORTH);
        side.setPreferredSize(new Dimension(330, 10));

        cameraHolder.removeAll();
        cameraHolder.add(column, BorderLayout.CENTER);
        cameraHolder.add(side, BorderLayout.EAST);
        cameraHolder.revalidate();
        cameraHolder.repaint();
    }

    /** How it is mounted and lit: the choices of the camera's general form that differ between machines. */
    private FormWizard mount(ReferenceCamera camera) {
        List<Actuator> lights = new ArrayList<>();
        lights.add(null);
        if (camera.getHead() != null) {
            lights.addAll(camera.getHead().getActuators());
        }
        for (Actuator actuator : camera.getMachine().getActuators()) {
            if (!lights.contains(actuator)) {
                lights.add(actuator);
            }
        }
        return Form.of(camera).named(camera.getName())
                .section("MachineSettings.Cameras.Mount", "sliders") //$NON-NLS-1$ //$NON-NLS-2$
                .segmented("looking", "CameraConfigurationWizard.PropertiesPanel.LookingLabel.text", Camera.Looking.class) //$NON-NLS-1$ //$NON-NLS-2$
                .choice("lightActuator", "CameraConfigurationWizard.LightPanel.LightActuatorLabel.text", lights, null) //$NON-NLS-1$ //$NON-NLS-2$
                .toggle("beforeCaptureLightOn", "CameraForm.Light.Before", "CameraForm.Light.Before.Note") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                .visibleWhen("lightActuator", Objects::nonNull) //$NON-NLS-1$
                .build();
    }

    /** What the calibration measured, read only, with the step that measures it at the right. */
    private FormWizard facts(ReferenceCamera camera) {
        boolean up = camera.getLooking() == Camera.Looking.Up;
        Form.Builder form = Form.of(new CameraFacts(camera, page.units())).named(camera.getName())
                .section("MachineSettings.Cameras.Measured", "target") //$NON-NLS-1$ //$NON-NLS-2$
                .measuredBy(up ? CalibrationStep.BottomCamera : CalibrationStep.PrimaryFiducial, camera)
                .readOnly("unitsPerPixel", "MachineSettings.Cameras.UnitsPerPixel"); //$NON-NLS-1$ //$NON-NLS-2$
        if (up) {
            form.readOnly("position", "MachineSettings.Cameras.Position"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        else {
            form.readOnly("defaultZ", "MachineSettings.Cameras.DefaultZ"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return form.readOnly("settle", "MachineSettings.Cameras.Settle").build(); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private static JComponent hint(SetupChecks.Check check) {
        JPanel box = new JPanel(new BorderLayout(8, 0));
        box.setOpaque(false);
        box.setBorder(new EmptyBorder(10, Tokens.PAD_SECTION, 2, Tokens.PAD_SECTION));
        JLabel text = new JLabel(check.text() + "\u3000" + check.fix(), //$NON-NLS-1$
                Ui.icon("alert", 14, Ui.warnText()), JLabel.LEFT); //$NON-NLS-1$
        text.setIconTextGap(6);
        text.setForeground(Ui.warnText());
        text.setFont(Ui.font(Tokens.FS_AUX));
        box.add(text, BorderLayout.CENTER);
        return box;
    }

    /** One camera, the stylesheet's {@code .camcard}: its name, how it is mounted, and a problem if it has one. */
    private final class CameraCard extends RoundedPanel {
        final ReferenceCamera camera;
        private final boolean[] on;
        private final JLabel line = Ui.t2(""); //$NON-NLS-1$
        private final Chip state = new Chip("", Chip.Tone.Warn, Chip.Shape.Status); //$NON-NLS-1$

        CameraCard(ReferenceCamera camera) {
            this(camera, new boolean[1]);
        }

        private CameraCard(ReferenceCamera camera, boolean[] on) {
            super(Tokens.R_MD, () -> on[0] ? Ui.accentSoft() : Ui.surface2(), () -> on[0] ? Ui.accent() : Ui.border());
            this.camera = camera;
            this.on = on;
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(10, 12, 10, 12));
            JLabel icon = new JLabel(Ui.icon("camera", 18, Ui.text2())); //$NON-NLS-1$
            add(icon, BorderLayout.WEST);
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            JLabel name = new JLabel(camera.getName());
            name.setFont(Ui.weighted(Tokens.FS_BODY, Tokens.FW_SECTION));
            text.add(name);
            text.add(Box.createVerticalStrut(2));
            line.setFont(Ui.font(Tokens.FS_AUX));
            text.add(line);
            add(text, BorderLayout.CENTER);
            add(state, BorderLayout.EAST);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    select(camera);
                }
            });
            describe();
        }

        void describe() {
            String mounted = camera.getHead() != null
                    ? String.format(Translations.getString("MachineSettings.Cameras.OnHead"), camera.getHead().getName()) //$NON-NLS-1$
                    : Translations.getString("MachineSettings.Cameras.OnMachine"); //$NON-NLS-1$
            line.setText(org.openpnp.gui.support.DisplayNames.of(camera.getLooking()) + " \u00b7 " + mounted); //$NON-NLS-1$
            SetupChecks.Check missing = page.getChecks().about(SetupChecks.DEVICE_MISSING, camera);
            SetupChecks.Check none = page.getChecks().about(SetupChecks.NO_DEVICE, camera);
            state.setText(Translations.getString(missing != null ? "MachineSettings.Cameras.Missing" //$NON-NLS-1$
                    : "MachineSettings.Cameras.NoDevice")); //$NON-NLS-1$
            state.setVisible(missing != null || none != null);
        }

        void setOn(boolean chosen) {
            on[0] = chosen;
            repaint();
        }
    }
}
