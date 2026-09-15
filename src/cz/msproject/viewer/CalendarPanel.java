package cz.msproject.viewer;

import org.mpxj.LocalTimeRange;
import org.mpxj.ProjectCalendar;
import org.mpxj.ProjectCalendarException;
import org.mpxj.ProjectCalendarHours;
import org.mpxj.ProjectFile;
import org.mpxj.Resource;
import org.mpxj.Task;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;

/**
 * Záložka Kalendáře: seznam kalendářů projektu, pracovní týden (hodiny po dnech),
 * výjimky (svátky, mimořádné směny) a měsíční mřížka s nepracovními dny.
 */
public final class CalendarPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> list = new JList<>(listModel);
    private final List<ProjectCalendar> calendars = new ArrayList<>();
    private final DefaultTableModel weekModel = Ui.readOnlyModel(Str.t("Den"), Str.t("Typ"), Str.t("Pracovní doba"), Str.t("Hodin"));
    private final DefaultTableModel exceptionModel = Ui.readOnlyModel(Str.t("Název"), Str.t("Od"), Str.t("Do"), Str.t("Pracovní"), Str.t("Pracovní doba"));
    private final JTable weekTable = new JTable(weekModel);
    private final JTable exceptionTable = new JTable(exceptionModel);
    private final JLabel title = new JLabel(" ");
    private final JLabel monthLabel = new JLabel(" ", SwingConstants.CENTER);
    private final MonthGrid grid = new MonthGrid();
    private ProjectFile project;
    private ProjectCalendar current;
    private YearMonth month = YearMonth.now();

    public CalendarPanel() {
        super(new BorderLayout());
        list.setFont(Theme.BASE);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showCalendar(list.getSelectedIndex());
            }
        });
        JScrollPane left = new JScrollPane(list);
        left.setMinimumSize(new Dimension(180, 100));
        left.setPreferredSize(new Dimension(260, 100));

        for (JTable t : new JTable[]{weekTable, exceptionTable}) {
            Ui.styleTable(t);
        }
        weekTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        weekTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        weekTable.getColumnModel().getColumn(2).setPreferredWidth(220);
        weekTable.getColumnModel().getColumn(3).setPreferredWidth(60);
        exceptionTable.getColumnModel().getColumn(0).setPreferredWidth(200);

        title.setFont(Theme.TITLE.deriveFont(15f));
        title.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));

        JPanel week = new JPanel(new BorderLayout());
        week.add(Ui.section(Str.t("Pracovní týden")), BorderLayout.NORTH);
        week.add(new JScrollPane(weekTable), BorderLayout.CENTER);
        JPanel exceptions = new JPanel(new BorderLayout());
        exceptions.add(Ui.section(Str.t("Výjimky (svátky, mimořádné dny)")), BorderLayout.NORTH);
        exceptions.add(new JScrollPane(exceptionTable), BorderLayout.CENTER);
        JSplitPane tables = new JSplitPane(JSplitPane.VERTICAL_SPLIT, week, exceptions);
        tables.setResizeWeight(0.45);
        tables.setBorder(null);

        JPanel monthPanel = new JPanel(new BorderLayout());
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        JButton prev = new JButton("<");
        JButton next = new JButton(">");
        JButton today = new JButton(Str.t("Dnes"));
        prev.addActionListener(e -> setMonth(month.minusMonths(1)));
        next.addActionListener(e -> setMonth(month.plusMonths(1)));
        today.addActionListener(e -> setMonth(YearMonth.now()));
        monthLabel.setFont(Theme.BOLD);
        monthLabel.setPreferredSize(new Dimension(160, 24));
        nav.add(prev);
        nav.add(monthLabel);
        nav.add(next);
        nav.add(today);
        monthPanel.add(Ui.section(Str.t("Měsíc – šedě nepracovní dny, oranžově výjimky, modře dnešek")), BorderLayout.NORTH);
        monthPanel.add(nav, BorderLayout.SOUTH);
        monthPanel.add(grid, BorderLayout.CENTER);
        // navigace nahoře, mřížka pod ní
        monthPanel.remove(nav);
        JPanel monthTop = new JPanel(new BorderLayout());
        monthTop.add(Ui.section(Str.t("Měsíc – šedě nepracovní dny, oranžově výjimky, modře dnešek")), BorderLayout.NORTH);
        monthTop.add(nav, BorderLayout.CENTER);
        monthPanel.removeAll();
        monthPanel.add(monthTop, BorderLayout.NORTH);
        monthPanel.add(grid, BorderLayout.CENTER);

        JSplitPane right = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tables, monthPanel);
        right.setResizeWeight(0.55);
        right.setBorder(null);
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(title, BorderLayout.NORTH);
        rightPanel.add(right, BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, rightPanel);
        split.setBorder(null);
        split.setDividerLocation(260);
        add(split, BorderLayout.CENTER);
    }

    public void setProject(ProjectFile p) {
        project = p;
        calendars.clear();
        listModel.clear();
        current = null;
        if (p == null) {
            title.setText(" ");
            weekModel.setRowCount(0);
            exceptionModel.setRowCount(0);
            grid.repaint();
            return;
        }
        ProjectCalendar def = null;
        try {
            def = p.getDefaultCalendar();
        } catch (RuntimeException ignored) {
            def = null;
        }
        int selected = 0;
        for (ProjectCalendar c : p.getCalendars()) {
            if (c.getName() == null || c.getName().isBlank()) {
                continue;
            }
            String label = c.getName();
            if (c == def) {
                label += Str.t("   (výchozí)");
                selected = calendars.size();
            } else if (c.isDerived() && c.getParent() != null) {
                label += "   ← " + c.getParent().getName();
            }
            int users = usersOf(p, c);
            if (users > 0) {
                label += "   [" + users + "]";
            }
            calendars.add(c);
            listModel.addElement(label);
        }
        LocalDate start = p.getProjectProperties().getStartDate() == null ? LocalDate.now() : p.getProjectProperties().getStartDate().toLocalDate();
        month = YearMonth.from(start);
        if (!calendars.isEmpty()) {
            list.setSelectedIndex(selected);
        } else {
            title.setText("Projekt neobsahuje žádný kalendář.");
        }
    }

    /** Kolik úkolů a zdrojů kalendář používá. */
    private static int usersOf(ProjectFile p, ProjectCalendar c) {
        int n = 0;
        for (Task t : p.getTasks()) {
            if (t.getCalendar() == c) {
                n++;
            }
        }
        for (Resource r : p.getResources()) {
            if (r.getCalendar() == c) {
                n++;
            }
        }
        return n;
    }

    public JList<String> calendarList() {
        return list;
    }

    ProjectCalendar current() {
        return current;
    }

    private void setMonth(YearMonth m) {
        month = m;
        monthLabel.setText(m.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, Fmt.CS) + " " + m.getYear());
        grid.repaint();
    }

    private void showCalendar(int idx) {
        weekModel.setRowCount(0);
        exceptionModel.setRowCount(0);
        current = idx >= 0 && idx < calendars.size() ? calendars.get(idx) : null;
        if (current == null) {
            title.setText(" ");
            grid.repaint();
            return;
        }
        ProjectCalendar c = current;
        StringBuilder t = new StringBuilder(Fmt.text(c.getName()));
        if (c.isDerived() && c.getParent() != null) {
            t.append(Str.t("   —   odvozený z „")).append(c.getParent().getName()).append("“");
        }
        if (c.getMinutesPerDay() != null) {
            t.append("   —   ").append(Fmt.number(c.getMinutesPerDay() / 60.0)).append(Str.t(" h/den"));
        }
        title.setText(t.toString());
        double weekHours = 0;
        for (DayOfWeek d : DayOfWeek.values()) {
            String type;
            try {
                switch (c.getDayType(d)) {
                    case WORKING: type = Str.t("pracovní"); break;
                    case NON_WORKING: type = Str.t("nepracovní"); break;
                    default: type = c.isWorkingDay(d) ? Str.t("pracovní (výchozí)") : Str.t("nepracovní (výchozí)");
                }
            } catch (RuntimeException ex) {
                type = c.isWorkingDay(d) ? Str.t("pracovní") : Str.t("nepracovní");
            }
            ProjectCalendarHours hours = null;
            try {
                hours = c.getHours(d);
            } catch (RuntimeException ignored) {
                hours = null;
            }
            double h = hoursOf(hours);
            weekHours += h;
            weekModel.addRow(new Object[]{d.getDisplayName(TextStyle.FULL_STANDALONE, Fmt.CS), type, ranges(hours), h == 0 ? "" : Fmt.number(h)});
        }
        weekModel.addRow(new Object[]{Str.t("Týden celkem"), "", "", Fmt.number(weekHours)});
        List<ProjectCalendarException> exceptions;
        try {
            exceptions = c.getExpandedCalendarExceptions();
        } catch (RuntimeException ex) {
            exceptions = c.getCalendarExceptions();
        }
        int shown = 0;
        for (ProjectCalendarException ex : exceptions) {
            if (ex.getFromDate() == null) {
                continue;
            }
            ProjectCalendarHours hours = null;
            try {
                hours = c.getHours(ex.getFromDate());
            } catch (RuntimeException ignored) {
                hours = null;
            }
            exceptionModel.addRow(new Object[]{Fmt.orDash(ex.getName()), Fmt.DATE.format(ex.getFromDate()),
                    ex.getToDate() == null ? "" : Fmt.DATE.format(ex.getToDate()), ex.getWorking() ? Str.t("ano") : Str.t("ne"), ex.getWorking() ? ranges(hours) : "—"});
            if (++shown >= 2000) {
                break;
            }
        }
        setMonth(month);
    }

    private static double hoursOf(ProjectCalendarHours hours) {
        if (hours == null) {
            return 0;
        }
        double sum = 0;
        for (LocalTimeRange r : hours) {
            if (r == null || r.getStart() == null || r.getEnd() == null) {
                continue;
            }
            long minutes = java.time.Duration.between(r.getStart(), r.getEnd()).toMinutes();
            if (minutes <= 0) {
                minutes += 24 * 60; // směna přes půlnoc (konec 0:00)
            }
            sum += minutes / 60.0;
        }
        return sum;
    }

    private static String ranges(ProjectCalendarHours hours) {
        if (hours == null || hours.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (LocalTimeRange r : hours) {
            if (r == null || r.getStart() == null || r.getEnd() == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            LocalTime end = r.getEnd();
            sb.append(TIME.format(r.getStart())).append("–").append(end.equals(LocalTime.MIDNIGHT) ? "24:00" : TIME.format(end));
        }
        return sb.toString();
    }

    private boolean isException(LocalDate d) {
        if (current == null) {
            return false;
        }
        try {
            for (ProjectCalendarException ex : current.getExpandedCalendarExceptions()) {
                if (ex.contains(d)) {
                    return true;
                }
            }
            ProjectCalendar p = current.getParent();
            int guard = 0;
            while (p != null && guard++ < 10) {
                for (ProjectCalendarException ex : p.getExpandedCalendarExceptions()) {
                    if (ex.contains(d)) {
                        return true;
                    }
                }
                p = p.getParent();
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return false;
    }

    private String exceptionName(LocalDate d) {
        if (current == null) {
            return null;
        }
        try {
            ProjectCalendar c = current;
            int guard = 0;
            while (c != null && guard++ < 10) {
                for (ProjectCalendarException ex : c.getExpandedCalendarExceptions()) {
                    if (ex.contains(d)) {
                        return ex.getName();
                    }
                }
                c = c.getParent();
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    /** Mřížka jednoho měsíce. */
    private final class MonthGrid extends JComponent {
        private static final long serialVersionUID = 1L;

        MonthGrid() {
            setOpaque(true);
            setBackground(Theme.BG);
            setToolTipText("");
            setPreferredSize(new Dimension(320, 260));
        }

        private LocalDate dateAt(int x, int y) {
            int w = getWidth();
            int h = getHeight();
            int cw = Math.max(1, w / 7);
            int header = 22;
            int ch = Math.max(1, (h - header) / 6);
            int col = x / cw;
            int row = (y - header) / ch;
            if (col < 0 || col > 6 || row < 0 || row > 5) {
                return null;
            }
            LocalDate first = month.atDay(1);
            int offset = first.getDayOfWeek().getValue() - 1;
            return first.minusDays(offset).plusDays(row * 7 + col);
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            LocalDate d = dateAt(e.getX(), e.getY());
            if (d == null || current == null) {
                return null;
            }
            boolean working;
            try {
                working = current.isWorkingDate(d);
            } catch (RuntimeException ex) {
                working = d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY;
            }
            String name = exceptionName(d);
            String hours = "";
            try {
                hours = working ? ranges(current.getHours(d)) : "";
            } catch (RuntimeException ignored) {
                hours = "";
            }
            return "<html><b>" + Fmt.DATE.format(d) + "</b> " + d.getDayOfWeek().getDisplayName(TextStyle.FULL, Fmt.CS)
                    + "<br>" + (working ? Str.t("pracovní den") + (hours.isEmpty() ? "" : ": " + hours) : Str.t("nepracovní den"))
                    + (name == null || name.isBlank() ? "" : "<br>Výjimka: " + Fmt.html(name)) + "</html>";
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g.setColor(Theme.BG);
            g.fillRect(0, 0, w, h);
            int cw = Math.max(1, w / 7);
            int header = 22;
            int ch = Math.max(1, (h - header) / 6);
            g.setFont(Theme.SMALL.deriveFont(Font.BOLD));
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i < 7; i++) {
                String name = DayOfWeek.of(i + 1).getDisplayName(TextStyle.SHORT_STANDALONE, Fmt.CS);
                g.setColor(Theme.HEADER_BG);
                g.fillRect(i * cw, 0, cw, header);
                g.setColor(Theme.MUTED);
                g.drawString(name, i * cw + (cw - fm.stringWidth(name)) / 2, (header + fm.getAscent() - fm.getDescent()) / 2);
            }
            if (current == null) {
                g.dispose();
                return;
            }
            LocalDate first = month.atDay(1);
            int offset = first.getDayOfWeek().getValue() - 1;
            LocalDate d = first.minusDays(offset);
            LocalDate today = LocalDate.now();
            g.setFont(Theme.BASE);
            fm = g.getFontMetrics();
            for (int row = 0; row < 6; row++) {
                for (int col = 0; col < 7; col++) {
                    int x = col * cw;
                    int y = header + row * ch;
                    boolean inMonth = YearMonth.from(d).equals(month);
                    boolean working;
                    try {
                        working = current.isWorkingDate(d);
                    } catch (RuntimeException ex) {
                        working = d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY;
                    }
                    boolean exception = isException(d);
                    Color bg = working ? Theme.BG : Theme.WEEKEND;
                    if (exception) {
                        bg = working ? new Color(255, 237, 213) : new Color(254, 215, 170);
                    }
                    g.setColor(bg);
                    g.fillRect(x, y, cw, ch);
                    g.setColor(Theme.GRID);
                    g.drawRect(x, y, cw, ch);
                    if (d.equals(today)) {
                        g.setColor(Theme.STATUS_DATE);
                        g.drawRect(x + 1, y + 1, cw - 3, ch - 3);
                    }
                    g.setColor(!inMonth ? Theme.INACTIVE : working ? Theme.INK : Theme.MUTED);
                    g.drawString(String.valueOf(d.getDayOfMonth()), x + 6, y + fm.getAscent() + 4);
                    if (exception && inMonth) {
                        String name = exceptionName(d);
                        if (name != null && !name.isBlank()) {
                            g.setFont(Theme.SMALL);
                            FontMetrics sm = g.getFontMetrics();
                            String s = name;
                            while (s.length() > 2 && sm.stringWidth(s) > cw - 10) {
                                s = s.substring(0, s.length() - 2) + "…";
                            }
                            g.setColor(new Color(154, 52, 18));
                            g.drawString(s, x + 6, y + ch - 6);
                            g.setFont(Theme.BASE);
                        }
                    }
                    d = d.plusDays(1);
                }
            }
            g.dispose();
        }
    }
}
