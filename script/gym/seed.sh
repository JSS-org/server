#!/usr/bin/env bash
# Development data only. Schema and required reference data use db.sh.
set -euo pipefail
if [[ $# -eq 1 && "$1" == development ]]; then
  exec python3 "$(dirname "$0")/database.py" seed-development
fi
cat >&2 <<'HELP'
用法：bash script/gym/seed.sh development
需要显式设置 GYM_DB_ENV=development、GYM_SEED_TENANT_ID 及 GYM_DB_*。
新gym库：bash script/gym/db.sh init
已有完整旧库接管：bash script/gym/db.sh adopt-legacy
以后升级/补必要菜单：bash script/gym/db.sh migrate
旧的完整重放、--schema-only、--menus-only入口已停止使用。
HELP
if [[ $# -eq 1 && ( "$1" == --help || "$1" == -h ) ]]; then exit 0; fi
exit 1
