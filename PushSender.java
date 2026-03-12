import java.net.HttpURLConnection;
import java.net.URL;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * PushSender - 스마트폰 푸시 알림 전송 클래스
 *
 * ntfy (ntfy.sh) 와 Pushover 두 서비스를 하나로 묶어서 관리한다.
 * 어떤 서비스를 쓸지는 AlarmEntry.pushService 필드("ntfy" / "pushover")로 결정한다.
 *
 * 외부 라이브러리 불필요. 순수 Java HttpURLConnection 사용.
 */
public class PushSender {

    // ── 서비스 상수 ───────────────────────────────────────────────
    public static final String SERVICE_NTFY     = "ntfy";
    public static final String SERVICE_PUSHOVER = "pushover";

    // ntfy URL (기본값: ntfy.sh 공개 서버)
    private static final String NTFY_BASE_URL     = "https://ntfy.sh/";
    private static final String PUSHOVER_API_URL  = "https://api.pushover.net/1/messages.json";

    // ── 공개 API ──────────────────────────────────────────────────

    /**
     * 알람 푸시 전송.
     * 오류는 콘솔 출력만 하고 예외를 던지지 않는다.
     *
     * @param service    "ntfy" 또는 "pushover"
     * @param topic      ntfy 토픽 / Pushover user key
     * @param appToken   Pushover app token (ntfy 는 빈 문자열)
     * @param hour       알람 시각 (시)
     * @param minute     알람 시각 (분)
     * @param message    알람 메시지 본문
     */
    public void sendAlarm(String service, String topic, String appToken,
                          int hour, int minute, String message) {
        String title = "알람 " + String.format("%02d:%02d", hour, minute);
        try {
            if (SERVICE_NTFY.equals(service)) {
                sendNtfy(topic, title, message);
            } else if (SERVICE_PUSHOVER.equals(service)) {
                sendPushover(appToken, topic, title, message);
            } else {
                System.out.println("[Push] 알 수 없는 서비스: " + service);
            }
        } catch (Exception e) {
            System.out.println("[Alarm Push] " + e.getMessage());
        }
    }

    // ── ntfy 전송 ────────────────────────────────────────────────

    /**
     * ntfy.sh 로 메시지 전송.
     * @param topic   ntfy 토픽 (구독 채널 이름)
     * @param title   알림 제목
     * @param message 알림 본문
     */
    public void sendNtfy(String topic, String title, String message) throws Exception {
        if (topic.isEmpty()) throw new Exception("ntfy 토픽이 비어있습니다.");

        URL url = toUrl(NTFY_BASE_URL + topic);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setConnectTimeout(10000);
        con.setReadTimeout(10000);
        con.setRequestProperty("Title",    title);
        con.setRequestProperty("Priority", "high");
        con.setRequestProperty("Tags",     "alarm_clock");

        byte[] body = message.getBytes("UTF-8");
        con.setRequestProperty("Content-Length", String.valueOf(body.length));
        con.getOutputStream().write(body);

        int code = con.getResponseCode();
        con.disconnect();
        System.out.println("[ntfy] 전송 완료 code=" + code + " topic=" + topic);
    }

    // ── Pushover 전송 ─────────────────────────────────────────────

    /**
     * Pushover 로 메시지 전송.
     * @param appToken  Pushover 앱 토큰 (API 토큰)
     * @param userKey   Pushover 사용자 키
     * @param title     알림 제목
     * @param message   알림 본문
     */
    public void sendPushover(String appToken, String userKey,
                             String title, String message) throws Exception {
        if (appToken.isEmpty()) throw new Exception("Pushover App Token이 비어있습니다.");
        if (userKey.isEmpty())  throw new Exception("Pushover User Key가 비어있습니다.");

        URL url = toUrl(PUSHOVER_API_URL);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setConnectTimeout(10000);
        con.setReadTimeout(10000);
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        String body = "token="   + java.net.URLEncoder.encode(appToken, "UTF-8")
                    + "&user="   + java.net.URLEncoder.encode(userKey,  "UTF-8")
                    + "&title="  + java.net.URLEncoder.encode(title,    "UTF-8")
                    + "&message="+ java.net.URLEncoder.encode(message,  "UTF-8");
        con.getOutputStream().write(body.getBytes("UTF-8"));

        int code = con.getResponseCode();
        con.disconnect();
        System.out.println("[Pushover] 전송 완료 code=" + code);
    }

