#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
8-BALL CLUB 在线对战服务器（零第三方依赖）。

- 数据库：SQLite（用户表 + 会话表），密码用 PBKDF2-SHA256 加盐哈希。
- HTTP：ThreadingHTTPServer（静态网页 + JSON API）。
- 实时：Server-Sent Events（SSE），服务器向客户端推送对局事件。
- 房间/匹配：内存中排队，两名玩家匹配后创建对局（后续轮次接入权威物理）。

用法：python server.py [端口]   （默认 8080）
"""
import hashlib
import json
import os
import re
import secrets
import sqlite3
import sys
import threading
import time
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from game import Shot, Track, advance, all_stopped, snapshot
from match import Match
from ai import AI

ROOT = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(ROOT, "data")
WEB = os.path.join(ROOT, "web")
os.makedirs(DATA, exist_ok=True)
DB_PATH = os.path.join(DATA, "pool.db")

# ----------------------------------------------------------------- database

def db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    with db() as c:
        c.execute("""CREATE TABLE IF NOT EXISTS users(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE NOT NULL,
            pass_hash TEXT NOT NULL,
            created_at INTEGER NOT NULL)""")
        c.execute("""CREATE TABLE IF NOT EXISTS sessions(
            token TEXT PRIMARY KEY,
            user_id INTEGER NOT NULL,
            created_at INTEGER NOT NULL)""")
        cols = [r["name"] for r in c.execute("PRAGMA table_info(users)")]
        if "is_guest" not in cols:
            c.execute("ALTER TABLE users ADD COLUMN is_guest INTEGER NOT NULL DEFAULT 0")


init_db()


def is_guest(user_id):
    with db() as c:
        row = c.execute("SELECT is_guest FROM users WHERE id=?", (user_id,)).fetchone()
    return bool(row and row["is_guest"])


def create_guest():
    for _ in range(6):
        name = "游客" + secrets.token_hex(3).upper()
        try:
            with db() as c:
                cur = c.execute(
                    "INSERT INTO users(username, pass_hash, created_at, is_guest) VALUES(?,?,?,1)",
                    (name, "", int(time.time())))
                return cur.lastrowid, name
        except sqlite3.IntegrityError:
            continue
    return None, None


def hash_password(pw, salt):
    return hashlib.pbkdf2_hmac("sha256", pw.encode("utf-8"), salt.encode("utf-8"), 200_000).hex()


def create_user(username, password):
    salt = secrets.token_hex(16)
    h = salt + "$" + hash_password(password, salt)
    try:
        with db() as c:
            cur = c.execute("INSERT INTO users(username, pass_hash, created_at) VALUES(?,?,?)",
                            (username, h, int(time.time())))
            return cur.lastrowid
    except sqlite3.IntegrityError:
        return None


def verify_user(username, password):
    with db() as c:
        row = c.execute("SELECT * FROM users WHERE username=?", (username,)).fetchone()
    if not row:
        return None
    salt, h = row["pass_hash"].split("$", 1)
    if secrets.compare_digest(hash_password(password, salt), h):
        return row["id"]
    return None


def create_session(user_id):
    token = secrets.token_hex(32)
    with db() as c:
        c.execute("INSERT INTO sessions(token, user_id, created_at) VALUES(?,?,?)",
                  (token, user_id, int(time.time())))
    return token


def session_user(token):
    if not token:
        return None
    with db() as c:
        row = c.execute("SELECT user_id FROM sessions WHERE token=?", (token,)).fetchone()
    return row["user_id"] if row else None


def username_of(user_id):
    with db() as c:
        row = c.execute("SELECT username FROM users WHERE id=?", (user_id,)).fetchone()
    return row["username"] if row else "?"


# ------------------------------------------------------- rooms & matchmaking

class Room:
    def __init__(self, room_id, p1, p2):
        self.id = room_id
        self.players = [p1, p2]
        self.match = Match(secrets.randbits(63), names=(username_of(p1), username_of(p2)))
        self.busy = False

    def state(self):
        st = self.match.state()
        st["id"] = self.id
        return st


ROOMS = {}          # room_id -> Room
PLAYER_ROOM = {}    # user_id -> room_id
WAITING = []        # [user_id]
SSE_CLIENTS = {}    # user_id -> [queue]
LOCK = threading.Lock()


def push(user_id, event):
    with LOCK:
        qs = SSE_CLIENTS.get(user_id, [])
    for q in qs:
        q.append(event)


def broadcast(room, event):
    for pid in room.players:
        push(pid, event)


def simulate_and_broadcast(room, shot):
    """服务端权威：应用击球 -> 实时推演 -> 每 30Hz 广播球位 -> 结算广播。"""
    m = room.match
    try:
        m.arm_shot(shot)
        track = Track()
        dt = 1.0 / 120.0
        last = 0.0
        for _ in range(6000):
            advance(m.balls, dt, track)
            if all_stopped(m.balls):
                break
            now = time.time()
            if now - last >= 1.0 / 30.0:
                last = now
                broadcast(room, {"type": "frame", "balls": snapshot(m.balls)})
        m.resolve(track, shot)
    except Exception as e:  # 兜底，避免线程崩溃卡住房间
        m.log.append("服务器错误：" + str(e))
    finally:
        room.busy = False
        broadcast(room, {"type": "state", "state": room.state()})


# ------------------------------------------------------- 人机对战

AI_GAMES = {}          # user_id -> {"match": Match, "level": int, "busy": bool}


def ai_public_state(g):
    st = g["match"].state()
    st["id"] = "ai"
    st["ai"] = True
    st["level"] = g["level"]
    return st


def _stream_shot(user_id, m, shot):
    m.arm_shot(shot)
    track = Track()
    dt = 1.0 / 120.0
    last = 0.0
    for _ in range(7000):
        advance(m.balls, dt, track)
        if all_stopped(m.balls):
            break
        now = time.time()
        if now - last >= 1.0 / 30.0:
            last = now
            push(user_id, {"type": "frame", "balls": snapshot(m.balls)})
    return m.resolve(track, shot)


def _ai_worker(user_id, g, human_shot):
    m = g["match"]
    try:
        _stream_shot(user_id, m, human_shot)
        push(user_id, {"type": "state", "state": ai_public_state(g)})
        guard = 0
        while m.current != 0 and m.winner < 0 and guard < 24:
            guard += 1
            push(user_id, {"type": "ai_thinking", "state": ai_public_state(g)})
            time.sleep(0.8)
            shot = AI.choose(m, g["level"])
            _stream_shot(user_id, m, shot)
            push(user_id, {"type": "state", "state": ai_public_state(g)})
    except Exception as e:
        m.log.append("AI 错误：" + str(e))
    finally:
        g["busy"] = False
        push(user_id, {"type": "state", "state": ai_public_state(g)})


# --------------------------------------------------------------- http server

class Handler(BaseHTTPRequestHandler):
    server_version = "8BallClub/1.0"

    def log_message(self, fmt, *args):
        sys.stderr.write("[%s] %s\n" % (time.strftime("%H:%M:%S"), fmt % args))

    # ---- helpers
    def _send(self, code, body, ctype="application/json; charset=utf-8"):
        data = body if isinstance(body, (bytes, bytearray)) else json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def _json_ok(self, obj):
        self._send(200, obj)

    def _err(self, code, msg):
        self._send(code, {"error": msg})

    def _body_json(self):
        n = int(self.headers.get("Content-Length") or 0)
        if n <= 0:
            return {}
        try:
            return json.loads(self.rfile.read(n).decode("utf-8") or "{}")
        except Exception:
            return {}

    def _auth_user(self):
        q = urllib.parse.urlparse(self.path).query
        token = urllib.parse.parse_qs(q).get("token", [None])[0]
        if token is None:
            token = self.headers.get("Authorization", "").replace("Bearer ", "").strip()
        return session_user(token)

    # ---- static
    def _serve_static(self):
        rel = urllib.parse.urlparse(self.path).path
        if rel == "/":
            rel = "/index.html"
        path = os.path.normpath(os.path.join(WEB, rel.lstrip("/")))
        if not path.startswith(WEB) or not os.path.isfile(path):
            self._err(404, "not found")
            return
        ext = os.path.splitext(path)[1].lower()
        ctype = {".html": "text/html; charset=utf-8", ".js": "application/javascript; charset=utf-8",
                 ".css": "text/css; charset=utf-8", ".png": "image/png", ".svg": "image/svg+xml",
                 ".ico": "image/x-icon"}.get(ext, "application/octet-stream")
        with open(path, "rb") as f:
            data = f.read()
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(data)

    # ---- routes
    def do_GET(self):
        path = urllib.parse.urlparse(self.path).path
        if path == "/api/events":
            self.sse_events()
        elif path == "/api/me":
            uid = self._auth_user()
            if uid:
                self._json_ok({"user": username_of(uid), "guest": is_guest(uid)})
            else:
                self._json_ok({"user": None})
        elif path == "/api/health":
            self._json_ok({"ok": True, "rooms": len(ROOMS), "waiting": len(WAITING)})
        elif path == "/api/ai/state":
            self.ai_state()
        else:
            self._serve_static()

    def do_POST(self):
        path = urllib.parse.urlparse(self.path).path
        if path == "/api/register":
            self.register()
        elif path == "/api/login":
            self.login()
        elif path == "/api/guest":
            self.guest_login()
        elif path == "/api/logout":
            self.logout()
        elif path == "/api/match/join":
            self.match_join()
        elif path == "/api/match/shoot":
            self.match_shoot()
        elif path == "/api/ai/new":
            self.ai_new()
        elif path == "/api/ai/shoot":
            self.ai_shoot()
        else:
            self._err(404, "not found")

    # ---- auth
    def register(self):
        b = self._body_json()
        name = (b.get("username") or "").strip()
        pw = b.get("password") or ""
        if not re.fullmatch(r"[A-Za-z0-9_\u4e00-\u9fa5]{2,16}", name):
            self._err(400, "昵称需为 2-16 位中英文/数字/下划线")
            return
        if len(pw) < 6:
            self._err(400, "密码至少 6 位")
            return
        uid = create_user(name, pw)
        if uid is None:
            self._err(409, "该昵称已被注册")
            return
        self._json_ok({"token": create_session(uid), "user": name, "guest": False})

    def guest_login(self):
        uid, name = create_guest()
        if uid is None:
            self._err(500, "游客创建失败，请重试")
            return
        self._json_ok({"token": create_session(uid), "user": name, "guest": True})

    def login(self):
        b = self._body_json()
        uid = verify_user((b.get("username") or "").strip(), b.get("password") or "")
        if uid is None:
            self._err(401, "昵称或密码错误")
            return
        self._json_ok({"token": create_session(uid), "user": username_of(uid), "guest": is_guest(uid)})

    def logout(self):
        q = urllib.parse.urlparse(self.path).query
        token = urllib.parse.parse_qs(q).get("token", [None])[0]
        if token:
            with db() as c:
                c.execute("DELETE FROM sessions WHERE token=?", (token,))
        self._json_ok({"ok": True})

    # ---- matchmaking
    def match_join(self):
        uid = self._auth_user()
        if not uid:
            self._err(401, "请先登录")
            return
        matched_room = None
        in_room_state = None
        with LOCK:
            existing = PLAYER_ROOM.get(uid)
            if existing:
                in_room_state = ROOMS[existing].state()
            else:
                for other in WAITING:
                    if other != uid:
                        WAITING.remove(other)
                        room_id = secrets.token_hex(8)
                        room = Room(room_id, other, uid)
                        ROOMS[room_id] = room
                        PLAYER_ROOM[other] = room_id
                        PLAYER_ROOM[uid] = room_id
                        matched_room = room
                        break
                if matched_room is None:
                    WAITING.append(uid)
        # 广播必须放在锁外，避免与 push() 的重入锁死锁
        if in_room_state is not None:
            self._json_ok({"status": "in_room", "state": in_room_state})
        elif matched_room is not None:
            broadcast(matched_room, {"type": "matched", "state": matched_room.state()})
            self._json_ok({"status": "matched", "state": matched_room.state()})
        else:
            self._json_ok({"status": "waiting"})

    def match_shoot(self):
        uid = self._auth_user()
        if not uid:
            self._err(401, "请先登录")
            return
        room_id = PLAYER_ROOM.get(uid)
        if not room_id:
            self._err(409, "尚未在对局中")
            return
        room = ROOMS[room_id]
        idx = room.players.index(uid)
        m = room.match
        if idx != m.current:
            self._err(409, "还没轮到你")
            return
        if room.busy:
            self._err(409, "正在处理上一杆")
            return
        b = self._body_json()
        cue = m.balls[0]
        shot = Shot(
            float(b.get("x", cue.x)),
            float(b.get("y", cue.y)),
            float(b.get("angle", 0.0)),
            max(0.05, min(1.0, float(b.get("power", 0.5)))),
            float(b.get("spinX", 0.0)),
            float(b.get("spinY", 0.0)),
            int(b.get("calledPocket", -1)),
        )
        room.busy = True
        threading.Thread(target=simulate_and_broadcast, args=(room, shot), daemon=True).start()
        self._json_ok({"status": "ok"})

    # ---- 人机对战
    def ai_new(self):
        uid = self._auth_user()
        if not uid:
            self._err(401, "请先登录")
            return
        b = self._body_json()
        level = int(b.get("level", 1))
        level = max(0, min(2, level))
        with LOCK:
            g = AI_GAMES.get(uid)
            if g and g["match"].winner < 0 and not g["busy"]:
                g["level"] = level
                self._json_ok({"state": ai_public_state(g)})
                return
            m = Match(secrets.randbits(63), names=(username_of(uid), "电脑"))
            g = {"match": m, "level": level, "busy": False}
            AI_GAMES[uid] = g
        self._json_ok({"state": ai_public_state(g)})

    def ai_state(self):
        uid = self._auth_user()
        if not uid:
            self._err(401, "请先登录")
            return
        g = AI_GAMES.get(uid)
        if not g:
            self._err(409, "还没有人机对局")
            return
        self._json_ok({"state": ai_public_state(g)})

    def ai_shoot(self):
        uid = self._auth_user()
        if not uid:
            self._err(401, "请先登录")
            return
        g = AI_GAMES.get(uid)
        if not g:
            self._err(409, "还没有人机对局")
            return
        m = g["match"]
        if m.current != 0:
            self._err(409, "还没轮到你")
            return
        if g["busy"]:
            self._err(409, "正在处理上一杆")
            return
        b = self._body_json()
        cue = m.balls[0]
        shot = Shot(
            float(b.get("x", cue.x)),
            float(b.get("y", cue.y)),
            float(b.get("angle", 0.0)),
            max(0.05, min(1.0, float(b.get("power", 0.5)))),
            float(b.get("spinX", 0.0)),
            float(b.get("spinY", 0.0)),
            int(b.get("calledPocket", -1)),
        )
        g["busy"] = True
        threading.Thread(target=_ai_worker, args=(uid, g, shot), daemon=True).start()
        self._json_ok({"status": "ok"})

    # ---- SSE
    def sse_events(self):
        uid = self._auth_user()
        if not uid:
            self._err(401, "请先登录")
            return
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "keep-alive")
        self.end_headers()
        q = []
        with LOCK:
            SSE_CLIENTS.setdefault(uid, []).append(q)
        try:
            self.wfile.write(b": connected\n\n")
            self.wfile.flush()
            last_keepalive = time.time()
            while True:
                if q:
                    ev = q.pop(0)
                    self.wfile.write(("data: %s\n\n" % json.dumps(ev, ensure_ascii=False)).encode("utf-8"))
                    self.wfile.flush()
                else:
                    time.sleep(0.05)
                if time.time() - last_keepalive > 15:
                    self.wfile.write(b": ping\n\n")
                    self.wfile.flush()
                    last_keepalive = time.time()
        except (BrokenPipeError, ConnectionResetError, OSError):
            pass
        finally:
            with LOCK:
                lst = SSE_CLIENTS.get(uid, [])
                if q in lst:
                    lst.remove(q)


def main():
    if len(sys.argv) > 1:
        port = int(sys.argv[1])
    else:
        port = int(os.environ.get("PORT", "8080"))
    srv = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    print("8-BALL CLUB server listening on http://0.0.0.0:%d  (db=%s)" % (port, DB_PATH))
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
