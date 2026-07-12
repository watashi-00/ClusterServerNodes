package hexacloud.core.contracts;

import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;

public interface ClusterOperations {
    ClusterOperations registerAllServers();
    ClusterOperations registerServer(int port);
    ClusterOperations registerServer(ServerNode serverNode);
    ClusterOperations registerServer(int port, NodeStatus status);
    
    ClusterOperations deregisterAllServers();
    ClusterOperations deregisterServer(String fullHost);
    ClusterOperations deregisterLastServer();

    ClusterOperations listClusterNodes();
}
