import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/**
 * ItsCctvManager - ITS 국가교통정보센터 교통 CCTV 연동
 *
 * ── API ──────────────────────────────────────────────────────
 *   엔드포인트 : https://openapi.its.go.kr:9443/cctvInfo
 *   파라미터   : apiKey, type(its=국도/ex=고속도로), cctvType(1=HLS/2=동영상/3=정지영상),
 *               minX/maxX/minY/maxY (경도/위도), getType=json
 *   응답 JSON  : response.data[].{ cctvname, cctvurl, coordx, coordy }
 *    [ 5bfd056aa2be4e7fa5a0eb6c76894928 ]
 * ── 동작 방식 ─────────────────────────────────────────────────
 *   cctvType=1(HLS) 로 cctvurl 목록을 받아온 뒤,
 *   정지영상 URL 로 변환(cctvurl + "/images" 등)하지 않고,
 *   별도 정지영상 API(cctvType=3) 로 JPEG URL 을 확보하여
 *   REFRESH_SEC 초마다 이미지를 fetch → HostCallback.setBgImage() 호출.
 *
 * ── 순수 Java ────────────────────────────────────────────────
 *   외부 라이브러리 불필요. ImageIO + HttpURLConnection 만 사용.
 */
public class ItsCctvManager {

    // ── 공개 상수 ────────────────────────────────────────────
    public static final String BASE_URL =
        "https://openapi.its.go.kr:9443/cctvInfo";

    /** 서울 근교 국도 좌표 범위 (강변북로·올림픽대로 포함) */
    // private static final double MIN_X = 126.75, MAX_X = 127.20;
    // private static final double MIN_Y = 37.42,  MAX_Y = 37.70;

	private static final double MIN_X = 124.0, MAX_X = 132.0;   // 경도 (전국)
	private static final double MIN_Y = 33.0,  MAX_Y = 39.0;    // 위도 (전국)

    private static final int REFRESH_SEC = 5;   // 정지영상 갱신 주기(초)
    private static final int CONNECT_TIMEOUT = 8_000;
    private static final int READ_TIMEOUT    = 8_000;

    // ── CCTV 항목 ────────────────────────────────────────────
    public static class CctvItem {
        public final String name;
        public final String url;   // 정지영상 JPEG URL
        public final double x, y;
        public CctvItem(String name, String url, double x, double y) {
            this.name = name; this.url = url; this.x = x; this.y = y;
        }
        @Override public String toString() { return name; }
    }

    // ── 호스트 콜백 ──────────────────────────────────────────
    public interface HostCallback {
        /** 갱신된 JPEG 이미지를 배경으로 설정 */
        void setBgImage(String path, BufferedImage img);
        /** repaint */
        void repaintClock();
    }

    // ── 필드 ─────────────────────────────────────────────────
    private String       apiKey        = "";
    private List<CctvItem> items       = new ArrayList<>();
    private List<CctvItem> activeItems = new ArrayList<>(); // 필터 적용 목록 (없으면 전체)
    private int          current       = 0;
    private boolean      running       = false;
    private javax.swing.Timer timer    = null;
    private final HostCallback host;

    // ── 생성자 ───────────────────────────────────────────────
    public ItsCctvManager(HostCallback host) {
        this.host = host;
    }

    // ── 설정 접근자 ──────────────────────────────────────────
    public String  getApiKey() { return apiKey; }
    public void    setApiKey(String k) { this.apiKey = k != null ? k.trim() : ""; }
    public boolean isRunning() { return running; }
    public List<CctvItem> getItems() { return Collections.unmodifiableList(items); }
    public int    getCurrentIndex() { return current; }

    // ── 공개 API ─────────────────────────────────────────────

