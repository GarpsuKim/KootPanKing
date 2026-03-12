import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * CaptureManager - 화면 캡처 + IP 카메라 스트림 통합 클래스
 *
 * ── 포함된 구 클래스 ──────────────────────────────────────────
 *   ① ScreenCapture    : 화면/시계 캡처 및 이미지 표시 유틸리티  (구 ScreenCapture.java)
 *   ② CameraBackground : IP Webcam MJPEG 스트림 수신           (구 CameraBackground.java)
 *
 * ── 외부 참조 변경사항 ────────────────────────────────────────
 *   구 ScreenCapture(panel)     →  new CaptureManager(panel)  (동일 생성자)
 *   구 CameraBackground(cb)     →  new CaptureManager.Camera(cb)
 *   구 CameraBackground.FrameListener  →  CaptureManager.Camera.FrameListener
 *
 * ══════════════════════════════════════════════════════════════
 *  ScreenCapture 기능 (CaptureManager 인스턴스 메서드)
 * ══════════════════════════════════════════════════════════════
 *   captureClockScreen()  : ClockPanel 만 캡처 → 임시 PNG
 *   captureFullScreen()   : 전체 모니터 캡처   → 임시 PNG
 *   captureMonitor(int)   : 특정 모니터 캡처   → 임시 PNG
 *   showImageWindow(File) : 수신 이미지를 서브 윈도우에 표시
 *
 * ══════════════════════════════════════════════════════════════
 *  CameraBackground 기능 (CaptureManager.Camera 이너 클래스)
 * ══════════════════════════════════════════════════════════════
 *   cam.start(url)    : MJPEG 스트림 수신 시작
 *   cam.stop()        : 스트림 중지
 *   cam.capture(dir)  : 현재 프레임을 dir/img/cam_*.jpg 저장
 *   cam.isRunning()   : 스트림 실행 여부
 *   cam.getLastFrame(): 마지막 수신 프레임
 */
public class CaptureManager {

    // ── 시계 패널 참조 (ScreenCapture 기능용) ────────────────
    private final JPanel clockPanel;

    /** 여러 이미지 창이 겹치지 않도록 오프셋 순환 */
    private int imageWindowOffset = 0;

    // ── 생성자 ───────────────────────────────────────────────

    public CaptureManager(JPanel clockPanel) {
        this.clockPanel = clockPanel;
    }

