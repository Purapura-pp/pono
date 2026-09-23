package org.openpnp.gui;

import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.openpnp.Translations;
import org.openpnp.gui.shell.Dialogs;
import org.openpnp.gui.shell.DockPanel;
import org.openpnp.gui.shell.Forms;
import org.openpnp.gui.shell.Ui;
import org.openpnp.gui.support.AutoScroller;
import org.openpnp.gui.support.LogEntryListCellRenderer;
import org.openpnp.gui.support.LogEntryListModel;
import org.openpnp.gui.support.MessageBoxes;
import org.openpnp.logging.SystemLogger;
import org.openpnp.model.Configuration;
import org.pmw.tinylog.Configurator;
import org.pmw.tinylog.Level;
import org.pmw.tinylog.LogEntry;

/**
 * The log page, as mockup 17 draws it: one toolbar where there were two titled strips. What goes
 * into the log file - the level everything is recorded at, which is saved - is apart from what
 * this page shows, which only filters; the two used to sit in boxes titled alike with nothing
 * to tell them apart. Clearing asks first, the lines can be copied from a right click, and the
 * font follows the size chosen in the settings.
 */
@SuppressWarnings("serial")
public class LogPanel extends JPanel {

    private Preferences prefs = Preferences.userNodeForPackage(LogPanel.class);

    private static final String PREF_LOG_LEVEL = "LogPanel.logLevel"; //$NON-NLS-1$
    private static final String PREF_LOG_LEVEL_DEF = Level.INFO.toString();

    private final Configuration configuration;

    private boolean systemOutEnabled = true;

    private LogEntryListModel logEntries = new LogEntryListModel();
    private JList<LogEntry> logEntryJList = new JList<>(logEntries);
    private final LogEntryListCellRenderer renderer = new LogEntryListCellRenderer();

    private LogEntryListModel.LogEntryFilter logLevelFilter = new LogEntryListModel.LogEntryFilter();
    private LogEntryListModel.LogEntryFilter searchBarFilter = new LogEntryListModel.LogEntryFilter();
    private LogEntryListModel.LogEntryFilter systemOutFilter = new LogEntryListModel.LogEntryFilter();

    private ScheduledExecutorService scheduledExecutor;
    private final DockPanel.Tab logTab;

    /** What "Show" offers: everything, or from the level up. */
    private static final List<Level> SHOWN = List.of(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARNING, Level.ERROR);

