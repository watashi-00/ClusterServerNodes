package hexacloud.application;

import hexacloud.core.model.NodeStatus;
import hexacloud.core.ports.GatewayPort;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.tui.TerminalUiFactory;
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
            .registerServer(3004, NodeStatus.OFFLINE);
            
        // Register port 3005 with custom health-check path and header using the new NodeBuilder API
        hexacloud.registerNode("http://localhost", 3005)
            .pingEnabled(true)
            .pingPath("/")
            .pingHeader("Authorization", "Bearer showCaseToken")
            .register();

        hexacloud.listen()
            .startPingScheduler();

        // Launch the pure Terminal UI client passing the configured gateway
        TerminalUiFactory.createTui("MyCompany - GateBridge DevOps Panel")
            .seedGateway(hexacloud)
            .start();
    }
}
