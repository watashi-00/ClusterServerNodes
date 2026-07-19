#!/bin/bash

GATEWAY_HTTP="http://localhost:3001"
GATEWAY_TELNET="localhost"
CLUSTER="watashi-00"
TOKEN="watashi_secretKey"

echo "=================================================="
echo "          GateBridge DevOps Shell Controller"
echo "=================================================="
echo ""

# 1. Fetch nodes list via HTTP GET
echo "[HTTP] Querying cluster nodes list via GET request..."
curl -s -H "X-Cluster-Token: ${TOKEN}" "${GATEWAY_HTTP}/clusters/${CLUSTER}/get_nodes"
echo -e "\n"

# 1b. Fetch nodes list as JSON via HTTP GET
echo "[HTTP] Querying cluster nodes list in JSON format..."
curl -s -H "X-Cluster-Token: ${TOKEN}" "${GATEWAY_HTTP}/clusters/${CLUSTER}/get_nodes_json"
echo -e "\n"

# 2. Fetch cluster security config via HTTP GET
echo "[HTTP] Querying cluster configurations..."
curl -s -H "X-Cluster-Token: ${TOKEN}" "${GATEWAY_HTTP}/clusters/${CLUSTER}/get_cluster_config"
echo -e "\n"

# 3. Fetch nodes list via Telnet (netcat TCP client)
if command -v nc >/dev/null 2>&1; then
    echo "[Telnet] Querying cluster nodes list via Telnet socket command..."
    # Format: <token> <command> <args>
    echo "${TOKEN} GET_NODES" | nc -N localhost 3000
    echo ""
else
    echo "[Skip] Netcat ('nc') is not installed, skipping Telnet socket test."
fi

# 4. Submit custom telemetry event via HTTP GET
echo "[HTTP] Submitting node telemetry and event via HTTP GET request..."
curl -s -H "X-Cluster-Token: ${TOKEN}" "${GATEWAY_HTTP}/clusters/${CLUSTER}/telemetry?host=localhost&port=3005&cpu=18.4&ram=45.2&event=PythonServiceStarted&protocol=HTTP&format=JSON"
echo -e "\n"

# 5. Submit custom telemetry event via Telnet socket
if command -v nc >/dev/null 2>&1; then
    echo "[Telnet] Submitting node telemetry and event via Telnet socket..."
    echo "${TOKEN} TELEMETRY localhost 3004 cpu=12.8 ram=38.4 event=NodeServiceHealthy protocol=TCP format=JSON" | nc -N localhost 3000
    echo ""
fi

echo "=================================================="
