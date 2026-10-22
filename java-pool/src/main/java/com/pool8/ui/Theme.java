package com.pool8.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** 配色与字体。 */
public final class Theme {
    public static final Color BG_TOP = new Color(0x111A15);
    public static final Color BG_BOTTOM = new Color(0x070B09);
    public static final Color SIDEBAR = new Color(0x111915);
    public static final Color SIDEBAR_2 = new Color(0x172019);
    public static final Color CARD = new Color(0x1B241D);
    public static final Color CARD_ACTIVE = new Color(0x243026);
    public static final Color LINE = new Color(0x2C3A31);
    public static final Color GOLD = new Color(0xD8B36A);
    public static final Color GOLD_SOFT = new Color(0xB89150);
    public static final Color TEXT = new Color(0xEDE7D8);
    public static final Color MUTED = new Color(0x8FA096);
    public static final Color GREEN = new Color(0x3FBF7F);
    public static final Color RED = new Color(0xE05A4E);
    public static final Color FELT = new Color(0x0E6B3E);
    public static final Color FELT_LIGHT = new Color(0x158049);
    public static final Color FELT_DARK = new Color(0x07401F);
    public static final Color WOOD = new Color(0x4B2617);
    public static final Color WOOD_LIGHT = new Color(0x6B3A22);

    private static final Set<String> FONTS = new HashSet<>(Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));

    public static Font font(int style, double size) {
        String[] prefer = {"Microsoft YaHei UI", "Microsoft YaHei", "微软雅黑", "PingFang SC", "Segoe UI", "Dialog"};
        for (String f : prefer) {
            if (FONTS.contains(f)) return new Font(f, style, (int) Math.round(size)).deriveFont((float) size);
        }
        return new Font(Font.SANS_SERIF, style, (int) Math.round(size)).deriveFont((float) size);
    }

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    public static Color mix(Color a, Color b, double t) {
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    public static Color brighten(Color c, double f) {
        return new Color(Math.min(255, (int) (c.getRed() + (255 - c.getRed()) * f)),
                Math.min(255, (int) (c.getGreen() + (255 - c.getGreen()) * f)),
                Math.min(255, (int) (c.getBlue() + (255 - c.getBlue()) * f)));
    }

    public static Color darken(Color c, double f) {
        return new Color((int) (c.getRed() * (1 - f)), (int) (c.getGreen() * (1 - f)), (int) (c.getBlue() * (1 - f)));
    }

    private Theme() {}
}
