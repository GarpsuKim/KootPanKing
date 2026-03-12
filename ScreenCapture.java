import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.swing.*;

/**
 * ScreenCapture - 화면 캡처 및 이미지 표시 유틸리티
 *
 * ── 제공 기능 ────────────────────────────────────────────────
 *   ① captureClockScreen()  : ClockPanel 만 캡처 (시계 이미지)
 *   ② captureFullScreen()   : 모든 모니터 전체 영역 캡처
 *   ③ captureMonitor(int)   : 특정 모니터 캡처
 *   ④ showImageWindow(File) : 수신 이미지를 서브 윈도우에 표시
 *
 * ── 저장 경로 ────────────────────────────────────────────────
 *   System.getProperty("java.io.tmpdir") 하위 타임스탬프 PNG 파일
 *
 * ── TelegramBot 연동 ─────────────────────────────────────────
 *   TelegramBot.CommandHandler 의 captureClockScreen / captureFullScreen /
 *   captureMonitor 콜백을 이 클래스로 위임한다.
 *
 * ── 사용법 ───────────────────────────────────────────────────
 *   ScreenCapture sc = new ScreenCapture(clockPanel);
 *   File f = sc.captureClockScreen();
 *   File f = sc.captureFullScreen();
 *   File f = sc.captureMonitor(0);
 *   sc.showImageWindow(file);
 */
public class ScreenCapture {

    private final JPanel clockPanel;   // 시계 패널 (ClockPanel)

    /** 여러 이미지 창이 겹치지 않도록 오프셋을 순환 */
    private int imageWindowOffset = 0;

    // ── 생성자 ───────────────────────────────────────────────

    public ScreenCapture(JPanel clockPanel) {
        this.clockPanel = clockPanel;
    }

    // ── 공개 API ─────────────────────────────────────────────

    /**
     * ClockPanel 만 캡처하여 임시 PNG 파일로 저장.
     * @return 저장된 PNG 파일
     */
    public File captureClockScreen() throws Exception {
        int w = clockPanel.getWidth();
        int h = clockPanel.getHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        clockPanel.paint(g2);
        g2.dispose();

        File outFile = new File(System.getProperty("java.io.tmpdir"),
            "clock_capture_" + System.currentTimeMillis() + ".png");
        javax.imageio.ImageIO.write(img, "PNG", outFile);
        return outFile;
    }

    /**
     * 모든 모니터를 포함한 전체 화면을 캡처.
     * @return 저장된 PNG 파일
     */
    public File captureFullScreen() throws Exception {
        Rectangle fullBounds = new Rectangle();
        for (GraphicsDevice gd : GraphicsEnvironment
                .getLocalGraphicsEnvironment().getScreenDevices()) {
            fullBounds = fullBounds.union(gd.getDefaultConfiguration().getBounds());
        }
        BufferedImage img = new Robot().createScreenCapture(fullBounds);
        File outFile = new File(System.getProperty("java.io.tmpdir"),
            "screenshot_" + System.currentTimeMillis() + ".png");
        javax.imageio.ImageIO.write(img, "PNG", outFile);
        return outFile;
    }

    /**
     * 특정 모니터를 캡처.
     * @param monitorIndex 0 부터 시작하는 모니터 인덱스
     * @return 저장된 PNG 파일
     * @throws Exception 모니터 인덱스 범위 초과 시
     */
    public File captureMonitor(int monitorIndex) throws Exception {
        GraphicsDevice[] screens = GraphicsEnvironment
            .getLocalGraphicsEnvironment().getScreenDevices();
        if (monitorIndex >= screens.length)
            throw new Exception("모니터 " + (monitorIndex + 1) + "이 없습니다. "
                + "(연결된 모니터: " + screens.length + "개)");
        Rectangle bounds = screens[monitorIndex].getDefaultConfiguration().getBounds();
        BufferedImage img = new Robot().createScreenCapture(bounds);
        File outFile = new File(System.getProperty("java.io.tmpdir"),
            "monitor" + (monitorIndex + 1) + "_" + System.currentTimeMillis() + ".png");
        javax.imageio.ImageIO.write(img, "PNG", outFile);
        return outFile;
    }

    /**
     * 이미지 파일을 새 JFrame 서브 윈도우에 표시.
     * 화면 크기의 80% 를 최대 크기로 자동 스케일.
     * 여러 창이 열릴 경우 30px 씩 오프셋하여 겹침 방지.
     *
     * @param imageFile 표시할 이미지 파일
     */
    public void showImageWindow(File imageFile) {
        try {
            BufferedImage img = javax.imageio.ImageIO.read(imageFile);
            if (img == null) {
                System.out.println("[ImageWindow] 이미지 파싱 실패: " + imageFile.getName());
                return;
            }

            // 화면 크기의 80% 를 최대로 제한하여 스케일
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int maxW = (int)(screen.width  * 0.80);
            int maxH = (int)(screen.height * 0.80);
            int imgW = img.getWidth();
            int imgH = img.getHeight();

            ImageIcon icon;
            if (imgW > maxW || imgH > maxH) {
                double scale = Math.min((double) maxW / imgW, (double) maxH / imgH);
                imgW = (int)(imgW * scale);
                imgH = (int)(imgH * scale);
                Image scaled = img.getScaledInstance(imgW, imgH, Image.SCALE_SMOOTH);
                icon = new ImageIcon(scaled);
            } else {
                icon = new ImageIcon(img);
            }

            JFrame frame = new JFrame("📷 " + imageFile.getName());
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setAlwaysOnTop(true);
            JLabel label = new JLabel(icon);
            label.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
            frame.add(label);
            frame.pack();

            // 화면 중앙 기준, 창마다 30px 씩 오프셋 (최대 9단계 순환)
            int ox = imageWindowOffset * 30;
            int oy = imageWindowOffset * 30;
            int x  = Math.max(0, Math.min((screen.width  - frame.getWidth())  / 2 + ox,
                                           screen.width  - frame.getWidth()));
            int y  = Math.max(0, Math.min((screen.height - frame.getHeight()) / 2 + oy,
                                           screen.height - frame.getHeight()));
            frame.setLocation(x, y);
            imageWindowOffset = (imageWindowOffset + 1) % 10;

            frame.setVisible(true);
            System.out.println("[ImageWindow] 표시: " + imageFile.getName());

        } catch (Exception e) {
            System.out.println("[ImageWindow] 표시 실패: " + e.getMessage());
        }
    }
}
