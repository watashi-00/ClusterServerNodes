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
        gateway.registerServer(3001, NodeStatus.OFFLINE);
        gateway.registerServer(3002, NodeStatus.OFFLINE);
        gateway.registerServer(3003, NodeStatus.OFFLINE);
        gateway.registerServer(3004, NodeStatus.OFFLINE);
        gateway.registerServer(3005, NodeStatus.OFFLINE);
        gateway.listClusterNodes();
        gateway.startPingScheduler(5);
    }
}