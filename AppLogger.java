import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

// ═══════════════════════════════════════════════════════════
//  AppLogger - 모든 콘솔 출력을 로그 파일에 동시 기록
//
//  로그 폴더 : <실행폴더>/log/
//  파일명    : <실행파일명>_yyyyMMdd_HHmmss.txt
//
//  개선 내역
//  B. System.err 별도 [ERR] 태그 분리
//     - out Tee 와 err Tee 를 독립적으로 구성
//     - err 경유 라인은 파일에 [ERR] 접두어 추가
//  C. 스레드명 포함
//     - 파일 기록 포맷: [타임스탬프] [스레드명] [Class#method] 메시지
//  G. resolveExePath() ② 경로 복구
//     - 원래 코드에서 주석 번호가 ①→③ 으로 건너뛰던 누락 경로
//       (ProcessHandle 기반 실행 명령행 탐색) 추가
//  H. close() 미기록 버퍼 플러시 보강
//     - lineBuf 에 남아있는 미완성 라인을 close() 시 강제 기록
//  신규. 호출자 자동 감지
//     - StackTrace 를 분석해 AppLogger/PrintStream/java.* 를 건너뛰고
//       실제 호출한 앱 클래스·메쏘드를 [Class#method] 형태로 선두에 삽입
// ═══════════════════════════════════════════════════════════
public class AppLogger {

    private static PrintWriter  writer      = null;
    private static String       logFilePath = "";
    private static String       exeFilePath = "";
    private static final Object LOCK        = new Object();
    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    // lineBuf 는 out/err 각각 독립 관리 (내부 Tee 클래스에서 직접 보유)
    // close() 시 플러시를 위해 두 Tee 의 참조를 보관
    private static TeeStream outTee = null;
    private static TeeStream errTee = null;

    // ── 공개 API ─────────────────────────────────────────────────

    /** 로거 초기화 - main() 가장 먼저 호출 */
    public static void init() {

        // ① sun.java.command / ProcessHandle / CodeSource 순으로 실행 파일 경로 탐색
        String exePath = resolveExePath();
        exeFilePath = exePath != null ? exePath : "(unknown)";

        // 실행 폴더 결정
        File exeFile = exePath != null ? new File(exePath) : null;
        File runDir  = (exeFile != null && exeFile.getParentFile() != null)
                       ? exeFile.getParentFile()
                       : new File(System.getProperty("user.dir"));

        // log 하위 폴더 생성
        File logDir = new File(runDir, "log");
        if (!logDir.exists()) logDir.mkdirs();

        // 로그 파일명: <실행파일 기본명>_yyyyMMdd_HHmmss.txt
        String baseName  = (exeFile != null) ? stripExt(exeFile.getName()) : "app";
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName  = baseName + "_" + timestamp + ".txt";
        File   logFile   = new File(logDir, fileName);
        logFilePath = logFile.getAbsolutePath();

        // PrintWriter 열기 (UTF-8, 자동 flush)
        try {
            writer = new PrintWriter(
                         new OutputStreamWriter(
                             new FileOutputStream(logFile, true), "UTF-8"), true);
        } catch (Exception e) {
            System.err.println("[AppLogger] 로그 파일 열기 실패: " + e.getMessage());
            return;
        }

        // ── B. out / err 를 별도 TeeStream 으로 교체 ──────────────
        // ★ "UTF-8" 지정 필수 - Windows 기본 인코딩(CP949)이면 한글이 깨짐
        final PrintStream originalOut = System.out;
        final PrintStream originalErr = System.err;

        try {
            outTee = new TeeStream(originalOut, false);  // isErr = false
            errTee = new TeeStream(originalErr, true);   // isErr = true  → [ERR] 접두어
        } catch (Exception e) {
            System.err.println("[AppLogger] tee 스트림 생성 실패: " + e.getMessage());
            return;
        }

        System.setOut(outTee);
        System.setErr(errTee);

        System.out.println("[AppLogger] 초기화 완료");
        System.out.println("[AppLogger] 실행 파일: " + exeFilePath);
        System.out.println("[AppLogger] 로그 파일: " + logFilePath);
    }

