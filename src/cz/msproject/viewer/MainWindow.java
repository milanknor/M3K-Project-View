package cz.msproject.viewer;

import org.mpxj.ProjectFile;
import org.mpxj.Task;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.event.ChangeEvent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

/** Hlavní okno prohlížeče. */
public final class MainWindow extends JFrame {
    private static final long serialVersionUID = 1L;
    public static final String APP_NAME = "M3K Project View";
    /** Název s oranžovou trojkou podle loga (pro HTML popisky). */
    static final String APP_NAME_HTML = "<b>M<span style='color:#FF790A'>3</span>K</b> Project View";
    public static final String VERSION = "0.3.1";
    public static final String AUTHOR = "Milan Knor";
    /** Otevřená okna (každé = jeden soubor); po zavření posledního se aplikace ukončí. */
    static final List<MainWindow> WINDOWS = new ArrayList<>();
    /** Během přestavby oken (změna vzhledu) se po zavření posledního okna neukončuje. */
    static boolean restarting;

    final TaskTree tree = new TaskTree();
    final Columns columns = new Columns();
    final TaskTableModel model = new TaskTableModel(tree, columns);
    final JTable table = new JTable(model);
    Columns.Column sortColumn;
    boolean sortAscending = true;
    final GanttPanel gantt = new GanttPanel(tree, table);
    final DetailsPanel details = new DetailsPanel();
    final ResourcePanel resources = new ResourcePanel();
    final ResourceUsagePanel usage = new ResourceUsagePanel();
    final CalendarPanel calendars = new CalendarPanel();
    final TimelinePanel timeline = new TimelinePanel();
    final ProjectInfoPanel info = new ProjectInfoPanel();
    final JTextField search = new JTextField(18);
    final JComboBox<TaskTree.Filter> filterBox = new JComboBox<>(TaskTree.Filter.values());
    final JButton extraFilterButton = new JButton(Str.t("Filtr…"));
    TaskFilter extraFilter;
    final JComboBox<String> levelBox = new JComboBox<>();
    final JComboBox<GanttPanel.Zoom> zoomBox = new JComboBox<>(GanttPanel.Zoom.values());
    final JCheckBox linksBox = new JCheckBox(Str.t("Vazby"), true);
    final JCheckBox baselineBox = new JCheckBox(Str.t("Základní plán"), false);
    final JComboBox<String> baselineSel = new JComboBox<>();
    final JCheckBox labelsBox = new JCheckBox(Str.t("Popisky"), true);
    final JCheckBox slackBox = new JCheckBox(Str.t("Rezerva"), false);
    final JLabel status = new JLabel(Str.t("Otevřete soubor Microsoft Project (Ctrl+O) nebo jej přetáhněte do okna."));
    final JLabel stats = new JLabel(" ");
    final JLabel titleLabel = new JLabel(Str.t("Žádný otevřený soubor"));
    final JTabbedPane tabs = new JTabbedPane();
    final JMenu recentMenu = new JMenu(Str.t("Nedávné soubory"));
    final JMenu windowMenu = new JMenu(Str.t("Okno"));
    final StartPanel start = new StartPanel(this::open, this::chooseFile);
    final JPanel center = new JPanel(new java.awt.CardLayout());
    final JScrollPane tableScroll;
    final JSplitPane mainSplit;
    final JSplitPane verticalSplit;
    final Preferences prefs = Preferences.userNodeForPackage(MainWindow.class);
    final RecentFiles recent = new RecentFiles(prefs);
    final Exports exports = new Exports(this);

    ProjectFile project;
    Path currentPath;
    boolean loading;
    /** Po otevření souboru přizpůsobit šířky sloupců obsahu – dokud si je uživatel nenastaví sám. */
    private boolean autoFitWidths = prefs.getBoolean("autoFitColumns", true);
    /** Čas poslední změny souboru při načtení – hlídá přepsání na síťovém disku. */
    private long loadedStamp;
    private boolean suppressEvents;

