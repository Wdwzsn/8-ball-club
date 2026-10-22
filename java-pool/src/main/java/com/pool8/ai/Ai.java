package com.pool8.ai;

import com.pool8.core.Ball;
import com.pool8.core.Shot;
import com.pool8.core.Table;
import com.pool8.core.World;
import com.pool8.rules.Group;
import com.pool8.rules.Match;
import com.pool8.rules.Outcome;
import com.pool8.rules.Rules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 电脑对手：枚举“目标球 + 袋口”组合，用与真实比赛完全相同的物理引擎推演，
 * 再按“进球 / 犯规 / 走位”打分，取最优杆。难度只影响候选数量与瞄准误差。
 */
public final class Ai {
    public static final int EASY = 0, NORMAL = 1, HARD = 2;

    private static final double R = Table.R;

    private Ai() {}

    private static final class Cand {
        double cx, cy, angle, power;
        int target, pocket;
        double geom;
        Shot shot(double da, double pm) {
            Shot s = new Shot(cx, cy, angle + da, power * pm, 0, 0, pocket);
            return s;
        }
    }

    public static Shot choose(Match m, int level, Random rnd) {
        if (m.breakShot) return breakShot(m, rnd);
        List<Cand> cands = buildCandidates(m);
        cands.sort(Comparator.comparingDouble((Cand c) -> -c.geom));
        int limit = level == EASY ? 5 : level == NORMAL ? 10 : 18;
        if (cands.size() > limit) cands = cands.subList(0, limit);

        Shot best = null;
        double bestScore = -1e9;
        int bestTarget = 0, bestPocket = -1;
        int variants = level == EASY ? 1 : level == NORMAL ? 3 : 4;
        double[] offs = {0, 0.008, -0.008, 0.016, -0.016};
        double[] pms = {1.0, 1.35, 0.75};

        for (Cand c : cands) {
            for (int i = 0; i < variants; i++) {
                double da = offs[i];
                for (int k = 0; k < (level == HARD ? 3 : 2); k++) {
                    Shot s = c.shot(da, pms[k]);
                    double sc = simulateScore(m, s, c.target, c.pocket);
                    if (sc > bestScore) {
                        bestScore = sc;
                        best = s;
                        bestTarget = c.target;
                        bestPocket = c.pocket;
                    }
                }
            }
        }
        if (best == null || bestScore < -600) {
            Shot safety = safety(m, rnd, level);
            if (safety != null) return safety;
        }
        if (best == null) best = fallback(m);
        if (m.mustCallPocket() && best.calledPocket < 0) {
            best.calledPocket = nearestPocket(m, 8);
        }

        // 杆法精修：为最优球路尝试高低杆/左右塞，改善走位
        if (level != EASY && bestScore > -600) {
            double[][] spins = {
                    {0, 0}, {0, 0.6}, {0, -0.6}, {0.45, 0}, {-0.45, 0},
                    {0.35, 0.5}, {0.35, -0.5}, {-0.35, 0.5}, {-0.35, -0.5}
            };
            for (double[] sp : spins) {
                Shot s = best.copy();
                s.spinX = sp[0];
                s.spinY = sp[1];
                double sc = simulateScore(m, s, bestTarget, bestPocket);
                if (sc > bestScore + 6.0) {
                    bestScore = sc;
                    best = s;
                }
            }
        }

        // 难度误差
        double sigma = level == EASY ? 0.030 : level == NORMAL ? 0.010 : 0.0028;
        best.angle += rnd.nextGaussian() * sigma;
        best.power = clamp(best.power * (1 + rnd.nextGaussian() * (level == EASY ? 0.15 : 0.05)), 0.12, 1.0);
        return best;
    }

    private static Shot breakShot(Match m, Random rnd) {
        Ball apex = null;
        double bestX = 9;
        for (Ball b : m.world.balls) {
            if (!b.active || b.id == 0) continue;
            if (b.x < bestX) { bestX = b.x; apex = b; }
        }
        double cx = m.world.balls[0].x, cy = m.world.balls[0].y;
        if (m.ballInHand) { cx = Table.HEAD_X - 0.06; cy = Table.H / 2; }
        double angle = apex != null ? Math.atan2(apex.y - cy, apex.x - cx) : 0;
        angle += (rnd.nextDouble() - 0.5) * 0.012;
        return new Shot(cx, cy, angle, 0.93, 0, 0, -1);
    }

