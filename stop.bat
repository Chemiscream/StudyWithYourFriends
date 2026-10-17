@echo off
rem Stop StudyLog Java app (8090). MySQL is left running and shared.
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8090 " ^| findstr LISTENING') do (
  echo Killing PID %%p
  taskkill /PID %%p /F >nul 2>&1
)
echo Done.
