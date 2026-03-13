@echo off
chcp 65001 > nul
setlocal EnableDelayedExpansion

echo ================================================
echo  GitHub Auto Upload
echo ================================================
echo.

:: Git PATH
set "PATH=%PATH%;%ProgramFiles%\Git\bin;%ProgramFiles%\Git\cmd"

:: ── Settings ───────────────────────────────────────
set "SOURCE_DIR=K:\Java25\@AnalogClockSwing\src_KootPanKing"
set "GIT_USER=GarpsuKim"
set "GIT_PASS=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"
set "REPO=github.com/GarpsuKim/KootPanKing"

:: ── Check git installation ─────────────────────────
where git > nul 2>&1
if errorlevel 1 (
    echo [ERROR] Git is not installed.
    echo         Run git_setup.bat first.
    pause & exit /b 1
)


:: ── Move to source folder ──────────────────────────
cd /d "%SOURCE_DIR%"
if errorlevel 1 (
    echo [ERROR] Folder not found:
    echo         %SOURCE_DIR%
    pause & exit /b 1
)

:: ── Check .git exists ──────────────────────────────
if not exist ".git" (
    echo [ERROR] Git repository not initialized.
    echo         Run git_setup.bat first.
    pause & exit /b 1
)

:: ── Update remote URL ──────────────────────────────
git remote set-url origin "https://%GIT_USER%:!GIT_PASS!@%REPO%" 2>nul
if errorlevel 1 (
    git remote add origin "https://%GIT_USER%:!GIT_PASS!@%REPO%"
)

:: ── Commit message: date + time (wmic 대신 PowerShell 사용) ──
for /f "usebackq delims=" %%a in (`powershell -NoProfile -Command "Get-Date -Format 'yyyy-MM-dd HH:mm'"`) do set "COMMIT_MSG=Update %%a"

:: ── [1/3] Stage ────────────────────────────────────
echo [1/3] Staging changed files...
git add -A
if errorlevel 1 (
    echo [ERROR] git add failed.
    pause & exit /b 1
)

:: ── [2/3] Commit ───────────────────────────────────
echo [2/3] Committing: !COMMIT_MSG!
git diff --cached --quiet
if not errorlevel 1 (
    echo.
    echo [INFO] No changes to commit.
    pause & exit /b 0
)
git commit -m "!COMMIT_MSG!"
if errorlevel 1 (
    echo [ERROR] git commit failed.
    pause & exit /b 1
)

:: ── [3/3] Push ─────────────────────────────────────
echo [3/3] Pushing to GitHub...
git push -u origin main
if errorlevel 1 (
    echo.
    echo [ERROR] Push failed. Possible causes:
    echo         1. Token expired or invalid ^(token.txt^)
    echo         2. Network connection problem
    echo         3. Try running git_setup.bat again.
    pause & exit /b 1
)

echo.
echo ================================================
echo  Upload complete^^!
echo  Commit : !COMMIT_MSG!
echo  Repo   : https://github.com/GarpsuKim/KootPanKing
echo ================================================
pause
endlocal
