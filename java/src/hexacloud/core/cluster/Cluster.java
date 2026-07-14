package hexacloud.core.cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.config.ClusterConfig;
import hexacloud.core.config.ClusterStatePersistence;
import hexacloud.core.cluster.event.ClusterEventBusManager;
import hexacloud.core.cluster.event.NodeRegistered;
import hexacloud.core.server.route.RouteRegistry;
import hexacloud.core.server.route.ClusterController;

public class Cluster {

    private final ConcurrentHashMap<String, ServerNode> cluster;
    private final ClusterEventBusManager eventManager;
    private final ClusterSecurityManager securityManager;
    private final RouteRegistry routeRegistry;

    private String clusterName = ClusterConfig.DEFAULT_CLUSTER_NAME;
    private String clusterUri = ClusterConfig.DEFAULT_CLUSTER_URI;
    private List<ServerNode> tempCluster;

    public Cluster() {
        this(ClusterConfig.DEFAULT_CLUSTER_NAME, null);
    }

    public Cluster(String clusterName) {
        this(clusterName, null);
    }

    public Cluster(String clusterName, ClusterEventBusManager eventManager) {
        this.cluster = new ConcurrentHashMap<>();
        this.clusterName = clusterName;
        this.eventManager = eventManager;
        this.securityManager = new ClusterSecurityManager(clusterName);
        this.routeRegistry = new RouteRegistry();
        this.routeRegistry.registerController(new ClusterController(this));
        ClusterRegistry.getInstance().registerCluster(this);
    }

    public RouteRegistry getRouteRegistry() {
        return routeRegistry;
    }

    public ClusterSecurityManager security() {
        return securityManager;
    }

    public void registerServer(ServerNode node) {
        if (node == null) return;
        if(this.tempCluster != null && !this.tempCluster.isEmpty()) {
            DebugUtils.error(this.clusterName, null, "Cannot register a new server while there are stopped servers in the cluster. Please register all stopped servers first.");
            return;
        }
        String host = validHost(node.host());
        if (host == null) return;

        ServerNode validNode = new ServerNode(
            host, node.port(), node.status(), node.isExternal(),
            node.pingEnabled(), node.pingPath(), node.pingHeaderName(), node.pingHeaderValue()
        );
        addClusterNode(validNode);
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
            DebugUtils.error(this.clusterName, host, "Cannot update status: Server host '" + host + "' is not registered in the cluster.");
            return;
        }
        this.cluster.computeIfPresent(host, (key, serverNode) -> serverNode.withStatus(status));
    }

    /**
     * Update an existing server node configuration in the registry.
     */
    public void updateServerNode(ServerNode updatedNode) {
        if (updatedNode == null) return;
        String key = updatedNode.getFullHost();
        if (this.cluster.containsKey(key)) {
            this.cluster.put(key, updatedNode);
            DebugUtils.log("Updated server node configuration: " + updatedNode);
            ClusterStatePersistence.saveState();
        }
    }

    private void toggleAllServers(boolean start) {
        if(!start) {
            this.tempCluster = new ArrayList<>(cluster.values());
        }

        if(start && (this.tempCluster == null || this.tempCluster.isEmpty())) {
            DebugUtils.error(this.clusterName, null, "All servers are already running or no existing servers to start.");
            return;
        }

        if(!start && (cluster.isEmpty())) {
            DebugUtils.error(this.clusterName, null, "All servers are already stopped or no existing servers to stop.");
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
            DebugUtils.error(this.clusterName, null, "Cannot register a new server while there are stopped servers in the cluster. Please register all stopped servers first.");
            return;
        }
        DebugUtils.log("Registering server on host: " + host + ", port: " + port);
        host = validHost(host);
        addClusterNode(new ServerNode(host, port, status, isExternal));
    }

    private void addClusterNode(ServerNode node) {
        if (!validServer(node)) {return;}

        if(cluster.size() >= ClusterConfig.MAX_CLUSTER_SIZE) {
            DebugUtils.error(this.clusterName, null, "Cluster is full. Cannot add more nodes.");
            return;
        }

        cluster.put(node.getFullHost(), node);
        if(eventManager != null) {
            eventManager.dispatch(new NodeRegistered(node));
        }
        ClusterStatePersistence.saveState();
    }
    
    private void removeClusterNode() {
        if(cluster.isEmpty()) {
            DebugUtils.error(this.clusterName, null, "Cluster is empty. No nodes to remove.");
            return;
        }
        cluster.keySet().stream()
            .reduce((first, second) -> second)
            .ifPresent(lastKey -> {
                cluster.remove(lastKey);
                ClusterStatePersistence.saveState();
            });
    }

    private void removeClusterNode(String fullHost) {
        if(cluster.containsKey(fullHost)) {
            cluster.remove(fullHost);
            ClusterStatePersistence.saveState();
        }
    }

    private String validHost(String host) {
        if(host == null || host.isEmpty()) {
            DebugUtils.error(this.clusterName, null, "Invalid host: null or empty");
            return null;
        }

        if(!host.startsWith("http://") && !host.startsWith("https://") 
           && !host.startsWith("ws://") && !host.startsWith("wss://") 
           && !host.startsWith("tcp://")) {
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
            DebugUtils.error(this.clusterName, null, "Invalid server node: null");
            return false;
        }
        if(node.host() == null || node.host().isEmpty()) {
            DebugUtils.error(this.clusterName, null, "Invalid server node: host is null or empty");
            return false;
        }
        if(node.port() <= 0 || node.port() > 65535) {
            DebugUtils.error(this.clusterName, null, "Invalid server node: port is out of range");
            return false;
        }

        boolean portInUse = cluster.values().stream().anyMatch(n -> n != null && n.port() == node.port());
        if(portInUse) {
            DebugUtils.error(this.clusterName, null, "Invalid server node: port is already in use");
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
        return securityManager.getSecret();
    }

    public boolean isRequireToken() {
        return securityManager.isRequireToken();
    }

    public int getTimeoutMs() {
        return securityManager.getTimeoutMs();
    }

    public String getAllowedIps() {
        return securityManager.getAllowedIps();
    }

    public boolean authenticate(String token) {
        return securityManager.authenticate(token);
    }

    public boolean isIpAllowed(String ipAddress) {
        return securityManager.isIpAllowed(ipAddress);
    }

    public boolean checkRateLimit(String clientId) {
        return securityManager.checkRateLimit(clientId);
    }

    public int getRateLimitRequests() {
        return securityManager.getRateLimitRequests();
    }

    public int getRateLimitDurationSeconds() {
        return securityManager.getRateLimitDurationSeconds();
    }

    public void setTimeoutMs(int timeoutMs) {
        securityManager.setTimeoutMs(timeoutMs);
        ClusterStatePersistence.saveState();
    }

    public void setAllowedIps(String allowedIps) {
        securityManager.setAllowedIps(allowedIps);
        ClusterStatePersistence.saveState();
    }

    public void setRateLimit(int requests, int durationSeconds) {
        securityManager.setRateLimit(requests, durationSeconds);
        ClusterStatePersistence.saveState();
    }

    public void setSecret(String secret) {
        securityManager.setSecret(secret);
        ClusterStatePersistence.saveState();
    }

    public void setRequireToken(boolean requireToken) {
        securityManager.setRequireToken(requireToken);
        ClusterStatePersistence.saveState();
    }
}
