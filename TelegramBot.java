
import javax.swing.*;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
	* TelegramBot - 텔레그램 Bot API 연동 클래스
	*
	* 역할:
	*   - getUpdates 폴링으로 명령어 수신
	*   - 명령어 처리 (캡처/종료/재시작/cmd 등)
	*   - 메시지 및 파일 전송
	*
	* AnalogClockSwing 과의 결합은 CommandHandler 콜백 인터페이스로만 연결.
	* 이 클래스는 Swing/AWT 에 직접 의존하지 않는다.
*/
public class TelegramBot {
    // ── 콜백 인터페이스 ──────────────────────────────────────────
    /**
		* 명령어 처리 중 AnalogClockSwing 의 기능이 필요할 때 호출되는 콜백.
		* AnalogClockSwing 이 구현하여 TelegramBot 생성 시 주입한다.
	*/
    public interface CommandHandler {
        /** 시계 패널 캡처 → PNG 파일 반환 */
        File captureClockScreen() throws Exception;
        /** 전체 화면 캡처 → PNG 파일 반환 */
        File captureFullScreen() throws Exception;
        /** 특정 모니터 캡처 → PNG 파일 반환 (0-based index) */
        File captureMonitor(int index) throws Exception;
        /** PC 종료 (설정 저장 → 종료 메일 → OS 셧다운) */
        void shutdownPC();
        /** PC 재시작 (설정 저장 → 종료 메일 → OS 재시작) */
        void rebootPC();
        /** 시계 창 표시/숨김 토글 → 결과 상태 반환 (true=표시됨, false=숨겨짐) */
        boolean toggleTrayWindow();
        /** 다운로드된 이미지 파일을 PC 화면 서브 윈도우에 표시 */
        void showImage(java.io.File imageFile);
        /** 다운로드된 미디어 파일을 wmplayer로 재생 */
        void playMedia(java.io.File mediaFile);
        /** 설정 저장 */
        void saveConfig();
        /** 다이얼로그 위치/폰트 등 준비 */
        void prepareDialog(java.awt.Window dlg);
        /** 알람 목록에서 텔레그램 Chat ID 첫 번째 값 반환 (없으면 "") */
        String getFirstAlarmTelegramChatId();
	}
    // ── 설정 필드 (외부에서 직접 읽기/쓰기) ──────────────────────
    public String  botToken  = "";  // BotFather 에서 발급받은 Bot Token
    public String  myChatId  = "";  // 허용된 Chat ID (보안) - 비어있으면 전체 허용
    public volatile boolean polling = false; // 폴링 활성화 여부
    // ── 내부 상태 ─────────────────────────────────────────────────
    private volatile long   lastUpdateId = 0;  // 마지막 처리한 update_id
    private Timer           pollTimer    = null; // EDT 전용 타이머
    // 멀티스레드 안전: 폴링 중복 실행 방지
    private final AtomicBoolean pollRunning = new AtomicBoolean(false);
    // 멀티스레드 안전: /reboot, /down 확인 대기 명령
    private final AtomicReference<String> pendingCmd = new AtomicReference<>("");
    // 콜백 핸들러 (AnalogClockSwing 이 구현)
    private final CommandHandler handler;
    // ── 생성자 ────────────────────────────────────────────────────
    public TelegramBot(CommandHandler handler) {
        this.handler = handler;
	}
    // ── 폴링 시작 / 중지 ─────────────────────────────────────────
    /** 폴링 시작. EDT(Swing Timer) 에서 호출해야 한다. */
    public void startPolling() {
        if (pollTimer != null) pollTimer.stop();
        if (!polling || botToken.isEmpty()) return;
		skipOldUpdates(); // ← 추가: 기존 메시지 무시
		pollTimer = new Timer(5000, e -> {
            // 이전 폴링 스레드가 아직 실행 중이면 생략 (중복 방지)
            if (pollRunning.compareAndSet(false, true)) {
                new Thread(() -> {
                    try     { poll(); }
                    finally { pollRunning.set(false); }
				}, "TelegramPoll").start();
			}
		});
        pollTimer.start();
        System.out.println("[Telegram] 폴링 시작 (5초 간격)");
	}
    /** 폴링 중지. EDT 에서 호출해야 한다. */
    public void stopPolling() {
        if (pollTimer != null) {
            pollTimer.stop();
            pollTimer = null;
		}
        System.out.println("[Telegram] 폴링 중지");
	}
	/** 폴링 시작 전 기존 메시지를 모두 건너뜀 - 재시작 후 이전 명령 재처리 방지 */
	private void skipOldUpdates() {
		try {
			String apiUrl = "https://api.telegram.org/bot" + botToken
            + "/getUpdates?timeout=0&offset=-1";
			HttpURLConnection con = (HttpURLConnection) toUrl(apiUrl).openConnection();
			con.setRequestMethod("GET");
			con.setConnectTimeout(8000);
			con.setReadTimeout(8000);
			if (con.getResponseCode() == 200) {
				java.io.BufferedReader br = new java.io.BufferedReader(
				new java.io.InputStreamReader(con.getInputStream(), "UTF-8"));
				StringBuilder sb = new StringBuilder();
				String line;
				while ((line = br.readLine()) != null) sb.append(line);
				con.disconnect();
				// 가장 마지막 update_id 파싱
				java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"update_id\":(\\d+)")
                .matcher(sb.toString());
				while (m.find())
                lastUpdateId = Long.parseLong(m.group(1));
				System.out.println("[Telegram] 기존 메시지 무시 완료 (lastUpdateId=" + lastUpdateId + ")");
			}
			} catch (Exception e) {
			System.out.println("[Telegram] skipOldUpdates 실패: " + e.getMessage());
		}
	}
    // ── 시작 알림 ─────────────────────────────────────────────────
    /** 앱 시작 알림 전송 (비동기) */
    public void sendStartupNotice() {
        if (!polling || botToken.isEmpty() || myChatId.isEmpty()) return;
        new Thread(() -> {
            try {
                String now     = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
                String pcName  = java.net.InetAddress.getLocalHost().getHostName();
                String userId  = System.getProperty("user.name");
                String osName  = System.getProperty("os.name") + " " + System.getProperty("os.version");
                String javaVer = System.getProperty("java.version");
                String localIp = java.net.InetAddress.getLocalHost().getHostAddress();
                String publicIp = getPublicIp();
                String msg = "🟢 PC가 시작되었습니다.\n\n"
				+ "🕐 시작 시각: " + now + "\n"
				+ "💻 PC 이름 : " + pcName  + "\n"
				+ "👤 사용자  : " + userId  + "\n"
				+ "🌐 IP (내부): " + localIp  + "\n"
				+ "🌍 IP (외부): " + publicIp + "\n"
				+ "🖥 OS      : " + osName  + "\n"
				+ "☕ Java    : " + javaVer;
                send(myChatId, msg);
                System.out.println("[Telegram] 시작 알림 전송 완료");
				} catch (Exception e) {
                System.out.println("[Telegram] 시작 알림 전송 실패: " + e.getMessage());
			}
		}, "TelegramStartup").start();
	}
    /** 종료/재시작 알림 텔레그램 전송 (비동기) */
    public void sendShutdownNotice() { sendShutdownNotice(false); }
    public void sendShutdownNotice(boolean reboot) {
        if (!polling || botToken.isEmpty() || myChatId.isEmpty()) return;
        new Thread(() -> {
            try {
                String now    = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
                String pcName = java.net.InetAddress.getLocalHost().getHostName();
                String userId = System.getProperty("user.name");
                String msg = (reboot ? "🔄 PC가 재시작됩니다." : "🔴 PC가 종료됩니다.") + "\n\n"
                + "🕐 " + (reboot ? "재시작" : "종료") + " 시각: " + now + "\n"
                + "💻 PC 이름 : " + pcName + "\n"
                + "👤 사용자  : " + userId;
                send(myChatId, msg);
                System.out.println("[Telegram] " + (reboot ? "재시작" : "종료") + " 알림 전송 완료");
				} catch (Exception e) {
                System.out.println("[Telegram] 종료 알림 전송 실패: " + e.getMessage());
			}
		}, "TelegramShutdown").start();
	}
    /** 외부 공인 IP 조회 (api.ipify.org 사용) */
    private String getPublicIp() {
        try {
            // java.net.URL url = new java.net.URL("https://api.ipify.org");
            java.net.URL url = toUrl("https://api.ipify.org");
			java.net.HttpURLConnection con = (java.net.HttpURLConnection) url.openConnection();
            con.setConnectTimeout(5000);
            con.setReadTimeout(5000);
            java.io.BufferedReader br = new java.io.BufferedReader(
			new java.io.InputStreamReader(con.getInputStream(), "UTF-8"));
            String ip = br.readLine();
            br.close();
            con.disconnect();
            return (ip != null && !ip.isEmpty()) ? ip.trim() : "(조회 실패)";
			} catch (Exception e) {
            return "(조회 실패)";
		}
	}
    // ── getUpdates 폴링 ──────────────────────────────────────────
    private void poll() {
        if (botToken.isEmpty()) return;
        try {
            String apiUrl = "https://api.telegram.org/bot" + botToken
			+ "/getUpdates?timeout=1&offset=" + (lastUpdateId + 1);
            HttpURLConnection con = (HttpURLConnection) toUrl(apiUrl).openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            int responseCode = con.getResponseCode();
            if (responseCode != 200) {
                System.out.println("[Telegram Poll] HTTP " + responseCode);
                con.disconnect();
                return;
			}
            java.io.BufferedReader br = new java.io.BufferedReader(
			new java.io.InputStreamReader(con.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            con.disconnect();
            String json = sb.toString();
            System.out.println("[Telegram Poll] 응답: " + json.substring(0, Math.min(2000, json.length())));
			
            // update_id / chat_id / text 파싱 (라이브러리 없이 정규식)
            Pattern uidPat = Pattern.compile("\"update_id\":(\\d+)");
            Pattern cidPat = Pattern.compile("\"chat\":\\{\"id\":(-?\\d+)");
            Pattern txtPat = Pattern.compile("\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"");
			
            Matcher uidMat = uidPat.matcher(json);
            while (uidMat.find()) {
                long updateId = Long.parseLong(uidMat.group(1));
                if (updateId <= lastUpdateId) continue;
                lastUpdateId = updateId;
				
                // 이 update 블록 범위 계산
                int blockStart = uidMat.start();
                int blockEnd   = json.length();
                Matcher nextUid = uidPat.matcher(json.substring(blockStart + 1));
                if (nextUid.find()) blockEnd = blockStart + 1 + nextUid.start();
                String block = json.substring(blockStart, blockEnd);
				
                // chat_id 추출
                Matcher cidMat = cidPat.matcher(block);
                if (!cidMat.find()) continue;
                String fromChatId = cidMat.group(1);
				
                // text 추출 — 없으면 첨부파일 수신 시도
                Matcher txtMat = txtPat.matcher(block);
                if (!txtMat.find()) {
                    // Chat ID 보안 체크 (첨부파일도 동일하게 적용)
                    if (!myChatId.isEmpty() && !fromChatId.equals(myChatId)) {
                        System.out.println("[Telegram] 허용되지 않은 Chat ID: " + fromChatId);
                        send(fromChatId, "❌ 허용되지 않은 접근입니다.");
                        continue;
					}
                    receiveFile(fromChatId, block);
                    continue;
				}
                String text = txtMat.group(1)
				.replace("\\n",  "\n")
				.replace("\\r",  "")
				.replace("\\t",  "\t")
				.replace("\\\"", "\"")
				.replace("\\\\", "\\");
                System.out.println("[Telegram Poll] chatId=" + fromChatId + " text=" + text);
                // Chat ID 보안 체크
                if (!myChatId.isEmpty() && !fromChatId.equals(myChatId)) {
                    System.out.println("[Telegram] 허용되지 않은 Chat ID: " + fromChatId);
                    send(fromChatId, "❌ 허용되지 않은 접근입니다.");
                    continue;
				}
                processCommand(fromChatId, text.trim());
			}
			} catch (Exception e) {
            System.out.println("[Telegram Poll] 오류: " + e.getMessage());
		}
	}
	
    // ── 명령어 처리 ───────────────────────────────────────────────
    private void processCommand(String chatId, String text) {
        System.out.println("[Telegram CMD] " + chatId + " → " + text);
        String cmd = text.toLowerCase().split(" ")[0];
		
        switch (cmd) {
            case "/cmd"   : processCMD  ( chatId,  text);	break;
            case "/save"  : processSave ( chatId,  text);	break;
            case "/wh"    : processStart( chatId,  text);	break;
            case "/start" : processStart( chatId,  text);	break;
            case "/yes"   : processYes  ( chatId,  text);	break; 
            case "/tray"  : processTray ( chatId,  text);	break; 
			
			case "/c1":	processCapture  ( chatId,  0);	break; 
			case "/c2":	processCapture  ( chatId,  1);	break; 
			case "/c3":	processCapture  ( chatId,  2);	break; 
			case "/c4":	processCapture  ( chatId,  3);	break; 
			
			case "/h":
            case "/help":
			send(chatId,
				"📋 사용 가능한 명령어\n\n" +
				"/h, /help       - 명령어 목록\n" +
				"/wh             - PC 정보 조회\n" +
				"/s, /screenshot - 전체 화면 캡처 전송\n" +
				"/c, /capture    - 시계 화면 캡처 전송\n" +
				"/c1             - 모니터 1 캡처 전송\n" +
				"/c2             - 모니터 2 캡처 전송\n" +
				"/c3             - 모니터 3 캡처 전송\n" +
				"/c4             - 모니터 4 캡처 전송\n" +
				"/d, /down       - PC 종료\n" +
				"/r, /reboot     - PC 재시작\n" +
				"/tray           - 시계 창 표시/숨김 토글\n" +
				"/cmd < 명령어 , 파일명.bat >  - 명령어 또는 파일명.bat 실행\n" +
				"/save <파일명>  - PC에 <파일명> 텍스트 파일 생성\n" +
			"( /save 로 배치파일을 만들 수 있습니다. 파일 내용을 2째 줄부터 입력하세요)" );
			break;
            case "/c":
            case "/capture":
			send(chatId, "📷 시계 캡처 중...");
			try {
				sendFile(chatId, handler.captureClockScreen());
                } catch (Exception ex) {
				send(chatId, "❌ 캡처 실패: " + ex.getMessage());
			}
			break;
			
            case "/s":
            case "/screenshot":
			send(chatId, "🖥 전체 화면 캡처 중...");
			/*
				new Thread(() -> {
				try   { sendFile(chatId, handler.captureFullScreen()); }
				catch (Exception ex) { send(chatId, "❌ 전체화면 캡처 실패: " + ex.getMessage()); }
				}, "ScreenCapture").start();
			*/
			new Thread(() -> {
				try {
					final File[] result = new File[1];
					SwingUtilities.invokeAndWait(() -> {
						try { result[0] = handler.captureFullScreen(); }
						catch (Exception e) { System.out.println("[Capture] " + e.getMessage()); }
					});
					if (result[0] != null) sendFile(chatId, result[0]);
				} catch (Exception e) { send(chatId, "❌ 전체화면 캡처 실패: " + e.getMessage()); }
			}, "ScreenCapture").start();
			break;
			
			case "/d":
            case "/down":
			pendingCmd.set("/down");
			send(chatId, "⚠️ PC를 종료하시겠습니까?\n/yes - 확인\n/no  - 취소");
			break;
			
            case "/r":
            case "/reboot":
			pendingCmd.set("/reboot");
			send(chatId, "🔄 PC를 재시작하시겠습니까?\n/yes - 확인\n/no  - 취소");
			break;			
			
            case "/n":
            case "/no": {
                // getAndSet("") : 읽기와 초기화를 원자적으로 처리
                String cancelled = pendingCmd.getAndSet("");
                if (!cancelled.isEmpty()) send(chatId, "✅ " + cancelled + " 취소되었습니다.");
                else                      send(chatId, "❓ 대기 중인 명령이 없습니다.");
                break;
			}
			
            case "":
            case " ":
			// 빈 메시지 무시
			break;
			
            default:
			send(chatId, "❓ 알 수 없는 명령어입니다.\n/help 로 명령어 목록을 확인하세요.");
			break;
		}  //  switch
	}  //  processCommand
	
	private void processCapture(String chatId, int monitor) {
		
	    // 모니터 개수 확인 (GraphicsEnvironment 사용)
		int monitorCount = java.awt.GraphicsEnvironment
        .getLocalGraphicsEnvironment()
        .getScreenDevices().length;

		if (monitor >= monitorCount) {
			send(chatId, "모니터 " + (monitor + 1) + "번 없음 (현재 " + monitorCount + "개 연결됨)");
			return;  // 스레드 실행 전에 조기 종료
		}
		
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					final File[] result = new File[1];
					SwingUtilities.invokeAndWait(new Runnable() {
						@Override
						public void run() {
							try {
								result[0] = handler.captureMonitor(monitor);
								} catch (Exception e) {
								System.out.println("[Capture] " + e.getMessage());
							}
						}
					});
					if (result[0] != null) sendFile(chatId, result[0]);
					else send(chatId, "캡처 실패: 결과 없음");
					} catch (Exception e) {
					send(chatId, "캡처 실패: " + e.getMessage());
				}
			}
		}, "TelegramCapture");
		t.setDaemon(true);
		t.start();
	}  //  processCapture
	
    private void processTray (String chatId, String text) {
		new Thread(() -> {
			try {
				final boolean[] visible = new boolean[1];
				SwingUtilities.invokeAndWait(() -> visible[0] = handler.toggleTrayWindow());
				if (visible[0]) send(chatId, "🪟 시계 창이 표시되었습니다.");
				else            send(chatId, "📥 시계 창이 트레이로 숨겨졌습니다.");
				} catch (Exception ex) {
				send(chatId, "❌ 트레이 토글 실패: " + ex.getMessage());
			}
		}, "TrayToggle").start();
	}  //  processTray
	
	private void processYes(String chatId, String text) {
		// getAndSet("") : 읽기와 초기화를 원자적으로 처리 → 중복 실행 원천 차단
		String pending = pendingCmd.getAndSet("");
		if (pending.equals("/down")) {
			sendShutdownNotice(false);
			new Thread(() -> {
				try {
					Thread.sleep(2000);
					handler.shutdownPC();
					} catch (Exception ex) {
					send(chatId, "❌ 종료 실패: " + ex.getMessage());
				}
			}, "Shutdown").start();
			} else if (pending.equals("/reboot")) {
			sendShutdownNotice(true);
			new Thread(() -> {
				try {
					Thread.sleep(2000);
					handler.rebootPC();
					} catch (Exception ex) {
					send(chatId, "❌ 재시작 실패: " + ex.getMessage());
				}
			}, "Reboot").start();
			} else {
			send(chatId, "❓ 대기 중인 명령이 없습니다.");
		}
		return ;
	}   // processYes
	
	private void processStart(String chatId, String text) {
		new Thread(() -> {
			try {
				String now      = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
				String pcName   = java.net.InetAddress.getLocalHost().getHostName();
				String userId   = System.getProperty("user.name");
				String osName   = System.getProperty("os.name") + " " + System.getProperty("os.version");
				String javaVer  = System.getProperty("java.version");
				String localIp  = java.net.InetAddress.getLocalHost().getHostAddress();
				String publicIp = getPublicIp();
				String msg = "🖥 PC 정보\n\n"
				+ "🕐 현재 시각: " + now      + "\n"
				+ "💻 PC 이름 : " + pcName   + "\n"
				+ "👤 사용자  : " + userId   + "\n"
				+ "🌐 IP (내부): " + localIp  + "\n"
				+ "🌍 IP (외부): " + publicIp + "\n"
				+ "🖥 OS      : " + osName   + "\n"
				+ "☕ Java    : " + javaVer;
				send(chatId, msg);
				} catch (Exception ex) {
				send(chatId, "❌ PC 정보 조회 실패: " + ex.getMessage());
			}
		}, "WhInfo").start();
	}  //  processStart
	
    private void processSave(String chatId, String text) {
		String saveArgs = text.substring("/save".length());
		int nl = saveArgs.indexOf('\n');
		if (nl < 0) {
			send(chatId, "사용법: /save 파일명\n내용 첫째줄\n내용 둘째줄\n...");
			return ;
		}
		String fileName = saveArgs.substring(0, nl).trim();
		String content  = saveArgs.substring(nl + 1);
		if (fileName.isEmpty()) {
			send(chatId, "❌ 파일명이 없습니다.");
			return ;
		}
		if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
			send(chatId, "❌ 파일명에 경로 문자를 포함할 수 없습니다.");
			return ;
		}
		try {
			// java.io.File saveFile = new java.io.File(System.getProperty("user.dir"), fileName);
			java.io.File saveFile = new java.io.File(resolveRunDir(), fileName);
			if (saveFile.exists()) {
				send(chatId, "❌ 이미 존재하는 파일입니다. 다른 파일 명칭을 지정하세요: " + saveFile.getAbsolutePath());
				return ;
			}
			try (java.io.PrintWriter pw = new java.io.PrintWriter(
				new java.io.OutputStreamWriter(
				new java.io.FileOutputStream(saveFile), "UTF-8"))) {
				pw.print(content);
			}
			send(chatId, "✅ 저장 완료: " + saveFile.getAbsolutePath());
			} catch (Exception ex) {
			send(chatId, "❌ 저장 실패: " + ex.getMessage());
		}
		return ;
	}	//  processSave
	
    private void processCMD(String chatId, String text) 	{
		String command = text.substring("/cmd".length()).trim();
		if (command.isEmpty()) {
			send(chatId, "사용법: /cmd <명령어>\n예) /cmd dir\n예) /cmd ipconfig");
			return ;
		}
		// ── 위험 명령어 차단 ──────────────────────────────
		String blockedKeyword = findBlockedKeyword(command);
		if (blockedKeyword != null) {
			send(chatId, "🚫 [" + blockedKeyword + "] 는 처리 할 수 없습니다.");
			AppLogger.writeToFile("[CMD Block] chatId=" + chatId + " cmd=" + command);
			return ;
		}
		// ─────────────────────────────────────────────────
		final String finalCmd = command;
		new Thread(() -> {
			try {
				ProcessBuilder pb = new ProcessBuilder();
				pb.command("cmd.exe", "/c", finalCmd);
				pb.redirectErrorStream(true);
				Process proc = pb.start();
				java.io.BufferedReader br = new java.io.BufferedReader(
				new java.io.InputStreamReader(proc.getInputStream(), "MS949"));
				StringBuilder sb = new StringBuilder();
				String line;
				int lineCount = 0, maxLines = 50;
				while ((line = br.readLine()) != null) {
					if (lineCount < maxLines) sb.append(line).append("\n");
					lineCount++;
				}
				proc.waitFor();
				if (lineCount > maxLines)
				sb.append("\n... (").append(lineCount).append("줄 중 ").append(maxLines).append("줄만 표시)");
				if (sb.length() > 0) send(chatId, "```\n" + sb + "```");
				else                 send(chatId, "✅ 실행 완료 (출력 없음)");
				} catch (Exception ex) {
				send(chatId, "❌ 실행 실패: " + ex.getMessage());
			}
		}, "CmdExec").start();
		return ;
	}  //  processCMD
	
	// ── 메시지 전송 ───────────────────────────────────────────────
    /** 텍스트 메시지 전송 */
    public void send(String chatId, String text) {
        if (botToken.isEmpty() || chatId.isEmpty()) {
            System.out.println("[Telegram] Bot Token 또는 Chat ID 없음");
            return;
		}
        try {
            URL url = toUrl("https://api.telegram.org/bot" + botToken + "/sendMessage");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setConnectTimeout(10000);
            con.setReadTimeout(10000);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            String body = "chat_id=" + java.net.URLEncoder.encode(chatId, "UTF-8")
			+ "&text="    + java.net.URLEncoder.encode(text,   "UTF-8");
            con.getOutputStream().write(body.getBytes("UTF-8"));
            int code = con.getResponseCode();
            con.disconnect();
            System.out.println("[Telegram] 발송 완료 code=" + code);
			} catch (Exception e) {
            System.out.println("[Telegram] 발송 오류: " + e.getMessage());
		}
	}
    /** 파일(이미지/문서) 전송 */
    public void sendFile(String chatId, File file) throws Exception {
        String name    = file.getName().toLowerCase();
        boolean isImg  = name.endsWith(".jpg") || name.endsWith(".jpeg")
		|| name.endsWith(".png") || name.endsWith(".gif")
		|| name.endsWith(".bmp") || name.endsWith(".webp");
        String method  = isImg ? "sendPhoto"    : "sendDocument";
        String field   = isImg ? "photo"        : "document";
        String boundary = "----TelegramBoundary" + System.currentTimeMillis();
		
        URL url = toUrl("https://api.telegram.org/bot" + botToken + "/" + method);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setConnectTimeout(30000);
        con.setReadTimeout(60000);
        con.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
		
        try (java.io.OutputStream out = con.getOutputStream()) {
            // chat_id 파트
            out.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n"
			+ chatId + "\r\n").getBytes("UTF-8"));
            // 파일 파트 헤더
            out.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + field
                + "\"; filename=\"" + file.getName() + "\"\r\n"
			+ "Content-Type: application/octet-stream\r\n\r\n").getBytes("UTF-8"));
            // 파일 내용
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = fis.read(buf)) != -1) out.write(buf, 0, len);
			}
            out.write(("\r\n--" + boundary + "--\r\n").getBytes("UTF-8"));
		}
        int code = con.getResponseCode();
        con.disconnect();
        if (code != 200) throw new Exception("HTTP " + code);
	}
	
    // ── 첨부파일 수신 저장 ─────────────────────────────────────────
    /**
		* 스마트폰에서 보낸 photo / document / audio / video / voice 를
		* 현재폴더/download/ 에 저장한다.
	*/
    private void receiveFile(String chatId, String block) {
        try {
            // ── file_id 추출: 우선순위 photo > document > video > audio > voice > animation
            String fileId = null;
            String savedName = null;
			
            // photo: 배열 중 가장 큰 해상도 (마지막 file_id)
            Pattern photoIdPat = Pattern.compile("\"file_id\":\"([^\"]+)\"");
            if (block.contains("\"photo\"")) {
                Matcher m = photoIdPat.matcher(block);
                while (m.find()) fileId = m.group(1); // 마지막 = 최고해상도
                if (fileId != null) {
                    // 타임스탬프 파일명
                    savedName = "photo_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss")
					.format(new java.util.Date()) + ".jpg";
				}
			}
			
            // document / video / audio / voice / animation
            if (fileId == null) {
                String[] types = {"document", "video", "audio", "voice", "animation"};
                for (String t : types) {
                    if (!block.contains("\"" + t + "\"")) continue;
                    // 해당 타입 블록 먼저 추출 (file_name을 typeBlock 안에서만 검색)
                    int ti = block.indexOf("\"" + t + "\"");
                    String typeBlock = block.substring(ti);
                    // file_name 추출
                    Pattern fnPat = Pattern.compile("\"file_name\":\"([^\"]+)\"");
                    Matcher fnMat = fnPat.matcher(typeBlock);
                    if (fnMat.find()) {
                        savedName = sanitizeFileName(fnMat.group(1));
						} else {
                        // file_name 없을 때 타입별 기본 확장자 부여 (없으면 isMediaFile() 판별 실패)
                        java.util.Map<String, String> defaultExt = new java.util.HashMap<>();
                        defaultExt.put("video",     ".mp4");
                        defaultExt.put("audio",     ".mp3");
                        defaultExt.put("voice",     ".ogg");
                        defaultExt.put("animation", ".gif");
                        defaultExt.put("document",  "");
                        String defExt = defaultExt.getOrDefault(t, "");
                        savedName = t + "_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss")
						.format(new java.util.Date()) + defExt;
					}
                    // file_id: 해당 타입 객체 전체에서 마지막 file_id 사용
                    // 텔레그램 video JSON 구조: thumbnail{file_id}, thumb{file_id}, file_id(본체)
                    // → thumbnail/thumb 의 file_id 가 먼저, 본체 file_id 가 마지막에 위치
                    Matcher idMat = photoIdPat.matcher(typeBlock);
                    while (idMat.find()) fileId = idMat.group(1); // 마지막 = 본체
                    break;
				}
			}
			
            if (fileId == null || savedName == null) {
                System.out.println("[Telegram] 첨부파일 file_id 추출 실패");
                return;
			}
			
            // ── getFile API → file_path + file_size 획득 (가장 정확한 크기 출처)
            String[] fileInfo = getFileInfo(fileId);
            if (fileInfo == null) {
                send(chatId, "❌ 파일 경로 조회 실패");
                return;
			}
            String filePath = fileInfo[0];
            long   fileSize = Long.parseLong(fileInfo[1]); // -1이면 크기 미제공
			
            // ── 파일 크기 체크
            // 이미지/미디어: 50MB, 일반 파일: 20MB (텔레그램 Bot API 실제 상한 50MB)
            boolean isMediaOrImage = isImageFile(new java.io.File(savedName))
			|| isMediaFile(new java.io.File(savedName));
            long   limitBytes = isMediaOrImage ? 50L * 1024 * 1024 : 20L * 1024 * 1024;
            String limitLabel = isMediaOrImage ? "50MB" : "20MB";
			
            // path==null 이면 텔레그램이 getFile 자체를 거부 = 50MB 초과 확실
            if (filePath == null || fileSize < 0) {
                System.out.println("[Telegram] 파일 제공 불가 -> 50MB 초과");
                send(chatId, "❌ 파일이 너무 커서 저장할 수 없습니다.\n"
                    + "⚠️ 최대 허용: " + limitLabel + "\n"
				+ "(텔레그램 Bot API 50MB 제한)");
                return;
			}
            if (fileSize > limitBytes) {
                String sizeMB = String.format("%.1f", fileSize / 1024.0 / 1024.0);
                System.out.println("[Telegram] 파일 크기 초과: " + sizeMB + "MB -> 수신 거부");
                send(chatId, "❌ 파일 크기 초과로 저장할 수 없습니다.\n"
                    + "📦 파일 크기: " + sizeMB + "MB\n"
				+ "⚠️ 최대 허용: " + limitLabel);
                return;
			}
			
            // ── download 폴더 생성
            // java.io.File dlDir = new java.io.File(System.getProperty("user.dir"), "download");
			java.io.File dlDir = new java.io.File(resolveRunDir(), "download");
			if (!dlDir.exists()) dlDir.mkdirs();
			
            // ── 중복 파일명 처리
            java.io.File outFile = new java.io.File(dlDir, savedName);
            if (outFile.exists()) {
                String base = savedName.contains(".")
				? savedName.substring(0, savedName.lastIndexOf('.'))
				: savedName;
                String ext  = savedName.contains(".")
				? savedName.substring(savedName.lastIndexOf('.'))
				: "";
                int seq = 1;
                while (outFile.exists()) {
                    outFile = new java.io.File(dlDir, base + "(" + seq++ + ")" + ext);
				}
			}
			
            // ── 실제 다운로드
            String downloadUrl = "https://api.telegram.org/file/bot" + botToken + "/" + filePath;
            HttpURLConnection con = (HttpURLConnection) toUrl(downloadUrl).openConnection();
            con.setConnectTimeout(30000);
            con.setReadTimeout(60000);
            int dlCode = con.getResponseCode();
            if (dlCode != 200) {
                con.disconnect();
                System.out.println("[Telegram] 다운로드 실패 HTTP " + dlCode);
                send(chatId, "❌ 파일 다운로드 실패 (HTTP " + dlCode + ")\n"
				+ "파일이 너무 크거나 만료되었을 수 있습니다.");
                return;
			}
            try (java.io.InputStream in  = con.getInputStream();
				java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) fos.write(buf, 0, len);
			}
            con.disconnect();
			
            System.out.println("[Telegram] 파일 저장 완료: " + outFile.getAbsolutePath());
            send(chatId, "✅ 파일 저장 완료\n📁 " + outFile.getName()
			+ "\n📂 " + outFile.getAbsolutePath());
			
            // ── 이미지 파일이면 PC 화면에 즉시 표시
            if (isImageFile(outFile) && handler != null) {
                final java.io.File imgFile = outFile;
                javax.swing.SwingUtilities.invokeLater(() -> handler.showImage(imgFile));
			}
            // ── 미디어 파일이면 wmplayer로 즉시 재생
            if (isMediaFile(outFile) && handler != null) {
                final java.io.File mediaFile = outFile;
                new Thread(() -> handler.playMedia(mediaFile), "TelegramMediaPlay").start();
			}
			
			} catch (Exception e) {
            System.out.println("[Telegram] 파일 수신 오류: " + e.getMessage());
            send(chatId, "❌ 파일 수신 실패: " + e.getMessage());
		}
	}
	
    /** 이미지 파일 여부 판별 (확장자 기준) */
    private static boolean isImageFile(java.io.File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".jpg") || n.endsWith(".jpeg")
		|| n.endsWith(".png") || n.endsWith(".gif")
		|| n.endsWith(".bmp") || n.endsWith(".webp");
	}
	
    /** 미디어 파일 여부 판별 (확장자 기준) */
    private static boolean isMediaFile(java.io.File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".mp3") || n.endsWith(".mp4")
		|| n.endsWith(".wav") || n.endsWith(".m4a")
		|| n.endsWith(".aac") || n.endsWith(".ogg")
		|| n.endsWith(".wma") || n.endsWith(".avi");
	}
	
    /**
		* Windows 파일명 금지 문자 제거/치환.
		* 금지 문자: \ / : * ? " < > | #
		* 제어문자(0x00~0x1F) 도 제거.
		* 결과가 비거나 점(.)만 남으면 "file" 로 대체.
	*/
    private static String sanitizeFileName(String name) {
        if (name == null || name.isEmpty()) return "file";
        // Windows 금지 문자 → _
        String s = name.replaceAll("[\\\\/:*?\"<>|#]", "_");
        // 제어문자 제거
        s = s.replaceAll("[\\x00-\\x1F]", "");
        // 앞뒤 공백·점 제거
        s = s.trim().replaceAll("^\\.+", "").replaceAll("\\.+$", "");
        return s.isEmpty() ? "file" : s;
	}
	
    /** getFile API로 file_path, file_size 동시 조회. [0]=path, [1]=size(-1이면 없음) */
    private String[] getFileInfo(String fileId) {
        try {
            String apiUrl = "https://api.telegram.org/bot" + botToken + "/getFile?file_id=" + fileId;
            HttpURLConnection con = (HttpURLConnection) toUrl(apiUrl).openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            int httpCode = con.getResponseCode();
            // 400 = 파일이 너무 커서 텔레그램이 제공 불가 (실제 50MB 초과)
            if (httpCode != 200) {
                System.out.println("[Telegram] getFileInfo HTTP " + httpCode + " -> 파일 제공 불가 (50MB 초과 추정)");
                con.disconnect();
                return new String[]{null, "-1"};  // path=null → 호출부에서 크기초과 에러 처리
			}
            java.io.BufferedReader br = new java.io.BufferedReader(
			new java.io.InputStreamReader(con.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            con.disconnect();
            String resp = sb.toString();
            Matcher pm = Pattern.compile("\"file_path\":\"([^\"]+)\"").matcher(resp);
            Matcher sm = Pattern.compile("\"file_size\":(\\d+)").matcher(resp);
            String path = pm.find() ? pm.group(1) : null;
            long   size = sm.find() ? Long.parseLong(sm.group(1)) : -1L;
            System.out.println("[Telegram] getFileInfo path=" + path + " size=" + size);
            return new String[]{path, String.valueOf(size)};
			} catch (Exception e) {
            System.out.println("[Telegram] getFileInfo 오류: " + e.getMessage());
            return null;
		}
	}
	/** jar / exe / class 어떤 방식으로 실행해도 실행 파일의 폴더를 반환 */
	private static java.io.File resolveRunDir() {
		// ① sun.java.command (jar / exe)
		try {
			String sc = System.getProperty("sun.java.command", "").trim();
			String first = sc.split("\\s+")[0];
			if (first.endsWith(".jar") || first.endsWith(".exe")) {
				java.io.File f = new java.io.File(first).getAbsoluteFile();
				if (f.getParentFile() != null) return f.getParentFile();
			}
		} catch (Exception ignored) {}
		// ② CodeSource (class / jar)
		try {
			java.io.File f = new java.io.File(
				TelegramBot.class.getProtectionDomain()
			.getCodeSource().getLocation().toURI()).getAbsoluteFile();
			java.io.File parent = f.isDirectory() ? f : f.getParentFile();
			if (parent != null) return parent;
		} catch (Exception ignored) {}
		// ③ 최후 fallback
		return new java.io.File(System.getProperty("user.home"));
	}
    // ── 위험 명령어 차단 목록 ─────────────────────────────────────
    /**
		* 실행을 금지할 명령어 키워드 목록.
		*
		* 비교 방식: 입력된 명령어를 소문자로 변환한 뒤
		* 아래 키워드 중 하나라도 포함(contains)되면 차단.
		*
		* ─ 파일/디스크 파괴
		*   format, del /f, erase, rd /s, rmdir /s,
		*   diskpart, cipher /w, sdelete
		* ─ 시스템/계정 변경
		*   net user, net localgroup, reg delete, reg add,
		*   bcdedit, bootrec, attrib +h +s
		* ─ 보안 우회
		*   netsh firewall, netsh advfirewall,
		*   sc delete, sc stop, taskkill /f,
		*   icacls, cacls, takeown
		* ─ 악성 실행
		*   powershell -enc, powershell -exec bypass,
		*   wscript, cscript, mshta, rundll32,
		*   regsvr32 /u, certutil -decode
		* ─ 네트워크 공격
		*   ping -t, ping -n 9999, arp -d, route delete
		* ─ 기타
		*   shutdown /s, shutdown /r, logoff,
		*   wmic process delete, vssadmin delete
	*/
    private static final java.util.List<String> BLOCKED_KEYWORDS =
	java.util.Arrays.asList(
		// 파일/디스크 파괴
		"format ",         // format c: 등 (뒤에 공백 필수 → format.com 단독 실행 허용)
		"del /f",
		"del/f",
		"erase /f",
		"rd /s",
		"rmdir /s",
		"diskpart",
		"cipher /w",
		"sdelete",
		// 시스템/계정 변경
		"net user",
		"net localgroup",
		"reg delete",
		"reg add",
		"bcdedit",
		"bootrec",
		// 보안 우회
		"netsh firewall",
		"netsh advfirewall",
		"sc delete",
		"sc stop",
		"taskkill /f",
		"icacls",
		"cacls",
		"takeown",
		// 악성 실행 패턴
		"-enc ",           // powershell -EncodedCommand
		"-exec bypass",    // powershell -ExecutionPolicy Bypass
		"wscript",
		"cscript",
		"mshta",
		"rundll32",
		"regsvr32 /u",
		"certutil -decode",
		// 네트워크 공격
		"ping -t",
		"arp -d",
		"route delete",
		// 기타 위험
		"shutdown /s",
		"shutdown /r",
		"shutdown/s",
		"shutdown/r",
		"logoff",
		"wmic process delete",
		"vssadmin delete"
	);
	
    /**
		* 입력 명령어에 차단 키워드가 포함되어 있으면 해당 키워드를 반환.
		* 안전하면 null 반환.
		*
		* @param command 사용자가 입력한 /cmd 뒤의 문자열
		* @return 차단 키워드 (없으면 null)
	*/
    private static String findBlockedKeyword(String command) {
        String lower = command.toLowerCase();
        for (String keyword : BLOCKED_KEYWORDS) {
            if (lower.contains(keyword)) return keyword.trim();
		}
        return null;
	}
	
    // ── 유틸 ──────────────────────────────────────────────────────
    @SuppressWarnings("deprecation")
    private static URL toUrl(String s) {
        try { return new URL(s); }
        catch (Exception e) { throw new RuntimeException(e); }
	}

    // ── 텔레그램 보내기 다이얼로그 ───────────────────────────────
    /**
     * 텔레그램 보내기 다이얼로그를 표시합니다.
     * EDT(Event Dispatch Thread)에서 호출해야 합니다.
     *
     * @param owner 부모 Window (JFrame 등)
     */
    public void showTelegramDialog(java.awt.Window owner) {
        JDialog dlg = new JDialog(owner instanceof java.awt.Frame ? (java.awt.Frame) owner
                : null, "✈️ 텔레그램 보내기", false);
        dlg.setLayout(new BorderLayout(8, 8));
        dlg.getRootPane().setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        // ── Bot Token / Chat ID 설정 패널 ────────────────────
        JPanel cfgPanel = new JPanel(new GridBagLayout());
        cfgPanel.setBorder(BorderFactory.createTitledBorder("설정"));
        GridBagConstraints cg = new GridBagConstraints();
        cg.insets = new Insets(3, 4, 3, 4);
        cg.anchor = GridBagConstraints.WEST;

        cg.gridx=0; cg.gridy=0; cfgPanel.add(new JLabel("Bot Token:"), cg);
        JTextField tokenField = new JTextField(botToken, 30);
        cg.gridx=1; cg.fill=GridBagConstraints.HORIZONTAL; cg.weightx=1;
        cfgPanel.add(tokenField, cg);

        cg.gridx=0; cg.gridy=1; cg.fill=GridBagConstraints.NONE; cg.weightx=0;
        cfgPanel.add(new JLabel("Chat ID:"), cg);
        JTextField chatIdField = new JTextField(myChatId, 20);
        chatIdField.setToolTipText("@userinfobot 에게 /start 보내면 Chat ID 확인 가능");
        cg.gridx=1; cg.fill=GridBagConstraints.HORIZONTAL; cg.weightx=1;
        cfgPanel.add(chatIdField, cg);

        // 원격제어 폴링 ON/OFF
        cg.gridx=0; cg.gridy=2; cg.fill=GridBagConstraints.NONE; cg.weightx=0;
        cg.gridwidth=2;
        JCheckBox pollingCb = new JCheckBox(
            "🎮 원격제어 활성화 (5초마다 명령 수신)", polling);
        pollingCb.setToolTipText("텔레그램에서 /help /screenshot /shutdown 등 명령 수신");
        pollingCb.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        cfgPanel.add(pollingCb, cg);
        cg.gridwidth=1;

        cg.gridx=0; cg.gridy=3; cg.gridwidth=2;
        cfgPanel.add(new JLabel(
            "<html><font color=gray size=-1>" +
            "※ 원격제어: /help /capture /screenshot /shutdown /reboot" +
            "</font></html>"), cg);
        cg.gridwidth=1;

        // ── 메시지 입력 패널 ──────────────────────────────────
        JPanel msgPanel = new JPanel(new BorderLayout(4, 4));
        msgPanel.setBorder(BorderFactory.createTitledBorder("메시지"));
        JTextArea msgArea = new JTextArea(5, 36);
        msgArea.setLineWrap(true);
        msgArea.setWrapStyleWord(true);
        msgArea.setText("안녕하세요!\n\n[끝판왕]에서 텔레그램으로 개통 축하 인사 보냅니다.\n\n"
            + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
        msgPanel.add(new JScrollPane(msgArea), BorderLayout.CENTER);

        // ── 첨부파일 패널 ─────────────────────────────────────
        JPanel attachPanel = new JPanel(new BorderLayout(4, 4));
        attachPanel.setBorder(BorderFactory.createTitledBorder("첨부파일"));

        DefaultListModel<String> fileListModel = new DefaultListModel<>();
        java.util.List<File> attachedFiles = new java.util.ArrayList<>();
        JList<String> fileList = new JList<>(fileListModel);
        fileList.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        JScrollPane fileScroll = new JScrollPane(fileList);
        fileScroll.setPreferredSize(new Dimension(400, 80));

        JPanel attachBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

        // 파일 추가 버튼
        JButton addFileBtn = new JButton("📎 파일 추가");
        addFileBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(System.getProperty("user.home"));
            fc.setMultiSelectionEnabled(true);
            fc.setDialogTitle("첨부할 파일 선택");
            if (fc.showOpenDialog(dlg) == JFileChooser.APPROVE_OPTION) {
                for (File f : fc.getSelectedFiles()) {
                    attachedFiles.add(f);
                    fileListModel.addElement(f.getName()
                        + "  (" + (f.length() / 1024) + " KB)");
                }
            }
        });

        // 이미지 추가 버튼
        JButton addImgBtn = new JButton("🖼 이미지 추가");
        addImgBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(System.getProperty("user.home"));
            fc.setMultiSelectionEnabled(true);
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "이미지", "jpg","jpeg","png","gif","bmp","webp"));
            fc.setDialogTitle("첨부할 이미지 선택");
            if (fc.showOpenDialog(dlg) == JFileChooser.APPROVE_OPTION) {
                for (File f : fc.getSelectedFiles()) {
                    attachedFiles.add(f);
                    fileListModel.addElement("🖼 " + f.getName()
                        + "  (" + (f.length() / 1024) + " KB)");
                }
            }
        });

        // 시계 화면 캡처 버튼
        JButton captureBtn = new JButton("📷 시계 캡처");
        captureBtn.addActionListener(e -> {
            try {
                File capFile = (handler != null) ? handler.captureClockScreen() : null;
                if (capFile != null) {
                    attachedFiles.add(capFile);
                    fileListModel.addElement("📷 " + capFile.getName()
                        + "  (" + (capFile.length() / 1024) + " KB)");
                    JOptionPane.showMessageDialog(dlg,
                        "시계 화면이 캡처되었습니다.", "캡처 완료",
                        JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg,
                    "캡처 실패: " + ex.getMessage(), "오류",
                    JOptionPane.ERROR_MESSAGE);
            }
        });

        // 선택 삭제 버튼
        JButton removeBtn = new JButton("🗑 삭제");
        removeBtn.addActionListener(e -> {
            int idx = fileList.getSelectedIndex();
            if (idx >= 0) {
                attachedFiles.remove(idx);
                fileListModel.remove(idx);
            }
        });

        attachBtnPanel.add(addFileBtn);
        attachBtnPanel.add(addImgBtn);
        attachBtnPanel.add(captureBtn);
        attachBtnPanel.add(removeBtn);
        attachPanel.add(fileScroll,     BorderLayout.CENTER);
        attachPanel.add(attachBtnPanel, BorderLayout.SOUTH);

        // ── 전송 상태 표시 ────────────────────────────────────
        JLabel statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(0, 120, 0));

        // ── 버튼 패널 ─────────────────────────────────────────
        JButton sendBtn  = new JButton("  ✈️ 전송  ");
        JButton closeBtn = new JButton("  닫기  ");
        sendBtn.setBackground(new Color(0, 136, 204));
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setOpaque(true);
        sendBtn.setBorderPainted(false);
        sendBtn.setFont(new Font("Malgun Gothic", Font.BOLD, 13));

        sendBtn.addActionListener(e -> {
            // 설정 저장
            botToken = tokenField.getText().trim();
            myChatId = chatIdField.getText().trim();
            polling  = pollingCb.isSelected();
            String chatId = chatIdField.getText().trim();
            String text   = msgArea.getText().trim();

            if (botToken.isEmpty()) {
                JOptionPane.showMessageDialog(dlg,
                    "Bot Token을 입력하세요.", "텔레그램", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (chatId.isEmpty()) {
                JOptionPane.showMessageDialog(dlg,
                    "Chat ID를 입력하세요.", "텔레그램", JOptionPane.WARNING_MESSAGE);
                return;
            }

            sendBtn.setEnabled(false);
            statusLabel.setText("전송 중...");
            statusLabel.setForeground(new Color(200, 100, 0));

            new Thread(() -> {
                StringBuilder result = new StringBuilder();
                boolean anyError = false;

                // 1) 텍스트 메시지 전송
                if (!text.isEmpty()) {
                    try {
                        send(chatId, text);
                        result.append("✅ 텍스트 전송 완료\n");
                    } catch (Exception ex) {
                        result.append("❌ 텍스트 전송 실패: ").append(ex.getMessage()).append("\n");
                        anyError = true;
                    }
                }

                // 2) 첨부파일 전송
                for (File f : attachedFiles) {
                    try {
                        sendFile(chatId, f);
                        result.append("✅ ").append(f.getName()).append(" 전송 완료\n");
                    } catch (Exception ex) {
                        result.append("❌ ").append(f.getName())
                            .append(" 실패: ").append(ex.getMessage()).append("\n");
                        anyError = true;
                    }
                }

                final String finalResult = result.toString();
                final boolean hasError   = anyError;
                SwingUtilities.invokeLater(() -> {
                    sendBtn.setEnabled(true);
                    statusLabel.setForeground(hasError ? Color.RED : new Color(0, 120, 0));
                    statusLabel.setText(hasError ? "일부 실패" : "전송 완료 ✅");
                    JOptionPane.showMessageDialog(dlg,
                        finalResult.isEmpty() ? "보낼 내용이 없습니다." : finalResult,
                        "전송 결과", hasError ?
                        JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
                });
            }, "TelegramSend").start();
        });

        closeBtn.addActionListener(e -> {
            botToken = tokenField.getText().trim();
            myChatId = chatIdField.getText().trim();
            polling  = pollingCb.isSelected();
            if (polling) startPolling();
            else         stopPolling();
            if (handler != null) handler.saveConfig();
            dlg.dispose();
        });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btnRow.add(statusLabel);
        btnRow.add(sendBtn);
        btnRow.add(closeBtn);

        // ── 레이아웃 조립 ─────────────────────────────────────
        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.add(msgPanel,    BorderLayout.CENTER);
        center.add(attachPanel, BorderLayout.SOUTH);

        dlg.add(cfgPanel, BorderLayout.NORTH);
        dlg.add(center,   BorderLayout.CENTER);
        dlg.add(btnRow,   BorderLayout.SOUTH);

        if (handler != null) handler.prepareDialog(dlg);
        dlg.setVisible(true);
    }

    // ── 텔레그램 안내 HTML 파일 열기 ─────────────────────────────
    public void showTelegramHelp(java.awt.Component parent) {
        try {
            // TELEGRAM_help.txt 탐색: 실행 파일 옆 폴더 우선
            java.io.File txtFile = new java.io.File("TELEGRAM_help.txt");
            if (!txtFile.exists()) {
                try {
                    java.security.CodeSource cs = getClass().getProtectionDomain().getCodeSource();
                    if (cs != null) {
                        java.io.File base = new java.io.File(cs.getLocation().toURI());
                        java.io.File dir  = base.isDirectory() ? base : base.getParentFile();
                        txtFile = new java.io.File(dir, "TELEGRAM_help.txt");
                    }
                } catch (Exception ignored) {}
            }

            String content;
            if (txtFile.exists()) {
                java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(
                        new java.io.FileInputStream(txtFile), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
                br.close();
                content = sb.toString();
            } else {
                content = "TELEGRAM_help.txt 파일을 찾을 수 없습니다.\n실행 파일과 같은 폴더에 TELEGRAM_help.txt 를 넣어주세요.";
            }

            // URL → <a href> 링크 변환 후 HTML 생성
            String escaped = content
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            String html = escaped.replaceAll(
                "(https?://[\\S]+)",
                "<a href='$1' target='_blank'>$1</a>");

            java.io.File htmlFile = java.io.File.createTempFile("telegram_help_", ".html");
            htmlFile.deleteOnExit();
            try (java.io.PrintWriter pw = new java.io.PrintWriter(
                    new java.io.OutputStreamWriter(
                        new java.io.FileOutputStream(htmlFile), "UTF-8"))) {
                pw.println("<!DOCTYPE html><html><head>");
                pw.println("<meta charset='UTF-8'>");
                pw.println("<title>텔레그램 설정 안내</title>");
                pw.println("<style>");
                pw.println("body { font-family: 'Malgun Gothic', monospace;");
                pw.println("       background:#1a1a2e; color:#e0e0e0;");
                pw.println("       padding:30px; line-height:1.7; }");
                pw.println("pre  { white-space:pre-wrap; font-size:14px; }");
                pw.println("a    { color:#4fc3f7; font-weight:bold; }");
                pw.println("a:hover { color:#81d4fa; }");
                pw.println("</style></head><body><pre>");
                pw.println(html);
                pw.println("</pre></body></html>");
            }
            java.awt.Desktop.getDesktop().browse(htmlFile.toURI());

        } catch (Exception e) {
            javax.swing.JOptionPane.showMessageDialog(parent,
                "안내 파일 열기 실패: " + e.getMessage(),
                "오류", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }
}