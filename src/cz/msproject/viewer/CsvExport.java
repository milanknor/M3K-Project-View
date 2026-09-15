package cz.msproject.viewer;

import org.mpxj.Task;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Export seznamu úkolů do CSV (UTF-8 s BOM, středník – otevře se správně v českém Excelu). */
public final class CsvExport {
    private static final String SEP = ";";

    private CsvExport() {
    }

    public static void write(Path target, List<TaskTree.Row> rows, String currency) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            w.write(0xFEFF);
            writeLine(w, "ID", "Úroveň", "WBS", "Název úkolu", "Trvání", "Zahájení", "Dokončení", "% dokončeno",
                    "Předchůdci", "Názvy zdrojů", "Práce", "Náklady", "Celková rezerva", "Kritický", "Milník",
                    "Souhrnný", "Aktivní", "Termín", "Poznámky");
            for (TaskTree.Row r : rows) {
                Task t = r.task;
                writeLine(w,
                        Fmt.text(t.getID()),
                        String.valueOf(r.depth + 1),
                        Fmt.text(t.getWBS()),
                        Fmt.text(t.getName()),
                        Fmt.duration(t.getDuration()),
                        Fmt.date(t.getStart()),
                        Fmt.date(t.getFinish()),
                        Fmt.percent(t.getPercentageComplete()),
                        Fmt.predecessors(t),
                        Fmt.resources(t),
                        Fmt.duration(t.getWork()),
                        Fmt.money(t.getCost(), currency),
                        Fmt.duration(t.getTotalSlack()),
                        Fmt.yesNo(t.getCritical()),
                        Fmt.yesNo(t.getMilestone()),
                        Fmt.yesNo(r.isSummary()),
                        Fmt.yesNo(t.getActive()),
                        Fmt.date(t.getDeadline()),
                        Fmt.text(t.getNotes()));
            }
        }
    }

    private static void writeLine(BufferedWriter w, String... cells) throws IOException {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                w.write(SEP);
            }
            w.write(quote(cells[i]));
        }
        w.write("\r\n");
    }

    private static String quote(String s) {
        if (s == null) {
            return "";
        }
        String v = s.replace("—", "");
        if (v.contains(SEP) || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
