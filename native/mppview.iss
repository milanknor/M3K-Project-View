; Inno Setup skript - instalator M3K Project View (nativni C++); program bezi i bez instalace
; Nativni verze nahrazuje Java verzi M3K Project View: pred instalaci ji tise odinstaluje ([Code]).
[Setup]
AppId={{B2F4E6A8-5C31-4D7E-9A20-3E1F7C9D8B45}
AppName=M3K Project View
AppVersion=1.0.9
AppVerName=M3K Project View 1.0.9
AppPublisher=Milan Knor (M3K Soft)
AppPublisherURL=https://m3ksoft.cz
AppSupportURL=https://m3ksoft.cz
AppCopyright=(c) 2026 Milan Knor (M3K Soft), freeware; mppread.dll = GNU LGPL 2.1+ (port MPXJ, Packwood Software)
VersionInfoCompany=Milan Knor (M3K Soft)
VersionInfoVersion=1.0.9
DefaultDirName={autopf}\M3K Soft\M3K Project View
DefaultGroupName=M3K Soft
UninstallDisplayIcon={app}\mppview.exe
UninstallDisplayName=M3K Project View 1.0.9
SetupIconFile=mppview.ico
LicenseFile=LICENSE.txt
OutputDir=out
OutputBaseFilename=M3K-Project-View-1.0.9-Setup
Compression=lzma2/ultra
SolidCompression=yes
ChangesAssociations=yes
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesInstallIn64BitMode=x64compatible
WizardStyle=modern

[Languages]
Name: "cs"; MessagesFile: "compiler:Languages\Czech.isl"

[Tasks]
Name: "assoc"; Description: "Otevirat soubory .mpp v M3K Project View"; GroupDescription: "Pridruzeni souboru:"
Name: "desktopicon"; Description: "Vytvorit zastupce na plose"; GroupDescription: "Zastupci:"; Flags: unchecked

[Files]
Source: "out\mppview.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "out\mppread.dll"; DestDir: "{app}"; Flags: ignoreversion
Source: "out\mppread-src.zip"; DestDir: "{app}"; Flags: ignoreversion
Source: "LICENSE.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "COPYING.LIB"; DestDir: "{app}"; Flags: ignoreversion
Source: "LICENSE-MIT.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "README.md"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\M3K Project View"; Filename: "{app}\mppview.exe"
Name: "{autodesktop}\M3K Project View"; Filename: "{app}\mppview.exe"; Tasks: desktopicon

[Registry]
Root: HKA; Subkey: "Software\Classes\.mpp\OpenWithProgids"; ValueType: string; ValueName: "MppView.Project"; ValueData: ""; Flags: uninsdeletevalue; Tasks: assoc
Root: HKA; Subkey: "Software\Classes\.mpp"; ValueType: string; ValueName: ""; ValueData: "MppView.Project"; Flags: uninsdeletevalue; Tasks: assoc
Root: HKA; Subkey: "Software\Classes\MppView.Project"; ValueType: string; ValueName: ""; ValueData: "Harmonogram MS Project"; Flags: uninsdeletekey; Tasks: assoc
Root: HKA; Subkey: "Software\Classes\MppView.Project\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\mppview.exe,1"; Tasks: assoc
Root: HKA; Subkey: "Software\Classes\MppView.Project\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\mppview.exe"" ""%1"""; Tasks: assoc
Root: HKA; Subkey: "Software\Classes\SystemFileAssociations\.mpp\shell\MppView"; ValueType: string; ValueName: ""; ValueData: "Otevrit v M3K Project View"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\SystemFileAssociations\.mpp\shell\MppView"; ValueType: string; ValueName: "Icon"; ValueData: "{app}\mppview.exe,0"
Root: HKA; Subkey: "Software\Classes\SystemFileAssociations\.mpp\shell\MppView\command"; ValueType: string; ValueName: ""; ValueData: """{app}\mppview.exe"" ""%1"""
Root: HKA; Subkey: "Software\Classes\.xml\OpenWithProgids"; ValueType: string; ValueName: "MppView.Project"; ValueData: ""; Flags: uninsdeletevalue; Tasks: assoc

[Run]
Filename: "{app}\mppview.exe"; Description: "Spustit M3K Project View"; Flags: nowait postinstall skipifsilent

[Code]
// Java verze M3K Project View se instalovala jen pro uzivatele (Inno Setup, AppId 7D3C9A61-...).
// Pred instalaci ji tise odinstalujeme, aby v Aplikacich zustala jedna polozka.
// ponytail: MSI Java verze (pro vsechny uzivatele) se tu neresi - odstrani ho spravce pres msiexec /x.
const
  JavaUninstallKey = 'Software\Microsoft\Windows\CurrentVersion\Uninstall\{7D3C9A61-2B4E-4F8A-9C15-6E0D8B2F4A73}_is1';

function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  Uninstaller: String;
  ResultCode: Integer;
begin
  Result := '';
  if RegQueryStringValue(HKEY_CURRENT_USER, JavaUninstallKey, 'UninstallString', Uninstaller) then
  begin
    Uninstaller := RemoveQuotes(Uninstaller);
    if FileExists(Uninstaller) then
    begin
      if not Exec(Uninstaller, '/VERYSILENT /SUPPRESSMSGBOXES /NORESTART', '', SW_HIDE, ewWaitUntilTerminated, ResultCode)
         or (ResultCode <> 0) then
        Result := 'Nepodarilo se odinstalovat starsi verzi M3K Project View (kod ' + IntToStr(ResultCode) +
                  '). Odinstalujte ji v Nastaveni > Aplikace a spustte instalaci znovu.';
    end;
  end;
end;
