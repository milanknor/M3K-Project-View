; Instalátor M3K Project View (Inno Setup 6).
; Vstup: dist\M3K Project View\ (výstup jpackage app-image z build.ps1 -Package).
; Výstup: dist\M3K-Project-View-<verze>-Setup.exe
; Sestavení: .\build.ps1 -Installer   (nebo ISCC.exe /DAppVersion=0.1.0 installer\setup.iss)

#ifndef AppVersion
  #define AppVersion "0.1.0"
#endif
#define AppName "M3K Project View"
#define AppExeName "M3K Project View.exe"
#define AppPublisher "Milan Knor"
#define ProgId "M3KProjectView.Project"
#define SrcDir "..\dist\M3K Project View"

[Setup]
AppId={{7D3C9A61-2B4E-4F8A-9C15-6E0D8B2F4A73}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
AppCopyright=© 2026 Milan Knor. MPXJ (LGPL 2.1+), OpenJDK (GPLv2+CE)
VersionInfoCompany={#AppPublisher}
VersionInfoVersion={#AppVersion}
VersionInfoDescription=Instalátor {#AppName}
DefaultDirName={userpf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
UninstallDisplayIcon={app}\{#AppExeName}
UninstallDisplayName={#AppName}
OutputDir=..\dist
OutputBaseFilename=M3K-Project-View-{#AppVersion}-Setup
SetupIconFile=..\build\icon.ico
LicenseFile=..\LICENSE.txt
Compression=lzma2/ultra64
SolidCompression=yes
LZMAUseSeparateProcess=yes
WizardStyle=modern
; Aplikace obsahuje 64bitový Java runtime.
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
; Instalace jen pro aktuálního uživatele (%LOCALAPPDATA%\Programs) – bez práv správce a bez UAC.
PrivilegesRequired=lowest
ChangesAssociations=yes
CloseApplications=yes
RestartApplications=no

[Languages]
Name: "czech"; MessagesFile: "compiler:Languages\Czech.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[CustomMessages]
czech.AssocTask=Otevírat soubory .mpp, .mpt a .mpx tímto programem (nastavit jako výchozí)
czech.AssocGroup=Přidružení souborů:
czech.ProgIdName=Soubor Microsoft Project
czech.DeletePrefs=Chcete smazat i uživatelské nastavení programu (nedávné soubory, poslední složka)?
english.AssocTask=Open .mpp, .mpt and .mpx files with this program (set as default)
english.AssocGroup=File associations:
english.ProgIdName=Microsoft Project File
english.DeletePrefs=Do you also want to delete the program's user settings (recent files, last folder)?

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "assoc"; Description: "{cm:AssocTask}"; GroupDescription: "{cm:AssocGroup}"; Flags: unchecked

[Files]
Source: "{#SrcDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Comment: "Prohlížeč souborů Microsoft Project"
Name: "{group}\Čtěte mě"; Filename: "{app}\CTETE-ME.md"
Name: "{group}\Licence"; Filename: "{app}\LICENSE.txt"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Registry]
; ProgID programu – vždy, aby se program nabízel v nabídce „Otevřít v programu".
Root: HKA; Subkey: "Software\Classes\{#ProgId}"; ValueType: string; ValueData: "{cm:ProgIdName}"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\{#ProgId}\DefaultIcon"; ValueType: string; ValueData: """{app}\{#AppExeName}"",0"
Root: HKA; Subkey: "Software\Classes\{#ProgId}\shell\open\command"; ValueType: string; ValueData: """{app}\{#AppExeName}"" ""%1"""
Root: HKA; Subkey: "Software\Classes\.mpp\OpenWithProgids"; ValueType: string; ValueName: "{#ProgId}"; ValueData: ""; Flags: uninsdeletevalue uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.mpt\OpenWithProgids"; ValueType: string; ValueName: "{#ProgId}"; ValueData: ""; Flags: uninsdeletevalue uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.mpx\OpenWithProgids"; ValueType: string; ValueName: "{#ProgId}"; ValueData: ""; Flags: uninsdeletevalue uninsdeletekeyifempty
Root: HKA; Subkey: "Software\Classes\.mpd\OpenWithProgids"; ValueType: string; ValueName: "{#ProgId}"; ValueData: ""; Flags: uninsdeletevalue uninsdeletekeyifempty
; Výchozí program pro přípony – jen když to uživatel zaškrtne.
Root: HKA; Subkey: "Software\Classes\.mpp"; ValueType: string; ValueData: "{#ProgId}"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: assoc
Root: HKA; Subkey: "Software\Classes\.mpt"; ValueType: string; ValueData: "{#ProgId}"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: assoc
Root: HKA; Subkey: "Software\Classes\.mpx"; ValueType: string; ValueData: "{#ProgId}"; Flags: uninsdeletevalue uninsdeletekeyifempty; Tasks: assoc

[Run]
Filename: "{app}\{#AppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(AppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[Code]
// Pokud v systému není žádný program pro .mpp, nabídneme přidružení rovnou zaškrtnuté.
procedure InitializeWizard;
var
  Existing: String;
begin
  if not RegQueryStringValue(HKEY_CLASSES_ROOT, '.mpp', '', Existing) or (Existing = '') then
    WizardSelectTasks('assoc');
end;

// Po odinstalaci se zeptáme, zda smazat nastavení uložené přes java.util.prefs.
const
  PrefsKey = 'Software\JavaSoft\Prefs\cz\msproject\viewer';

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if (CurUninstallStep = usPostUninstall) and (not UninstallSilent)
     and RegKeyExists(HKEY_CURRENT_USER, PrefsKey) then
  begin
    if MsgBox(CustomMessage('DeletePrefs'), mbConfirmation, MB_YESNO) = IDYES then
    begin
      RegDeleteKeyIncludingSubkeys(HKEY_CURRENT_USER, PrefsKey);
      RegDeleteKeyIfEmpty(HKEY_CURRENT_USER, 'Software\JavaSoft\Prefs\cz\msproject');
      RegDeleteKeyIfEmpty(HKEY_CURRENT_USER, 'Software\JavaSoft\Prefs\cz');
    end;
  end;
end;
