package com.pool8.rules;

import com.pool8.core.Ball;
import com.pool8.core.Shot;
import com.pool8.core.ShotTracker;
import com.pool8.core.World;

/** 标准八球规则判定核心（WPA 世界标准规则，略去“三犯”与跳球）。 */
public final class Rules {
    private Rules() {}

    public static boolean inGroup(int ballId, Group g) {
        if (g == Group.SOLIDS) return ballId >= 1 && ballId <= 7;
        if (g == Group.STRIPES) return ballId >= 9 && ballId <= 15;
        return false;
    }

    /** 统计某组台面上剩余的球数。 */
    public static int remaining(World w, Group g) {
        int n = 0;
        for (Ball b : w.balls) {
            if (b.active && inGroup(b.id, g)) n++;
        }
        return n;
    }

    /** 本杆开始前该组剩余球数（把本杆已落袋的球算回去）。 */
    private static int remainingBefore(World w, Group g, ShotTracker t) {
        int n = remaining(w, g);
        for (ShotTracker.Pot p : t.potted) {
            if (inGroup(p.ballId, g)) n++;
        }
        return n;
    }

    /**
     * @param groups 双方球组
     * @param openTable 是否开放球台
     * @param shooter 本杆击球者 0/1
     * @param breakShot 是否开球杆
     */
    public static Outcome evaluate(World w, Group[] groups, boolean openTable,
                                   int shooter, boolean breakShot, Shot shot) {
        Outcome o = new Outcome();
        ShotTracker t = w.tracker;
        o.firstHit = t.firstHit;
        o.eightPocket = t.pocketOf(8);

        // 开球：必须进球或至少 4 颗球碰库
        if (breakShot && !t.anyPotted() && t.railBalls.size() < 4) {
            o.foul("开球不合法（未进球且碰库球不足 4 颗）");
        }

        // 首碰合法性
        if (t.firstHit < 0) {
            o.foul("空杆：母球没有碰到任何目标球");
        } else if (openTable) {
            if (t.firstHit == 8) o.foul("开放球台不能首先击打 8 号球");
        } else {
            Group g = groups[shooter];
            if (remainingBefore(w, g, t) > 0) {
                if (!inGroup(t.firstHit, g)) {
                    o.foul("首先击中的不是自己的球（" + Ball.name(t.firstHit) + "）");
                }
            } else if (t.firstHit != 8) {
                o.foul("已清台，必须先击打 8 号球");
            }
        }

        if (t.cuePotted) o.foul("母球落袋");

        if (!t.cuePotted && !t.anyPotted() && !t.railAfterContact) {
            o.foul("击球后没有球碰到库边");
        }

        // 8 号球
        if (o.eightPocket >= 0) {
            if (breakShot) {
                o.respotEight = true;
                o.continueTurn = true;
            } else {
                boolean cleared = groups[shooter] != Group.OPEN
                        && remainingBefore(w, groups[shooter], t) == 0;
                boolean called = shot.calledPocket == o.eightPocket;
                boolean legal = !o.foul && cleared && called;
                o.winner = legal ? shooter : 1 - shooter;
                if (legal) o.reason = "8 号球入袋，本局胜利";
                else if (o.foul) o.reason = "犯规同时击落 8 号球";
                else if (!cleared) o.reason = "过早击落 8 号球";
                else o.reason = "8 号球未进入指定袋口";
                return o;
            }
        }

        // 开放球台：开球后球台开放；开球之后第一次合法进袋才决定球组
        if (openTable && !o.foul && !breakShot) {
            for (ShotTracker.Pot p : t.potted) {
                if (p.ballId == 0 || p.ballId == 8) continue;
                o.assigned = Group.of(p.ballId);
                break;
            }
        }

        if (openTable) {
            // 开放球台上任何一颗非 8 号球进袋都算“自己的球”，可以继续
            for (ShotTracker.Pot p : t.potted) {
                if (p.ballId == 0 || p.ballId == 8) continue;
                o.ownPotted++;
            }
        } else {
            Group eff = groups[shooter];
            for (ShotTracker.Pot p : t.potted) {
                if (p.ballId == 0 || p.ballId == 8) continue;
                if (inGroup(p.ballId, eff)) o.ownPotted++;
                else o.oppPotted++;
            }
        }

        if (!o.foul && o.ownPotted > 0) o.continueTurn = true;
        if (breakShot && !o.foul && t.anyPotted()) o.continueTurn = true;
        if (o.respotEight && !o.foul) o.continueTurn = true;
        return o;
    }

    public static Group other(Group g) {
        return g == Group.SOLIDS ? Group.STRIPES : Group.SOLIDS;
    }
}
