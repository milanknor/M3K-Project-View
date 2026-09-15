# M3K Project View

Verze 0.2.0 · Autor: Milan Knor · Vizuální styl: oranžová `#FF790A` na antracitové `#111115`
(návrh v `Codex\2026-09-15\ko\outputs\M3K-Project-View`)

Prohlížeč souborů Microsoft Project pro Windows – **pouze pro čtení**, bez nutnosti
instalovat Microsoft Project nebo Javu (běhové prostředí je součástí balíčku).

## Co umí

- Otevře soubory **MPP / MPT** (Project 98 až Project 2021/365), **XML** (MSPDI),
  **MPX** a **MPD**. Soubory chráněné heslem pro čtení se otevřou po zadání hesla.
- **Ganttův diagram**: tabulka úkolů + časová osa s dvouřádkovou hlavičkou, víkendy
  a svátky podle kalendáře projektu, pruhy s průběhem, souhrnné pruhy, milníky,
  rozdělené úkoly, vazby (FS/SS/FF/SF) se zvýrazněním vazeb vybraného úkolu,
  kritické úkoly červeně, neaktivní šedě, termíny (deadline), značky omezení,
  základní plán (i Základní plán 1–10), celková rezerva, čára dneška a datum
  stavu; podrobný tooltip nad pruhem.
- **Sloupce tabulky**: ~55 vestavěných sloupců v kategoriích (termíny, práce
  a náklady, základní plán, rezervy…) a **vlastní pole** ze souboru (Text1…,
  Číslo1…, Příznak1…, Datum1…, Doba trvání1…, Náklady1…, Kód osnovy1… včetně
  podnikových, s aliasy). Výběr pravým tlačítkem na hlavičku, přetahování pořadí,
  šířky; vše se pamatuje. **Řazení** kliknutím na hlavičku (vzestupně / sestupně /
  podle souboru) v rámci každé úrovně osnovy.
- **Osnova**: sbalování/rozbalování souhrnů (šipka, dvojklik, mezerník, +/−),
  rozbalení do zvolené úrovně, „Rozbalit vše“ / „Sbalit vše“; výchozí stav
  sbalení se přebírá ze souboru.
- **Hledání a filtry**: název, ID, WBS, zdroje, poznámky; rychlé filtry (kritické,
  milníky, nedokončené, rozpracované, dokončené, zpožděné, souhrnné, se zdroji,
  neaktivní) a **rozšířený filtr** (zdroj, období, % dokončení, text ve zvoleném
  sloupci) s uloženými pojmenovanými filtry; nadřazené souhrny zůstávají vidět.
- **Detail úkolu**: obecné údaje, vlastní pole, všechny základní plány, přiřazené
  zdroje, vazby (dvojklik přejde na úkol), poznámky, odkaz na vložený podprojekt
  (otevře se v novém okně).
- **Časová osa**: souhrnné úkoly zvolené úrovně jako bloky a milníky s popisky –
  přehled pro prezentaci, export do PNG.
- **Zdroje**: seznam zdrojů (typ, jednotky, sazby, práce, náklady, přetížení)
  a přiřazení vybraného zdroje.
- **Vytížení**: tabulka zdroj × období (dny / týdny / měsíce) s hodinami práce nebo
  % vytížení podle časově rozložených dat v souboru; přetížení (práce > kapacita
  podle kalendáře zdroje a max. jednotek) červeně; rozpis vybraného zdroje po úkolech.
- **Kalendáře**: pracovní týden, výjimky (svátky), měsíční mřížka s nepracovními dny.
- **Projekt**: vlastnosti souboru a projektu, statistika, kalendáře, podprojekty.
- **Tisk a export**: tisk (stránkování po řádcích, hlavička na každé stránce),
  uložení do **PDF** (A4/A3), CSV podle zobrazených sloupců (UTF‑8, středník –
  rovnou do Excelu), Ganttův diagram a časová osa do PNG.
- **Vzhled**: světlý / tmavý režim, velikost písma (i Ctrl+Shift++/−), čeština
  nebo angličtina (Zobrazit → Jazyk, projeví se po restartu).
- Více souborů ve více oknech (Ctrl+N, nabídka Okno), úvodní obrazovka s nedávnými
  soubory, drag & drop, spuštění s cestou k souboru jako argumentem, kontextová
  nabídka „Otevřít v M3K Project View“ v Průzkumníku. Program se nepřipojuje k síti.

## Ovládání

| Akce | Klávesa |
|---|---|
| Otevřít soubor | Ctrl+O |
| Znovu načíst | F5 |
| Hledat | Ctrl+F (Esc vymaže) |
| Rozbalit / sbalit vše | Ctrl+Shift+E / Ctrl+Shift+C |
| Sbalit/rozbalit vybraný souhrn | mezerník, `+`, `−` |
| Přiblížit / oddálit / celý projekt | Ctrl++ / Ctrl+− / Ctrl+0, Ctrl+kolečko myši |
| Přejít na dnešek / na vybraný úkol | Ctrl+T / Ctrl+G |
| Rozšířený filtr | Ctrl+Shift+F |
| Export CSV / PNG / PDF / tisk | Ctrl+E / Ctrl+Shift+P / Ctrl+Shift+S / Ctrl+P |
| Nové okno / otevřít v novém okně / zavřít okno | Ctrl+N / Ctrl+Shift+O / Ctrl+W |
| Větší / menší písmo | Ctrl+Shift++ / Ctrl+Shift+− |
| Nápověda | F1 |

