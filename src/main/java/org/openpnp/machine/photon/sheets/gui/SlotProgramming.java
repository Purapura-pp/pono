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

package org.openpnp.machine.photon.sheets.gui;

import java.awt.BorderLayout;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import org.openpnp.Translations;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Ui;
import org.openpnp.machine.photon.PhotonFeeder;
import org.openpnp.machine.photon.PhotonProperties;
import org.openpnp.machine.photon.protocol.PhotonBusInterface;
import org.openpnp.machine.photon.protocol.commands.ProgramFeederFloorAddress;
import org.openpnp.machine.photon.protocol.commands.UninitializedFeedersRespond;
import org.openpnp.spi.Machine;
import org.openpnp.util.UiUtils;
import org.pmw.tinylog.Logger;

/**
 * The slots' addresses programmed one feeder at a time: a feeder that has not been given an
 * address yet, put into a slot, gives that slot the address shown, which then goes up by one for
 * the next. Every other Photon feeder is out of the machine meanwhile, or it answers too.
 */
final class SlotProgramming extends JPanel {
    private static final int LAST = 254;

    private final Machine machine;
    private final PhotonProperties properties;
    private final Runnable programmed;
    private final JSpinner address = new JSpinner(new SpinnerNumberModel(1, 1, LAST, 1));
    private final AtomicInteger next = new AtomicInteger(1);
    private final JButton start;
    private final JLabel status = Ui.muted(""); //$NON-NLS-1$
    private volatile boolean running;

    /**
     * @param programmed Run on the UI's thread after each slot: the highest address searched may
     *        have gone up to it.
     */
    SlotProgramming(Machine machine, PhotonProperties properties, Runnable programmed) {
        super(new BorderLayout(0, 6));
        this.machine = machine;
        this.properties = properties;
        this.programmed = programmed;
        setOpaque(false);
        start = Ui.button(Translations.getString("PhotonForms.Program.Start"), Ui.iconSm("play"), //$NON-NLS-1$ //$NON-NLS-2$
                Ui.Size.Sm, Ui.Variant.Default);
        start.addActionListener(e -> {
            if (running) {
                stop(Translations.getString("PhotonForms.Program.Stopped")); //$NON-NLS-1$
            }
            else {
                begin();
            }
        });
        address.addChangeListener(e -> {
            next.set((Integer) address.getValue());
            if (running) {
                status.setText(String.format(Translations.getString("PhotonForms.Program.Waiting"), next.get())); //$NON-NLS-1$
            }
        });
        add(Forms.row(address, start), BorderLayout.NORTH);
        add(status, BorderLayout.CENTER);
        status.setText(Translations.getString("PhotonForms.Program.Idle")); //$NON-NLS-1$
    }

    private void begin() {
        if (!machine.isEnabled()) {
            UiUtils.showError(new Exception(Translations.getString("PhotonForms.Program.NotEnabled"))); //$NON-NLS-1$
            return;
        }
        running = true;
        start.setText(Translations.getString("PhotonForms.Program.Stop")); //$NON-NLS-1$
        start.setIcon(Ui.iconSm("stop")); //$NON-NLS-1$
        status.setText(String.format(Translations.getString("PhotonForms.Program.Waiting"), next.get())); //$NON-NLS-1$
        Thread worker = new Thread(this::work, "Photon slot programming"); //$NON-NLS-1$
        worker.setDaemon(true);
        worker.start();
    }

    /** On the UI's thread: the button back to Start and why it stopped. */
    private void stop(String why) {
        running = false;
        start.setText(Translations.getString("PhotonForms.Program.Start")); //$NON-NLS-1$
        start.setIcon(Ui.iconSm("play")); //$NON-NLS-1$
        address.setEnabled(true);
        status.setText(why);
    }

    /** Leaving the form stops it: nothing is programmed that nobody watches. */
    @Override
    public void removeNotify() {
        running = false;
        super.removeNotify();
    }

    private void work() {
        PhotonBusInterface bus = PhotonFeeder.getBus();
        while (running) {
            try {
                String uuid = uninitialized(bus);
                if (uuid == null) {
                    Thread.sleep(200);
                    continue;
                }
                if (!running) {
                    break;
                }
                int slot = next.get();
                SwingUtilities.invokeLater(() -> {
                    address.setEnabled(false);
                    status.setText(String.format(Translations.getString("PhotonForms.Program.Found"), slot)); //$NON-NLS-1$
                });
                program(bus, uuid, slot);
                PhotonFeeder feeder = new PhotonFeeder();
                feeder.setHardwareId(uuid);
                feeder.setSlotAddress(slot);
                UiUtils.submitUiMachineTask(feeder::initializeIfNeeded).get();
                if (!feeder.isInitialized()) {
                    throw new Exception(Translations.getString("PhotonForms.Program.NotInitialized")); //$NON-NLS-1$
                }
                properties.setMaxFeederAddress(Math.max(properties.getMaxFeederAddress(), slot));
                if (slot == LAST) {
                    SwingUtilities.invokeLater(() -> {
                        stop(Translations.getString("PhotonForms.Program.Last")); //$NON-NLS-1$
                        programmed.run();
                    });
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    address.setValue(slot + 1);
                    address.setEnabled(true);
                    status.setText(String.format(Translations.getString("PhotonForms.Program.Done"), slot, slot + 1)); //$NON-NLS-1$
                    programmed.run();
                });
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            catch (Exception e) {
                Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
                Logger.warn(cause, "Photon slot programming stopped."); //$NON-NLS-1$
                String why = String.format(Translations.getString("PhotonForms.Program.Failed"), cause.getMessage()); //$NON-NLS-1$
                SwingUtilities.invokeLater(() -> stop(why));
                return;
            }
        }
    }

    private static String uninitialized(PhotonBusInterface bus) throws Exception {
        return UiUtils.submitUiMachineTask(() -> {
            UninitializedFeedersRespond.Response response = new UninitializedFeedersRespond().send(bus);
            return response == null ? null : response.uuid;
        }).get();
    }

    private static void program(PhotonBusInterface bus, String uuid, int slot) throws Exception {
        UiUtils.submitUiMachineTask(() -> {
            if (new ProgramFeederFloorAddress(uuid, slot).send(bus) == null) {
                throw new Exception(Translations.getString("PhotonForms.Program.NoAnswer")); //$NON-NLS-1$
            }
        }).get();
    }
}
