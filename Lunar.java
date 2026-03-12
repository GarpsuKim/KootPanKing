import java.time.ZonedDateTime;

/**
 * Lunar - 양력 → 음력 변환 및 천간지지 계산 클래스
 *
 * 출처: 한국천문연구원(KASI) 음양력 대조표
 * 범위: 1900년 ~ 2050년
 *
 * 외부 라이브러리 불필요. 순수 Java 구현.
 * Swing/AWT 에 전혀 의존하지 않는 독립 유틸리티 클래스.
 */
public class Lunar {

    // ── 변환 결과 ─────────────────────────────────────────────────
    /**
     * 음력 변환 결과.
     * year / month / day : 음력 연월일
     * leap               : 윤달 여부
     * yearGanZhi / monthGanZhi / dayGanZhi : 천간지지
     */
    public static class Result {
        public final int     year, month, day;
        public final boolean leap;
        public final String  yearGanZhi;
        public final String  monthGanZhi;
        public final String  dayGanZhi;

        public Result(int year, int month, int day, boolean leap,
                      String yearGanZhi, String monthGanZhi, String dayGanZhi) {
            this.year        = year;
            this.month       = month;
            this.day         = day;
            this.leap        = leap;
            this.yearGanZhi  = yearGanZhi;
            this.monthGanZhi = monthGanZhi;
            this.dayGanZhi   = dayGanZhi;
        }

        /** 스크롤 바 표시용 문자열 */
        public String toDisplayText() {
            String leapStr = leap ? "윤" : "";
            return String.format(
                "  (음력) %04d년 %s%02d월 %02d일   %s년 %s月 %s日  ",
                year, leapStr, month, day,
                yearGanZhi, monthGanZhi, dayGanZhi);
        }
    }

    // ── 공개 API ──────────────────────────────────────────────────

    /**
     * 양력 날짜 → 음력 + 천간지지 변환.
     * @param zdt 변환할 날짜/시간 (타임존 포함)
     * @return 변환 결과 {@link Result}
     */
    public static Result convert(ZonedDateTime zdt) {
        int[] raw = solarToLunar(zdt);
        int ly = raw[0], lm = raw[1], ld = raw[2];
        boolean leap = (raw[3] == 1);

        String yearGanZhi  = getGanZhi(ly - 4, 60);
        String monthGanZhi = getMonthGanZhi(ly, lm);
        String dayGanZhi   = getDayGanZhi(zdt);

        return new Result(ly, lm, ld, leap, yearGanZhi, monthGanZhi, dayGanZhi);
    }

    // ── KASI 음양력 데이터 ────────────────────────────────────────
    // 출처: 한국천문연구원(KASI) 음양력 대조표
    // NY_EPOCH  : 각 음력년 정월초하루의 Java epochDay (1970-01-01 기준)
    // MONTH_DATA: 월별 일수 인코딩
    //   bit[16]  = 윤달 30일 여부 (1=30일, 0=29일)
    //   bit[15:4]= 1~12월 (1=30일, 0=29일)
    //   bit[3:0] = 윤달 위치 (0=없음)
    private static final int[] NY_EPOCH = {
        /* 1900 */ -25537, -25153, -24799, -24444, -24061, -23707, -23352, -22968, -22614, -22259,
        /* 1910 */ -21875, -21521, -21137, -20783, -20429, -20045, -19691, -19336, -18952, -18597,
        /* 1920 */ -18213, -17859, -17505, -17121, -16767, -16413, -16028, -15674, -15319, -14935,
        /* 1930 */ -14581, -14198, -13844, -13489, -13105, -12750, -12396, -12012, -11658, -11274,
        /* 1940 */ -10920, -10566, -10182, -9827, -9473, -9088, -8734, -8380, -7996, -7642,
        /* 1950 */ -7258, -6904, -6549, -6165, -5811, -5456, -5072, -4718, -4335, -3980,
        /* 1960 */ -3626, -3242, -2887, -2533, -2149, -1794, -1441, -1057, -702, -318,
        /* 1970 */ 36, 391, 775, 1129, 1483, 1867, 2221, 2605, 2959, 3314,
        /* 1980 */ 3698, 4053, 4407, 4791, 5145, 5529, 5883, 6237, 6621, 6976,
        /* 1990 */ 7331, 7715, 8069, 8423, 8806, 9161, 9545, 9899, 10254, 10638,
        /* 2000 */ 10992, 11346, 11730, 12084, 12439, 12823, 13177, 13562, 13916, 14270,
        /* 2010 */ 14654, 15008, 15362, 15746, 16101, 16485, 16839, 17194, 17578, 17932,
        /* 2020 */ 18286, 18670, 19024, 19379, 19763, 20117, 20501, 20855, 21209, 21593,
        /* 2030 */ 21948, 22302, 22686, 23041, 23425, 23779, 24133, 24517, 24871, 25225,
        /* 2040 */ 25609, 25964, 26319, 26703, 27057, 27441, 27795, 28149, 28533, 28887,
        /* 2050 */ 29242, 29626,
    };

