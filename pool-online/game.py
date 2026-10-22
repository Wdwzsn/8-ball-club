# -*- coding: utf-8 -*-
"""8-ball 物理引擎与规则（Python 移植，与 Java 桌面版同一套逻辑）。

单位：米、秒。球半径 28.575mm，九尺台 2.54 x 1.27m。
滑动/滚动两阶段摩擦 + 球球弹性碰撞 + 库边反弹 + 落袋判定。
"""
import math
import random

R = 0.028575
G = 9.81
MU_SLIDE = 0.19
MU_ROLL = 0.017
E_BALL = 0.95
E_CUSHION = 0.74
CUSHION_FRICTION = 0.17
SPIN_DECAY = 0.42
MAX_CUE_SPEED = 8.6

W = 2.54
H = 1.27
CORNER_JAW = 0.079
SIDE_JAW = 0.067
HEAD_X = W * 0.25
FOOT_X = W * 0.75

RAILS = [
    (CORNER_JAW, 0, W / 2 - SIDE_JAW, 0, 0, 1),
    (W / 2 + SIDE_JAW, 0, W - CORNER_JAW, 0, 0, 1),
    (CORNER_JAW, H, W / 2 - SIDE_JAW, H, 0, -1),
    (W / 2 + SIDE_JAW, H, W - CORNER_JAW, H, 0, -1),
    (0, CORNER_JAW, 0, H - CORNER_JAW, 1, 0),
    (W, CORNER_JAW, W, H - CORNER_JAW, -1, 0),
]
POCKETS = [(0, 0), (W, 0), (0, H), (W, H), (W / 2, 0), (W / 2, H)]


class Ball:
    __slots__ = ("id", "x", "y", "vx", "vy", "wx", "wy", "wz", "active")

    def __init__(self, bid):
        self.id = bid
        self.x = self.y = self.vx = self.vy = self.wx = self.wy = self.wz = 0.0
        self.active = True

    def place(self, x, y):
        self.x, self.y = x, y
        self.vx = self.vy = self.wx = self.wy = self.wz = 0.0
        self.active = True

    def speed(self):
        return math.hypot(self.vx, self.vy)

    def stop(self):
        self.vx = self.vy = self.wx = self.wy = self.wz = 0.0

    def moving(self):
        return self.active and (self.speed() > 0.0035 or abs(self.wz) > 1.2)


class Shot:
    __slots__ = ("x", "y", "angle", "power", "spinX", "spinY", "called_pocket")

    def __init__(self, x, y, angle, power, spinX=0.0, spinY=0.0, called_pocket=-1):
        self.x, self.y = x, y
        self.angle = angle
        self.power = power
        self.spinX = spinX
        self.spinY = spinY
        self.called_pocket = called_pocket


class Track:
    """单杆过程记录，规则判定用。"""
    __slots__ = ("first_hit", "rail_after", "rail_balls", "potted", "cue_potted")

    def __init__(self):
        self.first_hit = -1
        self.rail_after = False
        self.rail_balls = set()
        self.potted = []   # [(ball_id, pocket)]
        self.cue_potted = False

    def hit(self, a, b):
        if self.first_hit < 0 and (a == 0 or b == 0):
            self.first_hit = b if a == 0 else a

    def rail(self, bid):
        self.rail_balls.add(bid)
        if self.first_hit >= 0:
            self.rail_after = True

    def pot(self, bid, pocket):
        self.potted.append((bid, pocket))
        if bid == 0:
            self.cue_potted = True

    def pocket_of(self, bid):
        for p, k in self.potted:
            if p == bid:
                return k
        return -1

    def any_potted(self):
        return any(b != 0 for b, _ in self.potted)


def pocket_at(x, y):
    if x + y < 0.098:
        return 0
    if (W - x) + y < 0.098:
        return 1
    if x + (H - y) < 0.098:
        return 2
    if (W - x) + (H - y) < 0.098:
        return 3
    if y <= R + 0.0015 and abs(x - W / 2) < SIDE_JAW:
        return 4
    if y >= H - R - 0.0015 and abs(x - W / 2) < SIDE_JAW:
        return 5
    return -1


def rack(seed):
    rnd = random.Random(seed)
    ids = [i for i in range(1, 16) if i != 8]
    rnd.shuffle(ids)
    solid = next(i for i in ids if i <= 7)
    stripe = next(i for i in ids if i >= 9)
    rest = [i for i in ids if i not in (solid, stripe)]
    d = R * 2 + 0.00016
    dx = d * math.sqrt(3) / 2
    order = [[0] * 5 for _ in range(5)]
    order[0][0] = rest[0]
    p = 1
    for row in range(1, 5):
        for j in range(row + 1):
            if row == 2 and j == 1:
                order[row][j] = 8
            elif row == 4 and j == 0:
                order[row][j] = solid
            elif row == 4 and j == 4:
                order[row][j] = stripe
            else:
                order[row][j] = rest[p]
                p += 1
    balls = [Ball(i) for i in range(16)]
    for row in range(5):
        for j in range(row + 1):
            b = balls[order[row][j]]
            b.place(FOOT_X + row * dx, H / 2 + (j - row / 2.0) * d)
    balls[0].place(HEAD_X, H / 2)
    return balls


def strike(balls, shot):
    cue = balls[0]
    cue.place(shot.x, shot.y)
    speed = max(0.12, shot.power * MAX_CUE_SPEED)
    dx, dy = math.cos(shot.angle), math.sin(shot.angle)
    cue.vx, cue.vy = dx * speed, dy * speed
    f = max(-0.55, min(2.2, 1.0 + 1.45 * shot.spinY))
    cue.wx = -cue.vy / R * f
    cue.wy = cue.vx / R * f
    cue.wz = -shot.spinX * 190.0 * min(1.0, shot.power)


