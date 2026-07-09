package hexacloud.gateway.cluster;

import java.util.ArrayList;
import java.util.List;

public class Cluster {
    private final int MAX_CLUSTER_SIZE = 10;
    private final List<ServerNode> cluster = new ArrayList<>(MAX_CLUSTER_SIZE);

    private String clusterName = "DefaultCluster";
    private String clusterUri = "http://localhost:";

    private List<ServerNode> tempCluster;

    public void start(int port, boolean isExternal) {
        centralizedStart(port, clusterUri, isExternal);
    }

    public void start(int port, String host,  boolean isExternal) {
        centralizedStart(port, host, isExternal);
    }

    public void stop(int port) {
        System.out.println("Stopping server on port: " + port);
        removeClusterNode(port);
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
        System.out.println("Current cluster nodes:");
        for (ServerNode node : cluster) {
            if(node != null) {
                System.out.println(node);
            }
        }
    }

    private void toggleAllServers(boolean start) {
        if(!start) {
            this.tempCluster = new ArrayList<>(cluster);
        }

        if(start && (this.tempCluster == null || this.tempCluster.isEmpty())) {
            System.err.println("All servers are already running or no existing servers to start.");
            return;
        }

        if(!start && (cluster.isEmpty())) {
            System.err.println("All servers are already stopped or no existing servers to stop.");
            return;
        }

        for(ServerNode node : start ? tempCluster : cluster) {
            if(node != null) {
                if(start) {
                    start(node.port(), node.host(), node.isExternal());
                } else {
                    stop(node.port());
                }
            }
        }

        if(start) {
            this.tempCluster = null;
        }
        
    }

    private void centralizedStart(int port, String host, boolean isExternal) {
        if(this.tempCluster != null && !this.tempCluster.isEmpty()) {
            System.err.println("Cannot start a new server while there are stopped servers in the cluster. Please start all stopped servers first.");
            return;
        }
        System.out.println("Starting server on host: " + host + ", port: " + port);
        host = validHost(host);
        addClusterNode(new ServerNode(host, port, false, isExternal));
    }

    private void addClusterNode(ServerNode node) {
        if (!validServer(node)) {return;}

        if(cluster.size() >= MAX_CLUSTER_SIZE) {
            System.err.println("Cluster is full. Cannot add more nodes.");
            return;
        }

        cluster.add(node);

    }
    
    private void removeClusterNode() {
        if(cluster.isEmpty()) {
            System.err.println("Cluster is empty. No nodes to remove.");
            return;
        }
        cluster.removeLast();
    }

    private void removeClusterNode(int port) {
        for (int i = 0; i < cluster.size(); i++) {
            if(cluster.get(i) != null && cluster.get(i).port() == port) {
                System.out.println("Removed cluster node: " + cluster.get(i));
                cluster.remove(i);
                return;
            }
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

        if(sb.charAt(sb.length() - 1) != ':') {
            sb.append(':');
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

        for(ServerNode n : cluster) {
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
