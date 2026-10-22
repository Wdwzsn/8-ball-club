#!/usr/bin/env bash
# 8-BALL CLUB 一键部署脚本（Ubuntu 22.04 / 24.04，需 root）
#
# 用法（在服务器上、仓库目录内）：
#   sudo bash pool-online/deploy/install.sh <域名> <邮箱> [端口]
# 示例：
#   sudo bash pool-online/deploy/install.sh play.example.com me@example.com
#
# 作用：安装 Python3/Nginx/Certbot → 配置 systemd 常驻 → 配置 Nginx 反代(SSE)
#       → 申请 HTTPS 证书 → 校验健康接口。
set -euo pipefail

DOMAIN="${1:-}"
EMAIL="${2:-}"
PORT="${3:-8080}"

if [ -z "$DOMAIN" ] || [ -z "$EMAIL" ]; then
  echo "用法: sudo bash deploy/install.sh <域名> <邮箱> [端口]"
  exit 1
fi
if [ "$(id -u)" -ne 0 ]; then
  echo "请用 root 运行（sudo）"
  exit 1
fi

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$DEPLOY_DIR/.." && pwd)"
echo "==> 应用目录: $APP_DIR"
echo "==> 域名: $DOMAIN    端口: $PORT"

export DEBIAN_FRONTEND=noninteractive
echo "==> 安装依赖 ..."
apt-get update -y
apt-get install -y python3 nginx certbot python3-certbot-nginx

# 运行用户（非 root）
if ! id -u pool >/dev/null 2>&1; then
  useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin pool
fi
mkdir -p "$APP_DIR/data"
chown -R pool:pool "$APP_DIR/data"
chmod 750 "$APP_DIR/data"

# systemd 常驻服务
echo "==> 配置 systemd ..."
cat >/etc/systemd/system/pool.service <<EOF
[Unit]
Description=8-BALL CLUB Online
After=network.target

[Service]
Type=simple
User=pool
WorkingDirectory=$APP_DIR
ExecStart=/usr/bin/python3 $APP_DIR/server.py $PORT
Restart=always
RestartSec=3
Environment=PYTHONUNBUFFERED=1
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable pool
systemctl restart pool
sleep 1
systemctl --no-pager --lines=5 status pool || true

# Nginx 反向代理（关键：SSE 需要关闭缓冲 + 长超时）
echo "==> 配置 Nginx ..."
cat >/etc/nginx/sites-available/pool <<EOF
server {
    listen 80;
    server_name $DOMAIN;
    client_max_body_size 4m;

    location / {
        proxy_pass http://127.0.0.1:$PORT;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_set_header Connection "";
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        gzip off;
    }
}
EOF
ln -sf /etc/nginx/sites-available/pool /etc/nginx/sites-enabled/pool
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx

# 防火墙（只在已安装 ufw 时处理；不主动 enable，避免锁死 SSH）
if command -v ufw >/dev/null 2>&1; then
  ufw allow OpenSSH >/dev/null 2>&1 || true
  ufw allow 80/tcp >/dev/null 2>&1 || true
  ufw allow 443/tcp >/dev/null 2>&1 || true
fi

# HTTPS 证书
echo "==> 申请 Let's Encrypt 证书 ..."
if ! certbot --nginx -d "$DOMAIN" --non-interactive --agree-tos -m "$EMAIL" --redirect; then
  echo "!! 证书申请失败。常见原因："
  echo "   1) 域名 A 记录还没指向本机 IP（用 dig $DOMAIN 检查）"
  echo "   2) 80 端口未放行（云厂商安全组 + ufw）"
  echo "   之后可重试: sudo certbot --nginx -d $DOMAIN"
fi

# 校验
echo "==> 健康检查:"
curl -fsS "http://127.0.0.1:$PORT/api/health" && echo
echo "==> 部署完成：https://$DOMAIN"
echo "    更新代码: cd $APP_DIR && git pull && sudo systemctl restart pool"
