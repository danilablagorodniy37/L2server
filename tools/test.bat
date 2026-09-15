@echo off
setlocal
title Tests

rem Runs the datapack, tools and database tests (see tests\README.md).
rem   tools\test.bat            Python tests
rem   tools\test.bat --core     also core JUnit tests
rem   tools\test.bat --server   also the boot test (servers must be stopped)

set ROOT=%~dp0..
set PYTEST_ARGS=
set CORE=
for %%A in (%*) do (
	if "%%A"=="--core" set CORE=1
	if "%%A"=="--server" set PYTEST_ARGS=--run-server
)

pushd "%ROOT%"
python -m pytest %PYTEST_ARGS%
set RESULT=%ERRORLEVEL%
popd

if defined CORE (
	pushd "%ROOT%\core"
	call "%ROOT%\core\mvnw.cmd" -B -q test
	if errorlevel 1 set RESULT=1
	popd
)

exit /b %RESULT%
