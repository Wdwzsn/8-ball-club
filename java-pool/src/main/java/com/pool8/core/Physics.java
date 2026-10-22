package com.pool8.core;

/**
 * 简化刚体台球物理。
 *
 * 采用“滑动 -> 滚动”两阶段摩擦模型：
 *  - 台呢接触点相对速度 u = v + ω × r，滑动摩擦使 u 以 3.5μg 的速率衰减；
 *  - 当 u 归零后进入纯滚动，滚动阻力使球减速；
 *  - 侧旋 wz 独立衰减，并在库边接触时产生切向“甩球”效果。
 */
public final class Physics {
    public static final double G = 9.81;
    public static final double MU_SLIDE = 0.19;
    public static final double MU_ROLL = 0.017;
    public static final double E_BALL = 0.95;
    public static final double E_CUSHION = 0.74;
    public static final double CUSHION_FRICTION = 0.17;
    public static final double SPIN_DECAY = 0.42;
    public static final double MAX_CUE_SPEED = 8.6;
    public static final double R = Table.R;

    private Physics() {}

    public static void strike(World w, Shot s) {
        Ball cue = w.balls[0];
        double speed = Math.max(0.12, s.power * MAX_CUE_SPEED);
        double dirx = Math.cos(s.angle), diry = Math.sin(s.angle);
        cue.vx = dirx * speed;
        cue.vy = diry * speed;
        // 高低杆：改变初始滚动角速度（1 = 自然滚动）
        double f = 1.0 + 1.45 * s.spinY;
        f = Math.max(-0.55, Math.min(2.2, f));
        cue.wx = -cue.vy / R * f;
        cue.wy = cue.vx / R * f;
        // 左右塞
        cue.wz = -s.spinX * 190.0 * Math.min(1.0, s.power);
    }

    /** 按时间推进，内部自动细分为不会穿透的子步。 */
    public static void advance(World w, double dt) {
        double maxV = 0;
        for (Ball b : w.balls) {
            if (!b.active) continue;
            maxV = Math.max(maxV, b.speed() + Math.abs(b.wz) * R);
        }
        if (maxV < 1e-9) return;
        int steps = (int) Math.ceil(dt * maxV / (R * 0.45));
        steps = Math.max(1, Math.min(60, steps));
        double h = dt / steps;
        for (int i = 0; i < steps; i++) step(w, h);
    }

    private static void step(World w, double h) {
        for (Ball b : w.balls) {
            if (!b.active) continue;
            friction(b, h);
            b.x += b.vx * h;
            b.y += b.vy * h;
        }
        collideBalls(w);
        collideRails(w);
        checkPockets(w);
        for (Ball b : w.balls) {
            if (!b.active) continue;
            if (Math.hypot(b.vx, b.vy) < 0.004 && Math.abs(b.wz) < 0.8) b.stop();
        }
    }

    private static void friction(Ball b, double h) {
        // 台呢接触点相对速度
        double ux = b.vx - R * b.wy;
        double uy = b.vy + R * b.wx;
        double us = Math.hypot(ux, uy);
        if (us > 1e-5) {
            double dec = MU_SLIDE * G * h;
            double desired = 3.5 * dec;             // u 的衰减量
            if (desired >= us) {
                // 本步内进入纯滚动
                double t = us / (3.5 * MU_SLIDE * G);
                double ax = -ux / us * MU_SLIDE * G;
                double ay = -uy / us * MU_SLIDE * G;
                b.vx += ax * t;
                b.vy += ay * t;
                b.wx = -b.vy / R;
                b.wy = b.vx / R;
            } else {
                double ax = -ux / us * MU_SLIDE * G;
                double ay = -uy / us * MU_SLIDE * G;
                double ux2 = ux + (-ux / us) * desired;
                double uy2 = uy + (-uy / us) * desired;
                b.vx += ax * h;
                b.vy += ay * h;
                b.wy = (b.vx - ux2) / R;
                b.wx = (uy2 - b.vy) / R;
            }
        } else {
            double sp = Math.hypot(b.vx, b.vy);
            if (sp > 1e-6) {
                double dec = MU_ROLL * G * h;
                if (dec >= sp) {
                    b.vx = 0; b.vy = 0;
                } else {
                    b.vx -= b.vx / sp * dec;
                    b.vy -= b.vy / sp * dec;
                }
            }
            b.wy = b.vx / R;
            b.wx = -b.vy / R;
        }
        b.wz *= Math.exp(-SPIN_DECAY * h);
    }

