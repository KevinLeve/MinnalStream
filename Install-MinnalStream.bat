@echo off
setlocal
cd /d "%~dp0"
echo MinnalStream Hotspot Preview setup
call Setup-Windows.bat
if errorlevel 1 exit /b 1
start "MinnalStream Receiver" powershell.exe -NoProfile -ExecutionPolicy Bypass -STA -File "%~dp0windows\Run-Receiver.ps1"
endlocal
