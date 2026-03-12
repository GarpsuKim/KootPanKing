import java.util.UUID;

/**
 * AlarmEntry - 알람 항목 데이터 클래스
 *
 * AnalogClockSwing 에서 분리된 독립 파일.
 * 직렬화(Serializable) 지원 - alarms.dat 파일 저장/로드에 사용.
 */
public class AlarmEntry implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    String  id;             // UUID
    String  label;          // 메시지
    int     hour, minute;   // 시:분
    boolean[] days;         // [0]=일 [1]=월 ... [6]=토, 모두 false=매일
    boolean enabled;
    String  soundFile;      // "" = 기본차임벨
    boolean usePush;        // 스마트폰 push
    String  pushToken;      // Pushover user token  or  ntfy topic
    String  pushAppToken;   // Pushover app/api token
    String  pushService;    // "pushover" | "ntfy"
    boolean useEmail;
    String  emailTo;
    boolean useKakao;       // 카카오톡 나에게 보내기
    boolean useTelegram;    // 텔레그램 알림
    String  telegramChatId; // 텔레그램 Chat ID
    boolean fired;          // 이번 분에 이미 울렸는지

    AlarmEntry() {
        id = UUID.randomUUID().toString();
        label = "알람";
        hour = 7; minute = 0;
        days = new boolean[7];
        enabled = true;
        soundFile = "";
        usePush = false; pushToken = ""; pushAppToken = ""; pushService = "ntfy";
        useEmail = false; emailTo = "";
        useKakao = false;
        useTelegram = false; telegramChatId = "";
        fired = false;
    }

    // 오늘 요일(Calendar.SUNDAY=1~SATURDAY=7)에 울려야 하는가?
    boolean matchesDay(int calDow) {
        boolean allOff = true;
        for (boolean b : days) if (b) { allOff = false; break; }
        if (allOff) return true; // 매일
        // calDow: 1=일,2=월,...7=토 → days[0..6]
        return days[calDow - 1];
    }
}
