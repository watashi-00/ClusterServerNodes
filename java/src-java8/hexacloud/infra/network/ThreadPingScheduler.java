package hexacloud.infra.network;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import hexacloud.core.cluster.event.ClusterEventBusManager;
import hexacloud.core.cluster.event.ClusterEvent.NodeStatusChanged;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.PingResult;
import hexacloud.core.model.ServerNode;
import hexacloud.core.ports.PingClientPort;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.utils.ThreadManager;
import hexacloud.core.config.ClusterConfig;

/**
 * Thread ping scheduler that schedules health check tasks at fixed rates 
 * using decoupled PingClientPort adapters.
 */
public class ThreadPingScheduler {

    private ScheduledExecutorService scheduler;
    private int interval = ClusterConfig.DEFAULT_PING_INTERVAL_SECONDS;

    private final String clusterName;
    private final PingClientPort pingClient;
    private final ClusterEventBusManager eventManager;

    public ThreadPingScheduler(String clusterName, ClusterEventBusManager eventManager) {
        this.clusterName = clusterName;
        this.eventManager = eventManager;
        this.pingClient = new MultiProtocolPingAdapter();
    }
    
    public void startPingScheduler(Supplier<List<ServerNode>> clusterSupplier) {
        throw new UnsupportedOperationException("Start functionality is not implemented for Java 8");
    }


    public void setInterval(int intervalInSeconds) {
        this.interval = intervalInSeconds;
    }

    public void stopPingScheduler() {
        throw new UnsupportedOperationException("Stop functionality is not implemented for Java 8");
    }


    private void pingClusterNode(ServerNode node) {
        throw new UnsupportedOperationException("Ping functionality is not implemented for Java 8");
    }
}
