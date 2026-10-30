@echo off
setlocal
pwsh -NoProfile -File "%~dp0scripts\https.ps1" %*
exit /b %errorlevel%
