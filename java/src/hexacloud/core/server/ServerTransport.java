package hexacloud.core.server;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.server.route.RouteRegistry;

public interface ServerTransport {
    void listen(int port, RouteRegistry registry, Cluster cluster);
    void stop();
    boolean isRunning();
}
