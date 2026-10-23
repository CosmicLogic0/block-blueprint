@echo off
setlocal
cd /d "%~dp0"
title Build Blueprint - building the mod

where java >nul 2>nul
if errorlevel 1 (
  echo Java 25 JDK is needed to build the mod. Installing it now with Windows' built-in installer...
  winget install --id EclipseAdoptium.Temurin.25.JDK -e --accept-package-agreements --accept-source-agreements
  echo.
  echo If that finished, CLOSE this window and double-click build-mod.bat again.
  pause
  exit /b 1
)

set DBG=%USERPROFILE%\bb-debug
if not exist "%DBG%" mkdir "%DBG%"
set LOOMS=1.17-SNAPSHOT 1.18-SNAPSHOT 1.16-SNAPSHOT
set OK=
for %%L in (%LOOMS%) do (
  if not defined OK (
    echo Trying build tool version %%L ...
    call gradlew.bat build -x test -Ploom_version=%%L > "%DBG%\build-log.txt" 2>&1
    if not errorlevel 1 set OK=1
    type "%DBG%\build-log.txt"
  )
)
if not defined OK (
  echo.
  echo The build failed. Saving a copy of the details so Claude can read them - please wait a moment...
  robocopy "%USERPROFILE%\.gradle\caches\modules-2\files-2.1" "%DBG%\libs" *.jar /S /XF *natives* *sources* /NFL /NDL /NJH /NJS /NP >nul
  echo.
  echo Saved. Just tell Claude: "the build failed, details are saved" - no need to copy any text.
  pause
  exit /b 1
)

set JAR=build\libs\buildblueprint-1.0.0.jar
if not exist "..\Mod" mkdir "..\Mod"
del /q "..\Mod\buildblueprint-*.jar" >nul 2>nul
copy /y "%JAR%" "..\Mod\" >nul
set MODS=%USERPROFILE%\curseforge\minecraft\Instances\My mod\mods
if exist "%MODS%" (
  del /q "%MODS%\buildblueprint-*.jar" >nul 2>nul
  copy /y "%JAR%" "%MODS%\" >nul
  echo.
  echo Done! The mod was copied into your CurseForge profile "My mod":
  echo   %MODS%
  echo Open that profile in CurseForge and press Play.
) else (
  echo.
  echo Done! Your mod file is %JAR%
  echo Copy it into the mods folder of your Minecraft profile.
  explorer build\libs
)
pause
