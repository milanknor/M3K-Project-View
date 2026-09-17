package cz.msproject.viewer;

import org.mpxj.ConstraintType;
import org.mpxj.Duration;
import org.mpxj.ProjectFile;
import org.mpxj.ProjectProperties;
import org.mpxj.Relation;
import org.mpxj.RelationType;
import org.mpxj.Task;
import org.mpxj.TimeUnit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Rozbor kvality harmonogramu ve stylu kontrol DCMA 14 Point Assessment: hledá
 * chybějící vazby, tvrdá omezení, zápornou i přehnanou rezervu, dlouhá trvání,
 * prodlevy ve vazbách a nesrovnalosti proti datu stavu. Nic nemění – jen popisuje,
 * co v plánu vypadá podezřele.
 */
public final class ScheduleCheck {

    /** Hranice podle DCMA: rezerva i trvání nad 44 pracovních dní jsou „dlouhé“. */
    private static final double LONG_DAYS = 44;

    /** Jeden nález: název kontroly, popis, počet dotčených úkolů a jejich seznam. */
    public record Finding(String rule, String detail, int count, int total, List<Task> tasks, Level level) {

        /** Podíl dotčených úkolů v procentech (0, když se nepočítá z počtu úkolů). */
        public double share() {
            return total <= 0 ? 0 : count * 100.0 / total;
        }
    }

    /** Závažnost nálezu. */
    public enum Level {
        OK(Str.t("v pořádku")), NOTE(Str.t("k zamyšlení")), WARNING(Str.t("varování"));

        public final String label;

        Level(String label) {
            this.label = label;
        }
    }

    private ScheduleCheck() {
    }

    /** Provede všechny kontroly nad úkoly projektu. */
    public static List<Finding> run(ProjectFile project) {
        List<Task> tasks = new ArrayList<>();
        for (Task t : project.getTasks()) {
            if (t == null || t.getName() == null) {
                continue;
            }
            if (t.getSummary() || !t.getActive()) {
                continue;
            }
            tasks.add(t);
        }
        int total = tasks.size();
        List<Finding> out = new ArrayList<>();
        if (total == 0) {
            return out;
        }
        ProjectProperties props = project.getProjectProperties();
        LocalDateTime status = props.getStatusDate() != null ? props.getStatusDate() : LocalDateTime.now();
        LocalDate statusDay = status.toLocalDate();

        List<Task> noPredecessor = new ArrayList<>();
        List<Task> noSuccessor = new ArrayList<>();
        List<Task> hardConstraint = new ArrayList<>();
        List<Task> negativeSlack = new ArrayList<>();
        List<Task> highSlack = new ArrayList<>();
        List<Task> longDuration = new ArrayList<>();
        List<Task> withLag = new ArrayList<>();
        List<Task> withLead = new ArrayList<>();
        List<Task> nonFs = new ArrayList<>();
        List<Task> lateAgainstStatus = new ArrayList<>();
        List<Task> futureActuals = new ArrayList<>();
        List<Task> startedWithoutActual = new ArrayList<>();
        List<Task> withoutResource = new ArrayList<>();
        List<Task> milestoneWithoutLink = new ArrayList<>();

        for (Task t : tasks) {
            List<Relation> pred = safe(t.getPredecessors());
            List<Relation> succ = safe(t.getSuccessors());
            if (pred.isEmpty()) {
                noPredecessor.add(t);
            }
            if (succ.isEmpty()) {
                noSuccessor.add(t);
            }
            if (t.getMilestone() && pred.isEmpty() && succ.isEmpty()) {
                milestoneWithoutLink.add(t);
            }
            ConstraintType c = t.getConstraintType();
            if (c == ConstraintType.MUST_START_ON || c == ConstraintType.MUST_FINISH_ON
                    || c == ConstraintType.START_NO_LATER_THAN || c == ConstraintType.FINISH_NO_LATER_THAN) {
                hardConstraint.add(t);
            }
            double slack = days(t.getTotalSlack(), props);
            if (slack < -0.01) {
                negativeSlack.add(t);
            } else if (slack > LONG_DAYS) {
                highSlack.add(t);
            }
            if (!t.getMilestone() && days(t.getDuration(), props) > LONG_DAYS) {
                longDuration.add(t);
            }
            for (Relation r : pred) {
                double lag = days(r.getLag(), props);
                if (lag > 0.01) {
                    add(withLag, t);
                } else if (lag < -0.01) {
                    add(withLead, t);
                }
                if (r.getType() != null && r.getType() != RelationType.FINISH_START) {
                    add(nonFs, t);
                }
            }
            double percent = t.getPercentageComplete() == null ? 0 : t.getPercentageComplete().doubleValue();
            LocalDateTime finish = t.getFinish();
            if (percent < 100 && finish != null && finish.toLocalDate().isBefore(statusDay)) {
                lateAgainstStatus.add(t);
            }
            if (after(t.getActualStart(), statusDay) || after(t.getActualFinish(), statusDay)) {
                futureActuals.add(t);
            }
            if (percent > 0 && t.getActualStart() == null) {
                startedWithoutActual.add(t);
            }
            if (!t.getMilestone() && t.getResourceAssignments().isEmpty() && days(t.getDuration(), props) > 0) {
                withoutResource.add(t);
            }
        }

        add(out, Str.t("Úkoly bez předchůdce"),
                Str.t("Nemají na co navazovat – při posunu začátku se samy neposunou."), noPredecessor, total, 5);
        add(out, Str.t("Úkoly bez následníka"),
                Str.t("Nic z nich nevychází, takže jejich zpoždění se v plánu neprojeví."), noSuccessor, total, 5);
        add(out, Str.t("Tvrdá omezení"),
                Str.t("„Musí začít/skončit“ a „nejpozději“ přebíjejí vazby a zakrývají skutečnou kritickou cestu."), hardConstraint, total, 0);
        add(out, Str.t("Záporná celková rezerva"),
                Str.t("Plán je v konfliktu – termín nelze splnit bez změny vazeb nebo omezení."), negativeSlack, total, 0);
        add(out, Str.t("Rezerva nad 44 dní"),
                Str.t("Vysoká rezerva bývá známkou chybějící nebo volné vazby."), highSlack, total, 5);
        add(out, Str.t("Trvání nad 44 dní"),
                Str.t("Dlouhé úkoly se špatně řídí; obvykle jde o nerozpadlý balík práce."), longDuration, total, 5);
        add(out, Str.t("Vazby s prodlevou"),
                Str.t("Prodleva ve vazbě skrývá činnost, která v plánu není vidět."), withLag, total, 5);
        add(out, Str.t("Vazby s předstihem"),
                Str.t("Záporná prodleva (překryv) podle DCMA v plánu být nemá."), withLead, total, 0);
        add(out, Str.t("Jiné vazby než konec–začátek"),
                Str.t("SS/FF/SF vazby jsou v pořádku, pokud je jich málo a jsou zdůvodněné."), nonFs, total, 10);
        add(out, Str.t("Zpožděné proti datu stavu"),
                Str.t("Dokončení je před datem stavu, ale úkol není hotový."), lateAgainstStatus, total, 5);
        add(out, Str.t("Skutečné termíny v budoucnosti"),
                Str.t("Skutečné zahájení nebo dokončení je po datu stavu – chyba ve výkazu."), futureActuals, total, 0);
        add(out, Str.t("Rozpracované bez skutečného zahájení"),
                Str.t("Procento dokončení je vyplněné, ale skutečné zahájení chybí."), startedWithoutActual, total, 5);
        add(out, Str.t("Úkoly bez zdroje"),
                Str.t("Bez přiřazení nejde plánovat kapacity ani náklady."), withoutResource, total, 20);
        add(out, Str.t("Milníky bez vazeb"),
                Str.t("Milník bez vazby nemá v plánu žádnou oporu."), milestoneWithoutLink, total, 0);
        return out;
    }

