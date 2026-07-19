package hexacloud.core.server.route;

import java.io.PrintWriter;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.ClusterRegistry;
import hexacloud.core.cluster.ClusterService;
import hexacloud.core.cluster.TelemetryRequest;
import hexacloud.core.model.ServerNode;

public class ClusterController implements RouteController {

    private final Cluster cluster;
    private final ClusterService clusterService;

    public ClusterController(Cluster cluster) {
        this.cluster = cluster;
        this.clusterService = new ClusterService(cluster);
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

        TelemetryRequest request = TelemetryRequest.parse(cluster.getClusterName(), args);
        if (request.getHost() == null || request.getPort() == 0) {
            out.println("ERROR: Missing or invalid host/port parameter.");
            return;
        }

        boolean success = clusterService.updateTelemetry(request);
        if (success) {
            out.println("SUCCESS: Telemetry updated for " + request.getHost() + ":" + request.getPort());
        } else {
            out.println("ERROR: Node not registered: " + request.getHost() + ":" + request.getPort());
        }
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
