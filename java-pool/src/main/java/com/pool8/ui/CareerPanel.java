package com.pool8.ui;

import com.pool8.account.Account;
import com.pool8.audio.SoundFx;
import com.pool8.career.Achievement;
import com.pool8.career.AvatarArt;
import com.pool8.career.Career;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

/** 「我的生涯」：头像、昵称、人机战绩与成就（深色绿金主题）。 */
public final class CareerPanel extends JPanel {
    private final MainFrame app;
    private final Career career = Career.get();
    private final JButton[] avatarBtns = new JButton[AvatarArt.COUNT];
    private final Preview preview = new Preview();
    private final JTextField nickField = new JTextField();
    private final JPanel achiPanel = new JPanel();
    private final JLabel achiTitle = new JLabel();
    private final JLabel loginChip = new JLabel();
    private final StatCard[] statCards = {
            new StatCard("人机胜场", "0"), new StatCard("总局数", "0"), new StatCard("胜率", "—"),
            new StatCard("最长连赢", "0"), new StatCard("累计进球", "0"), new StatCard("开球进球", "0")
    };

    public CareerPanel(MainFrame app) {
        this.app = app;
        setLayout(new BorderLayout());
        setOpaque(false);

        // ---------------- 顶栏 ----------------
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        header.setBackground(new Color(0x0F1813));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x24332A)),
                new EmptyBorder(10, 16, 10, 18)));
        JButton back = pill("← 返回", false);
        back.addActionListener(e -> { SoundFx.get().click(); app.showMenu(); });
        JLabel title = new JLabel("我 的 生 涯");
        title.setFont(Theme.font(Font.BOLD, 21));
        title.setForeground(Theme.GOLD);
        JPanel titleBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 6));
        titleBox.setOpaque(false);
        titleBox.add(title);
        loginChip.setFont(Theme.font(Font.PLAIN, 11));
        loginChip.setForeground(Theme.MUTED);
        header.add(back, BorderLayout.WEST);
        header.add(titleBox, BorderLayout.CENTER);
        header.add(loginChip, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ---------------- 主体 ----------------
        JPanel body = new JPanel(new BorderLayout(26, 0));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(20, 26, 20, 26));

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(380, 0));
        left.setMaximumSize(new Dimension(380, Integer.MAX_VALUE));

        preview.setAlignmentX(Component.CENTER_ALIGNMENT);
        left.add(preview);
        left.add(Box.createVerticalStrut(8));

        JLabel hint = new JLabel("选择头像（点击切换）");
        hint.setFont(Theme.font(Font.PLAIN, 10.5));
        hint.setForeground(Theme.MUTED);
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        left.add(hint);
        left.add(Box.createVerticalStrut(6));

        JPanel avatarRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        avatarRow.setOpaque(false);
        avatarRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        avatarRow.setMaximumSize(new Dimension(380, 64));
        for (int i = 0; i < AvatarArt.COUNT; i++) {
            final int idx = i;
            JButton b = new JButton(new ImageIcon(AvatarArt.avatar(i, 44)));
            b.setPreferredSize(new Dimension(54, 54));
            b.setContentAreaFilled(false);
            b.setFocusPainted(false);
            b.setBorderPainted(true);
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            b.setToolTipText("头像 " + (i + 1));
            b.addActionListener(e -> {
                SoundFx.get().click();
                career.avatarIndex = idx;
                career.save();
                refresh();
            });
            avatarBtns[i] = b;
            avatarRow.add(b);
        }
        left.add(avatarRow);
        left.add(Box.createVerticalStrut(14));

        JPanel nickRow = new JPanel(new BorderLayout(8, 0));
        nickRow.setOpaque(false);
        nickRow.setMaximumSize(new Dimension(380, 32));
        nickField.setFont(Theme.font(Font.PLAIN, 13));
        nickField.setBackground(new Color(0x0D1511));
        nickField.setForeground(Theme.TEXT);
        nickField.setCaretColor(Theme.GOLD);
        nickField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x24332A)),
                new EmptyBorder(3, 8, 3, 8)));
        JButton saveBtn = pill("保存", true);
        saveBtn.addActionListener(e -> {
            SoundFx.get().click();
            String n = nickField.getText().trim();
            if (!n.isEmpty()) { career.nickname = n; career.save(); }
            refresh();
        });
        nickRow.add(nickField, BorderLayout.CENTER);
        nickRow.add(saveBtn, BorderLayout.EAST);
        left.add(nickRow);
        left.add(Box.createVerticalStrut(14));

        JPanel stats = new JPanel(new GridLayout(2, 3, 8, 8));
        stats.setOpaque(false);
        stats.setMaximumSize(new Dimension(380, 140));
        for (StatCard c : statCards) stats.add(c);
        left.add(stats);

        body.add(left, BorderLayout.WEST);

        // ---------------- 成绩 ----------------
        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.setOpaque(false);
        JPanel achHead = new JPanel(new BorderLayout(0, 6));
        achHead.setOpaque(false);
        achiTitle.setFont(Theme.font(Font.BOLD, 14));
        achiTitle.setForeground(Theme.TEXT);
        achHead.add(achiTitle, BorderLayout.NORTH);
        achHead.add(new Progress(), BorderLayout.CENTER);
        right.add(achHead, BorderLayout.NORTH);

        achiPanel.setLayout(new BoxLayout(achiPanel, BoxLayout.Y_AXIS));
        achiPanel.setOpaque(false);
        achiPanel.setBorder(new EmptyBorder(6, 0, 0, 8));
        JScrollPane sp = new JScrollPane(achiPanel);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(true);
        sp.getViewport().setBackground(new Color(0x0A100D));
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        styleScrollBar(sp.getVerticalScrollBar());
        right.add(sp, BorderLayout.CENTER);
        body.add(right, BorderLayout.CENTER);

        add(body, BorderLayout.CENTER);
        refresh();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g.setPaint(new GradientPaint(0, 0, new Color(0x0B120E), 0, h, new Color(0x070A08)));
        g.fillRect(0, 0, w, h);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(w * 0.35, h * 0.15), w * 0.8f,
                new float[]{0f, 1f}, new Color[]{Theme.alpha(new Color(0x123A26), 90), new Color(0, 0, 0, 0)}));
        g.fillRect(0, 0, w, h);
        g.dispose();
        super.paintComponent(g0);
    }

    private void refresh() {
        preview.idx = career.avatarIndex;
        preview.repaint();
        String who = Account.get().loggedIn() ? "已登录：" + Account.get().username() : "未登录（右上角登录）";
        loginChip.setText(who);
        achiTitle.setText("成就    " + career.unlockedCount() + " / " + career.achievements.size());
        for (int i = 0; i < AvatarArt.COUNT; i++) {
            boolean sel = i == career.avatarIndex;
            avatarBtns[i].setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(sel ? Theme.GOLD : new Color(0x24332A), sel ? 2 : 1, true),
                    new EmptyBorder(2, 2, 2, 2)));
        }
        nickField.setText(career.nickname);
        String pct = career.games == 0 ? "—" : Math.round(career.winRate() * 100) + "%";
        statCards[0].value = String.valueOf(career.wins);
        statCards[1].value = String.valueOf(career.games);
        statCards[2].value = pct;
        statCards[3].value = String.valueOf(career.bestStreak);
        statCards[4].value = String.valueOf(career.totalPotted);
        statCards[5].value = String.valueOf(career.breakPots);
        for (StatCard c : statCards) c.repaint();

        achiPanel.removeAll();
        for (Achievement a : career.achievements) {
            achiPanel.add(new AchiCard(a, career.unlocked.contains(a.id)));
            achiPanel.add(Box.createVerticalStrut(6));
        }
        achiPanel.revalidate();
        achiPanel.repaint();
        JScrollPane sp = (JScrollPane) javax.swing.SwingUtilities.getAncestorOfClass(JScrollPane.class, achiPanel);
        if (sp != null) sp.repaint();
        revalidate();
        repaint();
    }

    private static void styleScrollBar(JScrollBar bar) {
        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(8, 0));
        bar.setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                thumbColor = new Color(0x2C3A31);
                trackColor = new Color(0x0A100D);
            }
            @Override
            protected JButton createDecreaseButton(int orientation) { return zeroButton(); }
            @Override
            protected JButton createIncreaseButton(int orientation) { return zeroButton(); }
            private JButton zeroButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                b.setMinimumSize(new Dimension(0, 0));
                b.setMaximumSize(new Dimension(0, 0));
                return b;
            }
        });
    }

    private static JButton pill(String text, boolean primary) {
        JButton b = new JButton(text);
        b.setFont(Theme.font(Font.BOLD, 11.5));
        b.setForeground(primary ? Theme.GOLD : Theme.TEXT);
        b.setBackground(primary ? new Color(0x2A3528) : new Color(0x1B251F));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? Theme.GOLD : new Color(0x24332A)),
                new EmptyBorder(6, 14, 6, 14)));
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** 大号头像预览：深色圆底 + 金环 + 绿色柔光。 */
    private static final class Preview extends JPanel {
        int idx;
        Preview() {
            setOpaque(false);
            setPreferredSize(new Dimension(160, 160));
            setMaximumSize(new Dimension(160, 160));
            setMinimumSize(new Dimension(160, 160));
        }
        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            double r = Math.min(w, h) / 2.0 - 6;
            double cx = w / 2.0, cy = h / 2.0;
            g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) (r * 1.35),
                    new float[]{0f, 1f}, new Color[]{Theme.alpha(new Color(0x1E5B3A), 120), new Color(0, 0, 0, 0)}));
            g.fill(new Ellipse2D.Double(cx - r * 1.35, cy - r * 1.35, r * 2.7, r * 2.7));
            g.setColor(new Color(0x16241B));
            g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            g.drawImage(AvatarArt.avatar(idx, (int) (r * 1.9)), (int) (cx - r * 0.95), (int) (cy - r * 0.95), null);
            g.setColor(Theme.GOLD);
            g.setStroke(new java.awt.BasicStroke(2.2f));
            g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
            g.dispose();
        }
    }

    /** 战绩小卡片。 */
    private static final class StatCard extends JPanel {
        final String label;
        String value;
        StatCard(String label, String value) { this.label = label; this.value = value; setOpaque(false); }
        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g.setColor(new Color(0x121A16));
            g.fill(new RoundRectangle2D.Double(0, 0, w, h, 10, 10));
            g.setColor(new Color(0x24332A));
            g.setStroke(new java.awt.BasicStroke(1f));
            g.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 10, 10));
            g.setColor(Theme.GOLD);
            g.setFont(Theme.font(Font.BOLD, 19));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(value, (w - fm.stringWidth(value)) / 2, 28);
            g.setColor(Theme.MUTED);
            g.setFont(Theme.font(Font.PLAIN, 10));
            fm = g.getFontMetrics();
            g.drawString(label, (w - fm.stringWidth(label)) / 2, 47);
            g.dispose();
        }
    }

    /** 成就条目。 */
    private static final class AchiCard extends JPanel {
        private final Achievement a;
        private final boolean done;
        AchiCard(Achievement a, boolean done) {
            this.a = a;
            this.done = done;
            setOpaque(false);
            setPreferredSize(new Dimension(0, 54));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        }
        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g.setColor(done ? new Color(0x16241B) : new Color(0x101613));
            g.fill(new RoundRectangle2D.Double(0, 0, w, h, 10, 10));
            g.setColor(done ? new Color(0x3A6B4E) : new Color(0x1E2A23));
            g.setStroke(new java.awt.BasicStroke(1f));
            g.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 10, 10));
            g.setFont(Theme.font(Font.BOLD, 14));
            g.setColor(done ? Theme.GOLD : new Color(0x59665E));
            g.drawString(done ? "✓" : "○", 14, h / 2 + 6);
            g.setColor(done ? Theme.GOLD : new Color(0x6E7C73));
            g.setFont(Theme.font(Font.BOLD, 12.5));
            g.drawString(a.name, 40, h / 2 - 3);
            g.setColor(done ? new Color(0xB89150) : new Color(0x59665E));
            g.setFont(Theme.font(Font.PLAIN, 10.5));
            g.drawString(a.desc, 40, h / 2 + 15);
            g.dispose();
        }
    }

    /** 成就进度条。 */
    private final class Progress extends JPanel {
        Progress() {
            setOpaque(false);
            setPreferredSize(new Dimension(0, 8));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        }
        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g.setColor(new Color(0x101915));
            g.fill(new RoundRectangle2D.Double(0, 0, w, h, h, h));
            double r = career.achievements.isEmpty() ? 0
                    : career.unlockedCount() / (double) career.achievements.size();
            if (r > 0) {
                g.setPaint(new GradientPaint(0, 0, new Color(0x3FBF7F), w, 0, Theme.GOLD));
                g.fill(new RoundRectangle2D.Double(0, 0, Math.max(h, w * r), h, h, h));
            }
            g.dispose();
        }
    }
}
