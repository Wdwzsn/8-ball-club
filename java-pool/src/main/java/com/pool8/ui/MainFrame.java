package com.pool8.ui;

import com.pool8.ai.Ai;
import com.pool8.audio.SoundFx;
import com.pool8.net.NetGame;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.CardLayout;
import java.awt.Dimension;

/** 主窗口：菜单与球桌之间切换。 */
public final class MainFrame extends JFrame {
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);
    private final MenuPanel menu;
    private GamePanel game;
    private CareerPanel careerPanel;

    public MainFrame() {
        setTitle("8-BALL CLUB · 标准八球");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                exit();
            }
        });
        // 初始窗口：在 150% 缩放的 2K 屏上也完整可见，可自由拉伸/最大化
        java.awt.Dimension scr = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        int w = Math.min(1500, scr.width - 40);
        int h = Math.min(900, scr.height - 80);
        setSize(w, h);
        setMinimumSize(new Dimension(1100, 680));
        setLocationRelativeTo(null);
        root.setOpaque(true);
        menu = new MenuPanel(this);
        root.add(menu, "menu");
        setContentPane(root);
        cards.show(root, "menu");
        SoundFx.get().startBgm();
    }

    private void mount(GamePanel p) {
        if (game != null) {
            game.dispose();
            root.remove(game);
        }
        game = p;
        root.add(p, "game");
        cards.show(root, "game");
        p.revalidate();
        SwingUtilities.invokeLater(p::requestFocusInWindow);
    }

    public void startAi(int difficulty) {
        GamePanel p = new GamePanel(this, GamePanel.MODE_AI);
        p.setDifficulty(difficulty);
        mount(p);
        p.startLocal(System.nanoTime());
    }

    public void startLocal2p() {
        GamePanel p = new GamePanel(this, GamePanel.MODE_LOCAL);
        mount(p);
        p.startLocal(System.nanoTime());
    }

    public void startHost() {
        GamePanel p = new GamePanel(this, GamePanel.MODE_HOST);
        mount(p);
        NetGame ng = NetGame.host(GamePanel.PORT, "房主", p);
        p.setNet(ng);
    }

    public void startClient(String addr, int port) {
        GamePanel p = new GamePanel(this, GamePanel.MODE_CLIENT);
        mount(p);
        NetGame ng = NetGame.join(addr, port, "挑战者", p);
        p.setNet(ng);
    }

    public void askJoin() {
        String s = JOptionPane.showInputDialog(this,
                "输入房主的 IP 地址（默认端口 " + GamePanel.PORT + "）\n格式：192.168.1.10 或 192.168.1.10:24680",
                "加入联机房间", JOptionPane.QUESTION_MESSAGE);
        if (s == null) return;
        s = s.trim();
        if (s.isEmpty()) return;
        String host = s;
        int port = GamePanel.PORT;
        int colon = s.lastIndexOf(':');
        if (colon > 0) {
            try {
                port = Integer.parseInt(s.substring(colon + 1).trim());
                host = s.substring(0, colon).trim();
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "端口格式不正确", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        startClient(host, port);
    }

    public void showMenu() {
        if (game != null) {
            game.dispose();
            root.remove(game);
            game = null;
        }
        cards.show(root, "menu");
        menu.resetHover();
        SwingUtilities.invokeLater(menu::requestFocusInWindow);
    }

    public void openRules() {
        RulesDialog.show(this);
    }

    public void startCareer() {
        if (game != null) {
            game.dispose();
            root.remove(game);
            game = null;
        }
        if (careerPanel == null) {
            careerPanel = new CareerPanel(this);
            root.add(careerPanel, "career");
        }
        cards.show(root, "career");
    }

    public void toggleFullscreen() {
        java.awt.GraphicsDevice gd = getGraphicsConfiguration().getDevice();
        if (!gd.isFullScreenSupported()) return;
        boolean fs = isUndecorated();
        dispose();
        setUndecorated(!fs);
        if (!fs) {
            setVisible(true);
            gd.setFullScreenWindow(this);
        } else {
            gd.setFullScreenWindow(null);
            setVisible(true);
        }
    }

    public void exit() {
        int r = JOptionPane.showConfirmDialog(this, "确定要退出游戏吗？", "退出 8-BALL CLUB", JOptionPane.YES_NO_OPTION);
        if (r == JOptionPane.YES_OPTION) {
            SoundFx.get().stopBgm();
            dispose();
            System.exit(0);
        }
    }
}
