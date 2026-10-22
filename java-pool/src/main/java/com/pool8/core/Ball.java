package com.pool8.core;

/**
 * 一颗台球。长度单位：米；速度：米/秒；角速度：弧度/秒。
 *
 * 旋转模型（自下而上的简化刚体模型）：
 *   u = (vx - R*wy, vy + R*wx)   —— 球体与台呢接触点的相对滑动速度
 *   wz                            —— 绕竖直轴的侧旋（塞）
 */
public final class Ball {
    public static final int CUE = 0;

    public final int id;
    public double x, y;
    public double vx, vy;
    public double wx, wy;   // 滚动角速度（与台面平行轴）
    public double wz;       // 侧旋
    public boolean active = true;
    public int pottedIn = -1;

    public Ball(int id) {
        this.id = id;
    }

    public boolean isCue()   { return id == 0; }
    public boolean isEight() { return id == 8; }
    public boolean isSolid() { return id >= 1 && id <= 7; }
    public boolean isStripe(){ return id >= 9 && id <= 15; }

    public double speed() {
        return Math.hypot(vx, vy);
    }

    /** 是否仍在运动（含旋转造成的滑动）。 */
    public boolean isMoving() {
        return active && (speed() > 0.0035 || Math.abs(wz) > 1.2);
    }

    public void stop() {
        vx = vy = wx = wy = wz = 0;
    }

    public void place(double nx, double ny) {
        x = nx; y = ny;
        stop();
        active = true;
        pottedIn = -1;
    }

    public Ball copy() {
        Ball b = new Ball(id);
        b.x = x; b.y = y;
        b.vx = vx; b.vy = vy;
        b.wx = wx; b.wy = wy; b.wz = wz;
        b.active = active;
        b.pottedIn = pottedIn;
        return b;
    }

    /** 标准球色（1-7 全色，8 黑，9-15 花色）。 */
    public int baseColor() {
        switch (id) {
            case 0: return 0xF6F3EA;
            case 1: case 9:  return 0xF0C027;
            case 2: case 10: return 0x1B4BA8;
            case 3: case 11: return 0xC4232C;
            case 4: case 12: return 0x5B2A8C;
            case 5: case 13: return 0xE5741B;
            case 6: case 14: return 0x0E7A40;
            case 7: case 15: return 0x7C2230;
            case 8: return 0x1A1B20;
            default: return 0xFFFFFF;
        }
    }

    public static String name(int id) {
        if (id == 0) return "母球";
        if (id == 8) return "8号球";
        return id + "号球";
    }
}
