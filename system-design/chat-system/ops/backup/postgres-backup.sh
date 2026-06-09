#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-./backups}"
DATABASE_URL="${DATABASE_URL:-postgresql://chat:chat@localhost:5432/chat_stage6}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"

mkdir -p "$BACKUP_DIR"
FILE="$BACKUP_DIR/chat_stage6_$(date -u +%Y%m%dT%H%M%SZ).dump"

pg_dump "$DATABASE_URL" --format=custom --no-owner --file="$FILE"
find "$BACKUP_DIR" -type f -name 'chat_stage6_*.dump' -mtime +"$RETENTION_DAYS" -delete

echo "Backup written to $FILE"
