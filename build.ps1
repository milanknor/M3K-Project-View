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
  -Msi       navíc (zahrnuje -Package) sestaví dist\M3K-Project-View-<verze>.msi
             pro hromadné nasazení (Intune/SCCM/GPO): instalace pro všechny
             uživatele, tiše přes „msiexec /i soubor.msi /qn“. Vyžaduje WiX
             Toolset 3 (proměnná MSPV_WIX, PATH, build\wix nebo Program Files).
  -KeepAppImage  znovu nesestaví dist\<název>\ – použije existující složku.
             Slouží k zabalení aplikace, jejíž .exe podepsal někdo jiný.
  -SkipTests přeskočí testy.
  -Run       po sestavení spustí aplikaci (volitelně s cestou k souboru v -File).

  Cesta k JDK 21: proměnná prostředí MSPV_JDK, jinak JAVA_HOME, jinak výchozí níže.

  Podpis (volitelný, bez nastavení se přeskočí – Windows pak hlásí „Neznámý vydavatel“):
    MSPV_CERT [+ MSPV_CERT_PASS]  soubor .pfx s certifikátem
    MSPV_CERT_THUMBPRINT          otisk certifikátu v úložišti Windows (HW token)
    MSPV_SIGN_CMD                 vlastní skript, který dostane soubor jako argument
                                  (cloudové podepisování / HSM)
    MSPV_TIMESTAMP                server časového razítka (výchozí DigiCert)
    MSPV_SIGNTOOL                 cesta k signtool.exe (jinak se hledá ve Windows SDK)
  Vedle instalátoru a ZIPu vždy vznikne soubor .sha256 s otiskem k ověření.
