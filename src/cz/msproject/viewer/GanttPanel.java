package cz.msproject.viewer;

import org.mpxj.Duration;
import org.mpxj.ProjectCalendar;
import org.mpxj.ProjectFile;
import org.mpxj.Relation;
import org.mpxj.RelationType;
import org.mpxj.Task;
import org.mpxj.TimeUnit;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Ganttův diagram: časová osa (hlavička) + plátno s pruhy úkolů. */
public final class GanttPanel extends javax.swing.JPanel {
    private static final long serialVersionUID = 1L;

    public enum Zoom {
        DAYS(Str.t("Dny"), 42),
        WEEKS(Str.t("Týdny"), 14),
        MONTHS(Str.t("Měsíce"), 4.2),
        QUARTERS(Str.t("Čtvrtletí"), 1.4),
        YEARS(Str.t("Roky"), 0.45),
        FIT(Str.t("Přizpůsobit oknu"), 0);

        public final String label;
        public final double pxPerDay;

        Zoom(String label, double pxPerDay) {
            this.label = label;
            this.pxPerDay = pxPerDay;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private record Span(LocalDate from, LocalDate to, String label, String shortLabel) {
    }

    private static final double MIN_PX = 0.12;
    private static final double MAX_PX = 240;
    private static final int MAX_WIDTH = 2_000_000;

    private final TaskTree tree;
    private final JTable table;
    private final Header header = new Header();
    private final Chart chart = new Chart();
    private final JScrollPane scroll;
    private final Map<LocalDate, Boolean> workingCache = new HashMap<>();

    private ProjectFile project;
    private ProjectCalendar calendar;
    private LocalDate origin = LocalDate.now().minusDays(7);
    private LocalDate end = LocalDate.now().plusDays(30);
    private LocalDate today = LocalDate.now();
    private LocalDate statusDate;
    private double pxPerDay = Zoom.WEEKS.pxPerDay;
    private Zoom zoom = Zoom.FIT;
    private boolean showLinks = true;
    private boolean showBaseline = false;
    private boolean showLabels = true;
    private boolean showSlack = false;
    private boolean showToday = true;
    /** Který základní plán se kreslí (0 = hlavní, 1–10). */
    private int baselineIndex = 0;
    private Consumer<Zoom> zoomListener;

    public void setBaselineIndex(int i) {
        baselineIndex = Math.max(0, Math.min(10, i));
        chart.repaint();
    }

    public int getBaselineIndex() {
        return baselineIndex;
    }

    public GanttPanel(TaskTree tree, JTable table) {
        super(new BorderLayout());
        this.tree = tree;
        this.table = table;
        scroll = new JScrollPane(chart);
        scroll.setColumnHeaderView(header);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.getVerticalScrollBar().setUnitIncrement(Theme.ROW);
        scroll.getHorizontalScrollBar().setUnitIncrement(40);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BG);
        scroll.getColumnHeader().setBackground(Theme.HEADER_BG);
        scroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (zoom == Zoom.FIT) {
                    applyZoom();
                }
            }
        });
        add(scroll, BorderLayout.CENTER);
        setFont(Theme.BASE);
    }

    public JScrollPane getScrollPane() {
        return scroll;
    }

    public JComponent getChart() {
        return chart;
    }

    public JComponent getHeader() {
        return header;
    }

    public void setZoomListener(Consumer<Zoom> l) {
        zoomListener = l;
    }

    public double getPxPerDay() {
        return pxPerDay;
    }

    public void setShowLinks(boolean v) {
        showLinks = v;
        chart.repaint();
    }

    public void setShowBaseline(boolean v) {
        showBaseline = v;
        chart.repaint();
    }

    public void setShowLabels(boolean v) {
        showLabels = v;
        chart.repaint();
    }

    public void setShowSlack(boolean v) {
        showSlack = v;
        chart.repaint();
    }

    public void setToday(LocalDate d) {
        today = d;
    }

    public void setProject(ProjectFile p) {
        project = p;
        linkIndexDirty = true;
        workingCache.clear();
        calendar = null;
        statusDate = null;
        if (p != null) {
            try {
                calendar = p.getDefaultCalendar();
            } catch (RuntimeException ignored) {
                calendar = null;
            }
            if (p.getProjectProperties().getStatusDate() != null) {
                statusDate = p.getProjectProperties().getStatusDate().toLocalDate();
            }
        }
        computeRange();
        applyZoom();
    }

    private void computeRange() {
        LocalDate min = null;
        LocalDate max = null;
        List<LocalDateTime> dates = new ArrayList<>();
        for (TaskTree.Row r : tree.all()) {
            Task t = r.task;
            dates.add(t.getStart());
            dates.add(t.getFinish());
            for (int i = 0; i <= 10; i++) {
                dates.add(Columns.baselineStart(t, i));
                dates.add(Columns.baselineFinish(t, i));
            }
            dates.add(t.getDeadline());
        }
        if (project != null) {
            dates.add(project.getProjectProperties().getStartDate());
            dates.add(project.getProjectProperties().getFinishDate());
        }
        for (LocalDateTime d : dates) {
            if (d == null) {
                continue;
            }
            LocalDate ld = d.toLocalDate();
            if (ld.getYear() < 1900 || ld.getYear() > 2200) {
                continue;
            }
            if (min == null || ld.isBefore(min)) {
                min = ld;
            }
            if (max == null || ld.isAfter(max)) {
                max = ld;
            }
        }
        if (min == null) {
            min = today.minusDays(7);
            max = today.plusDays(30);
        }
        long days = Math.max(1, ChronoUnit.DAYS.between(min, max));
        long pad = Math.max(3, Math.round(days * 0.04));
        origin = min.minusDays(pad);
        end = max.plusDays(pad + 1);
    }

    private long totalDays() {
        return Math.max(1, ChronoUnit.DAYS.between(origin, end));
    }

    public void setZoom(Zoom z) {
        zoom = z == null ? Zoom.FIT : z;
        applyZoom();
    }

    private void applyZoom() {
        long days = totalDays();
        if (zoom == Zoom.FIT) {
            int w = Math.max(50, scroll.getViewport().getWidth() - 2);
            pxPerDay = w / (double) days;
        } else if (zoom != null) {
            pxPerDay = zoom.pxPerDay;
        }
        pxPerDay = Math.max(MIN_PX, Math.min(MAX_PX, pxPerDay));
        pxPerDay = Math.min(pxPerDay, MAX_WIDTH / (double) days);
        layoutChanged();
    }

    /** Přiblížení/oddálení kolem zadaného bodu (x v souřadnicích plátna, nebo -1 pro střed). */
    public void zoomBy(double factor, int anchorX) {
        Rectangle vis = chart.getVisibleRect();
        int anchor = anchorX < 0 ? vis.x + vis.width / 2 : anchorX;
        double anchorDays = anchor / pxPerDay;
        int anchorOffset = anchor - vis.x;
        zoom = null;
        pxPerDay = Math.max(MIN_PX, Math.min(MAX_PX, pxPerDay * factor));
        pxPerDay = Math.min(pxPerDay, MAX_WIDTH / (double) totalDays());
        layoutChanged();
        int newX = (int) Math.round(anchorDays * pxPerDay) - anchorOffset;
        Rectangle r = new Rectangle(Math.max(0, newX), vis.y, vis.width, vis.height);
        chart.scrollRectToVisible(r);
        if (zoomListener != null) {
            zoomListener.accept(null);
        }
    }

    public void layoutChanged() {
        int width = Math.max(1, (int) Math.ceil(totalDays() * pxPerDay) + 1);
        int height = Math.max(1, tree.visible().size() * Theme.ROW);
        chart.setPreferredSize(new Dimension(width, height));
        header.setPreferredSize(new Dimension(width, Theme.HEADER));
        chart.revalidate();
        header.revalidate();
        chart.repaint();
        header.repaint();
    }

    public void rowsChanged() {
        linkIndexDirty = true;
        layoutChanged();
    }

    public double x(LocalDateTime d) {
        return ChronoUnit.MINUTES.between(origin.atStartOfDay(), d) / 1440.0 * pxPerDay;
    }

    public double x(LocalDate d) {
        return ChronoUnit.DAYS.between(origin, d) * pxPerDay;
    }

    public LocalDate dateAt(int px) {
        return origin.plusDays((long) Math.floor(px / pxPerDay));
    }

    public void scrollToDate(LocalDate d) {
        Rectangle vis = chart.getVisibleRect();
        int px = (int) Math.round(x(d));
        chart.scrollRectToVisible(new Rectangle(Math.max(0, px - vis.width / 3), vis.y, vis.width, vis.height));
    }

    public void scrollToRow(int row) {
        if (row < 0 || row >= tree.visible().size()) {
            return;
        }
        Task t = tree.visible().get(row).task;
        Rectangle vis = chart.getVisibleRect();
        int px = t.getStart() == null ? vis.x : (int) Math.round(x(t.getStart()));
        chart.scrollRectToVisible(new Rectangle(Math.max(0, px - 60), row * Theme.ROW, vis.width, Theme.ROW));
    }

    // --- časové úseky hlavičky -------------------------------------------------

    private enum Scale { DAY, WEEK, MONTH, QUARTER }

    private Scale scale() {
        if (pxPerDay >= 18) {
            return Scale.DAY;
        }
        if (pxPerDay >= 5) {
            return Scale.WEEK;
        }
        if (pxPerDay >= 1.2) {
            return Scale.MONTH;
        }
        return Scale.QUARTER;
    }

    private List<Span> topSpans(LocalDate from, LocalDate to) {
        List<Span> list = new ArrayList<>();
        Scale s = scale();
        if (s == Scale.DAY || s == Scale.WEEK) {
            LocalDate d = from.withDayOfMonth(1);
            while (!d.isAfter(to)) {
                LocalDate n = d.plusMonths(1);
                list.add(new Span(d, n, Fmt.MONTH_YEAR.format(d), Fmt.MONTH_SHORT.format(d) + " " + String.valueOf(d.getYear()).substring(2)));
                d = n;
            }
        } else {
            LocalDate d = from.withDayOfYear(1);
            while (!d.isAfter(to)) {
                LocalDate n = d.plusYears(1);
                list.add(new Span(d, n, String.valueOf(d.getYear()), String.valueOf(d.getYear()).substring(2)));
                d = n;
            }
        }
        return list;
    }

    private List<Span> bottomSpans(LocalDate from, LocalDate to) {
        List<Span> list = new ArrayList<>();
        switch (scale()) {
            case DAY: {
                LocalDate d = from;
                while (!d.isAfter(to)) {
                    String num = String.valueOf(d.getDayOfMonth());
                    String wd = Fmt.WEEKDAY_SHORT.format(d);
                    wd = wd.substring(0, 1).toUpperCase(Fmt.CS) + wd.substring(1).replace(".", "");
                    list.add(new Span(d, d.plusDays(1), wd + " " + num, num));
                    d = d.plusDays(1);
                }
                break;
            }
            case WEEK: {
                LocalDate d = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                while (!d.isAfter(to)) {
                    list.add(new Span(d, d.plusWeeks(1), Fmt.DAY_MONTH.format(d), String.valueOf(d.getDayOfMonth())));
                    d = d.plusWeeks(1);
                }
                break;
            }
            case MONTH: {
                LocalDate d = from.withDayOfMonth(1);
                while (!d.isAfter(to)) {
                    String full = d.getMonth().getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, Fmt.CS);
                    list.add(new Span(d, d.plusMonths(1), full, Fmt.MONTH_SHORT.format(d).replace(".", "")));
                    d = d.plusMonths(1);
                }
                break;
            }
            default: {
                LocalDate d = from.withDayOfMonth(1).withMonth(((from.getMonthValue() - 1) / 3) * 3 + 1);
                while (!d.isAfter(to)) {
                    int q = (d.getMonthValue() - 1) / 3 + 1;
                    list.add(new Span(d, d.plusMonths(3), q + Str.t(". čtvrtletí"), "Q" + q));
                    d = d.plusMonths(3);
                }
            }
        }
        return list;
    }

    /** Omezení jiné než „co nejdříve / co nejpozději“. */
    static boolean isRealConstraint(Task t) {
        org.mpxj.ConstraintType c = t.getConstraintType();
        return c != null && c != org.mpxj.ConstraintType.AS_SOON_AS_POSSIBLE && c != org.mpxj.ConstraintType.AS_LATE_AS_POSSIBLE;
    }

    /** Části rozděleného úkolu (prázdný seznam = nerozdělený nebo neznámý). */
    static List<org.mpxj.LocalDateTimeRange> splits(Task t) {
        try {
            List<org.mpxj.LocalDateTimeRange> s = t.getWorkSplits();
            if (s == null || s.size() < 2) {
                return List.of();
            }
            List<org.mpxj.LocalDateTimeRange> ok = new ArrayList<>();
            for (org.mpxj.LocalDateTimeRange r : s) {
                if (r != null && r.getStart() != null && r.getEnd() != null && !r.getEnd().isBefore(r.getStart())) {
                    ok.add(r);
                }
            }
            return ok.size() < 2 ? List.of() : ok;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private boolean isWorking(LocalDate d) {
        Boolean w = workingCache.get(d);
        if (w == null) {
            boolean v;
            if (calendar != null) {
                try {
                    v = calendar.isWorkingDate(d);
                } catch (RuntimeException ex) {
                    v = d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY;
                }
            } else {
                v = d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY;
            }
            w = v;
            workingCache.put(d, w);
        }
        return w;
    }

    private void paintNonWorking(Graphics2D g, Rectangle clip, int y, int h) {
        if (pxPerDay < 5) {
            return;
        }
        LocalDate d = dateAt(Math.max(0, clip.x)).minusDays(1);
        LocalDate stop = dateAt(clip.x + clip.width).plusDays(1);
        g.setColor(Theme.WEEKEND);
        while (!d.isAfter(stop)) {
            if (!isWorking(d)) {
                int x0 = (int) Math.round(x(d));
                int x1 = (int) Math.round(x(d.plusDays(1)));
                g.fillRect(x0, y, Math.max(1, x1 - x0), h);
            }
            d = d.plusDays(1);
        }
    }

    private static void drawCentered(Graphics2D g, String text, int x0, int x1, int baseline, int pad) {
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text);
        int avail = x1 - x0 - 2 * pad;
        if (w > avail) {
            return;
        }
        g.drawString(text, x0 + (x1 - x0 - w) / 2, baseline);
    }

    // --- hlavička ------------------------------------------------------------------

    private final class Header extends JComponent {
        private static final long serialVersionUID = 1L;

        Header() {
            setFont(Theme.SMALL);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            Rectangle clip = g.getClipBounds();
            Rectangle vis = getVisibleRect();
            int half = Theme.HEADER / 2;
            g.setColor(Theme.HEADER_BG);
            g.fillRect(clip.x, clip.y, clip.width, clip.height);
            g.setFont(Theme.SMALL);
            LocalDate from = dateAt(Math.max(0, clip.x)).minusDays(1);
            LocalDate to = dateAt(clip.x + clip.width).plusDays(1);
            FontMetrics fm = g.getFontMetrics();
            int base1 = (half + fm.getAscent() - fm.getDescent()) / 2;
            int base2 = half + base1;

            for (Span s : topSpans(from, to)) {
                int x0 = (int) Math.round(x(s.from()));
                int x1 = (int) Math.round(x(s.to()));
                g.setColor(Theme.GRID_STRONG);
                g.drawLine(x0, 0, x0, Theme.HEADER);
                int a = Math.max(x0, vis.x);
                int b = Math.min(x1, vis.x + vis.width);
                g.setColor(Theme.INK);
                g.setFont(Theme.BOLD.deriveFont(12f));
                FontMetrics bm = g.getFontMetrics();
                if (bm.stringWidth(s.label()) + 8 <= b - a) {
                    drawCentered(g, s.label(), a, b, base1, 4);
                } else if (bm.stringWidth(s.shortLabel()) + 4 <= b - a) {
                    drawCentered(g, s.shortLabel(), a, b, base1, 2);
                }
                g.setFont(Theme.SMALL);
            }
            g.setColor(Theme.GRID_STRONG);
            g.drawLine(clip.x, half, clip.x + clip.width, half);

            Scale sc = scale();
            for (Span s : bottomSpans(from, to)) {
                int x0 = (int) Math.round(x(s.from()));
                int x1 = (int) Math.round(x(s.to()));
                if (sc == Scale.DAY && !isWorking(s.from())) {
                    g.setColor(Theme.WEEKEND);
                    g.fillRect(x0, half + 1, Math.max(1, x1 - x0), half - 1);
                }
                g.setColor(Theme.GRID);
                g.drawLine(x0, half, x0, Theme.HEADER);
                g.setColor(Theme.MUTED);
                if (sc == Scale.WEEK) {
                    if (fm.stringWidth(s.label()) + 6 <= x1 - x0) {
                        g.drawString(s.label(), x0 + 3, base2);
                    } else if (fm.stringWidth(s.shortLabel()) + 4 <= x1 - x0) {
                        g.drawString(s.shortLabel(), x0 + 2, base2);
                    }
                } else if (fm.stringWidth(s.label()) + 6 <= x1 - x0) {
                    drawCentered(g, s.label(), x0, x1, base2, 3);
                } else {
                    drawCentered(g, s.shortLabel(), x0, x1, base2, 1);
                }
            }
            g.setColor(Theme.GRID_STRONG);
            g.drawLine(clip.x, Theme.HEADER - 1, clip.x + clip.width, Theme.HEADER - 1);
            g.dispose();
        }
    }

    // --- plátno ---------------------------------------------------------------------

    private final class Chart extends JComponent implements Scrollable {
        private static final long serialVersionUID = 1L;

        Chart() {
            setOpaque(true);
            setBackground(Theme.BG);
            setFont(Theme.SMALL);
            setToolTipText("");
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int r = e.getY() / Theme.ROW;
                    if (r >= 0 && r < tree.visible().size()) {
                        table.setRowSelectionInterval(r, r);
                        table.requestFocusInWindow();
                    }
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    int r = e.getY() / Theme.ROW;
                    if (e.getClickCount() == 2 && r >= 0 && r < tree.visible().size()) {
                        TaskTree.Row row = tree.visible().get(r);
                        if (row.isSummary()) {
                            tree.toggle(row);
                            fireRowsChanged();
                        }
                    }
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    if (e.isControlDown()) {
                        double factor = e.getPreciseWheelRotation() < 0 ? 1.25 : 0.8;
                        zoomBy(factor, e.getX());
                        e.consume();
                    } else {
                        scroll.dispatchEvent(SwingUtilities.convertMouseEvent(Chart.this, e, scroll));
                    }
                }
            };
            addMouseListener(mouse);
            addMouseWheelListener(mouse);
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            int r = e.getY() / Theme.ROW;
            if (r < 0 || r >= tree.visible().size()) {
                return null;
            }
            TaskTree.Row row = tree.visible().get(r);
            Task t = row.task;
            StringBuilder sb = new StringBuilder("<html><b>").append(t.getID() == null ? "" : t.getID() + " · ")
                    .append(Fmt.html(Fmt.text(t.getName()))).append("</b>");
            if (t.getWBS() != null) {
                sb.append(" <font color='" + Theme.MUTED_HEX + "'>(WBS ").append(Fmt.html(t.getWBS())).append(")</font>");
            }
            sb.append("<br>").append(Fmt.dateTime(t.getStart())).append(" – ").append(Fmt.dateTime(t.getFinish()));
            sb.append("<br>Trvání: ").append(Fmt.duration(t.getDuration()));
            sb.append(Str.t(" · Dokončeno: ")).append(Fmt.percent(t.getPercentageComplete()));
            if (t.getWork() != null) {
                sb.append(Str.t(" · Práce: ")).append(Fmt.duration(t.getWork()));
            }
            String res = Fmt.resources(t);
            if (!res.isEmpty()) {
                sb.append("<br>Zdroje: ").append(Fmt.html(res));
            }
            String pred = Fmt.predecessors(t);
            if (!pred.isEmpty()) {
                sb.append("<br>Předchůdci: ").append(Fmt.html(pred));
            }
            if (t.getTotalSlack() != null && !row.isSummary()) {
                sb.append("<br>Celková rezerva: ").append(Fmt.duration(t.getTotalSlack()));
            }
            if (t.getDeadline() != null) {
                sb.append("<br>Termín: ").append(Fmt.date(t.getDeadline()));
            }
            if (isRealConstraint(t)) {
                sb.append("<br>Omezení: ").append(Labels.constraint(t.getConstraintType()))
                        .append(t.getConstraintDate() == null ? "" : " " + Fmt.date(t.getConstraintDate()));
            }
            List<org.mpxj.LocalDateTimeRange> splits = splits(t);
            if (splits.size() > 1) {
                sb.append("<br>Rozdělený úkol: ").append(splits.size()).append(Str.t(" částí"));
            }
            if (t.getCritical()) {
                sb.append("<br><font color='#dc2626'>Kritický úkol</font>");
            }
            if (!t.getActive()) {
                sb.append("<br><font color='#9ca3af'>Neaktivní úkol</font>");
            }
            return sb.append("</html>").toString();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            Rectangle clip = g.getClipBounds();
            g.setColor(Theme.BG);
            g.fillRect(clip.x, clip.y, clip.width, clip.height);
            List<TaskTree.Row> rows = tree.visible();
            int from = Math.max(0, clip.y / Theme.ROW);
            int to = Math.min(rows.size(), (clip.y + clip.height) / Theme.ROW + 1);
            int selected = table.getSelectedRow();

            paintNonWorking(g, clip, clip.y, clip.height);
            for (int i = from; i < to; i++) {
                if (i == selected) {
                    g.setColor(Theme.SELECTION);
                    g.fillRect(clip.x, i * Theme.ROW, clip.width, Theme.ROW);
                } else if (i % 2 == 1) {
                    g.setColor(Theme.STRIPE_ALPHA);
                    g.fillRect(clip.x, i * Theme.ROW, clip.width, Theme.ROW);
                }
                g.setColor(Theme.GRID);
                g.drawLine(clip.x, (i + 1) * Theme.ROW - 1, clip.x + clip.width, (i + 1) * Theme.ROW - 1);
            }

            LocalDate dFrom = dateAt(Math.max(0, clip.x)).minusDays(1);
            LocalDate dTo = dateAt(clip.x + clip.width).plusDays(1);
            g.setColor(Theme.GRID);
            for (Span s : bottomSpans(dFrom, dTo)) {
                int x0 = (int) Math.round(x(s.from()));
                g.drawLine(x0, clip.y, x0, clip.y + clip.height);
            }
            g.setColor(Theme.GRID_STRONG);
            for (Span s : topSpans(dFrom, dTo)) {
                int x0 = (int) Math.round(x(s.from()));
                g.drawLine(x0, clip.y, x0, clip.y + clip.height);
            }

            if (showLinks) {
                paintLinks(g, rows, from, to, clip);
            }
            for (int i = from; i < to; i++) {
                paintRow(g, rows.get(i), i);
            }

            Stroke dashed = new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 4f, new float[]{4f, 4f}, 0f);
            if (statusDate != null) {
                int sx = (int) Math.round(x(statusDate));
                g.setStroke(dashed);
                g.setColor(Theme.STATUS_DATE);
                g.drawLine(sx, clip.y, sx, clip.y + clip.height);
            }
            if (showToday) {
                int tx = (int) Math.round(x(today) + pxPerDay / 2);
                g.setStroke(dashed);
                g.setColor(Theme.TODAY);
                g.drawLine(tx, clip.y, tx, clip.y + clip.height);
            }
            if (rows.isEmpty()) {
                g.setStroke(new BasicStroke(1f));
                g.setFont(Theme.BASE);
                g.setColor(Theme.MUTED);
                g.drawString(tree.size() == 0 ? Str.t("Otevřete soubor Microsoft Project (Ctrl+O) nebo jej přetáhněte do okna.")
                        : Str.t("Filtru neodpovídá žádný úkol."), 24, 40);
            }
            g.dispose();
        }

        private void paintRow(Graphics2D g, TaskTree.Row row, int i) {
            Task t = row.task;
            int y0 = i * Theme.ROW;
            LocalDateTime s = t.getStart() != null ? t.getStart() : t.getFinish();
            LocalDateTime f = t.getFinish() != null ? t.getFinish() : t.getStart();
            g.setStroke(new BasicStroke(1f));

            LocalDateTime bs = Columns.baselineStart(t, baselineIndex);
            LocalDateTime bf = Columns.baselineFinish(t, baselineIndex);
            if (showBaseline && bs != null && bf != null) {
                int a = (int) Math.round(x(bs));
                int b = (int) Math.round(x(bf));
                g.setColor(Theme.BASELINE);
                if (t.getMilestone() || b - a < 2) {
                    g.fillPolygon(new int[]{a, a + 4, a, a - 4}, new int[]{y0 + 18, y0 + 22, y0 + 26, y0 + 22}, 4);
                } else {
                    g.fillRect(a, y0 + 20, Math.max(2, b - a), 4);
                }
            }
            if (t.getDeadline() != null) {
                int dx = (int) Math.round(x(t.getDeadline()));
                g.setColor(Theme.DEADLINE);
                g.fillPolygon(new int[]{dx - 5, dx + 5, dx}, new int[]{y0 + 5, y0 + 5, y0 + 13}, 3);
            }
            if (isRealConstraint(t) && t.getConstraintDate() != null) {
                // značka omezení: pevné (musí začít/skončit) plná, pružné (nejdříve/nejpozději) obrysová
                int cx = (int) Math.round(x(t.getConstraintDate()));
                boolean fixed = t.getConstraintType() == org.mpxj.ConstraintType.MUST_START_ON || t.getConstraintType() == org.mpxj.ConstraintType.MUST_FINISH_ON;
                g.setColor(fixed ? Theme.CONSTRAINT_FIXED : Theme.CONSTRAINT_FLEXIBLE);
                g.drawLine(cx, y0 + 20, cx, y0 + 25);
                if (fixed) {
                    g.fillRect(cx - 3, y0 + 21, 7, 4);
                } else {
                    g.drawRect(cx - 3, y0 + 21, 6, 3);
                }
            }
            if (s == null) {
                return;
            }
            int a = (int) Math.round(x(s));
            int b = (int) Math.round(x(f));
            boolean inactive = !t.getActive();
            boolean critical = t.getCritical() && !inactive;
            int labelX;

            if (t.getMilestone() || (!row.isSummary() && b - a < 1 && t.getDuration() != null && t.getDuration().getDuration() == 0)) {
                int cx = t.getMilestone() && t.getFinish() != null ? b : a;
                int cy = y0 + 13;
                g.setColor(inactive ? Theme.INACTIVE : critical ? Theme.CRITICAL : Theme.MILESTONE);
                g.fillPolygon(new int[]{cx, cx + 7, cx, cx - 7}, new int[]{cy - 7, cy, cy + 7, cy}, 4);
                labelX = cx + 10;
            } else if (row.isSummary()) {
                int w = Math.max(3, b - a);
                g.setColor(inactive ? Theme.INACTIVE : Theme.SUMMARY);
                g.fillRect(a, y0 + 8, w, 6);
                g.fillPolygon(new int[]{a, a + 6, a}, new int[]{y0 + 13, y0 + 13, y0 + 19}, 3);
                g.fillPolygon(new int[]{a + w, a + w - 6, a + w}, new int[]{y0 + 13, y0 + 13, y0 + 19}, 3);
                labelX = a + w + 8;
            } else {
                int w = Math.max(3, b - a);
                int h = 13;
                int y = y0 + 6;
                Color strong = critical ? Theme.CRITICAL : Theme.TASK;
                Color light = critical ? Theme.CRITICAL_LIGHT : Theme.TASK_LIGHT;
                List<org.mpxj.LocalDateTimeRange> splits = inactive ? List.of() : splits(t);
                if (inactive) {
                    g.setColor(Theme.INACTIVE);
                    g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 4f, new float[]{3f, 3f}, 0f));
                    g.drawRoundRect(a, y, w, h, 3, 3);
                    g.setStroke(new BasicStroke(1f));
                } else if (splits.size() > 1) {
                    // rozdělený úkol: každá část jako pruh, mezery tečkovaně; průběh se rozloží podle délky částí
                    double pct = t.getPercentageComplete() == null ? 0 : Math.max(0, Math.min(100, t.getPercentageComplete().doubleValue()));
                    int totalW = 0;
                    int[] xs = new int[splits.size() * 2];
                    for (int k = 0; k < splits.size(); k++) {
                        xs[2 * k] = (int) Math.round(x(splits.get(k).getStart()));
                        xs[2 * k + 1] = Math.max(xs[2 * k] + 2, (int) Math.round(x(splits.get(k).getEnd())));
                        totalW += xs[2 * k + 1] - xs[2 * k];
                    }
                    int remaining = (int) Math.round(totalW * pct / 100.0);
                    g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 4f, new float[]{2f, 3f}, 0f));
                    for (int k = 0; k < splits.size(); k++) {
                        int sa = xs[2 * k];
                        int sw = xs[2 * k + 1] - sa;
                        if (k > 0) {
                            g.setColor(strong);
                            g.drawLine(xs[2 * k - 1], y + h / 2, sa, y + h / 2);
                        }
                        g.setStroke(new BasicStroke(1f));
                        g.setColor(light);
                        g.fillRoundRect(sa, y, sw, h, 3, 3);
                        int pw = Math.min(sw, remaining);
                        if (pw > 0) {
                            g.setColor(strong);
                            g.fillRoundRect(sa, y, pw, h, 3, 3);
                            remaining -= pw;
                        }
                        g.setColor(strong);
                        g.drawRoundRect(sa, y, sw, h, 3, 3);
                        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 4f, new float[]{2f, 3f}, 0f));
                    }
                    g.setStroke(new BasicStroke(1f));
                    w = Math.max(w, xs[xs.length - 1] - a);
                } else {
                    g.setColor(light);
                    g.fillRoundRect(a, y, w, h, 3, 3);
                    double pct = t.getPercentageComplete() == null ? 0 : Math.max(0, Math.min(100, t.getPercentageComplete().doubleValue()));
                    int pw = (int) Math.round(w * pct / 100.0);
                    if (pw > 0) {
                        g.setColor(strong);
                        g.fillRoundRect(a, y, pw, h, 3, 3);
                    }
                    g.setColor(strong);
                    g.drawRoundRect(a, y, w, h, 3, 3);
                }
                labelX = a + w + 8;
                if (showSlack && t.getTotalSlack() != null && project != null) {
                    try {
                        Duration slack = t.getTotalSlack().convertUnits(TimeUnit.DAYS, project.getProjectProperties());
                        double days = slack.getDuration();
                        if (days > 0.01) {
                            int sx = (int) Math.round(days * pxPerDay);
                            g.setColor(Theme.MUTED);
                            g.drawLine(a + w, y + h + 1, a + w + sx, y + h + 1);
                            g.drawLine(a + w + sx, y + h - 2, a + w + sx, y + h + 4);
                            labelX = Math.max(labelX, a + w + sx + 8);
                        }
                    } catch (RuntimeException ignored) {
                        // neznámé jednotky rezervy – bez zobrazení
                    }
                }
            }
            if (showLabels) {
                String label = Fmt.resources(t);
                if (!label.isEmpty()) {
                    g.setFont(Theme.SMALL);
                    g.setColor(inactive ? Theme.INACTIVE : Theme.MUTED);
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(label, labelX, y0 + (Theme.ROW + fm.getAscent() - fm.getDescent()) / 2 - 1);
                }
            }
        }

        private void paintLinks(Graphics2D g, List<TaskTree.Row> rows, int from, int to, Rectangle clip) {
            Map<Integer, Integer> index = linkIndex(rows);
            int selected = table.getSelectedRow();
            // vazby vybraného úkolu se kreslí až nakonec, zvýrazněně
            for (int pass = 0; pass < 2; pass++) {
                boolean highlightPass = pass == 1;
                g.setStroke(new BasicStroke(highlightPass ? 2.2f : 1.1f));
                for (int i = 0; i < rows.size(); i++) {
                    Task t = rows.get(i).task;
                    if (t.getStart() == null || t.getFinish() == null) {
                        continue;
                    }
                    for (Relation rel : t.getPredecessors()) {
                        Task p = rel.getPredecessorTask();
                        if (p == null || p.getStart() == null || p.getFinish() == null) {
                            continue;
                        }
                        Integer j = index.get(p.getUniqueID() != null ? p.getUniqueID() : System.identityHashCode(p));
                        if (j == null) {
                            continue;
                        }
                        boolean highlighted = selected >= 0 && (i == selected || j == selected);
                        if (highlighted != highlightPass) {
                            continue;
                        }
                        if ((i < from && j < from) || (i >= to && j >= to)) {
                            continue;
                        }
                        RelationType type = rel.getType() == null ? RelationType.FINISH_START : rel.getType();
                        boolean fromStart = type == RelationType.START_START || type == RelationType.START_FINISH;
                        boolean toFinish = type == RelationType.FINISH_FINISH || type == RelationType.START_FINISH;
                        int sx = (int) Math.round(x(fromStart ? p.getStart() : p.getFinish()));
                        int tx = (int) Math.round(x(toFinish ? t.getFinish() : t.getStart()));
                        int sy = j * Theme.ROW + 13;
                        int ty = i * Theme.ROW + 13;
                        g.setColor(highlighted ? Theme.HIGHLIGHT : t.getCritical() && p.getCritical() ? Theme.CRITICAL : Theme.LINK);
                        paintLink(g, sx, sy, tx, ty, fromStart, toFinish);
                    }
                }
            }
        }

        private void paintLink(Graphics2D g, int sx, int sy, int tx, int ty, boolean fromStart, boolean toFinish) {
            int yBound = ty > sy ? ty - Theme.ROW / 2 + 1 : ty + Theme.ROW / 2 - 1;
            if (!fromStart && !toFinish) {
                int mx = sx + 8;
                if (mx <= tx - 3) {
                    polyline(g, sx, sy, mx, sy, mx, ty, tx, ty);
                } else {
                    polyline(g, sx, sy, mx, sy, mx, yBound, tx - 8, yBound, tx - 8, ty, tx, ty);
                }
                arrow(g, tx, ty, true);
            } else if (fromStart && !toFinish) {
                int mx = Math.min(sx, tx) - 8;
                polyline(g, sx, sy, mx, sy, mx, ty, tx, ty);
                arrow(g, tx, ty, true);
            } else if (!fromStart) {
                int mx = Math.max(sx, tx) + 8;
                polyline(g, sx, sy, mx, sy, mx, ty, tx, ty);
                arrow(g, tx, ty, false);
            } else {
                int mx = sx - 8;
                if (mx >= tx + 3) {
                    polyline(g, sx, sy, mx, sy, mx, ty, tx, ty);
                } else {
                    polyline(g, sx, sy, mx, sy, mx, yBound, tx + 8, yBound, tx + 8, ty, tx, ty);
                }
                arrow(g, tx, ty, false);
            }
        }

        private void polyline(Graphics2D g, int... pts) {
            for (int k = 0; k + 3 < pts.length; k += 2) {
                g.drawLine(pts[k], pts[k + 1], pts[k + 2], pts[k + 3]);
            }
        }

        private void arrow(Graphics2D g, int x, int y, boolean pointingRight) {
            int d = pointingRight ? -1 : 1;
            g.fillPolygon(new int[]{x, x + d * 6, x + d * 6}, new int[]{y, y - 4, y + 4}, 3);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(600, 400);
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? Theme.ROW : 40;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? Math.max(Theme.ROW, r.height - Theme.ROW) : Math.max(40, r.width - 40);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return zoom == Zoom.FIT;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private Runnable rowsChangedCallback;
    private Map<Integer, Integer> linkIndexCache;
    private List<TaskTree.Row> linkIndexRows;
    private int linkIndexSize = -1;

    /** Mapa klíč úkolu → index viditelného řádku; přepočítá se jen po změně viditelných řádků. */
    private Map<Integer, Integer> linkIndex(List<TaskTree.Row> rows) {
        if (linkIndexCache == null || linkIndexRows != rows || linkIndexSize != rows.size() || linkIndexDirty) {
            Map<Integer, Integer> index = new HashMap<>(rows.size() * 2);
            for (int i = 0; i < rows.size(); i++) {
                index.put(rows.get(i).key(), i);
            }
            linkIndexCache = index;
            linkIndexRows = rows;
            linkIndexSize = rows.size();
            linkIndexDirty = false;
        }
        return linkIndexCache;
    }

    private boolean linkIndexDirty = true;

    public void setRowsChangedCallback(Runnable r) {
        rowsChangedCallback = r;
    }

    private void fireRowsChanged() {
        if (rowsChangedCallback != null) {
            rowsChangedCallback.run();
        }
    }

    /** Vykreslí celý diagram (hlavička + plátno) do obrázku. */
    public java.awt.image.BufferedImage renderImage() {
        return renderImage(1.0);
    }

    /** Vykreslí celý diagram v zadaném měřítku (2 = dvojnásobné rozlišení pro tisk/PDF). */
    public java.awt.image.BufferedImage renderImage(double scale) {
        Dimension d = chart.getPreferredSize();
        int w = Math.max(1, d.width);
        int h = Math.max(1, d.height) + Theme.HEADER;
        int iw = (int) Math.ceil(w * scale);
        int ih = (int) Math.ceil(h * scale);
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(iw, ih, java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Theme.BG);
        g.fillRect(0, 0, iw, ih);
        g.scale(scale, scale);
        Rectangle savedH = header.getBounds();
        Rectangle savedC = chart.getBounds();
        header.setBounds(0, 0, w, Theme.HEADER);
        chart.setBounds(0, 0, w, h - Theme.HEADER);
        g.setClip(0, 0, w, Theme.HEADER);
        header.paint(g);
        g.translate(0, Theme.HEADER);
        g.setClip(0, 0, w, h - Theme.HEADER);
        chart.paint(g);
        g.dispose();
        header.setBounds(savedH);
        chart.setBounds(savedC);
        return img;
    }

}
