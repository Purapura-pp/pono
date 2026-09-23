package org.openpnp.gui.audit;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.openpnp.machine.reference.camera.ImageCamera;
import org.openpnp.machine.reference.feeder.ReferenceAutoFeeder;
import org.openpnp.machine.reference.feeder.ReferenceStripFeeder;
import org.openpnp.machine.reference.feeder.ReferenceTrayFeeder;
import org.openpnp.model.Abstract2DLocatable.Side;
import org.openpnp.model.Board;
import org.openpnp.model.BoardLocation;
import org.openpnp.model.Configuration;
import org.openpnp.model.Footprint;
import org.openpnp.model.Job;
import org.openpnp.model.Length;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.model.Package;
import org.openpnp.model.Panel;
import org.openpnp.model.PanelLocation;
import org.openpnp.model.Part;
import org.openpnp.model.Placement;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Feeder;
import org.openpnp.spi.Machine;

/**
 * Writes the configuration the UI ruler photographs: the scene of the mockups, on the simulated
 * machine of the default configuration. 48 placements on the board the job mockup shows, with
 * R11, R12, C14, U3, FID1 and D2 first, 24 feeders named as on the feeders mockup, and a 16-up
 * panel for production mode.
 *
 * <pre>
 * java -cp target/openpnp-gui-0.0.1-alpha-SNAPSHOT.jar;target/test-classes \
 *     org.openpnp.gui.audit.UiFixture E:/pono-env/ui-fixture
 * </pre>
 *
 * The directory is emptied first. It is written through the model rather than as XML, so that it
 * can simply be written again when the model changes.
 */
public class UiFixture {
    private static final LengthUnit MM = LengthUnit.Millimeters;
    /** Where the ruler parks the nozzle, and with it the camera: what the job mockup's DRO reads. */
    static final Location PARKED = new Location(MM, 120.45, 85.21, -2.0, 90.0);
    /** The mockups' board, rendered by tools/ui-ruler/render-mockups.ps1 from design/mockups. */
    private static final File CAMERA_PICTURE =
            new File("E:/pono-env/ui-mockups/rendered/camera-pcb.png");
    /** 26.7 mm across the picture's 1920 pixels: R12 and its neighbours, as the mockup shows. */
    private static final double CAMERA_UPP = 0.01393;

    private final Configuration configuration;
    private final File jobs;
    private final Map<String, Part> parts = new LinkedHashMap<>();

    private UiFixture(Configuration configuration, File jobs) {
        this.configuration = configuration;
        this.jobs = jobs;
    }

    public static void main(String[] args) throws Exception {
        File directory = new File(args.length > 0 ? args[0] : "E:/pono-env/ui-fixture")
                .getCanonicalFile();
        empty(directory.toPath());
        File jobs = new File(directory, "jobs");
        jobs.mkdirs();

        Configuration.initialize(directory);
        Configuration configuration = Configuration.get();
        configuration.load();

        UiFixture fixture = new UiFixture(configuration, jobs);
        fixture.write();
        // The save backs up the defaults it replaces, which are of no use to anyone here.
        Path backups = new File(directory, "backups").toPath();
        empty(backups);
        Files.deleteIfExists(backups);
        System.out.println("UI fixture written to " + directory);
        System.exit(0);
    }

    private void write() throws Exception {
        Machine machine = configuration.getMachine();
        // The welcome dialog is modal and would stop every photograph.
        machine.setProperty("Welcome2_0_Dialog_Shown", true);

        for (Part part : List.copyOf(configuration.getParts())) {
            configuration.removePart(part);
        }
        for (Feeder feeder : List.copyOf(machine.getFeeders())) {
            machine.removeFeeder(feeder);
        }

        packagesAndParts();
        feeders(machine);
        camera(machine);
        nozzleTip(machine);

        Board board = demoBoard();
        Board cell = demoCell();
        Panel panel = demoPanel(cell);
        demoBoardJob(board);
        demoPanelJob(panel);

        configuration.save();
    }

