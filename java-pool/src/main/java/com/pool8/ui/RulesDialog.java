package com.pool8.ui;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

/** 规则说明窗口。 */
public final class RulesDialog {
    private RulesDialog() {}

    private static final String HTML = """
        <html><body style='font-family:Microsoft YaHei UI,Microsoft YaHei,sans-serif;color:#E7E1D2;
        background:#121A16;margin:18px;line-height:1.55;font-size:12.5px'>
        <h2 style='color:#D8B36A;margin:0 0 6px 0'>标准八球规则</h2>
        <p style='color:#8FA096;margin-top:0'>按 WPA 世界标准八球规则实现（跳球与三犯规则从略）</p>

        <h3 style='color:#D8B36A'>1. 开球</h3>
        <ul>
          <li>母球必须放在开球线（罚球线）后方。</li>
          <li>合法开球：母球先碰到球堆，并且至少有一颗球进袋，或至少四颗球碰到库边；否则判犯规，对手获得开球线后自由球。</li>
          <li>开球必进球台保持“开放”：开球后第一次合法进袋才决定球组。</li>
          <li>开球时 8 号球进袋不算输：8 号球重新摆回置球点，继续击球。</li>
        </ul>

        <h3 style='color:#D8B36A'>2. 球组</h3>
        <ul>
          <li>全色 1-7 为一组，花色 9-15 为一组；8 号球最后打。</li>
          <li>开球后第一次合法进球，该球所属的组归击球者，另一组归对手。</li>
          <li>清完自己组的所有球后，才能击打 8 号球。</li>
        </ul>

        <h3 style='color:#D8B36A'>3. 犯规（对手获得自由球）</h3>
        <ul>
          <li>母球落袋（滑杆）。</li>
          <li>空杆：母球没有碰到任何目标球。</li>
          <li>先碰到对方的球，或开放球台先碰 8 号球，或清台后先碰的不是 8 号球。</li>
          <li>击球后既没有球进袋，也没有任何球碰到库边。</li>
          <li>开球不合法。</li>
          <li>犯规后对手可以把母球放在台面任意合法位置（开球犯规限制在开球线后）。</li>
        </ul>

        <h3 style='color:#D8B36A'>4. 8 号球</h3>
        <ul>
          <li>打 8 号球前必须“叫袋”：点击目标袋口指定入袋位置。</li>
          <li>8 号球进指定袋且本杆无犯规 → 获胜。</li>
          <li>8 号球进错袋、提前打进 8 号球、或打进 8 号球同时犯规 → 判负。</li>
        </ul>

        <h3 style='color:#D8B36A'>5. 操作</h3>
        <ul>
          <li>移动鼠标瞄准；按住左键向球杆后方拖动蓄力，松开击球。</li>
          <li>方向键微调角度（Shift 更精细），上下键调力度，空格击球。</li>
          <li>右下角“杆法”圆盘：点击设置高杆/低杆/左右塞。</li>
          <li>自由球：移动母球到合法位置后点击放置。</li>
          <li>A 键开关辅助线，M 键开关音效，Esc 返回菜单。</li>
        </ul>

        <h3 style='color:#D8B36A'>6. 联机</h3>
        <ul>
          <li>一方点“创建联机房间”，把窗口上显示的 IP 与端口告诉好友；好友点“加入联机房间”输入 IP 即可。</li>
          <li>同一局域网可直接连接；跨互联网需要房主在路由器上把 TCP 24680 端口映射到本机。</li>
          <li>房主为权威端，负责规则判定与结果同步，双方画面保持一致。</li>
        </ul>
        </body></html>
        """;

    public static void show(JFrame owner) {
        JDialog d = new JDialog(owner, "规则说明", true);
        JEditorPane pane = new JEditorPane("text/html", HTML);
        pane.setEditable(false);
        pane.setBackground(new Color(0x121A16));
        pane.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        pane.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        JScrollPane sp = new JScrollPane(pane);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        d.setLayout(new BorderLayout());
        d.add(sp, BorderLayout.CENTER);
        d.setSize(new Dimension(720, 680));
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
    }
}
