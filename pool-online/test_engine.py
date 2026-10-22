# -*- coding: utf-8 -*-
"""引擎冒烟测试：开球 + 若干随机杆。"""
import math
import random

from game import Shot, Track, advance, all_stopped, HEAD_X, H
from match import Match


def run_shot(m, shot):
    t = Track()
    for _ in range(9000):
        advance(m.balls, 1.0 / 240, t)
        if all_stopped(m.balls):
            break
    m.resolve(t, shot)


def main():
    m = Match(7)
    m.arm_shot(Shot(HEAD_X - 0.06, H / 2, 0.0, 0.95, 0, 0, -1))
    run_shot(m, Shot(HEAD_X - 0.06, H / 2, 0.0, 0.95, 0, 0, -1))
    print("break:", "foul" if m.log[-1].startswith("犯规") else "ok", "|", m.message)
    print("active:", sum(1 for b in m.balls if b.active), "groups:", m.groups)

    rnd = random.Random(1)
    for i in range(6):
        if m.winner >= 0:
            break
        cue = m.balls[0]
        if not cue.active:
            m.ensure_cue()
            cue = m.balls[0]
        targets = [b for b in m.balls if b.active and b.id not in (0, 8)] or [m.balls[8]]
        tgt = targets[0] if targets else m.balls[8]
        ang = math.atan2(tgt.y - cue.y, tgt.x - cue.x)
        sh = Shot(cue.x, cue.y, ang, 0.5 + rnd.random() * 0.3, 0, 0, -1)
        if m.ball_in_hand:
            sh.x, sh.y = m.free_spot()
        m.arm_shot(sh)
        run_shot(m, sh)
        print("shot", i + 1, "|", m.message[:48])
    print("final: winner", m.winner, "score", m.score)
    print("ENGINE OK")


if __name__ == "__main__":
    main()
