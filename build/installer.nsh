; Make sure no copy of Block Blueprint is still running before files are replaced
; (a leftover copy made the installer fail with "cannot be closed" / "failed to uninstall old application files").
!macro customCheckAppRunning
  nsExec::Exec 'taskkill /F /T /IM "Block Blueprint.exe"'
  Sleep 1500
!macroend
