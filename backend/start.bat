@echo off
chcp 65001 >nul
title RyouStream v1.0.0 Epsilon

echo.
echo   ╔═══════════════════════════════════════╗
echo   ║   RyouStream v1.0.0 Epsilon           ║
echo   ║   Ryounime Stream Platform            ║
echo   ╚═══════════════════════════════════════╝
echo.

:: Cek Python
where python >nul 2>&1
if %errorlevel% neq 0 (
    where python3 >nul 2>&1
    if %errorlevel% neq 0 (
        echo   GAGAL: Python tidak ditemukan!
        echo   Download: https://www.python.org/downloads/
        pause
        exit /b 1
    )
    set PYTHON=python3
) else (
    set PYTHON=python
)

for /f "tokens=*" %%i in ('%PYTHON% --version 2^>^&1') do set PYVER=%%i
echo   Python : %PYVER%
echo   Dir    : %~dp0backend
echo.

:: Install dependencies
%PYTHON% -c "import requests" >nul 2>&1
if %errorlevel% neq 0 (
    echo   Install dependencies...
    %PYTHON% -m pip install -r "%~dp0backend\requirements.txt" --quiet
    echo   Dependencies siap
    echo.
)

:: Jalankan server
echo   Memulai server...
echo   ──────────────────────────────────────
echo.
cd /d "%~dp0backend"
%PYTHON% server.py
pause
