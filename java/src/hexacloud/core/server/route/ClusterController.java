package hexacloud.core.server.route;

import java.io.PrintWriter;
import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.ClusterRegistry;
import hexacloud.core.model.ServerNode;

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
            out.println("ERROR: Missing arguments. Expected format: <host> <port> [key=value]...");
            return;
        }

        String[] parts = args.trim().split("\\s+");
        if (parts.length < 2) {
            out.println("ERROR: Host and port are required. Format: <host> <port> [key=value]...");
            return;
        }

        String host = parts[0];
        int port;
        try {
            port = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            out.println("ERROR: Invalid port format: " + parts[1]);
            return;
        }

        ServerNode targetNode = null;
        for (ServerNode node : cluster.getCluster()) {
            String normalizedNodeHost = node.host().replace("http://", "").replace("https://", "").replace("ws://", "").replace("wss://", "").replace("tcp://", "").replace("udp://", "").replace("grpc://", "");
            String normalizedTargetHost = host.replace("http://", "").replace("https://", "").replace("ws://", "").replace("wss://", "").replace("tcp://", "").replace("udp://", "").replace("grpc://", "");
            
            if (normalizedNodeHost.equals(normalizedTargetHost) && node.port() == port) {
                targetNode = node;
                break;
            }
        }

        if (targetNode == null) {
            out.println("ERROR: Node not registered: " + host + ":" + port);
            return;
        }

        for (int i = 2; i < parts.length; i++) {
            String kv = parts[i];
            if (!kv.contains("=")) continue;
            String[] kvParts = kv.split("=", 2);
            String key = kvParts[0].toLowerCase();
            String val = kvParts[1];

            try {
                if (key.equals("cpu")) {
                    targetNode.setCpuUsage(Double.parseDouble(val));
                } else if (key.equals("ram")) {
                    targetNode.setRamUsage(Double.parseDouble(val));
                } else if (key.equals("language") || key.equals("lang")) {
                    targetNode.setRuntime(val);
                } else if (key.equals("latency")) {
                    targetNode.setLatencyMs(Integer.parseInt(val));
                } else if (key.equals("status")) {
                    hexacloud.core.model.NodeStatus newStatus = hexacloud.core.model.NodeStatus.valueOf(val.toUpperCase());
                    if (targetNode.status() != newStatus) {
                        cluster.dispatchEvent(new hexacloud.core.cluster.event.NodeStatusChanged(targetNode.getFullHost(), newStatus));
                    }
                }
            } catch (Exception e) {
                // Ignore parameter format errors
            }
        }

        if (targetNode.status() != hexacloud.core.model.NodeStatus.ONLINE) {
            cluster.dispatchEvent(new hexacloud.core.cluster.event.NodeStatusChanged(targetNode.getFullHost(), hexacloud.core.model.NodeStatus.ONLINE));
        }

        out.println("SUCCESS: Telemetry updated for " + host + ":" + port);
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
