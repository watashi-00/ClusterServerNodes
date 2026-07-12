package hexacloud.core.cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.config.ClusterConfig;
import hexacloud.core.config.EnvLoader;
import hexacloud.core.utils.RateLimiter;

public class Cluster {

    private final ConcurrentHashMap<String, ServerNode> cluster;

    private String clusterName = ClusterConfig.DEFAULT_CLUSTER_NAME;
    private String clusterUri = ClusterConfig.DEFAULT_CLUSTER_URI;
    private String secret;
    private boolean requireToken;
    private int timeoutMs;
    private String allowedIps;
    private int rateLimitRequests;
    private int rateLimitDurationSeconds;
    private RateLimiter rateLimiter;

    private List<ServerNode> tempCluster;

    public Cluster() {
        this(ClusterConfig.DEFAULT_CLUSTER_NAME);
    }

    public Cluster(String clusterName) {
        this.cluster = new ConcurrentHashMap<>();
        this.clusterName = clusterName;
        this.secret = EnvLoader.get(clusterName, "secret", null);
        this.requireToken = EnvLoader.getBoolean(clusterName, "requireToken", true);
        this.timeoutMs = EnvLoader.getInt(clusterName, "timeoutMs", 5000);
        this.allowedIps = EnvLoader.get(clusterName, "allowedIps", "");
        this.rateLimitRequests = EnvLoader.getInt(clusterName, "rateLimitRequests", 100);
        this.rateLimitDurationSeconds = EnvLoader.getInt(clusterName, "rateLimitDurationSeconds", 60);
        this.rateLimiter = new RateLimiter(this.rateLimitRequests, this.rateLimitDurationSeconds);
        
        DebugUtils.info("Cluster '" + clusterName + "' initialized settings -> requireToken: " + requireToken + ", timeoutMs: " + timeoutMs + ", allowedIps: [" + allowedIps + "], rateLimit: " + rateLimitRequests + "/" + rateLimitDurationSeconds + "s");
    }

    public void registerServer(ServerNode node) {
        centralizedRegister(node.port(), node.host(), node.status(), node.isExternal());
    }

    public void registerServer(int port) {
        centralizedRegister(port, clusterUri, NodeStatus.OFFLINE, false);
    }

    public void registerServer(int port, NodeStatus status) {
        centralizedRegister(port, clusterUri, status, false);
    }

    public void deregisterServer(String fullHost) {
        DebugUtils.log("Deregistering server " + fullHost);
        removeClusterNode(fullHost);
    }

    public void deregisterLastServer() {
        DebugUtils.log("Deregistering last server in the cluster");
        removeClusterNode();
    }

    public void deregisterAllServers() {
        toggleAllServers(false);
    }

    public void registerAllServers() {
        toggleAllServers(true);
    }

    public void listClusterNodes() {
        for (ServerNode node : cluster.values()) {
            if(node != null) {
                DebugUtils.log(node.toString());
            }
        }
    }

    public List<ServerNode> getCluster() {
        return new ArrayList<>(cluster.values());
    }

    public void updateStatusServer(String host, NodeStatus status) {
        if (!this.cluster.containsKey(host)) {
            DebugUtils.error("Cannot update status: Server host '" + host + "' is not registered in the cluster.");
            return;
        }
        this.cluster.computeIfPresent(host, (key, serverNode) -> serverNode.withStatus(status));
    }

    private void toggleAllServers(boolean start) {
        if(!start) {
            this.tempCluster = new ArrayList<>(cluster.values());
        }

        if(start && (this.tempCluster == null || this.tempCluster.isEmpty())) {
            DebugUtils.error("All servers are already running or no existing servers to start.");
            return;
        }

        if(!start && (cluster.isEmpty())) {
            DebugUtils.error("All servers are already stopped or no existing servers to stop.");
            return;
        }

        for(ServerNode node : start ? tempCluster : cluster.values()) {
            if(node != null) {
                if(start) {
                    registerServer(node);
                } else {
                    deregisterServer(node.getFullHost());
                }
            }
        }

        if(start) {
            this.tempCluster = null;
        }
        
    }

