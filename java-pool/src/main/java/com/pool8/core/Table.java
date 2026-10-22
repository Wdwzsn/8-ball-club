package com.pool8.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 九尺标准球台几何。台面（库边鼻线之间）2.54m x 1.27m。
 * 坐标系：左上角为原点，x 向右，y 向下（屏幕坐标一致）。
 */
public final class Table {
    public static final double W = 2.54;
    public static final double H = 1.27;
    public static final double R = 0.028575;

    /** 角袋处库边断口（沿库边到台角的距离）。 */
    public static final double CORNER_JAW = 0.079;
    /** 中袋处库边断口（中袋中心到断口的半宽）。 */
    public static final double SIDE_JAW = 0.067;

    /** 木框视觉宽度。 */
    public static final double RAIL_W = 0.095;

    public static final double HEAD_X = W * 0.25;   // 开球线
    public static final double FOOT_X = W * 0.75;   // 置球点

    /** 角袋捕获阈值：x+y 小于该值即落袋。 */
    private static final double CORNER_CAP = 0.098;
    private static final double SIDE_CAP_Y = R + 0.0015;

    /** 一条库边（有向线段 + 指向台内的法线）。 */
    public static final class Seg {
        public final double ax, ay, bx, by, nx, ny;
        public Seg(double ax, double ay, double bx, double by, double nx, double ny) {
            this.ax = ax; this.ay = ay; this.bx = bx; this.by = by;
            this.nx = nx; this.ny = ny;
        }
    }

    public static final class Pocket {
        public final int index;
        public final double x, y;
        public final boolean side;
        public Pocket(int index, double x, double y, boolean side) {
            this.index = index; this.x = x; this.y = y; this.side = side;
        }
        public String label() {
            if (side) return y < H / 2 ? "中袋" : "中袋";
            boolean left = x < W / 2, top = y < H / 2;
            return (top ? "上" : "下") + (left ? "左" : "右") + "角袋";
        }
    }

    public final List<Seg> rails = new ArrayList<>(8);
    public final Pocket[] pockets = new Pocket[6];

    public Table() {
        // 0 左上 1 右上 2 左下 3 右下 4 上中 5 下中
        pockets[0] = new Pocket(0, 0, 0, false);
        pockets[1] = new Pocket(1, W, 0, false);
        pockets[2] = new Pocket(2, 0, H, false);
        pockets[3] = new Pocket(3, W, H, false);
        pockets[4] = new Pocket(4, W / 2, 0, true);
        pockets[5] = new Pocket(5, W / 2, H, true);

        // 上库（法线朝下，即 +y）
        rails.add(new Seg(CORNER_JAW, 0, W / 2 - SIDE_JAW, 0, 0, 1));
        rails.add(new Seg(W / 2 + SIDE_JAW, 0, W - CORNER_JAW, 0, 0, 1));
        // 下库（法线朝上）
        rails.add(new Seg(CORNER_JAW, H, W / 2 - SIDE_JAW, H, 0, -1));
        rails.add(new Seg(W / 2 + SIDE_JAW, H, W - CORNER_JAW, H, 0, -1));
        // 左库（法线朝右）
        rails.add(new Seg(0, CORNER_JAW, 0, H - CORNER_JAW, 1, 0));
        // 右库（法线朝左）
        rails.add(new Seg(W, CORNER_JAW, W, H - CORNER_JAW, -1, 0));
    }

    /** 判断球是否落袋，返回袋口索引，未落袋返回 -1。 */
    public int pocketAt(double x, double y) {
        if (x + y < CORNER_CAP) return 0;
        if ((W - x) + y < CORNER_CAP) return 1;
        if (x + (H - y) < CORNER_CAP) return 2;
        if ((W - x) + (H - y) < CORNER_CAP) return 3;
        if (y <= SIDE_CAP_Y && Math.abs(x - W / 2) < SIDE_JAW) return 4;
        if (y >= H - SIDE_CAP_Y && Math.abs(x - W / 2) < SIDE_JAW) return 5;
        return -1;
    }

    /** 球心是否完全在台面内（含边距）。 */
    public boolean inside(double x, double y, double margin) {
        return x >= margin && x <= W - margin && y >= margin && y <= H - margin;
    }

    /** 是否处于袋口危险区（摆球时禁止）。 */
    public boolean nearPocket(double x, double y, double margin) {
        for (Pocket p : pockets) {
            if (Math.hypot(x - p.x, y - p.y) < 0.085 + margin) return true;
        }
        return false;
    }

    public static double clampX(double x, double m) { return Math.max(m, Math.min(W - m, x)); }
    public static double clampY(double y, double m) { return Math.max(m, Math.min(H - m, y)); }
}
