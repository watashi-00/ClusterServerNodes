package hexacloud.core.server;

import java.util.ArrayList;
import java.util.List;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.event.ClusterEventBusManager;
import hexacloud.core.contracts.ServerOperations;
import hexacloud.core.server.route.RouteRegistry;
import hexacloud.core.server.route.ClusterController;
import hexacloud.core.utils.DebugUtils;
import hexacloud.infra.server.HttpTransport;
import hexacloud.infra.server.TelnetTransport;
import hexacloud.infra.server.WsTransport;

public class ServerManager implements ServerOperations {

    private final Cluster cluster;
    protected final ClusterEventBusManager eventManager;
    private final RouteRegistry routeRegistry;
    private final List<ServerTransport> activeTransports = new ArrayList<>();
    
    private boolean telnetEnabled = false;
    private boolean httpEnabled = false;
    private boolean wsEnabled = false;
    private int port = 3000;

    public ServerManager(Cluster cluster, ClusterEventBusManager eventManager) {
        this.cluster = cluster;
        this.eventManager = eventManager;
        this.routeRegistry = new RouteRegistry();
        this.routeRegistry.registerController(new ClusterController(cluster));
    }

    public ServerManager(int port, Cluster cluster, ClusterEventBusManager eventManager) {
        this(cluster, eventManager);
        this.port = port;
    }

    public ServerManager enableTelnet(boolean enabled) {
        this.telnetEnabled = enabled;
        DebugUtils.log("ServerManager: Telnet transport " + (enabled ? "AUTHORIZED" : "DISABLED"));
        return this;
    }

    public ServerManager enableHttp(boolean enabled) {
        this.httpEnabled = enabled;
        DebugUtils.log("ServerManager: HTTP transport " + (enabled ? "AUTHORIZED" : "DISABLED"));
        return this;
    }

    public ServerManager enableWs(boolean enabled) {
        this.wsEnabled = enabled;
        DebugUtils.log("ServerManager: WebSocket transport " + (enabled ? "AUTHORIZED" : "DISABLED"));
        return this;
    }

    @Override
    public ServerManager listen(int port) {
        DebugUtils.log("ServerManager: Starting authorized protocol listeners on base port " + port + "...");
        
        // Stop any running transports before starting new ones
        stopTransports();

        if(telnetEnabled) {
            ServerTransport telnet = new TelnetTransport();
            telnet.listen(port, routeRegistry, cluster);
            activeTransports.add(telnet);
        }
        
        if(httpEnabled) {
            ServerTransport http = new HttpTransport();
            // HTTP runs on port + 1
            http.listen(port + 1, routeRegistry, cluster);
            activeTransports.add(http);
        }
        
        if(wsEnabled) {
            ServerTransport ws = new WsTransport();
            // WS runs on port + 2
            ws.listen(port + 2, routeRegistry, cluster);
            activeTransports.add(ws);
        }
        
        if(activeTransports.isEmpty()) {
            DebugUtils.error("ServerManager: Cannot listen. No protocols were authorized! All are disabled.");
        }
        return this;
    }

    @Override
    public ServerManager listen() {
        listen(this.port);
        return this;
    }

    @Override
    public ServerManager stop() {
        stopTransports();
        return this;
    }

    private void stopTransports() {
        for(ServerTransport transport : activeTransports) {
            if(transport != null && transport.isRunning()) {
                transport.stop();
            }
        }
        activeTransports.clear();
    }
}
