package hexacloud.infra.network;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import hexacloud.core.model.ServerNode;

public class ThreadPingScheduler {

    private ScheduledExecutorService scheduler;
    private HttpCli httpcli;
    private int pingInterval = 5; // Default ping interval in seconds
    
    public void startPingScheduler(List<ServerNode> cluster) {
        if(httpcli == null) {
            this.httpcli = new HttpCli();
        }

        if(scheduler == null || scheduler.isShutdown()) {
            scheduler = java.util.concurrent.Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(() -> {
                for(ServerNode node : cluster) {
                    if(node != null) {
                        pingClusterNode(node);
                    }
                }
            }, 0, this.pingInterval, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    public void stopPingScheduler() {
        if(scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    public void setPingInterval(int pingInterval) {
        this.pingInterval = pingInterval;
    }

    private void pingClusterNode(ServerNode node) {
        var host = node.isExternal() ? node.host() : node.host() + node.port();
        httpcli.fetchPing(host);
    }

}
