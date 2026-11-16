@echo off
rem Truck Dash sandbox launcher (Windows). Needs Java 17 or newer.
cd /d "%~dp0"
if not exist "truck-dash-sandbox.jar" (
  echo.
  echo  Cannot find truck-dash-sandbox.jar next to this file.
  echo  The GitHub "Download ZIP" contains only source code, not the built program.
  echo  Please use the ready-made zip (truck-dash-sandbox-windows.zip) instead:
  echo    1. Unzip it anywhere.
  echo    2. Open the unzipped folder and double-click run-sandbox.bat.
  echo.
  pause
  exit /b 1
)
where java >nul 2>nul
if errorlevel 1 (
  echo.
  echo  Java was not found. Install "Temurin 17 (or newer) JRE" from https://adoptium.net
  echo  and tick "Add to PATH" and "Set JAVA_HOME" in the installer, then run this again.
  echo.
  pause
  exit /b 1
)
java -jar "truck-dash-sandbox.jar"
if errorlevel 1 pause
