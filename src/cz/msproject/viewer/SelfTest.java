package cz.msproject.viewer;

import org.mpxj.ProjectFile;
import org.mpxj.Task;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Automatické ověření: načtení skutečných souborů, filtrování, formátování, export a vykreslení. */
public final class SelfTest {
    private SelfTest() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static TaskTree.Row ancestorAtDepth(TaskTree.Row r, int depth) {
        TaskTree.Row x = r;
        while (x != null && x.depth > depth) {
            x = x.parent;
        }
        return x;
    }

    /** {@code --test soubor1 soubor2 …} */
    public static void run(String[] args) throws Exception {
        int total = 0;
        for (int i = 1; i < args.length; i++) {
            Path p = Path.of(args[i]);
            ProjectFile f;
            if (p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).contains("password")) {
                try {
                    ProjectLoader.load(p, null);
                    throw new AssertionError("Chráněný soubor se otevřel bez hesla: " + p);
                } catch (ProjectLoader.PasswordRequiredException expected) {
                    System.out.println(p.getFileName() + ": bez hesla správně vyžádáno heslo");
                }
                try {
                    ProjectLoader.load(p, "spatne-heslo");
                    throw new AssertionError("Chráněný soubor se otevřel se špatným heslem: " + p);
                } catch (ProjectLoader.PasswordRequiredException expected) {
                    System.out.println(p.getFileName() + ": špatné heslo odmítnuto");
                }
                f = ProjectLoader.load(p, "password");
            } else {
                f = ProjectLoader.load(p, null);
            }
            TaskTree tree = new TaskTree();
            tree.load(f);
            check(tree.size() > 0, "Žádné úkoly: " + p);
            int visibleAll = tree.visible().size();
            check(visibleAll > 0, "Žádné viditelné řádky: " + p);

            String currency = Fmt.text(f.getProjectProperties().getCurrencySymbol());
            for (TaskTree.Row r : tree.all()) {
                Task t = r.task;
                Fmt.duration(t.getDuration());
                Fmt.duration(t.getWork());
                Fmt.date(t.getStart());
                Fmt.date(t.getFinish());
                Fmt.predecessors(t);
                Fmt.successors(t);
                Fmt.resources(t);
                Fmt.money(t.getCost(), currency);
                Fmt.percent(t.getPercentageComplete());
                Labels.constraint(t.getConstraintType());
                Labels.priority(t.getPriority());
                check(r.parent == null || r.parent.children.contains(r), "Rozbitá hierarchie: " + t.getName());
                check(r.parent == null || r.depth == r.parent.depth + 1, "Špatná hloubka: " + t.getName());
            }
            Columns columns = new Columns();
            columns.setProject(f);
            TaskTableModel model = new TaskTableModel(tree, columns);
            model.setShown(columns.all());
            check(model.getColumnCount() == columns.all().size(), "Model nezobrazil všechny sloupce");
            for (int r = 0; r < model.getRowCount(); r++) {
                for (int c = 0; c < model.getColumnCount(); c++) {
                    check(model.getValueAt(r, c) != null, "Prázdná buňka");
                }
            }
            String lowerName = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            if (lowerName.contains("flags")) {
                check(!columns.custom().isEmpty(), "Soubor s vlastními poli nevrátil žádné vlastní sloupce: " + p);
            }
            if (!columns.custom().isEmpty()) {
                StringBuilder cs = new StringBuilder();
                for (Columns.Column c : columns.custom()) {
                    cs.append(cs.length() == 0 ? "" : ", ").append(c.title);
                }
                System.out.println("  vlastní pole (" + columns.custom().size() + "): " + cs);
            }
            model.setShown(new java.util.ArrayList<>());
            check(model.getColumnCount() == 1 && model.columnAt(0) == Columns.NAME, "Prázdný výběr sloupců nenechal název");

            // řazení v rámci úrovně: sestupně podle názvu, prázdné na konci, pak zpět
            tree.expandAll();
            List<TaskTree.Row> original = new java.util.ArrayList<>(tree.visible());
            tree.setOrder(columns.comparator(Columns.NAME, false));
            check(tree.visible().size() == original.size(), "Řazení změnilo počet řádků");
            java.text.Collator coll = java.text.Collator.getInstance(Fmt.CS);
            for (TaskTree.Row r : tree.all()) {
                TaskTree.Row prev = null;
                for (TaskTree.Row v : tree.visible()) {
                    if (v.parent != r) {
                        continue;
                    }
                    if (prev != null) {
                        String a = Fmt.text(prev.task.getName());
                        String b = Fmt.text(v.task.getName());
                        check(a.isBlank() ? b.isBlank() : b.isBlank() || coll.compare(a, b) >= 0, "Sestupné řazení podle názvu nesedí: " + a + " < " + b);
                    }
                    prev = v;
                }
            }
            for (int k = 1; k < tree.visible().size(); k++) {
                TaskTree.Row a = tree.visible().get(k - 1);
                TaskTree.Row b = tree.visible().get(k);
                check(b.depth <= a.depth + 1, "Řazení rozbilo hierarchii");
                check(b.depth == 0 || b.parent == (b.depth == a.depth + 1 ? a : ancestorAtDepth(a, b.depth - 1)), "Řazení rozbilo vazbu na rodiče");
            }
            tree.setOrder(null);
            check(tree.visible().equals(original), "Zrušení řazení nevrátilo pořadí ze souboru");
            tree.setOrder(columns.comparator(columns.byKey("start"), true));
            tree.setQuery("a");
            check(tree.visible().size() <= original.size(), "Řazení s hledáním přidalo řádky");
            tree.setQuery("");
            tree.setOrder(null);

            tree.setQuery("THIS_SHOULD_NOT_MATCH_73423");
            check(tree.visible().isEmpty(), "Hledání nefiltruje");
            tree.setQuery("");
            check(tree.visible().size() == visibleAll, "Zrušení hledání nevrací seznam");
            tree.setFilter(TaskTree.Filter.CRITICAL);
            for (TaskTree.Row r : tree.visible()) {
                check(r.task.getCritical() || r.isSummary(), "Filtr kritických propouští nekritický úkol");
            }
            tree.setFilter(TaskTree.Filter.MILESTONES);
            for (TaskTree.Row r : tree.visible()) {
                check(r.task.getMilestone() || r.isSummary(), "Filtr milníků propouští jiný úkol");
            }
            tree.setFilter(TaskTree.Filter.ALL);
            check(tree.visible().size() == visibleAll, "Zrušení filtru nevrací seznam");

            tree.collapseAll();
            int collapsed = tree.visible().size();
            for (TaskTree.Row r : tree.visible()) {
                check(r.depth == 0, "Po sbalení je vidět vnořený řádek");
            }
            tree.expandAll();
            check(tree.visible().size() == tree.size(), "Po rozbalení nejsou vidět všechny řádky");
            check(collapsed <= tree.size(), "Sbalení zvětšilo seznam");
            TaskTree.Row summary = tree.all().stream().filter(TaskTree.Row::isSummary).findFirst().orElse(null);
            if (summary != null) {
                tree.toggle(summary);
                check(tree.isCollapsed(summary), "Toggle nesbalil souhrn");
                check(tree.visible().size() < tree.size(), "Sbalený souhrn stále zobrazuje děti");
                TaskTree.Row child = summary.children.isEmpty() ? null : summary.children.get(0);
                if (child != null) {
                    check(tree.indexOfVisible(child) < 0, "Dítě sbaleného souhrnu je vidět");
                    tree.reveal(child);
                    check(tree.indexOfVisible(child) >= 0, "reveal() neodkryl dítě");
                }
                tree.expandAll();
            }
            tree.expandToLevel(1);
            for (TaskTree.Row r : tree.visible()) {
                check(r.depth == 0, "Úroveň 1 zobrazuje vnořené řádky");
            }
            tree.expandAll();

            Path csv = Files.createTempFile("msproject-viewer-", ".csv");
            try {
                List<Columns.Column> shown = new java.util.ArrayList<>();
                for (String k : Columns.DEFAULT_KEYS) {
                    shown.add(columns.byKey(k));
                }
                CsvExport.write(csv, tree.visible(), columns, shown);
                List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
                check(lines.size() == tree.visible().size() + 1, "CSV má špatný počet řádků");
                check(lines.get(0).startsWith("﻿ID;"), "CSV bez BOM/hlavičky");
            } finally {
                Files.deleteIfExists(csv);
            }
            total += tree.size();
            StringBuilder names = new StringBuilder();
            for (org.mpxj.Resource r : f.getResources()) {
                names.append(names.length() == 0 ? "" : ", ").append(r.getID()).append('=').append(r.getName());
            }
            System.out.println("  zdroje: [" + names + "]");
            System.out.println(p.getFileName() + ": " + tree.size() + " úkolů, " + f.getResources().size() + " zdrojů, formát "
                    + f.getProjectProperties().getFileType() + (f.getProjectProperties().getMppFileType() == null ? "" : " MPP" + f.getProjectProperties().getMppFileType()));
        }
        check(total > 0, "Nebyly zadány žádné testovací soubory");

