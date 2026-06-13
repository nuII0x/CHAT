#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SYSTEMD_DIR="${HOME}/.config/systemd/user"
SERVICE_NAME="nullchat-node.service"
GENERATED_SERVICE="${SYSTEMD_DIR}/${SERVICE_NAME}"

mkdir -p "${SYSTEMD_DIR}"
sed "s|__PROJECT_ROOT__|${PROJECT_ROOT}|g" "${PROJECT_ROOT}/node/systemd/${SERVICE_NAME}" > "${GENERATED_SERVICE}"

systemctl --user daemon-reload
systemctl --user enable --now "${SERVICE_NAME}"

echo "NullChat node service installed and started."
echo "Logs: journalctl --user -u ${SERVICE_NAME} -f"
