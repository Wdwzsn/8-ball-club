package com.pool8;

import com.pool8.ui.GamePanel;

/** 无界面 UI/AI 流程冒烟测试：让电脑自我对弈一整局。 */
public final class UiSmokeTest {
    public static void main(String[] args) throws Exception {
        GamePanel p = new GamePanel(null, GamePanel.MODE_AI);
        p.setSize(1600, 900);
        p.startLocal(424242L);
        p.devSelfPlay();
        long t0 = System.currentTimeMillis();
        int lastShots = -1;
        while (p.match().winner < 0 && System.currentTimeMillis() - t0 < 240_000) {
            Thread.sleep(300);
            if (p.match().shotCount != lastShots) {
                lastShots = p.match().shotCount;
                System.out.println("shot " + lastShots + " current=" + p.match().current
                        + " groups=" + p.match().groups[0] + "/" + p.match().groups[1]
                        + " msg=" + p.match().message);
            }
        }
        System.out.println("winner=" + p.match().winner + " shots=" + p.match().shotCount
                + " score=" + p.match().score[0] + ":" + p.match().score[1]
                + " in " + (System.currentTimeMillis() - t0) + "ms");
        if (p.match().winner < 0) {
            System.out.println("[FAIL] 自我对弈未能结束");
            System.exit(1);
        }
        System.out.println("[PASS] UI/AI 流程正常");
        System.exit(0);
    }
}
