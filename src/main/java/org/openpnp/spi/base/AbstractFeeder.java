package org.openpnp.spi.base;

import javax.swing.Icon;

import org.openpnp.Translations;
import org.openpnp.gui.support.Icons;
import org.openpnp.gui.support.PropertySheetWizardAdapter;
import org.openpnp.model.Configuration;
import org.openpnp.model.Part;
import org.openpnp.spi.Feeder;
import org.openpnp.spi.Nozzle;
import org.simpleframework.xml.Attribute;

public abstract class AbstractFeeder extends AbstractMachineElement implements Feeder {
    /**
     * History:
     * 
     * Note: Can't actually use the @Version annotation because of a bug in SimpleXML. See
     * http://sourceforge.net/p/simple/mailman/message/27887562/
     * 
     * 1.0: Initial revision. 
     * 1.1: Migrate retryCount to feedRetryCount and zero out pickRetryCount for initial release
     *      of feature.
     */
    @Attribute(required=false)
    private double version = 1.0;
    
    @Attribute
    protected String id;

    @Attribute(required = false)
    protected String name;

    @Attribute
    protected boolean enabled;

    @Attribute
    protected String partId;
    
    /**
     * Note: This is feedRetryCount in reality. It was left as retryCount for backwards
     * compatibility when pickRetryCount was added. 
     * 
     * TODO Migration has been added and this can be removed after 2021-12-29.  
     */
    @Attribute(required=false)
    protected Integer retryCount = 3;
    
    @Attribute(required=false)
    protected int feedRetryCount = 3;
    
    @Attribute(required = false)
    protected int pickRetryCount = 3;

    protected Part part;

    public AbstractFeeder() {
        this.id = Configuration.createId("FDR");
        this.name = getClass().getSimpleName();
    }

