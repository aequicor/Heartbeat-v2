@echo off
setlocal DisableDelayedExpansion
pushd "%~dp0" || exit /b 1

powershell.exe -NoLogo -NoProfile -Command ^
    "$ErrorActionPreference = 'Stop';" ^
    "$javaHomes = @($env:JAVA_HOME, $env:JDK_HOME);" ^
    "$javaHomes += @(Get-Command java.exe -CommandType Application -All -ErrorAction SilentlyContinue | ForEach-Object { Split-Path -Parent (Split-Path -Parent $_.Source) });" ^
    "$jdkFolders = @((Join-Path $env:USERPROFILE '.jdks'));" ^
    "$jdkFolders += @(foreach ($vendor in @('Java', 'Eclipse Adoptium', 'Microsoft', 'Amazon Corretto', 'BellSoft', 'Zulu')) { Join-Path $env:ProgramFiles $vendor });" ^
    "$javaHomes += @(foreach ($folder in $jdkFolders) { if (Test-Path -LiteralPath $folder -PathType Container) { (Get-ChildItem -LiteralPath $folder -Directory -ErrorAction SilentlyContinue).FullName } });" ^
    "$javaHomes += (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr');" ^
    "$javaHome = $javaHomes | Where-Object { $_ } | ForEach-Object { $_.Trim().Trim([char]34) } | Where-Object {" ^
    "    $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin\java.exe') -PathType Leaf) -and (Test-Path -LiteralPath (Join-Path $_ 'bin\jpackage.exe') -PathType Leaf)" ^
    "    -and (Test-Path -LiteralPath (Join-Path $_ 'release') -PathType Leaf)" ^
    "    -and (Select-String -LiteralPath (Join-Path $_ 'release') -Pattern '^JAVA_VERSION=\x22(?:25|26)(?:[.\x22+-])' -Quiet)" ^
    "} | Select-Object -First 1;" ^
    "if (-not $javaHome) { throw 'Install OpenJDK 26 and set JAVA_HOME to its installation directory. A full JDK 25 or 26 with bin\jpackage.exe is required.' };" ^
    "$criteria = 'gradle\gradle-daemon-jvm.properties';" ^
    "if (Test-Path -LiteralPath $criteria -PathType Leaf) {" ^
    "    $version = Select-String -LiteralPath $criteria -Pattern '^\s*toolchainVersion\s*[=:]\s*(\d+)\s*$' | Select-Object -First 1;" ^
    "    if ($version -and $version.Matches[0].Groups[1].Value -notin @('25', '26')) { throw ('Set toolchainVersion to 25 or 26 in ' + $criteria + '. Gradle gives this file priority over JAVA_HOME.') }" ^
    "};" ^
    "$env:JAVA_HOME = (Get-Item -LiteralPath $javaHome).FullName;" ^
    "Write-Host ('Gradle launcher JDK: ' + $env:JAVA_HOME);" ^
    "$startedAt = [DateTime]::UtcNow;" ^
    "& '.\gradlew.bat' ('-Dorg.gradle.java.home=' + $env:JAVA_HOME) ':platform:desktop:packageInnoSetup';" ^
    "if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE };" ^
    "$packageDir = 'platform\desktop\build\compose\binaries\main\innosetup';" ^
    "$installers = @(Get-ChildItem -LiteralPath $packageDir -Filter 'Heartbeat-Lab-*-setup.exe' -File | Where-Object { $_.LastWriteTimeUtc -ge $startedAt });" ^
    "if ($installers.Count -ne 1) { throw ('Expected one installer from this build in ' + $packageDir) };" ^
    "Write-Host ('Opening installer: ' + $installers[0].FullName);" ^
    "Start-Process -FilePath $installers[0].FullName"

set "result=%ERRORLEVEL%"
popd
exit /b %result%
