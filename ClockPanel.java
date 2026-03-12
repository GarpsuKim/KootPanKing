import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.time.*;
import java.time.format.*;
import java.util.*;

class ClockPanel extends JPanel {
    private int radius;
    private static final int DIG_HEIGHT = 44;  // digital bar height
    private static final int DIG_GAP    = 10;  // gap between clock and digital

    // AnalogClockSwing 의 설정 필드를 참조하기 위한 host 참조
    private final AnalogClockSwing host;

    // 테두리(bw) + 그림자(10) + 여유(6) 를 PADDING 으로 동적 계산
    // → 어떤 radius 값에서도 테두리가 절대 잘리지 않음
    private int getPadding() {
        if (!host.borderVisible) return 6;   // 테두리 제거 시 최소 여유만
        int bw = (host.borderWidth > 0) ? host.borderWidth : Math.max(8, radius / 16);
        return bw + 6;   // border + margin
	}
	
    ClockPanel(AnalogClockSwing host) {
        this.host = host;
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        radius = (int)(screen.height * 0.25);
        setOpaque(false);

	}
	
    int getRadius() { return radius; }
	
    void adjustRadius(int delta) {
        radius = Math.max(80, Math.min(700, radius + delta));
	}
	
    // Preferred size = clock area + (optional) digital bar
    @Override
    public Dimension getPreferredSize() {
        int p  = getPadding();
        int diameter = radius * 2;
        int w = diameter + p * 2;
        int h = diameter + p * 2
			+ (host.showDigital ? DIG_GAP + DIG_HEIGHT : 0)
			+ (host.showLunar   ? DIG_GAP + DIG_HEIGHT : 0);
        return new Dimension(w, h);
	}
	
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
		
        // Clock center (always fixed in clock area)
        int cx = getWidth() / 2;
        int cy = getPadding() + radius;
		
