package com.pool8;

import com.pool8.core.Table;
import com.pool8.net.NetGame;
import com.pool8.ui.GamePanel;

/** 双端联机冒烟测试：房主与客人各开一个 GamePanel，自动轮流击球并核对同步。 */
public final class NetSmokeTest {
    public static void main(String[] args) throws Exception {
        int port = 24791;
        GamePanel host = new GamePanel(null, GamePanel.MODE_HOST);
        host.setSize(1400, 860);
        NetGame hn = NetGame.host(port, "房主", host);
        host.setNet(hn);

        Thread.sleep(300);
        GamePanel client = new GamePanel(null, GamePanel.MODE_CLIENT);
        client.setSize(1400, 860);
        NetGame cn = NetGame.join("127.0.0.1", port, "挑战者", client);
        client.setNet(cn);

        long t0 = System.currentTimeMillis();
        while ((host.match() == null || client.match() == null) && System.currentTimeMillis() - t0 < 8000) {
            Thread.sleep(100);
        }
        if (host.match() == null || client.match() == null) {
            System.out.println("[FAIL] 未能建立联机对局 " + host.devPhase() + "/" + client.devPhase());
            System.exit(1);
        }
        System.out.println("联机建立：host=" + host.match().names[0] + " client=" + client.match().names[1]);

        int shots = 0;
        long lastAction = System.currentTimeMillis();
        long lastPrint = 0;
        while (host.match().winner < 0 && shots < 8 && System.currentTimeMillis() - t0 < 60_000) {
            Thread.sleep(150);
            if (System.currentTimeMillis() - lastPrint > 2500) {
                lastPrint = System.currentTimeMillis();
                System.out.printf("   [t=%4.1fs] host %s c=%d n=%d | client %s c=%d n=%d%n",
                        (System.currentTimeMillis() - t0) / 1000.0,
                        host.devPhase(), host.match().current, host.match().shotCount,
                        client.devPhase(), client.match().current, client.match().shotCount);
            }
            GamePanel cur = host.match().current == 0 ? host : client;
            if (!cur.devPhase().equals("AIM") && !cur.devPhase().equals("PLACING")) continue;
            if (System.currentTimeMillis() - lastAction < 800) continue;
            if (cur.match().mustCallPocket() && cur.match().calledPocket < 0) cur.devSetCalledPocket(0);
            cur.devPlayOneShot(0.62);
            lastAction = System.currentTimeMillis();
            shots++;
            System.out.println("shot " + shots + " by player " + host.match().current);
            // 等待这一杆在两端都结束，并且结果已同步
            long deadline = System.currentTimeMillis() + 25_000;
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(120);
                boolean busy = host.devPhase().equals("SHOOTING") || client.devPhase().equals("SHOOTING")
                        || host.match().shotCount != client.match().shotCount;
                if (!busy) break;
            }
            Thread.sleep(300);
            double maxDiff = 0;
            for (int i = 0; i < 16; i++) {
                var a = host.match().world.balls[i];
                var b = client.match().world.balls[i];
                if (a.active != b.active) maxDiff = 99;
                else if (a.active) maxDiff = Math.max(maxDiff, Math.hypot(a.x - b.x, a.y - b.y));
            }
            System.out.printf("   同步误差 %.6f m  host.current=%d client.current=%d%n",
                    maxDiff, host.match().current, client.match().current);
            if (maxDiff > 0.002) {
                System.out.println("[FAIL] 双方球位不同步");
                System.exit(1);
            }
            if (host.match().current != client.match().current) {
                System.out.println("[FAIL] 双方回合不同步");
                System.exit(1);
            }
            if (host.devPhase().equals("DISCONNECTED") || client.devPhase().equals("DISCONNECTED")) {
                System.out.println("[FAIL] 连接断开");
                System.exit(1);
            }
        }
        System.out.println("[PASS] 联机同步正常，完成 " + shots + " 杆，winner="
                + host.match().winner + "/" + client.match().winner);
        hn.close();
        cn.close();
        System.exit(0);
    }
}
