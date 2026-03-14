import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

/**
 * GoogleCalendarService - Google Calendar API 연동 클래스 (순수 Java 구현)
 *
 * ── 외부 JAR 의존성 완전 제거 ─────────────────────────────────────
 *   google-api-client, google-http-client, opencensus 등 불필요.
 *   GmailSender / Kakao 와 동일한 순수 Java HTTP 방식.
 *
 * ── OAuth 2.0 흐름 ────────────────────────────────────────────────
 *   1) credentials.json 에서 client_id / client_secret 읽기
 *   2) 브라우저로 Google 인증 페이지 열기
 *   3) 로컬 HTTP 서버(포트 8888)로 authorization code 수신
 *   4) code → access_token / refresh_token 교환 후 tokens/token.json 저장
 *   5) 재실행 시 token.json 로드 → 만료 시 자동 갱신
 *
 * ── credentials.json 위치 ─────────────────────────────────────────
 *   실행 JAR / EXE 와 같은 폴더
 *
 * ── token 저장 위치 ───────────────────────────────────────────────
 *   실행 폴더/tokens/token.json
 */
public class GoogleCalendarService {

    // ── 상수 ──────────────────────────────────────────────────────
    private static final String CREDENTIALS_FILE = "credentials.json";
    private static final String TOKEN_FILE        = "tokens/token.json";
    private static final String REDIRECT_URI      = "http://localhost:8888/Callback";
    private static final String SCOPE             = "https://www.googleapis.com/auth/calendar.readonly";
    private static final String TOKEN_URL         = "https://oauth2.googleapis.com/token";
    private static final String CALENDAR_API      = "https://www.googleapis.com/calendar/v3";

    // ── OAuth 토큰 상태 ────────────────────────────────────────────
    private String clientId     = "";
    private String clientSecret = "";
    private String accessToken  = "";
    private String refreshToken = "";
    private long   expiresAt    = 0;   // System.currentTimeMillis() 기준

    private boolean initialized = false;

    // ── 초기화 ────────────────────────────────────────────────────

    /**
     * Google Calendar 서비스 초기화.
     * 저장된 token.json 이 있으면 로드, 없으면 브라우저 인증 진행.
     * @return 초기화 성공 여부
     */
    public boolean init() {
        try {
            // credentials.json 로드
            File credFile = resolveFile(CREDENTIALS_FILE);
            if (!credFile.exists()) {
                System.out.println("[GCalendar] credentials.json 없음: " + credFile.getAbsolutePath());
                return false;
            }
            loadCredentials(credFile);

            // 기존 token.json 로드 시도
            File tokenFile = resolveFile(TOKEN_FILE);
            if (tokenFile.exists()) {
                loadToken(tokenFile);
                System.out.println("[GCalendar] 기존 토큰 로드 완료");
            }

            // 토큰이 없거나 refresh_token 도 없으면 → 브라우저 인증
            if (accessToken.isEmpty() && refreshToken.isEmpty()) {
                if (!doBrowserAuth()) return false;
            }

            // access_token 만료 시 갱신
            ensureValidToken();

            initialized = true;
            System.out.println("[GCalendar] 초기화 완료");
            return true;

        } catch (Exception e) {
            System.out.println("[GCalendar] 초기화 실패: " + e.getMessage());
            return false;
        }
    }

    public boolean isInitialized() { return initialized; }

    /**
     * 로그아웃 - 메모리 토큰 초기화 + token.json 삭제.
     * 다음 init() 호출 시 브라우저 인증부터 다시 시작.
     * @return 삭제된 token.json 경로 (파일이 없었으면 null)
     */
    public String logout() {
        accessToken  = "";
        refreshToken = "";
        expiresAt    = 0;
        initialized  = false;

        File tokenFile = resolveFile(TOKEN_FILE);
        if (tokenFile.exists()) {
            String path = tokenFile.getAbsolutePath();
            tokenFile.delete();
            System.out.println("[GCalendar] 로그아웃 완료 - 토큰 삭제: " + path);
            return path;
        }
        System.out.println("[GCalendar] 로그아웃 완료 - 토큰 파일 없음");
        return null;
    }

    // ── 공개 API: 일정 조회 ───────────────────────────────────────

    /** 오늘 일정 조회 */
    public List<CalendarEvent> getToday() {
        ZonedDateTime start = LocalDate.now().atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime end   = start.plusDays(1).minusSeconds(1);
        return getEvents(start, end);
    }

