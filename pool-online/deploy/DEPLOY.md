# 8-BALL CLUB · 上线部署指南

目标：把在线对战部署到你的**海外 VPS**，通过 `https://你的域名` 7×24 访问。
全程约 10 分钟，脚本已自动化。

> 说明：桌面版（Java）不需要部署；这里部署的是**在线对战**（Python 后端 + 网页前端，同一个进程）。

---

## 0. 准备

| 项 | 要求 |
|---|---|
| 服务器 | 海外 VPS，Ubuntu 22.04 / 24.04，1 核 1G 起 |
| 域名 | 任意注册商，能改 DNS 解析 |
| 邮箱 | 申请免费 HTTPS 证书用 |
| 端口 | 云厂商安全组放行 **22 / 80 / 443** |

---

## 1. 域名解析

在域名 DNS 里加一条 **A 记录**：

```
主机记录: play
记录类型: A
记录值:   你的服务器公网 IP
```

等待 1~10 分钟生效。检查：

```bash
dig +short play.你的域名
# 应输出你的服务器 IP
```

---

## 2. 登录服务器并拉取代码

```bash
ssh root@你的服务器IP

apt-get update -y && apt-get install -y git
git clone https://github.com/Wdwzsn/8-ball-club.git /opt/8ball
cd /opt/8ball
```

---

## 3. 一键部署

```bash
sudo bash pool-online/deploy/install.sh play.你的域名 你的邮箱
```

脚本会自动完成：

1. 安装 `python3` / `nginx` / `certbot`
2. 创建 `pool` 运行用户，数据目录 `pool-online/data`
3. 写入并启动 systemd 服务 `pool`（崩溃自动重启、开机自启）
4. 配置 Nginx 反代到 `127.0.0.1:8080`（**SSE 已关闭缓冲**）
5. 申请并启用 HTTPS，自动跳转
6. 校验 `http://127.0.0.1:8080/api/health`

---

## 4. 验证

浏览器打开：

```
https://play.你的域名
```

- 能打开登录页 → 前端 OK
- 注册一个账号 → 数据库 OK
- 两个浏览器各注册一个号，点「开始匹配」→ 实时对战 OK

命令行验证：

```bash
curl https://play.你的域名/api/health
# {"ok": true, ...}
```

---

## 5. 日常维护

```bash
# 更新代码
cd /opt/8ball && git pull && sudo systemctl restart pool

# 查看状态 / 日志
sudo systemctl status pool
sudo journalctl -u pool -f

# 重启 / 停止 / 启动
sudo systemctl restart pool
sudo systemctl stop pool
sudo systemctl start pool
```

### 自动备份数据库

```bash
crontab -e
# 加一行（每天 4 点备份，保留 7 份）：
0 4 * * * /opt/8ball/pool-online/deploy/backup.sh >> /var/log/pool-backup.log 2>&1
```

备份文件在 `/opt/8ball/pool-online/backups/`。恢复时停服务、把备份覆盖 `data/pool.db`、再启动。

---

## 6. 常见问题

**502 Bad Gateway**
```bash
sudo systemctl status pool        # 看服务是否在跑
sudo journalctl -u pool -n 50     # 看报错
```

**HTTPS 证书申请失败**
- 域名 A 记录还没生效（`dig` 检查）
- 80 端口没放行（安全组 + `ufw status`）
- 重试：`sudo certbot --nginx -d play.你的域名`

**实时对局不刷新 / 卡住**
- Nginx 必须关闭缓冲：确认配置里有 `proxy_buffering off;`
- 改完 `sudo nginx -t && sudo systemctl reload nginx`

**数据库无法写入**
```bash
sudo chown -R pool:pool /opt/8ball/pool-online/data
sudo systemctl restart pool
```

**想先用 IP 直接测试（不配域名）**
- 安全组放行 8080，直接访问 `http://IP:8080`（不推荐长期这样）

---

## 7. 关于备案

- **海外 / 香港服务器：不需要备案**，解析域名即可用。
- 中国大陆服务器：域名必须完成 ICP 备案，否则会被拦截。

---

## 8. 成本参考

| 项 | 费用 |
|---|---|
| 海外 VPS（1C1G） | 约 ¥20–40 / 月 |
| 域名 | 约 ¥30–100 / 年 |
| HTTPS 证书 | 免费（Let's Encrypt） |
