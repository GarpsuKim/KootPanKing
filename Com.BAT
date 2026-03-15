@echo off

if "%1" =="" (
    echo [ERRO] source ????
    echo 사용법: com.bat  source
    goto :end
)

set CLASS_NAME=%1
set ROOT_DIR=K:\Java25\@AnalogClockSwing
set SOURCE_DIR=%ROOT_DIR%\src_KootPanKing
set SOURCE_FILE=%SOURCE_DIR%\%CLASS_NAME%.java
set ERROR_FILE=%ROOT_DIR%\ERROR\ERROR_%CLASS_NAME%.txt
set OUT=%ROOT_DIR%\class

echo SOURCE: %SOURCE_FILE%

type nul > "%ERROR_FILE%"

javac -d %OUT%  -Xlint:deprecation ^
  --class-path "%OUT%;K:\java25\Lib\*" ^
  -encoding UTF-8 ^
  -sourcepath "%SOURCE_DIR%" ^
  "%SOURCE_FILE%" 2> "%ERROR_FILE%"

if %errorlevel% neq 0 (
    echo [ERROR] COMPILE ERROR
    echo ==========================================
    type %ERROR_FILE%
    echo ==========================================
    echo.
	pause
    goto :end
)

for %%A in (%ERROR_FILE%) do set FILE_SIZE=%%~zA
if %FILE_SIZE% gtr 0 (
    type %ERROR_FILE%
    echo ■■■■■■■■■■■ [WARNING] COMPILE WARNING
	pause
    echo.
)

echo [ok] COMPILE SUCCESSFUL!

:end
