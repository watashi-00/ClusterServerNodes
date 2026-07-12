package hexacloud.application;

import hexacloud.core.model.NodeStatus;
import hexacloud.core.ports.GatewayPort;
import hexacloud.core.utils.DebugUtils;

public class Main {
    
    public static void main(String[] args) {
        new Main().start();
    }

    public void start() {
        DebugUtils.setDebugEnabled(true);
        
        GatewayPort hexacloud = GatewayPort.builder("watashi-00")
            .port(3000)
            .pingInterval(5)
            .enableTelnet(true)
            .enableHttp(true)
            .enableWs(true)
            .build();

        hexacloud.registerServer(3001, NodeStatus.OFFLINE);
        hexacloud.registerServer(3002, NodeStatus.OFFLINE);
        hexacloud.registerServer(3003, NodeStatus.OFFLINE);
        hexacloud.registerServer(3004, NodeStatus.OFFLINE);
        hexacloud.registerServer(3005, NodeStatus.OFFLINE);
        
        hexacloud.listClusterNodes();
        hexacloud.listen();
        hexacloud.startPingScheduler();
    }
}