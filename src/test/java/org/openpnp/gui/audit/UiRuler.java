package org.openpnp.gui.audit;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import org.openpnp.Main;
import org.openpnp.gui.MainFrame;
import org.openpnp.gui.components.ThemeDialog;
import org.openpnp.gui.components.ThemeInfo;
import org.openpnp.gui.components.ThemeSettingsPanel;
import org.openpnp.gui.shell.CameraStage;
import org.openpnp.gui.shell.DockPanel;
import org.openpnp.gui.shell.DroPanel;
import org.openpnp.gui.shell.JogCard;
import org.openpnp.gui.shell.NavigationRail;
import org.openpnp.gui.shell.StatusBarPanel;
import org.openpnp.gui.shell.TopBarPanel;
import org.openpnp.gui.theme.PonoThemes;
import org.openpnp.model.Board;
import org.openpnp.model.Configuration;
import org.openpnp.model.Job;
import org.openpnp.model.Panel;
import org.openpnp.model.Part;
import org.openpnp.spi.Actuator;
import org.openpnp.spi.Axis;
import org.openpnp.spi.Camera;
import org.openpnp.spi.Driver;
import org.openpnp.spi.Feeder;
import org.openpnp.spi.Head;
import org.openpnp.spi.Machine;
import org.openpnp.spi.Nozzle;
import org.openpnp.spi.NozzleTip;
import org.openpnp.spi.Signaler;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;

/**
 * The UI ruler. Starts Pono on a copy of the UI fixture, photographs every page in the scene of
 * the mockups, puts each photograph beside its mockup at the same pixel size, audits what is on
 * screen, and writes the lot into one report.
 *
 * <pre>
 * java -cp target/openpnp-gui-0.0.1-alpha-SNAPSHOT.jar;target/test-classes \
 *     org.openpnp.gui.audit.UiRuler [--rounds base,small,hidpi] [--scenes job,feeders] \
 *     [--fixture E:/pono-env/ui-fixture] [--mockups E:/pono-env/ui-mockups/rendered] \
 *     [--design design/mockups] [--out E:/pono-env/ui-ruler/&lt;time&gt;]
 * </pre>
 *
 * A round is a window size: 1600 by 1000, the mockups' own; 1366 by 768, the small laptop; 1536 by
 * 864, a 1080p laptop at 125 %. Each is painted at the scale of the screen the window is on. Swing
 * measures text for the layout at that scale, and a glyph rounds to a different width at another
 * one: painted at 150 % what was laid out at 125 %, Chinese text comes out about 4 % wider than
 * its label and is cut off, which no one at either setting ever sees.
 *
 * tools/ui-ruler/ruler.ps1 builds and runs it.
 */
public class UiRuler {
    /** One window size, photographed in each of its themes. */
    static final class Round {
        final String id;
        final int width;
        final int height;
        final List<String> themes;

        Round(String id, int width, int height, String... themes) {
            this.id = id;
            this.width = width;
            this.height = height;
            this.themes = List.of(themes);
        }

        String label(String theme) {
            return width + "\u00d7" + height + " \u00b7 " + Math.round(scale * 100) + "% \u00b7 "
                    + themeName(theme);
        }
    }

    static final List<Round> ROUNDS = List.of(
            new Round("base", 1600, 1000, "dark", "light"),
            new Round("small", 1366, 768, "dark"),
            new Round("hidpi", 1536, 864, "light"));

    /** The scale of the screen the window is on, which the layout measured its text at. */
    static double scale = 1.0;

    /** A page of the rail, known by the class of the panel it shows. */
    static final class Scene {
        final String id;
        final String label;
        final Component page;

        Scene(String id, String label, Component page) {
            this.id = id;
            this.label = label;
            this.page = page;
        }
    }

    private static final Map<String, String> PAGE_IDS = new LinkedHashMap<>();

    static {
        String[][] ids = { { "JobPanel", "job" }, { "FeedersPanel", "feeders" },
                { "PartsPanel", "parts" }, { "PackagesPanel", "packages" },
                { "BoardsPanel", "boards" }, { "PanelsPanel", "panels" },
                { "VisionSettingsPanel", "vision" }, { "MachineSetupPanel", "machine" },
                { "IssuesAndSolutionsPanel", "issues" }, { "CalibrationPanel", "calibration" },
                { "LogPanel", "log" },
                { "SettingsPanel", "settings" } };
        for (String[] id : ids) {
            PAGE_IDS.put(id[0], id[1]);
        }
    }

    private final File fixture;
    private final File mockups;
    private final File design;
    private final File out;
    private final File config;
    private final Set<String> roundFilter;
    private final Set<String> sceneFilter;
    private final PrintWriter log;
    private final List<UiAudit.Finding> findings = new ArrayList<>();
    /** Round id and theme to scene id to the photograph and its pair, for the report. */
    private final Map<String, Map<String, String[]>> photographs = new LinkedHashMap<>();
    private final Map<String, String> sceneLabels = new LinkedHashMap<>();
    private final List<String> wizardClasses = new ArrayList<>();
    private final Set<String> classNames = new TreeSet<>();
    private MainFrame frame;
    private Configuration configuration;
    private UiAudit.Rules rules;
    /**
     * Whether the window is put on the screen. Off it, the ruler runs behind whatever the user is
     * doing: every photograph is painted from the components, not read off the screen, and the
     * dialogs are laid out and painted without being shown at all.
     */
    private final boolean onscreen;
    /** Left of every screen a desk is likely to have, and within what Windows keeps coordinates in. */
    private static final int OFFSCREEN_X = -20000;
    private Map<Component, String> landmarks;

