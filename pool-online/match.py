# -*- coding: utf-8 -*-
"""8-ball 规则与对局状态机（服务端权威）。"""
import math

from game import (R, W, H, HEAD_X, FOOT_X, POCKETS, Ball, Track, rack, strike,
                  advance, all_stopped, snapshot)

OPEN, SOLIDS, STRIPES = "OPEN", "SOLIDS", "STRIPES"


def in_group(bid, g):
    if g == SOLIDS:
        return 1 <= bid <= 7
    if g == STRIPES:
        return 9 <= bid <= 15
    return False


def remaining(balls, g):
    return sum(1 for b in balls if b.active and in_group(b.id, g))


class Match:
    def __init__(self, seed, names=("房主", "挑战者")):
        self.balls = rack(seed)
        self.groups = [OPEN, OPEN]
        self.score = [0, 0]
        self.names = list(names)
        self.current = 0
        self.open_table = True
        self.ball_in_hand = True
        self.kitchen_only = True
        self.break_shot = True
        self.winner = -1
        self.called_pocket = -1
        self.shot_count = 0
        self.message = "开球：把母球放在开球线后击球"
        self.log = ["新的一局开始"]

    # ---------------- 状态输出 ----------------
    def state(self):
        return {
            "players": list(self.names),
            "turn": self.current,
            "groups": list(self.groups),
            "openTable": self.open_table,
            "ballInHand": self.ball_in_hand,
            "kitchenOnly": self.kitchen_only,
            "winner": self.winner,
            "calledPocket": self.called_pocket,
            "score": list(self.score),
            "balls": snapshot(self.balls),
            "message": self.message,
            "log": self.log[-6:],
        }

    # ---------------- 摆放 ----------------
    def can_place_cue(self, x, y):
        if not (R + 0.0015 <= x <= W - R - 0.0015 and R + 0.0015 <= y <= H - R - 0.0015):
            return False
        if self.kitchen_only and x > HEAD_X - 0.002:
            return False
        for px, py in POCKETS:
            if math.hypot(x - px, y - py) < 0.089:
                return False
        for b in self.balls:
            if b.active and b.id != 0 and math.hypot(b.x - x, b.y - y) < 2 * R + 0.0012:
                return False
        return True

    def place_cue(self, x, y):
        cue = self.balls[0]
        cue.place(x, y)

    def free_spot(self):
        x0 = HEAD_X - 0.04 if self.kitchen_only else W / 2
        y0 = H / 2
        if self.can_place_cue(x0, y0):
            return x0, y0
        d = 0.03
        while d < 1.4:
            for sy in (-1, 1):
                y = y0 + sy * d
                if not (R + 0.005 <= y <= H - R - 0.005):
                    continue
                for sx in (-1, 1):
                    x = x0 + sx * d
                    if R + 0.005 <= x <= W - R - 0.005 and self.can_place_cue(x, y):
                        return x, y
            d += 0.03
        return R + 0.01, H / 2

    def ensure_cue(self):
        cue = self.balls[0]
        if not cue.active:
            cue.place(*self.free_spot())

    def respot_eight(self):
        e = self.balls[8]
        e.stop()
        e.active = True
        x = FOOT_X
        for i in range(80):
            cand = FOOT_X + i * 0.01
            if cand > W - R - 0.005:
                break
            if all(not b.active or b.id == 8 or math.hypot(b.x - cand, b.y - H / 2) >= 2 * R + 0.0008 for b in self.balls):
                x = cand
                break
        e.place(x, H / 2)

    # ---------------- 击球 ----------------
    def arm_shot(self, shot):
        self.called_pocket = shot.called_pocket
        if self.ball_in_hand:
            if not self.can_place_cue(shot.x, shot.y):
                shot.x, shot.y = self.free_spot()
            self.place_cue(shot.x, shot.y)
        self.ball_in_hand = False
        self.kitchen_only = False
        strike(self.balls, shot)
        self.shot_count += 1

    # ---------------- 规则 ----------------
    def _remaining_before(self, g, track):
        n = remaining(self.balls, g)
        for bid, _ in track.potted:
            if in_group(bid, g):
                n += 1
        return n

    def resolve(self, track, shot):
        o = self._evaluate(track, shot)
        shooter = self.current
        other = 1 - shooter
        if o["assigned"] is not None:
            self.groups[shooter] = o["assigned"]
            self.groups[other] = STRIPES if o["assigned"] == SOLIDS else SOLIDS
            self.open_table = False
            self.log.append(self.names[shooter] + " 选定 " + ("全色" if o["assigned"] == SOLIDS else "花色"))
        self.score[shooter] += o["own_potted"]
        if o["respot_eight"]:
            self.respot_eight()
            self.log.append("开球击落 8 号球，重新摆放")
        if o["winner"] >= 0:
            self.winner = o["winner"]
            self.message = o["reason"] + " —— " + self.names[self.winner] + " 获胜"
            self.log.append(self.message)
            return o
        if o["foul"]:
            self.current = other
            self.ball_in_hand = True
            self.kitchen_only = self.break_shot
            self.ensure_cue()
            self.message = "犯规：" + o["reason"] + " → " + self.names[self.current] + " 自由球"
            self.log.append(self.message)
        elif o["continue"]:
            self.message = ("进球 +%d，%s 继续" % (o["own_potted"], self.names[self.current])) if o["own_potted"] else self.names[self.current] + " 继续"
            self.log.append(self.message)
        else:
            self.current = other
            self.message = self.names[self.current] + " 上场"
            self.log.append(self.message)
        self.break_shot = False
        self.called_pocket = -1
        return o

    def _evaluate(self, t, shot):
        o = {"foul": False, "reason": "", "winner": -1, "own_potted": 0, "opp_potted": 0,
             "assigned": None, "respot_eight": False, "continue": False}
        if self.break_shot and not t.any_potted() and len(t.rail_balls) < 4:
            o["foul"] = True
            o["reason"] = "开球不合法（未进球且碰库不足 4 颗）"
        if t.first_hit < 0:
            o["foul"] = True
            o["reason"] = "空杆：母球没碰到任何球"
        elif self.open_table:
            if t.first_hit == 8:
                o["foul"] = True
                o["reason"] = "开放球台不能先碰 8 号球"
        else:
            g = self.groups[self.current]
            if self._remaining_before(g, t) > 0:
                if not in_group(t.first_hit, g):
                    o["foul"] = True
                    o["reason"] = "首先击中的不是自己的球"
            elif t.first_hit != 8:
                o["foul"] = True
                o["reason"] = "已清台，必须先击打 8 号球"
        if t.cue_potted:
            o["foul"] = True
            o["reason"] = "母球落袋"
        if not t.cue_potted and not t.any_potted() and not t.rail_after:
            o["foul"] = True
            o["reason"] = "击球后没有球碰库"

        eight_pocket = t.pocket_of(8)
        if eight_pocket >= 0:
            if self.break_shot:
                o["respot_eight"] = True
                o["continue"] = True
            else:
                cleared = self.groups[self.current] != OPEN and self._remaining_before(self.groups[self.current], t) == 0
                legal = (not o["foul"]) and cleared and shot.called_pocket == eight_pocket
                o["winner"] = self.current if legal else 1 - self.current
                if legal:
                    o["reason"] = "8 号球入袋，本局胜利"
                elif o["foul"]:
                    o["reason"] = "犯规同时击落 8 号球"
                elif not cleared:
                    o["reason"] = "过早击落 8 号球"
                else:
                    o["reason"] = "8 号球未进指定袋口"
                return o

        if self.open_table and not o["foul"] and not self.break_shot:
            for bid, _ in t.potted:
                if bid != 0 and bid != 8:
                    o["assigned"] = SOLIDS if bid <= 7 else STRIPES
                    break
        if self.open_table:
            o["own_potted"] = sum(1 for b, _ in t.potted if b != 0 and b != 8)
        else:
            g = self.groups[self.current]
            for bid, _ in t.potted:
                if bid == 0 or bid == 8:
                    continue
                if in_group(bid, g):
                    o["own_potted"] += 1
                else:
                    o["opp_potted"] += 1
        if not o["foul"] and o["own_potted"] > 0:
            o["continue"] = True
        if self.break_shot and not o["foul"] and t.any_potted():
            o["continue"] = True
        return o
