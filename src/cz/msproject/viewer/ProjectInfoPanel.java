package cz.msproject.viewer;

import org.mpxj.ProjectCalendar;
import org.mpxj.ProjectFile;
import org.mpxj.ProjectProperties;
import org.mpxj.Task;

import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.nio.file.Files;
import java.nio.file.Path;

/** Přehled vlastností otevřeného projektu. */
public final class ProjectInfoPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JEditorPane pane = new JEditorPane("text/html", "");

    public ProjectInfoPanel() {
        super(new BorderLayout());
        pane.setEditable(false);
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setFont(Theme.BASE);
        pane.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        add(new JScrollPane(pane), BorderLayout.CENTER);
        setProject(null, null, null);
    }

    public void setProject(ProjectFile p, Path path, TaskTree tree) {
        if (p == null) {
            pane.setText("<html><body style='" + Theme.HTML_MUTED + "'>Není otevřen žádný projekt.</body></html>");
            return;
        }
        ProjectProperties pp = p.getProjectProperties();
        String currency = Fmt.text(pp.getCurrencySymbol());
        StringBuilder h = new StringBuilder("<html><body style='" + Theme.HTML_BODY + "'>");
        String title = firstNonBlank(pp.getProjectTitle(), pp.getName(), path == null ? null : path.getFileName().toString());
        h.append("<div style='font-size:14pt;font-weight:bold;margin-bottom:8px'>").append(Fmt.html(title)).append("</div>");

        section(h, Str.t("Soubor"));
        row(h, Str.t("Cesta"), path == null ? "—" : path.toAbsolutePath().toString());
        String size = "—";
        if (path != null) {
            try {
                size = Fmt.number(Files.size(path) / 1024.0) + " KB";
            } catch (Exception ignored) {
                size = "—";
            }
        }
        row(h, Str.t("Velikost"), size);
        String format = Fmt.orDash(pp.getFileType());
        if (pp.getMppFileType() != null) {
            format += " (MPP" + pp.getMppFileType() + mppVersion(pp.getMppFileType()) + ")";
        }
        row(h, Str.t("Formát"), format);
        row(h, Str.t("Aplikace"), Fmt.orDash(firstNonBlank(pp.getFullApplicationName(), pp.getFileApplication()))
                + (pp.getApplicationVersion() == null ? "" : Str.t(" · verze ") + pp.getApplicationVersion()));
        row(h, Str.t("Vytvořeno"), Fmt.dateTime(pp.getCreationDate()));
        row(h, Str.t("Naposledy uloženo"), Fmt.dateTime(pp.getLastSaved()));

        section(h, Str.t("Projekt"));
        row(h, Str.t("Název"), Fmt.orDash(pp.getName()));
        row(h, Str.t("Titul"), Fmt.orDash(pp.getProjectTitle()));
        row(h, Str.t("Předmět"), Fmt.orDash(pp.getSubject()));
        row(h, Str.t("Autor"), Fmt.orDash(pp.getAuthor()));
        row(h, Str.t("Manažer"), Fmt.orDash(pp.getManager()));
        row(h, Str.t("Společnost"), Fmt.orDash(pp.getCompany()));
        row(h, Str.t("Kategorie"), Fmt.orDash(pp.getCategory()));
        row(h, Str.t("Klíčová slova"), Fmt.orDash(pp.getKeywords()));
        row(h, Str.t("Komentář"), Fmt.orDash(pp.getComments()));

        section(h, Str.t("Harmonogram"));
        row(h, Str.t("Zahájení projektu"), Fmt.dateTime(pp.getStartDate()));
        row(h, Str.t("Dokončení projektu"), Fmt.dateTime(pp.getFinishDate()));
        row(h, Str.t("Datum stavu"), Fmt.dateTime(pp.getStatusDate()));
        row(h, Str.t("Aktuální datum"), Fmt.dateTime(pp.getCurrentDate()));
        row(h, Str.t("Plánovat od"), pp.getScheduleFrom() == null ? "—" : (pp.getScheduleFrom().toString().equals("START") ? Str.t("data zahájení") : Str.t("data dokončení")));
        row(h, Str.t("Trvání"), Fmt.duration(pp.getDuration()));
        row(h, Str.t("Práce"), Fmt.duration(pp.getWork()));
        row(h, Str.t("Náklady"), Fmt.money(pp.getCost(), currency));
        row(h, Str.t("Dokončeno"), Fmt.percent(pp.getPercentageComplete()));
        row(h, Str.t("Základní plán uložen"), Fmt.dateTime(pp.getBaselineDate()));
        ProjectCalendar cal = null;
        try {
            cal = p.getDefaultCalendar();
        } catch (RuntimeException ignored) {
            cal = null;
        }
        row(h, Str.t("Výchozí kalendář"), cal == null ? "—" : Fmt.orDash(cal.getName()));
        row(h, Str.t("Hodin denně / týdně"), (pp.getMinutesPerDay() == null ? "—" : Fmt.number(pp.getMinutesPerDay() / 60.0))
                + " / " + (pp.getMinutesPerWeek() == null ? "—" : Fmt.number(pp.getMinutesPerWeek() / 60.0)));
        row(h, Str.t("Dnů v měsíci"), pp.getDaysPerMonth() == null ? "—" : String.valueOf(pp.getDaysPerMonth()));
        row(h, Str.t("Měna"), Fmt.orDash(firstNonBlank(pp.getCurrencyCode(), pp.getCurrencySymbol())));

        section(h, Str.t("Statistika"));
        int tasks = 0;
        int milestones = 0;
        int summaries = 0;
        int critical = 0;
        int done = 0;
        int inactive = 0;
        if (tree != null) {
            for (TaskTree.Row r : tree.all()) {
                Task t = r.task;
                tasks++;
                if (r.isSummary()) {
                    summaries++;
                } else if (t.getMilestone()) {
                    milestones++;
                }
                if (t.getCritical()) {
                    critical++;
                }
                if (t.getPercentageComplete() != null && t.getPercentageComplete().doubleValue() >= 100) {
                    done++;
                }
                if (!t.getActive()) {
                    inactive++;
                }
            }
        }
        row(h, Str.t("Úkolů celkem"), String.valueOf(tasks));
        row(h, Str.t("z toho souhrnných"), String.valueOf(summaries));
        row(h, Str.t("z toho milníků"), String.valueOf(milestones));
        row(h, Str.t("Kritických"), String.valueOf(critical));
        row(h, Str.t("Dokončených"), String.valueOf(done));
        row(h, Str.t("Neaktivních"), String.valueOf(inactive));
        int res = 0;
        for (org.mpxj.Resource r : p.getResources()) {
            if (r.getName() != null && !r.getName().isBlank()) {
                res++;
            }
        }
        row(h, Str.t("Zdrojů"), String.valueOf(res));
        row(h, Str.t("Přiřazení"), String.valueOf(p.getResourceAssignments().size()));
        StringBuilder cals = new StringBuilder();
        for (ProjectCalendar c : p.getCalendars()) {
            if (c.getName() == null) {
                continue;
            }
            if (cals.length() > 0) {
                cals.append(", ");
            }
            cals.append(c.getName());
        }
        row(h, Str.t("Kalendáře"), Fmt.orDash(cals.toString()));
        StringBuilder subs = new StringBuilder();
        for (Task t : p.getTasks()) {
            if (t.getSubprojectFile() != null && !t.getSubprojectFile().isBlank()) {
                subs.append(subs.length() > 0 ? "<br>" : "").append(Fmt.html(Fmt.text(t.getID()))).append(" · ")
                        .append(Fmt.html(Fmt.text(t.getName()))).append(" → ").append(Fmt.html(t.getSubprojectFile()));
            }
        }
        if (subs.length() > 0) {
            section(h, Str.t("Vložené podprojekty (otevřou se samostatně přes odkaz v detailu úkolu)"));
            h.append("<tr><td colspan='2'>").append(subs).append("</td></tr>");
        }
        h.append("</table></body></html>");
        pane.setText(h.toString());
        pane.setCaretPosition(0);
    }

    private static String mppVersion(Integer type) {
        switch (type) {
            case 8: return Str.t(" – Project 98");
            case 9: return " – Project 2000/2002";
            case 12: return " – Project 2003/2007";
            case 14: return Str.t(" – Project 2010 a novější");
            default: return "";
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private boolean tableOpen;

    private void section(StringBuilder h, String title) {
        if (tableOpen) {
            h.append("</table>");
        }
        h.append("<div style='font-size:11pt;font-weight:bold;color:" + Theme.ACCENT_HEX + ";margin-top:10px;margin-bottom:2px'>")
                .append(title).append("</div><table cellspacing='0' cellpadding='2'>");
        tableOpen = true;
    }

    private static void row(StringBuilder h, String label, String value) {
        h.append("<tr><td style='color:" + Theme.MUTED_HEX + ";padding-right:14px;white-space:nowrap'>").append(label)
                .append("</td><td>").append(Fmt.html(value)).append("</td></tr>");
    }
}