    // ═══════════════════════════════════════════════════════════
    //  ScreenCapture 기능 — 화면 캡처 및 이미지 표시 (구 ScreenCapture.java)
    // ═══════════════════════════════════════════════════════════

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
        ImageIO.write(img, "PNG", outFile);
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
        ImageIO.write(img, "PNG", outFile);
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
        ImageIO.write(img, "PNG", outFile);
        return outFile;
    }

    /**
     * 이미지 파일을 새 JFrame 서브 윈도우에 표시.
     * 화면 크기의 80% 를 최대 크기로 자동 스케일.
     * 여러 창이 열릴 경우 30px 씩 오프셋하여 겹침 방지.
     * @param imageFile 표시할 이미지 파일
     */
    public void showImageWindow(File imageFile) {
        try {
            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) {
                System.out.println("[ImageWindow] 이미지 파싱 실패: " + imageFile.getName());
                return;
            }

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

    // ═══════════════════════════════════════════════════════════
    //  Camera — IP Webcam MJPEG 스트림 수신 (구 CameraBackground.java)
    //
    //  IP Webcam MJPEG 포맷:
    //    Content-Type: multipart/x-mixed-replace; boundary=--myboundary
    //    각 파트: --myboundary\r\nContent-Type: image/jpeg\r\n\r\n<JPEG>\r\n
    //
    //  저장 파일명: img/cam_yyyyMMdd_HHmmss_SSS.jpg
    //
    //  사용법:
    //    CaptureManager.Camera cam = new CaptureManager.Camera(frameListener);
    //    cam.start("http://192.168.x.x:8080/video");
    //    cam.stop();
    //    cam.capture(saveDir);
    // ═══════════════════════════════════════════════════════════

    public static class Camera {

        /** 새 프레임 도착 시 콜백 */
        public interface FrameListener {
            void onFrame(BufferedImage frame);
        }

        private final FrameListener    listener;
        private volatile boolean       running   = false;
        private volatile BufferedImage lastFrame = null;
        private Thread readerThread;

        public Camera(FrameListener listener) {
            this.listener = listener;
        }

        public boolean isRunning()          { return running; }
        public BufferedImage getLastFrame() { return lastFrame; }

        /** MJPEG 스트림 수신 시작 */
        public void start(String streamUrl) {
            stop();
            running = true;
            readerThread = new Thread(() -> {
                while (running) {
                    try {
                        connectAndRead(streamUrl);
                    } catch (Exception e) {
                        if (running) {
                            System.out.println("[Camera] 연결 오류, 3초 후 재시도: " + e.getMessage());
                            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                        }
                    }
                }
            }, "Camera-Reader");
            readerThread.setDaemon(true);
            readerThread.start();
            System.out.println("[Camera] 스트림 시작: " + streamUrl);
        }

        /** 스트림 중지 */
        public void stop() {
            running = false;
            if (readerThread != null) {
                readerThread.interrupt();
                readerThread = null;
            }
            lastFrame = null;
            System.out.println("[Camera] 스트림 중지");
        }

        /**
         * 현재 프레임을 saveDir/img/ 폴더에 저장.
         * 파일명: cam_yyyyMMdd_HHmmss_SSS.jpg
         * @return 저장된 파일 경로 (실패 시 null)
         */
        public String capture(File saveDir) {
            BufferedImage frame = lastFrame;
            if (frame == null) {
                System.out.println("[Camera] 캡처 실패: 수신된 프레임 없음");
                return null;
            }
            try {
                File imgDir = new File(saveDir, "img");
                if (!imgDir.exists()) imgDir.mkdirs();

                String ts   = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
                File   file = new File(imgDir, "cam_" + ts + ".jpg");
                ImageIO.write(frame, "jpg", file);
                System.out.println("[Camera] 저장 완료: " + file.getAbsolutePath());
                return file.getAbsolutePath();
            } catch (Exception e) {
                System.out.println("[Camera] 저장 오류: " + e.getMessage());
                return null;
            }
        }

        // ── MJPEG 스트림 파싱 ───────────────────────────────

        private void connectAndRead(String streamUrl) throws Exception {
            @SuppressWarnings("deprecation")
            URL url = new URL(streamUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.connect();

            String contentType = conn.getContentType();
            String boundary = "--myboundary";
            if (contentType != null && contentType.contains("boundary=")) {
                boundary = contentType.split("boundary=")[1].trim();
                if (!boundary.startsWith("--")) boundary = "--" + boundary;
            }

            InputStream in = new BufferedInputStream(conn.getInputStream(), 65536);

            while (running) {
                if (!skipToBoundary(in, boundary)) break;

                int contentLength = -1;
                String hLine;
                while (!(hLine = readLine(in)).isEmpty()) {
                    if (hLine.toLowerCase().startsWith("content-length:")) {
                        try { contentLength = Integer.parseInt(hLine.split(":")[1].trim()); }
                        catch (Exception ignored) {}
                    }
                }

                byte[] jpegBytes;
                if (contentLength > 0) {
                    jpegBytes = readBytes(in, contentLength);
                } else {
                    jpegBytes = readUntilBoundary(in, boundary);
                }
                if (jpegBytes == null || jpegBytes.length == 0) continue;

                try {
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpegBytes));
                    if (img != null) {
                        lastFrame = img;
                        if (listener != null) listener.onFrame(img);
                    }
                } catch (Exception ignored) {}
            }
            conn.disconnect();
        }

        private boolean skipToBoundary(InputStream in, String boundary) throws IOException {
            while (running) {
                String line = readLine(in);
                if (line == null) return false;
                if (line.startsWith(boundary)) return true;
            }
            return false;
        }

        private String readLine(InputStream in) throws IOException {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = in.read()) != -1) {
                if (c == '\n') break;
                if (c != '\r') sb.append((char) c);
            }
            return c == -1 ? null : sb.toString();
        }

        private byte[] readBytes(InputStream in, int len) throws IOException {
            byte[] buf = new byte[len];
            int    off = 0;
            while (off < len) {
                int n = in.read(buf, off, len - off);
                if (n < 0) break;
                off += n;
            }
            return buf;
        }

        private byte[] readUntilBoundary(InputStream in, String boundary) throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(32768);
            byte[] bnd = ("\r\n" + boundary).getBytes("UTF-8");
            int    idx = 0;
            int    c;
            while ((c = in.read()) != -1) {
                if (c == bnd[idx]) {
                    idx++;
                    if (idx == bnd.length) {
                        byte[] data = baos.toByteArray();
                        int end = data.length;
                        if (end >= 2 && data[end-2] == '\r' && data[end-1] == '\n') end -= 2;
                        return java.util.Arrays.copyOf(data, end);
                    }
                } else {
                    if (idx > 0) { baos.write(bnd, 0, idx); idx = 0; }
                    baos.write(c);
                }
            }
            return baos.toByteArray();
        }
    }
}
