import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AlarmController
 *
 * AnalogClockSwing 에서 알람 관련 코드를 전부 분리한 단일 클래스.
 *
 * 담당:
 *   - 알람 항목 데이터 (AlarmEntry — static inner class, 구 AlarmEntry.java)
 *   - 알람 목록 관리 (List<AlarmEntry>)
 *   - alarms.dat 저장 / 로드
 *   - 매 초 알람 체크  → AnalogClockSwing.chimeCheckTimer 에서 호출
 *   - 알람 발화        (fireAlarm / fireAlarmNow)
 *   - 알람 관리 다이얼로그 (showAlarmDialog)
 *   - 알람 편집 다이얼로그 (showAlarmEditDialog ← 내부 private)
 *
 * AnalogClockSwing 의 서비스 객체(push/gmail/kakao/tg)와
 * 차임벨·타임존·UI 헬퍼는 HostCallback 인터페이스로 주입받는다.
 *
 * ── 외부 참조 변경사항 ────────────────────────────────────────
 *   구 AlarmEntry  →  AlarmController.AlarmEntry  (또는 import static)
 */
public class AlarmController {

    // ═══════════════════════════════════════════════════════════
    //  AlarmEntry — 알람 항목 데이터 클래스 (구 AlarmEntry.java)
    //
    //  직렬화(Serializable) 지원 - alarms.dat 파일 저장/로드에 사용.
    //  serialVersionUID 는 기존 AlarmEntry 와 동일하게 유지하여
    //  기존 alarms.dat 파일과 역호환성을 보장한다.
    // ═══════════════════════════════════════════════════════════

    public static class AlarmEntry implements java.io.Serializable {

        private static final long serialVersionUID = 1L;

        String    id;             // UUID
        String    label;          // 메시지
        int       hour, minute;   // 시:분
        boolean[] days;           // [0]=일 [1]=월 ... [6]=토, 모두 false=매일
        boolean   enabled;
        String    soundFile;      // "" = 기본차임벨
        boolean   usePush;        // 스마트폰 push
        String    pushToken;      // Pushover user token  or  ntfy topic
        String    pushAppToken;   // Pushover app/api token
        String    pushService;    // "pushover" | "ntfy"
        boolean   useEmail;
        String    emailTo;
        boolean   useKakao;       // 카카오톡 나에게 보내기
        boolean   useTelegram;    // 텔레그램 알림
        String    telegramChatId; // 텔레그램 Chat ID
        boolean   fired;          // 이번 분에 이미 울렸는지

        AlarmEntry() {
            id = UUID.randomUUID().toString();
            label = "알람";
            hour = 7; minute = 0;
            days = new boolean[7];
            enabled = true;
            soundFile = "";
            usePush = false; pushToken = ""; pushAppToken = ""; pushService = "ntfy";
            useEmail = false; emailTo = "";
            useKakao = false;
            useTelegram = false; telegramChatId = "";
            fired = false;
        }

        /** 오늘 요일(Calendar.SUNDAY=1~SATURDAY=7)에 울려야 하는가? */
        boolean matchesDay(int calDow) {
            boolean allOff = true;
            for (boolean b : days) if (b) { allOff = false; break; }
            if (allOff) return true; // 매일
            // calDow: 1=일,2=월,...7=토 → days[0..6]
            return days[calDow - 1];
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  HostCallback — AnalogClockSwing 이 구현체를 제공
    // ═══════════════════════════════════════════════════════════

    public interface HostCallback {
        String  getChimeFile();
        boolean isChimeFull();
        ZoneId  getTimeZone();
        void    prepareMessageBox();
        void    prepareDialog(java.awt.Window w);
        void    saveConfig();
        void    showNtfyQrDialog(java.awt.Window parent, String url, String topic);
        void    showPushoverQrDialog(java.awt.Window parent);
        /** 무지개 배경 - 지정 색으로 교체 + 리페인트 (CalendarAlarmPoller 와 동일 패턴) */
        void    setBgColorAndRepaint(java.awt.Color c);
        /** 무지개 배경 - 원래 배경 복원 */
        void    restoreBgColor();
        /** 무지개 지속 시간 (초) — INI rainbowSeconds, 기본 30 */
        int     getRainbowSeconds();
    }

    // ═══════════════════════════════════════════════════════════
    //  주입 필드
    // ═══════════════════════════════════════════════════════════

    private final JFrame       ownerFrame;
    private final String       alarmFile;
    private final PushSender   push;
    private final GmailSender  gmail;
    private final Kakao        kakao;
    private final TelegramBot  tg;
    private final HostCallback host;

    // ═══════════════════════════════════════════════════════════
    //  알람 데이터
    // ═══════════════════════════════════════════════════════════

    private List<AlarmEntry> alarmList       = new ArrayList<>();
    private int              lastAlarmMinute = -1;
    private javax.swing.Timer checkTimer     = null;

    // ═══════════════════════════════════════════════════════════
    //  생성자
    // ═══════════════════════════════════════════════════════════

    public AlarmController(JFrame ownerFrame,
                           String alarmFile,
                           PushSender push,
                           GmailSender gmail,
                           Kakao kakao,
                           TelegramBot tg,
                           HostCallback host) {
        this.ownerFrame = ownerFrame;
        this.alarmFile  = alarmFile;
        this.push       = push;
        this.gmail      = gmail;
        this.kakao      = kakao;
        this.tg         = tg;
        this.host       = host;
    }

    // ═══════════════════════════════════════════════════════════
    //  공개 API
    // ═══════════════════════════════════════════════════════════

    /** 알람 목록 반환 (텔레그램 설정 다이얼로그 등 외부에서 읽기용) */
    public List<AlarmEntry> getAlarmList() { return alarmList; }

    // ═══════════════════════════════════════════════════════════
    //  저장 / 로드
    // ═══════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public void loadAlarms() {
        try (ObjectInputStream ois = new ObjectInputStream(
                new FileInputStream(alarmFile))) {
            alarmList = (List<AlarmEntry>) ois.readObject();
        } catch (Exception ignored) {
            alarmList = new ArrayList<>();
        }
    }

