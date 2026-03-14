import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CalendarAlarmPoller - Google Calendar 이벤트 폴링 및 알림 트리거 클래스
 *
 * 기능:
 *   ① 1분 주기로 Google Calendar 이벤트 조회
 *   ② 이벤트 시작 시각 도달 시 알림 발동:
 *      - wmplayer 동영상 재생
 *      - 메시지 박스 팝업
 *      - 텔레그램 메시지 전송
 *   ③ 매일 아침 자동 브리핑 (오전 7시)
 *
 * 사용법:
 *   CalendarAlarmPoller poller = new CalendarAlarmPoller(calendarService, telegramBot, hostCallback);
 *   poller.start();   // 폴링 시작
 *   poller.stop();    // 폴링 중지
 */
public class CalendarAlarmPoller {

    // ── 알림 설정 ─────────────────────────────────────────────────
    /** 이벤트 시작 몇 분 전부터 감지할지 (기본 1분) */
    private static final int ALARM_WINDOW_MINUTES = 1;
    /** 동일 이벤트 중복 알림 방지 쿨다운 (분) */
    private static final int COOLDOWN_MINUTES     = 5;
    /** 아침 브리핑 시각 (시) */
    private static final int MORNING_BRIEF_HOUR   = 7;

    // ── 호스트 콜백 인터페이스 ────────────────────────────────────
    public interface HostCallback {
        /** wmplayer로 동영상 재생 (알람 동영상 파일 경로) */
        void playAlarmMedia();
        /** 알람 텔레그램 Chat ID 반환 */
        String getTelegramChatId();
        /** 메시지 박스 준비 (화면 중앙 조정 등) */
        void prepareMessageBox();
    }

    // ── 의존성 ────────────────────────────────────────────────────
    private final GoogleCalendarService calendarService;
    private final TelegramBot           telegramBot;
    private final HostCallback          host;

    // ── 내부 상태 ─────────────────────────────────────────────────
    private Timer  pollTimer         = null;
    private boolean running          = false;

    /** 이미 알림을 보낸 이벤트 ID + 알림 시각 (중복 방지) */
    private final Map<String, Long> firedAlarms = new HashMap<>();

    /** 아침 브리핑 마지막 발송 날짜 (중복 방지) */
    private int lastBriefDay = -1;

    // ── 생성자 ────────────────────────────────────────────────────
    public CalendarAlarmPoller(GoogleCalendarService calendarService,
                               TelegramBot telegramBot,
                               HostCallback host) {
        this.calendarService = calendarService;
        this.telegramBot     = telegramBot;
        this.host            = host;
    }

    // ── 시작 / 중지 ───────────────────────────────────────────────

    /** 폴링 시작 (EDT에서 호출) */
    public void start() {
        if (running) return;
        if (!calendarService.isInitialized()) {
            System.out.println("[CalPoller] GoogleCalendarService 초기화 안됨 - 시작 불가");
            return;
        }
        running = true;
        pollTimer = new Timer(60_000, e -> {
            new Thread(() -> {
                checkAlarms();
                checkMorningBrief();
            }, "CalendarPoller").start();
        });
        pollTimer.setInitialDelay(3000); // 앱 시작 후 3초 뒤 첫 폴링
        pollTimer.start();
        System.out.println("[CalPoller] 폴링 시작 (1분 간격)");
    }

    /** 폴링 중지 */
    public void stop() {
        if (pollTimer != null) {
            pollTimer.stop();
            pollTimer = null;
        }
        running = false;
        System.out.println("[CalPoller] 폴링 중지");
    }

    public boolean isRunning() { return running; }

    // ── 알람 체크 ─────────────────────────────────────────────────

