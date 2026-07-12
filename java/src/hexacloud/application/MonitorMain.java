package hexacloud.application;

import hexacloud.core.model.NodeStatus;
import hexacloud.infra.gateway.GatewayFactory;

/**
 * Example launcher for the GateBridge terminal monitor.
 *
 * This class is provided as a sample monitor entry point and is not required
 * for framework integration.
 */
public class MonitorMain {

    private final String clusterName;
    private final int httpPort;

    public static void main(String[] args) {
        new MonitorMain().start();
    }

    public MonitorMain() {
        this.clusterName = "watashi-00";
        this.httpPort = 3001;
    }

    public void start() {
        // Bootstrap gateway server in background
        GatewayFactory.createGateway(clusterName)
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
            .listen()
            .startPingScheduler();

        // Start the terminal UI framework; users only need to call this in their apps.
        TerminalUI.startTerminal(clusterName, httpPort);
    }
}
