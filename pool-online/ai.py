# -*- coding: utf-8 -*-
"""电脑对手：枚举「目标球 × 袋口」，用与真实对局相同的物理推演打分，取最优杆。"""
import copy
import math
import random

from game import R, W, H, Shot, Track, advance, all_stopped
from match import Match, OPEN, SOLIDS, STRIPES, in_group, remaining

HEAD_X = W * 0.25


def _clone(m):
    return copy.deepcopy(m)


def _clear(balls, x1, y1, x2, y2, ignore_a, ignore_b):
    dx, dy = x2 - x1, y2 - y1
    l2 = dx * dx + dy * dy
    if l2 < 1e-12:
        return True
    for b in balls:
        if not b.active or b.id in (ignore_a, ignore_b) or b.id == 0:
            continue
        t = ((b.x - x1) * dx + (b.y - y1) * dy) / l2
        if t <= 0.02 or t >= 0.98:
            continue
        px, py = x1 + t * dx, y1 + t * dy
        if math.hypot(b.x - px, b.y - py) < 2 * R * 0.96:
            return False
    return True


def _targets(m):
    out = []
    if m.open_table:
        out = [i for i in range(1, 16) if i != 8 and m.balls[i].active]
    else:
        g = m.groups[m.current]
        out = [i for i in range(1, 16) if m.balls[i].active and in_group(i, g)]
        if not out:
            out = [8]
    return out or [8]


def _on_eight(m):
    g = m.groups[m.current]
    return g != OPEN and remaining(m.balls, g) == 0


