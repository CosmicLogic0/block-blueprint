@echo off
cd /d "%~dp0"
rem First time only: make a "Block Blueprint" shortcut with the logo, here and on the Desktop.
if not exist "Block Blueprint.lnk" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$d=(Get-Location).Path; $w=New-Object -ComObject WScript.Shell; foreach($p in @([Environment]::GetFolderPath('Desktop'), $d)){ $s=$w.CreateShortcut((Join-Path $p 'Block Blueprint.lnk')); $s.TargetPath=(Join-Path $d 'start.bat'); $s.WorkingDirectory=$d; $s.IconLocation=(Join-Path $d 'assets\icon.ico'); $s.WindowStyle=7; $s.Description='Block Blueprint'; $s.Save() }" >nul 2>nul
)
where node >nul 2>nul
if errorlevel 1 (
  echo Node.js is required. Install the LTS version from https://nodejs.org then run this again.
  pause
  exit /b 1
)
if not exist node_modules\electron (
  echo First run: installing the app's components. This takes a minute...
  call npm install
  if errorlevel 1 (
    echo Install failed. Check your internet connection and try again.
    pause
    exit /b 1
  )
)
call npm start