## Instalace

Spusťte `dist\M3K-Project-View-0.2.0-Setup.exe` (Windows 10/11, 64 bit). Instaluje se
jen pro aktuálního uživatele do `%LOCALAPPDATA%\Programs\M3K Project View` – bez práv
správce a bez výzvy UAC. Volitelně zástupce na ploše a přidružení souborů
`.mpp`, `.mpt` a `.mpx` (zaškrtnuto předem jen tehdy, když v systému žádný program
pro `.mpp` není – Microsoft Project tedy nepřebije). Program se vždy objeví v nabídce
„Otevřít v programu“. Odinstalace je přes Nastavení → Aplikace; na konci se zeptá,
zda smazat i uložené nastavení (nedávné soubory).

Tichá instalace: `M3K-Project-View-0.2.0-Setup.exe /VERYSILENT /TASKS=desktopicon,assoc`

## Spuštění bez instalace

1. Rozbalte `dist\M3K-Project-View-Windows.zip` do libovolné složky.
2. Spusťte `M3K Project View.exe` (složky `app` a `runtime` musí zůstat vedle něj).

## Sestavení ze zdrojáků

Vyžaduje JDK 21 (Eclipse Temurin) a Python s Pillow (jen pro ikonu). Knihovna
MPXJ 16.7.0 a její závislosti jsou ve složce `lib\`.

```powershell
# kompilace + testy (build\viewer.jar)
.\build.ps1

# navíc samostatná aplikace do dist\M3K Project View\ + ZIP
.\build.ps1 -Package

# navíc instalátor dist\M3K-Project-View-<verze>-Setup.exe (vyžaduje Inno Setup 6:
# winget install JRSoftware.InnoSetup; cestu k ISCC.exe lze zadat v MSPV_ISCC)
.\build.ps1 -Installer

# spustit z build (volitelně se souborem)
.\build.ps1 -SkipTests -Run -File "C:\cesta\projekt.mpp"
```

Cesta k JDK: proměnná `MSPV_JDK`, jinak `JAVA_HOME`, jinak výchozí cesta ve skriptu.

Testy: `App --test soubor.mpp …` (načtení, hierarchie, filtry, formátování,
CSV, odmítnutí neplatných souborů) a `App --ui-test soubor.mpp výstup.png [DAYS|WEEKS|MONTHS|…]`
(sestavení okna, hledání, filtr, sbalení, navigace, vykreslení do PNG).

## Struktura

```
src\cz\msproject\viewer\   zdrojové kódy (Java 21, Swing)
lib\                       MPXJ + závislosti (LGPL / Apache 2.0 / MIT / EDL)
lib\licenses\              licence třetích stran (generuje collect_licenses.py)
test\                      testovací soubory MPP8, MPP9, MPP12, MPP14
tools\make_icon.py         generátor ikony
tools\collect_licenses.py  vytáhne licence a NOTICE z jarů, vytvoří THIRD-PARTY.txt a index
installer\setup.iss        instalátor (Inno Setup 6)
LICENSE.txt                licenční podmínky programu (freeware)
build.ps1                  sestavení, testy, balíček, instalátor
```

## Licence

Program je **freeware** (© 2026 Milan Knor, `LICENSE.txt`): zdarma k použití i k dalšímu
šíření v nezměněné podobě; zdrojový kód není součástí distribuce (balíček obsahuje jen
zkompilovanou aplikaci s běhovým prostředím).
Knihovny třetích stran (MPXJ – LGPL 2.1+, Apache POI, Jackcess, Jackson, Commons,
sqlite-jdbc, log4j-api – Apache 2.0, jsoup – MIT, JAXB/Jakarta – EDL 1.0) a běhové
prostředí Java (Temurin OpenJDK – GPLv2 s Classpath Exception) zůstávají pod svými
licencemi. Jejich texty jsou přímo v aplikaci (Nápověda → Licence…), ve složce
`licenses\` vedle programu (`THIRD-PARTY.txt` + texty z jednotlivých knihoven) a
v `runtime\legal\`. MPXJ je přiložena jako samostatný `app\mpxj.jar`, takže ji lze
podle LGPL nahradit (licenční podmínky to výslovně dovolují, jak LGPL vyžaduje).
Instalátor zobrazí licenční podmínky před instalací.

Microsoft Project je ochranná známka Microsoft Corporation; tento program není
produktem Microsoftu.

## Omezení

Aplikace harmonogram nepřepočítává – zobrazuje termíny, procenta, kritičnost
i vytížení tak, jak jsou uložené v souboru (u XML/MPX bez časově rozložených dat
se vytížení odvozuje z rovnoměrného rozložení práce). Vložené podprojekty se
neslučují do jednoho zobrazení – otevřou se odkazem v samostatném okně. Anglický
překlad pokrývá nabídky, sloupce, dialogy a nápovědu; některé stavové hlášky
zůstávají česky. Seskupování úkolů (jako v Projectu)
není k dispozici – nahrazují ho řazení, filtry a sloupce.

Čtení souborů zajišťuje [MPXJ](https://www.mpxj.org) (LGPL 2.1+). Tato aplikace
není produktem společnosti Microsoft.
