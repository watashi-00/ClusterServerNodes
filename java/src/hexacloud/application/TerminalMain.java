package hexacloud.application;

import hexacloud.core.model.NodeStatus;
import hexacloud.core.ports.GatewayPort;
import hexacloud.core.utils.DebugUtils;
import hexacloud.infra.gateway.GatewayFactory;

public class TerminalMain {
    public static void main(String[] args) {
        DebugUtils.setDebugEnabled(false);
        
        GatewayPort hexacloud = GatewayFactory.createGateway("watashi-00")
            .port(3000)
            .pingInterval(5)
            .enableTelnet(true)
            .enableHttp(true)
            .enableWs(true);

        hexacloud.registerServer(3001, NodeStatus.OFFLINE)
            .registerServer(3002, NodeStatus.OFFLINE)
            .registerServer(3003, NodeStatus.OFFLINE)
            .registerServer(3004, NodeStatus.OFFLINE)
            .registerServer(3005, NodeStatus.OFFLINE)
            .listen()
            .startPingScheduler();

        // Launch the pure Terminal UI client
        TerminalUI.startTerminal("MyCompany - GateBridge DevOps Panel");
    }
}