    private static List<Cand> buildCandidates(Match m) {
        World w = m.world;
        List<Cand> out = new ArrayList<>();
        List<Integer> targets = new ArrayList<>();
        if (m.openTable) {
            for (int i = 1; i <= 15; i++) if (i != 8 && w.balls[i].active) targets.add(i);
        } else {
            Group g = m.groups[m.current];
            for (int i = 1; i <= 15; i++) {
                if (w.balls[i].active && Rules.inGroup(i, g)) targets.add(i);
            }
            if (targets.isEmpty()) targets.add(8);
        }
        if (targets.isEmpty()) targets.add(8);

        Ball cue = w.balls[0];
        boolean bih = m.ballInHand || !cue.active;
        double cueX = cue.x, cueY = cue.y;

        for (int tid : targets) {
            Ball t = w.balls[tid];
            for (Table.Pocket pk : w.table.pockets) {
                double pdx = pk.x - t.x, pdy = pk.y - t.y;
                double pd = Math.hypot(pdx, pdy);
                if (pd < 1e-6) continue;
                double ux = pdx / pd, uy = pdy / pd;
                double gx = t.x - ux * 2 * R;
                double gy = t.y - uy * 2 * R;
                if (!clearPath(w, t.x, t.y, pk.x, pk.y, tid, 0)) continue;

                if (bih) {
                    double[] dists = {0.32, 0.52, 0.75};
                    for (double db : dists) {
                        double px = t.x - ux * (2 * R + db);
                        double py = t.y - uy * (2 * R + db);
                        if (!m.canPlaceCue(px, py)) {
                            px = Table.clampX(px, R + 0.01);
                            py = Table.clampY(py, R + 0.01);
                        }
                        if (!m.canPlaceCue(px, py)) continue;
                        if (!clearPath(w, px, py, gx, gy, tid, 0)) continue;
                        addCand(out, m, tid, pk.index, px, py, gx, gy, ux, uy, t, pk);
                    }
                } else {
                    if (!cue.active) continue;
                    if (Math.hypot(gx - cueX, gy - cueY) < 0.02) continue;
                    if (!clearPath(w, cueX, cueY, gx, gy, tid, 0)) continue;
                    addCand(out, m, tid, pk.index, cueX, cueY, gx, gy, ux, uy, t, pk);
                }
            }
        }
        return out;
    }

    private static void addCand(List<Cand> out, Match m, int tid, int pocket,
                                double cx, double cy, double gx, double gy,
                                double ux, double uy, Ball t, Table.Pocket pk) {
        double ax = gx - cx, ay = gy - cy;
        double ad = Math.hypot(ax, ay);
        if (ad < 0.02) return;
        double aimx = ax / ad, aimy = ay / ad;
        double cut = aimx * ux + aimy * uy;          // 1 = 正打
        if (cut < 0.18) return;
        double distT = Math.hypot(t.x - cx, t.y - cy);
        double distP = Math.hypot(pk.x - t.x, pk.y - t.y);
        Cand c = new Cand();
        c.cx = cx; c.cy = cy;
        c.angle = Math.atan2(ay, ax);
        c.power = clamp(0.26 + distT * 0.10 + distP * 0.11 + (1 - cut) * 0.25, 0.22, 0.95);
        c.target = tid;
        c.pocket = pocket;
        c.geom = cut * 2.0 - distT * 0.35 - distP * 0.55;
        out.add(c);
    }

    static boolean clearPath(World w, double x1, double y1, double x2, double y2,
                             int ignoreA, int ignoreB) {
        double dx = x2 - x1, dy = y2 - y1;
        double L2 = dx * dx + dy * dy;
        if (L2 < 1e-12) return true;
        for (Ball b : w.balls) {
            if (!b.active || b.id == ignoreA || b.id == ignoreB || b.id == 0) continue;
            double t = ((b.x - x1) * dx + (b.y - y1) * dy) / L2;
            if (t <= 0.02 || t >= 0.98) continue;
            double px = x1 + t * dx, py = y1 + t * dy;
            if (Math.hypot(b.x - px, b.y - py) < 2 * R * 0.96) return false;
        }
        return true;
    }