    /** 향후 N일 일정 조회 (오늘 포함) */
    public List<CalendarEvent> getNextDays(int days) {
        ZonedDateTime start = LocalDate.now().atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime end   = start.plusDays(days);
        return getEvents(start, end);
    }

    /** 이번 주 일정 조회 (월요일~일요일) */
    public List<CalendarEvent> getThisWeek() {
        LocalDate today  = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LocalDate sunday = today.with(DayOfWeek.SUNDAY);
        ZonedDateTime start = monday.atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime end   = sunday.atTime(23, 59, 59).atZone(ZoneId.systemDefault());
        return getEvents(start, end);
    }

    /** 이번 달 일정 조회 */
    public List<CalendarEvent> getThisMonth() {
        LocalDate today    = LocalDate.now();
        LocalDate firstDay = today.withDayOfMonth(1);
        LocalDate lastDay  = today.withDayOfMonth(today.lengthOfMonth());
        ZonedDateTime start = firstDay.atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime end   = lastDay.atTime(23, 59, 59).atZone(ZoneId.systemDefault());
        return getEvents(start, end);
    }

    /** 향후 N분 이내 시작 이벤트 조회 (알람 폴링용) */
    public List<CalendarEvent> getUpcomingAlarms(int withinMinutes) {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime end = now.plusMinutes(withinMinutes);
        return getEvents(now.minusSeconds(30), end);
    }

    // ── 내부: 이벤트 조회 ─────────────────────────────────────────

