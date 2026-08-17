#!/usr/bin/env bash
set -euo pipefail

API=${API:-http://localhost:8080}
USER_EMAIL=${USER_EMAIL:-user@example.com}
USER_PASSWORD=${USER_PASSWORD:-user1234}
ADMIN_EMAIL=${ADMIN_EMAIL:-admin@example.com}
ADMIN_PASSWORD=${ADMIN_PASSWORD:-admin1234}
VIDEO_ID=${VIDEO_ID:-}

login() {
  local email=$1
  local password=$2
  curl -fsS -X POST "$API/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"password\":\"$password\"}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'
}

USER_TOKEN=$(login "$USER_EMAIL" "$USER_PASSWORD")
ADMIN_TOKEN=$(login "$ADMIN_EMAIL" "$ADMIN_PASSWORD")

if [[ -z "$VIDEO_ID" ]]; then
  echo "Set VIDEO_ID=<published-video-id> before running this script."
  exit 1
fi

SESSION_ID="demo-$(date +%s)"

curl -fsS -X POST "$API/api/personalization/events" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"eventType\":\"playback_start\",\"videoId\":\"$VIDEO_ID\",\"sessionId\":\"$SESSION_ID\",\"source\":\"home\",\"deviceType\":\"web\",\"country\":\"PL\",\"attributes\":{\"startupTimeMs\":420}}"

echo
curl -fsS -X POST "$API/api/personalization/events" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"eventType\":\"playback_complete\",\"videoId\":\"$VIDEO_ID\",\"sessionId\":\"$SESSION_ID\",\"source\":\"home\",\"deviceType\":\"web\",\"country\":\"PL\",\"attributes\":{\"completionRate\":1.0,\"rebufferTimeMs\":0}}"

echo
curl -fsS -X POST "$API/api/admin/features/recompute" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

echo
curl -fsS "$API/api/trending/videos?windowHours=24&limit=10" \
  -H "Authorization: Bearer $USER_TOKEN"

echo
curl -fsS "$API/api/recommendations/videos?limit=10" \
  -H "Authorization: Bearer $USER_TOKEN"

echo
curl -fsS "$API/api/experiments/home_recommendations_ranking/assignment" \
  -H "Authorization: Bearer $USER_TOKEN"
