@echo off
chcp 65001 >nul
title P-Nav LogCat

set LOG_DIR=%~dp0logs
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value') do set datetime=%%I
set LOG_FILE=%LOG_DIR%\pnax_%datetime:~0,8%_%datetime:~8,6%.txt

echo ============================================
echo   P-Nav Log Tool
echo ============================================
echo Log file: %LOG_FILE%
echo Press Ctrl+C to stop
echo ============================================

adb logcat -c
adb logcat -v threadtime AndroidRuntime:E System.err:W myapplication:D CrashHandler:D DEBUG_MAIN:D SPP:D DGPS:D RTK:D FtpHelper:D *:S >> "%LOG_FILE%"

pause
