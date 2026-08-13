@echo off
setlocal
title Dragon Mine Z: Multiverse - Test Client

pushd "%~dp0" >nul 2>&1
if errorlevel 1 (
    echo Could not open the Dragon Mine Z: Multiverse project folder.
    echo.
    pause
    exit /b 1
)

if not exist "gradlew.bat" (
    echo The Gradle launcher is missing from this project folder.
    echo.
    pause
    popd
    exit /b 1
)

echo ============================================================
echo       Dragon Mine Z: Multiverse - Minecraft Test Client
echo ============================================================
echo.
echo Starting Minecraft 1.20.1 with Forge, Dragon Mine Z,
echo its required dependencies, and this addon loaded.
echo.
echo The first launch can take several minutes while the test
echo environment is prepared. Keep this window open while playing.
echo.

call "gradlew.bat" runClient --no-daemon
set "DMZ_LAUNCH_EXIT=%ERRORLEVEL%"

if not "%DMZ_LAUNCH_EXIT%"=="0" (
    echo.
    echo ============================================================
    echo Minecraft did not start correctly.
    echo Check this log for details:
    echo "%~dp0run\logs\latest.log"
    echo ============================================================
    echo.
    pause
)

popd
exit /b %DMZ_LAUNCH_EXIT%
