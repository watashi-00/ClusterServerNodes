package hexacloud.core.ports;

import java.util.List;

import hexacloud.core.model.ServerNode;

public interface GatewayPort {
    // Cluster
    void addServer(ServerNode node);
    
    void startAllServers();
    void addServer(int port);
    void addServer(int port, boolean isExternal);
    void addServer(int port, String host, boolean isExternal);
    
    void stopAllServers();
    void stopServer(int port);
    void stopLastServer();

    void listClusterNodes();

    // SchedulerPing
    void startPingScheduler();
    void startPingScheduler(int pingInterval);
    void startPingScheduler(List<ServerNode> cluster);
    void startPingScheduler(int pingInterval, List<ServerNode> cluster);
    void setPingInterval(int pingInterval);
    void stopPingScheduler();
    
}