        Path bad = Files.createTempFile("msproject-viewer-invalid-", ".mpp");
        try {
            Files.writeString(bad, "toto není soubor MPP");
            try {
                ProjectLoader.load(bad, null);
                throw new AssertionError("Neplatný soubor byl přijat");
            } catch (org.mpxj.MPXJException expected) {
                System.out.println("Neplatný MPP odmítnut: " + expected.getMessage());
            }
        } finally {
            Files.deleteIfExists(bad);
        }
        Path badXml = Files.createTempFile("msproject-viewer-invalid-", ".xml");
        try {
            Files.writeString(badXml, "<html><body>toto není projekt</body></html>");
            try {
                ProjectLoader.load(badXml, null);
                throw new AssertionError("Neplatný XML soubor byl přijat");
            } catch (Exception expected) {
                System.out.println("Neplatný XML odmítnut: " + expected.getClass().getSimpleName());
            }
        } finally {
            try {
                Files.deleteIfExists(badXml);
            } catch (java.io.IOException stillOpen) {
                // MPXJ po odmítnutí souboru drží handle až do GC – soubor se smaže při ukončení
                badXml.toFile().deleteOnExit();
            }
        }
        System.out.println("PASS: načteno " + total + " úkolů");
    }

    /** {@code --ui-test soubor výstup.png} – sestaví okno, ověří interakce a vykreslí PNG. */
    public static void uiTest(String[] args) throws Exception {
        Path file = Path.of(args[1]);
        Path out = Path.of(args[2]);
        GanttPanel.Zoom zoom = args.length > 3 ? GanttPanel.Zoom.valueOf(args[3]) : GanttPanel.Zoom.WEEKS;
        ProjectFile project = ProjectLoader.load(file, null);
        SwingUtilities.invokeAndWait(() -> {
            MainWindow w = new MainWindow();
            try {
                w.setSize(1400, 860);
                w.apply(file, project);
                w.addNotify();
                w.validate();
                w.gantt.setZoom(zoom);
                int total = w.tree.visible().size();
                check(total > 0, "Bez řádků");
                check(w.model.getRowCount() == total, "Model tabulky nesouhlasí");
                w.search.setText("THIS_SHOULD_NOT_MATCH_73423");
                check(w.tree.visible().isEmpty(), "Hledání v okně nefiltruje");
                w.search.setText("");
                check(w.tree.visible().size() == total, "Vymazání hledání nevrací seznam");
                w.filterBox.setSelectedItem(TaskTree.Filter.CRITICAL);
                for (TaskTree.Row r : w.tree.visible()) {
                    check(r.task.getCritical() || r.isSummary(), "Filtr v okně propouští nekritický úkol");
                }
                w.filterBox.setSelectedItem(TaskTree.Filter.ALL);
                check(w.tree.visible().size() == total, "Zrušení filtru v okně nevrací seznam");
                w.table.setRowSelectionInterval(0, 0);
                TaskTree.Row first = w.tree.visible().get(0);
                if (first.isSummary()) {
                    w.toggle(first);
                    check(w.tree.visible().size() < total, "Sbalení v okně nefunguje");
                    w.toggle(first);
                    check(w.tree.visible().size() == total, "Rozbalení v okně nefunguje");
                }
                w.goToTask(w.tree.all().get(w.tree.all().size() - 1).task);
                check(w.table.getSelectedRow() == w.tree.visible().size() - 1, "goToTask nevybral poslední úkol");
                // řazení kliknutím na hlavičku: vzestupně → sestupně → zrušit
                int nameView = w.table.convertColumnIndexToView(w.model.indexOf(Columns.NAME));
                check(nameView >= 0, "Sloupec Název není zobrazen");
                w.sortBy(Columns.NAME);
                check(w.sortColumn == Columns.NAME && w.sortAscending && w.tree.isSorted(), "Řazení v okně se nezapnulo");
                check(String.valueOf(w.table.getColumnModel().getColumn(nameView).getHeaderValue()).endsWith("▲"), "Hlavička bez šipky řazení");
                w.sortBy(Columns.NAME);
                check(!w.sortAscending, "Druhé kliknutí nepřepnulo na sestupné řazení");
                w.sortBy(Columns.NAME);
                check(w.sortColumn == null && !w.tree.isSorted(), "Třetí kliknutí nezrušilo řazení");
                // výběr sloupců: přidat vlastní/jiný sloupec a vrátit výchozí
                int before = w.table.getColumnCount();
                java.util.List<String[]> setting = new java.util.ArrayList<>();
                for (String k : Columns.DEFAULT_KEYS) {
                    setting.add(new String[]{k, ""});
                }
                setting.add(new String[]{"deadline", "80"});
                setting.add(new String[]{"custom:TEXT99", ""});
                w.applyColumns(setting);
                check(w.table.getColumnCount() == before + 1, "Přidání sloupce Termín se neprojevilo");
                check(w.model.getValueAt(0, w.model.indexOf(w.columns.byKey("deadline"))) != null, "Sloupec Termín nevrací hodnotu");
                setting.remove(setting.size() - 1);
                setting.remove(setting.size() - 1);
                w.applyColumns(setting);
                check(w.table.getColumnCount() == before, "Návrat na výchozí sloupce se neprojevil");
                w.table.setRowSelectionInterval(0, 0);
                w.details.tabs().setSelectedIndex(0);
                w.validate();
                BufferedImage img = w.renderImage();
                check(img.getWidth() > 100 && img.getHeight() > Theme.HEADER, "Prázdný obrázek");
                ImageIO.write(img, "png", out.toFile());
                // tisk/PDF: stránkování a zápis PDF
                BufferedImage img2 = w.renderImage(2.0);
                check(img2.getWidth() >= img.getWidth() * 2 - 2 && img2.getHeight() >= img.getHeight() * 2 - 2, "Obrázek 2× nemá dvojnásobné rozměry");
                PrintLayout fitLayout = new PrintLayout(img2, 2.0, w.tree.visible().size(), true, "test");
                PrintLayout oneToOne = new PrintLayout(img2, 2.0, w.tree.visible().size(), false, "test");
                check(fitLayout.pageCount(770, 520) >= 1, "Stránkování nedalo žádnou stránku");
                check(oneToOne.pageCount(770, 520) >= fitLayout.pageCount(770, 520), "Měřítko 1:1 má méně stránek než přizpůsobení");
                PrintLayout tall = new PrintLayout(img2, 2.0, 5000, true, "test");
                check(tall.pageCount(770, 520) > 1, "5000 řádků se vešlo na jednu stránku");
                BufferedImage pageImg = fitLayout.renderPage(0, 770, 520, 72);
                check(pageImg.getWidth() == 770 && pageImg.getHeight() == 520, "Stránka má špatné rozměry");
                Path pdf = Files.createTempFile("m3k-print-", ".pdf");
                try {
                    int pages = w.writePdf(pdf, true, 0);
                    byte[] bytes = Files.readAllBytes(pdf);
                    check(pages >= 1 && bytes.length > 1000, "PDF je prázdné");
                    String head = new String(bytes, 0, 8, StandardCharsets.ISO_8859_1);
                    String tail = new String(bytes, bytes.length - 6, 6, StandardCharsets.ISO_8859_1);
                    check(head.startsWith("%PDF-1.4") && tail.contains("%%EOF"), "PDF nemá hlavičku/patičku");
                    String all = new String(bytes, StandardCharsets.ISO_8859_1);
                    check(all.contains("/Count " + pages + " ") && all.contains("/Type /Page ") && all.contains("startxref"), "PDF nemá strukturu stránek");
                    Path keep = out.resolveSibling(stripExt(out.getFileName().toString()) + ".pdf");
                    Files.copy(pdf, keep, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } finally {
                    Files.deleteIfExists(pdf);
                }
                BufferedImage window = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = window.createGraphics();
                w.getRootPane().printAll(g);
                g.dispose();
                Path windowPng = out.resolveSibling(stripExt(out.getFileName().toString()) + "-window.png");
                ImageIO.write(window, "png", windowPng.toFile());
                w.gantt.setZoom(GanttPanel.Zoom.DAYS);
                check(Math.abs(w.gantt.getPxPerDay() - GanttPanel.Zoom.DAYS.pxPerDay) < 1e-9, "Měřítko Dny nenastavilo 42 px/den");
                int daysWidth = w.gantt.getChart().getPreferredSize().width;
                w.gantt.setZoom(GanttPanel.Zoom.WEEKS);
                check(w.gantt.getChart().getPreferredSize().width < daysWidth, "Měřítko Týdny nezúžilo diagram");
                w.gantt.zoomBy(0.5, -1);
                check(w.zoomBox.getSelectedIndex() == -1, "Vlastní přiblížení nezrušilo výběr měřítka");
                if (w.resources.table().getRowCount() > 0) {
                    w.resources.table().setRowSelectionInterval(0, 0);
                }
                List<LicenseDialog.Entry> licenses = LicenseDialog.entries();
                check(licenses.size() >= 20, "Málo licenčních položek: " + licenses.size());
                check(licenses.get(0).resource().equals("LICENSE.txt") && LicenseDialog.text("LICENSE.txt").contains("freeware")
                        && LicenseDialog.text("LICENSE.txt").contains("LGPL"), "Chybí licenční podmínky programu");
                for (LicenseDialog.Entry e : licenses) {
                    check(LicenseDialog.text(e.resource()).length() > 50, "Prázdný licenční text: " + e.resource());
                }
                LicenseDialog dlg = new LicenseDialog(w);
                check(dlg.currentText().contains("Milan Knor"), "Dialog Licence nezobrazil licenci programu");
                dlg.addNotify();
                dlg.validate();
                BufferedImage dlgImg = new BufferedImage(dlg.getWidth(), dlg.getHeight(), BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D dg = dlgImg.createGraphics();
                dlg.getContentPane().printAll(dg);
                dg.dispose();
                ImageIO.write(dlgImg, "png", out.resolveSibling(stripExt(out.getFileName().toString()) + "-licenses.png").toFile());
                dlg.dispose();
                System.out.println("PASS: UI hledání, filtr, sbalení, navigace, vykreslení (" + img.getWidth() + "×" + img.getHeight()
                        + "), měřítko, licence (" + licenses.size() + " položek) → " + out + ", " + windowPng);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                w.dispose();
            }
        });
        new File(out.toString()).length();
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }
}
