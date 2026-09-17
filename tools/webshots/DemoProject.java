import org.mpxj.*;
import org.mpxj.mspdi.MSPDIWriter;

import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Vytvoří anonymní ukázkový harmonogram (MSPDI XML) pro snímky na web. */
public final class DemoProject {

    private static ProjectFile file;
    private static Resource stavbyvedouci;
    private static Resource zemniPrace;
    private static Resource montazOk;
    private static Resource oplasteni;
    private static Resource tzb;
    private static Resource elektro;
    private static Resource komunikace;
    private static Task previous;
    /** Datum stavu – podle něj se odvozuje rozpracovanost a skutečné termíny. */
    private static final LocalDate STATUS = LocalDate.of(2026, 7, 31);
    private static final java.util.Set<LocalDate> SVATKY = new java.util.HashSet<>();

    public static void main(String[] args) throws Exception {
        file = new ProjectFile();
        ProjectProperties props = file.getProjectProperties();
        props.setName("Výrobní hala – ukázkový harmonogram");
        props.setProjectTitle("Novostavba výrobní haly s administrativním vestavkem");
        props.setCompany("Ukázková stavební s.r.o.");
        props.setManager("Vedoucí projektu");
        props.setStartDate(LocalDateTime.of(2026, 4, 7, 7, 0));
        props.setStatusDate(LocalDateTime.of(2026, 7, 31, 17, 0));
        props.setCurrencySymbol("Kč");
        ProjectCalendar cal = file.addDefaultBaseCalendar();
        file.setDefaultCalendar(cal);
        holiday(cal, LocalDate.of(2026, 4, 6), "Velikonoční pondělí");
        holiday(cal, LocalDate.of(2026, 5, 1), "Svátek práce");
        holiday(cal, LocalDate.of(2026, 5, 8), "Den vítězství");
        holiday(cal, LocalDate.of(2026, 7, 6), "Mistr Jan Hus");
        holiday(cal, LocalDate.of(2026, 9, 28), "Den české státnosti");
        holiday(cal, LocalDate.of(2026, 10, 28), "Vznik samostatného státu");
        holiday(cal, LocalDate.of(2026, 11, 17), "Den boje za svobodu");
        holiday(cal, LocalDate.of(2026, 12, 24), "Štědrý den");
        holiday(cal, LocalDate.of(2026, 12, 25), "1. svátek vánoční");
        holiday(cal, LocalDate.of(2027, 1, 1), "Nový rok");

        stavbyvedouci = resource("Stavbyvedoucí", 1150);
        zemniPrace = resource("Zemní práce", 820);
        montazOk = resource("Montáž OK", 980);
        oplasteni = resource("Opláštění", 880);
        tzb = resource("TZB", 910);
        elektro = resource("Elektro", 930);
        komunikace = resource("Komunikace", 860);
        resource("Autojeřáb", 0);

        LocalDate start = LocalDate.of(2026, 4, 7);   // 6. 4. je Velikonoční pondělí
        milestone("Zahájení výstavby", start, true);

        Task priprava = summary("Příprava staveniště");
        LocalDate d = start;
        d = task(priprava, "Předání a vytyčení staveniště", d, 2, 100, stavbyvedouci, false);
        d = task(priprava, "Zařízení staveniště a oplocení", d, 6, 100, zemniPrace, false);
        d = task(priprava, "Staveništní přípojky NN a vody", d, 8, 100, tzb, false);

        Task zaklady = summary("Zemní práce a založení");
        d = task(zaklady, "Skrývka ornice a hrubé terénní úpravy", d, 10, 100, zemniPrace, false);
        d = task(zaklady, "Vrtané piloty", d, 14, 100, zemniPrace, true);
        d = task(zaklady, "Základové patky a prahy", d, 16, 100, zemniPrace, true);
        d = task(zaklady, "Podkladní beton a ležatá kanalizace", d, 9, 100, tzb, false);

        Task nosna = summary("Nosná konstrukce haly");
        d = task(nosna, "Montáž ocelových sloupů", d, 8, 100, montazOk, true);
        d = task(nosna, "Montáž vazníků a ztužidel", d, 12, 100, montazOk, true);
        d = task(nosna, "Vaznice a trapézový plech střechy", d, 9, 70, montazOk, true);
        milestone("Dokončená nosná konstrukce", d, false);

        Task plast = summary("Opláštění a střecha");
        d = task(plast, "Střešní izolace a hydroizolační fólie", d, 14, 25, oplasteni, true);
        d = task(plast, "Světlíky a střešní výlezy", d, 6, 0, oplasteni, false);
        d = task(plast, "Sendvičové panely obvodového pláště", d, 18, 0, oplasteni, true);
        d = task(plast, "Sekční vrata a vstupní dveře", d, 7, 0, oplasteni, false);
        d = task(plast, "Klempířské prvky a okapy", d, 8, 0, oplasteni, false);

        Task vnitrni = summary("Vnitřní práce");
        d = task(vnitrni, "Drátkobetonová podlaha haly", d, 15, 0, komunikace, true);
        d = task(vnitrni, "Zdivo a příčky administrativního vestavku", d, 12, 0, oplasteni, false);
        d = task(vnitrni, "Rozvody ZTI a vytápění", d, 16, 0, tzb, false);
        d = task(vnitrni, "Vzduchotechnika a strojovna", d, 14, 0, tzb, false);
        d = task(vnitrni, "Silnoproud a osvětlení haly", d, 18, 0, elektro, false);
        d = task(vnitrni, "EPS, EZS a strukturovaná kabeláž", d, 10, 0, elektro, false);
        d = task(vnitrni, "Podlahy, obklady a malby vestavku", d, 16, 0, oplasteni, false);

        Task venkovni = summary("Zpevněné plochy a okolí");
        LocalDate v = d.minusDays(30);
        task(venkovni, "Dešťová kanalizace a retenční nádrž", v, 12, 0, tzb, false);
        task(venkovni, "Podkladní vrstvy komunikací", v.plusDays(14), 14, 0, komunikace, false);
        task(venkovni, "Asfaltové plochy a parkoviště", v.plusDays(30), 12, 0, komunikace, false);
        task(venkovni, "Oplocení areálu a vjezdová brána", v.plusDays(40), 8, 0, komunikace, false);
        task(venkovni, "Terénní úpravy a výsadba zeleně", v.plusDays(48), 7, 0, zemniPrace, false);

        Task dokonceni = summary("Dokončení a předání");
        d = task(dokonceni, "Vyklizení staveniště a úklid", d, 5, 0, zemniPrace, false);
        d = task(dokonceni, "Komplexní zkoušky a revize", d, 9, 0, tzb, true);
        Task predani = task2(dokonceni, "Předání díla objednateli", d, 3, 0, stavbyvedouci, true);
        predani.setDeadline(dateTime(finishOf(predani).plusDays(5)));
        d = finishOf(predani);
        milestone("Kolaudace", d.plusDays(12), false);

        rollUp();

        // základní plán = původní termíny o pár dní dříve (aby bylo co porovnávat)
        for (Task t : file.getTasks()) {
            if (t.getStart() != null && t.getFinish() != null) {
                t.setBaselineStart(t.getStart().minusDays(4));
                t.setBaselineFinish(t.getFinish().minusDays(4));
                t.setBaselineDuration(t.getDuration());
                t.setBaselineWork(t.getWork());
                t.setBaselineCost(t.getCost());
            }
        }
        try (FileOutputStream out = new FileOutputStream(args[0])) {
            new MSPDIWriter().write(file, out);
        }
        System.out.println("Vytvořeno: " + args[0] + " (" + file.getTasks().size() + " úkolů, "
                + file.getResources().size() + " zdrojů)");
    }

