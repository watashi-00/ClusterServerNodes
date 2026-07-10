package hexacloud.application;

import hexacloud.core.model.NodeStatus;
import hexacloud.core.ports.GatewayPort;
import hexacloud.infra.gateway.GatewayFactory;

public class Main {
    
    public static void main(String[] args) {
        new Main().start();
    }

    public void start() {
        GatewayPort gateway = GatewayFactory.createGateway();
        gateway.addServer(8080, NodeStatus.ONLINE);
        gateway.startPingScheduler(5);
    }
}