package cz.msproject.viewer;

import org.mpxj.CustomField;
import org.mpxj.DataType;
import org.mpxj.Duration;
import org.mpxj.ProjectFile;
import org.mpxj.ProjectProperties;
import org.mpxj.Task;
import org.mpxj.TaskField;
import org.mpxj.TaskType;
import org.mpxj.TimeUnit;

import java.text.Collator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sloupce tabulky úkolů: vestavěné (pevný seznam) a vlastní pole ze souboru
 * (Text1…30, Číslo1…20, Příznak1…20, Datum1…10, Doba trvání1…10, Náklady1…10,
 * Zahájení/Dokončení1…10, Kód osnovy1…10 vč. podnikových), která mají alias nebo
 * jsou v některém úkolu vyplněná. Každý sloupec umí text buňky a klíč pro řazení.
 */
public final class Columns {

    public enum Align { LEFT, RIGHT }

    public enum Category {
        GENERAL("Obecné"), DATES("Termíny"), WORK_COST("Práce a náklady"), BASELINE("Základní plán"),
        SLACK("Rezervy a kritická cesta"), OTHER("Ostatní"), CUSTOM("Vlastní pole");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    /** Jeden sloupec; {@code key} je stabilní identifikátor pro uložení nastavení. */
    public static final class Column {
        public final String key;
        public final String title;
        public final Category category;
        public final int width;
        public final Align align;
        private final BiFunction<TaskTree.Row, Columns, String> text;
        private final BiFunction<TaskTree.Row, Columns, Object> sortKey;

        Column(String key, String title, Category category, int width, Align align,
                BiFunction<TaskTree.Row, Columns, String> text, BiFunction<TaskTree.Row, Columns, Object> sortKey) {
            this.key = key;
            this.title = title;
            this.category = category;
            this.width = width;
            this.align = align;
            this.text = text;
            this.sortKey = sortKey;
        }

