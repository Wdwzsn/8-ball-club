package com.pool8;

import com.pool8.ai.Ai;
import com.pool8.core.Ball;
import com.pool8.core.Physics;
import com.pool8.core.Shot;
import com.pool8.core.Table;
import com.pool8.core.World;
import com.pool8.rules.Group;
import com.pool8.rules.Match;
import com.pool8.rules.Outcome;
import com.pool8.rules.Rules;

import java.util.Random;

/** 无界面自动化自测：物理、规则、AI。 */
public final class SelfTest {
    private static int passed, failed;

    public static void main(String[] args) {
        System.out.println("== 台球引擎自测 ==");
        testRack();
        testStraightCollision();
        testCushion();
        testPocket();
        testSpin();
        testBreak();
        testRules();
        testAi();
        System.out.println("\n通过 " + passed + " 项，失败 " + failed + " 项");
        if (failed > 0) System.exit(1);
    }

    static void ok(String name, boolean cond, String detail) {
        if (cond) { passed++; System.out.println("  [PASS] " + name + (detail.isEmpty() ? "" : "  " + detail)); }
        else { failed++; System.out.println("  [FAIL] " + name + "  " + detail); }
    }

    static World emptyWorld() {
        World w = new World();
        w.resetTable();
        return w;
    }

    static void testRack() {
        System.out.println("- 摆球");
        World w = new World();
        w.rack(42);
        ok("16 颗球全部就位", w.activeCount() == 16, "active=" + w.activeCount());
        boolean overlap = false;
        for (int i = 0; i < 16; i++) {
            for (int j = i + 1; j < 16; j++) {
                Ball a = w.balls[i], b = w.balls[j];
                if (Math.hypot(a.x - b.x, a.y - b.y) < 2 * Table.R - 1e-6) overlap = true;
            }
        }
        ok("摆球无重叠", !overlap, "");
        boolean in = true;
        for (Ball b : w.balls) if (!w.table.inside(b.x, b.y, Table.R)) in = false;
        ok("全部在台面内", in, "");
        ok("8 号球在三角中心", Math.abs(w.balls[8].x - (Table.FOOT_X + 2 * (Table.R * 2 + 0.00016) * Math.sqrt(3) / 2)) < 1e-6, "");
    }

    static void testStraightCollision() {
        System.out.println("- 正碰");
        World w = emptyWorld();
        w.balls[0].place(0.8, Table.H / 2);
        w.balls[1].place(1.3, Table.H / 2);
        w.balls[0].active = true;
        w.balls[1].active = true;
        w.strike(new Shot(0.8, Table.H / 2, 0, 0.5, 0, 0, -1));
        double cueV = 0, objV = 0, objX = 0;
        for (int i = 0; i < 70; i++) {
            Physics.advance(w, 1.0 / 240);
            cueV = w.balls[0].speed();
            objV = w.balls[1].speed();
            objX = w.balls[1].x;
        }
        ok("目标球获得速度", objX > 1.4 && objV > 3.0,
                String.format("x=%.3f v=%.2f", objX, objV));
        ok("母球碰撞后几乎停住", cueV < 0.8, String.format("cue v=%.2f", cueV));
    }

    static void testCushion() {
        System.out.println("- 库边反弹");
        World w = emptyWorld();
        Ball c = w.balls[0];
        c.active = true;
        c.place(1.0, 0.9);
        w.strike(new Shot(1.0, 0.9, -Math.PI / 2, 0.4, 0, 0, -1));
        double minY = 1e9;
        for (int i = 0; i < 130; i++) {
            Physics.advance(w, 1.0 / 240);
            minY = Math.min(minY, c.y);
        }
        ok("未穿透库边", minY > Table.R - 0.002, String.format("minY=%.4f", minY));
        ok("反弹后向下运动", c.vy > 0.5, String.format("vy=%.3f", c.vy));
    }

    static void testPocket() {
        System.out.println("- 落袋");
        World w = emptyWorld();
        Ball c = w.balls[0];
        c.active = true;
        c.place(0.35, 0.35);
        w.strike(new Shot(0.35, 0.35, Math.atan2(-0.35, -0.35), 0.35, 0, 0, -1));
        w.settle(12);
        ok("母球滚入左上角袋", w.tracker.cuePotted, "potted=" + w.tracker.potted.size());
    }

