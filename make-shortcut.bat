@echo off
rem Creates a "Block Blueprint" shortcut with the logo on your Desktop and in this folder.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
 "$d=(Get-Location).Path; $w=New-Object -ComObject WScript.Shell; foreach($p in @([Environment]::GetFolderPath('Desktop'), $d)){ $s=$w.CreateShortcut((Join-Path $p 'Block Blueprint.lnk')); $s.TargetPath=(Join-Path $d 'start.bat'); $s.WorkingDirectory=$d; $s.IconLocation=(Join-Path $d 'assets\icon.ico'); $s.WindowStyle=7; $s.Description='Block Blueprint'; $s.Save() }"
echo.
echo Done! Use the "Block Blueprint" shortcut on your Desktop (or in this folder) to open the app.
pause
