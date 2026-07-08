package hexacloud.server;

public class Cluster implements ImplServer {
    static ServerNode[] cluster = new ServerNode[4];
    String baseUrl = "http://localhost:";
    private short qtd = 0;

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
        if(host == null || host.isEmpty()) {
            host = "http://localhost";
        }
        if(!host.startsWith("http://") && !host.startsWith("https://")) {
            host = "http://" + host;
        }
        if(host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        if(!host.endsWith(":")) {
            host = host + ":";
        }
        return host;

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
