@echo off
rem A snapshot of the client's system folder, to take before changing anything in it.
rem The daily backup (tools\backup.bat) keeps three days; this one is the copy you make
rem yourself before a cut, and the one you put back when the client stops starting.
rem
rem   tools\client\backup.bat              a snapshot, named by the minute
rem   tools\client\backup.bat list         the snapshots there are
rem   tools\client\backup.bat restore      put the newest one back
rem   tools\client\backup.bat restore client-before-2026-09-20_1530    put that one back
setlocal enabledelayedexpansion
if "%L2CLIENT%"=="" set L2CLIENT=S:\Programming\L2client\HighFive
if "%L2BACKUPS%"=="" set L2BACKUPS=S:\Programming\L2server\backups
set SYSTEM=%L2CLIENT%\system

if not exist "%SYSTEM%" (
	echo No client system folder at %SYSTEM%.
	echo Set L2CLIENT to where the client is and run this again.
	exit /b 1
)
if not exist "%L2BACKUPS%" mkdir "%L2BACKUPS%"

if /i "%~1"=="list" goto :list
if /i "%~1"=="restore" goto :restore
goto :snapshot

:list
echo Snapshots in %L2BACKUPS%:
for /d %%d in ("%L2BACKUPS%\client-before-*") do echo   %%~nxd
exit /b 0

:restore
set NAME=%~2
if "%NAME%"=="" for /f "delims=" %%d in ('dir /b /ad /o-n "%L2BACKUPS%\client-before-*" 2^>nul') do if "!NAME!"=="" set NAME=%%d
if "!NAME!"=="" (
	echo No snapshot to put back. Make one first: tools\client\backup.bat
	exit /b 1
)
if not exist "%L2BACKUPS%\!NAME!" (
	echo No snapshot called !NAME! in %L2BACKUPS%.
	exit /b 1
)
echo Putting !NAME! back into %SYSTEM%
robocopy "%L2BACKUPS%\!NAME!" "%SYSTEM%" /E /NFL /NDL /NJH /NJS /NP >nul
if errorlevel 8 (
	echo The copy failed. The client may be running - close it and try again.
	exit /b 1
)
echo Done. Start the client once to see that it opens.
exit /b 0

:snapshot
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd_HHmm"') do set STAMP=%%i
set SNAPSHOT=%L2BACKUPS%\client-before-!STAMP!
echo Copying %SYSTEM% to !SNAPSHOT!
robocopy "%SYSTEM%" "!SNAPSHOT!" /E /NFL /NDL /NJH /NJS /NP >nul
if errorlevel 8 (
	echo The copy failed.
	exit /b 1
)
echo Done. Put it back with: tools\client\backup.bat restore client-before-!STAMP!
exit /b 0
