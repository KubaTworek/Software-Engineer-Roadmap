#!/usr/bin/env bash
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "Usage: $0 <backup-file.dump>"
  exit 1
fi

DATABASE_URL="${DATABASE_URL:-postgresql://chat:chat@localhost:5432/chat_stage6}"
pg_restore --clean --if-exists --no-owner --dbname="$DATABASE_URL" "$1"
