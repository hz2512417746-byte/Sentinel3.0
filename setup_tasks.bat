@echo off
REM Create two highest-privilege scheduled tasks (run once, then no UAC prompt)

schtasks /create /tn "SentinelStart" /tr "%~dp0start_core.bat" /sc once /st 00:00 /rl highest /f
schtasks /create /tn "SentinelStop" /tr "%~dp0stop_core.bat" /sc once /st 00:00 /rl highest /f

exit
