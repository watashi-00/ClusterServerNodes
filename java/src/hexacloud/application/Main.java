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
        GatewayPort hexacloud = GatewayFactory.createGateway("watashi-00");
        hexacloud.enableTelnet(true)
                 .enableHttp(true)
                 .enableWs(true);
        hexacloud.listen(3000);
        hexacloud.registerServer(3001, NodeStatus.OFFLINE);
        hexacloud.registerServer(3002, NodeStatus.OFFLINE);
        hexacloud.registerServer(3003, NodeStatus.OFFLINE);
        hexacloud.registerServer(3004, NodeStatus.OFFLINE);
        hexacloud.registerServer(3005, NodeStatus.OFFLINE);
        hexacloud.listClusterNodes();
        hexacloud.startPingScheduler(5);
    }
}