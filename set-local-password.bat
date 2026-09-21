@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat --quiet installDist
if errorlevel 1 exit /b %errorlevel%
if defined JAVA_HOME (
  "%JAVA_HOME%\bin\java.exe" "-Dlifttrax.config=config/lifttrax-local.properties" -cp "build/install/lift-trax-java/lib/*" com.lifttrax.cli.SetLocalPasswordCli %*
) else (
  java "-Dlifttrax.config=config/lifttrax-local.properties" -cp "build/install/lift-trax-java/lib/*" com.lifttrax.cli.SetLocalPasswordCli %*
)