    private List<CalendarEvent> getEvents(ZonedDateTime start, ZonedDateTime end) {
        List<CalendarEvent> result = new ArrayList<>();
        if (!initialized) {
            System.out.println("[GCalendar] 초기화되지 않음");
            return result;
        }
        try {
            ensureValidToken();

            // RFC3339 형식으로 변환
            DateTimeFormatter rfc3339 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
            String timeMin = URLEncoder.encode(start.format(rfc3339), "UTF-8");
            String timeMax = URLEncoder.encode(end.format(rfc3339),   "UTF-8");

            // ── 모든 캘린더 ID 목록 조회 (국경일/기념일 포함) ────────
            List<String> calendarIds = getCalendarIds();
            if (calendarIds.isEmpty()) calendarIds.add("primary"); // fallback

            // ── 각 캘린더별 이벤트 조회 후 합산 ──────────────────────
            Set<String> seenIds = new HashSet<>(); // 중복 이벤트 방지
            for (String calId : calendarIds) {
                try {
                    String url = CALENDAR_API + "/calendars/"
                            + URLEncoder.encode(calId, "UTF-8") + "/events"
                            + "?maxResults=50"
                            + "&singleEvents=true"
                            + "&orderBy=startTime"
                            + "&timeMin=" + timeMin
                            + "&timeMax=" + timeMax;

                    String json = httpGet(url);
                    List<CalendarEvent> events = parseEvents(json);
                    for (CalendarEvent ev : events) {
                        if (ev.id != null && seenIds.add(ev.id)) {
                            result.add(ev);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[GCalendar] 캘린더 조회 오류 (" + calId + "): " + e.getMessage());
                }
            }

            // 시작 시간 순 정렬
            result.sort((a, b) -> a.startTime.compareTo(b.startTime));

        } catch (Exception e) {
            System.out.println("[GCalendar] 이벤트 조회 오류: " + e.getMessage());
        }
        return result;
    }

    /**
     * 내 계정에 등록된 모든 캘린더 ID 목록 조회.
     * 국경일(ko.south_korea#holiday@group.v.calendar.google.com 등),
     * 기념일, 공유 캘린더 모두 포함.
     */
    private List<String> getCalendarIds() {
        List<String> ids = new ArrayList<>();
        try {
            String url = CALENDAR_API + "/users/me/calendarList?maxResults=100";
            String json = httpGet(url);

            // calendarList 응답의 items 배열에서 각 캘린더 id 추출
            String itemsStr = extractBetween(json, "\"items\":[", null);
            if (itemsStr == null) return ids;
            List<String> objects = splitJsonObjects(itemsStr);
            for (String obj : objects) {
                String calId = extractJsonStr(obj, "id");
                if (calId != null && !calId.isEmpty()) {
                    ids.add(calId);
                    System.out.println("[GCalendar] 캘린더: " + calId);
                }
            }
        } catch (Exception e) {
            System.out.println("[GCalendar] 캘린더 목록 조회 오류: " + e.getMessage());
        }
        return ids;
    }

    // ── 내부: JSON 파싱 ───────────────────────────────────────────

    private List<CalendarEvent> parseEvents(String json) {
        List<CalendarEvent> list = new ArrayList<>();
        // "items" 배열 추출
        String items = extractBetween(json, "\"items\":[", null);
        if (items == null) return list;

        // 각 이벤트 객체 분리 (중첩 브레이스 카운팅)
        List<String> objects = splitJsonObjects(items);
        for (String obj : objects) {
            try {
                CalendarEvent ev = parseOneEvent(obj);
                if (ev != null) list.add(ev);
            } catch (Exception e) {
                System.out.println("[GCalendar] 이벤트 파싱 오류: " + e.getMessage());
            }
        }
        return list;
    }

    private CalendarEvent parseOneEvent(String obj) {
        String id    = extractJsonStr(obj, "id");
        String title = extractJsonStr(obj, "summary");
        if (title == null || title.isEmpty()) title = "(제목 없음)";

        // start 블록
        String startBlock = extractBlock(obj, "\"start\"");
        String endBlock   = extractBlock(obj, "\"end\"");
        if (startBlock == null || endBlock == null) return null;

        boolean allDay = false;
        ZonedDateTime startTime, endTime;

        String startDt   = extractJsonStr(startBlock, "dateTime");
        String startDate = extractJsonStr(startBlock, "date");

        if (startDt != null && !startDt.isEmpty()) {
            startTime = parseRfc3339(startDt);
        } else if (startDate != null && !startDate.isEmpty()) {
            startTime = LocalDate.parse(startDate).atStartOfDay(ZoneId.systemDefault());
            allDay = true;
        } else return null;

        String endDt   = extractJsonStr(endBlock, "dateTime");
        String endDate = extractJsonStr(endBlock, "date");

        if (endDt != null && !endDt.isEmpty()) {
            endTime = parseRfc3339(endDt);
        } else if (endDate != null && !endDate.isEmpty()) {
            endTime = LocalDate.parse(endDate).atStartOfDay(ZoneId.systemDefault());
        } else {
            endTime = startTime;
        }

        return new CalendarEvent(id, title, startTime, endTime, allDay);
    }

    // ── 내부: OAuth 브라우저 인증 ─────────────────────────────────

    private boolean doBrowserAuth() throws Exception {
        String authUrl = "https://accounts.google.com/o/oauth2/auth"
                + "?client_id="     + URLEncoder.encode(clientId, "UTF-8")
                + "&redirect_uri="  + URLEncoder.encode(REDIRECT_URI, "UTF-8")
                + "&response_type=code"
                + "&access_type=offline"
                + "&scope="         + URLEncoder.encode(SCOPE, "UTF-8");

        System.out.println("[GCalendar] 브라우저 인증 시작...");
        System.out.println("[GCalendar] URL: " + authUrl);

        try { Desktop.getDesktop().browse(new URI(authUrl)); }
        catch (Exception e) { System.out.println("[GCalendar] 브라우저 열기 실패: " + e.getMessage()); }

        // 로컬 HTTP 서버로 code 수신
        String code = receiveAuthCode(8888, 120000);
        if (code == null || code.isEmpty()) {
            System.out.println("[GCalendar] 인증 코드 수신 실패");
            return false;
        }

        // code → token 교환
        exchangeToken(code);
        return !accessToken.isEmpty();
    }

    /** 포트 8888 에서 authorization code 수신 */
    private String receiveAuthCode(int port, int timeoutMs) {
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setSoTimeout(timeoutMs);
            Socket sock = ss.accept();
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(sock.getInputStream(), "UTF-8"));
            String line = br.readLine(); // GET /Callback?code=xxx HTTP/1.1

            // 성공 응답 전송
            String resp = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n"
                    + "<html><body><h2>✅ Google 로그인 성공!</h2>"
                    + "<p>이 창을 닫으세요.</p></body></html>";
            sock.getOutputStream().write(resp.getBytes("UTF-8"));
            sock.close();

            if (line == null) return null;
            // code= 파싱
            Pattern p = Pattern.compile("[?&]code=([^& ]+)");
            Matcher m = p.matcher(line);
            return m.find() ? m.group(1) : null;
        } catch (Exception e) {
            System.out.println("[GCalendar] 인증 코드 수신 오류: " + e.getMessage());
            return null;
        }
    }

