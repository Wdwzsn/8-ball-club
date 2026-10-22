package com.pool8.rules;

import com.pool8.core.Ball;
import com.pool8.core.Shot;
import com.pool8.core.Table;
import com.pool8.core.World;

/** 一局八球比赛的状态机。 */
public final class Match {
    public final World world;
    public final Group[] groups = {Group.OPEN, Group.OPEN};
    public final int[] score = new int[2];
    public final String[] names = {"玩家 1", "玩家 2"};
    public final boolean[] ai = new boolean[2];

    public int current = 0;
    public boolean openTable = true;
    public boolean ballInHand = true;
    public boolean kitchenOnly = true;
    public boolean breakShot = true;
    public int winner = -1;
    public int calledPocket = -1;
    public int shotCount;
    public long seed = 1;
    public boolean offerRerack;

    public String message = "";
    public String lastEvent = "";
    public final java.util.ArrayDeque<String> log = new java.util.ArrayDeque<>();

    public Match(long seed) {
        this.seed = seed;
        this.world = new World();
        newGame(seed);
    }

    public Match(World world, long seed) {
        this.seed = seed;
        this.world = world;
        newGame(seed);
    }

    public void newGame(long seed) {
        this.seed = seed;
        world.rack(seed);
        groups[0] = Group.OPEN;
        groups[1] = Group.OPEN;
        score[0] = score[1] = 0;
        openTable = true;
        current = 0;
        winner = -1;
        breakShot = true;
        ballInHand = true;
        kitchenOnly = true;
        calledPocket = -1;
        shotCount = 0;
        offerRerack = false;
        message = "开球：把母球放在开球线后，向后拖动球杆蓄力，松手击球";
        log.clear();
        addLog("新的一局开始");
    }

    public void addLog(String s) {
        lastEvent = s;
        log.addFirst(s);
        while (log.size() > 8) log.removeLast();
    }

    /** 重新摆球，由当前选手（犯规后的对手）开球。 */
    public void rerack(long newSeed) {
        int breaker = current;
        newGame(newSeed);
        current = breaker;
        offerRerack = false;
        addLog(names[breaker] + " 重新摆球开球");
    }

    public int opponent() { return 1 - current; }

    public Group groupOf(int player) { return groups[player]; }

    public boolean onEight(int player) {
        Group g = groups[player];
        return g != Group.OPEN && Rules.remaining(world, g) == 0;
    }

    public boolean mustCallPocket() {
        return winner < 0 && !breakShot && !openTable && onEight(current);
    }

    /** 合法母球摆放。 */
    public boolean canPlaceCue(double x, double y) {
        if (!world.table.inside(x, y, Table.R + 0.0015)) return false;
        if (kitchenOnly && x > Table.HEAD_X - 0.002) return false;
        if (world.table.nearPocket(x, y, 0.004)) return false;
        for (Ball b : world.balls) {
            if (!b.active || b.id == 0) continue;
            if (Math.hypot(b.x - x, b.y - y) < 2 * Table.R + 0.0012) return false;
        }
        return true;
    }

    public void placeCue(double x, double y) {
        Ball cue = world.balls[0];
        cue.active = true;
        cue.x = x;
        cue.y = y;
        cue.stop();
    }

    /** 把母球放回台面（开球点或最近空位）。 */
    public void ensureCueOnTable() {
        Ball cue = world.balls[0];
        if (cue.active) return;
        double[] p = findFreeSpot();
        cue.place(p[0], p[1]);
    }

    /** 在合法区域内找一个不与任何球重叠的母球位置。 */
    public double[] findFreeSpot() {
        double x0 = kitchenOnly ? Table.HEAD_X - 0.04 : Table.W / 2;
        double y0 = Table.H / 2;
        if (canPlaceCue(x0, y0)) return new double[]{x0, y0};
        for (double d = 0.03; d < 1.4; d += 0.03) {
            for (int sy = -1; sy <= 1; sy += 2) {
                double y = y0 + sy * d;
                if (y < Table.R + 0.005 || y > Table.H - Table.R - 0.005) continue;
                for (int sx = -1; sx <= 1; sx += 2) {
                    double x = x0 + sx * d;
                    if (x < Table.R + 0.005 || x > Table.W - Table.R - 0.005) continue;
                    if (canPlaceCue(x, y)) return new double[]{x, y};
                }
            }
        }
        return new double[]{Table.R + 0.01, Table.H / 2};
    }

    /** 8 号球重新摆放（置球点，被占则沿长轴后移）。 */
    public void respotEight() {
        Ball eight = world.balls[8];
        eight.active = true;
        eight.pottedIn = -1;
        eight.stop();
        double x = Table.FOOT_X, y = Table.H / 2;
        for (int i = 0; i < 80; i++) {
            double cand = Table.FOOT_X + i * 0.010;
            if (cand > Table.W - Table.R - 0.005) break;
            if (!overlaps(cand, y, eight)) { x = cand; break; }
        }
        eight.place(x, y);
    }

    private boolean overlaps(double x, double y, Ball self) {
        for (Ball b : world.balls) {
            if (!b.active || b == self) continue;
            if (Math.hypot(b.x - x, b.y - y) < 2 * Table.R + 0.0008) return true;
        }
        return false;
    }

    /** 击球前调用：记录叫袋。 */
    public void armShot(Shot shot) {
        shot.calledPocket = calledPocket;
        if (ballInHand) {
            placeCue(shot.x, shot.y);
        }
        ballInHand = false;
        kitchenOnly = false;
        world.strike(shot);
        shotCount++;
    }

    /** 所有球停下后调用，应用规则。 */
    public Outcome resolve(Shot shot) {
        Outcome o = Rules.evaluate(world, groups, openTable, current, breakShot, shot);
        int shooter = current;
        int other = 1 - shooter;

        if (o.assigned != null) {
            groups[shooter] = o.assigned;
            groups[other] = o.assigned.other();
            openTable = false;
            addLog(names[shooter] + " 选定 " + o.assigned.label);
        }
        score[shooter] += o.ownPotted;

        if (o.respotEight) {
            respotEight();
            addLog("开球击落 8 号球，8 号球重新摆放");
        }

        if (o.winner >= 0) {
            winner = o.winner;
            message = o.reason + " —— " + names[winner] + " 获胜！";
            addLog(message);
            return o;
        }

        if (o.foul) {
            current = other;
            ballInHand = true;
            kitchenOnly = breakShot;
            if (breakShot) offerRerack = true;
            ensureCueOnTable();
            message = "犯规：" + o.reason + " → " + names[current] + " 获得自由球";
            addLog(message);
        } else if (o.continueTurn) {
            message = (o.ownPotted > 0 ? "进球 +" + o.ownPotted + "，" : "")
                    + names[current] + " 继续击球";
            addLog(message);
        } else {
            current = other;
            message = names[current] + " 上场";
            addLog(message);
        }
        breakShot = false;
        calledPocket = -1;
        return o;
    }
}
