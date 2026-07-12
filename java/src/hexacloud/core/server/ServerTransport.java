package hexacloud.core.server;

import hexacloud.core.server.route.RouteRegistry;

public interface ServerTransport {
    void listen(int port, RouteRegistry registry);
    void stop();
    boolean isRunning();
}
