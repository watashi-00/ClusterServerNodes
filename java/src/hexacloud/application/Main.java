package hexacloud.application;

import hexacloud.core.ports.GatewayBuilderPort;
import hexacloud.core.ports.RunningGatewayPort;
import hexacloud.core.utils.DebugUtils;
import hexacloud.infra.gateway.GatewayFactory;
import hexacloud.core.event.Event;
import hexacloud.core.event.EventController;
import hexacloud.core.event.Subscribe;
import hexacloud.core.cluster.event.ClusterEvent.NodeRegistered;
import hexacloud.core.cluster.event.ClusterEvent.NodeEventSubmitted;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.PingProtocol;
import hexacloud.core.model.ServerNode;
import hexacloud.core.server.route.RouteController;
import hexacloud.core.server.route.RouteMapping;
import java.io.PrintWriter;
import java.util.Map;

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
            .registerController(new CustomAppController())
            .requireToken(true, "developer-secret-token")
            .rateLimit(200, 60)
            .allowedIps("127.0.0.1")
            .timeout(4500);

        // Register servers with explicit protocol metadata so the demo shows
        // both the pull-based ping side and the new event submission contract.
        builder.registerServer(new ServerNode(
                "http://localhost", 3001, NodeStatus.OFFLINE, false,
                PingProtocol.HTTP, "/health", "Authorization", "Bearer developer-secret-token"
            ))
            .registerServer(new ServerNode(
                "http://localhost", 3002, NodeStatus.OFFLINE, false,
                PingProtocol.GRPC, "/grpc-health", null, null
            ))
            .registerServer(new ServerNode(
                "http://localhost", 3003, NodeStatus.OFFLINE, false,
                PingProtocol.WEBSOCKET, "/ws", null, null
            ))
            .registerServer(new ServerNode(
                "http://localhost", 3004, NodeStatus.OFFLINE, false,
                PingProtocol.TCP, "/", null, null
            ))
            .registerServer(new ServerNode(
                "http://localhost", 3005, NodeStatus.OFFLINE, false,
                PingProtocol.UDP, "/", null, null
            ));

        RunningGatewayPort runningGateway = builder.listen()
            .listClusterNodes()
            .startPingScheduler();

        runningGateway.eventManager().dispatch(new UserCustomEvent("Hello EventController scanning system!"));
        runningGateway.eventManager().dispatch(new NodeEventSubmitted(
            "http://localhost:3001",
            3001,
            "HTTP",
            "json",
            "demo.boot",
            Map.of(
                "source", "Main",
                "cluster", runningGateway.getClusterName(),
                "status", "active"
            )
        ));
    }

    // Custom event verification
    public static record UserCustomEvent(String message) implements Event {}

    //Custom controller listener - automatically discovered by PathUtils scanner
    public static class CustomEventListener implements EventController {
        @Subscribe
        public void onCustomEvent(UserCustomEvent event) {
            DebugUtils.info("UserCustomEvent handler method invoked: " + event.message());
        }

        @Subscribe
        public void onNodeRegistered(NodeRegistered event) {
            DebugUtils.info("Event received: Node successfully registered at " + event.node().getFullHost());
        }

        @Subscribe
        public void onNodeEventSubmitted(NodeEventSubmitted event) {
            DebugUtils.info(
                "Event received: " + event.event() + " from " + event.host() +
                " [" + event.protocol() + "/" + event.format() + "] " + event.attributes()
            );
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