    private void packagesAndParts() {
        pkg("R0402", 1.0, 0.5, twoPads(0.5, 0.6, 0.5));
        pkg("R0603", 1.6, 0.8, twoPads(0.75, 0.9, 0.8));
        pkg("C0402", 1.0, 0.5, twoPads(0.5, 0.6, 0.5));
        pkg("C0603", 1.6, 0.8, twoPads(0.75, 0.9, 0.8));
        pkg("C0805", 2.0, 1.25, twoPads(0.95, 1.4, 1.0));
        pkg("LED-0805", 2.0, 1.25, twoPads(0.95, 1.4, 1.0));
        pkg("SOT-23", 2.9, 1.3, sot23());
        pkg("SOIC-8", 4.9, 3.9, soic8());
        pkg("QFN-32", 5.0, 5.0, new double[0][]);
        pkg("FIDUCIAL-1MM", 1.0, 1.0, new double[][] { { 0, 0, 1.0, 1.0, 100 } });

        part("R0603-10K", "10k\u03a9 \u00b11%", "R0603", 0.45);
        part("R0603-1K", "1k\u03a9 \u00b11%", "R0603", 0.45);
        part("R0402-4K7", "4.7k\u03a9 \u00b11%", "R0402", 0.35);
        part("C0402-100N", "100nF 16V X7R", "C0402", 0.5);
        part("C0603-1U", "1\u00b5F 16V", "C0603", 0.8);
        part("C0805-10U", "10\u00b5F 10V", "C0805", 1.25);
        part("LED-0805-RED", "\u7ea2\u8272 LED", "LED-0805", 0.8);
        part("SOT-23-BSS138", "N \u6c9f\u9053 MOSFET", "SOT-23", 1.1);
        part("SOIC-8-LM358", "\u53cc\u8fd0\u653e", "SOIC-8", 1.75);
        part("QFN-32-STM32G0", "MCU", "QFN-32", 0.9);
        part("FIDUCIAL-1MM", "\u57fa\u51c6\u70b9", "FIDUCIAL-1MM", 0.0);
    }

    /** Two pads of the given size, centred at plus and minus the given distance on X. */
    private static double[][] twoPads(double x, double width, double height) {
        return new double[][] { { -x, 0, width, height, 0 }, { x, 0, width, height, 0 } };
    }

    private static double[][] sot23() {
        return new double[][] { { -0.95, -1.1, 0.6, 0.7, 0 }, { 0.95, -1.1, 0.6, 0.7, 0 },
                { 0, 1.1, 0.6, 0.7, 0 } };
    }

    private static double[][] soic8() {
        double[][] pads = new double[8][];
        for (int i = 0; i < 4; i++) {
            double x = -1.905 + i * 1.27;
            pads[i] = new double[] { x, -2.7, 0.6, 1.5, 0 };
            pads[7 - i] = new double[] { x, 2.7, 0.6, 1.5, 0 };
        }
        return pads;
    }

    private void pkg(String id, double bodyWidth, double bodyHeight, double[][] pads) {
        Package pkg = new Package(id);
        Footprint footprint = pkg.getFootprint();
        footprint.setUnits(MM);
        footprint.setBodyWidth(bodyWidth);
        footprint.setBodyHeight(bodyHeight);
        for (int i = 0; i < pads.length; i++) {
            Footprint.Pad pad = new Footprint.Pad();
            pad.setName(String.valueOf(i + 1));
            pad.setX(pads[i][0]);
            pad.setY(pads[i][1]);
            pad.setWidth(pads[i][2]);
            pad.setHeight(pads[i][3]);
            pad.setRoundness(pads[i][4]);
            footprint.addPad(pad);
        }
        configuration.addPackage(pkg);
    }

    private void part(String id, String name, String packageId, double height) {
        Part part = new Part(id);
        part.setName(name);
        part.setPackage(configuration.getPackage(packageId));
        part.setHeight(new Length(height, MM));
        configuration.addPart(part);
        parts.put(id, part);
    }

