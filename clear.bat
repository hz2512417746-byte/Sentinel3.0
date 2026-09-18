@echo off
echo Clearing all logs...
echo.
echo [1/2] MySQL...
mysql -uroot -p%MYSQL_PASSWORD% -e "TRUNCATE dpi_fraud.log_events; TRUNCATE dpi_fraud.alerts; TRUNCATE dpi_fraud.block_records;" 2>nul
echo MySQL cleared.
echo.
echo [2/2] Redis...
redis-cli FLUSHALL 2>nul
echo Redis cleared.
echo.
echo Done. Run stop.bat then start.bat to restart fresh.
pause
