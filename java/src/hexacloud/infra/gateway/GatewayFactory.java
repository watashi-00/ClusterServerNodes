package hexacloud.infra.gateway;

import hexacloud.core.ports.GatewayPort;

public class GatewayFactory {
    public static GatewayPort createGateway(String clusterName) {
        return new LocalGatewayAdapter(clusterName);
    }

    public static GatewayPort createGateway(String clusterName, int port) {
        return new LocalGatewayAdapter(clusterName, port);
    }    
}