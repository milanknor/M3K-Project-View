package cz.msproject.viewer;

import org.mpxj.Duration;
import org.mpxj.ProjectFile;
import org.mpxj.ProjectProperties;
import org.mpxj.Task;
import org.mpxj.TimeUnit;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Porovnání dvou verzí téhož harmonogramu. Úkoly se párují postupně podle GUID,
 * jedinečného ID, kombinace WBS + název a nakonec podle samotného názvu; co se
 * nespáruje, je přidané nebo odebrané. Program nic nepřepočítává – porovnávají se
 * hodnoty uložené v souborech.
 */
public final class ScheduleDiff {

    /** Co se s úkolem stalo. */
    public enum Kind {
        ADDED(Str.t("přidán")), REMOVED(Str.t("odebrán")), CHANGED(Str.t("změněn")), SAME(Str.t("beze změny"));

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    /** Jeden řádek porovnání. */
    public record Row(Kind kind, Task oldTask, Task newTask, String name, String wbs,
                      LocalDateTime oldStart, LocalDateTime newStart, LocalDateTime oldFinish, LocalDateTime newFinish,
                      long startShift, long finishShift, double durationDelta, double percentDelta, List<String> changes) {

        public String changesText() {
            return String.join(", ", changes);
        }
    }

    private ScheduleDiff() {
    }

    /** Porovná starší soubor s novějším; vrací řádky v pořadí novějšího souboru, odebrané na konci. */
    public static List<Row> compare(ProjectFile older, ProjectFile newer) {
        Map<String, Task> byGuid = new HashMap<>();
        Map<String, Task> byUnique = new HashMap<>();
        Map<String, Task> byWbsName = new HashMap<>();
        Map<String, Task> byName = new HashMap<>();
        List<Task> oldTasks = new ArrayList<>();
        for (Task t : older.getTasks()) {
            if (t == null || t.getName() == null || t.getName().isBlank()) {
                continue;
            }
            oldTasks.add(t);
            put(byGuid, t.getGUID() == null ? null : t.getGUID().toString(), t);
            put(byUnique, t.getUniqueID() == null ? null : "u" + t.getUniqueID(), t);
            put(byWbsName, key(t.getWBS(), t.getName()), t);
            put(byName, key(null, t.getName()), t);
        }
        java.util.Set<Task> matched = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        List<Row> out = new ArrayList<>();
        ProjectProperties props = newer.getProjectProperties();
        for (Task t : newer.getTasks()) {
            if (t == null || t.getName() == null || t.getName().isBlank()) {
                continue;
            }
            Task match = find(byGuid, t.getGUID() == null ? null : t.getGUID().toString(), matched);
            if (match == null) {
                match = find(byUnique, t.getUniqueID() == null ? null : "u" + t.getUniqueID(), matched);
            }
            if (match == null) {
                match = find(byWbsName, key(t.getWBS(), t.getName()), matched);
            }
            if (match == null) {
                match = find(byName, key(null, t.getName()), matched);
            }
            if (match != null) {
                matched.add(match);
                out.add(row(match, t, props));
            } else {
                out.add(new Row(Kind.ADDED, null, t, Fmt.text(t.getName()), Fmt.text(t.getWBS()),
                        null, t.getStart(), null, t.getFinish(), 0, 0, 0, 0, List.of(Str.t("nový úkol"))));
            }
        }
        for (Task t : oldTasks) {
            if (!matched.contains(t)) {
                out.add(new Row(Kind.REMOVED, t, null, Fmt.text(t.getName()), Fmt.text(t.getWBS()),
                        t.getStart(), null, t.getFinish(), null, 0, 0, 0, 0, List.of(Str.t("v nové verzi chybí"))));
            }
        }
        return out;
    }

    /** Souhrn: kolik úkolů přibylo, zmizelo, posunulo se a o kolik dní se hnul konec projektu. */
    public static String summary(List<Row> rows) {
        int added = 0;
        int removed = 0;
        int changed = 0;
        long maxShift = 0;
        for (Row r : rows) {
            switch (r.kind()) {
                case ADDED -> added++;
                case REMOVED -> removed++;
                case CHANGED -> {
                    changed++;
                    if (Math.abs(r.finishShift()) > Math.abs(maxShift)) {
                        maxShift = r.finishShift();
                    }
                }
                default -> {
                    // beze změny se do souhrnu nepočítá
                }
            }
        }
        String s = Str.t("Změněno: ") + changed + Str.t(", přidáno: ") + added + Str.t(", odebráno: ") + removed;
        if (maxShift != 0) {
            s += Str.t("  •  největší posun dokončení: ") + (maxShift > 0 ? "+" : "") + maxShift + Str.t(" dní");
        }
        return s;
    }

