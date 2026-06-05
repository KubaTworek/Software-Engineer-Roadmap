#!/usr/bin/env bash
set -euo pipefail

BASE_URL=${BASE_URL:-http://localhost:8080}
VIDEO_FILE=${1:-sample.mp4}

if [ ! -f "$VIDEO_FILE" ]; then
  echo "Usage: $0 ./sample.mp4"
  exit 1
fi

TOKEN=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"admin1234"}' | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')

echo "Creating video..."
VIDEO_ID=$(curl -s -X POST "$BASE_URL/api/videos" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Demo video","description":"Uploaded from demo-flow.sh"}' | sed -n 's/.*"id":"\([^"]*\)".*/\1/p')

echo "Video ID: $VIDEO_ID"
SIZE=$(wc -c < "$VIDEO_FILE" | tr -d ' ')
UPLOAD_JSON=$(curl -s -X POST "$BASE_URL/api/videos/$VIDEO_ID/uploads" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"filename\":\"$(basename "$VIDEO_FILE")\",\"contentType\":\"video/mp4\",\"sizeBytes\":$SIZE}")

UPLOAD_ID=$(echo "$UPLOAD_JSON" | sed -n 's/.*"uploadId":"\([^"]*\)".*/\1/p')
UPLOAD_URL=$(echo "$UPLOAD_JSON" | sed -n 's/.*"uploadUrl":"\([^"]*\)".*/\1/p')

echo "Uploading to object storage..."
curl -s -X PUT "$UPLOAD_URL" --data-binary "@$VIDEO_FILE" -H 'Content-Type: video/mp4' > /dev/null

echo "Completing upload..."
curl -s -X POST "$BASE_URL/api/videos/$VIDEO_ID/uploads/$UPLOAD_ID/complete" -H "Authorization: Bearer $TOKEN" | jq . || true

echo "Transcoding is async. Check admin panel or API. When READY, publish with:"
echo "curl -X POST $BASE_URL/api/videos/$VIDEO_ID/publish -H 'Authorization: Bearer $TOKEN'"
