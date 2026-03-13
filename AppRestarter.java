import javax.swing.*;

/**
 * AppRestarter - 앱 생명주기 관리 통합 클래스
 *
 * ── 포함된 구 클래스 ──────────────────────────────────────────
 *   ① AppRestarter    : 앱 재시작 및 AppCDS(JSA) 자동 생성  (기존)
 *   ② WindowsAutoStart: Windows 부팅 자동 실행 등록/해제    (구 WindowsAutoStart.java)
 *   ③ ShutdownGuard   : 종료 신호 감지 → 이메일+텔레그램 알림 (구 ShutdownGuard.java)
 *
 * ── 외부 참조 변경사항 ────────────────────────────────────────
 *   구 WindowsAutoStart.check()  →  AppRestarter.AutoStart.check()
 *   구 WindowsAutoStart.set(b)   →  AppRestarter.AutoStart.set(b)
 *   구 ShutdownGuard             →  AppRestarter.ShutdownGuard
 *
 * ══════════════════════════════════════════════════════════════
 *  AppRestarter (메인 클래스)
 * ══════════════════════════════════════════════════════════════
 *   ① restartApp()          : 설정 저장 후 현재 프로세스 종료 → 자기 자신 재실행
 *   ② buildAppCdsIfNeeded() : jar 환경에서 JSA 아카이브 백그라운드 자동 생성
 *
 * ── 실행 파일 경로 탐색 우선순위 ────────────────────────────
 *   ① sun.java.command 에 .jar / .exe 가 명시된 경우
 *   ② CodeSource 위치가 .jar / .exe 인 경우
 *   ③ CodeSource 폴더(최대 3단계 위)에서 .exe 탐색
 *
 * ── 사용법 ───────────────────────────────────────────────────
 *   AppRestarter restarter = new AppRestarter(gmail, tg, ownerFrame);
 *   restarter.setCachedPaths(exePath, javawPath, jsaPath);
 *   restarter.restartApp(saveConfigRunnable);
 *   restarter.buildAppCdsIfNeeded(saveConfigRunnable);
 *
 *   AppRestarter.AutoStart.check();        // 자동 실행 등록 여부
 *   AppRestarter.AutoStart.set(true/false); // 등록/해제
 *
 *   AppRestarter.ShutdownGuard guard = new AppRestarter.ShutdownGuard(gmail, tg);
 *   guard.register();
 *   guard.cancel();
 */
public class AppRestarter {

    // ── 의존성 ────────────────────────────────────────────────
    private final GmailSender       gmail;
    private final TelegramBot       tg;
    private final java.awt.Window   ownerWindow;

    // ── 경로 캐시 (INI 저장/로드) ────────────────────────────
    private String cachedExePath   = "";
    private String cachedJavawPath = "";
    private String cachedJsaPath   = "";

    // ── 생성자 ───────────────────────────────────────────────
    public AppRestarter(GmailSender gmail, TelegramBot tg, java.awt.Window ownerWindow) {
        this.gmail       = gmail;
        this.tg          = tg;
        this.ownerWindow = ownerWindow;
    }

    // ── 캐시 경로 접근자 ─────────────────────────────────────

    public void setCachedPaths(String exePath, String javawPath, String jsaPath) {
        this.cachedExePath   = exePath   != null ? exePath   : "";
        this.cachedJavawPath = javawPath != null ? javawPath : "";
        this.cachedJsaPath   = jsaPath   != null ? jsaPath   : "";
    }

    public String getCachedExePath()   { return cachedExePath; }
    public String getCachedJavawPath() { return cachedJavawPath; }
    public String getCachedJsaPath()   { return cachedJsaPath; }

    // ── 공개 API ─────────────────────────────────────────────

