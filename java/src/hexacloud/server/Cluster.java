package hexacloud.server;

public class Cluster implements ImplServer {
    static ServerNode[] cluster = new ServerNode[4];
    String baseUrl = "http://localhost:";

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
        removeClusterNode(new ServerNode(baseUrl, port, false));
    }

    @Override
    public void stop() {
        System.out.println("Stopping server");
        removeClusterNode();
    }

    private void addClusterNode(ServerNode node) {
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
            if(cluster[i] != null && cluster[i].host().equals(node.host()) && cluster[i].port() == node.port()) {
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
}