    public LogPanel(Configuration configuration) {
        this.configuration = configuration;

        loadLoggingPreferences();

        logEntries.addFilter(logLevelFilter);
        logEntries.addFilter(searchBarFilter);
        logEntries.addFilter(systemOutFilter);

        setLayout(new BorderLayout(0, 0));
        setOpaque(false);
        setBorder(new javax.swing.border.EmptyBorder(0, 10, 10, 10));
        putClientProperty(MainFrame.DOCK_PAGE, Boolean.TRUE);

        logEntryJList.setCellRenderer(renderer);
        logEntryJList.setFixedCellHeight(renderer.rowHeight());
        JScrollPane scrollPane = new JScrollPane(logEntryJList);
        scrollPane.setBorder(null);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        AutoScroller autoScroller = new AutoScroller(scrollPane);

        // ---- the toolbar ----
        DockPanel.Toolbar toolbar = new DockPanel.Toolbar();
        toolbar.add(Ui.t2(Translations.getString("LogPanel.RecordLevel"))); //$NON-NLS-1$
        toolbar.add(createGlobalLogLevel());
        toolbar.separator();
        toolbar.add(Ui.t2(Translations.getString("LogPanel.Show"))); //$NON-NLS-1$
        Forms.Segmented shown = new Forms.Segmented(SHOWN,
                v -> Translations.getString(v == Level.TRACE ? "LogPanel.Show.All" : "LogPanel.Level." + ((Level) v).name())) //$NON-NLS-1$ //$NON-NLS-2$
                .tight();
        shown.setSelectedItem(Level.TRACE);
        shown.setToolTipText(Translations.getString("LogPanel.Show.ToolTip")); //$NON-NLS-1$
        shown.onChange(() -> {
            LogEntry entry = getSelectedEntry();
            Level level = (Level) shown.getSelectedItem();
            logLevelFilter.setFilter(logEntry -> logEntry.getLevel().compareTo(level) >= 0);
            logEntries.filter();
            setSelectedEntry(entry);
        });
        toolbar.add(shown);
        toolbar.separator();
        Forms.Toggle follow = new Forms.Toggle();
        follow.setSelected(true);
        follow.onChange(() -> autoScroller.setEnabled(follow.isSelected()));
        JPanel followRow = Forms.toggleRow(follow, Translations.getString("LogPanel.AutoScroll")); //$NON-NLS-1$
        followRow.setToolTipText(Translations.getString("LogPanel.AutoScroll.ToolTip")); //$NON-NLS-1$
        followRow.setMaximumSize(followRow.getPreferredSize());
        toolbar.add(followRow);
        toolbar.glue();
        JTextField searchTextField = toolbar.filter(Translations.getString("LogPanel.Search.Placeholder")); //$NON-NLS-1$
        searchTextField.getDocument().addDocumentListener(new DocumentListener() {
            private void updateSearchBarFilter() {
                LogEntry entry = getSelectedEntry();
                String searchText = searchTextField.getText().toLowerCase();
                searchBarFilter.setFilter(logEntry -> logEntry.getRenderedLogEntry().toLowerCase().contains(searchText));
                logEntries.filter();
                setSelectedEntry(entry);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateSearchBarFilter();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                updateSearchBarFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateSearchBarFilter();
            }
        });
        toolbar.button(copySelectedAction, "copy", "LogPanel.CopySelected", Ui.Variant.Ghost); //$NON-NLS-1$ //$NON-NLS-2$
        toolbar.button(openFolderAction, "folder", "LogPanel.OpenFolder", Ui.Variant.Ghost); //$NON-NLS-1$ //$NON-NLS-2$
        toolbar.button(clearAction, "trash", "LogPanel.Clear", Ui.Variant.Danger); //$NON-NLS-1$ //$NON-NLS-2$
        JButton more = Ui.menuButton(null, Ui.iconSm("more"), Ui.Size.Sm, Ui.Variant.Ghost, () -> { //$NON-NLS-1$
            JPopupMenu menu = new JPopupMenu();
            JCheckBoxMenuItem systemOut = new JCheckBoxMenuItem(Translations.getString("LogPanel.SystemOutput"), //$NON-NLS-1$
                    systemOutEnabled);
            systemOut.setToolTipText(Translations.getString("LogPanel.SystemOutput.ToolTip")); //$NON-NLS-1$
            systemOut.addActionListener(e -> setSystemOutput(systemOut.isSelected()));
            menu.add(systemOut);
            return menu;
        });
        more.setFocusable(false);
        more.setToolTipText(Translations.getString("Dock.More")); //$NON-NLS-1$
        toolbar.add(more);

        // ---- the lines ----
        logEntryJList.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_C,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), "log-copy"); //$NON-NLS-1$
        logEntryJList.getActionMap().put("log-copy", copySelectedAction); //$NON-NLS-1$
        JPopupMenu popup = new JPopupMenu();
        popup.add(copySelectedAction);
        popup.add(copyShownAction);
        logEntryJList.setComponentPopupMenu(popup);

        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        page.add(toolbar, BorderLayout.NORTH);
        page.add(scrollPane, BorderLayout.CENTER);
        page.add(DockPanel.foot(Translations.getString("LogPanel.Foot")), BorderLayout.SOUTH); //$NON-NLS-1$
        DockPanel dock = new DockPanel();
        logTab = dock.addTab(Ui.iconSm("log"), Translations.getString("LogPanel.Tab.Log"), page); //$NON-NLS-1$ //$NON-NLS-2$
        dock.setMaximize(() -> MainFrame.get().toggleDockMaximised());
        add(dock, BorderLayout.CENTER);
        logEntries.addListDataListener(new javax.swing.event.ListDataListener() {
            @Override
            public void intervalAdded(javax.swing.event.ListDataEvent e) {
                logTab.setCount(logEntries.getSize());
            }

            @Override
            public void intervalRemoved(javax.swing.event.ListDataEvent e) {
                logTab.setCount(logEntries.getSize());
            }

            @Override
            public void contentsChanged(javax.swing.event.ListDataEvent e) {
                logTab.setCount(logEntries.getSize());
            }
        });

        scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutor.scheduleAtFixedRate(new Runnable() {
            public void run() {
                refreshLogIfOnTop();
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    /** The lines are as tall as the font size chosen makes them. */
    @Override
    public void updateUI() {
        super.updateUI();
        if (renderer != null && logEntryJList != null) {
            logEntryJList.setFixedCellHeight(renderer.rowHeight());
        }
    }

    private void setSystemOutput(boolean enabled) {
        systemOutEnabled = enabled;
        if (systemOutEnabled) {
            systemOutFilter.setFilter(logEntry -> true);
        } else {
            systemOutFilter.setFilter(logEntry -> !Objects.equals(logEntry.getClassName(), SystemLogger.class.getName()));
        }
        logEntries.filter();
    }

    private void loadLoggingPreferences() {
        Configurator
                .currentConfig()
                .level(storedLevel())
                .activate();

        Configurator
                .currentConfig()
                .addWriter(logEntries)
                .activate();
    }

    /**
     * The level the log records at, as saved. An older version stored an int under the same key,
     * and Level.valueOf threw on it while the main window was being built.
     */
    private Level storedLevel() {
        try {
            return Level.valueOf(prefs.get(PREF_LOG_LEVEL, PREF_LOG_LEVEL_DEF));
        }
        catch (Exception e) {
            return Level.INFO;
        }
    }

    /** What is written to the log file, for the whole program, and kept: not a filter of this page. */
    private JComboBox<Level> createGlobalLogLevel() {
        JComboBox<Level> level = Forms.dropdown(new JComboBox<>(Level.values()));
        level.setSelectedItem(storedLevel());
        level.setToolTipText(Translations.getString("LogPanel.RecordLevel.ToolTip")); //$NON-NLS-1$
        level.addActionListener(e -> {
            Level logLevel = (Level) level.getSelectedItem();
            prefs.put(PREF_LOG_LEVEL, logLevel.toString());
            Configurator
                    .currentConfig()
                    .level(logLevel)
                    .activate();
            MainFrame frame = MainFrame.get();
            if (frame != null) {
                frame.setStatus(String.format(Translations.getString("LogPanel.RecordLevel.Changed"), //$NON-NLS-1$
                        org.openpnp.gui.support.DisplayNames.of(logLevel)));
            }
        });
        level.setMaximumSize(new java.awt.Dimension(150, 28));
        level.setPreferredSize(new java.awt.Dimension(130, 28));
        return level;
    }

    private void copyStringToClipboard(String s) {
        StringSelection selection = new StringSelection(s);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, selection);
    }

    private final AbstractAction copySelectedAction = new AbstractAction(
            Translations.getString("LogPanel.CopySelected.Menu")) { //$NON-NLS-1$
        {
            putValue(SHORT_DESCRIPTION, Translations.getString("LogPanel.CopySelected.ToolTip")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            List<LogEntry> logList = logEntryJList.getSelectedValuesList();
            StringBuilder sb = new StringBuilder();
            logList.forEach(logEntry -> sb.append(logEntry.getRenderedLogEntry()));
            copyStringToClipboard(sb.toString());
        }
    };

    private final AbstractAction copyShownAction = new AbstractAction(
            Translations.getString("LogPanel.CopyShown")) { //$NON-NLS-1$
        @Override
        public void actionPerformed(ActionEvent e) {
            StringBuilder sb = new StringBuilder();
            logEntries.getFilteredLogEntries().forEach(logEntry -> sb.append(logEntry.getRenderedLogEntry()));
            copyStringToClipboard(sb.toString());
        }
    };

    private final AbstractAction openFolderAction = new AbstractAction(
            Translations.getString("LogPanel.OpenFolder")) { //$NON-NLS-1$
        {
            putValue(SHORT_DESCRIPTION, Translations.getString("LogPanel.OpenFolder.ToolTip")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            File folder = new File(configuration.getConfigurationDirectory(), "log"); //$NON-NLS-1$
            try {
                java.awt.Desktop.getDesktop().open(folder);
            }
            catch (Exception ex) {
                MessageBoxes.errorBox(LogPanel.this, Translations.getString("LogPanel.OpenFolder.Error"), ex); //$NON-NLS-1$
            }
        }
    };

    /** Only this page's lines go; the log file keeps them. Asked first, as it was not. */
    private final AbstractAction clearAction = new AbstractAction(Translations.getString("LogPanel.Clear")) { //$NON-NLS-1$
        {
            putValue(SHORT_DESCRIPTION, Translations.getString("LogPanel.Clear.ToolTip")); //$NON-NLS-1$
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            int lines = logEntries.getOriginalLogEntries().size();
            if (lines == 0) {
                return;
            }
            int chosen = Dialogs.ask(LogPanel.this, Dialogs.Tone.Warn, "trash", //$NON-NLS-1$
                    String.format(Translations.getString("LogPanel.Clear.Title"), lines), //$NON-NLS-1$
                    Translations.getString("LogPanel.Clear.What"), null, //$NON-NLS-1$
                    Dialogs.Choice.danger(Translations.getString("LogPanel.Clear.Action"))); //$NON-NLS-1$
            if (chosen == 0) {
                logEntries.clear();
            }
        }
    };

    protected void refreshLogIfOnTop() {
        if (isShowing()) {
            if (logEntries.isRefreshNeeded()) {
                logEntries.refresh();
            }
        }
    }

    protected LogEntry getSelectedEntry() {
        int index = logEntryJList.getSelectedIndex();
        if (index >= 0) {
            return logEntries.getElementAt(index);
        }
        return null;
    }

    protected void setSelectedEntry(LogEntry entry) {
        if (entry != null) {
            int index = logEntries.getFilteredLogEntries().indexOf(entry);
            if (index >= 0) {
                logEntryJList.setSelectedIndex(index);
            }
        }
    }
}
