package com.pool8.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 单杆击球过程记录，规则判定所需的全部证据。 */
public final class ShotTracker {
    public static final class Pot {
        public final int ballId;
        public final int pocket;
        public Pot(int ballId, int pocket) { this.ballId = ballId; this.pocket = pocket; }
    }

    public int firstHit = -1;
    public boolean railAfterContact = false;
    public final Set<Integer> railBalls = new HashSet<>();
    public final List<Pot> potted = new ArrayList<>();
    public boolean cuePotted = false;

    public void reset() {
        firstHit = -1;
        railAfterContact = false;
        railBalls.clear();
        potted.clear();
        cuePotted = false;
    }

    public void hit(int a, int b) {
        if (firstHit < 0 && (a == 0 || b == 0)) {
            firstHit = (a == 0) ? b : a;
        }
    }

    public void rail(int ballId) {
        railBalls.add(ballId);
        if (firstHit >= 0) railAfterContact = true;
    }

    public void pot(int ballId, int pocket) {
        potted.add(new Pot(ballId, pocket));
        if (ballId == 0) cuePotted = true;
    }

    public int pocketOf(int ballId) {
        for (Pot p : potted) if (p.ballId == ballId) return p.pocket;
        return -1;
    }

    public boolean anyPotted() {
        for (Pot p : potted) if (p.ballId != 0) return true;
        return false;
    }
}
