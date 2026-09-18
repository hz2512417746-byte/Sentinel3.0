@echo off
REM Sentinel 3.0 start entry: trigger scheduled task when not admin (no UAC), else run core directly

net session >nul 2>&1
if %errorlevel% neq 0 (
    schtasks /run /tn "SentinelStart"
    exit
)

call "%~dp0start_core.bat"
exit
