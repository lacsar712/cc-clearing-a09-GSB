#!/bin/sh
set -e

BACKEND_URL="${BACKEND_URL:-http://backend:8080}"
echo "Waiting for backend at ${BACKEND_URL}..."

i=0
until curl -sf "${BACKEND_URL}/api/health" >/dev/null; do
  i=$((i + 1))
  if [ "$i" -gt 60 ]; then
    echo "Backend not ready after 60 attempts"
    exit 1
  fi
  sleep 2
done
echo "Backend is up"

LOGIN=$(curl -sf -X POST "${BACKEND_URL}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"operator","password":"op123456"}')
TOKEN=$(printf '%s' "$LOGIN" | jq -r '.token')
if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "Failed to login for seed"
  echo "$LOGIN"
  exit 1
fi
AUTH="Authorization: Bearer ${TOKEN}"

MEMBERS=$(curl -sf "${BACKEND_URL}/api/members" -H "$AUTH")
COUNT=$(printf '%s' "$MEMBERS" | jq 'length')
if [ "$COUNT" -gt 0 ]; then
  echo "Seed skipped: members already exist ($COUNT)"
  exit 0
fi

echo "Seeding members..."
M1=$(curl -sf -X POST "${BACKEND_URL}/api/members" -H "$AUTH" -H "Content-Type: application/json" -d '{"name":"Alpha Bank"}')
M2=$(curl -sf -X POST "${BACKEND_URL}/api/members" -H "$AUTH" -H "Content-Type: application/json" -d '{"name":"Beta Securities"}')
M3=$(curl -sf -X POST "${BACKEND_URL}/api/members" -H "$AUTH" -H "Content-Type: application/json" -d '{"name":"Gamma Clearing"}')

ID1=$(printf '%s' "$M1" | jq -r '.memberId')
ID2=$(printf '%s' "$M2" | jq -r '.memberId')
ID3=$(printf '%s' "$M3" | jq -r '.memberId')

SETTLE_DATE=$(date -u +%Y-%m-%d 2>/dev/null || echo "2026-09-10")
TRADE_DATE="$SETTLE_DATE"

echo "Seeding OPEN obligations for settleDate=${SETTLE_DATE} USD..."
curl -sf -X POST "${BACKEND_URL}/api/obligations" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"payerMemberId\":\"${ID1}\",\"payeeMemberId\":\"${ID2}\",\"currency\":\"USD\",\"amount\":100000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}" >/dev/null
curl -sf -X POST "${BACKEND_URL}/api/obligations" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"payerMemberId\":\"${ID2}\",\"payeeMemberId\":\"${ID3}\",\"currency\":\"USD\",\"amount\":60000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}" >/dev/null
curl -sf -X POST "${BACKEND_URL}/api/obligations" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"payerMemberId\":\"${ID3}\",\"payeeMemberId\":\"${ID1}\",\"currency\":\"USD\",\"amount\":40000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}" >/dev/null
curl -sf -X POST "${BACKEND_URL}/api/obligations" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"payerMemberId\":\"${ID1}\",\"payeeMemberId\":\"${ID3}\",\"currency\":\"USD\",\"amount\":25000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}" >/dev/null

echo "Executing netting for settleDate=${SETTLE_DATE} USD..."
NETTING=$(curl -sf -X POST "${BACKEND_URL}/api/netting-runs" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"settleDate\":\"${SETTLE_DATE}\",\"currency\":\"USD\"}")
RUN_ID=$(printf '%s' "$NETTING" | jq -r '.run.runId')
echo "Netting run completed: ${RUN_ID}"
printf '%s' "$NETTING" | jq -r '.positions[] | "  position \(.memberId[0:8])… net=\(.netAmount)"'

echo "Seeding member receipts matching system net positions (all MATCHED initially)..."
RECEIPTS=$(printf '%s' "$NETTING" | jq -c '{items: [.positions[] | {memberId, netAmount}]}')
RESULT=$(curl -sf -X PUT "${BACKEND_URL}/api/reconciliations/${RUN_ID}/receipts" \
  -H "$AUTH" -H "Content-Type: application/json" -d "$RECEIPTS")
printf '%s' "$RESULT" | jq -r '"reconciliation: matched=\(.matchedCount) mismatch=\(.mismatchCount) missing=\(.missingCount) allMatched=\(.allMatched)"'

echo "Seed completed successfully"
exit 0
