#!/usr/bin/env bash
set -euo pipefail

BASE_URL=${BASE_URL:-http://localhost:8080}
SAMPLE_FILE=${SAMPLE_FILE:-sample.mp4}

if ! command -v jq >/dev/null; then
  echo "jq is required"
  exit 1
fi

if [ ! -f "$SAMPLE_FILE" ]; then
  echo "Sample file not found: $SAMPLE_FILE"
  echo "Generate one with: ffmpeg -f lavfi -i testsrc=size=1280x720:rate=30 -f lavfi -i sine=frequency=1000 -t 10 -c:v libx264 -c:a aac sample.mp4"
  exit 1
fi

TOKEN=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"admin1234"}' | jq -r .accessToken)

VIDEO_ID=$(curl -s -X POST "$BASE_URL/api/videos" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Phase 2 Demo","description":"Queue, Redis, Search, QoE and CDN prewarm demo"}' | jq -r .id)

echo "Video: $VIDEO_ID"

SIZE=$(wc -c < "$SAMPLE_FILE" | tr -d ' ')
UPLOAD=$(curl -s -X POST "$BASE_URL/api/videos/$VIDEO_ID/uploads" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"filename\":\"$SAMPLE_FILE\",\"contentType\":\"video/mp4\",\"sizeBytes\":$SIZE}")

UPLOAD_ID=$(echo "$UPLOAD" | jq -r .uploadId)
UPLOAD_URL=$(echo "$UPLOAD" | jq -r .uploadUrl)

echo "Uploading through presigned URL..."
curl -s -X PUT "$UPLOAD_URL" --upload-file "$SAMPLE_FILE" -H 'Content-Type: video/mp4' >/dev/null

echo "Completing upload and enqueueing transcoding..."
curl -s -X POST "$BASE_URL/api/videos/$VIDEO_ID/uploads/$UPLOAD_ID/complete" \
  -H "Authorization: Bearer $TOKEN" | jq .

echo "Watch RabbitMQ or logs until video becomes READY, then publish:"
echo "curl -X POST $BASE_URL/api/videos/$VIDEO_ID/publish -H 'Authorization: Bearer $TOKEN'"
