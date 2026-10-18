@echo off
tasklist /FI "IMAGENAME eq explorer.exe" 2>nul
echo Trying handle64...
handle64 -a "C:\Users\25961\Desktop\myGNSS\myGNSS-master\myGNSS\app\build\outputs\apk\debug\app-debug.apk" 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo handle64 not available, trying alternate approach
    echo Checking if file is locked by restarting in safe mode...
)
pause