    private static final int[] MONTH_DATA = {
        /* 1900 */ 0x004ae8, 0x00a570, 0x006950, 0x00b255, 0x006c90, 0x005b20, 0x0056c4, 0x009ad0, 0x004ae0, 0x00a4e2,
        /* 1910 */ 0x00d260, 0x00d920, 0x00d926, 0x00b640, 0x00ad90, 0x0055c5, 0x00a5d0, 0x0052d0, 0x01a952, 0x006ca0,
        /* 1920 */ 0x00ad40, 0x01d5a5, 0x005ac0, 0x00ab60, 0x004b74, 0x004ae0, 0x00a4e0, 0x01d268, 0x00d920, 0x00da90,
        /* 1930 */ 0x005b46, 0x009b60, 0x004b70, 0x004975, 0x0064b0, 0x007250, 0x01b523, 0x00b690, 0x005740, 0x00ab67,
        /* 1940 */ 0x005570, 0x004ae0, 0x00a566, 0x00d260, 0x00d920, 0x01da94, 0x005b40, 0x009b60, 0x004bb2, 0x0025d0,
        /* 1950 */ 0x0092d0, 0x01c955, 0x00d4a0, 0x00da40, 0x00ed23, 0x006d90, 0x002dc0, 0x0095e8, 0x0092e0, 0x00c960,
        /* 1960 */ 0x00e4a6, 0x00ea40, 0x00ed20, 0x006d94, 0x002ec0, 0x0096e0, 0x004ae3, 0x00a4e0, 0x00d260, 0x00d927,
        /* 1970 */ 0x00da90, 0x005b40, 0x00aba5, 0x0055b0, 0x0029b0, 0x0094b4, 0x00aa50, 0x00b520, 0x00b698, 0x005740,
        /* 1980 */ 0x00ab60, 0x015375, 0x004970, 0x0064b0, 0x007254, 0x007520, 0x007690, 0x0036c6, 0x0096e0, 0x004ae0,
        /* 1990 */ 0x00a4e5, 0x00d260, 0x00d920, 0x00dc93, 0x005d40, 0x00ada0, 0x0055b8, 0x0029b0, 0x0094b0, 0x00ca55,
        /* 2000 */ 0x00d520, 0x00da90, 0x005b44, 0x00ab60, 0x005370, 0x004972, 0x0064b0, 0x007250, 0x00b525, 0x00b690,
        /* 2010 */ 0x005740, 0x01ab63, 0x005370, 0x004970, 0x0064b9, 0x006a50, 0x006d20, 0x01ad95, 0x0055c0, 0x00a5d0,
        /* 2020 */ 0x0052d4, 0x00a950, 0x00d490, 0x01ea42, 0x00ed20, 0x006d96, 0x00a550, 0x006aa0, 0x01aca5, 0x00ad40,
        /* 2030 */ 0x00ada0, 0x0055d3, 0x0029d0, 0x0094d0, 0x00ca5b, 0x006d20, 0x00ad90, 0x0055c6, 0x00aae0, 0x0054e0,
        /* 2040 */ 0x01aa64, 0x00d520, 0x00da90, 0x015d42, 0x00ada0, 0x0055b0, 0x0029b6, 0x0094b0, 0x00ca50, 0x01d525,
        /* 2050 */ 0x00da90,
    };

