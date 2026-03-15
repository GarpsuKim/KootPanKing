import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * ChimeController - 차임벨 재생 로직 + 설정 다이얼로그
 *
 * ── 책임 ────────────────────────────────────────────────────
 *   ① 매 초 시각을 체크하여 지정된 분에 차임벨 자동 재생
 *   ② wmplayer 를 통한 오디오/비디오 파일 재생 및 중지
 *   ③ OS 기본 앱을 통한 미디어 파일 재생 (텔레그램 수신 파일)
 *   ④ showChimeDialog() 로 설정 UI 표시
 *
 * ── wmplayer 의존성 ──────────────────────────────────────────
 *   Windows Media Player 경로를 우선/차순으로 탐색:
 *     C:\Program Files\Windows Media Player\wmplayer.exe
 *     C:\Program Files (x86)\Windows Media Player\wmplayer.exe
 *   존재하지 않으면 오류 메시지를 표시한다.
 *
 * ── HostCallback 인터페이스 ──────────────────────────────────
 *   ChimeController 는 AnalogClockSwing 의 상태를 직접 참조하지 않고
 *   HostCallback 을 통해 필요한 값만 가져온다.
 *   AlarmController.HostCallback 과 동일한 패턴.
 *
 * ── 사용법 ───────────────────────────────────────────────────
 *   ChimeController chime = new ChimeController(ownerFrame, hostCallback);
 *   chime.startCheckTimer();             // initUI 완료 후 호출
 *   chime.showChimeDialog();             // 팝업 메뉴 → 차임벨 설정
 *   chime.stopChime();                   // 강제 중지
 *   chime.playMediaFile(file);           // 텔레그램 수신 미디어 재생
 *
 *   // INI 저장/로드 시
 *   chime.isEnabled()  chime.getFile()  chime.isFull()  chime.getMinutes()
 *   chime.setEnabled() chime.setFile()  chime.setFull() chime.setMinutes()
 */
public class ChimeController {

    // ── 호스트 콜백 인터페이스 ───────────────────────────────
    public interface HostCallback {
        /** 현재 타임존 (차임벨 시각 체크에 사용) */
        ZoneId getTimeZone();
        /** JOptionPane 표시 전 시계 위치 조정 */
        void prepareMessageBox();
        /** JDialog 위치 조정 및 pack */
        void prepareDialog(java.awt.Window dlg);
        /** 무지개 배경 - 지정 색으로 교체 + 리페인트 (AlarmController 와 동일 패턴) */
        void setBgColorAndRepaint(java.awt.Color c);
        /** 무지개 배경 - 원래 배경 복원 */
        void restoreBgColor();
        /** 무지개 지속 시간 (초) — INI rainbowSeconds, 기본 30 */
        int getRainbowSeconds();
    }

    // ── 설정 필드 ────────────────────────────────────────────
    private boolean   enabled  = false;
    private String    file     = "";           // 오디오/비디오 파일 경로
    /** 연주 시간: 0=처음15초, 1=처음30초, 2=끝까지 */
    private int       duration = 0;
    private boolean[] minutes  = new boolean[60]; // 0~59분 체크

    // ── 내부 상태 ────────────────────────────────────────────
    private Process   chimeProcess = null;     // 현재 실행중인 wmplayer
    private Timer     checkTimer   = null;
    private int       lastChimeMinute = -1;    // 중복 실행 방지

    // ── 의존성 ───────────────────────────────────────────────
    private final JFrame       ownerFrame;
    private final HostCallback host;

    // ── 생성자 ───────────────────────────────────────────────
    public ChimeController(JFrame ownerFrame, HostCallback host) {
        this.ownerFrame = ownerFrame;
        this.host       = host;
        minutes[0]      = true;  // 기본값: 정각(0분)에 연주
    }

    // ── 설정 접근자 (INI 저장/로드용) ───────────────────────

    public boolean   isEnabled()  { return enabled; }
    public String    getFile()    { return file; }
    /** 연주 시간: 0=처음15초, 1=처음30초, 2=끝까지 */
    public int       getDuration() { return duration; }
    /** @deprecated isFull() 대신 getDuration()==2 사용 권장 */
    @Deprecated
    public boolean   isFull()     { return duration == 2; }
    public boolean[] getMinutes() { return minutes; }

    public void setEnabled(boolean v)  { this.enabled  = v; }
    public void setFile(String v)      { this.file     = v != null ? v : ""; }
    /** 연주 시간 설정: 0=처음15초, 1=처음30초, 2=끝까지 */
    public void setDuration(int v)     { this.duration = (v >= 0 && v <= 2) ? v : 0; }
    /** @deprecated setDuration() 사용 권장 */
    @Deprecated
    public void setFull(boolean v)     { this.duration = v ? 2 : 0; }
    public void setMinutes(boolean[] v) {
        if (v != null && v.length == 60) System.arraycopy(v, 0, minutes, 0, 60);
    }

    // ── 공개 API ─────────────────────────────────────────────

