#!/usr/bin/env bash
# =============================================================================
# JSS 健身 · 4.1 章前 5 个认证/用户接口端到端验证
#
# 覆盖（admin/target/plan/10-backend-mock-api-inventory.md §4.1 前 5 行 与
#       admin/target/plan/08-gym-api-contract.md §4 的目标契约映射）：
#   1) POST /admin-api/system/auth/login                 账号登录，返回 accessToken + refreshToken
#   2) POST /admin-api/system/auth/logout                注销当前会话
#   3) POST /admin-api/system/auth/refresh-token         刷新令牌
#   4) GET  /admin-api/system/auth/get-permission-info   当前用户权限信息（权限码 + 角色）
#   5) GET  /admin-api/system/user/profile/get           当前用户资料
#
# 前置：yudao-server 已启动（默认 48080），且已执行 script/gym/seed.sh
# 依赖：curl、python3
#
# 环境变量：
#   GYM_API_BASE_URL=http://127.0.0.1:48080   GYM_TENANT_ID=1
#   GYM_HQ_USER=gymadmin    GYM_STORE_USER=gymstore   GYM_DEMO_PASSWORD=Gym@123456
#   GYM_EXPECT_HQ_CODES=51  GYM_EXPECT_STORE_CODES=35
#
# 用法：bash script/gym/verify-auth-api.sh        # 全通过退出码 0，任一失败退出码 1
# =============================================================================
set -uo pipefail

BASE_URL="${GYM_API_BASE_URL:-http://127.0.0.1:48080}"
API="${BASE_URL}/admin-api"
TENANT_ID="${GYM_TENANT_ID:-1}"
HQ_USER="${GYM_HQ_USER:-gymadmin}"
STORE_USER="${GYM_STORE_USER:-gymstore}"
PASSWORD="${GYM_DEMO_PASSWORD:-Gym@123456}"
EXPECT_HQ_CODES="${GYM_EXPECT_HQ_CODES:-51}"
EXPECT_STORE_CODES="${GYM_EXPECT_STORE_CODES:-35}"

# 仅总部权限码（09-permissions.md §3 标注「仅总部」），门店角色不应返回
HQ_ONLY_CODES=(
  'gym:accounts:write' 'gym:roles:read' 'gym:roles:write' 'gym:payroll:templates'
  'gym:blacklists:read' 'gym:blacklists:write' 'gym:settings:manage' 'gym:contracts:manage'
  'gym:approvals:templates' 'gym:content:manage' 'gym:activities:manage' 'gym:franchise:manage'
  'gym:payment:read' 'gym:payment:write' 'gym:settlement:read' 'gym:settlement:manage'
)

PASS=0
FAIL=0

ok()   { PASS=$((PASS + 1)); printf '  \033[32m✓\033[0m %s\n' "$1"; }
bad()  { FAIL=$((FAIL + 1)); printf '  \033[31m✗\033[0m %s\n' "$1"; }
step() { printf '\n\033[1m%s\033[0m\n' "$1"; }

# 从 stdin 读取 JSON，按 Python 表达式取值（表达式内用 d 引用解析结果）
json() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)" 2>/dev/null; }

# check <断言表达式(结果为 True/False)> <描述>
check() {
  if [[ "$1" == "True" ]]; then ok "$2"; else bad "$2（实际：$1）"; fi
}

post() { # post <path> [data] [token]
  local path="$1" data="${2:-}" token="${3:-}"
  local args=(-s -X POST "${API}${path}" -H "tenant-id: ${TENANT_ID}" -H 'Content-Type: application/json')
  [[ -n "${token}" ]] && args+=(-H "Authorization: Bearer ${token}")
  [[ -n "${data}" ]] && args+=(-d "${data}")
  curl "${args[@]}"
}

get() { # get <path> [token]
  local path="$1" token="${2:-}"
  local args=(-s "${API}${path}" -H "tenant-id: ${TENANT_ID}")
  [[ -n "${token}" ]] && args+=(-H "Authorization: Bearer ${token}")
  curl "${args[@]}"
}

echo "=============================================================="
echo " gym 认证接口验证  目标：${API}  租户：${TENANT_ID}"
echo "=============================================================="

# ---------------------------------------------------------------- 0. 服务可用
step "0. 服务可达性 / 未登录鉴权"
UNAUTH=$(get "/system/auth/get-permission-info")
check "$(echo "${UNAUTH}" | json "d['code'] == 401")" "未携带 token 访问被拒绝（code=401 账号未登录）"

# ---------------------------------------------------------------- 1. 登录
step "1. POST /system/auth/login（账号登录）"
LOGIN=$(post "/system/auth/login" "{\"username\":\"${HQ_USER}\",\"password\":\"${PASSWORD}\"}")
check "$(echo "${LOGIN}" | json "d['code'] == 0")" "登录成功（code=0）"
HQ_TOKEN=$(echo "${LOGIN}" | json "d['data']['accessToken'] or ''")
HQ_REFRESH=$(echo "${LOGIN}" | json "d['data']['refreshToken'] or ''")
check "$(echo "${LOGIN}" | json "bool(d['data']['accessToken'])")" "返回 accessToken（${HQ_TOKEN:0:8}…）"
check "$(echo "${LOGIN}" | json "bool(d['data']['refreshToken'])")" "返回 refreshToken（${HQ_REFRESH:0:8}…）"
check "$(echo "${LOGIN}" | json "d['data']['userId'] > 0 and d['data']['expiresTime'] > 0")" "返回 userId 与过期时间 expiresTime"

