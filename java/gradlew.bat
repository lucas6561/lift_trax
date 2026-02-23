@echo off
setlocal

set SCRIPT_DIR=%~dp0
set PROPS_FILE=%SCRIPT_DIR%gradle\wrapper\gradle-wrapper.properties

for /f "tokens=1,* delims==" %%A in (%PROPS_FILE%) do (
  if "%%A"=="distributionUrl" set DIST_URL=%%B
)

if "%DIST_URL%"=="" (
  echo distributionUrl not found in %PROPS_FILE%
  exit /b 1
)

set DIST_URL=%DIST_URL:\:=:%
for %%F in (%DIST_URL%) do set DIST_FILE=%%~nxF
set DIST_NAME=%DIST_FILE:.zip=%
set GRADLE_HOME=%SCRIPT_DIR%.gradle-dist\%DIST_NAME%
set GRADLE_EXE=%GRADLE_HOME%\bin\gradle.bat

if not exist "%GRADLE_EXE%" (
  echo Please download %DIST_URL% and unzip it into %SCRIPT_DIR%.gradle-dist\%DIST_NAME%
  exit /b 1
)

call "%GRADLE_EXE%" %*
