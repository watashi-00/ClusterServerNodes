package hexacloud.infra.gateway;

import hexacloud.core.ports.GatewayPort;

/**
 * Factory class for bootstrapping GateBridge gateways.
 * Instantiates the default LocalGatewayAdapter with the required configuration parameters.
 */
public class GatewayFactory {
    
    /**
     * Create a GateBridge Gateway instance for the specified cluster.
     *
     * @param clusterName the unique name of the cluster.
     * @return the GatewayPort implementation instance.
     */
    public static GatewayPort createGateway(String clusterName) {
        return new LocalGatewayAdapter(clusterName);
    }

    /**
     * Create a GateBridge Gateway instance listening on a pre-configured Telnet port.
     *
     * @param clusterName the unique name of the cluster.
     * @param port the Telnet server listening port.
     * @return the GatewayPort implementation instance.
     */
    public static GatewayPort createGateway(String clusterName, int port) {
        return new LocalGatewayAdapter(clusterName, port);
    }    
}