    private void centralizedRegister(int port, String host, NodeStatus status, boolean isExternal) {
        if(this.tempCluster != null && !this.tempCluster.isEmpty()) {
            DebugUtils.error("Cannot register a new server while there are stopped servers in the cluster. Please register all stopped servers first.");
            return;
        }
        DebugUtils.log("Registering server on host: " + host + ", port: " + port);
        host = validHost(host);
        addClusterNode(new ServerNode(host, port, status, isExternal));
    }

    private void addClusterNode(ServerNode node) {
        if (!validServer(node)) {return;}

        if(cluster.size() >= ClusterConfig.MAX_CLUSTER_SIZE) {
            DebugUtils.error("Cluster is full. Cannot add more nodes.");
            return;
        }

        cluster.put(node.getFullHost(), node);

    }
    
    private void removeClusterNode() {
        if(cluster.isEmpty()) {
            DebugUtils.error("Cluster is empty. No nodes to remove.");
            return;
        }
        cluster.keySet().stream()
            .reduce((first, second) -> second)
            .ifPresent(lastKey -> cluster.remove(lastKey));
    }

    private void removeClusterNode(String fullHost) {
        if(cluster.containsKey(fullHost)) {
            cluster.remove(fullHost);
        }
    }

    private String validHost(String host) {
        if(host == null || host.isEmpty()) {
            DebugUtils.error("Invalid host: null or empty");
            return null;
        }

        if(!host.startsWith("http://") && !host.startsWith("https://")) {
            host = "http://" + host;
        }
        if(host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        if(host.endsWith(":")) {
            host = host.substring(0, host.length() - 1);
        }
        return host;
    }

    private boolean validServer(ServerNode node) {
        if(node == null) {
            DebugUtils.error("Invalid server node: null");
            return false;
        }
        if(node.host() == null || node.host().isEmpty()) {
            DebugUtils.error("Invalid server node: host is null or empty");
            return false;
        }
        if(node.port() <= 0 || node.port() > 65535) {
            DebugUtils.error("Invalid server node: port is out of range");
            return false;
        }

        boolean portInUse = cluster.values().stream().anyMatch(n -> n != null && n.port() == node.port());
        if(portInUse) {
            DebugUtils.error("Invalid server node: port is already in use");
            return false;
        }

        return true;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getSecret() {
        return secret;
    }

    public boolean isRequireToken() {
        return requireToken;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public String getAllowedIps() {
        return allowedIps;
    }

    public boolean authenticate(String token) {
        if(!requireToken) {
            return true;
        }
        if(secret == null || secret.isEmpty()) {
            DebugUtils.error("Cluster '" + clusterName + "' access barred: Token is required but no secret key is configured.");
            return false;
        }
        boolean authorized = secret.equals(token);
        if(!authorized) {
            DebugUtils.error("Cluster '" + clusterName + "' access barred: Invalid API token provided.");
        }
        return authorized;
    }

    public boolean isIpAllowed(String ipAddress) {
        if(allowedIps == null || allowedIps.trim().isEmpty()) {
            return true;
        }
        String[] ips = allowedIps.split(",");
        for(String ip : ips) {
            if(ip.trim().equals(ipAddress)) {
                return true;
            }
        }
        DebugUtils.error("Cluster '" + clusterName + "' access barred: IP '" + ipAddress + "' is not allowed.");
        return false;
    }

    public boolean checkRateLimit(String clientId) {
        boolean allowed = rateLimiter.allowRequest(clientId);
        if(!allowed) {
            DebugUtils.error("Cluster '" + clusterName + "' access barred: Too many requests from '" + clientId + "'.");
        }
        return allowed;
    }
}
