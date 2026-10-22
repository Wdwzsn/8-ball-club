@echo off
setlocal enabledelayedexpansion
set "ROOT=%~dp0"
set "JAR=%ROOT%dist\8-ball-club.jar"
if not exist "%JAR%" (
  echo [ERROR] dist\8-ball-club.jar not found. Run build.ps1 first.
  pause
  exit /b 1
)
set "JAVA="
if exist "%ROOT%.jdk-home" (
  set /p JDK=<"%ROOT%.jdk-home"
  if exist "!JDK!\bin\java.exe" set "JAVA=!JDK!\bin\java.exe"
)
if not defined JAVA if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if not defined JAVA for %%J in (java.exe) do if not "%%~$PATH:J"=="" set "JAVA=%%~$PATH:J"
if not defined JAVA (
  for /d %%D in ("D:\*\jbr") do if exist "%%D\bin\java.exe" set "JAVA=%%D\bin\java.exe"
)
if not defined JAVA (
  echo [ERROR] Java not found.
  pause
  exit /b 1
)
"%JAVA%" -Dfile.encoding=UTF-8 -cp "%JAR%" com.pool8.SelfTest
echo.
pause
