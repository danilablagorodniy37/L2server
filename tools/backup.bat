@echo off
rem Backs up what git does not hold: the database and the client's system folder.
rem Usage: tools\backup.bat [folder]   (default: S:\Programming\L2server\backups)
setlocal
set TARGET=%~1
if "%TARGET%"=="" set TARGET=S:\Programming\L2server\backups
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyy-MM-dd"') do set STAMP=%%i
if not exist "%TARGET%" mkdir "%TARGET%"

echo Dumping the database to %TARGET%\l2jdb_custom-%STAMP%.sql
mysqldump -h127.0.0.1 -P3307 -uroot -proot --single-transaction --routines --triggers l2jdb_custom > "%TARGET%\l2jdb_custom-%STAMP%.sql"
if errorlevel 1 echo Database dump failed, check that MySQL is running on 3307.

set CLIENT=S:\Programming\L2client\HighFive\system
if exist "%CLIENT%" (
	echo Copying the client system folder to %TARGET%\client-system-%STAMP%
	robocopy "%CLIENT%" "%TARGET%\client-system-%STAMP%" /E /NFL /NDL /NJH /NJS /NP >nul
)

rem keep the last week and no more: a dump a day of six megabytes, a client folder of two hundred
powershell -NoProfile -Command "Get-ChildItem '%TARGET%' -Filter 'l2jdb_custom-*.sql' | Sort-Object Name -Descending | Select-Object -Skip 7 | Remove-Item -Force"
powershell -NoProfile -Command "Get-ChildItem '%TARGET%' -Directory -Filter 'client-system-*' | Sort-Object Name -Descending | Select-Object -Skip 3 | Remove-Item -Recurse -Force"

echo Done. What is in git needs no backup: game, core, tools and the plan.
endlocal