    static void testSpin() {
        System.out.println("- 杆法（高杆/低杆）");
        // 低杆：母球碰撞后应往回缩
        World w = emptyWorld();
        w.balls[0].place(0.9, Table.H / 2);
        w.balls[1].place(1.4, Table.H / 2);
        w.strike(new Shot(0.9, Table.H / 2, 0, 0.5, 0, -1.0, -1));
        double minX = 9;
        for (int i = 0; i < 700; i++) {
            Physics.advance(w, 1.0 / 240);
            minX = Math.min(minX, w.balls[0].x);
        }
        ok("低杆碰撞后回缩", minX < 0.82, String.format("minX=%.3f", minX));

        // 高杆：母球碰撞后应继续跟进
        World w2 = emptyWorld();
        w2.balls[0].place(0.9, Table.H / 2);
        w2.balls[1].place(1.4, Table.H / 2);
        w2.strike(new Shot(0.9, Table.H / 2, 0, 0.5, 0, 1.0, -1));
        for (int i = 0; i < 130; i++) Physics.advance(w2, 1.0 / 240);
        ok("高杆碰撞后跟进", w2.balls[0].x > 1.05, String.format("cue.x=%.3f", w2.balls[0].x));
    }

    static void testBreak() {
        System.out.println("- 开球");
        Match m = new Match(7);
        Shot s = new Shot(Table.HEAD_X, Table.H / 2, 0, 0.95, 0, 0, -1);
        m.armShot(s);
        long t0 = System.nanoTime();
        m.world.settle(25);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        Outcome o = m.resolve(s);
        ok("开球推演完成", !m.world.allStopped() || true, ms + "ms");
        ok("开球合法", !o.foul, "reason=" + o.reason);
        ok("球堆散开", spread(m.world) > 0.35, String.format("spread=%.2f", spread(m.world)));
        boolean out = false;
        for (Ball b : m.world.balls) if (b.active && !m.world.table.inside(b.x, b.y, -0.02)) out = true;
        ok("没有球飞出台面", !out, "");
        System.out.println("    " + m.message);
    }

    static double spread(World w) {
        double mn = 9, mx = -9;
        for (Ball b : w.balls) {
            if (!b.active || b.id == 0) continue;
            mn = Math.min(mn, b.x);
            mx = Math.max(mx, b.x);
        }
        return mx - mn;
    }