WRONG=$(post "/system/auth/login" "{\"username\":\"${HQ_USER}\",\"password\":\"wrong-password\"}")
check "$(echo "${WRONG}" | json "d['code'] != 0")" "错误密码登录失败（code=$(echo "${WRONG}" | json "d['code']")）"

# ---------------------------------------------------------------- 2. 权限码
step "2. GET /system/auth/get-permission-info（权限码 / 角色）"
INFO=$(get "/system/auth/get-permission-info" "${HQ_TOKEN}")
check "$(echo "${INFO}" | json "d['code'] == 0")" "查询成功（code=0）"
check "$(echo "${INFO}" | json "'gym_hq_admin' in d['data']['roles']")" "角色包含 gym_hq_admin"
check "$(echo "${INFO}" | json "len([p for p in d['data']['permissions'] if p]) == ${EXPECT_HQ_CODES}")" \
  "返回 ${EXPECT_HQ_CODES} 个 gym:* 权限码（实际 $(echo "${INFO}" | json "len([p for p in d['data']['permissions'] if p])")）"
check "$(echo "${INFO}" | json "all(p.startswith('gym:') for p in d['data']['permissions'] if p)")" "权限码均为 gym: 前缀（与 system:* 隔离）"
check "$(echo "${INFO}" | json "'gym:members:read' in d['data']['permissions'] and 'gym:files:upload' in d['data']['permissions']")" "关键权限码存在（gym:members:read / gym:files:upload）"
check "$(echo "${INFO}" | json "d['data']['user']['username'] == '${HQ_USER}'")" "返回当前用户信息（username=${HQ_USER}）"

# ---------------------------------------------------------------- 3. 用户资料
step "3. GET /system/user/profile/get（用户资料）"
PROFILE=$(get "/system/user/profile/get" "${HQ_TOKEN}")
check "$(echo "${PROFILE}" | json "d['code'] == 0")" "查询成功（code=0）"
check "$(echo "${PROFILE}" | json "d['data']['username'] == '${HQ_USER}'")" "资料归属当前用户"
check "$(echo "${PROFILE}" | json "(d['data'].get('dept') or {}).get('name') == '健身总部'")" "部门=健身总部（门店/总部 = system_dept）"
check "$(echo "${PROFILE}" | json "any(r['name'] == '健身总部管理员' for r in d['data'].get('roles', []))")" "角色=健身总部管理员"

# ---------------------------------------------------------------- 4. 刷新令牌
step "4. POST /system/auth/refresh-token（刷新令牌）"
REFRESHED=$(post "/system/auth/refresh-token?refreshToken=${HQ_REFRESH}")
check "$(echo "${REFRESHED}" | json "d['code'] == 0")" "刷新成功（code=0）"
NEW_TOKEN=$(echo "${REFRESHED}" | json "d['data']['accessToken'] or ''")
check "$(echo "${REFRESHED}" | json "bool(d['data']['accessToken']) and d['data']['accessToken'] != '${HQ_TOKEN}'")" "返回新的 accessToken（${NEW_TOKEN:0:8}…）"
REINFO=$(get "/system/auth/get-permission-info" "${NEW_TOKEN}")
check "$(echo "${REINFO}" | json "d['code'] == 0")" "新 accessToken 可用"

# ---------------------------------------------------------------- 5. 登出
step "5. POST /system/auth/logout（注销会话）"
LOGOUT=$(post "/system/auth/logout" "" "${NEW_TOKEN}")
check "$(echo "${LOGOUT}" | json "d['code'] == 0 and d['data'] == True")" "登出成功（code=0）"
AFTER=$(get "/system/auth/get-permission-info" "${NEW_TOKEN}")
check "$(echo "${AFTER}" | json "d['code'] == 401")" "登出后原 accessToken 失效（code=401）"

# ---------------------------------------------------------------- 6. 门店角色（反向断言）
step "6. 门店角色权限收窄（gym_store_manager）"
SLOGIN=$(post "/system/auth/login" "{\"username\":\"${STORE_USER}\",\"password\":\"${PASSWORD}\"}")
STOKEN=$(echo "${SLOGIN}" | json "d['data']['accessToken'] or ''")
SINFO=$(get "/system/auth/get-permission-info" "${STOKEN}")
check "$(echo "${SINFO}" | json "d['code'] == 0")" "门店账号登录并取码成功"
check "$(echo "${SINFO}" | json "'gym_store_manager' in d['data']['roles']")" "角色包含 gym_store_manager"
check "$(echo "${SINFO}" | json "len([p for p in d['data']['permissions'] if p]) == ${EXPECT_STORE_CODES}")" \
  "返回 ${EXPECT_STORE_CODES} 个权限码（实际 $(echo "${SINFO}" | json "len([p for p in d['data']['permissions'] if p])")）"
check "$(echo "${SINFO}" | json "'gym:members:read' in d['data']['permissions']")" "门店侧权限存在（gym:members:read）"
ABSENT=True
for code in "${HQ_ONLY_CODES[@]}"; do
  if [[ "$(echo "${SINFO}" | json "'${code}' in d['data']['permissions']")" == "True" ]]; then
    ABSENT=False
    bad "门店角色不应持有仅总部权限码：${code}"
  fi
done
[[ "${ABSENT}" == "True" ]] && ok "不含任何仅总部权限码（16 个码全部收窄）"

# ---------------------------------------------------------------- 汇总
echo
echo "=============================================================="
printf ' 结果：\033[32m通过 %d\033[0m / \033[31m失败 %d\033[0m\n' "${PASS}" "${FAIL}"
echo "=============================================================="
[[ "${FAIL}" -eq 0 ]] || exit 1