    /** writer 에만 직접 기록 (타임스탬프 + 스레드명 + 호출자 포함) */
    public static void writeToFile(String msg) {
        writeToFile(msg, false);
    }

    /** writer 에만 직접 기록 - isErr=true 이면 [ERR] 접두어 추가 */
    public static void writeToFile(String msg, boolean isErr) {
        if (writer == null) return;
        String ts     = TS.format(new Date());
        String thread = Thread.currentThread().getName();
        String caller = resolveCallerTag();
        String prefix = isErr ? "[ERR] " : "";
        synchronized (LOCK) {
            writer.println("[" + ts + "] [" + thread + "] " + caller + prefix + msg);
        }
    }

    /** 로그 파일 전체 경로 반환 */
    public static String getLogFilePath() { return logFilePath; }

    /** 실행 파일 전체 경로 반환 */
    public static String getExeFilePath() { return exeFilePath; }

    /**
     * 로거 닫기.
     * H. close() 시 out/err lineBuf 에 남아있는 미완성 라인을 강제 플러시한 뒤
     *    writer 를 닫는다.
     */
    public static void close() {
        // 미기록 버퍼 플러시 (개행 없이 프로세스가 종료될 때 마지막 라인 손실 방지)
        if (outTee != null) outTee.flushLineBuf();
        if (errTee != null) errTee.flushLineBuf();
        if (writer != null) { writer.flush(); writer.close(); }
    }

    // ── 내부: TeeStream ──────────────────────────────────────────

    /**
     * out 또는 err 를 감싸는 Tee 스트림.
     * - 콘솔(원본 스트림)에는 그대로 전달
     * - 파일에는 타임스탬프 + 스레드명 + 호출자 + (ERR 태그) 를 앞에 붙여 기록
     * - isErr=true 이면 파일 기록 시 [ERR] 접두어 추가
     */
    private static class TeeStream extends PrintStream {
        private final StringBuilder lineBuf = new StringBuilder();
        private final boolean isErr;

        TeeStream(PrintStream original, boolean isErr) throws UnsupportedEncodingException {
            super(original, true, "UTF-8");
            this.isErr = isErr;
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            super.write(buf, off, len);   // 콘솔 출력
            String s;
            try   { s = new String(buf, off, len, "UTF-8"); }
            catch (Exception e) { s = new String(buf, off, len); }
            synchronized (lineBuf) {
                for (int i = 0; i < s.length(); i++) {
                    char c = s.charAt(i);
                    if (c == '\n') {
                        String line = lineBuf.toString();
                        lineBuf.setLength(0);
                        if (!isSuppressed(line)) writeToFile(line, isErr);
                    } else if (c != '\r') {
                        lineBuf.append(c);
                    }
                }
            }
        }

        /** H. close() 시 미완성 라인 강제 기록 */
        void flushLineBuf() {
            synchronized (lineBuf) {
                if (lineBuf.length() > 0) {
                    String line = lineBuf.toString();
                    lineBuf.setLength(0);
                    if (!isSuppressed(line)) writeToFile(line, isErr);
                }
            }
        }
    }

    // ── 내부: 노이즈 필터 ────────────────────────────────────────

    /**
     * 로그 파일에 기록하지 않을 노이즈 라인 판정.
     * - [Stream] 1920x1080 캡처 완료 → 매초 발생하는 정상 스트림 출력
     * - [Telegram Poll] ok:true result:[] → 5초마다 발생하는 빈 폴링 응답
     */
    private static boolean isSuppressed(String msg) {
        if (msg == null) return false;
        if (msg.contains("[Stream]") && msg.contains("캡처 완료")
                && msg.contains("1920x1080")) return true;
        if (msg.contains("[Telegram Poll]") && msg.contains("\"ok\":true")
                && msg.contains("\"result\":[]")) return true;
        return false;
    }