    private void feeders(Machine machine) throws Exception {
        // The mockup's stock and slots: F-01 well stocked, F-03 low, F-12 out, F-08 not counted,
        // the tray last picked from yesterday. The times are from when the fixture is written,
        // which the ruler does just before it photographs.
        long now = System.currentTimeMillis();
        stock(strip(machine, "F-01", "R0603-10K", 8, 4, 0), "A1", 1250, 10, 50, now - 3 * 60_000);
        stock(strip(machine, "F-02", "R0603-1K", 8, 4, 1), "A2", 1000, 180, 50, now - 5 * 60_000);
        stock(strip(machine, "F-03", "C0402-100N", 8, 2, 2), "A3", 212, 200, 50, now - 10_000);
        stock(strip(machine, "F-04", "R0402-4K7", 8, 2, 3), "A4", 2000, 400, 50, now - 8 * 60_000);
        stock(strip(machine, "F-05", "C0805-10U", 8, 4, 4), "A5", 500, 60, 50, now - 26 * 60_000);
        stock(strip(machine, "F-06", "SOT-23-BSS138", 8, 4, 5), "A6", 300, 12, 20, now - 40 * 60_000);
        stock(strip(machine, "F-07", "C0603-1U", 8, 4, 6), "B1", 1000, 900, 50, now - 2 * 3_600_000);
        strip(machine, "F-08", "C0603-1U", 8, 4, 7).setSlotName("B2");

        // A feeder cannot be saved without a part, so the mockup's unassigned F-09 gets one.
        ReferenceAutoFeeder auto = new ReferenceAutoFeeder();
        auto.setName("F-09");
        auto.setPart(parts.get("R0402-4K7"));
        auto.setEnabled(false);
        auto.setSlotName("B3");
        machine.addFeeder(auto);

        stock(strip(machine, "F-10", "R0603-10K", 8, 4, 9), "B4", 1000, 20, 50, now - 90 * 60_000);
        stock(strip(machine, "F-11", "QFN-32-STM32G0", 12, 8, 10), "B5", 50, 8, 5, 0);
        stock(strip(machine, "F-12", "LED-0805-RED", 8, 4, 11), "A7", 400, 400, 20, now - 12 * 60_000);
        String[] spares = { "R0603-1K", "C0402-100N", "C0603-1U", "C0805-10U", "R0402-4K7" };
        for (int i = 13; i <= 23; i++) {
            ReferenceStripFeeder spare = strip(machine, String.format("F-%02d", i),
                    spares[i % spares.length], 8, 4, i - 1);
            spare.setEnabled(i % 3 != 0);
            spare.setSlotName("B" + (i - 7));
        }

        ReferenceTrayFeeder tray = new ReferenceTrayFeeder();
        tray.setName("TRAY-1");
        tray.setPart(parts.get("SOIC-8-LM358"));
        tray.setTrayCountX(5);
        tray.setTrayCountY(8);
        tray.setFeedCount(3);
        tray.setLocation(new Location(MM, 40, 250, -30, 0));
        tray.setOffsets(new Location(MM, 12, 12, 0, 0));
        tray.setSlotName("C1");
        tray.setLastPickMillis(now - 26 * 3_600_000L);
        machine.addFeeder(tray);
    }

    private static void stock(ReferenceStripFeeder feeder, String slot, int parts, int fed, int low,
            long lastPick) {
        feeder.setSlotName(slot);
        feeder.setMaxFeedCount(parts);
        feeder.setFeedCount(fed);
        feeder.setLowCount(low);
        feeder.setLastPickMillis(lastPick);
    }

    /**
     * The top camera looks at the mockups' board, its centre where the camera is parked. The frame
     * is the picture's own size, so that one image pixel is one camera pixel.
     */
    private void camera(Machine machine) throws Exception {
        Camera camera = machine.getDefaultHead().getDefaultCamera();
        if (!(camera instanceof ImageCamera) || !CAMERA_PICTURE.exists()) {
            System.out.println("No camera picture at " + CAMERA_PICTURE
                    + ": the top camera keeps its sample image.");
            return;
        }
        File picture = new File(jobs.getParentFile(), CAMERA_PICTURE.getName());
        Files.copy(CAMERA_PICTURE.toPath(), picture.toPath(), StandardCopyOption.REPLACE_EXISTING);
        BufferedImage image = ImageIO.read(picture);
        ImageCamera top = (ImageCamera) camera;
        top.setSourceUri(picture.toURI().toString());
        top.setViewWidth(image.getWidth());
        top.setViewHeight(image.getHeight());
        Location upp = new Location(MM, CAMERA_UPP, CAMERA_UPP, 0, 0);
        top.setUnitsPerPixel(upp);
        top.setImageUnitsPerPixel(upp);
        top.setImageOffset(new Location(MM, image.getWidth() / 2.0 * CAMERA_UPP - PARKED.getX(),
                image.getHeight() / 2.0 * CAMERA_UPP - PARKED.getY(), 0, 0));
    }