    /**
     * API 로 CCTV 목록 조회 (백그라운드 스레드).
     * 완료 시 onDone(성공여부, 에러메시지) 콜백.
     */
    public void fetchList(Runnable onSuccess, java.util.function.Consumer<String> onError) {
        new Thread(() -> {
            try {
                List<CctvItem> result = callApi();
                SwingUtilities.invokeLater(() -> {
                    items       = result;
                    activeItems = new ArrayList<>(result); // 기본: 전체
                    current     = 0;
                    onSuccess.run();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> onError.accept(e.getMessage()));
            }
        }, "ItsCctv-Fetch").start();
    }

    /**
     * 필터링된 목록(activeItems) 기준으로 인덱스 지정.
     * MenuBuilder 의 showCctvSelectDialog 에서 선택한 아이템을
     * items(전체) 인덱스로 받아 activeItems 내 위치로 매핑한다.
     */
    public void select(int globalIndex) {
        if (items.isEmpty()) return;
        // globalIndex → activeItems 내 위치 탐색
        CctvItem target = items.get(Math.max(0, Math.min(globalIndex, items.size() - 1)));
        int ai = activeItems.indexOf(target);
        if (ai < 0) {
            // 필터에 없는 항목 선택 시 activeItems 를 전체로 리셋 후 진행
            activeItems = new ArrayList<>(items);
            ai = activeItems.indexOf(target);
        }
        current = ai < 0 ? 0 : ai;
        fetchAndDisplay(activeItems.get(current));
        if (running) {
            stopTimer();
            timer = new javax.swing.Timer(REFRESH_SEC * 1000, e -> refreshNow());
            timer.start();
        }
    }

    /** 다음 CCTV (activeItems 내 순환) */
    public void next() {
        if (activeItems.isEmpty()) return;
        current = (current + 1) % activeItems.size();
        fetchAndDisplay(activeItems.get(current));
        if (running) { stopTimer(); timer = new javax.swing.Timer(REFRESH_SEC * 1000, e -> refreshNow()); timer.start(); }
    }

    /** 이전 CCTV (activeItems 내 순환) */
    public void prev() {
        if (activeItems.isEmpty()) return;
        current = (current - 1 + activeItems.size()) % activeItems.size();
        fetchAndDisplay(activeItems.get(current));
        if (running) { stopTimer(); timer = new javax.swing.Timer(REFRESH_SEC * 1000, e -> refreshNow()); timer.start(); }
    }

    /**
     * 필터링된 목록 설정 (MenuBuilder 의 필터 버튼 클릭 시 호출).
     * keyword 가 비어 있으면 전체 목록으로 복원.
     */
    public void setFilter(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            activeItems = new ArrayList<>(items);
        } else {
            activeItems = new ArrayList<>();
            for (CctvItem it : items) {
                if (it.name.contains(keyword.trim())) activeItems.add(it);
            }
            if (activeItems.isEmpty()) activeItems = new ArrayList<>(items); // 결과 없으면 전체
        }
        current = 0;
        // System.out.println("[ItsCctv] 필터 적용: "" + keyword + "" → " + activeItems.size() + "개");
        System.out.println("[ItsCctv] 필터: " + keyword + " -> " + activeItems.size() + "개");
}

    /** 갱신 타이머 시작 */
    public void start() {
        if (activeItems.isEmpty() && !items.isEmpty()) activeItems = new ArrayList<>(items);
        if (activeItems.isEmpty()) return;
        running = true;
        stopTimer();
        refreshNow();  // 즉시 1회
        timer = new javax.swing.Timer(REFRESH_SEC * 1000, e -> refreshNow());
        timer.start();
        System.out.println("[ItsCctv] 시작: " + currentName());
    }

    /** 갱신 타이머 중지 */
    public void stop() {
        running = false;
        stopTimer();
        System.out.println("[ItsCctv] 중지");
    }

    public String currentName() {
        if (activeItems.isEmpty()) return "";
        return activeItems.get(current).name;
    }

    // ── 내부: API 호출 ───────────────────────────────────────

    private List<CctvItem> callApi() throws Exception {
        if (apiKey.isEmpty()) throw new Exception("API 키가 입력되지 않았습니다.");

        // cctvType=3 (정지영상 JPEG URL)
        // type=its (국도) — 서울 시내 포함
        String urlStr = BASE_URL
            + "?apiKey=" + apiKey
            + "&type=its"
            + "&cctvType=3"
            + "&minX=" + MIN_X + "&maxX=" + MAX_X
            + "&minY=" + MIN_Y + "&maxY=" + MAX_Y
            + "&getType=json";

        String json = httpGet(urlStr);
        return parseJson(json);
    }

