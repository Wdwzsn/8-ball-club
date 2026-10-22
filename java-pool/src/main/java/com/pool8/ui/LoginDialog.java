package com.pool8.ui;

import com.pool8.account.Account;
import com.pool8.audio.SoundFx;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;

/** 深色登录 / 注册弹窗。 */
public final class LoginDialog extends JDialog {
    private final JTextField serverField = new JTextField(Account.get().server());
    private final JTextField userField = new JTextField(Account.get().username());
    private final JPasswordField passField = new JPasswordField();
    private final JLabel status = new JLabel(" ");
    private final JButton loginBtn = pill("登 录", true);
    private final JButton regBtn = pill("注 册", false);
    private boolean ok;

    private LoginDialog(Window owner) {
        super(owner, "登录 / 注册", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(0x121A16));
        root.setBorder(new EmptyBorder(20, 24, 18, 24));

        JLabel title = new JLabel("登录 / 注册");
        title.setFont(Theme.font(Font.BOLD, 20));
        title.setForeground(Theme.GOLD);
        root.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(Box.createVerticalStrut(10));
        form.add(row("服务器", serverField));
        form.add(Box.createVerticalStrut(8));
        form.add(row("昵称", userField));
        form.add(Box.createVerticalStrut(8));
        form.add(row("密码", passField));
        form.add(Box.createVerticalStrut(10));

        JPanel btns = new JPanel();
        btns.setOpaque(false);
        btns.setLayout(new BoxLayout(btns, BoxLayout.X_AXIS));
        loginBtn.addActionListener(e -> submit(true));
        regBtn.addActionListener(e -> submit(false));
        JButton cancel = pill("取消", false);
        cancel.addActionListener(e -> dispose());
        btns.add(loginBtn);
        btns.add(Box.createHorizontalStrut(10));
        btns.add(regBtn);
        btns.add(Box.createHorizontalStrut(10));
        btns.add(cancel);
        form.add(btns);
        form.add(Box.createVerticalStrut(8));

        status.setFont(Theme.font(Font.PLAIN, 11.5));
        status.setForeground(new Color(0xE05A4E));
        form.add(status);
        form.add(Box.createVerticalStrut(4));
        JLabel hint = new JLabel("提示：需先启动 pool-online/server.py；账号与网页版通用。");
        hint.setFont(Theme.font(Font.PLAIN, 10.5));
        hint.setForeground(Theme.MUTED);
        form.add(hint);

        root.add(form, BorderLayout.CENTER);
        setContentPane(root);
        setSize(430, 340);
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private JPanel row(String label, JTextField field) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(Theme.font(Font.BOLD, 12));
        l.setForeground(Theme.TEXT);
        l.setPreferredSize(new Dimension(52, 28));
        field.setFont(Theme.font(Font.PLAIN, 14));
        field.setBackground(new Color(0x0D1511));
        field.setForeground(Theme.TEXT);
        field.setCaretColor(Theme.TEXT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE),
                new EmptyBorder(4, 8, 4, 8)));
        p.add(l, BorderLayout.WEST);
        p.add(field, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(400, 34));
        return p;
    }

    private void submit(boolean isLogin) {
        String srv = serverField.getText().trim();
        String u = userField.getText().trim();
        String p = new String(passField.getPassword());
        if (u.isEmpty() || p.isEmpty()) {
            status.setText("请填写昵称和密码");
            return;
        }
        Account.get().setServer(srv);
        status.setForeground(Theme.MUTED);
        status.setText(isLogin ? "登录中…" : "注册中…");
        loginBtn.setEnabled(false);
        regBtn.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (isLogin) Account.get().login(u, p);
                else Account.get().register(u, p);
                return null;
            }
            @Override
            protected void done() {
                try {
                    get();
                    SoundFx.get().click();
                    ok = true;
                    dispose();
                } catch (Exception ex) {
                    Throwable t = ex.getCause() != null ? ex.getCause() : ex;
                    status.setForeground(new Color(0xE05A4E));
                    status.setText(t.getMessage() == null ? "网络错误" : t.getMessage());
                    loginBtn.setEnabled(true);
                    regBtn.setEnabled(true);
                }
            }
        }.execute();
    }

    private static JButton pill(String text, boolean primary) {
        JButton b = new JButton(text);
        b.setFont(Theme.font(Font.BOLD, 12));
        b.setForeground(primary ? Theme.GOLD : Theme.TEXT);
        b.setBackground(primary ? new Color(0x2A3528) : new Color(0x1B251F));
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? Theme.GOLD : Theme.LINE),
                new EmptyBorder(6, 16, 6, 16)));
        b.setFocusPainted(false);
        b.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        return b;
    }

    /** 打开登录窗，返回是否登录成功。 */
    public static boolean show(Window owner) {
        LoginDialog d = new LoginDialog(owner);
        d.setVisible(true);
        return d.ok;
    }
}
