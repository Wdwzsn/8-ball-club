package com.pool8.ui;

import com.pool8.ai.Ai;
import com.pool8.audio.SoundFx;
import com.pool8.career.Career;
import com.pool8.core.Ball;
import com.pool8.core.Physics;
import com.pool8.core.Shot;
import com.pool8.core.Table;
import com.pool8.core.World;
import com.pool8.net.NetGame;
import com.pool8.net.Snapshot;
import com.pool8.rules.Group;
import com.pool8.rules.Match;
import com.pool8.rules.Rules;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

/** 球桌视图 + 交互 + HUD + 对局流程控制。 */
public final class GamePanel extends JPanel implements ActionListener, MouseListener, MouseMotionListener,
        KeyListener, NetGame.Listener {

    public static final int MODE_AI = 0, MODE_LOCAL = 1, MODE_HOST = 2, MODE_CLIENT = 3;
    public static final int PORT = NetGame.DEFAULT_PORT;

    private enum Phase { WAIT_PEER, PLACING, AIM, SHOOTING, AI_THINK, AI_AIM, NET_WAIT, RERACK_CHOICE, OVER, DISCONNECTED }

    private final MainFrame app;
    private final int mode;
    private int difficulty = Ai.NORMAL;
    private final SoundFx sound = SoundFx.get();
    private final Timer timer;
    private final Random rnd = new Random();

    private Match match;
    private NetGame net;
    private int myIndex = 0;

    private Phase phase = Phase.WAIT_PEER;
    private double aimAngle;
    private double power;
    private double spinX, spinY;
    private boolean dragging;
    private double dragSX, dragSY;
    private int mouseX, mouseY;
    private boolean mouseInTable;
    private double placeX, placeY;
    private boolean placingDragging;

    private boolean simulating;
    private double simTime;
    private long lastTick;
    private Shot activeShot;
    private double resultAt;
    private Shot pendingAiShot;
    private double aiAimStartNanos;
    private static final double AI_AIM_SECONDS = 2.0;
    private final java.util.List<double[]> pottedFx = new java.util.ArrayList<>();
    private long strikeNanos;
    private double firedPower;
    private boolean paused;
    private boolean volumeDragging;
    private final Rectangle2D volRect = new Rectangle2D.Double();
    private String toast = "";
    private double toastUntil;

    private boolean assist = true;
    private boolean soundOn = true;

    // 布局
    private int sidebarW;
    private double scale = 400, ox = 100, oy = 100;
    private BufferedImage base;
    private int baseW = -1, baseH = -1;

    // 按钮
    private final Rectangle2D[] btn = {new Rectangle2D.Double(), new Rectangle2D.Double(),
            new Rectangle2D.Double(), new Rectangle2D.Double()};
    private final Rectangle2D overRestart = new Rectangle2D.Double();
    private final Rectangle2D overMenu = new Rectangle2D.Double();
    private final Rectangle2D rerackRerack = new Rectangle2D.Double();
    private final Rectangle2D rerackAccept = new Rectangle2D.Double();
    private int hoverBtn = -1;

    // 网络
    private String netStatus = "";
    private Snapshot pendingResult;
    private boolean remoteShotQueued;

    public GamePanel(MainFrame app, int mode) {
        this.app = app;
        this.mode = mode;
        setPreferredSize(new Dimension(1600, 900));
        setBackground(Theme.BG_BOTTOM);
        setFocusable(true);
        addMouseListener(this);
        addMouseMotionListener(this);
        addKeyListener(this);
        timer = new Timer(15, this);
        timer.start();
    }

    public void setNet(NetGame net) {
        this.net = net;
        this.myIndex = net.myIndex();
        if (match != null && phase == Phase.PLACING && match.current != myIndex) {
            phase = Phase.NET_WAIT;
        }
    }

    public void setDifficulty(int d) { this.difficulty = d; }

    public void startLocal(long seed) {
        match = new Match(seed);
        match.names[0] = mode == MODE_AI ? "你" : "玩家 1";
        match.names[1] = mode == MODE_AI ? "电脑 · " + diffName() : "玩家 2";
        match.ai[1] = (mode == MODE_AI);
        wireWorld();
        phase = Phase.PLACING;
        initPlacement();
        aimAngle = 0;
        toast("开球：请把母球放在开球线后，向后方拖动球杆蓄力", 6);
        repaint();
    }

    private String diffName() {
        return difficulty == Ai.EASY ? "简单" : difficulty == Ai.HARD ? "高手" : "普通";
    }

    private void wireWorld() {
        match.world.listener = new World.Listener() {
            @Override
            public void onBallHit(Ball a, Ball b, double impact) {
                if (impact > 0.12) sound.ballHit(impact);
            }
            @Override
            public void onCushion(Ball b, double impact) {
                if (impact > 0.2) sound.railHit(impact);
            }
            @Override
            public void onPocket(Ball b, int pocket) {
                sound.potted();
                pottedFx.add(new double[]{b.id, pocket, System.nanoTime()});
            }
        };
    }

    public void dispose() {
        timer.stop();
        if (net != null) net.close();
    }

    private void toast(String s, double seconds) {
        toast = s;
        toastUntil = System.currentTimeMillis() / 1000.0 + seconds;
    }

    // ---------------------------------------------------------------- 布局

    private void ensureLayout(int w, int h) {
        sidebarW = (int) Math.max(268, Math.min(330, w * 0.20));
        int areaW = w - sidebarW;
        double pad = Math.max(12, Math.min(40, areaW * 0.022));
        double tw = Table.W + 2 * Table.RAIL_W;
        double th = Table.H + 2 * Table.RAIL_W;
        scale = Math.min((areaW - pad * 2) / tw, (h - pad * 2) / th);
        ox = (areaW - Table.W * scale) / 2.0;
        oy = (h - Table.H * scale) / 2.0;
        if (base == null || baseW != w || baseH != h) {
            base = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D bg = base.createGraphics();
            Art.paintTable(bg, ox, oy, scale);
            bg.dispose();
            baseW = w;
            baseH = h;
        }
    }

    private double sx(double wx) { return ox + wx * scale; }
    private double sy(double wy) { return oy + wy * scale; }
    private double wx(double screenX) { return (screenX - ox) / scale; }
    private double wy(double screenY) { return (screenY - oy) / scale; }

    // ---------------------------------------------------------------- 绘制

    @Override
    protected void paintComponent(Graphics gg) {
        super.paintComponent(gg);
        Graphics2D g = (Graphics2D) gg.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        int w = getWidth(), h = getHeight();
        ensureLayout(w, h);

        g.setPaint(new GradientPaint(0, 0, Theme.BG_TOP, 0, h, Theme.BG_BOTTOM));
        g.fillRect(0, 0, w, h);

        g.drawImage(base, 0, 0, null);

        if (match != null) {
            for (Ball b : match.world.balls) {
                if (!b.active) continue;
                if (b.id == 0 && phase == Phase.PLACING) continue;
                Art.drawBall(g, b, ox, oy, scale, 1.0);
            }
            drawPottedFx(g);
            if (phase == Phase.PLACING && humanTurn()) drawPlacement(g);
            if (match.mustCallPocket() || match.calledPocket >= 0) drawPocketGlow(g);
            if ((phase == Phase.AIM && humanTurn()) || phase == Phase.AI_AIM) {
                drawAim(g);
                drawCue(g);
            }
            if (phase == Phase.SHOOTING) {
                double age = (System.nanoTime() - strikeNanos) / 1e9;
                if (age < 0.16 && firedPower > 0) {
                    double t = age / 0.16;
                    drawCue(g, (0.018 + firedPower * 0.22) * (1 - t * 1.3));
                }
            }
            if (phase == Phase.OVER) drawOverlay(g);
            if (phase == Phase.RERACK_CHOICE) drawRerackChoice(g);
        }

        drawSidebar(g, w, h);
        drawPrompt(g, w, h);
        drawToast(g, w, h);
        if (phase == Phase.WAIT_PEER || phase == Phase.DISCONNECTED) drawWaitOverlay(g, w, h);

        g.dispose();
    }

    private void drawPocketGlow(Graphics2D g) {
        long t = System.currentTimeMillis();
        double pulse = 0.5 + 0.5 * Math.sin(t / 320.0);
        for (Table.Pocket p : match.world.table.pockets) {
            double x = sx(p.x), y = sy(p.y);
            double r = (p.side ? 0.063 : 0.062) * scale;
            boolean called = p.index == match.calledPocket;
            boolean need = match.mustCallPocket() && match.calledPocket < 0;
            if (!called && !need) continue;
            Color c = called ? new Color(0xD8B36A) : new Color(120, 235, 170);
            int a = called ? 210 : (int) (60 + 80 * pulse);
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), a));
            g.setStroke(new BasicStroke(called ? 2.6f : 1.5f));
            double rr = r * (called ? 1.55 : 1.35 + 0.12 * pulse);
            g.draw(new Ellipse2D.Double(x - rr, y - rr, rr * 2, rr * 2));
            if (called) {
                g.setColor(new Color(216, 179, 106, 60));
                g.fill(new Ellipse2D.Double(x - rr, y - rr, rr * 2, rr * 2));
            }
        }
    }

    private void drawAim(Graphics2D g) {
        if (!assist) return;
        Ball cue = match.world.cue();
        if (!cue.active) return;
        double dx = Math.cos(aimAngle), dy = Math.sin(aimAngle);
        double r = Table.R * scale;
        double cx = sx(cue.x), cy = sy(cue.y);
        double bestT = 1e9;
        int hitId = -1;
        double hx = 0, hy = 0;

        for (Ball b : match.world.balls) {
            if (!b.active || b.id == 0) continue;
            double ex = b.x - cue.x, ey = b.y - cue.y;
            double proj = ex * dx + ey * dy;
            if (proj <= 0) continue;
            double perp2 = ex * ex + ey * ey - proj * proj;
            double rr = 4 * Table.R * Table.R;
            if (perp2 > rr) continue;
            double t = proj - Math.sqrt(rr - perp2);
            if (t < bestT) { bestT = t; hitId = b.id; hx = cue.x + dx * t; hy = cue.y + dy * t; }
        }
        double tRail = 1e9;
        if (dx < -1e-9) tRail = Math.min(tRail, (Table.R - cue.x) / dx);
        if (dx > 1e-9) tRail = Math.min(tRail, (Table.W - Table.R - cue.x) / dx);
        if (dy < -1e-9) tRail = Math.min(tRail, (Table.R - cue.y) / dy);
        if (dy > 1e-9) tRail = Math.min(tRail, (Table.H - Table.R - cue.y) / dy);
        if (tRail < bestT) { bestT = tRail; hitId = -1; hx = cue.x + dx * tRail; hy = cue.y + dy * tRail; }

        if (bestT > 1e8) return;
        double gx = sx(hx), gy = sy(hy);

        g.setColor(new Color(255, 255, 255, 150));
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                new float[]{7f, 7f}, 0f));
        g.draw(new java.awt.geom.Line2D.Double(cx + dx * r, cy + dy * r, gx, gy));

        // 幽灵球
        g.setStroke(new BasicStroke(1.4f));
        g.setColor(new Color(255, 255, 255, 55));
        g.fill(new Ellipse2D.Double(gx - r, gy - r, r * 2, r * 2));
        g.setColor(new Color(255, 255, 255, 165));
        g.draw(new Ellipse2D.Double(gx - r, gy - r, r * 2, r * 2));

        if (hitId > 0) {
            Ball obj = match.world.balls[hitId];
            double nx = obj.x - hx, ny = obj.y - hy;
            double nl = Math.hypot(nx, ny);
            if (nl > 1e-6) { nx /= nl; ny /= nl; }
            double oX = sx(obj.x), oY = sy(obj.y);
            g.setColor(new Color(255, 236, 180, 115));
            g.setStroke(new BasicStroke(2.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new java.awt.geom.Line2D.Double(oX, oY,
                    oX + nx * 0.55 * scale, oY + ny * 0.55 * scale));
            // 母球分离方向
            double dot = dx * nx + dy * ny;
            double tx = dx - dot * nx, ty = dy - dot * ny;
            double tl = Math.hypot(tx, ty);
            if (tl > 0.02) {
                tx /= tl; ty /= tl;
                g.setColor(new Color(180, 235, 255, 70));
                g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new java.awt.geom.Line2D.Double(cx, cy,
                        cx + tx * 0.32 * scale, cy + ty * 0.32 * scale));
            }
        }
    }

    private void drawCue(Graphics2D g) {
        drawCue(g, 0.018 + power * 0.22);
    }

    private void drawCue(Graphics2D g, double pullMeters) {
        Ball cue = match.world.cue();
        if (!cue.active) return;
        double r = Table.R * scale;
        double pull = pullMeters * scale;
        double cx = sx(cue.x), cy = sy(cue.y);
        double dx = Math.cos(aimAngle), dy = Math.sin(aimAngle);
        double px = -dy, py = dx;
        double tipX = cx - dx * (r + pull);
        double tipY = cy - dy * (r + pull);
        double len = 1.42 * scale;
        double buttX = tipX - dx * len;
        double buttY = tipY - dy * len;

        Shape shaft = quad(tipX, tipY, buttX, buttY, px, py, 0.0045 * scale, 0.0115 * scale);
        Shape shadow = quad(tipX + 4, tipY + 5, buttX + 4, buttY + 5, px, py, 0.0045 * scale, 0.0115 * scale);
        g.setColor(new Color(0, 0, 0, 60));
        g.fill(shadow);

        g.setPaint(new LinearGradientPaint(new Point2D.Double(tipX, tipY), new Point2D.Double(buttX, buttY),
                new float[]{0f, 0.55f, 0.72f, 1f},
                new Color[]{new Color(0xE8C48C), new Color(0xB07A44), new Color(0x6B3B22), new Color(0x3A2014)}));
        g.fill(shaft);
        g.setColor(new Color(0, 0, 0, 110));
        g.setStroke(new BasicStroke(0.9f));
        g.draw(shaft);

        // 握把缠线
        double wrap0 = 0.16, wrap1 = 0.42;
        Shape grip = quad(tipX - dx * len * wrap0, tipY - dy * len * wrap0,
                tipX - dx * len * wrap1, tipY - dy * len * wrap1, px, py, 0.0095 * scale, 0.0110 * scale);
        g.setColor(new Color(0x1B1712));
        g.fill(grip);

        // 先角 + 皮头
        double fer0 = 0.012;
        Shape ferrule = quad(tipX, tipY, tipX - dx * len * fer0, tipY - dy * len * fer0,
                px, py, 0.0042 * scale, 0.0046 * scale);
        g.setColor(new Color(0xF2ECDC));
        g.fill(ferrule);
        shapeAt(g, tipX, tipY, dx, dy, px, py, 0.005 * scale, 0x3A78C8);

        // 后堵
        g.setColor(new Color(0x2A1A12));
        g.setStroke(new BasicStroke(2f));
        g.draw(new java.awt.geom.Line2D.Double(buttX + px * 0.011 * scale, buttY + py * 0.011 * scale,
                buttX - px * 0.011 * scale, buttY - py * 0.011 * scale));
    }

    private Shape quad(double x1, double y1, double x2, double y2,
                       double px, double py, double w1, double w2) {
        Path2D p = new Path2D.Double();
        p.moveTo(x1 + px * w1, y1 + py * w1);
        p.lineTo(x2 + px * w2, y2 + py * w2);
        p.lineTo(x2 - px * w2, y2 - py * w2);
        p.lineTo(x1 - px * w1, y1 - py * w1);
        p.closePath();
        return p;
    }

    private void shapeAt(Graphics2D g, double x, double y, double dx, double dy,
                         double px, double py, double w, int rgb) {
        Path2D p = new Path2D.Double();
        p.moveTo(x + dx * w + px * w * 0.8, y + dy * w + py * w * 0.8);
        p.lineTo(x + dx * w - px * w * 0.8, y + dy * w - py * w * 0.8);
        p.lineTo(x - px * w * 0.8, y - py * w * 0.8);
        p.lineTo(x + px * w * 0.8, y + py * w * 0.8);
        p.closePath();
        g.setColor(new Color(rgb));
        g.fill(p);
    }

    private void drawPottedFx(Graphics2D g) {
        long now = System.nanoTime();
        for (int i = pottedFx.size() - 1; i >= 0; i--) {
            double[] e = pottedFx.get(i);
            double age = (now - (long) e[2]) / 1e9;
            if (age > 0.45) { pottedFx.remove(i); continue; }
            double t = age / 0.45;
            int pocket = (int) e[1];
            Table.Pocket pk = match.world.table.pockets[pocket];
            double r = Table.R * scale * (1 - t * 0.85);
            if (r < 1) continue;
            Art.drawBallAt(g, (int) e[0], sx(pk.x), sy(pk.y), r, Math.max(0.0, 1 - t));
        }
    }

    private void drawPlacement(Graphics2D g) {
        Ball cue = match.world.cue();
        if (match.kitchenOnly) {
            double kx = sx(0), ky = sy(0);
            double kw = Table.HEAD_X * scale;
            g.setColor(new Color(255, 255, 255, 16));
            g.fill(new Rectangle2D.Double(kx, ky, kw, Table.H * scale));
            g.setColor(new Color(255, 255, 255, 40));
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                    new float[]{6f, 6f}, 0f));
            g.draw(new java.awt.geom.Line2D.Double(kx + kw, ky, kx + kw, ky + Table.H * scale));
        }
        boolean valid = match.canPlaceCue(placeX, placeY);
        double r = Table.R * scale;
        double x = sx(placeX), y = sy(placeY);
        if (valid) {
            Art.drawBallAt(g, 0, x, y, r, 0.85);
            g.setColor(new Color(120, 255, 180, 150));
            g.setStroke(new BasicStroke(1.6f));
            g.draw(new Ellipse2D.Double(x - r * 1.5, y - r * 1.5, r * 3, r * 3));
        } else {
            g.setColor(new Color(255, 90, 80, 70));
            g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
            g.setColor(new Color(255, 90, 80, 200));
            g.setStroke(new BasicStroke(1.8f));
            g.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
            g.draw(new java.awt.geom.Line2D.Double(x - r * 0.6, y - r * 0.6, x + r * 0.6, y + r * 0.6));
            g.draw(new java.awt.geom.Line2D.Double(x + r * 0.6, y - r * 0.6, x - r * 0.6, y + r * 0.6));
        }
    }

    // ---------------------------------------------------------------- HUD

    private void drawSidebar(Graphics2D g, int w, int h) {
        int x0 = w - sidebarW;
        g.setPaint(new GradientPaint(x0, 0, new Color(0x141D18), x0 + sidebarW, h, new Color(0x0A100D)));
        g.fillRect(x0, 0, sidebarW, h);
        g.setColor(new Color(255, 255, 255, 16));
        g.drawLine(x0, 0, x0, h);

        int pad = 20;
        g.setColor(Theme.GOLD);
        g.setFont(Theme.font(Font.BOLD, 24));
        g.drawString("8-BALL CLUB", x0 + pad, 42);
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(Font.PLAIN, 10.5));
        g.drawString("标准八球 · 拟真物理 · 绿色台呢", x0 + pad + 1, 60);
        g.setColor(new Color(255, 255, 255, 18));
        g.drawLine(x0 + pad, 74, x0 + sidebarW - pad, 74);

        String modeText = mode == MODE_AI ? "人机对战" : mode == MODE_LOCAL ? "双人同屏"
                : mode == MODE_HOST ? "联机 · 房主" : "联机 · 客人";
        g.setColor(new Color(0x223028));
        g.fill(new RoundRectangle2D.Double(x0 + pad, 84, 96, 22, 11, 11));
        g.setColor(Theme.GOLD_SOFT);
        g.setFont(Theme.font(Font.PLAIN, 10.5));
        g.drawString(modeText, x0 + pad + 12, 99);

        if (match != null) {
            drawPlayerCard(g, x0 + 14, 116, sidebarW - 28, 82, 0);
            drawPlayerCard(g, x0 + 14, 206, sidebarW - 28, 82, 1);
        }

        int logTop = 302;
        int logBottom = h - 312;
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(Font.BOLD, 10.5));
        g.drawString("战 报", x0 + pad, logTop - 10);
        g.setColor(new Color(255, 255, 255, 14));
        g.drawLine(x0 + pad, logTop - 3, x0 + sidebarW - pad, logTop - 3);

        if (match != null) {
            g.setFont(Theme.font(Font.PLAIN, 11));
            int y = logTop + 14;
            int shown = 0;
            for (String s : match.log) {
                if (y > logBottom) break;
                g.setColor(shown == 0 ? Theme.TEXT : Theme.MUTED);
                g.fillOval(x0 + pad, y - 6, 4, 4);
                g.drawString(clip(g, s, sidebarW - pad * 2 - 12), x0 + pad + 10, y);
                y += 34;
                shown++;
            }
        }

        drawSpin(g, x0 + 62, h - 232, 40);
        drawPower(g, x0 + 120, h - 258, sidebarW - 120 - 22);
        drawVolume(g, x0 + 120, h - 176, sidebarW - 120 - 22);

        int by = h - 108;
        int bw = (sidebarW - 28 - 10) / 2;
        drawButton(g, btn[0], x0 + 14, by, bw, 34, assist ? "辅助线 开" : "辅助线 关", hoverBtn == 0);
        drawButton(g, btn[1], x0 + 14 + bw + 10, by, bw, 34, soundOn ? "音效 开" : "音效 关", hoverBtn == 1);
        drawButton(g, btn[2], x0 + 14, by + 44, bw, 34, "重新开局", hoverBtn == 2);
        drawButton(g, btn[3], x0 + 14 + bw + 10, by + 44, bw, 34, "返回菜单", hoverBtn == 3);

        if (net != null) {
            g.setColor(new Color(0x8FA096));
            g.setFont(Theme.font(Font.PLAIN, 9.5));
            String s = net.status().isEmpty() ? "联机中" : net.status();
            g.drawString(clip(g, s, sidebarW - 40), x0 + 20, h - 16);
        }
    }

    private void drawPlayerCard(Graphics2D g, int x, int y, int w, int hgt, int p) {
        boolean active = match.current == p && match.winner < 0;
        Shape card = new RoundRectangle2D.Double(x, y, w, hgt, 14, 14);
        g.setColor(active ? new Color(0x24332A) : new Color(0x18211C));
        g.fill(card);
        if (active) {
            g.setColor(new Color(216, 179, 106, 220));
            g.fill(new RoundRectangle2D.Double(x, y + 12, 3, hgt - 24, 3, 3));
        }
        g.setColor(new Color(255, 255, 255, 18));
        g.setStroke(new BasicStroke(1f));
        g.draw(card);

        g.setColor(active ? Theme.GOLD : Theme.TEXT);
        g.setFont(Theme.font(Font.BOLD, 14));
        String name = match.names[p];
        g.drawString(clip(g, name, w - 96), x + 16, y + 26);
        if (p == myIndex && net != null) {
            int tw = g.getFontMetrics().stringWidth(clip(g, name, w - 96));
            g.setColor(Theme.GREEN);
            g.setFont(Theme.font(Font.PLAIN, 9));
            g.drawString("你", x + 22 + tw, y + 25);
        }

        Group grp = match.groups[p];
        String gl = grp == Group.OPEN ? "球组待定" : grp.label;
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(Font.PLAIN, 10));
        g.drawString(gl, x + 16, y + 44);

        g.setColor(Theme.GOLD);
        g.setFont(Theme.font(Font.BOLD, 22));
        String sc = String.valueOf(match.score[p]);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(sc, x + w - 18 - fm.stringWidth(sc), y + 34);
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(Font.PLAIN, 8.5));
        g.drawString("得分", x + w - 18 - 22, y + 45);

        // 剩余球
        int bx = x + 16;
        int by = y + 60;
        int cnt = 0;
        if (grp != Group.OPEN) {
            for (int id = 1; id <= 15; id++) {
                if (!match.world.balls[id].active || !Rules.inGroup(id, grp)) continue;
                if (cnt >= 7) break;
                Art.drawBallAt(g, id, bx + cnt * 16, by, 6.2, 1.0);
                cnt++;
            }
            if (cnt == 0) {
                g.setColor(Theme.GOLD);
                g.setFont(Theme.font(Font.BOLD, 10));
                g.drawString("已清台 → 8 号球", x + 16, by + 4);
            }
        } else {
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(Font.PLAIN, 9.5));
            g.drawString("开放球台，先进球定组", x + 16, by + 4);
        }
        if (match.ballInHand && active) {
            g.setColor(new Color(120, 255, 180, 200));
            g.setFont(Theme.font(Font.BOLD, 9));
            g.drawString("自由球", x + w - 56, y + 70);
        }
    }

    private void drawSpin(Graphics2D g, int cx, int cy, int r) {
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(Font.PLAIN, 10));
        g.drawString("杆法", cx - 14, cy - r - 12);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx - r * 0.3, cy - r * 0.35), r * 1.4f,
                new float[]{0f, 0.7f, 1f},
                new Color[]{new Color(0xFFFDF4), new Color(0xE8E2D0), new Color(0xA9A491)}));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.setColor(new Color(0, 0, 0, 90));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.setColor(new Color(0, 0, 0, 28));
        g.draw(new java.awt.geom.Line2D.Double(cx - r, cy, cx + r, cy));
        g.draw(new java.awt.geom.Line2D.Double(cx, cy - r, cx, cy + r));
        g.setColor(new Color(0xC0392B));
        double px = cx + spinX * r * 0.68, py = cy - spinY * r * 0.68;
        g.fill(new Ellipse2D.Double(px - 5, py - 5, 10, 10));
        g.setColor(new Color(255, 255, 255, 160));
        g.fill(new Ellipse2D.Double(px - 2, py - 2.4, 3, 3));
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(Font.PLAIN, 9));
        g.drawString(spinLabel(), cx - r, cy + r + 16);
    }

    private String spinLabel() {
        if (Math.abs(spinX) < 0.15 && Math.abs(spinY) < 0.15) return "中杆";
        StringBuilder sb = new StringBuilder();
        if (spinY > 0.2) sb.append("高杆");
        else if (spinY < -0.2) sb.append("低杆");
        if (spinX > 0.2) sb.append(sb.length() > 0 ? " · 右塞" : "右塞");
        else if (spinX < -0.2) sb.append(sb.length() > 0 ? " · 左塞" : "左塞");
        return sb.length() == 0 ? "中杆" : sb.toString();
    }

    private void drawPower(Graphics2D g, int x, int y, int w) {
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(Font.PLAIN, 10));
        g.drawString("力度", x, y - 8);
        String pct = Math.round(power * 100) + "%";
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(Font.BOLD, 10));
        g.drawString(pct, x + w - g.getFontMetrics().stringWidth(pct), y - 8);
        Shape bar = new RoundRectangle2D.Double(x, y, w, 12, 8, 8);
        g.setColor(new Color(0x0C1410));
        g.fill(bar);
        Shape fill = new RoundRectangle2D.Double(x + 2, y + 2, Math.max(0, (w - 4) * power), 8, 6, 6);
        g.setPaint(new LinearGradientPaint(new Point2D.Double(x, y), new Point2D.Double(x + w, y),
                new float[]{0f, 0.5f, 0.8f, 1f},
                new Color[]{new Color(0x3FBF7F), new Color(0xC9D34A), new Color(0xE8A33C), new Color(0xE05A4E)}));
        g.fill(fill);
        g.setColor(new Color(255, 255, 255, 40));
        g.setStroke(new BasicStroke(1f));
        g.draw(bar);
    }

    private void drawVolume(Graphics2D g, int x, int y, int w) {
        volRect.setRect(x, y, w, 12);
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(Font.PLAIN, 10));
        g.drawString("音量", x, y - 8);
        g.setColor(new Color(0x0C1410));
        g.fill(new RoundRectangle2D.Double(x, y, w, 12, 8, 8));
        double v = sound.getVolume();
        Shape fill = new RoundRectangle2D.Double(x + 2, y + 2, Math.max(0, (w - 4) * v), 8, 6, 6);
        g.setPaint(new LinearGradientPaint(new Point2D.Double(x, y), new Point2D.Double(x + w, y),
                new float[]{0f, 1f}, new Color[]{new Color(0x2A4A38), new Color(0x3FBF7F)}));
        g.fill(fill);
        g.setColor(new Color(255, 255, 255, 40));
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(x, y, w, 12, 8, 8));
    }

    private void drawButton(Graphics2D g, Rectangle2D rect, int x, int y, int w, int h, String label, boolean hover) {
        rect.setRect(x, y, w, h);
        Shape r = new RoundRectangle2D.Double(x, y, w, h, 10, 10);
        g.setColor(hover ? new Color(0x2C3A31) : new Color(0x1B251F));
        g.fill(r);
        g.setColor(new Color(255, 255, 255, 22));
        g.setStroke(new BasicStroke(1f));
        g.draw(r);
        g.setColor(hover ? Theme.TEXT : new Color(0xC9D3CB));
        g.setFont(Theme.font(Font.PLAIN, 11.5));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(label, (float) (x + (w - fm.stringWidth(label)) / 2.0), (float) (y + h / 2.0 + fm.getAscent() * 0.36));
    }

    private void drawPrompt(Graphics2D g, int w, int h) {
        if (match == null) return;
        String s;
        switch (phase) {
            case AIM: s = match.mustCallPocket() && match.calledPocket < 0
                    ? "8 号球必须叫袋：点击目标袋口" : "拖动球杆蓄力 · 松手击球（空格也可）"; break;
            case PLACING: s = "自由球：移动母球到合法位置，点击放置"; break;
            case SHOOTING: s = "击球中…"; break;
            case AI_THINK: s = "电脑正在计算球路…"; break;
            case AI_AIM: s = "电脑瞄准中…"; break;
            case NET_WAIT: s = "等待对手击球…"; break;
            case OVER: s = "本局结束"; break;
            default: s = "";
        }
        if (s.isEmpty()) return;
        int areaW = w - sidebarW;
        g.setFont(Theme.font(Font.PLAIN, 11.5));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(s);
        int bw = tw + 34;
        int bx = (areaW - bw) / 2, by = h - 40;
        g.setColor(new Color(0, 0, 0, 130));
        g.fill(new RoundRectangle2D.Double(bx, by, bw, 26, 13, 13));
        g.setColor(new Color(255, 255, 255, 26));
        g.draw(new RoundRectangle2D.Double(bx, by, bw, 26, 13, 13));
        g.setColor(new Color(0xE8E2D4));
        g.drawString(s, bx + 17, by + 17);
    }

    private void drawToast(Graphics2D g, int w, int h) {
        if (!toastActive()) return;
        int areaW = w - sidebarW;
        g.setFont(Theme.font(Font.BOLD, 13));
        FontMetrics fm = g.getFontMetrics();
        String s = clip(g, toast, areaW - 80);
        int tw = fm.stringWidth(s);
        int bw = tw + 48;
        int bx = (areaW - bw) / 2, by = 22;
        g.setColor(new Color(0, 0, 0, 150));
        g.fill(new RoundRectangle2D.Double(bx, by, bw, 34, 17, 17));
        g.setColor(new Color(216, 179, 106, 90));
        g.setStroke(new BasicStroke(1.1f));
        g.draw(new RoundRectangle2D.Double(bx, by, bw, 34, 17, 17));
        g.setColor(Theme.GOLD);
        g.drawString(s, bx + 24, by + 23);
    }

    private boolean toastActive() {
        return System.currentTimeMillis() / 1000.0 < toastUntil;
    }

    private void drawOverlay(Graphics2D g) {
        int w = getWidth(), h = getHeight();
        int areaW = w - sidebarW;
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, 0, areaW, h);
        int cw = 380, ch = 220;
        int cx = (areaW - cw) / 2, cy = (h - ch) / 2;
        Shape card = new RoundRectangle2D.Double(cx, cy, cw, ch, 18, 18);
        g.setColor(new Color(0x16201A));
        g.fill(card);
        g.setColor(new Color(216, 179, 106, 120));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(card);

        String title = match == null ? "" : match.names[match.winner] + " 获胜";
        g.setColor(Theme.GOLD);
        g.setFont(Theme.font(Font.BOLD, 24));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, cx + (cw - fm.stringWidth(title)) / 2, cy + 58);
        String sub;
        if (mode == MODE_AI) sub = match.winner == 0 ? "恭喜，你赢了！" : "很遗憾，你输了";
        else if (net != null) sub = match.winner == myIndex ? "你获胜！" : "你输了";
        else sub = "精彩对局！";
        g.setColor(mode == MODE_AI && match.winner != 0 ? Theme.RED : Theme.GOLD);
        g.setFont(Theme.font(Font.PLAIN, 12));
        g.drawString(sub, cx + (cw - g.getFontMetrics().stringWidth(sub)) / 2, cy + 84);

        int bw = 140, bh = 38;
        int bx = cx + 30, by = cy + 130;
        overRestart.setRect(bx, by, bw, bh);
        overMenu.setRect(bx + bw + 40, by, bw, bh);
        g.setColor(new Color(0x2A382E));
        g.fill(new RoundRectangle2D.Double(bx, by, bw, bh, 10, 10));
        g.fill(new RoundRectangle2D.Double(bx + bw + 40, by, bw, bh, 10, 10));
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(Font.BOLD, 13));
        g.drawString("再来一局", bx + 40, by + 24);
        g.drawString("返回菜单", bx + bw + 80, by + 24);
    }

    private void drawRerackChoice(Graphics2D g) {
        int w = getWidth(), h = getHeight();
        int areaW = w - sidebarW;
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, 0, areaW, h);
        int cw = 460, ch = 200;
        int cx = (areaW - cw) / 2, cy = (h - ch) / 2;
        Shape card = new RoundRectangle2D.Double(cx, cy, cw, ch, 18, 18);
        g.setColor(new Color(0x16201A));
        g.fill(card);
        g.setColor(new Color(216, 179, 106, 120));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(card);
        String title = "开球犯规";
        g.setColor(Theme.GOLD);
        g.setFont(Theme.font(Font.BOLD, 22));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, cx + (cw - fm.stringWidth(title)) / 2, cy + 46);
        String sub = match.names[match.current] + " 可选择：";
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(Font.PLAIN, 12));
        g.drawString(sub, cx + (cw - g.getFontMetrics().stringWidth(sub)) / 2, cy + 72);
        int bw = 200, bh = 38;
        int bx = cx + 24, by = cy + 110;
        rerackRerack.setRect(bx, by, bw, bh);
        rerackAccept.setRect(bx + bw + 12, by, bw, bh);
        g.setColor(new Color(0x2A382E));
        g.fill(new RoundRectangle2D.Double(bx, by, bw, bh, 10, 10));
        g.fill(new RoundRectangle2D.Double(bx + bw + 12, by, bw, bh, 10, 10));
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(Font.BOLD, 12.5));
        g.drawString("重新摆球（我开球）", bx + 26, by + 24);
        g.drawString("接受球局（自由球）", bx + bw + 38, by + 24);
    }

    private void rerack() {
        match.rerack(System.nanoTime());
        phase = Phase.PLACING;
        initPlacement();
        aimAngle = 0;
        toast("重新摆球，你开球", 3);
    }

    private void acceptTable() {
        match.offerRerack = false;
        phase = Phase.PLACING;
        initPlacement();
        toast("自由球：在开球线后摆放母球", 3);
    }

    private void drawWaitOverlay(Graphics2D g, int w, int h) {
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(0, 0, w, h);
        String title;
        if (phase == Phase.DISCONNECTED) title = "连接已断开";
        else if (mode == MODE_CLIENT) title = "正在连接房主…";
        else title = "等待对手连接…";
        g.setColor(Theme.GOLD);
        g.setFont(Theme.font(Font.BOLD, 26));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, (w - fm.stringWidth(title)) / 2, h / 2 - 40);

        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(Font.PLAIN, 13));
        if (phase == Phase.WAIT_PEER && mode != MODE_CLIENT) {
            String ip = "本机 IP：" + NetGame.localAddress() + "    端口：" + PORT;
            g.drawString(ip, (w - g.getFontMetrics().stringWidth(ip)) / 2, h / 2 + 6);
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(Font.PLAIN, 11.5));
            String tip = "让好友在“加入房间”中输入上面的 IP 与端口（按 Esc 取消）";
            g.drawString(tip, (w - g.getFontMetrics().stringWidth(tip)) / 2, h / 2 + 32);
        } else {
            String msg = netStatus.isEmpty() ? (phase == Phase.DISCONNECTED ? "对手已离开" : "请稍候…") : netStatus;
            g.drawString(msg, (w - g.getFontMetrics().stringWidth(msg)) / 2, h / 2 + 6);
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(Font.PLAIN, 11.5));
            String tip = phase == Phase.DISCONNECTED ? "按 Esc 返回主菜单" : "按 Esc 取消";
            g.drawString(tip, (w - g.getFontMetrics().stringWidth(tip)) / 2, h / 2 + 32);
        }
    }

    private String clip(Graphics2D g, String s, int max) {
        if (s == null) return "";
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(s) <= max) return s;
        String ell = "…";
        int i = s.length();
        while (i > 1 && fm.stringWidth(s.substring(0, i) + ell) > max) i--;
        return s.substring(0, i) + ell;
    }

    // ---------------------------------------------------------------- 交互

    @Override
    public void actionPerformed(ActionEvent e) {
        long now = System.nanoTime();
        double dt = lastTick == 0 ? 1.0 / 60.0 : (now - lastTick) / 1e9;
        lastTick = now;
        dt = Math.max(0.002, Math.min(0.05, dt));
        if (match != null && simulating && !paused) {
            Physics.advance(match.world, dt);
            simTime += dt;
            if (match.world.allStopped() || simTime > 32) {
                finishShot();
            }
        } else if (phase == Phase.AI_AIM && pendingAiShot != null) {
            double el = (System.nanoTime() - aiAimStartNanos) / 1e9;
            power = pendingAiShot.power * (float) Math.min(1.0, el / AI_AIM_SECONDS);
            if (el >= AI_AIM_SECONDS) {
                Shot s = pendingAiShot;
                pendingAiShot = null;
                applyLocalShot(s);
            }
        }
        repaint();
    }

    private boolean humanTurn() {
        if (match == null || match.winner >= 0) return false;
        if (match.ai[match.current]) return false;
        if (net != null) return match.current == myIndex;
        return true;
    }

    private boolean myTurn() {
        if (match == null) return false;
        if (net != null) return match.current == myIndex;
        return true;
    }

    private void initPlacement() {
        Ball cue = match.world.cue();
        if (!cue.active) match.ensureCueOnTable();
        cue = match.world.cue();
        placeX = cue.x;
        placeY = cue.y;
        if (!match.canPlaceCue(placeX, placeY)) {
            placeX = Math.min(Table.HEAD_X - 0.12, Table.W / 2);
            placeY = Table.H / 2;
            for (int i = 0; i < 40 && !match.canPlaceCue(placeX, placeY); i++) {
                placeX = Math.max(Table.R + 0.02, placeX - 0.02);
            }
        }
        if (match.ballInHand && match.canPlaceCue(placeX, placeY)) {
            match.placeCue(placeX, placeY);
        }
    }

    private void startAi() {
        phase = Phase.AI_THINK;
        final Match mm = match;
        SwingWorker<Shot, Void> w = new SwingWorker<>() {
            @Override
            protected Shot doInBackground() {
                return Ai.choose(mm, difficulty, new Random());
            }
            @Override
            protected void done() {
                if (phase != Phase.AI_THINK) return;
                try {
                    Shot s = get();
                    if (s == null) { phase = Phase.AIM; return; }
                    pendingAiShot = s;
                    aimAngle = s.angle;
                    spinX = s.spinX;
                    spinY = s.spinY;
                    power = 0;
                    if (match.ballInHand && match.canPlaceCue(s.x, s.y)) {
                        match.placeCue(s.x, s.y);
                    }
                    aiAimStartNanos = System.nanoTime();
                    phase = Phase.AI_AIM;
                } catch (Exception ex) {
                    phase = Phase.AIM;
                }
            }
        };
        w.execute();
    }

    private void requestShot(Shot s) {
        if (match.mustCallPocket() && s.calledPocket < 0) {
            s.calledPocket = match.calledPocket;
        }
        if (s.calledPocket < 0) s.calledPocket = -1;
        if (net != null) {
            if (net.isHost()) {
                net.broadcastShot(s, match);
                applyLocalShot(s);
            } else {
                net.sendShotToHost(s, match);
                phase = Phase.NET_WAIT;
                toast("等待房主确认击球…", 2);
            }
            return;
        }
        applyLocalShot(s);
    }

    private void applyLocalShot(Shot s) {
        if (match.winner >= 0) return;
        match.calledPocket = s.calledPocket;
        if (match.ballInHand && !match.canPlaceCue(s.x, s.y)) {
            double[] p = match.findFreeSpot();
            s.x = p[0];
            s.y = p[1];
            match.placeCue(s.x, s.y);
        }
        double p = s.power;
        match.armShot(s);
        activeShot = s.copy();
        simulating = true;
        simTime = 0;
        phase = Phase.SHOOTING;
        power = 0;
        strikeNanos = System.nanoTime();
        firedPower = p;
        sound.cueStrike(p);
        toast("", 0.01);
        repaint();
    }

    private void finishShot() {
        simulating = false;
        match.world.stopAll();
        if (net != null && !net.isHost()) {
            phase = Phase.NET_WAIT;
            if (pendingResult != null) {
                Snapshot s = pendingResult;
                pendingResult = null;
                s.applyTo(match);
                if (match.winner >= 0) {
                    phase = Phase.OVER;
                    playGameEndSound();
                    toast(match.message, 6);
                } else {
                    afterShot();
                    toast(match.message, 5);
                }
            }
            return;
        }
        boolean wasBreak = match.breakShot;
        var o = match.resolve(activeShot);
        resultAt = System.currentTimeMillis() / 1000.0;
        toast(match.message, 5);
        if (mode == MODE_AI) {
            Career c = Career.get();
            if (wasBreak && o.ownPotted > 0) c.addBreakPot();
            if (o.ownPotted > 0) c.addPotted(o.ownPotted);
            if (match.winner >= 0) c.recordAiGame(match.winner == 0);
        }
        if (net != null && net.isHost()) net.sendResult(match);
        if (match.winner >= 0) {
            phase = Phase.OVER;
            playGameEndSound();
            repaint();
            return;
        }
        afterShot();
    }

    private void afterShot() {
        if (match.winner >= 0) { phase = Phase.OVER; return; }
        if (match.ballInHand && humanTurn()) {
            if (match.offerRerack) {
                phase = Phase.RERACK_CHOICE;
            } else {
                phase = Phase.PLACING;
                initPlacement();
            }
        } else if (match.ballInHand) {
            match.offerRerack = false;
            phase = Phase.AIM;
        } else {
            phase = Phase.AIM;
        }
        aimAngle = defaultAim();
        if (match.ai[match.current]) {
            startAi();
        } else if (net != null && match.current != myIndex) {
            phase = Phase.NET_WAIT;
        }
        repaint();
    }

    private double defaultAim() {
        Ball cue = match.world.cue();
        if (!cue.active) return 0;
        Ball best = null;
        double bd = 1e9;
        Group g = match.groups[match.current];
        for (Ball b : match.world.balls) {
            if (!b.active || b.id == 0) continue;
            if (match.openTable && b.id == 8) continue;
            if (!match.openTable && !Rules.inGroup(b.id, g)) continue;
            double d = Math.hypot(b.x - cue.x, b.y - cue.y);
            if (d < bd) { bd = d; best = b; }
        }
        if (best == null) return 0;
        return Math.atan2(best.y - cue.y, best.x - cue.x);
    }

    private void playGameEndSound() {
        if (mode == MODE_AI) {
            if (match.winner == 0) sound.win(); else sound.lose();
        } else if (net != null) {
            if (match.winner == myIndex) sound.win(); else sound.lose();
        } else {
            sound.win();
        }
    }

    private void shoot() {
        if (match == null || !humanTurn() || phase != Phase.AIM) return;
        if (match.mustCallPocket() && match.calledPocket < 0) {
            toast("8 号球必须叫袋：先点击目标袋口", 3);
            return;
        }
        if (power < 0.03) {
            toast("向后方拖动球杆蓄力", 2);
            return;
        }
        Ball cue = match.world.cue();
        Shot s = new Shot(cue.x, cue.y, aimAngle, Math.max(0.08, power), spinX, spinY, match.calledPocket);
        requestShot(s);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        mouseX = e.getX();
        mouseY = e.getY();
        if (phase == Phase.RERACK_CHOICE) {
            sound.click();
            if (rerackRerack.contains(mouseX, mouseY)) rerack();
            else if (rerackAccept.contains(mouseX, mouseY)) acceptTable();
            return;
        }
        if (phase == Phase.OVER) {
            sound.click();
            if (overRestart.contains(mouseX, mouseY)) {
                restart();
            } else if (overMenu.contains(mouseX, mouseY)) {
                app.showMenu();
            }
            return;
        }
        if (phase == Phase.WAIT_PEER || phase == Phase.DISCONNECTED) return;
        if (match == null) return;

        // 侧栏
        if (mouseX >= getWidth() - sidebarW) {
            if (btn[0].contains(mouseX, mouseY)) { sound.click(); assist = !assist; return; }
            if (btn[1].contains(mouseX, mouseY)) { sound.click(); soundOn = !soundOn; sound.setEnabled(soundOn); return; }
            if (btn[2].contains(mouseX, mouseY)) { sound.click(); restart(); return; }
            if (btn[3].contains(mouseX, mouseY)) { sound.click(); app.showMenu(); return; }
            int spinCx = getWidth() - sidebarW + 62;
            int spinCy = getHeight() - 232;
            if (Math.hypot(mouseX - spinCx, mouseY - spinCy) < 48) {
                sound.click();
                placingDragging = true;
                setSpin(mouseX - spinCx, mouseY - spinCy, 40);
                return;
            }
            if (volRect.contains(mouseX, mouseY)) {
                sound.click();
                volumeDragging = true;
                setVolumeFrom(mouseX);
                return;
            }
            return;
        }

        if (!humanTurn()) return;

        if (phase == Phase.PLACING) {
            if (inTable(mouseX, mouseY)) {
                double wxx = wx(mouseX), wyy = wy(mouseY);
                if (match.canPlaceCue(wxx, wyy)) {
                    sound.click();
                    match.placeCue(wxx, wyy);
                    phase = Phase.AIM;
                    aimAngle = defaultAim();
                    toast("母球已就位", 1.5);
                } else {
                    toast("这个位置不合法", 1.5);
                }
            }
            return;
        }

        if (phase == Phase.AIM) {
            // 叫袋
            if (match.mustCallPocket()) {
                int pk = pocketNear(mouseX, mouseY, 34);
                if (pk >= 0) {
                    sound.click();
                    match.calledPocket = pk;
                    toast("已叫袋：" + match.world.table.pockets[pk].label(), 2);
                    return;
                }
            }
            dragging = true;
            dragSX = mouseX;
            dragSY = mouseY;
            power = 0;
        }
    }

    private void setSpin(double dx, double dy, int r) {
        // 圆盘上方 = 高杆（+spinY），下方 = 低杆
        spinX = Math.max(-1, Math.min(1, dx / (r * 0.68)));
        spinY = Math.max(-1, Math.min(1, -dy / (r * 0.68)));
        double l = Math.hypot(spinX, spinY);
        if (l > 1) { spinX /= l; spinY /= l; }
    }

    private void setVolumeFrom(int mx) {
        double v = (mx - volRect.getX()) / volRect.getWidth();
        sound.setVolume((float) Math.max(0, Math.min(1, v)));
    }

    private int pocketNear(double x, double y, double rad) {
        int best = -1;
        double bd = rad;
        for (Table.Pocket p : match.world.table.pockets) {
            double d = Math.hypot(sx(p.x) - x, sy(p.y) - y);
            if (d < bd) { bd = d; best = p.index; }
        }
        return best;
    }

    private boolean inTable(double x, double y) {
        return x >= ox && x <= ox + Table.W * scale && y >= oy && y <= oy + Table.H * scale;
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        placingDragging = false;
        volumeDragging = false;
        if (!dragging) return;
        dragging = false;
        if (phase == Phase.AIM && power > 0.03) {
            shoot();
        } else {
            power = 0;
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
        if (placingDragging) {
            int spinCx = getWidth() - sidebarW + 62;
            int spinCy = getHeight() - 232;
            setSpin(mouseX - spinCx, mouseY - spinCy, 40);
            return;
        }
        if (volumeDragging) {
            setVolumeFrom(mouseX);
            return;
        }
        if (phase == Phase.PLACING && humanTurn() && inTable(mouseX, mouseY)) {
            placeX = Math.max(Table.R, Math.min(Table.W - Table.R, wx(mouseX)));
            placeY = Math.max(Table.R, Math.min(Table.H - Table.R, wy(mouseY)));
        }
        if (dragging) {
            double dx = mouseX - dragSX, dy = mouseY - dragSY;
            double pull = -(dx * Math.cos(aimAngle) + dy * Math.sin(aimAngle));
            power = Math.max(0, Math.min(1, pull / (0.40 * scale)));
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
        mouseInTable = inTable(mouseX, mouseY);
        hoverBtn = -1;
        for (int i = 0; i < 4; i++) if (btn[i].contains(mouseX, mouseY)) hoverBtn = i;
        if (phase == Phase.AIM && humanTurn() && mouseInTable && !dragging) {
            Ball cue = match.world.cue();
            if (cue.active) {
                double ax = wx(mouseX) - cue.x, ay = wy(mouseY) - cue.y;
                if (Math.hypot(ax, ay) > 0.02) aimAngle = Math.atan2(ay, ax);
            }
        }
        if (phase == Phase.PLACING && humanTurn() && mouseInTable) {
            placeX = Math.max(Table.R, Math.min(Table.W - Table.R, wx(mouseX)));
            placeY = Math.max(Table.R, Math.min(Table.H - Table.R, wy(mouseY)));
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {}
    @Override
    public void mouseExited(MouseEvent e) { mouseInTable = false; }
    @Override
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_F11) {
            app.toggleFullscreen();
            return;
        }
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            app.showMenu();
            return;
        }
        if (match == null) return;
        if (e.getKeyCode() == KeyEvent.VK_P) {
            if (simulating) { paused = !paused; toast(paused ? "已暂停（按 P 继续）" : "继续", 1.5); }
            return;
        }
        if (!humanTurn()) return;
        if (phase != Phase.AIM) return;
        double step = e.isShiftDown() ? 0.002 : 0.012;
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT: aimAngle -= step; break;
            case KeyEvent.VK_RIGHT: aimAngle += step; break;
            case KeyEvent.VK_UP: power = Math.min(1, power + 0.04); break;
            case KeyEvent.VK_DOWN: power = Math.max(0, power - 0.04); break;
            case KeyEvent.VK_SPACE:
                if (power < 0.05) power = 0.55;
                shoot();
                break;
            case KeyEvent.VK_A: assist = !assist; break;
            case KeyEvent.VK_M: soundOn = !soundOn; sound.setEnabled(soundOn); break;
            default: break;
        }
    }

    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}

    private void restart() {
        if (net != null) {
            app.showMenu();
            return;
        }
        long seed = System.nanoTime();
        if (mode == MODE_AI) {
            startLocal(seed);
        } else {
            match = new Match(seed);
            match.names[0] = "玩家 1";
            match.names[1] = "玩家 2";
            wireWorld();
            phase = Phase.PLACING;
            initPlacement();
            aimAngle = 0;
            toast("新的一局开始", 3);
        }
    }

    // ---------------------------------------------------------------- 网络回调

    @Override
    public void onStart(int myIndex, long seed, String[] names) {
        SwingUtilities.invokeLater(() -> {
            this.myIndex = myIndex;
            match = new Match(seed);
            match.names[0] = names[0];
            match.names[1] = names[1];
            match.ai[0] = match.ai[1] = false;
            wireWorld();
            phase = Phase.PLACING;
            initPlacement();
            aimAngle = 0;
            if (net != null && match.current != myIndex) phase = Phase.NET_WAIT;
            toast("对手已连接，开球吧！", 4);
            repaint();
        });
    }

    @Override
    public void onShot(Shot shot, Snapshot pre) {
        SwingUtilities.invokeLater(() -> {
            if (match == null) return;
            if (net != null && net.isHost()) {
                if (match.current != 1 || phase == Phase.OVER) return;
                net.broadcastShot(shot, match);
                applyLocalShot(shot);
            } else {
                pre.applyTo(match);
                applyLocalShot(shot);
            }
        });
    }

    @Override
    public void onResult(Snapshot post) {
        SwingUtilities.invokeLater(() -> {
            if (match == null) return;
            if (simulating) { pendingResult = post; return; }
            post.applyTo(match);
            if (match.winner >= 0) {
                phase = Phase.OVER;
                playGameEndSound();
                toast(match.message, 6);
            } else {
                afterShot();
                toast(match.message, 5);
            }
            repaint();
        });
    }

    @Override
    public void onDisconnect(String reason) {
        SwingUtilities.invokeLater(() -> {
            netStatus = reason;
            phase = Phase.DISCONNECTED;
            toast(reason, 8);
            repaint();
        });
    }

    @Override
    public void onStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            netStatus = status;
            repaint();
        });
    }

    // ---------------------------------------------------------------- 开发/截图辅助

    public Match match() { return match; }

    public void renderTo(Graphics2D g, int w, int h) {
        setSize(w, h);
        paintComponent(g);
    }

    /** 直接推演一杆（用于截图与演示，不播放动画）。 */
    public void devShoot(Shot s) {
        if (match == null) return;
        match.calledPocket = s.calledPocket;
        match.armShot(s);
        match.world.settle(30);
        match.resolve(s);
        phase = match.ballInHand ? Phase.PLACING : Phase.AIM;
        if (match.ballInHand) initPlacement();
        aimAngle = defaultAim();
        toast(match.message, 6);
    }

    public void devSetPower(double p) { power = p; }
    public void devSetSpin(double x, double y) { spinX = x; spinY = y; }
    public void devSetAim(double a) { aimAngle = a; }
    public void devSetPhaseAim() { phase = Phase.AIM; }
    public void devSetCalledPocket(int p) { if (match != null) match.calledPocket = p; }

    /** 让电脑自己和自己打，用于无界面冒烟测试 / 演示。 */
    public void devSelfPlay() {
        if (match == null) return;
        match.ai[0] = true;
        match.ai[1] = true;
        afterShot();
    }

    public void devPlayOneShot(double power) {
        if (match == null) return;
        phase = Phase.AIM;
        this.power = power;
        shoot();
    }

    public String devPhase() { return phase.name(); }
    public int devMyIndex() { return myIndex; }
    public NetGame devNet() { return net; }
    public double[] devLayout() {
        ensureLayout(getWidth(), getHeight());
        return new double[]{ox, oy, scale, sidebarW};
    }
    public double devSpinX() { return spinX; }
    public double devSpinY() { return spinY; }
}
