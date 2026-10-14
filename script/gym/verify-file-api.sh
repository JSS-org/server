#!/usr/bin/env bash
# =============================================================================
# JSS 健身 · §4.1 后 3 个文件接口端到端验证
#
# 覆盖（admin/target/plan/10-backend-mock-api-inventory.md §4.1 第 6~8 行）：
#   1) POST /admin-api/gym/files            上传（multipart：file + category + storeId?）
#   2) GET  /admin-api/gym/files/{id}       元数据
#   3) GET  /admin-api/gym/files/{id}/content  内容流（鉴权下载）
#
# 断言重点：魔数校验、类别白名单、逐类大小限制、门店数据范围越权、私有不暴露存储地址
#
# 前置：yudao-server 已启动（默认 48080），且已执行 script/gym/seed.sh
# 依赖：curl、python3
#
# 环境变量：
#   GYM_API_BASE_URL=http://127.0.0.1:48080   GYM_TENANT_ID=1
#   GYM_HQ_USER=gymadmin  GYM_STORE_USER=gymstore  GYM_DEMO_PASSWORD=Gym@123456
#
# 用法：bash script/gym/verify-file-api.sh
# =============================================================================
set -uo pipefail

BASE_URL="${GYM_API_BASE_URL:-http://127.0.0.1:48080}"
API="${BASE_URL}/admin-api"
TENANT_ID="${GYM_TENANT_ID:-1}"
HQ_USER="${GYM_HQ_USER:-gymadmin}"
STORE_USER="${GYM_STORE_USER:-gymstore}"
PASSWORD="${GYM_DEMO_PASSWORD:-Gym@123456}"

# 错误码（yudao-module-gym/enums/ErrorCodeConstants.java）
CODE_FILE_NOT_EXISTS=1070000001
CODE_CATEGORY_INVALID=1070000002
CODE_TYPE_NOT_ALLOWED=1070000003
CODE_SIZE_EXCEED=1070000004
CODE_STORE_NO_PERMISSION=1070000007

PASS=0
FAIL=0
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

ok()   { PASS=$((PASS + 1)); printf '  \033[32m✓\033[0m %s\n' "$1"; }
bad()  { FAIL=$((FAIL + 1)); printf '  \033[31m✗\033[0m %s\n' "$1"; }
step() { printf '\n\033[1m%s\033[0m\n' "$1"; }
check() { if [[ "$1" == "True" ]]; then ok "$2"; else bad "$2（实际：$1）"; fi; }
json() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)" 2>/dev/null; }

login() { # login <username> -> token
  curl -s -X POST "${API}/system/auth/login" -H "tenant-id: ${TENANT_ID}" \
    -H 'Content-Type: application/json' -d "{\"username\":\"$1\",\"password\":\"${PASSWORD}\"}" \
    | json "d['data']['accessToken'] or ''"
}

upload() { # upload <token> <file> <category> [storeId] -> json
  local token="$1" file="$2" category="$3" store_id="${4:-}"
  local args=(-s -X POST "${API}/gym/files/upload" -H "tenant-id: ${TENANT_ID}"
              -H "Authorization: Bearer ${token}" -F "file=@${file}" -F "category=${category}")
  [[ -n "${store_id}" ]] && args+=(-F "storeId=${store_id}")
  curl "${args[@]}"
}

get_json() { # get_json <token> <path>
  curl -s "${API}$2" -H "tenant-id: ${TENANT_ID}" -H "Authorization: Bearer $1"
}

echo "=============================================================="
echo " gym 文件接口验证  目标：${API}  租户：${TENANT_ID}"
echo "=============================================================="

# ---------------------------------------------------------------- 0. 造测试文件
step "0. 生成测试文件（魔数构造）"
python3 - "${TMP_DIR}" <<'PY'
import os, sys
d = sys.argv[1]
png = b'\x89PNG\r\n\x1a\n' + b'gym-png-filler' * 10
pdf = b'%PDF-1.7\n% gym contract filler\n'
mp4 = b'\x00\x00\x00\x18ftypisom' + b'gym-mp4-filler' * 10
open(os.path.join(d, 'photo.png'), 'wb').write(png)
open(os.path.join(d, 'contract.pdf'), 'wb').write(pdf)
open(os.path.join(d, 'demo.mp4'), 'wb').write(mp4)
# 伪装：PDF 内容 + .png 后缀
open(os.path.join(d, 'fake.png'), 'wb').write(pdf)
# 超限：11MB 的 PNG（图片类别上限 10MB）
open(os.path.join(d, 'big.png'), 'wb').write(b'\x89PNG\r\n\x1a\n' + b'\x00' * (11 * 1024 * 1024))
# 同名不同内容：两个目录放同名文件，用于验证「同名文件不互相覆盖」
for sub, filler in (('dup-a', b'A' * 32), ('dup-b', b'B' * 64)):
    os.makedirs(os.path.join(d, sub), exist_ok=True)
    open(os.path.join(d, sub, 'same-name.png'), 'wb').write(b'\x89PNG\r\n\x1a\n' + filler)
