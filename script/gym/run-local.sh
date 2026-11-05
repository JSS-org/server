#!/usr/bin/env bash
# gym-only development storage; global infra master storage remains unchanged.
set -euo pipefail
cd "$(dirname "$0")/../.."
# Optional ignored, owner-readable local settings. The same GYM_DB_* values serve bootstrap and Java.
local_env_file="${GYM_LOCAL_ENV_FILE:-.runtime/gym-local.env}"
if [[ -f "$local_env_file" && -z "${GYM_DB_NAME:-}" && -z "${SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_URL:-}" ]]; then
  set -a
  source "$local_env_file"
  set +a
fi
if [[ -n "${GYM_DB_NAME:-}" ]]; then
  db_host="${GYM_DB_HOST:-127.0.0.1}"
  if [[ "$db_host" == *:* && "$db_host" != \[*\] ]]; then db_host="[$db_host]"; fi
  db_url="jdbc:mysql://${db_host}:${GYM_DB_PORT:-3306}/${GYM_DB_NAME}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&nullCatalogMeansCurrent=true"
  export SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_URL="$db_url"
  export SPRING_DATASOURCE_DYNAMIC_DATASOURCE_SLAVE_URL="$db_url"
  export GYM_DB_USERNAME="${GYM_DB_USER:?Set GYM_DB_USER with GYM_DB_NAME}"
  export GYM_DB_PASSWORD="${GYM_DB_PASSWORD?Set GYM_DB_PASSWORD explicitly}"
fi
export SPRING_DATA_REDIS_DATABASE="${GYM_REDIS_DATABASE:-${SPRING_DATA_REDIS_DATABASE:-0}}"
exec java -jar yudao-server/target/yudao-server.jar --spring.profiles.active=local \
  --gym.file.storage-config-id="${GYM_FILE_STORAGE_CONFIG_ID:-4}" "$@"
