package hexacloud.infra.gateway;

import java.util.List;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.ClusterManager;
import hexacloud.core.cluster.event.ClusterEventBusManager;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;
import hexacloud.core.ports.GatewayPort;
import hexacloud.infra.network.ThreadPingScheduler;

class LocalGatewayAdapter implements GatewayPort {

    private final ClusterManager clusterManager;
    private final ClusterEventBusManager clusterEventManager;
    private final ThreadPingScheduler schedulerPing;

    public LocalGatewayAdapter() {
        this.clusterEventManager = new ClusterEventBusManager();
        this.clusterManager = new ClusterManager(new Cluster("watashi-00"), this.clusterEventManager);
        this.schedulerPing = new ThreadPingScheduler(this.clusterEventManager);
    }

    @Override
    public void startPingScheduler() {
        schedulerPing.startPingScheduler(() -> this.clusterManager.getCluster());
    }
    
    @Override
    public void startPingScheduler(int intervalInSeconds) {
        schedulerPing.setInterval(intervalInSeconds);
        schedulerPing.startPingScheduler(() -> this.clusterManager.getCluster());
    }
    
    @Override
    public void startPingScheduler(List<ServerNode> cluster) {
        schedulerPing.startPingScheduler(() -> cluster);
    }
    
    @Override
	public void startPingScheduler(int intervalInSeconds, List<ServerNode> cluster) {
        schedulerPing.setInterval(intervalInSeconds);
        schedulerPing.startPingScheduler(() -> cluster);
	}

	@Override
	public void startAllServers() {
        clusterManager.startAll();
	}

    @Override
    public void addServer(int port) {
        clusterManager.start(port);
    }

    @Override
    public void addServer(int port, NodeStatus status) {
        clusterManager.start(port, status);
    }

    @Override
    public void addServer(ServerNode node) {
        clusterManager.start(node);
    }

	@Override
	public void addServer(int port, boolean isExternal) {
        clusterManager.start(port, isExternal);
	}

	@Override
	public void addServer(int port, String host, boolean isExternal) {
        clusterManager.start(port, host, isExternal);
	}

	@Override
	public void stopAllServers() {
        clusterManager.stopAll();
	}

	@Override
	public void stopServer(String fullHost) {
        clusterManager.stop(fullHost);
	}

	@Override
	public void stopLastServer() {
        clusterManager.stop();
	}

	@Override
	public void listClusterNodes() {
        clusterManager.listClusterNodes();
	}

	@Override
	public void setPingInterval(int pingInterval) {
        schedulerPing.setInterval(pingInterval);
	}

    @Override
    public void stopPingScheduler() {
        schedulerPing.stopPingScheduler();
    }

}
