package cz.msproject.viewer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Export seznamu úkolů do CSV (UTF-8 s BOM, středník – otevře se správně v českém Excelu). */
public final class CsvExport {
    private static final String SEP = ";";

    private CsvExport() {
    }

    /** Zapíše zobrazené sloupce v jejich pořadí; navíc vždy Úroveň (za ID/názvem) a Poznámky (na konci). */
    public static void write(Path target, List<TaskTree.Row> rows, Columns columns, List<Columns.Column> shown) throws IOException {
        List<Columns.Column> cols = new ArrayList<>(shown);
        Columns.Column level = columns.byKey("level");
        Columns.Column notes = columns.byKey("notes");
        if (level != null && !cols.contains(level)) {
            int at = Math.min(cols.size(), Math.max(cols.indexOf(Columns.ID), cols.indexOf(Columns.NAME)) + 1);
            cols.add(at, level);
        }
        if (notes != null && !cols.contains(notes)) {
            cols.add(notes);
        }
        try (BufferedWriter w = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            w.write(0xFEFF);
            String[] head = new String[cols.size()];
            for (int i = 0; i < cols.size(); i++) {
                head[i] = cols.get(i).title;
            }
            writeLine(w, head);
            String[] cells = new String[cols.size()];
            for (TaskTree.Row r : rows) {
                for (int i = 0; i < cols.size(); i++) {
                    Columns.Column c = cols.get(i);
                    cells[i] = c == notes ? Fmt.text(r.task.getNotes()) : columns.text(c, r);
                }
                writeLine(w, cells);
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