        public boolean isCustom() {
            return category == Category.CUSTOM;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private static final Collator COLLATOR = Collator.getInstance(Fmt.CS);
    private static final Pattern CUSTOM_FIELD = Pattern.compile("^(ENTERPRISE_)?(TEXT|NUMBER|FLAG|DATE|DURATION|COST|START|FINISH|OUTLINE_CODE)(\\d+)$");

    public static final Column ID;
    public static final Column NAME;
    public static final List<Column> BUILTIN;
    /** Výchozí sada sloupců (v tomto pořadí). */
    public static final List<String> DEFAULT_KEYS;

    static {
        List<Column> b = new ArrayList<>();
        ID = add(b, "id", "ID", Category.GENERAL, 46, Align.RIGHT, (r, c) -> Fmt.text(r.task.getID()), (r, c) -> r.task.getID());
        NAME = add(b, "name", "Název úkolu", Category.GENERAL, 330, Align.LEFT, (r, c) -> Fmt.text(r.task.getName()), (r, c) -> str(r.task.getName()));
        add(b, "duration", "Trvání", Category.GENERAL, 92, Align.RIGHT, (r, c) -> Fmt.duration(r.task.getDuration()), (r, c) -> c.minutes(r.task.getDuration()));
        add(b, "start", "Zahájení", Category.GENERAL, 96, Align.LEFT, (r, c) -> Fmt.date(r.task.getStart()), (r, c) -> r.task.getStart());
        add(b, "finish", "Dokončení", Category.GENERAL, 96, Align.LEFT, (r, c) -> Fmt.date(r.task.getFinish()), (r, c) -> r.task.getFinish());
        add(b, "percent", "% dokonč.", Category.GENERAL, 78, Align.RIGHT, (r, c) -> Fmt.percent(r.task.getPercentageComplete()), (r, c) -> num(r.task.getPercentageComplete()));
        add(b, "predecessors", "Předchůdci", Category.GENERAL, 110, Align.LEFT, (r, c) -> Fmt.predecessors(r.task), (r, c) -> str(Fmt.predecessors(r.task)));
        add(b, "resources", "Názvy zdrojů", Category.GENERAL, 190, Align.LEFT, (r, c) -> Fmt.resources(r.task), (r, c) -> str(Fmt.resources(r.task)));
        add(b, "work", "Práce", Category.WORK_COST, 90, Align.RIGHT, (r, c) -> Fmt.duration(r.task.getWork()), (r, c) -> c.minutes(r.task.getWork()));
        add(b, "cost", "Náklady", Category.WORK_COST, 100, Align.RIGHT, (r, c) -> Fmt.money(r.task.getCost(), c.currency), (r, c) -> num(r.task.getCost()));
        add(b, "totalSlack", "Celková rezerva", Category.SLACK, 110, Align.RIGHT, (r, c) -> Fmt.duration(r.task.getTotalSlack()), (r, c) -> c.minutes(r.task.getTotalSlack()));
        add(b, "wbs", "WBS", Category.GENERAL, 70, Align.LEFT, (r, c) -> Fmt.text(r.task.getWBS()), (r, c) -> str(r.task.getWBS()));
        DEFAULT_KEYS = List.of("id", "name", "duration", "start", "finish", "percent", "predecessors", "resources", "work", "cost", "totalSlack", "wbs");

        add(b, "uniqueId", "Jedinečné ID", Category.GENERAL, 90, Align.RIGHT, (r, c) -> Fmt.text(r.task.getUniqueID()), (r, c) -> r.task.getUniqueID());
        add(b, "outlineNumber", "Číslo osnovy", Category.GENERAL, 90, Align.LEFT, (r, c) -> Fmt.text(r.task.getOutlineNumber()), (r, c) -> str(r.task.getOutlineNumber()));
        add(b, "level", "Úroveň", Category.GENERAL, 60, Align.RIGHT, (r, c) -> String.valueOf(r.depth + 1), (r, c) -> r.depth);
        add(b, "kind", "Typ", Category.GENERAL, 110, Align.LEFT, (r, c) -> Labels.taskKind(r), (r, c) -> str(Labels.taskKind(r)));
        add(b, "successors", "Následníci", Category.GENERAL, 110, Align.LEFT, (r, c) -> Fmt.successors(r.task), (r, c) -> str(Fmt.successors(r.task)));
        add(b, "initials", "Iniciály zdrojů", Category.GENERAL, 110, Align.LEFT, (r, c) -> Fmt.text(r.task.getResourceInitials()), (r, c) -> str(r.task.getResourceInitials()));
        add(b, "priority", "Priorita", Category.GENERAL, 110, Align.LEFT, (r, c) -> Labels.priority(r.task.getPriority()),
                (r, c) -> r.task.getPriority() == null ? null : r.task.getPriority().getValue());
        add(b, "taskType", "Typ úkolu", Category.GENERAL, 120, Align.LEFT, (r, c) -> taskType(r.task.getType()), (r, c) -> str(taskType(r.task.getType())));
        add(b, "calendar", "Kalendář", Category.GENERAL, 120, Align.LEFT,
                (r, c) -> r.task.getCalendar() == null ? "" : Fmt.text(r.task.getCalendar().getName()),
                (r, c) -> r.task.getCalendar() == null ? null : str(r.task.getCalendar().getName()));
        add(b, "notes", "Poznámky", Category.GENERAL, 220, Align.LEFT, (r, c) -> firstLine(r.task.getNotes()), (r, c) -> str(firstLine(r.task.getNotes())));

        add(b, "actualStart", "Skutečné zahájení", Category.DATES, 110, Align.LEFT, (r, c) -> Fmt.date(r.task.getActualStart()), (r, c) -> r.task.getActualStart());
        add(b, "actualFinish", "Skutečné dokončení", Category.DATES, 110, Align.LEFT, (r, c) -> Fmt.date(r.task.getActualFinish()), (r, c) -> r.task.getActualFinish());
        add(b, "deadline", "Termín", Category.DATES, 96, Align.LEFT, (r, c) -> Fmt.date(r.task.getDeadline()), (r, c) -> r.task.getDeadline());
        add(b, "constraint", "Omezení", Category.DATES, 170, Align.LEFT,
                (r, c) -> Labels.constraint(r.task.getConstraintType()) + (r.task.getConstraintDate() == null ? "" : " " + Fmt.date(r.task.getConstraintDate())),
                (r, c) -> str(Labels.constraint(r.task.getConstraintType())));
        add(b, "constraintDate", "Datum omezení", Category.DATES, 100, Align.LEFT, (r, c) -> Fmt.date(r.task.getConstraintDate()), (r, c) -> r.task.getConstraintDate());
        add(b, "earlyStart", "Nejdřívější zahájení", Category.DATES, 120, Align.LEFT, (r, c) -> Fmt.date(r.task.getEarlyStart()), (r, c) -> r.task.getEarlyStart());
        add(b, "earlyFinish", "Nejdřívější dokončení", Category.DATES, 120, Align.LEFT, (r, c) -> Fmt.date(r.task.getEarlyFinish()), (r, c) -> r.task.getEarlyFinish());
        add(b, "lateStart", "Nejzazší zahájení", Category.DATES, 120, Align.LEFT, (r, c) -> Fmt.date(r.task.getLateStart()), (r, c) -> r.task.getLateStart());
        add(b, "lateFinish", "Nejzazší dokončení", Category.DATES, 120, Align.LEFT, (r, c) -> Fmt.date(r.task.getLateFinish()), (r, c) -> r.task.getLateFinish());
        add(b, "created", "Vytvořeno", Category.DATES, 100, Align.LEFT, (r, c) -> Fmt.date(r.task.getCreateDate()), (r, c) -> r.task.getCreateDate());
        add(b, "startVariance", "Odchylka zahájení", Category.DATES, 120, Align.RIGHT, (r, c) -> Fmt.duration(r.task.getStartVariance()), (r, c) -> c.minutes(r.task.getStartVariance()));
        add(b, "finishVariance", "Odchylka dokončení", Category.DATES, 120, Align.RIGHT, (r, c) -> Fmt.duration(r.task.getFinishVariance()), (r, c) -> c.minutes(r.task.getFinishVariance()));

        add(b, "actualWork", "Skutečná práce", Category.WORK_COST, 100, Align.RIGHT, (r, c) -> Fmt.duration(r.task.getActualWork()), (r, c) -> c.minutes(r.task.getActualWork()));
        add(b, "remainingWork", "Zbývající práce", Category.WORK_COST, 100, Align.RIGHT, (r, c) -> Fmt.duration(r.task.getRemainingWork()), (r, c) -> c.minutes(r.task.getRemainingWork()));
        add(b, "percentWork", "% práce", Category.WORK_COST, 70, Align.RIGHT, (r, c) -> Fmt.percent(r.task.getPercentageWorkComplete()), (r, c) -> num(r.task.getPercentageWorkComplete()));
        add(b, "actualCost", "Skutečné náklady", Category.WORK_COST, 110, Align.RIGHT, (r, c) -> Fmt.money(r.task.getActualCost(), c.currency), (r, c) -> num(r.task.getActualCost()));
        add(b, "remainingCost", "Zbývající náklady", Category.WORK_COST, 110, Align.RIGHT, (r, c) -> Fmt.money(r.task.getRemainingCost(), c.currency), (r, c) -> num(r.task.getRemainingCost()));
        add(b, "fixedCost", "Pevné náklady", Category.WORK_COST, 100, Align.RIGHT, (r, c) -> Fmt.money(r.task.getFixedCost(), c.currency), (r, c) -> num(r.task.getFixedCost()));
        add(b, "workVariance", "Odchylka práce", Category.WORK_COST, 110, Align.RIGHT, (r, c) -> Fmt.duration(r.task.getWorkVariance()), (r, c) -> c.minutes(r.task.getWorkVariance()));
        add(b, "costVariance", "Odchylka nákladů", Category.WORK_COST, 110, Align.RIGHT, (r, c) -> Fmt.money(r.task.getCostVariance(), c.currency), (r, c) -> num(r.task.getCostVariance()));

        add(b, "baselineStart", "Zahájení (zákl. plán)", Category.BASELINE, 120, Align.LEFT, (r, c) -> Fmt.date(r.task.getBaselineStart()), (r, c) -> r.task.getBaselineStart());
        add(b, "baselineFinish", "Dokončení (zákl. plán)", Category.BASELINE, 120, Align.LEFT, (r, c) -> Fmt.date(r.task.getBaselineFinish()), (r, c) -> r.task.getBaselineFinish());
        add(b, "baselineDuration", "Trvání (zákl. plán)", Category.BASELINE, 110, Align.RIGHT, (r, c) -> Fmt.duration(r.task.getBaselineDuration()), (r, c) -> c.minutes(r.task.getBaselineDuration()));
        add(b, "baselineWork", "Práce (zákl. plán)", Category.BASELINE, 110, Align.RIGHT, (r, c) -> Fmt.duration(r.task.getBaselineWork()), (r, c) -> c.minutes(r.task.getBaselineWork()));
        add(b, "baselineCost", "Náklady (zákl. plán)", Category.BASELINE, 120, Align.RIGHT, (r, c) -> Fmt.money(r.task.getBaselineCost(), c.currency), (r, c) -> num(r.task.getBaselineCost()));

        add(b, "freeSlack", "Volná rezerva", Category.SLACK, 100, Align.RIGHT, (r, c) -> Fmt.duration(r.task.getFreeSlack()), (r, c) -> c.minutes(r.task.getFreeSlack()));
        add(b, "critical", "Kritický", Category.SLACK, 70, Align.LEFT, (r, c) -> Fmt.yesNo(r.task.getCritical()), (r, c) -> r.task.getCritical() ? 1 : 0);
        add(b, "milestone", "Milník", Category.OTHER, 70, Align.LEFT, (r, c) -> Fmt.yesNo(r.task.getMilestone()), (r, c) -> r.task.getMilestone() ? 1 : 0);
        add(b, "summary", "Souhrnný", Category.OTHER, 80, Align.LEFT, (r, c) -> Fmt.yesNo(r.isSummary()), (r, c) -> r.isSummary() ? 1 : 0);
        add(b, "active", "Aktivní", Category.OTHER, 70, Align.LEFT, (r, c) -> Fmt.yesNo(r.task.getActive()), (r, c) -> r.task.getActive() ? 1 : 0);
        add(b, "estimated", "Odhad", Category.OTHER, 70, Align.LEFT, (r, c) -> Fmt.yesNo(r.task.getEstimated()), (r, c) -> r.task.getEstimated() ? 1 : 0);
        add(b, "effortDriven", "Řízený úsilím", Category.OTHER, 100, Align.LEFT, (r, c) -> Fmt.yesNo(r.task.getEffortDriven()), (r, c) -> r.task.getEffortDriven() ? 1 : 0);
        add(b, "hyperlink", "Odkaz", Category.OTHER, 160, Align.LEFT, (r, c) -> Fmt.text(r.task.getHyperlink()), (r, c) -> str(r.task.getHyperlink()));
        add(b, "contact", "Kontakt", Category.OTHER, 120, Align.LEFT, (r, c) -> Fmt.text(r.task.getContact()), (r, c) -> str(r.task.getContact()));
        add(b, "subproject", "Podprojekt", Category.OTHER, 160, Align.LEFT, (r, c) -> Fmt.text(r.task.getSubprojectFile()), (r, c) -> str(r.task.getSubprojectFile()));
        BUILTIN = Collections.unmodifiableList(b);
    }

    private final Map<String, Column> byKey = new LinkedHashMap<>();
    private final List<Column> custom = new ArrayList<>();
    private String currency = "";
    private ProjectProperties props;

    public Columns() {
        for (Column c : BUILTIN) {
            byKey.put(c.key, c);
        }
    }

    private static Column add(List<Column> list, String key, String title, Category cat, int width, Align align,
            BiFunction<TaskTree.Row, Columns, String> text, BiFunction<TaskTree.Row, Columns, Object> sortKey) {
        Column c = new Column(key, title, cat, width, align, text, sortKey);
        list.add(c);
        return c;
    }

    // ---------------------------------------------------------------- projekt

    /** Načte měnu, jednotky a vlastní pole z projektu (null = žádný projekt). */
    public void setProject(ProjectFile p) {
        byKey.keySet().removeIf(k -> k.startsWith("custom:"));
        custom.clear();
        props = p == null ? null : p.getProjectProperties();
        currency = p == null ? "" : Fmt.text(p.getProjectProperties().getCurrencySymbol());
        if (p == null) {
            return;
        }
        for (TaskField f : TaskField.values()) {
            Matcher m = CUSTOM_FIELD.matcher(f.name());
            if (!m.matches()) {
                continue;
            }
            String alias = null;
            try {
                CustomField cf = p.getCustomFields().get(f);
                alias = cf == null ? null : cf.getAlias();
            } catch (RuntimeException ignored) {
                alias = null;
            }
            boolean used = alias != null && !alias.isBlank();
            if (!used) {
                for (Task t : p.getTasks()) {
                    if (isSet(t.getCachedValue(f))) {
                        used = true;
                        break;
                    }
                }
            }
            if (!used) {
                continue;
            }
            String base = (m.group(1) == null ? "" : "Podnik. ") + czechName(m.group(2)) + m.group(3);
            String title = alias == null || alias.isBlank() ? base : alias + " (" + base + ")";
            DataType type = f.getDataType();
            Align align = type == DataType.NUMERIC || type == DataType.CURRENCY || type == DataType.DURATION ? Align.RIGHT : Align.LEFT;
            int width = type == DataType.STRING ? 150 : type == DataType.BOOLEAN ? 70 : 100;
            Column c = new Column("custom:" + f.name(), title, Category.CUSTOM, width, align,
                    (r, ctx) -> ctx.format(r.task.getCachedValue(f), type),
                    (r, ctx) -> ctx.sortValue(r.task.getCachedValue(f)));
            custom.add(c);
            byKey.put(c.key, c);
        }
    }

    private static boolean isSet(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean bool) {
            return bool;
        }
        if (v instanceof String s) {
            return !s.isBlank();
        }
        if (v instanceof Number n) {
            return Math.abs(n.doubleValue()) > 1e-9;
        }
        if (v instanceof Duration d) {
            return Math.abs(d.getDuration()) > 1e-9;
        }
        return true;
    }

