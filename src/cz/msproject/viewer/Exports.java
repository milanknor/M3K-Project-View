package cz.msproject.viewer;

import javax.imageio.ImageIO;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Export CSV/PNG, tisk a PDF pro jedno hlavní okno. */
final class Exports {
    private static final double A4_W = 595.28;
    private static final double A4_H = 841.89;
    private static final double A3_W = 841.89;
    private static final double A3_H = 1190.55;
    /** Nad tímto počtem pixelů se obrázek nevykresluje (ochrana paměti). */
    private static final long MAX_PIXELS = 60_000_000L;

    private final MainWindow w;

    Exports(MainWindow w) {
        this.w = w;
    }

    private String exportDir() {
        return w.prefs.get("lastExportDir", w.currentPath == null || w.currentPath.getParent() == null
                ? System.getProperty("user.home") : w.currentPath.getParent().toString());
    }

    private String baseName() {
        return Ui.stripExt(w.currentPath.getFileName().toString());
    }

    private void rememberDir(File f) {
        if (f.getParent() != null) {
            w.prefs.put("lastExportDir", f.getParent());
        }
    }

    private boolean requireProject(String title) {
        if (w.project == null || w.currentPath == null) {
            JOptionPane.showMessageDialog(w, Str.t("Nejprve otevřete projekt."), title, JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        return true;
    }

    /** Rozměr obrázku tabulka + diagram v pixelech obrazovky. */
    private long[] imageSize() {
        Dimension chartSize = w.gantt.getChart().getPreferredSize();
        long width = (long) w.table.getPreferredSize().width + chartSize.width;
        long height = (long) Theme.HEADER + Math.max(1, w.tree.visible().size() * Theme.ROW);
        return new long[]{width, height};
    }

    // ---------------------------------------------------------------- CSV a PNG

    void exportCsv() {
        if (!requireProject(Str.t("Export CSV"))) {
            return;
        }
        File f = FileDialogs.save(w, Str.t("Exportovat zobrazené úkoly do CSV"), exportDir(), baseName() + ".csv", new FileDialogs.Filter("CSV (*.csv)", "csv"));
        if (f == null) {
            return;
        }
        try {
            List<Columns.Column> shown = new ArrayList<>();
            for (int i = 0; i < w.table.getColumnModel().getColumnCount(); i++) {
                shown.add(w.model.columnAt(w.table.getColumnModel().getColumn(i).getModelIndex()));
            }
            CsvExport.write(f.toPath(), w.tree.visible(), w.columns, shown);
            rememberDir(f);
            w.status.setText(Str.t("Exportováno ") + w.tree.visible().size() + Str.t(" řádků do ") + f.getAbsolutePath());
        } catch (Exception ex) {
            w.showError(Str.t("Export se nezdařil."), ex);
        }
    }

    // ---------------------------------------------------------------- Excel (XLSX)

    void exportXlsx() {
        if (!requireProject(Str.t("Export do Excelu"))) {
            return;
        }
        File f = FileDialogs.save(w, Str.t("Exportovat do Excelu"), exportDir(), baseName() + ".xlsx",
                new FileDialogs.Filter("Excel (*.xlsx)", "xlsx"));
        if (f == null) {
            return;
        }
        try {
            int rows = writeXlsx(f.toPath());
            rememberDir(f);
            w.status.setText(Str.t("Sešit uložen do ") + f.getAbsolutePath() + " (" + rows + Str.t(" řádků úkolů)"));
        } catch (Exception | OutOfMemoryError ex) {
            w.showError(Str.t("Export do Excelu se nezdařil."), ex);
        }
    }

    /** Zapíše sešit s listy Úkoly, Zdroje, Přiřazení, Vytížení a Projekt; vrací počet řádků úkolů. */
    int writeXlsx(Path target) throws IOException {
        org.mpxj.ProjectProperties props = w.project.getProjectProperties();
        String currency = Fmt.text(props.getCurrencySymbol());
        try (XlsxWriter x = new XlsxWriter(target, currency)) {
            List<Columns.Column> cols = new ArrayList<>();
            for (int i = 0; i < w.table.getColumnModel().getColumnCount(); i++) {
                Columns.Column c = w.model.columnAt(w.table.getColumnModel().getColumn(i).getModelIndex());
                if (c != null) {
                    cols.add(c);
                }
            }
            int[] widths = new int[cols.size()];
            String[] titles = new String[cols.size()];
            for (int i = 0; i < cols.size(); i++) {
                widths[i] = Math.max(8, cols.get(i).width / 7);
                titles[i] = cols.get(i).title;
            }
            x.sheet(Str.t("Úkoly"), widths);
            x.header(titles);
            for (TaskTree.Row row : w.tree.visible()) {
                x.row();
                for (Columns.Column c : cols) {
                    writeCell(x, c, row);
                }
            }
            writeResources(x, currency);
            writeAssignments(x);
            writeUsage(x);
            writeProject(x, props);
            return w.tree.visible().size();
        }
    }

    /** Buňka podle typu hodnoty: datum a čísla jdou do Excelu jako čísla, zbytek jako text. */
    private void writeCell(XlsxWriter x, Columns.Column c, TaskTree.Row row) throws IOException {
        Object key = w.columns.sortKey(c, row);
        String text = w.columns.text(c, row);
        if (text.isEmpty()) {
            x.blank();
            return;
        }
        if (key instanceof java.time.LocalDateTime d) {
            x.date(d);
            return;
        }
        if (key instanceof Number n && !(key instanceof Boolean)) {
            String k = c.key.toLowerCase(java.util.Locale.ROOT);
            XlsxWriter.Style style = k.contains("cost") ? XlsxWriter.Style.MONEY
                    : k.startsWith("percent") ? XlsxWriter.Style.PERCENT : XlsxWriter.Style.NUMBER;
            x.number(n.doubleValue(), style);
            return;
        }
        if (c == Columns.NAME) {
            x.text("    ".repeat(Math.min(row.depth, 8)) + text, row.isSummary() ? XlsxWriter.Style.BOLD : XlsxWriter.Style.TEXT);
            return;
        }
        x.text(text);
    }

    private void writeResources(XlsxWriter x, String currency) throws IOException {
        x.sheet(Str.t("Zdroje"), new int[]{8, 34, 14, 12, 14, 12, 14, 12});
        x.header(Str.t("ID"), Str.t("Název"), Str.t("Typ"), Str.t("Max. jednotky %"), Str.t("Standardní sazba"),
                Str.t("Práce (h)"), Str.t("Náklady") + (currency.isBlank() ? "" : " (" + currency + ")"), Str.t("Přetížený"));
        for (org.mpxj.Resource r : w.project.getResources()) {
            if (r.getName() == null || r.getName().isBlank()) {
                continue;
            }
            x.row();
            x.number(r.getID() == null ? 0 : r.getID().doubleValue(), XlsxWriter.Style.NUMBER);
            x.text(Fmt.text(r.getName()));
            x.text(Labels.resourceType(r.getType()));
            x.number(r.getMaxUnits() == null ? 0 : r.getMaxUnits().doubleValue(), XlsxWriter.Style.NUMBER);
            x.number(r.getStandardRate() == null ? 0 : r.getStandardRate().getAmount(), XlsxWriter.Style.MONEY);
            x.number(hours(r.getWork()), XlsxWriter.Style.NUMBER);
            x.number(r.getCost() == null ? 0 : r.getCost().doubleValue(), XlsxWriter.Style.MONEY);
            x.text(r.getOverAllocated() ? Str.t("ano") : Str.t("ne"));
        }
    }

    private void writeAssignments(XlsxWriter x) throws IOException {
        x.sheet(Str.t("Přiřazení"), new int[]{8, 40, 24, 12, 12, 14, 14, 14});
        x.header(Str.t("ID úkolu"), Str.t("Úkol"), Str.t("Zdroj"), Str.t("Jednotky %"), Str.t("Práce (h)"),
                Str.t("Náklady"), Str.t("Zahájení"), Str.t("Dokončení"));
        for (org.mpxj.Task t : w.project.getTasks()) {
            for (org.mpxj.ResourceAssignment a : t.getResourceAssignments()) {
                if (a.getResource() == null) {
                    continue;
                }
                x.row();
                x.number(t.getID() == null ? 0 : t.getID().doubleValue(), XlsxWriter.Style.NUMBER);
                x.text(Fmt.text(t.getName()));
                x.text(Fmt.text(a.getResource().getName()));
                x.number(a.getUnits() == null ? 0 : a.getUnits().doubleValue(), XlsxWriter.Style.NUMBER);
                x.number(hours(a.getWork()), XlsxWriter.Style.NUMBER);
                x.number(a.getCost() == null ? 0 : a.getCost().doubleValue(), XlsxWriter.Style.MONEY);
                x.date(a.getStart());
                x.date(a.getFinish());
            }
        }
    }

    private void writeUsage(XlsxWriter x) throws IOException {
        ResourceUsagePanel.Usage u = ResourceUsagePanel.compute(w.project, ResourceUsagePanel.Period.WEEKS);
        int[] widths = new int[u.headers.length + 3];
        widths[0] = 30;
        widths[1] = 18;
        widths[2] = 12;
        for (int i = 3; i < widths.length; i++) {
            widths[i] = 10;
        }
        x.sheet(Str.t("Vytížení (týdny)"), widths);
        x.row();
        x.text(Str.t("Zdroj"), XlsxWriter.Style.HEADER);
        x.text(Str.t("Detail"), XlsxWriter.Style.HEADER);
        x.text(Str.t("Celkem h"), XlsxWriter.Style.HEADER);
        for (String h : u.headers) {
            x.text(h, XlsxWriter.Style.HEADER);
        }
        for (int r = 0; r < u.names.size(); r++) {
            x.row();
            x.text(u.names.get(r));
            x.text(u.detail.get(r));
            double[] work = u.work.get(r);
            double total = 0;
            for (double v : work) {
                total += v;
            }
            x.number(total, XlsxWriter.Style.NUMBER);
            for (double v : work) {
                if (v == 0) {
                    x.blank();
                } else {
                    x.number(v, XlsxWriter.Style.NUMBER);
                }
            }
        }
    }

    private void writeProject(XlsxWriter x, org.mpxj.ProjectProperties props) throws IOException {
        x.sheet(Str.t("Projekt"), new int[]{28, 46});
        x.header(Str.t("Údaj"), Str.t("Hodnota"));
        pair(x, Str.t("Soubor"), w.currentPath.toAbsolutePath().toString());
        pair(x, Str.t("Název projektu"), Fmt.text(props.getProjectTitle()));
        pair(x, Str.t("Společnost"), Fmt.text(props.getCompany()));
        pair(x, Str.t("Vedoucí"), Fmt.text(props.getManager()));
        pair(x, Str.t("Zahájení"), Fmt.date(props.getStartDate()));
        pair(x, Str.t("Dokončení"), Fmt.date(props.getFinishDate()));
        pair(x, Str.t("Datum stavu"), Fmt.date(props.getStatusDate()));
        pair(x, Str.t("Úkolů celkem"), String.valueOf(w.tree.size()));
        pair(x, Str.t("Zobrazeno řádků"), String.valueOf(w.tree.visible().size()));
        pair(x, Str.t("Zdrojů"), String.valueOf(w.project.getResources().size()));
        pair(x, Str.t("Exportováno"), Fmt.dateTime(java.time.LocalDateTime.now()) + "  ·  " + MainWindow.APP_NAME + " " + MainWindow.VERSION);
    }

    private static void pair(XlsxWriter x, String name, String value) throws IOException {
        x.row();
        x.text(name, XlsxWriter.Style.BOLD);
        x.text(value);
    }

    /** Práce v hodinách (převod podle nastavení projektu). */
    private double hours(org.mpxj.Duration d) {
        if (d == null) {
            return 0;
        }
        try {
            return d.convertUnits(org.mpxj.TimeUnit.HOURS, w.project.getProjectProperties()).getDuration();
        } catch (RuntimeException ex) {
            return d.getDuration();
        }
    }

    void exportPng() {
        if (!requireProject(Str.t("Uložit PNG"))) {
            return;
        }
        long[] size = imageSize();
        if (size[0] * size[1] > MAX_PIXELS) {
            JOptionPane.showMessageDialog(w, Str.t("Obrázek by byl příliš velký (") + size[0] + " × " + size[1] + " px).\n"
                    + Str.t("Zmenšete měřítko nebo sbalte část osnovy."), Str.t("Uložit PNG"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        File f = FileDialogs.save(w, Str.t("Uložit Ganttův diagram jako PNG"), exportDir(), baseName() + ".png", new FileDialogs.Filter("PNG (*.png)", "png"));
        if (f == null) {
            return;
        }
        try {
            BufferedImage img = w.renderImage();
            ImageIO.write(img, "png", f);
            rememberDir(f);
            w.status.setText(Str.t("Diagram uložen do ") + f.getAbsolutePath() + " (" + img.getWidth() + " × " + img.getHeight() + " px)");
        } catch (Exception | OutOfMemoryError ex) {
            w.showError(Str.t("Uložení obrázku se nezdařilo."), ex);
        }
    }

    /** Uloží hotový obrázek jako PNG (např. časovou osu). */
    void savePng(BufferedImage img, String suffix, String title) {
        if (img == null || !requireProject(title)) {
            return;
        }
        File f = FileDialogs.save(w, title, exportDir(), baseName() + suffix + ".png", new FileDialogs.Filter("PNG (*.png)", "png"));
        if (f == null) {
            return;
        }
        try {
            ImageIO.write(img, "png", f);
            rememberDir(f);
            w.status.setText(Str.t("Uloženo do ") + f.getAbsolutePath() + " (" + img.getWidth() + " × " + img.getHeight() + " px)");
        } catch (Exception ex) {
            w.showError(Str.t("Uložení obrázku se nezdařilo."), ex);
        }
    }

    // ---------------------------------------------------------------- tisk a PDF

    private boolean checkPrintable(String title) {
        if (!requireProject(title)) {
            return false;
        }
        if (w.tree.visible().isEmpty()) {
            JOptionPane.showMessageDialog(w, Str.t("Není co tisknout – filtru neodpovídá žádný úkol."), title, JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        return true;
    }

    /** Obrázek pro tisk: dvojnásobné rozlišení, ale s ochranou proti obřím obrázkům (null = příliš velký). */
    private PrintLayout printLayout(boolean fitWidth) {
        long[] size = imageSize();
        long pixels = size[0] * size[1];
        if (pixels > MAX_PIXELS) {
            return null;
        }
        double scale = pixels * 4 > 40_000_000L ? 1.0 : 2.0;
        String title = w.currentPath.getFileName() + "   —   " + Fmt.text(w.project.getProjectProperties().getProjectTitle());
        return new PrintLayout(w.renderImage(scale), scale, w.tree.visible().size(), fitWidth, title);
    }

    private static JPanel printOptions(JCheckBox fit) {
        JPanel p = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        fit.setFont(Theme.BASE);
        p.add(fit);
        JLabel hint = new JLabel("<html><span style='color:" + Theme.MUTED_HEX + "'>"
                + Str.t("Tiskne se to, co je zobrazeno: viditelné řádky, zvolené sloupce a měřítko diagramu.<br>")
                + Str.t("Hlavička tabulky a časová osa se opakují na každé stránce.</span></html>"));
        hint.setFont(Theme.SMALL);
        p.add(hint);
        return p;
    }

    void printChart() {
        if (!checkPrintable(Str.t("Tisk"))) {
            return;
        }
        JCheckBox fit = new JCheckBox(Str.t("Přizpůsobit na šířku stránky (jinak měřítko 1:1 a více stránek vedle sebe)"), w.prefs.getBoolean("printFit", true));
        if (JOptionPane.showConfirmDialog(w, printOptions(fit), Str.t("Tisk"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        w.prefs.putBoolean("printFit", fit.isSelected());
        PrintLayout layout = printLayout(fit.isSelected());
        if (layout == null) {
            JOptionPane.showMessageDialog(w, Str.t("Diagram je pro tisk příliš velký. Zmenšete měřítko nebo sbalte část osnovy."), Str.t("Tisk"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName(MainWindow.APP_NAME + " – " + w.currentPath.getFileName());
        PageFormat pf = job.defaultPage();
        pf.setOrientation(PageFormat.LANDSCAPE);
        job.setPrintable(layout, pf);
        if (job.printDialog()) {
            try {
                job.print();
                w.status.setText(Str.t("Odesláno na tiskárnu: ") + (job.getPrintService() == null ? "" : job.getPrintService().getName()));
            } catch (PrinterException | RuntimeException ex) {
                w.showError(Str.t("Tisk se nezdařil."), ex);
            }
        }
    }

    void exportPdf() {
        if (!checkPrintable(Str.t("Uložit jako PDF"))) {
            return;
        }
        JCheckBox fit = new JCheckBox(Str.t("Přizpůsobit na šířku stránky (jinak měřítko 1:1 a více stránek vedle sebe)"), w.prefs.getBoolean("printFit", true));
        JComboBox<String> size = new JComboBox<>(new String[]{Str.t("A4 na šířku"), Str.t("A4 na výšku"), Str.t("A3 na šířku"), Str.t("A3 na výšku")});
        size.setSelectedIndex(Math.max(0, Math.min(3, w.prefs.getInt("pdfSize", 0))));
        size.setFont(Theme.BASE);
        JPanel p = printOptions(fit);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.add(Ui.label(Str.t("Stránka:")));
        row.add(size);
        p.add(row, 1);
        if (JOptionPane.showConfirmDialog(w, p, Str.t("Uložit jako PDF"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        w.prefs.putBoolean("printFit", fit.isSelected());
        w.prefs.putInt("pdfSize", size.getSelectedIndex());
        File f = FileDialogs.save(w, Str.t("Uložit jako PDF"), exportDir(), baseName() + ".pdf", new FileDialogs.Filter("PDF (*.pdf)", "pdf"));
        if (f == null) {
            return;
        }
        try {
            int pages = writePdf(f.toPath(), fit.isSelected(), size.getSelectedIndex());
            rememberDir(f);
            w.status.setText(Str.t("PDF uloženo do ") + f.getAbsolutePath() + " (" + pages + Str.t(" str.)"));
        } catch (Exception | OutOfMemoryError ex) {
            w.showError(Str.t("Uložení PDF se nezdařilo."), ex);
        }
    }

    /** Zapíše PDF; vrací počet stránek. {@code sizeIndex}: 0 A4 šířka, 1 A4 výška, 2 A3 šířka, 3 A3 výška. */
    int writePdf(Path target, boolean fitWidth, int sizeIndex) throws IOException {
        PrintLayout layout = printLayout(fitWidth);
        if (layout == null) {
            throw new IOException(Str.t("Diagram je příliš velký – zmenšete měřítko nebo sbalte část osnovy."));
        }
        double pw = sizeIndex >= 2 ? A3_H : A4_H;
        double ph = sizeIndex >= 2 ? A3_W : A4_W;
        if (sizeIndex % 2 == 1) {
            double t = pw;
            pw = ph;
            ph = t;
        }
        double margin = 36;
        double cw = pw - 2 * margin;
        double ch = ph - 2 * margin;
        int pages = layout.pageCount(cw, ch);
        final double fpw = pw;
        final double fph = ph;
        PdfWriter.write(target, pages, i -> {
            BufferedImage page = new BufferedImage((int) Math.round(fpw * 150 / 72), (int) Math.round(fph * 150 / 72), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = page.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, page.getWidth(), page.getHeight());
            g.scale(150 / 72.0, 150 / 72.0);
            g.translate(margin, margin);
            layout.paintPage(g, i, cw, ch);
            g.dispose();
            return page;
        }, pw, ph, w.currentPath.getFileName().toString());
        return pages;
    }
}
