package com.pool8.rules;

/** 规则判定结果（纯数据，不修改比赛状态）。 */
public final class Outcome {
    public boolean foul;
    public String reason = "";
    public int winner = -1;
    public int ownPotted;
    public int oppPotted;
    public Group assigned;
    public boolean respotEight;
    public boolean continueTurn;
    public int eightPocket = -1;
    public int firstHit = -1;

    public void foul(String why) {
        if (!foul) reason = why;
        foul = true;
    }
}
