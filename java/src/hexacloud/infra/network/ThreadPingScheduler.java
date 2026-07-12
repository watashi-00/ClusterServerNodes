package hexacloud.infra.network;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import hexacloud.core.cluster.event.ClusterEventBusManager;
import hexacloud.core.cluster.event.NodeStatusChanged;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.config.ClusterConfig;

public class ThreadPingScheduler {

    private ScheduledExecutorService scheduler;
    private int interval = ClusterConfig.DEFAULT_PING_INTERVAL_SECONDS;

    private final HttpCli httpcli;
    private final ClusterEventBusManager eventManager;

    public ThreadPingScheduler(ClusterEventBusManager eventManager) {
        this.eventManager = eventManager;
        this.httpcli = new HttpCli();
    }
    
    public void startPingScheduler(Supplier<List<ServerNode>> clusterSupplier) {

        if(scheduler == null || scheduler.isShutdown()) {
            scheduler = java.util.concurrent.Executors.newScheduledThreadPool(ClusterConfig.SCHEDULER_THREAD_POOL_SIZE);
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    for(ServerNode node : clusterSupplier.get()) {
                        if(node != null) {
                            pingClusterNode(node);
                        }
                    }
                } catch (Exception e) {
                    DebugUtils.error("Unexpected error in ping scheduler execution", e);
                }
            }, 0, this.interval, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    public void stopPingScheduler() {
        if(scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if(!scheduler.awaitTermination(ClusterConfig.AWAIT_TERMINATION_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch(InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public void setInterval(int intervalInSeconds) {
        this.interval = intervalInSeconds;
    }

    private void pingClusterNode(ServerNode node) {
        CompletableFuture<NodeStatus> response = httpcli.fetchPingAsync(node.getFullHost());

        response.thenAccept(status -> {
            if(node.status() != status) {
                // event dispatch NodeStatusChanged
                eventManager.dispatch(new NodeStatusChanged(node.getFullHost(), status));
            }
        });
    }
}
