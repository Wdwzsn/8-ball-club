@echo off
setlocal enabledelayedexpansion
set "ROOT=%~dp0"
set "JAR=%ROOT%dist\8-ball-club.jar"
if not exist "%JAR%" (
  echo [ERROR] dist\8-ball-club.jar not found.
  echo Please run build.ps1 first.
  pause
  exit /b 1
)

set "JAVA="
if exist "%ROOT%.jdk-home" (
  set /p JDK=<"%ROOT%.jdk-home"
  if exist "!JDK!\bin\javaw.exe" set "JAVA=!JDK!\bin\javaw.exe"
)
if not defined JAVA if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JAVA=%JAVA_HOME%\bin\javaw.exe"
if not defined JAVA for %%J in (javaw.exe) do if not "%%~$PATH:J"=="" set "JAVA=%%~$PATH:J"
if not defined JAVA (
  for /d %%D in ("C:\Program Files\Java\*") do if exist "%%D\bin\javaw.exe" set "JAVA=%%D\bin\javaw.exe"
)
if not defined JAVA (
  for /d %%D in ("C:\Program Files\Eclipse Adoptium\*") do if exist "%%D\bin\javaw.exe" set "JAVA=%%D\bin\javaw.exe"
)
if not defined JAVA (
  for /d %%D in ("D:\*\jbr") do if exist "%%D\bin\javaw.exe" set "JAVA=%%D\bin\javaw.exe"
)
if not defined JAVA (
  echo [ERROR] Java runtime not found. Install JDK 17+ from https://adoptium.net
  pause
  exit /b 1
)

start "" "%JAVA%" -jar "%JAR%" %*
exit /b 0
