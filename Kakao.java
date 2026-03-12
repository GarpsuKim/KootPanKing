import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Kakao {
	
	// ── 카카오톡 ────────────────────────────────────────────────
	private static final String KAKAO_REDIRECT = "http://localhost:8080/callback";

	// ※ 소스코드에 API 키를 절대 하드코딩하지 마세요.
	//    아래 두 값은 clock_config.properties 에서 로드되거나
	//    설정 UI(알람 관리 → 카카오 설정)에서 사용자가 직접 입력합니다.
	String kakaoRestApiKey   = "";  // clock_config: kakao.apiKey
	String kakaoClientSecret = "";  // clock_config: kakao.clientSecret

	String kakaoAccessToken  = "";
	String kakaoRefreshToken = "";
	
	Kakao () {	}	
	
    /** Kakao.txt 를 HTML 로 변환하여 기본 브라우저로 표시 */
    void showKakaoGuide() {
        try {
            // Kakao.txt 경로: 실행 파일 옆 또는 현재 디렉토리
            File txtFile = new File("Kakao.txt");
            if (!txtFile.exists()) {
                try {
                    File jar = new File(Kakao.class.getProtectionDomain()
					.getCodeSource().getLocation().toURI());
                    txtFile = new File(jar.getParent(), "Kakao.txt");
				} catch (Exception ignored) {}
			}
			
            String content = "";
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
					content = "Kakao.txt 파일을 찾을 수 없습니다.\n실행 파일과 같은 폴더에 Kakao.txt 를 넣어주세요.";
			}
			
            // URL 을 <a href> 링크로 변환
            String html = textToHtml(content);
			
            // 임시 HTML 파일 생성 후 브라우저로 열기
            File htmlFile = File.createTempFile("kakao_guide_", ".html");
            htmlFile.deleteOnExit();
            try (java.io.PrintWriter pw = new java.io.PrintWriter(
				new java.io.OutputStreamWriter(
				new java.io.FileOutputStream(htmlFile), "UTF-8"))) {
                pw.println("<!DOCTYPE html><html><head>");
                pw.println("<meta charset='UTF-8'>");
                pw.println("<title>카카오톡 설정 안내</title>");
                pw.println("<style>");
                pw.println("body { font-family: 'Malgun Gothic', monospace; ");
                pw.println("       background:#1a1a2e; color:#e0e0e0; ");
                pw.println("       padding:30px; line-height:1.7; }");
                pw.println("pre { white-space:pre-wrap; font-size:14px; }");
                pw.println("a { color:#4fc3f7; font-weight:bold; }");
                pw.println("a:hover { color:#81d4fa; }");
                pw.println("</style></head><body><pre>");
                pw.println(html);
                pw.println("</pre></body></html>");
			}
            Desktop.getDesktop().browse(htmlFile.toURI());
			
			} catch (Exception e) {
            prepareMessageBox();
            JOptionPane.showMessageDialog(null,
			"안내 파일 열기 실패: " + e.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
		}
	}
	
    /** 텍스트에서 URL 을 HTML 링크로 변환 */
    private String textToHtml(String text) {
        // HTML 특수문자 이스케이프
        text = text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
        // http/https URL → <a href> 링크 (새 탭으로 열기)
        text = text.replaceAll(
            "(https?://[\\S]+)",
		"<a href='$1' target='_blank'>$1</a>");
        return text;
	}
	
    /** 카카오 로그인 - 브라우저 열기 + 로컬 서버로 코드 수신 */
	void kakaoLogin() {
        if (kakaoRestApiKey.isEmpty() || kakaoClientSecret.isEmpty()) {
            prepareMessageBox();
            JOptionPane.showMessageDialog(null,
                "REST API 키와 Client Secret을 먼저 입력하세요.\n" +
                "설정 방법: 알람 관리 → 카카오 설정 탭",
                "카카오 로그인", JOptionPane.WARNING_MESSAGE);
            return;
		}
        try {
			
			String authUrl = "https://kauth.kakao.com/oauth/authorize"
			+ "?client_id=" + kakaoRestApiKey
			+ "&redirect_uri=" + java.net.URLEncoder.encode(KAKAO_REDIRECT, "UTF-8")
			+ "&response_type=code"
			+ "&scope=talk_message";  
			
            // 브라우저 열기
            Desktop.getDesktop().browse(new java.net.URI(authUrl));
			
            // 로컬 HTTP 서버로 인가 코드 수신
            new Thread(() -> {
                try {
                    java.net.ServerSocket ss = new java.net.ServerSocket(8080);
                    ss.setSoTimeout(120000); // 2분 타임아웃
                    java.net.Socket sock = ss.accept();
                    java.io.BufferedReader br = new java.io.BufferedReader(
					new java.io.InputStreamReader(sock.getInputStream()));
                    String line = br.readLine(); // GET /callback?code=xxx HTTP/1.1
                    sock.getOutputStream().write(
                        ("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n" +
							"<html><body><h2>&#9989; \ucee4\uce74\uc624 \ub85c\uadf8\uc778 \uc131\uacf5!</h2>" +
						"<p>\uc774 \ucc3d\uc744 \ub2eb\uc73c\uc138\uc694.</p></body></html>")
					.getBytes("UTF-8"));
                    sock.close(); ss.close();
					
                    // 코드 파싱
                    String code = "";
                    if (line != null && line.contains("code=")) {
                        code = line.split("code=")[1].split("[ &]")[0];
					}
                    if (code.isEmpty()) return;
					
                    // 토큰 교환
                    kakaoExchangeToken(code);
					} catch (Exception e) {
                    System.out.println("[Kakao] 로그인 서버 오류: " + e.getMessage());
				}
			}, "KakaoLogin").start();
			
			} catch (Exception e) {
            prepareMessageBox();
            JOptionPane.showMessageDialog(null,
			"브라우저 열기 실패: " + e.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
		}
	}
	
    /** 인가 코드 → 액세스 토큰 교환 */
    private void kakaoExchangeToken(String code) {
		
        try {
            URL url = toUrl("https://kauth.kakao.com/oauth/token");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
			
            String body = "grant_type=authorization_code"
			+ "&client_id=" + kakaoRestApiKey
			+ "&client_secret=" + kakaoClientSecret  
			+ "&redirect_uri=" + java.net.URLEncoder.encode(KAKAO_REDIRECT, "UTF-8")
			+ "&code=" + code;
            con.getOutputStream().write(body.getBytes("UTF-8"));
			
            java.io.BufferedReader br = new java.io.BufferedReader(
			new java.io.InputStreamReader(con.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            con.disconnect();
			
            String json = sb.toString();
            kakaoAccessToken  = extractJson(json, "access_token");
            kakaoRefreshToken = extractJson(json, "refresh_token");
            // 보안: 토큰은 메모리에만 저장, 파일 저장 안함
            // 로그인 성공 팝업은 브라우저에서 이미 표시되므로 생략
            System.out.println("[Kakao] 로그인 성공");
			} catch (Exception e) {
			SwingUtilities.invokeLater(() -> {
				prepareMessageBox();
				JOptionPane.showMessageDialog(null,
				"토큰 교환 실패: " + e.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
			});
		}
	}
	
    /** 액세스 토큰 갱신 */
    private void kakaoRefreshAccessToken() {
        if (kakaoRefreshToken.isEmpty()) return;
        try {
            URL url = toUrl("https://kauth.kakao.com/oauth/token");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
            String body = "grant_type=refresh_token"
			+ "&client_id=" + kakaoRestApiKey
			+ "&client_secret=" + kakaoClientSecret  
			+ "&refresh_token=" + kakaoRefreshToken;
            con.getOutputStream().write(body.getBytes("UTF-8"));
            java.io.BufferedReader br = new java.io.BufferedReader(
			new java.io.InputStreamReader(con.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            con.disconnect();
            String json = sb.toString();
            String newAccess = extractJson(json, "access_token");
            if (!newAccess.isEmpty()) kakaoAccessToken = newAccess;
            String newRefresh = extractJson(json, "refresh_token");
            if (!newRefresh.isEmpty()) kakaoRefreshToken = newRefresh;
            // 보안: 토큰은 메모리에만 저장
			} catch (Exception e) {
            System.out.println("[Kakao] 토큰 갱신 실패: " + e.getMessage());
		}
	}
	
    /** 카카오톡 나에게 보내기 */
	void sendKakao(String title, String msg) {
        sendKakao(title, msg, true); // 최초 호출: 재시도 허용
	}
	
	void sendKakao(String title, String msg, boolean retry) {
        if (kakaoAccessToken.isEmpty()) {
            System.out.println("[Kakao] 액세스 토큰 없음 - 로그인 필요");
            return;
		}
        try {
            URL url = toUrl("https://kapi.kakao.com/v2/api/talk/memo/default/send");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setRequestProperty("Authorization", "Bearer " + kakaoAccessToken);
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=utf-8");
			
            StringBuilder tb = new StringBuilder();
            tb.append("{");
            tb.append("\"object_type\":\"text\",");
            tb.append("\"text\":\"\u23f0 ");
            tb.append(title.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"));
            tb.append("\\n\\n");
            tb.append(msg.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"));
            tb.append("\",");
            tb.append("\"link\":{\"web_url\":\"https://kakao.com\"}");
            tb.append("}");
            String template = tb.toString();
            String body = "template_object=" + java.net.URLEncoder.encode(template, "UTF-8");
            con.getOutputStream().write(body.getBytes("UTF-8"));
			
            int code = con.getResponseCode();
            if (code == 401 && retry) {
                // 토큰 만료 → 갱신 후 1회만 재시도
                con.disconnect();
                kakaoRefreshAccessToken();
                sendKakao(title, msg, false); // retry=false 로 추가 재귀 차단
                return;
			}
            con.disconnect();
            System.out.println("[Kakao] 발송 완료 code=" + code);
			} catch (Exception e) {
            System.out.println("[Kakao] 발송 오류: " + e.getMessage());
		}
	}
	
	
    /** JOptionPane 표시 전 공통 처리 */
    private void prepareMessageBox() {
        // JOptionPane은 null parent 사용 → 자동으로 화면 중앙에 표시됨
    }
	
	private static java.net.URL toUrl(String s) {
		try { 
			@SuppressWarnings("deprecation")
			java.net.URL url = new java.net.URL(s);
			return url;
		}
		catch (Exception e) { throw new RuntimeException(e); }
	}
	
    /**
		* JSON 문자열에서 키 값 추출 (정규식 파서)
		* - 문자열값("...") 및 숫자/boolean 값 모두 처리
		* - 콜론 앞뒤 공백 허용
		* - 이스케이프 문자(\") 포함된 문자열값도 정확히 추출
	*/
    private String extractJson(String json, String key) {
        // 문자열값: "key" : "value"  (이스케이프 포함)
        Pattern strPat = Pattern.compile(
		"\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher strMat = strPat.matcher(json);
        if (strMat.find()) return strMat.group(1);
		
        // 숫자/boolean값: "key" : 12345  또는  "key" : true
        Pattern numPat = Pattern.compile(
		"\"" + Pattern.quote(key) + "\"\\s*:\\s*([^,}\\s]+)");
        Matcher numMat = numPat.matcher(json);
        if (numMat.find()) return numMat.group(1);
		
        return "";
	}
}