#!/bin/bash
# demo.sh - Complete End-to-End Demo Script for Video Presentation

BASE_URL="http://localhost:8080"

echo "============================================================"
echo "      DISTRIBUTED RATE LIMITER SERVICE - DEMO PRESENTATION"
echo "============================================================"
echo ""

# Color formatting for presentation
GREEN='\033[0;32m'
RED='\033[0;31m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

sleep 1

echo -e "${YELLOW}>>> STEP 1: Calling Protected Endpoint (/api/data) with Default Limit (10 req/min)${NC}"
echo -e "${CYAN}Command:${NC} curl -i -X POST $BASE_URL/api/data -H 'X-API-Key: demo_user'"
echo ""
curl -i -X POST "$BASE_URL/api/data" \
  -H "X-API-Key: demo_user"
echo -e "\n"
sleep 2

echo -e "${YELLOW}>>> STEP 2: Exceeding Default Rate Limit (Sending 11 Requests in Loop)${NC}"
echo ""
for i in {1..11}; do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/data" -H "X-API-Key: demo_user")
  REMAINING=$(curl -s -i -X POST "$BASE_URL/api/data" -H "X-API-Key: demo_user" | grep -i "X-RateLimit-Remaining" | tr -d '\r')
  
  if [ "$STATUS" -eq 200 ]; then
    echo -e "Request #$i: ${GREEN}HTTP $STATUS${NC} | $REMAINING"
  else
    echo -e "Request #$i: ${RED}HTTP $STATUS (Rate Limited!)${NC} | $REMAINING"
  fi
  sleep 0.2
done
echo -e "\n"
sleep 2

echo -e "${YELLOW}>>> STEP 3: Checking Rate Limit Status for 'demo_user'${NC}"
echo -e "${CYAN}Command:${NC} curl -i -X GET $BASE_URL/api/rate-limit/status/demo_user"
echo ""
curl -i -X GET "$BASE_URL/api/rate-limit/status/demo_user"
echo -e "\n"
sleep 2

echo -e "${YELLOW}>>> STEP 4: Configuring Custom Limit (3 req/min) for 'premium_user'${NC}"
echo -e "${CYAN}Command:${NC} curl -i -X POST $BASE_URL/api/rate-limit/configure -H 'Content-Type: application/json' -d '{\"apiKey\":\"premium_user\",\"requestsPerMinute\":3}'"
echo ""
curl -i -X POST "$BASE_URL/api/rate-limit/configure" \
  -H "Content-Type: application/json" \
  -d '{
    "apiKey": "premium_user",
    "requestsPerMinute": 3
  }'
echo -e "\n"
sleep 2

echo -e "${YELLOW}>>> STEP 5: Testing Custom Rate Limit for 'premium_user' (Sending 4 Requests)${NC}"
echo ""
for i in {1..4}; do
  echo -e "${CYAN}--- Request #$i ---${NC}"
  curl -i -s -X POST "$BASE_URL/api/data" -H "X-API-Key: premium_user"
  echo ""
  sleep 0.5
done
echo -e "\n"
sleep 2

echo -e "${YELLOW}>>> STEP 6: Resetting Rate Limit for 'demo_user'${NC}"
echo -e "${CYAN}Command:${NC} curl -i -X DELETE $BASE_URL/api/rate-limit/reset/demo_user"
echo ""
curl -i -X DELETE "$BASE_URL/api/rate-limit/reset/demo_user"
echo -e "\n"
sleep 2

echo -e "${YELLOW}>>> STEP 7: Verifying 'demo_user' Request Succeeds After Reset${NC}"
echo -e "${CYAN}Command:${NC} curl -i -X POST $BASE_URL/api/data -H 'X-API-Key: demo_user'"
echo ""
curl -i -X POST "$BASE_URL/api/data" \
  -H "X-API-Key: demo_user"
echo -e "\n"
sleep 2

echo -e "${YELLOW}>>> STEP 8: Edge Case - Missing Header Validation${NC}"
echo -e "${CYAN}Command:${NC} curl -i -X POST $BASE_URL/api/data"
echo ""
curl -i -X POST "$BASE_URL/api/data"
echo -e "\n"

echo "============================================================"
echo "                 DEMO COMPLETED SUCCESSFULLY!"
echo "============================================================"
