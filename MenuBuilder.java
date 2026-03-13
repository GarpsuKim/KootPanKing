import java.io.File;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * AnalogClockSwing 의 팝업 메뉴 생성 로직을 분리한 클래스.
 * AnalogClockSwing 인스턴스를 HostContext 인터페이스로 주입받아
 * 부모 클래스에 대한 직접 의존 없이 JPopupMenu 를 빌드한다.
 */
public class MenuBuilder {
    private static final String mainTitle = "■■ 끝판왕 (v1.63)";
    // ── 호스트(AnalogClockSwing)가 제공해야 하는 콜백 인터페이스 ──
    public interface HostContext {
        // ── 상태 읽기 ─────────────────────────────────────────────
        boolean  isAlwaysOnTop();
        boolean  isShowDigital();
        boolean  isShowNumbers();
        String   getTheme();
        float    getOpacity();
        Color    getBgColor();
        boolean  isGalaxyMode();
        boolean  isMatrixMode();
        boolean  isRainMode();
        boolean  isSnowMode();
        boolean  isFireMode();
        boolean  isSparkleMode();
        boolean  isBubbleMode();
        Color    getBorderColor();
        int      getBorderWidth();
        int      getBorderAlpha();
        boolean  isBorderVisible();
        Color    getTickColor();
        boolean  isTickVisible();
        boolean  isSecondVisible();
        Color    getHourColor();
        Color    getMinuteColor();
        Color    getSecondColor();
        Color    getNumberColor();
        Font     getNumberFont();
        Font     getDigitalFont();
        Color    getDigitalColor();
        boolean  isShowLunar();
        int      getShowInterval();
        int      getAnimInterval();
        boolean  isChimeEnabled();
        int[]    getIntervals();           // INTERVALS 배열
        boolean  checkAutoStart();
        GmailSender getGmail();
        TelegramBot getTg();

        // ── 상태 쓰기 ─────────────────────────────────────────────
        void setAlwaysOnTop(boolean v);
        void setShowDigital(boolean v);
        void setShowNumbers(boolean v);
        void setTheme(String v);
        void setOpacity(float v);
        void setBgColor(Color v);
        void setGalaxyMode(boolean v);
        void setMatrixMode(boolean v);
        void setRainMode(boolean v);
        void setSnowMode(boolean v);
        void setFireMode(boolean v);
        void setSparkleMode(boolean v);
        void setBubbleMode(boolean v);
        String getBgImagePath();
        void   setBgImage(String fullPath, java.awt.image.BufferedImage img);
        void setTickColor(Color v);
        void setTickVisible(boolean v);
        void setSecondVisible(boolean v);
        void setHourColor(Color v);
        void setMinuteColor(Color v);
        void setSecondColor(Color v);
        void setNumberColor(Color v);
        void setNumberFont(Font v);
        void setDigitalFont(Font v);
        void setDigitalColor(Color v);
        void setShowLunar(boolean v);
        void setShowInterval(int v);
        void setAnimInterval(int v);
        void setBorderVisible(boolean v);

        // ── 액션 위임 ─────────────────────────────────────────────
        void repaintClock();
        void repackAndKeepCenter();
        void saveConfig();
        void startShowTimer();
        void stopShowTimer();
        void startAnimTimer();
        void stopChime();
        void disposeInstance();           // Close 버튼 용
        void exitAll();                   // EXIT 버튼 용
        void restartApp();                // Restart 버튼 용
        void showBorderDialog();
        void showChimeDialog();
        void showAbout();
        void showTelegramDialog();
        void showTelegramHelp();
        void autoStartItemActionListener(JCheckBoxMenuItem item);
        void showAlarmDialog();
        void sendToTray();            // 창을 숨기고 트레이로 보내기

        // ── SlideShow ─────────────────────────────────────────────
        boolean isSlideRunning();         // slideTimer 실행 중 여부
        boolean isSlideImagesEmpty();     // slideImages 비어있는지
        String  getSlideFolder();         // 선택된 폴더 경로
        int     getSlideInterval();
        int     getSlideOverlay();
        String  getSlideEffect();         // 전환 효과 키
        void    setSlideInterval(int v);
        void    setSlideOverlay(int v);
        void    setSlideEffect(String v); // 전환 효과 설정
        void    startSlideTimer();
        void    stopSlideTimer();
        void    stopSlideTimerOnly();     // ⑤ 타이머만 중지
        void    loadCurrentSlideImage(); // ⑬ 첫 이미지 즉시 로드
        void    loadSlideImages();        // 폴더에서 이미지 재로드
        void    advanceSlide(int delta);  // 슬라이드 이전/다음
        void    showFolderChooser(JCheckBoxMenuItem slideOnOff); // folderItemAction 위임

        // ── Global Cities ─────────────────────────────────────────
        String  getCityName();
        void    openCityInstance(String cityName, java.time.ZoneId zone); // 새 시계 인스턴스

        // ── Camera ────────────────────────────────────────────────
        boolean isCameraMode();
        void    setCameraMode(boolean v);
        void    startCamera(String url);
        void    stopCamera();
        void    captureCamera();          // 현재 프레임 → img/ 저장
        String  getCameraUrl();
        void    setCameraUrl(String url);

        // ── ITS 교통 CCTV ─────────────────────────────────────────
        ItsCctvManager getItsCctv();      // lazy-init 접근
        void    startItsCctv();           // 다른 배경 해제 + 타이머 시작
        void    stopItsCctv();            // 타이머 중지 + 배경 초기화

        // ── Kakao ─────────────────────────────────────────────────
        String  getKakaoAccessToken();
        void    kakaoLogin();
        void    kakaoSend(String title, String msg);
        void    showKakaoGuide();
        void    prepareMessageBox();      // JOptionPane 위치 조정

        // ── Log / Config ──────────────────────────────────────────
        String  getLogFilePath();         // 현재 로그 파일 경로
        String  getConfigFilePath();      // 설정 파일(ini) 경로

        // ── UI 부모 컴포넌트 ──────────────────────────────────────
        Component getOwnerComponent();    // JColorChooser 등의 parent
    }

    // ── 생성자 ────────────────────────────────────────────────────
    private final HostContext host;

    public MenuBuilder(HostContext host) {
        this.host = host;
    }

    // ═══════════════════════════════════════════════════════════
    //  build() : 팝업 메뉴를 새로 만들어 반환
    // ═══════════════════════════════════════════════════════════
    public JPopupMenu build() {
        JPopupMenu menu = new JPopupMenu();

        // ── Title ──────────────────────────────────────────────
        JMenuItem titleMenu = new JMenuItem(mainTitle);
        menu.add(titleMenu);
        menu.addSeparator();

        // ── Always On Top ──────────────────────────────────────
        JCheckBoxMenuItem aot = new JCheckBoxMenuItem("Always On Top", host.isAlwaysOnTop());
        aot.addActionListener(e -> {
            host.setAlwaysOnTop(aot.isSelected());
        });
        menu.add(aot);

        // ── Theme ──────────────────────────────────────────────
        JMenu themeMenu = new JMenu("Theme");
        ButtonGroup themeGroup = new ButtonGroup();
        for (String t : new String[]{"Light", "Black"}) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(t, host.getTheme().equals(t));
            themeGroup.add(item);
            item.addActionListener(e -> { host.setTheme(t); host.repaintClock(); });
            themeMenu.add(item);
        }
        menu.add(themeMenu);

        // ── Digital Clock ──────────────────────────────────────
        JMenu digitalMenu = new JMenu("Digital Clock");
        JCheckBoxMenuItem digitalOnOff = new JCheckBoxMenuItem("Show Digital", host.isShowDigital());
        digitalOnOff.addActionListener(e -> {
            host.setShowDigital(digitalOnOff.isSelected());
            host.repackAndKeepCenter();
        });
        digitalMenu.add(digitalOnOff);
        digitalMenu.addSeparator();

        // Digital Font sub-menu
        JMenu digFontMenu = new JMenu("Digital Font");
        String[] digFonts = {"Consolas", "Courier New", "Monospaced", "Arial", "Tahoma",
                "Verdana", "Malgun Gothic", "굴림", "돋움"};
        ButtonGroup dfg = new ButtonGroup();
        for (String fn : digFonts) {
            JRadioButtonMenuItem fi = new JRadioButtonMenuItem(fn,
                    host.getDigitalFont().getFamily().equals(fn));
            dfg.add(fi);
            fi.addActionListener(e -> {
                host.setDigitalFont(new Font(fn, host.getDigitalFont().getStyle(),
                        host.getDigitalFont().getSize()));
                host.repaintClock();
            });
            digFontMenu.add(fi);
        }
        digFontMenu.addSeparator();

        // Digital Font size
        JMenu digSizeMenu = new JMenu("Size");
        for (int sz : new int[]{10, 11, 12, 13, 14, 16, 18, 20, 22, 24}) {
            JMenuItem si = new JMenuItem(String.valueOf(sz));
            si.addActionListener(e -> {
                host.setDigitalFont(host.getDigitalFont().deriveFont((float) sz));
                host.repackAndKeepCenter();
            });
            digSizeMenu.add(si);
        }
        digFontMenu.add(digSizeMenu);
        digitalMenu.add(digFontMenu);

        // Digital Color
        JMenuItem digColorItem = new JMenuItem("Digital Color...");
        digColorItem.addActionListener(e -> {
            Color c = JColorChooser.showDialog(host.getOwnerComponent(), "디지털 글자색",
                    host.getDigitalColor());
            if (c != null) { host.setDigitalColor(c); host.repaintClock(); }
        });
        digitalMenu.add(digColorItem);
        menu.add(digitalMenu);

        // ── Lunar ──────────────────────────────────────────────
        JCheckBoxMenuItem lunarItem = new JCheckBoxMenuItem("Lunar", host.isShowLunar());
        lunarItem.addActionListener(e -> {
            host.setShowLunar(lunarItem.isSelected());
            host.repackAndKeepCenter();
        });
        menu.add(lunarItem);