    /** 매 초 시각 체크 타이머 시작 (initUI 완료 후 1회 호출) */
    public void startCheckTimer() {
        if (checkTimer != null) checkTimer.stop();
        checkTimer = new Timer(1000, e -> checkAndPlay());
        checkTimer.start();
    }

    /** 차임벨 강제 중지 */
    public void stopChime() {
        if (chimeProcess != null) {
            chimeProcess.destroy();
            chimeProcess = null;
        }
    }

    /**
     * 텔레그램 수신 미디어 파일을 OS 기본 앱으로 재생.
     * @param mediaFile 재생할 파일
     */
    public void playMediaFile(File mediaFile) {
        try {
            Desktop.getDesktop().open(mediaFile);
            System.out.println("[MediaPlay] 기본 앱으로 재생: " + mediaFile.getName());
        } catch (Exception ex) {
            System.out.println("[MediaPlay] 재생 오류: " + ex.getMessage());
        }
    }

    /**
     * 차임벨 설정 다이얼로그 표시.
     * 파일 선택, 연주 시간(15초/끝까지), 분 단위 체크박스 60개.
     */
    public void showChimeDialog() {
        JDialog dlg = new JDialog(ownerFrame, "차임벨 설정", true);
        dlg.setLayout(new BorderLayout(8, 8));
        dlg.getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ── 상단 패널: on/off, 파일, duration ─────────────────
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(BorderFactory.createTitledBorder("기본 설정"));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 6, 4, 6);
        gc.anchor = GridBagConstraints.WEST;

        // on/off
        gc.gridx=0; gc.gridy=0; gc.gridwidth=1;
        topPanel.add(new JLabel("차임벨:"), gc);
        JCheckBox onOffBox = new JCheckBox("사용", enabled);
        gc.gridx=1; gc.gridwidth=3;
        topPanel.add(onOffBox, gc);

        // 파일 선택
        gc.gridx=0; gc.gridy=1; gc.gridwidth=1;
        topPanel.add(new JLabel("파일:"), gc);
        JTextField fileField = new JTextField(file, 28);
        fileField.setEditable(false);
        gc.gridx=1; gc.gridwidth=2; gc.fill=GridBagConstraints.HORIZONTAL;
        topPanel.add(fileField, gc);
        gc.fill=GridBagConstraints.NONE;
        JButton browseBtn = new JButton("찾기...");
        gc.gridx=3; gc.gridwidth=1;
        topPanel.add(browseBtn, gc);

        browseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("오디오/비디오 파일 선택");
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "미디어 파일 (mp3, wav, wma, mp4, avi, wmv, m4a, flac, ogg)",
                "mp3","wav","wma","mp4","avi","wmv","m4a","flac","ogg","aac","mkv"));
            fc.setAcceptAllFileFilterUsed(true);
            if (!file.isEmpty()) fc.setSelectedFile(new File(file));
            if (fc.showOpenDialog(dlg) == JFileChooser.APPROVE_OPTION) {
                fileField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });

        // 테스트 / 중지 버튼
        JButton testBtn = new JButton("▶ 테스트");
        gc.gridx=0; gc.gridy=2; gc.gridwidth=2;
        topPanel.add(testBtn, gc);
        JButton stopBtn = new JButton("■ 중지");
        gc.gridx=2; gc.gridwidth=2;
        topPanel.add(stopBtn, gc);

        testBtn.addActionListener(e -> {
            String f = fileField.getText().trim();
            if (f.isEmpty()) { JOptionPane.showMessageDialog(dlg, "파일을 먼저 선택하세요."); return; }
            // 임시로 파일 경로만 교체하여 재생 (설정은 OK 버튼에서 확정)
            String prev = file;
            file = f;
            playChimeInternal();
            file = prev;
        });
        stopBtn.addActionListener(e -> stopChime());

        // Duration
        gc.gridx=0; gc.gridy=3; gc.gridwidth=1;
        topPanel.add(new JLabel("연주 시간:"), gc);
        JRadioButton r15   = new JRadioButton("처음 15초만", duration == 0);
        JRadioButton r30   = new JRadioButton("처음 30초만", duration == 1);
        JRadioButton rFull = new JRadioButton("끝까지",      duration == 2);
        ButtonGroup bg = new ButtonGroup();
        bg.add(r15); bg.add(r30); bg.add(rFull);
        gc.gridx=1; gc.gridwidth=1; topPanel.add(r15, gc);
        gc.gridx=2;                 topPanel.add(r30, gc);
        gc.gridx=3;                 topPanel.add(rFull, gc);

        dlg.add(topPanel, BorderLayout.NORTH);

        // ── 중앙: 분 체크박스 60개 ────────────────────────────
        JPanel minPanel = new JPanel(new GridLayout(10, 6, 3, 3));
        minPanel.setBorder(BorderFactory.createTitledBorder("연주 시각 (매 시각 N분에 연주)"));
        JCheckBox[] minBoxes = new JCheckBox[60];
        for (int i = 0; i < 60; i++) {
            minBoxes[i] = new JCheckBox(String.format("%02d분", i), minutes[i]);
            minBoxes[i].setFont(new Font("Malgun Gothic", Font.PLAIN, 11));
            minPanel.add(minBoxes[i]);
        }

        JScrollPane minScroll = new JScrollPane(minPanel);
        minScroll.setPreferredSize(new Dimension(420, 260));
        dlg.add(minScroll, BorderLayout.CENTER);

        // ── 하단: 전체선택 / 해제 / 정각 / 확인 / 취소 ────────
        JPanel botPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton allBtn    = new JButton("전체 선택");
        JButton noneBtn   = new JButton("선택 해제");
        JButton topBtn    = new JButton("정각(0분)");
        JButton okBtn     = new JButton("  확인  ");
        JButton cancelBtn = new JButton("  취소  ");

        allBtn.addActionListener(e  -> { for (JCheckBox cb : minBoxes) cb.setSelected(true); });
        noneBtn.addActionListener(e -> { for (JCheckBox cb : minBoxes) cb.setSelected(false); });
        topBtn.addActionListener(e  -> {
            for (JCheckBox cb : minBoxes) cb.setSelected(false);
            minBoxes[0].setSelected(true);
        });
        okBtn.addActionListener(e -> {
            enabled  = onOffBox.isSelected();
            file     = fileField.getText().trim();
            duration = rFull.isSelected() ? 2 : r30.isSelected() ? 1 : 0;
            for (int i = 0; i < 60; i++) minutes[i] = minBoxes[i].isSelected();
            dlg.dispose();
        });
        cancelBtn.addActionListener(e -> dlg.dispose());

        botPanel.add(allBtn); botPanel.add(noneBtn); botPanel.add(topBtn);
        botPanel.add(Box.createHorizontalStrut(20));
        botPanel.add(okBtn); botPanel.add(cancelBtn);
        dlg.add(botPanel, BorderLayout.SOUTH);

        host.prepareDialog(dlg);
        dlg.setVisible(true);
    }

    // ── 내부: 시각 체크 ──────────────────────────────────────

    private void checkAndPlay() {
        if (!enabled || file.isEmpty()) return;
        ZonedDateTime now = ZonedDateTime.now(host.getTimeZone());
        int min = now.getMinute();
        int sec = now.getSecond();
        if (sec == 0 && minutes[min] && min != lastChimeMinute) {
            lastChimeMinute = min;
            playChimeInternal();
        }
        if (sec > 2) lastChimeMinute = -1; // 다음 분 준비
    }

    // ── 내부: wmplayer 재생 ──────────────────────────────────

    private void playChimeInternal() {
        stopChime(); // 이전 연주 종료
        try {
            String wmplayer = "C:\\Program Files\\Windows Media Player\\wmplayer.exe";
            if (!new File(wmplayer).exists()) {
                wmplayer = "C:\\Program Files (x86)\\Windows Media Player\\wmplayer.exe";
            }
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(wmplayer);
            if (duration < 2) {          // 0=15초, 1=30초 → 재생 후 자동 닫기
                cmd.add("/play");
                cmd.add("/close");
            }
            cmd.add(file);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            chimeProcess = pb.start();

            if (duration < 2) {
                // duration==0 → 15초, duration==1 → 30초 후 자동 종료
                int stopMs = (duration == 1) ? 30000 : 15000;
                new Timer(stopMs, ev -> {
                    ((Timer) ev.getSource()).stop();
                    stopChime();
                }).start();
            }
        } catch (Exception ex) {
            host.prepareMessageBox();
            JOptionPane.showMessageDialog(null,
                "wmplayer 실행 오류:\n" + ex.getMessage(),
                "차임벨 오류", JOptionPane.ERROR_MESSAGE);
        }

        // ── 무지개 배경 타이머 (7색 순환, getRainbowSeconds()초 후 자동 복귀) ──
        final java.awt.Color[] RAINBOW = {
            new java.awt.Color(255,  0,  0),   // 빨
            new java.awt.Color(255,127,  0),   // 주
            new java.awt.Color(255,255,  0),   // 노
            new java.awt.Color(  0,200,  0),   // 초
            new java.awt.Color(  0,  0,255),   // 파
            new java.awt.Color( 75,  0,130),   // 남
            new java.awt.Color(148,  0,211),   // 보
        };
        final int totalSecs = Math.max(1, host.getRainbowSeconds());
        final int[] colorIdx = {0};
        final Timer[] rainbowTimer = {null};
        rainbowTimer[0] = new Timer(1000, ev -> {
            if (colorIdx[0] < totalSecs) {
                host.setBgColorAndRepaint(RAINBOW[colorIdx[0] % RAINBOW.length]);
                colorIdx[0]++;
            } else {
                rainbowTimer[0].stop();
                host.restoreBgColor();
            }
        });
        rainbowTimer[0].start();
    }
}