    private ReferenceStripFeeder strip(Machine machine, String name, String partId, int tapeWidth,
            int pitch, int lane) throws Exception {
        ReferenceStripFeeder feeder = new ReferenceStripFeeder();
        feeder.setName(name);
        feeder.setEnabled(true);
        feeder.setPart(parts.get(partId));
        feeder.setTapeWidth(new Length(tapeWidth, MM));
        feeder.setPartPitch(new Length(pitch, MM));
        double y = 44.125 + lane * 12;
        feeder.setReferenceHoleLocation(new Location(MM, 312.88, y, -32.4, 0));
        feeder.setLastHoleLocation(new Location(MM, 412.88, y, -32.4, 0));
        machine.addFeeder(feeder);
        return feeder;
    }

    /**
     * The job mockup's board, in its coordinates: the six rows it shows first, in its order, then
     * the rest of the 48.
     */
    private Board demoBoard() throws Exception {
        Board board = configuration.getBoard(new File(jobs, "demo-board.board.xml"));
        board.setName("demo-board");
        board.setDimensions(new Location(MM, 160, 120, 0, 0));

        place(board, "R11", "R0603-10K", 118.2, 83.0, 0, Side.Top);
        place(board, "R12", "R0603-10K", 120.45, 85.21, 90, Side.Top)
                .setComments("\u9760\u8fd1 U3\uff0c\u6ce8\u610f\u65b9\u5411");
        place(board, "C14", "C0402-100N", 126.9, 92.1, 90, Side.Top);
        place(board, "U3", "SOIC-8-LM358", 112.0, 96.5, 270, Side.Top);
        Placement fid1 = place(board, "FID1", "FIDUCIAL-1MM", 140.0, 70.0, 0, Side.Top);
        fid1.setType(Placement.Type.Fiducial);
        fid1.setEnabled(false);
        place(board, "D2", "LED-0805-RED", 98.3, 64.2, 180, Side.Bottom);

        for (int i = 1; i <= 10; i++) {
            place(board, "R" + i, i % 3 == 0 ? "R0402-4K7" : "R0603-1K", 20 + i * 8.5, 18.0,
                    i % 2 == 0 ? 90 : 0, Side.Top);
        }
        for (int i = 13; i <= 20; i++) {
            place(board, "R" + i, "R0603-10K", 60 + (i - 13) * 7.5, 104.0, 0, Side.Top);
        }
        for (int i = 1; i <= 13; i++) {
            place(board, "C" + i, i % 4 == 0 ? "C0805-10U" : i % 2 == 0 ? "C0603-1U" : "C0402-100N",
                    16 + i * 9.0, 32.0 + (i % 3) * 6, i % 2 == 0 ? 0 : 90, Side.Top);
        }
        for (int i = 15; i <= 17; i++) {
            place(board, "C" + i, "C0402-100N", 130 + (i - 15) * 6.0, 96.0, 0, Side.Top);
        }
        place(board, "U1", "QFN-32-STM32G0", 70.0, 60.0, 0, Side.Top).setErrorHandling(
                Placement.ErrorHandling.Defer);
        place(board, "U2", "SOIC-8-LM358", 40.0, 70.0, 90, Side.Top);
        place(board, "D1", "LED-0805-RED", 12.0, 110.0, 0, Side.Top);
        place(board, "D3", "LED-0805-RED", 24.0, 110.0, 0, Side.Top);
        place(board, "Q1", "SOT-23-BSS138", 88.0, 46.0, 180, Side.Top);
        place(board, "Q2", "SOT-23-BSS138", 96.0, 46.0, 180, Side.Top);
        for (String id : new String[] { "FID2", "FID3" }) {
            Placement fid = place(board, id, "FIDUCIAL-1MM", id.equals("FID2") ? 5.0 : 155.0,
                    id.equals("FID2") ? 5.0 : 115.0, 0, Side.Top);
            fid.setType(Placement.Type.Fiducial);
        }
        if (board.getPlacements().size() != 48) {
            throw new IllegalStateException(board.getPlacements().size() + " placements, not 48");
        }
        configuration.saveBoard(board);
        return board;
    }

