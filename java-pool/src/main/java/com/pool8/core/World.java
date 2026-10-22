package com.pool8.core;

import java.util.Random;

/** 球、球台与击球过程的集合。 */
public final class World {
    public interface Listener {
        default void onBallHit(Ball a, Ball b, double impact) {}
        default void onCushion(Ball b, double impact) {}
        default void onPocket(Ball b, int pocket) {}
    }

    public final Table table = new Table();
    public final Ball[] balls = new Ball[16];
    public final ShotTracker tracker = new ShotTracker();
    public Listener listener;
    public long lastRackSeed = 1;

    public World() {
        for (int i = 0; i < 16; i++) balls[i] = new Ball(i);
        resetTable();
    }

    public Ball cue() { return balls[0]; }

    /** 把全部球移出台面，等待摆球。 */
    public void resetTable() {
        for (Ball b : balls) {
            b.active = false;
            b.pottedIn = -1;
            b.x = -1; b.y = -1;
            b.stop();
        }
    }

    /** 标准八球摆球。 */
    public void rack(long seed) {
        lastRackSeed = seed;
        resetTable();
        Random rnd = new Random(seed);
        // 打乱 1..15
        int[] ids = new int[15];
        int k = 0;
        for (int i = 1; i <= 15; i++) if (i != 8) ids[k++] = i;
        for (int i = ids.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = ids[i]; ids[i] = ids[j]; ids[j] = t;
        }
        // 后三角两角：一花一全
        int solidCorner = -1, stripeCorner = -1;
        for (int i = 0; i < ids.length; i++) {
            if (solidCorner < 0 && ids[i] <= 7) { solidCorner = ids[i]; ids[i] = -1; }
        }
        for (int i = 0; i < ids.length; i++) {
            if (stripeCorner < 0 && ids[i] >= 9) { stripeCorner = ids[i]; ids[i] = -1; }
        }
        int[] pool = new int[12];
        k = 0;
        for (int id : ids) if (id > 0) pool[k++] = id;

        double d = Table.R * 2 + 0.00016;
        double dx = d * Math.sqrt(3) / 2;
        int[][] order = new int[5][5];
        for (int[] row : order) java.util.Arrays.fill(row, 0);
        order[0][0] = pool[0];
        int pi = 1;
        for (int row = 1; row < 5; row++) {
            for (int j = 0; j <= row; j++) {
                if (row == 2 && j == 1) { order[row][j] = 8; continue; }
                if (row == 4 && j == 0) { order[row][j] = solidCorner; continue; }
                if (row == 4 && j == 4) { order[row][j] = stripeCorner; continue; }
                order[row][j] = pool[pi++];
            }
        }

        for (int row = 0; row < 5; row++) {
            for (int j = 0; j <= row; j++) {
                Ball b = balls[order[row][j]];
                b.active = true;
                b.pottedIn = -1;
                b.x = Table.FOOT_X + row * dx;
                b.y = Table.H / 2 + (j - row / 2.0) * d;
                b.stop();
            }
        }
        Ball cue = cue();
        cue.active = true;
        cue.place(Table.HEAD_X, Table.H / 2);
    }

    public int activeCount() {
        int n = 0;
        for (Ball b : balls) if (b.active) n++;
        return n;
    }

    public boolean allStopped() {
        for (Ball b : balls) {
            if (b.active && (b.speed() > 0.0035 || Math.abs(b.wz) > 1.2)) return false;
        }
        return true;
    }

    public void stopAll() {
        for (Ball b : balls) b.stop();
    }

    public World copy() {
        World w = new World();
        w.copyFrom(this);
        return w;
    }

    public void copyFrom(World o) {
        for (int i = 0; i < 16; i++) {
            Ball a = balls[i], b = o.balls[i];
            a.x = b.x; a.y = b.y;
            a.vx = b.vx; a.vy = b.vy;
            a.wx = b.wx; a.wy = b.wy; a.wz = b.wz;
            a.active = b.active;
            a.pottedIn = b.pottedIn;
        }
        lastRackSeed = o.lastRackSeed;
        tracker.reset();
        listener = null;
    }

    /** 应用一杆击球（设置母球位置与速度）。 */
    public void strike(Shot s) {
        tracker.reset();
        Ball cue = cue();
        cue.place(s.x, s.y);
        Physics.strike(this, s);
    }

    /** 无渲染快速推演到静止。返回耗时（秒）。 */
    public double settle(double maxSeconds) {
        double dt = 1.0 / 240.0;
        double t = 0;
        while (!allStopped() && t < maxSeconds) {
            Physics.advance(this, dt);
            t += dt;
            if (escaped()) break;
        }
        stopAll();
        return t;
    }

    private boolean escaped() {
        for (Ball b : balls) {
            if (!b.active) continue;
            if (b.x < -0.15 || b.x > Table.W + 0.15 || b.y < -0.15 || b.y > Table.H + 0.15) return true;
        }
        return false;
    }
}
