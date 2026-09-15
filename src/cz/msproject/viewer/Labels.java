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
            case AS_SOON_AS_POSSIBLE: return Str.t("Co nejdříve");
            case AS_LATE_AS_POSSIBLE: return Str.t("Co nejpozději");
            case MUST_START_ON: return Str.t("Musí začít");
            case MUST_FINISH_ON: return Str.t("Musí skončit");
            case START_NO_EARLIER_THAN: return Str.t("Zahájit nejdříve");
            case START_NO_LATER_THAN: return Str.t("Zahájit nejpozději");
            case FINISH_NO_EARLIER_THAN: return Str.t("Dokončit nejdříve");
            case FINISH_NO_LATER_THAN: return Str.t("Dokončit nejpozději");
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
            name = Str.t("Nevyrovnávat");
        } else if (v >= 900) {
            name = Str.t("Nejvyšší");
        } else if (v >= 800) {
            name = Str.t("Velmi vysoká");
        } else if (v >= 700) {
            name = Str.t("Vyšší");
        } else if (v >= 600) {
            name = Str.t("Vysoká");
        } else if (v >= 500) {
            name = Str.t("Střední");
        } else if (v >= 400) {
            name = Str.t("Nízká");
        } else if (v >= 300) {
            name = Str.t("Nižší");
        } else if (v >= 200) {
            name = Str.t("Velmi nízká");
        } else {
            name = Str.t("Nejnižší");
        }
        return name + " (" + v + ")";
    }

    public static String resourceType(ResourceType t) {
        if (t == null) {
            return "—";
        }
        switch (t) {
            case WORK: return Str.t("Pracovní");
            case MATERIAL: return Str.t("Materiálový");
            case COST: return Str.t("Nákladový");
            default: return t.toString();
        }
    }

    public static String taskKind(TaskTree.Row r) {
        Task t = r.task;
        if (!t.getActive()) {
            return Str.t("Neaktivní úkol");
        }
        if (t.getSubprojectFile() != null && !t.getSubprojectFile().isBlank()) {
            return Str.t("Vložený podprojekt");
        }
        if (r.isSummary()) {
            return Str.t("Souhrnný úkol");
        }
        if (t.getMilestone()) {
            return Str.t("Milník");
        }
        return Str.t("Úkol");
    }
}
