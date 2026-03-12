/**
 * ShutdownGuard - 종료 신호 감지 → 이메일 + 텔레그램 알림 전송
 *
 * ── 감지 방법 ────────────────────────────────────────────────────
 *   Shutdown Hook 1개로 모든 케이스를 처리한다.
 *
 *   ✅ Windows 종료/재시작/로그아웃  (JVM 에 SIGTERM 유사 신호 전달)
 *   ✅ kill PID  (SIGTERM)
 *   ✅ Ctrl+C    (SIGINT)
 *   ✅ System.exit()
 *   ❌ kill -9   (SIGKILL) — OS 즉시 강제종료, 어떤 방법으로도 불가
 *
 * ── windowClosing 을 쓰지 않는 이유 ────────────────────────────
 *   이 앱은 setUndecorated(true) + DefaultCloseOperation 미설정 구조라
 *   windowClosing 이벤트가 Windows 종료 시 보장되지 않는다.
 *   Shutdown Hook 은 Windows 종료 시에도 JVM 이 정리 작업을 할 수 있는
 *   유일하게 신뢰할 수 있는 콜백이다.
 *
 * ── Windows 종료 시 시간 제한 대응 ─────────────────────────────
 *   Windows 는 기본 5초 내에 프로세스가 종료되지 않으면 강제 kill 한다.
 *   텔레그램 + 이메일을 별도 스레드로 병렬 전송하고
 *   최대 4초 대기 후 종료한다.
 *
 * ── 사용법 ───────────────────────────────────────────────────────
 *   ShutdownGuard guard = new ShutdownGuard(gmail, tg);
 *   guard.register();   // main 또는 생성자에서 1회 호출
 *
 *   정상 종료(메뉴 → EXIT) 전에 cancel() 호출 → 알림 생략
 */
public class ShutdownGuard {

    private final GmailSender gmail;
    private final TelegramBot tg;

    /** true = cancel() 호출됨 → Hook 실행 시 알림 생략 */
    private volatile boolean cancelled = false;

    /** Shutdown Hook 스레드 (중복 등록 방지) */
    private Thread hookThread = null;

    // ── 생성자 ───────────────────────────────────────────────────

    public ShutdownGuard(GmailSender gmail, TelegramBot tg) {
        this.gmail = gmail;
        this.tg    = tg;
    }

    // ── 공개 API ─────────────────────────────────────────────────

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
    public void cancel() {
        cancelled = true;
        System.out.println("[ShutdownGuard] 알림 취소 (정상 종료)");
    }

    /** cancel() 을 되돌린다. */
    public void resume() {
        cancelled = false;
    }

    // ── 알림 전송 (텔레그램 + 이메일 병렬) ──────────────────────

    private void sendNotifications() {
        String now    = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                            .format(new java.util.Date());
        String pcName = getPcName();
        String userId = System.getProperty("user.name", "(unknown)");

        // 텔레그램 + 이메일 병렬 전송 (Windows 5초 제한 대응)
        Thread tgThread   = new Thread(() -> sendTelegram(now, pcName, userId), "SG-Telegram");
        Thread mailThread = new Thread(() -> sendEmail(now, pcName, userId),    "SG-Email");

        tgThread.start();
        mailThread.start();

        // 최대 4초 대기 (Windows 강제 kill 전에 완료)
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

    // ── 유틸 ─────────────────────────────────────────────────────

    private static String getPcName() {
        try { return java.net.InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "(unknown)"; }
    }
}
