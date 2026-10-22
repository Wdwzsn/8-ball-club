#!/usr/bin/env bash
# SQLite 备份：保留最近 7 份。建议加 cron：
#   0 4 * * * /opt/8ball/pool-online/deploy/backup.sh >> /var/log/pool-backup.log 2>&1
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$DIR/.." && pwd)"
DB="$APP_DIR/data/pool.db"
OUT="$APP_DIR/backups"
mkdir -p "$OUT"
if [ ! -f "$DB" ]; then
  echo "数据库不存在: $DB"
  exit 0
fi
STAMP="$(date +%Y%m%d-%H%M%S)"
OUTFILE="$OUT/pool-$STAMP.db"
python3 - "$DB" "$OUTFILE" <<'PY'
import sqlite3, sys
src = sqlite3.connect(sys.argv[1])
dst = sqlite3.connect(sys.argv[2])
src.backup(dst)
dst.close()
src.close()
print("backup ->", sys.argv[2])
PY
ls -1t "$OUT"/pool-*.db 2>/dev/null | tail -n +8 | xargs -r rm -f
