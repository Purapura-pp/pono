/*
 * Copyright (C) 2011 Jason von Nieda <jason@vonnieda.org>
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

package org.openpnp.gui.components;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;

import org.openpnp.Translations;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.components.CameraView.RenderingQuality;
import org.openpnp.gui.components.CameraView.ZoomSensitivity;
import org.openpnp.gui.components.reticle.CrosshairReticle;
import org.openpnp.gui.components.reticle.FiducialReticle;
import org.openpnp.gui.components.reticle.GridReticle;
import org.openpnp.gui.components.reticle.Reticle;
import org.openpnp.gui.components.reticle.RulerReticle;
import org.openpnp.gui.processes.EstimateObjectZCoordinateProcess;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.Nozzle;
import org.openpnp.util.MovableUtils;
import org.openpnp.util.UiUtils;

// TODO: For the time being, since setting a property on the reticle doesn't re-save it we are
// making a redundant call to setReticle on every property update. Fix that somehow.
@SuppressWarnings("serial")
public class CameraViewPopupMenu extends JPopupMenu {
    private CameraView cameraView;
    private JMenu zoomIncMenu;
    private JMenu reticleMenu;
    private JMenu reticleOptionsMenu;
    private JMenu renderingQualityMenu;

    public CameraViewPopupMenu(CameraView cameraView) {
        this.cameraView = cameraView;

        // For cameras that have been calibrated at two different heights, add menu options to reset
        // the viewing plane and for estimating an object's height
        if (cameraView.isViewingPlaneChangable()) {
            JMenuItem mntmEstimateZCoordinate = new JMenuItem(Translations.getString("CameraViewPopupMenu.mntmEstimateZCoordinate.text")); //$NON-NLS-1$
            mntmEstimateZCoordinate.addActionListener(estimateZCoordinateAction);
            add(mntmEstimateZCoordinate);
        }

        // For non-movable cameras, add a menu option to move the selected nozzle to the camera
        if (cameraView.getCamera().getHead() == null) {
            JMenuItem mntmMoveSelectedNozzleToCamera = new JMenuItem(Translations.getString("CameraViewPopupMenu.mntmMoveSelectedNozzleToCamera.text")); //$NON-NLS-1$
            mntmMoveSelectedNozzleToCamera.addActionListener(moveSelectedNozzleToCameraAction);
            add(mntmMoveSelectedNozzleToCamera);
        }

        zoomIncMenu = createZoomIncMenu();

        add(zoomIncMenu);

        renderingQualityMenu = createRenderingQualityMenu();

        add(renderingQualityMenu);

        reticleMenu = createReticleMenu();

        add(reticleMenu);

        JCheckBoxMenuItem chkShowImageInfo = new JCheckBoxMenuItem(showImageInfoAction);
        chkShowImageInfo.setSelected(cameraView.isShowImageInfo());
        add(chkShowImageInfo);


        if (cameraView.getDefaultReticle() != null) {
            if (cameraView.getDefaultReticle() instanceof RulerReticle) {
                setReticleOptionsMenu(createRulerReticleOptionsMenu(
                        (RulerReticle) cameraView.getDefaultReticle()));
            }
            else if (cameraView.getDefaultReticle() instanceof GridReticle) {
                setReticleOptionsMenu(createRulerReticleOptionsMenu(
                        (GridReticle) cameraView.getDefaultReticle()));
            }
            else if (cameraView.getDefaultReticle() instanceof FiducialReticle) {
                setReticleOptionsMenu(createFiducialReticleOptionsMenu(
                        (FiducialReticle) cameraView.getDefaultReticle()));
            }
            else if (cameraView.getDefaultReticle() instanceof CrosshairReticle) {
                setReticleOptionsMenu(createCrosshairReticleOptionsMenu(
                        (CrosshairReticle) cameraView.getDefaultReticle()));
            }
        }
    }

    private JMenu createZoomIncMenu() {
        JMenu subMenu = new JMenu(Translations.getString("CameraViewPopupMenu.ZoomSensitivity.text")); //$NON-NLS-1$
        ButtonGroup buttonGroup = new ButtonGroup();
        JRadioButtonMenuItem menuItem = new JRadioButtonMenuItem(Translations.getString("CameraViewPopupMenu.High.text")); //$NON-NLS-1$
        buttonGroup.add(menuItem);
        if (cameraView.getZoomIncPerMouseWheelTick()
                == CameraView.zoomIncrements.get(ZoomSensitivity.High)) {
            menuItem.setSelected(true);
        }
        menuItem.setToolTipText(Translations.getString("CameraViewPopupMenu.menuItem.toolTipText")); //$NON-NLS-1$
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cameraView.setZoomIncPerMouseWheelTick(
                        CameraView.zoomIncrements.get(ZoomSensitivity.High));
            }
        });
        subMenu.add(menuItem);
        menuItem = new JRadioButtonMenuItem(Translations.getString("CameraViewPopupMenu.Medium.text")); //$NON-NLS-1$
        buttonGroup.add(menuItem);
        if (cameraView.getZoomIncPerMouseWheelTick()
                == CameraView.zoomIncrements.get(ZoomSensitivity.Medium)) {
            menuItem.setSelected(true);
        }
        menuItem.setToolTipText(Translations.getString("CameraViewPopupMenu.menuItem2.toolTipText")); //$NON-NLS-1$
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cameraView.setZoomIncPerMouseWheelTick(
                        CameraView.zoomIncrements.get(ZoomSensitivity.Medium));
            }
        });
        subMenu.add(menuItem);
        menuItem = new JRadioButtonMenuItem(Translations.getString("CameraViewPopupMenu.Low.text")); //$NON-NLS-1$
        buttonGroup.add(menuItem);
        if (cameraView.getZoomIncPerMouseWheelTick()
                == CameraView.zoomIncrements.get(ZoomSensitivity.Low)) {
            menuItem.setSelected(true);
        }
        menuItem.setToolTipText(Translations.getString("CameraViewPopupMenu.menuItem3.toolTipText")); //$NON-NLS-1$
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cameraView.setZoomIncPerMouseWheelTick(
                        CameraView.zoomIncrements.get(ZoomSensitivity.Low));
            }
        });
        subMenu.add(menuItem);
        
        return subMenu;
    }

    private JMenu createRenderingQualityMenu() {
        JMenu subMenu = new JMenu(Translations.getString("CameraViewPopupMenu.RenderingQuality.text")); //$NON-NLS-1$
        ButtonGroup buttonGroup = new ButtonGroup();
        JRadioButtonMenuItem menuItem;
        
        menuItem = new JRadioButtonMenuItem(Translations.getString("CameraViewPopupMenu.LowQuality.text")); //$NON-NLS-1$
        buttonGroup.add(menuItem);
        if (cameraView.getRenderingQuality() == RenderingQuality.Low) {
            menuItem.setSelected(true);
        }
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cameraView.setRenderingQuality(RenderingQuality.Low);
            }
        });
        subMenu.add(menuItem);
        
        menuItem = new JRadioButtonMenuItem(Translations.getString("CameraViewPopupMenu.HighQuality.text")); //$NON-NLS-1$
        buttonGroup.add(menuItem);
        if (cameraView.getRenderingQuality() == RenderingQuality.High) {
            menuItem.setSelected(true);
        }
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cameraView.setRenderingQuality(RenderingQuality.High);
            }
        });
        subMenu.add(menuItem);
        
        menuItem = new JRadioButtonMenuItem(Translations.getString("CameraViewPopupMenu.HighestQualityBestScale.text")); //$NON-NLS-1$
        buttonGroup.add(menuItem);
        if (cameraView.getRenderingQuality() == RenderingQuality.BestScale) {
            menuItem.setSelected(true);
        }
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cameraView.setRenderingQuality(RenderingQuality.BestScale);
            }
        });
        subMenu.add(menuItem);
        
        return subMenu;
    }

    private JMenu createReticleMenu() {
        JMenu menu = new JMenu(Translations.getString("CameraViewPopupMenu.Reticle.text")); //$NON-NLS-1$

        ButtonGroup buttonGroup = new ButtonGroup();

        JRadioButtonMenuItem menuItem;

        Reticle reticle = cameraView.getDefaultReticle();

        menuItem = new JRadioButtonMenuItem(noReticleAction);
        if (reticle == null) {
            menuItem.setSelected(true);
        }
        buttonGroup.add(menuItem);
        menu.add(menuItem);

        menuItem = new JRadioButtonMenuItem(crosshairReticleAction);
        if (reticle != null && reticle.getClass() == CrosshairReticle.class) {
            menuItem.setSelected(true);
        }
        buttonGroup.add(menuItem);
        menu.add(menuItem);

        menuItem = new JRadioButtonMenuItem(gridReticleAction);
        if (reticle != null && reticle.getClass() == GridReticle.class) {
            menuItem.setSelected(true);
        }
        buttonGroup.add(menuItem);
        menu.add(menuItem);

        menuItem = new JRadioButtonMenuItem(rulerReticleAction);
        if (reticle != null && reticle.getClass() == RulerReticle.class) {
            menuItem.setSelected(true);
        }
        buttonGroup.add(menuItem);
        menu.add(menuItem);

        menuItem = new JRadioButtonMenuItem(fiducialReticleAction);
        if (reticle != null && reticle.getClass() == FiducialReticle.class) {
            menuItem.setSelected(true);
        }
        buttonGroup.add(menuItem);
        menu.add(menuItem);

        return menu;
    }
    
    /**
     * The reticle menus only ever offer millimetres and inches, so anything else falls back to the
     * unit's own name rather than inventing a key that no bundle carries.
     */
    private static String unitsLabel(LengthUnit units) {
        if (units == LengthUnit.Millimeters) {
            return Translations.getString("CameraViewPopupMenu.Millimeters.text"); //$NON-NLS-1$
        }
        if (units == LengthUnit.Inches) {
            return Translations.getString("CameraViewPopupMenu.Inches.text"); //$NON-NLS-1$
        }
        return units.toString();
    }

    private void addColorMenuItems(JMenu menu, ButtonGroup buttonGroup, CrosshairReticle reticle) {
        menu.add(createColorMenuItem(Translations.getString("CameraViewPopupMenu.Color.Red"), Color.red, buttonGroup, reticle)); //$NON-NLS-1$
        menu.add(createColorMenuItem(Translations.getString("CameraViewPopupMenu.Color.Green"), Color.green, buttonGroup, reticle)); //$NON-NLS-1$
        menu.add(createColorMenuItem(Translations.getString("CameraViewPopupMenu.Color.Yellow"), Color.yellow, buttonGroup, reticle)); //$NON-NLS-1$
        menu.add(createColorMenuItem(Translations.getString("CameraViewPopupMenu.Color.Orange"), Color.decode("#ffd35d"), buttonGroup, reticle)); //$NON-NLS-1$ //$NON-NLS-2$
        menu.add(createColorMenuItem(Translations.getString("CameraViewPopupMenu.Color.Blue"), Color.blue, buttonGroup, reticle)); //$NON-NLS-1$
        menu.add(createColorMenuItem(Translations.getString("CameraViewPopupMenu.Color.White"), Color.white, buttonGroup, reticle)); //$NON-NLS-1$
    }

    private JMenuItem createColorMenuItem(String name, Color color, ButtonGroup buttonGroup, CrosshairReticle reticle) {
        JMenuItem menuItem = new JRadioButtonMenuItem(name);
        buttonGroup.add(menuItem);
        if (reticle.getColor().equals(color)) {
            menuItem.setSelected(true);
        }
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reticle.setColor(color);
                cameraView.setDefaultReticle(reticle);
            }
        });
        return menuItem;
    }

    private JMenu createCrosshairReticleOptionsMenu(final CrosshairReticle reticle) {
        JMenu menu = new JMenu(Translations.getString("CameraViewPopupMenu.Options.text")); //$NON-NLS-1$

        ButtonGroup buttonGroup = new ButtonGroup();

        addColorMenuItems(menu, buttonGroup, reticle);

        return menu;
    }

    private JMenu createRulerReticleOptionsMenu(final RulerReticle reticle) {
        JMenu menu = new JMenu(Translations.getString("CameraViewPopupMenu.Options.text")); //$NON-NLS-1$

        JMenu subMenu;
        JRadioButtonMenuItem menuItem;
        ButtonGroup buttonGroup;

        subMenu = new JMenu(Translations.getString("CameraViewPopupMenu.Color.text")); //$NON-NLS-1$
        buttonGroup = new ButtonGroup();
        addColorMenuItems(subMenu, buttonGroup, reticle);
        menu.add(subMenu);

        subMenu = new JMenu(Translations.getString("CameraViewPopupMenu.Units.text")); //$NON-NLS-1$
        buttonGroup = new ButtonGroup();
        menuItem = new JRadioButtonMenuItem(Translations.getString("CameraViewPopupMenu.Millimeters.text")); //$NON-NLS-1$
        buttonGroup.add(menuItem);
        if (reticle.getUnits() == LengthUnit.Millimeters) {
            menuItem.setSelected(true);
        }
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reticle.setUnits(LengthUnit.Millimeters);
                cameraView.setDefaultReticle(reticle);
            }
        });
        subMenu.add(menuItem);
        menuItem = new JRadioButtonMenuItem(Translations.getString("CameraViewPopupMenu.Inches.text")); //$NON-NLS-1$
        buttonGroup.add(menuItem);
        if (reticle.getUnits() == LengthUnit.Inches) {
            menuItem.setSelected(true);
        }
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reticle.setUnits(LengthUnit.Inches);
                cameraView.setDefaultReticle(reticle);
            }
        });
        subMenu.add(menuItem);
        menu.add(subMenu);

        subMenu = new JMenu(Translations.getString("CameraViewPopupMenu.UnitsPerTick.text")); //$NON-NLS-1$
        buttonGroup = new ButtonGroup();
        menuItem = new JRadioButtonMenuItem("0.1");
        buttonGroup.add(menuItem);
        if (reticle.getUnitsPerTick() == 0.1) {
            menuItem.setSelected(true);
        }
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reticle.setUnitsPerTick(0.1);
                cameraView.setDefaultReticle(reticle);
            }
        });
        subMenu.add(menuItem);
        menuItem = new JRadioButtonMenuItem("0.25");
        buttonGroup.add(menuItem);
        if (reticle.getUnitsPerTick() == 0.25) {
            menuItem.setSelected(true);
        }
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reticle.setUnitsPerTick(0.25);
                cameraView.setDefaultReticle(reticle);
            }
        });
        subMenu.add(menuItem);
        menuItem = new JRadioButtonMenuItem("0.50");
        buttonGroup.add(menuItem);
        if (reticle.getUnitsPerTick() == 0.50) {
            menuItem.setSelected(true);
        }
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reticle.setUnitsPerTick(0.50);
                cameraView.setDefaultReticle(reticle);
            }
        });
        subMenu.add(menuItem);
        menuItem = new JRadioButtonMenuItem("1");
        buttonGroup.add(menuItem);
        if (reticle.getUnitsPerTick() == 1) {
            menuItem.setSelected(true);
        }
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reticle.setUnitsPerTick(1);
                cameraView.setDefaultReticle(reticle);
            }
        });
        subMenu.add(menuItem);
        menuItem = new JRadioButtonMenuItem("2");
        buttonGroup.add(menuItem);
        if (reticle.getUnitsPerTick() == 2) {
            menuItem.setSelected(true);
        }
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reticle.setUnitsPerTick(2);
                cameraView.setDefaultReticle(reticle);
            }
        });
        subMenu.add(menuItem);
        menuItem = new JRadioButtonMenuItem("5");
        buttonGroup.add(menuItem);
        if (reticle.getUnitsPerTick() == 5) {
            menuItem.setSelected(true);
        }
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reticle.setUnitsPerTick(5);
                cameraView.setDefaultReticle(reticle);
            }
        });
        subMenu.add(menuItem);
        menuItem = new JRadioButtonMenuItem("10");
        buttonGroup.add(menuItem);
        if (reticle.getUnitsPerTick() == 10) {
            menuItem.setSelected(true);
        }
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reticle.setUnitsPerTick(10);
                cameraView.setDefaultReticle(reticle);
            }
        });
        subMenu.add(menuItem);
        menu.add(subMenu);

        return menu;
    }

    private JMenu createFiducialReticleOptionsMenu(final FiducialReticle reticle) {
        JMenu menu = new JMenu(Translations.getString("CameraViewPopupMenu.Options.text")); //$NON-NLS-1$

        JMenu subMenu;
        JRadioButtonMenuItem menuItem;
        ButtonGroup buttonGroup;

        subMenu = new JMenu(Translations.getString("CameraViewPopupMenu.Color.text")); //$NON-NLS-1$
        buttonGroup = new ButtonGroup();
        addColorMenuItems(subMenu, buttonGroup, reticle);
        menu.add(subMenu);

        subMenu = new JMenu(Translations.getString("CameraViewPopupMenu.Units.text")); //$NON-NLS-1$
        buttonGroup = new ButtonGroup();
        menuItem = new JRadioButtonMenuItem(Translations.getString("CameraViewPopupMenu.Millimeters.text")); //$NON-NLS-1$
        buttonGroup.add(menuItem);
        if (reticle.getUnits() == LengthUnit.Millimeters) {
            menuItem.setSelected(true);
        }
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reticle.setUnits(LengthUnit.Millimeters);
                cameraView.setDefaultReticle(reticle);
            }
        });
        subMenu.add(menuItem);
        menuItem = new JRadioButtonMenuItem(Translations.getString("CameraViewPopupMenu.Inches.text")); //$NON-NLS-1$
        buttonGroup.add(menuItem);
        if (reticle.getUnits() == LengthUnit.Inches) {
            menuItem.setSelected(true);
        }
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reticle.setUnits(LengthUnit.Inches);
                cameraView.setDefaultReticle(reticle);
            }
        });
        subMenu.add(menuItem);
        menu.add(subMenu);

        subMenu = new JMenu(Translations.getString("CameraViewPopupMenu.Shape.text")); //$NON-NLS-1$
        buttonGroup = new ButtonGroup();
        menuItem = new JRadioButtonMenuItem(Translations.getString("CameraViewPopupMenu.Circle.text")); //$NON-NLS-1$
        buttonGroup.add(menuItem);
        if (reticle.getShape() == FiducialReticle.Shape.Circle) {
            menuItem.setSelected(true);
        }
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reticle.setShape(FiducialReticle.Shape.Circle);
                cameraView.setDefaultReticle(reticle);
            }
        });
        subMenu.add(menuItem);
        menuItem = new JRadioButtonMenuItem(Translations.getString("CameraViewPopupMenu.Square.text")); //$NON-NLS-1$
        buttonGroup.add(menuItem);
        if (reticle.getShape() == FiducialReticle.Shape.Square) {
            menuItem.setSelected(true);
        }
        menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reticle.setShape(FiducialReticle.Shape.Square);
                cameraView.setDefaultReticle(reticle);
            }
        });
        subMenu.add(menuItem);
        menu.add(subMenu);

        JCheckBoxMenuItem chkMenuItem = new JCheckBoxMenuItem(Translations.getString("CameraViewPopupMenu.Filled.text")); //$NON-NLS-1$
        chkMenuItem.setSelected(reticle.isFilled());
        chkMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                reticle.setFilled(((JCheckBoxMenuItem) e.getSource()).isSelected());
                cameraView.setDefaultReticle(reticle);
            }
        });
        menu.add(chkMenuItem);

        JMenuItem inputMenuItem = new JMenuItem(Translations.getString("CameraViewPopupMenu.Size.text")); //$NON-NLS-1$
        inputMenuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String result = JOptionPane.showInputDialog(cameraView,
                        String.format(
                                Translations.getString("CameraViewPopupMenu.Reticle.EnterSize"), //$NON-NLS-1$
                                unitsLabel(reticle.getUnits())),
                        reticle.getSize() + "");
                if (result != null) {
                    reticle.setSize(Double.valueOf(result));
                    cameraView.setDefaultReticle(reticle);
                }
            }
        });
        menu.add(inputMenuItem);

        return menu;
    }

    private void setReticleOptionsMenu(JMenu menu) {
        if (reticleOptionsMenu != null) {
            reticleMenu.remove(reticleMenu.getMenuComponentCount() - 1);
            reticleMenu.remove(reticleMenu.getMenuComponentCount() - 1);
        }
        if (menu != null) {
            reticleMenu.addSeparator();
            reticleMenu.add(menu);
        }
        reticleOptionsMenu = menu;
    }

    private Action showImageInfoAction = new AbstractAction(Translations.getString("CameraViewPopupMenu.ShowImageInfo")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent e) {
            cameraView.setShowImageInfo(((JCheckBoxMenuItem) e.getSource()).isSelected());
        }
    };

    private Action noReticleAction = new AbstractAction(Translations.getString("CameraViewPopupMenu.Reticle.None")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            setReticleOptionsMenu(null);
            cameraView.setDefaultReticle(null);
        }
    };

    private Action crosshairReticleAction = new AbstractAction(Translations.getString("CameraViewPopupMenu.Reticle.Crosshair")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            CrosshairReticle reticle = new CrosshairReticle();
            JMenu optionsMenu = createCrosshairReticleOptionsMenu(reticle);
            setReticleOptionsMenu(optionsMenu);
            cameraView.setDefaultReticle(reticle);
        }
    };

    private Action gridReticleAction = new AbstractAction(Translations.getString("CameraViewPopupMenu.Reticle.Grid")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            GridReticle reticle = new GridReticle();
            JMenu optionsMenu = createRulerReticleOptionsMenu(reticle);
            setReticleOptionsMenu(optionsMenu);
            cameraView.setDefaultReticle(reticle);
        }
    };

    private Action rulerReticleAction = new AbstractAction(Translations.getString("CameraViewPopupMenu.Reticle.Ruler")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            RulerReticle reticle = new RulerReticle();
            JMenu optionsMenu = createRulerReticleOptionsMenu(reticle);
            setReticleOptionsMenu(optionsMenu);
            cameraView.setDefaultReticle(reticle);
        }
    };

    private Action fiducialReticleAction = new AbstractAction(Translations.getString("CameraViewPopupMenu.Reticle.Fiducial")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent arg0) {
            FiducialReticle reticle = new FiducialReticle();
            JMenu optionsMenu = createFiducialReticleOptionsMenu(reticle);
            setReticleOptionsMenu(optionsMenu);
            cameraView.setDefaultReticle(reticle);
        }
    };

    /**
     * Listen for menu selection to estimate an object's height
     */
    private ActionListener estimateZCoordinateAction = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            UiUtils.messageBoxOnException(() -> {
                new EstimateObjectZCoordinateProcess(MainFrame.get(), cameraView);
            });
        }
    };

    /**
     * Listener for menu selection to move the selected nozzle to the camera (only works for
     * non-movable cameras)
     */
    private ActionListener moveSelectedNozzleToCameraAction = new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
            UiUtils.submitUiMachineTask(() -> {
                // Get the selected nozzle
                Nozzle nozzle = MainFrame.get().getMachineControls().getSelectedNozzle();
                // Add the offsets to the Camera's nozzle calibrated position.
                Location location = cameraView.getCamera().getLocation(nozzle);
                // Don't change rotation. 
                location = nozzle.getLocation().derive(location, true, true, true, false);
                // Move the nozzle to the camera
                MovableUtils.moveToLocationAtSafeZ(nozzle, location);
                MovableUtils.fireTargetedUserAction(nozzle);
            });
        }
    };

}
