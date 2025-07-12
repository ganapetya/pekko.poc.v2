#!/bin/bash

# Cluster Monitor - Real-time monitoring of Pekko cluster
# Shows cluster members, shard distribution, and system health

echo "🔍 PEKKO CLUSTER MONITOR"
echo "========================"
echo "Press Ctrl+C to stop monitoring"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

MONITOR_INTERVAL=5
COUNTER=0

while true; do
    COUNTER=$((COUNTER + 1))
    TIMESTAMP=$(date '+%H:%M:%S')
    
    clear
    echo "🔍 PEKKO CLUSTER MONITOR - Update #$COUNTER at $TIMESTAMP"
    echo "=========================================================="
    echo ""
    
    # Check if cluster is running
    echo -e "${BLUE}🔧 DOCKER SERVICES STATUS:${NC}"
    docker-compose ps --format "table {{.Name}}\t{{.State}}\t{{.Ports}}" 2>/dev/null || echo "Could not fetch Docker status"
    echo ""
    
    # Test health endpoint
    echo -e "${BLUE}💓 HEALTH CHECK:${NC}"
    HEALTH_RESPONSE=$(curl -s -w "%{http_code}" "http://localhost:8080/health" -o /tmp/monitor_health.txt 2>/dev/null)
    if [ "$HEALTH_RESPONSE" -eq "200" ]; then
        echo -e "   ${GREEN}✅ Load balancer + cluster: HEALTHY${NC} ($(cat /tmp/monitor_health.txt))"
    else
        echo -e "   ${RED}❌ Load balancer + cluster: FAILED${NC} (HTTP $HEALTH_RESPONSE)"
    fi
    echo ""
    
    # Cluster members
    echo -e "${BLUE}👥 CLUSTER MEMBERS:${NC}"
    MEMBERS_RESPONSE=$(curl -s "http://localhost:8558/cluster/members" 2>/dev/null)
    if [ $? -eq 0 ] && [ -n "$MEMBERS_RESPONSE" ]; then
        echo "$MEMBERS_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "Raw response: $MEMBERS_RESPONSE"
    else
        echo -e "   ${YELLOW}⚠️  Could not fetch cluster members (cluster may be starting...)${NC}"
    fi
    echo ""
    
    # Shard distribution
    echo -e "${BLUE}🎯 SHARD DISTRIBUTION (CaseCompanion):${NC}"
    SHARDS_RESPONSE=$(curl -s "http://localhost:8558/cluster/shards/CaseCompanion" 2>/dev/null)
    if [ $? -eq 0 ] && [ -n "$SHARDS_RESPONSE" ]; then
        echo "$SHARDS_RESPONSE" | python3 -m json.tool 2>/dev/null || echo "Raw response: $SHARDS_RESPONSE"
    else
        echo -e "   ${YELLOW}⚠️  Could not fetch shard info (sharding may not be active yet)${NC}"
    fi
    echo ""
    
    # Load balancer stats (if available)
    echo -e "${BLUE}⚖️  LOAD BALANCER ACCESS LOGS (last 5):${NC}"
    docker logs pekkopoc-load-balancer --tail 5 2>/dev/null | grep -E "(GET|POST)" | tail -5 || echo "   No recent access logs"
    echo ""
    
    # Management endpoints
    echo -e "${BLUE}🎛️  MANAGEMENT ENDPOINTS:${NC}"
    echo "   • Cluster members: curl -s http://localhost:8558/cluster/members | jq"
    echo "   • Shards: curl -s http://localhost:8558/cluster/shards/CaseCompanion | jq"
    echo "   • Health check: curl http://localhost:8080/health"
    echo "   • Load balancer logs: docker logs pekkopoc-load-balancer -f"
    echo ""
    
    echo -e "${YELLOW}⏱️  Next update in ${MONITOR_INTERVAL} seconds... (Press Ctrl+C to stop)${NC}"
    
    # Wait for next update
    sleep $MONITOR_INTERVAL
done

# Cleanup
rm -f /tmp/monitor_health.txt 