    @Override
    public void configurationLoaded(Configuration configuration) throws Exception {
        super.configurationLoaded(configuration);
        part = configuration.getPart(partId);

        if (version == 1.0) {
            feedRetryCount = retryCount;
            retryCount = null;
            pickRetryCount = 0;
            version = 1.1;
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        Object oldValue = this.enabled;
        this.enabled = enabled;
        firePropertyChange("enabled", oldValue, enabled);
    }

    @Override
    public void setPart(Part part) {
        Part oldValue = this.part;
        this.part = part;
        firePropertyChange("part", oldValue, part);
        if (part != null) {
            this.partId = part.getId();
        }
        else {
            this.partId = "";
        }
        // Also notify the old/new part that the feeder count has changed.
        if (oldValue != null) {
            oldValue.setAssignedFeeders(-1);
        }
        if (part != null) {
            part.setAssignedFeeders(+1);
        }
    }

    @Override
    public Part getPart() {
        return part;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
        firePropertyChange("name", null, name);
    }

    @Override
    public Icon getPropertySheetHolderIcon() {
        return Icons.feeder;
    }

    public int getFeedRetryCount() {
        return feedRetryCount;
    }

    public void setFeedRetryCount(int feedRetryCount) {
        this.feedRetryCount = feedRetryCount;
        firePropertyChange("feedRetryCount", null, feedRetryCount);
    }
    
    public int getPickRetryCount() {
        return pickRetryCount;
    }

    public void setPickRetryCount(int pickRetryCount) {
        this.pickRetryCount = pickRetryCount;
        firePropertyChange("pickRetryCount", null, pickRetryCount);
    }

    @Override
    public PropertySheet[] getPropertySheets() {
        return new PropertySheet[] {new PropertySheetWizardAdapter(getConfigurationWizard(),
                Translations.getString("AbstractFeeder.ConfigurationWizard.title")), //$NON-NLS-1$
                new PropertySheetWizardAdapter(org.openpnp.gui.support.FeederStockForm.build(this),
                        Translations.getString("AbstractFeeder.Stock.title"))}; //$NON-NLS-1$
    }
    
    public void postPick(Nozzle nozzle) throws Exception { }
    
    @Override
    public boolean canTakeBackPart() {
        return false;   // default feeder does not take back parts
    }

    @Override
    public void takeBackPart(Nozzle nozzle) throws Exception {
        throw new UnsupportedOperationException("Not supported on this Feeder");
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        Object oldValue = this.priority;
        this.priority = priority;
        firePropertyChange("priority", oldValue, priority);
    }

    @Attribute(required=false)
    protected Priority priority = Priority.Normal;

    /** Where the feeder sits on the machine as the people at it name the place: "A3", "B2". */
    @Attribute(required = false)
    protected String slotName;

    @Attribute(required = false)
    protected long lastPickMillis;

    /**
     * Parts loaded at the last refill, for a feeder that does not count its parts from its own
     * geometry: 0 is not known.
     */
    @Attribute(required = false)
    protected int loadedCount;

    @Attribute(required = false)
    protected int pickedSinceRefill;

    @Attribute(required = false)
    protected int lowCount;

    /**
     * Parts left counted from what was loaded at the last refill. A feeder that knows its
     * capacity from its geometry, a strip's maximum feed count say, answers from that instead.
     */
    @Override
    public Integer getPartsLeft() {
        return loadedCount > 0 ? Math.max(0, loadedCount - pickedSinceRefill) : null;
    }

    /**
     * The feeder works its parts left out from its own geometry and feed count, a tray's say,
     * rather than from a count of the parts loaded.
     */
    public boolean isCountedFromGeometry() {
        return false;
    }

    public String getSlotName() {
        return slotName;
    }

    public void setSlotName(String slotName) {
        String oldValue = this.slotName;
        this.slotName = slotName == null || slotName.trim().isEmpty() ? null : slotName.trim();
        firePropertyChange("slotName", oldValue, this.slotName);
    }

    @Override
    public long getLastPickMillis() {
        return lastPickMillis;
    }

    /** For a feeder brought over from elsewhere, and for pictures of a machine at work. */
    public void setLastPickMillis(long lastPickMillis) {
        long oldValue = this.lastPickMillis;
        this.lastPickMillis = lastPickMillis;
        firePropertyChange("lastPickMillis", oldValue, lastPickMillis);
    }

    public int getLoadedCount() {
        return loadedCount;
    }

    public void setLoadedCount(int loadedCount) {
        Integer oldLeft = getPartsLeft();
        this.loadedCount = Math.max(0, loadedCount);
        firePropertyChange("loadedCount", null, this.loadedCount);
        firePartsLeft(oldLeft);
    }

    public int getPickedSinceRefill() {
        return pickedSinceRefill;
    }

    @Override
    public int getLowCount() {
        return lowCount;
    }

    public void setLowCount(int lowCount) {
        int oldValue = this.lowCount;
        this.lowCount = Math.max(0, lowCount);
        firePropertyChange("lowCount", oldValue, this.lowCount);
    }

    @Override
    public void recordPick() {
        Integer oldLeft = getPartsLeft();
        long oldValue = lastPickMillis;
        lastPickMillis = System.currentTimeMillis();
        pickedSinceRefill++;
        firePropertyChange("lastPickMillis", oldValue, lastPickMillis);
        firePartsLeft(oldLeft);
    }

    @Override
    public void refill(Integer partsLoaded) {
        Integer oldLeft = getPartsLeft();
        if (partsLoaded != null) {
            loadedCount = Math.max(0, partsLoaded);
            firePropertyChange("loadedCount", null, loadedCount);
        }
        pickedSinceRefill = 0;
        firePartsLeft(oldLeft);
    }

    /**
     * Tells listeners that the parts left may have changed; also for subclasses whose own
     * counters it is worked out from.
     */
    protected void firePartsLeft(Integer oldLeft) {
        firePropertyChange("partsLeft", oldLeft, getPartsLeft());
    }
}