    private UiRuler(Map<String, String> options) throws IOException {
        fixture = new File(options.getOrDefault("fixture", "E:/pono-env/ui-fixture")).getCanonicalFile();
        mockups = new File(options.getOrDefault("mockups", "E:/pono-env/ui-mockups/rendered"));
        design = new File(options.getOrDefault("design", "design/mockups"));
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date());
        out = new File(options.getOrDefault("out", "E:/pono-env/ui-ruler/" + stamp));
        roundFilter = split(options.get("rounds"));
        sceneFilter = split(options.get("scenes"));
        onscreen = Boolean.parseBoolean(options.getOrDefault("onscreen", "false"));
        new File(out, "shots").mkdirs();
        new File(out, "pairs").mkdirs();
        log = new PrintWriter(Files.newBufferedWriter(new File(out, "ruler.log").toPath(),
                StandardCharsets.UTF_8), true);
        config = new File(System.getProperty("java.io.tmpdir"), "pono-ui-ruler").getCanonicalFile();
        copyFixture();
    }

    public static void main(String[] args) throws Exception {
        // Before anything asks for a preference: see MemoryPreferencesFactory.
        System.setProperty("java.util.prefs.PreferencesFactory",
                MemoryPreferencesFactory.class.getName());
        // A window size on record, so that this is not a first start, which fills the screen.
        java.util.prefs.Preferences windowPrefs = java.util.prefs.Preferences.userNodeForPackage(MainFrame.class);
        windowPrefs.putInt("MainFrame.windowWidth", 1600); //$NON-NLS-1$
        windowPrefs.putInt("MainFrame.windowHeight", 1000); //$NON-NLS-1$
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            options.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        }
        UiRuler ruler = new UiRuler(options);
        System.setProperty("configDir", ruler.config.getPath());
        Thread thread = new Thread(() -> {
            int status = 0;
            try {
                ruler.run();
            }
            catch (Throwable t) {
                t.printStackTrace(ruler.log);
                status = 2;
            }
            ruler.log.println(status == 0 ? "RULER DONE" : "RULER FAILED");
            ruler.log.close();
            System.exit(status);
        }, "ui-ruler");
        thread.start();
        Main.main(new String[0]);
    }

    private void run() throws Exception {
        frame = waitForFrame();
        configuration = Configuration.get();
        scale = edtGet(() -> frame.getGraphicsConfiguration().getDefaultTransform().getScaleX());
        say("Pono is up, painting at " + Math.round(scale * 100) + "%");
        Thread.sleep(4000);
        closeStrayDialogs("\u542f\u52a8");
        inventory();
        rules = rules();
        prepareMachine();
        openJob();
        landmarks = edtGet(this::landmarks);

        for (Round round : ROUNDS) {
            if (!roundFilter.isEmpty() && !roundFilter.contains(round.id)) {
                continue;
            }
            sizeWindow(round);
            Map<String, BufferedImage> gallery = new LinkedHashMap<>();
            for (String theme : round.themes) {
                applyTheme(theme);
                for (Scene scene : edtGet(this::scenes)) {
                    if (!sceneFilter.isEmpty() && !sceneFilter.contains(scene.id)) {
                        continue;
                    }
                    photograph(round, theme, scene);
                }
                if (round.id.equals("base") && (sceneFilter.isEmpty() || sceneFilter.contains("gallery"))) {
                    gallery.put(theme, photographGallery(round, theme));
                }
                if (round.id.equals("base") && (sceneFilter.isEmpty() || sceneFilter.contains("dialogs"))) {
                    photographDialogs(round, theme);
                }
                if (round.id.equals("base") && (sceneFilter.isEmpty() || sceneFilter.contains("welcome"))) {
                    photographWelcome(round, theme);
                }
            }
            if (gallery.size() == 2) {
                pairGallery(round, gallery.get("dark"), gallery.get("light"));
            }
        }
        writeFindings();
        writeReport();
        say("report written to " + new File(out, "report.md"));
    }

    // ----- setting the scene -------------------------------------------------------------------

    /** The fixture, copied so that nothing the run saves lands in it, with its paths moved along. */
    private void copyFixture() throws IOException {
        if (!new File(fixture, "machine.xml").exists()) {
            throw new IOException("no UI fixture at " + fixture + ": run UiFixture first");
        }
        if (config.exists()) {
            try (Stream<Path> paths = Files.walk(config.toPath())) {
                paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                        .forEach(p -> p.toFile().delete());
            }
        }
        String from = fixture.getPath();
        String to = config.getPath();
        try (Stream<Path> paths = Files.walk(fixture.toPath())) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path target = config.toPath().resolve(fixture.toPath().relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                }
                else if (path.toString().endsWith(".xml")) {
                    String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                    Files.write(target, text.replace(from, to).getBytes(StandardCharsets.UTF_8));
                }
                else {
                    Files.copy(path, target);
                }
            }
        }
    }

    private MainFrame waitForFrame() throws Exception {
        boolean placed = onscreen;
        for (int i = 0; i < 9000; i++) {
            MainFrame frame = MainFrame.get();
            // The frame is known from the first line of its constructor, seconds before it is shown.
            if (frame != null && !placed) {
                placeOffScreen(frame);
                placed = true;
            }
            if (frame != null && frame.isShowing()) {
                return frame;
            }
            Thread.sleep(20);
        }
        throw new IllegalStateException("the main window did not come up");
    }

    /**
     * Off every screen before it is first shown: the native window is made, hidden, just before
     * it is shown, and moved as it is made, so that it never appears in front of the user's work.
     */
    private static void placeOffScreen(MainFrame frame) {
        Runnable away = () -> {
            if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) {
                frame.setExtendedState(Frame.NORMAL);
            }
            frame.setLocation(OFFSCREEN_X, 0);
        };
        frame.addHierarchyListener(new java.awt.event.HierarchyListener() {
            @Override
            public void hierarchyChanged(java.awt.event.HierarchyEvent e) {
                if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.DISPLAYABILITY_CHANGED) != 0
                        && frame.isDisplayable()) {
                    frame.removeHierarchyListener(this);
                    away.run();
                }
            }
        });
        if (frame.isDisplayable()) {
            // Made already: moved as soon as the event thread gets to it.
            SwingUtilities.invokeLater(away);
        }
    }

    private void prepareMachine() throws Exception {
        Machine machine = configuration.getMachine();
        machine.submit(() -> {
            machine.setEnabled(true);
            return null;
        }, null, true).get(60, TimeUnit.SECONDS);
        machine.submit(() -> {
            machine.home();
            Nozzle nozzle = machine.getDefaultHead().getDefaultNozzle();
            nozzle.moveTo(UiFixture.PARKED);
            return null;
        }).get(60, TimeUnit.SECONDS);
        say("machine enabled and homed");
    }

    private void openJob() throws Exception {
        Job job = configuration.loadJob(new File(config, "jobs/demo-board.job.xml"));
        edt(() -> frame.getJobTab().setJob(job));
        settle(1500);
        say("job opened");
    }

    private void sizeWindow(Round round) throws Exception {
        edt(() -> {
            frame.setExtendedState(Frame.NORMAL);
            Insets insets = frame.getInsets();
            frame.setBounds(onscreen ? 0 : OFFSCREEN_X, 0, round.width + insets.left + insets.right,
                    round.height + insets.top + insets.bottom);
            frame.validate();
        });
        settle(2000);
        JRootPane pane = frame.getRootPane();
        if (pane.getWidth() != round.width || pane.getHeight() != round.height) {
            findings.add(new UiAudit.Finding(UiAudit.Check.WindowSize, "\u7a97\u53e3", round.label("-"),
                    "\u7a97\u53e3", "\u8981 " + round.width + "\u00d7" + round.height + "\uff0c\u5b9e\u9645 "
                            + pane.getWidth() + "\u00d7" + pane.getHeight()));
        }
        say("window " + pane.getWidth() + "x" + pane.getHeight() + " at " + frame.getX() + "," + frame.getY()
                + " for " + round.id);
    }

    private void applyTheme(String theme) throws Exception {
        ThemeInfo info = "dark".equals(theme) ? PonoThemes.dark() : PonoThemes.light();
        edt(() -> {
            new ThemeSettingsPanel().setTheme(info, configuration.getFontSize(),
                    configuration.isAlternateRows());
            configuration.setThemeInfo(info);
            ThemeDialog.getInstance().setOldTheme(info);
        });
        // The switch fades from a snapshot of the old look; it must be gone before a photograph.
        settle(2500);
        say("theme " + theme);
    }

    private List<Scene> scenes() {
        NavigationRail rail = frame.getNavigation();
        List<Scene> scenes = new ArrayList<>();
        for (Component page : rail.getPageComponents()) {
            String type = page.getClass().getSimpleName();
            String id = PAGE_IDS.getOrDefault(type,
                    type.replaceFirst("Panel$", "").toLowerCase());
            String label = rail.getLabel(page);
            scenes.add(new Scene(id, label, page));
            sceneLabels.put(id, label);
        }
        return scenes;
    }

    /** What each page shows in the mockups: the row they have selected. */
    private List<String> setUp(Scene scene) {
        List<String> missed = new ArrayList<>();
        Component page = scene.page;
        switch (scene.id) {
            case "job":
                expect(missed, click(page, "\u5355\u677f"), "\u300c\u5355\u677f\u300d\u6807\u7b7e");
                expect(missed, selectFirstRow(page), "\u7b2c\u4e00\u5757\u5355\u677f");
                expect(missed, click(page, "\u8d34\u7247\u4f4d"), "\u300c\u8d34\u7247\u4f4d\u300d\u6807\u7b7e");
                expect(missed, selectRow(page, "R12"::equals), "\u8d34\u7247\u4f4d R12");
                break;
            case "feeders":
                expect(missed, selectRow(page, "F-08"::equals), "\u98de\u8fbe F-08");
                break;
            case "parts":
                expect(missed, selectRow(page, "R0603-10K"::equals), "\u5143\u4ef6 R0603-10K");
                break;
            case "packages":
                expect(missed, selectRow(page, "R0603"::equals), "\u5c01\u88c5 R0603");
                break;
            case "boards":
                expect(missed, selectRow(page, s -> s.contains("demo-board")), "\u5355\u677f demo-board");
                break;
            case "panels":
                expect(missed, selectRow(page, s -> s.contains("demo-panel")), "\u62fc\u677f demo-panel");
                break;
            case "vision":
                expect(missed, selectFirstRow(page), "\u7b2c\u4e00\u4e2a\u89c6\u89c9\u914d\u7f6e");
                break;
            case "machine":
                expect(missed, selectTreeNode(page, "Top"), "\u76f8\u673a Top");
                break;
            default:
                break;
        }
        return missed;
    }

    private static void expect(List<String> missed, boolean done, String what) {
        if (!done) {
            missed.add(what);
        }
    }

    // ----- photographing -----------------------------------------------------------------------

    private void photograph(Round round, String theme, Scene scene) throws Exception {
        String label = round.label(theme);
        edt(() -> frame.showTab(scene.page));
        settle(800);
        List<String> missed = edtGet(() -> setUp(scene));
        for (String what : missed) {
            findings.add(new UiAudit.Finding(UiAudit.Check.SceneSetup, scene.label, label,
                    scene.label + "\u9875", "\u6ca1\u627e\u5230" + what));
        }
        settle(1800);
        closeStrayDialogs(scene.label);

        BufferedImage shot = edtGet(() -> paint(frame.getRootPane(), scale));
        String name = round.id + "-" + theme + "-" + scene.id;
        ImageIO.write(shot, "png", new File(out, "shots/" + name + ".png"));
        String pair = null;
        File mockup = mockup(scene.id, theme);
        if (mockup != null) {
            BufferedImage mock = ImageIO.read(mockup);
            ImageIO.write(pair(mock, shot, "\u6548\u679c\u56fe " + mockup.getName(),
                    "\u5f53\u524d \u00b7 " + label, scale), "png",
                    new File(out, "pairs/" + name + ".png"));
            pair = "pairs/" + name + ".png";
        }
        photographs.computeIfAbsent(round.id + "-" + theme, k -> new LinkedHashMap<>())
                .put(scene.id, new String[] { "shots/" + name + ".png", pair, label });

        List<UiAudit.Finding> found = edtGet(() -> new UiAudit(rules, landmarks,
                frame.getRootPane(), shot, scale, scene.label, label).run());
        findings.addAll(found);
        say(name + ": " + found.size() + " findings" + (pair == null ? "" : ", paired"));
    }

    /** The control gallery in the current theme: one half of 06-design-system. */
    private BufferedImage photographGallery(Round round, String theme) throws Exception {
        String label = round.label(theme);
        // Shown, off the screen: the audit only looks at what is showing.
        org.openpnp.gui.shell.ControlGallery gallery = edtGet(() -> {
            org.openpnp.gui.shell.ControlGallery g = new org.openpnp.gui.shell.ControlGallery();
            g.setLocation(-4000, -4000);
            g.setVisible(true);
            return g;
        });
        settle(1200);
        BufferedImage shot = edtGet(() -> {
            JComponent content = (JComponent) gallery.getContentPane();
            layoutAll(content);
            BufferedImage image = paint(content, scale);
            List<UiAudit.Finding> found = new UiAudit(rules, new IdentityHashMap<>(),
                    content, image, scale, "\u63a7\u4ef6\u6837\u5f20", label).run();
            findings.addAll(found);
            gallery.dispose();
            return image;
        });
        String name = round.id + "-" + theme + "-gallery";
        ImageIO.write(shot, "png", new File(out, "shots/" + name + ".png"));
        photographs.computeIfAbsent(round.id + "-" + theme, k -> new LinkedHashMap<>())
                .put("gallery", new String[] { "shots/" + name + ".png", null, label });
        sceneLabels.put("gallery", "\u63a7\u4ef6\u6837\u5f20");
        say(name + " photographed");
        return shot;
    }

    private static void layoutAll(Component c) {
        if (c instanceof Container) {
            ((Container) c).doLayout();
            for (Component child : ((Container) c).getComponents()) {
                layoutAll(child);
            }
        }
    }

    /**
     * The three kinds of dialog as 19-dialogs has them, over the job page: an error with its
     * details open, a question whose button moves the machine, a delete. They are the dialogs the
     * program builds, handed over instead of shown, and audited like a page.
     */
    private void photographDialogs(Round round, String theme) throws Exception {
        String label = round.label(theme);
        showJobPage();
        List<javax.swing.JDialog> shown = new ArrayList<>();
        // Laid out, and never shown: a dialog shown anywhere is moved onto the screen, in front
        // of whatever the user is doing, by the fit every dialog gets when it opens.
        org.openpnp.gui.shell.Dialogs.setPresenter(dialog -> {
            shown.add(dialog);
            return -1;
        });
        List<BufferedImage> images = new ArrayList<>();
        try {
            // The same error within five seconds is counted rather than shown again, and the
            // other theme's has just been.
            Thread.sleep(5200);
            edt(() -> {
                org.openpnp.gui.shell.Dialogs.error(frame,
                        "\u6ca1\u80fd\u79fb\u52a8\u5230\u8d34\u88c5\u4f4d\u7f6e", //$NON-NLS-1$
                        new Exception("Can't move y to 350.120000mm, higher than soft limit 350.000000mm."), //$NON-NLS-1$
                        null, false);
                org.openpnp.util.UiUtils.confirmMoveToLocationAndAct(frame,
                        "\u5148\u628a\u76f8\u673a\u79fb\u5230 F-08 \u518d\u81ea\u52a8\u8bbe\u7f6e", true, //$NON-NLS-1$
                        () -> {
                        }, () -> {
                        });
                org.openpnp.gui.shell.Dialogs.confirmDelete(frame, "Dialogs.Kind.Feeders", //$NON-NLS-1$
                        Arrays.asList("F-13 \u00b7 C0402-100N", "F-14 \u00b7 C0603-1U", //$NON-NLS-1$ //$NON-NLS-2$
                                "F-15 \u00b7 C0805-10U")); //$NON-NLS-1$
            });
            settle(1200);
            // The mockup has the error's details open.
            edt(() -> {
                if (!shown.isEmpty()) {
                    openDetails(shown.get(0).getRootPane());
                }
            });
            settle(800);
            edt(() -> {
                for (javax.swing.JDialog dialog : shown) {
                    JRootPane pane = dialog.getRootPane();
                    layoutAll(pane);
                    BufferedImage image = paint(pane, scale);
                    images.add(image);
                    findings.addAll(new UiAudit(rules, new IdentityHashMap<>(), pane, image, scale,
                            "\u5bf9\u8bdd\u6846", label).run());
                }
            });
        }
        finally {
            org.openpnp.gui.shell.Dialogs.setPresenter(null);
            edt(() -> shown.forEach(Window::dispose));
        }
        if (images.size() != 3) {
            findings.add(new UiAudit.Finding(UiAudit.Check.SceneSetup, "\u5bf9\u8bdd\u6846", label,
                    "\u5bf9\u8bdd\u6846", "\u53ea\u62cd\u5230 " + images.size() + " \u4e2a\u5bf9\u8bdd\u6846"));
        }
        // Where 19-dialogs puts them: the error at the left, the question and the delete at the right.
        double[][] at = { { 0.075, 0.12 }, { 0.54, 0.15 }, { 0.54, 0.47 } };
        BufferedImage shot = overPage(images, at);
        writeComposed(round, theme, "dialogs", "\u5bf9\u8bdd\u6846", shot);
    }

    /** The welcome window, 20-welcome, over the job page. */
    private void photographWelcome(Round round, String theme) throws Exception {
        String label = round.label(theme);
        showJobPage();
        // Laid out and never shown, as the dialogs are.
        org.openpnp.gui.Welcome2_0Dialog welcome = edtGet(() -> {
            org.openpnp.gui.Welcome2_0Dialog d = new org.openpnp.gui.Welcome2_0Dialog(frame, configuration);
            d.pack();
            return d;
        });
        settle(1200);
        List<BufferedImage> images = new ArrayList<>();
        try {
            edt(() -> {
                JRootPane pane = welcome.getRootPane();
                layoutAll(pane);
                BufferedImage image = paint(pane, scale);
                images.add(image);
                findings.addAll(new UiAudit(rules, new IdentityHashMap<>(), pane, image, scale,
                        "\u6b22\u8fce\u7a97\u53e3", label).run());
            });
        }
        finally {
            edt(welcome::dispose);
        }
        BufferedImage shot = overPage(images, new double[][] { centred(images.get(0)) });
        writeComposed(round, theme, "welcome", "\u6b22\u8fce\u7a97\u53e3", shot);
    }

    private void showJobPage() throws Exception {
        for (Scene scene : edtGet(this::scenes)) {
            if (scene.id.equals("job")) {
                edt(() -> frame.showTab(scene.page));
                settle(800);
                return;
            }
        }
    }

    /** Clicks the "Details" heading of an error dialog open. */
    private static void openDetails(Component c) {
        if (c instanceof JLabel && org.openpnp.Translations.getString("Dialogs.Details") //$NON-NLS-1$
                .equals(((JLabel) c).getText())) {
            for (java.awt.event.MouseListener listener : c.getMouseListeners()) {
                listener.mouseClicked(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_CLICKED,
                        System.currentTimeMillis(), 0, 1, 1, 1, false));
            }
            return;
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                openDetails(child);
            }
        }
    }

    /** Where a window of the given photograph sits centred on the main window's, as fractions. */
    private double[] centred(BufferedImage window) throws Exception {
        JRootPane pane = frame.getRootPane();
        double width = pane.getWidth() * scale;
        double height = pane.getHeight() * scale;
        return new double[] { Math.max(0, (width - window.getWidth()) / 2 / width),
                Math.max(0, (height - window.getHeight()) / 2 / height) };
    }

    /** The windows over the main window, dimmed as a modal dialog dims it. */
    private BufferedImage overPage(List<BufferedImage> windows, double[][] at) throws Exception {
        BufferedImage page = edtGet(() -> paint(frame.getRootPane(), scale));
        Graphics2D g = page.createGraphics();
        g.setColor(new Color(0, 0, 0, 110));
        g.fillRect(0, 0, page.getWidth(), page.getHeight());
        for (int i = 0; i < windows.size() && i < at.length; i++) {
            int x = (int) Math.round(at[i][0] * page.getWidth());
            int y = (int) Math.round(at[i][1] * page.getHeight());
            BufferedImage window = windows.get(i);
            g.setColor(new Color(0, 0, 0, 90));
            g.fillRoundRect(x + 2, y + 6, window.getWidth(), window.getHeight(), 24, 24);
            g.drawImage(window, x, y, null);
        }
        g.dispose();
        return page;
    }

    private void writeComposed(Round round, String theme, String id, String sceneLabel, BufferedImage shot)
            throws IOException {
        String label = round.label(theme);
        String name = round.id + "-" + theme + "-" + id;
        ImageIO.write(shot, "png", new File(out, "shots/" + name + ".png"));
        String pair = null;
        File mockup = mockup(id, theme);
        if (mockup != null) {
            ImageIO.write(pair(ImageIO.read(mockup), shot, "\u6548\u679c\u56fe " + mockup.getName(),
                    "\u5f53\u524d \u00b7 " + label, scale), "png", new File(out, "pairs/" + name + ".png"));
            pair = "pairs/" + name + ".png";
        }
        photographs.computeIfAbsent(round.id + "-" + theme, k -> new LinkedHashMap<>())
                .put(id, new String[] { "shots/" + name + ".png", pair, label });
        sceneLabels.put(id, sceneLabel);
        say(name + " photographed" + (pair == null ? "" : ", paired"));
    }

    /** Both halves side by side, as the mockup draws them, beside the mockup. */
    private void pairGallery(Round round, BufferedImage dark, BufferedImage light) throws IOException {
        BufferedImage both = new BufferedImage(dark.getWidth() + light.getWidth(),
                Math.max(dark.getHeight(), light.getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = both.createGraphics();
        g.drawImage(dark, 0, 0, null);
        g.drawImage(light, dark.getWidth(), 0, null);
        g.dispose();
        File mockup = mockup("design-system", "both");
        String name = round.id + "-both-gallery";
        ImageIO.write(both, "png", new File(out, "shots/" + name + ".png"));
        if (mockup != null) {
            ImageIO.write(pair(ImageIO.read(mockup), both, "\u6548\u679c\u56fe " + mockup.getName(),
                    "\u5f53\u524d \u00b7 \u63a7\u4ef6\u6837\u5f20", scale), "png",
                    new File(out, "pairs/" + name + ".png"));
            photographs.get(round.id + "-dark").put("gallery",
                    new String[] { "shots/" + name + ".png", "pairs/" + name + ".png", round.label("dark") });
        }
    }

    static BufferedImage paint(JComponent pane, double scale) {
        BufferedImage image = new BufferedImage((int) Math.round(pane.getWidth() * scale),
                (int) Math.round(pane.getHeight() * scale), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.scale(scale, scale);
        pane.paint(g);
        g.dispose();
        return image;
    }

    /** The rendered mockup of the scene in the theme: a file named like 01-workbench-job.dark.png. */
    private File mockup(String scene, String theme) {
        File[] files = mockups.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            String name = file.getName();
            int dot = name.indexOf('.');
            if (dot > 0 && name.substring(0, dot).endsWith("-" + scene)
                    && name.substring(dot).equals("." + theme + ".png")) {
                return file;
            }
        }
        return null;
    }

    /** The mockup and the photograph side by side, the mockup scaled to the photograph's height. */
    static BufferedImage pair(BufferedImage mock, BufferedImage shot, String left, String right,
            double scale) {
        int height = shot.getHeight();
        int mockWidth = (int) Math.round(mock.getWidth() * (double) height / mock.getHeight());
        int bar = (int) Math.round(34 * scale);
        int gap = (int) Math.round(12 * scale);
        BufferedImage image = new BufferedImage(mockWidth + gap + shot.getWidth(), bar + height,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x202428));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.drawImage(mock, 0, bar, mockWidth, height, null);
        g.drawImage(shot, mockWidth + gap, bar, null);
        g.setColor(new Color(0xe6ebf2));
        g.setFont(new Font("Microsoft YaHei UI", Font.BOLD, (int) Math.round(15 * scale)));
        int baseline = (int) Math.round(23 * scale);
        g.drawString(left, (int) Math.round(10 * scale), baseline);
        g.drawString(right, mockWidth + gap + (int) Math.round(10 * scale), baseline);
        g.dispose();
        return image;
    }

    // ----- finding things on screen ------------------------------------------------------------

    private static <T extends Component> List<T> showing(Component root, Class<T> type) {
        List<T> found = new ArrayList<>();
        collect(root, type, found);
        return found;
    }

    private static <T extends Component> void collect(Component c, Class<T> type, List<T> found) {
        if (!c.isShowing()) {
            return;
        }
        if (type.isInstance(c)) {
            found.add(type.cast(c));
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                collect(child, type, found);
            }
        }
    }

    private static boolean selectRow(Component root, Predicate<String> match) {
        for (JTable table : showing(root, JTable.class)) {
            for (int row = 0; row < table.getRowCount(); row++) {
                for (int col = 0; col < table.getColumnCount(); col++) {
                    Object value = table.getValueAt(row, col);
                    if (value != null && match.test(value.toString())) {
                        select(table, row);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean selectFirstRow(Component root) {
        for (JTable table : showing(root, JTable.class)) {
            if (table.getRowCount() > 0) {
                select(table, 0);
                return true;
            }
        }
        return false;
    }

    private static void select(JTable table, int row) {
        table.getSelectionModel().setSelectionInterval(row, row);
        table.scrollRectToVisible(table.getCellRect(row, 0, true));
    }

    private static boolean click(Component root, String textStart) {
        for (AbstractButton button : showing(root, AbstractButton.class)) {
            String text = button.getText();
            if (text != null && text.replaceAll("<[^>]*>", "").trim().startsWith(textStart)) {
                button.doClick(0);
                return true;
            }
        }
        return false;
    }

    private static boolean selectTreeNode(Component root, String text) {
        for (JTree tree : showing(root, JTree.class)) {
            TreeModel model = tree.getModel();
            TreePath path = find(model, new TreePath(model.getRoot()), text);
            if (path != null) {
                tree.expandPath(path.getParentPath());
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
                return true;
            }
        }
        return false;
    }

    private static TreePath find(TreeModel model, TreePath path, String text) {
        Object node = path.getLastPathComponent();
        String name = String.valueOf(node);
        if (name.equals(text) || name.endsWith(" " + text)) {
            return path;
        }
        for (int i = 0; i < model.getChildCount(node); i++) {
            TreePath hit = find(model, path.pathByAddingChild(model.getChild(node, i)), text);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private Map<Component, String> landmarks() {
        Map<Component, String> marks = new IdentityHashMap<>();
        NavigationRail rail = frame.getNavigation();
        for (Component page : rail.getPageComponents()) {
            marks.put(page, rail.getLabel(page) + "\u9875");
        }
        marks.put(frame.getInspector(), "\u5c5e\u6027\u680f");
        marks.put(rail, "\u5bfc\u822a\u680f");
        mark(marks, TopBarPanel.class, "\u9876\u680f");
        mark(marks, StatusBarPanel.class, "\u72b6\u6001\u680f");
        mark(marks, CameraStage.class, "\u76f8\u673a");
        mark(marks, DockPanel.class, "\u6570\u636e\u533a");
        mark(marks, JogCard.class, "\u70b9\u52a8\u5361\u7247");
        mark(marks, DroPanel.class, "DRO");
        return marks;
    }

    private void mark(Map<Component, String> marks, Class<? extends Component> type, String label) {
        List<Component> found = new ArrayList<>();
        everything(frame.getRootPane(), type, found);
        for (Component c : found) {
            marks.putIfAbsent(c, label);
        }
    }

    private static void everything(Component c, Class<? extends Component> type, List<Component> found) {
        if (type.isInstance(c)) {
            found.add(c);
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                everything(child, type, found);
            }
        }
    }

    // ----- dialogs nobody asked for ------------------------------------------------------------

    private void closeStrayDialogs(String when) throws Exception {
        List<String> closed = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            try {
                for (Window window : Window.getWindows()) {
                    if (window.isShowing() && window instanceof Dialog) {
                        List<String> texts = new ArrayList<>();
                        for (JLabel label : showing(window, JLabel.class)) {
                            if (label.getText() != null && !label.getText().isBlank()) {
                                texts.add(UiAudit.shorten(label.getText().replaceAll("<[^>]*>", " "), 120));
                            }
                        }
                        closed.add("\u300c" + ((Dialog) window).getTitle() + "\u300d" + String.join(" / ", texts));
                        window.dispose();
                    }
                }
            }
            finally {
                done.countDown();
            }
        });
        done.await(20, TimeUnit.SECONDS);
        for (String dialog : closed) {
            findings.add(new UiAudit.Finding(UiAudit.Check.UnexpectedDialog, when, "-",
                    "\u5bf9\u8bdd\u6846", "\u5df2\u5173\u95ed\uff1a" + dialog));
            say("closed a dialog: " + dialog);
        }
    }

    // ----- rules -------------------------------------------------------------------------------

    /** Pono's wizards and class names, from the classes themselves rather than a list to keep. */
    private void inventory() {
        try (ScanResult scan = new ClassGraph().enableClassInfo().acceptPackages("org.openpnp")
                .scan()) {
            for (ClassInfo info : scan.getAllClasses()) {
                if (info.getClasspathElementURL().toString().contains("test-classes")) {
                    continue;
                }
                classNames.add(info.getSimpleName());
            }
            for (ClassInfo info : scan.getClassesImplementing("org.openpnp.gui.support.Wizard")) {
                // The declarative forms are what replaces the old wizards, not one of them.
                if (info.isAbstract() || info.isInterface() || info.isAnonymousInnerClass()
                        || info.getPackageName().equals("org.openpnp.gui.support")
                        || info.getPackageName().equals("org.openpnp.gui.form")
                        || info.getClasspathElementURL().toString().contains("test-classes")) {
                    continue;
                }
                wizardClasses.add(info.getName());
            }
        }
        wizardClasses.sort(null);
        say(wizardClasses.size() + " wizard classes, " + classNames.size() + " class names");
    }

    private UiAudit.Rules rules() throws IOException {
        Set<String> words = new TreeSet<>();
        List<String> phrases = new ArrayList<>();
        try (java.io.InputStream in = UiRuler.class.getResourceAsStream("latin-whitelist.txt")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.contains(" ")) {
                    phrases.add(line);
                }
                else {
                    words.add(line);
                }
            }
        }
        Set<String> data = new TreeSet<>();
        Machine machine = configuration.getMachine();
        for (Part part : configuration.getParts()) {
            data(data, part.getId());
            data(data, part.getName());
        }
        for (org.openpnp.model.Package pkg : configuration.getPackages()) {
            data(data, pkg.getId());
        }
        for (Feeder feeder : machine.getFeeders()) {
            data(data, feeder.getName());
        }
        for (Head head : machine.getHeads()) {
            data(data, head.getName());
            for (Nozzle nozzle : head.getNozzles()) {
                data(data, nozzle.getName());
            }
            for (Camera camera : head.getCameras()) {
                data(data, camera.getName());
            }
            for (Actuator actuator : head.getActuators()) {
                data(data, actuator.getName());
            }
        }
        for (Camera camera : machine.getCameras()) {
            data(data, camera.getName());
        }
        for (Actuator actuator : machine.getActuators()) {
            data(data, actuator.getName());
        }
        for (Driver driver : machine.getDrivers()) {
            data(data, driver.getName());
        }
        for (Axis axis : machine.getAxes()) {
            data(data, axis.getName());
        }
        for (Signaler signaler : machine.getSignalers()) {
            data(data, signaler.getName());
        }
        for (NozzleTip tip : machine.getNozzleTips()) {
            data(data, tip.getName());
        }
        for (Board board : configuration.getBoards()) {
            data(data, board.getName());
            data(data, board.getFile() == null ? null : board.getFile().getName());
        }
        for (Panel panel : configuration.getPanels()) {
            data(data, panel.getName());
            data(data, panel.getFile() == null ? null : panel.getFile().getName());
        }
        File[] jobs = new File(config, "jobs").listFiles();
        for (File job : jobs == null ? new File[0] : jobs) {
            data(data, job.getName());
        }
        return new UiAudit.Rules(words, phrases, data, typeScale(), classNames);
    }

    private static void data(Set<String> data, String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        data.add(name);
        Matcher m = Pattern.compile("[A-Za-z][A-Za-z0-9_.'\\-]*").matcher(name);
        while (m.find()) {
            data.add(m.group());
            data.add(m.group().replaceAll("[.'\\-]+$", ""));
        }
    }

    /** Every font size the stylesheet and the mockups use, in pixels. */
    private Set<Float> typeScale() throws IOException {
        Set<Float> sizes = new TreeSet<>();
        Pattern size = Pattern.compile("font-size:\\s*([0-9.]+)px");
        File[] files = design.listFiles((dir, name) -> name.endsWith(".css") || name.endsWith(".html"));
        for (File file : files == null ? new File[0] : files) {
            Matcher m = size.matcher(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            while (m.find()) {
                sizes.add(Float.parseFloat(m.group(1)));
            }
        }
        if (sizes.isEmpty()) {
            throw new IOException("no font sizes found in " + design.getAbsolutePath());
        }
        say("type scale " + sizes);
        return sizes;
    }

    // ----- the report --------------------------------------------------------------------------

    private void writeFindings() throws IOException {
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(new File(out, "findings.tsv").toPath(),
                StandardCharsets.UTF_8))) {
            w.println("check\tround\tscene\twhere\ttext");
            for (UiAudit.Finding f : findings) {
                w.println(f.check + "\t" + f.round + "\t" + f.scene + "\t" + f.where + "\t" + f.text);
            }
        }
    }

    private void writeReport() throws IOException {
        Map<UiAudit.Check, Map<String, List<UiAudit.Finding>>> grouped = new LinkedHashMap<>();
        for (UiAudit.Check check : UiAudit.Check.values()) {
            grouped.put(check, new LinkedHashMap<>());
        }
        for (UiAudit.Finding f : findings) {
            grouped.get(f.check).computeIfAbsent(f.key(), k -> new ArrayList<>()).add(f);
        }
        int unique = grouped.values().stream().mapToInt(Map::size).sum();

        StringBuilder r = new StringBuilder();
        r.append("# \u754c\u9762\u4f53\u68c0\u62a5\u544a\n\n");
        r.append("\u6784\u5efa `").append(Main.getVersion()).append("` \u00b7 ")
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()))
                .append(" \u00b7 \u6f14\u793a\u914d\u7f6e `").append(fixture).append("`\n\n");
        r.append("\u544a\u8b66 **").append(unique).append("** \u6761\uff08\u53bb\u91cd\u540e\uff1b\u51fa\u73b0 ")
                .append(findings.size()).append(" \u6b21\uff09\u3002\u65e7\u5411\u5bfc\u7c7b **")
                .append(wizardClasses.size()).append("** \u4e2a\uff0cP9 \u4e4b\u524d\u53ea\u8981\u6c42\u4e0d\u589e\u52a0\u3002\n\n");

        r.append("## \u5404\u9879\u68c0\u67e5\n\n| \u68c0\u67e5 | \u6761\u6570 | \u51fa\u73b0\u6b21\u6570 | \u6700\u591a\u7684\u9875\u9762 |\n|---|---:|---:|---|\n");
        for (Map.Entry<UiAudit.Check, Map<String, List<UiAudit.Finding>>> e : grouped.entrySet()) {
            int occurrences = e.getValue().values().stream().mapToInt(List::size).sum();
            Map<String, Integer> byScene = new TreeMap<>();
            e.getValue().values().forEach(list -> list.forEach(f -> byScene.merge(f.scene, 1, Integer::sum)));
            String top = byScene.entrySet().stream()
                    .sorted((a, b) -> b.getValue() - a.getValue()).limit(3)
                    .map(x -> x.getKey() + " " + x.getValue()).collect(Collectors.joining("\u3001"));
            r.append("| ").append(e.getKey().title).append(" | ").append(e.getValue().size())
                    .append(" | ").append(occurrences).append(" | ").append(top).append(" |\n");
        }

        r.append("\n## \u622a\u56fe\uff08\u6709\u6548\u679c\u56fe\u7684\u9875\u9762\u9644\u5e76\u6392\u5bf9\u6bd4\uff1a\u5de6\u6548\u679c\u56fe\uff0c\u53f3\u5f53\u524d\uff09\n\n");
        List<String> columns = new ArrayList<>(photographs.keySet());
        r.append("| \u9875\u9762 |");
        for (String column : columns) {
            String[] any = photographs.get(column).values().iterator().next();
            r.append(' ').append(any[2]).append(" |");
        }
        r.append("\n|---|");
        columns.forEach(c -> r.append("---|"));
        r.append('\n');
        for (Map.Entry<String, String> scene : sceneLabels.entrySet()) {
            r.append("| ").append(scene.getValue()).append(" |");
            for (String column : columns) {
                String[] files = photographs.get(column).get(scene.getKey());
                if (files == null) {
                    r.append(" \u2014 |");
                    continue;
                }
                r.append(" [\u622a\u56fe](").append(files[0]).append(')');
                if (files[1] != null) {
                    r.append(" \u00b7 [**\u5bf9\u6bd4**](").append(files[1]).append(')');
                }
                r.append(" |");
            }
            r.append('\n');
        }

        r.append("\n## \u65e7\u5411\u5bfc\u6e05\u5355\uff08P9 \u8ba1\u6570\uff09\n\n\u5171 ").append(wizardClasses.size())
                .append(" \u4e2a\u7c7b\u3002\n\n");
        Map<String, List<String>> byPackage = new TreeMap<>();
        for (String name : wizardClasses) {
            int dot = name.lastIndexOf('.');
            byPackage.computeIfAbsent(name.substring(0, dot), k -> new ArrayList<>())
                    .add(name.substring(dot + 1));
        }
        for (Map.Entry<String, List<String>> e : byPackage.entrySet()) {
            r.append("- `").append(e.getKey()).append("`\uff08").append(e.getValue().size())
                    .append("\uff09\uff1a").append(String.join("\u3001", e.getValue())).append('\n');
        }

        r.append("\n## \u660e\u7ec6\n");
        for (Map.Entry<UiAudit.Check, Map<String, List<UiAudit.Finding>>> e : grouped.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            r.append("\n### ").append(e.getKey().title).append("\uff08").append(e.getValue().size())
                    .append(" \u6761\uff09\n\n| \u4f4d\u7f6e | \u5185\u5bb9 | \u9875\u9762 | \u8f6e\u6b21 |\n|---|---|---|---|\n");
            int shown = 0;
            for (List<UiAudit.Finding> list : e.getValue().values()) {
                if (shown++ == 400) {
                    r.append("| \u2026 | \u53e6\u6709 ").append(e.getValue().size() - 400)
                            .append(" \u6761\uff0c\u89c1 findings.tsv | | |\n");
                    break;
                }
                UiAudit.Finding f = list.get(0);
                Set<String> scenes = new LinkedHashSet<>();
                Set<String> rounds = new LinkedHashSet<>();
                list.forEach(x -> {
                    scenes.add(x.scene);
                    rounds.add(x.round);
                });
                r.append("| ").append(cell(f.where)).append(" | ").append(cell(f.text)).append(" | ")
                        .append(cell(String.join("\u3001", scenes))).append(" | ")
                        .append(rounds.size() == 1 ? cell(rounds.iterator().next()) : rounds.size() + " \u8f6e")
                        .append(" |\n");
            }
        }
        Files.write(new File(out, "report.md").toPath(), r.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String cell(String text) {
        return text.replace("|", "\\|").replace("\n", " ");
    }

    // ----- plumbing ----------------------------------------------------------------------------

    /**
     * Runs on the event thread and waits for it, closing any modal dialog that stops it: a dialog
     * runs its own event loop, so the closing still gets through.
     */
    private void edt(Runnable runnable) throws Exception {
        edtGet(() -> {
            runnable.run();
            return null;
        });
    }

    private <T> T edtGet(Callable<T> callable) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            try {
                result.set(callable.call());
            }
            catch (Throwable t) {
                error.set(t);
            }
            finally {
                done.countDown();
            }
        });
        for (int i = 0; i < 6 && !done.await(10, TimeUnit.SECONDS); i++) {
            closeStrayDialogs("\u7b49\u5f85\u754c\u9762");
        }
        if (done.getCount() > 0) {
            throw new IllegalStateException("the event thread did not come back");
        }
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
        return result.get();
    }

    private void settle(long millis) throws Exception {
        Thread.sleep(millis);
        edt(() -> {
        });
        edt(() -> {
        });
    }

    private void say(String message) {
        log.println(new SimpleDateFormat("HH:mm:ss").format(new Date()) + " " + message);
    }

    private static Set<String> split(String list) {
        return list == null || list.isBlank() ? Set.of()
                : new LinkedHashSet<>(Arrays.asList(list.split(",")));
    }

    private static String themeName(String theme) {
        return "dark".equals(theme) ? "\u6df1\u8272" : "light".equals(theme) ? "\u6d45\u8272" : theme;
    }
}
