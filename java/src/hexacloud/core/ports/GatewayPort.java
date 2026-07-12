package hexacloud.core.ports;

import hexacloud.core.contracts.ClusterOperations;
import hexacloud.core.contracts.SchedulerOperations;
import hexacloud.core.contracts.ServerOperations;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;
import hexacloud.core.cluster.event.ClusterEventBusManager;

public interface GatewayPort extends SchedulerOperations, ClusterOperations, ServerOperations {
    
    GatewayPort port(int port);
    GatewayPort pingInterval(int intervalInSeconds);
    GatewayPort enableTelnet(boolean enabled);
    GatewayPort enableHttp(boolean enabled);
    GatewayPort enableWs(boolean enabled);

    @Override GatewayPort registerAllServers();
    @Override GatewayPort registerServer(int port);
    @Override GatewayPort registerServer(ServerNode serverNode);
    @Override GatewayPort registerServer(int port, NodeStatus status);
    @Override GatewayPort deregisterAllServers();
    @Override GatewayPort deregisterServer(String fullHost);
    @Override GatewayPort deregisterLastServer();
    @Override GatewayPort listClusterNodes();

    @Override GatewayPort startPingScheduler();
    @Override GatewayPort startPingScheduler(int intervalInSeconds);
    @Override GatewayPort setPingInterval(int intervalInSeconds);
    @Override GatewayPort stopPingScheduler();

    @Override GatewayPort listen(int port);
    @Override GatewayPort listen();

    ClusterEventBusManager eventManager();
}
