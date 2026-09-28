package org.openpnp.gui;

import java.awt.Component;
import java.awt.Desktop;
import java.awt.GridLayout;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.apache.commons.io.FileUtils;
import org.openpnp.Main;
import org.openpnp.Translations;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Ui;
import org.openpnp.imgur.Imgur;
import org.openpnp.imgur.Imgur.Album;
import org.openpnp.imgur.Imgur.Image;
import org.openpnp.model.Board;
import org.openpnp.model.BoardLocation;
import org.openpnp.model.Configuration;
import org.openpnp.model.Job;
import org.openpnp.util.UiUtils;
import org.pmw.tinylog.Logger;

import com.github.kennedyoliveira.pastebin4j.AccountCredentials;
import com.github.kennedyoliveira.pastebin4j.Paste;
import com.github.kennedyoliveira.pastebin4j.PasteBin;
import com.github.kennedyoliveira.pastebin4j.PasteExpiration;
import com.github.kennedyoliveira.pastebin4j.PasteVisibility;

/**
 * Help → Submit a help request: what went wrong in your words, and the files that show it - the
 * configuration, the log, the job, a screenshot, the latest vision images. Uploaded, the text goes
 * to an unlisted Pastebin paste and the pictures to an Imgur album, and the browser opens the paste
 * to share. Packed, they are copied into a folder of their own under help in the configuration
 * directory, which is opened, for whoever the user chooses to send it to; that is also what a
 * failed upload offers.
 */
public final class SubmitDiagnosticsDialog {
    /** The vision images taken along, newest first. */
    private static final int VISION_IMAGES = 10;
    private static final int WIDTH = 640;

    private SubmitDiagnosticsDialog() {
    }

    /** What to take along, as ticked. */
    private static final class Included {
        String description;
        boolean machine, parts, packages, log, systemInfo, job, screenshot, vision;
    }

