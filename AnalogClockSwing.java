import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.UUID;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AnalogClockSwing extends JFrame {	
    private static final String thisProgramName = "[KootPanKing 끝판왕 (v1.64)]";
    // ── Alarm ─────────────────────────────────────────────────────
    // AlarmEntry/알람 로직+UI 는 AlarmController.java 로 분리됨
    AlarmController alarmController;
	
    ClockPanel clockPanel;
    private JPopupMenu popupMenu;
    private Properties config = new Properties();
	
    // ── 설정 파일 저장 폴더 결정 (우선순위 3단계) ─────────────────
    private static final String APP_DIR     = resolveAppDir();
    static final String CONFIG_FILE = APP_DIR + "clock_settings.ini";
	
    private static String resolveAppDir() {
        File jarDir = null;
        // ① sun.java.command
        if (jarDir == null) {
            try {
                String sc = System.getProperty("sun.java.command", "").trim();
                if (sc.endsWith(".jar"))
				jarDir = new File(sc).getAbsoluteFile().getParentFile();
			} catch (Exception ignored) {}
		}
        // ③ CodeSource 폴백
        if (jarDir == null) {
            try {
                File loc = new File(AnalogClockSwing.class.getProtectionDomain()
				.getCodeSource().getLocation().toURI());
                jarDir = loc.isDirectory() ? loc : loc.getParentFile();
			} catch (Exception ignored) {}
		}
        // 기존 설정 파일이 JAR 옆에 있으면 → 무조건 JAR 폴더 사용 (설정 유지)
        if (jarDir != null && new File(jarDir, "clock_settings.ini").exists()) {
            System.out.println("[AppDir] 기존 설정 발견 → JAR 폴더: " + jarDir.getAbsolutePath());
            return jarDir.getAbsolutePath() + File.separator;
		}
        // JAR 폴더에 쓰기 가능하면 사용
        if (jarDir != null && jarDir.canWrite()) {
            System.out.println("[AppDir] JAR 폴더 사용: " + jarDir.getAbsolutePath());
            return jarDir.getAbsolutePath() + File.separator;
		}
        // 쓰기 불가(C:\Program Files 등) → %APPDATA%\AnalogClockSwing\
        String appData = System.getenv("APPDATA");
        if (appData == null) appData = System.getProperty("user.home");
        File dir = new File(appData + File.separator + "AnalogClockSwing");
        if (!dir.exists()) dir.mkdirs();
        System.out.println("[AppDir] APPDATA 폴더 사용: " + dir.getAbsolutePath());
        return dir.getAbsolutePath() + File.separator;
	}
	
    // ── Settings ──────────────────────────────────────────────
    boolean startHidden   = false;  // 트레이 아이콘 상태로 시작
    boolean alwaysOnTop   = true;
    boolean showDigital   = true;
    boolean showNumbers   = true;   // 1~12 숫자 표시 여부
    String  theme         = "Light";
    float   opacity       = 1.0f;
    Color   bgColor       = null;          // null = marble
    String  bgImagePath   = "";            // 고정 배경 이미지 full path ("" = 없음)
    java.awt.image.BufferedImage bgImageCache = null; // 로드된 이미지 캐시
    boolean galaxyMode    = false;         // true = 은하수 배경
    float   galaxyAngle   = 0f;            // 은하수 회전 누적 (radians)
    float   galaxySpeed   = 0.004f;        // 회전 속도 (기본값)
    boolean matrixMode    = false;         // true = 매트릭스 배경
    float   matrixOffset  = 0f;            // 위로 스크롤 누적 (픽셀)
    float   matrixSpeed   = 1.5f;          // 스크롤 속도 px/tick (기본값)
    boolean rainMode      = false;         // true = 빗방울 배경
    boolean snowMode      = false;         // true = 눈 배경
    boolean fireMode      = false;         // true = 불꽃 배경
    boolean sparkleMode   = false;         // true = 반짝이 배경
    boolean bubbleMode    = false;         // true = 버블 배경
	
    // ── Camera Background ─────────────────────────────────────────
    boolean          cameraMode = false;
    CaptureManager.Camera camera = null;
    java.awt.image.BufferedImage cameraFrame = null; // 최신 프레임
    String           cameraUrl  = "http://192.168.0.100:8080/video"; // 마지막 사용 URL

    // ── YouTube Background ────────────────────────────────────────
    boolean          youtubeMode   = false;
    String           youtubeUrl    = "";          // 사용자 입력 YouTube URL
    Thread           youtubeThread = null;        // 프레임 읽기 스레드
    volatile boolean youtubeRunning = false;      // 스레드 중지 플래그

    // ── ITS 교통 CCTV ─────────────────────────────────────────────
    ItsCctvManager   itsCctv    = null;
    // ── Slideshow ─────────────────────────────────────────────────
    String  slideFolder   = "";
    int     slideInterval = 5;                     // 초
    java.util.List<java.io.File> slideImages = new java.util.ArrayList<>();
    private int     slideIndex    = 0;
    java.awt.image.BufferedImage slideImage = null;
    int     slideOverlay  = 55;            // 0=없음, 255=완전 어둡게
    javax.swing.Timer slideTimer = null;
    boolean slideEnabled  = false;         // 슬라이드쇼 활성 여부 (수동 모드 포함)

    // ── Slide 전환 효과 ───────────────────────────────────────────
    String  slideEffect   = "fade";        // fade/zoom_in/zoom_out/left/right/up/down
    java.awt.image.BufferedImage slidePrevImage = null;  // 전환 중 이전 이미지
    float   slideProgress = 1.0f;          // 0.0(시작) → 1.0(완료)
    javax.swing.Timer slideTransTimer = null;
    Color   borderColor   = null;          // null = theme default
    int     borderWidth   = -1;            // -1 = auto (radius/16)
    int     borderAlpha   = 255;           // 0~255
    boolean borderVisible = true;          // 테두리 보이기/제거
    Color   tickColor     = null;           // null = theme default
    boolean tickVisible   = true;           // 눈금 보이기/제거
    boolean secondVisible = true;           // 초침 보이기/제거
    Color   hourColor     = new Color(30,  50,  210); // 시침 색상
    Color   minuteColor   = new Color(10,  160,  30); // 분침 색상
    Color   secondColor   = new Color(220,  30,  30); // 초침 색상
    Color   numberColor   = null;           // null = theme default
    Font    numberFont    = new Font("Georgia", Font.BOLD, 14);
    Font    digitalFont   = new Font("Consolas", Font.PLAIN, 14);
    Color   digitalColor  = Color.WHITE;
    String  cityName      = "Local";
    ZoneId  timeZone      = ZoneId.systemDefault();
    int     showInterval  = 0;
    int     animInterval  = 0;
	
    Timer showTimer, animTimer;
	
    // ── Chime settings ────────────────────────────────────────
    boolean  showLunar      = false;
    float    lunarScrollX   = 0;
	
    // scroll offsets for digital marquee
    float   scrollX       = 0;
    String  lastScrollStr = "";
    Timer   scrollTimer;
	
    // ── 인스턴스 카운터 (Close 시 0이면 전체 종료) ──────────────
    static int instanceCount = 0;
	
    // ── 서비스 객체 ───────────────────────────────────────────────
    final Kakao        kakao = new Kakao();
    final TelegramBot  tg    = new TelegramBot(new TelegramBot.CommandHandler() {
        @Override public java.io.File captureClockScreen() throws Exception { return screenCapture.captureClockScreen(); }
        @Override public java.io.File captureFullScreen()  throws Exception { return screenCapture.captureFullScreen(); }
        @Override public java.io.File captureMonitor(int i) throws Exception { return screenCapture.captureMonitor(i); }
        @Override public void shutdownPC() {
            if (shutdownGuard != null) shutdownGuard.cancel();
            saveConfig();
            String now = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
            gmail.sendShutdownNotice(
                () -> {
                    AppLogger.close();
                    try { Runtime.getRuntime().exec(new String[]{"shutdown", "-s", "-f", "-t", "0"}); }
                    catch (Exception e) { System.out.println("[Shutdown] " + e.getMessage()); }
				},
                "텔레그램 원격 종료 알림",
                GmailSender.APP_SIGNATURE + "텔레그램 명령으로 PC가 종료됩니다.\n\n종료 시각: " + now
			);
		}
        @Override public void rebootPC() {
            if (shutdownGuard != null) shutdownGuard.cancel();
            saveConfig();
            String now = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
            gmail.sendShutdownNotice(
                () -> {
                    AppLogger.close();
                    try { Runtime.getRuntime().exec(new String[]{"shutdown", "-r", "-f", "-t", "0"}); }
                    catch (Exception e) { System.out.println("[Reboot] " + e.getMessage()); }
				},
                "텔레그램 원격 재시작 알림",
                GmailSender.APP_SIGNATURE + "텔레그램 명령으로 PC가 재시작됩니다.\n\n재시작 시각: " + now
			);
		}
        @Override public boolean toggleTrayWindow() {
            boolean nowVisible = !isVisible();
            setVisible(nowVisible);
            startHidden = !nowVisible; // 현재 상태를 startHidden에 반영
            saveConfig();              // 즉시 저장
            return nowVisible;
		}
        @Override public void showImage(java.io.File imageFile) {
            screenCapture.showImageWindow(imageFile);
		}
        @Override public void playMedia(java.io.File mediaFile) {
            chimeController.playMediaFile(mediaFile);
		}
        @Override public void saveConfig() {
            AnalogClockSwing.this.saveConfig();
		}
        @Override public void prepareDialog(java.awt.Window dlg) {
            AnalogClockSwing.this.prepareDialog(dlg);
		}
        @Override public String getFirstAlarmTelegramChatId() {
            for (AlarmController.AlarmEntry a : alarmController.getAlarmList()) {
                if (!a.telegramChatId.isEmpty()) return a.telegramChatId;
			}
            return "";
		}
	});
    final GmailSender  gmail = new GmailSender();
    private final PushSender   push  = new PushSender();
    AppRestarter.ShutdownGuard shutdownGuard; // 강제 종료 감지 훅
    AppRestarter       appRestarter;          // 재시작 / AppCDS 관리
    CaptureManager     screenCapture;         // 화면 캡처
    ChimeController    chimeController; // 차임벨
    // ── chime 설정 임시 보관 (loadConfig 시점에 chimeController 가 미생성) ──
    private boolean   pendingChimeEnabled  = false;
    private String    pendingChimeFile     = "";
    private boolean   pendingChimeFull     = false;
    private boolean[] pendingChimeMinutes  = null;
	
    // ── 공용 Random (매번 new 생성 방지) ─────────────────────────
    private final Random rnd = new Random();
	
    // ── valid intervals for show/animation menus ───────────────
    static final int[] INTERVALS =
	{0,1,2,3,4,5,10,15,20,30,45,60,90,120,180,300,600,1800};
	
    // ── Constructor (새 도시 인스턴스용) ─────────────────────
    public AnalogClockSwing(AnalogClockSwing parent, String newCityName, ZoneId newZone) {
        // 부모 설정 복사
        this.alwaysOnTop  = parent.alwaysOnTop;
        this.showDigital  = parent.showDigital;
        this.showNumbers  = parent.showNumbers;
        this.theme        = parent.theme;
        this.opacity      = parent.opacity;
        this.bgColor      = parent.bgColor;
        this.bgImagePath  = parent.bgImagePath;
        this.bgImageCache = parent.bgImageCache;
        this.galaxyMode   = parent.galaxyMode;
        this.galaxyAngle  = parent.galaxyAngle;
        this.galaxySpeed  = parent.galaxySpeed;
        this.matrixMode   = parent.matrixMode;
        this.matrixOffset = parent.matrixOffset;
        this.matrixSpeed  = parent.matrixSpeed;
        this.borderColor  = parent.borderColor;
        this.borderWidth  = parent.borderWidth;
        this.borderAlpha  = parent.borderAlpha;
        this.borderVisible= parent.borderVisible;
        this.tickColor    = parent.tickColor;
        this.tickVisible  = parent.tickVisible;
        this.secondVisible= parent.secondVisible;
        this.hourColor    = parent.hourColor;
        this.minuteColor  = parent.minuteColor;
        this.secondColor  = parent.secondColor;
        this.numberColor  = parent.numberColor;
        this.numberFont   = parent.numberFont;
        this.digitalFont  = parent.digitalFont;
        this.digitalColor = parent.digitalColor;
        this.showLunar    = parent.showLunar;
        this.slideFolder  = parent.slideFolder;
        this.slideInterval= parent.slideInterval;
        this.slideImages  = new java.util.ArrayList<>(parent.slideImages);
        this.slideIndex   = parent.slideIndex;
        this.slideImage   = null;          // ④ 부모 참조 공유 방지 → 독립 로드
        this.slideOverlay = parent.slideOverlay;
        this.slideEffect  = parent.slideEffect;
        this.showInterval = 0;   // show/anim 은 독립
        this.animInterval = 0;
        // chimeController 복사 (부모 설정 그대로)
        ChimeController parentChime = parent.chimeController;
        this.chimeController = new ChimeController(this, new ChimeController.HostCallback() {
            @Override public ZoneId getTimeZone()             { return timeZone; }
            @Override public void prepareMessageBox()         { AnalogClockSwing.this.prepareMessageBox(); }
            @Override public void prepareDialog(java.awt.Window w) { AnalogClockSwing.this.prepareDialog(w); }
		});
        this.chimeController.setEnabled(parentChime.isEnabled());
        this.chimeController.setFile(parentChime.getFile());
        this.chimeController.setFull(parentChime.isFull());
        boolean[] parentMins = new boolean[60];
        System.arraycopy(parentChime.getMinutes(), 0, parentMins, 0, 60);
        this.chimeController.setMinutes(parentMins);
        this.appRestarter  = new AppRestarter(gmail, tg, this);
        this.screenCapture = new CaptureManager(null); // clockPanel은 initUI 후 주입
        // 새 도시
        this.cityName  = newCityName;
        this.timeZone  = newZone;
        initUI(parent);
	}
	
    // ── Constructor (기본, ini 로드) ───────────────────────────
    public AnalogClockSwing() {
        loadConfig();
        // AppLogger 경로를 GmailSender 에 주입
        gmail.exeFilePath = AppLogger.getExeFilePath();
        gmail.logFilePath = AppLogger.getLogFilePath();
        // ── 분리된 서비스 객체 초기화 ────────────────────────
        appRestarter  = new AppRestarter(gmail, tg, this);
        System.out.println("[AnalogClockSwing] NEW build - AppRestarter OK");
        screenCapture = new CaptureManager(null); // clockPanel은 initUI 후 주입
        chimeController = new ChimeController(this, new ChimeController.HostCallback() {
            @Override public ZoneId getTimeZone()             { return timeZone; }
            @Override public void prepareMessageBox()         { AnalogClockSwing.this.prepareMessageBox(); }
            @Override public void prepareDialog(java.awt.Window w) { AnalogClockSwing.this.prepareDialog(w); }
		});
        applyChimeConfig(); // loadConfig() 에서 임시 보관한 chime 설정 적용
        // AlarmController 초기화
        alarmController = new AlarmController(
            this, APP_DIR + "alarms.dat",
            push, gmail, kakao, tg,
            new AlarmController.HostCallback() {
                @Override public String  getChimeFile()   { return chimeController.getFile(); }
                @Override public boolean isChimeFull()    { return chimeController.isFull(); }
                @Override public ZoneId  getTimeZone()    { return timeZone; }
                @Override public void prepareMessageBox()              { AnalogClockSwing.this.prepareMessageBox(); }
                @Override public void prepareDialog(java.awt.Window w) { AnalogClockSwing.this.prepareDialog(w); }
                @Override public void saveConfig()                     { AnalogClockSwing.this.saveConfig(); }
                @Override public void showNtfyQrDialog(java.awt.Window p, String url, String topic) {
                    AnalogClockSwing.this.showNtfyQrDialog(p, url, topic);
				}
                @Override public void showPushoverQrDialog(java.awt.Window p) {
                    AnalogClockSwing.this.showPushoverQrDialog(p);
				}
			}
		);
        alarmController.loadAlarms();
        initUI(null);
        gmail.sendStartupNotice(); // 시작 알림 이메일
        tg.sendStartupNotice();    // 시작 알림 텔레그램
        shutdownGuard = new AppRestarter.ShutdownGuard(gmail, tg); // 강제 종료 감지 훅 등록
		
        // ── AppCDS JSA 자동 생성 (jar 실행 시만, 백그라운드) ──────
        appRestarter.buildAppCdsIfNeeded(this::saveConfig);
        shutdownGuard.register();                     // Shutdown Hook (Windows종료/kill/Ctrl+C)
	}	//      public AnalogClockSwing() 
	
    private void initUI(AnalogClockSwing parent) {
        instanceCount++;
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setAlwaysOnTop(alwaysOnTop);
        try { setOpacity(opacity); } catch (Exception ignored) {}
		
        clockPanel = new ClockPanel(this);
        add(clockPanel);
        // screenCapture 에 clockPanel 주입 (기본 생성자에서 null 로 초기화된 경우)
        if (screenCapture == null) screenCapture = new CaptureManager(clockPanel);
        else screenCapture = new CaptureManager(clockPanel);
        buildPopupMenu();
        initTrayIcon(); // 트레이 아이콘 초기화
		
        // ── Mouse: 더블클릭(좌/우)=팝업 / 싱글클릭 무시 / 드래그=창이동
        final Point[] dragOrigin = {null};
        clockPanel.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e))
				dragOrigin[0] = e.getPoint();
                else
				dragOrigin[0] = null;
			}
            @Override public void mouseReleased(MouseEvent e) {
                dragOrigin[0] = null;
			}
			/*
			@Override public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2) {
					showPopup(e);
					} else if (e.getClickCount() == 1 && cameraMode) {
					// 더블클릭 두 번째 이벤트와 충돌 방지: 지연 후 실행
					Object prop = java.awt.Toolkit.getDefaultToolkit()
					.getDesktopProperty("awt.multiClickInterval");
					int delay = (prop instanceof Integer) ? (Integer) prop : 600;
					javax.swing.Timer t = new javax.swing.Timer(delay, ev -> captureCamera());
					t.setRepeats(false);
					t.start();
				}
			}
*/
@Override public void mouseClicked(MouseEvent e) {
    if (e.getClickCount() == 2) {
        // 싱글클릭 타이머가 있으면 취소
        Object prev = clockPanel.getClientProperty("singleClickTimer");
        if (prev instanceof javax.swing.Timer) ((javax.swing.Timer) prev).stop();
        showPopup(e);
    } else if (e.getClickCount() == 1 && cameraMode) {
        Object prop = java.awt.Toolkit.getDefaultToolkit()
            .getDesktopProperty("awt.multiClickInterval");
        int delay = (prop instanceof Integer) ? (Integer) prop : 600;
        javax.swing.Timer t = new javax.swing.Timer(delay, ev -> captureCamera());
        t.setRepeats(false);
        t.start();
        clockPanel.putClientProperty("singleClickTimer", t);
    }
}
			private void showPopup(MouseEvent e) {
                buildPopupMenu();
                popupMenu.show(clockPanel, e.getX(), e.getY());
			}
		});
        clockPanel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                if (dragOrigin[0] != null) {
                    Point loc = getLocation();
                    setLocation(loc.x + e.getX() - dragOrigin[0].x,
					loc.y + e.getY() - dragOrigin[0].y);
				}
			}
		});
		
        // ── Mouse: wheel resize ───────────────────────────────
        clockPanel.addMouseWheelListener(e -> {
            clockPanel.adjustRadius(-e.getWheelRotation() * 10);
            repackAndKeepCenter();
		});
		
        // ── Main repaint timer ────────────────────────────────
        new Timer(100, e -> {
            if (galaxyMode) galaxyAngle += galaxySpeed;
            if (matrixMode) matrixOffset += matrixSpeed;
            clockPanel.repaint();
		}).start();
		
        // ── Galaxy 속도 조절 키 (+/-  PageUp/PageDown) ────────
        setFocusable(true);
        addKeyListener(new java.awt.event.KeyAdapter() {
            private static final float STEP    = 0.002f;
            private static final float MAX_SPD = 0.05f;
            private static final float MIN_SPD = 0.0f;
            @Override public void keyPressed(java.awt.event.KeyEvent e) {
                int k = e.getKeyCode();
                boolean isPgUp   = (k == java.awt.event.KeyEvent.VK_PAGE_UP);
                boolean isPgDown = (k == java.awt.event.KeyEvent.VK_PAGE_DOWN);

                // ── 카메라 모드: Space → 캡처 저장 ─────────────────────────
                if (k == java.awt.event.KeyEvent.VK_SPACE && cameraMode && camera != null) {
                    captureCamera();
                    return;
                }

                // ── ITS 교통 CCTV 모드: PgUp/PgDn → 이전/다음 카메라 ──────
                if ((isPgUp || isPgDown) && itsCctv != null && itsCctv.isRunning()) {
                    if (isPgUp) itsCctv.prev(); else itsCctv.next();
                    return;
                }

                // ── 배경 이미지 모드: PgUp/PgDn → 같은 폴더 이전/다음 이미지 ──
                if ((isPgUp || isPgDown) && !bgImagePath.isEmpty() && !slideEnabled) {
                    bgNavigate(isPgUp ? -1 : 1);
                    return;
                }

                // ── Galaxy/Matrix 속도 조절 ──────────────────────────────────
                if (!galaxyMode && !matrixMode) return;
                if (k == java.awt.event.KeyEvent.VK_PLUS
                        || k == java.awt.event.KeyEvent.VK_ADD
                        || isPgUp
                        || k == java.awt.event.KeyEvent.VK_EQUALS) {
                    if (galaxyMode) galaxySpeed = Math.min(MAX_SPD, galaxySpeed + STEP);
                    if (matrixMode) matrixSpeed = Math.min(10.0f, matrixSpeed + 0.5f);
                } else if (k == java.awt.event.KeyEvent.VK_MINUS
                        || k == java.awt.event.KeyEvent.VK_SUBTRACT
                        || isPgDown) {
                    if (galaxyMode) galaxySpeed = Math.max(MIN_SPD, galaxySpeed - STEP);
                    if (matrixMode) matrixSpeed = Math.max(0.0f,  matrixSpeed - 0.5f);
                }
            }
        });
		
        // ── Digital scroll timer (every 40ms) ────────────────
        scrollTimer = new Timer(40, e -> {
            scrollX      -= 1.5f;
            lunarScrollX -= 1.5f;
            clockPanel.repaint();
		});
        scrollTimer.start();
		
        startShowTimer();
        startAnimTimer();
        chimeController.startCheckTimer();
        // 슬라이드쇼: loadConfig(parent=null) 또는 복사 생성자(parent!=null) 모두 여기서 시작
        if (!slideImages.isEmpty() && slideEnabled) {
            startSlideTimer();          // side effect + 타이머 설정
            loadCurrentSlideImage();    // 첫 이미지 로드 (단일 호출)
        } else if (parent != null && !slideImages.isEmpty() && slideImage == null) {
            // 복사 생성자 경로에서 slideEnabled가 없을 때 (도시창 추가)
            startSlideTimer();
            loadCurrentSlideImage();
        }
        // 텔레그램 원격제어 폴링 (설정에서 활성화된 경우)
        if (parent == null) tg.startPolling();
        // 기본값: 정각(0분)에 연주 (parent 에서 복사한 경우 덮어쓰지 않음)
        if (parent == null) chimeController.getMinutes()[0] = true;
		
        // 초기 크기 설정 (pack() 대신 직접 제어)
        setMinimumSize(new Dimension(1, 1));
        Dimension initPref = clockPanel.getPreferredSize();
        setSize(initPref.width, initPref.height);
        clockPanel.setSize(initPref);
        // 새 인스턴스는 부모 창 오른쪽에 배치
        if (parent != null) {
            Point parentLoc = parent.getLocation();
            setLocation(parentLoc.x + parent.getWidth() + 10, parentLoc.y);
			} else {
            setLocationRelativeTo(null);
		}
        // 시작 시 무조건 창 표시 (startHidden 무시)
        setVisible(true);
	}	// private void initUI(AnalogClockSwing parent) 
	
    // ── Resize keeping clock center stable ─────────────────────
    // Windows 에서 pack() 은 이전보다 작아지지 않는 버그가 있으므로
    // setMinimumSize + setSize 로 강제 제어한다.
    void repackAndKeepCenter() {
        Point     oldLoc  = getLocation();
        Dimension oldSize = getSize();
		
        Dimension pref = clockPanel.getPreferredSize();
        Insets    ins  = getInsets();
        int newW = pref.width  + ins.left + ins.right;
        int newH = pref.height + ins.top  + ins.bottom;
		
        // ★ 최소 크기 제약 완전 해제 후 직접 강제 설정
        setMinimumSize(new Dimension(1, 1));
        setSize(newW, newH);
        clockPanel.setSize(pref);
        validate();
        repaint();
		
        // 중심 위치 유지
        setLocation(oldLoc.x - (newW - oldSize.width)  / 2,
		oldLoc.y - (newH - oldSize.height) / 2);
	}
	
    // ═══════════════════════════════════════════════════════════
    //  Popup Menu Builder  (위임 → MenuBuilder)
    // ═══════════════════════════════════════════════════════════
    private void buildPopupMenu() {
        popupMenu = new MenuBuilder(new MenuBuilder.ClockHostContext(this)).build();
	}
	
	/**
		* 설정 저장 후 현재 프로세스를 종료하고 자기 자신을 다시 실행한다.
		*
		* 실행 방식 우선순위:
		*   ① sun.java.command 에 .jar / .exe 가 있으면 그 경로로 재실행
		*   ② CodeSource 위치(jar/exe/class 폴더)로 재실행
		*   ③ 모두 실패하면 재시작 불가 메시지 표시
	*/
    void restartApp() {
        appRestarter.restartApp(() -> {
            if (shutdownGuard != null) shutdownGuard.cancel();
            saveConfig();
		});
	}
	
    void autoStartItemActionListener(JCheckBoxMenuItem autoStartItem) {
		if (autoStartItem.isSelected()) {
			if (AppRestarter.AutoStart.set(true)) {
				prepareMessageBox();
				JOptionPane.showMessageDialog(null,
					"✅ 자동 실행 등록 완료!\n다음 부팅부터 자동으로 시작됩니다.",
				"자동 실행", JOptionPane.INFORMATION_MESSAGE);
				} else {
				autoStartItem.setSelected(false);
				prepareMessageBox();
				JOptionPane.showMessageDialog(null,
					"❌ 자동 실행 등록 실패\n관리자 권한이 필요할 수 있습니다.",
				"자동 실행", JOptionPane.ERROR_MESSAGE);
			}
			} else {
			if (AppRestarter.AutoStart.set(false)) {
				prepareMessageBox();
				JOptionPane.showMessageDialog(null,
				"✅ 자동 실행 해제 완료!", "자동 실행", JOptionPane.INFORMATION_MESSAGE);
				} else {
				autoStartItem.setSelected(true);
				prepareMessageBox();
				JOptionPane.showMessageDialog(null,
				"❌ 자동 실행 해제 실패", "자동 실행", JOptionPane.ERROR_MESSAGE);
			}
		}
	}  //  autoStartItemActionListener
	
    /**
     * 배경 이미지 모드에서 같은 폴더의 이전(delta=-1)/다음(delta=+1) 이미지로 교체.
     * 백그라운드 스레드에서 로드 후 EDT에서 갱신.
     */
    void bgNavigate(int delta) {
        if (bgImagePath.isEmpty()) return;
        java.io.File cur = new java.io.File(bgImagePath);
        java.io.File dir = cur.getParentFile();
        if (dir == null || !dir.isDirectory()) return;

        // 같은 폴더의 이미지 파일 목록 (정렬)
        java.io.File[] files = dir.listFiles(f ->
            !f.isHidden() && f.isFile() && isImageFile(f.getName()));
        if (files == null || files.length == 0) return;
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        // 현재 파일 인덱스 탐색
        int idx = -1;
        for (int i = 0; i < files.length; i++) {
            if (files[i].getAbsolutePath().equalsIgnoreCase(cur.getAbsolutePath())) {
                idx = i; break;
            }
        }
        if (idx < 0) idx = 0; // 현재 파일이 목록에 없으면 첫 번째로

        int next = ((idx + delta) % files.length + files.length) % files.length;
        final java.io.File nextFile = files[next];

        // 백그라운드 로드
        new Thread(() -> {
            try {
                java.awt.image.BufferedImage raw = javax.imageio.ImageIO.read(nextFile);
                if (raw == null) return;
                java.awt.image.BufferedImage img = applyExifOrientation(raw, nextFile);
                javax.swing.SwingUtilities.invokeLater(() -> {
                    bgImagePath  = nextFile.getAbsolutePath();
                    bgImageCache = img;
                    saveConfig();
                    clockPanel.repaint();
                });
            } catch (Exception e) {
                System.out.println("[BgNavigate] 로드 실패: " + e.getMessage());
            }
        }, "BgNavigate").start();
    }

    /** 파일명이 이미지 확장자인지 확인 */
    private boolean isImageFile(String name) {
        String n = name.toLowerCase();
        for (String ext : IMG_EXT) if (n.endsWith(ext)) return true;
        return false;
    }

    void folderItemAction(JCheckBoxMenuItem slideOnOff) {
        // JFileChooser 생성자가 지정 경로를 동기 탐색하여 EDT를 블로킹함.
        // 백그라운드 스레드에서 생성 후 준비되면 EDT에서 다이얼로그 표시.
        final String initPath = slideFolder.isEmpty()
                ? System.getProperty("user.home") : slideFolder;
        new Thread(() -> {
            // 백그라운드: 생성자 블로킹 구간 (네트워크/외장 드라이브 포함)
            final JFileChooser fc = new JFileChooser(initPath);
            // 폴더와 이미지 파일 모두 표시 - 파일 선택 시 상위 폴더를 slideFolder로 사용
            fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            fc.setDialogTitle("이미지 폴더 선택 (폴더 또는 이미지 파일 선택)");
            fc.setFileFilter(new javax.swing.filechooser.FileFilter() {
                @Override public boolean accept(java.io.File f) {
                    if (f.isDirectory()) return true;
                    String n = f.getName().toLowerCase();
                    for (String ext : IMG_EXT) if (n.endsWith(ext)) return true;
                    return false;
                }
                @Override public String getDescription() {
                    return "이미지 파일 (jpg, png, gif, bmp, webp)";
                }
            });
            fc.setAcceptAllFileFilterUsed(false);
            // 준비 완료 → EDT에서 다이얼로그 표시
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (fc.showOpenDialog(AnalogClockSwing.this) == JFileChooser.APPROVE_OPTION) {
                    java.io.File selected = fc.getSelectedFile();
                    // 파일 선택 시 상위 폴더, 폴더 선택 시 그대로 사용
                    slideFolder = selected.isDirectory()
                            ? selected.getAbsolutePath()
                            : selected.getParent();
                    loadSlideImages();
                    if (!slideImages.isEmpty()) {
                        slideIndex = 0;
                        loadCurrentSlideImage();
                        startSlideTimer();
                        slideOnOff.setSelected(true);
                        saveConfig();
                        prepareMessageBox();
                        JOptionPane.showMessageDialog(null,
                            slideImages.size() + "개 이미지 로드됨.", "Slideshow",
                            JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        stopSlideTimer();
                        slideOnOff.setSelected(false);
                        saveConfig();
                        prepareMessageBox();
                        JOptionPane.showMessageDialog(null,
                            "이미지 파일이 없습니다.\n(지원: jpg, png, gif, bmp, webp)",
                            "Slideshow", JOptionPane.WARNING_MESSAGE);
                    }
                }
            });
        }, "FolderChooserInit").start();
    }  // folderItemAction
	
    // ═══════════════════════════════════════════════════════════
    //  Timers
    // ═══════════════════════════════════════════════════════════
    // ── Slideshow 메서드들 ─────────────────────────────────────────
    private static final String[] IMG_EXT = {".jpg",".jpeg",".png",".gif",".bmp",".webp"};
	
    void loadSlideImages() {
        slideImages.clear();
        if (slideFolder.isEmpty()) return;
        java.io.File dir = new java.io.File(slideFolder);
        if (!dir.isDirectory()) return;
        java.io.File[] files = dir.listFiles(f -> {
            if (!f.isFile() || f.isHidden()) return false;
            String n = f.getName().toLowerCase();
            for (String ext : IMG_EXT) if (n.endsWith(ext)) return true;
            return false;
		});
        if (files != null) {
            java.util.Arrays.sort(files,
                (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (java.io.File f : files) slideImages.add(f);
		}
	}
	
    void loadCurrentSlideImage() {
        if (slideImages.isEmpty()) { slideImage = null; return; }
        slideIndex = ((slideIndex % slideImages.size()) + slideImages.size()) % slideImages.size();
        final java.io.File imgFile = slideImages.get(slideIndex);
        final java.awt.image.BufferedImage prevSnap = slideImage; // EDT에서 스냅샷
        // ① EDT 블로킹 방지: 백그라운드 스레드에서 읽기
        new Thread(() -> {
            java.awt.image.BufferedImage loaded = null;
            try {
                java.awt.image.BufferedImage raw = javax.imageio.ImageIO.read(imgFile);
                loaded = applyExifOrientation(raw, imgFile);
            } catch (Exception ignored) {}
            final java.awt.image.BufferedImage result = loaded;
            javax.swing.SwingUtilities.invokeLater(() -> {
                slidePrevImage = prevSnap; // EDT 안에서 안전하게 설정
                slideImage = result;
                startSlideTransition();
            });
        }, "SlideLoader").start();
    }

    /**
     * JPEG APP1(EXIF) 세그먼트를 직접 바이트 파싱해 Orientation 태그(274)를 읽고 회전한다.
     * JDK의 JPEG ImageReader는 APP1을 'unknown' 바이트 덩어리로 취급하므로
     * IIOMetadata DOM 탐색으로는 TIFFField를 찾을 수 없다. 직접 파싱이 유일한 방법.
     */
    private java.awt.image.BufferedImage applyExifOrientation(
            java.awt.image.BufferedImage img, java.io.File file) {
        if (img == null) return null;
        int orientation = readJpegOrientation(file);
        if (orientation <= 1) return img;
        int w = img.getWidth(), h = img.getHeight();
        // TYPE_CUSTOM(0)은 BufferedImage 생성 불가 → TYPE_INT_ARGB fallback
        int type = img.getType() == 0 ? java.awt.image.BufferedImage.TYPE_INT_ARGB : img.getType();
        java.awt.image.BufferedImage out;
        java.awt.geom.AffineTransform t = new java.awt.geom.AffineTransform();
        switch (orientation) {
            case 2: // 좌우 반전
                t.translate(w, 0); t.scale(-1, 1);
                out = new java.awt.image.BufferedImage(w, h, type); break;
            case 3: // 180도
                t.translate(w, h); t.rotate(Math.PI);
                out = new java.awt.image.BufferedImage(w, h, type); break;
            case 4: // 상하 반전
                t.translate(0, h); t.scale(1, -1);
                out = new java.awt.image.BufferedImage(w, h, type); break;
            case 5: // 90도 CW + 좌우 반전
                t.rotate(Math.PI / 2); t.scale(1, -1);
                out = new java.awt.image.BufferedImage(h, w, type); break;
            case 6: // 90도 CW (스마트폰 세로 촬영 → 오른쪽으로 누운 경우)
                t.translate(h, 0); t.rotate(Math.PI / 2);
                out = new java.awt.image.BufferedImage(h, w, type); break;
            case 7: // 90도 CCW + 좌우 반전
                t.translate(h, w); t.rotate(Math.PI / 2); t.scale(-1, 1);
                out = new java.awt.image.BufferedImage(h, w, type); break;
            case 8: // 90도 CCW (왼쪽으로 누운 경우)
                t.translate(0, w); t.rotate(-Math.PI / 2);
                out = new java.awt.image.BufferedImage(h, w, type); break;
            default: return img;
        }
        java.awt.Graphics2D g2 = out.createGraphics();
        g2.drawRenderedImage(img, t);
        g2.dispose();
        return out;
    }

    /** JPEG 파일의 APP1 세그먼트에서 EXIF Orientation 값을 반환한다. 없으면 1. */
    private int readJpegOrientation(java.io.File file) {
        try (java.io.DataInputStream dis = new java.io.DataInputStream(
                new java.io.BufferedInputStream(new java.io.FileInputStream(file)))) {
            if (dis.readUnsignedShort() != 0xFFD8) return 1; // SOI 확인
            while (true) {
                int marker = dis.readUnsignedShort();
                int segLen  = dis.readUnsignedShort(); // length includes 2-byte length field
                if (segLen < 2) break;                 // 손상된 세그먼트 방어
                if (marker == 0xFFE1) { // APP1
                    byte[] data = new byte[segLen - 2];
                    dis.readFully(data);
                    // "Exif\0\0" 헤더 확인 (XMP 등 다른 APP1은 건너뜀)
                    if (data.length > 6 &&
                            data[0]=='E' && data[1]=='x' && data[2]=='i' &&
                            data[3]=='f' && data[4]==0  && data[5]==0) {
                        return parseTiffOrientation(data, 6);
                    }
                    // Exif 아닌 APP1: 이미 readFully로 읽었으므로 skip 없이 continue
                } else if ((marker & 0xFF00) == 0xFF00) {
                    dis.skip(segLen - 2);
                } else {
                    break;
                }
            }
        } catch (Exception ignored) {}
        return 1;
    }

    /** TIFF 헤더(offset)에서 IFD0의 Orientation 태그 값을 반환한다. */
    private int parseTiffOrientation(byte[] data, int offset) {
        if (data.length < offset + 8) return 1;
        boolean le = data[offset] == 'I' && data[offset+1] == 'I'; // II=LE, MM=BE
        int ifdOffset = tiffInt(data, offset + 4, le);
        int ifdAbs = offset + ifdOffset;
        if (ifdAbs + 2 > data.length) return 1;
        int numEntries = tiffShort(data, ifdAbs, le);
        for (int i = 0; i < numEntries; i++) {
            int e = ifdAbs + 2 + i * 12;
            if (e + 12 > data.length) break;
            if (tiffShort(data, e, le) == 274) // Orientation tag
                return tiffShort(data, e + 8, le);
        }
        return 1;
    }

    private int tiffShort(byte[] d, int o, boolean le) {
        int a = d[o]&0xFF, b = d[o+1]&0xFF;
        return le ? (b<<8|a) : (a<<8|b);
    }
    private int tiffInt(byte[] d, int o, boolean le) {
        int b0=d[o]&0xFF, b1=d[o+1]&0xFF, b2=d[o+2]&0xFF, b3=d[o+3]&0xFF;
        return le ? (b3<<24|b2<<16|b1<<8|b0) : (b0<<24|b1<<16|b2<<8|b3);
    }

    /** 전환 효과 타이머 (40ms × 13프레임 ≈ 520ms) */
    private void startSlideTransition() {
        if (slideTransTimer != null) slideTransTimer.stop();
        if (slidePrevImage == null) {
            slideProgress = 1.0f;
            clockPanel.repaint();
            return;
        }
        slideProgress = 0.0f;
        final int STEPS = 13;
        final int[] count = {0};
        slideTransTimer = new javax.swing.Timer(40, e -> {
            count[0]++;
            slideProgress = Math.min(1.0f, (float) count[0] / STEPS);
            clockPanel.repaint();
            if (slideProgress >= 1.0f) {
                ((javax.swing.Timer) e.getSource()).stop();
                slidePrevImage = null;
            }
        });
        slideTransTimer.start();
    }
	
    void advanceSlide(int delta) {
        if (slideImages.isEmpty()) return;
        slideIndex = ((slideIndex + delta) % slideImages.size() + slideImages.size()) % slideImages.size();
        loadCurrentSlideImage();
        // ⑭ 수동 조작 후 slideTimer 리셋 (즉시 자동전환 방지)
        if (slideTimer != null && slideTimer.isRunning()) slideTimer.restart();
	}
	
    void startSlideTimer() {
        if (slideTimer != null) slideTimer.stop();
        if (slideImages.isEmpty()) return;
        // 이미지가 있으면 수동(간격=0) 포함 항상 다른 배경 모드 해제
        stopShowTimer();
        stopItsCctv();
        galaxyMode = false;
        matrixMode = false;
        rainMode = false; snowMode = false; fireMode = false;
        sparkleMode = false; bubbleMode = false;
        stopCamera();
        bgImagePath  = "";
        bgImageCache = null;
        slideEnabled = true;   // 슬라이드쇼 활성 플래그
        if (slideInterval <= 0) return;  // 수동 모드: 타이머 없이 종료
        // ⑱ 자동 타이머 콜백에서만 size>1 체크 (수동 ▶/◀는 항상 동작)
        slideTimer = new Timer(slideInterval * 1000, e -> {
            if (slideImages.size() > 1) advanceSlide(1);
        });
        slideTimer.start();
	}
	
    /** ⑤ 타이머만 중지, 다른 배경 모드/상태 건드리지 않음 (간격=0 수동 선택 시) */
    void stopSlideTimerOnly() {
        if (slideTransTimer != null) { slideTransTimer.stop(); slideTransTimer = null; }
        if (slideTimer      != null) { slideTimer.stop();      slideTimer      = null; }
    }

    void stopSlideTimer() {
        if (slideTransTimer != null) { slideTransTimer.stop(); slideTransTimer = null; } // ③
        if (slideTimer      != null) { slideTimer.stop();      slideTimer      = null; }
        slideEnabled   = false;
        slideImage     = null;
        slidePrevImage = null;
        slideProgress  = 1.0f;
        clockPanel.repaint();
	}
	
    void stopShowTimer() {
        if (showTimer != null) { showTimer.stop(); showTimer = null; }
        showInterval = 0;
        clockPanel.repaint();
	}
	
    // ── Camera Background ─────────────────────────────────────────
	
    void startCamera(String streamUrl) {
        if (camera == null) {
            camera = new CaptureManager.Camera(frame -> {
                cameraFrame = frame;
                javax.swing.SwingUtilities.invokeLater(() -> clockPanel.repaint());
			});
		}
        camera.start(streamUrl);
	}
	
    void stopCamera() {
        if (camera != null) {
            camera.stop();
            camera = null;
		}
        cameraFrame = null;
        cameraMode  = false;
        clockPanel.repaint();
	}

    // ── YouTube Background ────────────────────────────────────────

    /**
     * yt-dlp 로 실제 스트림 URL 추출 → ffmpeg 파이프로 프레임 읽기.
     * yt-dlp.exe / ffmpeg.exe 는 실행 폴더(APP_DIR) 또는 PATH 에 있어야 함.
     * YouTube URL → yt-dlp 로 실제 스트림 URL 추출 후 ffmpeg 캡처
     * 그 외 직접 URL (RTSP, MJPEG, HLS, 일반 HTTP 스트림 등) → ffmpeg 직접 캡처
     */
    void startYoutube(String ytUrl) {
        // URL 이 바뀐 경우 기존 스트림 완전 중단 후 재시작
        if (youtubeRunning && !ytUrl.equals(youtubeUrl)) {
            System.out.println("[Stream] URL 변경 → 기존 스트림 중단");
            youtubeRunning = false;
            if (youtubeThread != null) { youtubeThread.interrupt(); youtubeThread = null; }
        }
        stopYoutube();
        youtubeUrl     = ytUrl;
        youtubeMode    = true;
        youtubeRunning = true;

        youtubeThread = new Thread(() -> {
            while (youtubeRunning) {
                try {
                    // ── ① URL 종류 판별 ──────────────────────────────────
                    // YouTube 여부: youtube.com / youtu.be 도메인
                    boolean isYoutube = youtubeUrl.contains("youtube.com")
                                     || youtubeUrl.contains("youtu.be");

                    String streamUrl;
                    if (isYoutube) {
                        // ── YouTube: yt-dlp 로 실제 스트림 URL 추출 ──────
                        String ytdlpPath = resolveExe("yt-dlp.exe");
                        ProcessBuilder ytPb = new ProcessBuilder(
                            ytdlpPath,
                            "-f", "bestvideo[ext=mp4]/bestvideo/best",
                            "--get-url",
                            "--no-playlist",
                            youtubeUrl
                        );
                        ytPb.redirectErrorStream(true);
                        Process ytProc = ytPb.start();
                        java.io.BufferedReader ytReader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(ytProc.getInputStream(), "UTF-8"));
                        streamUrl = "";
                        String line;
                        while ((line = ytReader.readLine()) != null) {
                            line = line.trim();
                            if (line.startsWith("http")) { streamUrl = line; break; }
                        }
                        ytProc.waitFor();
                        if (streamUrl.isEmpty() || !youtubeRunning) {
                            System.out.println("[Stream] YouTube 스트림 URL 추출 실패");
                            break;
                        }
                        System.out.println("[Stream] YouTube 스트림 URL 추출 성공");
                    } else {
                        // ── 직접 URL: RTSP / MJPEG / HLS / HTTP 스트림 등
                        // yt-dlp 없이 ffmpeg 에 직접 URL 전달
                        streamUrl = youtubeUrl;
                        System.out.println("[Stream] 직접 URL 사용: " + streamUrl);
                    }

                    // ── ② 5초마다 jpg 1장씩 캡처 ────────────────────────
                    String ffmpegPath = resolveExe("ffmpeg.exe");
                    final String thisUrl = youtubeUrl;
                    long startMs = System.currentTimeMillis();
                    long maxMs   = 6L * 3600 * 1000;
                    long seekSec = 0; // 매 캡처마다 seek 위치 이동 (라이브는 0 고정)

                    while (youtubeRunning) {
                        // URL 변경 감지
                        if (!thisUrl.equals(youtubeUrl)) {
                            System.out.println("[Stream] URL 변경 → 재시작");
                            break;
                        }
                        // 6시간 후 yt-dlp 재추출
                        if (System.currentTimeMillis() - startMs > maxMs) {
                            System.out.println("[Stream] URL 만료 → 재추출");
                            break;
                        }

                        try {
                            // ffmpeg: 스트림에서 jpg 1장을 stdout으로 출력
                            // -vf "scale=iw:ih:flags=lanczos" → 원본 해상도 유지, lanczos 고품질
                            // -q:v 1 → jpg 최고 품질 (1=최고, 31=최저)
                            // -frames:v 1 → 딱 1프레임만
                            ProcessBuilder capPb = new ProcessBuilder(
                                ffmpegPath,
                                "-reconnect",          "1",
                                "-reconnect_streamed",  "1",
                                "-reconnect_delay_max", "5",
                                "-i",                  streamUrl,
                                "-frames:v",           "1",
                                "-vf",                 "scale=iw:ih:flags=lanczos",
                                "-q:v",                "1",
                                "-f",                  "image2",
                                "-vcodec",             "mjpeg",
                                "pipe:1"
                            );
                            capPb.redirectErrorStream(false);
                            Process capProc = capPb.start();
                            // stderr 버림
                            new Thread(() -> {
                                try { capProc.getErrorStream().transferTo(java.io.OutputStream.nullOutputStream()); }
                                catch (Exception ignored) {}
                            }, "YT-cap-stderr").start();

                            // stdout → jpg 바이트 읽기 → BufferedImage
                            byte[] jpgBytes = capProc.getInputStream().readAllBytes();
                            capProc.waitFor();

                            if (jpgBytes.length > 0) {
                                java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(jpgBytes);
                                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(bais);
                                if (img != null) {
                                    cameraFrame = img;
                                    javax.swing.SwingUtilities.invokeLater(() -> clockPanel.repaint());
                                    System.out.println("[Stream] 캡처 완료 " + img.getWidth() + "x" + img.getHeight());
                                }
                            }
                        } catch (Exception capEx) {
                            System.out.println("[Stream] 캡처 오류: " + capEx.getMessage());
                        }

                        // 5초 대기
                        try { Thread.sleep(5000); } catch (InterruptedException ie) { break; }
                    }

                } catch (Exception ex) {
                    System.out.println("[Stream] 오류: " + ex.getMessage());
                    if (!youtubeRunning) break;
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                }
            }
            System.out.println("[Stream] 스레드 종료");
        }, "YouTube-BG");
        youtubeThread.setDaemon(true);
        youtubeThread.start();
    }

    void stopYoutube() {
        youtubeRunning = false;
        if (youtubeThread != null) {
            youtubeThread.interrupt();
            youtubeThread = null;
        }
        youtubeMode = false;
        cameraFrame = null;
        if (clockPanel != null) clockPanel.repaint();
    }

    /** 실행 파일 경로 탐색: APP_DIR → PATH */
    private String resolveExe(String exeName) {
        File f = new File(APP_DIR, exeName);
        if (f.exists()) return f.getAbsolutePath();
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                File candidate = new File(dir, exeName);
                if (candidate.exists()) return candidate.getAbsolutePath();
            }
        }
        return exeName;
    }

    /**
     * Windows 바탕화면 단색 배경색 반환.
     * HKCU\Control Panel\Colors\Background → "R G B" 문자열 파싱.
     * 읽기 실패 시 null 반환 → ClockPanel 에서 기본색 사용.
     */
    Color getDesktopColor() {
        try {
            Process p = Runtime.getRuntime().exec(
                new String[]{"reg", "query",
                    "HKCU\\Control Panel\\Colors", "/v", "Background"});
            java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream(), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                // 형식: Background    REG_SZ    R G B
                java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(\\d+)\\s+(\\d+)\\s+(\\d+)$").matcher(line.trim());
                if (m.find()) {
                    return new Color(
                        Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)));
                }
            }
            p.waitFor();
        } catch (Exception e) {
            System.out.println("[DesktopColor] 읽기 실패: " + e.getMessage());
        }
        return null;
    }

    // ── ITS 교통 CCTV ─────────────────────────────────────────────

    ItsCctvManager getItsCctv() {
        if (itsCctv == null) {
            itsCctv = new ItsCctvManager(new ItsCctvManager.HostCallback() {
                @Override public void setBgImage(String path, java.awt.image.BufferedImage img) {
                    bgImageCache = img;
                    bgImagePath  = path;
                }
                @Override public void repaintClock() { clockPanel.repaint(); }
            });
        }
        return itsCctv;
    }

    void startItsCctv() {
        // 다른 배경 모드 해제
        stopCamera(); stopSlideTimer(); stopShowTimer(); stopYoutube();
        galaxyMode = false; matrixMode = false; rainMode = false;
        snowMode   = false; fireMode   = false; sparkleMode = false;
        bubbleMode = false; bgColor    = null;
        getItsCctv().start();
    }

    void stopItsCctv() {
        if (itsCctv != null) itsCctv.stop();
        bgImageCache = null;
        bgImagePath  = "";
        clockPanel.repaint();
    }
	
    /** 현재 카메라 프레임을 실행폴더/img/ 에 저장 */
    void captureCamera() {
        if (camera == null) return;
        // 실행 파일 폴더 기준 saveDir 결정
        File saveDir;
        String exePath = AppLogger.getExeFilePath();
        if (exePath != null && !exePath.isEmpty() && !exePath.equals("(unknown)")) {
            saveDir = new File(exePath).getParentFile();
			} else {
            saveDir = new File(System.getProperty("user.dir"));
		}
        String saved = camera.capture(saveDir);
        if (saved != null) {
            // 저장 완료 토스트(짧은 팝업) 대신 타이틀바 없는 시계라 콘솔 출력
            System.out.println("[Camera] 캡처 저장: " + saved);
            // 간단한 비차단 알림
            String fileName = new java.io.File(saved).getName();
            javax.swing.JOptionPane pane = new javax.swing.JOptionPane(
                "📸 저장 완료: img/" + fileName,
			javax.swing.JOptionPane.INFORMATION_MESSAGE);
            javax.swing.JDialog dlg = pane.createDialog(this, "카메라 캡처");
            dlg.setModal(false);
            dlg.setVisible(true);
            new Timer(2000, ev -> dlg.dispose()).start();
		}
	}
	
    void startShowTimer() {
        if (showTimer != null) showTimer.stop();
        if (showInterval > 0) {
            // 배경색 Show 를 실제로 시작할 때만 다른 배경 모드를 해제한다.
            // showInterval == 0(미사용) 상태에서는 기존 배경 모드를 건드리지 않는다.
            galaxyMode = false;
            matrixMode = false;
            rainMode = false; snowMode = false; fireMode = false;
            sparkleMode = false; bubbleMode = false;
            stopCamera();
            stopItsCctv();
            stopSlideTimer();
            showTimer = new Timer(showInterval * 1000, e -> {
                bgColor = new Color(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256));
                clockPanel.repaint();
            });
            showTimer.start();
        }
        // showInterval == 0 이면 아무것도 하지 않음 — 기존 배경 모드 유지
    }
	
    // 현재 창이 위치한 모니터의 GraphicsConfiguration 반환
    private GraphicsConfiguration getCurrentScreenGC() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        // 창의 중심 포인트
        Point center = new Point(
            getX() + getWidth()  / 2,
            getY() + getHeight() / 2
		);
        for (GraphicsDevice gd : ge.getScreenDevices()) {
            GraphicsConfiguration gc = gd.getDefaultConfiguration();
            if (gc.getBounds().contains(center)) {
                return gc;
			}
		}
        // 못 찾으면 기본 스크린
        return ge.getDefaultScreenDevice().getDefaultConfiguration();
	}
	
    void startAnimTimer() {
        if (animTimer != null) animTimer.stop();
        if (animInterval > 0) {
            animTimer = new Timer(animInterval * 1000, e -> {
                // 현재 시계가 있는 모니터 영역 안에서만 이동
                GraphicsConfiguration gc = getCurrentScreenGC();
                Rectangle bounds = gc.getBounds();
                Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
				
                // 실제 사용 가능한 영역 (작업표시줄 제외)
                int minX = bounds.x + insets.left;
                int minY = bounds.y + insets.top;
                int maxX = bounds.x + bounds.width  - insets.right  - getWidth();
                int maxY = bounds.y + bounds.height - insets.bottom - getHeight();
				
                if (maxX <= minX) maxX = minX + 1;
                if (maxY <= minY) maxY = minY + 1;
				
                int nx = minX + rnd.nextInt(maxX - minX);
                int ny = minY + rnd.nextInt(maxY - minY);
                setLocation(nx, ny);
			});
            animTimer.start();
		}
	}
	
    // ═══════════════════════════════════════════════════════════
    //  Border Dialog
    // ═══════════════════════════════════════════════════════════
    void showBorderDialog() {
        JDialog dlg = new JDialog(this, "테두리 설정", true);
        dlg.setLayout(new BorderLayout(8, 8));
		
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 8, 6, 8);
        gc.anchor = GridBagConstraints.WEST;
		
        // ── 색깔 ────────────────────────────────────────────
        gc.gridx=0; gc.gridy=0;
        panel.add(new JLabel("테두리 색깔:"), gc);
		
        Color initColor = (borderColor != null) ? borderColor
		: (theme.equals("Black") ? new Color(220,220,220) : new Color(20,20,20));
        final Color[] chosen = { initColor };
		
        // 색상 미리보기 버튼
        JButton colorBtn = new JButton("  색상 선택  ");
        colorBtn.setBackground(new Color(chosen[0].getRed(), chosen[0].getGreen(),
		chosen[0].getBlue(), 255));
        colorBtn.setOpaque(true);
        colorBtn.setBorderPainted(true);
        gc.gridx=1; gc.gridwidth=2;
        panel.add(colorBtn, gc);
		
        colorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(dlg, "테두리 색상", chosen[0]);
            if (c != null) {
                chosen[0] = c;
                colorBtn.setBackground(c);
                colorBtn.repaint();
			}
		});
		
        // 초기화 버튼
        JButton resetColorBtn = new JButton("테마 기본색");
        gc.gridx=3; gc.gridwidth=1;
        panel.add(resetColorBtn, gc);
        resetColorBtn.addActionListener(e -> {
            chosen[0] = null;  // null = 테마 기본값
            Color def = theme.equals("Black") ? new Color(220,220,220) : new Color(20,20,20);
            colorBtn.setBackground(def);
            colorBtn.repaint();
		});
		
        // ── 두께 ────────────────────────────────────────────
        gc.gridx=0; gc.gridy=1; gc.gridwidth=1;
        panel.add(new JLabel("두께 (px):"), gc);
		
        int initW = (borderWidth > 0) ? borderWidth : Math.max(8, clockPanel.getRadius() / 16);
        JSpinner widthSpinner = new JSpinner(new SpinnerNumberModel(initW, 1, 120, 1));
        widthSpinner.setPreferredSize(new Dimension(80, 26));
        gc.gridx=1; gc.gridwidth=1;
        panel.add(widthSpinner, gc);
		
        JLabel widthPx = new JLabel("px");
        gc.gridx=2;
        panel.add(widthPx, gc);
		
        JButton autoWidthBtn = new JButton("자동 (radius/16)");
        gc.gridx=3;
        panel.add(autoWidthBtn, gc);
        autoWidthBtn.addActionListener(e ->
		widthSpinner.setValue(Math.max(8, clockPanel.getRadius() / 16)));
		
        // ── 투명도 ──────────────────────────────────────────
        gc.gridx=0; gc.gridy=2; gc.gridwidth=1;
        panel.add(new JLabel("투명도:"), gc);
		
        JSlider alphaSlider = new JSlider(0, 255, borderAlpha);
        alphaSlider.setMajorTickSpacing(51);
        alphaSlider.setMinorTickSpacing(17);
        alphaSlider.setPaintTicks(true);
        alphaSlider.setPaintLabels(false);
        alphaSlider.setPreferredSize(new Dimension(200, 40));
        gc.gridx=1; gc.gridwidth=2; gc.fill=GridBagConstraints.HORIZONTAL;
        panel.add(alphaSlider, gc);
        gc.fill=GridBagConstraints.NONE;
		
        JLabel alphaLabel = new JLabel(Math.round(borderAlpha / 2.55f) + "%");
        alphaLabel.setPreferredSize(new Dimension(40, 20));
        gc.gridx=3; gc.gridwidth=1;
        panel.add(alphaLabel, gc);
        alphaSlider.addChangeListener(e ->
		alphaLabel.setText(Math.round(alphaSlider.getValue() / 2.55f) + "%"));
		
        // ── 실시간 미리보기 체크 ─────────────────────────────
        gc.gridx=0; gc.gridy=3; gc.gridwidth=4;
        JCheckBox previewBox = new JCheckBox("실시간 미리보기", false);
        panel.add(previewBox, gc);
		
        // 슬라이더/스피너 변경 시 미리보기
        alphaSlider.addChangeListener(e -> {
            if (previewBox.isSelected()) {
                borderAlpha = alphaSlider.getValue();
                clockPanel.repaint();
			}
		});
        widthSpinner.addChangeListener(e -> {
            if (previewBox.isSelected()) {
                borderWidth = (int) widthSpinner.getValue();
                repackAndKeepCenter();
			}
		});
		
        dlg.add(panel, BorderLayout.CENTER);
		
        // ── 버튼 ────────────────────────────────────────────
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton okBtn     = new JButton("  확인  ");
        JButton cancelBtn = new JButton("  취소  ");
        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);
        dlg.add(btnPanel, BorderLayout.SOUTH);
		
        okBtn.addActionListener(e -> {
            borderColor = chosen[0];
            borderWidth = (int) widthSpinner.getValue();
            borderAlpha = alphaSlider.getValue();
            repackAndKeepCenter();
            clockPanel.repaint();
            dlg.dispose();
		});
        cancelBtn.addActionListener(e -> {
            // 미리보기 취소 시 원복
            clockPanel.repaint();
            dlg.dispose();
		});
		
        dlg.setResizable(false);
        prepareDialog(dlg);
        dlg.setVisible(true);
	}
	
    private void prepareDialog(java.awt.Window dlg) {
        moveToTopRight();
        if (dlg != null) {
            dlg.pack();
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            dlg.setLocation(
                (screen.width  - dlg.getWidth())  / 2,
                (screen.height - dlg.getHeight()) / 2
			);
		}
	}
	
    /** JOptionPane 표시 전 시계를 오른쪽 상단으로 이동 */
    void prepareMessageBox() {
        moveToTopRight();
        // JOptionPane은 null parent 사용 → 자동으로 화면 중앙에 표시됨
	}
	
    /** 시계를 화면 오른쪽 상단으로 이동 */
    private void moveToTopRight() {
        Toolkit tk = Toolkit.getDefaultToolkit();
        Dimension screen = tk.getScreenSize();
        GraphicsConfiguration gc = getGraphicsConfiguration();
        Insets insets = tk.getScreenInsets(gc); // 작업표시줄 여백
        int x = screen.width  - getWidth()  - insets.right  - 10;
        int y = insets.top + 10;
        setLocation(x, y);
	}
	
    /**
		* JSON 문자열에서 키 값 추출 (정규식 파서)
		* - 문자열값("...") 및 숫자/boolean 값 모두 처리
		* - 콜론 앞뒤 공백 허용
		* - 이스케이프 문자(\") 포함된 문자열값도 정확히 추출
	*/
    private String extractJson(String json, String key) {
        // 문자열값: "key" : "value"  (이스케이프 포함)
        Pattern strPat = Pattern.compile(
		"\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher strMat = strPat.matcher(json);
        if (strMat.find()) return strMat.group(1);
		
        // 숫자/boolean값: "key" : 12345  또는  "key" : true
        Pattern numPat = Pattern.compile(
		"\"" + Pattern.quote(key) + "\"\\s*:\\s*([^,}\\s]+)");
        Matcher numMat = numPat.matcher(json);
        if (numMat.find()) return numMat.group(1);
		
        return "";
	}
	
    // ═══════════════════════════════════════════════════════════
    //  트레이 아이콘
    // ═══════════════════════════════════════════════════════════
    private TrayIcon trayIcon = null;
	
    private void initTrayIcon() {
        if (!SystemTray.isSupported()) return;
        try {
            SystemTray tray = SystemTray.getSystemTray();
			
            // 트레이 아이콘 이미지 (시계 모양 간단히 그리기)
            int size = 16;
            java.awt.image.BufferedImage img =
			new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(30, 30, 60));
            g2.fillOval(0, 0, size-1, size-1);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(0, 0, size-1, size-1);
            // 시침/분침
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(size/2, size/2, size/2, 3);       // 12시 방향 분침
            g2.drawLine(size/2, size/2, size-3, size/2);  // 3시 방향 시침
            g2.dispose();
			
            // 트레이 팝업 메뉴
            PopupMenu trayPopup = new PopupMenu();
			
            MenuItem showItem = new MenuItem("🕐 시계 보이기/숨기기");
            showItem.addActionListener(e -> {
                setVisible(!isVisible());
			});
            trayPopup.add(showItem);
			
            trayPopup.addSeparator();
			
            // 카카오톡 보내기
            MenuItem trayKakaoItem = new MenuItem("📱 카카오톡 보내기...");
            trayKakaoItem.addActionListener(e -> {
                if (kakao.kakaoAccessToken.isEmpty()) {
					prepareMessageBox();
                    JOptionPane.showMessageDialog(null,
                        "먼저 카카오 로그인을 해주세요.\n(시계 우클릭 → 카카오톡 → 카카오 로그인)",
					"카카오톡", JOptionPane.WARNING_MESSAGE);
                    return;
				}
                String msg = JOptionPane.showInputDialog(null,
				"보낼 메시지를 입력하세요:", "카카오톡 나에게 보내기", JOptionPane.PLAIN_MESSAGE);
                if (msg != null && !msg.trim().isEmpty()) {
                    new Thread(() -> kakao.sendKakao("📱 [끝판왕] 시계", msg.trim()), "KakaoSend").start();
				}
			});
            trayPopup.add(trayKakaoItem);
			
            // 텔레그램 보내기
            MenuItem trayTelegramItem = new MenuItem("✈️ 텔레그램 보내기...");
            trayTelegramItem.addActionListener(e -> showTelegramDialog());
            trayPopup.add(trayTelegramItem);
			
            trayPopup.addSeparator();
			
            MenuItem exitItem2 = new MenuItem("EXIT");
            exitItem2.addActionListener(e -> { saveConfig(); sendShutdownEmailAndExit(); });
            trayPopup.add(exitItem2);
			
            trayIcon = new TrayIcon(img, "[끝판왕] 시계", trayPopup);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> setVisible(!isVisible())); // 더블클릭 토글
            tray.add(trayIcon);
			} catch (Exception e) {
            System.out.println("[Tray] 트레이 아이콘 오류: " + e.getMessage());
		}
	}
	
    // ═══════════════════════════════════════════════════════════
    private void showPushoverQrDialog(java.awt.Window parent) {
        push.showPushoverQrDialog(parent, this::prepareDialog);
	}
	
    // ═══════════════════════════════════════════════════════════
    //  ntfy / Pushover QR코드 다이얼로그 → PushSender 로 이동됨
    // ═══════════════════════════════════════════════════════════
	
    /** ntfy 구독 QR코드 다이얼로그 표시 */
    private void showNtfyQrDialog(java.awt.Window parent, String url, String topic) {
        push.showNtfyQrDialog(parent, url, topic, this::prepareDialog);
	}
	
    /**
		* 순수 Java로 QR코드 이미지 생성
		* Reed-Solomon ECC + QR 매트릭스 인코딩 (Version 3, ECC Level M)
		* URL 길이에 맞게 자동으로 버전 선택
	*/
	
    void showTelegramHelp() {
        tg.showTelegramHelp(this);
	}
	
    void showTelegramDialog() {
        tg.showTelegramDialog(this);
	}	
	
	/** 파일/이미지를 텔레그램으로 전송 (multipart/form-data) */
	
    void showChimeDialog() { chimeController.showChimeDialog(); }
	
    void showAbout() {
        StringBuilder sb = new StringBuilder();
        File f = new File("about.txt");
        if (f.exists()) {
            try (BufferedReader br = new BufferedReader(
			new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
			String line;
			while ((line = br.readLine()) != null) sb.append(line).append("\n");
            } catch (IOException ex) { sb.append("about.txt 읽기 실패"); }
			} else {
            sb.append(thisProgramName)
			.append("\n\n\n")
			.append("• 대리석 질감 아나로그 시계\n\n")
			.append("• 텔레그램으로 내 컴퓨터 끝장 원격 제어\n\n")
			.append("• 자유 자재 시계 디자인\n\n")
			.append("• 텔레그램, GMail, 카카오톡 동시 처리\n\n")
			.append("• 김갑수 , 2026-3-16 , 대한민국 서울\n\n");
		}
        JTextArea ta = new JTextArea(sb.toString(), 15, 40);
        ta.setEditable(false);
        ta.setFont(new Font("Malgun Gothic", Font.PLAIN, 13));
        // parent를 this(JFrame)로 지정하여 브라우저 앞으로 나오게 함
        JDialog aboutDlg = new JOptionPane(new JScrollPane(ta),
		JOptionPane.INFORMATION_MESSAGE).createDialog(this, thisProgramName);
        aboutDlg.setAlwaysOnTop(true);
		// aboutDlg.getContentPane().setBackground(Color.DARK_GRAY);
		// aboutDlg.getContentPane().setBackground(Color.BLACK);
		aboutDlg.setVisible(true);
	}
	
    // ═══════════════════════════════════════════════════════════
    //  Config load / save
    // ═══════════════════════════════════════════════════════════
    private void loadConfig() {
		File f = new File(CONFIG_FILE);
		System.out.println("[Config] 경로: " + f.getAbsolutePath());
        if (!f.exists()) {
            downloadDefaultConfig(f);
            if (!f.exists()) return; // 다운로드 실패 시 코드 기본값 사용
		}
        try (FileInputStream fis = new FileInputStream(f)) {
            config.load(fis);
            alwaysOnTop  = Boolean.parseBoolean(config.getProperty("alwaysOnTop","true"));
            startHidden  = Boolean.parseBoolean(config.getProperty("startHidden","false"));
            showDigital  = Boolean.parseBoolean(config.getProperty("showDigital","true"));
            showNumbers  = Boolean.parseBoolean(config.getProperty("showNumbers","true"));
            theme        = config.getProperty("theme","Light");
            opacity      = Float.parseFloat(config.getProperty("opacity","1.0"));
            cityName     = config.getProperty("cityName","Local");
            String tz    = config.getProperty("timeZone","local");
            timeZone     = tz.equals("local") ? ZoneId.systemDefault() : ZoneId.of(tz);
            showInterval = Integer.parseInt(config.getProperty("showInterval","0"));
            animInterval = Integer.parseInt(config.getProperty("animInterval","0"));
            numberFont   = new Font(
                config.getProperty("fontName","Georgia"), Font.BOLD,
			Integer.parseInt(config.getProperty("fontSize","14")));
            digitalFont  = new Font(
                config.getProperty("digFontName","Consolas"), Font.PLAIN,
			Integer.parseInt(config.getProperty("digFontSize","14")));
            String bg = config.getProperty("bgColor","");
            if (!bg.isEmpty()) bgColor = new Color(Integer.parseInt(bg));
            bgImagePath = config.getProperty("bgImagePath", "");
            if (!bgImagePath.isEmpty()) {
                try {
                    java.io.File bgFile = new java.io.File(bgImagePath);
                    java.awt.image.BufferedImage raw = javax.imageio.ImageIO.read(bgFile);
                    bgImageCache = applyExifOrientation(raw, bgFile); // EXIF 회전 보정
                } catch (Exception e) {
                    System.out.println("[BgImage] 로드 실패: " + e.getMessage());
                    bgImagePath = ""; bgImageCache = null;
                }
            }
            String tc = config.getProperty("tickColor","");
            if (!tc.isEmpty()) tickColor = new Color(Integer.parseInt(tc));
            tickVisible   = Boolean.parseBoolean(config.getProperty("tickVisible","true"));
            secondVisible = Boolean.parseBoolean(config.getProperty("secondVisible","true"));
            String hc = config.getProperty("hourColor","");
            if (!hc.isEmpty()) hourColor   = new Color(Integer.parseInt(hc));
            String mc = config.getProperty("minuteColor","");
            if (!mc.isEmpty()) minuteColor = new Color(Integer.parseInt(mc));
            String sc2 = config.getProperty("secondColor","");
            if (!sc2.isEmpty()) secondColor = new Color(Integer.parseInt(sc2));
            String nc = config.getProperty("numberColor","");
            if (!nc.isEmpty()) numberColor = new Color(Integer.parseInt(nc));
            String dc = config.getProperty("digitalColor","");
            digitalColor = dc.isEmpty() ? Color.WHITE : new Color(Integer.parseInt(dc));
            showLunar = Boolean.parseBoolean(config.getProperty("showLunar","false"));
            slideFolder   = config.getProperty("slideFolder", "");
            slideInterval = Integer.parseInt(config.getProperty("slideInterval","5"));
            slideOverlay  = Integer.parseInt(config.getProperty("slideOverlay","55"));
            slideEffect   = config.getProperty("slideEffect", "fade");
            if (Boolean.parseBoolean(config.getProperty("slideEnabled","false"))
				&& !slideFolder.isEmpty()) {
                loadSlideImages();
                slideIndex = 0;  // ⑥ 이전 값 잔류 방지
                // ⑰ 저장된 slideIndex 복원
                try {
                    int saved = Integer.parseInt(config.getProperty("slideIndex", "0"));
                    if (!slideImages.isEmpty())
                        slideIndex = Math.max(0, Math.min(saved, slideImages.size() - 1));
                } catch (Exception ignored) {}
                if (!slideImages.isEmpty()) slideEnabled = true; // initUI에서 startSlideTimer 호출
			}
            // Border
            String bc = config.getProperty("borderColor","");
            if (!bc.isEmpty()) borderColor = new Color(Integer.parseInt(bc));
            borderWidth = Integer.parseInt(config.getProperty("borderWidth","-1"));
            borderAlpha = Integer.parseInt(config.getProperty("borderAlpha","255"));
            borderVisible = Boolean.parseBoolean(config.getProperty("borderVisible","true"));
        } catch (Exception ignored) {}
        // ── Chime: loadConfig() 호출 시점에 chimeController 가 null 이므로
        //    별도 try 블록으로 분리. applyChimeConfig() 에서 생성 후 적용.
        try {
            pendingChimeEnabled = Boolean.parseBoolean(config.getProperty("chimeEnabled","false"));
            pendingChimeFile    = config.getProperty("chimeFile","");
            pendingChimeFull    = Boolean.parseBoolean(config.getProperty("chimeFull","false"));
            String mins = config.getProperty("chimeMinutes","0");
            boolean[] loadedMins = new boolean[60];
            for (String m : mins.split(",")) {
                try { int idx = Integer.parseInt(m.trim());
                    if (idx >= 0 && idx < 60) loadedMins[idx] = true;
                } catch (Exception ignored2) {}
            }
            pendingChimeMinutes = loadedMins;
        } catch (Exception ignored) {}
        // ── 인증 정보 / 경로 캐시: 앞쪽 파싱 예외와 무관하게 항상 읽는다 ──
        try {
            gmail.from   = config.getProperty("gmail.from",   "");
            gmail.pass   = config.getProperty("gmail.pass",   "");
            gmail.lastTo = config.getProperty("gmail.lastTo", "");
            kakao.kakaoRestApiKey   = config.getProperty("kakao.apiKey",       "");
            kakao.kakaoClientSecret = config.getProperty("kakao.clientSecret", "");
            tg.botToken  = config.getProperty("tg.botToken", "");
            tg.myChatId  = config.getProperty("tg.myChatId", "");
            tg.polling   = Boolean.parseBoolean(config.getProperty("tg.polling", "false"));
            if (appRestarter != null) appRestarter.setCachedPaths(
                config.getProperty("app.exePath",   ""),
                config.getProperty("app.javawPath", ""),
			config.getProperty("app.jsaPath",   ""));
            cameraUrl       = config.getProperty("camera.url", "http://192.168.0.100:8080/video");
            // ITS CCTV API 키 복원 (별도 try - appRestarter NPE 와 무관하게 항상 실행)
            try { getItsCctv().setApiKey(config.getProperty("its.cctv.apiKey", "")); } catch (Exception ignored) {}
            galaxyMode  = Boolean.parseBoolean(config.getProperty("galaxyMode", "false"));
            try { galaxySpeed = Float.parseFloat(config.getProperty("galaxySpeed", "0.004")); } catch (Exception ignored) {}
            matrixMode  = Boolean.parseBoolean(config.getProperty("matrixMode", "false"));
            try { matrixSpeed = Float.parseFloat(config.getProperty("matrixSpeed", "1.5"));   } catch (Exception ignored) {}
            // 창 위치 / 크기 복원
            try {
                int wx = Integer.parseInt(config.getProperty("winX", "-1"));
                int wy = Integer.parseInt(config.getProperty("winY", "-1"));
                int ww = Integer.parseInt(config.getProperty("winW", "-1"));
                int wh = Integer.parseInt(config.getProperty("winH", "-1"));
                if (wx >= 0 && wy >= 0) setLocation(wx, wy);
                if (ww > 0  && wh > 0)  setSize(ww, wh);
            } catch (Exception ignored) {}
            rainMode    = Boolean.parseBoolean(config.getProperty("rainMode",    "false"));
            snowMode    = Boolean.parseBoolean(config.getProperty("snowMode",    "false"));
            fireMode    = Boolean.parseBoolean(config.getProperty("fireMode",    "false"));
            sparkleMode = Boolean.parseBoolean(config.getProperty("sparkleMode", "false"));
            bubbleMode  = Boolean.parseBoolean(config.getProperty("bubbleMode",  "false"));
            // ── 애니메이션 배경 모드가 켜져 있으면 bgImage 우선순위 충돌 방지 ──
            // drawFace() 에서 bgImageCache != null 조건이 galaxy/matrix/rain 등보다
            // 먼저 체크되므로, 이미지 배경 캐시를 비워야 애니메이션 배경이 표시된다.
            if (galaxyMode || matrixMode || rainMode || snowMode
                    || fireMode || sparkleMode || bubbleMode) {
                bgImagePath  = "";
                bgImageCache = null;
            }
            // cameraMode 복원은 initUI 완료 후 startCamera 호출이 필요하므로 플래그만 저장
            if (Boolean.parseBoolean(config.getProperty("cameraMode", "false"))
				&& !cameraUrl.isEmpty()) {
                // initUI 이후에 실행되도록 SwingUtilities.invokeLater 사용
                final String urlToRestore = cameraUrl;
                javax.swing.SwingUtilities.invokeLater(() -> {
                    cameraMode = true;
                    startCamera(urlToRestore);
                    clockPanel.repaint();
				});
			}
            // youtubeMode 복원
            youtubeUrl = config.getProperty("youtubeUrl", "");
            if (Boolean.parseBoolean(config.getProperty("youtubeMode", "false"))
                    && !youtubeUrl.isEmpty()) {
                final String ytUrlToRestore = youtubeUrl;
                javax.swing.SwingUtilities.invokeLater(() -> {
                    startYoutube(ytUrlToRestore);
                });
            }
		} catch (Exception ignored) {}
	}
	
    /** ini 파일이 없을 때 GitHub에서 기본 설정 파일을 다운로드 */
    private void downloadDefaultConfig(File destFile) {
        final String DEFAULT_INI_URL =
		"https://raw.githubusercontent.com/GarpsuKim/KootPanKing/refs/heads/main/clock_settings_default.ini";
		System.out.println("[Config] 기본 설정 파일 다운로드 시도: " + DEFAULT_INI_URL);
        try {
            java.net.URL url = new java.net.URI(DEFAULT_INI_URL).toURL();
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            con.connect();
            if (con.getResponseCode() != 200) {
                System.out.println("[Config] 다운로드 실패 (HTTP " + con.getResponseCode() + ") — 코드 기본값 사용");
                con.disconnect();
                return;
			}
            try (java.io.InputStream in = con.getInputStream();
				java.io.FileOutputStream out = new java.io.FileOutputStream(destFile)) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
			}
            con.disconnect();
            System.out.println("[Config] 기본 설정 파일 다운로드 완료: " + destFile.getAbsolutePath());
			} catch (Exception e) {
            System.out.println("[Config] 다운로드 오류 — 코드 기본값 사용: " + e.getMessage());
		}
	}
	
    /** loadConfig() 에서 임시 보관한 chime 설정을 chimeController 생성 후 적용 */
    private void applyChimeConfig() {
        if (chimeController == null) return;
        chimeController.setEnabled(pendingChimeEnabled);
        chimeController.setFile(pendingChimeFile);
        chimeController.setFull(pendingChimeFull);
        if (pendingChimeMinutes != null) {
            chimeController.setMinutes(pendingChimeMinutes);
        }
    }

    void saveConfig() {
        config.setProperty("alwaysOnTop",  String.valueOf(alwaysOnTop));
        config.setProperty("startHidden",  String.valueOf(!isVisible())); // 숨김 상태로 종료했으면 true
        config.setProperty("showDigital",  String.valueOf(showDigital));
        config.setProperty("showNumbers",  String.valueOf(showNumbers));
        config.setProperty("theme",        theme);
        config.setProperty("opacity",      String.valueOf(opacity));
        config.setProperty("cityName",     cityName);
        config.setProperty("timeZone",     timeZone.getId());
        config.setProperty("showInterval", String.valueOf(showInterval));
        config.setProperty("animInterval", String.valueOf(animInterval));
        config.setProperty("fontName",     numberFont.getFamily());
        config.setProperty("fontSize",     String.valueOf(numberFont.getSize()));
        config.setProperty("digFontName",  digitalFont.getFamily());
        config.setProperty("digFontSize",  String.valueOf(digitalFont.getSize()));
        if (bgColor     != null) config.setProperty("bgColor",     String.valueOf(bgColor.getRGB()));
        config.setProperty("bgImagePath", bgImagePath != null ? bgImagePath : "");
        if (tickColor   != null) config.setProperty("tickColor",   String.valueOf(tickColor.getRGB()));
        config.setProperty("tickVisible",   String.valueOf(tickVisible));
        config.setProperty("secondVisible", String.valueOf(secondVisible));
        config.setProperty("hourColor",     String.valueOf(hourColor.getRGB()));
        config.setProperty("minuteColor",   String.valueOf(minuteColor.getRGB()));
        config.setProperty("secondColor",   String.valueOf(secondColor.getRGB()));
        if (numberColor != null) config.setProperty("numberColor", String.valueOf(numberColor.getRGB()));
        config.setProperty("digitalColor", String.valueOf(digitalColor.getRGB()));
        config.setProperty("showLunar", String.valueOf(showLunar));
        config.setProperty("slideFolder",   slideFolder);
        config.setProperty("slideInterval", String.valueOf(slideInterval));
        config.setProperty("slideOverlay",  String.valueOf(slideOverlay));
        config.setProperty("slideEffect",   slideEffect);
        config.setProperty("slideIndex",    String.valueOf(slideIndex));   // ⑰
        config.setProperty("slideEnabled",  String.valueOf(slideEnabled));
        config.setProperty("gmail.from",   gmail.from);
        config.setProperty("gmail.pass",   gmail.pass);
        config.setProperty("gmail.lastTo", gmail.lastTo);
        config.setProperty("kakao.apiKey",        kakao.kakaoRestApiKey);
        config.setProperty("kakao.clientSecret",  kakao.kakaoClientSecret);
        config.setProperty("tg.botToken",  tg.botToken);
        config.setProperty("tg.myChatId",  tg.myChatId);
        config.setProperty("tg.polling",   String.valueOf(tg.polling));
        if (!appRestarter.getCachedExePath().isEmpty())   config.setProperty("app.exePath",   appRestarter.getCachedExePath());
        if (!appRestarter.getCachedJavawPath().isEmpty()) config.setProperty("app.javawPath", appRestarter.getCachedJavawPath());
        if (!appRestarter.getCachedJsaPath().isEmpty())   config.setProperty("app.jsaPath",   appRestarter.getCachedJsaPath());
        config.setProperty("camera.url",  cameraUrl);
        config.setProperty("its.cctv.apiKey", getItsCctv().getApiKey());
        config.setProperty("galaxyMode",  String.valueOf(galaxyMode));
        config.setProperty("galaxySpeed", String.valueOf(galaxySpeed));
        config.setProperty("matrixMode",  String.valueOf(matrixMode));
        config.setProperty("matrixSpeed", String.valueOf(matrixSpeed));
        // 창 위치 / 크기
        config.setProperty("winX", String.valueOf(getX()));
        config.setProperty("winY", String.valueOf(getY()));
        config.setProperty("winW", String.valueOf(getWidth()));
        config.setProperty("winH", String.valueOf(getHeight()));
        config.setProperty("rainMode",    String.valueOf(rainMode));
        config.setProperty("snowMode",    String.valueOf(snowMode));
        config.setProperty("fireMode",    String.valueOf(fireMode));
        
        config.setProperty("sparkleMode", String.valueOf(sparkleMode));
        config.setProperty("bubbleMode",  String.valueOf(bubbleMode));
        config.setProperty("cameraMode",  String.valueOf(cameraMode && camera != null && camera.isRunning()));
        config.setProperty("youtubeMode", String.valueOf(youtubeMode));
        config.setProperty("youtubeUrl",  youtubeUrl != null ? youtubeUrl : "");
        // config.setProperty("kakaoAccessToken",  kakaoAccessToken);
        // config.setProperty("kakaoRefreshToken", kakaoRefreshToken);
        // Border
        if (borderColor != null) config.setProperty("borderColor", String.valueOf(borderColor.getRGB()));
        config.setProperty("borderWidth", String.valueOf(borderWidth));
        config.setProperty("borderAlpha", String.valueOf(borderAlpha));
        config.setProperty("borderVisible", String.valueOf(borderVisible));
        // Chime
        config.setProperty("chimeEnabled", String.valueOf(chimeController.isEnabled()));
        config.setProperty("chimeFile",    chimeController.getFile());
        config.setProperty("chimeFull",    String.valueOf(chimeController.isFull()));
        StringBuilder sb = new StringBuilder();
        boolean[] mins = chimeController.getMinutes();
        for (int i=0; i<60; i++) if (mins[i]) { if (sb.length()>0) sb.append(","); sb.append(i); }
        config.setProperty("chimeMinutes", sb.toString());
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            config.store(fos, "AnalogClockSwing Settings");
		} catch (IOException ignored) {}
	}
	
	
	
    /** 프로그램 종료 직전 이메일 + 텔레그램 전송 후 종료 */
    private void sendShutdownEmailAndExit() {
        if (shutdownGuard != null) shutdownGuard.cancel();
        // Gmail 설정 유무와 무관하게 텔레그램 전송이 완료된 뒤 종료한다.
        // tg.sendShutdownNotice() 는 비동기(새 스레드)이므로,
        // Gmail 미설정 시 즉시 System.exit() 하면 텔레그램 메시지가 취소된다.
        // → 별도 스레드에서 텔레그램 동기 전송 완료 후 Gmail → exit 순서를 보장한다.
        new Thread(() -> {
            // ① 텔레그램 종료 알림 (동기 대기)
            tg.sendShutdownNoticeSync();
            // ② Gmail 종료 알림 → 완료(또는 미설정) 후 exit
            javax.swing.SwingUtilities.invokeLater(() ->
                gmail.sendShutdownNotice(() -> { AppLogger.close(); System.exit(0); })
            );
        }, "ShutdownSequence").start();
	}
	
	private static java.net.URL toUrl(String s) {
		try { 
			@SuppressWarnings("deprecation")
			java.net.URL url = new java.net.URL(s);
			return url;
		}
		catch (Exception e) { throw new RuntimeException(e); }
	}
    // QR 코드 생성 메서드들은 PushSender 로 이동됨 → push 위임
    private java.awt.image.BufferedImage generateQrCode(String text, int imgSize) {
        return push.generateQrCode(text, imgSize);
	}
    private java.awt.image.BufferedImage generateQrCodeOffline(String text, int imgSize) {
        return push.generateQrCodeOffline(text, imgSize);
	}
    private boolean[][] buildQrMatrix(String text, int size) {
        return push.buildQrMatrix(text, size);
	}
    private void drawFinderPattern(boolean[][] m, int row, int col) {
        push.drawFinderPattern(m, row, col);
	}
    private boolean isReserved(boolean[][] m, int r, int c, int size) {
        return push.isReserved(m, r, c, size);
	}
    // ═══════════════════════════════════════════════════════════
    //  Main
    // ═══════════════════════════════════════════════════════════
    public static void main(String[] args) {
        AppLogger.init();   // ★ 로거 초기화 - 가장 먼저 실행
		System.out.println("[ " + thisProgramName + " ] [main] start");
        AppLogger.writeToFile("[ " + thisProgramName + " ] [main] 시작");
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(AnalogClockSwing::new);
		System.out.println("[ " + thisProgramName + " ] [main] bye bye");
        AppLogger.writeToFile("[ " + thisProgramName + " ] [main] 바이바이");
	}
}