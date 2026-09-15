# M3K Project View

Verze 0.1.0 · Autor: Milan Knor · Vizuální styl: oranžová `#FF790A` na antracitové `#111115`
(návrh v `Codex\2026-09-15\ko\outputs\M3K-Project-View`)

Prohlížeč souborů Microsoft Project pro Windows – **pouze pro čtení**, bez nutnosti
instalovat Microsoft Project nebo Javu (běhové prostředí je součástí balíčku).

## Co umí

- Otevře soubory **MPP / MPT** (Project 98 až Project 2021/365), **XML** (MSPDI),
  **MPX** a **MPD**. Soubory chráněné heslem pro čtení se otevřou po zadání hesla.
- **Ganttův diagram**: tabulka úkolů (ID, název, trvání, zahájení, dokončení,
  % dokončení, předchůdci, zdroje, práce, náklady, celková rezerva, WBS) +
  časová osa s dvouřádkovou hlavičkou, víkendy podle kalendáře projektu,
  pruhy s průběhem, souhrnné pruhy, milníky, vazby (FS/SS/FF/SF), kritické
  úkoly červeně, neaktivní úkoly šedě, termíny (deadline), základní plán,
  celková rezerva, čára dneška a datum stavu.
- **Osnova**: sbalování/rozbalování souhrnů (šipka, dvojklik, mezerník, +/−),
  rozbalení do zvolené úrovně, „Rozbalit vše“ / „Sbalit vše“; výchozí stav
  sbalení se přebírá ze souboru.
- **Hledání a filtry**: název, ID, WBS, zdroje, poznámky; filtr kritických,
  milníků, nedokončených, rozpracovaných, dokončených, zpožděných, souhrnných,
  s přiřazenými zdroji a neaktivních úkolů (nadřazené souhrny zůstávají vidět).
- **Detail úkolu**: obecné údaje, přiřazené zdroje, vazby (dvojklik přejde na
  úkol), poznámky.
- **Zdroje**: seznam zdrojů (typ, jednotky, sazby, práce, náklady, přetížení)
  a přiřazení vybraného zdroje.
- **Projekt**: vlastnosti souboru a projektu, statistika, kalendáře.
- **Export**: zobrazené úkoly do CSV (UTF‑8, středník – rovnou do Excelu),
  Ganttův diagram do PNG.
- Nedávné soubory, drag & drop, spuštění s cestou k souboru jako argumentem
  (lze nastavit jako „Otevřít v programu“ pro `.mpp`).

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
| Export CSV / PNG | Ctrl+E / Ctrl+Shift+P |
| Nápověda | F1 |

## Instalace

Spusťte `dist\M3K-Project-View-0.1.0-Setup.exe` (Windows 10/11, 64 bit). Instaluje se
jen pro aktuálního uživatele do `%LOCALAPPDATA%\Programs\M3K Project View` – bez práv
správce a bez výzvy UAC. Volitelně zástupce na ploše a přidružení souborů
`.mpp`, `.mpt` a `.mpx` (zaškrtnuto předem jen tehdy, když v systému žádný program
pro `.mpp` není – Microsoft Project tedy nepřebije). Program se vždy objeví v nabídce
„Otevřít v programu“. Odinstalace je přes Nastavení → Aplikace; na konci se zeptá,
zda smazat i uložené nastavení (nedávné soubory).

Tichá instalace: `M3K-Project-View-0.1.0-Setup.exe /VERYSILENT /TASKS=desktopicon,assoc`

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

Aplikace harmonogram nepřepočítává – zobrazuje termíny, procenta a kritičnost
tak, jak jsou uložené v souboru. Nezobrazuje časově rozložené vytížení zdrojů,
vlastní pole (Text1…), více základních plánů ani vložené podprojekty
(otevřete je samostatně).

Čtení souborů zajišťuje [MPXJ](https://www.mpxj.org) (LGPL 2.1+). Tato aplikace
není produktem společnosti Microsoft.