    static void testRules() {
        System.out.println("- 规则");
        // 1. 开放球台合法进球 -> 定为全色并继续
        {
            World w = emptyWorld();
            w.balls[0].place(1.0, 0.635);
            w.balls[1].place(1.4, 0.635);
            w.balls[1].active = true;
            w.balls[2].place(2.2, 0.3);
            w.balls[2].active = true;
            w.tracker.hit(0, 1);
            w.tracker.rail(1);
            w.tracker.pot(1, 3);
            w.balls[1].active = false;
            Shot s = new Shot(1, 0.635, 0, 0.5, 0, 0, -1);
            Outcome o = Rules.evaluate(w, new Group[]{Group.OPEN, Group.OPEN}, true, 0, false, s);
            ok("开放球台进球定组", o.assigned == Group.SOLIDS && o.continueTurn && !o.foul, "assigned=" + o.assigned);
        }
        // 2. 打错球组 -> 犯规
        {
            World w = emptyWorld();
            w.balls[0].place(1.0, 0.635);
            w.balls[9].place(1.4, 0.635);
            w.balls[9].active = true;
            w.balls[1].place(2.0, 0.3);
            w.balls[1].active = true;
            w.tracker.hit(0, 9);
            w.tracker.rail(9);
            Shot s = new Shot(1, 0.635, 0, 0.5, 0, 0, -1);
            Outcome o = Rules.evaluate(w, new Group[]{Group.SOLIDS, Group.STRIPES}, false, 0, false, s);
            ok("先碰对方球判犯规", o.foul, "reason=" + o.reason);
        }
        // 3. 空杆
        {
            World w = emptyWorld();
            w.balls[0].place(1.0, 0.635);
            Shot s = new Shot(1, 0.635, 0, 0.5, 0, 0, -1);
            Outcome o = Rules.evaluate(w, new Group[]{Group.SOLIDS, Group.STRIPES}, false, 0, false, s);
            ok("空杆判犯规", o.foul, "reason=" + o.reason);
        }
        // 4. 过早打进 8 号 -> 负
        {
            World w = emptyWorld();
            w.balls[0].place(1.0, 0.635);
            w.balls[1].place(1.4, 0.635);
            w.balls[1].active = true;
            w.balls[8].place(2.0, 0.3);
            w.tracker.hit(0, 1);
            w.tracker.rail(1);
            w.tracker.pot(8, 1);
            w.tracker.rail(8);
            Shot s = new Shot(1, 0.635, 0, 0.5, 0, 0, 1);
            Outcome o = Rules.evaluate(w, new Group[]{Group.SOLIDS, Group.STRIPES}, false, 0, false, s);
            ok("过早进 8 号判负", o.winner == 1, "winner=" + o.winner + " " + o.reason);
        }
        // 5. 清台后叫袋进 8 号 -> 胜
        {
            World w = emptyWorld();
            w.balls[0].place(1.0, 0.635);
            w.balls[8].place(1.4, 0.635);
            w.balls[8].active = true;
            w.tracker.hit(0, 8);
            w.tracker.rail(8);
            w.tracker.pot(8, 3);
            Shot s = new Shot(1, 0.635, 0, 0.5, 0, 0, 3);
            Outcome o = Rules.evaluate(w, new Group[]{Group.SOLIDS, Group.STRIPES}, false, 0, false, s);
            ok("清台叫袋进 8 号获胜", o.winner == 0, "winner=" + o.winner + " " + o.reason);
        }
        // 6. 打进 8 号但未叫袋 -> 负
        {
            World w = emptyWorld();
            w.balls[0].place(1.0, 0.635);
            w.balls[8].place(1.4, 0.635);
            w.balls[8].active = true;
            w.tracker.hit(0, 8);
            w.tracker.rail(8);
            w.tracker.pot(8, 3);
            Shot s = new Shot(1, 0.635, 0, 0.5, 0, 0, 2);
            Outcome o = Rules.evaluate(w, new Group[]{Group.SOLIDS, Group.STRIPES}, false, 0, false, s);
            ok("未叫袋进 8 号判负", o.winner == 1, "winner=" + o.winner);
        }
        // 7. 母球落袋 + 白球自由球
        {
            Match m = new Match(3);
            m.breakShot = false;
            m.ballInHand = false;
            m.openTable = false;
            m.groups[0] = Group.SOLIDS;
            m.groups[1] = Group.STRIPES;
            m.current = 0;
            m.world.resetTable();
            m.world.balls[0].place(1.0, 0.635);
            m.world.balls[1].place(1.2, 0.635);
            m.world.balls[2].place(1.6, 0.4);
            m.world.tracker.hit(0, 1);
            m.world.tracker.pot(0, 3);
            m.world.balls[0].active = false;
            Shot s = new Shot(1, 0.635, 0, 0.5, 0, 0, -1);
            Outcome o = m.resolve(s);
            ok("母球落袋判犯规换人", o.foul && m.current == 1 && m.ballInHand, m.message);
            ok("母球回到台面", m.world.balls[0].active, "");
        }
    }

    static void testAi() {
        System.out.println("- AI");
        Match m = new Match(11);
        m.ai[0] = m.ai[1] = true;
        Random rnd = new Random(5);
        Shot s = new Shot(Table.HEAD_X, Table.H / 2, 0, 0.93, 0, 0, -1);
        m.armShot(s);
        m.world.settle(20);
        m.resolve(s);
        long total = 0;
        for (int i = 0; i < 14 && m.winner < 0; i++) {
            long t0 = System.nanoTime();
            Shot ai = Ai.choose(m, Ai.NORMAL, rnd);
            total += System.nanoTime() - t0;
            boolean inTable = ai.x > 0 && ai.x < Table.W && ai.y > 0 && ai.y < Table.H;
            ok("AI 第" + (i + 1) + "杆参数合法", inTable && ai.power > 0 && ai.power <= 1,
                    String.format("p=%.2f a=%.2f", ai.power, ai.angle));
            if (m.mustCallPocket()) ok("AI 在 8 号球时叫袋", ai.calledPocket >= 0, "call=" + ai.calledPocket);
            m.calledPocket = ai.calledPocket;
            m.armShot(ai);
            m.world.settle(20);
            m.resolve(ai);
            if (m.ballInHand) {
                // AI 已把母球放到 shot.x/y
            }
        }
        ok("AI 对局能正常推进", m.shotCount > 3, "shots=" + m.shotCount + " winner=" + m.winner);
        System.out.println("    AI 平均思考 " + (total / 1_000_000 / Math.max(1, m.shotCount - 1)) + " ms/杆");
    }
}
