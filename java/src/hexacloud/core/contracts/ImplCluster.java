package hexacloud.core.contracts;

import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;

public interface ImplCluster {
    void registerAllServers();
    void registerServer(int port);
    void registerServer(ServerNode serverNode);
    void registerServer(int port, NodeStatus status);
    void registerServer(int port, boolean isExternal);
    void registerServer(int port, String host, boolean isExternal);
    
    void deregisterAllServers();
    void deregisterServer(String fullHost);
    void deregisterLastServer();

    void listClusterNodes();
}
