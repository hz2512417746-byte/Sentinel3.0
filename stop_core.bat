@echo off
REM Sentinel 3.0 stop core logic (run by scheduled task as admin, no window)

taskkill /F /IM java.exe 2>nul
for /f "tokens=5" %%a in ('netstat -ano ^| find ":5173" ^| find "LISTENING"') do taskkill /F /PID %%a 2>nul
net stop MySQL80
net stop Redis

exit /b