    private static Row row(Task a, Task b, ProjectProperties props) {
        List<String> changes = new ArrayList<>();
        long startShift = shift(a.getStart(), b.getStart());
        long finishShift = shift(a.getFinish(), b.getFinish());
        double durationDelta = days(b.getDuration(), props) - days(a.getDuration(), props);
        double percentDelta = percent(b) - percent(a);
        if (startShift != 0) {
            changes.add(Str.t("začátek ") + sign(startShift) + Str.t(" dní"));
        }
        if (finishShift != 0) {
            changes.add(Str.t("konec ") + sign(finishShift) + Str.t(" dní"));
        }
        if (Math.abs(durationDelta) > 0.01) {
            changes.add(Str.t("trvání ") + Fmt.number(days(a.getDuration(), props)) + " → " + Fmt.number(days(b.getDuration(), props)) + Str.t(" dní"));
        }
        if (Math.abs(percentDelta) > 0.01) {
            changes.add(Str.t("dokončeno ") + Fmt.number(percent(a)) + " → " + Fmt.number(percent(b)) + " %");
        }
        if (!Fmt.text(a.getName()).equals(Fmt.text(b.getName()))) {
            changes.add(Str.t("přejmenován z „") + Fmt.text(a.getName()) + "“");
        }
        if (a.getCritical() != b.getCritical()) {
            changes.add(b.getCritical() ? Str.t("nově kritický") : Str.t("už není kritický"));
        }
        double workDelta = hours(b.getWork(), props) - hours(a.getWork(), props);
        if (Math.abs(workDelta) > 0.01) {
            changes.add(Str.t("práce ") + sign(Math.round(workDelta)) + Str.t(" h"));
        }
        double costA = a.getCost() == null ? 0 : a.getCost().doubleValue();
        double costB = b.getCost() == null ? 0 : b.getCost().doubleValue();
        if (Math.abs(costB - costA) > 0.5) {
            changes.add(Str.t("náklady ") + sign(Math.round(costB - costA)));
        }
        Kind kind = changes.isEmpty() ? Kind.SAME : Kind.CHANGED;
        return new Row(kind, a, b, Fmt.text(b.getName()), Fmt.text(b.getWBS()),
                a.getStart(), b.getStart(), a.getFinish(), b.getFinish(),
                startShift, finishShift, durationDelta, percentDelta, changes);
    }

    private static String sign(long v) {
        return (v > 0 ? "+" : "") + v;
    }

    private static double percent(Task t) {
        return t.getPercentageComplete() == null ? 0 : t.getPercentageComplete().doubleValue();
    }

    private static long shift(LocalDateTime a, LocalDateTime b) {
        if (a == null || b == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(a.toLocalDate(), b.toLocalDate());
    }

    private static double days(Duration d, ProjectProperties props) {
        return convert(d, TimeUnit.DAYS, props);
    }

    private static double hours(Duration d, ProjectProperties props) {
        return convert(d, TimeUnit.HOURS, props);
    }

    private static double convert(Duration d, TimeUnit unit, ProjectProperties props) {
        if (d == null) {
            return 0;
        }
        try {
            return d.getUnits() == unit ? d.getDuration() : d.convertUnits(unit, props).getDuration();
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private static String key(String wbs, String name) {
        String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return (wbs == null || wbs.isBlank() ? "" : wbs.trim() + "|") + n;
    }

    private static void put(Map<String, Task> map, String key, Task t) {
        if (key != null && !key.isBlank()) {
            map.putIfAbsent(key, t);
        }
    }

    private static Task find(Map<String, Task> map, String key, java.util.Set<Task> used) {
        if (key == null || key.isBlank()) {
            return null;
        }
        Task t = map.get(key);
        return t == null || used.contains(t) ? null : t;
    }
}
