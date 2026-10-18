@echo off
echo BEFORE:
dir "C:\Users\25961\Desktop\myGNSS\myGNSS-master\myGNSS\app\build\outputs\apk\debug\"
echo.
echo Attempting delete...
del /f /q "C:\Users\25961\Desktop\myGNSS\myGNSS-master\myGNSS\app\build\outputs\apk\debug\app-debug.apk"
echo exit code: %ERRORLEVEL%
echo.
echo AFTER:
dir "C:\Users\25961\Desktop\myGNSS\myGNSS-master\myGNSS\app\build\outputs\apk\debug\"