    public MainWindow() {
        super(APP_NAME);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setIconImages(Icons.appImages());
        setMinimumSize(new Dimension(900, 600));
        WINDOWS.add(this);

        // ---- tabulka úkolů ----
        table.setFont(Theme.BASE);
        table.setRowHeight(Theme.ROW);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setShowVerticalLines(false);
        table.setGridColor(Theme.GRID);
        table.setFillsViewportHeight(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.getTableHeader().setReorderingAllowed(true);
        table.getTableHeader().setPreferredSize(new Dimension(0, Theme.HEADER));
        table.getTableHeader().setFont(Theme.BOLD.deriveFont(12f));
        table.getTableHeader().setToolTipText("Kliknutí řadí (vzestupně / sestupně / podle souboru), pravé tlačítko vybírá sloupce, tažením se mění pořadí a šířka.");
        table.setSelectionBackground(Theme.SELECTION);
        table.setSelectionForeground(Theme.INK);
        details.setColumns(columns);
        applyColumns(loadColumnSetting());
        // jakmile si uživatel šířku sloupce nastaví sám, přestaneme ji po otevření souboru přepisovat
        table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            @Override
            public void columnMarginChanged(ChangeEvent e) {
                if (table.getTableHeader().getResizingColumn() != null) {
                    setAutoFitWidths(false);
                }
            }

            @Override
            public void columnAdded(TableColumnModelEvent e) {
            }

            @Override
            public void columnRemoved(TableColumnModelEvent e) {
            }

            @Override
            public void columnMoved(TableColumnModelEvent e) {
            }

            @Override
            public void columnSelectionChanged(ListSelectionEvent e) {
            }
        });
        table.getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                    return;
                }
                int view = table.getTableHeader().columnAtPoint(e.getPoint());
                if (view >= 0 && e.getClickCount() == 1 && table.getTableHeader().getDraggedColumn() == null) {
                    sortBy(model.columnAt(table.convertColumnIndexToModel(view)));
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showColumnChooser(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showColumnChooser(e.getX(), e.getY());
                }
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int r = table.getSelectionModel().getLeadSelectionIndex();
                if (!table.isRowSelected(r)) {
                    r = table.getSelectedRow();
                }
                details.show(r >= 0 && r < tree.visible().size() ? tree.visible().get(r) : null);
                updateSelectionStatus();
                gantt.getChart().repaint();
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int r = table.rowAtPoint(e.getPoint());
                int c = table.columnAtPoint(e.getPoint());
                if (r < 0 || r >= tree.visible().size()) {
                    return;
                }
                TaskTree.Row row = tree.visible().get(r);
                if (c >= 0 && model.columnAt(table.convertColumnIndexToModel(c)) == Columns.NAME && row.isSummary() && e.getClickCount() == 1) {
                    Rectangle cell = table.getCellRect(r, c, false);
                    int glyphStart = cell.x + 4 + row.depth * 16;
                    if (e.getX() >= glyphStart && e.getX() <= glyphStart + 18) {
                        toggle(row);
                    }
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int r = table.rowAtPoint(e.getPoint());
                if (e.getClickCount() == 2 && r >= 0 && r < tree.visible().size()) {
                    toggle(tree.visible().get(r));
                }
            }
        });
        Ui.bindKey(table, KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), "copyRows", this::copySelection);
        Ui.bindKey(table, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggle", () -> selectedRow().ifPresent(this::toggle));
        Ui.bindKey(table, KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0), "expand", () -> selectedRow().ifPresent(r -> setCollapsed(r, false)));
        Ui.bindKey(table, KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, 0), "expand2", () -> selectedRow().ifPresent(r -> setCollapsed(r, false)));
        Ui.bindKey(table, KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0), "collapse", () -> selectedRow().ifPresent(r -> setCollapsed(r, true)));
        Ui.bindKey(table, KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0), "collapse2", () -> selectedRow().ifPresent(r -> setCollapsed(r, true)));

        tableScroll = new JScrollPane(table);
        tableScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        tableScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        tableScroll.setBorder(null);
        tableScroll.getHorizontalScrollBar().setUnitIncrement(40);
        // Sdílený model svislého posuvu = tabulka a diagram se posouvají společně.
        gantt.getScrollPane().getVerticalScrollBar().setModel(tableScroll.getVerticalScrollBar().getModel());
        gantt.setRowsChangedCallback(this::refresh);
        gantt.setZoomListener(z -> {
            suppressEvents = true;
            try {
                if (z == null) {
                    zoomBox.setSelectedIndex(-1);
                } else {
                    zoomBox.setSelectedItem(z);
                }
            } finally {
                suppressEvents = false;
            }
        });

        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tableScroll, gantt);
        mainSplit.setResizeWeight(0.45);
        mainSplit.setDividerLocation(720);
        mainSplit.setBorder(null);
        mainSplit.setContinuousLayout(true);
        details.setPreferredSize(new Dimension(100, 210));
        details.setMinimumSize(new Dimension(100, 80));
        verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainSplit, details);
        verticalSplit.setResizeWeight(0.78);
        verticalSplit.setBorder(null);
        verticalSplit.setContinuousLayout(true);

        details.setNavigator(this::goToTask);
        details.setSubprojectOpener(this::openSubproject);
        resources.setNavigator(this::goToTask);
        usage.setNavigator(this::goToTask);
        timeline.setNavigator(this::goToTask);
        timeline.setExporter(img -> exports.savePng(img, "-casova-osa", Str.t("Uložit časovou osu jako PNG")));

        tabs.setFont(Theme.BASE);
        tabs.addTab(Str.t("Ganttův diagram"), verticalSplit);
        tabs.addTab(Str.t("Časová osa"), timeline);
        tabs.addTab(Str.t("Zdroje"), resources);
        tabs.addTab(Str.t("Vytížení"), usage);
        tabs.addTab(Str.t("Kalendáře"), calendars);
        tabs.addTab(Str.t("Projekt"), info);

        setJMenuBar(buildMenu());
        add(buildToolbar(), BorderLayout.NORTH);
        center.add(start, "start");
        center.add(tabs, "main");
        add(center, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        installDropHandler(getRootPane(), table, gantt.getChart(), gantt.getHeader(), resources.table(), usage.table(), usage, calendars, calendars.calendarList(),
                timeline, details, info, tabs, start);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                checkFileChanged();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                saveWindowState();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                WINDOWS.remove(MainWindow.this);
                if (WINDOWS.isEmpty() && !restarting) {
                    System.exit(0);
                }
            }
        });
        restoreWindowState();
        updateLevelBox();
        updateRecentMenu();
        updateStats();
    }

    // ---------------------------------------------------------------- UI stavba

    private JMenuBar buildMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu(Str.t("Soubor"));
        file.setMnemonic('S');
        file.add(Ui.item(Str.t("Otevřít…"), KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), this::chooseFile));
        file.add(Ui.item(Str.t("Otevřít v novém okně…"), KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), () -> {
            MainWindow w = newWindow();
            w.chooseFile();
        }));
        file.add(Ui.item(Str.t("Nové okno"), KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), this::newWindow));
        file.add(recentMenu);
        file.add(Ui.item(Str.t("Znovu načíst"), KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), this::reload));
        file.add(Ui.item(Str.t("Zavřít okno"), KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK), () -> {
            saveWindowState();
            dispose();
        }));
        file.addSeparator();
        file.add(Ui.item(Str.t("Kopírovat vybrané řádky"), KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK), this::copySelection));
        file.add(Ui.item(Str.t("Exportovat zobrazené úkoly do CSV…"), KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK), exports::exportCsv));
        file.add(Ui.item(Str.t("Exportovat do Excelu (XLSX)…"), null, exports::exportXlsx));
        file.add(Ui.item(Str.t("Uložit Ganttův diagram jako PNG…"), KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), exports::exportPng));
        file.add(Ui.item(Str.t("Uložit jako PDF…"), KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), exports::exportPdf));
        file.add(Ui.item(Str.t("Tisk…"), KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK), exports::printChart));
        file.addSeparator();
        file.add(Ui.item(Str.t("Konec"), KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK), () -> {
            saveWindowState();
            for (MainWindow w : new ArrayList<>(WINDOWS)) {
                w.dispose();
            }
            System.exit(0);
        }));
        bar.add(file);

        JMenu view = new JMenu(Str.t("Zobrazit"));
        view.setMnemonic('Z');
        view.add(Ui.item(Str.t("Hledat"), KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), () -> {
            tabs.setSelectedIndex(0);
            search.requestFocusInWindow();
            search.selectAll();
        }));
        view.add(Ui.item(Str.t("Rozbalit vše"), KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), () -> {
            tree.expandAll();
            refresh();
        }));
        view.add(Ui.item(Str.t("Sbalit vše"), KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), () -> {
            tree.collapseAll();
            refresh();
        }));
        view.add(Ui.item(Str.t("Rozšířený filtr…"), KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), this::showFilterDialog));
        JMenu savedFilters = new JMenu(Str.t("Uložené filtry"));
        savedFilters.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                savedFilters.removeAll();
                List<TaskFilter> list = FilterDialog.savedFilters(prefs);
                if (list.isEmpty()) {
                    JMenuItem none = new JMenuItem(Str.t("(žádné – uložte filtr v dialogu Rozšířený filtr)"));
                    none.setEnabled(false);
                    savedFilters.add(none);
                }
                for (TaskFilter f : list) {
                    savedFilters.add(Ui.item(f.name() + "   —   " + f.describe(), null, () -> applyExtraFilter(f)));
                }
                savedFilters.addSeparator();
                savedFilters.add(Ui.item(Str.t("Zrušit rozšířený filtr"), null, () -> applyExtraFilter(null)));
            }

            @Override
            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            @Override
            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }
        });
        view.add(savedFilters);
        JMenu views = new JMenu(Str.t("Uložené pohledy"));
        views.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                views.removeAll();
                List<SavedView> list = SavedView.load(prefs);
                if (list.isEmpty()) {
                    JMenuItem none = new JMenuItem(Str.t("(žádné – uložte současné zobrazení níže)"));
                    none.setEnabled(false);
                    views.add(none);
                }
                for (SavedView v : list) {
                    views.add(Ui.item(v.name(), null, () -> applyView(v)));
                }
                views.addSeparator();
                views.add(Ui.item(Str.t("Uložit současný pohled…"), null, MainWindow.this::saveCurrentView));
                JMenu delete = new JMenu(Str.t("Smazat pohled"));
                delete.setEnabled(!list.isEmpty());
                for (SavedView v : list) {
                    delete.add(Ui.item(v.name(), null, () -> {
                        SavedView.remove(prefs, v.name());
                        status.setText(Str.t("Pohled smazán: ") + v.name());
                    }));
                }
                views.add(delete);
            }

            @Override
            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            @Override
            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }
        });
        view.add(views);
        view.add(Ui.item(Str.t("Sloupce tabulky…"), null, () -> showColumnChooser(0, Theme.HEADER)));
        view.add(Ui.item(Str.t("Zrušit řazení"), null, () -> {
            sortColumn = null;
            tree.setOrder(null);
            updateHeaderTitles();
            refresh();
        }));
        view.addSeparator();
        view.add(Ui.item(Str.t("Přiblížit"), KeyStroke.getKeyStroke(KeyEvent.VK_ADD, InputEvent.CTRL_DOWN_MASK), () -> gantt.zoomBy(1.25, -1)));
        view.add(Ui.item(Str.t("Oddálit"), KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, InputEvent.CTRL_DOWN_MASK), () -> gantt.zoomBy(0.8, -1)));
        view.add(Ui.item(Str.t("Celý projekt"), KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK), () -> zoomBox.setSelectedItem(GanttPanel.Zoom.FIT)));
        view.addSeparator();
        view.add(Ui.item(Str.t("Přejít na dnešek"), KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK), () -> gantt.scrollToDate(java.time.LocalDate.now())));
        view.add(Ui.item(Str.t("Přejít na vybraný úkol"), KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK), () -> gantt.scrollToRow(table.getSelectedRow())));
        view.addSeparator();
        view.add(Ui.check(Str.t("Zobrazit vazby"), linksBox));
        view.add(Ui.check(Str.t("Zobrazit základní plán"), baselineBox));
        view.add(Ui.check(Str.t("Zobrazit popisky zdrojů"), labelsBox));
        view.add(Ui.check(Str.t("Zobrazit celkovou rezervu"), slackBox));
        view.addSeparator();
        JMenu appearance = new JMenu(Str.t("Vzhled"));
        javax.swing.ButtonGroup modeGroup = new javax.swing.ButtonGroup();
        javax.swing.JRadioButtonMenuItem light = new javax.swing.JRadioButtonMenuItem(Str.t("Světlý režim"), !Theme.DARK);
        javax.swing.JRadioButtonMenuItem dark = new javax.swing.JRadioButtonMenuItem(Str.t("Tmavý režim"), Theme.DARK);
        modeGroup.add(light);
        modeGroup.add(dark);
        light.addActionListener(e -> App.applyAppearance(false, Theme.SCALE));
        dark.addActionListener(e -> App.applyAppearance(true, Theme.SCALE));
        appearance.add(light);
        appearance.add(dark);
        appearance.addSeparator();
        javax.swing.ButtonGroup sizeGroup = new javax.swing.ButtonGroup();
        String[] sizeNames = {Str.t("Menší písmo"), Str.t("Normální písmo"), Str.t("Větší písmo"), Str.t("Největší písmo")};
        float[] sizes = {0.9f, 1f, 1.15f, 1.3f};
        for (int i = 0; i < sizes.length; i++) {
            final float s = sizes[i];
            javax.swing.JRadioButtonMenuItem mi = new javax.swing.JRadioButtonMenuItem(sizeNames[i], Math.abs(Theme.SCALE - s) < 0.01f);
            mi.addActionListener(e -> App.applyAppearance(Theme.DARK, s));
            sizeGroup.add(mi);
            appearance.add(mi);
        }
        appearance.addSeparator();
        appearance.add(Ui.item(Str.t("Zvětšit písmo"), KeyStroke.getKeyStroke(KeyEvent.VK_ADD, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
                () -> App.applyAppearance(Theme.DARK, Math.min(2f, Theme.SCALE + 0.1f))));
        appearance.add(Ui.item(Str.t("Zmenšit písmo"), KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
                () -> App.applyAppearance(Theme.DARK, Math.max(0.7f, Theme.SCALE - 0.1f))));
        view.add(appearance);
        JMenu language = new JMenu(Str.t("Jazyk"));
        javax.swing.ButtonGroup langGroup = new javax.swing.ButtonGroup();
        javax.swing.JRadioButtonMenuItem cs = new javax.swing.JRadioButtonMenuItem(Str.t("Čeština"), !Str.isEnglish());
        javax.swing.JRadioButtonMenuItem en = new javax.swing.JRadioButtonMenuItem("English", Str.isEnglish());
        langGroup.add(cs);
        langGroup.add(en);
        cs.addActionListener(e -> changeLanguage("cs"));
        en.addActionListener(e -> changeLanguage("en"));
        language.add(cs);
        language.add(en);
        view.add(language);
        bar.add(view);

        JMenu tools = new JMenu(Str.t("Nástroje"));
        tools.setMnemonic('T');
        tools.add(Ui.item(Str.t("Kontrola kvality plánu…"), null, this::showScheduleCheck));
        tools.add(Ui.item(Str.t("Porovnat s jinou verzí…"), null, this::compareWithFile));
        tools.addSeparator();
        tools.add(Ui.item(Str.t("Exportovat do Excelu (XLSX)…"), null, exports::exportXlsx));
        bar.add(tools);

        windowMenu.setMnemonic('O');
        windowMenu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                windowMenu.removeAll();
                for (MainWindow w : WINDOWS) {
                    JCheckBoxMenuItem mi = new JCheckBoxMenuItem(w.currentPath == null ? Str.t("(bez souboru)") : w.currentPath.getFileName().toString(), w == MainWindow.this);
                    mi.addActionListener(ev -> {
                        w.setState(NORMAL);
                        w.toFront();
                        w.requestFocus();
                    });
                    windowMenu.add(mi);
                }
                windowMenu.addSeparator();
                windowMenu.add(Ui.item(Str.t("Nové okno"), null, MainWindow.this::newWindow));
            }

            @Override
            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            @Override
            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }
        });
        bar.add(windowMenu);

        JMenu help = new JMenu(Str.t("Nápověda"));
        help.setMnemonic('N');
        help.add(Ui.item(Str.t("Ovládání"), KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), () -> HelpDialogs.showHelp(this)));
        help.add(Ui.item(Str.t("Licence…"), null, () -> new LicenseDialog(this).setVisible(true)));
        help.add(Ui.item(Str.t("O aplikaci"), null, () -> HelpDialogs.showAbout(this)));
        bar.add(help);
        return bar;
    }

    private JComponent buildToolbar() {
        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(Theme.BG);
        top.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.GRID_STRONG));

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        titleRow.setOpaque(false);
        JLabel brand = new JLabel("<html>" + APP_NAME_HTML + "</html>", new ImageIcon(Icons.app(28)), SwingConstants.LEFT);
        brand.setFont(Theme.TITLE);
        brand.setForeground(Icons.DARK);
        brand.setIconTextGap(8);
        titleRow.add(brand);
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 24));
        sep.setForeground(Theme.GRID_STRONG);
        titleRow.add(sep);
        titleLabel.setFont(Theme.TITLE.deriveFont(Font.PLAIN));
        titleLabel.setForeground(Theme.INK);
        titleRow.add(titleLabel);
        top.add(titleRow, BorderLayout.NORTH);

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(0, 8, 6, 8));
        JButton open = new JButton(Str.t("Otevřít…"));
        open.setFont(Theme.BASE);
        open.addActionListener(e -> chooseFile());
        bar.add(open);
        bar.addSeparator();
        bar.add(Ui.label(Str.t("Hledat:")));
        search.setFont(Theme.BASE);
        search.setMinimumSize(new Dimension(140, search.getPreferredSize().height));
        search.setMaximumSize(new Dimension(260, Integer.MAX_VALUE));
        search.setToolTipText("Hledá v názvu, ID, WBS, zdrojích a poznámkách. Esc vymaže.");
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onSearch();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onSearch();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onSearch();
            }
        });
        Ui.bindKey(search, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear", () -> search.setText(""));
        bar.add(search);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(Ui.label(Str.t("Filtr:")));
        filterBox.setFont(Theme.BASE);
        filterBox.addActionListener(e -> {
            if (!suppressEvents) {
                tree.setFilter((TaskTree.Filter) filterBox.getSelectedItem());
                refresh();
            }
        });
        bar.add(filterBox);
        extraFilterButton.setFont(Theme.BASE);
        extraFilterButton.setToolTipText("Rozšířený filtr: zdroj, období, % dokončení, text ve sloupci; filtry lze uložit");
        extraFilterButton.addActionListener(e -> showFilterDialog());
        bar.add(extraFilterButton);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(Ui.label(Str.t("Osnova:")));
        levelBox.setFont(Theme.BASE);
        levelBox.setToolTipText("Rozbalí osnovu do zvolené úrovně");
        levelBox.addActionListener(e -> {
            if (suppressEvents || levelBox.getSelectedIndex() < 0) {
                return;
            }
            int idx = levelBox.getSelectedIndex();
            if (idx == 0) {
                tree.expandAll();
            } else {
                tree.expandToLevel(idx);
            }
            refresh();
        });
        bar.add(levelBox);
        bar.addSeparator();
        bar.add(Ui.label(Str.t("Měřítko:")));
        zoomBox.setFont(Theme.BASE);
        zoomBox.setSelectedItem(GanttPanel.Zoom.FIT);
        zoomBox.addActionListener(e -> {
            if (!suppressEvents && zoomBox.getSelectedItem() != null) {
                gantt.setZoom((GanttPanel.Zoom) zoomBox.getSelectedItem());
            }
        });
        bar.add(zoomBox);
        JButton minus = new JButton("−");
        minus.setToolTipText("Oddálit (Ctrl+kolečko myši)");
        minus.addActionListener(e -> gantt.zoomBy(0.8, -1));
        JButton plus = new JButton("+");
        plus.setToolTipText("Přiblížit (Ctrl+kolečko myši)");
        plus.addActionListener(e -> gantt.zoomBy(1.25, -1));
        bar.add(minus);
        bar.add(plus);
        bar.addSeparator();
        for (JCheckBox b : new JCheckBox[]{linksBox, baselineBox, labelsBox, slackBox}) {
            b.setFont(Theme.BASE);
            b.setOpaque(false);
        }
        // Popisky a Rezerva jen v nabídce Zobrazit, ať se lišta vejde i na menší okno
        bar.add(linksBox);
        bar.add(baselineBox);
        linksBox.addActionListener(e -> gantt.setShowLinks(linksBox.isSelected()));
        baselineBox.addActionListener(e -> gantt.setShowBaseline(baselineBox.isSelected()));
        baselineSel.setFont(Theme.BASE);
        baselineSel.setVisible(false);
        baselineSel.setToolTipText("Který uložený základní plán se má zobrazit (soubor jich má více)");
        baselineSel.addActionListener(e -> {
            if (!suppressEvents && baselineSel.getSelectedIndex() >= 0 && baselineSel.getSelectedIndex() < columns.baselines().size()) {
                gantt.setBaselineIndex(columns.baselines().get(baselineSel.getSelectedIndex()));
                if (!baselineBox.isSelected()) {
                    baselineBox.doClick();
                }
            }
        });
        bar.add(baselineSel);
        labelsBox.addActionListener(e -> gantt.setShowLabels(labelsBox.isSelected()));
        slackBox.addActionListener(e -> gantt.setShowSlack(slackBox.isSelected()));
        baselineBox.setToolTipText("Šedý pruh pod úkolem = uložený základní plán");
        slackBox.setToolTipText("Tenká čára za úkolem = celková časová rezerva");
        bar.addSeparator();
        JButton today = new JButton(Str.t("Dnes"));
        today.setToolTipText("Posune diagram na dnešní datum (Ctrl+T)");
        today.addActionListener(e -> gantt.scrollToDate(java.time.LocalDate.now()));
        bar.add(today);
        top.add(bar, BorderLayout.CENTER);
        return top;
    }

    private JComponent buildStatusBar() {
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.GRID_STRONG),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        status.setFont(Theme.BASE);
        stats.setFont(Theme.BASE);
        stats.setForeground(Theme.MUTED);
        stats.setHorizontalAlignment(SwingConstants.RIGHT);
        bottom.add(status, BorderLayout.CENTER);
        bottom.add(stats, BorderLayout.EAST);
        return bottom;
    }

    private void installDropHandler(JComponent... targets) {
        TransferHandler handler = new TransferHandler() {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean canImport(TransferSupport s) {
                return !loading && s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport s) {
                if (!canImport(s)) {
                    return false;
                }
                try {
                    List<File> files = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) {
                        // první soubor do tohoto okna, další do nových oken (max. 8 najednou)
                        open(files.get(0).toPath());
                        for (int i = 1; i < Math.min(files.size(), 8); i++) {
                            newWindow().open(files.get(i).toPath());
                        }
                        return true;
                    }
                } catch (Exception ex) {
                    showError(Str.t("Přetažený soubor se nepodařilo převzít."), ex);
                }
                return false;
            }
        };
        for (JComponent c : targets) {
            c.setTransferHandler(handler);
        }
    }

    // ---------------------------------------------------------------- sloupce a řazení

    /** Uložené nastavení sloupců: "klíč:šířka,klíč:šířka,…" (v pořadí zobrazení). */
    private List<String[]> loadColumnSetting() {
        List<String[]> out = parseColumnSetting(prefs.get("columns", ""));
        if (out.isEmpty()) {
            for (String k : Columns.DEFAULT_KEYS) {
                out.add(new String[]{k, ""});
            }
        }
        return out;
    }

    /** Rozebere zapsané nastavení sloupců „klíč:šířka,…“. */
    private static List<String[]> parseColumnSetting(String value) {
        List<String[]> out = new ArrayList<>();
        if (value != null && !value.isBlank()) {
            for (String part : value.split(",")) {
                int i = part.lastIndexOf(':');
                if (i > 0) {
                    out.add(new String[]{part.substring(0, i), part.substring(i + 1)});
                }
            }
        }
        return out;
    }

    /** Aktuální sloupce jako řetězec „klíč:šířka,…“. */
    private String columnSettingString() {
        StringBuilder sb = new StringBuilder();
        for (String[] kv : currentColumnSetting()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(kv[0]).append(':').append(kv[1]);
        }
        return sb.toString();
    }

    /** Nastaví zobrazené sloupce (klíč + volitelná šířka); klíče neznámé v tomto projektu se přeskočí, ale zůstávají uložené. */
    void applyColumns(List<String[]> setting) {
        StringBuilder store = new StringBuilder();
        List<Columns.Column> shown = new ArrayList<>();
        List<Integer> widths = new ArrayList<>();
        for (String[] kv : setting) {
            if (store.length() > 0) {
                store.append(',');
            }
            store.append(kv[0]).append(':').append(kv[1]);
            Columns.Column c = columns.byKey(kv[0]);
            if (c != null && !shown.contains(c)) {
                shown.add(c);
                int w = c.width;
                try {
                    if (!kv[1].isEmpty()) {
                        w = Math.max(30, Integer.parseInt(kv[1]));
                    }
                } catch (NumberFormatException ignored) {
                    w = c.width;
                }
                widths.add(w);
            }
        }
        prefs.put("columns", store.toString());
        model.setShown(shown);
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            TableColumn col = table.getColumnModel().getColumn(i);
            col.setPreferredWidth(widths.get(col.getModelIndex()));
            col.setCellRenderer(new TaskCellRenderer());
        }
        updateHeaderTitles();
    }

    /** Aktuální sloupce v pořadí zobrazení se šířkami (pro uložení). */
    private List<String[]> currentColumnSetting() {
        List<String[]> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            TableColumn col = table.getColumnModel().getColumn(i);
            Columns.Column c = model.columnAt(col.getModelIndex());
            if (c != null) {
                out.add(new String[]{c.key, String.valueOf(col.getWidth() > 0 ? col.getWidth() : col.getPreferredWidth())});
                seen.add(c.key);
            }
        }
        // sloupce uložené dříve, ale v tomto projektu neexistující (vlastní pole), zůstanou zapamatované
        for (String[] kv : loadColumnSetting()) {
            if (!seen.contains(kv[0]) && columns.byKey(kv[0]) == null) {
                out.add(kv);
            }
        }
        return out;
    }

    private void showColumnChooser(int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        List<Columns.Column> shown = model.shown();
        JMenu[] byCategory = new JMenu[Columns.Category.values().length];
        for (Columns.Category cat : Columns.Category.values()) {
            byCategory[cat.ordinal()] = new JMenu(cat.label);
        }
        for (Columns.Column c : columns.all()) {
            JCheckBoxMenuItem mi = new JCheckBoxMenuItem(c.title, shown.contains(c));
            mi.addActionListener(e -> toggleColumn(c, mi.isSelected()));
            byCategory[c.category.ordinal()].add(mi);
        }
        JLabel head = new JLabel(Str.t("  Sloupce tabulky"));
        head.setFont(Theme.BOLD);
        head.setForeground(Theme.MUTED);
        menu.add(head);
        menu.addSeparator();
        for (JMenu m : byCategory) {
            if (m.getItemCount() > 0) {
                menu.add(m);
            }
        }
        if (columns.custom().isEmpty()) {
            JMenuItem none = new JMenuItem(Str.t("Vlastní pole (tento soubor žádná nemá)"));
            none.setEnabled(false);
            menu.add(none);
        }
        menu.addSeparator();
        menu.add(Ui.item(Str.t("Výchozí sloupce"), null, () -> {
            List<String[]> def = new ArrayList<>();
            for (String k : Columns.DEFAULT_KEYS) {
                def.add(new String[]{k, ""});
            }
            applyColumns(def);
        }));
        menu.add(Ui.item(Str.t("Přizpůsobit šířky obsahu"), null, () -> {
            setAutoFitWidths(true);
            autoSizeColumns();
        }));
        JCheckBoxMenuItem auto = new JCheckBoxMenuItem(Str.t("Přizpůsobovat šířky po otevření souboru"), autoFitWidths);
        auto.addActionListener(e -> {
            setAutoFitWidths(auto.isSelected());
            if (auto.isSelected()) {
                autoSizeColumns();
            }
        });
        menu.add(auto);
        menu.show(table.getTableHeader(), x, y);
    }

    private void toggleColumn(Columns.Column c, boolean show) {
        List<String[]> setting = currentColumnSetting();
        setting.removeIf(kv -> kv[0].equals(c.key));
        if (show) {
            setting.add(new String[]{c.key, ""});
        }
        applyColumns(setting);
    }

    private void setAutoFitWidths(boolean on) {
        autoFitWidths = on;
        prefs.putBoolean("autoFitColumns", on);
    }

    private void autoSizeColumns() {
        java.awt.FontMetrics fm = table.getFontMetrics(Theme.BOLD);
        for (int v = 0; v < table.getColumnModel().getColumnCount(); v++) {
            TableColumn col = table.getColumnModel().getColumn(v);
            Columns.Column c = model.columnAt(col.getModelIndex());
            int w = fm.stringWidth(String.valueOf(col.getHeaderValue())) + 20;
            int rows = Math.min(tree.visible().size(), 500);
            for (int r = 0; r < rows; r++) {
                TaskTree.Row row = tree.visible().get(r);
                int indent = c == Columns.NAME ? 20 + row.depth * 16 : 12;
                w = Math.max(w, fm.stringWidth(columns.text(c, row)) + indent);
            }
            col.setPreferredWidth(Math.min(600, w));
        }
    }

    void sortBy(Columns.Column c) {
        if (c == null) {
            return;
        }
        if (sortColumn != c) {
            sortColumn = c;
            sortAscending = true;
        } else if (sortAscending) {
            sortAscending = false;
        } else {
            sortColumn = null;
        }
        tree.setOrder(sortColumn == null ? null : columns.comparator(sortColumn, sortAscending));
        updateHeaderTitles();
        refresh();
        status.setText(sortColumn == null ? Str.t("Řazení zrušeno – pořadí ze souboru.")
                : Str.t("Seřazeno podle „") + sortColumn.title + Str.t("“ ") + (sortAscending ? Str.t("vzestupně") : Str.t("sestupně")) + Str.t(" v rámci každé úrovně osnovy."));
    }

    private void updateHeaderTitles() {
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            TableColumn col = table.getColumnModel().getColumn(i);
            Columns.Column c = model.columnAt(col.getModelIndex());
            if (c != null) {
                col.setHeaderValue(c == sortColumn ? c.title + (sortAscending ? " ▲" : " ▼") : c.title);
            }
        }
        table.getTableHeader().repaint();
    }

    // ---------------------------------------------------------------- jazyk

    private void changeLanguage(String lang) {
        if (lang.equals(Str.lang())) {
            return;
        }
        prefs.put("lang", lang);
        int r = JOptionPane.showConfirmDialog(this, Str.t("Změna jazyka se projeví po restartu programu. Restartovat nyní?"),
                Str.t("Jazyk"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (r == JOptionPane.YES_OPTION && !App.restartApplication()) {
            JOptionPane.showMessageDialog(this, Str.t("Restart se nezdařil – ukončete a spusťte program ručně."), Str.t("Jazyk"), JOptionPane.WARNING_MESSAGE);
        }
    }

    // ---------------------------------------------------------------- rozšířený filtr

    private void showFilterDialog() {
        if (project == null) {
            JOptionPane.showMessageDialog(this, Str.t("Nejprve otevřete projekt."), Str.t("Rozšířený filtr"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        FilterDialog d = new FilterDialog(this, prefs, project, columns, extraFilter);
        d.setVisible(true);
        if (d.result() != null) {
            applyExtraFilter(d.result());
        }
    }

    void applyExtraFilter(TaskFilter f) {
        extraFilter = f == null || f.isEmpty() ? null : f;
        tree.setExtra(extraFilter == null ? null : extraFilter.predicate(columns));
        extraFilterButton.setText(extraFilter == null ? Str.t("Filtr…") : Str.t("Filtr ●"));
        extraFilterButton.setForeground(extraFilter == null ? Theme.INK : Theme.HIGHLIGHT);
        refresh();
        status.setText(extraFilter == null ? Str.t("Rozšířený filtr zrušen.") : Str.t("Rozšířený filtr: ") + extraFilter.describe() + "   (" + tree.matchCount() + Str.t(" shod)"));
    }

    /** UNC cesta (\\server\sdílení) nebo URL – přístup k ní znamená síťové spojení. */
    static boolean isNetworkPath(Path p) {
        String s = p.toString();
        return s.startsWith("\\\\") || s.startsWith("//") || s.matches("(?i)^[a-z]+://.*");
    }

    /** Otevře vložený podprojekt v novém okně; cesta se hledá absolutně i relativně k otevřenému souboru. */
    void openSubproject(String ref) {
        if (ref == null || ref.isBlank()) {
            return;
        }
        List<Path> candidates = new ArrayList<>();
        try {
            String clean = ref.strip();
            if (clean.startsWith("<>\\")) {
                clean = clean.substring(3);
            }
            Path p = Path.of(clean);
            candidates.add(p);
            if (currentPath != null && currentPath.getParent() != null) {
                candidates.add(currentPath.getParent().resolve(clean));
                candidates.add(currentPath.getParent().resolve(p.getFileName()));
            }
        } catch (RuntimeException ex) {
            showError(Str.t("Cesta k podprojektu je neplatná: ") + ref, null);
            return;
        }
        for (Path c : candidates) {
            // síťová cesta ze souboru: samotný přístup by mohl odeslat přihlašovací údaje Windows cizímu serveru – proto dotaz
            if (isNetworkPath(c) && JOptionPane.showConfirmDialog(this,
                    Str.t("Odkaz na podprojekt vede na síťovou cestu:") + "\n" + c + "\n\n"
                            + Str.t("Otevřít ji? Přistupujte jen k serverům, kterým důvěřujete."),
                    Str.t("Podprojekt"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
                return;
            }
            if (Files.isRegularFile(c)) {
                newWindow().open(c);
                return;
            }
        }
        JOptionPane.showMessageDialog(this, "Soubor podprojektu se nepodařilo najít:\n" + ref
                + "\n\nHledáno i ve složce otevřeného souboru. Otevřete ho ručně přes Soubor → Otevřít.", Str.t("Podprojekt"), JOptionPane.WARNING_MESSAGE);
    }

    // ---------------------------------------------------------------- akce

    private java.util.Optional<TaskTree.Row> selectedRow() {
        int r = table.getSelectedRow();
        return r >= 0 && r < tree.visible().size() ? java.util.Optional.of(tree.visible().get(r)) : java.util.Optional.empty();
    }

    void toggle(TaskTree.Row row) {
        if (!row.isSummary()) {
            return;
        }
        tree.toggle(row);
        refresh();
    }

    void setCollapsed(TaskTree.Row row, boolean collapsed) {
        if (!row.isSummary()) {
            return;
        }
        tree.setCollapsed(row, collapsed);
        refresh();
    }

    /** Sloupce v pořadí, v jakém jsou zobrazené (pro kopírování a export). */
    private List<Columns.Column> viewColumns() {
        List<Columns.Column> out = new ArrayList<>();
        for (int v = 0; v < table.getColumnModel().getColumnCount(); v++) {
            Columns.Column c = model.columnAt(table.convertColumnIndexToModel(v));
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    /** Vybrané řádky; když není vybráno nic, všechny zobrazené. */
    private List<TaskTree.Row> rowsToCopy() {
        List<TaskTree.Row> rows = new ArrayList<>();
        int[] sel = table.getSelectedRows();
        if (sel.length == 0) {
            rows.addAll(tree.visible());
            return rows;
        }
        for (int i : sel) {
            if (i >= 0 && i < tree.visible().size()) {
                rows.add(tree.visible().get(i));
            }
        }
        return rows;
    }

    /** Zkopíruje vybrané řádky do schránky (text oddělený tabulátory – vloží se rovnou do Excelu). */
    void copySelection() {
        String text = selectionAsText();
        if (text.isEmpty()) {
            return;
        }
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(text), null);
            status.setText(rowsToCopy().size() + Str.t(" řádků zkopírováno do schránky."));
        } catch (RuntimeException ex) {
            status.setText(Str.t("Schránku se nepodařilo otevřít – zkuste to znovu."));
        }
    }

    /** Vybrané řádky jako text oddělený tabulátory (hlavička + data). */
    String selectionAsText() {
        List<TaskTree.Row> rows = rowsToCopy();
        if (rows.isEmpty()) {
            return "";
        }
        List<Columns.Column> cols = viewColumns();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            sb.append(i > 0 ? "\t" : "").append(cols.get(i).title);
        }
        sb.append(System.lineSeparator());
        for (TaskTree.Row row : rows) {
            for (int i = 0; i < cols.size(); i++) {
                Columns.Column c = cols.get(i);
                String text = columns.text(c, row);
                if (c == Columns.NAME) {
                    text = "    ".repeat(Math.min(row.depth, 8)) + text;
                }
                sb.append(i > 0 ? "\t" : "").append(text.replace('\t', ' ').replace('\n', ' '));
            }
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    /** Text stavového řádku po načtení souboru. */
    private String loadedStatus() {
        return currentPath == null ? "" : "Načteno: " + currentPath.toAbsolutePath() + Str.t("   (pouze pro čtení – soubor se nemění)");
    }

    /** Při výběru více řádků ukáže ve stavovém řádku součty (souhrnné úkoly se nesčítají). */
    private void updateSelectionStatus() {
        int[] sel = table.getSelectedRows();
        if (sel.length < 2) {
            if (currentPath != null) {
                status.setText(loadedStatus());
            }
            return;
        }
        org.mpxj.ProjectProperties props = project == null ? null : project.getProjectProperties();
        double hours = 0;
        double cost = 0;
        int tasks = 0;
        java.time.LocalDateTime from = null;
        java.time.LocalDateTime to = null;
        for (int i : sel) {
            if (i < 0 || i >= tree.visible().size()) {
                continue;
            }
            TaskTree.Row row = tree.visible().get(i);
            Task t = row.task;
            if (t.getStart() != null && (from == null || t.getStart().isBefore(from))) {
                from = t.getStart();
            }
            if (t.getFinish() != null && (to == null || t.getFinish().isAfter(to))) {
                to = t.getFinish();
            }
            if (row.isSummary()) {
                continue;
            }
            tasks++;
            if (t.getWork() != null && props != null) {
                hours += t.getWork().convertUnits(org.mpxj.TimeUnit.HOURS, props).getDuration();
            }
            if (t.getCost() != null) {
                cost += t.getCost().doubleValue();
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append(Str.t("Vybráno ")).append(sel.length).append(Str.t(" řádků")).append(" (").append(tasks).append(Str.t(" úkolů bez souhrnů)"));
        if (hours > 0) {
            sb.append(Str.t("  •  práce ")).append(Fmt.number(hours)).append(Str.t(" h"));
        }
        if (cost > 0) {
            sb.append(Str.t("  •  náklady ")).append(Fmt.money(cost, props == null ? "" : Fmt.text(props.getCurrencySymbol())));
        }
        if (from != null && to != null) {
            sb.append("  •  ").append(Fmt.date(from)).append(" – ").append(Fmt.date(to));
        }
        status.setText(sb.toString());
    }

    private void onSearch() {
        if (suppressEvents) {
            return;
        }
        tree.setQuery(search.getText());
        refresh();
    }

    /** Přepočítá viditelné řádky a překreslí tabulku i diagram; zachová výběr. */
    void refresh() {
        TaskTree.Row selected = selectedRow().orElse(null);
        model.fireTableDataChanged();
        if (selected != null) {
            int idx = tree.indexOfVisible(selected);
            if (idx >= 0) {
                table.setRowSelectionInterval(idx, idx);
            }
        }
        gantt.rowsChanged();
        updateStats();
    }

    void goToTask(Task t) {
        TaskTree.Row row = tree.find(t);
        if (row == null) {
            return;
        }
        if (tree.isFiltering() && tree.indexOfVisible(row) < 0) {
            suppressEvents = true;
            search.setText("");
            filterBox.setSelectedItem(TaskTree.Filter.ALL);
            suppressEvents = false;
            tree.setQuery("");
            tree.setFilter(TaskTree.Filter.ALL);
            if (extraFilter != null) {
                applyExtraFilter(null);
            }
        }
        tree.reveal(row);
        refresh();
        int idx = tree.indexOfVisible(row);
        if (idx >= 0) {
            tabs.setSelectedIndex(0);
            table.setRowSelectionInterval(idx, idx);
            table.scrollRectToVisible(table.getCellRect(idx, 0, true));
            gantt.scrollToRow(idx);
            table.requestFocusInWindow();
        }
    }

    void updateStats() {
        if (tree.size() == 0) {
            stats.setText("Žádný projekt");
            return;
        }
        int milestones = 0;
        int summaries = 0;
        int critical = 0;
        for (TaskTree.Row r : tree.all()) {
            if (r.isSummary()) {
                summaries++;
            } else if (r.task.getMilestone()) {
                milestones++;
            }
            if (r.task.getCritical()) {
                critical++;
            }
        }
        String s = tree.size() + Str.t(" úkolů  •  ") + summaries + Str.t(" souhrnných  •  ") + milestones + Str.t(" milníků  •  ") + critical + Str.t(" kritických");
        if (tree.isFiltering()) {
            s += Str.t("  •  filtr: ") + tree.matchCount() + Str.t(" shod, zobrazeno ") + tree.visible().size() + Str.t(" řádků");
        } else {
            s += Str.t("  •  zobrazeno ") + tree.visible().size();
        }
        stats.setText(s);
    }

    private void updateBaselineChoices() {
        suppressEvents = true;
        try {
            baselineSel.removeAllItems();
            for (int i : columns.baselines()) {
                baselineSel.addItem(Columns.baselineName(i));
            }
            baselineSel.setVisible(columns.baselines().size() > 1);
            if (baselineSel.getItemCount() > 0) {
                baselineSel.setSelectedIndex(0);
            }
            gantt.setBaselineIndex(columns.baselines().isEmpty() ? 0 : columns.baselines().get(0));
        } finally {
            suppressEvents = false;
        }
    }

    private void updateLevelBox() {
        suppressEvents = true;
        try {
            levelBox.removeAllItems();
            levelBox.addItem(Str.t("Všechny úrovně"));
            int max = Math.max(1, tree.maxDepthInProject());
            for (int i = 1; i <= max; i++) {
                levelBox.addItem(Str.t("Úroveň ") + i);
            }
            levelBox.setSelectedIndex(0);
        } finally {
            suppressEvents = false;
        }
    }

    // ---------------------------------------------------------------- otevírání

    /** Otevře další okno (kaskádově posunuté) – každé okno zobrazuje jeden soubor. */
    MainWindow newWindow() {
        MainWindow w = new MainWindow();
        w.setLocation(getX() + 32, getY() + 32);
        w.setSize(getSize());
        w.setVisible(true);
        return w;
    }

    void chooseFile() {
        if (loading) {
            return;
        }
        File f = FileDialogs.open(this, Str.t("Otevřít projekt Microsoft Project"), prefs.get("lastDir", System.getProperty("user.home")), List.of(
                new FileDialogs.Filter(Str.t("Soubory Microsoft Project (*.mpp, *.mpt, *.mpx, *.xml, *.mpd)"), ProjectLoader.EXTENSIONS),
                new FileDialogs.Filter("Project (*.mpp, *.mpt)", "mpp", "mpt"),
                new FileDialogs.Filter("Project XML (*.xml)", "xml"),
                new FileDialogs.Filter("Project Exchange (*.mpx)", "mpx"),
                new FileDialogs.Filter("Project Database (*.mpd)", "mpd")));
        if (f != null) {
            if (f.getParent() != null) {
                prefs.put("lastDir", f.getParent());
            }
            open(f.toPath());
        }
    }

    void reload() {
        if (currentPath != null) {
            open(currentPath);
        }
    }

    /** Vybere starší verzi téhož plánu a ukáže, co se od ní změnilo. */
    void compareWithFile() {
        if (project == null || currentPath == null) {
            JOptionPane.showMessageDialog(this, Str.t("Nejprve otevřete projekt."), Str.t("Porovnání verzí"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        File f = FileDialogs.open(this, Str.t("Vyberte starší verzi harmonogramu"), prefs.get("lastDir", System.getProperty("user.home")), List.of(
                new FileDialogs.Filter(Str.t("Soubory Microsoft Project (*.mpp, *.mpt, *.mpx, *.xml, *.mpd)"), ProjectLoader.EXTENSIONS),
                new FileDialogs.Filter("Project (*.mpp, *.mpt)", "mpp", "mpt"),
                new FileDialogs.Filter("Project XML (*.xml)", "xml"),
                new FileDialogs.Filter("Project Exchange (*.mpx)", "mpx"),
                new FileDialogs.Filter("Project Database (*.mpd)", "mpd")));
        if (f == null) {
            return;
        }
        if (f.getParent() != null) {
            prefs.put("lastDir", f.getParent());
        }
        Path older = f.toPath();
        if (older.equals(currentPath)) {
            JOptionPane.showMessageDialog(this, Str.t("Vybrali jste tentýž soubor, který je otevřený."), Str.t("Porovnání verzí"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        status.setText(Str.t("Porovnávám s ") + older.getFileName() + "…");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<List<ScheduleDiff.Row>, Void>() {
            @Override
            protected List<ScheduleDiff.Row> doInBackground() throws Exception {
                ProjectFile old = ProjectLoader.load(older, null);
                return ScheduleDiff.compare(old, project);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    List<ScheduleDiff.Row> rows = get();
                    new CompareDialog(MainWindow.this, older, rows).setVisible(true);
                    status.setText(Str.t("Porovnání s ") + older.getFileName() + ": " + ScheduleDiff.summary(rows));
                } catch (java.util.concurrent.ExecutionException ex) {
                    showError(Str.t("Starší verzi se nepodařilo načíst."), ex.getCause());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    // ---------------------------------------------------------------- nástroje

    /** Rozbor kvality plánu (chybějící vazby, tvrdá omezení, rezervy, prodlevy…). */
    void showScheduleCheck() {
        if (project == null) {
            JOptionPane.showMessageDialog(this, Str.t("Nejprve otevřete projekt."), Str.t("Kontrola kvality plánu"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<ScheduleCheck.Finding> findings = ScheduleCheck.run(project);
        if (findings.isEmpty()) {
            JOptionPane.showMessageDialog(this, Str.t("Projekt neobsahuje žádné úkoly ke kontrole."), Str.t("Kontrola kvality plánu"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        new ScheduleCheckDialog(this, findings).setVisible(true);
    }

    /** Vybere v tabulce zadané úkoly; kvůli viditelnosti zruší hledání i rychlý filtr a rozbalí osnovu. */
    void selectTasks(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        suppressEvents = true;
        try {
            search.setText("");
            tree.setQuery("");
            filterBox.setSelectedItem(TaskTree.Filter.ALL);
            tree.setFilter(TaskTree.Filter.ALL);
        } finally {
            suppressEvents = false;
        }
        tree.expandAll();
        refresh();
        java.util.Set<Task> wanted = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        wanted.addAll(tasks);
        table.clearSelection();
        int first = -1;
        int found = 0;
        List<TaskTree.Row> visible = tree.visible();
        for (int i = 0; i < visible.size(); i++) {
            if (wanted.contains(visible.get(i).task)) {
                table.addRowSelectionInterval(i, i);
                found++;
                if (first < 0) {
                    first = i;
                }
            }
        }
        if (first >= 0) {
            tabs.setSelectedIndex(0);
            table.scrollRectToVisible(table.getCellRect(first, 0, true));
            gantt.scrollToRow(first);
        }
        if (found < tasks.size()) {
            status.setText(Str.t("Vybráno ") + found + Str.t(" z ") + tasks.size()
                    + Str.t(" úkolů – ostatní skrývá rozšířený filtr."));
        }
    }

    // ---------------------------------------------------------------- uložené pohledy

    /** Zeptá se na název a uloží současné sloupce, filtr, úroveň osnovy a měřítko. */
    void saveCurrentView() {
        String suggestion = Str.t("Pohled ") + (SavedView.load(prefs).size() + 1);
        String name = JOptionPane.showInputDialog(this, Str.t("Název pohledu (sloupce, filtr, úroveň osnovy a měřítko):"),
                suggestion);
        if (name == null || name.isBlank()) {
            return;
        }
        GanttPanel.Zoom zoom = zoomBox.getSelectedIndex() < 0 ? null : (GanttPanel.Zoom) zoomBox.getSelectedItem();
        SavedView v = new SavedView(name.trim(), columnSettingString(),
                filterBox.getSelectedItem() == null ? TaskTree.Filter.ALL.name() : ((TaskTree.Filter) filterBox.getSelectedItem()).name(),
                extraFilter == null ? "" : extraFilter.serialize(),
                Math.max(0, levelBox.getSelectedIndex()),
                zoom == null ? "" : zoom.name());
        SavedView.save(prefs, v);
        status.setText(Str.t("Pohled uložen: ") + v.name());
    }

    /** Nastaví zobrazení podle uloženého pohledu. */
    void applyView(SavedView v) {
        suppressEvents = true;
        try {
            List<String[]> cols = parseColumnSetting(v.columns());
            if (!cols.isEmpty()) {
                applyColumns(cols);
                setAutoFitWidths(false);
            }
            try {
                TaskTree.Filter f = TaskTree.Filter.valueOf(v.filter());
                filterBox.setSelectedItem(f);
                tree.setFilter(f);
            } catch (IllegalArgumentException ignored) {
                // pohled z novější verze s neznámým filtrem – zůstane stávající
            }
            TaskFilter extra = v.extra().isBlank() ? null : TaskFilter.deserialize(v.extra());
            extraFilter = extra == null || extra.isEmpty() ? null : extra;
            tree.setExtra(extraFilter == null ? null : extraFilter.predicate(columns));
            extraFilterButton.setText(extraFilter == null ? Str.t("Filtr…") : Str.t("Filtr ●"));
            extraFilterButton.setForeground(extraFilter == null ? Theme.INK : Theme.HIGHLIGHT);
            if (v.level() >= 0 && v.level() < levelBox.getItemCount()) {
                levelBox.setSelectedIndex(v.level());
                tree.setMaxDepth(v.level());
            }
            if (!v.zoom().isBlank()) {
                try {
                    GanttPanel.Zoom z = GanttPanel.Zoom.valueOf(v.zoom());
                    zoomBox.setSelectedItem(z);
                    gantt.setZoom(z);
                } catch (IllegalArgumentException ignored) {
                    // neznámé měřítko – zůstane stávající
                }
            }
        } finally {
            suppressEvents = false;
        }
        refresh();
        updateStats();
        status.setText(Str.t("Pohled: ") + v.name());
    }

    /** Čas poslední změny otevřeného souboru (0 = neznámý). */
    private long fileStamp(Path path) {
        try {
            return path == null ? 0 : Files.getLastModifiedTime(path).toMillis();
        } catch (java.io.IOException | RuntimeException ex) {
            return 0;
        }
    }

    /**
     * Po návratu do okna zjistí, jestli někdo soubor mezitím přepsal (typicky plán
     * na síťovém disku), a nabídne znovunačtení. Ptá se jen jednou na každou změnu.
     */
    void checkFileChanged() {
        if (loading || currentPath == null || loadedStamp == 0) {
            return;
        }
        long now = fileStamp(currentPath);
        if (now == 0 || now == loadedStamp) {
            return;
        }
        loadedStamp = now;
        int answer = JOptionPane.showConfirmDialog(this,
                Str.t("Soubor se od otevření změnil:") + "\n" + currentPath.getFileName() + "\n\n"
                        + Str.t("Načíst znovu? Neuložené nastavení zobrazení (filtr, sbalení) zůstane."),
                Str.t("Soubor se změnil"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            reload();
        } else {
            status.setText(Str.t("Soubor na disku se změnil – zobrazení zůstává podle dřívějšího načtení (F5 načte znovu)."));
        }
    }

    public void open(Path path) {
        load(path, null);
    }

    private void load(Path path, String password) {
        if (loading) {
            return;
        }
        if (!Files.isRegularFile(path)) {
            showError("Soubor neexistuje nebo není přístupný:\n" + path, null);
            recent.remove(path);
            updateRecentMenu();
            return;
        }
        loading = true;
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        status.setText("Načítám " + path.getFileName() + "…");
        new SwingWorker<ProjectFile, Void>() {
            @Override
            protected ProjectFile doInBackground() throws Exception {
                return ProjectLoader.load(path, password);
            }

            @Override
            protected void done() {
                loading = false;
                setCursor(Cursor.getDefaultCursor());
                try {
                    apply(path, get());
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    if (cause instanceof ProjectLoader.PasswordRequiredException) {
                        askPassword(path, password != null);
                    } else if (ErrorLog.isOutOfMemory(cause)) {
                        status.setText(Str.t("Soubor se nepodařilo načíst."));
                        showError(Str.t("Soubor je příliš velký pro dostupnou paměť. Zavřete ostatní okna programu a zkuste to znovu."), null);
                    } else {
                        status.setText("Soubor se nepodařilo načíst" + (project == null ? "." : Str.t("; předchozí projekt zůstává otevřený.")));
                        showError("Soubor se nepodařilo otevřít:\n" + path.getFileName()
                                + "\n\nMůže být poškozený, v nepodporované variantě nebo to není soubor Microsoft Project.", cause);
                    }
                } catch (Exception ex) {
                    status.setText("Soubor se nepodařilo načíst.");
                    showError(Str.t("Soubor se nepodařilo otevřít."), ex);
                }
            }
        }.execute();
    }

    private void askPassword(Path path, boolean retry) {
        status.setText("Soubor je chráněn heslem pro čtení.");
        JPasswordField pf = new JPasswordField(20);
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.add(new JLabel((retry ? Str.t("Heslo nebylo přijato. ") : "") + Str.t("Soubor ") + path.getFileName() + Str.t(" je chráněn heslem pro čtení.")), BorderLayout.NORTH);
        p.add(pf, BorderLayout.CENTER);
        SwingUtilities.invokeLater(pf::requestFocusInWindow);
        int result = JOptionPane.showConfirmDialog(this, p, Str.t("Heslo pro čtení"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            char[] chars = pf.getPassword();
            String pw = new String(chars);
            java.util.Arrays.fill(chars, '\0');
            load(path, pw);
        } else {
            status.setText("Otevření souboru bylo zrušeno.");
        }
    }

    void apply(Path path, ProjectFile file) {
        project = file;
        currentPath = path;
        suppressEvents = true;
        try {
            search.setText("");
            filterBox.setSelectedItem(TaskTree.Filter.ALL);
        } finally {
            suppressEvents = false;
        }
        tree.setToday(java.time.LocalDate.now());
        tree.setQuery("");
        tree.setFilter(TaskTree.Filter.ALL);
        tree.setMaxDepth(0);
        sortColumn = null;
        tree.setOrder(null);
        extraFilter = null;
        tree.setExtra(null);
        extraFilterButton.setText("Filtr…");
        extraFilterButton.setForeground(Theme.INK);
        tree.load(file);
        columns.setProject(file);
        table.clearSelection();
        applyColumns(currentColumnSetting());
        if (autoFitWidths) {
            autoSizeColumns();
        }
        updateBaselineChoices();
        gantt.setProject(file);
        details.setProject(file);
        details.show(null);
        resources.setProject(file);
        usage.setProject(file);
        calendars.setProject(file);
        timeline.setProject(file, tree);
        info.setProject(file, path, tree);
        updateLevelBox();
        String name = path.getFileName().toString();
        titleLabel.setText(name);
        setTitle(name + " — " + APP_NAME);
        recent.add(path);
        updateRecentMenu();
        updateStats();
        ((java.awt.CardLayout) center.getLayout()).show(center, "main");
        loadedStamp = fileStamp(path);
        status.setText(loadedStatus());
        if (tree.size() == 0) {
            status.setText("Soubor byl načten, ale neobsahuje žádné úkoly: " + path.getFileName());
        }
        SwingUtilities.invokeLater(() -> {
            if (tree.size() > 0) {
                table.setRowSelectionInterval(0, 0);
            }
            gantt.layoutChanged();
        });
    }

    void showError(String message, Throwable ex) {
        String detail = "";
        if (ex != null) {
            Throwable root = ProjectLoader.rootCause(ex);
            detail = "\n\n" + Str.t("Technický detail: ") + root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
            ErrorLog.write(ErrorLog.describe("dialog", ex));
        }
        JOptionPane.showMessageDialog(this, message + detail, Str.t("Chyba"), JOptionPane.ERROR_MESSAGE);
    }

    // ---------------------------------------------------------------- export (viz Exports)

    /** Tabulka + diagram v plné velikosti jako jeden obrázek. */
    BufferedImage renderImage() {
        return renderImage(1.0);
    }

    /** Tabulka + diagram jako jeden obrázek v zadaném měřítku (2 = dvojnásobné rozlišení). */
    BufferedImage renderImage(double scale) {
        if (!Theme.DARK) {
            return renderImageNow(scale);
        }
        // export a tisk vždy ve světlých barvách (tmavé pozadí se na papír nehodí)
        Theme.init(false, Theme.SCALE);
        try {
            return renderImageNow(scale);
        } finally {
            Theme.init(true, Theme.SCALE);
        }
    }

    private BufferedImage renderImageNow(double scale) {
        BufferedImage chartImg = gantt.renderImage(scale);
        int tableWidth = table.getPreferredSize().width;
        int rowsHeight = Math.max(1, tree.visible().size() * Theme.ROW);
        int h = Theme.HEADER + rowsHeight;
        int tw = (int) Math.ceil(tableWidth * scale);
        BufferedImage img = new BufferedImage(tw + chartImg.getWidth(), Math.max((int) Math.ceil(h * scale), chartImg.getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Theme.BG);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Rectangle headerBounds = table.getTableHeader().getBounds();
        Rectangle tableBounds = table.getBounds();
        table.getTableHeader().setBounds(0, 0, tableWidth, Theme.HEADER);
        table.setBounds(0, 0, tableWidth, rowsHeight);
        g.scale(scale, scale);
        g.setClip(0, 0, tableWidth, Theme.HEADER);
        table.getTableHeader().paint(g);
        g.translate(0, Theme.HEADER);
        g.setClip(0, 0, tableWidth, rowsHeight);
        table.paint(g);
        g.translate(0, -Theme.HEADER);
        table.getTableHeader().setBounds(headerBounds);
        table.setBounds(tableBounds);
        g.setClip(null);
        g.scale(1 / scale, 1 / scale);
        g.drawImage(chartImg, tw, 0, null);
        g.setColor(Theme.GRID_STRONG);
        g.drawLine(tw, 0, tw, img.getHeight());
        g.dispose();
        return img;
    }

    // ---------------------------------------------------------------- nedávné soubory, stav okna

    private void updateRecentMenu() {
        recentMenu.removeAll();
        List<Path> list = recent.list();
        start.setRecent(list);
        for (MainWindow w : WINDOWS) {
            if (w != this) {
                w.start.setRecent(list);
            }
        }
        if (list.isEmpty()) {
            JMenuItem none = new JMenuItem(Str.t("(žádné)"));
            none.setEnabled(false);
            recentMenu.add(none);
            return;
        }
        int n = 1;
        for (Path p : list) {
            JMenuItem mi = new JMenuItem(n + "  " + p.getFileName() + "   —   " + p.getParent());
            if (n <= 9) {
                mi.setMnemonic('0' + n);
            }
            mi.setToolTipText("Shift+kliknutí otevře v novém okně");
            mi.addActionListener(e -> {
                if ((e.getModifiers() & ActionEvent.SHIFT_MASK) != 0 && project != null) {
                    newWindow().open(p);
                } else {
                    open(p);
                }
            });
            recentMenu.add(mi);
            n++;
        }
        recentMenu.addSeparator();
        JMenuItem clear = new JMenuItem(Str.t("Vymazat seznam"));
        clear.addActionListener(e -> {
            recent.clear();
            updateRecentMenu();
        });
        recentMenu.add(clear);
    }

    private void restoreWindowState() {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int w = prefs.getInt("winW", 0);
        int h = prefs.getInt("winH", 0);
        int x = prefs.getInt("winX", Integer.MIN_VALUE);
        int y = prefs.getInt("winY", Integer.MIN_VALUE);
        if (w >= 800 && h >= 500 && x != Integer.MIN_VALUE && screen.contains(x + 50, y + 50)) {
            setBounds(x, y, Math.min(w, screen.width), Math.min(h, screen.height));
        } else {
            setSize(Math.min(1440, screen.width - 40), Math.min(900, screen.height - 40));
            setLocationRelativeTo(null);
        }
        if (prefs.getBoolean("winMax", false)) {
            setExtendedState(MAXIMIZED_BOTH);
        }
        int divider = prefs.getInt("divider", -1);
        if (divider > 100) {
            mainSplit.setDividerLocation(divider);
        }
    }

    void saveWindowState() {
        boolean max = (getExtendedState() & MAXIMIZED_BOTH) == MAXIMIZED_BOTH;
        prefs.putBoolean("winMax", max);
        if (!max) {
            prefs.putInt("winW", getWidth());
            prefs.putInt("winH", getHeight());
            prefs.putInt("winX", getX());
            prefs.putInt("winY", getY());
        }
        prefs.putInt("divider", mainSplit.getDividerLocation());
        prefs.put("columns", columnSettingString());
        try {
            prefs.flush();
        } catch (java.util.prefs.BackingStoreException | RuntimeException ignored) {
            // nastavení se uloží při dalším spuštění, nebo vůbec – nepodstatné
        }
    }

    // ---------------------------------------------------------------- renderer

    private final class TaskCellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        private int glyph; // 0 nic, 1 rozbaleno, 2 sbaleno, 3 milník
        private int glyphX;

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            if (glyph == 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getForeground());
            int cy = getHeight() / 2;
            int x = glyphX;
            if (glyph == 1) {
                g2.fillPolygon(new int[]{x, x + 9, x + 4}, new int[]{cy - 2, cy - 2, cy + 3}, 3);
            } else if (glyph == 2) {
                g2.fillPolygon(new int[]{x + 2, x + 7, x + 2}, new int[]{cy - 5, cy, cy + 5}, 3);
            } else {
                g2.fillPolygon(new int[]{x + 4, x + 8, x + 4, x}, new int[]{cy - 4, cy, cy + 4, cy}, 4);
            }
            g2.dispose();
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int r, int c) {
            super.getTableCellRendererComponent(t, value, selected, false, r, c);
            if (r < 0 || r >= tree.visible().size()) {
                return this;
            }
            TaskTree.Row row = tree.visible().get(r);
            Task task = row.task;
            boolean inactive = !task.getActive();
            Font f = row.isSummary() ? Theme.BOLD : Theme.BASE;
            if (inactive) {
                f = f.deriveFont(Font.ITALIC);
            }
            setFont(f);
            if (!selected) {
                setBackground(r % 2 == 1 ? Theme.STRIPE : Theme.BG);
                setForeground(inactive ? Theme.INACTIVE : task.getCritical() && !row.isSummary() ? Theme.CRITICAL : Theme.INK);
            } else {
                setForeground(Theme.INK);
            }
            int left = 6;
            glyph = 0;
            Columns.Column col = model.columnAt(t.convertColumnIndexToModel(c));
            setHorizontalAlignment(SwingConstants.LEFT);
            if (col == Columns.NAME) {
                glyph = row.isSummary() ? (tree.isCollapsed(row) ? 2 : 1) : task.getMilestone() ? 3 : 0;
                glyphX = 6 + row.depth * 16;
                left = glyphX + 14;
            } else if (col != null && col.align == Columns.Align.RIGHT) {
                setHorizontalAlignment(SwingConstants.RIGHT);
            }
            setBorder(BorderFactory.createEmptyBorder(0, left, 0, 6));
            String text = value == null ? "" : value.toString();
            setToolTipText(col == Columns.NAME ? Fmt.text(task.getName()) : text.length() > 20 ? text : null);
            return this;
        }
    }
}
