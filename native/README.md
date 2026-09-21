# M3K Project View 1.0.1 (nativní C++) — prohlížeč MS Project (.mpp / .xml)

Autor: **M3K Soft** · Prohlížeč harmonogramů MS Project pro techniky, kteří kontrolují průběh zakázky.
Čistě Win32/C++, jeden soubor `mppview.exe` (~0,8 MB), žádný runtime, žádná Java, funguje i bez instalace.
Nativní verze M3K Project View — stejné funkce jako Java verze (MPXJ), bez Javy. Ikona převzata z Java verze.

## Co umí
- Otevře **.mpp z Project 2007 / 2010 / 2013 / 2016 / 2019 / 2021 / 365** (MPP12, MPP14) a **XML (MSPDI)** exportované z Projectu. Project 98–2003 (MPP8/9), MPX a MPD srozumitelně odmítne.
- **Tabulka úkolů** s volitelnými sloupci (pravé tlačítko na hlavičku): základní, termíny, skutečné/časné/pozdní termíny, směrné plány 0–10, práce, náklady,
  rezervy, omezení, priorita, typ, kalendář, **vlastní pole** (Text/Číslo/Datum/Příznak/Doba… s aliasy z projektu). Přetažení sloupců, **řazení klikem na hlavičku**
  (v rámci hierarchie), součty vybraných řádků ve stavovém řádku, Ctrl+C kopíruje řádky do Excelu.
- **Ganttův diagram**: průběh, souhrny, milníky, zvolený směrný plán (0–10), konečné termíny, značky omezení, vazby, **kritická cesta**, svátky a nepracovní dny
  podle kalendáře, čára dnes / stav k, průběhová čára, legenda, **obarvení pruhů podle pole** (zdroj, úroveň, stav, priorita, vlastní pole…). Tooltip na pruhu.
- **Záložky** (Ctrl+1…6): Ganttův diagram · Časová osa (milníky/souhrny, export PNG) · Zdroje (přehled + přiřazení) · Vytížení (práce po dnech/týdnech/měsících,
  přetížení) · Kalendář (pracovní týden, výjimky, měsíční náhled) · Projekt (vlastnosti souboru a projektu).
- **Filtry**: hledání, rychlé filtry (zpožděné, probíhající, kritické, tento týden, 14 dní, milníky, s poznámkou…), podle zdroje, **rozšířený filtr** (Ctrl+Shift+F:
  zdroj, období, rozsah %, text v libovolném sloupci) s ukládáním. **Uložené pohledy** (sloupce + filtr + rozbalení + měřítko + obarvení). Rozbalit do úrovně.
- **Nástroje**: kontrola kvality plánu (14 kontrol ve stylu DCMA), **porovnání se starší verzí** souboru (nové/odstraněné/posunuté úkoly).
- **Export**: CSV, **XLSX** (vlastní zapisovač), PNG, **PDF** (A4/A3 na šířku, stránkování), tisk.
- **Detail úkolu** (F4, výšku lze měnit tažením „•••“): záložky **Obecné** (termíny · průběh a směrný plán · práce a náklady · plánování, vlastní pole, další směrné plány),
  **Zdroje** (jednotky, práce, % práce, náklady), **Vazby** (směr, typ FS/SS/FF/SF, prodleva; dvojklik = skok), **Poznámky**. Navigace po vazbách, **sledování změny souboru** na disku + F5, tmavý režim, velikost písma (Ctrl+Shift+±), „Otevřít v novém okně“,
  přidružení .mpp + položka „Otevřít v MppView“ v Průzkumníku, nastavení v `%APPDATA%\MppView\mppview.ini`.
- Dávkový režim bez dialogů: `mppview.exe soubor.mpp --csv x.csv --xlsx x.xlsx --png x.png --pdf x.pdf` (exit 0 = OK, 2 = soubor nelze otevřít, 3–6 = export selhal).
- `mppdump.exe [-q] [-n] [-r] [-c] [-dll] [-rt] soubor …` — vývojářská konzole (výpis, samokontrola, `-dll` čtení přes knihovnu, `-rt` kontrola serializace); není součástí instalace.

## Sestavení
Vyžaduje MSVC Build Tools 2022.