PY
ls -la "${TMP_DIR}" | tail -6
ok "已生成 photo.png / contract.pdf / demo.mp4 / fake.png / big.png / dup-a|dup-b/same-name.png"

# ---------------------------------------------------------------- 1. 登录
step "1. 登录（gymadmin 总部 / gymstore 门店）"
HQ_TOKEN="$(login "${HQ_USER}")"
STORE_TOKEN="$(login "${STORE_USER}")"
check "$([[ -n "${HQ_TOKEN}" ]] && echo True || echo False)" "gymadmin 登录成功"
check "$([[ -n "${STORE_TOKEN}" ]] && echo True || echo False)" "gymstore 登录成功"

# ---------------------------------------------------------------- 2. 上传
step "2. POST /gym/files/upload（上传）"
UP_PNG="$(upload "${HQ_TOKEN}" "${TMP_DIR}/photo.png" image)"
check "$(echo "${UP_PNG}" | json "d['code'] == 0")" "上传图片成功（code=0）"
PNG_ID="$(echo "${UP_PNG}" | json "d['data']['id'] or ''")"
check "$(echo "${UP_PNG}" | json "d['data']['mimeType'] == 'image/png'")" "魔数识别为 image/png"
check "$(echo "${UP_PNG}" | json "d['data']['category'] == 'image'")" "类别回写 image"
check "$(echo "${UP_PNG}" | json "d['data']['storeId'] is not None")" "未传 storeId 时默认落到上传人门店（storeId=$(echo "${UP_PNG}" | json "d['data']['storeId']")）"
check "$(echo "${UP_PNG}" | json "'url' not in d['data'] and 'path' not in d['data']")" "响应不含 infra 存储地址（url/path 不外泄）"

UP_PDF="$(upload "${HQ_TOKEN}" "${TMP_DIR}/contract.pdf" contract)"
check "$(echo "${UP_PDF}" | json "d['code'] == 0")" "上传合同 PDF 成功（code=0）"
PDF_ID="$(echo "${UP_PDF}" | json "d['data']['id'] or ''")"

UP_MP4="$(upload "${HQ_TOKEN}" "${TMP_DIR}/demo.mp4" video)"
check "$(echo "${UP_MP4}" | json "d['code'] == 0 and d['data']['mimeType'] == 'video/mp4'")" "上传视频成功并识别为 video/mp4"

# ---------------------------------------------------------------- 3. 元数据
step "3. GET /gym/files/{id}（元数据）"
META="$(get_json "${HQ_TOKEN}" "/gym/files/${PNG_ID}")"
check "$(echo "${META}" | json "d['code'] == 0")" "查询元数据成功（code=0）"
check "$(echo "${META}" | json "d['data']['name'] == 'photo.png'")" "返回原始文件名 photo.png"
check "$(echo "${META}" | json "d['data']['size'] == $(stat -f%z "${TMP_DIR}/photo.png")")" "返回文件大小"
check "$(echo "${META}" | json "str(d['data']['id']) == '${PNG_ID}'")" "返回文件编号"

# ---------------------------------------------------------------- 4. 内容流
step "4. GET /gym/files/{id}/content（内容流）"
HTTP_CODE="$(curl -s -o "${TMP_DIR}/downloaded.png" -w '%{http_code}' \
  "${API}/gym/files/${PNG_ID}/content" -H "tenant-id: ${TENANT_ID}" -H "Authorization: Bearer ${HQ_TOKEN}")"
check "$([[ "${HTTP_CODE}" == "200" ]] && echo True || echo False)" "下载返回 HTTP 200（实际 ${HTTP_CODE}）"
if cmp -s "${TMP_DIR}/photo.png" "${TMP_DIR}/downloaded.png"; then ok "下载内容与上传内容逐字节一致"; else bad "下载内容与上传内容不一致"; fi
CONTENT_TYPE="$(curl -s -D - -o /dev/null "${API}/gym/files/${PNG_ID}/content" \
  -H "tenant-id: ${TENANT_ID}" -H "Authorization: Bearer ${HQ_TOKEN}" | grep -i '^content-type' | tr -d '\r')"
