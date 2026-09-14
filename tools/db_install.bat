@echo off
setlocal EnableDelayedExpansion
title Install custom build database

rem ---------------------------------------------------------------------------
rem Creates a fresh database for the custom build from login\sql and game\sql.
rem Refuses to touch an existing database: drop it yourself if you really
rem want a reinstall.
rem
rem Optional: copy accounts (logins, passwords, access levels) from another
rem database, e.g. the working High Five one:
rem   db_install.bat l2jdb
rem ---------------------------------------------------------------------------

set MYSQL="C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
set DB_HOST=127.0.0.1
set DB_PORT=3307
set DB_USER=root
set DB_PASS=root
set DB_NAME=l2jdb_custom
set COPY_ACCOUNTS_FROM=%~1

set ROOT=%~dp0..
set MYSQL_CMD=%MYSQL% -h%DB_HOST% -P%DB_PORT% -u%DB_USER% -p%DB_PASS%

set CHECK_FILE=%TEMP%\l2_db_install_check.txt
%MYSQL_CMD% -N -e "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='%DB_NAME%'" > "%CHECK_FILE%" 2>nul
set /p EXISTS=<"%CHECK_FILE%"
del "%CHECK_FILE%" 2>nul
if not defined EXISTS (
	echo Cannot connect to MySQL on %DB_HOST%:%DB_PORT%.
	exit /b 1
)
if not "%EXISTS%"=="0" (
	echo Database %DB_NAME% already exists, nothing done.
	exit /b 1
)

echo Creating database %DB_NAME%...
%MYSQL_CMD% -e "CREATE DATABASE %DB_NAME% DEFAULT CHARACTER SET utf8mb4" 2>nul || exit /b 1

for %%d in ("%ROOT%\login\sql" "%ROOT%\game\sql" "%ROOT%\game\sql\custom") do (
	for %%f in ("%%~d\*.sql") do (
		echo   %%~nxf
		%MYSQL_CMD% %DB_NAME% < "%%f" 2>nul
		if errorlevel 1 (
			echo Failed on %%f
			exit /b 1
		)
	)
)

if not "%COPY_ACCOUNTS_FROM%"=="" (
	echo Copying accounts from %COPY_ACCOUNTS_FROM%...
	%MYSQL_CMD% -e "INSERT INTO %DB_NAME%.accounts SELECT * FROM %COPY_ACCOUNTS_FROM%.accounts" 2>nul || exit /b 1
)

echo Done.
endlocal
