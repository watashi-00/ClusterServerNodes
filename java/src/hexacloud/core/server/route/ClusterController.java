package hexacloud.core.server.route;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.ClusterRegistry;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;
import hexacloud.core.utils.DebugUtils;

public class ClusterController implements RouteController {

    private final Cluster cluster;

    public ClusterController(Cluster cluster) {
        this.cluster = cluster;
    }

    @RouteMapping("GET_NODES")
    public void getNodes(String args, PrintWriter out) {
        StringBuilder sb = new StringBuilder();
        for(ServerNode node : this.cluster.getCluster()) {
            sb.append(node.getFullHost()).append("=").append(node.status()).append(";");
        }
        out.println(sb.toString());
    }

    @RouteMapping("REGISTER")
    public void register(String args, PrintWriter out) {
        try {
            int regPort = Integer.parseInt(args);
            cluster.registerServer(regPort);
            out.println("SUCCESS: Node port " + regPort + " registered.");
        } catch(NumberFormatException e) {
            out.println("ERROR: Invalid port format: " + args);
        }
    }

    @RouteMapping("TELEMETRY")
    public void telemetry(String args, PrintWriter out) {
        if (args == null || args.trim().isEmpty()) {
            out.println("ERROR: Missing arguments. Expected format: <host> <port> [key=value]... or host=...&port=...");
            return;
        }

        String host = null;
        int port = 0;
        Double cpu = null;
        Double ram = null;
        String lang = null;
        Integer latency = null;
        String statusStr = null;
        String eventName = null;
        String eventProtocol = null;
        String eventFormat = null;
        Map<String, String> eventAttributes = new LinkedHashMap<>();

        if (args.contains("&") || args.contains("host=")) {
            String[] params = args.split("&");
            for (String param : params) {
                if (!param.contains("=")) continue;
                String[] kv = param.split("=", 2);
                String key = kv[0].toLowerCase().trim();
                String val = kv[1].replace("+", " ").replace("%20", " ").trim();
                collectEventAttribute(eventAttributes, key, val);
                try {
                    if (key.equals("host")) host = val;
                    else if (key.equals("port")) port = Integer.parseInt(val);
                    else if (key.equals("cpu")) cpu = Double.parseDouble(val);
                    else if (key.equals("ram")) ram = Double.parseDouble(val);
                    else if (key.equals("language") || key.equals("lang")) lang = val;
                    else if (key.equals("latency")) latency = Integer.parseInt(val);
                    else if (key.equals("status")) statusStr = val;
                    else if (key.equals("event")) eventName = val;
                    else if (key.equals("protocol")) eventProtocol = val;
                    else if (key.equals("format")) eventFormat = val;
                } catch (Exception e) {
                    DebugUtils.error(cluster.getClusterName(), null, "Failed to parse query parameter: key=" + key + ", value=" + val, e);
                }
            }
        } else {
            String decodedArgs = args.replace("+", " ").replace("%20", " ");
            String[] parts = decodedArgs.trim().split("\\s+");
            if (parts.length >= 2) {
                host = parts[0];
                try {
                    port = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    DebugUtils.error(cluster.getClusterName(), null, "Failed to parse port parameter: " + parts[1], e);
                }

                for (int i = 2; i < parts.length; i++) {
                    String kv = parts[i];
                    if (!kv.contains("=")) continue;
                    String[] kvParts = kv.split("=", 2);
                    String key = kvParts[0].toLowerCase().trim();
                    String val = kvParts[1].trim();
                    collectEventAttribute(eventAttributes, key, val);
                    try {
                        if (key.equals("cpu")) cpu = Double.parseDouble(val);
                        else if (key.equals("ram")) ram = Double.parseDouble(val);
                        else if (key.equals("language") || key.equals("lang")) lang = val;
                        else if (key.equals("latency")) latency = Integer.parseInt(val);
                        else if (key.equals("status")) statusStr = val;
                        else if (key.equals("event")) eventName = val;
                        else if (key.equals("protocol")) eventProtocol = val;
                        else if (key.equals("format")) eventFormat = val;
                    } catch (Exception e) {
                        DebugUtils.error(cluster.getClusterName(), null, "Failed to parse telemetry parameter: key=" + key + ", value=" + val, e);
                    }
                }
            }
        }

        if (host == null || port == 0) {
            out.println("ERROR: Missing or invalid host/port parameter.");
            return;
        }

        NodeStatus requestedStatus = null;
        if (statusStr != null) {
            try {
                requestedStatus = NodeStatus.valueOf(statusStr.toUpperCase());
            } catch (Exception e) {
                DebugUtils.error(cluster.getClusterName(), null, "Failed to parse status value: " + statusStr, e);
            }
        }

        Cluster.NodeUpdateResult result = cluster.updateTelemetryServer(host, port, cpu, ram, lang, latency, requestedStatus);
        if (result == null) {
            out.println("ERROR: Node not registered: " + host + ":" + port);
            return;
        }

        NodeStatus finalStatus = requestedStatus != null ? requestedStatus : NodeStatus.ONLINE;
        if (result.statusChanged()) {
            cluster.dispatchEvent(new hexacloud.core.cluster.event.ClusterEvent.NodeStatusChanged(result.host(), finalStatus));
        }
        if (result.telemetryUpdated()) {
            cluster.dispatchEvent(new hexacloud.core.cluster.event.ClusterEvent.NodeTelemetryUpdated(result.host()));
        }
        if (eventName != null && !eventName.isBlank()) {
            cluster.dispatchEvent(new hexacloud.core.cluster.event.ClusterEvent.NodeEventSubmitted(
                result.host(),
                port,
                normalizeEventProtocol(eventProtocol, result.protocol()),
                normalizeEventFormat(eventFormat),
                eventName,
                Map.copyOf(eventAttributes)
            ));
        }

        out.println("SUCCESS: Telemetry updated for " + host + ":" + port);
    }

