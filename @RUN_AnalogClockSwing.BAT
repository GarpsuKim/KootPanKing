@echo off
chcp 65001 > nul

K:
cd \Java25\@AnalogClockSwing

REM ── 경로 설정 ──────────────────────────────────────────────────
set ROOT_DIR=K:\Java25\@AnalogClockSwing
set OUT=%ROOT_DIR%\class
set LIB=K:\Java25\Lib
set JSON=json-simple-1.1.1.jar

REM ── JAR 존재 확인 ──────────────────────────────────────────────
echo [STEP 1] JAR 존재 확인 중...
set MISSING=0

if not exist "%LIB%\jackson-core-2.21.1.jar"           ( echo [MISS] jackson-core-2.21.1.jar           & set MISSING=1 )
if not exist "%LIB%\jetty-server-9.4.54.v20240208.jar" ( echo [MISS] jetty-server-9.4.54.v20240208.jar & set MISSING=1 )
if not exist "%LIB%\guava-33.4.0-jre.jar"              ( echo [MISS] guava-33.4.0-jre.jar              & set MISSING=1 )
if not exist "%LIB%\%JSON%"                            ( echo [MISS] %JSON%                            & set MISSING=1 )

if %MISSING%==1 (
    echo.
    echo [ERROR] 위 JAR 파일 없음. %LIB% 폴더 확인하세요.
    pause
    goto :end
)
echo [STEP 1] JAR 확인 완료

REM ── Fat JAR 빌드 (모든 라이브러리를 하나의 JAR로 합침) ─────────
echo [STEP 2] Fat JAR 빌드 중...

REM 임시 폴더 초기화
if exist "%ROOT_DIR%\fat_tmp" rmdir /S /Q "%ROOT_DIR%\fat_tmp"
mkdir "%ROOT_DIR%\fat_tmp"

REM 라이브러리 JAR 들을 임시 폴더에 풀기 (순서 중요: 앱 클래스가 마지막에 덮어씀)
cd "%ROOT_DIR%\fat_tmp"
jar xf "%LIB%\jackson-core-2.21.1.jar"
jar xf "%LIB%\jetty-server-9.4.54.v20240208.jar"
jar xf "%LIB%\guava-33.4.0-jre.jar"
jar xf "%LIB%\%JSON%"

REM 앱 클래스 복사 (라이브러리보다 나중에 복사 → 충돌 시 앱 클래스 우선)
xcopy /E /Y /Q "%OUT%\*" "%ROOT_DIR%\fat_tmp\"

REM Fat JAR 생성 (Main-Class: AnalogClockSwing)
cd "%ROOT_DIR%"
if exist AnalogClockSwing.jar del AnalogClockSwing.jar
jar cfe AnalogClockSwing.jar AnalogClockSwing -C fat_tmp .

REM 임시 폴더 정리
rmdir /S /Q "%ROOT_DIR%\fat_tmp"

echo [STEP 2] Fat JAR 빌드 완료

REM ── 실행 (Fat JAR 단독 실행 - 외부 -cp 불필요) ─────────────────
echo [STEP 3] 실행 중...
start "" javaw -jar "%ROOT_DIR%\AnalogClockSwing.jar"

echo.
echo [완료] AnalogClockSwing 시작됨
echo.

:end
