package hexacloud.infra.gateway;

import hexacloud.core.ports.GatewayPort;

public class GatewayFactory {
    public static GatewayPort createGateway() {
        return new LocalGatewayAdapter();
    }    
}
