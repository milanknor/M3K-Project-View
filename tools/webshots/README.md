# Snímky obrazovky pro webovou stránku

Pomocné třídy, kterými vznikly obrázky ve `web\assets\`. Nejsou součástí aplikace
a nekompilují se s ní — spouštějí se ručně, když je potřeba snímky obnovit
(nová verze, změna vzhledu).

| soubor             | co dělá                                                                 |
|--------------------|-------------------------------------------------------------------------|
| `DemoProject.java` | vytvoří ukázkový stavební harmonogram (MSPDI XML, 36 úkolů, 6 zdrojů)     |
| `Shots.java`       | otevře okno v předvídatelném stavu a uloží PNG jednotlivých záložek       |

`Shots` sahá na vnitřní pole `MainWindow`, proto patří do balíčku
`cz.msproject.viewer` a překládá se dohromady s aplikací (viz postup níže).

## Postup

```powershell
$jdk = "C:\Users\Milan\Documents\Codex\2026-09-10\dok\work\jdk\jdk-21.0.12.1+1"
$cp  = "build\viewer.jar;lib\*"
$tmp = "$env:TEMP\webshots"

.\build.ps1 -SkipTests           # potřebujeme build\viewer.jar

# 1) ukázková data — uložit do složky, jejíž cesta se smí objevit ve stavovém řádku
New-Item -ItemType Directory -Force "$tmp\src\cz\msproject\viewer" | Out-Null
Copy-Item tools\webshots\DemoProject.java "$tmp\src\"
Copy-Item tools\webshots\Shots.java "$tmp\src\cz\msproject\viewer\"
& "$jdk\bin\javac.exe" -encoding UTF-8 -cp $cp -d "$tmp\classes" "$tmp\src\DemoProject.java"
& "$jdk\bin\java.exe" -cp "$tmp\classes;$cp" DemoProject "$env:USERPROFILE\Documents\Harmonogramy\Bytovy dum Husova - HMG.xml"

# 2) snímky
& "$jdk\bin\javac.exe" -encoding UTF-8 -cp $cp -d "$tmp\classes" "$tmp\src\cz\msproject\viewer\Shots.java"
& "$jdk\bin\java.exe" -cp "$tmp\classes;$cp" cz.msproject.viewer.Shots "$env:USERPROFILE\Documents\Harmonogramy\Bytovy dum Husova - HMG.xml" "$tmp\out"
```

Vzniknou `gantt.png`, `timeline.png`, `usage.png`, `calendar.png` (okno 1400×880)
a `gantt-full.png` — celý diagram v plné velikosti, ze kterého je výřez do hlavičky
stránky. Tmavou variantu (`gantt-dark.png` na webu) dá stejný příkaz se
zapnutým tmavým motivem, který se vynutí systémovou vlastností (v PowerShellu ji
uzavřete do uvozovek, jinak si argument rozdělí):

```powershell
& "$jdk\bin\java.exe" "-Dm3k.dark=true" -cp "$tmp\classes;$cp" cz.msproject.viewer.Shots "$env:USERPROFILE\Documents\Harmonogramy\Bytovy dum Husova - HMG.xml" "$tmp\out-dark"
Copy-Item "$tmp\out-dark\gantt.png" web\assets\gantt-dark.png
```

Pozor na dvě věci:

* **cesta k souboru je vidět ve stavovém řádku** — ukázkový harmonogram proto
  nenechávejte v dočasné složce s náhodným názvem;
* **kritická cesta** — v ukázkových datech je kritických jen několik úkolů hlavní
  větve, jinak je celý diagram červený a snímek vypadá jako chybový stav.
