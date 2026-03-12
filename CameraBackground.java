import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.imageio.ImageIO;

/**
 * CameraBackground - IP Webcam MJPEG 스트림 수신
 *
 * ── 사용법 ──────────────────────────────────────────────────────
 *   CameraBackground cam = new CameraBackground(frameConsumer);
 *   cam.start("http://192.168.x.x:8080/video");   // 스트리밍 시작
 *   cam.stop();                                     // 중지
 *   cam.capture(saveDir);                           // 현재 프레임 저장
 *
 * ── IP Webcam MJPEG 포맷 ─────────────────────────────────────
 *   Content-Type: multipart/x-mixed-replace; boundary=--myboundary
 *   각 파트: --myboundary\r\nContent-Type: image/jpeg\r\n\r\n<JPEG bytes>\r\n
 *
 * ── 저장 파일명 ──────────────────────────────────────────────
 *   img/cam_yyyyMMdd_HHmmss_SSS.jpg
 */
public class CameraBackground {

    /** 새 프레임 도착 시 콜백 */
    public interface FrameListener {
        void onFrame(BufferedImage frame);
    }

    private final FrameListener listener;
    private volatile boolean    running  = false;
    private volatile BufferedImage lastFrame = null;
    private Thread readerThread;

    public CameraBackground(FrameListener listener) {
        this.listener = listener;
    }

    // ── 공개 API ─────────────────────────────────────────────────

    public boolean isRunning() { return running; }

    public BufferedImage getLastFrame() { return lastFrame; }

    /** MJPEG 스트림 수신 시작 */
    public void start(String streamUrl) {
        stop(); // 기존 스트림 중지
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
        }, "CameraBackground-Reader");
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

    // ── MJPEG 스트림 파싱 ─────────────────────────────────────────

    private void connectAndRead(String streamUrl) throws Exception {
        @SuppressWarnings("deprecation")
        URL url = new URL(streamUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);
        conn.connect();

        // boundary 추출: Content-Type: multipart/x-mixed-replace; boundary=--myboundary
        String contentType = conn.getContentType();
        String boundary = "--myboundary"; // IP Webcam 기본값
        if (contentType != null && contentType.contains("boundary=")) {
            boundary = contentType.split("boundary=")[1].trim();
            if (!boundary.startsWith("--")) boundary = "--" + boundary;
        }

        InputStream in = new BufferedInputStream(conn.getInputStream(), 65536);

        while (running) {
            // boundary 라인까지 스킵
            if (!skipToBoundary(in, boundary)) break;

            // 헤더 스킵 (빈 줄까지)
            int contentLength = -1;
            String hLine;
            while (!(hLine = readLine(in)).isEmpty()) {
                if (hLine.toLowerCase().startsWith("content-length:")) {
                    try { contentLength = Integer.parseInt(hLine.split(":")[1].trim()); }
                    catch (Exception ignored) {}
                }
            }

            // JPEG 데이터 읽기
            byte[] jpegBytes;
            if (contentLength > 0) {
                jpegBytes = readBytes(in, contentLength);
            } else {
                jpegBytes = readUntilBoundary(in, boundary);
            }
            if (jpegBytes == null || jpegBytes.length == 0) continue;

            // BufferedImage 변환
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

    /** boundary 문자열이 나올 때까지 스킵. 발견하면 true 반환 */
    private boolean skipToBoundary(InputStream in, String boundary) throws IOException {
        while (running) {
            String line = readLine(in);
            if (line == null) return false;
            if (line.startsWith(boundary)) return true;
        }
        return false;
    }

    /** InputStream에서 한 줄 읽기 (\r\n 또는 \n) */
    private String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
        }
        return c == -1 ? null : sb.toString();
    }

    /** 정확히 len 바이트 읽기 */
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

    /** boundary가 나올 때까지 읽어서 JPEG 바이트 반환 */
    private byte[] readUntilBoundary(InputStream in, String boundary) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(32768);
        byte[] bnd = ("\r\n" + boundary).getBytes("UTF-8");
        int    idx = 0;
        int    c;
        while ((c = in.read()) != -1) {
            if (c == bnd[idx]) {
                idx++;
                if (idx == bnd.length) {
                    // boundary 발견 → 지금까지 쌓인 데이터 반환
                    byte[] data = baos.toByteArray();
                    // 뒤쪽 \r\n 제거
                    int end = data.length;
                    if (end >= 2 && data[end-2] == '\r' && data[end-1] == '\n') end -= 2;
                    return java.util.Arrays.copyOf(data, end);
                }
            } else {
                // 매칭 실패 → 이전에 비교했던 바이트들 flush
                if (idx > 0) {
                    baos.write(bnd, 0, idx);
                    idx = 0;
                }
                baos.write(c);
            }
        }
        return baos.toByteArray();
    }
}
