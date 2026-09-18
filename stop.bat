@echo off
REM Sentinel 3.0 stop entry: trigger scheduled task when not admin (no UAC), else run core directly

net session >nul 2>&1
if %errorlevel% neq 0 (
    schtasks /run /tn "SentinelStop"
    exit
)

call "%~dp0stop_core.bat"
exit
