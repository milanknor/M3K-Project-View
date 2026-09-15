package cz.msproject.viewer;

import org.mpxj.ConstraintType;
import org.mpxj.Priority;
import org.mpxj.ResourceType;
import org.mpxj.Task;

/** České popisky výčtových hodnot MPXJ. */
public final class Labels {
    private Labels() {
    }

    public static String constraint(ConstraintType c) {
        if (c == null) {
            return "—";
        }
        switch (c) {
            case AS_SOON_AS_POSSIBLE: return "Co nejdříve";
            case AS_LATE_AS_POSSIBLE: return "Co nejpozději";
            case MUST_START_ON: return "Musí začít";
            case MUST_FINISH_ON: return "Musí skončit";
            case START_NO_EARLIER_THAN: return "Zahájit nejdříve";
            case START_NO_LATER_THAN: return "Zahájit nejpozději";
            case FINISH_NO_EARLIER_THAN: return "Dokončit nejdříve";
            case FINISH_NO_LATER_THAN: return "Dokončit nejpozději";
            default: return c.toString();
        }
    }

    public static String priority(Priority p) {
        if (p == null) {
            return "—";
        }
        int v = p.getValue();
        String name;
        if (v >= 1000) {
            name = "Nevyrovnávat";
        } else if (v >= 900) {
            name = "Nejvyšší";
        } else if (v >= 800) {
            name = "Velmi vysoká";
        } else if (v >= 700) {
            name = "Vyšší";
        } else if (v >= 600) {
            name = "Vysoká";
        } else if (v >= 500) {
            name = "Střední";
        } else if (v >= 400) {
            name = "Nízká";
        } else if (v >= 300) {
            name = "Nižší";
        } else if (v >= 200) {
            name = "Velmi nízká";
        } else {
            name = "Nejnižší";
        }
        return name + " (" + v + ")";
    }

    public static String resourceType(ResourceType t) {
        if (t == null) {
            return "—";
        }
        switch (t) {
            case WORK: return "Pracovní";
            case MATERIAL: return "Materiálový";
            case COST: return "Nákladový";
            default: return t.toString();
        }
    }

    public static String taskKind(TaskTree.Row r) {
        Task t = r.task;
        if (!t.getActive()) {
            return "Neaktivní úkol";
        }
        if (t.getSubprojectFile() != null && !t.getSubprojectFile().isBlank()) {
            return "Vložený podprojekt";
        }
        if (r.isSummary()) {
            return "Souhrnný úkol";
        }
        if (t.getMilestone()) {
            return "Milník";
        }
        return "Úkol";
    }
}
