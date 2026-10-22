package com.pool8;

import com.pool8.core.Table;
import com.pool8.ui.GamePanel;

import java.awt.event.MouseEvent;

/** 用合成鼠标事件测试真实交互路径：摆放母球 -> 瞄准 -> 拖动蓄力 -> 出杆。 */
public final class InputSmokeTest {
    static int fails;

    public static void main(String[] args) throws Exception {
        GamePanel p = new GamePanel(null, GamePanel.MODE_LOCAL);
        p.setSize(1600, 900);
        p.startLocal(99);
        double[] lay = p.devLayout();
        int ax = (int) lay[0], ay = (int) lay[1];
        double sc = lay[2];
        int hx = (int) (ax + 0.50 * sc), hy = (int) (ay + Table.H / 2 * sc);

        check("初始为自由球摆位", p.devPhase().equals("PLACING"), p.devPhase());
        click(p, hx, hy);
        check("点击后进入瞄准", p.devPhase().equals("AIM"), p.devPhase());

        // 鼠标移到右侧 -> 向右瞄准
        move(p, hx + 200, hy);

        // 在母球处按下，向左拖动 150px 蓄力
        press(p, hx, hy);
        drag(p, hx - 150, hy);
        release(p, hx - 150, hy);
        Thread.sleep(200);
        check("松手后开始击球", p.devPhase().equals("SHOOTING") || p.match().shotCount > 0,
                p.devPhase() + " shots=" + p.match().shotCount);

        long t0 = System.currentTimeMillis();
        while (p.devPhase().equals("SHOOTING") && System.currentTimeMillis() - t0 < 30_000) {
            Thread.sleep(150);
        }
        check("一杆能正常结束", !p.devPhase().equals("SHOOTING"), p.devPhase());

        // 杆法圆盘
        int sx = (int) (1600 - lay[3] + 62), sy = 900 - 232;
        press(p, sx, sy);
        drag(p, sx + 18, sy - 22);
        release(p, sx + 18, sy - 22);
        check("点击杆法圆盘可设置塞", Math.abs(p.devSpinX()) > 0.05 && p.devSpinY() > 0.05,
                String.format("spin=%.2f,%.2f", p.devSpinX(), p.devSpinY()));

        System.out.println(fails == 0 ? "[PASS] 交互路径正常" : "[FAIL] " + fails + " 项");
        System.exit(fails == 0 ? 0 : 1);
    }

    static void check(String name, boolean ok, String detail) {
        System.out.println((ok ? "  [PASS] " : "  [FAIL] ") + name + "  " + detail);
        if (!ok) fails++;
    }

    static void click(GamePanel p, int x, int y) {
        press(p, x, y);
        release(p, x, y);
    }

    static void move(GamePanel p, int x, int y) {
        p.dispatchEvent(new MouseEvent(p, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, x, y, 0, false));
    }

    static void press(GamePanel p, int x, int y) {
        p.dispatchEvent(new MouseEvent(p, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, x, y, 1,
                false, MouseEvent.BUTTON1));
    }

    static void drag(GamePanel p, int x, int y) {
        p.dispatchEvent(new MouseEvent(p, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(),
                MouseEvent.BUTTON1_DOWN_MASK, x, y, 0, false, MouseEvent.BUTTON1));
    }

    static void release(GamePanel p, int x, int y) {
        p.dispatchEvent(new MouseEvent(p, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, x, y, 1,
                false, MouseEvent.BUTTON1));
    }
}
