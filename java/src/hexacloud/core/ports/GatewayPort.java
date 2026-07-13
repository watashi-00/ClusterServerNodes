package hexacloud.core.ports;

import hexacloud.core.contracts.ClusterOperations;
import hexacloud.core.contracts.SchedulerOperations;
import hexacloud.core.contracts.ServerOperations;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;
import hexacloud.core.cluster.event.ClusterEventBusManager;

/**
 * Main entrance port for the GateBridge gateway framework.
 * Provides APIs to manage cluster server nodes, network servers, and monitoring schedulers.
 */
public interface GatewayPort extends SchedulerOperations, ClusterOperations, ServerOperations {
    
    /**
     * Start configuring a server node with custom health check parameters (ping path, token headers, etc.).
     *
     * @param host the protocol and domain/IP of the service node (e.g. "http://localhost").
     * @param port the port number the service node listens on.
     * @return a builder instance to further configure and register the node.
     */
    NodeBuilderPort registerNode(String host, int port);
    
    /**
     * Set the main Telnet listening port.
     */
    GatewayPort port(int port);
    
    /**
     * Set the global check interval for the ping scheduler.
     */
    GatewayPort pingInterval(int intervalInSeconds);
    
    /**
     * Enable or disable the Telnet server transport interface.
     */
    GatewayPort enableTelnet(boolean enabled);
    
    /**
     * Enable or disable the HTTP REST API transport interface.
     */
    GatewayPort enableHttp(boolean enabled);
    
    /**
     * Enable or disable the WebSocket transport interface.
     */
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
    GatewayPort stop();

    /**
     * Get the cluster name associated with this gateway.
     */
    String getClusterName();

    /**
     * Access the event manager to subscribe to cluster node changes or connection pings.
     */
    ClusterEventBusManager eventManager();
}
