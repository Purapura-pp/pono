package org.openpnp.gui.tablemodel;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import org.openpnp.Translations;
import org.openpnp.gui.support.DisplayNames;
import org.openpnp.gui.support.TableUtils;
import org.openpnp.model.AbstractVisionSettings;
import org.openpnp.model.BottomVisionSettings;
import org.openpnp.model.Configuration;
import org.openpnp.vision.pipeline.CvPipeline;

/**
 * The vision settings as mockup 11 lists them: the name, which kind they are, whether they are on,
 * the pipeline's stages by what they do, and what uses them. The table had the name and the use.
 */
@SuppressWarnings("serial")
public class VisionSettingsTableModel extends AbstractObjectTableModel
        implements PropertyChangeListener, TableUtils.ColumnKinds {
    public static final int NAME = 0;
    public static final int TYPE = 1;
    public static final int ENABLED = 2;
    public static final int PIPELINE = 3;
    public static final int USED_IN = 4;

    private String[] columnNames = new String[]{
            Translations.getString("VisionSettingsTableModel.ColumnName.Name"), //$NON-NLS-1$
            Translations.getString("VisionSettingsTableModel.ColumnName.Type"), //$NON-NLS-1$
            Translations.getString("VisionSettingsTableModel.ColumnName.Enabled"), //$NON-NLS-1$
            Translations.getString("VisionSettingsTableModel.ColumnName.Pipeline"), //$NON-NLS-1$
            Translations.getString("VisionSettingsTableModel.ColumnName.AssignedTo") //$NON-NLS-1$
    };
    @SuppressWarnings("rawtypes")
    private Class[] columnTypes = new Class[] {String.class, String.class, Boolean.class, String.class, String.class};

    private List<AbstractVisionSettings> visionSettings;

    private final Configuration configuration;

    public VisionSettingsTableModel(Configuration configuration) {
        this.configuration = configuration;
        configuration.addPropertyChangeListener("visionSettings", this); //$NON-NLS-1$
        visionSettings = new ArrayList<>(configuration.getVisionSettings());
        for (AbstractVisionSettings settings : visionSettings) {
            settings.addPropertyChangeListener(this);
        }
    }

    @Override
    public TableUtils.Kind[] getColumnKinds() {
        return new TableUtils.Kind[] { TableUtils.Kind.Id, TableUtils.Kind.Status, TableUtils.Kind.Check,
                TableUtils.Kind.Name, TableUtils.Kind.Secondary };
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getSource() instanceof AbstractVisionSettings) {
            fireTableDataChanged();
        } else {
            if (visionSettings != null) {
                for (AbstractVisionSettings visionSettings : this.visionSettings) {
                    visionSettings.removePropertyChangeListener(this);
                }
            }
            visionSettings = new ArrayList<>(configuration.getVisionSettings());
            fireTableDataChanged();
            for (AbstractVisionSettings visionSettings : this.visionSettings) {
                visionSettings.addPropertyChangeListener(this);
            }
        }
    }

    @Override
    public int getRowCount() {
        return (visionSettings == null) ? 0 : visionSettings.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnTypes[columnIndex];
    }

    /** The name and whether they are on; the built-in settings are only shown. */
    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return (columnIndex == NAME || columnIndex == ENABLED) && !getRowObjectAt(rowIndex).isStockSetting();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        AbstractVisionSettings visionSettings = this.visionSettings.get(rowIndex);
        switch (columnIndex) {
            case NAME:
                return DisplayNames.visionSettingsName(visionSettings.getName());
            case TYPE:
                return kind(visionSettings);
            case ENABLED:
                return visionSettings.isEnabled();
            case PIPELINE:
                return stages(visionSettings.getPipeline());
            case USED_IN: {
                // Both kinds of use: a fiducial vision setting is assigned to parts and packages
                // as much as a bottom vision one is, and this column used to show it as unused.
                String used = DisplayNames.usedIn(visionSettings.getUsedIn());
                return used.isEmpty() ? Translations.getString(visionSettings.isStockSetting()
                        ? "VisionSettingsTableModel.BuiltIn" : "VisionSettingsTableModel.Unused") : used; //$NON-NLS-1$ //$NON-NLS-2$
            }
            default:
                return null;
        }
    }

    /** "Bottom vision" or "Fiducial vision". */
    public static String kind(AbstractVisionSettings settings) {
        return Translations.getString(settings instanceof BottomVisionSettings
                ? "VisionSettingsTableModel.Type.Bottom" : "VisionSettingsTableModel.Type.Fiducial"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /** What the pipeline does, "阈值 › 找轮廓 › 最小外接矩形". */
    public static String stages(CvPipeline pipeline) {
        return org.openpnp.gui.support.PipelineStages.text(pipeline);
    }

    @Override
    public AbstractVisionSettings getRowObjectAt(int index) {
        return visionSettings.get(index);
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        AbstractVisionSettings visionSettings = this.visionSettings.get(rowIndex);
        if (columnIndex == NAME) {
            visionSettings.setName((String) aValue);
            configuration.setDirty(true);
        }
        else if (columnIndex == ENABLED) {
            visionSettings.setEnabled(Boolean.TRUE.equals(aValue));
            configuration.setDirty(true);
        }
    }

    @Override
    public int indexOf(Object selectedVisionSettings) {
        return visionSettings.indexOf(selectedVisionSettings);
    }

}
