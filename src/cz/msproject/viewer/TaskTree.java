package cz.msproject.viewer;

import org.mpxj.ProjectFile;
import org.mpxj.Task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Hierarchie úkolů, sbalování souhrnů a filtrování viditelných řádků. */
public final class TaskTree {

    public enum Filter {
        ALL("Všechny úkoly"),
        CRITICAL("Kritické úkoly"),
        MILESTONES("Milníky"),
        INCOMPLETE("Nedokončené"),
        IN_PROGRESS("Rozpracované"),
        COMPLETED("Dokončené"),
        LATE("Zpožděné (po termínu)"),
        SUMMARY("Jen souhrnné úkoly"),
        WITH_RESOURCES("S přiřazenými zdroji"),
        INACTIVE("Neaktivní");

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
        public final List<Row> children = new ArrayList<>();

        Row(Task task, Row parent, int depth) {
            this.task = task;
            this.parent = parent;
            this.depth = depth;
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

    public void clear() {
        roots.clear();
        all.clear();
        visible.clear();
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

    private void addTask(Task t, Row parent, int depth, Set<Task> seen) {
        if (t == null || !seen.add(t)) {
            return;
        }
        if (isProjectSummary(t)) {
            for (Task c : t.getChildTasks()) {
                addTask(c, parent, depth, seen);
            }
            return;
        }
        if (!isRealTask(t)) {
            for (Task c : t.getChildTasks()) {
                addTask(c, parent, depth, seen);
            }
            return;
        }
        Row row = new Row(t, parent, depth);
        all.add(row);
        if (parent == null) {
            roots.add(row);
        } else {
            parent.children.add(row);
        }
        for (Task c : t.getChildTasks()) {
            addTask(c, row, depth + 1, seen);
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
        return !query.isEmpty() || filter != Filter.ALL;
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

    public Row find(Task t) {
        if (t == null) {
            return null;
        }
        for (Row r : all) {
            if (r.task == t) {
                return r;
            }
        }
        return null;
    }

    public Row findByUniqueId(Integer uid) {
        if (uid == null) {
            return null;
        }
        for (Row r : all) {
            if (uid.equals(r.task.getUniqueID())) {
                return r;
            }
        }
        return null;
    }

    public int indexOfVisible(Row r) {
        return visible.indexOf(r);
    }

    private boolean matches(Row r) {
        Task t = r.task;
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
            case SUMMARY: return r.isSummary();
            case WITH_RESOURCES: return !Fmt.resources(t).isEmpty();
            case INACTIVE: return !t.getActive();
            default: return true;
        }
    }

    public void rebuild() {
        visible.clear();
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
            for (Row r : all) {
                if (keep.contains(r)) {
                    visible.add(r);
                }
            }
        } else {
            for (Row r : roots) {
                addVisible(r);
            }
            matchCount = visible.size();
        }
    }

    private void addVisible(Row r) {
        if (r.depth >= maxDepth) {
            return;
        }
        visible.add(r);
        if (collapsed.contains(r.key())) {
            return;
        }
        for (Row c : r.children) {
            addVisible(c);
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