def _friction(b, h):
    ux = b.vx - R * b.wy
    uy = b.vy + R * b.wx
    us = math.hypot(ux, uy)
    if us > 1e-5:
        desired = 3.5 * MU_SLIDE * G * h
        if desired >= us:
            t = us / (3.5 * MU_SLIDE * G)
            ax = -ux / us * MU_SLIDE * G
            ay = -uy / us * MU_SLIDE * G
            b.vx += ax * t
            b.vy += ay * t
            b.wx = -b.vy / R
            b.wy = b.vx / R
        else:
            ax = -ux / us * MU_SLIDE * G
            ay = -uy / us * MU_SLIDE * G
            ux2 = ux + (-ux / us) * desired
            uy2 = uy + (-uy / us) * desired
            b.vx += ax * h
            b.vy += ay * h
            b.wy = (b.vx - ux2) / R
            b.wx = (uy2 - b.vy) / R
    else:
        sp = math.hypot(b.vx, b.vy)
        if sp > 1e-6:
            dec = MU_ROLL * G * h
            if dec >= sp:
                b.vx = b.vy = 0.0
            else:
                b.vx -= b.vx / sp * dec
                b.vy -= b.vy / sp * dec
        b.wy = b.vx / R
        b.wx = -b.vy / R
    b.wz *= math.exp(-SPIN_DECAY * h)


def _collide_balls(balls, track):
    for i in range(16):
        a = balls[i]
        if not a.active:
            continue
        for j in range(i + 1, 16):
            b = balls[j]
            if not b.active:
                continue
            dx, dy = b.x - a.x, b.y - a.y
            d2 = dx * dx + dy * dy
            if d2 > 4 * R * R or d2 < 1e-12:
                continue
            d = math.sqrt(d2)
            nx, ny = dx / d, dy / d
            ov = 2 * R - d
            if ov > 0:
                a.x -= nx * ov * 0.5
                a.y -= ny * ov * 0.5
                b.x += nx * ov * 0.5
                b.y += ny * ov * 0.5
            rvx, rvy = b.vx - a.vx, b.vy - a.vy
            vn = rvx * nx + rvy * ny
            if vn >= 0:
                continue
            track.hit(a.id, b.id)
            jimp = -(1 + E_BALL) * vn * 0.5
            a.vx -= jimp * nx
            a.vy -= jimp * ny
            b.vx += jimp * nx
            b.vy += jimp * ny
            tx, ty = -ny, nx
            vt = rvx * tx + rvy * ty + R * (a.wz + b.wz) * 0.12
            jt = max(-0.10 * jimp, min(0.10 * jimp, -vt * 0.035))
            a.vx -= jt * tx
            a.vy -= jt * ty
            b.vx += jt * tx
            b.vy += jt * ty
            a.wz *= 0.96
            b.wz *= 0.96


def _collide_rails(balls, track):
    for b in balls:
        if not b.active:
            continue
        for ax, ay, bx, by, nx0, ny0 in RAILS:
            dx, dy = bx - ax, by - ay
            ln2 = dx * dx + dy * dy
            t = ((b.x - ax) * dx + (b.y - ay) * dy) / ln2
            if t <= 0 or t >= 1:
                continue
            cx, cy = ax + t * dx, ay + t * dy
            ddx, ddy = b.x - cx, b.y - cy
            dist = math.hypot(ddx, ddy)
            if dist >= R:
                continue
            if dist > 1e-9:
                nx, ny = ddx / dist, ddy / dist
            else:
                nx, ny = nx0, ny0
            if nx * nx0 + ny * ny0 < 0:
                nx, ny = nx0, ny0
            b.x += nx * (R - dist)
            b.y += ny * (R - dist)
            vn = b.vx * nx + b.vy * ny
            if vn < 0:
                tx, ty = -ny, nx
                vt = b.vx * tx + b.vy * ty
                kick = -0.085 * b.wz * R
                vn2 = -E_CUSHION * vn
                vt2 = vt * (1 - CUSHION_FRICTION) + kick
                b.vx = nx * vn2 + tx * vt2
                b.vy = ny * vn2 + ty * vt2
                b.wz *= 0.72
                track.rail(b.id)


def _check_pockets(balls, track):
    for b in balls:
        if not b.active:
            continue
        p = pocket_at(b.x, b.y)
        if p < 0 and (b.x < -0.06 or b.x > W + 0.06 or b.y < -0.06 or b.y > H + 0.06):
            p = min(range(6), key=lambda k: math.hypot(b.x - POCKETS[k][0], b.y - POCKETS[k][1]))
        if p >= 0:
            b.active = False
            b.stop()
            b.x, b.y = POCKETS[p]
            track.pot(b.id, p)


def _step(balls, h, track):
    for b in balls:
        if b.active:
            _friction(b, h)
            b.x += b.vx * h
            b.y += b.vy * h
    _collide_balls(balls, track)
    _collide_rails(balls, track)
    _check_pockets(balls, track)
    for b in balls:
        if b.active and math.hypot(b.vx, b.vy) < 0.004 and abs(b.wz) < 0.8:
            b.stop()


def advance(balls, dt, track=None):
    track = track or Track()
    maxv = 0.0
    for b in balls:
        if b.active:
            maxv = max(maxv, b.speed() + abs(b.wz) * R)
    if maxv < 1e-9:
        return
    steps = max(1, min(60, int(math.ceil(dt * maxv / (R * 0.45)))))
    h = dt / steps
    for _ in range(steps):
        _step(balls, h, track)


def all_stopped(balls):
    return not any(b.moving() for b in balls)


def snapshot(balls):
    return [{"id": b.id, "x": round(b.x, 6), "y": round(b.y, 6), "active": b.active} for b in balls]
