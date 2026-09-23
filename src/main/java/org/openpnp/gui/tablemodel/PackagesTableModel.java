/*
 * Copyright (C) 2011 Jason von Nieda <jason@vonnieda.org>
 * 
 * This file is package of OpenPnP.
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

package org.openpnp.gui.tablemodel;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import org.openpnp.Translations;
import org.openpnp.model.BottomVisionSettings;
import org.openpnp.model.Configuration;
import org.openpnp.model.FiducialVisionSettings;
import org.openpnp.model.Package;

/**
 * The packages as mockup 08 has them: the ID, the body, the pads, the nozzle tips that can pick
 * it - "未分配" where none can - the bottom vision and how many parts use it. The description,
 * the tape and the fiducial vision are hidden until the column settings bring them back.
 */
@SuppressWarnings("serial")
public class PackagesTableModel extends AbstractObjectTableModel implements PropertyChangeListener,
        org.openpnp.gui.support.TableUtils.ColumnKinds, org.openpnp.gui.support.TableUtils.DefaultHidden {
    public static final int BODY = 5;
    public static final int PADS = 6;
    public static final int NOZZLE_TIPS = 7;
    public static final int PARTS = 8;

    final private Configuration configuration;

    private String[] columnNames = new String[] {
            Translations.getString("PackagesTableModel.ColumnName.ID"), //$NON-NLS-1$
            Translations.getString("PackagesTableModel.ColumnName.Description"), //$NON-NLS-1$
            Translations.getString("PackagesTableModel.ColumnName.TapeSpecification"), //$NON-NLS-1$
            Translations.getString("PackagesTableModel.ColumnName.BottomVision"), //$NON-NLS-1$
            Translations.getString("PackagesTableModel.ColumnName.FiducialVision"), //$NON-NLS-1$
            Translations.getString("PackagesTableModel.ColumnName.Body"), //$NON-NLS-1$
            Translations.getString("PackagesTableModel.ColumnName.Pads"), //$NON-NLS-1$
            Translations.getString("PackagesTableModel.ColumnName.NozzleTips"), //$NON-NLS-1$
            Translations.getString("PackagesTableModel.ColumnName.Parts") //$NON-NLS-1$
    };
    private Class[] columnTypes = new Class[] {String.class, String.class, String.class, BottomVisionSettings.class,
            FiducialVisionSettings.class, String.class, Integer.class, String.class, Integer.class};

    @Override
    public int[] getDefaultHiddenColumns() {
        return new int[] { 1, 2, 4 };
    }

    @Override
    public org.openpnp.gui.support.TableUtils.Kind[] getColumnKinds() {
        org.openpnp.gui.support.TableUtils.Kind name = org.openpnp.gui.support.TableUtils.Kind.Name;
        org.openpnp.gui.support.TableUtils.Kind number = org.openpnp.gui.support.TableUtils.Kind.Number;
        return new org.openpnp.gui.support.TableUtils.Kind[] { org.openpnp.gui.support.TableUtils.Kind.Id, name,
                name, name, org.openpnp.gui.support.TableUtils.Kind.Secondary, number, number, name, number };
    }

    /** The nozzle tips that can pick the package, by name, or empty when none can. */
    public static String nozzleTips(Package packag) {
        List<String> names = new ArrayList<>();
        for (org.openpnp.spi.NozzleTip tip : packag.getCompatibleNozzleTips()) {
            names.add(tip.getName());
        }
        return String.join("\u3001", names); //$NON-NLS-1$
    }
    private List<Package> packages;

    public PackagesTableModel(Configuration configuration) {
        this.configuration = configuration;
        configuration.addPropertyChangeListener("packages", this);
        packages = new ArrayList<>(configuration.getPackages());

    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    public int getColumnCount() {
        return columnNames.length;
    }

    public int getRowCount() {
        return (packages == null) ? 0 : packages.size();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnTypes[columnIndex];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex >= 1 && columnIndex <= 4;
    }

    @Override
    public Package getRowObjectAt(int index) {
        return packages.get(index);
    }

    @Override
    public int indexOf(Object selectedPackage) {
        return packages.indexOf(selectedPackage);
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        try {
            Package this_package = packages.get(rowIndex);
            if (columnIndex == 1) {
                this_package.setDescription((String) aValue);
            }
            else if (columnIndex == 2) {
                this_package.setTapeSpecification((String) aValue);
            }
            else if (columnIndex == 3) {
                this_package.setBottomVisionSettings((BottomVisionSettings) aValue);
            }
            else if (columnIndex == 4) {
                this_package.setFiducialVisionSettings((FiducialVisionSettings) aValue);
            }
            configuration.setDirty(true);
        }
        catch (Exception e) {
            org.openpnp.gui.support.TableUtils.rejected(this, columnIndex, aValue, e);
        }
    }

    public Object getValueAt(int row, int col) {
        Package this_package = packages.get(row);
        switch (col) {
            case 0:
                return this_package.getId();
            case 1:
                return this_package.getDescription();
            case 2:
                return this_package.getTapeSpecification();
            case 3:
                return this_package.getBottomVisionSettings();
            case 4:
                return this_package.getFiducialVisionSettings();
            case BODY: {
                org.openpnp.model.Footprint footprint = this_package.getFootprint();
                return footprint == null ? "\u2014" //$NON-NLS-1$
                        : number(footprint.getBodyWidth()) + " \u00d7 " + number(footprint.getBodyHeight()); //$NON-NLS-1$
            }
            case PADS:
                return this_package.getFootprint() == null ? 0 : this_package.getFootprint().getPads().size();
            case NOZZLE_TIPS:
                return nozzleTips(this_package);
            case PARTS: {
                int parts = 0;
                for (org.openpnp.model.Part part : configuration.getParts()) {
                    if (part.getPackage() == this_package) {
                        parts++;
                    }
                }
                return parts;
            }
            default:
                return null;
        }
    }

    /** "1.6", "1.25": the body in the footprint's units without the trailing zeros. */
    private static String number(double value) {
        String text = String.format(java.util.Locale.ROOT, "%.3f", value).replaceAll("0+$", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text; //$NON-NLS-1$
    }

    @Override
    public void propertyChange(PropertyChangeEvent arg0) {
        packages = new ArrayList<>(configuration.getPackages());
        fireTableDataChanged();
    }
}