    /** Shrnutí do jedné věty. */
    public static String summary(List<Finding> findings) {
        int warnings = 0;
        int notes = 0;
        for (Finding f : findings) {
            if (f.level() == Level.WARNING) {
                warnings++;
            } else if (f.level() == Level.NOTE) {
                notes++;
            }
        }
        if (warnings == 0 && notes == 0) {
            return Str.t("Žádná z kontrol nenašla problém.");
        }
        return Str.t("Varování: ") + warnings + Str.t(", k zamyšlení: ") + notes
                + Str.t(" (kontrol celkem: ") + findings.size() + ")";
    }

    private static void add(List<Finding> out, String rule, String detail, List<Task> tasks, int total, double tolerancePercent) {
        double share = total <= 0 ? 0 : tasks.size() * 100.0 / total;
        Level level = tasks.isEmpty() ? Level.OK : share <= tolerancePercent ? Level.NOTE : Level.WARNING;
        out.add(new Finding(rule, detail, tasks.size(), total, List.copyOf(tasks), level));
    }

    private static void add(List<Task> list, Task t) {
        if (list.isEmpty() || list.get(list.size() - 1) != t) {
            list.add(t);
        }
    }

    private static List<Relation> safe(List<Relation> list) {
        return list == null ? List.of() : list;
    }

    private static boolean after(LocalDateTime d, LocalDate day) {
        return d != null && d.toLocalDate().isAfter(day);
    }

    /** Trvání v pracovních dnech (0, když není zadané). */
    private static double days(Duration d, ProjectProperties props) {
        if (d == null) {
            return 0;
        }
        try {
            return d.getUnits() == TimeUnit.DAYS ? d.getDuration() : d.convertUnits(TimeUnit.DAYS, props).getDuration();
        } catch (RuntimeException ex) {
            return 0;
        }
    }
}
