@echo off
setlocal
title Build game server core

rem Builds core\ with the Maven wrapper and installs l2jserver.jar into game\.
rem Dependency jars in game\libs are not touched.

set ROOT=%~dp0..

pushd "%ROOT%\core"
call mvnw.cmd -B -q -DskipTests package
if errorlevel 1 (
	popd
	echo Build failed.
	exit /b 1
)
popd

copy /y "%ROOT%\core\target\l2jserver.jar" "%ROOT%\game\l2jserver.jar" >nul || exit /b 1
echo Installed core\target\l2jserver.jar into game\
endlocal
