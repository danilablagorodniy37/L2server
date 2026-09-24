@echo off
title Game Server Console
cd /d "%~dp0"

rem the server needs Java 21: JAVA_HOME wins over whatever java comes first in PATH
set JAVA=java
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set JAVA="%JAVA_HOME%\bin\java.exe"

:start
echo Starting L2J Game Server...
echo.

%JAVA% -Xms4g -Xmx12g -cp "l2jserver.jar;libs/*" com.l2jserver.gameserver.GameServer

if ERRORLEVEL 2 goto restart
if ERRORLEVEL 1 goto error
goto end

:restart
echo.
echo Admin Restarted Game Server.
echo.
goto start

:error
echo.
echo Game Server Terminated Abnormally!
echo.

:end
echo.
echo Game Server Terminated.
pause
