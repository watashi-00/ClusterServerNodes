package hexacloud.gateway;

import java.util.List;

import hexacloud.gateway.cluster.Cluster;
import hexacloud.gateway.cluster.ServerNode;
import hexacloud.gateway.contracts.ImplGateway;
import hexacloud.gateway.scheduler.SchedulerPing;

class Gateway implements ImplGateway {

    private final Cluster cluster;
    private final SchedulerPing schedulerPing;

    public Gateway() {
        this.cluster = new Cluster();
        this.schedulerPing = new SchedulerPing();
    }

    @Override
    public void startPingScheduler() {
        schedulerPing.startPingScheduler(this.cluster.getCluster());
    }

    @Override
    public void startPingScheduler(int pingInterval) {
        schedulerPing.setPingInterval(pingInterval);
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
	public void startPingScheduler(int pingInterval, List<ServerNode> cluster) {
        schedulerPing.setPingInterval(pingInterval);
        schedulerPing.startPingScheduler(cluster);
	}

	@Override
	public void setPingInterval(int pingInterval) {
        schedulerPing.setPingInterval(pingInterval);
	}

    @Override
    public void stopPingScheduler() {
        schedulerPing.stopPingScheduler();
    }

}
