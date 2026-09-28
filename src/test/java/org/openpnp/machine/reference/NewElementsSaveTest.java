package org.openpnp.machine.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openpnp.model.Configuration;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Axis;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Driver;
import org.openpnp.spi.Feeder;
import org.openpnp.spi.Head;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.Signaler;

/**
 * Everything the machine offers to make is written into machine.xml as it comes, before anyone
 * has set anything on it: a required attribute it leaves null fails the save of the whole file.
 */
public class NewElementsSaveTest {
    @TempDir
    Path tempDir;

    private ReferenceMachine machine;

    private final List<String> failed = new ArrayList<>();

    private interface Step<T> {
        void run(T element) throws Exception;
    }

    @BeforeEach
    public void setUp() throws Exception {
        Configuration.initialize(tempDir.resolve(".openpnp").toFile());
        Configuration.get().load();
        machine = (ReferenceMachine) Configuration.get().getMachine();
    }

    @AfterEach
    public void tearDown() throws Exception {
        machine.close();
    }

    @Test
    public void everyKindOfElementCanBeSavedAsItIsMade() throws Exception {
        Head head = machine.getDefaultHead();
        for (Class<? extends Feeder> type : machine.getCompatibleFeederClasses()) {
            saved(type, machine::addFeeder, machine::removeFeeder);
        }
        for (Class<? extends Camera> type : machine.getCompatibleCameraClasses()) {
            saved(type, machine::addCamera, machine::removeCamera);
        }
        for (Class<? extends Actuator> type : machine.getCompatibleActuatorClasses()) {
            saved(type, machine::addActuator, machine::removeActuator);
        }
        for (Class<? extends Axis> type : machine.getCompatibleAxisClasses()) {
            saved(type, machine::addAxis, machine::removeAxis);
        }
        for (Class<? extends Driver> type : machine.getCompatibleDriverClasses()) {
            saved(type, machine::addDriver, machine::removeDriver);
        }
        for (Class<? extends Signaler> type : machine.getCompatibleSignalerClasses()) {
            saved(type, machine::addSignaler, machine::removeSignaler);
        }
        for (Class<? extends Nozzle> type : machine.getCompatibleNozzleClasses()) {
            saved(type, head::addNozzle, head::removeNozzle);
        }
        saved(ReferenceNozzleTip.class, machine::addNozzleTip, machine::removeNozzleTip);
        assertEquals(List.of(), failed, "made new and not saved");
    }

    private <T> void saved(Class<? extends T> type, Step<T> add, Step<T> remove) throws Exception {
        T element;
        try {
            element = type.getDeclaredConstructor().newInstance();
            add.run(element);
        }
        catch (Throwable e) {
            // A camera whose capture library is not on this computer cannot even be made; that is
            // not what this is about.
            return;
        }
        try {
            Configuration.get().machineXml();
        }
        catch (Exception e) {
            failed.add(type.getSimpleName() + ": " + e.getMessage());
        }
        finally {
            remove.run(element);
        }
    }
}
