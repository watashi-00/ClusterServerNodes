package hexacloud.core.ports;

import hexacloud.core.contracts.ClusterOperations;
import hexacloud.core.contracts.SchedulerOperations;
import hexacloud.core.contracts.ServerOperations;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;

public interface GatewayPort extends SchedulerOperations, ClusterOperations, ServerOperations {
    
    // Configurações fluídas do Gateway
    GatewayPort port(int port);
    GatewayPort pingInterval(int intervalInSeconds);
    GatewayPort enableTelnet(boolean enabled);
    GatewayPort enableHttp(boolean enabled);
    GatewayPort enableWs(boolean enabled);

    // Sobrescritas covariantes de ClusterOperations
    @Override GatewayPort registerAllServers();
    @Override GatewayPort registerServer(int port);
    @Override GatewayPort registerServer(ServerNode serverNode);
    @Override GatewayPort registerServer(int port, NodeStatus status);
    @Override GatewayPort deregisterAllServers();
    @Override GatewayPort deregisterServer(String fullHost);
    @Override GatewayPort deregisterLastServer();
    @Override GatewayPort listClusterNodes();

    // Sobrescritas covariantes de SchedulerOperations
    @Override GatewayPort startPingScheduler();
    @Override GatewayPort startPingScheduler(int intervalInSeconds);
    @Override GatewayPort setPingInterval(int intervalInSeconds);
    @Override GatewayPort stopPingScheduler();

    // Sobrescritas covariantes de ServerOperations
    @Override GatewayPort listen(int port);
    @Override GatewayPort listen();
}