check "$([[ "${CONTENT_TYPE}" == *"image/png"* ]] && echo True || echo False)" "Content-Type 为 image/png（${CONTENT_TYPE}）"

# ---------------------------------------------------------------- 5. 反例
step "5. 反例：类型不匹配 / 类别白名单 / 超限 / 非法类别"
MISMATCH="$(upload "${HQ_TOKEN}" "${TMP_DIR}/fake.png" image)"
check "$(echo "${MISMATCH}" | json "d['code'] == ${CODE_TYPE_NOT_ALLOWED}")" "PDF 内容伪装 .png → 拒绝（code=${CODE_TYPE_NOT_ALLOWED}，旧 Mock 为 HTTP 415）"
check "$(echo "${MISMATCH}" | json "'application/pdf' in d['msg']")" "错误信息点明实际类型 application/pdf"

WRONG_CATEGORY="$(upload "${HQ_TOKEN}" "${TMP_DIR}/contract.pdf" image)"
check "$(echo "${WRONG_CATEGORY}" | json "d['code'] == ${CODE_TYPE_NOT_ALLOWED}")" "合同 PDF 传成 image 类别 → 拒绝（类别白名单）"

TOO_BIG="$(upload "${HQ_TOKEN}" "${TMP_DIR}/big.png" image)"
check "$(echo "${TOO_BIG}" | json "d['code'] == ${CODE_SIZE_EXCEED}")" "11MB 图片超出 10MB 上限 → 拒绝（code=${CODE_SIZE_EXCEED}）"

BAD_CATEGORY="$(upload "${HQ_TOKEN}" "${TMP_DIR}/photo.png" coupon)"
check "$(echo "${BAD_CATEGORY}" | json "d['code'] == ${CODE_CATEGORY_INVALID}")" "未知类别 coupon → 拒绝（code=${CODE_CATEGORY_INVALID}）"

# ---------------------------------------------------------------- 6. 越权
step "6. 门店数据范围越权（gymstore 读 gymadmin 门店的文件）"
CROSS="$(get_json "${STORE_TOKEN}" "/gym/files/${PDF_ID}")"
check "$(echo "${CROSS}" | json "d['code'] == ${CODE_STORE_NO_PERMISSION}")" "跨门店读元数据被拒（code=${CODE_STORE_NO_PERMISSION}）"
CROSS_HTTP_CODE="$(curl -s -o "${TMP_DIR}/cross-content.json" -w '%{http_code}' \
  "${API}/gym/files/${PDF_ID}/content" -H "tenant-id: ${TENANT_ID}" -H "Authorization: Bearer ${STORE_TOKEN}")"
CROSS_CONTENT="$(cat "${TMP_DIR}/cross-content.json")"
check "$(echo "${CROSS_CONTENT}" | json "d['code'] == ${CODE_STORE_NO_PERMISSION}")" "跨门店下载被拒"
check "$([[ "${CROSS_HTTP_CODE}" == "403" ]] && echo True || echo False)" "跨门店下载 HTTP 403"

STORE_UP="$(upload "${STORE_TOKEN}" "${TMP_DIR}/contract.pdf" contract)"
STORE_ID="$(echo "${STORE_UP}" | json "d['data']['id'] or ''")"
check "$(echo "${STORE_UP}" | json "d['code'] == 0")" "门店账号上传本店文件成功"
STORE_READ="$(get_json "${STORE_TOKEN}" "/gym/files/${STORE_ID}")"
check "$(echo "${STORE_READ}" | json "d['code'] == 0")" "门店账号可读本店文件（正例）"
HQ_READ="$(get_json "${HQ_TOKEN}" "/gym/files/${STORE_ID}")"
check "$(echo "${HQ_READ}" | json "d['code'] == 0")" "总部账号（数据范围=全部）可跨门店读"

# ---------------------------------------------------------------- 7. 鉴权与不存在
step "7. 鉴权与不存在"
NO_TOKEN="$(get_json "" "/gym/files/${PNG_ID}")"
check "$(echo "${NO_TOKEN}" | json "d['code'] == 401")" "未携带 token → 401（code=401）"
NOT_FOUND="$(get_json "${HQ_TOKEN}" "/gym/files/999999999")"
check "$(echo "${NOT_FOUND}" | json "d['code'] == ${CODE_FILE_NOT_EXISTS}")" "文件不存在 → code=${CODE_FILE_NOT_EXISTS}"
MISSING_HTTP_CODE="$(curl -s -o "${TMP_DIR}/missing-content.json" -w '%{http_code}' \
  "${API}/gym/files/999999999/content" -H "tenant-id: ${TENANT_ID}" -H "Authorization: Bearer ${HQ_TOKEN}")"