        drawClock(g2, cx, cy);
        int nextY = cy + radius + DIG_GAP;
        if (host.showDigital) {
            drawDigital(g2, cx, nextY);
            nextY += DIG_HEIGHT + DIG_GAP;
		}
        if (host.showLunar) {
            drawLunar(g2, cx, nextY);
		}
        g2.dispose();
	}
	
    // ── Draw clock ──────────────────────────────────────
    private void drawClock(Graphics2D g2, int cx, int cy) {
        boolean dark = host.theme.equals("Black");
        Color faceBase   = dark ? new Color(25,25,35)    : Color.WHITE;
        Color faceEdge   = dark ? new Color(5,5,10)      : new Color(215,215,225);
        // 테두리: 사용자 설정 우선, 없으면 테마 기본값
        Color defaultBorder = dark ? new Color(220,220,220) : new Color(20,20,20);
        Color baseBorderColor = (host.borderColor != null) ? host.borderColor : defaultBorder;
        // 알파 적용
        Color borderPaint = new Color(
            baseBorderColor.getRed(),
            baseBorderColor.getGreen(),
            baseBorderColor.getBlue(),
			host.borderAlpha);
        Color tColor = (host.tickColor   != null) ? host.tickColor   : (dark ? Color.WHITE : Color.BLACK);
        Color nColor = (host.numberColor != null) ? host.numberColor : (dark ? Color.WHITE : Color.BLACK);
		
        int bw = (host.borderVisible && host.borderWidth > 0) ? host.borderWidth
               : host.borderVisible ? Math.max(8, radius / 16) : 0;
		
        // ── 테두리 링 ─────────────────────────────────────
        if (host.borderVisible && host.borderAlpha > 0) {
            g2.setColor(borderPaint);
            g2.fillOval(cx - radius - bw, cy - radius - bw,
				(radius + bw) * 2, (radius + bw) * 2);
		}
		
        // ── Face ──────────────────────────────────────
        drawFace(g2, cx, cy, faceBase, faceEdge, dark);
		
        // ── Ticks & numbers ───────────────────────────
        if (host.tickVisible) drawTicks(g2, cx, cy, tColor);
        if (host.showNumbers) drawNumbers(g2, cx, cy, nColor);
		
        // ── 도시 이름 (Local 아닐 때만, 6시 위쪽) ────────
        if (!host.cityName.equals("Local")) {
            drawCityName(g2, cx, cy, nColor);
		}
		
        // ── Hands ────────────────────────────────────
        ZonedDateTime now = ZonedDateTime.now(host.timeZone);
        int hr  = now.getHour() % 12;
        int min = now.getMinute();
        int sec = now.getSecond();
        int ms  = now.getNano() / 1_000_000;
		
        double secAngle  = (sec + ms / 1000.0) * 6.0 - 90;
        double minAngle  = (min + sec / 60.0)  * 6.0 - 90;
        double hourAngle = (hr  + min / 60.0)  * 30.0 - 90;
		
        drawHand(g2, cx, cy, hourAngle, radius * 0.55, radius * 0.025 + 4,
			host.hourColor);
        drawHand(g2, cx, cy, minAngle,  radius * 0.80, radius * 0.018 + 3,
			host.minuteColor);
        if (host.secondVisible) drawSecondHand(g2, cx, cy, secAngle);
		
        // ── Center cap ───────────────────────────────
        int cr = Math.max(5, radius / 20);
        g2.setColor(new Color(220, 40, 40));
        g2.fillOval(cx - cr, cy - cr, cr * 2, cr * 2);
        g2.setColor(new Color(80, 0, 0));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval(cx - cr, cy - cr, cr * 2, cr * 2);
	}
	
    // ── Marble / custom face ──────────────────────────
    private void drawFace(Graphics2D g2, int cx, int cy,
			Color c1, Color c2, boolean dark) {
        Shape clip = new Ellipse2D.Double(cx - radius, cy - radius, radius*2, radius*2);
		
        // ── 고정 배경 이미지 ──────────────────────────────────────
        if (host.bgImageCache != null) {
            Shape oldClip = g2.getClip();
            g2.clip(clip);
            int iw = host.bgImageCache.getWidth(), ih = host.bgImageCache.getHeight();
            int d  = radius * 2;
            double scale = Math.max((double)d / iw, (double)d / ih);
            int sw = (int)(iw * scale), sh = (int)(ih * scale);
            int ox = cx - radius + (d - sw) / 2;
            int oy = cy - radius + (d - sh) / 2;
            g2.drawImage(host.bgImageCache, ox, oy, sw, sh, null);
            g2.setClip(oldClip);
            return;
        }

        // ── 슬라이드쇼 이미지 배경 ────────────────────────────
        if (host.slideImage != null) {
            Shape oldClip = g2.getClip();
            g2.clip(clip);
            // 원에 꽉 차도록 비율 유지하며 중앙 크롭
            int iw = host.slideImage.getWidth(), ih = host.slideImage.getHeight();
            int d  = radius * 2;
            double scale = Math.max((double)d / iw, (double)d / ih);
            int sw = (int)(iw * scale), sh = (int)(ih * scale);
            int ox = cx - radius + (d - sw) / 2;
            int oy = cy - radius + (d - sh) / 2;
            g2.drawImage(host.slideImage, ox, oy, sw, sh, null);
            // 살짝 어둡게 오버레이 (시계 바늘 가독성)
            if (host.slideOverlay > 0) {
                g2.setColor(new Color(0, 0, 0, host.slideOverlay));
                g2.fill(clip);
			}
            g2.setClip(oldClip);
            // marble veins 제외 (이미지 위엔 불필요)
            return;
		}

        // ── 카메라 배경 ───────────────────────────────────────────
        if (host.cameraMode && host.cameraFrame != null) {
            Shape oldClip = g2.getClip();
            g2.clip(clip);
            java.awt.image.BufferedImage cf = host.cameraFrame;
            int iw = cf.getWidth(), ih = cf.getHeight();
            int d  = radius * 2;
            double scale = Math.max((double)d / iw, (double)d / ih);
            int sw = (int)(iw * scale), sh = (int)(ih * scale);
            int ox = cx - radius + (d - sw) / 2;
            int oy = cy - radius + (d - sh) / 2;
            g2.drawImage(cf, ox, oy, sw, sh, null);
            // 약간 어둡게 오버레이 (시계 바늘 가독성)
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fill(clip);
            g2.setClip(oldClip);
            return;
        }
		
        // ── Galaxy 배경 ───────────────────────────────────────────
        if (host.galaxyMode) {
            Shape oldClip = g2.getClip();
            g2.clip(clip);
            drawGalaxy(g2, cx, cy);
            g2.setClip(oldClip);
            return;
        }

        // ── Matrix 배경 ───────────────────────────────────────────
        if (host.matrixMode) {
            Shape oldClip = g2.getClip();
            g2.clip(clip);
            drawMatrix(g2, cx, cy);
            g2.setClip(oldClip);
            return;
        }

        if (host.bgColor != null) {
            // 사용자 지정 색상에도 RadialGradient 적용
            Color bright = blendColor(host.bgColor, Color.WHITE, 0.45f);
            Color dark2  = blendColor(host.bgColor, Color.BLACK, 0.30f);
            RadialGradientPaint rg = new RadialGradientPaint(
                new Point2D.Float(cx, cy),
                radius * 1.25f,
                new float[]{0f, 0.55f, 1f},
                new Color[]{bright, host.bgColor, dark2}
			);
            g2.setPaint(rg);
            g2.fill(clip);
			} else {
            // 기본 대리석 RadialGradient
            RadialGradientPaint rg = new RadialGradientPaint(
                new Point2D.Float(cx, cy),
                radius * 1.3f,
                new float[]{0f, 1f},
                new Color[]{c1, c2}
			);
            g2.setPaint(rg);
            g2.fill(clip);
		}
		
        // Marble veins
        Shape oldClip = g2.getClip();
        g2.clip(clip);
        drawVeins(g2, cx, cy, dark);
        g2.setClip(oldClip);
	}
	
    // 두 색을 t 비율로 혼합 (t=0 → a, t=1 → b)
    private Color blendColor(Color a, Color b, float t) {
        float s = 1f - t;
        int r = Math.min(255, (int)(a.getRed()   * s + b.getRed()   * t));
        int g = Math.min(255, (int)(a.getGreen() * s + b.getGreen() * t));
        int bv= Math.min(255, (int)(a.getBlue()  * s + b.getBlue()  * t));
        return new Color(r, g, bv);
	}
	
    private void drawVeins(Graphics2D g2, int cx, int cy, boolean dark) {
        Random rnd = new Random(7);
        int veinCount = 10;
        for (int i = 0; i < veinCount; i++) {
            int alpha = dark ? 18 : 12;
            Color vc = dark ? new Color(220,220,240,alpha) : new Color(150,150,170,alpha);
            g2.setColor(vc);
            float sw = 0.5f + rnd.nextFloat() * 2f;
            g2.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double ax = cx + (rnd.nextDouble()*2-1)*radius;
            double ay = cy + (rnd.nextDouble()*2-1)*radius;
            double bx = cx + (rnd.nextDouble()*2-1)*radius;
            double by = cy + (rnd.nextDouble()*2-1)*radius;
            double ctrlX = cx + (rnd.nextDouble()*2-1)*radius*0.6;
            double ctrlY = cy + (rnd.nextDouble()*2-1)*radius*0.6;
            GeneralPath p = new GeneralPath();
            p.moveTo(ax, ay);
            p.quadTo(ctrlX, ctrlY, bx, by);
            g2.draw(p);
		}
	}

    // ── Galaxy background ─────────────────────────────
    private static final int     GAL_N      = 2000;
    private static final float[] GAL_DIST   = new float[GAL_N];
    private static final float[] GAL_ANG0   = new float[GAL_N];
    private static final float[] GAL_ANGSPD = new float[GAL_N];
    private static final int[]   GAL_HUE    = new int[GAL_N];
    private static final float[] GAL_BR     = new float[GAL_N];
    private static final float[] GAL_SZ     = new float[GAL_N];
    private static final float[] GAL_FLAT   = new float[GAL_N];
    private static boolean galInit   = false;
    private static int     galRadius = 0;

    private static void initGalaxy(int r) {
        java.util.Random rnd = new java.util.Random(42);
        for (int i = 0; i < GAL_N; i++) {
            int   arm  = i % 4;
            float dist = (float)Math.pow(rnd.nextFloat(), 0.55f) * 0.92f + 0.04f;
            float ang  = (float)(arm * Math.PI / 2.0 + dist * 3.8
                       + (rnd.nextFloat() - 0.5f) * 0.5f);
            GAL_DIST[i]   = dist;
            GAL_ANG0[i]   = ang;
            GAL_ANGSPD[i] = 0.25f - dist * 0.18f;
            GAL_HUE[i]    = (arm * 60 + (int)(dist * 120) + rnd.nextInt(30)) % 360;
            GAL_BR[i]     = 0.4f + rnd.nextFloat() * 0.6f;
            GAL_SZ[i]     = 0.8f + rnd.nextFloat() * 2.2f;
            GAL_FLAT[i]   = 0.55f + (rnd.nextFloat() - 0.5f) * 0.1f;
        }
        galInit   = true;
        galRadius = r;
    }

    private void drawGalaxy(Graphics2D g2, int cx, int cy) {
        if (!galInit || galRadius != radius) initGalaxy(radius);
        float t = host.galaxyAngle;
        java.awt.geom.Ellipse2D.Double face =
            new java.awt.geom.Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2);
        java.awt.RadialGradientPaint bg = new java.awt.RadialGradientPaint(
            new java.awt.geom.Point2D.Float(cx, cy), radius,
            new float[]{0f, 1f},
            new java.awt.Color[]{ new java.awt.Color(8, 8, 28), new java.awt.Color(2, 2, 10) }
        );
        g2.setPaint(bg); g2.fill(face);
        for (int i = 0; i < GAL_N; i++) {
            float ang  = GAL_ANG0[i] + t * GAL_ANGSPD[i];
            float dist = GAL_DIST[i] * radius;
            float px   = cx + (float)Math.cos(ang) * dist;
            float py   = cy + (float)Math.sin(ang) * dist * GAL_FLAT[i];
            float hf   = GAL_HUE[i] / 360f;
            float rv   = 0.5f + 0.5f * (float)Math.cos(2 * Math.PI * hf);
            float gv   = 0.5f + 0.5f * (float)Math.cos(2 * Math.PI * (hf + 0.33f));
            float bv   = 0.5f + 0.5f * (float)Math.cos(2 * Math.PI * (hf + 0.67f));
            float br   = GAL_BR[i];
            g2.setColor(new java.awt.Color(
                Math.min(1f, rv*br), Math.min(1f, gv*br), Math.min(1f, bv*br), br*0.88f));
            float sz = GAL_SZ[i];
            g2.fill(new java.awt.geom.Ellipse2D.Float(px-sz/2, py-sz/2, sz, sz));
        }
        java.awt.RadialGradientPaint glow = new java.awt.RadialGradientPaint(
            new java.awt.geom.Point2D.Float(cx, cy), radius * 0.35f,
            new float[]{0f, 1f},
            new java.awt.Color[]{ new java.awt.Color(200,190,255,90), new java.awt.Color(0,0,0,0) }
        );
        g2.setPaint(glow); g2.fill(face);
    }

    // ── Matrix background ─────────────────────────────
    // 각 열의 고정 시드 (반지름 바뀌면 재생성)
    private static int[]   mtxColCount  = null;  // 열 개수
    private static float[] mtxColX      = null;  // 각 열 x 좌표
    private static float[] mtxColSpeed  = null;  // 각 열 개별 속도 배율
    private static int[]   mtxColSeed   = null;  // 각 열 랜덤 시드
    private static int     mtxRadius    = 0;

    private static void initMatrix(int cx, int cy, int r) {
        int cellW = Math.max(8, r / 12);
        int cols  = (int)Math.ceil(r * 2.0 / cellW) + 2;
        mtxColCount = new int[]{cols};
        mtxColX     = new float[cols];
        mtxColSpeed = new float[cols];
        mtxColSeed  = new int[cols];
        java.util.Random rnd = new java.util.Random(99);
        for (int i = 0; i < cols; i++) {
            mtxColX[i]    = (cx - r) + i * cellW;
            mtxColSpeed[i]= 0.5f + rnd.nextFloat() * 1.0f;
            mtxColSeed[i] = rnd.nextInt(10000);
        }
        mtxRadius = r;
    }

    private void drawMatrix(Graphics2D g2, int cx, int cy) {
        if (mtxColCount == null || mtxRadius != radius) initMatrix(cx, cy, radius);

        // 배경
        g2.setColor(new java.awt.Color(0, 0, 0));
        g2.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);

        int cellW = Math.max(8, radius / 12);
        int cellH = cellW;
        int rows  = (int)Math.ceil(radius * 2.0 / cellH) + 2;
        int cols  = mtxColCount[0];
        float off = host.matrixOffset;

        java.awt.Font fnt = new java.awt.Font("Monospaced", java.awt.Font.BOLD, cellH - 1);
        g2.setFont(fnt);
        java.awt.FontMetrics fm = g2.getFontMetrics();

        for (int ci = 0; ci < cols; ci++) {
            float colSpd = mtxColSpeed[ci];
            int   seed   = mtxColSeed[ci];
            float colOff = off * colSpd;           // 열마다 속도 다름
            int   shift  = (int)(colOff / cellH);  // 몇 칸 올라갔나

            for (int ri = 0; ri < rows + 2; ri++) {
                // 위로 스크롤: ri가 커질수록 아래 → offset 빼서 위로 이동
                float py = (cy - radius) + ri * cellH - (colOff % cellH);
                float px = mtxColX[ci];

                // 원 바깥 스킵
                float dx = px + cellW/2f - cx;
                float dy = py + cellH/2f - cy;
                if (dx*dx + dy*dy > (float)radius*radius) continue;

                // 글자 결정 (시드 + 행 + shift로 변화)
                int charIdx = Math.abs((seed + ri + shift) * 1103515245 + 12345) % 62;
                char ch;
                if (charIdx < 10)      ch = (char)('0' + charIdx);
                else if (charIdx < 36) ch = (char)('A' + charIdx - 10);
                else                   ch = (char)('a' + charIdx - 36);

                // head(선두) 밝기
                int headRow = (int)(colOff / cellH) % rows;
                int distFromHead = Math.floorMod(ri - headRow, rows);
                int trailLen = 8;

                float bright;
                if (distFromHead == 0)        bright = 1.0f;        // 선두: 흰색
                else if (distFromHead < trailLen)
                    bright = 1.0f - (float)distFromHead / trailLen; // 꼬리
                else                          bright = 0.05f;        // 희미한 잔상

                int gb = (int)(bright * 220);
                int r2 = distFromHead == 0 ? 255 : 0;
                g2.setColor(new java.awt.Color(r2, gb, (int)(bright * 30), Math.min(255,(int)(bright*230+25))));
                g2.drawString(String.valueOf(ch),
                    (int)px + (cellW - fm.charWidth(ch)) / 2,
                    (int)py + cellH - 2);
            }
        }
    }

    // ── Tick marks ────────────────────────────────────
    private void drawTicks(Graphics2D g2, int cx, int cy, Color color) {
        for (int i = 0; i < 60; i++) {
            double angle = Math.toRadians(i * 6 - 90);
            boolean five = (i % 5 == 0);
            int innerR = five ? (int)(radius * 0.80) : (int)(radius * 0.88);
            int outerR = (int)(radius * 0.95);
            float sw   = five ? Math.max(2.5f, radius/30f) : Math.max(1f, radius/70f);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine((int)(cx+innerR*Math.cos(angle)), (int)(cy+innerR*Math.sin(angle)),
				(int)(cx+outerR*Math.cos(angle)), (int)(cy+outerR*Math.sin(angle)));
		}
	}
	
    // ── Numbers ───────────────────────────────────────
    private void drawNumbers(Graphics2D g2, int cx, int cy, Color color) {
        int fs = Math.max(10, radius / 7);
        Font f = host.numberFont.deriveFont(Font.BOLD, (float)fs);
        g2.setFont(f);
        g2.setColor(color);
        FontMetrics fm = g2.getFontMetrics();
        int numR = (int)(radius * 0.68);
        for (int i = 1; i <= 12; i++) {
            double a = Math.toRadians(i * 30 - 90);
            String s = String.valueOf(i);
            int nx = (int)(cx + numR * Math.cos(a)) - fm.stringWidth(s)/2;
            int ny = (int)(cy + numR * Math.sin(a)) + fm.getAscent()/2 - 2;
            g2.drawString(s, nx, ny);
		}
	}
	
    // ── City name label ───────────────────────────────
    private void drawCityName(Graphics2D g2, int cx, int cy, Color baseColor) {
        // 6시 숫자 위, 시계 중심 아래 약 55% 위치
        int labelY = cy + (int)(radius * 0.55);
		
        int fs = Math.max(9, radius / 10);
        Font f = new Font(host.numberFont.getFamily(), Font.BOLD, fs);
        g2.setFont(f);
        FontMetrics fm = g2.getFontMetrics();
		
        String text = host.cityName;
        int sw = fm.stringWidth(text);
        int sh = fm.getAscent();
		
        int tx = cx - sw / 2;
        int ty = labelY + sh / 2 - 2;
		
        // 반투명 배경 pill
        int pad = Math.max(4, radius / 30);
        int pillW = sw + pad * 2;
        int pillH = sh + pad;
        int pillX = tx - pad;
        int pillY = ty - sh - pad / 2;
		
        boolean dark = host.theme.equals("Black");
        Color pillBg = dark
			? new Color(255, 255, 255, 55)
			: new Color(0, 0, 0, 40);
        g2.setColor(pillBg);
        g2.fillRoundRect(pillX, pillY, pillW, pillH, pillH, pillH);
		
        // 텍스트
        Color textColor = (host.numberColor != null) ? host.numberColor
			: (dark ? new Color(220,220,220) : new Color(30,30,30));
        g2.setColor(textColor);
        g2.drawString(text, tx, ty);
	}
	
    // ── Hands ─────────────────────────────────────────
    private void drawHand(Graphics2D g2, int cx, int cy, double deg,
			double len, double wid, Color color) {
        double a  = Math.toRadians(deg);
        int tx = (int)(cx + len * Math.cos(a));
        int ty = (int)(cy + len * Math.sin(a));
        int bx = (int)(cx - len * 0.14 * Math.cos(a));
        int by = (int)(cy - len * 0.14 * Math.sin(a));
        // Shadow
        g2.setColor(new Color(0,0,0,60));
        g2.setStroke(new BasicStroke((float)wid+2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(bx+2, by+2, tx+2, ty+2);
        // Hand
        GradientPaint gp = new GradientPaint(bx, by, color.brighter(), tx, ty, color.darker());
        g2.setPaint(gp);
        g2.setStroke(new BasicStroke((float)wid, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(bx, by, tx, ty);
	}
	
    private void drawSecondHand(Graphics2D g2, int cx, int cy, double deg) {
        double a   = Math.toRadians(deg);
        int tipLen = (int)(radius * 0.88);
        int tailLen= (int)(radius * 0.22);
        int tx = (int)(cx + tipLen  * Math.cos(a));
        int ty = (int)(cy + tipLen  * Math.sin(a));
        int bx = (int)(cx - tailLen * Math.cos(a));
        int by = (int)(cy - tailLen * Math.sin(a));
        float sw = Math.max(1.5f, radius / 80f);
        g2.setColor(new Color(0,0,0,55));
        g2.setStroke(new BasicStroke(sw+1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(bx+1, by+1, tx+1, ty+1);
        g2.setColor(host.secondColor);
        g2.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(bx, by, tx, ty);
	}
	
    // ── Digital clock with scrolling marquee ──────────
    private void drawDigital(Graphics2D g2, int cx, int digTopY) {
        boolean dark = host.theme.equals("Black");
        ZonedDateTime now = ZonedDateTime.now(host.timeZone);
		
        String timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String dateStr = now.format(DateTimeFormatter.ofPattern("MM/dd a (E)", Locale.ENGLISH)).toLowerCase();
        String city    = host.cityName.equals("Local")
			? host.timeZone.getId().replaceAll(".*/", "")
			: host.cityName;
        String full = "  " + timeStr + "   " + dateStr + "   " + city + "  ";
		
        // Update scroll reference when string changes
        if (!full.equals(host.lastScrollStr)) {
            host.lastScrollStr = full;
		}
		
        // Digital bar dimensions
        int digW = radius * 2;
        int digH = DIG_HEIGHT;
        int barX = cx - digW / 2;
        int barY = digTopY;
		
        // ── Digital bar background with 3D bevel ─────
        drawDigitalBevel(g2, barX, barY, digW, digH, dark);
		
        // ── Clipping for scroll ───────────────────────
        Shape oldClip = g2.getClip();
        g2.setClip(new Rectangle(barX + 4, barY + 4, digW - 8, digH - 8));
		
        // Measure text
        int fs = Math.max(10, digH * 55 / 100);
        Font f = host.digitalFont.deriveFont(Font.PLAIN, (float)fs);
        g2.setFont(f);
        FontMetrics fm = g2.getFontMetrics();
        int strW = fm.stringWidth(full);
		
        // Wrap scrollX
        if (host.scrollX < -(float)strW) host.scrollX = 0;
		
        int ty = barY + (digH + fm.getAscent() - fm.getDescent()) / 2;
		
        // Draw text twice for seamless loop
        g2.setColor(host.digitalColor);
        float drawX = barX + host.scrollX;
        g2.drawString(full, drawX, ty);
        g2.drawString(full, drawX + strW, ty);
		
        g2.setClip(oldClip);
	}
	
    // ── Lunar calendar bar ────────────────────────────
    private void drawLunar(Graphics2D g2, int cx, int barTopY) {
        boolean dark = host.theme.equals("Black");
		
        // 음력 계산 (Lunar 클래스에 위임)
        Lunar.Result lunar = Lunar.convert(ZonedDateTime.now(host.timeZone));
        String text = lunar.toDisplayText();
		
        int digW = radius * 2;
        int digH = DIG_HEIGHT;
        int barX = cx - digW / 2;
        int barY = barTopY;
		
        drawDigitalBevel(g2, barX, barY, digW, digH, dark);
		
        // 스크롤 클리핑
        Shape oldClip = g2.getClip();
        g2.setClip(new Rectangle(barX + 4, barY + 4, digW - 8, digH - 8));
		
        int fs = Math.max(10, digH * 55 / 100);
        Font f = host.digitalFont.deriveFont(Font.PLAIN, (float) fs);
        g2.setFont(f);
        FontMetrics fm = g2.getFontMetrics();
        int strW = fm.stringWidth(text);
		
        if (host.lunarScrollX < -(float) strW) host.lunarScrollX = 0;
		
        int ty = barY + (digH + fm.getAscent() - fm.getDescent()) / 2;
        g2.setColor(host.digitalColor);
        g2.drawString(text, barX + host.lunarScrollX,          ty);
        g2.drawString(text, barX + host.lunarScrollX + strW,   ty);
		
        g2.setClip(oldClip);
	}
	
    // ── Digital bar 3D bevel ──────────────────────────
    private void drawDigitalBevel(Graphics2D g2, int x, int y, int w, int h, boolean dark) {
        // Base fill
        Color base = dark ? new Color(15,15,20,230) : new Color(30,30,30,230);
        g2.setColor(base);
        g2.fillRoundRect(x, y, w, h, 12, 12);
		
        // Top-left highlight
        g2.setColor(dark ? new Color(80,80,100,160) : new Color(100,100,100,160));
        g2.setStroke(new BasicStroke(2));
        g2.drawArc(x+1, y+1, w-2, h-2, 45, 180);
		
        // Bottom-right shadow
        g2.setColor(new Color(0,0,0,200));
        g2.drawArc(x+1, y+1, w-2, h-2, 225, 180);
		
        // Outer frame
        g2.setColor(dark ? new Color(60,60,80) : new Color(0,0,0));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(x, y, w, h, 12, 12);
		
        // Inner bright line (screen edge)
        g2.setColor(dark ? new Color(60,60,80,80) : new Color(255,255,255,30));
        g2.setStroke(new BasicStroke(1));
        g2.drawRoundRect(x+3, y+3, w-6, h-6, 8, 8);
	}
}