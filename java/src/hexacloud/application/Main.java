package hexacloud.application;

import hexacloud.core.model.NodeStatus;
import hexacloud.core.ports.GatewayPort;
import hexacloud.core.utils.DebugUtils;
import hexacloud.infra.gateway.GatewayFactory;

public class Main {
    
    public static void main(String[] args) {
        new Main().start();
    }

    public void start() {
        DebugUtils.setDebugEnabled(true);
        
        GatewayPort hexacloud = GatewayFactory.createGateway("watashi-00")
            .port(3000)
            .pingInterval(5)
            .enableTelnet(true)
            .enableHttp(true)
            .enableWs(true)
            .registerServer(3001, NodeStatus.OFFLINE)
            .registerServer(3002, NodeStatus.OFFLINE)
            .registerServer(3003, NodeStatus.OFFLINE)
            .registerServer(3004, NodeStatus.OFFLINE)
            .registerServer(3005, NodeStatus.OFFLINE)
            .listClusterNodes()
            .listen()
            .startPingScheduler();
    }
}