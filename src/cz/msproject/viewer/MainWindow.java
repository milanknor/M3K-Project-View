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
import javax.swing.JFileChooser;
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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
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
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

/** Hlavní okno prohlížeče. */
public final class MainWindow extends JFrame {
    private static final long serialVersionUID = 1L;
    public static final String APP_NAME = "M3K Project View";
    /** Název s oranžovou trojkou podle loga (pro HTML popisky). */
    static final String APP_NAME_HTML = "<b>M<span style='color:#FF790A'>3</span>K</b> Project View";
    public static final String VERSION = "0.2.0";
    public static final String AUTHOR = "Milan Knor";
    private static final int RECENT_MAX = 8;
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
    private final Preferences prefs = Preferences.userNodeForPackage(MainWindow.class);

    ProjectFile project;
    Path currentPath;
    boolean loading;
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
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
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
                int r = table.getSelectedRow();
                details.show(r >= 0 && r < tree.visible().size() ? tree.visible().get(r) : null);
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
        bindKey(table, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggle", () -> selectedRow().ifPresent(this::toggle));
        bindKey(table, KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0), "expand", () -> selectedRow().ifPresent(r -> setCollapsed(r, false)));
        bindKey(table, KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, 0), "expand2", () -> selectedRow().ifPresent(r -> setCollapsed(r, false)));
        bindKey(table, KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0), "collapse", () -> selectedRow().ifPresent(r -> setCollapsed(r, true)));
        bindKey(table, KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0), "collapse2", () -> selectedRow().ifPresent(r -> setCollapsed(r, true)));

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
        timeline.setExporter(img -> savePng(img, "-casova-osa", Str.t("Uložit časovou osu jako PNG")));

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
        file.add(item(Str.t("Otevřít…"), KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), this::chooseFile));
        file.add(item(Str.t("Otevřít v novém okně…"), KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), () -> {
            MainWindow w = newWindow();
            w.chooseFile();
        }));
        file.add(item(Str.t("Nové okno"), KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK), this::newWindow));
        file.add(recentMenu);
        file.add(item(Str.t("Znovu načíst"), KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), this::reload));
        file.add(item(Str.t("Zavřít okno"), KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK), () -> {
            saveWindowState();
            dispose();
        }));
        file.addSeparator();
        file.add(item(Str.t("Exportovat zobrazené úkoly do CSV…"), KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK), this::exportCsv));
        file.add(item(Str.t("Uložit Ganttův diagram jako PNG…"), KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), this::exportPng));
        file.add(item(Str.t("Uložit jako PDF…"), KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), this::exportPdf));
        file.add(item(Str.t("Tisk…"), KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK), this::printChart));
        file.addSeparator();
        file.add(item(Str.t("Konec"), KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK), () -> {
            saveWindowState();
            for (MainWindow w : new ArrayList<>(WINDOWS)) {
                w.dispose();
            }
            System.exit(0);
        }));
        bar.add(file);

        JMenu view = new JMenu(Str.t("Zobrazit"));
        view.setMnemonic('Z');
        view.add(item(Str.t("Hledat"), KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), () -> {
            tabs.setSelectedIndex(0);
            search.requestFocusInWindow();
            search.selectAll();
        }));
        view.add(item(Str.t("Rozbalit vše"), KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), () -> {
            tree.expandAll();
            refresh();
        }));
        view.add(item(Str.t("Sbalit vše"), KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), () -> {
            tree.collapseAll();
            refresh();
        }));
        view.add(item(Str.t("Rozšířený filtr…"), KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), this::showFilterDialog));
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
                    savedFilters.add(item(f.name() + "   —   " + f.describe(), null, () -> applyExtraFilter(f)));
                }
                savedFilters.addSeparator();
                savedFilters.add(item(Str.t("Zrušit rozšířený filtr"), null, () -> applyExtraFilter(null)));
            }

            @Override
            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            @Override
            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }
        });
        view.add(savedFilters);
        view.add(item(Str.t("Sloupce tabulky…"), null, () -> showColumnChooser(0, Theme.HEADER)));
        view.add(item(Str.t("Zrušit řazení"), null, () -> {
            sortColumn = null;
            tree.setOrder(null);
            updateHeaderTitles();
            refresh();
        }));
        view.addSeparator();
        view.add(item(Str.t("Přiblížit"), KeyStroke.getKeyStroke(KeyEvent.VK_ADD, InputEvent.CTRL_DOWN_MASK), () -> gantt.zoomBy(1.25, -1)));
        view.add(item(Str.t("Oddálit"), KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, InputEvent.CTRL_DOWN_MASK), () -> gantt.zoomBy(0.8, -1)));
        view.add(item(Str.t("Celý projekt"), KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK), () -> zoomBox.setSelectedItem(GanttPanel.Zoom.FIT)));
        view.addSeparator();
        view.add(item(Str.t("Přejít na dnešek"), KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK), () -> gantt.scrollToDate(java.time.LocalDate.now())));
        view.add(item(Str.t("Přejít na vybraný úkol"), KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK), () -> gantt.scrollToRow(table.getSelectedRow())));
        view.addSeparator();
        view.add(check(Str.t("Zobrazit vazby"), linksBox));
        view.add(check(Str.t("Zobrazit základní plán"), baselineBox));
        view.add(check(Str.t("Zobrazit popisky zdrojů"), labelsBox));
        view.add(check(Str.t("Zobrazit celkovou rezervu"), slackBox));
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
        appearance.add(item(Str.t("Zvětšit písmo"), KeyStroke.getKeyStroke(KeyEvent.VK_ADD, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
                () -> App.applyAppearance(Theme.DARK, Math.min(2f, Theme.SCALE + 0.1f))));
        appearance.add(item(Str.t("Zmenšit písmo"), KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
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
                windowMenu.add(item(Str.t("Nové okno"), null, MainWindow.this::newWindow));
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
        help.add(item(Str.t("Ovládání"), KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), this::showHelp));
        help.add(item(Str.t("Licence…"), null, () -> new LicenseDialog(this).setVisible(true)));
        help.add(item(Str.t("O aplikaci"), null, this::showAbout));
        bar.add(help);
        return bar;
    }

    private JCheckBoxMenuItem check(String label, JCheckBox box) {
        JCheckBoxMenuItem mi = new JCheckBoxMenuItem(label, box.isSelected());
        mi.addActionListener(e -> box.setSelected(mi.isSelected()));
        box.addActionListener(e -> mi.setSelected(box.isSelected()));
        return mi;
    }

    private static JMenuItem item(String label, KeyStroke key, Runnable action) {
        JMenuItem mi = new JMenuItem(label);
        if (key != null) {
            mi.setAccelerator(key);
        }
        mi.addActionListener(e -> action.run());
        return mi;
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
        bar.add(label(Str.t("Hledat:")));
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
        bindKey(search, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear", () -> search.setText(""));
        bar.add(search);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(label(Str.t("Filtr:")));
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
        bar.add(label(Str.t("Osnova:")));
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
        bar.add(label(Str.t("Měřítko:")));
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

    private static JLabel label(String text) {
        JLabel l = new JLabel(text + " ");
        l.setFont(Theme.BASE);
        l.setForeground(Theme.MUTED);
        return l;
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

    private static void bindKey(JComponent c, KeyStroke key, String name, Runnable action) {
        c.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
        c.getActionMap().put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
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
                        open(files.get(0).toPath());
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
        List<String[]> out = new ArrayList<>();
        String v = prefs.get("columns", "");
        if (!v.isBlank()) {
            for (String part : v.split(",")) {
                int i = part.lastIndexOf(':');
                if (i > 0) {
                    out.add(new String[]{part.substring(0, i), part.substring(i + 1)});
                }
            }
        }
        if (out.isEmpty()) {
            for (String k : Columns.DEFAULT_KEYS) {
                out.add(new String[]{k, ""});
            }
        }
        return out;
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
        menu.add(item(Str.t("Výchozí sloupce"), null, () -> {
            List<String[]> def = new ArrayList<>();
            for (String k : Columns.DEFAULT_KEYS) {
                def.add(new String[]{k, ""});
            }
            applyColumns(def);
        }));
        menu.add(item(Str.t("Přizpůsobit šířky obsahu"), null, this::autoSizeColumns));
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
        JFileChooser fc = new JFileChooser(prefs.get("lastDir", System.getProperty("user.home")));
        fc.setDialogTitle(Str.t("Otevřít projekt Microsoft Project"));
        fc.setAcceptAllFileFilterUsed(true);
        FileNameExtensionFilter all = new FileNameExtensionFilter(Str.t("Soubory Microsoft Project (*.mpp, *.mpt, *.mpx, *.xml, *.mpd)"), ProjectLoader.EXTENSIONS);
        fc.addChoosableFileFilter(all);
        fc.addChoosableFileFilter(new FileNameExtensionFilter("Project (*.mpp, *.mpt)", "mpp", "mpt"));
        fc.addChoosableFileFilter(new FileNameExtensionFilter("Project XML (*.xml)", "xml"));
        fc.addChoosableFileFilter(new FileNameExtensionFilter("Project Exchange (*.mpx)", "mpx"));
        fc.setFileFilter(all);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            prefs.put("lastDir", fc.getSelectedFile().getParent());
            open(fc.getSelectedFile().toPath());
        }
    }

    void reload() {
        if (currentPath != null) {
            open(currentPath);
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
            removeRecent(path);
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
        addRecent(path);
        updateStats();
        ((java.awt.CardLayout) center.getLayout()).show(center, "main");
        status.setText("Načteno: " + path.toAbsolutePath() + Str.t("   (pouze pro čtení – soubor se nemění)"));
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

    private void showError(String message, Throwable ex) {
        String detail = "";
        if (ex != null) {
            Throwable root = ProjectLoader.rootCause(ex);
            detail = "\n\nTechnický detail: " + root.getClass().getSimpleName() + (root.getMessage() == null ? "" : ": " + root.getMessage());
        }
        JOptionPane.showMessageDialog(this, message + detail, Str.t("Chyba"), JOptionPane.ERROR_MESSAGE);
    }

    // ---------------------------------------------------------------- export

    private void exportCsv() {
        if (project == null) {
            JOptionPane.showMessageDialog(this, Str.t("Nejprve otevřete projekt."), Str.t("Export CSV"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser(prefs.get("lastExportDir", currentPath.getParent() == null ? "." : currentPath.getParent().toString()));
        fc.setDialogTitle(Str.t("Exportovat zobrazené úkoly do CSV"));
        fc.setFileFilter(new FileNameExtensionFilter("CSV (*.csv)", "csv"));
        fc.setSelectedFile(new File(stripExt(currentPath.getFileName().toString()) + ".csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            f = new File(f.getPath() + ".csv");
        }
        if (f.exists() && JOptionPane.showConfirmDialog(this, Str.t("Soubor ") + f.getName() + Str.t(" už existuje. Přepsat?"), Str.t("Export CSV"),
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            List<Columns.Column> shown = new ArrayList<>();
            for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
                shown.add(model.columnAt(table.getColumnModel().getColumn(i).getModelIndex()));
            }
            CsvExport.write(f.toPath(), tree.visible(), columns, shown);
            prefs.put("lastExportDir", f.getParent());
            status.setText("Exportováno " + tree.visible().size() + Str.t(" řádků do ") + f.getAbsolutePath());
        } catch (Exception ex) {
            showError(Str.t("Export se nezdařil."), ex);
        }
    }

    private void exportPng() {
        if (project == null) {
            JOptionPane.showMessageDialog(this, Str.t("Nejprve otevřete projekt."), Str.t("Uložit PNG"), JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Dimension chartSize = gantt.getChart().getPreferredSize();
        int tableWidth = table.getPreferredSize().width;
        long w = (long) tableWidth + chartSize.width;
        long h = (long) Theme.HEADER + Math.max(1, tree.visible().size() * Theme.ROW);
        if (w * h > 60_000_000L) {
            JOptionPane.showMessageDialog(this, Str.t("Obrázek by byl příliš velký (") + w + " × " + h + " px).\nZmenšete měřítko nebo sbalte část osnovy.",
                    Str.t("Uložit PNG"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser(prefs.get("lastExportDir", currentPath.getParent() == null ? "." : currentPath.getParent().toString()));
        fc.setDialogTitle(Str.t("Uložit Ganttův diagram jako PNG"));
        fc.setFileFilter(new FileNameExtensionFilter("PNG (*.png)", "png"));
        fc.setSelectedFile(new File(stripExt(currentPath.getFileName().toString()) + ".png"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
            f = new File(f.getPath() + ".png");
        }
        if (f.exists() && JOptionPane.showConfirmDialog(this, Str.t("Soubor ") + f.getName() + Str.t(" už existuje. Přepsat?"), Str.t("Uložit PNG"),
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            BufferedImage img = renderImage();
            ImageIO.write(img, "png", f);
            prefs.put("lastExportDir", f.getParent());
            status.setText("Diagram uložen do " + f.getAbsolutePath() + " (" + img.getWidth() + " × " + img.getHeight() + " px)");
        } catch (Exception ex) {
            showError(Str.t("Uložení obrázku se nezdařilo."), ex);
        }
    }

    /** Uloží hotový obrázek jako PNG (dialog s výběrem souboru). */
    void savePng(BufferedImage img, String suffix, String title) {
        if (project == null || img == null) {
            return;
        }
        JFileChooser fc = new JFileChooser(prefs.get("lastExportDir", currentPath.getParent() == null ? "." : currentPath.getParent().toString()));
        fc.setDialogTitle(title);
        fc.setFileFilter(new FileNameExtensionFilter("PNG (*.png)", "png"));
        fc.setSelectedFile(new File(stripExt(currentPath.getFileName().toString()) + suffix + ".png"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
            f = new File(f.getPath() + ".png");
        }
        if (f.exists() && JOptionPane.showConfirmDialog(this, Str.t("Soubor ") + f.getName() + Str.t(" už existuje. Přepsat?"), title,
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            ImageIO.write(img, "png", f);
            prefs.put("lastExportDir", f.getParent());
            status.setText("Uloženo do " + f.getAbsolutePath() + " (" + img.getWidth() + " × " + img.getHeight() + " px)");
        } catch (Exception ex) {
            showError(Str.t("Uložení obrázku se nezdařilo."), ex);
        }
    }

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

    // ---------------------------------------------------------------- tisk a PDF

    private static final double A4_W = 595.28;
    private static final double A4_H = 841.89;
    private static final double A3_W = 841.89;
    private static final double A3_H = 1190.55;

    private boolean checkPrintable(String title) {
        if (project == null) {
            JOptionPane.showMessageDialog(this, Str.t("Nejprve otevřete projekt."), title, JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        if (tree.visible().isEmpty()) {
            JOptionPane.showMessageDialog(this, Str.t("Není co tisknout – filtru neodpovídá žádný úkol."), title, JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        return true;
    }

    /** Obrázek pro tisk: dvojnásobné rozlišení, ale s ochranou proti obřím obrázkům. */
    private PrintLayout printLayout(boolean fitWidth) {
        Dimension chartSize = gantt.getChart().getPreferredSize();
        long w = (long) table.getPreferredSize().width + chartSize.width;
        long h = (long) Theme.HEADER + Math.max(1, tree.visible().size() * Theme.ROW);
        double scale = w * h * 4 > 40_000_000L ? 1.0 : 2.0;
        if (w * h > 60_000_000L) {
            return null;
        }
        String title = currentPath.getFileName() + "   —   " + Fmt.text(project.getProjectProperties().getProjectTitle());
        return new PrintLayout(renderImage(scale), scale, tree.visible().size(), fitWidth, title);
    }

    private static JPanel printOptions(JCheckBox fit) {
        JPanel p = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        fit.setFont(Theme.BASE);
        p.add(fit);
        JLabel hint = new JLabel("<html><span style='color:" + Theme.MUTED_HEX + "'>Tiskne se to, co je zobrazeno: viditelné řádky, zvolené sloupce a měřítko diagramu.<br>"
                + Str.t("Hlavička tabulky a časová osa se opakují na každé stránce.</span></html>"));
        hint.setFont(Theme.SMALL);
        p.add(hint);
        return p;
    }

    private void printChart() {
        if (!checkPrintable(Str.t("Tisk"))) {
            return;
        }
        JCheckBox fit = new JCheckBox(Str.t("Přizpůsobit na šířku stránky (jinak měřítko 1:1 a více stránek vedle sebe)"), prefs.getBoolean("printFit", true));
        if (JOptionPane.showConfirmDialog(this, printOptions(fit), Str.t("Tisk"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        prefs.putBoolean("printFit", fit.isSelected());
        PrintLayout layout = printLayout(fit.isSelected());
        if (layout == null) {
            JOptionPane.showMessageDialog(this, Str.t("Diagram je pro tisk příliš velký. Zmenšete měřítko nebo sbalte část osnovy."), Str.t("Tisk"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        java.awt.print.PrinterJob job = java.awt.print.PrinterJob.getPrinterJob();
        job.setJobName(APP_NAME + " – " + currentPath.getFileName());
        java.awt.print.PageFormat pf = job.defaultPage();
        pf.setOrientation(java.awt.print.PageFormat.LANDSCAPE);
        job.setPrintable(layout, pf);
        if (job.printDialog()) {
            try {
                job.print();
                status.setText("Odesláno na tiskárnu: " + job.getPrintService().getName());
            } catch (java.awt.print.PrinterException ex) {
                showError(Str.t("Tisk se nezdařil."), ex);
            }
        }
    }

    private void exportPdf() {
        if (!checkPrintable(Str.t("Uložit jako PDF"))) {
            return;
        }
        JCheckBox fit = new JCheckBox(Str.t("Přizpůsobit na šířku stránky (jinak měřítko 1:1 a více stránek vedle sebe)"), prefs.getBoolean("printFit", true));
        JComboBox<String> size = new JComboBox<>(new String[]{Str.t("A4 na šířku"), Str.t("A4 na výšku"), Str.t("A3 na šířku"), Str.t("A3 na výšku")});
        size.setSelectedIndex(prefs.getInt("pdfSize", 0));
        size.setFont(Theme.BASE);
        JPanel p = printOptions(fit);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.add(label(Str.t("Stránka:")));
        row.add(size);
        p.add(row, 1);
        if (JOptionPane.showConfirmDialog(this, p, Str.t("Uložit jako PDF"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        prefs.putBoolean("printFit", fit.isSelected());
        prefs.putInt("pdfSize", size.getSelectedIndex());
        JFileChooser fc = new JFileChooser(prefs.get("lastExportDir", currentPath.getParent() == null ? "." : currentPath.getParent().toString()));
        fc.setDialogTitle(Str.t("Uložit jako PDF"));
        fc.setFileFilter(new FileNameExtensionFilter("PDF (*.pdf)", "pdf"));
        fc.setSelectedFile(new File(stripExt(currentPath.getFileName().toString()) + ".pdf"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            f = new File(f.getPath() + ".pdf");
        }
        if (f.exists() && JOptionPane.showConfirmDialog(this, Str.t("Soubor ") + f.getName() + Str.t(" už existuje. Přepsat?"), Str.t("Uložit jako PDF"),
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            int pages = writePdf(f.toPath(), fit.isSelected(), size.getSelectedIndex());
            prefs.put("lastExportDir", f.getParent());
            status.setText("PDF uloženo do " + f.getAbsolutePath() + " (" + pages + Str.t(" str.)"));
        } catch (Exception ex) {
            showError(Str.t("Uložení PDF se nezdařilo."), ex);
        }
    }

    /** Zapíše PDF; vrací počet stránek. {@code sizeIndex}: 0 A4 šířka, 1 A4 výška, 2 A3 šířka, 3 A3 výška. */
    int writePdf(Path target, boolean fitWidth, int sizeIndex) throws java.io.IOException {
        PrintLayout layout = printLayout(fitWidth);
        if (layout == null) {
            throw new java.io.IOException(Str.t("Diagram je příliš velký – zmenšete měřítko nebo sbalte část osnovy."));
        }
        double pw = sizeIndex >= 2 ? A3_H : A4_H;
        double ph = sizeIndex >= 2 ? A3_W : A4_W;
        if (sizeIndex % 2 == 1) {
            double t = pw;
            pw = ph;
            ph = t;
        }
        double margin = 36;
        double cw = pw - 2 * margin;
        double ch = ph - 2 * margin;
        int pages = layout.pageCount(cw, ch);
        final double fpw = pw;
        final double fph = ph;
        PdfWriter.write(target, pages, i -> {
            BufferedImage page = new BufferedImage((int) Math.round(fpw * 150 / 72), (int) Math.round(fph * 150 / 72), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = page.createGraphics();
            g.setColor(Theme.BG);
            g.fillRect(0, 0, page.getWidth(), page.getHeight());
            g.scale(150 / 72.0, 150 / 72.0);
            g.translate(margin, margin);
            layout.paintPage(g, i, cw, ch);
            g.dispose();
            return page;
        }, pw, ph, currentPath.getFileName().toString());
        return pages;
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    // ---------------------------------------------------------------- nedávné soubory, stav okna

    private List<Path> recentFiles() {
        List<Path> list = new ArrayList<>();
        for (int i = 0; i < RECENT_MAX; i++) {
            String v = prefs.get("recent" + i, null);
            if (v != null && !v.isBlank()) {
                list.add(Path.of(v));
            }
        }
        return list;
    }

    private void storeRecent(List<Path> list) {
        for (int i = 0; i < RECENT_MAX; i++) {
            if (i < list.size()) {
                prefs.put("recent" + i, list.get(i).toAbsolutePath().toString());
            } else {
                prefs.remove("recent" + i);
            }
        }
        updateRecentMenu();
    }

    private void addRecent(Path path) {
        List<Path> list = recentFiles();
        Path abs = path.toAbsolutePath();
        list.removeIf(p -> p.toAbsolutePath().equals(abs));
        list.add(0, abs);
        while (list.size() > RECENT_MAX) {
            list.remove(list.size() - 1);
        }
        storeRecent(list);
    }

    private void removeRecent(Path path) {
        List<Path> list = recentFiles();
        Path abs = path.toAbsolutePath();
        list.removeIf(p -> p.toAbsolutePath().equals(abs));
        storeRecent(list);
    }

    private void updateRecentMenu() {
        recentMenu.removeAll();
        List<Path> list = recentFiles();
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
        clear.addActionListener(e -> storeRecent(new ArrayList<>()));
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
        StringBuilder sb = new StringBuilder();
        for (String[] kv : currentColumnSetting()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(kv[0]).append(':').append(kv[1]);
        }
        prefs.put("columns", sb.toString());
    }

    // ---------------------------------------------------------------- nápověda

    private void showHelp() {
        String text = "<html><body style='" + Theme.HTML_BODY + ";width:520px'>"
                + Str.t("<b>Otevření souboru</b><br>Ctrl+O, tlačítko Otevřít… nebo přetažení souboru do okna. ")
                + Str.t("Podporované formáty: MPP/MPT (Project 98 – 2021/365), XML (MSPDI), MPX, MPD.<br><br>")
                + Str.t("<b>Osnova</b><br>Kliknutí na šipku u souhrnného úkolu, dvojklik nebo mezerník sbalí/rozbalí podúkoly. ")
                + Str.t("Klávesy + a − rozbalí/sbalí vybraný souhrn. Ctrl+Shift+E rozbalí vše, Ctrl+Shift+C sbalí vše. ")
                + Str.t("Pole Osnova rozbalí hierarchii do zvolené úrovně.<br><br>")
                + Str.t("<b>Hledání a filtry</b><br>Ctrl+F přejde do pole Hledat (název, ID, WBS, zdroje, poznámky). ")
                + Str.t("Filtr omezí seznam např. na kritické, zpožděné nebo nedokončené úkoly; nadřazené souhrny zůstávají vidět.<br><br>")
                + Str.t("<b>Diagram</b><br>Měřítko: Dny/Týdny/Měsíce/Čtvrtletí/Roky/Přizpůsobit, Ctrl+kolečko myši přibližuje kolem kurzoru, ")
                + Str.t("Ctrl+0 zobrazí celý projekt, Ctrl+T posune na dnešek, Ctrl+G na vybraný úkol. ")
                + Str.t("Modrá = úkol, červená = kritický úkol, černý pruh = souhrn, kosočtverec = milník, zelená šipka = termín, ")
                + Str.t("šedý pruh = základní plán, červená svislá čára = dnešek, modrá = datum stavu.<br><br>")
                + Str.t("<b>Export</b><br>Ctrl+E uloží zobrazené úkoly do CSV (otevře se v Excelu), Ctrl+Shift+P uloží diagram jako PNG.<br><br>")
                + Str.t("Aplikace soubory pouze čte a nikam je neodesílá.")
                + "</body></html>";
        JOptionPane.showMessageDialog(this, text, Str.t("Ovládání"), JOptionPane.PLAIN_MESSAGE);
    }

    private void showAbout() {
        String text = "<html><body style='" + Theme.HTML_BODY + ";width:420px'>"
                + "<div style='font-size:14pt'>" + APP_NAME_HTML + " " + VERSION + "</div>"
                + Str.t("Prohlížeč souborů Microsoft Project (pouze pro čtení).<br>")
                + Str.t("Autor: ") + AUTHOR + Str.t(". Freeware – zdarma k použití i šíření v nezměněné podobě (Nápověda → Licence…).<br><br>")
                + Str.t("Čtení souborů zajišťuje knihovna <b>MPXJ</b> (https://www.mpxj.org, licence LGPL 2.1+). ")
                + Str.t("Licence všech knihoven třetích stran: Nápověda → Licence… (a složka <b>licenses</b> vedle programu).<br>")
                + Str.t("Běhové prostředí: Java ") + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ").<br><br>"
                + Str.t("Tato aplikace není produktem společnosti Microsoft. Microsoft Project je ochranná známka Microsoft Corporation.")
                + "</body></html>";
        JOptionPane.showMessageDialog(this, text, Str.t("O aplikaci"), JOptionPane.PLAIN_MESSAGE, new ImageIcon(Icons.app(64)));
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

    static Dimension screenSize() {
        return Toolkit.getDefaultToolkit().getScreenSize();
    }
}
