@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1" -Role peer %*
exit /b %errorlevel%
