package cz.msproject.viewer;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Zápis sešitu XLSX bez knihoven třetích stran. Soubor je ZIP s několika XML díly
 * (OOXML, ECMA-376); texty se zapisují přímo do buněk, takže není potřeba sdílená
 * tabulka řetězců. Listy se zapisují postupně: {@link #sheet}, pak {@link #row}
 * a buňky, nakonec {@link #close()}.
 */
public final class XlsxWriter implements AutoCloseable {

    /** Formát buňky (odpovídá pořadí v cellXfs ve stylech). */
    public enum Style {
        TEXT(0), HEADER(1), BOLD(2), DATE(3), NUMBER(4), MONEY(5), PERCENT(6);

        final int id;

        Style(int id) {
            this.id = id;
        }
    }

    /** Excel počítá dny od 1. 1. 1900 s chybou přestupného roku – základ je 30. 12. 1899. */
    private static final LocalDate EPOCH = LocalDate.of(1899, 12, 30);
    private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";

    private final ZipOutputStream zip;
    private final Writer out;
    private final String currency;
    private final List<String> sheets = new ArrayList<>();
    private boolean sheetOpen;
    private boolean rowOpen;
    private int row;
    private int col;
    private int maxCol;

    public XlsxWriter(Path target, String currencySymbol) throws IOException {
        zip = new ZipOutputStream(Files.newOutputStream(target), StandardCharsets.UTF_8);
        out = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
        currency = currencySymbol == null ? "" : currencySymbol;
    }

    // ---------------------------------------------------------------- listy a řádky

    /** Začne nový list; šířky sloupců jsou ve znacích (0 = výchozí). */
    public void sheet(String name, int[] widths) throws IOException {
        endSheet();
        sheets.add(sheetName(name));
        zip.putNextEntry(new ZipEntry("xl/worksheets/sheet" + sheets.size() + ".xml"));
        sheetOpen = true;
        row = 0;
        maxCol = 0;
        out.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        out.write("<worksheet xmlns=\"" + NS + "\">");
        out.write("<sheetViews><sheetView workbookViewId=\"0\">"
                + "<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>"
                + "</sheetView></sheetViews>");
        if (widths != null && widths.length > 0) {
            out.write("<cols>");
            for (int i = 0; i < widths.length; i++) {
                int w = Math.max(6, Math.min(80, widths[i] <= 0 ? 12 : widths[i]));
                out.write("<col min=\"" + (i + 1) + "\" max=\"" + (i + 1) + "\" width=\"" + w + "\" customWidth=\"1\"/>");
            }
            out.write("</cols>");
        }
        out.write("<sheetData>");
    }

    public void row() throws IOException {
        endRow();
        row++;
        col = 0;
        out.write("<row r=\"" + row + "\">");
        rowOpen = true;
    }

    public void text(String value) throws IOException {
        text(value, Style.TEXT);
    }

    public void text(String value, Style style) throws IOException {
        cell(style);
        if (value == null || value.isEmpty()) {
            out.write("/>");
            return;
        }
        out.write(" t=\"inlineStr\"><is><t xml:space=\"preserve\">" + xml(value) + "</t></is></c>");
    }

    public void number(double value, Style style) throws IOException {
        cell(style);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            out.write("/>");
            return;
        }
        out.write("><v>" + trim(value) + "</v></c>");
    }

    public void date(LocalDateTime value) throws IOException {
        cell(Style.DATE);
        if (value == null) {
            out.write("/>");
            return;
        }
        double serial = ChronoUnit.DAYS.between(EPOCH, value.toLocalDate())
                + value.toLocalTime().toSecondOfDay() / 86400.0;
        out.write("><v>" + trim(serial) + "</v></c>");
    }

    /** Prázdná buňka. */
    public void blank() throws IOException {
        cell(Style.TEXT);
        out.write("/>");
    }

    /** Řádek textů (hlavička). */
    public void header(String... titles) throws IOException {
        row();
        for (String t : titles) {
            text(t, Style.HEADER);
        }
    }

    // ---------------------------------------------------------------- uzavření

    @Override
    public void close() throws IOException {
        try {
            endSheet();
            writePart("xl/styles.xml", styles());
            writePart("xl/workbook.xml", workbook());
            writePart("xl/_rels/workbook.xml.rels", workbookRels());
            writePart("[Content_Types].xml", contentTypes());
            writePart("_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                            + "</Relationships>");
        } finally {
            out.close();
        }
    }

    private void endRow() throws IOException {
        if (rowOpen) {
            out.write("</row>");
            rowOpen = false;
        }
    }

    private void endSheet() throws IOException {
        if (!sheetOpen) {
            return;
        }
        endRow();
        out.write("</sheetData>");
        if (row > 1 && maxCol > 0) {
            out.write("<autoFilter ref=\"A1:" + colName(maxCol) + row + "\"/>");
        }
        out.write("</worksheet>");
        out.flush();
        zip.closeEntry();
        sheetOpen = false;
    }

    /** Otevře značku buňky; volající ji dokončí („/>“ nebo „>…</c>“). */
    private void cell(Style style) throws IOException {
        col++;
        maxCol = Math.max(maxCol, col);
        out.write("<c r=\"" + colName(col) + row + "\" s=\"" + style.id + "\"");
    }

    private void writePart(String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        out.write(content);
        out.flush();
        zip.closeEntry();
    }

    private String workbook() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"" + NS + "\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<sheet name=\"").append(xml(sheets.get(i))).append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return sb.append("</sheets></workbook>").toString();
    }

    private String workbookRels() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<Relationship Id=\"rId").append(i + 1)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i + 1).append(".xml\"/>");
        }
        sb.append("<Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return sb.append("</Relationships>").toString();
    }

    private String contentTypes() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return sb.append("</Types>").toString();
    }

    private String styles() {
        String money = currency.isBlank() ? "#,##0.00" : "#,##0.00\\ &quot;" + xml(currency) + "&quot;";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"" + NS + "\">"
                + "<numFmts count=\"3\">"
                + "<numFmt numFmtId=\"164\" formatCode=\"d\\.\\ m\\.\\ yyyy\"/>"
                
                + "<numFmt numFmtId=\"166\" formatCode=\"0\\ &quot;%&quot;\"/>"
                + "<numFmt numFmtId=\"167\" formatCode=\"" + money + "\"/>"
                + "</numFmts>"
                + "<fonts count=\"3\">"
                + "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>"
                + "</fonts>"
                + "<fills count=\"3\">"
                + "<fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF111115\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "</fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"7\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
                + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
                + "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"167\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
                + "<xf numFmtId=\"166\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }

    // ---------------------------------------------------------------- pomocné

    /** Název listu: Excel nepovolí delší než 31 znaků ani znaky : \ / ? * [ ]. */
    private static String sheetName(String name) {
        String s = (name == null || name.isBlank() ? "List" : name).replaceAll("[:\\\\/?*\\[\\]]", " ");
        return s.length() > 31 ? s.substring(0, 31) : s;
    }

    static String colName(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index;
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    private static String trim(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) {
            return String.valueOf((long) v);
        }
        return String.format(java.util.Locale.ROOT, "%.6f", v);
    }

    private static String xml(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> {
                    // XML 1.0 nepovoluje řídicí znaky kromě tabulátoru a konců řádků
                    if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
