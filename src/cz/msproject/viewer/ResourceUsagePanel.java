package cz.msproject.viewer;

import org.mpxj.Duration;
import org.mpxj.LocalDateTimeRange;
import org.mpxj.ProjectCalendar;
import org.mpxj.ProjectFile;
import org.mpxj.Resource;
import org.mpxj.ResourceAssignment;
import org.mpxj.ResourceType;
import org.mpxj.Task;
import org.mpxj.TimeUnit;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Vytížení zdrojů v čase: tabulka Zdroj × období (dny / týdny / měsíce) s hodinami
 * práce nebo % vytížení; přetížení (práce > kapacita podle kalendáře zdroje
 * a max. jednotek) je červeně. Dole rozpis práce vybraného zdroje po úkolech.
 */
public final class ResourceUsagePanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int MAX_BUCKETS = 420;

    public enum Period {
        DAYS(Str.t("Dny")), WEEKS(Str.t("Týdny")), MONTHS(Str.t("Měsíce"));

        final String label;

        Period(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Mode {
        HOURS(Str.t("Hodiny práce")), PERCENT(Str.t("% vytížení"));

        final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Výsledek výpočtu: názvy řádků, hodnoty a kapacity po obdobích. */
    static final class Usage {
        final List<LocalDateTimeRange> buckets;
        final String[] headers;
        final List<String> names = new ArrayList<>();
        final List<String> detail = new ArrayList<>();
        final List<double[]> work = new ArrayList<>();
        final List<double[]> capacity = new ArrayList<>();
        final List<Object> keys = new ArrayList<>();
        int overCells;

        Usage(List<LocalDateTimeRange> buckets, String[] headers) {
            this.buckets = buckets;
            this.headers = headers;
        }

        boolean isOver(int row, int col) {
            double[] cap = capacity.get(row);
            return cap != null && work.get(row)[col] > cap[col] + 0.01;
        }
    }

    private final class UsageModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        Usage usage;
        List<Integer> rows = new ArrayList<>();

        void set(Usage u, boolean onlyOver) {
            usage = u;
            rows.clear();
            if (u != null) {
                for (int i = 0; i < u.names.size(); i++) {
                    if (!onlyOver || hasOver(u, i)) {
                        rows.add(i);
                    }
                }
            }
            fireTableStructureChanged();
        }

        private boolean hasOver(Usage u, int i) {
            for (int c = 0; c < u.buckets.size(); c++) {
                if (u.isOver(i, c)) {
                    return true;
                }
            }
            return false;
        }

        int usageRow(int viewRow) {
            return rows.get(viewRow);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return usage == null ? 0 : usage.buckets.size();
        }

        @Override
        public String getColumnName(int column) {
            return usage.headers[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            int r = rows.get(rowIndex);
            double v = usage.work.get(r)[columnIndex];
            if (v < 0.005) {
                return "";
            }
            if (mode == Mode.PERCENT) {
                double[] cap = usage.capacity.get(r);
                if (cap == null || cap[columnIndex] < 0.005) {
                    return Fmt.number(v) + " h";
                }
                return Math.round(v / cap[columnIndex] * 100) + " %";
            }
            return Fmt.number(v);
        }

        boolean isOver(int rowIndex, int columnIndex) {
            return usage.isOver(rows.get(rowIndex), columnIndex);
        }
    }

    private final class NameModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private final UsageModel data;
        private final String title;

        NameModel(UsageModel data, String title) {
            this.data = data;
            this.title = title;
        }

        @Override
        public int getRowCount() {
            return data.getRowCount();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? title : column == 1 ? Str.t("Celkem h") : Str.t("Detail");
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            int r = data.usageRow(rowIndex);
            if (columnIndex == 0) {
                return data.usage.names.get(r);
            }
            if (columnIndex == 1) {
                double sum = 0;
                for (double v : data.usage.work.get(r)) {
                    sum += v;
                }
                return Fmt.number(sum);
            }
            return data.usage.detail.get(r);
        }
    }

    private final JComboBox<Period> periodBox = new JComboBox<>(Period.values());
    private final JComboBox<Mode> modeBox = new JComboBox<>(Mode.values());
    private final JCheckBox onlyOver = new JCheckBox(Str.t("Jen přetížené zdroje"));
    private final JLabel summary = new JLabel(" ");
    private final JLabel detailTitle = new JLabel(Str.t("Práce vybraného zdroje po úkolech"));
    private final UsageModel resModel = new UsageModel();
    private final UsageModel taskModel = new UsageModel();
    private final JTable resTable = new JTable(resModel);
    private final JTable resNames = new JTable(new NameModel(resModel, Str.t("Zdroj")));
    private final JTable taskTable = new JTable(taskModel);
    private final JTable taskNames = new JTable(new NameModel(taskModel, Str.t("Úkol")));
    private ProjectFile project;
    private Usage usage;
    private Mode mode = Mode.HOURS;
    private Consumer<Task> navigator;
    private final List<Resource> resources = new ArrayList<>();

    public ResourceUsagePanel() {
        super(new BorderLayout());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        bar.add(label(Str.t("Období:")));
        periodBox.setSelectedItem(Period.WEEKS);
        periodBox.setFont(Theme.BASE);
        periodBox.addActionListener(e -> recompute());
        bar.add(periodBox);
        bar.add(label("Zobrazit:"));
        modeBox.setFont(Theme.BASE);
        modeBox.addActionListener(e -> {
            mode = (Mode) modeBox.getSelectedItem();
            resModel.fireTableDataChanged();
            taskModel.fireTableDataChanged();
        });
        bar.add(modeBox);
        onlyOver.setFont(Theme.BASE);
        onlyOver.addActionListener(e -> applyFilter());
        bar.add(onlyOver);
        summary.setFont(Theme.BASE);
        summary.setForeground(Theme.MUTED);
        bar.add(summary);
        add(bar, BorderLayout.NORTH);

        JScrollPane top = frozen(resTable, resNames, new int[]{220, 70, 120});
        JScrollPane bottom = frozen(taskTable, taskNames, new int[]{300, 70, 110});
        resTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showTasks();
            }
        });
        MouseAdapter dbl = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && navigator != null) {
                    int r = ((JTable) e.getSource()).getSelectedRow();
                    if (r >= 0 && r < taskModel.getRowCount() && taskModel.usage != null) {
                        Object key = taskModel.usage.keys.get(taskModel.usageRow(r));
                        if (key instanceof Task t) {
                            navigator.accept(t);
                        }
                    }
                }
            }
        };
        taskTable.addMouseListener(dbl);
        taskNames.addMouseListener(dbl);
        detailTitle.setFont(Theme.BOLD);
        detailTitle.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(detailTitle, BorderLayout.NORTH);
        bottomPanel.add(bottom, BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottomPanel);
        split.setResizeWeight(0.6);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);
        JLabel legend = new JLabel("<html><span style='background:#fecaca;color:#991b1b'>&nbsp;přetížení&nbsp;</span> práce > kapacita zdroje v období "
                + "(kalendář zdroje × max. jednotky) · <span style='background:#ffedd5'>&nbsp;práce&nbsp;</span> · hodnoty jsou hodiny práce, "
                + "podle časového rozložení uloženého v souboru</html>");
        legend.setFont(Theme.SMALL);
        legend.setBorder(BorderFactory.createEmptyBorder(4, 12, 6, 12));
        add(legend, BorderLayout.SOUTH);
    }

    private static JLabel label(String s) {
        JLabel l = new JLabel(s);
        l.setFont(Theme.BASE);
        l.setForeground(Theme.MUTED);
        return l;
    }

    /** Tabulka s hodnotami + „zmrazené“ sloupce s názvy jako záhlaví řádků. */
    private JScrollPane frozen(JTable data, JTable names, int[] nameWidths) {
        for (JTable t : new JTable[]{data, names}) {
            t.setFont(Theme.BASE);
            t.setRowHeight(24);
            t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            t.getTableHeader().setReorderingAllowed(false);
            t.setShowVerticalLines(true);
            t.setGridColor(Theme.GRID);
            t.setFillsViewportHeight(true);
            t.getTableHeader().setFont(Theme.BOLD.deriveFont(12f));
        }
        names.setSelectionModel(data.getSelectionModel());
        names.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, false, r, c);
                setHorizontalAlignment(c == 1 ? SwingConstants.RIGHT : SwingConstants.LEFT);
                setFont(c == 0 ? Theme.BOLD : Theme.BASE);
                setForeground(c == 2 ? Theme.MUTED : Theme.INK);
                if (!sel) {
                    setBackground(Theme.HEADER_BG);
                }
                return this;
            }
        });
        data.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, false, r, c);
                setHorizontalAlignment(SwingConstants.RIGHT);
                UsageModel m = (UsageModel) t.getModel();
                boolean over = m.usage != null && m.isOver(r, c);
                boolean has = v != null && !v.toString().isEmpty();
                setForeground(over ? Theme.OVERALLOCATED_INK : Theme.INK);
                if (sel) {
                    setBackground(over ? new Color(252, 165, 165) : Theme.SELECTION);
                } else {
                    setBackground(over ? Theme.OVERALLOCATED : has ? Theme.WORK_LIGHT : Theme.BG);
                }
                setFont(over ? Theme.BOLD : Theme.BASE);
                return this;
            }
        });
        names.setPreferredScrollableViewportSize(new Dimension(nameWidths[0] + nameWidths[1] + nameWidths[2], 0));
        JScrollPane sp = new JScrollPane(data);
        sp.setRowHeaderView(names);
        sp.setCorner(JScrollPane.UPPER_LEFT_CORNER, names.getTableHeader());
        sp.setBorder(null);
        sp.getHorizontalScrollBar().setUnitIncrement(40);
        data.getModel().addTableModelListener(e -> {
            for (int i = 0; i < data.getColumnCount(); i++) {
                data.getColumnModel().getColumn(i).setPreferredWidth(64);
            }
            for (int i = 0; i < Math.min(3, names.getColumnCount()); i++) {
                names.getColumnModel().getColumn(i).setPreferredWidth(nameWidths[i]);
            }
        });
        return sp;
    }

    public void setNavigator(Consumer<Task> navigator) {
        this.navigator = navigator;
    }

    public JTable table() {
        return resTable;
    }

    public void setProject(ProjectFile p) {
        project = p;
        recompute();
    }

    Period period() {
        return (Period) periodBox.getSelectedItem();
    }

    void setPeriod(Period p) {
        periodBox.setSelectedItem(p);
    }

    Usage usage() {
        return usage;
    }

    private javax.swing.SwingWorker<Usage, Void> worker;

    /** Výpočet běží na pozadí (u velkých projektů trvá sekundy); dokončený výsledek se zobrazí přes applyFilter(). */
    private void recompute() {
        resources.clear();
        if (worker != null) {
            worker.cancel(false);
            worker = null;
        }
        if (project == null) {
            usage = null;
            applyFilter();
            return;
        }
        for (Resource r : project.getResources()) {
            if (r.getName() != null && !r.getName().isBlank()) {
                resources.add(r);
            }
        }
        usage = null;
        resModel.set(null, false);
        taskModel.set(null, false);
        summary.setText(Str.t("Počítám vytížení…"));
        final ProjectFile p = project;
        final Period per = period();
        javax.swing.SwingWorker<Usage, Void> wk = new javax.swing.SwingWorker<>() {
            @Override
            protected Usage doInBackground() {
                return compute(p, per);
            }

            @Override
            protected void done() {
                if (this != worker) {
                    return;
                }
                worker = null;
                try {
                    usage = get();
                } catch (Exception ex) {
                    usage = new Usage(new ArrayList<>(), new String[0]);
                    ErrorLog.write(ErrorLog.describe("vytížení", ex));
                    summary.setText(Str.t("Vytížení se nepodařilo spočítat."));
                    return;
                }
                applyFilter();
            }
        };
        worker = wk;
        wk.execute();
    }

    /** Počká na dokončení výpočtu a zobrazí výsledek (testy; volat z vlákna událostí). */
    void waitForCompute() throws Exception {
        javax.swing.SwingWorker<Usage, Void> wk = worker;
        if (wk != null) {
            usage = wk.get();
            worker = null;
            applyFilter();
        }
    }

    private void applyFilter() {
        resModel.set(usage, onlyOver.isSelected());
        taskModel.set(null, false);
        detailTitle.setText("Práce vybraného zdroje po úkolech");
        if (usage == null) {
            summary.setText(" ");
            return;
        }
        int overRes = 0;
        for (int i = 0; i < usage.names.size(); i++) {
            for (int c = 0; c < usage.buckets.size(); c++) {
                if (usage.isOver(i, c)) {
                    overRes++;
                    break;
                }
            }
        }
        summary.setText(usage.names.size() + Str.t(" zdrojů · ") + usage.buckets.size() + Str.t(" období · ")
                + (overRes == 0 ? Str.t("bez přetížení") : overRes + Str.t(" přetížených zdrojů, ") + usage.overCells + Str.t(" období")));
        if (resModel.getRowCount() > 0) {
            resTable.setRowSelectionInterval(0, 0);
        }
    }

    private void showTasks() {
        int view = resTable.getSelectedRow();
        if (usage == null || view < 0 || view >= resModel.getRowCount()) {
            taskModel.set(null, false);
            return;
        }
        Object key = usage.keys.get(resModel.usageRow(view));
        if (!(key instanceof Resource r)) {
            return;
        }
        detailTitle.setText(Fmt.text("Práce zdroje „" + r.getName()) + Str.t("“ po úkolech (dvojklik přejde na úkol)"));
        Usage u = new Usage(usage.buckets, usage.headers);
        for (ResourceAssignment a : r.getTaskAssignments()) {
            Task t = a.getTask();
            if (t == null || t.getName() == null) {
                continue;
            }
            double[] w = hours(a.getTimephasedWork(usage.buckets, TimeUnit.HOURS), usage.buckets.size());
            u.names.add(Fmt.text((t.getID() == null ? "" : t.getID() + " · ") + t.getName()));
            u.detail.add(Fmt.units(a.getUnits()) + (a.getStart() == null ? "" : ", " + Fmt.date(a.getStart()) + " – " + Fmt.date(a.getFinish())));
            u.work.add(w);
            u.capacity.add(null);
            u.keys.add(t);
        }
        taskModel.set(u, false);
    }

    // ---------------------------------------------------------------- výpočet

    static Usage compute(ProjectFile p, Period period) {
        LocalDate min = null;
        LocalDate max = null;
        for (ResourceAssignment a : p.getResourceAssignments()) {
            if (a.getResource() == null || a.getTask() == null) {
                continue;
            }
            LocalDateTime s = a.getStart() != null ? a.getStart() : a.getTask().getStart();
            LocalDateTime f = a.getFinish() != null ? a.getFinish() : a.getTask().getFinish();
            if (s == null || f == null || s.getYear() < 1900 || f.getYear() > 2200) {
                continue;
            }
            LocalDate sd = s.toLocalDate();
            LocalDate fd = f.toLocalDate();
            if (min == null || sd.isBefore(min)) {
                min = sd;
            }
            if (max == null || fd.isAfter(max)) {
                max = fd;
            }
        }
        if (min == null) {
            return new Usage(new ArrayList<>(), new String[0]);
        }
        Period eff = period;
        List<LocalDateTimeRange> buckets = buckets(min, max, eff);
        while (buckets.size() > MAX_BUCKETS && eff != Period.MONTHS) {
            eff = eff == Period.DAYS ? Period.WEEKS : Period.MONTHS;
            buckets = buckets(min, max, eff);
        }
        if (buckets.size() > MAX_BUCKETS) {
            buckets = buckets.subList(0, MAX_BUCKETS);
        }
        String[] headers = new String[buckets.size()];
        DateTimeFormatter fmt = eff == Period.MONTHS ? DateTimeFormatter.ofPattern("LLL yy", Fmt.CS) : Fmt.DAY_MONTH;
        for (int i = 0; i < buckets.size(); i++) {
            headers[i] = fmt.format(buckets.get(i).getStart());
        }
        Usage u = new Usage(buckets, headers);
        ProjectCalendar defaultCal = null;
        try {
            defaultCal = p.getDefaultCalendar();
        } catch (RuntimeException ignored) {
            defaultCal = null;
        }
        for (Resource r : p.getResources()) {
            if (r.getName() == null || r.getName().isBlank()) {
                continue;
            }
            double[] work;
            try {
                work = hours(r.getTimephasedWork(buckets, TimeUnit.HOURS), buckets.size());
            } catch (RuntimeException ex) {
                work = new double[buckets.size()];
            }
            double[] cap = null;
            if (r.getType() == null || r.getType() == ResourceType.WORK) {
                ProjectCalendar cal = r.getCalendar() != null ? r.getCalendar() : defaultCal;
                double units = r.getMaxUnits() == null ? 100 : r.getMaxUnits().doubleValue();
                if (cal != null) {
                    cap = new double[buckets.size()];
                    for (int i = 0; i < buckets.size(); i++) {
                        try {
                            Duration d = cal.getWork(buckets.get(i).getStart(), buckets.get(i).getEnd(), TimeUnit.HOURS);
                            cap[i] = (d == null ? 0 : d.getDuration()) * units / 100.0;
                        } catch (RuntimeException ex) {
                            cap[i] = 0;
                        }
                    }
                }
            }
            u.names.add(Fmt.text(r.getName()));
            u.detail.add(Labels.resourceType(r.getType()) + ", " + Fmt.units(r.getMaxUnits()));
            u.work.add(work);
            u.capacity.add(cap);
            u.keys.add(r);
            int row = u.names.size() - 1;
            for (int i = 0; i < buckets.size(); i++) {
                if (u.isOver(row, i)) {
                    u.overCells++;
                }
            }
        }
        return u;
    }

    private static double[] hours(List<Duration> list, int n) {
        double[] out = new double[n];
        if (list == null) {
            return out;
        }
        for (int i = 0; i < Math.min(n, list.size()); i++) {
            Duration d = list.get(i);
            out[i] = d == null ? 0 : d.getDuration();
        }
        return out;
    }

    static List<LocalDateTimeRange> buckets(LocalDate min, LocalDate max, Period period) {
        List<LocalDateTimeRange> out = new ArrayList<>();
        LocalDate d;
        switch (period) {
            case DAYS: d = min; break;
            case WEEKS: d = min.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); break;
            default: d = min.withDayOfMonth(1);
        }
        int guard = 0;
        while (!d.isAfter(max) && guard++ < 100_000) {
            LocalDate n = period == Period.DAYS ? d.plusDays(1) : period == Period.WEEKS ? d.plusWeeks(1) : d.plusMonths(1);
            out.add(new LocalDateTimeRange(d.atStartOfDay(), n.atStartOfDay()));
            d = n;
        }
        return out;
    }
}
