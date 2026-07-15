package hexacloud.core.ports;

import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;

/**
 * Interface representing the configuration and bootstrapping phase of the GateBridge gateway.
 * Methods return the builder instance to support fluent configuration chaining.
 */
public interface GatewayBuilderPort {

    /**
     * Start configuring a server node with custom health check parameters (ping path, token headers, etc.).
     */
    NodeBuilderPort registerNode(String host, int port);

    /**
     * Set the main Telnet listening port.
     */
    GatewayBuilderPort port(int port);

    /**
     * Set the global check interval for the ping scheduler.
     */
    GatewayBuilderPort pingInterval(int intervalInSeconds);

    /**
     * Enable or disable the Telnet server transport interface.
     */
    GatewayBuilderPort enableTelnet(boolean enabled);

    /**
     * Enable or disable the HTTP REST API transport interface.
     */
    GatewayBuilderPort enableHttp(boolean enabled);

    /**
     * Enable or disable the WebSocket transport interface.
     */
    GatewayBuilderPort enableWs(boolean enabled);

    /**
     * Register a server node on a given port.
     */
    GatewayBuilderPort registerServer(int port);

    /**
     * Register a server node with a pre-configured status.
     */
    GatewayBuilderPort registerServer(int port, NodeStatus status);

    /**
     * Register a pre-constructed ServerNode instance.
     */
    GatewayBuilderPort registerServer(ServerNode serverNode);

    /**
     * Register all servers defined in the configurations.
     */
    GatewayBuilderPort registerAllServers();

    /**
     * Register a custom route controller to expose additional business command endpoints.
     */
    GatewayBuilderPort registerController(hexacloud.core.server.route.RouteController controller);

    /**
     * Set rate limit rules for the cluster gateway.
     */
    GatewayBuilderPort rateLimit(int requests, int durationSeconds);

    /**
     * Set security token validation settings.
     */
    GatewayBuilderPort requireToken(boolean requireToken, String secret);

    /**
     * Set allowed client IP whitelist (comma-separated).
     */
    GatewayBuilderPort allowedIps(String allowedIps);

    /**
     * Set connection/request timeout in milliseconds.
     */
    GatewayBuilderPort timeout(int timeoutMs);

    /**
     * Start all configured protocol listeners on the default port.
     * Transitions the gateway from the configuration builder phase to the running phase.
     */
    RunningGatewayPort listen();

    /**
     * Start all configured protocol listeners on the specified port.
     * Transitions the gateway from the configuration builder phase to the running phase.
     */
    RunningGatewayPort listen(int port);
}
