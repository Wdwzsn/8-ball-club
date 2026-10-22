package com.pool8.ui;

import com.pool8.account.Account;
import com.pool8.ai.Ai;
import com.pool8.audio.SoundFx;
import com.pool8.career.AvatarArt;
import com.pool8.career.Career;
import com.pool8.core.Table;
import com.pool8.core.World;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** 主菜单。 */
public final class MenuPanel extends JPanel implements MouseListener, MouseMotionListener, KeyListener {
    private final MainFrame app;
    private int difficulty = Ai.NORMAL;

    private final World preview;
    private BufferedImage cached;
    private int cachedW = -1, cachedH = -1;

    private final Rectangle2D[] main = new Rectangle2D[5];
    private final Rectangle2D[] diff = new Rectangle2D[3];
    private final Rectangle2D rulesBtn = new Rectangle2D.Double();
    private final Rectangle2D quitBtn = new Rectangle2D.Double();
    private final Rectangle2D loginBox = new Rectangle2D.Double();
    private int hover = -1;
    private int hoverSmall = -1;

    private static final String[] MAIN_LABEL = {"人机对战", "双人同屏", "创建联机房间", "加入联机房间", "我的生涯"};
    private static final String[] MAIN_SUB = {
            "与电脑对战 · 三档难度", "同一台电脑轮流击球", "把 IP 告诉好友，等待连接",
            "输入房主 IP 加入对局", "头像 · 战绩 · 成就"};
    private static final String[] DIFF_LABEL = {"简单", "普通", "高手"};

    public MenuPanel(MainFrame app) {
        this.app = app;
        setBackground(Theme.BG_BOTTOM);
        setFocusable(true);
        addMouseListener(this);
        addMouseMotionListener(this);
        addKeyListener(this);
        preview = new World();
        preview.rack(20240612L);
        for (int i = 0; i < 5; i++) main[i] = new Rectangle2D.Double();
        for (int i = 0; i < 3; i++) diff[i] = new Rectangle2D.Double();
    }

    public void resetHover() {
        hover = -1;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics gg) {
        super.paintComponent(gg);
        Graphics2D g = (Graphics2D) gg.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();

        g.setPaint(new GradientPaint(0, 0, new Color(0x0E1511), 0, h, new Color(0x070A08)));
        g.fillRect(0, 0, w, h);

        if (cached == null || cachedW != w || cachedH != h) {
            cached = renderPreview(w, h);
            cachedW = w;
            cachedH = h;
        }
        g.drawImage(cached, 0, 0, null);

        int lw = Math.max(520, (int) (w * 0.44));
        g.setPaint(new GradientPaint(0, 0, Theme.alpha(new Color(0x0B1210), 250), lw * 0.82f, 0,
                Theme.alpha(new Color(0x0B1210), 0)));
        g.fillRect(0, 0, lw, h);

        // 标题（按窗口高度自适应，保证小窗口下按钮也完整可见）
        int titleY = (int) (h * 0.165);
        g.setColor(Theme.GOLD);
        g.setFont(Theme.font(Font.BOLD, Math.min(62, w * 0.042)));
        g.drawString("8-BALL CLUB", 78, titleY);
        g.setColor(Theme.TEXT);
        g.setFont(Theme.font(Font.PLAIN, 15));
        g.drawString("标 准 八 球  ·  拟 真 物 理  ·  绿 色 台 呢", 82, titleY + 36);

        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(Font.PLAIN, 12.5));
        g.drawString("真实滚动摩擦与库边反弹，WPA 标准八球规则。", 82, titleY + 88);
        g.drawString("支持人机对战、双人同屏与局域网联机。", 82, titleY + 110);

        int x = 82;
        int bh = h < 760 ? 46 : 50;
        int gap = h < 760 ? 7 : 8;
        int y = titleY + 150;
        int bw = Math.min(360, w - 200);
        for (int i = 0; i < 5; i++) {
            drawMainButton(g, main[i], x, y + i * (bh + gap), bw, bh, i, hover == i);
        }

