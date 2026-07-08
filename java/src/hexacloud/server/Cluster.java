package hexacloud.server;

import hexacloud.server.check.SchedulerPing;

public class Cluster implements ImplServer {
    static ServerNode[] cluster = new ServerNode[4];
    String baseUrl = "http://localhost:";
    private short qtd = 0;

    private ServerNode[] tempCluster;

    SchedulerPing schedulerPing = new SchedulerPing();

    @Override
    public void start(int port, boolean isExternal) {
        System.out.println("Starting server on port: " + port);
        addClusterNode(new ServerNode(baseUrl, port, false));
    }

    @Override
    public void start(int port, String host,  boolean isExternal) {
        System.out.println("Starting server on host: " + host + ", port: " + port);
        host = validHost(host);
        addClusterNode(new ServerNode(host, port, false));
    }

    @Override
    public void stop(int port) {
        System.out.println("Stopping server on port: " + port);
        removeClusterNode(new ServerNode(null, port, false));
    }

    @Override
    public void stop() {
        System.out.println("Stopping server");
        removeClusterNode();
    }
    
    @Override
    public void stopAll() {
        this.schedulerPing.stopPingScheduler();
        tempCluster = cluster.clone();
        for(ServerNode node : cluster) {
            if(node != null) {
                System.out.println("Stopping server on host: " + node.host() + ", port: " + node.port());
                removeClusterNode(node);
            }
        }
    }

    @Override
    public void startAll() {
        for(ServerNode node : tempCluster) {
            if(node != null) {
                System.out.println("Starting server on host: " + node.host() + ", port: " + node.port());
                addClusterNode(node);
            }
        }
        this.schedulerPing.startPingScheduler(cluster);
        tempCluster = null;
    }

    @Override
    public void setInterval(int interval) {

    }


    private void addClusterNode(ServerNode node) {
        if (!validServer(node)) {return;}
        for (int i = 0; i < cluster.length; i++) {
            if(cluster[i] == null) {
                cluster[i] = node;
                System.out.println("Added cluster node: " + node.host());
                return;
            }
        }
    }
    private void removeClusterNode() {
        for (int i = cluster.length - 1; i >= 0; i--) {
            if(cluster[i] != null) {
                System.out.println("Removed cluster node: " + cluster[i].host());
                cluster[i] = null;
                return;
            }
        }
    }

    private void removeClusterNode(ServerNode node) {
        for (int i = 0; i < cluster.length; i++) {
            if(cluster[i] != null && cluster[i].port() == node.port()) {
                System.out.println("Removed cluster node: " + cluster[i].host());
                cluster[i] = null;
                return;
            }
        }
    }

    public void listClusterNodes() {
        System.out.println("Current cluster nodes:");
        for (ServerNode node : cluster) {
            if(node != null) {
                System.out.println(node.host());
            }
        }
    }

    private String validHost(String host) {
        StringBuilder sb = new StringBuilder(host);

        if(sb == null || sb.length() == 0) {
            System.err.println("Invalid host: null or empty");
            return null;
        }

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

        if(qtd >= cluster.length) {
            System.err.println("Invalid server node: cluster is full");
            return false;
        }

        return true;
    }
}
