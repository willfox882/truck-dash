@echo off
rem Truck Dash Sandbox launcher (Windows). Needs Java 17 or newer.
where java >nul 2>nul
if errorlevel 1 (
  echo Java 17+ was not found. Install "Temurin 17 JRE" from https://adoptium.net and run this again.
  pause
  exit /b 1
)
java -jar "%~dp0truck-dash-sandbox.jar"
if errorlevel 1 pause