    /** JSON 파싱 (외부 라이브러리 없이 정규식/substring) */
    private List<CctvItem> parseJson(String json) throws Exception {
        List<CctvItem> list = new ArrayList<>();

        // "data" 배열 추출
        int dataIdx = json.indexOf("\"data\"");
        if (dataIdx < 0) {
            // 빈 결과 또는 오류 응답
            String msg = extractField(json, "message");
            if (msg.isEmpty()) msg = "데이터 없음 (범위 내 CCTV 없음)";
            throw new Exception(msg);
        }

        int arrStart = json.indexOf('[', dataIdx);
        int arrEnd   = findMatchingBracket(json, arrStart);
        if (arrStart < 0 || arrEnd < 0) throw new Exception("JSON 파싱 오류");

        String arr = json.substring(arrStart + 1, arrEnd);
        // 각 객체 { ... } 분리
        int pos = 0;
        while (pos < arr.length()) {
            int ob = arr.indexOf('{', pos);
            if (ob < 0) break;
            int oe = findMatchingBrace(arr, ob);
            if (oe < 0) break;
            String obj = arr.substring(ob + 1, oe);
            String name = extractField(obj, "cctvname");
            String url  = extractField(obj, "cctvurl");
            String sx   = extractField(obj, "coordx");
            String sy   = extractField(obj, "coordy");
            if (!name.isEmpty() && !url.isEmpty()) {
                double x = 0, y = 0;
                try { x = Double.parseDouble(sx); } catch (Exception ignored) {}
                try { y = Double.parseDouble(sy); } catch (Exception ignored) {}
                list.add(new CctvItem(name, url, x, y));
            }
            pos = oe + 1;
        }
        if (list.isEmpty()) throw new Exception("해당 범위에 정지영상 CCTV 없음");
        return list;
    }

    private String extractField(String obj, String key) {
        String pat = "\"" + key + "\"";
        int ki = obj.indexOf(pat);
        if (ki < 0) return "";
        int ci = obj.indexOf(':', ki + pat.length());
        if (ci < 0) return "";
        ci++;
        while (ci < obj.length() && obj.charAt(ci) == ' ') ci++;
        if (ci >= obj.length()) return "";
        if (obj.charAt(ci) == '"') {
            int end = obj.indexOf('"', ci + 1);
            return end < 0 ? "" : obj.substring(ci + 1, end);
        } else {
            int end = ci;
            while (end < obj.length() && obj.charAt(end) != ',' && obj.charAt(end) != '}') end++;
            return obj.substring(ci, end).trim();
        }
    }

    private int findMatchingBracket(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            if (s.charAt(i) == '[') depth++;
            else if (s.charAt(i) == ']') { if (--depth == 0) return i; }
        }
        return -1;
    }

    private int findMatchingBrace(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            if (s.charAt(i) == '{') depth++;
            else if (s.charAt(i) == '}') { if (--depth == 0) return i; }
        }
        return -1;
    }

    // ── 내부: 정지영상 fetch ─────────────────────────────────

    private void refreshNow() {
        if (!running || activeItems.isEmpty()) return;
        fetchAndDisplay(activeItems.get(current));
    }

    private void fetchAndDisplay(CctvItem item) {
        final String imgUrl = item.url;
        final String name   = item.name;
        new Thread(() -> {
            try {
                BufferedImage img = fetchImage(imgUrl);
                if (img != null) {
                    SwingUtilities.invokeLater(() -> {
                        host.setBgImage("[ITS CCTV] " + name, img);
                        host.repaintClock();
                    });
                }
            } catch (Exception e) {
                System.out.println("[ItsCctv] 이미지 갱신 실패: " + e.getMessage());
            }
        }, "ItsCctv-Refresh").start();
    }

    private BufferedImage fetchImage(String urlStr) throws Exception {
        @SuppressWarnings("deprecation")
        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setConnectTimeout(CONNECT_TIMEOUT);
        con.setReadTimeout(READ_TIMEOUT);
        con.setRequestProperty("User-Agent", "Mozilla/5.0");
        con.setRequestProperty("Accept", "image/jpeg,image/*");
        int code = con.getResponseCode();
        if (code != 200) throw new Exception("HTTP " + code);
        BufferedImage img = javax.imageio.ImageIO.read(con.getInputStream());
        con.disconnect();
        return img;
    }

    private String httpGet(String urlStr) throws Exception {
        @SuppressWarnings("deprecation")
        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setConnectTimeout(CONNECT_TIMEOUT);
        con.setReadTimeout(READ_TIMEOUT);
        con.setRequestProperty("Accept", "application/json");
        int code = con.getResponseCode();
        BufferedReader br = new BufferedReader(
            new InputStreamReader(
                code == 200 ? con.getInputStream() : con.getErrorStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        con.disconnect();
        return sb.toString();
    }

    private void stopTimer() {
        if (timer != null) { timer.stop(); timer = null; }
    }
}