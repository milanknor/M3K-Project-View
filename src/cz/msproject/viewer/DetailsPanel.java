package cz.msproject.viewer;

import org.mpxj.ProjectFile;
import org.mpxj.Relation;
import org.mpxj.ResourceAssignment;
import org.mpxj.Task;

import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Spodní panel s podrobnostmi vybraného úkolu. */
public final class DetailsPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JTabbedPane tabs = new JTabbedPane();
    private final JEditorPane general = new JEditorPane("text/html", "");
    private final DefaultTableModel assignModel = readOnly("Zdroj", "Typ", "Jednotky", "Práce", "Zahájení", "Dokončení", "% práce", "Náklady");
    private final DefaultTableModel linkModel = readOnly("Směr", "ID", "Název úkolu", "Typ vazby", "Prodleva", "Zahájení", "Dokončení");
    private final JTable assignTable = new JTable(assignModel);
    private final JTable linkTable = new JTable(linkModel);
    private final JTextArea notes = new JTextArea();
    private final List<Task> linkTasks = new ArrayList<>();
    private String currency = "";
    private Consumer<Task> navigator;
    private Consumer<String> subprojectOpener;
    private Columns columns;

    /** Volá se s cestou k vloženému podprojektu po kliknutí na odkaz v detailu. */
    public void setSubprojectOpener(Consumer<String> opener) {
        subprojectOpener = opener;
    }

    public DetailsPanel() {
        super(new BorderLayout());
        general.setEditable(false);
        general.setFont(Theme.BASE);
        general.addHyperlinkListener(e -> {
            if (e.getEventType() == javax.swing.event.HyperlinkEvent.EventType.ACTIVATED && subprojectOpener != null
                    && e.getDescription() != null && e.getDescription().startsWith("subproject:")) {
                subprojectOpener.accept(e.getDescription().substring("subproject:".length()));
            }
        });
        general.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        general.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        notes.setEditable(false);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setFont(Theme.BASE);
        notes.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        for (JTable t : new JTable[]{assignTable, linkTable}) {
            t.setFont(Theme.BASE);
            t.setRowHeight(24);
            t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            t.setAutoCreateRowSorter(false);
            t.getTableHeader().setReorderingAllowed(false);
            t.setShowVerticalLines(false);
            t.setGridColor(Theme.GRID);
            t.setFillsViewportHeight(true);
        }
        linkTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && navigator != null) {
                    int r = linkTable.getSelectedRow();
                    if (r >= 0 && r < linkTasks.size()) {
                        navigator.accept(linkTasks.get(r));
                    }
                }
            }
        });
        tabs.setFont(Theme.BASE);
        tabs.addTab("Obecné", new JScrollPane(general));
        tabs.addTab("Zdroje", new JScrollPane(assignTable));
        tabs.addTab("Vazby", new JScrollPane(linkTable));
        tabs.addTab("Poznámky", new JScrollPane(notes));
        add(tabs, BorderLayout.CENTER);
        show(null);
    }

    private static DefaultTableModel readOnly(String... cols) {
        return new DefaultTableModel(cols, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    public void setNavigator(Consumer<Task> navigator) {
        this.navigator = navigator;
    }

    public void setProject(ProjectFile p) {
        currency = p == null ? "" : Fmt.text(p.getProjectProperties().getCurrencySymbol());
    }

    /** Definice sloupců – kvůli vlastním polím (Text1…, Číslo1…) v záložce Obecné. */
    public void setColumns(Columns c) {
        columns = c;
    }

    public JTabbedPane tabs() {
        return tabs;
    }

    public void show(TaskTree.Row row) {
        assignModel.setRowCount(0);
        linkModel.setRowCount(0);
        linkTasks.clear();
        if (row == null) {
            general.setText("<html><body style='font-family:Segoe UI;font-size:10pt;color:#64748b'>"
                    + "Vyberte úkol v tabulce nebo v diagramu. Souhrnné úkoly sbalíte a rozbalíte kliknutím na šipku, "
                    + "dvojklikem nebo mezerníkem.</body></html>");
            notes.setText("");
            tabs.setTitleAt(1, "Zdroje");
            tabs.setTitleAt(2, "Vazby");
            tabs.setTitleAt(3, "Poznámky");
            return;
        }
        Task t = row.task;
        StringBuilder h = new StringBuilder();
        h.append("<html><body style='font-family:Segoe UI;font-size:10pt;color:#1e293b'>");
        h.append("<div style='font-size:12pt;font-weight:bold;margin-bottom:4px'>")
                .append(t.getID() == null ? "" : t.getID() + " · ").append(Fmt.html(Fmt.text(t.getName()))).append("</div>");
        h.append("<table cellspacing='0' cellpadding='2'>");
        h.append("<tr>");
        cell(h, "Typ", Labels.taskKind(row));
        cell(h, "WBS", Fmt.orDash(t.getWBS()));
        cell(h, "Osnova", Fmt.orDash(t.getOutlineNumber()));
        cell(h, "Jedinečné ID", Fmt.text(t.getUniqueID()));
        h.append("</tr><tr>");
        cell(h, "Zahájení", Fmt.dateTime(t.getStart()));
        cell(h, "Dokončení", Fmt.dateTime(t.getFinish()));
        cell(h, "Trvání", Fmt.duration(t.getDuration()));
        cell(h, "Dokončeno", Fmt.percent(t.getPercentageComplete()));
        h.append("</tr><tr>");
        cell(h, "Skutečné zahájení", Fmt.date(t.getActualStart()));
        cell(h, "Skutečné dokončení", Fmt.date(t.getActualFinish()));
        cell(h, "Práce", Fmt.duration(t.getWork()));
        cell(h, "Práce dokončena", Fmt.percent(t.getPercentageWorkComplete()));
        h.append("</tr><tr>");
        cell(h, "Základní plán", t.getBaselineStart() == null ? "—" : Fmt.date(t.getBaselineStart()) + " – " + Fmt.date(t.getBaselineFinish()));
        cell(h, "Trvání (zákl. plán)", Fmt.duration(t.getBaselineDuration()));
        cell(h, "Náklady", Fmt.money(t.getCost(), currency));
        cell(h, "Termín (deadline)", Fmt.date(t.getDeadline()));
        h.append("</tr><tr>");
        cell(h, "Omezení", Labels.constraint(t.getConstraintType()) + (t.getConstraintDate() == null ? "" : " (" + Fmt.date(t.getConstraintDate()) + ")"));
        cell(h, "Celková rezerva", Fmt.duration(t.getTotalSlack()));
        cell(h, "Volná rezerva", Fmt.duration(t.getFreeSlack()));
        cell(h, "Kritický", Fmt.yesNo(t.getCritical()));
        h.append("</tr><tr>");
        cell(h, "Priorita", Labels.priority(t.getPriority()));
        cell(h, "Kalendář", t.getCalendar() == null ? "—" : Fmt.orDash(t.getCalendar().getName()));
        cell(h, "Předchůdci", Fmt.orDash(Fmt.predecessors(t)));
        cell(h, "Následníci", Fmt.orDash(Fmt.successors(t)));
        h.append("</tr>");
        if (t.getHyperlink() != null || t.getContact() != null) {
            h.append("<tr>");
            cell(h, "Odkaz", Fmt.orDash(t.getHyperlink()));
            cell(h, "Kontakt", Fmt.orDash(t.getContact()));
            h.append("</tr>");
        }
        if (t.getSubprojectFile() != null && !t.getSubprojectFile().isBlank()) {
            h.append("<tr><td style='color:#64748b;padding-right:4px'>Podprojekt:</td><td colspan='7'><b>")
                    .append(Fmt.html(t.getSubprojectFile())).append("</b> &nbsp; <a href='subproject:")
                    .append(Fmt.html(t.getSubprojectFile())).append("'>otevřít v novém okně</a>")
                    .append(t.getSubprojectReadOnly() ? " <span style='color:#64748b'>(jen pro čtení)</span>" : "").append("</td></tr>");
        }
        if (columns != null && columns.baselines().size() > 1) {
            int n = 0;
            for (int i : columns.baselines()) {
                if (i == 0) {
                    continue;
                }
                java.time.LocalDateTime bs = Columns.baselineStart(t, i);
                java.time.LocalDateTime bf = Columns.baselineFinish(t, i);
                if (bs == null && bf == null) {
                    continue;
                }
                if (n % 2 == 0) {
                    h.append(n == 0 ? "<tr>" : "</tr><tr>");
                }
                cell(h, Columns.baselineName(i), Fmt.date(bs) + " – " + Fmt.date(bf) + " (" + Fmt.duration(Columns.baselineDuration(t, i)) + ")");
                cell(h, "Práce / náklady (ZP " + i + ")", Fmt.duration(Columns.baselineWork(t, i)) + " / " + Fmt.money(Columns.baselineCost(t, i), currency));
                n++;
            }
            if (n > 0) {
                h.append("</tr>");
            }
        }
        h.append("</table>");
        if (columns != null && !columns.custom().isEmpty()) {
            StringBuilder cf = new StringBuilder();
            int n = 0;
            for (Columns.Column c : columns.custom()) {
                String v = columns.text(c, row);
                if (v.isEmpty()) {
                    continue;
                }
                if (n % 4 == 0) {
                    cf.append(n == 0 ? "<tr>" : "</tr><tr>");
                }
                cell(cf, c.title, v);
                n++;
            }
            if (n > 0) {
                h.append("<div style='color:#64748b;margin-top:6px'>Vlastní pole</div><table cellspacing='0' cellpadding='2'>")
                        .append(cf).append("</tr></table>");
            }
        }
        h.append("</body></html>");
        general.setText(h.toString());
        general.setCaretPosition(0);

        int assigned = 0;
        for (ResourceAssignment a : t.getResourceAssignments()) {
            if (a.getResource() == null) {
                continue;
            }
            assigned++;
            assignModel.addRow(new Object[]{
                    Fmt.text(a.getResource().getName()),
                    Labels.resourceType(a.getResource().getType()),
                    Fmt.units(a.getUnits()),
                    Fmt.duration(a.getWork()),
                    Fmt.date(a.getStart()),
                    Fmt.date(a.getFinish()),
                    Fmt.percent(a.getPercentageWorkComplete()),
                    Fmt.money(a.getCost(), currency)});
        }
        int links = 0;
        for (Relation r : t.getPredecessors()) {
            Task p = r.getPredecessorTask();
            links++;
            linkTasks.add(p);
            linkModel.addRow(new Object[]{"Předchůdce", p == null ? "?" : Fmt.text(p.getID()), p == null ? "" : Fmt.text(p.getName()),
                    Fmt.relationType(r.getType()) + " – " + Fmt.relationTypeLong(r.getType()), lag(r),
                    p == null ? "" : Fmt.date(p.getStart()), p == null ? "" : Fmt.date(p.getFinish())});
        }
        for (Relation r : t.getSuccessors()) {
            Task s = r.getSuccessorTask();
            links++;
            linkTasks.add(s);
            linkModel.addRow(new Object[]{"Následník", s == null ? "?" : Fmt.text(s.getID()), s == null ? "" : Fmt.text(s.getName()),
                    Fmt.relationType(r.getType()) + " – " + Fmt.relationTypeLong(r.getType()), lag(r),
                    s == null ? "" : Fmt.date(s.getStart()), s == null ? "" : Fmt.date(s.getFinish())});
        }
        String n = Fmt.text(t.getNotes()).strip();
        notes.setText(n);
        notes.setCaretPosition(0);
        tabs.setTitleAt(1, assigned == 0 ? "Zdroje" : "Zdroje (" + assigned + ")");
        tabs.setTitleAt(2, links == 0 ? "Vazby" : "Vazby (" + links + ")");
        tabs.setTitleAt(3, n.isEmpty() ? "Poznámky" : "Poznámky ●");
    }

    private static String lag(Relation r) {
        if (r.getLag() == null || Math.abs(r.getLag().getDuration()) < 1e-9) {
            return "—";
        }
        return Fmt.duration(r.getLag());
    }

    private static void cell(StringBuilder h, String label, String value) {
        h.append("<td style='color:#64748b;padding-right:4px'>").append(label).append(":</td>")
                .append("<td style='padding-right:18px'><b>").append(Fmt.html(value)).append("</b></td>");
    }
}
