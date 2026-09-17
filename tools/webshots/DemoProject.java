import org.mpxj.*;
import org.mpxj.mspdi.MSPDIWriter;

import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Vytvoří ukázkový stavební harmonogram (MSPDI XML) pro snímky na web. */
public final class DemoProject {

    private static ProjectFile file;
    private static Resource stavbyvedouci;
    private static Resource zednici;
    private static Resource betonari;
    private static Resource tzb;
    private static Resource elektro;
    private static Task previous;

    public static void main(String[] args) throws Exception {
        file = new ProjectFile();
        ProjectProperties props = file.getProjectProperties();
        props.setName("Bytový dům Husova – výstavba");
        props.setProjectTitle("Bytový dům Husova – harmonogram výstavby");
        props.setCompany("Ukázková stavební společnost");
        props.setManager("Milan Knor");
        props.setStartDate(LocalDateTime.of(2026, 3, 2, 7, 0));
        props.setStatusDate(LocalDateTime.of(2026, 6, 30, 17, 0));
        props.setCurrencySymbol("Kč");
        ProjectCalendar cal = file.addDefaultBaseCalendar();
        file.setDefaultCalendar(cal);

        stavbyvedouci = resource("Stavbyvedoucí", 1200);
        betonari = resource("Četa betonářů", 850);
        zednici = resource("Četa zedníků", 780);
        tzb = resource("TZB – topení a voda", 900);
        elektro = resource("Elektro", 920);
        resource("Věžový jeřáb", 0);

        LocalDate start = LocalDate.of(2026, 3, 2);

        milestone("Zahájení stavby", start);

        Task priprava = summary("Příprava a zařízení staveniště");
        LocalDate d = start;
        d = task(priprava, "Vytyčení stavby a přejímka staveniště", d, 3, 100, stavbyvedouci, false);
        d = task(priprava, "Oplocení a zařízení staveniště", d, 7, 100, zednici, false);
        d = task(priprava, "Přípojky vody a elektro", d, 10, 100, tzb, false);

        Task hrubka = summary("Hrubá stavba");
        d = task(hrubka, "Skrývka ornice a výkopy", d, 12, 100, betonari, false);
        d = task(hrubka, "Základové pasy a deska", d, 18, 100, betonari, true);
        d = task(hrubka, "Hydroizolace spodní stavby", d, 6, 100, zednici, false);
        d = task(hrubka, "Svislé konstrukce 1. NP", d, 15, 100, zednici, true);
        d = task(hrubka, "Stropní konstrukce nad 1. NP", d, 10, 100, betonari, true);
        d = task(hrubka, "Svislé konstrukce 2. NP", d, 15, 80, zednici, true);
        d = task(hrubka, "Stropní konstrukce nad 2. NP", d, 10, 45, betonari, true);
        d = task(hrubka, "Svislé konstrukce 3. NP", d, 15, 0, zednici, true);
        d = task(hrubka, "Stropní konstrukce nad 3. NP", d, 10, 0, betonari, true);
        d = task(hrubka, "Krov a střešní plášť", d, 20, 0, zednici, true);
        milestone("Dokončení hrubé stavby", d);

        Task obvod = summary("Obvodový plášť");
        LocalDate o = d.minusDays(25);
        task(obvod, "Výplně otvorů – okna a dveře", o, 15, 0, zednici, false);
        task(obvod, "Zateplení fasády", o.plusDays(20), 25, 0, zednici, false);
        task(obvod, "Klempířské prvky", o.plusDays(30), 10, 0, zednici, false);

        Task vnitrni = summary("Vnitřní práce");
        d = task(vnitrni, "Hrubé rozvody ZTI a topení", d, 20, 0, tzb, false);
        d = task(vnitrni, "Elektroinstalace – rozvody", d, 18, 0, elektro, false);
        d = task(vnitrni, "Vnitřní omítky", d, 22, 0, zednici, true);
        d = task(vnitrni, "Potěry a izolace podlah", d, 14, 0, betonari, false);
        d = task(vnitrni, "Obklady a dlažby", d, 20, 0, zednici, false);
        d = task(vnitrni, "Montáž zařizovacích předmětů", d, 12, 0, tzb, false);
        d = task(vnitrni, "Kompletace elektro", d, 10, 0, elektro, false);
        d = task(vnitrni, "Podlahové krytiny", d, 12, 0, zednici, false);
        d = task(vnitrni, "Malby a nátěry", d, 10, 0, zednici, true);

        Task dokonceni = summary("Dokončení a předání");
        d = task(dokonceni, "Úklid a vyklizení staveniště", d, 5, 0, zednici, false);
        d = task(dokonceni, "Revize a zkoušky", d, 8, 0, tzb, true);
        Task predani = task2(dokonceni, "Předání díla investorovi", d, 3, 0, stavbyvedouci, true);
        predani.setDeadline(dateTime(d.plusDays(5)));
        d = finishOf(predani);
        milestone("Kolaudace", d.plusDays(10));

        // základní plán = původní termíny posunuté o pár dní (aby bylo co porovnávat)
        for (Task t : file.getTasks()) {
            if (t.getStart() != null && t.getFinish() != null) {
                t.setBaselineStart(t.getStart().minusDays(3));
                t.setBaselineFinish(t.getFinish().minusDays(3));
                t.setBaselineDuration(t.getDuration());
                t.setBaselineWork(t.getWork());
                t.setBaselineCost(t.getCost());
            }
        }
        try (FileOutputStream out = new FileOutputStream(args[0])) {
            new MSPDIWriter().write(file, out);
        }
        System.out.println("Vytvořeno: " + args[0] + " (" + file.getTasks().size() + " úkolů, " + file.getResources().size() + " zdrojů)");
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

    private static void milestone(String name, LocalDate day) {
        Task t = file.addTask();
        t.setName(name);
        t.setMilestone(true);
        t.setStart(dateTime(day));
        t.setFinish(dateTime(day));
        t.setDuration(Duration.getInstance(0, TimeUnit.DAYS));
        t.setCritical(true);
        t.setPercentageComplete(Integer.valueOf(day.isBefore(LocalDate.of(2026, 7, 1)) ? 100 : 0));
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
        t.setPercentageComplete(Integer.valueOf(percent));
        t.setCritical(critical);
        t.setWork(Duration.getInstance(days * 8.0 * (res == stavbyvedouci ? 1 : 3), TimeUnit.HOURS));
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
        while (x.getDayOfWeek().getValue() > 5) {
            x = x.plusDays(1);
        }
        return x;
    }

    private static LocalDate addWorkDays(LocalDate from, int days) {
        LocalDate x = from;
        int left = Math.max(1, days) - 1;
        while (left > 0) {
            x = x.plusDays(1);
            if (x.getDayOfWeek().getValue() <= 5) {
                left--;
            }
        }
        return x;
    }
}
