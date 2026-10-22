package com.pool8.net;

import com.pool8.core.Ball;
import com.pool8.rules.Group;
import com.pool8.rules.Match;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** 联机同步用的完整局面快照（球位 + 规则状态）。 */
public final class Snapshot {
    public final double[] x = new double[16];
    public final double[] y = new double[16];
    public final boolean[] active = new boolean[16];
    public Group[] groups = {Group.OPEN, Group.OPEN};
    public boolean openTable, breakShot, ballInHand, kitchenOnly;
    public int current, winner, calledPocket, shotCount;
    public int score0, score1;
    public String message = "", event = "";
    public String name0 = "玩家 1", name1 = "玩家 2";

    public static Snapshot of(Match m) {
        Snapshot s = new Snapshot();
        for (int i = 0; i < 16; i++) {
            Ball b = m.world.balls[i];
            s.x[i] = b.x; s.y[i] = b.y; s.active[i] = b.active;
        }
        s.groups = new Group[]{m.groups[0], m.groups[1]};
        s.openTable = m.openTable;
        s.breakShot = m.breakShot;
        s.ballInHand = m.ballInHand;
        s.kitchenOnly = m.kitchenOnly;
        s.current = m.current;
        s.winner = m.winner;
        s.calledPocket = m.calledPocket;
        s.shotCount = m.shotCount;
        s.score0 = m.score[0];
        s.score1 = m.score[1];
        s.message = m.message;
        s.event = m.lastEvent;
        s.name0 = m.names[0];
        s.name1 = m.names[1];
        return s;
    }

    public void applyTo(Match m) {
        for (int i = 0; i < 16; i++) {
            Ball b = m.world.balls[i];
            b.x = x[i];
            b.y = y[i];
            b.active = active[i];
            b.pottedIn = -1;
            b.stop();
        }
        m.groups[0] = groups[0];
        m.groups[1] = groups[1];
        m.openTable = openTable;
        m.breakShot = breakShot;
        m.ballInHand = ballInHand;
        m.kitchenOnly = kitchenOnly;
        m.current = current;
        m.winner = winner;
        m.calledPocket = calledPocket;
        m.shotCount = shotCount;
        m.score[0] = score0;
        m.score[1] = score1;
        m.message = message;
        if (event != null && !event.isEmpty() && !event.equals(m.lastEvent)) {
            m.log.addFirst(event);
            while (m.log.size() > 8) m.log.removeLast();
        }
        m.lastEvent = event;
        m.names[0] = name0;
        m.names[1] = name1;
    }

    public void write(DataOutputStream out) throws IOException {
        for (int i = 0; i < 16; i++) {
            out.writeDouble(x[i]);
            out.writeDouble(y[i]);
            out.writeBoolean(active[i]);
        }
        out.writeByte(groups[0].ordinal());
        out.writeByte(groups[1].ordinal());
        out.writeBoolean(openTable);
        out.writeBoolean(breakShot);
        out.writeBoolean(ballInHand);
        out.writeBoolean(kitchenOnly);
        out.writeInt(current);
        out.writeInt(winner);
        out.writeInt(calledPocket);
        out.writeInt(shotCount);
        out.writeInt(score0);
        out.writeInt(score1);
        out.writeUTF(message);
        out.writeUTF(event);
        out.writeUTF(name0);
        out.writeUTF(name1);
    }

    public static Snapshot read(DataInputStream in) throws IOException {
        Snapshot s = new Snapshot();
        for (int i = 0; i < 16; i++) {
            s.x[i] = in.readDouble();
            s.y[i] = in.readDouble();
            s.active[i] = in.readBoolean();
        }
        s.groups = new Group[]{Group.values()[in.readByte()], Group.values()[in.readByte()]};
        s.openTable = in.readBoolean();
        s.breakShot = in.readBoolean();
        s.ballInHand = in.readBoolean();
        s.kitchenOnly = in.readBoolean();
        s.current = in.readInt();
        s.winner = in.readInt();
        s.calledPocket = in.readInt();
        s.shotCount = in.readInt();
        s.score0 = in.readInt();
        s.score1 = in.readInt();
        s.message = in.readUTF();
        s.event = in.readUTF();
        s.name0 = in.readUTF();
        s.name1 = in.readUTF();
        return s;
    }
}
