package hexacloud.infra.server;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.util.function.BiConsumer;

import hexacloud.core.server.ServerTransport;
import hexacloud.core.server.route.RouteRegistry;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.utils.ThreadManager;

/**
 * Concrete HTTP implementation of ServerTransport bound to a local port
 * and using virtual threads for routing and rate-limiting incoming traffic.
 */
public class HttpTransport implements ServerTransport {

    //private HttpServer server;
    private boolean running = false;

    @Override
    public void listen(int port, RouteRegistry registry, hexacloud.core.cluster.Cluster cluster) {
        throw new UnsupportedOperationException("Listen functionality is not implemented for Java 8");
    }

    @Override
    public void stop() {
        throw new UnsupportedOperationException("Stop functionality is not implemented for Java 8");
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