    /** Dopočítá souhrnným úkolům termíny, trvání, práci a % dokončení z podřízených. */
    private static void rollUp() {
        for (Task t : file.getChildTasks()) {
            rollUp(t);
        }
    }

    private static void rollUp(Task task) {
        for (Task child : task.getChildTasks()) {
            rollUp(child);
        }
        if (task.getChildTasks().isEmpty()) {
            return;
        }
        LocalDateTime start = null;
        LocalDateTime finish = null;
        double work = 0;
        double done = 0;
        for (Task child : task.getChildTasks()) {
            if (child.getStart() != null && (start == null || child.getStart().isBefore(start))) {
                start = child.getStart();
            }
            if (child.getFinish() != null && (finish == null || child.getFinish().isAfter(finish))) {
                finish = child.getFinish();
            }
            double hours = child.getWork() != null ? child.getWork().getDuration() : 0;
            work += hours;
            Number percent = child.getPercentageComplete();
            done += hours * (percent != null ? percent.doubleValue() : 0);
        }
        if (start == null || finish == null) {
            return;
        }
        task.setStart(start);
        task.setFinish(finish);
        task.setDuration(Duration.getInstance(workDays(start.toLocalDate(), finish.toLocalDate()), TimeUnit.DAYS));
        task.setWork(Duration.getInstance(work, TimeUnit.HOURS));
        task.setPercentageComplete(Integer.valueOf((int) Math.round(work > 0 ? done / work : 0)));
    }

