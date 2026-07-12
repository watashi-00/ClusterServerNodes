package hexacloud.application;

import hexacloud.core.model.NodeStatus;
import hexacloud.core.ports.GatewayPort;
import hexacloud.core.utils.DebugUtils;
import hexacloud.infra.gateway.GatewayFactory;
import hexacloud.core.event.Event;
import hexacloud.core.event.EventController;
import hexacloud.core.event.Subscribe;
import hexacloud.core.cluster.event.NodeRegistered;

public class Main {
    
    public static void main(String[] args) {
        new Main().start();
    }

    public void start() {
        DebugUtils.setDebugEnabled(false);
        
        GatewayPort hexacloud = GatewayFactory.createGateway("watashi-00")
            .port(3000)
            .pingInterval(5)
            .enableTelnet(true)
            .enableHttp(true)
            .enableWs(true);

        // Custom event verification
        record UserCustomEvent(String message) implements Event {}

        // Custom controller listener
        class CustomEventListener implements EventController {
            @Subscribe
            public void onCustomEvent(UserCustomEvent event) {
                DebugUtils.info("UserCustomEvent handler method invoked: " + event.message());
            }

            @Subscribe
            public void onNodeRegistered(NodeRegistered event) {
                DebugUtils.info("Event received: Node successfully registered at " + event.node().getFullHost());
            }
        }

        // Register the event listener controller before registering nodes
        hexacloud.eventManager().registerListener(new CustomEventListener());

        // Register servers (will trigger NodeRegistered event for each node!)
        hexacloud.registerServer(3001, NodeStatus.OFFLINE)
            .registerServer(3002, NodeStatus.OFFLINE)
            .registerServer(3003, NodeStatus.OFFLINE)
            .registerServer(3004, NodeStatus.OFFLINE)
            .registerServer(3005, NodeStatus.OFFLINE)
            .listClusterNodes()
            .listen()
            .startPingScheduler();

        hexacloud.eventManager().dispatch(new UserCustomEvent("Hello EventController scanning system!"));

        // Sleep briefly to let the user review the boot logs before transitioning to interactive TUI
        try {
            Thread.sleep(1200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Launch the JNI interactive TUI monitor in the foreground
        new MonitorMain().start();
    }
}