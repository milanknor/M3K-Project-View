package cz.msproject.viewer;

import org.mpxj.ProjectFile;
import org.mpxj.Resource;
import org.mpxj.ResourceAssignment;
import org.mpxj.Task;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Seznam zdrojů a jejich přiřazení. */
public final class ResourcePanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private static final String[] RES_COLS = {Str.t("ID"), Str.t("Název zdroje"), Str.t("Typ"), Str.t("Iniciály"), Str.t("Skupina"), Str.t("Max. jednotky"), Str.t("Standardní sazba"),
            Str.t("Přesčasová sazba"), Str.t("Náklady na použití"), Str.t("Práce"), Str.t("Náklady"), Str.t("Přetížen"), Str.t("E-mail")};
    private static final int[] RES_WIDTHS = {44, 220, 90, 60, 110, 90, 120, 120, 110, 100, 110, 70, 180};
    private static final String[] ASG_COLS = {Str.t("ID úkolu"), Str.t("Úkol"), Str.t("Jednotky"), Str.t("Práce"), Str.t("Zahájení"), Str.t("Dokončení"), Str.t("% práce"), Str.t("Náklady")};

    private final DefaultTableModel resModel = new DefaultTableModel(RES_COLS, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final DefaultTableModel asgModel = new DefaultTableModel(ASG_COLS, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable resTable = new JTable(resModel);
    private final JTable asgTable = new JTable(asgModel);
    private final JLabel summary = new JLabel(" ");
    private final JLabel asgTitle = new JLabel(Str.t("Přiřazení vybraného zdroje"));
    private final List<Resource> resources = new ArrayList<>();
    private final List<Task> asgTasks = new ArrayList<>();
    private String currency = "";
    private Consumer<Task> navigator;

    public ResourcePanel() {
        super(new BorderLayout());
        for (JTable t : new JTable[]{resTable, asgTable}) {
            t.setFont(Theme.BASE);
            t.setRowHeight(24);
            t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            t.getTableHeader().setReorderingAllowed(false);
            t.setShowVerticalLines(false);
            t.setGridColor(Theme.GRID);
            t.setFillsViewportHeight(true);
        }
        resTable.setAutoCreateRowSorter(true);
        DefaultTableCellRenderer right = new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.RIGHT);
                return this;
            }
        };
        for (int i = 0; i < RES_COLS.length; i++) {
            resTable.getColumnModel().getColumn(i).setPreferredWidth(RES_WIDTHS[i]);
            if (i == 0 || (i >= 5 && i <= 10)) {
                resTable.getColumnModel().getColumn(i).setCellRenderer(right);
            }
        }
        int[] asgWidths = {70, 320, 90, 100, 100, 100, 80, 110};
        for (int i = 0; i < ASG_COLS.length; i++) {
            asgTable.getColumnModel().getColumn(i).setPreferredWidth(asgWidths[i]);
            if (i == 0 || i == 2 || i == 3 || i == 6 || i == 7) {
                asgTable.getColumnModel().getColumn(i).setCellRenderer(right);
            }
        }
        resTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showAssignments();
            }
        });
        asgTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && navigator != null) {
                    int r = asgTable.getSelectedRow();
                    if (r >= 0 && r < asgTasks.size()) {
                        navigator.accept(asgTasks.get(r));
                    }
                }
            }
        });
        summary.setFont(Theme.BASE);
        summary.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        asgTitle.setFont(Theme.BOLD);
        asgTitle.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        JPanel top = new JPanel(new BorderLayout());
        top.add(summary, BorderLayout.NORTH);
        top.add(new JScrollPane(resTable), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(asgTitle, BorderLayout.NORTH);
        bottom.add(new JScrollPane(asgTable), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        split.setResizeWeight(0.6);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);
    }

    public void setNavigator(Consumer<Task> navigator) {
        this.navigator = navigator;
    }

    public JTable table() {
        return resTable;
    }

    public void setProject(ProjectFile p) {
        resModel.setRowCount(0);
        asgModel.setRowCount(0);
        resources.clear();
        asgTasks.clear();
        currency = p == null ? "" : Fmt.text(p.getProjectProperties().getCurrencySymbol());
        if (p == null) {
            summary.setText(" ");
            return;
        }
        int over = 0;
        for (Resource r : p.getResources()) {
            if (r.getName() == null || r.getName().isBlank()) {
                continue;
            }
            resources.add(r);
            if (r.getOverAllocated()) {
                over++;
            }
            resModel.addRow(new Object[]{
                    Fmt.text(r.getID()),
                    Fmt.text(r.getName()),
                    Labels.resourceType(r.getType()),
                    Fmt.text(r.getInitials()),
                    Fmt.text(r.getGroup()),
                    Fmt.units(r.getMaxUnits()),
                    Fmt.rate(r.getStandardRate(), currency),
                    Fmt.rate(r.getOvertimeRate(), currency),
                    Fmt.money(r.getCostPerUse(), currency),
                    Fmt.duration(r.getWork()),
                    Fmt.money(r.getCost(), currency),
                    r.getOverAllocated() ? Str.t("ano") : "",
                    Fmt.text(r.getEmailAddress())});
        }
        summary.setText(resources.isEmpty() ? Str.t("Projekt neobsahuje žádné zdroje.")
                : resources.size() + Str.t(" zdrojů") + (over > 0 ? "   •   " + over + Str.t(" přetížených") : "")
                + Str.t("   •   Dvojklik na přiřazení přejde na úkol v Ganttově diagramu."));
        showAssignments();
    }

    private void showAssignments() {
        asgModel.setRowCount(0);
        asgTasks.clear();
        int view = resTable.getSelectedRow();
        if (view < 0) {
            asgTitle.setText("Přiřazení vybraného zdroje");
            return;
        }
        int idx = resTable.convertRowIndexToModel(view);
        if (idx < 0 || idx >= resources.size()) {
            return;
        }
        Resource r = resources.get(idx);
        asgTitle.setText(Fmt.plain("Přiřazení zdroje: " + r.getName()));
        for (ResourceAssignment a : r.getTaskAssignments()) {
            Task t = a.getTask();
            if (t == null || t.getName() == null) {
                continue;
            }
            asgTasks.add(t);
            asgModel.addRow(new Object[]{
                    Fmt.text(t.getID()),
                    Fmt.text(t.getName()),
                    Fmt.units(a.getUnits()),
                    Fmt.duration(a.getWork()),
                    Fmt.date(a.getStart()),
                    Fmt.date(a.getFinish()),
                    Fmt.percent(a.getPercentageWorkComplete()),
                    Fmt.money(a.getCost(), currency)});
        }
    }
}
