#!/usr/bin/env bash
set -euo pipefail

API=${API:-http://localhost:8080}
SAMPLE=${SAMPLE:-./sample.mp4}

echo "Logging in as admin..."
TOKEN=$(curl -s -X POST "$API/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"admin1234"}' | jq -r .accessToken)

if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  echo "Could not login. Is the stack running?"
  exit 1
fi

echo "Creating live stream..."
LIVE=$(curl -s -X POST "$API/api/live" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"title":"Demo live stream","description":"Phase 5 demo","latencyMode":"LOW_LATENCY","dvrEnabled":true,"dvrWindowSeconds":1800,"recordingEnabled":true}')

LIVE_ID=$(echo "$LIVE" | jq -r .id)
INGEST_URL=$(echo "$LIVE" | jq -r .ingestUrl)

echo "Live stream id: $LIVE_ID"
echo "Ingest URL:     $INGEST_URL"

echo "Starting live session..."
curl -s -X POST "$API/api/live/$LIVE_ID/start" -H "Authorization: Bearer $TOKEN" | jq .

cat <<MSG

Now push a stream in another terminal:

ffmpeg -re -stream_loop -1 -i "$SAMPLE" \\
  -c:v libx264 -preset veryfast -tune zerolatency \\
  -c:a aac -f flv "$INGEST_URL"

Then get playback URL:

curl "$API/api/live/$LIVE_ID/playback" -H "Authorization: Bearer $TOKEN" | jq .

Stop live:

curl -X POST "$API/api/live/$LIVE_ID/stop" -H "Authorization: Bearer $TOKEN"

Convert to VOD:

curl -X POST "$API/api/live/$LIVE_ID/convert-to-vod" -H "Authorization: Bearer $TOKEN" | jq .
MSG