    /** The small board the 16-up panel is made of: 16 of them make the job's 48 placements. */
    private Board demoCell() throws Exception {
        Board cell = configuration.getBoard(new File(jobs, "demo-cell.board.xml"));
        cell.setName("demo-cell");
        cell.setDimensions(new Location(MM, 40, 30, 0, 0));
        place(cell, "R1", "R0603-10K", 12.0, 15.0, 0, Side.Top);
        place(cell, "C1", "C0402-100N", 20.0, 15.0, 90, Side.Top);
        place(cell, "D1", "LED-0805-RED", 28.0, 15.0, 0, Side.Top);
        configuration.saveBoard(cell);
        return cell;
    }

    private Panel demoPanel(Board cell) throws Exception {
        Panel panel = configuration.getPanel(new File(jobs, "demo-panel.panel.xml"));
        panel.setName("demo-panel");
        panel.setDimensions(new Location(MM, 4 * 42 + 6, 4 * 32 + 6, 0, 0));
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                BoardLocation location = new BoardLocation(new Board(cell));
                location.setLocation(new Location(MM, 3 + column * 42, 3 + row * 32, 0, 0));
                location.setSide(Side.Top);
                panel.addChild(location);
            }
        }
        configuration.savePanel(panel);
        return panel;
    }

    /** Two boards, the first with 31 of its 48 placements placed: the job mockup's 31 / 48. */
    private void demoBoardJob(Board board) throws Exception {
        Job job = new Job();
        File file = new File(jobs, "demo-board.job.xml");
        job.setFile(file);
        BoardLocation first = boardLocation(board, 60, 40);
        BoardLocation second = boardLocation(board, 60, 180);
        job.addBoardOrPanelLocation(first);
        job.addBoardOrPanelLocation(second);
        // R11 is placed, R12 is the one being placed, C14 is next; 30 of the rest are done.
        List<Placement> placements = board.getPlacements();
        job.storePlacedStatus(first, "R11", true);
        for (int i = 6; i < 36; i++) {
            job.storePlacedStatus(first, placements.get(i).getId(), true);
        }
        configuration.saveJob(job, file);
    }

    private BoardLocation boardLocation(Board board, double x, double y) {
        BoardLocation location = new BoardLocation(new Board(board));
        location.setLocation(new Location(MM, x, y, -10, 0));
        location.setSide(Side.Top);
        return location;
    }

    private void demoPanelJob(Panel panel) throws Exception {
        Job job = new Job();
        File file = new File(jobs, "demo-panel.job.xml");
        job.setFile(file);
        PanelLocation location = new PanelLocation(new Panel(panel));
        location.setLocation(new Location(MM, 60, 40, -10, 0));
        location.setSide(Side.Top);
        job.addBoardOrPanelLocation(location);
        configuration.resolvePanel(job, location);
        configuration.saveJob(job, file);
    }

    private Placement place(Board board, String id, String partId, double x, double y,
            double rotation, Side side) {
        Placement placement = new Placement(id);
        placement.setPart(parts.get(partId));
        placement.setLocation(new Location(MM, x, y, 0, rotation));
        placement.setSide(side);
        board.addPlacement(placement);
        return placement;
    }

    /**
     * NT1 with what mockup 23 shows switched on - runout calibration, the colour-keyed
     * background, vacuum checks - so that its sheets show the fields that depend on them.
     */
    private static void nozzleTip(Machine machine) {
        for (org.openpnp.spi.NozzleTip tip : machine.getNozzleTips()) {
            if (tip instanceof org.openpnp.machine.reference.ReferenceNozzleTip && "NT1".equals(tip.getName())) {
                org.openpnp.machine.reference.ReferenceNozzleTip nt = (org.openpnp.machine.reference.ReferenceNozzleTip) tip;
                nt.getCalibration().setEnabled(true);
                nt.getCalibration().setBackgroundCalibrationMethod(
                        org.openpnp.machine.reference.ReferenceNozzleTipCalibration.BackgroundCalibrationMethod.BrightnessAndKeyColor);
                nt.setMethodPartOn(org.openpnp.machine.reference.ReferenceNozzleTip.VacuumMeasurementMethod.Absolute);
                nt.setMethodPartOff(org.openpnp.machine.reference.ReferenceNozzleTip.VacuumMeasurementMethod.Difference);
            }
        }
    }

    private static void empty(Path directory) throws Exception {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).filter(p -> !p.equals(directory))
                    .forEach(p -> p.toFile().delete());
        }
    }
}