        // ── Global Cities ──────────────────────────────────────
        JMenu globalMenu = buildGlobalMenu(new JMenu("Global Cities..."));
        menu.add(globalMenu);

        // ── Opacity ────────────────────────────────────────────
        JMenu opacityMenu = new JMenu("Opacity");
        ButtonGroup opg = new ButtonGroup();
        for (int op : new int[]{100, 90, 80, 70, 60, 50, 40, 30}) {
            JRadioButtonMenuItem oi = new JRadioButtonMenuItem(op + "%",
                    Math.round(host.getOpacity() * 100) == op);
            opg.add(oi);
            final float f = op / 100f;
            oi.addActionListener(e -> host.setOpacity(f));
            opacityMenu.add(oi);
        }
        menu.add(opacityMenu);

        // ── 아나로그 배경색 ────────────────────────────────────
        JMenuItem bgItem = new JMenuItem("배경색 지정...");
        bgItem.addActionListener(e -> {
            Color c = JColorChooser.showDialog(host.getOwnerComponent(), "배경색",
                    host.getBgColor() != null ? host.getBgColor() : Color.WHITE);
            if (c != null) { host.stopSlideTimer(); host.stopShowTimer(); host.stopCamera(); host.setGalaxyMode(false); host.setMatrixMode(false); host.setRainMode(false); host.setSnowMode(false); host.setFireMode(false); host.setSparkleMode(false); host.setBubbleMode(false); host.setBgImage("", null); host.setBgColor(c); host.saveConfig(); host.repaintClock(); }
        });
        JMenuItem bgImageItem = new JMenuItem("이미지 파일 선택...");
        bgImageItem.addActionListener(e -> {
            // JFileChooser 생성자가 지정 경로를 동기 탐색하여 EDT를 블로킹함.
            // 백그라운드 스레드에서 생성 후 준비되면 EDT에서 다이얼로그 표시.
            final String prev = host.getBgImagePath();
            new Thread(() -> {
                // ① 백그라운드: 생성자 블로킹 구간
                final String initPath = (prev != null && !prev.isEmpty())
                        ? new java.io.File(prev).getParent()
                        : System.getProperty("user.home");
                final JFileChooser fc = new JFileChooser(initPath);
                fc.setDialogTitle("배경 이미지 선택");
                fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "이미지 파일 (jpg, jpeg, png, bmp, gif)", "jpg", "jpeg", "png", "bmp", "gif"));
                // ② EDT: 다이얼로그 표시
                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (fc.showOpenDialog(host.getOwnerComponent()) != JFileChooser.APPROVE_OPTION) return;
                    final java.io.File f = fc.getSelectedFile();
                    // ③ 백그라운드: 이미지 읽기 (ImageIO.read 도 EDT 블로킹 원인)
                    new Thread(() -> {
                        try {
                            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(f);
                            if (img == null) throw new Exception("이미지를 읽을 수 없습니다.");
                            javax.swing.SwingUtilities.invokeLater(() -> {
                                host.stopSlideTimer(); host.stopShowTimer(); host.stopCamera();
                                host.setGalaxyMode(false); host.setMatrixMode(false);
                                host.setRainMode(false); host.setSnowMode(false); host.setFireMode(false);
                                host.setSparkleMode(false); host.setBubbleMode(false);
                                host.setBgColor(null);
                                host.setBgImage(f.getAbsolutePath(), img);
                                host.saveConfig();
                                host.repaintClock();
                            });
                        } catch (Exception ex) {
                            javax.swing.SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(host.getOwnerComponent(),
                                    "이미지 로드 실패: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE));
                        }
                    }, "BgImageLoader").start();
                });
            }, "BgImageChooserInit").start();
        });
        JMenuItem bgReset = new JMenuItem("배경색 초기화 (Marble)");
        bgReset.addActionListener(e -> { host.stopSlideTimer(); host.stopShowTimer(); host.stopCamera(); host.setGalaxyMode(false); host.setMatrixMode(false); host.setRainMode(false); host.setSnowMode(false); host.setFireMode(false); host.setSparkleMode(false); host.setBubbleMode(false); host.setBgImage("", null); host.setBgColor(null); host.repaintClock(); });
        JMenuItem bgGalaxy = new JMenuItem("Galaxy");
        bgGalaxy.addActionListener(e -> { host.stopSlideTimer(); host.stopShowTimer(); host.stopCamera(); host.setBgImage("", null); host.setGalaxyMode(true); host.setMatrixMode(false); host.setRainMode(false); host.setSnowMode(false); host.setFireMode(false); host.setSparkleMode(false); host.setBubbleMode(false); host.setBgColor(null); host.repaintClock(); });
        JMenuItem bgMatrix = new JMenuItem("Matrix");
        bgMatrix.addActionListener(e -> { host.stopSlideTimer(); host.stopShowTimer(); host.stopCamera(); host.setBgImage("", null); host.setMatrixMode(true); host.setGalaxyMode(false); host.setRainMode(false); host.setSnowMode(false); host.setFireMode(false); host.setSparkleMode(false); host.setBubbleMode(false); host.setBgColor(null); host.repaintClock(); });
        JMenuItem bgRain = new JMenuItem("🌧 Rain");
        bgRain.addActionListener(e -> { host.stopSlideTimer(); host.stopShowTimer(); host.stopCamera(); host.setBgImage("", null); host.setRainMode(true); host.setGalaxyMode(false); host.setMatrixMode(false); host.setSnowMode(false); host.setFireMode(false); host.setSparkleMode(false); host.setBubbleMode(false); host.setBgColor(null); host.repaintClock(); });
        JMenuItem bgSnow = new JMenuItem("❄️ Snow");
        bgSnow.addActionListener(e -> { host.stopSlideTimer(); host.stopShowTimer(); host.stopCamera(); host.setBgImage("", null); host.setSnowMode(true); host.setGalaxyMode(false); host.setMatrixMode(false); host.setRainMode(false); host.setFireMode(false); host.setSparkleMode(false); host.setBubbleMode(false); host.setBgColor(null); host.repaintClock(); });
        JMenuItem bgFire = new JMenuItem("🔥 Fire");
        bgFire.addActionListener(e -> { host.stopSlideTimer(); host.stopShowTimer(); host.stopCamera(); host.setBgImage("", null); host.setFireMode(true); host.setGalaxyMode(false); host.setMatrixMode(false); host.setRainMode(false); host.setSnowMode(false); host.setSparkleMode(false); host.setBubbleMode(false); host.setBgColor(null); host.repaintClock(); });
        JMenuItem bgSparkle = new JMenuItem("✨ Sparkle");
        bgSparkle.addActionListener(e -> { host.stopSlideTimer(); host.stopShowTimer(); host.stopCamera(); host.setBgImage("", null); host.setSparkleMode(true); host.setGalaxyMode(false); host.setMatrixMode(false); host.setRainMode(false); host.setSnowMode(false); host.setFireMode(false); host.setBubbleMode(false); host.setBgColor(null); host.repaintClock(); });
        JMenuItem bgBubble = new JMenuItem("🫧 Bubble");
        bgBubble.addActionListener(e -> { host.stopSlideTimer(); host.stopShowTimer(); host.stopCamera(); host.setBgImage("", null); host.setBubbleMode(true); host.setGalaxyMode(false); host.setMatrixMode(false); host.setRainMode(false); host.setSnowMode(false); host.setFireMode(false); host.setSparkleMode(false); host.setBgColor(null); host.repaintClock(); });
        // ── 배경색 Show ────────────────────────────────────────
        JMenu showMenu = new JMenu("배경색 Show...");
        for (int v : host.getIntervals()) {
            JMenuItem si = new JMenuItem(v == 0 ? "0 (중단)" : String.valueOf(v));
            si.addActionListener(e -> { host.setShowInterval(v); host.startShowTimer(); });
            showMenu.add(si);
        }
        JMenu bgMenu = new JMenu("배경_설정");
        bgMenu.add(bgItem);       // 1
        bgMenu.add(bgImageItem);  // 2 (이미지 파일 선택)
        bgMenu.add(bgReset);      // 3
        bgMenu.add(bgGalaxy);     // 4
        bgMenu.add(bgMatrix);     // 5
        bgMenu.add(bgRain);       // 6
        bgMenu.add(bgSnow);       // 7
        bgMenu.add(bgFire);       // 8
        bgMenu.add(bgSparkle);    // 10
        bgMenu.add(bgBubble);     // 11
        bgMenu.add(showMenu);     // 12
        bgMenu.add(new javax.swing.JSeparator());
        JMenuItem speedGuide = new JMenuItem("⚡ 크기 및 속도 조절");        speedGuide.addActionListener(e -> JOptionPane.showMessageDialog(
            host.getOwnerComponent(),
            "시계 크기와 Galaxy/Matrix의 이동 속도를 조절 할 수 있습니다.\n\n" +
            " Galaxy Matrix 더 빠르게 :  PgUp 또는 [ + ]\n" +
            " Galaxy Matrix 더 느리게 :  PgDn 또는 [ - ]\n" +
            " ITS CCTV 이전 카메라    :  PgUp\n" +
            " ITS CCTV 다음 카메라    :  PgDn\n" +
			" 시계 크기 실시간 변경    :  마우스 스크롤",
            "크기와 이동 속도 조절", JOptionPane.INFORMATION_MESSAGE));
        bgMenu.add(speedGuide);
        bgMenu.add(buildCameraMenuItem());
        bgMenu.add(buildItsCctvMenuItem());
        menu.add(bgMenu);

        // ── 테두리 ─────────────────────────────────────────────
        JMenuItem borderItem = new JMenuItem("테두리 설정...");
        borderItem.addActionListener(e -> host.showBorderDialog());
        JCheckBoxMenuItem borderVisible = new JCheckBoxMenuItem("보이기", host.isBorderVisible());
        borderVisible.addActionListener(e -> {
            host.setBorderVisible(borderVisible.isSelected());
            host.repackAndKeepCenter();
        });
        JMenu borderMenu = new JMenu("테두리");
        borderMenu.add(borderVisible);
        borderMenu.addSeparator();
        borderMenu.add(borderItem);
        menu.add(borderMenu);

        // ── 눈금 Color ─────────────────────────────────────────
        JMenuItem tickItem = new JMenuItem("눈금 Color...");
        tickItem.addActionListener(e -> {
            Color def = host.getTheme().equals("Black") ? Color.WHITE : Color.BLACK;
            Color c = JColorChooser.showDialog(host.getOwnerComponent(), "눈금 색상",
                    host.getTickColor() != null ? host.getTickColor() : def);
            if (c != null) { host.setTickColor(c); host.repaintClock(); }
        });
        JMenuItem tickReset = new JMenuItem("눈금 Color 초기화");
        tickReset.addActionListener(e -> { host.setTickColor(null); host.repaintClock(); });
        JCheckBoxMenuItem tickVisible = new JCheckBoxMenuItem("보이기", host.isTickVisible());
        tickVisible.addActionListener(e -> {
            host.setTickVisible(tickVisible.isSelected());
            host.repaintClock();
        });
        JMenu tickMenu = new JMenu("눈금");
        tickMenu.add(tickVisible);
        tickMenu.addSeparator();
        tickMenu.add(tickItem);
        tickMenu.add(tickReset);
        menu.add(tickMenu);

        // ── 바늘 조정 ───────────────────────────────────────────
        JCheckBoxMenuItem secondVisibleItem = new JCheckBoxMenuItem("초침 보이기", host.isSecondVisible());
        secondVisibleItem.addActionListener(e -> {
            host.setSecondVisible(secondVisibleItem.isSelected());
            host.repaintClock();
        });

        JMenuItem hourColorItem = new JMenuItem("시침 색깔 변경...");
        hourColorItem.addActionListener(e -> {
            Color c = JColorChooser.showDialog(host.getOwnerComponent(), "시침 색상", host.getHourColor());
            if (c != null) { host.setHourColor(c); host.repaintClock(); }
        });

        JMenuItem minuteColorItem = new JMenuItem("분침 색깔 변경...");
        minuteColorItem.addActionListener(e -> {
            Color c = JColorChooser.showDialog(host.getOwnerComponent(), "분침 색상", host.getMinuteColor());
            if (c != null) { host.setMinuteColor(c); host.repaintClock(); }
        });

        JMenuItem secondColorItem = new JMenuItem("초침 색깔 변경...");
        secondColorItem.addActionListener(e -> {
            Color c = JColorChooser.showDialog(host.getOwnerComponent(), "초침 색상", host.getSecondColor());
            if (c != null) { host.setSecondColor(c); host.repaintClock(); }
        });

        JMenu handMenu = new JMenu("바늘 조정");
        handMenu.add(secondVisibleItem);
        handMenu.addSeparator();
        handMenu.add(secondColorItem);
        handMenu.add(minuteColorItem);
        handMenu.add(hourColorItem);
        menu.add(handMenu);


        JMenu fontMenu = new JMenu("1...12 폰트");
        String[] fonts = {"Georgia", "Times New Roman", "Arial", "Tahoma", "Verdana",
                "Palatino Linotype", "Book Antiqua", "Garamond",
                "Malgun Gothic", "굴림", "돋움"};
        JMenu fontFamilyMenu = new JMenu("폰트 종류");
        ButtonGroup fntg = new ButtonGroup();
        for (String fn : fonts) {
            JRadioButtonMenuItem fi = new JRadioButtonMenuItem(fn,
                    host.getNumberFont().getFamily().equals(fn));
            fntg.add(fi);
            fi.addActionListener(e -> {
                host.setNumberFont(new Font(fn, Font.BOLD, host.getNumberFont().getSize()));
                host.repaintClock();
            });
            fontFamilyMenu.add(fi);
        }
        fontMenu.add(fontFamilyMenu);

        JMenu fontSizeMenu = new JMenu("폰트 크기");
        for (int sz : new int[]{8, 10, 12, 14, 16, 18, 20, 22, 24, 28, 32}) {
            JMenuItem si = new JMenuItem(String.valueOf(sz));
            si.addActionListener(e -> {
                host.setNumberFont(host.getNumberFont().deriveFont((float) sz));
                host.repaintClock();
            });
            fontSizeMenu.add(si);
        }
        fontMenu.add(fontSizeMenu);

        JMenuItem numColorItem = new JMenuItem("숫자 Color...");
        numColorItem.addActionListener(e -> {
            Color def = host.getTheme().equals("Black") ? Color.WHITE : Color.BLACK;
            Color c = JColorChooser.showDialog(host.getOwnerComponent(), "숫자 색상",
                    host.getNumberColor() != null ? host.getNumberColor() : def);
            if (c != null) { host.setNumberColor(c); host.repaintClock(); }
        });
        JMenuItem numColorReset = new JMenuItem("숫자 Color 초기화");
        numColorReset.addActionListener(e -> { host.setNumberColor(null); host.repaintClock(); });
        fontMenu.add(numColorItem);
        fontMenu.add(numColorReset);

        // ── 숫자 보이기 / 제거 토글 ───────────────────────────
        fontMenu.addSeparator();
        JCheckBoxMenuItem showNumbersItem = new JCheckBoxMenuItem("보이기", host.isShowNumbers());
        showNumbersItem.addActionListener(e -> {
            host.setShowNumbers(showNumbersItem.isSelected());
            host.repaintClock();
        });
        fontMenu.add(showNumbersItem);

        menu.add(fontMenu);

        // ── Animation ──────────────────────────────────────────
        JMenu animMenu = new JMenu("Animation");
        for (int v : host.getIntervals()) {
            JMenuItem ai = new JMenuItem(v == 0 ? "0 (중단)" : String.valueOf(v));
            ai.addActionListener(e -> { host.setAnimInterval(v); host.startAnimTimer(); });
            animMenu.add(ai);
        }
        menu.add(animMenu);

        // ── 차임벨 ─────────────────────────────────────────────
        JMenuItem chimeItem = new JMenuItem("차임벨 설정...");
        chimeItem.addActionListener(e -> host.showChimeDialog());
        menu.add(chimeItem);

        // ── 알람 관리 ───────────────────────────────────────────
        JMenuItem alarmItem = new JMenuItem("알람 관리...");
        alarmItem.addActionListener(e -> host.showAlarmDialog());
        menu.add(alarmItem);

        // ── 지금 Gmail 보내기 ──────────────────────────────────
        menu.add(buildSendGmailItem());

        // ── 카카오톡 ────────────────────────────────────────────
        JMenu kakaoMenu = buildKakaoMenu(new JMenu("카카오톡..."));
        menu.add(kakaoMenu);

        // ── 텔레그램 ────────────────────────────────────────────
        JMenu telegramMenu = new JMenu("텔레그램");
        JMenuItem telegramSendItem = new JMenuItem("✈️ 텔레그램 보내기...");
        telegramSendItem.addActionListener(e -> host.showTelegramDialog());
        telegramMenu.add(telegramSendItem);
        JMenuItem telegramHelpItem = new JMenuItem("📖 텔레그램 설정 안내...");
        telegramHelpItem.addActionListener(e -> {
            // 기존 showTelegramHelp() 대신 블로그 URL을 바로 브라우저로 열기 (요구사항 3)
            try {
                java.awt.Desktop.getDesktop().browse(
                    new java.net.URI("https://blog.naver.com/garpsu/224213397702"));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(host.getOwnerComponent(),
                    "브라우저 열기 실패: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            }
        });
        telegramMenu.add(telegramHelpItem);
        menu.add(telegramMenu);

        // ── Slide show ─────────────────────────────────────────
        JMenu slideMenu = buildSlideShowMenu(new JMenu("Slide show"));
        menu.add(slideMenu);

        // ── 생활정보 ────────────────────────────────────────────
        JMenu lifeMenu = new JMenu("생활정보");
        String[][] lifeLinks = {
            {"🌏 생활천문관",  "https://astro.kasi.re.kr/index"},
            {"🕐 TIME.IS",    "https://time.is"},
            {"🕰 TIME&DATE",  "https://www.timeanddate.com/worldclock/full.html"},
            {"🌤 날씨",        "https://www.weather.go.kr/w/index.do"},
        };
        for (String[] entry : lifeLinks) {
            JMenuItem li = new JMenuItem(entry[0]);
            final String url = entry[1];
            li.addActionListener(e -> {
                try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(host.getOwnerComponent(),
                        "브라우저 열기 실패: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
                }
            });
            lifeMenu.add(li);
        }
        menu.add(lifeMenu);
        menu.addSeparator();

        // ── Close / Restart / EXIT ─────────────────────────────
        // 1번: [시스템] 서브메뉴로 이동
        JMenu systemMenu = new JMenu("시스템...");

        // 2번: About - 블로그 URL을 백그라운드에서 열고, 다이얼로그를 앞에 표시
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> {
            // 브라우저는 별도 스레드로 열어 다이얼로그 포커스를 빼앗기지 않게 함
            new Thread(() -> {
                try {
                    java.awt.Desktop.getDesktop().browse(
                        new java.net.URI("https://blog.naver.com/garpsu/224213400580"));
                } catch (Exception ex) {
                    System.out.println("[About] 브라우저 열기 실패: " + ex.getMessage());
                }
            }, "About-Browser").start();
            host.showAbout();
        });
        systemMenu.add(aboutItem);

        // 4번: Log - 로그 파일을 기본 브라우저로 표시
        JMenuItem logItem = new JMenuItem("Log...");
        logItem.addActionListener(e -> {
            String logPath = host.getLogFilePath();
            if (logPath == null || logPath.isEmpty()) {
                JOptionPane.showMessageDialog(host.getOwnerComponent(),
                    "로그 파일 경로를 찾을 수 없습니다.", "Log", JOptionPane.WARNING_MESSAGE);
                return;
            }
            java.io.File logFile = new java.io.File(logPath);
            if (!logFile.exists()) {
                JOptionPane.showMessageDialog(host.getOwnerComponent(),
                    "로그 파일이 존재하지 않습니다.\n" + logPath, "Log", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                String logText;
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(
                            new java.io.FileInputStream(logFile), "UTF-8"))) {
                    StringBuilder sbLog = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sbLog.append(line).append("\n");
                    logText = sbLog.toString();
                }
                String escaped = logText
                    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                java.io.File htmlFile = java.io.File.createTempFile("applog_", ".html");
                htmlFile.deleteOnExit();
                try (java.io.PrintWriter pw = new java.io.PrintWriter(
                        new java.io.OutputStreamWriter(
                            new java.io.FileOutputStream(htmlFile), "UTF-8"))) {
                    pw.println("<!DOCTYPE html><html><head>");
                    pw.println("<meta charset='UTF-8'>");
                    pw.println("<title>끝판왕 로그</title>");
                    pw.println("<style>");
                    pw.println("body { font-family: 'Consolas','Malgun Gothic',monospace;");
                    pw.println("       background:#0d0d0d; color:#c8ffc8;");
                    pw.println("       padding:20px; line-height:1.6; }");
                    pw.println("pre  { white-space:pre-wrap; font-size:13px; }");
                    pw.println("</style></head><body><pre>");
                    pw.println(escaped);
                    pw.println("</pre></body></html>");
                }
                java.awt.Desktop.getDesktop().browse(htmlFile.toURI());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(host.getOwnerComponent(),
                    "로그 파일 열기 실패: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            }
        });
        systemMenu.add(logItem);

        // 기본설정파일 - ini 파일을 브라우저로 표시
        JMenuItem iniItem = new JMenuItem("⚙️ 기본설정파일...");
        iniItem.addActionListener(e -> {
            String iniPath = host.getConfigFilePath();
            java.io.File iniFile = new java.io.File(iniPath);
            if (!iniFile.exists()) {
                JOptionPane.showMessageDialog(host.getOwnerComponent(),
                    "설정 파일이 존재하지 않습니다.\n" + iniPath, "기본설정파일", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                String iniText;
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(
                            new java.io.FileInputStream(iniFile), "UTF-8"))) {
                    StringBuilder sbIni = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sbIni.append(line).append("\n");
                    iniText = sbIni.toString();
                }
                String escaped = iniText
                    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                java.io.File htmlFile = java.io.File.createTempFile("appcfg_", ".html");
                htmlFile.deleteOnExit();
                try (java.io.PrintWriter pw = new java.io.PrintWriter(
                        new java.io.OutputStreamWriter(
                            new java.io.FileOutputStream(htmlFile), "UTF-8"))) {
                    pw.println("<!DOCTYPE html><html><head>");
                    pw.println("<meta charset='UTF-8'>");
                    pw.println("<title>끝판왕 설정파일</title>");
                    pw.println("<style>");
                    pw.println("body { font-family: 'Consolas','Malgun Gothic',monospace;");
                    pw.println("       background:#0d0d0d; color:#ffe066;");
                    pw.println("       padding:20px; line-height:1.6; }");
                    pw.println("pre  { white-space:pre-wrap; font-size:13px; }");
                    pw.println("</style></head><body><pre>");
                    pw.println(escaped);
                    pw.println("</pre></body></html>");
                }
                java.awt.Desktop.getDesktop().browse(htmlFile.toURI());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(host.getOwnerComponent(),
                    "설정 파일 열기 실패: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            }
        });
        systemMenu.add(iniItem);

        systemMenu.addSeparator();

        // ── 트레이로 보내기 ────────────────────────────────────
        JMenuItem trayItem = new JMenuItem("🗕 트레이로 보내기");
        trayItem.addActionListener(e -> host.sendToTray());
        systemMenu.add(trayItem);

        // ── PC 부팅 시 자동 실행 ───────────────────────────────
        boolean isAutoStart = host.checkAutoStart();
        JCheckBoxMenuItem autoStartItem = new JCheckBoxMenuItem("🚀 PC 부팅 시 자동 실행", isAutoStart);
        autoStartItem.addActionListener(e -> host.autoStartItemActionListener(autoStartItem));
        systemMenu.add(autoStartItem);

        systemMenu.addSeparator();

        JMenuItem closeItem = new JMenuItem("Close");
        closeItem.addActionListener(e -> host.disposeInstance());
        systemMenu.add(closeItem);

        JMenuItem restartItem = new JMenuItem("🔄 Restart");
        restartItem.addActionListener(e -> host.restartApp());
        systemMenu.add(restartItem);

        JMenuItem exitItem = new JMenuItem("EXIT");
        exitItem.addActionListener(e -> host.exitAll());
        systemMenu.add(exitItem);

        menu.add(systemMenu);

        return menu;
    }

    // ── SlideShow 서브메뉴 ────────────────────────────────────────
    private JMenu buildSlideShowMenu(JMenu slideMenu) {
        JCheckBoxMenuItem slideOnOff = new JCheckBoxMenuItem("Enable",
                host.isSlideRunning());
        slideOnOff.addActionListener(e -> {
            if (slideOnOff.isSelected()) {
                if (host.isSlideImagesEmpty() && !host.getSlideFolder().isEmpty()) {
                    host.loadSlideImages();  // 폴더는 있지만 이미지가 안 로드된 경우 재로드
                }
                if (host.isSlideImagesEmpty()) {
                    host.prepareMessageBox();
                    JOptionPane.showMessageDialog(null,
                            "먼저 폴더를 선택하세요.", "Slideshow", JOptionPane.WARNING_MESSAGE);
                    slideOnOff.setSelected(false);
                    return;
                }
                host.startSlideTimer();
            } else {
                host.stopSlideTimer();
            }
            host.saveConfig();
        });
        slideMenu.add(slideOnOff);
        slideMenu.addSeparator();

        JMenuItem folderItem = new JMenuItem("폴더 선택...");
        folderItem.addActionListener(e -> host.showFolderChooser(slideOnOff));
        slideMenu.add(folderItem);

        JMenu slideIntervalMenu = new JMenu("간격");
        ButtonGroup sig = new ButtonGroup();
        for (int sec : new int[]{0, 1, 2, 3, 4, 5, 10, 15, 20, 30, 60}) {
            String label = sec == 0 ? "수동" : sec + "초";
            JRadioButtonMenuItem si2 = new JRadioButtonMenuItem(label, host.getSlideInterval() == sec);
            sig.add(si2);
            final int sv = sec;
            si2.addActionListener(e2 -> {
                host.setSlideInterval(sv);
                if (!host.isSlideImagesEmpty()) host.startSlideTimer();
                host.saveConfig();
            });
            slideIntervalMenu.add(si2);
        }
        slideMenu.add(slideIntervalMenu);
        slideMenu.addSeparator();

        // 오버레이 어둡기 조절
        JMenu overlayMenu = new JMenu("오버레이 어둡기");
        ButtonGroup ovg = new ButtonGroup();
        int[]    overlayVals = {0, 30, 55, 80, 120, 180};
        String[] overlayLbls = {"없음", "매우 약하게", "약하게", "보통", "강하게", "매우 강하게"};
        for (int i = 0; i < overlayVals.length; i++) {
            final int ov = overlayVals[i];
            JRadioButtonMenuItem oi = new JRadioButtonMenuItem(overlayLbls[i], host.getSlideOverlay() == ov);
            ovg.add(oi);
            oi.addActionListener(e2 -> { host.setSlideOverlay(ov); host.repaintClock(); host.saveConfig(); });
            overlayMenu.add(oi);
        }
        slideMenu.add(overlayMenu);
        slideMenu.addSeparator();

        // ── 전환 효과 서브메뉴 ─────────────────────────────────────
        JMenu fxMenu = new JMenu("전환 효과");
        ButtonGroup fxBg = new ButtonGroup();
        String[] fxKeys  = { "fade",  "zoom_in", "zoom_out", "left",  "right", "up",   "down"  };
        String[] fxNames = { "Fade",  "Zoom In", "Zoom Out", "Left",  "Right", "Up",   "Down"  };
        for (int i = 0; i < fxKeys.length; i++) {
            final String key = fxKeys[i];
            JRadioButtonMenuItem fxItem = new JRadioButtonMenuItem(
                    fxNames[i], host.getSlideEffect().equals(key));
            fxItem.addActionListener(e -> {
                host.setSlideEffect(key);
                host.saveConfig();
            });
            fxBg.add(fxItem);
            fxMenu.add(fxItem);
        }
        slideMenu.add(fxMenu);
        slideMenu.addSeparator();
        JMenuItem slidePrev = new JMenuItem("◀ 이전");
        slidePrev.addActionListener(e -> { host.advanceSlide(-1); host.repaintClock(); });
        JMenuItem slideNext = new JMenuItem("▶ 다음");
        slideNext.addActionListener(e -> { host.advanceSlide(1);  host.repaintClock(); });
        slideMenu.add(slidePrev);
        slideMenu.add(slideNext);
        return slideMenu;
    }

    // ── Global Cities 서브메뉴 ────────────────────────────────────
    private JMenu buildGlobalMenu(JMenu globalMenu) {
        String[][] cities = {
            {"Seoul","Asia/Seoul"},{"Tokyo","Asia/Tokyo"},{"Beijing","Asia/Shanghai"},
            {"Hong Kong","Asia/Hong_Kong"},{"Singapore","Asia/Singapore"},
            {"Bangkok","Asia/Bangkok"},{"Dubai","Asia/Dubai"},{"Mumbai","Asia/Kolkata"},
            {"Moscow","Europe/Moscow"},{"Istanbul","Europe/Istanbul"},
            {"Paris","Europe/Paris"},{"Berlin","Europe/Berlin"},
            {"London","Europe/London"},{"New York","America/New_York"},
            {"Chicago","America/Chicago"},{"Los Angeles","America/Los_Angeles"},
            {"Toronto","America/Toronto"},{"São Paulo","America/Sao_Paulo"},
            {"Sydney","Australia/Sydney"},{"Auckland","Pacific/Auckland"},
            {"Local","local"}
        };
        for (String[] city : cities) {
            String label = host.getCityName().equals(city[0]) ? "✓ " + city[0] : "   " + city[0];
            JMenuItem ci = new JMenuItem(label);
            final String cn = city[0];
            final String tz = city[1];
            ci.addActionListener(e -> {
                if (cn.equals(host.getCityName())) return;
                java.time.ZoneId newZone = tz.equals("local")
                        ? java.time.ZoneId.systemDefault()
                        : java.time.ZoneId.of(tz);
                host.openCityInstance(cn, newZone);
            });
            globalMenu.add(ci);
        }
        return globalMenu;
    }

    // ── 카카오톡 서브메뉴 ─────────────────────────────────────────
    // ── 스마트폰 카메라 메뉴 ─────────────────────────────────────
    private JMenu buildCameraMenuItem() {
        JMenu camMenu = new JMenu("📷 스마트폰 카메라");

        JMenuItem camStart = new JMenuItem("▶ 폰 카메라 연결");
        camStart.addActionListener(e -> {
            // 저장된 URL을 기본값으로 표시
            String input = (String) JOptionPane.showInputDialog(
                host.getOwnerComponent(),
                "<html><b>IP Webcam 스트림 주소 입력</b><br><br>"
                + "Android에 <b>IP Webcam</b> 앱 설치 후 서버 시작하면<br>"
                + "화면에 주소가 표시됩니다. /video 를 끝에 붙이세요.<br><br>"
                + "예: http://192.168.0.5:8080/video</html>",
                "스마트폰 카메라",
                JOptionPane.PLAIN_MESSAGE,
                null, null, host.getCameraUrl());
            if (input == null || input.trim().isEmpty()) return;
            String url = input.trim();
            if (!url.contains("/video") && !url.contains("/mjpeg"))
                url = url.replaceAll("/+$", "") + "/video";
            host.setCameraUrl(url);   // INI에 저장
            host.stopSlideTimer();
            host.setGalaxyMode(false);
            host.setMatrixMode(false);
            host.setRainMode(false); host.setSnowMode(false); host.setFireMode(false);
            host.setSparkleMode(false); host.setBubbleMode(false);
            host.setBgColor(null);
            host.setCameraMode(true);
            host.startCamera(url);
            host.repaintClock();
        });

        JMenuItem camCapture = new JMenuItem("📸 이미지 저장");
        camCapture.addActionListener(e -> {
            if (!host.isCameraMode()) {
                JOptionPane.showMessageDialog(host.getOwnerComponent(),
                    "카메라가 실행 중이 아닙니다.", "이미지 저장", JOptionPane.WARNING_MESSAGE);
                return;
            }
            host.captureCamera();
        });

        JMenuItem camStop = new JMenuItem("⏹ 폰 카메라 중지");
        camStop.addActionListener(e -> {
            host.stopCamera();
            host.setBgColor(null);
            host.repaintClock();
        });

        JMenuItem camInstall = new JMenuItem("📲 IP WebCam 설치 (Play Store)");
        camInstall.addActionListener(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(
                    new java.net.URI("https://play.google.com/store/apps/details?id=com.pas.webcam&pcampaignid=web_share"));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(host.getOwnerComponent(),
                    "브라우저 열기 실패: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            }
        });

        camMenu.add(camStart);
        camMenu.add(camCapture);
        camMenu.add(camStop);
        camMenu.addSeparator();
        camMenu.add(camInstall);
        camMenu.addSeparator();
        camMenu.add(buildCameraGuideItem());
        return camMenu;
    }

/*
    private JMenuItem buildCameraGuideItem() {
        JMenuItem guideItem = new JMenuItem("📖 사용방법 안내");
        guideItem.addActionListener(e -> {
            // ── cameraGuide.txt 읽기 ──────────────────────────────
            String guideText = readCameraGuide();
            if (guideText == null) return; // 오류 메시지는 readCameraGuide 내부에서 표시

            // ── 이메일 + 텔레그램 병렬 전송 ──────────────────────
            GmailSender gmail = host.getGmail();
            TelegramBot tg    = host.getTg();

            boolean emailOk = gmail.isConfigured() && !gmail.lastTo.isEmpty();
            boolean tgOk    = tg.polling && !tg.botToken.isEmpty() && !tg.myChatId.isEmpty();

            if (!emailOk && !tgOk) {
                JOptionPane.showMessageDialog(host.getOwnerComponent(),
                    "이메일과 텔레그램이 모두 설정되어 있지 않습니다.\n"
                    + "알람 관리에서 먼저 설정해 주세요.",
                    "안내 전송 실패", JOptionPane.WARNING_MESSAGE);
                return;
            }

            final String subject = "[끝판왕] 스마트폰 카메라 사용방법 안내";
            final String body    = guideText;

            if (emailOk) {
                new Thread(() -> {
                    try {
                        gmail.send(gmail.lastTo, subject, body);
                        System.out.println("[CameraGuide] 이메일 전송 완료");
                    } catch (Exception ex) {
                        System.out.println("[CameraGuide] 이메일 전송 실패: " + ex.getMessage());
                    }
                }, "CameraGuide-Email").start();
            }

            if (tgOk) {
                new Thread(() -> {
                    try {
                        tg.send(tg.myChatId, "📖 " + subject + "\n\n" + body);
                        System.out.println("[CameraGuide] 텔레그램 전송 완료");
                    } catch (Exception ex) {
                        System.out.println("[CameraGuide] 텔레그램 전송 실패: " + ex.getMessage());
                    }
                }, "CameraGuide-Telegram").start();
            }

            // ── 전송 완료 메시지 ──────────────────────────────────
            StringBuilder sent = new StringBuilder();
            if (emailOk)   sent.append("이메일");
            if (emailOk && tgOk) sent.append("과 ");
            if (tgOk)      sent.append("텔레그램 메시지");
            sent.append("로 카메라 사용방법 안내문을 보냈습니다.");

            JOptionPane.showMessageDialog(host.getOwnerComponent(),
                sent.toString(), "안내 전송", JOptionPane.INFORMATION_MESSAGE);
        });
        return guideItem;
    }
*/
    /** cameraGuide.txt 읽기. 실패 시 오류 팝업 후 null 반환 */
	/*
    private String readCameraGuide() {
        // 실행 파일 옆 → 현재 디렉토리 순서로 탐색
        File f = new File(AppLogger.getExeFilePath()).getParentFile();
        File txtFile = (f != null) ? new File(f, "cameraGuide.txt")
                                   : new File("cameraGuide.txt");
        if (!txtFile.exists()) txtFile = new File("cameraGuide.txt");

        if (!txtFile.exists()) {
            JOptionPane.showMessageDialog(host.getOwnerComponent(),
                "cameraGuide.txt 파일을 찾을 수 없습니다.\n"
                + "실행 파일과 같은 폴더에 cameraGuide.txt 를 넣어주세요.",
                "파일 없음", JOptionPane.ERROR_MESSAGE);
            return null;
        }

		showCameraGuide (this); 	

		try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(
                    new java.io.FileInputStream(txtFile), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            return sb.toString().trim();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(host.getOwnerComponent(),
                "cameraGuide.txt 읽기 실패: " + ex.getMessage(),
                "오류", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }
	*/


    // ── 카매라 안내 HTML 파일 열기 ─────────────────────────────
	/*
    public void showCameraGuide(java.awt.Component parent) {
        try {
            // cameraGuide.txt 탐색: 실행 파일 옆 폴더 우선
            java.io.File txtFile = new java.io.File("cameraGuide.txt");
            if (!txtFile.exists()) {
                try {
                    java.security.CodeSource cs = getClass().getProtectionDomain().getCodeSource();
                    if (cs != null) {
                        java.io.File base = new java.io.File(cs.getLocation().toURI());
                        java.io.File dir  = base.isDirectory() ? base : base.getParentFile();
                        txtFile = new java.io.File(dir, "cameraGuide.txt");
                    }
                } catch (Exception ignored) {}
            }

            String content;
            if (txtFile.exists()) {
                java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(
                        new java.io.FileInputStream(txtFile), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
                br.close();
                content = sb.toString();
            } else {
                content = "cameraGuide.txt 파일을 찾을 수 없습니다.\n실행 파일과 같은 폴더에 cameraGuide.txt 를 넣어주세요.";
            }

            // URL → <a href> 링크 변환 후 HTML 생성
            String escaped = content
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            String html = escaped.replaceAll(
                "(https?://[\\S]+)",
                "<a href='$1' target='_blank'>$1</a>");

            java.io.File htmlFile = java.io.File.createTempFile("camera_Guide_", ".html");
            htmlFile.deleteOnExit();
            try (java.io.PrintWriter pw = new java.io.PrintWriter(
                    new java.io.OutputStreamWriter(
                        new java.io.FileOutputStream(htmlFile), "UTF-8"))) {
                pw.println("<!DOCTYPE html><html><head>");
                pw.println("<meta charset='UTF-8'>");
                pw.println("<title>스마트 폰 카매라 설정 안내</title>");
                pw.println("<style>");
                pw.println("body { font-family: 'Malgun Gothic', monospace;");
                pw.println("       background:#1a1a2e; color:#e0e0e0;");
                pw.println("       padding:30px; line-height:1.7; }");
                pw.println("pre  { white-space:pre-wrap; font-size:14px; }");
                pw.println("a    { color:#4fc3f7; font-weight:bold; }");
                pw.println("a:hover { color:#81d4fa; }");
                pw.println("</style></head><body><pre>");
                pw.println(html);
                pw.println("</pre></body></html>");
            }
            java.awt.Desktop.getDesktop().browse(htmlFile.toURI());

        } catch (Exception e) {
            javax.swing.JOptionPane.showMessageDialog(parent,
                "안내 파일 열기 실패: " + e.getMessage(),
                "오류", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }
	*/



    private JMenuItem buildCameraGuideItem() {
        JMenuItem guideItem = new JMenuItem("📖 사용방법 안내");
        guideItem.addActionListener(e -> {
            final String GUIDE_URL     = "https://blog.naver.com/garpsu/224213426659";
            final String GUIDE_MSG     = "PC와 스마트폰 카메라 연결 방법 안내 : " + GUIDE_URL;
            final String GUIDE_SUBJECT = "[끝판왕] PC와 스마트폰 카메라 연결 방법 안내";

            // ── ① 브라우저로 안내 URL 열기 ───────────────────────
            new Thread(() -> {
                try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI(GUIDE_URL));
                } catch (Exception ex) {
                    System.out.println("[CameraGuide] 브라우저 열기 실패: " + ex.getMessage());
                }
            }, "CameraGuide-Browser").start();

            // ── ② 텔레그램 + 이메일 병렬 전송 ───────────────────
            GmailSender gmail = host.getGmail();
            TelegramBot tg    = host.getTg();

            boolean emailOk = gmail.isConfigured() && !gmail.lastTo.isEmpty();
            boolean tgOk    = tg.polling && !tg.botToken.isEmpty() && !tg.myChatId.isEmpty();

            if (emailOk) {
                new Thread(() -> {
                    try {
                        gmail.send(gmail.lastTo, GUIDE_SUBJECT, GUIDE_MSG);
                        System.out.println("[CameraGuide] 이메일 전송 완료");
                    } catch (Exception ex) {
                        System.out.println("[CameraGuide] 이메일 전송 실패: " + ex.getMessage());
                    }
                }, "CameraGuide-Email").start();
            }

            if (tgOk) {
                new Thread(() -> {
                    try {
                        tg.send(tg.myChatId, GUIDE_MSG);
                        System.out.println("[CameraGuide] 텔레그램 전송 완료");
                    } catch (Exception ex) {
                        System.out.println("[CameraGuide] 텔레그램 전송 실패: " + ex.getMessage());
                    }
                }, "CameraGuide-Telegram").start();
            }

            // ── ③ 완료 다이얼로그 ────────────────────────────────
            JOptionPane.showMessageDialog(host.getOwnerComponent(),
                "PC와 스마트폰 카메라 연결 방법을 텔레그램 메시지로 보냈습니다.\n"
                + "안내문에 따라서 스마트폰에 [IP WebCam] 앱을 설치하세요.",
                "스마트폰 카메라 안내", JOptionPane.INFORMATION_MESSAGE);
        });
        return guideItem;
    }


    private JMenu buildItsCctvMenuItem() {
        JMenu cctvMenu = new JMenu("🚦 ITS 교통 CCTV");

        // ── API 키 설정 ──────────────────────────────────────
        JMenuItem keyItem = new JMenuItem("🔑 API 키 설정...");
        keyItem.addActionListener(e -> {
            ItsCctvManager mgr = host.getItsCctv();
            String cur = mgr.getApiKey();

            // JTextField 에 기존 키를 초기값으로 직접 설정
            JTextField keyField = new JTextField(cur, 36);
            Object[] msg = {
                "<html><b>ITS 국가교통정보센터 API 키 입력</b><br><br>"
                + "발급처: <b>https://www.its.go.kr</b><br>"
                + "회원가입 → 오픈데이터 → CCTV 화상자료 → 인증키 신청<br><br>"
                + "현재 키: " + (cur.isEmpty() ? "(없음)" : cur.substring(0, Math.min(8, cur.length())) + "...") + "</html>",
                keyField
            };
            int result = JOptionPane.showConfirmDialog(
                host.getOwnerComponent(), msg, "ITS API 키 설정",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) return;
            String input = keyField.getText().trim();
            mgr.setApiKey(input);
            host.saveConfig();
            JOptionPane.showMessageDialog(host.getOwnerComponent(),
                "API 키가 저장되었습니다.", "ITS CCTV", JOptionPane.INFORMATION_MESSAGE);
        });
        cctvMenu.add(keyItem);
        cctvMenu.addSeparator();

        // ── CCTV 목록 조회 + 연결 ───────────────────────────
        JMenuItem connectItem = new JMenuItem("▶ CCTV 목록 조회 및 연결");
        connectItem.addActionListener(e -> {
            ItsCctvManager mgr = host.getItsCctv();
            if (mgr.getApiKey().isEmpty()) {
                JOptionPane.showMessageDialog(host.getOwnerComponent(),
                    "먼저 API 키를 설정하세요.", "ITS CCTV", JOptionPane.WARNING_MESSAGE);
                return;
            }
            connectItem.setEnabled(false);
            connectItem.setText("⏳ 조회 중...");
            mgr.fetchList(
                () -> {
                    // 성공 - 목록에서 선택
                    connectItem.setEnabled(true);
                    connectItem.setText("▶ CCTV 목록 조회 및 연결");
                    java.util.List<ItsCctvManager.CctvItem> items = mgr.getItems();
                    if (items.isEmpty()) {
                        JOptionPane.showMessageDialog(host.getOwnerComponent(),
                            "조회된 CCTV가 없습니다.", "ITS CCTV", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    ItsCctvManager.CctvItem selected =
                        showCctvSelectDialog(host.getOwnerComponent(), items, mgr);
                    if (selected == null) return;
                    int idx = items.indexOf(selected);
                    // 다른 배경 모드 해제 후 시작
                    host.stopSlideTimer(); host.stopCamera();
                    host.setGalaxyMode(false); host.setMatrixMode(false);
                    host.setRainMode(false);   host.setSnowMode(false);
                    host.setFireMode(false);   host.setSparkleMode(false);
                    host.setBubbleMode(false); host.setBgColor(null);
                    host.stopShowTimer();
                    host.startItsCctv();
                    mgr.select(idx);
                    host.saveConfig();
                },
                err -> {
                    connectItem.setEnabled(true);
                    connectItem.setText("▶ CCTV 목록 조회 및 연결");
                    JOptionPane.showMessageDialog(host.getOwnerComponent(),
                        "CCTV 조회 실패:\n" + err
                        + "\n\nAPI 키와 네트워크 연결을 확인하세요.",
                        "ITS CCTV 오류", JOptionPane.ERROR_MESSAGE);
                });
        });
        cctvMenu.add(connectItem);

        // ── 이전 / 다음 ──────────────────────────────────────
        JMenuItem prevItem = new JMenuItem("◀ 이전 카메라");
        prevItem.addActionListener(e -> {
            ItsCctvManager mgr = host.getItsCctv();
            if (!mgr.isRunning() || mgr.getItems().isEmpty()) return;
            mgr.prev();
            System.out.println("[ItsCctv] 이전: " + mgr.currentName());
        });
        cctvMenu.add(prevItem);

        JMenuItem nextItem = new JMenuItem("▶ 다음 카메라");
        nextItem.addActionListener(e -> {
            ItsCctvManager mgr = host.getItsCctv();
            if (!mgr.isRunning() || mgr.getItems().isEmpty()) return;
            mgr.next();
            System.out.println("[ItsCctv] 다음: " + mgr.currentName());
        });
        cctvMenu.add(nextItem);

        cctvMenu.addSeparator();

        // ── 중지 ─────────────────────────────────────────────
        JMenuItem stopItem = new JMenuItem("⏹ CCTV 중지");
        stopItem.addActionListener(e -> {
            host.stopItsCctv();
            host.repaintClock();
        });
        cctvMenu.add(stopItem);

        cctvMenu.addSeparator();

        // ── 안내 ─────────────────────────────────────────────
        JMenuItem guideItem = new JMenuItem("📖 ITS API 신청 안내");
        guideItem.addActionListener(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(
                    new java.net.URI("https://www.its.go.kr/opendata/opendataList?service=cctv"));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(host.getOwnerComponent(),
                    "브라우저 열기 실패: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
            }
        });
        cctvMenu.add(guideItem);

        return cctvMenu;
    }

    /**
     * ITS 교통 CCTV 선택 다이얼로그.
     * "도시 이름" 텍스트 필드로 CCTV 목록을 실시간 필터링한다.
     */
    private ItsCctvManager.CctvItem showCctvSelectDialog(
            java.awt.Component owner,
            java.util.List<ItsCctvManager.CctvItem> allItems,
            ItsCctvManager mgr) {

        java.awt.Window window = (owner instanceof java.awt.Window)
            ? (java.awt.Window) owner
            : javax.swing.SwingUtilities.getWindowAncestor(owner);
        JDialog dlg = new JDialog(window, "ITS 교통 CCTV 선택",
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setLayout(new BorderLayout(6, 6));
        dlg.getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ── 상단: 도시 이름 필터 ─────────────────────────────────
        JPanel filterPanel = new JPanel(new BorderLayout(6, 0));
        filterPanel.setBorder(BorderFactory.createTitledBorder("도시 이름"));
        JTextField filterField = new JTextField();
        filterField.setToolTipText("도시·지역 이름 입력 후 [필터] 버튼 클릭 (예: 서울, 부산, 고양)");
        filterPanel.add(filterField, BorderLayout.CENTER);
        JButton filterBtn = new JButton("필터");
        filterPanel.add(filterBtn, BorderLayout.EAST);
        dlg.add(filterPanel, BorderLayout.NORTH);

        // ── 중앙: CCTV 목록 ──────────────────────────────────────
        DefaultListModel<ItsCctvManager.CctvItem> listModel = new DefaultListModel<>();
        allItems.forEach(listModel::addElement);
        JList<ItsCctvManager.CctvItem> cctvList = new JList<>(listModel);
        cctvList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        cctvList.setVisibleRowCount(27);
        if (!allItems.isEmpty()) cctvList.setSelectedIndex(0);
        JScrollPane scroll = new JScrollPane(cctvList);
        scroll.setPreferredSize(new java.awt.Dimension(480, 570));
        dlg.add(scroll, BorderLayout.CENTER);

        // ── 필터 로직 (버튼 클릭 시) ─────────────────────────────
        JLabel countLabel = new JLabel("총 " + allItems.size() + "개");
        countLabel.setForeground(java.awt.Color.GRAY);
        Runnable applyFilter = () -> {
            String keyword = filterField.getText().trim();
            listModel.clear();
            java.util.List<ItsCctvManager.CctvItem> filtered;
            if (keyword.isEmpty()) {
                filtered = allItems;
            } else {
                filtered = new java.util.ArrayList<>();
                for (ItsCctvManager.CctvItem it : allItems) {
                    if (it.name.contains(keyword)) filtered.add(it);
                }
            }
            for (ItsCctvManager.CctvItem it : filtered) listModel.addElement(it);
            countLabel.setText((keyword.isEmpty() ? "총 " : "필터 결과 ")
                    + filtered.size() + "개");
            if (!listModel.isEmpty()) cctvList.setSelectedIndex(0);
        };
        filterBtn.addActionListener(e -> applyFilter.run());
        // Enter 키로도 필터 실행
        filterField.addActionListener(e -> applyFilter.run());

        // ── 하단: 확인 / 취소 ────────────────────────────────────
        final ItsCctvManager.CctvItem[] result = {null};
        JButton okBtn     = new JButton("  확인  ");
        JButton cancelBtn = new JButton("  취소  ");
        okBtn.addActionListener(e -> {
            result[0] = cctvList.getSelectedValue();
            if (result[0] != null) {
                // 현재 필터 키워드를 mgr 에 반영 → PgUp/PgDn이 필터 목록 내 순환
                mgr.setFilter(filterField.getText().trim());
            }
            dlg.dispose();
        });
        cancelBtn.addActionListener(e -> dlg.dispose());
        // 더블클릭으로도 선택 가능
        cctvList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && cctvList.getSelectedValue() != null) {
                    result[0] = cctvList.getSelectedValue();
                    mgr.setFilter(filterField.getText().trim());
                    dlg.dispose();
                }
            }
        });
        // Enter 키로 확인
        dlg.getRootPane().setDefaultButton(okBtn);

        JPanel botPanel = new JPanel(new BorderLayout(6, 0));
        JPanel botRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        botRight.add(okBtn);
        botRight.add(cancelBtn);
        botPanel.add(countLabel, BorderLayout.WEST);
        botPanel.add(botRight, BorderLayout.EAST);
        dlg.add(botPanel, BorderLayout.SOUTH);

        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);  // modal: 여기서 블록됨
        return result[0];
    }

    private JMenu buildKakaoMenu(JMenu kakaoMenu) {
        String kakaoLoginStatus = host.getKakaoAccessToken().isEmpty()
                ? "🔑 카카오 로그인..." : "✅ 카카오 로그인됨";
        JMenuItem kakaoLoginItem = new JMenuItem(kakaoLoginStatus);
        kakaoLoginItem.addActionListener(e -> {
            host.prepareMessageBox();
            host.kakaoLogin();
        });
        kakaoMenu.add(kakaoLoginItem);
        kakaoMenu.addSeparator();

        JMenuItem kakaoSendItem = new JMenuItem("📱 나에게 메시지 보내기...");
        kakaoSendItem.addActionListener(e -> {
            if (host.getKakaoAccessToken().isEmpty()) {
                host.prepareMessageBox();
                JOptionPane.showMessageDialog(null,
                        "먼저 카카오 로그인을 해주세요.", "카카오톡", JOptionPane.WARNING_MESSAGE);
                return;
            }
            host.prepareMessageBox();
            String msg = JOptionPane.showInputDialog(null,
                    "보낼 메시지를 입력하세요:", "카카오톡 나에게 보내기", JOptionPane.PLAIN_MESSAGE);
            if (msg != null && !msg.trim().isEmpty()) {
                new Thread(() -> host.kakaoSend("📱 [끝판왕] 시계", msg.trim()), "KakaoSend").start();
            }
        });
        kakaoMenu.add(kakaoSendItem);
        kakaoMenu.addSeparator();

        JMenuItem kakaoGuideItem = new JMenuItem("📖 설정 안내...");
        kakaoGuideItem.addActionListener(e -> host.showKakaoGuide());
        kakaoMenu.add(kakaoGuideItem);
        return kakaoMenu;
    }

    // ── Gmail 보내기 항목 (별도 메서드로 가독성 향상) ──────────────
    private JMenuItem buildSendGmailItem() {
        JMenuItem sendGmailItem = new JMenuItem("📧 지금 Gmail 보내기");
        sendGmailItem.addActionListener(e -> {
            GmailSender gmail = host.getGmail();
            if (gmail.from.isEmpty() || gmail.pass.isEmpty()) {
                JOptionPane.showMessageDialog(host.getOwnerComponent(),
                        "Gmail 주소와 앱 비밀번호를 먼저 입력하세요.", "Gmail 보내기",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            // 입력 서브 다이얼로그
            JPanel ip = new JPanel(new GridBagLayout());
            GridBagConstraints ig = new GridBagConstraints();
            ig.insets = new Insets(4, 4, 4, 4);
            ig.anchor = GridBagConstraints.WEST;

            ig.gridx = 0; ig.gridy = 0;
            ip.add(new JLabel("수신자 이메일:"), ig);
            JTextField toField = new JTextField(20);
            toField.setText(!gmail.lastTo.isEmpty() ? gmail.lastTo : gmail.from);
            ig.gridx = 1; ig.fill = GridBagConstraints.HORIZONTAL; ig.weightx = 1;
            ip.add(toField, ig);

            ig.gridx = 0; ig.gridy = 1; ig.fill = GridBagConstraints.NONE; ig.weightx = 0;
            ip.add(new JLabel("제목:"), ig);
            JTextField subjField = new JTextField("[끝판왕] 알림", 20);
            ig.gridx = 1; ig.fill = GridBagConstraints.HORIZONTAL; ig.weightx = 1;
            ip.add(subjField, ig);

            ig.gridx = 0; ig.gridy = 2; ig.fill = GridBagConstraints.NONE; ig.weightx = 0;
            ip.add(new JLabel("내용:"), ig);
            JTextArea bodyArea = new JTextArea(4, 20);
            bodyArea.setLineWrap(true);
            bodyArea.setText(GmailSender.APP_SIGNATURE
                    + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            ig.gridx = 1; ig.fill = GridBagConstraints.BOTH; ig.weightx = 1; ig.weighty = 1;
            ip.add(new JScrollPane(bodyArea), ig);

            if (JOptionPane.showConfirmDialog(host.getOwnerComponent(), ip,
                    "📧 지금 Gmail 보내기",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

            String toAddr = toField.getText().trim();
            if (toAddr.isEmpty()) {
                JOptionPane.showMessageDialog(host.getOwnerComponent(),
                        "수신자 이메일을 입력하세요.", "Gmail 보내기", JOptionPane.WARNING_MESSAGE);
                return;
            }

            sendGmailItem.setEnabled(false);
            final String subj = subjField.getText().trim();
            final String body = bodyArea.getText().trim();

            new Thread(() -> {
                try {
                    gmail.send(toAddr, subj, body);
                    SwingUtilities.invokeLater(() -> {
                        sendGmailItem.setEnabled(true);
                        JOptionPane.showMessageDialog(host.getOwnerComponent(),
                                "✅ Gmail 전송 완료!\n수신자: " + toAddr,
                                "Gmail 보내기", JOptionPane.INFORMATION_MESSAGE);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        sendGmailItem.setEnabled(true);
                        JOptionPane.showMessageDialog(host.getOwnerComponent(),
                                "❌ 전송 실패: " + ex.getMessage()
                                + "\n\n확인사항:\n1. Gmail 앱 비밀번호 확인"
                                + "\n2. Gmail → 구글 계정 → 보안 → 앱 비밀번호",
                                "Gmail 오류", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }, "GmailSendNow").start();
        });
        return sendGmailItem;
    }

    // ═══════════════════════════════════════════════════════════
    //  HostContext 구현체 (AnalogClockSwing 에서 이동)
    // ═══════════════════════════════════════════════════════════
    public static class ClockHostContext implements HostContext {

        private final AnalogClockSwing app;

        public ClockHostContext(AnalogClockSwing app) {
            this.app = app;
        }

        // ── 상태 읽기 ───────────────────────────────────────
        @Override public boolean     isAlwaysOnTop()  { return app.alwaysOnTop; }
        @Override public boolean     isShowDigital()  { return app.showDigital; }
        @Override public boolean     isShowNumbers()  { return app.showNumbers; }
        @Override public String      getTheme()       { return app.theme; }
        @Override public float       getOpacity()     { return app.opacity; }
        @Override public Color       getBgColor()     { return app.bgColor; }
        @Override public Color       getBorderColor() { return app.borderColor; }
        @Override public int         getBorderWidth() { return app.borderWidth; }
        @Override public int         getBorderAlpha() { return app.borderAlpha; }
        @Override public boolean     isBorderVisible(){ return app.borderVisible; }
        @Override public Color       getTickColor()    { return app.tickColor; }
        @Override public boolean     isTickVisible()   { return app.tickVisible; }
        @Override public boolean     isSecondVisible() { return app.secondVisible; }
        @Override public Color       getHourColor()    { return app.hourColor; }
        @Override public Color       getMinuteColor()  { return app.minuteColor; }
        @Override public Color       getSecondColor()  { return app.secondColor; }
        @Override public Color       getNumberColor() { return app.numberColor; }
        @Override public Font        getNumberFont()  { return app.numberFont; }
        @Override public Font        getDigitalFont() { return app.digitalFont; }
        @Override public Color       getDigitalColor(){ return app.digitalColor; }
        @Override public boolean     isShowLunar()    { return app.showLunar; }
        @Override public int         getShowInterval(){ return app.showInterval; }
        @Override public int         getAnimInterval(){ return app.animInterval; }
        @Override public boolean     isChimeEnabled() { return app.chimeController.isEnabled(); }
        @Override public int[]       getIntervals()   { return AnalogClockSwing.INTERVALS; }
        @Override public boolean     checkAutoStart() { return AppRestarter.AutoStart.check(); }
        @Override public GmailSender getGmail()       { return app.gmail; }
        @Override public TelegramBot getTg()           { return app.tg; }

        // ── 상태 쓰기 ───────────────────────────────────────
        @Override public void setAlwaysOnTop(boolean v)  { app.alwaysOnTop=v; app.setAlwaysOnTop(v); }
        @Override public void setShowDigital(boolean v)  { app.showDigital=v; }
        @Override public void setShowNumbers(boolean v)  { app.showNumbers=v; }
        @Override public void setTheme(String v)         { app.theme=v; }
        @Override public void setOpacity(float v)        { app.opacity=v; try{app.setOpacity(v);}catch(Exception ignored){} }
        @Override public void setBgColor(Color v)        { app.bgColor=v; }
        @Override public String getBgImagePath()          { return app.bgImagePath != null ? app.bgImagePath : ""; }
        @Override public void setBgImage(String fullPath, java.awt.image.BufferedImage img) { app.bgImagePath = fullPath; app.bgImageCache = img; }
        @Override public boolean isGalaxyMode()          { return app.galaxyMode; }
        @Override public void setGalaxyMode(boolean v)   { app.galaxyMode=v; }
        @Override public boolean isMatrixMode()          { return app.matrixMode; }
        @Override public void setMatrixMode(boolean v)   { app.matrixMode=v; }
        @Override public boolean isRainMode()            { return app.rainMode; }
        @Override public void setRainMode(boolean v)     { app.rainMode=v; }
        @Override public boolean isSnowMode()            { return app.snowMode; }
        @Override public void setSnowMode(boolean v)     { app.snowMode=v; }
        @Override public boolean isFireMode()            { return app.fireMode; }
        @Override public void setFireMode(boolean v)     { app.fireMode=v; }
        @Override public boolean isSparkleMode()         { return app.sparkleMode; }
        @Override public void setSparkleMode(boolean v)  { app.sparkleMode=v; }
        @Override public boolean isBubbleMode()          { return app.bubbleMode; }
        @Override public void setBubbleMode(boolean v)   { app.bubbleMode=v; }
        @Override public void setTickColor(Color v)       { app.tickColor=v; }
        @Override public void setTickVisible(boolean v)   { app.tickVisible=v; }
        @Override public void setSecondVisible(boolean v) { app.secondVisible=v; }
        @Override public void setHourColor(Color v)       { app.hourColor=v; }
        @Override public void setMinuteColor(Color v)     { app.minuteColor=v; }
        @Override public void setSecondColor(Color v)     { app.secondColor=v; }
        @Override public void setNumberColor(Color v)    { app.numberColor=v; }
        @Override public void setNumberFont(Font v)      { app.numberFont=v; }
        @Override public void setDigitalFont(Font v)     { app.digitalFont=v; }
        @Override public void setDigitalColor(Color v)   { app.digitalColor=v; }
        @Override public void setShowLunar(boolean v)    { app.showLunar=v; }
        @Override public void setShowInterval(int v)     { app.showInterval=v; }
        @Override public void setAnimInterval(int v)     { app.animInterval=v; }
        @Override public void setBorderVisible(boolean v){ app.borderVisible=v; }

        // ── 액션 위임 ───────────────────────────────────────
        @Override public void repaintClock()        { app.clockPanel.repaint(); }
        @Override public void repackAndKeepCenter() { app.repackAndKeepCenter(); }
        @Override public void saveConfig()          { app.saveConfig(); }
        @Override public void startShowTimer()      { app.startShowTimer(); }
        @Override public void stopShowTimer()       { app.stopShowTimer(); }
        @Override public void startAnimTimer()      { app.startAnimTimer(); }
        @Override public void stopChime()           { app.chimeController.stopChime(); }
        @Override public void showBorderDialog()    { app.showBorderDialog(); }
        @Override public void showChimeDialog()     { app.chimeController.showChimeDialog(); }
        @Override public void showAbout()           { app.showAbout(); }
        @Override public void showTelegramDialog()  { app.showTelegramDialog(); }
        @Override public void showTelegramHelp()    { app.showTelegramHelp(); }
        @Override public void showAlarmDialog()     { app.alarmController.showAlarmDialog(); }
        @Override public void sendToTray() {
            app.setVisible(false);
            app.startHidden = true;
            app.saveConfig();
        }
        @Override public void autoStartItemActionListener(JCheckBoxMenuItem item) {
            app.autoStartItemActionListener(item);
        }
        @Override public void restartApp() { app.restartApp(); }
        @Override public void disposeInstance() {
            app.chimeController.stopChime();
            if (app.showTimer       != null) app.showTimer.stop();
            if (app.animTimer       != null) app.animTimer.stop();
            if (app.scrollTimer     != null) app.scrollTimer.stop();
            if (app.slideTransTimer != null) { app.slideTransTimer.stop(); app.slideTransTimer = null; }
            if (app.slideTimer      != null) { app.slideTimer.stop();      app.slideTimer      = null; }
            app.tg.stopPolling();
            app.startHidden = false;
            app.dispose();
            AnalogClockSwing.instanceCount--;
            if (AnalogClockSwing.instanceCount <= 0) {
                if (app.shutdownGuard != null) app.shutdownGuard.cancel();
                app.saveConfig();
                app.tg.sendShutdownNotice();
                app.gmail.sendShutdownNotice(() -> { AppLogger.close(); System.exit(0); });
            }
        }
        @Override public void exitAll() {
            if (app.shutdownGuard != null) app.shutdownGuard.cancel();
            app.saveConfig();
            app.tg.sendShutdownNotice();
            app.gmail.sendShutdownNotice(() -> { AppLogger.close(); System.exit(0); });
        }

        // ── SlideShow ───────────────────────────────────────
        @Override public boolean isSlideRunning()    { return app.slideEnabled && !app.slideImages.isEmpty(); }
        @Override public boolean isSlideImagesEmpty(){ return app.slideImages.isEmpty(); }
        @Override public String  getSlideFolder()    { return app.slideFolder; }
        @Override public void    loadSlideImages()   { app.loadSlideImages(); }
        @Override public int     getSlideInterval()  { return app.slideInterval; }
        @Override public int     getSlideOverlay()   { return app.slideOverlay; }
        @Override public String  getSlideEffect()    { return app.slideEffect; }
        @Override public void    setSlideInterval(int v) { app.slideInterval = v; }
        @Override public void    setSlideOverlay(int v)  { app.slideOverlay  = v; }
        @Override public void    setSlideEffect(String v){ app.slideEffect   = v; }
        @Override public void    startSlideTimer()        { app.startSlideTimer(); }
        @Override public void    stopSlideTimer()         { app.stopSlideTimer(); }
        @Override public void    stopSlideTimerOnly()     { app.stopSlideTimerOnly(); }
        @Override public void    loadCurrentSlideImage()  { app.loadCurrentSlideImage(); }
        @Override public void    advanceSlide(int d)      { app.advanceSlide(d); }
        @Override public void    showFolderChooser(JCheckBoxMenuItem item) { app.folderItemAction(item); }

        // ── Global Cities ───────────────────────────────────
        @Override public String  getCityName() { return app.cityName; }
        @Override public void    openCityInstance(String cn, java.time.ZoneId zone) {
            SwingUtilities.invokeLater(() -> new AnalogClockSwing(app, cn, zone));
        }

        // ── Camera ───────────────────────────────────────────────
        @Override public boolean isCameraMode()           { return app.cameraMode; }
        @Override public void    setCameraMode(boolean v) { app.cameraMode = v; }
        @Override public void    startCamera(String url)  { app.startCamera(url); }
        @Override public void    stopCamera()             { app.stopCamera(); }
        @Override public void    captureCamera()          { app.captureCamera(); }
        @Override public String  getCameraUrl()           { return app.cameraUrl; }
        @Override public void    setCameraUrl(String url) { app.cameraUrl = url; }

        // ── ITS 교통 CCTV ────────────────────────────────────────
        @Override public ItsCctvManager getItsCctv()  { return app.getItsCctv(); }
        @Override public void startItsCctv()          { app.startItsCctv(); }
        @Override public void stopItsCctv()           { app.stopItsCctv(); }

        // ── Kakao ───────────────────────────────────────────
        @Override public String  getKakaoAccessToken()          { return app.kakao.kakaoAccessToken; }
        @Override public void    kakaoLogin()                   { app.kakao.kakaoLogin(); }
        @Override public void    kakaoSend(String title, String msg) { app.kakao.sendKakao(title, msg); }
        @Override public void    showKakaoGuide()               { app.kakao.showKakaoGuide(); }
        @Override public void    prepareMessageBox()            { app.prepareMessageBox(); }
        @Override public String  getLogFilePath()               { return AppLogger.getLogFilePath(); }
        @Override public String  getConfigFilePath()            { return AnalogClockSwing.CONFIG_FILE; }

        // ── UI 부모 컴포넌트 ────────────────────────────────
        @Override public Component getOwnerComponent() { return app; }
    }
}