package com.pool8.core;

/** 一次击球参数。 */
public final class Shot {
    public double x, y;        // 母球出手位置（自由球时由玩家摆放）
    public double angle;       // 击球方向（弧度）
    public double power;       // 0..1
    public double spinX;       // 左塞/右塞 -1..1
    public double spinY;       // 低杆/高杆 -1..1
    public int calledPocket = -1;

    public Shot() {}

    public Shot(double x, double y, double angle, double power, double spinX, double spinY, int calledPocket) {
        this.x = x; this.y = y;
        this.angle = angle; this.power = power;
        this.spinX = spinX; this.spinY = spinY;
        this.calledPocket = calledPocket;
    }

    public Shot copy() {
        return new Shot(x, y, angle, power, spinX, spinY, calledPocket);
    }

    @Override
    public String toString() {
        return String.format("Shot[p=(%.3f,%.3f) a=%.3f p=%.2f spin=(%.2f,%.2f) call=%d]",
                x, y, angle, power, spinX, spinY, calledPocket);
    }
}