    private void checkAlarms() {
        try {
            List<GoogleCalendarService.CalendarEvent> upcoming =
                    calendarService.getUpcomingAlarms(ALARM_WINDOW_MINUTES);

            ZonedDateTime now = ZonedDateTime.now();

            for (GoogleCalendarService.CalendarEvent event : upcoming) {
                if (event.allDay) continue; // 종일 이벤트는 알람 제외

                // 이미 알림을 보낸 이벤트인지 확인 (쿨다운 체크)
                Long lastFired = firedAlarms.get(event.id);
                if (lastFired != null) {
                    long minutesSince = (System.currentTimeMillis() - lastFired) / 60000;
                    if (minutesSince < COOLDOWN_MINUTES) continue;
                }

                // 이벤트 시작 시각이 현재 시각 ±ALARM_WINDOW_MINUTES 범위인지 확인
                long diffMinutes = java.time.Duration.between(now, event.startTime).toMinutes();
                if (diffMinutes >= 0 && diffMinutes <= ALARM_WINDOW_MINUTES) {
                    System.out.println("[CalPoller] 알람 트리거: " + event.title);
                    firedAlarms.put(event.id, System.currentTimeMillis());
                    fireAlarm(event);
                }
            }

            // 오래된 쿨다운 항목 정리
            cleanupFiredAlarms();

        } catch (Exception e) {
            System.out.println("[CalPoller] 알람 체크 오류: " + e.getMessage());
        }
    }

    /** 알람 발동: 동영상 + 메시지박스 + 텔레그램 */
    private void fireAlarm(GoogleCalendarService.CalendarEvent event) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        String title   = "📅 캘린더 알람";
        String content = "⏰ " + event.startTime.format(fmt) + "\n" + event.title;

        // ① wmplayer 동영상 재생
        if (host != null) {
            new Thread(() -> host.playAlarmMedia(), "CalAlarmMedia").start();
        }

        // ② 메시지 박스 팝업 (EDT에서 실행)
        SwingUtilities.invokeLater(() -> {
            if (host != null) host.prepareMessageBox();
            JOptionPane.showMessageDialog(
                    null,
                    content,
                    title,
                    JOptionPane.INFORMATION_MESSAGE);
        });

        // ③ 텔레그램 메시지 전송
        if (telegramBot != null) {
            String chatId = (host != null) ? host.getTelegramChatId() : "";
            if (!chatId.isEmpty()) {
                String msg = title + "\n─────────────────\n" + content;
                new Thread(() -> telegramBot.send(chatId, msg), "CalAlarmTelegram").start();
            }
        }

        System.out.println("[CalPoller] 알람 발동 완료: " + event.title);
    }

    // ── 아침 브리핑 ───────────────────────────────────────────────

    private void checkMorningBrief() {
        ZonedDateTime now = ZonedDateTime.now();
        int hour = now.getHour();
        int day  = now.getDayOfYear();

        if (hour == MORNING_BRIEF_HOUR && day != lastBriefDay) {
            lastBriefDay = day;
            sendMorningBrief();
        }
    }

    private void sendMorningBrief() {
        new Thread(() -> {
            try {
                List<GoogleCalendarService.CalendarEvent> events = calendarService.getToday();
                if (events.isEmpty()) {
                    System.out.println("[CalPoller] 오늘 일정 없음 - 브리핑 생략");
                    return;
                }

                String msg = "🌅 좋은 아침입니다!\n\n"
                           + GoogleCalendarService.formatEvents("오늘 일정", events);

                if (telegramBot != null && host != null) {
                    String chatId = host.getTelegramChatId();
                    if (!chatId.isEmpty()) {
                        telegramBot.send(chatId, msg);
                        System.out.println("[CalPoller] 아침 브리핑 전송 완료");
                    }
                }
            } catch (Exception e) {
                System.out.println("[CalPoller] 아침 브리핑 오류: " + e.getMessage());
            }
        }, "MorningBrief").start();
    }

    // ── 유틸 ──────────────────────────────────────────────────────

    /** 1시간 이상 지난 쿨다운 항목 정리 */
    private void cleanupFiredAlarms() {
        long now = System.currentTimeMillis();
        firedAlarms.entrySet().removeIf(e -> (now - e.getValue()) > 3_600_000);
    }
}
