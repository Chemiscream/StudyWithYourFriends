@echo off
setlocal enabledelayedexpansion
rem StudyLog Java service: ensure MySQL is up, then start Spring Boot on 8090
set DEV=C:\Files\dev
set JAVA_HOME=%DEV%\jdk-21.0.12.1+1
set MYSQLD=%DEV%\mysql-8.0.28-winx64\bin\mysqld.exe
set MYSQL_INI=%DEV%\mysql-data\my.ini
set APPDIR=%~dp0

rem 1) start MySQL if 3306 is not listening
netstat -ano | findstr ":3306 " | findstr LISTENING >nul 2>&1
if errorlevel 1 (
  echo Starting MySQL...
  start "studylog-mysql" /min "%MYSQLD%" --defaults-file="%MYSQL_INI%" --console
  set /a tries=0
  :waitmysql
  timeout /t 1 /nobreak >nul
  netstat -ano | findstr ":3306 " | findstr LISTENING >nul 2>&1
  if not errorlevel 1 goto mysqlready
  set /a tries+=1
  if !tries! lss 30 goto waitmysql
  echo MySQL failed to start
  exit /b 1
)
:mysqlready

rem 2) start the app
echo Starting StudyLog on port 8090...
start "studylog-app" /min "%JAVA_HOME%\bin\javaw.exe" -jar "%APPDIR%target\studylog.jar"
echo Done.
endlocal
