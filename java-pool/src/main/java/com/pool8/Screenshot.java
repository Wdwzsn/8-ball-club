package com.pool8;

import com.pool8.career.AvatarArt;
import com.pool8.core.Shot;
import com.pool8.core.Table;
import com.pool8.core.World;
import com.pool8.ui.CareerPanel;
import com.pool8.ui.GamePanel;
import com.pool8.ui.MainFrame;
import com.pool8.ui.MenuPanel;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/** 离屏截图（用于视觉校验）。 */
public final class Screenshot {
    private Screenshot() {}

    public static void save(String dir) {
        new File(dir).mkdirs();
        int w = 1600, h = 900;

        MenuPanel menu = new MenuPanel(null);
        menu.setSize(w, h);
        write(menu, w, h, new File(dir, "menu.png"));

        GamePanel p = new GamePanel(null, GamePanel.MODE_AI);
        p.setSize(w, h);
        p.startLocal(7);
        p.devShoot(new Shot(Table.HEAD_X, Table.H / 2, 0, 0.95, 0, 0, -1));
        p.devSetPower(0.62);
        p.devSetSpin(0.35, -0.45);
        p.devSetAim(0.15);
        write(p, w, h, new File(dir, "table.png"));

        // 中局：再打两杆，摆出有球分布的画面
        World wd = p.match().world;
        int attempts = 0;
        while (attempts < 2) {
            Shot s = new Shot(wd.cue().x, wd.cue().y, Math.atan2(0.635 - wd.cue().y, 1.9 - wd.cue().x),
                    0.55, 0, 0, -1);
            p.devShoot(s);
            attempts++;
        }
        p.devSetAim(0.32);
        p.devSetPower(0.45);
        p.devSetSpin(0, 0.35);
        write(p, w, h, new File(dir, "table2.png"));

        // 小窗口布局检查
        MenuPanel menuSmall = new MenuPanel(null);
        write(menuSmall, 1280, 760, new File(dir, "menu_small.png"));
        GamePanel ps = new GamePanel(null, GamePanel.MODE_AI);
        ps.setSize(1280, 760);
        ps.startLocal(7);
        ps.devShoot(new Shot(Table.HEAD_X, Table.H / 2, 0, 0.95, 0, 0, -1));
        ps.devSetPower(0.55);
        ps.devSetAim(-0.2);
        write(ps, 1280, 760, new File(dir, "table_small.png"));

        CareerPanel cp = new CareerPanel(null);
        cp.setSize(1400, 820);
        write(cp, 1400, 820, new File(dir, "career.png"));

        BufferedImage sheet = new BufferedImage(6 * 130 + 20, 150, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = sheet.createGraphics();
        g2.setColor(new Color(0x0E1511));
        g2.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int i = 0; i < 6; i++) {
            g2.drawImage(AvatarArt.avatar(i, 120), 10 + i * 130, 15, null);
        }
        g2.dispose();
        try {
            ImageIO.write(sheet, "png", new File(dir, "avatars.png"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("screenshots written to " + dir);
        System.exit(0);
    }

    private static void write(JPanel panel, int w, int h, File file) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        panel.setSize(w, h);
        panel.addNotify();
        panel.validate();
        panel.paint(g);
        g.dispose();
        try {
            ImageIO.write(img, "png", file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
