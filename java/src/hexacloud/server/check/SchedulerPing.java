package hexacloud.server.check;

import java.util.concurrent.ScheduledExecutorService;

import hexacloud.server.ServerNode;

public class SchedulerPing {

    private ScheduledExecutorService scheduler;
    private int pingInterval = 5; // Default ping interval in seconds
    
    
    public void startPingScheduler(ServerNode[] cluster) {
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

    public void pingClusterNode(ServerNode node) {
        // Implement the logic to ping the cluster node
        System.out.println("Pinging node: " + node.host() + node.port());
    }
}
