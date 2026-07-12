package hexacloud.core.server;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.contracts.Implserver;
import hexacloud.core.utils.DebugUtils;

public class ServerManager implements Implserver{
    private final Server server;

    public ServerManager(Cluster cluster) {
        DebugUtils.log("Creating ServerManager on default port with cluster: " + (cluster != null ? cluster.getClusterName() : "null"));
        this.server = new Server(cluster);
    }

    public ServerManager(int port, Cluster cluster) {
        DebugUtils.log("Creating ServerManager on port " + port + " with cluster: " + (cluster != null ? cluster.getClusterName() : "null"));
        this.server = new Server(port, cluster);
    }

    @Override
    public void listen(int port) {
        if(server == null) {
            DebugUtils.error("Cannot listen on port " + port + ": Underlying Server instance is null!");
            return;
        }
        DebugUtils.log("ServerManager instructing Server to listen on port " + port);
        this.server.listen(port);
    }

}
