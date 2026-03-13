@echo off
chcp 65001 > nul
echo ============================================
echo  AnalogClock EXE 빌드 스크립트 (jpackage)
echo ============================================

:: ── 0. JDK 확인 ─────────────────────────────
javac -version >nul 2>&1
if errorlevel 1 (
    echo [오류] javac를 찾을 수 없습니다.
    echo JDK 17 이상을 설치하고 PATH에 추가하세요.
    echo 다운로드: https://adoptium.net
    pause & exit /b 1
)
jpackage --version >nul 2>&1
if errorlevel 1 (
    echo [오류] jpackage를 찾을 수 없습니다.
    echo JDK 14 이상이 필요합니다.
    pause & exit /b 1
)

:: ── 1. 작업 폴더 생성 ────────────────────────
if not exist build     mkdir build
if not exist dist      mkdir dist
if not exist input_dir mkdir input_dir

:: ── 2. 컴파일 ────────────────────────────────
echo.
echo [1/4] 컴파일 중...
javac -encoding UTF-8 -d build ^
  AlarmController.java  ^
  AnalogClockSwing.java  ^
  AppLogger.java  ^
  AppRestarter.java  ^
  CaptureManager.java  ^
  ChimeController.java  ^
  ClockPanel.java  ^
  GmailSender.java  ^
  Kakao.java  ^
  Lunar.java  ^
  MenuBuilder.java  ^
  PushSender.java  ^
  TelegramBot.java


if errorlevel 1 (
    echo [오류] 컴파일 실패!
    pause & exit /b 1
)
echo     완료

:: ── 3. JAR 생성 ──────────────────────────────
echo.
echo [2/4] JAR 생성 중...

:: manifest 파일 생성
echo Main-Class: AnalogClockSwing> build\manifest.txt

jar cfm input_dir\AnalogClock.jar build\manifest.txt -C build .
if errorlevel 1 (
    echo [오류] JAR 생성 실패!
    pause & exit /b 1
)
echo     완료

:: ── 4. 설정 파일 복사 (있는 경우) ────────────
echo.
echo [3/4] 리소스 복사 중...
if exist clock_config.properties copy clock_config.properties input_dir\ >nul
if exist alarms.dat              copy alarms.dat              input_dir\ >nul
if exist Kakao.txt               copy Kakao.txt               input_dir\ >nul
if exist *.wav                   copy *.wav                   input_dir\ >nul
if exist *.mp3                   copy *.mp3                   input_dir\ >nul
if exist app.ico                 copy app.ico                 input_dir\ >nul
echo     완료

:: ── 5. jpackage EXE 빌드 ─────────────────────
echo.
echo [4/4] EXE 빌드 중... (1~2분 소요)

:: 아이콘 파일 존재 여부에 따라 분기
if exist app.ico (
    jpackage ^
        --name KootPanKing  ^
        --type app-image ^
        --input input_dir ^
        --dest dist ^
        --main-jar AnalogClock.jar ^
        --main-class AnalogClockSwing ^
        --icon app.ico ^
        --app-version 1.0.0 ^
        --vendor "MyApp"
) else (
    jpackage ^
        --name KootPanKing  ^
        --type app-image ^
        --input input_dir ^
        --dest dist ^
        --main-jar AnalogClock.jar ^
        --main-class AnalogClockSwing ^
        --app-version 1.0.0 ^
        --vendor "MyApp"
)

if errorlevel 1 (
    echo [오류] jpackage 빌드 실패!
    pause & exit /b 1
)

:: ── 완료 ─────────────────────────────────────
echo.
echo ============================================
echo  빌드 완료!
echo  실행파일 위치: dist\AnalogClock\AnalogClock.exe
echo ============================================
echo.
echo  배포 시 dist\AnalogClock\ 폴더 전체를 전달하세요.
echo  (Java 설치 없이 실행 가능)
echo.
pause