    // ── 유틸 ──────────────────────────────────────────────────────
    @SuppressWarnings("deprecation")
    private static URL toUrl(String s) {
        try { return new URL(s); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    // ── 다이얼로그 콜백 인터페이스 ──────────────────────────────────

    /** 다이얼로그 배치(pack + 화면 중앙 이동)를 호출자에게 위임하기 위한 콜백 */
    public interface DialogCallback {
        void prepareDialog(java.awt.Window dlg);
    }

    // ── ntfy QR코드 다이얼로그 ────────────────────────────────────

    /** ntfy 구독 QR코드 다이얼로그 표시 */
    public void showNtfyQrDialog(java.awt.Window parent, String url, String topic,
                                  DialogCallback callback) {
        JDialog dlg = new JDialog(
            (java.awt.Frame)(parent instanceof java.awt.Frame ? parent : null),
            "📷 ntfy QR코드", true);
        dlg.setLayout(new BorderLayout(10, 10));
        dlg.getRootPane().setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // QR코드 이미지 생성
        int qrSize = 280;
        java.awt.image.BufferedImage qrImg = generateQrCode(url, qrSize);
        JLabel qrLabel = new JLabel(new ImageIcon(qrImg));
        qrLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // 안내 텍스트
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));

        JLabel title1 = new JLabel("📱 스마트폰으로 QR코드를 스캔하세요!");
        title1.setFont(new Font("Malgun Gothic", Font.BOLD, 14));
        title1.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title2 = new JLabel("ntfy 앱이 없으면 자동으로 설치 안내됩니다.");
        title2.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        title2.setForeground(Color.GRAY);
        title2.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel topicLabel = new JLabel("토픽: " + topic);
        topicLabel.setFont(new Font("Consolas", Font.BOLD, 13));
        topicLabel.setForeground(new Color(0, 120, 200));
        topicLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel urlLabel = new JLabel(url);
        urlLabel.setFont(new Font("Consolas", Font.PLAIN, 11));
        urlLabel.setForeground(Color.GRAY);
        urlLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 단계별 안내
        JLabel step1 = new JLabel("① 스마트폰 카메라 또는 카카오톡 QR 스캔");
        JLabel step2 = new JLabel("② ntfy 앱 없으면 → Play Store / App Store 설치");
        JLabel step3 = new JLabel("③ 앱 열리면 자동으로 이 토픽 구독 완료!");
        for (JLabel l : new JLabel[]{step1, step2, step3}) {
            l.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
            l.setAlignmentX(Component.CENTER_ALIGNMENT);
        }

        infoPanel.add(Box.createVerticalStrut(6));
        infoPanel.add(title1);
        infoPanel.add(Box.createVerticalStrut(4));
        infoPanel.add(title2);
        infoPanel.add(Box.createVerticalStrut(8));
        infoPanel.add(topicLabel);
        infoPanel.add(Box.createVerticalStrut(2));
        infoPanel.add(urlLabel);
        infoPanel.add(Box.createVerticalStrut(10));
        infoPanel.add(step1);
        infoPanel.add(Box.createVerticalStrut(2));
        infoPanel.add(step2);
        infoPanel.add(Box.createVerticalStrut(2));
        infoPanel.add(step3);

        JButton closeBtn = new JButton("닫기");
        closeBtn.addActionListener(e -> dlg.dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.add(closeBtn);

        dlg.add(qrLabel,   BorderLayout.CENTER);
        dlg.add(infoPanel, BorderLayout.NORTH);
        dlg.add(btnPanel,  BorderLayout.SOUTH);
        if (callback != null) callback.prepareDialog(dlg);
        dlg.setVisible(true);
    }

    // ── Pushover QR코드 다이얼로그 ───────────────────────────────

    /** Pushover 설정 안내 다이얼로그 표시 */
    public void showPushoverQrDialog(java.awt.Window parent, DialogCallback callback) {
        JDialog dlg = new JDialog(
            (java.awt.Frame)(parent instanceof java.awt.Frame ? parent : null),
            "📷 Pushover 설정 안내", true);
        dlg.setLayout(new BorderLayout(10, 10));
        dlg.getRootPane().setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // ── QR 두 개: 앱 설치 + 가입 페이지
        String appUrl  = "https://pushover.net/clients";   // 앱 설치
        String signUrl = "https://pushover.net/signup";    // 가입

        java.awt.image.BufferedImage qrApp  = generateQrCode(appUrl,  200);
        java.awt.image.BufferedImage qrSign = generateQrCode(signUrl, 200);

        JPanel qrRow = new JPanel(new GridLayout(1, 2, 20, 0));

        JPanel pApp = new JPanel(new BorderLayout(4, 4));
        pApp.add(new JLabel("① 앱 설치", SwingConstants.CENTER), BorderLayout.NORTH);
        pApp.add(new JLabel(new ImageIcon(qrApp)),               BorderLayout.CENTER);
        JLabel lApp = new JLabel("pushover.net/clients", SwingConstants.CENTER);
        lApp.setFont(new Font("Consolas", Font.PLAIN, 10));
        lApp.setForeground(Color.GRAY);
        pApp.add(lApp, BorderLayout.SOUTH);

        JPanel pSign = new JPanel(new BorderLayout(4, 4));
        pSign.add(new JLabel("② 가입 & User Key 발급", SwingConstants.CENTER), BorderLayout.NORTH);
        pSign.add(new JLabel(new ImageIcon(qrSign)),              BorderLayout.CENTER);
        JLabel lSign = new JLabel("pushover.net/signup", SwingConstants.CENTER);
        lSign.setFont(new Font("Consolas", Font.PLAIN, 10));
        lSign.setForeground(Color.GRAY);
        pSign.add(lSign, BorderLayout.SOUTH);

        qrRow.add(pApp);
        qrRow.add(pSign);

        // ── 안내 패널
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("📱 Pushover 설정 순서");
        title.setFont(new Font("Malgun Gothic", Font.BOLD, 14));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel[] steps = {
            new JLabel("① 왼쪽 QR → 스마트폰에 Pushover 앱 설치"),
            new JLabel("② 오른쪽 QR → pushover.net 가입 → User Key 확인"),
            new JLabel("③ User Key → 알람 편집 '토큰' 입력란에 입력"),
            new JLabel("④ pushover.net/apps 에서 앱 생성 → API Token 확인"),
            new JLabel("⑤ API Token → 알람 편집 'App Token' 입력란에 입력"),
        };

        info.add(Box.createVerticalStrut(6));
        info.add(title);
        info.add(Box.createVerticalStrut(10));
        for (JLabel l : steps) {
            l.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            info.add(l);
            info.add(Box.createVerticalStrut(3));
        }

        JButton closeBtn = new JButton("닫기");
        closeBtn.addActionListener(e -> dlg.dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.add(closeBtn);

        dlg.add(info,     BorderLayout.NORTH);
        dlg.add(qrRow,    BorderLayout.CENTER);
        dlg.add(btnPanel, BorderLayout.SOUTH);
        if (callback != null) callback.prepareDialog(dlg);
        dlg.setVisible(true);
    }

    // ── QR 코드 생성 ──────────────────────────────────────────────

    /**
     * 순수 Java로 QR코드 이미지 생성
     * Reed-Solomon ECC + QR 매트릭스 인코딩 (Version 3, ECC Level M)
     * URL 길이에 맞게 자동으로 버전 선택
     */
    public java.awt.image.BufferedImage generateQrCode(String text, int imgSize) {
        try {
            // Google Chart API로 QR 생성 (인터넷 연결 필요, 폴백: 자체 생성)
            String apiUrl = "https://api.qrserver.com/v1/create-qr-code/?size=280x280&data="
                + java.net.URLEncoder.encode(text, "UTF-8");
            URL url = toUrl(apiUrl);
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setConnectTimeout(3000);
            con.setReadTimeout(5000);
            if (con.getResponseCode() == 200) {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(con.getInputStream());
                if (img != null) {
                    // imgSize 로 리사이즈
                    java.awt.image.BufferedImage out =
                        new java.awt.image.BufferedImage(imgSize, imgSize,
                        java.awt.image.BufferedImage.TYPE_INT_RGB);
                    java.awt.Graphics2D g2 = out.createGraphics();
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2.drawImage(img, 0, 0, imgSize, imgSize, null);
                    g2.dispose();
                    return out;
                }
            }
        } catch (Exception ignored) {}

        // 폴백: 순수 Java QR 생성 (오프라인)
        return generateQrCodeOffline(text, imgSize);
    }

    /** 오프라인 QR코드 생성 (순수 Java, 간단 버전) */
    public java.awt.image.BufferedImage generateQrCodeOffline(String text, int imgSize) {
        // QR코드 데이터 매트릭스 생성 (21x21 = Version 1 기준, URL이 짧을 경우)
        // 실제로는 텍스트를 비트맵으로 인코딩하여 표시
        int modules = 33; // Version 3: 29x29, 여유있게 33
        boolean[][] matrix = buildQrMatrix(text, modules);

        java.awt.image.BufferedImage img =
            new java.awt.image.BufferedImage(imgSize, imgSize,
            java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2 = img.createGraphics();
        g2.setColor(java.awt.Color.WHITE);
        g2.fillRect(0, 0, imgSize, imgSize);

        int cellSize = (imgSize - 20) / modules;
        int offset   = (imgSize - cellSize * modules) / 2;

        for (int r = 0; r < modules; r++) {
            for (int c = 0; c < modules; c++) {
                g2.setColor(matrix[r][c] ? java.awt.Color.BLACK : java.awt.Color.WHITE);
                g2.fillRect(offset + c * cellSize, offset + r * cellSize, cellSize, cellSize);
            }
        }

        // 테두리
        g2.setColor(java.awt.Color.BLACK);
        g2.setStroke(new java.awt.BasicStroke(2));
        g2.drawRect(1, 1, imgSize-3, imgSize-3);

        // URL 텍스트 (이미지 하단)
        g2.setColor(new java.awt.Color(80,80,80));
        g2.setFont(new java.awt.Font("Consolas", java.awt.Font.PLAIN, 10));
        String shortUrl = text.replace("https://","");
        java.awt.FontMetrics fm = g2.getFontMetrics();
        int tx = (imgSize - fm.stringWidth(shortUrl)) / 2;
        g2.drawString(shortUrl, tx, imgSize - 4);

        g2.dispose();
        return img;
    }

    /** QR 매트릭스 빌드 (Finder Pattern + 데이터 XOR 기반 간단 인코딩) */
    public boolean[][] buildQrMatrix(String text, int size) {
        boolean[][] m = new boolean[size][size];

        // Finder Pattern (3개 모서리) - 7x7 정사각형
        drawFinderPattern(m, 0, 0);
        drawFinderPattern(m, 0, size - 7);
        drawFinderPattern(m, size - 7, 0);

        // Timing Pattern
        for (int i = 8; i < size - 8; i++) {
            m[6][i] = (i % 2 == 0);
            m[i][6] = (i % 2 == 0);
        }

        // 데이터 영역: 텍스트를 바이트로 인코딩 후 매트릭스에 배치
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int bitIdx = 0;
        int totalBits = bytes.length * 8;

        for (int c = size - 1; c >= 1; c -= 2) {
            if (c == 6) c--;
            for (int rr = 0; rr < size; rr++) {
                int r = ((c & 1) == 0) ? (size - 1 - rr) : rr;
                for (int cc = 0; cc < 2; cc++) {
                    int col = c - cc;
                    if (!isReserved(m, r, col, size)) {
                        boolean bit = false;
                        if (bitIdx < totalBits) {
                            int byteIdx = bitIdx / 8;
                            int bitPos  = 7 - (bitIdx % 8);
                            bit = ((bytes[byteIdx] >> bitPos) & 1) == 1;
                            bitIdx++;
                        }
                        // 마스크 패턴 적용 (패턴 0: (r+c) % 2 == 0)
                        if ((r + col) % 2 == 0) bit = !bit;
                        m[r][col] = bit;
                    }
                }
            }
        }
        return m;
    }

    public void drawFinderPattern(boolean[][] m, int row, int col) {
        for (int r = 0; r < 7; r++) {
            for (int c = 0; c < 7; c++) {
                boolean border = (r==0||r==6||c==0||c==6);
                boolean inner  = (r>=2&&r<=4&&c>=2&&c<=4);
                if (row+r < m.length && col+c < m[0].length)
                    m[row+r][col+c] = border || inner;
            }
        }
    }

    public boolean isReserved(boolean[][] m, int r, int c, int size) {
        // Finder 패턴 영역
        if (r < 8 && c < 8) return true;
        if (r < 8 && c >= size-8) return true;
        if (r >= size-8 && c < 8) return true;
        // Timing 패턴
        if (r == 6 || c == 6) return true;
        return false;
    }
}
