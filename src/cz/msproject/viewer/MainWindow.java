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
    public static final String VERSION = "0.1.0";
    public static final String AUTHOR = "Milan Knor";
    private static final int RECENT_MAX = 8;

    final TaskTree tree = new TaskTree();
    final TaskTableModel model = new TaskTableModel(tree);
    final JTable table = new JTable(model);
    final GanttPanel gantt = new GanttPanel(tree, table);
    final DetailsPanel details = new DetailsPanel();
    final ResourcePanel resources = new ResourcePanel();
    final ProjectInfoPanel info = new ProjectInfoPanel();
    final JTextField search = new JTextField(18);
    final JComboBox<TaskTree.Filter> filterBox = new JComboBox<>(TaskTree.Filter.values());
    final JComboBox<String> levelBox = new JComboBox<>();
    final JComboBox<GanttPanel.Zoom> zoomBox = new JComboBox<>(GanttPanel.Zoom.values());
    final JCheckBox linksBox = new JCheckBox("Vazby", true);
    final JCheckBox baselineBox = new JCheckBox("Základní plán", false);
    final JCheckBox labelsBox = new JCheckBox("Popisky", true);
    final JCheckBox slackBox = new JCheckBox("Rezerva", false);
    final JLabel status = new JLabel("Otevřete soubor Microsoft Project (Ctrl+O) nebo jej přetáhněte do okna.");
    final JLabel stats = new JLabel(" ");
    final JLabel titleLabel = new JLabel("Žádný otevřený soubor");
    final JTabbedPane tabs = new JTabbedPane();
    final JMenu recentMenu = new JMenu("Nedávné soubory");
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
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImages(Icons.appImages());
        setMinimumSize(new Dimension(900, 600));

        // ---- tabulka úkolů ----
        table.setFont(Theme.BASE);
        table.setRowHeight(Theme.ROW);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setShowVerticalLines(false);
        table.setGridColor(Theme.GRID);
        table.setFillsViewportHeight(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(0, Theme.HEADER));
        table.getTableHeader().setFont(Theme.BOLD.deriveFont(12f));
        table.setSelectionBackground(Theme.SELECTION);
        table.setSelectionForeground(Theme.INK);
        for (TaskTableModel.Col c : TaskTableModel.Col.values()) {
            TableColumn col = table.getColumnModel().getColumn(c.ordinal());
            col.setPreferredWidth(c.width);
            col.setCellRenderer(new TaskCellRenderer(c));
        }
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
                if (c == TaskTableModel.Col.NAME.ordinal() && row.isSummary() && e.getClickCount() == 1) {
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
        resources.setNavigator(this::goToTask);

        tabs.setFont(Theme.BASE);
        tabs.addTab("Ganttův diagram", verticalSplit);
        tabs.addTab("Zdroje", resources);
        tabs.addTab("Projekt", info);

        setJMenuBar(buildMenu());
        add(buildToolbar(), BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        installDropHandler(getRootPane(), table, gantt.getChart(), gantt.getHeader(), resources.table(), details, info, tabs);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveWindowState();
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
        JMenu file = new JMenu("Soubor");
        file.setMnemonic('S');
        file.add(item("Otevřít…", KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), this::chooseFile));
        file.add(recentMenu);
        file.add(item("Znovu načíst", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), this::reload));
        file.addSeparator();
        file.add(item("Exportovat zobrazené úkoly do CSV…", KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK), this::exportCsv));
        file.add(item("Uložit Ganttův diagram jako PNG…", KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), this::exportPng));
        file.addSeparator();
        file.add(item("Konec", KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK), () -> {
            saveWindowState();
            dispose();
            System.exit(0);
        }));
        bar.add(file);

        JMenu view = new JMenu("Zobrazit");
        view.setMnemonic('Z');
        view.add(item("Hledat", KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK), () -> {
            tabs.setSelectedIndex(0);
            search.requestFocusInWindow();
            search.selectAll();
        }));
        view.add(item("Rozbalit vše", KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), () -> {
            tree.expandAll();
            refresh();
        }));
        view.add(item("Sbalit vše", KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), () -> {
            tree.collapseAll();
            refresh();
        }));
        view.addSeparator();
        view.add(item("Přiblížit", KeyStroke.getKeyStroke(KeyEvent.VK_ADD, InputEvent.CTRL_DOWN_MASK), () -> gantt.zoomBy(1.25, -1)));
        view.add(item("Oddálit", KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, InputEvent.CTRL_DOWN_MASK), () -> gantt.zoomBy(0.8, -1)));
        view.add(item("Celý projekt", KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK), () -> zoomBox.setSelectedItem(GanttPanel.Zoom.FIT)));
        view.addSeparator();
        view.add(item("Přejít na dnešek", KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK), () -> gantt.scrollToDate(java.time.LocalDate.now())));
        view.add(item("Přejít na vybraný úkol", KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_DOWN_MASK), () -> gantt.scrollToRow(table.getSelectedRow())));
        view.addSeparator();
        view.add(check("Zobrazit vazby", linksBox));
        view.add(check("Zobrazit základní plán", baselineBox));
        view.add(check("Zobrazit popisky zdrojů", labelsBox));
        view.add(check("Zobrazit celkovou rezervu", slackBox));
        bar.add(view);

        JMenu help = new JMenu("Nápověda");
        help.setMnemonic('N');
        help.add(item("Ovládání", KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), this::showHelp));
        help.add(item("Licence…", null, () -> new LicenseDialog(this).setVisible(true)));
        help.add(item("O aplikaci", null, this::showAbout));
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
        top.setBackground(Color.WHITE);
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
        JButton open = new JButton("Otevřít…");
        open.setFont(Theme.BASE);
        open.addActionListener(e -> chooseFile());
        bar.add(open);
        bar.addSeparator();
        bar.add(label("Hledat:"));
        search.setFont(Theme.BASE);
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
        bar.add(label("Filtr:"));
        filterBox.setFont(Theme.BASE);
        filterBox.addActionListener(e -> {
            if (!suppressEvents) {
                tree.setFilter((TaskTree.Filter) filterBox.getSelectedItem());
                refresh();
            }
        });
        bar.add(filterBox);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(label("Osnova:"));
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
        bar.add(label("Měřítko:"));
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
            bar.add(b);
        }
        linksBox.addActionListener(e -> gantt.setShowLinks(linksBox.isSelected()));
        baselineBox.addActionListener(e -> gantt.setShowBaseline(baselineBox.isSelected()));
        labelsBox.addActionListener(e -> gantt.setShowLabels(labelsBox.isSelected()));
        slackBox.addActionListener(e -> gantt.setShowSlack(slackBox.isSelected()));
        baselineBox.setToolTipText("Šedý pruh pod úkolem = uložený základní plán");
        slackBox.setToolTipText("Tenká čára za úkolem = celková časová rezerva");
        bar.addSeparator();
        JButton today = new JButton("Dnes");
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
                    showError("Přetažený soubor se nepodařilo převzít.", ex);
                }
                return false;
            }
        };
        for (JComponent c : targets) {
            c.setTransferHandler(handler);
        }
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
        String s = tree.size() + " úkolů  •  " + summaries + " souhrnných  •  " + milestones + " milníků  •  " + critical + " kritických";
        if (tree.isFiltering()) {
            s += "  •  filtr: " + tree.matchCount() + " shod, zobrazeno " + tree.visible().size() + " řádků";
        } else {
            s += "  •  zobrazeno " + tree.visible().size();
        }
        stats.setText(s);
    }

    private void updateLevelBox() {
        suppressEvents = true;
        try {
            levelBox.removeAllItems();
            levelBox.addItem("Všechny úrovně");
            int max = Math.max(1, tree.maxDepthInProject());
            for (int i = 1; i <= max; i++) {
                levelBox.addItem("Úroveň " + i);
            }
            levelBox.setSelectedIndex(0);
        } finally {
            suppressEvents = false;
        }
    }

    // ---------------------------------------------------------------- otevírání

    void chooseFile() {
        if (loading) {
            return;
        }
        JFileChooser fc = new JFileChooser(prefs.get("lastDir", System.getProperty("user.home")));
        fc.setDialogTitle("Otevřít projekt Microsoft Project");
        fc.setAcceptAllFileFilterUsed(true);
        FileNameExtensionFilter all = new FileNameExtensionFilter("Soubory Microsoft Project (*.mpp, *.mpt, *.mpx, *.xml, *.mpd)", ProjectLoader.EXTENSIONS);
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
                        status.setText("Soubor se nepodařilo načíst" + (project == null ? "." : "; předchozí projekt zůstává otevřený."));
                        showError("Soubor se nepodařilo otevřít:\n" + path.getFileName()
                                + "\n\nMůže být poškozený, v nepodporované variantě nebo to není soubor Microsoft Project.", cause);
                    }
                } catch (Exception ex) {
                    status.setText("Soubor se nepodařilo načíst.");
                    showError("Soubor se nepodařilo otevřít.", ex);
                }
            }
        }.execute();
    }

    private void askPassword(Path path, boolean retry) {
        status.setText("Soubor je chráněn heslem pro čtení.");
        JPasswordField pf = new JPasswordField(20);
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.add(new JLabel((retry ? "Heslo nebylo přijato. " : "") + "Soubor " + path.getFileName() + " je chráněn heslem pro čtení."), BorderLayout.NORTH);
        p.add(pf, BorderLayout.CENTER);
        SwingUtilities.invokeLater(pf::requestFocusInWindow);
        int result = JOptionPane.showConfirmDialog(this, p, "Heslo pro čtení", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
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
        tree.load(file);
        model.setCurrency(Fmt.text(file.getProjectProperties().getCurrencySymbol()));
        table.clearSelection();
        model.fireTableDataChanged();
        gantt.setProject(file);
        details.setProject(file);
        details.show(null);
        resources.setProject(file);
        info.setProject(file, path, tree);
        updateLevelBox();
        String name = path.getFileName().toString();
        titleLabel.setText(name);
        setTitle(name + " — " + APP_NAME);
        addRecent(path);
        updateStats();
        status.setText("Načteno: " + path.toAbsolutePath() + "   (pouze pro čtení – soubor se nemění)");
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
        JOptionPane.showMessageDialog(this, message + detail, "Chyba", JOptionPane.ERROR_MESSAGE);
    }

    // ---------------------------------------------------------------- export

    private void exportCsv() {
        if (project == null) {
            JOptionPane.showMessageDialog(this, "Nejprve otevřete projekt.", "Export CSV", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser(prefs.get("lastExportDir", currentPath.getParent() == null ? "." : currentPath.getParent().toString()));
        fc.setDialogTitle("Exportovat zobrazené úkoly do CSV");
        fc.setFileFilter(new FileNameExtensionFilter("CSV (*.csv)", "csv"));
        fc.setSelectedFile(new File(stripExt(currentPath.getFileName().toString()) + ".csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            f = new File(f.getPath() + ".csv");
        }
        if (f.exists() && JOptionPane.showConfirmDialog(this, "Soubor " + f.getName() + " už existuje. Přepsat?", "Export CSV",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            CsvExport.write(f.toPath(), tree.visible(), Fmt.text(project.getProjectProperties().getCurrencySymbol()));
            prefs.put("lastExportDir", f.getParent());
            status.setText("Exportováno " + tree.visible().size() + " řádků do " + f.getAbsolutePath());
        } catch (Exception ex) {
            showError("Export se nezdařil.", ex);
        }
    }

    private void exportPng() {
        if (project == null) {
            JOptionPane.showMessageDialog(this, "Nejprve otevřete projekt.", "Uložit PNG", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Dimension chartSize = gantt.getChart().getPreferredSize();
        int tableWidth = table.getPreferredSize().width;
        long w = (long) tableWidth + chartSize.width;
        long h = (long) Theme.HEADER + Math.max(1, tree.visible().size() * Theme.ROW);
        if (w * h > 60_000_000L) {
            JOptionPane.showMessageDialog(this, "Obrázek by byl příliš velký (" + w + " × " + h + " px).\nZmenšete měřítko nebo sbalte část osnovy.",
                    "Uložit PNG", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser(prefs.get("lastExportDir", currentPath.getParent() == null ? "." : currentPath.getParent().toString()));
        fc.setDialogTitle("Uložit Ganttův diagram jako PNG");
        fc.setFileFilter(new FileNameExtensionFilter("PNG (*.png)", "png"));
        fc.setSelectedFile(new File(stripExt(currentPath.getFileName().toString()) + ".png"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
            f = new File(f.getPath() + ".png");
        }
        if (f.exists() && JOptionPane.showConfirmDialog(this, "Soubor " + f.getName() + " už existuje. Přepsat?", "Uložit PNG",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            BufferedImage img = renderImage();
            ImageIO.write(img, "png", f);
            prefs.put("lastExportDir", f.getParent());
            status.setText("Diagram uložen do " + f.getAbsolutePath() + " (" + img.getWidth() + " × " + img.getHeight() + " px)");
        } catch (Exception ex) {
            showError("Uložení obrázku se nezdařilo.", ex);
        }
    }

    /** Tabulka + diagram v plné velikosti jako jeden obrázek. */
    BufferedImage renderImage() {
        BufferedImage chartImg = gantt.renderImage();
        int tableWidth = table.getPreferredSize().width;
        int rowsHeight = Math.max(1, tree.visible().size() * Theme.ROW);
        int h = Theme.HEADER + rowsHeight;
        BufferedImage img = new BufferedImage(tableWidth + chartImg.getWidth(), Math.max(h, chartImg.getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        Rectangle headerBounds = table.getTableHeader().getBounds();
        Rectangle tableBounds = table.getBounds();
        table.getTableHeader().setBounds(0, 0, tableWidth, Theme.HEADER);
        table.setBounds(0, 0, tableWidth, rowsHeight);
        g.setClip(0, 0, tableWidth, Theme.HEADER);
        table.getTableHeader().paint(g);
        g.translate(0, Theme.HEADER);
        g.setClip(0, 0, tableWidth, rowsHeight);
        table.paint(g);
        g.translate(0, -Theme.HEADER);
        table.getTableHeader().setBounds(headerBounds);
        table.setBounds(tableBounds);
        g.setClip(null);
        g.drawImage(chartImg, tableWidth, 0, null);
        g.setColor(Theme.GRID_STRONG);
        g.drawLine(tableWidth, 0, tableWidth, img.getHeight());
        g.dispose();
        return img;
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
        if (list.isEmpty()) {
            JMenuItem none = new JMenuItem("(žádné)");
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
            mi.addActionListener(e -> open(p));
            recentMenu.add(mi);
            n++;
        }
        recentMenu.addSeparator();
        JMenuItem clear = new JMenuItem("Vymazat seznam");
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

    private void saveWindowState() {
        boolean max = (getExtendedState() & MAXIMIZED_BOTH) == MAXIMIZED_BOTH;
        prefs.putBoolean("winMax", max);
        if (!max) {
            prefs.putInt("winW", getWidth());
            prefs.putInt("winH", getHeight());
            prefs.putInt("winX", getX());
            prefs.putInt("winY", getY());
        }
        prefs.putInt("divider", mainSplit.getDividerLocation());
    }

    // ---------------------------------------------------------------- nápověda

    private void showHelp() {
        String text = "<html><body style='font-family:Segoe UI;font-size:10pt;width:520px'>"
                + "<b>Otevření souboru</b><br>Ctrl+O, tlačítko Otevřít… nebo přetažení souboru do okna. "
                + "Podporované formáty: MPP/MPT (Project 98 – 2021/365), XML (MSPDI), MPX, MPD.<br><br>"
                + "<b>Osnova</b><br>Kliknutí na šipku u souhrnného úkolu, dvojklik nebo mezerník sbalí/rozbalí podúkoly. "
                + "Klávesy + a − rozbalí/sbalí vybraný souhrn. Ctrl+Shift+E rozbalí vše, Ctrl+Shift+C sbalí vše. "
                + "Pole Osnova rozbalí hierarchii do zvolené úrovně.<br><br>"
                + "<b>Hledání a filtry</b><br>Ctrl+F přejde do pole Hledat (název, ID, WBS, zdroje, poznámky). "
                + "Filtr omezí seznam např. na kritické, zpožděné nebo nedokončené úkoly; nadřazené souhrny zůstávají vidět.<br><br>"
                + "<b>Diagram</b><br>Měřítko: Dny/Týdny/Měsíce/Čtvrtletí/Roky/Přizpůsobit, Ctrl+kolečko myši přibližuje kolem kurzoru, "
                + "Ctrl+0 zobrazí celý projekt, Ctrl+T posune na dnešek, Ctrl+G na vybraný úkol. "
                + "Modrá = úkol, červená = kritický úkol, černý pruh = souhrn, kosočtverec = milník, zelená šipka = termín, "
                + "šedý pruh = základní plán, červená svislá čára = dnešek, modrá = datum stavu.<br><br>"
                + "<b>Export</b><br>Ctrl+E uloží zobrazené úkoly do CSV (otevře se v Excelu), Ctrl+Shift+P uloží diagram jako PNG.<br><br>"
                + "Aplikace soubory pouze čte a nikam je neodesílá."
                + "</body></html>";
        JOptionPane.showMessageDialog(this, text, "Ovládání", JOptionPane.PLAIN_MESSAGE);
    }

    private void showAbout() {
        String text = "<html><body style='font-family:Segoe UI;font-size:10pt;width:420px'>"
                + "<div style='font-size:14pt'>" + APP_NAME_HTML + " " + VERSION + "</div>"
                + "Prohlížeč souborů Microsoft Project (pouze pro čtení).<br>"
                + "Autor: " + AUTHOR + ". Volně šiřitelný program, licence MIT (soubor LICENSE.txt).<br><br>"
                + "Čtení souborů zajišťuje knihovna <b>MPXJ</b> (https://www.mpxj.org, licence LGPL 2.1+). "
                + "Licence všech knihoven třetích stran: Nápověda → Licence… (a složka <b>licenses</b> vedle programu).<br>"
                + "Běhové prostředí: Java " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ").<br><br>"
                + "Tato aplikace není produktem společnosti Microsoft. Microsoft Project je ochranná známka Microsoft Corporation."
                + "</body></html>";
        JOptionPane.showMessageDialog(this, text, "O aplikaci", JOptionPane.PLAIN_MESSAGE, new ImageIcon(Icons.app(64)));
    }

    // ---------------------------------------------------------------- renderer

    private final class TaskCellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        private final TaskTableModel.Col col;
        private int glyph; // 0 nic, 1 rozbaleno, 2 sbaleno, 3 milník
        private int glyphX;

        TaskCellRenderer(TaskTableModel.Col col) {
            this.col = col;
        }

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
                setBackground(r % 2 == 1 ? Theme.STRIPE : Color.WHITE);
                setForeground(inactive ? Theme.INACTIVE : task.getCritical() && !row.isSummary() ? Theme.CRITICAL : Theme.INK);
            } else {
                setForeground(Theme.INK);
            }
            int left = 6;
            glyph = 0;
            setHorizontalAlignment(SwingConstants.LEFT);
            if (col == TaskTableModel.Col.NAME) {
                glyph = row.isSummary() ? (tree.isCollapsed(row) ? 2 : 1) : task.getMilestone() ? 3 : 0;
                glyphX = 6 + row.depth * 16;
                left = glyphX + 14;
            } else if (col == TaskTableModel.Col.ID || col == TaskTableModel.Col.DURATION || col == TaskTableModel.Col.PERCENT
                    || col == TaskTableModel.Col.WORK || col == TaskTableModel.Col.COST || col == TaskTableModel.Col.SLACK) {
                setHorizontalAlignment(SwingConstants.RIGHT);
            }
            setBorder(BorderFactory.createEmptyBorder(0, left, 0, 6));
            setToolTipText(col == TaskTableModel.Col.NAME ? Fmt.text(task.getName()) : null);
            return this;
        }
    }

    static Dimension screenSize() {
        return Toolkit.getDefaultToolkit().getScreenSize();
    }
}
