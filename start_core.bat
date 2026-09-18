@echo off
REM Sentinel 3.0 start core logic (run by scheduled task as admin, no window)

net start MySQL80 >nul 2>&1
net start Redis >nul 2>&1

REM Start ZooKeeper
cd /d C:\kafka_2.13-3.9.0
powershell -NoProfile -Command "Start-Process cmd -ArgumentList '/c','bin\windows\zookeeper-server-start.bat config\zookeeper.properties' -WorkingDirectory 'C:\kafka_2.13-3.9.0' -WindowStyle Hidden"
call :wait_port 2181 40

REM Clean stale Kafka broker node in ZK
(
    echo deleteall /brokers/ids
    echo quit
) | bin\windows\zookeeper-shell.bat localhost:2181 >nul 2>&1

REM Start Kafka
powershell -NoProfile -Command "Start-Process cmd -ArgumentList '/c','bin\windows\kafka-server-start.bat config\server.properties' -WorkingDirectory 'C:\kafka_2.13-3.9.0' -WindowStyle Hidden"
call :wait_port 9092 60

REM Start Flink job
powershell -NoProfile -Command "Start-Process java -ArgumentList '--add-opens','java.base/java.util=ALL-UNNAMED','--add-opens','java.base/java.lang=ALL-UNNAMED','-cp','target\dpi-flink-job-1.0.0-shaded.jar','com.antifraud.flink.DpiFraudDetectionJob' -WorkingDirectory '%~dp0flink-job' -WindowStyle Hidden"

REM Start Backend
powershell -NoProfile -Command "Start-Process java -ArgumentList '-jar','target\dpi-fraud-backend-1.0.0.jar' -WorkingDirectory '%~dp0backend' -WindowStyle Hidden"
call :wait_port 8080 60

REM Start Frontend
powershell -NoProfile -Command "Start-Process cmd -ArgumentList '/c','npx vite --host 0.0.0.0' -WorkingDirectory '%~dp0frontend' -WindowStyle Hidden"
call :wait_port 5173 40

REM Start Simulator
powershell -NoProfile -Command "Start-Process java -ArgumentList '-cp','target\dpi-simulator-1.0.0.jar','com.antifraud.simulator.Main' -WorkingDirectory '%~dp0simulator' -WindowStyle Hidden"

exit /b

REM ==== Wait for port: %1=port %2=timeout seconds ====
:wait_port
set /a _cnt=0
:wait_port_loop
netstat -ano | find ":%1 " | find "LISTENING" >nul
if %errorlevel%==0 exit /b 0
set /a _cnt+=1
if %_cnt% GEQ %2 goto wait_port_timeout
ping -n 2 127.0.0.1 >nul
goto wait_port_loop
:wait_port_timeout
exit /b 1
