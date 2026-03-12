import javax.swing.*;

/**
 * AppRestarter - 앱 재시작 및 AppCDS(JSA) 자동 생성
 *
 * ── 책임 ────────────────────────────────────────────────────
 *   ① restartApp()          : 설정 저장 후 현재 프로세스 종료 → 자기 자신 재실행
 *   ② buildAppCdsIfNeeded() : jar 환경에서 JSA 아카이브 백그라운드 자동 생성
 *
 * ── 실행 파일 경로 탐색 우선순위 ────────────────────────────
 *   ① sun.java.command 에 .jar / .exe 가 명시된 경우
 *   ② CodeSource 위치가 .jar / .exe 인 경우
 *   ③ CodeSource 폴더(최대 3단계 위)에서 .exe 탐색
 *      (launch4j / jpackage 번들 exe 환경 대응)
 *
 * ── javaw 탐색 우선순위 (jar 실행 시) ───────────────────────
 *   ① cachedJavawPath (INI 캐시)
 *   ② java.home/bin/javaw
 *   ③ JAVA_HOME 환경변수
 *   ④ PATH
 *   ⑤ 일반적인 JDK 설치 경로 (Program Files 하위)
 *
 * ── Windows 종료 시 주의 ────────────────────────────────────
 *   재시작 전 ShutdownGuard.cancel() 을 반드시 호출해야
 *   강제종료 알림이 오발송되지 않는다.
 *
 * ── 사용법 ───────────────────────────────────────────────────
 *   AppRestarter restarter = new AppRestarter(gmail, tg, ownerFrame);
 *   restarter.setCachedPaths(exePath, javawPath, jsaPath);  // INI 로드 후
 *   restarter.restartApp(saveConfigRunnable, cancelGuardRunnable);
 *   restarter.buildAppCdsIfNeeded(saveConfigRunnable);
 *
 *   // INI 저장 시 캐시 경로 읽기
 *   restarter.getCachedExePath()
 *   restarter.getCachedJavawPath()
 *   restarter.getCachedJsaPath()
 */
public class AppRestarter {

    // ── 의존성 ────────────────────────────────────────────────
    private final GmailSender gmail;
    private final TelegramBot tg;
    private final java.awt.Window ownerWindow;

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
     *
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

        // ── 텔레그램 + Gmail 메시지 준비 ────────────────────
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
     * AppCDS JSA 아카이브를 백그라운드에서 자동 생성합니다.
     * - jar 실행 환경에서만 동작
     * - JSA 가 이미 존재하면 캐시만 갱신하고 스킵
     * - 생성 완료 후 saveConfig 콜백으로 INI 에 app.jsaPath 저장
     *
     * @param saveConfig JSA 경로 갱신 후 INI 저장을 위한 콜백
     */
    public void buildAppCdsIfNeeded(Runnable saveConfig) {
        // exe 실행이면 스킵
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

        // 이미 존재하면 캐시만 갱신
        if (new java.io.File(jsaPath).exists()) {
            if (!jsaPath.equals(cachedJsaPath)) {
                cachedJsaPath = jsaPath;
                if (saveConfig != null) saveConfig.run();
                AppLogger.writeToFile("[AppCDS] 기존 JSA 사용: " + jsaPath);
            }
            return;
        }

        // javaw 경로 결정 - jpackage 번들 runtime\bin\javaw 는 -Xshare:dump 미지원
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
            // ① 텔레그램 전송 (동기, 최대 10초 대기)
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
                // ② 실행 파일 경로: INI 캐시 우선, 없거나 파일 없으면 탐색 후 캐시 저장
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

                // ③ .jar 이면 상위 폴더에서 .exe 탐색
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

                // ④ 찾은 경로를 캐시에 저장
                cachedExePath = exePath;

                ProcessBuilder pb;
                if (exePath.endsWith(".exe")) {
                    pb = new ProcessBuilder(exePath);
                } else {
                    // javaw 경로: 캐시 우선, 없으면 java.home 으로 조합
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
                    // AppCDS JSA 있으면 적용
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

    /**
     * 재시작에 사용할 실행 파일 경로 탐색.
     * 우선순위: sun.java.command → CodeSource → CodeSource 폴더 상위 탐색
     */
    static String resolveExePathForRestart() {
        // ① sun.java.command
        try {
            String sc = System.getProperty("sun.java.command", "").trim();
            String first = sc.split("\\s+")[0];
            if (first.endsWith(".jar") || first.endsWith(".exe")) {
                java.io.File f = new java.io.File(first).getAbsoluteFile();
                if (f.exists()) return f.getAbsolutePath();
            }
        } catch (Exception ignored) {}

        // ② CodeSource 자체가 .jar / .exe
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

        // ③ CodeSource 폴더에서 .exe 탐색 (최대 4단계 위로)
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

    /**
     * 현재 실행 중인 JAR 파일의 전체 경로를 탐색.
     * sun.java.command → CodeSource 순서.
     */
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

    /**
     * 시스템에 설치된 javaw.exe 경로를 탐색합니다.
     * 탐색 순서: JAVA_HOME → PATH → 일반 JDK 설치 경로
     */
    static String findSystemJavaw() {
        // ① JAVA_HOME 환경변수
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isEmpty()) {
            java.io.File f = new java.io.File(javaHome, "bin" + java.io.File.separator + "javaw.exe");
            if (f.exists()) return f.getAbsolutePath();
        }
        // ② PATH 에서 javaw.exe 탐색
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                java.io.File f = new java.io.File(dir.trim(), "javaw.exe");
                if (f.exists() && !f.getAbsolutePath().toLowerCase().contains("runtime")) {
                    return f.getAbsolutePath();
                }
            }
        }
        // ③ 일반적인 JDK 설치 경로 탐색
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

    // ── 유틸 ────────────────────────────────────────────────

    private static String getPcName() {
        try { return java.net.InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "(unknown)"; }
    }
}
