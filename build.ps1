<#
.SYNOPSIS
  Sestaví M3K Project View (prohlížeč souborů MS Project).

.DESCRIPTION
  Bez parametrů: zkompiluje zdrojáky do build\classes, vytvoří build\viewer.jar
  a spustí automatické testy nad soubory ve složce test\.

  -Package   navíc vytvoří samostatnou Windows aplikaci (s vlastním Java runtime)
             do dist\M3K Project View\ pomocí jpackage a zabalí ji do ZIP.
  -Installer navíc (zahrnuje -Package) sestaví instalátor
             dist\M3K-Project-View-<verze>-Setup.exe pomocí Inno Setup 6
             (installer\setup.iss). Cesta k ISCC.exe: proměnná MSPV_ISCC, jinak
             obvyklá umístění Inno Setupu.
  -SkipTests přeskočí testy.
  -Run       po sestavení spustí aplikaci (volitelně s cestou k souboru v -File).

  Cesta k JDK 21: proměnná prostředí MSPV_JDK, jinak JAVA_HOME, jinak výchozí níže.
#>
param(
    [switch]$Package,
    [switch]$Installer,
    [switch]$SkipTests,
    [switch]$Run,
    [string]$File = ""
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$root = $PSScriptRoot
Set-Location $root
$version = "0.2.0"
$appName = "M3K Project View"
if ($Installer) { $Package = $true }

# ---- JDK -------------------------------------------------------------------
$jdk = $env:MSPV_JDK
if (-not $jdk -or -not (Test-Path "$jdk\bin\javac.exe")) { $jdk = $env:JAVA_HOME }
if (-not $jdk -or -not (Test-Path "$jdk\bin\javac.exe")) {
    $jdk = "C:\Users\Milan\Documents\Codex\2026-09-10\dok\work\jdk\jdk-21.0.12.1+1"
}
if (-not (Test-Path "$jdk\bin\javac.exe")) {
    throw "JDK 21 nenalezeno. Nastavte MSPV_JDK nebo JAVA_HOME na složku s JDK 21 (Temurin: https://adoptium.net)."
}
$javac = "$jdk\bin\javac.exe"
$jar = "$jdk\bin\jar.exe"
$java = "$jdk\bin\java.exe"
$jpackage = "$jdk\bin\jpackage.exe"
Write-Host "JDK: $jdk"
$py = if (Get-Command py -ErrorAction SilentlyContinue) { "py" } else { "python" }

function Assert-Ok($step) {
    if ($LASTEXITCODE -ne 0) { throw "Krok selhal: $step (kód $LASTEXITCODE)" }
}

# ---- kompilace ---------------------------------------------------------------
$classes = "$root\build\classes"
if (Test-Path $classes) { Remove-Item $classes -Recurse -Force }
New-Item -ItemType Directory -Force $classes | Out-Null
$libJars = Get-ChildItem "$root\lib\*.jar" | ForEach-Object { $_.FullName }
$cp = $libJars -join ";"
$sources = Get-ChildItem "$root\src" -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$sourcesFile = "$root\build\sources.txt"
# argfile pro javac: bez BOM, cesty v uvozovkách a s lomítky (mezera v názvu složky)
$quoted = $sources | ForEach-Object { '"' + ($_ -replace '\\', '/') + '"' }
[IO.File]::WriteAllLines($sourcesFile, [string[]]$quoted, [Text.UTF8Encoding]::new($false))
Write-Host "Kompiluji $($sources.Count) souborů…"
& $javac -encoding UTF-8 --release 21 -Xlint:all,-serial -Werror -cp $cp -d $classes "@$sourcesFile"
Assert-Ok "javac"

# licence třetích stran → zdroje v jaru (dialog Nápověda → Licence)
Write-Host "Licence třetích stran…"
& $py "$root\tools\collect_licenses.py"
Assert-Ok "collect_licenses.py"
$licRes = "$classes\cz\msproject\viewer\licenses"
New-Item -ItemType Directory -Force $licRes | Out-Null
Copy-Item "$root\lib\licenses\*" $licRes -Recurse
Copy-Item "$root\LICENSE.txt" $licRes

& $jar --create --file "$root\build\viewer.jar" --main-class cz.msproject.viewer.App -C $classes .
Assert-Ok "jar"
Write-Host "Vytvořen build\viewer.jar"

# ---- testy -------------------------------------------------------------------
$runCp = "$root\build\viewer.jar;$root\lib\*"
if (-not $SkipTests) {
    $testFiles = Get-ChildItem "$root\test" -Include *.mpp, *.xml, *.mpx, *.mpd -Recurse | ForEach-Object { $_.FullName }
    if ($testFiles.Count -eq 0) { throw "Ve složce test\ nejsou žádné testovací soubory." }
    Write-Host "Testy načtení ($($testFiles.Count) souborů)…"
    & $java "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -cp $runCp cz.msproject.viewer.App --test @testFiles
    Assert-Ok "--test"
    Write-Host "Test uživatelského rozhraní…"
    $uiFile = Get-ChildItem "$root\test" -Include *.mpp, *.xml, *.mpx, *.mpd -Recurse | Sort-Object Length -Descending | Select-Object -First 1
    & $java "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -cp $runCp cz.msproject.viewer.App --ui-test $uiFile.FullName "$root\build\ui-test.png"
    Assert-Ok "--ui-test"
}

# ---- balíček -----------------------------------------------------------------
if ($Package) {
    $input = "$root\build\input"
    if (Test-Path $input) { Remove-Item $input -Recurse -Force }
    New-Item -ItemType Directory -Force $input | Out-Null
    Copy-Item "$root\build\viewer.jar" $input
    Copy-Item "$root\lib\*.jar" $input

    $icon = "$root\build\icon.ico"
    if (-not (Test-Path $icon) -or -not (Test-Path "$root\build\icon-doc.ico")) {
        Write-Host "Generuji ikonu…"
        & $py "$root\tools\make_icon.py" $icon
        if ($LASTEXITCODE -ne 0) { Write-Warning "Ikonu se nepodařilo vygenerovat, použije se výchozí."; $icon = $null }
    }

    $dist = "$root\dist"
    $appDir = "$dist\$appName"
    if (Test-Path $appDir) { Remove-Item $appDir -Recurse -Force }
    New-Item -ItemType Directory -Force $dist | Out-Null
    $args = @(
        "--type", "app-image",
        "--input", $input,
        "--dest", $dist,
        "--name", $appName,
        "--main-jar", "viewer.jar",
        "--main-class", "cz.msproject.viewer.App",
        "--app-version", $version,
        "--vendor", "Milan Knor",
        "--description", "Prohlizec souboru Microsoft Project",
        "--copyright", "(c) 2026 Milan Knor. MPXJ (LGPL 2.1+), OpenJDK (GPLv2+CE)",
        "--add-modules", "java.desktop,java.sql,java.naming,java.prefs,java.logging,java.xml,java.management,java.security.jgss,jdk.charsets,jdk.unsupported,jdk.crypto.ec",
        "--java-options", "-Dlog4j2.loggerContextFactory=org.apache.logging.log4j.simple.SimpleLoggerContextFactory",
        "--java-options", "-Dfile.encoding=UTF-8",
        "--java-options", "-Xss4m",
        "--java-options", "-XX:MaxRAMPercentage=60",
        "--java-options", "-XX:+UseStringDeduplication"
    )
    if ($icon) { $args += @("--icon", $icon) }
    Write-Host "jpackage…"
    & $jpackage @args
    Assert-Ok "jpackage"

    Copy-Item "$root\README.md" "$appDir\CTETE-ME.md"
    Copy-Item "$root\LICENSE.txt" "$appDir\"
    Copy-Item "$root\lib\licenses" "$appDir\licenses" -Recurse
    if (Test-Path "$root\build\icon-doc.ico") { Copy-Item "$root\build\icon-doc.ico" "$appDir\app\doc.ico" }

    $zip = "$dist\M3K-Project-View-Windows.zip"
    if (Test-Path $zip) { Remove-Item $zip -Force }
    Compress-Archive -Path $appDir -DestinationPath $zip
    Write-Host "Hotový balíček: $appDir"
    Write-Host "ZIP: $zip"
}

# ---- instalátor (Inno Setup) -------------------------------------------------
if ($Installer) {
    $candidates = @(
        $env:MSPV_ISCC,
        "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe",
        "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
        "$env:ProgramFiles\Inno Setup 6\ISCC.exe"
    )
    $iscc = $candidates | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
    if (-not $iscc) {
        $cmd = Get-Command ISCC.exe -ErrorAction SilentlyContinue
        if ($cmd) { $iscc = $cmd.Source }
    }
    if (-not $iscc) {
        throw "Inno Setup 6 nenalezen. Nainstalujte ho (winget install JRSoftware.InnoSetup) nebo nastavte MSPV_ISCC na cestu k ISCC.exe."
    }
    Write-Host "Inno Setup: $iscc"
    & $iscc "/Q" "/DAppVersion=$version" "$root\installer\setup.iss"
    Assert-Ok "ISCC"
    $setup = "$dist\M3K-Project-View-$version-Setup.exe"
    if (-not (Test-Path $setup)) { throw "Instalátor nevznikl: $setup" }
    Write-Host ("Instalátor: {0} ({1:N1} MB)" -f $setup, ((Get-Item $setup).Length / 1MB))
}

if ($Run) {
    $argList = @("-cp", $runCp, "cz.msproject.viewer.App")
    if ($File) { $argList += $File }
    Start-Process -FilePath $java -ArgumentList $argList -WorkingDirectory $root | Out-Null
}
