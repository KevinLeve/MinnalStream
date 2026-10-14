@echo off
setlocal
where winget >nul 2>nul
if errorlevel 1 (
  echo Windows Package Manager winget was not found.
  echo Install FFmpeg with ffplay.exe from https://ffmpeg.org/download.html and ensure ffplay is on PATH.
  pause
  exit /b 1
)
echo Installing FFmpeg using winget. Follow any prompts shown by Windows.
winget install --id Gyan.FFmpeg.Shared -e --accept-package-agreements --accept-source-agreements
if errorlevel 1 (
  echo winget installation did not complete. Install FFmpeg manually from https://ffmpeg.org/download.html
  pause
  exit /b 1
)
echo FFmpeg installation requested. Close and reopen MinnalStream after setup.
pause
endlocal
