import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

// ═══════════════════════════════════════════════════════════
//  AppLogger - 모든 콘솔 출력을 로그 파일에 동시 기록
//  로그 폴더: <실행폴더>/log/
//  파일명: <실행파일명>_yyyyMMdd_HHmmss.txt
// ═══════════════════════════════════════════════════════════
public class AppLogger {

    private static PrintWriter  writer      = null;
    private static String       logFilePath = "";
    private static String       exeFilePath = "";
    private static final Object LOCK        = new Object();
    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    /** 로거 초기화 - main() 가장 먼저 호출 */
    public static void init() {
        // ① 실행 파일 경로 탐색 (jar / exe / class)
        String exePath = resolveExePath();
        exeFilePath = exePath != null ? exePath : "(unknown)";

        // ② 실행 폴더 결정
        File exeFile = exePath != null ? new File(exePath) : null;
        File runDir  = (exeFile != null && exeFile.getParentFile() != null)
                       ? exeFile.getParentFile()
                       : new File(System.getProperty("user.dir"));

        // ③ log 하위 폴더 생성
        File logDir = new File(runDir, "log");
        if (!logDir.exists()) logDir.mkdirs();

        // ④ 로그 파일명: <실행파일 기본명>_yyyyMMdd_HHmmss.txt
        String baseName  = (exeFile != null) ? stripExt(exeFile.getName()) : "app";
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName  = baseName + "_" + timestamp + ".txt";
        File   logFile   = new File(logDir, fileName);
        logFilePath = logFile.getAbsolutePath();

        // ⑤ PrintWriter 열기 (UTF-8, 자동 flush)
        try {
            writer = new PrintWriter(new OutputStreamWriter(
                         new FileOutputStream(logFile, true), "UTF-8"), true);
        } catch (Exception e) {
            System.err.println("[AppLogger] 로그 파일 열기 실패: " + e.getMessage());
            return;
        }

        // ⑥ System.out / System.err 를 Tee 스트림으로 교체 (콘솔 + 파일 동시 출력)
        // ★ "UTF-8" 지정 필수 - Windows 기본 인코딩(CP949)이면 한글이 깨짐
        final PrintStream originalOut = System.out;
        final PrintStream tee;
        try {
            tee = new PrintStream(originalOut, true, "UTF-8") {
                private final StringBuilder lineBuf = new StringBuilder();

                @Override
                public void write(byte[] buf, int off, int len) {
                    super.write(buf, off, len);
                    String s;
                    try { s = new String(buf, off, len, "UTF-8"); }
                    catch (Exception e) { s = new String(buf, off, len); }
                    synchronized (lineBuf) {
                        for (int i = 0; i < s.length(); i++) {
                            char c = s.charAt(i);
                            if (c == '\n') {
                                String line = lineBuf.toString();
                                lineBuf.setLength(0);
                                if (!isSuppressed(line)) writeToFile(line);
                            } else if (c != '\r') {
                                lineBuf.append(c);
                            }
                        }
                    }
                }
            };
        } catch (Exception e) {
            System.err.println("[AppLogger] tee 스트림 생성 실패: " + e.getMessage());
            return;
        }
        System.setOut(tee);
        System.setErr(tee);

        System.out.println("[AppLogger] 초기화 완료");
        System.out.println("[AppLogger] 실행 파일: " + exeFilePath);
        System.out.println("[AppLogger] 로그 파일: " + logFilePath);
    }

    /**
     * 로그 파일에 기록하지 않을 노이즈 라인 판정.
     * - [Telegram Poll] 정상 응답({"ok":true,"result":[]}) 은 생략
     * - 필요 시 패턴 추가 가능
     */
    private static boolean isSuppressed(String msg) {
        if (msg == null) return false;
        // Telegram 정상 폴링 (결과 없음) - 5초마다 발생하여 로그를 불필요하게 채움
        if (msg.contains("[Telegram Poll]") && msg.contains("\"ok\":true")
                && msg.contains("\"result\":[]")) return true;
        return false;
    }

    /** writer 에만 직접 기록 (타임스탬프 포함) */
    public static void writeToFile(String msg) {
        if (writer == null) return;
        synchronized (LOCK) {
            writer.println("[" + TS.format(new Date()) + "] " + msg);
        }
    }

    /** 로그 파일 전체 경로 반환 */
    public static String getLogFilePath() { return logFilePath; }

    /** 실행 파일 전체 경로 반환 */
    public static String getExeFilePath() { return exeFilePath; }

    /** 로거 닫기 */
    public static void close() {
        if (writer != null) { writer.flush(); writer.close(); }
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────

    private static String resolveExePath() {
        // ① sun.java.command
        try {
            String sc = System.getProperty("sun.java.command", "").trim();
            if (sc.endsWith(".jar") || sc.endsWith(".exe"))
                return new File(sc.split("\\s+")[0]).getAbsolutePath();
        } catch (Exception ignored) {}
        // ③ CodeSource (class 실행 포함)
        try {
            java.security.CodeSource cs =
                AppLogger.class.getProtectionDomain().getCodeSource();
            if (cs != null)
                return new File(cs.getLocation().toURI()).getAbsolutePath();
        } catch (Exception ignored) {}
        return null;
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }
}