    private void collectEventAttribute(Map<String, String> attributes, String key, String value) {
        if (key == null || key.isBlank()
            || key.equals("host")
            || key.equals("port")
            || key.equals("event")
            || key.equals("protocol")
            || key.equals("format")
            || key.equals("token")) {
            return;
        }
        attributes.put(key, value);
    }

    private String normalizeEventProtocol(String requestedProtocol, String fallbackProtocol) {
        if (requestedProtocol == null || requestedProtocol.isBlank()) {
            return fallbackProtocol != null && !fallbackProtocol.isBlank() ? fallbackProtocol : "Unknown";
        }
        return requestedProtocol.trim().toUpperCase();
    }

    private String normalizeEventFormat(String requestedFormat) {
        if (requestedFormat == null || requestedFormat.isBlank()) {
            return "text";
        }
        return requestedFormat.trim().toLowerCase();
    }

    @RouteMapping("DEREGISTER")
    public void deregister(String args, PrintWriter out) {
        if (args == null || args.trim().isEmpty()) {
            out.println("ERROR: Missing host address.");
            return;
        }
        cluster.deregisterServer(args.trim());
        out.println("SUCCESS: Node " + args.trim() + " deregistered.");
    }

    @RouteMapping("LIST_CLUSTERS")
    public void listClusters(String args, PrintWriter out) {
        StringBuilder sb = new StringBuilder();
        for(Cluster c : ClusterRegistry.getInstance().getClusters()) {
            sb.append(c.getClusterName()).append(";");
        }
        out.println(sb.toString());
    }

    @RouteMapping("CREATE_CLUSTER")
    public void createCluster(String args, PrintWriter out) {
        if(args == null || args.trim().isEmpty()) {
            out.println("ERROR: Missing cluster name.");
            return;
        }
        String clusterName = args.trim();
        ClusterRegistry.getInstance().createCluster(clusterName);
        out.println("SUCCESS: Cluster '" + clusterName + "' created.");
    }

    @RouteMapping("GET_CLUSTER_CONFIG")
    public void getClusterConfig(String args, PrintWriter out) {
        StringBuilder sb = new StringBuilder();
        sb.append("requireToken=").append(cluster.isRequireToken()).append(";");
        sb.append("timeoutMs=").append(cluster.getTimeoutMs()).append(";");
        sb.append("allowedIps=").append(cluster.getAllowedIps()).append(";");
        sb.append("rateLimitRequests=").append(cluster.getRateLimitRequests()).append(";");
        sb.append("rateLimitDurationSeconds=").append(cluster.getRateLimitDurationSeconds()).append(";");
        out.println(sb.toString());
    }

    @RouteMapping("GET_GLOBAL_CONFIG")
    public void getGlobalConfig(String args, PrintWriter out) {
        StringBuilder sb = new StringBuilder();
        sb.append("maxClusterSize=").append(hexacloud.core.config.ClusterConfig.MAX_CLUSTER_SIZE).append(";");
        sb.append("maxWorkers=").append(hexacloud.core.config.ClusterConfig.MAX_WORKERS).append(";");
        sb.append("pingInterval=").append(hexacloud.core.config.ClusterConfig.DEFAULT_PING_INTERVAL_SECONDS).append(";");
        sb.append("httpVersion=").append(hexacloud.core.config.ClusterConfig.HTTP_VERSION.name()).append(";");
        out.println(sb.toString());
    }

    @RouteMapping("SET_ALLOWED_IPS")
    public void setAllowedIps(String args, PrintWriter out) {
        cluster.setAllowedIps(args.trim());
        out.println("SUCCESS: Allowed IPs updated.");
    }

    @RouteMapping("SET_TIMEOUT")
    public void setTimeout(String args, PrintWriter out) {
        try {
            int timeout = Integer.parseInt(args.trim());
            cluster.setTimeoutMs(timeout);
            out.println("SUCCESS: Timeout updated.");
        } catch (NumberFormatException e) {
            out.println("ERROR: Invalid timeout: " + args);
        }
    }

    @RouteMapping("SET_RATE_LIMIT")
    public void setRateLimit(String args, PrintWriter out) {
        try {
            String[] parts = args.trim().split(" ");
            if (parts.length < 2) {
                out.println("ERROR: Expected format: <requests> <durationSeconds>");
                return;
            }
            int requests = Integer.parseInt(parts[0]);
            int duration = Integer.parseInt(parts[1]);
            cluster.setRateLimit(requests, duration);
            out.println("SUCCESS: Rate limit updated.");
        } catch (NumberFormatException e) {
            out.println("ERROR: Invalid format.");
        }
    }
}