```
build.cmd            (out\mppview.exe + out\mppread.dll + out\mppdump.exe)
build.cmd setup      (navíc out\mppread-src.zip a instalátor out\M3K-Project-View-1.0.1-Setup.exe přes Inno Setup 6)
```

## Soubory a licence
Program je **freeware** (© 2026 M3K Soft) pro soukromé i komerční použití, bez zveřejnění zdrojového kódu aplikace — viz `LICENSE.txt`.
Výjimkou je knihovna **mppread.dll** (čtení .mpp/.xml): je to port části knihovny **MPXJ** (Jon Iles, https://github.com/joniles/mpxj),
proto je pod **GNU LGPL 2.1 nebo pozdější** (`COPYING.LIB`); původní copyright Packwood Software 2002–2022, seznam převzatých souborů a záznam změn je v hlavičce `mpp.h`. Aby to šlo s uzavřenou aplikací dohromady:
- čtečka je samostatná DLL, kterou aplikace načítá dynamicky z vlastní složky → uživatel ji může nahradit upravenou verzí,
- rozhraní je čisté C (`mppread.h`), model se přenáší jako bajty (`serial.h`), aplikace data z DLL kontroluje,
- vyhodnocení kalendářů (i opakovaných svátků) dělá knihovna, aplikace dostává hotový seznam dnů,
- zdrojový kód knihovny se dodává s programem (`mppread-src.zip`), `LICENSE.txt` výslovně povoluje úpravy knihovny a zpětnou analýzu
  v rozsahu potřebném k jejich ladění (požadavek § 6 LGPL).

| Soubor | Kam patří | Licence |
|---|---|---|
| `cfb.h`, `mpp.h`, `mspdi.h`, `mppread.cpp`, `mppread.h`, `mppread.rc`, `build-mppread.cmd`, `mppdump.cpp` | mppread.dll (+ test. konzole) | LGPL 2.1+ (zveřejněno v `mppread-src.zip`) |
| `model.h`, `serial.h`, `mppload.h` | sdílené aplikací i knihovnou | MIT (zveřejněno v `mppread-src.zip`) |
| `ui.h`, `columns.h`, `filters.h`, `gantt.h`, `usage.h`, `exports.h`, `tools.h`, `panels.h`, `detail.h`, `mppview.cpp`, `mppview.rc` | mppview.exe | uzavřené, freeware |

## Ověření (stav k 21. 9. 2026)
- Čtečka: celá testovací sada MPXJ (756 souborů) — **489/489** podporovaných (MPP12, MPP14, XML) se načte bez pádu; 267 odmítnutých jsou jen MPP8/9.
- Křížová kontrola MPP14 × XML stejného projektu (74 dvojic): kalendář den po dni shodný ve **74/74**, ostatní výstup shodný v 56/74;
  zbylé rozdíly jsou příznak „ručně plánovaný“ u souborů z Project 2010/2013, který se čte stejně jako v MPXJ (rozdíl je v testovacích datech).
- Dávkový export všech 489 souborů do CSV/XLSX/PNG/PDF: bez chyb (kromě 5 prázdných projektů, kde PNG/PDF nemá co kreslit → exit 5);
  XLSX validní ZIP+XML a otevřen v Excelu, PDF strukturálně ověřeno (xref, dekomprese obrázků).
- GUI ověřeno programově (`out\uitest.ps1`, parametry `-Tab 0..5`, `-Cmd`): všech 6 záložek, detail úkolu vč. vlastních polí, přehled, přetížení zdroje.
- **Neověřeno ručně myší**: přetahování sloupců, dialog rozšířeného filtru, uložené pohledy, porovnání verzí, tisk, tmavý režim, změna písma, přidružení souborů.

- Lišta: Hledat · Filtr + „Filtr…“ (rozšířený) · zdroj · Osnova (úroveň) · Měřítko · −/+ · Vazby · Základní plán · Dnes.

## Známá omezení
- Vytížení rozkládá práci rovnoměrně do pracovních dnů (časově rozložená data z .mpp se nečtou).
- Podprojekty se neslučují; soubory chráněné heslem nelze otevřít; více oken = více instancí programu.
- Rozhraní jen česky. Exe není podepsán — SmartScreen může při prvním spuštění varovat.
