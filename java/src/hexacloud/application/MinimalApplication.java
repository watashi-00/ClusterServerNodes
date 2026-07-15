package hexacloud.application;

import hexacloud.core.event.Event;
import hexacloud.core.event.EventController;
import hexacloud.core.event.Subscribe;
import hexacloud.core.cluster.event.ClusterEvent;
import hexacloud.core.server.route.RouteController;
import hexacloud.core.server.route.RouteMapping;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.ports.GatewayPort;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.utils.ThreadManager;
import hexacloud.infra.gateway.GatewayFactory;

import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

/**
 * Standalone minimal example application demonstrating the full set of framework features
 * programmatically without launching the Terminal User Interface (TUI).
 * 
 * Configures security, rate-limiting, custom route controllers, and custom event listeners.
 */
public class MinimalApplication {

    public static void main(String[] args) {
        new MinimalApplication().run();
    }

    public void run() {
        // Enable log prints for maximum visibility in the console output
        DebugUtils.setDebugEnabled(true);
        System.out.println("=== Starting GateBridge Minimal Demo Application (No TUI) ===");

        // 1. Programmatic Bootstrapping with Fluent Config API
        GatewayPort gateway = GatewayFactory.createGateway("demo-cluster")
            .port(4000)                   // Base Telnet port (HTTP runs on 4001, Websocket on 4002)
            .pingInterval(3)              // Ping checks scheduled every 3 seconds
            .enableHttp(true)             // Enable HTTP API mapping
            .enableTelnet(true)           // Enable Telnet socket server
            .enableWs(true)               // Enable WebSocket events stream
            .requireToken(true, "demo-secret-key-999")
            .rateLimit(150, 60)           // Limit: 150 requests per 60 seconds
            .allowedIps("127.0.0.1, localhost")
            .timeout(3000);               // Connection timeout threshold of 3 seconds

        // 2. Register Server Nodes
        gateway.registerServer(3001, NodeStatus.OFFLINE)
            .registerServer(3002, NodeStatus.OFFLINE);

        // 3. Start listeners and schedule pings
        gateway.listen()
            .startPingScheduler();

        System.out.println("Gateway successfully listening on port 4000 (Telnet), 4001 (HTTP), 4002 (WS)");

        // 4. Dispatch a custom event to verify listener registration
        gateway.eventManager().dispatch(new DeveloperCustomEvent("GateBridge Bootstrapped Successfully!"));

        // 5. Run a background monitoring loop to print system stats every 5 seconds
        ThreadManager.startVirtual("SystemMonitor", () -> {
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            Runtime runtime = Runtime.getRuntime();
            while (true) {
                try {
                    Thread.sleep(5000);
                    int threadCount = threadMXBean.getThreadCount();
                    long freeMem = runtime.freeMemory() / (1024 * 1024);
                    long totalMem = runtime.totalMemory() / (1024 * 1024);
                    long usedMem = totalMem - freeMem;
                    System.out.printf("[MONITOR] OS Threads: %d | RAM Used: %d MB / %d MB\n", 
                        threadCount, usedMem, totalMem);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }

    // ==========================================
    // CUSTOM DISCOVERABLE EVENT SUBSYSTEM
    // ==========================================

    public static record DeveloperCustomEvent(String message) implements Event {}

    public static class DemoEventController implements EventController {

        @Subscribe
        public void onDeveloperEvent(DeveloperCustomEvent event) {
            System.out.println("[EVENT] Developer Custom Event Received: " + event.message());
        }

        @Subscribe
        public void onNodeStatusChanged(ClusterEvent.NodeStatusChanged event) {
            System.out.printf("[EVENT] Node Status Changed -> Host: %s | Status: %s\n", 
                event.host(), event.status());
        }

        @Subscribe
        public void onNodeTelemetryUpdated(ClusterEvent.NodeTelemetryUpdated event) {
            System.out.printf("[EVENT] Telemetry Updated for Node: %s\n", event.host());
        }
    }

    // ==========================================
    // CUSTOM DISCOVERABLE ROUTE CONTROLLER
    // ==========================================

    public static class DemoRouteController implements RouteController {

        @RouteMapping("HELLO")
        public void handleHello(String args, PrintWriter out) {
            out.println("HELLO FROM MINIMAL APPLICATION ROUTE!");
            out.println("Arguments received: " + (args.isEmpty() ? "None" : args));
        }

        @RouteMapping("SYSTEM_INFO")
        public void handleSystemInfo(String args, PrintWriter out) {
            out.println("GateBridge Framework Status: ACTIVE");
            out.println("Available Processors: " + Runtime.getRuntime().availableProcessors());
        }
    }
}