    public void saveAlarms() {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream(alarmFile))) {
            oos.writeObject(alarmList);
        } catch (Exception ignored) {}
    }

    // ═══════════════════════════════════════════════════════════
    //  알람 체크 (매 초 — AnalogClockSwing.chimeCheckTimer 에서 호출)
    // ═══════════════════════════════════════════════════════════

    /** 매 초 알람 체크 타이머 시작 (initUI 완료 후 1회 호출) */
    public void startCheckTimer() {
        if (checkTimer != null) checkTimer.stop();
        checkTimer = new javax.swing.Timer(1000, e -> checkAlarms());
        checkTimer.start();
    }

    public void checkAlarms() {
        ZonedDateTime now = ZonedDateTime.now(host.getTimeZone());
        int h   = now.getHour();
        int m   = now.getMinute();
        int sec = now.getSecond();

        // 분이 바뀌면 fired 플래그 초기화
        if (lastAlarmMinute != m) {
            for (AlarmEntry a : alarmList) a.fired = false;
            lastAlarmMinute = m;
        }
        if (sec != 0) return;

        // java.time SUNDAY=7 → calDow=1, MONDAY=1 → calDow=2, ..., SATURDAY=6 → calDow=7
        int dow = now.getDayOfWeek().getValue() % 7 + 1;

        for (AlarmEntry a : alarmList) {
            if (!a.enabled || a.fired) continue;
            if (a.hour == h && a.minute == m && a.matchesDay(dow)) {
                a.fired = true;
                fireAlarm(a);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  알람 발화
    // ═══════════════════════════════════════════════════════════

    public void fireAlarm(AlarmEntry a) {
        // ① 소리
        String snd = a.soundFile.isEmpty() ? host.getChimeFile() : a.soundFile;
        if (!snd.isEmpty()) {
            try {
                List<String> cmd = new ArrayList<>();
                String wmp = "C:\\Program Files\\Windows Media Player\\wmplayer.exe";
                if (!new File(wmp).exists())
                    wmp = "C:\\Program Files (x86)\\Windows Media Player\\wmplayer.exe";
                cmd.add(wmp);
                if (!host.isChimeFull()) { cmd.add("/Play"); cmd.add("/Close"); }
                cmd.add(snd);
                new ProcessBuilder(cmd).start();
            } catch (Exception ignored) {}
        }
        // ② 텍스트박스 + 확인 버튼 팝업 + 무지개 배경 (EDT에서 실행)
        final String msg = a.label.isEmpty() ? "알람!" : a.label;
        final String content = String.format("⏰  %02d:%02d\n\n%s", a.hour, a.minute, msg);
        SwingUtilities.invokeLater(() -> {
            if (host != null) host.prepareMessageBox();

            javax.swing.JDialog dlg = new javax.swing.JDialog(
                (java.awt.Frame) null, "알람", false);
            dlg.setLayout(new java.awt.BorderLayout(8, 8));
            dlg.getRootPane().setBorder(
                javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));

            javax.swing.JTextArea ta = new javax.swing.JTextArea(content, 6, 30);
            ta.setEditable(false);
            ta.setFont(new java.awt.Font("Malgun Gothic", java.awt.Font.PLAIN, 14));
            ta.setLineWrap(true);
            ta.setWrapStyleWord(true);
            dlg.add(new javax.swing.JScrollPane(ta), java.awt.BorderLayout.CENTER);

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
            final javax.swing.Timer[] rainbowTimer = {null};
            if (host != null) {
                rainbowTimer[0] = new javax.swing.Timer(1000, ev -> {
                    if (colorIdx[0] < totalSecs) {
                        host.setBgColorAndRepaint(RAINBOW[colorIdx[0] % RAINBOW.length]);
                        colorIdx[0]++;
                    } else {
                        // 완료 → 타이머 중지 + 원래 배경 복원
                        rainbowTimer[0].stop();
                        host.restoreBgColor();
                    }
                });
                rainbowTimer[0].start();
            }

            javax.swing.JButton okBtn = new javax.swing.JButton("  확인  ");
            okBtn.addActionListener(ev -> dlg.dispose());
            // 다이얼로그 닫힐 때 타이머 중지 + 복원 (X 버튼 등)
            dlg.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosed(java.awt.event.WindowEvent e) {
                    if (rainbowTimer[0] != null) rainbowTimer[0].stop();
                    if (host != null) host.restoreBgColor();
                }
            });
            javax.swing.JPanel btnPanel = new javax.swing.JPanel(
                new java.awt.FlowLayout(java.awt.FlowLayout.CENTER));
            btnPanel.add(okBtn);
            dlg.add(btnPanel, java.awt.BorderLayout.SOUTH);

            dlg.setAlwaysOnTop(true);
            dlg.pack();
            // 화면 중앙 배치
            java.awt.Dimension screen =
                java.awt.Toolkit.getDefaultToolkit().getScreenSize();
            dlg.setLocation(
                (screen.width  - dlg.getWidth())  / 2,
                (screen.height - dlg.getHeight()) / 2);
            dlg.setVisible(true);
        });
        // ③ 스마트폰 push
        if (a.usePush && !a.pushToken.isEmpty())
            new Thread(() -> push.sendAlarm(
                a.pushService, a.pushToken, a.pushAppToken,
                a.hour, a.minute, msg), "AlarmPush").start();
        // ④ 이메일
        if (a.useEmail && !a.emailTo.isEmpty())
            new Thread(() -> gmail.sendAlarm(a.emailTo, a.hour, a.minute, msg), "AlarmMail").start();
        // ⑤ 카카오톡
        if (a.useKakao && !kakao.kakaoAccessToken.isEmpty()) {
            String title = String.format("%02d:%02d 알람", a.hour, a.minute);
            new Thread(() -> kakao.sendKakao(title, msg), "AlarmKakao").start();
        }
        // ⑥ 텔레그램
        if (a.useTelegram && !a.telegramChatId.isEmpty()) {
            String text = String.format("⏰ %02d:%02d 알람\n\n%s", a.hour, a.minute, msg);
            new Thread(() -> tg.send(a.telegramChatId, text), "AlarmTelegram").start();
        }
    }

    /** [지금 보내기] — 즉시 모든 채널 전송 후 결과 팝업 */
    public void fireAlarmNow(AlarmEntry a, java.awt.Window owner) {
        final String msg    = a.label.isEmpty() ? "알람!" : a.label;
        final String nowStr = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date());
        StringBuilder result = new StringBuilder();
        result.append("지금 보내기 실행!\n\n").append(nowStr).append("  ").append(msg).append("\n\n");

        // ntfy
        if (a.usePush && !a.pushToken.isEmpty() && "ntfy".equals(a.pushService)) {
            new Thread(() -> {
                try { push.sendNtfy(a.pushToken, "지금 보내기 " + nowStr, msg); }
                catch (Exception ex) { System.out.println("[SendNow] ntfy: " + ex.getMessage()); }
            }, "SendNowNtfy").start();
            result.append("ntfy 전송 중...\n");
        }
        // Pushover
        if (a.usePush && !a.pushToken.isEmpty() && "pushover".equals(a.pushService)) {
            if (a.pushAppToken.isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    owner.toFront();
                    JOptionPane.showMessageDialog(owner,
                        "Pushover App Token이 비어있습니다.\n알람 편집 → App Token을 입력하세요.",
                        "Pushover", JOptionPane.WARNING_MESSAGE);
                });
            } else {
                new Thread(() -> {
                    try { push.sendPushover(a.pushAppToken, a.pushToken, "지금 보내기 " + nowStr, msg); }
                    catch (Exception ex) { System.out.println("[SendNow] Pushover: " + ex.getMessage()); }
                }, "SendNowPushover").start();
                result.append("Pushover 전송 중...\n");
            }
        }
        // 이메일
        if (a.useEmail && !a.emailTo.isEmpty()) {
            new Thread(() -> gmail.sendAlarm(a.emailTo, a.hour, a.minute, msg), "SendNowMail").start();
            result.append("이메일 전송 중...\n");
        }
        // 카카오톡
        if (a.useKakao) {
            if (kakao.kakaoAccessToken.isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    owner.toFront();
                    JOptionPane.showMessageDialog(owner,
                        "카카오톡 로그인이 필요합니다.", "카카오톡", JOptionPane.WARNING_MESSAGE);
                });
            } else {
                new Thread(() -> kakao.sendKakao(a.label, msg), "SendNowKakao").start();
                result.append("카카오톡 전송 중...\n");
            }
        }
        // 텔레그램
        if (a.useTelegram) {
            if (tg.botToken.isEmpty() || a.telegramChatId.isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    owner.toFront();
                    JOptionPane.showMessageDialog(owner,
                        "텔레그램 Bot Token 또는 Chat ID가 없습니다.\n" +
                        "알람 관리 → Bot Token, 알람 편집 → Chat ID를 입력하세요.",
                        "텔레그램", JOptionPane.WARNING_MESSAGE);
                });
            } else {
                String text = String.format("⏰ 지금 보내기 %s\n\n%s", nowStr, msg);
                new Thread(() -> tg.send(a.telegramChatId, text), "SendNowTelegram").start();
                result.append("텔레그램 전송 중...\n");
            }
        }
        // 결과 팝업
        final String finalMsg = result.toString();
        SwingUtilities.invokeLater(() -> {
            owner.toFront();
            owner.requestFocus();
            JOptionPane.showMessageDialog(owner, finalMsg, "지금 보내기", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    // ═══════════════════════════════════════════════════════════
    //  알람 관리 다이얼로그 (public — 팝업메뉴에서 호출)
    // ═══════════════════════════════════════════════════════════

    public void showAlarmDialog() {
        JDialog dlg = new JDialog(ownerFrame, "알람 관리", true);
        dlg.setLayout(new BorderLayout(6, 6));
        dlg.getRootPane().setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        dlg.setMinimumSize(new Dimension(540, 420));

        // ── 알람 목록 ──────────────────────────────────────────
        DefaultListModel<String> listModel = new DefaultListModel<>();
        JList<String> alarmJList = new JList<>(listModel);
        alarmJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        alarmJList.setFont(new Font("Malgun Gothic", Font.PLAIN, 13));

        Runnable refreshList = () -> {
            listModel.clear();
            for (AlarmEntry a : alarmList) {
                String days = buildDayLabel(a);
                String ps   = a.usePush  ? " 📱" : "";
                String ms   = a.useEmail ? " 📧" : "";
                listModel.addElement(String.format("%s  %02d:%02d  %s  %s%s%s",
                    a.enabled ? "✅" : "⬜", a.hour, a.minute, a.label, days, ps, ms));
            }
        };
        refreshList.run();

        JScrollPane listScroll = new JScrollPane(alarmJList);
        listScroll.setPreferredSize(new Dimension(520, 200));

        // ── 버튼 ───────────────────────────────────────────────
        JButton addBtn    = new JButton("추가");
        JButton editBtn   = new JButton("편집");
        JButton delBtn    = new JButton("삭제");
        JButton toggleBtn = new JButton("켜기/끄기");
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btnPanel.add(addBtn); btnPanel.add(editBtn); btnPanel.add(delBtn); btnPanel.add(toggleBtn);

        addBtn.addActionListener(e -> {
            AlarmEntry ne = new AlarmEntry();
            if (showAlarmEditDialog(dlg, ne)) { alarmList.add(ne); saveAlarms(); refreshList.run(); }
        });
        editBtn.addActionListener(e -> {
            int idx = alarmJList.getSelectedIndex();
            if (idx < 0) return;
            if (showAlarmEditDialog(dlg, alarmList.get(idx))) { saveAlarms(); refreshList.run(); }
        });
        delBtn.addActionListener(e -> {
            int idx = alarmJList.getSelectedIndex();
            if (idx < 0) return;
            if (JOptionPane.showConfirmDialog(dlg, "삭제하시겠습니까?", "확인",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                alarmList.remove(idx); saveAlarms(); refreshList.run();
            }
        });
        toggleBtn.addActionListener(e -> {
            int idx = alarmJList.getSelectedIndex();
            if (idx < 0) return;
            alarmList.get(idx).enabled = !alarmList.get(idx).enabled;
            saveAlarms(); refreshList.run();
        });

        // ── 이메일 계정 설정 ────────────────────────────────────
        JPanel emailPanel = new JPanel(new GridBagLayout());
        emailPanel.setBorder(BorderFactory.createTitledBorder("이메일 발신 계정 (Gmail)"));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(3, 4, 3, 4); gc.anchor = GridBagConstraints.WEST;

        gc.gridx = 0; gc.gridy = 0;
        emailPanel.add(new JLabel("Gmail 주소:"), gc);
        JTextField fromField = new JTextField(gmail.from, 22);
        gc.gridx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1;
        emailPanel.add(fromField, gc);

        gc.gridx = 0; gc.gridy = 1; gc.fill = GridBagConstraints.NONE; gc.weightx = 0;
        emailPanel.add(new JLabel("앱 비밀번호:"), gc);
        JPasswordField passField = new JPasswordField(gmail.pass, 22);
        gc.gridx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1;
        emailPanel.add(passField, gc);

        gc.gridx = 0; gc.gridy = 2; gc.gridwidth = 2; gc.fill = GridBagConstraints.NONE;
        emailPanel.add(new JLabel(
            "<html><font color=gray size=-1>※ Gmail → 구글 계정 → 보안 → 앱 비밀번호 생성</font></html>"), gc);
        gc.gridwidth = 1;

        // ── [지금 Gmail 보내기] ────────────────────────────────
        JButton sendGmailBtn = new JButton("📧 지금 Gmail 보내기");
        sendGmailBtn.setOpaque(true);
        sendGmailBtn.setBorderPainted(false);
        sendGmailBtn.setBackground(new Color(66, 133, 244));
        sendGmailBtn.setForeground(Color.WHITE);
        sendGmailBtn.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
        sendGmailBtn.addActionListener(e -> {
            gmail.from = fromField.getText().trim();
            gmail.pass = new String(passField.getPassword()).trim();
            if (gmail.from.isEmpty() || gmail.pass.isEmpty()) {
                JOptionPane.showMessageDialog(dlg,
                    "Gmail 주소와 앱 비밀번호를 먼저 입력하세요.", "Gmail 보내기", JOptionPane.WARNING_MESSAGE);
                return;
            }
            JPanel ip = new JPanel(new GridBagLayout());
            GridBagConstraints ig = new GridBagConstraints();
            ig.insets = new Insets(4, 4, 4, 4); ig.anchor = GridBagConstraints.WEST;
            ig.gridx = 0; ig.gridy = 0; ip.add(new JLabel("수신자 이메일:"), ig);
            JTextField toField = new JTextField(20);
            int selIdx = alarmJList.getSelectedIndex();
            if (!gmail.lastTo.isEmpty())                                       toField.setText(gmail.lastTo);
            else if (selIdx >= 0 && !alarmList.get(selIdx).emailTo.isEmpty()) toField.setText(alarmList.get(selIdx).emailTo);
            else                                                               toField.setText(gmail.from);
            ig.gridx = 1; ig.fill = GridBagConstraints.HORIZONTAL; ig.weightx = 1;
            ip.add(toField, ig);
            ig.gridx = 0; ig.gridy = 1; ig.fill = GridBagConstraints.NONE; ig.weightx = 0;
            ip.add(new JLabel("제목:"), ig);
            JTextField subjField = new JTextField("[끝판왕] 시계 알림", 20);
            ig.gridx = 1; ig.fill = GridBagConstraints.HORIZONTAL; ig.weightx = 1;
            ip.add(subjField, ig);
            ig.gridx = 0; ig.gridy = 2; ig.fill = GridBagConstraints.NONE; ig.weightx = 0;
            ip.add(new JLabel("내용:"), ig);
            JTextArea bodyArea = new JTextArea(4, 20); bodyArea.setLineWrap(true);
            bodyArea.setText(GmailSender.APP_SIGNATURE +
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
            ig.gridx = 1; ig.fill = GridBagConstraints.BOTH; ig.weightx = 1; ig.weighty = 1;
            ip.add(new JScrollPane(bodyArea), ig);

            if (JOptionPane.showConfirmDialog(dlg, ip, "📧 지금 Gmail 보내기",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

            String toAddr = toField.getText().trim();
            if (toAddr.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "수신자 이메일을 입력하세요.", "Gmail 보내기", JOptionPane.WARNING_MESSAGE);
                return;
            }
            sendGmailBtn.setEnabled(false); sendGmailBtn.setText("📧 전송 중...");
            final String subj = subjField.getText().trim();
            final String body = bodyArea.getText().trim();
            new Thread(() -> {
                try {
                    gmail.send(toAddr, subj, body);
                    SwingUtilities.invokeLater(() -> {
                        sendGmailBtn.setEnabled(true); sendGmailBtn.setText("📧 지금 Gmail 보내기");
                        JOptionPane.showMessageDialog(dlg, "✅ Gmail 전송 완료!\n수신자: " + toAddr,
                            "Gmail 보내기", JOptionPane.INFORMATION_MESSAGE);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        sendGmailBtn.setEnabled(true); sendGmailBtn.setText("📧 지금 Gmail 보내기");
                        JOptionPane.showMessageDialog(dlg,
                            "❌ 전송 실패: " + ex.getMessage() +
                            "\n\n확인사항:\n1. Gmail 앱 비밀번호 확인\n2. Gmail → 구글 계정 → 보안 → 앱 비밀번호",
                            "Gmail 오류", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }, "GmailSendNow").start();
        });

        JButton closeBtn = new JButton("닫기");
        closeBtn.addActionListener(e -> {
            gmail.from = fromField.getText().trim();
            gmail.pass = new String(passField.getPassword()).trim();
            saveAlarms();
            dlg.dispose();
        });
        JPanel south2 = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south2.add(sendGmailBtn); south2.add(closeBtn);

        // ── 카카오톡 설정 ──────────────────────────────────────
        JPanel kakaoPanel = new JPanel(new GridBagLayout());
        kakaoPanel.setBorder(BorderFactory.createTitledBorder("카카오톡 나에게 보내기"));
        GridBagConstraints kgc = new GridBagConstraints();
        kgc.insets = new Insets(3, 4, 3, 4); kgc.anchor = GridBagConstraints.WEST;

        kgc.gridx = 0; kgc.gridy = 0; kakaoPanel.add(new JLabel("REST API 키:"), kgc);
        JTextField kakaoKeyField = new JTextField(kakao.kakaoRestApiKey, 28);
        kgc.gridx = 1; kgc.fill = GridBagConstraints.HORIZONTAL; kgc.weightx = 1;
        kakaoPanel.add(kakaoKeyField, kgc);
        JButton kakaoLoginBtn = new JButton("카카오 로그인");
        kgc.gridx = 2; kgc.fill = GridBagConstraints.NONE; kgc.weightx = 0;
        kakaoPanel.add(kakaoLoginBtn, kgc);

        kgc.gridx = 0; kgc.gridy = 1; kakaoPanel.add(new JLabel("Client Secret:"), kgc);
        JTextField kakaoSecretField = new JTextField(kakao.kakaoClientSecret, 28);
        kgc.gridx = 1; kgc.fill = GridBagConstraints.HORIZONTAL; kgc.weightx = 1; kgc.gridwidth = 2;
        kakaoPanel.add(kakaoSecretField, kgc); kgc.gridwidth = 1;

        kgc.gridx = 0; kgc.gridy = 2; kgc.gridwidth = 3; kgc.fill = GridBagConstraints.NONE;
        kakaoPanel.add(new JLabel(
            "<html><font color=gray size=-1>" +
            "※ developers.kakao.com → 내 애플리케이션 → 앱 키 → REST API 키<br>" +
            "※ Client Secret: 보안 → Client Secret → 코드 복사</font></html>"), kgc);
        kgc.gridwidth = 1;

        kgc.gridx = 0; kgc.gridy = 3; kgc.gridwidth = 3;
        JLabel kakaoStatusLabel = new JLabel(kakao.kakaoAccessToken.isEmpty() ? "❌ 미로그인" : "✅ 로그인됨");
        kakaoPanel.add(kakaoStatusLabel, kgc); kgc.gridwidth = 1;

        kakaoLoginBtn.addActionListener(e -> {
            kakao.kakaoRestApiKey   = kakaoKeyField.getText().trim();
            kakao.kakaoClientSecret = kakaoSecretField.getText().trim();
            host.saveConfig();
            host.prepareMessageBox();
            kakao.kakaoLogin();
            new Timer(3000, ev -> {
                kakaoStatusLabel.setText(kakao.kakaoAccessToken.isEmpty() ? "❌ 미로그인" : "✅ 로그인됨");
                ((Timer) ev.getSource()).stop();
            }).start();
        });

        // ── 텔레그램 설정 ──────────────────────────────────────
        JPanel tgPanel = new JPanel(new GridBagLayout());
        tgPanel.setBorder(BorderFactory.createTitledBorder("텔레그램 알림"));
        GridBagConstraints tgc = new GridBagConstraints();
        tgc.insets = new Insets(3, 4, 3, 4); tgc.anchor = GridBagConstraints.WEST;

        tgc.gridx = 0; tgc.gridy = 0; tgPanel.add(new JLabel("Bot Token:"), tgc);
        JTextField tgTokenField = new JTextField(tg.botToken, 28);
        tgc.gridx = 1; tgc.fill = GridBagConstraints.HORIZONTAL; tgc.weightx = 1;
        tgPanel.add(tgTokenField, tgc);

        tgc.gridx = 0; tgc.gridy = 1; tgc.gridwidth = 2; tgc.fill = GridBagConstraints.NONE;
        tgPanel.add(new JLabel(
            "<html><font color=gray size=-1>" +
            "※ 텔레그램 @BotFather → /newbot → Bot Token 발급<br>" +
            "※ Chat ID: @userinfobot 에게 /start 전송</font></html>"), tgc);

        tgTokenField.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { tg.botToken = tgTokenField.getText().trim(); }
        });

        // ── 레이아웃 조립 ──────────────────────────────────────
        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.add(listScroll, BorderLayout.CENTER);
        center.add(btnPanel,   BorderLayout.SOUTH);

        JPanel topRow = new JPanel(new GridLayout(1, 2, 4, 0));
        topRow.add(kakaoPanel); topRow.add(tgPanel);

        JPanel mainSouth = new JPanel(new BorderLayout(0, 4));
        mainSouth.add(topRow,      BorderLayout.NORTH);
        mainSouth.add(emailPanel,  BorderLayout.CENTER);
        mainSouth.add(south2,      BorderLayout.SOUTH);

        dlg.add(center,    BorderLayout.CENTER);
        dlg.add(mainSouth, BorderLayout.SOUTH);

        host.prepareDialog(dlg);
        dlg.toFront();
        dlg.setVisible(true);
    }

    // ═══════════════════════════════════════════════════════════
    //  알람 편집 다이얼로그 (private — showAlarmDialog 에서만 호출)
    // ═══════════════════════════════════════════════════════════

    private boolean showAlarmEditDialog(JDialog parent, AlarmEntry a) {
        JDialog ed = new JDialog(parent, "알람 편집", true);
        ed.setLayout(new BorderLayout(6, 6));
        ed.getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 6, 4, 6); gc.anchor = GridBagConstraints.WEST;
        int row = 0;

        // 시:분
        gc.gridx = 0; gc.gridy = row; form.add(new JLabel("시간:"), gc);
        JSpinner hSpin = new JSpinner(new SpinnerNumberModel(a.hour,   0, 23, 1));
        JSpinner mSpin = new JSpinner(new SpinnerNumberModel(a.minute, 0, 59, 1));
        ((JSpinner.NumberEditor) hSpin.getEditor()).getFormat().applyPattern("00");
        ((JSpinner.NumberEditor) mSpin.getEditor()).getFormat().applyPattern("00");
        JPanel timePnl = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        timePnl.add(hSpin); timePnl.add(new JLabel(":")); timePnl.add(mSpin);
        gc.gridx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1;
        form.add(timePnl, gc); row++;

        // 메시지
        gc.gridx = 0; gc.gridy = row; gc.fill = GridBagConstraints.NONE; gc.weightx = 0;
        form.add(new JLabel("메시지:"), gc);
        JTextField labelField = new JTextField(a.label, 22);
        gc.gridx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1;
        form.add(labelField, gc); row++;

        // 요일
        gc.gridx = 0; gc.gridy = row; gc.fill = GridBagConstraints.NONE; gc.weightx = 0;
        form.add(new JLabel("반복:"), gc);
        JPanel dayPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        String[] dn = {"일","월","화","수","목","금","토"};
        JCheckBox[] dayCb = new JCheckBox[7];
        for (int i = 0; i < 7; i++) { dayCb[i] = new JCheckBox(dn[i], a.days[i]); dayPanel.add(dayCb[i]); }
        boolean allOff = true;
        for (boolean b : a.days) if (b) { allOff = false; break; }
        JCheckBox everyDay = new JCheckBox("매일", allOff);
        everyDay.addActionListener(e -> { if (everyDay.isSelected()) for (JCheckBox cb : dayCb) cb.setSelected(false); });
        for (JCheckBox cb : dayCb) cb.addActionListener(e -> everyDay.setSelected(false));
        dayPanel.add(new JLabel("  ")); dayPanel.add(everyDay);
        gc.gridx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1;
        form.add(dayPanel, gc); row++;

        // 알람음
        gc.gridx = 0; gc.gridy = row; gc.fill = GridBagConstraints.NONE; gc.weightx = 0;
        form.add(new JLabel("알람음:"), gc);
        JTextField sndField = new JTextField(a.soundFile, 18);
        JButton sndBtn = new JButton("...");
        sndBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(a.soundFile.isEmpty() ? System.getProperty("user.home") : a.soundFile);
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "오디오/비디오", "mp3","wav","m4a","aac","ogg","wma","mp4","avi"));
            if (fc.showOpenDialog(ed) == JFileChooser.APPROVE_OPTION)
                sndField.setText(fc.getSelectedFile().getAbsolutePath());
        });
        JPanel sndPnl = new JPanel(new BorderLayout(3, 0));
        sndPnl.add(sndField, BorderLayout.CENTER); sndPnl.add(sndBtn, BorderLayout.EAST);
        gc.gridx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1;
        form.add(sndPnl, gc); row++;

        // 스마트폰 push
        JCheckBox pushCb = new JCheckBox("스마트폰 알림", a.usePush);
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2; form.add(pushCb, gc); gc.gridwidth = 1; row++;

        gc.gridx = 0; gc.gridy = row; gc.fill = GridBagConstraints.NONE; gc.weightx = 0;
        form.add(new JLabel("  서비스:"), gc);
        JComboBox<String> svcBox = new JComboBox<>(new String[]{"ntfy", "pushover"});
        svcBox.setSelectedItem(a.pushService);
        if (a.pushToken.isEmpty())
            a.pushToken = "clock-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        JTextField tokenField    = new JTextField(a.pushToken,    16);
        JTextField appTokenField = new JTextField(a.pushAppToken, 20);

        JButton qrBtn = new JButton("📷 QR 구독");
        qrBtn.setToolTipText("스마트폰으로 QR 스캔하여 ntfy 구독");
        qrBtn.addActionListener(e -> {
            String topic = tokenField.getText().trim();
            if (topic.isEmpty()) {
                JOptionPane.showMessageDialog(null, "토픽명을 먼저 입력하세요.", "QR코드", JOptionPane.WARNING_MESSAGE);
                return;
            }
            host.showNtfyQrDialog(ed, "https://ntfy.sh/" + topic, topic);
        });
        JButton pushoverQrBtn = new JButton("📷 QR 가입");
        pushoverQrBtn.setToolTipText("스마트폰으로 QR 스캔하여 Pushover 가입/앱 설치");
        pushoverQrBtn.addActionListener(e -> host.showPushoverQrDialog(ed));

        JPanel qrPanel   = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        qrPanel.add(qrBtn); qrPanel.add(pushoverQrBtn);
        JPanel pushRight = new JPanel(new BorderLayout(3, 0));
        pushRight.add(tokenField, BorderLayout.CENTER); pushRight.add(qrPanel, BorderLayout.EAST);
        JPanel pushPnl = new JPanel(new BorderLayout(3, 0));
        pushPnl.add(svcBox, BorderLayout.WEST); pushPnl.add(pushRight, BorderLayout.CENTER);
        gc.gridx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1;
        form.add(pushPnl, gc); row++;

        JLabel appTokenLabel = new JLabel("  App Token:");
        gc.gridx = 0; gc.gridy = row; gc.fill = GridBagConstraints.NONE; gc.weightx = 0;
        form.add(appTokenLabel, gc);
        gc.gridx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1;
        form.add(appTokenField, gc); row++;

        JLabel pushHintLabel = new JLabel();
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2; form.add(pushHintLabel, gc); gc.gridwidth = 1; row++;

        Runnable updatePushUI = () -> {
            boolean isNtfy = "ntfy".equals(svcBox.getSelectedItem());
            qrBtn.setVisible(isNtfy);         pushoverQrBtn.setVisible(!isNtfy);
            appTokenLabel.setVisible(!isNtfy); appTokenField.setVisible(!isNtfy);
            pushHintLabel.setText(isNtfy
                ? "<html><font color=gray size=-1>📷 QR 구독 버튼 → 스마트폰 카메라로 스캔 → ntfy 앱 자동 구독!</font></html>"
                : "<html><font color=gray size=-1>📷 QR 가입 버튼 → Pushover 가입/앱 설치<br>"
                  + "가입 후 User Key 위 입력란에, App Token 아래 입력란에 입력</font></html>");
        };
        updatePushUI.run();
        svcBox.addActionListener(e -> updatePushUI.run());

        // 카카오톡
        JCheckBox kakaoCb = new JCheckBox("카카오톡 나에게 보내기", a.useKakao);
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2; form.add(kakaoCb, gc); gc.gridwidth = 1; row++;

        // 텔레그램
        JCheckBox telegramCb = new JCheckBox("텔레그램 알림", a.useTelegram);
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2; form.add(telegramCb, gc); gc.gridwidth = 1; row++;

        gc.gridx = 0; gc.gridy = row; gc.fill = GridBagConstraints.NONE; gc.weightx = 0;
        form.add(new JLabel("  Chat ID:"), gc);
        JTextField telegramChatIdField = new JTextField(a.telegramChatId, 22);
        telegramChatIdField.setToolTipText("텔레그램에서 @userinfobot 에게 /start 보내면 Chat ID 확인 가능");
        gc.gridx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1;
        form.add(telegramChatIdField, gc); row++;

        // 이메일
        JCheckBox emailCb = new JCheckBox("이메일 발송", a.useEmail);
        gc.gridx = 0; gc.gridy = row; gc.gridwidth = 2; form.add(emailCb, gc); gc.gridwidth = 1; row++;

        gc.gridx = 0; gc.gridy = row; gc.fill = GridBagConstraints.NONE; gc.weightx = 0;
        form.add(new JLabel("  수신 주소:"), gc);
        String defaultEmailTo = !a.emailTo.isEmpty() ? a.emailTo
            : !gmail.lastTo.isEmpty() ? gmail.lastTo : "";
        JTextField emailToField = new JTextField(defaultEmailTo, 22);
        gc.gridx = 1; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1;
        form.add(emailToField, gc); row++;

        // 활성화
        gc.gridx = 0; gc.gridy = row; gc.fill = GridBagConstraints.NONE; gc.weightx = 0;
        JCheckBox enabledCb = new JCheckBox("활성화", a.enabled);
        gc.gridwidth = 2; form.add(enabledCb, gc); gc.gridwidth = 1;

        // 필드 → AlarmEntry 저장
        Runnable applyFields = () -> {
            a.hour           = (int) hSpin.getValue();
            a.minute         = (int) mSpin.getValue();
            a.label          = labelField.getText().trim();
            for (int i = 0; i < 7; i++) a.days[i] = dayCb[i].isSelected();
            a.soundFile      = sndField.getText().trim();
            a.usePush        = pushCb.isSelected();
            a.pushService    = (String) svcBox.getSelectedItem();
            a.pushToken      = tokenField.getText().trim();
            a.pushAppToken   = appTokenField.getText().trim();
            a.useKakao       = kakaoCb.isSelected();
            a.useTelegram    = telegramCb.isSelected();
            a.telegramChatId = telegramChatIdField.getText().trim();
            a.useEmail       = emailCb.isSelected();
            a.emailTo        = emailToField.getText().trim();
            a.enabled        = enabledCb.isSelected();
        };

        boolean[] ok = {false};

        JButton scheduleBtn = new JButton("⏰ 보내기 예약");
        scheduleBtn.setToolTipText("알람을 저장하고 예약 시간에 알림을 보냅니다");
        scheduleBtn.addActionListener(e -> { applyFields.run(); ok[0] = true; ed.dispose(); });

        JButton sendNowBtn = new JButton("📤 지금 보내기");
        sendNowBtn.setOpaque(true);
        sendNowBtn.setBorderPainted(false);
        sendNowBtn.setBackground(new Color(52, 168, 83));
        sendNowBtn.setForeground(Color.WHITE);
        sendNowBtn.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
        sendNowBtn.setToolTipText("저장하고 지금 즉시 알림을 전송합니다");
        sendNowBtn.addActionListener(e -> {
            applyFields.run(); ok[0] = true; ed.dispose();
            fireAlarmNow(a, parent);
        });

        JButton canBtn = new JButton("취소");
        canBtn.addActionListener(e -> ed.dispose());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnRow.add(sendNowBtn); btnRow.add(scheduleBtn); btnRow.add(canBtn);

        ed.add(new JScrollPane(form), BorderLayout.CENTER);
        ed.add(btnRow, BorderLayout.SOUTH);
        host.prepareDialog(ed);
        ed.setVisible(true);
        return ok[0];
    }

    // ═══════════════════════════════════════════════════════════
    //  유틸
    // ═══════════════════════════════════════════════════════════

    private String buildDayLabel(AlarmEntry a) {
        boolean allOff = true;
        for (boolean b : a.days) if (b) { allOff = false; break; }
        if (allOff) return "[매일]";
        String[] dn = {"일","월","화","수","목","금","토"};
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 7; i++) if (a.days[i]) sb.append(dn[i]);
        return sb.append("]").toString();
    }
}
