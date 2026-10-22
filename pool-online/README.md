# 8-BALL CLUB · 在线对战（前后端）

真正的**跨电脑在线台球对战** + 账号体系。零第三方依赖：后端用 Python 标准库 + SQLite，
前端是纯 HTML5 Canvas 单页。

- **数据库**：SQLite（`data/pool.db`）——`users` 表（昵称唯一、密码 PBKDF2-SHA256 加盐哈希）、`sessions` 会话表。
- **账号**：注册 / 登录 / 登出，一人一号（昵称唯一）。
- **实时**：Server-Sent Events（SSE）推送对局事件（匹配 / 击球帧 / 结算）。
- **匹配**：排队，两人匹配后建房间。
- **权威物理**：8 球物理引擎（滑动/滚动摩擦、球球/库边碰撞、落袋）与 WPA 规则在**服务端**运行，每 30Hz 推送球位，客户端只负责渲染与出杆交互。

> 状态：账号 + 数据库 + 匹配 + 服务端物理 + 网页可玩 已全部跑通。可双人跨电脑对战。
>
> **在线体验（Render 免费实例）：<https://eight-ball-club.onrender.com>**
> 免费实例 15 分钟无访问会休眠，首次打开需等 30~60 秒冷启动。

## 上线部署

完整步骤见 **[deploy/DEPLOY.md](deploy/DEPLOY.md)**（域名解析 → 一键脚本 → HTTPS 证书 → 备份）。

也可以免费部署到 Render（无需服务器）：见 **[deploy/RENDER.md](deploy/RENDER.md)**。

```bash
git clone https://github.com/Wdwzsn/8-ball-club.git /opt/8ball
cd /opt/8ball
sudo bash pool-online/deploy/install.sh play.你的域名 你的邮箱
```

## 本地运行

```bash
python server.py 8080
# 浏览器打开 http://127.0.0.1:8080
```

另一台电脑（同一局域网）访问 `http://<本机IP>:8080` 即可，两人分别注册/登录后点「开始匹配」。

## 部署到 VPS（实现长期在线）

1. 把整个 `pool-online/` 目录上传到服务器（或 `git clone` 后放入）。
2. 服务器安装 Python 3.10+（自带 sqlite3）。
3. 前台试跑：`python3 server.py 8080`，确认能打开。
4. 长期运行（systemd，示例）：

```ini
# /etc/systemd/system/pool.service
[Unit]
Description=8-BALL CLUB Online
After=network.target

[Service]
WorkingDirectory=/opt/pool-online
ExecStart=/usr/bin/python3 server.py 8080
Restart=always

[Install]
WantedBy=multi-user.target
```

5. `sudo systemctl enable --now pool`；放行防火墙端口 8080。
6. （可选）绑定域名：用 Nginx/Caddy 反向代理 `http://127.0.0.1:8080` 到你的域名，开启 HTTPS。

> 说明：域名注册、云服务器购买与备案需要你本人在对应平台完成；我能提供的是这套**可直接部署的完整工程与步骤**。

## HTTP 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/register` | `{username,password}` → `{token,user}` |
| POST | `/api/login` | `{username,password}` → `{token,user}` |
| GET | `/api/me?token=` | `{user}` |
| POST | `/api/logout?token=` | 销毁会话 |
| POST | `/api/match/join?token=` | 排队/匹配 → `{status,state}` |
| POST | `/api/match/shoot?token=` | 击球：`{x,y,angle,power,calledPocket}` → 服务端推演 |
| GET | `/api/events?token=` | SSE 实时事件流（matched / frame / state） |
| GET | `/api/health` | 服务状态 |

## 路线图

- [x] 账号注册/登录（PBKDF2）、SQLite 数据库、会话
- [x] 静态网页 + 大厅 + 匹配 + 房间 + SSE 实时
- [x] 服务端权威物理引擎（滑动/滚动摩擦、碰撞、落袋、8 球规则）
- [x] 网页台球渲染与出杆交互（球杆、瞄准、力度、自由球、8 号叫袋）
- [ ] 杆法（高低杆/塞）网页 UI、房号邀请、断线重连、观战、聊天
