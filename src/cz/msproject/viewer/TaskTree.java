package cz.msproject.viewer;

import org.mpxj.ProjectFile;
import org.mpxj.Task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Hierarchie úkolů, sbalování souhrnů a filtrování viditelných řádků. */
public final class TaskTree {

    public enum Filter {
        ALL(Str.t("Všechny úkoly")),
        CRITICAL(Str.t("Kritické úkoly")),
        MILESTONES(Str.t("Milníky")),
        INCOMPLETE(Str.t("Nedokončené")),
        IN_PROGRESS(Str.t("Rozpracované")),
        COMPLETED(Str.t("Dokončené")),
        LATE(Str.t("Zpožděné (po termínu)")),
        THIS_WEEK(Str.t("Probíhá tento týden")),
        NEXT_3_WEEKS(Str.t("Nejbližší tři týdny")),
        SUMMARY(Str.t("Jen souhrnné úkoly")),
        WITH_RESOURCES(Str.t("S přiřazenými zdroji")),
        INACTIVE(Str.t("Neaktivní"));

        public final String label;

        Filter(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Jeden řádek osnovy. */
    public static final class Row {
        public final Task task;
        public final Row parent;
        public final int depth;
        /** Pořadí v souboru (stabilní klíč pro řazení). */
        public final int seq;
        public final List<Row> children = new ArrayList<>();

        Row(Task task, Row parent, int depth, int seq) {
            this.task = task;
            this.parent = parent;
            this.depth = depth;
            this.seq = seq;
        }

        public boolean isSummary() {
            return task.getSummary() || !children.isEmpty();
        }

        public Integer key() {
            return task.getUniqueID() != null ? task.getUniqueID() : System.identityHashCode(task);
        }
    }

    private final List<Row> roots = new ArrayList<>();
    private final List<Row> all = new ArrayList<>();
    private final List<Row> visible = new ArrayList<>();
    private final Set<Integer> collapsed = new HashSet<>();
    private String query = "";
    private Filter filter = Filter.ALL;
    private int maxDepth = Integer.MAX_VALUE;
    private LocalDate today = LocalDate.now();
    private int matchCount;
    private Comparator<Row> order;
    private java.util.function.Predicate<Row> extra;

    /** Rozšířený filtr (null = žádný); kombinuje se s hledáním a základním filtrem. */
    public void setExtra(java.util.function.Predicate<Row> p) {
        extra = p;
        rebuild();
    }

    public boolean hasExtra() {
        return extra != null;
    }

    public void clear() {
        roots.clear();
        all.clear();
        visible.clear();
        visibleIndex = null;
        byTask.clear();
        collapsed.clear();
        matchCount = 0;
    }

    public void load(ProjectFile project) {
        clear();
        Set<Task> seen = new HashSet<>();
        for (Task t : project.getChildTasks()) {
            addTask(t, null, 0, seen);
        }
        List<Task> orphans = new ArrayList<>();
        for (Task t : project.getTasks()) {
            if (!seen.contains(t) && isRealTask(t)) {
                orphans.add(t);
            }
        }
        orphans.sort((a, b) -> Integer.compare(id(a), id(b)));
        for (Task t : orphans) {
            addTask(t, null, 0, seen);
        }
        for (Row r : all) {
            if (r.isSummary() && !r.task.getExpanded()) {
                collapsed.add(r.key());
            }
        }
        rebuild();
    }

    private static int id(Task t) {
        return t.getID() == null ? Integer.MAX_VALUE : t.getID();
    }

    private static boolean isRealTask(Task t) {
        if (t.getName() == null && t.getStart() == null && t.getFinish() == null) {
            return false;
        }
        return t.getID() == null || t.getID() != 0 || t.getParentTask() != null;
    }

    private static boolean isProjectSummary(Task t) {
        return t.getParentTask() == null && t.getID() != null && t.getID() == 0
                && t.getUniqueID() != null && t.getUniqueID() == 0;
    }

    /** Průchod hierarchií bez rekurze (soubor s tisíci úrovněmi nesmí přetéct zásobník); pořadí zůstává jako v souboru. */
    private void addTask(Task root, Row rootParent, int rootDepth, Set<Task> seen) {
        record Frame(Task task, Row parent, int depth) {
        }
        java.util.ArrayDeque<Frame> stack = new java.util.ArrayDeque<>();
        stack.push(new Frame(root, rootParent, rootDepth));
        while (!stack.isEmpty()) {
            Frame f = stack.pop();
            Task t = f.task();
            if (t == null || !seen.add(t)) {
                continue;
            }
            Row parent = f.parent();
            int depth = f.depth();
            if (!isProjectSummary(t) && isRealTask(t)) {
                Row row = new Row(t, parent, depth, all.size());
                all.add(row);
                if (parent == null) {
                    roots.add(row);
                } else {
                    parent.children.add(row);
                }
                parent = row;
                depth = depth + 1;
            }
            List<Task> children = t.getChildTasks();
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(new Frame(children.get(i), parent, depth));
            }
        }
    }

