@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0jnp-project-starter\source\run.ps1" -Role server %*
exit /b %errorlevel%
