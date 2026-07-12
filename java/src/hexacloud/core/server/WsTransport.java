package hexacloud.core.server;

import hexacloud.core.server.route.RouteRegistry;
import hexacloud.core.utils.DebugUtils;

public class WsTransport implements ServerTransport {
    private boolean running = false;

    @Override
    public void listen(int port, RouteRegistry registry, hexacloud.core.cluster.Cluster cluster) {
        running = true;
        DebugUtils.log("WebSocket Transport (Stub) successfully bound and simulated on port " + port);
    }

    @Override
    public void stop() {
        running = false;
        DebugUtils.log("WebSocket Transport (Stub) stopped.");
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
