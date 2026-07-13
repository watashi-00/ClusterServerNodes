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

echo "=================================================="
