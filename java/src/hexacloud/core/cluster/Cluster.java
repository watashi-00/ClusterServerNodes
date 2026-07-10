package hexacloud.core.cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;

public class Cluster {

    private final int MAX_CLUSTER_SIZE = 10;
    private final ConcurrentHashMap<String, ServerNode> cluster;

    private String clusterName = "DefaultCluster";
    private String clusterUri = "http://localhost:";

    private List<ServerNode> tempCluster;

    public Cluster() {
        this.cluster = new ConcurrentHashMap<>();
    }

    public Cluster(String clusterName) {
        this.cluster = new ConcurrentHashMap<>();
        this.clusterName = clusterName;
    }

    public void start(ServerNode node) {
        centralizedStart(node.port(), node.host(), node.status(), node.isExternal());
    }

    public void start(int port) {
        centralizedStart(port, clusterUri, NodeStatus.OFFLINE, false);
    }

    public void start(int port, boolean isExternal) {
        centralizedStart(port, clusterUri, NodeStatus.OFFLINE, isExternal);
    }

    public void start(int port, String host,  boolean isExternal) {
        centralizedStart(port, host, NodeStatus.OFFLINE,isExternal);
    }

    public void start(int port, NodeStatus status) {
        centralizedStart(port, clusterUri, status, false);
    }

    public void stop(String fullHost) {
        System.out.println("Stopping server" + fullHost);
        removeClusterNode(fullHost);
    }

    public void stop() {
        System.out.println("Stopping last server in the cluster");
        removeClusterNode();
    }

    public void stopAll() {
        toggleAllServers(false);
    }

    public void startAll() {
        toggleAllServers(true);
    }

    public void listClusterNodes() {
        for (ServerNode node : cluster.values()) {
            if(node != null) {
                System.out.println(node);
            }
        }
    }

    public List<ServerNode> getCluster() {
        return new ArrayList<>(cluster.values());
    }

    private void toggleAllServers(boolean start) {
        if(!start) {
            this.tempCluster = new ArrayList<>(cluster.values());
        }

        if(start && (this.tempCluster == null || this.tempCluster.isEmpty())) {
            System.err.println("All servers are already running or no existing servers to start.");
            return;
        }

        if(!start && (cluster.isEmpty())) {
            System.err.println("All servers are already stopped or no existing servers to stop.");
            return;
        }

        for(ServerNode node : start ? tempCluster : cluster.values()) {
            if(node != null) {
                if(start) {
                    start(node.port(), node.host(), node.isExternal());
                } else {
                    stop(node.getFullHost());
                }
            }
        }

        if(start) {
            this.tempCluster = null;
        }
        
    }

    private void centralizedStart(int port, String host, NodeStatus status, boolean isExternal) {
        if(this.tempCluster != null && !this.tempCluster.isEmpty()) {
            System.err.println("Cannot start a new server while there are stopped servers in the cluster. Please start all stopped servers first.");
            return;
        }
        System.out.println("Starting server on host: " + host + ", port: " + port);
        host = validHost(host);
        addClusterNode(new ServerNode(host, port, status, isExternal));
    }

    private void addClusterNode(ServerNode node) {
        if (!validServer(node)) {return;}

        if(cluster.size() >= MAX_CLUSTER_SIZE) {
            System.err.println("Cluster is full. Cannot add more nodes.");
            return;
        }

        cluster.put(node.getFullHost(), node);

    }
    
    private void removeClusterNode() {
        if(cluster.isEmpty()) {
            System.err.println("Cluster is empty. No nodes to remove.");
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
            System.err.println("Invalid host: null or empty");
            return null;
        }

        StringBuilder sb = new StringBuilder(host);

        if(sb.indexOf("http://") != 0 && sb.indexOf("https://") != 0) {
            sb.insert(0, "http://");
        }

        if(sb.charAt(sb.length() - 1) == '/') {
            sb.deleteCharAt(sb.length() - 1);
        }

        if(sb.charAt(sb.length() - 1) == ':') {
            sb.deleteCharAt(sb.length() -1);
        }

        return sb.toString();
    }

    private boolean validServer(ServerNode node) {
        if(node == null) {
            System.err.println("Invalid server node: null");
            return false;
        }
        if(node.host() == null || node.host().isEmpty()) {
            System.err.println("Invalid server node: host is null or empty");
            return false;
        }
        if(node.port() <= 0 || node.port() > 65535) {
            System.err.println("Invalid server node: port is out of range");
            return false;
        }

        for(ServerNode n : cluster.values()) {
            if(n != null && n.port() == node.port()) {
                System.err.println("Invalid server node: port is already in use");
                return false;
            }
        }

        return true;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }
}
