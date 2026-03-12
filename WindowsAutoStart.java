/**
 * WindowsAutoStart - Windows 부팅 자동 실행 등록/해제
 *
 * ── 동작 방식 ────────────────────────────────────────────────
 *   HKCU\Software\Microsoft\Windows\CurrentVersion\Run 레지스트리 키에
 *   reg.exe 를 통해 앱 실행 명령을 등록/해제한다.
 *
 * ── 실행 파일 판별 ───────────────────────────────────────────
 *   ProcessHandle.current() 로 현재 프로세스를 확인하여
 *   ① jpackage exe → exe 경로 직접 등록
 *   ② jar 직접 실행 → javaw -jar <path> 형태로 등록
 *
 * ── 외부 의존성 ──────────────────────────────────────────────
 *   AppRestarter.getSelfJarPath() - JAR 경로 탐색
 *   AppLogger                     - 로그 기록
 *
 * ── 사용법 ───────────────────────────────────────────────────
 *   boolean registered = WindowsAutoStart.check();
 *   boolean ok         = WindowsAutoStart.set(true);   // 등록
 *   boolean ok         = WindowsAutoStart.set(false);  // 해제
 */
public class WindowsAutoStart {

    private static final String REG_KEY  = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String REG_NAME = "AnalogClockSwing";

    // ── 공개 API ─────────────────────────────────────────────

    /** 현재 자동 실행 등록 여부 확인 */
    public static boolean check() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                "reg", "query", REG_KEY, "/v", REG_NAME });
            return p.waitFor() == 0;
        } catch (Exception e) { return false; }
    }

    /**
     * 자동 실행 등록(enable=true) 또는 해제(enable=false).
     * @return 성공 여부
     */
    public static boolean set(boolean enable) {
        try {
            String reg = System.getenv("SystemRoot") + "\\System32\\reg.exe";

            ProcessBuilder pb;
            if (enable) {
                String cmdValue = buildCmdValue();
                if (cmdValue == null) return false;
                System.out.println("[AutoStart] 등록: " + cmdValue);
                pb = new ProcessBuilder(
                    reg, "add", REG_KEY,
                    "/v", REG_NAME,
                    "/t", "REG_SZ",
                    "/d", cmdValue,
                    "/f");
            } else {
                pb = new ProcessBuilder(
                    reg, "delete", REG_KEY,
                    "/v", REG_NAME,
                    "/f");
            }

            pb.redirectErrorStream(true);
            Process p = pb.start();

            // reg.exe 출력은 Windows 한국어 환경에서 CP949
            new Thread(() -> {
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getInputStream(), "CP949"))) {
                    br.lines().forEach(l -> System.out.println("[AutoStart] " + l));
                } catch (Exception ignored) {}
            }).start();

            int exit = p.waitFor();
            System.out.println("[AutoStart] exit = " + exit);
            return exit == 0;

        } catch (Exception e) {
            System.out.println("[AutoStart] 오류: " + e.getMessage());
            return false;
        }
    }

    // ── 내부: 등록 명령값 생성 ──────────────────────────────

    /**
     * 레지스트리에 등록할 실행 명령 문자열을 생성한다.
     * jpackage exe → exe 경로 / jar → javaw -jar <path>
     */
    private static String buildCmdValue() {
        // 현재 실행 중인 프로세스 경로 확인
        String exePath = ProcessHandle.current().info().command().orElse(null);

        if (exePath != null
                && exePath.toLowerCase().endsWith(".exe")
                && !exePath.toLowerCase().contains("javaw")
                && !exePath.toLowerCase().contains("java")) {
            // ── jpackage exe 로 실행된 경우
            System.out.println("[AutoStart] exe 모드 등록: " + exePath);
            return exePath;
        }

        // ── jar 직접 실행된 경우
        String jarPath = AppRestarter.getSelfJarPath();
        String javaw   = System.getProperty("java.home")
            + java.io.File.separator + "bin"
            + java.io.File.separator + "javaw.exe";

        if (jarPath != null && jarPath.endsWith(".jar")) {
            System.out.println("[AutoStart] jar 모드 등록: " + javaw + " -jar " + jarPath);
            return javaw + " -jar " + jarPath;
        } else if (jarPath != null) {
            return javaw + " -cp " + jarPath + " AnalogClockSwing";
        }
        return null;
    }
}