    /** authorization code → access_token / refresh_token */
    private void exchangeToken(String code) throws Exception {
        String body = "grant_type=authorization_code"
                + "&client_id="     + URLEncoder.encode(clientId,     "UTF-8")
                + "&client_secret=" + URLEncoder.encode(clientSecret, "UTF-8")
                + "&redirect_uri="  + URLEncoder.encode(REDIRECT_URI, "UTF-8")
                + "&code="          + URLEncoder.encode(code,         "UTF-8");

        String json = httpPost(TOKEN_URL, body);
        parseAndStoreToken(json);
        saveToken();
        System.out.println("[GCalendar] 토큰 교환 완료");
    }

    /** access_token 만료 시 refresh_token 으로 갱신 */
    private void ensureValidToken() throws Exception {
        if (accessToken.isEmpty() && !refreshToken.isEmpty()) {
            refreshAccessToken();
            return;
        }
        // 만료 1분 전부터 갱신
        if (!refreshToken.isEmpty() && System.currentTimeMillis() > expiresAt - 60_000) {
            refreshAccessToken();
        }
    }

    private void refreshAccessToken() throws Exception {
        if (refreshToken.isEmpty()) return;
        String body = "grant_type=refresh_token"
                + "&client_id="     + URLEncoder.encode(clientId,     "UTF-8")
                + "&client_secret=" + URLEncoder.encode(clientSecret, "UTF-8")
                + "&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8");

