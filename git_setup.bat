@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

echo ================================================
echo  GitHub Setup ^(Run only once^)
echo ================================================
echo.

:: Git PATH
set "PATH=%PATH%;%ProgramFiles%\Git\bin;%ProgramFiles%\Git\cmd"

set "SOURCE_DIR=K:\Java25\@AnalogClockSwing\src_KootPanKing"
set "GIT_USER=GarpsuKim"
set "REPO=github.com/GarpsuKim/KootPanKing"
set "GIT_PASS=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"


:: ── Check git installation ─────────────────────────
where git > nul 2>&1
if errorlevel 1 (
    echo [ERROR] Git is not installed.
    echo         Check path: C:\Program Files\Git\bin
    pause & exit /b 1
)
echo [OK] Git version:
git --version
echo.


:: ── Step 1: Register safe.directory ───────────────
echo [Step 1] Registering safe.directory...
git config --global --add safe.directory "K:/Java25/@AnalogClockSwing/src_KootPanKing"
if errorlevel 1 (
    echo [ERROR] Failed to register safe.directory.
    pause & exit /b 1
)
echo         Done.
echo.

:: ── Step 2: Move to source folder ─────────────────
echo [Step 2] Moving to source folder...
cd /d "%SOURCE_DIR%"
if errorlevel 1 (
    echo [ERROR] Folder not found: %SOURCE_DIR%
    pause & exit /b 1
)
echo         Current path: %CD%
echo.

:: ── Step 3: .gitignore (token.txt 보호) ───────────
echo [Step 3] Creating .gitignore...
if not exist ".gitignore" (
    echo token.txt> .gitignore
    echo         .gitignore created.
) else (
    findstr /x /c:"token.txt" .gitignore > nul 2>&1
    if errorlevel 1 (
        echo token.txt>> .gitignore
        echo         token.txt added to .gitignore.
    ) else (
        echo         token.txt already in .gitignore.
    )
)
echo.

:: ── Step 4: git init (이미 존재하면 skip) ─────────
echo [Step 4] Initializing git...
if exist ".git" (
    echo         .git already exists, skipping init.
) else (
    git init
    if errorlevel 1 (
        echo [ERROR] git init failed.
        pause & exit /b 1
    )
)
echo.

:: ── Step 5: Register remote ───────────────────────
echo [Step 5] Registering remote origin...
git remote remove origin 2>nul
git remote add origin "https://%GIT_USER%:!GIT_PASS!@%REPO%"
if errorlevel 1 (
    echo [ERROR] Failed to register remote. Check token.txt.
    pause & exit /b 1
)
echo         Remote registered.
echo.
:: ── Step 6: Set main branch ──────────────────────
echo [Step 6] Setting main branch...
git checkout -B main 2>nul
git fetch origin
git branch --set-upstream-to=origin/main main 2>nul
echo         Done. Run git_push.bat to upload.
echo.

echo ================================================
echo  Setup complete^^!  Now run git_push.bat
echo ================================================
pause
endlocal
