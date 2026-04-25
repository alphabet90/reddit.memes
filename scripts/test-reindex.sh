API_URL=${API_URL:-"http://localhost:8080"}
API_KEY=${ADMIN_API_KEY:-"let-me-pass"}

echo "Testing reindex endpoint at $API_URL"

# Health check
echo "1. Checking health..."
curl -s "$API_URL/actuator/health" | jq .

# Get initial stats
echo -e "\n2. Getting initial stats..."
curl -s "$API_URL/" | jq .

# Trigger reindex
echo -e "\n3. Triggering reindex..."
response=$(curl -s -X POST \
  -H "X-Api-Key: $API_KEY" \
  "$API_URL/admin/reindex")

echo "$response" | jq .

# Verify results
echo -e "\n4. Verifying results..."
curl -s "$API_URL/" | jq .

echo -e "\n✅ Done!"