    private static int workDays(LocalDate from, LocalDate to) {
        int days = 0;
        for (LocalDate x = from; !x.isAfter(to); x = x.plusDays(1)) {
            if (x.getDayOfWeek().getValue() <= 5 && !SVATKY.contains(x)) {
                days++;
            }
        }
        return days;
    }

    private static Resource resource(String name, double rate) {
        Resource r = file.addResource();
        r.setName(name);
        r.setType(ResourceType.WORK);
        r.set(ResourceField.MAX_UNITS, Double.valueOf(100.0));
        if (rate > 0) {
            r.set(ResourceField.STANDARD_RATE, new Rate(rate, TimeUnit.HOURS));
        }
        return r;
    }

    private static void holiday(ProjectCalendar cal, LocalDate day, String name) {
        ProjectCalendarException ex = cal.addCalendarException(day);
        ex.setName(name);
        SVATKY.add(day);
    }

    private static Task summary(String name) {
        Task t = file.addTask();
        t.setName(name);
        return t;
    }

    private static LocalDateTime dateTime(LocalDate d) {
        return d.atTime(7, 0);
    }

    private static LocalDate finishOf(Task t) {
        return t.getFinish().toLocalDate();
    }

    private static void milestone(String name, LocalDate day, boolean done) {
        Task t = file.addTask();
        t.setName(name);
        t.setMilestone(true);
        t.setStart(dateTime(day));
        t.setFinish(dateTime(day));
        t.setDuration(Duration.getInstance(0, TimeUnit.DAYS));
        t.setCritical(true);
        boolean reached = !day.isAfter(STATUS);
        t.setPercentageComplete(Integer.valueOf(reached ? 100 : 0));
        if (reached) {
            t.setActualStart(dateTime(day));
            t.setActualFinish(dateTime(day));
        }
    }

    /** Vloží úkol a vrátí datum, kdy má začít následující (řetězení konec–začátek). */
    private static LocalDate task(Task parent, String name, LocalDate begin, int days, int percent, Resource res, boolean critical) {
        Task t = task2(parent, name, begin, days, percent, res, critical);
        return finishOf(t).plusDays(1);
    }

    private static Task task2(Task parent, String name, LocalDate begin, int days, int percent, Resource res, boolean critical) {
        Task t = parent.addTask();
        t.setName(name);
        LocalDate b = skipWeekend(begin);
        LocalDate e = addWorkDays(b, days);
        t.setStart(dateTime(b));
        t.setFinish(e.atTime(16, 0));
        t.setDuration(Duration.getInstance(days, TimeUnit.DAYS));
        // rozpracovanost musí sedět s datem stavu: co ještě nezačalo, nemůže být hotové
        if (b.isAfter(STATUS)) {
            percent = 0;
        } else if (!e.isAfter(STATUS)) {
            percent = 100;
        } else if (percent == 0 || percent == 100) {
            percent = 50;
        }
        t.setPercentageComplete(Integer.valueOf(percent));
        if (percent > 0) {
            t.setActualStart(t.getStart());
            if (percent >= 100) {
                t.setActualFinish(t.getFinish());
                t.setActualDuration(t.getDuration());
            }
        }
        t.setCritical(critical);
        t.setWork(Duration.getInstance(days * 8.0 * (res == stavbyvedouci ? 0.5 : 1.0), TimeUnit.HOURS));
        if (res != null) {
            ResourceAssignment a = t.addResourceAssignment(res);
            a.setUnits(res == stavbyvedouci ? 50.0 : 100.0);
            a.setWork(t.getWork());
        }
        if (previous != null) {
            t.addPredecessor(new Relation.Builder().predecessorTask(previous).type(RelationType.FINISH_START));
        }
        previous = t;
        return t;
    }

    private static LocalDate skipWeekend(LocalDate d) {
        LocalDate x = d;
        while (x.getDayOfWeek().getValue() > 5 || SVATKY.contains(x)) {
            x = x.plusDays(1);
        }
        return x;
    }

    private static LocalDate addWorkDays(LocalDate from, int days) {
        LocalDate x = from;
        int left = Math.max(1, days) - 1;
        while (left > 0) {
            x = x.plusDays(1);
            if (x.getDayOfWeek().getValue() <= 5 && !SVATKY.contains(x)) {
                left--;
            }
        }
        return x;
    }
}
