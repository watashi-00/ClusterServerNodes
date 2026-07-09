package hexacloud.gateway;

import hexacloud.gateway.contracts.ImplGateway;

public class GatewayFactory {
    public static ImplGateway createGateway() {
        return new Gateway();
    }    
}