        String json = httpPost(TOKEN_URL, body);
        parseAndStoreToken(json);
        saveToken();
        System.out.println("[GCalendar] 토큰 갱신 완료");
    }

    // ── 내부: credentials.json 로드 ──────────────────────────────

    private void loadCredentials(File f) throws Exception {
        String json = readFile(f);
        // "installed" 또는 "web" 블록 안에서 client_id / client_secret 추출
        String block = extractBlock(json, "\"installed\"");
        if (block == null) block = extractBlock(json, "\"web\"");
        if (block == null) block = json;

        clientId     = extractJsonStr(block, "client_id");
        clientSecret = extractJsonStr(block, "client_secret");

        if (clientId == null || clientId.isEmpty())
            throw new Exception("credentials.json 에서 client_id 를 찾을 수 없습니다.");
    }

    // ── 내부: token.json 저장 / 로드 ─────────────────────────────

    private void parseAndStoreToken(String json) {
        String at = extractJsonStr(json, "access_token");
        String rt = extractJsonStr(json, "refresh_token");
        String ei = extractJsonStr(json, "expires_in");

        if (at != null && !at.isEmpty()) accessToken = at;
        if (rt != null && !rt.isEmpty()) refreshToken = rt;
        if (ei != null && !ei.isEmpty()) {
            try { expiresAt = System.currentTimeMillis() + Long.parseLong(ei) * 1000; }
            catch (NumberFormatException ignored) {}
        }
    }

    private void saveToken() {
        try {
            File tokenFile = resolveFile(TOKEN_FILE);
            tokenFile.getParentFile().mkdirs();
            String json = "{\n"
                    + "  \"access_token\": \""  + accessToken  + "\",\n"
                    + "  \"refresh_token\": \"" + refreshToken + "\",\n"
                    + "  \"expires_at\": "      + expiresAt    + "\n"
                    + "}\n";
            try (PrintWriter pw = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream(tokenFile), "UTF-8"))) {
                pw.print(json);
            }
        } catch (Exception e) {
            System.out.println("[GCalendar] 토큰 저장 실패: " + e.getMessage());
        }
    }

    private void loadToken(File f) {
        try {
            String json = readFile(f);
            String at = extractJsonStr(json, "access_token");
            String rt = extractJsonStr(json, "refresh_token");
            String ea = extractJsonStr(json, "expires_at");

            if (at != null) accessToken  = at;
            if (rt != null) refreshToken = rt;
            if (ea != null) {
                try { expiresAt = Long.parseLong(ea); }
                catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            System.out.println("[GCalendar] 토큰 로드 실패: " + e.getMessage());
        }
    }

    // ── 내부: HTTP 유틸 ───────────────────────────────────────────

    private String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("Authorization", "Bearer " + accessToken);
        con.setConnectTimeout(10000);
        con.setReadTimeout(15000);
        String result = readStream(con);
        con.disconnect();
        return result;
    }

    private String httpPost(String urlStr, String body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        con.setConnectTimeout(10000);
        con.setReadTimeout(15000);
        con.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        String result = readStream(con);
        con.disconnect();
        return result;
    }

    private String readStream(HttpURLConnection con) throws Exception {
        InputStream is;
        try { is = con.getInputStream(); }
        catch (IOException e) {
            is = con.getErrorStream();
            if (is == null) throw e;
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append("\n");
        br.close();
        return sb.toString();
    }

    // ── 내부: JSON 파싱 유틸 ──────────────────────────────────────

    /** "key": "value" 추출 */
    private static String extractJsonStr(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** { } 블록 추출 (key: { ... }) */
    private static String extractBlock(String json, String key) {
        int ki = json.indexOf(key);
        if (ki < 0) return null;
        int start = json.indexOf('{', ki + key.length());
        if (start < 0) return null;
        int depth = 0, i = start;
        while (i < json.length()) {
            char c = json.charAt(i);
            if      (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return json.substring(start, i + 1); }
            i++;
        }
        return null;
    }

    /** "items":[ ... ] 에서 [ 뒤 내용 추출 */
    private static String extractBetween(String json, String startToken, String endToken) {
        int si = json.indexOf(startToken);
        if (si < 0) return null;
        return json.substring(si + startToken.length());
    }

    /** [ { ... }, { ... } ] 형태에서 최상위 {} 객체들 분리 */
    private static List<String> splitJsonObjects(String arr) {
        List<String> list = new ArrayList<>();
        int depth = 0, start = -1;
        for (int i = 0; i < arr.length(); i++) {
            char c = arr.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    list.add(arr.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return list;
    }

    /** RFC3339 → ZonedDateTime (예: 2026-03-14T10:00:00+09:00) */
    private static ZonedDateTime parseRfc3339(String s) {
        try {
            return ZonedDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            return ZonedDateTime.now();
        }
    }

    // ── 파일 유틸 ─────────────────────────────────────────────────

    private static String readFile(File f) throws Exception {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append("\n");
        br.close();
        return sb.toString();
    }

    /** 실행 파일 폴더 기준으로 파일 경로 해석 */
    private static File resolveFile(String name) {
        try {
            String sc = System.getProperty("sun.java.command", "").trim();
            String first = sc.split("\\s+")[0];
            if (first.endsWith(".jar") || first.endsWith(".exe")) {
                File f = new File(first).getAbsoluteFile();
                if (f.getParentFile() != null) return new File(f.getParentFile(), name);
            }
        } catch (Exception ignored) {}
        try {
            File f = new File(GoogleCalendarService.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsoluteFile();
            File dir = f.isDirectory() ? f : f.getParentFile();
            if (dir != null) return new File(dir, name);
        } catch (Exception ignored) {}
        return new File(System.getProperty("user.dir"), name);
    }

    // ── 텔레그램 메시지 포맷 (기존 동일) ────────────────────────

    public static String formatEvents(String title, List<CalendarEvent> events) {
        StringBuilder sb = new StringBuilder();
        sb.append("📅 ").append(title).append("\n");
        sb.append("─────────────────\n");

        if (events.isEmpty()) {
            sb.append("일정이 없습니다.\n");
        } else {
            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("M/d(E)", Locale.KOREAN);
            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
            String lastDate = "";
            for (CalendarEvent e : events) {
                String dateStr = e.startTime.format(dateFmt);
                if (!dateStr.equals(lastDate)) {
                    if (!lastDate.isEmpty()) sb.append("\n");
                    sb.append("📆 ").append(dateStr).append("\n");
                    lastDate = dateStr;
                }
                if (e.allDay) {
                    sb.append("  • ").append(e.title).append(" (종일)\n");
                } else {
                    sb.append("  ⏰ ").append(e.startTime.format(timeFmt))
                      .append(" ").append(e.title).append("\n");
                }
            }
        }
        sb.append("─────────────────\n");
        sb.append("총 ").append(events.size()).append("개 일정");
        return sb.toString();
    }

    // ── CalendarEvent DTO (기존 동일) ─────────────────────────────

    public static class CalendarEvent {
        public final String        id;
        public final String        title;
        public final ZonedDateTime startTime;
        public final ZonedDateTime endTime;
        public final boolean       allDay;

        public CalendarEvent(String id, String title,
                             ZonedDateTime startTime, ZonedDateTime endTime, boolean allDay) {
            this.id        = id;
            this.title     = title;
            this.startTime = startTime;
            this.endTime   = endTime;
            this.allDay    = allDay;
        }
    }
}