check "$([[ "${MISSING_HTTP_CODE}" == "404" ]] && echo True || echo False)" "下载不存在文件 HTTP 404"
check "$(cat "${TMP_DIR}/missing-content.json" | json "d['code'] == ${CODE_FILE_NOT_EXISTS}")" "下载不存在文件保留业务错误码"

# ---------------------------------------------------------------- 8. 同名覆盖回归
step "8. 同名文件不互相覆盖（缺陷回归）"
# 两个不同内容、同名 same-name.png 的文件先后上传
DUP_A="$(upload "${HQ_TOKEN}" "${TMP_DIR}/dup-a/same-name.png" image)"
DUP_B="$(upload "${HQ_TOKEN}" "${TMP_DIR}/dup-b/same-name.png" image)"
DUP_A_ID="$(echo "${DUP_A}" | json "d['data']['id'] or ''")"
DUP_B_ID="$(echo "${DUP_B}" | json "d['data']['id'] or ''")"
check "$(echo "${DUP_A}" | json "d['code'] == 0") and $(echo "${DUP_B}" | json "d['code'] == 0")" \
  "同名文件连传两次均成功（id=${DUP_A_ID} / ${DUP_B_ID}）"
check "$([[ "${DUP_A_ID}" != "${DUP_B_ID}" ]] && echo True || echo False)" "两次上传生成两条独立记录"
curl -s -o "${TMP_DIR}/dup-a-downloaded.png" "${API}/gym/files/${DUP_A_ID}/content" \
  -H "tenant-id: ${TENANT_ID}" -H "Authorization: Bearer ${HQ_TOKEN}"
curl -s -o "${TMP_DIR}/dup-b-downloaded.png" "${API}/gym/files/${DUP_B_ID}/content" \
  -H "tenant-id: ${TENANT_ID}" -H "Authorization: Bearer ${HQ_TOKEN}"
if cmp -s "${TMP_DIR}/dup-a/same-name.png" "${TMP_DIR}/dup-a-downloaded.png"; then
  ok "第一次上传的内容仍是自己的（未被覆盖）"
else
  bad "第一次上传的内容被后一次覆盖了（缺陷复发）"
fi
if cmp -s "${TMP_DIR}/dup-b/same-name.png" "${TMP_DIR}/dup-b-downloaded.png"; then
  ok "第二次上传的内容独立可读"
else
  bad "第二次上传的内容读取不一致"
fi
# 存储层：同名文件的 infra 存储路径必须不同
DUP_A_PATH="$(mysql -h"${GYM_DB_HOST:-127.0.0.1}" -P"${GYM_DB_PORT:-3306}" -u"${GYM_DB_USER:-yudao}" \
  -p"${GYM_DB_PASSWORD:-123456}" "${GYM_DB_NAME:-ruoyi-vue-pro}" -N -e \
  "SELECT i.path FROM gym_file f JOIN infra_file i ON i.url=f.url WHERE f.id=${DUP_A_ID};" 2>/dev/null | head -1)"
DUP_B_PATH="$(mysql -h"${GYM_DB_HOST:-127.0.0.1}" -P"${GYM_DB_PORT:-3306}" -u"${GYM_DB_USER:-yudao}" \
  -p"${GYM_DB_PASSWORD:-123456}" "${GYM_DB_NAME:-ruoyi-vue-pro}" -N -e \
  "SELECT i.path FROM gym_file f JOIN infra_file i ON i.url=f.url WHERE f.id=${DUP_B_ID};" 2>/dev/null | head -1)"
if [[ -n "${DUP_A_PATH}" && -n "${DUP_B_PATH}" && "${DUP_A_PATH}" != "${DUP_B_PATH}" ]]; then
  ok "存储路径唯一（$(basename "${DUP_A_PATH}") ≠ $(basename "${DUP_B_PATH}")）"
else
  bad "存储路径未区分：A=${DUP_A_PATH} B=${DUP_B_PATH}"
fi

# ---------------------------------------------------------------- 汇总
echo
echo "=============================================================="
printf ' 结果：\033[32m通过 %d\033[0m / \033[31m失败 %d\033[0m\n' "${PASS}" "${FAIL}"
echo "=============================================================="
[[ "${FAIL}" -eq 0 ]] || exit 1