class AI:
    EASY, NORMAL, HARD = 0, 1, 2

    @staticmethod
    def choose(m, level=1, rnd=None):
        rnd = rnd or random.Random()
        if m.break_shot:
            return AI._break(m, rnd)
        cands = AI._candidates(m)
        cands.sort(key=lambda c: -c["geom"])
        limit = 5 if level == 0 else 10 if level == 1 else 18
        cands = cands[:limit]
        best = None
        best_score = -1e9
        best_t, best_p = 0, -1
        variants = 1 if level == 0 else 3 if level == 1 else 4
        offs = [0.0, 0.008, -0.008, 0.016, -0.016]
        pms = [1.0, 1.35, 0.75]
        for c in cands:
            for i in range(variants):
                for k in range(3 if level == AI.HARD else 2):
                    shot = Shot(c["cx"], c["cy"], c["angle"] + offs[i],
                                min(1.0, c["power"] * pms[k]), 0, 0, c["pocket"])
                    sc = AI._score(m, shot, c["target"], c["pocket"])
                    if sc > best_score:
                        best_score, best, best_t, best_p = sc, shot, c["target"], c["pocket"]
        if best is None or best_score < -600:
            safe = AI._safety(m, level, rnd)
            if safe:
                return safe
        if best is None:
            return AI._fallback(m)
        if _on_eight(m):
            best.called_pocket = AI._nearest(m, 8)
        sigma = 0.030 if level == 0 else 0.010 if level == 1 else 0.0028
        best.angle += rnd.gauss(0, sigma)
        best.power = max(0.12, min(1.0, best.power * (1 + rnd.gauss(0, 0.15 if level == 0 else 0.05))))
        return best

    # ---------------- 候选 ----------------
    @staticmethod
    def _candidates(m):
        out = []
        cue = m.balls[0]
        bih = m.ball_in_hand or not cue.active
        for tid in _targets(m):
            t = m.balls[tid]
            for pk in range(6):
                px, py = [(0, 0), (W, 0), (0, H), (W, H), (W / 2, 0), (W / 2, H)][pk]
                dx, dy = px - t.x, py - t.y
                d = math.hypot(dx, dy)
                if d < 1e-6:
                    continue
                ux, uy = dx / d, dy / d
                gx, gy = t.x - ux * 2 * R, t.y - uy * 2 * R
                if not _clear(m.balls, t.x, t.y, px, py, tid, 0):
                    continue
                if bih:
                    for dist in (0.32, 0.52, 0.75):
                        cx = t.x - ux * (2 * R + dist)
                        cy = t.y - uy * (2 * R + dist)
                        cx = max(R + 0.01, min(W - R - 0.01, cx))
                        cy = max(R + 0.01, min(H - R - 0.01, cy))
                        if not m.can_place_cue(cx, cy):
                            continue
                        if not _clear(m.balls, cx, cy, gx, gy, tid, 0):
                            continue
                        AI._add(out, m, tid, pk, cx, cy, gx, gy, ux, uy, t, px, py)
                else:
                    if not cue.active:
                        continue
                    if math.hypot(gx - cue.x, gy - cue.y) < 0.02:
                        continue
                    if not _clear(m.balls, cue.x, cue.y, gx, gy, tid, 0):
                        continue
                    AI._add(out, m, tid, pk, cue.x, cue.y, gx, gy, ux, uy, t, px, py)
        return out

    @staticmethod
    def _add(out, m, tid, pk, cx, cy, gx, gy, ux, uy, t, px, py):
        ax, ay = gx - cx, gy - cy
        ad = math.hypot(ax, ay)
        if ad < 0.02:
            return
        cut = (ax / ad) * ux + (ay / ad) * uy
        if cut < 0.18:
            return
        dist_t = math.hypot(t.x - cx, t.y - cy)
        dist_p = math.hypot(px - t.x, py - t.y)
        out.append({
            "cx": cx, "cy": cy, "angle": math.atan2(ay, ax),
            "power": max(0.22, min(0.95, 0.26 + dist_t * 0.10 + dist_p * 0.11 + (1 - cut) * 0.25)),
            "target": tid, "pocket": pk,
            "geom": cut * 2.0 - dist_t * 0.35 - dist_p * 0.55,
        })

    # ---------------- 推演评分 ----------------
    @staticmethod
    def _score(m, shot, target, pocket):
        sim = _clone(m)
        try:
            sim.arm_shot(shot)
        except Exception:
            return -1e9
        track = Track()
        for _ in range(6000):
            advance(sim.balls, 1 / 120, track)
            if all_stopped(sim.balls):
                break
        o = sim.resolve(track, shot)
        sc = 0
        if o["winner"] == m.current:
            sc += 6000
        if o["winner"] == 1 - m.current:
            sc -= 6000
        if o["foul"]:
            sc -= 500
        sc += o["own_potted"] * 150 - o["opp_potted"] * 45
        if track.cue_potted:
            sc -= 300
        if target and track.pocket_of(target) == pocket:
            sc += 90
        if target == 8 and not _on_eight(m) and track.pocket_of(8) >= 0:
            sc -= 900
        return sc

    # ---------------- 兜底 ----------------
    @staticmethod
    def _safety(m, level, rnd):
        targets = _targets(m)
        cue = m.balls[0]
        if not cue.active:
            m.ensure_cue()
            cue = m.balls[0]
        best, best_sc = None, -1e9
        for tid in targets:
            t = m.balls[tid]
            base = math.atan2(t.y - cue.y, t.x - cue.x)
            for da in (0, 0.02, -0.02, 0.05, -0.05):
                for p in (0.28, 0.42, 0.6):
                    shot = Shot(cue.x, cue.y, base + da, p, 0, 0, -1)
                    sc = AI._score(m, shot, tid, -1)
                    sc -= abs(da) * 100
                    if sc > best_sc:
                        best_sc, best = sc, shot
        if best and level == 0:
            best.angle += rnd.gauss(0, 0.03)
        return best

    @staticmethod
    def _fallback(m):
        cue = m.balls[0]
        return Shot(cue.x, cue.y, 0, 0.3, 0, 0, -1)

    @staticmethod
    def _break(m, rnd):
        apex = min((b for b in m.balls if b.active and b.id != 0), key=lambda b: b.x, default=None)
        cue = m.balls[0]
        cx, cy = cue.x, cue.y
        if m.ball_in_hand:
            cx, cy = HEAD_X - 0.06, H / 2
        ang = math.atan2(apex.y - cy, apex.x - cx) if apex else 0.0
        ang += (rnd.random() - 0.5) * 0.012
        return Shot(cx, cy, ang, 0.93, 0, 0, -1)

    @staticmethod
    def _nearest(m, bid):
        b = m.balls[bid]
        return min(range(6), key=lambda k: math.hypot(b.x - [(0, 0), (W, 0), (0, H), (W, H), (W / 2, 0), (W / 2, H)][k][0],
                                                    b.y - [(0, 0), (W, 0), (0, H), (W, H), (W / 2, 0), (W / 2, H)][k][1]))
