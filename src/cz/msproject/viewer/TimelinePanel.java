package cz.msproject.viewer;

import org.mpxj.ProjectFile;
import org.mpxj.Task;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Časová osa pro prezentaci: souhrnné úkoly zvolené úrovně jako barevné bloky
 * v pruzích a milníky jako kosočtverce s popisky, nad tím osa měsíců.
 */
public final class TimelinePanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private static final Color[] PALETTE = {
            new Color(59, 130, 246), new Color(16, 185, 129), new Color(249, 115, 22), new Color(139, 92, 246),
            new Color(236, 72, 153), new Color(20, 184, 166), new Color(234, 179, 8), new Color(100, 116, 139)};

    private record Item(Task task, LocalDateTime start, LocalDateTime finish, boolean milestone, int lane, int colorIndex) {
    }

    private final JComboBox<String> levelBox = new JComboBox<>(new String[]{"Úroveň 1", "Úrovně 1–2", "Úrovně 1–3", "Všechny souhrny"});
    private final JCheckBox milestonesBox = new JCheckBox("Milníky", true);
    private final JCheckBox criticalBox = new JCheckBox("Jen kritické", false);
    private final JLabel summary = new JLabel(" ");
    private final Canvas canvas = new Canvas();
    private final List<Item> items = new ArrayList<>();
    private ProjectFile project;
    private TaskTree tree;
    private LocalDate origin = LocalDate.now();
    private LocalDate end = LocalDate.now().plusMonths(1);
    private int lanes = 1;
    private int milestoneRows = 1;
    private Consumer<Task> navigator;
    private Consumer<BufferedImage> exporter;

    public TimelinePanel() {
        super(new java.awt.BorderLayout());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        JLabel l = new JLabel("Souhrnné úkoly:");
        l.setFont(Theme.BASE);
        l.setForeground(Theme.MUTED);
        bar.add(l);
        levelBox.setFont(Theme.BASE);
        levelBox.addActionListener(e -> rebuild());
        bar.add(levelBox);
        milestonesBox.setFont(Theme.BASE);
        milestonesBox.addActionListener(e -> rebuild());
        bar.add(milestonesBox);
        criticalBox.setFont(Theme.BASE);
        criticalBox.addActionListener(e -> rebuild());
        bar.add(criticalBox);
        JButton png = new JButton("Uložit jako PNG…");
        png.setFont(Theme.BASE);
        png.addActionListener(e -> {
            if (exporter != null) {
                exporter.accept(renderImage(Math.max(1200, canvas.getWidth()), 2));
            }
        });
        bar.add(png);
        summary.setFont(Theme.BASE);
        summary.setForeground(Theme.MUTED);
        bar.add(summary);
        add(bar, java.awt.BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(canvas);
        sp.setBorder(null);
        add(sp, java.awt.BorderLayout.CENTER);
        JLabel hint = new JLabel("Bloky = souhrnné úkoly zvolené úrovně (dvojklik přejde na úkol v Ganttově diagramu), kosočtverce = milníky, červeně kritické.");
        hint.setFont(Theme.SMALL);
        hint.setForeground(Theme.MUTED);
        hint.setBorder(BorderFactory.createEmptyBorder(4, 12, 6, 12));
        add(hint, java.awt.BorderLayout.SOUTH);
    }

    public void setNavigator(Consumer<Task> navigator) {
        this.navigator = navigator;
    }

    public void setExporter(Consumer<BufferedImage> exporter) {
        this.exporter = exporter;
    }

    public void setProject(ProjectFile p, TaskTree t) {
        project = p;
        tree = t;
        rebuild();
    }

    int itemCount() {
        return items.size();
    }

    private int maxLevel() {
        int idx = levelBox.getSelectedIndex();
        return idx == 3 ? Integer.MAX_VALUE : idx + 1;
    }

    void rebuild() {
        items.clear();
        lanes = 1;
        milestoneRows = 1;
        if (project == null || tree == null || tree.size() == 0) {
            summary.setText(" ");
            canvas.revalidate();
            canvas.repaint();
            return;
        }
        LocalDate min = null;
        LocalDate max = null;
        List<Item> blocks = new ArrayList<>();
        List<Item> milestones = new ArrayList<>();
        int level = maxLevel();
        boolean onlyCritical = criticalBox.isSelected();
        int color = 0;
        for (TaskTree.Row r : tree.all()) {
            Task t = r.task;
            if (t.getStart() == null || t.getFinish() == null || !t.getActive()) {
                continue;
            }
            if (onlyCritical && !t.getCritical()) {
                continue;
            }
            boolean isMilestone = t.getMilestone() && !r.isSummary();
            boolean isBlock = r.isSummary() && r.depth < level;
            if (!isMilestone && !isBlock) {
                continue;
            }
            if (!milestonesBox.isSelected() && isMilestone) {
                continue;
            }
            LocalDate s = t.getStart().toLocalDate();
            LocalDate f = t.getFinish().toLocalDate();
            if (s.getYear() < 1900 || f.getYear() > 2200) {
                continue;
            }
            min = min == null || s.isBefore(min) ? s : min;
            max = max == null || f.isAfter(max) ? f : max;
            if (isMilestone) {
                milestones.add(new Item(t, t.getStart(), t.getFinish(), true, 0, 0));
            } else {
                blocks.add(new Item(t, t.getStart(), t.getFinish(), false, 0, r.depth == 0 ? color++ % PALETTE.length : (color + r.depth) % PALETTE.length));
            }
        }
        if (min == null) {
            summary.setText("Žádné souhrnné úkoly ani milníky k zobrazení.");
            canvas.revalidate();
            canvas.repaint();
            return;
        }
        long days = Math.max(1, ChronoUnit.DAYS.between(min, max));
        origin = min.minusDays(Math.max(2, days / 30));
        end = max.plusDays(Math.max(2, days / 30) + 1);
        // pruhy: bloky, které se překrývají, jdou do dalšího pruhu
        List<LocalDateTime> laneEnd = new ArrayList<>();
        for (Item b : blocks) {
            int lane = -1;
            for (int i = 0; i < laneEnd.size(); i++) {
                if (!b.start().isBefore(laneEnd.get(i))) {
                    lane = i;
                    break;
                }
            }
            if (lane < 0) {
                lane = laneEnd.size();
                laneEnd.add(b.finish());
            } else {
                laneEnd.set(lane, b.finish());
            }
            items.add(new Item(b.task(), b.start(), b.finish(), false, lane, b.colorIndex()));
        }
        lanes = Math.max(1, laneEnd.size());
        // milníky: střídat 3 řádky popisků, aby se nepřekrývaly
        milestones.sort((a, b) -> a.start().compareTo(b.start()));
        int row = 0;
        LocalDateTime lastAt = null;
        for (Item m : milestones) {
            if (lastAt != null && ChronoUnit.DAYS.between(lastAt, m.start()) * canvas.pxPerDay() < 120) {
                row = (row + 1) % 3;
            } else {
                row = 0;
            }
            lastAt = m.start();
            items.add(new Item(m.task(), m.start(), m.finish(), true, row, 0));
        }
        milestoneRows = milestones.isEmpty() ? 0 : 3;
        summary.setText(blocks.size() + " bloků · " + milestones.size() + " milníků · " + Fmt.DATE.format(min) + " – " + Fmt.DATE.format(max));
        canvas.revalidate();
        canvas.repaint();
    }

    /** Vykreslí osu do obrázku dané šířky (v px před škálováním). */
    public BufferedImage renderImage(int width, double scale) {
        int height = canvas.neededHeight();
        BufferedImage img = new BufferedImage((int) (width * scale), (int) (height * scale), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.scale(scale, scale);
        canvas.paintTimeline(g, width, height);
        g.dispose();
        return img;
    }

    private final class Canvas extends JComponent {
        private static final long serialVersionUID = 1L;
        private static final int AXIS = 34;
        private static final int LANE = 40;
        private static final int MS_ROW = 22;

        Canvas() {
            setOpaque(true);
            setToolTipText("");
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && navigator != null) {
                        Item it = itemAt(e.getX(), e.getY(), getWidth());
                        if (it != null) {
                            navigator.accept(it.task());
                        }
                    }
                }
            });
        }

        double pxPerDay() {
            int w = Math.max(200, getWidth());
            return (w - 24) / (double) Math.max(1, ChronoUnit.DAYS.between(origin, end));
        }

        int neededHeight() {
            return AXIS + lanes * LANE + 16 + milestoneRows * MS_ROW + 30;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(200, neededHeight());
        }

        private double xOf(LocalDateTime d, int width) {
            double ppd = (width - 24) / (double) Math.max(1, ChronoUnit.DAYS.between(origin, end));
            return 12 + ChronoUnit.MINUTES.between(origin.atStartOfDay(), d) / 1440.0 * ppd;
        }

        private Item itemAt(int x, int y, int width) {
            for (Item it : items) {
                if (it.milestone()) {
                    int cx = (int) xOf(it.start(), width);
                    int cy = AXIS + lanes * LANE + 16 + 8;
                    if (Math.abs(x - cx) <= 8 && Math.abs(y - cy) <= 8) {
                        return it;
                    }
                } else {
                    int a = (int) xOf(it.start(), width);
                    int b = (int) xOf(it.finish(), width);
                    int y0 = AXIS + it.lane() * LANE + 6;
                    if (x >= a && x <= b && y >= y0 && y <= y0 + LANE - 12) {
                        return it;
                    }
                }
            }
            return null;
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            Item it = itemAt(e.getX(), e.getY(), getWidth());
            if (it == null) {
                return null;
            }
            Task t = it.task();
            return "<html><b>" + Fmt.html(Fmt.text(t.getName())) + "</b><br>" + Fmt.date(t.getStart()) + " – " + Fmt.date(t.getFinish())
                    + "<br>" + Fmt.duration(t.getDuration()) + " · " + Fmt.percent(t.getPercentageComplete()) + "</html>";
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            paintTimeline(g, getWidth(), getHeight());
            g.dispose();
        }

        void paintTimeline(Graphics2D g, int width, int height) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            if (items.isEmpty()) {
                g.setFont(Theme.BASE);
                g.setColor(Theme.MUTED);
                g.drawString(project == null ? "Otevřete soubor Microsoft Project." : "Nic k zobrazení – zvolte jinou úroveň nebo zapněte milníky.", 20, 30);
                return;
            }
            // osa měsíců
            g.setFont(Theme.SMALL);
            FontMetrics fm = g.getFontMetrics();
            LocalDate m = origin.withDayOfMonth(1);
            long totalDays = ChronoUnit.DAYS.between(origin, end);
            boolean years = totalDays > 900;
            while (!m.isAfter(end)) {
                LocalDate n = years ? m.withDayOfYear(1).plusYears(1) : m.plusMonths(1);
                int x0 = (int) xOf(m.atStartOfDay(), width);
                int x1 = (int) xOf(n.atStartOfDay(), width);
                g.setColor(Theme.GRID_STRONG);
                g.drawLine(x0, 0, x0, height);
                g.setColor(Theme.HEADER_BG);
                g.fillRect(x0 + 1, 0, Math.max(0, x1 - x0 - 1), AXIS - 6);
                String label = years ? String.valueOf(m.getYear()) : (x1 - x0 > 70 ? Fmt.MONTH_YEAR.format(m) : Fmt.MONTH_SHORT.format(m));
                g.setColor(Theme.INK);
                if (fm.stringWidth(label) + 6 < x1 - x0) {
                    g.drawString(label, x0 + 4, (AXIS - 6 + fm.getAscent() - fm.getDescent()) / 2);
                }
                m = n;
            }
            g.setColor(Theme.GRID_STRONG);
            g.drawLine(0, AXIS - 6, width, AXIS - 6);
            // dnešek
            LocalDate today = LocalDate.now();
            if (!today.isBefore(origin) && !today.isAfter(end)) {
                int tx = (int) xOf(today.atStartOfDay(), width);
                g.setColor(Theme.TODAY);
                g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 4f, new float[]{4f, 4f}, 0f));
                g.drawLine(tx, AXIS - 6, tx, height);
                g.setStroke(new BasicStroke(1f));
            }
            // bloky
            g.setFont(Theme.BASE);
            fm = g.getFontMetrics();
            for (Item it : items) {
                if (it.milestone()) {
                    continue;
                }
                int a = (int) xOf(it.start(), width);
                int b = Math.max(a + 4, (int) xOf(it.finish(), width));
                int y0 = AXIS + it.lane() * LANE + 6;
                Task t = it.task();
                Color c = t.getCritical() ? Theme.CRITICAL : PALETTE[it.colorIndex() % PALETTE.length];
                g.setColor(c);
                g.fillRoundRect(a, y0, b - a, LANE - 12, 8, 8);
                double pct = t.getPercentageComplete() == null ? 0 : Math.max(0, Math.min(100, t.getPercentageComplete().doubleValue()));
                if (pct > 0) {
                    g.setColor(new Color(0, 0, 0, 60));
                    g.fillRoundRect(a, y0, (int) Math.round((b - a) * pct / 100), LANE - 12, 8, 8);
                }
                String name = Fmt.text(t.getName());
                String s = name;
                while (s.length() > 2 && fm.stringWidth(s) > b - a - 12) {
                    s = s.substring(0, s.length() - 2) + "…";
                }
                if (b - a > 30) {
                    g.setColor(Color.WHITE);
                    g.drawString(s, a + 6, y0 + (LANE - 12 + fm.getAscent() - fm.getDescent()) / 2);
                }
            }
            // milníky
            int msTop = AXIS + lanes * LANE + 16;
            g.setFont(Theme.SMALL);
            fm = g.getFontMetrics();
            for (Item it : items) {
                if (!it.milestone()) {
                    continue;
                }
                int cx = (int) xOf(it.start(), width);
                int cy = msTop + 8;
                Task t = it.task();
                g.setColor(t.getCritical() ? Theme.CRITICAL : Theme.MILESTONE);
                g.fillPolygon(new int[]{cx, cx + 7, cx, cx - 7}, new int[]{cy - 7, cy, cy + 7, cy}, 4);
                int ly = cy + 10 + it.lane() * MS_ROW + fm.getAscent();
                g.setColor(Theme.GRID_STRONG);
                g.drawLine(cx, cy + 7, cx, ly - fm.getAscent() - 2);
                g.setColor(Theme.INK);
                String label = Fmt.DAY_MONTH.format(t.getStart()) + " " + Fmt.text(t.getName());
                if (label.length() > 40) {
                    label = label.substring(0, 38) + "…";
                }
                int lx = Math.max(2, Math.min(width - fm.stringWidth(label) - 2, cx - fm.stringWidth(label) / 2));
                g.drawString(label, lx, ly);
            }
        }
    }
}