    // ── 내부: 호출자 태그 ────────────────────────────────────────

    /**
     * C / 신규. StackTrace 를 분석해 실제 호출한 앱 클래스·메쏘드를 반환.
     *
     * 건너뛸 프레임:
     *   - java.*, sun.*, javax.* 패키지
     *   - AppLogger 자신
     *   - TeeStream (AppLogger$TeeStream 형태)
     *
     * 반환 형식: [ClassName#methodName]
     * 탐색 실패 시 빈 문자열 반환.
     */
    private static String resolveCallerTag() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement e : stack) {
            String cls = e.getClassName();
            // JVM 내부 / 로거 자신 / Tee 내부 클래스 제외
            if (cls.startsWith("java.")
             || cls.startsWith("sun.")
             || cls.startsWith("javax.")
             || cls.startsWith("jdk.")
             || cls.equals("AppLogger")
             || cls.startsWith("AppLogger$")) {
                continue;
            }
            // 익명 클래스($1, $2 …)는 외부 클래스명만 사용
            String simpleCls = cls.contains("$")
                ? cls.substring(0, cls.indexOf('$'))
                : cls;
            return "[" + simpleCls + "#" + e.getMethodName() + "] ";
        }
        return "";
    }

    // ── 내부: 실행 파일 경로 탐색 ────────────────────────────────

    /**
     * G. 실행 파일 경로를 3단계로 탐색.
     *
     * ① sun.java.command 시스템 프로퍼티
     *    - java -jar app.jar  또는 launch4j exe 로 실행 시 파일명이 포함됨
     * ② ProcessHandle (Java 9+) 로 현재 프로세스 명령행 파싱
     *    - ① 이 빈 문자열이거나 클래스명만 담긴 경우를 보완
     *    - 원래 코드에서 주석 번호가 ①→③ 으로 건너뛰며 누락되었던 경로
     * ③ CodeSource (getProtectionDomain)
     *    - IDE / class 직접 실행 시 사용
     */
    private static String resolveExePath() {

        // ① sun.java.command - .jar 또는 .exe (java/javaw 제외)
        try {
            String sc = System.getProperty("sun.java.command", "").trim();
            String first = sc.split("\\s+")[0];
            if (first.endsWith(".jar") || first.endsWith(".exe"))
                return new File(first).getAbsolutePath();
        } catch (Exception ignored) {}

        // ② CodeSource (JAR / class 실행) - ProcessHandle 보다 우선
        //    ProcessHandle 은 javaw.exe 를 반환하여 C:\Program Files\ 아래에
        //    로그 폴더를 만들려다 권한 오류가 나므로 건너뜀
        try {
            java.security.CodeSource cs =
                AppLogger.class.getProtectionDomain().getCodeSource();
            if (cs != null) {
                File f = new File(cs.getLocation().toURI()).getAbsoluteFile();
                // java/javaw 자체를 가리키는 경우 제외
                String name = f.getName().toLowerCase();
                if (!name.equals("java.exe") && !name.equals("javaw.exe")
                        && !name.equals("java")  && !name.equals("javaw"))
                    return f.getAbsolutePath();
            }
        } catch (Exception ignored) {}

        // ③ ProcessHandle 기반 명령행 파싱 (Java 9+) - 최후 수단
        try {
            java.util.Optional<String> cmd =
                ProcessHandle.current().info().command();
            if (cmd.isPresent()) {
                File f = new File(cmd.get());
                String name = f.getName().toLowerCase();
                // java/javaw 이면 사용하지 않음
                if (f.exists()
                        && !name.equals("java.exe") && !name.equals("javaw.exe")
                        && !name.equals("java")     && !name.equals("javaw"))
                    return f.getAbsolutePath();
            }
        } catch (Exception ignored) {}

        // ④ 현재 작업 디렉터리 기준 (최후의 최후)
        return new File(System.getProperty("user.dir"), "app").getAbsolutePath();
    }

    // ── 내부: 유틸 ───────────────────────────────────────────────

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }
}
