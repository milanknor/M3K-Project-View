package cz.msproject.viewer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Jazyk uživatelského rozhraní. Zdrojový kód je psán česky; {@link #t(String)} vrátí
 * anglický překlad ze slovníku, když je zvolena angličtina (volba „lang“ v nastavení,
 * nebo -Dm3k.lang=en). Text bez překladu se vrátí beze změny.
 */
public final class Str {
    private static final boolean ENGLISH;
    private static final Locale LOCALE;
    private static final Map<String, String> EN = new HashMap<>();

    static {
        String lang = System.getProperty("m3k.lang");
        if (lang == null) {
            try {
                lang = Preferences.userNodeForPackage(MainWindow.class).get("lang", "cs");
            } catch (RuntimeException ex) {
                lang = "cs";
            }
        }
        ENGLISH = "en".equalsIgnoreCase(lang);
        LOCALE = ENGLISH ? Locale.forLanguageTag("en-GB") : Locale.forLanguageTag("cs-CZ");
        String[][] d = {
                // nabídky
                {"Soubor", "File"}, {"Zobrazit", "View"}, {"Okno", "Window"}, {"Nápověda", "Help"},
                {"Otevřít…", "Open…"}, {"Otevřít v novém okně…", "Open in New Window…"}, {"Nové okno", "New Window"},
                {"Nedávné soubory", "Recent Files"}, {"Znovu načíst", "Reload"}, {"Zavřít okno", "Close Window"},
                {"Exportovat zobrazené úkoly do CSV…", "Export Visible Tasks to CSV…"}, {"Uložit Ganttův diagram jako PNG…", "Save Gantt Chart as PNG…"},
                {"Uložit jako PDF…", "Save as PDF…"}, {"Tisk…", "Print…"}, {"Konec", "Exit"},
                {"Hledat", "Find"}, {"Rozbalit vše", "Expand All"}, {"Sbalit vše", "Collapse All"},
                {"Rozšířený filtr…", "Advanced Filter…"}, {"Uložené filtry", "Saved Filters"}, {"Sloupce tabulky…", "Table Columns…"},
                {"Zrušit řazení", "Clear Sorting"}, {"Přiblížit", "Zoom In"}, {"Oddálit", "Zoom Out"}, {"Celý projekt", "Whole Project"},
                {"Přejít na dnešek", "Go to Today"}, {"Přejít na vybraný úkol", "Go to Selected Task"},
                {"Zobrazit vazby", "Show Links"}, {"Zobrazit základní plán", "Show Baseline"}, {"Zobrazit popisky zdrojů", "Show Resource Labels"},
                {"Zobrazit celkovou rezervu", "Show Total Slack"}, {"Vzhled", "Appearance"}, {"Světlý režim", "Light Mode"}, {"Tmavý režim", "Dark Mode"},
                {"Menší písmo", "Smaller Font"}, {"Normální písmo", "Normal Font"}, {"Větší písmo", "Larger Font"}, {"Největší písmo", "Largest Font"},
                {"Zvětšit písmo", "Increase Font Size"}, {"Zmenšit písmo", "Decrease Font Size"}, {"Jazyk", "Language"},
                {"Ovládání", "Controls"}, {"Licence…", "Licenses…"}, {"O aplikaci", "About"},
                {"(bez souboru)", "(no file)"}, {"(žádné)", "(none)"},
                {"Vymazat seznam", "Clear List"}, {"Shift+kliknutí otevře v novém okně", "Shift+click opens in a new window"},
                // lišta
                {"Hledat:", "Find:"}, {"Filtr:", "Filter:"}, {"Osnova:", "Outline:"}, {"Měřítko:", "Scale:"}, {"Filtr…", "Filter…"}, {"Filtr ●", "Filter ●"},
                {"Vazby", "Links"}, {"Základní plán", "Baseline"}, {"Popisky", "Labels"}, {"Rezerva", "Slack"}, {"Dnes", "Today"},
                {"Všechny úrovně", "All levels"}, {"Úroveň ", "Level "}, {"Žádný otevřený soubor", "No file open"},
                {"Hledá v názvu, ID, WBS, zdrojích a poznámkách. Esc vymaže.", "Searches name, ID, WBS, resources and notes. Esc clears."},
                {"Rozbalí osnovu do zvolené úrovně", "Expands the outline to the chosen level"},
                {"Oddálit (Ctrl+kolečko myši)", "Zoom out (Ctrl+mouse wheel)"}, {"Přiblížit (Ctrl+kolečko myši)", "Zoom in (Ctrl+mouse wheel)"},
                {"Šedý pruh pod úkolem = uložený základní plán", "Grey bar below a task = saved baseline"},
                {"Tenká čára za úkolem = celková časová rezerva", "Thin line after a task = total slack"},
                {"Posune diagram na dnešní datum (Ctrl+T)", "Scrolls the chart to today (Ctrl+T)"},
                {"Který uložený základní plán se má zobrazit (soubor jich má více)", "Which saved baseline to show (the file has several)"},
                {"Rozšířený filtr: zdroj, období, % dokončení, text ve sloupci; filtry lze uložit", "Advanced filter: resource, period, % complete, text in a column; filters can be saved"},
                {"Kliknutí řadí (vzestupně / sestupně / podle souboru), pravé tlačítko vybírá sloupce, tažením se mění pořadí a šířka.",
                        "Click sorts (ascending / descending / file order), right-click chooses columns, drag to reorder and resize."},
                // záložky
                {"Ganttův diagram", "Gantt Chart"}, {"Časová osa", "Timeline"}, {"Zdroje", "Resources"}, {"Vytížení", "Usage"}, {"Kalendáře", "Calendars"},
                {"Projekt", "Project"}, {"Obecné", "General"}, {"Poznámky", "Notes"}, {"Poznámky ●", "Notes ●"},
                // stav
                {"Otevřete soubor Microsoft Project (Ctrl+O) nebo jej přetáhněte do okna.", "Open a Microsoft Project file (Ctrl+O) or drag it into the window."},
                {"Otevřete soubor Microsoft Project.", "Open a Microsoft Project file."}, {"Žádný projekt", "No project"},
                {"Načítám ", "Loading "}, {"Načteno: ", "Loaded: "}, {"   (pouze pro čtení – soubor se nemění)", "   (read-only – the file is not modified)"},
                {"Soubor byl načten, ale neobsahuje žádné úkoly: ", "The file was loaded but contains no tasks: "},
                {" úkolů  •  ", " tasks  •  "}, {" souhrnných  •  ", " summary  •  "}, {" milníků  •  ", " milestones  •  "}, {" kritických", " critical"},
                {"  •  filtr: ", "  •  filter: "}, {" shod, zobrazeno ", " matches, showing "}, {" řádků", " rows"}, {"  •  zobrazeno ", "  •  showing "},
                {"Řazení zrušeno – pořadí ze souboru.", "Sorting cleared – file order."}, {"Seřazeno podle „", "Sorted by “"},
                {"“ ", "” "}, {"vzestupně", "ascending"}, {"sestupně", "descending"}, {" v rámci každé úrovně osnovy.", " within each outline level."},
                {"Filtru neodpovídá žádný úkol.", "No task matches the filter."}, {"Rozšířený filtr zrušen.", "Advanced filter cleared."},
                {"Rozšířený filtr: ", "Advanced filter: "}, {" shod)", " matches)"},
                {"Exportováno ", "Exported "}, {" řádků do ", " rows to "}, {"Diagram uložen do ", "Chart saved to "}, {"Uloženo do ", "Saved to "},
                {"PDF uloženo do ", "PDF saved to "}, {" str.)", " pages)"}, {"Odesláno na tiskárnu: ", "Sent to printer: "},
                // dialogy
                {"Chyba", "Error"}, {"Export CSV", "CSV Export"}, {"Uložit PNG", "Save PNG"}, {"Tisk", "Print"}, {"Uložit jako PDF", "Save as PDF"},
                {"Nejprve otevřete projekt.", "Open a project first."}, {"Není co tisknout – filtru neodpovídá žádný úkol.", "Nothing to print – no task matches the filter."},
                {"Soubor ", "File "}, {" už existuje. Přepsat?", " already exists. Overwrite?"}, {"Export se nezdařil.", "Export failed."},
                {"Uložení obrázku se nezdařilo.", "Saving the image failed."}, {"Uložení PDF se nezdařilo.", "Saving the PDF failed."}, {"Tisk se nezdařil.", "Printing failed."},
                {"Přizpůsobit na šířku stránky (jinak měřítko 1:1 a více stránek vedle sebe)", "Fit to page width (otherwise 1:1 scale with pages side by side)"},
                {"Stránka:", "Page:"}, {"A4 na šířku", "A4 landscape"}, {"A4 na výšku", "A4 portrait"}, {"A3 na šířku", "A3 landscape"}, {"A3 na výšku", "A3 portrait"},
                {"Diagram je pro tisk příliš velký. Zmenšete měřítko nebo sbalte část osnovy.", "The chart is too large to print. Reduce the scale or collapse part of the outline."},
                {"Diagram je příliš velký – zmenšete měřítko nebo sbalte část osnovy.", "The chart is too large – reduce the scale or collapse part of the outline."},
                {"Otevřít projekt Microsoft Project", "Open Microsoft Project file"}, {"Soubory Microsoft Project (*.mpp, *.mpt, *.mpx, *.xml, *.mpd)", "Microsoft Project files (*.mpp, *.mpt, *.mpx, *.xml, *.mpd)"},
                {"Exportovat zobrazené úkoly do CSV", "Export visible tasks to CSV"}, {"Uložit Ganttův diagram jako PNG", "Save Gantt chart as PNG"},
                {"Uložit časovou osu jako PNG", "Save timeline as PNG"},
                {"Heslo pro čtení", "Read password"}, {"Heslo nebylo přijato. ", "The password was not accepted. "}, {" je chráněn heslem pro čtení.", " is protected by a read password."},
                {"Soubor je chráněn heslem pro čtení.", "The file is protected by a read password."}, {"Otevření souboru bylo zrušeno.", "Opening was cancelled."},
                {"Soubor se nepodařilo načíst", "The file could not be loaded"}, {"; předchozí projekt zůstává otevřený.", "; the previous project stays open."},
                {"Soubor se nepodařilo načíst.", "The file could not be loaded."}, {"Soubor se nepodařilo otevřít.", "The file could not be opened."},
                {"Přetažený soubor se nepodařilo převzít.", "The dropped file could not be accepted."},
                {"Podprojekt", "Subproject"}, {"Cesta k podprojektu je neplatná: ", "The subproject path is invalid: "},
                {"Obrázek by byl příliš velký (", "The image would be too large ("},
                {"Tiskne se to, co je zobrazeno: viditelné řádky, zvolené sloupce a měřítko diagramu.<br>", "What you see is printed: visible rows, chosen columns and chart scale.<br>"},
                {"Hlavička tabulky a časová osa se opakují na každé stránce.</span></html>", "The table header and time axis repeat on every page.</span></html>"},
                {"Strana ", "Page "}, {"řádky ", "rows "}, {", část ", ", part "},
                // sloupce
                {"Obecné", "General"}, {"Termíny", "Dates"}, {"Práce a náklady", "Work and Cost"}, {"Rezervy a kritická cesta", "Slack and Critical Path"},
                {"Ostatní", "Other"}, {"Vlastní pole", "Custom Fields"}, {"  Sloupce tabulky", "  Table columns"}, {"Výchozí sloupce", "Default Columns"},
                {"Přizpůsobit šířky obsahu", "Fit Column Widths"},
                {"Přizpůsobovat šířky po otevření souboru", "Fit Widths When Opening a File"},
                {"Probíhá tento týden", "Running This Week"}, {"Nejbližší tři týdny", "Next Three Weeks"},
                {"Kopírovat vybrané řádky", "Copy Selected Rows"},
                {"Soubor se změnil", "File Changed"}, {"Soubor se od otevření změnil:", "The file changed after it was opened:"},
                {"Načíst znovu? Neuložené nastavení zobrazení (filtr, sbalení) zůstane.", "Reload it? The current view settings (filter, collapsing) stay."},
                {"Uložené pohledy", "Saved Views"}, {"(žádné – uložte současné zobrazení níže)", "(none – save the current view below)"},
                {"Uložit současný pohled…", "Save Current View…"}, {"Smazat pohled", "Delete View"}, {"Pohled smazán: ", "View deleted: "},
                {"Pohled uložen: ", "View saved: "}, {"Pohled: ", "View: "}, {"Pohled ", "View "},
                {"Název pohledu (sloupce, filtr, úroveň osnovy a měřítko):", "View name (columns, filter, outline level and scale):"},
                {"Soubor na disku se změnil – zobrazení zůstává podle dřívějšího načtení (F5 načte znovu).", "The file on disk changed – the view still shows the earlier load (F5 reloads)."},
                {" řádků zkopírováno do schránky.", " rows copied to the clipboard."},
                {"Schránku se nepodařilo otevřít – zkuste to znovu.", "The clipboard could not be opened – try again."},
                {"Vybráno ", "Selected "}, {" úkolů bez souhrnů)", " tasks excluding summaries)"},
                {"  •  práce ", "  •  work "}, {" h", " h"}, {"  •  náklady ", "  •  cost "}, {"Exportovat do Excelu (XLSX)…", "Export to Excel (XLSX)…"}, {"Vlastní pole (tento soubor žádná nemá)", "Custom fields (none in this file)"},
                {"Název úkolu", "Task Name"}, {"Trvání", "Duration"}, {"Zahájení", "Start"}, {"Dokončení", "Finish"}, {"% dokonč.", "% Compl."},
                {"Předchůdci", "Predecessors"}, {"Názvy zdrojů", "Resource Names"}, {"Práce", "Work"}, {"Náklady", "Cost"}, {"Celková rezerva", "Total Slack"},
                {"Jedinečné ID", "Unique ID"}, {"Číslo osnovy", "Outline Number"}, {"Úroveň", "Level"}, {"Typ", "Type"}, {"Následníci", "Successors"},
                {"Iniciály zdrojů", "Resource Initials"}, {"Priorita", "Priority"}, {"Typ úkolu", "Task Type"}, {"Kalendář", "Calendar"},
                {"Skutečné zahájení", "Actual Start"}, {"Skutečné dokončení", "Actual Finish"}, {"Termín", "Deadline"}, {"Omezení", "Constraint"},
                {"Datum omezení", "Constraint Date"}, {"Nejdřívější zahájení", "Early Start"}, {"Nejdřívější dokončení", "Early Finish"},
                {"Nejzazší zahájení", "Late Start"}, {"Nejzazší dokončení", "Late Finish"}, {"Vytvořeno", "Created"}, {"Odchylka zahájení", "Start Variance"},
                {"Odchylka dokončení", "Finish Variance"}, {"Skutečná práce", "Actual Work"}, {"Zbývající práce", "Remaining Work"}, {"% práce", "% Work"},
                {"Skutečné náklady", "Actual Cost"}, {"Zbývající náklady", "Remaining Cost"}, {"Pevné náklady", "Fixed Cost"}, {"Odchylka práce", "Work Variance"},
                {"Odchylka nákladů", "Cost Variance"}, {"Zahájení (zákl. plán)", "Baseline Start"}, {"Dokončení (zákl. plán)", "Baseline Finish"},
                {"Trvání (zákl. plán)", "Baseline Duration"}, {"Práce (zákl. plán)", "Baseline Work"}, {"Náklady (zákl. plán)", "Baseline Cost"},
                {"Volná rezerva", "Free Slack"}, {"Kritický", "Critical"}, {"Milník", "Milestone"}, {"Souhrnný", "Summary"}, {"Aktivní", "Active"},
                {"Odhad", "Estimated"}, {"Řízený úsilím", "Effort Driven"}, {"Odkaz", "Hyperlink"}, {"Kontakt", "Contact"}, {"Podprojekt", "Subproject"},
                {"Základní plán ", "Baseline "}, {" (ZP ", " (BL "}, {"Text", "Text"}, {"Číslo", "Number"}, {"Příznak", "Flag"}, {"Datum", "Date"},
                {"Doba trvání", "Duration"}, {"Kód osnovy", "Outline Code"}, {"Podnik. ", "Enterprise "},
                // filtry
                {"Všechny úkoly", "All Tasks"}, {"Kritické úkoly", "Critical Tasks"}, {"Milníky", "Milestones"}, {"Nedokončené", "Incomplete"},
                {"Rozpracované", "In Progress"}, {"Dokončené", "Completed"}, {"Zpožděné (po termínu)", "Late (past finish)"}, {"Jen souhrnné úkoly", "Summary Tasks Only"},
                {"S přiřazenými zdroji", "With Assigned Resources"}, {"Neaktivní", "Inactive"},
                {"Rozšířený filtr", "Advanced Filter"}, {"Uložený filtr:", "Saved filter:"}, {"Zdroj (část názvu):", "Resource (part of name):"}, {"Období:", "Period:"},
                {"(d. M. yyyy; úkol zasahuje do období)", "(d. M. yyyy; task overlaps the period)"}, {"Dokončeno:", "Complete:"}, {"Text ve sloupci:", "Text in column:"},
                {" obsahuje ", " contains "}, {"název, poznámky, WBS", "name, notes, WBS"}, {"Jen běžné úkoly a milníky (bez souhrnných)", "Only ordinary tasks and milestones (no summaries)"},
                {"Vymazat", "Clear"}, {"Zrušit", "Cancel"}, {"Použít", "Apply"}, {"Uložit jako…", "Save as…"}, {"Smazat", "Delete"}, {"Název filtru:", "Filter name:"},
                {"(nový filtr)", "(new filter)"}, {"Konec období je před začátkem.", "The period end is before its start."},
                {"Procenta musí být 0–100.", "Percent must be 0–100."}, {"Procenta musí být celé číslo 0–100.", "Percent must be a whole number 0–100."},
                {"Nerozumím datu „", "Cannot parse date “"}, {"“ – zadejte např. 15. 9. 2026.", "” – enter e.g. 15. 9. 2026."},
                {"(žádné – uložte filtr v dialogu Rozšířený filtr)", "(none – save a filter in the Advanced Filter dialog)"}, {"Zrušit rozšířený filtr", "Clear Advanced Filter"},
                {"zdroj „", "resource “"}, {"období ", "period "}, {"dokončeno ", "complete "}, {"text „", "text “"}, {"bez souhrnů", "no summaries"},
                // měřítko
                {"Dny", "Days"}, {"Týdny", "Weeks"}, {"Měsíce", "Months"}, {"Čtvrtletí", "Quarters"}, {"Roky", "Years"}, {"Přizpůsobit oknu", "Fit to Window"},
                {". čtvrtletí", "Q"},
                // Gantt tooltip
                {"Trvání: ", "Duration: "}, {" · Dokončeno: ", " · Complete: "}, {" · Práce: ", " · Work: "}, {"Zdroje: ", "Resources: "}, {"Předchůdci: ", "Predecessors: "},
                {"Celková rezerva: ", "Total slack: "}, {"Termín: ", "Deadline: "}, {"Omezení: ", "Constraint: "}, {"Rozdělený úkol: ", "Split task: "}, {" částí", " parts"},
                {"Kritický úkol", "Critical task"}, {"Neaktivní úkol", "Inactive task"}, {"Souhrnný úkol", "Summary task"}, {"Úkol", "Task"}, {"Vložený podprojekt", "Inserted subproject"},
                // detail
                {"Zdroj", "Resource"}, {"Jednotky", "Units"}, {"Směr", "Direction"}, {"ID", "ID"}, {"Typ vazby", "Link Type"}, {"Prodleva", "Lag"},
                {"Vyberte úkol v tabulce nebo v diagramu. Souhrnné úkoly sbalíte a rozbalíte kliknutím na šipku, ", "Select a task in the table or the chart. Summary tasks collapse and expand with the arrow, "},
                {"dvojklikem nebo mezerníkem.</body></html>", "a double-click or the space bar.</body></html>"},
                {"WBS", "WBS"}, {"Osnova", "Outline"}, {"Dokončeno", "Complete"}, {"Práce dokončena", "Work Complete"}, {"Termín (deadline)", "Deadline"},
                {"Předchůdce", "Predecessor"}, {"Následník", "Successor"}, {"Podprojekt:", "Subproject:"}, {"otevřít v novém okně", "open in a new window"}, {"(jen pro čtení)", "(read-only)"},
                {"Práce / náklady (ZP ", "Work / cost (BL "},
                // zdroje
                {"Název zdroje", "Resource Name"}, {"Iniciály", "Initials"}, {"Skupina", "Group"}, {"Max. jednotky", "Max. Units"}, {"Standardní sazba", "Standard Rate"},
                {"Přesčasová sazba", "Overtime Rate"}, {"Náklady na použití", "Cost Per Use"}, {"Přetížen", "Overallocated"}, {"E-mail", "E-mail"}, {"ID úkolu", "Task ID"},
                {"Přiřazení vybraného zdroje", "Assignments of the selected resource"}, {"Přiřazení zdroje: ", "Assignments of resource: "},
                {"Projekt neobsahuje žádné zdroje.", "The project contains no resources."}, {" zdrojů", " resources"}, {" přetížených", " overallocated"},
                {"   •   Dvojklik na přiřazení přejde na úkol v Ganttově diagramu.", "   •   Double-click an assignment to go to the task in the Gantt chart."},
                {"Pracovní", "Work"}, {"Materiálový", "Material"}, {"Nákladový", "Cost"},
                // vytížení
                {"Hodiny práce", "Work hours"}, {"% vytížení", "% allocation"}, {"Jen přetížené zdroje", "Overallocated resources only"}, {"Celkem h", "Total h"}, {"Detail", "Detail"},
                {"Práce vybraného zdroje po úkolech", "Work of the selected resource by task"}, {"Práce zdroje „", "Work of resource “"},
                {"“ po úkolech (dvojklik přejde na úkol)", "” by task (double-click goes to the task)"}, {" zdrojů · ", " resources · "}, {" období · ", " periods · "},
                {"bez přetížení", "no overallocation"}, {" přetížených zdrojů, ", " overallocated resources, "}, {" období", " periods"},
                // kalendáře
                {"Den", "Day"}, {"Pracovní doba", "Working time"}, {"Hodin", "Hours"}, {"Název", "Name"}, {"Od", "From"}, {"Do", "To"},
                {"Pracovní týden", "Working week"}, {"Výjimky (svátky, mimořádné dny)", "Exceptions (holidays, special days)"},
                {"Měsíc – šedě nepracovní dny, oranžově výjimky, modře dnešek", "Month – grey non-working days, orange exceptions, blue today"},
                {"   (výchozí)", "   (default)"}, {"Projekt neobsahuje žádný kalendář.", "The project contains no calendar."},
                {"   —   odvozený z „", "   —   derived from “"}, {" h/den", " h/day"}, {"pracovní", "working"}, {"nepracovní", "non-working"},
                {"pracovní (výchozí)", "working (default)"}, {"nepracovní (výchozí)", "non-working (default)"}, {"Týden celkem", "Week total"},
                {"pracovní den", "working day"}, {"nepracovní den", "non-working day"}, {"Výjimka: ", "Exception: "},
                // časová osa
                {"Souhrnné úkoly:", "Summary tasks:"}, {"Úroveň 1", "Level 1"}, {"Úrovně 1–2", "Levels 1–2"}, {"Úrovně 1–3", "Levels 1–3"}, {"Všechny souhrny", "All summaries"},
                {"Jen kritické", "Critical only"}, {"Uložit jako PNG…", "Save as PNG…"},
                {"Bloky = souhrnné úkoly zvolené úrovně (dvojklik přejde na úkol v Ganttově diagramu), kosočtverce = milníky, červeně kritické.",
                        "Blocks = summary tasks of the chosen level (double-click goes to the task in the Gantt chart), diamonds = milestones, red = critical."},
                {"Žádné souhrnné úkoly ani milníky k zobrazení.", "No summary tasks or milestones to show."}, {" bloků · ", " blocks · "}, {" milníků · ", " milestones · "},
                {"Nic k zobrazení – zvolte jinou úroveň nebo zapněte milníky.", "Nothing to show – choose another level or enable milestones."},
                // úvodní obrazovka
                {"Prohlížeč souborů Microsoft Project – MPP, MPT, XML, MPX, MPD. Pouze pro čtení.", "Microsoft Project file viewer – MPP, MPT, XML, MPX, MPD. Read-only."},
                {"Otevřít soubor…   (Ctrl+O)", "Open file…   (Ctrl+O)"}, {"nebo přetáhněte soubor kamkoli do tohoto okna", "or drag a file anywhere into this window"},
                {"(zatím žádné)", "(none yet)"}, {" – soubor nenalezen", " – file not found"},
                // projekt
                {"Není otevřen žádný projekt.", "No project is open."}, {"Cesta", "Path"}, {"Velikost", "Size"}, {"Formát", "Format"}, {"Aplikace", "Application"},
                {" · verze ", " · version "}, {"Naposledy uloženo", "Last saved"}, {"Titul", "Title"}, {"Předmět", "Subject"}, {"Autor", "Author"}, {"Manažer", "Manager"},
                {"Společnost", "Company"}, {"Kategorie", "Category"}, {"Klíčová slova", "Keywords"}, {"Komentář", "Comments"}, {"Harmonogram", "Schedule"},
                {"Zahájení projektu", "Project start"}, {"Dokončení projektu", "Project finish"}, {"Datum stavu", "Status date"}, {"Aktuální datum", "Current date"},
                {"Plánovat od", "Schedule from"}, {"data zahájení", "start date"}, {"data dokončení", "finish date"}, {"Základní plán uložen", "Baseline saved"},
                {"Výchozí kalendář", "Default calendar"}, {"Hodin denně / týdně", "Hours per day / week"}, {"Dnů v měsíci", "Days per month"}, {"Měna", "Currency"},
                {"Statistika", "Statistics"}, {"Úkolů celkem", "Tasks total"}, {"z toho souhrnných", "of which summary"}, {"z toho milníků", "of which milestones"},
                {"Kritických", "Critical"}, {"Dokončených", "Completed"}, {"Neaktivních", "Inactive"}, {"Zdrojů", "Resources"}, {"Přiřazení", "Assignments"},
                {"Vložené podprojekty (otevřou se samostatně přes odkaz v detailu úkolu)", "Inserted subprojects (open separately via the link in task details)"},
                {" – Project 98", " – Project 98"}, {" – Project 2010 a novější", " – Project 2010 and later"},
                // hodnoty
                {"ano", "yes"}, {"ne", "no"}, {"Co nejdříve", "As Soon As Possible"}, {"Co nejpozději", "As Late As Possible"}, {"Musí začít", "Must Start On"},
                {"Musí skončit", "Must Finish On"}, {"Zahájit nejdříve", "Start No Earlier Than"}, {"Zahájit nejpozději", "Start No Later Than"},
                {"Dokončit nejdříve", "Finish No Earlier Than"}, {"Dokončit nejpozději", "Finish No Later Than"},
                {"Nevyrovnávat", "Do Not Level"}, {"Nejvyšší", "Highest"}, {"Velmi vysoká", "Very High"}, {"Vyšší", "Higher"}, {"Vysoká", "High"}, {"Střední", "Medium"},
                {"Nízká", "Low"}, {"Nižší", "Lower"}, {"Velmi nízká", "Very Low"}, {"Nejnižší", "Lowest"},
                {"Pevné jednotky", "Fixed Units"}, {"Pevná doba trvání", "Fixed Duration"}, {"Pevná práce", "Fixed Work"}, {"Pevná doba trvání a jednotky", "Fixed Duration and Units"},
                {"konec – začátek", "finish – start"}, {"začátek – začátek", "start – start"}, {"konec – konec", "finish – finish"}, {"začátek – konec", "start – finish"},
                {" (uplynulé)", " (elapsed)"},
                // licence a o aplikaci
                {"Licence", "Licenses"}, {" je freeware – zdarma k použití i k dalšímu šíření v nezměněné podobě.<br>", " is freeware – free to use and to redistribute unmodified.<br>"},
                {"Knihovny třetích stran a běhové prostředí Java mají vlastní licence; ", "Third-party libraries and the Java runtime have their own licenses; "},
                {"stejné texty jsou i ve složce <b>licenses</b> vedle programu.</html>", "the same texts are in the <b>licenses</b> folder next to the program.</html>"},
                {"Licenční texty nejsou v tomto sestavení k dispozici (chybí zdroje licenses/).", "License texts are not available in this build (licenses/ resources missing)."},
                {"Prohlížeč souborů Microsoft Project (pouze pro čtení).<br>", "Microsoft Project file viewer (read-only).<br>"},
                {"Autor: ", "Author: "}, {". Freeware – zdarma k použití i šíření v nezměněné podobě (Nápověda → Licence…).<br><br>", ". Freeware – free to use and redistribute unmodified (Help → Licenses…).<br><br>"},
                {"Čtení souborů zajišťuje knihovna <b>MPXJ</b> (https://www.mpxj.org, licence LGPL 2.1+). ", "Files are read by the <b>MPXJ</b> library (https://www.mpxj.org, LGPL 2.1+). "},
                {"Licence všech knihoven třetích stran: Nápověda → Licence… (a složka <b>licenses</b> vedle programu).<br>", "Licenses of all third-party libraries: Help → Licenses… (and the <b>licenses</b> folder next to the program).<br>"},
                {"Běhové prostředí: Java ", "Runtime: Java "},
                {"Tato aplikace není produktem společnosti Microsoft. Microsoft Project je ochranná známka Microsoft Corporation.", "This application is not a Microsoft product. Microsoft Project is a trademark of Microsoft Corporation."},
                {"Aplikace soubory pouze čte a nikam je neodesílá.", "The application only reads files and sends nothing anywhere."},
                // nápověda
                {"<b>Otevření souboru</b><br>Ctrl+O, tlačítko Otevřít… nebo přetažení souboru do okna. ", "<b>Opening a file</b><br>Ctrl+O, the Open… button or dragging a file into the window. "},
                {"Podporované formáty: MPP/MPT (Project 98 – 2021/365), XML (MSPDI), MPX, MPD.<br><br>", "Supported formats: MPP/MPT (Project 98 – 2021/365), XML (MSPDI), MPX, MPD.<br><br>"},
                {"<b>Osnova</b><br>Kliknutí na šipku u souhrnného úkolu, dvojklik nebo mezerník sbalí/rozbalí podúkoly. ", "<b>Outline</b><br>Clicking the arrow of a summary task, double-click or the space bar collapses/expands subtasks. "},
                {"Klávesy + a − rozbalí/sbalí vybraný souhrn. Ctrl+Shift+E rozbalí vše, Ctrl+Shift+C sbalí vše. ", "Keys + and − expand/collapse the selected summary. Ctrl+Shift+E expands all, Ctrl+Shift+C collapses all. "},
                {"Pole Osnova rozbalí hierarchii do zvolené úrovně.<br><br>", "The Outline box expands the hierarchy to the chosen level.<br><br>"},
                {"<b>Hledání a filtry</b><br>Ctrl+F přejde do pole Hledat (název, ID, WBS, zdroje, poznámky). ", "<b>Search and filters</b><br>Ctrl+F focuses the Find box (name, ID, WBS, resources, notes). "},
                {"Filtr omezí seznam např. na kritické, zpožděné nebo nedokončené úkoly; nadřazené souhrny zůstávají vidět.<br><br>", "The filter limits the list e.g. to critical, late or incomplete tasks; parent summaries stay visible.<br><br>"},
                {"<b>Diagram</b><br>Měřítko: Dny/Týdny/Měsíce/Čtvrtletí/Roky/Přizpůsobit, Ctrl+kolečko myši přibližuje kolem kurzoru, ", "<b>Chart</b><br>Scale: Days/Weeks/Months/Quarters/Years/Fit, Ctrl+mouse wheel zooms around the cursor, "},
                {"Ctrl+0 zobrazí celý projekt, Ctrl+T posune na dnešek, Ctrl+G na vybraný úkol. ", "Ctrl+0 shows the whole project, Ctrl+T scrolls to today, Ctrl+G to the selected task. "},
                {"Modrá = úkol, červená = kritický úkol, černý pruh = souhrn, kosočtverec = milník, zelená šipka = termín, ", "Blue = task, red = critical task, black bar = summary, diamond = milestone, green arrow = deadline, "},
                {"šedý pruh = základní plán, červená svislá čára = dnešek, modrá = datum stavu.<br><br>", "grey bar = baseline, red vertical line = today, blue = status date.<br><br>"},
                {"<b>Export</b><br>Ctrl+E uloží zobrazené úkoly do CSV (otevře se v Excelu), Ctrl+Shift+P uloží diagram jako PNG.<br><br>", "<b>Export</b><br>Ctrl+E saves visible tasks to CSV (opens in Excel), Ctrl+Shift+P saves the chart as PNG.<br><br>"},
                {"Čeština", "Czech"}, {"English", "English"},
                // chyby
                {"Technický detail: ", "Technical detail: "}, {"Podrobnosti: ", "Details: "},
                {"Došla paměť. Zavřete jiná okna nebo otevřete menší soubor; pro velmi velké projekty pomůže sbalit osnovu nebo zmenšit měřítko.",
                        "Out of memory. Close other windows or open a smaller file; for very large projects collapse the outline or reduce the scale."},
                {"V programu nastala neočekávaná chyba. Program běží dál, ale zkontrolujte poslední akci.", "An unexpected error occurred. The program keeps running, but check your last action."},
                {"Soubor je příliš velký pro dostupnou paměť. Zavřete ostatní okna programu a zkuste to znovu.", "The file is too large for the available memory. Close other program windows and try again."},
                {"Odkaz na podprojekt vede na síťovou cestu:", "The subproject link points to a network path:"},
                {"Otevřít ji? Přistupujte jen k serverům, kterým důvěřujete.", "Open it? Only access servers you trust."},
                {"Soubor neexistuje nebo není přístupný:\n", "The file does not exist or is not accessible:\n"},
                {"Nepodporovaná přípona. Podporovány jsou soubory MPP, MPT, MPX, XML (MSPDI) a MPD.", "Unsupported extension. Supported files: MPP, MPT, MPX, XML (MSPDI) and MPD."},
                {"Počítám vytížení…", "Computing usage…"}, {"Vytížení se nepodařilo spočítat.", "Usage could not be computed."},
                {"Zmenšete měřítko nebo sbalte část osnovy.", "Reduce the scale or collapse part of the outline."},
                {"Změna jazyka se projeví po restartu programu. Restartovat nyní?", "The language change takes effect after restarting the program. Restart now?"},
                {"Restart se nezdařil – ukončete a spusťte program ručně.", "Restart failed – please close and start the program manually."},
                // nástroje: kontrola kvality, porovnání verzí, export do Excelu
                {"Nástroje", "Tools"},
                {"Kontrola kvality plánu…", "Schedule Quality Check…"},
                {"Kontrola kvality plánu", "Schedule Quality Check"},
                {"Porovnat s jinou verzí…", "Compare with Another Version…"},
                {"Porovnání verzí", "Version Comparison"},
                {"Projekt neobsahuje žádné úkoly ke kontrole.", "The project contains no tasks to check."},
                {"v pořádku", "OK"},
                {"k zamyšlení", "worth a look"},
                {"varování", "warning"},
                {"Úkoly bez předchůdce", "Tasks without a predecessor"},
                {"Nemají na co navazovat – při posunu začátku se samy neposunou.", "They follow nothing, so they do not move when the start shifts."},
                {"Úkoly bez následníka", "Tasks without a successor"},
                {"Nic z nich nevychází, takže jejich zpoždění se v plánu neprojeví.", "Nothing follows them, so their delay never shows in the plan."},
                {"Tvrdá omezení", "Hard constraints"},
                {"„Musí začít/skončit“ a „nejpozději“ přebíjejí vazby a zakrývají skutečnou kritickou cestu.", "“Must start/finish” and “no later than” override links and hide the real critical path."},
                {"Záporná celková rezerva", "Negative total slack"},
                {"Plán je v konfliktu – termín nelze splnit bez změny vazeb nebo omezení.", "The plan is in conflict – the date cannot be met without changing links or constraints."},
                {"Rezerva nad 44 dní", "Slack over 44 days"},
                {"Vysoká rezerva bývá známkou chybějící nebo volné vazby.", "High slack usually points to a missing or loose link."},
                {"Trvání nad 44 dní", "Duration over 44 days"},
                {"Dlouhé úkoly se špatně řídí; obvykle jde o nerozpadlý balík práce.", "Long tasks are hard to manage; usually a work package that was never broken down."},
                {"Vazby s prodlevou", "Links with lag"},
                {"Prodleva ve vazbě skrývá činnost, která v plánu není vidět.", "A lag hides work that is not visible in the plan."},
                {"Vazby s předstihem", "Links with lead"},
                {"Záporná prodleva (překryv) podle DCMA v plánu být nemá.", "Negative lag (overlap) should not appear in a plan under DCMA."},
                {"Jiné vazby než konec–začátek", "Links other than finish-start"},
                {"SS/FF/SF vazby jsou v pořádku, pokud je jich málo a jsou zdůvodněné.", "SS/FF/SF links are fine when there are few of them and they are justified."},
                {"Zpožděné proti datu stavu", "Late against the status date"},
                {"Dokončení je před datem stavu, ale úkol není hotový.", "The finish is before the status date but the task is not complete."},
                {"Skutečné termíny v budoucnosti", "Actual dates in the future"},
                {"Skutečné zahájení nebo dokončení je po datu stavu – chyba ve výkazu.", "Actual start or finish is after the status date – a reporting error."},
                {"Rozpracované bez skutečného zahájení", "In progress without an actual start"},
                {"Procento dokončení je vyplněné, ale skutečné zahájení chybí.", "Percent complete is filled in but the actual start is missing."},
                {"Úkoly bez zdroje", "Tasks without a resource"},
                {"Bez přiřazení nejde plánovat kapacity ani náklady.", "Without assignments you cannot plan capacity or cost."},
                {"Milníky bez vazeb", "Milestones without links"},
                {"Milník bez vazby nemá v plánu žádnou oporu.", "A milestone with no link has nothing holding it in place."},
                {"Žádná z kontrol nenašla problém.", "No check found a problem."},
                {"Varování: ", "Warnings: "},
                {", k zamyšlení: ", ", worth a look: "},
                {" (kontrol celkem: ", " (checks in total: "},
                {"Kontrola", "Check"},
                {"Nález", "Found"},
                {"Podíl", "Share"},
                {"Podíl %", "Share %"},
                {"Závažnost", "Severity"},
                {"nic", "none"},
                {"Vybrat úkoly v tabulce", "Select tasks in the table"},
                {"Vybrat úkol v tabulce", "Select task in the table"},
                {"Kopírovat přehled", "Copy summary"},
                {"Uložit CSV…", "Save CSV…"},
                {"Zavřít", "Close"},
                {"Dotčené úkoly (", "Affected tasks ("},
                {"Uložit kontrolu jako CSV", "Save the check as CSV"},
                {"Úkoly", "Tasks"},
                {"Kontrola uložena do ", "Check saved to "},
                {"Uložení se nezdařilo.", "Saving failed."},
                {"přidán", "added"},
                {"odebrán", "removed"},
                {"změněn", "changed"},
                {"beze změny", "unchanged"},
                {"nový úkol", "new task"},
                {"v nové verzi chybí", "missing in the new version"},
                {"začátek ", "start "},
                {"konec ", "finish "},
                {" dní", " days"},
                {"trvání ", "duration "},
                {"přejmenován z „", "renamed from “"},
                {"nově kritický", "newly critical"},
                {"už není kritický", "no longer critical"},
                {"práce ", "work "},
                {"náklady ", "cost "},
                {"Změněno: ", "Changed: "},
                {", přidáno: ", ", added: "},
                {", odebráno: ", ", removed: "},
                {"  •  největší posun dokončení: ", "  •  largest finish shift: "},
                {"Zobrazit i úkoly beze změny", "Show unchanged tasks too"},
                {"Starší verze: ", "Older version: "},
                {"Otevřený soubor: ", "Open file: "},
                {"Stav", "Status"},
                {"Zahájení dřív", "Start before"},
                {"Zahájení teď", "Start now"},
                {"Posun", "Shift"},
                {"Dokončení dřív", "Finish before"},
                {"Dokončení teď", "Finish now"},
                {"Změny", "Changes"},
                {"Uložit porovnání jako CSV", "Save the comparison as CSV"},
                {"Porovnání uloženo do ", "Comparison saved to "},
                {"Vyberte starší verzi harmonogramu", "Choose an older version of the schedule"},
                {"Vybrali jste tentýž soubor, který je otevřený.", "You picked the same file that is already open."},
                {"Porovnávám s ", "Comparing with "},
                {"Porovnání s ", "Comparison with "},
                {"Starší verzi se nepodařilo načíst.", "The older version could not be loaded."},
                {" z ", " of "},
                {" úkolů – ostatní skrývá rozšířený filtr.", " tasks – the rest is hidden by the advanced filter."},
                {"Export do Excelu", "Excel Export"},
                {"Exportovat do Excelu", "Export to Excel"},
                {"Sešit uložen do ", "Workbook saved to "},
                {" řádků úkolů)", " task rows)"},
                {"Export do Excelu se nezdařil.", "Export to Excel failed."},
                {"Max. jednotky %", "Max units %"},
                {"Práce (h)", "Work (h)"},
                {"Přetížený", "Overallocated"},
                {"Jednotky %", "Units %"},
                {"Vytížení (týdny)", "Usage (weeks)"},
                {"Údaj", "Item"},
                {"Hodnota", "Value"},
                {"Název projektu", "Project name"},
                {"Vedoucí", "Manager"},
                {"Zobrazeno řádků", "Rows shown"},
                {"Exportováno", "Exported"},
        };
        for (String[] e : d) {
            EN.put(e[0], e[1]);
        }
    }

    private Str() {
    }

    public static boolean isEnglish() {
        return ENGLISH;
    }

    public static Locale locale() {
        return LOCALE;
    }

    public static String lang() {
        return ENGLISH ? "en" : "cs";
    }

    /** Překlad textu; bez překladu se vrací původní český text. */
    public static String t(String czech) {
        if (!ENGLISH || czech == null) {
            return czech;
        }
        String s = EN.get(czech);
        return s != null ? s : czech;
    }

    static int dictionarySize() {
        return EN.size();
    }
}
