package hexacloud.infra.gateway;

import java.util.List;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.model.ServerNode;
import hexacloud.core.ports.GatewayPort;
import hexacloud.infra.network.ThreadPingScheduler;

class LocalGatewayAdapter implements GatewayPort {

    private final Cluster cluster;
    private final ThreadPingScheduler schedulerPing;

    public LocalGatewayAdapter() {
        this.cluster = new Cluster();
        this.schedulerPing = new ThreadPingScheduler();
    }

    @Override
    public void startPingScheduler() {
        schedulerPing.startPingScheduler(this.cluster.getCluster());
    }

    @Override
    public void startPingScheduler(int intervalInSeconds) {
        schedulerPing.setInterval(intervalInSeconds);
        schedulerPing.startPingScheduler(this.cluster.getCluster());
    }

    @Override
    public void startPingScheduler(List<ServerNode> cluster) {
        schedulerPing.startPingScheduler(cluster);
    }

	@Override
	public void startAllServers() {
        cluster.startAll();
	}

    @Override
    public void addServer(int port) {
        cluster.start(port);
    }

    @Override
    public void addServer(ServerNode node) {
        cluster.start(node);
    }

	@Override
	public void addServer(int port, boolean isExternal) {
        cluster.start(port, isExternal);
	}

	@Override
	public void addServer(int port, String host, boolean isExternal) {
        cluster.start(port, host, isExternal);
	}

	@Override
	public void stopAllServers() {
        cluster.stopAll();
	}

	@Override
	public void stopServer(int port) {
        cluster.stop(port);
	}

	@Override
	public void stopLastServer() {
        cluster.stop();
	}

	@Override
	public void listClusterNodes() {
        cluster.listClusterNodes();
	}

	@Override
	public void startPingScheduler(int intervalInSeconds, List<ServerNode> cluster) {
        schedulerPing.setInterval(intervalInSeconds);
        schedulerPing.startPingScheduler(cluster);
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
