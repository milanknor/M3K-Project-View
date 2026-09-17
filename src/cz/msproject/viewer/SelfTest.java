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
            if (lowerName.contains("baseline")) {
                check(!columns.baselines().isEmpty(), "Soubor se základním plánem nenabídl žádný plán");
                System.out.println("  základní plány: " + columns.baselines());
            }
            if (lowerName.contains("baselines")) {
                check(columns.baselines().size() >= 2, "Soubor se základními plány nenabídl více plánů: " + columns.baselines());
                int other = columns.baselines().get(columns.baselines().get(0) == 0 ? 1 : 0);
                check(columns.byKey("baseline" + other + "Start") != null, "Chybí sloupec základního plánu " + other);
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

            // rozšířený filtr: zdroj, období, procenta, text ve sloupci; serializace
            String firstResource = null;
            for (org.mpxj.Resource res : f.getResources()) {
                if (res.getName() != null && !res.getName().isBlank() && !res.getTaskAssignments().isEmpty()) {
                    firstResource = res.getName();
                    break;
                }
            }
            if (firstResource != null) {
                TaskFilter byRes = new TaskFilter("", firstResource, null, null, null, null, "", "", true);
                tree.setExtra(byRes.predicate(columns));
                check(tree.hasExtra() && tree.isFiltering(), "Rozšířený filtr se nezapnul");
                for (TaskTree.Row r : tree.visible()) {
                    if (!r.isSummary()) {
                        check(Fmt.resources(r.task).toLowerCase(java.util.Locale.ROOT).contains(firstResource.toLowerCase(java.util.Locale.ROOT)),
                                "Filtr podle zdroje propustil úkol bez zdroje: " + r.task.getName());
                    }
                }
                check(tree.matchCount() > 0, "Filtr podle zdroje s přiřazeními nenašel nic");
            }
            TaskFilter pct = new TaskFilter("", "", null, null, 0, 0, "", "", true);
            tree.setExtra(pct.predicate(columns));
            for (TaskTree.Row r : tree.visible()) {
                if (!r.isSummary()) {
                    double v = r.task.getPercentageComplete() == null ? 0 : r.task.getPercentageComplete().doubleValue();
                    check(v == 0, "Filtr 0–0 % propustil rozpracovaný úkol");
                }
            }
            TaskFilter range = new TaskFilter("", "", java.time.LocalDate.of(1900, 1, 1), java.time.LocalDate.of(2200, 12, 31), null, null, "", "", false);
            tree.setExtra(range.predicate(columns));
            int datedTasks = 0;
            for (TaskTree.Row r : tree.all()) {
                if (r.task.getStart() != null || r.task.getFinish() != null) {
                    datedTasks++;
                }
            }
            check(tree.matchCount() == datedTasks, "Filtr období 1900–2200 nevrátil všechny úkoly s daty: " + tree.matchCount() + " ≠ " + datedTasks);
            TaskFilter textF = new TaskFilter("Test", "", null, null, null, null, "name", "THIS_SHOULD_NOT_MATCH", false);
            tree.setExtra(textF.predicate(columns));
            check(tree.visible().isEmpty(), "Filtr textu ve sloupci propustil řádky");
            TaskFilter round = TaskFilter.deserialize(textF.serialize());
            check(round != null && round.equals(textF), "Serializace filtru není vratná");
            check(TaskFilter.EMPTY.predicate(columns) == null, "Prázdný filtr vrací predikát");
            check(FilterDialog.parseDate("15. 9. 2026").equals(java.time.LocalDate.of(2026, 9, 15)) && FilterDialog.parseDate("2026-09-15") != null, "Parsování data selhalo");
            tree.setExtra(null);
            check(!tree.hasExtra() && tree.visible().size() == visibleAll, "Zrušení rozšířeného filtru nevrátilo seznam");

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
            // vytížení zdrojů: konzistentní rozměry, kapacita jen u pracovních zdrojů, součet práce ≥ 0
            for (ResourceUsagePanel.Period per : ResourceUsagePanel.Period.values()) {
                ResourceUsagePanel.Usage u = ResourceUsagePanel.compute(f, per);
                check(u.headers.length == u.buckets.size(), "Vytížení: hlavičky nesedí s obdobími");
                check(u.names.size() == u.work.size() && u.names.size() == u.capacity.size(), "Vytížení: řádky nesedí");
                for (int r = 0; r < u.names.size(); r++) {
                    check(u.work.get(r).length == u.buckets.size(), "Vytížení: špatný počet hodnot");
                    for (double v : u.work.get(r)) {
                        check(v >= 0 && !Double.isNaN(v), "Vytížení: záporná/NaN práce");
                    }
                }
                if (!f.getResourceAssignments().isEmpty() && u.buckets.isEmpty()) {
                    boolean anyDates = f.getResourceAssignments().stream().anyMatch(a -> a.getResource() != null && a.getTask() != null
                            && (a.getStart() != null || a.getTask().getStart() != null) && (a.getFinish() != null || a.getTask().getFinish() != null));
                    check(!anyDates, "Vytížení: přiřazení s daty, ale žádná období");
                }
                if (per == ResourceUsagePanel.Period.WEEKS && u.overCells > 0) {
                    System.out.println("  vytížení: " + u.names.size() + " zdrojů, " + u.buckets.size() + " týdnů, přetížených období " + u.overCells);
                }
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

        // pomocné třídy: escapování HTML, log chyb, nedávné soubory
        check(Fmt.html("a<b>'\"&\n").equals("a&lt;b&gt;&#39;&quot;&amp;<br>"), "Fmt.html neescapuje správně: " + Fmt.html("a<b>'\"&\n"));
        check(ErrorLog.describe("test", new IllegalStateException("x")).contains("IllegalStateException: x"), "ErrorLog.describe bez výjimky");
        check(ErrorLog.isOutOfMemory(new RuntimeException(new OutOfMemoryError())) && !ErrorLog.isOutOfMemory(new RuntimeException()), "isOutOfMemory nesedí");
        java.util.prefs.Preferences testPrefs = java.util.prefs.Preferences.userRoot().node("m3k-project-view-test");
        try {
            testPrefs.clear();
            testPrefs.put("recent0", "C:\\neexistuje\\a.mpp");
            testPrefs.put("recent1", "C:\\neplatna<>|cesta?*.mpp");
            RecentFiles rf = new RecentFiles(testPrefs);
            check(rf.list().size() >= 1, "RecentFiles nevrátil platnou položku");
            rf.add(Path.of("C:\\x\\b.mpp"));
            check(rf.list().get(0).toString().endsWith("b.mpp"), "RecentFiles.add nedal soubor na začátek");
            rf.add(Path.of("C:\\x\\b.mpp"));
            check(rf.list().stream().filter(x -> x.toString().endsWith("b.mpp")).count() == 1, "RecentFiles duplikuje položky");
            for (int i = 0; i < 20; i++) {
                rf.add(Path.of("C:\\x\\f" + i + ".mpp"));
            }
            check(rf.list().size() == RecentFiles.MAX, "RecentFiles neomezuje délku");
            rf.clear();
            check(rf.list().isEmpty(), "RecentFiles.clear nevymazal");
        } finally {
            testPrefs.removeNode();
        }

        // překlady, kontrola verze
        check(Str.dictionarySize() > 300, "Slovník překladů je podezřele malý: " + Str.dictionarySize());
        check(Str.isEnglish() ? Str.t("Soubor").equals("File") : Str.t("Soubor").equals("Soubor"), "Str.t nevrací očekávaný text");
        check(Str.t("__neexistuje__").equals("__neexistuje__"), "Str.t nevrací nepřeložený text beze změny");

        // výkon: syntetický projekt s 10 000 úkoly – načtení stromu, sloupce, řazení, filtr, text buněk
        {
            long t0 = System.nanoTime();
            ProjectFile big = new ProjectFile();
            big.getProjectProperties().setStartDate(java.time.LocalDateTime.of(2026, 1, 5, 8, 0));
            org.mpxj.ProjectCalendar cal = big.addDefaultBaseCalendar();
            big.setDefaultCalendar(cal);
            org.mpxj.Resource res = big.addResource();
            res.setName("Zdroj A");
            Task summary = null;
            Task prev = null;
            int n = 10_000;
            for (int i = 1; i <= n; i++) {
                Task t;
                if (i % 50 == 1) {
                    summary = big.addTask();
                    summary.setName("Fáze " + (i / 50 + 1));
                    t = summary;
                } else {
                    t = summary.addTask();
                    t.setName("Úkol " + i);
                    java.time.LocalDateTime s = java.time.LocalDateTime.of(2026, 1, 5, 8, 0).plusDays(i % 400);
                    t.setStart(s);
                    t.setFinish(s.plusDays(3));
                    t.setDuration(org.mpxj.Duration.getInstance(3, org.mpxj.TimeUnit.DAYS));
                    t.setPercentageComplete(i % 101);
                    if (i % 7 == 0) {
                        t.setText(1, "vlastní " + i);
                    }
                    if (prev != null && i % 3 == 0) {
                        t.addPredecessor(new org.mpxj.Relation.Builder().predecessorTask(prev).type(org.mpxj.RelationType.FINISH_START));
                    }
                    if (i % 5 == 0) {
                        t.addResourceAssignment(res);
                    }
                    prev = t;
                }
            }
            long t1 = System.nanoTime();
            TaskTree bigTree = new TaskTree();
            bigTree.load(big);
            long t2 = System.nanoTime();
            check(bigTree.size() == n, "Syntetický projekt: špatný počet řádků " + bigTree.size());
            Columns bigCols = new Columns();
            bigCols.setProject(big);
            long t3 = System.nanoTime();
            check(!bigCols.custom().isEmpty(), "Syntetický projekt: vlastní pole Text1 nebylo nalezeno");
            bigTree.setOrder(bigCols.comparator(Columns.NAME, false));
            bigTree.setQuery("9");
            long t4 = System.nanoTime();
            bigTree.setQuery("");
            bigTree.setOrder(null);
            TaskTableModel bigModel = new TaskTableModel(bigTree, bigCols);
            bigModel.setShown(bigCols.all());
            for (int r = 0; r < bigModel.getRowCount(); r += 97) {
                for (int c = 0; c < bigModel.getColumnCount(); c++) {
                    bigModel.getValueAt(r, c);
                }
            }
            for (int r = 0; r < bigModel.getRowCount(); r += 1000) {
                check(bigTree.find(bigTree.visible().get(r).task) == bigTree.visible().get(r), "find() nevrací řádek");
                check(bigTree.indexOfVisible(bigTree.visible().get(r)) == r, "indexOfVisible() nesedí");
            }
            long t5 = System.nanoTime();
            System.out.printf(java.util.Locale.ROOT, "  výkon 10 000 úkolů: sestavení %.2f s, strom %.2f s, sloupce %.2f s, řazení+hledání %.2f s, buňky %.2f s%n",
                    (t1 - t0) / 1e9, (t2 - t1) / 1e9, (t3 - t2) / 1e9, (t4 - t3) / 1e9, (t5 - t4) / 1e9);
            check((t2 - t1) < 5e9 && (t3 - t2) < 10e9 && (t4 - t3) < 5e9, "Syntetický projekt: příliš pomalé");
        }

        // odolnost: 20 000 úrovní vnořené osnovy (řetěz) nesmí přetéct zásobník; síťové cesty se poznají
        {
            ProjectFile deep = new ProjectFile();
            Task parent = deep.addTask();
            parent.setName("L0");
            for (int i = 1; i < 20_000; i++) {
                parent = parent.addTask();
                parent.setName("L" + i);
            }
            TaskTree deepTree = new TaskTree();
            deepTree.load(deep);
            check(deepTree.size() == 20_000 && deepTree.maxDepthInProject() == 20_000, "Hluboká osnova: špatný počet/hloubka");
            deepTree.expandAll();
            check(deepTree.visible().size() == 20_000, "Hluboká osnova: rozbalení nezobrazilo vše");
            deepTree.setQuery("L19999");
            check(deepTree.matchCount() == 1 && deepTree.visible().size() == 20_000, "Hluboká osnova: filtr nedržel cestu k listu");
            deepTree.setQuery("");
            deepTree.collapseAll();
            check(deepTree.visible().size() == 1, "Hluboká osnova: sbalení");
            TaskTree.Row leaf = deepTree.all().get(19_999);
            deepTree.reveal(leaf);
            check(deepTree.indexOfVisible(leaf) == 19_999, "Hluboká osnova: reveal");
            check(MainWindow.isNetworkPath(Path.of("\\\\server\\share\\x.mpp")) && !MainWindow.isNetworkPath(Path.of("C:\\x\\y.mpp")), "isNetworkPath nesedí");
        }

        // škodlivé a podvržené soubory: vše se musí odmítnout rychle a bez přístupu k síti/souborům
        {
            String base = Files.readString(Path.of(args[1]).resolveSibling("mspdirelations.xml"), StandardCharsets.UTF_8);
            int nl = base.indexOf('\n');
            String head = base.substring(0, nl);
            String rest = base.substring(nl + 1);
            java.util.Map<String, String> samples = new java.util.LinkedHashMap<>();
            samples.put("xxe-file.xml", head + "\n<!DOCTYPE Project [<!ENTITY xxe SYSTEM \"file:///C:/Windows/win.ini\">]>\n"
                    + rest.replace("<Name>Task 1</Name>", "<Name>XXE:&xxe;:END</Name>"));
            samples.put("xxe-net.xml", head + "\n<!DOCTYPE Project [<!ENTITY net SYSTEM \"http://127.0.0.1:9/x\">]>\n"
                    + rest.replace("<Name>Task 1</Name>", "<Name>&net;</Name>"));
            samples.put("xxe-dtd.xml", head + "\n<!DOCTYPE Project SYSTEM \"http://127.0.0.1:9/evil.dtd\">\n" + rest);
            StringBuilder ents = new StringBuilder("<!ENTITY a0 \"lol\">");
            for (int i = 1; i < 10; i++) {
                ents.append("<!ENTITY a").append(i).append(" \"").append(("&a" + (i - 1) + ";").repeat(10)).append("\">");
            }
            samples.put("bomb.xml", head + "\n<!DOCTYPE Project [" + ents + "]>\n" + rest.replace("<Name>Task 1</Name>", "<Name>&a9;</Name>"));
            samples.put("deep.xml", head + "\n<Project xmlns=\"http://schemas.microsoft.com/project\"><Tasks>" + "<Task>".repeat(50_000) + "</Task>".repeat(50_000) + "</Tasks></Project>");
            samples.put("zip-as.xml", "PK\u0003\u0004zip content pretending to be xml");
            samples.put("xml-as.mpp", base);
            samples.put("xml-as.mpx", base);
            samples.put("junk.mpd", "not an access database at all, just text ......................");
            samples.put("script.xml", head + "\n" + rest.replace("<Name>Task 1</Name>", "<Name>&lt;html&gt;&lt;img src='http://127.0.0.1:9/i'&gt;</Name>"));
            Path dir = Files.createTempDirectory("m3k-malicious-");
            try {
                for (java.util.Map.Entry<String, String> e : samples.entrySet()) {
                    Path f = dir.resolve(e.getKey());
                    Files.writeString(f, e.getValue(), StandardCharsets.UTF_8);
                    long t0 = System.nanoTime();
                    boolean rejected;
                    String outcome;
                    try {
                        ProjectFile pf = ProjectLoader.load(f, null);
                        rejected = false;
                        StringBuilder names = new StringBuilder();
                        for (Task t : pf.getTasks()) {
                            names.append(Fmt.text(t.getName())).append('|');
                        }
                        outcome = "načteno: " + names;
                        check(!names.toString().contains("[fonts]") && !names.toString().contains("win.ini"), "XXE: obsah lokálního souboru pronikl do dat: " + f);
                        check(!names.toString().contains("lollol"), "Entity bomb: expanze prošla: " + f);
                        if (e.getKey().equals("script.xml")) {
                            for (Task t : pf.getTasks()) {
                                String n = Fmt.text(t.getName());
                                check(!javax.swing.plaf.basic.BasicHTML.isHTMLString(n), "Název úkolu by Swing vykreslil jako HTML: " + n);
                            }
                        }
                    } catch (Exception ex) {
                        rejected = true;
                        outcome = "odmítnuto: " + ProjectLoader.rootCause(ex).getClass().getSimpleName();
                    }
                    double sec = (System.nanoTime() - t0) / 1e9;
                    check(sec < 20, "Škodlivý soubor se zpracovával příliš dlouho (" + sec + " s): " + f);
                    boolean mustReject = !e.getKey().equals("script.xml");
                    check(!mustReject || rejected, "Podvržený soubor nebyl odmítnut: " + f + " (" + outcome + ")");
                    System.out.printf(java.util.Locale.ROOT, "  ochrana %-12s %s (%.2f s)%n", e.getKey(), outcome.length() > 90 ? outcome.substring(0, 90) + "…" : outcome, sec);
                }
            } finally {
                try (var st = Files.list(dir)) {
                    for (Path f : st.toList()) {
                        Files.deleteIfExists(f);
                    }
                }
                Files.deleteIfExists(dir);
            }
            check(!javax.swing.plaf.basic.BasicHTML.isHTMLString(Fmt.text("<html><img src='http://x/'>")), "Fmt.text nezneškodnil <html>");
            check(!javax.swing.plaf.basic.BasicHTML.isHTMLString(Fmt.text("  <HTML>x")), "Fmt.text nezneškodnil <HTML> za mezerami");
            check(Fmt.text("běžný <b>text</b>").equals("běžný <b>text</b>"), "Fmt.text mění běžný text");
            check(!ProjectLoader.signatureMatches(ProjectLoader.Kind.MPP, "MPX,abc".getBytes(StandardCharsets.US_ASCII))
                    && ProjectLoader.signatureMatches(ProjectLoader.Kind.XML, "\uFEFF  <?xml".getBytes(StandardCharsets.UTF_8))
                    && ProjectLoader.kindByExtension(Path.of("a.MPT")) == ProjectLoader.Kind.MPP && ProjectLoader.kindByExtension(Path.of("a.exe")) == null,
                    "Kontrola signatury/přípony nesedí");
            check("".equals(System.getProperty("javax.xml.accessExternalDTD")), "Limity XML nejsou nastavené");
        }

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
                w.addNotify();
                w.validate();
                check(MainWindow.WINDOWS.contains(w), "Okno není v seznamu oken");
                BufferedImage startImg = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D sg = startImg.createGraphics();
                w.getRootPane().printAll(sg);
                sg.dispose();
                ImageIO.write(startImg, "png", out.resolveSibling(stripExt(out.getFileName().toString()) + "-start.png").toFile());
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
                // téma: tmavý režim a škálování mění rozměry a barvy a jde vrátit
                int rowBefore = Theme.ROW;
                boolean darkBefore = Theme.DARK;
                float scaleBefore = Theme.SCALE;
                Theme.init(true, scaleBefore + 0.3f);
                check(Theme.DARK && Theme.ROW > rowBefore && !Theme.BG.equals(java.awt.Color.WHITE) && Theme.HTML_BODY.contains(Theme.INK_HEX), "Tmavé téma se nenastavilo");
                Theme.init(false, 1f);
                check(!Theme.DARK && Theme.BG.equals(java.awt.Color.WHITE), "Světlé téma se nenastavilo");
                Theme.init(darkBefore, scaleBefore);
                check(Theme.ROW == rowBefore && Theme.DARK == darkBefore, "Návrat na původní téma selhal");
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
                    int pages = w.exports.writePdf(pdf, true, 0);
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
                // export do Excelu: zapsat sešit a ověřit, že má všechny díly a platné XML
                Path xlsx = out.resolveSibling(stripExt(out.getFileName().toString()) + ".xlsx");
                int xlsxRows = w.exports.writeXlsx(xlsx);
                check(xlsxRows == w.tree.visible().size(), "XLSX nezapsal všechny řádky úkolů");
                java.util.Set<String> parts = new java.util.HashSet<>();
                String sheet1 = "";
                try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(xlsx.toFile())) {
                    for (java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries(); en.hasMoreElements(); ) {
                        java.util.zip.ZipEntry ze = en.nextElement();
                        parts.add(ze.getName());
                        byte[] data = zf.getInputStream(ze).readAllBytes();
                        if (ze.getName().endsWith(".xml") || ze.getName().endsWith(".rels")) {
                            javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                            dbf.setNamespaceAware(true);
                            dbf.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(data));
                        }
                        if (ze.getName().equals("xl/worksheets/sheet1.xml")) {
                            sheet1 = new String(data, StandardCharsets.UTF_8);
                        }
                    }
                }
                check(parts.contains("[Content_Types].xml") && parts.contains("xl/workbook.xml")
                        && parts.contains("xl/styles.xml") && parts.contains("xl/worksheets/sheet5.xml"),
                        "XLSX nemá všechny díly: " + parts);
                check(sheet1.contains("<sheetData>") && sheet1.contains("inlineStr") && sheet1.contains("autoFilter"),
                        "List Úkoly nemá očekávaný obsah");
                check(XlsxWriter.colName(1).equals("A") && XlsxWriter.colName(26).equals("Z")
                        && XlsxWriter.colName(27).equals("AA") && XlsxWriter.colName(28).equals("AB"), "Špatné označení sloupce");

                // kopírování vybraných řádků do schránky (text, ne obsah schránky)
                w.table.setRowSelectionInterval(0, Math.min(2, w.tree.visible().size() - 1));
                String copied = w.selectionAsText();
                String[] copiedLines = copied.split("\\R");
                check(copiedLines.length == Math.min(3, w.tree.visible().size()) + 1, "Kopírování nevrátilo hlavičku a vybrané řádky: " + copiedLines.length);
                check(copiedLines[0].contains("\t") && copiedLines[1].contains("\t"), "Kopírovaný text není oddělený tabulátory");
                w.table.clearSelection();
                w.table.setRowSelectionInterval(0, 0);

                // automatické šířky sloupců: název musí být širší než výchozí úzká šířka
                int nameCol = w.table.convertColumnIndexToView(w.model.indexOf(Columns.NAME));
                check(w.table.getColumnModel().getColumn(nameCol).getPreferredWidth() >= 80,
                        "Sloupec Název se po otevření nepřizpůsobil obsahu");

                // filtry podle data
                for (TaskTree.Filter f : new TaskTree.Filter[]{TaskTree.Filter.THIS_WEEK, TaskTree.Filter.NEXT_3_WEEKS}) {
                    w.filterBox.setSelectedItem(f);
                    check(w.tree.visible().size() <= total, "Filtr " + f + " zobrazil víc řádků než bez filtru");
                }
                w.filterBox.setSelectedItem(TaskTree.Filter.ALL);
                check(w.tree.visible().size() == total, "Zrušení filtru podle data nevrátilo seznam");

                // kontrola kvality plánu
                java.util.List<ScheduleCheck.Finding> findings = ScheduleCheck.run(project);
                check(findings.size() >= 10, "Kontrola kvality vrátila málo pravidel: " + findings.size());
                for (ScheduleCheck.Finding f : findings) {
                    check(f.count() >= 0 && f.count() <= f.total(), "Nález " + f.rule() + " má nesmyslný počet");
                    check(f.count() == f.tasks().size(), "Nález " + f.rule() + " nesedí se seznamem úkolů");
                    check(f.count() == 0 ? f.level() == ScheduleCheck.Level.OK : f.level() != ScheduleCheck.Level.OK,
                            "Nález " + f.rule() + " má špatnou závažnost");
                }
                check(!ScheduleCheck.summary(findings).isBlank(), "Shrnutí kontroly je prázdné");

                // porovnání verzí: se sebou samým nesmí najít změnu, po posunu termínů ano
                ProjectFile copy = ProjectLoader.load(file, null);
                java.util.List<ScheduleDiff.Row> same = ScheduleDiff.compare(copy, project);
                for (ScheduleDiff.Row r : same) {
                    check(r.kind() == ScheduleDiff.Kind.SAME, "Porovnání téhož souboru hlásí změnu: " + r.name() + " " + r.changesText());
                }
                org.mpxj.Task moved = null;
                for (org.mpxj.Task t : copy.getTasks()) {
                    if (t != null && t.getName() != null && t.getStart() != null && t.getFinish() != null && !t.getSummary()) {
                        moved = t;
                        break;
                    }
                }
                if (moved != null) {
                    moved.setStart(moved.getStart().minusDays(7));
                    moved.setFinish(moved.getFinish().minusDays(7));
                    java.util.List<ScheduleDiff.Row> diff = ScheduleDiff.compare(copy, project);
                    int changed = 0;
                    for (ScheduleDiff.Row r : diff) {
                        if (r.kind() == ScheduleDiff.Kind.CHANGED) {
                            changed++;
                            check(r.startShift() == 7 && r.finishShift() == 7, "Porovnání spočítalo špatný posun: " + r.startShift());
                        }
                    }
                    check(changed == 1, "Porovnání nenašlo právě jednu změnu: " + changed);
                }

                // uložený pohled: uložit, načíst zpět a použít
                SavedView view = new SavedView("test-pohled", "id:60,name:300", TaskTree.Filter.MILESTONES.name(), "", 0, GanttPanel.Zoom.MONTHS.name());
                SavedView back = SavedView.deserialize(view.serialize());
                check(back != null && back.name().equals("test-pohled") && back.filter().equals("MILESTONES")
                        && back.zoom().equals("MONTHS"), "Uložený pohled se nenačetl zpět");
                w.applyView(back);
                check(w.tree.getFilter() == TaskTree.Filter.MILESTONES, "Pohled nenastavil filtr");
                w.filterBox.setSelectedItem(TaskTree.Filter.ALL);
                java.util.List<String[]> defaultColumns = new java.util.ArrayList<>();
                for (String k : Columns.DEFAULT_KEYS) {
                    defaultColumns.add(new String[]{k, ""});
                }
                w.applyColumns(defaultColumns);
                check(w.tree.visible().size() == total, "Návrat k výchozímu pohledu nevrátil seznam");

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
                // záložka Vytížení: vykreslit do PNG
                w.tabs.setSelectedIndex(3);
                w.usage.setPeriod(ResourceUsagePanel.Period.WEEKS);
                w.usage.waitForCompute();
                if (w.usage.table().getRowCount() > 0) {
                    w.usage.table().setRowSelectionInterval(0, 0);
                }
                w.validate();
                BufferedImage usageImg = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D ug = usageImg.createGraphics();
                w.getRootPane().printAll(ug);
                ug.dispose();
                ImageIO.write(usageImg, "png", out.resolveSibling(stripExt(out.getFileName().toString()) + "-usage.png").toFile());
                // Časová osa a Kalendáře: vykreslit a ověřit obsah
                w.timeline.rebuild();
                BufferedImage tl = w.timeline.renderImage(1200, 1);
                check(tl.getWidth() == 1200 && tl.getHeight() > 60, "Časová osa má špatné rozměry");
                ImageIO.write(tl, "png", out.resolveSibling(stripExt(out.getFileName().toString()) + "-timeline.png").toFile());
                check(w.calendars.calendarList().getModel().getSize() >= (project.getCalendars().isEmpty() ? 0 : 1), "Seznam kalendářů je prázdný");
                if (w.calendars.calendarList().getModel().getSize() > 0) {
                    check(w.calendars.current() != null, "Kalendář není vybrán");
                }
                w.tabs.setSelectedIndex(4);
                w.validate();
                BufferedImage calImg = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D cg = calImg.createGraphics();
                w.getRootPane().printAll(cg);
                cg.dispose();
                ImageIO.write(calImg, "png", out.resolveSibling(stripExt(out.getFileName().toString()) + "-calendar.png").toFile());
                w.tabs.setSelectedIndex(0);
                w.validate();
                List<LicenseDialog.Entry> licenses = LicenseDialog.entries();
                check(licenses.size() >= 20, "Málo licenčních položek: " + licenses.size());
                check(licenses.get(0).resource().equals("LICENSE.txt") && LicenseDialog.text("LICENSE.txt").contains("freeware")
                        && LicenseDialog.text("LICENSE.txt").contains("LGPL"), "Chybí licenční podmínky programu");
                for (LicenseDialog.Entry e : licenses) {
                    check(LicenseDialog.text(e.resource()).length() > 50, "Prázdný licenční text: " + e.resource());
                }
                // GPLv2 vyžaduje u šířených binárek odkaz na zdrojové kódy běhového prostředí
                String openjdk = LicenseDialog.text("OPENJDK-SOURCE.txt");
                check(openjdk.contains("Temurin") && openjdk.contains("adoptium/jdk21u")
                        && openjdk.contains("GPLv2") && openjdk.contains("@"),
                        "OPENJDK-SOURCE.txt neuvádí sestavení, zdrojáky nebo nabídku jejich poskytnutí");
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