    public List<Row> visible() {
        return Collections.unmodifiableList(visible);
    }

    public List<Row> all() {
        return Collections.unmodifiableList(all);
    }

    public int size() {
        return all.size();
    }

    public int matchCount() {
        return matchCount;
    }

    public boolean isFiltering() {
        return !query.isEmpty() || filter != Filter.ALL || extra != null;
    }

    public boolean isCollapsed(Row r) {
        return collapsed.contains(r.key());
    }

    public void setQuery(String q) {
        query = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        rebuild();
    }

    public void setFilter(Filter f) {
        filter = f == null ? Filter.ALL : f;
        rebuild();
    }

    public Filter getFilter() {
        return filter;
    }

    public void setMaxDepth(int depth) {
        maxDepth = depth <= 0 ? Integer.MAX_VALUE : depth;
        rebuild();
    }

    public void setToday(LocalDate d) {
        today = d;
    }

    /** Řazení v rámci každé úrovně osnovy (null = pořadí ze souboru); shodné hodnoty zachovají pořadí ze souboru. */
    public void setOrder(Comparator<Row> comparator) {
        order = comparator == null ? null : (a, b) -> {
            int c = comparator.compare(a, b);
            return c != 0 ? c : Integer.compare(a.seq, b.seq);
        };
        rebuild();
    }

    public boolean isSorted() {
        return order != null;
    }

    private List<Row> ordered(List<Row> list) {
        if (order == null || list.size() < 2) {
            return list;
        }
        List<Row> copy = new ArrayList<>(list);
        copy.sort(order);
        return copy;
    }

    public void toggle(Row r) {
        if (!r.isSummary()) {
            return;
        }
        if (!collapsed.add(r.key())) {
            collapsed.remove(r.key());
        }
        rebuild();
    }

    public void setCollapsed(Row r, boolean value) {
        if (value) {
            collapsed.add(r.key());
        } else {
            collapsed.remove(r.key());
        }
        rebuild();
    }

    public void expandAll() {
        collapsed.clear();
        rebuild();
    }

    public void collapseAll() {
        collapsed.clear();
        for (Row r : all) {
            if (r.isSummary()) {
                collapsed.add(r.key());
            }
        }
        rebuild();
    }

    /** Rozbalí vše do zadané úrovně (1 = jen kořeny) a sbalí zbytek. */
    public void expandToLevel(int level) {
        collapsed.clear();
        for (Row r : all) {
            if (r.isSummary() && r.depth >= level - 1) {
                collapsed.add(r.key());
            }
        }
        rebuild();
    }

    /** Zajistí, aby byl řádek vidět (rozbalí předky). */
    public void reveal(Row r) {
        boolean changed = false;
        for (Row p = r.parent; p != null; p = p.parent) {
            changed |= collapsed.remove(p.key());
        }
        if (changed) {
            rebuild();
        }
    }

    private final java.util.Map<Task, Row> byTask = new java.util.IdentityHashMap<>();
    private java.util.Map<Row, Integer> visibleIndex;

    public Row find(Task t) {
        if (t == null) {
            return null;
        }
        if (byTask.size() != all.size()) {
            byTask.clear();
            for (Row r : all) {
                byTask.put(r.task, r);
            }
        }
        return byTask.get(t);
    }