    private static void collideBalls(World w) {
        Ball[] bs = w.balls;
        for (int i = 0; i < bs.length; i++) {
            Ball a = bs[i];
            if (!a.active) continue;
            for (int j = i + 1; j < bs.length; j++) {
                Ball b = bs[j];
                if (!b.active) continue;
                double dx = b.x - a.x, dy = b.y - a.y;
                double d2 = dx * dx + dy * dy;
                if (d2 > 4 * R * R || d2 < 1e-12) continue;
                double d = Math.sqrt(d2);
                double nx = dx / d, ny = dy / d;
                double overlap = 2 * R - d;
                if (overlap > 0) {
                    a.x -= nx * overlap * 0.5;
                    a.y -= ny * overlap * 0.5;
                    b.x += nx * overlap * 0.5;
                    b.y += ny * overlap * 0.5;
                }
                double rvx = b.vx - a.vx, rvy = b.vy - a.vy;
                double vn = rvx * nx + rvy * ny;
                if (vn >= 0) continue;
                w.tracker.hit(a.id, b.id);
                double jimp = -(1 + E_BALL) * vn * 0.5;   // 等质量
                a.vx -= jimp * nx; a.vy -= jimp * ny;
                b.vx += jimp * nx; b.vy += jimp * ny;

                // 切向摩擦（侧旋造成的“甩球”）
                double tx = -ny, ty = nx;
                double vt = rvx * tx + rvy * ty + R * (a.wz + b.wz) * 0.12;
                double jt = Math.max(-0.10 * jimp, Math.min(0.10 * jimp, -vt * 0.035));
                a.vx -= jt * tx; a.vy -= jt * ty;
                b.vx += jt * tx; b.vy += jt * ty;
                if (Math.abs(a.wz) > 1 || Math.abs(b.wz) > 1) {
                    a.wz *= 0.96;
                    b.wz *= 0.96;
                }
                if (w.listener != null) w.listener.onBallHit(a, b, Math.abs(vn));
            }
        }
    }

    private static void collideRails(World w) {
        for (Ball b : w.balls) {
            if (!b.active) continue;
            for (Table.Seg s : w.table.rails) {
                double dx = s.bx - s.ax, dy = s.by - s.ay;
                double len2 = dx * dx + dy * dy;
                double t = ((b.x - s.ax) * dx + (b.y - s.ay) * dy) / len2;
                if (t <= 0 || t >= 1) continue;          // 袋口断口处不产生库边碰撞
                double cx = s.ax + t * dx, cy = s.ay + t * dy;
                double ddx = b.x - cx, ddy = b.y - cy;
                double dist = Math.hypot(ddx, ddy);
                if (dist >= R) continue;
                double nx, ny;
                if (dist > 1e-9) { nx = ddx / dist; ny = ddy / dist; }
                else { nx = s.nx; ny = s.ny; }
                if (nx * s.nx + ny * s.ny < 0) { nx = s.nx; ny = s.ny; }
                b.x += nx * (R - dist);
                b.y += ny * (R - dist);
                double vn = b.vx * nx + b.vy * ny;
                if (vn < 0) {
                    double tx = -ny, ty = nx;
                    double vt = b.vx * tx + b.vy * ty;
                    double kick = -0.085 * b.wz * R;
                    double vn2 = -E_CUSHION * vn;
                    double vt2 = vt * (1 - CUSHION_FRICTION) + kick;
                    b.vx = nx * vn2 + tx * vt2;
                    b.vy = ny * vn2 + ty * vt2;
                    b.wz *= 0.72;
                    w.tracker.rail(b.id);
                    if (w.listener != null) w.listener.onCushion(b, Math.abs(vn));
                }
            }
        }
    }

    private static void checkPockets(World w) {
        for (Ball b : w.balls) {
            if (!b.active) continue;
            // 极端情况下防止球飞出台面
            boolean out = b.x < -0.06 || b.x > Table.W + 0.06 || b.y < -0.06 || b.y > Table.H + 0.06;
            int p = w.table.pocketAt(b.x, b.y);
            if (p < 0 && out) p = nearestPocket(w, b);
            if (p >= 0) {
                b.active = false;
                b.pottedIn = p;
                b.stop();
                double px = w.table.pockets[p].x, py = w.table.pockets[p].y;
                b.x = px; b.y = py;
                w.tracker.pot(b.id, p);
                if (w.listener != null) w.listener.onPocket(b, p);
            }
        }
    }

    private static int nearestPocket(World w, Ball b) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (Table.Pocket p : w.table.pockets) {
            double d = Math.hypot(b.x - p.x, b.y - p.y);
            if (d < bd) { bd = d; best = p.index; }
        }
        return best;
    }
}
