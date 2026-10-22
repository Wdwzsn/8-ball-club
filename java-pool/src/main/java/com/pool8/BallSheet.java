package com.pool8;

import com.pool8.ui.Art;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

/** 生成球体贴图检查表（开发用）。 */
public final class BallSheet {
    public static void main(String[] args) throws Exception {
        int d = 120, pad = 20;
        BufferedImage img = new BufferedImage(d * 4 + pad * 5, d * 4 + pad * 5, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x0E6B3E));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int id = 0; id < 16; id++) {
            int x = pad + (id % 4) * (d + pad);
            int y = pad + (id / 4) * (d + pad);
            BufferedImage sp = Art.ballSprite(id, d);
            g.drawImage(sp, x, y, d, d, null);
        }
        g.dispose();
        ImageIO.write(img, "png", new File("shots/balls.png"));
        System.out.println("ok");
        System.exit(0);
    }
}
