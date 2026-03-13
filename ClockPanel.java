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

            java.awt.image.BufferedImage prev = host.slidePrevImage;
            java.awt.image.BufferedImage curr = host.slideImage;
            float t = host.slideProgress;

            if (prev != null && t < 1.0f) {
                // 전환 중: 효과에 따라 prev → curr 렌더링
                switch (host.slideEffect) {
                    case "zoom_out":
                        // prev 고정, curr 가 0.7→1.0 커지며 페이드인 (확대 등장)
                        drawSlideImg(g2, cx, cy, prev, 1.0f, 0, 0);
                        drawSlideImgZoom(g2, cx, cy, curr, t, 0.7f + t * 0.3f);
                        break;
                    case "zoom_in":
                        // prev 가 1.0→0.7 작아지며 페이드아웃, curr 페이드인 (축소 퇴장)
                        drawSlideImgZoom(g2, cx, cy, prev, 1.0f - t, 1.0f - t * 0.3f);
                        drawSlideImg(g2, cx, cy, curr, t, 0, 0);
                        break;
                    case "left":
                        // curr 가 오른쪽→왼쪽으로 밀려 들어옴
                        drawSlideImg(g2, cx, cy, prev, 1.0f, 0, 0);
                        drawSlideImg(g2, cx, cy, curr, 1.0f, (int)((1.0f - t) * radius * 2), 0);
                        break;
                    case "right":
                        // curr 가 왼쪽→오른쪽으로 밀려 들어옴
                        drawSlideImg(g2, cx, cy, prev, 1.0f, 0, 0);
                        drawSlideImg(g2, cx, cy, curr, 1.0f, -(int)((1.0f - t) * radius * 2), 0);
                        break;
                    case "up":
                        // curr 가 아래→위로 밀려 들어옴
                        drawSlideImg(g2, cx, cy, prev, 1.0f, 0, 0);
                        drawSlideImg(g2, cx, cy, curr, 1.0f, 0, (int)((1.0f - t) * radius * 2));
                        break;
                    case "down":
                        // curr 가 위→아래로 밀려 들어옴
                        drawSlideImg(g2, cx, cy, prev, 1.0f, 0, 0);
                        drawSlideImg(g2, cx, cy, curr, 1.0f, 0, -(int)((1.0f - t) * radius * 2));
                        break;
                    default: // "fade"
                        drawSlideImg(g2, cx, cy, prev, 1.0f, 0, 0);
                        drawSlideImg(g2, cx, cy, curr, t,    0, 0);
                        break;
                }
            } else {
                // 전환 완료: curr 만 그림
                drawSlideImg(g2, cx, cy, curr, 1.0f, 0, 0);
            }

            if (host.slideOverlay > 0) {
                g2.setColor(new Color(0, 0, 0, host.slideOverlay));
                g2.fill(clip);
            }
            g2.setClip(oldClip);
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

        // ── Rain 배경 ─────────────────────────────────────────────
        if (host.rainMode) {
            Shape oldClip = g2.getClip();
            g2.clip(clip);
            drawRain(g2, cx, cy);
            g2.setClip(oldClip);
            return;
        }

        // ── Snow 배경 ─────────────────────────────────────────────
        if (host.snowMode) {
            Shape oldClip = g2.getClip();
            g2.clip(clip);
            drawSnow(g2, cx, cy);
            g2.setClip(oldClip);
            return;
        }

        // ── Fire 배경 ─────────────────────────────────────────────
        if (host.fireMode) {
            Shape oldClip = g2.getClip();
            g2.clip(clip);
            drawFire(g2, cx, cy);
            g2.setClip(oldClip);
            return;
        }

        
        // ── Sparkle 배경 ──────────────────────────────────────────
        if (host.sparkleMode) {
            Shape oldClip = g2.getClip();
            g2.clip(clip);
            drawSparkle(g2, cx, cy);
            g2.setClip(oldClip);
            return;
        }

        // ── Bubble 배경 ───────────────────────────────────────────
        if (host.bubbleMode) {
            Shape oldClip = g2.getClip();
            g2.clip(clip);
            drawBubble(g2, cx, cy);
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

    // ── Rain ──────────────────────────────────────────
    private float[]  rainX, rainY, rainSpd, rainLen, rainAlpha;
    private int      rainRadius = 0;
    private void initRain(int cx, int cy, int r) {
        int n = 120;
        rainX = new float[n]; rainY = new float[n];
        rainSpd = new float[n]; rainLen = new float[n]; rainAlpha = new float[n];
        for (int i = 0; i < n; i++) resetRainDrop(i, cx, cy, r, true);
        rainRadius = r;
    }
    private void resetRainDrop(int i, int cx, int cy, int r, boolean init) {
        float angle = (float)(Math.random() * Math.PI * 2);
        float dist  = (float)(Math.random() * r);
        rainX[i]    = cx + (float)(Math.cos(angle) * dist);
        rainY[i]    = init ? cy - r + (float)(Math.random() * r * 2) : cy - r - (float)(Math.random() * 20);
        rainSpd[i]  = 6f + (float)(Math.random() * 8f);
        rainLen[i]  = 10f + (float)(Math.random() * 18f);
        rainAlpha[i]= 0.3f + (float)(Math.random() * 0.5f);
    }
    private void drawRain(Graphics2D g2, int cx, int cy) {
        if (rainX == null || rainRadius != radius) initRain(cx, cy, radius);
        // 배경
        g2.setColor(new java.awt.Color(10, 15, 26));
        g2.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
        g2.setStroke(new BasicStroke(1f));
        for (int i = 0; i < rainX.length; i++) {
            int a = Math.min(255, (int)(rainAlpha[i] * 200));
            g2.setColor(new java.awt.Color(150, 210, 255, a));
            int x1 = (int)rainX[i], y1 = (int)rainY[i];
            int x2 = (int)(rainX[i] - 1), y2 = (int)(rainY[i] - rainLen[i]);
            g2.drawLine(x1, y1, x2, y2);
            rainY[i] += rainSpd[i];
            if (rainY[i] > cy + radius) resetRainDrop(i, cx, cy, radius, false);
        }
        g2.setStroke(new BasicStroke(1f));
    }

    // ── Snow ──────────────────────────────────────────
    private float[]  snowX, snowY, snowSpd, snowR, snowAlpha, snowWobble, snowWobbleSpd;
    private int      snowRadius = 0;
    private void initSnow(int cx, int cy, int r) {
        int n = 100;
        snowX = new float[n]; snowY = new float[n]; snowSpd = new float[n];
        snowR = new float[n]; snowAlpha = new float[n];
        snowWobble = new float[n]; snowWobbleSpd = new float[n];
        for (int i = 0; i < n; i++) resetSnowFlake(i, cx, cy, r, true);
        snowRadius = r;
    }
    private void resetSnowFlake(int i, int cx, int cy, int r, boolean init) {
        float angle = (float)(Math.random() * Math.PI * 2);
        float dist  = (float)(Math.random() * r);
        snowX[i]    = cx + (float)(Math.cos(angle) * dist);
        snowY[i]    = init ? cy - r + (float)(Math.random() * r * 2) : cy - r - (float)(Math.random() * 10);
        snowSpd[i]  = 0.8f + (float)(Math.random() * 1.5f);
        snowR[i]    = 2f + (float)(Math.random() * 4f);
        snowAlpha[i]= 0.5f + (float)(Math.random() * 0.5f);
        snowWobble[i] = (float)(Math.random() * Math.PI * 2);
        snowWobbleSpd[i] = 0.02f + (float)(Math.random() * 0.04f);
    }
    private void drawSnow(Graphics2D g2, int cx, int cy) {
        if (snowX == null || snowRadius != radius) initSnow(cx, cy, radius);
        g2.setColor(new java.awt.Color(10, 15, 30));
        g2.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
        for (int i = 0; i < snowX.length; i++) {
            int a = Math.min(255, (int)(snowAlpha[i] * 230));
            g2.setColor(new java.awt.Color(220, 235, 255, a));
            int r2 = Math.max(1, (int)snowR[i]);
            g2.fillOval((int)(snowX[i] - r2), (int)(snowY[i] - r2), r2 * 2, r2 * 2);
            snowWobble[i] += snowWobbleSpd[i];
            snowX[i] += (float)(Math.sin(snowWobble[i]) * 0.4f);
            snowY[i] += snowSpd[i];
            if (snowY[i] > cy + radius) resetSnowFlake(i, cx, cy, radius, false);
            if (snowX[i] < cx - radius) snowX[i] = cx + radius;
            if (snowX[i] > cx + radius) snowX[i] = cx - radius;
        }
    }

    // ── Fire ──────────────────────────────────────────
    private float[]  fireX, fireY, fireVx, fireVy, fireLife, fireDecay, fireSize;
    private int      fireRadius = 0;
    private void initFire(int cx, int cy, int r) {
        int n = 150;
        fireX = new float[n]; fireY = new float[n]; fireVx = new float[n];
        fireVy = new float[n]; fireLife = new float[n]; fireDecay = new float[n]; fireSize = new float[n];
        for (int i = 0; i < n; i++) { resetFireParticle(i, cx, cy, r); fireLife[i] = (float)Math.random(); }
        fireRadius = r;
    }
    private void resetFireParticle(int i, int cx, int cy, int r) {
        fireX[i]    = cx + (float)((Math.random() - 0.5) * r * 0.6);
        fireY[i]    = cy + r * 0.3f;
        fireVx[i]   = (float)((Math.random() - 0.5) * 2.0);
        fireVy[i]   = -3f - (float)(Math.random() * 3f);
        fireLife[i] = 1.0f;
        fireDecay[i]= 0.015f + (float)(Math.random() * 0.02f);
        fireSize[i] = 8f + (float)(Math.random() * 14f);
    }
    private void drawFire(Graphics2D g2, int cx, int cy) {
        if (fireX == null || fireRadius != radius) initFire(cx, cy, radius);
        g2.setColor(new java.awt.Color(10, 5, 0));
        g2.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
        for (int i = 0; i < fireX.length; i++) {
            float life = fireLife[i];
            int r2 = Math.max(1, (int)(fireSize[i] * life));
            java.awt.Color c;
            if      (life > 0.7f) c = new java.awt.Color(255, 255, 180, Math.min(255,(int)(life*220)));
            else if (life > 0.4f) c = new java.awt.Color(255, 140, 0,   Math.min(255,(int)(life*200)));
            else                  c = new java.awt.Color(200, 30,  0,   Math.min(255,(int)(life*150)));
            java.awt.RadialGradientPaint rg = new java.awt.RadialGradientPaint(
                fireX[i], fireY[i], r2,
                new float[]{0f, 1f},
                new java.awt.Color[]{c, new java.awt.Color(0,0,0,0)});
            g2.setPaint(rg);
            g2.fillOval((int)(fireX[i]-r2),(int)(fireY[i]-r2),r2*2,r2*2);
            fireX[i] += fireVx[i] + (float)(Math.sin(fireLife[i]*10)*0.5);
            fireY[i] += fireVy[i];
            fireLife[i] -= fireDecay[i];
            if (fireLife[i] <= 0) resetFireParticle(i, cx, cy, radius);
        }
    }

    // ── Sparkle ───────────────────────────────────────
    private float[]  spkX, spkY, spkR, spkMaxR, spkLife, spkSpd, spkRot, spkRotSpd;
    private int[]    spkColorIdx;
    private int      spkRadius = 0;
    private static final int[][] SPK_COLORS = {
        {255,255,200},{200,220,255},{255,200,255},{200,255,220},{255,220,180}
    };
    private void initSparkle(int cx, int cy, int r) {
        int n = 80;
        spkX=new float[n]; spkY=new float[n]; spkR=new float[n]; spkMaxR=new float[n];
        spkLife=new float[n]; spkSpd=new float[n]; spkRot=new float[n]; spkRotSpd=new float[n];
        spkColorIdx=new int[n];
        for (int i = 0; i < n; i++) { resetSparkle(i, cx, cy, r); spkLife[i]=(float)Math.random(); }
        spkRadius = r;
    }
    private void resetSparkle(int i, int cx, int cy, int r) {
        float angle=(float)(Math.random()*Math.PI*2), dist=(float)(Math.random()*r*0.9f);
        spkX[i]=cx+(float)(Math.cos(angle)*dist); spkY[i]=cy+(float)(Math.sin(angle)*dist);
        spkMaxR[i]=3f+(float)(Math.random()*8f); spkR[i]=1f;
        spkLife[i]=0f; spkSpd[i]=0.01f+(float)(Math.random()*0.02f);
        spkRot[i]=(float)(Math.random()*Math.PI);
        spkRotSpd[i]=(float)((Math.random()-0.5)*0.06);
        spkColorIdx[i]=(int)(Math.random()*SPK_COLORS.length);
    }
    private void drawSparkle(Graphics2D g2, int cx, int cy) {
        if (spkX == null || spkRadius != radius) initSparkle(cx, cy, radius);
        g2.setColor(new java.awt.Color(5, 5, 16));
        g2.fillOval(cx-radius,cy-radius,radius*2,radius*2);
        for (int i = 0; i < spkX.length; i++) {
            float alpha = (float)Math.sin(spkLife[i] * Math.PI);
            float r2 = spkMaxR[i] * (float)Math.sin(spkLife[i] * Math.PI);
            if (r2 < 1) { spkLife[i] += spkSpd[i]; if(spkLife[i]>=1) resetSparkle(i,cx,cy,radius); continue; }
            int[] sc = SPK_COLORS[spkColorIdx[i]];
            int a = Math.min(255,(int)(alpha*230));
            java.awt.Graphics2D g3 = (java.awt.Graphics2D)g2.create();
            g3.translate(spkX[i], spkY[i]);
            g3.rotate(spkRot[i]);
            g3.setColor(new java.awt.Color(sc[0],sc[1],sc[2],a));
            // 십자 광선
            g3.setStroke(new BasicStroke(0.8f));
            int len = (int)(r2 * 2.5f);
            g3.drawLine(-len, 0, len, 0);
            g3.drawLine(0, -len, 0, len);
            // 중심 원
            g3.fillOval(-(int)r2, -(int)r2, (int)(r2*2), (int)(r2*2));
            g3.dispose();
            spkLife[i] += spkSpd[i];
            spkRot[i] += spkRotSpd[i];
            if (spkLife[i] >= 1) resetSparkle(i, cx, cy, radius);
        }
    }

    // ── Bubble ────────────────────────────────────────
    private float[]  bubX, bubY, bubR, bubSpd, bubAlpha, bubWobble, bubWobbleSpd;
    private boolean[] bubPop;
    private float[]  bubPopR, bubPopAlpha;
    private int      bubRadius = 0;
    private void initBubble(int cx, int cy, int r) {
        int n = 40;
        bubX=new float[n]; bubY=new float[n]; bubR=new float[n]; bubSpd=new float[n];
        bubAlpha=new float[n]; bubWobble=new float[n]; bubWobbleSpd=new float[n];
        bubPop=new boolean[n]; bubPopR=new float[n]; bubPopAlpha=new float[n];
        for (int i = 0; i < n; i++) resetBubble(i, cx, cy, r, true);
        bubRadius = r;
    }
    private void resetBubble(int i, int cx, int cy, int r, boolean init) {
        float angle=(float)(Math.random()*Math.PI*2), dist=(float)(Math.random()*r*0.8f);
        bubR[i]     = 6f + (float)(Math.random() * r * 0.15f);
        bubX[i]     = cx + (float)(Math.cos(angle) * dist);
        bubY[i]     = init ? cy + (float)((Math.random()-0.5)*r*2) : cy + r + bubR[i];
        bubSpd[i]   = 0.5f + (float)(Math.random() * 1.5f);
        bubAlpha[i] = 0.15f + (float)(Math.random() * 0.3f);
        bubWobble[i]= (float)(Math.random() * Math.PI * 2);
        bubWobbleSpd[i] = 0.02f + (float)(Math.random() * 0.04f);
        bubPop[i]   = false; bubPopR[i] = 0; bubPopAlpha[i] = 0;
    }
    private void drawBubble(Graphics2D g2, int cx, int cy) {
        if (bubX == null || bubRadius != radius) initBubble(cx, cy, radius);
        g2.setColor(new java.awt.Color(6, 6, 18));
        g2.fillOval(cx-radius,cy-radius,radius*2,radius*2);
        for (int i = 0; i < bubX.length; i++) {
            if (bubPop[i]) {
                // 터짐 효과
                int a = Math.min(255,(int)(bubPopAlpha[i]*200));
                g2.setColor(new java.awt.Color(180,220,255,a));
                g2.setStroke(new BasicStroke(0.8f));
                for (int s=0;s<8;s++) {
                    double ang = s * Math.PI / 4;
                    int x1=(int)(bubX[i]+Math.cos(ang)*bubR[i]*0.5f);
                    int y1=(int)(bubY[i]+Math.sin(ang)*bubR[i]*0.5f);
                    int x2=(int)(bubX[i]+Math.cos(ang)*(bubR[i]*0.5f+bubPopR[i]*0.5f));
                    int y2=(int)(bubY[i]+Math.sin(ang)*(bubR[i]*0.5f+bubPopR[i]*0.5f));
                    g2.drawLine(x1,y1,x2,y2);
                }
                bubPopR[i]+=2; bubPopAlpha[i]-=0.06f;
                if (bubPopAlpha[i]<=0) resetBubble(i,cx,cy,radius,false);
                continue;
            }
            int r2=Math.max(2,(int)bubR[i]);
            int a=Math.min(255,(int)(bubAlpha[i]*255));
            // 테두리
            g2.setColor(new java.awt.Color(180,220,255,Math.min(255,a*2)));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawOval((int)(bubX[i]-r2),(int)(bubY[i]-r2),r2*2,r2*2);
            // 하이라이트
            g2.setColor(new java.awt.Color(255,255,255,Math.min(255,(int)(bubAlpha[i]*180))));
            g2.fillOval((int)(bubX[i]-r2*0.4f),(int)(bubY[i]-r2*0.4f),(int)(r2*0.5f),(int)(r2*0.5f));
            bubWobble[i]+=bubWobbleSpd[i];
            bubX[i]+=(float)(Math.sin(bubWobble[i])*0.4f);
            bubY[i]-=bubSpd[i];
            if (bubY[i] < cy - radius - r2) {
                bubPop[i]=true; bubPopR[i]=0; bubPopAlpha[i]=1f;
            }
        }
        g2.setStroke(new BasicStroke(1f));
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

    // ── 슬라이드 전환 보조 메서드 ─────────────────────────────────

    /**
     * 이미지를 alpha 투명도 + (offsetX, offsetY) 위치 이동으로 그린다.
     * offsetX > 0 → 오른쪽에서 들어옴 (left 효과)
     * offsetX < 0 → 왼쪽에서 들어옴  (right 효과)
     * offsetY > 0 → 아래에서 들어옴  (up 효과)
     * offsetY < 0 → 위에서 들어옴    (down 효과)
     */
    private void drawSlideImg(Graphics2D g2, int cx, int cy,
            java.awt.image.BufferedImage img, float alpha, int offsetX, int offsetY) {
        if (img == null) return;
        java.awt.Composite old = g2.getComposite();
        if (alpha < 1.0f)
            g2.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
        int d  = radius * 2;
        double scale = Math.max((double) d / img.getWidth(), (double) d / img.getHeight());
        int sw = (int)(img.getWidth()  * scale);
        int sh = (int)(img.getHeight() * scale);
        int ox = cx - radius + (d - sw) / 2 + offsetX;
        int oy = cy - radius + (d - sh) / 2 + offsetY;
        g2.drawImage(img, ox, oy, sw, sh, null);
        g2.setComposite(old);
    }

    /**
     * 이미지를 중앙 기준으로 zoom 배율로 그린다. (zoom_in / zoom_out 효과)
     * zoom < 1 → 작게, zoom > 1 → 크게
     */
    private void drawSlideImgZoom(Graphics2D g2, int cx, int cy,
            java.awt.image.BufferedImage img, float alpha, float zoom) {
        if (img == null) return;
        java.awt.Composite old = g2.getComposite();
        if (alpha < 1.0f)
            g2.setComposite(java.awt.AlphaComposite.getInstance(
                    java.awt.AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha))));
        int d  = radius * 2;
        double scale = Math.max((double) d / img.getWidth(), (double) d / img.getHeight()) * zoom;
        int sw = (int)(img.getWidth()  * scale);
        int sh = (int)(img.getHeight() * scale);
        // ② drawSlideImg와 동일한 원점 계산으로 통일
        int d2 = radius * 2;
        int ox = cx - radius + (d2 - sw) / 2;
        int oy = cy - radius + (d2 - sh) / 2;
        g2.drawImage(img, ox, oy, sw, sh, null);
        g2.setComposite(old);
    }
}