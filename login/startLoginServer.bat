@echo off
title Login Server Console
cd /d "%~dp0"

rem the server needs Java 21: JAVA_HOME wins over whatever java comes first in PATH
set JAVA=java
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set JAVA="%JAVA_HOME%\bin\java.exe"

:start
echo Starting L2J Login Server.
echo.

%JAVA% -Xms1g -Xmx4g -jar l2jlogin.jar

if ERRORLEVEL 2 goto restart
if ERRORLEVEL 1 goto error
goto end

:restart
echo.
echo Admin Restarted Login Server.
echo.
goto start

:error
echo.
echo Login Server terminated abnormally!
echo.

:end
echo.
echo Login Server Terminated.
echo.
pause