        int dy = y + 5 * (bh + gap) + 18;
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(Font.PLAIN, 11));
        g.drawString("电脑难度", x, dy + 20);
        for (int i = 0; i < 3; i++) {
            Rectangle2D r = new Rectangle2D.Double(x + 78 + i * 82, dy, 74, 30);
            diff[i].setRect(r);
            boolean sel = difficulty == i;
            g.setColor(sel ? new Color(0x3A3020) : new Color(0x18211C));
            g.fill(new RoundRectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight(), 15, 15));
            g.setColor(sel ? Theme.GOLD : new Color(255, 255, 255, 26));
            g.setStroke(new java.awt.BasicStroke(1f));
            g.draw(new RoundRectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight(), 15, 15));
            g.setColor(sel ? Theme.GOLD : Theme.MUTED);
            g.setFont(Theme.font(sel ? Font.BOLD : Font.PLAIN, 11));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(DIFF_LABEL[i], (float) (r.getX() + (r.getWidth() - fm.stringWidth(DIFF_LABEL[i])) / 2),
                    (float) (r.getY() + 20));
        }

        rulesBtn.setRect(x, dy + 48, 96, 30);
        quitBtn.setRect(x + 110, dy + 48, 96, 30);
        drawSmallButton(g, rulesBtn, "规则说明", hoverSmall == 0);
        drawSmallButton(g, quitBtn, "退出游戏", hoverSmall == 1);

        if (h > 730) {
            g.setColor(new Color(0x5E6C64));
            g.setFont(Theme.font(Font.PLAIN, 10.5));
            g.drawString("Java 21 · Swing 2D · 无第三方依赖    版本 1.0", 82, h - 24);
        }

        String hint = "绿色台呢 · WPA 标准八球";
        g.setColor(new Color(255, 255, 255, 40));
        g.setFont(Theme.font(Font.PLAIN, 10));
        g.drawString(hint, w - 190, h - 26);

        drawAccountChip(g, w);
        g.dispose();
    }

    /** 右上角账号入口：未登录显示「登录 / 注册」，已登录显示头像+昵称。 */
    private void drawAccountChip(Graphics2D g, int w) {
        int cw = 232, ch = 46;
        int x = w - cw - 24, y = 18;
        loginBox.setRect(x, y, cw, ch);
        boolean logged = Account.get().loggedIn();
        Shape pill = new RoundRectangle2D.Double(x, y, cw, ch, ch, ch);
        g.setColor(Theme.alpha(new Color(0x0E1712), 220));
        g.fill(pill);
        g.setColor(logged ? new Color(216, 179, 106, 150) : new Color(255, 255, 255, 40));
        g.setStroke(new java.awt.BasicStroke(1.1f));
        g.draw(pill);
        if (logged) {
            int av = 34;
            g.drawImage(AvatarArt.avatar(Career.get().avatarIndex, av), x + 6, y + (ch - av) / 2, av, av, null);
            g.setColor(Theme.TEXT);
            g.setFont(Theme.font(Font.BOLD, 13));
            String name = Account.get().username();
            if (name.length() > 9) name = name.substring(0, 9) + "…";
            g.drawString(name, x + av + 16, y + 27);
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(Font.PLAIN, 10));
            g.drawString("已登录", x + av + 16, y + 40);
            g.setColor(Theme.GOLD);
            g.setFont(Theme.font(Font.PLAIN, 12));
            g.drawString("▾", x + cw - 22, y + 28);
        } else {
            g.setColor(Theme.GOLD);
            g.setFont(Theme.font(Font.BOLD, 13));
            g.drawString("登录 / 注册", x + 30, y + 29);
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(Font.PLAIN, 9.5));
            g.drawString("账号 · 战绩", x + 142, y + 29);
        }
    }

    private BufferedImage renderPreview(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int lw = Math.max(520, (int) (w * 0.44));
        int aw = w - lw, ah = h;
        double scale = Math.min((aw - 70) / (Table.W + 2 * Table.RAIL_W), (ah - 150) / (Table.H + 2 * Table.RAIL_W));
        double ox = lw + (aw - Table.W * scale) / 2.0;
        double oy = (ah - Table.H * scale) / 2.0 + 12;

        g.setPaint(new RadialGradientPaint(new Point2D.Double(ox + Table.W * scale * 0.5, oy + Table.H * scale * 0.35),
                (float) (Table.W * scale * 0.85), new float[]{0f, 1f},
                new Color[]{Theme.alpha(new Color(0x2A4A38), 190), Theme.alpha(new Color(0x080C0A), 0)}));
        g.fillRect(lw, 0, aw, ah);

        Art.paintTable(g, ox, oy, scale);
        for (com.pool8.core.Ball b : preview.balls) {
            if (b.active) Art.drawBall(g, b, ox, oy, scale, 1.0);
        }
        g.setColor(new Color(0, 0, 0, 70));
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private void drawMainButton(Graphics2D g, Rectangle2D r, int x, int y, int w, int h, int idx, boolean hover) {
        r.setRect(x, y, w, h);
        Shape body = new RoundRectangle2D.Double(x, y, w, h, 14, 14);
        g.setColor(hover ? new Color(0x27352C) : new Color(0x18211C));
        g.fill(body);
        g.setColor(hover ? new Color(216, 179, 106, 190) : new Color(255, 255, 255, 26));
        g.setStroke(new java.awt.BasicStroke(1.1f));
        g.draw(body);
        g.setColor(hover ? Theme.GOLD : new Color(255, 255, 255, 70));
        g.fill(new RoundRectangle2D.Double(x + 14, y + 16, 3, h - 32, 2, 2));

        g.setColor(hover ? Theme.GOLD : Theme.TEXT);
        g.setFont(Theme.font(Font.BOLD, 16));
        g.drawString(MAIN_LABEL[idx], x + 30, y + 26);
        g.setColor(Theme.MUTED);
        g.setFont(Theme.font(Font.PLAIN, 10.5));
        g.drawString(MAIN_SUB[idx], x + 30, y + 45);
        g.setColor(new Color(255, 255, 255, 40));
        g.setFont(Theme.font(Font.PLAIN, 17));
        g.drawString("›", x + w - 28, y + 36);
    }

    private void drawSmallButton(Graphics2D g, Rectangle2D r, String label, boolean hover) {
        g.setColor(hover ? new Color(0x1E2A23) : new Color(0x141C17));
        g.fill(new RoundRectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight(), 10, 10));
        g.setColor(hover ? new Color(216, 179, 106, 150) : new Color(255, 255, 255, 22));
        g.draw(new RoundRectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight(), 10, 10));
        g.setColor(hover ? Theme.TEXT : Theme.MUTED);
        g.setFont(Theme.font(Font.PLAIN, 10.5));
        g.drawString(label, (float) (r.getX() + 18), (float) (r.getY() + 20));
    }

    @Override
    public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        int x = e.getX(), y = e.getY();
        if (loginBox.contains(x, y)) {
            SoundFx.get().click();
            if (Account.get().loggedIn()) {
                int r = JOptionPane.showConfirmDialog(this, "退出当前账号？", "账号", JOptionPane.YES_NO_OPTION);
                if (r == JOptionPane.YES_OPTION) {
                    Account.get().logout();
                    repaint();
                }
            } else if (LoginDialog.show(SwingUtilities.getWindowAncestor(this))) {
                repaint();
            }
            return;
        }
        for (int i = 0; i < 5; i++) {
            if (main[i].contains(x, y)) {
                SoundFx.get().click();
                switch (i) {
                    case 0: app.startAi(difficulty); break;
                    case 1: app.startLocal2p(); break;
                    case 2: app.startHost(); break;
                    case 3: app.askJoin(); break;
                    case 4: app.startCareer(); break;
                    default: break;
                }
                return;
            }
        }
        for (int i = 0; i < 3; i++) {
            if (diff[i].contains(x, y)) {
                SoundFx.get().click();
                difficulty = i;
                repaint();
                return;
            }
        }
        if (rulesBtn.contains(x, y)) { SoundFx.get().click(); app.openRules(); return; }
        if (quitBtn.contains(x, y)) { SoundFx.get().click(); app.exit(); }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        int h = -1;
        for (int i = 0; i < 5; i++) if (main[i].contains(e.getX(), e.getY())) h = i;
        int hs = -1;
        if (rulesBtn.contains(e.getX(), e.getY())) hs = 0;
        if (quitBtn.contains(e.getX(), e.getY())) hs = 1;
        if (h != hover || hs != hoverSmall) { hover = h; hoverSmall = hs; repaint(); }
    }

    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
    @Override public void mouseDragged(MouseEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) app.startAi(difficulty);
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) app.exit();
    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}
}
