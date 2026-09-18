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
TOKEN=$(printf '%s' "$LOGIN" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
if [ -z "$TOKEN" ]; then
  echo "Failed to login for seed"
  echo "$LOGIN"
  exit 1
fi
AUTH="Authorization: Bearer ${TOKEN}"

MEMBERS=$(curl -sf "${BACKEND_URL}/api/members" -H "$AUTH")
COUNT=$(printf '%s' "$MEMBERS" | grep -o '"memberId"' | wc -l | tr -d ' ')
if [ "$COUNT" -gt 0 ]; then
  echo "Seed skipped: members already exist ($COUNT)"
  exit 0
fi

echo "Seeding members..."
M1=$(curl -sf -X POST "${BACKEND_URL}/api/members" -H "$AUTH" -H "Content-Type: application/json" -d '{"name":"Alpha Bank"}')
M2=$(curl -sf -X POST "${BACKEND_URL}/api/members" -H "$AUTH" -H "Content-Type: application/json" -d '{"name":"Beta Securities"}')
M3=$(curl -sf -X POST "${BACKEND_URL}/api/members" -H "$AUTH" -H "Content-Type: application/json" -d '{"name":"Gamma Clearing"}')

ID1=$(printf '%s' "$M1" | sed -n 's/.*"memberId":"\([^"]*\)".*/\1/p')
ID2=$(printf '%s' "$M2" | sed -n 's/.*"memberId":"\([^"]*\)".*/\1/p')
ID3=$(printf '%s' "$M3" | sed -n 's/.*"memberId":"\([^"]*\)".*/\1/p')

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

echo "Seeding EUR obligations for reconciliation demo..."
curl -sf -X POST "${BACKEND_URL}/api/obligations" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"payerMemberId\":\"${ID1}\",\"payeeMemberId\":\"${ID2}\",\"currency\":\"EUR\",\"amount\":50000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}" >/dev/null
curl -sf -X POST "${BACKEND_URL}/api/obligations" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"payerMemberId\":\"${ID2}\",\"payeeMemberId\":\"${ID3}\",\"currency\":\"EUR\",\"amount\":30000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}" >/dev/null
curl -sf -X POST "${BACKEND_URL}/api/obligations" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"payerMemberId\":\"${ID3}\",\"payeeMemberId\":\"${ID1}\",\"currency\":\"EUR\",\"amount\":20000.00000000,\"tradeDate\":\"${TRADE_DATE}\",\"settleDate\":\"${SETTLE_DATE}\"}" >/dev/null

echo "Running EUR netting run..."
RUN=$(curl -sf -X POST "${BACKEND_URL}/api/netting-runs" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"settleDate\":\"${SETTLE_DATE}\",\"currency\":\"EUR\"}")
RUN_ID=$(printf '%s' "$RUN" | sed -n 's/.*"runId":"\([^"]*\)".*/\1/p')

echo "Fetching EUR net positions for run ${RUN_ID}..."
POSITIONS=$(curl -sf "${BACKEND_URL}/api/netting-runs/${RUN_ID}/positions" -H "$AUTH")
NET1=$(printf '%s' "$POSITIONS" | sed -n "s/.*\"memberId\":\"${ID1}\",\"currency\":\"EUR\",\"netAmount\":\([0-9.-]*\).*/\1/p")
NET2=$(printf '%s' "$POSITIONS" | sed -n "s/.*\"memberId\":\"${ID2}\",\"currency\":\"EUR\",\"netAmount\":\([0-9.-]*\).*/\1/p")
NET3=$(printf '%s' "$POSITIONS" | sed -n "s/.*\"memberId\":\"${ID3}\",\"currency\":\"EUR\",\"netAmount\":\([0-9.-]*\).*/\1/p")

if [ -z "$RUN_ID" ] || [ -z "$NET1" ] || [ -z "$NET2" ] || [ -z "$NET3" ]; then
  echo "Seed failed: could not resolve EUR run positions"
  echo "$POSITIONS"
  exit 1
fi

echo "Seeding matched receipts (run ${RUN_ID})..."
curl -sf -X POST "${BACKEND_URL}/api/netting-runs/${RUN_ID}/receipts" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"memberId\":\"${ID1}\",\"reportedAmount\":${NET1}}" >/dev/null
curl -sf -X POST "${BACKEND_URL}/api/netting-runs/${RUN_ID}/receipts" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"memberId\":\"${ID2}\",\"reportedAmount\":${NET2}}" >/dev/null
curl -sf -X POST "${BACKEND_URL}/api/netting-runs/${RUN_ID}/receipts" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"memberId\":\"${ID3}\",\"reportedAmount\":${NET3}}" >/dev/null

echo "Seed completed successfully"
exit 0
