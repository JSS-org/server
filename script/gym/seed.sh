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
# 用法：bash script/gym/seed.sh [--schema-only|--menus-only]
# --schema-only：只执行业务建表/升级及配套数据回填，不执行权限、菜单、演示账号种子。
# --menus-only：只登记页面菜单，跳过脚本末尾的角色授权，不改账号或业务表。
# =============================================================================
set -euo pipefail

SCHEMA_ONLY=false
MENUS_ONLY=false
case "${1:-}" in
  '') ;;
  --schema-only) SCHEMA_ONLY=true ;;
  --menus-only) MENUS_ONLY=true ;;
  -h|--help)
    echo "用法：bash script/gym/seed.sh [--schema-only|--menus-only]"
    echo "--schema-only：仅升级业务表和回填业务数据，不执行权限/菜单/演示账号种子。"
    echo "--menus-only：仅登记页面菜单，不修改角色授权、账号或业务表。"
    exit 0 ;;
  *) echo "[ERROR] 未知参数：$1" >&2; exit 1 ;;
esac
if (( $# > 1 )); then
  echo "[ERROR] 参数过多" >&2
  exit 1
fi

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

emit_sql() {
  if [[ "${MENUS_ONLY}" == true ]]; then
    # 现有 *-menu(s).sql 先登记菜单，末尾再 INSERT 角色授权；本模式只执行前半部分。
    awk '/^[[:space:]]*INSERT[[:space:]]+INTO[[:space:]]+system_role_menu([[:space:]]|$)/ { exit } { print }' "$1"
  else
    cat "$1"
  fi
}

for sql_file in "${SQL_DIR}"/*.sql; do
  if [[ "${MENUS_ONLY}" == true ]]; then
    case "$(basename "${sql_file}")" in
      *-menu.sql|*-menus.sql) ;;
      *) continue ;;
    esac
  fi
  if [[ "${SCHEMA_ONLY}" == true ]]; then
    case "$(basename "${sql_file}")" in
      01-gym-permission-seed.sql|*-menu.sql|*-menus.sql|*-permission.sql) continue ;;
    esac
  fi
  echo "[INFO] 执行：$(basename "${sql_file}")"
  if ! emit_sql "${sql_file}" | MYSQL_PWD="${DB_PASSWORD}" mysql --connect-timeout=5 --default-character-set=utf8mb4 \
    -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" "${DB_NAME}"; then
    echo "[ERROR] 执行失败：$(basename "${sql_file}")，已停止后续脚本。请修复上面的 SQL 错误后重试。" >&2
    exit 1
  fi
done

echo "[OK] gym 数据库初始化完成（schema-only=${SCHEMA_ONLY}，menus-only=${MENUS_ONLY}，幂等，可重复执行）"