    /** 推演一杆并打分。 */
    public static double simulateScore(Match m, Shot s, int target, int pocket) {
        World sim = m.world.copy();
        sim.strike(s);
        sim.settle(14);
        Outcome o = Rules.evaluate(sim, m.groups, m.openTable, m.current, m.breakShot, s);
        double sc = 0;
        if (o.winner == m.current) sc += 6000;
        if (o.winner == 1 - m.current) sc -= 6000;
        if (o.foul) sc -= 500;
        sc += o.ownPotted * 150 - o.oppPotted * 45;
        if (sim.tracker.cuePotted) sc -= 300;
        if (target > 0 && sim.tracker.pocketOf(target) == pocket) sc += 90;
        if (target == 8 && !m.onEight(m.current) && sim.tracker.pocketOf(8) >= 0) sc -= 900;
        sc += leaveScore(m, sim, o) * 30;
        return sc;
    }

    /** 下一杆的难易程度（对己方为正，对对手为负）。 */
    private static double leaveScore(Match m, World sim, Outcome o) {
        int next = (o.continueTurn && !o.foul) ? m.current : 1 - m.current;
        Group g = m.groups[next];
        if (g == Group.OPEN) return 0;
        List<Integer> targets = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            if (sim.balls[i].active && Rules.inGroup(i, g)) targets.add(i);
        }
        if (targets.isEmpty()) targets.add(8);
        Ball cue = sim.balls[0];
        if (!cue.active) return -1;
        double best = 0;
        for (int tid : targets) {
            Ball t = sim.balls[tid];
            for (Table.Pocket pk : sim.table.pockets) {
                double dx = pk.x - t.x, dy = pk.y - t.y;
                double d = Math.hypot(dx, dy);
                if (d < 1e-6) continue;
                double gx = t.x - dx / d * 2 * R, gy = t.y - dy / d * 2 * R;
                double ax = gx - cue.x, ay = gy - cue.y;
                double ad = Math.hypot(ax, ay);
                if (ad < 0.02) continue;
                double cut = (ax / ad) * (dx / d) + (ay / ad) * (dy / d);
                if (cut < 0.35) continue;
                if (!clearPath(sim, cue.x, cue.y, gx, gy, tid, 0)) continue;
                if (!clearPath(sim, t.x, t.y, pk.x, pk.y, tid, 0)) continue;
                double q = cut * 1.2 - d * 0.5;
                if (q > best) best = q;
            }
        }
        return next == m.current ? best : -best;
    }

    /** 无法进球时，打一杆安全球。 */
    private static Shot safety(Match m, Random rnd, int level) {
        World w = m.world;
        List<Integer> targets = new ArrayList<>();
        if (m.openTable) {
            for (int i = 1; i <= 15; i++) if (i != 8 && w.balls[i].active) targets.add(i);
        } else {
            Group g = m.groups[m.current];
            for (int i = 1; i <= 15; i++) if (w.balls[i].active && Rules.inGroup(i, g)) targets.add(i);
            if (targets.isEmpty()) targets.add(8);
        }
        if (targets.isEmpty()) return fallback(m);
        Ball cue = w.balls[0];
        Shot best = null;
        double bestSc = -1e9;
        for (int tid : targets) {
            Ball t = w.balls[tid];
            double base = Math.atan2(t.y - cue.y, t.x - cue.x);
            for (double da : new double[]{0, 0.02, -0.02, 0.05, -0.05}) {
                for (double p : new double[]{0.28, 0.42, 0.6}) {
                    Shot s = new Shot(cue.x, cue.y, base + da, p, 0, 0, -1);
                    double sc = simulateScore(m, s, tid, -1);
                    sc -= Math.abs(da) * 100;
                    if (sc > bestSc) { bestSc = sc; best = s; }
                }
            }
        }
        if (best != null && level == EASY) best.angle += rnd.nextGaussian() * 0.03;
        return best;
    }

    private static Shot fallback(Match m) {
        Ball cue = m.world.balls[0];
        return new Shot(cue.x, cue.y, 0, 0.3, 0, 0, -1);
    }

    private static double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }

    private static int nearestPocket(Match m, int ballId) {
        Ball b = m.world.balls[ballId];
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (Table.Pocket p : m.world.table.pockets) {
            double d = Math.hypot(b.x - p.x, b.y - p.y);
            if (d < bd) { bd = d; best = p.index; }
        }
        return best;
    }
}
