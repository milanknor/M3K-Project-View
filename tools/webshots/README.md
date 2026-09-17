# Snímky obrazovky pro webovou stránku

Pomocné třídy, kterými vznikly obrázky ve `web\assets\`. Nejsou součástí aplikace
a nekompilují se s ní — spouštějí se ručně, když je potřeba snímky obnovit
(nová verze, změna vzhledu).

| soubor             | co dělá                                                                  |
|--------------------|--------------------------------------------------------------------------|
| `DemoProject.java` | vytvoří anonymní ukázkový harmonogram (MSPDI XML, 40 úkolů, 8 zdrojů)     |
| `Shots.java`       | otevře okno v předvídatelném stavu a uloží PNG jednotlivých záložek       |

Ukázkový projekt je smyšlená „Novostavba výrobní haly“ firmy „Ukázková stavební
s.r.o.“ — žádná data z reálných zakázek. Obsahuje souhrnné úkoly s dopočítanými
termíny, vazby konec–začátek, kritickou cestu, základní plán, státní svátky
v kalendáři a několik přetížených období u zdrojů.

`Shots` sahá na vnitřní pole `MainWindow`, proto patří do balíčku
`cz.msproject.viewer` a překládá se dohromady s aplikací (viz postup níže).

## Postup

```powershell
$jdk  = "C:\Users\Milan\Documents\Codex\2026-09-10\dok\work\jdk\jdk-21.0.12.1+1"
$cp   = "build\viewer.jar;lib\*"
$tmp  = "$env:TEMP\webshots"
$vzor = "$env:USERPROFILE\Documents\Ukázky\Výrobní hala - ukázkový harmonogram.xml"

.\build.ps1 -SkipTests           # potřebujeme build\viewer.jar

# 1) ukázková data — cesta k souboru je vidět ve stavovém řádku, patří do složky
#    s rozumným názvem (ne do dočasného adresáře s náhodným jménem)
New-Item -ItemType Directory -Force "$tmp\src\cz\msproject\viewer", (Split-Path $vzor) | Out-Null
Copy-Item tools\webshots\DemoProject.java "$tmp\src\"
Copy-Item tools\webshots\Shots.java "$tmp\src\cz\msproject\viewer\"
& "$jdk\bin\javac.exe" -encoding UTF-8 -cp $cp -d "$tmp\classes" "$tmp\src\DemoProject.java" "$tmp\src\cz\msproject\viewer\Shots.java"
& "$jdk\bin\java.exe" -cp "$tmp\classes;$cp" DemoProject $vzor

# 2) snímky – světlý a tmavý motiv (vlastnost se v PowerShellu uzavírá do uvozovek,
#    jinak si argument rozdělí)
& "$jdk\bin\java.exe" -cp "$tmp\classes;$cp" cz.msproject.viewer.Shots $vzor "$tmp\out"
& "$jdk\bin\java.exe" "-Dm3k.dark=true" -cp "$tmp\classes;$cp" cz.msproject.viewer.Shots $vzor "$tmp\out-dark"

# 3) na web
Copy-Item "$tmp\out\gantt.png","$tmp\out\timeline.png","$tmp\out\usage.png","$tmp\out\calendar.png" web\assets\
Copy-Item "$tmp\out-dark\gantt.png" web\assets\gantt-dark.png
```

`Shots` před snímkem nastaví dělicí příčku na 820 px a zavolá „Přizpůsobit šířky
obsahu“, jinak jsou názvy úkolů oříznuté na pár znaků; ve Vytížení vybere druhý
zdroj, aby spodní panel nebyl prázdný. Vzniká i `gantt-full.png` — celý diagram
v plné velikosti, ze kterého je výřez do hlavičky stránky.

Stránka na obrázky odkazuje s razítkem (`gantt.png?v={{ASSETS}}`), které doplní
`build.ps1 -Web` podle času poslední změny ve `web\assets\`. Bez něj by
prohlížeči, který stránku už viděl, zůstaly staré snímky — server neposílá
`Cache-Control`.

Na kritickou cestu pozor: v ukázkových datech je kritických jen několik úkolů
hlavní větve, jinak je celý diagram červený a snímek vypadá jako chybový stav.
