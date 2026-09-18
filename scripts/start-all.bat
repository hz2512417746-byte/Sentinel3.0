@echo off
echo ============================================
echo  DPI 实时反诈预警系统 - 全栈启动
echo ============================================

REM 1. Kafka (already running assumed)
echo [1/4] Kafka...
if not "%KAFKA_HOME%"=="" (
    start "Kafka" "%KAFKA_HOME%\bin\windows\kafka-server-start.bat" "%KAFKA_HOME%\config\kraft\server.properties"
)

REM 2. Spring Boot
echo [2/4] Spring Boot Backend...
start "Backend" cmd /c "cd /d C:\Users\25124\Desktop\校赛-竞赛项目\backend && mvn spring-boot:run"

REM 3. Log Simulator
echo [3/4] Log Simulator...
start "Simulator" cmd /c "python C:\Users\25124\Desktop\校赛-竞赛项目\scripts\dpi_simulator.py"

REM 4. Vue3 Frontend
echo [4/4] Vue3 Frontend...
start "Frontend" cmd /c "cd /d C:\Users\25124\Desktop\校赛-竞赛项目\frontend && npm run dev"

echo.
echo ============================================
echo  全部启动!
echo  Frontend: http://localhost:5173
echo  Backend:  http://localhost:8080
echo  Kafka:    localhost:9092
echo ============================================
pause