    public static void show(MainFrame frame) {
        // Taken before the question is up, or the question is in it.
        BufferedImage screenshot = null;
        try {
            screenshot = new Robot().createScreenCapture(frame.getBounds());
        }
        catch (Exception e) {
            Logger.warn(e, "No screenshot could be taken; the help request goes without one."); //$NON-NLS-1$
        }
        JTextArea description = new JTextArea(6, 20);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(description);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        // As wide as the dialog's text, which a text area sized by its columns pushed off the edge.
        scroll.setPreferredSize(new java.awt.Dimension(WIDTH - 110, 110));
        JCheckBox machine = check("SubmitDiagnosticsDialog.MachineXml"); //$NON-NLS-1$
        JCheckBox parts = check("SubmitDiagnosticsDialog.PartsXml"); //$NON-NLS-1$
        JCheckBox packages = check("SubmitDiagnosticsDialog.PackagesXml"); //$NON-NLS-1$
        JCheckBox log = check("SubmitDiagnosticsDialog.includeLogChk.text"); //$NON-NLS-1$
        JCheckBox systemInfo = check("SubmitDiagnosticsDialog.includeSystemInfoChk.text"); //$NON-NLS-1$
        JCheckBox job = check("SubmitDiagnosticsDialog.includeJobChk.text"); //$NON-NLS-1$
        JCheckBox shot = check("SubmitDiagnosticsDialog.includeScreenShotChk.text"); //$NON-NLS-1$
        shot.setEnabled(screenshot != null);
        shot.setSelected(screenshot != null);
        JCheckBox vision = check("SubmitDiagnosticsDialog.includeVisionChk.text"); //$NON-NLS-1$
        JPanel boxes = new JPanel(new GridLayout(0, 2, 12, 2));
        boxes.setOpaque(false);
        boxes.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (JCheckBox box : new JCheckBox[] { machine, parts, packages, log, systemInfo, job, shot, vision }) {
            boxes.add(box);
        }
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(heading("SubmitDiagnosticsDialog.lblComments.text")); //$NON-NLS-1$
        body.add(Box.createVerticalStrut(4));
        body.add(scroll);
        body.add(Box.createVerticalStrut(10));
        body.add(heading("SubmitDiagnosticsDialog.lblInclude.text")); //$NON-NLS-1$
        body.add(Box.createVerticalStrut(4));
        body.add(boxes);
        String title = Translations.getString("SubmitDiagnosticsDialog.lblSubmitAHelp.text"); //$NON-NLS-1$
        Dialogs.Content content = new Dialogs.Content().tone(Dialogs.Tone.Info, "upload").title(title) //$NON-NLS-1$
                .what(Translations.getString("SubmitDiagnosticsDialog.What")) //$NON-NLS-1$
                .more(Translations.getString("SubmitDiagnosticsDialog.More")) //$NON-NLS-1$
                .body(body).focus(description).width(WIDTH);
        int answer = Dialogs.show(frame, content, Arrays.asList(Dialogs.Choice.cancel(),
                Dialogs.Choice.plain(Translations.getString("SubmitDiagnosticsDialog.Pack")), //$NON-NLS-1$
                Dialogs.Choice.primary(Translations.getString("SubmitDiagnosticsDialog.Upload"))), 0, 2); //$NON-NLS-1$
        if (answer == 0) {
            return;
        }
        Included included = new Included();
        included.description = description.getText();
        included.machine = machine.isSelected();
        included.parts = parts.isSelected();
        included.packages = packages.isSelected();
        included.log = log.isSelected();
        included.systemInfo = systemInfo.isSelected();
        included.job = job.isSelected();
        included.screenshot = shot.isSelected();
        included.vision = vision.isSelected();
        BufferedImage taken = included.screenshot ? screenshot : null;
        boolean upload = answer == 2;
        frame.getStatusBar().setBusy(true);
        frame.setStatus(Translations.getString(upload ? "SubmitDiagnosticsDialog.Uploading" //$NON-NLS-1$
                : "SubmitDiagnosticsDialog.Packing")); //$NON-NLS-1$
        Thread thread = new Thread(() -> {
            try {
                Map<String, String> texts = texts(frame, included);
                List<File> images = images(included, taken);
                if (upload) {
                    String url = upload(included.description, texts, images);
                    Logger.info("Help request uploaded to {}", url); //$NON-NLS-1$
                    SwingUtilities.invokeLater(() -> {
                        frame.getStatusBar().setBusy(false);
                        frame.setStatus(String.format(Translations.getString("SubmitDiagnosticsDialog.Uploaded"), url)); //$NON-NLS-1$
                        UiUtils.browseUri(url);
                    });
                }
                else {
                    File folder = pack(included.description, texts, images);
                    SwingUtilities.invokeLater(() -> packed(frame, folder));
                }
            }
            catch (Exception e) {
                Logger.error(e, "The help request could not be {}.", upload ? "uploaded" : "packed"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                SwingUtilities.invokeLater(() -> {
                    frame.getStatusBar().setBusy(false);
                    frame.setStatus(""); //$NON-NLS-1$
                    if (upload && Dialogs.ask(frame, Dialogs.Tone.Warn, "alert", //$NON-NLS-1$
                            Translations.getString("SubmitDiagnosticsDialog.Submit.ErrorBox.Title"), //$NON-NLS-1$
                            e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                            Translations.getString("SubmitDiagnosticsDialog.PackInstead"), //$NON-NLS-1$
                            Dialogs.Choice.primary(Translations.getString("SubmitDiagnosticsDialog.Pack"))) == 0) { //$NON-NLS-1$
                        packAfterAll(frame, included, taken);
                    }
                    else if (!upload) {
                        Dialogs.error(frame, Translations.getString("SubmitDiagnosticsDialog.Pack.Failed"), e, null, false); //$NON-NLS-1$
                    }
                });
            }
        }, "pono-help-request"); //$NON-NLS-1$
        thread.setDaemon(true);
        thread.start();
    }

    /** Packs what the upload could not send. */
    private static void packAfterAll(MainFrame frame, Included included, BufferedImage screenshot) {
        frame.getStatusBar().setBusy(true);
        Thread thread = new Thread(() -> {
            try {
                File folder = pack(included.description, texts(frame, included), images(included, screenshot));
                SwingUtilities.invokeLater(() -> packed(frame, folder));
            }
            catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    frame.getStatusBar().setBusy(false);
                    Dialogs.error(frame, Translations.getString("SubmitDiagnosticsDialog.Pack.Failed"), e, null, false); //$NON-NLS-1$
                });
            }
        }, "pono-help-request"); //$NON-NLS-1$
        thread.setDaemon(true);
        thread.start();
    }

    private static void packed(MainFrame frame, File folder) {
        frame.getStatusBar().setBusy(false);
        frame.setStatus(String.format(Translations.getString("SubmitDiagnosticsDialog.Packed"), folder)); //$NON-NLS-1$
        try {
            Desktop.getDesktop().open(folder);
        }
        catch (Exception e) {
            Logger.warn(e, "The help request folder {} could not be opened.", folder); //$NON-NLS-1$
        }
    }

    private static JCheckBox check(String key) {
        JCheckBox box = Forms.check(Translations.getString(key));
        box.setSelected(true);
        return box;
    }

    private static JLabel heading(String key) {
        JLabel label = new JLabel(Translations.getString(key));
        label.setFont(Ui.weighted(Ui.BASE, 600));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /** The text files, by the name each goes under, the job's saved first. */
    private static Map<String, String> texts(MainFrame frame, Included included) throws Exception {
        Configuration configuration = Configuration.get();
        configuration.save();
        File configDir = configuration.getConfigurationDirectory();
        File logDir = new File(configDir, "log"); //$NON-NLS-1$
        Map<String, String> texts = new LinkedHashMap<>();
        if (included.machine) {
            add(texts, new File(configDir, "machine.xml")); //$NON-NLS-1$
        }
        if (included.parts) {
            add(texts, new File(configDir, "parts.xml")); //$NON-NLS-1$
        }
        if (included.packages) {
            add(texts, new File(configDir, "packages.xml")); //$NON-NLS-1$
        }
        if (included.log) {
            add(texts, new File(logDir, "OpenPnP.log")); //$NON-NLS-1$
        }
        if (included.systemInfo) {
            texts.put("SystemInfo.txt", systemInfo()); //$NON-NLS-1$
        }
        if (included.job && frame.getJobTab() != null && frame.getJobTab().getJob() != null) {
            Job job = frame.getJobTab().getJob();
            File file = File.createTempFile("OpenPnP-Diagnostics", ".job.xml"); //$NON-NLS-1$ //$NON-NLS-2$
            configuration.saveJob(job, file);
            add(texts, file);
            Set<Board> boards = new LinkedHashSet<>();
            for (BoardLocation location : job.getBoardLocations()) {
                boards.add(location.getBoard());
            }
            for (Board board : boards) {
                if (board.getFile() != null) {
                    add(texts, board.getFile());
                }
            }
        }
        return texts;
    }

    private static void add(Map<String, String> texts, File file) throws Exception {
        if (file.isFile()) {
            texts.put(file.getName(), FileUtils.readFileToString(file, StandardCharsets.UTF_8));
        }
    }

    private static List<File> images(Included included, BufferedImage screenshot) throws Exception {
        List<File> images = new ArrayList<>();
        if (screenshot != null) {
            File file = File.createTempFile("OpenPnP-Screenshot", ".png"); //$NON-NLS-1$ //$NON-NLS-2$
            ImageIO.write(screenshot, "PNG", file); //$NON-NLS-1$
            images.add(file);
        }
        if (included.vision) {
            File visionDir = new File(new File(Configuration.get().getConfigurationDirectory(), "log"), "vision"); //$NON-NLS-1$ //$NON-NLS-2$
            File[] files = visionDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".png")); //$NON-NLS-1$
            if (files != null) {
                Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
                images.addAll(Arrays.asList(files).subList(0, Math.min(files.length, VISION_IMAGES)));
            }
        }
        return images;
    }

    /** @return The paste's address. */
    private static String upload(String description, Map<String, String> texts, List<File> images) throws Exception {
        Album album = null;
        if (!images.isEmpty()) {
            Imgur imgur = new Imgur(Configuration.get().getImgurClientId());
            List<Image> uploaded = new ArrayList<>();
            for (File file : images) {
                uploaded.add(imgur.uploadImage(file));
            }
            album = imgur.createAlbum("OpenPnP Diagnostics Images", uploaded.toArray(new Image[] {})); //$NON-NLS-1$
        }
        StringBuilder files = new StringBuilder();
        for (Map.Entry<String, String> text : texts.entrySet()) {
            files.append(String.format("**** %s ****\n\n%s\n\n", text.getKey(), text.getValue())); //$NON-NLS-1$
        }
        PasteBin pasteBin = new PasteBin(new AccountCredentials("37ccaf49071a6226ad8f96efdfa9e936")); //$NON-NLS-1$
        Paste paste = new Paste();
        paste.setTitle("OpenPnP Diagnostics"); //$NON-NLS-1$
        paste.setExpiration(PasteExpiration.ONE_MONTH);
        paste.setVisibility(PasteVisibility.UNLISTED);
        paste.setContent(String.format("OpenPnP Diagnostics\n\nImages: %s\n\nDescription: %s\n\nFiles:\n\n%s", //$NON-NLS-1$
                album == null ? "None" : "http://imgur.com/a/" + album.id, description, files)); //$NON-NLS-1$ //$NON-NLS-2$
        return pasteBin.createPaste(paste);
    }

    /** @return The folder, help/yyyyMMdd-HHmmss under the configuration directory. */
    private static File pack(String description, Map<String, String> texts, List<File> images) throws Exception {
        File folder = new File(new File(Configuration.get().getConfigurationDirectory(), "help"), //$NON-NLS-1$
                new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date())); //$NON-NLS-1$
        if (!folder.mkdirs()) {
            throw new Exception("Cannot create " + folder); //$NON-NLS-1$
        }
        Files.write(new File(folder, "description.txt").toPath(), description.getBytes(StandardCharsets.UTF_8)); //$NON-NLS-1$
        for (Map.Entry<String, String> text : texts.entrySet()) {
            Files.write(new File(folder, text.getKey()).toPath(), text.getValue().getBytes(StandardCharsets.UTF_8));
        }
        int n = 0;
        for (File image : images) {
            String name = image.getName().startsWith("OpenPnP-Screenshot") ? "screenshot.png" //$NON-NLS-1$ //$NON-NLS-2$
                    : String.format("vision-%02d-%s", ++n, image.getName()); //$NON-NLS-1$
            Files.copy(image.toPath(), new File(folder, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return folder;
    }

    static String systemInfo() {
        StringBuilder sb = new StringBuilder();
        String[] keys = new String[] { "os.name", "os.arch", "java.runtime.name", "java.vm.vendor", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                "java.vm.name", "user.country", "java.runtime.version", "os.version", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                "java.vm.info", "java.version", }; //$NON-NLS-1$ //$NON-NLS-2$
        for (String key : keys) {
            sb.append(String.format("%s: %s\n", key, System.getProperty(key))); //$NON-NLS-1$
        }
        sb.append(String.format("Memory Total: %.2f\n", Runtime.getRuntime().totalMemory() / 1024.0 / 1024.0)); //$NON-NLS-1$
        sb.append(String.format("Memory Free: %.2f\n", Runtime.getRuntime().freeMemory() / 1024.0 / 1024.0)); //$NON-NLS-1$
        sb.append(String.format("Memory Max: %.2f\n", Runtime.getRuntime().maxMemory() / 1024.0 / 1024.0)); //$NON-NLS-1$
        sb.append(String.format("OpenPnp Version: %s", Main.getVersion())); //$NON-NLS-1$
        return sb.toString();
    }
}