    private static String czechName(String kind) {
        switch (kind) {
            case "TEXT": return "Text";
            case "NUMBER": return "Číslo";
            case "FLAG": return "Příznak";
            case "DATE": return "Datum";
            case "DURATION": return "Doba trvání";
            case "COST": return "Náklady";
            case "START": return "Zahájení";
            case "FINISH": return "Dokončení";
            case "OUTLINE_CODE": return "Kód osnovy";
            default: return kind;
        }
    }

    String format(Object v, DataType type) {
        if (v == null) {
            return "";
        }
        if (v instanceof Boolean bool) {
            return bool ? "ano" : "";
        }
        if (v instanceof LocalDateTime d) {
            return Fmt.date(d);
        }
        if (v instanceof Duration d) {
            return Fmt.duration(d);
        }
        if (v instanceof Number n) {
            return type == DataType.CURRENCY ? Fmt.money(n, currency) : Fmt.number(n.doubleValue());
        }
        return Fmt.text(v);
    }

    Object sortValue(Object v) {
        if (v instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        if (v instanceof Duration d) {
            return minutes(d);
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s) {
            return str(s);
        }
        return v;
    }

    // ---------------------------------------------------------------- přístup

    public List<Column> builtin() {
        return BUILTIN;
    }

    public List<Column> custom() {
        return Collections.unmodifiableList(custom);
    }

    public List<Column> all() {
        return new ArrayList<>(byKey.values());
    }

    public Column byKey(String key) {
        return byKey.get(key);
    }

    public String currency() {
        return currency;
    }

    public String text(Column c, TaskTree.Row r) {
        try {
            String s = c.text.apply(r, this);
            return s == null ? "" : s;
        } catch (RuntimeException ex) {
            return "";
        }
    }

    public Object sortKey(Column c, TaskTree.Row r) {
        try {
            return c.sortKey.apply(r, this);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Řazení podle sloupce; prázdné hodnoty vždy na konci. */
    public Comparator<TaskTree.Row> comparator(Column c, boolean ascending) {
        return (a, b) -> {
            Object ka = sortKey(c, a);
            Object kb = sortKey(c, b);
            if (ka == null && kb == null) {
                return 0;
            }
            if (ka == null) {
                return 1;
            }
            if (kb == null) {
                return -1;
            }
            int cmp = compareKeys(ka, kb);
            return ascending ? cmp : -cmp;
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareKeys(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return Double.compare(x.doubleValue(), y.doubleValue());
        }
        if (a instanceof Comparable && a.getClass().isInstance(b)) {
            return ((Comparable) a).compareTo(b);
        }
        return COLLATOR.compare(String.valueOf(a), String.valueOf(b));
    }

    // ---------------------------------------------------------------- pomocné

    private static Object str(String s) {
        return s == null || s.isBlank() ? null : Collator.getInstance(Fmt.CS).getCollationKey(s.strip());
    }

    private static Object num(Number n) {
        return n == null ? null : n.doubleValue();
    }

    /** Trvání převedené na minuty (klíč řazení); bez projektu se použije hrubý převod. */
    Double minutes(Duration d) {
        if (d == null) {
            return null;
        }
        try {
            if (props != null) {
                return d.convertUnits(TimeUnit.MINUTES, props).getDuration();
            }
        } catch (RuntimeException ignored) {
            // neznámé jednotky – hrubý převod níže
        }
        TimeUnit u = d.getUnits();
        double v = d.getDuration();
        if (u == null) {
            return v;
        }
        switch (u) {
            case MINUTES: case ELAPSED_MINUTES: return v;
            case HOURS: case ELAPSED_HOURS: return v * 60;
            case DAYS: case ELAPSED_DAYS: return v * 480;
            case WEEKS: case ELAPSED_WEEKS: return v * 2400;
            case MONTHS: case ELAPSED_MONTHS: return v * 9600;
            case YEARS: case ELAPSED_YEARS: return v * 115200;
            default: return v;
        }
    }

    static String taskType(TaskType t) {
        if (t == null) {
            return "";
        }
        switch (t) {
            case FIXED_UNITS: return "Pevné jednotky";
            case FIXED_DURATION: return "Pevná doba trvání";
            case FIXED_WORK: return "Pevná práce";
            case FIXED_DURATION_AND_UNITS: return "Pevná doba trvání a jednotky";
            default: return t.toString();
        }
    }

    static String firstLine(String notes) {
        if (notes == null) {
            return "";
        }
        String s = notes.strip().replace("\r", "");
        int nl = s.indexOf('\n');
        if (nl >= 0) {
            s = s.substring(0, nl) + " …";
        }
        return s.length() > 120 ? s.substring(0, 117) + "…" : s;
    }
}
