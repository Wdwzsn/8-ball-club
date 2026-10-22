package com.pool8.ui;

import com.pool8.core.Ball;
import com.pool8.core.Table;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** 程序化生成的材质与球体贴图（无外部图片资源）。 */
public final class Art {
    private static BufferedImage feltTile;
    private static BufferedImage woodTile;
    private static final Map<Long, BufferedImage> BALLS = new HashMap<>();
    private static final Map<Integer, BufferedImage> SHADOWS = new HashMap<>();

    private Art() {}

    public static synchronized void init() {
        if (feltTile != null) return;
        feltTile = makeFelt(160, 160);
        woodTile = makeWood(512, 192);
    }

    private static BufferedImage makeFelt(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(20240612);
        int br = 13, bg = 97, bb = 56;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double weave = Math.sin(x * 1.9) * Math.cos(y * 1.9) * 1.1
                        + Math.sin((x + y) * 0.9) * 0.7;
                double n = (rnd.nextDouble() - 0.5) * 4.2;
                double fiber = rnd.nextDouble() < 0.005 ? 7 : 0;
                int r = clamp(br + (int) (weave + n + fiber * 0.4));
                int g = clamp(bg + (int) (weave + n + fiber));
                int b = clamp(bb + (int) (weave * 0.8 + n * 0.7 + fiber * 0.5));
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    private static BufferedImage makeWood(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(99123);
        Color dark = new Color(0x2E160D);
        Color mid = new Color(0x542917);
        Color light = new Color(0x7A4528);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double grain = 0.5 + 0.5 * Math.sin(y * 1.35 + Math.sin(x * 0.017) * 2.2
                        + Math.sin(x * 0.0053) * 5.0 + Math.sin(x * 0.11) * 0.45);
                grain = Math.pow(grain, 1.4);
                Color c = Theme.mix(dark, light, grain * 0.52 + 0.10);
                double n = (rnd.nextDouble() - 0.5) * 6;
                int r = clamp(c.getRed() + (int) n);
                int g = clamp(c.getGreen() + (int) n);
                int b = clamp(c.getBlue() + (int) n);
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        // 细木纹
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int i = 0; i < 120; i++) {
            int y = rnd.nextInt(h);
            int x0 = rnd.nextInt(w);
            int len = 40 + rnd.nextInt(240);
            g.setColor(new Color(0, 0, 0, 8 + rnd.nextInt(20)));
            g.setStroke(new BasicStroke(0.5f + rnd.nextFloat() * 0.9f));
            g.drawLine(x0, y, x0 + len, y + rnd.nextInt(3) - 1);
        }
        g.dispose();
        return img;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public static TexturePaint woodPaint() {
        init();
        return new TexturePaint(woodTile, new Rectangle2D.Double(0, 0, woodTile.getWidth(), woodTile.getHeight()));
    }

    public static TexturePaint feltPaint() {
        init();
        return new TexturePaint(feltTile, new Rectangle2D.Double(0, 0, feltTile.getWidth(), feltTile.getHeight()));
    }

    /** 绘制整张球台（木框、台呢、袋口、点位），用于缓存成底图。 */
    public static void paintTable(Graphics2D g, double ox, double oy, double scale) {
        init();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        double rail = Table.RAIL_W * scale;
        double w = Table.W * scale;
        double h = Table.H * scale;
        double fx = ox - rail, fy = oy - rail;
        double fw = w + rail * 2, fh = h + rail * 2;
        double radius = rail * 1.05;

        // 桌面投影
        for (int i = 6; i >= 1; i--) {
            g.setColor(new Color(0, 0, 0, 10));
            g.fill(new RoundRectangle2D.Double(fx - i * 2 + 4, fy - i * 2 + 10, fw + i * 4, fh + i * 4, radius + i * 6, radius + i * 6));
        }

        Shape frame = new RoundRectangle2D.Double(fx, fy, fw, fh, radius * 2, radius * 2);
        g.setPaint(woodPaint());
        g.fill(frame);
        // 木框明暗
        g.setPaint(new RadialGradientPaint(new Point2D.Double(fx + fw * 0.35, fy + fh * 0.1),
                (float) (fw * 0.85), new float[]{0f, 0.55f, 1f},
                new Color[]{new Color(255, 255, 255, 26), new Color(255, 255, 255, 0), new Color(0, 0, 0, 90)}));
        g.fill(frame);
        g.setColor(new Color(0, 0, 0, 130));
        g.setStroke(new BasicStroke(2f));
        g.draw(frame);
        g.setColor(new Color(255, 226, 180, 46));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new RoundRectangle2D.Double(fx + 2, fy + 2, fw - 4, fh - 4, radius * 2 - 4, radius * 2 - 4));

        // 台呢
        Shape cloth = new RoundRectangle2D.Double(ox, oy, w, h, 10, 10);
        g.setPaint(feltPaint());
        g.fill(cloth);
        // 台呢光照 + 暗角
        g.setClip(cloth);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(ox + w * 0.38, oy + h * 0.28),
                (float) (w * 0.78), new float[]{0f, 0.45f, 1f},
                new Color[]{new Color(255, 255, 240, 30), new Color(0, 0, 0, 0), new Color(0, 0, 0, 120)}));
        g.fill(cloth);
        // 库边内侧阴影
        int edge = (int) Math.max(6, 0.02 * scale);
        g.setPaint(new java.awt.GradientPaint(0, (float) oy, new Color(0, 0, 0, 95), 0, (float) (oy + edge), new Color(0, 0, 0, 0)));
        g.fill(new Rectangle2D.Double(ox, oy, w, edge));
        g.setPaint(new java.awt.GradientPaint(0, (float) (oy + h), new Color(0, 0, 0, 95), 0, (float) (oy + h - edge), new Color(0, 0, 0, 0)));
        g.fill(new Rectangle2D.Double(ox, oy + h - edge, w, edge));
        g.setPaint(new java.awt.GradientPaint((float) ox, 0, new Color(0, 0, 0, 80), (float) (ox + edge), 0, new Color(0, 0, 0, 0)));
        g.fill(new Rectangle2D.Double(ox, oy, edge, h));
        g.setPaint(new java.awt.GradientPaint((float) (ox + w), 0, new Color(0, 0, 0, 80), (float) (ox + w - edge), 0, new Color(0, 0, 0, 0)));
        g.fill(new Rectangle2D.Double(ox + w - edge, oy, edge, h));
        g.setClip(null);

        // 开球线与置球点
        g.setColor(new Color(255, 255, 255, 20));
        g.setStroke(new BasicStroke(1f));
        g.draw(new java.awt.geom.Line2D.Double(ox + Table.HEAD_X * scale, oy, ox + Table.HEAD_X * scale, oy + h));
        g.setColor(new Color(0, 0, 0, 60));
        double dot = Math.max(1.6, 0.006 * scale);
        g.fill(new Ellipse2D.Double(ox + Table.FOOT_X * scale - dot / 2, oy + Table.H / 2 * scale - dot / 2, dot, dot));
        g.fill(new Ellipse2D.Double(ox + Table.HEAD_X * scale - dot / 2, oy + Table.H / 2 * scale - dot / 2, dot, dot));

        // 库边（橡胶条）：位于台呢外侧、木框内侧
        Table geo = new Table();
        double cw = 0.036 * scale;
        for (Table.Seg s : geo.rails) {
            double x1 = ox + s.ax * scale, y1 = oy + s.ay * scale;
            double x2 = ox + s.bx * scale, y2 = oy + s.by * scale;
            double cx = -s.nx * cw / 2, cy = -s.ny * cw / 2;
            g.setColor(new Color(0x0A5731));
            g.setStroke(new BasicStroke((float) cw, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            g.draw(new java.awt.geom.Line2D.Double(x1 + cx, y1 + cy, x2 + cx, y2 + cy));
            g.setColor(new Color(80, 175, 120, 70));
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            g.draw(new java.awt.geom.Line2D.Double(x1 + s.nx, y1 + s.ny, x2 + s.nx, y2 + s.ny));
            g.setColor(new Color(0, 0, 0, 95));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new java.awt.geom.Line2D.Double(x1 - s.nx * cw, y1 - s.ny * cw,
                    x2 - s.nx * cw, y2 - s.ny * cw));
        }

        // 袋口
        for (Table.Pocket p : geo.pockets) {
            double px = ox + p.x * scale, py = oy + p.y * scale;
            double pr = (p.side ? 0.063 : 0.062) * scale;
            // 袋口外圈皮革
            g.setColor(new Color(0x221008));
            g.fill(new Ellipse2D.Double(px - pr * 1.22, py - pr * 1.22, pr * 2.44, pr * 2.44));
            g.setPaint(new RadialGradientPaint(new Point2D.Double(px, py), (float) (pr * 1.24),
                    new float[]{0.75f, 0.9f, 1f},
                    new Color[]{new Color(0x2A1409), new Color(0x4A2A16), new Color(0x1C0D06)}));
            g.fill(new Ellipse2D.Double(px - pr * 1.18, py - pr * 1.18, pr * 2.36, pr * 2.36));
            // 洞
            g.setPaint(new RadialGradientPaint(new Point2D.Double(px - pr * 0.15, py - pr * 0.15), (float) (pr * 1.15),
                    new float[]{0f, 0.65f, 1f},
                    new Color[]{new Color(0x000000), new Color(0x080A08), new Color(0x1A1A18)}));
            g.fill(new Ellipse2D.Double(px - pr, py - pr, pr * 2, pr * 2));
            g.setColor(new Color(0, 0, 0, 160));
            g.setStroke(new BasicStroke(1.4f));
            g.draw(new Ellipse2D.Double(px - pr, py - pr, pr * 2, pr * 2));
        }

        // 库边钻石标记
        Color sight = new Color(232, 219, 184, 210);
        Color sightDark = new Color(120, 100, 66, 180);
        double ds = Math.max(3, 0.0075 * scale);
        double yTop = fy + rail * 0.5, yBot = fy + fh - rail * 0.5;
        for (int i = 1; i <= 7; i++) {
            if (i == 4) continue;
            double x = ox + w * i / 8.0;
            drawDiamond(g, x, yTop, ds, sight, sightDark);
            drawDiamond(g, x, yBot, ds, sight, sightDark);
        }
        for (int i = 1; i <= 3; i++) {
            double y = oy + h * i / 4.0;
            drawDiamond(g, fx + rail * 0.5, y, ds, sight, sightDark);
            drawDiamond(g, fx + fw - rail * 0.5, y, ds, sight, sightDark);
        }
    }

    private static void drawDiamond(Graphics2D g, double x, double y, double s, Color light, Color dark) {
        java.awt.geom.Path2D p = new java.awt.geom.Path2D.Double();
        p.moveTo(x, y - s);
        p.lineTo(x + s * 0.72, y);
        p.lineTo(x, y + s);
        p.lineTo(x - s * 0.72, y);
        p.closePath();
        g.setColor(dark);
        g.fill(p);
        g.setColor(light);
        g.setStroke(new BasicStroke(1f));
        g.draw(p);
    }

    public static BufferedImage ballSprite(int id, int diameter) {
        init();
        int d = Math.max(6, diameter);
        long key = id * 4096L + d;
        BufferedImage cached = BALLS.get(key);
        if (cached != null) return cached;
        if (BALLS.size() > 400) BALLS.clear();
        BufferedImage img = renderBall(id, d);
        BALLS.put(key, img);
        return img;
    }

    private static BufferedImage renderBall(int id, int d) {
        int ss = Math.max(12, d * 2);
        BufferedImage img = new BufferedImage(ss, ss, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double c = ss / 2.0;
        double r = ss / 2.0 - ss * 0.02;
        Shape circle = new Ellipse2D.Double(c - r, c - r, 2 * r, 2 * r);
        Color base = new Color(new Ball(id).baseColor());
        boolean stripe = id >= 9 && id <= 15;
        boolean cue = id == 0;

        g.setClip(circle);
        g.setColor(cue ? new Color(0xF7F2E2) : new Color(0xF6F1E2));
        g.fill(circle);
        if (stripe) {
            g.setColor(base);
            g.fill(new Rectangle2D.Double(c - r, c - r * 0.52, 2 * r, r * 1.04));
        } else if (!cue) {
            g.setColor(base);
            g.fill(circle);
        }

        // 球面光影
        g.setPaint(new RadialGradientPaint(new Point2D.Double(c - r * 0.38, c - r * 0.45), (float) (r * 1.45),
                new float[]{0f, 0.32f, 0.62f, 0.86f, 1f},
                new Color[]{new Color(255, 255, 255, 120), new Color(255, 255, 255, 26),
                        new Color(0, 0, 0, 0), new Color(0, 0, 0, 78), new Color(0, 0, 0, 165)}));
        g.fill(circle);

        if (!cue) {
            // 号码白圈
            double nr = r * 0.34;
            g.setColor(new Color(250, 248, 238, 245));
            g.fill(new Ellipse2D.Double(c - nr, c - nr, nr * 2, nr * 2));
            g.setColor(new Color(0, 0, 0, 40));
            g.setStroke(new BasicStroke((float) Math.max(0.6, r * 0.02)));
            g.draw(new Ellipse2D.Double(c - nr, c - nr, nr * 2, nr * 2));
            int num = id > 8 ? id - 8 : id;
            if (d >= 14) {
                g.setColor(new Color(0x151515));
                float fs = (float) (r * 0.62);
                g.setFont(Theme.font(java.awt.Font.BOLD, fs));
                String t = String.valueOf(num);
                java.awt.FontMetrics fm = g.getFontMetrics();
                g.drawString(t, (float) (c - fm.stringWidth(t) / 2.0), (float) (c + fm.getAscent() * 0.36));
            }
        } else {
            g.setColor(Theme.alpha(new Color(0xB63A31), 190));
            g.fill(new Ellipse2D.Double(c + r * 0.30, c - r * 0.42, r * 0.10, r * 0.10));
        }

        // 高光
        g.setPaint(new RadialGradientPaint(new Point2D.Double(c - r * 0.34, c - r * 0.40), (float) (r * 0.42),
                new float[]{0f, 1f}, new Color[]{new Color(255, 255, 255, 225), new Color(255, 255, 255, 0)}));
        g.fill(new Ellipse2D.Double(c - r * 0.34 - r * 0.42, c - r * 0.40 - r * 0.42, r * 0.84, r * 0.84));

        // 边缘
        g.setClip(null);
        g.setColor(new Color(0, 0, 0, 100));
        g.setStroke(new BasicStroke((float) Math.max(0.8, r * 0.035)));
        g.draw(circle);
        g.dispose();
        return img;
    }

    /** 软阴影贴图。 */
    public static BufferedImage shadow(int d) {
        int size = Math.max(6, d);
        BufferedImage cached = SHADOWS.get(size);
        if (cached != null) return cached;
        int s = size * 2;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new RadialGradientPaint(new Point2D.Double(s / 2.0, s / 2.0), s / 2f,
                new float[]{0f, 0.55f, 1f},
                new Color[]{new Color(0, 0, 0, 130), new Color(0, 0, 0, 60), new Color(0, 0, 0, 0)}));
        g.fill(new Ellipse2D.Double(0, 0, s, s));
        g.dispose();
        SHADOWS.put(size, img);
        return img;
    }

    public static void drawBall(Graphics2D g, Ball b, double ox, double oy, double scale, double alpha) {
        if (!b.active) return;
        double r = Table.R * scale;
        double cx = ox + b.x * scale;
        double cy = oy + b.y * scale;
        drawBallAt(g, b.id, cx, cy, r, alpha);
    }

    public static void drawBallAt(Graphics2D g, int id, double cx, double cy, double r, double alpha) {
        if (r < 1) return;
        int d = (int) Math.round(r * 2);
        // 阴影
        double shOff = r * 0.30;
        BufferedImage sh = shadow(d + 6);
        CompositeState cs = new CompositeState(g);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) (0.62 * alpha)));
        g.drawImage(sh, (int) Math.round(cx - r - shOff * 0.3), (int) Math.round(cy - r + shOff),
                d + (int) (r * 0.5), d + (int) (r * 0.5), null);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
        BufferedImage sp = ballSprite(id, d);
        g.drawImage(sp, (int) Math.round(cx - r), (int) Math.round(cy - r), d, d, null);
        cs.restore(g);
    }

    /** 简单保存/恢复 composite。 */
    static final class CompositeState {
        final java.awt.Composite c;
        CompositeState(Graphics2D g) { c = g.getComposite(); }
        void restore(Graphics2D g) { g.setComposite(c); }
    }
}