#>
param(
    [switch]$Package,
    [switch]$Installer,
    [switch]$Msi,
    [switch]$KeepAppImage,
    [switch]$SkipTests,
    [switch]$Run,
    [string]$File = ""
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$root = $PSScriptRoot
Set-Location $root
$version = "0.2.1"
$appName = "M3K Project View"
# stálý UpgradeCode MSI – nikdy neměnit, jinak se nová verze nenainstaluje přes starou
$msiUpgradeUuid = "9F2B1C57-4E3A-4D8B-9A61-7C0D8B2F4A73"
if ($Installer -or $Msi) { $Package = $true }

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

# ---- podpis a otisky ---------------------------------------------------------

function Get-SignTool {
    if ($env:MSPV_SIGNTOOL -and (Test-Path $env:MSPV_SIGNTOOL)) { return $env:MSPV_SIGNTOOL }
    $cmd = Get-Command signtool.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $sdk = Get-ChildItem "${env:ProgramFiles(x86)}\Windows Kits\10\bin\*\x64\signtool.exe" -ErrorAction SilentlyContinue |
        Sort-Object FullName | Select-Object -Last 1
    if ($sdk) { return $sdk.FullName }
    return $null
}

function Test-SigningConfigured {
    return [bool]($env:MSPV_SIGN_CMD -or $env:MSPV_CERT -or $env:MSPV_CERT_THUMBPRINT)
}

# Podepíše soubor certifikátem podle proměnných prostředí a ověří výsledek.
function Invoke-Signing($path) {
    $name = [IO.Path]::GetFileName($path)
    # jpackage označuje spouštěcí soubor jen pro čtení – signtool by hlásil „Access is denied“
    $item = Get-Item $path
    if ($item.IsReadOnly) { $item.IsReadOnly = $false }
    if ($env:MSPV_SIGN_CMD) {
        & $env:MSPV_SIGN_CMD $path
        Assert-Ok "podpis $name"
    } else {
        $signtool = Get-SignTool
        if (-not $signtool) {
            throw "signtool.exe nenalezen. Nainstalujte Windows SDK, nebo nastavte MSPV_SIGNTOOL na cestu k signtool.exe."
        }
        $ts = if ($env:MSPV_TIMESTAMP) { $env:MSPV_TIMESTAMP } else { "http://timestamp.digicert.com" }
        $a = @("sign", "/fd", "SHA256", "/td", "SHA256", "/tr", $ts, "/d", $appName)
        if ($env:MSPV_CERT) {
            if (-not (Test-Path $env:MSPV_CERT)) { throw "Certifikát nenalezen: $env:MSPV_CERT" }
            $a += @("/f", $env:MSPV_CERT)
            if ($env:MSPV_CERT_PASS) { $a += @("/p", $env:MSPV_CERT_PASS) }
        } else {
            $a += @("/sha1", $env:MSPV_CERT_THUMBPRINT)
        }
        $a += $path
        & $signtool @a
        Assert-Ok "signtool ($name)"
    }
    $sig = Get-AuthenticodeSignature $path
    if ($sig.Status -eq "NotSigned" -or $sig.Status -eq "HashMismatch") {
        throw "Podpis souboru $name selhal: $($sig.Status) – $($sig.StatusMessage)"
    }
    if ($sig.Status -ne "Valid") {
        # např. testovací certifikát bez důvěryhodného kořene nebo nedostupná kontrola odvolání
        Write-Warning ("Soubor {0} je podepsaný, ale řetěz důvěry se neověřil: {1}" -f $name, $sig.Status)
    }
    Write-Host ("Podepsáno: {0} – {1}" -f $name, $sig.SignerCertificate.Subject)
}

# Složka s WiX 3 (candle.exe/light.exe) pro jpackage --type msi; null = nenalezeno.
function Get-WixDir {
    if ($env:MSPV_WIX -and (Test-Path "$env:MSPV_WIX\candle.exe")) { return $env:MSPV_WIX }
    $cmd = Get-Command candle.exe -ErrorAction SilentlyContinue
    if ($cmd) { return (Split-Path $cmd.Source) }
    if (Test-Path "$root\build\wix\candle.exe") { return "$root\build\wix" }
    $pf = Get-ChildItem "${env:ProgramFiles(x86)}\WiX Toolset v3*\bin\candle.exe", "$env:ProgramFiles\WiX Toolset v3*\bin\candle.exe" -ErrorAction SilentlyContinue |
        Sort-Object FullName | Select-Object -Last 1
    if ($pf) { return (Split-Path $pf.FullName) }
    return $null
}

# jpackage plánuje RemoveExistingProducts na sekvenci 798, tedy ještě před
# InstallValidate/InstallInitialize – mimo instalační transakci. Windows Installer
# to považuje za chybu (ICE27) a aktualizace přes starší verzi může selhat.
# Posuneme akci na 1501 (hned po InstallInitialize = standardní „early removal“).
function Repair-MsiSequence($path) {
    $inst = New-Object -ComObject WindowsInstaller.Installer
    $db = $inst.GetType().InvokeMember("OpenDatabase", "InvokeMethod", $null, $inst, @($path, 1))  # 1 = transact
    $view = $db.GetType().InvokeMember("OpenView", "InvokeMethod", $null, $db,
        @('UPDATE `InstallExecuteSequence` SET `Sequence` = 1501 WHERE `Action` = ''RemoveExistingProducts'''))
    $view.GetType().InvokeMember("Execute", "InvokeMethod", $null, $view, $null)
    $view.GetType().InvokeMember("Close", "InvokeMethod", $null, $view, $null)
    $db.GetType().InvokeMember("Commit", "InvokeMethod", $null, $db, $null)
    # bez uvolnění COM objektů drží Windows Installer soubor otevřený
    foreach ($o in @($view, $db, $inst)) { [void][Runtime.InteropServices.Marshal]::ReleaseComObject($o) }
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
    Write-Host "MSI: RemoveExistingProducts přeplánováno na 1501 (kvůli ICE27)."
}

# Kontrola MSI validátorem ICE (WiX smoke.exe); chyby zastaví sestavení.
function Test-Msi($path, $wixDir) {
    $smoke = Join-Path $wixDir "smoke.exe"
    if (-not (Test-Path $smoke)) { return }
    $out = & $smoke -nologo $path 2>&1 | ForEach-Object { $_.ToString() }
    $errors = @($out | Where-Object { $_ -match ": error " })
    $warnings = @($out | Where-Object { $_ -match ": warning " })
    Write-Host ("MSI ověřeno (ICE): {0} chyb, {1} varování" -f $errors.Count, $warnings.Count)
    if ($errors.Count -gt 0) {
        $errors | ForEach-Object { Write-Host "  $_" }
        throw "MSI neprošlo kontrolou ICE."
    }
}

# Zapíše <soubor>.sha256 ve formátu nástrojů sha256sum/certutil.
function Write-Sha256($path) {
    $hash = (Get-FileHash -Algorithm SHA256 $path).Hash.ToLower()
    $name = [IO.Path]::GetFileName($path)
    [IO.File]::WriteAllText("$path.sha256", "$hash  $name`r`n", [Text.UTF8Encoding]::new($false))
    Write-Host ("SHA-256: {0}  {1}" -f $hash, $name)
}

# ---- balíček -----------------------------------------------------------------
if ($Package) {
    $dist = "$root\dist"
    $appDir = "$dist\$appName"
    New-Item -ItemType Directory -Force $dist | Out-Null

    $icon = "$root\build\icon.ico"
    if (-not (Test-Path $icon) -or -not (Test-Path "$root\build\icon-doc.ico")) {
        Write-Host "Generuji ikonu…"
        & $py "$root\tools\make_icon.py" $icon
        if ($LASTEXITCODE -ne 0) { Write-Warning "Ikonu se nepodařilo vygenerovat, použije se výchozí."; $icon = $null }
    }
}

# vlastní sestavení app-image (s -KeepAppImage se použije existující, např. s cizím podpisem)
if ($Package -and $KeepAppImage -and (Test-Path "$appDir\$appName.exe")) {
    Write-Host "Používám existující app-image: $appDir"
} elseif ($Package) {
    $input = "$root\build\input"
    if (Test-Path $input) { Remove-Item $input -Recurse -Force }
    New-Item -ItemType Directory -Force $input | Out-Null
    Copy-Item "$root\build\viewer.jar" $input
    Copy-Item "$root\lib\*.jar" $input

    if (Test-Path $appDir) { Remove-Item $appDir -Recurse -Force }
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
        "--java-options", "-Djavax.xml.accessExternalDTD=",
        "--java-options", "-Djavax.xml.accessExternalSchema=",
        "--java-options", "-Djdk.xml.entityExpansionLimit=1000",
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
}

# ---- podpis aplikace, ZIP a otisk -------------------------------------------
if ($Package) {
    # podpis spouštěcího souboru ještě před zabalením, aby ho měl ZIP, instalátor i MSI
    if (Test-SigningConfigured) {
        Invoke-Signing "$appDir\$appName.exe"
    } else {
        $sig = Get-AuthenticodeSignature "$appDir\$appName.exe"
        if ($sig.Status -ne "NotSigned") {
            Write-Host ("Spouštěcí soubor je už podepsaný: {0}" -f $sig.SignerCertificate.Subject)
        } else {
            Write-Host "Podpis přeskočen (nenastaveno MSPV_CERT / MSPV_CERT_THUMBPRINT / MSPV_SIGN_CMD) – Windows bude hlásit „Neznámý vydavatel“."
        }
    }

    $zip = "$dist\M3K-Project-View-Windows.zip"
    if (Test-Path $zip) { Remove-Item $zip -Force }
    Compress-Archive -Path $appDir -DestinationPath $zip
    Write-Sha256 $zip
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
    if (Test-SigningConfigured) { Invoke-Signing $setup }
    Write-Sha256 $setup
    Write-Host ("Instalátor: {0} ({1:N1} MB)" -f $setup, ((Get-Item $setup).Length / 1MB))
}

# ---- MSI pro hromadné nasazení (jpackage + WiX 3) ----------------------------
if ($Msi) {
    $wix = Get-WixDir
    if (-not $wix) {
        throw "WiX Toolset 3 nenalezen. Nainstalujte ho (winget install WiXToolset.WiXToolset), rozbalte binárky wix314-binaries.zip do build\wix, nebo nastavte MSPV_WIX."
    }
    Write-Host "WiX: $wix"
    $env:PATH = "$wix;$env:PATH"

    # Přiřazení souborů (.mpp…) se do MSI záměrně nedávají: jpackage sice do balíčku
    # zapíše ProgID i příkaz pro otevření, ale klíč přípony se při instalaci nevytvoří
    # (ověřeno na JDK 21), takže by asociace stejně nefungovala a v registru by zůstávaly
    # osiřelé ProgID. Přípony řeší instalátor EXE, u hromadného nasazení pak politika
    # (Intune/GPO „Set Default Associations“), což je na Windows 10/11 stejně jediný
    # spolehlivý způsob, jak výchozí program nastavit.
    $msiDir = "$root\build\msi"
    if (Test-Path $msiDir) { [IO.Directory]::Delete($msiDir, $true) }
    New-Item -ItemType Directory -Force $msiDir | Out-Null
    Write-Host "jpackage --type msi…"
    & $jpackage --type msi --app-image $appDir --name $appName --app-version $version --dest $msiDir `
        --vendor "Milan Knor" --description "Prohlizec souboru Microsoft Project" `
        --copyright "(c) 2026 Milan Knor. MPXJ (LGPL 2.1+), OpenJDK (GPLv2+CE)" `
        --win-menu --win-menu-group $appName --win-upgrade-uuid $msiUpgradeUuid
    Assert-Ok "jpackage --type msi"
    $built = Get-ChildItem "$msiDir\*.msi" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $built) { throw "MSI nevzniklo ve složce $msiDir." }
    $msiPath = "$dist\M3K-Project-View-$version.msi"
    Move-Item $built.FullName $msiPath -Force
    Repair-MsiSequence $msiPath
    Test-Msi $msiPath $wix
    # podpis až po úpravách databáze, jinak by ho změna souboru zneplatnila
    if (Test-SigningConfigured) { Invoke-Signing $msiPath }
    Write-Sha256 $msiPath
    Write-Host ("MSI: {0} ({1:N1} MB)" -f $msiPath, ((Get-Item $msiPath).Length / 1MB))
}

if ($Run) {
    $argList = @("-cp", $runCp, "cz.msproject.viewer.App")
    if ($File) { $argList += $File }
    Start-Process -FilePath $java -ArgumentList $argList -WorkingDirectory $root | Out-Null
}
