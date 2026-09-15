package cz.msproject.viewer;

import org.mpxj.ResourceAssignment;
import org.mpxj.Task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Rozšířený filtr úkolů: zdroj, období (úkol zasahuje do rozmezí), % dokončení,
 * text ve zvoleném sloupci. Prázdné položky se neuplatní. Lze uložit pod názvem
 * (serializace do jednoho řádku pro Preferences).
 */
public record TaskFilter(String name, String resource, LocalDate from, LocalDate to, Integer pctMin, Integer pctMax,
        String columnKey, String text, boolean leafOnly) {

    public static final TaskFilter EMPTY = new TaskFilter("", "", null, null, null, null, "", "", false);

    public boolean isEmpty() {
        return (resource == null || resource.isBlank()) && from == null && to == null && pctMin == null && pctMax == null
                && (text == null || text.isBlank()) && !leafOnly;
    }

    /** Stručný popis aktivních podmínek pro stavový řádek. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        if (resource != null && !resource.isBlank()) {
            sb.append("zdroj „").append(resource).append("“");
        }
        if (from != null || to != null) {
            sb.append(sb.length() > 0 ? ", " : "").append("období ").append(from == null ? "…" : Fmt.DATE.format(from))
                    .append(" – ").append(to == null ? "…" : Fmt.DATE.format(to));
        }
        if (pctMin != null || pctMax != null) {
            sb.append(sb.length() > 0 ? ", " : "").append("dokončeno ").append(pctMin == null ? 0 : pctMin).append("–").append(pctMax == null ? 100 : pctMax).append(" %");
        }
        if (text != null && !text.isBlank()) {
            sb.append(sb.length() > 0 ? ", " : "").append("text „").append(text).append("“");
        }
        if (leafOnly) {
            sb.append(sb.length() > 0 ? ", " : "").append("bez souhrnů");
        }
        return sb.toString();
    }

    public Predicate<TaskTree.Row> predicate(Columns columns) {
        if (isEmpty()) {
            return null;
        }
        String res = resource == null ? "" : resource.strip().toLowerCase(Locale.ROOT);
        String txt = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        Columns.Column col = columnKey == null || columnKey.isBlank() ? null : columns.byKey(columnKey);
        LocalDateTime fromDt = from == null ? null : from.atStartOfDay();
        LocalDateTime toDt = to == null ? null : to.plusDays(1).atStartOfDay();
        return r -> {
            Task t = r.task;
            if (leafOnly && r.isSummary()) {
                return false;
            }
            if (!res.isEmpty()) {
                boolean found = false;
                for (ResourceAssignment a : t.getResourceAssignments()) {
                    if (a.getResource() != null && a.getResource().getName() != null
                            && a.getResource().getName().toLowerCase(Locale.ROOT).contains(res)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            if (fromDt != null || toDt != null) {
                LocalDateTime s = t.getStart();
                LocalDateTime f = t.getFinish();
                if (s == null && f == null) {
                    return false;
                }
                if (s == null) {
                    s = f;
                }
                if (f == null) {
                    f = s;
                }
                if (toDt != null && !s.isBefore(toDt)) {
                    return false;
                }
                if (fromDt != null && f.isBefore(fromDt)) {
                    return false;
                }
            }
            if (pctMin != null || pctMax != null) {
                double pct = t.getPercentageComplete() == null ? 0 : t.getPercentageComplete().doubleValue();
                if (pctMin != null && pct < pctMin) {
                    return false;
                }
                if (pctMax != null && pct > pctMax) {
                    return false;
                }
            }
            if (!txt.isEmpty()) {
                String hay;
                if (col != null) {
                    hay = columns.text(col, r);
                } else {
                    hay = Fmt.text(t.getName()) + " " + Fmt.text(t.getNotes()) + " " + Fmt.text(t.getWBS());
                }
                if (!hay.toLowerCase(Locale.ROOT).contains(txt)) {
                    return false;
                }
            }
            return true;
        };
    }

    // ---------------------------------------------------------------- uložení

    public String serialize() {
        return String.join("\t", esc(name), esc(resource), from == null ? "" : from.toString(), to == null ? "" : to.toString(),
                pctMin == null ? "" : String.valueOf(pctMin), pctMax == null ? "" : String.valueOf(pctMax), esc(columnKey), esc(text),
                leafOnly ? "1" : "0");
    }

    public static TaskFilter deserialize(String line) {
        String[] p = line.split("\t", -1);
        if (p.length < 9) {
            return null;
        }
        try {
            return new TaskFilter(p[0], p[1], p[2].isEmpty() ? null : LocalDate.parse(p[2]), p[3].isEmpty() ? null : LocalDate.parse(p[3]),
                    p[4].isEmpty() ? null : Integer.valueOf(p[4]), p[5].isEmpty() ? null : Integer.valueOf(p[5]), p[6], p[7], "1".equals(p[8]));
        } catch (DateTimeParseException | NumberFormatException ex) {
            return null;
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ');
    }
}
