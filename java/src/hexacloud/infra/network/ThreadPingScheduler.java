package hexacloud.infra.network;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import hexacloud.core.cluster.event.ClusterEventBusManager;
import hexacloud.core.cluster.event.NodeStatusChanged;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;

public class ThreadPingScheduler {

    private ScheduledExecutorService scheduler;
    private int interval = 5; // Default ping interval in seconds

    private final HttpCli httpcli;
    private final ClusterEventBusManager eventManager;

    public ThreadPingScheduler(ClusterEventBusManager eventManager) {
        this.eventManager = eventManager;
        this.httpcli = new HttpCli();
    }
    
    public void startPingScheduler(Supplier<List<ServerNode>> clusterSupplier) {

        if(scheduler == null || scheduler.isShutdown()) {
            scheduler = java.util.concurrent.Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(() -> {
                for(ServerNode node : clusterSupplier.get()) {
                    if(node != null) {
                        pingClusterNode(node);
                    }
                }
            }, 0, this.interval, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    public void stopPingScheduler() {
        if(scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    public void setInterval(int intervalInSeconds) {
        this.interval = intervalInSeconds;
    }

    private void pingClusterNode(ServerNode node) {
        var host = node.isExternal() ? node.host() : node.host() + node.port();
        NodeStatus res = httpcli.fetchPing(host);
        
        if(node.status() != res) {
            eventManager.dispatch(new NodeStatusChanged(node.host(), res));
        }
    }

}
