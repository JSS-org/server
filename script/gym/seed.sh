#!/usr/bin/env bash
# =============================================================================
# JSS 健身 · gym 模块数据库初始化脚本（幂等，可重复执行）
#
# 按文件名顺序执行 script/gym/sql/ 下的全部脚本：
#   01-gym-permission-seed.sql  gym 权限码（51）+ 角色 / 演示账号 / 门店部门
#   02-gym-file.sql             gym_file 私有文件表
#   ...                         后续新增的 gym 建表 / 种子脚本直接放同目录即可
#
# 环境变量（均有默认值）：
#   GYM_DB_HOST=127.0.0.1  GYM_DB_PORT=3306  GYM_DB_NAME=ruoyi-vue-pro
#   GYM_DB_USER=yudao      GYM_DB_PASSWORD=123456
#
# 用法：bash script/gym/seed.sh
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_DIR="${SCRIPT_DIR}/sql"

DB_HOST="${GYM_DB_HOST:-127.0.0.1}"
DB_PORT="${GYM_DB_PORT:-3306}"
DB_NAME="${GYM_DB_NAME:-ruoyi-vue-pro}"
DB_USER="${GYM_DB_USER:-yudao}"
DB_PASSWORD="${GYM_DB_PASSWORD:-123456}"

if ! command -v mysql >/dev/null 2>&1; then
  echo "[ERROR] 未找到 mysql 客户端，请先安装 MySQL 客户端工具" >&2
  exit 1
fi
if [[ ! -d "${SQL_DIR}" ]]; then
  echo "[ERROR] 找不到 SQL 目录：${SQL_DIR}" >&2
  exit 1
fi

echo "[INFO] 目标库：${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"

for sql_file in $(find "${SQL_DIR}" -maxdepth 1 -name '*.sql' | sort); do
  echo "[INFO] 执行：$(basename "${sql_file}")"
  MYSQL_PWD="${DB_PASSWORD}" mysql -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" "${DB_NAME}" \
    < "${sql_file}" 2>/dev/null
done

echo "[OK] gym 数据库初始化完成（幂等，可重复执行）"
