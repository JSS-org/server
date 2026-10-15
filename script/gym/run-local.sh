#!/usr/bin/env bash
# gym-only development storage; global infra master storage remains unchanged.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec java -jar yudao-server/target/yudao-server.jar --spring.profiles.active=local \
  --gym.file.storage-config-id="${GYM_FILE_STORAGE_CONFIG_ID:-4}" "$@"
