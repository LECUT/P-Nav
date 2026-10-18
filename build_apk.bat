@echo off
chcp 65001 >nul
title 构建 myGNSS APK
color 0A

set "PROJECT_DIR=%~dp0"
set "BUILD_DIR=%TEMP%\myGNSS_build_%RANDOM%"

echo.
echo ╔══════════════════════════════════════════════╗
echo ║        myGNSS APK 构建工具                  ║
echo ╚══════════════════════════════════════════════╝
echo.
echo [1/4] 复制项目到临时目录...
if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
robocopy "%PROJECT_DIR%" "%BUILD_DIR%" /E /XD app\build .gradle build .cxx /NJH /NJS /NP /NS >nul
if %ERRORLEVEL% GEQ 8 (
    echo 复制失败!
    goto :error
)
echo        ✓ 复制完成

echo [2/4] 开始 Gradle 构建...
cd /d "%BUILD_DIR%"
call gradlew assembleDebug -q --no-daemon
if %ERRORLEVEL% NEQ 0 (
    echo        ✗ 构建失败!
    goto :error
)
echo        ✓ 构建成功

echo [3/4] 复制 APK 到桌面和项目根目录...
set "APK_SRC=%BUILD_DIR%\app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK_SRC%" (
    echo        ✗ APK 文件未找到!
    goto :error
)
copy /Y "%APK_SRC%" "%PROJECT_DIR%myGNSS-debug.apk" >nul
copy /Y "%APK_SRC%" "%USERPROFILE%\Desktop\myGNSS-v1.0-debug.apk" >nul
echo        ✓ APK 已复制

echo [4/4] 清理临时文件...
cd /d "%PROJECT_DIR%"
rmdir /s /q "%BUILD_DIR%" 2>nul
echo        ✓ 清理完成

echo.
echo ╔══════════════════════════════════════════════╗
echo ║         构建完成!                           ║
echo ╚══════════════════════════════════════════════╝
echo.
echo APK 位置:
echo   桌面:  %USERPROFILE%\Desktop\myGNSS-v1.0-debug.apk
echo   项目:  %PROJECT_DIR%myGNSS-debug.apk
echo.
pause
exit /b 0

:error
echo.
echo ╔══════════════════════════════════════════════╗
echo ║         构建失败!                           ║
echo ╚══════════════════════════════════════════════╝
echo.
pause
exit /b 1