    /** Index řádku mezi viditelnými (−1 = není vidět); mapa se staví líně po každém rebuild(). */
    public int indexOfVisible(Row r) {
        if (visibleIndex == null) {
            visibleIndex = new java.util.IdentityHashMap<>();
            for (int i = 0; i < visible.size(); i++) {
                visibleIndex.put(visible.get(i), i);
            }
        }
        Integer i = visibleIndex.get(r);
        return i == null ? -1 : i;
    }

    private boolean matches(Row r) {
        Task t = r.task;
        if (extra != null && !extra.test(r)) {
            return false;
        }
        if (!query.isEmpty()) {
            String hay = (Fmt.text(t.getName()) + " " + Fmt.text(t.getID()) + " " + Fmt.resources(t) + " "
                    + Fmt.text(t.getWBS()) + " " + Fmt.text(t.getNotes())).toLowerCase(Locale.ROOT);
            if (!hay.contains(query)) {
                return false;
            }
        }
        double pct = t.getPercentageComplete() == null ? 0 : t.getPercentageComplete().doubleValue();
        switch (filter) {
            case ALL: return true;
            case CRITICAL: return t.getCritical();
            case MILESTONES: return t.getMilestone();
            case INCOMPLETE: return pct < 100 && !r.isSummary();
            case IN_PROGRESS: return pct > 0 && pct < 100 && !r.isSummary();
            case COMPLETED: return pct >= 100 && !r.isSummary();
            case LATE: {
                LocalDateTime f = t.getFinish();
                return pct < 100 && f != null && f.toLocalDate().isBefore(today) && !r.isSummary();
            }
            case THIS_WEEK: {
                LocalDate monday = today.with(java.time.DayOfWeek.MONDAY);
                return !r.isSummary() && overlaps(t, monday, monday.plusDays(6));
            }
            case NEXT_3_WEEKS: return !r.isSummary() && overlaps(t, today, today.plusDays(21));
            case SUMMARY: return r.isSummary();
            case WITH_RESOURCES: return !Fmt.resources(t).isEmpty();
            case INACTIVE: return !t.getActive();
            default: return true;
        }
    }

    /** Zasahuje úkol do zadaného období? */
    private static boolean overlaps(Task t, LocalDate from, LocalDate to) {
        LocalDateTime s = t.getStart();
        LocalDateTime f = t.getFinish();
        if (s == null || f == null) {
            return false;
        }
        return !s.toLocalDate().isAfter(to) && !f.toLocalDate().isBefore(from);
    }

    public void rebuild() {
        visible.clear();
        visibleIndex = null;
        matchCount = 0;
        if (isFiltering()) {
            Set<Row> keep = new LinkedHashSet<>();
            for (Row r : all) {
                if (r.depth < maxDepth && matches(r)) {
                    matchCount++;
                    keep.add(r);
                    for (Row p = r.parent; p != null; p = p.parent) {
                        keep.add(p);
                    }
                }
            }
            for (Row r : ordered(roots)) {
                addFiltered(r, keep);
            }
        } else {
            for (Row r : ordered(roots)) {
                addVisible(r);
            }
            matchCount = visible.size();
        }
    }

    private void addFiltered(Row root, Set<Row> keep) {
        java.util.ArrayDeque<Row> stack = new java.util.ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Row r = stack.pop();
            if (!keep.contains(r)) {
                continue;
            }
            visible.add(r);
            List<Row> children = ordered(r.children);
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(children.get(i));
            }
        }
    }

    private void addVisible(Row root) {
        java.util.ArrayDeque<Row> stack = new java.util.ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Row r = stack.pop();
            if (r.depth >= maxDepth) {
                continue;
            }
            visible.add(r);
            if (collapsed.contains(r.key())) {
                continue;
            }
            List<Row> children = ordered(r.children);
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(children.get(i));
            }
        }
    }

    public int maxDepthInProject() {
        int m = 0;
        for (Row r : all) {
            m = Math.max(m, r.depth + 1);
        }
        return m;
    }
}
