package cz.msproject.viewer;

import org.mpxj.Duration;
import org.mpxj.Rate;
import org.mpxj.Relation;
import org.mpxj.RelationType;
import org.mpxj.ResourceAssignment;
import org.mpxj.Task;
import org.mpxj.TimeUnit;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Formátování hodnot z MPXJ do české podoby. */
public final class Fmt {
    public static final Locale CS = Locale.forLanguageTag("cs-CZ");
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d. M. yyyy", CS);
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d. M. yyyy H:mm", CS);
    public static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("LLLL yyyy", CS);
    public static final DateTimeFormatter MONTH_SHORT = DateTimeFormatter.ofPattern("LLL", CS);
    public static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d. M.", CS);
    public static final DateTimeFormatter WEEKDAY_SHORT = DateTimeFormatter.ofPattern("EE", CS);

    private static final NumberFormat NUMBER = NumberFormat.getNumberInstance(CS);
    private static final NumberFormat MONEY = NumberFormat.getNumberInstance(CS);

    static {
        NUMBER.setMaximumFractionDigits(2);
        MONEY.setMinimumFractionDigits(2);
        MONEY.setMaximumFractionDigits(2);
    }

    private Fmt() {
    }

    public static String date(LocalDateTime d) {
        return d == null ? "—" : DATE.format(d);
    }

    public static String dateTime(LocalDateTime d) {
        return d == null ? "—" : DATE_TIME.format(d);
    }

    public static String number(double v) {
        synchronized (NUMBER) {
            return NUMBER.format(v);
        }
    }

    public static String money(Number v, String symbol) {
        if (v == null) {
            return "—";
        }
        String s;
        synchronized (MONEY) {
            s = MONEY.format(v.doubleValue());
        }
        return symbol == null || symbol.isBlank() ? s : s + " " + symbol.trim();
    }

    public static String percent(Number v) {
        return v == null ? "0 %" : number(v.doubleValue()) + " %";
    }

    /** Tvary jednotek: [1, 2–4, 5+, necelé číslo]. */
    private static String[] unitForms(TimeUnit u) {
        switch (u) {
            case MINUTES: case ELAPSED_MINUTES: return new String[]{"minuta", "minuty", "minut", "minuty"};
            case HOURS: case ELAPSED_HOURS: return new String[]{"hodina", "hodiny", "hodin", "hodiny"};
            case DAYS: case ELAPSED_DAYS: return new String[]{"den", "dny", "dnů", "dne"};
            case WEEKS: case ELAPSED_WEEKS: return new String[]{"týden", "týdny", "týdnů", "týdne"};
            case MONTHS: case ELAPSED_MONTHS: return new String[]{"měsíc", "měsíce", "měsíců", "měsíce"};
            case YEARS: case ELAPSED_YEARS: return new String[]{"rok", "roky", "let", "roku"};
            default: return new String[]{"%", "%", "%", "%"};
        }
    }

    public static String duration(Duration d) {
        if (d == null) {
            return "—";
        }
        double v = d.getDuration();
        TimeUnit u = d.getUnits();
        if (u == null) {
            return number(v);
        }
        String[] f = unitForms(u);
        boolean whole = Math.abs(v - Math.rint(v)) < 1e-9;
        long n = Math.round(Math.abs(v));
        String form;
        if (!whole) {
            form = f[3];
        } else if (n == 1) {
            form = f[0];
        } else if (n >= 2 && n <= 4) {
            form = f[1];
        } else {
            form = f[2];
        }
        String s = number(v) + (u == TimeUnit.PERCENT || u == TimeUnit.ELAPSED_PERCENT ? " %" : " " + form);
        return u.isElapsed() ? s + " (uplynulé)" : s;
    }

    public static String units(Number n) {
        return n == null ? "—" : number(n.doubleValue()) + " %";
    }

    public static String rate(Rate r, String symbol) {
        if (r == null) {
            return "—";
        }
        String per;
        TimeUnit u = r.getUnits();
        if (u == null) {
            per = "";
        } else {
            switch (u) {
                case MINUTES: per = "/min"; break;
                case HOURS: per = "/hod"; break;
                case DAYS: per = "/den"; break;
                case WEEKS: per = "/týd"; break;
                case MONTHS: per = "/měs"; break;
                case YEARS: per = "/rok"; break;
                default: per = "";
            }
        }
        return money(r.getAmount(), symbol) + per;
    }

    public static String relationType(RelationType t) {
        if (t == null) {
            return "FS";
        }
        switch (t) {
            case START_START: return "SS";
            case FINISH_FINISH: return "FF";
            case START_FINISH: return "SF";
            default: return "FS";
        }
    }

    public static String relationTypeLong(RelationType t) {
        if (t == null) {
            return "konec – začátek";
        }
        switch (t) {
            case START_START: return "začátek – začátek";
            case FINISH_FINISH: return "konec – konec";
            case START_FINISH: return "začátek – konec";
            default: return "konec – začátek";
        }
    }

    /** Zápis předchůdce ve stylu MS Project, např. "12", "12SS", "12FS+2 dny". */
    public static String relation(Relation r, boolean predecessor) {
        Task other = predecessor ? r.getPredecessorTask() : r.getSuccessorTask();
        String id = other == null || other.getID() == null ? "?" : String.valueOf(other.getID());
        String type = relationType(r.getType());
        Duration lag = r.getLag();
        boolean hasLag = lag != null && Math.abs(lag.getDuration()) > 1e-9;
        StringBuilder sb = new StringBuilder(id);
        if (!type.equals("FS") || hasLag) {
            sb.append(type);
        }
        if (hasLag) {
            sb.append(lag.getDuration() < 0 ? "−" : "+");
            sb.append(duration(Duration.getInstance(Math.abs(lag.getDuration()), lag.getUnits())));
        }
        return sb.toString();
    }

    public static String predecessors(Task t) {
        List<String> parts = new ArrayList<>();
        for (Relation r : t.getPredecessors()) {
            parts.add(relation(r, true));
        }
        return String.join("; ", parts);
    }

    public static String successors(Task t) {
        List<String> parts = new ArrayList<>();
        for (Relation r : t.getSuccessors()) {
            parts.add(relation(r, false));
        }
        return String.join("; ", parts);
    }

    public static String resources(Task t) {
        List<String> names = new ArrayList<>();
        for (ResourceAssignment a : t.getResourceAssignments()) {
            if (a.getResource() == null || a.getResource().getName() == null) {
                continue;
            }
            String n = a.getResource().getName();
            Number u = a.getUnits();
            if (u != null && Math.abs(u.doubleValue() - 100.0) > 1e-9) {
                n += " [" + number(u.doubleValue()) + " %]";
            }
            if (!names.contains(n)) {
                names.add(n);
            }
        }
        return String.join(", ", names);
    }

    public static String text(Object o) {
        return o == null ? "" : Objects.toString(o);
    }

    public static String orDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    public static String yesNo(boolean b) {
        return b ? "ano" : "ne";
    }

    public static String html(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '&': sb.append("&amp;"); break;
                case '"': sb.append("&quot;"); break;
                case '\n': sb.append("<br>"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}
