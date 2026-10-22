package com.pool8;

import com.pool8.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** 入口。 */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--selftest")) {
            SelfTest.main(args);
            return;
        }
        if (args.length > 1 && args[0].equals("--screenshot")) {
            Screenshot.save(args[1]);
            return;
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}
