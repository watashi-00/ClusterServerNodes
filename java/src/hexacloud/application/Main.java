package hexacloud.application;

import hexacloud.core.ports.GatewayBuilderPort;
import hexacloud.core.ports.RunningGatewayPort;
import hexacloud.core.utils.DebugUtils;
import hexacloud.infra.gateway.GatewayFactory;
import hexacloud.core.event.Event;
import hexacloud.core.event.EventController;
import hexacloud.core.event.Subscribe;
import hexacloud.core.cluster.event.ClusterEvent.NodeRegistered;
import hexacloud.core.server.route.RouteController;
import hexacloud.core.server.route.RouteMapping;
import java.io.PrintWriter;

public class Main {
    
    public static void main(String[] args) {
        new Main().start();
    }

    public void start() {
        DebugUtils.setDebugEnabled(false);
        
        GatewayBuilderPort builder = GatewayFactory.createGateway("watashi-00")
            .port(3000)
            .pingInterval(5)
            .enableTelnet(true)
            .enableHttp(true)
            .enableWs(true)
            .requireToken(true, "developer-secret-token")
            .rateLimit(200, 60)
            .allowedIps("127.0.0.1")
            .timeout(4500);

        // Register servers (will trigger NodeRegistered event for each node!)
        builder.registerServer(3001)
            .registerServer(3002)
            .registerServer(3003)
            .registerServer(3004)
            .registerServer(3005);

        RunningGatewayPort runningGateway = builder.listen()
            .listClusterNodes()
            .startPingScheduler();

        runningGateway.eventManager().dispatch(new UserCustomEvent("Hello EventController scanning system!"));
    }

    // Custom event verification
    public static record UserCustomEvent(String message) implements Event {}

    // Custom controller listener - automatically discovered by PathUtils scanner
    public static class CustomEventListener implements EventController {
        @Subscribe
        public void onCustomEvent(UserCustomEvent event) {
            DebugUtils.info("UserCustomEvent handler method invoked: " + event.message());
        }

        @Subscribe
        public void onNodeRegistered(NodeRegistered event) {
            DebugUtils.info("Event received: Node successfully registered at " + event.node().getFullHost());
        }
    }

    // Custom developer endpoint controller - automatically discovered by PathUtils scanner
    public static class CustomAppController implements RouteController {
        @RouteMapping("HELLO")
        public void sayHello(String args, PrintWriter out) {
            out.println("HELLO FROM DEVELOPER ROUTE! Args: " + args);
        }
    }
}