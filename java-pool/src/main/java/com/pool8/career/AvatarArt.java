package com.pool8.career;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** 6 个程序化生成的卡通头像。 */
public final class AvatarArt {
    public static final int COUNT = 6;

    private static final int[] SKIN = {0xF3D1B4, 0xF1C27D, 0xC68642, 0x8D5524, 0x6B4226, 0xFFDBAC};
    private static final int[] HAIR = {0xE8C34A, 0x2B2B33, 0x4A3020, 0xB3472B, 0xB9B9C0, 0x3E6BD6};
    private static final int[] BG = {0x6FB98F, 0x6FA8DC, 0xD8B36A, 0xE08A8A, 0xC79BD0, 0x8FC7C2};
    // 0 短发 1 长发 2 卷发 3 刺头 4 光头+胡子 5 帽子
    private static final int[] STYLE = {0, 1, 2, 3, 4, 5};

    private AvatarArt() {}

    public static BufferedImage avatar(int idx, int size) {
        idx = Math.floorMod(idx, COUNT);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double c = size / 2.0;
        double r = c - size * 0.02;
        Color bg = new Color(BG[idx]);
        g.setColor(bg);
        g.fill(new Ellipse2D.Double(c - r, c - r, r * 2, r * 2));
        g.setColor(bg.darker());
        g.setStroke(new BasicStroke(size * 0.03f));
        g.draw(new Ellipse2D.Double(c - r, c - r, r * 2, r * 2));

        Color skin = new Color(SKIN[idx]);
        Color hair = new Color(HAIR[idx]);
        double hr = size * 0.30;
        double hx = c, hy = size * 0.55;
        Ellipse2D head = new Ellipse2D.Double(hx - hr, hy - hr, hr * 2, hr * 2);
        g.setColor(skin);
        g.fill(head);

        drawHair(g, STYLE[idx], hx, hy, hr, hair, skin, size);

        // 眼睛
        double ey = size * 0.54, ex = hr * 0.42;
        g.setColor(new Color(0x2A2A2A));
        g.fill(new Ellipse2D.Double(hx - ex - hr * 0.12, ey - hr * 0.12, hr * 0.24, hr * 0.30));
        g.fill(new Ellipse2D.Double(hx + ex - hr * 0.12, ey - hr * 0.12, hr * 0.24, hr * 0.30));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(hx - ex - hr * 0.07, ey - hr * 0.08, hr * 0.08, hr * 0.10));
        g.fill(new Ellipse2D.Double(hx + ex - hr * 0.01, ey - hr * 0.08, hr * 0.08, hr * 0.10));

        // 腮红
        g.setColor(new Color(232, 139, 139, 90));
        g.fill(new Ellipse2D.Double(hx - hr * 0.72, hy + hr * 0.18, hr * 0.30, hr * 0.18));
        g.fill(new Ellipse2D.Double(hx + hr * 0.42, hy + hr * 0.18, hr * 0.30, hr * 0.18));

        // 嘴
        g.setColor(new Color(0xB0503C));
        g.setStroke(new BasicStroke(size * 0.022f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(hx - hr * 0.22, hy + hr * 0.18, hr * 0.44, hr * 0.34, 200, 140, Arc2D.OPEN));

        g.dispose();
        return img;
    }

    private static void drawHair(Graphics2D g, int style, double hx, double hy, double hr,
                                 Color hair, Color skin, int size) {
        switch (style) {
            case 0: // 短发
                g.setColor(hair);
                g.fill(new Arc2D.Double(hx - hr * 1.02, hy - hr * 0.98, hr * 2.04, hr * 1.6, 0, 180, Arc2D.CHORD));
                g.fill(new RoundRectangle2D.Double(hx - hr * 1.02, hy - hr * 0.30, hr * 0.18, hr * 0.62, 6, 6));
                g.fill(new RoundRectangle2D.Double(hx + hr * 0.84, hy - hr * 0.30, hr * 0.18, hr * 0.62, 6, 6));
                break;
            case 1: // 长发
                g.setColor(hair);
                g.fill(new Arc2D.Double(hx - hr * 1.02, hy - hr * 0.98, hr * 2.04, hr * 1.6, 0, 180, Arc2D.CHORD));
                g.fill(new RoundRectangle2D.Double(hx - hr * 1.05, hy - hr * 0.10, hr * 0.34, hr * 1.25, 8, 8));
                g.fill(new RoundRectangle2D.Double(hx + hr * 0.71, hy - hr * 0.10, hr * 0.34, hr * 1.25, 8, 8));
                break;
            case 2: // 卷发
                g.setColor(hair);
                for (double a = 0; a <= Math.PI; a += Math.PI / 5) {
                    double bx = hx + Math.cos(a) * hr * 0.85;
                    double by = hy - Math.sin(a) * hr * 0.85;
                    g.fill(new Ellipse2D.Double(bx - hr * 0.34, by - hr * 0.34, hr * 0.68, hr * 0.68));
                }
                break;
            case 3: // 刺头
                g.setColor(hair);
                Path2D spikes = new Path2D.Double();
                for (int i = -3; i <= 3; i++) {
                    double bx = hx + i * hr * 0.34;
                    double by = hy - hr * 0.55;
                    spikes.moveTo(bx - hr * 0.16, hy - hr * 0.30);
                    spikes.lineTo(bx, by - hr * 0.55);
                    spikes.lineTo(bx + hr * 0.16, hy - hr * 0.30);
                }
                spikes.closePath();
                g.fill(spikes);
                g.fill(new Arc2D.Double(hx - hr, hy - hr * 0.55, hr * 2, hr * 1.1, 0, 180, Arc2D.CHORD));
                break;
            case 4: // 光头 + 胡子
                g.setColor(new Color(0x8D5524));
                g.fill(new Arc2D.Double(hx - hr * 0.98, hy - hr * 0.20, hr * 1.96, hr * 1.6, 180, 180, Arc2D.CHORD));
                g.setColor(hair);
                Path2D beard = new Path2D.Double();
                beard.moveTo(hx - hr * 0.5, hy - hr * 0.05);
                beard.quadTo(hx, hy + hr * 1.15, hx + hr * 0.5, hy - hr * 0.05);
                beard.quadTo(hx, hy + hr * 0.25, hx - hr * 0.5, hy - hr * 0.05);
                beard.closePath();
                g.fill(beard);
                break;
            default: // 帽子
                g.setColor(hair);
                g.fill(new Arc2D.Double(hx - hr * 1.02, hy - hr * 0.90, hr * 2.04, hr * 1.0, 0, 180, Arc2D.CHORD));
                g.fill(new RoundRectangle2D.Double(hx - hr * 0.62, hy - hr * 1.35, hr * 1.24, hr * 0.5, 8, 8));
                g.fill(new RoundRectangle2D.Double(hx - hr * 1.05, hy - hr * 0.72, hr * 2.1, hr * 0.16, 6, 6));
                break;
        }
    }
}
