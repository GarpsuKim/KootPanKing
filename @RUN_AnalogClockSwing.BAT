@echo off
chcp 65001 > nul

K:
cd \Java25\@AnalogClockSwing

REM ── 경로 설정 ──────────────────────────────────────────────────
set ROOT_DIR=K:\Java25\@AnalogClockSwing
set OUT=%ROOT_DIR%\class
set LIB=K:\Java25\Lib_

REM ── JAR 존재 확인 ──────────────────────────────────────────────
echo [STEP 1] JAR 존재 확인 중...
set MISSING=0

if not exist "%LIB%\jackson-core-2.21.1.jar"           ( echo [MISS] jackson-core-2.21.1.jar           & set MISSING=1 )
if not exist "%LIB%\jetty-server-9.4.54.v20240208.jar" ( echo [MISS] jetty-server-9.4.54.v20240208.jar & set MISSING=1 )
if not exist "%LIB%\guava-33.4.0-jre.jar"              ( echo [MISS] guava-33.4.0-jre.jar              & set MISSING=1 )

if %MISSING%==1 (
    echo.
    echo [ERROR] 위 JAR 파일 없음. %LIB% 폴더 확인하세요.
    pause
    goto :end
)
echo [STEP 1] JAR 확인 완료

REM ── 클래스패스 (Google Calendar JAR 완전 제거) ─────────────────
rem GoogleCalendarService 가 순수 Java HTTP 로 재작성되어
rem google-api-client / google-http-client / opencensus 전부 불필요
set CP=%LIB%\jackson-core-2.21.1.jar
set CP=%CP%;%LIB%\jetty-server-9.4.54.v20240208.jar
set CP=%CP%;%LIB%\guava-33.4.0-jre.jar

REM ── JAR 빌드 ───────────────────────────────────────────────────
echo [STEP 2] JAR 빌드 중...
if exist AnalogClockSwing.jar del AnalogClockSwing.jar
jar cfe AnalogClockSwing.jar AnalogClockSwing -C class .
echo [STEP 2] 빌드 완료

REM ── 실행 ───────────────────────────────────────────────────────
echo [STEP 3] 실행 중...
rem start "" javaw -cp "%ROOT_DIR%\AnalogClockSwing.jar;%CP%" AnalogClockSwing
start "" java -cp "%ROOT_DIR%\AnalogClockSwing.jar;%CP%" AnalogClockSwing

echo.
echo [완료] AnalogClockSwing 시작됨
echo.

:end
