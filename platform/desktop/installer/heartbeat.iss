#ifndef AppVersion
  #error AppVersion is not defined
#endif

#ifndef SourceDir
  #error SourceDir is not defined
#endif

#ifndef OutputDir
  #error OutputDir is not defined
#endif

#define AppName "Heartbeat Lab"
#define AppExeName "Heartbeat.exe"

[Setup]
AppId={{6DB8F558-591F-45BD-A896-4F57C84EEE0F}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
UninstallDisplayName={#AppName}
DefaultDirName={userpf}\{#AppName}
DefaultGroupName={#AppName}
AllowNoIcons=yes
DisableProgramGroupPage=yes
OutputDir={#OutputDir}
SetupIconFile=..\icons\heartbeat-lab.ico
OutputBaseFilename=Heartbeat-Lab-{#AppVersion}-setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\{#AppExeName}
PrivilegesRequired=lowest

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[InstallDelete]
Type: files; Name: "{autoprograms}\Heartbeat.lnk"; Check: IsOldShortcut('{autoprograms}\Heartbeat.lnk')
Type: files; Name: "{autodesktop}\Heartbeat.lnk"; Check: IsOldShortcut('{autodesktop}\Heartbeat.lnk')

[Icons]
Name: "{autoprograms}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Check: not WizardNoIcons
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(AppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[Code]
function IsOldShortcut(Path: String): Boolean;
var
  Shell, Shortcut: Variant;
begin
  Result := False;
  Path := ExpandConstant(Path);
  if FileExists(Path) then
    try
      Shell := CreateOleObject('WScript.Shell');
      Shortcut := Shell.CreateShortcut(Path);
      Result := CompareText(Shortcut.TargetPath, ExpandConstant('{app}\{#AppExeName}')) = 0;
    except
      Log('Could not inspect shortcut ' + Path);
    end;
end;