    private static final int KASI_BASE = 1900;

    // ── 양력 → 음력 변환 ──────────────────────────────────────────
    private static int[] solarToLunar(ZonedDateTime zdt) {
        long epochDay = zdt.toLocalDate().toEpochDay();

        // 어느 음력년에 속하는지 NY_EPOCH 로 판단
        int ly = KASI_BASE;
        for (int i = 0; i < NY_EPOCH.length - 1; i++) {
            if (epochDay >= NY_EPOCH[i] && epochDay < NY_EPOCH[i + 1]) {
                ly = KASI_BASE + i;
                break;
            }
        }

        int offset    = (int)(epochDay - NY_EPOCH[ly - KASI_BASE]);
        int data      = MONTH_DATA[ly - KASI_BASE];
        int leapMonth = data & 0xF;
        boolean leapBig = ((data >> 16) & 1) == 1;

        int lm = 1;
        boolean isLeap = false;
        for (lm = 1; lm <= 12; lm++) {
            int dim = ((data >> (16 - lm)) & 1) == 1 ? 30 : 29;
            if (offset < dim) break;
            offset -= dim;
            if (lm == leapMonth) {
                int leapDim = leapBig ? 30 : 29;
                if (offset < leapDim) { isLeap = true; break; }
                offset -= leapDim;
            }
        }
        return new int[]{ ly, lm, offset + 1, isLeap ? 1 : 0 };
    }

    // ── 천간지지 ──────────────────────────────────────────────────
    private static final String[] GAN = {"甲","乙","丙","丁","戊","己","庚","辛","壬","癸"};
    private static final String[] ZHI = {"子","丑","寅","卯","辰","巳","午","未","申","酉","戌","亥"};

    /** 천간지지 공통 계산 */
    private static String getGanZhi(int baseOffset, int cycle) {
        int idx = ((baseOffset % cycle) + cycle) % cycle;
        return GAN[idx % 10] + ZHI[idx % 12];
    }

    /**
     * 월 천간지지: 음력 월 기준 (년간에 따라 월간 시작점 다름)
     * 甲己년 → 丙寅월(2), 乙庚년 → 戊寅월(4), 丙辛년 → 庚寅월(6)
     * 丁壬년 → 壬寅월(8), 戊癸년 → 甲寅월(0)
     */
    private static String getMonthGanZhi(int lunarYear, int lunarMonth) {
        int yearGanIdx = ((lunarYear - 4) % 10 + 10) % 10;
        int[] monthGanStart = {2, 4, 6, 8, 0, 2, 4, 6, 8, 0};
        int ganIdx = (monthGanStart[yearGanIdx] + (lunarMonth - 1)) % 10;
        int zhiIdx = (lunarMonth + 1) % 12;  // 1월=寅(2)
        return GAN[ganIdx] + ZHI[zhiIdx];
    }

    /** 일 천간지지: 기준일 2000/1/7 = 甲子일 (epochDay 기준) */
    private static String getDayGanZhi(ZonedDateTime zdt) {
        long epochDay = zdt.toLocalDate().toEpochDay();
        long ref  = java.time.LocalDate.of(2000, 1, 7).toEpochDay(); // 甲子
        long diff = epochDay - ref;
        int  idx  = (int)(((diff % 60) + 60) % 60);
        return GAN[idx % 10] + ZHI[idx % 12];
    }
}
