package hexacloud;

import hexacloud.gateway.GatewayFactory;
import hexacloud.gateway.contracts.ImplGateway;

public class Main {
    
    public static void main(String[] args) {
        new Main().start();
    }

    public void start() {
        ImplGateway gateway = GatewayFactory.createGateway();
        gateway.addServer(8080);
        gateway.startPingScheduler(1);
    }
}