    /**
     * 재시작 확인 → 알림 전송 → 새 프로세스 실행 → System.exit(0)
     * @param onBeforeRestart 재시작 직전 실행할 콜백 (설정 저장, ShutdownGuard 취소 등)
     */
    public void restartApp(Runnable onBeforeRestart) {
        int confirm = JOptionPane.showConfirmDialog(
            ownerWindow, "앱을 재시작하시겠습니까?", "Restart",
            JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        if (onBeforeRestart != null) onBeforeRestart.run();

        String now    = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
        String pcName = getPcName();
        String userId = System.getProperty("user.name", "(unknown)");

        AppLogger.writeToFile("[Restart] 재시작 요청"
            + " | 시각=" + now + " | PC=" + pcName + " | 사용자=" + userId);

        final String tgMsg = "🔄 앱 재시작\n\n"
            + "🕐 시각  : " + now    + "\n"
            + "💻 PC    : " + pcName + "\n"
            + "👤 사용자: " + userId;
        String mailSubject = "🔄 [앱 재시작] " + pcName;
        String mailBody    = GmailSender.APP_SIGNATURE
            + "팝업 메뉴에서 앱 재시작이 요청되었습니다.\n\n"
            + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            + "시각  : " + now    + "\n"
            + "PC    : " + pcName + "\n"
            + "사용자: " + userId + "\n"
            + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

        Runnable doRestart = buildRestartRunnable(tgMsg);

        if (gmail != null && gmail.isConfigured() && !gmail.lastTo.isEmpty()) {
            gmail.sendShutdownNotice(doRestart, mailSubject, mailBody);
        } else {
            new Thread(doRestart, "RestartProc").start();
        }
    }

    /**
     * AppCDS JSA 아카이브를 백그라운드에서 자동 생성.
     * jar 실행 환경에서만 동작. JSA 가 이미 존재하면 캐시만 갱신 후 스킵.
     * @param saveConfig JSA 경로 갱신 후 INI 저장을 위한 콜백
     */
    public void buildAppCdsIfNeeded(Runnable saveConfig) {
        String jarPath = cachedExePath;
        if (jarPath.isEmpty()) {
            try {
                java.security.CodeSource cs = getClass().getProtectionDomain().getCodeSource();
                if (cs != null) {
                    String p = cs.getLocation().toURI().getPath();
                    if (p != null && p.endsWith(".jar"))
                        jarPath = new java.io.File(p).getAbsolutePath();
                }
            } catch (Exception ignored) {}
        }
        if (jarPath.isEmpty() || !jarPath.endsWith(".jar")) return;

        java.io.File jarFile = new java.io.File(jarPath);
        String jsaPath = new java.io.File(jarFile.getParentFile(),
            jarFile.getName().replace(".jar", ".jsa")).getAbsolutePath();

        if (new java.io.File(jsaPath).exists()) {
            if (!jsaPath.equals(cachedJsaPath)) {
                cachedJsaPath = jsaPath;
                if (saveConfig != null) saveConfig.run();
                AppLogger.writeToFile("[AppCDS] 기존 JSA 사용: " + jsaPath);
            }
            return;
        }

        String javaw = cachedJavawPath;
        if (javaw.isEmpty()) {
            javaw = System.getProperty("java.home") + java.io.File.separator
                + "bin" + java.io.File.separator + "javaw";
        }
        if (javaw.toLowerCase().contains("runtime" + java.io.File.separator + "bin")) {
            String sysJavaw = findSystemJavaw();
            if (sysJavaw != null) {
                AppLogger.writeToFile("[AppCDS] jpackage javaw 감지 → 시스템 javaw 사용: " + sysJavaw);
                javaw = sysJavaw;
            } else {
                AppLogger.writeToFile("[AppCDS] 시스템 javaw 탐색 실패 - JSA 생성 스킵");
                return;
            }
        }

        final String fJavaw = javaw;
        final String fJar   = jarPath;
        final String fJsa   = jsaPath;

        new Thread(() -> {
            try {
                AppLogger.writeToFile("[AppCDS] JSA 생성 시작: " + fJsa);
                ProcessBuilder pb = new ProcessBuilder(
                    fJavaw, "-Xshare:dump",
                    "-XX:SharedArchiveFile=" + fJsa,
                    "-jar", fJar);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
                p.destroyForcibly();

                if (new java.io.File(fJsa).exists()) {
                    cachedJsaPath = fJsa;
                    if (saveConfig != null) saveConfig.run();
                    AppLogger.writeToFile("[AppCDS] JSA 생성 완료: " + fJsa);
                } else {
                    AppLogger.writeToFile("[AppCDS] JSA 생성 실패 (파일 없음)");
                }
            } catch (Exception e) {
                AppLogger.writeToFile("[AppCDS] JSA 생성 오류: " + e.getMessage());
            }
        }, "AppCDS-Builder").start();
    }

    // ── 내부: 재시작 Runnable ────────────────────────────────

    private Runnable buildRestartRunnable(String tgMsg) {
        return () -> {
            if (tg != null && tg.polling && !tg.botToken.isEmpty() && !tg.myChatId.isEmpty()) {
                try {
                    Thread tgThread = new Thread(() -> tg.send(tg.myChatId, tgMsg), "RestartTG");
                    tgThread.start();
                    tgThread.join(10000);
                    AppLogger.writeToFile("[Restart] 텔레그램 전송 완료");
                } catch (Exception e) {
                    AppLogger.writeToFile("[Restart] 텔레그램 전송 실패: " + e.getMessage());
                }
            }
            try {
                String exePath = cachedExePath;
                if (exePath.isEmpty() || !new java.io.File(exePath).exists()) {
                    AppLogger.writeToFile("[Restart] 경로 캐시 없음 - 탐색 시작");
                    exePath = resolveExePathForRestart();
                } else {
                    AppLogger.writeToFile("[Restart] 캐시된 경로 사용: " + exePath);
                }
                if (exePath == null) {
                    AppLogger.writeToFile("[Restart] 실행 파일 경로 탐색 실패");
                    javax.swing.SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(null,
                            "실행 파일 경로를 찾을 수 없어 재시작할 수 없습니다.",
                            "Restart 실패", JOptionPane.ERROR_MESSAGE));
                    return;
                }

                if (!exePath.endsWith(".exe")) {
                    java.io.File jarDir = new java.io.File(exePath).getParentFile();
                    outer:
                    for (int up = 0; up < 3; up++) {
                        if (jarDir == null) break;
                        java.io.File[] exeFiles = jarDir.listFiles(
                            c -> c.getName().toLowerCase().endsWith(".exe") && c.isFile());
                        if (exeFiles != null) {
                            for (java.io.File ef : exeFiles) {
                                String n = ef.getName().toLowerCase();
                                if (n.contains("analog") || n.contains("clock")) {
                                    exePath = ef.getAbsolutePath();
                                    AppLogger.writeToFile("[Restart] jar→exe 전환: " + exePath);
                                    break outer;
                                }
                            }
                            if (exeFiles.length > 0) {
                                exePath = exeFiles[0].getAbsolutePath();
                                AppLogger.writeToFile("[Restart] jar→exe 전환(첫번째): " + exePath);
                                break;
                            }
                        }
                        jarDir = jarDir.getParentFile();
                    }
                }

                cachedExePath = exePath;

                ProcessBuilder pb;
                if (exePath.endsWith(".exe")) {
                    pb = new ProcessBuilder(exePath);
                } else {
                    String javaw = cachedJavawPath;
                    boolean javawExists = !javaw.isEmpty()
                        && (new java.io.File(javaw).exists()
                            || new java.io.File(javaw + ".exe").exists());
                    if (!javawExists) {
                        javaw = System.getProperty("java.home") + java.io.File.separator
                            + "bin" + java.io.File.separator + "javaw";
                        cachedJavawPath = javaw;
                        AppLogger.writeToFile("[Restart] javaw 경로 탐색: " + javaw);
                    } else {
                        AppLogger.writeToFile("[Restart] 캐시된 javaw 사용: " + javaw);
                    }
                    pb = new ProcessBuilder(javaw, "-jar", exePath);
                    if (!cachedJsaPath.isEmpty() && new java.io.File(cachedJsaPath).exists()) {
                        pb = new ProcessBuilder(javaw,
                            "-XX:SharedArchiveFile=" + cachedJsaPath,
                            "-jar", exePath);
                        AppLogger.writeToFile("[Restart] AppCDS JSA 적용: " + cachedJsaPath);
                    }
                }

                AppLogger.writeToFile("[Restart] INI 캐시 exePath=" + cachedExePath
                    + (cachedJavawPath.isEmpty() ? "" : " javawPath=" + cachedJavawPath));

                pb.directory(new java.io.File(exePath).getParentFile());
                pb.start();
                AppLogger.writeToFile("[Restart] 새 프로세스 시작 완료: " + exePath);
                AppLogger.close();
                System.exit(0);

            } catch (Exception ex) {
                AppLogger.writeToFile("[Restart] 재시작 실패: " + ex.getMessage());
                javax.swing.SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(null,
                        "재시작 실패: " + ex.getMessage(),
                        "Restart 실패", JOptionPane.ERROR_MESSAGE));
            }
        };
    }

    // ── 내부: 경로 탐색 ─────────────────────────────────────

    static String resolveExePathForRestart() {
        try {
            String sc = System.getProperty("sun.java.command", "").trim();
            String first = sc.split("\\s+")[0];
            if (first.endsWith(".jar") || first.endsWith(".exe")) {
                java.io.File f = new java.io.File(first).getAbsoluteFile();
                if (f.exists()) return f.getAbsolutePath();
            }
        } catch (Exception ignored) {}

        java.io.File csDir = null;
        try {
            java.io.File f = new java.io.File(
                AppRestarter.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).getAbsoluteFile();
            if ((f.getName().endsWith(".jar") || f.getName().endsWith(".exe")) && f.exists()) {
                return f.getAbsolutePath();
            }
            csDir = f.isDirectory() ? f : f.getParentFile();
        } catch (Exception ignored) {}

        java.io.File dir = csDir;
        for (int up = 0; up < 4; up++) {
            if (dir == null) break;
            java.io.File[] exeFiles = dir.listFiles(
                child -> child.getName().toLowerCase().endsWith(".exe") && child.isFile());
            if (exeFiles != null && exeFiles.length >= 1) {
                for (java.io.File ef : exeFiles) {
                    String n = ef.getName().toLowerCase();
                    if (n.contains("analog") || n.contains("clock")) {
                        AppLogger.writeToFile("[Restart] exe 탐색(이름매칭): " + ef.getAbsolutePath());
                        return ef.getAbsolutePath();
                    }
                }
                AppLogger.writeToFile("[Restart] exe 탐색(첫번째): " + exeFiles[0].getAbsolutePath());
                return exeFiles[0].getAbsolutePath();
            }
            dir = dir.getParentFile();
        }
        return null;
    }

    static String getSelfJarPath() {
        try {
            String sc = System.getProperty("sun.java.command", "").trim();
            if (sc.endsWith(".jar"))
                return new java.io.File(sc).getAbsolutePath();
        } catch (Exception ignored) {}
        try {
            return new java.io.File(AppRestarter.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).getAbsolutePath();
        } catch (Exception ignored) {}
        return null;
    }

    static String findSystemJavaw() {
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isEmpty()) {
            java.io.File f = new java.io.File(javaHome, "bin" + java.io.File.separator + "javaw.exe");
            if (f.exists()) return f.getAbsolutePath();
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                java.io.File f = new java.io.File(dir.trim(), "javaw.exe");
                if (f.exists() && !f.getAbsolutePath().toLowerCase().contains("runtime")) {
                    return f.getAbsolutePath();
                }
            }
        }
        String[] candidates = {
            "C:\\Program Files\\Java", "C:\\Program Files\\Eclipse Adoptium",
            "C:\\Program Files\\Microsoft", "C:\\Program Files\\Liberica"
        };
        for (String base : candidates) {
            java.io.File baseDir = new java.io.File(base);
            if (!baseDir.exists()) continue;
            java.io.File[] jdks = baseDir.listFiles(
                f -> f.isDirectory() && f.getName().toLowerCase().startsWith("jdk"));
            if (jdks == null) continue;
            java.util.Arrays.sort(jdks, java.util.Comparator.comparing(java.io.File::getName).reversed());
            for (java.io.File jdk : jdks) {
                java.io.File f = new java.io.File(jdk, "bin" + java.io.File.separator + "javaw.exe");
                if (f.exists()) return f.getAbsolutePath();
            }
        }
        return null;
    }

    private static String getPcName() {
        try { return java.net.InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "(unknown)"; }
    }

    // ═══════════════════════════════════════════════════════════
    //  AutoStart — Windows 부팅 자동 실행 등록/해제 (구 WindowsAutoStart.java)
    //
    //  HKCU\Software\Microsoft\Windows\CurrentVersion\Run 레지스트리 키에
    //  reg.exe 를 통해 앱 실행 명령을 등록/해제한다.
    //
    //  사용법:
    //    boolean on = AppRestarter.AutoStart.check();
    //    boolean ok = AppRestarter.AutoStart.set(true);   // 등록
    //    boolean ok = AppRestarter.AutoStart.set(false);  // 해제
    // ═══════════════════════════════════════════════════════════

    public static class AutoStart {

        private static final String REG_KEY  = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
        private static final String REG_NAME = "AnalogClockSwing";

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

        /**
         * 레지스트리에 등록할 실행 명령 문자열 생성.
         * jpackage exe → exe 경로 / jar → javaw -jar <path>
         */
        private static String buildCmdValue() {
            String exePath = ProcessHandle.current().info().command().orElse(null);

            if (exePath != null
                    && exePath.toLowerCase().endsWith(".exe")
                    && !exePath.toLowerCase().contains("javaw")
                    && !exePath.toLowerCase().contains("java")) {
                System.out.println("[AutoStart] exe 모드 등록: " + exePath);
                return exePath;
            }

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

    // ═══════════════════════════════════════════════════════════
    //  ShutdownGuard — 종료 신호 감지 → 이메일+텔레그램 알림 (구 ShutdownGuard.java)
    //
    //  Shutdown Hook 1개로 모든 케이스를 처리한다.
    //   ✅ Windows 종료/재시작/로그아웃 (JVM 에 SIGTERM 유사 신호 전달)
    //   ✅ kill PID (SIGTERM), Ctrl+C (SIGINT), System.exit()
    //   ❌ kill -9 (SIGKILL) — OS 즉시 강제종료, 어떤 방법으로도 불가
    //
    //  사용법:
    //    AppRestarter.ShutdownGuard guard = new AppRestarter.ShutdownGuard(gmail, tg);
    //    guard.register();   // main 또는 생성자에서 1회 호출
    //    guard.cancel();     // 정상 종료(메뉴 → EXIT) 전에 호출 → 알림 생략
    //    guard.resume();     // cancel() 을 되돌림
    // ═══════════════════════════════════════════════════════════

    public static class ShutdownGuard {

        private final GmailSender gmail;
        private final TelegramBot tg;

        /** true = cancel() 호출됨 → Hook 실행 시 알림 생략 */
        private volatile boolean cancelled = false;

        /** Shutdown Hook 스레드 (중복 등록 방지) */
        private Thread hookThread = null;

        public ShutdownGuard(GmailSender gmail, TelegramBot tg) {
            this.gmail = gmail;
            this.tg    = tg;
        }

        /** Shutdown Hook 등록. 설정 로드 완료 후 1회 호출. */
        public synchronized void register() {
            if (hookThread != null) return;

            hookThread = new Thread(() -> {
                if (cancelled) {
                    AppLogger.writeToFile("[ShutdownGuard] 정상 종료 — 알림 생략");
                    return;
                }
                AppLogger.writeToFile("[ShutdownGuard] 종료 신호 감지 — 알림 전송 시작");
                sendNotifications();
                AppLogger.writeToFile("[ShutdownGuard] 완료");
                AppLogger.close();
            }, "ShutdownGuard-Hook");

            Runtime.getRuntime().addShutdownHook(hookThread);
            System.out.println("[ShutdownGuard] 등록 완료");
        }

        /** 정상 종료 시 호출 — 알림을 보내지 않는다. */
        public void cancel()  { cancelled = true;  System.out.println("[ShutdownGuard] 알림 취소 (정상 종료)"); }

        /** cancel() 을 되돌린다. */
        public void resume()  { cancelled = false; }

        private void sendNotifications() {
            String now    = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
            String pcName = getPcName();
            String userId = System.getProperty("user.name", "(unknown)");

            Thread tgThread   = new Thread(() -> sendTelegram(now, pcName, userId), "SG-Telegram");
            Thread mailThread = new Thread(() -> sendEmail(now, pcName, userId),    "SG-Email");

            tgThread.start();
            mailThread.start();

            try { tgThread.join(4000); }   catch (InterruptedException ignored) {}
            try { mailThread.join(4000); } catch (InterruptedException ignored) {}
        }

        private void sendTelegram(String now, String pcName, String userId) {
            if (tg == null || !tg.polling || tg.botToken.isEmpty() || tg.myChatId.isEmpty()) return;
            try {
                String msg = "⚠️ 강제 종료 감지!\n\n"
                    + "🕐 시각  : " + now    + "\n"
                    + "💻 PC    : " + pcName + "\n"
                    + "👤 사용자: " + userId + "\n\n"
                    + "📋 사유  : Windows 종료/재시작 또는 kill 신호";
                tg.send(tg.myChatId, msg);
                AppLogger.writeToFile("[ShutdownGuard] 텔레그램 전송 완료");
            } catch (Exception e) {
                AppLogger.writeToFile("[ShutdownGuard] 텔레그램 전송 실패: " + e.getMessage());
            }
        }

        private void sendEmail(String now, String pcName, String userId) {
            if (gmail == null || !gmail.isConfigured() || gmail.lastTo.isEmpty()) return;
            try {
                String subject = "⚠️ [강제 종료 감지] " + pcName;
                String body    = GmailSender.APP_SIGNATURE
                    + "Windows 종료/재시작 또는 외부 신호로 프로세스가 종료되었습니다.\n\n"
                    + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                    + "감지 시각: " + now    + "\n"
                    + "PC 이름 : " + pcName + "\n"
                    + "사용자  : " + userId + "\n"
                    + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                    + "(kill -9 / SIGKILL 은 감지 불가)";
                gmail.send(gmail.lastTo, subject, body);
                AppLogger.writeToFile("[ShutdownGuard] 이메일 전송 완료");
            } catch (Exception e) {
                AppLogger.writeToFile("[ShutdownGuard] 이메일 전송 실패: " + e.getMessage());
            }
        }

        private static String getPcName() {
            try { return java.net.InetAddress.getLocalHost().getHostName(); }
            catch (Exception e) { return "(unknown)"; }
        }
    }
}
