@echo off
setlocal DisableDelayedExpansion
pushd "%~dp0" || exit /b 1

powershell.exe -NoLogo -NoProfile -Command ^
    "$ErrorActionPreference = 'Stop';" ^
    "$startedAt = [DateTime]::UtcNow;" ^
    "& '.\gradlew.bat' ':platform:desktop:packageInnoSetup';" ^
    "if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE };" ^
    "$packageDir = 'platform\desktop\build\compose\binaries\main\innosetup';" ^
    "$installers = @(Get-ChildItem -LiteralPath $packageDir -Filter 'Heartbeat-Lab-*-setup.exe' -File | Where-Object { $_.LastWriteTimeUtc -ge $startedAt });" ^
    "if ($installers.Count -ne 1) { throw ('Expected one installer from this build in ' + $packageDir) };" ^
    "Write-Host ('Opening installer: ' + $installers[0].FullName);" ^
    "Start-Process -FilePath $installers[0].FullName"

set "result=%ERRORLEVEL%"
popd
exit /b %result%
